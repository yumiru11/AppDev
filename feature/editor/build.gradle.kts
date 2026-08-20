plugins {
    id("appdev.android.library")
}

android {
    namespace = "com.yumiru11.githubapp.feature.editor"

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

    // 主题
    implementation(project(":core:designsystem"))

    // Markdown 编辑器（Sora 封装，T21）
    implementation(project(":core:editor"))

    // Markdown 预览（WebView 主渲染管线，T21 预览与展示共用）
    implementation(project(":core:markdown"))

    // 导航（ParsedUrl 链接分发）
    implementation(project(":core:navigation"))

    // Testing
    testImplementation(project(":core:testing"))
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
    testImplementation(kotlin("test"))
}
