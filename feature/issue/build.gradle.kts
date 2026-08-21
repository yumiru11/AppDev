plugins {
    id("appdev.android.library")
    alias(libs.plugins.roborazzi)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.yumiru11.githubapp.feature.issue"

    defaultConfig {
        // core:github-auth 库 manifest 的 ${appAuthRedirectScheme} 占位符（ADR-0001 自定义 scheme）
        manifestPlaceholders["appAuthRedirectScheme"] = "com.yumiru11.githubapp"
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

dependencies {
    // Compose BOM
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.material3)
    implementation(libs.compose.ui)
    implementation(libs.compose.material.icons.core)
    implementation(libs.compose.ui.tooling.preview)

    // Lifecycle
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)

    // Paging（Issue 列表分页）
    implementation(libs.paging.runtime)
    implementation(libs.paging.compose)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // Coil 3（作者/Assignees 头像）
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    // 数据层（REST IssueApi + T4 认证状态）
    implementation(project(":core:github-rest"))
    implementation(project(":core:github-auth"))
    // GraphQL 通道（T14 任务列表 mutation + 写操作上下文 viewerPermission/node id）
    implementation(project(":core:github-graphql"))
    // DTO 映射需访问 IssueDto.pullRequest（kotlinx.serialization JsonObject 类型）
    implementation(libs.kotlinx.serialization.json)

    // Markdown（评论/正文原生渲染）
    implementation(project(":core:markdown"))

    // 导航（GitHubLinkParser 解析评论链接）
    implementation(project(":core:navigation"))

    // 外壳（TopAppBar 等）
    implementation(project(":core:ui"))

    // 主题（@Preview 用 AppTheme 包裹，#86）
    implementation(project(":core:designsystem"))

    // Testing
    testImplementation(project(":core:testing"))
    testImplementation(libs.mockwebserver3)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    testImplementation(libs.paging.testing)
    testImplementation(libs.kotlinx.serialization.json)
    testImplementation(kotlin("test"))
}
