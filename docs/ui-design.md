# AppDev UI 设计规划（Material You × 现代直观）

> 状态：待确认。本文件是 UI 设计的权威参考，后续所有 UI ticket（T6/T24 及各功能页）必须遵循。
> 关联：spec #1（Implementation Decisions 主题/动效/图标节）、原型分支 prototype/markdown-renderer（渲染层已验证效果）。

---

## 1. 设计总纲

### 1.1 六条设计原则

1. **简洁流畅**：信息密度克制，每屏一个主任务；列表用 list item 而非重卡片；滚动 60fps 是硬指标
2. **动效丰富但克制**：所有动效有「意图」（反馈/转场/层级），非线性缓动，尊重系统「减弱动画」设置
3. **透明度合理分配**：容器用 M3 surface 层级（surfaceContainerLow/High 等）而非大量 alpha 叠加；alpha 只用于：按压反馈、禁用态、加载遮罩、悬浮层（且 ≤ 2 层）
4. **阴影精细且均匀**：全部用 M3 tonal elevation（1-5 级），禁止硬编码 shadow；层级 = 阴影层数 = 毛玻璃可选
5. **圆角全覆盖**：形状体系 AppDimens（cornerSmall=8 / medium=12 / large=16 / xl=28），所有容器/按钮/输入框/卡片/弹窗统一走形状令牌，视觉上「没有直角」
6. **图标体系统一**：Material Symbols（圆角变体、线条比默认稍细）+ Octicons 补充 GitHub 专属语义；**应用内任何地方不得用 emoji 当图标**（包括空态插图、按钮、标签）

### 1.2 红线

- 颜色一律 `MaterialTheme.colorScheme.*` + `ExtendedColors`，零硬编码十六进制
- 所有文案走 `stringResource`（en + zh-rCN 首批），含 contentDescription
- 布局一律 `start/end`（RTL 兼容），代码块保持 LTR
- 不用 emoji 作图标；不用 deprecated material-icons-extended
- 毛玻璃只出现在允许清单（§6），不滥用

---

## 2. 信息架构与导航（B站式）

### 2.1 全局骨架

```
┌─────────────────────────────────────────┐
│ AppTopBar（常驻）                         │
│ ┌───────────┐      ┌──┐ ┌────┐           │
│ │ 🔍 搜索…  │      │ 🔔│ │ 👤 │          │
│ └───────────┘      └──┘ └────┘           │
│  搜索框(可点)     通知(角标) 头像(进个人页) │
├─────────────────────────────────────────┤
│              内容区（页面容器）           │
│                                          │
├─────────────────────────────────────────┤
│  NavigationBar（M3，3 项）               │
│  首页 · 仓库 · 我的                     │
└─────────────────────────────────────────┘
```

- **顶栏**：左侧搜索框（胶囊形，点击展开搜索页）；右侧通知铃铛（未读角标）+ 头像（点击进个人主页）。游客显示默认头像，点击 → 登录引导
- **通知 = 弹出面板**：铃铛点击 → 全屏滑入面板（独立弹出界面查看，不走底部导航），见 §3.4
- **底部导航**：首页 / 仓库 / 我的。选中态 filled 图标 + 指示点，切换 Fade-through
- **个人主页双入口**：顶栏头像 + 底部「我的」→ 同一 Profile 页
- **深度导航**：所有 GitHub 链接（Markdown/WebView/深链）→ GitHubLinkParser → 应用内路由；External → Custom Tabs

### 2.2 首页：分区滑动（HorizontalPager）

```
┌─────────────────────────────────────────┐
│ [Trending] [News] [Issues] [PRs]        │  ← 可横向滚动的分区 Tab 条
├─────────────────────────────────────────┤
│                    │                     │
│   HorizontalPager   │  左右滑动切换分区    │
│   （每分区独立列表，    │                  │
│    各自 Paging 状态）  │                  │
└─────────────────────────────────────────┘
```

