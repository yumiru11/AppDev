package com.yumiru11.githubapp.core.githubrest.model

import kotlinx.serialization.Serializable

/**
 * GitHub REST 订阅 DTO（GET/PUT /repos/{owner}/{repo}/subscription）。
 *
 * Watch 状态判定：`subscribed=true` 即 Watch 中；404 表示未订阅（由调用方捕获 HttpException）。
 */
@Serializable
data class SubscriptionDto(
    val subscribed: Boolean,
    val ignored: Boolean = false,
)

/**
 * Watch 请求体（PUT /repos/{owner}/{repo}/subscription）。
 */
@Serializable
data class SubscriptionRequest(
    val subscribed: Boolean,
)
