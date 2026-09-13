# Android GitHub 客户端 —— 技术规划

> **核心结论先说：**
> **Kotlin + Jetpack Compose + Material 3 构建全原生 UI；GitHub 数据层 GraphQL 读优先、REST 写优先；Markdown 采用「WebView 主渲染 + Compose 原生短文本」的分层架构**——README、Issue/PR 正文走 WebView 高保真通道（GitHub 服务端渲染 HTML 优先 → 离线 markdown-it GFM 降级），注入同一套 Material You CSS 令牌，并统一拦截 GitHub 链接跳转应用内页面；评论列表、通知、行内评论等短文本保持 Compose 原生渲染，保证列表流畅与 Material You 深度融入。
> **（2026-09-11 回写：原计划为「Compose 原生渲染为主 + WebView 高保真兜底」，2026-08-19 Task B 经 ADR-0007 拍板反转为 WebView 主渲染；反转原因见该 ADR「背景」——原生增强链在 4 轮真机问题中持续暴露引擎级短板。§2.2 / §2.5 已同步改写，详见文末「偏离回写总表」。）**
> **（2026-09-12 回写：数据层的「GraphQL 读优先」**实际未成立**——Issue/PR 详情、timeline、通知、搜索等读路径全走 REST，GraphQL 仅覆盖 Viewer / RepositoryOverview / ViewerRepositories / ReviewThreads 等少数读位（§4.4）。决策与回归触发条件见 `docs/adr/0009-graphql-read-path-deviation.md`。）**
> 代码文件浏览与编辑交给专业代码编辑器（Rosemoe Sora Editor）满足"准确语法高亮"；写功能（评论、编辑、审查、合并、文件提交、分支管理）通过 GraphQL mutation + REST 完成闭环。项目采用多模块架构、Hilt、Room、Paging 3，测试与截图回归全部跑在 Linux 纯 JVM（Robolectric + Roborazzi），不依赖虚拟机、不依赖 Waydroid、不引入 Kotlin Multiplatform。

---

## 0. 偏离回写总表（2026-09-11）

> **为什么有这一节**：本文档是项目「技术规划权威」，被 `AGENTS.md` 列为必读。但规划写于动工之前，落地过程中有若干处方案被 ADR 拍板改掉、或在调研后换了更优解，而**正文长期停留在"计划用 X"的时态**——后续 agent 据此开工才发现 X 不成立（2026-09-11 调研 agent 即因此走弯路）。
> **读法**：下文正文中凡带 `（2026-09-11 回写：…）` / `（2026-09-12 回写：…）` 的段落，均以回写后的**现决策**为准；被划为历史记录的原计划**保留不删**，以保留「原计划 → 现决策 → 原因」的演进痕迹。
> **证据来源**：`docs/agents/spec-audit-2026-09-11.md` §10 P3 + §11 文档漂移清单，逐条以 grep/read 对当前 HEAD 复核。
> **状态图例**：✅ 已按现决策落地 ｜ 🔁 方案已被替代（正文已改写）｜ ⏳ 仍未实现（保留计划，标注待做）

| # | plan 原计划 | 代码现实（证据） | 状态 | 处理 | 详见 |
|---|---|---|---|---|---|
| 1 | `MarkdownRenderer` 抽象接口 + `MarkdownContent`/`RenderContext`/`SourceType` + `FeatureDetector` 路由（§2.4） | 三者全仓 **0 命中**；实际是两个独立 Composable 入口 `WebViewMarkdownRenderer`（`core/markdown/.../webview/WebViewMarkdownRenderer.kt:62`）与 `MarkdownViewer`（`MarkdownViewer.kt:55`），调用点直接选用，无接口层；`FeatureDetector` 仅剩自身定义 + 单测（生产 0 引用） | 🔁 | 改写为「按调用点直选渲染器」的现架构，抽象接口降级为未采用的提案 | §2.4 |
| 2 | 兜底 WebView 用 **Shiki**（TextMate 语法、按语言懒加载）（§2.2/§2.6/§2.12/§17） | `core/markdown/src/main/assets/webview/highlight.min.js` = **highlight.js v11.11.1 整包**；`renderer.js:141` 调 `hljs.highlightElement`；Shiki 在 `.kt/.js/.json/.toml` 中 **0 命中**（仅一处测试注释提及名号） | 🔁 | 改写为 highlight.js（整包加载，非懒加载）；starry-night 曾评估、未采用（见 §2.12） | §2.2 / §2.6 / §2.12 |
| 3 | 原生代码块 **Highlights 18 语言** → v2 接 **prism4j 150+**（§2.5/§2.12/§16/§17） | **prism4j 全仓 0 命中**；实际用 **KotlinTextMate 0.2.0 TextMate 语法，打包 7 个 grammar**（`core/markdown/src/main/assets/grammars/`：go/java/json/kotlin/python/shell/yaml；`TextMateCodeBlock.kt:28,63`） | 🔁 | 改写为 TextMate 7 语言；扩展路径按调研结论改为「加 JSON 资产」而非接 prism4j | §2.12 / §16 |
| 4 | 主题生成用 **Material Color Utilities**（seed → 全 tonal palette）（§5.2） | MCU 直接依赖 **0 命中**；动态色走 compose-material3 的 `dynamicLight/DarkColorScheme`（`ThemeColors.kt:297,314`），seed 色**只设 `primary`**（`ThemeColors.kt:499`：`lightColorScheme(primary = seed)`）；`styleVariant`/`contrastLevel` 不存在 | ⏳ + 🔁 | 标注未实现（保留计划）；同时说明 seed 半成品现状与 ADR-0004 六套主题的关系 | §5.2 |
| 5 | 主题模型 `AppThemePreferences`（11 字段）（§5.2） | `AppThemePreferences` **0 命中**；实际是扁平 `UserPreferencesRepository` 接口（`core/datastore/.../preferences/UserPreferencesRepository.kt`）+ ADR-0004 的六套 `ThemeMode` | 🔁 | 改写为实际模型，原 data class 保留作设计意图记录 | §5.2 |
| 6 | **androidx.benchmark** 生成 Baseline Profile（§14.3） | `androidx.benchmark`/`macrobenchmark`/`baselineprofile` 全仓 **0 命中**；`app/src/main/baseline-prof.txt` 为**手写**（该文件头 7-11 行自述原因：macrobenchmark 需真机/模拟器，与本仓「全 JVM」红线冲突）；`ProfileInstaller` 已接线 | ⏳ | 标注未实现 + 保留计划；写明手写产物是当前交付物、接入后应替换 | §14.3 |
| 7 | 风险对策「**原生优先** + 服务端 HTML 兜底 + 快照回归集」（§16） | ADR-0007 后已反转为 WebView 主渲染；「快照回归集」**部分落地**：#232 起 `MarkdownFixtureScreenshotTest` **31 张 fixture 基线** + `:core:markdown:verifyRoborazziDebug` 已进门禁（`ci.yml:149`）；但 WebView 通道仍无产物级回归（Robolectric 无法真渲染 WebView） | 🔁 + ✅/⏳ | 「原生优先」改写为 WebView 主渲染；快照回归集标注「原生 ✅ 31 张 / WebView 路径 ⏳」 | §16 / §12.5 |
| 8 | `MarkdownRenderer`/`MarkdownFeatureDetector`（§2.6/§2.12 单元测试项） | 同上第 1 项，抽象层不存在 | 🔁 | 改写为 `FeatureDetector`（现存、纯函数） | §12.2 |
| 9 | §12.4「测试全 Linux JVM 免模拟器」 | 单测/截图 ✅ 成立；但 **WebView 内容在 Linux JVM 无法真实渲染**，README/正文渲染的验证实际依赖 CI KVM 模拟器（`ci.yml:285-346`，`android-emulator-runner` api 30） | 🔁 | 补注例外边界（ADR-0007「代价」已承认此测试盲区） | §12.4 |
| 10 | §12.5 截图矩阵（Light/Dark/OLED/Dynamic/高对比 × en/zh/ar-RTL × 大字 × 预设主题） | 实测 66 张基线，覆盖 Light/Dark × en（+少量 RTL）；其余维度未覆盖。§12.5「产物 `build/outputs/roborazzi/*.png` 本机预览」亦无落地配方，CI 仅在 failure 上传 | ⏳ | 保留矩阵作目标，标注现状覆盖率与未落地项 | §12.5 |
| 11 | §14.1 README 首次渲染 < 800ms / 缓存命中 < 300ms、冷启动 < 1.5s | 三个性能目标**均无实测数据**（macrobenchmark 未接入，见第 6 项） | ⏳ | 保留目标，标注「尚无实测数据」 | §14.1 |
| 12 | §3.2 REST 补位端点表「Reviews：`GET/POST /pulls/{n}/reviews`」 | **读走 GraphQL** `PullRequestReviewThreads.graphql`（`PullRequestRepository.kt:439`）、**写走 REST** `POST /pulls/{n}/reviews`（`PullRequestApi.kt:190`）——能力等价，通道一分为二 | 🔁 | 补注双通道现实（`PullRequestDto.kt:131` 的 `GET /reviews` DTO 注释为遗留说明） | §3.2 / §4.5 |
| 13 | §7.3 Merge 示例给的是 GraphQL `mergePullRequest` mutation | 实现为 **REST `PUT /pulls/{n}/merge`**（`PullRequestApi.kt:204`），符合 §1.2「REST 写优先」但示例未回写 | 🔁 | 保留 GraphQL 示例作规划意图，补注实际走 REST | §7.3 |
| 14 | §3.1/§17 写 `multiplatform-markdown-renderer 0.43.0` | 版本目录实为 **0.38.1**（`gradle/libs.versions.toml:48`），且另引入 **KotlinTextMate 0.2.0** 承担代码块高亮（`textmate = "0.2.0"`） | 🔁 | 版本号统一改为 0.38.1，补记 KotlinTextMate | §3.1 / §17 |
| 15 | §17 最终清单「Markdown：mikepenz …（-m3、-code）」 | 实际四个制品：`markdown-renderer` + `-m3` + `-code` + **`-coil3`**（`libs.versions.toml:167-170`） | 🔁 | 补记 `-coil3` | §17 |
| 16 | §1.2/§3.2/§4.4「**GraphQL 读优先**」+ 典型 Query `IssueDetail`（`body`/`bodyHTML`/`timelineItems` 游标）（§1.2/§4.4） | **`IssueDetail.graphql` 不存在**；全仓 8 个 `.graphql`（Viewer / RepositoryOverview / ViewerRepositories / PullRequestReviewThreads / UpdateIssue / ResolveReviewThread / UnresolveReviewThread / IssueWriteContext）；Issue/PR 详情、timeline、通知、搜索读路径全 REST（`IssueRepository.kt:59`「读：列表分页流 + 详情 + 时间线（REST）」）；`bodyHTML` 仅出现在 schema 定义与文档 | 🔁 | 修订为「REST 读为主 + GraphQL 少数读位」；**新增 ADR-0009 记录原意/现实/原因/回归触发条件** | ADR-0009 / §4.4 |

