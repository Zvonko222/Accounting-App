# 售货记账 App — 架构设计文档（定稿 v1）

> 面向小型商户/个人卖家的离线优先（Local-first / Offline-first）售货记账软件。
> 本文档是全部 15 个开发阶段的唯一设计依据，改设计先改这里。

---

## 0. 已定决策记录（Decision Log）

| # | 决策 | 结论 | 状态 |
|---|------|------|------|
| D1 | 总体架构 | 分层：UI → ViewModel → Repository → Room DAO → SQLite；同步为旁路子系统 | 设计定稿 |
| D2 | 库存 | `products.stockQuantityMilli` 存当前值 + `stock_movements` 台账记每次变动 | 按recommended暂定，可推翻 |
| D3 | 商品分类 | 独立 `categories` 表，首次启动预置常用分类 | 按 recommended 暂定，可推翻 |
| D4 | 数量精度 | 支持小数，存"数量×1000"的整数（1.7 斤 = 1700） | 按 recommended 暂定，可推翻 |
| D5 | 金额 | 一律 `long`，单位"分"；禁止 double/float 存钱 | 硬性规则 |
| D6 | 主键 | 客户端生成 UUID 字符串，业务数据永不用自增 ID | 硬性规则 |
| D7 | 删除 | 全部软删除（isDeleted 墓碑），不允许物理 DELETE 业务数据 | 硬性规则 |
| D8 | 语言/UI | Java + XML + Activity/Fragment/RecyclerView/ViewModel/LiveData；不用 Compose/Coroutines/Flow/Hilt/RxJava | 硬性规则 |
| D9 | 线程 | 全局单线程 Executor 负责写库；读走 Room 的 LiveData 自动后台 | 硬性规则 |
| D10 | 已有工程骨架 | 复用现有 AS 工程；Phase 1 把包名 `com.example.myapplication` 改为 `com.example.accounting`；minSdk 24 / targetSdk 36 / Java 11 已配置，符合设计 | 事实记录 |

---

## 1. 总体架构

```
┌───────────────────────────────────────────────────────────┐
│ UI 层：Activity / Fragment / XML / RecyclerView             │ 只做两件事：显示数据、收集输入
├───────────────────────────────────────────────────────────┤
│ ViewModel 层：ViewModel + LiveData                          │ 持有界面状态；调用 Repository；不碰 View
├───────────────────────────────────────────────────────────┤
│ Repository 层：ProductRepository / SaleRepository / …       │ 全 App 唯一允许写数据库的层；事务边界
├──────────────────────────┬────────────────────────────────┤
│ 本地数据：Room DAO        │ 同步子系统（旁路，独立包）        │
│ SQLite = 唯一事实源       │ SyncScheduler / SyncWorker      │
└──────────────────────────┴───────────────┬─────────────────┘
                                           │ 仅同步时使用
                                           ▼
                                  ASP.NET Core API（Phase 11 起）
```

三条铁律，所有后续设计由此推导：

1. **UI 永远只读本地库。** 界面上看到的数据全部来自 Room；网络层不存在于 UI 的视野。离线可用因此不是"功能"，而是架构的自然结果。
2. **Repository 是唯一写入入口。** 所有写操作只发生在 Repository，跨表修改必须包在同一个事务里；Activity/ViewModel 永不直接调 DAO 写数据。
3. **同步是旁路。** 写库成功 ≠ 同步成功；同步失败不影响任何业务操作。SyncEngine 通过 `syncStatus` 列发现脏数据，与业务代码互不干扰。

### 线程模型

- **读**：DAO 返回 `LiveData<List<…>>`，Room 自动后台执行查询并在表变化时通知 UI。
- **写**：`AccountingApp` 创建全局唯一单线程 `ExecutorService`，注入各 Repository；串行写库、行为可预期。
- **回调**：写操作的成败通过回调回传 ViewModel → UI，失败必须让用户看见，绝不静默丢数据。

