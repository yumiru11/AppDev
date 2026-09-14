# AppDev 项目状态（2026-09-14）

> 本文件是当前进度的**权威快照**。每张票合并/关闭后更新。配合 `docs/agents/workflow.md`（流程）、`AGENTS.md`（环境）与 `docs/agents/task-audit-2026-09-06.md`（全量审计）阅读。
> 本版修正 2026-09-06 审计发现的 §7 D01「文档三处失真」：AGENTS.md / project-status.md / FEEDBACK.md 已与 `gh` 票面 + git 历史对齐。
> **基线**：`main@3b43c74`（#293 阅读密度回滚，本轮最后一合；main CI 全绿 run `34807483090`）。本轮共合入 **62 张 PR**：波次 **#229–#291（60 张）** 全部 squash + 收尾 **#292**（docs 对账）/ **#293**（阅读密度回滚）—— 分段：前半 #229–#255（26 张，§2.1）、中段 #256–#266（11 张，§2.1）、收尾 #267–#271（5 张，§2.2）、设计系统/渲染/加固 #272–#291（18 张，§2.3/§2.4）。计数口径：#250 是 issue 非 PR；**#278 / #279 是 issue**（#278 = 全量 `.dp` 扫荡跟踪票，**开启中**；#279 = 其重复票，已关闭）；**无 open PR**。完整清单见 `docs/agents/remaining-backlog-2026-09-12.md`。

## 1. 里程碑概览

| 里程碑 | 状态 |
|---|---|
| M0 基建（Gradle 骨架/CI/架构守卫） | ✅ 完成 |
| M1 核心链路（网络/认证/主题/Markdown 渲染） | ✅ 完成 |
| M2 首个端到端（README 浏览） | ✅ 完成 |
| M3 主要功能域（首页/Issue/通知/Profile/设置） | ✅ 完成（T1–T26 中 24 票） |
| M4 全功能（PR 深化/编辑提交/分支） | ✅ 完成（T16/T17/T23 已合入） |
| M5 发布收尾（性能/签名 Release） | 🔶 进行中：Baseline Profile + i18n 覆盖断言 + RTL 基线 + 发布链路演练已落地（PR #186）；**冷启动实测 / macrobenchmark / 生产签名密钥仍需真机或 Secrets**（见 §3.2） |
| M6 审计补全与 UI 打磨（2026-09-06 立项） | ✅ 9 张分类票 #163–#170 **全部关闭并合入**；#166/#167 的少数挂账项见 §3.1 |
| M7 修复波 + 门禁/截图链路加固（2026-09-12/13） | ✅ **42 张 PR #229–#271 全部 squash 合入**：前半 26 张（#229–#255，明细见 §2.1）；中段 11 张（#256–#266，见 §2.1）；收尾 5 张（#267–#271，见 §2.2）—— AGP 9.1.1 迁移 + 全模块 lint 恢复（#270）、离线 KaTeX（#269）、设计系统落地计划 + ADR-0010（#268）、nightly APK 体积门禁（#271） |
| M8 设计系统落地 + 离线 Mermaid + 证据链加固（2026-09-13/14） | ✅ **18 张 PR #272–#291 全部 squash 合入**（见 §2.3/§2.4）：设计系统 Batch 1–4（#273/#274/#280/#282，六类裸控件清零）、离线 Mermaid（#275）、阅读密度（#281）、截图证据链（#276/#277）、测试超时（#283）、UI-1/UI-4/UI-6/UI-7 + EDITOR-1 + DATA-2（#284/#286/#287/#288/#289）、SPEC-3 + 游客配额守卫（#290/#291）。**无 open PR** |

## 2. 已完成（T1–T26 中 25 票 + 计划外交付 + 2026-09-12/13/14 修复/加固波全部合入 main）

