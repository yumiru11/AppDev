# WebView 融入 Material You：能力边界深度调研报告

> 状态：**调研完成**（2026-08-14，四路并行后台调研 + 一手源验证）
> 背景：AppDev 拟将 WebView 作为 Markdown/README 主渲染路径（github-markdown-css v5.9.0 基底），本报告调研「在保证完整体验前提下，WebView 融入 Material You 能做到何种程度」——主题色、深色模式、动效、全局背景图等美化效果，以及 WebView 自定义能力与外部生态。

---

## 结论摘要（TL;DR）

1. **WebView 融入 Material You 的核心可行性：极高**。github-markdown-css v5.9.0 本身就是 GitHub 官方 CSS 提取物、全 CSS 变量化（131 处 `var()` 引用）、内置 `[data-theme]` + `prefers-color-scheme` 双通道——Material You 融合 = 把 `--fgColor-*`/`--bgColor-*` 等语义令牌映射为 M3 动态色板（Compose colorScheme → CSS 变量），是「纯变量覆盖工程」而非打补丁。
2. **深色模式：基础适配免费**。Android WebView 原生把 Activity `isLightTheme` 映射为 `prefers-color-scheme`；`addDocumentStartJavaScript`（androidx.webkit 1.9+）首帧前注入实现无闪烁（FOIT-free）。
3. **动效：观感可复刻、物理不可复刻**。经典 M3 easing（Emphasized 400ms/200ms/500ms）CSS cubic-bezier 一字不差可复刻；spring 物理需 JS 或放弃（官方提供 spring→curve 换算表）。主题切换用 CSS 变量 transition 或 View Transitions API crossfade。
4. **全局背景图：可行，但有一条硬约束——毛玻璃无法跨越 WebView 边界**。WebView 内容合成在独立 surface，App 侧 RenderEffect 模糊采样不到 WebView 像素；反过来 WebView 内 backdrop-filter 也采样不到透明 WebView 背后的 App 内容（Chromium by design）。推荐「背景图放 Compose 层 + 透明 WebView 透出」。
5. **外部生态成熟**：渲染层 markdown-it v15 / 高亮层 starry-night（GitHub PrettyLights 开源版）/ 主题层 github-markdown-css / 安全 DOMPurify / KernelSU 已有生产级「Compose 动态色→WebView CSS 变量」先例可审计。
6. **硬边界（做不到/不做）**：token 绝不进 WebView JS 上下文；真 spring 物理动效；WebView 与 Compose 动画管线帧同步；属性动画改 layout 属性（性能红线）。

---

## 1. 主题色融合（M3 color roles 全量化）

### 1.1 M3 color roles 完整清单（Compose ColorScheme 48 字段）

androidx 源码 `ColorScheme.kt`（androidx-main）确认当前 **48 个颜色属性**：

| 类组 | 字段 |
|---|---|
| Primary（5） | primary, onPrimary, primaryContainer, onPrimaryContainer, inversePrimary |
| Secondary（4） | secondary, onSecondary, secondaryContainer, onSecondaryContainer |
| Tertiary（4） | tertiary, onTertiary, tertiaryContainer, onTertiaryContainer |
| Background（2） | background, onBackground |
| Surface（5） | surface, onSurface, surfaceVariant, onSurfaceVariant, surfaceTint |
| Inverse（2） | inverseSurface, inverseOnSurface |
| Error（4） | error, onError, errorContainer, onErrorContainer |
| Outline（2） | outline, outlineVariant |
| Scrim（1） | scrim |
| SurfaceContainer（6） | surfaceBright, surfaceDim, surfaceContainer, surfaceContainerHigh, surfaceContainerLow, surfaceContainerLowest |
| Fixed（12） | primaryFixed, primaryFixedDim, onPrimaryFixed, onPrimaryFixedVariant, secondaryFixed×4, tertiaryFixed×4 |

来源：`androidx/compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/ColorScheme.kt`；官方 m3.material.io「26 标准 color roles + add-on roles」。

**对 WebView 的关键**：material-web / material-tokens 的 CSS token 体系恰好是 `--md-sys-color-*`（role）→ 引用 `--md-ref-palette-*`（tonal palette）两层——与 Compose colorScheme 一一对应。故 **48 个 role 全能映射为 CSS 变量**，且有官方 token 命名参照。

