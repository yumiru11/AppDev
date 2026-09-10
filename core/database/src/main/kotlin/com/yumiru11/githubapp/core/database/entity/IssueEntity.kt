package com.yumiru11.githubapp.core.database.entity

import androidx.room.Entity
import androidx.room.Index

/**
 * Issue 列表分页缓存实体（plan.md §4.6「Issue/PR 列表 → Paging + RemoteMediator（Room）」）。
 *
 * 一条记录 = 某仓库某过滤态某远端页内的一条 Issue。[page]/[position] 是远端分页顺序的
 * 本地重放键：DAO 的 PagingSource 查询按 (page, position) 升序出数据，与 GitHub
 * 返回顺序完全一致；RemoteMediator 续页锚点取 MAX(page) + 1（见 IssueRemoteMediator）。
 *
 * 复合主键 (owner, repo, filter, issueId)：同一 Issue 在 open/closed 两种过滤态下各存一份
 * （GitHub 的 filter 是远端查询条件，缓存必须同维度切分），切换过滤态不互相污染。
 *
 * 仅存列表行渲染所需字段（列表不展示正文/标签/反应；详情走单独接口实时拉取）。
 */
@Entity(
    tableName = "cached_issues",
    primaryKeys = ["owner", "repo", "filter", "issueId"],
    indices = [Index(value = ["owner", "repo", "filter", "page", "position"])],
)
data class IssueEntity(
    val owner: String,
    val repo: String,
    /** 远端过滤态（"open" / "closed"，与 IssueFilter.toRaw() 一致） */
    val filter: String,
    /** GitHub issue id（同仓库内唯一，LazyColumn key 复用） */
    val issueId: Long,
    /** Issue 序号（#number，详情页路由用） */
    val number: Int,
    val title: String,
    /** "open" / "closed" */
    val state: String,
    val authorLogin: String?,
    val authorAvatarUrl: String?,
    val commentCount: Int,
    /** GET /issues 会一并返回 PR，列表点击需分流到 PR 详情 */
    val isPullRequest: Boolean,
    /** GitHub updated_at 原样（ISO-8601 字符串） */
    val updatedAt: String?,
    val htmlUrl: String?,
    /** 远端页号（1 起） */
    val page: Int,
    /** 页内下标（0 起，与网络返回顺序一致） */
    val position: Int,
    /** 本地写入时间戳（毫秒，观测/调试用） */
    val cachedAt: Long,
)
