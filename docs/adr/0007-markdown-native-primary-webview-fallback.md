# ADR-0007：Markdown 渲染路线——WebView 主渲染（README/正文）+ 原生短文本（评论/通知）

- **状态**：已接受（2026-08-16 原生主渲染拍板；2026-08-19 Task B 修订为 WebView 主渲染）
- **关联**：ADR-0005（WebView 兜底建立）、ui-design.md §3.11、FEEDBACK.md、docs/research/readme-prototype-comparison.md、docs/research/webview-material-you-fusion.md
- **决策者**：用户（Task B 拍板）

## 背景

README/长文档渲染曾在 A（WebView 主）/B（原生增强）/C（混合向 B 演进）三路线间悬置。
初版按 prototype 真机验证拍板「原生主渲染 + WebView 兜底」（2026-08-16），但原生增强链在后续
**4 轮真机问题**中持续暴露引擎级短板（排版细节、复杂度、跨版本维护），而 WebView 通道基于
github-markdown-css 官方方案，渲染稳定、兼容面广。Task B 据此将路线切换为 **WebView 主渲染**，
原生渲染器仅保留给短文本（评论/通知）。

## 决策

1. **README 与 Issue 正文一律 WebView 渲染**
   - README：服务端 HTML 优先（`getReadmeHtml` 三级降级 + 双 key 缓存）；服务端异常 → 离线
     markdown-it GFM 降级，renderMode 仍 WEBVIEW
   - Issue 正文：无服务端 HTML API → 离线 GFM（`WebViewHtmlBuilder.build(rawMarkdown)`）+ 融合样式
2. **短文本保持原生**：评论列表、通知等调用点一律 WebView 禁用
3. **WebView 渲染基建**（沿用 ADR-0005/0007 既有实现，不重写）：
   - github-markdown-css + markdown-it + highlight.js + DOMPurify
   - Material You 融合：Kotlin 预计算混色变量（真机 WebView 不支持 CSS color-mix），
     深色 data-theme 翻转 + 首帧注入
   - 安全锁：WebViewSecurity 全锁、token 绝不注入、HtmlSanitizer + DOMPurify 双清洗
   - 交互经白名单 JS bridge（链接/复制/图片/checkbox/高度）补齐

## 后果

- **正面**：README/正文渲染与 GitHub 官方样式对齐、跨版本稳定；原生增强组件维护负担大幅下降
- **代价**：
  - WebView 性能 / 交互（图片预览/任务列表写回）需经 bridge 补齐
  - 测试盲区：Robolectric 渲染不了 WebView，依赖 CI 模拟器 + 真机走查
  - README 缓存与离线降级路径需要持续跟进
- **回退条件**：若 WebView 在真机出现无法接受的性能/交互缺口，再回流原生或专项解决
