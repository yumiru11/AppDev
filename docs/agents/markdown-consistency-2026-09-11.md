# Markdown「与网页端一致」现状报告（2026-09-11）

> **这份报告把 `request.txt` 的第一优先级验收标准
> 「readme 必须正确渲染，能做到与网页端一致的正常渲染部分写法」
> 从口号变成一张可核查的表。**
>
> 数据源：`plan.md` §2.3 渲染目标清单（35 条原子条目）× 三条渲染路径的实际产物。
> 表格与本报告的实验部分由代码强制同步——
> `core/markdown/src/test/kotlin/.../fixture/MarkdownGfmFixtureCatalogTest.kt` 与
> `MarkdownConsistencyReportTest.kt` 会解析下面 `BEGIN/END FIXTURE TABLE` 之间的表格，
> 断言它与 `MarkdownGfmFixtures.ALL` 逐行一致。**改夹具不改本表 → 测试红。**

## 0. 一句话结论

§2.3 的 **35 条**里：**7 条**有渲染且回归可执行、**24 条**有渲染但像素基线待云端录制、
**4 条**完全没有渲染路径（锚点跳转 / 图片懒加载 / KaTeX / Mermaid）。
WebView 路径的**产物层**回归已覆盖全部 35 条；**像素层**在 Linux JVM 上对 WebView
永远不可测，只能靠 CI 模拟器截图与真机走查。

## 1. 方法与分层（先读这一节，否则下面的 ✅ 会被误读）

Robolectric 下的 WebView **不能栅格化像素**（`docs/research/screenshot-automation-alt.md`），
所以本回归集按「能证明什么」分三层，**不假装全覆盖**：

| 层 | 载体 | 能证明 | **不能**证明 |
|---|---|---|---|
| ① 解析层 | `GfmNativeParserCapabilityTest`（纯 JVM，`GFMFlavourDescriptor`） | 原生链的解析器**认得**该写法（AST 元素证据，今天就可执行） | 认得不等于画得对 |
| ② 原生像素层 | `MarkdownFixtureScreenshotTest`（Robolectric Native Graphics + Roborazzi） | 原生短文本链渲染出的**像素**与基线一致（排版/配色/间距回归会被拦下） | ① 不覆盖 WebView 路径；② 图片解码被换成确定桩（只测布局分支）；③ 基线≠GitHub 截图，只代表「本 App 既有观感」 |
| ③ WebView 产物层 | `WebViewFixtureRenderModeTest` / `WebViewMaterialYouTokenContractTest` / `WebViewOfflineGfmCapabilityTest` | 注入 WebView 的 **HTML/CSS 是否正确**：两级管线分流、§2.10 令牌、`data-markdown-raw` 无损往返、离线通道**真的有哪些能力**（打包产物级证据）、token 不泄漏、无 `color-mix()` | WebView 里的**视觉结果**（真机是否按变量上色、字号是否与网页一致） |
| ④ 真实像素层 | CI 模拟器截图 `.github/scripts/screenshots.sh`（`readme-webview.png` / `readme-mermaid.png` 帧）+ 真机走查 | 真机 WebView 的**实际画面** | 非自动化断言（人工比对），且帧数少、无逐构造覆盖 |

**结论**：`README` 主通道（服务端 HTML）的「与网页端一致」本质上由 **GitHub 自己渲染**保证，
App 侧要保证的是「不要把它弄坏」（清洗、相对 URL 改写、CSS 注入、链接拦截）——这正是第 ③ 层锁的东西。
真正会「不一致」的是**离线降级通道**与**原生短文本通道**，见 §3。

## 2. §2.3 逐条对照表

图例：`✅` = 该路径能渲染成与网页端等价的结果；`—` = 该路径不渲染或只降级成别的东西。
回归状态：`✅` 有渲染 + 回归可执行 · `🟡` 有渲染 + 回归已写入但**像素基线待云端录制** ·
`❌` 无渲染路径（审计口径的「未实现」）。

