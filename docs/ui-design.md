# AppDev UI 设计规范（Material You × 现代直观）

> 状态：**已确认**（2026-08-15，两轮 grill-with-docs 拍板）。本文件是 UI 设计的**权威参考**，后续所有 UI ticket（T6/T24 及各功能页）必须遵循。
> 决策来源：两轮 grill 文档（`docs/ui-grill.md` 用户答卷 + `docs/ui-grill-round2.md` 用户答卷）+ 2026-08-15 补充问答（底部 Tab 结构/Home 长条按钮/Trending 数据源）。
> 关联：spec #1（Implementation Decisions 主题/动效/图标节）、ADR-0004（主题引擎）、ADR-0005（渲染策略）、ADR-0006（导航架构）、原型分支 prototype/markdown-renderer、调研 `docs/research/webview-material-you-fusion.md`。

---

## 1. 设计总纲

### 1.1 设计原则（用户拍板）

1. **简洁流畅**：信息密度克制，每屏一个主任务；列表用 list item 而非重卡片；滚动 60fps 是硬指标
2. **动效丰富但克制**：所有动效有「意图」（反馈/转场/层级），**非线性 + 微微回弹**（加速后匀减速、越过一点回弹），尊重系统「减弱动画」设置
3. **透明度合理分配**：容器用 M3 surface 层级（surfaceContainerLow/High 等）而非大量 alpha 叠加；alpha 只用于：按压反馈、禁用态、加载遮罩、悬浮层（且 ≤ 2 层）
4. **阴影精细且均匀**：全部用 M3 tonal elevation（1-5 级），禁止硬编码 shadow；FAB 用 Compose 圆角矩形自带阴影（用户拍板，不做玻璃光晕）
5. **圆角全覆盖**：形状体系 AppDimens（cornerSmall=8 / medium=12 / large=16 / xl=28），所有容器/按钮/输入框/卡片/弹窗统一走形状令牌，视觉上「没有直角」
6. **图标体系统一**：Material Symbols（圆角变体、线条比默认稍细）+ Octicons 补充 GitHub 专属语义；**应用内任何地方不得用 emoji 当图标**（用户硬性要求，包括 Alert 卡片图标——必须 Octicons）

### 1.2 红线

- 颜色一律 `MaterialTheme.colorScheme.*` + `ExtendedColors`，零硬编码十六进制
- 所有文案走 `stringResource`（en + zh-rCN 首批），含 contentDescription
- 布局一律 `start/end`（RTL 兼容），代码块保持 LTR
- 不用 emoji 作图标；不用 deprecated material-icons-extended
- 毛玻璃只出现在允许清单（§6），不滥用
- **GitHub 独有功能必须用 GitHub 图标（Octicons）**，不用 Material 替代（用户拍板：涉及 GitHub 功能肯定用 GitHub 图标）

---

## 2. 信息架构与导航（两层导航结构——2026-08-15 拍板）

### 2.1 核心结构：底部大分区 × 首页内小分区

用户拍板：**「首页是大的，套着小的」**——底部 Tab 切换大分区，首页内部顶部有分区条切换小分区。

```
┌─────────────────────────────────────────┐
│ AppTopBar（常驻，跨大分区）                │
│ ┌───────────┐      ┌──┐ ┌────┐           │
│ │ 🔍 搜索…  │      │ 🔔│ │ 👤 │          │
│ └───────────┘      └──┘ └────┘           │
├─────────────────────────────────────────┤
│ 顶部小分区条（仅首页大分区内）              │
│ ┌──────────────────────────────────┐     │
│ │  动态  │  Issue │  PR           │     │ ← 横向条 + 底部主题色指示条
│ ├──────────────────────────────────┤     │
│ │  Home 字样 + 长条按钮 + feed      │     │ ← 内容区
│ └──────────────────────────────────┘     │
├─────────────────────────────────────────┤
│ NavigationBar（M3，3 项，大分区）         │
│  首页 · 仓库 · 我的                      │
└─────────────────────────────────────────┘
```

**决策明细**：

| 项 | 决策 |
|---|---|
| 底部 Tab（大分区） | **首页 / 仓库 / 我的**（3 个固定；「做哪些功能就加哪些」——后续功能多了可扩展） |
| 首页内分区条（小分区） | **动态 / Issue / PR**（首批；「做哪些功能就加哪些」，随功能扩展） |
| 分区条样式 | 横向条 + 底部主题色指示条（类似 GitHub 网页 Gists/Home/Issues 下划线指示器），点击/滑动切换 |
| 顶栏行为 | **顶栏固定不动**（用户拍板 A1-3=B）；只有内容区横移 + 分区栏跟随内容（外壳固定，Android 风格） |
| 内容切换 | HorizontalPager 左右滑，跟手动画，分区间无回弹阻隔 |
| 顶栏跨分区 | 搜索框/铃铛/头像在所有大分区共用常驻 |

### 2.2 首页（Home 大分区）

```
┌─────────────────────────────────────────┐
│ Home                                   │ ← 顶部左侧「Home」字样
│ ┌─────────────────────────────────────┐ │
│ │ ➕ 新建 Issue                        │ │ ← 长条按钮（占满宽度，带图标）
│ ├─────────────────────────────────────┤ │
│ │ 🔀 查看 Pull Requests               │ │ ← 长条按钮
│ ├─────────────────────────────────────┤ │
│ │ 📦 新建仓库                          │ │ ← 长条按钮（按功能扩展）
│ └─────────────────────────────────────┘ │
│ ↓ 然后下面是 feed 动态流                  │
│ [repo star 了 xxx]                      │
│ [repo 发布了 v1.2]                      │
│ ↓ trending repo 穿插                    │
└─────────────────────────────────────────┘
```