| Ticket | Issue | 内容 | 合入 |
|---|---|---|---|
| T1 CI/CD | #2 | GitHub Actions Quality Gate + 签名 Release | ✅ |
| T2 架构守卫 | #3 | Konsist + core:testing + 截图基线 | ✅ |
| T3 导航骨架 | #4 | GitHubLinkParser + AppRoute + 底部导航 | ✅ |
| T4 认证 | #5 | PKCE + TokenStorage + 刷新降级 + 登录 UI | ✅ |
| T5 网络层 | #6 | Retrofit3/Apollo5 + 错误归一 + Room/DataStore | ✅ |
| T6 主题引擎 | #7 | 6 套主题 + 动态色 + 令牌族 + 玻璃顶底栏 | ✅ |
| T7 原生 Markdown | #8 | mikepenz 0.38.1 + KotlinTextMate + Alert + 链接 | ✅ |
| T8 WebView 兜底 | #9 | /markdown HTML + DOMPurify + markdown-it + hljs | ✅ |
| T9 README 浏览 | #10 | FeatureDetector 分流 + 相对链接 + JSON 回退 | ✅ |
| T10 首页 feed | #11 | Events API + 6 类事件过滤 + Home 分区 | ✅ |
| T11 文件树浏览 | #12 | Git Data 递归树 + Sora read-only + M3 编辑器主题 | ✅ |
| T12 仓库管理 | #13 | Star/Watch/Fork + Releases/Tags + 语言栏（PR #74） | ✅ |
| T13 Issue 列表/详情 | #14 | 时间线 + reactions + 截图基线（云端补录） | ✅ |
| T14 Issue 写 | #15 | 创建/编辑/评论/反应/关闭/任务列表同步（PR #75） | ✅ |
| T15 PR 列表详情 | #16 | 四 Tab Conversation/Commits/Checks/Files changed（PR #76） | ✅ |
| T16 Diff + 行评论 | #17 | DiffParser + PullRequestDiffView（unified/side-by-side）+ LineCommentSheet（PR #101） | ✅ |
| T17 Review/Merge | #18 | ReviewSheet + MergeBox（SplitButton）+ merge/update-branch/delete-ref（PR #102） | ✅ |
| T18 全局搜索 | #19 | 四类结果 Tab + 历史 + qualifier + 代码搜索门禁（PR #62） | ✅ |
| T19 通知 | #20 | 面板（#88）：分组折叠/滑动/筛选/全部已读/遮罩 0.5（PR #52/#98） | ✅ |
| T20 Profile | #21 | Repositories/Starred/Followers/Following 四路 Paging（PR #53） | ✅ |
| T21 Markdown 编辑器 | #22 | Sora 编辑 + 工具栏 + 编辑/预览双 Tab（PR #77） | ✅ |
| T22 文件编辑提交 | #23 | Contents API + sha 校验 + 409 三选项（PR #96） | ✅ |
| T23 分支/PR 创建 | #24 | BranchesScreen + GitRefApi + PullRequestCreateScreen（PR #105） | ✅ |
| T24 设置 | #25 | 主题/seed/圆角/动效滑杆/语言/毛玻璃总开关/PAT/配额（PR #54/#99） | ✅ |
| T26 M3 高亮 | #27 | M3TextMateTheme/GitHubTextMateTheme（PR #55） | ✅ |
| Task B 渲染切换 | #71 | WebView 主渲染（服务端 HTML + 离线 GFM 两级）PR #70/#73 合入 | ✅ |
| UI 打磨波 | #82 | Home Tab M3 化、PR 评论、Star/Watch 按钮（PR #82） | ✅ |
| ui-audit #83 毛玻璃 backdrop | #83 | 顶栏 backdrop 接线补完 + GlassRenderPolicy（PR #108） | ✅ |
| ui-audit #84 组件族/动效基建 | #84 | 共享状态组件族 + LocalMotionScale（#92 起） | ✅ |
| ui-audit #85 标签混色/语义色 | #85 | LabelChipColors + StatusChip 语义色 | ✅ |
| ui-audit #86 Paging itemKey | #86 | itemKey 迁移与模型稳定性标注 | ✅ |
| ui-audit #87 设置分组 | #87 | 设置页分组重构与个性化项接线 | ✅ |
| ui-audit #88 通知面板 | #88 | 面板完整形态（滑入+遮罩+Haze 玻璃+分组折叠+滑动操作） | ✅ |
| ui-audit #89 首页 Pager/长条按钮 | #89 | 首页分区 Pager 与 LongBarAction | ✅ |
| ui-audit #90 导航现代化 | #90 | 全局转场 + 预测返回 + @Serializable 类型安全路由 + 共享元素试点（PR #110） | ✅ |
| 全量审计 | — | `docs/agents/task-audit-2026-09-06.md`（51 项发现 → 9 张分类票） | ✅ 报告入库 |

### 2.1 2026-09-12 修复波（#229–#266，全部 squash 合入 main）

> 前半 #229–#255（26 张）与后半 #256–#266（11 张）同表。#250 是 issue 非 PR，不计入。

