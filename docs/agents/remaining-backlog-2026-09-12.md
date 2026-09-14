# 残余审计缺口清单（#229–#271 审计修复波之后的现实核验）

> 生成日期：2026-09-12
> 核验基准：`main@d00533f`（Merge PR #241；本 worktree `docs/remaining-backlog`，与 main 同树）
> **更新 2026-09-12（后半波 #256–#264 后）：`main@fd899b9`**。§3 已剪除本波闭环项（MD-1/2/3、TEST-1、GATE-1/2/3/4/5、DATA-1、DEAD-1/2、UI-3、UI-5、REPO-1），只留真开口；新增条目见 §2 尾部与 §3。
> **更新 2026-09-13（收尾波 #267–#271 后）：`main@db1b696`**。AGP9-1 / KaTeX（D-2 部分）/ 设计系统计划（SPEC-1/2 的 plan 部分）/ APK 体积门禁 / 覆盖率口径 / 探针加固 / 深链编辑 / 仓库头 均已闭环，逐条钉死见下方 §2 收尾波表；§3 只留真开口。
> **更新 2026-09-14（设计系统/渲染/加固波 #272–#289 后）：`main@15d1092`**。设计系统 SPEC-1 / SPEC-2 / UI-2 实现（Batch 1–4）、离线 Mermaid、UI-1 / UI-4 / UI-6 / UI-7、EDITOR-1、DATA-2、截图证据链（#276/#277）均已闭环；§2 尾部新增闭环表，§3 只列真开口（含 OPEN 的 #290/#291）。
> 审计基准：`a48ede1`（Merge PR #199，2026-09-11；四份审计报告的取证起点）
> 目的：把四份审计报告里**仍未解决**的条目收敛成一张可执行清单；把**已闭环**的条目显式钉死，防止被重新开票。

---

## 1. 范围与口径

### 1.1 数据源

| # | 报告 | 关键章节 | 基线 |
|---|---|---|---|
| S1 | `docs/agents/spec-audit-2026-09-11.md` | 106 判定 / §10 缺口清单 / §11 文档漂移 D1–D20 | `a48ede1` |
| S2 | `docs/agents/markdown-consistency-2026-09-11.md` | §2 夹具表 / §3 缺口 / §4 D1–D7 / §6 原型结论 | `a48ede1` |
| S3 | `docs/agents/ui-audit-2026-09-11.md` | §2 缺陷表 UI-A01–UI-C11 + D-01–D-04 | PR #199 帧 |
| S4 | `docs/agents/commit-audit-2026-09-11.md` | §5 P0-1–P3-7 / §4 G-01–G-18 / §3 Doc-01–Doc-19 | 296 提交 |
| S5 | `docs/agents/agp9-feasibility-2026-09-11.md` | 迁移可行性实测与路线 | `a48ede1` |

### 1.2 核验方法

- 四路并行只读核验代理（UI 缺陷 / spec 缺口 / markdown 缺口 / commit 门禁与文档漂移），**全部以当前 main 的 `file:line` 为准**重查，不采信报告原文。
- 覆盖 `a48ede1..d00533f` 全部 **66 个非 merge 提交 + 21 个 merge**（PR #195–#241），用于区分「本轮已修」与「仍未修」。
- 未跑 Gradle（遵约束），结论均为静态代码证据；像素类结论标注「无法离屏判定」。
- 分类口径：**(a)** 仍待实现 · **(b)** 已被 ADR/后续决策覆盖 · **(c)** 报告已过时/不成立 · **(d)** 需产品/设计决策（须先 grill）。

### 1.3 排除口径（明确不进 backlog）

- **在途工作 `test/screenshot-matrix-2`**：按任务约定不列为缺口。核验发现该分支**已完全并入 main**（`git log main..test/screenshot-matrix-2` 为空），其矩阵/RTL 守卫内容已在 main，故既不入 backlog 也不计入「未完成」。
- **用户前置**：OAuth `client id` 注入点已解（#239），但真机 PKCE 仍需用户在 `local.properties`/Secrets 配置；这是配置动作，不是代码缺口（见 §2）。

---

## 2. 本轮及紧邻波次已闭环清单（PR → 解决的审计条目）

> 说明：审计基线 `a48ede1` 之后的修复不止 #229–#241；#195–#228 同样关掉了多项审计条目，一并列出，避免重复开工。所有条目均以当前 main 代码复核。

