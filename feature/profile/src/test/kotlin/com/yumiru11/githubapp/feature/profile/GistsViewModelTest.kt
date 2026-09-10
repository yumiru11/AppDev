package com.yumiru11.githubapp.feature.profile

import androidx.lifecycle.SavedStateHandle
import androidx.paging.testing.asSnapshot
import com.yumiru11.githubapp.core.githubrest.api.GistApi
import com.yumiru11.githubapp.core.githubrest.api.GitHubRestClient
import com.yumiru11.githubapp.core.githubrest.auth.GuestTokenProvider
import com.yumiru11.githubapp.core.githubrest.http.InMemoryEtagStore
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit

/**
 * [GistsViewModel] 单测（L11）：路由参数注入 + 分页流首屏数据。
 *
 * 真实网络 IO 与 runTest 虚拟时钟会死锁，故用 runBlocking（同 ProfileViewModelPagingTest 先例）；
 * 列表断言用 paging-testing 的 [asSnapshot]（等待加载收敛）。
 */
class GistsViewModelTest {
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.close()
    }

    private fun retrofit(): Retrofit =
        GitHubRestClient.createRetrofit(
            baseUrl = server.url("/"),
            client =
                GitHubRestClient.createOkHttpClient(
                    tokenProvider = GuestTokenProvider(),
                    etagStore = InMemoryEtagStore(),
                    debugLogging = false,
                ),
            json = GitHubRestClient.createJson(),
        )

    private fun viewModel(
        username: String,
        gistApi: GistApi,
        profileRepository: ProfileRepository =
            ProfileRepository(
                userApi = mockk(),
                gistApi = gistApi,
            ),
    ): GistsViewModel = GistsViewModel(SavedStateHandle(mapOf("username" to username)), profileRepository)

    @Test
    fun username_fromRouteSavedState_isExposed() {
        val model = viewModel("torvalds", gistApi = mockk())

        assertEquals("torvalds", model.username)
    }

    @Test
    fun gistsFlow_collect_loadsFirstPageFromUserGistsEndpoint() =
        runBlocking {
            server.enqueue(
                MockResponse
                    .Builder()
                    .body(
                        """
                        [
                          {
                            "id": "g1",
                            "description": "hello gist",
                            "html_url": "https://gist.github.com/torvalds/g1",
                            "created_at": "2026-08-01T10:00:00Z",
                            "files": { "hello.kt": { "filename": "hello.kt", "language": "Kotlin" } }
                          }
                        ]
                        """.trimIndent(),
                    ).addHeader("Content-Type", "application/json")
                    .build(),
            )
            val gistApi = retrofit().create(GistApi::class.java)

            val items = viewModel("torvalds", gistApi).gists.asSnapshot()

            assertEquals(1, items.size)
            assertEquals("hello.kt", items.single().fileName)
            assertEquals("Kotlin", items.single().language)
            val request = server.takeRequest()
            assertEquals("/users/torvalds/gists", request.url.encodedPath)
            assertEquals("1", request.url.queryParameter("page"))
        }
}
