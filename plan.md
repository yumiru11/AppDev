# Android GitHub 客户端 —— 技术规划

> **核心结论先说：**
> **Kotlin + Jetpack Compose + Material 3 构建全原生 UI；GitHub 数据层 GraphQL 优先、REST 补位；Markdown 采用「Compose 原生渲染为主 + WebView 高保真兜底」的分层架构**——短内容、评论、常规 Issue/PR 正文用原生渲染保证列表流畅与 Material You 深度融入；README、长文档、复杂 GFM（mermaid、KaTeX、重型 HTML）落到 WebView 高保真通道，结合 GitHub 服务端渲染 HTML，注入同一套 Material You CSS 令牌，并统一拦截 GitHub 链接跳转应用内页面。
> 代码文件浏览与编辑交给专业代码编辑器（Rosemoe Sora Editor）满足"准确语法高亮"；写功能（评论、编辑、审查、合并、文件提交、分支管理）通过 GraphQL mutation + REST 完成闭环。项目采用多模块架构、Hilt、Room、Paging 3，测试与截图回归全部跑在 Linux 纯 JVM（Robolectric + Roborazzi），不依赖虚拟机、不依赖 Waydroid、不引入 Kotlin Multiplatform。

---

## 1. 总体目标与设计原则

### 1.1 产品目标

要构建一个：

- **功能全面**：仓库、文件树、README、Issue、PR、Review、行内评论、通知、搜索、代码浏览、代码编辑、仓库管理、分支、Release、主题、国际化。
- **轻量流畅**：启动快（冷启动 < 1.5s，中端机）、列表滚动稳定 60fps、README 秒开（缓存命中 < 300ms）、APK 体积有意图地控制。
- **美观统一**：全 Material You 审美——动态取色、预设主题、自定义主题、动效规范、图标体系统一。
- **Markdown 高保真**：README 与常用 GFM 写法的渲染结果对齐 GitHub 网页端。
- **可写能力完整**：评论、编辑 Issue/PR、Review、行内评论、Merge、文件编辑提交、分支管理。
- **工程化成熟**：多模块、单向数据流、完整测试金字塔、GitHub Actions CI/CD、i18n、无障碍、安全基线。

### 1.2 技术原则

1. **UI 全 Compose + Material 3**：页面结构、列表、导航、主题、动画全 Compose 化；Material You 作为设计系统而非套壳。
2. **Markdown 渲染分层**：按内容类型、复杂度、交互需求选择渲染器，不搞一刀切；不为边缘特性（math/mermaid/重型 HTML）拖累主路径性能。
3. **GraphQL 读优先、REST 写优先**：结构化读取走 GraphQL（类型安全 + 一次拿全）；写操作、文件内容、搜索、Markdown 服务端渲染走 REST。
4. **统一路由是基础设施**：所有渲染器（原生与 WebView）产生的链接都进入同一个 GitHub 链接解析器（GitHubLinkParser）→ 应用内导航或 Custom Tabs 兜底。
5. **主题令牌穿透两套渲染**：Compose 生成 Material You 颜色令牌（含扩展语义色），WebView 兜底通道通过 CSS Variables 使用同一套令牌，保证观感一致。
6. **可写功能与只读功能分层**：先保只读浏览体验，写功能通过 Repository 统一封装，业务逻辑与 API 细节解耦。
7. **编码质量约束**：版本目录（version catalog）、设计令牌、字符串资源、Konst 架构测试——从第一行代码开始落实，不事后补课。
8. **红线约束**：不引入 Kotlin Multiplatform；测试/预览全 JVM（Robolectric/Roborazzi）；不在评论列表中大量使用 WebView；不往 WebView 注入 token。

---

## 2. Markdown 渲染方案调研与决策

### 2.1 候选方案对比

| 方案 | 优点 | 缺点 | 适合场景 |
|---|---|---|---|
| 纯 Compose 自建 Markdown 渲染 | Material You 最统一，交互最原生 | GFM 兼容性工作量巨大；HTML、表格、任务列表、嵌套等很难完整支持 | 不推荐为主方案 |
| **Compose 第三方库（multiplatform-markdown-renderer）** | Compose 原生、M3 配色模块、GFM 表格/删除线/任务列表、可点击链接、语法高亮、懒加载、异步解析；社区活跃（v0.43.0） | 对极少数高级内容（math/mermaid/重型 HTML）支持不足 | ✅ **主渲染器** |
| Markwon + TextView | 成熟稳定，GFM 扩展全面，prism4j 高亮 150+ 语言 | 需 AndroidView 桥接，与 Compose 动效/主题割裂 | 不选（能力已被主渲染器覆盖） |
| WebView + 本地 JS 渲染（markdown-it 等） | 可高度还原 GFM，支持 KaTeX/Mermaid | 性能/内存开销，与 Compose 滚动嵌套复杂 | 仅作**兜底通道** |
| GitHub 服务端渲染 HTML + WebView | 最接近网页端；mention、issue 引用、相对链接完整 | 依赖 API/网络，需 sanitize、缓存、样式注入 | **兜底通道首选数据源** |

### 2.2 推荐架构：原生优先 + WebView 兜底（混合渲染）

```text
原始 Markdown
  │
  ├─① 预处理层 GhMarkdownProcessor
  │    · emoji 短码 → Unicode（gemoji 子集映射表，数据放 assets 不硬编码）
  │    · 相对链接 → 按仓库上下文重写（./docs → 默认分支路径）
  │    · 特性探测：mermaid 围栏 / $math$ / 重型 HTML / 超大文档 → 标记 fallback
  │
  ├─② 主路径：ComposeMarkdownRenderer（mikepenz renderer）
  │    · 注解器（annotator）扩展：@user 彩色 span、#n 引用、[!NOTE] 提醒块、锚点、代码语言徽标
  │    · 任务列表可交互 checkbox
  │    · 链接统一交给 GitHubLinkParser
  │
  └─③ 兜底路径：WebViewMarkdownRenderer（按需启用，绝不默认）
       ├─ 数据源优先级：
       │    GitHub 服务端 HTML（/readme HTML / POST /markdown gfm+context）
       │    → 本地 markdown-it + Shiki/KaTeX/Mermaid（assets 打包，离线可用）
       ├─ CSS：注入 Material You 令牌（见 2.9）
       ├─ 私有图片代理（shouldInterceptRequest 白名单 + Authorization）
       └─ 链接统一交给 GitHubLinkParser
```