### 1.2 动态取色（Monet）→ CSS 变量

- **Android 框架公开暴露 Monet tonal palette**：`@android:color/system_accent1_*`（primary）、`system_accent2_*`（secondary）、`system_accent3_*`（tertiary）、`system_neutral1_*`（neutral）、`system_neutral2_*`（neutralVariant），tone 索引 0/10/50/200/…/1000 对应 M3 tone 100/99/95/…/0——androidx 的 `DynamicTonalPalette.android.kt` 就是读这个（API 31+）；API 34+ 直接暴露完整 48-role scheme。
- **实践路径**：运行时从 `MaterialTheme.colorScheme`（已是动态色）逐字段读值 → 序列化为 `:root { --md-sys-color-primary: #xxxxxx; ... }` 注入。KernelSU 先例只注入 4-5 个（surfaceContainerHigh/Low、onSurface、primary），AppDev 可扩展至全量。
- **material-theme-builder（Google 官方）**：Web 导出物即 `css/theme/light.css` + `dark.css`，结构为 `--md-sys-color-*: var(--md-ref-palette-*)`，与 material-web / material-tokens 同源（`material-foundation/material-tokens`）。

### 1.3 深色模式机制（官方）

- **WebView 原生映射**：`WebView always sets prefers-color-scheme according to isLightTheme`（developer.android.com Webapps dark-theme 文档）→ github-markdown-css 的 `@media (prefers-color-scheme: dark)` 块自动生效，深浅色基础适配零成本。
- **显式属性通道**：JS 设 `document.documentElement.setAttribute('data-theme', 'dark')` → 命中 github-markdown-css 的 `[data-theme="dark"]` 选择器 → 变量切换。
- **androidx.webkit 配置**：`setAlgorithmicDarkeningAllowed(false)` + `DARK_STRATEGY_WEB_THEME_DARKENING_ONLY`（内容自带 prefers-color-scheme，防双重变暗）。
- **防 FOIT**：`WebViewCompat.addDocumentStartJavaScript`（1.9.0+，`WebViewFeature.DOCUMENT_START_SCRIPT`）在文档开始、任何页面脚本之前注入，阻塞页面加载（应保持简短）——首帧前设 data-theme + 变量，无闪烁。更新派 addJavaScriptOnEvent（1.16.0，INJECTION_EVENT_DOCUMENT_START）。
- **M3 双套变量**：`--md-sys-color-*` 本身 light/dark 值不同，Android 侧按当前主题把对应 colorScheme 序列化注入即可（AppDev 现状延续，从 7 变量扩展到完整令牌）。

### 1.4 覆盖边界

- **CSS 变量能覆盖**：全部颜色（含 box-shadow 的阴影色、细线边框色）、圆角、字体族、字号、行高、间距、过渡时长——github-markdown-css 已是变量化，直接覆盖。
- **不能覆盖（WebView 自绘 UI）**：滚动条外观（Chromium overlay scrollbar）、长按选择菜单（需 ActionMode 钩子）、输入框、下载/文件选择 UI、WebView 自身的加载错误页。**但这不在 markdown 内容区，影响面有限**。

---

## 2. 动效融合（M3 motion 参数与 CSS 复刻）

### 2.1 M3 动效规范完整参数（一手源）

**关键背景**：M3 有两套动效系统。AppDev 规范的 enter 400ms/exit 200ms/transient 500ms 对应**经典 easing/duration 系统**（官方已标记「不再维护」）；2025-05 M3 Expressive 引入 **spring 物理系统**。

**经典系统**（m3.material.io/styles/motion/easing-and-duration + androidx MotionTokens.kt 源码确切值）：

| Easing | Duration | 用途 | cubic-bezier（源码值） |
|---|---|---|---|
| Emphasized | 500ms | 屏幕内开始/结束 | `(0.2, 0, 0, 1)` |
| Emphasized decelerate | 400ms | 进入屏幕 | `(0.05, 0.7, 0.1, 1)` |
| Emphasized accelerate | 200ms | 退出屏幕 | `(0.3, 0, 0.8, 0.15)` |
| Standard | 300ms | 功能型 | `(0.2, 0, 0, 1)` |
| Standard decelerate | 250ms | 进入 | `(0, 0, 0, 1)` |
| Standard accelerate | 200ms | 退出 | `(0.3, 0, 1, 1)` |
| Legacy (M2) | — | 旧系统 | `(0.4, 0, 0.2, 1)` |

