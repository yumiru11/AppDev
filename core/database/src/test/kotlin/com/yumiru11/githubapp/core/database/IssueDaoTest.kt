package com.yumiru11.githubapp.core.database

import androidx.paging.PagingSource
import androidx.paging.PagingSource.LoadResult
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.yumiru11.githubapp.core.database.dao.IssueDao
import com.yumiru11.githubapp.core.database.entity.IssueEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * IssueDao 读写测试（Robolectric + inMemoryDatabaseBuilder，issue #165 / L07）。
 *
 * 覆盖：upsert 覆盖同主键、replaceByPage 整页替换（旧行不残留）、replaceAll 清空后重写、
 * clearByRepo 跨过滤态清理、maxPage/countByFilter、pagingSource 按 (page, position) 重放。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class IssueDaoTest {
    private lateinit var db: AppDatabase
    private lateinit var dao: IssueDao

    @Before
    fun setUp() {
        db =
            Room
                .inMemoryDatabaseBuilder(
                    ApplicationProvider.getApplicationContext(),
                    AppDatabase::class.java,
                ).allowMainThreadQueries()
                .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4)
                .build()
        dao = db.issueDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun upsert_samePrimaryKey_overwritesWithoutDuplicating() =
        runTest {
            dao.upsert(listOf(issue(id = 1, title = "old")))
            dao.upsert(listOf(issue(id = 1, title = "new")))

            val rows = dao.pagingSource(OWNER, REPO, FILTER).loadAll()
            assertEquals(1, rows.size)
            assertEquals("new", rows.single().title)
        }

    @Test
    fun upsertList_insertsAllRowsInOrder() =
        runTest {
            dao.upsert(listOf(issue(id = 1, position = 0), issue(id = 2, position = 1)))

            val rows = dao.pagingSource(OWNER, REPO, FILTER).loadAll()

            assertEquals(listOf(1L, 2L), rows.map { it.issueId })
        }

    @Test
    fun replaceByPage_staleRowsOfSamePage_areRemoved() =
        runTest {
            dao.upsert(listOf(issue(id = 1, page = 1), issue(id = 2, page = 1)))

            dao.replaceByPage(OWNER, REPO, FILTER, page = 1, entities = listOf(issue(id = 1, page = 1)))

            assertEquals(listOf(1L), dao.pagingSource(OWNER, REPO, FILTER).loadAll().map { it.issueId })
        }

    @Test
    fun replaceByPage_otherPages_arePreserved() =
        runTest {
            dao.upsert(listOf(issue(id = 1, page = 1), issue(id = 2, page = 2)))

            dao.replaceByPage(OWNER, REPO, FILTER, page = 2, entities = listOf(issue(id = 3, page = 2)))

            assertEquals(listOf(1L, 3L), dao.pagingSource(OWNER, REPO, FILTER).loadAll().map { it.issueId })
        }

    @Test
    fun replaceAll_dropsPreviousPagesAndWritesFirstPage() =
        runTest {
            dao.upsert(listOf(issue(id = 1, page = 1), issue(id = 2, page = 2)))

            dao.replaceAll(OWNER, REPO, FILTER, entities = listOf(issue(id = 9, page = 1)))

            val rows = dao.pagingSource(OWNER, REPO, FILTER).loadAll()
            assertEquals(listOf(9L), rows.map { it.issueId })
            assertEquals(1, dao.maxPage(OWNER, REPO, FILTER))
        }

    @Test
    fun clearByRepo_removesEveryFilterOfThatRepoOnly() =
        runTest {
            dao.upsert(listOf(issue(id = 1, filter = "open"), issue(id = 2, filter = "closed")))
            dao.upsert(listOf(issue(id = 3, filter = "open", repo = "other")))

            dao.clearByRepo(OWNER, REPO)

            assertEquals(0, dao.countByFilter(OWNER, REPO, "open"))
            assertEquals(0, dao.countByFilter(OWNER, REPO, "closed"))
            assertEquals(1, dao.countByFilter(OWNER, "other", "open"))
        }

    @Test
    fun maxPage_noCachedRows_returnsNull() =
        runTest {
            assertNull(dao.maxPage(OWNER, REPO, FILTER))
        }

    @Test
    fun countByFilter_separatesFiltersAndRepos() =
        runTest {
            dao.upsert(listOf(issue(id = 1, filter = "open"), issue(id = 2, filter = "closed")))

            assertEquals(1, dao.countByFilter(OWNER, REPO, "open"))
            assertEquals(1, dao.countByFilter(OWNER, REPO, "closed"))
            assertEquals(0, dao.countByFilter("someone", REPO, "open"))
        }

    @Test
    fun pagingSource_replaysRemoteOrderAcrossPages() =
        runTest {
            // 插入顺序刻意打乱：查询必须按 (page, position) 还原远端顺序
            dao.upsert(
                listOf(
                    issue(id = 3, page = 2, position = 0),
                    issue(id = 1, page = 1, position = 0),
                    issue(id = 4, page = 2, position = 1),
                    issue(id = 2, page = 1, position = 1),
                ),
            )

            val rows = dao.pagingSource(OWNER, REPO, FILTER).loadAll()

            assertEquals(listOf(1L, 2L, 3L, 4L), rows.map { it.issueId })
        }

    @Test
    fun pagingSource_pageTwoLoadedWithOffsetKey_returnsRemainingRows() =
        runTest {
            dao.upsert((1..5).map { issue(id = it.toLong(), page = 1, position = it - 1) })
            val source = dao.pagingSource(OWNER, REPO, FILTER)

            val first = source.load(refreshParams(loadSize = 2)) as LoadResult.Page
            val second = source.load(appendParams(key = first.nextKey ?: 0, loadSize = 2)) as LoadResult.Page

            assertEquals(listOf(1L, 2L), first.data.map { it.issueId })
            assertEquals(listOf(3L, 4L), second.data.map { it.issueId })
        }

    private fun issue(
        id: Long,
        page: Int = 1,
        position: Int = 0,
        owner: String = OWNER,
        repo: String = REPO,
        filter: String = FILTER,
        title: String = "Issue #$id",
    ): IssueEntity =
        IssueEntity(
            owner = owner,
            repo = repo,
            filter = filter,
            issueId = id,
            number = id.toInt(),
            title = title,
            state = "open",
            authorLogin = "octocat",
            authorAvatarUrl = "https://avatars.githubusercontent.com/u/1",
            commentCount = 0,
            isPullRequest = false,
            updatedAt = "2026-09-06T00:00:00Z",
            htmlUrl = "https://github.com/$owner/$repo/issues/$id",
            page = page,
            position = position,
            cachedAt = 1_700_000_000_000L,
        )

    private fun refreshParams(loadSize: Int): PagingSource.LoadParams.Refresh<Int> =
        PagingSource.LoadParams.Refresh(key = null, loadSize = loadSize, placeholdersEnabled = false)

    private fun appendParams(
        key: Int,
        loadSize: Int,
    ): PagingSource.LoadParams.Append<Int> = PagingSource.LoadParams.Append(key = key, loadSize = loadSize, placeholdersEnabled = false)

    /** 一次性读出全部缓存行（loadSize 取足够大） */
    private suspend fun PagingSource<Int, IssueEntity>.loadAll(): List<IssueEntity> {
        val result = load(refreshParams(loadSize = 100))
        assertTrue("期望 LoadResult.Page，实际 $result", result is LoadResult.Page)
        return (result as LoadResult.Page).data
    }

    private companion object {
        const val OWNER = "octocat"
        const val REPO = "Hello-World"
        const val FILTER = "open"
    }
}
