# CONTEXT.md — 项目术语表

> 维护规则：术语被多模块/多 ticket 使用时在此登记；新增术语走 grill-with-docs / domain-modeling 会话。ADR 见 `docs/adr/`。

## 认证与会话（T4 域）

- **PKCE** — OAuth 2.0 授权码 + Proof Key for Code Exchange。AppAuth 库实现，浏览器授权 → 回调 → 换 token。
- **OAuth 回调 scheme** — `com.yumiru11.githubapp://oauth-callback`（ADR-0001）。自定义 scheme，与 `https://github.com` 深链隔离。
- **TokenStorage** — token 读写抽象接口（ADR-0002）。生产 = EncryptedSharedPreferences；测试 = 内存实现。
- **静默刷新** — 401 触发 refresh token 换新 access token 后重试原请求；需防并发（多个 401 只刷一次）。
- **PAT 降级** — fine-grained PAT 不支持 GraphQL → 认证态标记 REST-only，网络层仅发 REST 请求（plan.md §4）。
- **开发者模式** — 登录页底部折叠项，PAT 输入入口（ADR-0003）。T24 设置页建成后可迁移。
- **AuthState** — 登录状态流：`SignedIn` / `Anonymous`（游客）/ `PAT(restOnly)`。
- **游客模式** — 未登录可浏览公开内容（T2 GuestWelcomeScreen 起点）。

## 主题体系（T6 域）

- **六套预设主题** — Light / Dark / OLED / Dynamic Light / Dynamic Dark / High Contrast（ADR-0004）。
- **动态取色** — Android 12+ 壁纸色（API 31+），低版本回退固定套。
- **ExtendedColors** — MaterialTheme 之外的语义色扩展（Alert 卡片色、品牌色等），T6 落地、T7/T26 消费。
- **玻璃拟真（Glassmorphism）** — 首版仅顶栏 + 底栏，设置可关（ADR-0004）；API 31+ RenderEffect 真模糊，26-30 半透明降级。
- **AppMotion / AppDimens / AppBlur / AppIcon** — 设计令牌族（docs/ui-design.md）。AppDimens 已在 T3 落地骨架。
- **Material Symbols 变量字体** — wght=300 细体 + ROND=100 圆角诉求；静态库保底，评估票决定是否引入（ADR-0004）。

## 渲染体系（T7/T8 域）

- **原生渲染器** — mikepenz 0.38.1（compileSdk 35 天花板；0.43 需 SDK 37/AGP 9，不碰）。负责短内容（Issue 正文/评论）。
- **WebView 兜底** — GitHub `/markdown` 服务端 HTML + 同套 Material You CSS 令牌。负责长文档/复杂 GFM（README 等）；表格横滚免费获得（ADR-0005）。
- **KotlinTextMate** — 代码高亮引擎（0.2.0），7 语言语法 + VS Code Dark+/Light+ 主题（JSONC 需清洗成纯 JSON）。
- **GitHub Alert** — NOTE/TIP/IMPORTANT/WARNING/CAUTION → 主题色卡片（自定义 blockQuote 注入；0.38 无 alert 槽位）。
- **表格横滚** — 原生裁剪接受；长表格走 WebView（ADR-0005）。后期转原生须先 prototype 验证。
- **图片认证头** — 私有仓库图片的 Coil 拦截器，本票不做（ADR-0005），真机验证归发布前。

## 链接与导航（T3 域，已合入）

- **GitHubLinkParser** — 纯函数 URL → `ParsedUrl`（core:navigation）。全形态：绝对/相对/`@mention`/裸 sha/`#123` 等。
- **AppRoute** — 导航路由表（core:navigation），`fromParsedUrl` 映射。
- **ExternalLinkHost** — `ParsedUrl.External` → Chrome Custom Tabs（core:ui）。
- **深链** — `https://github.com` VIEW intent-filter（T3 合入）；OAuth 回调 scheme 与之隔离（ADR-0001）。

## 数据层（T5 域，已合入）

- **错误归一化** — 401/403/404/409/422/429/5xx → 统一领域错误（core:github-data）。
- **重试策略** — 429/5xx 指数退避（core:github-data）。
- **双通道** — GraphQL 读优先（Apollo 5）+ REST 写优先（Retrofit 3）；统一模型映射（core:github-data）。
- **ETag 304** — OkHttp 拦截器缓存（core:github-rest）。
- **mockwebserver3** — 新 artifact（com.squareup.okhttp3:mockwebserver3），集成测试用。
