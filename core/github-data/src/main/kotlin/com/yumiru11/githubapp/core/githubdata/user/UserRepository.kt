package com.yumiru11.githubapp.core.githubdata.user

import com.yumiru11.githubapp.core.data.model.User

/**
 * 当前用户仓库接口（UI 层只依赖该抽象，Hilt @Binds 装配实现）。
 */
interface UserRepository {
    /**
     * 获取当前登录用户资料。
     *
     * GraphQL viewer 读优先，失败降级 REST GET /user；双通道均失败抛
     * [com.yumiru11.githubapp.core.githubdata.error.GitHubRequestException]。
     */
    suspend fun getCurrentUser(): User
}
