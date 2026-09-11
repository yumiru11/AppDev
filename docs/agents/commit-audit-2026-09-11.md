# AppDev 提交级审计报告（2026-09-11）

> 审计范围：`origin/main` 全部 **296** 个提交（无 merge 提交 **219** 个，merge 提交 **77** 个）；HEAD = `a48ede1`（Merge PR #199）
> 审计方式：`git log --oneline --no-merges` 全量清单 + `git show --stat` 逐票核对 + 全仓 grep 静态对码；**只读**，未跑 Gradle，未改任何生产代码
> 排除口径：所有 `*/build/*` 构建产物不计入
> 基线对照：`docs/agents/task-audit-2026-09-06.md`（51 项发现 → 9 张分类票 #163–#171）逐项复核

---

## 0. 总览

**一句话结论**：审计补全波（#163–#171）的**修复质量很高**——51 项发现里 L01–L16 逻辑票几乎全部真实落地（RemoteMediator/Trending/Gists/建删仓库/Release+asset/Topics/PR 编辑/R8/调试三件套均已验证存在）；但**票面"已合入"≠功能可用**：T23 的两个 Screen（共 650 行 UI）**从未被接线**，用户点进去是**白屏**；主认证 OAuth `clientId` 仍是占位符且无注入点；`docs/agents/project-status.md` 自称"三处文档已对齐"在提交级上不成立（AGENTS.md / FEEDBACK.md 停在 PR #172，其后 44 个提交未回写）。

### 问题计数表

> 口径：以 §5「优先级排序的问题清单」为**权威计数**（每条为一个可独立行动项）；§2/§3/§4 中的 D-xx / Doc-xx / G-xx 是证据编号，与 §5 条目多对一。

| 严重度 | 数量 | 类别分布 |
|---|---|---|
| **P0 阻断**（用户可见功能不可用 / 发布阻断） | **3** | 白屏 ×2（T23 两条路由）、主认证占位符 ×1 |
| **P1 高**（声称做了但没做全 / 门禁假绿 / 死设置） | **9** | 静默丢失 ×2、死设置/空开关 ×3、门禁结构性漏洞 ×4 |
| **P2 中**（文档漂移 / 测试缺口 / 空实现 / 挂账缺失） | **8** | 文档漂移 ×3 组、空实现 ×1、测试缺口 ×2、坏命令 ×1、守卫空转 ×1 |
| **P3 低**（清理项） | **7** | 死代码/孤儿文件 ×4、公共 API ×1、重复实现 ×1、草稿文件 ×1 |
| **合计** | **27** | 证据编号共 D-01~D-17（17）、Doc-01~Doc-19（19）、G-01~G-18（18） |

### 票面判定计数（T1–T26）

| 判定 | 数量 | 票号 |
|---|---|---|
| ✅ 完整 | **23** | T1 T2 T3 T4 T5 T6 T7 T8 T9 T10 T11 T12 T13 T14 T15 T16 T18 T19 T20 T21 T22 T24 T26 |
| 🔶 部分 | **2** | **T17**（合入时零新增测试）、**T25**（真机项待做，已诚实挂账） |
| ❌ 声称做了但没做 | **1** | **T23**（两个 Screen 共 650 行从未接线 → 用户点进去白屏） |

> T23 以最严口径计入 ❌，不重复计入 🔶；T4/T13/T24 的缺口（OAuth 占位符、模块 verify 缺位、配额行未接）已在 ✅ 旁标注而不降级，因其声明的功能主体确已落地。

---

## 1. 逐票完成度核对

> ⚠️ **方法论前提（重要发现，见 §6.3）**：本仓 **T1–T26 的票号 issue（#2–#27）在全部 219 个提交信息中一次都没出现**（`Fixes/Closes/Resolves/Part of #N` 命中数 = 0/25）。因此任务书建议的 `git log --grep="#<N>"` 对 T1–T26 **完全失效**（`git log --grep="#17"` 找不到 T16）。本表通过 **PR 号 → 分支名 → 合入提交** 三级反查重建，并已用 `git show --stat` 逐个核对。

| 票 | issue | 相关提交（合入方式） | 声明内容 | 实际落地 | 判定 | 缺口 |
|---|---|---|---|---|---|---|
| T1 CI/CD | #2 | `f1bf1f3`（squash PR #28） | GitHub Actions 质量门禁 + 签名 Release | `ci.yml` 423 行；spotless/detekt/lint/konsist/单测/verify/assemble/coverage/diff 齐备 | ✅ | 见 G-07/G-08：library lint 全禁用、screenshots job 非必需 |
| T2 架构守卫 | #3 | `1555c72`（squash PR #43） | Konsist + core:testing + 截图基线 | `app/src/test/.../konsist/ArchitectureTest.kt` + core:testing 3 文件 | ✅ | `konsistCheck` 配 `isFailOnNoMatchingTests=false`（G-08），测试类消失也绿 |
| T3 导航骨架 | #4 | `bee9dac`（squash PR #44） | GitHubLinkParser + AppRoute + 底部导航 | AppRoute @Serializable 齐备 | ✅ | 同期引入的 `HomePager` 基线被 T10 删除、未重建（见 §2 D-11） |
| T4 认证 | #5 | `b2b413f`（squash PR #46） | PKCE + TokenStorage + 刷新降级 + 登录 UI | OAuth PKCE + EncryptedTokenStorage 均在 | ✅（带缺口） | **`clientId` 是占位符**（P0-1）→ 开箱状态下 PKCE 链路不可用；功能主体已落地，故不降级，但 P0-1 必须独立修 |
| T5 网络层 | #6 | `37f0cac`（squash PR #45） | Retrofit3/Apollo5 + 错误归一 + Room/DataStore | 齐备；compileSdk 35→36 亦在此 | ✅ | — |
| T6 主题引擎 | #7 | `06d4a1a`（squash PR #48） | 6 套主题 + 动态色 + 令牌族 + 玻璃 | ThemeColors 6 套 + ExtendedColors | ✅ | — |
| T7 原生 Markdown | #8 | `a287b5e`（squash PR #47） | mikepenz + KotlinTextMate + Alert + 链接 | 齐备 | ✅ | 后由 Task B 降级为短文本通道（ADR-0007 已修订，非缺口） |
| T8 WebView 兜底 | #9 | `e08d6a4`（squash PR #51，**与 T9 合一个提交**） | DOMPurify + markdown-it + hljs | 6 个测试类齐备 | ✅ | 提交粒度：两票共用一个提交 |
| T9 README 浏览 | #10 | `e08d6a4`（同上） | FeatureDetector 分流 + 相对链接 + JSON 回退 | 后升级为 WebView 主渲染 | ✅ | `RelativeLinkRewriter` 随 Task B 删除（合理，见 §2 已排除项） |
| T10 首页 feed | #11 | `39fed0c`（squash PR #56） | Events API + 6 类事件过滤 + Home 分区 | FeedPagingSource + PullToRefresh 齐备 | ✅ | 同提交**删除** `HomePager_*.png` 基线且未重建 → feature:home 至今 0 基线（G-12） |
| T11 文件树浏览 | #12 | `cc9a6ca`（squash PR #61） | Git Data 递归树 + Sora read-only + M3 主题 | GitTreeApi/ContentApi/FileTreeBuilder/FileViewerScreen 齐备 | ✅ | — |
| T12 仓库管理 | #13 | `958f07d`（merge PR #74） | Star/Watch/Fork + Releases/Tags + 语言栏 | 全部有专属测试（本波最佳） | ✅ | — |
| T13 Issue 列表/详情 | #14 | `ed0b27f`（squash PR #57） | 时间线 + reactions + 截图基线 | 4 张基线存在 | ✅（带缺口） | feature:issue **不在 PR 门禁 verify 名单**（G-16；有 nightly `screenshot-drift` 兜底，但该 job 失败不开 issue，见 G-09） |
| T14 Issue 写 | #15 | `2e3654e`（merge PR #75） | 创建/编辑/评论/反应/关闭/任务列表同步 | 齐备，checkbox 写回已接 | ✅ | Issue 侧 `onCodeCopy {}` 空实现（D-03） |
| T15 PR 列表详情 | #16 | `699b28a`（merge PR #76） | 四 Tab | 齐备 | ✅ | — |
| T16 Diff + 行评论 | #17 | `ba79352`（merge PR #101） | DiffParser + PullRequestDiffView + LineCommentSheet | DiffParser/Repository/VM 有 5 个测试文件 | ✅ | 3 个 UI 类零测试零基线，且被 JaCoCo 排除（G-01） |
| T17 Review/Merge | #18 | `e44af18`（merge PR #102） | ReviewSheet + MergeBox + merge/update-branch/delete-ref | 功能可达（在 PR 详情内） | 🔶 | **合入时零新增测试**（`git diff --name-status e44af18^1 e44af18` → 7 个测试文件全为 `M`，无 `A`）；`MergeBoxClosedStateTest` 是 15 天后 `2e627b8` 补的 |
| T18 全局搜索 | #19 | `ab59234`（squash PR #62） | 四类结果 Tab + 历史 + qualifier + 代码搜索门禁 | 9 个测试文件（本波测试最足） | ✅ | `git log --grep="#19"` 零命中（squash 后无 merge 记录，见 §6.3） |
| T19 通知 | #20 | `7907914`（squash PR #52）+ `4e770d9`（merge PR #98） | 面板：分组折叠/滑动/筛选/全部已读 | PANEL_SCRIM_ALPHA=0.5 + SwipeToDismiss + `animateItem()` 重排 | ✅ | core:ui 旧 `NotificationPanel_*` 基线被 `0d8c64f` 删除（breaking 重构，已记理由，合理） |
| T20 Profile | #21 | `2c48b1a`（squash PR #53） | Repositories/Starred/Followers/Following 四路 Paging | 四路齐备 | ✅ | 四路之外又补了 Gists（`ad05201`），属**文档少记**（Doc-16） |
| T21 Markdown 编辑器 | #22 | `408532f`（merge PR #77） | Sora 编辑 + 工具栏 + 编辑/预览双 Tab | core/editor 有测试 | ✅ | `MarkdownEditorScreen.kt` 无测试无基线；`codeFont`/`codeLineNumbers` 是死设置（D-05/D-06） |
| T22 文件编辑提交 | #23 | `24bc486`（merge PR #96） | Contents API + sha 校验 + 409 三选项 | ContentApiWriteTest（含 409）+ 2 张基线 | ✅ | 本波最完整票 |
| **T23 分支/PR 创建** | **#24** | **`c97ac86`（merge PR #105）** | **BranchesScreen + GitRefApi + PullRequestCreateScreen** | **GitRefApi 可用且已测；两个 Screen 代码存在但【从未接线】** | **❌** | **P0-2 / P0-3：两条路由都是白屏。`MainActivity.kt` 20 个 screen lambda 里恰好漏传这 2 个** |
| T24 设置 | #25 | `2b3465d`（squash PR #54）+ `b06a3c6`（merge PR #99） | 主题/seed/圆角/动效滑杆/语言/毛玻璃总开关/PAT/配额 | 大部分接线 | ✅（带缺口） | 票面声明的"配额"**未接线**：`RateLimitRow` 永久"待接入"（D-07）；另有余量开关 `glass_bottom_sheet` 空转（D-04） |
| T25 性能发布 | #26 | `cba99e9`（merge PR #186） | Baseline Profile + i18n + RTL + 发布链路 | 4 项均落地且有实测证据（`baseline.prof` 7886B） | 🔶 | 冷启动实测 / macrobenchmark / 生产密钥待真机或 Secrets（已在 project-status §3.2 诚实挂账） |
| T26 M3 高亮 | #27 | `dbe27f6`（squash PR #55） | M3TextMateTheme/GitHubTextMateTheme | 齐备 | ✅ | — |

