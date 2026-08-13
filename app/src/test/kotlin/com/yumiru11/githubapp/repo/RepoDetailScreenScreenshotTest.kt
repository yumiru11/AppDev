package com.yumiru11.githubapp.repo

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import com.github.takahirom.roborazzi.captureRoboImage
import com.yumiru11.githubapp.core.data.model.Repository
import com.yumiru11.githubapp.feature.repo.RepoDetailScreen
import com.yumiru11.githubapp.feature.repo.RepoDetailViewModel
import com.yumiru11.githubapp.feature.repo.RepoRepository
import io.mockk.coEvery
import io.mockk.mockk
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant

/**
 * RepoDetailScreen 截图基准测试（Light / Dark 两态，Loading + Success 状态）。
 *
 * 使用 MockK 创建 [RepoDetailViewModel] 绕开 Hilt 装配，聚焦 UI 快照验证。
 * 基准 PNG：app/src/test/screenshots/RepoDetailScreen_*.png（入库）。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "+w1080dp-h1920dp")
class RepoDetailScreenScreenshotTest {
    private val fakeRepo =
        Repository(
            ownerLogin = "octocat",
            name = "Hello-World",
            description = "A sample repository for testing",
            stargazerCount = 42,
            forkCount = 7,
            language = "Kotlin",
            defaultBranch = "main",
            updatedAt = Instant.parse("2026-08-01T00:00:00Z"),
        )

    /** 截图 helper */
    private fun captureScreenshot(
        name: String,
        darkTheme: Boolean,
        content: @Composable () -> Unit,
    ) {
        captureRoboImage("src/test/screenshots/$name.png") {
            MaterialTheme(colorScheme = if (darkTheme) darkColorScheme() else lightColorScheme()) {
                Box(modifier = Modifier.size(width = 1080.dp, height = 1920.dp)) {
                    content()
                }
            }
        }
    }

    @Test
    fun repoDetailScreen_loading_lightTheme_matchesBaseline() {
        val repoRepository =
            mockk<RepoRepository> {
                // 不 stub — ViewModel 调用时将抛出异常，UI 显示 Loading 后转为 Error
            }
        val viewModel =
            RepoDetailViewModel(
                savedStateHandle = SavedStateHandle(mapOf("owner" to "octocat", "repo" to "Hello-World")),
                repoRepository = repoRepository,
            )

        captureScreenshot(name = "RepoDetailScreen_loading_light", darkTheme = false) {
            RepoDetailScreen(
                owner = "octocat",
                repo = "Hello-World",
                onBackClick = {},
                viewModel = viewModel,
            )
        }
    }

    @Test
    fun repoDetailScreen_loading_darkTheme_matchesBaseline() {
        val repoRepository =
            mockk<RepoRepository> {
                // 不 stub — ViewModel 调用时将抛出异常，UI 显示 Loading 后转为 Error
            }
        val viewModel =
            RepoDetailViewModel(
                savedStateHandle = SavedStateHandle(mapOf("owner" to "octocat", "repo" to "Hello-World")),
                repoRepository = repoRepository,
            )

        captureScreenshot(name = "RepoDetailScreen_loading_dark", darkTheme = true) {
            RepoDetailScreen(
                owner = "octocat",
                repo = "Hello-World",
                onBackClick = {},
                viewModel = viewModel,
            )
        }
    }

    @Test
    fun repoDetailScreen_success_lightTheme_matchesBaseline() {
        val repoRepository =
            mockk<RepoRepository> {
                coEvery { getRepository(any(), any()) } returns fakeRepo
                coEvery { getReadmeHtml(any(), any(), any<String>()) } returns Result.success("<p>README content</p>")
            }
        val viewModel =
            RepoDetailViewModel(
                savedStateHandle = SavedStateHandle(mapOf("owner" to "octocat", "repo" to "Hello-World")),
                repoRepository = repoRepository,
            )

        captureScreenshot(name = "RepoDetailScreen_success_light", darkTheme = false) {
            RepoDetailScreen(
                owner = "octocat",
                repo = "Hello-World",
                onBackClick = {},
                viewModel = viewModel,
            )
        }
    }

    @Test
    fun repoDetailScreen_success_darkTheme_matchesBaseline() {
        val repoRepository =
            mockk<RepoRepository> {
                coEvery { getRepository(any(), any()) } returns fakeRepo
                coEvery { getReadmeHtml(any(), any(), any<String>()) } returns Result.success("<p>README content</p>")
            }
        val viewModel =
            RepoDetailViewModel(
                savedStateHandle = SavedStateHandle(mapOf("owner" to "octocat", "repo" to "Hello-World")),
                repoRepository = repoRepository,
            )

        captureScreenshot(name = "RepoDetailScreen_success_dark", darkTheme = true) {
            RepoDetailScreen(
                owner = "octocat",
                repo = "Hello-World",
                onBackClick = {},
                viewModel = viewModel,
            )
        }
    }
}
