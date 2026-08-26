@file:Suppress("SwallowedException") // 状态检查端点 404/401/403 → false 是端点语义（游客只读），异常即结果，无需再抛

package com.yumiru11.githubapp.feature.repo

import com.yumiru11.githubapp.core.data.model.Release
import com.yumiru11.githubapp.core.data.model.Repository
import com.yumiru11.githubapp.core.data.model.Tag
import com.yumiru11.githubapp.core.githubrest.api.RepoManagementApi
import com.yumiru11.githubapp.core.githubrest.model.SubscriptionRequest
import retrofit2.HttpException
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 仓库管理数据仓库（T12：Star/Watch/Fork + Releases/Tags/Languages）。
 *
 * 状态检查端点语义（REST 无布尔返回，靠状态码判定）：
 * - isStarred：204 已星标；404 未星标；401/403（游客/令牌失效）一律按 false 处理
 * - isWatching：200 {subscribed} 取 subscribed；404 未订阅；401/403 按 false
 *
 * 写操作（star/unstar/watch/unwatch/fork）失败抛 [HttpException]，由 ViewModel 回滚并映射事件。
 */
@Singleton
class RepoManagementRepository
    @Inject
    constructor(
        private val repoManagementApi: RepoManagementApi,
    ) {
        /** 是否已星标（Response<Unit> 语义：204 → isSuccessful=true；404/401/403 → false，游客只读）。 */
        suspend fun isStarred(
            owner: String,
            repo: String,
        ): Boolean = repoManagementApi.isStarred(owner, repo).isSuccessful

        /** 设置星标状态（starred=true → PUT，false → DELETE）。 */
        suspend fun setStarred(
            owner: String,
            repo: String,
            starred: Boolean,
        ) {
            if (starred) {
                repoManagementApi.star(owner, repo)
            } else {
                repoManagementApi.unstar(owner, repo)
            }
        }

        /** 是否 Watch 中（404/401/403 → false）。 */
        suspend fun isWatching(
            owner: String,
            repo: String,
        ): Boolean =
            try {
                repoManagementApi.getSubscription(owner, repo).subscribed
            } catch (e: HttpException) {
                false
            }

        /** 设置 Watch 状态（watching=true → PUT subscribed:true，false → DELETE）。 */
        suspend fun setWatching(
            owner: String,
            repo: String,
            watching: Boolean,
        ) {
            if (watching) {
                repoManagementApi.watch(owner, repo, SubscriptionRequest(subscribed = true))
            } else {
                repoManagementApi.unwatch(owner, repo)
            }
        }

        /**
         * Fork 仓库。
         *
         * 失败返回 [Result.failure]：403 → 无权限；422 → 已 Fork 过（ViewModel 映射事件类型）。
         */
        suspend fun fork(
            owner: String,
            repo: String,
        ): Result<Repository> =
            try {
                val dto = repoManagementApi.fork(owner, repo)
                Result.success(
                    Repository(
                        ownerLogin = dto.owner.login,
                        name = dto.name,
                        description = dto.description,
                        isPrivate = dto.isPrivate,
                        stargazerCount = dto.stargazersCount,
                        forkCount = dto.forksCount,
                        language = dto.language,
                        defaultBranch = dto.defaultBranch,
                    ),
                )
            } catch (e: HttpException) {
                Result.failure(e)
            }

        /** 删除分支（T23；Git refs 端点；默认分支 GitHub 返回 422 → 调方按失败事件处理）。 */
        suspend fun deleteBranch(
            owner: String,
            repo: String,
            branch: String,
        ) {
            repoManagementApi.deleteBranch(owner, repo, branch)
        }

        /** Release 列表。 */
        suspend fun getReleases(
            owner: String,
            repo: String,
        ): Result<List<Release>> =
            runCatching {
                repoManagementApi.listReleases(owner, repo).map { it.toDomain() }
            }

        /** Release 详情（展开时刷新）。 */
        suspend fun getRelease(
            owner: String,
            repo: String,
            releaseId: Long,
        ): Result<Release> =
            runCatching {
                repoManagementApi.getRelease(owner, repo, releaseId).toDomain()
            }

        /** Tag 列表。 */
        suspend fun getTags(
            owner: String,
            repo: String,
        ): Result<List<Tag>> =
            runCatching {
                repoManagementApi.listTags(owner, repo).map { Tag(name = it.name, commitSha = it.commit.sha) }
            }

        /** 语言 → 字节数（Linguist 数据，语言栏渲染源）。 */
        suspend fun getLanguages(
            owner: String,
            repo: String,
        ): Result<Map<String, Long>> =
            runCatching {
                repoManagementApi.getLanguages(owner, repo)
            }
    }

/** ReleaseDto → 领域模型（publishedAt ISO 解析失败回退 null）。 */
private fun com.yumiru11.githubapp.core.githubrest.model.ReleaseDto.toDomain(): Release =
    Release(
        id = id,
        tagName = tagName,
        name = name,
        body = body,
        htmlUrl = htmlUrl,
        publishedAt = publishedAt?.let { runCatching { Instant.parse(it) }.getOrNull() },
        prerelease = prerelease,
        draft = draft,
        authorLogin = author?.login,
    )
