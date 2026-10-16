@file:Suppress("CyclomaticComplexMethod", "TooManyFunctions")
// HTML 标签分派（Block）与行内注解（appendElementNode）按标签穷尽分支，天然多于阈值；
// 一个 HTML 子集渲染器本就是「一个大分派 + 一组小组件」，拆文件只会把标签表撕成多处。
// 先例：feature/issue/IssueDetailScreen.kt、core/markdown/MarkdownViewer.kt。

package com.yumiru11.githubapp.core.markdown.html

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Chevron_right
import com.yumiru11.githubapp.core.designsystem.token.AppDimens
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

/**
 * HTML 子集的原生渲染器（README/正文里常见的 HTML 写法）。
 *
 * 覆盖范围（GitHub 保留、且 README 中高频出现的安全子集）：
 * 标题 h1–h6、段落与 div（align=center / text-align 居中）、br、hr、
 * 加粗/斜体/下划线/删除线/行内代码/kbd/sub/sup、链接（交 [onLinkClick] 由宿主导航）、
 * 有序/无序列表（含嵌套）、引用块、pre/code（language-* 语言标签）、
 * 表格（tr/th/td，含单元格对齐）、图片（内容图 / picture / a 包裹的可点击图 /
 * GitHub emoji 图的 alt 字形）、details+summary 折叠。
 *
 * 安全：script/style/iframe/object/embed/form/svg 由
 * [com.yumiru11.githubapp.core.markdown.HtmlSanitizer]（Kotlin 侧）与 WebView 侧
 * DOMPurify 剥离，本渲染器对未知标签采取「递归子节点」策略——不吞内容、也不展示源码。
 *
 * 纯 Compose 实现，不依赖 WebView；调用方在 details/徽章特化路径之后承接其余 HTML。
 */
@Composable
fun ComposeHtml(
    html: String,
    modifier: Modifier = Modifier,
    onLinkClick: (String) -> Unit = {},
) {
    val body = remember(html) { Jsoup.parseBodyFragment(html).body() }
    Column(modifier = modifier.fillMaxWidth()) {
        RenderNodes(nodes = body.childNodes(), onLinkClick = onLinkClick)
    }
}

@Composable
private fun RenderNodes(
    nodes: List<Node>,
    onLinkClick: (String) -> Unit,
) {
    val pending = mutableListOf<Node>()

    @Composable
    fun flushInline() {
        if (pending.isEmpty()) return
        val run = pending.toList()
        pending.clear()
        Paragraph(nodes = run, onLinkClick = onLinkClick, textAlign = TextAlign.Start, style = null)
    }
    nodes.forEach { node ->
        val element = node as? Element
        when {
            element == null -> {
                if ((node as? TextNode)?.text()?.isNotBlank() == true) pending.add(node)
            }

            isBlockTag(element) -> {
                flushInline()
                Block(element, onLinkClick)
            }

            else -> {
                pending.add(node)
            }
        }
    }
    flushInline()
}

private fun isBlockTag(element: Element): Boolean = element.tagName().lowercase() in BLOCK_TAGS