| PR | 范围 | 解决的审计条目（来源编号） | 当前代码证据 |
|---|---|---|---|
| #196 | UI 缺陷批 B | UI-B01 搜索历史区、UI-B02 PR Tab 截断 | `SearchScreen.kt:329-380`（历史 + 清空）、`PullRequestDetailScreen.kt:733-738`（`PrimaryScrollableTabRow`） |
| #197 | #167 UI04 | 全局背景图挂账项 | `feature/t167-background` 合入 main |
| #210 | #167 UI16/UI17 | Tab 切换动效、`AppMotion` 消费面 | 合入 main（动效令牌消费） |
| #211 | #166 UI14 | 代码浏览屏文件内查找（`#166` 挂账 UI14） | `FileFindState.kt` + `CodeEditorController` find 系列 |
| #212 | i18n 守卫 | 硬编码文案源码扫描（#235 前身） | `aedb576` |
| #214 | 覆盖率门禁 | commit-audit **P1-7**「报告外行计为已覆盖」 | `build.gradle.kts:542-550`（未上报行计 0 覆盖） |
| #215 | 系统栏 insets | **UI-A01 / UI-A02 / UI-A05** | `MainActivity.kt:210,232,252`；`ReposScreen.kt:255`；`FileViewerScreen.kt:155-159,310`；`ReposListInsetsGeometryTest` |
| #216 | 设计令牌 | **spec P1（D7）`AppTypography` 不存在** | `AppTypography.kt:51` + `AppTheme.kt:114` `typography = AppTypography.from()` |
| #218 | Markdown 回归集 | **spec P0「无 Markdown golden/对比回归」+ S2 §7 夹具集** | `MarkdownGfmFixtures.kt`、`MarkdownFixtureScreenshotTest`、`WebViewFixtureRenderModeTest` 等；31 张夹具基线入库 |
| #219 | 通知面板 + README | **UI-A04**（同像素叠印）、**UI-B03**（README-404 语义 + 离线降级）、**D-04**（失败日志） | `NotificationsPanel.kt:216-222` + `GlassRenderPolicy.kt:133-137`；`RepoDetailViewModel.kt:413-419`；`RepoRepository.kt:211-231` |
| #220 | T23 接线 | commit-audit **P0-1 / P0-2**（Branches / PrCreate 白屏） | `MainActivity.kt:401-419`；`NavHostWiringTest` |
| #223 | UI 缺陷批 C | **UI-C03 / UI-C05 / UI-C09** 等 | `RepoDetailScreen.kt:1283-1285`；`PullRequestDetailScreen.kt:790-793`；`AppearanceSettingsSection.kt:401-404` |
| #224 | CI 截图链路 | **D-01 / D-02 / D-03 / D-04** | `screenshots.sh:163-164,236-248`；`adb-helpers.sh:597-618`（md5 去重）、`:413,420-460`（水印） |
| #225 | 编辑器接线 | **spec P1-4 / commit D-05,D-06 / spec D8**（codeFont、行号死设置） | `CodeEditorView.kt:51-52,85`；`AppearanceSettingsSection.kt:183-196` |
| #227 | material3 pin | 无审计条目；ADR-0008，使 Expressive 动效可用 | `docs/adr/0008-material3-alpha18-pin.md` |
| #228 | UI22 | **spec P1-3 `glass_bottom_sheet` 死设置**、project-status §3.1 UI22 | `GlassSheetSurface.kt:91` `enabledFor(GlassScope.BOTTOM_SHEET)` |
| #229 | 文档回写 | commit-audit **Doc-01**（AGENTS 整体停更）、P0 对账 | `AGENTS.md:10` 已重写为 2026-09-12 状态 |
| #230 | PAT 降级 | **spec P0「PAT 降级未落地」** | `IssueRepository.kt:299,373`；`PullRequestRepository.kt:179,477,532`；`DefaultUserRepository.kt:34`；`ViewerRepositoriesPagingSource.kt:42` |
| #231 | 语义色 | **spec P1 / D6**（ExtendedColors 缺 8 字段 + Check Run 色错） | `ExtendedColors.kt:75-92`（8 字段全在）；`PullRequestTabContent.kt:652-671`；`AppStateChip.kt:88-90` |
| #232 | 离线 GFM | **S2 D3** + spec P0 离线 GFM 的 4 项（相对链接/图片、emoji、脚注、锚点） | `renderer.js`（`rewriteRelativeUrls` + emoji/anchor/footnote 插件）；`OfflineRendererExecutionTest` |
| #233 | 深链 | **spec P2「4 类链接落到浏览器」**（Tree/Release/Search）+ Discussion 占位跳转 | `AppRoute.kt:238-263`；`DiscussionRedirectTest` |
| #234 | 草稿持久化 | **spec P2「缓存 3 处缺」之草稿**（进程被杀即丢） | `core/datastore/.../draft/{DraftRepository,DraftAutoSaver,DraftModule}.kt` |
| #235 | i18n lint | **spec P1「四条 i18n lint 规则未启用」+ commit P1-8/G-07**（library lint 全禁用） | `buildSrc/.../AppDevI18nLint.kt:29-57`；`ci.yml:87`（全模块 lint） |
| #236 | plan 回写 + ADR | **spec P0「GraphQL 读优先」**（→ ADR-0009）+ 20 条文档漂移的多数 | `plan.md:11-35` 偏离回写总表；`docs/adr/0009-graphql-read-path-deviation.md` |
| #239 | OAuth | commit-audit **P0-3**（clientId 占位且无注入点） | `app/build.gradle.kts:16-48`；`OAuthConfigModule.kt:27` |
| #240 | WebView 主通道 | **spec P2「WebView 4 项」**（服务端 HTML 高亮、触屏复制、懒加载、缓存键）+ PR 文件列表缓存 | `renderer.js`（主通道高亮 + `decorateImages` 懒加载）；`PullRequestRepository.kt:111,236` |
| #241 | CI 清理 | **spec P3 `.maestro` 死配置**、**S2 §6 原型编译**、D-01 帧命名 | 删 `.maestro/`；`ReadmeComparisonScreenshotTest.kt:37` 实参删除 |