| PR | 范围 | 交付 |
|---|---|---|
| #229 | 文档回写 | AGENTS.md 对齐现实（状态 / 审计索引 / P0 对账 / 方法学 / `--no-daemon` 铁律） |
| #230 | 认证/数据 | PAT（fine-grained 无 GraphQL）自动降级 REST 补位端点 |
| #231 | 设计系统 | 补全 `ExtendedColors` §5.3（8 语义状态色）+ Check Run 逐状态映射 |
| #232 | 离线 GFM | 相对链接/图片重写 + emoji/脚注/锚点 |
| #233 | 导航 | Tree/Release/Search 深链落到 app 内（不再出浏览器） |
| #234 | 草稿 | 持久化扩展到文件编辑与评论/Issue/PR 表单 |
| #235 | i18n lint | 四条规则全模块启用 + 防退化 canary（修自引用恒真断言） |
| #236 | 文档 | plan.md 六处偏离回写 + 新增 ADR-0009（GraphQL 读路径现状） |
| #237 | 原生 markdown | details 重复渲染 / baseRepoUrl 透传 / coil-gif / script 正文残留 四项修复 |
| #238 | 测试 | 消除 Branches/Search/Home ViewModel 测试 `runTest` 首次初始化开销；并修 #233×#234 组合导致的 main 编译断裂 |
| #239 | 认证 | OAuth client id 三级注入点（Gradle 属性 / `local.properties` / 环境变量 → `BuildConfig`） |
| #240 | WebView 主通道 | 服务端 HTML 高亮 + 触屏复制 + 图片懒加载 + 两级缓存键（预热评估结论：不做） |
| #241 | CI 清理 | 删 `.maestro/`、截图 job 改名、修 prototype 编译 |
| #242 | CI | editor 探针改 30s 有界等待 Commit 动作 |
| #243 | 调研 | KaTeX/Mermaid 离线渲染可行性（含体积实测） |
| #244 | 文档 | 残余审计缺口清单（#229–#241 后现实核验） |
| #245 | 截图 | OLED/高对比/zh 矩阵 + RTL 方向守卫 + **修 `:feature:issue` verify 挂死根因**（`captureScreenshotDeterministic`） |
| #246 | 测试 | 相对时间夹具，扫掉 3 处日历时间炸弹 |
| #247 | 测试/CI | `WebViewDarkModePolicyTest` 补断言 + 修 `EditorView` 覆盖率排除误伤 + nightly 失败汇总补齐 |
| #248 | 深链 | `BlobRoute` 消费 `editState`，修复深链文件编辑死路 + editable 硬编码 |
| #249 | 截图 | 补 `:feature:pullrequest` 列表/详情/文件变更基线并纳入 CI verify |
| #251 | markdown | `@user` 提及渲染（离线+原生）+ 恢复 `kbd/sub/sup` 语义 |
| #252 | CI 截图探针 | 7 坏帧逐帧对账：修断言 bug、坐实 2 个真缺陷信号（`repo-star` UI-C01、`repo-actions` 探针空洞）；FAB/`checked` 语义修正 |
| #253 | 设置 | 开发者设置接入真实配额数据（GATE-2），移除「待接入」占位 |
| #254 | 清理 | 死代码/遗留文件核验后删除（`FeatureDetector` 按 ADR 保留并注释）（DEAD-1/DEAD-2） |
| #255 | 覆盖率 | 6 个无阈值模块补 LINE 棘轮（`app` / `core:common` / `core:editor` / `core:database` / `feature:search` / `feature:editor`），5 个豁免附说明 + 负向验证 |
| #256 | 截图 | 9 屏 / 20 帧基线扩展（Home/RepoDetail/FileViewer/Branches/Search/MarkdownEditor/Profile/Gists/Settings）；无限动画屏走 `captureScreenshotDeterministic` + **离线 ImageLoader** 消除头像加载非确定性；重录受 #258/#259 影响的 4 帧（RepoDetail/Settings light+dark） |
| #258 | 仓库详情 | 仓库头整块不渲染修复（`headerHeightPx` 自锁 → `Modifier.layout` 以 `Constraints.Infinity` 量自然高度）+ 回归测试 `repoDetailScreen_success_rendersRepositoryHeaderBlock`；CI `repo-star`/`repo-actions` 帧转绿（坏帧 2→0） |
| #259 | UI | 设置页返回箭头（`SettingsTopBar` + `onBackClick`）+ Issue/PR 详情底部避开 FAB（`AppDimens.fabContentClearance=96.dp` + 纯函数 padding，含反向护栏测试） |
| #260 | CI/门禁 | 去空转：覆盖率模块声明阈值却无 exec 数据时**硬失败**（旧行为 SKIPPED + BUILD SUCCESSFUL）；`konsistCheck` `isFailOnNoMatchingTests` false→true；新增 `.github/scripts/verify-screenshots.sh` 断言帧清单闭合（≥1 PNG、无 0 字节、每帧有产出或显式处置） |
| #261 | 覆盖率 | JaCoCo × Robolectric 盲区修复（`includeNoLocationClasses=true` + `includes=com/yumiru11/*`，`configureRobolectricCoverage`）；真值 `:app` 2.61%→**25.80%**、`:core:database` 5.17%→86.43%、`:feature:auth` 0%→100% |
| #262 | markdown | `#123` issue/PR 引用 + 裸 40-hex sha **双通道**链接化（离线 `renderer.js` 插件 + 原生 `MarkdownInlineSemantics`，复用 `GitHubLinkParser` 语义）；代码块/行内码/已有链接/裸 URL 不误伤；零基线漂移 |
| #263 | 覆盖率 | 22 个有阈值模块按 #261 后**真实**覆盖率重设棘轮（只升不降）+ 逐条负向验证；重写过时的「Robolectric 是工具链边界 / #181」说法（project-status + `coverageExcludes` 注释） |
| #264 | 数据 | ETag 缓存**跨进程持久化**（`RoomEtagStore` + `EtagCache` 表 + Migration 5→6，按账号 `EtagScopeProvider` 隔离，登出/切号全清，容量/TTL 上限）；新 store 实例证明 304-on-restart |
| #265 | 文档 | 后半波回写：AGENTS.md / project-status 对账到 #264（本表 #256–#264、方法学、剩余清单）+ `remaining-backlog` 剪枝（已闭环逐条勾除） |
| #266 | 截图 | 补齐剩余 6 屏 / 15 帧基线（Repos list+grid+guest、CreateRepo、ReleaseCreate、CommitDetail list+diff、CreateIssue、PullRequestCreate）；三个目标模块已在 verify 名单内，无需改 ci.yml |

### 2.2 2026-09-13 收尾波（#267–#271，全部 squash 合入 main）

