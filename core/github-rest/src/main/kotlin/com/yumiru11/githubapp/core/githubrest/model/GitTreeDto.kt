package com.yumiru11.githubapp.core.githubrest.model

import kotlinx.serialization.Serializable

/**
 * Git Tree 响应 DTO（GET /repos/{owner}/{repo}/git/trees/{treeSha}，T11 文件树）。
 *
 * GitHub Git Data API 返回：
 * ```json
 * {
 *   "sha": "9fb037999f264ba9a7fc6274d1faef2cf7a2b1b3",
 *   "truncated": false,
 *   "tree": [
 *     { "path": "README.md", "mode": "100644", "type": "blob", "sha": "...", "size": 14, "url": "..." },
 *     { "path": "src", "mode": "040000", "type": "tree", "sha": "...", "url": "..." }
 *   ]
 * }
 * ```
 *
 * 按需展开策略（非递归，plan.md §4.5 `recursive=1` 可整树拉取但大仓库会 truncated）：
 * 根树与每个子目录树各请求一次，`path` 为相对该树根的路径。
 *
 * `truncated` 为 true 时树不完整（大仓库），UI 应提示而非静默展示残缺列表。
 */
@Serializable
data class GitTreeResponseDto(
    val sha: String? = null,
    val truncated: Boolean = false,
    val tree: List<TreeItemDto> = emptyList(),
)

/**
 * Git Tree 单条目 DTO。
 *
 * @param path 相对所请求树的路径（子目录树条目为相对该目录的短路径，需上层拼接父路径）
 * @param mode 文件模式（100644/100755/040000/120000/160000）
 * @param type "blob"（文件）/ "tree"（目录）/ "commit"（submodule）
 * @param sha 条目对象 SHA（tree 条目 = 子树 SHA，可用于再次 getTree 展开）
 * @param size 文件字节数（仅 blob；tree 无该字段）
 */
@Serializable
data class TreeItemDto(
    val path: String = "",
    val mode: String? = null,
    val type: String? = null,
    val sha: String? = null,
    val size: Long? = null,
)