### 1.1 计划外波次核对

| 波次 | issue | 判定 | 证据 / 缺口 |
|---|---|---|---|
| Task B WebView 主渲染 | #71 | ✅ | PR #70 `9fd550a` / #73 `4dde028`；ADR-0007 已修订 |
| UI 打磨波 | #82 | ✅ | PR #82 `2f1f102` |
| ui-audit #83–#90 | #83–#90 | ✅ | PR #92→#110 全部合入；#83 backdrop 几何约束已回写 |
| **审计补全波** | #163–#170 | ✅ **（质量高）** | L01–L16 逐条对码**全部真实存在**（详见 §1.2） |
| 文档同步 | #171 | 🔶 | PR #172 `9d66f04` 当时确实对齐了三文档，但**此后 44 个提交未再回写 AGENTS.md / FEEDBACK.md**（Doc-01/Doc-02） |
| UI 一期/二期/三期 | #166/#167/#168 | 🔶 | **UI08 静默丢失**（D-01）；UI04/UI10/UI17/UI22 已诚实挂账 |

### 1.2 审计补全波 L01–L16 逐条对码（关键：本波修复是真的）

| 项 | 声明 | 对码证据 | 判定 |
|---|---|---|---|
| L01 Issue 订阅 | #163 | `IssueDetailScreen`/API 层 `subscription` 8 文件命中 | ✅ |
| L02 Label/Assignee/Milestone 编辑 | #163 | `updateIssue` 9 文件；`assignee` 20 / `milestone` 19 文件命中 | ✅ |
| L03 PR 编辑/关闭/重开 | #163 | `PullRequestApi.kt:232 updatePullRequest` + `PullRequestApiTest.kt:666-739` 三个专项测试 | ✅ |
| L04 建删仓库 | #164 | `CreateRepoScreen` 已接线 `MainActivity.kt:284-286`；`createRepository` 15 文件 | ✅ |
| L05 Release + asset | #164 | `RepoManagementApi.kt:118 createRelease`、`:132 uploadReleaseAsset` + multipart 测试 | ✅ |
| L06 Topics | #164 | `RepoManagementApi.kt:145 getTopics` + `TopicsDto` | ✅ |
| L07 RemoteMediator | #165 | `feature/issue/.../IssueRemoteMediator.kt:38` + AppDatabase v4 `cached_issues` + 专项测试 | ✅ |
| L08 Trending | #165 | `TrendRepository.kt` 镜像→搜索回退→6h 缓存 + `TrendingSection.kt` 已进首页 | ✅ |
| L09 COMMIT 详情页 | #164 | `CommitDetailScreen.kt:61`（注释自述"替换此前的 PlaceholderSearchScreen 占位"） | ✅ |
| L10 他人主页 + 关注 | #165 | `user/following` 5 文件；`onOpenUser` 已接线 | ✅ |
| L11 Gists | #165 | `GistsScreen` + `AppRoute.Gists` + `MainActivity.kt:231/317-322` | ✅ |
| L12 通知排序切换 | #165 | `NotificationSortOrder` enum + `NotificationsPanel.kt:181 onSelectSortOrder` | ✅ |
| L13 搜索缓存/限流提示 | #165 | `SearchRateLimitWarning.kt:35 rateLimitWarningThreshold` | ✅ |
| L14 调试三件套 | #169 | Timber + `RedactingDebugTree` + Chucker(no-op release) + LeakCanary 全在 toml/build 中 | ✅ |
| L15 R8/minify | #169 | `app/build.gradle.kts:94 isMinifyEnabled = true` + proguard-rules | ✅ |
| L16 白名单复核 | #169 | `431cf49` + `PrivateImageInterceptor` 5 文件 | ✅ |

> **结论**：9 张分类票的修复**没有水分**，这是本仓最强的一波。问题不在这一波，而在**更早的 T23 接线遗漏**（2026-08-26 合入，此后 3 周无人发现）与**门禁结构性漏洞**。

---

## 2. 半成品与死代码清单

> 说明：全仓 `TODO()` / `NotImplementedError` / `error("not implemented")` **零命中**；空 `catch {}` 零命中；`@Ignore` 零命中。**本仓的真实缺口没有 marker**，全是"声明了但没接线"，grep 关键词抓不到 —— 必须按"声明 → 消费者"路径反查。

### 2.1 P0 阻断

