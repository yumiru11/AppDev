package com.yumiru11.githubapp.core.datastore.draft

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

/**
 * [DefaultDraftRepository] 存储契约测试（临时文件 PreferenceDataStoreFactory，同
 * `UserPreferencesRepositoryTest` 夹具）。
 *
 * 契约：空内容 = 无草稿、超长不落盘、[DefaultDraftRepository.clear] 幂等、超出条数上限按最旧淘汰。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DefaultDraftRepositoryTest {
    private val key = DraftTargets.issueComment("octocat", "Hello-World", 1)

    @Test
    fun load_noDraft_returnsNull() =
        runTest {
            val repository = createRepository()

            assertNull(repository.load(key))
        }

    @Test
    fun save_thenLoad_returnsContent() =
        runTest {
            val repository = createRepository()

            repository.save(key, "hello draft")

            assertEquals("hello draft", repository.load(key))
        }

    @Test
    fun save_blankContent_clearsExistingDraft() =
        runTest {
            val repository = createRepository()
            repository.save(key, "hello")

            repository.save(key, "   ")

            assertNull(repository.load(key))
        }

    @Test
    fun save_oversizedContent_clearsExistingDraft() =
        runTest {
            val repository = createRepository()
            repository.save(key, "hello")
            val oversized = "x".repeat(DefaultDraftRepository.MAX_DRAFT_CHARS + 1)

            repository.save(key, oversized)

            assertNull("超长内容不落盘（宁缺勿截断）", repository.load(key))
        }

    @Test
    fun clear_existingDraft_removesIt() =
        runTest {
            val repository = createRepository()
            repository.save(key, "hello")

            repository.clear(key)

            assertNull(repository.load(key))
        }

    @Test
    fun clear_missingDraft_isNoOp() =
        runTest {
            val repository = createRepository()

            repository.clear(key)

            assertNull(repository.load(key))
        }

    @Test
    fun save_exceedsMaxDrafts_evictsOldest() =
        runTest {
            val repository = createRepository()
            val keys = (0 until DefaultDraftRepository.MAX_DRAFT_COUNT + 1).map { DraftTargets.issueComment("o", "r", it) }
            keys.forEach { k ->
                repository.save(k, "body-${k.value}")
                // Preferences 写入时间取 wall clock：sleep 拉开毫秒，保证淘汰顺序确定
                Thread.sleep(2)
            }

            assertNull("最旧一条被淘汰", repository.load(keys.first()))
            assertEquals("body-${keys.last().value}", repository.load(keys.last()))
        }

    @Test
    fun save_sameKeyTwice_updatesContentWithoutEviction() =
        runTest {
            val repository = createRepository()

            repository.save(key, "first")
            Thread.sleep(2)
            repository.save(key, "second")

            assertEquals("second", repository.load(key))
        }

    private fun createRepository(): DefaultDraftRepository {
        val scope = CoroutineScope(UnconfinedTestDispatcher())
        val file = newPreferencesFile()
        val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })
        return DefaultDraftRepository(dataStore)
    }

    private fun newPreferencesFile(): File {
        val file = File.createTempFile("draft-test", ".preferences_pb")
        file.deleteOnExit()
        return file
    }
}
