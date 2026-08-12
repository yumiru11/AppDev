plugins {
    id("appdev.android.library")
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// 非 UI 模块：禁用 Compose 编译器插件（同 core:navigation 的处理方式），
// 避免无 Compose 运行时依赖时编译失败。
composeCompiler {
    targetKotlinPlatforms.set(emptySet())
}

android {
    namespace = "com.yumiru11.githubapp.core.github_auth"

    // 显式关闭 compose（约定插件默认开启）
    buildFeatures {
        compose = false
    }

    testOptions {
        unitTests {
            // Robolectric：EncryptedTokenStorage 走 Android 框架（AndroidKeyStore/SharedPreferences）
            isIncludeAndroidResources = true
        }
    }
}

// AGP 在 buildFeatures.compose=true 时创建 kotlin-extension 配置并把 Compose 编译器
// 塞进编译器 classpath；清空该配置依赖以彻底禁用 Compose 编译器（afterEvaluate 见 core:navigation 注释）。
afterEvaluate {
    configurations.matching { it.name == "kotlin-extension" }.configureEach {
        dependencies.clear()
    }
}

dependencies {
    // 安全存储：EncryptedSharedPreferences 加密落盘（ADR-0002，plan.md §4 token 安全红线）
    implementation(libs.security.crypto)

    // 会话序列化（SessionData @Serializable）
    implementation(libs.kotlinx.serialization.json)

    // Hilt（EncryptedTokenStorage @Inject 装配；TokenStorage→生产/测试绑定模块后续票落地）
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    // 协程（认证流程/TokenProvider 桥接票使用；TokenStorage 接口本身为同步设计）
    implementation(libs.kotlinx.coroutines.core)

    // 测试：core:testing 已 api 导出 JUnit4/Robolectric/Roborazzi
    testImplementation(project(":core:testing"))
}
