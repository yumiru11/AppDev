package com.yumiru11.githubapp.core.data.model

/**
 * GraphQL 连接分页游标（对应 PageInfo 子集）。
 *
 * [endCursor] 为空或 [hasNextPage] 为 false 时表示已到末页。
 */
data class PageCursor(
    val endCursor: String?,
    val hasNextPage: Boolean,
)
