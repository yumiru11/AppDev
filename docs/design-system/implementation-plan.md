# 设计系统落地实施计划（SPEC-1 组件 / SPEC-2 间距 / UI-2 选中态）

> 本文件是 **docs-only 前置产出**：把「组件 / 间距 / 选中态」三项设计系统缺口的落地路径、API 契约、批次、门禁与验收标准一次性钉死。**本文件不包含任何生产代码改动**；实现只在 AGP 9 迁移合入后开始（见 §2 决策 2）。

---

## §0 元信息

| 项 | 值 |
|---|---|
| 日期 | 2026-09-12 |
| 状态 | Accepted（冻结待执行；实现依赖 AGP 9 合入） |
| 触发 | 残余审计缺口收敛 `docs/agents/remaining-backlog-2026-09-12.md` §3.2 的 **SPEC-1 / SPEC-2 / UI-2** 三条 P1 |
| 范围 | ① 高频率组件 ② 三个状态视图去重 ③ 间距 scale + 迁移收口 |
| 硬依赖 | **AGP 9 迁移必须先合入**；本计划文档是 AGP 9 之前唯一允许推进的工作 |
| 相关 ADR | [ADR-0010](../adr/0010-design-system-rollout.md)（本计划决策记录） |
| 对照报告 | `docs/agents/spec-audit-2026-09-11.md` §4.4 / §5.5；`docs/agents/remaining-backlog-2026-09-12.md` §3.2 |
| 明确不做 | markdown 阅读密度（段间距/行间距/左右/缩进，见 §2 决策 8）、47 项清单里的 GitHub 专属组件、全量 594 处 `.dp` 扫荡（独立票，见 §4 Batch 3） |

---

## §1 背景与问题（实测复核）

### 1.1 计数口径声明

本节的数字 **全部在 main@`c636397` 上重新实测**，不复用审计报告原值。为消除歧义，口径固定为：

- **来源**：仓库内 `*.kt` 主源；**排除** `*/build/**`、`*/src/test/**`、`*Test.kt`。
- **匹配**：POSIX 词边界 `\b<Call>(`，例：`\bCard(` 命中裸 `Card(`，**不**命中 `RepoListCard(` / `RepoGridCard(` / `MilestoneCard(` 等自定义命名控件。
- **裸调用**：命中行即调用点；`core/designsystem/`、`core/ui/` 内对底层 M3 组件的**包装实现**单列，不计入迁移目标。

> 为什么强调词边界：审计的口径是**朴素子串** `Card(`，会把 `RepoListCard(`、`SnackbarHostState(` 一并计入，这是下文差异的主因。词边界口径才是「该迁移多少处」的真实答案。

### 1.2 裸控件实测计数

| 控件 | 词边界实测（总数） | 其中 core 包装实现 | **迁移目标** | §3.2 口径 | §5.5 口径 |
|---|---|---|---|---|---|
| `Card(` | 19 | 0 | **19** | 44 | 19 |
| `Scaffold(` | 21 | 1（`core/ui/MainTabPager.kt`） | **20** | 20 | 24 |
| `FilterChip(` | 16 | 0 | **16** | 16 | 15 |
| `AlertDialog(` | 14 | 0 | **14** | 15 | 15 |
| `ModalBottomSheet(` | 7 | 1（`core/designsystem/.../GlassSheetSurface.kt`） | **6** | 6 | 6 |
| `SnackbarHost(` | 2 | 0 | **2** | 10 | 10 |
| **合计** | **79** | **2** | **77** | **111** | **89** |

**三方差异与原因（必须记录，不许抹平）**：

1. **`Card(` 19 vs §3.2 的 44**：§3.2 用子串口径，把 `RepoListCard(` / `RepoGridCard(` / `MilestoneCard(` 等**自定义命名**的卡片函数算成裸 `Card(`。词边界实测 19，其中真正调 M3 `Card(` 的只有 19 处。§5.5 的 19 与本次实测**完全一致**，佐证词边界才是正确口径。
2. **`SnackbarHost(` 2 vs 审计 10**：审计子串把 `SnackbarHostState(`（状态持有对象，非控件）计入。全仓裸 M3 `SnackbarHost(` 仅 2 处（`app/MainActivity.kt:667`、`feature/repo/RepoDetailScreen.kt:203`）；各 feature 早已统一走 `core/ui/AppSnackbarHost.kt:42`。
3. **`Scaffold(` 21 / `AlertDialog(` 14 / `ModalBottomSheet(` 7**：与审计差 1 到 3 处，来自注释/KDoc 提及与 core 包装实现的归类差异，不影响迁移面。
4. **§3.2 的「111 处」是子串口径**：111 = 20+44+16+15+10+6。本计划把波级 DoD 的基准**改钉在词边界口径**（77 处），并把门禁基准写死在 `.github`（§5）。审计的 111 仅留作历史对照，**不作为清零目标**。

#### 1.2.1 各控件 feature 分布与文件清单

**`Card(`（19）**

