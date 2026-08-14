package com.yumiru11.githubapp.core.markdown

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import com.mikepenz.markdown.coil3.Coil3ImageTransformerImpl
import com.mikepenz.markdown.compose.components.markdownComponents
import com.mikepenz.markdown.compose.elements.MarkdownCodeFence
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.model.rememberMarkdownState
import com.yumiru11.githubapp.core.navigation.link.GitHubLinkParser
import com.yumiru11.githubapp.core.navigation.link.ParsedUrl

/**
 * Markdown 渲染组件。
 *
 * 基于 mikepenz multiplatform-markdown-renderer 0.38.1 + KotlinTextMate 高亮。
 *
 * @param markdown Markdown 原文
 * @param onInternalLink 链接点击回调；Internal 类型（Repo/Issue/PR 等）由上层路由处理，
 *   External 类型默认打开 CustomTabs（上层处理）；默认空实现
 * @param modifier Modifier
 */
@Composable
fun MarkdownViewer(
    markdown: String,
    onInternalLink: (ParsedUrl) -> Unit = {},
    baseRepoUrl: String? = null,
    modifier: Modifier = Modifier,
) {
    val darkTheme = isSystemInDarkTheme()
    val state = rememberMarkdownState(markdown, immediate = true)

    // 链接点击接线：renderer 0.38.1 无 link 槽位，所有链接统一走 LocalUriHandler
    // （annotatorSettings 内部唯一消费点，构建 LinkAnnotation.Url 后经 openUri 分发）。
    // 在此覆盖 handler，把每次点击解析为 ParsedUrl 后交上层（Internal→应用内导航，
    // External→CustomTabs）。上层可能重组替换回调，用 rememberUpdatedState 取最新值。
    val currentOnInternalLink by rememberUpdatedState(onInternalLink)
    val currentBaseRepoUrl by rememberUpdatedState(baseRepoUrl)
    val linkUriHandler =
        remember {
            object : UriHandler {
                override fun openUri(uri: String) {
                    dispatchMarkdownLink(uri, currentBaseRepoUrl, currentOnInternalLink)
                }
            }
        }

    CompositionLocalProvider(LocalUriHandler provides linkUriHandler) {
        Markdown(
            state,
            imageTransformer = Coil3ImageTransformerImpl,
            colors =
                markdownColor(
                    inlineCodeBackground = MaterialTheme.colorScheme.surfaceContainerHighest,
                ),
            components =
                markdownComponents(
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
 */
fun parseMarkdownLink(
    url: String,
    baseRepoUrl: String? = null,
): ParsedUrl = GitHubLinkParser.parseUrl(resolveMarkdownUrl(url, baseRepoUrl))

/**
 * 仓库上下文相对链接解析（2026-08-14 真机走查修复：README 相对链接跳浏览器）。
 *
 * - 绝对 URL（http/https/mailto）→ 原样返回
 * - 相对路径（`docs/x.md`、`./x`、`/x`）→ `{baseRepoUrl}/blob/HEAD/{path}`
 * - 锚点（`#section`）→ `{baseRepoUrl}#section`
 * - 无 [baseRepoUrl] → 原样返回（无上下文可解析）
 */
fun resolveMarkdownUrl(
    url: String,
    baseRepoUrl: String?,
): String {
    if (baseRepoUrl == null) return url
    if (url.startsWith("http://") || url.startsWith("https://") || url.startsWith("mailto:")) return url

    val trimmed = url.removePrefix("./").removePrefix("/")
    return if (trimmed.startsWith("#")) {
        "$baseRepoUrl$trimmed"
    } else {
        "$baseRepoUrl/blob/HEAD/$trimmed"
    }
}

/**
 * Markdown 链接点击分流：解析 URL 后回调 [onInternalLink]。
 *
 * 纯函数（无 Compose/Android 依赖，可 JVM 单测）。内部链接回调具体 [ParsedUrl]
 * 子类型（Repo/Issue/PR/User 等，上层做应用内导航），外部链接回调
 * [ParsedUrl.External]（上层经 ExternalLinkHost 开 CustomTabs）。
 */
fun dispatchMarkdownLink(
    url: String,
    baseRepoUrl: String? = null,
    onInternalLink: (ParsedUrl) -> Unit,
) {
    onInternalLink(parseMarkdownLink(url, baseRepoUrl))
}
