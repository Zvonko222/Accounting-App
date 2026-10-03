using System.Text.Json;
using System.Text.Json.Serialization;

// ============================================================================
// 售货记账 · 云同步服务端（Phase 11）
//
// 设计：文档式同步存储。服务端不理解业务字段，只把客户端整行实体（JSON）
// 按 (deviceId, table, rowId) 幂等存取——客户端加字段、加表，服务端零改动。
// 幂等性：按 (deviceId, table, rowId) UPSERT，同一条数据重复推送
// 不会产生第二条记录（对应 ARCHITECTURE.md 4.2）。
//
// 存储：JSON 文件（写入 = 内存表整体落盘，临时文件 + 原子替换，防断电损坏）。
// 单店规模（数千行）完全够用；将来要 SQL 级查询/多店并发时，
// 只替换本文件里的存储函数，API 协议不变，客户端零改动。
//
// 运行：  dotnet run --urls http://127.0.0.1:5080
// 密钥：  环境变量 SYNC_KEY（默认 change-me-key），客户端设置页填同一把
// 数据库：环境变量 SYNC_DB（默认 syncdata/sync.json）
// ============================================================================

var builder = WebApplication.CreateBuilder(args);
var app = builder.Build();

var syncKey = Environment.GetEnvironmentVariable("SYNC_KEY") ?? "change-me-key";
var dbPath = Environment.GetEnvironmentVariable("SYNC_DB")
             ?? Path.Combine("syncdata", "sync.json");

var gate = new object(); // 单写者锁：推送串行化，读写互斥

var rows = LoadRows();

// ---------------------------------------------------------------------------

List<StoredRow> LoadRows()
{
    if (!File.Exists(dbPath))
    {
        Console.WriteLine($"[sync] 全新存储：{Path.GetFullPath(dbPath)}");
        return new List<StoredRow>();
    }
    try
    {
        var json = File.ReadAllText(dbPath);
        Console.WriteLine($"[sync] 已加载存储：{Path.GetFullPath(dbPath)}");
        return JsonSerializer.Deserialize<List<StoredRow>>(json) ?? new List<StoredRow>();
    }
    catch (IOException e)
    {
        // 文件损坏等极端情况：把坏文件改名备份，从空存储开始，不让服务端起不来
        Console.WriteLine($"[sync] 存储读取失败，已备份原文件并新建：{e.Message}");
        File.Move(dbPath, dbPath + ".broken." + Now(), overwrite: true);
        return new List<StoredRow>();
    }
}

/// <summary>整体落盘：先写临时文件再原子替换，断电也不会损坏已有数据</summary>
void SaveRows()
{
    Directory.CreateDirectory(Path.GetDirectoryName(Path.GetFullPath(dbPath))!);
    var temp = dbPath + ".tmp";
    File.WriteAllText(temp, JsonSerializer.Serialize(rows));
    File.Move(temp, dbPath, overwrite: true);
}

// 密钥校验：所有同步端点共用（纯判断，响应由端点自己返回）
bool CheckKey(HttpContext ctx)
{
    return ctx.Request.Headers["X-Sync-Key"].ToString() == syncKey;
}

long Now() => DateTimeOffset.UtcNow.ToUnixTimeMilliseconds();

// ---------------------------------------------------------------------------

app.MapGet("/", () => Results.Json(new
{
    service = "accounting-sync-server",
    hint = "POST /api/sync/push   GET /api/sync/pull?deviceId=&since=&limit="
}));

// 上行推送：逐行 UPSERT（幂等）
app.MapPost("/api/sync/push", (HttpContext ctx, JsonElement body) =>
{
    if (!CheckKey(ctx)) return Results.Json(new { error = "invalid sync key" },
        statusCode: StatusCodes.Status401Unauthorized);

    var deviceId = body.GetProperty("deviceId").GetString()!;
    var changes = body.GetProperty("changes");
    var now = Now();
    var accepted = 0;

    lock (gate)
    {
        foreach (var change in changes.EnumerateArray())
        {
            var table = change.GetProperty("table").GetString()!;
            var rowId = change.GetProperty("rowId").GetString()!;
            var updatedAt = change.GetProperty("updatedAt").GetInt64();
            var isDeleted = change.GetProperty("isDeleted").GetBoolean();
            // payload 原样保存原始 JSON 文本，不做任何字段解释
            var payload = change.GetProperty("payload").GetRawText();

            var existing = rows.Find(r =>
                r.DeviceId == deviceId && r.Table == table && r.RowId == rowId);
            if (existing != null)
            {
                rows.Remove(existing);
            }
            rows.Add(new StoredRow(deviceId, table, rowId, updatedAt, isDeleted, payload, now));
            accepted++;
        }
        SaveRows();
    }

    Console.WriteLine($"[push] device={deviceId[..Math.Min(8, deviceId.Length)]}… rows={accepted}");
    return Results.Json(new { serverTime = Now(), accepted });
});

// 下行拉取：该设备自 since 之后（按 updated_at）的所有变更，含墓碑
app.MapGet("/api/sync/pull", (HttpContext ctx) =>
{
    if (!CheckKey(ctx)) return Results.Json(new { error = "invalid sync key" },
        statusCode: StatusCodes.Status401Unauthorized);

    var deviceId = ctx.Request.Query["deviceId"].ToString();
    var since = long.TryParse(ctx.Request.Query["since"], out var s) ? s : 0;
    var limit = long.TryParse(ctx.Request.Query["limit"], out var l) ? Math.Min(l, 1000) : 200;
    var now = Now();

    List<object> changes;
    lock (gate)
    {
        changes = rows
            .Where(r => r.DeviceId == deviceId && r.UpdatedAt > since)
            .OrderBy(r => r.UpdatedAt)
            .Take((int)limit)
            .Select(r => (object)new
            {
                table = r.Table,
                rowId = r.RowId,
                updatedAt = r.UpdatedAt,
                isDeleted = r.IsDeleted,
                payload = JsonDocument.Parse(r.Payload).RootElement.Clone()
            })
            .ToList();
    }

    Console.WriteLine($"[pull] device={deviceId[..Math.Min(8, deviceId.Length)]}… since={since} rows={changes.Count}");
    return Results.Json(new { serverTime = now, changes });
});

app.Run();

/// <summary>服务端存储的一行：设备分区 + 表名 + 行主键 + 整行 JSON</summary>
record StoredRow(
    string DeviceId,
    string Table,
    string RowId,
    long UpdatedAt,
    bool IsDeleted,
    string Payload,
    long StoredAt);

