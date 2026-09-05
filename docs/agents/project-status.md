# AppDev 项目状态（2026-08-21）

> 本文件是当前进度的**权威快照**。每张票合并/关闭后更新。配合 `docs/agents/workflow.md`（流程）与 `AGENTS.md`（环境）阅读。

## 1. 里程碑概览

| 里程碑 | 状态 |
|---|---|
| M0 基建（Gradle 骨架/CI/架构守卫） | ✅ 完成 |
| M1 核心链路（网络/认证/主题/Markdown 渲染） | ✅ 完成 |
| M2 首个端到端（README 浏览） | ✅ 完成 |
| M3 主要功能域（首页/Issue/通知/Profile/设置） | 🔶 进行中（21/26 票） |
| M4 全功能（PR 深化/编辑提交/分支） | 🔶 进行中（T16/T17/T23 待做） |
| M5 发布收尾（性能/签名 Release） | ⏳ 未开始（T25） |

## 2. 已完成（26 票计划内 21 票全部合入 main；另有 Task B、UI 打磨波等计划外交付）

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
| Task B 渲染切换 | #71 | WebView 主渲染（服务端 HTML + 离线 GFM 两级）PR #70/#73 合入 | ✅ |
| T11 文件树浏览 | #12 | Git Data 递归树 + Sora read-only + M3 编辑器主题 | ✅ |
| T18 全局搜索 | #19 | 四类结果 Tab + 历史 + qualifier + 代码搜索门禁 | ✅ |
| T12 仓库管理 | #13 | Star/Watch/Fork + Releases/Tags + 语言栏（PR #74） | ✅ |
| T14 Issue 写 | #15 | 创建/编辑/评论/反应/关闭/任务列表同步（PR #75） | ✅ |
| T21 Markdown 编辑器 | #22 | Sora 编辑 + 工具栏 + 编辑/预览双 Tab（PR #77） | ✅ |
| T15 PR 列表详情 | #16 | 四 Tab Conversation/Commits/Checks/Files changed（PR #76） | ✅ |
| T22 文件编辑提交 | #23 | Contents API + sha 校验 + 409 三选项（重载/覆盖/保留本地，PR #96） | ✅ |
| UI 打磨波 | #82 | Home Tab M3 化、PR 评论、Star/Watch 按钮（PR #82） | ✅ |
| UI 审查修复批·通知面板 | #88 | 面板完整形态：右侧滑入+遮罩+Haze 玻璃、按仓库分组折叠、右滑已读回弹/左滑 done 删除、未读左缘色条、筛选 chips 迁移、三态组件接入；旧独立路由与 T19 分页栈随面板化移除 | ✅ |
| UI 审查修复批·导航现代化 | #90 | 全局转场（slideX+fadeIn/scaleOut，LocalMotionScale 缩放）+ 预测返回（enableOnBackInvokedCallback）+ AppRoute 迁移 @Serializable 类型安全路由 + 共享元素试点（仓库头像→RepoHeader）；MainTab 分区键与导航路由解耦（PR #110） | ✅ |
| UI 审查修复批·毛玻璃 backdrop | #83 | 顶栏 backdrop 接线补完：小分区条并入玻璃头（AppTopBar sectionBar 副行）、内容 full-bleed 且玻璃头高度改走 contentPadding、长条按钮随 feed 滚动；渲染模式判定收敛成可单测纯函数 GlassRenderPolicy；app 侧截图基线零变更（PR #108 / merge 8bcaec6）| ✅ 代码合入，票待真机穿越感复测后关 |

## 3. 剩余 5 票（功能线，依赖已全部满足除注明外）

| Ticket | Issue | 难度 | 内容 | 备注 |
|---|---|---|---|---|
| T16 Diff + 行评论 | #17 | ★★★★ | 自研 Diff 渲染 + 行评论 BottomSheet | 可立即开工；与 UI 票 #85 有文件冲突需错峰 |
| T17 Review/Merge | #18 | ★★★ | approve/merge/squash/rebase（建议 SplitButton） | 依赖 T16 |
| T23 分支/PR 创建 | #24 | ★★★ | 分支列表 + 创建 PR | 可立即开工 |
| T25 性能发布 | #26 | ★★★ | Baseline Profiles + i18n 完整 + 签名 Release | 全场最后 |

## 4. 进行中：UI 审查修复批（2026-08-21 立项）

