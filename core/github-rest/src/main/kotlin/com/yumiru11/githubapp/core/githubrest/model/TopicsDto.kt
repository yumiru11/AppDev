package com.yumiru11.githubapp.core.githubrest.model

import kotlinx.serialization.Serializable

/**
 * 仓库 Topics 响应体（GET /repos/{owner}/{repo}/topics，L06）。
 *
 * 该端点返回 `{"names": ["kotlin", "android"]}`，与大多数 REST 端点（直接返回数组）
 * 不同；GitHub 要求自定义 Accept 头 `application/vnd.github.mercy-preview+json`
 * （预览期已结束，正式 API 无需该头，2026-09 核对）。
 */
@Serializable
data class TopicsDto(
    val names: List<String> = emptyList(),
)
