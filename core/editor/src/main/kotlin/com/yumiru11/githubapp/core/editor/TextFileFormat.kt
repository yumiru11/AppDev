package com.yumiru11.githubapp.core.editor

import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

/**
 * 行尾风格（EDITOR-1「显式 CRLF/编码策略」的行尾维度）。
 *
 * [sequence] 是被探测文本里的字面序列，也是保存时还原用的替换目标（[TextFileCodec]）。
 */
enum class LineEnding(
    val sequence: String,
) {
    LF("\n"),
    CRLF("\r\n"),
    CR("\r"),
}

/**
 * 文本文件格式快照：行尾 + 字符集 + 是否带 BOM（EDITOR-1）。
 *
 * ### 策略（显式声明，PR body 同步）
 * 1. **打开时探测**：[TextFileCodec.decode] 先按 BOM 判定字符集（UTF-8 / UTF-16LE / UTF-16BE），
 *    无 BOM 时按 UTF-8 严格解码，失败回退 ISO-8859-1（Latin-1 对任意字节可逆，避免把
 *    非 UTF-8 文件解成 U+FFFD 后保存造成**不可逆的数据损坏**）；行尾取**首个行终止符**的风格。
 * 2. **编辑器内部一律 LF**：解码后立即归一为 LF（Sora 的 `Content` 以 `\n` 分行，CRLF 无法在
 *    撤销栈/选区里稳定保真），所以「原样传给编辑器」并不能保证原样写回。
 * 3. **保存时按快照还原**：`TextFileCodec.encode` 把 LF 还原为探测到的行尾、按探测到的字符集
 *    编码、按需补回 BOM —— **已存在的 CRLF 文件绝不会被静默转成 LF**。
 * 4. 新建文件 / 探测不到的默认格式 = [DEFAULT]（UTF-8 + LF + 无 BOM）。
 */
data class TextFileFormat(
    val lineEnding: LineEnding = LineEnding.LF,
    val charset: Charset = StandardCharsets.UTF_8,
    val hasBom: Boolean = false,
) {
    companion object {
        /** 默认格式（UTF-8 + LF + 无 BOM）：新建文件与无法探测时的口径。 */
        val DEFAULT: TextFileFormat = TextFileFormat()
    }
}

/**
 * 解码结果：编辑器用的 LF 文本 + 原始格式快照（保存时按快照还原）。
 *
 * @param text 已归一为 LF 的文本（可直接交给 Sora）
 * @param format 原始格式（保存时还原行尾/字符集/BOM）
 */
data class DecodedTextFile(
    val text: String,
    val format: TextFileFormat,
)

/**
 * 文本文件格式探测与还原（EDITOR-1；**纯 JVM 逻辑**，无 Android 类型，可单测）。
 *
 * 探测/还原语义见 [TextFileFormat] 的策略说明；本对象只做字节 ↔ 字符串的确定性转换。
 */
object TextFileCodec {
    /**
     * 解码字节：探测 BOM/字符集/行尾 → 返回 LF 文本 + 格式快照。
     *
     * 无 BOM 且不是合法 UTF-8 时回退 [StandardCharsets.ISO_8859_1]（逐字节映射、可逆），
     * 不会产生 U+FFFD；这样保存时能把原字节写回（用户不改内容则字节级不变）。
     */
    fun decode(bytes: ByteArray): DecodedTextFile {
        val bom = detectBom(bytes)
        val charset = bom?.charset ?: sniffCharset(bytes)
        val body = if (bom == null) bytes else bytes.copyOfRange(bom.length, bytes.size)
        val raw = body.toString(charset)
        return DecodedTextFile(
            text = normalizeLineEndings(raw),
            format =
                TextFileFormat(
                    lineEnding = detectLineEnding(raw),
                    charset = charset,
                    hasBom = bom != null,
                ),
        )
    }

    /**
     * 编码文本：归一 LF → 还原探测到的行尾 → 按格式字符集编码 → 按需补 BOM。
     *
     * 字符集不可映射的字符按 JDK 默认替换策略处理（'?'）；编码异常不做静默降级尝试
     * （由调用方兜底，避免「保存成功但内容被换掉」的隐性损坏）。
     */
    fun encode(
        text: String,
        format: TextFileFormat,
    ): ByteArray {
        val body = applyLineEnding(normalizeLineEndings(text), format.lineEnding).toByteArray(format.charset)
        val bom = if (format.hasBom) bomBytesFor(format.charset) else null
        return if (bom == null) body else bom + body
    }

    /** 首个行终止符的风格；全文无行终止符时取 LF。 */
    fun detectLineEnding(text: String): LineEnding {
        val crlf = text.indexOf("\r\n")
        val lf = text.indexOf('\n')
        val cr = text.indexOf('\r')
        return when {
            crlf >= 0 && (lf < 0 || crlf <= lf) && (cr < 0 || crlf <= cr) -> LineEnding.CRLF
            lf >= 0 && (cr < 0 || lf < cr) -> LineEnding.LF
            cr >= 0 -> LineEnding.CR
            else -> LineEnding.LF
        }
    }

    /** 任意行尾 → LF（CRLF/CR 都归一）。 */
    fun normalizeLineEndings(text: String): String = text.replace("\r\n", "\n").replace('\r', '\n')

    /** 任意行尾文本 → 目标行尾（先归一，重复调用幂等）。 */
    fun applyLineEnding(
        text: String,
        lineEnding: LineEnding,
    ): String {
        val lf = normalizeLineEndings(text)
        if (lineEnding == LineEnding.LF) return lf
        return lf.replace("\n", lineEnding.sequence)
    }

    /** BOM 探测结果（字符集 + 字节长度）。 */
    private data class Bom(
        val charset: Charset,
        val length: Int,
    )

    private fun detectBom(bytes: ByteArray): Bom? =
        when {
            bytes.startsWith(BOM_UTF8) -> Bom(StandardCharsets.UTF_8, BOM_UTF8.size)
            bytes.startsWith(BOM_UTF16_LE) -> Bom(StandardCharsets.UTF_16LE, BOM_UTF16_LE.size)
            bytes.startsWith(BOM_UTF16_BE) -> Bom(StandardCharsets.UTF_16BE, BOM_UTF16_BE.size)
            else -> null
        }

    private fun bomBytesFor(charset: Charset): ByteArray? =
        when (charset) {
            StandardCharsets.UTF_8 -> BOM_UTF8
            StandardCharsets.UTF_16LE -> BOM_UTF16_LE
            StandardCharsets.UTF_16BE -> BOM_UTF16_BE
            else -> null
        }

    /** 无 BOM 时的字符集探测：合法 UTF-8 → UTF-8；否则 ISO-8859-1（字节可逆回退）。 */
    private fun sniffCharset(bytes: ByteArray): Charset = if (isValidUtf8(bytes)) StandardCharsets.UTF_8 else StandardCharsets.ISO_8859_1

    private fun isValidUtf8(bytes: ByteArray): Boolean =
        runCatching {
            StandardCharsets.UTF_8
                .newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
        }.isSuccess

    private fun ByteArray.startsWith(prefix: ByteArray): Boolean {
        if (size < prefix.size) return false
        return prefix.indices.all { this[it] == prefix[it] }
    }

    private val BOM_UTF8 = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
    private val BOM_UTF16_LE = byteArrayOf(0xFF.toByte(), 0xFE.toByte())
    private val BOM_UTF16_BE = byteArrayOf(0xFE.toByte(), 0xFF.toByte())
}
