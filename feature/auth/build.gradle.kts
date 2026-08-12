plugins {
    id("appdev.android.library")
    alias(libs.plugins.roborazzi)
}

android {
    namespace = "com.yumiru11.githubapp.feature.auth"

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

    // Material Icons Core（Icons.Default 展开/收起箭头）
    implementation(libs.compose.material.icons.core)

    // 测试：core:testing 已 api 导出 JUnit4/Robolectric/Roborazzi/compose-test
    testImplementation(project(":core:testing"))
}
