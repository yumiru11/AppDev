# ADR-0008：material3 显式 pin 到 1.5.0-alpha18 —— 用局部技术债换取 M3 Expressive

- **状态**：已接受（2026-09-12）
- **关联**：ui-design.md §4.5 / §4.6 / §6.5、docs/research/md3-expressive-2026-09-11.md、`gradle/libs.versions.toml`
- **决策者**：用户（明确要求「必须符合 material design 3 expressive 设计语言」）

## 背景

用户要求 UI 符合 **M3 Expressive** 设计语言。M3E 的价值分两块：**动效方案**（`MotionScheme`
弹簧物理体系）与 **Expressive 组件**（`LoadingIndicator` / wavy 进度条 / `SplitButtonLayout` /
`ButtonGroup` / `Medium·LargeFlexibleTopAppBar` 等）。

调研（`docs/research/md3-expressive-2026-09-11.md`，字节码级实测）确认这两块**只存在于
material3 1.5.0 线**：

| material3 版本 | `minCompileSdk` | `minAGP` | 本项目（compileSdk 36 / AGP 8.7.3） |
|---|---|---|---|
| 1.4.0（BOM 当前 pin） | 35 | 8.6.0 | ✅ 可装，但 `MotionScheme.standard()/expressive()` 在 **Kotlin 层是 `internal`** → 拿不到 |
| **1.5.0-alpha18** | **35** | **8.6.0** | ✅ **可装** ← 本 ADR 采纳 |
| 1.5.0-alpha19 ~ alpha28 | **37** | **9.1.0** | ❌ 硬冲突 |

即：**Expressive 组件与「升级 AGP 9 + compileSdk 37」这件事是绑定的**，唯一不改工具链的
窗口就是 alpha01–alpha18，边界精确落在 alpha18 → alpha19 之间。

## 决策

1. **显式 pin `material3 = 1.5.0-alpha18`**（直接依赖的显式版本在冲突解析中胜过 BOM 的
   constraint；实测 `dependencyInsight`：BOM requested 1.4.0 → resolved 1.5.0-alpha18）。
2. **不改 `compose-bom`**。BOM 覆盖 Compose 全家（ui/foundation/runtime/…），改 BOM 会把
   整条 Compose 线一起拖进 alpha，改动面与风险远大于只 pin 单个 artifact。版本目录里为该
   pin 单开一个 `compose-material3` version key 并写明撤除条件。
3. **alpha 期的门控（`@ExperimentalMaterial3ExpressiveApi`）一律用局部 `@OptIn`**，
   **禁止**全局 `-opt-in` —— 全局开关会让全仓失去门控保护。实测 alpha18 的
   `MotionScheme` 接口/工厂、`LoadingIndicator`、wavy 系列**恰好都无门控**，故当前接入点
   不需要 `@OptIn`；`SplitButton` / `ButtonGroup` / Flexible 顶栏仍是门控的。
4. **动效方案直接委托官方实现**（`AppMotionScheme` → `MotionScheme.standard()/expressive()`），
   不在项目内复刻 spring 常量。

## 后果

### 正面

- 拿到官方 Expressive 弹簧物理（spatial 回弹 / effects 零回弹），且随官方数值修正自动跟进
- 整页首载统一为 `LoadingIndicator` 形变加载，搜索等待条为 wavy，全应用视觉一致
- **零工具链变更**：compileSdk 36 / AGP 8.7.3 / KSP 2.3.7 全部不动，全门禁可正常跑
- 新增一个 `androidx.graphics:graphics-shapes` 传递依赖（`RoundedPolygon` 供形变用）

### 代价与风险

- **R1（高）alpha 期 API 漂移**：`LoadingIndicator` 的门控在 **alpha18↔alpha19 之间翻转**
  （官方 release notes 有 "Revert `MaterialShapes` and `LoadingIndicator` promotions to
  stable"）。冻结在 a18 = 拿不到后续 bug 修复与 stable 化收益，且未来升级时这些调用点
  大概率要改写。
- **R2（已实测 2 例改名/改名级差异）**：
  ① `FlexibleTopAppBar` 这个类名在 1.5 线**根本不存在**，正确名是
  `MediumFlexibleTopAppBar` / `LargeFlexibleTopAppBar`；
  ② `SplitButtonDefaults.leadingButtonShape`（现行官方示例里的写法）在 **alpha18 不存在**，
  该版是 `leadingButtonShapes(CornerSize)` + `SplitButtonShapes`。
  → **教训：凡涉及 1.5.0-alpha 线的 API，一律以 AAR 字节码实测为准，
  `developer.android.com` 的 "Added in X" 标签在该线上不可信。**
- **R3（中）字节码可见 ≠ Kotlin 可见**：`ExpressiveMotionTokens` 的 getter 在字节码层是
  public 且**无 `$material3` 名字修饰**，看起来完全可用，但 Kotlin 元数据层是 `internal`
  ——测试里符号引用直接编译失败（`Cannot access 'object ExpressiveMotionTokens : Any':
  it is internal in file`）。故 `AppMotionSchemeTest` 改为断言具体数值并注明来源。

### 撤除条件（**何时该撤掉这个 pin**）

满足**任一**即可撤除，撤除时删除 `libs.versions.toml` 的 `compose-material3` version key
与 `compose-material3` 上的 `version.ref`（回到纯 BOM 托管）：

1. **material3 1.5.0 stable 发布且回归某个 `compose-bom`** → 删 pin，改由 BOM 托管；
2. **工具链升到 AGP 9 + compileSdk 37**（届时 alpha19+ 也能装）→ 直接跟到当时的
   stable / BOM，不必再停在 a18。

> 撤除前置动作：重跑全门禁（含 `coverageVerify` / `diffCoverageCheck` / `verifyRoborazziDebug`），
> 并**重录截图基线**（Record screenshots (CI canonical) workflow）。撤除前先确认
> `LoadingIndicator` / wavy 等调用点在新版本是否已改名或重新门控。

### 已知遗留（本票未做，需单独决策）

- `IssueStateUi.kt` / `PullRequestStateUi.kt` 的整页加载态**未**换成 `LoadingIndicator`。
  原因：这两个文件不在 `build.gradle.kts` 的 `uiSourceExcludes` 中，实测在覆盖率报告里
  为 **0/19 已覆盖行**；`diffCoverageCheck` 是**比例门禁**，这两处会成为该票唯一纳入统计的
  文件 → 必然 0% 失败。
  > ⚠️ 2026-09-13 勘误（#261）：原文此处写「0/24 已覆盖行（Robolectric 加载的 Composable
  > 不产 JaCoCo 数据，见 #181）」——该工具链结论已被 #261 证伪（真实根因是 JaCoCo agent
  > 默认 `inclnolocationclasses=false` 跳过无 CodeSource 的沙箱类，已修复，见
  > `configureRobolectricCoverage`）。修复后复测这两处仍为 0/19，原因是**没有单测渲染
  > 这两个状态 UI**，与工具链无关。原决策（不换 `LoadingIndicator`、不放宽排除名单）不受影响。
  **不为此放宽门禁排除名单**（那正是 #210 刚修掉的「刷绿」行为）。
  后续二选一：① 给 `AppLoadingState` 增加「垂直居中」形态并把 4 个调用点（均在
  `*Screen.kt`，已被排除）切过去；② 单独立一张门禁票，把 `*StateUi*` 以具体后缀形式加入
  `uiSourceExcludes` 并写明理由（先例：`*TabContent*` / `*TimelineItems*`）。
- `SplitButtonLayout` 未接入 MergeBox、Flexible 顶栏未接入——理由见 ui-design.md §4.6 / §6.5。