- **Home 字样 + 长条按钮 + feed + trending = 内容区**（用户拍板放内容区，非顶栏）——模仿 GitHub 网页登录后首页（用户确认示意图形态 OK）
- 长条按钮：占内容宽度、带图标、按下去直接进对应功能（新建 Issue 流程 / PR 列表 / 新建仓库）
- feed 内容：动态流（release 信息为主 + 关注仓库事件），与顶部小分区条「动态」分区关联
- trending repo：穿插在 feed 下方（数据源见 §2.5）

### 2.3 页面地图（全部页面）

| 层级 | 页面 | 入口 |
|---|---|---|
| 根 | 首页（Home 内容 + 小分区条） | 底部导航「首页」 |
| 根 | 仓库页（我的仓库） | 底部导航「仓库」 |
| 根 | 通知面板（弹出） | 顶栏铃铛（§3.4） |
| 根 | 我的（Profile） | 底部导航「我的」/ 顶栏头像 |
| 根 | 搜索页 | 顶栏搜索框 |
| 二级 | 仓库详情（README/文件树/代码/Releases） | 任意仓库链接/列表项 |
| 二级 | Issue 详情 | 链接/列表项 |
| 二级 | PR 详情（四 Tab） | 链接/列表项 |
| 二级 | Gists / Bookmarks / Stars 列表 | 我的页入口 |
| 二级 | 设置页 | 我的页入口 |
| 二级 | 代码浏览 / Diff / 编辑 | 仓库详情深链 |

### 2.4 深链与外部链接

- 所有 GitHub 链接（Markdown/WebView/深链）→ GitHubLinkParser → 应用内路由；External → Chrome Custom Tabs（T3 已落地）

### 2.5 Trending 数据源（2026-08-15 调研结论）

- GitHub 官方**无 Trending API**（已查证）
- gh4a 方案（已读源码）：第三方仓库 `Unpublished/GithubTrending` 维护的 JSON 镜像（`https://raw.githubusercontent.com/Unpublished/GithubTrending/trends/trending_{type}-all.json`，daily/weekly/monthly）
- **决策**：实现时先试第三方 JSON 镜像（与网页 Trending 一致，免费无 token）；若不稳定回退 Search API 变通（`created:>7d sort:stars`）。⚠️ 两者都做 fallback 链

---

## 3. 页面矩阵（布局 · 组件 · 动效 · 状态）

### 3.1 首页（Home）

- **布局**：AppTopBar + Home 内容区（Home 字样 + 长条按钮 + feed + trending） + 顶部小分区条（动态/Issue/PR）+ HorizontalPager
- **小分区内容**：
  - 动态：feed 条目（事件图标 Octicons + 仓库名 + 摘要 + 相对时间），点击 → 对应详情
  - Issue/PR：条目（状态色点 + 标题 + 仓库名 + 标签 chips + 评论数），M3 ListItem
- **Home 长条按钮**：占满宽度圆角矩形（medium 12dp），图标 + 文案，水波纹按压
- **组件**：AppListItem、AppStateChip、AppAvatar、LabelChipGroup、AppEmptyState、AppErrorState、AppLoadingState、AppPullToRefresh、LongBarAction（长条按钮）
- **动效**：分区滑动跟手 + 分区栏指示条联动；列表首项进入 slide+fade（stagger 24ms）；PullToRefresh M3 indicator；Star 微缩放+颜色
- **状态**：游客 → 首页显示 feed/trending 公共内容 + 顶栏头像引导登录；登录后全分区可用

### 3.2 仓库页（Repos）

- **布局**：顶栏 + 「我的仓库」列表（通栏卡片）
- **条目**：仓库名（粗体）+ 描述（2 行 …）+ 语言色点（GitHub 原色）+ Star/Fork 数 + 更新时间 + 作者头像
- **布局切换**：**网格 / 通栏两种布局，用户可切换**（列表页右上角视图切换按钮，用户拍板 B2-2）
- **交互**：点击 → 仓库详情；**长按弹出小窗选功能**（用户拍板，不用左滑）
- **动效**：列表项进入 slide+fade；布局切换 Crossfade
- **状态**：加载骨架；错误 AppErrorState（重试）；空态引导

### 3.3 搜索页（Search）

- **布局**：大号搜索框（自动聚焦）+ 历史记录 chips（可清空）+ qualifier 建议（`is:issue`、`language:kotlin`、`user:` 等）+ 结果 Tabs（仓库/用户/Issue/PR）
- **结果**：各类型列表（仓库卡/用户行/issue 行），Paging 分页
- **动效**：输入防抖 300ms 后结果区 Crossfade；历史 chips 进入动画
- **状态**：空搜索 → 历史；无结果 → AppEmptyState（文案 i18n）

### 3.4 通知面板（Notifications · 弹出式）

- **形态**：顶栏铃铛点击 → **右侧滑入全屏面板**（用户拍板 B + 细节确认）
- **遮罩**：中浅遮罩（背后隐约可见约 50%）+ 面板自身玻璃（用户拍板 2a：按建议）
- **面板样式**：全屏玻璃 + 自带小标题栏「通知」+ 右上角「全部已读」+「筛选」（用户拍板 A）
- **布局**：**按仓库分组 + 组内时间排序**（用户拍板 A3-2）；分组可折叠；每条：事件图标（Octicons）+ 摘要 + 相对时间 + 所属仓库名；未读项左缘 primary 色条
- **交互**：点击 → 对应详情页；左滑 → 操作（标已读/删除）；点遮罩关闭（背后不可交互，用户拍板）
- **动效**：面板 Slide+fade 滑入（与其他动效保持一致：非线性 + 微回弹，300ms 左右）；标记已读 → 色条淡出 + 条目重排（AnimatedItem）
- **状态**：未登录 → 登录引导空态

### 3.5 我的 / 个人主页（Profile）

- **布局**：资料头（大号头像 + 昵称 + @login + 简介 + 统计行：仓库/Starred/关注者/关注中）+ 入口列表（Gists / Bookmarks / **Stars** / 设置 / 关于）
- **动效**：头像点击 1.1x 回弹；统计数字 AnimatedContent 滚动
- **状态**：游客 → 登录引导；他人主页（点任意用户）→ 只读展示 + 关注按钮

