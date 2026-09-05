@file:Suppress("CyclomaticComplexMethod")
// fromParsedUrl 对 ParsedUrl 各子类型做 when 穷尽映射，分支天然多；精准抑制（与 GitHubLinkParser 同款）。

package com.yumiru11.githubapp.core.navigation

import com.yumiru11.githubapp.core.navigation.link.ParsedUrl
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer

/**
 * 应用内导航路由表（#90 类型化：Navigation Compose 2.8 的 @Serializable 类型安全路由）。
 *
 * - 无参页 = `data object`；带参页 = `data class`（导航时传对象本身，拒绝字符串拼参）
 * - `@SerialName` 固定路由基路径：Navigation 以 serialName 为 route pattern 的基路径，
 *   其余占位符由序列化描述符派生（如 `Repo` → `repo/{owner}/{repo}?ref={ref}`，
 *   带默认值的参数自动成为 optional query）——显式声明后 pattern 与旧字符串路由完全一致
 * - 纯 Kotlin，不依赖任何 Android / Compose 类型；serialization 为唯一增引（见 build.gradle.kts）
 */
sealed interface AppRoute {
    @Serializable
    @SerialName("home")
    data object Home : AppRoute

    @Serializable
    @SerialName("login")
    data object Login : AppRoute

    @Serializable
    @SerialName("search")
    data object Search : AppRoute

    @Serializable
    @SerialName("settings")
    data object Settings : AppRoute

    @Serializable
    @SerialName("editor")
    data object Editor : AppRoute

    // ref 为可选 query（T23 分支切换深链：切换后带新 ref 重进仓库详情，文件树按该分支加载）
    @Serializable
    @SerialName("repo")
    data class Repo(
        val owner: String,
        val repo: String,
        val ref: String = "",
    ) : AppRoute

    @Serializable
    @SerialName("issues")
    data class Issues(
        val owner: String,
        val repo: String,
    ) : AppRoute

    @Serializable
    @SerialName("issue")
    data class Issue(
        val owner: String,
        val repo: String,
        val number: Int,
    ) : AppRoute

    @Serializable
    @SerialName("pulls")
    data class Pulls(
        val owner: String,
        val repo: String,
    ) : AppRoute

    @Serializable
    @SerialName("pr_create")
    data class PrCreate(
        val owner: String,
        val repo: String,
    ) : AppRoute

    // ref 可选 query：从仓库文件 Tab 分支 Chip 进入时携带当前分支，分支页高亮
    @Serializable
    @SerialName("branches")
    data class Branches(
        val owner: String,
        val repo: String,
        val ref: String = "",
    ) : AppRoute

    @Serializable
    @SerialName("issue_create")
    data class IssueCreate(
        val owner: String,
        val repo: String,
    ) : AppRoute

    @Serializable
    @SerialName("pr")
    data class Pr(
        val owner: String,
        val repo: String,
        val number: Int,
    ) : AppRoute

    @Serializable
    @SerialName("commit")
    data class Commit(
        val owner: String,
        val repo: String,
        val sha: String,
    ) : AppRoute

    @Serializable
    @SerialName("discussion")
    data class Discussion(
        val owner: String,
        val repo: String,
        val number: Int,
    ) : AppRoute

    @Serializable
    @SerialName("user")
    data class User(
        val login: String,
    ) : AppRoute

    // path 走 query 参数（默认值 → optional query）：文件路径天然多段（a/b/c.kt），
    // 单段 {path} 占位符无法匹配多段深链（CI 截图段 5.11 首次暴露）。
    // 类型安全路由下 path 由 navigation 参数序列化器编码，不再需要手工 URLEncoder。
    @Serializable
    @SerialName("blob")
    data class Blob(
        val owner: String,
        val repo: String,
        val ref: String,
        val path: String = "",
    ) : AppRoute

    companion object {
        /**
         * 将 [ParsedUrl] 映射为类型安全 route 对象。
         *
         * 仅映射有明确路由归属的类型；[ParsedUrl.External] 及无 owner/repo 语境的
         * 类型（如 [ParsedUrl.IssueRef]、[ParsedUrl.Release]、[ParsedUrl.Tree]、
         * [ParsedUrl.Search]）返回 null，由调用方决定兜底行为。
         */
        fun fromParsedUrl(parsed: ParsedUrl): AppRoute? =
            when (parsed) {
                is ParsedUrl.Repo -> Repo(parsed.owner, parsed.repo)

                is ParsedUrl.Issue -> Issue(parsed.owner, parsed.repo, parsed.number)

                is ParsedUrl.IssueList -> Issues(parsed.owner, parsed.repo)

                is ParsedUrl.PullRequest -> Pr(parsed.owner, parsed.repo, parsed.number)

                is ParsedUrl.Commit ->
                    if (parsed.owner == null || parsed.repo == null) {
                        null
                    } else {
                        Commit(parsed.owner, parsed.repo, parsed.sha)
                    }

                is ParsedUrl.Discussion -> Discussion(parsed.owner, parsed.repo, parsed.number)

                is ParsedUrl.Blob -> Blob(parsed.owner, parsed.repo, parsed.ref, parsed.path)

                is ParsedUrl.User -> User(parsed.login)

                is ParsedUrl.External,
                is ParsedUrl.IssueRef,
                is ParsedUrl.Release,
                is ParsedUrl.Tree,
                is ParsedUrl.Search,
                -> null
            }

        /**
         * 无参路由在 Navigation 中的注册 pattern（基路径 = @SerialName 的 serialName）。
         *
         * 仅供 NavHost 的 startDestination 使用（本应用仅 Home/Login 两种无参起始路由）。
         * 带参路由的完整 pattern（`{owner}` 等占位符）由 Navigation 依序列化描述符生成，
         * 导航一律传 route 对象本身，无需手工拼 pattern。
         */
        inline fun <reified T : AppRoute> startDestinationPattern(): String =
            serializer<T>().descriptor.serialName
    }
}