# AppDev 待外部调研问题清单（交给别的 AI 调研）

> 用途：这些是我（主 agent）难以自证的细节问题，需要主题的 AI 用搜索/文档调研给出**有来源**的结论。
> 要求调研方：每条给结论 + 一手来源链接；区分「事实」与「推测」；标注不确定处。

---

## 一、WebView 主题深度融合（最高优先）

### 1.1 CSS 变量全面覆盖 GitHub 内容的可行性
- github-markdown-css v5.9.0 的全部 CSS 变量清单有哪些？（逐一列出变量名与用途：--fgColor-* / --bgColor-* / --borderColor-* / --color-prettylights-syntax-* / --fontStack-* 等）
- 除了颜色，**圆角、字体大小、行高、间距**这些能否也通过 CSS 变量覆盖，还是要另外写 CSS 规则覆盖？（github-markdown-css 是否有这些非颜色变量）

### 1.2 Material You 动态色 → CSS 变量的最佳映射表
- Compose MaterialTheme.colorScheme 的 48 个 role 应该各映射到 github-markdown-css 的哪个变量？（如 primary → --fgColor-accent）
- Material Web Components（material-web）的 `--md-sys-color-*` token 与 github-markdown-css 的 `--fgColor-*` 之间，有没有现成的「桥接」项目/文章/映射表？（不是理论上推，找实际存在的）

### 1.3 代码高亮 fusion vs 保真
- starry-night 的 `--color-prettylights-syntax-*` 变量与 github-markdown-css 的同类变量是否**同一组**（能通过同一 :root 覆盖联动）？有没有人把 starry-night 高亮做进 Android WebView 的先例？
- 若保留 GitHub 语法高亮原色（保真），在 Material You 深色模式下观感是否协调？有没有「代码高亮随主题色」的成熟做法（如 Shiki 的 dual theme + CSS 变量切换）？

### 1.4 WebView 深浅色融合细节
- `addJavaScriptOnEvent`（androidx.webkit 1.16.0）在 DOM ready 后注入——它相比 addDocumentStartJavaScript 的确切优势和使用姿势？（来源：AndroidX release notes）
- View Transitions API（document.startViewTransition）在 Android WebView 的兼容性实测？（Chrome 111+ 支持，WebView 是否同源）
- `@property`（CSS 自定义属性注册）让变量可过渡——在 Android WebView（Chromium）支持情况？

### 1.5 KernelSU 方案的已知问题
- KernelSU 的 GithubMarkdown.kt（WebView + github-markdown.css + Material 色注入）有没有已知 issue/bug？（如内存泄漏、滚动卡顿、图片加载、深浅色切换闪烁）
- 有没有比 KernelSU 更完整/更现代的「Android WebView Material You Markdown」开源实现？

---

## 二、全局背景图 + 毛玻璃（美化核心）

### 2.1 背景图方案
- 移动 App「全局背景图」的主流做法：背景图放哪个层级（Activity 背景 / Compose 底层 / Window background）？与列表滚动的交互（背景固定 vs 随内容滚动）？
- 深色模式下背景图「自动压暗」的成熟方案（Android 侧用代码滤镜？还是图片双版本？）

### 2.2 毛玻璃在 Compose 的实现边界
- **「顶栏毛玻璃遮 WebView 滚动内容」到底能不能做到？** 之前结论是 WebView 独立 surface 采样不到。有没有例外（如 WebView 用 TextureView 渲染模式 / RenderEffect 的新 API / Android 15+ 变化）？
- 若要「玻璃遮 WebView」，业界有没有 workaround？（如 WebView 截图模糊更新、SurfaceView 采样、或用 Android 15 的新 SurfaceControl API）

### 2.3 背景图 + 玻璃 + WebView 三层的成熟案例
- 有没有开源 App 同时做了「背景图 + 毛玻璃 + WebView 内容」三层？（类似沉浸式阅读器）它们的层级结构怎么设计？

---

## 三、Markdown 渲染器方向（双轨确认）

### 3.1 WebView 主渲染的坑
- GitHub /markdown API 的已知限制与 rate limit 详情（认证/未认证配额、400KB 上限、context 参数行为）——有没有本地降级方案评估？
- markdown-it v15 在 Android WebView 的 JS 渲染性能（长文档 50KB+ 的首屏时间）？有没有 benchmark？

### 3.2 原生渲染保留场景
- 评论/短文本用原生渲染（mikepenz 0.38.1）与 WebView 双轨的**性能差异**实测？（列表滚动时 iframe 卡顿问题）
- 有没有「评论列表用原生 + 长文用 WebView」的现有 App 成功案例？

---

## 四、字体与排版

### 4.1 中文字体在移动端的表现
- Material You 动态色下，正文字体用系统字体（Roboto/思源黑体）还是引入自定义字体（如 MiSans/HarmonyOS Sans/OPPO Sans）？各的许可与体积？
- Google 现有可商用的中文字体（Noto Sans SC / 思源黑体）在 App 内嵌的大/小？

### 4.2 阅读排版
- GitHub 网页端 markdown 的字体/字号/行距具体值（github-markdown-css 的 --fontStack 是什么字体栈？15px 基准字号 + 1.6 行距确认？）
- 「可读性优先」的移动端 Markdown 排版最佳实践（字重/行高/段距的推荐区间）？

---

## 五、图标体系

### 5.1 Material Symbols 变量字体在 Android 的落地
- Material Symbols 的**变量字体**（wght 可变）能否在 Android 通过 fontVariationSettings 使用？Compose 支持情况？（FontVariation 在 Compose 的使用姿势）
- `dev.vicart:variable-font` 这类库的实际成熟度/star/维护？（之前调研提过作为细体方案，现在还有什么更好的选择？）
- material-icons-core（当前使用）与 Material Symbols 的切换成本评估？

### 5.2 Octicons 的 Compose 落地
- primer/octicons 的 SVG 转 Android vector drawable / ImageVector 的成熟工具链？（有没有现成的 octicons-compose 库？）

---

## 六、动效与性能

### 6.1 M3 动效在 WebView 的复刻
- 之前调研给了 spring→curve 换算表，有没有现成的「M3 motion CSS 变量/class」开源实现（直接把 Compose MotionTokens 转成 CSS）？

### 6.2 WebView 性能
- content-visibility: auto 在 Android WebView 长文档的实测收益与坑（滚动条跳动问题解决了吗）？
- WebView 内存占用优化（长 README 连续翻页的内存泄漏问题）最佳实践？

---

## 七、截图/回归测试（CI 相关）

### 7.1 WebView 渲染的自动化测试
- Robolectric 无法渲染 WebView——**有没有能跑 WebView 渲染快照的 JVM/CI 方案**？（Roborazzi 的 WebView 支持现状、Paparazzi 是否支持、有没有其他工具）
- 业界对「WebView 内容截图做回归」的成熟做法？（instrumented + 打包工具？）

### 7.2 真机走查流程化
- 有没有「CI 构建 APK → 自动安装到真机 → 截图上传」的成熟 workflow？（GitHub Actions + Firebase Test Lab / 自建设备农场）
- screenshot 比较工具（如 droid-plugin 之外的 android-ui-test + 截图对比）推荐？

---

## 调研输出要求

1. 每条：结论 + 一手来源（官方文档/源码/GitHub issue 链接）+ 时间标注
2. 区分「已确认事实」「合理推测」「未确定项」
3. 按上表编号组织，输出为 Markdown
4. 不确定就写「未找到可靠来源」——不要编造