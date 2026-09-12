@file:Suppress("LongMethod") // onCreate 聚合导航装配（T19 通知 + T20 Profile + T24 设置 + 语言切换），拆分收益低于装配强内聚

package com.yumiru11.githubapp

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.rememberNavController
import com.yumiru11.githubapp.auth.authStateToDestination
import com.yumiru11.githubapp.auth.shouldNavigateForAuthState
import com.yumiru11.githubapp.core.datastore.preferences.UserPreferencesRepository
import com.yumiru11.githubapp.core.designsystem.component.LocalHazeState
import com.yumiru11.githubapp.core.designsystem.token.GlassRenderPolicy
import com.yumiru11.githubapp.core.designsystem.token.GlassScope
import com.yumiru11.githubapp.core.designsystem.token.LocalGlassSettings
import com.yumiru11.githubapp.core.githubauth.auth.AuthState
import com.yumiru11.githubapp.core.githubauth.auth.OAuthCallbackException
import com.yumiru11.githubapp.core.githubauth.auth.OAuthConfig
import com.yumiru11.githubapp.core.githubauth.auth.OAuthSessionManager
import com.yumiru11.githubapp.core.githubauth.auth.TokenExchangeException
import com.yumiru11.githubapp.core.navigation.AppRoute
import com.yumiru11.githubapp.core.navigation.link.GitHubLinkParser
import com.yumiru11.githubapp.core.navigation.link.ParsedUrl
import com.yumiru11.githubapp.core.ui.AppNavHost
import com.yumiru11.githubapp.core.ui.MainTab
import com.yumiru11.githubapp.core.ui.MainTabPager
import com.yumiru11.githubapp.core.ui.RepoDetailActions
import com.yumiru11.githubapp.core.ui.navigateToParsedUrl
import com.yumiru11.githubapp.core.ui.openExternalBrowser
import com.yumiru11.githubapp.feature.auth.AuthNavigation
import com.yumiru11.githubapp.feature.auth.AuthViewModel
import com.yumiru11.githubapp.feature.auth.LoginScreen
import com.yumiru11.githubapp.feature.editor.MarkdownEditorScreen
import com.yumiru11.githubapp.feature.home.HomeScreen
import com.yumiru11.githubapp.feature.issue.CreateIssueScreen
import com.yumiru11.githubapp.feature.issue.IssueDetailScreen
import com.yumiru11.githubapp.feature.issue.IssueListScreen
import com.yumiru11.githubapp.feature.notifications.ui.NotificationsPanel
import com.yumiru11.githubapp.feature.profile.GistsScreen
import com.yumiru11.githubapp.feature.profile.ProfileScreen
import com.yumiru11.githubapp.feature.pullrequest.PullRequestCreateScreen
import com.yumiru11.githubapp.feature.pullrequest.PullRequestDetailScreen
import com.yumiru11.githubapp.feature.pullrequest.PullRequestListScreen
import com.yumiru11.githubapp.feature.repo.BranchesScreen
import com.yumiru11.githubapp.feature.repo.CommitDetailScreen
import com.yumiru11.githubapp.feature.repo.CreateRepoScreen
import com.yumiru11.githubapp.feature.repo.FileEditEventSnackbar
import com.yumiru11.githubapp.feature.repo.FileEditHost
import com.yumiru11.githubapp.feature.repo.FileViewerScreen
import com.yumiru11.githubapp.feature.repo.ReleaseCreateScreen
import com.yumiru11.githubapp.feature.repo.RepoDetailScreen
import com.yumiru11.githubapp.feature.repo.RepoFilesViewModel
import com.yumiru11.githubapp.feature.repo.ReposScreen
import com.yumiru11.githubapp.feature.search.SearchScreen
import com.yumiru11.githubapp.feature.search.SearchViewModel
import com.yumiru11.githubapp.feature.settings.SettingsScreen
import com.yumiru11.githubapp.feature.settings.SettingsViewModel
import dagger.hilt.android.AndroidEntryPoint
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

