# DEVLOG — 开发日志

> 按阶段记录：改了什么 / 为什么 / 关键文件 / 数据流 / 怎么测。
> 设计依据见 [ARCHITECTURE.md](ARCHITECTURE.md)。

**构建状态**：`./gradlew :app:testDebugUnitTest :app:assembleDebug` ✅（16/16 单元测试通过，APK ≈ 8.1MB）

---

## Phase 1 — 工程骨架

**What changed**：包名 `com.example.myapplication` → `com.example.accounting`；引入 Room / WorkManager / Lifecycle / OkHttp+Gson（后两者为未来同步预留，本期未使用）；`AccountingApp`（Application）、`MainActivity`（底部导航 + 5 Fragment，add/show/hide 切换保留页面状态）、全套 Material3 主题 / 颜色 / 字符串 / 矢量图标 / 导航菜单。

**Why**：页面用 add+show 而不是 replace，切换 Tab 保留滚动位置，对不熟悉手机的用户更友好。ViewBinding 开启（`buildFeatures.viewBinding`），替代 findViewById，无其他魔法。

**Files**：`app/build.gradle.kts`、`gradle/libs.versions.toml`、`AccountingApp.java`、`ui/main/MainActivity.java`、`res/layout/activity_main.xml`、`res/menu/bottom_nav_menu.xml`、`res/values*`、各 `ui/*/Fragment`。

**How to test**：安装启动 → 底部 5 个 Tab 可切换且不丢状态。

---

## Phase 2 — Room 数据库

**What changed**：9 张表全部落地（categories / products / sales / sale_items / purchases / purchase_items / expenses / stock_movements / sync_state）；`SyncEntity` 抽象基类承载同步列组（9 列），所有业务实体继承；8 个 DAO；`AppDatabase` version=1、exportSchema=true、首次建库预置 5 个分类；`Migrations.java` 预留（当前无需迁移）。

**Why（关键设计）**：
- **同步列在 Phase 2 就建好**：未来接入云同步不用 Migration，直接可用。
- **金额 long 分、数量×1000 整数、时间 long 毫秒**：全程无浮点，见 `util/MoneyUtil`、`util/QuantityUtil`。
- **主键 UUID**：未来同步到服务器不撞号（自增 ID 只在本机有意义）。
- **SaleItem 三快照**（productName / unitPriceCents / lineTotalCents）：账本记录"当时发生了什么"，商品改名/改价不影响历史。
- **预置分类用 `RoomDatabase.Callback.onCreate`**：建库事务里直接执行 SQL（此时拿不到 DAO）。

**Files**：`data/db/entity/*.java`（10 个）、`data/db/dao/*.java`（8 个 + 2 个 Relation POJO）、`data/db/AppDatabase.java`、`data/sync/SyncStatus.java`。

**Data flow**：Entity → DAO(LiveData 查询 / 同步写) → Room → SQLite（`app/schemas/1.json` 留档）。

**How to test**：首启后 `adb shell "run-as com.example.accounting ls databases/"` 可见 `accounting.db`；categories 表有 5 行。

---

## Phase 3 — 商品管理（六件套第一次走通）

**What changed**：库存页完整功能：搜索（LiveData 换查询）、新增/编辑商品（`ProductEditActivity`，含分类下拉、期初库存、预警值、条码）、停用（软删除）、盘点修正（弹窗输入实际数量 → 差额自动入账 + 台账）、库存变动记录弹窗。

**Why**：
- **盘点 = 改库存 + 记台账一个事务**：`ProductRepository.adjustStock()` 用 `db.runInTransaction()`，两表同生同死。
- **新增商品的期初库存**也写 `TYPE_INITIAL` 台账，保证恒等式"当前库存 = 期初 + Σ变动"从第一笔起成立。
- **编辑不碰库存**：库存只能被 销售/进货/盘点 三个业务改变，避免"编辑页顺手改库存"的错账。

**Files**：`ui/inventory/InventoryViewModel.java`、`InventoryFragment.java`、`ProductAdapter.java`、`StockMovementAdapter.java`、`ProductEditActivity.java`、`res/layout/fragment_inventory.xml`、`item_product.xml`、`activity_product_edit.xml`、`dialog_adjust_stock.xml`、`dialog_stock_movements.xml`。

**Data flow**：`Fragment(UI) → ViewModel → ProductRepository → ProductDao/StockMovementDao → Room`。写操作在单线程 Executor 上排队，成败经 `SaveCallback` 回主线程 Toast。