| # | 位置（文件:行） | 问题 | 证据 | 建议动作 |
|---|---|---|---|---|
| **P0-1** | `core/github-auth/src/main/kotlin/.../auth/OAuthConfig.kt:22` + `AuthModule.kt:44` | **主认证链路开箱必失败**：`PLACEHOLDER_CLIENT_ID = "YOUR_OAUTH_APP_CLIENT_ID"`，且 `provideOAuthConfig(): OAuthConfig = OAuthConfig()` 用默认构造。全仓 **无任何** `BuildConfig` / gradle 属性注入点 | `grep -rniE "buildConfigField\|OAUTH_CLIENT\|clientId" --include=*.kts .` → **0 命中**；值流入 `OAuthSessionManager.kt:62` 授权 URL 与 `OkHttpTokenEndpointClient.kt:63` | **新票 P0**：加 `buildConfigField`/gradle 属性注入 + 启动期缺失即显式失败（不要静默用占位符拉起浏览器） |
| **P0-2** | `core/ui/.../AppNavHost.kt:94-100`（默认）→ `:268-273`（调用点）；`feature/repo/.../BranchesScreen.kt:68`（358 行） | **`AppRoute.Branches` 是白屏**：`branchesScreen` lambda 从未被 `MainActivity` 传入 → 走默认 `{ _, _, _, _, _ -> }`。入口真实可达：`MainActivity.kt:269-272 onBranchesClick → navigate(AppRoute.Branches(...))`。**`BranchesScreen` 全仓仅 3 处引用：声明 + 2 处注释/文档，零代码调用者** | `grep -c "branchesScreen\|createPullRequestScreen" MainActivity.kt` = **0**；参数差集脚本：22 个声明参数中**恰好 2 个未传入** | **新票 P0**：`MainActivity.kt` 补 `branchesScreen = { ... }` |
| **P0-3** | `core/ui/.../AppNavHost.kt:101-105`（默认）→ `:256-260`（调用点）；`feature/pullrequest/.../PullRequestCreateScreen.kt:62`（292 行） | **`AppRoute.PrCreate` 是白屏**：`createPullRequestScreen` 同样未传入 → 默认 `{ _, _, _ -> }`。入口可达：`PullRequestListScreen.kt:92` IconButton → `MainActivity.kt:359-361 navigate(AppRoute.PrCreate(o, r))`。`PullRequestCreateScreen` 全仓**仅声明 1 处** | 同上（同一根因，同一 PR #105 / T23） | **新票 P0**：补 `createPullRequestScreen = { ... }` |

> **为什么 CI 全绿却漏了**：`PullRequestCreateViewModelTest.kt`（164 行）与 `BranchesViewModel` 测试直接测 ViewModel，**从不经过导航装配**；两条路由的白屏是 lambda 默认值造成的，静态测试与覆盖率都看不见。**T23 计 650 行 UI + 2 个 ViewModel 对用户完全不可达**。
> **建议加门禁**（本报告最高性价比的一条）：断言 `AppNavHost` 的 screen lambda 不残留默认空实现 —— 一条检查可同时拦住这两个 P0。

### 2.2 P1 高

| # | 位置（文件:行） | 问题 | 证据 | 建议动作 |
|---|---|---|---|---|
| **D-01** | `feature/issue/.../IssueDetailScreen.kt`（`ReactionChip.kt` 全文件无动画） | **UI08「Reaction 点击微缩放」静默丢失**：`task-audit-2026-09-06.md:231` 列为 #167 范围，`project-status.md:73` 却把未闭环项写作"UI04/UI10/UI17/UI22"——**UI08 不在其中，等于默认已完成**。实际 `ReactionChip.kt`（106 行）**零 `animate*/scale/graphicsLayer`**；全仓 `reaction` + `scale/animate/spring` 联合 grep **0 命中**；无任何提交引用 UI08 | `grep -rniE "reaction" --include=*.kt . \| grep -iE "scale\|animate\|spring"` → 空 | **重开 #167 或新建票**：给 `ReactionChip` 加按压微缩放（`animateFloatAsState` + `graphicsLayer`） |
| **D-02** | `feature/issue/.../IssueDetailScreen.kt:512` | `override fun onCodeCopy(code: String) {}` **空实现** —— Issue 正文 WebView 里的代码块复制按钮点了没反应。**反证是漏接线而非设计**：`RepoDetailScreen.kt:1750-1753` 同一回调已实现 `ClipboardManager` 写入 | 直接读文件确认；`FEEDBACK.md:47` 也把它记为"未实现功能" | 并入修复票：照 `RepoDetailScreen` 实现 |
| **D-03** | `feature/pullrequest/.../PullRequestTabContent.kt:196`、`:205` | PR 正文两处空实现：`onCodeCopy {}`（同上）+ `onCheckboxClick {}`（**PR 正文任务列表反向同步缺失**，而 Issue 侧 `IssueDetailScreen.kt:522` 已接） | 已读 `PullRequestTabContent.kt:196-207` 原文 | 并入 D-02 同一票 |
| **D-04** | `core/datastore/.../DefaultUserPreferencesRepository.kt:180`（`glass_bottom_sheet`）+ UI `AppearanceSettingsSection.kt:143-153` | **典型"有 UI 有持久化但无消费点"死设置**：开关真实存在且**可点可拨**（`enabled = uiState.glassPerItemEnabled`）、值真实落盘、`AppThemeHost.kt:82` 真实读取 → `GlassSettings.kt:33` → 但**全仓没有任何 `enabledFor(GlassScope.BOTTOM_SHEET)` 调用**（7 个真实调用点只用 PANEL/BOTTOM_BAR/TOP_BAR）。**拨动开关屏幕上什么都不会变**。⭐ **该文件自己的 KDoc 恰好禁止这种开关**：`AppearanceSettingsSection.kt:66-68` 明文写"「图标风格」入口随 AppIcon 消费基建落地而解禁…**不再是「点了没反应」的空开关（FEEDBACK #6 的先例要求）**"——`iconStyle` 因消费已接线而解禁，`codeFont`/`lineNumbers` 因未接线而保持隐藏，**唯独 `glass_bottom_sheet` 在消费缺失的情况下被放了出来，违反同文件政策** | `grep -rn "BOTTOM_SHEET" --include=*.kt .` 生产命中仅 enum 常量 + switch 分支；其余全是测试。根源是 `c24d353`（#167 UI03）落了开关、而 UI22（BottomSheet 实际玻璃）未实现 | 与 UI22 同一根因：真机确认 Haze 跨 window 可行性后接线；**在此之前应按同文件政策隐藏该入口**（一行改动，立刻消除"假开关"） |
| **D-05** | `DefaultUserPreferencesRepository.kt:189` + `UserPreferencesRepository.kt:74` + `SettingsUiState.kt:25` | `code_line_numbers` **死设置**：编辑器**硬编码** `isLineNumberEnabled = true`（`CodeEditorView.kt:75`、`MarkdownEditorView.kt:81`）；无 UI 控件；`setCodeLineNumbers` 0 调用者；`AppThemeHost` 收集 10 个 flow 但不含它 | `grep -rni "lineNumbers" --include=*.kt .` 生产仅 2 处（VM/UiState，从未渲染） | ⚠️ **降级为 P2**：这是**有意延后且已两处书面说明**——`458450c`（PR #87）body 写"**隐藏 iconStyle/codeFont/lineNumbers 死设置入口（FEEDBACK #6先例），DataStore 字段与 ViewModel 写入口保留**"，`AppearanceSettingsSection.kt:67-68` KDoc 亦写明"代码字体 / 行号仍隐藏（待 Sora 配置接线）"。**真问题是它没进 `project-status.md` 的挂账表**（那里只挂了 UI04/UI10/UI17/UI22/UI14 等），导致下一个人无法从状态文档发现它 | 二选一：接 Sora 或删 key+setter；**无论选哪个，先补进 project-status §3.1 挂账表** |
| **D-06** | `DefaultUserPreferencesRepository.kt:188` + `SettingsUiState.kt:24` | `code_font` **死设置**：`SettingsScreen.kt` 从不读 `uiState.codeFont`；无 UI 控件；无编辑器消费者；`setCodeFont` 0 调用者。连带 4 条死字符串（`settings_code_font*`） | 同 D-05 方法；`grep -rn "codeFont" core/editor/src/main feature/editor/src/main app/src/main` → 空 | 同 D-05（可合并为一票）；同样**先挂账** |
| **D-07** | `feature/settings/.../DeveloperSettingsSection.kt:168-188` | `RateLimitRow` **永久显示"待接入"**（`settings_rate_limit_unavailable`），而数据**早就有了**：`RateLimitStore.kt:49` 接口 + `EtagCacheInterceptor.kt:27,80` 每次 GET 写入，且 `feature:search` 已在消费（`SearchRateLimitWarning.kt`）。T24 票面把"配额"算作已交付 | 已读 `DeveloperSettingsSection.kt:168-188` 原文；注释自述"剩余配额占位行（待 REST 通道配额 API 接线）" | **新票 P2**：接线 `RateLimitStore` 到该行（数据链路已通，工作量小） |
| **D-08** | 5 个 `ModalBottomSheet` 站点：`RepoPickerSheet.kt:48`、`IssueDetailScreen.kt:916/1153`、`LineCommentSheet.kt:55`、`ReviewSheet.kt:75`、`PullRequestDetailScreen.kt:997` | **UI22 BottomSheet 玻璃确认 ABSENT**：每个站点 `GlassSurface=0 / haze=0 / BOTTOM_SHEET=0`。已诚实挂账（§3.1），此处登记为**死设置 D-04 的根因** | grep + 逐站点读 | 保持挂账（需真机），但**应先隐藏 D-04 的开关**避免"假开关" |
| **D-09** | `core/ui/.../AppNavHost.kt:326-330` | Discussion 深链落到 `PlaceholderSearchScreen()` —— 用户可见的"敬请期待"页。属有意的崩溃规避，但**深链一进去就是占位页** | 已读 `AppNavHost.kt:328` | 低优先，登记备查 |

