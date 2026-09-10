package com.yumiru11.githubapp.core.githubrest.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * GitHub REST Release DTO（GET /repos/{owner}/{repo}/releases[/{id}]）。
 *
 * 列表与详情端点返回同一结构（含 body），详情端点用于展开时刷新。
 *
 * L05 追加：`assets`（附件列表）与 `target_commitish`（Tag 目标分支/commit）。
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
    /** Tag 目标：分支名或 commit SHA（新建 Release 表单回显/默认值来源） */
    @SerialName("target_commitish")
    val targetCommitish: String? = null,
    /** 附件列表（L05；上传成功后刷新可见） */
    val assets: List<ReleaseAssetDto> = emptyList(),
)

/**
 * Release 附件 DTO（L05）。
 *
 * [browserDownloadUrl] 为可直接在浏览器/Custom Tabs 打开的下载地址；
 * [size] 单位字节，[downloadCount] 为累计下载数。
 */
@Serializable
data class ReleaseAssetDto(
    val id: Long,
    val name: String,
    @SerialName("browser_download_url")
    val browserDownloadUrl: String? = null,
    val size: Long = 0,
    @SerialName("download_count")
    val downloadCount: Int = 0,
    @SerialName("content_type")
    val contentType: String? = null,
    @SerialName("created_at")
    val createdAt: String? = null,
)
