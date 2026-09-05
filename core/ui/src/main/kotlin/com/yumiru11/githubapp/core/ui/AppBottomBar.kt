package com.yumiru11.githubapp.core.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.yumiru11.githubapp.core.designsystem.component.GlassSurface
import com.yumiru11.githubapp.core.designsystem.theme.AppTheme

/**
 * 应用底部导航栏：首页 / 仓库 / 我的。
 *
 * 图标选中态 filled / 未选空心（Material You 规范）。
 *
 * 玻璃装配（T6 Wave2，ADR-0004 §6.1 允许清单）：[GlassSurface] 包住
 * [NavigationBar]，`windowInsets = navigationBars`（玻璃延伸进手势导航条区域），
 * NavigationBar 自身 insets 归零、containerColor 透明。玻璃层颜色/降级逻辑
 * 同 AppTopBar（见其 KDoc）。
 */
@Composable
fun AppBottomBar(
    selectedTab: String,
    onTabSelected: (String) -> Unit,
    blurEnabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    data class TabItem(
        val route: String,
        val labelRes: Int,
        val selectedIcon: ImageVector,
        val unselectedIcon: ImageVector,
    )

    val tabs =
        listOf(
            TabItem(MainTab.HOME, R.string.nav_home, Icons.Filled.Home, Icons.Outlined.Home),
            TabItem(MainTab.REPOS, R.string.nav_repos, Icons.Filled.Star, Icons.Outlined.Star),
            TabItem(MainTab.PROFILE, R.string.nav_profile, Icons.Filled.Person, Icons.Outlined.Person),
        )

    GlassSurface(
        modifier = modifier,
        windowInsets = WindowInsets.navigationBars,
        blurEnabled = blurEnabled,
    ) {
        NavigationBar(
            containerColor = Color.Transparent,
            windowInsets = WindowInsets(0.dp),
        ) {
            tabs.forEach { tab ->
                val isSelected = selectedTab == tab.route
                NavigationBarItem(
                    icon = {
                        Icon(
                            imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                            contentDescription = null,
                        )
                    },
                    label = { Text(stringResource(tab.labelRes)) },
                    selected = isSelected,
                    onClick = { onTabSelected(tab.route) },
                    // 不传 colors：M3 默认即 onSecondaryContainer/onSecondaryContainer/
                    // secondaryContainer（NavigationBarTokens ItemActive* 三 token），
                    // 此前显式覆盖值恰等于默认（#86 清理冗余）。
                )
            }
        }
    }
}

// ── @Preview（#86）：底栏 Light/Dark 双主题预览 ──

@Preview(name = "Light", showBackground = true)
@Composable
private fun AppBottomBarPreviewLight() {
    AppTheme(darkTheme = false) {
        AppBottomBar(selectedTab = MainTab.HOME, onTabSelected = {})
    }
}

@Preview(name = "Dark", showBackground = true)
@Composable
private fun AppBottomBarPreviewDark() {
    AppTheme(darkTheme = true) {
        AppBottomBar(selectedTab = MainTab.REPOS, onTabSelected = {})
    }
}
