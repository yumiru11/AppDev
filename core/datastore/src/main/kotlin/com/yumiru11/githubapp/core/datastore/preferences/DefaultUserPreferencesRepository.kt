package com.yumiru11.githubapp.core.datastore.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.yumiru11.githubapp.core.datastore.model.CodeFont
import com.yumiru11.githubapp.core.datastore.model.IconStyle
import com.yumiru11.githubapp.core.datastore.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [UserPreferencesRepository] 默认实现（Preferences DataStore）。
 *
 * 未知的持久化值（如旧版本枚举名）回退默认值，保证升级不崩溃。
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

        override val dynamicColorEnabled: Flow<Boolean> =
            dataStore.data.map { it[KEY_DYNAMIC_COLOR_ENABLED] ?: false }

        override val seedColor: Flow<Long> =
            dataStore.data.map { it[KEY_SEED_COLOR] ?: UserPreferencesRepository.DEFAULT_SEED_COLOR }

        override val oledEnabled: Flow<Boolean> =
            dataStore.data.map { it[KEY_OLED_ENABLED] ?: false }

        override val highContrastEnabled: Flow<Boolean> =
            dataStore.data.map { it[KEY_HIGH_CONTRAST_ENABLED] ?: false }

        override val cornerScale: Flow<Float> =
            dataStore.data.map { it[KEY_CORNER_SCALE] ?: UserPreferencesRepository.DEFAULT_CORNER_SCALE }

        override val motionScale: Flow<Float> =
            dataStore.data.map { it[KEY_MOTION_SCALE] ?: UserPreferencesRepository.DEFAULT_MOTION_SCALE }

        override val iconStyle: Flow<IconStyle> =
            dataStore.data.map { prefs ->
                prefs[KEY_ICON_STYLE]
                    ?.let { stored -> runCatching { IconStyle.valueOf(stored) }.getOrNull() }
                    ?: IconStyle.ROUNDED
            }

        override val codeFont: Flow<CodeFont> =
            dataStore.data.map { prefs ->
                prefs[KEY_CODE_FONT]
                    ?.let { stored -> runCatching { CodeFont.valueOf(stored) }.getOrNull() }
                    ?: CodeFont.MONO
            }

        override val codeLineNumbers: Flow<Boolean> =
            dataStore.data.map { it[KEY_CODE_LINE_NUMBERS] ?: true }

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

        override suspend fun setDynamicColorEnabled(enabled: Boolean) {
            dataStore.edit { it[KEY_DYNAMIC_COLOR_ENABLED] = enabled }
        }

        override suspend fun setSeedColor(color: Long) {
            dataStore.edit { it[KEY_SEED_COLOR] = color }
        }

        override suspend fun setOledEnabled(enabled: Boolean) {
            dataStore.edit { it[KEY_OLED_ENABLED] = enabled }
        }

        override suspend fun setHighContrastEnabled(enabled: Boolean) {
            dataStore.edit { it[KEY_HIGH_CONTRAST_ENABLED] = enabled }
        }

        override suspend fun setCornerScale(scale: Float) {
            dataStore.edit { it[KEY_CORNER_SCALE] = scale }
        }

        override suspend fun setMotionScale(scale: Float) {
            dataStore.edit { it[KEY_MOTION_SCALE] = scale }
        }

        override suspend fun setIconStyle(style: IconStyle) {
            dataStore.edit { it[KEY_ICON_STYLE] = style.name }
        }

        override suspend fun setCodeFont(font: CodeFont) {
            dataStore.edit { it[KEY_CODE_FONT] = font.name }
        }

        override suspend fun setCodeLineNumbers(enabled: Boolean) {
            dataStore.edit { it[KEY_CODE_LINE_NUMBERS] = enabled }
        }

        private companion object {
            val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
            val KEY_LANGUAGE_TAG = stringPreferencesKey("language_tag")
            val KEY_BLUR_ENABLED = booleanPreferencesKey("blur_enabled")
            val KEY_DYNAMIC_COLOR_ENABLED = booleanPreferencesKey("dynamic_color_enabled")
            val KEY_SEED_COLOR = longPreferencesKey("seed_color")
            val KEY_OLED_ENABLED = booleanPreferencesKey("oled_enabled")
            val KEY_HIGH_CONTRAST_ENABLED = booleanPreferencesKey("high_contrast_enabled")
            val KEY_CORNER_SCALE = floatPreferencesKey("corner_scale")
            val KEY_MOTION_SCALE = floatPreferencesKey("motion_scale")
            val KEY_ICON_STYLE = stringPreferencesKey("icon_style")
            val KEY_CODE_FONT = stringPreferencesKey("code_font")
            val KEY_CODE_LINE_NUMBERS = booleanPreferencesKey("code_line_numbers")
        }
    }
