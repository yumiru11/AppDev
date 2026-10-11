package com.yumiru11.githubapp.feature.settings

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yumiru11.githubapp.core.designsystem.component.AppSelect
import com.yumiru11.githubapp.core.designsystem.component.AppSettingsGroup

/** 通用（**L3 叶子页**）：语言（跟随系统 / English / 中文）。 */
@Composable
fun SettingsGeneralScreen(
    viewModel: SettingsViewModel,
    onBackClick: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    SettingsPageScaffold(
        title = stringResource(R.string.settings_general_entry),
        onBackClick = onBackClick,
    ) {
        item {
            AppSettingsGroup {
                item(
                    headlineContent = { Text(stringResource(R.string.settings_language)) },
                    supportingContent = { Text(languageLabel(uiState.languageTag)) },
                    trailingContent = {
                        AppSelect(
                            options = LANGUAGE_OPTIONS,
                            selectedOption = uiState.languageTag,
                            onOptionSelected = viewModel::setLanguageTag,
                            optionLabel = { tag -> languageLabel(tag) },
                        )
                    },
                )
            }
        }
    }
}

/** 语言选项的稳定顺序（null = 跟随系统）；列表本身是 route/偏好值，不随语言变化。 */
internal val LANGUAGE_OPTIONS: List<String?> = listOf(null, LANGUAGE_EN, LANGUAGE_ZH)

internal const val LANGUAGE_EN = "en"
internal const val LANGUAGE_ZH = "zh-rCN"

@Composable
private fun languageLabel(tag: String?): String =
    when (tag) {
        null -> stringResource(R.string.settings_language_system)
        LANGUAGE_EN -> stringResource(R.string.settings_language_en)
        else -> stringResource(R.string.settings_language_zh)
    }