### 技术选型

| 方面 | 选择 | 理由 |
|---|---|---|
| 语言 | Java 11，朴素写法 | 学习成本最低；工程已配置 |
| UI | XML + Activity/Fragment + RecyclerView + Material3 | 打开 XML 即见页面结构 |
| UI 状态 | ViewModel + LiveData | Java 生态最直白（Flow 属 Kotlin，不用） |
| 本地库 | Room，`exportSchema = true` | schema JSON 留档是写 Migration 的依据 |
| 后台任务 | WorkManager | 持久化、重启不丢；不依赖 Google Play 服务，国内机型可用 |
| JSON/HTTP | Phase 9 引入，倾向 OkHttp + Gson | 用不到就不加依赖；比 Retrofit 注解更直白 |
| 明确不用 | Hilt/Dagger、RxJava、Compose、Coroutines、多模块、Base 类族 | 与"可读可学"冲突 |

---

## 2. 数据库设计

### 2.1 全局数据约定（每张表每个字段都遵守）

1. **主键 = UUID 字符串**，创建时 `UUID.randomUUID()` 生成，终身不变。服务器按它做幂等写入。不用 ULID：排序一律走 `updatedAt`/时间列，从不按主键排序。
2. **金额存 `long` 分**。12.50 元 = 1250。
3. **时间存 `long` 毫秒时间戳**。格式化只发生在 UI 层工具类。
4. **软删除**：删除 = 打标记（见 2.2），防服务器把旧数据推回来复活。
5. **数量存"×1000"整数**（D4）：1.7 斤 = 1700；换算只存在于 `QuantityUtil`。

### 2.2 同步列组 —— 所有可同步业务表的公共 9 列

不单独建 SyncRecord 表：`WHERE syncStatus != SYNCED` 本身就是队列，行自带全部同步状态，调试一目了然；两张表记同一状态反而会不一致。

```
createdAt     毫秒    行创建时间（客户端时钟）
updatedAt     毫秒    最后修改时间 —— 同步排序与冲突判定的唯一依据
isDeleted     0/1     软删除标记（tombstone，随 push 上行）
deletedAt     毫秒    可空
syncStatus    整数    SyncStatus 常量：0=SYNCED 1=PENDING 2=SYNCING 3=FAILED
lastSyncedAt  毫秒    可空，服务器最后一次确认时间
retryCount    整数    同步重试次数
lastSyncError 文本    可空，最近失败原因（设置页可查看）
```

- 本地新建/修改 → `PENDING`；由 Pull 合并进来的 → `SYNCED`。
- `SYNCING` 是推送途中的短暂状态；**SyncWorker 启动第一步把所有 SYNCING 重置为 PENDING**（崩溃恢复）。

### 2.3 表关系总览

```
categories ──1:N──► products
sales      ──1:N──► sale_items        （主子表，同事务写入）
purchases  ──1:N──► purchase_items    （主子表，同事务写入）
expenses                            （独立，无子表）
stock_movements ──N:1──► products    （库存台账）
sync_state                          （本地内部表：游标/deviceId，不同步）
```

### 2.4 表结构（设计级 SQL；`-- …同步列组` 代表 2.2 的九列）

**categories**

```sql
CREATE TABLE categories (
  id        TEXT PRIMARY KEY NOT NULL,   -- UUID
  name      TEXT NOT NULL UNIQUE,        -- 唯一约束防"饮料/饮品"并存
  sortOrder INTEGER NOT NULL DEFAULT 0,
  -- …同步列组
);
-- 首次启动预置：饮料、零食、日用品、烟酒、其他
```

**products**