**后半波（#242–#264）新增闭环（2026-09-12，核验于 `fd899b9`）**

| PR | 范围 | 解决的审计条目 | 当前代码证据 |
|---|---|---|---|
| #242 | CI | REPO-1 探针侧（editor 30s 有界等待） | `.github/scripts/` try_tap 逻辑 |
| #245 | 截图 | 无限动画屏挂死根因（`:feature:issue` 7min+） | `core:testing/.../captureScreenshotDeterministic` |
| #246 | 测试 | 3 处日历时间炸弹（G-14 相关） | `isoDaysAgo` 夹具 |
| #247 | 测试/CI | **GATE-1** + **GATE-4** + `WebViewDarkModePolicyTest` 断言 | `build.gradle.kts:450,470`（`*EditorViewKt*` / `[^/]*EditorView\.kt$`）；`nightly.yml:280` |
| #248 | 深链 | **REPO-1** 深链文件编辑死路 | `MainActivity.kt:609,643` 现在消费 `RepoFilesUiState.editState` |
| #249 | 截图 | **TEST-1**（`:feature:pullrequest` 三重盲区） | PR 三屏基线 + `ci.yml:221` verify |
| #251 | markdown | **MD-1（@user 部分）** + **MD-3** | `renderer.js` mention 插件；`MarkdownInlineSemantics.kt` |
| #252 | CI 截图 | 7 坏帧对账 + 坐实 UI-C01 | `screenshots.sh` 探针 |
| #253 | 设置 | **GATE-2** | `DeveloperSettingsSection.kt:60,177`（`RateLimitRow(uiState.rateLimit)`） |
| #254 | 清理 | **DEAD-1** + **DEAD-2** | `PlaceholderScreen.kt`/`ReposScreen.kt`/`ProfileScreen.kt`/`tmp/test.txt` 已删；`FeatureDetector.kt` 保留并注释 |
| #255 | 覆盖率 | **GATE-3**（6 模块补阈值） | 见 `project-status.md` §2.1 |
| #256 | 截图 | **TEST-2（大幅收敛）** | 9 屏/20 帧 + 离线 ImageLoader；`ci.yml:210-227` 现为 app + 13 模块 |
| #258 | 仓库详情 | **UI-C01** | `RepoDetailScreen.kt` `Modifier.layout` + 回归测试 |
| #259 | UI | **UI-3** + **UI-5** | `SettingsScreen.kt:133-137`；`AppDimens.kt:38` + Issue/PR padding |
| #260 | CI/门禁 | **GATE-3（无 exec 硬失败）** + **GATE-5** | `build.gradle.kts:362-374`；`app/build.gradle.kts:310`；`.github/scripts/verify-screenshots.sh` |
| #261 | 覆盖率 | 假覆盖率根因（G-02/G-03 实质） | `build.gradle.kts:267` `configureRobolectricCoverage` |
| #262 | markdown | **MD-1（#123 / 裸 sha）** | `renderer.js` issueRefPlugin/commitShaPlugin；`MarkdownInlineSemantics.kt:217` |
| #263 | 覆盖率 | **GATE-3**（22 模块真值棘轮） | `build.gradle.kts:102-141` coverageThresholds |
| #264 | 数据 | **DATA-1** | `core/github-data/.../RoomEtagStore.kt`；`RestNetworkModule` 不再用 `InMemoryEtagStore` |

**收尾波（#265–#271）新增闭环（2026-09-13，核验于 `db1b696`）**

| PR | 范围 | 解决的审计条目 | 当前代码证据 |
|---|---|---|---|
| #265 | 文档 | 后半波回写 | `AGENTS.md` / `project-status.md` |
| #266 | 截图 | **TEST-2**（补 6 屏/15 帧） | 114 张 PNG（非 prototype 110） |
| #267 | 文档 | 状态对账到 #266 | `project-status.md` |
| #268 | 设计系统 | **SPEC-1 / SPEC-2 / UI-2 的 plan 部分** | `docs/design-system/implementation-plan.md` + `docs/adr/0010-design-system-rollout.md` |
| #269 | markdown | **D-2 KaTeX 部分** | `core/markdown/src/main/assets/webview/katex/`；`renderer.js:180-330`（post-sanitize）；`FeatureDetector.containsMath`；`MathRenderExecutionTest` |
| #270 | 构建/lint | **AGP9-1** + **Q01 lint 根因** | `gradle/wrapper/gradle-wrapper.properties`（9.3.1）；`gradle/libs.versions.toml:2-6,25`；`buildSrc/.../appdev.android.*.gradle.kts`（compileSdk 37 / built-in Kotlin）；CI lint guard（`ci.yml`） |
| #271 | CI | **APK 体积回归门禁（KaTeX 研究 R11）** | `.github/scripts/check-apk-size.sh`；`.github/apk-size-budget.properties`；`nightly.yml` |

