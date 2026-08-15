package com.yumiru11.githubapp.core.markdown

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mikepenz.markdown.compose.components.MarkdownComponentModel
import com.mikepenz.markdown.compose.elements.MarkdownTable
import com.mikepenz.markdown.compose.elements.MarkdownTableHeader
import com.mikepenz.markdown.compose.elements.MarkdownTableRow
import com.yumiru11.githubapp.core.markdown.native.MarkdownTableParser

/**
 * B 增强版表格：圆角容器 + 官方 MarkdownTable 布局 + 粗体表头 + 表头底色。
 *
 * 布局委托官方 MarkdownTable（内部 requiredWidth 保证列宽；此前自绘用
 * widthIn(min) 在父级 maxWidth 更小时塌缩为 0 → 真机字符叠加，2026-08-16 验证）。
 */
@Composable
fun EnhancedMarkdownTable(model: MarkdownComponentModel) {
    val data = remember(model.content, model.node) { MarkdownTableParser.parse(model.content, model.node) }
    if (data.header.isEmpty()) return

    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.padding(vertical = 8.dp),
    ) {
        MarkdownTable(
            content = model.content,
            node = model.node,
            style = model.typography.table,
            headerBlock = { content, header, tableWidth, style ->
                MarkdownTableHeader(
                    content = content,
                    header = header,
                    tableWidth = tableWidth,
                    style = style.copy(fontWeight = FontWeight.Bold),
                    // 官方默认 maxLines=1 + Ellipsis：长单元格被截断成 "GitHub token..."
                    // （2026-08-16 真机验证），放开行数让单元格自动换行。
                    maxLines = Int.MAX_VALUE,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Visible,
                )
            },
            rowBlock = { content, header, tableWidth, style ->
                MarkdownTableRow(
                    content = content,
                    header = header,
                    tableWidth = tableWidth,
                    style = style,
                    maxLines = Int.MAX_VALUE,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Visible,
                )
            },
        )
    }
}
