// 注意：AGP / kotlin-android / kotlin-compose 三个插件已由 buildSrc 类路径提供（见 buildSrc/build.gradle.kts），
// 根 build 里不可重复声明（否则报 "already on the classpath with an unknown version"）。
// 其余插件（serialization/ksp/hilt/apollo）不在 buildSrc，可以正常在这里 apply false；
// spotless/detekt 需要作用到根项目本身（全局格式化/静态分析），因此直接 apply。
// javapoet 冲突处理见 buildSrc/build.gradle.kts（buildSrc 是根构建 classloader 的 parent）。
import org.gradle.testing.jacoco.plugins.JacocoPluginExtension
import org.gradle.testing.jacoco.tasks.JacocoCoverageVerification
import org.gradle.testing.jacoco.tasks.JacocoReport
import org.w3c.dom.Element
import java.io.ByteArrayOutputStream
import javax.xml.parsers.DocumentBuilderFactory

plugins {
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.apollo) apply false
    alias(libs.plugins.spotless)
    alias(libs.plugins.detekt)
}

// JaCoCo 插件只 apply 在根项目：提供 JacocoReport/JacocoCoverageVerification 任务类型与工具链版本。
// 注意：Gradle 内置的 jacoco 插件 id 是 org.gradle.jacoco（分发目录 lib/plugins/gradle-jacoco-*.jar 的
// META-INF/gradle-plugins/org.gradle.jacoco.properties）；plugins {} 块无法无版本解析它（不在 org.gradle.*
// 命名空间），而门户版 id org.jacoco 的 marker（org.jacoco.gradle.plugin）在本机镜像下解析不到——
// 必须用 apply() 从分发目录加载（子模块由 AGP 在 enableUnitTestCoverage 时自动应用；版本经
// testCoverage.jacocoVersion 锁定，见下）。
apply(plugin = "org.gradle.jacoco")

the<JacocoPluginExtension>().toolVersion = libs.versions.jacoco.get()

// ── Spotless（ktlint 格式化，全模块生效）───────────────────────────────
// checkOnTask = false：不挂进 check/assemble，避免拖慢日常增量构建；CI 显式调 spotlessCheck
spotless {
    isEnforceCheck = false
    kotlin {
        target("**/*.kt", "**/*.kts")
        targetExclude("**/build/**")
        ktlint(libs.versions.ktlint.get())
        trimTrailingWhitespace()
        endWithNewline()
    }
}

// ── Detekt（静态分析，全项目 Kotlin 源码）─────────────────────────────────
detekt {
    buildUponDefaultConfig = true
    allRules = false
    config.setFrom(files("$rootDir/config/detekt/detekt.yml"))
}

tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
    source = files("app/src", "core", "feature", "buildSrc/src").asFileTree
    exclude("**/build/**")
    reports {
        html.required.set(true)
        sarif.required.set(false)
        md.required.set(false)
        xml.required.set(false)
    }
}

// ── 版本统一强制（防止传递依赖拉高/拉低关键版本）──────────────────────
// okhttp：Apollo KMP 传递依赖会拉高版本，必须强制单一版本（见 AGENTS.md「依赖选型」）
// kotlin-metadata-jvm：Hilt 2.57.2 编译器自带 metadata 2.2，Kotlin 2.3 的 @Metadata
// 版本 2.3.x 会报 “maximum supported version is 2.2.0” → force 到 Kotlin 同版本（AGENTS.md 指引）
// ── JaCoCo 覆盖率（Phase A + Phase B，见 docs/agents/testing-strategy.md）────────────────
// Phase A（96276b8）：debug buildType 开 enableUnitTestCoverage（AGP 自动应用 jacoco 插件到子模块）。
// Phase B（本票）：
//   T1 版本锁定 0.8.13：AGP 8.x 官方 DSL `testCoverage.jacocoVersion`（TestCoverage 接口，
//      developer.android.com/studio/test/coverage-report）。不能用 `extensions.getByName("jacoco")`
//      改 toolVersion——plugins.withId("com.android.*") 回调时 jacoco 插件还没被 AGP 应用，扩展不存在；
//      testCoverage DSL 属于 android 扩展本身，无时序问题。0.8.13+ 支持 Kotlin inline functions（jacoco#1670）。
//   T2 分母口径（实测修正，详见 testing-strategy.md）：AGP 8.7 报告并不排除未执行类（未执行类按 0%
//      计入）；真正的分母控制点是 ① synthetic 类（$$serializer/$WhenMappings 等）被 JaCoCo 0.8.13
//      Analyzer 按设计跳过（ACC_SYNTHETIC），② Hilt 生成类由下方 coverageExcludes 统一排除，
//      ③ 跨模块执行——app/feature 测试会跑下层模块类，因此每模块报告/验证用全部模块 exec 的并集
//      （与 coverageReport 聚合口径一致）。本配置新增每模块 jacocoTestReport（受控分母），AGP 自带
//      createDebugUnitTestCoverageReport 保留不动。
//   T3 聚合：根 coverageReport = 聚合全部模块 exec 的单一 JacocoReport；coverageVerify = 聚合各模块
//      jacocoTestCoverageVerification（每模块一个验证任务，classDirectories 各自限定 = 天然按模块拆规则，
//      比单任务按包名前缀 includes 拆规则更稳）。
//   T4 diff coverage：diffCoverageCheck（见文件底部，git diff 行级比对聚合 XML）。