**How to test**：新增商品"可乐"售价 3.5、期初 10 → 列表出现库存 10；盘点改成 8 → 台账出现"-2 盘点修正"；停用后列表消失、开单页也不再出现。

---

## Phase 4 — 销售 / 进货 / 支出

**What changed**：
- 开单页 `SaleEditActivity`：商品网格点击加购 → 购物车 +/- 调量（点数量可输精确值，称重用）→ 选收款方式 chip → 完成销售。
- 流水页 `SalesFragment`：销售/进货/支出三 Tab，本月数据；销售/进货点卡片弹明细详情；支持**作废**（错单不改账，作废后库存冲回）。
- 进货 `PurchaseEditActivity`：同构，另可改本单进价、填供应商；保存后商品"最近进价"更新。
- 支出 `ExpenseDialog`：类型 chip 免打字 + 金额 + 备注。

**Why（全 App 最重要的事务，`SaleRepository.recordSale`）**：
```
1. 校验：行数>0、每行数量>0
2. sales 主表：totalAmountCents 一次算定，之后永不重算
3. sale_items：写入名称/单价/成本/小计四快照
4. products：库存扣减（允许为负 = 先卖后补，UI 红色提示）
5. stock_movements：TYPE_SALE 负数台账，挂 relatedSaleId
以上 5 步包在同一个 Room 事务里：中途崩溃整体回滚，绝无"账记了库存没扣"。
```
作废 `voidSale()` 同事务：sale+items 打墓碑（isDeleted）→ 库存冲回 → TYPE_VOID_SALE 台账。**删除一律软删除**：硬删会让未来云同步把服务器旧数据推回来"复活"。

**Files**：`ui/sales/*`（ViewModel/Activity/Fragment/3 个 Adapter + 详情 Adapter）、`ui/purchase/*`、`ui/expense/ExpenseDialog.java`、`data/model/SaleCartLine.java`、`PurchaseCartLine.java`、`util/SaleCalculator.java`（纯函数，可单测）、`SaleRepository` / `PurchaseRepository` / `ExpenseRepository`。

**Data flow（记一笔销售）**：`SaleEditActivity → SaleEditViewModel(购物车=内存草稿) → SaleRepository.recordSale(事务) → Room → LiveData 自动刷新首页卡片/流水列表/库存列表`。

**How to test**：
1. 开单卖 2 个可乐 → 流水页出现 +7.00，可乐库存 8→（盘点后8）-2；
2. 作废该单 → 流水灰显"已作废"，库存 +2，台账出现"作废销售冲回"；
3. 记进货 5 个可乐 进价 2.5 → 库存 +5、商品最近进价变 2.5、统计页"进货支出"增加；
4. 记支出"水电 50" → 流水支出 Tab 出现 -50.00。

---

## Phase 5 — 库存管理

与 Phase 3/4 一体交付（库存页/盘点/台账/预警卡片在首页）。**恒等式**：`products.stockQuantityMilli = 期初 + Σ(stock_movements.changeQuantityMilli)`，任何一笔变动都有据可查。

---

## Phase 6 — 统计 + 首页

**What changed**：首页今日四卡片（销售额/单数/支出/毛利估算）+ 库存预警卡 + 三个大按钮；统计页本月五卡片 + **近 7 天柱状图（`BarChartView`，纯 Canvas，不引图表库）** + 热销榜前 5。

**Why**：统计全是 SQL 聚合（`StatisticsDao`），一天几千条流水毫秒级，不建预汇总表。**毛利用"成交当时的进价快照"估算**（`unitCostCents`），改进货价不改写历史毛利；`si.unitCostCents * si.quantityMilli / 1000` 先乘后除减少舍入误差。日分界用 `strftime(..., 'localtime')`，否则按 UTC 分组会把一天的账切成两天。

**Files**：`ui/home/*`、`ui/statistics/*`（含 `BarChartView`）、`StatisticsDao.java`。

**How to test**：记几笔不同日期的销售 → 柱状图出现当日柱；热销榜按销售额排序。

---

## Phase 7 ★核心 — 定期备份（本期重点需求）

**What changed**：
- `BackupManager`：本机备份 / 导出 / 恢复 / CSV 四件事。
- `BackupWorker` + `BackupScheduler`：WorkManager **每天自动备份一次**，App 私有目录保留最近 **7 份**，幂等注册（`ExistingPeriodicWorkPolicy.KEEP`），**手机重启/App 被杀后系统仍会按时唤醒执行**。
- 设置页 `SettingsFragment`：立即备份 / 导出备份文件（SAF 存到下载/网盘/电脑）/ 从备份恢复 / 导出销售 CSV；显示上次备份时间。

