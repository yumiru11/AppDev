# ADR-0005: Markdown 渲染分层策略（表格走 WebView、图片认证头本票不做）

- **状态**: Accepted (2026-08-12)
- **相关**: T7（#8 原生渲染）、T8（#9 WebView 兜底）、prototype/markdown-renderer
- **决策者**: 用户 + 实施者（grill-with-docs 会话拍板）

## 背景

renderer 0.38.1 无内置表格横向滚动（原型确认）。长表格的两个候选解：原生自建 table 组件 vs 依赖 WebView 兜底。

## 决策

1. **表格策略：目前用 WebView 方案**
   - T7 原生渲染器**不自建 table 组件**，长表格/复杂表格内容由 T8 WebView 兜底承担（HTML `overflow-x: auto` 自然横滚）
   - WebView 注入同一套 Material You CSS 令牌 → 与原生同色板（颜色/圆角/间距一致；排版细节存在引擎级差异，可接受）
   - **后期若转回原生渲染**：先做 prototype 验证再应用（不直接抄方案）
2. **图片认证头：本票不做**
   - 私有仓库图片需带 Authorization 的 Coil 拦截器，但测试环境无法验证（原型确认）
   - 首版只支持公开仓库图片（主路径）；私有图片记为后续增强，真机验证归入发布前

## 理由

- 分层自洽：短内容（Issue 正文/评论）原生渲染、长文档（README/复杂 GFM）WebView 兜底——长表格多出现在长文档，正好归 WebView
- 不自建 table 组件：0.38 槽位受限、工作量大、与 T8 重复造轮子
- 图片认证头无验证手段，先不做避免「假装完成」
