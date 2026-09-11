# AGP 9.x 迁移可行性报告（2026-09-11）

> 基线：AGP 8.7.3 / Kotlin 2.3.21 / KSP 2.3.7 / Gradle 8.12 / compileSdk 36
> 验证 worktree：`/home/zhiyi/dev/AppDev/.worktrees/agp9`　分支：`chore/agp9-feasibility`（基于 `origin/main` @ `99a6a8d`）
> 验证目标版本：**AGP 9.1.1 / Gradle 9.3.1 / compileSdk 37 / KSP 2.3.12 / Hilt 2.59.2 / Kotlin 2.3.21（不变）**
> 结论：**可行**。配置阶段、全量 Kotlin 编译、Room/Apollo/KSP 代码生成、Android Lint（含 #170 的 Compose 检测器恢复）**均已实测通过**；无阻塞点，无被墙构件。

---

## 1. 结论速览

### 可行性判定：**可行**（无阻塞点）

一句话：**AGP 9.1.1 + Gradle 9.3.1 + compileSdk 37 在本项目上打通了配置阶段→编译→lint 三层验证，全部一次通过或经 1 处克制改动即通过；且顶掉了 #170 的 Compose lint 检查器整包跳过问题。**

### 已实测通过（证据见 §5）

| 里程碑 | 命令 | 结果 |
|---|---|---|
| 基线（改动前） | `./gradlew help` | ✅ BUILD SUCCESSFUL in 2m 3s（AGP 8.7.3 / Gradle 8.12） |
| ① wrapper 解耦 | `./gradlew help` | ✅ BUILD SUCCESSFUL in 6m（Gradle 9.3.1 + **未改动的** AGP 8.7.3） |
| ② **配置阶段** | `./gradlew help` | ✅ BUILD SUCCESSFUL in 48s（AGP 9.1.1 + Gradle 9.3.1 + compileSdk 37） |
| ③ 单模块编译 | `:core:common:compileDebugKotlin` | ✅ 13s（built-in Kotlin） |
| ④ 代码生成链 | `:core:database:compileDebugKotlin :core:github-graphql:compileDebugKotlin` | ✅ 1m 4s（Room KSP + Apollo codegen + Kotlinx serialization） |
| ⑤ **全量编译** | `:app:compileDebugKotlin` | ✅ **2m 46s，270 tasks，25 模块全部编译** |
| ⑥ **Android Lint** | `:app:lintDebug` | ✅ **4m 15s，且 0 个 registry 被跳过（#170 修复确认）** |
| ⑦ 质量门禁链 | `:core:designsystem:compileDebugKotlin spotlessCheck detekt` | ✅ 48s（ktlint 1.8.0 / detekt 1.23.8 在 Gradle 9.3.1 下可用） |

### 阻塞点

**没有硬阻塞点。** 唯一需要"绕过"的是一个**上游第三方库缺陷**（`com.composables` 两个 artifact 的 namespace 撞车，撞上 AGP 9 的 `uniquePackageNames` 新默认值），已用官方提供的迁移期开关 `android.uniquePackageNames=false` 解除，并保留了彻底修法的建议（§6.1）。

### 预估工作量

| 工作项 | 预估 | 说明 |
|---|---|---|
| 工具链迁移本身（本次已实测的改动） | **0.5 天** | 6 个文件、~40 行改动，已在本 worktree 完成 |
| 移除 15 项 lint `disable +=` 并修复暴露出的违规 | **0.5–2 天** | 检测器已恢复运行，但当前仍被 disable 列表静音（§3.8）——这是本票**唯一"解锁了但还没兑现"的收益** |
| `android.uniquePackageNames=false` 的彻底修复 | **0.5 天** | 需排查 icons 依赖是否可去掉其一，或上游修 namespace |
| 逐项复验其余门禁（Roborazzi 截图基准 / JaCoCo coverageVerify / 单测 / konsist / assembleDebug / R8） | **1–2 天** | 见 §5.3 未验证清单 |
| 移除 library 模块的 lint 任务禁用 hack | **0.5 天** | 连带上面第二项一起做 |
| **合计** | **约 3–5 天** | 不含 material3 Expressive 组件本身的落地工作 |

> 注：上游调研（`docs/research/md3-expressive-2026-09-11.md` §风险 R2）预估"验证链重建成本高"。**实测后该风险显著低于预估**——本次未改动任何一行生产代码，也未改动任何测试/截图/覆盖率配置，四层验证就已通过。

---

## 2. 目标版本矩阵