**Why（备份 ≠ 同步，ARCHITECTURE.md 第 5 节）**：
1. **复制前必须 `PRAGMA wal_checkpoint(TRUNCATE)`**：Room 默认 WAL 模式，最近的写入停在 `-wal` 文件里，直接拷 `.db` 会丢最新数据——新手必踩的坑。
2. **恢复是三步**：校验（SQLite 魔数 + 文件头 user_version 不比当前 App 新）→ `db.close()` 后替换文件 → 删 `-wal/-shm` 残留。校验不过就不动现有数据。
3. **恢复后重启进程**（`AppRestarter`：300ms 闹钟拉起启动页 + exit）：让 Room 用新文件重建连接。
4. **`copyFile` 用 `getFD().sync()` 强制落盘**：断电场景备份文件也必须完整。
5. CSV 带 BOM 头，Excel 打开不乱码；字段全加引号防逗号。

**Files**：`data/repository/backup/BackupManager.java`、`BackupWorker.java`、`BackupScheduler.java`、`ui/settings/SettingsFragment.java`、`SettingsViewModel.java`、`fragment_settings.xml`、`util/AppRestarter.java`、`AccountingApp.java`（调度入口）。

**Data flow（每日备份）**：`App 启动 → BackupScheduler 注册周期任务 → 系统择机唤醒 BackupWorker → BackupManager.performLocalBackup(): checkpoint → 复制 → 滚动删旧 → sync_state 记时间`。

**How to test**：
1. 设置页点"立即备份" → Toast 显示备份文件名；再点一次，第二份出现且第一份仍在（保留 7 份）；
2. "导出备份文件"存到下载 → 文件管理器可见；
3. 删 App 数据重装 → "从备份恢复"选刚才的文件 → 确认 → App 重启 → 数据完整回来；
4. 选一个非 SQLite 文件恢复 → Toast"备份文件无效或已损坏"，现有数据无损；
5. "导出销售流水 CSV" → 用 Excel/WPS 打开验证。

---

## Phase 8 — 单元测试

**What changed**：16 个 JUnit 测试：`MoneyUtilTest`（分↔元、非法输入、一亿上限、四舍五入）、`QuantityUtilTest`（×1000、0/负数拒绝）、`SaleCalculatorTest`（整数四舍五入 333×1.5=500、优惠钳制不为负）。

**Why**：记账软件算错一分钱都是事故；这三个纯函数类无 Android 依赖，专为此设计。

**How to test**：`./gradlew :app:testDebugUnitTest`

---

## Phase 13 — 异常恢复核验（设计层面验证）

| 场景 | 保障机制 |
|---|---|
| 保存中 App 崩溃 | Room 事务原子性：要么全成功要么全回滚，不存在半截账 |
| 断电时正在备份 | 备份写目标文件 + `fd.sync()`；主库不受影响 |
| 恢复文件是坏的 | 恢复流程先复制到临时文件校验，通过才替换，原库全程未动 |
| 数据库文件损坏 | 本机 7 份滚动备份兜底；再不行有导出的备份文件 |
| 误删/误停用商品 | 全部软删除，数据库里数据仍在，可由专业人员找回 |
| 备份任务没跑（手机长期关机） | WorkManager 持久任务，开机后自动补执行；设置页可看到"上次备份时间"，一眼发现异常 |
| 数据库升级失败风险 | schema 已留档（app/schemas/1.json）；禁用 destructiveMigration；升版本必须写 Migration |

---

## 补丁 1 — Tab 切换重叠修复 + 开单/进货行编辑（用户反馈）

**What changed**：
1. **修复 Tab 内容重叠**：屏幕旋转或进程被杀重建后，FragmentManager 会把之前所有 Fragment 原样还原（show/hide 状态不保存），全部叠在容器里；且代码里的 `pages` 表此时是空的，再点 Tab 还会 add 出重复页面。修复：重建时清掉全部旧 Fragment、以 `currentFragment` 字段替代不可靠的 `findFragmentById`、`showPage` 对同页早退。
2. **开单/进货行编辑**：点商品网格不再直接加 1，而是弹出**行编辑弹窗**（商品名 + 单价/进价 + 数量 + 实时小计预览，校验失败不关弹窗）；购物车/清单里点商品名、数量、单价同样打开该弹窗回填修改；每行显示单价；底部新增"本单共 N 种商品"。`SaleCartLine.unitPriceCents` 改为可变（支持按单改价）。

