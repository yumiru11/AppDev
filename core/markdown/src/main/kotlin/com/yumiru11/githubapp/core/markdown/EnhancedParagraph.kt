package com.yumiru11.githubapp.core.markdown

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.mikepenz.markdown.compose.components.MarkdownComponentModel
import com.mikepenz.markdown.compose.elements.MarkdownText

/**
 * 段落增强：纯图片链接段（GitHub README 高频的 shields 徽章 `[![..](badge.svg)](..)` 独占一行）
 * 提升为块级图（圆角 + 阴影 + 点击预览）；其余段落保持默认。
 *
 * 背景（2026-08-16 真机验证）：mikepenz 0.38.1 无 inlineImage 槽（MarkdownComponents 字段已确认），
 * 内联图走固定 MarkdownInlineImageWithSize 裸渲染（无容器样式）——徽章「又小又粗糙」。
 * 文本层面判定整段为单个图片语法，路由到 EnhancedMarkdownImage。
 */
private val SINGLE_IMAGE_LINK_REGEX = Regex("""^(?:\[)?!\[[^\]]*]\([^)]*\)(?:\]\([^)]*\))?$""")
private val IMAGE_SRC_REGEX = Regex("""!\[[^\]]*]\(([^)\s]+)""")

@Composable
fun EnhancedParagraph(
    model: MarkdownComponentModel,
    baseRepoUrl: String? = null,
) {
    val isSingleHttpImage =
        remember(model.content, model.node, baseRepoUrl) {
            val nodeText = model.content.substring(model.node.startOffset, model.node.endOffset).trim()
            val src = IMAGE_SRC_REGEX.find(nodeText)?.groupValues?.get(1)
            // 相对路径图（有 baseRepoUrl 时）同样提升为块级——否则被 MarkdownText 行内渲染成小图
            // （2026-08-17 真机：openchamber 相对路径截图特别小）
            val hasResolvableSrc = src?.startsWith("http") == true || (src != null && baseRepoUrl != null)
            SINGLE_IMAGE_LINK_REGEX.matches(nodeText) && hasResolvableSrc
        }
    if (isSingleHttpImage) {
        // 仅网络图（徽章）提升为块级且保持原始尺寸；本地图段落保持默认内联渲染
        // （MarkdownImage 需要 IMAGE 节点，段落节点会渲染空——2026-08-16 回归风险规避）
        EnhancedMarkdownImage(model, stretch = false, baseRepoUrl = baseRepoUrl)
    } else {
        MarkdownText(content = model.content, node = model.node, style = model.typography.text)
    }
}
