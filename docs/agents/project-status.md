# AppDev 项目状态（2026-09-12）

> 本文件是当前进度的**权威快照**。每张票合并/关闭后更新。配合 `docs/agents/workflow.md`（流程）、`AGENTS.md`（环境）与 `docs/agents/task-audit-2026-09-06.md`（全量审计）阅读。
> 本版修正 2026-09-06 审计发现的 §7 D01「文档三处失真」：AGENTS.md / project-status.md / FEEDBACK.md 已与 `gh` 票面 + git 历史对齐。
> **基线**：`main@217cdd7`（2026-09-12 修复波末，26 张 PR #229–#255 全部 squash 合入；#256 仍 open。完整清单见 `docs/agents/remaining-backlog-2026-09-12.md`）。

## 1. 里程碑概览

| 里程碑 | 状态 |
|---|---|
| M0 基建（Gradle 骨架/CI/架构守卫） | ✅ 完成 |
| M1 核心链路（网络/认证/主题/Markdown 渲染） | ✅ 完成 |
| M2 首个端到端（README 浏览） | ✅ 完成 |
| M3 主要功能域（首页/Issue/通知/Profile/设置） | ✅ 完成（T1–T26 中 24 票） |
| M4 全功能（PR 深化/编辑提交/分支） | ✅ 完成（T16/T17/T23 已合入） |
| M5 发布收尾（性能/签名 Release） | 🔶 进行中：Baseline Profile + i18n 覆盖断言 + RTL 基线 + 发布链路演练已落地（PR #186）；**冷启动实测 / macrobenchmark / 生产签名密钥仍需真机或 Secrets**（见 §3.2） |
| M6 审计补全与 UI 打磨（2026-09-06 立项） | ✅ 9 张分类票 #163–#170 **全部关闭并合入**；#166/#167 的少数挂账项见 §3.1 |
| M7 修复波 + 门禁/截图链路加固（2026-09-12） | ✅ **26 张 PR #229–#255 全部 squash 合入**：审计 P0 收口（OAuth 注入 #239、PAT 降级 #230）+ 设计系统/语义色 #231 + 离线 GFM #232/#237/#251 + 深链 #233/#248 + 草稿 #234 + i18n lint #235 + 覆盖率棘轮 #255 + 截图链路可信化 #245/#246/#252 + 文档回写 #236/#244（明细见 §2.1）。**#256（9 屏 20 帧基线扩展）仍 open** |

## 2. 已完成（T1–T26 中 25 票 + 计划外交付 + 2026-09-12 修复波全部合入 main）

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

### 2.1 2026-09-12 修复波（#229–#255，全部 squash 合入 main）

