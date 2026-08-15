# ADR-0007：Markdown 渲染路线——原生主渲染 + WebView 兜底

- **状态**：已接受（2026-08-16 prototype 真机验证后拍板）
- **关联**：ADR-0005（WebView 兜底建立）、ui-design.md §3.11、FEEDBACK.md、docs/research/readme-prototype-comparison.md

## 背景

README/长文档渲染曾在 A（WebView 主）/B（原生增强）/C（混合向 B 演进）三路线间悬置。
prototype/readme-comparison 分支（双版本对照：WebView 融合 vs 原生增强）经 **4+ 小时真机逐项验证**后，
原生能力补齐到足以承担主渲染，用户拍板：**原生主渲染，WebView 保留兜底**。

## 决策

1. **原生（mikepenz 0.38.1 + 增强组件）为 README/正文主渲染通道**
   - 表格：EnhancedMarkdownTable（官方组件 + 单元格换行 + 斑马纹）
   - 列表：EnhancedList 自绘（marker 基线对齐 + 嵌套换行缩进 + 任务列表）
   - 徽章：EnhancedParagraph 路由（shields SVG，Markdown 语法 + HTML 形态双支持）
   - Alert/引用块：GitHubAlertOrQuote（M3 语义容器色）
   - details：NativeDetailsCard（原生折叠）
   - 代码：KotlinTextMate 半融合高亮 + 复制按钮
2. **WebView 兜底保留**，FeatureDetector 收紧为「原生处理不了才分流」：
   - mermaid 围栏、数学公式（$..$ / $$..$$）
   - `<svg>` / `<canvas>` / `<iframe>` / `<math>`（原生无法渲染）
   - 超长文档（>2000 行 / >50KB）
   - **details/table 不再触发兜底**（原生已支持，2026-08-16 收紧）
3. **主题融合**：原生天然 M3 融合；WebView 用 Kotlin 预计算混色变量（真机 WebView 不支持 CSS
   color-mix——ADR-0005 补充），页面背景统一「近黑 + 6% 主题色」。

## 后果

- **正面**：90%+ README 走原生（快、流畅、主题 100% 融合）；复杂文档 WebView 效果天花板最高
- **代价**：原生增强组件（Enhanced* 系列）需随 mikepenz 版本升级维护；复杂 HTML 布局仍显示源码降级
- **回退条件**：若后续发现原生无法解决的高频场景（如交互图表），回流 WebView 或专项解决
