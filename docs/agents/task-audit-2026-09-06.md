# AppDev 项目审计报告（2026-09-06 终版）

> 一次成稿，覆盖：票面完成核验（T1–T26 + ui-audit + 计划外交付）、逻辑类问题 16 项、UI 类问题 27 项、
> 门禁/发布/文档类 12 项、已实现确认 13 项、需求（request.txt）符合度矩阵、测试与截图基线现状。
> 全部问题已按标准化标题合并立项为 **9 张分类 issue（#163–#171）**；原 51 张明细票已全部关闭并入（编号见 §5 映射与归档表）。
> 审计基线：`main@2b7071d`（2026-09-05）；方法：文档全文 × 代码 grep 静态对照 + gh 票面实测 + lint 报告；
> 按用户指示**不做视觉审查**，体感类条目一律标注「待真机/模拟器复核」。

---

## 1. 审计范围与方法

### 1.1 数据源（全部通读）

| 类别 | 文档 | 用途 |
|---|---|---|
| 需求 | `request.txt` | 需求符合度矩阵（§9） |
| 规划 | `plan.md`（982 行） | 功能条款、缓存策略、性能目标、技术栈对账 |
| UI 权威 | `docs/ui-design.md`（455 行） | 导航/页面矩阵/动效/图标/玻璃/主题/无障碍契约 |
| UI 审查 | `docs/ui-audit-2026-08-21.md`（152 行） | 缺陷清单 #1-20 逐条复核 + 挂账表 §3.2 状态 |
| 反馈 | `FEEDBACK.md`（89 行） | 真机反馈 #1-31 逐条对账 |
| 决策 | `docs/adr/0001~0007` | 认证/存储/主题/渲染/导航/安全决策对账 |
| 领域 | `CONTEXT.md`（66 行） | 术语契约（玻璃清单/卡片风格/Octicons/趋势源） |
| 流程 | `docs/agents/workflow.md`、`workflow-grill.md`、`dsh-guide.md`、`domain.md` | 流程义务（验收卡/截图基线规则） |
| 测试 | `docs/agents/testing-checklist.md`、`testing-strategy.md`、`unit-test-coverage.md` | Phase C 测试残差、门禁契约 |
| 调研 | `docs/research/*`（7 份）、`docs/research-questions.md` | 结论已落 ADR；无独立欠账 |
| 实证 | `gh issue/pr list`、`git log/branch/status`、`app/build/reports/lint-results-debug.txt`、全仓 grep | 票面与代码证据 |

### 1.2 口径说明

- 问题判定 = 文档条款存在 + 代码 grep 零命中/占位/隐藏入口/文档与实现不符。
- 状态图例：**❌ 未实现** / 🔶 部分 / 📌 依赖用户决策（已建票并注明）/ ✅ 已实现（纠偏）。
- 代码风格与依赖版本类问题（lint 82 条警告中的 GradleDependency、格式等）按用户指示不列入；
  但 **lint 门禁失效（Q01/Q02）、无应用图标（Q03）、Bundle 语言拆分（Q04）** 因直接影响功能/观感/上架而列入。

---

## 2. 总览结论

| 结论 | 内容 |
|---|---|
| 票面完成度 | **T1–T26 中 24 票已合入 main 并关闭**；唯一未开工功能票 **T25（#26）**；#71 测试面板与 #1 Spec 按约定保持 OPEN |
| 计划外交付 | Task B（WebView 主渲染，PR #70/#73）、截图 CI（#60）、真机修复波（#58/#59/#63/#67/#69）、ui-audit 8 票（#83–#90）全部完成 |
| 「已完成 ≠ 设计闭环」 | ui-design/ADR-0006 已拍板契约仍有 **27 项 UI 零实现**（§6）；plan.md 功能条款 16 项未排票（§4） |
| 文档失真 | AGENTS.md / project-status.md / ui-audit 挂账表三者互不一致；T20「Gists 入口」无代码（§7 D01） |
| 门禁水份 | Compose lint 15 项检查因依赖 API 失效被跳过；模块级截图基线不在任何门禁；diffCoverage 仍是软门禁（§7） |
| 体感类 | 真机走查机制未流程化；FEEDBACK #17 复测结果未回写；md 标题字号校准、README 首帧性能等待实机（§11） |

