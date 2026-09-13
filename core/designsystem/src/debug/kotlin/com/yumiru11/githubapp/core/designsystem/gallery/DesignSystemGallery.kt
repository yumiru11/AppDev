package com.yumiru11.githubapp.core.designsystem.gallery

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yumiru11.githubapp.core.datastore.model.ThemeMode
import com.yumiru11.githubapp.core.designsystem.R
import com.yumiru11.githubapp.core.designsystem.component.AppCard
import com.yumiru11.githubapp.core.designsystem.component.AppCenteredLoadingState
import com.yumiru11.githubapp.core.designsystem.component.AppChip
import com.yumiru11.githubapp.core.designsystem.component.AppDialog
import com.yumiru11.githubapp.core.designsystem.component.AppEmptyState
import com.yumiru11.githubapp.core.designsystem.component.AppErrorState
import com.yumiru11.githubapp.core.designsystem.component.AppFilterChip
import com.yumiru11.githubapp.core.designsystem.component.AppLoadingState
import com.yumiru11.githubapp.core.designsystem.component.AppSegmentedButton
import com.yumiru11.githubapp.core.designsystem.component.AppStateChip
import com.yumiru11.githubapp.core.designsystem.component.CardGroup
import com.yumiru11.githubapp.core.designsystem.component.GitHubStatus
import com.yumiru11.githubapp.core.designsystem.component.LongBarAction
import com.yumiru11.githubapp.core.designsystem.icon.AppDevOcticons
import com.yumiru11.githubapp.core.designsystem.theme.AppTheme
import com.yumiru11.githubapp.core.designsystem.theme.extendedColors

/*
 * 设计系统画廊（**debug-only**，ADR-0010 决策 10 / `docs/design-system/implementation-plan.md` §7）。
 *
 * ## 为什么存在
 *
 * 组件契约需要**可执行的可视证据**：每个新建/收编组件都要有「明/暗 × 关键状态」的
 * Roborazzi 基线（DoD §6.1），而不是只靠 KDoc。本文件位于 `src/debug`，**不进 release 产物**。
 *
 * ## 截图测试怎么用
 *
 * [GallerySectionFrame] 只渲染一个分节，是截图测试的入口
 * （`DesignSystemGalleryScreenshotTest` 逐节 × 明暗各拍一帧，帧名
 * `DesignSystemGallery_<section.id>_<light|dark>`）。[DesignSystemGallery] 是可滚动的
 * 整屏形态，供真机 debug 走查（不接导航，直接组合）。
 *
 * ## 为什么 DIALOG 分节单独拍
 *
 * `Dialog` 渲染在**独立 window**：`captureScreenshotDeterministic` 手工绘制
 * `activity.window.decorView`，拍不到 dialog window。对话框分节因此由
 * `DesignSystemGalleryDialogScreenshotTest` 走 Roborazzi 的多窗口捕获路径拍（无无限动画，
 * 不会触发 `idle()` 挂死面）。其余分节一律走确定性捕获。
 *
 * ## 文案
 *
 * 全部经 `stringResource`（`src/debug/res` 的 en + zh-rCN 成对资源）；颜色角色分节的
 * token 名（primary/secondary/…）在 en 侧标记 `translatable="false"`——它们是开发者标识符。
 */

/** 分节间距 / 内边距（画廊自身的排版常量，不进组件契约）。 */
private val GALLERY_PADDING = 16.dp

private val GALLERY_SECTION_GAP = 24.dp

private val GALLERY_ITEM_GAP = 12.dp

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
        text = stringResource(section.titleRes()),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

