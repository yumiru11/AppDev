plugins {
    id("appdev.android.library")
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.yumiru11.githubapp.feature.search"
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

    // Paging（四类结果分页）
    implementation(libs.paging.runtime)
    implementation(libs.paging.compose)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // Coil 3（用户搜索结果头像）
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    // 数据层：搜索仓库（REST + 错误归一化）+ T4 认证状态（代码搜索登录门）+ 搜索历史 Room
    implementation(project(":core:github-data"))
    implementation(project(":core:github-rest"))
    implementation(project(":core:github-auth"))
    implementation(project(":core:database"))

    // 导航（GitHubLinkParser 解析结果 html_url → 应用内路由）
    implementation(project(":core:navigation"))

    // 语义色（Issue 状态点 success/danger）
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
