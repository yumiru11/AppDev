plugins {
    id("appdev.android.library")
    alias(libs.plugins.roborazzi)
}

android {
    namespace = "com.yumiru11.githubapp.core.ui"

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

    // Material Icons Core（基础图标集：Home/Person/Search/Notifications/Close/Folder 等）
    implementation(libs.compose.material.icons.core)

    // Navigation (T3 nav skeleton)
    implementation(libs.navigation.compose)

    // Custom Tabs for external links (T3)
    implementation(libs.androidx.browser)

    // 依赖 core:navigation 的 AppRoute/ParsedUrl
    implementation(project(":core:navigation"))

    // 玻璃拟真容器（ADR-0004：顶栏/底栏 GlassSurface，T6 已交付）
    implementation(project(":core:designsystem"))
    // Haze：MainTabPager 对分区内容挂 hazeSource（backdrop blur 内容侧，issue #83）
    implementation(libs.haze)

    // 测试：core:testing 已 api 导出 JUnit4/Robolectric/Roborazzi/compose-test
    testImplementation(project(":core:testing"))
}
