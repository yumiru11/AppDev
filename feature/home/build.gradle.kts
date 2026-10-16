plugins {
    id("appdev.android.library")
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.yumiru11.githubapp.feature.home"

    defaultConfig {
        // core:github-auth 库 manifest 的 {appAuthRedirectScheme} 占位符（ADR-0001）；
        // Robolectric 测试开资源表后 testManifest 合并需要（settings/feature-issue 同款）
        manifestPlaceholders["appAuthRedirectScheme"] = "com.yumiru11.githubapp"
    }

    // Robolectric compose 测试需要资源表（#89 RepoPickerSheetTest；settings/designsystem 同款配置）
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

    // Paging（动态流分页）
    implementation(libs.paging.runtime)
    implementation(libs.paging.compose)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // Coil 3（事件触发者头像）
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    // 数据层（REST 接口 + T4 认证状态）
    implementation(project(":core:github-rest"))
    implementation(project(":core:github-auth"))
    // 首页「讨论」分区（2026-10 UI 重构）：Discussions 只有 GraphQL 通道（REST 无此端点）
    implementation(project(":core:github-graphql"))

    // 导航（GitHubLinkParser 解析事件 html_url → 应用内路由）
    implementation(project(":core:navigation"))

    // 首页外壳（AppTopBar/AppBottomBar 玻璃栏）
    implementation(project(":core:ui"))
    // 玻璃令牌与 LocalHazeState（backdrop blur 内容侧接线，issue #83；同 repo/search 先例）
    // 主题 AppTheme（@Preview 包裹用，#86）；AppStateChip/三态组件族（#84）
    implementation(project(":core:designsystem"))
    implementation(libs.haze)
    // IssueDto.pullRequest 是 JsonObject（PR 识别 + merged_at 判定），列表投影要用到
    implementation(libs.kotlinx.serialization.json)

    // Testing
    testImplementation(project(":core:testing"))
    testImplementation(libs.mockwebserver3)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
    testImplementation(libs.paging.testing)
    testImplementation(libs.kotlinx.serialization.json)
    testImplementation(kotlin("test"))
}
