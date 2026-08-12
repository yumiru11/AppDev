plugins {
    id("appdev.android.library")
    alias(libs.plugins.roborazzi)
}

android {
    namespace = "com.yumiru11.githubapp.core.designsystem"

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.material3)
    implementation(libs.compose.ui)
    // ThemeMode 枚举统一在 core:datastore（数据层），UI 依赖数据层方向
    implementation(project(":core:datastore"))

    // 测试：core:testing 已 api 导出 JUnit4/Robolectric/Roborazzi/compose-test
    testImplementation(project(":core:testing"))
}
