plugins {
    id("appdev.android.library")
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.yumiru11.githubapp.feature.home"
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

    // 导航（GitHubLinkParser 解析事件 html_url → 应用内路由）
    implementation(project(":core:navigation"))

    // 首页外壳（AppTopBar/AppBottomBar 玻璃栏）
    implementation(project(":core:ui"))
    // 玻璃令牌与 LocalHazeState（backdrop blur 内容侧接线，issue #83；同 repo/search 先例）
    implementation(project(":core:designsystem"))
    implementation(libs.haze)

    // Testing
    testImplementation(project(":core:testing"))
    testImplementation(libs.mockwebserver3)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
    testImplementation(libs.paging.testing)
    testImplementation(libs.kotlinx.serialization.json)
    testImplementation(kotlin("test"))
}