### 2.3 P2 文档/结构类（与 §3 合并，此处只列死代码）

| # | 位置（文件:行） | 问题 | 证据 | 建议动作 |
|---|---|---|---|---|
| **D-10** | `core/ui/.../PlaceholderScreen.kt:40` | **孤儿文件**（零调用者）。`MainActivity.kt:54` **import 了它却从不调用**（另一处 `:199` 只是注释）；被 #166 `ReposScreen` 取代后未删 | `grep -rn "PlaceholderScreen" --include=*.kt .` → 生产仅 decl + 死 import | 删文件 + 删 import |
| **D-11** | `core/ui/src/main/kotlin/.../screens/ReposScreen.kt:22` | **孤儿占位页**，全仓零引用（被 `feature/repo/ReposScreen.kt` 取代；同名遮蔽了基于名字的扫描） | `grep -rn "screens\.ReposScreen\|PlaceholderReposScreen" --include=*.kt .` → 空 | 删文件。⚠️ **不要整目录删**：同目录 `screens/SearchScreen.kt:22` **仍在使用**（`AppNavHost.kt:328`） |
| **D-12** | `core/ui/src/main/kotlin/.../screens/ProfileScreen.kt:33` | 孤儿 + `AppNavHost.kt:32` 死 import（该文件无 `ProfileScreen(` 调用） | 同上方法 | 删文件 + 删 import |
| **D-13** | `app/src/main/java/.../auth/GuestWelcomeScreen.kt:32` | **仅测试存活的 UI**：生产调用者 0，只被 `GuestWelcomeScreenScreenshotTest.kt` 引用 → 截图基线反而**掩盖**了它已死。游客路径实际走 `LoginScreen` 的 `onBrowseAsGuest` | `grep -rn "GuestWelcomeScreen" .` → doc + decl + test×2 | 删 UI + 删基线，或明确保留为 v2 预留并加注释 |
| **D-14** | `core/designsystem/.../component/ContrastRatio.kt:29` `meetsWcagAa()` | `src/main` 公共 API，**生产零调用**，仅被 `LabelChipColorsTest.kt:87,89` 调用 → 作为"测试预言机"打进 release 产物（其兄弟 `contrastRatio()` 确在生产路径 `LabelChipColors.kt:95`） | 两处调用全在测试 | 移到 `core:testing` 或标 `@VisibleForTesting` |
| **D-15** | `core/github-data/.../user/UserRepository.kt:15` + `DefaultUserRepository.kt:27` | `getCurrentUser()` **死契约 + 死实现**：仅接口/实现/测试引用，生产 0 调用者（实现含 GraphQL→REST 降级，写得挺完整） | 同上方法 | 删或明确保留给后续票 |
| **D-16** | `IssueDetailScreen.kt:693,715` ↔ `PullRequestDetailScreen.kt:781,848` | `StatusChip` / `LabelChip` **两模块各一份私有重复实现**（均存活但重复）——与 §3.1 已挂账的"PR 评论 Sheet 与 Issue 统一"同源 | 名字 grep + 调用点 | 并入 §3.1 的"抽共享组件"票，一次消除 |
| **D-17** | `tmp/test.txt` | **草稿文件提交进 main**（内容 `test from app` / `add line 2`），由 2 个 junk 提交引入且 `tmp/` **未被 .gitignore** | `git ls-files tmp/` → `tmp/test.txt`；`git check-ignore` → 未忽略 | 删文件 + `.gitignore` 补 `tmp/` |

### 2.4 明确排除的误报（不要为此开票）

| 项 | 为什么不是缺口 |
|---|---|
| `RelativeLinkRewriter` 被删 | Task B 切 WebView 主渲染后相对链接由 WebView 改写承担，**属有意替换**（`fe51bf6`），非功能丢失 |
| core:ui `NotificationPanel_*` 基线被删 | `0d8c64f refactor(ui,navigation)!` 带 `!` 标记 + 明确理由（通知#88 起改为覆盖面板，不再有 destination），**规范做法** |
| `PR #58` 的 7 项修复曾 `cee1789` revert | **已全部重新落地**：`configChanges="locale\|layoutDirection"`（语言切换）✓、`PlaceholderScreen.kt`(72 行) ✓、`ThemeMode.kt`(69 行) ✓、游客直进/主题跟随/401 分类/底部胶囊由 `8cc7735`/`e71a3c4` 重做 ✓ —— **净零丢失** |
| `FileViewerScreen.startSearch()` "有句柄无 UI" | **假警报**：`CodeEditorController.kt:63` → 调用点 `FileViewerScreen.kt:113`；`jumpToLine()` → `:224`，均为顶栏 `IconButton`，真实可达。与审计描述的"悬浮按钮"形态不同而已（`project-status.md:87` 已记） |
| UI01/02/03/05/06/07/10/11/12/14/15/16/18/19/20/21/27 | **均已实现**（逐项 `file:line` 见 §1.1）；UI10 由 `e255a61` 落地 A 版 |
| `PrototypeReadmeComparisonScreen` 零引用 | prototype 模块，注释自述 inert，有意为之 |
| `PullRequestDetailScreen.kt:987` KDoc"写接口接入前 Submit 仅给暂未开放反馈" | **纯注释漂移**（Submit 已完整接线 `:1036 → VM:148-152 → Repository:116-122 → IssueApi:190`）；只改注释即可 —— 与 §3 Doc-17 合并 |
| detekt 79 处 `@file:Suppress` | 均有理由注释（T3 先例）；`maxIssues: 0` 且**无 baseline.xml**，属健康 |

---

## 3. 文档漂移清单

