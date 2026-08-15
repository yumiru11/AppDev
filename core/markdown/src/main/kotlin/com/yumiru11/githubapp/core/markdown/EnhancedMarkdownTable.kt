package com.yumiru11.githubapp.core.markdown

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mikepenz.markdown.compose.components.MarkdownComponentModel
import com.mikepenz.markdown.compose.elements.MarkdownText
import com.yumiru11.githubapp.core.markdown.native.MarkdownCell
import com.yumiru11.githubapp.core.markdown.native.MarkdownTableData
import com.yumiru11.githubapp.core.markdown.native.MarkdownTableParser

/**
 * B 增强版表格：圆角容器 + 横向滚动 + 表头加粗/上下边框 + 斑马纹。
 *
 * 单元格用 [MarkdownText] 渲染，保留 `**bold**` / `code` 等行内格式。
 */
@Composable
fun EnhancedMarkdownTable(model: MarkdownComponentModel) {
    val data = remember(model.content, model.node) { MarkdownTableParser.parse(model.content, model.node) }
    if (data.header.isEmpty()) return

    val columnCount = data.header.size
    val minWidth = (columnCount * 112).dp

    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(modifier = Modifier.horizontalScroll(rememberScrollState())) {
            Column(modifier = Modifier.widthIn(min = minWidth)) {
                TableRow(
                    cells = data.header,
                    content = model.content,
                    style = model.typography.table.copy(fontWeight = FontWeight.Bold),
                    background = MaterialTheme.colorScheme.surfaceContainerLow,
                    showTopDivider = false,
                )
                data.rows.forEachIndexed { index, row ->
                    TableRow(
                        cells = row,
                        content = model.content,
                        style = model.typography.table,
                        background =
                            if (index % 2 == 0) {
                                MaterialTheme.colorScheme.surfaceContainerLow
                            } else {
                                Color.Transparent
                            },
                        showTopDivider = index == 0,
                    )
                }
            }
        }
    }
}

@Composable
private fun TableRow(
    cells: List<MarkdownCell>,
    content: String,
    style: androidx.compose.ui.text.TextStyle,
    background: Color,
    showTopDivider: Boolean,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(background)
                .padding(vertical = 6.dp),
    ) {
        cells.forEach { cell ->
            MarkdownText(
                content = content,
                node = cell.node,
                modifier = Modifier.weight(1f).padding(horizontal = 13.dp),
                style = style,
            )
        }
    }
    if (showTopDivider) {
        androidx.compose.material3.HorizontalDivider(
            thickness = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant,
        )
    }
}
