plugins {
    id("appdev.android.library")
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.yumiru11.githubapp.feature.profile"

    defaultConfig {
        // AppAuth 库 manifest 的 ${appAuthRedirectScheme} 占位符（ADR-0001 自定义 scheme）：
        // feature:profile 依赖 core:github-auth，库 manifest 合入本模块（含测试 manifest）时须提供值。
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
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    // Compose Material Icons (core icons)
    implementation(libs.compose.material.icons.core)

    // Activity + Lifecycle
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)

    // Navigation
    implementation(libs.navigation.compose)

    // Paging 3（四列表分页：Repos/Starred/Followers/Following）
    implementation(libs.paging.compose)
    implementation(libs.paging.runtime)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // Coil 3（头像）
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    // 数据层 + 网络通道 + 认证状态（authState 驱动未登录引导）
    implementation(project(":core:data"))
    implementation(project(":core:github-rest"))
    implementation(project(":core:github-auth"))

    // 导航
    implementation(project(":core:navigation"))
    implementation(project(":core:ui"))

    // Testing
    testImplementation(project(":core:testing"))
    testImplementation(libs.mockwebserver3)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    testImplementation(libs.paging.testing)
    testImplementation(kotlin("test"))
    // MockWebServer 测试构造 Retrofit 需直接引用 Json 类型（core:github-rest 的 implementation 不外泄）
    testImplementation(libs.kotlinx.serialization.json)
}