**已核实为「无需回写」的审计项**（避免后人重复劳动）：

- **`AppTypography`**（审计 §11 D7 记为「全仓 0 命中」）——**已过时**：`165ad76` 已落地 `core/designsystem/.../theme/AppTypography.kt`（10.9KB，M3 baseline 15 档 + markdown 标题档）并接线 `MaterialTheme(typography = AppTypography.from())`（`AppTheme.kt:114`）。§5.4 现已属实。
- **`ExtendedColors`**（审计 §11 D6 记为「缺 8 个字段」）——字段名与语义**有意不同**：实现为 Alert 卡片语义（note/tip/important/warning/caution 各 2 字段）+ brand/danger/success 族，共 19 字段（`core/designsystem/.../ExtendedColors.kt`），而非计划里的 `info`/`merged`/`draft`。§5.3 已在其上方标注实际语义。

---

## 1. 总体目标与设计原则

### 1.1 产品目标

要构建一个：

- **功能全面**：仓库、文件树、README、Issue、PR、Review、行内评论、通知、搜索、代码浏览、代码编辑、仓库管理、分支、Release、主题、国际化。
- **轻量流畅**：启动快（冷启动 < 1.5s，中端机）、列表滚动稳定 60fps、README 秒开（缓存命中 < 300ms）、APK 体积有意图地控制。
- **美观统一**：全 Material You 审美——动态取色、预设主题、自定义主题、动效规范、图标体系统一。
- **Markdown 高保真**：README 与常用 GFM 写法的渲染结果对齐 GitHub 网页端。
- **可写能力完整**：评论、编辑 Issue/PR、Review、行内评论、Merge、文件编辑提交、分支管理。
- **工程化成熟**：多模块、单向数据流、完整测试金字塔、GitHub Actions CI/CD、i18n、无障碍、安全基线。

### 1.2 技术原则

1. **UI 全 Compose + Material 3**：页面结构、列表、导航、主题、动画全 Compose 化；Material You 作为设计系统而非套壳。
2. **Markdown 渲染分层**：按内容类型、复杂度、交互需求选择渲染器，不搞一刀切；不为边缘特性（math/mermaid/重型 HTML）拖累主路径性能。
3. **GraphQL 读优先、REST 写优先**：结构化读取走 GraphQL（类型安全 + 一次拿全）；写操作、文件内容、搜索、Markdown 服务端渲染走 REST。
4. **统一路由是基础设施**：所有渲染器（原生与 WebView）产生的链接都进入同一个 GitHub 链接解析器（GitHubLinkParser）→ 应用内导航或 Custom Tabs 兜底。
5. **主题令牌穿透两套渲染**：Compose 生成 Material You 颜色令牌（含扩展语义色），WebView 兜底通道通过 CSS Variables 使用同一套令牌，保证观感一致。
6. **可写功能与只读功能分层**：先保只读浏览体验，写功能通过 Repository 统一封装，业务逻辑与 API 细节解耦。
7. **编码质量约束**：版本目录（version catalog）、设计令牌、字符串资源、Konst 架构测试——从第一行代码开始落实，不事后补课。
8. **红线约束**：不引入 Kotlin Multiplatform；测试/预览全 JVM（Robolectric/Roborazzi）；不在评论列表中大量使用 WebView；不往 WebView 注入 token。

---

## 2. Markdown 渲染方案调研与决策

### 2.1 候选方案对比

| 方案 | 优点 | 缺点 | 适合场景 |
|---|---|---|---|
| 纯 Compose 自建 Markdown 渲染 | Material You 最统一，交互最原生 | GFM 兼容性工作量巨大；HTML、表格、任务列表、嵌套等很难完整支持 | 不推荐为主方案 |
| **Compose 第三方库（multiplatform-markdown-renderer）** | Compose 原生、M3 配色模块、GFM 表格/删除线/任务列表、可点击链接、语法高亮、懒加载、异步解析；社区活跃（规划时标注 v0.43.0，**实际锁定 0.38.1**，见 §3.1） | 对极少数高级内容（math/mermaid/重型 HTML）支持不足 | ✅ **主渲染器** |
| Markwon + TextView | 成熟稳定，GFM 扩展全面，prism4j 高亮 150+ 语言 | 需 AndroidView 桥接，与 Compose 动效/主题割裂 | 不选（能力已被主渲染器覆盖） |
| WebView + 本地 JS 渲染（markdown-it 等） | 可高度还原 GFM，支持 KaTeX/Mermaid | 性能/内存开销，与 Compose 滚动嵌套复杂 | 仅作**兜底通道** |
| GitHub 服务端渲染 HTML + WebView | 最接近网页端；mention、issue 引用、相对链接完整 | 依赖 API/网络，需 sanitize、缓存、样式注入 | **兜底通道首选数据源** |

### 2.2 实际架构：WebView 主渲染 + Compose 原生短文本（分层渲染）

> **（2026-09-11 回写：本节原题为「推荐架构：原生优先 + WebView 兜底（混合渲染）」。2026-08-19 Task B 经 ADR-0007 拍板**反转为 WebView 主渲染**——README 与 Issue/PR 正文一律 WebView，评论列表/通知等短文本保持原生。下方管线图已按现决策改写；原「原生优先」方案作为历史记录保留在本节末尾。）**

```text
原始 Markdown
  │
  ├─① 预处理层（按需）：相对链接重写 / 特性探测降级
  │    · README：服务端 HTML 优先（getReadmeHtml 三级降级 + 双 key 缓存）
  │    · Issue/PR 正文：无服务端 HTML API → 离线 GFM（WebViewHtmlBuilder.build(raw)）
  │
  ├─② 主路径（正文类内容）：WebViewMarkdownRenderer
  │    · 服务端 HTML 或离线 markdown-it 产物 → DOMPurify 权威清洗
  │    · Material You 融合：Kotlin 预计算混色变量（真机 WebView 不支持 CSS color-mix）
  │    · 交互经白名单 JS bridge：链接 / 复制 / 图片 / checkbox / 高度 / 滚动
  │    · 链接统一交给 GitHubLinkParser
  │
  └─③ 短文本路径：MarkdownViewer（mikepenz renderer 0.38.1 + KotlinTextMate）
       · 评论列表、通知、行内评论 —— 一律原生，绝不用 WebView
       · 注解器扩展：@user span、#n 引用、[!NOTE] 提醒块、锚点、代码语言徽标
       · 任务列表可交互 checkbox
       · 链接统一交给 GitHubLinkParser
```

**两套实现的真实边界**（与下方 §2.4 的关系）：

| 通道 | 入口（无抽象接口，调用点直选） | 服务的内容 | 调用点 |
|---|---|---|---|
| WebView（主） | `WebViewMarkdownRenderer`（`@Composable`） | README、Issue 正文、PR 正文、编辑器预览 | `RepoDetailScreen.kt:1748`、`IssueDetailScreen.kt:505`、`PullRequestTabContent.kt:167`、`MarkdownEditorScreen.kt:132` |
| 原生（短文本） | `MarkdownViewer` / `EnhancedMarkdownViewer` | 评论、timeline 条目、行内评论、仓库内 md 文件预览 | `PullRequestTimelineItems.kt:67/96/138`、`LineCommentSheet.kt:182`、`IssueDetailScreen.kt:871/1221` |

> **历史记录（原计划，已被 ADR-0007 取代）**：原 §2.2 规划「主路径 = `ComposeMarkdownRenderer`（mikepenz renderer）+ 兜底路径 = `WebViewMarkdownRenderer`（按需启用，绝不默认）」，并规定 README 走「Compose 原生为主；复杂内容 → WebView 服务端 HTML 通道」。**反转原因**：原生增强链在 4 轮真机问题中持续暴露引擎级短板（排版细节、复杂度、跨版本维护），而 WebView 通道基于 github-markdown-css 官方方案渲染稳定、兼容面广——完整理由与代价见 `docs/adr/0007-markdown-native-primary-webview-fallback.md`。
> **未随反转改变的红线**：评论列表绝不用 WebView；token 绝不注入 WebView；私有图片仅经 `shouldInterceptRequest` 白名单加 Authorization。

