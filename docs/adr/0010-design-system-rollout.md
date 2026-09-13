# ADR-0010：设计系统落地（组件 / 间距 / 选中态）以像素等价为前提分批迁移

- **状态**：已接受（2026-09-12）
- **关联**：`plan.md` §5.4 / §5.5、`docs/agents/spec-audit-2026-09-11.md` §4.4、`docs/agents/remaining-backlog-2026-09-12.md` §3.2（SPEC-1 / SPEC-2 / UI-2）、`docs/design-system/implementation-plan.md`（执行计划）、ADR-0008（material3 alpha18 pin）
- **决策者**：用户（范围、分批、像素等价红线、门禁、选中态 token 均已拍板）
- **前置依赖**：AGP 9 迁移合入后开工；本 ADR 与关联计划是 AGP 9 之前唯一允许推进的工作

## 背景

2026-09-11 全量审计把设计系统列为重灾区。2026-09-12 复核（词边界口径，见计划 §1）确认：

- `core:designsystem` 只落地了少量组件；`plan.md §5.5` 的 21 个通用组件里，**8 项需新建、5 项需从 feature 私有实现收编**（例：裸 `Card(` 19 处、`FilterChip(` 16 处、`AlertDialog(` 14 处、`Scaffold(` 20 处、`ModalBottomSheet(` 6 处、`SnackbarHost(` 2 处）。
- 三个状态视图（`AppEmptyState` / `AppErrorState` / `AppLoadingState`）**已存在且可复用**，但审计记录约 17 份 feature 私有重复实现，组件「有而不用」。
- `AppDimens` **没有 spacing scale**，裸 `.dp` 字面量在 feature 主源约 594 处，令牌化率约 10%。
- FilterChip / SegmentedButton 选中态未显式指定容器色，用户分不清当前筛选/视图（UI-2）。

同时，AGP 9 迁移正在进行，且项目采用 CI canonical 截图基线（本机录制无效），两条大迁移并行会让基线与构建脚本同时漂移、无法归因。

## 决策

采用**分批、像素等价、门禁锁回流**的落地方式，要点：

1. **范围三合一**：组件 / 间距 / 选中态，分 3 批交付（高频率组件 → 状态视图去重 → 间距 scale + 收口）。
2. **与 AGP 9 串行**：AGP 9 合入后才开始实现。
3. **像素等价红线**：同 props 下新旧渲染逐像素一致（纯重构）；任何视觉变化须独立票 + 用户批准；迁移期截图基线不得变更。
4. **API 风格**：厚包装 + 逃生舱；默认值取现状最常用值以保证像素等价。
5. **迁移策略**：按 feature 分批（一批一 PR + 该 feature 截图 verify）；新增 CI 门禁禁新增裸控件（存量只减不增）。
6. **UI-2 选中态**：显式 `selectedContainerColor = secondaryContainer`，收口到 `AppFilterChip` / `AppSegmentedButton`；合并前给用户看前后对比截图。
7. **间距 scale**：`AppDimens.spacing` = 4/8/12/16/24/32（xs/s/m/l/xl/xxl）+ 旧常量别名；本波只做定义 + 新代码强制 + 机会主义迁移，约 594 处全量扫荡独立成票。
8. **markdown 阅读密度排除**：用户真机实测的段间距/行间距/左右/缩进属于阅读排版，与 UI 结构间距正交，另开未来票。
9. **只提升三个状态视图**进 `core:designsystem`；其余私有组件遵循三法则（出现 3 次才提升）。
10. **验证设施**：debug-only 设计系统画廊屏（组件 × 明暗 × 关键状态）+ Roborazzi 基线，接入 CI verify。

## 后果

### 基线变更政策（baseline churn policy）

- **迁移批默认零基线变更**。每个 feature 迁移 PR 跑 `verifyRoborazziDebug`，期望**全绿且无文件差异**；一旦出现帧差异即视为像素等价红线被破坏，先修组件默认值，而非重录基线。
- **唯一例外**是经用户批准的视觉变更（含 UI-2 若实测与现状不等价）：独立票 + before/after 截图 + 基线**显式重录**，且必须由 CI canonical（`record-screenshots.yml`）录制，本机录制禁止。
- 新组件的画廊帧是**新增**基线，不属变更；由 CI canonical 首次录制。

### 与 AGP 9 的串行关系

- 实现严格排在 AGP 9 之后：构建脚本、Gradle/AGP 版本与截图流程在 AGP 9 期间会变，先做设计系统会让基线漂移无法二分定位。
- 本 ADR 与 `implementation-plan.md` 是 AGP 9 之前**唯一**允许推进的设计系统工作（纯文档，零生产代码）。

### 门禁新增

- 新增 CI 门禁：**禁止新增裸控件**（词边界匹配 `Card(` / `Scaffold(` / `FilterChip(` / `AlertDialog(` / `ModalBottomSheet(` / `SnackbarHost(`），每类计数不得超过版本库中钉死的基准，只允许持平或下降。
- 实现方式推荐 `.github/scripts/forbid-bare-controls.sh` + 基准文件（复用仓库既有脚本 idiom）；若误报无法用白名单覆盖，再升级到 detekt AST 规则。
- 门禁必须做**红→绿双向验证**（临时引入一处裸控件必红，撤销必绿），否则不得合并。基准初值取词边界实测（排除 `core:designsystem` / `core/ui` 包装与 `prototype/`）。

### 正面

- 迁移可审、可测、可独立回滚；视觉改版与结构重构解耦，出问题能定位。
- 设计系统组件有可执行的可视证据（画廊基线），不再只靠 KDoc。
- 门禁锁住回流，「改一处主题要动几十处裸控件」的债务不再增长。

### 代价与风险

- 所有 feature 在迁移完成前**同时存在裸控件与 App\* 组件**，过渡期风格不统一（可接受，门禁保证只减不增）。
- 逃生舱可能被滥用导致组件形同虚设，需 code review 把关默认值覆盖率。
- 与 AGP 9 串行会推迟设计系统收益；换取的是可归因的迁移过程。
- `AppStateViews.kt` 现有 Git 冲突标记（KDoc 内，不报编译错）需独立清理票处理，本 ADR 不改生产代码。
