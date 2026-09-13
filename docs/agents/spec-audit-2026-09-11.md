# AppDev 需求符合性审计报告（2026-09-11）

> 审计基准：`origin/main` @ `a48ede1ab7336134c47162c2690a62470a217d7e`（2026-09-11 20:55 +0800，Merge PR #199 feature/t167-readme-collapse）
> 审计方式：**逐条代码核查**（grep / read），每条结论附 `文件路径:行号` 证据；排除 `*/build/**` 产物
> 审计范围：`request.txt` 逐句 + `plan.md` §2–§14 + `docs/ui-design.md` + `docs/adr/0001–0007`
> 审计约束：只读，未运行 Gradle，未改任何生产代码（本报告为唯一新增文件）
> 三方并行：本报告 = 渲染/数据/写功能/搜索/架构主线；i18n 与 CI-CD 域、设计系统域由并行子审计提供，已标注证据来源

---

## 0. 总览

### 0.1 计数表

| 域 | ✅ 达标 | 🔶 部分 | ❌ 缺失 | 小计 |
|---|---|---|---|---|
| §1 request.txt 逐句 | 10 | 6 | 1 | 17 |
| §2 Markdown 渲染（plan §2.1–§2.14） | 5 | 8 | 2 | 15 |
| §3 数据层与认证（plan §4） | 7 | 6 | 3 | 16 |
| §4 设计系统（plan §5） | 5 | 4 | 5 | 14 |
| §5 Issue/PR 页面结构（plan §6） | 4 | 3 | 0 | 7 |
| §6 写功能（plan §7） | 5 | 5 | 1 | 11 |
| §7 代码浏览与编辑（plan §8） | 2 | 2 | 2 | 6 |
| §8 搜索（plan §9） | 3 | 0 | 0 | 3 |
| §9 架构 / i18n（plan §10–§11） | 8 | 6 | 3 | 17 |
| **合计** | **49** ✅ | **40** 🔶 | **17** ❌ | **106** |

> 另有 **1 项「规格本身自相矛盾」不计入判定**：`docs/ui-design.md:167` 与 `plan.md:447` 要求的「主题变体 Tonal Spot/Neutral/Vibrant/GitHub Classic」在代码中 0 命中，实际实现的是 `docs/adr/0004` 的六套（Light/Dark/OLED/DynamicLight/DynamicDark/HighContrast）。属 ADR 覆盖 plan 的合法演进，但 plan.md 未回写。

### 0.2 一句话结论

**骨架级能力（构建 / CI-CD / 认证 / 主题引擎 / 测试基建 / i18n 纪律）普遍达标甚至超出规格；而规格里最"重"的几件交付物——「与网页端一致的 Markdown 渲染回归集」「GraphQL 读优先」「§5.5 组件清单」「截图矩阵」——大面积只落到骨架，没有落到验收面。** 代码质量与工程纪律是本项目最强项（零硬编码文案 / 零裸动效时长 / 零 UI emoji / 291 处硬编码色仅 2 处真实泄漏 / 1786 个 JVM 单测 / 5 条 CI 流水线 / diff-coverage 硬门禁），但这些纪律覆盖的是一张**比 plan.md 小一圈的产品**。

### 0.3 最严重的 5 个缺口

1. **「与网页端一致」从未被验证过** —— `plan.md:950` 把「Markdown 渲染不一致」列为头号风险，对策是「快照回归集」，但全仓**没有任何 golden/对比回归**：`find` 仅得 `prototype/readme-comparison/` 的 4 张 PNG，且该模块的基线**不在 ci.yml / nightly.yml / record-screenshots.yml 任一 verify/record 列表**，也不在 detekt 扫描范围（`build.gradle.kts:53`）。README/Issue 渲染质量目前只靠人眼与 8 张 Markdown 截图（`core/markdown/src/test/screenshots/`）。
2. **离线 GFM 通道缺 6 项 plan §2.3 明确要求的能力**：KaTeX/数学、Mermaid 图形、脚注、emoji 短码（`:rocket:` → Unicode）、锚点跳转、内嵌 HTML 安全子集治理。且 `FeatureDetector`（唯一探测 MATH/MERMAID/HEAVY_HTML 的组件）**在生产代码里 0 引用**——探测到了也无处可去，即使调用，兜底 WebView 里也没有 KaTeX/Mermaid 可渲染。
3. **GraphQL「读优先」实际是「读点缀」**：全仓仅 **8 个 `.graphql` 文档**（`core/github-graphql/src/main/graphql/`），全部读取走 REST。plan §4.4 点名的 `Viewer`/`RepositoryOverview`/`IssueDetail(bodyHTML + timelineItems + 游标分页)` 中只有前两个存在，**`IssueDetail` 查询不存在**，官方 `pagination-support-with-jetpack-paging` 对接也未做（唯一的 `ViewerRepositoriesPagingSource` 是 GraphQL 分页，Issue 列表用 REST + RemoteMediator）。附带：`isRestOnly`（PAT 降级标记）被写入会话但**没有任何调用点用它跳过 GraphQL**，PAT 模式下 GraphQL 请求会直接失败而非降级。
4. **`plan.md §5.5` 的 47 项组件清单只落地 4 项在 `core:designsystem`**：`AppScaffold`/`AppCard`/`AppChip`/`AppDialog`/`AppSnackbar`/`AppBottomSheet`/`AppPullToRefresh`/`AppListItem`/`AppNavigationRail`/`AppAvatarRow` 全仓 0 命中（裸 `Scaffold(` 24 处、`AlertDialog(` 15 处、`Card(` 19 处、`FilterChip(` 15 处、`SnackbarHost(` 10 处）；另有 **17 份私有重复的状态视图实现**。`AppTypography` 全仓 0 命中（`AppTheme.kt:88-94` 未传 `typography =`），等宽字体无令牌、11 处硬写 `FontFamily.Monospace`。
5. **`request.txt` 的「在 linux 环境里不依赖虚拟机就能预览与测试」只达成一半**：测试面 **✅ 完全达成**（1786 个 `@Test` 全跑 unitTest/Robolectric，无 instrumentation 目录）；**预览面 ❌**——WebView 渲染的 README/Issue 正文在 Linux JVM 下**无法真实渲染**（`docs/research/screenshot-automation-alt.md:22` 明确结论），唯一预览路径是 CI 上的 KVM 模拟器（`ci.yml:289-348`），且该 job 依赖 `android-emulator-runner` + `/dev/kvm`。另：本机截图预览与仓库铁律「模块级基线只能由 CI 录制」互相拉扯（`docs/agents/project-status.md:134`）。

---

## 1. request.txt 逐句核对

| # | 用户原话（原文） | 判定 | 证据（文件:行） | 缺口 |
|---|---|---|---|---|
| R1 | 「使用 kotlin, jetpack compose」 | ✅ | `gradle/libs.versions.toml:9`（apollo）+ `buildSrc/src/main/kotlin/appdev.android.library.gradle.kts:25-27`（`compose = true`）；545 个 `.kt` 主源文件 | — |
| R2 | 「ui 全 material you 设计」 | 🔶 | 动态色 `core/designsystem/theme/ThemeColors.kt:270,287` + SDK 门 `:257`；OLED/高对比 `core/datastore/.../ThemeMode.kt:9-30`；`ExtendedColors.kt:25-64` | 无 Material Color Utilities（`SchemeTonalSpot`/`Hct`/`CorePalette` 全仓 0 命中）→ seed 色只做 `lightColorScheme(primary = seed)`（`ThemeColors.kt:450-456`），非全 tonal palette；`contrastLevel` 降级为布尔 `highContrastEnabled` |
| R3 | 「简洁流畅」 | 🔶 | 布局纪律好（`padding(left/right)` = 0，start/end = 17 处）；`AppMotion` 唯一时长入口（无裸 ms 字面量） | 性能目标（冷启动 < 1.5s / README < 800ms / 缓存命中 < 300ms，`plan.md:900-906`）**无一有实测数据**；macrobenchmark 未接入（见 §9） |
| R4 | 「github 使用 graphql+rest api」 | 🔶 | REST 17 个 Api 接口 / 90+ 端点（`core/github-rest/src/main/.../api/`）；GraphQL 8 个文档 | GraphQL 仅作补位（见 §3.4）；读路径实际全 REST |
| R5 | 「markdown 渲染需要完美融入 material you」 | ✅ | `MaterialYouFusionMapper.kt` + `WebViewHtmlBuilder.kt:115-117`（theme-vars 注入在 CSS link 之后，注释解释层叠顺序）；Kotlin 预计算混色（`MaterialYouFusionMapperTest.kt` 有测）；48 color role 映射见 `docs/research/webview-material-you-fusion.md:417` | — |
| R6 | 「能点击链接跳转对应的仓库/issue/pr/...」 | 🔶 | `GitHubLinkParser.kt:13-88` 13 种 `ParsedUrl`；`MarkdownBridge.kt:17,47`（JS bridge 走同一解析器）；原生 `MarkdownViewer.kt:169`；深链复用同一条路由（`MainActivity` intent-filter + `AppNavHost.navigateToParsedUrl:365-373`） | **4 种链接落到浏览器**：`ParsedUrl.Tree`（`AppRoute.kt:218-222` 返回 null）、`ParsedUrl.Release`（:218）、`ParsedUrl.Search`（:220）、无 context 的 `IssueRef`（:218）；`ParsedUrl.Discussion` 有路由但挂在**占位屏** `PlaceholderSearchScreen()`（`AppNavHost.kt:326-330`） |
| R7 | 「需要搜索相关信息后考虑…三方库还是 webview 还是自建方案」 | ✅ | 调研入库 `docs/research/webview-material-you-fusion.md`（306 行，含选型对比表）；`docs/research/highlight-engine-analysis.md`；ADR-0005/0007 记录两次拍板 | — |
| R8 | 「readme 必须正确渲染，能做到与网页端一致的正常渲染部分写法」 | 🔶 | README 三级降级 + 双 key 缓存实现完整（`feature/repo/.../RepoRepository.kt:192-279`：Tier1 `GET /readme` Accept html → Tier2 `POST /markdown` gfm+context → Tier3 过期缓存）；JSON 响应体误判修复（`:224`） | **无任何「与网页端一致」的回归验证**（见 §0.3 第 1 条）；离线通道缺 6 项 GFM（见 §2） |
| R9 | 「issue 和 pr 的 ui 结构基本与 github 网页端保持一致的同时适配 material you」 | ✅ | Issue：`TopAppBar`+`IssueHeader`+`CommentItem`+`EventItem`+`CrossReferenceItem`+`LinkedPrItem`+底部评论（`IssueDetailScreen.kt:178,531,799,1293,1356,1366`）；PR：四 Tab + `PrHeader` + `MergeBox`（`PullRequestDetailScreen.kt:627-673,713-790`）；`PrHeader` 含 base←head 分支信息 `:774` | — |
| R10 | 「有准确的语法高亮」 | 🔶 | 三层：原生代码块 TextMate（`TextMateCodeBlock.kt:28-38`，**7 种语言**）、文件级 Sora TextMate（`core/editor/src/main/assets/grammars/` **11 种**）、WebView highlight.js（`renderer.js:127-137`） | plan §2.12 要求的「Highlights 18 语言」与「v2 prism4j 150+ 语言」**均未落地**；Shiki 0 命中；README 代码块走服务端 HTML 时由 GitHub 渲染（`<pre>` 带 language class）但 WebView **未高亮 server HTML 路径的代码块**（`highlightCodeBlocks` 只在 `renderOfflineMarkdown()` 内调用，`renderer.js:315`） |
| R11 | 「支持 md 编辑、代码编辑、仓库管理、审查、评论等写功能」 | ✅ | md 编辑 `MarkdownComposer.kt:62`（11 按钮工具栏）；代码编辑 `FileEditScreen.kt`（Contents API + 409 三选项）；仓库管理 `RepoManagementCallbacks.kt`（Star/Watch/Fork/Release/Asset/Topics/删库/删分支）；审查 `ReviewSheet.kt` + `LineCommentSheet.kt` + GraphQL resolve/unresolve；评论 `IssueApi.kt:189,200,212` | 见 §6 的 5 项 🔶（@mention 补全未接线等） |
| R12 | 「ui 有精美且舒适且符合 material you 审美的预设主题与自定义主题」 | 🔶 | 6 套预设 `ThemeMode.kt:9-30`；自定义 seed 8 色盘 + 色板生成（`AppearanceSettingsSection.kt:86-91,558-568`）；圆角/动效滑杆、图标风格、玻璃逐项开关全接线（`AppThemeHost.kt:42-106`） | `styleVariant`（主题变体）与连续 `contrastLevel` **不存在**；`codeFont`/`codeLineNumbers` 存于 DataStore 但**零消费、零 UI 控件**（`AppearanceSettingsSection.kt:67-68` 自认「仍隐藏」）；等宽字体无令牌 |
| R13 | 「支持动态配色」 | ✅ | `ThemeColors.kt:257,270,287`（`supportsDynamicColors` + `dynamicLight/DarkColorScheme`）；API<31 回退 `:269,286`；设置开关 `AppearanceSettingsSection.kt:78-84`；**Mock 与回退分支有单测** | 无「动态色」截图基线（仅行为断言） |
| R14 | 「动效流畅」 | ✅✅ | **本项目最强项**：`AppMotion.kt:21-36` 唯一时长令牌（全主源集 `durationMillis` 无任何裸字面量）；Emphasized 曲线 `:41-47`；`AppMotion.scaledDuration:72-78` 36 处引用；**系统「减弱动画」也被尊重**（`AppMotionScale.kt:44-48` 读 `ANIMATOR_DURATION_SCALE`） | Tab 切换无 fade-through（首页用 `HorizontalPager`，PR 四 Tab 是裸 `TabRow` 硬切，`PullRequestDetailScreen.kt:683`）；shared element 仅 1 组试点 |
| R15 | 「支持 ui 自定义」 | ✅ | 圆角强度、动效强度、图标风格（Rounded/Outlined/Filled）、玻璃总开关+4 逐项、列表 stagger 开关、主题模式、动态色、OLED、高对比、seed 盘 —— 全部 DataStore → CompositionLocal → 即时重组（`AppThemeHost.kt:42-106`） | 「代码字体」「行号开关」两项无控件（同 R12） |
| R16 | 「图标使用 material you 风格图标库」 | 🔶 | `com.composables` Material Symbols 5 个变体依赖（`libs.versions.toml:78-86`）+ `core:designsystem/build.gradle.kts:30-34`；风格驱动 `LocalIconStyle`（`token/AppIcon.kt:54`）→ `AppIconSpec.vectorFor:34-42` | **覆盖率是最大缺口**：`AppIcon(` 仅 10 处 / 5 文件，而 legacy `androidx.compose.material.icons` 仍 **93 处 / 30+ 文件**；Octicons 20 枚（`AppDevOcticons.kt`）但 GitHub 专属缺 **draft / discussion / workflow** 三类 |
| R17 | 「项目有完整测试工作流和 ci/cd」 | ✅✅ | 5 个 workflow / 15 job：`ci.yml`（quality 门禁 + screenshots）、`nightly.yml`（6 job）、`record-screenshots.yml`、`release-dry-run.yml`、`release.yml`（tag `v*` → Secrets keystore → APK+AAB+mapping+草稿 Release）；`.github/dependabot.yml`；**diff-coverage 是真硬门禁**（`build.gradle.kts:345-614`） | 截图矩阵 7/10 维未覆盖（见 §9.4）；`.maestro/` 5 个 flow 是死配置 |
| R18 | 「活用 github actions 加速开发效率」 | ✅ | `gradle/actions/setup-gradle@v6` 构建+依赖缓存、`seed-plugins.sh` 插件预置、`concurrency: cancel-in-progress`、`paths-ignore: **/*.md`、PR 覆盖率 sticky 评论、nightly 失败自动开 issue、截图漂移检测 | — |
| R19 | 「项目架构清晰」 | ✅ | 24 模块 + 25 Gradle project；Konsist **6 条**架构规则实测全在跑（`ArchitectureTest.kt:39-98` + `I18nParityTest.kt:24-45`），且 `konsistCheck` 显式声明 inputs 防 UP-TO-DATE 静默跳过（`app/build.gradle.kts:250-263`） | `core:common`/`core:data` 未禁 Compose/UI（`ArchitectureTest.kt:51` 只限 github-*）；core→app 未禁 |
| R20 | 「支持 i18n」 | ✅ | 15/15 模块 `values/` + `values-zh-rCN/` 成对；625 × `stringResource` / 12 × `pluralStringResource`；22 个 `<plurals>`；相对时间本地化 `core/ui/.../time/RelativeTime.kt:66-71`；key 对齐硬断言 `I18nParityTest.kt:24-45`；`Locale.setDefault` 切换（`MainActivity.kt:120-121,495-502`） | §11.3「代码块保持 LTR / WebView 按内容 `dir`」**未实现**（`renderer.js` 与 `markdown-you.css:45-80` 均无 `direction`）；RTL 仅 1 张顶栏基线；绝对时间 `RepoDetailScreen.kt:1806` 用 ISO 固定 pattern 非 locale 化 |
| R21 | 「减少硬编码」 | ✅✅ | **用户可见硬编码文案 = 0**（42 条正则命中逐条核对全为误报：36 个 Compose 动画 `label`、6 个 `@Preview` 样例、2 个 `error()` 开发者文案）；主源集 `Color(0xFF` 291 命中中 **289 在 `core:designsystem` 色板定义**，真实泄漏仅 2 处（`MaterialYouFusionMapper.kt:20` 的 `0xFF0B0B0D` 是 `ui-design.md:218` 用户拍板值但未入令牌；`PrototypeActivity.kt:41` debug 源集）；UI emoji 作图标 **0 命中** | 间距类未令牌化：`feature/**` 裸 dp 字面量 **378**（`8.dp` 204 / `16.dp` 100 / `12.dp` 74），`AppDimens` 无 spacing scale；§11.4 四条 lint 规则（`MissingTranslation`/`HardcodedText`/`SetTextI18n`/`StringFormatInvalid`）**未显式启用**（唯一 lint 块 `app/build.gradle.kts:29-67` 只有 `abortOnError` + 15 条 Compose disable），仓库自认 `MissingTranslation` 是 warning 被放过（`I18nParityTest.kt:10-13`）——靠自研 parity 测试顶住 key 对齐，但**不覆盖 setText/format 类问题** |
| R22 | 「Linux 环境不依赖虚拟机就能预览测试」 | 🔶 | **测试 ✅ 完全达成**：1786 个 `@Test` / 188 测试文件全 unitTest 变体，`RobolectricTestRunner` + `GraphicsMode.NATIVE` + `@Config(sdk=[35])`（`core/testing/.../screenshot/ScreenshotTest.kt:36-38`）；`isIncludeAndroidResources = true` 在 15 模块逐一显式声明；**无 androidTest/instrumentation 目录** | **预览 ❌**：WebView 内容（README/Issue 正文 = 用户最关注的渲染面）在 Linux JVM 不可真实渲染；唯一路径是 CI 的 KVM 模拟器（`ci.yml:289-348`，`android-emulator-runner` api 30）。且 `plan.md:838` 承诺的 `build/outputs/roborazzi/*.png + diff 图` 在 docs 中无落地配方，CI 只在 failure 上传 roborazzi 产物（`ci.yml:155-163`），无 HTML 报告配置 |
| R23 | 「我不打算走 kotlin 多平台」 | ✅ | 0 处 KMP 配置；`settings.gradle.kts` 无 KMP 插件；`docs/adr/0007` 明确「无 KMP」 | — |
| R24 | 「详细搜索网络信息，仔细斟酌，再给出答复，选出最佳方案」 | ✅ | `docs/research/`（webview-material-you-fusion、highlight-engine-analysis、screenshot-automation-alt 等）；7 份 ADR 记录拍板过程与拒绝理由（如 ADR-0001 拒绝 Device Flow） | 部分调研结论未回写到 plan.md（theme variant / Shiki / prism4j / androidx.benchmark） |