```sql
CREATE TABLE products (
  id                 TEXT PRIMARY KEY NOT NULL,
  name               TEXT NOT NULL,
  barcode            TEXT,                        -- 可空；条码检索
  categoryId         TEXT REFERENCES categories(id),
  unit               TEXT NOT NULL DEFAULT '个',  -- 个/斤/瓶/箱，纯显示
  salePriceCents     INTEGER NOT NULL,
  purchasePriceCents INTEGER NOT NULL DEFAULT 0,  -- 最近进价，参考
  stockQuantityMilli INTEGER NOT NULL DEFAULT 0,  -- 当前库存；允许为负=先卖后补，UI 红色警告
  lowStockThreshold  INTEGER NOT NULL DEFAULT 0,  -- 0=不提醒
  note               TEXT,
  -- …同步列组
);
CREATE INDEX idx_products_name    ON products(name);
CREATE INDEX idx_products_sync    ON products(syncStatus);
CREATE INDEX idx_products_barcode ON products(barcode);
```

**sales**

```sql
CREATE TABLE sales (
  id               TEXT PRIMARY KEY NOT NULL,
  saleTime         INTEGER NOT NULL,            -- 业务时间（用户可改；统计按它算）
  totalAmountCents INTEGER NOT NULL,            -- 保存时一次算定，永不重算
  discountCents    INTEGER NOT NULL DEFAULT 0,
  payMethod        INTEGER NOT NULL,            -- PayMethod: CASH/WECHAT/ALIPAY/CARD/OTHER
  customerName     TEXT,                        -- 可空；v1 不建客户表
  note             TEXT,
  -- …同步列组
);
CREATE INDEX idx_sales_time ON sales(saleTime DESC);
CREATE INDEX idx_sales_sync ON sales(syncStatus);
```

**sale_items** —— 三个快照字段是记账软件的关键：账本必须记录"当时发生了什么"，商品以后改名/改价不得影响历史。

```sql
CREATE TABLE sale_items (
  id             TEXT PRIMARY KEY NOT NULL,
  saleId         TEXT NOT NULL REFERENCES sales(id) ON DELETE CASCADE,
  productId      TEXT NOT NULL REFERENCES products(id),
  productName    TEXT NOT NULL,          -- 快照
  unitPriceCents INTEGER NOT NULL,       -- 快照：当时成交价
  quantityMilli  INTEGER NOT NULL,
  lineTotalCents INTEGER NOT NULL,       -- 快照：保存时算定
  -- …同步列组
);
CREATE INDEX idx_sale_items_sale ON sale_items(saleId);
CREATE INDEX idx_sale_items_sync ON sale_items(syncStatus);
```

**purchases / purchase_items**（与 sales 对称）

```sql
CREATE TABLE purchases (
  id               TEXT PRIMARY KEY NOT NULL,
  purchaseTime     INTEGER NOT NULL,
  totalAmountCents INTEGER NOT NULL,
  supplierName     TEXT,                  -- 可空
  note             TEXT,
  -- …同步列组
);
CREATE TABLE purchase_items (
  id             TEXT PRIMARY KEY NOT NULL,
  purchaseId     TEXT NOT NULL REFERENCES purchases(id) ON DELETE CASCADE,
  productId      TEXT NOT NULL REFERENCES products(id),
  productName    TEXT NOT NULL,           -- 快照
  unitCostCents  INTEGER NOT NULL,        -- 本次进价快照
  quantityMilli  INTEGER NOT NULL,
  lineTotalCents INTEGER NOT NULL,
  -- …同步列组
);
```

**expenses**

```sql
CREATE TABLE expenses (
  id          TEXT PRIMARY KEY NOT NULL,
  expenseTime INTEGER NOT NULL,
  expenseType INTEGER NOT NULL,    -- ExpenseType: RENT/UTILITY/TRANSPORT/SALARY/OTHER
  amountCents INTEGER NOT NULL,
  note        TEXT,
  -- …同步列组
);
CREATE INDEX idx_expenses_time ON expenses(expenseTime);
```

**stock_movements**（D2 台账）