private fun GallerySection.titleRes(): Int =
    when (this) {
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

@Composable
private fun GallerySectionContent(section: GallerySection) {
    when (section) {
        GallerySection.CARDS -> CardsSection()
        GallerySection.CARD_GROUP -> CardGroupSection()
        GallerySection.FILTER_CHIPS -> FilterChipsSection()
        GallerySection.ASSIST_CHIPS -> AssistChipsSection()
        GallerySection.SEGMENTED_BUTTONS -> SegmentedButtonsSection()
        GallerySection.DIALOG -> DialogSection()
        GallerySection.EMPTY_STATE -> EmptyStateSection()
        GallerySection.ERROR_STATE -> ErrorStateSection()
        GallerySection.LOADING_STATE -> LoadingStateSection()
        GallerySection.STATE_CHIPS -> StateChipsSection()
        GallerySection.LONG_BAR_ACTION -> LongBarActionSection()
        GallerySection.COLOR_ROLES -> ColorRolesSection()
    }
}

/** 容器：默认 / 可点击 / 自定义容器色（surfaceVariant，迁移批的实况用法）。 */
@Composable
private fun CardsSection() {
    Column(verticalArrangement = Arrangement.spacedBy(GALLERY_ITEM_GAP)) {
        AppCard(modifier = Modifier.fillMaxWidth()) {
            GalleryItemText(text = stringResource(R.string.gallery_cards_default))
        }
        AppCard(modifier = Modifier.fillMaxWidth(), onClick = {}) {
            GalleryItemText(text = stringResource(R.string.gallery_cards_clickable))
        }
        AppCard(
            modifier = Modifier.fillMaxWidth(),
            onClick = {},
            colors =
                CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
        ) {
            GalleryItemText(text = stringResource(R.string.gallery_cards_variant))
        }
    }
}

/** 容器：分段卡（既有组件，和 AppCard 同屏对照圆角层级）。 */
@Composable
private fun CardGroupSection() {
    CardGroup {
        item { GalleryItemText(text = stringResource(R.string.gallery_card_group_first)) }
        item { GalleryItemText(text = stringResource(R.string.gallery_card_group_second)) }
        item { GalleryItemText(text = stringResource(R.string.gallery_card_group_third)) }
    }
}

/** 选择：未选中 / 选中 / 禁用 / 选中 + 前置图标（UI-2 选中态四态同屏）。 */
@Composable
private fun FilterChipsSection() {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        AppFilterChip(
            selected = false,
            onClick = {},
            label = stringResource(R.string.gallery_filter_chip),
        )
        AppFilterChip(
            selected = true,
            onClick = {},
            label = stringResource(R.string.gallery_filter_chip_selected),
        )
        AppFilterChip(
            selected = false,
            onClick = {},
            label = stringResource(R.string.gallery_filter_chip_disabled),
            enabled = false,
        )
        AppFilterChip(
            selected = true,
            onClick = {},
            label = stringResource(R.string.gallery_filter_chip_icon),
            leadingIcon = AppDevOcticons.Check,
        )
    }
}

/** 反馈：assist 语义 chip（默认 / 禁用）。 */
@Composable
private fun AssistChipsSection() {
    Column(verticalArrangement = Arrangement.spacedBy(GALLERY_ITEM_GAP)) {
        AppChip(
            onClick = {},
            label = { Text(text = stringResource(R.string.gallery_assist_chip)) },
        )
        AppChip(
            onClick = {},
            label = { Text(text = stringResource(R.string.gallery_assist_chip_disabled)) },
            enabled = false,
        )
    }
}

/** 选择：三连分段按钮（中间项选中，index/count 形状与真实用法一致）。 */
@Composable
private fun SegmentedButtonsSection() {
    val labels =
        listOf(
            R.string.gallery_segmented_one,
            R.string.gallery_segmented_two,
            R.string.gallery_segmented_three,
        )
    SingleChoiceSegmentedButtonRow {
        labels.forEachIndexed { index, labelRes ->
            AppSegmentedButton(
                selected = index == 1,
                onClick = {},
                label = stringResource(labelRes),
                shape = SegmentedButtonDefaults.itemShape(index = index, count = labels.size),
            )
        }
    }
}