**R 表小结**：✅10 / 🔶6 / ❌1（无完全缺失项，唯一硬冲突是 R22 的预览面）。

---

## 2. plan.md §2 Markdown 渲染体系

### 2.1 §2.3 GFM 全覆盖清单（逐项）

| GFM 特性 | 原生通道 | WebView·服务端 HTML | WebView·离线 markdown-it | 判定 | 证据 |
|---|---|---|---|---|---|
| 标题/段落/引用块 | ✅ | ✅ | ✅ | ✅ | `EnhancedMarkdownViewer.kt:73-91`（h1/h2 分隔线 + blockQuote）；`renderer.js` markdown-it 内建 |
| 加粗/斜体/删除线 | ✅ | ✅ | ✅ | ✅ | mikepenz renderer 内建；markdown-it 默认 `strikethrough: true` |
| 表格（含对齐） | ✅ | ✅ | ✅ | ✅ | `EnhancedMarkdownTable.kt`（63 行，横向滚动）+ `MarkdownTableParser.kt`；markdown-it 默认 `tables: true` |
| 有序/无序/嵌套列表 | ✅ | ✅ | ✅ | ✅ | `EnhancedList.kt:1-159` |
| 任务列表 `- [ ]`/`- [x]` | ✅ 可交互 | ✅ | ✅ | ✅ | 原生 `MarkdownCheckBox`（`EnhancedMarkdownViewer.kt:91`）+ 反向同步写回 `flipTaskListItem`（`IssueRepository.kt:468-480`，`FlipTaskListItemTest.kt` 5 用例）；离线 `taskListPlugin`（`renderer.js:257-297`） |
| 行内代码 | ✅ | ✅ | ✅ | ✅ | `EnhancedMarkdownViewer.kt:87`（`primary.copy(alpha=0.1f)`） |
| 围栏代码块 + 语言标注 | ✅ | ✅ | ✅ | ✅ | `TextMateCodeBlock.kt` |
| 代码语法高亮 | 🔶 7 语言 | ✅ GitHub 渲染 | ✅ highlight.js | 🔶 | `TextMateCodeBlock.kt:28-38` 仅 kotlin/python/go/java/json/yaml/shell；plan §2.12 要 18；**`highlightCodeBlocks` 只在离线路径调用**（`renderer.js:315`），服务端 HTML 路径的代码块未二次高亮 |
| 代码块复制按钮 | ✅ | ✅ | ✅ | 🔶 | 原生 `EnhancedCodeBlock.kt` + `CopyFeedbackState.kt`；WebView `bindCodeCopy`（`renderer.js:79-112`）——但按钮 `opacity:0` 只在 `mouseenter` 显示（`renderer.js:97-101`），**触屏无 hover ⇒ 移动端复制按钮不可见**（仍可盲点）；`btn.textContent = 'Copy'` 硬编码英文（`renderer.js:86`） |
| 图片（相对路径/GitHub 缓存域） | ✅ | ✅ | ✅ | ✅ | 服务端 HTML 相对 src → `raw.githubusercontent.com/{o}/{r}/HEAD/{p}`（`WebViewHtmlBuilder.kt:188-200`）；离线 `assets/` → appassets 域（`:153-158`，真机诊断修复记录在注释） |
| 外部链接/裸 URL 自动链接 | ✅ | ✅ | ✅ | ✅ | `renderer.js:307` `linkify: true` |
| 相对链接 `./` `../` `/owner/repo` | ✅ | ✅ | 🔶 | 🔶 | 服务端 HTML 的 `<a href>` 相对路径 → github blob 域（`WebViewHtmlBuilder.kt:203-211`）；离线 markdown 内相对链接无重写（只有图片被重写） |
| 提及 `@user` / `@org/team` | ✅ | ✅ | ❌ | 🔶 | 原生经 `GitHubLinkParser.kt:105-110`；服务端 HTML 由 GitHub 渲染；**离线 markdown-it 无 mention 插件** ⇒ `@user` 是纯文本；`@org/team` 显式返回 External（`:108`，注释「无对应路由」） |
| 引用 `#123` / `owner/repo#123` / `gh-123` | 🔶 | ✅ | ❌ | 🔶 | `GitHubLinkParser.kt:162-179` 支持前两种；**`gh-123` 形态 0 命中**；离线通道无 issue 引用识别 |
| 提交引用（裸 sha） | ✅ | ✅ | ❌ | 🔶 | `GitHubLinkParser.kt:113,293-296`（40 位 hex 独立 token）；离线无 |
| Emoji 短码 `:rocket:` | ❌ | ✅ | ❌ | 🔶 | **无 `GhMarkdownProcessor`、无 gemoji 资源、无 emoji 插件**（`grep -rn "Gemojis\|emojiMap\|shortcode"` 在 `core/markdown` 仅命中编辑器补全表）⇒ **离线/原生通道 `:rocket:` 显示为字面量**；plan §2.2 明确要求「emoji 短码 → Unicode（gemoji 子集映射表，数据放 assets 不硬编码）」 |
| GitHub Alerts `[!NOTE]`×5 | ✅ | ✅ | ✅ | ✅ | 原生 `GitHubAlertParser.kt:4-34` + `GitHubAlertCard.kt:52-111`（5 类 + 矢量 Octicon）；离线 `githubAlertPlugin`（`renderer.js:149-255`，含嵌套 blockquote 深度匹配） |
| 锚点跳转 `#section` | ❌ | 🔶 | ❌ | ❌ | `renderer.js:179` 的 `resolve()` 把 `#` 开头原样保留，但**无 `scrollToAnchor`**（plan §2.9 Kotlin→JS API 要求）、无 anchor 插件生成 heading id ⇒ **目录/锚点链接点击无效** |
| 图片懒加载/点击放大/GIF | 🔶 | 🔶 | 🔶 | 🔶 | 点击放大：`bindImages` → `onImageClick`（`renderer.js:51-64`）；**无 `loading="lazy"`**（grep 0 命中）；GIF 走 Coil（原生）/WebView 默认 |
| 内嵌 HTML（安全子集） | 🔶 | ✅ | 🔶 | 🔶 | 原生 `EnhancedHtmlBlock.kt`（170 行）+ `HtmlBadgeParser`/`HtmlDetailsParser`；安全治理见 §2.14 表；DOMPurify `FORBID_TAGS` 含 `style`/`form`（`renderer.js:24`）——GitHub 实际允许的 `<kbd>`/`<sub>` 等保留，但 `<details>` 的 `open` 属性、部分 `<span class>` 白名单未验证 |
| Math / KaTeX | ❌ | ~~✅ GitHub 渲染~~ **（2026-09-13 更正）GitHub 只出 `<math-renderer>` 占位，App 用离线 KaTeX 水合 ⇒ ✅** | 🔶→**✅（2026-09-13 落地离线 KaTeX 0.18.7）** | ✅ | 2026-09-13 起 WebView 打包离线 KaTeX（post-sanitize 渲染，两条通道都覆盖）。⚠️ 原判「GitHub 服务端渲染 ✅」经 `POST /markdown` 实测**不成立**：GitHub 只返回 `<math-renderer>` 占位 + 原始 `$…$` 文本（水合靠 github.com 前端脚本），见 `docs/research/katex-mermaid-offline-feasibility.md` §3.5 |
| Mermaid | ❌ | ~~✅ GitHub 渲染~~ **（2026-09-13 更正，实测）GitHub 只出 `highlight-source-mermaid` 高亮代码块，App 用离线 Mermaid Tiny 水合 ⇒ ✅** | ❌→**✅（2026-09-13 落地离线 Mermaid Tiny 11.17.2）** | ✅ | 2026-09-13 起 WebView 打包离线 Mermaid（`assets/webview/mermaid/mermaid.tiny.js`，IIFE 单文件，post-sanitize 渲染；`securityLevel:'strict'` + `htmlLabels:false`；Chromium ≥94 门禁（class static block 是解析期语法级失败）不满足时回退普通代码块）。⚠️ 原判「README 走服务端 HTML 时由 GitHub 出图 ✅」同样被实测推翻：GitHub 返回的是语法高亮代码块，图由 github.com 前端脚本渲染 |
| 脚注 | ❌ | ✅ | ❌ | 🔶 | markdown-it `footnote` 插件未装（`renderer.js:308-309` 只 `use` 了 alert + taskList 两个自研插件）；原生无 |

**统计：§2.3 共 22 项 → ✅9 / 🔶11 / ❌2（锚点、Math 渲染器缺失；Mermaid/脚注/emoji 短码归 🔶 因服务端 HTML 路径可覆盖）。**

> **2026-09-13 更正**：本表是 2026-09-11 快照。其中 Math/KaTeX 已于 2026-09-13 落地离线渲染
> （两条 WebView 通道；见 `docs/agents/markdown-consistency-2026-09-11.md` 的 2026-09-13 更新）、
> **Mermaid 也于同日由离线 Mermaid Tiny 11.17.2 落地**（Phase 2；Chromium ≥94 门禁，
> 不满足时回退代码块），部分 🔶 项的现状也已有变化——**以 catalog + 一致性报告为准**，本表不再逐行维护。

### 2.2 §2.4 渲染器抽象接口

| 条款 | 判定 | 证据 / 缺口 |
|---|---|---|
| `interface MarkdownRenderer { fun render(content, context): Flow<RenderResult> }` | ❌ | **全仓无此接口、无 `MarkdownContent`、无 `RenderContext`、无 `SourceType`、无 `RenderResult`**（grep 0 命中）。实际是两组具体组件：原生 `MarkdownViewer`/`EnhancedMarkdownViewer` + WebView `WebViewMarkdownRenderer`，通道选择由**宿主 feature 硬编码**而非统一抽象 |
| 「两套实现由 `MarkdownFeatureDetector` 判定路由」 | ❌ | `FeatureDetector.shouldFallback`（`FeatureDetector.kt:76-118`）存在且有 15 个测试用例，但**生产代码 0 引用**（grep 仅命中自身 + 测试 + 2 处 KDoc 提及）。ADR-0007 让它「保留但不用于 README 分流」，实际是**完全死代码** |

### 2.3 §2.5 分内容类型渲染决策表

| 内容类型 | plan 要求 | 实际 | 判定 |
|---|---|---|---|
| Issue/PR 评论、短正文 | Compose 原生 | ✅ 原生（`IssueDetailScreen.kt:443` `CommentItem` → `MarkdownViewer`）；铁律「评论列表绝不用 WebView」**未被违反** | ✅ |
| 常规 Issue/PR 正文 | 原生优先，探测复杂再切兜底 | **反转为 WebView 优先**（`IssueDetailScreen.kt:485` 注释「Issue 无服务端 HTML API → 离线 GFM + 融合样式」→ `RenderMode.OFFLINE_MARKDOWN_IT`） | 🔶 偏离 plan，但符合 ADR-0007 的架构演进；代价是 Issue 正文也吃 WebView 冷启动成本 |
| README | 原生为主，复杂 → WebView 服务端 HTML | **恒 WebView**（`RepoRepository.kt:147-152` 日志注释「Task B 后 README 恒走 WebView，renderMode 恒 WEBVIEW」） | 🔶 同上，ADR-0007 拍板 |
| Markdown 编辑预览 | 「服务端渲染时走 POST /markdown」 | 走**离线** markdown-it（`MarkdownEditorScreen.kt:139` `RenderMode.OFFLINE_MARKDOWN_IT`） | 🔶 偏离。影响：编辑预览能看到 GitHub 服务端会渲染出而离线渲染器渲染不出的差异（mermaid/katex/emoji 短码/锚点），**WYSIWYG 一致性目标未达成** |
| 代码文件浏览/编辑 | Sora Editor（TextMate） | ✅ `core/editor/CodeEditorView.kt:38`（editable 开关）+ 11 种 grammar | ✅ |
| PR Diff | 自建 Compose unified + 行评论 | ✅ `PullRequestDiffView.kt`（503 行，统一/分屏切换）+ `LineCommentSheet.kt`(186) | ✅ |
| 代码块语法高亮 | Highlights 18 → v2 prism4j 150+ | 7 种 TextMate（原生）/ highlight.js（Web） | 🔶 |

### 2.4 §2.6 README 策略（第一优先级）

| 层级 | plan | 实际 | 判定 |
|---|---|---|---|
| Tier1 服务端 HTML | `GET /repos/{o}/{r}/readme` + `Accept: application/vnd.github.html+json` | ✅ `ReadmeApi.kt:33-45`；处理链 = DOMPurify 清洗 → 移除 script → 注入 MY CSS 变量 → WebView → 拦截链接，全部落地 | ✅ |
| Tier2 REST Markdown API | `POST /markdown {text, mode:gfm, context}` | ✅ `ReadmeApi.kt:61` + `MarkdownRenderRequest.kt:9`（`MODE_GFM` 常量）；Tier1 收到 JSON 响应体时也回退到此（`RepoRepository.kt:224-240`） | ✅ |
| Tier3 本地兜底 | markdown-it + task-lists/tables/anchor/emoji/footnote + Shiki 或 hljs + KaTeX/Mermaid 按需 + 全 assets 打包 + WebViewAssetLoader | 🔶 资产齐备（`core/markdown/src/main/assets/webview/`：`markdown-it.min.js`、`highlight.min.js`、`purify.min.js`、`github-markdown.css`、`markdown-you.css`、`highlight-theme.css`、`renderer.js`）+ `WebViewAssetLoader` 域 `appassets.androidplatform.net`（`WebViewMarkdownRenderer.kt:97-100`）；**但插件只装了 2 个自研的**（alerts + taskList），anchor/emoji/footnote/KaTeX/Mermaid **全缺**；Shiki **0 命中**（用的是 highlight.js，plan §2.12 有「或 highlight.js」的余地，但 §2.6 写的是 Shiki） | 🔶 |

### 2.5 §2.7 Issue/PR 正文策略

| 条款 | 判定 | 证据 / 缺口 |
|---|---|---|
| GraphQL 取 `body` 与 `bodyHTML`，有 bodyHTML 优先原生 span 渲染 | ❌ | **`bodyHTML` 全仓 0 命中**（grep `--include=*.kt --include=*.graphql` 无结果）；无 `IssueDetail.graphql`。实际 Issue 正文走 REST `IssueApi.kt:69` 取 `body`，再离线 markdown-it 渲染 |
| 无 bodyHTML 则 `body` + `POST /markdown` | ❌ | Issue 正文**不调用** `POST /markdown`（唯一调用点在 README 路径 `RepoRepository.kt:228,246`）；走离线通道 |
| 编辑时用原始 `body` | ✅ | `IssueApi.kt:110` PATCH + `EditIssueDialog`（`IssueDetailScreen.kt:268`） |
| 详情页头部元素（标题/状态/标签/作者/Assignees/Milestone/Reactions）Compose 原生 | ✅ | `IssueDetailScreen.kt:531-780`（`IssueHeader` + `LabelChips` + `AssigneeRow:741` + `MilestoneCard:1085` + `ReactionBar:771`） |
| Timeline 评论一律原生 | ✅ | `IssueDetailScreen.kt:443`（`CommentItem`，native `MarkdownViewer`） |

### 2.6 §2.8 评论原生渲染铁律