### 3.6 设置页（Settings）

- **布局**：**分组卡片**（用户拍板：不同分组用不同卡片，**分组头不需要玻璃**）
  - 外观：主题模式（System/Light/Dark）、动态取色开关、seed 色盘、OLED 纯黑、高对比、圆角强度滑杆、动画强度滑杆、图标风格（Rounded/Outlined/Filled 预览）、代码字体、行号开关、**玻璃效果总开关 + 逐项开关**（§6.3）
  - 开发者：PAT 输入（明文显示开关）、降级提示、剩余配额展示
  - 通用：语言（en/zh）、关于
- **动效**：主题切换全屏 Crossfade；滑杆实时预览（圆角/动画即时生效）
- **i18n**：全部设置项文案中英

### 3.7 Gists / Bookmarks / Stars 列表页

- 统一「列表页」模板：标题 + LazyColumn
- Gists：gist 条目（文件名/语言色点/描述/时间）
- Bookmarks：保存的仓库/Issue（后续版本，首批可空态占位 + 收藏动作）
- **动效**：统一列表进入动画

### 3.8 仓库详情页（Repo Detail）

- **布局**：顶栏（返回 + 仓库名 + 更多菜单）+ 仓库头（名称/描述/Star/Fork/Watch 按钮 + 语言栏）+ 分区 Tab（README / 文件 / Releases）
- **README 交互（用户新想法）**：详情页顶部功能栏左右 Tab（默认 About）；README 下滑时头部信息**收起动画**（用户构思中，实现时给两版效果对比）
- **README Tab**：Markdown 渲染方向已拍板——WebView 主渲染（§3.11）
- **文件 Tab**：文件树（可展开）+ 代码浏览（Sora read-only）；文件列表：路径 + 分支选择 + 文件/文件夹显示修改时间
- **动效**：Tab 切换 Fade-through；Star 按钮微缩放+填充动画；文件树展开/收起（AnimatedVisibility + 高度动画）
- **状态**：加载骨架（AppLoadingState）；错误 AppErrorState（重试）

### 3.9 Issue / PR 详情页

- **布局**：顶栏（返回 + 更多：分享/浏览器/复制链接）+ IssueHeaderCard（StateChip/标题/作者/标签/Assignee/Milestone/ReactionBar）+ MarkdownBody + TimelineList
- **评论区**：评论按钮 = **右下角圆角矩形按钮**（Material 风格）→ 点击**上滑出评论输入框**（BottomSheet：圆角 + 把手滑条 + 编辑/预览切换 + 底部 md 功能按钮）（用户拍板 D2-3）
- **PR 额外**：PrTabs（Conversation/Commits/Checks/Files changed）+ MergeBox
- **动效**：评论提交 → 乐观插入 + 滑入动画；Reaction 点击微缩放；底部栏随键盘上浮
- **状态**：权限决定操作可见性（viewerPermission）

### 3.10 代码浏览 / Diff / 编辑

- 代码：Sora read-only（行号/高亮/横向滚动）+ 悬浮跳行/搜索按钮
- Diff：unified/side-by-side 切换按钮 + 行号着色 + 行点击 → 评论输入（BottomSheet 滑入）
- 编辑：Sora 编辑 + 底部操作栏（保存/分支选择/commit message 对话框）
- **动效**：Diff 视图切换 Crossfade；行评论 BottomSheet Slide+fade

### 3.11 Markdown 渲染方向（README/正文）

- **✅ 已拍板（2026-08-19 Task B 修订）**：**WebView 主渲染**（README/Issue 正文）+ **原生短文本**（评论/通知）（ADR-0007 修订版）
  - README：服务端 HTML 优先（`getReadmeHtml` 三级降级 + 双 key 缓存）；服务端异常 → 离线 GFM markdown-it 降级，renderMode 仍 WEBVIEW
  - Issue 正文：无服务端 HTML API → 离线 GFM（WebView 内 markdown-it）+ 融合样式
  - 渲染基建：github-markdown-css + markdown-it + highlight.js + DOMPurify；Material You 融合（MaterialYouFusionMapper 注入，Kotlin 预计算混色变量，深色 data-theme 翻转 + 首帧注入）
  - 评论列表/通知短文本保持原生（MarkdownViewer / EnhancedMarkdownViewer），铁律「评论列表绝不用 WebView」不变
- **历史**：2026-08-16 prototype 真机验证曾拍板「原生主 + WebView 兜底」；Task B（2026-08-19）因原生增强链 4 轮真机问题切换为 WebView 主渲染
- **已定渲染细节**（无论哪条路线都适用）：
  - 代码高亮配色：**C 半融合**——容器/工具条随主题 + 语法色保留 GitHub 原色（用户拍板）
  - Alert 卡片：**GitHub 网页样式（带左侧色条）** + 图标用 **Octicons**（禁 emoji，用户硬性要求）
  - 引用块：左竖条主题色 3dp + 淡底色（用户按建议）
  - 行内代码：rikkahub 效果（主题色底 + 主题色字）（用户拍板 9d）
  - 代码块背景：与卡片一致（M3 surfaceContainer，深色不再全黑；代码框/行内代码同款 10% 主题色淡底，真机验证）
  - 深色页面背景：近黑 #0B0B0D + 6% 主题色（用户拍板，WebView/原生统一）
  - 深色代码块背景：比正文背景更深一档（GitHub 做法 #161b22 层次）（用户拍板）
  - 内容宽度：左右留白与主页一致（用户拍板 9a）
  - 排版基准：**GitHub 网页基准**（正文 16sp / 1.6 行距，标题比例网页版）——用户明确「原生实现标题太大、WebView 刚好」，微调等实机
  - 图片：点击**向上 fade in** 全屏查看（背景纯黑）+ 圆角 + 阴影（用户拍板 9g）
  - 表格：GitHub 网页样式（表头加粗 + 上下边框 + 斑马纹 + 横向滚动）
  - checkbox：M3 风格（圆角方块 + 主题色勾）
  - 分隔线：细线 outlineVariant 1px，上下留白 16dp

