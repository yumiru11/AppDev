package com.yumiru11.githubapp.feature.home.data

import com.apollographql.apollo.ApolloClient
import com.yumiru11.githubapp.core.githubgraphql.generated.SearchDiscussionsQuery
import com.yumiru11.githubapp.core.githubrest.api.SearchApi
import com.yumiru11.githubapp.core.githubrest.model.IssueDto
import com.yumiru11.githubapp.feature.home.model.HomeDiscussionItem
import com.yumiru11.githubapp.feature.home.model.HomeIssueFilter
import com.yumiru11.githubapp.feature.home.model.HomeIssueItem
import com.yumiru11.githubapp.feature.home.model.HomeLabel
import com.yumiru11.githubapp.feature.home.model.HomePullFilter
import com.yumiru11.githubapp.feature.home.model.HomeUserItem
import kotlinx.serialization.json.JsonNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 首页四个「内容分区」的数据入口（2026-10 UI 重构，对齐 GitHubM3E 的 HomeViewModel）。
 *
 * 数据源选择（plan.md 的「GraphQL 读优先 / REST 写优先」在此处的落地口径）：
 * - 议题 / 拉取请求 / 热门议题 / 建议关注：**REST 搜索**（/search/issues、/search/users）
 *   —— 这些正是"跨仓库 + 带 qualifier 过滤"的场景，REST 搜索语义最直接；
 * - 讨论：**GraphQL**（search(type: DISCUSSION)）—— REST 完全没有 discussions 端点。
 *
 * 限流注意：搜索 API 配额远紧于常规 REST（认证 30 次/分）。因此本仓库**不做轮询**，
 * 只在分区首次进入与用户下拉时请求；失败由上层呈现为分区内错误态（不拖垮动态流）。
 */
@Singleton
class HomeSectionsRepository
    @Inject
    constructor(
        private val searchApi: SearchApi,
        private val apolloClient: ApolloClient,
    ) {
        /** 议题分区：assigned / created / mentioned。 */
        suspend fun issues(
            filter: HomeIssueFilter,
            page: Int = 1,
        ): List<HomeIssueItem> =
            searchApi
                .searchIssues(query = issueQuery(filter), page = page)
                .items
                .map { it.toHomeItem() }

        /** 拉取请求分区：involved / review-requested / recent。 */
        suspend fun pulls(
            filter: HomePullFilter,
            page: Int = 1,
        ): List<HomeIssueItem> =
            searchApi
                .searchIssues(query = pullQuery(filter), page = page)
                .items
                .map { it.toHomeItem() }

        /** 热门议题（发现分区）：按评论数排序的公开议题。 */
        suspend fun hotIssues(page: Int = 1): List<HomeIssueItem> =
            searchApi
                .searchIssues(query = HOT_ISSUES_QUERY, page = page)
                .items
                .map { it.toHomeItem() }

        /** 建议关注用户（发现分区）。 */
        suspend fun suggestedUsers(page: Int = 1): List<HomeUserItem> =
            searchApi
                .searchUsers(query = SUGGESTED_USERS_QUERY, page = page)
                .items
                .map { user ->
                    HomeUserItem(
                        login = user.login,
                        name = user.name,
                        avatarUrl = user.avatarUrl,
                        followers = user.followers,
                    )
                }

        /**
         * 与我相关的讨论（讨论分区）：GraphQL search type: DISCUSSION。
         *
         * 未登录（游客）时 Apollo 无 token → 上层不调用本方法（分区显示登录引导）。
         */
        suspend fun discussions(): List<HomeDiscussionItem> {
            val response = apolloClient.query(SearchDiscussionsQuery(query = DISCUSSIONS_QUERY, first = 20)).execute()
            if (response.hasErrors() && response.data == null) {
                error(response.errors?.firstOrNull()?.message ?: "discussion search failed")
            }
            val nodes =
                response.data
                    ?.search
                    ?.nodes
                    .orEmpty()
            return nodes.mapNotNull { node ->
                val discussion = node?.onDiscussion ?: return@mapNotNull null
                HomeDiscussionItem(
                    id = discussion.id,
                    number = discussion.number,
                    title = discussion.title,
                    repoFullName = discussion.repository?.nameWithOwner,
                    categoryName = discussion.category?.name,
                    isAnswered = discussion.answerChosenAt != null,
                    commentCount = discussion.comments.totalCount,
                    upvotes = discussion.upvoteCount,
                    updatedAt = discussion.updatedAt?.toString() ?: discussion.createdAt?.toString(),
                )
            }
        }

        private fun issueQuery(filter: HomeIssueFilter): String =
            when (filter) {
                HomeIssueFilter.ASSIGNED -> "is:issue is:open assignee:@me"
                HomeIssueFilter.CREATED -> "is:issue is:open author:@me"
                HomeIssueFilter.MENTIONED -> "is:issue is:open mentions:@me"
            }

        private fun pullQuery(filter: HomePullFilter): String =
            when (filter) {
                HomePullFilter.INVOLVED -> "is:pr is:open involves:@me"
                HomePullFilter.REVIEW -> "is:pr is:open review-requested:@me"
                HomePullFilter.RECENT -> "is:pr is:open sort:updated"
            }

        /**
         * REST issue 搜索结果 → 首页行模型。
         *
         * `repository_url` 形如 `https://api.github.com/repos/owner/name`，取 `/repos/` 之后即
         * `owner/name`；缺失时留空（行内不显示归属仓库）。
         * 草稿态：issue 搜索返回的 `pull_request` 对象只有 merged_at/diff_url/html_url/patch_url，
         * **不含 draft**，故 [HomeIssueItem.isDraft] 恒 false（详情页才有权威草稿态）。
         */
        private fun IssueDto.toHomeItem(): HomeIssueItem =
            HomeIssueItem(
                id = id,
                number = number,
                title = title,
                repoFullName = repositoryUrl?.substringAfter("/repos/").orEmpty(),
                state = state,
                isPullRequest = pullRequest != null,
                isDraft = false,
                isMerged = pullRequest?.get("merged_at")?.let { it !is JsonNull } == true,
                commentCount = comments,
                updatedAt = updatedAt,
                labels = labels.map { HomeLabel(name = it.name, colorHex = it.color) },
            )

        private companion object {
            const val DISCUSSIONS_QUERY = "involves:@me"
            const val HOT_ISSUES_QUERY = "is:issue is:open comments:>5"
            const val SUGGESTED_USERS_QUERY = "followers:>5000 type:user"
        }
    }
