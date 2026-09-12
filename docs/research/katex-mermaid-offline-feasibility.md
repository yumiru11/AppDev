# KaTeX / Mermaid 离线渲染可行性评估（含实测体积）

> 调研日期：2026-09-12 · 状态：**结论已出（DO：分阶段引入，见 §8/§9）**
> 代码事实基于 worktree `docs/katex-mermaid-feasibility`（main@`d00533f`）。
> 全部体积数字均为 **2026-09-12 实际下载 + `gzip -9` 实测**（URL/版本/摘要见 §2、§11），非估算；浏览器行为为 Node + jsdom 实测；解析耗时为开发机 Node 26 实测（标注 [实测-开发机]），低端机推算一律标注 [推断]。

---

## 结论摘要（TL;DR）

1. **判定：DO（分阶段）**。离线数学（KaTeX）与图表（Mermaid）都值得做，但必须：
   - 用**清洗后渲染（post-sanitize pass）**接入，**不得**放宽 `PURIFY_CONFIG` 的 `FORBID_TAGS/FORBID_ATTR`（§3.4 有实测证据：放宽 `style` 会全局削弱 sanitizer；而 `ADD_TAGS/ADD_ATTR` 在 `FORBID_*` 存在时**无效**）。
   - Mermaid **不要用 12.0.0**（发布仅 2 天、官方文档明示 **ES2024 / Safari 17.4+**、体积 1.59 MiB gzip、强绑 ELK）。推荐 **`@mermaid-js/tiny` 11.17.2**（2.56 MB raw / **672 KB gzip**，成熟 v11 线）。
   - 用现有 `FeatureDetector` 作为**是否注入脚本的开关**（它已能识别 MATH/MERMAID，但当前生产代码 0 引用）。
2. **Headline 数字（实测）**：
   - KaTeX 0.18.7 全量（js + css + 20×woff2 字体）：**557,295 B raw / ~339.7 KB deflated，APK 增量 ≈ +0.33 MiB**。
   - Mermaid Tiny 11.17.2（单文件 IIFE，无动态 import）：**2,555,146 B raw / 672,490 B gzip，APK 增量 ≈ +0.64 MiB**。
   - 两者合计 **≈ +0.97 MiB APK，按需加载（仅检测到数学/图表才解析执行）**。
3. **现状真正的缺口（实测推翻了审计里的一条假设）**：GitHub 服务端 HTML **并不渲染** KaTeX/Mermaid——`POST /markdown` 实测返回 `<math-renderer class="js-inline-math" style=...>$E=mc^2$</math-renderer>` 与 `<div class="highlight highlight-source-mermaid"><pre>…`。即：README 走服务端 HTML 时，数学与图表同样是**裸文本/高亮代码块**。`spec-audit-2026-09-11.md:104` 的「README 走服务端 HTML 时由 GitHub 出图 ✅」**不成立**（§3.5）。
4. **必经的改动面**：`renderer.js` 新增清洗后渲染钩子、`WebViewHtmlBuilder.runtimeScripts` 增加条件脚本、`FeatureDetector` 接线、**改写一个负向守卫测试**（`WebViewOfflineGfmCapabilityTest.kt:71-82` 现在断言 assets 里**禁止**出现 katex/mermaid 字样）、更新两个 fixture 的「未实现」注释。
5. **明确「不做」红线**（任一命中则停）：APK 增量预算 < 0.4 MiB；无法保证 WebView ≥ Chromium 94（Mermaid 11/12 都含 class static block，语法级失败不能 try/catch）；或唯一实现路径是全局放开 `style` 清洗（§6.4/§9）。

---

## 1. 问题与范围

### 1.1 背景

- Markdown 主渲染通道：`core/markdown/src/main/assets/webview/`（github-markdown-css 5.9.0、highlight.js 11.11.1、markdown-it 14.1.0、DOMPurify 3.2.4），HTML 由 `WebViewHtmlBuilder` 组装，Material You 主题变量由 Kotlin 注入。
- 现状：assets 内**没有** KaTeX/Mermaid 运行时；`FeatureDetector` 能识别 `MATH`/`MERMAID` 但生产代码 0 引用。README 优先服务端 HTML、失败降级 markdown-it；Issue/PR 正文与编辑器预览恒走离线 markdown-it。
- 已拍板的两个硬约束：**离线可用**（渲染期无 CDN）、**Material You 主题**（真机 WebView 不支持 `color-mix`，混色必须 Kotlin 预计算）。

### 1.2 本文回答的问题