---

## 4. 动效系统（M3 Motion）

### 4.1 缓动与时长（对齐 M3 easing-and-duration）

| 场景 | Easing | 时长 |
|---|---|---|
| 页面进入（大转场） | Emphasized decelerate | 400ms |
| 页面退出（永久） | Emphasized accelerate | 200ms |
| 页面进出（临时，BottomSheet/Drawer） | Emphasized | 500ms |
| 元素进出屏幕（列表项） | Emphasized decelerate | 300ms |
| 小型状态变化（图标填充/勾选） | Standard | 200ms |
| 按压反馈 | Standard accelerate | 150ms |

### 4.2 用户拍板的动效特性（两轮 grill）

- **分区切换动效（H1-1）**：非线性 + **微微回弹**——加速后匀减速、越过一点回弹（用户精确描述：加速→匀减速→过冲一点回弹）
- **页面转场（分区切换外）**：fade / fade slide（用户拍板）
- **通知面板滑入**：与其他动效保持一致（非线性 + 微回弹，300ms 左右）
- **列表 stagger（H2-2）**：可选开关（设置里）
- **Star 动画（H2-7）**：星形弹跳可以（用户确认）
- **下拉刷新（H2-8）**：M3 风格加载圈；无数据/错误 = 文字提示 + 重试按钮（用户拍板 B1-3）
- **刷新范围（B1-4）**：只刷新当前分区（用户拍板）
- **触觉反馈（H3-4）**：**不用**（用户拍板）
- **主题切换（F3-1）**：Crossfade（用户拍板前者）
- **WebView 深浅同步（F3-2）**：无闪烁方案（用户拍板后者）

### 4.3 非线性动画清单

| 效果 | 实现 | 应用点 |
|---|---|---|
| 渐入渐出 | `AnimatedVisibility` slide+alpha / Crossfade | 列表项进入、页面转场、主题切换、结果区刷新 |
| 回弹 | `spring(dampingRatio = HighBouncy, stiffness = Medium)` | Star/收藏/点赞图标、头像点击、通知「全部已读」 |
| 叠加 | 层级化 LazyColumn stagger（24ms/项，可选开关） | 首页/仓库/通知列表首次进入 |
| 元素位置变换 | `animateItemPlacement` / `AnimatedContent` | 列表排序、标记已读重排、统计数字变化 |
| 点按反馈 | M3 ripple + 可选 `scale(0.96f)` 按下 | 卡片、按钮、列表项 |
| 分区滑动 | HorizontalPager 跟手 + Tab 指示器联动 | 首页 |
| 图标状态 | FILL 轴 0↔1 过渡（可变字体） | 选中态/Star 切换 |

### 4.4 约束

- 遵循系统「减弱动画」：全局 `animationScale` 因子（用户可调 0-1）
- 滚动性能优先：不做无谓横向缩放与弹跳；列表滚动中禁用进入动画（首帧后）
- 所有动效时长 token 化（AppDimens 同级：AppMotion）

### 4.5 M3 Expressive 动效方案（2026-09-12 接入，ADR-0008）

**已接入官方的 Expressive 弹簧物理**，不再只有「时长 + 缓动」一层。

| 层 | 令牌 | 表达 | 管什么 |
|---|---|---|---|
| 1 转场 | `AppMotion`（§4.1/§4.3） | **显式时长**（ms）+ `Easing` | 页面进入/退出、fade-through 这类「A→B 有始有终」的编排 |
| 2 组件 | `AppMotionScheme` | **弹簧物理**（dampingRatio / stiffness） | 组件内可中断的位移/尺寸/形状/颜色（按压回弹、形变、展开） |

- `AppMotionScheme.standard()` / `expressive()` **直接委托官方**
  `MotionScheme.standard()` / `MotionScheme.expressive()`，不在项目内复刻 spring 常量
  （alpha 期官方调数值可自动跟进，也不会出现「项目常量与官方漂移」的隐性 bug）
- `AppTheme(motionScheme = AppMotionScheme.forMotionScale(motionScale))` —— 全树的
  `MaterialTheme.motionScheme` 消费方自动拿到 Expressive 物理；组件用自己的 spec 时
  应优先取 `MaterialTheme.motionScheme.*` 而不是新写 `spring(...)`
- 官方 Expressive 数值（1.5.0-alpha18 字节码实测）：spatial damping 0.6/0.8（**有回弹**）、
  effects damping **恒为 1.0（零回弹）**；spatial stiffness 200/380/800，effects 800/1600/3800。
  语义：**弹性只允许出现在空间维度**（位移/尺寸），颜色与透明度不得过冲
- **⚠️ spring 不受 `LocalMotionScale` 影响**：`LocalMotionScale` 只被
  `AppMotion.scaledDuration` 消费，作用对象是**时长**；`MotionScheme` 返回的 spec 没有任何
  缩放入口。系统侧「移除动画」由 Compose 内建 `MotionDurationScale` 兜底（整条动画被时间
  缩放，= 0 时下一帧结束），但**设置页「动画强度」滑杆管不到 spring**。故约定：
  **可感知的弹性动效，调用方必须自行判定缩放为 0 时跳过动画、直接落终态**
  （`CardGroup` 的 `if (motionScale <= NO_MOTION_SCALE) snap() else spring(...)` 是既有范式）；
  缩放为 0 时 `forMotionScale` 另会退化为 `standard()`（去回弹）。

### 4.6 Expressive 组件替换落地记录（2026-09-12）

全仓动效进度指示盘点：`CircularProgressIndicator` **25 处调用点 / 16 个文件**（另有 1 个
文件只残留 import、1 处 `LinearProgressIndicator`），逐个判定如下：