**Why**：旋转/重建是日常操作，状态管理必须可预期（清空重建比保存/恢复 show-hide 状态简单可靠）；"选择商品、确认单价和数量"是记账动作的基本盘，弹窗一次编辑两个值比多次弹窗快，实时小计让用户按下确定前就看到金额。

**Files**：`MainActivity.java`、`ui/sales/`（SaleEditActivity / SaleEditViewModel / CartAdapter / SaleCartLine / item_cart_line / activity_sale_edit）、`ui/purchase/`（PurchaseEditActivity / PurchaseEditViewModel / PurchaseCartAdapter / activity_purchase_edit）、`res/layout/dialog_edit_cart_line.xml`、`strings.xml`。

**How to test**：
1. 开单页横竖屏旋转几次 → 无重叠；切 5 个 Tab 再旋转 → 仍无重叠；
2. 开单点商品 → 弹窗显示预填单价/数量，改成 2.5 × 3 → 小计实时显示 ¥7.50 → 确定 → 购物车该行显示单价与小计，底部"本单共 1 种商品"；
3. 点购物车里的商品名 → 同弹窗回填当前值，修改后小计与合计同步更新；点 "-" 减到 0 行消失；
4. 进货同样流程，弹窗预填上次进价。

---

## 补丁 2 — 备份闪退修复 + 已保存单据可修改（用户反馈，模拟器实测验证）

**What changed**：
1. **修复备份闪退**：`SettingsViewModel.backupNow` 在后台线程直接调用回调 → `Toast.makeText` 在无 Looper 的线程抛 `RuntimeException` → 被 catch 后又走 onError 再弹 → 未捕获 FATAL。修复：`BackupManager.backupNow()` 统一在后台执行备份、**回调固定切回主线程**（与 export/restore/CSV 同一模式）；catch 里加 `Log.e("BackupManager", ...)` 永久留排障日志。
2. **已保存单据可修改**：销售/进货详情弹窗新增"修改"按钮（已作废的不显示）→ 跳转开单/进货页**回填旧单内容**（单价、数量、进价、供应商、收款方式全部可改）→ 保存。Repository 新增 `editSale` / `editPurchase`：**同一个事务里作废旧单（库存冲回 + VOID 台账）+ 按新内容重开新单**。原 recordSale/voidSale 重构为 `insertSaleLocked` / `voidSaleLocked` 事务内部步骤，三个入口共用。

**Why**：Toast 只能在主线程创建，回调线程纪律必须统一；账本不可涂改（快照不被 UPDATE），所以"修改"实现为作废+重开——旧单在流水里保留完整轨迹，"当前库存 = 期初 + Σ台账变动"恒等式不破坏。

**Files**：`BackupManager.java`、`SettingsViewModel.java`、`SaleRepository.java`、`PurchaseRepository.java`、`SaleEditActivity/ViewModel`、`PurchaseEditActivity/ViewModel`、`SalesFragment.java`、`strings.xml`。

**How to test（模拟器 Medium_Phone 实测通过）**：
1. 设置 → 立即备份：Toast 显示备份文件名，"上次备份"时间刷新，无闪退；
2. 流水 → 点销售单 → 详情弹窗"修改" → 开单页回填 → 改数量 → 保存修改 → 流水出现"已作废"旧单 + 新单；库存台账依次出现：期初/销售/作废冲回/新销售，与库存数吻合。

**Important code**：`SaleRepository.editSale`（作废+重开的单事务）；`BackupManager.backupNow`（回调线程纪律）。

---

## 补丁 3 — 功能延伸：整单优惠 / 流水增强 / 分类管理 / App 图标

**What changed**：
1. **开单页整单优惠**：合计行上方新增"优惠（元）"输入框，输入即实时重算合计（优惠不会把合计打成负数，钳制在 `SaleCalculator.orderTotalCents`）；保存与"修改"模式都会带上优惠（编辑模式回填旧单优惠）。`sales.discountCents` 字段与计算逻辑早已预留，本次只是接上 UI。
2. **流水页增强**：
   - 时间范围筛选：今天 / 近 7 天 / 本月 / 上月（Chip 单选，`Transformations.switchMap` 换绑查询）
   - "显示已作废"开关：默认开（能看到作废轨迹），关掉后列表只显示有效单
   - 列表每行新增**商品摘要**（SQL `GROUP_CONCAT` 生成 "cola×2、zhuozi×1"），不点详情就知道卖了什么
   - 支出长按删除（软删除，删除后列表消失）