Durations：Short 50-200ms / Medium 250-400ms / Long 450-600ms / ExtraLong 700-1000ms。

**Spring 系统**（m3.material.io/styles/motion/overview/how-it-works + androidx ExpressiveMotionTokens.kt / StandardMotionTokens.kt）：

- 三要素：stiffness、damping、initial velocity
- 两个 scheme：expressive（过冲回弹）/ standard（最小回弹）
- spatial（位移/旋转/尺寸/圆角，允许过冲） vs effects（颜色/透明度，damping 恒 1.0）
- 实测值：Expressive default spatial = damping 0.8 / stiffness 380；fast = 0.6/800；slow = 0.8/200；effects = damping 1.0 / stiffness 1600/3800/800
- Compose Spring 常量（androidx animation-core API）：DampingRatioNoBounce=1.0, MediumBounce=0.5, LowBouncy=0.75, HighBouncy=0.2；StiffnessLow=200, MediumLow=400, Medium=1500, High=10000

### 2.2 CSS 复刻可行性

- **经典 M3 easing：一字不差可复刻**。CSS `cubic-bezier()` 的 x 坐标限 [0,1] 但 **y 坐标可超出 [0,1]**（MDN），而经典 M3 曲线 y 全在 [0,1] 内，直接 `transition: all 400ms cubic-bezier(0.05, 0.7, 0.1, 1)` 即官方 Emphasized decelerate。
- **Spring：观感可复刻、物理不可复刻**：
  - 官方提供 spring→curve 换算表（m3.material.io/styles/motion/overview/specs）：Expressive default spatial → `cubic-bezier(0.38, 1.21, 0.22, 1)` 500ms（y>1 产生过冲）；官方注明只适用于「无中断、无手势」的动画
  - 多次回弹可用 `linear()` easing（Baseline 2023-12，Chrome 113+）以 20-30 控制点近似阻尼振荡，性能与 cubic-bezier 相同
  - 真 spring 物理（初速度继承、中断重定向）只能 JS 库（Framer Motion/GSAP），主线程开销大
- **WebView 支持**：Android WebView 随 Play 更新、与 Chrome 同源（2026 年 v147-149，官方文档明确特性对齐），linear()/@property/View Transitions/content-visibility/backdrop-filter 全可用。

### 2.3 可落地动效清单

| 动效 | 方案 | 状态 |
|---|---|---|
| 主题切换 | CSS 变量 + `transition: background-color 200ms`（@property 注册使变量可过渡）；或 View Transitions API crossfade（Chrome 111+，`document.startViewTransition()`） | ✅ 做（对应 Compose Crossfade） |
| 页面加载渐显 | opacity keyframes + Emphasized decelerate 400ms | ✅ 做 |
| 图片懒加载渐显 | `loading="lazy"` + onload 触发 200ms opacity | ✅ 做 |
| 代码块/折叠展开 | `grid-template-rows: 0fr→1fr`（可动画，免 max-height 魔数） | ✅ 做 |
| 列表 stagger | `--i` 自定义属性 + `animation-delay: calc(var(--i)*0.1s)`，只动 transform/opacity | ✅ 做 |
| 目录平滑滚动 | `scroll-behavior: smooth`（不影响用户手势，easing 不可调） | ✅ 做 |
| backdrop-filter 过渡 | WebView 76+ 支持，但昂贵 | ⚠️ 仅小面积浮层 |
| JS 真 spring 物理 | rAF/Framer Motion | ❌ 不做（观感已够） |
| WebView 内导航转场联动 Compose NavHost | 两套渲染管线无法同步 | ❌ 不做 |

### 2.4 Compose ↔ WebView 衔接

