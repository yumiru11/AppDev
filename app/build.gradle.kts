plugins {
    id("appdev.android.application")
    alias(libs.plugins.roborazzi)
}

android {
    namespace = "com.yumiru11.githubapp"

    defaultConfig {
        applicationId = "com.yumiru11.githubapp"
        versionCode = 1
        versionName = "0.1.0"
    }

    testOptions {
        unitTests {
            // Robolectric/Roborazzi 需要 Android 资源
            isIncludeAndroidResources = true
        }
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = false
        // AGP 8.7.3 的 lint-api 与新版 Compose 1.11 / Lifecycle AAR 内置 lint 检查器二进制不兼容
        // （IncompatibleClassChangeError），整体禁用这批库内检测器；AGP/lint 升级后移除本段
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
        disable += "UnrememberedState"
        disable += "NullSafeMutableLiveData"
    }

    // 签名：仅当环境变量/Gradle 属性提供 keystore 时启用（CI release 流程），
    // 本地无 keystore 不影响 debug 构建
    signingConfigs {
        val keystoreFile =
            System.getenv("KEYSTORE_FILE")?.let(::file)
                ?: project.findProperty("KEYSTORE_FILE")?.toString()?.let(::file)
        if (keystoreFile != null && keystoreFile.exists()) {
            create("release") {
                storeFile = keystoreFile
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                    ?: project.findProperty("KEYSTORE_PASSWORD")?.toString()
                keyAlias = System.getenv("KEY_ALIAS")
                    ?: project.findProperty("KEY_ALIAS")?.toString()
                keyPassword = System.getenv("KEY_PASSWORD")
                    ?: project.findProperty("KEY_PASSWORD")?.toString()
            }
        }
    }

    buildTypes {
        release {
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
    }
}

dependencies {
    // Compose BOM
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.material3)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    // Activity + Lifecycle
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)

    // Core
    implementation(libs.core.ktx)

    // Testing：Konsist 架构护栏 + core:testing 基建（JUnit4/Robolectric/Roborazzi/coroutines-test 由其 api 导出）
    testImplementation(libs.konsist)
    testImplementation(project(":core:testing"))
}

// Konsist 无原生任务：以过滤后的单测任务充当 konsistCheck（只跑 konsist 包下的架构测试）。
// isFailOnNoMatchingTests = false 保证过滤无匹配时空跑通过；T2 起该包含正式架构护栏规则
tasks.register<Test>("konsistCheck") {
    description = "Runs Konsist architecture tests (filtered from :app unit tests)."
    group = "verification"
    testClassesDirs =
        project.tasks
            .named<Test>("testDebugUnitTest")
            .get()
            .testClassesDirs
    classpath =
        project.tasks
            .named<Test>("testDebugUnitTest")
            .get()
            .classpath
    useJUnit()
    filter {
        includeTestsMatching("com.yumiru11.githubapp.konsist.*")
        isFailOnNoMatchingTests = false
    }
}
