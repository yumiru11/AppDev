package com.yumiru11.githubapp.konsist

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.container.KoScope
import com.lemonappdev.konsist.api.declaration.KoFileDeclaration
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 架构护栏测试（Konsist，分层依赖方向）。
 *
 * CI 的 `./gradlew konsistCheck` 通过 :app `testDebugUnitTest` 过滤
 * `com.yumiru11.githubapp.konsist.*` 包执行本类，违规即 CI 失败。
 *
 * 分层约束（plan.md §10.1）：
 * 1. core 模块不得依赖 feature 模块
 * 2. core:github-*（graphql/rest/auth/data）只依赖网络与模型，不得依赖 Compose / core UI
 * 3. feature 模块互不引用（只可通过 core:navigation 深链）；其推论前提是
 *    「模块目录名 = 包名段」，故另有显式断言：feature 内 .kt 文件 package 必须以
 *    com.yumiru11.githubapp.feature.<模块目录名> 开头（防包名漂移导致规则 3 漏检）
 * 4. 模型层禁止 import android.* —— 工单措辞为「core:model 禁止 import android 包」，
 *    实际骨架无独立 core:model 模块，故落地为：对承载纯数据模型的
 *    core:data 与 core:github-* 中 model 包施加该规则（未来建立 core:model 后平移）
 * 5. app 是装配层，可依赖一切，不施加限制
 *
 * 作用域用 KoScope 按模块路径限定（scopeFromDirectory），只扫 .kt 源文件。
 *
 * 测试命名规范：methodName_scenario_expectedBehavior。
 */
class ArchitectureTest {
    @Test
    fun scopeResolution_projectRootLocated_coreScopeIsNotEmpty() {
        // 防「Konsist 根目录解析失败 → 作用域为空 → 所有规则空转恒绿」的静默失效
        val files = Konsist.scopeFromDirectory("core").files
        assertTrue("Konsist should locate the project root and scan the core modules", files.isNotEmpty())
    }

    @Test
    fun coreModules_importFeaturePackages_areForbidden() {
        val violations =
            kotlinFiles(coreScope())
                .forbiddenImportViolations(FEATURE_PACKAGE_PREFIX)

        assertNoViolations("core modules must not depend on any feature module", violations)
    }

    @Test
    fun githubCoreModules_importComposeOrUiPackages_areForbidden() {
        val forbidden = listOf(ANDROIDX_COMPOSE_PACKAGE_PREFIX) + CORE_UI_PACKAGE_PREFIXES
        val violations =
            kotlinFiles(Konsist.scopeFromDirectories(GITHUB_CORE_MODULE_PATHS))
                .forbiddenImportViolations(*forbidden.toTypedArray())

        assertNoViolations("core:github-* must only depend on network and models (no Compose/UI)", violations)
    }

    @Test
    fun featureModules_importOtherFeatureModules_areForbidden() {
        val violations =
            kotlinFiles(featureScope()).flatMap { file ->
                val ownPackagePrefix = "$FEATURE_PACKAGE_PREFIX.${file.featureModuleName()}"
                file.imports
                    .filter { it.matchesAnyPrefix(FEATURE_PACKAGE_PREFIX) && !it.name.startsWith("$ownPackagePrefix.") }
                    .map { violation(file, it.name) }
            }

        assertNoViolations("feature modules must not depend on each other (use core:navigation deep links)", violations)
    }

    @Test
    fun featureModules_packageNaming_mustMatchModuleDirectory() {
        // 规则 3 依赖「目录名 = 包名段」约定，此处把约定变成显式检查：
        // feature/<module> 内的 .kt 文件 package 必须以 com.yumiru11.githubapp.feature.<module> 开头
        val violations =
            kotlinFiles(featureScope()).mapNotNull { file ->
                val moduleName = file.featureModuleName()
                val expectedPrefix = "$FEATURE_PACKAGE_PREFIX.$moduleName"
                val packageName = file.packagee?.name.orEmpty()
                val matches = packageName == expectedPrefix || packageName.startsWith("$expectedPrefix.")
                if (moduleName.isEmpty() || !matches) {
                    "${file.path}: package '$packageName' must start with '$expectedPrefix'"
                } else {
                    null
                }
            }

        assertNoViolations("feature module packages must be named com.yumiru11.githubapp.feature.<module dir>", violations)
    }