| 模块 | 处数 | 文件 |
|---|---|---|
| feature/repo | 10 | `RepoDetailScreen.kt`(6)、`CommitDetailScreen.kt`(2)、`FileViewerScreen.kt`(1)、`BranchesScreen.kt`(1) |
| feature/search | 4 | `SearchResultRows.kt`(4) |
| feature/home | 2 | `ui/TrendingSection.kt`(1)、`ui/FeedRow.kt`(1) |
| feature/pullrequest | 1 | `PullRequestListScreen.kt`(1) |
| feature/notifications | 1 | `ui/NotificationsPanel.kt`(1) |
| feature/issue | 1 | `IssueListScreen.kt`(1) |

**`Scaffold(`（21，迁移目标 20）**

| 模块 | 处数 | 文件 |
|---|---|---|
| feature/repo | 8 | `ReposScreen`、`RepoDetailScreen`、`ReleaseCreateScreen`、`FileViewerScreen`、`FileEditScreen`、`CreateRepoScreen`、`CommitDetailScreen`、`BranchesScreen`（各 1） |
| feature/pullrequest | 3 | `PullRequestListScreen`、`PullRequestDetailScreen`、`PullRequestCreateScreen`（各 1） |
| feature/issue | 3 | `IssueListScreen`、`IssueDetailScreen`、`CreateIssueScreen`（各 1） |
| feature/profile | 2 | `ProfileScreen`、`GistsScreen`（各 1） |
| feature/home / search / settings / editor | 各 1 | `HomeScreen`、`SearchScreen`、`SettingsScreen`、`MarkdownEditorScreen` |
| core/ui | 1 | `MainTabPager.kt`（包装实现，不计迁移） |

**`FilterChip(`（16）**

| 模块 | 处数 | 文件 |
|---|---|---|
| feature/pullrequest | 6 | `ReviewSheet.kt`(3)、`PullRequestListScreen.kt`(3) |
| feature/issue | 4 | `IssueListScreen.kt`(2)、`IssueDetailScreen.kt`(1)、`CreateIssueScreen.kt`(1) |
| feature/settings | 3 | `AppearanceSettingsSection.kt`(2)、`GeneralSettingsSection.kt`(1) |
| feature/repo | 2 | `RepoDetailScreen.kt`(2) |
| feature/notifications | 1 | `ui/NotificationsPanel.kt`(1) |

**`AlertDialog(`（14）**

| 模块 | 处数 | 文件 |
|---|---|---|
| feature/repo | 6 | `FileEditScreen.kt`(2)、`BranchesScreen.kt`(2)、`RepoDetailScreen.kt`(1)、`FileViewerScreen.kt`(1) |
| feature/pullrequest | 4 | `PullRequestDetailScreen.kt`(4) |
| feature/issue | 3 | `IssueDetailScreen.kt`(3) |
| feature/settings | 1 | `AboutDialog.kt`(1) |

**`ModalBottomSheet(`（7，迁移目标 6）**

| 模块 | 处数 | 文件 |
|---|---|---|
| feature/pullrequest | 3 | `ReviewSheet.kt`、`PullRequestDetailScreen.kt`、`LineCommentSheet.kt`（各 1） |
| feature/issue | 2 | `IssueDetailScreen.kt`(2) |
| feature/home | 1 | `ui/RepoPickerSheet.kt`(1) |
| core/designsystem | 1 | `GlassSheetSurface.kt`（包装实现，不计迁移） |

**`SnackbarHost(`（2）**：`app/MainActivity.kt:667`、`feature/repo/RepoDetailScreen.kt:203`。二者是**仅存**没走 `AppSnackbarHost` 的直接调用。

### 1.3 三个状态视图现状

`core/designsystem/.../component/AppStateViews.kt` 已提供 **public、可复用、语义完整** 的三件套：

| 组件 | 行号 | 语义 | 现网使用 |
|---|---|---|---|
| `AppEmptyState(icon, title, modifier, message, actionLabel, onAction)` | `:39` | 装饰图标无 contentDescription，文本承载信息 | 部分屏 |
| `AppErrorState(title, modifier, message, actionLabel, onAction)` | `:66` | 内置 Octicons Alert | 部分屏 |
| `AppLoadingState(modifier, label)` | `:101` | M3 `LoadingIndicator` 形变指示器 | 多数屏 |
| `AppCenteredLoadingState(modifier, label)` | `:190` | 上者居中铺满 | issue/PR 加载态 |

**问题不是「缺组件」，而是「有组件没人用」**：审计 §4.4 记录 **17 份私有重复实现** + 3 个 internal 包装（`HomeScreen.kt:605,611,621,631`、`IssueListScreen.kt:323,334`、`ProfileScreen.kt:606,618,629,674`、`PullRequestListScreen.kt:320`、`PullRequestTabContent.kt:309`、`SearchScreen.kt:519,526,562,571`、`IssueDetailScreen.kt:1075`）。

> 复核差异：本次用宽松正则 `private fun .*(Empty|Error|Loading|Placeholder)` 得 34 处，高于审计的 17。差额来自命名更宽（含 `Placeholder`）与 internal 包装。**Batch 2 开工前须逐文件复算一次**，以真实调用点为准；本计划先采信审计的 17 作为工作量下界。