### 2.3 渲染目标清单（GFM 全覆盖）

必须支持以下特性，与网页端一致：

- 标题、段落、引用块（blockquote）
- 加粗、斜体、删除线
- 表格（含单元格内联内容、对齐）
- 有序/无序列表、嵌套列表
- 任务列表 `- [ ]` / `- [x]`
- 行内代码、围栏代码块、代码语言标注、语法高亮、代码块复制按钮
- 图片（含相对路径引用、GitHub 缓存域）
- 外部链接、自动链接（裸 URL）
- 相对链接（`./`、`../`、`/owner/repo`）
- 提及：`@user`、`@org/team`
- 引用：`#123`、`owner/repo#123`、`gh-123`
- 提交引用（裸 sha）
- Emoji 短句：`:rocket:`
- GitHub Alerts：`[!NOTE]`、`[!TIP]`、`[!IMPORTANT]`、`[!WARNING]`、`[!CAUTION]`
- 锚点跳转（`#section`）
- 图片：懒加载、点击放大、GIF
- 内嵌 HTML（安全子集）
- Math/KaTeX（兜底通道，可选）
- Mermaid（兜底通道，可选）
- 脚注（尽力而为，不保证与网页完全一致）

### 2.4 渲染器抽象接口

```kotlin
interface MarkdownRenderer {
    fun render(
        content: MarkdownContent,
        context: RenderContext,
    ): Flow<RenderResult>          // 进度 → 完成 → 失败可降级信号
}

data class MarkdownContent(
    val rawMarkdown: String?,      // 原始 md（编辑态必填）
    val serverHtml: String?,       // 服务端渲染结果（有则优先于本地渲染）
    val sourceType: SourceType,    // README / ISSUE_BODY / PR_BODY / COMMENT / PREVIEW / FILE
)

data class RenderContext(
    val owner: String?,
    val repo: String?,
    val ref: String?,
    val themeTokens: MarkdownThemeTokens,
    val canWrite: Boolean,
    val interactiveTaskList: Boolean,
)
```

两套实现：`ComposeMarkdownRenderer`（主）与 `WebViewMarkdownRenderer`（兜底），由 `MarkdownFeatureDetector` 判定路由（含"本地离线编辑预览"分支）。

### 2.5 分内容类型渲染决策表

| 内容类型 | 渲染方式 | 理由 |
|---|---|---|
| Issue/PR 评论、短正文 | Compose 原生 | 列表滚动流畅、主题一致、无需 WebView |
| 常规 Issue/PR 正文 | Compose 原生；探测到复杂内容再切兜底 | 与网页一致且轻量 |
| README | Compose 原生为主；复杂内容 → WebView 服务端 HTML 通道 | 达到「网页端一致」最稳途径又不牺牲性能 |
| Markdown 编辑预览 | 与展示共用管线；服务端渲染时走 POST /markdown | 避免编辑态与展示态不一致 |
| 代码文件浏览/编辑 | Sora Editor（TextMate） | 需要行号/搜索/准确高亮/编辑 |
| PR Diff | 自建 Compose 统一 diff 视图（v1 轻量版）+ 行评论 | 初期不强行完整自研，WebView diff 仅兜底 |
| 代码块语法高亮 | 原生：Highlights（18 语言）→ v2 prism4j（150+ 语言）；文件级：TextMate | 分层覆盖查询覆盖 |

### 2.6 README 渲染策略（第一优先级）

1. **优先取 GitHub 服务端 HTML**：

   ```http
   GET /repos/{owner}/{repo}/readme
   Accept: application/vnd.github.html+json
   ```

   - 官方渲染：mention、issue 引用、相对链接、GFM 全部正确
   - 处理链：获取 HTML → DOMPurify 清洗 → 移除 script → 注入 Material You CSS 变量 → WebView 渲染 → 拦截所有链接

2. **次选 REST Markdown API**（编辑预览、无 HTML 可用时）：

   ```http
   POST /markdown
   Content-Type: application/json
   {"text": "…", "mode": "gfm", "context": "owner/repo"}
   ```

3. **最后本地兜底**（离线草稿预览、API 不可用时）：

   - markdown-it + markdown-it-task-lists/tables/anchor/emoji/footnote
   - Shiki（TextMate 语法，准确性高）或 highlight.js
   - KaTeX、Mermaid 按需懒加载
   - 全部 JS/CSS 打包进 assets，`WebViewAssetLoader` 提供，不从网络加载

### 2.7 Issue/PR 正文渲染策略

- GraphQL 取 `body` 与 `bodyHTML`：**有 bodyHTML 优先原生 span 渲染；无则 `body` + POST /markdown**；编辑时用原始 `body`。
- 详情页：标题、状态、标签、作者、Assignees、Milestone、Reactions → Compose 原生；正文 → 本地渲染，探测到复杂内容切换高保真通道。
- Timeline 评论：一律原生渲染（数量多，禁止逐条 WebView）。

### 2.8 评论渲染策略

- 全量 Compose 原生渲染，Material You 主题直接生效：