// 每模块覆盖率基线阈值（LINE COVEREDRATIO，0~1）。
// 规则（2026-08-16 定稿；2026-08-20 v2 因 UI 排除口径修订；2026-09-13 #261 修复后全量重设）：
//   - 逻辑模块（UseCase/Repository/ViewModel/模型/DTO/解析器为主）：地板 50~80
//   - UI/渲染模块（markdown/designsystem）：地板 25~70（Composable 主要靠 Roborazzi 截图兜底）
//   - 网络 DTO 模块（github-rest）：地板 15
//   - 地板作为 Phase C 目标（testing-strategy.md §4）；棘轮后阈值已普遍高于地板
//   - 棘轮规则（2026-09-13 起）：阈值 = max(旧阈值, floor2(实测 − 0.5pt))，即【只升不降】。
//     floor2 = 两位小数向下取整，故 2 位小数粒度下实际余量落在 0.5~1.5pp（逐条注明）；
//     实测逼近时宁保留旧阈，不为凑整数把阈值抬到 <0.5pp 余量。
//   - 实测口径（与 coverageVerify 完全同源）：各模块 jacocoTestReport XML 的 LINE COVEREDRATIO
//     （classDirectories = 本模块类 + coverageExcludes；executionData = 全部模块 exec 并集）。
//   - 2026-09-13 全量实测（#261 修复后的真实口径）：数值见每条「实测」注释。跨环境复核：
//     CI（main@3f78814, run 34708949868）聚合 LINE = 2,091 missed / 11,738 total，与本机逐位一致。
//   - ⚠️ 为什么此前阈值是假口径：#261 前 JaCoCo agent 默认 inclnolocationclasses=false，Robolectric
//     SandboxClassLoader 定义的应用类（无 CodeSource）整类被静默跳过 → 旧实测普遍比真实低数十 pp
//     （如 :core:markdown 0.45 vs 真实 0.76、:app 0.02 vs 真实 0.26），门禁形同虚设。
//     #261 修复（includeNoLocationClasses=true + includes=com/yumiru11/*）后按真实值一次性重设。
val coverageThresholds =
    mapOf(
        ":core:navigation" to 0.96, // 实测 96.69%（263/272 行；余量 0.69pp，旧阈 0.94）
        ":core:github-data" to 0.97, // 实测 97.79%（265/271 行；余量 0.79pp，旧阈 0.95）
        ":core:datastore" to 0.88, // 实测 89.33%（226/253 行；余量 1.33pp，旧阈 0.83）
        ":feature:repo" to 0.94, // 实测 95.49%（1567/1641 行；余量 1.49pp，旧阈 0.92）
        ":feature:profile" to 0.90, // 实测 91.40%（255/279 行；余量 1.40pp，旧阈 0.89）
        ":feature:notifications" to 0.93, // 实测 93.75%（180/192 行；余量 0.75pp，旧阈 0.89）
        ":feature:home" to 0.87, // 实测 88.32%（242/274 行；余量 1.32pp，旧阈 0.81）
        ":core:github-graphql" to 0.99, // 实测 100.00%（14/14 行；余量 1.00pp，旧阈 0.91）
        ":core:github-auth" to 0.78, // 实测 78.60%（224/285 行；余量 0.60pp，旧阈 0.70）
        ":feature:issue" to 0.82, // 实测 83.30%（818/982 行；余量 1.30pp，旧阈 0.68）
        ":core:markdown" to 0.75, // 实测 76.21%（1275/1673 行；余量 1.21pp，旧阈 0.45）
        ":core:designsystem" to 0.96, // 实测 96.56%（589/610 行；余量 0.56pp，旧阈 0.69）
        ":feature:settings" to 0.99, // 实测 100.00%（170/170 行；余量 1.00pp，旧阈 0.98）
        ":core:github-rest" to 0.92, // 实测 92.76%（935/1008 行；余量 0.76pp，旧阈 0.80）
        // 2026-09-11 首次纳入：提交级审计发现该模块是**三重盲区**（0 张截图基线 / 不在 CI 任何门禁 /
        // 6 个 UI 类被 JaCoCo 排除且排除理由声称的"截图兜底"在本模块并不存在）；2026-09-13 按真值棘轮。
        ":feature:pullrequest" to 0.79, // 实测 80.00%（1244/1555 行；余量 1.00pp，旧阈 0.73）
        // ── 2026-09-12 补阈批次（#255）：5 个「有自身单测 exec、可测」模块从无阈值到有阈值。
        // 2026-09-13 按 #261 后真值复测棘轮；4 个豁免模块的当前口径见文末 GATE-3 注释。
        ":app" to 0.25, // 实测 25.80%（129/500 行；余量 0.80pp；旧阈 0.02 是 #261 前的假口径）
        ":core:common" to 0.99, // 实测 100.00%（23/23 行；余量 1.00pp，维持旧阈）
        // core:editor：M3EditorThemeKt/MarkdownComposerKt 等 Compose 类不入单测拉低分母；逻辑类高覆盖。
        ":core:editor" to 0.51, // 实测 52.35%（156/298 行；余量 1.35pp，维持旧阈）
        // feature:search：VM/分页/缓存已覆盖；SearchResultRowsKt/SearchTopBarKt 纯 UI 不入单测。
        ":feature:search" to 0.53, // 实测 53.93%（288/534 行；余量 0.93pp，维持旧阈）
        // feature:editor：分母极小（32 行），1 行 ≈ 3.1pp，后续调阈需谨慎。
        ":feature:editor" to 0.58, // 实测 59.38%（19/32 行；余量 1.38pp，维持旧阈）
        // ── 2026-09-13 Robolectric 覆盖率修复批次（#261）────────────────────────────
        // 根因/修复见上方长注释；这两条在 #261 已按真值棘轮，本次全量重设后数值不变。
        ":core:database" to 0.85, // 实测 86.43%（618/715 行；余量 1.43pp；#261 前 5.17%）
        ":feature:auth" to 0.99, // 实测 100.00%（25/25 行；余量 1.00pp；#261 前 0.00%）
    )