**设计系统/渲染/加固波（#272–#289）新增闭环（2026-09-14，核验于 `15d1092`）**

| PR | 范围 | 解决的审计条目 | 当前代码证据 |
|---|---|---|---|
| #273 | 设计系统 | **SPEC-1**（Batch 1：Card / Chip 家族 / Dialog / SegmentedButton） | `core/designsystem/.../component/AppCard.kt` 等；debug 画廊屏 `.../gallery/` + 28 张 `DesignSystemGallery_*` 基线 |
| #274 | 设计系统 | **SPEC-1**（Batch 2：三态状态视图收编） | `AppStateViews.kt`（Empty / Error / Loading）+ 5 feature 私有实现收编 |
| #280 | 设计系统 | **SPEC-2**（间距 scale）+ 禁新增裸控件门禁 | `core/designsystem/.../token/AppDimens.kt:39` `spacing`；`.github/scripts/forbid-bare-controls.sh` |
| #282 | 设计系统 | **SPEC-1 / UI-2**（Batch 4：Scaffold / BottomSheet + 六类裸控件清零） | `AppScaffold.kt` / `AppBottomSheet.kt`；`bare-controls-baseline.txt` 六类全 0 |
| #275 | markdown | **S2 §3 缺口 3（Mermaid）** | `core/markdown/src/main/assets/webview/mermaid/`（2,556,235 B raw）；`mermaid-render-verify.yml` 双 API 腿 |
| #276 | CI 截图 | 拼板取帧契约 + 逐帧 critical 严重度 + `lookup_mismatch` 闸门 | `.github/scripts/lib/adb-helpers.sh`（`FRAME_SEVERITIES`）、`screenshots.sh` |
| #277 | CI 截图 | readme-mermaid 探针（视口内短滑 + 先取帧后断言） | `screenshots.sh` `readme_mermaid_swipe` |
| #281 | markdown | 阅读密度令牌（S1 阅读体验） | `core/markdown/.../MarkdownDensity.kt`（`Literal` 默认 / `Conservative`） |
| #283 | 构建/CI | 单测任务级 15min 超时 + verify 步骤级 30min 上限 | `build.gradle.kts:297`；`ci.yml` |
| #284 | pullrequest | **UI-1 / D-3** | `PullRequestDiffView.kt:62-65,193-203`（<600dp unified-only + 横向滚动） |
| #286 | repo | **UI-4 + UI-6** | `RepoDetailScreen.kt:1759`（`AppEmptyState`）；`FileTreeSection.kt` mtime 列 |
| #287 | editor | **EDITOR-1** | `MarkdownEditorScreen.kt:107-117`；`core/editor/.../TextFileFormat.kt` |
| #288 | data | **DATA-2** | `build.gradle.kts:139` `":core:data" to 0.99` |
| #289 | home | **UI-7 / D-4** | `feature/home/.../ui/FeedSkeleton.kt` |
| #272 / #285 | 文档 / CI | 文档对账 / 移除临时诊断日志 | `project-status.md`；`ci.yml` |

**结论**：四份报告点名的 P0 级用户可见阻断（T23 白屏、OAuth 占位、PAT 降级、README-404 语义、离线相对链接死链）**均已闭环**。

---

## 3. 仍待实现

> 规模：S ≈ 半天内、M ≈ 1–3 天、L ≈ 一周级/需拆分。
> 「来源」指报告编号；「现实」为当前 main 的 `file:line`。
> **2026-09-12 后半波（#256–#264）已闭环**：MD-1 / MD-2 / MD-3、TEST-1、GATE-1 / GATE-2 / GATE-3 / GATE-4 / GATE-5、DATA-1、DEAD-1 / DEAD-2、UI-3、UI-5、REPO-1 —— 已从下表删除，逐条证据见 §2 尾部。

### 3.1 P0

**无。** 本轮无用户可见的 P0 阻断残留；OAuth 唯一剩余动作是**用户配置** client id（`app/build.gradle.kts:16-48` 已提供 `local.properties`/env/Secrets 三级注入），不属代码缺口。

### 3.2 P1

**已无未闭环 P1**（2026-09-14 核验，仅 SPEC-3 的实现 PR 在途）：
- **SPEC-1**（10 个 `App*` 组件 / 裸控件 / 重复状态视图）✅ 闭环：#273（Batch 1）+ #274（Batch 2）+ #282（Batch 4，六类裸控件清零）。
- **SPEC-2**（间距未令牌化）✅ 闭环：#280（`AppDimens.spacing` scale）。
- **UI-1**（窄屏 side-by-side 不可读）✅ 闭环：#284（`<600dp` unified-only + 超长行横向滚动，D-3 关闭）。
- **UI-2**（选中态对比不足）✅ 闭环：#273/#274/#282（`AppFilterChip` / `AppSegmentedButton` 显式 `selectedContainerColor`）。
- **EDITOR-1**（软换行 / 替换 / CRLF+编码）✅ 闭环：#287。
- **SPEC-3**（`@mention` 补全永不产生候选）🔶 **实现 PR #290 OPEN、未合入 main** —— 本节唯一真开口。

