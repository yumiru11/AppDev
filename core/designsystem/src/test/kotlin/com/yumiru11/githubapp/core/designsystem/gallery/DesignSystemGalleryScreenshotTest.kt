package com.yumiru11.githubapp.core.designsystem.gallery

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.yumiru11.githubapp.core.testing.screenshot.captureScreenshotDeterministic
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 设计系统画廊截图基线（22 帧 = 11 分节 × 明/暗；DIALOG 分节见
 * [DesignSystemGalleryDialogScreenshotTest]）。
 *
 * 基准 PNG：`core/designsystem/src/test/screenshots/DesignSystemGallery_<section>_<theme>.png`
 * （入库；**只能由 CI "Record screenshots (CI canonical)" workflow 录制**，本机录制无效）。
 *
 * 统一走 [captureScreenshotDeterministic]：LOADING_STATE 分节含 `LoadingIndicator`
 * 无限动画，且确定性路径保证「录制/校验两次运行 PNG 逐字节相同」（根因见该函数 KDoc）。
 * 分节内的 `darkTheme` 与 AppTheme 的 `themeMode` 显式同向传入——不依赖 Robolectric 的
 * 系统深浅色默认值（否则会出现「暗色命名、浅色内容」的假帧，SettingsScreen 先例）。
 *
 * 测试命名规范：methodName_scenario_expectedBehavior。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class DesignSystemGalleryScreenshotTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun captureSection(
        section: GallerySection,
        darkTheme: Boolean,
    ) {
        composeRule.captureScreenshotDeterministic(
            name = "DesignSystemGallery_${section.id}_${themeSuffix(darkTheme)}",
            darkTheme = darkTheme,
        ) {
            GallerySectionFrame(section = section, darkTheme = darkTheme)
        }
    }

    private fun themeSuffix(darkTheme: Boolean): String = if (darkTheme) "dark" else "light"

    @Test
    fun galleryCards_lightTheme_matchesBaseline() = captureSection(GallerySection.CARDS, darkTheme = false)

    @Test
    fun galleryCards_darkTheme_matchesBaseline() = captureSection(GallerySection.CARDS, darkTheme = true)

    @Test
    fun galleryCardGroup_lightTheme_matchesBaseline() = captureSection(GallerySection.CARD_GROUP, darkTheme = false)

    @Test
    fun galleryCardGroup_darkTheme_matchesBaseline() = captureSection(GallerySection.CARD_GROUP, darkTheme = true)

    @Test
    fun galleryFilterChips_lightTheme_matchesBaseline() = captureSection(GallerySection.FILTER_CHIPS, darkTheme = false)

    @Test
    fun galleryFilterChips_darkTheme_matchesBaseline() = captureSection(GallerySection.FILTER_CHIPS, darkTheme = true)

    @Test
    fun galleryAssistChips_lightTheme_matchesBaseline() = captureSection(GallerySection.ASSIST_CHIPS, darkTheme = false)

    @Test
    fun galleryAssistChips_darkTheme_matchesBaseline() = captureSection(GallerySection.ASSIST_CHIPS, darkTheme = true)

    @Test
    fun gallerySegmentedButtons_lightTheme_matchesBaseline() = captureSection(GallerySection.SEGMENTED_BUTTONS, darkTheme = false)

    @Test
    fun gallerySegmentedButtons_darkTheme_matchesBaseline() = captureSection(GallerySection.SEGMENTED_BUTTONS, darkTheme = true)

    @Test
    fun galleryEmptyState_lightTheme_matchesBaseline() = captureSection(GallerySection.EMPTY_STATE, darkTheme = false)

    @Test
    fun galleryEmptyState_darkTheme_matchesBaseline() = captureSection(GallerySection.EMPTY_STATE, darkTheme = true)

    @Test
    fun galleryErrorState_lightTheme_matchesBaseline() = captureSection(GallerySection.ERROR_STATE, darkTheme = false)

    @Test
    fun galleryErrorState_darkTheme_matchesBaseline() = captureSection(GallerySection.ERROR_STATE, darkTheme = true)

    @Test
    fun galleryLoadingState_lightTheme_matchesBaseline() = captureSection(GallerySection.LOADING_STATE, darkTheme = false)

    @Test
    fun galleryLoadingState_darkTheme_matchesBaseline() = captureSection(GallerySection.LOADING_STATE, darkTheme = true)

    @Test
    fun galleryStateChips_lightTheme_matchesBaseline() = captureSection(GallerySection.STATE_CHIPS, darkTheme = false)

    @Test
    fun galleryStateChips_darkTheme_matchesBaseline() = captureSection(GallerySection.STATE_CHIPS, darkTheme = true)

    @Test
    fun galleryLongBarAction_lightTheme_matchesBaseline() = captureSection(GallerySection.LONG_BAR_ACTION, darkTheme = false)

    @Test
    fun galleryLongBarAction_darkTheme_matchesBaseline() = captureSection(GallerySection.LONG_BAR_ACTION, darkTheme = true)

    @Test
    fun galleryColorRoles_lightTheme_matchesBaseline() = captureSection(GallerySection.COLOR_ROLES, darkTheme = false)

    @Test
    fun galleryColorRoles_darkTheme_matchesBaseline() = captureSection(GallerySection.COLOR_ROLES, darkTheme = true)
}
