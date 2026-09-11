# Material 3 Expressive 调研与落地建议（2026-09-11）

> 项目基线：material3 **1.4.0**（经 `compose-bom 2026.06.01` 解析）/ Kotlin 2.3.21 / compileSdk 36 / AGP 8.7.3
> 分支：`docs/md3-expressive-research`　｜　调研方式：官方规范（m3.material.io / developer.android.com）+ **对已发布 AAR 做字节码核验**（不依赖记忆）
> 关联：`docs/ui-design.md`（§4 动效 / §5 图标 / §6 毛玻璃 / §7 主题）、`docs/ui-audit-2026-08-21.md` §4、`docs/adr/0004`、`0006`、`0007`

**证据分级**（本文件全程使用）：
- 🟢 **字节码实测** — 从 `dl.google.com` 下载对应版本 AAR，`javap` 读取真实 public/internal 与注解。**这是唯一不会骗人的证据。**
- 🔵 **官方文档** — 带链接的官方页面原文。
- ⚪ **本地文件** — 本仓库代码/配置。

> ⚠️ **方法论警告（本调研最重要的元结论）**：`developer.android.com` 的 API reference 在 **1.5.0-alpha 线**上的 "Added in X" 标签**不可信**。文档称 `MotionScheme.expressive()` 与 `LinearWavyProgressIndicator` 均 "Added in 1.5.0-alpha27"，但字节码实测显示它们在 **1.5.0-alpha01 就已 public**。凡涉及 alpha 线的版本结论，本报告一律以字节码为准，文档仅作旁证。

---

## 1. 结论速览

### 1.1 一句话结论

**M3 Expressive 的「组件」和「动效方案」在 dependency 层面被一道硬墙挡住：material3 ≥ 1.5.0-alpha19 强制要求 `compileSdk 37` + `AGP 9.1.0`，与本项目锁定的 `compileSdk 36` + `AGP 8.7.3` 直接冲突。但 `MotionScheme` 的物理动效体系在 1.4.0 上就能用——因为 `MotionScheme` 是公开接口，项目可以自己实现它。**

### 1.2 最该做的 3 件事

| 优先级 | 事项 | 成本 | 依据 |
|---|---|---|---|
| **P0** | **用自实现 `MotionScheme` 在 material3 1.4.0 上直接接入 Expressive 动效物理**（§4）。零依赖变更、零工具链变更 | ~1 票（约 150 行含单测） | 🟢 1.4.0 的 `MaterialTheme(colorScheme, motionScheme, shapes, typography, …)` 重载与 `MaterialExpressiveTheme` **均 public 且无 `@ExperimentalMaterial3ExpressiveApi` 门控**；`MotionScheme` 接口 6 个方法全 public 无门控；官方 spring 数值已公布 |
| **P1** | **不升级 material3，先吃掉与 Expressive 无关的 UI 债**（提案 #10/#11/#12 等，§5）。这些用 1.4.0 现成 API 即可，且与依赖升级解耦 | 3 票 | ⚪ `dropShadow`/`innerShadow`(Foundation 1.9+)、`Brush` 渐变、`SizeTransform` 在 1.11.4 上全部可用 |
| **P2** | **立一张「工具链升级可行性」调研票**（compileSdk 36→37 / AGP 8.7.3→9.1.0 / KSP / Roborazzi / Lint 全链路）。这是**所有 Expressive 组件的唯一前置**，且升级后 1.5.0 stable 也照样需要它 | 1 票（纯调研，不改码） | 🟢 1.5.0-alpha19~alpha28 的 AAR metadata 全部为 `minCompileSdk=37` / `minAndroidGradlePluginVersion=9.1.0` |

### 1.3 必须的版本升级结论

| 问题 | 结论 |
|---|---|
| 现在是什么版本？ | **material3 1.4.0**（BOM 2026.06.01 固定；Gradle 缓存中 `material3-android/1.4.0` 是唯一版本，本地实证） |
| 想用 Expressive 组件要升到哪？ | **1.5.0-alpha18**（能用且能装的最高版本）或更高 alpha。1.5.0 尚未 stable——最新 stable 仍是 **1.4.0** |
| 升得动吗？ | **alpha01–alpha18 升得动**（`minCompileSdk=35` / `AGP 8.6.0`）；**alpha19 起升不动**（`minCompileSdk=37` / `AGP 9.1.0`） |
| 会撞 Kotlin 2.3.21 / compileSdk 36 / AGP 8.7.3 吗？ | 撞 **compileSdk 与 AGP**；**不撞 Kotlin**（alpha 只要求 `kotlin-stdlib 2.1.20`，本项目 2.3.21 更高） |
| 1.5.0 stable 出来就能用吗？ | **不能**。alpha19 已经要求 compileSdk 37 + AGP 9.1.0，stable 只会更严。**Expressive 组件与「AGP 9 + compileSdk 37」是绑定的** |
| 有没有零升级的 Expressive 收益？ | **有，且很实在**：自实现 `MotionScheme`（见 §4），在 1.4.0 上拿到 Expressive 弹簧物理 |

### 1.4 最大风险

**「pin 一个冻结的 alpha」与「推工具链大版本」两条路都有实质代价，且没有第三条路。**

1. **pin 1.5.0-alpha18 的风险（高）**：该版本处于 M3E API 剧烈变动期——字节码实测显示 `LoadingIndicator` 的门控在 alpha18 是「无门控」、alpha19 又变回 `@ExperimentalMaterial3ExpressiveApi`（**一个版本内翻转**）。冻结在 a18 意味着：拿不到后续 bug 修复与 stable 化收益，且未来升级时这些 API 的签名大概率要重写。**不建议作为长期方案，只建议作为「过渡验证」或明确记录为技术债。**
2. **推 AGP 9.1.0 + compileSdk 37 的风险（高但可控）**：AGP 8.7.3 → 9.1.0 跨两个大版本，影响面覆盖本项目的**全部质量门禁**（spotless / detekt / konsist / lint / 单测 / JaCoCo `coverageVerify` / Roborazzi 截图基准 / Haze 1.6.10 的 activity 传递依赖）。这是一次「验证链重建」级别的改动，不能与功能票混做。
3. **反向风险（低但真实）**：什么都不做 → 项目继续以 42 处裸 `CircularProgressIndicator`（17 个文件）迎客，动效体系「建而不用」的既有短板延续。

> **附带利好**：`gradle/libs.versions.toml` 注释指出 KSP 2.3.8+ 需要 AGP 9 才有的 `addKspConfigurations(boolean)`，因此被迫停在 KSP 2.3.7。**升级到 AGP 9 会顺带解锁 KSP 2.3.8+**——工具链升级不是纯成本，有一项确定收益。

---

## 2. M3 Expressive 官方规范要点

### 2.1 它是什么 / 不是什么