| PR | 范围 | 交付 |
|---|---|---|
| #267 | 文档 | 状态对账到 #266（37 张 PR / `main@54eba10` / 114 张基线）；即 #265 之后的追齐 |
| #268 | 设计系统 | 落地计划 `docs/design-system/implementation-plan.md`（组件契约 / 批次表 / 门禁 / DoD）+ ADR-0010；实现严格排在 AGP 9 之后 —— **Batch 1–4 已随 #273/#274/#280/#282 落地（见 §2.4）** |
| #269 | markdown | **离线 KaTeX 0.18.7**（post-sanitize 渲染、仅 woff2 字体、`assets/webview/katex/` 共 556,268 B raw / ≈ +0.32 MiB；`FeatureDetector.containsMath` gate + SERVER_HTML `<math-renderer>` 检测 + JS 独立更严扫描；`trust:false` + 三上限；错误色读 `--md-sys-color-error`）。**Mermaid 随后落地（#275）** |
| #270 | 构建/lint | **AGP 9.1.1 迁移**：Gradle 8.12 → 9.3.1；AGP 8.7.3 → 9.1.1；built-in Kotlin（不再应用 `org.jetbrains.kotlin.android`）；compileSdk 36 → 37 / buildTools 36.0.0 / KSP 2.3.12 / Hilt 2.59.2；`targetSdk` 保 35 + `android.sdk.defaultTargetSdkToCompileSdkIfUnset=false`（Robolectric 约束）；**全模块 lint 恢复**：31 条 disable 删除、48 个 error 源码修复、0 disable；`com.composables` namespace 撞车根治（删空壳 base artifact，无 `uniquePackageNames` 逃生开关）；删 `AarMetadata` 禁用 hack；CI lint guard 三断言（0 跳过 registry / 0 `UnknownIssueId` / 0 disable） |
| #271 | CI | **nightly APK 体积回归门禁**：`.github/scripts/check-apk-size.sh` + `.github/apk-size-budget.properties`（baseline 7,585,167 B @ `2a44912`；budget = baseline + 256 KiB = 7,847,311 B）；超预算/缺配置/缺产物一律硬失败（#260 纪律）；红→绿实证含用 KaTeX 前预算拦住 KaTeX 后 APK |

### 2.3 2026-09-13 离线 Mermaid（PR #275，已合入）

| PR | 范围 | 交付 |
|---|---|---|
| #275 | markdown | **离线 Mermaid Tiny 11.17.2**（`assets/webview/mermaid/` 实测 2,556,235 B raw / deflate 672,490 B（2 文件：`mermaid.tiny.js` + `LICENSE`），IIFE 单文件 0 动态 import；post-sanitize 渲染，`securityLevel:'strict'` + `htmlLabels:false` + 图数上限 10；**Chromium ≥94 门禁**（class static block）Kotlin UA 判定 + JS 语法探针 + `window.mermaid` 三层兜底，不满足回退普通代码块）。同树 APK 实测 7,486,103 → 8,162,579 B（**+676,476 B ≈ +0.65 MiB**），`apk-size-budget.properties` 基线 7,585,167 → 8,162,579 / 预算 7,847,311 → 8,424,723。测试：`MermaidRenderExecutionTest`（12）+ `WebViewMermaidSupportTest`（7）+ FeatureDetector/WebViewHtmlBuilder 扩展；fixtures 34 登记双通道，未实现集合清零；Roborazzi 零漂移 |
| #269 | markdown | **离线 KaTeX 0.18.7**（`assets/webview/katex/` 实测 556,268 B raw / 23 文件，仅 woff2 字体；post-sanitize 渲染 `renderer.js:187,351`，`trust:false` + 三上限；错误色读 `--md-sys-color-error`）。同 #271 门禁记录：KaTeX 级 APK 增重 ≈ +0.32 MiB（研究 R11 与体积门禁实证） |

> **CI 证据车道（真渲染 ≠ 截图 job）**：`mermaid-render-verify.yml` 两条腿 —— API 33（WebView Chromium 101）断言 `MermaidRender: engine=supported rendered>=1`（真渲染）；API 30（Chromium 83）断言 `engine=blocked rendered=0` 且无 `supported`（`<94` 门禁真回退）。`ci.yml` 截图 job 跑 API 30，**在那是回退路径**，不能当渲染证据。`glass-verify.yml` = `Glass verification (API 31+)`。

### 2.4 2026-09-13/14 设计系统 + 加固波（#272–#291，全部 squash 合入）

