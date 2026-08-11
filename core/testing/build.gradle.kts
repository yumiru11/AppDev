plugins {
    id("appdev.android.library")
}

android {
    namespace = "com.yumiru11.githubapp.core.testing"
}

dependencies {
    // 领域模型（GitHubFakes 工厂返回 core:data 真实模型；api 导出保证测试模块可用）
    api(project(":core:data"))

    // Compose（截图测试基类依赖 @Composable / MaterialTheme；BOM 统一版本）
    api(platform(libs.compose.bom))
    api(libs.compose.ui)
    api(libs.compose.material3)
    // Compose UI 测试（createComposeRule / createAndroidComposeRule）
    api(libs.compose.ui.test.junit4)
    debugApi(libs.compose.ui.test.manifest)

    // JUnit4 + coroutines-test（MainDispatcherRule）
    api(libs.junit)
    api(libs.kotlinx.coroutines.test)

    // Robolectric + Roborazzi（截图基准测试）
    api(libs.robolectric)
    api(libs.roborazzi)
    api(libs.roborazzi.compose)
    api(libs.roborazzi.junit.rule)
}