> SPEC-1 / SPEC-2 / UI-2 的**计划部分**由 #268 闭环（`docs/design-system/implementation-plan.md` + ADR-0010）；**实现**已随 #273/#274/#280/#282 落地。

### 3.3 P2

**已闭环（2026-09-14）**：UI-4「No README」空态、UI-6 文件树时间列（#286）；UI-7 feed 骨架（#289，D-4 关闭）；DATA-2 `:core:data` 测试基建 + 首次设阈 0.99（#288）。下表只列真开口。

| ID | 来源 | 原文摘要 | 当前现实（file:line） | 建议动作 | 规模 |
|---|---|---|---|---|---|
| **TEST-2** | S4 P2-6 / G-14 / G-15 / G-18 | 屏幕基线覆盖面（**已大幅收敛**） | 基线 **148 张 PNG（非 prototype 144）**；app + 13 模块已在 CI verify；#273/#274/#282 补设计系统画廊屏。**剩余仅为低 ROI 屏** | 按 ROI 继续补 | S |
| **PROTO-1** | S2 §6 / S4 G-10 | 原型模块不在任何 verify/detekt 门禁 | `build.gradle.kts` 跳过 detekt；`ci.yml`/`nightly.yml` verify 列表无 prototype；PR #241 只修编译 | 归档或按 S2 §6 最小路径复活（见 §4/§6，待拍板） | S |
| **PERF-1** | S1 §14.1/§14.3 / S4 | 冷启动无实测；macrobenchmark 未接 | 无 `androidx.benchmark`；`baseline-prof.txt` 手写 | 需真机/模拟器（挂 #26；用户已 defer） | L |
| **PERF-2** | S2 §8.5 | 无跨端像素 diff 管线 | 仓内只有原型 `WebViewHeadlessHtmlWriterTest`（带外手跑） | 独立票：headless Chromium 采集比对 | L |

### 3.4 在途分支（勿重复实现）

> 2026-09-14 核验：设计系统 Batch 1–4 已随 #273/#274/#280/#282 合入；AGP9 工具链已随 #270 合入。当前独立存在的分支：原型 `prototype/markdown-renderer`（待归档决策）+ 两张 OPEN PR 分支（#290/#291）。

| 分支 | 内容 | 与本清单关系 | 现状 |
|---|---|---|---|
| `feature/md-render-fixes` | 修 **D1**（details 重复渲染）、**D2**（baseRepoUrl 透传）、**D4**（coil-gif）、**D5b**（script 正文残留）+ 重录 32-inline-html 基线 | 覆盖 **MD-2** | ✅ **已合入 main（PR #237）**，远端已删 |
| `fix/editor-screenshot-probe` | editor 探针 30s 有界等待 + 文档化 REPO-1 | 与 **REPO-1** 相关（探针侧） | ✅ **已合入 main（PR #242）** |
| `docs/katex-mermaid-feasibility` | KaTeX/Mermaid 离线可行性评估（含体积实测） | §6 D-2 决策输入 | ✅ **已合入 main（PR #243）**；报告在 `docs/research/` |
| `chore/agp9-feasibility` | 升 AGP 9.1.1/Gradle 9.3.1/compileSdk 37 + 可行性报告 | **AGP9-1** 的工具链骨架 | ✅ **已合入 main（PR #270）**，远端已删 |
| `feat/design-system-batch1` | 设计系统 Batch 1（Card / Chip 家族 / Dialog / Snackbar） | **SPEC-1 / UI-2** 实现起点 | ✅ **已合入 main（PR #273）**；后续 Batch 2–4 见 #274/#280/#282 |
| `feat/editor-mention-completion` | SPEC-3 `@mention` 接真实候选 | §3.2 SPEC-3 | 🔶 **OPEN PR #290**，未合入 main |
| `fix/repo-guest-commit-date-guard` | 游客模式不再为 mtime 列消耗 REST 配额 | §3.3 | 🔶 **OPEN PR #291**，未合入 main |
| `prototype/markdown-renderer` | 7 提交：独立原型（KotlinTextMate/grammars/themes），探索性 | **PROTO-1** 归档决策 | ❌ 未合入（`origin/prototype/markdown-renderer` 仍存在，领先 main 7 提交） |
| `test/screenshot-matrix-2` | 矩阵/RTL 守卫 | **明确排除，不入 backlog**；已并入 main | 已并入 main，无领先提交 |


---

## 4. 已被 ADR / 后续决策覆盖

