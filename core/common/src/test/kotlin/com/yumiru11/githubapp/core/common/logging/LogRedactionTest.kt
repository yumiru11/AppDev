package com.yumiru11.githubapp.core.common.logging

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 日志脱敏规则矩阵（#169 / L14 的验收断言）。
 *
 * 判定口径：**宁可多掩，不可漏掩**——多掩只损失排查细节，漏掩是不可逆的凭据泄露。
 */
class LogRedactionTest {
    @Test
    fun redact_classicPat_maskTokenKeepsPrefix() {
        val raw = "request header Authorization: Bearer ghp_ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"

        val redacted = LogRedaction.redact(raw)

        assertFalse(redacted.contains("ghp_ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"))
        assertTrue(redacted.contains(LogRedaction.MASK))
        // 非机密上下文保留，便于定位是哪条日志
        assertTrue(redacted.contains("Authorization"))
    }

    @Test
    fun redact_fineGrainedPat_masksWholeToken() {
        val raw = "token=github_pat_11ABCDEFG0abcdefghijklmnop_QRSTUVWXYZ0123456789abcdefghijklmnopqrstuvwxyz"

        val redacted = LogRedaction.redact(raw)

        assertFalse(redacted.contains("github_pat_11ABCDEFG"))
        assertTrue(redacted.contains("token="))
    }

    @Test
    fun redact_oauthTokenLiterals_maskEveryPrefixVariant() {
        val raw = "gho_ABCDEFGHIJKLMNOPQRSTUVWXYZ012345 ghu_ABCDEFGHIJKLMNOPQRSTUVWXYZ012345"

        val redacted = LogRedaction.redact(raw)

        assertFalse(redacted.contains("gho_ABCDEFGHIJKLMNOPQRSTUVWXYZ012345"))
        assertFalse(redacted.contains("ghu_ABCDEFGHIJKLMNOPQRSTUVWXYZ012345"))
    }

    @Test
    fun redact_queryParameters_maskAccessTokenInUrl() {
        val raw = "GET https://api.github.com/x?access_token=secretvalue123&page=2"

        val redacted = LogRedaction.redact(raw)

        assertFalse(redacted.contains("secretvalue123"))
        assertTrue(redacted.contains("page=2"))
    }

    @Test
    fun redact_jsonStyleSecrets_maskValuesKeepKeys() {
        val raw = """{"access_token":"abc123def456","client_secret":"s3cr3t-value","login":"octocat"}"""

        val redacted = LogRedaction.redact(raw)

        assertFalse(redacted.contains("abc123def456"))
        assertFalse(redacted.contains("s3cr3t-value"))
        assertTrue(redacted.contains("access_token"))
        assertTrue(redacted.contains("octocat"))
    }

    @Test
    fun redact_tokenEndpointBody_masksCodeVerifierAndCode() {
        val raw = "code=abcdef123456&code_verifier=verifier-value-1234567890&grant_type=authorization_code"

        val redacted = LogRedaction.redact(raw)

        assertFalse(redacted.contains("abcdef123456"))
        assertFalse(redacted.contains("verifier-value-1234567890"))
        assertTrue(redacted.contains("grant_type=authorization_code"))
    }

    @Test
    fun redact_plainLogMessage_returnsUnchanged() {
        val raw = "Loaded 42 repositories in 312ms for user octocat"

        assertEquals(raw, LogRedaction.redact(raw))
    }

    @Test
    fun redact_alreadyMaskedMessage_doesNotDoubleMask() {
        val raw = "Authorization: ${LogRedaction.MASK}"

        val redacted = LogRedaction.redact(raw)

        assertEquals(raw, redacted)
    }

    @Test
    fun redact_multipleSecretsInOneLine_masksAllOccurrences() {
        val raw = "a=ghp_AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA b=ghp_BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB"

        val redacted = LogRedaction.redact(raw)

        assertFalse(redacted.contains("ghp_AAAA"))
        assertFalse(redacted.contains("ghp_BBBB"))
        assertEquals(2, Regex(Regex.escape(LogRedaction.MASK)).findAll(redacted).count())
    }
}
