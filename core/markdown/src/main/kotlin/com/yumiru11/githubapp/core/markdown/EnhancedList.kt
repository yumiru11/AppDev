package com.yumiru11.githubapp.core.markdown

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mikepenz.markdown.compose.LocalMarkdownComponents
import com.mikepenz.markdown.compose.MarkdownElement
import com.mikepenz.markdown.compose.components.MarkdownComponentModel
import com.mikepenz.markdown.compose.elements.MarkdownText
import com.mikepenz.markdown.m3.elements.MarkdownCheckBox
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.flavours.gfm.GFMTokenTypes

/**
 * 自绘列表（marker 与内容 FirstBaseline 对齐）。
 *
 * 官方 MarkdownListItems 的 marker 在 Box 里（Box 无 baseline），alignByBaseline 无法与文本
 * 首行基线对齐（2026-08-16 真机验证偏移）。本组件直接 Row 布局 + alignByBaseline 对齐。
 *
 * 结构（每项 = Column）：第一行 Row(marker + 段落)，嵌套列表换行缩进到下一行
 * （2026-08-16 真机验证：先前把嵌套列表塞进父 Row 导致「竖着连在文本后面」）。
 */
private const val LIST_INDENT_DP = 20

@Composable
fun EnhancedUnorderedList(model: MarkdownComponentModel) {
    val components = LocalMarkdownComponents.current
    Column(Modifier.padding(start = LIST_INDENT_DP.dp)) {
        model.node.children
            .filter { it.type == MarkdownElementTypes.LIST_ITEM }
            .forEach { item ->
                ListItemColumn(
                    model = model,
                    components = components,
                    marker = "\u2022",
                    markerStyle = model.typography.bullet,
                ) { item }
            }
    }
}

@Composable
fun EnhancedOrderedList(model: MarkdownComponentModel) {
    val components = LocalMarkdownComponents.current
    var counter = 0
    Column(Modifier.padding(start = LIST_INDENT_DP.dp)) {
        model.node.children
            .filter { it.type == MarkdownElementTypes.LIST_ITEM }
            .forEach { item ->
                counter++
                ListItemColumn(
                    model = model,
                    components = components,
                    marker = "$counter.",
                    markerStyle = model.typography.ordered,
                ) { item }
            }
    }
}

/** 单个列表项：Row(marker + 段落) + 换行缩进的嵌套列表。 */
@Composable
private fun ListItemColumn(
    model: MarkdownComponentModel,
    components: com.mikepenz.markdown.compose.components.MarkdownComponents,
    marker: String,
    markerStyle: androidx.compose.ui.text.TextStyle,
    item: () -> org.intellij.markdown.ast.ASTNode,
) {
    val node = item()
    val hasCheckbox = node.children.any { it.type == GFMTokenTypes.CHECK_BOX }
    val paragraphs = node.children.filter { it.type == MarkdownElementTypes.PARAGRAPH }
    val nestedLists =
        node.children.filter {
            it.type == MarkdownElementTypes.UNORDERED_LIST || it.type == MarkdownElementTypes.ORDERED_LIST
        }
    // 非段落/非嵌套列表/非 bullet 的零散子节点（如直接文本）
    // 非段落/非嵌套列表/非 bullet 的零散子节点（如直接文本）
    val others =
        node.children.filter {
            it.type != MarkdownElementTypes.PARAGRAPH &&
                it.type != MarkdownElementTypes.UNORDERED_LIST &&
                it.type != MarkdownElementTypes.ORDERED_LIST &&
                it.type != MarkdownTokenTypes.LIST_BULLET &&
                it.type != MarkdownTokenTypes.LIST_NUMBER &&
                it.type != GFMTokenTypes.CHECK_BOX
        }
    // 块级元素（代码块/HTML/表格/引用）绝不能放 marker Row 内：fillMaxWidth 与
    // marker+段落同排互相挤压 → 代码块紧跟文字/溢出屏幕（2026-08-17 真机：
    // EchoMusic「本地开发」缩进围栏代码块紧跟文字 + 右侧漏出背景色）。换行全宽渲染。
    val blockOthers = others.filter { it.type in BLOCK_LEVEL_TYPES }
    val inlineOthers = others.filter { it.type !in BLOCK_LEVEL_TYPES }

    Column(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        Row(Modifier.fillMaxWidth()) {
            if (hasCheckbox) {
                val cb = node.children.first { it.type == GFMTokenTypes.CHECK_BOX }
                Box(Modifier.align(Alignment.CenterVertically).padding(end = 8.dp)) {
                    MarkdownCheckBox(model.content, cb, model.typography.text)
                }
            } else {
                Text(
                    text = marker,
                    style = markerStyle,
                    textAlign = if (markerStyle == model.typography.ordered) TextAlign.End else null,
                    modifier =
                        Modifier
                            .alignByBaseline()
                            .padding(end = 8.dp),
                )
            }
            paragraphs.forEach { para ->
                MarkdownText(
                    content = model.content,
                    node = para,
                    style = model.typography.text,
                    modifier = Modifier.alignByBaseline(),
                )
            }
            inlineOthers.forEach { other ->
                MarkdownElement(other, components, model.content, includeSpacer = false)
            }
        }
        // 块级元素换行全宽渲染（marker Row 之外）
        blockOthers.forEach { block ->
            // 列表内代码块与上文间距（wrapper 自带间距在列表项内失效，2026-08-17 真机）
            Box(Modifier.padding(vertical = 4.dp).fillMaxWidth()) {
                MarkdownElement(block, components, model.content, includeSpacer = false)
            }
        }
        // 嵌套列表：换行 + 缩进（不是塞进父 Row）
        nestedLists.forEach { nested ->
            Box(Modifier.padding(start = LIST_INDENT_DP.dp)) {
                MarkdownElement(nested, components, model.content, includeSpacer = false)
            }
        }
    }
}

/**
 * 列表项直接子节点中的块级元素类型：必须在 marker Row 外换行全宽渲染
 * （Row 内 fillMaxWidth 与 marker/段落同排会互相挤压导致溢出）。
 */
private val BLOCK_LEVEL_TYPES =
    setOf(
        MarkdownElementTypes.CODE_FENCE,
        MarkdownElementTypes.HTML_BLOCK,
        org.intellij.markdown.flavours.gfm.GFMElementTypes.TABLE,
        MarkdownElementTypes.BLOCK_QUOTE,
    )
