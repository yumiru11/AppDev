@file:OptIn(ExperimentalLayoutApi::class)

package com.yumiru11.githubapp.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yumiru11.githubapp.core.datastore.model.IconStyle
import com.yumiru11.githubapp.core.datastore.model.ThemeMode
import com.yumiru11.githubapp.core.designsystem.component.AppSelect
import com.yumiru11.githubapp.core.designsystem.component.AppSettingsGroup
import com.yumiru11.githubapp.core.designsystem.token.AppDimens

/**
 * 外观（**L3 叶子页**）：主题模式 / 动态取色 / 纯黑 / 高对比 / 主色调 / 图标风格。
 *
 * 叶子页的条目**不带前置图标**——只有入口行才需要图标辅助扫读；统一不带图标还能让所有行的
 * 文字基线严格对齐（早期版本入口行带图标、开关行不带，出图后文字位置明显不齐）。
 *
 * 每个分组拆成独立 section 函数（detekt `LongMethod` 80 行阈值；仓库既有做法）。
 */
@Composable
fun SettingsAppearanceScreen(
    viewModel: SettingsViewModel,
    onBackClick: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    SettingsPageScaffold(
        title = stringResource(R.string.settings_appearance_group),
        onBackClick = onBackClick,
    ) {
        item { ThemeSection(uiState = uiState, viewModel = viewModel) }
        item { ColorsSection(viewModel = viewModel, uiState = uiState) }
    }
}

/** 主题分组：主题模式（胶囊下拉）/ 动态取色 / 纯黑 / 高对比。 */
@Composable
private fun ThemeSection(
    uiState: SettingsUiState,
    viewModel: SettingsViewModel,
) {
    val themeOptions =
        listOf(
            ThemeMode.SYSTEM to stringResource(R.string.settings_theme_system),
            ThemeMode.LIGHT to stringResource(R.string.settings_theme_light),
            ThemeMode.DARK to stringResource(R.string.settings_theme_dark),
        )

    AppSettingsGroup(title = stringResource(R.string.settings_group_theme)) {
        item(
            headlineContent = { Text(stringResource(R.string.settings_theme_mode)) },
            supportingContent = { Text(themeOptions.first { it.first == uiState.themeMode }.second) },
            trailingContent = {
                AppSelect(
                    options = themeOptions.map { it.first },
                    selectedOption = uiState.themeMode,
                    onOptionSelected = viewModel::setThemeMode,
                    optionLabel = { mode -> themeOptions.first { it.first == mode }.second },
                )
            },
        )
        switchItem(
            checked = uiState.dynamicColorEnabled,
            onCheckedChange = viewModel::setDynamicColorEnabled,
            headlineContent = { Text(stringResource(R.string.settings_dynamic_color)) },
            supportingContent = { Text(stringResource(R.string.settings_dynamic_color_desc)) },
        )
        switchItem(
            checked = uiState.oledEnabled,
            onCheckedChange = viewModel::setOledEnabled,
            headlineContent = { Text(stringResource(R.string.settings_oled)) },
            supportingContent = { Text(stringResource(R.string.settings_oled_desc)) },
        )
        switchItem(
            checked = uiState.highContrastEnabled,
            onCheckedChange = viewModel::setHighContrastEnabled,
            headlineContent = { Text(stringResource(R.string.settings_high_contrast)) },
            supportingContent = { Text(stringResource(R.string.settings_high_contrast_desc)) },
        )
    }
}

/** 配色与图标分组：seed 色盘（行内色圆）/ 图标风格（胶囊下拉）。 */
@Composable
private fun ColorsSection(
    viewModel: SettingsViewModel,
    uiState: SettingsUiState,
) {
    val iconOptions =
        listOf(
            IconStyle.OUTLINED to stringResource(R.string.settings_icon_outlined),
            IconStyle.ROUNDED to stringResource(R.string.settings_icon_rounded),
            IconStyle.FILLED to stringResource(R.string.settings_icon_filled),
        )

    AppSettingsGroup(title = stringResource(R.string.settings_group_colors)) {
        item(
            headlineContent = { Text(stringResource(R.string.settings_seed_color)) },
            supportingContent = {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(AppDimens.spacing.xs),
                    verticalArrangement = Arrangement.spacedBy(AppDimens.spacing.xs),
                ) {
                    SEED_COLORS.forEach { color ->
                        SeedSwatch(
                            color = color,
                            name = stringResource(SEED_COLOR_NAMES.getValue(color)),
                            isSelected = color == uiState.seedColor,
                            enabled = !uiState.dynamicColorEnabled,
                            onSelect = { viewModel.setSeedColor(color) },
                        )
                    }
                }
            },
        )
        item(
            headlineContent = { Text(stringResource(R.string.settings_icon_style)) },
            supportingContent = { Text(iconOptions.first { it.first == uiState.iconStyle }.second) },
            trailingContent = {
                AppSelect(
                    options = iconOptions.map { it.first },
                    selectedOption = uiState.iconStyle,
                    onOptionSelected = viewModel::setIconStyle,
                    optionLabel = { style -> iconOptions.first { it.first == style }.second },
                )
            },
        )
    }
}