/**
 * 单 Activity 入口：edge-to-edge 沉浸式 + 导航骨架托管 + GitHub 深链路由 + OAuth 回调。
 *
 * - 登录态驱动首屏（T4 Wave2）：AuthState.Anonymous → 登录页；SignedIn/PAT → 主页。
 *   起始 destination 按 authState 传入 AppNavHost；状态变化经 LaunchedEffect 导航
 *   （仅目标页不符时 navigate，popUpTo(0) 清栈防循环）
 * - 主题（T6 Wave2）：[AppThemeHost] 把仓库持久化的 ThemeMode 接到 AppTheme；
 *   毛玻璃开关经 LocalGlassSettings 下发（#167：四条点位各自可关；AppThemeHost 合成）
 * - OAuth 回调（ADR-0001 自定义 scheme）：命中 oauth-callback 的 intent data →
 *   [OAuthSessionManager.handleCallback]（token 交换），成功后 authState 自动变
 *   SignedIn → 登录态导航接管跳主页；失败（用户取消/错误回调）留在登录页
 * - 深链（GitHub 链接）：[GitHubLinkParser] 解析 → [navigateToParsedUrl] 应用内导航；
 *   [ParsedUrl.External] / 无法识别 → 不导航（由 Custom Tabs / 系统兜底）
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val authViewModel: AuthViewModel by viewModels()

    private val settingsViewModel: SettingsViewModel by viewModels()

    @Inject lateinit var sessionManager: OAuthSessionManager

    // 主题/偏好仓库（T6 Wave2）：themeMode/玻璃/圆角/动效 → AppThemeHost（含 LocalGlassSettings）
    @Inject lateinit var userPreferencesRepository: UserPreferencesRepository

    // 待消费的深链 URI（冷启动 + onNewIntent 运行时）；用 mutableStateOf 以便 Compose 观察重组
    private val pendingDeepLink = mutableStateOf<Uri?>(null)

    // OAuth 回调 scheme（取自 OAuthConfig.REDIRECT_URI 单一事实来源，不重复硬编码字面量）
    private val oauthCallbackScheme: String? = Uri.parse(OAuthConfig.REDIRECT_URI).scheme

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(applyLanguageLocale(newBase, cachedLanguageTag))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // 品牌化启动屏（D3）：必须在 super.onCreate 之前安装 ——
        // 它负责把 window 主题从 Theme.AppDev.Splash 交棒给 postSplashScreenTheme。
        // 放在后面会抛 IllegalStateException，且冷启动空窗依旧。
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // 三键导航栏不再由系统叠半透明 scrim（P0 系统栏 insets 修复）：
        // 官方 edge-to-edge 指南要求显式置 false，否则系统那层 ~50% 黑会盖在
        // 延伸进导航栏区域的底栏玻璃之上，把底缘压成黑带（完整依据见
        // disableNavigationBarContrastScrim 的 KDoc）。手势导航不受影响。
        window.disableNavigationBarContrastScrim()
        handleIntentData(intent?.data)

        setContent {
            val navController = rememberNavController()
            val context = LocalContext.current
            val authState by authViewModel.authState.collectAsStateWithLifecycle()
            // 毛玻璃开关不再在此收集：AppThemeHost 从仓库合成 GlassSettings 并经
            // LocalGlassSettings 下发（#167 / UI03），顶/底栏/面板/BottomSheet 各按 scope 自取
            // 语言偏好（T24 设置页语言切换）：变化 → 缓存 + recreate 应用 locale
            val languageTag by userPreferencesRepository.languageTag.collectAsStateWithLifecycle(initialValue = null)
            // 底部三分区当前页（分区重构 2026-08-14：tab 切换 = pager 横滑，非导航）
            var mainTab by rememberSaveable { mutableStateOf(MainTab.HOME) }

            AppThemeHost(repository = userPreferencesRepository) {
                // 根级 HazeState（#88）：通知面板玻璃 backdrop-blur 整个 NavHost 内容。
                // MainTabPager/HomeScreen 自建内层 state 只服务其顶/底栏，面板读不到内层值
                val rootHazeState = rememberHazeState()
                var notificationPanelVisible by rememberSaveable { mutableStateOf(false) }
                // 根级 source 只服务通知面板玻璃 → 按 PANEL 点位判定（#167 / UI03）
                val panelGlassEnabled = LocalGlassSettings.current.enabledFor(GlassScope.PANEL)
                CompositionLocalProvider(LocalHazeState provides rootHazeState) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .then(
                                        if (GlassRenderPolicy.shouldAttachHazeSource(panelGlassEnabled)) {
                                            Modifier.hazeSource(rootHazeState)
                                        } else {
                                            Modifier
                                        },
                                    ),
                        ) {
                            AppNavHost(
                                navController = navController,
                                startDestination = authStateToDestination(authState),
                                homeScreen = {
                                    MainTabPager(
                                        selectedTab = mainTab,
                                        onTabSelected = { mainTab = it },
                                        homePage = { padding ->
                                            HomeScreen(
                                                onSearchClick = { navController.navigate(AppRoute.Search()) },
                                                onNotificationClick = { notificationPanelVisible = true },
                                                onProfileClick = { mainTab = MainTab.PROFILE },
                                                onCreateIssue = { owner, repo ->
                                                    navController.navigate(AppRoute.IssueCreate(owner, repo))
                                                },
                                                onViewPullRequests = { owner, repo ->
                                                    navController.navigate(AppRoute.Pulls(owner, repo))
                                                },
                                                // L04：未登录先引导登录（游客无法创建仓库）
                                                onCreateRepo = {
                                                    if (authState is AuthState.Anonymous) {
                                                        navController.navigate(AppRoute.Login) {
                                                            popUpTo(0) { inclusive = true }
                                                        }
                                                    } else {
                                                        navController.navigate(AppRoute.CreateRepo)
                                                    }
                                                },
                                                onLoginClick = {
                                                    navController.navigate(AppRoute.Login) {
                                                        popUpTo(0) { inclusive = true }
                                                    }
                                                },
                                                onFeedItemClick = { parsed -> navigateToParsedUrl(navController, parsed) },
                                                bottomContentPadding = padding.calculateBottomPadding(),
                                            )
                                        },
                                        reposPage = { padding ->
                                            // 「仓库」大分区（#166 / UI01+UI02）：此前是 PlaceholderScreen 占位，
                                            // 三个底部 Tab 里有一个点进去是空壳。现已落地完整形态（列表/网格切换 +
                                            // 长按菜单 + 三态占位 + 游客引导）。
                                            // 底部避让与 Home/Profile 同一契约：MainTabPager 的底栏不在
                                            // Scaffold 的 contentWindowInsets 里（容器显式归零），必须把
                                            // padding 形参转成 ReposScreen 的 bottomContentPadding——
                                            // 曾经这里丢弃形参且注释自称「ReposScreen 内部处理」，导致
                                            // 仓库分区列表整体少预留一个底栏总高（含系统导航栏 inset），
                                            // 末行被底栏压字（P0）。
                                            ReposScreen(
                                                onOpenRepository = { owner, repo ->
                                                    navController.navigate(AppRoute.Repo(owner, repo))
                                                },
                                                onLoginClick = {
                                                    navController.navigate(AppRoute.Login) {
                                                        popUpTo(0) { inclusive = true }
                                                    }
                                                },
                                                onSearchClick = { navController.navigate(AppRoute.Search()) },
                                                onNotificationClick = { notificationPanelVisible = true },
                                                onProfileClick = { mainTab = MainTab.PROFILE },
                                                bottomContentPadding = padding.calculateBottomPadding(),
                                            )
                                        },
                                        profilePage = { padding ->
                                            ProfileScreen(
                                                onLoginClick = {
                                                    navController.navigate(AppRoute.Login) {
                                                        popUpTo(0) { inclusive = true }
                                                    }
                                                },
                                                onOpenRepository = { owner, repo ->
                                                    navController.navigate(AppRoute.Repo(owner, repo))
                                                },
                                                onOpenUser = { login ->
                                                    navController.navigate(AppRoute.User(login))
                                                },
                                                onSettingsClick = { navController.navigate(AppRoute.Settings) },
                                                onOpenGists = { login ->
                                                    navController.navigate(AppRoute.Gists(login))
                                                },
                                                bottomContentPadding = padding.calculateBottomPadding(),
                                            )
                                        },
                                    )
                                },
                                loginScreen = {
                                    LoginScreen(
                                        onSignIn = { authViewModel.onSignIn() },
                                        onBrowseAsGuest = { authViewModel.onBrowseAsGuest() },
                                        onSavePat = { authViewModel.onSavePat(it) },
                                    )
                                },
                                // L06：Topic chip 携带 query 进入搜索页（SearchViewModel 就地提交，不改 feature:search）
                                searchScreen = { initialQuery ->
                                    SearchRoute(
                                        initialQuery = initialQuery,
                                        navController = navController,
                                    )
                                },
                                blobScreen = { owner, repo, ref, path ->
                                    BlobRoute(
                                        owner = owner,
                                        repo = repo,
                                        ref = ref,
                                        path = path,
                                        // 登录态：游客只读（不渲染「编辑」），与 RepoDetailScreen 同口径
                                        isLoggedIn = authState !is AuthState.Anonymous,
                                        navController = navController,
                                    )
                                },
                                repoDetailScreen = { owner, repo, ref, showFiles, treePath, showReleases, releaseTag ->
                                    RepoDetailScreen(
                                        owner = owner,
                                        repo = repo,
                                        initialRef = ref.ifBlank { null },
                                        // TREE/RELEASE 深链初始视图（github.com/…/tree/… 与 /releases/tag/…）
                                        initialShowFiles = showFiles,
                                        initialTreePath = treePath,
                                        initialShowReleases = showReleases,
                                        initialReleaseTag = releaseTag,
                                        onBackClick = { navController.popBackStack() },
                                        // T23：文件 Tab 分支 Chip → 分支管理页（带当前分支高亮）
                                        onBranchesClick = { o, r, currentRef ->
                                            // #90 类型安全路由：ref 由 navigation 参数序列化器编码，不再手工 URLEncoder
                                            navController.navigate(AppRoute.Branches(o, r, currentRef.orEmpty()))
                                        },
                                        // L05：新建 Release 表单页（ref 为空 → 表单目标分支留空，服务端按默认分支）
                                        onCreateRelease = { o, r ->
                                            navController.navigate(AppRoute.ReleaseCreate(o, r, ref))
                                        },
                                        // L06：Topic chip → 搜索页 query=topic:xxx
                                        onTopicClick = { topic ->
                                            navController.navigate(AppRoute.Search("topic:$topic"))
                                        },
                                    )
                                },
                                // L04：新建仓库页；成功后清出本页并打开新仓库详情
                                createRepoScreen = { onCreated ->
                                    CreateRepoScreen(
                                        onBackClick = { navController.popBackStack() },
                                        onCreated = onCreated,
                                    )
                                },
                                // L05：新建 Release 表单页（成功后由 NavHost 重进仓库详情以刷新列表）
                                releaseCreateScreen = { _, _, _, onCreated ->
                                    ReleaseCreateScreen(
                                        onBackClick = { navController.popBackStack() },
                                        onCreated = onCreated,
                                    )
                                },
                                // L09：COMMIT 详情页（深链/time-line 入口；替换此前占位屏）
                                commitScreen = { _, _, _ ->
                                    CommitDetailScreen(
                                        onBackClick = { navController.popBackStack() },
                                    )
                                },
                                // L10 他人主页（USER 路由）：只读资料头 + Follow/Unfollow；
                                // 路由参数 login 由 Navigation 写入 SavedStateHandle，
                                // ProfileScreen 经 ProfileViewModel 读取，无需在此透传
                                profileScreen = { onLoginClick: () -> Unit, onBackClick: () -> Unit ->
                                    ProfileScreen(
                                        onLoginClick = onLoginClick,
                                        onOpenRepository = { owner, repo ->
                                            navController.navigate(AppRoute.Repo(owner, repo))
                                        },
                                        onOpenUser = { login ->
                                            navController.navigate(AppRoute.User(login))
                                        },
                                        onSettingsClick = { navController.navigate(AppRoute.Settings) },
                                        onBackClick = onBackClick,
                                        onOpenGists = { login -> navController.navigate(AppRoute.Gists(login)) },
                                    )
                                },
                                // L11 Gists 列表页：条目点击 → Chrome Custom Tabs（v1 不做详情渲染）
                                gistsScreen = { _, onBackClick ->
                                    GistsScreen(
                                        onBackClick = onBackClick,
                                        onOpenExternal = { url -> openExternalBrowser(context, url) },
                                    )
                                },
                                settingsScreen = {
                                    SettingsScreen(viewModel = settingsViewModel)
                                },
                                issueListScreen = { owner, repo, onIssueClick ->
                                    IssueListScreen(
                                        owner = owner,
                                        repo = repo,
                                        onBackClick = { navController.popBackStack() },
                                        onIssueClick = onIssueClick,
                                        onCreateIssue = {
                                            navController.navigate(
                                                AppRoute.IssueCreate(owner, repo),
                                            )
                                        },
                                    )
                                },
                                issueDetailScreen = { owner, repo, number ->
                                    IssueDetailScreen(
                                        owner = owner,
                                        repo = repo,
                                        number = number,
                                        onBackClick = { navController.popBackStack() },
                                        onInternalLink = { parsed -> navigateToParsedUrl(navController, parsed) },
                                    )
                                },
                                pullRequestListScreen = { owner, repo, onPullRequestClick ->
                                    PullRequestListScreen(
                                        owner = owner,
                                        repo = repo,
                                        onBackClick = { navController.popBackStack() },
                                        onPullRequestClick = onPullRequestClick,
                                        // T23：PR 列表顶栏「新建」→ 创建 PR 页
                                        onCreatePullRequest = { o, r ->
                                            navController.navigate(
                                                AppRoute.PrCreate(o, r),
                                            )
                                        },
                                    )
                                },
                                pullRequestDetailScreen = { owner, repo, number ->
                                    PullRequestDetailScreen(
                                        owner = owner,
                                        repo = repo,
                                        number = number,
                                        onBackClick = { navController.popBackStack() },
                                        onInternalLink = { parsed -> navigateToParsedUrl(navController, parsed) },
                                    )
                                },
                                // T23：分支管理页（仓库文件 Tab 的分支 Chip 入口）。
                                // 选中分支 → 带 ref 重进仓库详情并弹出旧 REPO 页（AppNavHost 内已写好该
                                // 导航回调，此处只透传；currentRef 空串 = 未指定当前分支，转 null 表示不高亮）
                                branchesScreen = { owner, repo, currentRef, onBackClick, onBranchSelected ->
                                    BranchesScreen(
                                        owner = owner,
                                        repo = repo,
                                        currentRef = currentRef,
                                        onBackClick = onBackClick,
                                        onBranchSelected = onBranchSelected,
                                    )
                                },
                                // T23：创建 PR 页（PR 列表顶栏「新建」入口）。onCreated 由 AppNavHost 接线
                                // 到「清出本页并打开新 PR 详情」
                                createPullRequestScreen = { owner, repo, onCreated ->
                                    PullRequestCreateScreen(
                                        owner = owner,
                                        repo = repo,
                                        onBackClick = { navController.popBackStack() },
                                        onCreated = onCreated,
                                    )
                                },
                                editorScreen = { initialContent, onClose ->
                                    MarkdownEditorScreen(
                                        initialContent = initialContent,
                                        onClose = onClose,
                                        onInternalLink = { parsed -> navigateToParsedUrl(navController, parsed) },
                                        onExternalLink = { url ->
                                            openExternalBrowser(context, url)
                                        },
                                    )
                                },
                                createIssueScreen = { owner, repo ->
                                    CreateIssueScreen(
                                        owner = owner,
                                        repo = repo,
                                        onBackClick = { navController.popBackStack() },
                                        onCreated = { navController.popBackStack() },
                                    )
                                },
                            )
                        }
                        // 通知面板（#88）：覆盖层挂在 NavHost 之上，铃铛置 visible；
                        // 不进导航栈（ui-design §3.4 拍板「顶栏铃铛点击 → 右侧滑入面板」）
                        NotificationsPanel(
                            visible = notificationPanelVisible,
                            onDismiss = { notificationPanelVisible = false },
                            onLoginClick = {
                                navController.navigate(AppRoute.Login) {
                                    popUpTo(0) { inclusive = true }
                                }
                            },
                            onNotificationClick = { parsed -> navigateToParsedUrl(navController, parsed) },
                        )
                    }
                }
            }

            // 语言切换（T24）：仓库 Flow 发射新 tag → 缓存（attachBaseContext 同步读取）
            // → recreate 应用 locale；冷启动首帧用系统语言，Flow 发射后补一次 recreate
            LaunchedEffect(languageTag) {
                if (languageTag != cachedLanguageTag) {
                    cachedLanguageTag = languageTag
                    recreate()
                }
            }

            // 登录态驱动导航：仅状态变化且目标页不符时 navigate，popUpTo(0) 清栈防循环
            LaunchedEffect(authState) {
                val target = authStateToDestination(authState)
                val isOnLogin = navController.currentDestination?.hasRoute<AppRoute.Login>() == true
                if (shouldNavigateForAuthState(isOnLogin, target)) {
                    navController.navigate(target) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            }

            // 游客浏览等不改变登录态的导航事件
            LaunchedEffect(Unit) {
                authViewModel.navigationEvents.collect { event ->
                    when (event) {
                        AuthNavigation.Home -> {
                            navController.navigate(AppRoute.Home) {
                                popUpTo(0) { inclusive = true }
                            }
                        }
                    }
                }
            }

            // 深链处理：NavHost 就绪后，把 intent 携带的 GitHub 链接路由到应用内页面
            LaunchedEffect(pendingDeepLink.value) {
                val uri = pendingDeepLink.value ?: return@LaunchedEffect
                val parsed = GitHubLinkParser.parseUrl(uri.toString())
                if (parsed is ParsedUrl.External) {
                    // External 深链：用 Chrome Custom Tabs 在应用内打开原始 url
                    openExternalBrowser(context, parsed.url)
                } else if (!navigateToParsedUrl(navController, parsed)) {
                    // 解析成功但无应用内路由（如无仓库语境的 IssueRef）：原始 URL 落浏览器，
                    // 不留「点了没反应」的静默死路（fromParsedUrl 的 null 契约见 AppRoute）
                    openExternalBrowser(context, uri.toString())
                }
                pendingDeepLink.value = null // 消费后清空，避免重复导航
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntentData(intent.data)
    }

    /**
     * intent 数据分流：OAuth 回调（自定义 scheme，ADR-0001）→ handleCallback；
     * 其余（GitHub 深链）→ pendingDeepLink 交给深链路由。
     */
    private fun handleIntentData(uri: Uri?) {
        if (uri == null) return
        if (uri.scheme == oauthCallbackScheme) {
            lifecycleScope.launch {
                try {
                    sessionManager.handleCallback(uri)
                    // 成功后 authState 自动变 SignedIn → 登录态导航接管（跳主页）
                } catch (e: OAuthCallbackException) {
                    // 用户取消/错误回调：留在登录页（异常消息不含 token）
                    Log.w(TAG, "OAuth 回调失败（用户取消或错误回调）: ${e.message}")
                } catch (e: TokenExchangeException) {
                    // 授权码换 token 失败：留在登录页（异常消息不含 token）
                    Log.w(TAG, "OAuth token 交换失败: ${e.message}")
                }
            }
        } else {
            pendingDeepLink.value = uri
        }
    }

    /**
     * 应用语言 locale（T24）：tag 非空 → 构造对应 locale 的 ConfigurationContext；
     * null（跟随系统）→ 原样返回。Locale.setDefault 同步更新非 UI 格式化。
     */
    private fun applyLanguageLocale(
        base: Context,
        tag: String?,
    ): Context {
        if (tag == null) return base
        val locale = Locale.forLanguageTag(tag)
        Locale.setDefault(locale)
        val configuration = Configuration(base.resources.configuration)
        configuration.setLocale(locale)
        return base.createConfigurationContext(configuration)
    }

    private companion object {
        const val TAG = "MainActivity"

        /**
         * 语言 tag 缓存：attachBaseContext 在 DataStore 异步 Flow 可用前同步读取
         * （冷启动首帧系统语言 → Flow 发射后 recreate 补应用）。
         */
        @Volatile
        var cachedLanguageTag: String? = null
    }
}