| PR | 范围 | 交付 |
|---|---|---|
| #272 | 文档 | 回写 2026-09-12/13 修复波（AGP9 / KaTeX / 设计系统 / 门禁）+ 全量对账（#229–#271 段） |
| #273 | 设计系统 | **Batch 1**：`AppCard` / `AppChip` / `AppFilterChip` / `AppDialog` / `AppSegmentedButton` + debug 画廊屏 + 首批迁移（`core/designsystem/.../component/` + `.../gallery/`） |
| #274 | 设计系统 | **Batch 2**：状态视图三态组件（`AppStateViews`：Empty/Error/Loading）逃生舱 + 5 个 feature 私有实现收编 |
| #280 | 设计系统 | **Batch 3**：间距 scale `AppDimens.spacing`（`token/AppDimens.kt:39` `SpacingScale`；`contentPadding` 为 `spacing.l` 别名）+ **禁新增裸控件门禁**（`forbid-bare-controls.sh` + `bare-controls-baseline.txt`） |
| #282 | 设计系统 | **Batch 4**：`AppScaffold` / `AppBottomSheet` + **六类裸控件清零**，门禁基线棘轮到 **0**（Card/Scaffold/FilterChip/AlertDialog/ModalBottomSheet/SnackbarHost） |
| #275 | markdown | 离线 Mermaid（详见 §2.3） |
| #276 | CI 截图链 | 拼板取帧契约修复（`montage_board` 内 `base=${f%.png}`；此前每格恒为 `NOT CAPTURED` 占位）+ 坏帧水印标注真实 kind + **逐帧 critical 严重度**（`FRAME_SEVERITIES` 按帧名存，防 critical 降级成 warn）+ `lookup_mismatch` 防回归闸门（`::error::` + `exit 1`） |
| #277 | CI 截图链 | `readme-mermaid` 探针修复：**视口内短滑**（起点/终点都在 WebView 视口）+ 先取帧后断言（`settle=now`）+ 滚不到显式判坏；修此前「假帧/偶发 DUPLICATE」 |
| #281 | markdown | **阅读密度令牌单一事实来源** `MarkdownDensity`（`Literal` 默认 / `Conservative` 备选，`current = Literal`）+ `core:markdown` 基线重录 · **2026-09-14 已回滚**：产品负责人选择现状（紧凑）密度，令牌与默认值移除、基线恢复为 #281 之前的 CI 权威帧 |
| #283 | 构建/CI | 单测**任务级 15min 超时**（`build.gradle.kts:297` `test.timeout.set(Duration.ofMinutes(15))`）+ `ci.yml` verify 步骤级 **30min** 上限（超时只红一个任务/步骤，不吃满 60min Quality Gate 配额） |
| #284 | pullrequest | **UI-1**：窗口 `< 600dp` 不提供 side-by-side（强制 unified），unified 超长行整块 `horizontalScroll`（`PullRequestDiffView.kt:62-65,193-203`）；截图侧对应帧按设计 `SKIPPED`（pixel_6 = 411dp） |
| #286 | repo | **UI-6** 文件树「修改时间」列 + **UI-4** 无 README 空态收编 `AppEmptyState`（`RepoDetailScreen.kt:1759`） |
| #287 | editor | **EDITOR-1**：软换行开关（`MarkdownEditorScreen.kt:107-117`）+ 查找替换 + **显式 CRLF/编码策略**（`core/editor/.../TextFileFormat.kt`：内部归一 LF，按原 `lineEnding` + charset 回写，已存在 CRLF 不被静默转 LF） |
| #288 | data | **DATA-2**：`:core:data` 补测试基建 + 模型契约测试，**首次纳入覆盖率门禁，阈值 0.99**（实测 100%，`build.gradle.kts:139`） |
| #289 | home | **UI-7**：feed 首载骨架屏（`feature/home/.../ui/FeedSkeleton.kt`） |
| #285 | CI | 移除 `feature:profile` 的临时测试事件日志（#282 挂死诊断收尾，非功能性） |
| #290 | editor | **SPEC-3**：`@mention` 接入真实候选 —— REST collaborators 数据源 + `AppRoute.Editor(owner, repo)` + `core:editor/MentionCandidates.kt`；屏接收 `mentions: List<String>`，Hilt 在宿主层（`AppNavHost`）解析（`EditorMentionsViewModel`）。合入 2026-09-14T02:12:53Z |
| #291 | repo | 游客模式 REST 配额守卫：`AuthState.Anonymous` 短路文件树 mtime 列查询、**不做负缓存**（`RepoFilesViewModel.kt`）。合入 2026-09-14T02:23:50Z |
| #292 | docs | 波次对账：AGENTS.md / project-status / remaining-backlog 收敛到 60 PR / `main@42821ef`（docs-only，无 CI 检查） |
| #293 | markdown | **阅读密度回滚（本轮 last）**：产品负责人选定「现状（紧凑）」密度 → 移除 `MarkdownDensity` 令牌（回滚 #281 的 `Literal` 默认），`markdown-you.css` 与原生 Viewer 恢复改动前取值；`core:markdown` **37 帧基线直接取回 #281 之前的 CI 权威帧**（零本机录制）；补 `EnhancedListTest`（原生列表 marker/嵌套分支）使 diff 覆盖率 **76.9% → 100%**。合入 2026-09-14（main 头） |

> §2.1/§2.2 的 #267–#271 仍有效；本波在其后。**wave 内 PR 与 #292/#293 均已合入，无 open PR**。

## 3. 进行中：审计补全波（2026-09-06 立项，9 张分类票 #163–#171）

> 来源 `docs/agents/task-audit-2026-09-06.md`。分类票映射见审计报告 §5.1。
> 建议执行顺序见审计报告 §12：**#171 → #169/#170 → #163 → #164 → #165 → #166 → #167 → #168**。

| Issue | 标题 | 覆盖审计项 | 状态 |
|---|---|---|---|
| #171 | docs(agents): 回写真实状态 | D01 | ✅ 关闭（PR #172） |
| #169 | chore(app): 工程收尾 | L14/L15/L16/Q03/Q04 | ✅ 关闭（PR #174；签名 release 实测通过，APK 6.8MB） |
| #170 | fix(ci): 质量门禁修复 | Q01/Q02/D02/D03/D04 | ✅ 关闭（PR #181 + #188；Q01 的 Compose lint 需 AGP 9.x，见 §3.1） |
| #163 | feat(issue,pr): Issue/PR 写操作补全 | L01/L02/L03 | ✅ 关闭（PR #176） |
| #164 | feat(repo): 仓库域功能补全 | L04/L05/L06/L09 | ✅ 关闭（PR #179） |
| #165 | feat(data,home,profile): 内容与数据域补全 | L07/L08/L10–L13 | ✅ 关闭（PR #173 + #178） |
| #166 | feat(ui): UI 打磨一期·布局与页面 | UI01/02/05/11/14/19/20/21 | 🔶 关闭（PR #182/#183/#189/#191/#192/#193）；UI14 形态待确认，见 §3.1 |
| #167 | feat(ui): UI 打磨二期·动效体系 | UI03/04/06–10/15–18/22 | 🔶 关闭（PR #180/#184/#189/#190）；UI04/UI10/UI17/UI22 见 §3.1 |
| #168 | feat(ui): UI 打磨三期·图标与无障碍 | UI12/13/23–27 | ✅ 关闭（PR #185） |
| #26 | T25: 性能与发布收尾 | plan §12.5/§14.1 | 🔶 部分（PR #186 + 演练）；真机项待做，见 §3.2 |

常开不关：**#1**（Spec）、**#71**（Markdown 渲染测试面板）。