来源 `docs/ui-audit-2026-08-21.md`，8 张票（label `ui-audit`），依赖边：#87/#88/#89/#90 ←blocked_by— #84。

| Issue | 标题 | 依赖 |
|---|---|---|
| #83 | fix(designsystem): 毛玻璃改为 backdrop 模糊实现 | **PR #108 已合入 main（8bcaec6）**；票保持 open —— 关票条件是真机「滚动穿越感」复测 + FEEDBACK #17 闭环（验收卡见 PR #108 / 票上 2026-09-05 评论） |
| #84 | feat(designsystem): 共享状态组件族与动效缩放基建 | 无，**依赖根，最优先** |
| #85 | fix(issue,pr): 标签混色与状态徽章语义色 | 仅 StatusChip 行等 #84；⚠️ 与 T16 文件冲突错峰 |
| #86 | perf(list): Paging itemKey 迁移与模型稳定性标注 | 无；Home 屏行与 #89 错峰 |
| #87 | feat(settings): 设置页分组重构与个性化项接线 | blocked_by #84 |
| #89 | feat(home): 首页分区 Pager 与长条按钮 | blocked_by #84 |
| #90 | feat(navigation): 全局转场、预测返回与路由现代化 | **PR #110 已合入 main（a0ea8b6）**；票已关闭——真机预测返回手势复测见 PR #110 验收卡 |

待用户决策（不建 issue，见 audit 文档 §3.0）：图标候选批准 / 卡片两版对比 / 背景图 grill / README 收起动画构思。

## 5. 遗留事项（未闭环）

| 项 | 状态 |
|---|---|
| RepoDetail 截图基线（light/dark） | 待真机补录（T9 收尾时删除，标注待补） |
| WebView 私有图代理 + 缓存 <300ms | 待真机验证 |
| 真机走查机制（每票合并前的截图/真机验收） | 待建（截图工具调研在 docs/research-questions.md §7） |
| 图标候选清单（底部导航实心/空心） | 用户要求选出候选验证批准后才用（ADR-0006） |
| PiliPlus 卡片风格细节 | 实现时提取参照给两版对比（docs/ui-design.md §11） |
| Material Symbols 变量字体（wght=300） | ADR-0004 挂账；静态 outlined/filled 变体已可用 |
| **模块级截图基线不在任何门禁里**（#83 取证发现） | CI 只跑 `:app:verifyRoborazziDebug`。在**干净 main** 上跑 `:core:ui:verifyRoborazziDebug` / `:core:designsystem:verifyRoborazziDebug` 即失败（core:ui 7 张含我完全没碰的 `AppBottomBar_*` 全套；designsystem 2 张 `GlassSurface_*`），且与改动分支渲染出的 `*_actual.png` 逐字节 `cmp` **IDENTICAL** → 属基线录制环境与本机不一致的既有漂移，非改动引入。修法二选一：把模块级 `verifyRoborazziDebug` 纳入 CI，或重录基线并注明录制环境 |

## 6. 更新规则

- 每票合并 → 移入「已完成」表 + 更新里程碑
- 进行中任务开始/结束 → 更新第 4 节
- 遗留项闭环 → 从第 5 节移除
- 本文档由主代理维护（不派子代理更新）


## 工作流 grill 结论（2026-08-16）

- 已拍板：验收卡 + 分级验收、PR 测试清单、意图直读文档/ASCII 图、里程碑走查、设计闸门、基线改动需批准、全局性问题专项票
- 完整规则见 `docs/agents/workflow.md` §4；问题与用户回答原文见 `docs/workflow-grill.md`
- **待办**：截图工具调研（CICD 自动截图 + PR 评论）——用户用别的 AI 跑 research-questions.md 截图相关


## 测试覆盖率推进（2026-08-16 规划）

- 策略：`docs/agents/testing-strategy.md`（JaCoCo 0.8.13+ / 分层目标 / diff coverage 门禁）
- 分点清单：`docs/agents/testing-checklist.md`（A 纯逻辑 / B 数据层 / C 网络 / D 认证 / E ViewModel / F UI / G 可注入性 / H 断言质量）
- 待办：Phase A 接 JaCoCo → Phase B CI 门禁 → Phase C 按清单补测试（优先 A13/A14/A16/A8/A9 + B7 + E1/E4）→ Phase D UI 自动化
