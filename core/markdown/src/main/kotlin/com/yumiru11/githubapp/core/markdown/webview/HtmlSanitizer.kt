package com.yumiru11.githubapp.core.markdown.webview

/**
 * HTML 服务端预清洗器（plan.md §2.9 / §2.14 安全锁）。
 *
 * 纯函数，无 Android / WebView 依赖，可 JVM 单测。
 *
 * 防御层次：
 * 1. **本类**（Kotlin 端预清洗）：剥离危险标签 / 事件处理器 / 危险协议（白名单法）
 * 2. **DOMPurify JS**（WebView 内渲染时权威清洗）：完整 HTML 解析 + 白名单
 *
 * 设计为「白名单前置剥离」——剥离已知危险结构，保留安全内容（table/details/a/p 等）。
 * 不做完整 HTML 解析（避免引入 HTML parser 依赖），DOMPurify 是最终权威。
 */
object HtmlSanitizer {
    /** 必须完整剥离的危险标签（含内容） */
    private val DANGEROUS_TAG_PATTERNS =
        listOf(
            Regex("(?is)<script\\b[^>]*>.*?</script\\s*>"),
            Regex("(?is)<iframe\\b[^>]*>.*?</iframe\\s*>"),
            Regex("(?is)<iframe\\b[^>]*/?>"),
            Regex("(?is)<object\\b[^>]*>.*?</object\\s*>"),
            Regex("(?is)<object\\b[^>]*/?>"),
            Regex("(?is)<embed\\b[^>]*/?>"),
            Regex("(?is)<form\\b[^>]*>.*?</form\\s*>"),
            Regex("(?is)<form\\b[^>]*/?>"),
            Regex("(?is)<style\\b[^>]*>.*?</style\\s*>"),
            Regex("(?is)<base\\b[^>]*/?>"),
            Regex("(?is)<meta\\b[^>]*/?>"),
            Regex("(?is)<link\\b[^>]*/?>"),
            // SMIL 动画标签：attributeName/values 可注入事件名与 javascript: 载荷
            // （<animate attributeName=\"href\" values=\"javascript:alert(1)\"> 是经典 mXSS 向量），
            // DOMPurify 默认不白名单，Kotlin 端同步剥离（2026-08-16 补强）。
            Regex("(?is)<(?:animate|animateMotion|animateTransform|set)\\b[^>]*/?>"),
        )

    /**
     * 危险协议（href/src/xlink:href 中需剥离），仅允许 http/https/mailto（plan.md §15.3）。
     *
     * 每个协议覆盖三种取值形态：双引号、单引号、无引号（`href=javascript:alert(1)` 绕过面，
     * 审查确认后补强）。xlink:href 是 SVG 命名空间属性，同样可携带 javascript: 载荷。
     */
    private val DANGEROUS_PROTOCOL_PATTERNS =
        listOf(
            Regex("(?i)\\s+(?:href|xlink:href|src)\\s*=\\s*(?:\"javascript:[^\"]*\"|'javascript:[^']*'|javascript:[^\\s>]+)"),
            Regex("(?i)\\s+(?:href|xlink:href|src)\\s*=\\s*(?:\"data:[^\"]*\"|'data:[^']*'|data:[^\\s>]+)"),
            Regex("(?i)\\s+(?:href|xlink:href|src)\\s*=\\s*(?:\"blob:[^\"]*\"|'blob:[^']*'|blob:[^\\s>]+)"),
            Regex("(?i)\\s+(?:href|xlink:href|src)\\s*=\\s*(?:\"file:[^\"]*\"|'file:[^']*'|file:[^\\s>]+)"),
            Regex("(?i)\\s+(?:href|xlink:href|src)\\s*=\\s*(?:\"vbscript:[^\"]*\"|'vbscript:[^']*'|vbscript:[^\\s>]+)"),
        )

    /** 事件处理器属性（on\w+ 全剥离，含无引号取值变体） */
    private val EVENT_HANDLER_PATTERN = Regex("(?i)\\s+on\\w+\\s*=\\s*(\"[^\"]*\"|'[^']*'|[^\\s>]+)")

    /** style 属性（CSS 表达式注入风险） */
    private val STYLE_ATTR_PATTERN = Regex("(?i)\\s+style\\s*=\\s*(\"[^\"]*\"|'[^']*'|[^\\s>]+)")