3. **设置页分类管理**：新增分类（重名有友好提示，唯一约束兜底）、停用分类（软删除）；新增 `CategoryRepository`。
4. **App 图标**：替换默认安卓机器人——品牌深青绿底 + 白色账本卡片 + 柱状图前景（自适应图标，API 26+ 生效；API 24/25 的 legacy webp 图标未替换，会在 DEVLOG 记录）。

**Why**：优惠是记账高频需求且零成本（字段预留）；流水筛选与摘要是"翻账"的基本体验；分类管理补齐了商品模块最后一块只读短板。

**Files**：`SaleEditActivity/ViewModel`、`activity_sale_edit.xml`、`SalesViewModel`（Filter + switchMap 重写）、`SalesFragment`、`SaleDao/PurchaseDao/ExpenseDao`（摘要查询/删除过滤）、`SaleWithSummary/PurchaseWithSummary`（新 POJO）、`SaleListAdapter/PurchaseListAdapter/ExpenseListAdapter`、`fragment_sales.xml`、`CategoryRepository`（新）、`CategoryDao`、`AccountingApp`、`SettingsFragment/ViewModel`、`dialog_category_manage.xml`、`item_category.xml`、`CategoryManageAdapter`（新）、`ic_launcher_foreground/background.xml`、`TimeUtil`（上月边界）、`strings.xml`。

**How to test（模拟器实测通过）**：
1. 开单加购 → 优惠输 0.5 → 合计实时从 ¥3.50 变 ¥3.00 → 完成销售 → 流水行显示"现金 · cola×1"与 +¥3.00；
2. 流水点"上月" → 暂无记录；关"显示已作废" → 已作废行消失；
3. 设置 → 分类管理 → 添加 "wenju" → 列表末尾出现；重复添加 → 提示"分类已存在"，列表不重复；
4. 支出 Tab 长按一笔 → 确认 → 从列表消失。

**遗留说明**：API 24/25（Android 7.x）设备仍显示默认 legacy 图标，如需覆盖需要生成 PNG 资产（javadoc 工具链生成），待定。

---

## 补丁 4 — 拍照导入进货单（Phase 14 OCR 提前落地）

**What changed**：
1. **首页新增"拍照记进货"** → `OcrImportActivity`：拍照（系统相机 + FileProvider）或选相册图片 → `OcrEngine` 解码/EXIF 摆正 → **ML Kit bundled 中文文字识别（完全离线本机运行）** → `OcrLineParser` 解析出品名/数量/进价 → 确认界面逐行勾选（可点行改数量进价，无效行灰显不可选）→ 保存。
2. **`PurchaseRepository.recordPurchaseWithNewProducts`**：识别行在一个事务里保存——按品名查找已有商品，找不到**自动建档**（进价取识别值、售价 0 待补录、单位"个"），再走标准进货入库（库存 + 台账 + 最近进价更新）。任何一步失败整体回滚。
3. **ACTION_SEND 图片分享入口**：在其他应用里"分享图片 → 售货记账"直接进入识别（真实单据图片往往已在手机里，这是最顺手的入口）。
4. **依赖说明**：`com.google.mlkit:text-recognition-chinese:16.0.1`（bundled 版，模型打进 APK 约 +20MB，运行时零网络、不依赖 Google 服务框架——符合 local-first）；`androidx.exifinterface`（照片旋转）。OCR 是全项目唯一无法手写实现的"黑盒"，业务解析逻辑是可单测的纯 Java（`OcrLineParser`，11 个用例）。
5. **构建环境**：国内直连 Google Maven TLS 握手失败，`settings.gradle.kts` 增加阿里云镜像（内容相同，位于官方仓库之前）。

**Why**：OCR 按你最初的设计定位为**辅助录入**——识别不准可以逐行改、整单不用，手动录入永远可用；识别为 0 进价的行（赠品/噪声）默认无效需人工放行，绝不静默记错账。

**Files**：`data/ocr/OcrEngine.java`（新）、`util/OcrLineParser.java`（新）、`data/model/OcrLine.java`（新）、`ui/purchase/OcrImportActivity.java`、`OcrLineAdapter.java`（新）、`PurchaseRepository/PurchaseEditViewModel/PurchaseCartLine/ProductDao`、`activity_ocr_import.xml`、`item_ocr_line.xml`、`file_paths.xml`、`AndroidManifest.xml`、`fragment_home.xml`、`HomeFragment`、`libs.versions.toml`、`settings.gradle.kts`、`OcrLineParserTest.java`（11 用例）。

