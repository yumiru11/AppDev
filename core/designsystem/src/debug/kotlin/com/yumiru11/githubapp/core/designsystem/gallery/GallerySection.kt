package com.yumiru11.githubapp.core.designsystem.gallery

/**
 * 设计系统画廊分节（debug-only，ADR-0010 决策 10 / implementation-plan §7）。
 *
 * 独立成文件是为了满足 detekt `MatchingDeclarationName`（同一文件只允许一个类级声明且须与
 * 文件名同名）；画廊本身在 [DesignSystemGallery]。
 */
enum class GallerySection(
    /** 帧名使用的稳定 id：**改名即基线改名**（需 CI canonical 重录），不要随手改。 */
    val id: String,
) {
    CARDS("cards"),
    CARD_GROUP("card_group"),
    FILTER_CHIPS("filter_chips"),
    ASSIST_CHIPS("assist_chips"),
    SEGMENTED_BUTTONS("segmented_buttons"),
    DIALOG("dialog"),
    EMPTY_STATE("empty_state"),
    ERROR_STATE("error_state"),
    LOADING_STATE("loading_state"),
    STATE_CHIPS("state_chips"),
    LONG_BAR_ACTION("long_bar_action"),
    COLOR_ROLES("color_roles"),
}
