package com.yumiru11.githubapp.core.githubrest.model

import kotlinx.serialization.Serializable

/**
 * GitHub REST 通知 DTO（GET /notifications、PATCH 已读）。
 *
 * 纯 Kotlin + kotlinx-serialization（架构护栏：model 包禁 android import）。
 * 字段为 API 子集，ignoreUnknownKeys 容忍 GitHub 未来新增字段。
 *
 * @param reason 通知原因（mention/assign/subscribed/review_requested 等，UI 层映射本地化文案）
 * @param htmlUrl 内容页链接（应用内导航解析基准，T19 验收第 4 条）
 */
@Serializable
data class NotificationDto(
    val id: String,
    val repository: NotificationRepositoryDto,
    val subject: NotificationSubjectDto,
    val reason: String,
    val unread: Boolean = true,
    val updatedAt: String? = null,
    val lastReadAt: String? = null,
    val url: String? = null,
    val htmlUrl: String? = null,
)

/**
 * 通知所属仓库子对象（API 子集：完整对象含大量仓库字段，T19 只需展示用两字段）。
 */
@Serializable
data class NotificationRepositoryDto(
    val fullName: String? = null,
    val htmlUrl: String? = null,
)

/**
 * 通知主题子对象（issue/PR/release 等被通知内容的元数据）。
 */
@Serializable
data class NotificationSubjectDto(
    val title: String,
    val url: String,
    val latestCommentUrl: String? = null,
    val type: String,
)
