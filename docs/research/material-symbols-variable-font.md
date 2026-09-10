# Material Symbols 变量字体四轴评估（UI13 / issue #168）

> 状态：**结论已出（暂缓引入）** · 评估日期 2026-09-10 · 归属 ticket #168（原审计明细 UI13）
> 关联：ADR-0004 §3、`docs/ui-design.md` §5.1/§5.2/§4.3、`gradle/libs.versions.toml`
> 证据口径：**本机实测**（Gradle 缓存构件 + 已解析依赖 + 平台 android.jar），
> 本机当前**无外网**（`fonts.google.com` / `developer.android.com` 均不可达），
> 凡需外网才能取证的项一律标注「待实测」，不臆造数字。

---

## 1. 结论（TL;DR）

**暂缓引入变量字体，维持 com.composables 静态变体体系**（outlined / outlined-filled /
rounded / rounded-filled，见 #168 / UI12 的 `AppIconSpec`）。

三条决定因素：

1. **需求已被静态变体覆盖**：ui-design §5.1 的硬要求是「选中态实心 / 未选中空心」，
   §5.2 已拍板静态 outlined/filled 变体「现在就能做」——#168 UI12 已用四家族变体落地
   风格切换，**不依赖任何轴能力**。
2. **成本不对称**：静态家族按「每家族一份全量 ImageVector 类」计入 dex（本机实测单家族
   debug AAR ≈ 12.6 MB）；变量字体是 1 个字体文件覆盖全部图标 + 4 轴，理论上包体更优，
   但它把图标从 ImageVector 体系拽到文本渲染体系（§5），并引入 Typeface 重建 / 字形回退 /
   Robolectric 不可断言三类新风险。**收益（wght 300 与 FILL 过渡动画）当前不是阻塞需求。**
3. **可逆性**：静态变体是「数据」不是「架构」——`AppIconSpec` 只需换实现即可切到变量字体，
   消费点（底栏/顶栏/设置页）无需改动。等真要 FILL 过渡动画（§4.3）时再引入，代价不涨。

**再评估触发条件**（满足任一条即重开）：

- FILL 轴 **0↔1 平滑过渡**成为硬需求（ui-design §4.3「图标状态」行，当前用变体瞬时切换替代）；
- 需要全局 **wght 300「稍细」**（§5.1 用户拍板 11b）且静态库无该档；
- release 包体/构建时长被图标家族显著拖累（当前 R8 兜底，未发生，见 §3）。

---

## 2. 四轴在 Android / Compose 的可用性（本机实测）

平台侧：本仓 **minSdk 26**（`buildSrc/src/main/kotlin/appdev.android.library.gradle.kts` L17），
变量字体与轴 API 自 API 26 起可用。以本机 android.jar（compileSdk 37 平台）实测：

```text
$ javap -classpath <android.jar> android.graphics.fonts.FontVariationAxis
  public static android.graphics.fonts.FontVariationAxis[] fromFontVariationSettings(String);
  public static String toFontVariationSettings(FontVariationAxis[]);
$ javap -classpath <android.jar> 'android.graphics.Typeface$Builder'
  public Typeface$Builder setFontVariationSettings(FontVariationAxis[]);
  public Typeface$Builder setFontVariationSettings(String);
```

> 注：**不存在** `android.graphics.fonts.FontVariationSettings` 这个类（常见误记），
> 平台 API 是 `FontVariationAxis` + `Typeface.Builder.setFontVariationSettings(...)`。

Compose 侧：本仓 **compose-bom 2026.06.01** → 实测解析到 `ui-text 1.11.4`：

```text
$ javap -classpath ui-text.aar!classes.jar androidx.compose.ui.text.font.FontVariation
  public final FontVariation$Setting weight(int);      // wght
  public final FontVariation$Setting grade(int);        // GRAD
  public final FontVariation$Setting opticalSizing(long);// opsz
  public final FontVariation$Setting Setting(String, float); // 任意轴（FILL / ROND / slnt…）
  public final FontVariation$Settings Settings(FontWeight, int, FontVariation$Setting...);
$ javap -classpath ui-text.aar!classes.jar androidx.compose.ui.text.font.FontKt
  public static final Font Font-F3nL8kk(int, FontWeight, int, int, FontVariation$Settings);
  //                                     ↑resId  ↑weight   ↑style ↑loadingStrategy ↑variationSettings
```