| 缺口 | 覆盖决策 | 理由 | 证据 |
|---|---|---|---|
| **GraphQL「读优先」未成立**（S1 §4.4 / D4，P0） | **ADR-0009** | 读路径全 REST 是有意架构决定（GraphQL 收敛到少数写上下文/评审线程）；`IssueDetail.graphql`、Fragment 不再作为缺口；`URI` scalar 已补映射 | `docs/adr/0009-graphql-read-path-deviation.md`；`core/github-graphql/build.gradle.kts:46` |
| **`MarkdownRenderer` 抽象缺失 / 渲染分流由 feature 硬编码**（S1 §2.4 / D3） | **ADR-0007** | Task B 起 WebView 主渲染（服务端 HTML 优先 + 离线 GFM 降级）；抽象接口路线被有意放弃 | `docs/adr/0007-markdown-native-primary-webview-fallback.md`；`plan.md:169-177` 回写 |
| **主题变体 Tonal Spot/Neutral/Vibrant/GitHub Classic**（S1 D9） | **ADR-0004 / ADR-0006** | 代码实现的是六套模式（Light/Dark/OLED/DynamicLight/DynamicDark/HighContrast）；ui-design 旧名清单未回写 | `ThemeMode.kt:9-30`；`docs/adr/0004`、`0006` |
| **Material Color Utilities 全 tonal palette**（S1 §5.2 / D5） | plan.md 偏离回写（#236） | 说明这是已记录的实现取舍，不再是静默偏离 | `plan.md:11-35` 第 4 行、`:585` |
| **Shiki / Highlights-18 / prism4j**（S1 D10/D11） | plan.md 偏离回写（#236） | 实现选 KotlinTextMate + highlight.js，已回写 | `plan.md:216,241,348-360` |
| **androidx.benchmark**（S1 D14） | plan.md 偏离回写（#236） | 标注「⏳ 未实现」，由 PERF-1 承接 | `plan.md:1044` |
| **`mergePullRequest` GraphQL mutation 示例**（S1 D20） | plan.md 偏离回写（#236） | 实现用 REST `PUT /pulls/{n}/merge`，符合「REST 写优先」 | `plan.md:739`；`PullRequestApi.kt:204` |
| **Prototype 模块不进 CI 门禁**（S2 §6） | 报告结论（正式记录） | 4 条实测理由（编译曾失败、2 张 PNG 无任务、基线价值低、throwaway 定位）；**结论是「不纳入」**，不是待办 | `docs/agents/markdown-consistency-2026-09-11.md` §6；`build.gradle.kts:243-244,248-249` |
| **Material Symbols 变量字体（wght=300）** | project-status §4 决策（PR #185） | 评估完成、暂缓引入（含再评估触发条件） | `docs/agents/project-status.md:114` |
| **`FeatureDetector` 保留但不用** | ADR-0007 + #254 决策 | 有意保留为**文档化墓碑**（#254 核验后保留并加注释标注），不再是待办；对应 §6 D-5 已闭环 | `core/markdown/.../webview/FeatureDetector.kt`（带保留注释） |

---

## 5. 报告已过时 / 不成立（附反证）