### 1.4 `AppDimens` 现状

`core/designsystem/.../token/AppDimens.kt` 当前 **只有圆角 + 3 个孤立的间距常量**：

| 类别 | 现有成员 |
|---|---|
| 圆角 | `cornerExtraSmall`(4)、`cornerSmall`(8)、`cornerMedium`(12)、`cornerLarge`(16)、`cornerCardOuter`(20)、`cornerCardInner`(4)、`cornerExtraLarge`(28) |
| 间距 | `contentPadding`(16)、`minTouchTarget`(48)、`fabContentClearance`(96) |

**没有 spacing scale**。全仓引用 `AppDimens.` 仅 **66 处**，而裸 `.dp` 字面量约 594 处（见 1.5），令牌覆盖率约 10%。

### 1.5 裸 `.dp` 计数

| 口径 | 实测 | 审计原值 | 说明 |
|---|---|---|---|
| `feature/*/src/main` 裸 `\.dp` | **594** | 639 | 差额 45 来自注释/KDoc 中的 `.dp` 提及与排除规则差异 |
| `feature/ + core/ + app/`（主源，excl test） | **714** | 无 | 全量口径，含 core 与 app |
| `AppDimens.` 引用（主源） | **66** | 无 | 令牌化率约 10% |

**结论**：594 处量级的全量扫荡**不在本波**（决策 7）。本波只做「定义 scale + 新代码强制 + 按 feature 机会主义迁移」，全量作为独立票。

### 1.6 `plan.md §5.5` 21 个通用组件 vs 仓库现状

| # | 组件 | 判定 | 当前证据 / 缺口 |
|---|---|---|---|
| 1 | `AppTopBar` | ✅ 已存在（参考） | `core/ui/AppTopBar.kt:55`；硬绑搜索/通知/资料/未读数，本波不动 |
| 2 | `AppScaffold` | ❌ 新建 | 裸 `Scaffold(` 21 处 |
| 3 | `AppNavigationBar` | ✅ 等效（参考） | `core/ui/AppBottomBar.kt:40`（内层 `NavigationBar`） |
| 4 | `AppNavigationRail` | ⏸ 暂缓 | `\bNavigationRail\(` 全仓 **0** 命中；plan 独有，无产品需求，**不做** |
| 5 | `AppTabRow` | 🔶 收编 | 5 份私有：`HomeScreen`、`PullRequestDetailScreen`、`SearchScreen`、`ProfileScreen`、`MarkdownComposer` |
| 6 | `AppChip`/`AppFilterChip` | ❌ 新建 | 裸 `FilterChip(` 16 处 |
| 7 | `AppLabelChip` | 🔶 收编 | `IssueDetailScreen.kt:715`、`PullRequestDetailScreen.kt:848` 两份 private（形参类型不同） |
| 8 | `AppStateChip` | ✅ 已存在 | `AppStateChip.kt:135,157`（双入口：状态枚举 / 语义角色） |
| 9 | `AppAvatar` | 🔶 收编 | `ReposScreen.kt:560`、`ProfileScreen.kt:725` 两份 private |
| 10 | `AppAvatarRow` | ❌ 新建 | 0 命中 |
| 11 | `AppCard` | ❌ 新建 | 裸 `Card(` 19 处 |
| 12 | `AppListItem` | ❌ 新建 | 裸 `ListItem(` 仅 2 处 |
| 13 | `AppSectionHeader` | 🔶 收编 | `SettingsScreen.kt:121` internal（feature 内，跨模块不可复用） |
| 14 | `AppEmptyState` | ✅ 已存在 | `AppStateViews.kt:39` |
| 15 | `AppErrorState` | ✅ 已存在 | `AppStateViews.kt:66` |
| 16 | `AppLoadingState` | ✅ 已存在 | `AppStateViews.kt:101`（+ `AppCenteredLoadingState:190`） |
| 17 | `AppPullToRefresh` | ❌ 新建 | 裸 `PullToRefreshBox(` 6 处 |
| 18 | `AppSearchBar` | 🔶 收编 | `feature/search/SearchTopBar.kt:38` internal |
| 19 | `AppBottomSheet` | ❌ 新建 | 裸 `ModalBottomSheet(` 6 处（迁移目标） |
| 20 | `AppDialog` | ❌ 新建 | 裸 `AlertDialog(` 14 处 |
| 21 | `AppSnackbar` | ✅ 已存在 | `core/ui/AppSnackbarHost.kt:42`（动效走 `AppMotion` 令牌） |

**汇总**：已存在/等效 7 项（1、3、8、14、15、16、21）、收编 5 项（5、7、9、13、18）、新建 8 项（2、6、10、11、12、17、19、20）、暂缓 1 项（4）。**与审计「只落地 4 项」相比，本轮修复波已把状态视图三件套 + AppSnackbarHost 补齐**，缺口收敛为「8 新建 + 5 收编」。

