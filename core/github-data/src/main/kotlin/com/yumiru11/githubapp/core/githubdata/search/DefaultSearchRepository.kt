@file:Suppress("TooGenericExceptionCaught")
// 通道异常统一归一化（HttpException/IOException/未知），同 DefaultRepositoryRepository 先例

package com.yumiru11.githubapp.core.githubdata.search

import com.yumiru11.githubapp.core.data.model.Repository
import com.yumiru11.githubapp.core.data.model.SearchCodeItem
import com.yumiru11.githubapp.core.data.model.SearchIssue
import com.yumiru11.githubapp.core.data.model.User
import com.yumiru11.githubapp.core.githubdata.error.GitHubRequestException
import com.yumiru11.githubapp.core.githubdata.error.asGitHubError
import com.yumiru11.githubapp.core.githubdata.map.toDomain
import com.yumiru11.githubapp.core.githubdata.map.toSearchCodeItem
import com.yumiru11.githubapp.core.githubdata.map.toSearchIssue
import com.yumiru11.githubapp.core.githubrest.api.SearchApi
import kotlinx.coroutines.CancellationException
import javax.inject.Inject

/**
 * [SearchRepository] 默认实现：REST 搜索端点 + 错误归一化（429 → RateLimited 等）。
 *
 * 429/网络错误等由 [asGitHubError] 归一化后包装为 [GitHubRequestException]，
 * UI 层按 error 分类展示友好文案（T18 验收第 5 条：限流友好提示）。
 */
class DefaultSearchRepository
    @Inject
    constructor(
        private val searchApi: SearchApi,
    ) : SearchRepository {
        override suspend fun searchRepositories(
            query: String,
            page: Int,
            perPage: Int,
        ): List<Repository> =
            execute {
                searchApi
                    .searchRepositories(query = query, page = page, perPage = perPage)
                    .items
                    .map { it.toDomain() }
            }

        override suspend fun searchUsers(
            query: String,
            page: Int,
            perPage: Int,
        ): List<User> =
            execute {
                searchApi
                    .searchUsers(query = query, page = page, perPage = perPage)
                    .items
                    .map { it.toDomain() }
            }

        override suspend fun searchIssues(
            query: String,
            page: Int,
            perPage: Int,
        ): List<SearchIssue> =
            execute {
                searchApi
                    .searchIssues(query = query, page = page, perPage = perPage)
                    .items
                    .map { it.toSearchIssue() }
            }

        override suspend fun searchPullRequests(
            query: String,
            page: Int,
            perPage: Int,
        ): List<SearchIssue> =
            execute {
                searchApi
                    .searchIssues(query = "$query $PR_QUALIFIER", page = page, perPage = perPage)
                    .items
                    .map { it.toSearchIssue() }
            }

        override suspend fun searchCode(
            query: String,
            page: Int,
            perPage: Int,
        ): List<SearchCodeItem> =
            execute {
                searchApi
                    .searchCode(query = query, page = page, perPage = perPage)
                    .items
                    .map { it.toSearchCodeItem() }
            }

        private suspend fun <T> execute(block: suspend () -> List<T>): List<T> =
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                throw GitHubRequestException(t.asGitHubError(), t)
            }

        private companion object {
            const val PR_QUALIFIER = "is:pr"
        }
    }
