package com.yumiru11.githubapp.core.editor

import android.os.Bundle
import io.github.rosemoe.sora.lang.Language
import io.github.rosemoe.sora.lang.analysis.AnalyzeManager
import io.github.rosemoe.sora.lang.completion.CompletionPublisher
import io.github.rosemoe.sora.lang.completion.SimpleCompletionItem
import io.github.rosemoe.sora.lang.format.Formatter
import io.github.rosemoe.sora.lang.smartEnter.NewlineHandler
import io.github.rosemoe.sora.langs.textmate.TextMateLanguage
import io.github.rosemoe.sora.text.CharPosition
import io.github.rosemoe.sora.text.ContentReference
import io.github.rosemoe.sora.widget.SymbolPairMatch

/**
 * emoji 引用条目（GitHub 短码 → 实际字符）。
 */
data class MarkdownEmoji(
    val shortcode: String,
    val emoji: String,
)

/** 补全候选（Sora 适配层据此构造 [SimpleCompletionItem]）。 */
data class MarkdownCompletionCandidate(
    val label: String,
    val commitText: String,
    val prefixLength: Int,
)

/**
 * 补全候选计算器（纯函数，可 JVM 单测；Sora 适配层 [MarkdownEditorLanguage] 只做 API 桥接）。
 *
 * 输入光标前的文本前缀，判定触发类型：
 * - `@` 开头 → mention 补全（候选 = mentions 中前缀匹配项）
 * - `:` 开头 → emoji 补全（候选 = emojis 中短码前缀匹配项）
 * - 其他 → 无候选（返回空列表，由上层决定是否回退到语法关键字补全）
 *
 * @param prefix 光标前的文本（当前行 0..column 子串）
 * @param mentions 可提及的用户名列表（真实数据由上层注入，如仓库协作者）
 * @param emojis emoji 短码表
 */
object MarkdownCompletionProvider {
    fun completionsFor(
        prefix: String,
        mentions: List<String>,
        emojis: List<MarkdownEmoji>,
    ): List<MarkdownCompletionCandidate> {
        if (prefix.isEmpty()) return emptyList()
        // 取最后一个空白后的 token（"see @oct" → "@oct"），支持句中触发
        val lastWhitespace = prefix.indexOfLast { it.isWhitespace() }
        val token = if (lastWhitespace >= 0) prefix.substring(lastWhitespace + 1) else prefix
        if (token.isEmpty()) return emptyList()
        return when (token.first()) {
            '@' -> mentionCandidates(token, mentions)
            ':' -> emojiCandidates(token, emojis)
            else -> emptyList()
        }
    }

    private fun mentionCandidates(
        prefix: String,
        mentions: List<String>,
    ): List<MarkdownCompletionCandidate> {
        val query = prefix.substring(1)
        return mentions
            .filter { it.startsWith(query, ignoreCase = true) }
            .map { mention ->
                MarkdownCompletionCandidate(
                    label = "@$mention",
                    commitText = "@$mention",
                    prefixLength = prefix.length,
                )
            }
    }

    private fun emojiCandidates(
        prefix: String,
        emojis: List<MarkdownEmoji>,
    ): List<MarkdownCompletionCandidate> {
        val query = prefix.substring(1)
        return emojis
            .filter { it.shortcode.startsWith(query, ignoreCase = true) }
            .map { emoji ->
                val commit = ":${emoji.shortcode}:"
                MarkdownCompletionCandidate(
                    label = commit,
                    commitText = commit,
                    prefixLength = prefix.length,
                )
            }
    }
}

/**
 * Markdown 编辑器语言（Sora [Language] 适配层）。
 *
 * 委托 [TextMateLanguage] 提供语法高亮/缩进/符号配对，仅覆写 [requireAutoComplete]：
 * - `@`/`:` 前缀 → 发布 mention/emoji 补全候选
 * - 其他 → 委托给 TextMate（其 auto-complete 已禁用，实际不发布）
 *
 * 构造时需对委托调用 `setAutoCompleteEnabled(false)`，避免 TextMate 关键字补全
 * 与引用补全混出（markdown 无关键字补全需求）。
 */
