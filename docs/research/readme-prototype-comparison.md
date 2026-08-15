# README 渲染双版本原型对比报告（A: WebView 融合版 / B: 原生增强版）

> 分支：`prototype/readme-comparison`（决策前原型，不合并 main、不开 PR）。
> 样本：`prototype/readme-comparison/src/main/assets/complex-readme.md`（A/B 共用同一字节）。
> 截图：`prototype/readme-comparison/src/test/screenshots/`。

## 0. 截图与验证方式说明（重要降级）

- **B（原生增强版）**：Roborazzi + Robolectric Native Graphics 实测生成 light/dark 基准。
  `readmeComparison_nativeLight.png` / `readmeComparison_nativeDark.png`。
  为规避 Roborazzi 无边界测量下 `horizontalScroll` 的约束异常，截图时 `horizontalScrollEnabled = false`；
  生产路径默认开启横向滚动。
- **A（WebView 融合版）**：Robolectric 无法光栅化 WebView（调研已定论）。本次用
  `WebViewHtmlBuilder + MaterialYouFusionMapper + RenderMode.OFFLINE_MARKDOWN_IT` 生成**同一条离线链的最终 HTML**，
  再把 `appassets.androidplatform.net` 资源地址改写为本地 `file://` 后，由本机 headless Chromium 320px viewport 截图。
  `readmeComparison_webview_light.png` / `readmeComparison_webview_dark.png`。
  注入时机 / 防注入 / 防闪烁由 JVM 单测覆盖（`MaterialYouFusionMapperTest`、`WebViewHtmlBuilderFusionTest`）。
  这是本原型最大的已知偏差，路线决策时必须知晓：**A 截图为离线链等价渲染，不是真机 WebView 像素**。

## 1. 十六行维度对比（实测结论）