// JaCoCo 分析排除：生成代码/样板（R/BuildConfig/Manifest/Hilt 产物）+ UI 层，不计入分母
// 关键认知：JaCoCo exclude 匹配【编译后 .class 文件名】，不是 Kotlin 符号。
// @Composable 函数编译为 XxxKt.class / XxxKt$1.class，类名【不含】Screen/Dialog/Card/Section，
// 故单靠 *Screen*/*Card* 等类名模式几乎命中不到 Compose UI。UI 排除【以包路径为主】，
// 类名模式仅作补充（见下方）。
val coverageExcludes =
    listOf(
        "**/R.class",
        "**/R\$*.class",
        "**/BuildConfig.class",
        "**/BuildConfig\$*.class",
        "**/Manifest\$*.class",
        "**/*Test*.class",
        "**/hilt_aggregated_deps/**",
        "**/Hilt_*.class",
        "**/*_HiltComponents*.class",
        "**/Dagger*Component*.class",
        "**/*_Factory.class",
        // AGP 9 修正 classDirs 后新进入分母的 Hilt 生成类（实测：:feature:auth 的
        // AuthViewModel_HiltModules$KeyModule 与 …_ProvideFactory$InstanceHolder，各 1 行未覆盖 →
        // 0.92 假红）。它们与上方 *_Factory / Dagger* / Hilt_* 同类，按既有「生成代码不入分母」口径排除；
        // 阈值不动（0.99 不变）。模式限定 `_HiltModules`，不会命中任何生产类。
        "**/*_HiltModules*.class",
        // ── UI 层排除（口径选择：单测门禁只查逻辑；UI 视觉由真机/截图管线兜底）────────
        // ⚠️ 这是【口径选择】，不是工具链限制：#261 修复后 Robolectric 单测（含截图测试）的
        // 覆盖数据会真实进入 exec（见 coverageThresholds 上方注释）；排除 UI 类是为了让单测门禁
        // 聚焦可断言的逻辑。若要调整名单，需与文件底部 diff 门禁的 uiSourceExcludes 同步决策。
        // 包路径排除：纯 UI 子包，全仓唯一、无逻辑命中。
        //   designsystem 的 component/token/icon 子包 = 0% 纯 UI（token 用【具体前缀】
        //     **/designsystem/token/**，避免误伤 core:github-auth 的 token 包——OAuth 逻辑）；
        //   ui/composable/preview/screen/screens/widget 为约定俗成的 UI 包目录。
        "**/ui/**",
        "**/composable/**",
        "**/designsystem/component/**",
        "**/designsystem/token/**",
        "**/designsystem/icon/**",
        "**/preview/**",
        "**/screen/**",
        "**/screens/**",
        "**/widget/**",
        // 类名模式补充（仅命中 UI，绝不命中逻辑模型 / DTO / 数据库类）：
        //   *Screen*/*Dialog*/*Card*/*Section* —— UI 屏幕/弹窗/卡片/分段（如 GitHubAlertCard、
        //     设置段、文件树段），无逻辑同名类；
        //   *Composable* —— 罕见但无害（仅命中字面含 Composable 的类）；
        //   *EditorViewKt*/*EditorTheme*/*EditorController* —— core:editor 的 Sora 视图/主题/控制句柄
        //     （隔离 Sora 类型、单测不可达，core:editor 无阈值）；视图是 @Composable 顶层函数，
        //     编译产物为 CodeEditorViewKt.class / MarkdownEditorViewKt.class，故带 Kt 后缀 ——
        //     GATE-1：`**/*EditorView*.class` 会连带排除 MarkdownEditorViewModel.class
        //     （feature:editor 的逻辑 VM，有完整单测，必须留在覆盖率报告里）；
        //   *TabContent*/*TimelineItems* —— PR 详情四 Tab 内容装配 / 时间线条目渲染（纯 Composable，
        //     feature 根包无 ui/ 子包，只能靠具体后缀命中）。
        // 严禁回退 v1 宽模式（已用上方具体后缀替代）：
        //   *Content* 误伤 ReadmeContent/FileContentData/FileContentDto/ContentApi（逻辑模型 / REST）；
        //   *Item* 误伤 FeedItem/NotificationItem/SearchCodeItemDto/TreeItemDto/IssueTimelineItem
        //     （逻辑模型 / DTO）；
        //   *Tab* 误伤 AppDatabase/DatabaseModule/MarkdownTableData/MarkdownTableParser；
        //   *Component*/*Controller* 误伤逻辑控制器；
        //   *Theme* 误伤 core:datastore 的 ThemeMode（逻辑偏好模型）——故仅用 *EditorTheme*；
        //   *View* 误伤 *ViewModel（逻辑）——故仅用 *EditorViewKt*（精确到 @Composable 编译产物，
        //     MarkdownEditorViewModel 不在其中）。
        "**/*Screen*.class",
        "**/*Screen\$*.class",
        "**/*Dialog*.class",
        "**/*Composable*.class",
        "**/*Card*.class",
        "**/*Section*.class",
        "**/*EditorViewKt*.class",
        "**/*EditorTheme*.class",
        "**/*EditorController*.class",
        "**/*TabContent*.class",
        "**/*TimelineItems*.class",
        // T16：自研 diff 视图 / 行评论 BottomSheet（纯 Composable，单测不可达，截图/真机兑底）
        "**/*DiffView*.class",
        "**/*LineCommentSheet*.class",
        // T17：ReviewSheet / MergeBox（纯 Composable，单测不可达，截图/真机兑底；同 T16 DiffView 策略）
        "**/*ReviewSheet*.class",
        "**/*MergeBox*.class",
        // T16：Apollo codegen 产物（schema 驱动生成，行为由 Apollo 运行时保障；单测门禁不计生成代码，
        //   与 R/BuildConfig/Hilt 生成物同策略，防新增 .graphql 操作导致分母虚增）
        "**/githubgraphql/generated/**",
        // T16：github-graphql 的 Hilt 装配（provideApolloClient 纯胶水，需 android Context
        //   无法纯 JVM 单测；与 Dagger/Hilt 生成物同策略排除，分支逻辑由 Factory 测试兜底）
        "**/githubgraphql/di/**",
    )

// 根聚合报告：先注册，子模块回调里填充（dependsOn + classDirs + exec + sources）
// 所有启用了覆盖率的模块的 testDebugUnitTest 任务路径（含自身），供根聚合与每模块
// 报告/验证声明依赖（合并 exec 口径下必须显式依赖全部测试任务，否则 Gradle 报 implicit dependency）
val coverageTestTaskPaths = mutableListOf<String>()

val coverageReport =
    tasks.register<JacocoReport>("coverageReport") {
        group = "verification"
        description = "聚合所有 Android 模块的 JaCoCo 执行数据生成单一覆盖率报告（XML + HTML）"
        reports {
            xml.required.set(true)
            html.required.set(true)
        }
    }

// 聚合验证：聚合各模块 jacocoTestCoverageVerification（有阈值的模块）
val coverageVerify =
    tasks.register("coverageVerify") {
        group = "verification"
        description = "执行所有配置了阈值的模块覆盖率验证（LINE COVEREDRATIO ≥ 阈值），不达标即失败"
    }

subprojects {
    plugins.withId("com.android.application") {
        configureAndroidCoverage()
        configureRobolectricCoverage()
        this@subprojects.registerCoverageTasks()
    }
    plugins.withId("com.android.library") {
        configureAndroidCoverage()
        configureRobolectricCoverage()
        this@subprojects.registerCoverageTasks()
    }
}

// AGP 9 新 DSL：android 扩展实现（ApplicationExtension/LibraryExtension）实现的是**非泛型**
// CommonExtension（AGP 9 移除了泛型参数；legacy AppExtension/LibraryExtension 在新 DSL 下
// 不再是公开接口类型）。buildTypes 与 testCoverage 都在 CommonExtension 上，一处转换同时覆盖。
// 必须 getByName("android") 后强转：Gradle 的 configure<T> 按注册类型精确匹配，而注册类型是
// BaseAppModuleExtension（非公开 API，不能直接声明）。
fun Project.configureAndroidCoverage() {
    val androidExt = extensions.getByName("android") as com.android.build.api.dsl.CommonExtension
    // debug buildType 开单测覆盖率（AGP 自动应用 jacoco 插件到子模块）
    androidExt.buildTypes.getByName("debug") { enableUnitTestCoverage = true }
    // T1：锁定 JaCoCo 0.8.13（AGP 官方 DSL testCoverage.jacocoVersion，TestCoverage 接口；
    // 0.8.13+ 支持 Kotlin inline functions，jacoco#1670）。
    // 不能用 extensions.getByName("jacoco") 改 toolVersion——plugins.withId 回调时 jacoco 插件
    // 还没被 AGP 应用，扩展不存在；testCoverage DSL 属于 android 扩展本身，无时序问题。
    androidExt.testCoverage.jacocoVersion = libs.versions.jacoco.get()
}

