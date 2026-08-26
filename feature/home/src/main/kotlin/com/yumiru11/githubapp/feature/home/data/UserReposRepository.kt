package com.yumiru11.githubapp.feature.home.data

import com.yumiru11.githubapp.core.githubrest.api.UserApi
import com.yumiru11.githubapp.feature.home.model.RepoOption
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 「我的仓库」数据源（#89）：GET /user/repos 首页第一页，映射为选择器选项。
 * 选择器 MVP 不做面板内分页（50 个按更新触达的仓库足够日常选择）。
 */
@Singleton
class UserReposRepository
    @Inject
    constructor(
        private val userApi: UserApi,
    ) {
        suspend fun currentUserRepos(): List<RepoOption> =
            userApi
                .currentUserRepositories(perPage = PAGE_SIZE, page = FIRST_PAGE)
                .map { dto ->
                    RepoOption(
                        owner = dto.owner.login,
                        name = dto.name,
                        description = dto.description,
                        isPrivate = dto.isPrivate,
                    )
                }

        private companion object {
            const val PAGE_SIZE = 50
            const val FIRST_PAGE = 1
        }
    }
