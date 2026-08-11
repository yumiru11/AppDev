package com.yumiru11.githubapp.core.githubdata.paging

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.api.Optional
import com.apollographql.cache.normalized.FetchPolicy
import com.apollographql.cache.normalized.fetchPolicy
import com.yumiru11.githubapp.core.data.model.Repository
import com.yumiru11.githubapp.core.githubdata.error.GitHubError
import com.yumiru11.githubapp.core.githubdata.error.GitHubRequestException
import com.yumiru11.githubapp.core.githubdata.error.asGitHubError
import com.yumiru11.githubapp.core.githubdata.map.toDomain
import com.yumiru11.githubapp.core.githubgraphql.generated.ViewerRepositoriesQuery
import javax.inject.Inject

/**
 * 当前用户仓库分页数据源（Paging 3 + GraphQL cursor 游标分页）。
 *
 * [PagingSource.LoadResult.Error] 携带归一化后的 [GitHubRequestException]，
 * UI 层可通过 error 分类展示（限流提示/网络重试等）。
 */
class ViewerRepositoriesPagingSource
    @Inject
    constructor(
        private val apolloClient: ApolloClient,
    ) : PagingSource<String, Repository>() {
        override suspend fun load(params: LoadParams<String>): LoadResult<String, Repository> {
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

        override fun getRefreshKey(state: PagingState<String, Repository>): String? = null
    }
