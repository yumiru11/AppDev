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
import com.yumiru11.githubapp.core.ui.screens.ReposScreen
import com.yumiru11.githubapp.core.ui.screens.SearchScreen

/**
 * 应用导航宿主：入口 Composable，内部 Navigation Compose NavHost。
 *
 * 注册各 route 占位 destination；接收 [ParsedUrl] → [AppRoute.fromParsedUrl] 的
 * 外链导航能力（供外部消费）。
 */
@Composable
fun AppNavHost(
    navController: NavHostController = rememberNavController(),
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = AppRoute.HOME,
        modifier = modifier,
    ) {
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
            ReposScreen(owner = owner, repo = repo)
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
