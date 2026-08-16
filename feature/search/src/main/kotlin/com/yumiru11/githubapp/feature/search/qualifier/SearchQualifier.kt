package com.yumiru11.githubapp.feature.search.qualifier

/**
 * 搜索 qualifier 快速建议（docs/ui-design.md §3.3：`is:issue`、`language:kotlin`、`user:` 等）。
 *
 * 点击 chip 时把 [value] 追加到当前输入（若已存在则不重复），随后立即提交搜索。
 * qualifier 值为 GitHub 搜索语法字面量（数据而非 UI 文案），无需本地化。
 */
data class SearchQualifier(
    /** chip 展示文本（与 value 一致：qualifier 即展示内容） */
    val label: String,
    /** 追加到查询的 qualifier 字面量 */
    val value: String,
)

/** 常用 qualifier 静态清单（YAGNI：只收最高频的几条） */
val QUALIFIER_SUGGESTIONS: List<SearchQualifier> =
    listOf(
        SearchQualifier(label = "is:issue", value = "is:issue"),
        SearchQualifier(label = "is:pr", value = "is:pr"),
        SearchQualifier(label = "is:open", value = "is:open"),
        SearchQualifier(label = "language:kotlin", value = "language:kotlin"),
        SearchQualifier(label = "stars:>100", value = "stars:>100"),
        SearchQualifier(label = "user:", value = "user:"),
    )

/**
 * 把 qualifier 追加到当前输入：
 * - 输入为空 → 直接返回 qualifier
 * - 输入已包含该 qualifier → 原样返回（防重复）
 * - 否则 → "输入 qualifier"（空格分隔）
 */
fun appendQualifier(
    input: String,
    qualifier: String,
): String {
    val trimmed = input.trim()
    if (trimmed.isEmpty()) return qualifier
    if (trimmed.contains(qualifier)) return trimmed
    return "$trimmed $qualifier"
}
