package com.yumiru11.githubapp.core.data.model

/**
 * GitHub 用户（统一领域模型，REST/GraphQL 通道共同映射目标）。
 *
 * 纯 Kotlin（架构护栏：model 包禁 android/androidx import）。
 * 字段为 profile 页所需子集，后续工单按需扩展。
 */
data class User(
    val login: String,
    val name: String? = null,
    val avatarUrl: String? = null,
    val bio: String? = null,
    val url: String? = null,
    // T20 追加统计字段（additive；GitHub REST /user 无 starred 计数，Starred 走列表）
    val publicRepos: Int = 0,
    val followers: Int = 0,
    val following: Int = 0,
)
