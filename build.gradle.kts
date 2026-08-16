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

// 每模块覆盖率基线阈值（LINE COVEREDRATIO，0~1）。规则（2026-08-16 实测后定稿）：
//   - 逻辑模块（UseCase/Repository/ViewModel 为主）：地板 50~80，视实测收紧
//   - UI/渲染模块：地板 25（Composable 主要靠 Roborazzi 截图兜底，不设高单测门禁）
//   - 网络 DTO 模块：地板 15（DTO 反序列化样板占比高）
//   - app / feature/auth：豁免（纯 UI 装配，无规则，只进聚合报告）
//   - 阈值 = max(地板, 实测 - 2pt)，保证 CI 今天能过；实测 < 地板的模块（feature/repo、profile、
//     notifications、home、issue、settings）取实测 - 2pt，地板作为 Phase C 目标（testing-strategy.md §4）
//   - 实测口径：coverageReport 聚合数据（全部模块 exec 合并，含 app/feature 测试对下层模块的
//     跨模块执行；全量非 synthetic 生产类分母，Hilt 生成代码已排除）
val coverageThresholds =
    mapOf(
        ":core:navigation" to 0.94, // 实测 96.1%
        ":core:github-data" to 0.94, // 实测 96.1%
        ":core:datastore" to 0.84, // 实测 86.7%
        ":feature:repo" to 0.37, // 实测 39.0%（地板 70，Phase C 目标）
        ":feature:profile" to 0.29, // 实测 30.9%（地板 60，Phase C 目标）
        ":feature:notifications" to 0.28, // 实测 30.2%（地板 60，Phase C 目标）
        ":feature:home" to 0.28, // 实测 30.7%（地板 60，Phase C 目标）
        ":core:github-graphql" to 0.90, // 实测 92.5%
        ":core:github-auth" to 0.70, // 实测 72.5%
        ":feature:issue" to 0.21, // 实测 23.7%（地板 50，Phase C 目标）
        ":core:markdown" to 0.42, // 实测 44.2%
        ":core:designsystem" to 0.64, // 实测 66.7%
        ":feature:settings" to 0.17, // 实测 19.6%（地板 25，Phase C 目标）
        ":core:github-rest" to 0.76, // 实测 78.2%
    )

// JaCoCo 分析排除：生成代码/样板（R/BuildConfig/Manifest/Hilt 产物），不计入分母
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
        extensions.configure<com.android.build.gradle.AppExtension> {
            buildTypes.getByName("debug") { enableUnitTestCoverage = true }
        }
        configureJacocoVersion()
        this@subprojects.registerCoverageTasks()
    }
    plugins.withId("com.android.library") {
        extensions.configure<com.android.build.gradle.LibraryExtension> {
            buildTypes.getByName("debug") { enableUnitTestCoverage = true }
        }
        configureJacocoVersion()
        this@subprojects.registerCoverageTasks()
    }
}

// T1：锁定 JaCoCo 0.8.13（AGP 官方 DSL testCoverage.jacocoVersion，TestCoverage 接口）。
// testCoverage 在 CommonExtension（新 DSL 接口）上，legacy 的 AppExtension/LibraryExtension 没有；
// 且 Gradle 的 configure<CommonExtension> 按注册类型精确匹配（android 扩展注册为 BaseAppModuleExtension），
// 必须 getByName("android") 后强转（plugins.withId 回调里 jacoco 插件尚未应用，无法改 toolVersion）。
fun Project.configureJacocoVersion() {
    val androidExt = extensions.getByName("android") as com.android.build.api.dsl.CommonExtension<*, *, *, *, *, *>
    androidExt.testCoverage.jacocoVersion = libs.versions.jacoco.get()
}

