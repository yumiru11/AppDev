package com.yumiru11.githubapp.core.datastore.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.yumiru11.githubapp.core.datastore.model.ThemeMode
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