| 报告条目 | 原判 | 反证（当前 main） |
|---|---|---|
| S2 §0/§3：「24 条原生像素基线未录制」 | 🟡 待录 | **31 张 `MarkdownFixture_*.png` 全部存在**，与 `MarkdownGfmFixtures.baselineNames()` 逐名一致；`ci.yml:204`/`nightly.yml` 已 verify |
| S3 UI-A01（Profile 末行压底栏） | P0 代码缺陷 | `MainActivity.kt:252` + `ProfileScreen.kt:224-226` + `MainTabPager.kt:48-51` 接线本已正确（#215 body 自述 Home/Profile 未改动）；帧上是「内容穿过玻璃」的预期语义，非缺陷 |
| S3 UI-C01（仓库头不可见） | P2「非缺陷」 | **原反证判错**：#258 证明仓库头**真的整块不渲染**（`headerHeightPx` 自锁），虽非 #167 误触发却确为真缺陷；已修复（`Modifier.layout` 量自然高度 + 回归测试） |
| S3 UI-C03 / UI-B03 / UI-C05 | P1/P2 | 见 §2 #223/#219（已修） |
| S1 D1（AGENTS 停在 2026-09-06） | 文档漂移 | `AGENTS.md:10` 已重写为 2026-09-12（#229） |
| S1 D6/D10/D11/D5/D14/D19/D20 | 文档漂移 | `plan.md:11-35` 偏离回写总表 + 各章节注记（#236） |
| S2 D6（字体变量名漂移） | 文档失真 | `plan.md:303-316` 已写 `--fontStack-*` 并注明改正（#236） |
| S2 §0「D3 功能性损坏」 | 缺陷 | 已修（#232），`renderer.js` 产物层重写 |
| S4 P1-3（`glass_bottom_sheet` 死设置） | P1 | `GlassSheetSurface.kt:91` 已消费（#228） |
| S4 P1-4 / D-05 / D-06（codeFont/行号死设置） | P1 | 已接线（#225） |
| S4 P1-7（diff 门禁报告外行计覆盖） | P1 | `build.gradle.kts:542-550` 已修（#214） |
| S4 P1-8 / G-07（library lint 全禁用） | P1 | `buildSrc` 禁用块已移除；`ci.yml:87` 全模块 lint（#235） |
| S4 P0-1/P0-2/P0-3 | P0 | #220 / #239 已修 |
| S1 P3「`.maestro` 死配置」 | P3 | 已删（#241） |
| S2 §6「原型测试编译不过」 | 事实 | #241 已删该行实参，可编译 |
| S4 Doc-01 / Doc-14 / Doc-15 | 文档漂移 | AGENTS `:core:test` 已改 `:core:markdown:testDebugUnitTest`；`workflow.md:43-47` 已补 `coverageVerify`/模块 verify |
| S4 G-02 / G-03（覆盖率阈值不全 + SKIP 空转） | P1 | #255 补 6 模块阈值；**#260** 改无 exec 硬失败；**#263** 按真值棘轮 22 模块 |
| S4 P1-6 / G-04（`*EditorView*` 误伤 ViewModel） | P1 | `build.gradle.kts` 已收紧为 `*EditorViewKt*` / `[^/]*EditorView\.kt$`（#247，带注释） |
| S4 P1-9 / Doc-13（`RateLimitRow` 永“待接入”） | P1 | `DeveloperSettingsSection.kt:60` 已接 `uiState.rateLimit`（#253） |
| S4 G-08（konsist 无匹配测试也绿） | P2 | `app/build.gradle.kts:310` `isFailOnNoMatchingTests = true`（#260） |
| S4 G-09（nightly 汇总漏截图 job） | P2 | `nightly.yml:280` `needs` 已含 `screenshot-drift` + `screenshot-refresh`（#247） |
| S1 §4.6 / §3.6（ETag 进程内） | P1 | `core/github-data/.../RoomEtagStore.kt` + `EtagScopeProvider`；`RestNetworkModule` 不再注入 `InMemoryEtagStore`（#264） |
| S2 D1/D2/D4/D5a/D5b（details/baseRepoUrl/coil-gif/script/kbd-sub-sup） | 缺口 | #237 + #251 已修 |
| S4 P1-2 / G-01（PR 三屏三重盲区） | P1 | 3 屏基线 + `ci.yml:221` verify（#249） |
| S1 §2.4（`FeatureDetector` 死代码） | P2 | #254 核验后保留并加注释（文档化墓碑）；对应 §6 D-5 闭环 |
| S5（AGP9 可行性）「迁移未做」 | 缺口 | **已落地（#270）**：AGP 9.1.1 / Gradle 9.3.1 / compileSdk 37 / buildTools 36.0.0 / built-in Kotlin；报告路线被采用 |
| S2 §3 缺口 3（KaTeX/Mermaid） | 缺口 | **均已落地**：KaTeX（#269）；Mermaid 离线 Tiny 11.17.2（post-sanitize；Chromium ≥94 门禁，不满足回退普通代码块） |
| S1 §5.5 / §5.4（SPEC-1/SPEC-2） | P1 | **计划（#268）+ 实现全部落地（#273/#274/#280/#282）**；六类裸控件清零，门禁棘轮到 0 |
| KaTeX 可行性研究 R11（体积无回归门禁） | 缺口 | **已加门禁（#271）**：`check-apk-size.sh` + `.github/apk-size-budget.properties`（现 baseline 8,162,579 / budget 8,424,723 B） |
| S3 UI-B05（UI-1 窄屏 diff） | P1 | **已修（#284）**：`<600dp` unified-only + 超长行横向滚动（D-3 关闭） |
| S3 UI-B06 / UI-C04（UI-2 选中态） | P1 | **已修（#273/#274/#282）**：`AppFilterChip` / `AppSegmentedButton` 显式 `selectedContainerColor` |
| S1 §7.2 / §8.1（EDITOR-1） | P1 | **已修（#287）**：软换行开关 + 查找替换 + 显式 CRLF/编码（`core/editor/.../TextFileFormat.kt`） |
| S3 UI-C02（UI-4 空态）/ UI-C07（UI-6 时间列） | P2 | **已修（#286）**：`AppEmptyState` + 文件树 mtime 列 |
| S3 UI-C10（UI-7 feed 骨架） | P2 | **已修（#289）**：`FeedSkeleton.kt`（D-4 关闭） |
| S4 P2-5 / G-11（DATA-2） | P2 | **已修（#288）**：`:core:data` 补测试基建 + 首次设阈 0.99 |
| 截图证据链（拼板取帧契约 / 严重度旁路 / readme-mermaid 假帧） | 缺陷 | **已修（#276/#277）**：`base=${f%.png}` + 逐帧 critical 严重度 + 视口内短滑 + 先取帧后断言 + `lookup_mismatch` 闸门 |

> 备注：S1 D8（代码字体/行号设置项与 ui-design 冲突）随 #225 闭环；S1 D15–D20 的余额见 §3.3/§3.4。

---

## 6. 需产品 / 设计决策（须先 grill 确认）