> **原计划管线图（2026-09-11 保留作历史记录，勿据此实现）**：以下为 Task B 反转前的规划原文，其「主路径 = ComposeMarkdownRenderer / 兜底 = WebView（绝不默认）」的极性已与当前实现相反，仅保留以说明演进：
>
> ```text
> 原始 Markdown
>   │
>   ├─① 预处理层 GhMarkdownProcessor
>   │    · emoji 短码 → Unicode（gemoji 子集映射表，数据放 assets 不硬编码）
>   │    · 相对链接 → 按仓库上下文重写（./docs → 默认分支路径）
>   │    · 特性探测：mermaid 围栏 / $math$ / 重型 HTML / 超大文档 → 标记 fallback
>   │
>   ├─② 主路径：ComposeMarkdownRenderer（mikepenz renderer）
>   │    · 注解器（annotator）扩展：@user 彩色 span、#n 引用、[!NOTE] 提醒块、锚点、代码语言徽标
>   │    · 任务列表可交互 checkbox
>   │    · 链接统一交给 GitHubLinkParser
>   │
>   └─③ 兜底路径：WebViewMarkdownRenderer（按需启用，绝不默认）
>        ├─ 数据源优先级：
>        │    GitHub 服务端 HTML（/readme HTML / POST /markdown gfm+context）
>        │    → 本地 markdown-it + Shiki/KaTeX/Mermaid（assets 打包，离线可用）
>        ├─ CSS：注入 Material You 令牌（见 2.9）
>        ├─ 私有图片代理（shouldInterceptRequest 白名单 + Authorization）
>        └─ 链接统一交给 GitHubLinkParser
> ```
>
> 注：`GhMarkdownProcessor` 预处理层与 `ComposeMarkdownRenderer` 均**未落地**（全仓 0 命中）；相对链接重写随 Task B 一并移除（WebView 侧由服务端 HTML / markdown-it 自带处理）。「本地 markdown-it + Shiki」中的 Shiki 实际为 highlight.js，见 §2.12。

### 2.3 渲染目标清单（GFM 全覆盖）

必须支持以下特性，与网页端一致：

- 标题、段落、引用块（blockquote）
- 加粗、斜体、删除线
- 表格（含单元格内联内容、对齐）
- 有序/无序列表、嵌套列表
- 任务列表 `- [ ]` / `- [x]`
- 行内代码、围栏代码块、代码语言标注、语法高亮、代码块复制按钮
- 图片（含相对路径引用、GitHub 缓存域）
- 外部链接、自动链接（裸 URL）
- 相对链接（`./`、`../`、`/owner/repo`）
- 提及：`@user`、`@org/team`
- 引用：`#123`、`owner/repo#123`、`gh-123`
- 提交引用（裸 sha）
- Emoji 短句：`:rocket:`
- GitHub Alerts：`[!NOTE]`、`[!TIP]`、`[!IMPORTANT]`、`[!WARNING]`、`[!CAUTION]`
- 锚点跳转（`#section`）
- 图片：懒加载、点击放大、GIF
- 内嵌 HTML（安全子集）
- Math/KaTeX（兜底通道，可选）
- Mermaid（兜底通道，可选）
- 脚注（尽力而为，不保证与网页完全一致）

### 2.4 渲染器抽象接口（原计划，未采用）

> **（2026-09-11 回写：本节描述的统一抽象层 **从未落地**，全仓 0 命中——`MarkdownRenderer` / `MarkdownContent` / `RenderContext` / `SourceType` 四个符号在 `*.kt` 中均无定义（仅存在同名 **Composable 函数** `WebViewMarkdownRenderer`，是入口而非接口实现）。
> **实际架构**：不做统一接口，由调用点按内容类型直接选用两个 Composable 入口（见 §2.2 边界表）：
> - 正文类 → `WebViewMarkdownRenderer(sanitizedHtml, tokenProvider, bridgeCallback, modifier, httpClient, renderMode, baseRepoUrl, fillAvailableHeight, onScrollChanged)`，其中 `renderMode: RenderMode`（`SERVER_HTML` / `OFFLINE_MARKDOWN_IT`）取代了原 `SourceType` 的枚举职责；
> - 短文本 → `MarkdownViewer(markdown, onInternalLink, baseRepoUrl, modifier, scrollable)`。
> **为什么没做**：ADR-0007 反转为 WebView 主渲染后，两通道的内容边界变成「正文 vs 短文本」这样一条调用点一眼可判的线，抽象接口只剩转发价值；而接口要求的 `Flow<RenderResult>` 进度模型（进度→完成→失败降级）与两个通道的实际控制流（WebView 加载回调 / Compose 同步渲染）都不吻合。**若将来第三条渲染通道出现，再引入抽象层仍是合理选项。**
> **`MarkdownFeatureDetector` 路由已废弃**：`FeatureDetector`（`core/markdown/.../webview/FeatureDetector.kt:50`，纯函数 `shouldFallback`）保留并有单测，但 **README 分流判定不再使用它**，生产代码 0 引用——正文一律走 WebView，无需探测。下方代码块保留作原设计意图记录。**

```kotlin
interface MarkdownRenderer {
    fun render(
        content: MarkdownContent,
        context: RenderContext,
    ): Flow<RenderResult>          // 进度 → 完成 → 失败可降级信号
}

data class MarkdownContent(
    val rawMarkdown: String?,      // 原始 md（编辑态必填）
    val serverHtml: String?,       // 服务端渲染结果（有则优先于本地渲染）
    val sourceType: SourceType,    // README / ISSUE_BODY / PR_BODY / COMMENT / PREVIEW / FILE
)

data class RenderContext(
    val owner: String?,
    val repo: String?,
    val ref: String?,
    val themeTokens: MarkdownThemeTokens,
    val canWrite: Boolean,
    val interactiveTaskList: Boolean,
)
```

> 注：`MarkdownThemeTokens` 是本节唯一**真实存在**的符号（`core/markdown/.../webview/MarkdownThemeTokens.kt:25`），WebView 通道的 Material You 令牌导出即由它承担（含 `toCssVariables()` / `versionHash()`）。

原计划：「两套实现：`ComposeMarkdownRenderer`（主）与 `WebViewMarkdownRenderer`（兜底），由 `MarkdownFeatureDetector` 判定路由（含"本地离线编辑预览"分支）」——**该路由与两个实现的极性均已由 ADR-0007 反转，见 §2.2**。

### 2.5 分内容类型渲染决策表

> **（2026-09-11 回写：下表已按 ADR-0007 的 WebView 主渲染现状改写。原表把 README/正文列为「Compose 原生」，与实现相反。）**

| 内容类型 | 渲染方式（现状） | 理由 |
|---|---|---|
| Issue/PR 评论、通知、行内评论等短文本 | **Compose 原生**（`MarkdownViewer`） | 列表滚动流畅、主题一致、无需 WebView（铁律：评论列表绝不用 WebView） |
| Issue/PR 正文 | **WebView**（离线 GFM：`WebViewHtmlBuilder.build(raw)`） | 无服务端 HTML API，由 markdown-it + 融合样式承担 |
| README | **WebView**：服务端 HTML（`getReadmeHtml` 三级降级 + 双 key 缓存）优先，异常降级离线 markdown-it（renderMode 仍 WEBVIEW） | 达到「网页端一致」最稳途径（ADR-0007 决策 1） |
| Markdown 编辑预览 | **WebView**，与展示共用主渲染管线（离线 GFM） | 避免编辑态与展示态不一致 |
| 代码文件浏览/编辑 | Sora Editor（TextMate） | 需要行号/搜索/准确高亮/编辑 |
| PR Diff | 自建 Compose 统一 diff 视图（v1 轻量版）+ 行评论 | 初期不强行完整自研，WebView diff 仅兜底 |
| 代码块语法高亮 | **原生短文本：KotlinTextMate（7 个 grammar 资产）**；**WebView 通道：highlight.js 11.11.1 整包**；文件级：Sora TextMate | 见 §2.12（原「Highlights 18 语言 → v2 prism4j 150+」已废弃） |

### 2.6 README 渲染策略（第一优先级）

1. **优先取 GitHub 服务端 HTML**：

   ```http
   GET /repos/{owner}/{repo}/readme
   Accept: application/vnd.github.html+json
   ```

   - 官方渲染：mention、issue 引用、相对链接、GFM 全部正确
   - 处理链：获取 HTML → DOMPurify 清洗 → 移除 script → 注入 Material You CSS 变量 → WebView 渲染 → 拦截所有链接

2. **次选 REST Markdown API**（编辑预览、无 HTML 可用时）：

   ```http
   POST /markdown
   Content-Type: application/json
   {"text": "…", "mode": "gfm", "context": "owner/repo"}
   ```

3. **最后本地兜底**（离线草稿预览、API 不可用时）：

   - markdown-it + markdown-it-task-lists/tables/anchor/emoji/footnote
   - **highlight.js 11.11.1（整包，非按语言懒加载）** ← 2026-09-11 回写：原计划为 Shiki（TextMate 语法、按语言懒加载，Web Worker 里跑）
   - KaTeX 0.18.7 与 Mermaid Tiny 11.17.2 **已接入**（2026-09-13）：均 post-sanitize 渲染、离线 assets、
     条件注入；Mermaid 带 Chromium ≥94 门禁（不满足回退为普通代码块），见 §2.3 目标清单中二者的「可选」定位
   - 全部 JS/CSS 打包进 assets，`WebViewAssetLoader` 提供，不从网络加载

### 2.7 Issue/PR 正文渲染策略

