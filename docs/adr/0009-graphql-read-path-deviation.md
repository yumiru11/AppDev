# ADR-0009：GraphQL 读路径偏离 —— 接受「REST 读为主、GraphQL 收窄到少数读位」

- **状态**：已接受（2026-09-12）
- **关联**：`plan.md` §1.2 原则 3 / §3.2 API 能力表 / §4.4 GraphQL 设计、ADR-0003（PAT REST-only）、ADR-0007（WebView 主渲染）、`docs/agents/spec-audit-2026-09-11.md` §3.4 + §11 D4
- **决策者**：用户（明确：接受现状并文档化，不在本票实现新的 GraphQL 读）

## 背景

`plan.md` 原定数据层原则是「**GraphQL 读优先、REST 写优先**」：结构化读取走 GraphQL（类型安全、一次拿全），并给出了典型 Query 清单——其中包括 `IssueDetail`（`issue.body` / `bodyHTML` + `timelineItems` 游标 + `IssueComment`）。

2026-09-11 审计（spec-audit §3.4，P0 级）实测：**「GraphQL 读优先」实际未成立**——

- 全仓只有 **8 个 `.graphql` 文档**，`IssueDetail.graphql` **不存在**；
- `bodyHTML` 只出现在 `schema.graphqls` 定义与文档中，**没有任何查询引用**；
- Issue/PR 详情、timeline、通知、搜索等读路径**全部走 REST**；
- Fragment 复用未落地（8 个文档无 `fragment` 声明）、URI 自定义 scalar 未映射。

审计当时的定性是：「**静默偏离**，最伤后来者」——因此要求二选一：要么实现 GraphQL 读，要么**修订 plan.md 并记录 ADR**。用户拍板选后者：**接受 + 文档化**，本票（docs-only）不实现任何新 GraphQL 读。

## 决策

1. **接受「REST 读为主、GraphQL 收窄到少数读位」为既定架构**，不再以「GraphQL 读优先」为实施口径；`plan.md` §1.2 / §3.2 / §4.4 已回写现状并保留原计划作演进记录。
2. **本 ADR 不实现任何新读路径**。GraphQL 的存量为：读位 `Viewer` / `RepositoryOverview` / `ViewerRepositories`（分页）/ `PullRequestReviewThreads`；写位 `UpdateIssue`（任务列表勾选）/ `ResolveReviewThread` / `UnresolveReviewThread`（外加 `IssueWriteContext` 读上下文）。其余读写全部 REST。
3. **PAT REST-only 降级是一等公民**：fine-grained PAT 无 GraphQL 权限（ADR-0003），该模式下所有 GraphQL 消费点必须有 REST 替代或保守降级——#230 已落地 `isRestOnly` 门控（此前是「调用注定 403 的 GraphQL」）。
4. **回归触发条件写入本 ADR**（见文末）：满足任一条件时重新评估 GraphQL 读的引入范围，届时应新开实现票，而不是让 plan.md 再次静默失真。

## 现状对照（2026-09-12 实测，HEAD 含 #234）

| 读取域 | 实际通道 | 证据 |
|---|---|---|
| Viewer | GraphQL（PAT → REST） | `core/github-data/.../user/DefaultUserRepository.kt:34,37` |
| 仓库概览 RepositoryOverview | GraphQL（PAT → REST） | `core/github-data/.../repository/DefaultRepositoryRepository.kt:39,42` |
| 用户仓库列表分页 | GraphQL 游标（PAT → REST `/user/repos`） | `.../paging/ViewerRepositoriesPagingSource.kt:42,53` |
| PR 会话线程（reviewThreads） | GraphQL（PAT → 保守空上下文） | `feature/pullrequest/.../data/PullRequestRepository.kt:429,439` |
| Issue 列表 / 详情 / timeline | **REST**（`IssueRemoteMediator` + Room） | `feature/issue/.../data/IssueRepository.kt:59`（KDoc）、`feature/issue/.../IssueRemoteMediator.kt` |
| PR 详情 / 文件 / Diff / Checks | **REST** | `core/github-rest/.../api/PullRequestApi.kt:36,91,164` |
| 通知 / 搜索 | **REST** | `core/github-rest/.../api/NotificationApi.kt`、`SearchApi.kt` |
| 写操作（评论/Review 提交/Merge/文件） | **REST** | `PullRequestApi.kt:190`（POST reviews）、`:204`（PUT merge）、Contents API |
| Issue 写（任务列表勾选） | GraphQL mutation（PAT → REST PATCH 降级） | `IssueRepository.kt`（`UpdateIssue` + 降级注释） |

