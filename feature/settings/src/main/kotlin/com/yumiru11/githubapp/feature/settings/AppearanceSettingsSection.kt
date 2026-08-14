@file:Suppress("LongMethod")
// 外观分组含 10 个设置项（模式/开关/色盘/滑杆/风格），分支样板天然长；精准抑制。

package com.yumiru11.githubapp.feature.settings

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.unit.dp
import com.yumiru11.githubapp.core.datastore.model.CodeFont
import com.yumiru11.githubapp.core.datastore.model.IconStyle
import com.yumiru11.githubapp.core.datastore.model.ThemeMode
import com.yumiru11.githubapp.core.designsystem.token.AppDimens
import com.yumiru11.githubapp.core.designsystem.token.AppIcon
import com.yumiru11.githubapp.core.designsystem.token.AppMotion

/**
 * 外观分组（ui-design §3.6）：主题模式 / 动态取色 / seed 色盘 / OLED /
 * 高对比 / 毛玻璃 / 圆角强度滑杆（实时预览）/ 动画强度滑杆（实时预览）/
 * 图标风格（Rounded/Outlined/Filled 预览）/ 代码字体 / 行号。
 */
@Composable
internal fun AppearanceSettingsSection(
    uiState: SettingsUiState,
    viewModel: SettingsViewModel,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        ThemeModeRow(uiState = uiState, viewModel = viewModel)
        HorizontalDivider()
        SwitchSettingRow(
            title = stringResource(R.string.settings_dynamic_color),
            description = stringResource(R.string.settings_dynamic_color_desc),
            checked = uiState.dynamicColorEnabled,
            onCheckedChange = viewModel::setDynamicColorEnabled,
        )
        HorizontalDivider()
        SeedColorRow(
            selected = uiState.seedColor,
            enabled = !uiState.dynamicColorEnabled,
            onSelect = viewModel::setSeedColor,
        )
        HorizontalDivider()
        SwitchSettingRow(
            title = stringResource(R.string.settings_oled),
            description = stringResource(R.string.settings_oled_desc),
            checked = uiState.oledEnabled,
            onCheckedChange = viewModel::setOledEnabled,
        )
        HorizontalDivider()
        SwitchSettingRow(
            title = stringResource(R.string.settings_high_contrast),
            description = stringResource(R.string.settings_high_contrast_desc),
            checked = uiState.highContrastEnabled,
            onCheckedChange = viewModel::setHighContrastEnabled,
        )
        HorizontalDivider()
        SwitchSettingRow(
            title = stringResource(R.string.settings_blur),
            description = stringResource(R.string.settings_blur_desc),
            checked = uiState.blurEnabled,
            onCheckedChange = viewModel::setBlurEnabled,
        )
        HorizontalDivider()
        CornerScaleRow(scale = uiState.cornerScale, onScaleChange = viewModel::setCornerScale)
        HorizontalDivider()
        MotionScaleRow(scale = uiState.motionScale, onScaleChange = viewModel::setMotionScale)
        HorizontalDivider()
        IconStyleRow(selected = uiState.iconStyle, onSelect = viewModel::setIconStyle)
        HorizontalDivider()
        CodeFontRow(selected = uiState.codeFont, onSelect = viewModel::setCodeFont)
        HorizontalDivider()
        SwitchSettingRow(
            title = stringResource(R.string.settings_code_line_numbers),
            checked = uiState.codeLineNumbers,
            onCheckedChange = viewModel::setCodeLineNumbers,
        )
    }
}

/** 主题模式三选一（System/Light/Dark，FilterChip 行）。 */
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
    SettingRow(title = stringResource(R.string.settings_theme_mode)) {
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

/** seed 色盘：一排色圆，选中项描边高亮；动态取色开启时禁用。 */
@Composable
private fun SeedColorRow(
    selected: Long,
    enabled: Boolean,
    onSelect: (Long) -> Unit,
) {
    SettingRow(title = stringResource(R.string.settings_seed_color)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SEED_COLORS.forEach { color ->
                val isSelected = color == selected
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
                            ).clickable(enabled = enabled) { onSelect(color) },
                )
            }
        }
    }
}

/** 圆角强度滑杆 + 实时预览卡（圆角 = AppDimens.cornerLarge × scale）。 */
@Composable
private fun CornerScaleRow(
    scale: Float,
    onScaleChange: (Float) -> Unit,
) {
    SettingRow(title = stringResource(R.string.settings_corner_scale)) {
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
    SettingRow(title = stringResource(R.string.settings_motion_scale)) {
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

/** 图标风格三选一（Outlined/Rounded/Filled，Material Icons Core 同图标三风格预览）。 */
@Composable
private fun IconStyleRow(
    selected: IconStyle,
    onSelect: (IconStyle) -> Unit,
) {
    val options =
        listOf(
            IconStyle.OUTLINED to (stringResource(R.string.settings_icon_outlined) to Icons.Outlined.Settings),
            IconStyle.ROUNDED to (stringResource(R.string.settings_icon_rounded) to Icons.Rounded.Settings),
            IconStyle.FILLED to (stringResource(R.string.settings_icon_filled) to Icons.Filled.Settings),
        )
    SettingRow(title = stringResource(R.string.settings_icon_style)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            options.forEach { (style, pair) ->
                val (label, icon) = pair
                val isSelected = selected == style
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier =
                        Modifier
                            .clip(RoundedCornerShape(AppDimens.cornerMedium))
                            .clickable { onSelect(style) }
                            .then(
                                if (isSelected) {
                                    Modifier.background(MaterialTheme.colorScheme.secondaryContainer)
                                } else {
                                    Modifier
                                },
                            ).padding(12.dp),
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = label,
                        tint =
                            if (isSelected) {
                                MaterialTheme.colorScheme.onSecondaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** 代码字体二选一（Monospace / System default）。 */
@Composable
private fun CodeFontRow(
    selected: CodeFont,
    onSelect: (CodeFont) -> Unit,
) {
    val options =
        listOf(
            CodeFont.MONO to stringResource(R.string.settings_code_font_mono),
            CodeFont.SYSTEM to stringResource(R.string.settings_code_font_system),
        )
    SettingRow(title = stringResource(R.string.settings_code_font)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { (font, label) ->
                FilterChip(
                    selected = selected == font,
                    onClick = { onSelect(font) },
                    label = { Text(label) },
                )
            }
        }
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

/** 标题 + 内容行的通用容器（内容可换行、右对齐）。 */
@Composable
private fun SettingRow(
    title: String,
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
        Spacer(modifier = Modifier.height(8.dp))
        content()
    }
}

private val SCALE_RANGE = 0.5f..1.5f

private const val SCALE_STEPS = 9

/** 预设 seed 色盘（T24；默认值 = UserPreferencesRepository.DEFAULT_SEED_COLOR）。 */
private val SEED_COLORS =
    listOf(
        0xFF0969DA, // GitHub brand blue
        0xFF7C3AED, // purple
        0xFF0EA5E9, // sky
        0xFF10B981, // emerald
        0xFFF59E0B, // amber
        0xFFEF4444, // red
        0xFFEC4899, // pink
        0xFF64748B, // slate
    )
