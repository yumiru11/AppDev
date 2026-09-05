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

## UI 设计域（2026-08-15 grill 拍板，ADR-0006）

- **两层导航** — 底部 NavigationBar = 大分区（首页/仓库/我的）；首页大分区内部有顶部小分区条（动态/Issue/PR）切小分区（ADR-0006）。「首页是大的，套着小的」。
- **Home 长条按钮** — 首页内容区顶部的占宽圆角矩形按钮（新建 Issue/查看 PR/新建仓库），模仿 GitHub 网页登录后首页。
- **玻璃清单（8 项）** — 顶栏/底栏/通知面板/BottomSheet 默认开；图片查看器纯黑；FAB/设置分组头/卡片不用玻璃（ADR-0006）。强度 8dp，全局总开关 + 逐项开关。顶栏那块玻璃的矩形含首页小分区条副行（#83：同一块玻璃，不算新增点位）。
- **玻璃的本质 = 滚动穿越感** — macOS 访达侧边栏效果：内容滚过栏下被糊掉，能看到「有东西在动」（非 iOS 整屏雾感/非桌面小组件透色）。
- **卡片 PiliPlus 风格** — 中性 surfaceContainer 底 + 一点点主题色点缀（星标/链接/时间）；语言点 GitHub 原色；网格/通栏用户可切换；长按弹菜单。
- **半融合高亮** — 代码块容器随主题 + 语法色保留 GitHub 原色（C 方案）。
- **Alert 卡片** — GitHub 网页样式（左侧色条）+ Octicons 图标；**全应用禁 emoji 图标**。
- **Home 分区条** — 首页内横向 Tab（动态/Issue/PR），底部主题色指示条，随内容横移；顶栏固定不动。
- **Trending 数据源** — 官方无 API；gh4a 同款第三方 JSON 镜像（Unpublished/GithubTrending），fallback Search API 变通。

## 参考仓库（~/dev，克隆自 2026-08-15）

- **PiliPlus** — B 站客户端（Flutter）：卡片主题色点缀参考（B2-1）。
- **rikkahub** — LLM 客户端：原生 Markdown 全套（表格工具栏/复制/字号比例/自研 hljs 移植高亮）；**AGPL-3.0 不可引用**，只参考思路，从源头（hljs BSD-3）实现。
- **XMSLEEP** — 白噪音 MD3 + MaterialKolor 动态色：主题/动效参考。
- **gh4a** — OctoDroid：WebView markdown + Trending 数据源（Unpublished/GithubTrending JSON 镜像）。
