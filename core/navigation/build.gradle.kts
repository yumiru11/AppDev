plugins {
    id("appdev.android.library")
    // #90 类型安全路由：@Serializable AppRoute 需要序列化编译器插件生成 serializer
    alias(libs.plugins.kotlin.serialization)
}

// 纯 JVM 模块：禁用 Compose 编译器插件。
// 约定插件强加了 org.jetbrains.kotlin.plugin.compose，但本模块无 Compose 运行时依赖，
// 保留会导致编译失败（"Compose Compiler requires the Compose Runtime on the class path"）。
// 通过清空 composeCompiler.targetKotlinPlatforms 使 isApplicable() 返回 false，从而跳过该插件。
composeCompiler {
    targetKotlinPlatforms.set(emptySet())
}

android {
    namespace = "com.yumiru11.githubapp.core.navigation"
    // 纯 JVM 模块：禁用 Compose（约定插件默认开启 compose=true，
    // 但本模块无 Compose 运行时依赖，需显式关闭以免编译器报错）
    buildFeatures {
        compose = false
    }
}

// AGP 在 buildFeatures.compose=true 时创建 kotlin-extension 配置并把 Compose 编译器
// 塞进编译器 classpath。本模块为纯 JVM，清空该配置依赖以彻底禁用 Compose 编译器。
// 需在 afterEvaluate 中执行，因为 AGP 在配置阶段才填充该配置。
afterEvaluate {
    configurations.matching { it.name == "kotlin-extension" }.configureEach {
        dependencies.clear()
    }
}

dependencies {
    // 纯逻辑解析器：依赖最小化，禁止引入 Compose/android。
    // kotlinx-serialization-core 仅为类型安全路由服务（AppRoute @Serializable，
    // #90）；GitHubLinkParser 保持纯字符串解析，JUnit 可测。
    implementation(libs.kotlinx.serialization.core)
    // #90 路由序列化 round-trip 测试驱动生成 serializer（与 github-rest DTO 测试同口径）
    testImplementation(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
}