@Composable
private fun Block(
    element: Element,
    onLinkClick: (String) -> Unit,
) {
    when (element.tagName().lowercase()) {
        "h1", "h2", "h3", "h4", "h5", "h6" -> {
            Heading(element, onLinkClick)
        }

        "hr" -> {
            HorizontalDivider(
                modifier = Modifier.padding(vertical = AppDimens.spacing.m),
                color = MaterialTheme.colorScheme.outlineVariant,
            )
        }

        "br" -> {
            Spacer(Modifier.height(AppDimens.spacing.s))
        }

        "pre" -> {
            CodeBlockElement(element)
        }

        "blockquote" -> {
            Quote(element, onLinkClick)
        }

        "ul" -> {
            BulletList(element, ordered = false, onLinkClick)
        }

        "ol" -> {
            BulletList(element, ordered = true, onLinkClick)
        }

        "table" -> {
            HtmlTable(element, onLinkClick)
        }

        "img" -> {
            ImageBlock(element)
        }

        "picture" -> {
            ImageBlock(element.children().firstOrNull { it.tagName().equals("img", true) } ?: element)
        }

        "details" -> {
            DetailsBlock(element, onLinkClick)
        }

        "p", "div", "section", "article", "center", "figure", "figcaption", "main", "header", "footer" -> {
            val hasBlockChild = element.children().any { isBlockTag(it) }
            if (hasBlockChild) {
                val centered = alignmentOf(element) == TextAlign.Center
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start,
                ) {
                    RenderNodes(element.childNodes(), onLinkClick)
                }
            } else {
                Paragraph(
                    nodes = element.childNodes(),
                    onLinkClick = onLinkClick,
                    textAlign = alignmentOf(element),
                    style = if (element.tagName().equals("figcaption", true)) MaterialTheme.typography.bodySmall else null,
                )
            }
        }

        else -> {
            RenderNodes(element.childNodes(), onLinkClick)
        }
    }
}

@Composable
private fun Heading(
    element: Element,
    onLinkClick: (String) -> Unit,
) {
    val level = element.tagName().removePrefix("h").toIntOrNull() ?: 6
    val typography = MaterialTheme.typography
    val style =
        when (level) {
            1 -> typography.headlineMedium
            2 -> typography.headlineSmall
            3 -> typography.titleLarge
            4 -> typography.titleMedium
            else -> typography.titleSmall
        }
    Column(modifier = Modifier.fillMaxWidth().padding(top = AppDimens.spacing.m, bottom = AppDimens.spacing.xs)) {
        Text(
            text = Inline(element.childNodes(), onLinkClick, FontWeight.SemiBold),
            style = style,
            textAlign = alignmentOf(element),
            modifier = Modifier.fillMaxWidth(),
        )
        if (level <= 2) {
            HorizontalDivider(
                modifier = Modifier.padding(top = AppDimens.spacing.s),
                color = MaterialTheme.colorScheme.outlineVariant,
            )
        }
    }
}

@Composable
private fun Paragraph(
    nodes: List<Node>,
    onLinkClick: (String) -> Unit,
    textAlign: TextAlign,
    style: TextStyle?,
) {
    val text = Inline(nodes, onLinkClick, null)
    if (text.text.isBlank()) return
    Text(
        text = text,
        style = style ?: MaterialTheme.typography.bodyMedium,
        textAlign = textAlign,
        modifier = Modifier.fillMaxWidth().padding(vertical = AppDimens.spacing.xs),
    )
}