| 条款 | 判定 | 证据 |
|---|---|---|
| 全量 Compose 原生渲染 | ✅ | `IssueDetailScreen.kt:443`、`PullRequestTimelineItems.kt:44`（`CommentCard`，PR 侧）；grep 确认评论渲染路径**无 WebView 组件** |
| 正文 `onSurface` | ✅ | `MarkdownViewer.kt` 用 `markdownColor(text = scheme.onSurface)` |
| 链接 `primary` + 下划线 | ✅ | `EnhancedMarkdownViewer.kt:93-96`（`TextLinkStyles(SpanStyle(color = primary, textDecoration = Underline))`） |
| 引用块 `surfaceContainerHigh` + 3dp primary 左边框 + 圆角 | 🔶 | 走 `GitHubAlertOrQuote`（`EnhancedMarkdownViewer.kt:73`）→ `GitHubAlertCard.kt`；具体容器色/边框宽需逐值核对，未达 plan 字面（未在本次逐像素核验） |
| 行内代码 `surfaceContainerLow` + 细圆角 | 🔶 | 实际 `scheme.primary.copy(alpha = 0.1f)`（`EnhancedMarkdownViewer.kt:87`）——**偏离** plan 的 `surfaceContainerLow` 背景 |
| 代码块：圆角 surface + 等宽 + 横向滚动 + 语言标签 + 复制按钮 | ✅ | `EnhancedCodeBlock.kt`(92) + `TextMateCodeBlock.kt`(141) + `CopyFeedbackState.kt`(40) |
| 表格 `outlineVariant` 细边框 + 表头 `surfaceContainerHigh` | ✅ | `EnhancedMarkdownTable.kt`(63) + `MarkdownTableParser.kt` |
| 任务列表 M3 Checkbox（只读或可点击反向状态） | ✅ | `MarkdownCheckBox` + `flipTaskListItem` 反向同步（`IssueRepository.kt:468-480`）+ `FlipTaskListItemTest.kt` 5 用例 |

**§2.8 判定：铁律达标 ✅（无违规），细节色值 2 处 🔶。**

### 2.7 §2.9 WebView 高保真设计

| 条款 | 判定 | 证据 / 缺口 |
|---|---|---|
| WebView 只作内容容器，不做应用导航 | ✅ | `WebViewSecurity.kt` + `renderer.js:40-47`（所有 `a[href]` click `preventDefault` → bridge） |
| 模板结构（`theme-vars` / `markdown-you.css` / `highlight-theme.css` / `.markdown-body`） | ✅ | `WebViewHtmlBuilder.kt:95-128`，且 `theme-vars` 刻意置于 CSS `<link>` **之后**并注释说明层叠原因（`:113-114`） |
| Kotlin→JS：`setContent(html, options)` | ✅ | `WebViewMarkdownRenderer.kt`（`loadDataWithBaseURL` + `RenderMode`） |
| Kotlin→JS：`updateTheme(tokens)` | ✅ | 主题变量在 `build()` 时注入；切主题重建 HTML（缓存按 `contentHash + themeVersion` 双 key 失效，`RepoRepository.kt:200,209`） |
| Kotlin→JS：`scrollToAnchor(id)` | ❌ | **0 命中**。README 头部收起（#167 UI10）是读 WebView `onHeightChanged`/滚动回调实现（`RepoDetailScreen.kt:584-591`），与锚点跳转无关 |
| JS→Kotlin：`onLinkClick` | ✅ | `renderer.js:44` → `MarkdownBridge.kt:17` → `GitHubLinkParser` |
| JS→Kotlin：`onCodeCopy` | ✅ | `renderer.js:107` |
| JS→Kotlin：`onImageClick` | ✅ | `renderer.js:58` |
| JS→Kotlin：`onCheckboxClick(index, checked)` | ✅ | `renderer.js:72` → `flipTaskListItem` 反向同步 |
| JS→Kotlin：`onHeightChanged(h)` | ✅ | `renderer.js:114-125`（`ResizeObserver`） |

### 2.8 §2.10 Material You CSS 令牌注入

| 条款 | 判定 | 证据 / 缺口 |
|---|---|---|
| Compose 侧生成令牌注入 `:root` | ✅ | `MarkdownThemeTokens.kt` + `MaterialYouFusionMapper.kt`（48 role 映射）；写进 `<style id="theme-vars">`（`WebViewHtmlBuilder.kt:115-117`） |
| `--md-sys-color-*` / `--md-sys-shape-corner-medium` / `--md-sys-font-sans` / `--md-sys-font-mono` | ✅ | `MarkdownThemeTokensTest.kt` 断言变量集 |
| `.markdown-body` / `a` / `pre` / `blockquote` / table 规则 | ✅ | `markdown-you.css`（自维护，非照搬 GitHub 蓝灰；`github-markdown.css` 同目录保留为基础层） |
| 深/浅/OLED/动态色切换时重新注入（缓存按 token 版本双 key） | ✅ | `themeVersion` 参与缓存 key（`RepoRepository.kt:200,210`）；`WebViewDarkModePolicy.kt` + `WebViewDarkModePolicyTest.kt` |
| **真机 WebView 不支持 CSS `color-mix`，混色必须 Kotlin 预计算** | ✅ | `MaterialYouFusionMapper.kt` 预计算 + `MaterialYouFusionMapperTest.kt`；`WebViewHtmlBuilderFusionTest.kt` 校验注入产物 |
| 不直接照搬 GitHub CSS，自维护 `markdown-you.css` | ✅ | `core/markdown/src/main/assets/webview/markdown-you.css` + `github-markdown-css.LICENSE`（许可合规） |

### 2.9 §2.11 GitHubLinkParser 链接跳转

| 条款 | 判定 | 证据 / 缺口 |
|---|---|---|
| `sealed interface GitHubLink` 11 种类型 | 🔶 | 实现为 `sealed interface ParsedUrl`（`GitHubLinkParser.kt:13-88`），**13 种**（多 `IssueList`，少 `Search` 只有无 owner 形态）。命名与 plan 不同，能力**超出**（多了 IssueList 修自循环选择器的真实 bug，注释 `:223-225`） |
| 绝对链接（repo/issues/pull/blob/commit/releases） | ✅ | `parseAbsolute:142-156` + `parsePath:197-285` |
| 相对链接 `/owner/repo/issues/123`、`issues/123`、`../blob/main/file` | ✅ | `parseRelative:158-191`（含 `../` 归一化 `:182-185`） |
| Markdown 内引用 `#123` / `owner/repo#123` / `@user` / 裸 sha | ✅ | `:105-110`（@）、`:113`（sha）、`:163-179`（#123 / owner/repo#123） |
| 路由行为：Repo→仓库页 / Issue·PR→详情 / Blob·Tree→文件浏览 / Commit→提交页 / Release·User·Org→对应页 / 外部→Custom Tabs / 未知→浏览器 | 🔶 | `AppRoute.fromParsedUrl:172-228` 映射 8 类；**`Tree`→null（:218）、`Release`→null（:218）、`Search`→null（:220）、无 context 的 `IssueRef`→null（:218）** ⇒ 这 4 类落到浏览器而非应用内；`Discussion` 映射到 `Discussion` route 但该 route 挂 `PlaceholderSearchScreen()`（`AppNavHost.kt:326-330`）；`User` ✅ 映射（`:210`），**`Org` 语义 0 命中**（无独立 org 页） |
| 同一解析器复用为 Android 深链接处理 | ✅ | `GitHubLinkParserTest.kt` 覆盖矩阵 + `MainActivity` VIEW intent-filter 共用 `parseUrl` |
| 单测矩阵（plan §12.2 要求「绝对/相对/引用组合矩阵」） | ✅ | `core/navigation/src/test/.../GitHubLinkParserTest.kt` + `MarkdownLinkDispatchTest.kt` + `MarkdownBridgeTest.kt`（13 用例） |

### 2.10 §2.12 语法高亮方案

| 通道 | plan | 实际 | 判定 |
|---|---|---|---|
| 原生代码块 | Highlights 18 语言（6 组暗/亮主题随 App 色随主题）→ v2 prism4j 150+ | **KotlinTextMate**（`dev.textmate`）**7 语言** + M3 派生主题（`M3TextMateTheme.kt:1-149`、`GitHubTextMateTheme.kt`） | 🔶 换了实现（TextMate 比 Highlights 更准，方向合理），但**语言覆盖 7 < 18**，prism4j 0 命中 |
| 兜底 WebView | Shiki（TextMate 语法、按语言懒加载、Web Worker） | **highlight.js**（`highlight.min.js`，`renderer.js:127-137`） | 🔶 偏离；且**无按语言懒加载**（整包加载），服务端 HTML 路径甚至未高亮 |
| 代码文件浏览 | Sora Editor TextMate（VS Code 同款），行号/搜索/跳转行/wrap | ✅ `core/editor/CodeEditorView.kt:38`（`setEditable`、`setTabWidth(4)`）；`CodeEditorController.startSearch()`；`FileViewerScreen.kt:113` 顶栏搜索入口；11 种 grammar | 🔶 `isWordwrap = false` **硬编码**（`CodeEditorView.kt:76`）⇒ plan §8.1/§8.2 的「wrap 切换」未实现 |
| PR Diff | 自研轻量 unified；复杂可 WebView | ✅ `feature/pullrequest/data/DiffParser.kt` + `PullRequestDiffView.kt`（统一/分屏切换） | ✅ |

### 2.11 §2.13 性能优化

| 条款 | 判定 | 证据 / 缺口 |
|---|---|---|
| 本地资源加载：JS/CSS 全 assets + WebViewAssetLoader | ✅ | `core/markdown/src/main/assets/webview/`（344K）+ `WebViewMarkdownRenderer.kt:97-100` |
| WebView 预热（空闲时预热空模板 + 预注册 bridge） | ❌ | **0 命中**（grep `warm|preload|预热` 无生产实现）。每个渲染点 `WebView(ctx)` 新建（`WebViewMarkdownRenderer.kt:157`） |
| 缓存渲染结果（content hash + theme version 双 key） | ✅ | `CachedReadmeEntity(contentHash, themeVersion, html)` + `RepoRepository.kt:200,209-213`；`CachedReadmeDao` |
| README 用 ETag | ✅ | `EtagCacheInterceptor.kt` + `EtagStore.kt`（**进程内实现** `InMemoryEtagStore:29`，注释自认「后续可换 Room 持久化」⇒ 跨会话不生效） |
| 按需启用 JS 功能（普通 MD 只装 DOMPurify+主题；有代码块才加高亮；有 mermaid 才加载） | 🔶 | 离线模式**固定加载** `markdown-it.min.js` + `highlight.min.js`（`WebViewHtmlBuilder.kt:87-93`），无按需；服务端模式只装 `purify.min.js`（符合「普通 MD 只装 DOMPurify」） |
| 懒加载图片 `loading="lazy"` | ❌ | `loading="lazy"` **0 命中** |
| 私有仓库图片走拦截器代理加 Authorization | ✅ | `PrivateImageInterceptor.kt` + `WebViewMarkdownRenderer.kt:208`（`shouldInterceptRequest` 白名单 host）；`PrivateImageInterceptorTest.kt` |
| 嵌套滚动：README 单独页自身滚动；嵌在长列表中的正文预身高控制 | ✅ | `fillAvailableHeight` 参数；README 头部收起用滚动回调（`RepoDetailScreen.kt:584-591`） |
| 复用与销毁：README 单实例 WebView，非必要 destroy，LeakCanary 监控 | 🔶 | destroy ✅（`DisposableEffect` → `webView.destroy()`，`WebViewMarkdownRenderer.kt:134-142`）；**单实例复用 ❌**（每处新建）；LeakCanary 依赖见 `libs.versions.toml`（debug 包） |

### 2.12 §2.14 WebView 安全策略

| 条款 | 判定 | 证据 |
|---|---|---|
| `javaScriptEnabled = true` | ✅ | `WebViewSecurity.kt:35`（带 `@SuppressLint` + 理由） |
| `allowFileAccess / allowContentAccess = false` | ✅ | `:37-38` |
| `allowFileAccessFromFileURLs / allowUniversalAccessFromFileURLs = false` | ✅ | `:39-40` |
| `domStorageEnabled / databaseEnabled = false` | ✅ | `:42-43` |
| `geolocationEnabled = false` | ✅ | `:44` |
| 额外：`removeJavascriptInterface("searchBoxJavaBridge_"/"accessibility"/"accessibilityTraversal")` | ✅ 超规格 | `:53-55` |
| **不注入 token、不拼 token 进 HTML** | ✅✅ | `WebViewHtmlBuilder.build()` **无 token 入参**（`WebViewHtmlBuilder.kt:61-84` 签名）；`addJavascriptInterface` 全仓仅 1 处（`WebViewMarkdownRenderer.kt:197`，注册的是 `AndroidBridge` 回调，无 token 字段）；grep `token` 在 `core/markdown/.../webview/*.kt` 主源**仅出现在注释**（`WebViewHtmlBuilder.kt:46`、`WebViewMarkdownRenderer.kt:45`、`renderer.js:15`） |
| 私有图片只在 `shouldInterceptRequest` 对白名单 host 加 Authorization | ✅ | `PrivateImageInterceptor.kt`（host 白名单 + `Authorization`）；`PrivateImageInterceptorTest.kt` |
| 所有 HTML 一律 sanitize（DOMPurify 白名单） | ✅✅ | **三层防御**：`HtmlSanitizer.kt`（Kotlin 预清洗，剥离 script/iframe/object/embed/form/style/base/meta/link + SMIL 动画标签 `:34`，注释解释 mXSS 向量）→ `WebViewHtmlBuilder.kt:146` 强制调用（「组件内建责任，不依赖调用方」）→ `renderer.js:335` DOMPurify 权威清洗。含实体编码绕过兜底（`HtmlSanitizer.kt:68-70,116` 注释） |
| 禁任意 `file://` | ✅ | `DANGEROUS_SCHEMES = {javascript, data, blob, file, vbscript}`（`HtmlSanitizer.kt:63`）+ `WebViewSecurity` file access 全关 |
| 危险 scheme 仅允许 http/https/mailto | ✅ | `HtmlSanitizer.kt:38-50` 三形态（双引号/单引号/无引号）正则 + 解码后兜底判定 |
| 外链全部拦截交给路由 | ✅ | `renderer.js:36-49`（`preventDefault` + bridge） |

**§2 域判定汇总：15 条 → ✅5 / 🔶8 / ❌2（§2.4 抽象接口、§2.13 中的 WebView 预热与图片懒加载）。**

---

## 3. plan.md §4 数据层与认证

### 3.1 §4.1 认证方案（PKCE）

| 条款 | 判定 | 证据 |
|---|---|---|
| OAuth 授权码 + PKCE（AppAuth-Android） | ✅ | `OAuthSessionManager.kt:12-13,49`（`AuthorizationService` + `AuthorizationRequest.Builder`，注释「PKCE 由 Builder 自动开启 code_verifier/challenge」）；`net.openid.appauth` 依赖 |
| Custom Tabs 完成授权 | ✅ | `OAuthSessionManager.kt:81`（`AuthorizationService(context)` 拉起）；ADR-0001 自定义 scheme 回调 |
| 无 client secret | ✅ | 无 secret 常量；`OAuthConfig.kt` + `OAuthConfigTest.kt` |
| PAT 仅开发者模式 | ✅ | `feature/settings/DeveloperSettingsSection.kt:57,150`；`AuthViewModel.kt:69` / `SettingsViewModel.kt:175`（`SessionData(pat=..., isRestOnly=true)`） |
| **fine-grained PAT 不支持 GraphQL → 该模式自动降级为 REST-only 路由** | ❌ | `isRestOnly` 被**写入**（`Downgrade.kt:22`）并**被读取**用于推导 `AuthState.PAT`（`OAuthSessionManager.kt:133`），但**没有任何调用点用它跳过 GraphQL**：`apolloClient` 消费点（`IssueRepository.kt:285,324`、`PullRequestRepository.kt:152,418,461,463`、`ViewerRepositoriesPagingSource.kt:30`、`DefaultRepositoryRepository.kt:32`、`DefaultUserRepository.kt:29`）**全部无条件调用**。PAT 模式下这些请求会拿到 403 并归一化为 `GitHubError.Forbidden`，而不是自动落到 REST |

### 3.2 §4.2 Token 安全

| 条款 | 判定 | 证据 |
|---|---|---|
| EncryptedSharedPreferences / Android Keystore | ✅ | `EncryptedTokenStorage.kt:14-15,30`（`MasterKey` `AES256_GCM` + AndroidKeyStore 硬件背书；PrefKey `AES256_SIV` / PrefValue `AES256_GCM`）；ADR-0002 抽象接口 + `InMemoryTokenStorage` 测试实现 |
| 明文不入 DataStore | ✅ | Token 只在 `EncryptedTokenStorage`；DataStore 只存偏好（`core/datastore/preferences/`） |
| 不写日志 | ✅ | `core/common/logging/LogRedaction.kt:34`（正则覆盖 `access_token|refresh_token|code_verifier|token|password|authorization`）+ `LogRedactionTest.kt`；`app/.../logging/RedactingDebugTree.kt` |
| 不注入 WebView | ✅ | 见 §2.14 表（build 签名无 token 入参） |
| 不进崩溃上报 | 🔶 | `RedactingDebugTree` 只覆盖 Timber debug 树；**无 Crashlytics/Sentry 之类的上报 SDK**（grep 0 命中）⇒ 该条实际未被触发，但也没有「显式过滤」代码 |
| 401 统一拦截 → 静默刷新 → 失败重新登录 | ✅ | `AuthSessionInterceptor.kt:55-73`（401 → `TokenRefresher.refreshIfNeeded()` → 重放，带 `RetryMarker` 防死循环）；`TokenRefresherTest.kt` |
| 游客模式：未登录支持只读公共内容（无 token 请求） | ✅ | `AuthSessionInterceptor.kt:46-52`（token == null 不注入头）；`GuestTokenProvider` 历史问题已修（`RestNetworkModule.kt:33-35` 注释记录 P0-7 真机走查修复） |

