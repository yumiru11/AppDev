package com.yumiru11.githubapp.feature.repo

import com.yumiru11.githubapp.core.database.dao.CachedReadmeDao
import com.yumiru11.githubapp.core.database.entity.CachedReadmeEntity
import com.yumiru11.githubapp.core.githubrest.api.ContentApi
import com.yumiru11.githubapp.core.githubrest.api.GitTreeApi
import com.yumiru11.githubapp.core.githubrest.api.ReadmeApi
import com.yumiru11.githubapp.core.githubrest.api.RepositoryApi
import com.yumiru11.githubapp.core.githubrest.model.FileContentDto
import com.yumiru11.githubapp.core.githubrest.model.GitTreeResponseDto
import com.yumiru11.githubapp.core.githubrest.model.ReadmeDto
import com.yumiru11.githubapp.core.githubrest.model.TreeItemDto
import com.yumiru11.githubapp.core.markdown.webview.RenderMode
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
import java.io.IOException
import java.util.Base64

/**
 * RepoRepository 单测（纯 JVM，MockK 桩 REST API 与缓存 DAO）。
 *
 * 覆盖：README 一律 WEBVIEW（服务端 HTML）；服务端失败降级离线 GFM；
 * 空白内容 → WEBVIEW；404 → failure；双 key 缓存（命中跳过网络、contentHash 相同仅更新主题版本）。
 */
class RepoRepositoryTest {
    private val repositoryApi = mockk<RepositoryApi>()
    private val readmeApi = mockk<ReadmeApi>()
    private val cachedReadmeDao = mockk<CachedReadmeDao>()
    private val gitTreeApi = mockk<GitTreeApi>()
    private val contentApi = mockk<ContentApi>()

