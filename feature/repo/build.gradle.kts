plugins {
    id("appdev.android.library")
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.yumiru11.githubapp.feature.repo"

    defaultConfig {
        // core:github-auth 库 manifest 的 ${appAuthRedirectScheme} 占位符（ADR-0001 自定义 scheme）
        manifestPlaceholders["appAuthRedirectScheme"] = "com.yumiru11.githubapp"
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            // android.util.Log 等 Android 桩返回默认值而非抛 Stub 异常：
            // RepoRepository 渲染判定日志（ReadmeRender tag）在纯 JVM MockK 测试中安全
            isReturnDefaultValues = true
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

    // 登录态（游客只读：Star/Watch/Fork 按钮隐藏）
    implementation(project(":core:github-auth"))

    // Markdown 渲染
    implementation(project(":core:markdown"))
    implementation(libs.markdown.renderer)
    implementation(libs.markdown.renderer.m3)

    // 代码浏览（Sora 只读视图，T11；依赖隔离在 core:editor）
    implementation(project(":core:editor"))

    // 导航
    implementation(project(":core:navigation"))
    implementation(project(":core:ui"))

    // Testing
    testImplementation(project(":core:testing"))
    testImplementation(libs.mockwebserver3)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    testImplementation(kotlin("test"))
}