- AndroidView 包装 WebView 是官方典型用例；`Modifier.graphicsLayer/alpha` 会映射到 View 属性走硬件层
- NavHost 转场（navigation-compose 2.7.0+ enter/exit/pop 四元组）作用于外层 destination，**WebView 内容作为纹理被合成、不参与 Compose 转场**——一致性靠两侧共用同一套 M3 token 数值手动对齐
- 透明 WebView（setBackgroundColor(TRANSPARENT)）可叠 Compose 工具栏
- JS↔Compose 双向：evaluateJavascript（触发 CSS） / addWebMessageListener（回调原生）

### 2.5 性能边界

- 只动画 transform/opacity（合成器属性，GPU 合成）——web.dev 指南、Chromium 文档
- will-change 谨慎：只在即将动画时加、动画完移除；滥用吃 GPU 内存（移动端 512MB，约 50 层封顶）
- 长文档：`content-visibility: auto` + `contain-intrinsic-size: auto 500px`（Baseline 2024-09），跳屏外渲染，避免滚动条跳动
- 观感差异：WebView = 独立渲染进程/管线，与 Compose RenderThread 两套管线，低端机帧同步有细微差异

### 2.6 动效先例

MDN 用 scroll-behavior: smooth；sphinx_rtd_theme issue #1429 论证文档站平滑滚动争议；note.com 文章（M3 Expressive CSS 实现）的 cubic-bezier 值与官方 specs 一致；cssshowcase / animbits 提供纯 CSS stagger 进入动画。

---

## 3. 全局背景图与美化

### 3.1 核心硬约束：毛玻璃无法跨越 WebView 边界（调研最重要发现）

- **WebView 内容合成在独立 surface**（Android 8+ 强制 out-of-process renderer，Chromium 合成器）——App 侧 RenderEffect/Compose blur **采样不到 WebView 像素**
- **反向同样成立**：透明 WebView 内 backdrop-filter 采样不到 WebView 背后的 App 内容。Chromium 「by design」：*"CSS's backdrop-filter only applies to elements within the webpage, and the content beneath the browser window is not included in the computation"*（MicrosoftEdge/WebView2Feedback #4945，Chromium 同引擎；chromium issue 365818856 同）
- **推论**：不存在「双模糊」——两层 blur 分属两个合成域，只会各自失效或只生效一边。**玻璃效果必须在单侧实现**：要么背景与玻璃全在 Compose 层（WebView 透明透出），要么全在 WebView 页面内（页面自身背景 + 页面内 backdrop-filter）

### 3.2 透明 WebView 完整坑清单

| # | 坑 | 详情 |
|---|---|---|
| 1 | 页面背景覆盖 | HTML 必须显式 `body { background: transparent }`，否则默认背景盖掉透明（issuetracker 36925660，2011 年开、2025-12 仍报） |
| 2 | 硬件加速冲突（旧） | ICS/JellyBean 时代 HA 下透明失效/闪黑（SO 7711880、36940822）；现代 Chromium WebView 已基本解决 |
| 3 | setLayerType(SOFTWARE) 陷阱 | 经典 workaround 但**视频停播、滚动性能暴跌**、部分设备模糊文字——**本项目禁止** |
| 4 | 滚动残影/闪烁 | alpha=0 完全透明滚动时闪烁；workaround `Color.argb(1,0,0,0)`（近透明 alpha=1）替代（SO 5003156） |
| 5 | 加载时序（旧） | 老版本 loadUrl 重置背景色，需加载后再 set |
| 6 | 新版回归（活跃） | Chromium 40366722：背景层从透明变不透明（blend alpha 回归），影响所有透明 WebView App——上线须复查 |
| 7 | 文字渲染 | 透明背景下 subpixel AA 不可用（Windows ClearType 案例）；**Android 本就无 RGB 亚像素（FreeType 灰度 AA），影响小**；不要用 SOFTWARE 层 |
| 8 | 单层合成模型 | Android 8+ 强制 out-of-process renderer，独立进程合成 |

### 3.3 CSS 背景能力（MDN 确认）

- 多层背景：`background-image: url(a), url(b)` 逗号分隔、第一层最上、`background-color` 垫底（MDN background / background-image）
- `background-size: cover | contain` 逐层对应（MDN）
- `background-attachment: fixed`：**移动端不可靠**（Chrome Android 跟随滚动再跳回、高度被忽略——css-tricks 有专门文章）；替代：`position: fixed` 独立背景层 + `z-index: -1`；性能：fixed 每帧重绘大区域
- 暗色模式调暗：`prefers-color-scheme` 媒体查询 + `filter: brightness()` 或叠加半透明黑 overlay