<!-- BEGIN FIXTURE TABLE -->
| 夹具 | §2.3 条目 | 原生 | 服务端 HTML | 离线 GFM | 回归状态 |
|---|---|---|---|---|---|
| `01-headings` | 标题 | ✅ | ✅ | ✅ | 🟡 |
| `02-paragraphs` | 段落 | ✅ | ✅ | ✅ | 🟡 |
| `03-blockquote` | 引用块（blockquote） | ✅ | ✅ | ✅ | 🟡 |
| `04-bold` | 加粗 | ✅ | ✅ | ✅ | 🟡 |
| `05-italic` | 斜体 | ✅ | ✅ | ✅ | 🟡 |
| `06-strikethrough` | 删除线 | ✅ | ✅ | ✅ | 🟡 |
| `07-table` | 表格（含单元格内联内容、对齐） | ✅ | ✅ | ✅ | 🟡 |
| `08-ordered-list` | 有序列表 | ✅ | ✅ | ✅ | 🟡 |
| `09-unordered-list` | 无序列表 | ✅ | ✅ | ✅ | 🟡 |
| `10-nested-list` | 嵌套列表 | ✅ | ✅ | ✅ | 🟡 |
| `11-task-list` | 任务列表 `- [ ]` / `- [x]` | ✅ | ✅ | ✅ | 🟡 |
| `12-inline-code` | 行内代码 | ✅ | ✅ | ✅ | 🟡 |
| `13-fenced-code-block` | 围栏代码块 | ✅ | ✅ | ✅ | 🟡 |
| `14-code-language-tag` | 代码语言标注 | ✅ | ✅ | ✅ | 🟡 |
| `15-syntax-highlight` | 语法高亮 | ✅ | ✅ | ✅ | 🟡 |
| `16-code-copy` | 代码块复制按钮 | ✅ | ✅ | ✅ | 🟡 |
| `17-image-relative` | 图片（相对路径引用） | ✅ | ✅ | — | 🟡 |
| `18-image-github-cache-domain` | 图片（GitHub 缓存域） | ✅ | ✅ | ✅ | 🟡 |
| `19-external-link` | 外部链接 | ✅ | ✅ | ✅ | 🟡 |
| `20-autolink` | 自动链接（裸 URL） | ✅ | ✅ | ✅ | 🟡 |
| `21-relative-link` | 相对链接（`./`、`../`、`/owner/repo`） | ✅ | ✅ | — | 🟡 |
| `22-mention-user` | 提及：`@user` | — | ✅ | — | ✅ |
| `23-mention-org-team` | 提及：`@org/team` | — | ✅ | — | ✅ |
| `24-issue-ref` | 引用：`#123`、`owner/repo#123`、`gh-123` | — | ✅ | — | ✅ |
| `25-commit-sha-ref` | 提交引用（裸 sha） | — | ✅ | — | ✅ |
| `26-emoji-shortcode` | Emoji 短句：`:rocket:` | — | ✅ | — | ✅ |
| `27-github-alerts` | GitHub Alerts：`[!NOTE]`/`[!TIP]`/`[!IMPORTANT]`/`[!WARNING]`/`[!CAUTION]` | ✅ | ✅ | ✅ | 🟡 |
| `28-anchor-jump` | 锚点跳转（`#section`） | — | — | — | ❌ |
| `29-image-lazy` | 图片：懒加载 | — | — | — | ❌ |
| `30-image-zoom` | 图片：点击放大 | ✅ | ✅ | ✅ | 🟡 |
| `31-image-gif` | 图片：GIF | — | ✅ | ✅ | ✅ |
| `32-inline-html` | 内嵌 HTML（安全子集） | ✅ | ✅ | ✅ | 🟡 |
| `33-math-katex` | Math/KaTeX（兜底通道，可选） | — | — | — | ❌ |
| `34-mermaid` | Mermaid（兜底通道，可选） | — | — | — | ❌ |
| `35-footnote` | 脚注（尽力而为，不保证与网页完全一致） | — | ✅ | — | ✅ |
<!-- END FIXTURE TABLE -->

