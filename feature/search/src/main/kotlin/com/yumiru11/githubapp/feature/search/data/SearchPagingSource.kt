@file:Suppress("TooGenericExceptionCaught", "SwallowedException")
// - TooGenericExceptionCaught：任意异常统一归一化（HttpException/IOException/未知），同 DefaultRepositoryRepository 先例
// - SwallowedException：后台静默刷新（stale-while-revalidate）失败时【有意】保留缓存数据不出错——
//   用户已在看缓存内容，此时弹错反而打断阅读；后续显式刷新/翻页仍会正常暴露错误（异常链不丢失，
//   只是这条后台路径选择不中断）。同 IssueRepository 的降级路径先例。

package com.yumiru11.githubapp.feature.search.data

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.yumiru11.githubapp.core.githubdata.error.GitHubRequestException
import com.yumiru11.githubapp.core.githubdata.error.asGitHubError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 搜索结果分页数据源（通用：loader 按 Tab 绑定对应搜索端点）。
 *
 * 每 Tab 单独 Paging（plan.md §9.2：结果列表单独 Paging）：
 * loader 返回单页结果列表，items 非空即有下一页（项目既有分页约定）。
 *
 * 结果缓存（issue #165 / L13，plan.md §9.3）：
 * - 命中未过期缓存 → 立即返回缓存数据（零网络），UI 先渲染缓存
 * - 命中首页且该条目尚未 revalidate 过 → 在 [refreshScope] 后台静默刷新首页写回缓存并
 *   [invalidate]（stale-while-revalidate）：Paging 保留已渲染行，用户只看到数据被悄悄更新
 * - 未命中/已过期 → 正常出网并写回缓存
 *
 * 错误语义：任意异常（含 429）归一化为 [GitHubRequestException] 包进
 * [LoadResult.Error]——UI 层按 GitHubError 分类展示（限流友好提示）。
 */
class SearchPagingSource<T : Any>(
    private val loader: suspend (page: Int, perPage: Int) -> List<T>,
    private val cache: SearchResultCache? = null,
    private val cacheKey: String = "",
    private val refreshScope: CoroutineScope? = null,
) : PagingSource<Int, T>() {
    private val refreshing = AtomicBoolean(false)

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, T> =
        try {
            val page = params.key ?: STARTING_PAGE
            val cached = cache?.get<T>(cacheKey, page)
            if (cached != null) {
                if (page == STARTING_PAGE && !cached.revalidated) {
                    scheduleSilentRefresh(params.loadSize)
                }
                LoadResult.Page(
                    data = cached.items,
                    prevKey = if (page > STARTING_PAGE) page - 1 else null,
                    nextKey = if (cached.hasMore) page + 1 else null,
                )
            } else {
                val items = loader(page, params.loadSize)
                cache?.put(key = cacheKey, page = page, items = items, hasMore = items.isNotEmpty())
                LoadResult.Page(
                    data = items,
                    prevKey = if (page > STARTING_PAGE) page - 1 else null,
                    nextKey = if (items.isNotEmpty()) page + 1 else null,
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            LoadResult.Error(GitHubRequestException(t.asGitHubError(), t))
        }

    /**
     * 后台静默刷新首页（stale-while-revalidate）。
     *
     * 刷新成功后写回缓存并 [invalidate]：Paging 用新数据重建源，而新源读到的首页缓存已
     * 标记 revalidated → 不会再次触发刷新（自我终止）。刷新失败静默忽略——用户已看到缓存内容，
     * 后续显式刷新/翻页仍会正常报错。
     */
    private fun scheduleSilentRefresh(loadSize: Int) {
        val scope = refreshScope ?: return
        if (!refreshing.compareAndSet(false, true)) return
        scope.launch {
            try {
                val items = loader(STARTING_PAGE, loadSize)
                cache?.put(
                    key = cacheKey,
                    page = STARTING_PAGE,
                    items = items,
                    hasMore = items.isNotEmpty(),
                    revalidated = true,
                )
                invalidate()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // 静默刷新失败：保留缓存数据，不打断用户
            } finally {
                refreshing.set(false)
            }
        }
    }

    override fun getRefreshKey(state: PagingState<Int, T>): Int? =
        state.anchorPosition?.let { anchorPosition ->
            state.closestPageToPosition(anchorPosition)?.let { closestPage ->
                closestPage.prevKey?.plus(1) ?: closestPage.nextKey?.minus(1)
            }
        }

    private companion object {
        const val STARTING_PAGE = 1
    }
}