### 3.4 backdrop-filter 支持现状

- **支持**：MDN Baseline 「Widely available」2024-09；caniwebview：Android WebView ✓（Chrome 76+ 即支持）
- **已知问题**：
  - 嵌套 backdrop-filter 只对最近 backdrop root 生效（chromium 993644 / SO 60997948）→ 伪元素 workaround
  - **透明 WebView + backdrop-filter：无像素可采 → 模糊无效/黑**（WebView2 #4945、chromium 365818856，见 3.1）
  - 外层任何创建 surface 的属性（如 opacity:0.9）触发 backdrop-filter 失效（chromium 380416865）
  - WebKit 同 bug：webkit 275919（visionOS）
- **Flutter 先例**：flutter_inappwebview #1415 + Flutter engine PR 39244——平台视图(WebView)与 blur 合成域冲突的经典案例

### 3.5 背景图放哪层：权衡表

| 维度 | Compose 层（WebView 透明） | WebView 层（页面背景） |
|---|---|---|
| 滚动跟随 | 固定不动（不随内容滚） | 默认随内容滚；fixed 保持不动但移动端不可靠 |
| 与顶/底栏一致 | ✅ 天然同层 | ❌ 需单独对齐 |
| 玻璃联动 | ✅ Compose 链内（但玻璃条无法模糊 WebView 文本） | ✅ 页面内 backdrop-filter（可模糊页面元素） |
| 暗色模式 | ✅ 程序化切换，无闪烁 | 需 CSS + 注入，防 FOIT |
| 性能 | ✅ 零 WebView 开销 | ❌ 大图 + fixed 每帧重绘 |
| 阅读区稳定性 | ✅ 背景稳定 | 随内容滚，阅读区不稳 |

**推荐**：背景图放 **Compose 层**（全局统一、性能好、暗色可控），WebView `setBackgroundColor(TRANSPARENT)` + 页面 body 透明透出；玻璃效果在 Compose 层（顶/底栏）。若要求「Markdown 内容区自身有玻璃/渐变感」→ 在 WebView 页面 CSS 内实现（此时背景图放 WebView 层，用 position:fixed 背景层）。

### 3.6 先例项目

- **SamZebrado/TransparentFloatingBrowser**：透明 WebView 悬浮窗 + 黑色 DOM 透明化 + 触摸穿透 + Android 12+ overlay opacity 限制（最直接先例）
- **YXX168/Amber-MD**：玻璃态 Markdown 阅读器（Flutter + 玻璃态 UI + 霓虹渐变背景）——美学先例，非透明 WebView 路径但审美可参考
- **ak-asu/read4ever**：Flutter + WebView 无干扰阅读器 + 原生 ActionMode 集成
- **billthefarmer/MarkdownView**：WebView 子类 Markdown 库，CSS/JS 注入
- **vuplex**（Unity WebView）：官方推荐「Canvas 背景图放 RawImage 在透明 WebView 之后」——正是「背景图在 App 层」模式

### 3.7 App 侧渲染约束（影响 AppDev 现有玻璃设计）

- Compose `Modifier.blur`/RenderEffect 只模糊绑定对象**自身**内容，**不包含 WebView/VideoView 等独立 surface 的像素**——「顶栏毛玻璃遮 WebView 滚动内容」在技术上是**采样不到**的
- Haze 等 Compose 玻璃库同样**无法跨 WebView surface** 采样
- 结论：顶栏毛玻璃只能模糊 App 自身层（背景图/上滑工具栏），**WebView 文本滚动到顶栏下方时不穿玻璃**——这是设计上必须接受的妥协（或上下滚动条区域改用不透明色）

---

## 4. 自定义能力与外部生态

### 4.1 Android WebView 自定义程度总览

