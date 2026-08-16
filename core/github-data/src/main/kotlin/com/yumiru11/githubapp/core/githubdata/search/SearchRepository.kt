package com.yumiru11.githubapp.core.githubdata.search

import com.yumiru11.githubapp.core.data.model.Repository
import com.yumiru11.githubapp.core.data.model.SearchCodeItem
import com.yumiru11.githubapp.core.data.model.SearchIssue
import com.yumiru11.githubapp.core.data.model.User

/**
 * 搜索仓库接口（搜索 REST-only，plan.md §3.2）。
 *
 * 方法返回单页结果列表，分页推进由 feature 层 PagingSource 负责
 * （items 非空即有下一页，项目既有分页约定）；失败抛
 * [com.yumiru11.githubapp.core.githubdata.error.GitHubRequestException]（已归一化）。
 */
interface SearchRepository {
    suspend fun searchRepositories(
        query: String,
        page: Int,
        perPage: Int,
    ): List<Repository>

    suspend fun searchUsers(
        query: String,
        page: Int,
        perPage: Int,
    ): List<User>

    suspend fun searchIssues(
        query: String,
        page: Int,
        perPage: Int,
    ): List<SearchIssue>

    /** PR 搜索：复用 /search/issues + 自动追加 `is:pr` qualifier（GitHub 无独立 PR 搜索端点） */
    suspend fun searchPullRequests(
        query: String,
        page: Int,
        perPage: Int,
    ): List<SearchIssue>

    /** 代码搜索（需登录，未认证服务端 401） */
    suspend fun searchCode(
        query: String,
        page: Int,
        perPage: Int,
    ): List<SearchCodeItem>
}
