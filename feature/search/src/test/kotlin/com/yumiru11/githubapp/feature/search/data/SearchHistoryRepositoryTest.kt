package com.yumiru11.githubapp.feature.search.data

import com.yumiru11.githubapp.core.database.dao.SearchHistoryDao
import com.yumiru11.githubapp.core.database.entity.SearchHistoryEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * SearchHistoryRepository 单测（MockK 桩 DAO）。
 *
 * 覆盖：recent 委托 DAO、add 构造实体（query + 时间戳）upsert、clear 委托 DAO。
 */
class SearchHistoryRepositoryTest {
    private val dao = mockk<SearchHistoryDao>()
    private val repository = SearchHistoryRepository(dao)

    @Test
    fun recent_delegatesToDaoWithLimit() =
        runTest {
            coEvery { dao.getRecent(20) } returns listOf("kotlin", "jetpack")

            val result = repository.recent()

            assertEquals(listOf("kotlin", "jetpack"), result)
            coVerify { dao.getRecent(20) }
        }

    @Test
    fun add_upsertsEntityWithQueryAndTimestamp() =
        runTest {
            coEvery { dao.upsert(any()) } returns Unit

            repository.add("kotlin")

            coVerify {
                dao.upsert(
                    match<SearchHistoryEntity> {
                        it.query == "kotlin" && it.updatedAt > 0L
                    },
                )
            }
        }

    @Test
    fun clear_delegatesToDao() =
        runTest {
            coEvery { dao.clear() } returns Unit

            repository.clear()

            coVerify { dao.clear() }
        }
}