class MarkdownEditorLanguage(
    private val delegate: TextMateLanguage,
    private val mentions: List<String>,
    private val emojis: List<MarkdownEmoji>,
) : Language {
    override fun getAnalyzeManager(): AnalyzeManager = delegate.analyzeManager

    override fun getInterruptionLevel(): Int = delegate.interruptionLevel

    override fun requireAutoComplete(
        content: ContentReference,
        position: CharPosition,
        publisher: CompletionPublisher,
        extraArguments: Bundle,
    ) {
        val line = content.getLine(position.line)
        val prefix = line.substring(0, position.column)
        val candidates = MarkdownCompletionProvider.completionsFor(prefix, mentions, emojis)
        if (candidates.isEmpty()) {
            delegate.requireAutoComplete(content, position, publisher, extraArguments)
            return
        }
        candidates.forEach { candidate ->
            publisher.addItem(
                SimpleCompletionItem(
                    candidate.label,
                    candidate.commitText,
                    candidate.prefixLength,
                    candidate.commitText,
                ),
            )
        }
        publisher.updateList()
    }

    override fun getIndentAdvance(
        content: ContentReference,
        line: Int,
        column: Int,
    ): Int = delegate.getIndentAdvance(content, line, column)

    override fun useTab(): Boolean = delegate.useTab()

    override fun getFormatter(): Formatter = delegate.formatter

    override fun getSymbolPairs(): SymbolPairMatch = delegate.symbolPairs

    override fun getNewlineHandlers(): Array<NewlineHandler> = delegate.newlineHandlers ?: emptyArray()

    override fun destroy() = delegate.destroy()
}

/** 常用 GitHub emoji 短码表（真实短码，供编辑器默认注入；上层可替换为完整表）。 */
val DEFAULT_MARKDOWN_EMOJIS: List<MarkdownEmoji> =
    listOf(
        MarkdownEmoji("smile", "\uD83D\uDE04"),
        MarkdownEmoji("laughing", "\uD83D\uDE06"),
        MarkdownEmoji("blush", "\uD83D\uDE0A"),
        MarkdownEmoji("joy", "\uD83D\uDE02"),
        MarkdownEmoji("+1", "\uD83D\uDC4D"),
        MarkdownEmoji("-1", "\uD83D\uDC4E"),
        MarkdownEmoji("clap", "\uD83D\uDC4F"),
        MarkdownEmoji("wave", "\uD83D\uDC4B"),
        MarkdownEmoji("rocket", "\uD83D\uDE80"),
        MarkdownEmoji("tada", "\uD83C\uDF89"),
        MarkdownEmoji("heart", "\u2764\uFE0F"),
        MarkdownEmoji("fire", "\uD83D\uDD25"),
        MarkdownEmoji("bug", "\uD83D\uDC1B"),
        MarkdownEmoji("sparkles", "\u2728"),
        MarkdownEmoji("star", "\u2B50"),
        MarkdownEmoji("eyes", "\uD83D\uDC40"),
        MarkdownEmoji("warning", "\u26A0\uFE0F"),
        MarkdownEmoji("white_check_mark", "\u2705"),
        MarkdownEmoji("x", "\u274C"),
        MarkdownEmoji("question", "\u2753"),
        MarkdownEmoji("bulb", "\uD83D\uDCA1"),
        MarkdownEmoji("memo", "\uD83D\uDCDD"),
        MarkdownEmoji("wrench", "\uD83D\uDD27"),
        MarkdownEmoji("package", "\uD83D\uDCE6"),
        MarkdownEmoji("lock", "\uD83D\uDD12"),
        MarkdownEmoji("zap", "\u26A1"),
        MarkdownEmoji("coffee", "\u2615"),
        MarkdownEmoji("pizza", "\uD83C\uDF55"),
    )
