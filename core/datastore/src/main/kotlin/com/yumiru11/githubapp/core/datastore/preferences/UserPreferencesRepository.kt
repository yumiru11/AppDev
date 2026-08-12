package com.yumiru11.githubapp.core.datastore.preferences

import com.yumiru11.githubapp.core.datastore.model.ThemeMode
import kotlinx.coroutines.flow.Flow

/**
 * 用户偏好仓库接口（UI 层只依赖该抽象，Hilt @Binds 装配实现）。
 */
interface UserPreferencesRepository {
    /** 主题模式（默认跟随系统） */
    val themeMode: Flow<ThemeMode>

    /** 语言标签（BCP 47，null = 跟随系统语言） */
    val languageTag: Flow<String?>

    suspend fun setThemeMode(mode: ThemeMode)

    /** null 表示清除语言偏好（回退系统语言） */
    suspend fun setLanguageTag(tag: String?)
}