---

## 3. 票面完成状态核验（gh 实测）

| 票 | Issue | 合入 PR | 日期 | 代码落点（节选） |
|---|---|---|---|---|
| T1 CI/CD | #2 | #28 | 08-10 | ci.yml（spotless/detekt/lint/konsist/单测/Roborazzi/assemble/coverageVerify/diffCoverage 软门禁/模拟器截图）+ release.yml |
| T2 架构守卫 | #3 | #43 | 08-10 | Konsist ArchitectureTest、core:testing、32 张入库截图基线 |
| T3 导航骨架 | #4 | #44 | 08-11 | GitHubLinkParser、AppRoute（@Serializable）、MainActivity 深链 |
| T4 认证 | #5 | #46 | 08-12 | PKCE（AppAuth）、EncryptedTokenStorage、isRestOnly 降级、游客模式 |
| T5 网络层 | #6 | #45 | 08-12 | Retrofit3/Apollo5、EtagCacheInterceptor、Room/DataStore |
| T6 主题引擎 | #7 | #48 | 08-12 | 6 套主题、ExtendedColors、AppDimens/AppBlur 8dp、GlassRenderPolicy |
| T7 Markdown 原生 | #8 | #47 | 08-12 | mikepenz + KotlinTextMate + Enhanced*（短文本继续服务） |
| T8 WebView 兜底 | #9 | #51 | 08-13 | WebViewMarkdownRenderer/WebViewHtmlBuilder/renderer.js/DOMPurify |
| T9 README 浏览 | #10 | #51 | 08-13 | ReadmeApi.getReadmeHtml + 离线 GFM 两级 + CachedReadmeDao |
| T10 首页 feed | #11 | #56 | 08-13 | FeedPagingSource（6 类事件）+ PullToRefreshBox |
| T11 文件树/浏览 | #12 | #61 | 08-16 | FileTreeBuilder + Sora read-only |
| T12 仓库管理 | #13 | #74 | 08-20 | Star/Watch/Fork + Releases/Tags + 语言栏 + deleteBranch |
| T13 Issue 列表详情 | #14 | #57 | 08-13 | 时间线 + 跨引用 null id 修复（#73） |
| T14 Issue 写 | #15 | #75 | 08-20 | 创建/编辑/评论 CRUD/反应/关闭重开/checkbox 写回（bridge） |
| T15 PR 列表详情 | #16 | #76 | 08-20 | 四 Tab（Conversation/Commits/Checks/Files changed） |
| T16 Diff+行评论 | #17 | #101 | 08-23 | DiffParser + PullRequestDiffView（unified/side-by-side）+ LineCommentSheet |
| T17 Review/Merge | #18 | #102 | 08-25 | ReviewSheet + MergeBox（SplitButton）+ merge/update-branch/delete-ref |
| T18 搜索 | #19 | #62 | 08-16 | 四 Tab + qualifier + 历史（Room）+ 代码搜索登录门禁 + 防抖 300ms |
| T19 通知 | #20 | #52/#98 | 08-13/23 | 面板（#88）：分组折叠/时间排序/滑动/筛选/全部已读/遮罩 0.5 |
| T20 Profile | #21 | #53 | 08-13 | Repositories/Starred/Followers/Following 四路 Paging（**Gists 缺，见 L11**） |
| T21 Markdown 编辑器 | #22 | #77 | 08-20 | Sora 编辑 + 工具栏 + @mention/emoji 补全 + 编辑/预览双 Tab |
| T22 文件编辑提交 | #23 | #96 | 08-22 | Contents PUT/DELETE + 409 三选一 + 当前/新建分支 |
| T23 分支/PR 创建 | #24 | #105 | 08-26 | BranchesScreen + GitRefApi + PullRequestCreateScreen |
| T24 设置 | #25 | #54/#99 | 08-13/23 | 主题/seed/圆角/动效滑杆（已接线 AppThemeHost）/语言/毛玻璃总开关/PAT/配额 |
| **T25 性能发布** | **#26** | — | — | ⏳ 未开工（Baseline Profiles 文件不存在；无 v* tag） |
| T26 M3 高亮 | #27 | #55 | 08-13 | M3TextMateTheme/GitHubTextMateTheme |
| ui-audit | #83–#90 | #92~#110 | 08-21~09-05 | 毛玻璃 backdrop（#83）、组件族/动效基建（#84）、标签混色/语义色（#85）、itemKey（#86）、设置分组（#87）、通知面板（#88）、首页 Pager/LongBar（#89）、导航现代化（#90） |

