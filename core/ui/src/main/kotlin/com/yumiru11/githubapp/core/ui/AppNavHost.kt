@file:Suppress("LongMethod")
// NavHost 的 composable 注册样板天然较长（每个 destination 一段），拆散反损可读性；精准抑制。

package com.yumiru11.githubapp.core.ui
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.yumiru11.githubapp.core.navigation.AppRoute
import com.yumiru11.githubapp.core.navigation.link.ParsedUrl
import com.yumiru11.githubapp.core.ui.screens.HomeScreen
import com.yumiru11.githubapp.core.ui.screens.NotificationScreen
import com.yumiru11.githubapp.core.ui.screens.ProfileScreen
import com.yumiru11.githubapp.core.ui.screens.SearchScreen

/**
 * 应用导航宿主：入口 Composable，内部 Navigation Compose NavHost。
 *
 * 注册各 route destination；接收 [ParsedUrl] → [AppRoute.fromParsedUrl] 的
 * 外链导航能力（供外部消费）。
 *
 * - [startDestination]：起始 destination（T4 Wave2 登录态驱动首屏：Anonymous → 登录页，
 *   由宿主按 AuthState 传入；默认 HOME 保持向后兼容）
 * - [loginScreen]：登录页 Composable（宿主注入，避免 core:ui 依赖 feature:auth）
 * - [repoDetailScreen]：仓库详情页 Composable（宿主注入，避免 core:ui 依赖 feature:repo）
 * - [notificationsScreen]：通知页 Composable（宿主注入，避免 core:ui 依赖 feature:notifications）
 * - [profileScreen]：个人主页 Composable（宿主注入，避免 core:ui 依赖 feature:profile；
 *   onLoginClick 由宿主接线到 LOGIN 路由）
 * - [settingsScreen]：设置页 Composable（宿主注入，避免 core:ui 依赖 feature:settings）
 */
