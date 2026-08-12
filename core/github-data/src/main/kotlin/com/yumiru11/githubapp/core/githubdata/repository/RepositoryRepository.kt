package com.yumiru11.githubapp.core.githubdata.repository

import com.yumiru11.githubapp.core.data.model.Repository

/**
 * 仓库元数据仓库接口（UI 层只依赖该抽象，Hilt @Binds 装配实现）。
 */
interface RepositoryRepository {
    /**
     * 获取单个仓库概览。
     *
     * GraphQL 读优先，失败降级 REST GET /repos/{owner}/{repo}；双通道均失败抛
     * [com.yumiru11.githubapp.core.githubdata.error.GitHubRequestException]。
     */
    suspend fun getRepository(
        owner: String,
        name: String,
    ): Repository
}
