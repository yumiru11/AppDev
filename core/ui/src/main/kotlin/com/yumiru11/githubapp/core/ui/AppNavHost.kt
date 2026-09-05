@file:Suppress("LongMethod", "CyclomaticComplexMethod")
// NavHost 的 composable 注册样板天然较长（每个 destination 一段），拆散反损可读性；
// 路由数随 feature（T12/T14/T15/T21）增长，圈复杂度随之略超阈值，精准抑制。

package com.yumiru11.githubapp.core.ui

import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.yumiru11.githubapp.core.designsystem.token.AppMotion
import com.yumiru11.githubapp.core.designsystem.token.LocalMotionScale
import com.yumiru11.githubapp.core.navigation.AppRoute
import com.yumiru11.githubapp.core.navigation.EditorContentHolder
import com.yumiru11.githubapp.core.navigation.link.ParsedUrl
import com.yumiru11.githubapp.core.ui.screens.ProfileScreen
import com.yumiru11.githubapp.core.ui.screens.SearchScreen as PlaceholderSearchScreen

/**
 * 应用导航宿主：入口 Composable，内部 Navigation Compose NavHost（#90 类型安全路由）。
 *
 * 注册各类型安全 destination；接收 [ParsedUrl] → [AppRoute.fromParsedUrl] 的
 * 外链导航能力（供外部消费）。
 *
 * - [startDestination]：起始 destination（T4 Wave2 登录态驱动首屏：Anonymous → 登录页，
 *   由宿主按 AuthState 传入；默认 [AppRoute.Home] 保持向后兼容）
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
    startDestination: AppRoute = AppRoute.Home,
    homeScreen: @Composable () -> Unit = {},
    loginScreen: @Composable () -> Unit = {},
    searchScreen: @Composable () -> Unit = {},
    repoDetailScreen: @Composable (owner: String, repo: String, ref: String) -> Unit = { _, _, _ -> },
    blobScreen:
        @Composable (owner: String, repo: String, ref: String, path: String) -> Unit = { _, _, _, _ -> },
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
    branchesScreen: @Composable (
        owner: String,
        repo: String,
        currentRef: String?,
        onBackClick: () -> Unit,
        onBranchSelected: (String) -> Unit,
    ) -> Unit = { _, _, _, _, _ -> },
    createPullRequestScreen: @Composable (
        owner: String,
        repo: String,
        onCreated: (owner: String, repo: String, number: Int) -> Unit,
    ) -> Unit = { _, _, _ -> },
    editorScreen: @Composable (initialContent: String, onClose: () -> Unit) -> Unit = { _, _ -> },
    createIssueScreen: @Composable (owner: String, repo: String) -> Unit = { _, _ -> },
) {
    // #90 全局默认转场：push = 右侧 1/4 滑入 + 淡入（EmphasizedDecelerate 400ms），
    // pop = 当前页缩至 0.9 + 淡出；时长按 LocalMotionScale（设置页动效滑杆 × 系统
    // 动画缩放）折算。Navigation 2.8.4 的转场 lambda 非 @Composable，须在组合外捕获
    // 缩放值后以纯函数构造（AppMotion.scaledDuration(base, scale) 可单测重载）。
    // 底栏三分区是 HOME 内 pager，不经过导航转场（无「弹窗感」）。
    val motionScale = LocalMotionScale.current
    NavHost(
        navController = navController,
        // 起始 destination 仅可能是 Home/Login（无参路由）：pattern = @SerialName
        startDestination =
            when (startDestination) {
                is AppRoute.Home -> AppRoute.startDestinationPattern<AppRoute.Home>()
                is AppRoute.Login -> AppRoute.startDestinationPattern<AppRoute.Login>()
                else -> error("起始 destination 仅支持无参路由：$startDestination")
            },
        modifier = modifier,
        enterTransition = { appEnterTransition(motionScale) },
        exitTransition = { appExitTransition(motionScale) },
        popEnterTransition = { appPopEnterTransition(motionScale) },
        popExitTransition = { appPopExitTransition(motionScale) },
    ) {
        composable<AppRoute.Login> {
            loginScreen()
        }

        composable<AppRoute.Home> {
            // T10：宿主注入真实首页动态流页（替换 T3 占位 HomeScreen/HomeTabs/HomePager）
            homeScreen()
        }

        composable<AppRoute.Search> {
            // T18 真实搜索屏（feature/search）；此前误挂 core.ui 占位组件致「Coming soon」
            searchScreen()
        }

        composable<AppRoute.Settings> {
            settingsScreen()
        }

        composable<AppRoute.Repo> { backStackEntry ->
            val route = backStackEntry.toRoute<AppRoute.Repo>()
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
                            navController.navigate(AppRoute.Editor)
                        },
                    ),
            ) {
                repoDetailScreen(route.owner, route.repo, route.ref)
            }
        }

        composable<AppRoute.Editor> {
            editorScreen(EditorContentHolder.initialContent) { navController.popBackStack() }
        }

        composable<AppRoute.Issues> { backStackEntry ->
            val route = backStackEntry.toRoute<AppRoute.Issues>()
            // T13：Issue 列表页；点击项 → PR 走 PR 路由，否则走 Issue 详情路由
            issueListScreen(route.owner, route.repo) { o, r, number, isPullRequest ->
                navController.navigate(
                    if (isPullRequest) {
                        AppRoute.Pr(o, r, number)
                    } else {
                        AppRoute.Issue(o, r, number)
                    },
                )
            }
        }

        composable<AppRoute.Issue> { backStackEntry ->
            val route = backStackEntry.toRoute<AppRoute.Issue>()
            // T13：Issue 详情页
            issueDetailScreen(route.owner, route.repo, route.number)
        }

        composable<AppRoute.Pulls> { backStackEntry ->
            val route = backStackEntry.toRoute<AppRoute.Pulls>()
            // T15：PR 列表页；点击项 → PR 详情路由
            pullRequestListScreen(route.owner, route.repo) { o, r, number ->
                navController.navigate(AppRoute.Pr(o, r, number))
            }
        }

        composable<AppRoute.IssueCreate> { backStackEntry ->
            val route = backStackEntry.toRoute<AppRoute.IssueCreate>()
            // T14：创建 Issue 页；成功后返回列表
            createIssueScreen(route.owner, route.repo)
        }

        composable<AppRoute.PrCreate> { backStackEntry ->
            val route = backStackEntry.toRoute<AppRoute.PrCreate>()
            // T23：创建 PR 页；成功后清出本页并打开新 PR 详情
            createPullRequestScreen(route.owner, route.repo) { o, r, number ->
                navController.navigate(AppRoute.Pr(o, r, number)) {
                    popUpTo(AppRoute.PrCreate(o, r)) { inclusive = true }
                }
            }
        }

        composable<AppRoute.Branches> { backStackEntry ->
            val route = backStackEntry.toRoute<AppRoute.Branches>()
            // T23：分支管理页；切换分支 → 带 ref 重进仓库详情（旧 REPO 页一并弹出）。
            // ref/path 等参数在类型安全路由下由 navigation 参数序列化器编码，无需手工 URLEncoder
            branchesScreen(
                route.owner,
                route.repo,
                route.ref.ifBlank { null },
                { navController.popBackStack() },
                { branch ->
                    navController.navigate(AppRoute.Repo(route.owner, route.repo, branch)) {
                        popUpTo(AppRoute.Repo(route.owner, route.repo)) { inclusive = true }
                    }
                },
            )
        }

        composable<AppRoute.Pr> { backStackEntry ->
            val route = backStackEntry.toRoute<AppRoute.Pr>()
            // T15：PR 详情页（四 Tab）
            pullRequestDetailScreen(route.owner, route.repo, route.number)
        }

        composable<AppRoute.User> {
            profileScreen(
                { navController.navigate(AppRoute.Login) },
                { navController.navigate(AppRoute.Settings) },
            )
        }

        // T5+ Commit 详情页（真实屏未开发；暂以占位承载，避免悬空路由）
        composable<AppRoute.Commit> {
            PlaceholderSearchScreen()
        }

        // Discussion 深链历史崩溃修复（#90）：GitHubLinkParser 会产出 ParsedUrl.Discussion，
        // 旧字符串体系下映射出的路由无对应 destination → 导航即崩溃；注册占位与 COMMIT 同策略
        composable<AppRoute.Discussion> {
            PlaceholderSearchScreen()
        }

        composable<AppRoute.Blob> { backStackEntry ->
            val route = backStackEntry.toRoute<AppRoute.Blob>()
            // T11：blob 深链直达 FileViewer（此前误挂占位组件致「Coming soon」；
            // 多段 path 由参数序列化器编码进 query，Navigation 自动解码）
            blobScreen(route.owner, route.repo, route.ref, route.path)
        }
    }
}

/**
 * 处理 [ParsedUrl] 外链导航。
 *
 * - [ParsedUrl.External] → 返回 false（由 [ExternalLinkHost] 处理）
 * - 其他 → 导航到对应类型安全 route 并返回 true
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

// ── #90 全局转场规格（来源 ui-audit 提案 #6） ──
//
// push：新页自右 1/4 宽滑入 + 淡入（M3 EmphasizedDecelerate 400ms）；
// pop：当前页缩至 0.9 + 淡出。全部时长经 AppMotion.scaledDuration 折算
// （设置页动效滑杆 × 系统动画缩放，尊重「移除动画」）。转场随预测返回
// 手势（enableOnBackInvokedCallback）进度驱动，Android 14+ 返回预览同源。
//
// ⚠️ Navigation 2.8.4 NavHost 的转场 lambda 为普通（非 @Composable）函数，
// 故时长在此以纯函数 AppMotion.scaledDuration(base, motionScale) 构造，
// motionScale 由组合内 LocalMotionScale.current 捕获传入。

/** push 进入：右侧 1/4 宽滑入 + 淡入。 */
private fun appEnterTransition(motionScale: Float): EnterTransition {
    val duration = AppMotion.scaledDuration(AppMotion.DURATION_PAGE_ENTER, motionScale)
    return slideInHorizontally(
        initialOffsetX = { it / 4 },
        animationSpec = tween(duration, easing = AppMotion.EmphasizedDecelerate),
    ) + fadeIn(animationSpec = tween(duration, easing = AppMotion.EmphasizedDecelerate))
}

