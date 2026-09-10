@file:Suppress("LongParameterList")
// MarkdownComposer 是本仓唯一的"编辑/预览 + 工具栏"装配点：参数就是它要接的三类信息
// （内容与回调 / 编辑器令牌 / 预览插槽 + 补全候选）。拆成 Parameter Object 只会把
// 同一份信息换个地方堆，调用方还得先造对象——可读性反而更差（同 detekt LongMethod 的
// 专项抑制先例：T3/T24）。

package com.yumiru11.githubapp.core.editor

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Checklist
import com.composables.icons.materialsymbols.rounded.Code
import com.composables.icons.materialsymbols.rounded.Code_blocks
import com.composables.icons.materialsymbols.rounded.Format_bold
import com.composables.icons.materialsymbols.rounded.Format_h1
import com.composables.icons.materialsymbols.rounded.Format_italic
import com.composables.icons.materialsymbols.rounded.Format_list_bulleted
import com.composables.icons.materialsymbols.rounded.Format_list_numbered
import com.composables.icons.materialsymbols.rounded.Format_quote
import com.composables.icons.materialsymbols.rounded.Image
import com.composables.icons.materialsymbols.rounded.Link

/**
 * Markdown 组合器（#166 / UI05，ui-design §3.9 D2-3 用户拍板）。
 *
 * 把「编辑/预览双 Tab + md 工具栏 + 编辑器」这三件套收敛成一个组件，供两处复用：
 *   - feature:editor 的全屏 Markdown 编辑页
 *   - feature:issue 的评论输入 Sheet（§3.9 要求「上滑 Sheet + 编辑/预览 + 底部 md 功能按钮」）
 *
 * **为什么放在 core:editor**：编辑器内核（Sora）与语法动作（[MarkdownSyntaxFormatter]）
 * 都在这里，且工具栏文案属于编辑器语义；放在 feature 层就得复制一份或让 feature 互相引用
 * （本仓 Konsist 明令禁止 feature 互引）。
 *
 * **预览为什么由宿主注入**：预览需要 Markdown 渲染管线（core:markdown 的 WebView /
 * 原生 viewer），而 core:editor 刻意不依赖 core:markdown（Sora 与 WebView 两套重依赖
 * 不该绑在一起）。宿主传一个 `preview` 插槽，两边各自选合适的渲染档位：
 * 编辑页用 WebView（长文档、与 README 一致），评论预览用原生 viewer（短文本，
 * 守「评论列表绝不用 WebView」这条铁律）。
 *
 * @param text 当前文本（编辑器是文本唯一事实源，此处用于初始化与预览）
 * @param isPreview 是否处于预览态
 * @param onTogglePreview 切到预览/编辑（参数 = 目标态）
 * @param preview 预览内容插槽（宿主提供）
 * @param showTabs 是否渲染编辑/预览切换行（评论 Sheet 可关掉以省高度）
 */
@Composable
fun MarkdownComposer(
    text: String,
    isPreview: Boolean,
    onTogglePreview: (Boolean) -> Unit,
    onTextChanged: (String) -> Unit,
    onEditorReady: (MarkdownEditorController) -> Unit,
    onToolbarAction: (MarkdownToolbarAction) -> Unit,
    themeTokens: EditorThemeTokens,
    preview: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    mentions: List<String> = emptyList(),
    emojis: List<MarkdownEmoji> = DEFAULT_MARKDOWN_EMOJIS,
    showTabs: Boolean = true,
) {
    Column(modifier = modifier.fillMaxSize()) {
        if (showTabs) {
            MarkdownComposerTabs(
                isPreview = isPreview,
                onEditClick = { if (isPreview) onTogglePreview(false) },
                onPreviewClick = { if (!isPreview) onTogglePreview(true) },
            )
        }
        if (isPreview) {
            preview()
        } else {
            MarkdownToolbar(onAction = onToolbarAction)
            MarkdownEditorView(
                content = text,
                themeTokens = themeTokens,
                mentions = mentions,
                emojis = emojis,
                onEditorReady = onEditorReady,
                onTextChanged = onTextChanged,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** 编辑/预览双 Tab 行（选中态 primary，未选中 onSurfaceVariant）。 */
@Composable
private fun MarkdownComposerTabs(
    isPreview: Boolean,
    onEditClick: () -> Unit,
    onPreviewClick: () -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth()) {
        ComposerTab(
            text = stringResource(R.string.editor_tab_edit),
            selected = !isPreview,
            onClick = onEditClick,
            modifier = Modifier.weight(1f),
        )
        ComposerTab(
            text = stringResource(R.string.editor_tab_preview),
            selected = isPreview,
            onClick = onPreviewClick,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ComposerTab(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    androidx.compose.material3.TextButton(onClick = onClick, modifier = modifier) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color =
                if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
        )
    }
}

/** 工具栏：横向滚动图标按钮行（11 个语法动作，ui-design §3.9「底部 md 功能按钮」）。 */
@Composable
private fun MarkdownToolbar(onAction: (MarkdownToolbarAction) -> Unit) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp),
    ) {
        ToolbarButton(MaterialSymbols.Rounded.Format_bold, R.string.editor_bold) { onAction(MarkdownToolbarAction.BOLD) }
        ToolbarButton(MaterialSymbols.Rounded.Format_italic, R.string.editor_italic) { onAction(MarkdownToolbarAction.ITALIC) }
        ToolbarButton(MaterialSymbols.Rounded.Code, R.string.editor_inline_code) { onAction(MarkdownToolbarAction.INLINE_CODE) }
        ToolbarButton(MaterialSymbols.Rounded.Code_blocks, R.string.editor_code_block) { onAction(MarkdownToolbarAction.CODE_BLOCK) }
        ToolbarButton(MaterialSymbols.Rounded.Format_h1, R.string.editor_heading) { onAction(MarkdownToolbarAction.HEADING) }
        ToolbarButton(MaterialSymbols.Rounded.Format_list_bulleted, R.string.editor_unordered_list) {
            onAction(MarkdownToolbarAction.UNORDERED_LIST)
        }
        ToolbarButton(MaterialSymbols.Rounded.Format_list_numbered, R.string.editor_ordered_list) {
            onAction(MarkdownToolbarAction.ORDERED_LIST)
        }
        ToolbarButton(MaterialSymbols.Rounded.Checklist, R.string.editor_task_list) { onAction(MarkdownToolbarAction.TASK_LIST) }
        ToolbarButton(MaterialSymbols.Rounded.Link, R.string.editor_link) { onAction(MarkdownToolbarAction.LINK) }
        ToolbarButton(MaterialSymbols.Rounded.Image, R.string.editor_image) { onAction(MarkdownToolbarAction.IMAGE) }
        ToolbarButton(MaterialSymbols.Rounded.Format_quote, R.string.editor_quote) { onAction(MarkdownToolbarAction.QUOTE) }
    }
}

/** 单个工具栏图标按钮（矢量图标 + contentDescription，禁 emoji）。 */
@Composable
private fun ToolbarButton(
    icon: ImageVector,
    contentDescriptionRes: Int,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick) {
        Icon(
            imageVector = icon,
            contentDescription = stringResource(contentDescriptionRes),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
