package com.yumiru11.githubapp.core.data.model

import java.time.Instant

/**
 * GitHub 仓库（统一领域模型）。
 *
 * GraphQL 读优先通道提供 updatedAt/stargazerCount 等；REST 通道补 forkCount/defaultBranch。
 * 两个通道字段子集不同，缺省值保证映射函数无需空值分支。
 */
data class Repository(
    val ownerLogin: String,
    val name: String,
    val description: String? = null,
    val isPrivate: Boolean = false,
    val stargazerCount: Int = 0,
    val forkCount: Int = 0,
    val language: String? = null,
    val defaultBranch: String? = null,
    val updatedAt: Instant? = null,
) {
    /** 与 GitHub REST full_name 语义一致（owner/name） */
    val fullName: String
        get() = "$ownerLogin/$name"
}