### 3.1 挂账项（票已关，但条目未闭环 —— 都是"需要人来拍板/需要真机"的）

| 项 | 归属 | 为什么没做 |
|---|---|---|
| **UI04 全局背景图** | #167 | 需先按 `FEEDBACK.md` #30 完成方案 grill（选图/不透明度/深色压暗/固定不动），不猜 |
| **UI10 README 头部收起** | #167 | 设计文档写明「用户构思中，实现时给两版效果对比」——需要你的两版对比，不在无人值守时替你拍板 |
| ~~UI22 BottomSheet 玻璃~~ | #167 | ✅ 已落地（本 worktree / `feature/t167-ui22-sheet-glass`）：跨 window 采样问题**已机械核实为不可能**（非真机才能确认），五处 `ModalBottomSheet` 改走 `GlassSheetSurface` 半透明路径 + 策略纯函数单测；结论回写 `docs/ui-design.md` §6.5 |
| **UI17 AppMotion 消费面清理** | #167 | 纯清理性重构（局部仍有硬编码时长），优先级低于功能 |
| **UI14 代码浏览悬浮搜索** | #166 | `FileViewerScreen` 顶栏已有 `startSearch()` 与跳行入口，与审计描述的「悬浮按钮」形态不同 —— 需先确认形态 |
| **PR 时间线表情回应** | #166 | Issue 侧有、PR 侧无；`ReactionChip` 目前是 feature:issue 私有，需先抽到共享模块 |
| **PR 评论 Sheet 与 Issue 统一** | #166 | 两边各有一份 Sheet 实现，宜连带上面那条一起抽共享组件，一次消除重复 |
| ~~**Q01 Compose lint 15 项检查失效**~~ | #170 | ✅ **已闭环（#270）**：AGP 9.1.1 内置 lint 32.1.1 使检测器全部恢复；31 条 disable 删除、48 个 error 在源码修复、0 disable；守卫升级为 0 跳过 registry / 0 `UnknownIssueId` / 0 disable |

### 3.2 T25 剩余（均为"本机无法验证"类；**用户已明确 defer，不阻塞其它工作**）

| 项 | 状态 |
|---|---|
| OAuth client id（真机 PKCE） | 🔶 注入点已解（#239，`app/build.gradle.kts` 三级解析 `-PoauthClientId` / `local.properties:oauthClientId` / `OAUTH_CLIENT_ID` → `BuildConfig`）；**只剩用户填一次真实值**。未配置回落 `PLACEHOLDER_CLIENT_ID`，仅真机 PKCE 失败，不影响构建/测试/截图 |
| Baseline Profile + ProfileInstaller | ✅ APK 内 `assets/dexopt/baseline.prof`(7886B) + `.profm`(1107B) 实测存在 |
| zh 文案全覆盖 | ✅ 已是硬断言测试（`I18nParityTest`），全模块通过 |
| 签名发布链路 | 🔶 用临时 keystore 在本地与 `release-dry-run.yml` 演练跑通；**生产密钥需你在 Secrets 配置** `KEYSTORE_BASE64`/`KEYSTORE_PASSWORD`/`KEY_ALIAS`/`KEY_PASSWORD` |
| WebView 离开页面 destroy | ✅ `DisposableEffect` 全链销毁 |
| 冷启动 < 1.5s 实测 | ⏳ 需真机/模拟器（本机 Linux 纯 JVM） |
| Baseline Profile macrobenchmark 采集 | ⏳ 同上；当前是人工推导的启动热路径，接入路径已写在 `baseline-prof.txt` 文件头 |
| 全屏 RTL 截图矩阵 | 🔶 方向性已验证（顶栏 RTL 基线）；全屏矩阵需模拟器截图 job |

### 3.3 修复波后仍剩余（2026-09-14 核验）

> 完整可执行清单（含 file:line 证据与规模）见 `docs/agents/remaining-backlog-2026-09-12.md`；下列为该清单的收敛摘要。
> **本轮（#272–#291）新闭环**：设计系统 SPEC-1 / SPEC-2 / UI-2（#273/#274/#280/#282，六类裸控件清零）、离线 Mermaid（#275）、UI-1（#284）、UI-4 / UI-6（#286）、UI-7（#289）、EDITOR-1（#287）、DATA-2（#288）、SPEC-3（#290）、游客 REST 配额守卫（#291）。
> **更早闭环**：#256–#264（MD-1/2/3、TEST-1、GATE-1..5、DATA-1、DEAD-1/2、UI-3/UI-5、REPO-1）；#267–#271（AGP 9、KaTeX、APK 门禁、覆盖率真值）。**下表只列真开口**。

| 项 | 状态 / 说明 |
|---|---|
| **真机项（用户已 defer）** | ⏳ 冷启动 <1.5s 实测、Baseline Profile macrobenchmark 采集、全屏 RTL 截图矩阵；本机纯 JVM 无法验（见 §3.2） |
| **生产签名密钥** | 🔶 需用户在 Secrets 配 `KEYSTORE_BASE64` / `KEYSTORE_PASSWORD` / `KEY_ALIAS` / `KEY_PASSWORD` |
| **PROTO-1** | ⏳ 原型模块归档去留（需拍板，见 backlog §6 D-6） |
| **PERF-2** | ⏳ 跨端像素 diff 管线（高成本独立票，需优先级拍板） |
| ~~AGP 9.x / Q01 / 设计系统 SPEC-1 / SPEC-2 / UI-2 / EDITOR-1 / UI-1 / UI-4 / UI-6 / UI-7 / DATA-2 / 覆盖率空白~~ | ✅ 全部闭环（AGP 9 + Q01：#270；设计系统 + UI-2：#273/#274/#280/#282；EDITOR-1：#287；UI-1：#284；UI-4/UI-6：#286；UI-7：#289；DATA-2：#288） |