1. 实测体积到底多大（含字体子集化问题）？
2. 在 WebView 离线管线里怎么接（`<script src>` vs 懒注入、清洗前 vs 清洗后、与插件机制的关系）？
3. 检测策略（`$$…$$`、`$…$`、` ```mermaid `），如何避免 shell `$` 误判？
4. 体积/首帧/内存预算与分阶段方案，以及明确的「不做」阈值。
5. 风险：CSP、脚本注入安全（token 绝不进 WebView）、Mermaid 主题、离线正确性。
6. 最终 DO / DON'T 与理由。

### 1.3 不在本文范围

- 不实现任何渲染代码、不改依赖（research-only）。
- 不评估服务端渲染（GitHub 侧已证伪，见 §3.5）。

---

## 2. 实测体积（2026-09-12）

### 2.1 测量方法

- 取 npm `latest` 版本（`registry.npmjs.org/katex/latest`、`/mermaid/latest`），从 **jsDelivr** 下载 dist 产物；字体从 `/dist/fonts/*.woff2` 全量下载。
- raw = `stat -c%s`；gzip = `gzip -9 -c file | wc -c`（与 APK zip deflate 的压缩量级等价）。
- 主产物记录 sha256 前 16 位以便复现。
- 命令与 URL 见 §11。

### 2.2 主表（全部为实测值）

| 产物 | 版本 | 下载 URL（jsDelivr） | raw (B) | gzip (B) | sha256(16) |
|---|---|---|---|---|---|
| KaTeX `katex.min.js` | 0.18.7 | `.../katex@0.18.7/dist/katex.min.js` | 272,715 | **75,880** | `10a91b479cd92744` |
| KaTeX `katex.min.css` | 0.18.7 | `.../katex@0.18.7/dist/katex.min.css` | 24,788 | **3,509** | `50d9c78e03da144a` |
| KaTeX 字体 20 × `.woff2` | 0.18.7 | `.../katex@0.18.7/dist/fonts/*.woff2` | **259,792** | 260,280（已压缩，gzip 无收益） | — |
| KaTeX 小计（js+css+woff2） | | | **557,295** | **≈339,669** | |
| KaTeX `contrib/auto-render.min.js`（可选） | 0.18.7 | `.../dist/contrib/auto-render.min.js` | 3,486 | 1,583 | — |
| KaTeX `contrib/mhchem.min.js`（可选，化学式） | 0.18.7 | `.../dist/contrib/mhchem.min.js` | 33,706 | 8,410 | — |
| Mermaid `mermaid.min.js`（IIFE，全量） | 11.17.2 | `.../mermaid@11.17.2/dist/mermaid.min.js` | 3,572,661 | **976,006** | — |
| Mermaid `mermaid.min.js`（IIFE，全量） | 12.0.0 | `.../mermaid@12.0.0/dist/mermaid.min.js` | 5,575,485 | **1,594,745** | `28fca7ae6ebc7ed7` |
| **Mermaid Tiny `mermaid.tiny.js`**（推荐） | **11.17.2** | `.../@mermaid-js/tiny@11.17.2/dist/mermaid.tiny.js` | 2,555,146 | **672,490** | — |
| Mermaid Tiny `mermaid.tiny.js` | 12.0.0 | `.../@mermaid-js/tiny@12.0.0/dist/mermaid.tiny.js` | 2,898,960 | 774,936 | — |
| Mermaid ESM 入口（仅入口） | 12.0.0 | `.../mermaid@12.0.0/dist/mermaid.esm.min.mjs` | 30,198 | 11,276 | `6bd10ac3b89c1960` |

Mermaid ESM 构建的**懒加载分片总量**（不能只看入口）：

| 构建 | 分片目录 | 文件数 | 分片 raw 合计 |
|---|---|---|---|
| mermaid 11.17.2 | `dist/chunks/mermaid.esm/` | 103 | 7,045,348 B |
| mermaid 12.0.0 | `dist/chunks/mermaid.esm/` | 104 | 11,487,063 B |
| mermaid 12.0.0 | `dist/chunks/mermaid.esm/elk-QHHEJSRN.mjs` 单文件 | 1 | 3,747,694 B |

两个 IIFE 单文件（`mermaid.min.js` / `mermaid.tiny.js`）实测 **`import(` 出现 0 次**（自包含、无动态分片），这是离线 WebView 唯一稳妥的形态；ESM 懒加载分片在 `WebViewAssetLoader` 下既重（7–11 MB）又需要模块解析支持，**不建议**。

补充实测（跨版本对照）：

- `mermaid.min.js`（全量）内嵌 KaTeX 代码：11.17.2 含 `katex` 字样 23 次、12.0.0 含 24 次；ESM 侧 11.17.2 有 `katex-HNBAO5SE.mjs`（501,213 B）。**若同时引入 KaTeX 与全量 Mermaid，KaTeX 会被重复打包一份**；Tiny 构建明确不含 KaTeX 运行时（仅 8 处引用串）。
- Tiny 构建官方描述：不含 Mindmap、Architecture、KaTeX、懒加载、ELK 布局引擎；遇到 ELK 布局请求会回退 Dagre 仍可渲染。常用类型（flowchart / sequence / class / state / ER / journey / gantt / pie / quadrant / requirement / gitgraph / sankey / xy / block / packet / kanban / timeline / radar / treemap / venn / ishikawa / wardley / cynefin / treeView / C4）在 Tiny 中保留（实现时应逐类核验）。

### 2.3 与现有 assets 对照 / APK 影响

现有 `assets/webview/` 全量（8 文件）实测：

| 文件 | raw (B) | gzip (B) |
|---|---|---|
| `github-markdown.css` | 31,068 | 5,471 |
| `highlight.min.js` | 127,496 | 42,792 |
| `markdown-it.min.js` | 123,618 | 44,671 |
| `purify.min.js` | 22,216 | 8,490 |
| `markdown-you.css` | 14,731 | 3,913 |
| `highlight-theme.css` | 1,746 | 672 |
| `renderer.js` | 41,714 | 16,085 |
| `github-markdown-css.LICENSE` | 1,117 | 688 |
| **合计** | **363,706** | **≈122,782** |

APK 影响算法 [事实 + 推断]：项目当前**没有** `noCompress`/`androidResources` 策略（`core/markdown/build.gradle.kts` 无相关块，`app/build.gradle.kts:146-150` 仅排除 META-INF）。因此 `.js/.css` 在 APK 内被 deflate，增量 ≈ gzip 值；`.woff2` 本身已压缩，增量 ≈ raw 值。

| 引入项 | APK 增量（≈） | 备注 |
|---|---|---|
| KaTeX（js+css+woff2） | **+0.33 MiB** | 字体占 259,792 B 的大头 |
| Mermaid Tiny 11.17.2 | **+0.64 MiB** | 单文件 deflate |
| 二者合计 | **+0.97 MiB** | |
| （对照）Mermaid 全量 11.17.2 | +0.93 MiB | 还内含一份 KaTeX |
| （对照）Mermaid 全量 12.0.0 | +1.52 MiB | 强绑 ELK，不建议 |

注意两点：

1. **assets 在 APK 里「按需加载」并不能减小 APK**——文件始终随包分发、所有设备都要下载（AAB 不按设备拆分 assets）。「按需」节省的是**运行时解析/执行**，不是安装包体积。
2. 现状 webview assets 增量约 0.12 MiB；KaTeX+Tiny 使其膨胀约 9 倍，但绝对值仍在 1 MiB 量级，对「轻量」定位可接受（需在 nightly 的 APK 体积回归任务里设阈值）。

### 2.4 KaTeX 字体子集化问题

- KaTeX 的 20 个字体覆盖不同语义族：`Main`（正/斜/粗）、`Math`（数学斜体）、`AMS`（`\mathbb` 等黑板书）、`Caligraphic`/`Fraktur`/`Script`（花体/哥特/手写）、`SansSerif`、`Size1–4`（大运算符/根号等）、`Typewriter`。
- 全量 woff2 = **259,792 B**。CSS 同时列了 `.woff`（≈303,116 B）与 `.ttf`（≈513,664 B）回退；本项目 minSdk 26（Chromium ≥58，woff2 自 Chromium 36 起支持）→ **只打包 woff2 即可**，裁掉 woff/ttf 可避免约 816 KB 无用文件（字体基本不可再压缩）。
- **子集化（pyftsubset）不推荐**：最大可省也就 100–150 KB（且需按族裁剪），但数学字形依赖 Unicode 数学区，裁掉 `AMS`/`Size*`/`Math` 会静默渲染错误；除非有硬性预算，否则不值。
- 字体是**按需加载**的：CSS `@font-face` 的 woff2 只有在页面实际用到对应字形时才请求，典型页面只拉 1–4 个文件（约 30–90 KB），不会一次拉满 260 KB。

### 2.5 版本新鲜度与兼容性（实测证据）

| 库 | 最新版 | 发布日期 | 兼容性实测/官方声明 |
|---|---|---|---|
| KaTeX | 0.18.7 | 2026-09-06（0.18.0 2026-07-17） | bundle 内 `??=`/`static{`/`withResolvers` 均为 0 → ES5 级安全，老 WebView 可解析 |
| Mermaid | 11.17.2 | 2026-08-25 | 含 class static block（`static{` 536 处）→ 需 Chromium ≥ 94；`??=` 10 处（≥85）、`.at(` 18 处（≥92） |
| Mermaid | 12.0.0 | 2026-09-10（**仅 2 天**） | 官方 usage 文档明示：**v12 bundle 目标 ES2024、面向 Safari 17.4+**；`static{` 596 处；ELK 默认内置 |

结论：KaTeX 可直接用；Mermaid 需 **Chromium ≥ 94 的版本门禁 + 低于则回退代码块**（class static block 是**语法错误**，整个脚本无法解析，不能靠文件内 try/catch 兜底），并优先选 **v11.17.2 而非 2 天大的 v12**。

---

## 3. 现状流水线（代码事实）

### 3.1 两条通道与数据流

- **SERVER_HTML**：`ReadmeApi`（`core/github-rest/.../ReadmeApi.kt:33-39`，`Accept: application/vnd.github.html+json`）→ Kotlin `HtmlSanitizer` 正则预清洗 → 组装 HTML → renderer.js `init()` → DOMPurify。
- **OFFLINE_MARKDOWN_IT**：issue/PR 正文、编辑器预览恒走此通道；README 服务端失败时降级。原始 markdown 以属性形式进入 WebView（`WebViewHtmlBuilder.kt:175-189`，`data-markdown-raw`，HTML 属性转义），renderer.js 读回（`renderer.js:806-808`）后用 markdown-it 渲染。
- README 数据模型 `ReadmeContent` 同时持有 `markdown` 与 `html`（`feature/repo/.../RepoRepository.kt:184-239,504-513`）；`RepoDetailScreen.kt:1745-1755` 只把 `content`（html）传给 WebView，所以 SERVER_HTML 模式下 raw markdown 不进 WebView，OFFLINE 模式进。
- 调用点：README（`RepoDetailScreen.kt:1745-1755`）、Issue 正文（`IssueDetailScreen.kt:498-512`，恒 OFFLINE）、PR 正文（`PullRequestTabContent.kt:164-177`，恒 OFFLINE）、编辑器预览（`MarkdownEditorScreen.kt:132-139`，恒 OFFLINE）。评论/时间线仍为原生 `MarkdownViewer`（铁律「评论列表绝不用 WebView」未变）。

### 3.2 离线通道的精确顺序（决策关键）

```
markdown-it 渲染（5 个内置插件在 md.core/走 token 规则）
  → appendFootnotes → addImageLoadingAttributes → rewriteRelativeUrls（字符串）
  → container.innerHTML = html            （renderer.js:815-817，插件产物先落 DOM）
  → sanitizeNode(root)                     （renderer.js:851-852；DOMPurify 清洗 + 重写 innerHTML）
  → decorateImages → bind* → observeHeight → highlightCodeBlocks
```

即：**插件产物在 DOMPurify 之前产出、之后被清洗，并被 `node.innerHTML = cleaned` 整体重序列化**。这决定了：

- 若把 KaTeX/Mermaid 做成 markdown-it 插件，其产物会先经过当前 `PURIFY_CONFIG`（§3.4 实测会被剥离样式）；
- Mermaid 需要真实 DOM 度量与异步渲染，**不能**在字符串阶段的 markdown-it 插件里可靠完成；
- `innerHTML = cleaned` 会销毁先前挂上的 JS 状态/监听器 → Mermaid 必须在此步之后运行。

### 3.3 插件机制：没有注册 API

- `window.__appdevMarkdownPlugins`（`renderer.js:825-839`）**不是注册接口**，只是给 Node 测试用的导出（内置插件函数 + 辅助函数）。
- 真实插件表硬编码在 `createMarkdownIt()`（`renderer.js:773-782`）：`md.use(githubAlertPlugin/taskListPlugin/emojiPlugin/anchorPlugin/footnotePlugin)`，无 `register()`、无 `afterRender` 生命周期。
- 存在**负向守卫测试** `WebViewOfflineGfmCapabilityTest.kt:71-82`：断言 assets 文件名与 `renderer.js`/`markdown-it.min.js` 内容中**不得出现** `katex`/`mermaid`。任何引入都会先打破它，必须改为正向断言。
- 两处 fixture 现标注「未实现」：`MarkdownGfmFixtures.kt:256-267`；对应 `docs/agents/markdown-consistency-2026-09-11.md:85-86,140-143`。

### 3.4 DOMPurify 配置与实测存活矩阵（本文最重要的一节）

`renderer.js:29-40` 实际配置：

```js
var PURIFY_CONFIG = {
  FORBID_TAGS: ['script', 'iframe', 'object', 'embed', 'form', 'style'],
  FORBID_ATTR: ['onerror', 'onload', 'onclick', 'onmouseover', 'style'],
  ALLOWED_URI_REGEXP: /^(?:(?:https?|mailto):|[^a-z]|[a-z+.-]+(?:[^a-z+.-:]|$))/i
};
```

实测方法：载入**仓库内**的 `purify.min.js`（DOMPurify **3.2.4**）到 jsdom 26.1.0，用上面的配置对真实产物做 `sanitize`：

| 输入 | 指标 | 原样 | 当前配置清洗后 | `ADD_ATTR:['style']` | 从 `FORBID_*` 移除 style |
|---|---|---|---|---|---|
| KaTeX `renderToString(display)` | `style=` 属性数 | 25 | **0** | **0（FORBID 优先，ADD 无效）** | 25 |
| Mermaid 代表性 SVG | `<style>` / `style=` | 1 / 4 | **0 / 0** | 0 / 0 | 1 / 2 |
| GitHub `POST /markdown` 片段 | `<math-renderer>` 数 | 4 | **0** | — | — |

关键结论（实测，非推断）：

1. 当前配置会**剥离 KaTeX 的全部内联样式**（25/25）与 Mermaid 的内联 `<style>`——数学排版与图表配色会被破坏。
2. `ADD_ATTR:['style']` / `ADD_TAGS:['style']` **无法**在 `FORBID_ATTR/FORBID_TAGS` 含 `style` 时生效（DOMPurify 中 `FORBID_*` 优先级更高）。
3. 唯一能让样式存活的「放宽」方式是**从 `FORBID_*` 移除 `style`**——这会全局削弱 sanitizer（CSS 注入面），**不应采用**。
4. DOMPurify 默认白名单**保留** `<svg>`/`<math>`/`<rect>`/`<path>`（KaTeX 的 MathML 部分与 Mermaid 的 SVG 骨架能活），但样式全丢。
5. GitHub 片段里的自定义元素 `<math-renderer>` 被整体移除，留下裸文本 `$E=mc^2$`——即当前 SERVER_HTML 路径用户看到的是 **未渲染的 LaTeX 源码**。

因此推荐 **方案 B：清洗后渲染**（§4），让库产出的可信 DOM 完全不经过 DOMPurify，既保住输出，也不动安全配置。

### 3.5 GitHub 服务端是否已渲染数学/图表（实测：否）

对 `POST https://api.github.com/markdown`（GFM）实测，输入 `$E=mc^2$`、`$$\int_0^1 x^2 dx$$`、` ```mermaid graph TD A-->B `：

```html
<p>Inline <math-renderer class="js-inline-math" style="display: inline-block"
   data-run-id="…">$E=mc^2$</math-renderer> and block:</p>
<p><math-renderer class="js-display-math" style="display: block"
   data-run-id="…">$$\int_0^1 x^2 dx$$</math-renderer></p>
<div class="highlight highlight-source-mermaid"><pre class="notranslate">
<span class="pl-k">graph</span> <span class="pl-c1">TD</span> …
</pre></div>
```

- 数学：GitHub 返回 `<math-renderer>` 自定义元素 + 原始 `$…$` 文本，靠 github.com 前端脚本水合（MathJax）。App 的 WebView 不加载该脚本 → **不渲染**；且该元素还会被 DOMPurify 移除。
- Mermaid：GitHub 返回的是**语法高亮代码块**（`highlight-source-mermaid`），不是图；github.com 前端用 mermaid.js 客户端渲染。App 不加载 → **不渲染**。
- README 同源管线（`GET /repos/{o}/{r}/readme` + `html+json` 属同一渲染器；`mermaid-js/mermaid` README 实测无服务端渲染标记）。

所以审计 `spec-audit-2026-09-11.md:104` 的「GitHub 出图 ✅」判断应更正为 ❌；这也是本文判定「KaTeX/Mermaid 值得做」的正当性依据：它们不是服务端能力的重复，而是**所有通道共同的真实缺口**。

### 3.6 脚本加载与安全边界

- 资产经 `WebViewAssetLoader` 以 `https://appassets.androidplatform.net/` 域提供（`WebViewMarkdownRenderer.kt:95-102`），`loadDataWithBaseURL` 同源基址（`:226-232`）；无 `file://`。
- 条件脚本已有先例：`WebViewHtmlBuilder.runtimeScripts`（`:138-152`）按 `RenderMode` 与 `containsCodeBlock()`（`html.contains("<pre")`）决定是否加载 markdown-it / highlight.js——KaTeX/Mermaid 的开关可复用同一模式。
- 首帧注入：`addDocumentStartJavaScript` 绑定 origin `https://appassets.androidplatform.net`（`WebViewMarkdownRenderer.kt:186-196`），主题切换时用 `evaluateJavascript` 重放（`:233-238`）。
- **无 CSP**：全仓无 `Content-Security-Policy`/`http-equiv`（仅注释提及）；HTML 只有 charset/viewport/color-scheme meta。
- **token 绝不进 WebView**：Token 仅由 `PrivateImageInterceptor` 加到**原生 OkHttp 请求头**（`PrivateImageInterceptor.kt:55-68`）；`WebViewHtmlBuilder.kt:45-49` KDoc 与 `renderer.js:20-21` 注释均声明；Bridge 是 5 方法白名单（`MarkdownBridge.kt:45-92`）。主题 startScript 只注入 `data-theme` + hex 色值（`MaterialYouFusionMapper.kt:118`）。
- `WebSettings` 已锁：`allowFileAccess/allowContentAccess/domStorage/database = false`、无 filr URL 跨源（`WebViewSecurity.kt:32-56`）。

### 3.7 主题注入（供 Mermaid/KaTeX 复用）

- 双通道：HTML 内联 `<style id="theme-vars">`（`WebViewHtmlBuilder.kt:115-119`）+ `addDocumentStartJavaScript` 首帧脚本（`MaterialYouFusionMapper.buildStartScript:120-139`）。
- 变量命名：`--md-sys-color-*`（34 个 M3 role）、`--fgColor-*`/`--bgColor-*`/`--borderColor-*`、`--color-prettylights-syntax-*`、`--fontStack-sansSerif/monospace`、`--md-sys-shape-corner-*`、预混背景 `--alert-*-bg`/`--code-bg`/`--inline-code-bg`（`MaterialYouFusionMapper.kt:173-295`）。
- 深浅切换：`isDark = isSystemInDarkTheme()`，`update()` 会整页 `loadDataWithBaseURL` 重载并重放 startScript（`WebViewMarkdownRenderer.kt:81-90,226-238`）→ Mermaid 可在每次重载时按当前主题重新渲染；KaTeX 颜色基本继承 `currentColor`，只需把 `errorColor` 指向主题 error 色。

---

## 4. 集成方案对比

| 维度 | 方案 A：markdown-it 插件（清洗前产出） | 方案 B：清洗后渲染（推荐） |
|---|---|---|
| 接入点 | `createMarkdownIt()` 增 `md.use(...)`；产物先落 DOM（§3.2） | `sanitizeNode(root)` **之后**新增 `renderMath(root)` / `renderMermaid(root)` |
| 与 DOMPurify 关系 | 产物必经当前配置 → 样式被剥离（§3.4） | 产物不经过 sanitizer；安全靠库自身默认（KaTeX `trust:false`、Mermaid `securityLevel:'strict'`） |
| 需否放宽 sanitizer | 需（且 `ADD_*` 无效，只能去掉 `FORBID_*` 的 style）→ **安全回退，否决** | 不需要，`PURIFY_CONFIG` 保持不变 |
| Mermaid 可行性 | 字符串阶段无法度量/异步，且 `innerHTML=cleaned` 会清掉状态 → 不可靠 | 在真实 DOM 上 `mermaid.run()`，天然成立 |
| 重序列化影响 | 会被 `node.innerHTML = cleaned` 破坏 | 在重序列化之后，不受影响 |
| 主题切换 | 需在字符串阶段拿到色值 | 可在渲染时读 `getComputedStyle` 计算色值，随整页重载重跑 |
| 触发/加载 | 仍需 Kotlin 侧决定是否注入脚本 | 同左 |
| 测试可用性 | Node 端可测字符串 | 可测 DOM 后 pass；Roborazzi 出帧 |

**结论：采用方案 B**，并在 `WebViewHtmlBuilder.runtimeScripts` 里按检测结果条件注入 `<script src="…/katex.min.js">` / `<script src="…/mermaid.tiny.js">`（与现有 highlight.js 条件加载同构）。

渲染职责划分（渲染期无 CDN、无 eval、无远程请求）：

- KaTeX：对 `root` 做文本节点遍历，命中行内 `$…$` / 块级 `$$…$$` 后，`katex.render(tex, el, { throwOnError: false, displayMode, trust: false })`；输出为 DOM，不写 `innerHTML`。
- Mermaid：收集 `pre > code.language-mermaid`（离线通道）与 `div.highlight-source-mermaid > pre`（SERVER_HTML 通道，实测形状）两类节点，`mermaid.initialize({ startOnLoad: false, securityLevel: 'strict', htmlLabels: false, theme: 'base', themeVariables: <从 CSS 变量解析的 hex> })`，再 `mermaid.run({ nodes, suppressErrors: true })`。

---

## 5. 检测策略（触发规则，避免 shell `$` 误判）

现状 `FeatureDetector.kt:70-81`：

```kotlin
MERMAID_FENCE_REGEX = Regex("""```mermaid\b""", IGNORE_CASE)
FENCED_CODE_REGEX   = Regex("""```[^\n]*\n.*?```""", DOT_MATCHES_ALL)
MATH_REGEX          = Regex("""\$\$[^\$]+\$\$|\$[^\$\d\s][^\$]*[^\$\s]\$""")
```

它的 `MATH` 判定会**先剥离围栏代码块**（`stripFencedCodeBlocks`）——正是为了干掉 shell 里的 `${var}`/`$counter`。优点：已排除 `$` 后跟数字/空白、并要求收尾 `$` 前非空白（`$100 and $200`、`$HOME` 不命中）。不足与建议：

1. `MATH_REGEX` 的 `[^\$]*` **可跨行**；行内公式不应跨行 → 收紧为 `[^\$\n]*`，与 JS 侧保持一致。
2. JS 侧检测应基于**清洗后的 DOM 文本节点**，天然跳过 `pre/code/script/style/textarea`；这比纯文本正则更强（`` `echo $HOME` `` 与 ``` 围栏都不误判）。
3. 在 JS 侧再加两条防误判约束：
   - 行内：开头 `$` 前不得是字母/数字（避免 `US$5` 之类），`$` 后非空白，收尾 `$` 前非空白、后不接字母数字；
   - 块级：`$$…$$` 允许换行（`DOTALL`），优先于行内匹配。
4. `FeatureDetector` 的角色是**是否注入脚本的开关**（Kotlin 侧、在组装 HTML 前决策），不是正确性判据；JS 侧仍需独立、严格的扫描（防御性双保险）。
5. Mermaid 触发：Kotlin 用 ` ```mermaid ` 围栏；JS 侧同时接受 `code.language-mermaid`（离线）与 `div.highlight-source-mermaid > pre`（服务端 HTML），并可用 `mermaid.parse()` 预校验、失败则保留原代码块。
6. 不要使用 `renderMathInElement` 的全文档盲扫作为主路径（其 `$` 规则更宽松）；如采用必须在 `ignoredTags` 中列出 `pre/code` 等。现有 `FeatureDetector` 的规则应作为单一事实来源同步到 JS。

---

## 6. 性能与体积预算

### 6.1 APK / 下载

- KaTeX +0.33 MiB；Mermaid Tiny 11.17.2 +0.64 MiB；合计 **+0.97 MiB**。
- 字体按需加载（典型 30–90 KB/页），不受整包体积影响。
- 无 `noCompress` 策略：若改为 `noCompress += "js"` 以换读取速度，APK 会增大（Mermaid 需存 2.5 MB 而非 0.67 MB）→ **不建议**；保持默认压缩。
- 资产读取成本：每次页面加载由 asset loader 解压交付（`LOAD_NO_CACHE`，`WebViewSecurity.kt`），Mermaid 2.5 MB 解压属可接受量级；用「按需注入」避免无图表页面付出该成本。

### 6.2 首帧成本 [实测-开发机 + 推断]

开发机 Node 26 实测（`vm.runInNewContext`，含解析 + 早期执行，mermaid 因缺 `document` 提前抛错，故数字含部分执行）：KaTeX `katex.min.js` 266 KB ≈ 4.4 ms；`mermaid.min.js` 11.17.2 3.49 MB ≈ 16.9 ms；12.0.0 5.44 MB ≈ 21.3 ms；Tiny 12 2.83 MB ≈ 23.8 ms。（`new vm.Script` 在本机是惰性解析，读数 0 ms，不可用作依据，已弃用。）

[推断] 低端 Android 的 V8 解析/执行约为开发机的 3–5 倍耗时 → KaTeX 约 15–25 ms，Mermaid 约 60–120 ms 解析 + 每张图 100–600 ms 布局/绘制（复杂 flowchart 可能达秒级，ELK 明显更重，这是不建议 v12 的又一理由）。KaTeX 实测 `renderToString`：`E=mc^2` 0.174 ms、`\int` 0.336 ms、含 `\frac+\nabla` 0.423 ms/式（开发机），低端机推算 < 2 ms/式，可忽略。

### 6.3 低端机内存/稳定性

- KaTeX 无状态、开销可忽略。
- Mermaid Tiny 2.5 MB 源码 → 解析后 JS 堆约 20–60 MB [推断]；2 GB 低端机需实测 `onTrimMemory` 行为。建议：单文档最多渲染 N 张图（如 10）、渲染失败保留代码块、不要在滚动中反复 re-render。

### 6.4 明确「不做」阈值

命中任一条即暂停/放弃：

1. APK 增量预算 < 0.4 MiB（连 KaTeX 都放不下）→ 不做。
2. 无法保证 WebView ≥ Chromium 94 且无降级门禁 → **不做 Mermaid**（`static{}` 语法级失败）；KaTeX 仍可做。
3. 唯一实现路径需要全局放开 `style` 清洗（即方案 A）→ **不做**（安全回退）。
4. 若要求「零自动化测试改动」——现有负向守卫与 fixture 必须改，做不到就不做。

---

## 7. 风险清单

| # | 风险 | 评估 | 缓解 |
|---|---|---|---|
| R1 | CSP 缺失 | 现状无 CSP；引入第三方运行时会增大脚本面 | 可选加固：加 `<meta http-equiv="Content-Security-Policy">`，但内联 `<style id="theme-vars">` 与内联样式需 `style-src 'unsafe-inline'`；document-start 注入脚本是否受页面 CSP 约束需真机验证。**不作为前置**，列为后续硬化项 |
| R2 | KaTeX 注入 | `trust:false` 默认禁 `\href`/`\includegraphics`/`\html*`；错误信息可能回显未转义 LaTeX 源码 | 用 `throwOnError:false` + `strict:'ignore'`（或 'warn'）+ `maxSize` 上限；渲染进元素而非 `innerHTML`；**不得**设 `trust:true` |
| R3 | Mermaid 注入 | 未信源（任意仓库 README/issue） | `securityLevel:'strict'`（默认，编码 HTML、禁用点击）+ `htmlLabels:false`；禁止 `loose`/`antiscript`；`mermaid.parse()` 预校验；`suppressErrors:true` |
| R4 | token 泄漏 | 现有隔离良好（token 只在 OkHttp 请求头） | 保持：运行时不读 token；KaTeX/Mermaid 不接受任何 token/URL；不新增 Bridge 方法 |
| R5 | Mermaid 主题（Material You） | Mermaid 把颜色烘进 SVG 内联样式，**不能**直接吃 CSS 变量 | 渲染时用 `getComputedStyle(document.documentElement).getPropertyValue('--md-sys-color-*')` 解析 hex 构造 `themeVariables`；深浅切换依赖整页重载重跑（现状 `update()` 已重载） |
| R6 | Mermaid 字体 | 默认 `trebuchet ms` 在 Android 大概率缺失 → 文本度量偏差、标签溢出 | 显式 `fontFamily` 用 `--fontStack-sansSerif` 解析值；必要时内置 Open Sans/Recursive（体积另计） |
| R7 | 离线正确性 | 必须全本地：IIFE 单文件 + `<script src>` asset loader；禁用 ESM 懒分片 | 已确认 IIFE `import(` 次数为 0；ESM 方案（7–11 MB 分片）否决；无 CDN 引用 |
| R8 | WebView 版本 | Mermaid 需 Chromium ≥94 | Kotlin 侧读 WebView 版本（`WebViewCompat.getCurrentWebViewPackage` 等），低于门槛不注入脚本、回退代码块；记录遥测/日志 |
| R9 | 守卫/文档漂移 | `WebViewOfflineGfmCapabilityTest.kt:71-82` 负向断言、fixtures「未实现」注释、`markdown-consistency`、`spec-audit:104/749` 均会与事实不符 | 实施时同步改写；本文已记录需更正项 |
| R10 | 版本漂移 | `docs/research/webview-material-you-fusion.md:272` 写 DOMPurify v3.4.13，实际 ship 3.2.4 | 顺手更正（独立小改动） |
| R11 | 体积回退 | 无 `noCompress`/体积阈值策略 | 在 nightly APK 体积回归（`nightly.yml:232`）加阈值告警 |

---

## 8. 分阶段实施建议（DO 时）

**Phase 0 — 接线与门禁（无新运行时）**
1. 将 `FeatureDetector` 接入渲染决策（README/Issue/PR/编辑器），产出两个布尔：`hasMath`、`hasMermaid`。
2. `WebViewHtmlBuilder.runtimeScripts` 增加可选的 `katex`/`mermaid` 脚本注入参数（默认关）。
3. 改写 `WebViewOfflineGfmCapabilityTest.kt:71-82` 负向守卫为正向断言（存在性 + 版本 banner + 不含动态 `import(`）。
4. 更新 `MarkdownGfmFixtures.kt:256-267` 与 `markdown-consistency-2026-09-11.md`；更正 `spec-audit:104` 与 DOMPurify 版本漂移。

**Phase 1 — KaTeX（低成本、高收益）**
5. 落地 `assets/webview/katex/katex.min.js` + `katex.min.css` + `fonts/*.woff2`（仅 woff2；CSS 可裁 woff/ttf 回退）。
6. `renderer.js` 在 `sanitizeNode(root)` 之后新增 `renderMath(root)`：文本节点遍历 + 严格 `$…$`/`$$…$$` 规则（§5）。
7. 门禁：`core/markdown` 单测（JS/Node）、`FeatureDetectorTest`、Roborazzi 26 帧（需用户确认 `recordRoborazziDebug`，本机禁跑）。
8. 验收：离线 README/Issue/PR/编辑器四处的 `33-math-katex.md` fixture 正确出式；`$` in code 不误判。

**Phase 2 — Mermaid（Tiny 11.17.2）**
9. 落地 `assets/webview/mermaid/mermaid.tiny.js`；新增 WebView 版本门禁（Chromium ≥94，否则回退）。
10. `renderer.js` 新增 `renderMermaid(root)`：识别 `code.language-mermaid` 与 `div.highlight-source-mermaid > pre`，`mermaid.parse` → `mermaid.run`，失败保留代码块；themeVariables 由 CSS 变量解析。
11. 验收：`34-mermaid.md` fixture 出图；`mermaid-js/mermaid` README 真机走查（对照 `ui-audit` 的 UI-C11 高度塌陷场景）。
12. roboto/主题切换复测：深浅色切换后图表配色跟随。

**Phase 3 — 硬化（可选）**
13. CSP meta + 体积阈值 + 单文档渲染上限 + 失败埋点。

---

## 9. 最终建议（DO / DON'T + 数字）

**DO（分阶段，方案 B 清洗后渲染）**，理由与数字：

- 服务端 HTML 实测**不渲染**数学/图表（§3.5），缺口真实存在且覆盖所有通道；KaTeX/Mermaid 不是重复建设。
- 增量可控且可按需：**KaTeX +0.33 MiB**、**Mermaid Tiny 11.17.2 +0.64 MiB**，合计 **+0.97 MiB**；字体按页 30–90 KB。
- 安全路径清晰：不改 `PURIFY_CONFIG`，用 `trust:false`（KaTeX）与 `securityLevel:'strict'`（Mermaid）在清洗后渲染；token 隔离现状已满足。

**DON'T（条件性否决）**：

- 不要用 Mermaid **12.0.0**（发布 2 天、ES2024/Safari 17.4+、+1.52 MiB、强绑 ELK）。
- 不要把 KaTeX/Mermaid 做成 markdown-it 插件并放宽 `FORBID_*` 的 `style`（实测 ADD 无效、只能全局放开 → 安全回退）。
- 不要用 ESM 懒分片（7–11 MB 且 asset loader 兼容性差）。
- 不要在无法保证 WebView ≥ Chromium 94 时注入 Mermaid（语法级失败）。
- 不要做 KaTeX 字体子集化（最多省 ~100–150 KB，静默破图风险高）。

---

## 10. 未决问题

1. Mermaid Tiny 11.17.2 的**逐图类型覆盖**需在实现时用表格核验（尤其 C4、sankey、architecture 相关）。
2. Mermaid 在 Robolectric Native Graphics 下能否稳定出帧，还是必须真机/CI 截图通道验证。
3. `document-start` 注入脚本与页面 CSP 的交互需真机确认（影响 R1 的加固方案）。
4. 低端机 Mermaid 真实解析/渲染耗时与内存，需一台基线设备实测（本文为开发机 + 推断）。
5. 是否需要 `mhchem`（化学式，+8 KB gzip）——取决于目标读者。

---

## 11. 复现与来源

### 11.1 版本与日期（npm registry 实测）

- `katex` dist-tags latest = `0.18.7`（2026-09-06）；`0.18.0` 2026-07-17；`0.17.0` 2026-05-22。
- `mermaid` dist-tags latest = `12.0.0`（2026-09-10）；11.x 最新 `11.17.2`（2026-08-25）。
- `@mermaid-js/tiny` latest = `12.0.0`；11.x 最新 `11.17.2`。
- 现有资产版本（文件 banner 实测）：highlight.js 11.11.1、markdown-it 14.1.0、DOMPurify 3.2.4；github-markdown-css 5.9.0（`markdown-you.css:2`、`MaterialYouFusionMapper.kt:30/63` 佐证）。
- 许可证：KaTeX MIT、Mermaid MIT、DOMPurify Apache-2.0/MPL-2.0。

### 11.2 复现命令（本次实际执行）

```bash
# 版本
curl -s https://registry.npmjs.org/katex/latest | jq -r .version
curl -s https://registry.npmjs.org/mermaid/latest | jq -r .version
# 主产物 + 体积
curl -sSL -o katex.min.js https://cdn.jsdelivr.net/npm/katex@0.18.7/dist/katex.min.js
curl -sSL -o mermaid.tiny.js https://cdn.jsdelivr.net/npm/@mermaid-js/tiny@11.17.2/dist/mermaid.tiny.js
stat -c%s katex.min.js; gzip -9 -c katex.min.js | wc -c
# 字体全量
for f in $(curl -s "https://data.jsdelivr.com/v1/packages/npm/katex@0.18.7?structure=flat" | jq -r '.files[].name | select(startswith("/dist/fonts/") and endswith(".woff2"))'); do
  curl -sSL -o "$(basename $f)" "https://cdn.jsdelivr.net/npm/katex@0.18.7$f"
done
# GitHub 渲染行为
curl -s -X POST https://api.github.com/markdown -H "Content-Type: application/json" \
  -d '{"text":"$E=mc^2$\n\n```mermaid\ngraph TD\nA-->B\n```","mode":"gfm"}'
```

DOMPurify 存活矩阵：Node + jsdom，载入仓库 `purify.min.js`（3.2.4）与 `renderer.js:29-40` 同款 `PURIFY_CONFIG`，对 KaTeX `renderToString` 输出、代表性 Mermaid SVG、GitHub 片段分别 `sanitize` 后对比 `style=`/`<style>`/`<math-renderer>` 计数（§3.4）。

### 11.3 关键代码来源

- `core/markdown/src/main/assets/webview/renderer.js`：`PURIFY_CONFIG`（29-40）、`createMarkdownIt`（773-782）、离线渲染（795-821）、`init` 顺序（841-870）、测试导出（825-839）。
- `core/markdown/src/main/kotlin/.../webview/WebViewHtmlBuilder.kt`：`ASSET_BASE`（52）、条件脚本（138-152）、`data-markdown-raw`（175-189）、SERVER_HTML 清洗（171-173）。
- `core/markdown/src/main/kotlin/.../webview/WebViewMarkdownRenderer.kt`：AssetLoader（95-102）、document-start（186-196）、`loadDataWithBaseURL`（226-238）。
- `core/markdown/src/main/kotlin/.../webview/FeatureDetector.kt`（70-124）、`WebViewSecurity.kt`（32-56）、`HtmlSanitizer.kt`、`MaterialYouFusionMapper.kt`（102-139,173-295）。
- 守卫与 fixture：`WebViewOfflineGfmCapabilityTest.kt:71-82`、`MarkdownGfmFixtures.kt:256-267`、`.../markdown-fixtures/33-math-katex.md`、`34-mermaid.md`。
- 审计与文档：`docs/agents/spec-audit-2026-09-11.md:103-107,134,216,540,749`、`docs/agents/markdown-consistency-2026-09-11.md:85-86,140-143`、`docs/research/webview-material-you-fusion.md:272`、`plan.md:163-164,242,367`。

### 11.4 与既有审计的差异记录

- `spec-audit-2026-09-11.md:104`：「README 走服务端 HTML 时由 GitHub 出图 ✅」→ 实测**不成立**，应改 ❌（GitHub 只给高亮代码块 / `math-renderer` 占位）。
- `spec-audit-2026-09-11.md:749` 措辞为「需评估体积，可延后」（非「需体积评估」）；本文即该体积评估。
- `docs/research/webview-material-you-fusion.md:272`：DOMPurify 「v3.4.13」→ 实际 3.2.4。
