package com.yumiru11.githubapp.core.markdown

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.mikepenz.markdown.coil3.Coil3ImageTransformerImpl
import com.mikepenz.markdown.compose.components.markdownComponents
import com.mikepenz.markdown.compose.elements.MarkdownCodeFence
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.model.rememberMarkdownState
import com.yumiru11.githubapp.core.navigation.link.GitHubLinkParser
import com.yumiru11.githubapp.core.navigation.link.ParsedUrl

/**
 * Markdown 渲染组件（骨架）。
 *
 * 基于 mikepenz multiplatform-markdown-renderer 0.38.1 + KotlinTextMate 高亮。
 *
 * @param markdown Markdown 原文
 * @param onInternalLink 内部链接点击回调；Internal 类型（Repo/Issue/PR 等）由上层路由处理，
 *   External 类型默认打开 CustomTabs（上层处理）；默认空实现
 * @param modifier Modifier
 */
@Composable
fun MarkdownViewer(
    markdown: String,
    onInternalLink: (ParsedUrl) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val darkTheme = isSystemInDarkTheme()
    val state = rememberMarkdownState(markdown, immediate = true)

    Markdown(
        state,
        imageTransformer = Coil3ImageTransformerImpl,
        colors = markdownColor(
            inlineCodeBackground = MaterialTheme.colorScheme.surfaceContainerHighest,
        ),
        components = markdownComponents(
            codeFence = { model ->
                MarkdownCodeFence(model.content, model.node, model.typography.code) { code, language, _ ->
                    TextMateCodeBlock(code, language, darkTheme)
                }
            },
            blockQuote = { model -> GitHubAlertOrQuote(model) },
        ),
        modifier = modifier.verticalScroll(rememberScrollState()),
    )
}

/**
 * 解析 Markdown 链接 URL 为 [ParsedUrl]。
 *
 * 内部链接（GitHub 域名）返回具体的 [ParsedUrl] 子类型（Repo/Issue/PR 等），
 * 外部链接返回 [ParsedUrl.External]。
 *
 * 用法示例：
 * ```kotlin
 * val parsed = parseMarkdownLink("https://github.com/owner/repo/issues/42")
 * // parsed = ParsedUrl.Issue("owner", "repo", 42)
 * ```
 *
 * TODO: 接线点击处理
 * 0.38.1 的 link 组件 API 限制：[MarkdownComponentModel] 不直接暴露 URL，
 * 需要从 node AST 子节点提取。当前骨架仅实现解析器纯函数，
 * 点击接线待以下方案之一落地：
 * 1. link 组件内遍历 node.children 查找 LINK_DESTINATION 节点提取 URL
 * 2. 升级到支持 linkResolver 的版本
 * 3. 用 MarkdownState 的 linkClickHandler 机制（如有）
 */
fun parseMarkdownLink(url: String): ParsedUrl = GitHubLinkParser.parseUrl(url)