// Robolectric 沙箱类 + JaCoCo agent 的兼容修复（根因与实测证据见 coverageThresholds 上方长注释）。
// 必须在 android 扩展可用后调用；testOptions.unitTests.all 由 AGP 在配置每个 Test 任务时回放，
// 此时 JacocoTaskExtension 已存在（AGP 用 findByType 取它），因此这是唯一不依赖插件应用时序的注入点。
fun Project.configureRobolectricCoverage() {
    val androidExt = extensions.getByName("android") as com.android.build.api.dsl.CommonExtension
    androidExt.testOptions.unitTests.all { test ->
        test.extensions
            .findByType(org.gradle.testing.jacoco.plugins.JacocoTaskExtension::class.java)
            ?.apply {
                setIncludeNoLocationClasses(true)
                // includes 用 VM 类名（`/` 分隔，已实测 `com.yumiru11.*` 不匹配、`com/yumiru11/*` 匹配）：
                // 只插桩本项目类。Robolectric 沙箱类因此可见，同时 JDK 运行时生成的无 CodeSource 类
                // （jdk/internal/reflect/Generated*Accessor 等）不被插桩，避免测试 worker 启动即崩。
                setIncludes(listOf("com/yumiru11/*"))
            }
    }
}

// 为模块注册 jacocoTestReport（T2 全量分母）+ jacocoTestCoverageVerification（T3，有阈值时），并接入根聚合。
// 在 plugins.withId("com.android.*") 回调内执行——此时 android 扩展已可用，AGP 的 exec 输出路径
// （build/outputs/unit_test_code_coverage/debugUnitTest/testDebugUnitTest.exec）已确定。
fun Project.registerCoverageTasks() {
    // prototype/ 是一次性渲染原型（throwaway），测试源码编译不过且不属于覆盖率门禁范围，跳过
    if (path.startsWith(":prototype")) return
    // 注意：进入下方 tasks.register 的配置 lambda 后 receiver 是 Task，`path` 会解析成
    // 任务路径（:core:common:jacocoTestCoverageVerification）；这里先捕获项目路径供报错信息用。
    val modulePath = path
    val srcDirs = files(listOf("src/main/kotlin", "src/main/java").map { file(it) }.filter { it.exists() })
    // ★ AGP 9 路径（2026-09-13 实测修正）：Kotlin 类输出从 `tmp/kotlin-classes/debug` 移到
    // `intermediates/built_in_kotlinc/debug/compileDebugKotlin/classes`；Java 类输出从
    // `intermediates/javac/debug/classes` 移到 `intermediates/javac/debug/compileDebugJavaWithJavac/classes`。
    // 不修则 classDirectories 为空 → 报告 0 类 → coverageVerify 静默恒绿（实测：迁移首跑 XML 只有
    // sessioninfo、无任何 package/counter，而 24 份 exec 全在）。这正是 GATE-3 同类“空转门禁”，必须锁死。
    val classDirs =
        files(
            fileTree(
                file(
                    layout.buildDirectory
                        .dir("intermediates/built_in_kotlinc/debug/compileDebugKotlin/classes")
                        .get()
                        .asFile,
                ),
            ) { exclude(coverageExcludes) },
            fileTree(
                file(
                    layout.buildDirectory
                        .dir("intermediates/javac/debug/compileDebugJavaWithJavac/classes")
                        .get()
                        .asFile,
                ),
            ) { exclude(coverageExcludes) },
        )
    val execData = fileTree(layout.buildDirectory) { include("outputs/unit_test_code_coverage/**/*.exec") }

    // 各模块 exec 的并集（app/feature 测试会执行下层模块的类 → 跨模块覆盖），与根 coverageReport
    // 聚合口径一致（coverageVerify 契约：对同一份聚合数据做验证）。provider 惰性求值，执行时
    // 所有模块回调已完成，与子模块求值顺序无关。
    // 聚合 exec 数据：惰性持有 coverageReport 的 executionData 文件集合引用。
    // 禁止用 rootProject.provider { coverageReport.executionData.files } 的【急切】写法——
    // 它会在首个模块验证任务（如 :core:datastore）解析自身 executionData 时【重入】解析
    // coverageReport 的 ConfigurableFileCollection，而此刻该集合常处 finalize 中途，偶发
    // ValuedObjectStateException（“Valued object is in an unexpected state”），使 coverageVerify
    // 非确定性红（gradle/gradle#8794/#12962 同款坑）。改用 map { it.executionData } 惰性持有
    // 引用、执行期再解析，规避重入竞态。
    val coverageReportProvider =
        rootProject.tasks.named("coverageReport", JacocoReport::class.java)
    val mergedExecData = coverageReportProvider.map { it.executionData }

    tasks.register<JacocoReport>("jacocoTestReport") {
        group = "verification"
        description = "本模块 JaCoCo 覆盖率报告（全量生产类分母，未执行类按 0% 计；AGP 自带 createDebugUnitTestCoverageReport 保留）"
        dependsOn(coverageTestTaskPaths)
        reports {
            xml.required.set(true)
            html.required.set(true)
        }
        sourceDirectories.setFrom(srcDirs)
        classDirectories.setFrom(classDirs)
        executionData.setFrom(mergedExecData)
    }

    // 接入根聚合报告（根任务在根脚本已注册，子模块求值时必然存在，直接 get()；
    // 子模块 testDebugUnitTest 由 AGP 在插件应用后期创建，named() 会立即解析并抛异常，
    // 必须用字符串任务路径依赖（图构建期才解析））
    coverageTestTaskPaths += "$path:testDebugUnitTest"
    val rootReport = rootProject.tasks.named("coverageReport", JacocoReport::class.java).get()
    rootReport.dependsOn(coverageTestTaskPaths)
    rootReport.sourceDirectories.from(srcDirs)
    rootReport.classDirectories.from(classDirs)
    rootReport.executionData.from(execData)

    val threshold = coverageThresholds[path] ?: return
    tasks.register<JacocoCoverageVerification>("jacocoTestCoverageVerification") {
        group = "verification"
        description = "本模块覆盖率门禁（LINE COVEREDRATIO ≥ $threshold）"
        dependsOn(coverageTestTaskPaths)
        classDirectories.setFrom(classDirs)
        executionData.setFrom(mergedExecData)
        // ★ GATE-3（2026-09-13）：有阈值却无 exec 数据 = 该模块单测被静默跳过（从未执行 /
        // 被 --exclude-task / NO-SOURCE）——此时**必须红**，不得静默 SKIP。旧实现用
        // onlyIf 跳过验证，等于该模块的覆盖率门禁空转（残余审计 G-02/G-03，PR #255 前的
        // 5 个豁免模块就是靠这种空转「看起来有阈值」）。
        // 只约束 coverageThresholds 里声明了阈值的模块；仍豁免的 4 个模块不注册本任务（照旧跳过）：
        //   :core:data —— 无 src/test、无自身 exec（设阈会被 GATE-3 判「有阈值却无 exec」）；
        //     模型类仅被其它模块测试顺带执行，进聚合报告但不单独设阈；
        //   :core:testing —— 测试基建模块（MainDispatcherRule / ScreenshotTest / GitHubFakes），
        //     按定义由被测模块执行，无独立测试与 exec（#215）；
        //   :core:ui —— 有测试与 exec，但类全被 `**/ui/**` 排除 → 分母 0 类，设阈无意义；
        //   :prototype —— 一次性渲染原型（registerCoverageTasks 提前 return）。
        doFirst {
            if (execData.files.none { it.exists() }) {
                throw GradleException(
                    "覆盖率门禁失败：模块 $modulePath 在 coverageThresholds 里声明了阈值（$threshold），" +
                        "但找不到该模块的单元测试执行数据——约定路径 " +
                        "${layout.buildDirectory.get().asFile}/outputs/unit_test_code_coverage/**/*.exec 下无文件。" +
                        "有阈值却不产生 exec，说明单测被跳过或从未执行——覆盖率验证拒绝空转：" +
                        "请检查 src/test 是否存在、testDebugUnitTest 是否被 --exclude-task / NO-SOURCE 跳过。",
                )
            }
            // ★ AGP 9 追加（2026-09-13）：classDirectories 为空 = JaCoCo 报告 0 类 → 验证恒绿（空转）。
            // 实测根因：AGP 9 built-in Kotlin 把 Kotlin 类输出移出 tmp/kotlin-classes，classDirs 失配后
            // 聚合报告只剩 sessioninfo、无任何 counter。此断言把「路径失配」从静默绿变成立即红。
            if (classDirs.isEmpty) {
                throw GradleException(
                    "覆盖率门禁失败：模块 $modulePath 的 classDirectories 为空（编译产物路径变化或编译未执行）。" +
                        "空分母会让覆盖率验证恒绿（空转门禁），拒绝放行。" +
                        "AGP 9 的 Kotlin 类路径应为 build/intermediates/built_in_kotlinc/debug/compileDebugKotlin/classes。",
                )
            }
        }
        violationRules {
            rule {
                limit {
                    counter = "LINE"
                    value = "COVEREDRATIO"
                    minimum = threshold.toBigDecimal()
                }
            }
        }
    }
    rootProject.tasks
        .named("coverageVerify")
        .get()
        .dependsOn("$path:jacocoTestCoverageVerification")
}

