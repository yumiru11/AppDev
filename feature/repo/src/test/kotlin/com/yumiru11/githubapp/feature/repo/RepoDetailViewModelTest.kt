package com.yumiru11.githubapp.feature.repo

import androidx.lifecycle.SavedStateHandle
import com.yumiru11.githubapp.core.markdown.webview.MarkdownThemeTokens
import com.yumiru11.githubapp.core.markdown.webview.RenderMode
import com.yumiru11.githubapp.core.testing.MainDispatcherRule
import com.yumiru11.githubapp.core.testing.fake.GitHubFakes
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

/**
 * RepoDetailViewModel 单测（纯 JVM，MockK 桩 RepoRepository）。
 *
 * 覆盖：成功/404/网络错误 → UiState；README 一律 WEBVIEW（服务端 HTML / 离线 GFM 降级透传）；
 * README 404 → 空态；retry 重新加载。
 */
class RepoDetailViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val savedStateHandle =
        SavedStateHandle(mapOf("owner" to "octocat", "repo" to "Hello-World"))

    private fun viewModel(repoRepository: RepoRepository): RepoDetailViewModel =
        RepoDetailViewModel(
            savedStateHandle = savedStateHandle,
            repoRepository = repoRepository,
        )

    private fun httpException(code: Int): HttpException =
        HttpException(Response.error<Any>(code, "error".toResponseBody("text/plain".toMediaType())))

    @Test
    fun loadRepoDetail_success_emitsSuccessState() =
        runTest {
            val repoRepository =
                mockk<RepoRepository> {
                    coEvery { getRepository("octocat", "Hello-World") } returns GitHubFakes.fakeRepository()
                    coEvery { getReadme(any(), any(), any<String>()) } returns
                        Result.success(
                            ReadmeContent(
                                markdown = "# Hello",
                                html = "<p>server html</p>",
                                renderMode = ReadmeRenderMode.WEBVIEW,
                            ),
                        )
                }

            val state = viewModel(repoRepository).uiState.value

            assertTrue(state is RepoDetailUiState.Success)
            val success = state as RepoDetailUiState.Success
            assertEquals("octocat", success.repo.ownerLogin)
            assertEquals("Hello-World", success.repo.name)
            assertEquals(
                ReadmeState.Loaded("<p>server html</p>", ReadmeRenderMode.WEBVIEW),
                success.readmeState,
            )
        }

    @Test
    fun loadRepoDetail_notFound_emitsErrorNotFound() =
        runTest {
            val repoRepository =
                mockk<RepoRepository> {
                    coEvery { getRepository(any(), any()) } throws httpException(404)
                }

            val state = viewModel(repoRepository).uiState.value

            assertEquals(RepoDetailUiState.Error(RepoErrorType.NOT_FOUND), state)
        }

    @Test
    fun loadRepoDetail_networkError_emitsErrorNetwork() =
        runTest {
            val repoRepository =
                mockk<RepoRepository> {
                    coEvery { getRepository(any(), any()) } throws IOException("network down")
                }

            val state = viewModel(repoRepository).uiState.value

            assertEquals(RepoDetailUiState.Error(RepoErrorType.NETWORK), state)
        }

    @Test
    fun loadReadme_webViewHtml_emitsLoadedWebView() =
        runTest {
            val repoRepository =
                mockk<RepoRepository> {
                    coEvery { getRepository(any(), any()) } returns GitHubFakes.fakeRepository()
                    coEvery { getReadme(any(), any(), any<String>()) } returns
                        Result.success(
                            ReadmeContent(
                                markdown = "# Hello\n\nSimple README.",
                                html = "<p>server html</p>",
                                renderMode = ReadmeRenderMode.WEBVIEW,
                            ),
                        )
                }

            val state = viewModel(repoRepository).uiState.value

            val readmeState = (state as RepoDetailUiState.Success).readmeState
            assertEquals(
                ReadmeState.Loaded("<p>server html</p>", ReadmeRenderMode.WEBVIEW),
                readmeState,
            )
        }

    @Test
    fun loadReadme_offlineMarkdownFallback_emitsLoadedWebViewOffline() =
        runTest {
            // Task B 降级：README 拿不到服务端 HTML → html 字段为原始 markdown，
            // webViewRenderMode = OFFLINE_MARKDOWN_IT，UI 据此走 WebView 离线 GFM。
            val repoRepository =
                mockk<RepoRepository> {
                    coEvery { getRepository(any(), any()) } returns GitHubFakes.fakeRepository()
                    coEvery { getReadme(any(), any(), any<String>()) } returns
                        Result.success(
                            ReadmeContent(
                                markdown = "# Hello",
                                html = "# Hello",
                                renderMode = ReadmeRenderMode.WEBVIEW,
                                webViewRenderMode = RenderMode.OFFLINE_MARKDOWN_IT,
                            ),
                        )
                }

            val state = viewModel(repoRepository).uiState.value

            val readmeState = (state as RepoDetailUiState.Success).readmeState
            assertEquals(
                ReadmeState.Loaded(
                    content = "# Hello",
                    renderMode = ReadmeRenderMode.WEBVIEW,
                    webViewRenderMode = RenderMode.OFFLINE_MARKDOWN_IT,
                ),
                readmeState,
            )
        }

    @Test
    fun loadReadme_blankHtml_emitsEmpty() =
        runTest {
            // README 渲染通道恒 WEBVIEW，但 HTML 缺失/为空 → 空态
            val repoRepository =
                mockk<RepoRepository> {
                    coEvery { getRepository(any(), any()) } returns GitHubFakes.fakeRepository()
                    coEvery { getReadme(any(), any(), any<String>()) } returns
                        Result.success(
                            ReadmeContent(
                                markdown = "# Hello",
                                html = null,
                                renderMode = ReadmeRenderMode.WEBVIEW,
                            ),
                        )
                }

            val state = viewModel(repoRepository).uiState.value

            assertEquals(ReadmeState.Empty, (state as RepoDetailUiState.Success).readmeState)
        }

    @Test
    fun loadReadme_notFound_emitsEmpty() =
        runTest {
            val repoRepository =
                mockk<RepoRepository> {
                    coEvery { getRepository(any(), any()) } returns GitHubFakes.fakeRepository()
                    coEvery { getReadme(any(), any(), any<String>()) } returns Result.failure(httpException(404))
                }

            val state = viewModel(repoRepository).uiState.value

            assertEquals(ReadmeState.Empty, (state as RepoDetailUiState.Success).readmeState)
        }

    @Test
    fun loadReadme_networkError_emitsErrorNetwork() =
        runTest {
            val repoRepository =
                mockk<RepoRepository> {
                    coEvery { getRepository(any(), any()) } returns GitHubFakes.fakeRepository()
                    coEvery { getReadme(any(), any(), any<String>()) } returns Result.failure(IOException("boom"))
                }

            val state = viewModel(repoRepository).uiState.value

            assertEquals(
                ReadmeState.Error(RepoErrorType.NETWORK),
                (state as RepoDetailUiState.Success).readmeState,
            )
        }

    @Test
    fun retry_afterRepoError_reloadsAndSucceeds() =
        runTest {
            val repoRepository =
                mockk<RepoRepository> {
                    coEvery { getRepository(any(), any()) } throws IOException("first attempt failed")
                }
            val viewModel = viewModel(repoRepository)
            assertEquals(RepoDetailUiState.Error(RepoErrorType.NETWORK), viewModel.uiState.value)

            // 第二次尝试成功
            coEvery { repoRepository.getRepository(any(), any()) } returns GitHubFakes.fakeRepository()
            coEvery { repoRepository.getReadme(any(), any(), any<String>()) } returns
                Result.success(
                    ReadmeContent(
                        markdown = "# Hello",
                        html = "<p>server html</p>",
                        renderMode = ReadmeRenderMode.WEBVIEW,
                    ),
                )

            viewModel.retry()

            val state = viewModel.uiState.value
            assertTrue(state is RepoDetailUiState.Success)
            assertEquals(
                ReadmeState.Loaded("<p>server html</p>", ReadmeRenderMode.WEBVIEW),
                (state as RepoDetailUiState.Success).readmeState,
            )
        }

    @Test
    fun loadRepoDetail_unknownError_emitsErrorUnknown() =
        runTest {
            val repoRepository =
                mockk<RepoRepository> {
                    coEvery { getRepository(any(), any()) } throws IllegalStateException("unexpected")
                }

            val state = viewModel(repoRepository).uiState.value

            assertEquals(RepoDetailUiState.Error(RepoErrorType.UNKNOWN), state)
        }

    @Test
    fun retry_afterReadmeError_reloadsRepoAndReadmeAndSucceeds() =
        runTest {
            // 首次：仓库元数据成功、README 网络失败 → README Error；retry 应重新拉取两者并成功
            val repoRepository =
                mockk<RepoRepository> {
                    coEvery { getRepository(any(), any()) } returns GitHubFakes.fakeRepository()
                    coEvery { getReadme(any(), any(), any<String>()) } returns Result.failure(IOException("boom"))
                }
            val viewModel = viewModel(repoRepository)
            assertEquals(
                ReadmeState.Error(RepoErrorType.NETWORK),
                (viewModel.uiState.value as RepoDetailUiState.Success).readmeState,
            )

            coEvery { repoRepository.getReadme(any(), any(), any<String>()) } returns
                Result.success(
                    ReadmeContent(
                        markdown = "# Hello",
                        html = "<p>server html</p>",
                        renderMode = ReadmeRenderMode.WEBVIEW,
                    ),
                )

            viewModel.retry()

            val state = viewModel.uiState.value
            assertTrue(state is RepoDetailUiState.Success)
            assertEquals(
                ReadmeState.Loaded("<p>server html</p>", ReadmeRenderMode.WEBVIEW),
                (state as RepoDetailUiState.Success).readmeState,
            )
            coVerify(exactly = 2) { repoRepository.getRepository(any(), any()) }
            coVerify(exactly = 2) { repoRepository.getReadme(any(), any(), any<String>()) }
        }

    @Test
    fun loadReadme_passesCurrentThemeVersion_forCacheInvalidation() =
        runTest {
            // 主题版本是 README 双 key 缓存的失效键：VM 必须把当前版本传给仓库
            val repoRepository =
                mockk<RepoRepository> {
                    coEvery { getRepository(any(), any()) } returns GitHubFakes.fakeRepository()
                    coEvery { getReadme(any(), any(), any<String>()) } returns
                        Result.success(
                            ReadmeContent(
                                markdown = "# Hello",
                                html = "<p>server html</p>",
                                renderMode = ReadmeRenderMode.WEBVIEW,
                            ),
                        )
                }

            viewModel(repoRepository)

            coVerify(exactly = 1) {
                repoRepository.getReadme("octocat", "Hello-World", MarkdownThemeTokens.versionHash())
            }
        }
}
