package com.yumiru11.githubapp.core.editor

/**
 * 文件内查找状态机（#166 / UI14 代码浏览屏；**纯逻辑** —— 无 Android / Sora 类型，可直接单测）。
 *
 * 为什么单独成类（而不是把「第 n / 共 m 项」写在 Composable 里）：
 * - Sora 的 `EditorSearcher` 是**异步**的：查询词变更后要等后台线程扫完才派发结果事件，
 *   期间界面仍需一个自洽的状态可渲染（[isSearching] 表达该过渡态，避免闪一帧「无匹配」）；
 * - 序号推进 / 回绕（末项之后回到首项）、空查询词、无匹配、Sora 序号越界都是**纯算术**，
 *   写进 UI 层就无法单测（AGENTS.md：逻辑不进 View）。
 *
 * 数据流向：本状态机是展示事实源；[CodeEditorController] 回灌 Sora 匹配表的权威值
 * （[withResults]）收敛。[cycledNext] / [cycledPrevious] 为**乐观推进**——与 Sora
 * `cyclicJumping = true` 的环形语义一致，回灌后立即对齐（AGENTS.md 乐观更新约定）。
 *
 * 序号一律 0 起（Sora `currentMatchedPositionIndex` 同口径），展示层用 [matchOrdinal] 转 1 起。
 *
 * @param query 当前查询词（原样保留空格；空串 = 不查找且无高亮）
 * @param matchCount 匹配总数（权威值来自 Sora；搜索中先为 0）
 * @param currentMatchIndex 当前匹配序号 0 起；[NO_MATCH] = 无选中项
 * @param isSearching 查询词已变更但结果未回灌（过渡态）
 */
data class FileFindState(
    val query: String = "",
    val matchCount: Int = 0,
    val currentMatchIndex: Int = NO_MATCH,
    val isSearching: Boolean = false,
) {
    /** 是否有查询词（空查询词 = 不查找、无高亮）。 */
    val hasQuery: Boolean get() = query.isNotEmpty()

    /** 是否有可跳转的匹配项（驱动「上一处 / 下一处」可用态）。 */
    val hasMatches: Boolean get() = matchCount > 0

    /** 展示用 1 起序号；无当前匹配为 0。 */
    val matchOrdinal: Int get() = if (currentMatchIndex == NO_MATCH) 0 else currentMatchIndex + 1

    /**
     * 查询词变更：计数与序号**立即复位**（绝不显示上一查询词的陈旧计数），
     * 非空查询词进入 [isSearching]（结果由 [withResults] 回灌）。
     */
    fun withQuery(query: String): FileFindState =
        copy(
            query = query,
            matchCount = 0,
            currentMatchIndex = NO_MATCH,
            isSearching = query.isNotEmpty(),
        )

    /**
     * 回灌 Sora 权威结果：无匹配 → 清零；序号越界或未选中（Sora 返回 -1）→ 记为 [NO_MATCH]，
     * 不臆造序号（首次命中由宿主在结果到位后补一跳）。
     */
    fun withResults(
        matchCount: Int,
        currentMatchIndex: Int,
    ): FileFindState {
        if (matchCount <= 0) return copy(matchCount = 0, currentMatchIndex = NO_MATCH, isSearching = false)
        val index = if (currentMatchIndex in 0 until matchCount) currentMatchIndex else NO_MATCH
        return copy(matchCount = matchCount, currentMatchIndex = index, isSearching = false)
    }

    /** 下一处（循环：末项之后回到首项；未选中时取首项）。无匹配时原样返回。 */
    fun cycledNext(): FileFindState {
        if (!hasMatches) return this
        val next = if (currentMatchIndex == NO_MATCH) 0 else (currentMatchIndex + 1) % matchCount
        return copy(currentMatchIndex = next)
    }

    /** 上一处（循环：首项之前回到末项；未选中时取末项）。无匹配时原样返回。 */
    fun cycledPrevious(): FileFindState {
        if (!hasMatches) return this
        val previous =
            if (currentMatchIndex == NO_MATCH) {
                matchCount - 1
            } else {
                (currentMatchIndex - 1 + matchCount) % matchCount
            }
        return copy(currentMatchIndex = previous)
    }

    companion object {
        /** 无当前匹配项的哨兵值（与 Sora `currentMatchedPositionIndex` 的 -1 同值）。 */
        const val NO_MATCH: Int = -1
    }
}