---

## 4. 逻辑类问题（16 项，标准标题 `type(scope): 描述`；label `ready-for-agent`）

### L01 Subscribe/取消订阅 Issue —— issue #163
- **条款**：plan.md §6.1 Issue 操作清单（编辑 / Subscribe / Close / Reopen）
- **现状**：feature/issue 0 处 subscribe；REST 无 `PUT/DELETE …/issues/{n}/subscription`（仓库级 watch 已有，Issue 级缺失）
- **验收建议**：订阅态切换 + 401/403 归一 + ViewModel 单测（成功/失败回滚）

### L02 Label/Assignee/Milestone 编辑界面 —— issue #163
- **条款**：plan.md §7.2；ui-design §3.9 IssueHeaderCard
- **现状**：`IssueApi.updateIssue` PATCH 已支持三字段（自定义序列化防 null 清空），但 UI 仅标题/正文编辑、创建仅手输标签名（CreateIssueScreen L57），无 assignee/milestone 选择器
- **验收建议**：编辑 Sheet 内 LabelChipGroup 多选 / AssigneeRow / MilestoneCard，viewerPermission 显隐

### L03 PR 编辑/关闭/重开 —— issue #163
- **条款**：plan.md §7.3
- **现状**：PullRequestApi 无 `PATCH /pulls/{n}`；详情页只有 Review/Merge/Update branch/Delete branch
- **验收建议**：操作菜单补编辑/关闭/重开；关闭后徽章 closed、可重开

### L04 创建/删除仓库 + Home「新建仓库」按钮 —— issue #164
- **条款**：plan.md §7.6；ui-design §2.2（长条按钮 ×3）
- **现状**：全仓 0 端点；HomeScreen L82 注释「新建仓库占位禁用」
- **验收建议**：POST /user/repos + DELETE（二次确认）；按钮接线

### L05 发布 Release / 上传 asset —— issue #164
- **条款**：plan.md §7.6
- **现状**：仅 GET releases 列表/详情 + GET tags；`ReleaseDto` 无 assets 字段
- **验收建议**：发布草稿 Release + 附件上传；权限显隐

### L06 Topics 展示 —— issue #164
- **条款**：plan.md §7.6
- **现状**：REST/feature/repo 0 命中；GraphQL 仅 schema 自动生成类型无查询
- **验收建议**：仓库详情 topics 行（可点跳搜索）

### L07 列表级 RemoteMediator + Room 分页缓存 —— issue #165
- **条款**：plan.md §4.6（Issue/PR Paging+RemoteMediator；Timeline/PR 文件/文件内容分级缓存）
- **现状**：全仓 0 处 RemoteMediator；Room 仅 README/仓库元数据/搜索历史；ETag 只覆盖 REST GET
- **验收建议**：断网可看已访问列表；刷新合并

### L08 Trending 穿插 feed —— issue #165
- **条款**：ui-design §2.5 / ADR-0006 §7（数据源已拍板：Unpublished/GithubTrending JSON 镜像 + `created:>7d sort:stars` fallback）
- **现状**：feature/home 0 命中；从未排票
- **验收建议**：feed 底部 trending 区块；镜像超时自动回退；可点击进详情

### L09 COMMIT 详情页 —— issue #164
- **条款**：ui-audit §3.2（COMMIT/BLOB 占位；BLOB 已由 #90 修复）
- **现状**：AppNavHost 注释「T5+ Commit 详情页（真实屏未开发）」，PlaceholderSearchScreen 占位
- **验收建议**：提交信息/文件变更（+N −M）→ Diff

### L10 他人主页只读 + 关注按钮 —— issue #165
- **条款**：ui-design §3.5/§2.3
- **现状**：AppRoute.User.login 已留、AppNavHost 无对应屏幕；REST 无 `PUT/DELETE /user/following/{u}`
- **验收建议**：从评论头像跳他人主页；关注数字 +1

