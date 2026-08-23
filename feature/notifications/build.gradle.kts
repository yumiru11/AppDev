plugins {
    id("appdev.android.library")
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    // 截图基准（#88 通知面板 light/dark 基线；同 core:ui 配置）
    alias(libs.plugins.roborazzi)
}

android {
    namespace = "com.yumiru11.githubapp.feature.notifications"

    defaultConfig {
        // Roborazzi 截图测试（unit test 含资源）触发 manifest 合并：core:github-auth 的
        // OAuth 回调 scheme 占位符需由消费模块提供（与 app/defaultConfig 保持一致）
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

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // 数据层（REST 接口 + T4 认证状态）
    implementation(project(":core:github-rest"))
    implementation(project(":core:github-auth"))

    // 导航（GitHubLinkParser 解析通知 html_url → 应用内路由）
    implementation(project(":core:navigation"))

    // 相对时间 RelativeTime（#84 上移 core:ui）+ 后续共享 UI 组件
    implementation(project(":core:ui"))
    // 主题（@Preview 用 AppTheme 包裹，#86；其余屏经 core:ui 传递获得）
    implementation(project(":core:designsystem"))

    // Testing
    testImplementation(project(":core:testing"))
    testImplementation(libs.mockwebserver3)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.serialization.json)
    testImplementation(kotlin("test"))
}
