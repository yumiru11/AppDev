plugins {
    id("com.android.library")
    // AGP 9.0 built-in Kotlin：org.jetbrains.kotlin.android 不再需要（应用会硬失败）。
    // 详见 appdev.android.application.gradle.kts 的注释与错误原文。
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    // compileSdk 37：AGP 9.1.x 支持的最高 API 级；android-37.0 本机已安装。
    compileSdk = 37
    // SDK Build Tools 36.0.0：AGP 9.x 的最低版本（35.0.0 低于下限）
    buildToolsVersion = "36.0.0"

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    // built-in Kotlin 下 jvmTarget 默认取 compileOptions.targetCompatibility（= 17），无需显式设置。

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
    }
}

// AGP 8.7.3 的 LintJarApiMigration 与新版 Compose 1.11/Lifecycle AAR 内置 lint 检查器二进制不兼容
// （IncompatibleClassChangeError），disable 规则无法绕过（崩溃发生在检测器注册/迁移期）。
// 质量门禁只对 :app:lintDebug（AGENTS.md），library 模块禁用 lint 任务规避崩溃；
// AGP 9.x（lint 32.x）后此规避理论上可移除 —— 属于后续验证项，本次迁移先原样保留。
tasks.configureEach {
    if (name.startsWith("lint")) {
        enabled = false
    }
}