// 为模块注册 jacocoTestReport（T2 全量分母）+ jacocoTestCoverageVerification（T3，有阈值时），并接入根聚合。
// 在 plugins.withId("com.android.*") 回调内执行——此时 android 扩展已可用，AGP 的 exec 输出路径
// （build/outputs/unit_test_code_coverage/debugUnitTest/testDebugUnitTest.exec）已确定。
fun Project.registerCoverageTasks() {
    // prototype/ 是一次性渲染原型（throwaway），测试源码编译不过且不属于覆盖率门禁范围，跳过
    if (path.startsWith(":prototype")) return
    val srcDirs = files(listOf("src/main/kotlin", "src/main/java").map { file(it) }.filter { it.exists() })
    val classDirs =
        files(
            fileTree(
                file(
                    layout.buildDirectory
                        .dir("tmp/kotlin-classes/debug")
                        .get()
                        .asFile,
                ),
            ) { exclude(coverageExcludes) },
            fileTree(
                file(
                    layout.buildDirectory
                        .dir("intermediates/javac/debug/classes")
                        .get()
                        .asFile,
                ),
            ) { exclude(coverageExcludes) },
        )
    val execData = fileTree(layout.buildDirectory) { include("outputs/unit_test_code_coverage/**/*.exec") }

    // 各模块 exec 的并集（app/feature 测试会执行下层模块的类 → 跨模块覆盖），与根 coverageReport
    // 聚合口径一致（coverageVerify 契约：对同一份聚合数据做验证）。provider 惰性求值，执行时
    // 所有模块回调已完成，与子模块求值顺序无关。
    val mergedExecData =
        rootProject.provider {
            rootProject.tasks
                .named("coverageReport", JacocoReport::class.java)
                .get()
                .executionData.files
        }

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
        // 无单元测试的模块没有 exec 数据，跳过验证（没有测试可测）
        onlyIf("存在单元测试执行数据") { execData.files.any { it.exists() } }
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
        val changedFiles =
            git("diff", "--name-only", "$base...HEAD")
                .lineSequence()
                .filter { it.contains("/src/main/") && (it.endsWith(".kt") || it.endsWith(".java")) }
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
        val pkgNodes = doc.getElementsByTagName("package")
        for (i in 0 until pkgNodes.length) {
            val pkg = pkgNodes.item(i) as Element
            val pkgName = pkg.getAttribute("name")
            val sourceFiles = pkg.getElementsByTagName("sourcefile")
            for (j in 0 until sourceFiles.length) {
                val sf = sourceFiles.item(j) as Element
                val key = if (pkgName.isEmpty()) sf.getAttribute("name") else "$pkgName/${sf.getAttribute("name")}"
                val covered = mutableSetOf<Int>()
                val lines = sf.getElementsByTagName("line")
                for (k in 0 until lines.length) {
                    val ln = lines.item(k) as Element
                    if ((ln.getAttribute("ci").toIntOrNull() ?: 0) > 0) {
                        covered += ln.getAttribute("nr").toInt()
                    }
                }
                coveredByKey[key] = covered
            }
        }

        // 3) 逐文件比对新增行与覆盖集
        var totalAdded = 0
        var totalCovered = 0
        val uncoveredByFile = linkedMapOf<String, List<Int>>()
        val noReport = mutableListOf<String>()
        for (file in changedFiles) {
            val added = addedLines(file, base)
            if (added.isEmpty()) continue
            totalAdded += added.size
            val key = xmlKeyOf(file)
            val covered = key?.let { coveredByKey[it] } ?: emptySet()
            val uncovered = added.filter { it !in covered }
            totalCovered += added.size - uncovered.size
            if (uncovered.isNotEmpty()) uncoveredByFile[file] = uncovered
            if (key == null || key !in coveredByKey) noReport += file
        }
        if (totalAdded == 0) {
            logger.lifecycle("diffCoverageCheck: 变更文件无新增行（base=$base），通过")
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
        execOperations.exec {
            commandLine("git", *args)
            standardOutput = out
            errorOutput = out
            isIgnoreExitValue = false
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
    }
}

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
            .map { it.toDouble() }
            .orElse(0.80),
    )
}
