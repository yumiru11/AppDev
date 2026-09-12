plugins {
    id("appdev.android.library")
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// 非 UI 模块：禁用 Compose 编译器插件（同 core:navigation 的处理方式），
// 避免无 Compose 运行时依赖时编译失败；Konsist 亦禁止 core:github-* 依赖 Compose。
composeCompiler {
    targetKotlinPlatforms.set(emptySet())
}

android {
    namespace = "com.yumiru11.githubapp.core.githubdata"

    defaultConfig {
        // core:github-auth 库 manifest 的 ${appAuthRedirectScheme} 占位符（ADR-0001 自定义 scheme；
        // 本模块新增对 github-auth 的依赖 + Robolectric 测试 manifest 合并后需要）。
        manifestPlaceholders["appAuthRedirectScheme"] = "com.yumiru11.githubapp"
    }

    // 显式关闭 compose（约定插件默认开启）
    buildFeatures {
        compose = false
    }

    // Room（RoomEtagStore + in-memory DB）在 Robolectric 下需要 Android 资源
    testOptions {
        unitTests {
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
    // 仓库层（Repository 模式，UI 唯一数据入口）：映射 REST/GraphQL → core:data 统一模型
    api(project(":core:data"))
    implementation(project(":core:github-rest"))
    implementation(project(":core:github-graphql"))
    // PAT 降级门控（ADR-0003）：读 TokenStorage 的 isRestOnly 决定是否跳过 GraphQL 通道
    implementation(project(":core:github-auth"))

    // DATA-1：持久化 ETag 缓存的 EtagCacheDao（RoomEtagStore 数据出入口）
    implementation(project(":core:database"))

    // IssueDto.pullRequest 为 JsonObject（github-rest api 暴露的公共类型，需传递可见）
    implementation(libs.kotlinx.serialization.json)

    // RoomEtagStore 用 runBlocking 桥接同步 EtagStore 与 Room suspend DAO
    implementation(libs.kotlinx.coroutines.core)

    // Paging 3（GraphQL cursor PagingSource）
    implementation(libs.paging.runtime)

    // FetchPolicy（GraphQL 读优先通道强制网络新鲜度）
    implementation(libs.apollo.normalized.cache)

    // Hilt（RepositoryModule 装配）
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    // 测试
    testImplementation(project(":core:testing"))
    testImplementation(libs.paging.testing)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    testImplementation(libs.mockwebserver3)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.kotlinx.serialization.json)
    testImplementation(kotlin("test"))
}