## 理由（为什么接受这个偏离）

1. **PAT 模式必须有一条完整的 REST 读路径**。fine-grained PAT 不支持 GraphQL（ADR-0003），若真做「GraphQL 读优先」，就要同时维护 GraphQL 与 REST 两套完整读栈；反之只有 REST 一套。REST 优先让 PAT 用户体验与 OAuth 用户一致。
2. **原 Query 清单的最大消费方已消失**。`IssueDetail` 存在的两大理由——`bodyHTML` 服务端渲染与 timeline 游标分页——第一条随 ADR-0007（WebView 主渲染）失效：Issue/PR 正文没有服务端 HTML API，正文一律走离线 GFM；WebView 通道真正需要的 README 服务端 HTML 本就来自 REST `/readme`。
3. **GraphQL 保留在最划算的读位**。现存的四个 GraphQL 读位有两个共同特征：嵌套深（一次性取全多字段）或游标分页（官方 Paging 对接示例），REST 对应实现要么多次请求要么手写分页。其余读取域 REST 一次请求即可。
4. **实现成本 vs 用户收益**。补齐规范的 `IssueDetail.graphql`（Fragment、URI scalar 映射、分页、Normalized Cache 接线）工作量以周计，而**用户可见收益为零**（REST 已能完整支撑现有 UI；真机反馈的问题都不在数据通道上）。

## 后果

### 正面

- PAT（REST-only）用户全功能可用；`isRestOnly` 门控（#230）让「注定 403 的 GraphQL 调用」归零。
- 详情/时间线只有一条 REST 数据路径，调试与缓存策略更简单；Room + `IssueRemoteMediator` 离线可读。
- 少维护一套 GraphQL schema 漂移面（Apollo 升级只影响 8 个文档）。

### 代价与风险

- **放弃 GraphQL 的结构性红利**：类型安全（codegen 校验）、一次拿全（少 RTT）、Normalized Cache 跨页复用——这些只在 4 个读位享有。
- **REST 限流更硬**：REST 5000/hr（GraphQL 按点数计）。重度浏览（timeline 逐页、reactions、checks）比 GraphQL 方案更早触顶；目前靠 ETag + Room + 开发者模式限流提示缓解，尚未有用户可见的限流事故记录。
- **`bodyHTML` 服务端渲染能力缺口仍在**：离线 GFM 与网页端存在已记录的差异（`docs/agents/markdown-consistency-2026-09-11.md`），若该差异升级为投诉焦点，GraphQL `IssueDetail` 重新变得值得。
- **文档口径风险**：本 ADR + plan.md 回写是唯一防回归手段——评审新票时若再写「GraphQL 读优先」而无实现意图，应指向本 ADR。

## 回归触发条件（何时重新评估）

满足**任一**条件，重新评估（新开实现票，不改本 ADR 的「已接受」状态）：

1. **PAT 约束消失或变化**：GitHub 让 fine-grained PAT 支持 GraphQL，或产品决定只支持 OAuth（放弃 PAT 模式）——此时「一套 REST 读栈」的成本优势不再成立。
2. **REST 限流成为用户可见问题**：正常浏览路径出现 403/429 rate-limit（有日志或用户反馈证据），而 GraphQL 点数方案可显著降低消耗。
3. **详情页性能瓶颈定位到 REST fan-out**：一次详情打开需要 >3 个顺序 REST 请求（timeline + comments + reactions + checks），且成帧/首屏指标确认为此受损（需实测数据，非猜测）。
4. **`bodyHTML` 服务端渲染成为需求**：离线 GFM 的渲染差异被列为高优先级缺陷，且 team 决定回到服务端 HTML 一致性路线（届时先修订 ADR-0007，再谈本 ADR）。
5. **GraphQL 存量读位验证成熟**：若 `ViewerRepositories` / `PullRequestReviewThreads` 在生产长期稳定，可顺势把**同一模式**扩展到下一个读域（优先 Issue 列表游标），渐进式而非全量重写。

## 附：本 ADR 不改变的约定

- 写路径继续「REST 写优先」：评论、Review 提交、Merge、文件提交均 REST（`PullRequestApi.kt:190,204`）。
- WebView 相关数据（README 服务端 HTML、`POST /markdown` Tier 2）继续走 REST。
- 搜索继续 REST-only（GitHub 搜索无 GraphQL 等价）。
