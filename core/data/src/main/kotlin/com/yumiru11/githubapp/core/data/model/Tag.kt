package com.yumiru11.githubapp.core.data.model

/**
 * GitHub Tag（统一领域模型，T12）。
 *
 * 由 core:github-rest 的 TagDto 映射而来；commitSha 指向 tag 所在 commit。
 */
data class Tag(
    val name: String,
    val commitSha: String,
)
