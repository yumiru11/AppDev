package com.yumiru11.githubapp.core.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yumiru11.githubapp.core.designsystem.component.GlassSurface
import com.yumiru11.githubapp.core.navigation.AppRoute

/**
 * 应用底部导航栏：首页 / 仓库 / 我的。
 *
 * 图标选中态 filled。
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
        val icon: ImageVector,
    )

    val tabs =
        listOf(
            TabItem(AppRoute.HOME, R.string.nav_home, Icons.Default.Home),
            TabItem(TAB_REPOS, R.string.nav_repos, Icons.Default.Star),
            TabItem(AppRoute.PROFILE, R.string.nav_profile, Icons.Default.Person),
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
                NavigationBarItem(
                    icon = { Icon(tab.icon, contentDescription = null) },
                    label = { Text(stringResource(tab.labelRes)) },
                    selected = selectedTab == tab.route,
                    onClick = { onTabSelected(tab.route) },
                    colors =
                        NavigationBarItemDefaults.colors(
                            // 选中实心高亮（P1-5：胶囊+实心，2026-08-14 真机走查决策）
                            selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            selectedTextColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                        ),
                )
            }
        }
    }
}

/** 仓库 Tab 路由（底部导航专用，非 AppRoute 常量） */
const val TAB_REPOS = "repos"