> 注：`35-footnote` 与 `22`–`26` 一样是**服务端 HTML 独占**——路径列上原生与离线都是 `—`，
> 表示「降级通道不渲染脚注 / 提及 / 引用 / emoji」，**不是**「README 里看不到脚注」。
> 这类条目落在 `✅` 是因为 App 侧要保证的只是「不把 GitHub 渲染好的 HTML 弄坏」，
> 而这由第 ③ 层回归锁定。判读时请同时看路径列与状态列。

表是自动同步的，但上面这条注解释了 `❌` 的一个易误读处。

### 统计

| 口径 | 数量 |
|---|---|
| §2.3 原子条目总数 | 35 |
| ✅ 有渲染 + 回归可执行 | **7** |
| 🟡 有渲染 + 原生像素基线待录制（WebView 产物回归已就位） | **24** |
| ❌ 无渲染路径 | **4** |
| 服务端 HTML 通道覆盖 | 31 / 35 |
| 离线 GFM 通道覆盖 | 23 / 35 |
| 原生通道覆盖 | 24 / 35 |

严格口径（只认「基线已生效的像素回归」）：**0**——因为 24 条原生基线尚未录制，
4 条无渲染。这就是本票结束时「与网页端一致」的**真实自动化水位**，不要按 7/35 之外的数字宣传。

## 3. 最严重的 3 个缺口

### 缺口 1（最严重）：离线 GFM 通道缺 12 条 §2.3 写法，其中 2 条是「功能坏了」而非「样式不同」

离线 GFM（markdown-it 14.1.0 + renderer.js）是 **Issue/PR 正文的唯一通道**，也是 README
服务端异常时的降级通道。产物级证据（`WebViewOfflineGfmCapabilityTest`）：

| 缺失写法 | 产物证据 |
|---|---|
| 相对链接、相对图片 | `WebViewHtmlBuilder.kt:86` 的 `rewriteRelativeUrls(buildContentBlock(...))` 在离线模式下作用在**已转义的属性值**上，正则匹配不到 `<img src>`；WebView base 是 `https://appassets.androidplatform.net/`（`WebViewMarkdownRenderer.kt:226`）→ 相对路径解析到 appassets 域 404 |
| `@user` / `@org/team` 提及 | markdown-it 无 mention 插件 |
| `#123` / `owner/repo#123` / `gh-123` 引用 | 同上，正文裸引用不被 linkify（`GfmNativeParserCapabilityTest.issueReferenceFixture_hashSyntax_isNotAutolinked` 同样在原生侧证否） |
| 裸 sha 提交引用 | 同上 |
| Emoji 短码 `:rocket:` | `markdown-it.min.js` 不含 `emoji` 字样（未打包 markdown-it-emoji）；`plan.md` §2.2① 的 `GhMarkdownProcessor`（emoji→Unicode）**全仓无实现** |
| 锚点跳转 | 无 markdown-it-anchor；`plan.md:202` 约定的 `scrollToAnchor(id)` 无任何调用点 |
| 图片懒加载 | 全仓无 `loading="lazy"` 注入点（`plan.md:271` 的约定未落地） |
| KaTeX / Mermaid | `assets/webview/` 无对应运行时 |
| 脚注 | `markdown-it.min.js` 不含 `footnote` 字样（未打包 markdown-it-footnote） |

前两条是**功能性损坏**：Issue 正文里 `![img](./docs/x.png)` 与 `[doc](./docs/a.md)`
在网页端可点可看，在 App 里是死链。这也是本报告里唯一「用户一定能感知」的一类缺口。

### 缺口 2：原生短文本通道的 2 个缺陷（`<details>` 正文重复渲染、相对图片永不解析）

见 §4 的 D1 / D2。二者都在**用户可见的正文**里，且都在 §2.3 明确要求的写法上。

### 缺口 3：4 条「完全没有渲染路径」的写法