> **（2026-09-11 回写：原计划「GraphQL 取 `body` 与 `bodyHTML`，有 bodyHTML 优先原生 span 渲染」**未落地且方向已变**——`bodyHTML` 在 `.kt` / `.graphql` 中 **0 命中**，Issue/PR 详情读路径实为 REST；正文一律 **WebView 离线 GFM**（`IssueDetailScreen.kt:505`、`PullRequestTabContent.kt:167`），不做「探测到复杂内容再切换」的分流。）**

- **正文（现状）**：取原始 `body`（REST）→ `WebViewHtmlBuilder.build(raw)` 离线 GFM → `WebViewMarkdownRenderer`；编辑态用原始 `body`。
- 详情页：标题、状态、标签、作者、Assignees、Milestone、Reactions → Compose 原生；正文 → WebView（见上行）。
- Timeline 评论：**一律原生渲染**（数量多，禁止逐条 WebView）——此条不变，是铁律。

> 原计划（保留作历史记录）：「GraphQL 取 `body` 与 `bodyHTML`：有 bodyHTML 优先原生 span 渲染；无则 `body` + POST /markdown；编辑时用原始 `body`。详情页：…正文 → 本地渲染，探测到复杂内容切换高保真通道。」

### 2.8 评论渲染策略

- 全量 Compose 原生渲染，Material You 主题直接生效：

| 元素 | Material You 映射 |
|---|---|
| 正文文本 | `onSurface` |
| 链接 | `primary`（带下划线可选） |
| 引用块 | `surfaceContainerHigh` + 左侧 3dp `primary` 边框，圆角 |
| 行内代码 | `surfaceContainerLow` 背景 + 细圆角 |
| 代码块 | 圆角 surface、等宽字体、横向滚动、语言标签 + 复制按钮 |
| 表格 | `outlineVariant` 细边框、表头 `surfaceContainerHigh` |
| 任务列表 | M3 Checkbox 样式（只读或可点击反向 PR 状态） |

### 2.9 WebView 高保真渲染设计

- WebView 只作为"内容渲染容器"，不做应用导航。
- 模板结构：

```html
<!DOCTYPE html>
<html>
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <style id="theme-vars"></style>
  <link rel="stylesheet" href="markdown-you.css">
  <link rel="stylesheet" href="highlight-theme.css">
</head>
<body><div id="content" class="markdown-body"></div></body>
</html>
```

- Kotlin → JS：`setContent(html, options)`、`updateTheme(tokens)`、`scrollToAnchor(id)`
- JS → Kotlin（JSBridge）：`onLinkClick(url)`、`onCodeCopy(code)`、`onImageClick(src)`、`onCheckboxClick(index, checked)`、`onHeightChanged(h)`

### 2.10 Material You CSS 令牌注入

Compose 侧生成主题令牌，注入 `:root` 变量：

```css
:root {
  --md-sys-color-primary: …;
  --md-sys-color-on-surface: …;
  --md-sys-color-surface-container-low: …;
  --md-sys-color-surface-container-high: …;
  --md-sys-color-outline-variant: …;
  --md-sys-shape-corner-medium: 12px;
  --fontStack-sansSerif: …;
  --fontStack-monospace: …;
}
.markdown-body { background: transparent; color: var(--md-sys-color-on-surface); font-family: var(--fontStack-sansSerif); }
a { color: var(--md-sys-color-primary); }
pre { background: var(--md-sys-color-surface-container-low); border-radius: var(--md-sys-shape-corner-medium); padding: 12px; }
blockquote { background: var(--md-sys-color-surface-container-low); border-left: 3px solid var(--md-sys-color-primary); }
table td, table th { border: 1px solid var(--md-sys-color-outline-variant); }
```

- 深色/浅色/OLED/动态色切换时重新注入令牌（缓存按 token 版本双 key）。
- 不直接照搬 GitHub 蓝灰 CSS，基于 `github-markdown-css` 思路自维护 `markdown-you.css`。

> **（2026-09-11 回写：字体令牌**命名漂移**——原计划 `--md-sys-font-sans` / `--md-sys-font-mono`，实现注入的是 GitHub 命名 `--fontStack-sansSerif` / `--fontStack-monospace`（`MaterialYouFusionMapper.buildCss`；由 `WebViewMaterialYouTokenContractTest` 双向锁定）。上方 CSS 已按实现改写，其余 `--md-sys-*` 令牌名与实现一致。真机 WebView 不支持 CSS `color-mix`，混色必须 Kotlin 预计算。）**

### 2.11 链接跳转设计（GitHubLinkParser）

统一解析所有链接类型：

```kotlin
sealed interface GitHubLink {
    data class Repo(val owner: String, val name: String) : GitHubLink
    data class Issue(val owner: String, val repo: String, val number: Int) : GitHubLink
    data class PullRequest(val owner: String, val repo: String, val number: Int) : GitHubLink
    data class Commit(val owner: String, val repo: String, val sha: String) : GitHubLink
    data class Blob(val owner: String, val repo: String, val ref: String, val path: String) : GitHubLink
    data class Tree(val owner: String, val repo: String, val ref: String, val path: String) : GitHubLink
    data class Release(val owner: String, val repo: String, val tag: String?) : GitHubLink
    data class User(val login: String) : GitHubLink
    data class Discussion(val owner: String, val repo: String, val number: Int) : GitHubLink
    data class Search(val query: String) : GitHubLink
    data class External(val url: String) : GitHubLink
}
```

支持的输入形态：

- 绝对链接：`https://github.com/owner/repo(/issues/N|/pull/N|/blob/ref/path|/commit/sha|/releases…)`
- 相对链接：`/owner/repo/issues/123`、`issues/123`、`../blob/main/file`
- Markdown 内引用：`#123`、`owner/repo#123`、`@user`、裸 sha
- 路由行为：Repo→仓库页；Issue/PR→详情；Blob/Tree→文件浏览；Commit→提交页；Release/User/Org→对应页面；外部→Custom Tabs；未知→浏览器
- 同一解析器复用为 Android 深链接处理（外部 URL 打开 App 时同一条路由）。

### 2.12 语法高亮方案

> **（2026-09-11 回写：本节原计划的三层方案中，Highlights（18 语言）、prism4j（150+）、Shiki 三者**均未落地**。实际实现如下表——决策依据见 `docs/research/highlight-engine-analysis.md`（该调研显式比较了 KotlinTextMate / hljs 移植 / WebView starry-night 三方案，**结论：starry-night 与 hljs 移植均未采用**，保持 KotlinTextMate 并扩 grammar 资产，因为「瓶颈不在引擎而在资产」）。）**

| 场景 | 实际实现 | 语言覆盖 | 证据 |
|---|---|---|---|
| 原生通道代码块（评论等短文本） | **KotlinTextMate 0.2.0** TextMate 语法 + M3 派生主题 | **7 个 grammar 资产**：kotlin / python / go / java / json / yaml / shell；未覆盖语言走带样式兜底块（`FallbackCodeBlock`），不崩不错版 | `core/markdown/src/main/assets/grammars/`、`TextMateCodeBlock.kt:29`、`M3TextMateTheme.kt` |
| WebView 通道代码块（README/正文） | **highlight.js v11.11.1 整包** | 整包含的常见语言（非按语言懒加载） | `assets/webview/highlight.min.js`（127KB）、`renderer.js:141`（`hljs.highlightElement`）、`WebViewHtmlBuilder.kt:98` |
| 代码文件浏览/编辑 | Sora Editor TextMate 语法（VS Code 同款），支持行号、搜索、跳转行、wrap | 同 Sora 资产 | `core/editor` |
| PR Diff | 自研轻量 unified diff 渲染；复杂场景可 WebView + diff 库过渡 | — | `feature/pullrequest/.../DiffView*` |

**语言覆盖不足时的扩展路径（已调研拍板）**：从 VS Code 官方/社区 grammar 仓库（许可宽松）复制 JSON 语法资产进 `core/markdown/src/main/assets/grammars/`——每个语言约 0.5 天、**零 Kotlin 代码改动**（`rememberTextMateGrammar` 查 `GRAMMAR_FILES` 映射即可）。这比「接 prism4j」或「移植 highlight.js 引擎」（调研估 30–45 人天）便宜一个数量级。

> **原计划（保留作历史记录）**：「README 代码块：原生通道用 Highlights（18 种常用语言，6 组暗/亮主题可随 App「色随主题」）；覆盖不足 → v2 接入 prism4j（150+ 语法）自绘 `codeFence` 组件。兜底 WebView：Shiki（TextMate 语法、准确性高、按语言懒加载，Web Worker 里跑，首屏不加载全语言）。」
> **为何换掉**：Highlights 与 prism4j 均未引入（prism4j 全仓 0 命中；Highlights 依赖未出现在版本目录）；Shiki 需 Node 构建链产出 TextMate→JS 产物，与「assets 全静态打包、不从网络加载」的约束不划算，而 highlight.js 是 GitHub 网页端同源方案、单文件零构建。

### 2.13 渲染性能优化

- **本地资源加载**：JS/CSS 全部打 assets + WebViewAssetLoader，不从网络拉框架。
- **WebView 预热**：App 空闲时预热一个空模板实例，预注册 JS bridge。
- **缓存渲染结果**：以 `content hash + theme version` 为 key 缓存渲染产物（原生与 Web 各一套）；README 用 ETag。
- **按需启用 JS 功能**：普通 MD 只装 DOMPurify + 主题；出现代码块再加高亮；出现 mermaid 才加载 mermaid。
- **懒加载图片**：`loading="lazy"`；点击进全屏大图；私有仓库图片走拦截器代理加 Authorization。
- **嵌套滚动**：README 单独页面内 WebView 自身滚动；嵌在 Compose 长列表中的正文预身高控制，复杂时展开全屏内容页。
- **复用与销毁**：README 页使用单实例 WebView，非必要时 destroy，LeakCanary 监控。

