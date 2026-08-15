package com.yumiru11.githubapp.core.markdown.native

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GitHubAlertParserTest {
    @Test
    fun parse_noteAlert_extractsTypeAndBody() {
        val parsed = GitHubAlertParser.parse("> [!NOTE]\n> Useful **info**.")

        assertEquals(GitHubAlertType.NOTE, parsed?.type)
        assertEquals("Useful **info**.", parsed?.body)
    }

    @Test
    fun parse_warningAlert_extractsTypeAndBody() {
        val parsed = GitHubAlertParser.parse("> [!WARNING]\n> Watch out.")

        assertEquals(GitHubAlertType.WARNING, parsed?.type)
        assertEquals("Watch out.", parsed?.body)
    }

    @Test
    fun parse_ordinaryQuote_returnsNull() {
        assertNull(GitHubAlertParser.parse("> ordinary quote"))
    }
}
