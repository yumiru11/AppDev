package com.yumiru11.githubapp.feature.repo

import com.yumiru11.githubapp.core.database.dao.CachedReadmeDao
import com.yumiru11.githubapp.core.database.entity.CachedReadmeEntity
import com.yumiru11.githubapp.core.githubrest.api.ReadmeApi
import com.yumiru11.githubapp.core.githubrest.api.RepositoryApi
import com.yumiru11.githubapp.core.githubrest.model.ReadmeDto
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.util.Base64

/**
 * RepoRepository 单测（纯 JVM，MockK 桩 REST API 与缓存 DAO）。
 *
 * 覆盖：FeatureDetector 接线（普通 → NATIVE 且相对链接重写、复杂 → WEBVIEW）；
 * 空白内容 → WEBVIEW；404 → failure；双 key 缓存（命中跳过网络、contentHash 相同仅更新主题版本）。
 */
class RepoRepositoryTest {
    private val repositoryApi = mockk<RepositoryApi>()
    private val readmeApi = mockk<ReadmeApi>()
    private val cachedReadmeDao = mockk<CachedReadmeDao>()

    private val repository =
        RepoRepository(
            repositoryApi = repositoryApi,
            readmeApi = readmeApi,
            cachedReadmeDao = cachedReadmeDao,
        )

    private fun readmeDto(
        markdown: String,
        sha: String = "abc123",
        downloadUrl: String = "https://raw.githubusercontent.com/octocat/Hello-World/main/README.md",
    ): ReadmeDto =
        ReadmeDto(
            name = "README.md",
            path = "README.md",
            sha = sha,
            content = Base64.getEncoder().encodeToString(markdown.toByteArray()),
            encoding = "base64",
            downloadUrl = downloadUrl,
        )

    private fun httpException(code: Int): HttpException =
        HttpException(Response.error<Any>(code, "error".toResponseBody("text/plain".toMediaType())))

    private fun cachedEntity(themeVersion: String = "th-v1"): CachedReadmeEntity =
        CachedReadmeEntity(
            owner = "octocat",
            repo = "Hello-World",
            contentHash = "abc123",
            themeVersion = themeVersion,
            html = "<p>cached html</p>",
            updatedAt = 0L,
        )

    @Test
    fun getReadme_simpleMarkdown_returnsNativeWithRewrittenLinks() =
        runTest {
            val markdown = "# Hello\n\nSee [guide](./docs/guide.md) and ![logo](assets/logo.png)"
            coEvery { readmeApi.getReadmeMeta("octocat", "Hello-World") } returns readmeDto(markdown)
            coEvery { cachedReadmeDao.getByOwnerAndRepo(any(), any()) } returns null

            val result = repository.getReadme("octocat", "Hello-World", "th-v1")

            assertTrue(result.isSuccess)
            val content = result.getOrThrow()
            assertEquals(ReadmeRenderMode.NATIVE, content.renderMode)
            assertNull(content.html)
            // 相对链接已按 raw 基准重写为绝对 URL
            assertTrue(content.markdown.contains("https://raw.githubusercontent.com/octocat/Hello-World/main/docs/guide.md"))
            assertTrue(content.markdown.contains("https://raw.githubusercontent.com/octocat/Hello-World/main/assets/logo.png"))
            // 原生路径不触碰 HTML 获取
            coVerify(exactly = 0) { readmeApi.getReadmeHtml(any(), any()) }
        }

    @Test
    fun getReadme_mermaidMarkdown_returnsWebViewWithHtml() =
        runTest {
            val markdown = "# Diagram\n\n```mermaid\ngraph TD; A-->B;\n```"
            coEvery { readmeApi.getReadmeMeta("octocat", "Hello-World") } returns readmeDto(markdown)
            coEvery { cachedReadmeDao.getByOwnerAndRepo(any(), any()) } returns null
            coEvery { cachedReadmeDao.upsert(any()) } returns Unit
            coEvery { readmeApi.getReadmeHtml("octocat", "Hello-World") } returns
                "<div>server html</div>".toResponseBody("text/html".toMediaType())

            val result = repository.getReadme("octocat", "Hello-World", "th-v1")

            assertTrue(result.isSuccess)
            val content = result.getOrThrow()
            assertEquals(ReadmeRenderMode.WEBVIEW, content.renderMode)
            assertEquals("<div>server html</div>", content.html)
        }

