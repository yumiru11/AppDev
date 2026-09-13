package com.yumiru11.githubapp.core.designsystem.gallery

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.yumiru11.githubapp.core.datastore.model.ThemeMode
import com.yumiru11.githubapp.core.designsystem.theme.AppTheme
import com.yumiru11.githubapp.core.designsystem.token.AppDimens

/*
 * 设计系统画廊（**debug-only**，ADR-0010 决策 10 / `docs/design-system/implementation-plan.md` §7）。
 *
 * ## 为什么存在
 *
 * 组件契约需要**可执行的可视证据**：每个新建/收编组件都要有「明/暗 × 关键状态」的
 * Roborazzi 基线（DoD §6.1），而不是只靠 KDoc。本文件位于 `src/debug`，**不进 release 产物**。
 *
 * ## 文件分工
 *
 * - 本文件 = 画廊骨架（整屏 / 单分节帧 / 分节标题 / 分节→内容映射 / 排版常量）；
 * - `GallerySection.kt` = 分节枚举（id / 标题资源）；
 * - `GallerySections.kt` = 各分节的 composable 实现。
 *
 * ## 截图测试怎么用
 *
 * [GallerySectionFrame] 只渲染一个分节，是截图测试的入口
 * （`DesignSystemGalleryScreenshotTest` 逐节 × 明暗各拍一帧，帧名
 * `DesignSystemGallery_<section.id>_<light|dark>`）。[DesignSystemGallery] 是可滚动的
 * 整屏形态，供真机 debug 走查（不接导航，直接组合）。
 *
 * ## 为什么 DIALOG / BOTTOM_SHEET 分节单独拍
 *
 * `Dialog` 与 `ModalBottomSheet` 渲染在**独立 window**：`captureScreenshotDeterministic`
 * 手工绘制 `activity.window.decorView`，拍不到它们。这两个分节因此由
 * `DesignSystemGalleryWindowScreenshotTest` 走 Roborazzi 的多窗口捕获路径拍（无无限动画，
 * 不会触发 `idle()` 挂死面）。其余分节一律走确定性捕获。
 *
 * ## 文案
 *
 * 全部经 `stringResource`（`src/debug/res` 的 en + zh-rCN 成对资源）；颜色角色分节的
 * token 名（primary/secondary/…）在 en 侧标记 `translatable="false"`——它们是开发者标识符。
 */

/** 分节间距 / 内边距（画廊自身的排版常量，不进组件契约；分节实现共用，故 internal）。 */
internal val GALLERY_PADDING = AppDimens.spacing.l

internal val GALLERY_SECTION_GAP = AppDimens.spacing.xl

internal val GALLERY_ITEM_GAP = AppDimens.spacing.m

/**
 * 分节 → 内容映射。
 *
 * ## 为什么不是 `when`
 *
 * 原先「分节 → 内容」是 14 分支的穷举 `when`：每加一个分节就把 detekt
 * `CyclomaticComplexMethod` 往上顶一格（2026-09-13 Batch 4 实测 15 即判红）。映射表没有
 * 分支复杂度；穷举性改由 `DesignSystemGalleryTest.gallerySectionContentMap_coversEveryEntry`
 * 守住——新增分节不加表条目，该测试必红（红→绿可验证，不是恒真守卫）。
 */
internal val GALLERY_SECTION_CONTENT: Map<GallerySection, @Composable () -> Unit> =
    mapOf(
        GallerySection.CARDS to { CardsSection() },
        GallerySection.CARD_GROUP to { CardGroupSection() },
        GallerySection.FILTER_CHIPS to { FilterChipsSection() },
        GallerySection.ASSIST_CHIPS to { AssistChipsSection() },
        GallerySection.SEGMENTED_BUTTONS to { SegmentedButtonsSection() },
        GallerySection.DIALOG to { DialogSection() },
        GallerySection.BOTTOM_SHEET to { BottomSheetSection() },
        GallerySection.SCAFFOLD to { ScaffoldSection() },
        GallerySection.EMPTY_STATE to { EmptyStateSection() },
        GallerySection.ERROR_STATE to { ErrorStateSection() },
        GallerySection.LOADING_STATE to { LoadingStateSection() },
        GallerySection.STATE_CHIPS to { StateChipsSection() },
        GallerySection.LONG_BAR_ACTION to { LongBarActionSection() },
        GallerySection.COLOR_ROLES to { ColorRolesSection() },
    )

/**
 * 整屏画廊（debug 走查入口）：按顺序渲染 [sections]，可滚动。
 *
 * @param darkTheme 明暗主题（默认跟随系统；截图测试显式传入，避免 Robolectric 默认浅色
 *   把「暗色命名」拍成浅色内容）
 * @see GallerySectionFrame 截图测试用的单分节入口
 */
@Composable
fun DesignSystemGallery(
    modifier: Modifier = Modifier,
    sections: List<GallerySection> = GallerySection.entries,
    darkTheme: Boolean = isSystemInDarkTheme(),
) {
    AppTheme(themeMode = if (darkTheme) ThemeMode.DARK else ThemeMode.LIGHT) {
        Column(
            modifier =
                modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .verticalScroll(rememberScrollState())
                    .padding(GALLERY_PADDING),
            verticalArrangement = Arrangement.spacedBy(GALLERY_SECTION_GAP),
        ) {
            sections.forEach { section ->
                Column(verticalArrangement = Arrangement.spacedBy(GALLERY_ITEM_GAP)) {
                    SectionTitle(section)
                    GallerySectionContent(section)
                }
            }
        }
    }
}

/**
 * 单分节帧（截图测试入口）：整屏背景 + 分节标题 + 分节内容，顶部对齐不滚动。
 *
 * 不滚动是有意的：截图帧 = 首屏，滚动位置不参与基线（避免引入新的不确定性）。
 */
@Composable
fun GallerySectionFrame(
    section: GallerySection,
    modifier: Modifier = Modifier,
    darkTheme: Boolean = isSystemInDarkTheme(),
) {
    AppTheme(themeMode = if (darkTheme) ThemeMode.DARK else ThemeMode.LIGHT) {
        Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(
                modifier = Modifier.fillMaxSize().padding(GALLERY_PADDING),
                verticalArrangement = Arrangement.spacedBy(GALLERY_ITEM_GAP),
            ) {
                SectionTitle(section)
                GallerySectionContent(section)
            }
        }
    }
}

@Composable
private fun SectionTitle(section: GallerySection) {
    Text(
        text = stringResource(section.titleRes),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

@Composable
private fun GallerySectionContent(section: GallerySection) {
    GALLERY_SECTION_CONTENT.getValue(section)()
}
