@file:Suppress("LongMethod")
// 设置页分组列表样板天然较长（每组一个 section composable），拆散反损可读性；精准抑制（AppNavHost 同款先例）。

package com.yumiru11.githubapp.feature.settings

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yumiru11.githubapp.core.datastore.model.resolveEffectiveThemeMode
import com.yumiru11.githubapp.core.designsystem.theme.AppTheme
import com.yumiru11.githubapp.core.designsystem.token.AppMotion

/**
 * 设置页（T24）：分组列表（外观/开发者/通用），全部设置项经
 * [SettingsViewModel] 写 DataStore 并即时生效。
 *
 * 主题切换全屏 Crossfade（ui-design §3.6）：Crossfade 以「生效主题模式」为
 * targetState，内层 [AppTheme] 按各帧自己的 mode 渲染——旧帧保持旧主题、
 * 新帧新主题，切换即全屏渐变；seed 色同步传入保证帧内主题与全局一致。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val effectiveMode =
        resolveEffectiveThemeMode(
            base = uiState.themeMode,
            dynamicColorEnabled = uiState.dynamicColorEnabled,
            oledEnabled = uiState.oledEnabled,
            highContrastEnabled = uiState.highContrastEnabled,
            systemDark = isSystemInDarkTheme(),
        )

    Crossfade(
        targetState = effectiveMode,
        animationSpec =
            tween(
                durationMillis = AppMotion.DURATION_TRANSIENT,
                easing = AppMotion.Emphasized,
            ),
        label = "theme-crossfade",
    ) { mode ->
        AppTheme(themeMode = mode, seedColor = Color(uiState.seedColor)) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text(stringResource(R.string.settings_title)) },
                    )
                },
                modifier = modifier,
            ) { paddingValues ->
                LazyColumn(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(paddingValues),
                    contentPadding = PaddingValues(vertical = 8.dp),
                ) {
                    item {
                        SectionHeader(text = stringResource(R.string.settings_appearance_group))
                    }
                    item {
                        AppearanceSettingsSection(uiState = uiState, viewModel = viewModel)
                    }
                    item {
                        SectionHeader(text = stringResource(R.string.settings_developer_group))
                    }
                    item {
                        DeveloperSettingsSection(uiState = uiState, viewModel = viewModel)
                    }
                    item {
                        SectionHeader(text = stringResource(R.string.settings_general_group))
                    }
                    item {
                        GeneralSettingsSection(uiState = uiState, viewModel = viewModel)
                    }
                }
            }
        }
    }
}

/** 分组标题（M3 设置规范：labelLarge + primary 色）。 */
@Composable
internal fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}