| 组件 | 结论 | 依据 |
|---|---|---|
| **整页 / 分区首载** → `LoadingIndicator`（形变加载） | ✅ **已替换 12 处** | 官方定位「should replace most uses of the indeterminate circular progress indicator」。统一入口 `AppLoadingState`（一处改动让 Home / RepoPicker / Notifications / Gists / Profile / Repos 自动受益）+ RepoDetail 整页与 Releases/Tags/ReleaseDetail/Readme 四分区 + Branches / CommitDetail / FileTree / FileViewer / Search 结果区 / PullRequestCreate |
| **按钮内 / 分页 pending**（16~24dp, strokeWidth 2dp） | ⬜ **保持 flat `CircularProgressIndicator`（11 处）** | 官方明令「In very small buttons, use the flat shape since the wavy shape is not as visible at that size」；同理 `LoadingIndicator` 的**默认容器是 48×48dp**（ActiveSize 38dp，alpha18 `LoadingIndicatorTokens` 实测），塞进 18dp 按钮同样不可读 |
| **搜索进行中通栏条** → `LinearWavyProgressIndicator` | ✅ **已替换 1 处** | wavy 的官方适配区间是「让较长过程不显得静止」且**需要足够宽度**；通栏搜索等待条宽度足够、波形可见。testTag / 读屏语义原样保留 |
| **Issue / PR 列表与详情的整页加载态** | ⬜ **未替换（有意回退，2 处）** | `IssueStateUi.kt` / `PullRequestStateUi.kt` 不在 `uiSourceExcludes`，且实测在覆盖率报告中 **0/24 行已覆盖**（Robolectric 加载的 Composable 不产 JaCoCo 数据，#181）。diff 门禁按比例判定，这两处会成为唯一纳入统计的文件 → 必然 0% 失败。**不自行放宽门禁排除名单**，留待单独决策（见 ADR-0008「已知遗留」） |
| **`SplitButtonLayout`**（MergeBox 主按钮 + 方法下拉） | ⬜ **未替换（本票不做）** | 语义上确是全仓唯一契合点（`Button` + 间距 + `FilledTonalIconButton` + `DropdownMenu` 手搓）。但不做的理由充分：① alpha18 的 SplitButton API 处于剧烈变动期（`SplitButtonShapes` + `SplitButtonDefaults.Leading/Trailing{For,Tonal…}` 重载族，现行官方示例里的 `leadingButtonShape` 在该版**不存在**，且 alpha20 才去门控）；② `SplitButtonLayout` 是自定义 **Box** measure policy（字节码实测 `maybeCachedBoxMeasurePolicy`，无 Row/weight），而现有合并行靠 `Modifier.weight(1f)` 撑满，leading 插槽类型是 `@Composable () -> Unit`（非 `RowScope`）→ 现有宽度行为无法机械保留；③ `feature:pullrequest` **无任何截图基线**且本票禁跑 record → 几何回归无法可视验证，而这条路径上是**合并 / 删除分支**等破坏性写操作。建议单独立票并附 Roborazzi 基线 |
| **`Medium/LargeFlexibleTopAppBar`** | ⬜ **本票不换** | 见 §6.5 |

---

## 5. 图标规范（Material Symbols，圆角 + 稍细）

### 5.1 需求（用户拍板）

- 圆角图标（rounded 风格）
- 线条比默认（weight 400）稍细 → 目标 **weight 300**（用户拍板 11b：400 起、提供 300 选项）
- **选中态 filled、未选中 outlined 的语义保留**（用户拍板 11a）——底部导航「选中实心 / 未选空心」**必须做**
- **应用内禁 emoji 图标**；GitHub 专属语义用 Octicons（MIT，手挑 SVG 入库）
- **图标候选必须给用户验证批准后才用**（用户拍板 11a：选出来自己验证）

### 5.2 技术方案（已验证）

1. **基础（立即可用）**：com.composables Material Symbols（rounded/outlined/filled 静态变体）——**已确认 outlined/filled 变体全部在依赖中**（toml L63-68），「空心/实心」切换**现在就能做**，不需要等变量字体
2. **精细（后续优化）**：Material Symbols 可变字体（Google Fonts 分发），四轴控制：
   - `FILL` 0↔1（状态切换动画）
   - `wght` 100-700（取 **300** 达成「稍细」）
   - `ROND` 0-100（取 **50** 达成圆润，用户拍板 11c：全局 30-50 微圆润）
   - `GRAD` -50~200（低强调场景 -25）
   - 备选库：dev.vicart material-symbols（KMP，可变轴支持）——需评估 compileSdk/版本兼容后定
3. 落地：图标封装为 `AppIcon(icon: AppIconSpec)` 组件，AppIconSpec 携带 style/weight/round，由主题 `iconStyle` 驱动

### 5.3 图标分类

| 分类 | 来源 | 例子 |
|---|---|---|
| 通用 UI | Material Symbols | home/search/notifications/settings/star/arrow… |
| GitHub 专属 | Octicons 手挑 SVG（~15 个） | merge、draft PR、branch、fork、issue、discussion、workflow、gist、bookmark、trending |

---

## 6. 毛玻璃规范（用户拍板版）

### 6.1 允许应用点（用户逐项拍板）

| # | 位置 | 用户决策 | 说明 |
|---|---|---|---|
| 1 | **AppTopBar** | ✅ 默认开 | 内容滚动到顶栏下方时模糊（macOS 访达侧边栏滚动穿越感） |
| 2 | **NavigationBar** | ✅ 默认开 | 同上 |
| 3 | **通知面板背景** | ✅ 默认开 | 面板滑入时背后模糊成雾面（玻璃最出彩处） |
| 4 | **BottomSheet 背景** | ✅ 默认开 | 底部弹出面板背后模糊 |
| 5 | **图片全屏查看器** | ❌ **改纯黑** | 用户拍板：看图沉浸优先，纯黑更沉浸 |
| 6 | **FAB 区域** | ❌ 不用玻璃 | 用 Compose 圆角矩形自带阴影（用户拍板） |
| 7 | **设置分组头** | ❌ 不用玻璃 | 用不同分组卡片区分（用户拍板） |
| 8 | **卡片本身** | ❌ 不透明 | 可读性优先；保持不透明 + 主题色点缀（用户拍板） |

