package com.yumiru11.githubapp

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.rememberNavController
import com.yumiru11.githubapp.core.designsystem.theme.AppTheme
import com.yumiru11.githubapp.core.navigation.link.GitHubLinkParser
import com.yumiru11.githubapp.core.navigation.link.ParsedUrl
import com.yumiru11.githubapp.core.ui.AppNavHost
import com.yumiru11.githubapp.core.ui.navigateToParsedUrl

/**
 * 单 Activity 入口：edge-to-edge 沉浸式 + 导航骨架托管 + GitHub 深链路由。
 *
 * - edge-to-edge 由 [enableEdgeToEdge] 开启，内容延伸全屏，insets 由各 Composable 处理
 * - NavHost 持有共享 NavHostController（remember），供 UI 导航与深链复用
 * - 深链（GitHub 链接）：[GitHubLinkParser] 解析 → [navigateToParsedUrl] 应用内导航；
 *   [ParsedUrl.External] / 无法识别 → 不导航（由 Custom Tabs / 系统兜底）
 */
class MainActivity : ComponentActivity() {
    // 待消费的深链 URI（冷启动 + onNewIntent 运行时）；用 mutableStateOf 以便 Compose 观察重组
    private val pendingDeepLink = mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        pendingDeepLink.value = intent?.data

        setContent {
            val navController = rememberNavController()
            val context = LocalContext.current

            AppTheme {
                AppNavHost(navController = navController)
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
        // 更新深链 state，触发 recomposition → LaunchedEffect 路由导航
        pendingDeepLink.value = intent.data
    }
}
