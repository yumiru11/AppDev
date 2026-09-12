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

    // L06：Topic chip 点击 → 搜索页带 query（空串 = 普通入口，用户自行输入）。
    // 由 data object 升级为带默认参数的 data class：Navigation 侧 pattern 变为
    // `search?query={query}`，既有 `AppRoute.Search` 调用点改为 `AppRoute.Search()`。
    @Serializable
    @SerialName("search")
    data class Search(
        val query: String = "",
    ) : AppRoute

    @Serializable
    @SerialName("settings")
    data object Settings : AppRoute

    @Serializable
    @SerialName("editor")
    data object Editor : AppRoute

    // L04 新建仓库页（Home 第三条长条按钮 / 未登录则先引导登录）
    @Serializable
    @SerialName("create_repo")
    data object CreateRepo : AppRoute

    // L05 新建 Release 表单页（仓库详情 Releases Tab「新建 Release」入口）；
    // ref 为可选 query：宿主传仓库默认分支，表单「目标分支」预填（空 → 服务端按默认分支处理）
    @Serializable
    @SerialName("release_create")
    data class ReleaseCreate(
        val owner: String,
        val repo: String,
        val ref: String = "",
    ) : AppRoute

    // ref 为可选 query（T23 分支切换深链：切换后带新 ref 重进仓库详情，文件树按该分支加载）
    //
    // showFiles/treePath/showReleases/releaseTag 是**深链初始视图**提示（全部可选 query，
    // 默认值 = 「无提示」）——补齐需求审计 §10 P2「Tree / Release 落到浏览器」：
    //   · ParsedUrl.Tree    → showFiles=true（+ treePath：文件树自动展开到该目录）
    //   · ParsedUrl.Release → showReleases=true（+ releaseTag：展开该 tag 的 Release 详情）
    // 为什么不做成独立 destination：Tree/Release 落地的就是**同一个仓库详情屏**，只是初始
    // 分区不同；新开 destination 会把 RepoDetailScreen 及其 RepoDetailActions 装配复制三份。
    // 参数一律非空（Boolean + 空串），默认值即「无提示」→ 与既有 ref 同一种编码形态。
    @Serializable
    @SerialName("repo")
    data class Repo(
        val owner: String,
        val repo: String,
        val ref: String = "",
        /** true = 直接落在「文件」分区（Tree 深链；无路径时也要看到文件浏览而非 README） */
        val showFiles: Boolean = false,
        /** 文件分区自动展开到的仓库内路径（空 = 只到根树，不展开） */
        val treePath: String = "",
        /** true = 直接落在「Releases」分区（Release 深链；无 tag 时看到列表） */
        val showReleases: Boolean = false,
        /** Releases 分区自动展开的 Release tag（空 = 只到列表，不展开） */
        val releaseTag: String = "",
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

    // L11 Gists 列表页（本人/他人主页共用；username 为该主页用户 login）
    @Serializable
    @SerialName("gists")
    data class Gists(
        val username: String,
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
         * 将 [ParsedUrl] 映射为类型安全 route 对象（spec-audit §10 P2「链接落到浏览器」修复）。
         *
         * 映射表：
         * - [ParsedUrl.Tree]    → [Repo]（showFiles=true + treePath，ref 沿用 URL 分支）
         * - [ParsedUrl.Release] → [Repo]（showReleases=true + releaseTag，tag 可空 = 只看列表）
         * - [ParsedUrl.Search]  → [Search]（query 原样透传）
         * - [ParsedUrl.IssueRef] 带 owner/repo 语境 → [Issue]；无语境 → null（见下）
         * - 其余有明确归属的类型 → 同名 route（[ParsedUrl.Repo]/[ParsedUrl.Issue]/
         *   [ParsedUrl.IssueList]/[ParsedUrl.PullRequest]/[ParsedUrl.Commit]/[ParsedUrl.Discussion]/
         *   [ParsedUrl.Blob]/[ParsedUrl.User]）
         *
         * 返回 null = **显式**「无应用内路由」，调用方负责兜底（外部浏览器 / 忽略），有三类：
         * - [ParsedUrl.External]：非 GitHub 或无法识别，本身就是外链；
         * - [ParsedUrl.IssueRef] 无 owner/repo（`#123`）：本函数是纯函数、无屏幕语境，单靠
         *   引用无法定位仓库。README 内的纯锚点由 WebView bridge 先行忽略（`#` 前缀），
         *   深链兜底见 MainActivity（原始 URL 落浏览器）；
         * - [ParsedUrl.Commit] 无 owner/repo（裸 sha）：同理无法定位仓库。
         *
         * [ParsedUrl.Discussion] 保持映射（应用无 Discussions 屏）：目的地不再挂占位屏，
         * 而是在 AppNavHost 内显式转外部浏览器 —— 占位屏是静默死路，浏览器里的
         * Discussions 页面才是完整可用内容。
         */
        fun fromParsedUrl(parsed: ParsedUrl): AppRoute? =
            when (parsed) {
                is ParsedUrl.Repo -> {
                    Repo(parsed.owner, parsed.repo)
                }

                is ParsedUrl.Issue -> {
                    Issue(parsed.owner, parsed.repo, parsed.number)
                }

                is ParsedUrl.IssueList -> {
                    Issues(parsed.owner, parsed.repo)
                }

                is ParsedUrl.PullRequest -> {
                    Pr(parsed.owner, parsed.repo, parsed.number)
                }

                is ParsedUrl.Commit -> {
                    if (parsed.owner == null || parsed.repo == null) {
                        null
                    } else {
                        Commit(parsed.owner, parsed.repo, parsed.sha)
                    }
                }

                is ParsedUrl.Discussion -> {
                    Discussion(parsed.owner, parsed.repo, parsed.number)
                }

                is ParsedUrl.Blob -> {
                    Blob(parsed.owner, parsed.repo, parsed.ref, parsed.path)
                }

                is ParsedUrl.Tree -> {
                    // /tree/{ref}/{path} 即仓库详情的「文件」分区视图：复用同一 destination，
                    // 由初始视图提示参数落位（AppRoute.Repo 的 showFiles/treePath 注释）
                    Repo(
                        owner = parsed.owner,
                        repo = parsed.repo,
                        ref = parsed.ref,
                        showFiles = true,
                        treePath = parsed.path,
                    )
                }

                is ParsedUrl.Release -> {
                    // /releases 或 /releases/tag/{tag} → 仓库详情的 Releases 分区；
                    // tag 为空 = 只落列表（不展开详情）
                    Repo(
                        owner = parsed.owner,
                        repo = parsed.repo,
                        showReleases = true,
                        releaseTag = parsed.tag.orEmpty(),
                    )
                }

                is ParsedUrl.Search -> {
                    Search(parsed.query)
                }

                is ParsedUrl.IssueRef -> {
                    // 带 owner/repo 语境的引用可直接定位；无语境（`#123`）显式返回 null
                    if (parsed.owner != null && parsed.repo != null) {
                        Issue(parsed.owner, parsed.repo, parsed.number)
                    } else {
                        null
                    }
                }

                is ParsedUrl.User -> {
                    User(parsed.login)
                }

                is ParsedUrl.External -> {
                    null
                }
            }

        /**
         * 无参路由在 Navigation 中的注册 pattern（基路径 = @SerialName 的 serialName）。
         *
         * 仅供 NavHost 的 startDestination 使用（本应用仅 Home/Login 两种无参起始路由）。
         * 带参路由的完整 pattern（`{owner}` 等占位符）由 Navigation 依序列化描述符生成，
         * 导航一律传 route 对象本身，无需手工拼 pattern。
         */
        inline fun <reified T : AppRoute> startDestinationPattern(): String = serializer<T>().descriptor.serialName
    }
}
