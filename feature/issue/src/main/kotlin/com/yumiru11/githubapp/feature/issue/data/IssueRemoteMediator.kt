package com.yumiru11.githubapp.feature.issue.data

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import com.yumiru11.githubapp.core.database.dao.IssueDao
import com.yumiru11.githubapp.core.database.entity.IssueEntity
import com.yumiru11.githubapp.core.githubrest.api.IssueApi
import com.yumiru11.githubapp.feature.issue.model.IssueFilter
import kotlinx.coroutines.CancellationException
import retrofit2.HttpException
import java.io.IOException

/**
 * Issue 列表远端中介（issue #165 / L07，plan.md §4.6「Issue/PR 列表 → Paging + RemoteMediator」）。
 *
 * 数据流：Paging 请求 → 本中介拉远端页 → 事务写 Room → Room 的 PagingSource（本地表）
 * 出数据；本地表变更由 Room InvalidationTracker 触发自动刷新。
 *
 * 分页锚点：首屏 REFRESH 恒取远端第 1 页；续页 APPEND 取 `MAX(page) + 1`
 * （[IssueDao.maxPage]）——该锚点持久化在 Room，进程被杀后重进也能正确续页，
 * 且不依赖 `PagingState` 里与远端页号不同构的本地 offset key。
 *
 * 断网降级（验收「断网可看已访问列表」）：
 * - 已有该过滤态缓存 → 返回 [RemoteMediator.MediatorResult.Success]（endOfPaginationReached = true），
 *   Paging 继续读本地 PagingSource → 用户看到上次访问的列表
 * - 无缓存 → [RemoteMediator.MediatorResult.Error]，UI 走 loadState 错误态 + 重试
 */
@OptIn(ExperimentalPagingApi::class)
class IssueRemoteMediator(
    private val issueApi: IssueApi,
    private val issueDao: IssueDao,
    private val owner: String,
    private val repo: String,
    private val filter: IssueFilter,
    private val clock: () -> Long = System::currentTimeMillis,
) : RemoteMediator<Int, IssueEntity>() {
    /**
     * 首屏总是先跑一次 REFRESH：有缓存时用户先看到本地数据（Room 立即出），
     * 网络回来后整段替换；无缓存时语义等同普通首屏加载。
     */
    override suspend fun initialize(): InitializeAction = InitializeAction.LAUNCH_INITIAL_REFRESH

    override suspend fun load(
        loadType: LoadType,
        state: PagingState<Int, IssueEntity>,
    ): MediatorResult =
        when (loadType) {
            // GitHub Issue 列表只向后翻页（无 prev 语义）
            LoadType.PREPEND -> MediatorResult.Success(endOfPaginationReached = true)

            LoadType.REFRESH -> fetch(page = STARTING_PAGE, loadType = loadType, state = state)

            LoadType.APPEND -> append(state)
        }

    /** 续页：本地尚无数据时不出网（首屏刷新失败/缓存被清），交给下一次 REFRESH */
    private suspend fun append(state: PagingState<Int, IssueEntity>): MediatorResult {
        if (state.pages
                .lastOrNull()
                ?.data
                ?.lastOrNull() == null
        ) {
            return MediatorResult.Success(endOfPaginationReached = true)
        }
        val nextPage = (issueDao.maxPage(owner, repo, filter.toRaw()) ?: 0) + 1
        return fetch(page = nextPage, loadType = LoadType.APPEND, state = state)
    }

    private suspend fun fetch(
        page: Int,
        loadType: LoadType,
        state: PagingState<Int, IssueEntity>,
    ): MediatorResult =
        try {
            val raw = filter.toRaw()
            val perPage = state.config.pageSize
            val cachedAt = clock()
            val issues =
                issueApi.listIssues(
                    owner = owner,
                    repo = repo,
                    state = raw,
                    page = page,
                    perPage = perPage,
                )
            val entities =
                issues.mapIndexed { index, dto ->
                    dto.toEntity(owner = owner, repo = repo, filter = raw, page = page, position = index, cachedAt = cachedAt)
                }
            if (loadType == LoadType.REFRESH) {
                // 首屏：整段替换（清掉旧页，避免刷新后残留已被远端删除的条目）
                issueDao.replaceAll(owner, repo, raw, entities)
            } else {
                // 续页：仅替换该页
                issueDao.replaceByPage(owner, repo, raw, page, entities)
            }
            // 不满一页即末页（GitHub 末页返回 < per_page），省掉一次空页请求
            MediatorResult.Success(endOfPaginationReached = issues.size < perPage)
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            degradeToCache(e)
        } catch (e: HttpException) {
            degradeToCache(e)
        }

    /** 网络失败降级：有缓存 → Success（让 Room 出数据）；无缓存 → Error（UI 错误态 + 重试） */
    private suspend fun degradeToCache(cause: Throwable): MediatorResult =
        if (issueDao.countByFilter(owner, repo, filter.toRaw()) > 0) {
            MediatorResult.Success(endOfPaginationReached = true)
        } else {
            MediatorResult.Error(cause)
        }

    private companion object {
        const val STARTING_PAGE = 1
    }
}