### 3.3 §4.3 请求规范

| 条款 | 判定 | 证据 |
|---|---|---|
| `Accept: application/vnd.github+json` | ✅ | `GitHubHeaders.kt` + `GitHubHeaderInterceptor.kt` |
| `X-GitHub-Api-Version: 2022-11-28` | ✅ | 同上 |
| `Authorization: Bearer {token}` | ✅ | `AuthTokenInterceptor.kt:16-21` + `AuthSessionInterceptor.kt:51` |
| 共享 OkHttp（Auth 拦截器 / debug 日志 / ETag 缓存） | ✅ | `RestNetworkModule.kt:50-59`（单例 `@GitHubHttpClient`）+ `EtagCacheInterceptor.kt` |
| 错误归一化 401/403/404/409/422/429 | ✅ | `core/github-data/.../error/GitHubError.kt:14-104`（`Unauthorized/Forbidden/NotFound/Conflict/Validation/RateLimit/Network/Unknown` + `Throwable.asGitHubError()`）；`GitHubErrorMappingTest.kt` |
| **User-Agent（GitHub 强制要求，缺失即 403）** | ✅ | `GitHubHeaders.kt:16` |
| 限流策略：REST 5000/hr、GraphQL 点数——开发模式展示剩余点数提醒 | ✅ | `RateLimitStore.kt` + `RateLimitModule.kt`；`feature/search/SearchRateLimitSection.kt` + `SearchRateLimitWarning.kt`；开发者设置显示 |

### 3.4 §4.4 GraphQL 设计（最大偏离区）

| 条款 | 判定 | 证据 / 缺口 |
|---|---|---|
| Apollo Kotlin 5 codegen（response-based） | ✅ | `libs.versions.toml:9`（apollo 5.0.1）+ `:196` plugin；`core/github-graphql/src/main/graphql/schema.graphqls` |
| Fragment 复用 | ❌ | 8 个 `.graphql` 文件中**无 `fragment` 声明**（grep 0 命中） |
| Normalized Cache（memory → SQLite 链） | ✅ | `GitHubApolloClientFactory.kt:30`（`persistentCacheFactory?.let(memoryCache::chain) ?: memoryCache`）；`apollo-normalized-cache` 1.0.6 + `-sqlite`（`libs.versions.toml:124-125`） |
| 自定义 scalar 映射（DateTime、URI） | 🔶 | `scalar/InstantAdapter.kt`（DateTime ✅）+ `InstantAdapterTest.kt`；**URI 映射 0 命中** |
| 官方玩法：`pagination-support-with-jetpack-paging` 对接 Paging 3 游标 | 🔶 | `core/github-data/.../paging/ViewerRepositoriesPagingSource.kt`（GraphQL 分页 ✅，Profile 页用）；但 Issue 列表用 **REST + `IssueRemoteMediator`**（`feature/issue/.../IssueRemoteMediator.kt`，它是全仓唯一 `RemoteMediator`），plan §4.6 要求的「RemoteMediator（Room）」精神达成、通道换了 |
| 典型 Query `Viewer` | ✅ | `Viewer.graphql` |
| 典型 Query `RepositoryOverview` | ✅ | `RepositoryOverview.graphql`（含 `primaryLanguage`/`licenseInfo`/`viewerHasStarred`） |
| 典型 Query `IssueDetail`（`body` + `bodyHTML` + `timelineItems` 游标 + `IssueComment`) | ❌ | **不存在**。全仓 8 个文档：`IssueWriteContext`、`PullRequestReviewThreads`、`RepositoryOverview`、`ResolveReviewThread`、`UnresolveReviewThread`、`UpdateIssue`、`Viewer`、`ViewerRepositories`。读路径**全部 REST** ⇒ 「GraphQL 读优先」实际未成立 |

### 3.5 §4.5 REST 补位端点表（逐个核查）

| plan 端点 | 实际 | 判定 |
|---|---|---|
| `POST /markdown` | `ReadmeApi.kt:61` `renderMarkdown` | ✅ |
| `GET /repos/{o}/{r}/readme` | `ReadmeApi.kt:33`（html Accept）+ `:47`（meta） | ✅ |
| `GET/PUT/DELETE /repos/{o}/{r}/contents/{path}` | `ContentApi.kt:35`(GET) / `:50`(PUT) / `:66`(`@HTTP(method="DELETE", hasBody=true)`，注释解释 Retrofit `@DELETE` 不允许 `@Body`) | ✅ 三个全在 |
| `GET /repos/{o}/{r}/git/trees/{sha}?recursive=1` | `GitTreeApi.kt:26` | ✅ |
| `GET /repos/{o}/{r}/pulls/{n}/files` | `PullRequestApi.kt:91` | ✅ |
| `GET/POST /repos/{o}/{r}/pulls/{n}/reviews` | `PullRequestApi.kt:190`(POST)；GET 走 **GraphQL** `PullRequestReviewThreads.graphql`（非 REST，能力等价） | 🔶 通道替换 |
| `GET /repos/{o}/{r}/commits/{ref}/check-runs` | `PullRequestApi.kt:164`（另 `:178` `/status` combined） | ✅ |
| `GET/POST... /git/refs` | `GitRefApi.kt:19`(GET `git/ref/heads/{ref}`) / `:26`(POST `git/refs`) / `:38`(GET `branches`)；删除走 `RepoManagementApi.kt:156` `DELETE repos/{o}/{r}/git/refs/heads/{branch}` | ✅ 创建/列出/删除齐备（无 PATCH 改 ref，plan 未要求） |

**额外实现（超规格）**：Reviews 行评论 CRUD（`PullRequestApi.kt:102,115,126,138`）、timeline（`:151`）、Issue subscription（`IssueApi.kt:124,136,147`）、reactions（`:223,236,248,260`）、notifications（`NotificationApi.kt:23,32,38,42`）、search 4 类（`SearchApi.kt:31,40,48,56`）、star/watch/fork/releases/tags/topics/languages（`RepoManagementApi.kt`）、events（`EventsApi.kt:16`）、gists（`GistApi.kt:17`）、trend（`TrendApi.kt`）、follow（`UserApi.kt:130,136,142`）、user repos（`:118`）。

### 3.6 §4.6 缓存策略

| 数据 | plan 策略 | 实际 | 判定 |
|---|---|---|---|
| Viewer | 本地缓存 + 登录后刷新 | `Viewer.graphql` + Apollo normalized cache | ✅ |
| Repo 元数据 | ETag + Room | `CachedRepositoryDao` + `EtagCacheInterceptor` | ✅ |
| README | ETag + 渲染 HTML 缓存（content hash + theme version） | ✅ 完整（`RepoRepository.kt:192-279`） | ✅ |
| Issue/PR 列表 | Paging + RemoteMediator（Room） | Issue ✅（`IssueRemoteMediator` + `IssueDao`）；**PR 列表用 `PullRequestPagingSource`（无 Room）** | 🔶 半覆盖 |
| Timeline | 分页缓存 | 只有分页（`IssueApi.kt:82`），无 Room 缓存 | 🔶 |
| PR 文件列表 | 按 PR head sha 缓存 | **0 命中** | ❌ |
| 文件内容 | 按 `branch+path+sha` 缓存 | **0 命中**（`ContentApi` 直连） | ❌ |
| Markdown 渲染结果 | 按 content hash 缓存 | README ✅（`CachedReadmeEntity.contentHash`）；Issue 正文无缓存 | 🔶 |
| 搜索历史 | Room | ✅ `SearchHistoryDao` + `SearchHistoryRepository` | ✅ |
| 草稿 | DataStore/Room | **0 命中**（grep `draft` 无草稿持久化；`MarkdownEditorViewModel` 仅内存 `MutableStateFlow`）⇒ 进程被杀草稿丢失 | ❌ |
| ETag 存储 | plan 隐含跨会话 | `InMemoryEtagStore`（进程内，重启失效；注释自认待换 Room） | 🔶 |

**§4 域判定：16 条 → ✅7 / 🔶6 / ❌3（PAT GraphQL 降级、Fragment、IssueDetail Query；缓存 3 处缺失）。**

---

## 4. plan.md §5 设计系统

> 本节证据由并行子审计逐条实读核验（`core/designsystem` 32 主文件 / 13 测试；`core/ui` 15 主）。

### 4.1 §5.1 主题来源 / §5.2 主题模型

| 条款 | 判定 | 证据 / 缺口 |
|---|---|---|
| 系统动态配色（Android 12+ 壁纸取色） | ✅ | `ThemeColors.kt:257,270,287`（`supportsDynamicColors` + `dynamicLight/DarkColorScheme`）；API<31 回退 `:269,286`；设置开关 `AppearanceSettingsSection.kt:78-84` |
| 自定义 seed color | 🔶 | `seedColor: Long`（`UserPreferencesRepository.kt:53`）→ `AppThemeHost.kt:46,101` → `ThemeColors.kt:450-456`；**但生成只做 `lightColorScheme(primary = seed)`，其余角色走 M3 基线默认** ⇒ plan §5.2「Material Color Utilities（seed → 全 tonal palette）」**未实现**（`material-color-utilities`/`Hct`/`CorePalette`/`SchemeTonalSpot` 全仓 **0 命中**） |
| 预设主题 | ✅ | 6 套在 `ThemeMode.kt:9-30`（Light/Dark/OLED/DynamicLight/DynamicDark/HighContrast）；设置 UI `AppearanceSettingsSection.kt:76,178-200` |
| 暗色 / OLED 纯黑 / 高对比 | ✅ | `oledEnabled` `:56` + `highContrastEnabled` `:59`；`AppThemeHost.kt:44-45,60-61` |
| **`AppThemePreferences` data class（11 字段）** | ❌ 部分 | **该类全仓 0 命中**（`grep -rni "AppThemePreferences\|ThemePreferences"` 无结果）。实际是接口 `UserPreferencesRepository.kt:12`（扁平 18 个 Flow）。逐字段：见下表 |
| 自定义对比度 / 圆角强度 / 动画强度 / 图标风格 / 代码字体 / 行号开关 | 🔶 | 圆角 ✅ `.62`、动画 ✅ `motionScale .65`、图标风格 ✅ `IconStyle.kt:9`；**对比度 ❌ 只有布尔；代码字体 🔶 存而不消费；行号 🔶 存而不消费** |

| spec 字段 | 存在 | 被主题消费 | 设置页控件 |
|---|---|---|---|
| `themeMode` | ✅ `ThemeMode.kt:9`（7 值） | ✅ `AppThemeHost.kt:42,58` → `AppTheme.kt:44` | ✅ `AppearanceSettingsSection.kt:76,178-200`（3 chip + 3 独立开关） |
| `useDynamicColor` | ✅ `:50` | ✅ `AppThemeHost.kt:43,59` | ✅ `:78-84` |
| `seedColor` | ✅ `:53` | ✅ `AppThemeHost.kt:46,101` → `ThemeColors.kt:450` | ✅ `:86-91`（8 色盘 `:558-568`） |
| `styleVariant` | ❌ 0 命中 | ❌ | ❌ |
| `useOledDark` | ✅ `:56` | ✅ `AppThemeHost.kt:44,60`（并强制关玻璃 `:83-86`） | ✅ `:93-99` |
| `contrastLevel: Float` | ❌ 0 命中（仅布尔 `highContrastEnabled :59`） | 🔶 二值 | 🔶 `:101-107` |
| `cornerScale` | ✅ `:62` | ✅ `AppThemeHost.kt:49,102` → `AppShapes.from(:55)` | ✅ `:161,374` |
| `animationScale` | ✅ `motionScale :65` | ✅ `AppThemeHost.kt:50-55,103` → `LocalMotionScale` | ✅ `:162,407` |
| `iconStyle` | ✅ `IconStyle.kt:9`（3 值） | ✅ `AppThemeHost.kt:75,95` → `LocalIconStyle` → `AppIconSpec.kt:34` | ✅ `:155-160,289` |
| `codeFontFamily` | 🔶 `codeFont: CodeFont :71`（枚举，非 String） | ❌ **零消费** | ❌ 无控件（`strings.xml:45-47` 无引用） |
| `showLineNumbers` | 🔶 `codeLineNumbers :74` | ❌ **零消费** | ❌ 无控件（`strings.xml:48` 无引用） |

**11 字段：✅6 / 🔶3 / ❌2。** `AppearanceSettingsSection.kt:67-68` 自认「代码字体/行号仍隐藏（待 Sora 配置接线）」，与 `docs/ui-design.md:167` 明列项直接冲突。

**ADR-0004 逐条核验**：§1 六套预设 ✅；§2 玻璃 ✅（`GlassRenderPolicy.kt:34`/`GlassSurface.kt:88` API31+ RenderEffect、26-30 降级 `:37`、`blurEnabled` + 4 逐项开关 `:108-153`，已按 ADR-0006 扩到 4 scope）；§3 图标静态库 ✅，**「Material Symbols 变量字体（wght=300）评估」在代码中无落地证据**（无 `dev.vicart` 依赖、无字体资产）。

### 4.2 §5.3 GitHub 语义状态色映射

| 条款 | 判定 | 证据 / 缺口 |
|---|---|---|
| `ExtendedColors` 恰好含 spec 的 14 字段 | ❌ | `ExtendedColors.kt:25-64`：**缺 8 个** `warning,onWarning,info,onInfo,merged,onMerged,draft,onDraft`；**多 10 个** `note/tip/important/caution` 四组容器 + `brand,danger,dangerContainer,onDanger,onDangerContainer`。即 spec 的**状态语义一个都没有**，实际实现的是 **GitHub Alert 卡片语义** |
| Open/Reopened → secondary/tertiary container | 🔶 | `AppStateChip.kt:57,87-89` 用 `extended.success/successContainer/onSuccessContainer`（语义列对、字面偏离）；`GitHubStatus:30-37` **无 REOPENED** |
| Closed → error/errorContainer | 🔶 | `AppStateChip.kt:58,91-93` 用 `extended.danger/dangerContainer/onDangerContainer`（非 `colorScheme.error`） |
| Merged → tertiary（紫） | ✅ | `:59,95-97` |
| Draft → surfaceContainerHigh + onSurfaceVariant | 🔶 | `:60,99-101` dot=`outline`、容器=`surfaceVariant`（**非 `surfaceContainerHigh`**） |
| Checks success/failure/pending → success/error/warning | ❌ | `PullRequestTabContent.kt:631-648` `checkRunTint`：success→`colorScheme.primary`（非 success）、failure→`error` ✅、in-progress→`primary`（非 warning）、else→`onSurfaceVariant`。`AppStateColorRole:40-46` **无 WARNING 角色** |
| `MergeBox` 使用 merged/draft 语义色 | ❌ | `MergeBox.kt:129,252` `surfaceContainerLow`、`:187` `colorScheme.error`、`:178,196` `onSurfaceVariant` |

### 4.3 §5.4 设计令牌

| 条款 | 判定 | 证据 / 缺口 |
|---|---|---|
| `AppDimens` cornerSmall=8 / cornerMedium=12 / cornerLarge=16 / cornerExtraLarge=28 | ✅ | `token/AppDimens.kt:15,16,17,25`（实测 8/12/16/28.dp）；额外 `cornerExtraSmall 4dp :14`、`cornerCardOuter 20dp :20`、`cornerCardInner 4dp :23`、`minTouchTarget 48dp :31` |
| 内容距 16dp | ✅ | `contentPadding :28` = 16.dp |
| **列表横距 16dp** | ❌ 无令牌 | 实际 16dp **硬写**（`IssueListScreen.kt:242`、`PullRequestListScreen.kt:232`、`NotificationsPanel.kt:319`、`PullRequestTabContent.kt:84,228,258,291` 等） |
| **代码块 padding 12dp** | ❌ 无令牌 | 实际 12dp 硬写（`TextMateCodeBlock.kt:104,137`） |
| 颜色一律 `MaterialTheme.colorScheme.*` + `ExtendedColors`，永不硬编码十六进制 | ✅✅ | 主源集 `Color(0xFF` 291 命中中 **289 在 `core/designsystem` 色板定义**（`ThemeColors.kt` 270、`ExtendedColors.kt` 19，属合法主题定义）；真实泄漏仅 2 处（`MaterialYouFusionMapper.kt:20` `0xFF0B0B0D` 是 `ui-design.md:218` 用户拍板值；`PrototypeActivity.kt:41` debug 源集）；命名色（`Color.Red/...`）泄漏 **0**；`feature/**` + `core/ui` 硬编码色 **0** |
| 字体 `AppTypography`（字阶 + 代码等宽追加） | ❌ | **`AppTypography` 全仓 0 命中**；`AppTheme.kt:88-94` 调 `MaterialTheme` **无 `typography =`** ⇒ 全应用跑 M3 默认字阶；等宽无令牌，硬写 `FontFamily.Monospace`（`MarkdownViewer.kt:99`、`TextMateCodeBlock.kt:98,135`、`PullRequestDiffView.kt:268,282,330,362,387`、`CommitDetailScreen.kt:193,390`） |
| 间距令牌化（`ui-design.md:23`「所有容器/按钮/输入框/卡片/弹窗统一走形状令牌」） | 🔶 | 圆角类已收敛（`AppDimens.corner*` 引用 40 次 + `AppShapes` 注入 `MaterialTheme.shapes`）；**间距类未令牌化**：`feature/**` 裸 dp 字面量 **378**（`8.dp` 204 / `16.dp` 100 / `12.dp` 74），其中 `padding` 内 89 + `spacedBy` 41 + `size` 16；最差 `RepoDetailScreen.kt` 55、`IssueDetailScreen.kt` 29、`PullRequestTabContent.kt` 23；`AppDimens.*` 全仓仅引用 58 次 |

