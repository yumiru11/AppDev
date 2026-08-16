plugins {
    id("appdev.android.library")
}

android {
    namespace = "com.yumiru11.githubapp.core.editor"

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

dependencies {
    // 代码浏览/编辑（plan.md §3.1：Rosemoe Sora Editor + language-textmate，T11/T21 共享）
    // 版本经 sora-editor BOM 对齐（toml sora = 0.23.6）
    implementation(platform(libs.sora.editor.bom))
    implementation(libs.sora.editor)
    implementation(libs.sora.language.textmate)

    // 主题（M3 → Sora 配色映射：bg/text/行号/选区/当前行/语法色）
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.material3)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(project(":core:designsystem"))

    // 测试基建（ScreenshotTest 基类 + Robolectric/Roborazzi，见 AGENTS.md）
    testImplementation(project(":core:testing"))
}
