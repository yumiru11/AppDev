package com.yumiru11.githubapp.feature.settings

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.yumiru11.githubapp.core.designsystem.component.AppSettingsGroup
import com.yumiru11.githubapp.core.designsystem.icon.AppIcons

/**
 * 偏好设置（**L2 hub**）：只放二级入口，真正的开关在叶子页。
 *
 * 这一层与 L1 的区别只在语义（更细的分类），形态完全一致：条目 = 图标 + 标题 + 一句说明。
 */
@Composable
fun SettingsPreferencesScreen(
    onBackClick: () -> Unit,
    onOpenAppearance: () -> Unit,
    onOpenInterface: () -> Unit,
    onOpenEditor: () -> Unit,
    onOpenGeneral: () -> Unit,
) {
    SettingsPageScaffold(
        title = stringResource(R.string.settings_preferences),
        onBackClick = onBackClick,
    ) {
        item {
            AppSettingsGroup {
                item(
                    onClick = onOpenAppearance,
                    leadingContent = { SettingsEntryIcon(AppIcons.Palette) },
                    headlineContent = { Text(stringResource(R.string.settings_appearance_group)) },
                    supportingContent = { Text(stringResource(R.string.settings_appearance_desc)) },
                )
                item(
                    onClick = onOpenInterface,
                    leadingContent = { SettingsEntryIcon(AppIcons.MotionPhotosOn) },
                    headlineContent = { Text(stringResource(R.string.settings_interface)) },
                    supportingContent = { Text(stringResource(R.string.settings_interface_desc)) },
                )
                item(
                    onClick = onOpenEditor,
                    leadingContent = { SettingsEntryIcon(AppIcons.Code) },
                    headlineContent = { Text(stringResource(R.string.settings_editor)) },
                    supportingContent = { Text(stringResource(R.string.settings_editor_desc)) },
                )
                item(
                    onClick = onOpenGeneral,
                    leadingContent = { SettingsEntryIcon(AppIcons.Language) },
                    headlineContent = { Text(stringResource(R.string.settings_general_entry)) },
                    supportingContent = { Text(stringResource(R.string.settings_general_entry_desc)) },
                )
            }
        }
    }
}