| # | 文档:行 | 文档声称 | 代码/历史现实 | 建议修正 |
|---|---|---|---|---|
| **Doc-01** | `AGENTS.md:10` | "**当前状态（2026-09-06）**：`main@2b7071d`。**T1–T26 中 25 票已合入**，**唯一未开工的功能票是 T25（#26）**；…9 张分类票 #163–#171——**当前活动票就这 9 张**" | HEAD 是 `a48ede1`；T25 **已部分合入**（`cba99e9`/PR #186）；**#163–#170 全部关闭合入**，#171 亦已关闭。**四处均失真** | 按 `project-status.md` 重写"当前状态"段 |
| **Doc-02** | `docs/agents/project-status.md:4` | "本版修正 …§7 D01「文档三处失真」：**AGENTS.md / project-status.md / FEEDBACK.md 已与 gh 票面 + git 历史对齐**" | **提交级证伪**：`git log -1 -- AGENTS.md` ＝ `9d66f04`、`git log -1 -- FEEDBACK.md` ＝ `9d66f04`（均 2026-09-10），而 `project-status.md` 自身在 `331e6a8`（2026-09-11）；**9d66f04 之后共有 44 个提交**。三处对齐**只在 9d66f04 那一刻成立** | 要么在本版回写另两处，要么把这句改成"对齐时点：2026-09-10" |
| **Doc-03** | `project-status.md:84`（§3.1） | "**UI10 README 头部收起** ｜ #167 ｜ 设计文档写明「用户构思中，实现时给两版效果对比」——**需要你的两版对比**，不在无人值守时替你拍板" | **`e255a61` 已合入**："feat(repo,markdown): README 下滑收起仓库头部（#167 UI10，**A 版跟手渐隐**）"，时间 **2026-09-11 20:44**，晚于 project-status 最后一次更新（`331e6a8`，06:51）**14 小时** | 从 §3.1 移除 UI10 |
| **Doc-04** | `project-status.md:13` ↔ `:18` | 第 13 行："M3 …✅ 完成（T1–T26 中 **24 票**）"；第 18 行："已完成（T1–T26 中 **25 票**…）" | **同文件自相矛盾**；T1–T26 去 T25 应为 **25 票** | 统一为 25 |
| **Doc-05** | `project-status.md:73` | "#167 …🔶 关闭（PR #180/#184/#189/#190）；**UI04/UI10/UI17/UI22** 见 §3.1" | **漏列 UI08**——UI08 既未实现也未被挂账，被静默吞掉（D-01） | 补挂账或补实现 |
| **Doc-06** | `FEEDBACK.md:47` | "#22 …**复制按钮 🔵 新功能未做**" | **代码块复制按钮早已实现**：`EnhancedCodeBlock` + `CopyFeedbackState` + `code_copy` 文案（task-audit §8 OK01 亦确认） | 改 ✅ 已修复 |
| **Doc-07** | `FEEDBACK.md:16`（#5）、`:49`（#24） | 底部图标实心/空心"🔵 设计待定（Material Symbols 大票未落地）" | **已落地**：`996dc18` 三档图标风格 + `AppIcon` 统一入口 + 底栏仓库图标语义（#168 UI12/UI23） | 改 ✅ 已修复 |
| **Doc-08** | `FEEDBACK.md:17`（#6） | 设置图标风格"✅ 已修复（**消费点未落地前隐藏入口**）" | `996dc18` 已落地消费点，入口**不再需要隐藏**；该行描述已过期 | 改 ✅ 已修复（消费点已落地） |
| **Doc-09** | `FEEDBACK.md:67`（#28）、`:68`（#29） | WebView 融合"🔵 架构转向调研完成…**待用户决策落地**" | **已拍板并落地**：ADR-0007 + Task B（PR #70 `9fd550a` / #73 `4dde028`）WebView 主渲染已上线 | 改 ✅ 已落地 |
| **Doc-10** | `FEEDBACK.md:67`（#9）"卡片灰灰的" | 🔵 设计待定 | `e97df12`「仓库大分区——列表/网格切换」+ PR #182/#183 已重构卡片风格；project-status §4 自述"PiliPlus 卡片风格细节 ✅ 闭环（PR #182）" | 改 ✅ 或改挂 UI04 相关 |
| **Doc-11** | `FEEDBACK.md:80` | "首页 = 横向滚动 Tab + HorizontalPager（**Trending/News/Issues/PRs**）" | **与权威规范冲突**：`docs/ui-design.md:61` 拍板为"**动态 / Issue / PR**"，代码 `HomeTab.kt:9-12` 正是 FEED/ISSUES/PULL_REQUESTS 三 Tab；Trending 已改为 feed 内小节（`TrendingSection.kt`） | 改 FEEDBACK 行以对齐 ui-design |
| **Doc-12** | `FEEDBACK.md:69`（#30 全局背景图） | 🔵 待 grill | **属实**（UI04 确认无实现，见 §1.1） | 无需改（本行是 FEEDBACK 里少数仍准确的） |
| **Doc-13** | `task-audit-2026-09-06.md:279`（§8 **OK11**）"配额展示 ✅ 已实现 ｜ `DeveloperSettingsSection.RateLimitRow`" | 把 `RateLimitRow` 当成"配额展示已完成"的**正面证据** | **反了**：该行是永久占位（`settings_rate_limit_unavailable`，D-07）。这是旧审计的**假阳性**——用"存在一个叫 RateLimit 的行"证明功能完成 | 把 OK11 降级为 L-项并接线 |
| **Doc-14** | `AGENTS.md:50` | `./gradlew :core:test --tests "*XxxRepositoryTest*"   # 单模块单类` | **命令必失败**：`settings.gradle.kts` 无 `:core` 项目（只有 `:core:common`…`:core:testing`），`grep -c '":core"'` = 0 | 改成真实模块，如 `:core:github-rest:test` |
| **Doc-15** | `docs/agents/workflow.md:43`、`:46` | 门禁清单 `spotlessCheck detekt konsistCheck :app:lintDebug :app:testDebugUnitTest :app:verifyRoborazziDebug :app:assembleDebug`；另写"覆盖率门禁：`./gradlew jacocoTestReport`" | **漏 3 类 CI 硬门禁**：`coverageVerify`、6 个模块级 `verifyRoborazziDebug`、`diffCoverageCheck`（`ci.yml:147-152/179/187-193`）。且 `jacocoTestReport` 是**每模块注册**（`build.gradle.kts:281`），根项目无此任务 → 无参运行会歧义失败 | 与 AGENTS 一起补齐（这正是"本地全绿 CI 必挂"的复发土壤） |
| **Doc-16** | `AGENTS.md:112` | "架构决策记录（**ADR-0001~0006**）｜`docs/adr/`" | `docs/adr/` 已有 **0007**，且 AGENTS.md 正文第 20 行自己就引用"ADR-0007 拍板" → **同文件索引与正文互斥** | 改 0001~0007 |
| **Doc-17** | `feature/pullrequest/.../PullRequestDetailScreen.kt:987` | KDoc："写接口接入前 Submit 仅给出「暂未开放」反馈" | Submit 已完整接线（`#192`/`584468c`）：`:1036 → viewModel::submitComment → PullRequestDetailViewModel.kt:148-152 → PullRequestRepository.kt:116-122` | 删/改该注释 |
| **Doc-18** | `docs/agents/project-status.md:41`（T20 行） | "Repositories/Starred/Followers/Following **四路** Paging" | 代码已多出 **Gists** 一路（`ad05201` + `GistsScreen` + `AppRoute.Gists`）；**文档少记**（方向与 Doc-05 相反） | 补记 Gists（避免下次有人重复实现） |
| **Doc-19** | `docs/ui-audit-2026-08-21.md` §2.1 #3 | "5 处死设置" | 现存 **2 处**（`glass_bottom_sheet`、`code_font`/`code_line_numbers`）；`cornerScale`/`motionScale`（`AppThemeHost.kt:49-50`）与 `iconStyle`（`:75`）**已接线** | 收窄该行并注明已接线项 |

> **小结**：`project-status.md` 本身**质量最高、最可信**（挂账项逐条对码属实）；漂移集中在 **`AGENTS.md`（整体停更一个大波次）** 与 **`FEEDBACK.md`（7/31 条状态过期，且方向多为"已修但写作未修"）**。`AGENTS.md` 是被 30+ 工具读取的入口文件，**危害最大**。

---

## 4. 测试与门禁缺口

### 4.1 结构性漏洞（"声称有兜底，兜底不存在"）

| # | 位置 | 问题 | 证据 |
|---|---|---|---|
| **G-01** | `build.gradle.kts:166-182` 排除模式 + `feature/pullrequest` | **`:feature:pullrequest` 三重盲区**：① **0 张截图基线**（`find feature/pullrequest -path '*screenshots*'` → 空）；② **不在任何 CI 门禁**（`grep -c "feature:pullrequest" ci.yml nightly.yml` = **0/0**）；③ 6 个 UI 类被 JaCoCo 排除，**排除理由写的是"纯 Composable，单测不可达，**截图/真机兜底**"** —— 而该模块的截图兜底**根本不存在**。`**/*DiffView*`、`**/*LineCommentSheet*`、`**/*ReviewSheet*`、`**/*MergeBox*`、`**/*TabContent*`、`**/*TimelineItems*`（`build.gradle.kts:175-182`） | 三条证据均独立验证；且"单测不可达"被本项目自己的 `MergeBoxClosedStateTest.kt`（纯 JVM Robolectric + `createComposeRule()` + `assertIsEnabled/assertIsDisplayed`）**证伪** —— 正确措辞应是"测试尚未编写" |
| **G-02** | `build.gradle.kts:100-116` `coverageThresholds` | **只有 14/26 个模块有覆盖率阈值**。无阈值的**逻辑模块**：`:feature:pullrequest`(21 生产文件/8 测试)、`:core:ui`(15/10)、`:feature:search`(14/7)、`:core:database`(10/5)、`:core:editor`(9/4)、`:core:common`(`LogRedaction`)、`:core:data`(7/0)、`:feature:editor`(2/1)。声明豁免仅 `:app`/`:feature:auth`（`:90` 注释）与 `:prototype:readme-comparison`（`:243-244`） | 阈值 map 14 项 vs `settings.gradle.kts` 26 模块 |
| **G-03** | `build.gradle.kts:312` | `onlyIf("存在单元测试执行数据") { execData.files.any { it.exists() } }` → **有阈值但无 exec 数据时静默 SKIP 而不是失败**。"本地全绿 CI 必挂"的镜像：**CI 也会静默放过** | 已读该行原文 |
| **G-04** | `build.gradle.kts:172` + `:398` | `**/*EditorView*.class` **误命中 **`MarkdownEditorViewModel.class`（`fnmatch('MarkdownEditorViewModel.class','*EditorView*.class')` = **True**） → **一个带完整单测的逻辑 ViewModel 被踢出覆盖率分母**。`build.gradle.kts:165` 的注释恰恰声称"该模式专为避免误伤"——**注释与事实相反**。同一过宽模式在 diff 门禁 `:398` **再次出现**（双重漏洞） | `feature/editor/.../MarkdownEditorViewModel.kt` + `MarkdownEditorViewModelTest.kt` **真实存在** |
| **G-05** | `build.gradle.kts:141`(`**/ui/**`)、`:143`(`**/designsystem/component/**`)、`:144`(`**/designsystem/token/**`) | **排除掉"有测试的纯逻辑"**：`core/ui/.../time/RelativeTime.kt`（有 `RelativeTimeTest.kt`）、`designsystem/component/ContrastRatio.kt`（`contrastRatio`/`cardGroupSegmentCorners`）、`designsystem/token/GlassRenderPolicy.kt`（有 `GlassRenderPolicyTest.kt`）。**测试在跑，覆盖行永远不计数** | 三个文件与其测试文件均已确认存在 |
| **G-06** | `build.gradle.kts:480-481` | diff 门禁把**不在 JaCoCo 报告里的行计为"已覆盖"**：`val uncovered = codeLines.filter { it in known && it !in covered }` → `totalCovered += codeLines.size - uncovered.size`。后果：**在排除类里新增的代码恒得 100%**，"新增代码 ≥80%"对最需要门禁的区域完全失效（`noReport` 仅 `logger.warn`，`:496-498`） | 已读该段原文 |
| **G-07** | `buildSrc/src/main/kotlin/appdev.android.library.gradle.kts:61-65` | `tasks.configureEach { if (name.startsWith("lint")) enabled = false }` → **所有 library 模块的 lint 任务被禁用**，其 `:29-30` 的 `lint { abortOnError = true }` 成为**死配置**；`nightly.yml:59` 里的 `:core:ui:lintDebug` 是 **no-op**。真实 lint 覆盖面 = `:app` 一个模块 | 已读 `buildSrc` 原文；有注释解释根因（LintJarApiMigration 与 Compose 1.11 不兼容），**属已知妥协，但文档未记** |
| **G-08** | `ci.yml:289-291` | 分支保护只要求 **`Quality Gate` 一个 check**（`enforce_admins:false`、`allow_force_pushes:true`、无 required review、rulesets 空）→ **模拟器/Maestro 截图 job、nightly、release-dry-run、record-screenshots 全部不阻塞合并**。另有 `app/build.gradle.kts:266-269` `isFailOnNoMatchingTests = false`（konsist 测试类消失也绿）、`ci.yml:351-355` "Verify screenshots collected" `if: always()` 仅 echo **无法失败** | 已读 workflow 与保护配置 |
| **G-09** | `nightly.yml:278` | `notify-failure` 的 `needs:` 列表**漏掉 `screenshot-drift` 与 `screenshot-refresh`** → 这两个 job 失败**不会开 issue**，静默腐烂 | 已读 `nightly.yml` |
| **G-10** | `:prototype:readme-comparison` | **4 张基线 + 1 个截图测试 + roborazzi 插件**，且经 `app/build.gradle.kts:184` 以 `debugImplementation` **打进 debug APK** —— 但**没有任何 workflow 验证它**，覆盖率也被跳过（`build.gradle.kts:243-244`） | 基线文件已确认存在；`grep -c "prototype" ci.yml` = 0 |