### L11 Gists 列表页 + 文档措辞回填 —— issue #165
- **条款**：ui-design §3.7；project-status T20 行「Stars/Gists 入口」
- **现状**：feature/profile 0 gist 引用（**文档夸大**）；ProfileScreen 仅四类分页
- **验收建议**：Profile 增加 Gists 入口与列表；Bookmarks 可空态占位

### L12 通知组内时间排序切换控件 —— issue #165
- **条款**：ui-design §3.4（A3-2 拍板：按仓库分组 + 组内时间排序）
- **现状**：默认时间倒序已实现（NotificationGroup.sortedByDescending ✅）；无排序切换控件/无 AnimatedItem 重排
- **验收建议**：若固定倒序即关闭本票，否则补切换

### L13 搜索结果缓存与限流提示 —— issue #165
- **条款**：plan.md §9.3（限流处理+结果缓存）
- **现状**：历史已做（Room ✅）；搜索结果无缓存层；429/403 有归一但无配额提示（设置页 RateLimitRow 已有 ✅）
- **验收建议**：重复 query 命中缓存；429 有可读提示

### L14 调试三件套（Timber/Chucker/LeakCanary）—— issue #169
- **条款**：plan.md §3.1 技术栈表
- **现状**：toml/构建脚本 0 命中
- **验收建议**：debugImplementation 引入；Timber 日志脱敏（token 不落日志）

### L15 release R8/minify 并实跑 —— issue #169
- **条款**：plan.md §14.1；T25（#26）含签名 Release
- **现状**：app/build.gradle.kts 0 命中 minify/shrink；仓库无 v* tag（签名发布从未实跑）
- **验收建议**：minify + keep 规则（serializer/Coil/Sora/Apollo）+ 首次 tag 实跑 + mapping 归档

### L16 PrivateImageInterceptor host 白名单复核 —— issue #169
- **条款**：plan.md §15.4
- **现状**：拦截器已实现（token 仅进图片请求 ✅ 架构正确）；白名单内容未逐行复核
- **验收建议**：host 集合复核（raw.githubusercontent/avatars/api.github.com media）+ 单测

---

## 5. Issue 立项与归档（9 张分类票 #163–#171；原 51 张明细票已关闭并入）

### 5.1 分类票映射（当前唯一活动票）

| 类别 | 审计项 | Issue | 标题 |
|---|---|---|---|
| 逻辑·Issue/PR 写操作 | L01–L03 | #163 | feat(issue,pr): Issue/PR 写操作补全（Subscribe/Labels·Assignee·Milestone 编辑/PR 编辑关闭重开） |
| 逻辑·仓库域 | L04/L05/L06/L09 | #164 | feat(repo): 仓库域功能补全（建删仓库/发布 Release+asset/Topics/COMMIT 详情页） |
| 逻辑·内容与数据域 | L07/L08/L10–L13 | #165 | feat(data,home,profile): 内容与数据域补全（RemoteMediator/Trending/他人主页/Gists/搜索缓存/通知排序） |
| UI·一期布局与页面 | UI01/02/05/11/14/19/20/21 | #166 | feat(ui): UI 打磨一期·布局与页面（网格切换/长按菜单/评论 Sheet/图片查看器/更多菜单/文件树动画/悬浮搜索/Starred 统计） |
| UI·二期动效体系 | UI03/04/06–10/15–18/22 | #167 | feat(ui): UI 打磨二期·动效体系（Crossfade/stagger/Star·Reaction·Profile 动效/重排/玻璃逐项+BottomSheet/背景图/README 收起） |
| UI·三期图标与无障碍 | UI12/13/23–27 | #168 | feat(ui): UI 打磨三期·图标与无障碍（AppIcon/变量字体/底栏图标/触区·对比度·播报·Badge） |
| 工程收尾 | L14–L16/Q03/Q04 | #169 | chore(app): 工程收尾（调试三件套/R8+签名实跑/白名单复核/应用图标/Bundle 语言拆分） |
| 质量门禁 | Q01/Q02/D02–D04 | #170 | fix(ci): 质量门禁修复（Compose lint 失效/禁用清理/diffCoverage 实测/模块级截图基线/Nightly） |
| 文档同步 | D01 | #171 | docs(agents): 回写 AGENTS.md/project-status.md/FEEDBACK.md 真实状态 |

