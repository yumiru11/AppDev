@file:Suppress("LongMethod")
// NavHost 的 composable 注册样板天然较长（每个 destination 一段），拆散反损可读性；精准抑制。

package com.yumiru11.githubapp.core.ui
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
 */
@Composable
fun AppNavHost(
    navController: NavHostController = rememberNavController(),
    modifier: Modifier = Modifier,
    startDestination: String = AppRoute.HOME,
    blurEnabled: Boolean = true,
    loginScreen: @Composable () -> Unit = {},
    repoDetailScreen: @Composable (owner: String, repo: String) -> Unit = { _, _ -> },
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
            )
        }

        composable(AppRoute.SEARCH) {
            SearchScreen()
        }

        composable(AppRoute.NOTIFICATION) {
            NotificationScreen()
        }

        composable(AppRoute.PROFILE) {
            ProfileScreen()
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
            repoDetailScreen(owner, repo)
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
            ProfileScreen()
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