| 层 | API | 可定制内容 |
|---|---|---|
| 加载 | WebViewClient.shouldInterceptRequest | 任意请求拦截返回自定义 WebResourceResponse——本地资源注入总入口 |
| 导航 | shouldOverrideUrlLoading / onPageFinished / onPageCommitVisible | 链接全走应用内导航、JS 注入时机 |
| 弹窗/JS | WebChromeClient（onJsAlert/onConsoleMessage/onShowFileChooser） | alert/confirm/prompt、console、文件选择 |
| 设置 | WebSettings（textZoom/domStorageEnabled/mixedContentMode/cacheMode…） | 字体缩放（无障碍）、存储、缓存 |
| 本地资源 | androidx.webkit.WebViewAssetLoader | `https://appassets.androidplatform.net/assets/...` 虚拟域名托管 assets，天然 Same-Origin，替代 file:// 的 CORS 与安全坑 |
| JS→Native | addWebMessageListener（androidx.webkit 1.3.0+） | postMessage 受限通信，可限定 allowedOriginRules，无反射面（替代 addJavascriptInterface） |
| Native→JS | evaluateJavascript | 页面加载后注入任意 JS（主题切换、滚动监听） |
| 版本 | androidx.webkit:webkit 1.14.0 | WebViewCompat/WebViewFeature 门控 |

**对 AppDev**：注入三件套 = WebViewAssetLoader（模板/CSS 走虚拟域名）+ addWebMessageListener（链接/主题回调 + origin 白名单）+ evaluateJavascript（变量/脚本）。token 绝不进 WebView JS 上下文。

### 4.2 Markdown → HTML 渲染层

- **markdown-it v15**：27.5M 周下载、21.8k stars、2026-08 活跃。CommonMark 100% + 可插拔 token，`highlight` 选项直接挂钩高亮器
- **marked v18**：62M 周下载、37k stars，最快最轻但插件生态小
- **commonmark.js**：官方参考实现，无 GFM 扩展
- **markdown-it GFM 插件（拼图式）**：markdown-it-github-alerts（antfu，GitHub 兼容 alert 结构 + GitHub 提取配色 CSS）、markdown-it-task-lists、markdown-it-github-headings、mdit-plugins 合集（30+ 插件）
- **GitHub /markdown API 取舍**：`POST /markdown {text, mode: gfm, context: owner/repo}` 让 `#42` 变真实 issue 链接（本地渲染追不上），但 60次/时未认证限制、400KB 上限（raw）、离线不可用 → **本地 markdown-it 为主，/markdown API 仅长文档兜底 + 缓存**

### 4.3 主题化 CSS 开源项目

- **github-markdown-css v5.9.0**（2026-02 发布，8.9k stars，274K 周下载，MIT）：**实测** 131 处 var() 引用 + `[data-theme]` 双通道 + 7 主题文件（light/dark/dimmed/high-contrast/colorblind）；由 generate-github-markdown-css 从 GitHub 网页端提取 token，**上游更新即跟随官方**。⚠️ 单维护者、Snyk 标 Inactive（5 个月无 commit）→ 锁版本 + 自托管产物
- **primer/css**（13k stars）：⚠️ README 首行 **KTLO mode**（keep-the-lights-on）；兄弟仓库 primer/github-syntax-light/dark（官方 .pl-* 高亮 CSS）被 KernelSU 直接引用
- **「Material 化 markdown CSS」现状：GitHub 搜索无成熟项目**——Material You 动态色是每设备每套色板，静态 CSS 无法预制；正确形态 = 运行时把 Compose 动态色板转 CSS 变量注入（KernelSU 做法）→ 这是 AppDev 的自研空白点

### 4.4 代码高亮

| 方案 | 版本/活跃 | 与 GitHub 关系 |
|---|---|---|
| **@wooorm/starry-night** | v3.10.0，1.8k stars，活跃 | **GitHub 闭源 PrettyLights 的开源复刻**：600+ TextMate 语法、产出 `.pl-*` 类、自带 GitHub 无障碍主题集——**与 github-markdown-css 共享 `--color-prettylights-syntax-*` token**（实测 starry-night core.css 消费 30 处，github-markdown-css 定义 30+），主题切换天然同步 |
| Shiki | v4.4.3，13.7k stars，活跃 | VS Code TextMate 同引擎；github-light/dark 主题；双主题输出 `--shiki-dark` 变量——与 github-markdown-css 无 token 互通，暗色需手动联动 |
| highlight.js | v11，25k stars | 官方 github.css/github-dark.css，token 粒度与 GitHub 不一致 |
| Prism | v1，13k stars，半停滞 | 无官方 github 主题，482 open issues |

