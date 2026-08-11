package com.yumiru11.githubapp.core.githubdata.map

import com.yumiru11.githubapp.core.data.model.PageCursor
import com.yumiru11.githubapp.core.data.model.Repository
import com.yumiru11.githubapp.core.data.model.User
import com.yumiru11.githubapp.core.githubgraphql.generated.RepositoryOverviewQuery
import com.yumiru11.githubapp.core.githubgraphql.generated.ViewerQuery
import com.yumiru11.githubapp.core.githubgraphql.generated.ViewerRepositoriesQuery
import com.yumiru11.githubapp.core.githubrest.model.RepositoryDto
import com.yumiru11.githubapp.core.githubrest.model.UserDto

/**
 * REST DTO / Apollo 生成数据 → core:data 统一领域模型映射。
 *
 * internal：仅本模块 Repository/PagingSource 使用，避免泄漏 DTO 细节到上层。
 */
internal fun ViewerQuery.Viewer.toDomain(): User =
    User(
        login = login,
        name = name,
        avatarUrl = avatarUrl,
        bio = bio,
        url = url,
    )

internal fun UserDto.toDomain(): User =
    User(
        login = login,
        name = name,
        avatarUrl = avatarUrl,
        bio = bio,
        url = htmlUrl,
    )

/**
 * GraphQL 仓库概览查询不含 owner 字段，由调用方传入请求参数 [ownerLogin]。
 */
internal fun RepositoryOverviewQuery.Repository.toDomain(ownerLogin: String): Repository =
    Repository(
        ownerLogin = ownerLogin,
        name = name,
        description = description,
        stargazerCount = stargazerCount,
        forkCount = forkCount,
        language = primaryLanguage?.name,
        defaultBranch = defaultBranchRef?.name,
    )

internal fun RepositoryDto.toDomain(): Repository =
    Repository(
        ownerLogin = owner.login,
        name = name,
        description = description,
        isPrivate = isPrivate,
        stargazerCount = stargazersCount,
        forkCount = forksCount,
        language = language,
        defaultBranch = defaultBranch,
    )

internal fun ViewerRepositoriesQuery.Node.toDomain(): Repository =
    Repository(
        ownerLogin = owner.login,
        name = name,
        description = description,
        isPrivate = isPrivate,
        stargazerCount = stargazerCount,
        language = primaryLanguage?.name,
        updatedAt = updatedAt,
    )

internal fun ViewerRepositoriesQuery.PageInfo.toDomain(): PageCursor =
    PageCursor(
        endCursor = endCursor,
        hasNextPage = hasNextPage,
    )
