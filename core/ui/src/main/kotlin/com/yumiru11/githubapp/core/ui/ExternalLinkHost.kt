@file:Suppress("MatchingDeclarationName")
// 文件包含内聚的 LinkAction + routeLink + ExternalLinkHost 三声明（链接路由决策 + 宿主），
// detekt 的「文件名匹配单顶层声明」规则对多声明文件误报，此处精准抑制。

package com.yumiru11.githubapp.core.ui

import android.content.Context
import android.content.Intent
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
 * 用外部浏览器（Chrome Custom Tabs）打开链接，**显式排除本应用**。
 *
 * 不 setPackage 时隐式 VIEW 会被本应用自己的 github.com BROWSABLE intent-filter
 * 命中——与 WebView Shell 等一起弹「打开方式」选择器甚至自循环回应用内深链
 * （CI 实拍 C 板第 2 帧 resolver 根因）。这里解析可处理 https 的浏览器并锁定
 * 第一个非自身候选；设备上没有任何第三方浏览器时退化为系统默认解析。
 */
fun openExternalBrowser(
    context: Context,
    url: String,
) {
    val self = context.packageName
    val candidates =
        context.packageManager
            .queryIntentActivities(
                Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com")).addCategory(Intent.CATEGORY_BROWSABLE),
                0,
            ).filter { it.activityInfo.packageName != self }
    val intent = CustomTabsIntent.Builder().build()
    candidates
        .firstOrNull()
        ?.activityInfo
        ?.packageName
        ?.let { intent.intent.setPackage(it) }
    intent.launchUrl(context, Uri.parse(url))
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
                openExternalBrowser(context, action.url)
            }

            is LinkAction.DelegateInternal -> {
                onInternal(action.parsedUrl)
            }
        }
    }
}