`28-anchor-jump`、`29-image-lazy`、`33-math-katex`、`34-mermaid`。
其中**锚点跳转**与**图片懒加载**是 `plan.md` §2.9 / §2.13 已经**拍板过的约定**（不是可选加分项），
`math` 与 `mermaid` 才是 §2.3 标注「兜底通道，可选」。审计若按「未排票」计，
应把前两条与后两条分开计权。

## 4. 发现但**未修**的真实渲染缺陷（本票只记录，不修）

> 依据任务纪律：「不改生产代码的行为；发现真实渲染缺陷记录到报告并单独列出，
> 不要在本票里顺手修（避免混淆回归集与修复）」。

| # | 缺陷 | 证据 | 影响面 |
|---|---|---|---|
| **D1** | `<details>` 采用 GitHub 惯用的**空行分隔**写法时，块正文被**重复渲染**：一份折叠进卡片，一份以普通段落**常驻可见** → 折叠语义失效 + 内容重复 | 视觉证据：`MarkdownFixture_32-inline-html_light_actual.png`（录制基线时可直接复核）；代码路径 `HtmlDetailsParser.kt:32-39`（借区间到 `</details>` 取 body）+ CommonMark 空行终止 HTML block → 中间段落成为独立 `PARAGRAPH` 被正常渲染；`EnhancedHtmlBlock.kt:48-56` 只处理 HTML_BLOCK 自身 | 原生通道；README/正文里最常见的 details 写法 |
| **D2** | `EnhancedMarkdownViewer` **未把 `baseRepoUrl` 透传**给 `EnhancedMarkdownImage` → 相对路径图片在增强链上永远解析不到 raw 域 | `EnhancedMarkdownViewer.kt:176` `image = { model -> EnhancedMarkdownImage(model) }`（对比同文件 `EnhancedParagraph(model, baseRepoUrl)` 有透传）；`EnhancedMarkdownImage.kt:63` 只有拿到 `baseRepoUrl` 才会 `resolveRawImageUrl` | 原生通道；`FileViewerScreen` / `FileEditScreen` / `RepoDetailScreen:1467` 的预览与正文 |
| **D3** | 离线 GFM 不重写相对链接/图片（缺口 1 的代码根因） | `WebViewHtmlBuilder.kt:86`；既有测试 `WebViewHtmlBuilderTest.build_offlineMode_withBaseRepoUrl_doesNotRewriteEscapedRawMarkdown` 已把该行为**固化为期望**，修 D3 必须同时改这条测试 | Issue/PR 正文 + README 降级路径 |
| **D4** | 原生链**无 coil-gif 依赖** → GIF 只出首帧 | `gradle/libs.versions.toml:144-146` 只有 `coil-compose` / `coil-network-okhttp` / `coil-svg`；`catalog 31-image-gif` 因此不含 NATIVE | 原生通道 |
| **D5** | 内嵌 HTML 的**内联标签语义丢失**：`<kbd>` / `<sub>` / `<sup>` 降级为纯文本（网页端有 kbd 样式与上下标）；`<script>` 标签被剥离但其正文 `alert(1)` 仍可见 | 视觉证据：`MarkdownFixture_32-inline-html_light_actual.png` 中 `Ctrl + C`、`下标`、`上标` 均无对应排版；`EnhancedHtmlBlock.kt:62-72` 走 `stripHtmlTags` 纯文本降级 | 原生通道 |
| **D6** | `plan.md` §2.10 的字体变量命名与实现**漂移**：文档写 `--md-sys-font-sans` / `--md-sys-font-mono`，实现是 `--fontStack-sansSerif` / `--fontStack-monospace`（`markdown-you.css:23,209` 消费的是后者） | `WebViewMaterialYouTokenContractTest.buildCss_fontVariables_deviateFromPlanSection210NamesAsDocumented` 双向锁定 | **文档失真**（功能正常），但会让「按 §2.10 核对实现」的人得出错误结论 |
| **D7** | 原生链无 mention / issue-ref / sha 渲染 | `GfmNativeParserCapabilityTest.nonNativeFixtures_...` 证明 AST 无对应元素；`GitHubLinkParser` 只在**点击**时解析绝对 URL，不参与渲染 | 原生通道（评论/通知） |

