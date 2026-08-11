package com.yumiru11.githubapp.core.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.yumiru11.githubapp.core.navigation.AppRoute

/**
 * 应用底部导航栏：首页 / 仓库 / 我的。
 *
 * 图标选中态 filled。
 */
@Composable
fun AppBottomBar(
    selectedTab: String,
    onTabSelected: (String) -> Unit,
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

    NavigationBar(modifier = modifier) {
        tabs.forEach { tab ->
            NavigationBarItem(
                icon = { Icon(tab.icon, contentDescription = null) },
                label = { Text(stringResource(tab.labelRes)) },
                selected = selectedTab == tab.route,
                onClick = { onTabSelected(tab.route) },
            )
        }
    }
}

/** 仓库 Tab 路由（底部导航专用，非 AppRoute 常量） */
const val TAB_REPOS = "repos"
