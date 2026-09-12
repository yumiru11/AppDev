import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    // compileSdk 36：OkHttp 5.4（okhttp-android）AAR 要求 minCompileSdk 36（2026-08-12 实测）；
    // android-36 平台已从腾讯镜像安装（本机无 dl.google.com 访问）；AGP 8.7.3 对 36 仅警告不阻塞
    compileSdk = 36
    buildToolsVersion = "35.0.0"

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    lint {
        abortOnError = true
        // Compose/Lifecycle 库内 lint 检测器禁用：根因与解除条件见 :app 的 lint 块详细注释
        // （#170 / Q01：compose-ui lint jar 链接了更老的 Kotlin Analysis API，需 AGP 9.x 才能跑）。
        // 已移除两个不存在的 id（ComposableCoroutineCreation / UnrememberedState）——
        // lint 会为它们报 UnknownIssueId（#170 / Q02）。
        disable += "AutoboxingStateCreation"
        disable += "AutoboxingStateValueProperty"
        disable += "ComposableLambdaParameterNaming"
        disable += "ComposableNaming"
        disable += "CompositionLocalNaming"
        disable += "FlowOperatorInvokedInComposition"
        disable += "FrequentlyChangingValue"
        disable += "MutableCollectionMutableState"
        disable += "OpaqueUnitKey"
        disable += "ProduceStateDoesNotAssignValue"
        disable += "RememberInComposition"
        disable += "UnrememberedAnimatable"
        disable += "UnrememberedMutableState"
        disable += "NullSafeMutableLiveData"
        // 下面两条是 2026-09-12 实测补上的（library 模块恢复 lint 后暴露）：
        // 这两个检测器在 library 的 lint 分析里执行到一半抛
        // `IncompatibleClassChangeError: Found class KaFunctionCall, but interface was expected`
        // （同一个 #170 根因：Compose lint jar 针对更新版 Kotlin Analysis API 编译），
        // 且是**致命**的（:app 靠「整个 UiIssueRegistry 被跳过」侥幸躲过，library 没有这层保护）。
        // lint 自己的崩溃提示就建议 disable 这两个 id；disable 生效后检测器不再被执行。
        // 与 :app 的差异（app 有 StateFlowValueCalledInComposition、这里此前漏了）已在此对齐。
        disable += "CoroutineCreationDuringComposition"
        disable += "StateFlowValueCalledInComposition"

        // ── i18n 规则（plan.md §11.4 / 需求审计 §9.4）─────────────────────────────
        // 用户可见文案 99% 在 feature/core，所以这四条**必须配在约定插件**才对全模块生效。
        // 契约与「与另外两道 i18n 守卫的分工」见 buildSrc/src/main/kotlin/AppDevI18nLint.kt。
        // 下一行同时是配置期自检：被删掉 → 任何 Gradle 调用都直接失败（不留静默降级的口子）。
        enableI18nRules()
        verifyI18nRulesEnabled(project.path)
    }
}

extensions.configure<KotlinAndroidProjectExtension>("kotlin") {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

// ── library 模块的 lint 任务已恢复启用（2026-09-12，本票实测）────────────────────────────
//
// 历史：这里曾有一段 `tasks.configureEach { if (name.startsWith("lint")) enabled = false }`，
// 理由是「AGP 8.7.3 的 LintJarApiMigration 与新版 Compose AAR 内置 lint 检查器二进制不兼容
// （IncompatibleClassChangeError），崩溃发生在检测器注册/迁移期，disable 规则无法绕过」，
// 于是库模块的 lint 任务被整体关掉、质量门禁只跑 `:app:lintDebug`。
//
// 实测结论（本票，AGP 8.7.3 + Gradle 8.12）：
// 1. 该崩溃在 **:app 上同样发生、且同样是非致命**的 —— `:app:lintAnalyzeDebug` 会打印
//    `java.lang.NegativeArraySizeException` 于 `LintJarApiMigration.migrateClassNames`，
//    但 lint 继续跑完并写出报告（BUILD SUCCESSFUL）。library 模块同理，不再需要禁用任务。
// 2. 真缺口被这次实测抓到：`:app:lintDebug` **不分析依赖模块**（`checkDependencies` 默认 false，
//    其 lint model 的 resDirectories/javaDirectories 只含 app 自己的 `src/{main,debug}/…`）。
//    在 `feature:home` 放一条「en 有 zh 无」的字符串 + 一个硬编码 XML 布局，`:app:lintDebug`
//    报告里 0 命中；`:feature:home:lintDebug` 才报出来。→ 「只跑 app lint」= feature/core 的
//    资源与源码完全不进任何门禁（需求审计 §10 P1）。
//
// 因此恢复库模块 lint 任务；`.github/workflows/ci.yml` 的 lint 步骤也相应扩到全模块。
