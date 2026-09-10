package com.yumiru11.githubapp.feature.repo

import com.yumiru11.githubapp.core.data.model.Release

/**
 * Release 附件（L05；feature 内领域模型，避免为展示字段扩张 core:data 兼容面）。
 *
 * @param downloadUrl GitHub 直链（browser_download_url），点击 → Chrome Custom Tabs 下载
 * @param size 字节数（UI 做单位换算）
 * @param downloadCount 累计下载数
 */
data class ReleaseAsset(
    val id: Long,
    val name: String,
    val downloadUrl: String? = null,
    val size: Long = 0,
    val downloadCount: Int = 0,
)

/**
 * Release 详情（正文 + 附件列表，L05）。
 */
data class ReleaseDetail(
    val release: Release,
    val assets: List<ReleaseAsset> = emptyList(),
)

/**
 * 新建 Release 参数（L05）。
 *
 * 用参数对象而非 8 个位置参数：字段语义自解释（tagName/targetCommitish/draft/prerelease），
 * 也避开 detekt LongParameterList（阈值 8）。
 */
data class NewRelease(
    val tagName: String,
    val targetCommitish: String? = null,
    val name: String? = null,
    val body: String? = null,
    val draft: Boolean = false,
    val prerelease: Boolean = false,
)
