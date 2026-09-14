package com.yumiru11.githubapp.core.githubdata.repository

import com.yumiru11.githubapp.core.data.model.Repository
import com.yumiru11.githubapp.core.data.model.User

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

    /**
     * 获取仓库协作者（SPEC-3：Markdown 编辑器 `@mention` 的候选来源）。
     *
     * REST GET /repos/{owner}/{repo}/collaborators（只读，无 GraphQL 通道）。
     * 失败（未认证 / 无权限 / 网络）抛
     * [com.yumiru11.githubapp.core.githubdata.error.GitHubRequestException]，
     * **由调用方决定降级**——补全候选读路径失败只会少弹一个面板，不该影响编辑。
     */
    suspend fun listCollaborators(
        owner: String,
        name: String,
    ): List<User>
}