/**
 * 搜索路由承载（L06）：Topic chip 传入的 query 由宿主就地提交给 [SearchViewModel]。
 *
 * 为什么不改 feature:search：本票文件边界禁止修改该模块；[SearchViewModel.submitQuery]
 * 是既有公开入口，宿主在 LaunchedEffect 里提交一次即可得到与手输一致的搜索态。
 */
@Composable
private fun SearchRoute(
    initialQuery: String,
    navController: androidx.navigation.NavHostController,
    viewModel: SearchViewModel =
        androidx.hilt.navigation.compose
            .hiltViewModel(),
) {
    val context = LocalContext.current
    LaunchedEffect(initialQuery) {
        if (initialQuery.isNotBlank()) viewModel.submitQuery(initialQuery)
    }
    SearchScreen(
        onBackClick = { navController.popBackStack() },
        onResultClick = { parsed -> navigateToParsedUrl(navController, parsed) },
        onLoginClick = {
            navController.navigate(AppRoute.Login) {
                popUpTo(0) { inclusive = true }
            }
        },
        viewModel = viewModel,
    )
}

/**
 * BLOB 深链路由承载（T11 补接线）：把 owner/repo/ref/path 注入 [RepoFilesViewModel]
 * 并以全屏 FileViewer 呈现——此前该路由误挂占位组件致深链/树外链接显示「Coming soon」。
 *
 * T22 修复：本路由必须与 [RepoDetailScreen] 一样消费 `RepoFilesUiState.editState` ——
 * 此前只渲染查看器，点「编辑」后 ViewModel 已进入编辑态而界面不切换（深链文件编辑死路，
 * CI editor.png 探针 FAILED 的根因）。编辑态分支经 [FileEditHost] 与仓库详情共用；
 * [FileEditEventSnackbar] 消费草稿恢复/提交事件（PR #234 草稿流程在此路径同样可用）。
 */
