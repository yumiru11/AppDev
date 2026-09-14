plugins {
    id("appdev.android.library")
    // 截图基线（Roborazzi）：提供 recordRoborazziDebug / verifyRoborazziDebug 任务
    alias(libs.plugins.roborazzi)
    // #Hilt：编辑器 @mention 候选需要仓库协作者数据源（EditorMentionsViewModel）
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.yumiru11.githubapp.feature.editor"

    defaultConfig {
        // core:github-auth 库 manifest 的 ${appAuthRedirectScheme} 占位符（ADR-0001 自定义 scheme；
        // 本模块经 core:github-data 传递依赖 core:github-auth，单元测试 manifest 合并需要）。
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
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    // Compose Material Icons (core icons)
    implementation(libs.compose.material.icons.core)

    // Material Symbols (icons)
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

    // Hilt（EditorMentionsViewModel：@mention 候选从仓库层注入，测试可直接构造 VM 注入假仓库）
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // 仓库层（SPEC-3：@mention 候选源 = 仓库协作者，RepositoryRepository.listCollaborators）
    implementation(project(":core:github-data"))

    // Testing
    testImplementation(project(":core:testing"))
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
    testImplementation(kotlin("test"))
}
