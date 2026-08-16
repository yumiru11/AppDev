package com.yumiru11.githubapp.core.githubrest.model

import kotlinx.serialization.Serializable

/**
 * 文件内容 DTO（GET /repos/{owner}/{repo}/contents/{path}，T11 代码浏览）。
 *
 * 单文件响应：元数据 + base64 编码内容，字段形态与 [ReadmeDto] 一致（同一 GitHub 对象模型），
 * 但语义上是任意路径文件（T22 编辑提交将扩展同一端点的 PUT/DELETE）。
 *
 * 限制（GitHub Contents API）：
 * - `content` 仅对 ≤1MB 的文件返回；超大文件 content 为空，须走 Git Blobs API（T11 不实现，
 *   按验收标准对大文件给提示——见 feature/repo FileTypeDetector）。
 * - 二进制文件 content 仍为原始字节的 base64（type 字段为 "file"），由上层嗅探判定二进制。
 */
@Serializable
data class FileContentDto(
    val name: String = "",
    val path: String = "",
    val sha: String? = null,
    val size: Long = 0L,
    val type: String? = null,
    val content: String? = null,
    val encoding: String? = null,
    val downloadUrl: String? = null,
) {
    /**
     * 解码文件内容为原始字节（base64 + 行尾 \n 分段处理）。
     * 二进制嗅探（NUL 检测）必须用字节而非字符串，避免 UTF-8 解码损耗。
     */
    fun decodeBytes(): ByteArray? {
        if (content == null || encoding == null) return null
        if (encoding != "base64") return content.toByteArray()
        val cleaned = content.replace("\n", "")
        return runCatching {
            java.util.Base64
                .getDecoder()
                .decode(cleaned)
        }.getOrNull()
    }

    /**
     * 解码文件内容（UTF-8 文本；二进制文件请用 [decodeBytes] 嗅探）。
     * 与 [ReadmeDto.decodeContent] 同逻辑；DTO 各自自包含，T22 统一时再抽取。
     */
    fun decodeContent(): String? = decodeBytes()?.decodeToString()
}