**How to test（模拟器 Medium_Phone 实测通过）**：
1. 造一张白底黑字单据图（cola 2 3.50 / zhuozi 1 20.00 / ping 5 2.50）推送进设备，经 FileProvider 分享进入识别 → 确认界面三行全部正确解析；
2. 点"导入 3 行 · ¥39.50" → 流水·进货出现"进货 · cola×2,zhuozi×1,ping×5"，合计 -¥39.50；
3. 库存：**ping 自动建档**（5个/售价¥0.00/进价¥2.50），cola/zhuozi 复用已有商品、库存增加、最近进价更新为识别值；
4. 识别失败/无文字图片 → Toast"没有识别到可用的商品行"，可重新识别或手动录入（辅助功能定位）。

---

## 补丁 5 — 记账时间可修改（补录）+ 备份校验单测

**What changed**：
1. **记账时间可改**：开单/进货页新增"记账时间"行（默认当下，点击弹日期+时间两级选择器）；补录昨天的单选过去的时间即可，统计按它归账；**修改单据时回填旧单时间**。Repository 层把未来时间钳制到现在（防手滑）。销售与进货全链路覆盖（record/edit + Locked 内部步骤）。
2. **`BackupValidationTest`（5 用例）**：钉死恢复校验安全网——合法备份放行、user_version=0 放行（交给 Room Migration）、非数据库文件拒绝、更新版本备份拒绝（防 Room 降级崩溃）、文件不存在拒绝；同时把 `validateDatabaseFile` 的"文件不存在"归一化为 `invalid_file`（界面提示一致）。

**Why**：忘账补录是记账软件的日常（昨天忘了记今天的流水）；未来时间一律钳制保证统计口径不被手滑破坏。恢复校验是"数据安全最后一道闸"，必须有用例钉死。

**Files**：`SaleRepository/PurchaseRepository`（record/edit/Locked 加 recordTimeMillis + 钳制）、`SaleEditActivity/ViewModel`、`PurchaseEditActivity/ViewModel`、`activity_sale_edit.xml`、`activity_purchase_edit.xml`、`BackupManager`（错误归一化）、`BackupValidationTest.java`（新，5 用例）、`strings.xml`。

**How to test（模拟器实测通过）**：开单 → 加购 → 点"记账时间" → 选昨天 10-02 → 完成销售 → 流水"近 7 天"出现 "2026-10-02 22:51 +¥3.50"，"今天"筛选不含此单。

---

## 补丁 6 — 桌面小组件"今日经营"

**What changed**：
1. **2x2 桌面小组件**：品牌色卡片显示**今日销售额 + 今日单数**，两个按钮一键直达"记一笔销售"/"记进货"——商户把手机放柜台上一眼看到当天生意，不用开 App。
2. **实现**：`widget/TodayWidgetProvider`（AppWidgetProvider + RemoteViews + PendingIntent）+ `widget_today.xml` 布局 + `widget_today_info.xml` 元信息；SaleDao 增加两个**同步查询**（`sumBetweenSync`/`countBetweenSync`——RemoteViews 更新不在 LiveData 世界里）。
3. **刷新点**：系统定时兜底（≥30 分钟）+ 打开 App（MainActivity.onResume）+ 记完账（保存成功回调里主动刷新）。
4. **设置页新增"添加桌面小组件"入口**：`requestPinAppWidget` 一键钉选（API 26+；旧系统提示长按桌面手动添加）。

**Why**：这是 Android Framework 的经典组件（跨进程 RemoteViews、PendingIntent 事件模型、后台线程查询约束），学习价值与商户实用价值都高，且零新权限。

**Files**：`widget/TodayWidgetProvider.java`（新）、`res/layout/widget_today.xml`、`res/drawable/widget_bg.xml`、`widget_btn.xml`、`res/xml/widget_today_info.xml`、`SaleDao`（两个同步查询）、`MainActivity`（onResume 刷新）、`SaleEditActivity`（保存后刷新）、`SettingsFragment`/`fragment_settings.xml`（添加入口）、`AndroidManifest.xml`、`strings.xml`。