### 5.2 原 51 张明细票归档对照（已关闭，close 评论注明并入票）

| 审计编号 | 原票 | 并入 | 审计编号 | 原票 | 并入 | 审计编号 | 原票 | 并入 |
|---|---|---|---|---|---|---|---|---|
| L01 | #112 | #163 | L02 | #113 | #163 | L03 | #114 | #163 |
| L04 | #115 | #164 | L05 | #116 | #164 | L06 | #117 | #164 |
| L09 | #120 | #164 | L07 | #118 | #165 | L08 | #119 | #165 |
| L10 | #121 | #165 | L11 | #122 | #165 | L12 | #123 | #165 |
| L13 | #124 | #165 | L14 | #125 | #169 | L15 | #126 | #169 |
| L16 | #127 | #169 | UI01 | #128 | #166 | UI02 | #129 | #166 |
| UI03 | #130 | #167 | UI04 | #131 | #167 | UI05 | #132 | #166 |
| UI06 | #133 | #167 | UI07 | #134 | #167 | UI08 | #135 | #167 |
| UI09 | #136 | #167 | UI10 | #144 | #167 | UI11 | #137 | #166 |
| UI12 | #138 | #168 | UI13 | #139 | #168 | UI14 | #140 | #166 |
| UI15 | #141 | #167 | UI16 | #142 | #167 | UI17 | #143 | #167 |
| UI18 | #145 | #167 | UI19 | #146 | #166 | UI20 | #147 | #166 |
| UI21 | #148 | #166 | UI22 | #149 | #167 | UI23 | #150 | #168 |
| UI24 | #151 | #168 | UI25 | #152 | #168 | UI26 | #153 | #168 |
| UI27 | #154 | #168 | Q01 | #155 | #170 | Q02 | #156 | #170 |
| Q03 | #157 | #169 | Q04 | #158 | #169 | D01 | #159 | #171 |
| D02 | #160 | #170 | D03 | #161 | #170 | D04 | #162 | #170 |

> 说明：合并仅发生在 issue 组织层面，每项审计发现仍保留独立编号与证据（见 §4/§6/§7 明细与上表）。
## 6. UI 类问题（27 项，label `ui-audit`；编号 UI01–UI27，标题见 §5）

### 6.1 布局与信息架构类
- **UI01 网格/通栏布局切换**（ui-design §3.2 + CONTEXT 卡片风格）：ReposScreen 仅通栏列表，无 LazyVerticalGrid/切换按钮（用户拍板 B2-2）→ #166
- **UI02 仓库长按弹出菜单**（§3.2「长按弹小窗」，非左滑）：core:ui 0 命中 longClick → #166
- **UI03 玻璃逐项开关 ×4**（§6.3：顶栏/底栏/通知面板/BottomSheet 各自可关；OLED/高对比禁用）：仅 settings_blur 总开关 → #167
- **UI04 全局背景图**（§7.4：选图/不透明度/浅白深黑/深色压暗/固定不动/Compose 层）：0 实现，grill 细节待拍板 → #167（标注依赖用户决策）
- **UI22 BottomSheet 玻璃点位**（§6.1 #4 默认开）：GlassSurface 仅 Home/Notifications 消费，issue/pullrequest/editor 弹层无 → #167
- **UI23 底栏「仓库」Tab 图标语义**（ui-audit #12）：AppBottomBar 用 Star 图标与收藏语义冲突，等图标候选批准换 Octicons repo → #168（标注依赖用户决策）

### 6.2 页面级组件缺失
- **UI05 评论输入 Sheet 完整形态**（§3.9 D2-3：右下角圆角按钮 + 上滑 Sheet 圆角/把手/编辑预览/md 工具栏）：现状为 T14 简化输入框 → #166
- **UI19 仓库详情顶栏「更多」菜单**（§3.8 分享/浏览器/复制链接）：RepoDetailScreen 无 DropdownMenu/MoreVert（Issue/PR 同款已有）→ #166
- **UI11 图片全屏查看器**（§3.11 9g：纯黑背景 + fade in + 圆角阴影）：WebView bridge onImageClick 已留口，主应用无消费者 → #166
- **UI21 Profile 统计行缺 Starred 数字**（§3.5 统计行四件套）：仅 repos/followers/following → #166
- **UI12 AppIcon 组件与 iconStyle 消费**（§5/ADR-0004；FEEDBACK #6 先例）：0 组件；设置图标风格/代码字体/行号入口已隐藏 → #168
- **UI14 代码浏览悬浮搜索按钮**（§3.10）：跳行已有（FileViewerScreen L224），findText 无 → #166