### 4.2 测试覆盖缺口

| # | 问题 | 证据 |
|---|---|---|
| **G-11** | **`:core:data` 完全无法承载测试**：7 个生产文件（`model/` 下 PageCursor/Release/Repository/SearchCodeItem/SearchIssue/Tag/User），`core/data/build.gradle.kts` **连 `testImplementation` 都没有**（grep 0 命中）→ 不补构建配置就无法补测试 | 已核 `build.gradle.kts` 与目录 |
| **G-12** | **21/27 个 `*Screen(` 既无截图基线也无 Compose UI 测试**。基线只有 35 张、仅覆盖 5 个屏（GuestWelcome/Login/IssueList/IssueDetail/FileEdit）。缺基线的高价值屏：**`PullRequestListScreen`/`PullRequestDetailScreen`/`PullRequestCreateScreen`/`BranchesScreen`**、`HomeScreen`、`RepoDetailScreen`、`FileViewerScreen`、`SearchScreen`、`MarkdownEditorScreen`、`ProfileScreen`、`GistsScreen`、`SettingsScreen` | 35 PNG vs 27 Screen 声明 |
| **G-13** | **T17 合入时零新增测试**：`git diff --name-status e44af18^1 e44af18` → 7 个测试文件**全为 `M`（修改），无一个 `A`（新增）**。`ReviewSheet.kt`/`MergeBox.kt` 无测试无基线；`MergeBoxClosedStateTest.kt` 是 **15 天后**由另一张票（`2e627b8`，自述 `#163 L03`）补的 | git 命令输出 |
| **G-14** | T16/T21/T23 的 UI 类零测试零基线：`PullRequestDiffView.kt`、`LineCommentSheet.kt`、`PullRequestTabContent.kt`、`MarkdownEditorScreen.kt`、`BranchesScreen.kt`、`PullRequestCreateScreen.kt` | 逐文件无同名测试 |
| **G-15** | `core/markdown/src/test/.../WebViewDarkModePolicyTest.kt`：**4 个 `@Test` 零断言**，仅"调用不抛异常"即通过。其自身 KDoc `:48` 写明期望行为是"**一次 `WebSettingsCompat` 都不该调**"，却**没用 `verify(exactly = 0)`**（`mockk<WebSettings>(relaxed = true)` 其实已就位）→ 无法区分"正确跳过"与"函数体被清空" | 已读该测试文件 |
| **G-16** | CI PR 门禁 **7 个 verify 目标** vs nightly/record **8 个**：`:feature:issue`（4 张基线）因 `ci.yml:133-136` 记录的**真实技术原因**（真实 VM+Paging → 首帧无限动画 → `waitForIdle` 不收敛 → 420s timeout）仅在 nightly，**PR 阶段 UI 回归要等一天**。属**已诚实记录的技术债**，非隐瞒 | `ci.yml:143-153` vs `nightly.yml:156-165` |
| **G-17** | **全仓 0 个 `androidTest` 目录、0 处 `androidTestImplementation`** —— 188 个测试全为 JVM。符合架构决策，但**真机 WebView/Intent/权限行为零自动化** | `find . -type d -name androidTest` = 0 |
| **G-18** | `I18nParityTest` **文档声称属实**（`app/src/test/.../konsist/I18nParityTest.kt`，扫描全仓 15 en / 14 zh-rCN / 717 key，`project-status.md:97` 准确），但有 3 处**未记录的盲区**：① `repoRoot` 由 `File(".").parentFile` **cwd 推导** → "全模块"保证是"意外正确"，只有跑 `:app` 任务时成立；② 用**正则而非 XML 解析器**（`:77` 只匹配开标签）→ **`<plurals>`/`<string-array>` 项级 parity 不校验**；③ `translatable="false"` 为字面量精确匹配，当前"恰好"无误报 | 已读该测试文件与 `app/build.gradle.kts` lint 配置 |

### 4.3 门禁命令对齐（复现"本地全绿 CI 必挂"的土壤）

| 项 | 内容 |
|---|---|
| CI 实际跑的门禁（`quality` job） | `spotlessCheck`(`:74`)、`detekt`(`:77`)、`:app:lintDebug`(`:80`)、`konsistCheck`(`:113`)、`:app:testDebugUnitTest`(`:116`)、**7× `verifyRoborazziDebug`**(`:145-153`)、`:app:assembleDebug`(`:166`)、`coverageReport coverageVerify`(`:179`)、`diffCoverageCheck -PdiffCoverageThreshold=80`(`:187-193`) |
| AGENTS.md 文档的清单（`:36-43`） | 8 条：spotlessCheck / detekt / konsistCheck / :app:lintDebug / :app:testDebugUnitTest / coverageVerify / :app:verifyRoborazziDebug / :app:assembleDebug |
| **CI 有、文档无** | **6 个模块级 `verifyRoborazziDebug`**、**`diffCoverageCheck`**、`coverageReport`、`--no-daemon` |
| **文档有、CI 无** | 3 条"快速验证"命令 |
| **文档里的坏命令** | `AGENTS.md:50` `:core:test`（无此项目）；`workflow.md:46` `jacocoTestReport`（根项目无此任务，会歧义失败） |
| **更正任务书假设** | 本版 `AGENTS.md:41` **已经列了 `coverageVerify`**——真正的漏项是**模块级 verify 与 diff 覆盖**，以及 `workflow.md` 两处都漏 |

---

## 5. 优先级排序的问题清单

