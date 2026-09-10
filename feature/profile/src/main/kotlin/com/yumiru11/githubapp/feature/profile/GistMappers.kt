package com.yumiru11.githubapp.feature.profile

import com.yumiru11.githubapp.core.githubrest.model.GistDto
import com.yumiru11.githubapp.feature.profile.model.GistItem

/**
 * Gist DTO → 领域模型映射（feature 内私有，不泄漏 DTO 到 UI）。
 *
 * 文件名取自 files 对象的**键**（GitHub 保证存在），键缺失时回退文件对象的 filename，
 * 再回退 gist id——保证行标题恒非空（空标题行在 UI 上不可辨）。
 */
internal fun GistDto.toGistItem(): GistItem {
    val firstFile = files.entries.firstOrNull()
    return GistItem(
        id = id,
        fileName =
            firstFile?.key?.takeIf { it.isNotBlank() }
                ?: firstFile?.value?.filename?.takeIf { it.isNotBlank() }
                ?: id,
        language = firstFile?.value?.language?.takeIf { it.isNotBlank() },
        description = description?.takeIf { it.isNotBlank() },
        createdAt = createdAt,
        htmlUrl = htmlUrl,
    )
}