### 6.3 动效契约类（§4.1–4.4）
- **UI15 主题切换全屏 Crossfade**（F3-1 用户拍板）：app 0 命中 Crossfade，主题硬切 → #167
- **UI16 搜索/首页分区切换动效**（§3.3 结果区 Crossfade；§4.3 Fade through）：防抖 300ms ✅ 但两处 0 命中动画 → #167
- **UI17 AppMotion 消费面过窄**（§4.1 时长/Easing 表 + §4.4 token 化；audit #5）：仅 NotificationsPanel 等个别消费 → #167
- **UI07 Star 微缩放 + 填充动画**（H2-7 用户确认；ui-audit #13 KDoc 与实现不符）：RepoDetailScreen 无动画 → #167
- **UI08 Reaction 点击微缩放**（§3.9）：IssueDetailScreen 0 命中 → #167
- **UI09 Profile 头像回弹 + 统计数字 AnimatedContent**（§3.5）：0 命中 → #167
- **UI06 全局 stagger 开关**（H2-2 设置可开关）：仅首页首屏 Stagger.kt（#89），无全局组件/开关 → #167
- **UI18 通知已读重排动画**（§4.3 元素位置变换 + §3.4 AnimatedItem）：仅分组折叠 AnimatedVisibility → #167
- **UI20 文件树展开/收起动画**（§3.8 AnimatedVisibility）：FileTreeSection 0 命中 → #166
- **UI10 README 头部收起动画**（§3.8，用户构思中两版对比）：未实现，等拍板 → #167（标注依赖用户决策）

### 6.4 无障碍与细节类（ui-audit §2.3 剩余）
- **UI24 ReactionBar 触区 27dp < 48dp**（#15）→ #168
- **UI25 LabelChip 对比度 WCAG AA 未验证**（#20；#85 已修混色，on 色算法无断言）→ #168
- **UI26 StatusChip 开/关状态播报**（#18 剩余；ReactionBar/通知未读已补 stateDescription）→ #168
- **UI27 铃铛 Badge 99+ 上限**（#14 剩余；未读 CD 已并入 plurals ✅）→ #168

### 6.5 图标体系（ADR-0004/§5）
- **UI13 变量字体四轴评估票**（wght300/ROND 30-50/FILL/GRAD；Kotlin 2.3.21 兼容/体积/混用/性能）：从未执行 → #168
- 已实现 ✅：Material Symbols 静态 outlined/filled（底部导航实心/空心）、Octicons 15 枚入库并接线（Profile/Home/RepoPicker/Notifications）

---

## 7. 门禁、发布与文档类（Q01–Q04 + D01–D04）

| 编号 | 问题 | 证据 | Issue |
|---|---|---|---|
| Q01 | **Compose lint 失效**：UiIssueRegistry 引用无效 API，15 项检查（SuspiciousModifierThen/UnnecessaryComposedModifier/RememberInComposition…）不运行 | lint-results-debug.txt（ObsoleteLintCustomCheck） | #170 |
| Q02 | **lint 禁用 20 条 Compose 规则**，其中 2 条为已不存在的 UnknownIssueId | app/build.gradle.kts L34-48 | #170 |
| Q03 | **无应用图标**（启动器缺图标，直接损伤观感与发布） | lint MissingApplicationIcon + Manifest 无 android:icon | #169 |
| Q04 | **运行时语言切换 × Bundle 语言拆分冲突**（上架后 in-app 切换可能失效） | lint AppBundleLocaleChanges（MainActivity L368） | #169 |
| D01 | **文档三处失真**：AGENTS 仍写 15 票/「Task B 工作树未提交」/剩 11 票；project-status 仍列 T16/T17/T23 待做、ui-audit 4 票未合、截图调研待办；T20「Gists 入口」无代码；FEEDBACK #17 复测未回写 | AGENTS.md / project-status.md / FEEDBACK.md 原文 | #171 |
| D02 | **模块级 Roborazzi 基线不在门禁**：core:ui 7 张、core:designsystem 2 张漂移（清水 main 上 actual 与基线 IDENTICAL 仍 verify 失败） | project-status §5 | #170 |
| D03 | **无 Nightly** 全量截图/多设备/性能基线（testing-strategy §6 后续项） | workflows 仅 ci+release | #170 |
| D04 | **diffCoverageCheck 软门禁从未被验证**（continue-on-error + dry-run 探测；任务存在但无真实 PR 实测记录） | ci.yml + build.gradle.kts | #170 |