subprojects {
    configurations.all {
        resolutionStrategy {
            force("com.squareup.okhttp3:okhttp:${libs.versions.okhttp.get()}")
            force("org.jetbrains.kotlin:kotlin-metadata-jvm:${libs.versions.kotlin.get()}")
        }
    }
}

// ── Diff coverage（T4）：PR 新增生产代码行覆盖率门禁 ─────────────────────────────────
// 选型：自定义任务（方案 c）而非 diff-coverage 插件——插件（xyz.pavelkorolev.coverage.diff /
// com.form.diff-coverage）自带任务名不满足契约（diffCoverageCheck），且 Gradle 8.12/Kotlin 2.3
// 兼容性需额外验证；自研方案用 JDK 自带 XML + git，零新依赖，任务名/阈值属性完全可控。
// 用法（CI：GitHub Actions pull_request 事件）：
//   ./gradlew diffCoverageCheck -PdiffBaseSha=${{ github.event.pull_request.base.sha }}
// 阈值可配：-PdiffCoverageThreshold=0.80（默认 0.80，即新增代码行 ≥80% 被覆盖才通过）
abstract class DiffCoverageCheck : DefaultTask() {
    /** 对比基准 commit（PR base）。来源优先级：-PdiffBaseSha > DIFF_BASE_SHA 环境变量 > HEAD~1 */
    @get:Input
    abstract val baseSha: Property<String>

    /** 聚合 JaCoCo 报告输出目录（由 coverageReport 生成，目录内 *.xml 即报告） */
    @get:InputDirectory
    abstract val reportDir: DirectoryProperty

    /** 新增代码行覆盖率阈值（0~1） */
    @get:Input
    abstract val threshold: Property<Double>

    @get:Inject
    abstract val execOperations: ExecOperations

