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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yumiru11.githubapp.core.datastore.model.resolveEffectiveThemeMode
import com.yumiru11.githubapp.core.designsystem.theme.AppTheme
import com.yumiru11.githubapp.core.designsystem.token.AppMotion
import com.yumiru11.githubapp.core.designsystem.token.rememberSystemMotionScale
import com.yumiru11.githubapp.core.designsystem.token.resolveEffectiveMotionScale

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
    onBackClick: () -> Unit,
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
    // #87：设置页内层 AppTheme 与全局宿主同口径注入两缩放——修复「页内滑杆
    // 实时预览断链」（内层此前缺省复位为 1f，拖动滑杆仅预览卡变化、页面内
    // MaterialTheme 形状/动效不实时跟随）。
    val effectiveMotionScale =
        resolveEffectiveMotionScale(
            userScale = uiState.motionScale,
            systemScale = rememberSystemMotionScale(),
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
        AppTheme(
            themeMode = mode,
            seedColor = Color(uiState.seedColor),
            cornerScale = uiState.cornerScale,
            motionScale = effectiveMotionScale,
        ) {
            Scaffold(
                topBar = { SettingsTopBar(onBackClick = onBackClick) },
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
                        AppearanceSettingsSection(
                            uiState = uiState,
                            viewModel = viewModel,
                            // #167 / UI04：Photo Picker 选中 → 交给 ViewModel 持久化。
                            // 持久化读权限在 AppearanceSettingsSection 内取（那里才有 Context 与 launcher）。
                            onPickBackgroundImage = viewModel::setBackgroundImageUri,
                        )
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

/**
 * 设置页顶栏（UI-3）：设置是二级页（自个人页/入口导航进入），补返回箭头 ——
 * 此前屏内无返回可供性，只能靠系统返回手势/按键。图标用项目统一矢量图标
 * （AutoMirrored，RTL 自动镜像），文案走 stringResource。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsTopBar(onBackClick: () -> Unit) {
    TopAppBar(
        title = { Text(stringResource(R.string.settings_title)) },
        navigationIcon = {
            IconButton(onClick = onBackClick) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.settings_back),
                )
            }
        },
    )
}

/** 分组标题（M3 设置规范：labelLarge + primary 色；`heading()` 语义供 TalkBack 按节跳转，缺陷 #19）。 */
@Composable
internal fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier =
            Modifier
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .semantics { heading() },
    )
}