```sql
CREATE TABLE stock_movements (
  id                  TEXT PRIMARY KEY NOT NULL,
  productId           TEXT NOT NULL REFERENCES products(id),
  changeType          INTEGER NOT NULL,   -- StockChangeType: SALE/PURCHASE/ADJUST/INITIAL
  changeQuantityMilli INTEGER NOT NULL,   -- 带符号：卖出 -1500，进货 +2400
  relatedSaleId       TEXT,               -- 可空，来源单据
  relatedPurchaseId   TEXT,               -- 可空
  movementTime        INTEGER NOT NULL,
  note                TEXT,
  -- …同步列组
);
CREATE INDEX idx_stock_mov_product ON stock_movements(productId, movementTime);
```

**sync_state**（本地内部，不同步）

```sql
CREATE TABLE sync_state (
  key       TEXT PRIMARY KEY NOT NULL,  -- "lastPullAt" / "deviceId" / "serverClockOffset"
  value     TEXT NOT NULL,
  updatedAt INTEGER NOT NULL
);
```

### 2.5 有意不做的设计

| 不做 | 理由 | 何时再做 |
|---|---|---|
| Customer/Supplier 完整表 | 小商户不需要客户档案；单上一个可选名字够用 | 有赊账需求时 Migration 加表 |
| 实时计算历史小计 | 快照字段保证账本不可变 | 不需要 |
| 按月汇总表 | 几千条流水 `GROUP BY` 毫秒级 | 数据到十万级再说 |
| 修改历史单据 | 记账惯例：错单**作废重开**（软删+库存冲回） | 产品原则，不做 |
| SQLCipher | 加密依赖 v1 不值得 | 涉敏感数据再议 |

### 2.6 数据安全机制

- **Transaction**：跨表写全部包在 `db.runInTransaction()`（Repository 层，不用注解）。
- **Foreign Key**：Room 声明 FK 后自动开启约束；主子表 CASCADE，跨聚合 NO ACTION。
- **Migration**：`exportSchema=true`；每次改表必须写 `MIGRATION_N_M`；**永久禁止** `fallbackToDestructiveMigration`（等于清空用户账本）。
- **写入确认**：保存成败必须回传 UI；失败即报错，不静默。

---

## 3. 本地数据流 —— 卖出一单（全 App 最重要的链路）

```
[SalesFragment] 用户点大按钮"完成销售"
    │ 收集购物车：商品 × 数量 × 单价
    ▼
[SalesViewModel.recordSale(cart)]
    ▼
[SaleRepository.recordSale]  ← db.runInTransaction() 开始
    1. validateSale：商品存在、数量>0、单价≥0
    2. new Sale(UUID, now, 总价算定)                 PENDING
    3. 各购物车项 → SaleItem(UUID + 名称/价格快照)   PENDING
    4. 各商品 stockQuantityMilli -= 数量, updatedAt  PENDING
    5. 各商品 StockMovement(SALE, -数量, saleId)     PENDING
    事务提交（任一步失败全部回滚，UI 收到错误）
    ▼
[Room InvalidationTracker] → LiveData → 列表/库存页自动刷新
    ▼
[SyncScheduler.requestSync()] WorkManager 排"有网络才跑"的一次性任务
    ▼
UI 显示"已保存 ✓" —— 全程 0 次网络请求，飞行模式同样成立
```

- 总价保存时一次算定（`Σ行小计 − 优惠`），此后只读 `totalAmountCents`。
- **作废销售** `voidSale(saleId)` 同为事务：单据软删 + 库存冲回 + 反向 StockMovement。

## 4. 同步数据流

### 4.1 状态机

```
新建/修改 → PENDING →(Worker取出)→ SYNCING → 服务器确认 → SYNCED
              ▲                        │
              └────失败/崩溃恢复────────┘   失败: FAILED(+retryCount, +lastSyncError)
                                        FAILED 也属"待同步"，下次照常取
```

### 4.2 Push（本地 → 服务器）

