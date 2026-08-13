package com.yumiru11.githubapp

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import com.yumiru11.githubapp.auth.authStateToDestination
import com.yumiru11.githubapp.auth.shouldNavigateForAuthState
import com.yumiru11.githubapp.core.datastore.preferences.UserPreferencesRepository
import com.yumiru11.githubapp.core.githubauth.auth.OAuthCallbackException
import com.yumiru11.githubapp.core.githubauth.auth.OAuthConfig
import com.yumiru11.githubapp.core.githubauth.auth.OAuthSessionManager
import com.yumiru11.githubapp.core.githubauth.auth.TokenExchangeException
import com.yumiru11.githubapp.core.navigation.AppRoute
import com.yumiru11.githubapp.core.navigation.link.GitHubLinkParser
import com.yumiru11.githubapp.core.navigation.link.ParsedUrl
import com.yumiru11.githubapp.core.ui.AppNavHost
import com.yumiru11.githubapp.core.ui.navigateToParsedUrl
import com.yumiru11.githubapp.feature.auth.AuthNavigation
import com.yumiru11.githubapp.feature.auth.AuthViewModel
import com.yumiru11.githubapp.feature.auth.LoginScreen
import com.yumiru11.githubapp.feature.repo.RepoDetailScreen
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
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

    @Inject lateinit var sessionManager: OAuthSessionManager

    // 主题/毛玻璃偏好仓库（T6 Wave2）：themeMode → AppThemeHost，blurEnabled → 顶/底栏玻璃
    @Inject lateinit var userPreferencesRepository: UserPreferencesRepository

    // 待消费的深链 URI（冷启动 + onNewIntent 运行时）；用 mutableStateOf 以便 Compose 观察重组
    private val pendingDeepLink = mutableStateOf<Uri?>(null)

    // OAuth 回调 scheme（取自 OAuthConfig.REDIRECT_URI 单一事实来源，不重复硬编码字面量）
    private val oauthCallbackScheme: String? = Uri.parse(OAuthConfig.REDIRECT_URI).scheme

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

            AppThemeHost(repository = userPreferencesRepository) {
                AppNavHost(
                    navController = navController,
                    startDestination = authStateToDestination(authState),
                    blurEnabled = blurEnabled,
                    loginScreen = {
                        LoginScreen(
                            onSignIn = { authViewModel.onSignIn() },
                            onBrowseAsGuest = { authViewModel.onBrowseAsGuest() },
                            onSavePat = { authViewModel.onSavePat(it) },
                        )
                    },
                    repoDetailScreen = { owner, repo ->
                        RepoDetailScreen(
                            owner = owner,
                            repo = repo,
                            onBackClick = { navController.popBackStack() },
                        )
                    },
                )
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

    private companion object {
        const val TAG = "MainActivity"
    }
}