@Composable
fun AppNavHost(
    navController: NavHostController = rememberNavController(),
    modifier: Modifier = Modifier,
    startDestination: String = AppRoute.HOME,
    blurEnabled: Boolean = true,
    loginScreen: @Composable () -> Unit = {},
    repoDetailScreen: @Composable (owner: String, repo: String) -> Unit = { _, _ -> },
    notificationsScreen: @Composable () -> Unit = {},
    profileScreen: @Composable (onLoginClick: () -> Unit, onSettingsClick: () -> Unit) -> Unit = { _, _ -> },
    settingsScreen: @Composable () -> Unit = {},
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
    ) {
        composable(AppRoute.LOGIN) {
            loginScreen()
        }

        composable(AppRoute.HOME) {
            Column(modifier = Modifier.fillMaxSize()) {
                // T9 验收第 1 条：owner/repo 输入入口（最小可用，不重设计 HomeScreen 布局）
                OpenRepoEntry(
                    onOpen = { owner, repo ->
                        navController.navigate(
                            AppRoute.REPO
                                .replace("{owner}", owner)
                                .replace("{repo}", repo),
                        )
                    },
                )
                HomeScreen(
                    onSearchClick = { navController.navigate(AppRoute.SEARCH) },
                    onNotificationClick = { navController.navigate(AppRoute.NOTIFICATION) },
                    onProfileClick = { navController.navigate(AppRoute.PROFILE) },
                    onTabSelected = { route ->
                        navController.navigate(route) {
                            popUpTo(AppRoute.HOME) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    selectedTab = AppRoute.HOME,
                    blurEnabled = blurEnabled,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        composable(AppRoute.SEARCH) {
            SearchScreen()
        }

        // 通知页（T19，docs/ui-design.md §3.4）：全屏 slide-in 面板——从顶部滑入
        // （与 T3 占位 NotificationPanel 的滑入方向一致），退出反向滑出
        composable(
            route = AppRoute.NOTIFICATION,
            enterTransition = { slideInVertically(initialOffsetY = { -it }) + fadeIn() },
            exitTransition = { slideOutVertically(targetOffsetY = { -it }) + fadeOut() },
        ) {
            notificationsScreen()
        }

        composable(AppRoute.PROFILE) {
            // T20：宿主注入真实 ProfileScreen；T24：设置入口经 onSettingsClick 由 Feature ProfileScreen 透传
            profileScreen(
                { navController.navigate(AppRoute.LOGIN) },
                { navController.navigate(AppRoute.SETTINGS) },
            )
        }

        composable(AppRoute.SETTINGS) {
            settingsScreen()
        }

        composable(
            route = AppRoute.REPO,
            arguments =
                listOf(
                    navArgument("owner") { type = NavType.StringType },
                    navArgument("repo") { type = NavType.StringType },
                ),
        ) { backStackEntry ->
            val owner = backStackEntry.arguments?.getString("owner") ?: ""
            val repo = backStackEntry.arguments?.getString("repo") ?: ""
            val context = LocalContext.current
            // T9 验收第 3 条：README 链接接线——内部链接应用内导航，外部链接 CustomTabs
            CompositionLocalProvider(
                LocalRepoDetailActions provides
                    RepoDetailActions(
                        onNavigateToParsedUrl = { parsed -> navigateToParsedUrl(navController, parsed) },
                        onOpenExternal = { url ->
                            CustomTabsIntent.Builder().build().launchUrl(context, Uri.parse(url))
                        },
                    ),
            ) {
                repoDetailScreen(owner, repo)
            }
        }

        composable(
            route = AppRoute.ISSUE,
            arguments =
                listOf(
                    navArgument("owner") { type = NavType.StringType },
                    navArgument("repo") { type = NavType.StringType },
                    navArgument("number") { type = NavType.IntType },
                ),
        ) {
            // T5+ Issue 详情页
            SearchScreen()
        }

        composable(
            route = AppRoute.PR,
            arguments =
                listOf(
                    navArgument("owner") { type = NavType.StringType },
                    navArgument("repo") { type = NavType.StringType },
                    navArgument("number") { type = NavType.IntType },
                ),
        ) {
            // T5+ PR 详情页
            SearchScreen()
        }

        composable(
            route = AppRoute.USER,
            arguments =
                listOf(
                    navArgument("login") { type = NavType.StringType },
                ),
        ) {
            profileScreen(
                { navController.navigate(AppRoute.LOGIN) },
                { navController.navigate(AppRoute.SETTINGS) },
            )
        }

        composable(
            route = AppRoute.COMMIT,
            arguments =
                listOf(
                    navArgument("owner") { type = NavType.StringType },
                    navArgument("repo") { type = NavType.StringType },
                    navArgument("sha") { type = NavType.StringType },
                ),
        ) {
            // T5+ Commit 详情页
            SearchScreen()
        }

        composable(
            route = AppRoute.BLOB,
            arguments =
                listOf(
                    navArgument("owner") { type = NavType.StringType },
                    navArgument("repo") { type = NavType.StringType },
                    navArgument("ref") { type = NavType.StringType },
                    navArgument("path") { type = NavType.StringType },
                ),
        ) {
            // T5+ Blob 详情页
            SearchScreen()
        }

        composable("repos") {
            // T5+ 仓库列表页
            SearchScreen()
        }
    }
}

/**
 * 处理 [ParsedUrl] 外链导航。
 *
 * - [ParsedUrl.External] → 返回 false（由 [ExternalLinkHost] 处理）
 * - 其他 → 导航到对应 route 并返回 true
 */
fun navigateToParsedUrl(
    navController: NavHostController,
    parsedUrl: ParsedUrl,
): Boolean {
    if (parsedUrl is ParsedUrl.External) return false
    val route = AppRoute.fromParsedUrl(parsedUrl) ?: return false
    navController.navigate(route)
    return true
}

/**
 * 最小「打开仓库」输入入口（T9 验收第 1 条）。
 *
 * 输入 `owner/repo` → 格式校验（GitHub 用户名/仓库名合法字符）→ 导航 REPO 路由。
 */
@Composable
private fun OpenRepoEntry(
    onOpen: (owner: String, repo: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var input by remember { mutableStateOf("") }
    var showError by remember { mutableStateOf(false) }
    val ownerRepoRegex = remember { Regex("^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$") }

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = input,
            onValueChange = {
                input = it
                showError = false
            },
            modifier = Modifier.weight(1f),
            placeholder = { Text(stringResource(R.string.open_repo_hint)) },
            singleLine = true,
            isError = showError,
            supportingText =
                if (showError) {
                    { Text(stringResource(R.string.open_repo_invalid)) }
                } else {
                    null
                },
        )
        Spacer(modifier = Modifier.width(8.dp))
        Button(
            onClick = {
                val trimmed = input.trim()
                if (ownerRepoRegex.matches(trimmed)) {
                    val (owner, repo) = trimmed.split('/', limit = 2)
                    onOpen(owner, repo)
                } else {
                    showError = true
                }
            },
        ) {
            Text(text = stringResource(R.string.open_repo_button))
        }
    }
}
