package com.yumiru11.githubapp.core.githubrest.http

/**
 * GitHub 分页 Link 响应头解析（#166 / UI21）。
 *
 * 为什么需要它：REST 列表端点**不返回总数**，GitHub 自己在网页端也是靠 Link 头推的 ——
 * `GET /user/starred?per_page=1` 的响应头形如：
 * ```
 * link: <https://api.github.com/user/starred?per_page=1&page=2>; rel="next",
 *       <https://api.github.com/user/starred?per_page=1&page=42>; rel="last"
 * ```
 * 取 `rel="last"` 的 page 数 × per_page 即得总数（最后一页不满时略微高估，见 [totalCount] 说明）。
 *
 * 纯函数、无 Android 依赖，JVM 可单测 —— 这类"字符串协议解析"是最容易写错又最该测的一类代码。
 */
object GitHubLinkHeader {
    private val LINK_ENTRY = Regex("""<([^>]+)>\s*;\s*rel="([^"]+)"""")
    private val PAGE_PARAM = Regex("""[?&]page=(\d+)""")

    /** 解析 `rel="last"` 的 page 值；头缺失/无 last 段/URL 无 page 参数时返回 null。 */
    fun lastPage(linkHeader: String?): Int? {
        if (linkHeader.isNullOrBlank()) return null
        LINK_ENTRY.findAll(linkHeader).forEach { match ->
            val (url, rel) = match.destructured
            if (rel == "last") {
                return PAGE_PARAM
                    .find(url)
                    ?.groupValues
                    ?.get(1)
                    ?.toIntOrNull()
            }
        }
        return null
    }

    /**
     * 用 Link 头与本次结果条数推算总数。
     *
     * - 有 `rel="last"`：`lastPage × perPage`（最后一页不满时**略微高估**——
     *   与 GitHub 网页端"stars 计数"的口径一致，此处不额外发请求校准）
     * - 无 Link 头：说明只有一页，总数 = 本次返回条数
     *
     * @param currentPageSize 本次请求实际返回的条数（用 per_page=1 探测时应传 1 或 0）
     */
    fun totalCount(
        linkHeader: String?,
        perPage: Int,
        currentPageSize: Int,
    ): Int {
        val last = lastPage(linkHeader)
        if (last == null) return currentPageSize
        return (last * perPage).coerceAtLeast(currentPageSize)
    }
}
