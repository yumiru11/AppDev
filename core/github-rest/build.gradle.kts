plugins {
    id("appdev.android.library")
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// 非 UI 模块：禁用 Compose 编译器插件（同 core:navigation 的处理方式），
// 避免无 Compose 运行时依赖时编译失败；Konsist 亦禁止 core:github-* 依赖 Compose。
composeCompiler {
    targetKotlinPlatforms.set(emptySet())
}

android {
    namespace = "com.yumiru11.githubapp.core.githubrest"

    // 显式关闭 compose（约定插件默认开启）
    buildFeatures {
        compose = false
        // BuildConfig.DEBUG：日志拦截器仅 debug 生效（plan.md §4.3）
        buildConfig = true
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
    // API 端点常量（与 core:github-graphql 共享）
    implementation(project(":core:common"))

    // REST 写优先通道：Retrofit 3（原生 suspend）+ kotlinx-serialization converter
    api(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.serialization.json)

    // Hilt（RestNetworkModule 装配 OkHttp/Retrofit）
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    // 测试：MockWebServer3 模拟 GitHub API（新 API 命名空间 okhttp3.mockwebserver3）
    testImplementation(project(":core:testing"))
    testImplementation(libs.mockwebserver3)
}
