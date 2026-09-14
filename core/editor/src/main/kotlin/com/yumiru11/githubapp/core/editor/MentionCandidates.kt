package com.yumiru11.githubapp.core.editor

/**
 * `@mention` 补全候选的归一化。
 *
 * [MarkdownComposer] 的 `mentions` 参数（进而 [MarkdownEditorView] /
 * [MarkdownCompletionProvider]）只负责「前缀匹配 + 插入」，候选列表本身由宿主准备。
 * 宿主拿到的原始数据来自网络（仓库协作者 / Issue 参与者），必然带重复、空项与
 * 不定的顺序——本函数是**唯一的归一化入口**，两个调用点共用，避免各写一份：
 *
 * - 丢弃 null / 空白项（REST 列表出现空 login 属异常数据，不能进补全面板）
 * - 去掉首尾空白
 * - 大小写不敏感去重（GitHub login 大小写不敏感；保留**首次出现**的写法）
 * - 大小写不敏感字典序排序（`sortedWith` 稳定：同一 login 的多种写法按先来后到）
 * - 截断到 [limit]（超大型组织的协作者列表会灌爆 Sora 补全面板）
 *
 * 纯函数，无 Android 依赖，可直接 JVM 单测。
 *
 * @param logins 原始 login 序列（允许 null / 空白 / 重复 / 大小写混排）
 * @param limit 候选上限；`<= 0` 视为无候选
 */
fun assembleMentionCandidates(
    logins: Iterable<String?>,
    limit: Int = MAX_MENTION_CANDIDATES,
): List<String> {
    if (limit <= 0) return emptyList()
    val byKey = LinkedHashMap<String, String>()
    for (raw in logins) {
        val login = raw?.trim().orEmpty()
        if (login.isEmpty()) continue
        byKey.putIfAbsent(login.lowercase(), login)
    }
    return byKey.values.sortedWith(String.CASE_INSENSITIVE_ORDER).take(limit)
}

/**
 * 候选上限。
 *
 * 50 ≈ Sora 补全面板可扫读的量级（面板高度有限，再多只能靠滚动）；
 * 同时把请求侧 `per_page=100` 的单页结果收敛到稳定规模。
 */
const val MAX_MENTION_CANDIDATES: Int = 50
