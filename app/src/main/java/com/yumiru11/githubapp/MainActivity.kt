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
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import com.yumiru11.githubapp.auth.authStateToDestination
import com.yumiru11.githubapp.auth.shouldNavigateForAuthState
import com.yumiru11.githubapp.core.datastore.preferences.UserPreferencesRepository
import com.yumiru11.githubapp.core.designsystem.component.LocalHazeState
import com.yumiru11.githubapp.core.designsystem.token.AppBlur
import com.yumiru11.githubapp.core.githubauth.auth.OAuthCallbackException
import com.yumiru11.githubapp.core.githubauth.auth.OAuthConfig
import com.yumiru11.githubapp.core.githubauth.auth.OAuthSessionManager
import com.yumiru11.githubapp.core.githubauth.auth.TokenExchangeException
import com.yumiru11.githubapp.core.navigation.AppRoute
import com.yumiru11.githubapp.core.navigation.link.GitHubLinkParser
import com.yumiru11.githubapp.core.navigation.link.ParsedUrl
import com.yumiru11.githubapp.core.ui.AppNavHost
import com.yumiru11.githubapp.core.ui.MainTabPager
import com.yumiru11.githubapp.core.ui.PlaceholderScreen
import com.yumiru11.githubapp.core.ui.RepoDetailActions
import com.yumiru11.githubapp.core.ui.navigateToParsedUrl
import com.yumiru11.githubapp.feature.auth.AuthNavigation
import com.yumiru11.githubapp.feature.auth.AuthViewModel
import com.yumiru11.githubapp.feature.auth.LoginScreen
import com.yumiru11.githubapp.feature.editor.MarkdownEditorScreen
import com.yumiru11.githubapp.feature.home.HomeScreen
import com.yumiru11.githubapp.feature.issue.CreateIssueScreen
import com.yumiru11.githubapp.feature.issue.IssueDetailScreen
import com.yumiru11.githubapp.feature.issue.IssueListScreen
import com.yumiru11.githubapp.feature.notifications.ui.NotificationsPanel
import com.yumiru11.githubapp.feature.profile.ProfileScreen
import com.yumiru11.githubapp.feature.pullrequest.PullRequestDetailScreen
import com.yumiru11.githubapp.feature.pullrequest.PullRequestListScreen
import com.yumiru11.githubapp.feature.repo.FileViewerScreen
import com.yumiru11.githubapp.feature.repo.RepoDetailScreen
import com.yumiru11.githubapp.feature.repo.RepoFilesViewModel
import com.yumiru11.githubapp.feature.search.SearchScreen
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
 *   blurEnabled 经 AppNavHost 下传顶/底栏 GlassSurface（ADR-0004 玻璃只做两处）
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

    // 主题/毛玻璃偏好仓库（T6 Wave2）：themeMode → AppThemeHost，blurEnabled → 顶/底栏玻璃
    @Inject lateinit var userPreferencesRepository: UserPreferencesRepository

    // 待消费的深链 URI（冷启动 + onNewIntent 运行时）；用 mutableStateOf 以便 Compose 观察重组
    private val pendingDeepLink = mutableStateOf<Uri?>(null)

    // OAuth 回调 scheme（取自 OAuthConfig.REDIRECT_URI 单一事实来源，不重复硬编码字面量）
    private val oauthCallbackScheme: String? = Uri.parse(OAuthConfig.REDIRECT_URI).scheme

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(applyLanguageLocale(newBase, cachedLanguageTag))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntentData(intent?.data)

        setContent {
            val navController = rememberNavController()
            val context = LocalContext.current
            val authState by authViewModel.authState.collectAsStateWithLifecycle()
            // 毛玻璃开关（ADR-0004：默认开启，设置页 T24 提供关闭项）→ 顶栏/底栏 GlassSurface
            val blurEnabled by userPreferencesRepository.blurEnabled.collectAsStateWithLifecycle(initialValue = true)
            // 语言偏好（T24 设置页语言切换）：变化 → 缓存 + recreate 应用 locale
            val languageTag by userPreferencesRepository.languageTag.collectAsStateWithLifecycle(initialValue = null)
            // 底部三分区当前页（分区重构 2026-08-14：tab 切换 = pager 横滑，非导航）
            var mainTab by rememberSaveable { mutableStateOf(AppRoute.HOME) }

            AppThemeHost(repository = userPreferencesRepository) {
                // 根级 HazeState（#88）：通知面板玻璃 backdrop-blur 整个 NavHost 内容。
                // MainTabPager/HomeScreen 自建内层 state 只服务其顶/底栏，面板读不到内层值
                val rootHazeState = rememberHazeState()
                var notificationPanelVisible by rememberSaveable { mutableStateOf(false) }
                CompositionLocalProvider(LocalHazeState provides rootHazeState) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .then(
                                        if (blurEnabled && AppBlur.isBlurSupported()) {
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
                                        blurEnabled = blurEnabled,
                                        homePage = { padding ->
                                            HomeScreen(
                                                onSearchClick = { navController.navigate(AppRoute.SEARCH) },
                                                onNotificationClick = { notificationPanelVisible = true },
                                                onProfileClick = { mainTab = AppRoute.PROFILE },
                                                blurEnabled = blurEnabled,
                                                onLoginClick = {
                                                    navController.navigate(AppRoute.LOGIN) {
                                                        popUpTo(0) { inclusive = true }
                                                    }
                                                },
                                                onFeedItemClick = { parsed -> navigateToParsedUrl(navController, parsed) },
                                                modifier = Modifier.padding(padding),
                                            )
                                        },
                                        reposPage = { padding ->
                                            PlaceholderScreen(
                                                modifier =
                                                    Modifier
                                                        .padding(padding)
                                                        .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()),
                                            )
                                        },
                                        profilePage = { padding ->
                                            ProfileScreen(
                                                onLoginClick = {
                                                    navController.navigate(AppRoute.LOGIN) {
                                                        popUpTo(0) { inclusive = true }
                                                    }
                                                },
                                                onOpenRepository = { owner, repo ->
                                                    navController.navigate(
                                                        AppRoute.REPO
                                                            .replace("{owner}", owner)
                                                            .replace("{repo}", repo),
                                                    )
                                                },
                                                onOpenUser = { login ->
                                                    navController.navigate(
                                                        AppRoute.USER.replace("{login}", login),
                                                    )
                                                },
                                                onSettingsClick = { navController.navigate(AppRoute.SETTINGS) },
                                                modifier = Modifier.padding(padding),
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
                                searchScreen = {
                                    SearchScreen(
                                        onResultClick = { parsed -> navigateToParsedUrl(navController, parsed) },
                                        onLoginClick = {
                                            navController.navigate(AppRoute.LOGIN) {
                                                popUpTo(0) { inclusive = true }
                                            }
                                        },
                                    )
                                },
                                blobScreen = { owner, repo, ref, path ->
                                    BlobRoute(
                                        owner = owner,
                                        repo = repo,
                                        ref = ref,
                                        path = path,
                                        navController = navController,
                                    )
                                },
                                repoDetailScreen = { owner, repo ->
                                    RepoDetailScreen(
                                        owner = owner,
                                        repo = repo,
                                        onBackClick = { navController.popBackStack() },
                                    )
                                },
                                profileScreen = { onLoginClick: () -> Unit, onSettingsClick: () -> Unit ->
                                    ProfileScreen(
                                        onLoginClick = onLoginClick,
                                        onOpenRepository = { owner, repo ->
                                            navController.navigate(
                                                AppRoute.REPO
                                                    .replace("{owner}", owner)
                                                    .replace("{repo}", repo),
                                            )
                                        },
                                        onOpenUser = { login ->
                                            navController.navigate(
                                                AppRoute.USER.replace("{login}", login),
                                            )
                                        },
                                        onSettingsClick = { navController.navigate(AppRoute.SETTINGS) },
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
                                                AppRoute.ISSUE_CREATE
                                                    .replace("{owner}", owner)
                                                    .replace("{repo}", repo),
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
                                editorScreen = { initialContent, onClose ->
                                    MarkdownEditorScreen(
                                        initialContent = initialContent,
                                        onClose = onClose,
                                        onInternalLink = { parsed -> navigateToParsedUrl(navController, parsed) },
                                        onExternalLink = { url ->
                                            CustomTabsIntent.Builder().build().launchUrl(context, Uri.parse(url))
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
                                navController.navigate(AppRoute.LOGIN) {
                                    popUpTo(0) { inclusive = true }
                                }
                            },
                            onNotificationClick = { parsed -> navigateToParsedUrl(navController, parsed) },
                            blurEnabled = blurEnabled,
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
                if (shouldNavigateForAuthState(navController.currentDestination?.route, target)) {
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
                            navController.navigate(AppRoute.HOME) {
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
                    CustomTabsIntent.Builder().build().launchUrl(context, Uri.parse(parsed.url))
                } else {
                    navigateToParsedUrl(navController, parsed)
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
 * BLOB 深链路由承载（T11 补接线）：把 owner/repo/ref/path 注入 [RepoFilesViewModel]
 * 并以全屏 FileViewer 呈现——此前该路由误挂占位组件致深链/树外链接显示「Coming soon」。
 */
@Composable
private fun BlobRoute(
    owner: String,
    repo: String,
    ref: String,
    path: String,
    navController: androidx.navigation.NavHostController,
    viewModel: RepoFilesViewModel =
        androidx.hilt.navigation.compose
            .hiltViewModel(),
) {
    val fileState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(path) { viewModel.openDeepLinkFile(path) }

    FileViewerScreen(
        fileState = fileState.fileState,
        selectedPath = fileState.selectedPath ?: path,
        ref = ref,
        viewModel = viewModel,
        actions =
            RepoDetailActions(
                onNavigateToParsedUrl = { parsed -> navigateToParsedUrl(navController, parsed) },
                onOpenExternal = { url ->
                    CustomTabsIntent.Builder().build().launchUrl(context, Uri.parse(url))
                },
                onEditMarkdown = null,
            ),
        baseRepoUrl = "https://github.com/$owner/$repo",
        editable = true,
        onClose = { navController.popBackStack() },
        modifier = Modifier.fillMaxSize(),
    )
}