### 2.14 WebView 安全策略

```kotlin
webView.settings.apply {
    javaScriptEnabled = true
    allowFileAccess = false
    allowContentAccess = false
    allowFileAccessFromFileURLs = false
    allowUniversalAccessFromFileURLs = false
    domStorageEnabled = false
    databaseEnabled = false
    geolocationEnabled = false
}
```

- 不注入 token、不拼 token 进 HTML。
- 私有图片请求只在 `shouldInterceptRequest` 对白名单 host 加 Authorization。
- 所有 HTML 一律 sanitize；禁止任意 `file://`；外链全部拦截交给路由。

---

## 3. 总体技术栈规划

### 3.1 基础技术栈

| 分类 | 选型 | 说明 |
|---|---|---|
| 语言 | Kotlin | 全项目 Kotlin |
| UI | Jetpack Compose（BOM 最新） | 声明式 UI |
| 设计系统 | Material 3 / Material You | 动态配色、M3 组件、自适应 |
| 最低 SDK | Android 8.0 / API 26 | 更好管理 WebView、java.time、性能 |
| 架构 | MVVM + UDF + Repository | Compose 友好、易测试 |
| DI | Hilt | Jetpack 生态成熟 |
| 异步 | Coroutines + Flow | 标准方案 |
| 导航 | Navigation Compose（类型安全路由） | 初期够用，后期可演进 |
| 网络 | OkHttp + Retrofit（REST） | 通用网络/REST |
| GraphQL | Apollo Kotlin 5.x | GraphQL 客户端首选 |
| 图片 | Coil 3 | Compose 支持好 |
| 分页 | Paging 3 | 列表统一 |
| 本地缓存 | Room | 离线缓存、草稿、历史 |
| 偏好 | DataStore | 主题、设置 |
| 日志 | Timber | debug 网络可视化 |
| Debug 网络 | Chucker | Debug 包使用 |
| 内存检测 | LeakCanary | Debug 包使用 |
| Markdown（短文本通道） | multiplatform-markdown-renderer **0.38.1**（+m3、+code、+coil3）+ KotlinTextMate 0.2.0 | 见 §2（2026-09-11 回写：原写 0.43.0，实现为 0.38.1；高亮由 KotlinTextMate 承担） |
| Markdown（正文通道） | WebView + github-markdown-css + markdown-it + highlight.js + DOMPurify | README/正文主渲染（ADR-0007） |
| 代码/编辑 | Rosemoe Sora Editor（editor-compose + language-textmate） | 见 §8 |
| 图标 | Material Symbols（com.composables compose-icons）+ Octicons 补充 | 见 §5.8 |

### 3.2 GitHub API 能力表

| 能力 | 通道 | 端点 |
|---|---|---|
| 主要读取（feed/repo/issue/PR/timeline/viewer） | **原计划 GraphQL；实际 REST 为主** | `/graphql` 仅覆盖 Viewer / RepositoryOverview / ViewerRepositories / ReviewThreads——Issue/PR 详情与 timeline 全走 REST（ADR-0009、§4.4） |
| README 原文/HTML | REST | `GET /repos/{o}/{r}/readme` |
| Markdown 渲染（预览/兜底） | REST | `POST /markdown`（`mode=gfm`, `context`）—— README 三级降级的 **Tier 2**（`RepoRepository.kt:239,257`）；正文预览不走此端点，走 WebView 离线 GFM（见 §2.5） |
| 文件内容/新建/更新/删除 | REST | Contents API |
| 仓库树、分支、提交 | REST | Git Data API |
| PR 文件/Diff/Reviews/Checks | REST + GraphQL | pulls/files、reviews（**写** REST）、check-runs；Review 会话线程**读**走 GraphQL `PullRequestReviewThreads` |
| 搜索（仓库/用户/issue/代码） | REST | `/search/*`（搜索 REST-only） |
| 通知 | REST | Notifications API |
| 写操作 | REST + GraphQL mutation | comments/reviews/merge 等 |

---

## 4. GitHub 数据层与认证

### 4.1 认证方案（调研后决策：用 PKCE，不用 Device Flow）

| 方案 | 结论 | 说明 |
|---|---|---|
| **OAuth 授权码 + PKCE（AppAuth-Android）** | ✅ 首选 | GitHub 2025-07 起支持 S256 PKCE；原生移动端推荐流；Custom Tabs 完成授权；无需 client secret |
| PAT（fine-grained / classic） | 次要（开发者模式） | **fine-grained PAT 不支持 GraphQL**（仅 REST）→ 该模式自动降级为 REST-only 路由 |
| OAuth Device Flow | ❌ 不选 | GitHub 官方提示存在钓鱼风险，面向用户设备的 App 优先推荐授权码+PKCE |

### 4.2 Token 安全

- 存储：**EncryptedSharedPreferences / Android Keystore**
- 明文不入 DataStore、不写日志、不注入 WebView、不进崩溃上报
- 401 统一拦截 → 静默刷新 → 失败重新登录
- 游客模式：未登录支持只读公共内容（无 token 请求）

### 4.3 请求规范

统一请求头：

```http
Accept: application/vnd.github+json
X-GitHub-Api-Version: 2022-11-28
Authorization: Bearer {token}
```

- 共享 OkHttp：Auth 拦截器、日志（debug only）、ETag 缓存、错误归一化（401/403/404/409/422/429）
- 限流策略：REST 5000/hr、GraphQL 点数——开发模式展示剩余点数提醒）

### 4.4 GraphQL 设计（Apollo Kotlin）

> **（2026-09-12 回写：本节是**最大偏离区**。「GraphQL 读优先」实际未成立——下文的典型 Query 中 `IssueDetail` **从未实现**（全仓无 `IssueDetail.graphql`，`bodyHTML` 仅存在于 schema 与文档），Issue/PR 详情、timeline、通知、搜索读路径全走 REST。实际落地的 GraphQL 面：读 `Viewer` / `RepositoryOverview` / `ViewerRepositories` / `PullRequestReviewThreads`；写 `UpdateIssue` / `ResolveReviewThread` / `UnresolveReviewThread`（外加 `IssueWriteContext` 读上下文）。完整决策记录（原意 / 现实 / 原因 / 回归触发条件）见 `docs/adr/0009-graphql-read-path-deviation.md`。下方内容保留作规划原文。）**

- Codegen（response-based）、Fragment 复用、Normalized Cache（memory → SQLite 链）
- 自定义 scalar 映射（DateTime、URI）
- 官方玩法：`pagination-support-with-jetpack-paging` 示例对接 Paging 3 游标
- 典型 Query（规划原文保留；其中 `IssueDetail` **未实现**，见本节顶部回写）：

```graphql
query Viewer { viewer { login name avatarUrl bio url } }

query RepositoryOverview($owner: String!, $name: String!) {
  repository(owner: $owner, name: $name) {
    id name description stargazerCount forkCount
    primaryLanguage { name color }
    defaultBranchRef { name }
    licenseInfo { name }
    viewerHasStarred viewerSubscription
  }
}

query IssueDetail($owner: String!, $name: String!, $number: Int!, $after: String) {
  repository(owner: $owner, name: $name) {
    issue(number: $number) {
      id number title body bodyHTML state
      author { login avatarUrl }
      labels(first: 20) { nodes { name color } }
      assignees(first: 20) { nodes { login avatarUrl } }
      milestone { title }
      timelineItems(first: 50, after: $after) {
        pageInfo { hasNextPage endCursor }
        nodes {
          ... on IssueComment {
            id body bodyHTML author { login avatarUrl } createdAt
          }
        }
      }
    }
  }
}
```

### 4.5 REST 补位端点表

| 操作 | 端点 |
|---|---|
| Markdown 渲染 | `POST /markdown` |
| README | `GET /repos/{o}/{r}/readme` |
| 文件内容/更新/创建/删除 | `GET/PUT/DELETE /repos/{o}/{r}/contents/{path}` |
| Git Tree | `GET /repos/{o}/{r}/git/trees/{sha}?recursive=1` |
| PR Files | `GET /repos/{o}/{r}/pulls/{n}/files` |
| Reviews | 写：`POST /repos/{o}/{r}/pulls/{n}/reviews`；读：GraphQL `PullRequestReviewThreads`（REST 无会话线程等价端点） |
| Checks | `GET /repos/{o}/{r}/commits/{ref}/check-runs` |
| 分支/引用 | `GET/POST... /git/refs` |

### 4.6 缓存策略

| 数据 | 策略 |
|---|---|
| Viewer | 本地缓存 + 登录后刷新 |
| Repo 元数据 | ETag + Room |
| README | ETag + 渲染 HTML 缓存（content hash + theme version） |
| Issue/PR 列表 | Paging + RemoteMediator（Room） |
| Timeline | 分页缓存 |
| PR 文件列表 | 按 PR head sha 缓存 |
| 文件内容 | 按 `branch+path+sha` 缓存 |
| Markdown 渲染结果 | 按 content hash 缓存 |
| 搜索历史 | Room |
| 草稿 | DataStore/Room |

---

## 5. Material You UI 设计系统（独立模块）

### 5.1 主题来源

1. 系统动态配色（Android 12+ 壁纸取色）
2. 自定义 seed color
3. 预设主题
4. 暗色模式 / OLED 纯黑模式 / 高对比模式
5. 自定义对比度、圆角强度、动画强度、图标风格、代码字体、行号开关

### 5.2 主题模型