官方定性（[Start building with Material 3 Expressive](https://m3.material.io/blog/building-with-m3-expressive)，2025-05-13）：

> "Material 3 Expressive is an evolution of the Material 3 design system."
> "And to be clear — M3 Expressive isn't a new version of the system. **We're not deprecating M3, and this isn't "M4."**"
> "Extensive user research — **46 studies with more than 18,000 participants**"

四项研究结论原文：

> "1. Expressive designs are preferred by people of all ages.
> 2. Expressive designs consistently score higher on user attributes like playfulness, energy, creativity, and friendliness.
> 3. Users are more likely to switch to products that use M3 Expressive components and techniques.
> 4. Expressive designs are easier to use, with participants spotting key UI elements **up to four times faster** in expressive screens."

> ⚠️ **注意**：Android Developers Blog **没有**独立的 "Material 3 Expressive" 发布说明文章——`android-developers.googleblog.com/2025/05/material-3-expressive.html` 实测 **HTTP 404**。官方发布说明在 m3.material.io；Android 侧只有 [The Android Show: I/O Edition](https://android-developers.googleblog.com/2025/05/the-android-show-io-edition.html) 汇总稿（称其为 "an expansion pack"）与 [Androidify 案例](https://android-developers.googleblog.com/2025/05/androidify-building-delightful-ui-with-compose.html)。

**"baseline" 是官方术语**（[Lists](https://m3.material.io/components/lists/overview)）："Baseline variants are the original M3 component designs. They may not have the latest features introduced in M3 Expressive, like updated motion, shapes, type, and styles."

**stable 化时间线**（[Material Android is Compose-first](https://m3.material.io/blog/material-is-compose-first)，2026-05-19）：

> "Later in 2026, Material Android will release Compose 1.5.0, which will promote M3 experimental APIs to stable, including M3 Expressive."

→ 截至 2026-09-11，**1.5.0 仍未发布 stable**（[release notes 页头](https://developer.android.com/jetpack/androidx/releases/compose-material3)：Latest Update 2026-08-26，Stable = **1.4.0**，Alpha = **1.5.0-alpha27**；Maven metadata 最新已发布为 **1.5.0-alpha28**，时间戳 2026-09-09）。

**与 baseline 的四轴差异**：[blog](https://m3.material.io/blog/building-with-m3-expressive) 提出 14 个组件更新 + 七个 expressive tactic（variety of shapes / rich colors / typography / contain content / fluid motion / component flexibility / hero moments）。

### 2.2 Expressive 组件清单与推荐场景

**官方「十四件套」**（blog 原文）：App bars · Button groups **new** · Common buttons · Extended FAB · FAB menu **new** · FABs · Icon buttons · Loading indicator **new** · Navigation bar · Navigation rail · Progress indicators · Sliders · Split button **new** · Toolbars **new**

| 组件 | 用途 / 何时用 | 何时**不要**用（官方红线） | 官方"取代"声明 |
|---|---|---|---|
| **Loading indicator**（形变加载） | 短等待（**200ms–5s**）；"It should replace most uses of the indeterminate circular progress indicator"；活动指示器是「**七个形状的循环形变序列**」；默认 48dp，范围 24–240dp | "**Don't transition a loading indicator into a progress indicator**"；"never simply decorative" | 取代**大部分**不确定态圆形进度圈 |
| **Wavy progress**（Circular/Linear） | "The wavy shape can make longer processes feel less static and is best used when a more expressive style is appropriate" | "**In very small buttons, use the flat shape** since the wavy shape is not as visible at that size" | 与 flat 并存，非取代 |
| **Flexible top app bar**（Medium/Large） | 增 `subtitle` 槽 + `titleHorizontalAlignment` + 文本换行 + 更矮 | — | "**should replace medium and large app bars, which are no longer recommended**"（`CenterAlignedTopAppBar` **未**被点名弃用，见 §7 未决项） |
| **Split button** | "show an action with a menu of related actions. **This reduces visual complexity by hiding extra options**" | — | ⚠️ 特殊：菜单按钮旋转用 **standard** 而非 expressive scheme，"rotates inwards 180°" |
| **Floating toolbar**（Horizontal/Vertical） | "Floats above the body content… best used for **contextual actions relevant to the body content or the specific page**"；**Compose 上可随滚动折叠成 FAB**；水平 ≥16dp / 垂直 ≥24dp 边距 | 不要用方形 icon button（"square shape conflicts with the fully-rounded shape"）；不与带导航控件的 navigation bar 同屏；"**Vertical toolbars aren't recommended for compact windows**" | — |
| **Button group** | standard / connected 两变体；按压与选中做 **shape morph**；XS–XL 五档 | "Avoid using a connected group when none of the buttons can be toggled"；"**Only use multiple sizes in a group for hero moments**" | "**Connected button groups replace the segmented button**, which is no longer recommended" |
| **FAB menu** | 从 FAB 展开 **2–6** 个相关动作；配 `ToggleFloatingActionButton` | "**Don't open a FAB menu from an extended FAB or any other component**"；与 floating toolbar / nav rail 同屏时不要用 | "It should replace the speed dial and any usage of stacked small FABs" |
| **Docked toolbar**（Compose = `FlexibleBottomAppBar`） | "Spans the full width of the window… **global actions that remain the same across multiple pages**" | 已有 navigation bar 等底部元素时不要用；"**Avoid applying rounded corners to the container**"；最小 48×48dp 点击区 | "The **bottom app bar** is no longer recommended and should be replaced with the **docked toolbar**" |

**一句话区分 floating vs docked**（官方原文）："**Docked toolbar** — Spans the full width of the window… global actions. **Floating toolbar** — Floats above the body content… contextual actions."

### 2.3 MotionScheme：Expressive vs Standard

来源：[Adding Motion Physics with Jetpack Compose](https://m3.material.io/blog/m3-expressive-motion-theming)、[Motion specs](https://m3.material.io/styles/motion/overview/specs)

> "**Expressive** is Material's recommended motion scheme, and should be used for most situations, particularly hero moments and key interactions."
> "**Standard**, with its small amount of bounce, feels more functional and should be used for utilitarian products."
> "Expressive: … **overshoots the final values to add bounce**. Standard: … **eases into the final values**."（两者底层**都是 spring**）

**spatial / effects 分类原则及「为什么」**：

> "**Spatial** spring tokens are used for animations that **move something on screen**, for example the **x and y position, rotation, size, rounded corners**."
> "**Effects** spring tokens are used to animate properties such as **color and opacity**, **where there shouldn't be any overshoot**."

API 层的契约（`MotionScheme` KDoc 原文）：
- `*SpatialSpec()` — "designed to be applied to animations that **may change the shape or bounds** of the component"
- `*EffectsSpec()` — "designed to be applied to animations that **do not change the shape or bounds** of the component. For example, color animation"

**为什么这样分**（三条硬理由）：
1. **物理合理性**：弹簧模拟实体，过冲是实体停止时的真实行为；位移/尺寸/旋转描述实体。
2. **颜色没有空间维度**：过冲的「颜色」就是**错误的颜色**，弹入某个色相没有意义 → 规则是死板的"no overshoot"。
3. **数值上被强制执行**（🟢 见下表）：**两套 scheme 的 effects 数值完全相同**，且 `dampingRatio` 恒为 `1.0`（= `Spring.DampingRatioNoBouncy`，数学上零回弹）。**Expressive 与 Standard 的唯一差别是 spatial 的阻尼/刚度。** 这就是为什么「按元素切换 scheme」是安全的。

**官方 spring 数值**（经 `ExpressiveMotionTokens` / `StandardMotionTokens` 生成源交叉核对）：

| Token | Expressive damping / stiffness | Standard damping / stiffness |
|---|---|---|
| Fast spatial | **0.6f / 800.0f** | **0.9f / 1400.0f** |
| Default spatial | **0.8f / 380.0f** | **0.9f / 700.0f** |
| Slow spatial | **0.8f / 200.0f** | **0.9f / 300.0f** |
| Fast effects | **1.0f / 3800.0f** | **1.0f / 3800.0f** |
| Default effects | **1.0f / 1600.0f** | **1.0f / 1600.0f** |
| Slow effects | **1.0f / 800.0f** | **1.0f / 800.0f** |

> 🟢 **本地交叉验证（已升级为字节码实证，非仅字段名核对）**：从本项目 Gradle 缓存中的 material3 **1.4.0** AAR 反汇编 `androidx.compose.material3.tokens.ExpressiveMotionTokens` 的 `<clinit>`，**12 个常量的实际取值全部读出，与上表逐项吻合**：
>
> ```
> DefaultSpatialDamping = 0.8f   DefaultSpatialStiffness = 380.0f
> DefaultEffectsDamping = fconst_1 (即 1.0f)   DefaultEffectsStiffness = 1600.0f
> FastSpatialDamping    = 0.6f   FastSpatialStiffness    = 800.0f
> FastEffectsDamping    = fconst_1 (即 1.0f)   FastEffectsStiffness    = 3800.0f
> SlowSpatialDamping    = 0.8f   SlowSpatialStiffness    = 200.0f
> SlowEffectsDamping    = fconst_1 (即 1.0f)   SlowEffectsStiffness    = 800.0f
> ```
>
> 三条 `fconst_1` 是**「effects 恒为零回弹」这一设计原则的机器级证据**——它们甚至没有进常量池，是直接内联的 `1.0f`。
> ⚠️ 但该类被 Kotlin metadata 标记为 **`internal`**（字节码 public / Kotlin internal）→ **应用代码不能直接引用**，必须自带常量（见 §4.3）。

**三个 speed 档位的选用原则**："Most motion should use the default speed, while **smaller elements may use fast and larger elements may use slow**."

| Speed | Spatial 例子 | Effects 例子 |
|---|---|---|
| Default | 部分覆盖屏幕（bottom sheet、展开的 nav rail） | nav rail 内容的 opacity |
| Fast | 小组件（switch、button） | switch 手柄的颜色变化 |
| Slow | 全屏动画 | 全屏内容刷新 |

**spring → 曲线 → 时长对照（ms）**（官方 Specs 页；spring 本身**没有时长**，这是给不支持 spring 的平台做近似用的）：

| Spring | 曲线 | 时长 |
|---|---|---|
| Expressive fast spatial | 0.42, 1.67, 0.21, 0.90 | 350ms |
| Expressive default spatial | 0.38, 1.21, 0.22, 1.00 | 500ms |
| Expressive default effects | 0.34, 0.80, 0.34, 1.00 | 200ms |
| Expressive slow effects | 0.34, 0.88, 0.34, 1.00 | 300ms |
| Standard fast spatial | 0.27, 1.06, 0.18, 1.00 | 350ms |
| Standard default spatial | 0.27, 1.06, 0.18, 1.00 | 500ms |
| Standard slow spatial | 0.27, 1.06, 0.18, 1.00 | 750ms |
| Standard fast effects | 0.31, 0.94, 0.34, 1.00 | 150ms |
| Standard default effects | 0.34, 0.80, 0.34, 1.00 | 200ms |
| Standard slow effects | 0.34, 0.88, 0.34, 1.00 | 300ms |

> 「Expressive fast effects」「Expressive slow spatial」两行**官方表里没有** → 其 ms 近似值 **UNVERIFIED**（代码数值分别为 1.0/3800、0.8/200）。

**传统 easing/duration 体系的状态**（对本项目 §4 方案至关重要）：

> "In the expressive update, components and motion now use the **motion physics system**, which uses springs. **Products should migrate to the new system.** The easing and duration system is **still used for transitions** … but is **no longer maintained**."
> "M3 transitions use the legacy easing and duration system. They'll eventually be updated to use the motion physics system."

→ 即：**组件动效迁移到 spring，但导航转场仍归 easing+duration 管**。本项目 `AppMotion` 的 6 组 easing/时长（§4.1）正好落在「转场」语义上，因此**不该整体废弃**（见 §4）。

### 2.4 形状与字体变化

**圆角刻度从 5 档变 10 档**（[Corner radius scale](https://m3.material.io/styles/shape/corner-radius-scale)）：

| 名称 | 值 | 备注 |
|---|---|---|
| None / Extra small / Small / Medium / Large | 0 / 4 / 8 / 12 / 16 dp | 原有 |
| **Large increased** | **20dp** | **Expressive 新增** |
| Extra large | 28dp | 原有 |
| **Extra large increased** | **32dp** | **Expressive 新增** |
| **Extra extra large** | **48dp** | **Expressive 新增** |
| **Full** | 全圆 | 「Previously, this was defined using 50% of the component size」 |

- **内圆角规则**："**Outer radius - padding = inner radius** — For example: **48dp - 14dp = 34dp**"；"Avoid using the same corner radius value for nested objects."
- ⚠️ **官方警告（与本项目直接相关）**："**Be careful not to apply large or full corners to information-dense components, such as cards.**"
- ⚪ **对项目的直接影响**：M3 `Shapes` 数据类只有 `extraSmall/small/medium/large/extraLarge` **5 个槽位**，**新增的 3 档刻度不在 `MaterialTheme.shapes` 里**——本项目的 `AppShapes.resolveCornerTokens()` 同样是 5 槽。要用 20/32/48dp 只能走 `AppDimens` 手工令牌。

**Shape morph**（[Shape morph](https://m3.material.io/styles/shape/shape-morph)）：
> "**Shape morphing uses the expressive motion scheme by default. This can be switched to the standard motion scheme as needed.**"
> "**Shape is versatile, not semantic.** Avoid making shapes literal or assigning a specific function or meaning to a single shape."
> "**Use the shape library for mostly visual elements. Avoid applying unconventional shapes to text-heavy containers.** Shapes should be used **sparingly**."

**字体**（[Type scale & tokens](https://m3.material.io/styles/typography/type-scale-tokens)）：15 baseline + **15 emphasized**（更高字重 + 微调）。
> "**Material components don't use emphasized type styles by default.** To use an emphasized type style, swap the baseline token for the emphasized token."（`md.sys.typescale.display-large` → `md.sys.typescale.emphasized.display-large`）
> 适用："**Badges · Buttons (for primary actions) · Extended FAB · Selected list items · Selected menu items**"
> "**Avoid changing the type size**; this can affect how components render and reflow."
> 字体回退链："1. **Roboto Flex** → 2. **Roboto** → 3. **Noto Sans**"；"Roboto Flex … is **not yet part of the M3 typescale**"

> ⚪ **对项目的直接影响**：**Compose 侧没有 exposed emphasized typescale**（`Typography` 在 1.4.0 无 emphasized 槽）。要用只能手工构造 `TextStyle`。**建议本期不采纳**（见 §5）。

---

## 3. Compose API 可用性与版本矩阵

### 3.1 版本全景（本地实证 + Maven）

| 事实 | 值 | 证据 |
|---|---|---|
| 项目当前 material3 | **1.4.0** | ⚪ `compose-bom = "2026.06.01"`；BOM POM 中 `androidx.compose.material3:material3` = **1.4.0**；Gradle 缓存中 `material3-android/1.4.0` 是**唯一**版本目录 |
| BOM 2026.06.01 其他 pin | foundation / ui / runtime / animation = **1.11.4**；material-icons-core = 1.7.8 | ⚪ 直接从 `compose-bom-2026.06.01.pom` 解析；🟢 **Gradle 缓存交叉确认**：`ui-android` / `ui-graphics-android` / `foundation-android` / `animation-core-android` 的唯一版本目录均为 **1.11.4** |
| material3 最新 **stable** | **1.4.0** | 🔵 [release notes](https://developer.android.com/jetpack/androidx/releases/compose-material3)（Latest Update 2026-08-26：Stable 1.4.0 / Alpha 1.5.0-alpha27） |
| material3 最新 **alpha** | **1.5.0-alpha28** | 🔵 [maven-metadata.xml](https://dl.google.com/android/maven2/androidx/compose/material3/material3/maven-metadata.xml)（`<latest>1.5.0-alpha28</latest>`，时间戳 20260909171005） |
| BOM 映射页最新 BOM | 2026.08.00 | 🔵 [BOM to library version mapping](https://developer.android.com/develop/ui/compose/bom/bom-mapping)（Last updated 2026-09-01） |

### 3.2 API → 引入版本 → 门控 → 稳定状态

🟢 **全部由字节码实测**（下载 AAR → `javap`）。「门控」列 = 该类中 `ExperimentalMaterial3ExpressiveApi` 的引用数，`0` = **无门控（stable 语义）**。

| API | 1.4.0（当前） | 1.5.0-**alpha18** | 1.5.0-alpha28（最新） | 稳定状态 |
|---|---|---|---|---|
| `MotionScheme`（接口，6 个 spec 方法） | ✅ public，**无门控** | ✅ 无门控 | ✅ 无门控 | **1.4.0 即 stable** |
| `MaterialTheme.motionScheme` | ✅ public，无门控 | ✅ | ✅ | **1.4.0 即 stable** |
| `MaterialTheme(…, motionScheme, …)` 重载 | ✅ public，**无门控** | ✅ | ✅ | **1.4.0 即 stable** |
| `MaterialExpressiveTheme(...)` | ✅ public，**无门控** | ✅ | ✅ | **1.4.0 即 stable** |
| `MotionScheme.Companion.standard()` / `expressive()` | ❌ **internal**（`standard$material3()`） | ✅ **public** | ✅ public | 1.5.0 线起可用 |
| `Circular/LinearWavyProgressIndicator` | ❌ 类不存在 | ✅ **无门控** | ✅ 无门控 | alpha18 起无门控 |
| `LoadingIndicator` / `ContainedLoadingIndicator` | ❌ 类不存在 | ✅ 存在，**a18 恰好无门控**（⚠️ a19 起又变回门控） | ✅ 存在，**仍门控**（5 refs） | **仍未 stable** |
| `SplitButtonLayout` + `SplitButtonDefaults` | ❌ | ✅ 门控（2） | ✅ 无门控 | alpha20 起无门控 |
| `ButtonGroup`（12 个类） | ❌ | ✅ 门控（4） | ✅ 无门控 | alpha22 起无门控 |
| `FlexibleTopAppBar` / `LargeFlexibleTopAppBar`（在 `AppBarKt`） | ❌ | ✅ 门控（6） | ✅ 无门控 | alpha25 起无门控 |
| `Horizontal/VerticalFloatingToolbar`（9 个类） | ❌ | ✅ 门控（7） | ✅ 无门控 | alpha22 起无门控 |
| `FloatingActionButtonMenu` | ❌ | ✅ 门控（4） | ✅ 无门控 | alpha19 起无门控 |
| `MaterialShapes`（35 形状） | ❌ | — | ✅ | alpha28 文档标 "Added in 1.5.0-alpha28" |
| `ExperimentalMaterial3ExpressiveApi`（注解类） | ✅ 存在（1.3.0 引入） | ✅ | ✅ | 仅 `LoadingIndicator` 仍在用 |

**门控毕业时间线**（🟢 字节码逐版本扫描，与官方 release notes **完全吻合**，互为印证）：

| 版本 | 事件（字节码实测） | release notes 对应条目 |
|---|---|---|
| alpha01–alpha15 | 全部 expressive 组件门控；`MotionScheme` 接口本身也门控 | — |
| **alpha18** | `LoadingIndicator`、wavy **短暂无门控**；`MotionScheme` 接口去门控 | — |
| alpha19 | **`LoadingIndicator` 重新门控**；FAB Menu 去门控 | "Graduate FAB and FAB Menu APIs from Experimental Expressive" |
| alpha20 | SplitButton 去门控 | "Graduate `SplitButton` APIs to non-experimental" |
| alpha22 | ButtonGroup、FloatingToolbar 去门控 | "Graduate Expressive `FloatingToolbar` APIs to non-experimental" / "Promote `ButtonGroup` APIs to stable" |
| alpha25 | `FlexibleTopAppBar`（`AppBarKt`）去门控 | — |
| alpha28 | **仅剩 `LoadingIndicator` 门控** | — |

> ⚠️ **上表 alpha18 那一行是本次调研最危险的发现**：`LoadingIndicator` 的门控在 **alpha18 与 alpha19 之间翻转**（官方 release notes 有 "Revert `MaterialShapes` and `LoadingIndicator` promotions to stable" 条目）。**任何 pin alpha18 的方案都必须接受「该 API 随时会被重新门控」的事实。**

### 3.3 构建约束兼容性（**决定性**）

🟢 直接读各版本 AAR 内 `META-INF/com/android/build/gradle/aar-metadata.properties`——这是 Gradle 在配置期真正会用来卡你的东西。

| material3 版本 | `minCompileSdk` | `minAndroidGradlePluginVersion` | 本项目(36 / 8.7.3)能否使用 |
|---|---|---|---|
| **1.4.0**（当前 stable） | **35** | **8.6.0** | ✅ |
| 1.5.0-alpha01 ~ **alpha18** | **35** | **8.6.0** | ✅ **可用** |
| 1.5.0-**alpha19** ~ alpha28 | **37** | **9.1.0** | ❌ **被硬墙挡住** |

> **边界精确定位在 alpha18 → alpha19 之间。** 这是「不升工具链还能走多远」的确切答案。

**传递依赖**（🔵 1.5.0-alpha18 的 Gradle module metadata）：

| 依赖 | alpha18 要求 | 本项目现状 | 结论 |
|---|---|---|---|
| `compose.foundation` / `foundation-layout` / `ui` / `ui-text` / `runtime` / `animation-core` / `material-ripple` | `1.11.0-beta02` | BOM 给 **1.11.4** | ✅ 满足（1.11.4 > 1.11.0-beta02），**compose 传递依赖无需改动** |
| `androidx.graphics:graphics-shapes` | **`1.0.1`（新增传递依赖）** | 未显式声明 | ⚠️ 会被自动带入（`RoundedPolygon` 供 LoadingIndicator / shape morph 用） |
| `kotlin-stdlib` | `2.1.20` | Kotlin 2.3.21 | ✅ 更高 |
| `androidx.activity:activity-compose` | `1.8.2` | 1.9.3 | ✅ 更高 |

**结论**：**可以用 `libs.versions.toml` 显式 pin material3 版本来覆盖 BOM，且不会牵连任何其他 compose 库版本**（Gradle 冲突解析取高版本，`1.5.0-alpha18 > 1.4.0` 会胜出）。代价仅为新增一个 `graphics-shapes` 传递依赖。

---

## 4. MotionScheme 与项目 AppMotion / LocalMotionScale 的统一方案

### 4.1 两套体系的性质差异（先认清，再谈统一）

| | 项目现状（`AppMotion`） | M3 `MotionScheme` |
|---|---|---|
| 本质 | **时长 + 贝塞尔曲线**（tween） | **弹簧物理**（damping / stiffness） |
| 参数 | `DURATION_PAGE_ENTER=400ms`、`EmphasizedDecelerate(0.05,0.7,0.1,1)` 等 6 组 | 6 个 `spring(dampingRatio, stiffness)`，**无时长** |
| 可中断/可重定向 | ❌ tween 中断会跳变 | ✅ spring 用当前速度平滑重定向 |
| 缩放语义 | `LocalMotionScale` 乘在**时长**上（`scaledDuration`） | 靠 Compose 内建 `MotionDurationScale` **对整条动画做时间缩放** |
| 官方定位 | 已被 M3E **弃用（"no longer maintained"）**，但**仍管转场** | "Products should migrate to the new system"，管**组件动效** |

**关键洞察（决定方案形态）**：两者**不是替代关系**，因为官方自己就把它们分工了——**转场归 easing/duration，组件动效归 spring**。而项目 `AppMotion` 的 6 组令牌（页面进入/退出、临时面板、列表项、小状态变化、按压反馈）**全部是转场与整体进出语义**，正好是官方保留 easing/duration 的那部分。

### 4.2 推荐方案：**并行分工 + 单一消费入口收口（不改 AppMotion 现有语义）**

不用二选一，采用**三层结构**：

```
Layer 1  AppMotion（保留）           → 负责转场/整体进出（NavHost、BottomSheet、列表项进出）
Layer 2  AppMotionScheme（新增）     → 负责组件内动效（可中断的位移/尺寸/形状/颜色）
Layer 3  LocalMotionScale（保留）    → 统一缩放闸门，同时作用于 Layer 1 与 Layer 2
```

#### 4.2.1 为什么不能「替换」

- `AppMotion` 的 easing 曲线是 `docs/ui-design.md` §4.1 **用户拍板**的，且 NavHost / GlassSurface / CardGroup / AppStateViews 已在消费；
- 官方明确 easing/duration **仍然管转场**，`MotionScheme` 的 6 个 spec **不表达**「页面进入用 400ms emphasized decelerate」这种转场语义；
- 硬替换会同时打破「§4.1 拍板」与「尊重系统减弱动画」两条既有约束（§4.4）。

#### 4.2.2 为什么不能只「包装」

`MotionScheme` 返回 `FiniteAnimationSpec<T>`（spring），`AppMotion` 提供 `Int`（ms）+ `Easing`。**类型上无法互相包装**：spring 没有时长，无法从 `400ms` 推导出 `dampingRatio/stiffness`。任何「把 spring 折算成 ms」的做法都是把物理量退化成近似值，等于放弃 Expressive 的核心收益（可中断 + 设备自适应）。

#### 4.2.3 那 `LocalMotionScale` 怎么统一？

**这是本方案最关键的技术点，也是本次调研最有价值的发现之一：**

> Compose **已经内建**了系统动效缩放的支持，且是**实时响应**的——不需要项目手搓。

🔵 [`MotionDurationScale`](https://developer.android.com/reference/kotlin/androidx/compose/ui/MotionDurationScale)：*"Provides a **duration scale for motion** such as animations. **When the duration `scaleFactor` is 0, the motion will end in the next frame callback.**"*
🔵 [`WindowRecomposer.android.kt`](https://raw.githubusercontent.com/androidx/androidx/androidx-main/compose/ui/ui/src/androidMain/kotlin/androidx/compose/ui/platform/WindowRecomposer.android.kt) 中 `readAnimationScale()` 读 `Settings.Global.ANIMATOR_DURATION_SCALE`，并通过 `getAnimationScaleFlowFor()` + `ContentObserver` **实时观察**，注入 recomposer 的 `CoroutineContext`。

**推论（对 spring 同样成立）**：spring 虽然没有时长，但整条动画被**时间缩放**，所以系统「移除动画」= `scaleFactor 0` → 下一帧结束。**`MotionScheme` 天然尊重系统减弱动画。**

⚪ 而项目当前 `rememberSystemMotionScale()`（`core/designsystem/.../AppMotionScale.kt`）是 **`remember {}` 无 key，会话内只读一次**，注释也承认「运行中改开发者选项需重启进程才生效」。

**于是统一方案如下**：

| 动效类型 | 缩放机制 | 消费入口 |
|---|---|---|
| **转场**（`AppMotion` 6 组时长） | 项目自己的 `AppMotion.scaledDuration()`（用户滑杆 × 系统值取 min） | 既有入口不变 |
| **组件动效**（`MotionScheme` spec） | **Compose 内建 `MotionDurationScale` 自动处理系统侧**；项目只需叠加**用户滑杆** | 新增单一入口（下） |

即：**`LocalMotionScale` 从「乘时长」升级为「同时管两类」**，但两类走的物理通道不同。

### 4.3 落地形态（**1.4.0 即可实施，零依赖升级**）

#### 步骤 1：新增 `AppMotionScheme` 令牌（`core/designsystem/.../token/`）

因为 1.4.0 的 `MotionScheme.expressive()` 是 internal，且 `ExpressiveMotionTokens` 也是 Kotlin-internal（🟢 均已实测），**项目自带官方公布数值**——这反而更符合本项目「版本目录/令牌单一事实来源」的纪律：

```kotlin
// core/designsystem/src/main/kotlin/.../token/AppMotionScheme.kt
// 数值来源：M3 motion specs（m3.material.io/styles/motion/overview/specs）
// 与 androidx ExpressiveMotionTokens 生成源一致；该类为 Kotlin-internal 不可引用，故自带常量。
object AppMotionSpring {
    // Spatial（有回弹）
    const val FAST_SPATIAL_DAMPING = 0.6f;      const val FAST_SPATIAL_STIFFNESS = 800f
    const val DEFAULT_SPATIAL_DAMPING = 0.8f;   const val DEFAULT_SPATIAL_STIFFNESS = 380f
    const val SLOW_SPATIAL_DAMPING = 0.8f;      const val SLOW_SPATIAL_STIFFNESS = 200f
    // Effects（官方规定零回弹：dampingRatio 恒为 1.0）
    const val FAST_EFFECTS_STIFFNESS = 3800f
    const val DEFAULT_EFFECTS_STIFFNESS = 1600f
    const val SLOW_EFFECTS_STIFFNESS = 800f
}

/** Expressive 动效方案。纯函数式、可单测（断言 dampingRatio 与 stiffness）。 */
fun expressiveMotionScheme(
    scale: Float = 1f,
): MotionScheme = object : MotionScheme {
    override fun <T> defaultSpatialSpec(): FiniteAnimationSpec<T> =
        spring(dampingRatio = AppMotionSpring.DEFAULT_SPATIAL_DAMPING,
               stiffness = AppMotionSpring.DEFAULT_SPATIAL_STIFFNESS / scale)
    // … 其余 5 个同理
}
```

> **`scale` 的作用**：`stiffness` 与「完成时间」近似成**平方根反比**（`t ∝ 1/√stiffness`）。要尊重用户滑杆，应对 `stiffness` 除以 `scale²`；**不要**用 `scale` 线性除（那会让 0.5 档变成 4 倍慢）。**此换算为工程近似，非官方规范**，须在实现票中用截图/真机走查验收。若不愿引入近似，可只让用户滑杆作用于转场（Layer 1），组件动效完全交给系统的 `MotionDurationScale` —— **这是更保守也更诚实的选项**。

#### 步骤 2：在主题装配点接入

⚪ 消费点 = `core/designsystem/.../theme/AppTheme.kt`（`LocalMotionScale provides motionScale` 那一处）。1.4.0 已有 public 且无门控的重载：

```kotlin
MaterialTheme(
    colorScheme = colorScheme,
    motionScheme = expressiveMotionScheme(scale = motionScale),
    shapes = shapes,
    typography = typography,
    content = content,
)
```

#### 步骤 3：既有动效消费点迁移（**按语义分类，不批量替换**）

| 现有动效 | 归属 | 动作 |
|---|---|---|
| NavHost enter/exit/popEnter/popExit | 转场 → `AppMotion` | **保持不动**（官方也保留 easing/duration 管转场） |
| `AnimatedVisibility` slide+alpha（列表项进出） | 转场 | 保持不动 |
| Star 图标 `spring(HighBouncy, Medium)`（§4.2 拍板） | 组件动效 | 可迁移到 `fastSpatialSpec()`，但**用户拍板过的回弹强度优先**——建议保持 |
| `Crossfade`（主题切换） | effects | 迁移到 `defaultEffectsSpec<Float>()` |
| `CardGroup` 按压圆角弹性形变 | **spatial**（改变形状） | 迁移到 `fastSpatialSpec()`（🔵 官方："may change the shape or bounds"） |
| 图标 FILL 轴过渡 | effects | 迁移到 `fastEffectsSpec<Float>()` |

**分类判据（一句话，来自官方契约）**：*会改变组件形状或边界的 → spatial；不会的（颜色、透明度）→ effects。*

#### 步骤 4：单测与验证

- **纯函数单测**（走 `core:designsystem` 既有 token 测试先例，如 `AppMotionScaleTest.kt`）：断言 6 个 spec 的 `dampingRatio`/`stiffness` 等于官方值；断言 **所有 effects spec 的 `dampingRatio == 1.0f`**（这条能防住「有人把 effects 错配成 spatial」）。
  🟢 **可断言性已实测**：`androidx.compose.animation.core.SpringSpec` 在 **animation-core 1.11.4** 中公开 `getDampingRatio()` / `getStiffness()`，且 `spring()` 的返回类型正是 `SpringSpec<T>`，因此 `assertEquals(0.8f, (spec as SpringSpec<Float>).dampingRatio)` 直接可行，**无需任何测试后门**。
- **截图验证**：Roborazzi 静态截图**拍不到动效**——只能验证「接入后静态视觉无回归」。动效本身须靠**真机走查**（`FEEDBACK.md` 流程）。⚠️ 遵守 `AGENTS.md` 铁律：`recordRoborazziDebug` 本机极慢，**需先问用户**。
- **门禁**：`spotlessCheck + detekt + konsistCheck + :app:testDebugUnitTest + coverageVerify`（新增顶层文件若不落 JaCoCo 排除模式，**必须配单测**）。

### 4.4 与「尊重系统减弱动画」的关系（**重要澄清**）

- 官方 M3 **没有** motion + accessibility 的专门规范页（`m3.material.io/styles/motion/accessibility` 等实测只有站点框架，`/foundations/accessible-design/accessibility-basics` 404）。**不要对外声称「Material 要求提供 reduced-motion 变体」**——无此原文。
- 真正的机制是**平台侧**：`MotionDurationScale` + `ANIMATOR_DURATION_SCALE`（[Settings.Global](https://developer.android.com/reference/android/provider/Settings.Global#ANIMATOR_DURATION_SCALE)：*"Setting to 0.0f will cause animations to end immediately."*）。
- ⚪ 因此项目现有 `LocalMotionScale`（滑杆 × 系统值取 min）**依然必要**——它承担的是**用户滑杆**这一半；系统那一半已由 Compose 兜底。**唯一要避免的是绕过它**：手写 `withFrameNanos` 循环、不可中断的装饰性长动画。
- **建议（低成本改进）**：把 `rememberSystemMotionScale()` 的 `remember` 换成跟随 `MotionDurationScale` 的实时读取，可消除「改开发者选项要重启进程」的已知短板。但这属于**独立小票**，不必与 Expressive 绑定。

---

## 5. ui-audit §4 十四条提案逐条判定

> ⚠️ **前提修正（本节最重要的发现）**：`docs/ui-audit-2026-08-21.md` 是 **2026-08-21 静态快照**，而现在是 **2026-09-11**。`AGENTS.md` 记录 ui-audit 8 票（#83–#90）**已全部合入 main**。**逐条核对现有代码后发现：14 条提案中有 6 条已完全落地、3 条部分落地、5 条仍未做。** 下表「当前状态」列均为本次实测，不是照抄审计文档。

| 提案# | 内容 | **当前代码状态（2026-09-11 实测）** | 是否建议采纳 | 依据（官方规范/成本/风险） | 前置依赖 | 建议票规模 |
|---|---|---|---|---|---|---|
| **1** | Haze 重做毛玻璃 | ✅ **已落地** — `GlassSurface.kt` 已用 `hazeEffect`/`hazeSource`；ui-design §6.4 已回写 | —（已完成） | — | — | 无 |
| **2** | CardGroup 式设置分块 | ✅ **已落地** — `CardGroup.kt` 存在，settings 三个 Section 已消费；`CardGroup` 已消费 `LocalMotionScale` | —（已完成） | — | — | 无 |
| **3** | `LoadingIndicator` 形变加载 / 圆形·线性 wavy | ❌ **未做** — 全仓 **42 处** `ProgressIndicator`，分布 **17 个文件**（`AppStateViews.kt` 为统一入口） | **建议采纳，但分层做**：整页首载走 `LoadingIndicator`（官方："should replace most uses of the indeterminate circular progress indicator"）；按钮内 pending 走小尺寸 **flat**（官方明令 wavy 在极小尺寸不可见）；wavy 只用于 T16 Diff / T22 上传等**长过程** | 🔵 官方 wait-time 表：<200ms 不出指示器、200ms–5s 用 loading indicator、>5s 用 progress indicator。⚠️ **`LoadingIndicator` 是当前唯一仍被 `@ExperimentalMaterial3ExpressiveApi` 门控的 Expressive 组件**，且需 `graphics-shapes` 传递依赖 | **依赖 material3 升级**（§6 波次 C） | 大（3–4 票：令牌+统一入口 / 各 feature 替换 / 截图基准全量重录） |
| **4** | `SegmentedButton` 替换全部单选组 | ⚠️ **部分** — `NotificationsPanel.kt`、`PullRequestDiffView.kt` 已用；**`IssueDetailScreen.kt`（Milestone 单选卡）、`FileEditScreen.kt`（分支选择）、`AppearanceSettingsSection.kt` 仍是 `RadioButton`/自绘卡片** | **部分采纳**：建议**只收口真·二选一/三选一的"分段"语义**；Issue milestone 是**带副标题的选择卡**（≠ 分段控件），**不采纳**；`AppearanceSettingsSection` 的色盘卡片是视觉选择器，**不采纳** | 🔵 **重要：官方已宣告 `SegmentedButton` 被 Button group 取代** —"Connected button groups replace the segmented button, which is no longer recommended"（m3 tooltip：「deprecated in the expressive update」）。**因此不宜扩大 `SegmentedButton` 的使用面**，否则是在为即将弃用的 API 增加迁移债 | 若要改用 ButtonGroup → 依赖 material3 升级 | 小（1 票，仅 `FileEditScreen` 二选一） |
| **5** | `SwipeToDismissBox` 通知滑动 | ✅ **已落地** — `NotificationsPanel.kt` 已 import 并使用 | —（已完成） | — | — | 无 |
| **6** | NavHost 全局转场 + `enableOnBackInvokedCallback` | ✅ **已落地** — `AppNavHost.kt` 有 `appEnterTransition`/`appExitTransition`（含 `motionScale`）；manifest 已有 `android:enableOnBackInvokedCallback="true"` | —（已完成） | — | — | 无 |
| **7** | 共享元素转场（列表头像→详情、仓库卡→RepoHeader） | ⚠️ **试点已落地** — `core/ui/LocalNavTransitionScope.kt` 已封装 Compose 1.11 的 `sharedElement`/`SharedTransitionScope` | **建议扩大试点面，但不急于全量**（官方无"必须用共享元素"的硬要求；收益是导航连续性） | ⚪ 基座已具备；剩余为逐页接入 | 无（基座已就绪） | 中（1–2 票，先做 RepoHeader） |
| **8** | `MotionScheme.expressive()` + `LocalMotionScale` 统一缩放 | ⚠️ **半落地** — `LocalMotionScale` 已落地并注入 `AppTheme`；**`MotionScheme` 完全未接入**（全仓 `grep motionScheme` = 0 命中） | ✅ **强烈建议采纳，且是本期最高性价比项** | 🟢 见 §4：1.4.0 的 `MaterialTheme(motionScheme=…)` 与 `MotionScheme` 接口**均 public 无门控** → **零依赖升级即可拿到 Expressive 弹簧物理**。官方："Products should migrate to the new system" | **无**（1.4.0 即可） | 中（1 票，~150 行 + 单测） |
| **9** | 共享组件族上移 `core:ui` | ✅ **已落地** — `AppEmptyState`/`AppErrorState`/`AppLoadingState`（`AppStateViews.kt`）、`AppStateChip.kt`、`RelativeTime.kt` 均在位 | —（已完成） | — | — | 无 |
| **10** | `dropShadow()`/`innerShadow()` 精细阴影 | ❌ **未做** — 全仓 0 命中 | ✅ **建议采纳**（与 Expressive 解耦，**当前工具链即可用**） | 🟢 **已实测可用**：`androidx.compose.ui.draw.ShadowKt` 在 **ui 1.11.4** 中公开 `dropShadow(Modifier, Shape, Shadow)` / `dropShadow(Modifier, Shape, DropShadowScope.() -> Unit)` / `innerShadow(...)` 同形两重载（位于 **`androidx.compose.ui.draw`，即 `ui` 模块**——审计文档写的"Foundation"是**误标**）。⚪ ui-design §1.1-4 要求"阴影精细均匀"；tonal elevation 表达不了的场景（图片查看器圆角卡外投影、代码块内凹）。⚠️ 颜色须走 scrim+alpha，**不得硬编码色**（红线） | 无 | 小（1 票） |
| **11** | 主题色低对比渐变背景 | ❌ **未做** — 全仓 0 命中 `Brush.*Gradient` | ✅ **建议采纳**（`FEEDBACK.md` #30 的轻量兜底，与背景图方案不冲突） | ⚪ 纯 `colorScheme.*` + alpha，不触红线 | 无 | 小（1 票） |
| **12** | `AnimatedContent` + `SizeTransform(clip=false)` | ❌ **未做** — 全仓 0 命中 | ✅ **建议采纳**（Issue 头部状态变化、通知分组折叠的高度平滑过渡） | 🔵 官方："Common transitions should not use overt style effects like bouncy springs" → 这类高度过渡**应继续用 easing/duration**，即 `AppMotion`，**恰好不与 §4 方案冲突** | 无 | 小（1 票） |
| **13** | 技术债批次（类型安全路由 / itemKey / `@Immutable` / `@Preview`） | ✅ **已落地** — `@Serializable` 路由 52 处引用；`key =` 51 处；`@Immutable` 41 处；`@Preview` 25 处 | —（已完成） | — | — | 无 |
| **14** | 版本策略备忘（锁 1.4 stable；Expressive 在 1.5.0-alpha 线，按需 pin alpha 覆盖 BOM） | 📌 **本报告即该备忘的兑现** | ✅ **判定为「方向正确但不完整」** | 审计原文说「Expressive 组件在 1.5.0-alpha 线、`@ExperimentalMaterial3ExpressiveApi` 门控」——**前半句经字节码实测确认正确**；**后半句已过期**：截至 alpha28，**只剩 `LoadingIndicator` 仍门控**。**审计遗漏了最关键的一条：alpha19+ 的 `minCompileSdk=37` + `AGP=9.1.0` 硬约束**（§3.3）| — | 本报告取代 |

### 5.1 判定汇总

| 判定 | 提案# | 数量 |
|---|---|---|
| ✅ 已完成，无需立项 | 1, 2, 5, 6, 9, 13 | 6 |
| ✅ **建议采纳（本期可做，不依赖依赖升级）** | **8, 10, 11, 12** | **4** |
| ⚠️ 部分采纳 / 缩小范围 | 4（仅 FileEdit 二选一）、7（扩大试点） | 2 |
| ⏸ 采纳但**阻塞于 material3 升级** | 3（LoadingIndicator / wavy） | 1 |
| 📌 备忘（本报告兑现 + 修正） | 14 | 1 |

**明确不采纳 / 降级的部分及理由**：
- **提案 4 的「替换**全部**单选组」→ 降级**：① 官方已把 `SegmentedButton` 标为"no longer recommended"，扩大使用面是增加迁移债；② Issue milestone 选择卡是**带元信息的列表选择**，不是分段控件，语义不符。
- **Expressive 字体（15 emphasized styles）→ 不采纳**：Compose 侧 **无 exposed emphasized typescale**（`Typography` 无对应槽位），需手工构造 `TextStyle`；且官方警告"Avoid changing the type size"。收益低于成本。
- **Expressive 形状库（35 shapes）→ 本期不采纳**：官方明令"Shapes should be used **sparingly**"、"Avoid applying unconventional shapes to text-heavy containers"，且本项目已有 `AppShapes` 令牌体系与"卡片信息密度"约束；引入 35 形状与项目克制风格（ui-design §1.1-1「简洁流畅」）冲突。
- **`FloatingToolbar` / `FAB menu` / Docked toolbar → 本期不采纳**：本项目导航结构是**两层导航 + 底部 3 Tab**（ADR-0006 用户拍板），官方红线"Don't show a navigation bar and a toolbar with navigation controls at the same time"、"已有 navigation bar 时不要用 docked toolbar"——**直接冲突**。FAB menu 需先有 FAB 场景，本项目当前无。

---

## 6. 推荐落地路线（分波次，含每步验证方式）

> **编排原则**：**先做不依赖依赖升级的事**（波次 A/B），**把工具链升级单独隔离成一个前置调研**（波次 C0），**Expressive 组件放最后**（波次 C）。

### 波次 A — 零风险、零依赖变更（1–2 票，立即可做）

| 步 | 内容 | 验证方式 |
|---|---|---|
| A1 | **`AppMotionScheme` 令牌 + 主题接入**（§4.3 步骤 1–2） | `:core:designsystem:testDebugUnitTest`（新增 `AppMotionSchemeTest`：断言 6 个 spec 数值；**断言 effects 的 `dampingRatio == 1.0f`**）+ `spotlessCheck + detekt` |
| A2 | 既有动效消费点**按语义分类**迁移（§4.3 步骤 3，仅 effects 类） | `:app:compileDebugKotlin` + 真机走查（动效截图拍不到） |
| A3 | `coverageVerify` | ⚠️ 新增顶层文件 `AppMotionScheme.kt` 若不落 JaCoCo 排除模式（`*Token*` 等），**必须配单测**，否则 `feature:designsystem` 阈值会挂 |

**出口标准**：`spotlessCheck + detekt + konsistCheck + :app:lintDebug + :app:testDebugUnitTest + coverageVerify` 全绿；真机确认动效无异常、系统「移除动画」下全部即时完成。

### 波次 B — 与 Expressive 解耦的 UI 债（3 票，可并行）

| 步 | 内容 | 验证方式 |
|---|---|---|
| B1 | 提案 #10 `dropShadow`/`innerShadow`（图片查看器、代码块容器） | 截图基准（**新增基准须问用户**，`recordRoborazziDebug` 本机极慢）+ 无硬编码色 grep |
| B2 | 提案 #11 主题色低对比渐变（Login/GuestWelcome hero、Trending 大卡、空态） | 截图基准 + 亮/暗双主题各一张 |
| B3 | 提案 #12 `AnimatedContent + SizeTransform(clip=false)`（Issue 头部、通知分组折叠） | 单测（状态→尺寸断言）+ 真机走查 |

**出口标准**：同波次 A。**注意**：B 波次会改动 UI 静态外观 → **Roborazzi 基准需要重录**，这一步必须**先取得用户同意**。

### 波次 C0 — **工具链升级可行性调研票（所有 Expressive 组件的唯一前置）**

| 步 | 内容 |
|---|---|
| C0-1 | 核实 `compileSdk 37` 的 SDK 平台是否可用、Android Studio/AGP 9.1.0 的获取路径 |
| C0-2 | 盘点 AGP 8.7.3 → 9.1.0 的 breaking changes 对本项目的影响面（`buildSrc`、`build.gradle.kts` 全部约定插件） |
| C0-3 | **逐项验证质量门禁**：spotless 8.9.0 / detekt 1.23.8 / ktlint 1.8.0 / konsist 0.17.3 / **JaCoCo 0.8.13** / **Roborazzi 1.71.0** / Lint 在 AGP 9 下是否可用 |
| C0-4 | **Haze 1.6.10** 的传递依赖（`androidx.activity 1.12.2` 需 AGP ≥8.9.1 —— 见 toml 注释）在 AGP 9 下是否解禁，能否顺带升到 1.7.2 |
| C0-5 | 确认 KSP 升级到 2.3.8+ 的可行性（AGP 9 解锁 `addKspConfigurations(boolean)`） |
| C0-6 | **产出**：一份 go/no-go 决策文档 + 迁移票拆分建议 |

**出口标准**：文档产出，附每条结论的命令级证据。**此票不改任何生产代码。**

### 波次 C — Expressive 组件（**仅当 C0 判定 go，或用户明确接受 pin alpha18**）

| 步 | 内容 | 验证方式 |
|---|---|---|
| C1 | **加载态统一收口**：`AppStateViews.kt` 的 `AppLoadingState` 换成 `LoadingIndicator`（整页首载）；确认 `graphics-shapes` 传递依赖引入无误 | 单测（`AppLoadingState` 渲染）+ 截图基准 |
| C2 | **按钮内 pending → 小尺寸 flat**（官方：wavy 在极小尺寸不可见）；**不要**在按钮里用 wavy | 截图（小尺寸可读性） |
| C3 | 长过程（T16 Diff 加载、T22 上传）用 `Linear/CircularWavyProgressIndicator` | 截图 + 真机走查 |
| C4 | `MergeBox` 的**手搓 SplitButton** → `SplitButtonLayout`（⚪ 现状：`Button` + `Spacer(8dp)` + `FilledTonalIconButton` + `DropdownMenu` 手工拼装） | 截图基准对比 + 单测 |
| C5 | 若 C0 判定 go：`FlexibleTopAppBar` 评估替换现有顶栏（⚠️ 需先解决「顶栏毛玻璃矩形 + 首页小分区条必须同处一个 `hazeEffect` 矩形」的既有几何约束，ui-design §6.4） | 截图 + 玻璃效果真机走查 |

**依赖升级两种路径（二选一，均须用户拍板）**：
- **路径 1（保守，推荐）**：波次 C 整体**暂缓**，等 C0 出 go 结论 → 升 AGP 9.1 + compileSdk 37 + material3 1.5.0 stable（一次到位，不欠技术债）。
- **路径 2（激进）**：`libs.versions.toml` 显式 pin `material3 = "1.5.0-alpha18"` 覆盖 BOM → **立即拿到全部 Expressive 组件**，代价是冻结在 alpha 且 `LoadingIndicator` 门控不稳。**须在代码注释中显式记录为技术债与升级触发条件。**

---

## 7. 风险与未决项

### 7.1 风险登记

| # | 风险 | 等级 | 说明 / 缓解 |
|---|---|---|---|
| R1 | **pin alpha18 后 API 签名漂移** | **高** | 🟢 `LoadingIndicator` 门控在 a18↔a19 之间翻转，证明该窗口 API 不稳定。缓解：只在路径 2 下接受，并写死升级触发条件 |
| R2 | **AGP 9.1.0 升级的验证链重建成本** | **高** | 影响 spotless/detekt/konsist/lint/JaCoCo/Roborazzi/Haze 全链路。缓解：波次 C0 先行，独立成票，不与功能混做 |
| R3 | `graphics-shapes` 新传递依赖 | 低 | 官方 AndroidX 库，供 `RoundedPolygon`。需确认不引入额外体积/许可证问题（Apache-2.0） |
| R4 | **动效截图无法自动验证** | 中 | Roborazzi 静态截图拍不到动效；且 `recordRoborazziDebug` 本机极慢（1000s+ 曾卡死）。缓解：动效走真机走查 + 纯函数单测断言 spec 数值 |
| R5 | `stiffness / scale²` 换算是**工程近似**，非官方 | 中 | 若实现票采用，须真机走查验收；保守选项是**不缩放 spring**，只让滑杆作用于转场 |
| R6 | 波次 B 改动静态视觉 → **Roborazzi 基准全量重录** | 中 | 铁律：`recordRoborazziDebug` 默认禁止，**须先问用户** |
| R7 | 覆盖率门禁 | 中 | ⚪ 新增顶层文件若不落 JaCoCo 排除模式必须配单测（AGENTS 血泪教训：`feature:home` 0.8043 < 0.81 曾导致 CI 挂） |
| R8 | 官方 alpha 线文档 "Added in" 标签不可信 | 中 | 缓解：**一切 alpha 线版本结论以字节码实测为准**（本报告方法论） |

### 7.2 未决项（需用户/后续决策）

| # | 未决项 | 需要的决策 |
|---|---|---|
| U1 | **走路径 1（等工具链升级）还是路径 2（pin alpha18）？** | **用户拍板**。这是本报告唯一的重大分叉 |
| U2 | 是否启动波次 C0（AGP 9.1 + compileSdk 37 可行性调研） | 用户拍板（建议：**是**，纯调研零风险，且解锁 KSP 2.3.8+） |
| U3 | 「15 个 emphasized type styles」是否值得手工实现 | 建议本期不做（§5.1）；若用户坚持需单独立项 |
| U4 | `LocalMotionScale` 是否改为实时跟随 `MotionDurationScale` | 独立小票，不与 Expressive 绑定（§4.4） |
| U5 | **`CenterAlignedTopAppBar` 是否被 Expressive 弃用** | **UNVERIFIED** — 官方 app bars 页只点名 medium / large 不再推荐，**未提及 `CenterAlignedTopAppBar`**。不要据此改代码，需真机/官方确认 |
| U6 | 「Expressive fast effects」「Expressive slow spatial」的 ms 近似值 | **UNVERIFIED**（官方 spring→curve 表未列该两行）——只有用 spring 才不受影响，本方案正是用 spring |

---

## 8. 来源清单

### 8.1 官方设计规范（m3.material.io）

| 主题 | 链接 |
|---|---|
| M3 Expressive 发布说明 | [Start building with Material 3 Expressive](https://m3.material.io/blog/building-with-m3-expressive) |
| Motion 物理体系（Compose 落地） | [Adding Motion Physics with Jetpack Compose](https://m3.material.io/blog/m3-expressive-motion-theming) |
| Compose 1.5.0 / Expressive stable 化计划 | [Material Android is Compose-first](https://m3.material.io/blog/material-is-compose-first) |
| Motion 原理 | [Motion — How it works](https://m3.material.io/styles/motion/overview/how-it-works) |
| Motion 参数表（spring/curve/ms） | [Motion physics system — Specs](https://m3.material.io/styles/motion/overview/specs) |
| Easing & duration（转场，仍在使用） | [Applying easing and duration](https://m3.material.io/styles/motion/easing-and-duration/applying-easing-and-duration) |
| 圆角刻度（10 档） | [Corner radius scale](https://m3.material.io/styles/shape/corner-radius-scale) |
| 形状原则 + 35 shapes | [Shape — Overview & principles](https://m3.material.io/styles/shape/overview-principles) |
| Shape morph | [Shape morph](https://m3.material.io/styles/shape/shape-morph) |
| 字体刻度（15 + 15 emphasized） | [Type scale & tokens](https://m3.material.io/styles/typography/type-scale-tokens) |
| 字体与回退链 | [Fonts](https://m3.material.io/styles/typography/fonts) |
| Loading indicator | [Overview](https://m3.material.io/components/loading-indicator/overview) · [Guidelines](https://m3.material.io/components/loading-indicator/guidelines) |
| Progress indicators（flat vs wavy） | [Overview](https://m3.material.io/components/progress-indicators/overview) · [Guidelines](https://m3.material.io/components/progress-indicators/guidelines) |
| App bars（flexible 取代 medium/large） | [App bars — Overview](https://m3.material.io/components/app-bars/overview) |
| Split button | [Overview](https://m3.material.io/components/split-button/overview) · [Guidelines](https://m3.material.io/components/split-button/guidelines) |
| Toolbars（floating / docked） | [Toolbars — Overview](https://m3.material.io/components/toolbars/overview) · [Guidelines](https://m3.material.io/components/toolbars/guidelines) |
| Button groups（取代 segmented button） | [Overview](https://m3.material.io/components/button-groups/overview) · [Guidelines](https://m3.material.io/components/button-groups/guidelines) |
| FAB menu | [Overview](https://m3.material.io/components/fab-menu/overview) · [Guidelines](https://m3.material.io/components/fab-menu/guidelines) |
| Lists（baseline 定义） | [Lists — Overview](https://m3.material.io/components/lists/overview) |
| Compose 开发入口 | [Material Design 3 for Jetpack Compose](https://m3.material.io/develop/android/jetpack-compose) |

### 8.2 Android 开发者文档

| 主题 | 链接 |
|---|---|
| **Compose Material3 版本与 release notes** | [Compose Material 3 — Jetpack releases](https://developer.android.com/jetpack/androidx/releases/compose-material3) |
| **BOM → 库版本映射** | [BOM to library version mapping](https://developer.android.com/develop/ui/compose/bom/bom-mapping) |
| material3 全部已发布版本 | [maven-metadata.xml](https://dl.google.com/android/maven2/androidx/compose/material3/material3/maven-metadata.xml) |
| `MotionScheme` API | [MotionScheme](https://developer.android.com/reference/kotlin/androidx/compose/material3/MotionScheme) |
| `MaterialTheme`（`motionScheme`） | [MaterialTheme](https://developer.android.com/reference/kotlin/androidx/compose/material3/MaterialTheme) |
| `MaterialExpressiveTheme` | [MaterialExpressiveTheme](https://developer.android.com/reference/kotlin/androidx/compose/material3/MaterialExpressiveTheme.composable) |
| `LoadingIndicator` | [LoadingIndicator](https://developer.android.com/reference/kotlin/androidx/compose/material3/LoadingIndicator.composable) |
| `LinearWavyProgressIndicator` | [LinearWavyProgressIndicator](https://developer.android.com/reference/kotlin/androidx/compose/material3/LinearWavyProgressIndicator.composable) |
| `SplitButtonLayout` | [SplitButtonLayout](https://developer.android.com/reference/kotlin/androidx/compose/material3/SplitButtonLayout.composable) |
| `ButtonGroup` | [ButtonGroup](https://developer.android.com/reference/kotlin/androidx/compose/material3/ButtonGroup.composable) |
| `FloatingActionButtonMenu` | [FloatingActionButtonMenu](https://developer.android.com/reference/kotlin/androidx/compose/material3/FloatingActionButtonMenu.composable) |
| `TopAppBarDefaults`（flexible 高度常量） | [TopAppBarDefaults](https://developer.android.com/reference/kotlin/androidx/compose/material3/TopAppBarDefaults) |
| `ExperimentalMaterial3ExpressiveApi` | [API ref](https://developer.android.com/reference/kotlin/androidx/compose/material3/ExperimentalMaterial3ExpressiveApi) |
| **系统动效缩放（减弱动画）** | [`MotionDurationScale`](https://developer.android.com/reference/kotlin/androidx/compose/ui/MotionDurationScale) · [`Settings.Global.ANIMATOR_DURATION_SCALE`](https://developer.android.com/reference/android/provider/Settings.Global#ANIMATOR_DURATION_SCALE) |
| Compose 实现（实时观察动效缩放） | [`WindowRecomposer.android.kt`](https://raw.githubusercontent.com/androidx/androidx/androidx-main/compose/ui/ui/src/androidMain/kotlin/androidx/compose/ui/platform/WindowRecomposer.android.kt) |
| Compose 官方 spring 令牌源 | [`ExpressiveMotionTokens.kt`](https://raw.githubusercontent.com/androidx/androidx/androidx-main/compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/tokens/ExpressiveMotionTokens.kt) · [`StandardMotionTokens.kt`](https://raw.githubusercontent.com/androidx/androidx/androidx-main/compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/tokens/StandardMotionTokens.kt) |
| Android Blog（Expressive 覆盖） | [The Android Show: I/O Edition](https://android-developers.googleblog.com/2025/05/the-android-show-io-edition.html) · [Androidify](https://android-developers.googleblog.com/2025/05/androidify-building-delightful-ui-with-compose.html) |

### 8.3 本地实证（🟢 字节码 / ⚪ 仓库文件）

| 证据 | 位置 / 命令 |
|---|---|
| 项目解析到的 material3 版本 | ⚪ `gradle/libs.versions.toml`（`compose-bom = "2026.06.01"`）+ `compose-bom-2026.06.01.pom` → `material3:1.4.0` |
| 1.4.0 真实 API 面 | 🟢 `~/.gradle/caches/.../material3-android/1.4.0/*/material3.aar` → `javap -p androidx/compose/material3/MotionScheme.class` |
| **1.4.0 的 12 个 spring 常量实际取值** | 🟢 同 AAR → `javap -c -p androidx/compose/material3/tokens/ExpressiveMotionTokens.class`（读 `<clinit>` 中 `ldc`/`fconst_1` + `putstatic` 配对）；`StandardMotionTokens` 同法 |
| `dropShadow`/`innerShadow` 可用性 | 🟢 `ui-android-1.11.4.aar` → `javap -p androidx/compose/ui/draw/ShadowKt.class` |
| `SpringSpec` 可断言性 | 🟢 `animation-core-android-1.11.4.aar` → `javap -p androidx/compose/animation/core/SpringSpec.class`（`getDampingRatio()` / `getStiffness()` 为 public） |
| 各版本 `minCompileSdk` / `minAGP` | 🟢 各版本 AAR 的 `META-INF/com/android/build/gradle/aar-metadata.properties` |
| 各版本门控时间线 | 🟢 `javap -v <Class>.class \| grep -c ExperimentalMaterial3ExpressiveApi`（alpha01/10/15/18/19/20/21/22/25/26/27/28） |
| 传递依赖 | 🔵 `https://dl.google.com/android/maven2/androidx/compose/material3/material3/1.5.0-alpha18/material3-1.5.0-alpha18.module` |
| 现有动效/玻璃/形状令牌 | ⚪ `core/designsystem/.../token/AppMotion.kt`、`AppMotionScale.kt`、`theme/AppShapes.kt`、`AppTheme.kt`、`component/GlassSurface.kt`、`CardGroup.kt` |
| 现有加载态与单选组 | ⚪ `core/designsystem/.../component/AppStateViews.kt`、`feature/notifications/.../NotificationsPanel.kt`、`feature/pullrequest/.../MergeBox.kt`、`feature/issue/.../IssueDetailScreen.kt` |
| 现有关键约束 | ⚪ `docs/ui-design.md`（§4 动效 / §5 图标 / §6 毛玻璃）、`docs/adr/0004`、`0006`、`0007`、`docs/ui-audit-2026-08-21.md` §4 |

---

## 附：本报告与 `docs/ui-audit-2026-08-21.md` §4 提案 #14 的关系

提案 #14 原文：「版本策略备忘：主体锁 material3 1.4 stable；Expressive 组件（LoadingIndicator/Wavy/FlexibleTopAppBar/SplitButton/FloatingToolbar/ButtonGroup/FAB Menu）在 1.5.0-alpha 线、`@ExperimentalMaterial3ExpressiveApi` 门控，按需 pin alpha 覆盖 BOM 混用」

**本次调研的修正**（三处）：
1. ✅ **「Expressive 组件在 1.5.0-alpha 线」正确**（🚫 1.4.0 stable **确实没有**这些组件——字节码实测）。
2. ❌ **「`@ExperimentalMaterial3ExpressiveApi` 门控」已过期**：截至 alpha28，**只剩 `LoadingIndicator` 仍门控**；wavy / SplitButton / ButtonGroup / FloatingToolbar / FlexibleTopAppBar / FAB Menu / `MotionScheme` 均已毕业。
3. ➕ **遗漏了最关键的硬约束**：**alpha19+ 要求 `minCompileSdk=37` + `minAndroidGradlePluginVersion=9.1.0`**，与本项目 `compileSdk 36` + `AGP 8.7.3` 冲突。**「按需 pin alpha 覆盖 BOM」在 alpha19+ 上根本行不通**——可用窗口只有 **alpha01–alpha18**。这条约束决定了整个落地路线。
