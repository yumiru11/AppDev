package com.yumiru11.githubapp.feature.repo

import com.yumiru11.githubapp.core.githubrest.model.TreeItemDto

/**
 * 文件树节点（T11：Git Tree 条目 → UI 树）。
 *
 * @param name 显示名（路径最后一段）
 * @param path 仓库内完整路径（根条目 = 自身 path；子树条目 = 父路径 + "/" + 条目 path）
 * @param sha 条目 SHA（目录 = 子树 SHA，用于按需展开）
 * @param isDirectory 是否为目录（Git type = "tree"）
 * @param size 文件字节数（仅文件；目录为 null）
 * @param children 子节点（目录未加载时为 null；已展开且无子项为 emptyList）
 * @param isExpanded 目录是否展开（UI 状态，随 VM 更新）
 */
data class GitTreeNode(
    val name: String,
    val path: String,
    val sha: String,
    val isDirectory: Boolean,
    val size: Long? = null,
    val children: List<GitTreeNode>? = null,
    val isExpanded: Boolean = false,
)

/** 展开后的可见行（扁平列表 + 缩进深度，LazyColumn 直接消费）。 */
data class TreeRow(
    val node: GitTreeNode,
    val depth: Int,
)

/**
 * 文件树构建与展开工具（纯函数，供单元测试直接验证）。
 *
 * 策略（plan.md §4.5 + 验收「递归、按需加载子目录」）：
 * 根树与每个子目录树各请求一次（非递归），子树条目 path 相对所请求树，
 * 拼接父路径得到仓库内完整路径——大仓库不触发整树递归（避免 truncated）。
 */
object FileTreeBuilder {
    /** 根树条目 → 根节点列表（path 即仓库内路径）。 */
    fun buildRootNodes(entries: List<TreeItemDto>): List<GitTreeNode> = buildNodes(entries, parentPath = null)

    /** 子树条目 → 节点列表（完整路径 = parentPath + "/" + 条目 path）。 */
    fun buildChildNodes(
        entries: List<TreeItemDto>,
        parentPath: String,
    ): List<GitTreeNode> = buildNodes(entries, parentPath = parentPath)

    private fun buildNodes(
        entries: List<TreeItemDto>,
        parentPath: String?,
    ): List<GitTreeNode> =
        entries
            .filter { it.path.isNotBlank() }
            .map { entry ->
                val path = parentPath?.let { "$it/${entry.path}" } ?: entry.path
                GitTreeNode(
                    name = entry.path.substringAfterLast('/'),
                    path = path,
                    sha = entry.sha.orEmpty(),
                    isDirectory = entry.type == "tree",
                    size = entry.size,
                )
            }.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))

    /**
     * 按 path 定位节点并应用变换（不可变树更新；未命中返回原列表）。
     * 用于目录展开/收起后重建节点及其子树。
     */
    fun updateNode(
        nodes: List<GitTreeNode>,
        path: String,
        transform: (GitTreeNode) -> GitTreeNode,
    ): List<GitTreeNode> =
        nodes.map { node ->
            when {
                node.path == path -> transform(node)
                node.children != null -> node.copy(children = updateNode(node.children, path, transform))
                else -> node
            }
        }

    /** 展开树 → 可见行列表（目录未展开则不深入）。 */
    fun visibleRows(nodes: List<GitTreeNode>): List<TreeRow> {
        val rows = mutableListOf<TreeRow>()

        fun walk(
            list: List<GitTreeNode>,
            depth: Int,
        ) {
            for (node in list) {
                rows.add(TreeRow(node = node, depth = depth))
                if (node.isDirectory && node.isExpanded && node.children != null) {
                    walk(node.children, depth + 1)
                }
            }
        }
        walk(nodes, depth = 0)
        return rows
    }
}
