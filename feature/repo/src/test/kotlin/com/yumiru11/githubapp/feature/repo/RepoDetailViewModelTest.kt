package com.yumiru11.githubapp.feature.repo

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.yumiru11.githubapp.core.data.model.Release
import com.yumiru11.githubapp.core.githubauth.auth.AuthState
import com.yumiru11.githubapp.core.githubauth.auth.OAuthSessionManager
import com.yumiru11.githubapp.core.markdown.webview.MarkdownThemeTokens
import com.yumiru11.githubapp.core.markdown.webview.RenderMode
import com.yumiru11.githubapp.core.testing.MainDispatcherRule
import com.yumiru11.githubapp.core.testing.fake.GitHubFakes
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
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
 * RepoDetailViewModel 单测（纯 JVM，MockK 桩 RepoRepository / RepoManagementRepository）。
 *
 * 覆盖：成功/404/网络错误 → UiState；README 一律 WEBVIEW（服务端 HTML / 离线 GFM 降级透传）；
 * README 404 → 空态；retry 重新加载。
 *
 * T12 仓库管理：登录态加载 Star/Watch 状态（游客跳过）；Star/Watch 乐观更新 → 失败回滚 + 事件；
 * Fork 错误码映射事件；Releases/Tags 懒加载幂等；Release 详情展开/收起；语言栏数据加载。
 */
class RepoDetailViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val savedStateHandle =
        SavedStateHandle(mapOf("owner" to "octocat", "repo" to "Hello-World"))

    private fun viewModel(
        repoRepository: RepoRepository,
        repoManagementRepository: RepoManagementRepository = mockkRepoManagement(),
        authStateValue: AuthState = AuthState.Anonymous,
    ): RepoDetailViewModel {
        val sessionManager =
            mockk<OAuthSessionManager> {
                every { authState } returns MutableStateFlow(authStateValue)
            }
        return RepoDetailViewModel(
            savedStateHandle = savedStateHandle,
            repoRepository = repoRepository,
            repoManagementRepository = repoManagementRepository,
            sessionManager = sessionManager,
        )
    }

    /** 默认桩：语言栏加载成功（空 Map），其余方法由各测试按需覆盖。 */
    private fun mockkRepoManagement(): RepoManagementRepository =
        mockk {
            coEvery { getLanguages(any(), any()) } returns Result.success(emptyMap())
        }

    private fun httpException(code: Int): HttpException =
        HttpException(Response.error<Any>(code, "error".toResponseBody("text/plain".toMediaType())))

    private fun repoRepositoryWithReadme(): RepoRepository =
        mockk {
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

    // ---- 基础加载（T9 既有） ----

    @Test
    fun loadRepoDetail_success_emitsSuccessState() =
        runTest {
            val repoRepository = repoRepositoryWithReadme()

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
            val repoRepository = repoRepositoryWithReadme()

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
            val repoRepository = repoRepositoryWithReadme()

            viewModel(repoRepository)

            coVerify(exactly = 1) {
                repoRepository.getReadme("octocat", "Hello-World", MarkdownThemeTokens.versionHash())
            }
        }

    // ---- T12：登录态与 Star/Watch 状态加载 ----

    @Test
    fun loadRepoDetail_loggedIn_loadsStarWatchStatus() =
        runTest {
            val repoManagementRepository =
                mockk<RepoManagementRepository> {
                    coEvery { getLanguages(any(), any()) } returns Result.success(emptyMap())
                    coEvery { isStarred("octocat", "Hello-World") } returns true
                    coEvery { isWatching("octocat", "Hello-World") } returns true
                }

            val state = viewModel(repoRepositoryWithReadme(), repoManagementRepository, AuthState.PAT).uiState.value

            val success = state as RepoDetailUiState.Success
            assertEquals(true, success.isLoggedIn)
            assertEquals(true, success.isStarred)
            assertEquals(true, success.isWatching)
        }

    @Test
    fun loadRepoDetail_guest_skipsStarWatchStatus() =
        runTest {
            val repoManagementRepository =
                mockk<RepoManagementRepository> {
                    coEvery { getLanguages(any(), any()) } returns Result.success(emptyMap())
                }

            val state = viewModel(repoRepositoryWithReadme(), repoManagementRepository).uiState.value

            val success = state as RepoDetailUiState.Success
            assertEquals(false, success.isLoggedIn)
            coVerify(exactly = 0) { repoManagementRepository.isStarred(any(), any()) }
            coVerify(exactly = 0) { repoManagementRepository.isWatching(any(), any()) }
        }

    @Test
    fun loadRepoDetail_success_loadsLanguages() =
        runTest {
            val repoManagementRepository =
                mockk<RepoManagementRepository> {
                    coEvery { getLanguages("octocat", "Hello-World") } returns
                        Result.success(mapOf("Kotlin" to 102400L))
                }

            val state = viewModel(repoRepositoryWithReadme(), repoManagementRepository).uiState.value

            assertEquals(
                mapOf("Kotlin" to 102400L),
                (state as RepoDetailUiState.Success).languages,
            )
        }

    // ---- T12：Star 乐观更新 ----

    @Test
    fun toggleStar_loggedIn_optimisticUpdateThenConfirm() =
        runTest {
            val repoManagementRepository =
                mockk<RepoManagementRepository> {
                    coEvery { getLanguages(any(), any()) } returns Result.success(emptyMap())
                    coEvery { isStarred(any(), any()) } returns false
                    coEvery { isWatching(any(), any()) } returns false
                    // 挂起请求：让乐观更新中间态可观测（UnconfinedTestDispatcher 下协程默认立即跑完）
                    coEvery { setStarred("octocat", "Hello-World", true) } coAnswers {
                        delay(1000)
                        Unit
                    }
                }
            val viewModel = viewModel(repoRepositoryWithReadme(), repoManagementRepository, AuthState.PAT)
            assertEquals(false, (viewModel.uiState.value as RepoDetailUiState.Success).isStarred)

            viewModel.toggleStar()

            // 乐观更新：立即翻转 + 防重入（请求仍在途）
            val optimistic = viewModel.uiState.value as RepoDetailUiState.Success
            assertEquals(true, optimistic.isStarred)
            assertEquals(RepoAction.STAR, optimistic.pendingAction)
            // 请求进行中：第二次点击被忽略
            viewModel.toggleStar()
            assertEquals(true, (viewModel.uiState.value as RepoDetailUiState.Success).isStarred)

            // 请求完成：pendingAction 清除
            advanceUntilIdle()
            val confirmed = viewModel.uiState.value as RepoDetailUiState.Success
            assertEquals(true, confirmed.isStarred)
            assertEquals(null, confirmed.pendingAction)
            coVerify(exactly = 1) { repoManagementRepository.setStarred("octocat", "Hello-World", true) }
        }

    @Test
    fun toggleStar_failure_rollsBackAndEmitsToggleFailed() =
        runTest {
            val repoManagementRepository =
                mockk<RepoManagementRepository> {
                    coEvery { getLanguages(any(), any()) } returns Result.success(emptyMap())
                    coEvery { isStarred(any(), any()) } returns false
                    coEvery { isWatching(any(), any()) } returns false
                    coEvery { setStarred(any(), any(), any()) } throws IOException("boom")
                }
            val viewModel = viewModel(repoRepositoryWithReadme(), repoManagementRepository, AuthState.PAT)

            viewModel.events.test {
                viewModel.toggleStar()

                assertEquals(RepoEvent.ToggleFailed, awaitItem())
                val rolledBack = viewModel.uiState.value as RepoDetailUiState.Success
                assertEquals(false, rolledBack.isStarred)
                assertEquals(null, rolledBack.pendingAction)
            }
        }

    @Test
    fun toggleStar_guest_ignored() =
        runTest {
            val repoManagementRepository =
                mockk<RepoManagementRepository> {
                    coEvery { getLanguages(any(), any()) } returns Result.success(emptyMap())
                }
            val viewModel = viewModel(repoRepositoryWithReadme(), repoManagementRepository)
            val before = (viewModel.uiState.value as RepoDetailUiState.Success).isStarred

            viewModel.toggleStar()

            assertEquals(before, (viewModel.uiState.value as RepoDetailUiState.Success).isStarred)
            coVerify(exactly = 0) { repoManagementRepository.setStarred(any(), any(), any()) }
        }

    // ---- T12：Watch 乐观更新 ----

    @Test
    fun toggleWatch_loggedIn_optimisticUpdateThenConfirm() =
        runTest {
            val repoManagementRepository =
                mockk<RepoManagementRepository> {
                    coEvery { getLanguages(any(), any()) } returns Result.success(emptyMap())
                    coEvery { isStarred(any(), any()) } returns false
                    coEvery { isWatching(any(), any()) } returns false
                    // 挂起请求：让乐观更新中间态可观测
                    coEvery { setWatching("octocat", "Hello-World", true) } coAnswers {
                        delay(1000)
                        Unit
                    }
                }
            val viewModel = viewModel(repoRepositoryWithReadme(), repoManagementRepository, AuthState.PAT)
            assertEquals(false, (viewModel.uiState.value as RepoDetailUiState.Success).isWatching)

            viewModel.toggleWatch()

            val optimistic = viewModel.uiState.value as RepoDetailUiState.Success
            assertEquals(true, optimistic.isWatching)
            assertEquals(RepoAction.WATCH, optimistic.pendingAction)

            advanceUntilIdle()
            val confirmed = viewModel.uiState.value as RepoDetailUiState.Success
            assertEquals(true, confirmed.isWatching)
            assertEquals(null, confirmed.pendingAction)
            coVerify(exactly = 1) { repoManagementRepository.setWatching("octocat", "Hello-World", true) }
        }

    @Test
    fun toggleWatch_failure_rollsBackAndEmitsToggleFailed() =
        runTest {
            val repoManagementRepository =
                mockk<RepoManagementRepository> {
                    coEvery { getLanguages(any(), any()) } returns Result.success(emptyMap())
                    coEvery { isStarred(any(), any()) } returns false
                    coEvery { isWatching(any(), any()) } returns false
                    coEvery { setWatching(any(), any(), any()) } throws IOException("boom")
                }
            val viewModel = viewModel(repoRepositoryWithReadme(), repoManagementRepository, AuthState.PAT)

            viewModel.events.test {
                viewModel.toggleWatch()

                assertEquals(RepoEvent.ToggleFailed, awaitItem())
                val rolledBack = viewModel.uiState.value as RepoDetailUiState.Success
                assertEquals(false, rolledBack.isWatching)
                assertEquals(null, rolledBack.pendingAction)
            }
        }

    // ---- T12：Fork ----

    @Test
    fun fork_success_emitsForked() =
        runTest {
            val repoManagementRepository =
                mockk<RepoManagementRepository> {
                    coEvery { getLanguages(any(), any()) } returns Result.success(emptyMap())
                    coEvery { isStarred(any(), any()) } returns false
                    coEvery { isWatching(any(), any()) } returns false
                    coEvery { fork("octocat", "Hello-World") } returns Result.success(GitHubFakes.fakeRepository())
                }
            val viewModel = viewModel(repoRepositoryWithReadme(), repoManagementRepository, AuthState.PAT)

            viewModel.events.test {
                viewModel.fork()

                assertEquals(RepoEvent.Forked, awaitItem())
                assertEquals(null, (viewModel.uiState.value as RepoDetailUiState.Success).pendingAction)
            }
        }

    @Test
    fun fork_permissionDenied_emitsForkPermissionDenied() =
        runTest {
            val repoManagementRepository =
                mockk<RepoManagementRepository> {
                    coEvery { getLanguages(any(), any()) } returns Result.success(emptyMap())
                    coEvery { isStarred(any(), any()) } returns false
                    coEvery { isWatching(any(), any()) } returns false
                    coEvery { fork(any(), any()) } returns Result.failure(httpException(403))
                }
            val viewModel = viewModel(repoRepositoryWithReadme(), repoManagementRepository, AuthState.PAT)

            viewModel.events.test {
                viewModel.fork()

                assertEquals(RepoEvent.ForkPermissionDenied, awaitItem())
                assertEquals(null, (viewModel.uiState.value as RepoDetailUiState.Success).pendingAction)
            }
        }

    @Test
    fun fork_alreadyExists_emitsForkAlreadyExists() =
        runTest {
            val repoManagementRepository =
                mockk<RepoManagementRepository> {
                    coEvery { getLanguages(any(), any()) } returns Result.success(emptyMap())
                    coEvery { isStarred(any(), any()) } returns false
                    coEvery { isWatching(any(), any()) } returns false
                    coEvery { fork(any(), any()) } returns Result.failure(httpException(422))
                }
            val viewModel = viewModel(repoRepositoryWithReadme(), repoManagementRepository, AuthState.PAT)

            viewModel.events.test {
                viewModel.fork()

                assertEquals(RepoEvent.ForkAlreadyExists, awaitItem())
            }
        }

    @Test
    fun fork_otherFailure_emitsForkFailed() =
        runTest {
            val repoManagementRepository =
                mockk<RepoManagementRepository> {
                    coEvery { getLanguages(any(), any()) } returns Result.success(emptyMap())
                    coEvery { isStarred(any(), any()) } returns false
                    coEvery { isWatching(any(), any()) } returns false
                    coEvery { fork(any(), any()) } returns Result.failure(IOException("boom"))
                }
            val viewModel = viewModel(repoRepositoryWithReadme(), repoManagementRepository, AuthState.PAT)

            viewModel.events.test {
                viewModel.fork()

                assertEquals(RepoEvent.ForkFailed, awaitItem())
            }
        }

    @Test
    fun fork_guest_ignored() =
        runTest {
            val repoManagementRepository =
                mockk<RepoManagementRepository> {
                    coEvery { getLanguages(any(), any()) } returns Result.success(emptyMap())
                }
            val viewModel = viewModel(repoRepositoryWithReadme(), repoManagementRepository)

            viewModel.fork()

            coVerify(exactly = 0) { repoManagementRepository.fork(any(), any()) }
        }

    // ---- T12：Releases/Tags 懒加载 ----

    @Test
    fun ensureReleasesLoaded_idle_loadsOnce() =
        runTest {
            val release = Release(id = 1, tagName = "v1.0.0")
            val repoManagementRepository =
                mockk<RepoManagementRepository> {
                    coEvery { getLanguages(any(), any()) } returns Result.success(emptyMap())
                    coEvery { isStarred(any(), any()) } returns false
                    coEvery { isWatching(any(), any()) } returns false
                    coEvery { getReleases("octocat", "Hello-World") } returns Result.success(listOf(release))
                }
            val viewModel = viewModel(repoRepositoryWithReadme(), repoManagementRepository, AuthState.PAT)
            assertEquals(
                ReleasesState.Idle,
                (viewModel.uiState.value as RepoDetailUiState.Success).releasesState,
            )

            viewModel.ensureReleasesLoaded()
            assertEquals(
                ReleasesState.Loaded(listOf(release)),
                (viewModel.uiState.value as RepoDetailUiState.Success).releasesState,
            )

            // 幂等：Loaded 后不重复拉取
            viewModel.ensureReleasesLoaded()
            coVerify(exactly = 1) { repoManagementRepository.getReleases(any(), any()) }
        }

    @Test
    fun ensureReleasesLoaded_failure_emitsErrorState() =
        runTest {
            val repoManagementRepository =
                mockk<RepoManagementRepository> {
                    coEvery { getLanguages(any(), any()) } returns Result.success(emptyMap())
                    coEvery { getReleases(any(), any()) } returns Result.failure(IOException("boom"))
                }
            val viewModel = viewModel(repoRepositoryWithReadme(), repoManagementRepository)

            viewModel.ensureReleasesLoaded()

            assertEquals(
                ReleasesState.Error(RepoErrorType.NETWORK),
                (viewModel.uiState.value as RepoDetailUiState.Success).releasesState,
            )
        }

    @Test
    fun ensureTagsLoaded_idle_loadsOnce() =
        runTest {
            val repoManagementRepository =
                mockk<RepoManagementRepository> {
                    coEvery { getLanguages(any(), any()) } returns Result.success(emptyMap())
                    coEvery { getTags("octocat", "Hello-World") } returns
                        Result.success(
                            listOf(
                                com.yumiru11.githubapp.core.data.model
                                    .Tag(name = "v1.0.0", commitSha = "abc"),
                            ),
                        )
                }
            val viewModel = viewModel(repoRepositoryWithReadme(), repoManagementRepository)

            viewModel.ensureTagsLoaded()

            val tags = (viewModel.uiState.value as RepoDetailUiState.Success).tagsState
            assertTrue(tags is TagsState.Loaded)
            assertEquals("v1.0.0", (tags as TagsState.Loaded).tags.single().name)

            viewModel.ensureTagsLoaded()
            coVerify(exactly = 1) { repoManagementRepository.getTags(any(), any()) }
        }

    // ---- T12：Release 详情展开/收起 ----

    @Test
    fun loadReleaseDetail_success_expandsAndCollapses() =
        runTest {
            val release = Release(id = 1, tagName = "v1.0.0", body = "Initial release")
            val repoManagementRepository =
                mockk<RepoManagementRepository> {
                    coEvery { getLanguages(any(), any()) } returns Result.success(emptyMap())
                    coEvery { getReleases(any(), any()) } returns Result.success(listOf(release))
                    coEvery { getRelease("octocat", "Hello-World", 1) } returns Result.success(release)
                }
            val viewModel = viewModel(repoRepositoryWithReadme(), repoManagementRepository)
            viewModel.ensureReleasesLoaded()

            viewModel.loadReleaseDetail(1)

            val expanded = viewModel.uiState.value as RepoDetailUiState.Success
            assertEquals(1L, expanded.expandedReleaseId)
            assertEquals(ReleaseDetailState.Loaded(release), expanded.releaseDetailState)

            viewModel.collapseReleaseDetail()

            val collapsed = viewModel.uiState.value as RepoDetailUiState.Success
            assertEquals(null, collapsed.expandedReleaseId)
            assertEquals(ReleaseDetailState.Idle, collapsed.releaseDetailState)
        }

    @Test
    fun loadReleaseDetail_failure_emitsErrorState() =
        runTest {
            val release = Release(id = 1, tagName = "v1.0.0")
            val repoManagementRepository =
                mockk<RepoManagementRepository> {
                    coEvery { getLanguages(any(), any()) } returns Result.success(emptyMap())
                    coEvery { getReleases(any(), any()) } returns Result.success(listOf(release))
                    coEvery { getRelease(any(), any(), any()) } returns Result.failure(IOException("boom"))
                }
            val viewModel = viewModel(repoRepositoryWithReadme(), repoManagementRepository)
            viewModel.ensureReleasesLoaded()

            viewModel.loadReleaseDetail(1)

            assertEquals(
                ReleaseDetailState.Error(RepoErrorType.NETWORK),
                (viewModel.uiState.value as RepoDetailUiState.Success).releaseDetailState,
            )
        }
}