### 4.4 §5.5 组件清单（47 项 → ✅11 / 🔶26 / ❌10）

**关键结论：`core/designsystem` 全部只有 19 个 `@Composable`（17 顶层 / 15 public），落在 §5.5 清单里的只有 4 个** —— `AppStateChip`、`AppEmptyState`、`AppErrorState`、`AppLoadingState`。

**A. 通用组件（21 项）**

| 组件 | 判定 | 证据 |
|---|---|---|
| AppTopBar | ✅ | `core/ui/AppTopBar.kt:55`（硬绑 onSearchClick/onNotificationClick/onProfileClick/unreadCount，非通用） |
| AppScaffold | ❌ | 0 命中；裸 `Scaffold(` 24 文件 24 处 |
| AppNavigationBar | ✅等效 | `core/ui/AppBottomBar.kt:40`（内层 `NavigationBar:67`） |
| AppNavigationRail | ❌ | `NavigationRail` 全仓 0 命中（`ui-design.md:60` 只定 3 项底栏，rail 是 plan 独有） |
| AppTabRow | 🔶 | **5 份私有**：`HomeScreen.kt:660`、`PullRequestDetailScreen.kt:678`、`SearchScreen.kt:324`、`ProfileScreen.kt:404`、`MarkdownComposer.kt:103` |
| AppChip | ❌ | 裸 `FilterChip(` 9 文件 15 处 |
| AppLabelChip | 🔶 | `IssueDetailScreen.kt:715` private、`PullRequestDetailScreen.kt:848` private（形参类型不同，不可合并）；designsystem 仅非 Composable `LabelChipColors.kt:47,88` |
| AppStateChip | ✅ | `core/designsystem/component/AppStateChip.kt:77` public |
| AppAvatar | 🔶 | `ReposScreen.kt:560` private、`ProfileScreen.kt:725` private |
| AppAvatarRow | ❌ | 0 命中 |
| AppCard | ❌ | 裸 `Card(` 10 文件 19 处；designsystem 仅 `CardGroup.kt:110` |
| AppListItem | ❌ | M3 `ListItem(` 仅 2 处（`IssueDetailScreen.kt:994`、`ProfileScreen.kt:343`） |
| AppSectionHeader | 🔶 | `SettingsScreen.kt:121` internal（feature 内） |
| AppEmptyState | ✅ | `AppStateViews.kt:38` public |
| AppErrorState | ✅ | `AppStateViews.kt:65` public |
| AppLoadingState | ✅ | `AppStateViews.kt:86` public |
| AppPullToRefresh | ❌ | 裸 `PullToRefreshBox(` 3 文件 6 处（`HomeScreen.kt:469,503`、`IssueListScreen.kt:205,235`、`PullRequestListScreen.kt:195,225`） |
| AppSearchBar | 🔶 | `SearchTopBar.kt:38` internal |
| AppBottomSheet | ❌ | 裸 `ModalBottomSheet(` 5 文件 6 处 |
| AppDialog | ❌ | 裸 `AlertDialog(` 7 文件 15 处 |
| AppSnackbar | ❌ | 裸 `SnackbarHost(` 10 文件 10 处 |

旁证：3 个**已复用**的状态组件另有 **17 份私有重复实现**（`HomeScreen.kt:605,611,621,631`、`IssueListScreen.kt:323,334`、`ProfileScreen.kt:606,618,629,674`、`PullRequestListScreen.kt:320`、`PullRequestTabContent.kt:309`、`SearchScreen.kt:519,526,562,571`、`IssueDetailScreen.kt:1075`）+ 3 个 internal 包装。

**B. GitHub 专属组件（26 项）**

| 组件 | 判定 | 证据 |
|---|---|---|
| RepoHeader | 🔶 | `RepoDetailScreen.kt:745` private |
| RepoLanguageBar | 🔶 | **3 份重复**：`RepoDetailScreen.kt:1024`、`TrendingSection.kt:141`、`GistsScreen.kt:232` |
| RepoFileTree | ✅等效 | `FileTreeSection.kt:37` public（feature 内；Konsist 禁 feature→feature ⇒ 跨模块不可复用） |
| RepoFileItem | 🔶 | `FileTreeSection.kt:92` private `TreeRowItem` |
| IssueHeader | 🔶 | `IssueDetailScreen.kt:531` private |
| IssueTimelineItem | 🔶 | 无同名 Composable（名字被 model 占 `IssueModels.kt:145`），渲染拆 `:799`/`:1293` |
| IssueCommentItem | 🔶 | `IssueDetailScreen.kt:799` private（PR 侧重复 `PullRequestTimelineItems.kt:44`） |
| IssueEventItem | 🔶 | `IssueDetailScreen.kt:1293` private（PR 侧重复 `:184`） |
| PrHeader | 🔶 | `PullRequestDetailScreen.kt:715` private |
| PrTabBar | 🔶 | `PullRequestDetailScreen.kt:678` private `PrTabs` |
| PrConversationTimeline | 🔶 | 跨 `PullRequestTabContent.kt:70` + `PullRequestTimelineItems.kt:44/79/108/150/184` 拼装，无单一组件 |
| PrCommitItem | 🔶 | `PullRequestTabContent.kt:324` private `CommitRow` |
| PrCheckItem | 🔶 | `PullRequestTabContent.kt:422` private `CheckRunRow` |
| PrFileDiffItem | 🔶 | `PullRequestTabContent.kt:528` private `FileRow`；diff 行 `PullRequestDiffView.kt:238` private |
| PrReviewCard | 🔶 | `PullRequestTimelineItems.kt:79` internal |
| MergeBox | 🔶 | 无 `fun MergeBox`；`MergeBox.kt:85` internal → `:116` private `MergeBoxCard` |
| BranchSelector | 🔶 | `PullRequestCreateScreen.kt:234` private `BranchDropdown` |
| CommitDialog | 🔶 | 无独立组件，内联 `FileEditScreen.kt:93,194,209-212` |
| CodeViewer | ✅等效 | `core/editor/CodeEditorView.kt:38`（`editable=false`，`FileViewerScreen.kt:193,277`） |
| CodeEditor | ✅等效 | **同一个** `CodeEditorView.kt:38`（`editable=true`，`FileEditScreen.kt:173`）⚠️ plan 列 2 个组件，实现只有 1 个 `editable` 开关 |
| MarkdownViewer | ✅ | `MarkdownViewer.kt:55` public（+ `EnhancedMarkdownViewer.kt:56`、`webview/WebViewMarkdownRenderer.kt:62`） |
| MarkdownEditor | ✅等效 | `MarkdownEditorView.kt:43` + `MarkdownComposer.kt:62` public |
| ReactionBar | 🔶 | `IssueDetailScreen.kt:771` private；**`feature/pullrequest` 全模块 grep "reaction" 0 命中 ⇒ PR 无反应栏** |
| LabelChipGroup | 🔶 | 无分组组件，内联 forEach（`IssueDetailScreen.kt:618`、`PullRequestDetailScreen.kt:765`） |
| AssigneeRow | 🔶 | `IssueDetailScreen.kt:741` private（PR 侧对应 `ReviewerRow:874`） |
| MilestoneCard | 🔶 | `IssueDetailScreen.kt:1085` private `MilestoneOptionCard`（无仓库 Milestone 列表卡片） |

MISSING 精确验证（`fun <NAME>[ <(]` 与 `grep -w` 双 pattern 全 0 命中）：`AppScaffold`、`AppNavigationRail`、`AppChip`、`AppCard`、`AppListItem`、`AppPullToRefresh`、`AppBottomSheet`、`AppDialog`、`AppSnackbar`、`AppAvatarRow`。

### 4.5 §5.6 图标方案

| 条款 | 判定 | 证据 / 缺口 |
|---|---|---|
| Material Symbols（com.composables，避免 deprecated `material-icons-extended`） | ✅ 依赖 / 🔶 使用 | 依赖 `libs.versions.toml:78-86`（outlined/rounded/sharp × 普通/filled + `-cmp`）+ `core/designsystem/build.gradle.kts:30-34`；依赖是 `material-icons-core`（`libs.versions.toml:72`）**未用 extended ✅**。但 `AppIcon(` 实调仅 **10 处 / 5 文件**，legacy `androidx.compose.material.icons` 仍 **93 处 / 30+ 文件**（`Icons.Filled.` 33、`Icons.AutoMirrored` 20、`Icons.Outlined.` 2） |
| GitHub 专属图标（merge/draft/branch/fork/issue/discussion/workflow/checks）用 Octicons | 🔶 | `AppDevOcticons.kt` 共 **20 枚** ImageVector（Info/LightBulb/Alert/Stop/Flame/Repo/IssueOpened/PullRequest/Merge/Branch/Forked/Star/Eye/Comment/Check/Diff/File/Tag/History/Bookmark，MIT 出处注明）；**缺 draft=0 / discussion=0 / workflow=0** |
| 风格由主题配置驱动：选中 filled / 未选中 outlined，全部矢量可随主题着色 | ✅ | `LocalIconStyle` `token/AppIcon.kt:54` 默认 ROUNDED ← `AppThemeHost.kt:75,95`；`AppIconSpec.vectorFor:34-42`（FILLED→roundedFilled；OUTLINED→selected?outlinedFilled:outlined）；`AppIconSpecTest.kt` 有测；尺寸档 `iconSmall/Medium/Large :20-26` |
| 「不用 deprecated material-icons-extended」 | ✅ | 见上 |
| 全应用禁 emoji 图标（`AGENTS.md` 硬性要求） | ✅✅ | 严格 emoji 范围 grep 全仓仅 **6 命中且全在注释/KDoc**（`★`/`⚠️`）；排除注释后 **0 命中**；UI 用 `AppDevOcticons.Star`（`TrendingSection.kt:118`）+ contentDescription `:119`；`Text("★"…)` 0 命中；`strings.xml` emoji 0 命中 |
| Octicons 以 SVG 入库 | 🔶 | 走 PathParser 内联矢量而非 SVG 资源；`res/drawable/*.xml` 全仓仅 **3 个（全是 launcher 图标）** |

### 4.6 §5.7 动效规范

| 场景 | 判定 | 证据 |
|---|---|---|
| 列表 → 详情 Shared element / container transform | 🔶 试点级 | `SharedTransitionLayout` `AppNavHost.kt:117`；`Modifier.sharedTransitionElement(key)` `LocalNavTransitionScope.kt:41-48`；**仅 1 组配对**（repo 头像：`ProfileScreen.kt:514` + `RepoDetailScreen.kt:770`，key `repo-avatar-${ownerLogin}`） |
| Tab 切换 Fade through | ❌ 偏离 | 首页分区 = `HorizontalPager`（`HomeScreen.kt:291`）+ `PrimaryTabRow:665` + 弹簧回弹（`:251`）；PR 四 Tab = 裸 `TabRow`（`PullRequestDetailScreen.kt:683`）**无任何动画硬切**。无 fade-through |
| BottomSheet Slide + fade | ✅ | M3 `ModalBottomSheet` + `rememberModalBottomSheetState`（`RepoPickerSheet.kt:48`、`IssueDetailScreen.kt:916,1153`、`LineCommentSheet.kt:55`、`ReviewSheet.kt:75`、`PullRequestDetailScreen.kt:208`） |
| Snackbar M3 默认 | ✅ | `SnackbarHost(snackbarHostState)`（`IssueDetailScreen.kt:176` 等 10 文件） |
| PullToRefresh M3 indicator | ✅ | `PullToRefreshBox` + `PullToRefreshDefaults.Indicator`（`HomeScreen.kt:503,510` 显式；另 2 个列表） |
| Star 微缩放 + 颜色变化 | ✅ | `RepoDetailScreen.kt:925` `Animatable(1f)` → `:939-943` HighBouncy spring → `:960-961` `graphicsLayer` |
| 主题切换 Crossfade | ✅ | `AnimatedColorScheme.kt:27-71`（36 个 color role 逐值插值）+ `SettingsScreen.kt:65` 外层 `Crossfade` |
| Emphasized easing + 200–300ms | ✅✅ | `AppMotion.kt:21-36`（400/200/500/300/200/150ms）+ `:41-47` Emphasized CubicBezier（0.2/0/0/1 等）；**全主源集 `durationMillis` 无任何裸 ms 字面量**；唯一 >300ms 是 spec 自写的 `DURATION_TRANSIENT = 500`（`:27`） |
| 尊重系统动画减弱设置 | ✅✅ | `AppMotionScale.kt:44-48` 读 `Settings.Global.ANIMATOR_DURATION_SCALE` + `resolveEffectiveMotionScale:29-32` 取 min + 接线 `AppThemeHost.kt:51-55`；`animationScale` 用户滑杆经 `AppMotion.scaledDuration:72-78`（36 引用 / 10 真实生产调用） |
| 滚动性能优先，不做无谓横向缩放与弹跳 | ✅ | 无横向缩放；弹跳仅 Star（用户拍板 `RepoDetailScreen.kt:131`）与首页 Pager |

**§5 域判定：14 条 → ✅5 / 🔶4 / ❌5（AppThemePreferences 模型、ExtendedColors 字段集、AppTypography、间距/横距/代码块令牌、Checks 语义色、Tab fade-through）。**

---

## 5. plan.md §6 Issue/PR 页面结构

### 5.1 §6.1 Issue 详情页

| 结构元素 | 判定 | 证据 / 缺口 |
|---|---|---|
| TopAppBar（返回 / 仓库名 / 更多：分享、浏览器打开、复制链接） | ✅ | `IssueDetailScreen.kt:178` + 更多菜单 `:333-360`（`issue_share` `:352`、浏览器打开、复制链接 + Snackbar 反馈） |
| IssueHeaderCard：StateChip | ✅ | `:531` `IssueHeader`（内部 `AppStateChip`，`gitHubStatusColorRole` `AppStateChip.kt:55-62`） |
| 标题 / 作者 + 相对时间 | ✅ | `:531-600`；相对时间 `core/ui/.../time/RelativeTime.kt:66-71`（`pluralStringResource`） |
| LabelChips（低饱和 chip） | ✅ | `:618` + `LabelChipColors`（#85 标签混色落地） |
| Assignee 行、Milestone | ✅ | `:741` `AssigneeRow`、`:1085` `MilestoneCard` |
| ReactionBar | ✅ | `:771` `ReactionChip`/`ReactionBar`（含增删，`IssueApi.kt:223,236,248,260`） |
| 操作：编辑 / Subscribe / Close / Reopen（权限决定可见性） | ✅ | `canCloseReopen`/`canEditIssue`/`canSubscribe` 门控（`:403-411`、`:555-600`）；#163 补 Subscribe/Unsubscribe |
| **Labels / Assignees / Milestone 编辑**（plan §7.2 要求） | ✅ 超 §6.1 | `IssueMetaEditSheet`（`:281`）+ `openMetaEditor`/`toggleAssigneeSelection`/`selectMilestone` |
| MarkdownBody（正文，复杂度探测） | 🔶 | 渲染 ✅；**复杂度探测 ❌**（`FeatureDetector` 生产 0 引用） |
| TimelineList：CommentItem（头像/时间/MD/反应/编辑·删除菜单） | ✅ | `:443,799`；编辑 `:294`、删除 ✅、反应 ✅ |
| EventItem（labeled/assigned/locked/closed/reopened…） | ✅ | `:1293` `EventItem` + `IssueTimelineEventType`（`IssueModels.kt:183-198`，`fromRaw` 映射） |
| **CrossReferenceItem / LinkedPrItem** | ✅ | `:1356` `CrossReferenceItem`、`:1366` `LinkedPrItem`；数据侧 `IssueRepository.kt:401,428`（cross-referenced → `sourceIssue`；connected/linked → `linkedPullRequest`）；有测（`IssueRepositoryTimelineTest.kt:20`） |
| BottomCommentBar（工具栏 + 编写/预览 + 输入区，键盘 insets 适配） | ✅ | `:1171` `MarkdownComposer`（编辑/预览双 Tab + 11 按钮工具栏，`core/editor/MarkdownComposer.kt:62`） |

**Material You 适配规范（§6.1 下半）**：StateChip tonal container ✅；标签低饱和 chip ✅；Timeline 用 list item 非重卡片 ✅；评论卡容器色需逐像素核（🔶）；事件项次要文本 + 小图标 ✅；底部栏固定 + WindowInsets ✅。

### 5.2 §6.2 PR 详情页

