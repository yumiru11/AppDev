@file:Suppress("CyclomaticComplexMethod")
// fromParsedUrl 对 ParsedUrl 各子类型做 when 穷尽映射，分支天然多；精准抑制（与 GitHubLinkParser 同款）。

package com.yumiru11.githubapp.core.navigation

import com.yumiru11.githubapp.core.navigation.link.ParsedUrl
import java.net.URLEncoder

/**
 * 应用内导航路由表。
 *
 * 每个常量是 Navigation Compose 的 route 字符串，含 `{placeholder}` 参数。
 * 纯 Kotlin，不依赖任何 Android / Compose 类型。
 */
object AppRoute {
    const val HOME = "home"
    const val LOGIN = "login"
    const val REPO = "repo/{owner}/{repo}"
    const val ISSUE = "issue/{owner}/{repo}/{number}"
    const val ISSUES = "issues/{owner}/{repo}"
    const val PULLS = "pulls/{owner}/{repo}"
    const val ISSUE_CREATE = "issue_create/{owner}/{repo}"
    const val PR = "pr/{owner}/{repo}/{number}"
    const val COMMIT = "commit/{owner}/{repo}/{sha}"
    const val DISCUSSION = "discussion/{owner}/{repo}/{number}"

    // path 走 query 参数：文件路径天然多段（a/b/c.kt），单段 {path} 占位符无法匹配
    // 多段深链，会抛 "Navigation destination cannot be found" 崩溃（CI 截图段 5.11 首次暴露）。
    // 构造时必须 URLEncoder.encode（见 fromParsedUrl）；目标参数 NavType.StringType 自动解码。
    const val BLOB = "blob/{owner}/{repo}/{ref}?path={path}"
    const val USER = "user/{login}"
    const val SEARCH = "search"

    // NOTIFICATION 路由随 #88 移除：通知改为铃铛触发的覆盖面板（ui-design §3.4），不再进导航栈
    const val PROFILE = "profile"
    const val SETTINGS = "settings"
    const val EXTERNAL = "external"
    const val EDITOR = "editor"

    /**
     * 将 [ParsedUrl] 映射为具体 route 字符串。
     *
     * 仅映射有明确路由归属的类型；[ParsedUrl.External] 及无 owner/repo 语境的
     * 类型（如 [ParsedUrl.IssueRef]、[ParsedUrl.Release]、[ParsedUrl.Tree]、
     * [ParsedUrl.Search]）返回 null，由调用方决定兜底行为。
     */
    fun fromParsedUrl(parsed: ParsedUrl): String? =
        when (parsed) {
            is ParsedUrl.Repo -> {
                "repo/${parsed.owner}/${parsed.repo}"
            }

            is ParsedUrl.Issue -> {
                "issue/${parsed.owner}/${parsed.repo}/${parsed.number}"
            }

            is ParsedUrl.PullRequest -> {
                "pr/${parsed.owner}/${parsed.repo}/${parsed.number}"
            }

            is ParsedUrl.Commit -> {
                if (parsed.owner == null || parsed.repo == null) {
                    null
                } else {
                    "commit/${parsed.owner}/${parsed.repo}/${parsed.sha}"
                }
            }

            is ParsedUrl.Discussion -> {
                "discussion/${parsed.owner}/${parsed.repo}/${parsed.number}"
            }

            is ParsedUrl.Blob -> {
                // path 多段必须整体编码成单段 query 值（'/'→%2F，'+'→%20 防空格歧义）
                "blob/${parsed.owner}/${parsed.repo}/${parsed.ref}?path=" +
                    URLEncoder.encode(parsed.path, "UTF-8").replace("+", "%20")
            }

            is ParsedUrl.User -> {
                "user/${parsed.login}"
            }

            is ParsedUrl.External -> {
                null
            }

            is ParsedUrl.IssueRef -> {
                null
            }

            is ParsedUrl.Release -> {
                null
            }

            is ParsedUrl.Tree -> {
                null
            }

            is ParsedUrl.Search -> {
                null
            }
        }
}
