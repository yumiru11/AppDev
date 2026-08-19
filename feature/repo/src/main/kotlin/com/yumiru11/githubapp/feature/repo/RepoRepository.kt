@file:Suppress("TooGenericExceptionCaught") // 网络/IO 错误统一兜底

package com.yumiru11.githubapp.feature.repo

import android.util.Log
import com.yumiru11.githubapp.core.`data`.model.Repository
import com.yumiru11.githubapp.core.database.dao.CachedReadmeDao
import com.yumiru11.githubapp.core.database.entity.CachedReadmeEntity
import com.yumiru11.githubapp.core.githubrest.api.ContentApi
import com.yumiru11.githubapp.core.githubrest.api.GitTreeApi
import com.yumiru11.githubapp.core.githubrest.api.ReadmeApi
import com.yumiru11.githubapp.core.githubrest.api.RepositoryApi
import com.yumiru11.githubapp.core.githubrest.model.MarkdownRenderRequest
import com.yumiru11.githubapp.core.githubrest.model.ReadmeDto
import com.yumiru11.githubapp.core.markdown.webview.RenderMode
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 仓库详情数据仓库：组合 REST API + 本地缓存。
 *
 * README 渲染策略（plan.md §2.6 三级降级）：
 * 1. GET /repos/{o}/{r}/readme Accept: html+json → 服务端已渲染 HTML（优先）
 * 2. POST /markdown GFM+context → 服务端 GFM 渲染（备用）
 * 3. 本地 markdown-it 渲染（离线兜底，WebView 内执行）
 *
 * 缓存：双 key（contentHash + themeVersion），检查命中后优先返回缓存。
 */
