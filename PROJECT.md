# 售货记账 — 项目说明文档

> 面向小型商户/个人卖家的本地离线记账软件（Android）。
> 本文档回答三个问题：**用了什么技术、为什么这么用、代码在哪里**。
> 配套文档：[ARCHITECTURE.md](ARCHITECTURE.md)（设计依据）· [DEVLOG.md](DEVLOG.md)（逐阶段开发日志与实测记录）· [README.md](README.md)（快速上手）

---

## 1. 项目概况

| 项 | 内容 |
|---|---|
| 产品名 | 售货记账 |
| 包名 / applicationId | `com.example.accounting` |
| 定位 | 本地离线记账（Local-first），核心数据安全手段是**定期备份** |
| 状态 | 可运行、可日常使用；32/32 单元测试通过；lint 无错误 |
| 产物 | `app/build/outputs/apk/debug/app-debug.apk`（约 30MB，其中约 20MB 是离线 OCR 中文识别模型） |
| 环境要求 | Android Studio + JDK 17+；运行设备 Android 7.0（API 24）及以上 |

---

## 2. 技术栈清单

### 2.1 语言与运行时

| 项 | 版本 | 说明 |
|---|---|---|
| 语言 | **Java 11**（`compileOptions` 指定） | 全部业务代码为 Java，朴素写法，无 Kotlin。开发机用 JDK 21 跑 Gradle 没有问题 |
| UI 技术 | **XML Layout + Activity/Fragment + RecyclerView** | 不用 Compose。每页一个 XML，打开即见结构 |
| 构建脚本 | **Kotlin DSL**（`build.gradle.kts`） + **版本目录**（`gradle/libs.versions.toml`） | 依赖版本统一管理；脚本语言与 App 代码语言无关 |

### 2.2 Gradle / Android 构建

| 项 | 版本 |
|---|---|
| Gradle Wrapper | 9.3.1 |
| Android Gradle Plugin | 9.1.1 |
| compileSdk / targetSdk | 36 |
| minSdk | 24（Android 7.0，覆盖绝大多数在用机型） |

### 2.3 核心依赖库（全部来自 AndroidX / Google，版本见 `gradle/libs.versions.toml`）

| 库 | 版本 | 用在哪 | 为什么用它 |
|---|---|---|---|
| **Room**（runtime + compiler 注解处理器） | 2.6.1 | 本地数据库 | SQLite 之上的官方 ORM：DAO 接口 + 编译期 SQL 校验 + `LiveData` 自动刷新 + 事务 API；`exportSchema=true` 把表结构留档到 `app/schemas/`，是写 Migration 的依据 |
| **WorkManager** | 2.9.1 | 每日自动备份 | 系统级持久化后台任务：手机重启、App 被杀都不丢任务；不依赖 Google Play 服务，国产机型可用 |
| **Lifecycle ViewModel** | 2.8.7 | 每个页面 | 界面状态与生命周期解耦；屏幕旋转不丢数据 |
| **Lifecycle LiveData** | 2.8.7 | 查询结果流 | Room 查询返回 LiveData，数据一变列表自动刷新——"保存后界面自动更新"就是它 |
| **Material Components** | 1.14.0 | 全部 UI | Material 3 组件：底部导航、卡片、Chip、弹窗 |
| **AndroidX AppCompat / Activity / ConstraintLayout / RecyclerView** | 1.8.0 / 1.13.0 / 2.2.2 / 1.3.2 | 基础设施 | 标配套件；ViewBinding 开启（`buildFeatures.viewBinding`），替代 `findViewById` |
| **OkHttp + Gson** | 4.12.0 / 2.11.0 | **当前未使用** | 为未来云同步（Phase 9+）预留的 HTTP/JSON 库；本期不联网 |
| **ML Kit 文字识别（bundled 中文）** | 16.0.1 | 拍照导入进货单 | 模型打进 APK、**运行时完全离线**、不依赖 Google 服务框架；全项目唯一"黑盒"依赖，业务解析是可单测的纯 Java（`OcrLineParser`） |
| **ExifInterface** | 1.3.7 | 拍照导入 | 读取照片旋转方向，摆正后再识别 |
| **JUnit 4**（+ androidx test / espresso） | 4.13.2 | 单元测试 | 测 `MoneyUtil` / `QuantityUtil` / `SaleCalculator` / `OcrLineParser` 四个纯函数类 |

