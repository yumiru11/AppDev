@file:Suppress("LongMethod", "CyclomaticComplexMethod")
// NavHost 的 composable 注册样板天然较长（每个 destination 一段），拆散反损可读性；
// 路由数随 feature（T12/T14/T15/T21）增长，圈复杂度随之略超阈值，精准抑制。

package com.yumiru11.githubapp.core.ui
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.yumiru11.githubapp.core.navigation.AppRoute
import com.yumiru11.githubapp.core.navigation.EditorContentHolder
import com.yumiru11.githubapp.core.navigation.link.ParsedUrl
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
 * - [homeScreen]：首页动态流页 Composable（宿主注入，避免 core:ui 依赖 feature:home；
 *   blurEnabled 等参数由宿主在 lambda 闭包内直接传给 feature:home HomeScreen）
 * - [loginScreen]：登录页 Composable（宿主注入，避免 core:ui 依赖 feature:auth）
 * - [repoDetailScreen]：仓库详情页 Composable（宿主注入，避免 core:ui 依赖 feature:repo）
 * - 通知自 #88 起为铃铛触发的覆盖面板（ui-design §3.4），不再有导航 destination
 * - [profileScreen]：个人主页 Composable（宿主注入，避免 core:ui 依赖 feature:profile；
 *   onLoginClick 由宿主接线到 LOGIN 路由）
 * - [settingsScreen]：设置页 Composable（宿主注入，避免 core:ui 依赖 feature:settings）
 * - [editorScreen]：Markdown 编辑器页 Composable（宿主注入，避免 core:ui 依赖 feature:editor；
 *   initialContent 由 [EditorContentHolder] 传递，onClose 由宿主接线返回）
 */
@Composable
fun AppNavHost(
    navController: NavHostController = rememberNavController(),
    modifier: Modifier = Modifier,
    startDestination: String = AppRoute.HOME,
    homeScreen: @Composable () -> Unit = {},
    loginScreen: @Composable () -> Unit = {},
    repoDetailScreen: @Composable (owner: String, repo: String) -> Unit = { _, _ -> },
    profileScreen: @Composable (onLoginClick: () -> Unit, onSettingsClick: () -> Unit) -> Unit = { _, _ -> },
    settingsScreen: @Composable () -> Unit = {},
    issueListScreen: @Composable (
        owner: String,
        repo: String,
        onIssueClick: (owner: String, repo: String, number: Int, isPullRequest: Boolean) -> Unit,
    ) -> Unit = { _, _, _ -> },
    issueDetailScreen: @Composable (owner: String, repo: String, number: Int) -> Unit = { _, _, _ -> },
    pullRequestListScreen: @Composable (
        owner: String,
        repo: String,
        onPullRequestClick: (owner: String, repo: String, number: Int) -> Unit,
    ) -> Unit = { _, _, _ -> },
    pullRequestDetailScreen: @Composable (owner: String, repo: String, number: Int) -> Unit = { _, _, _ -> },
    editorScreen: @Composable (initialContent: String, onClose: () -> Unit) -> Unit = { _, _ -> },
    createIssueScreen: @Composable (owner: String, repo: String) -> Unit = { _, _ -> },
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
            // T10：宿主注入真实首页动态流页（替换 T3 占位 HomeScreen/HomeTabs/HomePager）
            homeScreen()
        }

        composable(AppRoute.SEARCH) {
            SearchScreen()
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
                        onEditMarkdown = { content ->
                            EditorContentHolder.initialContent = content
                            navController.navigate(AppRoute.EDITOR)
                        },
                    ),
            ) {
                repoDetailScreen(owner, repo)
            }
        }

        composable(AppRoute.EDITOR) {
            editorScreen(EditorContentHolder.initialContent) { navController.popBackStack() }
        }

        composable(
            route = AppRoute.ISSUES,
            arguments =
                listOf(
                    navArgument("owner") { type = NavType.StringType },
                    navArgument("repo") { type = NavType.StringType },
                ),
        ) { backStackEntry ->
            val owner = backStackEntry.arguments?.getString("owner") ?: ""
            val repo = backStackEntry.arguments?.getString("repo") ?: ""
            // T13：Issue 列表页；点击项 → PR 走 PR 路由，否则走 Issue 详情路由
            issueListScreen(owner, repo) { o, r, number, isPullRequest ->
                val routeTemplate = if (isPullRequest) AppRoute.PR else AppRoute.ISSUE
                navController.navigate(
                    routeTemplate
                        .replace("{owner}", o)
                        .replace("{repo}", r)
                        .replace("{number}", number.toString()),
                )
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
        ) { backStackEntry ->
            val owner = backStackEntry.arguments?.getString("owner") ?: ""
            val repo = backStackEntry.arguments?.getString("repo") ?: ""
            val number = backStackEntry.arguments?.getInt("number") ?: 0
            // T13：Issue 详情页
            issueDetailScreen(owner, repo, number)
        }

        composable(
            route = AppRoute.PULLS,
            arguments =
                listOf(
                    navArgument("owner") { type = NavType.StringType },
                    navArgument("repo") { type = NavType.StringType },
                ),
        ) { backStackEntry ->
            val owner = backStackEntry.arguments?.getString("owner") ?: ""
            val repo = backStackEntry.arguments?.getString("repo") ?: ""
            // T15：PR 列表页；点击项 → PR 详情路由
            pullRequestListScreen(owner, repo) { o, r, number ->
                navController.navigate(
                    AppRoute.PR
                        .replace("{owner}", o)
                        .replace("{repo}", r)
                        .replace("{number}", number.toString()),
                )
            }
        }

        composable(
            route = AppRoute.ISSUE_CREATE,
            arguments =
                listOf(
                    navArgument("owner") { type = NavType.StringType },
                    navArgument("repo") { type = NavType.StringType },
                ),
        ) { backStackEntry ->
            val owner = backStackEntry.arguments?.getString("owner") ?: ""
            val repo = backStackEntry.arguments?.getString("repo") ?: ""
            // T14：创建 Issue 页；成功后返回列表
            createIssueScreen(owner, repo)
        }

        composable(
            route = AppRoute.PR,
            arguments =
                listOf(
                    navArgument("owner") { type = NavType.StringType },
                    navArgument("repo") { type = NavType.StringType },
                    navArgument("number") { type = NavType.IntType },
                ),
        ) { backStackEntry ->
            val owner = backStackEntry.arguments?.getString("owner") ?: ""
            val repo = backStackEntry.arguments?.getString("repo") ?: ""
            val number = backStackEntry.arguments?.getInt("number") ?: 0
            // T15：PR 详情页（四 Tab）
            pullRequestDetailScreen(owner, repo, number)
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