### 附带观察（非缺陷）

- `FeatureDetector` 无任何生产调用点（`repository.kt` 内仅自引用）。这与 AGENTS.md
  「FeatureDetector 保留但 README 分流判定不再使用」一致，属**有意保留**，不计入缺陷。
- 基线 PNG 为 **RGBA 且大面积透明**（页面背景由宿主屏幕提供，`MarkdownViewer` 自身不画底）。
  与既有 `MarkdownViewer_*.png` 基线约定一致（本机实测 alpha0 比例 26%–92%）。
  副作用：这些基线**不会**捕获「页面背景色回归」——因为组件本就没有背景。

## 5. 需求审计引文的复核（发现的 4 处不符，供主代理决策）

任务描述里作为「审计原话」引用的内容与仓库实际状态有出入，逐条列出以免结论建立在错误前提上：

1. **`docs/agents/spec-audit-2026-09-11.md` 不存在**。仓库里的审计是
   `docs/agents/task-audit-2026-09-06.md`（无 `spec-audit` 系列文件）。
2. **该审计的 §9 把「README 正确渲染（与网页一致）」判为 `✅`**（理由：服务端 HTML 优先 +
   离线 GFM + 截图 CI 实拍），与「需求审计把它列为第一号 P0 缺口」的说法相反。
   审计真正点名的门禁项是 **D02「模块级 Roborazzi 基线不在门禁」**，其正文
   （`task-audit-2026-09-06.md:259`）指的是 **core:ui 7 张 / core:designsystem 2 张漂移**，
   已由 #170 闭环（`ci.yml:146-152` 现覆盖 app + 6 模块）——**与 `prototype` 无关**。
   审计对原型只做了一件事：在 §10 的基线盘点里把它数进「仓库内 32 张」（`:309`），
   并未点名它是门禁缺口。因此「审计明确点名孤儿原型」这一前提**在文档里找不到依据**
   （事实本身成立，见下）。
3. **「全仓无 Markdown golden/对比回归」不准确**。本票之前 `:core:markdown` **已有 6 张基线**
   （`MarkdownViewer_{light,dark,alert_light,alert_dark,table_light,table_dark}.png`），
   且**已在 CI 门禁**（`ci.yml:149`）。准确的说法是：
   **有 3 类写法的零散基线，但没有与 §2.3 清单对齐的夹具集，也没有 WebView 路径的产物回归。**
4. **`plan.md:950` 的原文**是「原生优先 + 服务端 HTML 兜底 + 快照回归集」——
   「快照回归集」确实不存在（本票补上），但该行的「原生优先」在 ADR-0007 之后**已过期**
   （现在是 WebView 主渲染）；顺带一并订正认知。

**审计说对的部分**（已实测复核）：
- `prototype/readme-comparison` 的 4 张基线不在任何 verify 列表 ✅（`ci.yml:146-152` / `nightly.yml:107-113` 均无该模块）；
- 不在 detekt 范围 ✅（`build.gradle.kts:53` `source = files("app/src", "core", "feature", "buildSrc/src")`，
  不含 `prototype`）；
- 也不在覆盖率门禁 ✅（`build.gradle.kts:243-244` 显式跳过 `:prototype`）。

**审计没说的部分**（本票补测）：

| 门禁 | `prototype/readme-comparison` 是否被覆盖 | 证据 |
|---|---|---|
| detekt | ❌ 不覆盖 | `build.gradle.kts:53` 的 source 列表无 `prototype` |
| **Konsist** | ❌ 不覆盖 | `app/src/test/.../konsist/ArchitectureTest.kt:34,51,93,102,104` 的 `scopeFromDirectory` 只取 `core` / `feature` / 具体 core 模块路径 |
| spotless（ktlint） | ✅ **覆盖** | `build.gradle.kts:46` `target("**/*.kt", "**/*.kts")`，仅排除 `**/build/**` —— 这是唯一管到原型的门禁 |
| 覆盖率 | ❌ 显式跳过 | `build.gradle.kts:243-244` |
| Roborazzi verify | ❌ 不在 CI 列表，且任务本身**编译失败** | 见下节第 1 条 |

