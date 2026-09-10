package com.yumiru11.githubapp.core.githubrest.model

import kotlinx.serialization.Serializable

/**
 * GitHub REST Gist DTO（GET /users/{username}/gists）。
 *
 * [files] 是 **filename → 文件对象** 的 JSON 对象（GitHub 以文件名为键）；
 * 展示取首个文件的文件名与语言（v1 不做多文件详情）。
 *
 * 纯 Kotlin + kotlinx-serialization（架构护栏：model 包禁 android import）。
 */
@Serializable
data class GistDto(
    val id: String,
    val description: String? = null,
    val htmlUrl: String? = null,
    val createdAt: String? = null,
    val files: Map<String, GistFileDto> = emptyMap(),
)

/** Gist 内单个文件（v1 只取文件名与语言） */
@Serializable
data class GistFileDto(
    val filename: String? = null,
    val language: String? = null,
)