- 分区 Tab 条：M3 PrimaryTabRow/ScrollableTabRow，选中下划线指示器随滑动跟手
- Pager 滑动：`HorizontalPager` + `pagerState` 跟手动画，分区间无回弹阻隔
- 各分区独立保留滚动位置与加载状态（`rememberSaveable` 分区索引）
- 分区清单（首批 4 个，可扩展）：**Trending**（热门仓库）/ **News**（动态流）/ **Issues**（我参与+关注）/ **PRs**
- Gists / Bookmarks / **Stars** 归入「我的」页二级入口（§3.5/§3.7）

### 2.3 页面地图（全部页面）

| 层级 | 页面 | 入口 |
|---|---|---|
| 根 | 首页（分区 Pager） | 底部导航「首页」 |
| 根 | 仓库页 | 底部导航「仓库」 |
| 根 | 通知面板（弹出） | 顶栏铃铛（§3.4） |
| 根 | 我的（Profile） | 底部导航「我的」/ 顶栏头像 |
| 根 | 搜索页 | 顶栏搜索框 |
| 二级 | 仓库详情（README/文件树/代码/Releases） | 任意仓库链接/列表项 |
| 二级 | Issue 详情 | 链接/列表项 |
| 二级 | PR 详情（四 Tab） | 链接/列表项 |
| 二级 | Gists / Bookmarks / Stars 列表 | 我的页入口 |
| 二级 | 设置页 | 我的页入口 |
| 二级 | 代码浏览 / Diff / 编辑 | 仓库详情深链 |

---

## 3. 页面矩阵（布局 · 组件 · 动效 · 状态）

### 3.1 首页（Home）

- **布局**：AppTopBar + 分区 Tab 条 + HorizontalPager（每分区 LazyColumn）
- **分区内容**：
  - Trending：仓库卡（头像/名称/描述/语言色点+star 数），点击 → 仓库详情
  - News：动态条目（事件图标 + 仓库名 + 摘要 + 相对时间），点击 → 对应详情
  - Issues/PRs：条目（状态色点 + 标题 + 仓库名 + 标签 chips + 评论数），M3 ListItem
  - Stars：同 Trending 列表结构
- **组件**：AppListItem、AppStateChip、AppAvatar、LabelChipGroup、AppEmptyState、AppErrorState、AppLoadingState、AppPullToRefresh
- **动效**：分区滑动跟手；列表首项进入 slide+fade（stagger 24ms）；PullToRefresh M3 indicator；Star 微缩放+颜色
- **状态**：游客 → 首页显示 Trending/Stars 公共内容 + 顶栏头像引导登录；登录后全分区可用

### 3.2 仓库页（Repos）

- **布局**：顶栏 + 两段（「我的仓库」Tab / 「浏览」Tab：热门、最近访问历史）
- **条目**：仓库名（粗体）+ 描述 + 语言色点 + Star/Fork 数；我的仓库条目右滑显示 Star 快捷操作
- **二级**：仓库详情页（§3.8）
- **动效**：Tab 切换 Fade-through；列表项进入 slide+fade

### 3.3 搜索页（Search）

- **布局**：大号搜索框（自动聚焦）+ 历史记录 chips（可清空）+ qualifier 建议（`is:issue`、`language:kotlin`、`user:` 等）+ 结果 Tabs（仓库/用户/Issue/PR）
- **结果**：各类型列表（仓库卡/用户行/issue 行），Paging 分页
- **动效**：输入防抖 300ms 后结果区 Crossfade；历史 chips 进入动画
- **状态**：空搜索 → 历史；无结果 → AppEmptyState（文案 i18n）

### 3.4 通知面板（Notifications · 弹出式）

- **形态**：顶栏铃铛点击 → **全屏滑入面板**（ModalBottomSheet 全屏 / 独立弹层），独立弹出界面查看，不进底部导航；自带面板顶栏（标题「通知」+ 全部已读按钮 + 关闭），背景与主界面毛玻璃衔接（§6 清单 3）
- **布局**：过滤 chips（全部/参与/提及）+ 通知列表
- **条目**：事件图标（Octicons：issue/PR/mention/release）+ 摘要 + 相对时间；未读项左缘 primary 色条 + surfaceContainerHigh 底
- **动效**：面板 Slide+fade 滑入（Emphasized 500ms）；标记已读 → 色条淡出 + 条目重排（AnimatedItem）
- **状态**：未登录 → 登录引导空态；下滑/关闭按钮收起面板