**选型**：目标「最接近 GitHub 网页端」→ starry-night（token 天然同步）；需要行高亮/diff transformer → Shiki。两者同 TextMate 底层，可共存。

### 4.5 动态主题注入

- **data-theme + localStorage + matchMedia 是标准模式**（head 内同步内联脚本防 FOIT → matchMedia change 事件跟随系统），~10 行原生 JS，**无第三方库需求**（npm 上只有 1-star 玩具）
- **material-web**（11.2k stars）：token 结构 `--md-ref-*`（原始值）→ `--md-sys-*`（语义角色）→ 组件 token，全 CSS 自定义属性——**是「Compose 动态色板→CSS 变量」桥接的良好参照**。⚠️ m3.material.io 官方宣布 MWC 进维护模式（Expressive 未在 Web 实现）——只借鉴 token 规范，不引组件；运行时生成色板用官方 @material/material-color-utilities

### 4.6 完整先例（重点推荐审计）

**首选：tiann/KernelSU 的 `GithubMarkdown.kt`**（17.8k stars，2026-08 活跃，GPL-3.0）——生产级「Compose 动态色 → WebView Markdown」，与 AppDev 目标同构：
- 渲染层：commonmark-java + GFM 扩展（Tables/Strikethrough/Autolink/TaskListItems）→ HtmlRenderer
- 注入层：WebViewAssetLoader + loadDataWithBaseURL("https://appassets.androidplatform.net")；shouldInterceptRequest 代理远程图片（经 OkHttp 带 header）——**图片加载正确姿势**
- 主题层：模板 `<html data-color-mode="light">` + `@style@` 占位符 + 独立 CSS（colors_light/dark.css + markdown.css(.markdown-body) + syntax_light/dark.css）；Compose MaterialTheme.colorScheme（surfaceContainerHigh/Low、onSurface、primary）→ 注入 `:root { --pre-background: ...; --link: ... }`
- 高亮层：syntax_light.css 来自 primer/github-syntax-light（官方 .pl-* 类）
- 工程细节：textZoom 计算、mixedContentMode MIXED_CONTENT_ALWAYS_ALLOW、onPageCommitVisible 结束 loading

**其他**：
- **slapperwan/gh4a（OctoDroid）**：老式范本（loadDataWithBaseURL file:// + addJavascriptInterface("NativeClient") + Google Code Prettify + is_dark_theme 布尔切换）——可审计但技术栈过时，作历史对照
- **gitnex-org/gitnex**：**反例**——markdown 走原生 Markwon 不用 WebView，证明原生路线可行但 GFM 深度与主题融合弱一档
- **KernelSU Module WebUI**：WebView 承载模块 HTML+CSS+JS + kernelsu npm 包 + localStorage
- **cmux（manaflow-ai，4.8k stars）**：macOS WebKit markdown viewer，非 Android

### 4.7 推荐技术栈（可组合）

```
渲染层   markdown-it v15 + markdown-it-github-alerts v1.0.1 + markdown-it-task-lists + mdit-plugins（按需）
高亮层   starry-night v3.10.0（与 github-markdown-css 共享 prettylights token）
         或 Shiki v4.4.3（行高亮/diff transformer）
主题层   github-markdown-css v5.9.0（全 CSS 变量 + [data-theme] 双通道）
         + 自研 Material 桥：Compose colorScheme → :root CSS 变量（参照 KernelSU）
安全层   DOMPurify v3.4.13（2026-08 活跃，MPL/Apache 双许可）
注入层   WebViewAssetLoader（androidx.webkit 1.14.0）+ addWebMessageListener（锁 allowedOriginRules）
         + evaluateJavascript + 内联防 FOIT 脚本（~10 行，无第三方库）
兜底     GitHub /markdown API（长文档/复杂 GFM，配缓存；context 提供 issue 链接）
```

### 4.8 维护活跃度速查（2026-08-14 实测）

