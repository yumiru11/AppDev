package com.yumiru11.githubapp.core.githubrest.api

import com.yumiru11.githubapp.core.githubrest.model.RepositoryDto
import com.yumiru11.githubapp.core.githubrest.model.UserDto
import retrofit2.Response
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * 仓库 REST 接口（写优先通道基础端点）。
 */
interface RepositoryApi {
    /** GET /repos/{owner}/{repo}：仓库元数据（含当前会话 permissions） */
    @GET("repos/{owner}/{repo}")
    suspend fun getRepository(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
    ): RepositoryDto

    /**
     * DELETE /repos/{owner}/{repo}：删除仓库（L04，204）。
     *
     * 仅 owner / admin 权限可用；无权限（403）与不存在（404）抛 [retrofit2.HttpException]。
     * 用 [Response] 承接状态码：204 无响应体，直接返回 Unit 会与错误响应混淆。
     */
    @DELETE("repos/{owner}/{repo}")
    suspend fun deleteRepository(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
    ): Response<Unit>

    /**
     * GET /repos/{owner}/{repo}/collaborators：仓库协作者列表（SPEC-3 @mention 候选源）。
     *
     * 只读端点（additive）：Markdown 编辑器的 `@mention` 补全需要真实的人名候选，
     * 而协作者即「本仓库里有访问权的人」——这是 GitHub 权威来源。
     * 不复用 `/repos/{owner}/{repo}/assignees`：那是「可被指派的人」（写权限用户），
     * 与「协作者」语义不等价（只读协作者不在其中），候选集会系统性偏小。
     *
     * 无权访问私有仓库 / 未认证时抛错，由上层降级为空候选（补全面板只是不弹，不影响编辑）。
     *
     * @param perPage 每页条数（默认 100：候选上限 50，单页足够）
     */
    @GET("repos/{owner}/{repo}/collaborators")
    suspend fun listCollaborators(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Query("per_page") perPage: Int = 100,
    ): List<UserDto>
}