### 3.5 我的 / 个人主页（Profile）

- **布局**：资料头（大号头像 + 昵称 + @login + 简介 + 统计行：仓库/Starred/关注者/关注中）+ 入口列表（Gists / Bookmarks / **Stars** / 设置 / 关于）
- **动效**：头像点击 1.1x 回弹；统计数字 AnimatedContent 滚动
- **状态**：游客 → 登录引导；他人主页（点任意用户）→ 只读展示 + 关注按钮

### 3.6 设置页（Settings）

- **布局**：分组列表（M3 设置规范）
  - 外观：主题模式（System/Light/Dark）、动态取色开关、seed 色盘、OLED 纯黑、高对比、圆角强度滑杆、动画强度滑杆、图标风格（Rounded/Outlined/Filled 预览）、代码字体、行号开关
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
- **README Tab**：Markdown 渲染（T7 产物）原生优先、复杂切 WebView 兜底
- **文件 Tab**：文件树（可展开）+ 代码浏览（Sora read-only）
- **动效**：Tab 切换 Fade-through；Star 按钮微缩放+填充动画；文件树展开/收起（AnimatedVisibility + 高度动画）
- **状态**：加载骨架（AppLoadingState）；错误 AppErrorState（重试）

### 3.9 Issue / PR 详情页

- **布局**：顶栏（返回 + 更多：分享/浏览器/复制链接）+ IssueHeaderCard（StateChip/标题/作者/标签/Assignee/Milestone/ReactionBar）+ MarkdownBody + TimelineList + 底部写评论栏（键盘 insets 适配）
- **PR 额外**：PrTabs（Conversation/Commits/Checks/Files changed）+ MergeBox
- **动效**：评论提交 → 乐观插入 + 滑入动画；Reaction 点击微缩放；底部栏随键盘上浮
- **状态**：权限决定操作可见性（viewerPermission）

### 3.10 代码浏览 / Diff / 编辑

- 代码：Sora read-only（行号/高亮/横向滚动）+ 悬浮跳行/搜索按钮
- Diff：unified/side-by-side 切换按钮 + 行号着色 + 行点击 → 评论输入（BottomSheet 滑入）
- 编辑：Sora 编辑 + 底部操作栏（保存/分支选择/commit message 对话框）
- **动效**：Diff 视图切换 Crossfade；行评论 BottomSheet Slide+fade

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

### 4.2 非线性动画清单（用户明确要求）

| 效果 | 实现 | 应用点 |
|---|---|---|
| 渐入渐出 | `AnimatedVisibility` slide+alpha / Crossfade | 列表项进入、页面转场、主题切换、结果区刷新 |
| 回弹 | `spring(dampingRatio = HighBouncy, stiffness = Medium)` | Star/收藏/点赞图标、头像点击、通知「全部已读」 |
| 叠加 | 层级化 LazyColumn stagger（24ms/项） | 首页/仓库/通知列表首次进入 |
| 元素位置变换 | `animateItemPlacement` / `AnimatedContent` | 列表排序、标记已读重排、统计数字变化 |
| 点按反馈 | M3 ripple + 可选 `scale(0.96f)` 按下 | 卡片、按钮、列表项 |
| 分区滑动 | HorizontalPager 跟手 + Tab 指示器联动 | 首页 |
| 图标状态 | FILL 轴 0↔1 过渡（可变字体） | 选中态/Star 切换 |

### 4.3 约束

- 遵循系统「减弱动画」：全局 `animationScale` 因子（用户可调 0-1）
- 滚动性能优先：不做无谓横向缩放与弹跳；列表滚动中禁用进入动画（首帧后）
- 所有动效时长 token 化（AppDimens 同级：AppMotion）

---

## 5. 图标规范（Material Symbols，圆角 + 稍细）

### 5.1 需求

- 圆角图标（rounded 风格）
- 线条比默认（weight 400）稍细 → 目标 **weight 300**
- 选中态 filled、未选中 outlined 的语义保留
- **应用内禁 emoji 图标**；GitHub 专属语义用 Octicons（MIT，手挑 SVG 入库）

