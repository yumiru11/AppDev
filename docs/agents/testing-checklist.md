# 业务逻辑全覆盖测试清单（分点方案）

> 2026-08-16 定稿。对照 `docs/agents/unit-test-coverage.md` 26 节逐层盘点，保证**逻辑业务几乎全覆盖**。
> 状态标注：✅ 已有测试 / 🔶 部分 / ❌ 缺失。覆盖标准：每业务点至少 正常+边界+异常 三类；状态类补 4 态。
> 门禁目标：纯逻辑 90% / 数据层 80% / ViewModel 75%（见 testing-strategy.md）。

## A. 纯逻辑层（解析/映射/转换）——目标 90%

| # | 业务点 | 文件 | 状态 | 需补测试点 |
|---|---|---|---|---|
| A1 | GitHub 链接解析（绝对/相对/@/#/sha/深链） | core:navigation GitHubLinkParser | ✅ 55 测试 | 畸形 URL、超长输入、编码字符、协议相对 |
| A2 | 路由表（AppRoute 双向） | core:navigation AppRoute | ✅ | 未知路由回退 |
| A3 | Alert 解析（5 类型） | core:markdown native/GitHubAlertParser | ✅ | 大小写/嵌套/无标题 |
| A4 | HTML 徽章解析（img/a/align） | core:markdown native/HtmlBadgeParser | 🔶 | 多 img、无 src、相对 src、非 http |
| A5 | details 解析 | core:markdown native/HtmlDetailsParser | ✅ | 无 summary/未闭合/跨块 |
| A6 | 表格解析（表头/行/列） | core:markdown native/MarkdownTableParser | ✅ | 空单元格/合并列/畸形 |
| A7 | FeatureDetector 分流 | core:markdown webview/FeatureDetector | ✅ | mermaid 变体/数学公式边界/超长阈值 |
| A8 | HTML 清洗（XSS） | core:markdown webview/HtmlSanitizer | 🔶 | javascript:/xlink:href/未加引号属性/编码绕过 |
| A9 | WebView HTML 构建（内联 CSS/图片改写） | core:markdown webview/WebViewHtmlBuilder | 🔶 | 相对路径变体/特殊字符转义 |
| A10 | M3 主题映射（变量注入/混色） | core:markdown webview/MaterialYouFusionMapper | 🔶 | 深浅色各值/alpha 边界 |
| A11 | 相对链接改写 | core:github-rest RelativeLinkRewriter | ✅ | 畸形/锚点/裸文件名 |
| A12 | DTO→Domain 映射 | core:githubdata GitHubMappers | 🔶 | 字段缺失/空/null/超长 |
| A13 | 相对时间（刚刚/x分钟/日期） | feature:issue RelativeTime | ❌ | 边界：0/59/60 分钟、跨天、跨年、未来时间 |
| A14 | Profile 映射 | feature:profile ProfileMappers | ❌ | 空数据/布尔字段/统计 |
| A15 | 复制反馈状态机 | core:markdown CopyFeedbackState | ✅ | 连续点击/重置竞态 |
| A16 | 错误分类映射（401/403/429/网络） | core:githubdata GitHubError | 🔶 | 各状态码/重试头 |

## B. 数据层（Repository/Paging/缓存）——目标 80%

| # | 业务点 | 文件 | 状态 | 需补测试点 |
|---|---|---|---|---|
| B1 | 仓库列表（缓存+分页+刷新） | core:githubdata DefaultRepositoryRepository + ViewerRepositoriesPagingSource | 🔶 | 分页首/尾页、刷新清缓存、失败回退 |
| B2 | 用户资料 | core:githubdata DefaultUserRepository | 🔶 | 缓存/网络/失败 |
| B3 | 重试策略 | core:githubdata RetryPolicy | ✅ | 最大次数/退避/取消 |
| B4 | Feed（事件流+分页） | feature:home FeedRepository + FeedPagingSource | ✅ | 空流/6 类事件过滤/分页边界 |
| B5 | Issue 列表/详情（含时间线） | feature:issue IssueRepository + IssuePagingSource | ✅ | 时间线类型分发/PR 判别/分页 |
| B6 | 通知（分页+未读） | feature:notifications NotificationRepository + PagingSource | ✅ | 401 分类/空态/标记已读 |
| B7 | Profile 聚合（4 类 PagingSource） | feature:profile ProfileRepository + Followers/Following/Repos/Starred PagingSource | ❌ | 各分页/错误/游标 |
| B8 | README（三级降级+双 key 缓存） | feature:repo RepoRepository | ✅ | JSON 回退/缓存命中/主题失效 |
| B9 | Room DAO（README/仓库缓存） | core:database CachedReadmeDao/CachedRepositoryDao | ✅ | 插入覆盖/删除/迁移 v1→v2 |

## C. 网络层（API/拦截器）——目标 80%

