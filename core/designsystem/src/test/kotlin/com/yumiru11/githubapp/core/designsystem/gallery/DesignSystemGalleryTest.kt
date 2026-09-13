package com.yumiru11.githubapp.core.designsystem.gallery

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import com.yumiru11.githubapp.core.designsystem.R
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 画廊 debug 入口的渲染冒烟测试（非截图）。
 *
 * 为什么需要：整屏 [DesignSystemGallery] 与 DIALOG 分节的截图帧走 Roborazzi 多窗口/独立
 * 活动路径，其覆盖不计入 JaCoCo；本测试用普通 compose 组合把这两条路径钉进覆盖率，
 * 同时断言「按 sections 过滤」与「DIALOG 标题可解析」两个真实行为。
 *
 * 测试命名规范：methodName_scenario_expectedBehavior。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class DesignSystemGalleryTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun text(resId: Int): String = ApplicationProvider.getApplicationContext<Context>().getString(resId)

    @Test
    fun designSystemGallery_allSections_rendersEverySectionTitle() {
        composeRule.setContent { DesignSystemGallery() }
        // 12 个分节纵向排列，多数标题在首屏外 —— 用 assertExists（组合已发生）而非可见性断言
        GallerySection.entries.forEach { section ->
            composeRule.onNodeWithText(text(sectionTitleRes(section))).assertExists()
        }
    }

    @Test
    fun designSystemGallery_singleSection_rendersOnlyRequestedSection() {
        composeRule.setContent { DesignSystemGallery(sections = listOf(GallerySection.CARDS)) }
        composeRule.onNodeWithText(text(R.string.gallery_section_cards)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.gallery_section_filter_chips)).assertDoesNotExist()
    }

    @Test
    fun gallerySectionFrame_defaultTheme_rendersDialogSection() {
        composeRule.setContent { GallerySectionFrame(section = GallerySection.DIALOG) }
        composeRule.onNodeWithText(text(R.string.gallery_section_dialog)).assertExists()
        composeRule.onNodeWithText(text(R.string.gallery_dialog_title)).assertExists()
    }

    private fun sectionTitleRes(section: GallerySection): Int =
        when (section) {
            GallerySection.CARDS -> R.string.gallery_section_cards
            GallerySection.CARD_GROUP -> R.string.gallery_section_card_group
            GallerySection.FILTER_CHIPS -> R.string.gallery_section_filter_chips
            GallerySection.ASSIST_CHIPS -> R.string.gallery_section_assist_chips
            GallerySection.SEGMENTED_BUTTONS -> R.string.gallery_section_segmented_buttons
            GallerySection.DIALOG -> R.string.gallery_section_dialog
            GallerySection.EMPTY_STATE -> R.string.gallery_section_empty_state
            GallerySection.ERROR_STATE -> R.string.gallery_section_error_state
            GallerySection.LOADING_STATE -> R.string.gallery_section_loading_state
            GallerySection.STATE_CHIPS -> R.string.gallery_section_state_chips
            GallerySection.LONG_BAR_ACTION -> R.string.gallery_section_long_bar_action
            GallerySection.COLOR_ROLES -> R.string.gallery_section_color_roles
        }
}