| 元素 | Material You 映射 |
|---|---|
| 正文文本 | `onSurface` |
| 链接 | `primary`（带下划线可选） |
| 引用块 | `surfaceContainerHigh` + 左侧 3dp `primary` 边框，圆角 |
| 行内代码 | `surfaceContainerLow` 背景 + 细圆角 |
| 代码块 | 圆角 surface、等宽字体、横向滚动、语言标签 + 复制按钮 |
| 表格 | `outlineVariant` 细边框、表头 `surfaceContainerHigh` |
| 任务列表 | M3 Checkbox 样式（只读或可点击反向 PR 状态） |

### 2.9 WebView 高保真渲染设计

- WebView 只作为"内容渲染容器"，不做应用导航。
- 模板结构：

```html
<!DOCTYPE html>
<html>
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <style id="theme-vars"></style>
  <link rel="stylesheet" href="markdown-you.css">
  <link rel="stylesheet" href="highlight-theme.css">
</head>
<body><div id="content" class="markdown-body"></div></body>
</html>
```

- Kotlin → JS：`setContent(html, options)`、`updateTheme(tokens)`、`scrollToAnchor(id)`
- JS → Kotlin（JSBridge）：`onLinkClick(url)`、`onCodeCopy(code)`、`onImageClick(src)`、`onCheckboxClick(index, checked)`、`onHeightChanged(h)`

### 2.10 Material You CSS 令牌注入

Compose 侧生成主题令牌，注入 `:root` 变量：

```css
:root {
  --md-sys-color-primary: …;
  --md-sys-color-on-surface: …;
  --md-sys-color-surface-container-low: …;
  --md-sys-color-surface-container-high: …;
  --md-sys-color-outline-variant: …;
  --md-sys-shape-corner-medium: 12px;
  --md-sys-font-sans: …;
  --md-sys-font-mono: …;
}
.markdown-body { background: transparent; color: var(--md-sys-color-on-surface); font-family: var(--md-sys-font-sans); }
a { color: var(--md-sys-color-primary); }
pre { background: var(--md-sys-color-surface-container-low); border-radius: var(--md-sys-shape-corner-medium); padding: 12px; }
blockquote { background: var(--md-sys-color-surface-container-low); border-left: 3px solid var(--md-sys-color-primary); }
table td, table th { border: 1px solid var(--md-sys-color-outline-variant); }
```

- 深色/浅色/OLED/动态色切换时重新注入令牌（缓存按 token 版本双 key）。
- 不直接照搬 GitHub 蓝灰 CSS，基于 `github-markdown-css` 思路自维护 `markdown-you.css`。

### 2.11 链接跳转设计（GitHubLinkParser）

统一解析所有链接类型：

```kotlin
sealed interface GitHubLink {
    data class Repo(val owner: String, val name: String) : GitHubLink
    data class Issue(val owner: String, val repo: String, val number: Int) : GitHubLink
    data class PullRequest(val owner: String, val repo: String, val number: Int) : GitHubLink
    data class Commit(val owner: String, val repo: String, val sha: String) : GitHubLink
    data class Blob(val owner: String, val repo: String, val ref: String, val path: String) : GitHubLink
    data class Tree(val owner: String, val repo: String, val ref: String, val path: String) : GitHubLink
    data class Release(val owner: String, val repo: String, val tag: String?) : GitHubLink
    data class User(val login: String) : GitHubLink
    data class Discussion(val owner: String, val repo: String, val number: Int) : GitHubLink
    data class Search(val query: String) : GitHubLink
    data class External(val url: String) : GitHubLink
}
```

支持的输入形态：

- 绝对链接：`https://github.com/owner/repo(/issues/N|/pull/N|/blob/ref/path|/commit/sha|/releases…)`
- 相对链接：`/owner/repo/issues/123`、`issues/123`、`../blob/main/file`
- Markdown 内引用：`#123`、`owner/repo#123`、`@user`、裸 sha
- 路由行为：Repo→仓库页；Issue/PR→详情；Blob/Tree→文件浏览；Commit→提交页；Release/User/Org→对应页面；外部→Custom Tabs；未知→浏览器
- 同一解析器复用为 Android 深链接处理（外部 URL 打开 App 时同一条路由）。

### 2.12 语法高亮方案

- **README 代码块**：原生通道用 Highlights（18 种常用语言，6 组暗/亮主题可随 App「色随主题」）；覆盖不足（JSON/YAML/HTML/CSS/SQL/Markdown 等）→ v2 接入 prism4j（150+ 语法）自绘 `codeFence` 组件（renderer 组件点已验证可替换）。
- **兜底 WebView**：Shiki（TextMate 语法、准确性高、按语言懒加载，Web Worker 里跑，首屏不加载全语言）。
- **代码文件浏览**：Sora Editor TextMate 语法（VS Code 同款语法），支持行号、搜索、跳转行、wrap。
- **PR Diff**：自研轻量 unified diff 渲染；复杂场景可 WebView + diff 库过渡。

### 2.13 渲染性能优化

- **本地资源加载**：JS/CSS 全部打 assets + WebViewAssetLoader，不从网络拉框架。
- **WebView 预热**：App 空闲时预热一个空模板实例，预注册 JS bridge。
- **缓存渲染结果**：以 `content hash + theme version` 为 key 缓存渲染产物（原生与 Web 各一套）；README 用 ETag。
- **按需启用 JS 功能**：普通 MD 只装 DOMPurify + 主题；出现代码块再加高亮；出现 mermaid 才加载 mermaid。
- **懒加载图片**：`loading="lazy"`；点击进全屏大图；私有仓库图片走拦截器代理加 Authorization。
- **嵌套滚动**：README 单独页面内 WebView 自身滚动；嵌在 Compose 长列表中的正文预身高控制，复杂时展开全屏内容页。
- **复用与销毁**：README 页使用单实例 WebView，非必要时 destroy，LeakCanary 监控。

### 2.14 WebView 安全策略

```kotlin
webView.settings.apply {
    javaScriptEnabled = true
    allowFileAccess = false
    allowContentAccess = false
    allowFileAccessFromFileURLs = false
    allowUniversalAccessFromFileURLs = false
    domStorageEnabled = false
    databaseEnabled = false
    geolocationEnabled = false
}
```