---

## 8. 已实现确认（13 项，防误报纠偏）

| # | 项 | 证据 |
|---|---|---|
| OK01 | 代码块复制按钮（FEEDBACK #22） | EnhancedCodeBlock + CopyFeedbackState + code_copy 文案 |
| OK02 | 私有仓库图片认证（ADR-0005 留白） | PrivateImageInterceptor（token 只进图片请求） |
| OK03 | 圆角/动效滑杆接线（ui-audit #3） | AppThemeHost L40-42：cornerScale→shapes、motionScale×系统缩放取最小 |
| OK04 | NavHost 全局转场/预测返回/共享元素试点（#90） | AppNavHost + LocalMotionScale + manifest enableOnBackInvokedCallback |
| OK05 | 首页 stagger（#89） | feature/home/ui/Stagger.kt |
| OK06 | 搜索防抖 300ms / 历史清空 | SearchViewModel L91/L257（DEBOUNCE_MS=300L） |
| OK07 | 通知面板全套（#88） | PANEL_SCRIM_ALPHA=0.5 + SwipeToDismissBox + 分组折叠 + 全部已读 |
| OK08 | 铃铛未读语义播报（#14 半） | AppTopBar plurals notification_unread_badge_cd |
| OK09 | SectionHeader heading()（#19） | SettingsScreen L129 |
| OK10 | WebView 任务列表写回 | MarkdownBridge.onCheckboxClick + FlipTaskListItemTest |
| OK11 | 配额展示（ui-design §3.6） | DeveloperSettingsSection.RateLimitRow |
| OK12 | 搜索自动聚焦（§3.3） | SearchTopBar FocusRequester + LaunchedEffect |
| OK13 | Octicons 接线（原「入库未接线」） | AppDevOcticons 被 Profile/Home/RepoPicker/Notifications 消费 |

---

## 9. 需求（request.txt）符合度矩阵

| 需求原文要点 | plan 章节 | 状态 | 备注 |
|---|---|---|---|
| Kotlin + Jetpack Compose + 全 Material You | §3.1/§5 | ✅ | 6 套主题 + 动态色 + 令牌 + 玻璃 |
| GraphQL + REST | §3.2/§4 | ✅ | Apollo5 读 + Retrofit3 写 + PAT 降级 isRestOnly |
| Markdown 融入 Material You + 链接跳转 | §2 | ✅ | WebView 主渲染（Task B）+ GitHubLinkParser 深链 |
| README 正确渲染（与网页一致） | §2.6 | ✅ | 服务端 HTML 优先 + 离线 GFM + 截图 CI 实拍 |
| Issue/PR UI 与网页结构一致 | §6 | ✅ | 四 Tab/时间线/ReviewSheet/MergeBox/语义色（#85） |
| 准确语法高亮 | §2.12 | ✅ | KotlinTextMate + M3TextMateTheme + highlight.js |
| md 编辑/代码编辑/仓库管理/审查/评论写功能 | §7/§8 | 🔶 | 缺：Subscribe/Labels-Milestones 编辑/PR 编辑关闭重开/建删仓库/发布 Release/Topics（§4 L01-L06） |
| 预设主题+自定义+动态配色+动效+UI 自定义 | §5 | 🔶 | 主题 ✅；UI 自定义缺：AppIcon 消费/背景图/玻璃逐项开关（§6） |
| Material You 图标库 | §5.6 | ✅ | Material Symbols + Octicons，禁 emoji |
| 完整测试工作流+CI/CD+活用 Actions | §12/§13 | 🔶 | 门禁齐备但 Compose lint 失效（Q01）、模块级截图无门禁（D02）、Nightly 无（D03） |
| 架构清晰+i18n+减少硬编码 | §10/§11 | ✅ | Konsist + en/zh 14 组成对；RTL/ar 矩阵归 T25 |
| Linux 免虚拟机预览测试（不走 KMP） | §12 | ✅ | Robolectric + Roborazzi + 模拟器截图（CI） |