> **#83 补充（2026-08-27 用户拍板）**：第 1 项「AppTopBar」的玻璃矩形**含首页小分区条副行**
> （分区条经 `AppTopBar(sectionBar = …)` 进同一块玻璃，属顶栏点位的内部构成，
> 不是新增第 5 处玻璃点位）。这是顶栏拿到「滚动穿越感」的几何前提；分区条自身
> containerColor 保持透明让模糊透上来，文字与指示条在 effect 层保持锐利。

### 6.2 毛玻璃的「本质」（用户拍板 12b）

- **用户要的是「时刻 3」：macOS 访达侧边栏效果**——内容滚过栏下面时，栏糊掉内容，但能看到「有东西在动」（滚动穿越感）
- **不是** iOS 控制中心的整屏雾感，**也不是**桌面小组件的透色同体感

### 6.3 强度与开关（用户拍板 3i）

- 模糊强度：**中（8dp）**
- 开关粒度：**全局总开关 + 设置页逐项开关**（顶栏/底栏/通知面板/BottomSheet 各自可开关）
- OLED 主题下：关闭背景图 + 玻璃（用户拍板 F2-1）
- 高对比主题下：禁用背景/玻璃（用户拍板 F1-3）

### 6.4 技术方案（issue #83 实现回写）

- **实现载体 = Haze（chrisbanes/haze）**，不是裸 `RenderEffect` API：
  **内容侧** `Modifier.hazeSource(state)` + **玻璃侧** `Modifier.hazeEffect(state)`，两侧共享一个
  `HazeState`，经 `LocalHazeState` 在「同时持有栏与内容」的容器里装配（`MainTabPager` 底栏 /
  `HomeScreen` 顶栏 / `MainActivity` 根级给通知面板）
- **Android 12+（API 31+）**：Haze 内部走 `RenderEffect`（`BlurEffect`）——真实模糊，性能可控
- **API 26-30（minSdk 26 起步）**：降级为「半透明纯色 surface 层」（不做 bitmap 模糊，性能优先），视觉近似
- 组件 `GlassSurface`（玻璃容器，对外 API 不变）；token `AppBlur`（`blurRadius` 8dp /
  `SCRIM_ALPHA` / `MIN_BLUR_API`）；模式判定 `GlassRenderPolicy`（纯函数，三条降级路径 + 生效
  路径 + 31 边界全部单测可断言——Robolectric 不渲染 RenderEffect、CI 模拟器在 API 30，
  不收敛成纯函数就无从验证）
- **几何约束（#83 真机踩过的坑，血泪）**：`hazeEffect` 只对**与玻璃矩形相交**的内容采样，于是
  ① 待模糊内容必须 full-bleed 铺到玻璃背后——insets 走滚动容器的 `contentPadding`，不是把内容
  整块 `Modifier.padding` 推到栏外；② 常驻在顶栏与列表之间的头部（首页小分区条）**必须进玻璃
  矩形**（`AppTopBar(sectionBar = …)` 副行插槽），否则它把列表视口整体下推，滚动内容永远进不了
  顶栏 → 顶栏只剩静止 scrim。两条都不满足时的表现就是「玻璃完全没效果」。
- 禁止：列表 item 内毛玻璃、多图层叠毛玻璃（≤2 层）、动态模糊（性能红线）
  ——首页「顶栏 + 小分区条」算**同一块**玻璃（一个 hazeEffect 矩形），不构成第 2 层

<<<<<<< HEAD
### 6.5 为什么**不**换用 M3 Expressive 的 `Medium/LargeFlexibleTopAppBar`（2026-09-12 决策）

M3 Expressive 提供了 `MediumFlexibleTopAppBar` / `LargeFlexibleTopAppBar`（1.5.0-alpha18 的
`AppBarKt` 实测存在，alpha25 起去门控）。**本票明确不采用**，理由如下：

1. **与 §6.4 的玻璃几何契约正面冲突**。本应用顶栏不是「一个 M3 顶栏组件」，而是
   `GlassSurface`（`hazeEffect` 矩形）**包住** `TopAppBar` + 副行插槽 `sectionBar` 的
   复合结构，且 `AppTopBar` KDoc 写死：「玻璃矩形的尺寸决定 backdrop 能糊到什么」、
   「分区条若留在内容列里，会把列表视口整体下推、滚动内容永远进不了顶栏矩形」。
   Flexible 系顶栏是**随滚动改变自身高度**的折叠栏——矩形高度逐帧变化，与
   `hazeEffect` 在布局期确定的采样矩形无法静态对齐：收缩时玻璃要么被裁掉一截（露出
   未模糊的内容），要么在栏下方留一条空白玻璃。这正是 #83「玻璃完全没效果」的同类根因。
2. **insets 契约会被推翻**。现方案是 `windowInsets = statusBars`（玻璃铺进状态栏）+
   `TopAppBar` 自身 insets 归零避免双重内缩；Flexible 系自带 `TopAppBarScrollBehavior`
   与自己的 insets/高度动画，两套 insets 语义叠加需要重新推导一遍并将 #83 的真机结论作废。
3. **`sectionBar` 副行必须与主行同处一个 hazeEffect 矩形**（首页小分区条）。折叠栏把
   主行高度做成滚动函数后，「主行 + 副行」的合计矩形不再是静态值，`AppTopBar` 对外
   暴露的「玻璃头总高度」契约（供 HomeScreen 决定内容 top inset）随之失效。
