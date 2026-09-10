package com.yumiru11.githubapp.core.githubrest.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 单提交详情 DTO（GET /repos/{owner}/{repo}/commits/{ref}，L09）。
 *
 * 响应同时携带提交元信息（[commit]）、统计（[stats]）与文件变更列表（[files]，
 * 分页 `?per_page=100&page=N`；单次响应最多 300 个文件，GitHub 上限）。
 */
@Serializable
data class CommitDetailDto(
    val sha: String,
    val commit: CommitInfoDto,
    val author: UserDto? = null,
    val stats: CommitStatsDto? = null,
    val files: List<CommitFileDto> = emptyList(),
)

/**
 * 提交元信息（commit 对象：message / author / committer）。
 *
 * author/committer 复用既有 [CommitAuthorDto]（PullRequestDto.kt，同为 git 身份三元组）。
 */
@Serializable
data class CommitInfoDto(
    val message: String? = null,
    val author: CommitAuthorDto? = null,
    val committer: CommitAuthorDto? = null,
)

/** 提交增删统计（additions/deletions/total）。 */
@Serializable
data class CommitStatsDto(
    val additions: Int = 0,
    val deletions: Int = 0,
    val total: Int = 0,
)

/**
 * 变更文件条目。
 *
 * [patch] 为 unified diff 文本；二进制文件或过大文件时 GitHub 省略该字段（null）。
 */
@Serializable
data class CommitFileDto(
    val filename: String,
    val status: String? = null,
    val additions: Int = 0,
    val deletions: Int = 0,
    val changes: Int = 0,
    val patch: String? = null,
    @SerialName("previous_filename")
    val previousFilename: String? = null,
)
