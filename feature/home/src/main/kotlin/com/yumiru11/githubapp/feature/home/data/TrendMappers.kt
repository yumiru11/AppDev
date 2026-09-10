package com.yumiru11.githubapp.feature.home.data

import com.yumiru11.githubapp.core.githubrest.model.RepositoryDto
import com.yumiru11.githubapp.core.githubrest.model.TrendDto
import com.yumiru11.githubapp.feature.home.model.TrendItem

/**
 * Trending 数据源 DTO → 领域模型映射（feature 内私有，不泄漏 DTO 到 UI）。
 *
 * 两路数据源（镜像 JSON / 搜索 API 回退）归一为同一 [TrendItem]，
 * owner/repo 缺失的脏数据返回 null 由调用方过滤。
 */
internal fun TrendDto.toTrendItem(): TrendItem? =
    fullName
        .takeIf { it.isNotEmpty() }
        ?.let { name ->
            TrendItem(
                fullName = name,
                description = description?.takeIf { it.isNotBlank() },
                language = language?.takeIf { it.isNotBlank() },
                stars = stars,
                forks = forks,
                url = url,
            )
        }

/** 搜索回退结果 → 领域模型（复用仓库搜索 DTO） */
internal fun RepositoryDto.toTrendItem(): TrendItem =
    TrendItem(
        fullName = fullName,
        description = description?.takeIf { it.isNotBlank() },
        language = language?.takeIf { it.isNotBlank() },
        stars = stargazersCount,
        forks = forksCount,
        url = htmlUrl ?: "https://github.com/$fullName",
    )
