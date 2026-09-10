package com.yumiru11.githubapp.core.database.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.yumiru11.githubapp.core.database.entity.IssueEntity

/**
 * Issue 列表分页缓存 DAO（plan.md §4.6，issue #165 / L07）。
 *
 * 写（RemoteMediator 驱动）：
 * - [replaceAll]：首屏 REFRESH 成功 → 整段替换该过滤态缓存（事务内先清后写）
 * - [replaceByPage]：续页 APPEND 成功 → 仅替换该页
 * - [clearByRepo]：仓库级清理（取消关注/删除仓库等场景）
 *
 * 读：
 * - [pagingSource]：Room 生成 LimitOffsetPagingSource，按 (page, position) 重放远端顺序；
 *   未加载页由 RemoteMediator 续拉，本地表变更自动 invalidate
 * - [maxPage]：续页锚点（MAX(page) + 1），进程重启后仍准确
 * - [countByFilter]：断网降级判定（已有缓存 → 返回 Success 让 Room 出数据；无缓存 → Error）
 *
 * 声明为抽象类而非接口：[@Transaction] 需要带方法体的实现，抽象类是 Room 官方推荐写法
 * （接口 default 方法在 KSP 下的 JVM default 处理存在版本差异，抽象类无歧义）。
 */
@Dao
abstract class IssueDao {
    /** 插入或覆盖（复合主键 owner+repo+filter+issueId） */
    @Upsert
    abstract suspend fun upsert(entities: List<IssueEntity>)

    /** 删除某过滤态某页的全部行（[replaceByPage] 的前半步，供测试单独断言） */
    @Query("DELETE FROM cached_issues WHERE owner = :owner AND repo = :repo AND filter = :filter AND page = :page")
    abstract suspend fun deleteByPage(
        owner: String,
        repo: String,
        filter: String,
        page: Int,
    )

    /** 清空某仓库某过滤态的缓存（[replaceAll] 的前半步） */
    @Query("DELETE FROM cached_issues WHERE owner = :owner AND repo = :repo AND filter = :filter")
    abstract suspend fun clearByFilter(
        owner: String,
        repo: String,
        filter: String,
    )

    /** 清空某仓库全部过滤态的缓存 */
    @Query("DELETE FROM cached_issues WHERE owner = :owner AND repo = :repo")
    abstract suspend fun clearByRepo(
        owner: String,
        repo: String,
    )

    /** 首屏刷新：整段替换该过滤态缓存（事务保证「清」与「写」之间不出现空窗，UI 不闪空列表） */
    @Transaction
    open suspend fun replaceAll(
        owner: String,
        repo: String,
        filter: String,
        entities: List<IssueEntity>,
    ) {
        clearByFilter(owner, repo, filter)
        upsert(entities)
    }

    /** 续页：仅替换该页（逐页 upsert 会残留被远端删除的旧行） */
    @Transaction
    open suspend fun replaceByPage(
        owner: String,
        repo: String,
        filter: String,
        page: Int,
        entities: List<IssueEntity>,
    ) {
        deleteByPage(owner, repo, filter, page)
        upsert(entities)
    }

    /** 已缓存的最大远端页号（null = 该过滤态无缓存） */
    @Query("SELECT MAX(page) FROM cached_issues WHERE owner = :owner AND repo = :repo AND filter = :filter")
    abstract suspend fun maxPage(
        owner: String,
        repo: String,
        filter: String,
    ): Int?

    /** 该过滤态缓存条数（0 = 无缓存，断网时无法降级出数据） */
    @Query("SELECT COUNT(*) FROM cached_issues WHERE owner = :owner AND repo = :repo AND filter = :filter")
    abstract suspend fun countByFilter(
        owner: String,
        repo: String,
        filter: String,
    ): Int

    /** 按远端分页顺序重放缓存（Room 生成 LimitOffsetPagingSource，自动按 loadSize 分页） */
    @Query(
        "SELECT * FROM cached_issues WHERE owner = :owner AND repo = :repo AND filter = :filter " +
            "ORDER BY page ASC, position ASC",
    )
    abstract fun pagingSource(
        owner: String,
        repo: String,
        filter: String,
    ): PagingSource<Int, IssueEntity>
}
