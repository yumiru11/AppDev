package com.yumiru11.githubapp.feature.repo

/**
 * 文件查看分类（T11 验收：大文件/二进制有明确提示而非卡死）。
 */
enum class FileKind {
    /** 文本代码（Sora 高亮展示） */
    CODE,

    /** Markdown 文本（Rendered/Source 可切换） */
    MARKDOWN,

    /** 二进制（嗅探判定，给提示） */
    BINARY,

    /** 超过大小上限（给提示，不取内容） */
    TOO_LARGE,
}

/**
 * 文件内容查看结果（RepoRepository 产出，VM 状态消费）。
 *
 * @param text 解码文本（CODE/MARKDOWN 非空；BINARY/TOO_LARGE 为 null）
 */
data class FileContentData(
    val fileName: String,
    val path: String,
    val size: Long,
    val kind: FileKind,
    val text: String? = null,
    /** 文件 blob SHA（T22 编辑提交必需：PUT sha 校验 / DELETE sha 必填）。 */
    val sha: String? = null,
)

/**
 * 文件分类器（纯函数，供单元测试直接验证）。
 *
 * 规则（与验收标准对应）：
 * - size > 1MB → TOO_LARGE（GitHub Contents API 对 >1MB 文件不返回 content，直接提示不请求内容）
 * - 首 8KB 含 NUL 字节 → BINARY（标准文本/二进制嗅探启发式；UTF-16 文本会命中，与 GitHub 网页行为一致）
 * - 扩展名 .md/.markdown → MARKDOWN
 * - 其余 → CODE
 */
object FileClassifier {
    /** 大文件阈值：GitHub Contents API content 字段上限（1MB）；超过即视为不可预览 */
    const val LARGE_FILE_LIMIT_BYTES = 1_048_576L

    private const val BINARY_SNIFF_BYTES = 8_192

    /**
     * @param fileName 文件名（可含路径）
     * @param size 文件字节数（GitHub size 字段）
     * @param bytes 已解码文件字节（TOO_LARGE 判定先于 bytes 判空；BINARY 判定需要字节）
     */
    fun classify(
        fileName: String,
        size: Long,
        bytes: ByteArray?,
    ): FileKind {
        if (size > LARGE_FILE_LIMIT_BYTES) return FileKind.TOO_LARGE
        if (bytes != null && isBinary(bytes)) return FileKind.BINARY
        val base = fileName.substringAfterLast('/')
        val ext = base.substringAfterLast('.', "").lowercase()
        if (ext == "md" || ext == "markdown") return FileKind.MARKDOWN
        return FileKind.CODE
    }

    /** NUL 字节嗅探（只扫前 [BINARY_SNIFF_BYTES] 字节，避免大数组全量遍历）。 */
    fun isBinary(bytes: ByteArray): Boolean {
        val limit = minOf(bytes.size, BINARY_SNIFF_BYTES)
        for (i in 0 until limit) {
            if (bytes[i] == 0.toByte()) return true
        }
        return false
    }
}