| # | 业务点 | 文件 | 状态 | 需补测试点 |
|---|---|---|---|---|
| C1 | REST API ×6（User/Repo/Issue/Notif/Event/Readme） | core:github-rest api/* | 🔶 | 请求构造/分页参数/DTO 解析/错误码 |
| C2 | 认证头注入 | core:github-rest AuthTokenInterceptor | ✅ | 无 token 跳过/空 token |
| C3 | ETag 缓存（304/缓存键） | core:github-rest EtagCacheInterceptor + EtagStore | ✅ | 非 GET 跳过/缓存 miss/过期 |
| C4 | 公共头（版本/时间戳） | core:github-rest GitHubHeaderInterceptor | 🔶 | 各 header 值/重复调用 |
| C5 | Token 交换客户端 | core:github-auth OkHttpTokenEndpointClient | ✅ | 错误响应/网络失败/重试 |
| C6 | GraphQL 工厂/标量适配 | core:github-graphql GitHubApolloClientFactory + InstantAdapter | 🔶 | 时间解析边界/错误 |

## D. 认证/会话（核心业务）——目标 90%

| # | 业务点 | 文件 | 状态 | 需补测试点 |
|---|---|---|---|---|
| D1 | 回调解析（code/error/state） | core:github-auth OAuthCallbackParser | ✅ | 畸形/缺参/重复 |
| D2 | 会话状态推导 | core:github-auth AuthState + OAuthSessionManager | ✅ | 各 token 态组合 |
| D3 | Token 刷新（并发/失败/降级） | core:github-auth TokenRefresher + AuthSessionInterceptor + Downgrade | ✅ | 并发刷新单飞/刷新失败登出/PAT 降级 |
| D4 | 加密存储（损坏/空） | core:github-auth EncryptedTokenStorage | 🔶 | 损坏密文/版本迁移 |
| D5 | 登录态驱动导航 | feature:auth AuthViewModel + AuthNavigation | ✅ | 游客/登录/登出切换 |

## E. ViewModel 状态流转——目标 75%

| # | 业务点 | 文件 | 状态 | 需补测试点 |
|---|---|---|---|---|
| E1 | 首页（登录态/Feed/刷新/错误） | feature:home HomeViewModel | 🔶 | Loading→Success→Error→Empty、游客态 |
| E2 | Issue 列表/详情 | feature:issue IssueListViewModel/IssueDetailViewModel | ✅ | 重试/分页失败/时间线加载 |
| E3 | 通知 | feature:notifications NotificationsViewModel | ✅ | 401/空态/标记已读失败 |
| E4 | Profile | feature:profile ProfileViewModel | ❌ | 加载/刷新/错误/空 |
| E5 | README 详情 | feature:repo RepoDetailViewModel | ✅ | 缓存/降级/切换 |
| E6 | 设置（主题/偏好持久化） | feature:settings SettingsViewModel | ✅ | 主题切换/滑杆/持久化失败 |
| E7 | 全局：语言切换/主题跟随 | app MainActivity/AppThemeHost | 🔶 | recreate/系统深色切换/语言竞态 |

## F. UI 行为（Robolectric + Compose UI Test + 截图）

| # | 业务点 | 状态 | 需补 |
|---|---|---|---|
| F1 | 导航骨架/深链/游客直进 | ✅ 部分 | 深链全矩阵（T3 验收） |
| F2 | 登录页/回调 | ✅ | — |
| F3 | 各 Screen 状态截图基线 | ✅ 10+ 基线 | RepoDetail 基线补录（T9 遗留） |
| F4 | 底部 Tab/分区/通知面板 | ✅ | 分区切换动效 |

## G. 可注入性审计（方法论 19/20 节——时间/随机/线程）

| # | 审计点 | 现状 | 动作 |
|---|---|---|---|
| G1 | 时间依赖（RelativeTime/格式化/时间戳） | ❌ 未审计 | 重构为 Clock 注入（纯函数 + fixed clock 测试） |
| G2 | Dispatcher 注入（VM/Repository） | 🔶 部分（MainDispatcherRule） | 统一构造注入 ioDispatcher |
| G3 | 随机/ID 生成 | ✅ 无（GitHub 无本地生成） | 无需 |
| G4 | 配置/语言注入 | 🔶 | 测试固定 locale |

## H. 断言质量规矩（方法论 24 节）

- 每个测试断言**业务结果**（输出/状态/调用次数/副作用），禁用 `assertNotNull` 凑数
- 分支覆盖：条件组合全测（如登录判断 age<18/18/>18 × agreed 真假）
- 命名 `methodName_scenario_expectedBehavior`（已有约定）

## I. 执行优先级（对应 testing-strategy.md Phase C）

1. **第一批（纯逻辑缺口，成本最低）**：A13/A14/A16/A8/A9 补全 + A1 边界——单测即可
2. **第二批（数据层）**：B7 Profile 全链 + B1/B2 边界
3. **第三批（ViewModel）**：E1/E4 状态流转
4. **第四批（可注入性重构）**：G1 时间注入（影响 A13/E 层测试质量）
5. **随票规则**：新功能票必须按本清单为对应业务点补测试（CI diff coverage 门禁兜底）