---

## 10. 测试与截图基线现状

- **CI 质量门禁**：spotless / detekt / lintDebug / konsistCheck / testDebugUnitTest / verifyRoborazziDebug(:app) / assembleDebug / coverageReport+coverageVerify（硬，per-module 阈值表）/ diffCoverageCheck（软）。
- **单测分布**（模块 main/test 文件数）：github-rest 45/24；markdown 27/17；issue 13/10；designsystem 19/11；repo 16/8；home 15/7；ui 13/7；profile 9/6；pullrequest 21/6；data 12/6；search 10/5；notifications 9/4；graphql 3/4；auth 15/9；database 8/4；editor 8/4；settings 7/2；auth(feature) 3/2。
- **Phase C 清单（testing-checklist）当前状态**：A 类补全（HtmlSanitizer/WebViewHtmlBuilder/Fusion/RelativeTime/ProfileMappers/GitHubError/CopyFeedback/FeatureDetector 均已补）；B7 四路 PagingSource 已补；E4 ProfileViewModel 已补；剩余 🔶：A12 Mapper 边界、B1/B2 缓存回退、G1 Clock 注入主流业务、G2 Dispatcher 构造注入。
- **截图基线**：仓库内 32 张（app 2 / designsystem 4 / markdown 6 / core:ui 8 / auth 2 / issue 2 / repo 2 / notifications 2 / prototype 4）；**远未达 plan §12.5 矩阵**（缺 OLED/高对比/en-zh-ar/大字/6 主题各屏）；模块级基线漂移（D02）。

---

## 11. 待用户决策挂账（已在对应 issue 正文标注依赖）

| 决策项 | 阻塞条目 |
|---|---|
| 图标候选清单批准（Material Symbols rounded/wght300/FILL + Octicons repo） | UI12/UI13/UI23 |
| 卡片 PiliPlus 两版对比 | UI01（网格/通栏选型） |
| 全局背景图方案 grill（FEEDBACK #30） | UI04 |
| README 头部收起动画两版构思 | UI10 |

---

## 12. 建议执行顺序

1. **P0 文档失真修复（#171）**：回写 AGENTS/project-status/FEEDBACK —— 1 个 PR，先行消除认知偏差。
2. **P0 发布堵点（#169 前两项 + #170）**：应用图标、Bundle 语言拆分、R8/签名发布（与 T25 协调）、lint 门禁恢复。
3. **P1 功能对等（#163 → #164 → #165）**：Issue/PR 写操作补全；仓库域（建删仓库/Release/Topics/COMMIT）；内容数据域（Trending/他人主页/Gists/缓存）。
4. **P1 UI（#166 → #167）**：一期布局与页面（评论 Sheet/图片查看器/网格切换等）；二期动效体系（Crossfade/Star·Reaction·Profile 动效/玻璃完整化）。
5. **P2 三期与收尾**：#168 图标与无障碍（变量字体评估先行）；工程收尾剩余项；#170 门禁全量（模块级基线/Nightly/diffCoverage 实测）。## 13. 附录：验证命令与口径

```bash
# 质量门禁（与 CI 命令级对齐）
./gradlew spotlessCheck detekt konsistCheck :app:lintDebug :app:testDebugUnitTest coverageVerify :app:verifyRoborazziDebug
# 单票快速验证
./gradlew :app:compileDebugKotlin
```
- 本报告问题判定均为**静态证据**；体感类（毛玻璃穿越感、滚动帧率、README 排版）标注「待真机/模拟器复核」。
- 修复闭环规则：issue 关闭时在正文回链本报告编号（Lxx/UIxx/Qxx/Dxx）。

## 14. 勘误区（已 gh 实测纠正）

- 明细票 #112–#162（51 张）已于 2026-09-06 创建，随后按用户指示合并整理为分类票 #163–#171（9 张），
  明细票全部关闭并附「并入 #N」评论；无重复票（UI10 唯一票 = #144，已并入 #167）。
- 本报告 §5 为最终权威映射；此后请以 9 张分类票追踪。