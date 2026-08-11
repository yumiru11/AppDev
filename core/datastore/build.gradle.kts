plugins {
    id("appdev.android.library")
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// 非 UI 模块：禁用 Compose 编译器插件（同 core:navigation 的处理方式），
// 避免无 Compose 运行时依赖时编译失败。
composeCompiler {
    targetKotlinPlatforms.set(emptySet())
}

android {
    namespace = "com.yumiru11.githubapp.core.datastore"

    // 显式关闭 compose（约定插件默认开启）
    buildFeatures {
        compose = false
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
    // DataStore Preferences（主题/语言等用户偏好）
    implementation(libs.datastore.preferences)

    // Hilt（DataStoreModule 装配）
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    // 测试
    testImplementation(project(":core:testing"))
    testImplementation(libs.turbine)
}
