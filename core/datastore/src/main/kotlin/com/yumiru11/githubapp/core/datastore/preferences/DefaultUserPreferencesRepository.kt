package com.yumiru11.githubapp.core.datastore.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.yumiru11.githubapp.core.datastore.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [UserPreferencesRepository] 默认实现（Preferences DataStore）。
 *
 * 未知的持久化值（如旧版本枚举名）回退 [ThemeMode.SYSTEM]，保证升级不崩溃。
 */
@Singleton
class DefaultUserPreferencesRepository
    @Inject
    constructor(
        private val dataStore: DataStore<Preferences>,
    ) : UserPreferencesRepository {
        override val themeMode: Flow<ThemeMode> =
            dataStore.data.map { prefs ->
                prefs[KEY_THEME_MODE]
                    ?.let { stored -> runCatching { ThemeMode.valueOf(stored) }.getOrNull() }
                    ?: ThemeMode.SYSTEM
            }

        override val languageTag: Flow<String?> = dataStore.data.map { it[KEY_LANGUAGE_TAG] }

        override val blurEnabled: Flow<Boolean> =
            dataStore.data.map { it[KEY_BLUR_ENABLED] ?: true }

        override suspend fun setThemeMode(mode: ThemeMode) {
            dataStore.edit { it[KEY_THEME_MODE] = mode.name }
        }

        override suspend fun setBlurEnabled(enabled: Boolean) {
            dataStore.edit { it[KEY_BLUR_ENABLED] = enabled }
        }

        override suspend fun setLanguageTag(tag: String?) {
            dataStore.edit {
                if (tag == null) {
                    it.remove(KEY_LANGUAGE_TAG)
                } else {
                    it[KEY_LANGUAGE_TAG] = tag
                }
            }
        }

        private companion object {
            val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
            val KEY_LANGUAGE_TAG = stringPreferencesKey("language_tag")
            val KEY_BLUR_ENABLED = booleanPreferencesKey("blur_enabled")
        }
    }
