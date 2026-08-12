plugins {
    id("appdev.android.library")
    alias(libs.plugins.roborazzi)
    // Hilt（AuthViewModel @HiltViewModel）
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.yumiru11.githubapp.feature.auth"

    defaultConfig {
        // AppAuth 库 manifest 的 ${appAuthRedirectScheme} 占位符（ADR-0001 自定义 scheme）：
        // feature:auth 依赖 core:github-auth，库 manifest 合入本模块（含测试 manifest）时须提供值。
        manifestPlaceholders["appAuthRedirectScheme"] = "com.yumiru11.githubapp"
    }

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

    // 认证核心（T4 Wave2 接线：OAuthSessionManager / TokenStorage / AuthState / OAuthConfig）
    implementation(project(":core:github-auth"))

    // Hilt（AuthViewModel @HiltViewModel 装配）
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    // Lifecycle（viewModelScope）
    implementation(libs.lifecycle.viewmodel.compose)

    // 测试：core:testing 已 api 导出 JUnit4/Robolectric/Roborazzi/compose-test/coroutines-test
    testImplementation(project(":core:testing"))
    // MockK（mock OAuthSessionManager）+ Turbine（导航事件流断言）
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
}
