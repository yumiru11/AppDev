package com.yumiru11.githubapp.feature.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yumiru11.githubapp.core.datastore.model.ThemeMode
import com.yumiru11.githubapp.core.designsystem.component.AppSelect
import com.yumiru11.githubapp.core.designsystem.component.AppSettingsGroup
import com.yumiru11.githubapp.core.designsystem.icon.AppIcon
import com.yumiru11.githubapp.core.designsystem.icon.AppIcons
import com.yumiru11.githubapp.core.designsystem.token.AppDimens

/**
 * 设置主页（**L1 hub**，2026-10-10 分层重构）。
 *
 * 结构对齐参考实现：**第一层只放入口**，条目 = 图标 + 标题 + 一句说明（可点进入下一层），
 * 只有最常用的「主题模式」留在本层用胶囊下拉直接改。真正的开关全部下沉到叶子页——
 * 于是「带图标的入口」与「不带图标的开关行」在视觉上天然分层，文字基线也统一。
 */
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBackClick: () -> Unit,
    onOpenPreferences: () -> Unit,
    onOpenAccount: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val themeOptions =
        listOf(
            ThemeMode.SYSTEM to stringResource(R.string.settings_theme_system),
            ThemeMode.LIGHT to stringResource(R.string.settings_theme_light),
            ThemeMode.DARK to stringResource(R.string.settings_theme_dark),
        )

    SettingsPageScaffold(
        title = stringResource(R.string.settings_title),
        onBackClick = onBackClick,
    ) {
        item {
            AppSettingsGroup(
                modifier = modifier,
                title = stringResource(R.string.settings_general_group),
            ) {
                item(
                    leadingContent = { SettingsEntryIcon(AppIcons.Brightness2) },
                    headlineContent = { Text(stringResource(R.string.settings_theme_mode)) },
                    supportingContent = {
                        Text(themeOptions.first { it.first == uiState.themeMode }.second)
                    },
                    trailingContent = {
                        AppSelect(
                            options = themeOptions.map { it.first },
                            selectedOption = uiState.themeMode,
                            onOptionSelected = viewModel::setThemeMode,
                            optionLabel = { mode -> themeOptions.first { it.first == mode }.second },
                        )
                    },
                )
                item(
                    onClick = onOpenPreferences,
                    leadingContent = { SettingsEntryIcon(AppIcons.Tune) },
                    headlineContent = { Text(stringResource(R.string.settings_preferences)) },
                    supportingContent = { Text(stringResource(R.string.settings_preferences_desc)) },
                )
                item(
                    onClick = onOpenAccount,
                    leadingContent = { SettingsEntryIcon(AppIcons.AccountCircle) },
                    headlineContent = { Text(stringResource(R.string.settings_account_entry)) },
                    supportingContent = { Text(stringResource(R.string.settings_account_entry_desc)) },
                )
            }
        }
        item {
            var showAbout by remember { mutableStateOf(false) }
            AppSettingsGroup(title = stringResource(R.string.settings_about_group)) {
                item(
                    onClick = { showAbout = true },
                    leadingContent = { SettingsEntryIcon(AppIcons.Info) },
                    headlineContent = { Text(stringResource(R.string.settings_about)) },
                    supportingContent = { Text(stringResource(R.string.settings_about_desc)) },
                )
            }
            if (showAbout) {
                AboutDialog(onDismiss = { showAbout = false })
            }
        }
    }
}

/** 入口行前置图标（L1/L2 专用）：统一 `onSurfaceVariant`，风格随「图标风格」偏好切换。 */
@Composable
internal fun SettingsEntryIcon(spec: com.yumiru11.githubapp.core.designsystem.icon.AppIconSpec) {
    AppIcon(
        spec = spec,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(end = AppDimens.spacing.xs),
    )
}