### 1.7 附带发现（本次复核新暴露，非本波范围）

**`main` 上 `core/designsystem/.../component/AppStateViews.kt:171-186` 残留 Git 冲突标记**（`<<<<<<< HEAD` / `=======` / `>>>>>>> origin/main`），位于 `AppLoadingState` 上方的 KDoc 块注释内部，因此**不报编译错**（整段在 `/* ... */` 里），但属于真实仓库卫生缺陷。建议开独立清理票（docs-only 任务不动生产代码）。来源提交：`6c1d997`（merge into material3-expressive-alpha18 分支）。

---

## §2 决策记录（10 条，已批准，不再重议）

| # | 决策 | 一句话理由 |
|---|---|---|
| 1 | 范围 = 组件 / 间距 / 选中态三者全做，分 **3 批**：① 高频率组件 ② 状态视图去重 ③ 间距 scale + 收口 | 先做收益最高、调用点最多的，风险与面量递减 |
| 2 | 与 AGP 9 迁移**串行**：AGP 9 合入后才开工；本份计划是此前唯一允许的工作 | 两条大迁移并行会让 screenshot 基线与构建脚本同时漂移，不可归因 |
| 3 | **像素等价红线**：同 props 下新旧渲染必须逐像素一致（纯重构）；任何视觉变化须独立票 + 用户批准；迁移期截图基线**不得变更** | 保证「组件化」与「视觉改版」分离，出事能二分定位 |
| 4 | 组件 API 风格 = **厚包装 + 逃生舱**：内置意见默认值，同时保留透传底层 M3 组件的参数 | 让 90% 调用点零参数，10% 特例不被封死 |
| 5 | 迁移策略 = **按 feature 分批**（一批一 PR + 该 feature 的截图 verify）；新增 CI 门禁**禁新增裸控件**（存量只减不增） | 每个 PR 可独立回归、独立回滚；门禁锁住回流 |
| 6 | UI-2 选中态：显式 `selectedContainerColor = colorScheme.secondaryContainer`，收口到 `AppFilterChip` / `AppSegmentedButton`；合并前给用户看**前后对比截图** | 消除「用户分不清当前筛选/视图」，且把色决策收进设计系统 |
| 7 | 间距 scale：`AppDimens.spacing` = 4/8/12/16/24/32（xs/s/m/l/xl/xxl）+ 保留旧常量别名；本波 = 定义 + 新代码强制 + 机会主义迁移；~594 处全量扫荡独立成票 | 一次 594 处改动无法审、无法回滚；先把「尺子」定义对 |
| 8 | 用户真机实测的 markdown 阅读密度（段间距 0.6、行间距 0.3、左右 0.7、md 缩进每层 0.4，cm）**明确排除**在本设计系统之外，另开未来票 | 那是**阅读排版**（markdown 专用、字体/行高联动），与 UI 结构间距（组件 gap/padding）正交 |
| 9 | 私有组件仅三个状态视图提升进 `core:designsystem`；其余私有组件保留（三法则：出现 3 次才提升） | 避免为「可能的复用」过度抽象 |
| 10 | 验证设施：debug-only **设计系统画廊屏**（组件 × 明暗 × 关键状态），配 Roborazzi 基线，接入 CI verify | 组件契约需要可执行的可视证据，不能只靠 KDoc |

---

## §3 组件 API 契约（21 项）

统一约束（全部组件适用）：

- **i18n**：组件内部**零硬编码文案**；所有可见文本与 `contentDescription` 由调用方经 `stringResource()` 传入（en + zh-rCN 成对）。
- **语义**：可点击组件必须有可播报角色；纯装饰图标 `contentDescription = null`；图文合并节点用 `semantics(mergeDescendants = true)`。
- **RTL**：一律 `start/end` 与 `PaddingValues(start=, end=)`，禁 `left/right`；内容方向由父 `LayoutDirection` 决定。
- **像素等价**：默认值 = **现状最常用值**（下述每项已到代码核实）；逃生舱参数默认 = 底层 M3 组件默认。
- **逃生舱**：`modifier` 恒为第一个可选参数；`shape`/`colors`/`elevation`（容器类）与 `contentPadding`、`interactionSource` 等按需透传。

### 3.1 新建组件

**① `AppScaffold`（新建，`core:designsystem`）**

```kotlin
@Composable
fun AppScaffold(
    modifier: Modifier = Modifier,
    topBar: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    containerColor: Color = MaterialTheme.colorScheme.background,
    contentColor: Color = contentColorFor(containerColor),
    contentWindowInsets: WindowInsets = ScaffoldDefaults.contentWindowInsets,
    content: @Composable (PaddingValues) -> Unit,
)
```

- 默认值逐项等于 `ScaffoldDefaults`，**渲染等价裸 `Scaffold`**（像素等价红线）。
- 逃生舱：`containerColor` / `contentColor` / `contentWindowInsets` 全透传。
- 迁移 20 处；`core/ui/MainTabPager.kt` 的 1 处评估后单独处置。

**② `AppFilterChip` + `AppChip`（新建，`core:designsystem`）**