| 轴 | 取值 | Compose 表达 | 可用性判定 |
|---|---|---|---|
| **FILL** | 0–1 | `FontVariation.Setting("FILL", 1f)`（无专用 helper） | ✅ 可表达；**切换即换 Typeface**，逐帧动画成本高（见 §4 风险） |
| **wght** | 100–700 | `FontVariation.weight(300)` | ✅ 有专用 API，静态库无 300 档，这是变量字体唯一「静态做不到」的能力 |
| **GRAD** | -50–200 | `FontVariation.grade(-25)` | ✅ 有专用 API；低强调场景可做，但本仓未提出该需求 |
| **ROND** | 0–100 | `FontVariation.Setting("ROND", 50f)`（无专用 helper） | ⚠️ 仅 Material Symbols **Rounded** 变量字体含 ROND 轴；且需自写 String 轴名 |
| （opsz） | 20–48 | `FontVariation.opticalSizing(...)` | ✅ 图标场景基本用不到（图标是矢量大小，不是光学尺寸） |

**结论**：四轴在「API 26 + Compose 1.11.4」下技术上全部可用，卡点不在可行性，在**取舍**（§5）。

---

## 3. 包体增量（本机实测）

**现状：静态家族按家族全量进 dex。** 实测（Gradle 缓存构件）：

```text
# 单个静态家族构件
icons-material-symbols-rounded-cmp-debug.aar        12 632 726 B   // ≈3 900 个 ImageVector Kt 类
icons-material-symbols-rounded-android-2.2.1.aar     2 422 975 B   // 仅 res/drawable XML（3 933 个），classes.jar 空

# #168 / UI12 引入 4 个家族前后的同一 debug APK（本机实测）
app-debug.apk  改动前（仅 rounded 家族）  33 025 258 B
app-debug.apk  改动后（+3 家族，共 4 个） 43 783 663 B   // +10.8 MB / +32%
  └─ classes25.dex（图标家族聚合 dex，未压缩）20 879 500 B → 44 248 912 B
```

- **每多引入一个静态家族 ≈ +12.6 MB AAR / +20 MB 未压缩 dex / +3.6 MB debug APK**
  （#168 / UI12 引入 4 个家族：rounded 原已存在，新增 outlined / outlined-filled / rounded-filled）。
  ——这是**本次为「三档图标风格」付的真实成本**，把包体维度摆上台面正是本评估的意义之一：
  变量字体只需 1 个文件，理论上能把这份成本收回。
- **release 有 R8 兜底**：未被引用的图标是「静态字段 + 惰性委托」，R8 可整体删除 →
  生产包不随家族数线性增长；受影响的是 **debug APK 与 CI 构建时长**（dex 合并）。
- **变量字体的包体**：Google Fonts 分发的 Material Symbols 可变 TTF 单文件覆盖全部图标 +
  全部轴，量级明显小于「多家族 × 静态类」；但**具体字节数需外网实测，本机无法取证 → 待实测项**。
  另需注意 Android 上 `.ttf` 不能压缩进 APK（AGP 默认 `noCompress` 不含字体时需确认），
  实际 APK 增量接近文件原始大小，而非「压缩后」大小。

**结论**：包体维度变量字体理论上更优，但当前无实测数字支撑决策；且 §5 的体系代价远大于此收益。

---

## 4. Kotlin 2.3.21 / AGP 8.7.3 兼容性

| 项 | 判定 | 依据 |
|---|---|---|
| 变量字体文件本身 | ✅ 与 Kotlin/AGP 无关 | 变量字体是资源（`.ttf` + `fvar` 表），运行时由平台解析 |
| Kotlin 2.3.21 | ✅ 无冲突 | 走的是 `androidx.compose.ui.text.font.Font(resId, …, variationSettings)` 既有重载（§2 实测），不涉及编译器特性 |
| AGP 8.7.3 | ✅ 无冲突 | 资源引入即可；**但**若改用三方 KMP 包装库需另审 AAR metadata（本仓 haze 1.7.2 因传递依赖要求 AGP≥8.9.1 被迫降到 1.6.10 —— 同类坑先例） |
| dev.vicart material-symbols（`-variable-font` 变体） | ⚠️ **未实测（待验证项）** | 本机无外网，无法解析其版本/产物/维护活跃度；引入前必须在 prototype 分支跑最小 demo（原始评估要求） |

**风险清单（即便将来引入）**：

1. `FontVariation.Setting` 作用在**非变量字体**上会抛错（`IllegalArgumentException`），
   字体资源与调用点必须成套引入，不能半途替换。
