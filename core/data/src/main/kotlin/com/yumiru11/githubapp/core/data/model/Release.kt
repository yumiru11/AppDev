package com.yumiru11.githubapp.core.data.model

import java.time.Instant

/**
 * GitHub Release（统一领域模型，T12）。
 *
 * 由 core:github-rest 的 ReleaseDto 映射而来；publishedAt 为 ISO 时间戳字符串，
 * 解析失败时为 null（调用方回退原样展示）。
 */
data class Release(
    val id: Long,
    val tagName: String,
    val name: String? = null,
    val body: String? = null,
    val htmlUrl: String? = null,
    val publishedAt: Instant? = null,
    val prerelease: Boolean = false,
    val draft: Boolean = false,
    val authorLogin: String? = null,
)
