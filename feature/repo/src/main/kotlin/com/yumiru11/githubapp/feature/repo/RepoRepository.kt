@file:Suppress("TooGenericExceptionCaught") // 网络/IO 错误统一兜底

package com.yumiru11.githubapp.feature.repo

import com.yumiru11.githubapp.core.`data`.model.Repository
import com.yumiru11.githubapp.core.database.dao.CachedReadmeDao
import com.yumiru11.githubapp.core.database.entity.CachedReadmeEntity
import com.yumiru11.githubapp.core.githubrest.api.ReadmeApi
import com.yumiru11.githubapp.core.githubrest.api.RepositoryApi
import com.yumiru11.githubapp.core.githubrest.model.MarkdownRenderRequest
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
         * 获取 README HTML（三级降级 + 双 key 缓存）。
         * @param themeVersion 当前主题版本，用于缓存失效
         */
        suspend fun getReadmeHtml(
            owner: String,
            repo: String,
            themeVersion: String,
        ): Result<String> {
            // 1. 查缓存
            val cached = cachedReadmeDao.getByOwnerAndRepo(owner, repo)
            if (cached != null && cached.themeVersion == themeVersion) {
                return Result.success(cached.html)
            }

            return try {
                // 2. GET README 元数据（含 sha 用于 contentHash）
                val meta = readmeApi.getReadmeMeta(owner, repo)
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
                        readmeApi.getReadmeHtml(owner, repo).string()
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
    }
