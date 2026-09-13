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
 *
 * **保留声明（DEAD-2）**：自 ADR-0007 拍板 WebView 主渲染后，README/正文分流判定不再经本探测器，
 * 生产代码已无调用方（仅 `FeatureDetectorTest` 覆盖）。这是**有意保留**的文档化墓碑：
 * plan.md §2.2 仍把特性探测列为预处理层的一环，判定规则（mermaid/数学/重型 HTML/超长）
 * 可作为未来非 WebView 通道的复用点；删除与否属产品决策（remaining-backlog §6 D-5），
 * 请勿在未决策前当死代码清除。
 */
object FeatureDetector {
    /** 超长文档行数阈值（plan.md §2.2：>2000 行视为超长） */
    const val MAX_LINES = 2000

    /** 超长文档字节阈值（>50KB 视为超长） */
    const val MAX_BYTES = 50_000

    /** 重复型重型 HTML 块计数阈值：table / details 出现次数 ≥ 此值才走兜底（单个由原生裁剪） */
    const val REPEATED_HTML_THRESHOLD = 2

    /** 单次出现即触发的复杂 HTML 标签（原生处理不了：图形/交互/数学） */
    private val CRITICAL_HTML_TAGS = listOf("<svg", "<canvas", "<iframe", "<math")

    /**
     * 重复型重型 HTML 标签。2026-08-16 原型真机验证后收紧：details/table 原生已能渲染
     * （NativeDetailsCard/EnhancedMarkdownTable），不再触发兜底；仅保留原生确实无法处理的
     * 标签（当前无——预留结构，避免未来误判直接走 WebView）。
     */
    private val REPEATED_HTML_TAGS = emptyList<String>()

    /** mermaid 围栏正则（大小写不敏感，宽松匹配 ```mermaid 后缀） */
    private val MERMAID_FENCE_REGEX = Regex("""```mermaid\b""", RegexOption.IGNORE_CASE)

    /**
     * 代码围栏块正则（``` 到闭合 ```，非贪婪、跨行）。MATH 判定前先剥离围栏内容：
     * 代码块里的 \${'$'}{var} / \${'$'}counter 是字符串模板不是数学公式
     * （2026-08-17 真机实证：mikepenz README 代码块导致误判 WebView，#64）。
     */
    private val FENCED_CODE_REGEX = Regex("""```[^\n]*\n.*?```""", RegexOption.DOT_MATCHES_ALL)

    /**
     * 行内数学公式 $...$ 或块级 $$...$$（排除 $ 后跟数字如价格的误判）。
     *
     * 2026-09-13（离线 KaTeX）：与 `renderer.js` 的 `MATH_TOKEN_REGEX` 对齐收紧——
     * 行内公式不跨行（原 `[^\$]*` 可跨行）、允许单字符公式（`$x$`，原规则要求 ≥2 字符）。
     * 本判定只做「是否注入 KaTeX 脚本/CSS」的开关，JS 侧仍是独立且更严的正确性判据
     * （见 docs/research/katex-mermaid-offline-feasibility.md §5）。
     */
    private val MATH_REGEX = Regex("""\$\$[^\$]+\$\$|\$(?![\d\s])[^\$\n]*[^\s$]\$""")

    /**
     * 内容是否含数学公式（$…$ / $$…$$）。
     *
     * 供 WebView 侧决定是否注入离线 KaTeX 运行时（`WebViewHtmlBuilder.needsKatex`）——
     * 与 [shouldFallback] 的 MATH 判定同一规则（先剥离围栏代码块，再匹配 [MATH_REGEX]）。
     */
    fun containsMath(markdown: String): Boolean = MATH_REGEX.containsMatchIn(stripFencedCodeBlocks(markdown))

    /**
     * 内容是否含 mermaid 围栏（三个反引号 + mermaid 信息串）。
     *
     * 供 WebView 侧决定是否注入离线 Mermaid 运行时（`WebViewHtmlBuilder.needsMermaid`）。
     * **有意不剥离围栏代码块**（与 [containsMath] 不同）：mermaid 围栏本身就是围栏代码块，
     * 剥离会把唯一命中源删掉。代价是「文档里用围栏展示 mermaid 语法」会多注入一次脚本，
     * 但 JS 侧（renderer.js 的 renderMermaid）按清洗后的真实 DOM 独立严判，不会误渲染。
     */
    fun containsMermaid(markdown: String): Boolean = MERMAID_FENCE_REGEX.containsMatchIn(markdown)

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

        // 1b. 数学公式 $...$ / $$...$$（先剔除代码围栏内容——代码块中的
        //     ${'$'}{var}/${'$'}counter 非公式，P1 #64）
        if (containsMath(markdown)) {
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

    /** 剔除 ``` 围栏代码块（保留围栏外的正文），MATH 判定前调用。 */
    private fun stripFencedCodeBlocks(markdown: String): String = FENCED_CODE_REGEX.replace(markdown, "")

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