- 不注入 token、不拼 token 进 HTML。
- 私有图片请求只在 `shouldInterceptRequest` 对白名单 host 加 Authorization。
- 所有 HTML 一律 sanitize；禁止任意 `file://`；外链全部拦截交给路由。

---

## 3. 总体技术栈规划

### 3.1 基础技术栈

| 分类 | 选型 | 说明 |
|---|---|---|
| 语言 | Kotlin | 全项目 Kotlin |
| UI | Jetpack Compose（BOM 最新） | 声明式 UI |
| 设计系统 | Material 3 / Material You | 动态配色、M3 组件、自适应 |
| 最低 SDK | Android 8.0 / API 26 | 更好管理 WebView、java.time、性能 |
| 架构 | MVVM + UDF + Repository | Compose 友好、易测试 |
| DI | Hilt | Jetpack 生态成熟 |
| 异步 | Coroutines + Flow | 标准方案 |
| 导航 | Navigation Compose（类型安全路由） | 初期够用，后期可演进 |
| 网络 | OkHttp + Retrofit（REST） | 通用网络/REST |
| GraphQL | Apollo Kotlin 5.x | GraphQL 客户端首选 |
| 图片 | Coil 3 | Compose 支持好 |
| 分页 | Paging 3 | 列表统一 |
| 本地缓存 | Room | 离线缓存、草稿、历史 |
| 偏好 | DataStore | 主题、设置 |
| 日志 | Timber | debug 网络可视化 |
| Debug 网络 | Chucker | Debug 包使用 |
| 内存检测 | LeakCanary | Debug 包使用 |
| Markdown | multiplatform-markdown-renderer 0.43.0（+m3、+code） | 见 §2 |
| 代码/编辑 | Rosemoe Sora Editor（editor-compose + language-textmate） | 见 §8 |
| 图标 | Material Symbols（com.composables compose-icons）+ Octicons 补充 | 见 §5.8 |

### 3.2 GitHub API 能力表

| 能力 | 通道 | 端点 |
|---|---|---|
| 主要读取（feed/repo/issue/PR/timeline/viewer） | GraphQL | `/graphql` |
| README 原文/HTML | REST | `GET /repos/{o}/{r}/readme` |
| Markdown 渲染（预览/兜底） | REST | `POST /markdown`（`mode=gfm`, `context`） |
| 文件内容/新建/更新/删除 | REST | Contents API |
| 仓库树、分支、提交 | REST | Git Data API |
| PR 文件/Diff/Reviews/Checks | REST | pulls/files、reviews、check-runs |
| 搜索（仓库/用户/issue/代码） | REST | `/search/*`（搜索 REST-only） |
| 通知 | REST | Notifications API |
| 写操作 | REST + GraphQL mutation | comments/reviews/merge 等 |

---

## 4. GitHub 数据层与认证

### 4.1 认证方案（调研后决策：用 PKCE，不用 Device Flow）

| 方案 | 结论 | 说明 |
|---|---|---|
| **OAuth 授权码 + PKCE（AppAuth-Android）** | ✅ 首选 | GitHub 2025-07 起支持 S256 PKCE；原生移动端推荐流；Custom Tabs 完成授权；无需 client secret |
| PAT（fine-grained / classic） | 次要（开发者模式） | **fine-grained PAT 不支持 GraphQL**（仅 REST）→ 该模式自动降级为 REST-only 路由 |
| OAuth Device Flow | ❌ 不选 | GitHub 官方提示存在钓鱼风险，面向用户设备的 App 优先推荐授权码+PKCE |

### 4.2 Token 安全

- 存储：**EncryptedSharedPreferences / Android Keystore**
- 明文不入 DataStore、不写日志、不注入 WebView、不进崩溃上报
- 401 统一拦截 → 静默刷新 → 失败重新登录
- 游客模式：未登录支持只读公共内容（无 token 请求）

### 4.3 请求规范

统一请求头：

```http
Accept: application/vnd.github+json
X-GitHub-Api-Version: 2022-11-28
Authorization: Bearer {token}
```

- 共享 OkHttp：Auth 拦截器、日志（debug only）、ETag 缓存、错误归一化（401/403/404/409/422/429）
- 限流策略：REST 5000/hr、GraphQL 点数——开发模式展示剩余点数提醒）

### 4.4 GraphQL 设计（Apollo Kotlin）

- Codegen（response-based）、Fragment 复用、Normalized Cache（memory → SQLite 链）
- 自定义 scalar 映射（DateTime、URI）
- 官方玩法：`pagination-support-with-jetpack-paging` 示例对接 Paging 3 游标
- 典型 Query：

```graphql
query Viewer { viewer { login name avatarUrl bio url } }

query RepositoryOverview($owner: String!, $name: String!) {
  repository(owner: $owner, name: $name) {
    id name description stargazerCount forkCount
    primaryLanguage { name color }
    defaultBranchRef { name }
    licenseInfo { name }
    viewerHasStarred viewerSubscription
  }
}

query IssueDetail($owner: String!, $name: String!, $number: Int!, $after: String) {
  repository(owner: $owner, name: $name) {
    issue(number: $number) {
      id number title body bodyHTML state
      author { login avatarUrl }
      labels(first: 20) { nodes { name color } }
      assignees(first: 20) { nodes { login avatarUrl } }
      milestone { title }
      timelineItems(first: 50, after: $after) {
        pageInfo { hasNextPage endCursor }
        nodes {
          ... on IssueComment {
            id body bodyHTML author { login avatarUrl } createdAt
          }
        }
      }
    }
  }
}
```

### 4.5 REST 补位端点表

