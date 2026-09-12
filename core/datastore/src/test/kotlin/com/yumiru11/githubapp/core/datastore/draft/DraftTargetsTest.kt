package com.yumiru11.githubapp.core.datastore.draft

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * [DraftTargets] / [DraftKey] 键规则测试。
 *
 * 两条契约：**稳定**（同一目标反复算键必须相同，否则草稿永远恢复不出来）与
 * **唯一**（不同目标必须不同键，否则草稿互相覆盖）。
 */
class DraftTargetsTest {
    @Test
    fun sameTarget_repeatedCalls_produceSameKey() {
        val first = DraftTargets.issueComment("octocat", "Hello-World", 42)
        val second = DraftTargets.issueComment("octocat", "Hello-World", 42)

        assertEquals(first, second)
    }

    @Test
    fun fileEdit_differentRef_producesDifferentKeys() {
        val onMain = DraftTargets.fileEdit("octocat", "Hello-World", "main", "README.md")
        val onFeature = DraftTargets.fileEdit("octocat", "Hello-World", "feature/x", "README.md")

        assertNotEquals(onMain, onFeature)
    }

    @Test
    fun fileEdit_pathWithSlash_doesNotCollideWithRefSegment() {
        // 键不可反解，只需保证不同目标 → 不同键：段内 '/' 不影响唯一性
        val a = DraftTargets.fileEdit("o", "r", "feature", "x/y.md")
        val b = DraftTargets.fileEdit("o", "r", "feature/x", "y.md")

        assertNotEquals(a, b)
    }

    @Test
    fun newIssue_and_editIssue_produceDifferentKeys() {
        val create = DraftTargets.newIssue("octocat", "Hello-World")
        val edit = DraftTargets.issueEdit("octocat", "Hello-World", 42)

        assertNotEquals(create, edit)
    }

    @Test
    fun issueComment_and_pullRequestForm_produceDifferentKeys() {
        // 前缀隔离：评论草稿不能落进新建 PR 表单的槽
        val comment = DraftTargets.issueComment("octocat", "Hello-World", 7)
        val pullForm = DraftTargets.newPullRequest("octocat", "Hello-World")

        assertNotEquals(comment, pullForm)
    }

    @Test
    fun commentEdit_and_issueComment_sameNumber_produceDifferentKeys() {
        val timeline = DraftTargets.issueComment("o", "r", 7)
        val editing = DraftTargets.commentEdit("o", "r", 7L)

        assertNotEquals(timeline, editing)
    }

    @Test
    fun lineComment_differentAnchor_producesDifferentKeys() {
        val line10 = DraftTargets.lineComment("o", "r", 1, "src/a.kt", "RIGHT", 10)
        val line20 = DraftTargets.lineComment("o", "r", 1, "src/a.kt", "RIGHT", 20)
        val left10 = DraftTargets.lineComment("o", "r", 1, "src/a.kt", "LEFT", 10)

        assertNotEquals(line10, line20)
        assertNotEquals(line10, left10)
    }

    @Test
    fun allTargets_forSameRepo_produceDistinctKeys() {
        val keys =
            listOf(
                DraftTargets.fileEdit("o", "r", "main", "a.md"),
                DraftTargets.newFile("o", "r", "main"),
                DraftTargets.newIssue("o", "r"),
                DraftTargets.newPullRequest("o", "r"),
                DraftTargets.issueComment("o", "r", 1),
                DraftTargets.commentEdit("o", "r", 1L),
                DraftTargets.issueEdit("o", "r", 1),
                DraftTargets.pullEdit("o", "r", 1),
                DraftTargets.pullReview("o", "r", 1),
                DraftTargets.lineComment("o", "r", 1, "a.kt", "RIGHT", 1),
            )

        assertEquals("键空间必须两两不同", keys.size, keys.toSet().size)
    }

    @Test
    fun draftKey_blank_rejectedAtConstruction() {
        assertThrows(IllegalArgumentException::class.java) { DraftKey("") }
        assertThrows(IllegalArgumentException::class.java) { DraftKey("   ") }
    }
}