```kotlin
@Composable
fun AppFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
    selectedContainerColor: Color = MaterialTheme.colorScheme.secondaryContainer,
    containerColor: Color = FilterChipDefaults.filterChipColors().containerColor,
    labelColor: Color = Color.Unspecified,
    border: BorderStroke? = null,
    interactionSource: MutableInteractionSource? = null,
)
```

- **UI-2 核心**：`selectedContainerColor` 显式钉在 `secondaryContainer`。开工第一步先核实「裸 `FilterChip` 现有选中容器色是否已等于 `secondaryContainer`」：若等值，本项**零视觉变化**；若不等，走决策 6 的前后对比审批。
- `AppChip` 为 `AssistChip` 语义的薄包装（无 `selected`）。
- 迁移 16 处（+ `SegmentedButton` 同款收口见 §3.2）。

**③ `AppCard`（新建，`core:designsystem`）**

```kotlin
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    shape: Shape = CardDefaults.shape,
    colors: CardColors = CardDefaults.cardColors(),
    elevation: CardElevation = CardDefaults.cardElevation(),
    content: @Composable ColumnScope.() -> Unit,
)
```

- 默认值 = `CardDefaults` 全默认，**渲染等价裸 `Card`**；`onClick != null` 时切到可点击重载（同一 shape/colors）。
- 迁移 19 处；`RepoListCard` / `RepoGridCard` 等**自定义命名控件保留**（它们不是裸控件，仅内部换用 `AppCard`）。

**④ `AppDialog`（新建，`core:designsystem`）**

```kotlin
@Composable
fun AppDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    shape: Shape = AlertDialogDefaults.shape,
    containerColor: Color = AlertDialogDefaults.containerColor,
    tonalElevation: Dp = AlertDialogDefaults.TonalElevation,
    properties: DialogProperties = DialogProperties(),
)
```

- 默认值 = `AlertDialogDefaults`，**渲染等价裸 `AlertDialog`**。迁移 14 处。

**⑤ `AppBottomSheet`（新建，`core:designsystem`）**

```kotlin
@Composable
fun AppBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(),
    shape: Shape = BottomSheetDefaults.ExpandedShape,
    containerColor: Color = BottomSheetDefaults.ContainerColor,
    scrimColor: Color = BottomSheetDefaults.ScrimColor,
    dragHandle: @Composable (() -> Unit)? = { BottomSheetDefaults.DragHandle() },
    content: @Composable ColumnScope.() -> Unit,
)
```

- 默认值 = `BottomSheetDefaults`，**渲染等价裸 `ModalBottomSheet`**。迁移 6 处。
- 注意与既有 `GlassSheetSurface.kt`（已包装 `ModalBottomSheet`）的关系：优先让 `GlassSheetSurface` 内部改调 `AppBottomSheet`，不强行合并两者（玻璃变体是 escape hatch 的合法用例）。

**⑥ `AppPullToRefresh`（新建，`core:designsystem`）**

```kotlin
@Composable
fun AppPullToRefresh(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    state: PullToRefreshState = rememberPullToRefreshState(),
    indicator: @Composable BoxScope.() -> Unit = { Indicator(state) },
    content: @Composable BoxScope.() -> Unit,
)
```

- 迁移 6 处（`HomeScreen`、`IssueListScreen`、`PullRequestListScreen` 各 2）。
- ⚠️ 截图：含无限动画（下拉指示器），**必须用 `captureScreenshotDeterministic`**（AGENTS.md 方法学 3）。

**⑦ `AppAvatar`（收编两份 private）**

```kotlin
@Composable
fun AppAvatar(
    imageUrl: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    fallbackIcon: ImageVector = AppDevOcticons.Person,
)
```

- 合并 `ReposScreen.kt:560` 与 `ProfileScreen.kt:725`；`contentDescription` 由调用方传入（用户名），不允许组件猜。

**⑧ `AppAvatarRow`（新建）**

```kotlin
@Composable
fun AppAvatarRow(
    avatars: List<AppAvatarModel>,
    modifier: Modifier = Modifier,
    avatarSize: Dp = 24.dp,
    overlap: Dp = 8.dp,
    maxVisible: Int = 5,
)
```

- 0 当前使用；先建契约与画廊帧，等真实调用点出现再迁。

**⑨ `AppTabRow`（收编 5 份 private）**

```kotlin
@Composable
fun AppTabRow(
    tabs: List<AppTabItem>,
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = TabRowDefaults.primaryContainerColor,
    contentColor: Color = TabRowDefaults.primaryContentColor,
    indicator: @Composable TabIndicatorScope.() -> Unit = { TabRowDefaults.PrimaryIndicator(...) },
)
```

- 5 份私有：`HomeScreen.kt:660`、`PullRequestDetailScreen.kt:678`、`SearchScreen.kt:324`、`ProfileScreen.kt:404`、`MarkdownComposer.kt:103`。
- ⚠️ 截图探针：M3 `TabRow` 选中态是 `selected`；M3 `SegmentedButton` 是 `checkable/checked`（radio 语义），两者探针语义不同（AGENTS.md 方法学 5），画廊断言须区分。

