package com.yumiru11.githubapp.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yumiru11.githubapp.core.designsystem.component.CardGroup

/**
 * 通用分组（ui-design §3.6，#87 分组卡化）：语言（System/English/中文）+ 关于；
 * 语言副标题常显当前值（原生设置惯例）。
 */
@Composable
internal fun GeneralSettingsSection(
    uiState: SettingsUiState,
    viewModel: SettingsViewModel,
) {
    var showAbout by remember { mutableStateOf(false) }

    CardGroup {
        item { LanguageRow(languageTag = uiState.languageTag, onSelect = viewModel::setLanguageTag) }
        item(onClick = { showAbout = true }) { AboutRow() }
    }

    if (showAbout) {
        AboutDialog(onDismiss = { showAbout = false })
    }
}

/** 「关于」行（整段可点由 [CardGroup] 条目承担；图标为装饰性，文案即语义）。 */
@Composable
private fun AboutRow() {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Default.Info,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = stringResource(R.string.settings_about),
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

/** 语言三选一（跟随系统 / English / 中文；null = 系统语言）；副标题常显当前值。 */
@Composable
private fun LanguageRow(
    languageTag: String?,
    onSelect: (String?) -> Unit,
) {
    val options =
        listOf(
            null to stringResource(R.string.settings_language_system),
            LANGUAGE_EN to stringResource(R.string.settings_language_en),
            LANGUAGE_ZH to stringResource(R.string.settings_language_zh),
        )
    val currentValue =
        options
            .firstOrNull { (tag, _) -> tag == languageTag }
            ?.second
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            text = stringResource(R.string.settings_language),
            style = MaterialTheme.typography.bodyLarge,
        )
        if (currentValue != null) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = currentValue,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { (tag, label) ->
                FilterChip(
                    selected = languageTag == tag,
                    onClick = { onSelect(tag) },
                    label = { Text(label) },
                )
            }
        }
    }
}

private const val LANGUAGE_EN = "en"

private const val LANGUAGE_ZH = "zh-rCN"
