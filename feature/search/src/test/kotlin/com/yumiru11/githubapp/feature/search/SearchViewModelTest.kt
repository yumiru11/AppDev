package com.yumiru11.githubapp.feature.search

import androidx.paging.PagingData
import com.yumiru11.githubapp.core.githubauth.auth.AuthState
import com.yumiru11.githubapp.core.githubauth.auth.OAuthSessionManager
import com.yumiru11.githubapp.core.githubauth.token.SessionData
import com.yumiru11.githubapp.core.githubdata.error.GitHubError
import com.yumiru11.githubapp.core.githubdata.error.GitHubRequestException
import com.yumiru11.githubapp.core.githubrest.http.InMemoryRateLimitStore
import com.yumiru11.githubapp.core.githubrest.http.RateLimitSnapshot
import com.yumiru11.githubapp.core.githubrest.http.RateLimitStore
import com.yumiru11.githubapp.core.testing.MainDispatcherRule
import com.yumiru11.githubapp.feature.search.data.SearchHistoryRepository
import com.yumiru11.githubapp.feature.search.data.SearchPagingRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import retrofit2.HttpException
import java.io.IOException

/**
 * SearchViewModel 单测（纯 JVM，MockK 桩 Paging/History 仓库与 T4 OAuthSessionManager）。
 *
 * 覆盖：提交 → Success（活动 Tab 流）、300ms 防抖搜索、空输入 → Idle、
 * 空白提交忽略、Tab 切换重建流、代码搜索登录门（未登录不发请求/登录后补搜）、
 * 构造期异常 → Error（429 → RATE_LIMITED）、retry 恢复、历史记录/清除/去重。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {
    // debounce 依赖虚拟时间：StandardTestDispatcher + runTest 共享调度器
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(StandardTestDispatcher())

    @Before
    fun warmUpMockK() {
        // MockK/ByteBuddy 首次生成字节码 + ViewModel/Paging 首次类加载与 JIT 都是真实墙钟开销；
        // 若发生在 runTest 体内，会计入整测超时预算，高负载/CI 覆盖率插桩下超过 60s 即抛
        // UncompletedCoroutinesError（kotlinx.coroutines#3800）。在 @Before 中复用桩工厂与一次
        // 轻量冒烟预热，把一次性初始化移出受测超时窗口。
        val warmUp = viewModel(pagingRepository(), historyRepository(), sessionManager(AuthState.Anonymous))
        warmUp.submitQuery("warmup")
    }

    private fun sessionManager(auth: AuthState): OAuthSessionManager =
        mockk<OAuthSessionManager> {
            every { authState } returns MutableStateFlow(auth)
        }

    private fun pagingRepository(): SearchPagingRepository =
        mockk<SearchPagingRepository> {
            every { repositories(any()) } returns flowOf(PagingData.empty())
            every { users(any()) } returns flowOf(PagingData.empty())
            every { issues(any()) } returns flowOf(PagingData.empty())
            every { pullRequests(any()) } returns flowOf(PagingData.empty())
            every { code(any()) } returns flowOf(PagingData.empty())
        }

    private fun historyRepository(): SearchHistoryRepository =
        mockk<SearchHistoryRepository> {
            coEvery { recent() } returns emptyList()
            coEvery { add(any()) } returns Unit
            coEvery { clear() } returns Unit
        }

    @Test
    fun submitQuery_signedIn_emitsSuccessWithActiveTabFlow() =
        runTest(mainDispatcherRule.testDispatcher) {
            val paging = pagingRepository()
            val viewModel =
                viewModel(
                    paging,
                    historyRepository(),
                    sessionManager(AuthState.SignedIn(SessionData(accessToken = "tok"))),
                )
            runCurrent()

            viewModel.submitQuery("kotlin")
            runCurrent()

            val state = viewModel.uiState.value
            assertTrue(state is SearchUiState.Success)
            assertEquals("kotlin", (state as SearchUiState.Success).query)
            assertEquals(SearchTab.REPOSITORIES, state.activeTab)
            verify(exactly = 1) { paging.repositories("kotlin") }
        }

    @Test
    fun submitQuery_recordsHistoryAndRefreshesList() =
        runTest(mainDispatcherRule.testDispatcher) {
            val recorded = mutableListOf<String>()
            val historySlot = slot<String>()
            val history =
                mockk<SearchHistoryRepository> {
                    coEvery { recent() } returns emptyList()
                    coEvery { add(capture(historySlot)) } answers { recorded += historySlot.captured }
                    coEvery { clear() } returns Unit
                }
            val viewModel = viewModel(pagingRepository(), history, sessionManager(AuthState.PAT))
            runCurrent()

            viewModel.submitQuery("kotlin")
            runCurrent()

            assertEquals(listOf("kotlin"), recorded)
        }

    @Test
    fun submitQuery_blankInput_ignoredWithoutSearchOrHistory() =
        runTest(mainDispatcherRule.testDispatcher) {
            val paging = pagingRepository()
            val history = historyRepository()
            val viewModel = viewModel(paging, history, sessionManager(AuthState.PAT))
            runCurrent()

            viewModel.submitQuery("   ")
            runCurrent()

            assertEquals(SearchUiState.Idle, viewModel.uiState.value)
            verify(exactly = 0) { paging.repositories(any()) }
            coVerify(exactly = 0) { history.add(any()) }
        }

    @Test
    fun onQueryChange_debouncedSearch_waits300msAndSearches() =
        runTest(mainDispatcherRule.testDispatcher) {
            val paging = pagingRepository()
            val viewModel = viewModel(paging, historyRepository(), sessionManager(AuthState.PAT))
            runCurrent()

            viewModel.onQueryChange("kot")
            viewModel.onQueryChange("kotlin")
            advanceTimeBy(299)
            runCurrent()
            verify(exactly = 0) { paging.repositories(any()) }

            advanceTimeBy(1)
            runCurrent()

            verify(exactly = 1) { paging.repositories("kotlin") }
            assertTrue(viewModel.uiState.value is SearchUiState.Success)
        }

    @Test
    fun onQueryChange_clearToEmpty_emitsIdle() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = viewModel(pagingRepository(), historyRepository(), sessionManager(AuthState.PAT))
            runCurrent()
            viewModel.submitQuery("kotlin")
            runCurrent()
            assertTrue(viewModel.uiState.value is SearchUiState.Success)

            viewModel.onQueryChange("")
            runCurrent()

            assertEquals(SearchUiState.Idle, viewModel.uiState.value)
        }

    @Test
    fun submitQuery_sameQueryAsDebounced_recordsHistoryWithoutDoubleSearch() =
        runTest(mainDispatcherRule.testDispatcher) {
            val paging = pagingRepository()
            val history =
                mockk<SearchHistoryRepository> {
                    coEvery { recent() } returns emptyList()
                    coEvery { add(any()) } returns Unit
                    coEvery { clear() } returns Unit
                }
            val viewModel = viewModel(paging, history, sessionManager(AuthState.PAT))
            runCurrent()
            viewModel.onQueryChange("kotlin")
            advanceTimeBy(300)
            runCurrent()
            verify(exactly = 1) { paging.repositories("kotlin") }

            viewModel.submitQuery("kotlin")
            runCurrent()

            verify(exactly = 1) { paging.repositories("kotlin") }
            coVerify(exactly = 1) { history.add("kotlin") }
        }

    @Test
    fun selectTab_rebuildsFlowForNewTab() =
        runTest(mainDispatcherRule.testDispatcher) {
            val paging = pagingRepository()
            val viewModel = viewModel(paging, historyRepository(), sessionManager(AuthState.PAT))
            runCurrent()
            viewModel.submitQuery("kotlin")
            runCurrent()
            assertTrue(viewModel.uiState.value is SearchUiState.Success)

            viewModel.selectTab(SearchTab.ISSUES)
            runCurrent()

            val state = viewModel.uiState.value as SearchUiState.Success
            assertEquals(SearchTab.ISSUES, state.activeTab)
            verify(exactly = 1) { paging.issues("kotlin") }
            verify(exactly = 1) { paging.repositories("kotlin") }
        }

    @Test
    fun selectTab_codeTabAnonymous_doesNotSearchAndKeepsEmptyFlow() =
        runTest(mainDispatcherRule.testDispatcher) {
            val paging = pagingRepository()
            val viewModel = viewModel(paging, historyRepository(), sessionManager(AuthState.Anonymous))
            runCurrent()
            viewModel.submitQuery("kotlin")
            runCurrent()
            assertTrue(viewModel.uiState.value is SearchUiState.Success)

            viewModel.selectTab(SearchTab.CODE)
            runCurrent()

            val state = viewModel.uiState.value as SearchUiState.Success
            assertEquals(SearchTab.CODE, state.activeTab)
            verify(exactly = 0) { paging.code(any()) }
        }

    @Test
    fun selectTab_codeTabSignedIn_searches() =
        runTest(mainDispatcherRule.testDispatcher) {
            val paging = pagingRepository()
            val viewModel = viewModel(paging, historyRepository(), sessionManager(AuthState.SignedIn(SessionData("tok"))))
            runCurrent()
            viewModel.submitQuery("kotlin")
            runCurrent()

            viewModel.selectTab(SearchTab.CODE)
            runCurrent()

            verify(exactly = 1) { paging.code("kotlin") }
        }

    @Test
    fun authState_anonymousThenSignedIn_codeTabAutoResearches() =
        runTest(mainDispatcherRule.testDispatcher) {
            val paging = pagingRepository()
            val authFlow = MutableStateFlow<AuthState>(AuthState.Anonymous)
            val session =
                mockk<OAuthSessionManager> {
                    every { authState } returns authFlow
                }
            val viewModel = viewModel(paging, historyRepository(), session)
            runCurrent()
            viewModel.submitQuery("kotlin")
            runCurrent()
            viewModel.selectTab(SearchTab.CODE)
            runCurrent()
            verify(exactly = 0) { paging.code(any()) }

            authFlow.value = AuthState.SignedIn(SessionData("tok"))
            runCurrent()

            verify(exactly = 1) { paging.code("kotlin") }
        }

    @Test
    fun load_http429_emitsErrorRateLimited() =
        runTest(mainDispatcherRule.testDispatcher) {
            val paging =
                mockk<SearchPagingRepository> {
                    every { repositories(any()) } throws httpException(429)
                }
            val viewModel = viewModel(paging, historyRepository(), sessionManager(AuthState.PAT))
            runCurrent()

            viewModel.submitQuery("kotlin")
            runCurrent()

            assertEquals(SearchUiState.Error(SearchErrorType.RATE_LIMITED), viewModel.uiState.value)
        }

    @Test
    fun load_ioException_emitsErrorNetwork() =
        runTest(mainDispatcherRule.testDispatcher) {
            val paging =
                mockk<SearchPagingRepository> {
                    every { repositories(any()) } throws IOException("network down")
                }
            val viewModel = viewModel(paging, historyRepository(), sessionManager(AuthState.PAT))
            runCurrent()

            viewModel.submitQuery("kotlin")
            runCurrent()

            assertEquals(SearchUiState.Error(SearchErrorType.NETWORK), viewModel.uiState.value)
        }

    @Test
    fun retry_afterError_reloadsAndSucceeds() =
        runTest(mainDispatcherRule.testDispatcher) {
            val paging =
                mockk<SearchPagingRepository> {
                    every { repositories(any()) } throws IOException("network down")
                }
            val viewModel = viewModel(paging, historyRepository(), sessionManager(AuthState.PAT))
            runCurrent()
            viewModel.submitQuery("kotlin")
            runCurrent()
            assertEquals(SearchUiState.Error(SearchErrorType.NETWORK), viewModel.uiState.value)

            every { paging.repositories(any()) } returns flowOf(PagingData.empty())
            viewModel.retry()
            runCurrent()

            assertTrue(viewModel.uiState.value is SearchUiState.Success)
        }

    @Test
    fun retry_inSuccessState_doesNotReload() =
        runTest(mainDispatcherRule.testDispatcher) {
            val paging = pagingRepository()
            val viewModel = viewModel(paging, historyRepository(), sessionManager(AuthState.PAT))
            runCurrent()
            viewModel.submitQuery("kotlin")
            runCurrent()
            assertTrue(viewModel.uiState.value is SearchUiState.Success)

            viewModel.retry()
            runCurrent()

            verify(exactly = 1) { paging.repositories("kotlin") }
        }

    @Test
    fun clearHistory_clearsAndEmitsEmpty() =
        runTest(mainDispatcherRule.testDispatcher) {
            val history =
                mockk<SearchHistoryRepository> {
                    coEvery { recent() } returns listOf("kotlin")
                    coEvery { add(any()) } returns Unit
                    coEvery { clear() } returns Unit
                }
            val viewModel = viewModel(pagingRepository(), history, sessionManager(AuthState.PAT))
            runCurrent()
            assertEquals(listOf("kotlin"), viewModel.history.value)

            coEvery { history.recent() } returns emptyList()
            viewModel.clearHistory()
            runCurrent()

            assertEquals(emptyList<String>(), viewModel.history.value)
            coVerify { history.clear() }
        }

    @Test
    fun init_anonymous_historyAndIdleInitialState() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = viewModel(pagingRepository(), historyRepository(), sessionManager(AuthState.Anonymous))
            runCurrent()

            assertEquals(SearchUiState.Idle, viewModel.uiState.value)
            assertEquals(SearchTab.REPOSITORIES, viewModel.activeTab.value)
            assertTrue(!viewModel.isLoggedIn.value)
        }

    @Test
    fun submitQuery_repositoryThrowsGitHubRequestException_mapsToErrorType() =
        runTest(mainDispatcherRule.testDispatcher) {
            val paging =
                mockk<SearchPagingRepository> {
                    every { repositories(any()) } throws GitHubRequestException(GitHubError.RateLimited(retryAfterSeconds = 60))
                }
            val viewModel = viewModel(paging, historyRepository(), sessionManager(AuthState.PAT))
            runCurrent()

            viewModel.submitQuery("kotlin")
            runCurrent()

            assertEquals(SearchUiState.Error(SearchErrorType.RATE_LIMITED), viewModel.uiState.value)
        }

    /** 统一构造被测 ViewModel（16 处调用点共用，便于后续新增依赖） */
    private fun viewModel(
        paging: SearchPagingRepository = pagingRepository(),
        history: SearchHistoryRepository = historyRepository(),
        session: OAuthSessionManager = sessionManager(AuthState.PAT),
        rateLimit: RateLimitStore = InMemoryRateLimitStore(),
    ): SearchViewModel = SearchViewModel(paging, history, session, rateLimit)

    @Test
    fun rateLimitWarning_remainingBelowThreshold_emitsWarning() =
        runTest(mainDispatcherRule.testDispatcher) {
            val store = InMemoryRateLimitStore()
            val viewModel = viewModel(rateLimit = store)
            runCurrent()
            assertNull(viewModel.rateLimitWarning.value)

            // core 配额 5000/h，剩余 3 → 低于 50 阈值
            store.record(
                RateLimitSnapshot(
                    limit = 5000,
                    remaining = 3,
                    resetEpochSeconds = System.currentTimeMillis() / 1000 + 120,
                    resource = "core",
                ),
            )
            runCurrent()

            val warning = viewModel.rateLimitWarning.value
            assertEquals(3, warning?.remaining)
            assertTrue("重置时间应折算为 1~3 分钟，实际 ${warning?.resetInMinutes}", (warning?.resetInMinutes ?: 0L) in 1L..3L)
        }

    @Test
    fun rateLimitWarning_remainingAboveThreshold_staysNull() =
        runTest(mainDispatcherRule.testDispatcher) {
            val store = InMemoryRateLimitStore()
            val viewModel = viewModel(rateLimit = store)
            runCurrent()

            store.record(RateLimitSnapshot(limit = 5000, remaining = 4321, resetEpochSeconds = 1_800_000_000L, resource = "core"))
            runCurrent()

            assertNull(viewModel.rateLimitWarning.value)
        }

    @Test
    fun retry_afterRateLimited_reloadsAndSucceeds() =
        runTest(mainDispatcherRule.testDispatcher) {
            val paging =
                mockk<SearchPagingRepository> {
                    every { repositories(any()) } throws httpException(429)
                }
            val viewModel = viewModel(paging = paging)
            runCurrent()
            viewModel.submitQuery("kotlin")
            runCurrent()
            assertEquals(SearchUiState.Error(SearchErrorType.RATE_LIMITED), viewModel.uiState.value)

            every { paging.repositories(any()) } returns flowOf(PagingData.empty())
            viewModel.retry()
            runCurrent()

            assertTrue(viewModel.uiState.value is SearchUiState.Success)
        }

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
}
