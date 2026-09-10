package com.yumiru11.githubapp.feature.repo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [RepoNameValidator] 纯函数矩阵测试（L04）。
 *
 * 规则逐条覆盖：空/超长/非法字符/纯点/.git 结尾/下划线结尾，外加合法边界（数字、连字符、
 * 单字符、100 字符上限、大写）与"看起来像但合法"的反例（"a.gitignore"、"my_repo"、".hidden"）。
 */
class RepoNameValidatorTest {
    @Test
    fun error_blankName_returnsEmpty() {
        assertEquals(RepoNameError.EMPTY, RepoNameValidator.error(""))
        assertEquals(RepoNameError.EMPTY, RepoNameValidator.error("   "))
        assertEquals(RepoNameError.EMPTY, RepoNameValidator.error("\t"))
    }

    @Test
    fun error_overHundredChars_returnsTooLong() {
        val name = "a".repeat(RepoNameValidator.MAX_LENGTH + 1)

        assertEquals(RepoNameError.TOO_LONG, RepoNameValidator.error(name))
    }

    @Test
    fun error_exactlyHundredChars_isValid() {
        val name = "a".repeat(RepoNameValidator.MAX_LENGTH)

        assertNull(RepoNameValidator.error(name))
        assertTrue(RepoNameValidator.isValid(name))
    }

    @Test
    fun error_illegalCharacters_returnsInvalidChars() {
        listOf("my repo", "repo/name", "repo@name", "仓库", "repo!", "#repo", "repo name").forEach { name ->
            assertEquals("应判非法字符：$name", RepoNameError.INVALID_CHARS, RepoNameValidator.error(name))
        }
    }

    @Test
    fun error_onlyDots_returnsOnlyDots() {
        listOf(".", "..", "...").forEach { name ->
            assertEquals("应判纯点：$name", RepoNameError.ONLY_DOTS, RepoNameValidator.error(name))
        }
    }

    @Test
    fun error_gitSuffix_returnsGitSuffix() {
        listOf("repo.git", "repo.GIT", "my.git").forEach { name ->
            assertEquals("应判 .git 结尾：$name", RepoNameError.GIT_SUFFIX, RepoNameValidator.error(name))
        }
    }

    @Test
    fun error_trailingUnderscore_returnsTrailingUnderscore() {
        assertEquals(RepoNameError.TRAILING_UNDERSCORE, RepoNameValidator.error("my_repo_"))
    }

    @Test
    fun error_validNames_returnsNull() {
        listOf(
            "hello-world",
            "Hello_World",
            "repo123",
            "a",
            ".hidden",
            "a.gitignore",
            "my_repo",
            "dot.separated.name",
            "-leading-hyphen",
        ).forEach { name ->
            assertNull("应判合法：$name", RepoNameValidator.error(name))
        }
    }

    @Test
    fun isValid_mirrorsErrorResult() {
        assertTrue(RepoNameValidator.isValid("hello-world"))
        assertFalse(RepoNameValidator.isValid("hello world"))
        assertFalse(RepoNameValidator.isValid(""))
    }
}