> **（2026-09-11 回写：下表的 `AppThemePreferences` data class **从未落地**——该符号全仓 0 命中。实际实现是「扁平 `UserPreferencesRepository` 接口（DataStore 持久化）+ ADR-0004 的六套 `ThemeMode`」，见本节末「实际模型」。原 data class 保留作设计意图记录：其中 `styleVariant` / `contrastLevel` / `cornerScale` / `animationScale` / `iconStyle` / `codeFontFamily` / `showLineNumbers` 七个字段在实现中**或不存在、或换了载体**。）**

```kotlin
data class AppThemePreferences(
    val themeMode: ThemeMode,       // SYSTEM / LIGHT / DARK
    val useDynamicColor: Boolean,
    val seedColor: Long,
    val styleVariant: ThemeVariant, // TONAL_SPOT / NEUTRAL / VIBRANT / GITHUB_CLASSIC …
    val useOledDark: Boolean,
    val contrastLevel: Float,
    val cornerScale: Float,         // 0..1 圆角强度
    val animationScale: Float,      // 0..1
    val iconStyle: IconStyle,       // OUTLINED / ROUNDED / FILLED
    val codeFontFamily: String,
    val showLineNumbers: Boolean,
)
```

**实际模型（现状，以此为准）**：

| 维度 | 实现 | 证据 |
|---|---|---|
| 主题选择 | **单值 `ThemeMode` 六套**：`LIGHT / DARK / OLED / DYNAMIC_LIGHT / DYNAMIC_DARK / HIGH_CONTRAST` | ADR-0004 §1；`AppTheme.kt:68-98` |
| 持久化 | 扁平 `UserPreferencesRepository` 接口（DataStore），**无聚合 data class** | `core/datastore/.../preferences/UserPreferencesRepository.kt` |
| 种子色 | `seedColor: Color?`，**只作用于 `primary`**，其余角色取 M3 基线值；OLED / HIGH_CONTRAST 不收 seed | `ThemeColors.kt:499`、`AppTheme.kt:84-99` |
| 圆角 | `cornerScale` + `AppShapes.from(cornerScale)` **已接线** | `AppTheme.kt:109` |
| 动效强度 | `LocalMotionScale`（对应原 `animationScale`）**已接线** | `AppTheme.kt:103,113`、`AppMotion` |
| 图标风格 | 三档图标风格 + `AppIcon` 统一入口**已接线**（`996dc18`） | `AppThemeHost` 消费点 |
| `styleVariant` | **不存在**。原计划的 TONAL_SPOT/NEUTRAL/VIBRANT/GITHUB_CLASSIC 已由 ADR-0004 的六套主题取代 | ADR-0004 §1 |
| `contrastLevel` | **不存在**（高对比以独立 `HIGH_CONTRAST` 主题套实现） | ADR-0004 §1 |
| `codeFontFamily` / `showLineNumbers` | 存而**未消费**（死设置，挂账中） | 审计 `docs/ui-audit-2026-08-21.md` §2.1 |

- 生成（**现状**）：动态色优先走 `dynamicLightColorScheme()` / `dynamicDarkColorScheme()`（Android 12+）；其余套为静态色板。
- **⏳ Material Color Utilities（未实现）**：计划中的「seed → 全 tonal palette」**未落地**——MCU 直接依赖 0 命中，seed 只在 `lightColorScheme(primary = seed)` 里改了一个角色（`ThemeColors.kt:499`）。当前 seed 属**半成品**：视觉上仅主题色变化，容器/强调色族不跟随。若要补齐需显式引入 MCU 生成 tonal palette。
- WebView 侧同步导出令牌（见 §2.9）：由 `MarkdownThemeTokens` + `MaterialYouFusionMapper` 承担（**注意：真机 WebView 不支持 CSS `color-mix`，混色必须在 Kotlin 侧预计算**）。

### 5.3 GitHub 语义状态色映射

> **（2026-09-11 回写：下表为规划口径。**实际 `ExtendedColors` 的字段集与此不同**——实现的是 Alert 卡片语义（`noteContainer`/`onNoteContainer`、`tip`、`important`、`warning`、`caution` 各 2 字段）+ 品牌与状态族（`brand`、`success` 族、`danger` 族），共 **19 字段**（`core/designsystem/.../theme/ExtendedColors.kt`）；计划中的 `info` / `merged` / `draft` 三族**不存在**，`Merged`/`Draft` 状态改用 M3 原生角色（tertiary / surfaceContainerHigh）表达。下表保留作状态语义映射的设计依据。）**

| GitHub 状态 | 语义 | Material You 映射 |
|---|---|---|
| Open / Reopened | 成功/进行中 | secondary / tertiary container |
| Closed | 关闭 | error / errorContainer |
| Merged | 特殊 | tertiary（紫色调） |
| Draft | 中性 | surfaceContainerHigh + onSurfaceVariant |
| Checks success / failure / pending | 成功/错误/进行 | success / error / warning（扩展色） |

扩展色**原计划**定义（保留作历史记录）：

```kotlin
data class ExtendedColors(
    val success: Color, val onSuccess: Color,
    val successContainer: Color, val onSuccessContainer: Color,
    val warning: Color, val onWarning: Color,
    val warningContainer: Color, val onWarningContainer: Color,
    val info: Color, val onInfo: Color,
    val merged: Color, val onMerged: Color,
    val draft: Color, val onDraft: Color,
)
```

**实际字段集（现状）**：`noteContainer` / `onNoteContainer`（Alert note，M3 primaryContainer 派生）、`tipContainer` / `onTipContainer`、`importantContainer` / `onImportantContainer`、`warningContainer` / `onWarningContainer`、`cautionContainer` / `onCautionContainer`、`brand`（GitHub 品牌蓝）、`success` / `onSuccess` / `successContainer` / `onSuccessContainer`、`danger` / `onDanger` / `dangerContainer` / `onDangerContainer`。

### 5.4 设计令牌

- 颜色：一律 `MaterialTheme.colorScheme.*` + `ExtendedColors`，永不硬编码十六进制。
- 尺寸：`AppDimens`（cornerSmall=8dp、cornerMedium=12dp、cornerLarge=16dp、cornerExtraLarge=28dp、列表横距 16dp、内容距 16dp、代码块 padding 12dp）
- 字体：`AppTypography`（字阶、代码等宽追加）—— ✅ **已落地并接线**（`165ad76`，`core/designsystem/.../theme/AppTypography.kt`：M3 baseline 15 档 + `markdownHeading1..6`；由 `MaterialTheme(typography = AppTypography.from())` 注入，`AppTheme.kt:114`）。

### 5.5 组件清单

**通用组件**：AppTopBar、AppScaffold、AppNavigationBar、AppNavigationRail、AppTabRow、AppChip、AppLabelChip、AppStateChip、AppAvatar、AppAvatarRow、AppCard、AppListItem、AppSectionHeader、AppEmptyState、AppErrorState、AppLoadingState、AppPullToRefresh、AppSearchBar、AppBottomSheet、AppDialog、AppSnackbar

**GitHub 专属组件**：RepoHeader、RepoLanguageBar、RepoFileTree、RepoFileItem、IssueHeader、IssueTimelineItem、IssueCommentItem、IssueEventItem、PrHeader、PrTabBar、PrConversationTimeline、PrCommitItem、PrCheckItem、PrFileDiffItem、PrReviewCard、MergeBox、BranchSelector、CommitDialog、CodeViewer、CodeEditor、MarkdownViewer、MarkdownEditor、ReactionBar、LabelChipGroup、AssigneeRow、MilestoneCard

### 5.6 图标方案

- Material Symbols（com.composables compose-icons：`icons-material-symbols-rounded/outlined` 等边字体），避免 deprecated 的 `material-icons-extended`
- GitHub 特有图标（merge、draft PR、branch、fork、issue、discussion、workflow/checks）用 Octicons 补充语义
- 风格由主题配置驱动：选中态 filled、未选中 outlined；全部矢量可随主题着色

### 5.7 动效规范

- 对齐 Material Motion：Emphasized easing、时长压缩（短动画 200–300ms）
- 场景表：

| 场景 | 动效 |
|---|---|
| 列表 → 详情 | Shared element / container transform |
| Tab 切换 | Fade through |
| BottomSheet | Slide + fade |
| Snackbar | M3 默认 |
| PullToRefresh | M3 indicator |
| Star/喜欢/走 | 微缩放 + 颜色变化 |
| 主题切换 | Crossfade |

- 尊重系统动画减弱设置；滚动性能优先，不做无谓横向缩放与弹跳

---

## 6. Issue / PR 页面结构（与网页结构对齐）

### 6.1 Issue 详情页

```text
TopAppBar（返回 / 仓库名 / 更多：分享、浏览器打开、复制链接）
IssueHeaderCard
  · StateChip（Open / Closed）
  · 标题
  · 作者 + 相对时间
  · LabelChips（低饱和 chip）
  · Assignee 行、Milestone
  · ReactionBar
  · 操作：编辑 / Subscribe / Close / Reopen（权限决定可见性）
MarkdownBody（正文渲染，复杂度探测）
TimelineList
  · CommentItem（作者头像 / 时间 / MD 正文 / 反应 / 编辑·删除菜单）
  · EventItem（labeled / assigned / locked / closed / reopened…）
  · CrossReferenceItem / LinkedPrItem
BottomCommentBar（工具栏 + 编写/预览 + 输入区，键盘 insets 适配）
```

Material You 适配规范：
- StateChip 用 tonal container 区分状态
- 标签用彩色但低饱和 chip
- Timeline 保持 list item 而非重卡片
- 评论卡 `surfaceContainerLow`
- 事件项使用次要文本 + 小图标
- 底部栏固定，键盘弹起用 WindowInsets 适配

### 6.2 PR 详情页

