plugins {
    id("appdev.android.library")
}

android {
    namespace = "com.yumiru11.githubapp.core.testing"
}

dependencies {
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