### 5.2 技术方案（两级）

1. **基础**：com.composables Material Symbols（rounded/outlined/filled 静态变体）——原型已验证，兜底方案
2. **精细（首选，T6 落地时验证）**：Material Symbols **可变字体**（Google Fonts 分发），四轴控制：
   - `FILL` 0↔1（状态切换动画）
   - `wght` 100-700（取 **300** 达成「稍细」）
   - `ROND` 0-100（取 **100** 达成「圆角」最强）
   - `GRAD` -50~200（低强调场景 -25）
   - 备选库：dev.vicart material-symbols（KMP，可变轴支持）——需评估 compileSdk/版本兼容后定
3. 落地：图标封装为 `AppIcon(icon: AppIconSpec)` 组件，AppIconSpec 携带 style/weight/round，由主题 `iconStyle` 驱动

### 5.3 图标分类

| 分类 | 来源 | 例子 |
|---|---|---|
| 通用 UI | Material Symbols | home/search/notifications/settings/star/arrow… |
| GitHub 专属 | Octicons 手挑 SVG（~15 个） | merge、draft PR、branch、fork、issue、discussion、workflow、gist、bookmark、trending |

---

## 6. 毛玻璃规范（克制清单）

### 6.1 允许应用点（仅 5 处）

1. **AppTopBar**：内容滚动到顶栏下方时（列表滚动时背景模糊，静止时半透明纯色）
2. **NavigationBar**：同上，滚动内容滑入其下时
3. **BottomSheet** 背景层
4. **图片全屏查看器**背景（大图查看时）
5. **大图 Banner 悬浮信息层**（如仓库详情无，预留：Trending 推荐位）

### 6.2 技术方案

- **Android 12+（API 31+）**：`RenderEffect`（`BlurEffect`）——真实模糊，性能可控
- **API 26-30（minSdk 26 起步）**：降级为「半透明纯色 surface 层」（不做 bitmap 模糊，性能优先），视觉近似
- 封装 `AppBlur(modifier)`：内部按 SDK 分流；模糊半径 token（12dp 标准）
- 禁止：列表 item 内毛玻璃、多图层叠毛玻璃（≤2 层）、动态模糊（性能红线）

---

## 7. 主题系统

### 7.1 预设风格（首批 6 套）

