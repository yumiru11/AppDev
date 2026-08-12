plugins {
    id("appdev.android.library")
    alias(libs.plugins.roborazzi)
}

android {
    namespace = "com.yumiru11.githubapp.core.markdown"

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all {
                // Roborazzi 官方推荐：硬件渲染模式提升截图颜色准确性（符号不再与背景混色）
                it.systemProperties["robolectric.pixelCopyRenderMode"] = "hardware"
            }
        }
    }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.material3)
    implementation(libs.compose.ui)

    // Markdown 渲染链路。
    // ⚠️ 版本覆盖：toml 全局 markdown=0.43.0 需 compileSdk 37 / AGP 9（本机不可用），
    // 按 ADR-0005 决策模块内固定 0.38.1；不动 toml 全局条目（待 AGP 9 迁移后统一升）。
    implementation("com.mikepenz:multiplatform-markdown-renderer:0.38.1")
    implementation("com.mikepenz:multiplatform-markdown-renderer-m3:0.38.1")
    implementation("com.mikepenz:multiplatform-markdown-renderer-code:0.38.1")
    implementation("com.mikepenz:multiplatform-markdown-renderer-coil3:0.38.1")

    // 图片（Coil 3 + okhttp 网络）
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    // 图标（Material Symbols cmp 变体：GitHub Alert 卡片用 MaterialSymbols.Rounded.*）
    implementation(libs.icons.material.symbols.base.cmp)
    implementation(libs.icons.material.symbols.rounded.cmp)

    // KotlinTextMate：VS Code 同款语法高亮（替换 renderer-code 内置 Highlights）
    implementation(libs.textmate.compose)

    // GitHubLinkParser（core:navigation 提供，Markdown 内链接解析后交上层导航）
    implementation(project(":core:navigation"))

    // 测试基建（ScreenshotTest 基类 + Robolectric/Roborazzi，见 AGENTS.md）
    testImplementation(project(":core:testing"))
}

configurations.all {
    resolutionStrategy {
        // renderer-coil3 桥传递依赖 coil 3.5.0（需要更高 compileSdk）→ 强制回 toml 的 3.4.0（原型实测）
        force("io.coil-kt.coil3:coil:${libs.versions.coil.get()}")
        force("io.coil-kt.coil3:coil-android:${libs.versions.coil.get()}")
        force("io.coil-kt.coil3:coil-compose:${libs.versions.coil.get()}")
        force("io.coil-kt.coil3:coil-compose-core:${libs.versions.coil.get()}")
        force("io.coil-kt.coil3:coil-core:${libs.versions.coil.get()}")
    }
}
