plugins {
    id("appdev.android.library")
}

// 纯 JVM 模块：禁用 Compose 编译器插件（同 core:navigation 的处理方式）。
// 约定插件强加了 org.jetbrains.kotlin.plugin.compose，但本模块无 Compose 运行时依赖，
// 保留会导致编译失败（"Compose Compiler requires the Compose Runtime on the class path"）。
// 通过清空 composeCompiler.targetKotlinPlatforms 使 isApplicable() 返回 false，从而跳过该插件。
composeCompiler {
    targetKotlinPlatforms.set(emptySet())
}

android {
    namespace = "com.yumiru11.githubapp.core.common"
    // 纯 JVM 模块：显式关闭 compose（约定插件默认开启）
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
