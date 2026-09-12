package com.yumiru11.githubapp.core.datastore.draft

/**
 * 草稿编辑目标工厂（键规则的唯一定义处）。
 *
 * 段用 [SEGMENT_SEPARATOR]（U+001F）连接而非 `/`：GitHub 的 login / 仓库名 / git ref / 文件路径
 * 都不含控制字符，故段内容里的 `/`（分支名 `feature/x`、路径 `docs/a.md`）不会把段"切错"。
 * 键本身不反解，这里只需要保证**不同目标 → 不同键**。
 *
 * 已接线：文件编辑（Contents API）。评论输入 Sheet / 新建 Issue、PR 表单沿用同一约定
 * （`comment` / `issue` / `pull` 前缀 + owner/repo/number）在后续票接线，键空间已由前缀隔开。
 */
object DraftTargets {
    /**
     * 文件编辑（Contents API）。
     *
     * [ref] 参与键：同一路径在不同分支上是**不同内容**（不同编辑目标），草稿不能互相串。
     *
     * @param ref 当前查看分支（null = 未确定，落空串段；键仍稳定）
     * @param path 仓库内文件路径
     */
    fun fileEdit(
        owner: String,
        repo: String,
        ref: String?,
        path: String,
    ): DraftKey = buildKey("file", owner, repo, ref.orEmpty(), path)

    /**
     * 新建文件（路径在提交对话框才输入 → 键不含路径，按「仓库 + 分支」一个草稿槽）。
     *
     * 只存正文：路径由用户在对话框里填，重启后无法归属到某个具体文件。
     */
    fun newFile(
        owner: String,
        repo: String,
        ref: String?,
    ): DraftKey = buildKey("new", owner, repo, ref.orEmpty())

    private fun buildKey(vararg segments: String): DraftKey = DraftKey(segments.joinToString(SEGMENT_SEPARATOR))

    private const val SEGMENT_SEPARATOR = "\u001F"
}
