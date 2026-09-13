package com.yumiru11.githubapp.core.designsystem.gallery

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
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
import com.yumiru11.githubapp.core.designsystem.R
import com.yumiru11.githubapp.core.designsystem.component.AppBottomSheet
import com.yumiru11.githubapp.core.designsystem.component.AppCard
import com.yumiru11.githubapp.core.designsystem.component.AppCenteredLoadingState
import com.yumiru11.githubapp.core.designsystem.component.AppChip
import com.yumiru11.githubapp.core.designsystem.component.AppDialog
import com.yumiru11.githubapp.core.designsystem.component.AppEmptyState
import com.yumiru11.githubapp.core.designsystem.component.AppErrorState
import com.yumiru11.githubapp.core.designsystem.component.AppFilterChip
import com.yumiru11.githubapp.core.designsystem.component.AppLoadingState
import com.yumiru11.githubapp.core.designsystem.component.AppScaffold
import com.yumiru11.githubapp.core.designsystem.component.AppSegmentedButton
import com.yumiru11.githubapp.core.designsystem.component.AppStateChip
import com.yumiru11.githubapp.core.designsystem.component.CardGroup
import com.yumiru11.githubapp.core.designsystem.component.GitHubStatus
import com.yumiru11.githubapp.core.designsystem.component.LongBarAction
import com.yumiru11.githubapp.core.designsystem.icon.AppDevOcticons
import com.yumiru11.githubapp.core.designsystem.theme.extendedColors
import com.yumiru11.githubapp.core.designsystem.token.AppDimens

/*
 * 设计系统画廊的**分节内容**（debug-only，ADR-0010 决策 10 / implementation-plan §7）。
 *
 * 独立成文件的两个原因（2026-09-13 实测）：
 * 1. detekt `TooManyFunctions`（阈值 20）：分节 composable 与画廊骨架分开承载；
 * 2. 分节 → 内容的映射（`GALLERY_SECTION_CONTENT`，在 `DesignSystemGallery.kt`）不再用
 *    14 分支的穷举 `when`——`CyclomaticComplexMethod` 会随分节线性增长，穷举性改由
 *    `DesignSystemGalleryTest.gallerySectionContentMap_coversEveryEntry` 守住。
 *
 * 全部 `internal`：只被同包（debug source set）的 [GallerySection] 枚举引用。
 */

/** 容器：默认 / 可点击 / 自定义容器色（surfaceVariant，迁移批的实况用法）。 */
@Composable
internal fun CardsSection() {
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
internal fun CardGroupSection() {
    CardGroup {
        item { GalleryItemText(text = stringResource(R.string.gallery_card_group_first)) }
        item { GalleryItemText(text = stringResource(R.string.gallery_card_group_second)) }
        item { GalleryItemText(text = stringResource(R.string.gallery_card_group_third)) }
    }
}

/** 选择：未选中 / 选中 / 禁用 / 选中 + 前置图标（UI-2 选中态四态同屏）。 */
@Composable
internal fun FilterChipsSection() {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(AppDimens.spacing.s),
        verticalArrangement = Arrangement.spacedBy(AppDimens.spacing.xs),
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
internal fun AssistChipsSection() {
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
internal fun SegmentedButtonsSection() {
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

/** 画廊内脚手架帧的固定高度（分节帧是首屏快照，铺满会把分节标题挤出帧外）。 */
private val SCAFFOLD_FRAME_HEIGHT = 360.dp

/**
 * 容器：页面脚手架（[AppScaffold]）——顶栏 + 内容 + FAB 的整屏形态；
 * 覆盖「默认 `containerColor`/`contentColor`/insets」下顶栏与内容的内边距避让。
 */
@Composable
internal fun ScaffoldSection() {
    AppScaffold(
        modifier = Modifier.fillMaxWidth().height(SCAFFOLD_FRAME_HEIGHT),
        topBar = {
            Surface(color = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = 3.dp) {
                Text(
                    text = stringResource(R.string.gallery_scaffold_top_bar),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.fillMaxWidth().padding(AppDimens.spacing.l),
                )
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {}) {
                Icon(imageVector = AppDevOcticons.Star, contentDescription = null)
            }
        },
    ) { padding ->
        Text(
            text = stringResource(R.string.gallery_scaffold_content),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(padding).padding(AppDimens.spacing.l),
        )
    }
}

/**
 * 容器：底部弹层（[AppBottomSheet]，独立 window —— 帧走多窗口捕获路径，见
 * [DesignSystemGalleryWindowScreenshotTest]）。
 *
 * `@OptIn`：`sheetState`/`properties` 是 M3 实验性类型，函数签名含它们时调用点仍需 opt-in
 * （Kotlin 实验标记传播）；画廊这些分节没有别的实验性调用，所以在此显式声明。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BottomSheetSection() {
    AppBottomSheet(onDismissRequest = {}) {
        Column(modifier = Modifier.fillMaxWidth().padding(AppDimens.spacing.l)) {
            Text(
                text = stringResource(R.string.gallery_bottom_sheet_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(R.string.gallery_bottom_sheet_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = AppDimens.spacing.s),
            )
        }
    }
}

/** 容器：对话框（独立 window；帧由多窗口捕获路径拍摄，见 [DesignSystemGalleryWindowScreenshotTest]）。 */
@Composable
internal fun DialogSection() {
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
internal fun EmptyStateSection() {
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
internal fun ErrorStateSection() {
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
internal fun LoadingStateSection() {
    Column(verticalArrangement = Arrangement.spacedBy(GALLERY_ITEM_GAP)) {
        AppLoadingState(label = stringResource(R.string.gallery_loading_label))
        AppCenteredLoadingState(modifier = Modifier.fillMaxWidth().height(160.dp))
    }
}

/** 状态：四态徽标（open/closed/merged/draft 语义色各不相同）。 */
@Composable
internal fun StateChipsSection() {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(AppDimens.spacing.s),
        verticalArrangement = Arrangement.spacedBy(AppDimens.spacing.s),
    ) {
        AppStateChip(status = GitHubStatus.OPEN, label = stringResource(R.string.gallery_state_open))
        AppStateChip(status = GitHubStatus.CLOSED, label = stringResource(R.string.gallery_state_closed))
        AppStateChip(status = GitHubStatus.MERGED, label = stringResource(R.string.gallery_state_merged))
        AppStateChip(status = GitHubStatus.DRAFT, label = stringResource(R.string.gallery_state_draft))
    }
}

/** 容器：长条按钮（可用 / 禁用两态）。 */
@Composable
internal fun LongBarActionSection() {
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
internal fun ColorRolesSection() {
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
        verticalArrangement = Arrangement.spacedBy(AppDimens.spacing.xs),
    ) {
        Box(
            modifier =
                Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(AppDimens.cornerSmall))
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
        modifier = Modifier.padding(horizontal = AppDimens.spacing.m, vertical = AppDimens.spacing.l),
    )
}
