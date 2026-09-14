package com.yumiru11.githubapp.feature.editor

import androidx.lifecycle.SavedStateHandle
import com.yumiru11.githubapp.core.data.model.User
import com.yumiru11.githubapp.core.githubdata.error.GitHubError
import com.yumiru11.githubapp.core.githubdata.error.GitHubRequestException
import com.yumiru11.githubapp.core.testing.MainDispatcherRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * 编辑器 `@mention` 候选 ViewModel 测试（SPEC-3）。
 *
 * 覆盖：owner/repo 导航参数驱动的加载、归一化（去重/排序/丢空白，由
 * `assembleMentionCandidates` 保证）、无仓库语境不请求、读失败降级为空候选。
 */
class EditorMentionsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun editorMentionsViewModel_routeArgsProvided_loadsSortedDedupedCandidates() {
        val repository =
            FakeRepositoryRepository(
                collaborators = listOf(User("Octocat"), User("hubot"), User("octocat"), User("   ")),
            )

        val viewModel =
            EditorMentionsViewModel(
                savedStateHandle = SavedStateHandle(mapOf("owner" to "octocat", "repo" to "Hello-World")),
                repository = repository,
            )

        assertEquals(listOf("hubot", "Octocat"), viewModel.candidates.value)
        assertEquals("octocat", repository.requestedOwner)
        assertEquals("Hello-World", repository.requestedName)
    }

    @Test
    fun editorMentionsViewModel_noRouteArgs_emitsEmptyWithoutRequesting() {
        val repository = FakeRepositoryRepository(collaborators = listOf(User("octocat")))

        val viewModel = EditorMentionsViewModel(SavedStateHandle(), repository)

        assertTrue(viewModel.candidates.value.isEmpty())
        assertNull("无 owner/repo 不应发起网络请求", repository.requestedOwner)
    }

    @Test
    fun editorMentionsViewModel_blankRouteArgs_emitsEmptyWithoutRequesting() {
        val repository = FakeRepositoryRepository(collaborators = listOf(User("octocat")))

        val viewModel =
            EditorMentionsViewModel(
                savedStateHandle = SavedStateHandle(mapOf("owner" to "  ", "repo" to "")),
                repository = repository,
            )

        assertTrue(viewModel.candidates.value.isEmpty())
        assertNull(repository.requestedOwner)
    }

    @Test
    fun editorMentionsViewModel_loadFails_degradesToEmptyCandidates() {
        val repository = FakeRepositoryRepository(failure = GitHubRequestException(GitHubError.Forbidden))

        val viewModel =
            EditorMentionsViewModel(
                savedStateHandle = SavedStateHandle(mapOf("owner" to "octocat", "repo" to "Hello-World")),
                repository = repository,
            )

        assertEquals("octocat", repository.requestedOwner)
        assertTrue("读失败只应没有候选，不抛异常", viewModel.candidates.value.isEmpty())
    }
}