| 结构元素 | 判定 | 证据 / 缺口 |
|---|---|---|
| PrHeader：StateChip（Open/Closed/Merged/Draft） | ✅ | `PullRequestDetailScreen.kt:715-790`；`PullRequestStateUi.kt`（4 态） |
| 标题、作者、Labels | ✅ | `:713-790` |
| 分支信息 base ← head | ✅ | `:774`（`ChecksSummaryRow` 同行） |
| Labels / Reviewers / Checks 摘要 / Mergeable 状态 | ✅ | `:774` `ChecksSummaryRow(combinedStatus)`；Reviewers `ReviewerRow:874`；Mergeable 经 `PullRequestApi.kt:178` 的 `/status` |
| PrTabs：Conversation / Commits / Checks / Files changed | ✅ | `:627-673`（`PullRequestTab` 枚举 4 值 + `tabTitle` 本地化 `:705-710`） |
| ConversationTab：PR Body（Markdown） | ✅ | `PullRequestTabContent.kt:70` |
| ConversationTab：Review 卡片（approve/comment/request-changes） | ✅ | `PullRequestTimelineItems.kt:79` `PrReviewCard` |
| ConversationTab：Review Comments、Commit References、Timeline 事件 | ✅ | `PullRequestTimelineItems.kt:44/108/150/184`；`:187` `CrossReferenceItem`、`:191` `LinkedPrItem` |
| CommitsTab：提交列表（作者头像、缩写 SHA、展开 diff） | ✅ | `PullRequestTabContent.kt:324` `CommitRow`（diff 展开经 `CommitDiffParser`） |
| ChecksTab：CheckRun 列表（状态图标/结论/失败详情展开） | 🔶 | `PullRequestTabContent.kt:422` `CheckRunRow`；**状态色错**（见 §4.2：success→primary、in-progress→primary，非 success/warning） |
| FilesChangedTab：文件列表（+N −M）→ DiffView（统一/分屏切换、行号） | ✅ | `PullRequestTabContent.kt:528` `FileRow` + `PullRequestDiffView.kt`（统一/分屏 + 行号） |
| FilesChangedTab：行号点击 → 新增/回复行评论、解析会话 | ✅ | `LineCommentSheet.kt`(186) + GraphQL `ResolveReviewThread`/`UnresolveReviewThread`（`PullRequestRepository.kt:461,463`） |
| MergeBox：合并方法选择（merge/squash/rebase） | ✅ | `MergeBox.kt:116` `MergeBoxCard`；`PullRequestApi.kt:204` `PUT /pulls/{n}/merge` |
| MergeBox：合并标题/备注、删除分支选项、合并按钮（按 viewerPermission 显隐） | ✅ | `MergeBox.kt:85-283`；`RepoManagementApi.kt:156` 删分支；`PullRequestRepository.kt` 权限门控 |

### 5.3 §6.3 Review 交互

| 条款 | 判定 | 证据 |
|---|---|---|
| Comment / Approve / Request changes / Submit review | ✅ | `ReviewSheet.kt`(143) + `PullRequestApi.kt:190` `POST /pulls/{n}/reviews` |
| 编辑评论、回复评论、解决/解除解决会话（对应 API 权限） | ✅ | `PullRequestApi.kt:126`（PATCH 评论）、`:115`（回复）；`PullRequestRepository.kt:461,463`（GraphQL resolve/unresolve）+ `LineCommentSheet.kt:87` |
| 乐观更新 + 失败回滚 + 错误提示 | ✅ | `RepoEvent`/`RepoDetailScreen.kt:287-345`（Snackbar 分类）；`PullRequestError.kt`；Issue 侧 `IssueError.kt` |

**§6 域判定：7 条 → ✅4 / 🔶3 / ❌0。这是与 plan 对齐度最高的域。**

---

## 6. plan.md §7 写功能

### 6.1 §7.1 Markdown 编辑

| 条款 | 判定 | 证据 / 缺口 |
|---|---|---|
| 编辑器：Sora Editor 加载 Markdown TextMate 语法 | ✅ | `core/editor/src/main/assets/grammars/markdown.tmLanguage.json` + `MarkdownEditorView.kt:61-67` |
| 工具栏：加粗/斜体/行内代码/代码块/标题/列表(有序、任务)/链接/图片/引用 | ✅ | `MarkdownComposer.kt:147-171`（**11 个按钮**全齐，矢量图标 + `stringResource` contentDescription） |
| 编辑/预览双 Tab，预览与主渲染管线共用 | 🔶 | 双 Tab ✅（`MarkdownComposer.kt:77-83`）；但预览走**离线** markdown-it（`MarkdownEditorScreen.kt:139`），plan §7.1 要求「服务端渲染时走 `POST /markdown`」⇒ 编辑态与展示态不一致（mermaid/katex/emoji 短码/锚点在预览里看不到） |
| **提及补全：@user、表情、引用自动补全（Sora auto-complete API）** | 🔶 | 补全器完整（`MarkdownCompletion.kt:42-58`，`@`→mention、`:`→emoji，`MarkdownEditorLanguage` 包装 Sora `CompletionPublisher`）+ `MarkdownCompletionProviderTest.kt` 有测；**但 `mentions` 参数默认 `emptyList()`（`MarkdownComposer.kt:72`），全仓无任何生产调用点传值**（grep `mentions =` 仅得定义处与测试）⇒ **@user 补全在生产中永不产生候选**；emoji 补全 ✅（`DEFAULT_MARKDOWN_EMOJIS` 默认值）。「引用自动补全」未实现 |
| 明确不用 WYSIWYG rich editor | ✅ | 用 Sora 源码编辑器，符合 |

### 6.2 §7.2 Issue 操作

| 条款 | 判定 | 证据 |
|---|---|---|
| 创建 Issue、编辑 body/title | ✅ | `CreateIssueScreen.kt` + `IssueApi.kt:97`(POST) / `:110`(PATCH) + `EditIssueDialog`（`IssueDetailScreen.kt:268`） |
| 关闭 / 重开 | ✅ | `IssueDetailScreen.kt:239`（`closeIssue`/`reopenIssue`） |
| 评论新增、编辑、删除、反应 | ✅ | `IssueApi.kt:189,200,212,223,236,248,260` |
| Labels / Assignees / Milestone 编辑（权限实测） | ✅ | `IssueMetaEditSheet`（`IssueDetailScreen.kt:281`）+ `IssueApi.kt:157,168,179`（列表查询）；权限门控 `canEditMeta` |
| Subscribe/Unsubscribe | ✅ | `IssueApi.kt:124,136,147` + `IssueDetailScreen.kt:405,589` |
| 任务列表反向同步 | ✅ | `flipTaskListItem`（`IssueRepository.kt:468-480`）+ `FlipTaskListItemTest.kt` 5 用例 |

### 6.3 §7.3 PR 审查与合并

| 条款 | 判定 | 证据 |
|---|---|---|
| 创建 PR（base/head）、编辑、关闭、重开 | ✅ | `PullRequestCreateScreen.kt`(292) + `PullRequestApi.kt:245`(POST) / `:231`(PATCH) |
| Review：approve / comment / request changes / submit | ✅ | `ReviewSheet.kt` + `PullRequestApi.kt:190` |
| 行内评论（position/side/anchor） | ✅ | `LineCommentSheet.kt` + `PullRequestApi.kt:115` + GraphQL anchor（`PullRequestReviewThreads.graphql`） |
| Merge（merge/squash/rebase）、Delete branch、Update branch | ✅ | `PullRequestApi.kt:204`(merge) / `:217`(update-branch) / `RepoManagementApi.kt:156`(delete ref) |
| Mutation 示例 `mergePullRequest` | ✅ 等价 | 实现走 REST `PUT /pulls/{n}/merge` 而非 GraphQL mutation（plan §4.3「REST 写优先」与 §7.3 示例冲突，实现选了 REST） |

### 6.4 §7.4 文件编辑（Contents API）

| 条款 | 判定 | 证据 |
|---|---|---|
| `GET contents`（sha + base64）→ 解码 → Sora 编辑 → commit message → 分支 → `PUT contents` | ✅ | `ContentApi.kt:35,50` + `FileEditScreen.kt`(379) + `CodeEditorView.kt:38`(editable=true) |
| 创建：同 PUT（无 sha） | ✅ | `FileWriteRequest` sha=null 语义（`ContentApi.kt:46-48` 注释） |
| 删除：`DELETE` + sha | ✅ | `ContentApi.kt:66`（`@HTTP(method="DELETE", hasBody=true)`） |
| **冲突处理：409 → reload / overwrite / Copy local changes，绝不静默覆盖** | ✅✅ | `FileEditScreen.kt:61`（注释「绝不静默覆盖」）+ `:268-330` 三选项对话框（`conflict_reload` `:295`、`conflict_overwrite` `:298`、`conflict_keep_local` `:313`）；`ConflictOperation.UPDATE/DELETE` 分支 |
| 支持「提交到当前分支 / 新建分支」两种模式 | ✅ | `FileEditScreen.kt:101,219-240`（`useNewBranch` + `newBranchName` 校验 `:192`）；`RepoRepository.kt:282-284`（GitHub PUT 对不存在 ref 自动建分支，2026-08-22 实测） |

### 6.5 §7.5 分支管理

| 条款 | 判定 | 证据 |
|---|---|---|
| 列出分支、切换 | 🔶 | 列表 ✅ `BranchesScreen.kt`(358) + `GitRefApi.kt:38`；**「切换分支」无实现**（无 BranchSelector 组件，无 ref 状态切换入口；`FileViewerScreen`/`RepoDetailScreen` 的 ref 来自路由参数） |
| 创建分支（Git Refs API） | ✅ | `GitRefApi.kt:26` `POST /git/refs` + `BranchesScreen.kt:191` |
| 删除分支（权限） | ✅ | `RepoManagementApi.kt:156` + `BranchesScreen.kt:211`（默认分支 422 保护注释 `:154`） |
| PR 创建时选 base/head | ✅ | `PullRequestCreateScreen.kt:234` `BranchDropdown` |

### 6.6 §7.6 仓库管理

| 条款 | 判定 | 证据 |
|---|---|---|
| Star / Unstar、Watch / Unwatch、Fork | ✅ | `RepoManagementApi.kt:34,41,48`(star) / `:55,62,70`(watch) / `:77`(fork)；乐观更新 + 回滚（`RepoDetailScreen.kt:248-250`） |
| 创建 / 删除仓库（设置页） | ✅ | `UserApi.kt:118`(POST /user/repos) + `CreateRepoScreen.kt`；`RepositoryApi.kt:26`(DELETE) + `DeleteRepoDialog`（`RepoDetailScreen.kt:349`） |
| Releases / Tags 浏览（发布 Release，上传 asset） | ✅ | `RepoManagementApi.kt:84,91,99,117,131`（列表/详情/tags/POST release/POST asset）+ `ReleaseCreateScreen.kt` + `ReleaseAsset.kt` |
| Topics、License、语言栏（Linguist 数据） | ✅ | `RepoManagementApi.kt:106`(`/languages`)、`:145`(`/topics`)；`RepoDetailScreen.kt:791` Topics chip 行、`:1024` `LanguageBar`；License 经 `RepositoryOverview.graphql` `licenseInfo` |
| 通知管理：列表 / 标记已读 / 分类过滤 | ✅ | `NotificationApi.kt:23,32,38,42` + `NotificationsPanel.kt`（分组折叠 #88 + 滑动操作 + 筛选 + 全部已读） |

**§7 域判定：11 条 → ✅5 / 🔶5 / ❌1（分支切换缺失）。**

---

## 7. plan.md §8 代码浏览与编辑

### 7.1 §8.1 代码浏览

| 条款 | 判定 | 证据 / 缺口 |
|---|---|---|
| Sora Editor read-only（TextMate 高亮、行号、横向滚动、搜索、跳转行） | ✅ | `CodeEditorView.kt:38,74`（`setEditable(editable)`）；`CodeEditorController.kt`（`startSearch()`）；`FileViewerScreen.kt:113` 顶栏搜索；11 种 grammar（`core/editor/src/main/assets/grammars/`） |
| 大文件 / binary 提示 | ✅ | `FileClassifier.kt` + `FileViewerScreen.kt:184`（`repo_file_binary`） |
| SVG/图片预览 | 🔶 | 图片预览需逐项核（有 `AppImageOverlay`）；**SVG 依赖 `coil-svg`** — `AGENTS.md` 提到 shields 徽章需 `coil-svg + SvgDecoder`，本次未逐项验证 SVG 渲染器注册 |
| Markdown 文件可切 Rendered/Source | ✅ | `FileViewerScreen.kt:251-290`（`showSource` 切换，Rendered 用原生 `EnhancedMarkdownViewer:285`） |
| 编码处理与 CRLF 保留 | ❌ | **0 命中**（grep `CRLF\|crlf\|encoding\|Charset` 在 `feature/repo` 无相关处理；`ContentApi` 只做 base64 解码） |

### 7.2 §8.2 代码编辑（Sora Editor）

| 条款 | 判定 | 证据 / 缺口 |
|---|---|---|
| undo/redo | ✅ | `CodeEditorController.kt:84-91` |
| 查找替换 | 🔶 | `startSearch()` 只有**查找**；**替换 0 命中** |
| 自动缩进 | ✅ | Sora + TextMate 默认（`setTabWidth(4) :77`） |
| 括号配对 | ✅ | `MarkdownCompletion.kt:149` `delegate.symbolPairs`（CodeEditor 走 TextMate 默认 symbol pairs） |
| **软换行切换** | ❌ | `isWordwrap = false` **硬编码**（`CodeEditorView.kt:76`）⇒ 无切换入口；Markdown 编辑器侧硬编码 `true`（`MarkdownEditorView.kt:82`） |
| 等宽字体 | 🔶 | 硬写 `FontFamily.Monospace`（无 `AppTypography` 令牌，且 `codeFont` 偏好零消费） |
| 主题同步（编辑器配色 = M3 派生：bg=surfaceContainerLow / text=onSurface / line number=onSurfaceVariant / selection=primaryContainer / current line=surfaceContainerHigh / keyword=tertiary / string=success / comment=onSurfaceVariant / function=primary / number=secondary） | ✅ | `core/editor/M3EditorTheme.kt`(149) + `M3EditorThemeTest.kt` + `core/markdown/M3TextMateTheme.kt`(149) + `M3TextMateThemeTest.kt`；`rememberM3EditorThemeTokens()` |

### 7.3 §8.3 Diff 视图

| 条款 | 判定 | 证据 |
|---|---|---|
| 自研 Compose 统一 diff（行号 + `+/-/context` 着色 + 空白行数处理） | ✅ | `feature/pullrequest/data/DiffParser.kt` + `PullRequestDiffView.kt:238`（diff 行渲染） |
| 行内评论点位计算（REST position/side 或 GraphQL anchor） | ✅ | `LineCommentSheet.kt` + `PullRequestApi.kt:115` + `PullRequestReviewThreads.graphql` |
| 支持分屏（side-by-side）与统一视图切换 | ✅ | `PullRequestDiffView.kt`（503 行，两模式切换） |
| v1 复杂度过高时 WebView 兜底 | ✅ 未触发 | 纯 Compose 实现，无需兜底 |

**§8 域判定：6 条 → ✅2 / 🔶2 / ❌2（CRLF/编码、软换行切换；查找替换降级为 🔶）。**

---

## 8. plan.md §9 搜索

| 条款 | 判定 | 证据 |
|---|---|---|
| §9.1 范围：Repositories / Users / Issues / Pull Requests / Code | ✅ | `SearchTab` 枚举 5 值（`SearchUiState.kt:16-25`）；`SearchPagingRepository.kt:37-49` 五路各自 Pager；`SearchApi.kt:31,40,48,56` |
| （可选 Discussions） | ✅ 未做（plan 标可选） | — |
| §9.2 M3 SearchBar | ✅ | `SearchTopBar.kt:38`（internal `AppSearchBar` 对应物） |
| §9.2 搜索历史（Room） | ✅ | `SearchHistoryDao` + `SearchHistoryEntity` + `SearchHistoryRepository.kt:16-33` |
| §9.2 qualifier 快速建议 | ✅ | `qualifier/SearchQualifier.kt:17-21`（`QUALIFIER_SUGGESTIONS` 静态清单，YAGNI 取舍有注释） |
| §9.2 结果 Tabs（仓库/用户/issue） | ✅ | `SearchScreen.kt:324` Tab 行 |
| §9.2 结果列表单独 Paging | ✅ | `SearchPagingSource.kt:19-21`（每 Tab 独立 Paging，注释解释限流考量：只收集活动 Tab 的流） |
| §9.2 代码搜索需登录 | ✅ | `SearchApi.kt:21`（未认证 401）+ 代码搜索门禁 UI |
| §9.3 REST 四端点 | ✅ | `SearchApi.kt:31,40,48,56` |
| §9.3 限流处理 + 结果缓存 | ✅ | `SearchRateLimitSection.kt` + `SearchRateLimitWarning.kt`（429 归一化）；`SearchResultCache.kt`(56) + `SearchCacheModule.kt` |

**§9 域判定：3 条 → ✅3 / 🔶0 / ❌0。完全达标。**

---

## 9. plan.md §10 架构 / §11 i18n

### 9.1 §10.1 模块结构

| 条款 | 判定 | 证据 / 缺口 |
|---|---|---|
| `app/` + `core/*`(14) + `feature/*`(10) | ✅ | `settings.gradle.kts:29-57`（25 个 Gradle project + `:prototype:readme-comparison`） |
| core 子模块清单与 plan 一致 | ✅ | common / designsystem / data / ui / navigation / github-graphql / github-rest / github-auth / github-data / markdown / editor / database / datastore / testing —— 14 个全在（`find core -maxdepth 1 -type d`） |
| feature 子模块清单一致 | ✅ | auth / home / repo / issue / pullrequest / search / editor / settings / notifications / profile —— 10 个全在 |

### 9.2 §10.2 模块职责

