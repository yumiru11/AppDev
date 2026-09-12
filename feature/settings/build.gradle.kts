plugins {
    id("appdev.android.library")
    // Hilt（SettingsViewModel @HiltViewModel）
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.yumiru11.githubapp.feature.settings"

    defaultConfig {
        // core:github-auth 库 manifest 的 ${appAuthRedirectScheme} 占位符（ADR-0001 自定义 scheme；
        // Robolectric 测试开资源表后 testManifest 合并需要，feature/issue 等同款）
        manifestPlaceholders["appAuthRedirectScheme"] = "com.yumiru11.githubapp"
    }

    // Robolectric compose 语义测试需要资源表（#87 SectionHeaderSemanticsTest；designsystem 同款配置）
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

    // Material Icons Core（Icons.Default/Outlined/Rounded/Filled 图标风格预览）
    implementation(libs.compose.material.icons.core)

    // Lifecycle（viewModelScope）
    implementation(libs.lifecycle.viewmodel.compose)
    // #167 / UI04：背景图的系统 Photo Picker（rememberLauncherForActivityResult +
    // PickVisualMedia —— Android 13+ 即 Material You 风格的系统选择器）
    implementation(libs.activity.compose)

    // 偏好仓库（T24 全部设置项持久化）
    implementation(project(":core:datastore"))

    // 设计令牌（AppMotion/AppDimens 滑杆预览）
    implementation(project(":core:designsystem"))

    // 认证核心（PAT 开发者模式：TokenStorage/OAuthSessionManager/AuthState）
    implementation(project(":core:github-auth"))

    // 限流快照（GATE-2：开发者分组「剩余配额」行直读 RateLimitStore，与 feature:search 同源）
    implementation(project(":core:github-rest"))

    // Hilt（SettingsViewModel @HiltViewModel 装配）
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    // 测试：core:testing 已 api 导出 JUnit4/Robolectric/Roborazzi/compose-test/coroutines-test
    testImplementation(project(":core:testing"))
    // MockK（mock OAuthSessionManager）+ Turbine（Flow 断言）
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
}