| 操作 | 端点 |
|---|---|
| Markdown 渲染 | `POST /markdown` |
| README | `GET /repos/{o}/{r}/readme` |
| 文件内容/更新/创建/删除 | `GET/PUT/DELETE /repos/{o}/{r}/contents/{path}` |
| Git Tree | `GET /repos/{o}/{r}/git/trees/{sha}?recursive=1` |
| PR Files | `GET /repos/{o}/{r}/pulls/{n}/files` |
| Reviews | `GET/POST /repos/{o}/{r}/pulls/{n}/reviews` |
| Checks | `GET /repos/{o}/{r}/commits/{ref}/check-runs` |
| 分支/引用 | `GET/POST... /git/refs` |

### 4.6 缓存策略

| 数据 | 策略 |
|---|---|
| Viewer | 本地缓存 + 登录后刷新 |
| Repo 元数据 | ETag + Room |
| README | ETag + 渲染 HTML 缓存（content hash + theme version） |
| Issue/PR 列表 | Paging + RemoteMediator（Room） |
| Timeline | 分页缓存 |
| PR 文件列表 | 按 PR head sha 缓存 |
| 文件内容 | 按 `branch+path+sha` 缓存 |
| Markdown 渲染结果 | 按 content hash 缓存 |
| 搜索历史 | Room |
| 草稿 | DataStore/Room |

---

## 5. Material You UI 设计系统（独立模块）

### 5.1 主题来源

1. 系统动态配色（Android 12+ 壁纸取色）
2. 自定义 seed color
3. 预设主题
4. 暗色模式 / OLED 纯黑模式 / 高对比模式
5. 自定义对比度、圆角强度、动画强度、图标风格、代码字体、行号开关

### 5.2 主题模型

```kotlin
data class AppThemePreferences(
    val themeMode: ThemeMode,       // SYSTEM / LIGHT / DARK
    val useDynamicColor: Boolean,
    val seedColor: Long,
    val styleVariant: ThemeVariant, // TONAL_SPOT / NEUTRAL / VIBRANT / GITHUB_CLASSIC …
    val useOledDark: Boolean,
    val contrastLevel: Float,
    val cornerScale: Float,         // 0..1 圆角强度
    val animationScale: Float,      // 0..1
    val iconStyle: IconStyle,       // OUTLINED / ROUNDED / FILLED
    val codeFontFamily: String,
    val showLineNumbers: Boolean,
)
```

- 生成：Material Color Utilities（seed → 全 tonal palette）；`dynamicLight/DarkColorScheme()` 优先。
- WebView 侧同步导出令牌（见 2.9）。

### 5.3 GitHub 语义状态色映射

| GitHub 状态 | 语义 | Material You 映射 |
|---|---|---|
| Open / Reopened | 成功/进行中 | secondary / tertiary container |
| Closed | 关闭 | error / errorContainer |
| Merged | 特殊 | tertiary（紫色调） |
| Draft | 中性 | surfaceContainerHigh + onSurfaceVariant |
| Checks success / failure / pending | 成功/错误/进行 | success / error / warning（扩展色） |

扩展色定义：

```kotlin
data class ExtendedColors(
    val success: Color, val onSuccess: Color,
    val successContainer: Color, val onSuccessContainer: Color,
    val warning: Color, val onWarning: Color,
    val warningContainer: Color, val onWarningContainer: Color,
    val info: Color, val onInfo: Color,
    val merged: Color, val onMerged: Color,
    val draft: Color, val onDraft: Color,
)
```

### 5.4 设计令牌

- 颜色：一律 `MaterialTheme.colorScheme.*` + `ExtendedColors`，永不硬编码十六进制。
- 尺寸：`AppDimens`（cornerSmall=8dp、cornerMedium=12dp、cornerLarge=16dp、cornerExtraLarge=28dp、列表横距 16dp、内容距 16dp、代码块 padding 12dp）
- 字体：`AppTypography`（字阶、代码等宽追加）

### 5.5 组件清单

**通用组件**：AppTopBar、AppScaffold、AppNavigationBar、AppNavigationRail、AppTabRow、AppChip、AppLabelChip、AppStateChip、AppAvatar、AppAvatarRow、AppCard、AppListItem、AppSectionHeader、AppEmptyState、AppErrorState、AppLoadingState、AppPullToRefresh、AppSearchBar、AppBottomSheet、AppDialog、AppSnackbar

**GitHub 专属组件**：RepoHeader、RepoLanguageBar、RepoFileTree、RepoFileItem、IssueHeader、IssueTimelineItem、IssueCommentItem、IssueEventItem、PrHeader、PrTabBar、PrConversationTimeline、PrCommitItem、PrCheckItem、PrFileDiffItem、PrReviewCard、MergeBox、BranchSelector、CommitDialog、CodeViewer、CodeEditor、MarkdownViewer、MarkdownEditor、ReactionBar、LabelChipGroup、AssigneeRow、MilestoneCard

### 5.6 图标方案

- Material Symbols（com.composables compose-icons：`icons-material-symbols-rounded/outlined` 等边字体），避免 deprecated 的 `material-icons-extended`
- GitHub 特有图标（merge、draft PR、branch、fork、issue、discussion、workflow/checks）用 Octicons 补充语义
- 风格由主题配置驱动：选中态 filled、未选中 outlined；全部矢量可随主题着色

### 5.7 动效规范

- 对齐 Material Motion：Emphasized easing、时长压缩（短动画 200–300ms）
- 场景表：

| 场景 | 动效 |
|---|---|
| 列表 → 详情 | Shared element / container transform |
| Tab 切换 | Fade through |
| BottomSheet | Slide + fade |
| Snackbar | M3 默认 |
| PullToRefresh | M3 indicator |
| Star/喜欢/走 | 微缩放 + 颜色变化 |
| 主题切换 | Crossfade |

- 尊重系统动画减弱设置；滚动性能优先，不做无谓横向缩放与弹跳

---

## 6. Issue / PR 页面结构（与网页结构对齐）

