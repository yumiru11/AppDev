plugins {
    id("appdev.android.application")
    alias(libs.plugins.roborazzi)
    // Hilt（@HiltAndroidApp 图根 + @AndroidEntryPoint 注入）
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.yumiru11.githubapp"

    defaultConfig {
        applicationId = "com.yumiru11.githubapp"
        versionCode = 1
        versionName = "0.1.0"

        // AppAuth 库 manifest 的 RedirectUriReceiverActivity 用 ${appAuthRedirectScheme} 占位符
        // （core:github-auth 声明，ADR-0001 自定义 scheme）；库 manifest 合入 app 时须由 app 提供值。
        manifestPlaceholders["appAuthRedirectScheme"] = "com.yumiru11.githubapp"
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
        // T4 Wave2 新增（AuthNavigationTest/AuthViewModel 触发）：AGP 8.7.3 lint 与
        // Compose 1.11 的 StateFlow 值检测器二进制不兼容（IncompatibleClassChangeError，
        // lint 崩溃 "KaFunctionCall interface was expected"）；AGP/lint 升级后移除
        disable += "StateFlowValueCalledInComposition"
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

    // OkHttp 5 / jspecify 均携带 OSGi MANIFEST，合并 Java 资源时路径冲突 → 排除
    packaging {
        resources {
            excludes += "META-INF/versions/9/OSGI-INF/MANIFEST.MF"
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

    // Navigation + External links (T3)
    implementation(libs.navigation.compose)
    implementation(libs.androidx.browser)

    // 导航骨架（core:ui + core:navigation）
    implementation(project(":core:ui"))
    implementation(project(":core:navigation"))

    // 主题（core:designsystem）
    implementation(project(":core:designsystem"))

    // 数据层（T5）：仓库层/网络通道/离线缓存/偏好存储
    implementation(project(":core:github-data"))
    implementation(project(":core:database"))
    implementation(project(":core:datastore"))

    // 认证（T4 Wave2 接线）：OAuthSessionManager/TokenStorage 注入 MainActivity + AuthViewModel
    implementation(project(":core:github-auth"))
    implementation(project(":core:github-rest"))
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    // OkHttp（AppDiModule 桥接 @AuthHttpClient 客户端需要直接引用；core:github-auth 的 implementation 不外泄编译类路径）
    implementation(libs.okhttp)

    // 登录页（T4 Wave2 接线）：LoginScreen/AuthViewModel 装配进 app 导航
    implementation(project(":feature:auth"))
    // 首页动态流（T10）：HomeScreen/HomeViewModel 装配进 app 导航
    implementation(project(":feature:home"))
    // 仓库详情页（T9 README 浏览 tracer bullet）
    implementation(project(":feature:repo"))
    // 通知页（T19 通知列表/已读/过滤）
    implementation(project(":feature:notifications"))
    // 个人主页（T20：资料头 + 四列表分页）
    implementation(project(":feature:profile"))
    // 设置页（T24）：SettingsScreen/SettingsViewModel 装配进 app 导航
    implementation(project(":feature:settings"))
    // Issue 列表/详情（T13）
    implementation(project(":feature:issue"))
    // 原型入口（仅 debug）：README 双版本对照（prototype/readme-comparison 分支产物）
    debugImplementation(project(":prototype:readme-comparison"))

    // Hilt（app 图根）
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    // Core
    implementation(libs.core.ktx)

    // Testing：Konsist 架构护栏 + core:testing 基建（JUnit4/Robolectric/Roborazzi/coroutines-test 由其 api 导出）
    testImplementation(libs.konsist)
    testImplementation(project(":core:testing"))
    // MockK（core:testing 不导出，截图测试直接使用）
    testImplementation(libs.mockk)
    // Hilt 图装配测试（@HiltAndroidTest + HiltAndroidTestRunner，Robolectric）
    testImplementation(libs.hilt.android.testing)
    kspTest(libs.hilt.compiler)
}

// Konsist 无原生任务：以过滤后的单测任务充当 konsistCheck（只跑 konsist 包下的架构测试）。
// isFailOnNoMatchingTests = false 保证过滤无匹配时空跑通过；T2 起该包含正式架构护栏规则

// 禁用 AAR metadata 检查：mikepenz markdown-renderer 0.43.0 要求 compileSdk 37，
// 但 android-37 平台尚未发布，compileSdk 36 编译无问题（API 兼容）
afterEvaluate {
    tasks
        .matching {
            it.name.contains(
                "AarMetadata",
                ignoreCase = true,
            ) || it.name.contains("aarMetadata", ignoreCase = true)
        }.configureEach {
            enabled = false
        }
}
tasks.register<Test>("konsistCheck") {
    description = "Runs Konsist architecture tests (filtered from :app unit tests)."
    group = "verification"
    // 并行执行（org.gradle.parallel=true）下需确保编译先完成：
    // 任务从 testDebugUnitTest 取 classpath 产物，若并行期间编译仍写入会读浆 → 显式依赖编译完成
    dependsOn(":app:compileDebugKotlin", ":app:compileDebugUnitTestKotlin")
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
    // Konsist 在测试运行期扫描全仓源码，但多数模块（feature/大部分 core）不在 :app classpath 上，
    // 默认 inputs 感知不到它们的变更 → 新增违规文件时任务被判 UP-TO-DATE 静默跳过。
    // 显式声明源码目录为 inputs（排除 build/）：任一模块源码变更即触发重扫。
    // 注意：不能用整目录 inputs（会把 build 产物卷进来，Gradle 8.12 隐式依赖校验在
    // org.gradle.parallel=true 下会报错），必须用 fileTree 排除构建产物。
    inputs.files(
        rootProject.layout.projectDirectory
            .dir("core")
            .asFileTree
            .matching { exclude("**/build/**") },
        rootProject.layout.projectDirectory
            .dir("feature")
            .asFileTree
            .matching { exclude("**/build/**") },
        rootProject.layout.projectDirectory
            .dir("app/src")
            .asFileTree
            .matching { exclude("**/build/**") },
    )
    useJUnit()
    filter {
        includeTestsMatching("com.yumiru11.githubapp.konsist.*")
        isFailOnNoMatchingTests = false
    }
}
