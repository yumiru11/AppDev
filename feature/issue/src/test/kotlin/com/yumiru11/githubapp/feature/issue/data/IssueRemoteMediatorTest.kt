package com.yumiru11.githubapp.feature.issue.data

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingConfig
import androidx.paging.PagingSource
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import com.yumiru11.githubapp.core.database.dao.IssueDao
import com.yumiru11.githubapp.core.database.entity.IssueEntity
import com.yumiru11.githubapp.core.githubrest.api.IssueApi
import com.yumiru11.githubapp.core.githubrest.model.IssueDto
import com.yumiru11.githubapp.core.githubrest.model.UserDto
import com.yumiru11.githubapp.feature.issue.model.IssueFilter
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import java.io.IOException

/**
 * IssueRemoteMediator 单测（issue #165 / L07，MockK 桩 IssueApi + 内存版 IssueDao，零网络）。
 *
 * 覆盖：首屏 REFRESH 写第 1 页、末页判定、续页锚点 = MAX(page)+1、PREPEND 恒终止、
 * 本地无数据时不续拉、网络失败降级（有缓存 → Success 让 Room 出数据；无缓存 → Error）、
 * 首屏整段替换（旧页不残留）、initialize 恒 LAUNCH_INITIAL_REFRESH。
 */
@OptIn(ExperimentalPagingApi::class)
class IssueRemoteMediatorTest {
    @Test
    fun initialize_alwaysLaunchesInitialRefresh() =
        runTest {
            assertEquals(RemoteMediator.InitializeAction.LAUNCH_INITIAL_REFRESH, mediator().initialize())
        }

    @Test
    fun loadRefresh_fullPage_writesFirstPageAndKeepsPaging() =
        runTest {
            val dao = FakeIssueDao()
            val api = apiReturning((1..30).map { dto(id = it.toLong()) })

            val result = mediator(api = api, dao = dao).load(LoadType.REFRESH, emptyState())

            assertSuccess(result, endOfPaginationReached = false)
            assertEquals(30, dao.rows.size)
            assertTrue(dao.rows.all { it.page == 1 })
            assertEquals((0..29).toList(), dao.rows.map { it.position })
            coVerify { api.listIssues("octocat", "Hello-World", "open", 1, 30) }
        }

    @Test
    fun loadRefresh_partialPage_signalsEndOfPagination() =
        runTest {
            val dao = FakeIssueDao()

            val result =
                mediator(dao = dao, api = apiReturning(listOf(dto(1), dto(2))))
                    .load(LoadType.REFRESH, emptyState())

            assertSuccess(result, endOfPaginationReached = true)
        }

    @Test
    fun loadRefresh_emptyPage_signalsEndOfPagination() =
        runTest {
            val dao = FakeIssueDao()

            val result = mediator(dao = dao, api = apiReturning(emptyList())).load(LoadType.REFRESH, emptyState())

            assertSuccess(result, endOfPaginationReached = true)
            assertTrue(dao.rows.isEmpty())
        }

    @Test
    fun loadRefresh_success_dropsPreviouslyCachedPages() =
        runTest {
            val dao = FakeIssueDao()
            dao.upsert((1..3).map { entity(id = it.toLong(), page = 2, position = it) })

            mediator(dao = dao, api = apiReturning(listOf(dto(99)))).load(LoadType.REFRESH, emptyState())

            assertEquals(listOf(99L), dao.rows.map { it.issueId })
        }

    @Test
    fun loadAppend_requestsMaxCachedPagePlusOne() =
        runTest {
            val dao = FakeIssueDao()
            dao.upsert((1..30).map { entity(id = it.toLong(), page = 1, position = it) })
            val api = apiReturning(listOf(dto(31)))

            val result =
                mediator(api = api, dao = dao)
                    .load(LoadType.APPEND, stateWith(lastItem = issue(id = 30)))

            assertSuccess(result, endOfPaginationReached = true)
            coVerify { api.listIssues("octocat", "Hello-World", "open", 2, 30) }
            assertEquals(listOf(31L), dao.rows.filter { it.page == 2 }.map { it.issueId })
        }

    @Test
    fun loadAppend_replacesOnlyThatPage() =
        runTest {
            val dao = FakeIssueDao()
            dao.upsert((1..30).map { entity(id = it.toLong(), page = 1, position = it) })

            mediator(dao = dao, api = apiReturning(listOf(dto(32))))
                .load(LoadType.APPEND, stateWith(lastItem = issue(id = 30)))

            // 续页只动第 2 页：第 1 页 30 行原样保留（逐页替换语义，同页旧行清理见 IssueDaoTest）
            assertEquals(30, dao.rows.count { it.page == 1 })
            assertEquals(listOf(32L), dao.rows.filter { it.page == 2 }.map { it.issueId })
        }

