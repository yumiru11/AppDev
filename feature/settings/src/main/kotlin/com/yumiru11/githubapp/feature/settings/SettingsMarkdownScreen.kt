package com.yumiru11.githubapp.feature.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yumiru11.githubapp.core.datastore.model.MarkdownRenderMode
import com.yumiru11.githubapp.core.designsystem.component.AppNotice
import com.yumiru11.githubapp.core.designsystem.component.AppSettingsGroup
import com.yumiru11.githubapp.core.designsystem.token.AppDimens

/**
 * Markdown 渲染（**L3 叶子页**）：选择渲染通道，以及「遇到原生处理不了的内容是否回退」。
 *
 * 选项语义见 [MarkdownRenderMode]：原生优先（默认）/ 仅原生 / WebView 优先 / 仅 WebView。
 * 页尾一条说明卡，讲清默认档会回退什么、不会回退什么 —— 避免用户以为「选了原生就全原生」。
 */
@Composable
fun SettingsMarkdownScreen(
    viewModel: SettingsViewModel,
    onBackClick: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    SettingsPageScaffold(
        title = stringResource(R.string.settings_markdown_title),
        onBackClick = onBackClick,
    ) {
        item {
            AppSettingsGroup(title = stringResource(R.string.settings_markdown_group_channel)) {
                MarkdownRenderMode.entries.forEach { mode ->
                    item(
                        onClick = { viewModel.setMarkdownRenderMode(mode) },
                        headlineContent = { Text(stringResource(titleRes(mode))) },
                        supportingContent = { Text(stringResource(descRes(mode))) },
                        trailingContent = {
                            RadioButton(
                                selected = uiState.markdownRenderMode == mode,
                                onClick = { viewModel.setMarkdownRenderMode(mode) },
                            )
                        },
                    )
                }
            }
        }
        item {
            AppNotice(
                text = stringResource(R.string.settings_markdown_note),
                modifier = Modifier.padding(horizontal = AppDimens.spacing.l),
            )
        }
    }
}

@StringRes
private fun titleRes(mode: MarkdownRenderMode): Int =
    when (mode) {
        MarkdownRenderMode.NATIVE_PREFERRED -> R.string.settings_markdown_native_preferred
        MarkdownRenderMode.NATIVE_ONLY -> R.string.settings_markdown_native_only
        MarkdownRenderMode.WEBVIEW_PREFERRED -> R.string.settings_markdown_webview_preferred
        MarkdownRenderMode.WEBVIEW_ONLY -> R.string.settings_markdown_webview_only
    }

@StringRes
private fun descRes(mode: MarkdownRenderMode): Int =
    when (mode) {
        MarkdownRenderMode.NATIVE_PREFERRED -> R.string.settings_markdown_native_preferred_desc
        MarkdownRenderMode.NATIVE_ONLY -> R.string.settings_markdown_native_only_desc
        MarkdownRenderMode.WEBVIEW_PREFERRED -> R.string.settings_markdown_webview_preferred_desc
        MarkdownRenderMode.WEBVIEW_ONLY -> R.string.settings_markdown_webview_only_desc
    }