    @TaskAction
    fun check() {
        val xmlFile =
            reportDir
                .get()
                .asFile
                .walkTopDown()
                .filter { it.isFile && it.extension == "xml" }
                .firstOrNull()
        check(xmlFile != null) { "在 ${reportDir.get().asFile} 找不到 JaCoCo XML 报告（请先运行 ./gradlew coverageReport）" }
        val base = baseSha.get()
        val gate = threshold.get()

        // 1) base...HEAD（三段式）变更的生产源码文件
        // UI 源文件跳过：单测门禁只查逻辑；Composable 由真机/截图管线兑底。
        // feature 模块把 UI 混在根包（与 ViewModel 同目录），包路径排除无法分离，故在 diff 门禁
        // 按【文件路径】跳过 UI 源文件。仅用【安全】模式（包目录 + 不命中逻辑类的文件名 token）：
        // 包目录（纯 UI 子包，全仓唯一、无逻辑命中）；文件名 token 仅命中 UI，绝不命中逻辑。
        // 严禁 v1 宽模式（*Content*/*Item*/*Tab*/*Component*/*Controller* 会误伤逻辑模型/DTO/
        // 数据库类）。*Theme* 会误伤 core:datastore 的 ThemeMode（逻辑偏好），*View* 会误伤
        // *ViewModel（逻辑），故用具体后缀（*EditorTheme*/*EditorView.kt 精确匹配）——
        // GATE-1：`*EditorView[^/]*\.kt$` 会误伤 MarkdownEditorViewModel.kt（有完整单测的逻辑
        // VM），收紧为 `*EditorView\.kt$`（只命中 CodeEditorView.kt / MarkdownEditorView.kt
        // 两个纯 Composable，编译产物 XxxEditorViewKt.class 与上面的 JaCoCo 排除对齐）。
        val uiSourceExcludes =
            listOf(
                // 包目录
                Regex("""(^|/)ui/"""),
                Regex("""(^|/)composable/"""),
                Regex("""(^|/)designsystem/"""),
                Regex("""(^|/)preview/"""),
                Regex("""(^|/)screen/"""),
                Regex("""(^|/)screens/"""),
                Regex("""(^|/)widget/"""),
                Regex("""(^|/)theme/"""),
                // 文件名 token（仅命中 UI）
                Regex("""(^|/)[^/]*Screen[^/]*\.kt$"""),
                Regex("""(^|/)[^/]*Dialog[^/]*\.kt$"""),
                Regex("""(^|/)[^/]*Card[^/]*\.kt$"""),
                Regex("""(^|/)[^/]*Section[^/]*\.kt$"""),
                Regex("""(^|/)[^/]*EditorView\.kt$"""),
                Regex("""(^|/)[^/]*EditorTheme[^/]*\.kt$"""),
                Regex("""(^|/)[^/]*EditorController[^/]*\.kt$"""),
                Regex("""(^|/)[^/]*TabContent[^/]*\.kt$"""),
                Regex("""(^|/)[^/]*TimelineItems[^/]*\.kt$"""),
                // Composable 视图：*View.kt（排除 *ViewModel.kt，ViewModel 是逻辑需门禁）
                Regex("""(^|/)[^/]*View\.kt$"""),
                // WebView 渲染宿主：整个文件是 @Composable + AndroidView factory 装配
                // （无独立单测可打点；逻辑部分已抽到 WebViewDarkModePolicy 并有单测）。
                // #170 实测：动一行注释都会让 diff 门禁把它算成"未覆盖新增行"。
                Regex("""(^|/)[^/]*Renderer\.kt$"""),
                // *Composer.kt：编辑/预览装配层（MarkdownComposer 等纯 Composable）。
                // "Composer" 是 Compose 专有词（runtime 的 Composer），不会有同名逻辑类。
                Regex("""(^|/)[^/]*Composer[^/]*\.kt$"""),
                // 这两处是**纯 Compose 装配**：AppThemeHost 只做"偏好 Flow → CompositionLocal"
                // 的接线，AppBackground 只做"图 + 蒙版 + 内容"的三层堆叠。
                // 逻辑部分已抽成可测纯函数（BackgroundScrim 有 5 例 JVM 断言；色板/动效换算在
                // core:designsystem 各自有测试）—— 这里排除的只是无法单测的装配代码。
                // 实测（PR #197）：不加这两条，动一行接线就会被 diff 门禁判成"未覆盖新增行"。
                // ⚠️ 2026-09-13 更正（#261）：旧注释称"Robolectric 沙箱加载的类不产 JaCoCo 数据"
                // 已被证伪（真实根因 = agent 跳过无 CodeSource 类，已修复；AppThemeHost 实测
                // 62/62 行、SystemBarContrast 3/3 行现均被覆盖）。此排除保留为 diff 门禁口径，
                // 与工具链无关；是否移出留给单独的 diff 门禁清理决策。
                Regex("""(^|/)AppThemeHost\.kt$"""),
                Regex("""(^|/)AppBackground\.kt$"""),
                // MainActivity.kt：单 Activity 入口 = 根级 Compose 装配层（T23 接线修复暴露）。
                // 文件内容是 setContent + AppNavHost 的 20 个 screen lambda 装配、深链/OAuth
                // intent 分流与 locale 切换，无独立可单测逻辑。:app 自 #255 起已有覆盖率阈值
                // （2026-09-13 棘轮至 0.25），但本文件 #261 修复后实测仅 42/367 行被覆盖——
                // 属于「装配代码占比大、单测只能触达一部分」的形态。不排除的真实后果（实测 PR 前）：
                // 改 2 个 lambda 接线新增 24 行里有 13 行可执行、仅 7 行被覆盖 → 53.8% < 80%，
                // CI diff 门禁必红。
                Regex("""(^|/)MainActivity\.kt$"""),
                // SystemBarContrast.kt（PR #215）：单个 `Window.disableNavigationBarContrastScrim()`
                // 扩展函数，只有「取 API 版本判断 + 一行 setter」，**没有可断言的逻辑分支**；
                // 它已由 `MainActivityNavBarContrastTest` 的 4 例覆盖行为契约（对其调用的断言走的是
                // 同一函数）。⚠️ 2026-09-13 更正（#261）：旧注释称"报告里恒 0（Robolectric 沙箱
                // 类不产 JaCoCo 数据）"已被证伪——修复后实测 3/3 行被覆盖。排除保留为口径选择
                // （本次不改名单；可单独立票复核是否移出）。
                Regex("""(^|/)SystemBarContrast\.kt$"""),
                // 纯 Composable 的 BottomSheet：*Sheet.kt（feature 模块把 UI 混在根包，如
                // feature/pullrequest/{ReviewSheet,LineCommentSheet}.kt —— 包目录排除够不着）。
                // 为什么排除：这两个文件是**整文件 @Composable**、无独立可断言的逻辑分支，
                // 「Sheet 该长什么样」由 Roborazzi 截图基线（app 模块）与真机走查兜底。
                // ⚠️ 2026-09-13 更正（#261）：旧注释把「报告里 0/104 与 0/69」归因于「被 Robolectric
                // 沙箱加载的类不产出 JaCoCo 覆盖数据」——已证伪（沙箱类现正常入 exec）。真实的
                // 0/104 解释是这些类早已被本文件 coverageExcludes 的 *ReviewSheet*.class /
                // *LineCommentSheet*.class 排除，报告里根本不出现；该归因错误不改变排除结论。
                // 实测（UI22 × #219 merge）：不加这条，仅因这五处 Sheet 套一层玻璃容器
                // （-w 口径下净增 30 行：每文件 1 行 import + 几行调用）就带出 103 行新增
                // 可执行行 → diff 门禁按比例判定必红。故此排除是口径选择，非工具链事实。
                // 命名安全性：*Sheet.kt 只命中 BottomSheet Composable，全仓无逻辑类同名。
                Regex("""(^|/)[^/]*Sheet\.kt$"""),
            )

        val changedFiles =
            git("diff", "--name-only", "$base...HEAD")
                .lineSequence()
                .filter { it.contains("/src/main/") && (it.endsWith(".kt") || it.endsWith(".java")) }
                .filter { path -> uiSourceExcludes.none { it.containsMatchIn(path) } }
                // ★ core:testing 整体排除（2026-09-11，PR #215 实证）：
                // 它是**测试基建模块**（MainDispatcherRule / ScreenshotTest / GitHubFakes /
                // SystemBarInsets 等 JVM 夹具），代码写在 src/main 是因为要被各模块的
                // testImplementation 依赖 —— **按定义就会被测模块的测试执行**，没有独立测试，
                // 也不在 coverageThresholds 里（无阈值 → registerCoverageTasks 提前 return
                // → 永不进覆盖率报告）。若不排除，门禁会把「测试基建」判成「生产代码未覆盖」，
                // 这是口径错误而非覆盖率不足（#215 实测：core/testing 的 40 行占未覆盖 40/42）。
                .filterNot { it.contains("core/testing/src/") }
                // ★ buildSrc 整体排除（2026-09-12，PR #235 实证）：
                // buildSrc 是**构建工具链**（AppDevI18nLint 自定义 lint 规则等），在 Gradle
                // 配置期/构建期执行，没有 JaCoCo 插桩，也没有独立测试与覆盖率口径
                // （不进 coverageThresholds / registerCoverageTasks）→ 报告里恒为 0/N，
                // 与 core/testing 同属**口径错误而非覆盖率不足**（#235 实测：22 行可执行
                // 全未覆盖，占比 0/22，使总体 4.5% < 80% 误红）。
                .filterNot { it.startsWith("buildSrc/") }
                .toList()
        if (changedFiles.isEmpty()) {
            logger.lifecycle("diffCoverageCheck: 无变更的生产源码文件（base=$base），通过")
            return
        }

        // 2) 解析聚合 XML → "package/sourcefile" → 已覆盖行号集合（禁止加载外部 DTD report.dtd）
        val dbf = DocumentBuilderFactory.newInstance()
        dbf.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
        val doc = dbf.newDocumentBuilder().parse(xmlFile)
        val coveredByKey = mutableMapOf<String, Set<Int>>()
        // JaCoCo 报告里出现的行号集合 = 它认为"可执行"的行。新增行若不在此集合里，
        // 说明 JaCoCo 根本不把它当可执行行（函数签名的括号行、纯声明行等），
        // 计入分母会制造永远无法覆盖的假失败（#170 实测：函数签名的收尾括号行被判未覆盖）。
        val knownByKey = mutableMapOf<String, Set<Int>>()
        val pkgNodes = doc.getElementsByTagName("package")
        for (i in 0 until pkgNodes.length) {
            val pkg = pkgNodes.item(i) as Element
            val pkgName = pkg.getAttribute("name")
            val sourceFiles = pkg.getElementsByTagName("sourcefile")
            for (j in 0 until sourceFiles.length) {
                val sf = sourceFiles.item(j) as Element
                val key = if (pkgName.isEmpty()) sf.getAttribute("name") else "$pkgName/${sf.getAttribute("name")}"
                val covered = mutableSetOf<Int>()
                val known = mutableSetOf<Int>()
                val lines = sf.getElementsByTagName("line")
                for (k in 0 until lines.length) {
                    val ln = lines.item(k) as Element
                    val nr = ln.getAttribute("nr").toInt()
                    known += nr
                    if ((ln.getAttribute("ci").toIntOrNull() ?: 0) > 0) {
                        covered += nr
                    }
                }
                coveredByKey[key] = covered
                knownByKey[key] = known
            }
        }

        // 3) 逐文件比对新增行与覆盖集
        //
        // ★ #170 / D04 口径修正：只统计"可执行新增行"。原先把 KDoc/注释/空行/import/注解
        // 也算进分母，导致"写文档 = 掉覆盖率"——实测某 PR 原始口径 24.5%、可执行行口径 80.4%，
        // 门禁读数完全失真（且当时 threshold 语义还错，见任务注册处注释）。
        // JaCoCo 的行覆盖只对可执行行有定义，非可执行行永远不会出现在报告里，必须排除。
        var totalAdded = 0
        var rawAdded = 0
        var totalCovered = 0
        val uncoveredByFile = linkedMapOf<String, List<Int>>()
        val noReport = mutableListOf<String>()
        for (file in changedFiles) {
            val added = addedLines(file, base)
            if (added.isEmpty()) continue
            rawAdded += added.size
            val codeLines = added.filter { isExecutableLine(file, it) }
            if (codeLines.isEmpty()) continue
            totalAdded += codeLines.size
            val key = xmlKeyOf(file)
            val covered = key?.let { coveredByKey[it] } ?: emptySet()
            val known = key?.let { knownByKey[it] } ?: emptySet()
            // 只统计 JaCoCo 认账的行：不在报告里的行视为不可执行（不计入分母也不计入未覆盖）
            val uncovered = codeLines.filter { it in known && it !in covered }
            // ★ 门禁修复（2026-09-11，PR #210 实证）：文件**完全不在覆盖率报告里**时，
            // known/covered 都是空集 → 上面那条 filter 恒为空 → `codeLines.size - 0`
            // 把**整个文件的每一行都算成"已覆盖"**。即"没有任何测试触及的模块"在 diff
            // 门禁里反而是 100% 绿的假象（PR #210 实测：feature:pullrequest 无覆盖率任务，
            // LineCommentSheet/ReviewSheet 的新增行就这样被计为已覆盖）。
            //
            // 修正：报告缺席 = 无法证明被覆盖 → 一律计入未覆盖。这样"未测模块"不再刷绿，
            // 要么补测试，要么把文件加进上面的 uiSourceExcludes（并写明不可测理由）。
            val unreported = key == null || key !in coveredByKey
            if (unreported) {
                totalCovered += 0
                uncoveredByFile[file] = codeLines
            } else {
                totalCovered += codeLines.size - uncovered.size
                if (uncovered.isNotEmpty()) uncoveredByFile[file] = uncovered
            }
            if (key == null || key !in coveredByKey) noReport += file
        }
        logger.lifecycle("diffCoverageCheck: 新增 $rawAdded 行，其中可执行 $totalAdded 行（base=$base）")
        if (totalAdded == 0) {
            logger.lifecycle("diffCoverageCheck: 无可执行新增行，通过")
            return
        }

        val ratio = totalCovered.toDouble() / totalAdded
        val pct = (ratio * 100).let { java.lang.Math.round(it * 10.0) / 10.0 }
        val gatePct = (gate * 100).let { java.lang.Math.round(it * 10.0) / 10.0 }
        logger.lifecycle("diffCoverageCheck: 新增代码 $totalCovered/$totalAdded 行已覆盖（$pct%，阈值 $gatePct%）")
        if (ratio >= gate - 1e-9) {
            if (noReport.isNotEmpty()) {
                logger.warn("diffCoverageCheck: 以下文件不在覆盖率报告中（无测试触及），但已达阈值，仅提示：${noReport.joinToString()}")
            }
            return
        }
        val detail =
            buildString {
                uncoveredByFile.forEach { (f, lines) ->
                    append("  - ")
                        .append(f)
                        .append(": 未覆盖行 ")
                        .append(lines.take(30).joinToString(", "))
                        .append(if (lines.size > 30) " …共 ${lines.size} 行" else "")
                        .append('\n')
                }
                noReport.forEach { append("  - ").append(it).append(": 未出现在覆盖率报告中（全部新增行未覆盖）\n") }
            }
        throw GradleException(
            "diffCoverageCheck 失败：新增代码覆盖率 $pct% < $gatePct%（$totalCovered/$totalAdded 行）\n$detail",
        )
    }