4. **收益与风险不成比例**。Flexible 顶栏的可见收益是「大标题随滚动折叠」，而本应用
   顶栏是**胶囊搜索框 + 铃铛 + 头像**的功能栏（没有 M3 意义上的 large title 可折），
   换过去等于用一次高风险的玻璃几何重做，换一个本场景并不存在的折叠大标题。

> 结论：**顶栏保持自建玻璃头**。若将来要试，必须单独立票，前置条件是把 #83 的玻璃几何
> 契约（§6.4）在真机上重新验证一遍，并明确「collapsing 矩形如何与 hazeEffect 静态采样
> 对齐」的技术方案。
=======
### 6.5 弹层点位（§6.1 #4 BottomSheet）的几何结论（issue #167 / UI22 实现回写）

`project-status` §3.1 曾挂账「M3 `ModalBottomSheet` 在独立 window 内，Haze 能否跨 window 采样
必须真机确认」。**已核实：不能，且不是能力问题而是几何事实**，无需真机复验：

- `ModalBottomSheet` 走 `ModalBottomSheetDialog`（`Dialog` → 自带 `ViewRootImpl`），与主 window
  是 SurfaceFlinger 的两层；Haze 1.6.10 在 `HazeEffectNode` 内用 `LocalView.current.windowId`
  过滤可采样区域（`Utils.android.kt#getWindowId`），跨 window 的 source 会被直接剔除；
  跨 window 也没有可共享的 `GraphicsLayer`/`RenderNode`
- 机械核实：`core/designsystem` 的 `GlassSheetSurfaceTest.modalBottomSheet_contentWindowDiffersFromMainWindow`
  用真实 `ModalBottomSheet` 断言弹层内 `LocalView.windowId` ≠ 主 window（且 `LocalHazeState`
  **确实**跨 window 下传——所以降级判据是窗口隔离，不是「拿不到 HazeState」）
- 因此弹层内挂 `hazeEffect` 只会采样同 window 内的纯色 dialog scrim（模糊纯色 = 零视觉差异）
  → 白付一次离屏 RenderNode。**实现取 §6.2 既有的半透明 scrim 路径**（`GlassSheetSurface`
  = `surfaceContainerLow` @ `AppBlur.SCRIM_ALPHA`，与栏侧同款取值、同一 token），
  逐项开关/OLED/高对比照旧裁决该点位是否走玻璃效果
- 未来若把弹层改成「主 window 内的覆盖层」（自绘 Sheet + 同 window `hazeSource`），
  只需放开 `GlassRenderPolicy.resolveInDialogWindow` 的返回值并同步单测，
  五处 `ModalBottomSheet` 调用点无需改动（判定与接线已经分离）
- **五处已接线**（issue #167 / UI22）：`IssueDetailScreen`（评论 Sheet + Labels/Assignees/Milestone
  编辑 Sheet）、`LineCommentSheet`、`ReviewSheet`、`PullRequestDetailScreen`（评论 Sheet）、
  `RepoPickerSheet`
- **两条降级实现已收敛为一条（2026-09-11 UI22 × #219 merge 回写）**：`GlassSheetSurface`
  现为 `GlassSurface` 的**薄封装**（传 `scope = GlassScope.BOTTOM_SHEET` +
  `backdropReachable = false` + 弹层基色 `surfaceContainerLow`），**不再拥有独立渲染实现**。
  收敛前 UI22 自带 `GlassRenderPolicy.sheetGlassAlpha`、#219 引入
  `GlassRenderPolicy.layerAlpha`，两者各自回答「降级该叠多厚」——正是本项目反复吃亏的
  「同一语义两套事实来源」。前者已删除：
  **全仓唯一的降级叠色出口 = `GlassRenderPolicy.layerAlpha`**，唯一的渲染路径判定 =
  `GlassRenderPolicy.resolve`。今后某点位若需要「降级即不透明」，一律透传
  `opaqueWhenBlurUnavailable`，**不得**再新写 alpha 分支。
>>>>>>> origin/main

---

## 7. 主题系统

### 7.1 预设风格（首批 6 套，ADR-0004 已定）

