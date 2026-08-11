package com.yumiru11.githubapp.core.githubdata.user

import com.apollographql.apollo.ApolloClient
import com.apollographql.cache.normalized.FetchPolicy
import com.apollographql.cache.normalized.fetchPolicy
import com.yumiru11.githubapp.core.data.model.User
import com.yumiru11.githubapp.core.githubdata.error.GitHubRequestException
import com.yumiru11.githubapp.core.githubdata.error.asGitHubError
import com.yumiru11.githubapp.core.githubdata.map.toDomain
import com.yumiru11.githubapp.core.githubgraphql.generated.ViewerQuery
import com.yumiru11.githubapp.core.githubrest.api.UserApi
import kotlinx.coroutines.CancellationException
import javax.inject.Inject

/**
 * [UserRepository] 默认实现：GraphQL viewer 读优先（NetworkOnly），失败降级 REST GET /user。
 *
 * Apollo execute() 不抛网络异常（错误收敛到 response.exception），故 GraphQL 通道
 * 仅需判空 data；REST 通道 Retrofit 3 suspend 直抛异常，归一化后包装。
 */
class DefaultUserRepository
    @Inject
    constructor(
        private val apolloClient: ApolloClient,
        private val userApi: UserApi,
    ) : UserRepository {
        override suspend fun getCurrentUser(): User {
            val viewer =
                apolloClient
                    .query(ViewerQuery())
                    .fetchPolicy(FetchPolicy.NetworkOnly)
                    .execute()
                    .data
                    ?.viewer
            if (viewer != null) return viewer.toDomain()

            return try {
                userApi.currentUser().toDomain()
            } catch (e: CancellationException) {
                throw e
            } catch (
                @Suppress("TooGenericExceptionCaught") t: Throwable,
            ) {
                // REST 通道异常统一归一化（HttpException/IOException/未知），故抑制 TooGenericExceptionCaught
                throw GitHubRequestException(t.asGitHubError(), t)
            }
        }
    }