| # | 维度 | A WebView 融合版 | B 原生增强版 |
|---|---|---|---|
| 1 | 主题融合度（颜色） | 容器/前景/边框经 `MaterialYouFusionMapper` 覆盖 GitHub 语义变量；语法 token 保留 GitHub PrettyLights 原色。实测 M3 浅/深色均进入内容卡片与链接 | 容器/前景/边框直接 `MaterialTheme.colorScheme`；`ExtendedColors` 提供 alert 五色；语法 token 用 VS Code Dark+/Light+ 原色。主题融合 100% 在 Compose 管线内 |
| 2 | 主题融合度（圆角/字体） | 注入 `--md-sys-shape-corner-*` 与 `--fontStack-*`；CSS 层可精确控圆角/字号 | 组件用 `MaterialTheme.shapes.*`；Typography 令牌控制 16sp/1.6 与标题比例，字体由 Compose 全局决定 |
| 3 | 深色模式适配 | `data-theme` + `prefers-color-scheme` 双通道；`addDocumentStartJavaScript` 首帧注入；`setAlgorithmicDarkeningAllowed(false)` + `WEB_THEME_DARKENING_ONLY`。无闪烁由单测覆盖，真机仍待验证 | MaterialTheme 自动适配；代码块深色容器用 `surfaceContainerLowest`（比正文深一档）；Alert 深色经 ExtendedColors |
| 4 | 表格 | github-markdown-css 原生表格：表头加粗、上下边框、斑马纹、overflow-x 横滚。高保真 | 自研 AST 提取 + 圆角容器 + 表头加粗/上下边框/斑马纹 + `horizontalScroll`。单元格用 `MarkdownText` 保留行内格式。需继续做列宽与长表压测 |
| 5 | 代码高亮（配色） | highlight.js 11 + PrettyLights 原色（半融合 C），CSS 变量随主题切换 | KotlinTextMate 7 语法 + VS Code Dark+/Light+ 原色，背景透明交 M3 容器（半融合 C） |
| 6 | 代码高亮（语言/可读性） | highlight.js 全量常见语言；行内/围栏均可。Robolectric 无法测像素，headless 截图验证 Kotlin/JSON/markdown fence | 仅 7 个打包 grammar（kotlin/python/go/java/json/yaml/shell）；其余落样式兜底块，可读性仍可用但无 token 色 |
| 7 | 代码块工具 | JS 注入悬浮 Copy 按钮（A 侧截图是文件链，clipboard 未真机验证）；无语言标签 | 语言标签 + 复制按钮 + 勾形反馈 1.5s（`CopyFeedbackStateTest` 覆盖）+ 横向滚动 |
| 8 | Alert 卡片 | github-markdown-css 左色条 + 自写 CSS mask Octicons + 五型 tinted 背景；禁 emoji | 左 4dp 色条 + ExtendedColors 五型容器 + Octicons ImageVector + 加粗标题；禁 emoji |
| 9 | 引用块 | CSS：3px primary 左竖条 + surfaceContainerLow 淡底 + 圆角 | 官方 `MarkdownBlockQuote` + M3 色；本原型未额外加自绘 3dp 竖条（T7 截图可复核） |
| 10 | 折叠 details | HTML `<details>` 由浏览器原生折叠，保真度最高 | spike：`custom` 槽可收到 `HTML_BLOCK`，但 GFM 把 `<summary>`、正文、`</details>` 拆成多个节点，当前只渲染首块卡片；正文可能重复、闭合块需抑制 → **记录为原生缺口（见 §4）** |
| 11 | 任务列表 checkbox | markdown-it 任务插件 + `accent-color` M3 风格圆角勾选；交互事件已有 bridge | renderer 默认 checkbox（M3 风格）；本原型未替换。交互未验证 |
| 12 | 图片（圆角/点击/加载） | CSS 圆角 + 阴影；`loading=lazy`；bridge `onImageClick` 已留口但真机未验证 | 圆角 + shadow + 点击纯黑 Dialog fade-in；本地 fixture 经自定义 asset transformer 加载成功 |
| 13 | 链接 | JS 拦截所有 `<a>` → `MarkdownBridge` → `GitHubLinkParser`；外部 CustomTabs、内部应用内导航 | `LocalUriHandler` 覆盖 → `resolveMarkdownUrl` → `GitHubLinkParser`；同一分发链 |
| 14 | 排版（16sp/1.6 基准） | github-markdown-css 标题比例 + override 16px/1.6；实测比例符合 GitHub | `markdownTypography` 显式收敛：H1 32/H2 24/H3 20/H4 16，正文 16sp/1.6；修复「原生标题太大」 |
| 15 | 动效潜力 | CSS 变量 transition / keyframes / View Transitions 可复刻经典 M3 easing；真 spring 不做 | Compose `AnimatedVisibility`/`animateFloatAsState`（details 箭头、图片 fade-in）；与 App 动效系统同管线，潜力更高 |
| 16 | 性能/内存 + 维护成本 | 单 WebView 预热 + 缓存可控；但 WebView 独立渲染进程有内存与首帧成本；CSS/JS 资产需随上游手工更新 | 无 WebView 开销、滚动与列表天然 Compose；每加语言只加 TextMate JSON（零代码）；表格/details 自研需维护；mikepenz 升级有槽位风险 |

## 2. 路线建议

**推荐 C：混合起步 → 向 B 演进。**

- 当前 `feature:repo` 已是混合结构：普通 README 原生、复杂 README WebView 兜底。保留它，把 A 的融合链与 B 的增强组件继续打磨，用户可用真机对照后逐步提高「原生可用内容」边界。
- A 已经用很小的成本把 Material You 与 GitHub CSS 变量打通，适合继续承担 mermaid / KaTeX / 重型 HTML / 超长文档。
- B 已证明表格、代码块工具、Alert、排版收敛可行；剩余缺口集中在 mikepenz 0.38.1 的 details 槽位与行内代码圆角，适合放 roadmap 而不是立即全量切换。
- **理由**：一次性全量 A 会牺牲列表滚动/内存与 Compose 动效；一次性全量 B 会被 GFM 长尾（details/复杂 HTML/长表）拖住。
- **风险**：两套链路样式可能漂移（需同一视觉基准测试）；WebView 真机无闪烁注入与私有图尚未真机验证；mikepenz 上游升级可能改变槽位行为。