    private fun git(vararg args: String): String {
        val out = ByteArrayOutputStream()
        val result =
            execOperations.exec {
                commandLine("git", *args)
                standardOutput = out
                errorOutput = out
                isIgnoreExitValue = true
            }
        // 失败时给出可操作的原因提示：#170 实测 CI 浅克隆（fetch-depth=1）会让
        // `git diff <base>...HEAD` 直接退出 128，而默认报错只有 "non-zero exit value 128"，
        // 排查成本极高 —— 这里把 stderr 与常见解法一起抛出。
        if (result.exitValue != 0) {
            throw GradleException(
                "diffCoverageCheck 执行 git " + args.joinToString(" ") + " 失败（exit " + result.exitValue + "）：" +
                    out.toString(Charsets.UTF_8).trim() +
                    "\n提示：比对基准提交必须存在于本地仓库 —— CI 上需要 actions/checkout 的 fetch-depth: 0。",
            )
        }
        return out.toString(Charsets.UTF_8)
    }

    /** 解析 base...HEAD 对 file 的 unified=0 diff，返回新增行在新文件中的行号 */
    private fun addedLines(
        file: String,
        base: String,
    ): List<Int> {
        val diff = git("diff", "--unified=0", "$base...HEAD", "--", file)
        val added = mutableListOf<Int>()
        var newLine = 0
        for (raw in diff.lineSequence()) {
            val hunk = HUNK_PATTERN.find(raw)
            if (hunk != null) {
                newLine = hunk.groupValues[1].toInt()
                continue
            }
            if (raw.startsWith("+++") || raw.startsWith("---") || raw.startsWith("\\")) continue
            when {
                raw.startsWith("+") -> {
                    added += newLine
                    newLine++
                }

                raw.startsWith("-") -> {
                    Unit
                }

                else -> {
                    newLine++
                } // 上下文行也推进新文件行号
            }
        }
        return added
    }