    /** href/src/xlink:href 属性整体匹配（双引号/单引号/无引号三种取值形态，用于解码后兜底判定） */
    private val URL_ATTR_PATTERN =
        Regex("""(?i)\s+(?:href|xlink:href|src)\s*=\s*("([^"]*)"|'([^']*)'|([^\s>]+))""")

    /** 危险 scheme 集合（与 [DANGEROUS_PROTOCOL_PATTERNS] 同集，解码后判定用） */
    private val DANGEROUS_SCHEMES = setOf("javascript", "data", "blob", "file", "vbscript")

    /** WHATWG URL 解析在 scheme 判定前会移除的字符（ASCII tab / 换行） */
    private val TAB_OR_NEWLINE_REGEX = Regex("[\\t\\n\\r]")

    private val DECIMAL_ENTITY_REGEX = Regex("&#(\\d+);?")
    private val HEX_ENTITY_REGEX = Regex("&#[xX]([0-9a-fA-F]+);?")

    /** 常用命名实体（属性值上下文解码，含 tab/换行混淆实体；宽松允许省略分号） */
    private val NAMED_ENTITIES =
        mapOf(
            "amp" to "&",
            "lt" to "<",
            "gt" to ">",
            "quot" to "\"",
            "apos" to "'",
            "colon" to ":",
            "Tab" to "\t",
            "NewLine" to "\n",
        )

    /**
     * 预清洗 HTML：剥离危险标签、事件处理器、危险协议。
     *
     * @param html 原始 HTML（服务端返回或本地 markdown-it 渲染产物）
     * @return 清洗后 HTML，可安全注入 WebView（DOMPurify 将二次清洗）
     */
    fun sanitize(html: String): String {
        if (html.isEmpty()) return ""

        var result = html

        // 1. 剥离危险标签（含内容），迭代至稳定（防嵌套构造）
        var previous: String
        do {
            previous = result
            DANGEROUS_TAG_PATTERNS.forEach { pattern ->
                result = result.replace(pattern, "")
            }
        } while (result != previous && result.length < previous.length + 1)

        // 2. 剥离事件处理器属性（on*）
        result = result.replace(EVENT_HANDLER_PATTERN, "")

        // 3. 剥离 style 属性
        result = result.replace(STYLE_ATTR_PATTERN, "")

        // 4. 剥离危险协议（javascript: / data: / blob: / file: / vbscript:，含引号/无引号变体）
        DANGEROUS_PROTOCOL_PATTERNS.forEach { pattern ->
            result = result.replace(pattern, "")
        }

        // 4b. 解码兜底：浏览器解析属性值时先解码字符引用并移除 tab/换行再做 scheme 判定，
        // 直接正则匹配会漏 jav&#x61;script:、&#106;avascript:、javascript&colon;、java\nscript: 等
        // 编码绕过（2026-08-16 审查补强）。解码后按 scheme 白名单整属性剥离。
        result =
            URL_ATTR_PATTERN.replace(result) { match ->
                val value =
                    match.groupValues[2]
                        .ifEmpty { match.groupValues[3] }
                        .ifEmpty { match.groupValues[4] }
                val scheme =
                    decodeHtmlEntities(value)
                        .replace(TAB_OR_NEWLINE_REGEX, "")
                        .substringBefore(':')
                        .lowercase()
                if (scheme in DANGEROUS_SCHEMES) "" else match.value
            }

        return result
    }

    /**
     * 属性值上下文 HTML 实体解码（单层，与浏览器 tokenizer 行为一致；
     * 只用于 scheme 判定，不改动原文）。
     */
    private fun decodeHtmlEntities(text: String): String {
        var out = text
        NAMED_ENTITIES.forEach { (name, replacement) ->
            out = out.replace("&$name;", replacement).replace("&$name", replacement)
        }
        out =
            DECIMAL_ENTITY_REGEX.replace(out) { match ->
                match.groupValues[1]
                    .toIntOrNull()
                    ?.toChar()
                    ?.toString() ?: match.value
            }
        out =
            HEX_ENTITY_REGEX.replace(out) { match ->
                match.groupValues[1]
                    .toIntOrNull(16)
                    ?.toChar()
                    ?.toString() ?: match.value
            }
        return out
    }
}