**⑩ `AppListItem`（新建）**：薄包装 M3 `ListItem`，透传 `headlineContent` / `supportingContent` / `leadingContent` / `trailingContent` / `colors`；当前仅 2 处，建契约后机会主义迁移。

**⑪ `AppChip` 家族其余 + `AppSectionHeader` / `AppSearchBar` 收编**：

```kotlin
@Composable
fun AppSectionHeader(title: String, modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null)

@Composable
fun AppSearchBar(
    input: String,
    onInputChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
)
```

- `AppSectionHeader` 由 `SettingsScreen.kt:121` 的 internal 提升为 public（需先确认无 feature 私有依赖）。
- `AppSearchBar` 由 `feature/search/SearchTopBar.kt:38` 提升；提升前评估 Konsist 分层（feature 组件进 core 需去 feature 依赖）。

### 3.2 已存在组件（参考，不动或仅补逃生舱）

| 组件 | 位置 | 本波动作 |
|---|---|---|
| `AppTopBar` | `core/ui/AppTopBar.kt:55` | 不动；已含 `sectionBar` 逃生舱 + 玻璃开关 |
| `AppNavigationBar` | `core/ui/AppBottomBar.kt:40` | 不动 |
| `AppStateChip` | `AppStateChip.kt:135,157` | 不动；已是双入口 + `stateDescription` 语义 |
| `AppEmptyState` / `AppErrorState` / `AppLoadingState` / `AppCenteredLoadingState` | `AppStateViews.kt` | 仅清理 §1.7 冲突标记（独立票） |
| `AppSnackbar` | `core/ui/AppSnackbarHost.kt:42` | 不动；迁 2 处裸 `SnackbarHost` 到它 |

### 3.3 收尾组件（`AppSegmentedButton`，UI-2 配套）

```kotlin
@Composable
fun AppSegmentedButton(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    shape: Shape = SegmentedButtonDefaults.itemShape(index = 0, count = 1),
    selectedContainerColor: Color = MaterialTheme.colorScheme.secondaryContainer,
    activeContentColor: Color = MaterialTheme.colorScheme.onSecondaryContainer,
)
```

- 现状：`SegmentedButton(` 2 处（`NotificationsPanel.kt:673`、`PullRequestDetailScreen`）；UI-2 一并收口。

---

## §4 迁移批次表

> 批次 = 交付波；**批次内部按 feature 分 PR**（决策 5），每个 feature PR 跑该 feature 的 `verifyRoborazziDebug`。

### Batch 1：高频率组件（Card / Chip 家族 / Dialog / Snackbar）

| 组件 | 迁移目标 | 触及 feature（处数） | 预估 PR |
|---|---|---|---|
| `AppCard` | 19 | repo(10)、search(4)、home(2)、pullrequest(1)、notifications(1)、issue(1) | 1 实现 + 6 迁移 |
| `AppFilterChip`/`AppChip`（UI-2） | 16 | pullrequest(6)、issue(4)、settings(3)、repo(2)、notifications(1) | 1 实现 + 5 迁移 |
| `AppDialog` | 14 | repo(6)、pullrequest(4)、issue(3)、settings(1) | 1 实现 + 4 迁移 |
| `AppSnackbar`（已存在，仅迁移） | 2 | app(1)、repo(1) | 1 (app+repo 合并) |
| **小计** | **51** | 8 个 feature/模块 | **≈19 PR**（可合并到 12-14） |

### Batch 2：三个状态视图去重

| 目标 | 触及 feature（审计的 17 份私有） | 预估 PR |
|---|---|---|
| `AppEmptyState` / `AppErrorState` / `AppLoadingState` 收编 | home、issue、profile、pullrequest、search | 5-8（按 feature） |
| 开工会先复算真实私有实现数（§1.3 说明） | | |

### Batch 3：间距 scale + 迁移收口

| 目标 | 内容 | 预估 PR |
|---|---|---|
| `AppDimens.spacing` 定义 | xs=4 / s=8 / m=12 / l=16 / xl=24 / xxl=32 + 旧常量别名 | 1 |
| 门禁上线 | §5 的禁新增裸控件脚本 + 基准文件 | 1 |
| 机会主义迁移 | 各 feature 顺手改（不设配额） | N（不封顶） |
| **全量扫荡** | ~594 处 `.dp` | **独立票，不在本波** |

**波级总计预估**：≈27-35 张 PR（不含 594 处全量扫荡票）。app 模块是共享冲突点，迁移 PR 尽量按 feature 并行、app 侧最后串行。

---

## §5 门禁设计：禁止新增裸控件

### 5.1 规则

对 §1.2 的 6 个模式（词边界），**每个模式的实测计数不得高于基准**；只允许持平或下降。基准写死在版本库，删除/放宽必须改基准文件并在 PR 说明理由。

### 5.2 方案二选一

**方案 A（推荐）：`.github/scripts/forbid-bare-controls.sh` + 基准文件**