markdown-it 21.8k/活跃 · marked 37k/活跃 · github-markdown-css 8.9k/低活跃（锁版本+自托管）· primer/css 13k/KTLO · starry-night 1.8k/活跃 · Shiki 13.7k/活跃 · highlight.js 25k/活跃 · Prism 13k/半停滞 · material-web 11.2k/官方维护模式 · DOMPurify 17.3k/活跃(v3.4.13) · KernelSU 17.8k/活跃 · mdit-plugins 205/活跃 · markdown-it-github-alerts 220/低活跃

---

## 最终决策建议

1. **全面转向 WebView 主渲染是可行且推荐的**（上一轮已定），本调研确认深度融合路径清晰：github-markdown-css v5.9.0 基底 + Material You 变量覆盖层 + addDocumentStartJavaScript 无闪烁注入。
2. **融合范围分三档**：
   - **核心必做**（第一版）：全量颜色令牌映射（~20 个 M3 role → github-markdown-css 语义令牌）、深浅色双套变量、无闪烁注入、字体/圆角令牌化、链接 bridge
   - **美化加分项**（第二版）：主题切换 crossfade、代码块展开/图片渐显、全局背景图（Compose 层）+ 透明 WebView、starry-night 高亮
   - **明确不做**：WebView 内真 spring 物理、跨 WebView 边界的毛玻璃、WebView 自绘 UI 定制、token 注入 JS 上下文
3. **红线确认**：token 绝不进 WebView；`setLayerType(SOFTWARE)` 禁止；`background-attachment: fixed` 不用；只动画 transform/opacity。
4. **验证约束**：WebView 渲染 Robolectric 测不了 → 截图/回归需真机或 CI 方案（与「截图工具调研」合并）。
5. **落盘**：本报告为 `docs/research/webview-material-you-fusion.md`；后续开 ADR-0006「WebView 主渲染 + Material You 融合」立项时引用本报告。

---

## 来源清单（一手源）

- M3 Color roles：m3.material.io/styles/color/roles；androidx ColorScheme.kt；material-foundation/material-tokens（css/theme/light.css）
- 动态取色：androidx DynamicTonalPalette.android.kt；material-color-utilities；material-foundation/material-theme-builder
- 深色机制：developer.android.com/develop/ui/views/layout/webapps/dark-theme；WebViewCompat.addDocumentStartJavaScript（androidx.webkit 1.9.0+）
- M3 动效：m3.material.io/styles/motion/easing-and-duration；m3.material.io/styles/motion/overview/how-it-works；m3.material.io/styles/motion/overview/specs；androidx MotionTokens.kt / ExpressiveMotionTokens.kt / StandardMotionTokens.kt；androidx Spring（animation-core current.txt）
- CSS 动效：MDN easing-function / linear() / content-visibility / scroll-behavior / backdrop-filter；web.dev animations-guide；developer.chrome.com/docs/webview
- 透明 WebView：SO 5003156 / 7711880；issuetracker 36925660 / 36940822；chromium 40366722；Qt 论坛（ClearType）
- backdrop-filter 边界：MicrosoftEdge/WebView2Feedback #4945；chromium 365818856 / 993644 / 380416865；webkit 275919；flutter_inappwebview #1415；Flutter engine PR 39244
- 生态：github.com/sindresorhus/github-markdown-css；github.com/wooorm/starry-night；github.com/shikijs/shiki；github.com/markdown-it/markdown-it；github.com/primer/css；github.com/material-components/material-web；github.com/cure53/DOMPurify；github.com/antfu/markdown-it-github-alerts；github.com/mdit-plugins/mdit-plugins
- 先例：github.com/tiann/KernelSU（GithubMarkdown.kt / module-webui）；github.com/slapperwan/gh4a（MarkdownPreviewWebView.java）；github.com/gitnex-org/gitnex；github.com/SamZebrado/TransparentFloatingBrowser；github.com/YXX168/Amber-MD；github.com/ak-asu/read4ever；github.com/billthefarmer/MarkdownView；github.com/k1717/Readwide
- Android WebView：developer.android.com/reference/android/webkit/WebView；developer.android.com/reference/androidx/webkit/WebViewAssetLoader；developer.android.com/develop/ui/views/graphics/hardware-accel