| 优先级 | 问题 | 证据 | 建议动作 |
|---|---|---|---|
| **P0-1** | **`AppRoute.PrCreate` 白屏**（T23）：`PullRequestCreateScreen`(292 行) 从未接线 | `grep -c "createPullRequestScreen" MainActivity.kt` = 0；`AppNavHost.kt:105` 默认 `{ _,_,_ -> }`；入口 `PullRequestListScreen.kt:92 → MainActivity.kt:359` | **直接修**（补 lambda）+ 加"lambda 默认值守卫"门禁 |
| **P0-2** | **`AppRoute.Branches` 白屏**（T23）：`BranchesScreen`(358 行) 从未接线 | `grep -rn "BranchesScreen"` 仅声明 + 注释；入口 `MainActivity.kt:269` | **直接修**（同一个 PR 一起修） |
| **P0-3** | **OAuth `clientId` 是占位符且无注入点** → 主认证开箱必失败、发布阻断 | `OAuthConfig.kt:22`、`AuthModule.kt:44`；`buildConfigField` 全仓 0 命中 | **新建票 P0** |
| **P1-1** | **UI08「Reaction 点击微缩放」静默丢失**（#167 票面默认已完成） | `ReactionChip.kt` 零动画；全仓 reaction+animate 0 命中；`project-status.md:73` 未挂账 | **新建票**（或重开 #167） |
| **P1-2** | **`:feature:pullrequest` 三重盲区**：0 基线 + 不在门禁 + JaCoCo 排除理由（"截图兜底"）不存在 | G-01 三条证据 | **新建票**：补基线 + 进 `ci.yml:143-153` + 改注释 |
| **P1-3** | **`glass_bottom_sheet` 死设置**：开关能拨、值落盘、屏幕上无任何变化 | `BOTTOM_SHEET` 无 `enabledFor` 调用点 | 与 UI22 同票：真机确认后接线，否则**隐藏入口**（FEEDBACK #6 先例） |
| **P1-4** | **`code_font` / `code_line_numbers` 死设置**（+4 条死字符串），写入口保留但从未挂账 | `CodeEditorView.kt:75` 硬编码行号；`setCodeFont`/`setCodeLineNumbers` 0 调用者 | 并入一票：接 Sora 或删除；**先挂账** |
| **P1-5** | **覆盖率门禁 12/26 模块无阈值**，含 `:feature:pullrequest`/`:core:ui`/`:feature:search`/`:core:database`/`:core:editor` | `build.gradle.kts:100-116` vs `settings.gradle.kts` | **新建票**：补阈值（缺失即失败而非 SKIP，G-03） |
| **P1-6** | **`**/*EditorView*.class` 误命中 `MarkdownEditorViewModel`** → 有测试的逻辑 VM 出分母；diff 门禁同款 | `fnmatch` 实测 True；`build.gradle.kts:172` + `:398` | **直接修**：改 `*EditorViewController*` 等不歧义模式 |
| **P1-7** | **diff 门禁把"报告外行"计为已覆盖** → 排除类里新增代码恒 100% | `build.gradle.kts:480-481` | **直接修**：报告外行计为未覆盖，或至少硬失败 |
| **P1-8** | **library lint 全禁用** → `abortOnError` 是死配置、nightly `:core:ui:lintDebug` 是 no-op | `appdev.android.library.gradle.kts:61-65` | 在 `AGENTS.md`/`testing-strategy.md` **写明真实 lint 覆盖面 = :app**，避免误以为有模块级 lint |
| **P1-9** | **`RateLimitRow` 永久"待接入"** 而数据链路早已存在；旧审计 OK11 误判为已完成 | D-07；`task-audit:279` | **直接修**（工作量小）+ 修正 OK11 |
| **P2-1** | **`AGENTS.md` 整体停更一个大波次**（T25 已部分合入、#163–#170 全关闭均未回写） | Doc-01；`git log -1 -- AGENTS.md` = `9d66f04`，其后 44 提交 | **直接修**（入口文件，危害最大） |
| **P2-2** | **`FEEDBACK.md` 7 条状态过期**（#5/#6/#9/#22/#24/#28/#29 均已修但写作未修）+ 首页 Tab 描述与 ui-design 冲突 | Doc-06~Doc-11 | **直接修** |
| **P2-3** | **`project-status.md` 3 处**：三处对齐声明过期（Doc-02）、UI10 挂账已落地（Doc-03）、24/25 票自相矛盾（Doc-04） | 见 §3 | **直接修** |
| **P2-4** | **Issue/PR 正文 `onCodeCopy {}` 空实现**（复制按钮无反应）+ PR `onCheckboxClick {}`（任务列表不同步） | `IssueDetailScreen.kt:512`、`PullRequestTabContent.kt:196,205`；反证 `RepoDetailScreen.kt:1750-1753` | **并入一票** |
| **P2-5** | **`:core:data` 无法承载测试**（无 `testImplementation`） | G-11 | 并入 G-15 同一工程票 |
| **P2-6** | **21/27 Screen 无基线**；T17 合入时零新增测试 | G-12/G-13/G-14 | 并入测试票，按 ROI 排序（先 PR 三屏） |
| **P2-7** | **文档命令坏链**：`AGENTS.md:50` `:core:test`；`workflow.md:43/46` 漏 3 类门禁 + `jacocoTestReport` 非根任务 | Doc-14/Doc-15 | **直接修**（低成本、防复发） |
| **P2-8** | **konsist/截图守卫可空转**：`isFailOnNoMatchingTests=false`；`ci.yml:351-355` 仅 echo；`nightly.yml:278` 漏 needs 致截图漂移失败不开 issue | G-08/G-09 | **直接修** |
| **P3-1** | 死代码清理：`PlaceholderScreen.kt`、`screens/ReposScreen.kt`、`screens/ProfileScreen.kt` + 2 个死 import（`MainActivity.kt:54`、`AppNavHost.kt:32`） | D-10~D-12 | 并入清理票。⚠️ **保留 `screens/SearchScreen.kt`**（仍在使用） |
| **P3-2** | `GuestWelcomeScreen` 仅测试存活（基线掩盖了它的死亡） | D-13 | 删或加注说明 |
| **P3-3** | `meetsWcagAa()` / `UserRepository.getCurrentUser()` 生产零调用者 | D-14/D-15 | 移 `core:testing` 或删除 |
| **P3-4** | `StatusChip`/`LabelChip` 双份实现 | D-16 | 并入"抽共享组件"票 |
| **P3-5** | `tmp/test.txt` 草稿文件在 main + `tmp/` 未 ignore | D-17 | **直接修** |
| **P3-6** | `AGENTS.md:112` ADR 索引漏 0007 | Doc-16 | **直接修** |
| **P3-7** | `I18nParityTest` 3 处盲区（cwd 推导、正则非 XML、plurals 项级不校验） | G-18 | 并入测试基建票 |

---

## 6. 提交历史健康度评价

### 6.1 Conventional Commits 合规率

| 指标 | 值 |
|---|---|
| 无 merge 提交总数 | **219** |
| 符合 `type(scope): description` | **201** |
| **合规率** | **91.8%** |
| 不合规 | **18** |

**18 个不合规提交的构成**：

| 类别 | 数量 | 明细 |
|---|---|---|
| 早期 T 票 squash 提交（无 type 前缀） | **15** | `T1: CI/CD…(#28)`、`T2: 架构护栏…(#43)`、`T3:`、`T4:`、`T5:`、`T6:`、`T7:`、`T8+T9:`、`T10:`、`T13:`、`T19:`、`T20:`、`T24:`、`T26:`、`Wave2: T4/T6 接线收尾(#50)` |
| `Revert` 前缀 | 1 | `cee1789 Revert "fix: 真机走查 7 问题修复…(#58)"`（Git 标准格式，可接受） |
| **junk 提交** | **2** | `88aad09 test:add test file`、`034d72c test:add line 2 in test file` —— **连冒号后空格都没有**，且内容是把 `tmp/test.txt` 草稿提交进 main |

> 结论：**91.8% 合规、且不合规集中在 2026-08-10~08-14 的最早期**。2026-08-15 之后**除 1 个 Revert 与 2 个 junk 外全部合规**，格式纪律实际很好。两个 junk 提交（冒烟测试误入 main）是唯一的真实瑕疵。

### 6.2 merge vs squash 策略一致性

| 指标 | 值 |
|---|---|
| PR 总数（可识别） | **74** |
| merge commit 保留历史 | **47** |
| squash（单提交 + `(#N)` 后缀） | **27** |

**问题：策略在时间上交错，而非按"复杂度"分层。**

- `#28`–`#62` 期（08-10~08-17）**混用**：`#59`/`#60`/`#63`/`#66` 用 merge commit，而同期的 `#61`/`#62`/`#65`/`#67`/`#69` 用 squash。
- **T1–T11、T13、T18 等 16 个功能票全部被 squash 成单个提交** —— 这些恰是**体量最大**的核心链路票（T5 网络层、T6 主题引擎、T11 文件树）。按 `docs/agents/workflow.md` 自述"复杂/多提交修复波用 merge commit 保留历史（不 squash）"，**这些票属于应保留历史的类型，却被 squash 了**，与自述策略相反。
- `#70` 之后（Task B 起）稳定使用 merge commit，策略才收敛。
- 后果：`T8+T9` 两票压成 1 个提交、`Wave2: T4/T6` 两票压成 1 个提交 → **提交粒度与票粒度不再 1:1**，历史不可按票回溯。