    @Test
    fun getReadme_blankContent_returnsWebView() =
        runTest {
            // 内容解码失败（content = null）→ 无 markdown 可探测 → 走 WebView 服务端 HTML
            coEvery { readmeApi.getReadmeMeta(any(), any()) } returns
                ReadmeDto(
                    name = "README.md",
                    path = "README.md",
                    sha = "abc123",
                    content = null,
                    encoding = "base64",
                )
            coEvery { cachedReadmeDao.getByOwnerAndRepo(any(), any()) } returns null
            coEvery { cachedReadmeDao.upsert(any()) } returns Unit
            coEvery { readmeApi.getReadmeHtml(any(), any()) } returns
                "<p>html</p>".toResponseBody("text/html".toMediaType())

            val result = repository.getReadme("octocat", "Hello-World", "th-v1")

            assertTrue(result.isSuccess)
            assertEquals(ReadmeRenderMode.WEBVIEW, result.getOrThrow().renderMode)
        }

    @Test
    fun getReadme_notFound_returnsFailure() =
        runTest {
            coEvery { readmeApi.getReadmeMeta(any(), any()) } throws httpException(404)

            val result = repository.getReadme("octocat", "Hello-World", "th-v1")

            assertTrue(result.isFailure)
        }

    @Test
    fun getReadme_cachedHtmlHit_skipsNetworkFetch() =
        runTest {
            // mermaid 内容 → WEBVIEW 路径 → getReadmeHtml 命中缓存（themeVersion 匹配）→ 不再请求网络 HTML
            val markdown = "```mermaid\ngraph TD; A-->B;\n```"
            coEvery { readmeApi.getReadmeMeta(any(), any()) } returns readmeDto(markdown)
            coEvery { cachedReadmeDao.getByOwnerAndRepo("octocat", "Hello-World") } returns cachedEntity()
            coEvery { cachedReadmeDao.upsert(any()) } returns Unit

            val result = repository.getReadme("octocat", "Hello-World", "th-v1")

            assertTrue(result.isSuccess)
            assertEquals("<p>cached html</p>", result.getOrThrow().html)
            coVerify(exactly = 0) { readmeApi.getReadmeHtml(any(), any()) }
            coVerify(exactly = 0) { cachedReadmeDao.upsert(any()) }
        }

    @Test
    fun getReadme_contentHashMatch_updatesThemeVersionOnly() =
        runTest {
            // 缓存存在但主题版本过期；内容 sha 相同 → 仅更新 themeVersion，不重新请求 HTML
            val markdown = "```mermaid\ngraph TD; A-->B;\n```"
            coEvery { readmeApi.getReadmeMeta(any(), any()) } returns readmeDto(markdown)
            coEvery { cachedReadmeDao.getByOwnerAndRepo("octocat", "Hello-World") } returns cachedEntity(themeVersion = "th-old")
            coEvery { cachedReadmeDao.upsert(any()) } returns Unit

            val result = repository.getReadme("octocat", "Hello-World", "th-v1")

            assertTrue(result.isSuccess)
            assertEquals("<p>cached html</p>", result.getOrThrow().html)
            coVerify(exactly = 0) { readmeApi.getReadmeHtml(any(), any()) }
            coVerify {
                cachedReadmeDao.upsert(
                    match { entity ->
                        entity.themeVersion == "th-v1" && entity.contentHash == "abc123" && entity.html == "<p>cached html</p>"
                    },
                )
            }
        }
}
