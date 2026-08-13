package com.yumiru11.githubapp.feature.repo

import androidx.lifecycle.SavedStateHandle
import com.yumiru11.githubapp.core.testing.MainDispatcherRule
import com.yumiru11.githubapp.core.testing.fake.GitHubFakes
import io.mockk.coEvery
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
 * 覆盖：成功/404/网络错误 → UiState；FeatureDetector 接线（NATIVE/WEBVIEW 透传）；
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
                                html = null,
                                renderMode = ReadmeRenderMode.NATIVE,
                            ),
                        )
                }

            val state = viewModel(repoRepository).uiState.value

            assertTrue(state is RepoDetailUiState.Success)
            val success = state as RepoDetailUiState.Success
            assertEquals("octocat", success.repo.ownerLogin)
            assertEquals("Hello-World", success.repo.name)
            assertEquals(
                ReadmeState.Loaded("# Hello", ReadmeRenderMode.NATIVE),
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
    fun loadReadme_nativeMarkdown_emitsLoadedNative() =
        runTest {
            val repoRepository =
                mockk<RepoRepository> {
                    coEvery { getRepository(any(), any()) } returns GitHubFakes.fakeRepository()
                    coEvery { getReadme(any(), any(), any<String>()) } returns
                        Result.success(
                            ReadmeContent(
                                markdown = "# Hello\n\nSimple README.",
                                html = null,
                                renderMode = ReadmeRenderMode.NATIVE,
                            ),
                        )
                }

            val state = viewModel(repoRepository).uiState.value

            val readmeState = (state as RepoDetailUiState.Success).readmeState
            assertEquals(
                ReadmeState.Loaded("# Hello\n\nSimple README.", ReadmeRenderMode.NATIVE),
                readmeState,
            )
        }

    @Test
    fun loadReadme_complexMarkdown_emitsLoadedWebView() =
        runTest {
            val repoRepository =
                mockk<RepoRepository> {
                    coEvery { getRepository(any(), any()) } returns GitHubFakes.fakeRepository()
                    coEvery { getReadme(any(), any(), any<String>()) } returns
                        Result.success(
                            ReadmeContent(
                                markdown = "```mermaid\ngraph TD; A-->B;\n```",
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
    fun retry_afterError_reloadsAndSucceeds() =
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
                        html = null,
                        renderMode = ReadmeRenderMode.NATIVE,
                    ),
                )

            viewModel.retry()

            val state = viewModel.uiState.value
            assertTrue(state is RepoDetailUiState.Success)
            assertEquals(
                ReadmeState.Loaded("# Hello", ReadmeRenderMode.NATIVE),
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
}
