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
import com.yumiru11.githubapp.core.datastore.model.ThemeMode
import com.yumiru11.githubapp.core.designsystem.component.CardGroup
import com.yumiru11.githubapp.core.designsystem.token.AppDimens
import com.yumiru11.githubapp.core.designsystem.token.AppIcon
import com.yumiru11.githubapp.core.designsystem.token.AppMotion
import kotlin.math.roundToInt

/**
 * 外观分组（ui-design §3.6，#87 分组卡化）：主题模式 / 动态取色 / seed 色盘 /
 * OLED / 高对比 / 毛玻璃 / 圆角强度滑杆（实时预览）/ 动画强度滑杆（实时预览），
 * 经 [CardGroup] 呈现为分段卡；非开关项副标题显示当前值（原生设置惯例）。
 *
 * 图标风格 / 代码字体 / 行号三项暂隐藏（FEEDBACK #6 先例：消费点未落地前不暴露
 * 入口）——iconStyle 待 AppIcon 风格消费基建（ADR-0004 挂账），codeFont /
 * lineNumbers 待 Sora 配置接线；DataStore 字段与 [SettingsViewModel] 写入口保留。
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
        item { CornerScaleRow(scale = uiState.cornerScale, onScaleChange = viewModel::setCornerScale) }
        item { MotionScaleRow(scale = uiState.motionScale, onScaleChange = viewModel::setMotionScale) }
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

/** 开关型设置行（标题 + 可选说明 + Switch）。 */
@Composable
internal fun SwitchSettingRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    description: String? = null,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
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
        )
    }
}

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
