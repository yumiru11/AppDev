package com.yumiru11.githubapp.core.markdown

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Chevron_right
import com.mikepenz.markdown.compose.components.MarkdownComponentModel
import com.mikepenz.markdown.compose.elements.MarkdownText
import com.mikepenz.markdown.m3.Markdown
import com.yumiru11.githubapp.core.markdown.native.HtmlDetailsParser

/**
 * HTML_BLOCK 原生处理：识别 `<details>` 时自研折叠；其余类型降级为文本。
 *
 * 这是 mikepenz 0.38.1 无 details 槽的 spike；若 future 版本提供 details 槽应替换本组件。
 */
@Composable
fun EnhancedHtmlBlock(model: MarkdownComponentModel) {
    val details = remember(model.content, model.node) { HtmlDetailsParser.parse(model.content, model.node) }
    if (details != null) {
        NativeDetailsCard(summary = details.summary, body = details.body)
    } else if (!isClosingDetailsOnly(model)) {
        MarkdownText(
            content = model.content,
            node = model.node,
            style = model.typography.text,
        )
    }
}

private fun isClosingDetailsOnly(model: MarkdownComponentModel): Boolean {
    val text = model.content.substring(model.node.startOffset, model.node.endOffset).trim()
    return text.equals("</details>", ignoreCase = true)
}

@Composable
private fun NativeDetailsCard(
    summary: String,
    body: String,
) {
    var expanded by remember { mutableStateOf(false) }
    val arrowRotation by animateFloatAsState(if (expanded) 90f else 0f, label = "detailsArrow")

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable { expanded = !expanded }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Icon(
                    imageVector = MaterialSymbols.Rounded.Chevron_right,
                    contentDescription = stringResource(R.string.details_expand),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp).rotate(arrowRotation),
                )
                Text(
                    text = summary,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            AnimatedVisibility(visible = expanded) {
                Markdown(
                    content = body,
                    modifier = Modifier.padding(start = 38.dp, end = 12.dp, bottom = 12.dp),
                )
            }
        }
    }
}
