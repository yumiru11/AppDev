package com.yumiru11.githubapp.core.designsystem.gallery

import com.yumiru11.githubapp.core.testing.screenshot.ScreenshotTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 设计系统画廊**独立 window 分节**截图基线（4 帧 = 2 分节 × 明/暗）。
 *
 * ## 为什么单独一个测试类
 *
 * [GallerySection.DIALOG] 与 [GallerySection.BOTTOM_SHEET] 渲染在**独立 window**
 * （Dialog / ModalBottomSheet 各自是 `Dialog` → 自带 `ViewRootImpl`）：
 * `captureScreenshotDeterministic` 手工绘制 `activity.window.decorView` 拍不到它们；
 * [ScreenshotTest.captureScreenshot] 走的是 Roborazzi 的 compose 多窗口捕获路径
 * （`captureScreenIfMultipleWindows`），dialog window 会被捕获合成。
 * 这两个分节没有无限动画（ModalBottomSheet 的入场是有限动画），不会触发 `idle()` 挂死面
 * （挂死根因见 core:testing 的 `captureScreenshotDeterministic` KDoc），因此这条路径是安全且必要的。
 *
 * 基准 PNG：`core/designsystem/src/test/screenshots/DesignSystemGallery_<section>_<theme>.png`
 * （入库；只能由 CI "Record screenshots (CI canonical)" workflow 录制）。
 *
 * 测试命名规范：methodName_scenario_expectedBehavior。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class DesignSystemGalleryWindowScreenshotTest : ScreenshotTest() {
    @Test
    fun galleryDialog_lightTheme_matchesBaseline() {
        captureScreenshot("DesignSystemGallery_dialog_light", darkTheme = false) {
            GallerySectionFrame(section = GallerySection.DIALOG, darkTheme = false)
        }
    }

    @Test
    fun galleryDialog_darkTheme_matchesBaseline() {
        captureScreenshot("DesignSystemGallery_dialog_dark", darkTheme = true) {
            GallerySectionFrame(section = GallerySection.DIALOG, darkTheme = true)
        }
    }

    @Test
    fun galleryBottomSheet_lightTheme_matchesBaseline() {
        captureScreenshot("DesignSystemGallery_bottom_sheet_light", darkTheme = false) {
            GallerySectionFrame(section = GallerySection.BOTTOM_SHEET, darkTheme = false)
        }
    }

    @Test
    fun galleryBottomSheet_darkTheme_matchesBaseline() {
        captureScreenshot("DesignSystemGallery_bottom_sheet_dark", darkTheme = true) {
            GallerySectionFrame(section = GallerySection.BOTTOM_SHEET, darkTheme = true)
        }
    }
}
