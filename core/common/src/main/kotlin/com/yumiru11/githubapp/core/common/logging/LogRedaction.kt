package com.yumiru11.githubapp.core.common.logging

/**
 * 日志脱敏（#169 / L14）：token 绝不落日志。
 *
 * 动机：debug 变体接入 Timber + Chucker 后，日志面变大；OAuth token / PAT /
 * 授权码一旦被打印就会随 logcat、崩溃上报、Chucker 导出外泄（plan.md §15.4 安全契约）。
 *
 * 做法：在**写出日志前**对整条消息做正则替换，把机密片段换成 [MASK]。纯函数、无 Android
 * 依赖，可单测；宁可多掩（例如把 `code=200` 也掩成 `***`）也不漏掩——多掩只损失排查
 * 细节，漏掩是不可逆的凭据泄露。
 *
 * 覆盖形态：
 * - GitHub token 字面量：`ghp_` / `gho_` / `ghu_` / `ghs_` / `ghr_` / `github_pat_`
 * - HTTP 头：`Authorization: Bearer <token>` / `token <token>`
 * - 键值对：`access_token=…`、`"client_secret": "…"`、`refresh_token: …`
 * - URL query：`?access_token=…&…`
 */
object LogRedaction {
    /** 掩码字面量：长度短于各规则的最小机密长度，避免二次匹配 */
    const val MASK = "***"

    /** 一条规则 = 一个正则 + 需要掩掉的捕获组序号（0 = 整个匹配） */
    private data class Rule(
        val pattern: Regex,
        val secretGroup: Int = 0,
    )

    /**
     * 需要脱敏的「键」：`key=value` / `"key": "value"` 形态里的键名。
     * 抽成常量是为了让正则行不触发 detekt MaxLineLength，同时键集合保持单点可读。
     */
    private const val SECRET_KEYS =
        "access_token|refresh_token|id_token|client_secret|client_id|code_verifier|code|token|password|authorization"

    private val RULES =
        listOf(
            // GitHub token 字面量（PAT / OAuth / server-to-server / refresh）
            Rule(Regex("""\bgh[pousr]_[A-Za-z0-9]{16,}\b""")),
            Rule(Regex("""\bgithub_pat_[A-Za-z0-9_]{16,}\b""")),
            // Authorization 头
            Rule(Regex("""(?i)\b(?:bearer|token)\s+[A-Za-z0-9._~+/=-]{8,}""")),
            // key=value / "key": "value"（JSON）—— 组 3 为机密值，保留键名与分隔符便于排查。
            // 分隔符两侧的引号都要吃进去：否则 `"access_token":"abc"` 形态会漏掩
            // （值首字符是引号，落不进值字符类）。
            Rule(
                Regex("""(?i)\b($SECRET_KEYS)\b("?\s*[=:]\s*"?)([^\s"'&,}\]]{4,})"""),
                secretGroup = 3,
            ),
            // URL query（组 2 为机密值）
            Rule(
                Regex("""(?i)([?&](?:access_token|token|client_secret|code)=)([^\s&"']+)"""),
                secretGroup = 2,
            ),
        )

    /** 返回脱敏后的日志文本；无命中时原样返回。 */
    fun redact(message: String): String = RULES.fold(message) { acc, rule -> applyRule(acc, rule) }

    private fun applyRule(
        input: String,
        rule: Rule,
    ): String {
        val matches = rule.pattern.findAll(input).toList()
        if (matches.isEmpty()) return input
        val builder = StringBuilder(input)
        // 从后往前替换：前面的替换不会让后面匹配到的区间偏移
        matches.asReversed().forEach { match ->
            val group = match.groups[rule.secretGroup] ?: return@forEach
            builder.replace(group.range.first, group.range.last + 1, MASK)
        }
        return builder.toString()
    }
}