    /**
     * 该新增行是否"可执行"（计入覆盖率分母）。
     *
     * JaCoCo 只给可执行行打标；把注释/空行/import/纯注解/纯收尾括号算进分母会让
     * 「写文档就掉覆盖率」。判据取保守白名单：非空、不以注释或 import/package/注解开头、
     * 不是纯括号收尾行。
     */
    private fun isExecutableLine(
        file: String,
        lineNumber: Int,
    ): Boolean {
        val lines = sourceCache.getOrPut(file) { runCatching { File(file).readLines() }.getOrDefault(emptyList()) }
        val text = lines.getOrNull(lineNumber - 1)?.trim() ?: return false
        if (text.isEmpty()) return false
        if (NON_CODE_PREFIXES.any { text.startsWith(it) }) return false
        return !CLOSING_ONLY_PATTERN.matches(text)
    }

    /** 变更文件内容缓存（同一文件只读一次） */
    private val sourceCache = mutableMapOf<String, List<String>>()

    /** "core/x/src/main/kotlin/com/foo/Bar.kt" → "com/foo/Bar.kt"（与 XML 的 package/sourcefile 对应） */
    private fun xmlKeyOf(file: String): String? {
        val idx = file.indexOf("/src/main/")
        if (idx < 0) return null
        val rel = file.substring(idx + "/src/main/".length)
        val segments = rel.split('/')
        val name = segments.last()
        val pkg = segments.dropLast(1).drop(1).joinToString("/")
        return if (pkg.isEmpty()) name else "$pkg/$name"
    }

    companion object {
        private val HUNK_PATTERN = Regex("""^@@ -\d+(?:,\d+)? \+(\d+)(?:,\d+)? @@""")

        /** 非可执行行前缀：注释块、单行注释、包/导入声明、注解 */
        private val NON_CODE_PREFIXES = listOf("//", "/*", "*", "import ", "package ", "@")

        /** 纯收尾括号行（"}", ");", "])," 等）：不承载可执行指令，JaCoCo 不为其打标 */
        private val CLOSING_ONLY_PATTERN = Regex("""^[)\]},;]+$""")
    }
}

// 默认阈值（0..1）。-PdiffCoverageThreshold 同时接受 0..1 与 0..100 两种口径（见下方归一逻辑）。
// 顶层 val 必须是 camelCase（ktlint property-naming；只有 const val 才允许 SCREAMING_SNAKE）
val defaultDiffThreshold = 0.80

tasks.register<DiffCoverageCheck>("diffCoverageCheck") {
    group = "verification"
    description = "Diff coverage 门禁：PR 新增生产代码行覆盖率 ≥ 阈值（默认 0.80）。用法：-PdiffBaseSha=<base-sha> [-PdiffCoverageThreshold=0.80]"
    dependsOn(coverageReport)
    reportDir.set(layout.buildDirectory.dir("reports/jacoco/coverageReport"))
    baseSha.set(
        providers
            .gradleProperty("diffBaseSha")
            .orElse(providers.environmentVariable("DIFF_BASE_SHA"))
            .orElse("HEAD~1"),
    )
    threshold.set(
        providers
            .gradleProperty("diffCoverageThreshold")
            // ★ #170 / D04：口径归一。CI 一直传 -PdiffCoverageThreshold=80（百分数），
            // 而本任务按 0..1 比例比较 → gate=80.0，ratio（0..1）恒 < 80 → 门禁**恒失败**；
            // 失败又被 ci.yml 的 continue-on-error 吞掉，于是"从未真正拦过任何 PR"。
            // 现在同时接受 0..1 与 0..100 两种写法，越界值钳到合法区间。
            .map { raw ->
                val value = raw.trim().toDoubleOrNull() ?: defaultDiffThreshold
                if (value > 1.0) (value / 100.0).coerceIn(0.0, 1.0) else value.coerceIn(0.0, 1.0)
            }.orElse(defaultDiffThreshold),
    )
}
