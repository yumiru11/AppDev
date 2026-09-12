@file:Suppress("TooGenericExceptionCaught") // PagingSource.load 不允许抛异常：REST 通道网络/IO/HTTP/未知错误统一收敛为 LoadResult.Error

package com.yumiru11.githubapp.core.githubdata.paging

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.api.Optional
import com.apollographql.cache.normalized.FetchPolicy
import com.apollographql.cache.normalized.fetchPolicy
import com.yumiru11.githubapp.core.data.model.Repository
import com.yumiru11.githubapp.core.githubauth.session.isRestOnly
import com.yumiru11.githubapp.core.githubauth.token.TokenStorage
import com.yumiru11.githubapp.core.githubdata.error.GitHubError
import com.yumiru11.githubapp.core.githubdata.error.GitHubRequestException
import com.yumiru11.githubapp.core.githubdata.error.asGitHubError
import com.yumiru11.githubapp.core.githubdata.map.toDomain
import com.yumiru11.githubapp.core.githubgraphql.generated.ViewerRepositoriesQuery
import com.yumiru11.githubapp.core.githubrest.api.UserApi
import kotlinx.coroutines.CancellationException
import javax.inject.Inject

/**
 * 当前用户仓库分页数据源（Paging 3 + GraphQL cursor 游标分页）。
 *
 * [PagingSource.LoadResult.Error] 携带归一化后的 [GitHubRequestException]，
 * UI 层可通过 error 分类展示（限流提示/网络重试等）。
 *
 * **PAT 降级门控**（ADR-0003）：`isRestOnly`（fine-grained PAT 无 GraphQL 权限）时
 * 改走 REST `GET /user/repos`（page/per_page 整数分页，排序与 GraphQL 一致 = updated desc），
 * 不再发出注定 403 的 GraphQL 请求。分页键仍是 [String]（GraphQL 为游标、REST 为页码字符串），
 * 两条通道各自闭环，不混用。
 */
class ViewerRepositoriesPagingSource
    @Inject
    constructor(
        private val apolloClient: ApolloClient,
        private val userApi: UserApi,
        private val tokenStorage: TokenStorage,
    ) : PagingSource<String, Repository>() {
        override suspend fun load(params: LoadParams<String>): LoadResult<String, Repository> =
            if (isRestOnly(tokenStorage.loadSession())) {
                loadViaRest(params)
            } else {
                loadViaGraphQl(params)
            }

        /** GraphQL cursor 分页（OAuth 通道，原行为不变） */
        private suspend fun loadViaGraphQl(params: LoadParams<String>): LoadResult<String, Repository> {
            val response =
                apolloClient
                    .query(
                        ViewerRepositoriesQuery(
                            first = params.loadSize,
                            after = params.key?.let { Optional.present(it) } ?: Optional.absent(),
                        ),
                    ).fetchPolicy(FetchPolicy.NetworkOnly)
                    .execute()

            val repositories = response.data?.viewer?.repositories
            return if (repositories != null) {
                val pageInfo = repositories.pageInfo.toDomain()
                LoadResult.Page(
                    data = repositories.nodes.orEmpty().mapNotNull { it?.toDomain() },
                    prevKey = null,
                    nextKey = pageInfo.endCursor?.takeIf { pageInfo.hasNextPage },
                )
            } else {
                val cause = response.exception
                val error =
                    cause?.asGitHubError()
                        ?: GitHubError.GraphQl(response.errors?.map { it.message }.orEmpty())
                LoadResult.Error(GitHubRequestException(error, cause))
            }
        }

        /**
         * REST page/per_page 分页（PAT 降级通道）。
         *
         * 下一页判定：返回条数达到 loadSize 才翻页（GitHub 尾页不足一页 → 自动终止），
         * 与 core:profile 的 REST 分页源同款语义。
         */
        private suspend fun loadViaRest(params: LoadParams<String>): LoadResult<String, Repository> {
            val page = params.key?.toIntOrNull() ?: FIRST_PAGE
            return try {
                val repositories =
                    userApi.currentUserRepositories(
                        perPage = params.loadSize,
                        page = page,
                        sort = REST_SORT_UPDATED,
                        direction = REST_DIRECTION_DESC,
                    )
                LoadResult.Page(
                    data = repositories.map { it.toDomain() },
                    prevKey = if (page > FIRST_PAGE) (page - 1).toString() else null,
                    nextKey = if (repositories.size == params.loadSize) (page + 1).toString() else null,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                LoadResult.Error(GitHubRequestException(e.asGitHubError(), e))
            }
        }

        override fun getRefreshKey(state: PagingState<String, Repository>): String? = null

        private companion object {
            const val FIRST_PAGE = 1

            /** 与 GraphQL 查询的 orderBy UPDATED_AT DESC 对齐 */
            const val REST_SORT_UPDATED = "updated"
            const val REST_DIRECTION_DESC = "desc"
        }
    }