    @Test
    fun loadAppend_withoutLocalItems_skipsNetwork() =
        runTest {
            val api = apiReturning(listOf(dto(1)))

            val result = mediator(api = api, dao = FakeIssueDao()).load(LoadType.APPEND, emptyState())

            assertSuccess(result, endOfPaginationReached = true)
            coVerify(exactly = 0) { api.listIssues(any(), any(), any(), any(), any()) }
        }

    @Test
    fun loadPrepend_alwaysStopsPagination() =
        runTest {
            val api = apiReturning(listOf(dto(1)))

            val result =
                mediator(api = api, dao = FakeIssueDao())
                    .load(LoadType.PREPEND, stateWith(lastItem = issue(id = 1)))

            assertSuccess(result, endOfPaginationReached = true)
            coVerify(exactly = 0) { api.listIssues(any(), any(), any(), any(), any()) }
        }

    @Test
    fun loadRefresh_ioFailureWithoutCache_returnsError() =
        runTest {
            val api = failingApi(IOException("network down"))

            val result = mediator(api = api, dao = FakeIssueDao()).load(LoadType.REFRESH, emptyState())

            assertTrue(result is RemoteMediator.MediatorResult.Error)
        }

    @Test
    fun loadRefresh_httpFailureWithoutCache_returnsError() =
        runTest {
            val api = failingApi(httpException(500))

            val result = mediator(api = api, dao = FakeIssueDao()).load(LoadType.REFRESH, emptyState())

            assertTrue(result is RemoteMediator.MediatorResult.Error)
        }

    @Test
    fun loadRefresh_ioFailureWithCache_degradesToSuccessSoRoomServesData() =
        runTest {
            val dao = FakeIssueDao()
            dao.upsert(listOf(entity(id = 7, page = 1, position = 0)))
            val api = failingApi(IOException("offline"))

            val result = mediator(api = api, dao = dao).load(LoadType.REFRESH, emptyState())

            assertSuccess(result, endOfPaginationReached = true)
            // 降级不破坏既有缓存（验收：断网可看已访问列表）
            assertEquals(1, dao.rows.size)
        }

    @Test
    fun loadAppend_ioFailureWithCache_degradesToSuccess() =
        runTest {
            val dao = FakeIssueDao()
            dao.upsert(listOf(entity(id = 7, page = 1, position = 0)))
            val api = failingApi(IOException("offline"))

            val result =
                mediator(api = api, dao = dao)
                    .load(LoadType.APPEND, stateWith(lastItem = issue(id = 7)))

            assertSuccess(result, endOfPaginationReached = true)
        }

    @Test
    fun loadRefresh_usesFilterRawValueAndClockForCacheTimestamp() =
        runTest {
            val dao = FakeIssueDao()

            mediator(dao = dao, api = apiReturning(listOf(dto(1))), filter = IssueFilter.CLOSED, now = 42L)
                .load(LoadType.REFRESH, emptyState())

            assertEquals(42L, dao.rows.single().cachedAt)
            assertEquals("closed", dao.rows.single().filter)
        }

    @Test
    fun loadRefresh_mapsDtoFieldsIntoCacheEntity() =
        runTest {
            val dao = FakeIssueDao()

            mediator(
                dao = dao,
                api = apiReturning(listOf(dto(id = 1347, number = 42, title = "Bug", isPr = true))),
            ).load(LoadType.REFRESH, emptyState())

            val row = dao.rows.single()
            assertTrue(row.isPullRequest)
            assertEquals(1347L, row.issueId)
            assertEquals(42, row.number)
            assertEquals("Bug", row.title)
            assertEquals("octocat", row.authorLogin)
            assertFalse(row.cachedAt == 0L)
        }

    /** MediatorResult.Success 未实现 equals → 逐字段断言 */
    private fun assertSuccess(
        result: RemoteMediator.MediatorResult,
        endOfPaginationReached: Boolean,
    ) {
        assertTrue("期望 MediatorResult.Success，实际 $result", result is RemoteMediator.MediatorResult.Success)
        assertEquals(
            endOfPaginationReached,
            (result as RemoteMediator.MediatorResult.Success).endOfPaginationReached,
        )
    }

    private fun mediator(
        api: IssueApi = apiReturning(emptyList()),
        dao: IssueDao = FakeIssueDao(),
        filter: IssueFilter = IssueFilter.OPEN,
        now: Long = 1_700_000_000_000L,
    ): IssueRemoteMediator =
        IssueRemoteMediator(
            issueApi = api,
            issueDao = dao,
            owner = "octocat",
            repo = "Hello-World",
            filter = filter,
            clock = { now },
        )