```text
PrHeader
  · StateChip：Open / Closed / Merged / Draft
  · 标题、作者、setLabel
  · 分支信息：base ← head
  · Labels / Reviewers / Checks 摘要 / Mergeable 状态
PrTabs
  · Conversation / Commits / Checks / Files changed
ConversationTab
  · PR Body（Markdown）
  · Review 卡片（approve/comment/request-changes）
  · Review Comments、Commit References、Timeline 事件
CommitsTab
  · 提交列表（作者头像、缩写 SHA、展开 diff）
ChecksTab
  · CheckRun 列表（状态图标 / 结论 / 失败详情展开）
FilesChangedTab
  · 文件列表（+N −M）→ DiffView（统一视图/分屏切换、行号）
  · 行号点击 → 新增/回复行评论、解析会话
MergeBox
  · 合并方法选择（merge / squash / rebase）
  · 合并标题/备注、删除分支选项、合并按钮（按 viewerPermission 显隐）
```

### 6.3 Review 交互

- Comment / Approve / Request changes / Submit review
- 编辑评论、回复评论、解决/解除解决会话（对应 API 权限）
- 乐观更新 + 失败回滚 + 错误提示

---

## 7. 写功能规划

### 7.1 Markdown 编辑（评论 / Issue / PR 正文）

- 编辑器：Sora Editor 加载 Markdown TextMate 语法（源码高亮变化）
- 工具栏：加粗 / 斜体 / 行内代码 / 代码块 / 标题 / 列表(有序、任务) / 链接 / 图片 / 引用
- 编辑/预览双 Tab，预览与主渲染管线共用（WYSIWYG 一致性）
- 提及补全：@user、表情、引用自动补全（Sora auto-complete API）
- **明确不用 WYSIWYG rich editor**（调研确认：不支持表格/任务列表/多行代码块，且禁止手输 md 符号——与 GitHub 编写习惯冲突）

### 7.2 Issue 操作

- 创建 Issue、编辑 body/title
- 关闭 / 重开
- 评论新增、编辑、删除、反应
- Labels / Assignees / Milestone 编辑（权限实测）

### 7.3 PR 审查与合并

- 创建 PR（base/head）、编辑、关闭、重开
- Review：approve / comment / request changes / submit、行内评论（position/side/anchor）
- Merge（merge/squash/rebase）、Delete branch、Update branch
- > **（2026-09-11 回写：下列 GraphQL mutation 示例**保留作规划意图**，但**实际实现走 REST**——`PUT /repos/{owner}/{repo}/pulls/{number}/merge`（`core/github-rest/.../api/PullRequestApi.kt:204`，`mergeMethod` 经 `MergePullRequestRequest` 传参）。这与 §1.2 的「REST 写优先」原则一致，仅示例未同步。）**

Mutation 示例（原计划）：

```graphql
mutation MergePullRequest($pullRequestId: ID!, $mergeMethod: PullRequestMergeMethod) {
  mergePullRequest(input: { pullRequestId: $pullRequestId, mergeMethod: $mergeMethod }) {
    pullRequest { merged state }
  }
}
```

### 7.4 文件编辑（Contents API）

流程：`GET contents`（获取 sha + base64）→ 解码 → Sora 编辑 → 输入 commit message → 选择分支 → `PUT contents`。

```json
PUT /repos/{owner}/{repo}/contents/{path}
{ "message": "Update file", "content": "<base64>", "sha": "<file-sha>", "branch": "main" }
```

- 创建：同 `PUT`（无 sha）
- 删除：`DELETE` + sha
- **冲突处理**：409 → reload / overwrite / Copy local changes，绝不静默覆盖
- 支持"提交到当前分支 / 新建分支"两种模式

### 7.5 分支管理

- 列出分支、切换
- 创建分支（Git Refs API）
- 删除分支（权限）
- PR 创建时选 base/head

### 7.6 仓库管理

- Star / Unstar、Watch / Unwatch、Fork
- 创建 / 删除仓库（设置页）
- Releases / Tags 浏览（发布 Release，上传 asset）
- Topics、License、语言栏（Linguist 数据）
- 通知管理：列表 / 标记已读 / 分类过滤

---

## 8. 代码浏览与编辑

### 8.1 代码浏览

- Sora Editor read-only（TextMate 高亮、行号、横向滚动、搜索、跳转行）
- 大文件 / binary 提示；SVG/图片预览；Markdown 文件可切 Rendered/Source
- 编码处理与 CRLF 保留

### 8.2 代码编辑（Sora Editor）

- 全功能：undo/redo、查找替换、自动缩进、括号配对、软换行切换、等宽字体
- **主题同步**：由当前 Material You 主题生成编辑器配色：

```text
editor bg      = surfaceContainerLow
text           = onSurface
line number    = onSurfaceVariant
selection      = primaryContainer
current line   = surfaceContainerHigh
keyword        = tertiary
string         = success
comment        = onSurfaceVariant
function       = primary
number         = secondary
```

- 不照搬 IDE 主题，保持 App 配色一致性

### 8.3 Diff 视图

- 自研 Compose 统一 diff（行号 + `+/-/context` 着色 + 空白行数处理）
- 行内评论点位计算（REST review 参数 position/side 或 GraphQL anchor）
- 支持分屏（side-by-side）与统一视图切换
- v1 不强行完整自研所有边缘情况，复杂度过高时 WebView 兜底

---

## 9. 搜索设计

### 9.1 范围

- Repositories / Users / Issues / Pull Requests / Code（可选 Discussions）

### 9.2 UI

- M3 SearchBar：搜索历史（Room）、qualifier 快速建议、结果 Tabs（仓库/用户/issue）
- 结果列表单独 Paging，代码搜索需登录

### 9.3 API

- REST：`/search/repositories?q=…`、`/search/issues`、`/search/users`、`/search/code`
- 注意：代码搜索有额外限制；限流处理 + 结果缓存

---

## 10. 项目架构与模块化

### 10.1 模块结构

```text
app/
core/
  common/           常量、扩展、时间格式化、硬编码治理
  designsystem/     主题引擎 / 组件 / 图标 / tokens / 动效
  ui/               App 级组件（Avatar、StateView、MarkdownHost、Timeline…）
  navigation/       AppRoute、GitHubLinkParser、Navigator
  github-graphql/   Apollo client + queries + mutations + 模型
  github-rest/      Retrofit 服务 + DTO + 错误映射
  github-auth/      AppAuth + token 存储 + 会话
  github-data/      Repository 实现、mapper、PagingSource、缓存策略
  markdown/         渲染器抽象 + 原生渲染 + WebView 渲染 + 特性探测
  editor/           Sora 封装 + 主题映射 + Diff 视图
  database/         Room DAO / migration
  datastore/        DataStore（设置）
  testing/          Fake 数据 / 测试基础设施 / 截图基础
feature/
  auth/ home/ repo/ issue/ pullrequest/ search/ editor/
  settings/ notifications/ profile/
```

### 10.2 模块职责要点

- `core:github-*` 只依赖网络与模型，不依赖 UI
- `core:markdown` 内部隔离 WebView（AndroidView 仅存在于该模块内部）
- `core:editor` 隔离 Sora 依赖（未来替换低成本）
- feature 之间通过 navigation 深链交互，不互相引用
- **Konsist** 校验：分层依赖方向；`core:model` 禁止 import android 包

### 10.3 状态管理（单向数据流）

```kotlin
data class IssueDetailUiState(
    val status: UiStatus,               // Loading / Content / Error
    val issue: IssueUiModel?,
    val timeline: PagingData<TimelineUiModel>,
    val canEdit: Boolean,
    val canComment: Boolean,
)

@HiltViewModel
class IssueDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getIssueDetail: GetIssueDetailUseCase,
    private val issueActions: IssueActions,
) : ViewModel() {
    val uiState: StateFlow<IssueDetailUiState>
}
```

- 写操作为事件通道：乐观更新 / 失败回滚 / Snackbar 错误规整

---

## 11. 国际化 i18n

### 11.1 字符串资源

- `values/`（默认英文）+ `values-zh-rCN/`（中文本地化）+ 可选（日/西…）
- Compose 一律 `stringResource(R.string.…)`；含 contentDescription

### 11.2 复数与时间的格式化

```xml
<plurals name="issue_comments_count">
  <item quantity="one">%d comment</item>
  <item quantity="other">%d comments</item>
</plurals>
```

- 相对时间（3 分钟前 / yesterday / 2 days ago）本地化；绝对时间走 `java.time` + locale
- 不硬编码："3 分钟前" 这类字符串

### 11.3 RTL

- 一律 `start/end`；代码块保持 LTR
- WebView 内 Markdown 按内容 `dir` 处理
- 阿拉伯语布局纳入截图测试矩阵

### 11.4 Lint

- 开启 `MissingTranslation / HardcodedText / SetTextI18n / StringFormatInvalid`

---

## 12. 测试规划（全 JVM）

### 12.1 金字塔

```
E2E（可选，真机自测）
↑
Compose UI 测试（Robolectric + compose-test）
↑
集成测试（MockWebServer / Apollo MockServer）
↑
单元测试（JUnit4 + MockK + Turbine）
```

### 12.2 单元测试

- ViewModel、UseCase、Repository、Mapper
- **GitHubLinkParser**（绝对/相对/引用组合矩阵）
- Markdown 特性检测（`FeatureDetector.shouldFallback`，纯函数 + 单测）、模板令牌生成器、CSS 生成器；原计划的 `MarkdownRenderer` 抽象层测试项不存在（见 §2.4）
- 配额/限流处理、PagingSource

### 12.3 API 集成测试

- OkHttp MockWebServer（REST）：401/403/404/409/422/429、ETag 304、分页
- Apollo MockServer（GraphQL）：正常/部分错误/分页游标

