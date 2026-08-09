// ===== PROTOTYPE 模块（可抛弃代码，勿当生产用）=====
// 问题：mikepenz multiplatform-markdown-renderer 0.43.0（+m3 / +code / +coil3）在
// Kotlin 2.3.21 / Compose BOM 2026.06.01 上渲染 GitHub Markdown 是否成立？视觉是否贴合 Material You？
// 验证完成交付结论后，本模块整体删除；生产实现进 core:markdown + core:designsystem。
// 分支：prototype/markdown-renderer
plugins {
    id("appdev.android.library")
    alias(libs.plugins.roborazzi)
}

android {
    namespace = "com.yumiru11.githubapp.prototype.md"

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

    // Markdown 渲染链路（与生产同版本）
    implementation(libs.markdown.renderer)
    implementation(libs.markdown.renderer.m3)
    implementation(libs.markdown.renderer.code)
    implementation(libs.markdown.renderer.coil3)

    // 图片（Coil 3 + okhttp 网络）
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    // 图标（Material Symbols，cmp = ImageVector；需 base artifact 提供 MaterialSymbols 对象）
    implementation(libs.icons.material.symbols.base.cmp)
    implementation(libs.icons.material.symbols.outlined.cmp)
    implementation(libs.icons.material.symbols.rounded.cmp)

    // 测试：Robolectric + Roborazzi 截图（Linux 纯 JVM 免模拟器）
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.junit.rule)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.compose.ui.test.junit4)
    testImplementation(libs.compose.ui.test.manifest)
}

configurations.all {
    resolutionStrategy {
        force("com.squareup.okhttp3:okhttp:${libs.versions.okhttp.get()}")
        // renderer-coil3 桥依赖 coil 3.5.0（要求 compileSdk 36，本机不可用）——强制回 3.4.0
        force("io.coil-kt.coil3:coil:${libs.versions.coil.get()}")
        force("io.coil-kt.coil3:coil-android:${libs.versions.coil.get()}")
        force("io.coil-kt.coil3:coil-compose:${libs.versions.coil.get()}")
        force("io.coil-kt.coil3:coil-compose-core:${libs.versions.coil.get()}")
        force("io.coil-kt.coil3:coil-core:${libs.versions.coil.get()}")
    }
}