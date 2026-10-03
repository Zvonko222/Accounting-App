# 售货记账

面向小型商户/个人卖家的**本地离线记账软件**（Android，Java + XML）。

**Local-first**：所有功能 100% 离线可用，本地 Room/SQLite 是唯一事实源。
**定期备份**是本项目的数据安全核心：每日自动备份（本机 7 份滚动）+ 手动导出/恢复 + CSV 导出。

## 文档索引

| 文档 | 内容 |
|---|---|
| **[PROJECT.md](PROJECT.md)** | **项目说明文档：技术栈清单（含版本）、架构、模块、数据流、目录、构建测试指南** |
| [ARCHITECTURE.md](ARCHITECTURE.md) | 总体设计依据：数据库表结构、同步列预留、事务边界、异常恢复矩阵 |
| [DEVLOG.md](DEVLOG.md) | 逐阶段开发日志：每步改了什么/为什么/怎么测，含模拟器实测记录 |


## 功能

- 🛒 开单：商品网格点选加购，+/- 调量，支持称重小数（1.7 斤）、整单优惠、收款方式、记账时间可补录
- 📦 商品管理：分类、期初库存、条码、库存预警、软删除停用
- 🚚 进货：更新库存与最近进价，供应商可选
- 💰 支出：房租/水电/运费/工资/损耗
- 📋 流水：本月销售/进货/支出，单据详情，**错单作废**（账目不可涂改，库存自动冲回）
- 📊 统计：今日/本月卡片、毛利估算（按成交时的进价快照）、近 7 天柱状图、热销榜
- 🗂 库存台账：每次库存变动（销售/进货/盘点/作废）都有记录，可回答"库存为什么是这个数"
- 📷 拍照导入：拍进货单自动识别品名/数量/进价（ML Kit 本机离线识别，逐行确认后才入库，缺商品自动建档）
- 📱 桌面小组件：不用打开 App，一眼看到今日销售额和单数，按钮一键开单
- ☁️ 云同步（可选）：局域网自建服务器增量备份，离线先记账、有网自动传、断点自动重试
- 💾 备份：**每日 WorkManager 自动备份**（重启/被杀不丢任务）、SAF 导出、校验式恢复、CSV

## 构建

```bash
./gradlew :app:assembleDebug        # 打包（JDK 17+，Android Studio 直接打开也行）
./gradlew :app:testDebugUnitTest    # 16 个单元测试
```

APK 输出：`app/build/outputs/apk/debug/app-debug.apk`（minSdk 24 / targetSdk 36）

## 架构（一句话版）

```
Activity/Fragment ──► ViewModel ──► Repository ──► Room DAO ──► SQLite
                                        │
                                        └── 唯一写入口；跨表操作包在事务里
后台：WorkManager（每日备份 Worker）
```

三条铁律：**UI 只读本地库；Repository 是唯一写入口；所有删除都是软删除**。
完整设计（数据库表结构、同步列预留、事务边界、异常恢复矩阵）：[ARCHITECTURE.md](ARCHITECTURE.md)。
逐阶段开发记录与测试方法：[DEVLOG.md](DEVLOG.md)。

## 目录

```
app/src/main/java/com/example/accounting/
├── AccountingApp.java          # 全局 Database / 单线程写 Executor / 各 Repository
├── data/
│   ├── db/entity/              # 9 张表 + 同步列基类 SyncEntity（9 列，为未来云同步预留）
│   ├── db/dao/                 # 一表一 DAO
│   ├── repository/             # Product / Sale / Purchase / Expense
│   │   └── backup/             # BackupManager + Worker + Scheduler（备份子系统）
│   └── model/                  # 常量（PayMethod/ExpenseType）与查询结果 POJO
├── ui/                         # main / home / sales / inventory / purchase / statistics / settings
└── util/                       # MoneyUtil(分↔元) / QuantityUtil(×1000) / TimeUtil / AppRestarter
```

每个功能模块固定六件套：`Fragment + ViewModel + Repository + Entity + Dao + XML`。

## 数据安全要点（为什么敢用它记账）

1. 金额一律 `long` 分、数量一律 ×1000 整数、时间一律毫秒 —— **全程无浮点**
2. 记销售 = 主表 + 明细快照 + 扣库存 + 记台账，**一个事务，崩溃全回滚**
3. 删除/作废 = 软删除墓碑，历史账目永不被涂改
4. 数据库升级必须写 Migration，schema 留档于 `app/schemas/`，**禁止破坏性迁移**
5. 备份前 `PRAGMA wal_checkpoint` 合并 WAL，防止直接拷贝丢最新写入
6. 恢复先校验（SQLite 魔数 + 版本兼容）再替换，校验不过原库不动

## 未来路线（已预留，不急）

云同步（本地 → ASP.NET Core API，增量 + UUID 幂等 + 墓碑防复活）：表结构已含全部所需字段（`syncStatus` 等 9 列），届时只需新增 `data/sync/` 包，无数据库迁移。OCR 拍单识别为辅助功能。