```
[SyncWorker.doWork]（WorkManager，有网络时唤醒；重启/被杀不影响最终执行）
 1. 恢复：UPDATE 各表 SET syncStatus=PENDING WHERE syncStatus=SYNCING
 2. 每表取 WHERE syncStatus IN (PENDING,FAILED) ORDER BY updatedAt LIMIT 50 → 标 SYNCING
 3. POST /api/sync/push { deviceId, changes:[{type:"SALE", row:{id(UUID),业务字段,updatedAt,isDeleted}}] }
 4. 服务器按 UUID 幂等 upsert（唯一索引），返回确认 + 服务器时间
 5. 事务内：成功行 SET SYNCED, lastSyncedAt=服务器时间
           失败行 SET FAILED, retryCount+1, lastSyncError
 6. 有剩余回 2；失败 Result.retry()（指数退避）
```

**幂等性两层保证**：客户端 UUID 终身不变 + 服务器每表 UUID 唯一索引 upsert。
"超时但服务器其实已写入 → 重推"不会产生第二笔记录。

### 4.3 Pull（服务器 → 本地，先 Push 后 Pull）

```
 1. GET /api/sync/pull?since=lastPullAt（游标在 sync_state）
 2. 返回所有 updatedAt > since 的行（含 tombstone）
    游标 = 服务器时间 − 5 分钟重叠窗，靠幂等 upsert 容忍重叠，防漏晚到的行
 3. 合并：本地无此行 → 插入(SYNCED)
        本地 SYNCED 且远端较新 → 覆盖本地(SYNCED)
        本地 PENDING → 本地优先（未上云的本地是更新真相，下次 push 覆盖服务器）
 4. lastPullAt = 服务器水位
```

**冲突规则 v1 = 待推送的本地数据胜出**（本质按 updatedAt 最后写入胜出）。
边界：两台设备同时改同一行，早改的被覆盖。v1 单设备风险≈0；真多设备时只改合并规则这一处，架构不推翻。

### 4.4 异常场景矩阵

| 场景 | 系统行为 | 靠什么 |
|---|---|---|
| 无网络点"保存销售" | 立即成功，界面照常 | 写库与网络解耦 |
| 同步中断网 | 整批回 PENDING | 批次级成败，不半推 |
| 同步中 App 被杀 | 个别行停 SYNCING | Worker 启动第 1 步统一重置 |
| 手机重启/被系统杀 | 任务自动恢复 | WorkManager 自带持久化 |
| 超时但服务器已写入 | 重推同批 | UUID + 服务器唯一索引幂等 |
| 服务器 5xx | FAILED 退避重试 | retryCount 累积，失败原因设置页可见 |
| 删除后同步 | 服务器同删，不复活 | tombstone 随 push 上行 |
| 手机时钟不准 | updatedAt 乱序 | 记录服务器时钟偏移校正；单设备影响极小 |

### 4.5 与服务器松耦合

客户端只认识三个接口：注册设备（拿 token）/ push / pull。DTO 只含业务字段 + UUID + updatedAt + isDeleted。服务器内部选型（PostgreSQL/SQL Server）与客户端无关。

## 5. 备份 ≠ 同步

同步防"不一致"，不防"错的结论被同步"（误删会同步上云；服务器盘也会坏）。Phase 7 独立做：

1. **手动导出 DB 文件**：先 `PRAGMA wal_checkpoint`（Room 默认 WAL，直接复制 .db 会丢最近写入），再 SAF 存到用户选的位置。
2. **导出 CSV**：销售/进货/支出流水，商家能直接打开看。
3. **本地自动滚动备份**：WorkManager 每日备份到 App 私有目录，保留最近 7 份。

恢复 = 选备份 → 校验版本 → 替换 DB → 重启 App（破坏性操作，确认对话框）。

## 6. 项目目录结构

单模块单包树，无 Base 类、无 DI。每个功能模块固定六件套：
`Fragment + ViewModel + Repository + Entity + Dao + XML`

