plugins {
    id("appdev.android.library")
    alias(libs.plugins.roborazzi)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.yumiru11.githubapp.feature.pullrequest"

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

    // Paging（PR 列表分页）
    implementation(libs.paging.runtime)
    implementation(libs.paging.compose)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // Coil 3（作者/Reviewers 头像）
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    // 数据层（REST PullRequestApi + T4 认证状态）
    implementation(project(":core:github-rest"))
    implementation(project(":core:github-auth"))

    // Markdown（PR 正文 WebView 渲染 + 评论/Review 原生渲染）
    implementation(project(":core:markdown"))

    // 导航（GitHubLinkParser 解析评论链接）
    implementation(project(":core:navigation"))

    // 外壳（TopAppBar 等）+ AppStateChip 状态徽标（#84）
    implementation(project(":core:ui"))
    implementation(project(":core:designsystem"))

    // 主题（@Preview 用 AppTheme 包裹，#86）
    implementation(project(":core:designsystem"))

    // Testing
    testImplementation(project(":core:testing"))
    testImplementation(libs.mockwebserver3)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
    testImplementation(libs.paging.testing)
    testImplementation(libs.kotlinx.serialization.json)
    testImplementation(kotlin("test"))
}