    @Test
    fun modelPackages_importAndroidPackages_areForbidden() {
        val violations =
            kotlinFiles(Konsist.scopeFromDirectories(MODEL_HOST_MODULE_PATHS))
                .filter { it.isModelPackageFile() }
                .forbiddenImportViolations(ANDROID_PACKAGE_PREFIX)

        assertNoViolations("model layer must not depend on the Android framework", violations)
    }

    // ── 作用域与匹配辅助 ────────────────────────────────────────────────────

    private fun coreScope(): KoScope = Konsist.scopeFromDirectory("core")

    private fun featureScope(): KoScope = Konsist.scopeFromDirectory("feature")

    /** 只保留 .kt 源文件（.kts 构建脚本不参与 import 规则；用 path 判定，KoFileDeclaration.name 不含扩展名） */
    private fun kotlinFiles(scope: KoScope): List<KoFileDeclaration> = scope.files.filter { it.path.endsWith(".kt") }

    /** 收集文件中命中任一禁用包前缀的 import，格式化为可读违规描述 */
    private fun List<KoFileDeclaration>.forbiddenImportViolations(vararg forbiddenPrefixes: String): List<String> =
        flatMap { file ->
            file.imports
                .filter { it.matchesAnyPrefix(*forbiddenPrefixes) }
                .map { violation(file, it.name) }
        }

    /** import 包名命中前缀：完全相等或以「前缀.」开头（避免 android/androidx 之类误判）。
     *  注意：KoImportDeclaration 用 name 取导入的全限定包名（path 是所在文件路径） */
    private fun com.lemonappdev.konsist.api.declaration.KoImportDeclaration.matchesAnyPrefix(vararg prefixes: String): Boolean =
        prefixes.any { name == it || name.startsWith("$it.") }

    /** 文件属于 model 包（如 com.yumiru11.githubapp.core.data.model） */
    private fun KoFileDeclaration.isModelPackageFile(): Boolean = packagee?.name?.split(".")?.contains("model") == true

    /** 从文件路径推导所属 feature 模块目录名（feature/<module>/src/...），空串表示无法识别（保守禁全部 feature import） */
    private fun KoFileDeclaration.featureModuleName(): String {
        val segments = path.replace("\\", "/").split("/")
        val featureIndex = segments.indexOf("feature")
        return segments.getOrNull(featureIndex + 1).orEmpty()
    }

    private fun violation(
        file: KoFileDeclaration,
        importPath: String,
    ): String = "${file.path}: import $importPath"

    private fun assertNoViolations(
        rule: String,
        violations: List<String>,
    ) {
        assertTrue(
            "$rule:\n" + violations.joinToString(separator = "\n"),
            violations.isEmpty(),
        )
    }

    private companion object {
        const val FEATURE_PACKAGE_PREFIX = "com.yumiru11.githubapp.feature"
        const val ANDROIDX_COMPOSE_PACKAGE_PREFIX = "androidx.compose"
        const val ANDROID_PACKAGE_PREFIX = "android"

        val CORE_UI_PACKAGE_PREFIXES =
            listOf(
                "com.yumiru11.githubapp.core.ui",
                "com.yumiru11.githubapp.core.designsystem",
            )

        val GITHUB_CORE_MODULE_PATHS =
            listOf(
                "core/github-graphql",
                "core/github-rest",
                "core/github-auth",
                "core/github-data",
            )

        val MODEL_HOST_MODULE_PATHS = listOf("core/data") + GITHUB_CORE_MODULE_PATHS
    }
}
