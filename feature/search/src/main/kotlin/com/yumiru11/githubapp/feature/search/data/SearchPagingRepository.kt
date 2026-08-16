package com.yumiru11.githubapp.feature.search.data

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.yumiru11.githubapp.core.data.model.Repository
import com.yumiru11.githubapp.core.data.model.SearchCodeItem
import com.yumiru11.githubapp.core.data.model.SearchIssue
import com.yumiru11.githubapp.core.data.model.User
import com.yumiru11.githubapp.core.githubdata.search.SearchRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 搜索分页数据仓库（T18）：按 [query] 为每个结果 Tab 构建独立 Paging 流。
 *
 * ViewModel 只收集当前 Tab 的流（搜索 API 限流严格：未认证 10 次/分，避免
 * 同时发起五路请求）；切换 Tab 即重建 Pager → 新请求。
 */
@Singleton
class SearchPagingRepository
    @Inject
    constructor(
        private val searchRepository: SearchRepository,
    ) {
        fun repositories(query: String): Flow<PagingData<Repository>> =
            pager { page, perPage -> searchRepository.searchRepositories(query, page, perPage) }

        fun users(query: String): Flow<PagingData<User>> = pager { page, perPage -> searchRepository.searchUsers(query, page, perPage) }

        fun issues(query: String): Flow<PagingData<SearchIssue>> =
            pager { page, perPage -> searchRepository.searchIssues(query, page, perPage) }

        fun pullRequests(query: String): Flow<PagingData<SearchIssue>> =
            pager { page, perPage -> searchRepository.searchPullRequests(query, page, perPage) }

        fun code(query: String): Flow<PagingData<SearchCodeItem>> =
            pager { page, perPage -> searchRepository.searchCode(query, page, perPage) }

        private fun <T : Any> pager(loader: suspend (page: Int, perPage: Int) -> List<T>): Flow<PagingData<T>> =
            Pager(
                config = PagingConfig(pageSize = PAGE_SIZE),
                pagingSourceFactory = { SearchPagingSource(loader) },
            ).flow

        private companion object {
            const val PAGE_SIZE = 30
        }
    }