| PR | 范围 | 交付 |
|---|---|---|
| #229 | 文档回写 | AGENTS.md 对齐现实（状态 / 审计索引 / P0 对账 / 方法学 / `--no-daemon` 铁律） |
| #230 | 认证/数据 | PAT（fine-grained 无 GraphQL）自动降级 REST 补位端点 |
| #231 | 设计系统 | 补全 `ExtendedColors` §5.3（8 语义状态色）+ Check Run 逐状态映射 |
| #232 | 离线 GFM | 相对链接/图片重写 + emoji/脚注/锚点 |
| #233 | 导航 | Tree/Release/Search 深链落到 app 内（不再出浏览器） |
| #234 | 草稿 | 持久化扩展到文件编辑与评论/Issue/PR 表单 |
| #235 | i18n lint | 四条规则全模块启用 + 防退化 canary（修自引用恒真断言） |
| #236 | 文档 | plan.md 六处偏离回写 + 新增 ADR-0009（GraphQL 读路径现状） |
| #237 | 原生 markdown | details 重复渲染 / baseRepoUrl 透传 / coil-gif / script 正文残留 四项修复 |
| #238 | 测试 | 消除 Branches/Search/Home ViewModel 测试 `runTest` 首次初始化开销；并修 #233×#234 组合导致的 main 编译断裂 |
| #239 | 认证 | OAuth client id 三级注入点（Gradle 属性 / `local.properties` / 环境变量 → `BuildConfig`） |
| #240 | WebView 主通道 | 服务端 HTML 高亮 + 触屏复制 + 图片懒加载 + 两级缓存键（预热评估结论：不做） |
| #241 | CI 清理 | 删 `.maestro/`、截图 job 改名、修 prototype 编译 |
| #242 | CI | editor 探针改 30s 有界等待 Commit 动作 |
| #243 | 调研 | KaTeX/Mermaid 离线渲染可行性（含体积实测） |
| #244 | 文档 | 残余审计缺口清单（#229–#241 后现实核验） |
| #245 | 截图 | OLED/高对比/zh 矩阵 + RTL 方向守卫 + **修 `:feature:issue` verify 挂死根因**（`captureScreenshotDeterministic`） |
| #246 | 测试 | 相对时间夹具，扫掉 3 处日历时间炸弹 |
| #247 | 测试/CI | `WebViewDarkModePolicyTest` 补断言 + 修 `EditorView` 覆盖率排除误伤 + nightly 失败汇总补齐 |
| #248 | 深链 | `BlobRoute` 消费 `editState`，修复深链文件编辑死路 + editable 硬编码 |
| #249 | 截图 | 补 `:feature:pullrequest` 列表/详情/文件变更基线并纳入 CI verify |
| #251 | markdown | `@user` 提及渲染（离线+原生）+ 恢复 `kbd/sub/sup` 语义 |
| #252 | CI 截图探针 | 7 坏帧逐帧对账：修断言 bug、坐实 2 个真缺陷信号（`repo-star` UI-C01、`repo-actions` 探针空洞）；FAB/`checked` 语义修正 |
| #253 | 设置 | 开发者设置接入真实配额数据（GATE-2），移除「待接入」占位 |
| #254 | 清理 | 死代码/遗留文件核验后删除（`FeatureDetector` 按 ADR 保留并注释）（DEAD-1/DEAD-2） |
| #255 | 覆盖率 | 6 个无阈值模块补 LINE 棘轮（`app` / `core:common` / `core:editor` / `core:database` / `feature:search` / `feature:editor`），5 个豁免附说明 + 负向验证 |

## 3. 进行中：审计补全波（2026-09-06 立项，9 张分类票 #163–#171）

> 来源 `docs/agents/task-audit-2026-09-06.md`。分类票映射见审计报告 §5.1。
> 建议执行顺序见审计报告 §12：**#171 → #169/#170 → #163 → #164 → #165 → #166 → #167 → #168**。

| Issue | 标题 | 覆盖审计项 | 状态 |
|---|---|---|---|
| #171 | docs(agents): 回写真实状态 | D01 | ✅ 关闭（PR #172） |
| #169 | chore(app): 工程收尾 | L14/L15/L16/Q03/Q04 | ✅ 关闭（PR #174；签名 release 实测通过，APK 6.8MB） |
| #170 | fix(ci): 质量门禁修复 | Q01/Q02/D02/D03/D04 | ✅ 关闭（PR #181 + #188；Q01 的 Compose lint 需 AGP 9.x，见 §3.1） |
| #163 | feat(issue,pr): Issue/PR 写操作补全 | L01/L02/L03 | ✅ 关闭（PR #176） |
| #164 | feat(repo): 仓库域功能补全 | L04/L05/L06/L09 | ✅ 关闭（PR #179） |
| #165 | feat(data,home,profile): 内容与数据域补全 | L07/L08/L10–L13 | ✅ 关闭（PR #173 + #178） |
| #166 | feat(ui): UI 打磨一期·布局与页面 | UI01/02/05/11/14/19/20/21 | 🔶 关闭（PR #182/#183/#189/#191/#192/#193）；UI14 形态待确认，见 §3.1 |
| #167 | feat(ui): UI 打磨二期·动效体系 | UI03/04/06–10/15–18/22 | 🔶 关闭（PR #180/#184/#189/#190）；UI04/UI10/UI17/UI22 见 §3.1 |
| #168 | feat(ui): UI 打磨三期·图标与无障碍 | UI12/13/23–27 | ✅ 关闭（PR #185） |
| #26 | T25: 性能与发布收尾 | plan §12.5/§14.1 | 🔶 部分（PR #186 + 演练）；真机项待做，见 §3.2 |

常开不关：**#1**（Spec）、**#71**（Markdown 渲染测试面板）。

### 3.1 挂账项（票已关，但条目未闭环 —— 都是"需要人来拍板/需要真机"的）

