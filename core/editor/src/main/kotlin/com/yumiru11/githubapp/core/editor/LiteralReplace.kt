package com.yumiru11.githubapp.core.editor

/** 字面量匹配区间（字符偏移，含首不含尾）。 */
data class LiteralMatch(
    val start: Int,
    val end: Int,
) {
    /** 匹配长度（编辑器 `Content.replace` 的结束列推导用）。 */
    val length: Int get() = end - start
}

/** replace-all 计划：替换后的全文 + 替换处数（0 处时 [text] 原样返回）。 */
data class LiteralReplacePlan(
    val text: String,
    val count: Int,
)

/**
 * 字面量查找/替换的纯逻辑（EDITOR-1；无 Android / Sora 类型，可单测）。
 *
 * 为什么自建扫描而不是复用 Sora `EditorSearcher` 的匹配表：
 * 1. `EditorSearcher` 的匹配表**不对外暴露**（`lastResults` 是 protected），无法用于「替换全部」；
 * 2. Sora 自带的 `replaceAll` 会弹 `ProgressDialog` 且把替换放到后台线程，宿主既不能本地化
 *    文案也无法在一次调用里拿到替换处数（测试不可控）；
 * 3. 匹配规则本身极简：[indexOf] 与 Sora `TextUtils.indexOf`（0.23.6 字节码核实）**逐行同构**——
 *    逐字符比对，`caseInsensitive` 时按 `Character.toLowerCase` 判定——因此「替换的全部」
 *    与查找高亮的命中集合一致。
 *
 * 宿主用法：[CodeEditorController.replaceAll] 用 [replaceAll] 得到全文后，一次性写回
 * `Content.replace(...)`，整次替换在 Sora 撤销栈里只有**一个**动作（单步撤销）。
 */
object LiteralSearch {
    /**
     * 与 Sora `TextUtils.indexOf(text, pattern, caseInsensitive, fromIndex)` 同语义的扫描。
     *
     * @return 首个 ≥ [fromIndex] 的匹配起始偏移；无匹配返回 -1
     */
    fun indexOf(
        text: CharSequence,
        query: CharSequence,
        caseInsensitive: Boolean,
        fromIndex: Int,
    ): Int {
        if (query.isEmpty() || query.length > text.length) return -1
        val maxStart = text.length - query.length
        var start = fromIndex.coerceAtLeast(0)
        while (start <= maxStart) {
            var offset = 0
            while (offset < query.length) {
                val source = text[start + offset]
                val target = query[offset]
                if (source != target && !(caseInsensitive && source.lowercaseChar() == target.lowercaseChar())) {
                    break
                }
                offset++
            }
            if (offset == query.length) return start
            start++
        }
        return -1
    }

    /**
     * 全部**不重叠**匹配（按出现顺序；与 Sora 扫描的推进方式一致：命中后跳过整个匹配）。
     *
     * 空查询词返回空表（Sora 的 `search("")` 会抛 `IllegalArgumentException`，
     * 调用方以空查询词 = 不查找）。
     */
    fun findAll(
        text: String,
        query: String,
        caseInsensitive: Boolean,
    ): List<LiteralMatch> {
        if (query.isEmpty()) return emptyList()
        val matches = mutableListOf<LiteralMatch>()
        var from = 0
        while (true) {
            val index = indexOf(text, query, caseInsensitive, from)
            if (index < 0) break
            matches += LiteralMatch(index, index + query.length)
            from = index + query.length
        }
        return matches
    }

    /** 替换全文中的全部匹配（返回新全文与替换处数；不修改入参）。 */
    fun replaceAll(
        text: String,
        query: String,
        replacement: String,
        caseInsensitive: Boolean,
    ): LiteralReplacePlan {
        val matches = findAll(text, query, caseInsensitive)
        if (matches.isEmpty()) return LiteralReplacePlan(text, 0)
        val builder = StringBuilder(text.length - matches.sumOf { it.length } + replacement.length * matches.size)
        var cursor = 0
        for (match in matches) {
            builder.append(text, cursor, match.start)
            builder.append(replacement)
            cursor = match.end
        }
        builder.append(text, cursor, text.length)
        return LiteralReplacePlan(builder.toString(), matches.size)
    }

    /** 替换**单处**匹配（返回新全文；不修改入参）。 */
    fun replaceOne(
        text: String,
        match: LiteralMatch,
        replacement: String,
    ): String = text.substring(0, match.start) + replacement + text.substring(match.end)

    /**
     * 选出「当前」匹配（replace-one 的目标）：
     * 1. 与当前选区完全重合的匹配（用户经查找面板跳到的那一处）；
     * 2. 否则取光标之后（含光标处）的首个匹配；
     * 3. 否则回绕到首个匹配（与查找面板的环形跳转语义一致）。
     */
    fun selectMatch(
        matches: List<LiteralMatch>,
        selectionStart: Int,
        selectionEnd: Int,
    ): LiteralMatch? =
        matches.firstOrNull { it.start == selectionStart && it.end == selectionEnd }
            ?: matches.firstOrNull { it.start >= selectionStart }
            ?: matches.firstOrNull()
}
