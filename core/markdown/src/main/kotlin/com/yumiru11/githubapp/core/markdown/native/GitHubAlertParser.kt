package com.yumiru11.githubapp.core.markdown.native

/** GitHub Alert 类型（渲染层只认五种，未知类型退回普通引用）。 */
enum class GitHubAlertType {
    NOTE,
    TIP,
    IMPORTANT,
    WARNING,
    CAUTION,
}

/** GitHub Alert 解析结果：类型 + 去 `>` 前缀后的正文。 */
data class ParsedGitHubAlert(
    val type: GitHubAlertType,
    val body: String,
)

/**
 * GitHub Alert 纯解析器（`> [!TYPE]` 语法）。
 *
 * 供原生 [com.yumiru11.githubapp.core.markdown.GitHubAlertCard] 复用，
 * 避免把解析逻辑埋在 Composable 内。
 */
object GitHubAlertParser {
    private val ALERT_REGEX = Regex("""(?m)^>?[ \t]*\[!([A-Z]+)\]""")

    fun parse(nodeText: String): ParsedGitHubAlert? {
        val type =
            ALERT_REGEX
                .find(nodeText)
                ?.groupValues
                ?.get(1)
                ?.uppercase()
                ?.let { raw -> GitHubAlertType.entries.firstOrNull { it.name == raw } }
                ?: return null

        val body =
            nodeText
                .lineSequence()
                .drop(1)
                .joinToString("\n") { line -> line.removePrefix(">").trimStart() }
                .trim()
        return ParsedGitHubAlert(type = type, body = body)
    }
}