> 仍需产品/设计先 grill 的决策：D-1（主题命名 vs ADR）、D-6（prototype 去留）、D-8（跨端像素 diff）——见 `remaining-backlog-2026-09-12.md` §6。**窄屏 diff（D-3，已按统一 unified + 横向滚动落地 #284）、feed 骨架（D-4，已落地 #289）、KaTeX/Mermaid（D-2，已落地 #269/#275）均已关闭。**


## 4. 遗留事项（未闭环）

| 项 | 状态 |
|---|---|
| RepoDetail 截图基线（light/dark） | ✅ 已补录；21 张陈旧基线一并重录（PR #181），并以 CI 为权威录制环境（PR #188） |
| WebView 私有图代理 + 缓存 <300ms | 待真机验证 |
| 真机走查机制（每票合并前的截图/真机验收） | 仍未流程化；截图工具调研见 `docs/research-questions.md` §7 |
| 图标候选清单（底部导航实心/空心） | ✅ 闭环（PR #185：三档图标风格 + AppIcon 统一入口 + 底栏仓库图标语义） |
| PiliPlus 卡片风格细节 | ✅ 闭环（PR #182：「仓库」大分区 + 网格/通栏切换） |
| Material Symbols 变量字体（wght=300） | ✅ 评估完成（PR #185）：暂缓引入，含再评估触发条件 |
| **模块级截图基线不在任何门禁里** | ✅ 闭环（PR #181 引入 CI 权威录制 workflow，#188 应用基线并开启 app + 6 模块 verify）。**注意：模块级基线的权威录制环境是 CI** —— 本机渲染与之不同，不要本机 record 后提交 |
| ~~Compose lint 15 项检查失效~~ | ✅ 已闭环（#270，见 §3.1） |
| diffCoverage 软门禁从未实测 | ✅ 已修两个真缺陷并转**硬门禁**（PR #181）：① 阈值口径把 80 当 8000%；② 非可执行行计入分母造成假失败 |
| Nightly 全量截图/多设备/性能基线 | ✅ 已落地（PR #181 的 nightly.yml：verify / 全模块 record+diff / release 体积 / 失败汇总） |
| 应用图标缺失 / Bundle 语言拆分 | ✅ 已闭环（PR #174：自适应+主题化单色层图标、Bundle 语言拆分） |
| **截图基线覆盖（2026-09-12/13/14 修复波）** | ✅ 大幅补齐：模块级基线纳入 CI verify（app + 13 模块，PR #188/#256）；#245 补 OLED/高对比/zh + RTL；#249 补 `:feature:pullrequest`；#256 补 9 屏/20 帧 + 离线 ImageLoader 确定性；#252 修探针；#260 加帧闭包断言；#266 补 6 屏/15 帧；#273/#274/#280/#282 补设计系统画廊屏。**当前入库 148 张 PNG（含 `prototype/readme-comparison` 4 张；非 prototype = 144）**，其中 `core:designsystem` 32 张（含 28 张 `DesignSystemGallery_*`）；TEST-2 仅余少量低 ROI 屏 |
| **RepoDetail 仓库头整块不渲染（UI-C01）** | ✅ 已修复（#258）：根因 `headerHeightPx` 自锁，改 `Modifier.layout` 量自然高度；回归测试 + `repo-star`/`repo-actions` 帧转绿（坏帧 2→0）。已知真实缺陷清单**已清空** |

## 5. 更新规则

- 每票合并 → 移入「已完成」表 + 更新里程碑
- 进行中任务开始/结束 → 更新第 3 节
- 遗留项闭环 → 从第 4 节移除
- 本文档由主代理维护（不派子代理更新）

## 6. 本波新增的工程约定（2026-09-11，来自 #163–#170 的实战教训）

这些不是流程口号，是**踩过并修掉**的问题，后续开发请直接遵守：

| 约定 | 来源 |
|---|---|
| **模块级截图基线只能由 CI 录制**（`Record screenshots (CI canonical)` workflow）。本机与 runner 的 Robolectric 渲染不是逐字节相同，本机录的基线在 CI 上 verify 会全红 | #181 实测 8 张全红 |
| ~~**被 Robolectric 沙箱加载的类不产出 JaCoCo 覆盖数据**（测试全绿但覆盖率 0）~~ **已证伪并修复（2026-09-13 #261）**：真实根因是 JaCoCo agent 默认 `inclnolocationclasses=false`，Robolectric `SandboxClassLoader` 定义的应用类无 CodeSource → 整类被静默跳过，**与「逻辑是否纯 JVM 可测」无关**。修复 = `includeNoLocationClasses=true` + `includes=com/yumiru11/*`（`build.gradle.kts` 的 `configureRobolectricCoverage`）。修复后 Robolectric 单测覆盖率真实入 exec（`:app` 2.61%→25.80%、`:core:designsystem` 71.2%→96.56%）；「希望进覆盖率门禁的逻辑必须纯 JVM 可测」的旧约束**不再成立** | #181 → #261 |
| **不要用协程 withTimeout 包真实网络调用**：runTest 用虚拟时钟，超时会被「立刻」触发，测试拿到 null | #191 |
| **MockWebServer 的顺序队列不可依赖**（并发请求会错位）；改用按路径 + page 路由的 Dispatcher | #191 |
| **辅助查询（Star 总数 / viewer 登录名）失败不得拖垮整页**：包 runCatching，取不到就少显示一项 | #191 / #193 |
| **新增 Composable 文件要落进 diff 门禁的 UI 排除名单**（按文件名后缀），否则新文件会被判「新增代码 0% 覆盖」 | #189 |
| **ModalBottomSheet 的 content 里不要放无限动画**：Robolectric 的 waitForIdle 永不收敛，verify 会挂死。**#245 起含无限动画的屏改用 `captureScreenshotDeterministic` 兜底**（`:feature:issue` 挂死已解） | #181 → #245 |