| 组件 | 当前 | 目标 | 依据（来源） |
|---|---|---|---|
| **AGP** | 8.7.3 | **9.1.1** | 满足 `minCompileSdk=37` 的**最低** AGP（9.0.x 最高只支持 API 36.1）。[AGP 9.1.1 release notes](https://developer.android.com/build/releases/agp-9-1-0-release-notes)、[AGP 9.0.1 release notes](https://developer.android.com/build/releases/agp-9-0-0-release-notes) |
| **Gradle** | 8.12 | **9.3.1** | AGP 9.1 要求**最低 Gradle 9.3.1**（同上 release notes）。腾讯镜像实测存在 `gradle-9.3.1-bin.zip`（HTTP 200） |
| **JDK** | 21 | **21（不变）** | AGP 9.x 最低 JDK **17**，本项目锁 21 → 兼容（同上） |
| **compileSdk** | 36 | **37** | AGP 9.1.x 支持的最高 API 即 37.0；`android-37.0` 平台**本机已安装**（`AndroidVersion.ApiLevel=37.0`, `Revision=2`）。也是 material3 1.5.0-alpha19+ 的 `minCompileSdk=37` |
| **buildToolsVersion** | 35.0.0 | **36.0.0** | AGP 9.x 的**最低** SDK Build Tools 就是 36.0.0（release notes 兼容表）；本机已安装 36.0.0 |
| **targetSdk** | 35 | **35（不变）** | AGP 9 的 `android.sdk.defaultTargetSdkToCompileSdkIfUnset` 默认翻为 `true` **只影响未显式设置时**；本项目在约定插件里显式写了 35，故不受影响。升 targetSdk 属运行期行为变更，不在工具链迁移范围 |
| **Kotlin (KGP)** | 2.3.21 | **2.3.21（不变）** | AGP 9 内置 KGP 2.2.10；官方文档示例明确用 `org.jetbrains.kotlin.android version "2.3.21"`，2.3.21 在支持区间。[AGP 9.4 文档示例](https://developer.android.com/build/releases/about-agp) |
| **KSP** | 2.3.7 | **2.3.12** | AGP 官方 skill 要求 AGP 9 下 **KSP ≥ 2.3.6**；且 toml 原注释指出 2.3.8+ 需要 AGP 9 的 `addKspConfigurations(boolean)` → 升 AGP 9 后自动解锁。[AGP 9 migration skill](https://developer.android.com/agents/skills/build-system/agp/agp-9-upgrade/skill) |
| **Hilt** | 2.57.2 | **2.59.2** | AGP 官方 skill 要求 AGP 9 下 **Hilt ≥ 2.59.2**；实测 2.57.2 在 AGP 9 直接报 `Android BaseExtension not found.`（§4 步骤 3）。可用更新版本：2.60 / 2.60.1 |
| **androidx.lifecycle** | 2.8.7 | **未动（2.8.7）** | 上游提到 lifecycle 2.11.0 需 AGP 9.1.0，但那是**被 hilt-navigation-compose 1.4.0 传递拉入**才发生；本项目 hilt-navigation-compose 仍是 1.2.0 → **本票无需要**。若采纳建议同步升 hilt-navigation-compose 1.4.0 + lifecycle 2.11.0/2.12.0（见 §7） |
| **compileSdk 37 SDK 平台** | — | **无需下载** | `/home/zhiyi/Android/Sdk/platforms/android-37.0` 已存在 |

### 版本可得性核对（本机镜像实测）

| 构件 | 结果 |
|---|---|
| `mirrors.cloud.tencent.com/gradle/gradle-9.1.0 / 9.2.0 / 9.2.1 / 9.3.0 / **9.3.1** / 9.4.0 / 9.5.0 / 9.6.0 -bin.zip` | ✅ 全部 HTTP 200（`9.0-bin` 与 `9.3.2-bin` 是 404 —— **注意 AGP 9.3 系列要配 Gradle 9.5.0，不是 9.3.2**） |
| Google Maven `com.android.tools.build:gradle` 9.x 稳定版 | 9.0.0 / 9.0.1 / 9.1.0 / **9.1.1** / 9.2.0 / 9.2.1 / 9.3.0 / 9.3.1 / 9.3.2 / 9.4.0（最新 alpha 9.5.0-alpha05） |
| Maven Central `com.google.devtools.ksp` | 2.3.0 … **2.3.12** |
| Maven Central `hilt-android-gradle-plugin` | 2.58 / 2.59 / **2.59.2** / 2.60 / 2.60.1 |
| Google Maven `material3` | … 1.5.0-alpha18 / **1.5.0-alpha19** … **1.5.0-alpha28**（最新） |

### AGP↔Gradle↔JDK 官方矩阵（查实，供后续升级选路）

| AGP | 最低 Gradle | 最低 JDK | 最高支持 API |
|---|---|---|---|
| 9.0.1 | 9.1.0 | 17 | **36.1**（→ 不能满足 compileSdk 37） |
| **9.1.1** | **9.3.1** | 17 | **37.0** ← 本票目标 |
| 9.3 | 9.5.0 | 17 | 37 |
| 9.4.0 | 9.6.0 | 17 | 37 |

---

## 3. AGP 9 breaking changes 对本案的影响清单

> 每条都标注了**实测是否真的需要改**。这是本报告相对官方文档最有价值的部分：官方列了 20+ 条，本案实际只命中 6 条。

| # | AGP 9 变更 | 影响本案哪个文件/配置 | 需要的改法 | 实测结果 |
|---|---|---|---|---|
| 3.1 | **built-in Kotlin 默认开启**；`org.jetbrains.kotlin.android` 插件**硬拒绝** | `buildSrc/src/main/kotlin/appdev.android.{application,library}.gradle.kts` 的 `plugins {}` | 删除 `id("org.jetbrains.kotlin.android")`；`kotlin.plugin.compose` **保留**（built-in Kotlin 只替代 kotlin-android，不替代 Compose 编译器插件） | ✅ **必须改**。不改直接 BUILD FAILED（原文见 §4 步骤 2） |
| 3.2 | built-in Kotlin 下 `jvmTarget` **默认取 `android.compileOptions.targetCompatibility`** | 两个约定插件尾部的 `extensions.configure<KotlinAndroidProjectExtension>("kotlin") { compilerOptions { jvmTarget = JVM_17 } }` | **整块删除**（compileOptions 已设 VERSION_17，等价） | ✅ **必须改**。且删除是**净简化**——少一个 import、少一个块。注意 `KotlinAndroidProjectExtension` 类型在 built-in Kotlin 下不再是 `kotlin` 扩展的注册类型，按类型 configure 会失败 |
| 3.3 | **`CommonExtension` 的泛型参数被移除** | 根 `build.gradle.kts` 的 `as CommonExtension<*, *, *, *, *, *>` | 去掉 6 个泛型参数 → `as CommonExtension` | ✅ **必须改**（否则 buildSrc 脚本编译失败）。已查 [AGP 9.1 API 文档](https://developer.android.com/reference/tools/gradle-api/9.1/com/android/build/api/dsl/CommonExtension)确认非泛型 `CommonExtension` 上 `testCoverage` 与 `buildTypes` **都还在** |
| 3.4 | 旧 DSL 实现类（`com.android.build.gradle.AppExtension` / `LibraryExtension`）在 `newDsl` 下**不再实现公开接口** | 根 `build.gradle.kts` 的 `extensions.configure<AppExtension>` / `<LibraryExtension>` | 改成 `getByName("android") as com.android.build.api.dsl.CommonExtension`，一处转换同时覆盖 `buildTypes` 与 `testCoverage` | ✅ **必须改**。已合并为一个 `configureAndroidCoverage()` 辅助函数（原为两个重复块 + 一个 `configureJacocoVersion()`） |
| 3.5 | `android.uniquePackageNames` 默认 `false → true` | 无（**第三方依赖撞车**） | `gradle.properties` 加 `android.uniquePackageNames=false` | ⚠️ **必须改**。`com.composables:icons-material-symbols-rounded-android` 与 `...-rounded-cmp-android` 的 namespace **都是** `com.composables.icons.materialsymbols.rounded`，`:app:processDebugMainManifest` 直接失败（原文见 §4 步骤 6）。**注意 `:app:compileDebugKotlin` 不触发这个错误，只有走到 manifest merge 才会暴露** |
| 3.6 | `getDefaultProguardFile()` 只支持 `proguard-android-optimize.txt` | `app/build.gradle.kts` 的 `proguardFiles(...)` | 无需改（本案用的正是 `proguard-android-optimize.txt`） | ✅ **实测无需改** |
| 3.7 | `android.sdk.defaultTargetSdkToCompileSdkIfUnset` 默认 `false → true` | `defaultConfig { targetSdk = 35 }` | 无需改（显式设置 35，不受默认值影响） | ✅ **实测无需改**（编译通过且 targetSdk 仍是 35） |
| 3.8 | `enableUnitTestCoverage` / `testCoverage.jacocoVersion`（JaCoCo 硬门禁依赖） | 根 `build.gradle.kts` 的 `configureAndroidCoverage()` | 仅因 3.3/3.4 的转换写法调整，**API 本身在 AGP 9.1 仍在** | ✅ **实测无需改语义**。已查 [AGP 9.1 BuildType API](https://developer.android.com/reference/tools/gradle-api/9.1/com/android/build/api/dsl/BuildType)：`enableUnitTestCoverage` 仍在；`CommonExtension.testCoverage: TestCoverage` 仍在 → **覆盖率门禁配置口径不变**（但 coverageVerify 本身**未实跑**，见 §5.3） |
| 3.9 | 旧 variant API（`applicationVariants` 等）移除 → 波及**依赖它的插件** | `app/build.gradle.kts` 的 Hilt 插件 | Hilt 2.57.2 → **2.59.2** | ✅ **必须改**。这是**插件**而非本项目脚本的问题（原文见 §4 步骤 3） |
| 3.10 | KSP 需 ≥ 2.3.6 | `gradle/libs.versions.toml` | 2.3.7 → **2.3.12** | ✅ **必须改**（顺带解锁，原本被迫停在 2.3.7） |
| 3.11 | `kotlin.sourceSets{}` / `android.kotlinOptions{}` DSL 迁移 | 全仓 grep | **本案零命中**（全仓无 `kotlinOptions`、无 `kotlin.sourceSets`） | ✅ **实测无需改** |
| 3.12 | `android.enableAppCompileTimeRClass`、`android.proguard.failOnMissingFiles`、`android.r8.*` 等其余默认值翻转 | 全仓 | **本案零命中**（未使用这些属性/未声明不存在的 proguard 文件） | ✅ **实测无需改** |
| 3.13 | **`android.newDsl=false` / `android.builtInKotlin=false` 退出开关** | — | **本案未使用**（直接走官方推荐的迁移正路） | ✅ 未使用。判断依据：退出开关在 **AGP 10 会被移除**，且本案走正路的实测成本只是 6 个文件的克制改动 → 无理由欠债 |
| 3.14 | `android.enableLegacyVariantApi` 等：旧 `BaseExtension` 报 "API 'applicationVariants' is obsolete" | — | 未命中 | ✅ 实测未出现 |

### ⚠️ 3.8 的重要补充：#170 的 lint 检测器**已恢复加载**，但仍被 disable 列表静音

`:app:lintDebug` **BUILD SUCCESSFUL**，且日志与报告里**完全没有**出现：

```
Library lint checks reference invalid APIs; these checks will be skipped!
Lint found an issue registry (...)
ObsoleteLintCustomCheck
```

→ 说明 **Compose 的 `UiIssueRegistry` 不再被跳过**（根因：AGP 9.1.1 内置 **lint 32.1.1**，从 AGP 9.1.1 的 POM 查到；正是 `app/build.gradle.kts` 注释预言的 "lint 32.x 才带上 Kotlin 2.3 时代的 Analysis API"）。

**但**：`app/build.gradle.kts` 与 `appdev.android.library.gradle.kts` 里的 **15 条 `disable +=` 仍在生效** → 检查器被**加载**却被**主动关掉**。要真正兑现 #170，必须删掉这 15 行（然后预计会暴露一批真实违规，因为 `abortOnError = true`）。**这是本票解锁了但尚未兑现的收益，也是后续 PR 的第一优先项。**

同时 CI 的 "Lint detector coverage guard" 步骤逻辑是「**只允许** `UiIssueRegistry` 一个 registry 被跳过」——现在一个都没有，守卫**仍然通过**（它只对新出现的跳过项报错）。所以这个守卫不需要改，但它也不再能证明 #170 的修复；建议改为「断言 registry 跳过数为 0」+「断言 15 条 disable 已清零」。

---

## 4. 实测过程（按步骤，含错误原文）

> 纪律：每步一次构建、看完整输出、不做 grep/tail 过滤后重跑。每步输出都存到 `/tmp/agp9-step*.log` 完整读取。
> 同时有其他 worktree 在跑 Gradle（本机 4 核 / 15GB），除 `help` 外一律加 `--max-workers=2`。

### 步骤 0 — 基线与前置事实核实

```bash
$ ls /home/zhiyi/Android/Sdk/platforms/          # → android-35  android-36  android-37.0
$ cat .../android-37.0/source.properties        # → AndroidVersion.ApiLevel=37.0 / Revision=2
$ java -version                                  # → openjdk 21.0.12.1
```
**关键发现**：`android-37.0` **已安装** → `app/build.gradle.kts:223` 的注释「android-37 平台尚未发布」**已过期**，compileSdk 37 **无需任何下载**，不存在被墙风险。

```bash
$ ./gradlew help --console=plain                 # 基线，AGP 8.7.3 / Gradle 8.12
BUILD SUCCESSFUL in 2m 3s                        # EXIT=0
```

### 步骤 1 — Gradle wrapper 8.12 → 9.3.1（**AGP 不动**）

先核对镜像：
```bash
$ curl -sI .../gradle-9.3.1-bin.zip              # → 200
```
改 `gradle/wrapper/gradle-wrapper.properties` 的 `distributionUrl` → `gradle-9.3.1-bin.zip`：
```bash
$ ./gradlew help --console=plain
Downloading https://mirrors.cloud.tencent.com/gradle/gradle-9.3.1-bin.zip
Welcome to Gradle 9.3.1!
BUILD SUCCESSFUL in 6m                           # EXIT=0
```
**结论**：wrapper 升级与 AGP 升级**可解耦**——AGP 8.7.3 在 Gradle 9.3.1 上配置阶段仍可用（仅有 Gradle 10 的 deprecation 警告）。这为「先升 wrapper 观察、再升 AGP」的分步验证提供了实测依据。

### 步骤 2 — 只升 AGP 到 9.1.1 → **硬失败 ①**

改动：`buildSrc/build.gradle.kts` 的 `com.android.tools.build:gradle:8.7.3 → 9.1.1`，`libs.versions.toml` 的 `agp = "9.1.1"`（后者其实是**装饰性**的——AGP 真实版本钉在 buildSrc 的 classpath 上，根 build 不可重复声明）。

```
* Where:
Precompiled script plugin '.../buildSrc/src/main/kotlin/appdev.android.application.gradle.kts' line: 1

* What went wrong:
An exception occurred applying plugin request [id: 'org.jetbrains.kotlin.android']
> Failed to apply plugin 'org.jetbrains.kotlin.android'.
   > ⛔ Failed to apply plugin 'org.jetbrains.kotlin.android'
     The 'org.jetbrains.kotlin.android' plugin is no longer required for Kotlin support since AGP 9.0.
     Solution: Remove the 'org.jetbrains.kotlin.android' plugin from this project's build file: build.gradle.
     See https://kotl.in/gradle/agp-built-in-kotlin for more details.
      > java.lang.Throwable (no error message)
BUILD FAILED in 1m 5s
```
→ 对应 §3.1。**注意这是硬拒绝（⛔），不是警告**；`android.builtInKotlin=false` 虽然能退出，但 AGP 9 默认 newDsl=true，而 kotlin-android 与新 DSL 不兼容，退出要**同时**关两个开关 → 选择走正路。

### 步骤 2b — 迁 built-in Kotlin + 新 DSL + compileSdk 37 → **硬失败 ②**

按 §3.1–3.4 改完后（两个约定插件删 kotlin-android、删 jvmTarget 块、compileSdk 37、buildTools 36.0.0；根 build 换非泛型 `CommonExtension`）：
```
> Task :buildSrc:compileKotlin                       # ✅ buildSrc 这次编过了
...
* Where:
Build file '.../app/build.gradle.kts' line: 1

* What went wrong:
An exception occurred applying plugin request [id: 'com.google.dagger.hilt.android', version: '2.57.2']
> Failed to apply plugin 'com.google.dagger.hilt.android'.
   > Android BaseExtension not found.
BUILD FAILED in 42s
```
→ 对应 §3.9。Hilt 2.57.2 走旧 variant API。**这条错误原文就是「Hilt ≥ 2.59.2」是硬门槛的直接证据。**

### 步骤 2c — KSP 2.3.12 + Hilt 2.59.2 → ✅ **配置阶段通过（里程碑 2）**

```
> Task :help
Welcome to Gradle 9.3.1.
BUILD SUCCESSFUL in 48s
```
（仅剩既有的 Compose 插件告警：纯 JVM 模块 `buildFeatures.compose = false` 但约定插件强加了 compose 编译器插件——**这是基线就有的，不是 AGP 9 引入的**。）

### 步骤 3 — `:core:common:compileDebugKotlin`

```
> Task :core:common:compileDebugKotlin
BUILD SUCCESSFUL in 13s
```
→ **built-in Kotlin 真的在编译 Kotlin**（不是"插件加载了但没干活"）。同时出现一条与 AGP 9 无关的既有告警：`Warning: SDK processing. This version only understands SDK XML versions up to 3 but an SDK XML file of version 4 was encountered.`（android-37.0 的 `package.xml` 是 v4）——仅为告警，不阻塞。

### 步骤 4 — Room KSP + Apollo codegen

```bash
$ ./gradlew :core:database:compileDebugKotlin :core:github-graphql:compileDebugKotlin --max-workers=2
> Task :core:database:kspDebugKotlin                 # Room
> Task :core:github-graphql:generateGithubApolloSources   # Apollo codegen
> Task :core:github-graphql:kspDebugKotlin
> Task :core:github-rest:kspDebugKotlin
BUILD SUCCESSFUL in 1m 4s                            # 46 tasks
```
→ KSP 2.3.12 + AGP 9 的 `addKspConfigurations` 路径可用；Apollo 5.0.1 代码生成正常。仅有既有告警（schema 里 `ProjectCardArchivedState` 的 deprecated 枚举、`ExperimentalSerializationApi` opt-in）。

### 步骤 5 — `:app:compileDebugKotlin`（**全量编译，里程碑 5**）

```bash
$ ./gradlew :app:compileDebugKotlin --max-workers=2
> Task :app:kspDebugKotlin
> Task :app:compileDebugKotlin
BUILD SUCCESSFUL in 2m 46s                           # 270 actionable tasks
```
**25 个模块（app + core/* 14 + feature/* 10 + prototype）全部编译通过**，包括 Hilt 聚合、Compose 编译器、Sora 编辑器、Haze、Apollo、Room、Coil。
**零条 AGP 9 / built-in Kotlin / compileSdk 37 引入的新告警**；日志中全部 `w:` 都是既有告警（`TabRow` deprecated、`kotlinx.serialization` opt-in、Sora `createNoCompletion` deprecated、KT-73255 注解目标提示）。

### 步骤 6 — `:app:lintDebug` → **硬失败 ③（AGP 9 的新默认值）**

```bash
$ ./gradlew :app:lintDebug --max-workers=2
> Task :app:processDebugMainManifest FAILED
[com.composables:icons-material-symbols-rounded-android:2.2.1] .../AndroidManifest.xml Error:
	Namespace 'com.composables.icons.materialsymbols.rounded' is used in multiple modules and/or
	libraries: com.composables:icons-material-symbols-rounded-android:2.2.1,
	com.composables:icons-material-symbols-rounded-cmp-android-debug:2.2.1.
	Please ensure that all modules and libraries have a unique namespace.
* What went wrong:
Execution failed for task ':app:processDebugMainManifest'.
> Manifest merger failed with multiple errors, see logs
BUILD FAILED in 1m 12s
```
→ 对应 §3.5。**上游第三方库缺陷**：`com.composables` 把 base 与 cmp 两个 artifact 发成了同一个 namespace，而本项目**两个都需要**（base 提供 `MaterialSymbols` 命名空间对象，cmp 提供 `Cover` 用的 ImageVector 扩展属性 —— 见 `core/designsystem/build.gradle.kts` 注释与全仓 `import com.composables.*` 用法）。

修法（官方给的迁移期退出口）：`gradle.properties` 加
```properties
android.uniquePackageNames=false
```
> **注意**：`:app:compileDebugKotlin` **不会**触发这个错误（不走到 manifest merge）——只有 `lintDebug` / `assembleDebug` / `processDebugMainManifest` 才暴露。这是一个"编译绿但打包红"的陷阱。

### 步骤 6b — 复跑 `:app:lintDebug` → ✅ **里程碑 6 + #170 修复确认**

```
> Task :app:lintAnalyzeDebug
> Task :app:lintReportDebug
Wrote HTML report to .../app/build/reports/lint-results-debug.html
> Task :app:lintDebug
BUILD SUCCESSFUL in 4m 15s
```
对完整日志与报告文件全文检索，**`Library lint checks reference invalid APIs` / `these checks will be skipped` / `Lint found an issue registry` / `ObsoleteLintCustomCheck` / `Unknown issue id` 全部零命中** → **Compose 的 15 项检测器已恢复注册**（详见 §3.8 补充）。

### 步骤 7 — 质量门禁插件链（spotless / detekt）

见 §5.2。

### 步骤 7 — 质量门禁插件链（spotless / detekt）→ ✅ 通过

在删除探针、回滚依赖之后（同时验证"回滚干净"）：

```bash
$ ./gradlew :core:designsystem:compileDebugKotlin spotlessCheck detekt --max-workers=2
> Task :core:designsystem:compileDebugKotlin UP-TO-DATE   # ← 探针已彻底移除，回到探针前状态
> Task :spotlessKotlin
> Task :spotlessKotlinCheck
> Task :spotlessCheck
> Task :detekt
BUILD SUCCESSFUL in 48s
```
→ **spotless 8.9.0 + ktlint 1.8.0** 与 **detekt 1.23.8** 在 Gradle 9.3.1 + AGP 9.1.1 下均可用；且 `compileDebugKotlin` 显示 `UP-TO-DATE`，反证探针文件与依赖覆盖**已完全回滚**（工作树只剩预期改动）。

---

## 5. 已通过的里程碑 / 未验证的部分

### 5.1 已实测通过 ✅

| 里程碑 | 命令 | 结果 | 日志 |
|---|---|---|---|
| 基线 | `./gradlew help` | BUILD SUCCESSFUL 2m 3s | — |
| wrapper 解耦 | `./gradlew help`（Gradle 9.3.1 + AGP 8.7.3） | BUILD SUCCESSFUL 6m | `/tmp/agp9-step1*.log` |
| **配置阶段** | `./gradlew help`（AGP 9.1.1 + compileSdk 37） | BUILD SUCCESSFUL 48s | `/tmp/agp9-step2c.log` |
| 单模块编译 | `:core:common:compileDebugKotlin` | BUILD SUCCESSFUL 13s | `/tmp/agp9-step3-core-common.log` |
| Room + Apollo + KSP | `:core:database:compileDebugKotlin :core:github-graphql:compileDebugKotlin` | BUILD SUCCESSFUL 1m 4s | `/tmp/agp9-step4-codegen.log` |
| **全量编译** | `:app:compileDebugKotlin` | BUILD SUCCESSFUL 2m 46s（270 tasks / 25 模块） | `/tmp/agp9-step5-app.log` |
| **Android Lint** | `:app:lintDebug` | BUILD SUCCESSFUL 4m 15s，**0 registry 被跳过** | `/tmp/agp9-step6b-lint.log` |

### 5.2 质量门禁插件链验证 ✅

| 门禁 | 命令 | 结果 |
|---|---|---|
| ktlint 格式（spotless 8.9.0 + ktlint 1.8.0） | `./gradlew spotlessCheck` | ✅ BUILD SUCCESSFUL（与 detekt 合并构建 48s） |
| 静态分析（detekt 1.23.8） | `./gradlew detekt` | ✅ BUILD SUCCESSFUL |
| 回滚干净性反证 | `:core:designsystem:compileDebugKotlin` | ✅ `UP-TO-DATE`（探针彻底移除） |

→ 日志 `/tmp/agp9-step7-gates.log`。

### 5.3 **未验证**的部分（诚实清单）⚠️

以下门禁**本次没有实跑**，不得声称"全绿"：

| 门禁 | 命令 | 状态 | 风险判断 |
|---|---|---|---|
| 截图基准 | `:app:verifyRoborazziDebug` | **未跑** | Roborazzi 1.71.0 + Robolectric 4.16.1 在 AGP 9 下的兼容性未验证。**本机 `recordRoborazzi*` 是禁跑项**；`verify` 未跑（耗时长）。**这是剩余风险最高的一项** |
| 单测 | `:app:testDebugUnitTest` | **未跑** | Robolectric 沙箱 + AGP 9 的 `isIncludeAndroidResources` 行为未验证 |
| 覆盖率硬门禁 | `coverageReport coverageVerify` | **未跑** | 配置口径已确认未变（§3.8），但任务是否真能出 exec 数据未验证 |
| diff coverage | `diffCoverageCheck` | **未跑** | 纯自定义任务，依赖 `coverageReport` 输出 |
| 架构测试 | `konsistCheck` | **未跑** | 自定义 `Test` 任务，依赖 `compileDebugUnitTestKotlin`（依赖 built-in Kotlin 下的测试编译任务名，**有改名风险，需实测**） |
| 打 APK | `:app:assembleDebug` | **未跑** | 走完 manifest merge + dex + 打包；步骤 6 已证明 manifest merge 在加 flag 后可用。**建议优先补跑，性价比最高** |
| R8 / release | `:app:assembleRelease` | **未跑** | AGP 9 有 R8 变更（`-processkotlinnullchecks`、移除 `-addconfigurationdebugging`、`getDefaultProguardFile` 限制）。proguard-rules.pro 有大量 keep 规则，**需实测** |
| Baseline Profile | Baseline Profile 插件链 | **未跑** | 该插件的 AGP 9 兼容版本未核实 |
| detekt / konsist / spotless 版本 | — | 见 §5.2 | — |
| library 模块 lint | 各 `:core:*:lintDebug` | **未跑** | 约定插件里 `tasks.configureEach { if (name.startsWith("lint")) enabled = false }` 仍禁用全部 library lint 任务（AGP 8.7.3 时代的 crash 规避）。**AGP 9 下这个 hack 很可能可以删除，但未实测** |
| CI 实跑 | GitHub Actions | **未跑** | 未 push，CI 未触发；本机为腾讯/阿里镜像链，CI 直连官方源，**依赖解析路径不同** |

---

## 6. 阻塞点与建议

### 6.1 无硬阻塞点；一个需"上游修复"的软问题

`android.uniquePackageNames=false` 是**第三方库缺陷**的迁就，不是长久之计（AGP 10 会收紧）。三条彻底修法，按性价比排序：

1. **排查 `icons-material-symbols-rounded-android`（base）是否真的还需要** —— 全仓 `import com.composables.icons.materialsymbols.*` 里出现的都是具体图标名与 `MaterialSymbols`；base 与 cmp 两个 artifact 是否提供重复类、能否只留 cmp，需要一次依赖级排查（`./gradlew :core:designsystem:dependencies`）。**若能只留一个，本问题彻底消失。**
2. 给 `com.composables` 提 issue（两个 artifact 共用 namespace 是明确的打包缺陷）。
3. 若 1、2 都不可行 → 保留 flag，并在 AGP 10 迁移时重新评估。

### 6.2 建议：先做"兑现 #170"，再做 Expressive 组件

理由：lint 检测器**已经恢复加载**，此刻删掉 15 条 `disable` 是**零迁移风险的纯收益**，且会立刻暴露一批真实违规（`abortOnError = true`）。建议**独立成一票**（因为它会改动生产代码），完成后再开 Expressive 组件的票 —— 否则 Expressive 组件落地时会被同一批 lint 违规污染，难以归因。

### 6.3 建议：targetSdk 不要跟着 compileSdk 走

`targetSdk` 继续保持 35，独立成票做行为走查（分区存储 / 前台服务 / 通知权限 / 预测性返回）。本次已确认 AGP 9 的新默认值**不影响显式设置**。

### 6.4 建议：不要用 `android.newDsl=false` / `android.builtInKotlin=false` 退出

实测走正路的成本只有 6 个文件 / ~40 行，而退出开关 **AGP 10 会被移除**。参考：Flutter 作为生态方被迫用这两个开关过渡，但**单一应用项目没有这个必要**。

---

## 7. 若采用，后续 PR 拆分建议

| PR | 内容 | 依赖 | 风险 | 预估 |
|---|---|---|---|---|
| **PR1** | **工具链骨架**：Gradle 9.3.1 + AGP 9.1.1 + built-in Kotlin + 新 DSL 修复 + compileSdk 37 + buildTools 36.0.0 + KSP 2.3.12 + Hilt 2.59.2 + `android.uniquePackageNames=false` | — | 低（本报告已实测） | 0.5 天 |
| **PR2** | **门禁复验补齐**：`assembleDebug` / `assembleRelease`(R8) / `testDebugUnitTest` / `coverageVerify` / `konsistCheck` / `verifyRoborazziDebug` 逐个跑通并修复暴露的问题（**Roborazzi + R8 是两块硬骨头**） | PR1 | **中–高** | 1–2 天 |
| **PR3** | **兑现 #170**：删除 15 条 `disable +=` + 移除 library 模块的 lint 任务禁用 hack + 修违规 + 更新 CI "Lint detector coverage guard" 为「跳过数必须为 0」 | PR1（建议 PR2 后） | 中（会暴露真实违规） | 0.5–2 天 |
| **PR4** | **icons namespace 根治**：排查并去掉重复 artifact，去掉 `android.uniquePackageNames=false` | PR1 | 低 | 0.5 天 |
| **PR5** | **依赖版本对齐（可选）**：hilt-navigation-compose 1.2.0 → 1.4.0 + lifecycle 2.8.7 → 2.11.0/2.12.0；Haze 1.6.10 → 1.7.2（AGP 约束已解除）；markdown 0.38.1 → 0.43.0（compileSdk 37 已就位，可顺带删掉 `app/build.gradle.kts` 的 `AarMetadata` 任务禁用 hack） | PR1 | 中（行为变更需回归） | 0.5–1 天 |
| **PR6** | **material3 1.5.0-alpha 落地**（见 §8/§9） | PR1 + PR3 | 中（alpha 债） | 另估 |

> 建议 **PR1 独立先合**（它是所有后续项的前置），PR2/PR3 可并行，PR5/PR6 视产品节奏。

---

## 8. material3 1.5.0-alpha 解封验证

> 方法学前置：**判断 Kotlin 库成员的可用性，`javap` 不够** —— 它只看 JVM 可见性，看不到 Kotlin metadata 的 `internal` 标记。本节的结论一律由**真实 Kotlin 编译探针**得出（在 `core:designsystem` 临时放 `probe/ExpressiveProbe.kt` + 显式覆盖 material3 版本 → 编译 → 读 `e:` 错误 → **删除探针、回滚依赖**）。
> 一个有用的**快速信号**（可先于编译判断）：Kotlin `internal` 顶层/成员声明在 JVM 上会被**模块名修饰**（如 `standard$material3()`）；修饰消失即"疑似 public"。本节用它做了交叉印证，但**结论以编译探针为准**。

### 8.1 minCompileSdk / minAGP 边界（**从 AAR 元数据独立核实，非转述**）

直接从 Google Maven 下载 AAR，解出 `META-INF/com/android/build/gradle/aar-metadata.properties`：

| material3 版本 | `minCompileSdk` | `minAndroidGradlePluginVersion` | 能否在**旧**工具链（AGP 8.7.3 / compileSdk 36）用 |
|---|---|---|---|
| **1.4.0**（BOM 2026.06.01 当前 pin） | 35 | 8.6.0 | ✅ 当前基线 |
| **1.5.0-alpha18** | **35** | **8.6.0** | ✅ **可以**（`minCompileSdk=35`，未引入 `minCompileMinorSdk`） |
| **1.5.0-alpha19** | **37** | **9.1.0** | ❌ 不可以 |
| **1.5.0-alpha28**（最新） | **37** | **9.1.0** | ❌ 不可以 |

→ **alpha18 ↔ alpha19 之间确实存在硬断层**，与上游调研一致；且 **alpha18 的 `minAGP=8.6.0` 意味着它不需要 AGP 9**。

### 8.2 Expressive 符号可用性（真实 Kotlin 编译探针结果）

探针覆盖 9 组符号，`e:` 错误原文照录。判定规则：**`Cannot access ... : it is internal in file` = 不可用；`Unresolved reference` = 不存在；参数/调用形态类错误 = 符号可用**。

| 符号 | material3 **1.4.0**（对照） | **1.5.0-alpha18** | **1.5.0-alpha28** | 结论 |
|---|---|---|---|---|
| `MotionScheme`（接口类型） | ❌ `e: ...:15:35 Cannot access 'interface MotionScheme : Any': it is internal in file.` | ✅ 可访问 | ✅ 可访问 | **alpha18 起 public** |
| `MotionScheme.standard()` | ❌ `e: ...:38:57 Cannot access 'fun standard(): MotionScheme': it is internal in 'androidx.compose.material3.MotionScheme.Companion'.` | ✅ 可访问 | ✅ 可访问 | **alpha18 起 public**（注：是**函数**不是属性 —— 探针里属性形态报 `Function invocation 'standard()' expected.`） |
| `MotionScheme.expressive()` | ❌ 同上（`Cannot access 'fun expressive(): ...'`） | ✅ 可访问 | ✅ 可访问 | **alpha18 起 public** |
| `MaterialTheme.motionScheme` | ❌ `e: ...:43:66 Cannot access 'val motionScheme: MotionScheme': it is internal in 'androidx.compose.material3.MaterialTheme'.` | ✅ 可访问 | ✅ 可访问 | **alpha18 起 public** |
| `MaterialTheme(colorScheme=, motionScheme=, …)` 重载 | ❌ `e: ...:53:5 Cannot access 'fun MaterialTheme(...)': it is internal in file.` | ✅ 可访问 | ✅ 可访问 | **alpha18 起 public** |
| `MaterialExpressiveTheme {}` | ❌ `Cannot access 'fun MaterialExpressiveTheme(...)': it is internal in file.` | ✅ 可访问 | ✅ 可访问 | **alpha18 起 public** |
| `ExperimentalMaterial3ExpressiveApi`（注解） | ❌ `Cannot access 'annotation class ExperimentalMaterial3ExpressiveApi : Annotation': it is internal in file.` | ✅ 可访问 | ✅ 可访问 | **alpha18 起 public** |
| `LoadingIndicator` | ❌ `Unresolved reference 'LoadingIndicator'.` | ✅ 可访问 | ✅ 可访问 | alpha18 起存在且 public |
| `CircularWavyProgressIndicator` / `LinearWavyProgressIndicator` | ❌ `Unresolved reference`（**1.4.0 无此类**） | ✅ 可访问 | ✅ 可访问 | alpha18 起存在且 public |
| `SplitButtonLayout` | ❌ `Unresolved reference` | ✅ 可访问（报 `No value passed for parameter 'leadingButton' / 'trailingButton'`、`actual type is '() -> Unit', but 'Dp' was expected`） | ✅ 同 | alpha18 起存在且 public |
| `ButtonGroup` | ❌ `Unresolved reference` | ✅ 可访问 | ✅ 可访问（报 `No value passed for parameter 'overflowIndicator'`） | alpha18 起存在且 public |
| `FlexibleTopAppBar` | ❌ `Unresolved reference` | ❌ `Unresolved reference 'FlexibleTopAppBar'.` | ❌ 同 | **该名字在 1.5 线不存在**（见下） |
| `MediumFlexibleTopAppBar` / `LargeFlexibleTopAppBar` | （1.4.0 无） | （bytecode 存在） | ✅ **可访问**（报 `No value passed for parameter 'title'`） | **正确名称是 Medium/Large 两个变体** |
| `FloatingToolbar` | ❌ `Unresolved reference` | ❌ `Unresolved reference`（**alpha18 的 `FloatingToolbarKt` 里根本没有顶级函数**） | ❌ 同 | 该名字不存在 |
| `HorizontalFloatingToolbar` / `VerticalFloatingToolbar` | ❌ `Unresolved reference` | （bytecode 不存在） | ✅ **可访问**（报 `No value passed for parameter 'expanded'`） | **alpha28 才有；alpha18 无此 API** |

**两条对上游调研的修正（都属"名字/存在性"问题，不是可见性问题）**：
1. `FlexibleTopAppBar` 在 1.5 线**已被拆成 `MediumFlexibleTopAppBar` + `LargeFlexibleTopAppBar`**（javap 显示 `MediumFlexibleTopAppBar-eXZ4JBQ` / `LargeFlexibleTopAppBar-eXZ4JBQ`，且 alpha18 与 alpha28 都是这两个名字，**没有**裸 `FlexibleTopAppBar`）。上游把它列为"alpha18↔alpha19 门控翻转"的观察对象是**找错了符号名**。
2. `FloatingToolbar` 在 **alpha18 里不存在**（`FloatingToolbarKt` 只有 `access$…` 内部辅助与 `FloatingToolbarState`），**alpha28 才提供 `HorizontalFloatingToolbar` / `VerticalFloatingToolbar`** → **若目标是 FloatingToolbar 家族，必须 ≥ 某个 alpha19+（具体起点未逐版二分）**。

### 8.3 关键判据：**alpha18 就能拿到 MotionScheme 弹簧物理，且不需要 AGP 9**

这是本节最重要的发现，且与上游调研的"硬墙"叙事**部分冲突**：

> **`MotionScheme` / `MotionScheme.standard()` / `MotionScheme.expressive()` / `MaterialTheme.motionScheme` / `MaterialTheme(motionScheme=)` / `MaterialExpressiveTheme` 在 material3 `1.5.0-alpha18` 上全部 Kotlin-public，而 alpha18 的 `minCompileSdk=35` / `minAGP=8.6.0`。**

即：**"先零成本拿 Expressive 弹簧动效"这条捷径在 alpha18 上是存在的** —— 只是不能停在 1.4.0（1.4.0 里这些符号确实全是 `internal`，本报告用对照探针复现了上游的结论）。上游调研的 §"alpha01–alpha18 升得动"其实已经点到了这个窗口，但没有把"alpha18 的 Expressive 符号是否 public"验到底。

**但 alpha18 路线有三个必须写清的代价**（详见 §9）：
- alpha18 **没有** `HorizontalFloatingToolbar`/`VerticalFloatingToolbar`；`FlexibleTopAppBar` 在**任何** alpha 上都不存在（要用 `Medium/Large` 变体）。
- alpha18 是**一次性 alpha 债**：它不要求 compileSdk 37，所以**它不会逼你升 AGP 9**，但**它也不能长期停**——从 alpha19 起必须靠 AGP 9.1+ 才能前进，等于把同一次迁移推迟、且届时**同时**面对"alpha API 变动 + 工具链升级"两个变量。
- alpha 期间 API 会改名/改参数（本报告已实测到 `FlexibleTopAppBar` 消失、`FloatingToolbar` 被 `Horizontal/Vertical` 取代两个实例）→ **pin alpha 版本号是必须的**，且每次升 alpha 都要重跑这组探针。

### 8.4 版本 pin 策略与风险

| 项 | 建议 |
|---|---|
| 若采纳 alpha | **pin 精确版本号，不用 `+`/范围**；经 BOM 覆盖写法：`implementation(platform(libs.compose.bom))` + `implementation("androidx.compose.material3:material3:1.5.0-alphaNN")`（直接依赖的显式版本**优先于 BOM**，本次探针即用此写法生效） |
| 建议 pin 哪个 | **若决定走 AGP 9：取 `1.5.0-alpha28`**（最新，`FloatingToolbar` 家族齐全，且要求 compileSdk 37/AGP 9.1.0 —— 正好由 PR1 满足）。**若想不升 AGP 先拿动效：`1.5.0-alpha18`**，但接受没有 FloatingToolbar 家族 |
| 升级路径 | AGP 9 就位后，alpha19+ → **1.5.0 stable**（stable 只会更严，不会更松）→ 届时删掉显式版本、回到纯 BOM 管理 |
| 风险 | ① alpha API 破坏性改名（已实测 2 例）；② `compose-bom 2026.06.01` 与 `material3 1.5.0-alpha28` 混用（BOM 的 `material3` 是 1.4.0、compose-ui 是 1.11.4）—— **本次探针证明该组合可编译**，但这是"BOM 被局部推翻"的状态，长期应等 BOM 跟上；③ `material3-lint` 1.5.0-alpha 的 lint 检查器可能与 lint 32.1.1 有新互动（**未验证**） |
| 门控 | `@ExperimentalMaterial3ExpressiveApi` 在 alpha18 起才可用（1.4.0 里连注解都是 internal）→ 用到了就要全链路 `@OptIn`，会散落到多个 Composable |

---

## 9. 对「Expressive 落地路线」的建议

### 9.1 两条可选路线

| | **路线 A（推荐）：先 AGP 9，再上 alpha28** | **路线 B：先 pin alpha18 拿动效，AGP 9 推迟** |
|---|---|---|
| 拿到的能力 | MotionScheme 全套 + LoadingIndicator + 波浪进度 + SplitButton + ButtonGroup + **Horizontal/VerticalFloatingToolbar** + Medium/LargeFlexibleTopAppBar | MotionScheme 全套 + LoadingIndicator + 波浪进度 + SplitButton + ButtonGroup + Medium/LargeFlexibleTopAppBar，**无 FloatingToolbar 家族** |
| 前置成本 | PR1（0.5 天，**已实测可行**）+ PR2/PR3 | 几乎为零（只改一行依赖 + pin 版本） |
| 工具链债 | 一次付清（AGP 10 前必须做的事） | **推迟，且届时叠加 alpha API 变动** |
| 主要风险 | PR2 的 Roborazzi / R8 两块未验证 | alpha18 停在 8.6.0 era 的 API 面上，升 alpha19 时必须**先**升 AGP 9 → 顺序被锁定，无法并行 |
| 适合 | 想一次到位、不欠技术债 | 只想尽快在真机看到 Expressive 动效做设计验证 |

### 9.2 推荐次序（含一条**并行**的省钱做法）

1. **立刻可做（零成本、无依赖）**：在**一个独立分支**上 pin `material3 1.5.0-alpha18`，跑本报告 §8.2 的探针 + 真机看动效 → 用于**产品侧确认"要不要 Expressive"**。这一步不需要 AGP 9，**不阻塞任何事**。
2. **同时推进 PR1**（工具链骨架，本报告已实测）。
3. **PR1 合入后**：升级到 `1.5.0-alpha28`，补齐 FloatingToolbar 家族；**把 alpha18 分支废弃**（避免长期停在需要"未来再迁一次"的版本上）。
4. **PR3 兑现 #170** 之后再开 Expressive 组件的正式落地票 —— 否则 lint 违规与 Expressive 改动混在一起，归因困难。
5. **等 `1.5.0 stable`**：删掉显式 alpha 版本、回归纯 BOM，并把这组探针**固化成回归测试**（见 §9.3）。

### 9.3 建议固化：把这组探针变成资产

本次的 `ExpressiveProbe.kt` 已删除（未提交）。建议**把它的精简版固化成一个受版本控制的编译期断言**（例如放在 `core:designsystem` 的 `test` 源集或一个 `@Disabled` 的样例文件 + 注释），这样**每次升 alpha 时改一行版本号就能立刻知道哪些符号消失了** —— 本次实测到的两例改名（`FlexibleTopAppBar`、`FloatingToolbar`）说明这个回归成本是真实存在的。

---

## 10. 附：本 worktree 的改动清单（PR1 内容）

| 文件 | 改动 |
|---|---|
| `gradle/wrapper/gradle-wrapper.properties` | `gradle-8.12-bin.zip` → `gradle-9.3.1-bin.zip`（腾讯镜像，实测 200） |
| `buildSrc/build.gradle.kts` | `com.android.tools.build:gradle:8.7.3` → `9.1.1` |
| `buildSrc/src/main/kotlin/appdev.android.application.gradle.kts` | 删 `org.jetbrains.kotlin.android`；删 `KotlinAndroidProjectExtension` import 与 jvmTarget 块；`compileSdk 36→37`；`buildToolsVersion 35.0.0→36.0.0`；targetSdk 保持 35（含理由注释） |
| `buildSrc/src/main/kotlin/appdev.android.library.gradle.kts` | 同上（无 defaultConfig.targetSdk） |
| `build.gradle.kts`（根） | 两个 `extensions.configure<AppExtension/LibraryExtension>` + `configureJacocoVersion()` 合并为 `configureAndroidCoverage()`，改用非泛型 `CommonExtension` |
| `gradle/libs.versions.toml` | `agp 8.7.3→9.1.1`；`ksp 2.3.7→2.3.12`；`hilt 2.57.2→2.59.2`（含理由注释） |
| `gradle.properties` | 新增 `android.uniquePackageNames=false`（含理由注释与彻底修法指引） |

**未改动**：任何 `src/` 下的生产代码与测试代码、`settings.gradle.kts` 的仓库配置、`org.gradle.java.home`、Roborazzi 配置、JaCoCo 阈值表、CI workflow。

---

## 11. 参考来源

- [Android Gradle plugin 9.0.1 release notes](https://developer.android.com/build/releases/agp-9-0-0-release-notes) — 兼容表、breaking changes、Gradle 属性默认值翻转清单
- [Android Gradle plugin 9.1.1 release notes](https://developer.android.com/build/releases/agp-9-1-0-release-notes) — 支持 API 37.0、最低 Gradle 9.3.1、最低 JDK 17
- [Android Gradle plugin 9.4.0 release notes](https://developer.android.com/build/releases/agp-9-4-0-release-notes) — 最高 API 37、最低 Gradle 9.6.0、`android.newDsl.optOut`
- [About Android Gradle plugin](https://developer.android.com/build/releases/about-agp) — AGP↔Gradle 版本矩阵、AGP 9 Upgrade skill 入口
- [Migrate to built-in Kotlin](https://developer.android.com/build/migrate-to-built-in-kotlin) — 迁移四步、退出开关、`jvmTarget` 默认规则
- [AGP 9 migration skill](https://developer.android.com/agents/skills/build-system/agp/agp-9-upgrade/skill) — KSP ≥ 2.3.6、Hilt ≥ 2.59.2
- [AGP 9.1 `CommonExtension` API](https://developer.android.com/reference/tools/gradle-api/9.1/com/android/build/api/dsl/CommonExtension) — 非泛型、`testCoverage`、`enableKotlin`
- [AGP 9.1 `BuildType` API](https://developer.android.com/reference/tools/gradle-api/9.1/com/android/build/api/dsl/BuildType) — `enableUnitTestCoverage` 仍在
- [Update your Kotlin projects for AGP 9.0（JetBrains）](https://blog.jetbrains.com/kotlin/2026/01/update-your-projects-for-agp9)
- 各构件版本/AAR 元数据：`dl.google.com/dl/android/maven2`（AGP、material3）、`repo1.maven.org`（KSP、Hilt）实测查询
- 本仓：`docs/research/md3-expressive-2026-09-11.md`（Expressive 需求来源，其 `minCompileSdk/minAGP` 结论已由本报告 §8.1 从 AAR 元数据独立复核）
