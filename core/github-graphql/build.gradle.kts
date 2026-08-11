plugins {
    id("appdev.android.library")
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.apollo)
}

android {
    namespace = "com.yumiru11.githubapp.core.github_graphql"
}

// Apollo Kotlin 5：GitHub GraphQL 读优先通道（response-based codegen 为默认）。
// schema 下载（Apollo 5 需用 service 专属任务）：
//   GITHUB_TOKEN=... ./gradlew :core:github-graphql:downloadGithubApolloSchemaFromIntrospection
apollo {
    service("github") {
        packageName.set("com.yumiru11.githubapp.core.github_graphql.generated")
        srcDir("src/main/graphql")
        introspection {
            endpointUrl.set("https://api.github.com/graphql")
            headers.put("Authorization", "Bearer ${System.getenv("GITHUB_TOKEN") ?: ""}")
            schemaFile.set(file("src/main/graphql/schema.graphqls"))
        }
    }
}

dependencies {
    // GraphQL 客户端 + Normalized Cache（memory → SQLite 链）
    api(libs.apollo.runtime)
    implementation(libs.apollo.normalized.cache)
    implementation(libs.apollo.normalized.cache.sqlite)
    implementation(libs.sqldelight.android.driver)

    // 共享 OkHttp（Auth 拦截器/统一请求头由 core:github-rest 的装配提供）
    implementation(libs.okhttp)

    // Hilt（GraphqlModule 装配 ApolloClient）
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    // 测试：MockWebServer3 模拟 /graphql 端点（验收允许 mockwebserver 通道）
    testImplementation(project(":core:testing"))
    testImplementation(libs.mockwebserver3)
}
