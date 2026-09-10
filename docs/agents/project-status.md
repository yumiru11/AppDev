# AppDev 项目状态（2026-09-10）

> 本文件是当前进度的**权威快照**。每张票合并/关闭后更新。配合 `docs/agents/workflow.md`（流程）、`AGENTS.md`（环境）与 `docs/agents/task-audit-2026-09-06.md`（全量审计）阅读。
> 本版修正 2026-09-06 审计发现的 §7 D01「文档三处失真」：AGENTS.md / project-status.md / FEEDBACK.md 已与 `gh` 票面 + git 历史对齐。

## 1. 里程碑概览

| 里程碑 | 状态 |
|---|---|
| M0 基建（Gradle 骨架/CI/架构守卫） | ✅ 完成 |
| M1 核心链路（网络/认证/主题/Markdown 渲染） | ✅ 完成 |
| M2 首个端到端（README 浏览） | ✅ 完成 |
| M3 主要功能域（首页/Issue/通知/Profile/设置） | ✅ 完成（T1–T26 中 24 票） |
| M4 全功能（PR 深化/编辑提交/分支） | ✅ 完成（T16/T17/T23 已合入） |
| M5 发布收尾（性能/签名 Release） | 🔶 **T25（#26）唯一未开工的功能票** |
| M6 审计补全与 UI 打磨（2026-09-06 立项） | 🔶 进行中（9 张分类票 #163–#171） |

## 2. 已完成（T1–T26 中 25 票 + 计划外交付全部合入 main）

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
| T11 文件树浏览 | #12 | Git Data 递归树 + Sora read-only + M3 编辑器主题 | ✅ |
| T12 仓库管理 | #13 | Star/Watch/Fork + Releases/Tags + 语言栏（PR #74） | ✅ |
| T13 Issue 列表/详情 | #14 | 时间线 + reactions + 截图基线（云端补录） | ✅ |
| T14 Issue 写 | #15 | 创建/编辑/评论/反应/关闭/任务列表同步（PR #75） | ✅ |
| T15 PR 列表详情 | #16 | 四 Tab Conversation/Commits/Checks/Files changed（PR #76） | ✅ |
| T16 Diff + 行评论 | #17 | DiffParser + PullRequestDiffView（unified/side-by-side）+ LineCommentSheet（PR #101） | ✅ |
| T17 Review/Merge | #18 | ReviewSheet + MergeBox（SplitButton）+ merge/update-branch/delete-ref（PR #102） | ✅ |
| T18 全局搜索 | #19 | 四类结果 Tab + 历史 + qualifier + 代码搜索门禁（PR #62） | ✅ |
| T19 通知 | #20 | 面板（#88）：分组折叠/滑动/筛选/全部已读/遮罩 0.5（PR #52/#98） | ✅ |
| T20 Profile | #21 | Repositories/Starred/Followers/Following 四路 Paging（PR #53） | ✅ |
| T21 Markdown 编辑器 | #22 | Sora 编辑 + 工具栏 + 编辑/预览双 Tab（PR #77） | ✅ |
| T22 文件编辑提交 | #23 | Contents API + sha 校验 + 409 三选项（PR #96） | ✅ |
| T23 分支/PR 创建 | #24 | BranchesScreen + GitRefApi + PullRequestCreateScreen（PR #105） | ✅ |
| T24 设置 | #25 | 主题/seed/圆角/动效滑杆/语言/毛玻璃总开关/PAT/配额（PR #54/#99） | ✅ |
| T26 M3 高亮 | #27 | M3TextMateTheme/GitHubTextMateTheme（PR #55） | ✅ |
| Task B 渲染切换 | #71 | WebView 主渲染（服务端 HTML + 离线 GFM 两级）PR #70/#73 合入 | ✅ |
| UI 打磨波 | #82 | Home Tab M3 化、PR 评论、Star/Watch 按钮（PR #82） | ✅ |
| ui-audit #83 毛玻璃 backdrop | #83 | 顶栏 backdrop 接线补完 + GlassRenderPolicy（PR #108） | ✅ |
| ui-audit #84 组件族/动效基建 | #84 | 共享状态组件族 + LocalMotionScale（#92 起） | ✅ |
| ui-audit #85 标签混色/语义色 | #85 | LabelChipColors + StatusChip 语义色 | ✅ |
| ui-audit #86 Paging itemKey | #86 | itemKey 迁移与模型稳定性标注 | ✅ |
| ui-audit #87 设置分组 | #87 | 设置页分组重构与个性化项接线 | ✅ |
| ui-audit #88 通知面板 | #88 | 面板完整形态（滑入+遮罩+Haze 玻璃+分组折叠+滑动操作） | ✅ |
| ui-audit #89 首页 Pager/长条按钮 | #89 | 首页分区 Pager 与 LongBarAction | ✅ |
| ui-audit #90 导航现代化 | #90 | 全局转场 + 预测返回 + @Serializable 类型安全路由 + 共享元素试点（PR #110） | ✅ |
| 全量审计 | — | `docs/agents/task-audit-2026-09-06.md`（51 项发现 → 9 张分类票） | ✅ 报告入库 |