```
app/src/main/java/com/example/accounting/
├── AccountingApp.java                  // Application：全局唯一 Database、Executor、各 Repository
├── data/
│   ├── db/
│   │   ├── AppDatabase.java            // version=1, exportSchema=true
│   │   ├── Migrations.java             // MIGRATION_1_2()… 逐版本累加
│   │   ├── entity/                     // Product, Category, Sale, SaleItem, Purchase,
│   │   │                               // PurchaseItem, Expense, StockMovement, SyncState
│   │   └── dao/                        // ProductDao, SaleDao, …（一表一 DAO）
│   ├── repository/                     // ProductRepository, SaleRepository, PurchaseRepository,
│   │                                   // ExpenseRepository, StatisticsRepository, BackupManager
│   └── sync/                           // ← 同步子系统整个关在这一个包
│       ├── SyncStatus.java             // 常量：SYNCED/PENDING/SYNCING/FAILED
│       ├── SyncScheduler.java          // 只做一件事：把任务排进 WorkManager
│       ├── SyncWorker.java             // WorkManager 入口（薄壳）
│       ├── SyncEngine.java             // push/pull 编排、分批、状态机、冲突合并
│       ├── SyncApi.java                // HTTP（Phase 9 出现）
│       └── dto/                        // PushBatch.java 等
├── ui/
│   ├── main/MainActivity.java          // BottomNavigationView + 5 Fragment
│   ├── home/                           // 今日概览 + 超大"记一笔"按钮
│   ├── sales/                          // SalesFragment + SaleEditActivity + VM + Adapter
│   ├── inventory/                      // InventoryFragment + ProductEditActivity + VM + Adapter
│   ├── statistics/                     // 日/月汇总，SQL 聚合
│   └── settings/                       // 备份、同步状态（PENDING/FAILED 条数）
└── util/
    ├── MoneyUtil.java                  // 1250 ↔ "12.50"
    ├── QuantityUtil.java               // 1700 ↔ "1.7"
    └── TimeUtil.java                   // 毫秒 ↔ "10-03 14:05"
res/layout/                             // activity_main.xml, fragment_sales.xml, item_product.xml…
```

UI 面向大龄/非技术用户：底部 5 个大图标 Tab；开单页商品网格点击即加购（少碰键盘）；数字键盘大按钮弹层；只有破坏性操作才弹窗。

## 7. 阶段计划（15 Phase）

| Phase | 内容 | 备注 |
|---|---|---|
| 1 | 工程骨架：改包名、加依赖、MainActivity + 5 空 Fragment + 底部导航、AccountingApp | 复用现有 AS 工程 |
| 2 | Room：全部实体 + DAO + AppDatabase + 预置分类 | 同步列 Phase 2 就建好，后期免 Migration |
| 3 | 商品管理（六件套走通第一遍） | |
| 4 | 销售记录（开单 + 列表 + 作废） | |
| 5 | 库存管理（含 stock_movements 台账） | |
| 6 | 统计 | 纯 SQL 聚合 |
| 7 | 本地备份/导出 | 见第 5 节 |
| 8 | Repository/DAO 层统一走查 + 单元测试 | 原计划的"Repository 层"已并入 3–6（每模块自带六件套） |
| 9 | Sync Queue（SyncStatus/Engine 骨架，无网络） | |
| 10 | WorkManager 接入 | |
| 11 | ASP.NET Core API | 客户端只依赖 3 个接口 |
| 12 | 增量同步（push/pull/幂等/冲突） | |
| 13 | 异常恢复（崩溃/断网/重启全场景演练） | |
| 14 | OCR（辅助功能，永不阻塞手动录入） | |

原则：Phase 1–10 期间 App 就是一个完全可用的纯离线记账软件，每阶段交付能跑的东西。

---

*本文档由架构设计讨论定稿；修改任何设计决定请先更新 Decision Log。*
