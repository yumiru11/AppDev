@file:Suppress("LongMethod")
// 外观分组含 8 个设置项（模式/开关/色盘/滑杆），行内分支样板天然长；精准抑制（T24 先例）。
@file:OptIn(ExperimentalLayoutApi::class)

package com.yumiru11.githubapp.feature.settings

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.yumiru11.githubapp.core.datastore.model.IconStyle
import com.yumiru11.githubapp.core.datastore.model.ThemeMode
import com.yumiru11.githubapp.core.designsystem.component.CardGroup
import com.yumiru11.githubapp.core.designsystem.icon.AppIcon
import com.yumiru11.githubapp.core.designsystem.icon.AppIcons
import com.yumiru11.githubapp.core.designsystem.token.AppDimens
import com.yumiru11.githubapp.core.designsystem.token.AppIcon
import com.yumiru11.githubapp.core.designsystem.token.AppMotion
import kotlin.math.roundToInt

/**
 * 外观分组（ui-design §3.6，#87 分组卡化）：主题模式 / 动态取色 / seed 色盘 /
 * OLED / 高对比 / 毛玻璃 / 圆角强度滑杆（实时预览）/ 动画强度滑杆（实时预览）/
 * 图标风格预览卡，经 [CardGroup] 呈现为分段卡；非开关项副标题显示当前值（原生设置惯例）。
 *
 * 「图标风格」入口随 AppIcon 消费基建落地而解禁（issue #168 / UI12）：底栏与顶栏
 * 图标已改为经 [AppIcon] 按 LocalIconStyle 取变体，切换即时生效，不再是「点了没反应」的
 * 空开关（FEEDBACK #6 的先例要求）。代码字体 / 行号仍隐藏（待 Sora 配置接线），
 * DataStore 字段与 [SettingsViewModel] 写入口保留。
 */
@Composable
internal fun AppearanceSettingsSection(
    uiState: SettingsUiState,
    viewModel: SettingsViewModel,
) {
    CardGroup {
        item { ThemeModeRow(uiState = uiState, viewModel = viewModel) }
        item {
            SwitchSettingRow(
                title = stringResource(R.string.settings_dynamic_color),
                description = stringResource(R.string.settings_dynamic_color_desc),
                checked = uiState.dynamicColorEnabled,
                onCheckedChange = viewModel::setDynamicColorEnabled,
            )
        }
        item {
            SeedColorRow(
                selected = uiState.seedColor,
                enabled = !uiState.dynamicColorEnabled,
                onSelect = viewModel::setSeedColor,
            )
        }
        item {
            SwitchSettingRow(
                title = stringResource(R.string.settings_oled),
                description = stringResource(R.string.settings_oled_desc),
                checked = uiState.oledEnabled,
                onCheckedChange = viewModel::setOledEnabled,
            )
        }
        item {
            SwitchSettingRow(
                title = stringResource(R.string.settings_high_contrast),
                description = stringResource(R.string.settings_high_contrast_desc),
                checked = uiState.highContrastEnabled,
                onCheckedChange = viewModel::setHighContrastEnabled,
            )
        }
        item {
            SwitchSettingRow(
                title = stringResource(R.string.settings_blur),
                description = stringResource(R.string.settings_blur_desc),
                checked = uiState.blurEnabled,
                onCheckedChange = viewModel::setBlurEnabled,
            )
        }
        // 逐项开关（#167 / UI03，ui-design §6.3 四条允许点位）：总开关关闭或
        // OLED/高对比生效时整体置灰 —— 此时玻璃本就被强制禁用，可点会误导。
        item {
            SwitchSettingRow(
                title = stringResource(R.string.settings_glass_top_bar),
                checked = uiState.glassTopBar,
                onCheckedChange = viewModel::setGlassTopBar,
                enabled = uiState.glassPerItemEnabled,
                indented = true,
            )
        }
        item {
            SwitchSettingRow(
                title = stringResource(R.string.settings_glass_bottom_bar),
                checked = uiState.glassBottomBar,
                onCheckedChange = viewModel::setGlassBottomBar,
                enabled = uiState.glassPerItemEnabled,
                indented = true,
            )
        }
        item {
            SwitchSettingRow(
                title = stringResource(R.string.settings_glass_panel),
                checked = uiState.glassPanel,
                onCheckedChange = viewModel::setGlassPanel,
                enabled = uiState.glassPerItemEnabled,
                indented = true,
            )
        }
        item {
            SwitchSettingRow(
                title = stringResource(R.string.settings_glass_bottom_sheet),
                checked = uiState.glassBottomSheet,
                onCheckedChange = viewModel::setGlassBottomSheet,
                enabled = uiState.glassPerItemEnabled,
                indented = true,
            )
        }
        // 图标风格（#168 / UI12）：三张预览卡，切换即时生效
        item {
            IconStyleRow(
                selected = uiState.iconStyle,
                onSelect = viewModel::setIconStyle,
            )
        }
        item { CornerScaleRow(scale = uiState.cornerScale, onScaleChange = viewModel::setCornerScale) }
        item { MotionScaleRow(scale = uiState.motionScale, onScaleChange = viewModel::setMotionScale) }
        // 列表 stagger 开关（#167 / UI06，§4.2 H2-2 用户拍板「可选开关」）：
        // 放在动效强度滑杆下方 —— 同属"动效"语义，但一个是全局时长、一个是逐项错峰。
        item {
            SwitchSettingRow(
                title = stringResource(R.string.settings_stagger),
                description = stringResource(R.string.settings_stagger_desc),
                checked = uiState.staggerEnabled,
                onCheckedChange = viewModel::setStaggerEnabled,
            )
        }
    }
}

