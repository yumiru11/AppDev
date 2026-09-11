plugins {
    id("com.android.application")
    // AGP 9.0 built-in Kotlin：不再需要（且会被硬拒绝）org.jetbrains.kotlin.android。
    // 实测错误原文：
    //   Failed to apply plugin 'org.jetbrains.kotlin.android'
    //   > The 'org.jetbrains.kotlin.android' plugin is no longer required for Kotlin support
    //     since AGP 9.0.
    // KGP 版本由 buildSrc/build.gradle.kts 的 kotlin-gradle-plugin:2.3.21 类路径钉住
    // （AGP 9.0 默认只带 KGP 2.2.10；声明更高版本即可，无需 strictly 降级写法）。
    // Compose 编译器插件与 kotlin-android 无关，built-in Kotlin 下仍需保留。
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    // compileSdk 37：AGP 9.1.x 支持的最高 API 级（AGP 9.0 只到 36.1）；android-37.0 平台
    // 本机已安装（source.properties: AndroidVersion.ApiLevel=37.0），无需联网下载。
    // 37 同时也是 material3 1.5.0-alpha19+（minCompileSdk=37 / minAGP=9.1.0）与
    // mikepenz markdown-renderer 0.43.0 的下限。
    compileSdk = 37
    // SDK Build Tools 36.0.0：AGP 9.x 的最低版本就是 36.0.0，不能再钉 35.0.0（低于下限）
    buildToolsVersion = "36.0.0"

    defaultConfig {
        minSdk = 26
        // targetSdk 保持 35：compileSdk 决定可用的编译期 API，targetSdk 决定运行期行为兼容开关。
        // 升 targetSdk 需要单独的行为走查（分区存储/前台服务/通知权限等），不属于工具链迁移范围。
        // （AGP 9 的 android.sdk.defaultTargetSdkToCompileSdkIfUnset 默认 true 只影响【未显式设置】时，
        //  这里显式设置了 35，因此不受影响。）
        targetSdk = 35
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    // 注意：built-in Kotlin 下无需再显式设置 kotlin.compilerOptions.jvmTarget ——
    // 其默认值就是 android.compileOptions.targetCompatibility（= 17）。
    // 原 `extensions.configure<KotlinAndroidProjectExtension>("kotlin") { jvmTarget = JVM_17 }`
    // 已删除（built-in Kotlin 注册的 `kotlin` 扩展类型不再是 KotlinAndroidProjectExtension，
    //  且按类型 configure 在 newDsl 下会失败）。

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // Apollo Kotlin 5.0.0 KMP JAR（normalized-cache 等）携带重复的 commonMain/ 元数据，
    // 运行时无用，整体排除避免 mergeDebugJavaResource 重复路径冲突（GitLight 实测）
    packaging {
        resources {
            excludes += "commonMain/**"
            excludes += "META-INF/kotlin-project-structure-metadata.json"
        }
    }
}
