package com.yumiru11.githubapp.core.githubdata.user

import com.apollographql.apollo.ApolloClient
import com.apollographql.cache.normalized.FetchPolicy
import com.apollographql.cache.normalized.fetchPolicy
import com.yumiru11.githubapp.core.data.model.User
import com.yumiru11.githubapp.core.githubauth.session.isRestOnly
import com.yumiru11.githubapp.core.githubauth.token.TokenStorage
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
 *
 * **PAT 降级门控**（ADR-0003）：`isRestOnly` 时直接走 REST GET /user，
 * 不发出注定 403 的 GraphQL 请求。
 */
class DefaultUserRepository
    @Inject
    constructor(
        private val apolloClient: ApolloClient,
        private val userApi: UserApi,
        private val tokenStorage: TokenStorage,
    ) : UserRepository {
        override suspend fun getCurrentUser(): User {
            if (!isRestOnly(tokenStorage.loadSession())) {
                val viewer =
                    apolloClient
                        .query(ViewerQuery())
                        .fetchPolicy(FetchPolicy.NetworkOnly)
                        .execute()
                        .data
                        ?.viewer
                if (viewer != null) return viewer.toDomain()
            }

            return restCurrentUser()
        }

        /** REST 兜底通道（PAT 降级主通道）：GET /user */
        private suspend fun restCurrentUser(): User =
            try {
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