    private fun apiReturning(issues: List<IssueDto>): IssueApi =
        mockk {
            coEvery { listIssues(any(), any(), any(), any(), any()) } returns issues
        }

    private fun failingApi(error: Throwable): IssueApi =
        mockk {
            coEvery { listIssues(any(), any(), any(), any(), any()) } throws error
        }

    private fun dto(
        id: Long,
        number: Int = 42,
        title: String = "Bug report",
        isPr: Boolean = false,
    ): IssueDto =
        IssueDto(
            id = id,
            number = number,
            title = title,
            state = "open",
            user = UserDto(id = 1L, login = "octocat", avatarUrl = "https://avatars.githubusercontent.com/u/1"),
            comments = 3,
            updatedAt = "2026-09-06T00:00:00Z",
            htmlUrl = "https://github.com/octocat/Hello-World/issues/$number",
            pullRequest = if (isPr) kotlinx.serialization.json.JsonObject(emptyMap()) else null,
        )

    private fun entity(
        id: Long,
        page: Int,
        position: Int,
    ): IssueEntity =
        IssueEntity(
            owner = "octocat",
            repo = "Hello-World",
            filter = "open",
            issueId = id,
            number = id.toInt(),
            title = "Issue #$id",
            state = "open",
            authorLogin = "octocat",
            authorAvatarUrl = null,
            commentCount = 0,
            isPullRequest = false,
            updatedAt = null,
            htmlUrl = null,
            page = page,
            position = position,
            cachedAt = 0L,
        )

    private fun issue(id: Long): IssueEntity = entity(id = id, page = 1, position = 0)

    private fun emptyState(): PagingState<Int, IssueEntity> =
        PagingState(
            pages = emptyList(),
            anchorPosition = null,
            config = PagingConfig(pageSize = PAGE_SIZE),
            leadingPlaceholderCount = 0,
        )

    private fun stateWith(lastItem: IssueEntity): PagingState<Int, IssueEntity> =
        PagingState(
            pages = listOf(PagingSource.LoadResult.Page(data = listOf(lastItem), prevKey = null, nextKey = 2)),
            anchorPosition = 0,
            config = PagingConfig(pageSize = PAGE_SIZE),
            leadingPlaceholderCount = 0,
        )

    private fun httpException(code: Int): HttpException {
        val body = """{"message":"error"}""".toResponseBody("application/json".toMediaType())
        val rawResponse =
            okhttp3.Response
                .Builder()
                .request(
                    okhttp3.Request
                        .Builder()
                        .url("http://localhost/")
                        .build(),
                ).protocol(okhttp3.Protocol.HTTP_1_1)
                .code(code)
                .message("error")
                .body(body)
                .build()
        return HttpException(retrofit2.Response.error<Any>(body, rawResponse))
    }

    private companion object {
        const val PAGE_SIZE = 30
    }
}

/**
 * 内存版 [IssueDao]：语义对齐 Room 实现（同主键覆盖、按页替换/清理），
 * 用于在没有 Room 运行时的纯 JVM 单测中驱动 mediator（真库行为由 IssueDaoTest 覆盖）。
 */
private class FakeIssueDao : IssueDao() {
    val rows = mutableListOf<IssueEntity>()

    override suspend fun upsert(entities: List<IssueEntity>) {
        entities.forEach { candidate ->
            rows.removeAll { it.sameKey(candidate) }
            rows += candidate
        }
    }

    override suspend fun deleteByPage(
        owner: String,
        repo: String,
        filter: String,
        page: Int,
    ) {
        rows.removeAll { it.owner == owner && it.repo == repo && it.filter == filter && it.page == page }
    }

    override suspend fun clearByFilter(
        owner: String,
        repo: String,
        filter: String,
    ) {
        rows.removeAll { it.owner == owner && it.repo == repo && it.filter == filter }
    }

    override suspend fun clearByRepo(
        owner: String,
        repo: String,
    ) {
        rows.removeAll { it.owner == owner && it.repo == repo }
    }

    override suspend fun maxPage(
        owner: String,
        repo: String,
        filter: String,
    ): Int? = rows.filter { it.owner == owner && it.repo == repo && it.filter == filter }.maxOfOrNull { it.page }

    override suspend fun countByFilter(
        owner: String,
        repo: String,
        filter: String,
    ): Int = rows.count { it.owner == owner && it.repo == repo && it.filter == filter }

    override fun pagingSource(
        owner: String,
        repo: String,
        filter: String,
    ): PagingSource<Int, IssueEntity> = error("FakeIssueDao 不提供 Room 分页源（由 IssueDaoTest 覆盖真库）")

    private fun IssueEntity.sameKey(other: IssueEntity): Boolean =
        owner == other.owner && repo == other.repo && filter == other.filter && issueId == other.issueId
}
