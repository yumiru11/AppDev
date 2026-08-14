package com.yumiru11.githubapp.core.datastore.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.yumiru11.githubapp.core.datastore.model.CodeFont
import com.yumiru11.githubapp.core.datastore.model.IconStyle
import com.yumiru11.githubapp.core.datastore.model.ThemeMode
import com.yumiru11.githubapp.core.datastore.model.resolveEffectiveThemeMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

/**
 * UserPreferencesRepository 读写测试（临时文件 PreferenceDataStoreFactory）。
 *
 * 覆盖：默认值（SYSTEM/null）、主题写入、语言写入/清除、新实例读回持久化值。
 * 每个测试独立 scope：DataStore 生命周期绑定 scope，先 cancel 再开同文件新实例。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class UserPreferencesRepositoryTest {
    @Test
    fun themeMode_byDefault_emitsSystem() =
        runTest {
            val repository = createRepository()

            assertEquals(ThemeMode.SYSTEM, repository.themeMode.first())
        }

    @Test
    fun languageTag_byDefault_emitsNull() =
        runTest {
            val repository = createRepository()

            assertNull(repository.languageTag.first())
        }

    @Test
    fun blurEnabled_byDefault_emitsTrue() =
        runTest {
            val repository = createRepository()

            assertEquals(true, repository.blurEnabled.first())
        }

    @Test
    fun setBlurEnabled_false_persistsAndEmits() =
        runTest {
            val repository = createRepository()

            repository.setBlurEnabled(false)

            assertEquals(false, repository.blurEnabled.first())
        }

    @Test
    fun setBlurEnabled_false_newInstance_readsBackPersistedValue() =
        runTest {
            val file = newPreferencesFile()
            val scope = newScope()
            createRepository(scope, file).setBlurEnabled(false)
            scope.cancel()

            val reloaded = createRepository(newScope(), file)

            assertEquals(false, reloaded.blurEnabled.first())
        }

    @Test
    fun setThemeMode_darkMode_persistsAndEmits() =
        runTest {
            val repository = createRepository()

            repository.setThemeMode(ThemeMode.DARK)

            assertEquals(ThemeMode.DARK, repository.themeMode.first())
        }

    @Test
    fun setThemeMode_darkMode_newInstance_readsBackPersistedValue() =
        runTest {
            val file = newPreferencesFile()
            val scope = newScope()
            createRepository(scope, file).setThemeMode(ThemeMode.DARK)
            scope.cancel()

            val reloaded = createRepository(newScope(), file)

            assertEquals(ThemeMode.DARK, reloaded.themeMode.first())
        }

    @Test
    fun setLanguageTag_chinese_persistsAndEmits() =
        runTest {
            val repository = createRepository()

            repository.setLanguageTag("zh-CN")

            assertEquals("zh-CN", repository.languageTag.first())
        }

    @Test
    fun setLanguageTag_null_clearsPreference() =
        runTest {
            val repository = createRepository()
            repository.setLanguageTag("zh-CN")

            repository.setLanguageTag(null)

            assertNull(repository.languageTag.first())
        }

    @Test
    fun setLanguageTag_chinese_newInstance_readsBackPersistedValue() =
        runTest {
            val file = newPreferencesFile()
            val scope = newScope()
            createRepository(scope, file).setLanguageTag("zh-CN")
            scope.cancel()

            val reloaded = createRepository(newScope(), file)

            assertEquals("zh-CN", reloaded.languageTag.first())
        }

    // ── T24 新增字段（主题引擎扩展）──────────────────────────────────────

    @Test
    fun dynamicColorEnabled_byDefault_emitsFalse() =
        runTest {
            val repository = createRepository()

            assertEquals(false, repository.dynamicColorEnabled.first())
        }

    @Test
    fun seedColor_byDefault_emitsBrandBlue() =
        runTest {
            val repository = createRepository()

            assertEquals(UserPreferencesRepository.DEFAULT_SEED_COLOR, repository.seedColor.first())
        }

    @Test
    fun oledEnabled_byDefault_emitsFalse() =
        runTest {
            val repository = createRepository()

            assertEquals(false, repository.oledEnabled.first())
        }

    @Test
    fun highContrastEnabled_byDefault_emitsFalse() =
        runTest {
            val repository = createRepository()

            assertEquals(false, repository.highContrastEnabled.first())
        }

    @Test
    fun cornerScale_byDefault_emitsOne() =
        runTest {
            val repository = createRepository()

            assertEquals(1f, repository.cornerScale.first())
        }

    @Test
    fun motionScale_byDefault_emitsOne() =
        runTest {
            val repository = createRepository()

            assertEquals(1f, repository.motionScale.first())
        }

    @Test
    fun iconStyle_byDefault_emitsRounded() =
        runTest {
            val repository = createRepository()

            assertEquals(IconStyle.ROUNDED, repository.iconStyle.first())
        }

    @Test
    fun codeFont_byDefault_emitsMono() =
        runTest {
            val repository = createRepository()

            assertEquals(CodeFont.MONO, repository.codeFont.first())
        }

    @Test
    fun codeLineNumbers_byDefault_emitsTrue() =
        runTest {
            val repository = createRepository()

            assertEquals(true, repository.codeLineNumbers.first())
        }

    @Test
    fun setDynamicColorEnabled_true_persistsAndEmits() =
        runTest {
            val repository = createRepository()

            repository.setDynamicColorEnabled(true)

            assertEquals(true, repository.dynamicColorEnabled.first())
        }

    @Test
    fun setSeedColor_customColor_persistsAndEmits() =
        runTest {
            val repository = createRepository()

            repository.setSeedColor(0xFF7C3AED)

            assertEquals(0xFF7C3AED, repository.seedColor.first())
        }

    @Test
    fun setOledEnabled_true_persistsAndEmits() =
        runTest {
            val repository = createRepository()

            repository.setOledEnabled(true)

            assertEquals(true, repository.oledEnabled.first())
        }

    @Test
    fun setHighContrastEnabled_true_persistsAndEmits() =
        runTest {
            val repository = createRepository()

            repository.setHighContrastEnabled(true)

            assertEquals(true, repository.highContrastEnabled.first())
        }

    @Test
    fun setCornerScale_1_5_persistsAndEmits() =
        runTest {
            val repository = createRepository()

            repository.setCornerScale(1.5f)

            assertEquals(1.5f, repository.cornerScale.first())
        }

    @Test
    fun setMotionScale_0_5_persistsAndEmits() =
        runTest {
            val repository = createRepository()

            repository.setMotionScale(0.5f)

            assertEquals(0.5f, repository.motionScale.first())
        }

    @Test
    fun setIconStyle_filled_persistsAndEmits() =
        runTest {
            val repository = createRepository()

            repository.setIconStyle(IconStyle.FILLED)

            assertEquals(IconStyle.FILLED, repository.iconStyle.first())
        }

    @Test
    fun setCodeFont_system_persistsAndEmits() =
        runTest {
            val repository = createRepository()

            repository.setCodeFont(CodeFont.SYSTEM)

            assertEquals(CodeFont.SYSTEM, repository.codeFont.first())
        }

    @Test
    fun setCodeLineNumbers_false_persistsAndEmits() =
        runTest {
            val repository = createRepository()

            repository.setCodeLineNumbers(false)

            assertEquals(false, repository.codeLineNumbers.first())
        }

    @Test
    fun setSeedColor_custom_newInstance_readsBackPersistedValue() =
        runTest {
            val file = newPreferencesFile()
            val scope = newScope()
            createRepository(scope, file).setSeedColor(0xFF7C3AED)
            scope.cancel()

            val reloaded = createRepository(newScope(), file)

            assertEquals(0xFF7C3AED, reloaded.seedColor.first())
        }

    @Test
    fun setCornerScale_1_5_newInstance_readsBackPersistedValue() =
        runTest {
            val file = newPreferencesFile()
            val scope = newScope()
            createRepository(scope, file).setCornerScale(1.5f)
            scope.cancel()

            val reloaded = createRepository(newScope(), file)

            assertEquals(1.5f, reloaded.cornerScale.first())
        }

    @Test
    fun setIconStyle_outlined_newInstance_readsBackPersistedValue() =
        runTest {
            val file = newPreferencesFile()
            val scope = newScope()
            createRepository(scope, file).setIconStyle(IconStyle.OUTLINED)
            scope.cancel()

            val reloaded = createRepository(newScope(), file)

            assertEquals(IconStyle.OUTLINED, reloaded.iconStyle.first())
        }

    @Test
    fun setOledEnabled_true_newInstance_readsBackPersistedValue() =
        runTest {
            val file = newPreferencesFile()
            val scope = newScope()
            createRepository(scope, file).setOledEnabled(true)
            scope.cancel()

            val reloaded = createRepository(newScope(), file)

            assertEquals(true, reloaded.oledEnabled.first())
        }

    @Test
    fun setIconStyle_invalidStoredValue_fallsBackToRounded() =
        runTest {
            val file = newPreferencesFile()
            val seedScope = newScope()
            PreferenceDataStoreFactory
                .create(scope = seedScope, produceFile = { file })
                .edit { it[stringPreferencesKey("icon_style")] = "COMIC" }
            seedScope.cancel()

            val repository = createRepository(newScope(), file)

            assertEquals(IconStyle.ROUNDED, repository.iconStyle.first())
        }

    @Test
    fun resolveEffectiveThemeMode_darkBaseWithDynamicColor_resolvesDynamicDark() {
        assertEquals(
            ThemeMode.DYNAMIC_DARK,
            resolveEffectiveThemeMode(ThemeMode.DARK, dynamicColorEnabled = true, oledEnabled = false, highContrastEnabled = false),
        )
    }

    @Test
    fun resolveEffectiveThemeMode_oledEnabled_winsOverDynamicColor() {
        assertEquals(
            ThemeMode.OLED,
            resolveEffectiveThemeMode(ThemeMode.LIGHT, dynamicColorEnabled = true, oledEnabled = true, highContrastEnabled = false),
        )
    }

    @Test
    fun resolveEffectiveThemeMode_highContrastEnabled_winsOverOled() {
        assertEquals(
            ThemeMode.HIGH_CONTRAST,
            resolveEffectiveThemeMode(ThemeMode.DARK, dynamicColorEnabled = true, oledEnabled = true, highContrastEnabled = true),
        )
    }

    @Test
    fun resolveEffectiveThemeMode_systemBaseWithDynamicColor_systemDark_resolvesDynamicDark() {
        assertEquals(
            ThemeMode.DYNAMIC_DARK,
            resolveEffectiveThemeMode(
                ThemeMode.SYSTEM,
                dynamicColorEnabled = true,
                oledEnabled = false,
                highContrastEnabled = false,
                systemDark = true,
            ),
        )
    }

    @Test
    fun resolveEffectiveThemeMode_systemBaseWithDynamicColor_systemLight_resolvesDynamicLight() {
        assertEquals(
            ThemeMode.DYNAMIC_LIGHT,
            resolveEffectiveThemeMode(
                ThemeMode.SYSTEM,
                dynamicColorEnabled = true,
                oledEnabled = false,
                highContrastEnabled = false,
                systemDark = false,
            ),
        )
    }

    @Test
    fun resolveEffectiveThemeMode_noSwitches_returnsBase() {
        assertEquals(
            ThemeMode.LIGHT,
            resolveEffectiveThemeMode(ThemeMode.LIGHT, dynamicColorEnabled = false, oledEnabled = false, highContrastEnabled = false),
        )
    }

    @Test
    fun setThemeMode_invalidStoredValue_fallsBackToSystem() =
        runTest {
            val file = newPreferencesFile()
            val seedScope = newScope()
            PreferenceDataStoreFactory
                .create(scope = seedScope, produceFile = { file })
                .edit { it[stringPreferencesKey("theme_mode")] = "NEON" }
            seedScope.cancel()

            val repository = createRepository(newScope(), file)

            assertEquals(ThemeMode.SYSTEM, repository.themeMode.first())
        }

    private fun createRepository(): DefaultUserPreferencesRepository = createRepository(newScope(), newPreferencesFile())

    private fun createRepository(
        scope: CoroutineScope,
        file: File,
    ): DefaultUserPreferencesRepository {
        val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })
        return DefaultUserPreferencesRepository(dataStore)
    }

    private fun newScope(): CoroutineScope = CoroutineScope(UnconfinedTestDispatcher())

    private fun newPreferencesFile(): File {
        val file = File.createTempFile("prefs-test", ".preferences_pb")
        file.deleteOnExit()
        return file
    }
}
