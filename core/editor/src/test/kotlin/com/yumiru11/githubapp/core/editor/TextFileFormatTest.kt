package com.yumiru11.githubapp.core.editor

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets

/**
 * [TextFileCodec] 单测（EDITOR-1 CRLF/编码策略的纯逻辑防线）。
 *
 * 核心断言：**打开→保存的字节级往返**——已存在的 CRLF/UTF-16/拉丁文件保存后不被静默改写。
 */
class TextFileFormatTest {
    // ── 行尾探测 ────────────────────────────────────────────────────────────

    @Test
    fun detectLineEnding_crlfFile_returnsCrlf() {
        assertEquals(LineEnding.CRLF, TextFileCodec.detectLineEnding("a\r\nb"))
    }

    @Test
    fun detectLineEnding_lfFile_returnsLf() {
        assertEquals(LineEnding.LF, TextFileCodec.detectLineEnding("a\nb"))
    }

    @Test
    fun detectLineEnding_crOnlyFile_returnsCr() {
        assertEquals(LineEnding.CR, TextFileCodec.detectLineEnding("a\rb"))
    }

    @Test
    fun detectLineEnding_mixedFirstIsLf_returnsLf() {
        assertEquals(LineEnding.LF, TextFileCodec.detectLineEnding("a\nb\r\nc"))
    }

    @Test
    fun detectLineEnding_mixedFirstIsCrlf_returnsCrlf() {
        assertEquals(LineEnding.CRLF, TextFileCodec.detectLineEnding("a\r\nb\nc"))
    }

    @Test
    fun detectLineEnding_noTerminator_defaultsToLf() {
        assertEquals(LineEnding.LF, TextFileCodec.detectLineEnding("single line"))
    }

    @Test
    fun detectLineEnding_emptyText_defaultsToLf() {
        assertEquals(LineEnding.LF, TextFileCodec.detectLineEnding(""))
    }

    // ── 归一 / 还原 ─────────────────────────────────────────────────────────

    @Test
    fun normalizeLineEndings_crlfAndCr_returnsLfOnly() {
        assertEquals("a\nb\nc", TextFileCodec.normalizeLineEndings("a\r\nb\rc"))
    }

    @Test
    fun applyLineEnding_lfTextToCrlf_returnsCrlf() {
        assertEquals("a\r\nb", TextFileCodec.applyLineEnding("a\nb", LineEnding.CRLF))
    }

    @Test
    fun applyLineEnding_calledTwice_isIdempotent() {
        val once = TextFileCodec.applyLineEnding("a\nb", LineEnding.CRLF)
        assertEquals(once, TextFileCodec.applyLineEnding(once, LineEnding.CRLF))
    }

    // ── 解码 ───────────────────────────────────────────────────────────────

    @Test
    fun decode_crlfFile_normalizesTextButKeepsCrlfFormat() {
        val bytes = "line1\r\nline2\r\n".toByteArray(StandardCharsets.UTF_8)

        val decoded = TextFileCodec.decode(bytes)

        assertEquals("line1\nline2\n", decoded.text)
        assertEquals(LineEnding.CRLF, decoded.format.lineEnding)
        assertEquals(StandardCharsets.UTF_8, decoded.format.charset)
        assertFalse(decoded.format.hasBom)
    }

    @Test
    fun decode_utf8Bom_stripsBomAndFlagsIt() {
        val bytes = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + "hi".toByteArray()

        val decoded = TextFileCodec.decode(bytes)

        assertEquals("hi", decoded.text)
        assertTrue(decoded.format.hasBom)
        assertEquals(StandardCharsets.UTF_8, decoded.format.charset)
    }

    @Test
    fun decode_utf16LeBom_decodesUtf16AndFlagsBom() {
        val payload = "héllo".toByteArray(StandardCharsets.UTF_16LE)
        val bytes = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + payload

        val decoded = TextFileCodec.decode(bytes)

        assertEquals("héllo", decoded.text)
        assertEquals(StandardCharsets.UTF_16LE, decoded.format.charset)
        assertTrue(decoded.format.hasBom)
    }

