package com.yumiru11.githubapp.feature.search.data

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.yumiru11.githubapp.core.data.model.Repository
import com.yumiru11.githubapp.core.data.model.SearchCodeItem
import com.yumiru11.githubapp.core.data.model.SearchIssue
import com.yumiru11.githubapp.core.data.model.User
import com.yumiru11.githubapp.core.githubdata.search.SearchRepository
import com.yumiru11.githubapp.feature.search.SearchTab
import com.yumiru11.githubapp.feature.search.di.SearchCacheScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 搜索分页数据仓库（T18 + issue #165 / L13）：按 [query] 为每个结果 Tab 构建独立 Paging 流。
 *
 * ViewModel 只收集当前 Tab 的流（搜索 API 限流严格：未认证 10 次/分，避免
 * 同时发起五路请求）；切换 Tab 即重建 Pager → 新请求。
 *
 * 结果缓存（L13）：每条流的数据源都挂 [SearchResultCache]，命中即零网络出数据
 * （见 [SearchPagingSource]）；后台静默刷新使用应用级 [cacheScope]（缓存回填不应绑死
 * 某个 ViewModel 的生命周期）。
 */
@Singleton
class SearchPagingRepository
    @Inject
    constructor(
        private val searchRepository: SearchRepository,
        private val resultCache: SearchResultCache,
        @SearchCacheScope private val cacheScope: CoroutineScope,
    ) {
        fun repositories(query: String): Flow<PagingData<Repository>> =
            pager(SearchTab.REPOSITORIES, query) { page, perPage -> searchRepository.searchRepositories(query, page, perPage) }

        fun users(query: String): Flow<PagingData<User>> =
            pager(SearchTab.USERS, query) { page, perPage -> searchRepository.searchUsers(query, page, perPage) }

        fun issues(query: String): Flow<PagingData<SearchIssue>> =
            pager(SearchTab.ISSUES, query) { page, perPage -> searchRepository.searchIssues(query, page, perPage) }

        fun pullRequests(query: String): Flow<PagingData<SearchIssue>> =
            pager(SearchTab.PULL_REQUESTS, query) { page, perPage -> searchRepository.searchPullRequests(query, page, perPage) }

        fun code(query: String): Flow<PagingData<SearchCodeItem>> =
            pager(SearchTab.CODE, query) { page, perPage -> searchRepository.searchCode(query, page, perPage) }

        private fun <T : Any> pager(
            tab: SearchTab,
            query: String,
            loader: suspend (page: Int, perPage: Int) -> List<T>,
        ): Flow<PagingData<T>> =
            Pager(
                config = PagingConfig(pageSize = PAGE_SIZE),
                pagingSourceFactory = {
                    SearchPagingSource(
                        loader = loader,
                        cache = resultCache,
                        cacheKey = cacheKey(tab, query),
                        refreshScope = cacheScope,
                    )
                },
            ).flow

        /** 缓存 key：Tab 编码了结果类型（同一 key 的泛型 T 恒定，见 SearchResultCache KDoc） */
        private fun cacheKey(
            tab: SearchTab,
            query: String,
        ): String = "${tab.name}:$query"

        private companion object {
            const val PAGE_SIZE = 30
        }
    }
