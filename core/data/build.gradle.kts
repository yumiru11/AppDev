plugins {
    id("appdev.android.library")
}

// 纯模型模块：禁用 Compose 编译器插件（同 core:navigation 的处理方式），
// 避免无 Compose 运行时依赖时编译失败。
composeCompiler {
    targetKotlinPlatforms.set(emptySet())
}

android {
    namespace = "com.yumiru11.githubapp.core.data"
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
    // Paging 3：统一模型含分页游标类型（PagingSource 在 core:github-data 实现）
    api(libs.paging.runtime)

    // 测试（DATA-2）：本模块是纯模型层，测试为纯 JVM 契约测试，无 Android 资源 /
    // Robolectric 需求；core:testing 是仓库统一测试基建入口（JUnit 由其 api 出口传递），
    // libs.junit 显式声明以便单看本文件即可编译测试源码。
    testImplementation(project(":core:testing"))
    testImplementation(libs.junit)
}
