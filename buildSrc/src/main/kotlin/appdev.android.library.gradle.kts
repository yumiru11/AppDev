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
        // AGP 8.7.3 的 lint-api 与新版 Compose 1.11 / Lifecycle AAR 内置 lint 检查器二进制不兼容
        // （IncompatibleClassChangeError），整体禁用这批库内检测器（AGP/lint 升级后移除本段）。
        disable += "AutoboxingStateCreation"
        disable += "AutoboxingStateValueProperty"
        disable += "ComposableCoroutineCreation"
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
        disable += "UnrememberedState"
        disable += "NullSafeMutableLiveData"
    }
}

extensions.configure<KotlinAndroidProjectExtension>("kotlin") {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

// AGP 8.7.3 的 LintJarApiMigration 与新版 Compose 1.11/Lifecycle AAR 内置 lint 检查器二进制不兼容
// （IncompatibleClassChangeError），disable 规则无法绕过（崩溃发生在检测器注册/迁移期）。
// 质量门禁只对 :app:lintDebug（AGENTS.md），library 模块禁用 lint 任务规避崩溃；AGP/lint 升级后移除本段。
tasks.configureEach {
    if (name.startsWith("lint")) {
        enabled = false
    }
}