| 预设 | seed | 说明 |
|---|---|---|
| Tonal Spot（默认） | 跟随系统动态取色 | Material You 标准 |
| Neutral | 中性灰 | 极简 |
| Vibrant | 高饱和色 | 鲜艳 |
| Expressive | 丰富色调 | M3 Expressive 风格基调 |
| GitHub Classic | GitHub 蓝 (#0969DA) | 品牌向 |
| Midnight | 深色系 | OLED 友好深蓝 |

### 7.2 能力

- 动态取色（Android 12+ 壁纸）优先；关闭则用 seed 生成（Material Color Utilities）
- 暗色 / OLED 纯黑 / 高对比模式
- 自定义：seed 色、对比度、圆角强度（0-1）、动画强度（0-1）、图标风格（Rounded/Outlined/Filled）、代码字体、行号开关
- 模型：`AppThemePreferences`（spec 已定）+ DataStore 持久化
- 扩展语义色 `ExtendedColors`：success/warning/info/merged/draft 映射 GitHub 状态
- WebView 侧：同一套令牌以 CSS 变量注入（markdown-you.css，T8）

---

## 8. 无障碍与 i18n

- **无障碍**：所有可点元素 contentDescription；StateChip 状态语义；48dp 触区；对比度达标；TalkBack 走查；大字号适配（sp 单位）；RTL（start/end）
- **i18n**：`values/`（en）+ `values-zh-rCN/`（zh）首批，全部 `stringResource`；plurals + 相对时间本地化；lint 四规则开启；**空态/错误/加载/引导文案全部 i18n**（含 emoji 禁止区——空态插图用矢量图标而非 emoji）

---

## 9. 技术要点（搜索确认）

| 项 | 结论 | 来源 |
|---|---|---|
| 毛玻璃 | Compose `Modifier.blur()` 只模糊自身内容，不能模糊背后 → 必须 RenderEffect/BlurEffect（API 31+）；<31 降级半透明纯色 | Android 官方 + psvmc 实现文 |
| 动效 | M3 新版推荐 spring（motion physics）；easing 系统仍用于转场：Emphasized 500 / decelerate 400 / accelerate 200 | m3.material.io |
| 细线圆角图标 | Material Symbols 可变字体支持 wght(100-700)/ROND(0-100)/FILL/GRAD 四轴 → 可实现 weight 300 + ROND 100 | Google Fonts 官方 |
| 应用内打开网页 | Chrome **Custom Tabs**（androidx.browser）——用户所提技术的名称；External 链接统一走它，保持应用内体验 | - |
| 分区滑动 | Compose `HorizontalPager` + ScrollableTabRow 联动 | Compose 官方 |
| 沉浸式 edge-to-edge | Android 15+ 强制 edge-to-edge：状态栏/导航栏透明，内容延伸至全屏；顶栏/底部导航做成沉浸式（毛玻璃清单 1/2 与此天然契合） | Android 官方设计指南 |
| M3 Expressive | 2025-2026 官方动效方向 = motion physics（spring 主导）+ 更大胆形状 + 动态色；本规划的 spring 回弹/Emphasized 转场与之一致 | m3.material.io / I/O 2025 |
| 官方审美参考 | Jetsnack（Compose 示例：M3 + 动态色 + 多类动画转场）、Reply（adaptive design 研究）——实现阶段对照学习 | developer.android.com samples |

---

## 10. 落地映射（对现有 ticket 的影响）

| Ticket | 影响 |
|---|---|
| T3 导航 | 骨架改为：AppTopBar（搜索/通知/头像）+ 底部导航 + 首页 Pager 容器；External → Custom Tabs |
| T6 主题 | 预设 6 套 + 动态取色 + AppMotion/AppDimens 令牌 + 图标可变字体方案（weight 300/ROND 100）+ AppIcon 封装 |
| T7/T8 渲染 | 无结构影响（样式走主题令牌） |
| T9 首页/README | 首页 = 分区 Pager（T9 含 README 渲染，首页结构在 T3 骨架） |
| T10 动态流 | News 分区 |
| T24 设置 | 设置项扩充（动画强度/圆角/图标风格预览/语言切换） |
| 各功能页 | 按 §3 页面矩阵落地布局与动效 |
| 新增建议 | 无新票；UI 基建（AppTopBar/NavigationBar/Pager/AppIcon/AppBlur/AppMotion）建议并入 T3+T6 验收标准 |

---

## 11. 待确认决策点

1. 底部导航 3 项 = 首页/仓库/我的；通知 = 顶栏铃铛弹出面板（不进底部导航）✅ 已确认
2. 首页分区首批 = Trending/News/Issues/PRs；Stars/Gists/Bookmarks 归「我的」二级 ✅ 已确认
3. 图标「细线」用可变字体（wght 300 + ROND 100）——新增依赖需评估（T6 实现期验证）；若评估不通过，退回 composables rounded（默认粗细）
4. 毛玻璃仅 5 处 + <31 降级半透明 ✅ 已确认
5. 预设主题 6 套命名/风格 ✅ 已确认
6. edge-to-edge 沉浸式（顶栏/底部导航透明延伸）✅ 已确认采用

---

## 12. 审美校验（2026 官方/权威来源）

- **M3 Expressive 方向一致**：官方 2025-2026 动效重心 = motion physics（spring 主导）、vibrant colors、contrasting shapes、flexible typography——本规划 spring 回弹 + 层级形状 + 动态色与其对齐
- **Compose-first 确认**：Material Android 官方已完全 Compose 优先，本技术栈正确
- **Edge-to-edge 合规**：Android 15+ 强制全屏沉浸；顶栏/底部导航毛玻璃（§6.1-1/2）与之天然契合
- **动态色是根基**：官方主题 builder + DynamicColors 是 Material You 核心，预设主题仅是兜底（§7）
- **对照案例**：Jetsnack（M3+动态色+动画）、Reply（adaptive）、Google 官方 Notifications guidance（通知设计）——实现阶段逐项对照
