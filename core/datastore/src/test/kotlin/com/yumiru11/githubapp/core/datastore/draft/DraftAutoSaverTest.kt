package com.yumiru11.githubapp.core.datastore.draft

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.IOException

/**
 * [DraftAutoSaver] 时序测试（虚拟时间断言防抖/取消）。
 *
 * 三条契约：防抖只落最后一次、[DraftAutoSaver.saveNow] 立刻落盘并取消待写、
 * [DraftAutoSaver.discard] 先取消再删（否则"丢弃后防抖又写回来"）。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DraftAutoSaverTest {
    private val key = DraftTargets.issueComment("octocat", "Hello-World", 1)

    @Test
    fun scheduleSave_rapidChanges_writesOnlyLastAfterDebounce() =
        runTest {
            val repository = FakeDraftRepository()
            val saver = DraftAutoSaver(repository, backgroundScope, debounceMillis = DEBOUNCE)

            saver.scheduleSave(key, "a")
            saver.scheduleSave(key, "ab")
            saver.scheduleSave(key, "abc")
            advanceTimeBy(DEBOUNCE - 1)
            runCurrent()

            assertNull("防抖窗口内不落盘", repository.storedDrafts[key])

            advanceTimeBy(2)
            runCurrent()

            assertEquals("abc", repository.storedDrafts[key])
            assertEquals("三次输入只写一次", 1, repository.writes)
        }

    @Test
    fun saveNow_withPendingDebounce_cancelsPendingAndWritesImmediately() =
        runTest {
            val repository = FakeDraftRepository()
            val saver = DraftAutoSaver(repository, backgroundScope, debounceMillis = DEBOUNCE)

            saver.scheduleSave(key, "old")
            saver.saveNow(key, "new")
            runCurrent()

            assertEquals("new", repository.storedDrafts[key])

            advanceTimeBy(DEBOUNCE * 2)
            runCurrent()

            assertEquals("待写任务被取消，旧内容不得回写", "new", repository.storedDrafts[key])
        }

    @Test
    fun discard_withPendingDebounce_cancelsPendingAndPreventsResurrection() =
        runTest {
            val repository = FakeDraftRepository()
            val saver = DraftAutoSaver(repository, backgroundScope, debounceMillis = DEBOUNCE)
            repository.storedDrafts[key] = "existing"

            saver.scheduleSave(key, "typed")
            saver.discard(key)
            runCurrent()

            assertNull(repository.storedDrafts[key])

            advanceTimeBy(DEBOUNCE * 2)
            runCurrent()

            assertNull("已丢弃的草稿不得被防抖写复活", repository.storedDrafts[key])
        }

    @Test
    fun load_ioFailure_returnsNull() =
        runTest {
            val repository = FakeDraftRepository(loadFailure = IOException("corrupt"))
            val saver = DraftAutoSaver(repository, backgroundScope, debounceMillis = DEBOUNCE)

            assertNull(saver.load(key))
        }

    @Test
    fun scheduleSave_ioFailure_isSilentlyDropped() =
        runTest {
            val repository = FakeDraftRepository(saveFailure = IOException("disk full"))
            val saver = DraftAutoSaver(repository, backgroundScope, debounceMillis = DEBOUNCE)

            saver.scheduleSave(key, "text")
            advanceTimeBy(DEBOUNCE * 2)
            runCurrent()

            assertNull(repository.storedDrafts[key])
        }

    private companion object {
        const val DEBOUNCE = 800L
    }
}

/** 内存 [DraftRepository]（可控 load 门闩 / IO 失败注入，用于时序断言）。 */
internal class FakeDraftRepository(
    private val loadFailure: IOException? = null,
    private val loadGate: CompletableDeferred<Unit>? = null,
    private val saveFailure: IOException? = null,
) : DraftRepository {
    val storedDrafts = mutableMapOf<DraftKey, String>()
    var writes = 0

    override suspend fun load(key: DraftKey): String? {
        loadGate?.await()
        loadFailure?.let { throw it }
        return storedDrafts[key]
    }

    override suspend fun save(
        key: DraftKey,
        content: String,
    ) {
        saveFailure?.let { throw it }
        writes++
        if (content.isBlank()) storedDrafts.remove(key) else storedDrafts[key] = content
    }

    override suspend fun clear(key: DraftKey) {
        storedDrafts.remove(key)
    }
}