/** 容器：对话框（独立 window；帧由多窗口捕获路径拍摄，见文件 KDoc）。 */
@Composable
private fun DialogSection() {
    AppDialog(
        onDismissRequest = {},
        title = { Text(text = stringResource(R.string.gallery_dialog_title)) },
        text = { Text(text = stringResource(R.string.gallery_dialog_text)) },
        confirmButton = {
            TextButton(onClick = {}) {
                Text(text = stringResource(R.string.gallery_dialog_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = {}) {
                Text(text = stringResource(R.string.gallery_dialog_dismiss))
            }
        },
    )
}

/**
 * 状态：空态 —— 厚包装默认形态（带内置 `Button`）与逃生舱 `action` 槽（自定义 `TextButton`）
 * 同屏对照；第二项锁定「`action` 非空时替代内置按钮」的契约。
 */
@Composable
private fun EmptyStateSection() {
    Column(verticalArrangement = Arrangement.spacedBy(GALLERY_ITEM_GAP)) {
        AppEmptyState(
            icon = AppDevOcticons.Repo,
            title = stringResource(R.string.gallery_empty_title),
            message = stringResource(R.string.gallery_empty_message),
            actionLabel = stringResource(R.string.gallery_empty_action),
            onAction = {},
        )
        AppEmptyState(
            icon = AppDevOcticons.Repo,
            title = stringResource(R.string.gallery_empty_title),
            action = {
                TextButton(onClick = {}) {
                    Text(text = stringResource(R.string.gallery_empty_action))
                }
            },
        )
    }
}

/**
 * 状态：错误态（内置 Alert 图标）—— 厚包装重试按钮与逃生舱 `action` 槽同屏对照。
 */
@Composable
private fun ErrorStateSection() {
    Column(verticalArrangement = Arrangement.spacedBy(GALLERY_ITEM_GAP)) {
        AppErrorState(
            title = stringResource(R.string.gallery_error_title),
            message = stringResource(R.string.gallery_error_message),
            actionLabel = stringResource(R.string.gallery_error_action),
            onAction = {},
        )
        AppErrorState(
            title = stringResource(R.string.gallery_error_title),
            action = {
                TextButton(onClick = {}) {
                    Text(text = stringResource(R.string.gallery_error_action))
                }
            },
        )
    }
}

/** 状态：加载态（含 `LoadingIndicator` 无限动画——必须走确定性捕获）。 */
@Composable
private fun LoadingStateSection() {
    Column(verticalArrangement = Arrangement.spacedBy(GALLERY_ITEM_GAP)) {
        AppLoadingState(label = stringResource(R.string.gallery_loading_label))
        AppCenteredLoadingState(modifier = Modifier.fillMaxWidth().height(160.dp))
    }
}

/** 状态：四态徽标（open/closed/merged/draft 语义色各不相同）。 */
@Composable
private fun StateChipsSection() {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AppStateChip(status = GitHubStatus.OPEN, label = stringResource(R.string.gallery_state_open))
        AppStateChip(status = GitHubStatus.CLOSED, label = stringResource(R.string.gallery_state_closed))
        AppStateChip(status = GitHubStatus.MERGED, label = stringResource(R.string.gallery_state_merged))
        AppStateChip(status = GitHubStatus.DRAFT, label = stringResource(R.string.gallery_state_draft))
    }
}

/** 容器：长条按钮（可用 / 禁用两态）。 */
@Composable
private fun LongBarActionSection() {
    Column(verticalArrangement = Arrangement.spacedBy(GALLERY_ITEM_GAP)) {
        LongBarAction(
            text = stringResource(R.string.gallery_long_bar_enabled),
            icon = AppDevOcticons.Star,
            onClick = {},
        )
        LongBarAction(
            text = stringResource(R.string.gallery_long_bar_disabled),
            icon = AppDevOcticons.Repo,
            onClick = {},
            enabled = false,
        )
    }
}

/**
 * 主题：色角色色板（明暗两帧对照 = 动态色/扩展色适配证据）。
 *
 * 顺序固定 primary → secondary → tertiary → error → success → warning：
 * 前四个取自 `colorScheme`，后两个取自 `ExtendedColors`（零硬编码色）。
 */
@Composable
private fun ColorRolesSection() {
    val scheme = MaterialTheme.colorScheme
    val extended = MaterialTheme.extendedColors
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(GALLERY_ITEM_GAP),
        verticalArrangement = Arrangement.spacedBy(GALLERY_ITEM_GAP),
    ) {
        ColorSwatch(label = stringResource(R.string.gallery_color_primary), color = scheme.primary)
        ColorSwatch(label = stringResource(R.string.gallery_color_secondary), color = scheme.secondary)
        ColorSwatch(label = stringResource(R.string.gallery_color_tertiary), color = scheme.tertiary)
        ColorSwatch(label = stringResource(R.string.gallery_color_error), color = scheme.error)
        ColorSwatch(label = stringResource(R.string.gallery_color_success), color = extended.success)
        ColorSwatch(label = stringResource(R.string.gallery_color_warning), color = extended.warning)
    }
}

@Composable
private fun ColorSwatch(
    label: String,
    color: Color,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(color),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 画廊内的占位正文（12dp 内边距与真实卡片用法一致）。 */
@Composable
private fun GalleryItemText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 16.dp),
    )
}