## 6. 孤儿原型 `prototype/readme-comparison` 的门禁结论

**结论：不纳入 CI 门禁，理由有四条实测证据（比审计的「孤儿」判断更严重）。**

1. **它的测试源码根本编译不过** —— 这是决定性的：
   `./gradlew :prototype:readme-comparison:verifyRoborazziDebug` 在
   `:prototype:readme-comparison:compileDebugUnitTestKotlin` 阶段即失败：
   ```
   e: ReadmeComparisonScreenshotTest.kt:37:13 No parameter with name 'horizontalScrollEnabled' found.
   ```
   `EnhancedMarkdownViewer` 早已删除 `horizontalScrollEnabled` 参数，原型测试未同步。
   → **一旦把该 verify 任务写进 CI，CI 会立刻红**（不是「可能红」）。修法是删掉
   `ReadmeComparisonScreenshotTest.kt:37` 那一行实参（一行），但属本票范围外的修复，
   留作后续小票（`build.gradle.kts:243` 的注释「测试源码编译不过」正是这个已知状态）。
2. **4 张基线里只有 2 张是可验证的**：`readmeComparison_native{Light,Dark}.png` 由 Roborazzi 消费；
   `readmeComparison_webview_{light,dark}.png` **不被任何 Gradle 任务产出或校验**
   （`WebViewHeadlessHtmlWriterTest` 只写 `build/headless/*.html`，PNG 来自一次性的
   带外 headless Chromium 运行，见该测试 KDoc）。纳入门禁只会「验 2 张，剩 2 张继续孤儿」。
3. **那 2 张原生基线本身价值很低**：尺寸 **320×470**，而原型 fixture `complex-readme.md` 有 92 行；
   `EnhancedMarkdownViewer` 内建 `verticalScroll` 会把内容裁到屏幕高度 →
   基线只截到首屏，**读不出 README 的大部分**。（本票的夹具基线用
   `@Config(qualifiers = "w480dp-h1600dp")` 规避了同一问题，实测产物高度随内容自适应，
   112–1264px 不等，无裁剪。）
4. **模块定位是 throwaway**：`build.gradle.kts:243-244` 已按「一次性渲染原型」显式跳过覆盖率门禁，
   `AssetMarkdownImageTransformer.kt:3` 自述 `Do not reuse in production`。

**因此**：原始 4 张基线**继续保留**（历史留痕，`git log 3877d5d` 有来源），
但本报告即为其「不纳入及其理由」的正式记录。
后续若要复活它，最小路径是：修 1 行编译错误 → 删除其中 2 张无法验证的 webview PNG（或补一个真正的
headless 采集任务）→ 用 `Record screenshots (CI canonical)` 重新录制 2 张原生基线 → 再加进 `ci.yml`。

## 7. 本票新增的回归集与运行方式

| 文件 | 层 | 作用 |
|---|---|---|
| `core/markdown/src/test/resources/markdown-fixtures/*.md`（35 个） | 夹具 | §2.3 逐条对应，纯 Markdown、无元数据注释 |
| `.../core/markdown/fixture/MarkdownGfmFixtures.kt` | 目录 | 夹具 ↔ §2.3 条目 ↔ 三条路径的**唯一事实来源**，并给出基线清单 |
| `.../fixture/MarkdownGfmFixtureCatalogTest.kt` | 自校验 | 清单完整性（多一条少一条都红）、夹具存在且非空、基线清单唯一 |
| `.../fixture/GfmNativeParserCapabilityTest.kt` | ① | 原生解析器能力探针（AST 元素级） |
| `.../fixture/MarkdownFixtureScreenshotTest.kt` | ② | 原生像素基线（24 浅色 + 7 深色） |
| `.../webview/WebViewFixtureRenderModeTest.kt` | ③ | 两级管线分流 + `data-markdown-raw` 无损往返（35 条全跑） |
| `.../webview/WebViewMaterialYouTokenContractTest.kt` | ③ | §2.10 令牌 + 字体命名偏差 + **禁用 `color-mix()`** |
| `.../webview/WebViewOfflineGfmCapabilityTest.kt` | ③ | 离线通道能力（产物级证据）+ 缺口集合钉死 |

