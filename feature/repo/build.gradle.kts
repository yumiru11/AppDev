plugins {
    id("appdev.android.library")
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.yumiru11.githubapp.feature.repo"

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

    // Material Symbols (icons)
    implementation(libs.icons.material.symbols.rounded)
    implementation(libs.icons.material.symbols.rounded.cmp)

    // Activity + Lifecycle
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)

    // Navigation
    implementation(libs.navigation.compose)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // Coil 3 (avatar images)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    // 主题
    implementation(project(":core:designsystem"))

    // 数据层
    implementation(project(":core:data"))
    implementation(project(":core:github-rest"))
    implementation(project(":core:database"))

    // Markdown 渲染
    implementation(project(":core:markdown"))
    implementation(libs.markdown.renderer)
    implementation(libs.markdown.renderer.m3)

    // 导航
    implementation(project(":core:navigation"))
    implementation(project(":core:ui"))

    // Testing
    testImplementation(project(":core:testing"))
    testImplementation(libs.mockwebserver3)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
    testImplementation(kotlin("test"))
}
