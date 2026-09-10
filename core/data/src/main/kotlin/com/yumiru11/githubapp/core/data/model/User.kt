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
    // #166 / UI21：GitHub 资料页统计行是「repos / followers / following / stars」四件套，
    // 而 REST /user 不返回 star 总数（需另探 Link 头），故用可空表示"未取到"——
    // UI 侧据此决定是否渲染这一项，而不是拿 0 冒充真实值。
    val starredCount: Int? = null,
)