```bash
# 本票新增部分（② 之外全部可立即执行）
./gradlew :core:markdown:testDebugUnitTest --offline
# 像素基线（首次运行会因缺基线而失败——这是预期，基线须云端录制）
./gradlew :core:markdown:verifyRoborazziDebug --offline
```

**基线录制**：只能走 CI 的 `Record screenshots (CI canonical)` workflow
（`.github/workflows/record-screenshots.yml`）——Robolectric Native Graphics 依赖运行环境，
本机录的基线在 CI 上 verify 会全红（PR #181 实测 8 张全红）。**本机禁止跑
`recordRoborazziDebug`**（1000s+ 卡死史，AGENTS.md 铁律）。需录制的 31 个文件名见
`MarkdownGfmFixtures.baselineNames()`。

## 8. 已知局限（不要把这些当成已验证）

1. **原生像素基线尚未录制** → 表里 24 条 `🟡` 的像素层回归**当前不生效**；本票只交付了测试与夹具。
2. **WebView 像素在 JVM 上永远测不到** → 第 ③ 层全是「产物正确」，不是「画面对」。
3. **图片解码不在基线覆盖内** → 基线用固定位图桩（只测布局分支，含徽章 20dp / 普通图全宽）。
4. **徽章渲染不进 golden**（刻意）：`NativeBadgeRow` 走 `coil3 AsyncImage` 且无注入口，
   需要真实网络 → 像素不确定。徽章**解析**由 `EnhancedHtmlBlockTest` 覆盖，
   渲染只靠真机走查。
5. **与 GitHub 网页端的像素等价性从未被自动化比对**：基线是「本 App 既有观感」的快照，
   不是 GitHub 截图。跨端一致性靠 `SERVER_HTML` 通道（GitHub 渲染）+ 人工比对。
   若要做真正的跨端 diff，需要新增「headless Chromium 渲染同夹具 → 与本 App 截图对比」的管线，
   这是独立一票的工作量。
6. **`compose-lint` 的失效（审计 Q01）与本报告无关，但同样会让「看起来在跑其实没查」**，
   建议一并处理。
7. **本回归集不闭合审计 §10 的「远未达 plan §12.5 矩阵」**（`task-audit-2026-09-06.md:309`
   点名的 OLED / 高对比 / en-zh-ar / 大字 / 6 主题各屏）。夹具基线只做
   **浅色全量 + 深色 7 条**，主题维度是刻意收敛的（否则 35 夹具 × 多主题会膨胀到数百张，
   而深色对代码块/Alert 之外写法的视觉影响很小）。真要补 §12.5 矩阵，应作为独立一票，
   按「渲染主题 × 代表夹具」而不是「全量夹具 × 全量主题」设计。

## 9. 建议的后续票（按优先级）

1. **修 D1 / D2**（原生通道的可见错误，改动小、影响面明确），并为二者各自补一条针对性单测。
2. **修 D3**（离线 GFM 相对链接/图片）：属于「功能不可用」，同时要改既有测试
   `build_offlineMode_withBaseRepoUrl_doesNotRewriteEscapedRawMarkdown` 的期望。
3. **录制 31 张原生基线**（走 CI workflow），让 24 条 `🟡` 变成真正生效的像素回归。
4. **补离线 GFM 的 emoji 短码**（`plan.md` §2.2① 的 `GhMarkdownProcessor` 从未实现），
   这是 Issue 正文里最常见的不一致。
5. **原型模块复活或归档**：按 §6 的最小路径，或直接把 4 张基线移入 `docs/` 作为历史留痕并删模块。
6. **订正 `plan.md` §2.10 的字体变量名**与 §16 风险表的「原生优先」措辞（文档失真，属 #171）。