### 6.1 Issue 详情页

```text
TopAppBar（返回 / 仓库名 / 更多：分享、浏览器打开、复制链接）
IssueHeaderCard
  · StateChip（Open / Closed）
  · 标题
  · 作者 + 相对时间
  · LabelChips（低饱和 chip）
  · Assignee 行、Milestone
  · ReactionBar
  · 操作：编辑 / Subscribe / Close / Reopen（权限决定可见性）
MarkdownBody（正文渲染，复杂度探测）
TimelineList
  · CommentItem（作者头像 / 时间 / MD 正文 / 反应 / 编辑·删除菜单）
  · EventItem（labeled / assigned / locked / closed / reopened…）
  · CrossReferenceItem / LinkedPrItem
BottomCommentBar（工具栏 + 编写/预览 + 输入区，键盘 insets 适配）
```

Material You 适配规范：
- StateChip 用 tonal container 区分状态
- 标签用彩色但低饱和 chip
- Timeline 保持 list item 而非重卡片
- 评论卡 `surfaceContainerLow`
- 事件项使用次要文本 + 小图标
- 底部栏固定，键盘弹起用 WindowInsets 适配

### 6.2 PR 详情页

```text
PrHeader
  · StateChip：Open / Closed / Merged / Draft
  · 标题、作者、setLabel
  · 分支信息：base ← head
  · Labels / Reviewers / Checks 摘要 / Mergeable 状态
PrTabs
  · Conversation / Commits / Checks / Files changed
ConversationTab
  · PR Body（Markdown）
  · Review 卡片（approve/comment/request-changes）
  · Review Comments、Commit References、Timeline 事件
CommitsTab
  · 提交列表（作者头像、缩写 SHA、展开 diff）
ChecksTab
  · CheckRun 列表（状态图标 / 结论 / 失败详情展开）
FilesChangedTab
  · 文件列表（+N −M）→ DiffView（统一视图/分屏切换、行号）
  · 行号点击 → 新增/回复行评论、解析会话
MergeBox
  · 合并方法选择（merge / squash / rebase）
  · 合并标题/备注、删除分支选项、合并按钮（按 viewerPermission 显隐）
```

### 6.3 Review 交互

- Comment / Approve / Request changes / Submit review
- 编辑评论、回复评论、解决/解除解决会话（对应 API 权限）
- 乐观更新 + 失败回滚 + 错误提示

---

## 7. 写功能规划

### 7.1 Markdown 编辑（评论 / Issue / PR 正文）

- 编辑器：Sora Editor 加载 Markdown TextMate 语法（源码高亮变化）
- 工具栏：加粗 / 斜体 / 行内代码 / 代码块 / 标题 / 列表(有序、任务) / 链接 / 图片 / 引用
- 编辑/预览双 Tab，预览与主渲染管线共用（WYSIWYG 一致性）
- 提及补全：@user、表情、引用自动补全（Sora auto-complete API）
- **明确不用 WYSIWYG rich editor**（调研确认：不支持表格/任务列表/多行代码块，且禁止手输 md 符号——与 GitHub 编写习惯冲突）

### 7.2 Issue 操作

- 创建 Issue、编辑 body/title
- 关闭 / 重开
- 评论新增、编辑、删除、反应
- Labels / Assignees / Milestone 编辑（权限实测）

### 7.3 PR 审查与合并

- 创建 PR（base/head）、编辑、关闭、重开
- Review：approve / comment / request changes / submit、行内评论（position/side/anchor）
- Merge（merge/squash/rebase）、Delete branch、Update branch
- Mutation 示例：

```graphql
mutation MergePullRequest($pullRequestId: ID!, $mergeMethod: PullRequestMergeMethod) {
  mergePullRequest(input: { pullRequestId: $pullRequestId, mergeMethod: $mergeMethod }) {
    pullRequest { merged state }
  }
}
```

### 7.4 文件编辑（Contents API）

流程：`GET contents`（获取 sha + base64）→ 解码 → Sora 编辑 → 输入 commit message → 选择分支 → `PUT contents`。

```json
PUT /repos/{owner}/{repo}/contents/{path}
{ "message": "Update file", "content": "<base64>", "sha": "<file-sha>", "branch": "main" }
```

- 创建：同 `PUT`（无 sha）
- 删除：`DELETE` + sha
- **冲突处理**：409 → reload / overwrite / Copy local changes，绝不静默覆盖
- 支持"提交到当前分支 / 新建分支"两种模式

### 7.5 分支管理

- 列出分支、切换
- 创建分支（Git Refs API）
- 删除分支（权限）
- PR 创建时选 base/head

### 7.6 仓库管理

- Star / Unstar、Watch / Unwatch、Fork
- 创建 / 删除仓库（设置页）
- Releases / Tags 浏览（发布 Release，上传 asset）
- Topics、License、语言栏（Linguist 数据）
- 通知管理：列表 / 标记已读 / 分类过滤

---

## 8. 代码浏览与编辑

### 8.1 代码浏览

- Sora Editor read-only（TextMate 高亮、行号、横向滚动、搜索、跳转行）
- 大文件 / binary 提示；SVG/图片预览；Markdown 文件可切 Rendered/Source
- 编码处理与 CRLF 保留

### 8.2 代码编辑（Sora Editor）

- 全功能：undo/redo、查找替换、自动缩进、括号配对、软换行切换、等宽字体
- **主题同步**：由当前 Material You 主题生成编辑器配色：

```text
editor bg      = surfaceContainerLow
text           = onSurface
line number    = onSurfaceVariant
selection      = primaryContainer
current line   = surfaceContainerHigh
keyword        = tertiary
string         = success
comment        = onSurfaceVariant
function       = primary
number         = secondary
```

- 不照搬 IDE 主题，保持 App 配色一致性

### 8.3 Diff 视图

