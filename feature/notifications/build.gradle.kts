plugins {
    id("appdev.android.library")
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.yumiru11.githubapp.feature.notifications"
}

dependencies {
    // Compose BOM
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.material3)
    implementation(libs.compose.ui)
    implementation(libs.compose.material.icons.core)

    // Lifecycle
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)

    // Paging（通知列表分页）
    implementation(libs.paging.runtime)
    implementation(libs.paging.compose)

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

    // Testing
    testImplementation(project(":core:testing"))
    testImplementation(libs.mockwebserver3)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
    testImplementation(libs.paging.testing)
    testImplementation(libs.kotlinx.serialization.json)
    testImplementation(kotlin("test"))
}
