package com.yumiru11.githubapp.feature.profile

import com.yumiru11.githubapp.core.data.model.Repository
import com.yumiru11.githubapp.core.data.model.User
import com.yumiru11.githubapp.core.githubrest.model.RepositoryDto
import com.yumiru11.githubapp.core.githubrest.model.UserDto

/**
 * REST DTO → core:data 领域模型映射（feature 内私有，不泄漏 DTO 到 UI）。
 */
internal fun UserDto.toUser(): User =
    User(
        login = login,
        name = name,
        avatarUrl = avatarUrl,
        bio = bio,
        url = htmlUrl,
        publicRepos = publicRepos,
        followers = followers,
        following = following,
    )

internal fun RepositoryDto.toRepository(): Repository =
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