### 2.4 明确不用的技术（以及为什么）

| 不用 | 理由 |
|---|---|
| Kotlin / Compose | 项目首要目标是**可读可学**：Java + XML 对传统 Android 学习者最直白 |
| Coroutines / RxJava / Flow | 线程模型用"单线程写 Executor + LiveData"已完整解决，少一层魔法 |
| Hilt / Dagger | 依赖注入用 `AccountingApp` 手工组装（4 个 Repository 各 new 一次），看得见 |
| 多模块 / Clean Architecture 分层包 | 单模块单包树，六件套一个功能一个目录；规模不需要更多 |
| 第三方图表库（MPAndroidChart 等） | 统计页柱状图 `BarChartView` 用 Canvas 手绘，代码 100 行内可读可控 |
| 双列布局数据库框架（GreenDAO/Realm 等） | Room 是官方推荐且生态最稳 |

---

## 3. 架构设计

### 3.1 分层

```
Activity / Fragment / XML / RecyclerView     只做：显示数据、收集输入
        ↓ 调用
ViewModel（持 LiveData 状态）                 只做：状态、购物车草稿、委托写操作
        ↓ 调用
Repository（Product/Sale/Purchase/Expense）  只做：数据来源 + 事务边界（全 App 唯一写入口）
        ↓
Room DAO ──► SQLite（accounting.db，唯一事实源）
        └──► LiveData 自动回推 UI
```

**三条铁律**（看懂这个就看懂了整个项目）：

1. **UI 永远只读本地库** —— 界面数据 100% 来自 Room 的 LiveData，没有"先查网络再显示"的路径，离线可用是架构的自然结果。
2. **Repository 是唯一写入入口** —— Activity/ViewModel 永不直接调 DAO 写数据；跨表修改（如记销售 = 4 类写入）全部包在一个 `runInTransaction` 里，中途崩溃整体回滚。
3. **所有删除都是软删除** —— 业务数据永不物理 DELETE，打 `isDeleted` 墓碑，历史账目可追溯、未来同步不会"复活"旧数据。

### 3.2 线程模型（Java 下的简单方案）

| 操作 | 机制 |
|---|---|
| 读 | DAO 返回 `LiveData`，Room 自带后台线程执行，表变化自动通知 |
| 写 | `AccountingApp` 持有全局**单线程** `ExecutorService`，Repository 在其上排队执行 |
| 结果通知 | Repository 完成 → `mainHandler.post` 切回主线程 → 回调 Toast/刷新（**Toast 只能在主线程创建，这是实测踩过的坑，见 DEVLOG 补丁 2**） |
| OCR 识别 | `OcrEngine` 用独立单线程解码图片（大图解码不能在主线程），ML Kit 的回调自带线程调度，结果统一切回主线程 |

### 3.3 每个功能模块固定"六件套"

`Fragment/Activity + ViewModel + Repository + Entity + DAO + XML`
看文件名即知职责，无 Base 类、无接口套娃。

---

## 4. 数据库设计概要

9 张表（完整字段定义见 [ARCHITECTURE.md](ARCHITECTURE.md) 第 2 节，机器可读版本在 `app/schemas/1.json`）：

| 表 | 职责 |
|---|---|
| `categories` | 商品分类（首启预置 5 个，名称唯一约束） |
| `products` | 商品 + 当前库存 + 售价/最近进价 |
| `sales` / `sale_items` | 销售单主子表；明细含**名称/单价/成本/小计四个快照**，历史账本不可变 |
| `purchases` / `purchase_items` | 进货单主子表（对称设计） |
| `expenses` | 房租水电等支出 |
| `stock_movements` | **库存台账**：每次变动一笔（销售/进货/盘点/期初/作废冲回），恒等式"当前库存 = 期初 + Σ变动" |
| `sync_state` | 本地内部键值（上次备份时间等） |

