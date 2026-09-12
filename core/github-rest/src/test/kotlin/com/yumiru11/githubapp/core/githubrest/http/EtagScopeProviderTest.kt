package com.yumiru11.githubapp.core.githubrest.http

import com.yumiru11.githubapp.core.githubrest.auth.TokenProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * ETag 账号作用域派生测试（安全约束：一个账号不得读到另一个账号的缓存分区）。
 */
class EtagScopeProviderTest {
    @Test
    fun currentScope_guestProvider_isAnonymous() {
        assertEquals(EtagScopeProvider.ANONYMOUS_SCOPE, GuestEtagScopeProvider.currentScope())
    }

    @Test
    fun currentScope_noToken_isAnonymous() {
        val provider = TokenEtagScopeProvider(TokenProvider { null })

        assertEquals(EtagScopeProvider.ANONYMOUS_SCOPE, provider.currentScope())
    }

    @Test
    fun currentScope_blankToken_isAnonymous() {
        val provider = TokenEtagScopeProvider(TokenProvider { "" })

        assertEquals(EtagScopeProvider.ANONYMOUS_SCOPE, provider.currentScope())
    }

    @Test
    fun currentScope_sameToken_isStable() {
        val provider = TokenEtagScopeProvider(TokenProvider { "gho_same" })

        assertEquals(provider.currentScope(), provider.currentScope())
    }

    @Test
    fun currentScope_differentTokens_produceDifferentScopes() {
        val accountA = TokenEtagScopeProvider(TokenProvider { "gho_account_a" })
        val accountB = TokenEtagScopeProvider(TokenProvider { "gho_account_b" })

        assertNotEquals(accountA.currentScope(), accountB.currentScope())
    }

    @Test
    fun currentScope_neverExposesRawToken() {
        val token = "gho_secret_token"
        val scope = TokenEtagScopeProvider(TokenProvider { token }).currentScope()

        assertNotEquals(token, scope)
        assertFalse("作用域不得包含原文 token", scope.contains(token))
    }

    @Test
    fun sha256Hex_knownVector_matchesExpected() {
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            sha256Hex("abc"),
        )
    }
}
