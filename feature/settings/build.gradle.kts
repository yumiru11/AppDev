plugins {
    id("appdev.android.library")
    // Hilt（SettingsViewModel @HiltViewModel）
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.yumiru11.githubapp.feature.settings"
}

dependencies {
    // Compose BOM
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.material3)
    implementation(libs.compose.ui)

    // Material Icons Core（Icons.Default/Outlined/Rounded/Filled 图标风格预览）
    implementation(libs.compose.material.icons.core)

    // Lifecycle（viewModelScope）
    implementation(libs.lifecycle.viewmodel.compose)

    // 偏好仓库（T24 全部设置项持久化）
    implementation(project(":core:datastore"))

    // 设计令牌（AppMotion/AppDimens 滑杆预览）
    implementation(project(":core:designsystem"))

    // 认证核心（PAT 开发者模式：TokenStorage/OAuthSessionManager/AuthState）
    implementation(project(":core:github-auth"))

    // Hilt（SettingsViewModel @HiltViewModel 装配）
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    // 测试：core:testing 已 api 导出 JUnit4/Robolectric/Roborazzi/compose-test/coroutines-test
    testImplementation(project(":core:testing"))
    // MockK（mock OAuthSessionManager）+ Turbine（Flow 断言）
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
}