### 6.1 2026-09-12 修复波补充（截图链路可信化 + 回归守卫）

| 约定 | 来源 |
|---|---|
| **含无限动画的屏必须用 `captureScreenshotDeterministic`** —— `captureRoboImage(content)` 会 `ShadowLooper.idle()`；无限动画使主 looper 队列永不空 → verify/record 挂死（`:feature:issue` 7min+ 根因）。改用冻结时钟 + 固定 `advanceTimeBy` + 手工 draw + `Bitmap.captureRoboImage` | #245 |
| **截图基线只能由 CI canonical 录制**（`Record screenshots (CI canonical)` / `record-screenshots.yml`）；本机 `recordRoborazziDebug` 禁止（渲染非逐字节相同，本机录的 CI verify 全红）。**录制前必须先 rebase 到最新 main**（已证实：#256 rebase 至 #259 后录制，仅重录 4 帧） | #181 / #245 / #256 |
| **截图探针语义**：`TabRow` 选中态 = `selected`；M3 `SegmentedButton` = `checkable/checked`（radio 语义）；`ExtendedFAB` 文案不进 uiautomator dump，须 `try_tap_fab`（判据右下角 x≥0.7w 且 y≥0.85h，放宽会误点 diff 行）；M3 `OutlinedTextField` placeholder 不当就绪信号（#250）；探针不得静默降级为 `opt:` | #250 / #252 |
| **时间炸弹**：截图/测试夹具禁止写死绝对时间戳，一律相对时间（`isoDaysAgo`） | #246 |
| **守卫先做红证明**：恒真守卫会以「在跑但什么都没查」上线——修复波抓到 i18n lint canary 自引用、`repo-actions` 探针空洞、RTL `@Config(qualifiers)` 假测试三例 | #235 / #245 / #252 |
| **`captureRoboImage` 在普通测试模式下静默返回、不落盘**：测试绿 ≠ 拍了帧；无限动画屏必须 `captureScreenshotDeterministic`，验收看产物帧闭包断言 | #260 |
| **AGP 9 恢复全模块 lint 后禁止 re-disable**：31 条旧 disable 删除 + 48 个 error 源码修复，CI 守卫断言 0 跳过 registry / 0 `UnknownIssueId` / 0 `disable +=` | #270 |
| **APK 体积回归门禁**：release APK 超 `.github/apk-size-budget.properties` 的 `budget_bytes`（baseline + 256 KiB）即 nightly 判红；有意增重要在 PR 里同步抬预算并记录理由 | #271 |
| **设计系统迁移以像素等价为前提分批**：Batch 1 = Card/Chip 家族/Dialog/Snackbar；Batch 2 = 三态状态视图（Empty/Error/Loading）；Batch 3 = 间距 scale + 禁新增裸控件门禁；Batch 4 = Scaffold/BottomSheet + 六类裸控件清零。门禁基线 `.github/bare-controls-baseline.txt` 全 0，回流即红 | #268 / ADR-0010 / #273/#274/#280/#282 |
| **Mermaid 真渲染证据只能来自 API 33 腿**：`ci.yml` 截图 job 跑 API 30（WebView Chromium 83），Mermaid 按 `<94` 门禁回退为代码块；`mermaid-render-verify.yml` 的 API 33 腿才断言 `engine=supported rendered>0`，API 30 腿断言 `engine=blocked rendered=0`。两条都绿才是完整证据 | #275 |
| **拼板取帧必须用无扩展名帧名**：`montage_board` 内先 `base=${f%.png}`；坏帧 severity 按帧名存（`FRAME_SEVERITIES`）防 critical 被降级成 warn；帧文件存在却未被取用 = 取帧契约破坏，`::error::` + `exit 1` | #276 |
| **APK 体积预算随有意增重同行抬升**：离线 Mermaid 使 baseline `7585167` → `8162579` B、budget `7847311` → `8424723` B（+256 KiB 余量）；有意增重必须在 PR 里记录旧→新值 + 实测增量 | #271 / #275 |

---

## 工作流 grill 结论（2026-08-16）

- 已拍板：验收卡 + 分级验收、PR 测试清单、意图直读文档/ASCII 图、里程碑走查、设计闸门、基线改动需批准、全局性问题专项票
- 完整规则见 `docs/agents/workflow.md` §4；问题与用户回答原文见 `docs/workflow-grill.md`

## 测试覆盖率推进（2026-08-16 规划）

- 策略：`docs/agents/testing-strategy.md`（JaCoCo 0.8.13+ / 分层目标 / diff coverage 门禁）
- 分点清单：`docs/agents/testing-checklist.md`（A 纯逻辑 / B 数据层 / C 网络 / D 认证 / E ViewModel / F UI / G 可注入性 / H 断言质量）
- 当前：JaCoCo + `coverageVerify` 硬门禁已上线；diff coverage 仍为软门禁（D04 实测归 #170）