/** 主题模式三选一（System/Light/Dark）；副标题常显当前值。 */
@Composable
private fun ThemeModeRow(
    uiState: SettingsUiState,
    viewModel: SettingsViewModel,
) {
    val options =
        listOf(
            ThemeMode.SYSTEM to stringResource(R.string.settings_theme_system),
            ThemeMode.LIGHT to stringResource(R.string.settings_theme_light),
            ThemeMode.DARK to stringResource(R.string.settings_theme_dark),
        )
    val currentValue = options.first { it.first == uiState.themeMode }.second
    SettingRow(title = stringResource(R.string.settings_theme_mode), valueText = currentValue) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { (mode, label) ->
                FilterChip(
                    selected = uiState.themeMode == mode,
                    onClick = { viewModel.setThemeMode(mode) },
                    label = { Text(label) },
                )
            }
        }
    }
}

/**
 * seed 色盘：色圆一排（FlowRow 自动换行保证 48dp 触点放得下），选中项描边高亮；
 * 动态取色开启时禁用。无障碍：每块色圆为独立 selectable 单选（TalkBack 读色名 +
 * 选中态，缺陷 #8）。
 */
@Composable
private fun SeedColorRow(
    selected: Long,
    enabled: Boolean,
    onSelect: (Long) -> Unit,
) {
    val selectedName =
        SEED_COLORS
            .firstOrNull { (color, _) -> color == selected }
            ?.let { (_, nameRes) -> stringResource(nameRes) }
    SettingRow(title = stringResource(R.string.settings_seed_color), valueText = selectedName) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            SEED_COLORS.forEach { (color, nameRes) ->
                SeedSwatch(
                    color = color,
                    name = stringResource(nameRes),
                    isSelected = color == selected,
                    enabled = enabled,
                    onSelect = { onSelect(color) },
                )
            }
        }
    }
}

/** 单个 seed 色块：24dp 视觉圆 + 48dp 最小触区包裹（缺陷 #8 触点修复）。 */
@Composable
private fun SeedSwatch(
    color: Long,
    name: String,
    isSelected: Boolean,
    enabled: Boolean,
    onSelect: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .size(AppDimens.minTouchTarget)
                .semantics { contentDescription = name }
                .selectable(
                    selected = isSelected,
                    enabled = enabled,
                    role = Role.RadioButton,
                    onClick = onSelect,
                ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
                Modifier
                    .size(AppIcon.iconMedium)
                    .clip(CircleShape)
                    .background(Color(color))
                    .then(
                        if (isSelected) {
                            Modifier.border(
                                width = 3.dp,
                                color = MaterialTheme.colorScheme.primary,
                                shape = CircleShape,
                            )
                        } else {
                            Modifier
                        },
                    ),
        )
    }
}

/**
 * 图标风格三选一（issue #168 / UI12，ui-design §3.6「Rounded/Outlined/Filled 预览」）。
 *
 * 每张预览卡用同一条 [AppIcons.Settings] 规格、只改 [IconStyle] 渲染 → 所见即所得；
 * 选中即经 [SettingsViewModel.setIconStyle] 写 DataStore，AppThemeHost 注入的
 * LocalIconStyle 随之变化，底栏/顶栏图标立即换变体。
 *
 * 无障碍：卡片是单选（[Role.RadioButton] + selected 语义，同 seed 色盘模式），
 * 图标为装饰（CD = null），语义由卡片文本承载；触区 ≥ [AppDimens.minTouchTarget]。
 */
