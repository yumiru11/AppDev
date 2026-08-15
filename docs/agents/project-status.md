# AppDev 项目状态（2026-08-15）

> 本文件是当前进度的**权威快照**。每张票合并/关闭后更新。配合 `docs/agents/workflow.md`（流程）与 `AGENTS.md`（环境）阅读。

## 1. 里程碑概览

| 里程碑 | 状态 |
|---|---|
| M0 基建（Gradle 骨架/CI/架构守卫） | ✅ 完成 |
| M1 核心链路（网络/认证/主题/Markdown 渲染） | ✅ 完成 |
| M2 首个端到端（README 浏览） | ✅ 完成 |
| M3 主要功能域（首页/Issue/通知/Profile/设置） | 🔶 进行中（15/26 票） |
| M4 全功能（PR/搜索/编辑/分支） | ⏳ 未开始 |
| M5 发布收尾（性能/签名 Release） | ⏳ 未开始 |

## 2. 已完成（15 票，全部合入 main）

| Ticket | Issue | 内容 | 合入 |
|---|---|---|---|
| T1 CI/CD | #2 | GitHub Actions Quality Gate + 签名 Release | ✅ |
| T2 架构守卫 | #3 | Konsist + core:testing + 截图基线 | ✅ |
| T3 导航骨架 | #4 | GitHubLinkParser + AppRoute + 底部导航 | ✅ |
| T4 认证 | #5 | PKCE + TokenStorage + 刷新降级 + 登录 UI | ✅ |
| T5 网络层 | #6 | Retrofit3/Apollo5 + 错误归一 + Room/DataStore | ✅ |
| T6 主题引擎 | #7 | 6 套主题 + 动态色 + 令牌族 + 玻璃顶底栏 | ✅ |
| T7 原生 Markdown | #8 | mikepenz 0.38.1 + KotlinTextMate + Alert + 链接 | ✅ |
| T8 WebView 兜底 | #9 | /markdown HTML + DOMPurify + markdown-it + hljs | ✅ |
| T9 README 浏览 | #10 | FeatureDetector 分流 + 相对链接 + JSON 回退 | ✅ |
| T10 首页 feed | #11 | Events API + 6 类事件过滤 + Home 分区 | ✅ |
| T13 Issue 列表/详情 | #14 | 时间线 + reactions + 截图基线（云端补录） | ✅ |
| T19 通知 | #20 | 通知面板 + 按仓库分组（UI 票：改时间排序） | ✅ |
| T20 Profile | #21 | 资料头 + Stars/Gists 入口 | ✅ |
| T24 设置 | #25 | 主题/圆角/动画/语言/图标风格 | ✅ |
| T26 M3 高亮 | #27 | M3TextMateTheme（半融合变体待 UI 票） | ✅ |

## 3. 剩余 11 票（依赖链排序）

### 🟢 可立即做（无阻塞）

| Ticket | Issue | 难度 | 内容 |
|---|---|---|---|
| T11 文件树与代码浏览 | #12 | ★★★ | 文件树 + Sora read-only + 分支/路径选择 |
| T12 仓库管理 | #13 | ★★ | Star/Watch/Fork + Releases/Tags |
| T18 搜索 | #19 | ★★★ | 仓库/用户/Issue/PR 搜索 + Paging |

### 🟡 功能域主干

| Ticket | Issue | 难度 | 依赖 | 内容 |
|---|---|---|---|---|
| T14 Issue 写 | #15 | ★★★★ | T13 | 创建/编辑/评论/反应/关闭 |
| T15 PR 列表详情 | #16 | ★★★★ | T13 | 四 Tab（最复杂页面） |
| T16 Diff + 行评论 | #17 | ★★★★ | T15 | 自研 Diff 渲染 |
| T17 Review/Merge | #18 | ★★★ | T16 | approve/merge/squash/rebase |
| T21 Markdown 编辑器 | #22 | ★★★★ | T7 | Sora 编辑 + 预览一致 |
| T22 文件编辑提交 | #23 | ★★★★ | T11+T21 | Contents API + 409 |
| T23 分支/PR 创建 | #24 | ★★★ | T15 | 分支列表 + 创建 PR |

### 🔴 收尾

| Ticket | Issue | 难度 | 依赖 | 内容 |
|---|---|---|---|---|
| T25 性能发布 | #26 | ★★★ | 全部 | Baseline Profiles + i18n 完整 + 签名 Release |

## 4. 进行中

| 任务 | 分支 | 执行方式 | 说明 |
|---|---|---|---|
| README 渲染原型 | `prototype/readme-comparison` | DSH × V4 Pro（见 dsh-guide.md） | 双版本对照（WebView 融合 vs 原生增强），产出对比报告后用户拍板路线 A/B/C |

## 5. 遗留事项（未闭环）

| 项 | 状态 |
|---|---|
| RepoDetail 截图基线（light/dark） | 待真机补录（T9 收尾时删除，标注待补） |
| WebView 私有图代理 + 缓存 <300ms | 待真机验证 |
| 真机走查机制（每票合并前的截图/真机验收） | 待建（截图工具调研在 docs/research-questions.md §7） |
| 图标候选清单（底部导航实心/空心） | 用户要求选出候选验证批准后才用（ADR-0006） |
| PiliPlus 卡片风格细节 | 实现时提取参照给两版对比（docs/ui-design.md §11） |
| Material Symbols 变量字体（wght=300） | ADR-0004 挂账；静态 outlined/filled 变体已可用 |

## 6. 更新规则

- 每票合并 → 移入「已完成」表 + 更新里程碑
- 进行中任务开始/结束 → 更新第 4 节
- 遗留项闭环 → 从第 5 节移除
- 本文档由主代理维护（不派子代理更新）