@Singleton
class RepoRepository
    @Inject
    constructor(
        private val repositoryApi: RepositoryApi,
        private val readmeApi: ReadmeApi,
        private val cachedReadmeDao: CachedReadmeDao,
        private val gitTreeApi: GitTreeApi,
        private val contentApi: ContentApi,
    ) {
        /**
         * 获取仓库元数据。
         */
        suspend fun getRepository(
            owner: String,
            name: String,
        ): Repository {
            val dto = repositoryApi.getRepository(owner, name)
            return Repository(
                ownerLogin = dto.owner.login,
                name = dto.name,
                description = dto.description,
                isPrivate = dto.isPrivate,
                stargazerCount = dto.stargazersCount,
                forkCount = dto.forksCount,
                language = dto.language,
                defaultBranch = dto.defaultBranch,
            )
        }

        /**
         * 获取根文件树（T11：按需展开第一步）。
         *
         * @param ref 分支/Tag/SHA（Git Data API 接受 ref 作 tree_sha，无需先取 commit）
         */
        suspend fun getTree(
            owner: String,
            repo: String,
            ref: String,
        ): Result<List<GitTreeNode>> =
            runCatching {
                val response = gitTreeApi.getTree(owner, repo, ref)
                FileTreeBuilder.buildRootNodes(response.tree)
            }

        /**
         * 获取子目录树（T11：目录点击展开）。
         *
         * @param treeSha 目录条目 SHA
         * @param parentPath 父目录完整路径（子树条目 path 相对该目录，需拼接）
         */
        suspend fun getChildTree(
            owner: String,
            repo: String,
            treeSha: String,
            parentPath: String,
        ): Result<List<GitTreeNode>> =
            runCatching {
                val response = gitTreeApi.getTree(owner, repo, treeSha)
                FileTreeBuilder.buildChildNodes(response.tree, parentPath)
            }

        /**
         * 获取文件内容并分类（T11 验收：大文件/二进制给提示而非卡死）。
         *
         * 解码 → [FileClassifier] 判定（TOO_LARGE 不取内容；BINARY 嗅探；MARKDOWN/CODE 返回文本）。
         *
         * @param ref 分支/Tag/SHA（Contents API ref 查询参数，null 由调用方传默认分支名）
         */
        suspend fun getFileContent(
            owner: String,
            repo: String,
            path: String,
            ref: String?,
        ): Result<FileContentData> =
            runCatching {
                val dto = contentApi.getFileContent(owner, repo, path, ref)
                val bytes = dto.decodeBytes()
                val kind = FileClassifier.classify(dto.name, dto.size, bytes)
                val text = if (kind == FileKind.CODE || kind == FileKind.MARKDOWN) bytes?.decodeToString().orEmpty() else null
                FileContentData(
                    fileName = dto.name,
                    path = dto.path,
                    size = dto.size,
                    kind = kind,
                    text = text,
                )
            }

        /**
         * 获取 README 内容（Task B：README 一律 WebView 渲染）。
         *
         * 数据流：
         * 1. GET README 元数据 → base64 原文（markdown）
         * 2. 一律走服务端 HTML 路径（getReadmeHtml，三级降级 + 双 key 缓存）；
         *    服务端 HTML 获取失败时降级离线 GFM（renderMode 仍 WEBVIEW，WebView 内 markdown-it 渲染）
         *
         * @param themeVersion 当前主题版本，用于缓存失效
         */
        suspend fun getReadme(
            owner: String,
            repo: String,
            themeVersion: String,
        ): Result<ReadmeContent> =
            try {
                val meta = readmeApi.getReadmeMeta(owner, repo)
                val rawMarkdown = meta.decodeContent() ?: ""
                // 渲染通道判定日志（Q7 复测锚点）：真机/CI logcat 过滤 ReadmeRender。
                // 用 Log.i 而非 Log.d：真机 vivo [log.tag]=[I]，Debug 级日志被系统过滤（2026-08-17 实证）。
                // Task B 后 README 恒走 WebView，renderMode 恒 WEBVIEW。
                Log.i(
                    TAG,
                    "repo=$owner/$repo renderMode=${ReadmeRenderMode.WEBVIEW}" +
                        " lines=${rawMarkdown.count { it == '\n' } + 1} bytes=${rawMarkdown.length}",
                )
                val htmlResult = getReadmeHtml(owner, repo, themeVersion, meta)
                val content =
                    htmlResult.fold(
                        onSuccess = { html ->
                            ReadmeContent(
                                markdown = rawMarkdown,
                                html = html,
                                renderMode = ReadmeRenderMode.WEBVIEW,
                                webViewRenderMode = RenderMode.SERVER_HTML,
                            )
                        },
                        onFailure = { failure ->
                            // 服务端 HTML 获取失败 → 降级离线 GFM（WebView 内 markdown-it 渲染），renderMode 仍 WEBVIEW
                            if (rawMarkdown.isNotBlank()) {
                                ReadmeContent(
                                    markdown = rawMarkdown,
                                    html = rawMarkdown,
                                    renderMode = ReadmeRenderMode.WEBVIEW,
                                    webViewRenderMode = RenderMode.OFFLINE_MARKDOWN_IT,
                                )
                            } else {
                                throw failure
                            }
                        },
                    )
                Result.success(content)
            } catch (e: Exception) {
                Result.failure(e)
            }

        /**
         * 获取 README HTML（三级降级 + 双 key 缓存）。
         *
         * @param themeVersion 当前主题版本，用于缓存失效
         * @param meta README 元数据（由 [getReadme] 传入，避免 double getReadmeMeta 网络请求）
         */
        @Suppress("NestedBlockDepth") // JSON 回退判定（try→content-type/首字符→markdown 三级降级）结构固有，拆散反损可读性（T3 先例）
        suspend fun getReadmeHtml(
            owner: String,
            repo: String,
            themeVersion: String,
            meta: ReadmeDto,
        ): Result<String> {
            // 1. 查缓存
            val cached = cachedReadmeDao.getByOwnerAndRepo(owner, repo)
            if (cached != null && cached.themeVersion == themeVersion) {
                return Result.success(cached.html)
            }

            return try {
                // 2. contentHash = README 元数据 sha（由调用方传入，避免重复拉取 meta）
                val contentHash = meta.sha

                // 缓存命中但 contentHash 相同 → 更新 themeVersion 即可
                if (cached != null && cached.contentHash == contentHash) {
                    val updated = cached.copy(themeVersion = themeVersion, updatedAt = System.currentTimeMillis())
                    cachedReadmeDao.upsert(updated)
                    return Result.success(cached.html)
                }

                // Tier 1: 服务端已渲染 HTML
                val html =
                    try {
                        val response = readmeApi.getReadmeHtml(owner, repo)
                        val body = response.string()
                        // GitHub 对部分 README（symlink/超限等）无视 html Accept 返回 API JSON；
                        // 检测到 JSON 响应体时回退 markdown 渲染（2026-08-14 真机走查修复：
                        // openchamber 等仓库 README 显示原始 JSON）
                        val contentType = response.contentType()?.toString().orEmpty()
                        if (body.trimStart().startsWith("{") || contentType.contains("application/json")) {
                            val fallbackMarkdown = meta.decodeContent() ?: ""
                            if (fallbackMarkdown.isNotBlank()) {
                                readmeApi
                                    .renderMarkdown(
                                        MarkdownRenderRequest(
                                            text = fallbackMarkdown,
                                            mode = MarkdownRenderRequest.MODE_GFM,
                                            context = "$owner/$repo",
                                        ),
                                    ).string()
                            } else {
                                error("README HTML 响应为 JSON 且内容为空")
                            }
                        } else {
                            body
                        }
                    } catch (e: Exception) {
                        // Tier 2: POST /markdown GFM 渲染
                        val markdown = meta.decodeContent() ?: ""
                        if (markdown.isNotBlank()) {
                            readmeApi
                                .renderMarkdown(
                                    MarkdownRenderRequest(
                                        text = markdown,
                                        mode = MarkdownRenderRequest.MODE_GFM,
                                        context = "$owner/$repo",
                                    ),
                                ).string()
                        } else {
                            throw e
                        }
                    }

                // 3. 写回缓存
                cachedReadmeDao.upsert(
                    CachedReadmeEntity(
                        owner = owner,
                        repo = repo,
                        contentHash = contentHash,
                        themeVersion = themeVersion,
                        html = html,
                        updatedAt = System.currentTimeMillis(),
                    ),
                )

                Result.success(html)
            } catch (e: Exception) {
                // Tier 3: 降级 → 返回过期缓存（若有）
                if (cached != null) {
                    Result.success(cached.html)
                } else {
                    Result.failure(e)
                }
            }
        }

        private companion object {
            const val TAG = "ReadmeRender"
        }
    }

/**
 * README 内容（渲染通道判定结果）。
 *
 * @param markdown 原始 Markdown 文本
 * @param html 渲染内容：服务端 HTML 时为 HTML；离线 GFM 降级时为原始 Markdown
 * @param renderMode 渲染通道（Task B 后恒为 [ReadmeRenderMode.WEBVIEW]）
 * @param webViewRenderMode WebView 子模式：服务端 HTML 或离线 markdown-it（降级时）
 */
data class ReadmeContent(
    val markdown: String,
    val html: String?,
    val renderMode: ReadmeRenderMode,
    val webViewRenderMode: RenderMode = RenderMode.SERVER_HTML,
)