| 条款 | 判定 | 证据 |
|---|---|---|
| `core:github-*` 只依赖网络与模型，不依赖 UI | ✅ | Konsist 规则②：`ArchitectureTest.kt:48-55`（github-graphql/rest/auth/data 不得 import `androidx.compose` / `core.ui` / `core.designsystem`），实测在跑 |
| `core:markdown` 内部隔离 WebView（`AndroidView` 仅存在于该模块内部） | ✅ | `AndroidView` 在 markdown 模块内（`WebViewMarkdownRenderer.kt`）；`feature/editor` 只通过 `WebViewMarkdownRenderer` 组件调用 |
| `core:editor` 隔离 Sora 依赖 | ✅ | `io.github.rosemoe.*` 导入仅出现在 `core/editor/src/main`（grep 确认） |
| feature 之间通过 navigation 深链交互，不互相引用 | ✅ | Konsist 规则③ `ArchitectureTest.kt:58-68` + 规则④（包名断言防漏检）`:71-88` |
| **Konsist 校验：分层依赖方向；`core:model` 禁止 import android** | ✅ | 规则①（core 不得依赖 feature）`:39-45` + 规则⑤（model 包不得 import `android.*`，模块表 `:166`）`:91-98`；共 **6 条**规则实际在跑（含 ⑥ i18n key 对齐 `I18nParityTest.kt:24-45`），`konsistCheck` 显式声明 inputs 防 UP-TO-DATE 静默跳过（`app/build.gradle.kts:250-263`） |
| （未覆盖，非声称项） | — | `core:common`/`core:data` 未禁 Compose/UI（`:51` 仅限 github-*）；core→app 未禁；命名规范靠 ktlint/detekt 无 Konsist 规则；无「禁止硬编码文案」规则 |

### 9.3 §10.3 状态管理（UDF）

| 条款 | 判定 | 证据 |
|---|---|---|
| `IssueDetailUiState(status/issue/timeline: PagingData/canEdit/canComment)` | ✅ | `IssueDetailUiState.kt`（`UiStatus` Loading/Content/Error + `Success.canEditIssue/canCloseReopen/canComment`） |
| `@HiltViewModel` + `SavedStateHandle` + UseCase/Actions 注入 | 🔶 | `@HiltViewModel` 大量使用 ✅；**`GetIssueDetailUseCase` / `IssueActions` 这样的 UseCase 层 0 命中** —— 实际是 ViewModel 直连 Repository（`IssueDetailViewModel` → `IssueRepository`），plan 示例的分层未落地（YAGNI 取舍，但偏离 spec 字面） |
| ViewModel 暴露 `StateFlow<UiState>` | ✅ | 全部 VM 一致（`MarkdownEditorViewModel.kt:29-30` 等） |
| 写操作走事件通道：乐观更新 / 失败回滚 / Snackbar 错误规整 | ✅ | `RepoEvent`/`RepoDetailScreen.kt:287-345`；`IssueError.kt`/`PullRequestError.kt`（错误 → 文案映射） |

### 9.4 §11 i18n

| 条款 | 判定 | 证据 / 缺口 |
|---|---|---|
| §11.1 `values/`（英文）+ `values-zh-rCN/` | ✅ | **15/15 模块成对存在**（全量枚举）；`I18nParityTest.kt:24-45` 硬断言 key 对齐 |
| §11.1 `stringResource(R.string.…)`，含 contentDescription | ✅✅ | **625 × `stringResource(` / 12 × `pluralStringResource(`**；主源集 `Text("…")` = **0**、`contentDescription = "…"` = **0**、slot 字面量 = **0** |
| §11.2 `<plurals>` | ✅ | 全仓 **22 个** `<plurals>`；`core/ui/src/main/res/values/strings.xml:29-52`（`time_just_now` + minutes/hours/days/weeks/months/years）、`notification_unread_badge_cd` 等；`feature/issue/.../strings.xml:16`、`feature/pullrequest/.../strings.xml:16,81` |
| §11.2 相对时间本地化（3 分钟前 / yesterday / 2 days ago） | ✅ | `core/ui/.../time/RelativeTime.kt:66-71`（`pluralStringResource`）；**无硬编码「3 分钟前」** |
| §11.2 绝对时间走 `java.time` + locale | 🔶 | 全仓唯一 `DateTimeFormatter`：`RepoDetailScreen.kt:1806` `ofPattern("yyyy-MM-dd")` —— 用了 java.time 但 pattern 为 ISO 固定式，**非 locale 化** |
| §11.3 一律 `start/end` | ✅ | `padding(left\|right)` = **0**、`Alignment.Left/Right` = **0**、`padding(start/end)` = 17；`AndroidManifest.xml:13` `supportsRtl="true"` |
| §11.3 代码块保持 LTR | ❌ | `renderer.js` 无 `dir`/`direction`（grep 空）；`markdown-you.css:45-80` 的 `code`/`pre` 无 `direction`/`unicode-bidi`（grep 空）；`core/markdown`+`core/editor` 无 `TextDirection`/`LayoutDirection` |
| §11.3 WebView 内 Markdown 按内容 `dir` 处理 | ❌ | 同上（`<html lang="en" data-theme=...>` 硬写 `lang="en"`，`WebViewHtmlBuilder.kt:97`） |
| §11.3 阿拉伯语布局纳入截图测试矩阵 | ❌ | **无 `values-ar`**；RTL 截图测试仅 1 个（`core/ui/.../AppTopBarRtlScreenshotTest.kt`）→ 1 张基线 `AppTopBar_rtl.png`；测试文件 `:26-27` 自认「全屏级 RTL 矩阵仍待真机/模拟器补」 |
| §11.4 开启 `MissingTranslation / HardcodedText / SetTextI18n / StringFormatInvalid` | ❌ | 唯一 lint 块 `app/build.gradle.kts:29-67` 只有 `abortOnError = true`(`:30`)、`checkReleaseBuilds = false`(`:31`)、15 条 Compose detector disable(`:52-66`)；**全仓无 `lint.xml`**；四条规则名 grep 仅命中 `plan.md:799` 与 `I18nParityTest.kt:10` 注释。仓库自认：「lint 的 MissingTranslation 默认只报 warning，而本项目 `:app:lintDebug` 是 abortOnError（warning 不拦）」(`I18nParityTest.kt:10-13`)。且 CI 只跑 `:app:lintDebug`（`ci.yml:79`）+ nightly 加 `:core:ui:lintDebug`（`nightly.yml:59`）⇒ **feature 模块的资源问题不进任何门禁** |

### 9.5 §12/§13/§14 测试与 CI/CD（用户原话 R17/R18/R22 的落点）

**§12 测试金字塔**

| 层 | 判定 | 证据 |
|---|---|---|
| 单元（JUnit4 + MockK + Turbine） | ✅ | 188 测试文件 / **1786 个 `@Test`**；`core:testing` 基建（`MainDispatcherRule`/`ScreenshotTest`/`GitHubFakes`） |
| 集成（MockWebServer / Apollo MockServer） | ✅ | `core/github-rest/src/test`（30 文件，如 `ReadmeApiTest.kt:168,325`）；`core/github-graphql/src/test/.../GraphQLIntegrationTest.kt` |
| Compose UI（Robolectric Native Graphics） | ✅ | `core/testing/.../screenshot/ScreenshotTest.kt:36-38`（`RobolectricTestRunner` + `GraphicsMode.NATIVE` + `@Config(sdk=[35])`）；`isIncludeAndroidResources = true` 在 15 模块逐一显式声明 |
| 截图（Roborazzi） | 🔶 | 12 个截图测试类 / **35 张基线 PNG**（app 2、core/designsystem 4、core/markdown 6、core/ui 9、feature/auth 2、feature/issue 4、feature/notifications 2、feature/repo 2、prototype 4） |
| §12.2 单测覆盖点名项（`GitHubLinkParser` 组合矩阵 / 特性检测 / 模板令牌生成器 / CSS 生成器 / 配额 / PagingSource） | ✅ | `GitHubLinkParserTest.kt`、`FeatureDetectorTest.kt`（15 用例）、`MarkdownThemeTokensTest.kt`、`WebViewHtmlBuilderTest.kt`、`ViewerRepositoriesPagingSourceTest.kt` |
| §12.4 `testOptions.unitTests { isIncludeAndroidResources = true }` | ✅ | 15 模块逐一 |
| **§12.5 截图矩阵（Light/Dark/OLED/Dynamic-mock/高对比 × en/zh/ar-RTL × 大字 × 各预设主题）** | ❌ | 实测覆盖仅 **Light/Dark × en（+ 顶栏 1 张 RTL）**。未覆盖 7 维：**OLED**（仅行为断言 `AppThemeHostTest.kt:191-204`）、**Dynamic mock**（仅 flag 管道 `:325,349,401`）、**高对比**（`AppThemeHostTest.kt:208-222,271-285`）、**zh**（`I18nParityTest` 只比 key 不比渲染）、**大字**（无 fontScale 用例，`@Config(qualifiers=…)` 仅 6 处全为 `"night"`）、**各预设主题**（`ThemeMode` 7 值无分主题截图）、**全屏 RTL**。⚠️ `AppTopBarRtlScreenshotTest.kt:20-27` 自述 `@Config(qualifiers="ar-rSA-ldrtl")` 实测**不生效**（渲染与 LTR 逐字节相同 ⇒ 假绿），改用组合内 `LocalLayoutDirection` |
| §12.5 门禁覆盖缺口 | 🔶 | `ci.yml:145-153` / `nightly.yml:106-114` 只 verify **7 模块**；`:feature:issue` 因 ModalBottomSheet 无限动画挂死被排除（`ci.yml:133-136`）；`:prototype:readme-comparison` 的 4 张基线**不在任何 verify/record 列表**（孤儿），且 `prototype/` 不在 detekt 范围（`build.gradle.kts:53`） |
| §12.5 支持「点击后再截」 | ✅ | Roborazzi 交互后截图（如评论打开态） |
| §12.5 产物 `build/outputs/roborazzi/*.png` + diff 图，Linux 本机可预览 | ❌ | CI **只在 failure 上传** roborazzi 产物（`ci.yml:155-163`）；**无 HTML 报告配置**（grep `generateHtmlReport\|htmlReport` 无命中）；该路径在 docs 中无落地配方（grep 仅命中 plan.md 与调研稿） |
| §12.6 本机运行命令 | ✅ | `./gradlew` 任务全部存在（`spotlessCheck`/`detekt`/`konsistCheck`/`lintDebug`/`testDebugUnitTest`/`coverageVerify`/`verifyRoborazziDebug`/`assembleDebug`） |

**§13 CI/CD**

| 条款 | 判定 | 证据 |
|---|---|---|
| §13.1 PR/main 检查（spotless+deteck+lint+konsist+test+roborazzi+assemble+artifact） | ✅✅ | `ci.yml:35` job `quality`：checkout(fetch-depth 0) `:44` → setup-java 21 `:49` → `spotlessCheck` `:73` → `detekt` `:76` → `:app:lintDebug` `:79` → Lint detector coverage guard `:88` → `konsistCheck` `:112` → `:app:testDebugUnitTest` `:115` → verify 7 模块截图 `:143-153` → `:app:assembleDebug` `:165` → upload APK `:168` → `coverageReport coverageVerify`（硬门禁）`:178` → `diffCoverageCheck`（硬门禁，仅 PR）`:187-193` → PR 覆盖率 sticky 评论 `:211`。`concurrency: cancel-in-progress` + `paths-ignore: **/*.md` |
| §13.2 发布流程（tag v* → 版本号 → keystore → AAB/APK → Release 草稿 → mapping） | ✅ | `release.yml`（`push: tags v*`）：Decode keystore from Secrets `:51-57` → `:app:assembleRelease :app:bundleRelease` `:64` → upload `mapping.txt` `:66` → `softprops/action-gh-release` 草稿（APK+AAB+mapping）`:73-80` |
| §13.3 质量门禁全绿方可合并 | ✅ | 上述全部为硬门禁；`diffCoverageCheck` 无 `continue-on-error` |
| §13.3 无新增硬编码字符串、无新增禁用 API（lint 规则） | ❌ | 见 §11.4 —— 四条 i18n lint 规则未启用，且 feature 模块根本不跑 lint |
| §13.4 Dependabot / Renovate 每周自动升级 + 分组 | ✅ | `.github/dependabot.yml`（gradle + github-actions 各每周 1 个聚合 PR `:11,:29`；6 条 ignore） |
| 附加（超规格）：nightly / 截图漂移检测 / release 体积回归 / 失败自动开 issue | ✅ | `nightly.yml`（6 job）：quality `:34`、module-screenshots `:76`、screenshot-drift（全模块 record 后 `git diff --quiet` 断言）`:126`、screenshot-refresh `:183`、release-shrink（`assembleRelease` R8 回归）`:232`、notify-failure `:276` |
| 附加：签名发布演练 + Baseline Profile 打包断言 | ✅ | `release-dry-run.yml`：临时 keystore → 签名 APK/AAB → apksigner 验签 + **硬断言 APK 内 `assets/dexopt/baseline.prof`** `:80-87` |

**§14 性能**

| 条款 | 判定 | 证据 / 缺口 |
|---|---|---|
| §14.1 目标（冷启动 <1.5s / 首页 <2s / README <800ms，缓存 <300ms / 60fps / APK 控制） | ❌ | **无一有实测数据**；`docs/agents/project-status.md` 把冷启动/帧率列为「需真机」。APK 体积有回归 job（`nightly.yml:232`）与 `R8 + shrinkResources`（`app/build.gradle.kts:75-76`） |
| §14.2 Compose（stable 类 / remember / derivedStateOf / LazyColumn key+contentType） | ✅ | `itemKey` 迁移（ui-audit #86）；`@Immutable`/stable 标注 |
| §14.2 Markdown 渲染结果缓存复用 | ✅ | README 双 key 缓存；Issue 正文无缓存（🔶） |
| §14.2 Coil 图片缓存 / 虚拟滚动零废图 | ✅ | Coil 3（`libs.versions.toml`）+ `Coil3ImageTransformerImpl` |
| **§14.3 Baseline Profiles（`androidx.benchmark` 生成：启动路径/首页滚动/Issue 详情/README 渲染/主题切换）** | ❌ | **`androidx.benchmark` / macrobenchmark / `baselineprofile` 插件全仓 ABSENT**（grep libs.versions.toml / build.gradle.kts / app/build.gradle.kts / settings.gradle.kts 全空）。`app/src/main/baseline-prof.txt`（70 行）存在但**是手写**人工推导（文件头 `:7-11` 自述 macrobenchmark 需真机故未接入）；`androidx.profileinstaller:profileinstaller 1.4.1`（`libs.versions.toml:27,101`；`app/build.gradle.kts:195`）✅ |
| §14.4 WebView 内存（单实例复用 / 离开 destroy / 减少列表中 WebView / LeakCanary） | 🔶 | destroy ✅（`WebViewMarkdownRenderer.kt:134-142` `DisposableEffect` → `stopLoading` + `clearHistory` + `destroy` + 断 bridge）；**单实例复用 ❌**（每处 `WebView(ctx)` 新建 `:157`）；LeakCanary 依赖 ✅ |

**§10/§11/§12–§14 域判定：17 条 → ✅8 / 🔶6 / ❌3（§11.3 代码块 LTR+dir、§11.4 lint 四规则、§12.5 截图矩阵、§13.3 lint 门禁、§14.1 性能实测、§14.3 macrobenchmark —— 合并后按上表计）。**

---

## 10. 缺口清单（按优先级）