| 预设 | seed | 说明 |
|---|---|---|
| Tonal Spot（默认） | 跟随系统动态取色 | Material You 标准 |
| Neutral | 中性灰 | 极简 |
| Vibrant | 高饱和色 | 鲜艳 |
| Expressive | 丰富色调 | M3 Expressive 风格基调 |
| GitHub Classic | GitHub 蓝 (#0969DA) | 品牌向 |
| Midnight | 深色系 | OLED 友好深蓝 |

### 7.2 色彩性格（用户拍板 F1-1）

- **A 克制（M3 规范态）**——用户拍板：默认走规范、克制的 M3 色彩
- 动态取色：**默认开，可选**（用户拍板 F1-2）
- 高对比模式下禁用背景/玻璃（用户拍板 F1-3）

### 7.3 能力

- 动态取色（Android 12+ 壁纸）优先；关闭则用 seed 生成（Material Color Utilities）
- 暗色 / OLED 纯黑 / 高对比模式；**OLED 下关背景图 + 玻璃**（用户拍板 F2-1）
- 深色下背景图自动降低亮度（用户拍板 A4-4：浅色变白深色变黑；深色自动降亮度）
- 自定义：seed 色、对比度、圆角强度（0-1）、动画强度（0-1）、图标风格（Rounded/Outlined/Filled）、代码字体、行号开关
- 模型：`AppThemePreferences`（spec 已定）+ DataStore 持久化
- 扩展语义色 `ExtendedColors`：success/warning/info/merged/draft 映射 GitHub 状态
- WebView 侧：同一套令牌以 CSS 变量注入（调研确认可行，详见 `docs/research/webview-material-you-fusion.md`）

### 7.4 全局背景图（用户拍板 A4 + 12c）

- **默认无图**，用户自选（设置里可选背景图）
- 可选统一图片不透明度设置
- 浅色下背景变白、深色下背景变黑、深色自动降低亮度
- **背景固定不动**（不随内容滚动，用户拍板 A4-5）
- 背景图放 Compose 层（WebView 透明透出，调研结论：毛玻璃无法跨 WebView 边界）

---

## 8. 无障碍与 i18n

- **无障碍**：所有可点元素 contentDescription；StateChip 状态语义；48dp 触区；对比度达标；TalkBack 走查；大字号适配（sp 单位）；RTL（start/end）
- **i18n**：`values/`（en）+ `values-zh-rCN/`（zh）首批，全部 `stringResource`；plurals + 相对时间本地化；lint 四规则开启；**空态/错误/加载/引导文案全部 i18n**（含 emoji 禁止区——空态插图用矢量图标而非 emoji）

---

## 9. 技术要点（已调研确认）

| 项 | 结论 | 来源 |
|---|---|---|
| 毛玻璃 | Compose `Modifier.blur()` 只模糊自身内容，不能模糊背后 → 必须 RenderEffect/BlurEffect（API 31+）；<31 降级半透明纯色 | Android 官方 + psvmc 实现文 |
| 动效 | M3 新版推荐 spring（motion physics）；easing 系统仍用于转场：Emphasized 500 / decelerate 400 / accelerate 200 | m3.material.io |
| 细线圆角图标 | **已验证 outlined/filled 静态变体可立即用**（toml 依赖已含）；可变字体 wght(100-700)/ROND(0-100)/FILL/GRAD 四轴 → 可实现 weight 300 + ROND 50 | Google Fonts 官方 + 项目依赖实测 |
| 应用内打开网页 | Chrome **Custom Tabs**（androidx.browser）——External 链接统一走它，保持应用内体验 | - |
| 分区滑动 | Compose `HorizontalPager` + ScrollableTabRow 联动 | Compose 官方 |
| 沉浸式 edge-to-edge | Android 15+ 强制 edge-to-edge：状态栏/导航栏透明，内容延伸至全屏；顶栏/底部导航沉浸式与玻璃天然契合 | Android 官方设计指南 |
| M3 Expressive | 2025-2026 官方动效方向 = motion physics（spring 主导）；本规划 spring 回弹/Emphasized 转场与之一致 | m3.material.io / I/O 2025 |
| Trending 数据源 | 官方无 API；gh4a 用第三方 JSON 镜像（Unpublished/GithubTrending） | gh4a 源码（已读） |
| WebView Material You 融合 | github-markdown-css 全变量化 + 48 color role 映射可行；深色自动跟随；动效观感可复刻；毛玻璃无法跨 WebView 边界 | `docs/research/webview-material-you-fusion.md` |
| 原生渲染增强 | rikkahub 证明可行（表格工具栏/复制/字号比例/高亮引擎 hljs 移植）；其仓库 AGPL-3.0 不可引用，需从源头（hljs BSD-3）自行实现 | `docs/research/highlight-engine-analysis.md` |
| 官方审美参考 | Jetsnack（Compose 示例：M3 + 动态色 + 多类动画转场）、Reply（adaptive design 研究）——实现阶段对照学习 | developer.android.com samples |

---

## 10. 落地映射（对 ticket 的影响）

| Ticket | 影响 |
|---|---|
| T3 导航（已合入） | 骨架已建；**需按两层导航重构**：底部 3 Tab + 首页内分区条 + Home 长条按钮（新 UI 票） |
| T6 主题（已合入） | 6 套主题 + 动态色 + 令牌族已落地；**补：玻璃总开关 + 逐项开关、背景图设置、图标风格消费点**（新 UI 票） |
| T9 README（已合入） | WebView 主渲染已切换（Task B）；服务端 HTML + 离线 GFM 两级（§3.11） |
| T19 通知（已合入） | 按仓库分组 + 时间排序重构、面板样式调整（新 UI 票） |
| T20 我的（已合入） | 布局微调（Stars 入口已有） |
| T24 设置（已合入） | 补：玻璃开关组、背景图选项、布局视图切换 |
| T10 动态流 | 首页「动态」小分区 + Home 长条按钮 |
| T13 Issue（已合入） | 评论按钮/输入框交互按 D2-3 重构 |
| 各功能页 | 按 §3 页面矩阵落地布局与动效 |
| 新增建议 | **UI 修复波票**（两层导航重构 + 玻璃开关 + 背景图 + 卡片 PiliPlus 风格 + 图标验证 + 布局切换）；**README prototype 票**（§3.11，用户钦定） |

---

## 11. 待确认决策点（当前剩余）

1. 图标候选清单——**用户要求选出来验证批准**（§5.1）⏳
2. 仓库详情页 README 头部收起动画——用户构思中，实现时给两版效果对比 ⏳
3. 卡片 PiliPlus 风格细节——实现时参照 PiliPlus 首页卡片提取配色后给两版对比 ⏳

---

## 12. 审美校验（2026 官方/权威来源）

- **M3 Expressive 方向一致**：官方 2025-2026 动效重心 = motion physics（spring 主导）、vibrant colors、contrasting shapes、flexible typography——本规划 spring 回弹 + 层级形状 + 动态色与其对齐
- **Compose-first 确认**：Material Android 官方已完全 Compose 优先，本技术栈正确
- **Edge-to-edge 合规**：Android 15+ 强制全屏沉浸；顶栏/底部导航毛玻璃与之天然契合
- **动态色是根基**：官方主题 builder + DynamicColors 是 Material You 核心，预设主题仅是兜底（§7）
- **对照案例**：Jetsnack（M3+动态色+动画）、Reply（adaptive）、Google 官方 Notifications guidance（通知设计）——实现阶段逐项对照
- **参考仓库**：~/dev/PiliPlus（卡片主题色点缀）、~/dev/rikkahub（原生 Markdown 全套）、~/dev/XMSLEEP（MD3 白噪音）、~/dev/gh4a（WebView markdown + Trending 数据源）——已克隆本地供实现对照