### 6.3 可追溯性：本仓最实质的历史缺陷

**T1–T26 的 26 张功能票，没有任何一个被自动关闭关键词引用过：**

```
git log --no-merges --pretty='%b' origin/main | grep -ciE "^(fixes|closes|resolves) #N"
→ #2..#27 全部为 0（26/26 零命中）
```

现存的自动关闭关键词**只覆盖后期的 ui-audit 波与审计补全波**：`Fixes #85`、`Fixes #87`、`Fixes #164`、`Fixes #168`(×5)、`Fixes #171`。

逐票精确口径（避免"完全没出现过"这种过强表述）：

| 情况 | 票号 | 证据 |
|---|---|---|
| 以描述性文字提及（非自动关闭关键词） | **仅 2 张**：#17(T16)、#26(T25) | `T16（Issue #17）`、`T16 Diff 行评论（Issue #17）`；`cba99e9 …（T25 / #26）` + body `Refs #26` |
| **从未以票号出现** | 其余 **24 张** | 含 T18：`git log --grep="#19"` → 零命中；T18 被 squash 成 `ab59234`（PR #62），**连 merge 记录都没有** |

> ⚠️ **一个容易误判的陷阱**：全仓 grep `#2`…`#27` 会得到一堆命中，但绝大多数是**同形异义的编号空间**——`ui-audit #15`/`#12`、`audit 缺陷 #2/#20`、`FEEDBACK #6/#17`、甚至 `#2 imagemagick 改脚本` 这类脚本步骤编号。**审计时若按 `#[0-9]+` 泛匹配会得出"票号有记录"的错误结论**；必须按 `^(fixes|closes|resolves) #N` 或语义上下文判定。

**后果**：
- 票↔PR↔issue 的关联**只存在于 GitHub PR body**（"Fixes #N"写在 PR 描述里），**不在 git 历史中**。`git log --grep="#17"` 找不到 T16，`--grep="#19"` 找不到 T18。
- 任何**仅凭 git 的溯源都会失败**；唯一可行路径是 `project-status.md` 的手工对照表 —— 而该表恰是 §3 中会漂移的文档（它已经漂移了：Doc-01/Doc-03/Doc-04）。
- **建议**：把 `Part of #N` / `Fixes #N` 写进提交 body 固化为规则。本波已有正面案例：`da7f3ec`、`ad05201`、`08c6750` 均带 `Part of #165`，`8ec6adf` 带 `Fixes #164 的 L04/L05/L06/L09 四条款` —— 说明习惯已养成，只是未回溯到 T1–T26。

### 6.4 回退与丢失

| 项 | 结论 |
|---|---|
| Revert 总数 | **1**（`cee1789` 回退 `72812b3`/PR #58） |
| 该 revert 的净影响 | **零丢失**——7 项修复全部由 `8cc7735`/`e71a3c4` 重新落地；已逐项验证 `configChanges="locale\|layoutDirection"`、`PlaceholderScreen.kt`(72 行)、`ThemeMode.kt`(69 行) 均在树上 |
| 被删除的生产文件 | 仅 3 类，**均有正当理由**：`RelativeLinkRewriter.kt`（Task B 取代）、`core/ui` 旧 `NotificationPanel`/`NotificationScreen`/`HomePager`/`HomeTabs`（`0d8c64f` 带 `!` 标记的 breaking 重构，迁移到 feature 模块）、`feature/pullrequest/util/RelativeTime.kt`（归并到 core:ui） |
| 被删除的**测试资产** | ⚠️ `core/ui/src/test/screenshots/HomePager_{light,dark}.png` + `HomePagerScreenshotTest.kt` 由 **T10 自己**（`39fed0c`）删除，`feature/home` **至今未重建** → 首页分区**永久失去截图回归**（G-12）。这是"票内自删测试资产"的典型 |

### 6.5 提交粒度与卫生

| 项 | 评价 |
|---|---|
| 平均粒度 | 良好：`feat`/`fix` 提交普遍单一职责，`git show --stat` 多为 3–10 文件 |
| 重复 subject | ⚠️ 2 个提交**subject 完全相同**：`eee3d94`(03:12) 与 `e756246`(03:32) 均为 `feat(profile,github-rest,data): Profile 统计行补 Star 总数（#166 UI21）`，相隔 20 分钟（后者是前者的补丁，却复用同一消息）→ 破坏"一个提交 = 一个逻辑变更"的可读性 |
| 草稿文件入库 | ⚠️ `tmp/test.txt` 至今在树上（D-17），`tmp/` 未 ignore |
| 已修复的历史问题 | `.github/gradle/init-mirror.gradle` 由 T5 `37f0cac` 删除（符合"仓库内保持官方源"约定）；`*.log` 已进 `.gitignore` |
| 好的实践 | 契约变更用 `!` 标记（`0d8c64f refactor(ui,navigation)!`）并写明理由；修复波提交带 `（#N / 审计编号）` 便于回溯；`git log` 中大量提交 body 记录了**实测证据与踩坑**（如 `cba99e9` 记录 HSPL 规则不能带标志位），可维护性显著高于平均水平 |

### 6.6 历史健康度总结

| 维度 | 评分 | 说明 |
|---|---|---|
| 提交信息格式 | **B+** | 91.8%；不合规集中在最早期；2 个 junk 提交 |
| 分支/合并策略 | **C+** | 27/74 squash，且**与自述策略相反**（大票被 squash、早期策略交错）|
| 提交粒度 | **B** | 多数单一职责；2 处多票合一、1 处重复 subject |
| 可追溯性 | **D** | **26 张功能票的 issue 号在 git 中零出现**，git 内无法按票回溯 |
| 提交内容质量 | **A-** | body 记录实测证据与坑，`!` 标记规范，revert 后完整重落地 |
| 测试随票交付 | **C** | T22/T12/T18 优秀；**T17 合入时零新增测试**；T16/T21/T23 的 UI 层零测试 |

---

## 附录 A：审计方法与可复现命令

```bash
# 全量提交清单与分布
git log --oneline --no-merges origin/main | wc -l          # 219
git rev-list --count --merges origin/main                   # 77
git log --no-merges --pretty=%s origin/main | sed -E 's/^([a-z]+)(\(([^)]*)\))?!?:.*/\1/' | sort | uniq -c

# 票 → 提交反查（T1–T26 无任何自动关闭关键词，改用 PR 号 + 分支名反查，见 §6.3）
git log --merges --pretty='%h %s' origin/main | grep 'pull request'   # 47 个
git log --no-merges --pretty='%h %s' origin/main | grep -E '\(#[0-9]+\)$'  # 27 个 squash

# 关键证据
grep -c "branchesScreen\|createPullRequestScreen" app/src/main/java/com/yumiru11/githubapp/MainActivity.kt   # → 0
grep -rn "BranchesScreen\|PullRequestCreateScreen" --include=*.kt . | grep -v '/build/'                      # → 仅声明/注释
sed -n '172p;312p;480,481p' build.gradle.kts
sed -n '100,116p' build.gradle.kts                          # coverageThresholds（14 项）
git log -1 --format='%h %ad %s' --date=short -- AGENTS.md FEEDBACK.md docs/agents/project-status.md
```

## 附录 B：本次审计的边界与未验证项

1. **未跑 Gradle**（遵约束）→ 所有结论为**静态证据**；`coverageVerify`/`spotlessCheck`/`detekt`/`konsistCheck` 的实际执行结果未实测。任何修复票的验证命令**必须含** `spotlessCheck + detekt + konsistCheck + coverageVerify`（删生产文件会动 JaCoCo 分母；删 i18n key 必须 en+zh-rCN 同删否则 `I18nParityTest` 挂）。
2. **未做视觉审查**（与 2026-09-06 审计同口径）；玻璃穿越感、动效体感、README 排版一律未评。
3. `CortexKit`/`.agents/skills` 等工具目录未审计。
4. 名称频次扫描**不可信用于 UI 入口判定**：`BranchesScreen`（358 行、完全孤儿）在词频扫描中因 `RepoDetailScreen.kt:3` 的**一句注释**被评为"存活"——这正是两个白屏 bug 存活 3 周的方法论漏洞。**建议门禁按"声明→调用点"路径校验，而非词频。**