    @Test
    fun decode_utf16BeBom_decodesUtf16Be() {
        val payload = "hi".toByteArray(StandardCharsets.UTF_16BE)
        val bytes = byteArrayOf(0xFE.toByte(), 0xFF.toByte()) + payload

        val decoded = TextFileCodec.decode(bytes)

        assertEquals("hi", decoded.text)
        assertEquals(StandardCharsets.UTF_16BE, decoded.format.charset)
    }

    @Test
    fun decode_invalidUtf8_fallsBackToLatin1WithoutReplacementChars() {
        // 0xFF 不是合法 UTF-8 起始字节；Latin-1 回退保证字节可逆
        val bytes = byteArrayOf(0x61, 0xFF.toByte(), 0x62)

        val decoded = TextFileCodec.decode(bytes)

        assertEquals("a\u00FFb", decoded.text)
        assertEquals(StandardCharsets.ISO_8859_1, decoded.format.charset)
        assertFalse(decoded.text.contains('\uFFFD'))
    }

    @Test
    fun decode_plainAscii_detectsUtf8() {
        val decoded = TextFileCodec.decode("plain".toByteArray(StandardCharsets.UTF_8))

        assertEquals("plain", decoded.text)
        assertEquals(StandardCharsets.UTF_8, decoded.format.charset)
    }

    // ── 编码（保存策略：字节级往返）─────────────────────────────────────────

    @Test
    fun encode_decodeRoundTrip_crlfFile_isByteIdentical() {
        val original = "fn main() {\r\n    println!();\r\n}\r\n".toByteArray(StandardCharsets.UTF_8)

        val decoded = TextFileCodec.decode(original)
        val reEncoded = TextFileCodec.encode(decoded.text, decoded.format)

        assertArrayEquals(original, reEncoded)
    }

    @Test
    fun encode_editedCrlfText_keepsCrlfLineEndings() {
        val decoded = TextFileCodec.decode("a\r\nb\r\n".toByteArray())
        val edited = decoded.text + "c\n"

        val reEncoded = TextFileCodec.encode(edited, decoded.format)

        assertEquals("a\r\nb\r\nc\r\n", reEncoded.toString(StandardCharsets.UTF_8))
    }

    @Test
    fun encode_utf8BomFile_restoresBom() {
        val original = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + "x\n".toByteArray()

        val decoded = TextFileCodec.decode(original)

        assertArrayEquals(original, TextFileCodec.encode(decoded.text, decoded.format))
    }

    @Test
    fun encode_utf16LeBomFile_restoresBomAndEncoding() {
        val original = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + "ab\n".toByteArray(StandardCharsets.UTF_16LE)

        val decoded = TextFileCodec.decode(original)

        assertArrayEquals(original, TextFileCodec.encode(decoded.text, decoded.format))
    }

    @Test
    fun encode_latin1FallbackFile_preservesAllBytes() {
        val original = byteArrayOf(0x61, 0xFF.toByte(), 0x0A, 0x62)

        val decoded = TextFileCodec.decode(original)

        assertArrayEquals(original, TextFileCodec.encode(decoded.text, decoded.format))
    }

    @Test
    fun encode_lfDefaultFormat_doesNotIntroduceCrlfOrBom() {
        val bytes = TextFileCodec.encode("a\nb\n", TextFileFormat.DEFAULT)

        assertEquals("a\nb\n", bytes.toString(StandardCharsets.UTF_8))
        assertFalse(bytes.size >= 3 && bytes[0] == 0xEF.toByte())
    }

    @Test
    fun encode_textAlreadyNormalized_isStableAcrossRepeatedEncoding() {
        val format = TextFileFormat(lineEnding = LineEnding.CRLF)

        val once = TextFileCodec.encode("a\nb", format)
        val twice = TextFileCodec.encode(once.toString(StandardCharsets.UTF_8), format)

        assertArrayEquals(once, twice)
    }
}
