package com.yumiru11.githubapp.feature.repo

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Wrap_text
import com.yumiru11.githubapp.core.designsystem.token.LocalCodeEditorPreferences
import com.yumiru11.githubapp.core.designsystem.token.LocalCodeEditorPreferencesWriter
import com.yumiru11.githubapp.core.editor.LineEnding
import com.yumiru11.githubapp.core.editor.TextFileFormat
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

// 编辑器工具控件（EDITOR-1）：软换行开关 + 文件格式状态指示。
// 两处消费点（文件查看器悬浮工具条 / 文件编辑页顶栏与底部状态行）：换行偏好读写走
// LocalCodeEditorPreferences（+ 写入端），不各自持有状态。

/**
 * 软换行开关（图标按钮；开 = primary 高亮 + 「软换行：开」描述，关 = onSurfaceVariant）。
 *
 * 未注入写入端（截图测试 / `@Preview`）时按钮为惰性（不崩溃、不落盘），
 * 状态仍按偏好快照渲染 —— 与未注入偏好时读默认值同款降级。
 */
@Composable
internal fun SoftWrapToggleButton(modifier: Modifier = Modifier) {
    val softWrap = LocalCodeEditorPreferences.current.codeSoftWrap
    val writer = LocalCodeEditorPreferencesWriter.current
    IconButton(
        onClick = { writer?.setCodeSoftWrap(!softWrap) },
        modifier = modifier,
    ) {
        Icon(
            imageVector = MaterialSymbols.Rounded.Wrap_text,
            contentDescription =
                stringResource(
                    if (softWrap) R.string.repo_file_soft_wrap_on else R.string.repo_file_soft_wrap_off,
                ),
            tint = if (softWrap) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * 文件格式状态指示（EDITOR-1 验收：如「CRLF · UTF-8」）。
 *
 * 显示的是**打开时探测的原始格式**（不是编辑器内部的 LF 表示）——保存按它还原，
 * 用户据此确认「这次保存不会把行尾改掉」。
 */
@Composable
internal fun TextFormatIndicator(
    format: TextFileFormat,
    modifier: Modifier = Modifier,
) {
    val eolLabel = stringResource(lineEndingLabelRes(format.lineEnding))
    val charsetLabel = stringResource(charsetLabelRes(format.charset))
    Text(
        text = stringResource(R.string.repo_file_format_indicator, eolLabel, charsetLabel),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        modifier = modifier.padding(horizontal = 12.dp, vertical = 2.dp),
    )
}

/** 行尾风格 → 展示文案资源（LF/CRLF/CR 为技术名，两语言同值）。 */
@StringRes
internal fun lineEndingLabelRes(lineEnding: LineEnding): Int =
    when (lineEnding) {
        LineEnding.LF -> R.string.repo_file_eol_lf
        LineEnding.CRLF -> R.string.repo_file_eol_crlf
        LineEnding.CR -> R.string.repo_file_eol_cr
    }

/**
 * 字符集 → 展示文案资源。
 *
 * 探测只产出 UTF-8 / UTF-16LE / UTF-16BE / ISO-8859-1 四种；`else` 为防御分支
 * （未来若探测口径扩展，状态行显示「未知编码」而不是崩溃或露出类名）。
 */
@StringRes
internal fun charsetLabelRes(charset: Charset): Int =
    when (charset) {
        StandardCharsets.UTF_8 -> R.string.repo_file_charset_utf8
        StandardCharsets.UTF_16LE -> R.string.repo_file_charset_utf16le
        StandardCharsets.UTF_16BE -> R.string.repo_file_charset_utf16be
        StandardCharsets.ISO_8859_1 -> R.string.repo_file_charset_latin1
        else -> R.string.repo_file_charset_unknown
    }