@Composable
internal fun IconStyleRow(
    selected: IconStyle,
    onSelect: (IconStyle) -> Unit,
) {
    val options =
        listOf(
            IconStyle.OUTLINED to R.string.settings_icon_outlined,
            IconStyle.ROUNDED to R.string.settings_icon_rounded,
            IconStyle.FILLED to R.string.settings_icon_filled,
        )
    val currentName = stringResource(options.first { (style, _) -> style == selected }.second)
    SettingRow(title = stringResource(R.string.settings_icon_style), valueText = currentName) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { (style, labelRes) ->
                IconStylePreviewCard(
                    style = style,
                    label = stringResource(labelRes),
                    isSelected = style == selected,
                    onSelect = { onSelect(style) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** 单张风格预览卡：该风格下的图标 + 风格名；选中态 = primaryContainer 容器 + primary 描边。 */
@Composable
private fun IconStylePreviewCard(
    style: IconStyle,
    label: String,
    isSelected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val contentColor =
        if (isSelected) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        }
    Surface(
        modifier =
            modifier
                .heightIn(min = AppDimens.minTouchTarget)
                .selectable(
                    selected = isSelected,
                    role = Role.RadioButton,
                    onClick = onSelect,
                ),
        shape = MaterialTheme.shapes.medium,
        color =
            if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        // 选中态除容器色再叠描边：高对比/低视力下容器色差不足（无障碍冗余编码）
        border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AppIcon(
                spec = AppIcons.Settings,
                contentDescription = null,
                style = style,
                tint = contentColor,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = contentColor,
            )
        }
    }
}

/** 圆角强度滑杆 + 实时预览卡（圆角 = AppDimens.cornerLarge × scale）；副标题显百分比。 */
@Composable
private fun CornerScaleRow(
    scale: Float,
    onScaleChange: (Float) -> Unit,
) {
    SettingRow(
        title = stringResource(R.string.settings_corner_scale),
        valueText = stringResource(R.string.settings_scale_percent, (scale * 100).roundToInt()),
    ) {
        Column {
            Slider(
                value = scale,
                onValueChange = onScaleChange,
                valueRange = SCALE_RANGE,
                steps = SCALE_STEPS,
            )
            Surface(
                shape = RoundedCornerShape(AppDimens.cornerLarge * scale),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = stringResource(R.string.settings_corner_scale),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
    }
}

/** 动画强度滑杆 + 实时预览（脉冲周期 = AppMotion 令牌 × scale，key 强制重建动画）。 */
@Composable
private fun MotionScaleRow(
    scale: Float,
    onScaleChange: (Float) -> Unit,
) {
    SettingRow(
        title = stringResource(R.string.settings_motion_scale),
        valueText = stringResource(R.string.settings_scale_percent, (scale * 100).roundToInt()),
    ) {
        Column {
            Slider(
                value = scale,
                onValueChange = onScaleChange,
                valueRange = SCALE_RANGE,
                steps = SCALE_STEPS,
            )
            key(scale) {
                MotionPulsePreview(scale = scale)
            }
        }
    }
}

/** 脉冲动画预览点：周期 = AppMotion.DURATION_SMALL_STATE_CHANGE × scale。 */
@Composable
private fun MotionPulsePreview(scale: Float) {
    val durationMillis = (AppMotion.DURATION_SMALL_STATE_CHANGE * scale).toInt()
    val transition = rememberInfiniteTransition(label = "motion-preview")
    val progress by
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec =
                infiniteRepeatable(
                    animation = tween(durationMillis = durationMillis, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
            label = "pulse",
        )
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(48.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
                Modifier
                    .size(16.dp + 24.dp * progress)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 1f - 0.5f * progress)),
        )
    }
}

/**
 * 开关型设置行（标题 + 可选说明 + Switch）。
 *
 * @param enabled 交互可用性；false 时置灰（#167 / UI03：OLED/高对比下逐项开关被强制禁用）
 * @param indented 作为上级开关的子项缩进（同款控件层级化，避免另造组件）
 */
@Composable
internal fun SwitchSettingRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    description: String? = null,
    enabled: Boolean = true,
    indented: Boolean = false,
) {
    // 禁用态统一走 M3 的 38% onSurface（与 Switch 自身的禁用色一致）
    val titleColor =
        if (enabled) {
            MaterialTheme.colorScheme.onSurface
        } else {
            MaterialTheme.colorScheme.onSurface.copy(alpha = DISABLED_CONTENT_ALPHA)
        }
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(
                    start = if (indented) AppDimens.contentPadding * 2 else AppDimens.contentPadding,
                    end = AppDimens.contentPadding,
                    top = 8.dp,
                    bottom = 8.dp,
                ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = if (indented) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge,
                color = titleColor,
            )
            if (description != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
        )
    }
}

/** M3 禁用态内容不透明度（状态层规范值） */
private const val DISABLED_CONTENT_ALPHA = 0.38f

/** 标题 + 当前值副标题（可选）+ 内容行的通用容器（内容可换行）。 */
@Composable
internal fun SettingRow(
    title: String,
    valueText: String? = null,
    content: @Composable () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
        )
        if (valueText != null) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = valueText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        content()
    }
}

private val SCALE_RANGE = 0.5f..1.5f

private const val SCALE_STEPS = 9

/** 预设 seed 色盘（T24；默认值 = UserPreferencesRepository.DEFAULT_SEED_COLOR）+ 色名资源（#87 无障碍）。 */
private val SEED_COLORS =
    listOf(
        0xFF0969DA to R.string.settings_seed_blue, // GitHub brand blue
        0xFF7C3AED to R.string.settings_seed_purple, // purple
        0xFF0EA5E9 to R.string.settings_seed_sky, // sky
        0xFF10B981 to R.string.settings_seed_emerald, // emerald
        0xFFF59E0B to R.string.settings_seed_amber, // amber
        0xFFEF4444 to R.string.settings_seed_red, // red
        0xFFEC4899 to R.string.settings_seed_pink, // pink
        0xFF64748B to R.string.settings_seed_slate, // slate
    )