2. 轴值变化 → **重建 Typeface**：FILL 过渡动画要做「逐帧插值」需自己做节流/步进，
   否则 60fps 下每帧一次 Typeface 创建会掉帧（ui-design §4.4「滚动性能优先」红线）。
3. **Robolectric 不解析真实字体**：变量字体渲染效果在本仓的 JVM 测试管线里断言不到，
   截图基线也只能拍到 fallback 字形 —— 会打穿「Linux 纯 JVM 免模拟器」的测试策略
   （plan.md §12），必须真机/模拟器兜底。
4. 图标从 `ImageVector` 改为字形：失去 `Icon(imageVector=…)` 的 tint/尺寸语义，
   需改用 `Text` + `color` + `fontSize`，RTL/bidi 与文本基线也会介入（§5）。

---

## 5. 与现有静态变体混用的代价

| 维度 | ImageVector（现状） | 变量字体 |
|---|---|---|
| 渲染管线 | 矢量 Path，直接 draw | **文本管线**（字体解析 + 字形排版） |
| 着色 | `Icon(tint=…)` 直接生效 | 走 `Text(color=…)`，需自建封装 |
| 尺寸 | `Modifier.size(dp)` 精确 | 字号近似，需换算 + 基线对齐 |
| 测试 | Robolectric 可断言/截图 | JVM 内**不可断言**（§4.3） |
| 动画 | 变体瞬时切换（当前）；轴过渡需变量字体 | FILL 轴过渡天然可做 |
| 与 Octicons 共存 | ✅ 同体系（15 枚 Octicons 已是 ImageVector） | ❌ 双体系：同一行图标两种渲染路径 |

**混用结论**：本仓图标体系是「Material Symbols（ImageVector）+ Octicons（ImageVector）」
单一体系；引入变量字体必然形成**双体系**（符号走文本、Octicons 走矢量），
底栏这类「符号与 Octicon 并排」的场景会出现两套渲染路径与两套对齐逻辑 ——
这是比包体更硬的反对理由。

---

## 6. 若将来引入：推荐实施方案（备查，本次不执行）

1. `res/font/material_symbols_rounded_variable.ttf`（Rounded 变体，含 FILL/wght/GRAD/opsz/ROND）
2. `res/values/fonts.xml` 或代码侧 `Font(R.font.…, variationSettings = FontVariation.Settings(FontVariation.weight(300), FontVariation.Setting("FILL", 1f), FontVariation.Setting("ROND", 50f)))`
3. `AppIconSpec` 增加「轴实现」分支：`ImageVector` 与「字形 + 轴值」二选一，
   由 `LocalIconStyle` 驱动；消费点签名不变（#168 已把消费点收敛到 `AppIcon` 入口，切换成本低）
4. 验收须含：真机对照截图（wght 300 vs 400 / ROND 0 vs 50）、滚动帧率、APK 增量实测

---

## 7. 复现命令（本机实测证据）

```bash
# 平台 API 是否存在
AJ=$(ls -d $ANDROID_HOME/platforms/android-3*/android.jar | sort -V | tail -1)
javap -classpath "$AJ" android.graphics.fonts.FontVariationAxis
javap -classpath "$AJ" 'android.graphics.Typeface$Builder' | grep -i variation

# Compose 文本轴 API（ui-text 1.11.4）
AAR=$(find ~/.gradle/caches/modules-2/files-2.1/androidx.compose.ui/ui-text-android -name '*.aar' | head -1)
unzip -o -q "$AAR" classes.jar -d /tmp/ut && cd /tmp/ut
javap -classpath classes.jar androidx.compose.ui.text.font.FontVariation
javap -classpath classes.jar androidx.compose.ui.text.font.FontKt | grep -i "Font-"

# 静态家族体量
ls -l ~/.gradle/caches/modules-2/files-2.1/com.composables/icons-material-symbols-rounded-cmp-android-debug/*/*.aar
unzip -l app/build/outputs/apk/debug/app-debug.apk | grep classes.*dex
```

## 8. 未验证项（外网恢复后补齐）

- [ ] Material Symbols 可变字体（Rounded/Outlined/Sharp 变体）**实际文件大小**与字形覆盖
- [ ] `dev.vicart` material-symbols `-variable-font` 变体的**版本/维护活跃度/AGP 8.7.3 兼容**最小 demo
- [ ] `FontVariation.Setting("FILL"/"ROND", …)` 在 Android 26/30/34 真机的实际渲染与性能
- [ ] 变量字体在 AGP 资源打包下的压缩策略（`androidResources.noCompress`）对 APK 增量的影响
