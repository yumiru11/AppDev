plugins {
    id("appdev.android.library")
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.apollo)
}

// 非 UI 模块：禁用 Compose 编译器插件（同 core:navigation 的处理方式），
// 避免无 Compose 运行时依赖时编译失败；Konsist 亦禁止 core:github-* 依赖 Compose。
composeCompiler {
    targetKotlinPlatforms.set(emptySet())
}

android {
    namespace = "com.yumiru11.githubapp.core.githubgraphql"

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

// Apollo Kotlin 5：GitHub GraphQL 读优先通道（response-based codegen 为默认）。
// schema 下载（Apollo 5 需用 service 专属任务）：
//   GITHUB_TOKEN=... ./gradlew :core:github-graphql:downloadGithubApolloSchemaFromIntrospection
apollo {
    service("github") {
        packageName.set("com.yumiru11.githubapp.core.githubgraphql.generated")
        srcDir("src/main/graphql")
        introspection {
            endpointUrl.set("https://api.github.com/graphql")
            headers.put("Authorization", "Bearer ${System.getenv("GITHUB_TOKEN") ?: ""}")
            schemaFile.set(file("src/main/graphql/schema.graphqls"))
        }
        // 自定义标量映射（plan.md §4.4）：DateTime → java.time.Instant（自定义 Adapter），
        // 其余 URL/文本型标量映射为 String；GitObjectID 等未映射标量默认 Any 会泄漏到领域层，故显式收敛
        mapScalar("DateTime", "java.time.Instant", "com.yumiru11.githubapp.core.githubgraphql.scalar.InstantAdapter")
        mapScalar("URI", "kotlin.String")
        mapScalar("HTML", "kotlin.String")
        mapScalar("Date", "kotlin.String")
        mapScalar("GitObjectID", "kotlin.String")
        mapScalar("Base64String", "kotlin.String")
        mapScalar("PreciseDateTime", "java.time.Instant", "com.yumiru11.githubapp.core.githubgraphql.scalar.InstantAdapter")

        // Normalized Cache（Apollo 5 新版独立缓存库）：compiler plugin 生成 cache() builder 扩展
        plugin("com.apollographql.cache:normalized-cache-apollo-compiler-plugin:${libs.versions.apollo.normalized.cache.get()}")
        pluginArgument("com.apollographql.cache.packageName", packageName.get())
    }
}

dependencies {
    // API 端点常量 + @GitHubHttpClient 限定符（共享 OkHttp，issue #6）
    implementation(project(":core:common"))
    implementation(project(":core:github-rest"))

    // GraphQL 客户端 + Normalized Cache（Apollo 5 新版独立缓存库 com.apollographql.cache，
    // memory → SQLite 链；旧版 apollo-normalized-cache 已弃用且装配 API 均为 internal）
    api(libs.apollo.runtime)
    // api 暴露 FetchPolicy/fetchPolicy 扩展：消费方（feature:issue 写上下文查询）需控制缓存策略
    api(libs.apollo.normalized.cache)
    implementation(libs.apollo.normalized.cache.sqlite)

    // 共享 OkHttp（Auth 拦截器/统一请求头由 core:github-rest 的装配提供）
    implementation(libs.okhttp)

    // Hilt（GraphqlModule 装配 ApolloClient）
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    // 测试：MockWebServer3 模拟 /graphql 端点（验收允许 mockwebserver 通道）
    testImplementation(project(":core:testing"))
    testImplementation(libs.mockwebserver3)
}
