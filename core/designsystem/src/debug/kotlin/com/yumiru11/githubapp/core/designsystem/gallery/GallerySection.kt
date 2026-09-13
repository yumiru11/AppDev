package com.yumiru11.githubapp.core.designsystem.gallery

import androidx.annotation.StringRes
import com.yumiru11.githubapp.core.designsystem.R

/**
 * 设计系统画廊分节（debug-only，ADR-0010 决策 10 / implementation-plan §7）。
 *
 * 独立成文件是为了满足 detekt `MatchingDeclarationName`（同一文件只允许一个类级声明且须与
 * 文件名同名）；画廊本身在 [DesignSystemGallery]。
 *
 * 分节→标题资源、分节→内容两块映射分开承载：
 * - 标题资源作为枚举属性（`titleRes`）——调用方与测试直接读，不再各写一个 14 分支 `when`
 *   （detekt `CyclomaticComplexMethod` 会随分节增长判红，2026-09-13 Batch 4 实测）；
 * - 内容映射仍留在 [DesignSystemGallery] 的 `when`（分支体是独立 composable，不可枚举化）。
 */
enum class GallerySection(
    /** 帧名使用的稳定 id：**改名即基线改名**（需 CI canonical 重录），不要随手改。 */
    val id: String,
    /** 分节标题（debug res；en + zh-rCN 成对）。 */
    @StringRes val titleRes: Int,
) {
    CARDS("cards", R.string.gallery_section_cards),
    CARD_GROUP("card_group", R.string.gallery_section_card_group),
    FILTER_CHIPS("filter_chips", R.string.gallery_section_filter_chips),
    ASSIST_CHIPS("assist_chips", R.string.gallery_section_assist_chips),
    SEGMENTED_BUTTONS("segmented_buttons", R.string.gallery_section_segmented_buttons),
    DIALOG("dialog", R.string.gallery_section_dialog),
    BOTTOM_SHEET("bottom_sheet", R.string.gallery_section_bottom_sheet),
    SCAFFOLD("scaffold", R.string.gallery_section_scaffold),
    EMPTY_STATE("empty_state", R.string.gallery_section_empty_state),
    ERROR_STATE("error_state", R.string.gallery_section_error_state),
    LOADING_STATE("loading_state", R.string.gallery_section_loading_state),
    STATE_CHIPS("state_chips", R.string.gallery_section_state_chips),
    LONG_BAR_ACTION("long_bar_action", R.string.gallery_section_long_bar_action),
    COLOR_ROLES("color_roles", R.string.gallery_section_color_roles),
}