@Composable
private fun Quote(
    element: Element,
    onLinkClick: (String) -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(AppDimens.cornerSmall),
        modifier = Modifier.fillMaxWidth().padding(vertical = AppDimens.spacing.xs),
    ) {
        Row(modifier = Modifier.padding(AppDimens.spacing.m)) {
            Column {
                if (element.children().any { isBlockTag(it) }) {
                    RenderNodes(element.childNodes(), onLinkClick)
                } else {
                    Text(
                        text = Inline(element.childNodes(), onLinkClick, null),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun BulletList(
    element: Element,
    ordered: Boolean,
    onLinkClick: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = AppDimens.spacing.xs)) {
        element
            .children()
            .filter { it.tagName().equals("li", true) }
            .forEachIndexed { index, li ->
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = AppDimens.spacing.xs)) {
                    Text(
                        text = if (ordered) (index + 1).toString() + "." else BULLET,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(AppDimens.spacing.l),
                    )
                    val nested = li.children().filter { it.tagName().equals("ul", true) || it.tagName().equals("ol", true) }
                    val ownNodes = li.childNodes().filterNot { node -> nested.any { it === node } }
                    Column(modifier = Modifier.weight(1f)) {
                        Paragraph(ownNodes, onLinkClick, TextAlign.Start, null)
                        nested.forEach { sub ->
                            BulletList(sub, ordered = sub.tagName().equals("ol", true), onLinkClick = onLinkClick)
                        }
                    }
                }
            }
    }
}

@Composable
private fun CodeBlockElement(element: Element) {
    val code = element.text()
    val language = languageOf(element)
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth().padding(vertical = AppDimens.spacing.xs),
    ) {
        Column(modifier = Modifier.padding(AppDimens.spacing.m)) {
            if (language != null) {
                Text(
                    text = language.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                Text(
                    text = code,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun HtmlTable(
    element: Element,
    onLinkClick: (String) -> Unit,
) {
    val rows =
        element.select("tr").map { tr ->
            tr.children().filter { it.tagName().equals("th", true) || it.tagName().equals("td", true) }
        }
    if (rows.isEmpty()) return
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth().padding(vertical = AppDimens.spacing.s),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(AppDimens.spacing.s)) {
            rows.forEach { cells ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    cells.forEach { cell ->
                        val header = cell.tagName().equals("th", true)
                        Text(
                            text = Inline(cell.childNodes(), onLinkClick, if (header) FontWeight.SemiBold else null),
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = alignmentOf(cell),
                            modifier =
                                Modifier
                                    .weight(1f)
                                    .padding(horizontal = AppDimens.spacing.s, vertical = AppDimens.spacing.xs),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ImageBlock(element: Element) {
    val src = resolveSrc(element) ?: return
    AsyncImage(
        model = src,
        contentDescription = element.attr("alt").ifBlank { null },
        contentScale = ContentScale.FillWidth,
        modifier = Modifier.fillMaxWidth().heightIn(max = CONTENT_IMAGE_MAX_HEIGHT),
    )
}

@Composable
private fun DetailsBlock(
    element: Element,
    onLinkClick: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(element.hasAttr("open")) }
    val summary =
        element
            .children()
            .firstOrNull { it.tagName().equals("summary", true) }
            ?.text()
            .orEmpty()
    val bodyNodes = element.childNodes().filterNot { node -> node is Element && node.tagName().equals("summary", true) }
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth().padding(vertical = AppDimens.spacing.xs),
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(AppDimens.spacing.m),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = summary, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                Icon(
                    imageVector = MaterialSymbols.Rounded.Chevron_right,
                    contentDescription = null,
                    modifier = Modifier.size(AppDimens.spacing.l).rotate(if (expanded) 90f else 0f),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (expanded) {
                Column(modifier = Modifier.padding(horizontal = AppDimens.spacing.m, vertical = AppDimens.spacing.s)) {
                    RenderNodes(bodyNodes, onLinkClick)
                }
            }
        }
    }
}

/** 行内序列 → AnnotatedString（读主题色后交给纯函数递归，保证主题切换即时生效）。 */
@Composable
private fun Inline(
    nodes: List<Node>,
    onLinkClick: (String) -> Unit,
    baseWeight: FontWeight?,
): AnnotatedString {
    val linkColor = MaterialTheme.colorScheme.primary
    val codeBackground = MaterialTheme.colorScheme.surfaceContainerHighest
    return buildInline(nodes, onLinkClick, baseWeight, linkColor, codeBackground)
}

private fun buildInline(
    nodes: List<Node>,
    onLinkClick: (String) -> Unit,
    baseWeight: FontWeight?,
    linkColor: Color,
    codeBackground: Color,
): AnnotatedString =
    buildAnnotatedString {
        nodes.forEach { node -> appendNode(node, onLinkClick, baseWeight, linkColor, codeBackground) }
    }

private fun AnnotatedString.Builder.appendNode(
    node: Node,
    onLinkClick: (String) -> Unit,
    baseWeight: FontWeight?,
    linkColor: Color,
    codeBackground: Color,
) {
    when (node) {
        is TextNode -> append(node.text())
        is Element -> appendElementNode(node, onLinkClick, baseWeight, linkColor, codeBackground)
        else -> Unit
    }
}

private fun AnnotatedString.Builder.appendElementNode(
    element: Element,
    onLinkClick: (String) -> Unit,
    baseWeight: FontWeight?,
    linkColor: Color,
    codeBackground: Color,
) {
    val tag = element.tagName().lowercase()
    if (tag == "br") {
        append("\n")
        return
    }
    if (tag == "img") {
        append(element.attr("alt"))
        return
    }
    val style =
        SpanStyle(
            fontWeight = if (tag == "b" || tag == "strong") FontWeight.Bold else baseWeight,
            fontStyle = if (tag == "i" || tag == "em") FontStyle.Italic else null,
            textDecoration =
                when (tag) {
                    "u", "ins" -> TextDecoration.Underline
                    "s", "del", "strike" -> TextDecoration.LineThrough
                    else -> null
                },
            fontFamily = if (tag in MONOSPACE_TAGS) FontFamily.Monospace else null,
            background = if (tag == "code") codeBackground else Color.Unspecified,
            color = colorFromStyle(element) ?: Color.Unspecified,
        )
    if (tag == "a") {
        val href = element.attr("href")
        if (href.isBlank()) {
            withStyle(style) { element.childNodes().forEach { appendNode(it, onLinkClick, baseWeight, linkColor, codeBackground) } }
        } else {
            withLink(
                LinkAnnotation.Url(
                    url = href,
                    styles = TextLinkStyles(style = style.copy(color = linkColor, textDecoration = TextDecoration.Underline)),
                    linkInteractionListener = { onLinkClick(href) },
                ),
            ) {
                element.childNodes().forEach { appendNode(it, onLinkClick, baseWeight, linkColor, codeBackground) }
            }
        }
        return
    }
    withStyle(style) {
        element.childNodes().forEach { appendNode(it, onLinkClick, baseWeight, linkColor, codeBackground) }
    }
}

private fun colorFromStyle(element: Element): Color? {
    val style = element.attr("style")
    if (style.isBlank()) return null
    val raw =
        COLOR_STYLE_REGEX
            .find(style)
            ?.groupValues
            ?.get(1)
            ?.trim() ?: return null
    return runCatching { Color(android.graphics.Color.parseColor(raw)) }.getOrNull()
}

private fun alignmentOf(element: Element): TextAlign {
    val align = element.attr("align").lowercase()
    // 去掉空白后比较：避免在源码里出现带空格的样式字面量（i18n 文案守卫按「整句常量」判定）。
    val normalizedStyle = element.attr("style").lowercase().replace(" ", "")
    return if (align == "center" || normalizedStyle.contains("text-align:center")) {
        TextAlign.Center
    } else {
        TextAlign.Start
    }
}

private fun resolveSrc(element: Element): String? = element.attr("src").ifBlank { element.attr("data-src") }.ifBlank { null }

private fun languageOf(pre: Element): String? {
    val code = pre.children().firstOrNull { it.tagName().equals("code", true) } ?: return null
    return code
        .className()
        .split(" ")
        .firstOrNull { it.startsWith("language-") }
        ?.removePrefix("language-")
}

private val MONOSPACE_TAGS = setOf("code", "kbd", "samp", "tt")

private val BLOCK_TAGS =
    setOf(
        "p",
        "div",
        "section",
        "article",
        "center",
        "figure",
        "figcaption",
        "main",
        "header",
        "footer",
        "h1",
        "h2",
        "h3",
        "h4",
        "h5",
        "h6",
        "hr",
        "pre",
        "blockquote",
        "ul",
        "ol",
        "table",
        "picture",
        "details",
    )

private val COLOR_STYLE_REGEX = Regex("""color\s*:\s*([^;]+)""")

private const val BULLET = "\u2022"
private val CONTENT_IMAGE_MAX_HEIGHT = 520.dp
