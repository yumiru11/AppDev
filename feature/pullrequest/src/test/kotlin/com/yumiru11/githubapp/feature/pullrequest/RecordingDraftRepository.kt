package com.yumiru11.githubapp.feature.pullrequest

import com.yumiru11.githubapp.core.datastore.draft.DraftAutoSaver
import com.yumiru11.githubapp.core.datastore.draft.DraftKey
import com.yumiru11.githubapp.core.datastore.draft.DraftRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import java.io.IOException

/** 内存 [DraftRepository]（草稿相关 ViewModel 单测夹具；可注入读失败）。 */
internal class RecordingDraftRepository(
    private val loadFailure: IOException? = null,
) : DraftRepository {
    val drafts = mutableMapOf<DraftKey, String>()

    override suspend fun load(key: DraftKey): String? {
        loadFailure?.let { throw it }
        return drafts[key]
    }

    override suspend fun save(
        key: DraftKey,
        content: String,
    ) {
        if (content.isBlank()) drafts.remove(key) else drafts[key] = content
    }

    override suspend fun clear(key: DraftKey) {
        drafts.remove(key)
    }
}

/** 即时落盘的 [DraftAutoSaver]（防抖 0，Unconfined；单测里同步可断言）。 */
internal fun draftSaver(
    repository: DraftRepository,
    debounceMillis: Long = 0,
) = DraftAutoSaver(repository, CoroutineScope(UnconfinedTestDispatcher()), debounceMillis)
