package com.yumiru11.githubapp.core.githubrest.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 创建仓库请求体（POST /user/repos，L04）。
 *
 * - `private` 为 Kotlin 保留词，用 @SerialName 映射到 [isPrivate]
 * - [autoInit] → `auto_init`：为 true 时 GitHub 自动建分支并生成初始 commit + README
 * - [gitignoreTemplate] / [licenseTemplate] 为可选模板名（如 "Android"、"mit"）；
 *   null 时不出现在请求体（服务端按缺省处理）
 */
@Serializable
data class CreateRepositoryRequest(
    val name: String,
    val description: String? = null,
    @SerialName("private")
    val isPrivate: Boolean = false,
    @SerialName("auto_init")
    val autoInit: Boolean = false,
    @SerialName("gitignore_template")
    val gitignoreTemplate: String? = null,
    @SerialName("license_template")
    val licenseTemplate: String? = null,
)
