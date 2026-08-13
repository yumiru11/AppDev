package com.yumiru11.githubapp.core.database.entity

import androidx.room.Entity

/**
 * README 缓存实体（双 key 失效：contentHash + themeVersion）。
 *
 * - [contentHash] 来自 GitHub API ReadmeDto.sha（文件内容 SHA），内容变更时自动失效
 * - [themeVersion] 来自 MarkdownThemeTokens.versionHash()，主题更新时自动失效
 * - 查询时若任一不匹配即视为缓存过期，需重新请求
 *
 * 复合主键 [owner]/[repo] 对应 GitHub 仓库唯一标识。
 */
@Entity(tableName = "cached_readme", primaryKeys = ["owner", "repo"])
data class CachedReadmeEntity(
    val owner: String,
    val repo: String,
    /** 内容 SHA（GitHub API ReadmeDto.sha），用于检测内容变更 */
    val contentHash: String,
    /** 主题版本哈希，用于检测主题变更时失效缓存 */
    val themeVersion: String,
    /** 服务端渲染 HTML（WebView 兜底通道渲染） */
    val html: String,
    val updatedAt: Long,
)
