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
        )

    /** 危险协议（href/src 中需剥离），仅允许 http/https/mailto（plan.md §15.3） */
    private val DANGEROUS_PROTOCOL_PATTERNS =
        listOf(
            Regex("(?i)\\s+href\\s*=\\s*\"\\s*javascript:[^\"]*\""),
            Regex("(?i)\\s+href\\s*=\\s*'\\s*javascript:[^']*'"),
            Regex("(?i)\\s+src\\s*=\\s*\"\\s*javascript:[^\"]*\""),
            Regex("(?i)\\s+src\\s*=\\s*'\\s*javascript:[^']*'"),
            Regex("(?i)\\s+href\\s*=\\s*\"\\s*data:[^\"]*\""),
            Regex("(?i)\\s+href\\s*=\\s*'\\s*data:[^']*'"),
            Regex("(?i)\\s+src\\s*=\\s*\"\\s*data:[^\"]*\""),
            Regex("(?i)\\s+src\\s*=\\s*'\\s*data:[^']*'"),
            Regex("(?i)\\s+href\\s*=\\s*\"\\s*blob:[^\"]*\""),
            Regex("(?i)\\s+href\\s*=\\s*'\\s*blob:[^']*'"),
            Regex("(?i)\\s+src\\s*=\\s*\"\\s*blob:[^\"]*\""),
            Regex("(?i)\\s+src\\s*=\\s*'\\s*blob:[^']*'"),
            Regex("(?i)\\s+href\\s*=\\s*\"\\s*file:[^\"]*\""),
            Regex("(?i)\\s+href\\s*=\\s*'\\s*file:[^']*'"),
            Regex("(?i)\\s+src\\s*=\\s*\"\\s*file:[^\"]*\""),
            Regex("(?i)\\s+src\\s*=\\s*'\\s*file:[^']*'"),
            Regex("(?i)\\s+href\\s*=\\s*\"\\s*vbscript:[^\"]*\""),
            Regex("(?i)\\s+href\\s*=\\s*'\\s*vbscript:[^']*'"),
            Regex("(?i)\\s+src\\s*=\\s*\"\\s*vbscript:[^\"]*\""),
            Regex("(?i)\\s+src\\s*=\\s*'\\s*vbscript:[^']*'"),
        )

    /** 事件处理器属性（on* 全剥离） */
    private val EVENT_HANDLER_PATTERN = Regex("(?i)\\s+on[a-z]+\\s*=\\s*(\"[^\"]*\"|'[^']*'|[^\\s>]+)")

    /** style 属性（CSS 表达式注入风险） */
    private val STYLE_ATTR_PATTERN = Regex("(?i)\\s+style\\s*=\\s*(\"[^\"]*\"|'[^']*'|[^\\s>]+)")

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

        // 4. 剥离危险协议（javascript: / data:）
        DANGEROUS_PROTOCOL_PATTERNS.forEach { pattern ->
            result = result.replace(pattern, "")
        }

        return result
    }
}