- 仓库已有同款 idiom（`.github/scripts/verify-screenshots.sh`），零新基建。
- 逻辑：`grep -rEn "\b(Card|Scaffold|FilterChip|AlertDialog|ModalBottomSheet|SnackbarHost)\("` → 排除 `*/build/`、`*/src/test/`、`*Test.kt`、白名单目录 → 计数 → 与 `.github/bare-controls-baseline.txt` 逐项比较 → 任一超基准即 `exit 1`。
- **白名单**：`core/designsystem/`（包装实现）、`core/ui/`（`AppSnackbarHost` 等既有包装）、`prototype/`（throwaway，不进 CI）。
- 优点：便宜、可读、与现有脚本一致。缺点：grep 无法区分「定义」与「调用」，靠白名单兜底。

**方案 B：detekt 自定义规则（AST）**

- 优点：AST 精确区分调用点，不受注释/字符串干扰。缺点：要写 detekt-api 代码 + 注册 + 维护，成本显著高于本波收益。

**推荐 A**，并预设升级路径：若出现真实误报且白名单无法覆盖，再切 B。

### 5.3 红 → 绿双向验证（强制，不可省）

1. 在 main 跑脚本：应 **通过**（计数 = 基准）。
2. 临时加一行裸 `Card(`（或在既有文件把 `AppCard` 换回 `Card`）：脚本应 **`exit 1`** 且打印超基准项。
3. 撤销临时改动：脚本应恢复 **通过**。
4. 把 1-3 的终端输出贴进门禁 PR。**没有红证明的门禁一律视为未完成**（AGENTS.md 方法学 2）。

### 5.4 接入

- 加进 `Quality Gate` 必需检查（与 `spotlessCheck` / `detekt` / `konsistCheck` 同 job 或独立 step）。
- 基准初值 = 词边界实测：`Card=19, Scaffold=20, FilterChip=16, AlertDialog=14, ModalBottomSheet=6, SnackbarHost=2`（排除白名单后）。

---

## §6 验收 DoD

### 6.1 组件级（每个新建/收编组件）

- [ ] 实现落地 `core:designsystem`（收编项去除 feature 私有依赖，Konsist 分层绿）
- [ ] 画廊帧：明/暗 × 关键状态（§7）
- [ ] **≥1 处真实迁移**（不是只建组件不用）
- [ ] 语义：可点击/选中态可播报；纯装饰图标 `contentDescription = null`
- [ ] i18n：零硬编码文案（en + zh-rCN 成对）
- [ ] RTL：`start/end`，无 `left/right`
- [ ] 像素等价：同 props 截图与迁移前逐像素一致（纯重构红线）

### 6.2 批次级

- [ ] 该批 feature 内目标裸控件计数 **清零**（`AppSnackbar` 批为降到白名单内）
- [ ] 该批 `verifyRoborazziDebug` 全绿，且**基线未变**（除非走 §6.4 视觉变更审批）
- [ ] 给用户的**前后对比截图**（迁移批只需证明「无变化」）

### 6.3 波级

- [ ] 词边界口径的 6 类裸控件总数 → **0**（白名单外）；基准文件相应下调
- [ ] 门禁生效（红→绿验证已归档）
- [ ] 全量 `.dp` 扫荡票已开（不要求本波完成）

### 6.4 视觉变更例外

任何**非零**视觉变化（含 UI-2 若实测与现状不等价）：须独立票 + 用户批准的 before/after 截图 + 基线**显式重录**（CI canonical，AGENTS.md 方法学 4）。像素等价红线默认覆盖本波其余全部工作。

---

## §7 画廊屏规格（debug-only）

### 7.1 形态

- 位置：`core/designsystem/src/debug/kotlin/.../gallery/DesignSystemGallery.kt`（debug source set，不进 release 产物）。
- 入口：debug 变体下的一个可滚动屏，分节渲染全部设计系统组件；不接导航（截图测试直接组合 `DesignSystemGallery()`）。
- 主题：同一组合在 `AppTheme(darkTheme = false/true)` 下各渲一次，验证动态色/扩展色适配。

### 7.2 矩阵

| 维度 | 取值 |
|---|---|
| 主题 | Light / Dark |
| 组件组 | 容器(Card/Dialog/BottomSheet) · 选择(FilterChip/SegmentedButton/TabRow) · 状态(Empty/Error/Loading/StateChip) · 反馈(Snackbar) · 头像(Avatar/AvatarRow) · 搜索(SearchBar) |
| 状态 | 默认 / 禁用 / 选中（选择类）/ 带 action（消息类）/ 长文本截断 |

### 7.3 帧数估算

- 预计 **10-14 个组件组 × 2 主题 ≈ 20-28 帧**（首版取 24 帧）。
- 截图用 `captureScreenshotDeterministic`（含 `LoadingIndicator` 无限动画的组必须用，AGENTS.md 方法学 3）；线程离线 ImageLoader 已在 `core:testing` 提供。
- 基线由 CI canonical 录制（`record-screenshots.yml`）；`core:designsystem` 已在 verify 名单（`.github/workflows/ci.yml:215`）。