- 自研 Compose 统一 diff（行号 + `+/-/context` 着色 + 空白行数处理）
- 行内评论点位计算（REST review 参数 position/side 或 GraphQL anchor）
- 支持分屏（side-by-side）与统一视图切换
- v1 不强行完整自研所有边缘情况，复杂度过高时 WebView 兜底

---

## 9. 搜索设计

### 9.1 范围

- Repositories / Users / Issues / Pull Requests / Code（可选 Discussions）

### 9.2 UI

- M3 SearchBar：搜索历史（Room）、qualifier 快速建议、结果 Tabs（仓库/用户/issue）
- 结果列表单独 Paging，代码搜索需登录

### 9.3 API

- REST：`/search/repositories?q=…`、`/search/issues`、`/search/users`、`/search/code`
- 注意：代码搜索有额外限制；限流处理 + 结果缓存

---

## 10. 项目架构与模块化

### 10.1 模块结构

```text
app/
core/
  common/           常量、扩展、时间格式化、硬编码治理
  designsystem/     主题引擎 / 组件 / 图标 / tokens / 动效
  ui/               App 级组件（Avatar、StateView、MarkdownHost、Timeline…）
  navigation/       AppRoute、GitHubLinkParser、Navigator
  github-graphql/   Apollo client + queries + mutations + 模型
  github-rest/      Retrofit 服务 + DTO + 错误映射
  github-auth/      AppAuth + token 存储 + 会话
  github-data/      Repository 实现、mapper、PagingSource、缓存策略
  markdown/         渲染器抽象 + 原生渲染 + WebView 渲染 + 特性探测
  editor/           Sora 封装 + 主题映射 + Diff 视图
  database/         Room DAO / migration
  datastore/        DataStore（设置）
  testing/          Fake 数据 / 测试基础设施 / 截图基础
feature/
  auth/ home/ repo/ issue/ pullrequest/ search/ editor/
  settings/ notifications/ profile/
```

### 10.2 模块职责要点

- `core:github-*` 只依赖网络与模型，不依赖 UI
- `core:markdown` 内部隔离 WebView（AndroidView 仅存在于该模块内部）
- `core:editor` 隔离 Sora 依赖（未来替换低成本）
- feature 之间通过 navigation 深链交互，不互相引用
- **Konsist** 校验：分层依赖方向；`core:model` 禁止 import android 包

### 10.3 状态管理（单向数据流）

```kotlin
data class IssueDetailUiState(
    val status: UiStatus,               // Loading / Content / Error
    val issue: IssueUiModel?,
    val timeline: PagingData<TimelineUiModel>,
    val canEdit: Boolean,
    val canComment: Boolean,
)

@HiltViewModel
class IssueDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getIssueDetail: GetIssueDetailUseCase,
    private val issueActions: IssueActions,
) : ViewModel() {
    val uiState: StateFlow<IssueDetailUiState>
}
```

- 写操作为事件通道：乐观更新 / 失败回滚 / Snackbar 错误规整

---

## 11. 国际化 i18n

### 11.1 字符串资源

- `values/`（默认英文）+ `values-zh-rCN/`（中文本地化）+ 可选（日/西…）
- Compose 一律 `stringResource(R.string.…)`；含 contentDescription

### 11.2 复数与时间的格式化

```xml
<plurals name="issue_comments_count">
  <item quantity="one">%d comment</item>
  <item quantity="other">%d comments</item>
</plurals>
```

- 相对时间（3 分钟前 / yesterday / 2 days ago）本地化；绝对时间走 `java.time` + locale
- 不硬编码："3 分钟前" 这类字符串

### 11.3 RTL

- 一律 `start/end`；代码块保持 LTR
- WebView 内 Markdown 按内容 `dir` 处理
- 阿拉伯语布局纳入截图测试矩阵

### 11.4 Lint

- 开启 `MissingTranslation / HardcodedText / SetTextI18n / StringFormatInvalid`

---

## 12. 测试规划（全 JVM）

### 12.1 金字塔

```
E2E（可选，真机自测）
↑
Compose UI 测试（Robolectric + compose-test）
↑
集成测试（MockWebServer / Apollo MockServer）
↑
单元测试（JUnit4 + MockK + Turbine）
```

### 12.2 单元测试

- ViewModel、UseCase、Repository、Mapper
- **GitHubLinkParser**（绝对/相对/引用组合矩阵）
- Markdown 特性检测、模板令牌生成器、CSS 生成器
- 配额/限流处理、PagingSource

### 12.3 API 集成测试

- OkHttp MockWebServer（REST）：401/403/404/409/422/429、ETag 304、分页
- Apollo MockServer（GraphQL）：正常/部分错误/分页游标

### 12.4 Compose UI 测试（Linux 免模拟器）

- 环境：Robolectric 4.10+（Native Graphics）+ compose ui-test、`testOptions.unitTests { isIncludeAndroidResources = true }`
- 覆盖：IssueHeader 状态、StateChip、LabelChips、PR Tabs、主题切换、Markdown 链接点击、空/错/加载态

### 12.5 截图测试（Roborazzi）

- 矩阵：Light / Dark / OLED / Dynamic color mock / 高对比；en / zh / ar-RTL；大字；各预设主题
- 支持「点击后再截」：PullToRefresh、评论打开后状态
- 产物：`build/outputs/roborazzi/*.png` + diff 图 —— Linux 本机即可预览

### 12.6 本机运行命令（Linux）

```bash
./gradlew :app:testDebugUnitTest            # 全量单测+Robolectric+Compose 行为
./gradlew :app:recordRoborazziDebug         # 生成/更新截图基准
./gradlew :app:verifyRoborazziDebug         # 校验截图
./gradlew :app:verifyRoborazziDebug --tests "*Markdown*"   # 只跑 MD 快照
./gradlew :app:konsistCheck :app:detekt :app:lintDebug
./gradlew :app:assembleDebug
```

---