**How to test**：桌面空白处长按 → 小组件 → 找"售货记账" → 添加到桌面；记一笔销售后回桌面看数字变化；点组件上的按钮直接开单。
（实测注：模拟器精简版 Launcher3 不支持 `requestPinAppWidget` 快捷钉选、拖拽自动化不稳定——真机主流桌面均支持；`APPWIDGET_UPDATE` 广播被系统保护拒绝这一行为反过来确认了 Receiver 注册正确。）

---

## 补丁 7 — 云同步全链路（Phase 9–12 收官）

**What changed**：
1. **Android 同步子系统**（`data/sync/`）：`SyncEngine`（push 8 表脏行 / pull 增量游标 / 冲突合并"本地待上行胜出"）、`SyncApi`（OkHttp+Gson，密钥头 `X-Sync-Key`）、`SyncScheduler`（保存后即时请求 + 每 6 小时兜底，NetworkType.CONNECTED 约束 + 指数退避）、`SyncWorker`；8 个 DAO 补同步方法（脏行/批量状态/远程 upsert）。v1 简化：不打 SYNCING 中间态（服务端幂等使重推安全，崩溃后行仍 PENDING 自动重推）。
2. **设置页"云同步（可选）"卡**：服务器地址 + 访问密钥 + 保存 + 立即同步 + 状态行（上次同步时间/失败原因/待上行行数）。
3. **服务端**（`server/AccountingSyncServer`，ASP.NET Core 最小 API，**零第三方依赖**）：文档式同步存储——不理解业务字段，按 `(deviceId, table, rowId)` UPSERT 幂等存取整行 JSON；pull 按 updatedAt 增量返回含墓碑；存储为 JSON 文件（临时文件+原子替换），将来换 PostgreSQL 只替换存储函数、协议与客户端不动。
4. **修了两个真 bug**：主线程写库崩溃（保存配置移到写线程）；Gson payload 的 LinkedTreeMap→JsonElement 强转 ClassCastException（pull 中断根因）。

**Why（与你的使用模式对齐）**：所有信息始终先存本机（离线全功能），服务器只是备份镜像——在店内局域网时自动增量上传；不在内网/用流量时任务在 WorkManager 排队，网络恢复自动续传；服务器宕机只影响"备份"，完全不影响记账。

**Files**：`data/sync/`（Engine/Api/Scheduler/Worker/dto 全新）、8 个 DAO、`AccountingApp`、`MainActivity`、`AndroidManifest.xml`（usesCleartextTraffic）、`SettingsFragment/ViewModel`、`fragment_settings.xml`、`strings.xml`、`server/`（新目录）。

**How to test（模拟器 + 本机服务端端到端实测通过）**：
1. 启动服务端：`cd server/AccountingSyncServer && dotnet run --urls http://127.0.0.1:5080`（模拟器内地址为 `http://10.0.2.2:5080`）；
2. App 设置页填地址+密钥（默认 `change-me-key`）→ 保存 → 立即同步 → 状态行变"上次同步：…"，服务端收到全部 40 行（8 表，幂等合并）；
3. curl 向服务器推一条新商品 → App 再同步 → 库存页出现该商品且字段与 payload 一致（**下行回流验证**）；
4. 错误密钥 → 401；服务端宕机 → App 一切功能正常，任务自动重试。

**已知边界**：明文 HTTP 仅适合局域网（公网部署必须加 HTTPS/反向代理）；重装 App 后 deviceId 变化，旧服务器数据不会自动回流（重装恢复请走备份文件）——多设备共享账本（店铺账号）是下一阶段路线。

---

## 补丁 8 — 六项体验反馈修复

1. **状态栏遮挡**：targetSdk 35+ 强制 edge-to-edge，五个页面根布局加 `fitsSystemWindows`（Insets 监听方案在 FragmentContainerView 上不生效，声明式方案稳定，截图验证通过）。
2. **分类自定义触手可及**：商品编辑页分类下拉旁加"+"按钮，弹窗输入即可新增并自动选中（不必去设置页）。
3. **开单/进货页商品按分类筛选**：网格上方一排"全部 / 各分类"Chip（`ui/common/CategoryFilter` 三页共用同一逻辑），货物多了好找货。
4. **库存页同样加分类筛选**，与搜索叠加生效。
5. **"作废"按钮从流水列表移除**：移入单据详情弹窗（点单 → 修改/作废/关闭），列表不再出现让人生疑的"作废"字样；作废仍有二次确认。
6. **拍照识别增强**：解析器 v2——支持"数量 品名 价格"顺序、"3.5x2"写法、独立单位字（元/瓶/kg…）剔除、带规格品名（可乐500ml）；单个整数按数量处理（价格未知标无效待补）；只有品名没有数字的行保留为无效行而非直接丢弃；确认页新增**原始识别文本**展示，识别不准可对照修改。OcrLineParser 测试 16 个全绿。
7. **统计页扩充**：新增"分类销售额（本月）"榜（JOIN 商品分类聚合，未分类归入"未分类"）+ 毛利率 + 日均销售额。