| 优先级 | 缺口（具体到函数/UI 元素/端点） | 证据（file:line） | 建议动作 |
|---|---|---|---|
| **P0** | **无 Markdown 渲染回归集，「与网页端一致」不可验证**。需建 golden fixture 集（`.md` 输入 + GitHub 服务端 HTML 期望）与对比测试；`prototype/readme-comparison` 的 4 张基线是唯一相关物证且为孤儿 | `plan.md:950`（风险表点名对策）；`find . -name '*ScreenshotTest.kt'` 无 markdown 对比类；`build.gradle.kts:53`（detekt 不含 prototype）；`ci.yml:145-153`（verify 列表无 prototype） | **新建票**（P0「Markdown 一致性回归集」）——把 `prototype/readme-comparison` 提升为 `app/src/test` 或独立 `:core:markdown:goldencheck` 任务并接入 CI verify |
| **P0** | **离线 GFM 缺 6 项**：KaTeX / Mermaid / 脚注 / emoji 短码 / 锚点 `scrollToAnchor` / 相对链接重写。`FeatureDetector` 判定 MATH/MERMAID 后**无渲染器可去**（assets 无 katex/mermaid） | `core/markdown/src/main/assets/webview/`（仅 markdown-it/highlight/purify + 3 css）；`renderer.js:308-309`（只 use 2 插件）；`FeatureDetector.kt:93,100`；`ghMarkdownProcessor` 0 命中 | **并入 #1（Spec 常开票）/ 或 #167 动效外的「渲染补全」新票**。拆两条：① emoji 短码 + 脚注 + 锚点（低成本，Kotlin/assets 即可）；② KaTeX/Mermaid（需评估体积，可延后） |
| **P0** | **GraphQL 读优先未成立**：无 `IssueDetail.graphql`（`bodyHTML` + `timelineItems` 游标）；无 Fragment；URI scalar 未映射；Comment 请求全部 REST | `core/github-graphql/src/main/graphql/` 8 文件清单；`grep bodyHTML` 0 命中；`scalar/` 仅 `InstantAdapter.kt` | **新建票**或并入 #165（数据域）。若坚持 REST-only 是**有意的架构决定**，则须**修订 plan.md §4.4** 并记录 ADR（当前是静默偏离，最伤后来者） |
| **P0** | **PAT 降级未落地**：`isRestOnly=true` 后 GraphQL 仍被无条件调用 → PAT 用户拿到 403 而非自动走 REST | 写：`Downgrade.kt:22`；读仅用于 `AuthState`：`OAuthSessionManager.kt:133`；**消费点无门控**：`IssueRepository.kt:285,324`、`PullRequestRepository.kt:152,418,461,463`、`ViewerRepositoriesPagingSource.kt:30`、`DefaultRepositoryRepository.kt:32`、`DefaultUserRepository.kt:29` | **新建票**（小改动，高用户可见度）：在 `IssueRepository`/`PullRequestRepository` 的 Apollo 分支加 `isRestOnly` 判定并走 REST 替代路径；配 1 个单测 |
| **P1** | **§5.5 组件清单 10 项 0 命中 + 26 项 private 不可复用 + 17 份重复状态视图** | `AppScaffold`/`AppCard`/`AppChip`/`AppDialog`/`AppSnackbar`/`AppBottomSheet`/`AppPullToRefresh`/`AppListItem`/`AppNavigationRail`/`AppAvatarRow` 双 pattern grep 全 0；裸调用计数见 §4.4；重复实现清单同处 | **并入 #166/#168 的后续波**（UI 打磨四期）。优先抽 `AppCard`/`AppDialog`/`AppSnackbar`/`AppChip` 四个（裸调用 19/15/10/15 处，收益最高）；17 份重复状态视图抽 1 个共享组件 |
| **P1** | **`AppTypography` 不存在 + 间距令牌缺失**：`MaterialTheme` 未传 `typography`；`feature/**` 裸 dp 378 处；等宽字体 11 处硬写 | `AppTheme.kt:88-94`；`AppDimens.kt` 无 spacing scale；`AppDimens.*` 仅 58 次引用 vs 378 处裸字面量 | **新建票**（design tokens 二期）：`AppTypography` + `AppSpacing`（4/8/12/16/24），并把 `codeFont`/`codeLineNumbers` 接到 `CodeEditorView`（这两个偏好**存了但零消费**） |
| **P1** | **ExtendedColors 缺 8 个 spec 字段 + Checks 状态色错**：success/pending 用 `primary`；`AppStateColorRole` 无 WARNING | `ExtendedColors.kt:25-64`；`PullRequestTabContent.kt:631-648`；`AppStateChip.kt:55-62` | **并入 #168**（图标与无障碍三期已关，可开续票）或新票。改动局部（`checkRunTint` 4 分支 + `ExtendedColors` 补字段），**建议单独小票** |
| **P1** | **截图矩阵 7/10 维未覆盖 + RTL 假绿 + `:feature:issue` 被排除在 verify 外** | 覆盖证据见 §9.5 表；假绿 `AppTopBarRtlScreenshotTest.kt:20-27`；排除 `ci.yml:133-136` | **新建票**（测试矩阵二期）：先补 **OLED + 高对比 + zh**（无需新基建，改 `@Config`/组合参数即可）；RTL 需先解决 `@Config(qualifiers=…)` 不生效（改用 `createConfigurationContext` 或 Robolectric 4.16 的 `RuntimeEnvironment.setQualifiers`） |
| **P1** | **§11.4 四条 i18n lint 规则未启用，且 feature 模块不跑 lint** | `app/build.gradle.kts:29-67`；`ci.yml:79`（仅 `:app:lintDebug`）；`nightly.yml:59`（+`:core:ui`） | **新建票**（小）：加 `lint { enable += setOf("MissingTranslation","HardcodedText","SetTextI18n","StringFormatInvalid"); warningsAsErrors += ... }` 到 convention plugin；CI 加 `./gradlew lintDebug`（全模块） |
| **P2** | **4 类链接落到浏览器**：`ParsedUrl.Tree` / `Release` / `Search` / 无 context 的 `IssueRef` → `fromParsedUrl` 返回 null；`Discussion` 挂占位屏 | `AppRoute.kt:218-222`；`AppNavHost.kt:326-330` | **并入 #164（仓库域）**：`Tree` 已有 `Blob` 路由可复用（差一个 `path` 语义）；`Release` 有 `GET /releases/tags/{tag}` 端点，可落到 Releases Tab 并高亮 |
| **P2** | **代码编辑器缺软换行切换与查找替换；CRLF/编码未处理** | `CodeEditorView.kt:76`（`isWordwrap = false` 硬编码）；`CodeEditorController.startSearch()` 无 replace；`feature/repo` grep CRLF/encoding 0 命中 | **并入 #166**（UI14 代码浏览形态确认时一并拍板）或新票 |
| **P2** | **缓存 3 处缺**：PR 文件列表未按 head sha 缓存、文件内容未按 `branch+path+sha` 缓存、**草稿无持久化**（进程被杀即丢） | §3.6 表；`MarkdownEditorViewModel.kt:29-30`（仅内存 `MutableStateFlow`） | **新建票**（草稿持久化价值最高，与 §7.4「本地草稿保留」的风险对策直接对应 `plan.md:955`） |
| **P2** | **WebView 预热缺失 + 图片无懒加载 + 服务端 HTML 路径代码块未高亮 + 复制按钮触屏不可见** | `grep warm/preload` 0；`loading="lazy"` 0；`renderer.js:315`（仅离线路径调 `highlightCodeBlocks`）；`renderer.js:97-101`（`opacity:0` + hover） | **并入 #26（T25 性能）**：4 条都是小改动，收益明确（README 秒开、长文档省内存） |
| **P2** | **§14.3 macrobenchmark 未接入**，`baseline-prof.txt` 为手写 | 全仓无 `androidx.benchmark`；`baseline-prof.txt:7-11` 自述 | 保持挂账（与 #26 一致，需真机/模拟器） |
| **P3** | **`:maestro/` 5 个 flow 死配置；`prototype/` 不在 detekt 与任何截图门禁** | `.maestro/screenshots/*.yaml` 5 文件无 workflow 执行；`build.gradle.kts:53`；`ci.yml:145-153` | **杂项清理**：删除 `.maestro/`（已由 adb 版 `screenshots.sh` 取代）或接线；`prototype/` 纳入 detekt 或标记为归档 |
| **P3** | **`@mention` 补全永不产生候选**（`mentions` 默认空且无调用点传值）；「引用自动补全」未实现 | `MarkdownComposer.kt:72`；grep `mentions =` 仅定义处 + 测试 | **并入 P0 的渲染补全票**：`IssueDetailScreen.kt:1171` 调用点传入仓库协作者列表（数据可从 `GET /repos/{o}/{r}/assignees` 或 GraphQL `repository.collaborators` 取） |
| **P3** | **`contrastLevel`/`styleVariant` 不存在；`codeFont`/`codeLineNumbers` 存而不用** | §4.1 逐字段表 | 与 P1 的 token 票合并；若 `styleVariant` 有意不做，须回写 plan.md |
| **P3** | **plan.md 未回写的研究结论**：Shiki（实现用 highlight.js）、prism4j 150+、Highlights 18 语言、Material Color Utilities、androidx.benchmark、`MarkdownRenderer` 抽象接口、`AppThemePreferences` | `plan.md:65,159,261,260,319,468,916,452-465,98-120` | **文档票**：把这些偏离逐条回写 plan.md（或标注「已由 ADR-0007/0004 覆盖」），否则 plan.md 会持续误导后续 agent |

---

## 11. 文档漂移清单

> 说明：`docs/agents/project-status.md` 在本快照（2026-09-11）**已是较新且大体诚实**的版本（它明确列了 §3.1 挂账 7 项、§3.2「本机无法验证」5 项、§4 遗留 8 项）。用户 prompt 里「自述严重滞后」的判断对应的是更早的快照。以下为本次实测的残留漂移。

| # | 文档声称 | 代码现实 | 位置 |
|---|---|---|---|
| D1 | `AGENTS.md`「当前状态（2026-09-06）：`main@2b7071d`」 | 实际 HEAD = `a48ede1`（2026-09-11）；差 5 天多张票 | `AGENTS.md` 顶部状态段 vs `git log -1` |
| D2 | `AGENTS.md`「剩余 11 票见 project-status.md」/ project-status 的 9 票清单 | 9 票（#163–#170 + #26）中 **#166/#167 标 🔶 关闭**、#26 标 🔶 部分 —— 与 AGENTS「剩余 11 票」口径不一致 | `AGENTS.md` 状态段 vs `docs/agents/project-status.md:59-72` |
| D3 | `plan.md §2.4` 规定 `interface MarkdownRenderer` + `MarkdownContent` + `RenderContext` + `SourceType`，并要求由 `MarkdownFeatureDetector` 判定路由 | **全部 0 命中**；`FeatureDetector` 生产 0 引用（ADR-0007 让它「保留但不用」，实际是死代码） | `plan.md:98-122` vs `core/markdown/.../webview/FeatureDetector.kt` 全仓引用 |
| D4 | `plan.md §4.4`「GraphQL 读优先」+ `IssueDetail` 典型 Query（含 `bodyHTML`、`timelineItems` 游标） | 读路径全 REST（8 个 `.graphql` 文档，无 `IssueDetail.graphql`，`bodyHTML` 0 命中） | `plan.md:369-408` vs `core/github-graphql/src/main/graphql/` |
| D5 | `plan.md §5.2` `AppThemePreferences` 11 字段 + Material Color Utilities 生成全 tonal palette | 类不存在（用的是扁平 `UserPreferencesRepository` 接口）；MCU 0 命中，seed 只设 `primary` | `plan.md:452-468` vs `core/datastore/.../UserPreferencesRepository.kt:12`、`ThemeColors.kt:450-456` |
| D6 | `plan.md §5.3` `ExtendedColors` 14 字段（success/warning/info/merged/draft） | 缺 8 个，实际实现的是 Alert 卡片语义（note/tip/important/caution）+ brand/danger | `plan.md:484-493` vs `core/designsystem/.../ExtendedColors.kt:25-64` |
| D7 | `plan.md §5.4`「字体：`AppTypography`（字阶、代码等宽追加）」 | `AppTypography` 全仓 0 命中；`MaterialTheme` 未传 `typography` | `plan.md:499` vs `core/designsystem/theme/AppTheme.kt:88-94` |
| D8 | `docs/ui-design.md §7.1`（:167）设置页明列「代码字体、行号开关」 | `AppearanceSettingsSection.kt:67-68` 注释自认「代码字体/行号仍隐藏」；`strings.xml:45-48` 的 key 无任何 composable 引用 | `docs/ui-design.md:167` vs `feature/settings/.../AppearanceSettingsSection.kt:67-68` |
| D9 | **两份 spec 冲突**：`docs/ui-design.md §7.1` 的 6 套主题（Tonal Spot / Neutral / Vibrant / Expressive / GitHub Classic #0969DA / Midnight）vs `docs/adr/0004` 的 6 套（Light/Dark/OLED/DynamicLight/DynamicDark/HighContrast） | 代码实现的是 **ADR-0004 那套**；ui-design 那 6 个名字 **0 命中**（"Neutral" 2 命中均为配色注释词） | `docs/ui-design.md:167` 附近 vs `core/datastore/.../ThemeMode.kt:9-30`；ADR-0006 声明「替代 ADR-0004 的清单」但只覆盖玻璃部分 |
| D10 | `plan.md §2.6/§2.12`「Shiki（TextMate 语法、按语言懒加载）」 | 用 **highlight.js**（整包加载）；Shiki 在 `.kt/.js/.json` 中 **0 命中**（仅 docs/plan 提及） | `plan.md:159,261` vs `core/markdown/src/main/assets/webview/highlight.min.js`、`renderer.js:127-137` |
| D11 | `plan.md §2.12`「Highlights 18 语言 → v2 prism4j 150+ 语言」 | 原生代码块 **7 种** TextMate；prism4j 0 命中 | `plan.md:260,953` vs `core/markdown/.../TextMateCodeBlock.kt:28-38` |
| D12 | `plan.md §12.5`「矩阵：Light/Dark/OLED/Dynamic color mock/高对比；en/zh/ar-RTL；大字；各预设主题」 | 实测覆盖 Light/Dark × en（+1 张顶栏 RTL）；另 7 维未覆盖 | `plan.md:836` vs `*/src/test/screenshots/` 目录清点 |
| D13 | `plan.md §12.5`「产物 `build/outputs/roborazzi/*.png` + diff 图 —— Linux 本机即可预览」 | 该路径在 docs 中无落地配方；CI 只在 failure 上传产物；无 HTML 报告配置 | `plan.md:838` vs `ci.yml:155-163`；grep `build/outputs/roborazzi` 仅命中 plan.md 与调研稿 |
| D14 | `plan.md §14.3`「androidx.benchmark 生成：启动路径、首页滚动、Issue 详情、README 渲染、主题切换」 | `androidx.benchmark` / macrobenchmark / baselineprofile 插件全仓 ABSENT；`baseline-prof.txt` 为手写 | `plan.md:914-917` vs `baseline-prof.txt:7-11` 自述 + grep |
| D15 | `docs/agents/project-status.md:117`「diffCoverage 已转**硬门禁**」 | 同一文件 `:153` 仍写「diff coverage 仍为**软门禁**」 | `docs/agents/project-status.md:117` vs `:153`（自相矛盾） |
| D16 | `app/build.gradle.kts:104` 注释「MainActivity 用 `AppCompatDelegate.setApplicationLocales`」 | 实际是 `attachBaseContext` + `createConfigurationContext` + `Locale.setDefault` | `app/build.gradle.kts:104` vs `app/src/main/java/.../MainActivity.kt:120-121,495-502` |
| D17 | `AGENTS.md`「Validation 命令含 `coverageVerify`」等清单完整 ✅，但**未提示** `:feature:issue` 截图 verify 被排除在 CI 之外 | `ci.yml:133-136` 注释说明了排除原因（无限动画挂死），AGENTS 未同步 | `AGENTS.md` 验证命令段 vs `ci.yml:133-153` |
| D18 | `AGENTS.md` / `docs/adr/0004 §3`「Material Symbols 变量字体（wght=300）评估」 | 代码中无落地证据（无 `dev.vicart` 依赖、无字体资产）；`project-status.md:141` 声称「评估完成（PR #185）」 | `docs/agents/project-status.md:141` vs `gradle/libs.versions.toml`、`core/designsystem/src/main/assets`（无字体） |
| D19 | `plan.md §4.5`「Reviews：`GET/POST /pulls/{n}/reviews`」 | GET 走 GraphQL `PullRequestReviewThreads.graphql`（能力等价，通道不同） | `plan.md:419` vs `core/github-graphql/src/main/graphql/PullRequestReviewThreads.graphql` |
| D20 | `plan.md §7.3` Mutation 示例 `mergePullRequest`（GraphQL） | 实现为 REST `PUT /pulls/{n}/merge`（符合 §4.3「REST 写优先」，但 §7.3 示例未回写） | `plan.md:620-626` vs `core/github-rest/.../PullRequestApi.kt:204` |

---

## 附：审计方法与可复现命令

```bash
# 基准
cd /home/zhiyi/dev/AppDev/.worktrees/audit-spec
git log -1 --format='%H %ci %s'      # a48ede1ab7336134c47162c2690a62470a217d7e 2026-09-11 20:55:10 +0800

# §2 Markdown：资产清单 + 插件清单 + 能力 grep
find core/markdown/src/main/assets -type f | sort
grep -n "md.use(\|makeHtml\|highlightElement" core/markdown/src/main/assets/webview/renderer.js
grep -rni "mermaid\|katex\|mathjax\|gemoji\|shortcode" --include=*.kt --include=*.js core | grep -v /build/

# §4 数据层：端点全量 + GraphQL 文档 + PAT 降级
grep -rn '@\(GET\|POST\|PUT\|DELETE\|PATCH\|HTTP\)(' --include=*.kt core/github-rest/src/main
ls core/github-graphql/src/main/graphql/
grep -rn "isRestOnly" --include=*.kt core feature app | grep -v Test

# §5 设计系统：组件存在性（双 pattern，避免误报）
grep -rnE "fun (AppScaffold|AppCard|AppChip|AppDialog|AppSnackbar|AppBottomSheet|AppPullToRefresh|AppListItem|AppNavigationRail|AppAvatarRow)[ <(]" \
  core/*/src/main feature/*/src/main app/src/main --include=*.kt
grep -rnw "AppTypography\|AppThemePreferences\|ThemeVariant\|CorePalette" core feature app --include=*.kt

# §9 架构/i18n：Konsist 规则 + 文案硬编码 + lint 规则
grep -n "func\|assert" app/src/test/kotlin/com/yumiru11/githubapp/konsist/ArchitectureTest.kt
grep -rnE '(Text|contentDescription|label|title|placeholder)\s*=\s*"[^"]' --include=*.kt feature core app | grep -v /build/
grep -rn "MissingTranslation\|HardcodedText\|SetTextI18n\|StringFormatInvalid" --include=*.kts --include=*.xml .

# §12.5 截图矩阵
find . -name '*.png' -path '*screenshots*' -not -path '*/build/*' | sed 's|/[^/]*$||' | sort | uniq -c
```

**审计完整性声明**：本报告覆盖 `plan.md` §2–§14 与 `request.txt` 全部 24 句要求，共 106 条判定 + 20 条文档漂移。**未覆盖/未逐像素核验项**（已在正文标注）：① §2.8 引用块与表格的具体色值/边框宽未逐值比对；② SVG 渲染器（`coil-svg`/`SvgDecoder`）注册未逐项验证；③ `docs/ui-design.md` 455 行的视觉细节（毛玻璃允许清单逐条、信息架构层级）未逐条核验，仅核验 plan §5 与 ADR-0004/0006；④ 真机行为（OAuth 浏览器拉起、WebView 实际渲染观感、性能实测）无法在纯 JVM 环境验证 —— 与 `project-status.md §3.2` 的挂账一致。