### 12.4 Compose UI 测试（Linux 免模拟器）

- 环境：Robolectric 4.10+（Native Graphics）+ compose ui-test、`testOptions.unitTests { isIncludeAndroidResources = true }`
- 覆盖：IssueHeader 状态、StateChip、LabelChips、PR Tabs、主题切换、Markdown 链接点击、空/错/加载态
- **例外边界（⏳ 已在 ADR-0007「代价」承认）**：WebView 内部内容的真实渲染在 Linux JVM 无法验证（Robolectric 无 WebView 引擎）——README/正文的端到端验证依赖 CI KVM 模拟器（`ci.yml:285-346`，api 30 + Maestro/adb 截图）；JVM 侧只能测 HTML 产物与分流逻辑（`WebViewHtmlBuilderTest` / `WebViewFixtureRenderModeTest`）

### 12.5 截图测试（Roborazzi）

- 矩阵：Light / Dark / OLED / Dynamic color mock / 高对比；en / zh / ar-RTL；大字；各预设主题
- 支持「点击后再截」：PullToRefresh、评论打开后状态
- 产物：`build/outputs/roborazzi/*.png` + diff 图 —— Linux 本机即可预览

> **（2026-09-11 回写：现状盘点——全仓 **66 张**基线（`core:markdown` 占 37：6 张 `MarkdownViewer_*` + 31 张 `MarkdownFixture_*`），覆盖以 Light/Dark × en（含少量 RTL）为主；**OLED / 高对比 / zh / 大字等维度未覆盖**。矩阵保留为目标；「产物 `build/outputs/roborazzi/*.png` 本机预览」无落地配方，CI 仅在 failure 上传产物（`ci.yml:155-163`）。）**

### 12.6 本机运行命令（Linux）

```bash
./gradlew :app:testDebugUnitTest            # 全量单测+Robolectric+Compose 行为
./gradlew :app:recordRoborazziDebug         # 生成/更新截图基准
./gradlew :app:verifyRoborazziDebug         # 校验截图
./gradlew :app:verifyRoborazziDebug --tests "*Markdown*"   # 只跑 MD 快照
./gradlew :app:konsistCheck :app:detekt :app:lintDebug
./gradlew :app:assembleDebug
```

---

## 13. CI/CD 规划（GitHub Actions）

### 13.1 PR/主分支检查

```yaml
name: CI
on:
  pull_request:
  push: { branches: [main] }
concurrency:
  group: ${{ github.workflow }}-${{ github.ref }}
  cancel-in-progress: true
jobs:
  check:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { distribution: temurin, java-version: 21 }
      - uses: gradle/actions/setup-gradle@v4
      - run: ./gradlew spotlessCheck detekt lintDebug konsistCheck
      - run: ./gradlew testDebugUnitTest
      - run: ./gradlew verifyRoborazziDebug
      - run: ./gradlew assembleDebug
      - uses: actions/upload-artifact@v4
        with: { name: app-debug, path: app/build/outputs/apk/debug/app-debug.apk }
```

### 13.2 发布流程

- tag v* → 版本号由 gradle 读取 → 签名 keystore（GitHub Secrets）→ AAB/APK → GitHub Release 草稿（可选 Play Internal Track）→ 上传 mapping 文件

### 13.3 质量门禁

- spotless、detekt、lint、konsist、单测、截图、assemble 全绿方可合并
- 无新增硬编码字符串、无新增禁用 API（lint 规则）

### 13.4 依赖管理

- Dependabot / Renovate：每周自动升级；Compose BOM / Kotlin / AGP 单独分组；breaking change 人工审查

---

## 14. 性能优化

### 14.1 目标

| 指标 | 目标 |
|---|---|
| 冷启动 | < 1.5s（中端机） |
| 首页可交互 | < 2s |
| README 首次渲染 | < 800ms；缓存命中 < 300ms |
| 列表滚动 | 稳定 60fps（高刷 90/120） |
| APK | 依赖有意图（R8 + shrink） |

> **（2026-09-12 回写：以上目标**均无实测数据**——macrobenchmark/baselineprofile 未接入（见 §14.3），仓库内无任何真机 benchmark 记录；目标保留。）**

### 14.2 Compose

- stable 类 + remember/derivedStateOf + LazyColumn `key`/`contentType`
- Markdown 渲染结果缓存复用
- Coil 图片缓存；虚拟滚动零废图

### 14.3 Baseline Profiles

> **（2026-09-12 回写：⏳ **未实现**。`androidx.benchmark` / macrobenchmark / `baselineprofile` 插件全仓 0 命中；当前交付物是**手写**的 `app/src/main/baseline-prof.txt`（文件头 7-11 行自述：macrobenchmark 需真机/模拟器，与本仓「全 JVM」红线冲突）。`ProfileInstaller` 已接线（`libs.versions.toml:44,119`、`app/build.gradle.kts:201`），APK 内 profile 实测存在（`project-status.md:96`）。接入 macrobenchmark 后应替换手写产物。）**

- 计划：androidx.benchmark 生成：启动路径、首页滚动、Issue 详情、README 渲染、主题切换
- 现状：手写基线（按代码路径人工推导）+ APK 内置 ProfileInstaller

### 14.4 WebView 内存

- 单实例复用；离开页面 destroy；减少列表中 WebView；LeakCanary 监控

---

## 15. 无障碍与安全

### 15.1 无障碍

- 全部可点元素 contentDescription；状态语义（StateChip `role/statesDescription`）
- Heading 层级、48dp 触区、对比度达标、TalkBack 走查、减动画、大字号、RTL

### 15.2 Token 安全

- EncryptedSharedPreferences + Keystore；日志脱敏；不注入 WebView；异常上报过滤

### 15.3 HTML 安全

- DOMPurify 白名单 sanitize；禁 script/iframe（白名单外）；危险 scheme 阻断（仅 http/https/mailto）

### 15.4 网络安全

- 仅官方 api.github.com；图片代理 host 白名单；不加载第三方脚本

---

## 16. 风险与对策

| 风险 | 影响 | 对策 |
|---|---|---|
| Markdown 渲染不一致 | README/Issue 显示不理想 | **WebView 主渲染（ADR-0007）** + 服务端 HTML 优先 / 离线 GFM 降级 + 快照回归集（原生 fixture 基线 ✅ 31 张已进门禁；WebView 路径产物回归 ⏳） |
| WebView 性能/内存 | 卡顿、OOM | 主渲染通道（不再是「仅复杂内容用」）：复用单实例、离页 destroy、LeakCanary 监控 |
| API 限流（REST/GraphQL） | 请求失败 | ETag + Room + Apollo 缓存 + 精细查询 + 分流 |
| 代码块语言覆盖不足 | 部分代码块无高亮 | KotlinTextMate 7 grammar；扩展走「加 JSON 语法资产」（§2.12），prism4j 方案已废弃 |
| fine-grained PAT 不支持 GraphQL | GraphQL 功能不可用 | PAT 模式降级 REST-only（✅ #230 已落地门控） |
| 文件编辑冲突 | 用户数据丢失 | sha 校验、409 拦截、本地草稿保留 |
| Material You × GitHub 语义冲突 | 状态识别不清 | 扩展语义色 token 体系 |
| sora-editor LGPL-2.1 | 闭源合规 | 开源项目合法使用；闭源前再做评估 |

---

## 17. 最终推荐技术栈清单

```text
基础：Kotlin · Jetpack Compose(BOM) · Material 3 · Lifecycle/ViewModel
      Navigation Compose · Paging 3 · Hilt · DataStore · Room · Coil 3
构建：Gradle version catalog · Convention plugins · AGP 最新稳定 · JDK17/21
网络：OkHttp · Retrofit + kotlinx-serialization · Apollo Kotlin 5 · Chucker(debug)
Markdown（短文本）：mikepenz multiplatform-markdown-renderer 0.38.1（-m3、-code、-coil3）+ KotlinTextMate 0.2.0
Markdown（正文）：WebView（WebViewAssetLoader）+ 服务端 HTML（/readme html / POST /markdown）+ markdown-it 离线 GFM
代码/编辑：Rosemoe Sora Editor（editor-compose + language-textmate）
语法高亮：KotlinTextMate（原生短文本，7 grammar 资产）· highlight.js 11.11.1（WebView 通道，整包）· Sora TextMate（代码浏览）
主题：dynamic color + 扩展语义色 + CSS 变量桥（⏳ Material Color Utilities 未接入，见 §5.2）
图标：Material Symbols（compose-icons）+ Octicons（GitHub 专属）
测试：JUnit4 · Robolectric(RNG) · Roborazzi · MockK · Turbine · MockWebServer · Apollo MockServer
CI/CD：GitHub Actions · spotless · detekt · Android Lint · Konsist · Roborazzi · 签名 Release
i18n：values/en-zh + plurals + lint 规则
```

---

## 18. 收尾说明

> 一句话：**Kotlin + Compose + Material 3 全原生 UI；GraphQL 读、REST 写、AppAuth+PKCE 认证；Markdown 原生为主、WebView 服务端 HTML 兜底并在双端共享同一套 Material You 令牌与统一链接解析器；代码浏览编辑用 Sora Editor；全链路 JVM 测试在 Linux 上免模拟器运行；GitHub Actions 支撑 CI、截图回归与发布闭环；从基建第一天落实 i18n、令牌化与硬编码红线。**
>
> **（2026-09-12 回写：本句为规划期小结，两处已失真——① 数据层现为「REST 读为主、GraphQL 少数读位」（ADR-0009）；② Markdown 现为「WebView 主渲染 + 原生短文本」（ADR-0007）。一概以正文各节回写为准。）**