| 项 | 归属 | 为什么没做 |
|---|---|---|
| **UI04 全局背景图** | #167 | 需先按 `FEEDBACK.md` #30 完成方案 grill（选图/不透明度/深色压暗/固定不动），不猜 |
| **UI10 README 头部收起** | #167 | 设计文档写明「用户构思中，实现时给两版效果对比」——需要你的两版对比，不在无人值守时替你拍板 |
| ~~UI22 BottomSheet 玻璃~~ | #167 | ✅ 已落地（本 worktree / `feature/t167-ui22-sheet-glass`）：跨 window 采样问题**已机械核实为不可能**（非真机才能确认），五处 `ModalBottomSheet` 改走 `GlassSheetSurface` 半透明路径 + 策略纯函数单测；结论回写 `docs/ui-design.md` §6.5 |
| **UI17 AppMotion 消费面清理** | #167 | 纯清理性重构（局部仍有硬编码时长），优先级低于功能 |
| **UI14 代码浏览悬浮搜索** | #166 | `FileViewerScreen` 顶栏已有 `startSearch()` 与跳行入口，与审计描述的「悬浮按钮」形态不同 —— 需先确认形态 |
| **PR 时间线表情回应** | #166 | Issue 侧有、PR 侧无；`ReactionChip` 目前是 feature:issue 私有，需先抽到共享模块 |
| **PR 评论 Sheet 与 Issue 统一** | #166 | 两边各有一份 Sheet 实现，宜连带上面那条一起抽共享组件，一次消除重复 |
| **Q01 Compose lint 15 项检查失效** | #170 | 根因是 compose-ui lint jar 需要更新的 Kotlin Analysis API；**实测 AGP 8.13.2 + Gradle 8.14.3 无法修复**，需 AGP 9.x 迁移。已加「lint 覆盖面守卫」防止继续退化 |

### 3.2 T25 剩余（均为"本机无法验证"类）

| 项 | 状态 |
|---|---|
| Baseline Profile + ProfileInstaller | ✅ APK 内 `assets/dexopt/baseline.prof`(7886B) + `.profm`(1107B) 实测存在 |
| zh 文案全覆盖 | ✅ 已是硬断言测试（`I18nParityTest`），全模块通过 |
| 签名发布链路 | 🔶 用临时 keystore 在本地与 `release-dry-run.yml` 演练跑通；**生产密钥需你在 Secrets 配置** `KEYSTORE_BASE64`/`KEYSTORE_PASSWORD`/`KEY_ALIAS`/`KEY_PASSWORD` |
| WebView 离开页面 destroy | ✅ `DisposableEffect` 全链销毁 |
| 冷启动 < 1.5s 实测 | ⏳ 需真机/模拟器（本机 Linux 纯 JVM） |
| Baseline Profile macrobenchmark 采集 | ⏳ 同上；当前是人工推导的启动热路径，接入路径已写在 `baseline-prof.txt` 文件头 |
| 全屏 RTL 截图矩阵 | 🔶 方向性已验证（顶栏 RTL 基线）；全屏矩阵需模拟器截图 job |

### 3.3 修复波后仍剩余（2026-09-12 核验）

> 完整可执行清单（含 file:line 证据与规模）见 `docs/agents/remaining-backlog-2026-09-12.md`；下列为该清单的收敛摘要。

| 项 | 状态 / 说明 |
|---|---|
| T25 真机项 | ⏳ 冷启动 <1.5s 实测、Baseline Profile macrobenchmark 采集；本机纯 JVM 无法验（见 §3.2） |
| 生产签名密钥 | 🔶 需用户在 Secrets 配 `KEYSTORE_BASE64` / `KEYSTORE_PASSWORD` / `KEY_ALIAS` / `KEY_PASSWORD` |
| AGP 9.x 迁移 | ⏳ 未做；是 Q01 Compose lint 失效的根因解，可行性实测路线见 `docs/agents/agp9-feasibility-2026-09-11.md` |
| 设计系统 SPEC-1 / SPEC-2 | ⏳ `App*` 组件 10 个仅 1 个落地；间距未令牌化（裸 `.dp` 约 639 处） |
| MD-1 剩余 | ⏳ `@user` 已修（#251），Issue 引用 `#123`、裸 sha 仍未渲染 |
| EDITOR-1 | ⏳ 软换行不可切、replace 缺失、CRLF/编码未显式处理 |
| DATA-1 ETag 持久化 | ⏳ `RestNetworkModule` 仍 `InMemoryEtagStore`，重启即失效 |
| UI-1 窄屏 side-by-side diff | ⏳ 每栏 ~40 字符且无横向滚动（需 §6 决策） |
| 覆盖率剩余模块 | 🔶 2026-09-13 全量棘轮（`chore/coverage-ratchet-true-values`）：22 个有阈值模块按 #261 修复后的**真实**覆盖率重设（旧阈值普遍低于实测数十 pp，门禁曾形同虚设）；仍豁免 = `core:data` / `core:testing` / `core:ui` / `prototype`（理由见 `build.gradle.kts` 注释） |