## 3. 两条路线人天估算（只做后续产品化，不含本次原型）

| 路线 | 估算 | 主要工作 |
|---|---|---|
| A 打磨到 README 主路径 | 8–12 人天 | 全量 48 role 变量、`addWebMessageListener` 替换 JS bridge、真机无闪烁/深色验证、私有图代理真机验证、mermaid/KaTeX 懒加载、预热与缓存、A/B 视觉基准 |
| B 增强到 README 主路径 | 15–22 人天 | 表格列宽/长表/单元格换行、details 节点级重构或换上游版本、行内代码圆角（SpanStyle 层 0.38 无解，需上游升级/自建 inline 组件）、图片全屏手势、任务列表写回、代码块语言扩展、字号/留白视觉回归 |

## 4. mikepenz 0.38.1 原生缺口清单（实测）

1. **无 details/html 槽**：`custom` 槽能收到 `HTML_BLOCK`，但节点被 GFM 拆成 `<summary>` 首块、正文 PARAGRAPH、`</details>` 尾块；单节点 API 无法干净表达完整折叠语义。
2. **行内代码无圆角**：SpanStyle 层不能给 inline code 背景加圆角；当前只能做到 primary 10% 底 + primary 字（rikkahub 式配色，无圆角）。
3. **表格槽只有整表 node**：无 header/row/cell 槽；本原型用 AST 自绘，列宽等宽、无自动列宽算法。
4. **图片槽无可组合 Modifier**：默认 `MarkdownImage` 不接受 modifier，需包 Box 再裁切/阴影；点击全屏需自建。
5. **Alert 无槽**：沿用 blockQuote 槽文本解析方案，多行嵌套复杂 blockquote 时仍有边角。
6. **语法高亮语言覆盖取决于资产**：renderer-code 的 Highlights 不直接用，当前 KotlinTextMate 仅 7 语言；其余代码块走样式兜底。

## 5. 关键测试与命令

- `MaterialYouFusionMapperTest` / `WebViewHtmlBuilderFusionTest` / `GitHubTextMateThemeTest`
- `MarkdownTableParserTest` / `HtmlDetailsParserTest` / `GitHubAlertParserTest` / `CopyFeedbackStateTest`
- `ReadmeComparisonScreenshotTest`（B light/dark 基准）、`WebViewHeadlessHtmlWriterTest`（A 截图 HTML 生成）
- B 基准记录：`./gradlew :prototype:readme-comparison:recordRoborazziDebug --tests "*ReadmeComparisonScreenshotTest"`


## 真机验证补充（2026-08-16，prototype 分支 4+ 小时逐项）

原始报告基于 Robolectric/headless 截图；以下为真机（vivo，系统 WebView）验证后的修正与结论：

### 与报告不同的实测结论

| 报告项 | 真机实测 |
|---|---|
| 行内代码圆角「0.38.1 无解」 | **有解**：`markdownExtendedSpans` + `RoundedCornerSpanPainter`（topMargin 按字号/行高计算居中） |
| 表格「Robolectric 假阴性」 | 真机 widthIn(min) 列宽塌缩为 0 → 官方 `requiredWidth` 组件 |
| WebView 背景色 | 内联 CSS 生效但 **真机 WebView 不支持 color-mix()** → 全部背景透明 → **Kotlin 预计算混色变量** |
| WebView 图片 | DOMPurify URI 白名单**删除相对路径 src** → 离线 raw 图片改写绝对 URL |
| 原生徽章 | Coil 3 无 SVG 解码器 → coil-svg；SvgDecoder intrinsic ≈ 声明尺寸 10 倍 → 固定高 20dp；HTML 形态需 HtmlBadgeParser |

### 路线结论（用户拍板）

**原生主渲染 + WebView 兜底**（ADR-0007）：原生补齐表格/列表/徽章/details/Alert 后足以主渲染，
复杂 HTML（mermaid/数学/svg 等）走 WebView。原型壳保留（app debug 入口 + prototype 模块），
后续真机验收/回归可复用。