| # | 决策点 | 为什么不能由代理拍板 | 关联条目 |
|---|---|---|---|
| D-1 | **`docs/ui-design.md` §7.1 六套主题命名** vs ADR-0004 的六模式 | 需决定「改文档对齐 ADR」还是「改代码实现文档命名」，影响设置页文案与截图矩阵 | S1 D9 |
| ~~D-2~~ | ~~离线通道是否引入 KaTeX/Mermaid~~ | ✅ **已决定并落地**：#269（KaTeX）+ #275（Mermaid，Chromium ≥94 门禁 + API 33/30 双证据腿） | ~~S2 §3 缺口 3~~ |
| ~~D-3~~ | ~~窄屏 side-by-side diff：隐藏还是横向滚动~~ | ✅ **已决定并落地**（#284）：`<600dp` 强制 unified + 超长行横向滚动 | ~~UI-1~~ |
| ~~D-4~~ | ~~feed 首载：骨架屏还是 M3 LoadingIndicator~~ | ✅ **已决定并落地**（#289）：feed 首载骨架屏 | ~~UI-7~~ |
| ~~D-5~~ | ~~`FeatureDetector`：删除还是保留为文档化墓碑~~ | ✅ **已决定**（#254 核验后保留 + 注释；与 ADR-0007 措辞一致） | ~~DEAD-2~~ |
| D-6 | **`prototype/readme-comparison`：归档删除还是按 §6 复活** | 报告已给「不纳入」结论，但模块去留需拍板 | PROTO-1 |
| ~~D-7~~ | ~~深链编辑（REPO-1）期望行为~~ | ✅ **已实现**（#248 `BlobRoute` 消费 `RepoFilesUiState.editState`，落在文件查看页内编辑） | ~~REPO-1~~ |
| D-8 | **跨端「与网页端一致」是否投入自动像素 diff 管线** | 高成本独立票，需优先级拍板 | PERF-2 |

> 以上仍开口的 D-1/D-6/D-8 均属 `docs/ui-design.md`/交互取向类，按项目规则**先 grill 再实现**（`plan.md` 与 `workflow.md` §4 设计闸门）。
>
> **2026-09-13 更新**：D-2 的 KaTeX 部分已落地（离线 KaTeX 0.18.7，post-sanitize 渲染、
> 仅 woff2 字体、条件注入；两条 WebView 通道覆盖，见 `docs/agents/markdown-consistency-2026-09-11.md`
> 的 2026-09-13 更新与 `MathRenderExecutionTest`）。Mermaid 已按可行性报告的 Phase 2 结论落地
> （Tiny 11.17.2 + WebView ≥ 94 版本门禁，见 `MermaidRenderExecutionTest`）。

---

## 7. 建议的下一波打包（2026-09-14 重排）

> Wave A（设计系统 / 令牌）、Wave B（Markdown）、Wave C（UI 残余）、Wave D（测试 / 数据）**均已落地**；下表为**真实剩余**。

| 波次 | 内容 | 状态 |
|---|---|---|
| ~~Wave A：设计系统 / 令牌~~ | SPEC-1 / SPEC-2 / UI-2 | ✅ 落地（#273/#274/#280/#282）；六类裸控件门禁清零 |
| ~~Wave B：Markdown~~ | MD-1/2/3 + KaTeX/Mermaid + 阅读密度 | ✅ 落地（#237/#251/#262/#269/#275/#281） |
| ~~Wave C：UI 残余缺陷~~ | UI-1 / UI-4 / UI-6 / UI-7 | ✅ 落地（#284/#286/#289） |
| ~~Wave D：测试 / 数据~~ | DATA-2 + 覆盖率口径 | ✅ 落地（#288 / #260 / #261 / #263） |
| **Wave E：真机 / 决策（先 grill）** | §3.2 真机项（用户已 defer）；§6 的 D-1/D-6/D-8；PROTO-1 / PERF-1 / PERF-2 | 🔶 剩余 |
| **Wave F：在途收尾** | #290（SPEC-3 @mention）、#291（游客 REST 配额守卫），合并后关闭对应条目 | 🔶 两张 OPEN PR |

**并行建议**：Wave F 两张 PR 互不冲突，可并行 review/合并；Wave E 的决策项优先产出。

---

## 附：核验基准与复现

```bash
# 核验基准（2026-09-14 设计系统/加固波后）
git log -1 --format='%H %ci %s'        # 15d1092 ... (#287)
git merge-base --is-ancestor 423bc79 HEAD && echo "31 基线已在 main"

# 报告 vs 现实快速对照
grep -rn "fun AppScaffold\|fun AppCard\|fun AppChip\|fun AppBottomSheet" --include=*.kt core/designsystem   # #273/#282
bash .github/scripts/forbid-bare-controls.sh && cat .github/bare-controls-baseline.txt   # #280/#282 六类全 0
ls .github/scripts/verify-screenshots.sh          # 帧闭包断言（#260）
find . -path "*/src/test/screenshots/*.png" -not -path "*/build/*" | wc -l   # 148（含 prototype 4；非 prototype 144）
ls docs/design-system/implementation-plan.md docs/adr/0010-design-system-rollout.md   # #268
grep -n 'budget_bytes' .github/apk-size-budget.properties   # #271/#275 → 8424723
grep -n 'test.timeout' build.gradle.kts   # #283 → 15min 任务级
grep -n '"":core:data"" to 0.99' build.gradle.kts   # #288 首次纳入
grep -n "includeNoLocationClasses" build.gradle.kts   # #261 Robolectric 覆盖率修复
gh pr list --repo yumiru11/AppDev --state open --json number,title   # #290/#291 在途
```

**边界声明**：本清单为只读核验产物，未改任何生产代码，未跑 Gradle；像素类争议在无新 CI 帧时标注为「无法离屏判定」，需截图链路复跑后由 §6 决策收敛。
