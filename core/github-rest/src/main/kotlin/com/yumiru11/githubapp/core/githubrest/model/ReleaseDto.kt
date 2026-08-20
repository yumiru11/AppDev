package com.yumiru11.githubapp.core.githubrest.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * GitHub REST Release DTO（GET /repos/{owner}/{repo}/releases[/{id}]）。
 *
 * 列表与详情端点返回同一结构（含 body），详情端点用于展开时刷新。
 */
@Serializable
data class ReleaseDto(
    val id: Long,
    @SerialName("tag_name")
    val tagName: String,
    val name: String? = null,
    val body: String? = null,
    @SerialName("html_url")
    val htmlUrl: String? = null,
    @SerialName("published_at")
    val publishedAt: String? = null,
    val prerelease: Boolean = false,
    val draft: Boolean = false,
    val author: UserDto? = null,
)