**Files**：`util/InsetsUtil.java`、5 个布局根、`ui/common/CategoryFilter.java`（新）、`fragment_inventory/activity_sale_edit/activity_purchase_edit.xml`（筛选条）、`SaleEditActivity/PurchaseEditActivity/InventoryFragment/InventoryViewModel`（筛选与快捷新增）、`ProductEditActivity/activity_product_edit.xml`、`item_sale/item_purchase.xml + SaleListAdapter/PurchaseListAdapter/SalesFragment`（作废迁移）、`OcrLineParser.java + OcrLineParserTest`（v2，16 用例）、`OcrImportActivity/activity_ocr_import.xml`（原始文本）、`StatisticsDao/StatisticsViewModel/StatisticsFragment/CategorySalesAdapter/CategorySales.java`（统计扩充）。

**How to test**：打开任一页面看状态栏不再遮挡；库存/开单/进货页点分类 Chip 过滤；商品编辑页点"+"加分类自动选中；点流水单据 → 详情里"作废"；统计页看分类榜与毛利率。

---

## 补丁 9 — 二级分类 / 流水扁平化 / 统计三图三区间 / OCR 增强

1. **二级分类**：`categories.parentId` + **数据库 Migration v1→v2**（`app/schemas/2.json` 留档）；管理弹窗可选上级分类，列表"└"缩进，商品编辑下拉按层级展示，筛选条选父分类自动含子分类。
2. **流水页扁平化**：去 Tab，三类单据合并为**一条按时间倒序的大流水**（"售/进/支"色点徽标 + 金额颜色），类型筛选（全部/销售/进货/支出）与时间筛选并排；点行进详情（修改/作废），长按支出删除。`MediatorLiveData` 合并三源，筛选变化自动重绑。
3. **统计图三区间三图型**：周（近7天按日）/ 月（本月按日）/ 年（本年按月聚合，新增 `observeMonthlySalesSince`）；柱状图 / 折线图（`TrendChartView` 双模式）/ 扇形图（`PieChartView`，分类占比，中心显示总额，榜单即图例）。
4. **分类管理入口移到库存页**（搜索框旁"分类"按钮），与设置页共用 `ui/common/CategoryManageDialog`。
5. **OCR 图像增强**：识别前灰度化 + 对比度拉伸（ColorMatrix 单次绘制，低对比拍照单据识别率提升）。

**Files**：`Category.java/Migrations.java/AppDatabase.java(→v2)`、`CategoryRepository(sortHierarchical/parentId)`、`ui/common/CategoryFilter/CategoryManageDialog`、`CategoryManageAdapter/dialog_category_manage.xml`、`LedgerItem/LedgerAdapter/item_ledger.xml`（新）、`SalesViewModel/SalesFragment/fragment_sales.xml`（重构，旧三 Adapter 删除）、`TrendChartView/PieChartView/StatisticsDao/StatisticsViewModel/StatisticsFragment/fragment_statistics.xml/item_category_sales.xml/CategorySales.java`、`OcrEngine`（enhanceForOcr）、`TimeUtil(yearStart)`、`strings.xml`。

**How to test（模拟器截图验证通过）**：设置→分类管理→加"饮料"再加子级"茶饮"→商品编辑下拉见缩进、筛选条选"饮料"含茶饮商品；流水页一条时间线混排、切类型/时间 Chip 即时过滤；统计页切周/月/年与柱/折/扇；拍照识别低对比单据对比旧版。

---

## 尚未实现（按你的指示裁剪）

- **Phase 9–12（云同步 / WorkManager 同步任务 / ASP.NET Core 后端 / 增量同步）**：你明确"这是本地记账软件，重点定期备份"。数据库层的同步列组（9 列）已全部就位，`SyncStatus` 常量已定义，未来接同步子系统**不需要改任何表结构**，只需新增 `data/sync/` 包。
- **Phase 14（OCR）**：辅助功能，架构上不阻塞手动录入。