## 13. CI/CD 规划（GitHub Actions）

### 13.1 PR/主分支检查

```yaml
name: CI
on:
  pull_request:
  push: { branches: [main] }
concurrency:
  group: ${{ github.workflow }}-${{ github.ref }}
  cancel-in-progress: true
jobs:
  check:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { distribution: temurin, java-version: 21 }
      - uses: gradle/actions/setup-gradle@v4
      - run: ./gradlew spotlessCheck detekt lintDebug konsistCheck
      - run: ./gradlew testDebugUnitTest
      - run: ./gradlew verifyRoborazziDebug
      - run: ./gradlew assembleDebug
      - uses: actions/upload-artifact@v4
        with: { name: app-debug, path: app/build/outputs/apk/debug/app-debug.apk }
```

### 13.2 发布流程

- tag v* → 版本号由 gradle 读取 → 签名 keystore（GitHub Secrets）→ AAB/APK → GitHub Release 草稿（可选 Play Internal Track）→ 上传 mapping 文件

### 13.3 质量门禁

- spotless、detekt、lint、konsist、单测、截图、assemble 全绿方可合并
- 无新增硬编码字符串、无新增禁用 API（lint 规则）

### 13.4 依赖管理

- Dependabot / Renovate：每周自动升级；Compose BOM / Kotlin / AGP 单独分组；breaking change 人工审查

---

## 14. 性能优化

### 14.1 目标

| 指标 | 目标 |
|---|---|
| 冷启动 | < 1.5s（中端机） |
| 首页可交互 | < 2s |
| README 首次渲染 | < 800ms；缓存命中 < 300ms |
| 列表滚动 | 稳定 60fps（高刷 90/120） |
| APK | 依赖有意图（R8 + shrink） |

### 14.2 Compose

- stable 类 + remember/derivedStateOf + LazyColumn `key`/`contentType`
- Markdown 渲染结果缓存复用
- Coil 图片缓存；虚拟滚动零废图

### 14.3 Baseline Profiles

- androidx.benchmark 生成：启动路径、首页滚动、Issue 详情、README 渲染、主题切换
- APK 内置 ProfileInstaller

### 14.4 WebView 内存

- 单实例复用；离开页面 destroy；减少列表中 WebView；LeakCanary 监控

---

## 15. 无障碍与安全

### 15.1 无障碍

- 全部可点元素 contentDescription；状态语义（StateChip `role/statesDescription`）
- Heading 层级、48dp 触区、对比度达标、TalkBack 走查、减动画、大字号、RTL

### 15.2 Token 安全

- EncryptedSharedPreferences + Keystore；日志脱敏；不注入 WebView；异常上报过滤

### 15.3 HTML 安全

- DOMPurify 白名单 sanitize；禁 script/iframe（白名单外）；危险 scheme 阻断（仅 http/https/mailto）

### 15.4 网络安全

- 仅官方 api.github.com；图片代理 host 白名单；不加载第三方脚本

---

## 16. 风险与对策

| 风险 | 影响 | 对策 |
|---|---|---|
| Markdown 渲染不一致 | README/Issue 显示不理想 | 原生优先 + 服务端 HTML 兜底 + 快照回归集 |
| WebView 性能/内存 | 卡顿、OOM | 仅复杂内容用、复用单实例、快典 Destroy |
| API 限流（REST/GraphQL） | 请求失败 | ETag + Room + Apollo 缓存 + 精细查询 + 分流 |
| Highlights 语言覆盖不足 | 部分代码块无高亮 | v2 接 prism4j 自定义 codeFence 组件 |
| fine-grained PAT 不支持 GraphQL | GraphQL 功能不可用 | PAT 模式降级 REST-only |
| 文件编辑冲突 | 用户数据丢失 | sha 校验、409 拦截、本地草稿保留 |
| Material You × GitHub 语义冲突 | 状态识别不清 | 扩展语义色 token 体系 |
| sora-editor LGPL-2.1 | 闭源合规 | 开源项目合法使用；闭源前再做评估 |

---

## 17. 最终推荐技术栈清单

```text
基础：Kotlin · Jetpack Compose(BOM) · Material 3 · Lifecycle/ViewModel
      Navigation Compose · Paging 3 · Hilt · DataStore · Room · Coil 3
构建：Gradle version catalog · Convention plugins · AGP 最新稳定 · JDK17/21
网络：OkHttp · Retrofit + kotlinx-serialization · Apollo Kotlin 5 · Chucker(debug)
Markdown：mikepenz multiplatform-markdown-renderer 0.43.0（-m3、-code）
  + WebViewAssetLoader + 服务端 HTML（/readme html / POST /markdown）+ markdown-it 兜底
代码/编辑：Rosemoe Sora Editor（editor-compose + language-textmate）
语法高亮：Highlights（原生代码块）→ prism4j（扩展）；Shiki（Web 兜底）
主题：Material Color Utilities + dynamic color + 扩展语义色 + CSS 变量桥
图标：Material Symbols（compose-icons）+ Octicons（GitHub 专属）
测试：JUnit4 · Robolectric(RNG) · Roborazzi · MockK · Turbine · MockWebServer · Apollo MockServer
CI/CD：GitHub Actions · spotless · detekt · Android Lint · Konsist · Roborazzi · 签名 Release
i18n：values/en-zh + plurals + lint 规则
```

---

## 18. 收尾说明

> 一句话：**Kotlin + Compose + Material 3 全原生 UI；GraphQL 读、REST 写、AppAuth+PKCE 认证；Markdown 原生为主、WebView 服务端 HTML 兜底并在双端共享同一套 Material You 令牌与统一链接解析器；代码浏览编辑用 Sora Editor；全链路 JVM 测试在 Linux 上免模拟器运行；GitHub Actions 支撑 CI、截图回归与发布闭环；从基建第一天落实 i18n、令牌化与硬编码红线。**