@Composable
internal fun BlobRoute(
    owner: String,
    repo: String,
    ref: String,
    path: String,
    isLoggedIn: Boolean,
    navController: androidx.navigation.NavHostController,
    viewModel: RepoFilesViewModel =
        androidx.hilt.navigation.compose
            .hiltViewModel(),
) {
    val fileState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(path) { viewModel.openDeepLinkFile(path) }
    // 编辑事件必须在此消费：否则草稿恢复提示（含「丢弃草稿」动作）与提交结果事件永远无人接收
    FileEditEventSnackbar(viewModel = viewModel, snackbarHostState = snackbarHostState)

    val baseRepoUrl = "https://github.com/$owner/$repo"
    val actions =
        RepoDetailActions(
            onNavigateToParsedUrl = { parsed -> navigateToParsedUrl(navController, parsed) },
            onOpenExternal = { url -> openExternalBrowser(context, url) },
            onEditMarkdown = null,
        )
    Box(modifier = Modifier.fillMaxSize()) {
        FileEditHost(
            editState = fileState.editState,
            filePath = fileState.selectedPath,
            baseRepoUrl = baseRepoUrl,
            defaultRef = ref,
            viewModel = viewModel,
            onClose = { viewModel.dismissEdit() },
            modifier = Modifier.fillMaxSize(),
            actions = actions,
        ) {
            FileViewerScreen(
                fileState = fileState.fileState,
                selectedPath = fileState.selectedPath ?: path,
                ref = ref,
                viewModel = viewModel,
                actions = actions,
                baseRepoUrl = baseRepoUrl,
                findState = fileState.findState,
                isFindOpen = fileState.isFindOpen,
                // 游客只读：与 RepoDetailScreen 一致，未登录不渲染「编辑」入口
                editable = isLoggedIn,
                onClose = { navController.popBackStack() },
                modifier = Modifier.fillMaxSize(),
            )
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding(),
        )
    }
}