---

## §8 风险与回滚

| 风险 | 触发条件 | 缓解 |
|---|---|---|
| 截图基线漂移 | 迁移批改了渲染 | 像素等价红线 + 每批先跑 verify（期望不变）；基线任何变动必须走 §6.4 |
| AGP 9 迁移延期 | 依赖未合入 | 本波不启动；计划文档可先合（本 PR 即如此） |
| 门禁误报 | 自定义命名控件/注释命中 | 词边界 + 白名单；红→绿验证暴露误报 |
| 逃生舱滥用 | 调用方全透传底层参数，组件形同虚设 | code review 检查；默认参数覆盖 ≥90% 调用点 |
| alpha18 pin 行为 | `MaterialTheme` / `LoadingIndicator` 门控变化（ADR-0008） | 组件的 M3 依赖集中在 `core:designsystem`，升级时只改一处 |
| `AppStateViews.kt` 冲突标记（§1.7） | 已存在于 main | 独立清理票；不在本波改生产代码 |
| 全量 `.dp` 扫荡失控 | 一次改 594 处 | 明确排除本波，独立票分 feature 推进 |

**回滚**：每个 feature 迁移是独立 PR，revert 单 PR 即可回到裸控件；`AppDimens.spacing` 是纯增量（不改旧常量语义），无回滚成本。

---

## §9 与现有基建的关系

| 基建 | 位置 | 本计划如何对接 |
|---|---|---|
| `AppDimens` | `core/designsystem/.../token/AppDimens.kt` | **扩展**：新增 `spacing`（xs..xxl），保留现有圆角与 `contentPadding`/`minTouchTarget`/`fabContentClearance`；旧名作为别名保留 |
| `AppTypography` | `core/designsystem/.../theme/AppTypography.kt` | 已由 `AppTheme.kt:114` 注入（15 档 + markdown 6 档）；组件一律 `MaterialTheme.typography.*`，不硬写 `sp` |
| `ExtendedColors` | `core/designsystem/.../theme/ExtendedColors.kt` | 状态类组件（StateChip 等）已消费；新组件的语义色一律走 `MaterialTheme.colorScheme.*` / `ExtendedColors`，零硬编码 |
| `AppShapes` | `core/designsystem/.../theme/AppShapes.kt` | 组件圆角走 `MaterialTheme.shapes.*`（由 `AppDimens × cornerScale` 派生），不硬写圆角 |
| `AppMotion` / `AppMotionScheme` | `core/designsystem/.../token/` | 动效时长走 `AppMotion.scaledDuration`（`AppSnackbarHost` 已是范例）；组件不得硬编码 ms |
| Konsist 分层 | `app/src/test/kotlin/.../konsist/ArchitectureTest.kt` | 收编组件进 `core:designsystem` 前必须去 feature 依赖；feature→feature 仍禁 |
| material3 pin | `docs/adr/0008-material3-alpha18-pin.md` | 组件对 M3 Expressive API（`LoadingIndicator` 等）的依赖集中在 `core:designsystem`，升级/撤 pin 时收敛面 |
| 截图基建 | `core:testing`（`captureScreenshotDeterministic`）、`.github/workflows/ci.yml`、`record-screenshots.yml` | 画廊帧走同一套确定性捕获；基线只由 CI 录制 |

---

## 附录 A：实测命令（可复现）

```bash
# 词边界裸控件计数（迁移目标口径），在仓库根执行
for p in 'Card\(' 'Scaffold\(' 'FilterChip\(' 'AlertDialog\(' 'ModalBottomSheet\(' 'SnackbarHost\('; do
  grep -rEn --include=*.kt "\b$p" . | grep -v '/build/' | grep -v '/test/' | grep -v 'Test.kt' | wc -l
done

# 裸 .dp（feature 主源）
grep -rn --include=*.kt '\.dp\b' feature/ | grep -v '/build/' | grep -v '/test/' | wc -l

# AppDimens 引用
grep -rn 'AppDimens\.' --include=*.kt feature/ core/ app/ | grep -v '/build/' | grep -v '/test/' | wc -l
```

## 附录 B：与审计的差异清单（供后续对账）

| 项 | 本计划（词边界实测） | 审计 | 差异原因 |
|---|---|---|---|
| `Card(` | 19 | §3.2=44 / §5.5=19 | 子串口径把 `RepoListCard(` 等计入 |
| `SnackbarHost(` | 2 | 10 | 子串把 `SnackbarHostState(` 计入 |
| `Scaffold(` | 21 | §3.2=20 / §5.5=24 | 注释/KDoc 与文件数口径 |
| `AlertDialog(` | 14 | 15 | 1 处注释/定义归类 |
| 裸 `.dp`（feature） | 594 | 639 | 注释中 `.dp` 与排除规则 |
| 状态视图私有重复 | 待复算（宽松正则 34） | 17 | 命名范围（含 `Placeholder`）与 internal 包装 |
| 波级基准 | **77** | 111 | 口径统一为词边界后的真实迁移面 |
