package com.yumiru11.githubapp.core.datastore.draft

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.IOException

/**
 * [DraftText] 三重恢复守卫 + 写侧语义测试。
 *
 * 守卫：与基线相同不恢复、用户已输入不覆盖、读失败静默保持基线。
 * 写侧：防抖落盘、回到基线清草稿、[DraftText.saveNow] 立即落盘、[DraftText.discard] 复位且删除。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DraftTextTest {
    private val key = DraftTargets.issueComment("octocat", "Hello-World", 3)

    private fun saver(
        repository: DraftRepository,
        scope: CoroutineScope,
    ) = DraftAutoSaver(repository, scope, debounceMillis = DEBOUNCE)

    @Test
    fun init_draftDiffersFromBaseline_restoresDraft() =
        runTest {
            val repository = FakeDraftRepository()
            repository.storedDrafts[key] = "recovered"
            val draft = DraftText(key, baseline = "", saver = saver(repository, backgroundScope), scope = backgroundScope)

            runCurrent()

            assertEquals("recovered", draft.text.value)
        }

    @Test
    fun init_draftEqualsBaseline_keepsBaseline() =
        runTest {
            val repository = FakeDraftRepository()
            repository.storedDrafts[key] = "same"
            val draft = DraftText(key, baseline = "same", saver = saver(repository, backgroundScope), scope = backgroundScope)

            runCurrent()

            assertEquals("same", draft.text.value)
        }

    @Test
    fun init_userTypedBeforeLoad_doesNotClobberInput() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            val repository = FakeDraftRepository(loadGate = gate)
            repository.storedDrafts[key] = "from-disk"
            val draft = DraftText(key, baseline = "", saver = saver(repository, backgroundScope), scope = backgroundScope)

            draft.onChanged("user-typed")
            gate.complete(Unit)
            runCurrent()

            assertEquals("user-typed", draft.text.value)
        }

    @Test
    fun init_loadThrowsIOException_keepsBaselineSilently() =
        runTest {
            val repository = FakeDraftRepository(loadFailure = IOException("corrupt"))
            val draft = DraftText(key, baseline = "base", saver = saver(repository, backgroundScope), scope = backgroundScope)

            runCurrent()

            assertEquals("base", draft.text.value)
        }

    @Test
    fun onChanged_nonBaseline_schedulesDebouncedSave() =
        runTest {
            val repository = FakeDraftRepository()
            val draft = DraftText(key, baseline = "", saver = saver(repository, backgroundScope), scope = backgroundScope)

            draft.onChanged("hello")
            advanceTimeBy(DEBOUNCE * 2)
            runCurrent()

            assertEquals("hello", repository.storedDrafts[key])
        }

    @Test
    fun onChanged_backToBaseline_discardsStoredDraft() =
        runTest {
            val repository = FakeDraftRepository()
            repository.storedDrafts[key] = "leftover"
            val draft = DraftText(key, baseline = "", saver = saver(repository, backgroundScope), scope = backgroundScope)
            runCurrent()

            draft.onChanged("")

            runCurrent()

            assertNull(repository.storedDrafts[key])
            assertEquals("", draft.text.value)
        }

    @Test
    fun saveNow_nonBaseline_writesImmediately() =
        runTest {
            val repository = FakeDraftRepository()
            val draft = DraftText(key, baseline = "", saver = saver(repository, backgroundScope), scope = backgroundScope)

            draft.onChanged("in-progress")
            draft.saveNow()
            runCurrent()

            assertEquals("in-progress", repository.storedDrafts[key])
        }

    @Test
    fun saveNow_atBaseline_writesNothing() =
        runTest {
            val repository = FakeDraftRepository()
            val draft = DraftText(key, baseline = "base", saver = saver(repository, backgroundScope), scope = backgroundScope)

            draft.saveNow()
            runCurrent()

            assertNull(repository.storedDrafts[key])
        }

    @Test
    fun discard_clearsStoredDraftAndResetsTextToBaseline() =
        runTest {
            val repository = FakeDraftRepository()
            repository.storedDrafts[key] = "restored"
            val draft = DraftText(key, baseline = "base", saver = saver(repository, backgroundScope), scope = backgroundScope)
            runCurrent()
            assertEquals("restored", draft.text.value)

            draft.discard()
            runCurrent()

            assertNull(repository.storedDrafts[key])
            assertEquals("base", draft.text.value)
        }

    private companion object {
        const val DEBOUNCE = 800L
    }
}