    private val repository =
        RepoRepository(
            repositoryApi = repositoryApi,
            readmeApi = readmeApi,
            cachedReadmeDao = cachedReadmeDao,
            gitTreeApi = gitTreeApi,
            contentApi = contentApi,
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
    fun getReadme_simpleMarkdown_alwaysReturnsWebViewHtml() =
        runTest {
            val markdown = "# Hello\n\nSee [guide](./docs/guide.md) and ![logo](assets/logo.png)"
            coEvery { readmeApi.getReadmeMeta("octocat", "Hello-World") } returns readmeDto(markdown)
            coEvery { cachedReadmeDao.getByOwnerAndRepo(any(), any()) } returns null
            coEvery { cachedReadmeDao.upsert(any()) } returns Unit
            coEvery { readmeApi.getReadmeHtml(any(), any()) } returns
                "<div>server html</div>".toResponseBody("text/html".toMediaType())

            val result = repository.getReadme("octocat", "Hello-World", "th-v1")

            assertTrue(result.isSuccess)
            val content = result.getOrThrow()
            assertEquals(ReadmeRenderMode.WEBVIEW, content.renderMode)
            assertEquals("<div>server html</div>", content.html)
            assertEquals(RenderMode.SERVER_HTML, content.webViewRenderMode)
            // 无论内容是否简单，一律请求服务端 HTML
            coVerify(exactly = 1) { readmeApi.getReadmeHtml("octocat", "Hello-World") }
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

    @Test
    fun getReadme_htmlResponseIsJson_fallsBackToRenderedMarkdown() =
        runTest {
            // 2026-08-14 真机走查修复：GitHub 对部分 README 无视 html Accept 返回 API JSON，
            // 直接 .string() 当 HTML 会显示原始 JSON（openchamber 案例）——须回退 markdown 渲染
            val markdown = "```mermaid\ngraph TD; A-->B;\n```"
            val jsonBody = """{"name":"README.md","content":"${Base64.getEncoder().encodeToString(
                markdown.toByteArray(),
            )}","encoding":"base64"}"""
            coEvery { readmeApi.getReadmeMeta(any(), any()) } returns readmeDto(markdown)
            coEvery { cachedReadmeDao.getByOwnerAndRepo("octocat", "Hello-World") } returns null
            coEvery { cachedReadmeDao.upsert(any()) } returns Unit
            coEvery { readmeApi.getReadmeHtml(any(), any()) } returns
                jsonBody.toResponseBody("application/json".toMediaType())
            coEvery { readmeApi.renderMarkdown(any()) } returns
                "<pre><code class=\"language-mermaid\">graph TD; A-->B;</code></pre>".toResponseBody("text/html".toMediaType())

            val result = repository.getReadme("octocat", "Hello-World", "th-v1")

            assertTrue(result.isSuccess)
            assertEquals("<pre><code class=\"language-mermaid\">graph TD; A-->B;</code></pre>", result.getOrThrow().html)
            coVerify(exactly = 1) { readmeApi.renderMarkdown(any()) }
        }

    @Test
    fun getReadme_htmlResponseIsHtml_usesHtmlDirectly() =
        runTest {
            val markdown = "```mermaid\ngraph TD; A-->B;\n```"
            coEvery { readmeApi.getReadmeMeta(any(), any()) } returns readmeDto(markdown)
            coEvery { cachedReadmeDao.getByOwnerAndRepo("octocat", "Hello-World") } returns null
            coEvery { cachedReadmeDao.upsert(any()) } returns Unit
            coEvery { readmeApi.getReadmeHtml(any(), any()) } returns
                "<p>rendered html</p>".toResponseBody("text/html".toMediaType())

            val result = repository.getReadme("octocat", "Hello-World", "th-v1")

            assertTrue(result.isSuccess)
            assertEquals("<p>rendered html</p>", result.getOrThrow().html)
            coVerify(exactly = 0) { readmeApi.renderMarkdown(any()) }
        }

    @Test
    fun getReadmeHtml_themeVersionMismatchContentChanged_refetchesHtmlAndUpserts() =
        runTest {
            // 缓存存在但主题版本过期且内容 sha 已变（README 更新过）→ 缓存 miss，重新拉取 HTML 并落新缓存
            val markdown = "```mermaid\ngraph TD; A-->B;\n```"
            coEvery { readmeApi.getReadmeMeta(any(), any()) } returns readmeDto(markdown, sha = "newsha")
            coEvery { cachedReadmeDao.getByOwnerAndRepo("octocat", "Hello-World") } returns cachedEntity(themeVersion = "th-old")
            coEvery { cachedReadmeDao.upsert(any()) } returns Unit
            coEvery { readmeApi.getReadmeHtml(any(), any()) } returns
                "<p>fresh html</p>".toResponseBody("text/html".toMediaType())

            val result = repository.getReadmeHtml("octocat", "Hello-World", "th-v1", readmeDto(markdown, sha = "newsha"))

            assertTrue(result.isSuccess)
            assertEquals("<p>fresh html</p>", result.getOrThrow())
            coVerify(exactly = 1) { readmeApi.getReadmeHtml("octocat", "Hello-World") }
            coVerify {
                cachedReadmeDao.upsert(
                    match { entity ->
                        entity.themeVersion == "th-v1" &&
                            entity.contentHash == "newsha" &&
                            entity.html == "<p>fresh html</p>"
                    },
                )
            }
        }

    @Test
    fun getReadmeHtml_networkFailureWithStaleCache_returnsStaleCachedHtml() =
        runTest {
            // 三级降级（Tier 3）：主题版本过期 + 内容已变 + 网络拉取失败 → 返回过期缓存 HTML
            val markdown = "```mermaid\ngraph TD; A-->B;\n```"
            coEvery { readmeApi.getReadmeMeta(any(), any()) } returns readmeDto(markdown, sha = "newsha")
            coEvery { cachedReadmeDao.getByOwnerAndRepo("octocat", "Hello-World") } returns cachedEntity(themeVersion = "th-old")
            coEvery { readmeApi.getReadmeHtml(any(), any()) } throws IOException("network down")

            val result = repository.getReadmeHtml("octocat", "Hello-World", "th-v1", readmeDto(markdown, sha = "newsha"))

            assertTrue(result.isSuccess)
            assertEquals("<p>cached html</p>", result.getOrThrow())
        }

    @Test
    fun getReadmeHtml_networkFailureWithoutCache_returnsFailure() =
        runTest {
            // 无缓存可降级 → 网络失败原样上抛（VM 映射为 NETWORK 错误态）
            val markdown = "```mermaid\ngraph TD; A-->B;\n```"
            coEvery { readmeApi.getReadmeMeta(any(), any()) } returns readmeDto(markdown, sha = "newsha")
            coEvery { cachedReadmeDao.getByOwnerAndRepo(any(), any()) } returns null
            coEvery { readmeApi.getReadmeHtml(any(), any()) } throws IOException("network down")

            val result = repository.getReadmeHtml("octocat", "Hello-World", "th-v1", readmeDto(markdown, sha = "newsha"))

            assertTrue(result.isFailure)
        }

    @Test
    fun getReadme_htmlFetchFailure_returnsOfflineMarkdownFallback() =
        runTest {
            // Task B 降级：服务端 HTML 获取失败 → 返回原始 markdown，renderMode 仍 WEBVIEW，
            // 由 WebView 离线 markdown-it 渲染（WebViewHtmlBuilder.build(rawMarkdown)）
            val markdown = "# Hello\n\nSee [guide](./docs/guide.md)"
            coEvery { readmeApi.getReadmeMeta(any(), any()) } returns readmeDto(markdown)
            coEvery { cachedReadmeDao.getByOwnerAndRepo(any(), any()) } returns null
            coEvery { readmeApi.getReadmeHtml(any(), any()) } throws IOException("network down")

            val result = repository.getReadme("octocat", "Hello-World", "th-v1")

            assertTrue(result.isSuccess)
            val content = result.getOrThrow()
            assertEquals(ReadmeRenderMode.WEBVIEW, content.renderMode)
            assertEquals(markdown, content.html)
            assertEquals(RenderMode.OFFLINE_MARKDOWN_IT, content.webViewRenderMode)
        }

    @Test
    fun getTree_validResponse_buildsRootNodes() =
        runTest {
            coEvery { gitTreeApi.getTree("octocat", "Hello-World", "main") } returns
                GitTreeResponseDto(
                    sha = "rootsha",
                    tree =
                        listOf(
                            TreeItemDto(path = "README.md", type = "blob", sha = "s1", size = 10),
                            TreeItemDto(path = "src", type = "tree", sha = "s2"),
                        ),
                )

            val result = repository.getTree("octocat", "Hello-World", "main")

            assertTrue(result.isSuccess)
            val nodes = result.getOrThrow()
            assertEquals(2, nodes.size)
            // 排序：目录（src）在前，文件（README.md）在后
            assertTrue(nodes[0].isDirectory)
            assertEquals("src", nodes[0].path)
            assertEquals("s2", nodes[0].sha)
            assertEquals("README.md", nodes[1].path)
            coVerify(exactly = 1) { gitTreeApi.getTree("octocat", "Hello-World", "main") }
        }

    @Test
    fun getTree_apiFailure_returnsFailure() =
        runTest {
            coEvery { gitTreeApi.getTree(any(), any(), any()) } throws IOException("network down")

            val result = repository.getTree("octocat", "Hello-World", "main")

            assertTrue(result.isFailure)
        }

    @Test
    fun getChildTree_validResponse_prependsParentPath() =
        runTest {
            coEvery { gitTreeApi.getTree("octocat", "Hello-World", "s2") } returns
                GitTreeResponseDto(
                    tree =
                        listOf(
                            TreeItemDto(path = "Main.kt", type = "blob", sha = "s3"),
                            TreeItemDto(path = "util", type = "tree", sha = "s4"),
                        ),
                )

            val result = repository.getChildTree("octocat", "Hello-World", "s2", parentPath = "src")

            assertTrue(result.isSuccess)
            val nodes = result.getOrThrow()
            // 排序：目录（util）在前，文件（Main.kt）在后
            assertEquals(listOf("src/util", "src/Main.kt"), nodes.map { it.path })
        }

    @Test
    fun getFileContent_codeFile_decodesAndClassifiesCode() =
        runTest {
            val source = "fun main() = println(\"hi\")"
            coEvery { contentApi.getFileContent("octocat", "Hello-World", "src/Main.kt", "main") } returns
                FileContentDto(
                    name = "Main.kt",
                    path = "src/Main.kt",
                    size = source.length.toLong(),
                    content = Base64.getEncoder().encodeToString(source.toByteArray()),
                    encoding = "base64",
                )

            val result = repository.getFileContent("octocat", "Hello-World", "src/Main.kt", "main")

            assertTrue(result.isSuccess)
            val data = result.getOrThrow()
            assertEquals(FileKind.CODE, data.kind)
            assertEquals(source, data.text)
            coVerify(exactly = 1) { contentApi.getFileContent("octocat", "Hello-World", "src/Main.kt", "main") }
        }

    @Test
    fun getFileContent_markdownFile_classifiesMarkdown() =
        runTest {
            val source = "# Title\n\nbody"
            coEvery { contentApi.getFileContent(any(), any(), any(), any()) } returns
                FileContentDto(
                    name = "README.md",
                    path = "README.md",
                    size = source.length.toLong(),
                    content = Base64.getEncoder().encodeToString(source.toByteArray()),
                    encoding = "base64",
                )

            val result = repository.getFileContent("octocat", "Hello-World", "README.md", null)

            assertTrue(result.isSuccess)
            assertEquals(FileKind.MARKDOWN, result.getOrThrow().kind)
            assertEquals(source, result.getOrThrow().text)
        }

    @Test
    fun getFileContent_largeFile_classifiesTooLargeWithoutText() =
        runTest {
            coEvery { contentApi.getFileContent(any(), any(), any(), any()) } returns
                FileContentDto(
                    name = "big.bin",
                    path = "big.bin",
                    size = FileClassifier.LARGE_FILE_LIMIT_BYTES + 1,
                    content = "",
                    encoding = "base64",
                )

            val result = repository.getFileContent("octocat", "Hello-World", "big.bin", "main")

            assertTrue(result.isSuccess)
            val data = result.getOrThrow()
            assertEquals(FileKind.TOO_LARGE, data.kind)
            assertNull(data.text)
        }

    @Test
    fun getFileContent_binaryFile_classifiesBinaryWithoutText() =
        runTest {
            val binaryBytes = byteArrayOf(0x50, 0x4B, 0x03, 0x04, 0x00, 0x00, 0x00)
            coEvery { contentApi.getFileContent(any(), any(), any(), any()) } returns
                FileContentDto(
                    name = "archive.zip",
                    path = "archive.zip",
                    size = binaryBytes.size.toLong(),
                    content = Base64.getEncoder().encodeToString(binaryBytes),
                    encoding = "base64",
                )

            val result = repository.getFileContent("octocat", "Hello-World", "archive.zip", "main")

            assertTrue(result.isSuccess)
            val data = result.getOrThrow()
            assertEquals(FileKind.BINARY, data.kind)
            assertNull(data.text)
        }

    @Test
    fun getFileContent_apiFailure_returnsFailure() =
        runTest {
            coEvery { contentApi.getFileContent(any(), any(), any(), any()) } throws httpException(404)

            val result = repository.getFileContent("octocat", "Hello-World", "missing.txt", "main")

            assertTrue(result.isFailure)
        }
}
