package com.yumiru11.githubapp.feature.repo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 文件分类测试（T11 验收第 3 条：大文件/二进制有明确提示而非卡死）。
 */
class FileClassifierTest {
    @Test
    fun classify_largeFile_overLimit_isTooLarge() {
        assertEquals(
            FileKind.TOO_LARGE,
            FileClassifier.classify("big.txt", FileClassifier.LARGE_FILE_LIMIT_BYTES + 1, byteArrayOf(1, 2, 3)),
        )
    }

    @Test
    fun classify_fileAtExactLimit_isNotTooLarge() {
        // 阈值语义：超过 1MB 才拒绝（=1MB 仍尝试展示）
        val kind = FileClassifier.classify("big.txt", FileClassifier.LARGE_FILE_LIMIT_BYTES, "hello".toByteArray())
        assertEquals(FileKind.CODE, kind)
    }

    @Test
    fun classify_largeFile_nullBytes_stillTooLarge() {
        assertEquals(FileKind.TOO_LARGE, FileClassifier.classify("big.bin", 2_000_000L, null))
    }

    @Test
    fun classify_binaryBytes_isBinary() {
        val bytes = byteArrayOf(0x7F, 0x45, 0x4C, 0x46, 0x02, 0x01, 0x01, 0x00) // ELF 头部含 NUL
        assertEquals(FileKind.BINARY, FileClassifier.classify("app", 8L, bytes))
    }

    @Test
    fun classify_binaryBytesAfterPrefix_isBinary() {
        // NUL 不在首字节但出现在嗅探窗口内
        val bytes = ByteArray(100) { 'A'.code.toByte() }.also { it[50] = 0 }
        assertEquals(FileKind.BINARY, FileClassifier.classify("data.dat", 100L, bytes))
    }

    @Test
    fun classify_nulBeyondSniffWindow_ignored() {
        // 8KB 嗅探窗口之外的 NUL 不判定为二进制（性能边界）
        val bytes = ByteArray(20_000) { 'A'.code.toByte() }.also { it[10_000] = 0 }
        assertFalse(FileClassifier.isBinary(bytes))
    }

    @Test
    fun classify_markdownExtensions_isMarkdown() {
        assertEquals(FileKind.MARKDOWN, FileClassifier.classify("README.md", 10L, "text".toByteArray()))
        assertEquals(FileKind.MARKDOWN, FileClassifier.classify("docs/guide.markdown", 10L, "text".toByteArray()))
    }

    @Test
    fun classify_markdownUpperCaseExtension_isMarkdown() {
        assertEquals(FileKind.MARKDOWN, FileClassifier.classify("README.MD", 10L, "text".toByteArray()))
    }

    @Test
    fun classify_codeFile_isCode() {
        assertEquals(FileKind.CODE, FileClassifier.classify("Main.kt", 10L, "fun main()".toByteArray()))
        assertEquals(FileKind.CODE, FileClassifier.classify("no-extension", 10L, "plain".toByteArray()))
    }

    @Test
    fun classify_nullBytes_fallsThroughToExtension() {
        // 内容为空（GitHub 极端返回）→ 按扩展名分类，不崩溃
        assertEquals(FileKind.MARKDOWN, FileClassifier.classify("x.md", 5L, null))
        assertEquals(FileKind.CODE, FileClassifier.classify("x.kt", 5L, null))
    }

    @Test
    fun isBinary_textUtf8_isFalse() {
        assertFalse(FileClassifier.isBinary("fun main() { println(\"héllo\") }".toByteArray()))
    }

    @Test
    fun isBinary_emptyArray_isFalse() {
        assertFalse(FileClassifier.isBinary(ByteArray(0)))
    }
}