**全局约定**：金额一律 `long` 分（12.50 元存 1250）· 数量一律 ×1000 整数（1.7 斤存 1700）· 时间一律毫秒时间戳 · 主键 UUID 字符串 · 每张业务表内嵌 9 列同步状态（`syncStatus/updatedAt/isDeleted/...`，为未来云同步预留，本期不用但已就位）。

**换算只发生在三个工具类里**（配有单元测试）：`util/MoneyUtil`（分↔元）、`util/QuantityUtil`（×1000）、`util/TimeUtil`（毫秒↔显示）。

---

## 5. 功能模块与页面

| 模块 | 页面 | 文件位置 |
|---|---|---|
| 主框架 | `MainActivity`：底部 5 Tab，add/show/hide 切换保留页面状态 | `ui/main/` |
| 首页 | `HomeFragment`：今日 4 卡片 + 库存预警 + 三个大按钮（销售/进货/支出入口） | `ui/home/` |
| 开单 | `SaleEditActivity`：商品网格点选 → 行编辑弹窗（单价+数量+实时小计）→ 购物车（+/- /点行再编辑/"本单共 N 种"/**整单优惠**）→ 收款方式 Chip → **记账时间可改（补录）** → 完成 | `ui/sales/` |
| 流水 | `SalesFragment`：销售/进货/支出三 Tab；**时间范围筛选**（今天/近7天/本月/上月，`Transformations.switchMap`）；**显示已作废开关**；**行内商品摘要**（SQL GROUP_CONCAT）；详情弹窗（明细行）；**修改**（跳编辑模式）；**作废**（库存自动冲回）；支出长按删除 | `ui/sales/` |
| 进货 | `PurchaseEditActivity`：同开单（进价预填上次进价）+ 供应商 + **记账时间可改** | `ui/purchase/` |
| 库存 | `InventoryFragment` + `ProductEditActivity`：搜索、新增/编辑/停用（软删）、盘点修正、库存台账弹窗 | `ui/inventory/` |
| 支出 | `ExpenseDialog`：类型 Chip 免打字 + 金额 | `ui/expense/` |
| 拍照导入 | `OcrImportActivity`：拍照/相册/图片分享 → ML Kit 本机识别 → 逐行勾选确认（可改数量进价）→ 自动建档 + 一个事务保存进货单 | `ui/purchase/` + `data/ocr/` |
| 桌面小组件 | `TodayWidgetProvider`：2x2"今日经营"看板（今日销售额/单数）+ 一键开单按钮；开 App/记完账主动刷新 | `widget/` |
| 云同步 | `SyncEngine/SyncApi/SyncWorker`：8 表增量 push/pull、UUID 幂等、本地待上行胜出；设置页配置服务器地址与密钥 | `data/sync/` + `server/` |
| 统计 | `StatisticsFragment`：本月 5 卡片（毛利按成交时进价快照估算）+ 近 7 天柱状图（手绘 `BarChartView`）+ 热销榜 | `ui/statistics/` |
| 设置 | `SettingsFragment`：立即备份 / 导出备份文件 / 从备份恢复 / 导出 CSV / 上次备份时间 / **分类管理**（新增+软删，唯一约束重名校验） | `ui/settings/` |

**修改已保存单据**的实现：详情弹窗"修改" → 编辑页回填 → 保存时一个事务里**作废旧单 + 重开新单**（`SaleRepository.editSale` / `PurchaseRepository.editPurchase`）。账本不可涂改，轨迹完整。

---

## 6. 备份子系统（本项目数据安全核心）

```
每日自动：App 启动 → BackupScheduler 幂等注册 WorkManager 周期任务
                          → 系统择机唤醒 BackupWorker（重启/被杀不丢）
                          → performLocalBackup()
手动入口：SettingsFragment → SettingsViewModel → BackupManager
```

`BackupManager` 四个能力与两个关键保护：

1. **本机滚动备份**：checkpoint WAL → 复制 `.db` 到私有目录 `files/backups/` → 保留最近 7 份 → 记录时间到 `sync_state`
2. **导出备份文件**（SAF，无存储权限）到用户选的位置
3. **恢复**：先复制到临时文件校验（SQLite 魔数 + user_version 兼容性）→ 关闭 Room 连接 → 替换文件 → 删 `-wal/-shm` 残留 → 重启进程（`AppRestarter`）
4. **导出销售 CSV**（UTF-8 BOM，Excel 直开）

保护 A：复制前必做 `PRAGMA wal_checkpoint(TRUNCATE)`（WAL 里是最新写入，直接拷 `.db` 会丢账）。
保护 B：校验不过就**不动原库**；备份写文件 `getFD().sync()` 强制落盘，断电也完整。

**备份 ≠ 同步**：同步防"多端不一致"，备份防"数据永久丢失"（数据库损坏、误删、误清数据），二者独立。

---

## 7. 拍照导入（OCR）技术方案

### 7.1 链路

```
入口（三种）：首页按钮 / 系统相机拍照（FileProvider） / 其他应用"分享图片"（ACTION_SEND）
        ↓
OcrEngine：ContentResolver 读图 → 按 EXIF 旋转摆正 → 超边等比降采样（≤2048px）
        ↓
ML Kit 中文文字识别（TextRecognizer + ChineseTextRecognizerOptions）
        ↓ 按行取原始文本
OcrLineParser.parseAll()：每行拆出 品名 / 数量 / 进价（纯 Java，11 个单测）
        ↓
确认界面：逐行勾选、点行改数量进价、无效行灰显
        ↓
PurchaseRepository.recordPurchaseWithNewProducts()：一个事务
   ├─ 按品名查找已有商品（findByName）；没有 → 自动建档
   │   （进价=识别值、售价 0 待补录、单位"个"、库存 0）
   └─ 标准进货入库：库存 + 台账 + 最近进价更新；任何一步失败整体回滚
```

### 7.2 关键技术决策

| 决策 | 理由 |
|---|---|
| ML Kit **bundled** 版（`text-recognition-chinese`）而非 Google 服务版 | 模型打进 APK，运行时**零网络**、不依赖 GMS（国产机可用）——符合 local-first；代价是 APK 约 +20MB |
| 识别/解析分离 | ML Kit 是唯一无法手写的"黑盒"（只负责"图→文字"）；"文字→品名/数量/进价"是自己的纯 Java（`OcrLineParser`），可单测、可维护、可按自己账本习惯调整 |
| 0 进价的行默认无效 | 赠品行/识别噪声，需人工在确认界面放行，绝不静默记错账 |
| 自动建档放在**同一个事务** | 不可能出现"建了商品但没有进货记录"的半截数据 |
| 不申请存储/相机权限 | 相册走 SAF（选中的 URI 自带临时授权）；拍照走系统相机 + FileProvider（照片写自己的 cache 目录）；分享进来的图片自带授权 |

### 7.3 解析器文法（`OcrLineParser`）

能解析的行（清洗全角→半角、去货币符号）：

| 原始行 | 解析结果 |
|---|---|
| `可乐 2 3.50` | 可乐 ×2，进价 3.50 |
| `可乐x2 3.5` | 数量粘在名字上 |
| `薯片 ×3 12.00元` | 独立数量标记 `×3` |
| `苹果 1.5 6.00` | 数量支持小数（称重） |
| `可乐 3.50` | 只有一个数字 → 当进价，数量按 1 |
| `¥3.50 可乐 2` | 价格在前也能解 |

歧义启发式：两个数字无法分清"数量/进价"时，**带小数点的更像价格**（金额常带分位，数量多为整数），分不出再按位置（最后=进价，倒数第二=数量）。
丢弃的行：表头表尾停用词（合计/金额/数量/日期…）、纯数字、品名短于 2 字、没有价格的行——**宁可丢弃让用户手补，不猜着记错账**。

### 7.4 排障

`adb logcat -s OcrEngine:*`：解码尺寸、原始行数、失败原因（含异常栈）。
`adb logcat -s BackupManager:*`：备份失败原因。

---

## 8. 目录结构

```
app/src/main/java/com/example/accounting/
├── AccountingApp.java          # 全局 Database、单线程写 Executor、各 Repository、备份调度
├── data/
│   ├── db/
│   │   ├── AppDatabase.java    # Room 单例；version=1；exportSchema；首启预置分类
│   │   ├── Migrations.java     # 迁移预留（改表必须写 Migration，禁止破坏性迁移）
│   │   ├── entity/             # 10 个实体（含同步列基类 SyncEntity）
│   │   └── dao/                # 8 个 DAO + Relation POJO（SaleWithItems/Summary 等）
│   ├── model/                  # 常量（PayMethod/ExpenseType）+ 查询结果 POJO + 购物车行 + OcrLine
│   ├── ocr/OcrEngine.java      # 拍照导入识别引擎（解码/EXIF/ML Kit）
│   ├── repository/             # Product/Sale/Purchase/Expense/Category + SaveCallback
│   │   └── backup/             # BackupManager + BackupWorker + BackupScheduler（备份子系统）
│   └── sync/SyncStatus.java    # 同步状态常量（本期恒 PENDING，预留）
├── ui/                         # main / home / sales / inventory / purchase（含 OcrImportActivity）/ expense / statistics / settings
└── util/                       # MoneyUtil / QuantityUtil / TimeUtil / SaleCalculator / OcrLineParser / AppRestarter
app/src/test/java/...           # 32 个单元测试（金额、数量、销售计算、OCR 行解析、备份恢复校验）
app/schemas/1.json              # Room 表结构留档（Migration 依据）
gradle/libs.versions.toml       # 全部依赖版本
```

---

## 9. 构建与测试

```bash
./gradlew :app:assembleDebug        # 打 Debug APK
./gradlew :app:testDebugUnitTest    # 跑 32 个单元测试
./gradlew :app:lintDebug            # lint 检查
```

**依赖仓库镜像**：`settings.gradle.kts` 在 `google()`/`mavenCentral()` 之前配置了阿里云镜像（`maven.aliyun.com/repository/google` 与 `/central`）。国内网络直连 Google Maven 常出现 TLS 握手失败（PKIX 错误），镜像内容相同、仅改变下载来源，对 App 本身无任何影响；海外网络环境可把镜像行删掉。

已验证的场景（详见 DEVLOG 补丁记录）：离线记账全流程、错单作废与库存冲回、修改单据、备份/恢复/CSV、模拟器 UI 自动化走查、拍照导入端到端（真实 ML Kit 识别 + 自动建档 + 事务入库）。

---

## 10. 设计原则（贯穿全部代码）

**Understandable > Clever · Readable > Short · Explicit > Hidden**

- 无 Base 类族、无反射魔法、无一层套一层的间接调用；宁可多个小文件，不写巨型类
- 注释解释"为什么"（事务为什么这样包、WAL 为什么要先 checkpoint、Toast 为什么必须主线程）
- 无魔法数字：状态用常量类（`SyncStatus`、`PayMethod`、`StockMovement.TYPE_*`）
- 关键业务流程在代码结构里直接可读：`insertSaleLocked` 里 1 主表 → 2 明细快照 → 3 扣库存 → 4 记台账
- 每次修改按 What/Why/Files/How to test 记录进 [DEVLOG.md](DEVLOG.md)

---

## 11. 未来路线（已预留 / 未实现）

- **云同步**（本地 → ASP.NET Core API，增量 + UUID 幂等 + 墓碑防复活）：所有表已内嵌同步 9 列，届时只需新增 `data/sync/` 包与 `SyncEngine`，无数据库迁移；OkHttp+Gson 依赖已就位
- **条码扫描开单**：CameraX + ML Kit 条码识别，扫码即加购（与拍照导入同一 ML Kit 家族，架构可复用）
