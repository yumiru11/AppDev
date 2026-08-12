# ADR-0001: OAuth 回调使用自定义 scheme

- **状态**: Accepted (2026-08-12)
- **相关**: T4（#5 认证）、T3 深链（已合入）
- **决策者**: 用户 + 实施者（grill-with-docs 会话拍板）

## 背景

AppAuth PKCE 流程需回调 URL 精确匹配 intent-filter。T3 已注册 `https://github.com` 深链（MainActivity VIEW intent-filter），GitHub 官方 app 亦声明同 host。

## 决策

OAuth 回调采用**自定义 scheme**：

```text
com.yumiru11.githubapp://oauth-callback
```

## 理由

- https 方案（`https://github.com/oauth-callback`）的验证优势（autoVerify/数字资产链接）需要**域名所有权**——我们无 github.com 所有权，享受不到该优势
- https + 既有 host 会与 T3 深链 intent-filter 串扰：回调可能被深链解析器误判（GitHubLinkParser 对未知 path 返回 External）、或系统弹选择器（浏览器/GitHub app/我们）
- 自定义 scheme 从机制上隔离两类 intent，AppAuth 官方示例惯例

## 代价

- 系统首次弹「打开 xxx 应用？」确认（可勾选始终）
- 无 autoVerify 验证（不可接受，见理由）

## 后续

- MainActivity 需增加第二个 intent-filter（自定义 scheme + oauth-callback path）
- 与 https 深链 filter 并存，互不干扰
