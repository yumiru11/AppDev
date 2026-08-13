@file:Suppress("ReturnCount")
// 探测器天然多分支早返回（mermaid/重型HTML/超长/普通 各一判定），guard-clause 风格
// 拆散反损可读性；T3 GitHubLinkParser 同类先例已采用此抑制方式。

package com.yumiru11.githubapp.core.markdown.webview

/**
 * Markdown 内容复杂度探测结果（FeatureDetector 输出）。
 *
 * - [Native]：内容简单，走 T7 原生渲染器（MarkdownViewer）
 * - [WebView]：内容复杂或 renderer 0.38.1 体验差，走 T8 WebView 兜底通道
 */
sealed interface FallbackDecision {
    /** 走原生渲染器（mikepenz multiplatform-markdown-renderer 0.38.1） */
    data object Native : FallbackDecision

    /** 走 WebView 兜底通道，附触发原因（便于日志与调试） */
    data class WebView(
        val reason: FallbackReason,
    ) : FallbackDecision
}

/** 触发 WebView 兜底的原因 */
enum class FallbackReason {
    /** mermaid 围栏代码块（renderer 不支持图形渲染） */
    MERMAID,

    /** 数学公式（$math$ / $$math$$，renderer 0.38.1 不支持 LaTeX） */
    MATH,

    /** 重型 HTML（多个 table / details / svg 等内嵌 HTML 块） */
    HEAVY_HTML,

    /** 超长文档（行数或字节超阈值） */
    TOO_LONG,
}

/**
 * Markdown 特性探测器（纯函数，无 Android / Compose 依赖，可 JVM 单测）。
 *
 * 判定规则（plan.md §2.2 预处理层「特性探测」）：
 * 1. mermaid 围栏（```mermaid 或 ```MERMAID，大小写不敏感）→ [FallbackReason.MERMAID]
 * 2. 重型 HTML（table / details / svg 等 HTML 块计数 ≥ 阈值）→ [FallbackReason.HEAVY_HTML]
 * 3. 超长文档（行数 > MAX_LINES 或字节数 > MAX_BYTES）→ [FallbackReason.TOO_LONG]
 * 4. 其余（普通 README / 短正文）→ [FallbackDecision.Native]
 *
 * 单个 markdown 表格由原生 renderer 渲染（ADR-0005：原生裁剪接受），
 * 仅多个 HTML table 块才判定为重型 HTML 走兜底。
 */
object FeatureDetector {
    /** 超长文档行数阈值（plan.md §2.2：>2000 行视为超长） */
    const val MAX_LINES = 2000

    /** 超长文档字节阈值（>50KB 视为超长） */
    const val MAX_BYTES = 50_000

    /** 重复型重型 HTML 块计数阈值：table / details 出现次数 ≥ 此值才走兜底（单个由原生裁剪） */
    const val REPEATED_HTML_THRESHOLD = 2

    /** 单次出现即触发的复杂 HTML 标签（renderer 0.38.1 无法渲染图形/交互/折叠） */
    private val CRITICAL_HTML_TAGS = listOf("<svg", "<canvas", "<iframe", "<math", "<details")

    /** 重复型重型 HTML 标签（多次出现才触发兜底；单次由原生处理，如单个 table 走原生裁剪 ADR-0005） */
    private val REPEATED_HTML_TAGS = listOf("<table")

    /** mermaid 围栏正则（大小写不敏感，宽松匹配 ```mermaid 后缀） */
    private val MERMAID_FENCE_REGEX = Regex("""```mermaid\b""", RegexOption.IGNORE_CASE)

    /** 行内数学公式 $...$ 或块级 $$...$$（排除 $ 后跟数字如价格的误判） */
    private val MATH_REGEX = Regex("""\$\$[^\$]+\$\$|\$[^\$\d\s][^\$]*[^\$\s]\$""")

    /**
     * 判定 markdown 内容是否需要走 WebView 兜底通道。
     *
     * @param markdown 原始 markdown 文本（空串返回 Native）
     * @return [FallbackDecision]，含触发原因（若走兜底）
     */
    fun shouldFallback(markdown: String): FallbackDecision {
        if (markdown.isEmpty()) return FallbackDecision.Native

        // 1. mermaid 围栏
        if (MERMAID_FENCE_REGEX.containsMatchIn(markdown)) {
            return FallbackDecision.WebView(FallbackReason.MERMAID)
        }

        // 1b. 数学公式 $...$ / $$...$$
        if (MATH_REGEX.containsMatchIn(markdown)) {
            return FallbackDecision.WebView(FallbackReason.MATH)
        }

        // 2a. 单次出现即触发的复杂 HTML（svg/canvas/iframe/math）
        if (CRITICAL_HTML_TAGS.any { tag -> markdown.contains(tag, ignoreCase = true) }) {
            return FallbackDecision.WebView(FallbackReason.HEAVY_HTML)
        }

        // 2b. 重复型重型 HTML（table/details 出现次数累加 ≥ 阈值）
        val repeatedCount = REPEATED_HTML_TAGS.sumOf { tag -> countOccurrences(markdown, tag) }
        if (repeatedCount >= REPEATED_HTML_THRESHOLD) {
            return FallbackDecision.WebView(FallbackReason.HEAVY_HTML)
        }

        // 3. 超长文档（行数或字节超阈值）
        val lineCount = markdown.count { it == '\n' } + 1
        if (lineCount > MAX_LINES || markdown.length > MAX_BYTES) {
            return FallbackDecision.WebView(FallbackReason.TOO_LONG)
        }

        return FallbackDecision.Native
    }

    /** 大小写不敏感统计子串出现次数（split 法，避免正则元字符干扰） */
    private fun countOccurrences(
        text: String,
        literal: String,
    ): Int {
        val lower = text.lowercase()
        val needle = literal.lowercase()
        if (needle.isEmpty()) return 0
        var count = 0
        var idx = lower.indexOf(needle)
        while (idx >= 0) {
            count++
            idx = lower.indexOf(needle, idx + needle.length)
        }
        return count
    }
}