> 另有 8 项需产品/设计先 grill 的决策（窄屏 diff、feed 骨架、KaTeX/Mermaid、`FeatureDetector` 去留等），见 `remaining-backlog-2026-09-12.md` §6。


## 4. 遗留事项（未闭环）

| 项 | 状态 |
|---|---|
| RepoDetail 截图基线（light/dark） | ✅ 已补录；21 张陈旧基线一并重录（PR #181），并以 CI 为权威录制环境（PR #188） |
| WebView 私有图代理 + 缓存 <300ms | 待真机验证 |
| 真机走查机制（每票合并前的截图/真机验收） | 仍未流程化；截图工具调研见 `docs/research-questions.md` §7 |
| 图标候选清单（底部导航实心/空心） | ✅ 闭环（PR #185：三档图标风格 + AppIcon 统一入口 + 底栏仓库图标语义） |
| PiliPlus 卡片风格细节 | ✅ 闭环（PR #182：「仓库」大分区 + 网格/通栏切换） |
| Material Symbols 变量字体（wght=300） | ✅ 评估完成（PR #185）：暂缓引入，含再评估触发条件 |
| **模块级截图基线不在任何门禁里** | ✅ 闭环（PR #181 引入 CI 权威录制 workflow，#188 应用基线并开启 app + 6 模块 verify）。**注意：模块级基线的权威录制环境是 CI** —— 本机渲染与之不同，不要本机 record 后提交 |
| Compose lint 15 项检查失效 | 🔶 见 §3.1（需 AGP 9.x 迁移） |
| diffCoverage 软门禁从未实测 | ✅ 已修两个真缺陷并转**硬门禁**（PR #181）：① 阈值口径把 80 当 8000%；② 非可执行行计入分母造成假失败 |
| Nightly 全量截图/多设备/性能基线 | ✅ 已落地（PR #181 的 nightly.yml：verify / 全模块 record+diff / release 体积 / 失败汇总） |
| 应用图标缺失 / Bundle 语言拆分 | ✅ 已闭环（PR #174：自适应+主题化单色层图标、Bundle 语言拆分） |
| **截图基线覆盖（2026-09-12 修复波）** | 🔶 大幅补齐：模块级基线纳入 CI verify（app + 6 模块，PR #188）；#245 补 OLED/高对比/zh + RTL；#249 补 `:feature:pullrequest`；#252 修复探针。#256 仍在扩（9 屏/20 帧）；仍有多屏缺基线（TEST-2） |
| **RepoDetail 仓库头整块不渲染（UI-C01）** | 🔴 真实缺陷，修复中：`fix/repodetail-header-render`（尚无提交）；验收 = `repo-star` / `repo-actions` 帧转绿 |

## 5. 更新规则

- 每票合并 → 移入「已完成」表 + 更新里程碑
- 进行中任务开始/结束 → 更新第 3 节
- 遗留项闭环 → 从第 4 节移除
- 本文档由主代理维护（不派子代理更新）

## 6. 本波新增的工程约定（2026-09-11，来自 #163–#170 的实战教训）

这些不是流程口号，是**踩过并修掉**的问题，后续开发请直接遵守：