## 3. 进行中：审计补全波（2026-09-06 立项，9 张分类票 #163–#171）

> 来源 `docs/agents/task-audit-2026-09-06.md`。分类票映射见审计报告 §5.1。
> 建议执行顺序见审计报告 §12：**#171 → #169/#170 → #163 → #164 → #165 → #166 → #167 → #168**。

| Issue | 标题 | 覆盖审计项 | 状态 |
|---|---|---|---|
| #171 | docs(agents): 回写真实状态 | D01 | 🔶 本票（本文件即产出） |
| #169 | chore(app): 工程收尾 | L14/L15/L16/Q03/Q04 | ⏳ 待做 |
| #170 | fix(ci): 质量门禁修复 | Q01/Q02/D02/D03/D04 | ⏳ 待做 |
| #163 | feat(issue,pr): Issue/PR 写操作补全 | L01/L02/L03 | ⏳ 待做 |
| #164 | feat(repo): 仓库域功能补全 | L04/L05/L06/L09 | ⏳ 待做 |
| #165 | feat(data,home,profile): 内容与数据域补全 | L07/L08/L10–L13 | ⏳ 待做 |
| #166 | feat(ui): UI 打磨一期·布局与页面 | UI01/02/05/11/14/19/20/21 | ⏳ 待做 |
| #167 | feat(ui): UI 打磨二期·动效体系 | UI03/04/06–10/15–18/22 | ⏳ 待做 |
| #168 | feat(ui): UI 打磨三期·图标与无障碍 | UI12/13/23–27 | ⏳ 待做 |
| #26 | T25: 性能与发布收尾 | plan §12.5/§14.1 | ⏳ 待做（与 #169 的 R8/签名项协调） |

常开不关：**#1**（Spec）、**#71**（Markdown 渲染测试面板）。

## 4. 遗留事项（未闭环）

| 项 | 状态 |
|---|---|
| RepoDetail 截图基线（light/dark） | 待补录（T9 收尾时删除，标注待补）→ 归 #170 模块级基线议题 |
| WebView 私有图代理 + 缓存 <300ms | 待真机验证 |
| 真机走查机制（每票合并前的截图/真机验收） | 仍未流程化；截图工具调研见 `docs/research-questions.md` §7 |
| 图标候选清单（底部导航实心/空心） | 归 #168（UI12/UI13/UI23） |
| PiliPlus 卡片风格细节 | 归 #166（UI01 网格/通栏选型） |
| Material Symbols 变量字体（wght=300） | 归 #168（UI13 变量字体四轴评估） |
| **模块级截图基线不在任何门禁里** | 归 #170（D02） |
| Compose lint 15 项检查失效 | 归 #170（Q01） |
| diffCoverage 软门禁从未实测 | 归 #170（D04） |
| Nightly 全量截图/多设备/性能基线 | 归 #170（D03） |
| 应用图标缺失 / Bundle 语言拆分 | 归 #169（Q03/Q04） |

## 5. 更新规则

- 每票合并 → 移入「已完成」表 + 更新里程碑
- 进行中任务开始/结束 → 更新第 3 节
- 遗留项闭环 → 从第 4 节移除
- 本文档由主代理维护（不派子代理更新）

---

## 工作流 grill 结论（2026-08-16）

- 已拍板：验收卡 + 分级验收、PR 测试清单、意图直读文档/ASCII 图、里程碑走查、设计闸门、基线改动需批准、全局性问题专项票
- 完整规则见 `docs/agents/workflow.md` §4；问题与用户回答原文见 `docs/workflow-grill.md`

## 测试覆盖率推进（2026-08-16 规划）

- 策略：`docs/agents/testing-strategy.md`（JaCoCo 0.8.13+ / 分层目标 / diff coverage 门禁）
- 分点清单：`docs/agents/testing-checklist.md`（A 纯逻辑 / B 数据层 / C 网络 / D 认证 / E ViewModel / F UI / G 可注入性 / H 断言质量）
- 当前：JaCoCo + `coverageVerify` 硬门禁已上线；diff coverage 仍为软门禁（D04 实测归 #170）
