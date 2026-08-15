package com.yumiru11.githubapp.core.markdown

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Check
import com.composables.icons.materialsymbols.rounded.Content_copy

/**
 * B 增强版代码块：语言标签 + 复制按钮（勾形反馈 1.5s）+ 横向滚动 + C 半融合高亮。
 *
 * 浅色容器 = surfaceContainer；深色容器 = surfaceContainerLowest（比正文更深一档）。
 */
@Composable
fun EnhancedCodeBlock(
    code: String,
    language: String?,
    isDark: Boolean,
    horizontalScrollEnabled: Boolean = true,
) {
    val context = LocalContext.current
    val copyState = rememberCopyFeedbackState()
    val codeBackground =
        if (isDark) MaterialTheme.colorScheme.surfaceContainerLowest else MaterialTheme.colorScheme.surfaceContainer

    Surface(
        color = codeBackground,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 4.dp),
            ) {
                Text(
                    text = language?.uppercase() ?: stringResource(R.string.code_plain_text),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("code", code))
                    copyState.markCopied()
                }) {
                    Icon(
                        imageVector = if (copyState.copied) MaterialSymbols.Rounded.Check else MaterialSymbols.Rounded.Content_copy,
                        contentDescription = stringResource(if (copyState.copied) R.string.code_copied else R.string.code_copy),
                        tint = if (copyState.copied) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .then(
                            if (horizontalScrollEnabled) {
                                Modifier.horizontalScroll(rememberScrollState())
                            } else {
                                Modifier
                            },
                        ).padding(bottom = 12.dp),
            ) {
                TextMateCodeBlock(
                    code = code,
                    language = language,
                    theme = GitHubTextMateTheme.rememberGitHubTextMateTheme(isDark),
                )
            }
        }
    }
}