/** push 退出（被覆盖页）：淡出（M3 EmphasizedAccelerate 200ms）。 */
private fun appExitTransition(motionScale: Float): ExitTransition {
    val duration = AppMotion.scaledDuration(AppMotion.DURATION_PAGE_EXIT, motionScale)
    return fadeOut(animationSpec = tween(duration, easing = AppMotion.EmphasizedAccelerate))
}

/** pop 进入（返回时下层页）：左侧 1/4 宽滑入 + 淡入，与 push 对称。 */
private fun appPopEnterTransition(motionScale: Float): EnterTransition {
    val duration = AppMotion.scaledDuration(AppMotion.DURATION_PAGE_ENTER, motionScale)
    return slideInHorizontally(
        initialOffsetX = { -it / 4 },
        animationSpec = tween(duration, easing = AppMotion.EmphasizedDecelerate),
    ) + fadeIn(animationSpec = tween(duration, easing = AppMotion.EmphasizedDecelerate))
}

/** pop 退出（返回时当前页）：缩至 0.9 + 淡出。 */
private fun appPopExitTransition(motionScale: Float): ExitTransition {
    val duration = AppMotion.scaledDuration(AppMotion.DURATION_PAGE_ENTER, motionScale)
    return scaleOut(
        targetScale = 0.9f,
        animationSpec = tween(duration, easing = AppMotion.EmphasizedAccelerate),
    ) + fadeOut(animationSpec = tween(duration, easing = AppMotion.EmphasizedAccelerate))
}