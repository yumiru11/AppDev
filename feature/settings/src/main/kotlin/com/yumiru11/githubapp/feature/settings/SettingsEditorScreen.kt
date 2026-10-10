package com.yumiru11.githubapp.feature.settings

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yumiru11.githubapp.core.datastore.model.CodeFont
import com.yumiru11.githubapp.core.designsystem.component.AppSelect
import com.yumiru11.githubapp.core.designsystem.component.AppSettingsGroup

/** 编辑器（**L3 叶子页**）：代码字体 / 行号 / 代码换行 / Markdown 换行。 */
@Composable
fun SettingsEditorScreen(
    viewModel: SettingsViewModel,
    onBackClick: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val fontOptions =
        listOf(
            CodeFont.MONO to stringResource(R.string.settings_code_font_mono),
            CodeFont.SYSTEM to stringResource(R.string.settings_code_font_system),
        )

    SettingsPageScaffold(
        title = stringResource(R.string.settings_editor),
        onBackClick = onBackClick,
    ) {
        item {
            AppSettingsGroup {
                item(
                    headlineContent = { Text(stringResource(R.string.settings_code_font)) },
                    supportingContent = { Text(fontOptions.first { it.first == uiState.codeFont }.second) },
                    trailingContent = {
                        AppSelect(
                            options = fontOptions.map { it.first },
                            selectedOption = uiState.codeFont,
                            onOptionSelected = viewModel::setCodeFont,
                            optionLabel = { font -> fontOptions.first { it.first == font }.second },
                        )
                    },
                )
                switchItem(
                    checked = uiState.codeLineNumbers,
                    onCheckedChange = viewModel::setCodeLineNumbers,
                    headlineContent = { Text(stringResource(R.string.settings_code_line_numbers)) },
                    supportingContent = { Text(stringResource(R.string.settings_code_line_numbers_desc)) },
                )
                switchItem(
                    checked = uiState.codeSoftWrap,
                    onCheckedChange = viewModel::setCodeSoftWrap,
                    headlineContent = { Text(stringResource(R.string.settings_code_wrap)) },
                    supportingContent = { Text(stringResource(R.string.settings_code_wrap_desc)) },
                )
                switchItem(
                    checked = uiState.markdownSoftWrap,
                    onCheckedChange = viewModel::setMarkdownSoftWrap,
                    headlineContent = { Text(stringResource(R.string.settings_md_wrap)) },
                    supportingContent = { Text(stringResource(R.string.settings_md_wrap_desc)) },
                )
            }
        }
    }
}