| 约定 | 来源 |
|---|---|
| **模块级截图基线只能由 CI 录制**（`Record screenshots (CI canonical)` workflow）。本机与 runner 的 Robolectric 渲染不是逐字节相同，本机录的基线在 CI 上 verify 会全红 | #181 实测 8 张全红 |
| ~~**被 Robolectric 沙箱加载的类不产出 JaCoCo 覆盖数据**（测试全绿但覆盖率 0）~~ **已证伪并修复（2026-09-13 #261）**：真实根因是 JaCoCo agent 默认 `inclnolocationclasses=false`，Robolectric `SandboxClassLoader` 定义的应用类无 CodeSource → 整类被静默跳过，**与「逻辑是否纯 JVM 可测」无关**。修复 = `includeNoLocationClasses=true` + `includes=com/yumiru11/*`（`build.gradle.kts` 的 `configureRobolectricCoverage`）。修复后 Robolectric 单测覆盖率真实入 exec（`:app` 2.61%→25.80%、`:core:designsystem` 71.2%→96.56%）；「希望进覆盖率门禁的逻辑必须纯 JVM 可测」的旧约束**不再成立** | #181 → #261 |
| **不要用协程 withTimeout 包真实网络调用**：runTest 用虚拟时钟，超时会被「立刻」触发，测试拿到 null | #191 |
| **MockWebServer 的顺序队列不可依赖**（并发请求会错位）；改用按路径 + page 路由的 Dispatcher | #191 |
| **辅助查询（Star 总数 / viewer 登录名）失败不得拖垮整页**：包 runCatching，取不到就少显示一项 | #191 / #193 |
| **新增 Composable 文件要落进 diff 门禁的 UI 排除名单**（按文件名后缀），否则新文件会被判「新增代码 0% 覆盖」 | #189 |
| **ModalBottomSheet 的 content 里不要放无限动画**：Robolectric 的 waitForIdle 永不收敛，verify 会挂死。**#245 起含无限动画的屏改用 `captureScreenshotDeterministic` 兜底**（`:feature:issue` 挂死已解） | #181 → #245 |

### 6.1 2026-09-12 修复波补充（截图链路可信化 + 回归守卫）

| 约定 | 来源 |
|---|---|
| **含无限动画的屏必须用 `captureScreenshotDeterministic`** —— `captureRoboImage(content)` 会 `ShadowLooper.idle()`；无限动画使主 looper 队列永不空 → verify/record 挂死（`:feature:issue` 7min+ 根因）。改用冻结时钟 + 固定 `advanceTimeBy` + 手工 draw + `Bitmap.captureRoboImage` | #245 |
| **截图基线只能由 CI canonical 录制**（`Record screenshots (CI canonical)` / `record-screenshots.yml`）；本机 `recordRoborazziDebug` 禁止（渲染非逐字节相同，本机录的 CI verify 全红）。⏳ 「录制前先 rebase 到最新 main」待核实 | #181 / #245 |
| **截图探针语义**：`TabRow` 选中态 = `selected`；M3 `SegmentedButton` = `checkable/checked`（radio 语义）；`ExtendedFAB` 文案不进 uiautomator dump，须 `try_tap_fab`（判据右下角 x≥0.7w 且 y≥0.85h，放宽会误点 diff 行）；M3 `OutlinedTextField` placeholder 不当就绪信号（#250）；探针不得静默降级为 `opt:` | #250 / #252 |
| **时间炸弹**：截图/测试夹具禁止写死绝对时间戳，一律相对时间（`isoDaysAgo`） | #246 |
| **守卫先做红证明**：恒真守卫会以「在跑但什么都没查」上线——修复波抓到 i18n lint canary 自引用、`repo-actions` 探针空洞、RTL `@Config(qualifiers)` 假测试三例 | #235 / #245 / #252 |

---

## 工作流 grill 结论（2026-08-16）

- 已拍板：验收卡 + 分级验收、PR 测试清单、意图直读文档/ASCII 图、里程碑走查、设计闸门、基线改动需批准、全局性问题专项票
- 完整规则见 `docs/agents/workflow.md` §4；问题与用户回答原文见 `docs/workflow-grill.md`

## 测试覆盖率推进（2026-08-16 规划）

- 策略：`docs/agents/testing-strategy.md`（JaCoCo 0.8.13+ / 分层目标 / diff coverage 门禁）
- 分点清单：`docs/agents/testing-checklist.md`（A 纯逻辑 / B 数据层 / C 网络 / D 认证 / E ViewModel / F UI / G 可注入性 / H 断言质量）
- 当前：JaCoCo + `coverageVerify` 硬门禁已上线；diff coverage 仍为软门禁（D04 实测归 #170）
