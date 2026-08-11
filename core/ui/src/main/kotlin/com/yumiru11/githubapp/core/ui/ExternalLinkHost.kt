@file:Suppress("MatchingDeclarationName")
// 文件包含内聚的 LinkAction + routeLink + ExternalLinkHost 三声明（链接路由决策 + 宿主），
// detekt 的「文件名匹配单顶层声明」规则对多声明文件误报，此处精准抑制。

package com.yumiru11.githubapp.core.ui

import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import com.yumiru11.githubapp.core.navigation.link.ParsedUrl

/**
 * 链接路由动作：区分外部链接（CustomTabs）与内部链接（回调委托）。
 */
sealed interface LinkAction {
    /** 外部链接 → 打开 Chrome Custom Tabs */
    data class OpenExternal(
        val url: String,
    ) : LinkAction

    /** 内部链接 → 交给回调处理（导航/渲染器等） */
    data class DelegateInternal(
        val parsedUrl: ParsedUrl,
    ) : LinkAction
}

/**
 * 将 [ParsedUrl] 路由为 [LinkAction]。纯函数，可单独单测。
 */
fun routeLink(parsedUrl: ParsedUrl): LinkAction =
    when (parsedUrl) {
        is ParsedUrl.External -> LinkAction.OpenExternal(parsedUrl.url)
        else -> LinkAction.DelegateInternal(parsedUrl)
    }

/**
 * 外部链接宿主 Composable。
 *
 * 拦截 [ParsedUrl.External] → Chrome Custom Tabs；
 * 非 External → [onInternal] 回调（供渲染器点击 / WebView bridge 复用）。
 */
@Composable
fun ExternalLinkHost(
    parsedUrl: ParsedUrl,
    onInternal: (ParsedUrl) -> Unit,
) {
    val context = LocalContext.current
    LaunchedEffect(parsedUrl) {
        when (val action = routeLink(parsedUrl)) {
            is LinkAction.OpenExternal -> {
                val uri = Uri.parse(action.url)
                CustomTabsIntent.Builder().build().launchUrl(context, uri)
            }

            is LinkAction.DelegateInternal -> {
                onInternal(action.parsedUrl)
            }
        }
    }
}
