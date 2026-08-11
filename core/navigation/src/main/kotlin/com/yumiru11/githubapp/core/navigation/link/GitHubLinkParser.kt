@file:Suppress("ReturnCount", "CyclomaticComplexMethod")
// URL 解析器天然多形态多分支（绝对/相对/引用/@mention/sha/各子路由），
// detekt 的返回数/圈复杂度上限对纯解析器过于苛刻，拆散反损可读性；此处精准抑制。

package com.yumiru11.githubapp.core.navigation.link

/**
 * 解析 GitHub 链接的纯函数结果。
 *
 * 所有类型均为纯 Kotlin data class，不依赖任何 Android / Compose 类型，
 * 以便在纯 JVM 环境下单测。
 */
sealed interface ParsedUrl {
    data class Repo(
        val owner: String,
        val repo: String,
    ) : ParsedUrl

    data class Issue(
        val owner: String,
        val repo: String,
        val number: Int,
    ) : ParsedUrl

    data class PullRequest(
        val owner: String,
        val repo: String,
        val number: Int,
    ) : ParsedUrl

    data class Commit(
        val owner: String?,
        val repo: String?,
        val sha: String,
    ) : ParsedUrl

    data class Discussion(
        val owner: String,
        val repo: String,
        val number: Int,
    ) : ParsedUrl

    data class Blob(
        val owner: String,
        val repo: String,
        val ref: String,
        val path: String,
    ) : ParsedUrl

    data class Tree(
        val owner: String,
        val repo: String,
        val ref: String,
        val path: String,
    ) : ParsedUrl

    data class Release(
        val owner: String,
        val repo: String,
        val tag: String?,
    ) : ParsedUrl

    data class User(
        val login: String,
    ) : ParsedUrl

    /** 无 owner/repo 语境的 issue 引用，如 `#123`。 */
    data class IssueRef(
        val owner: String?,
        val repo: String?,
        val number: Int,
    ) : ParsedUrl

    data class Search(
        val query: String,
    ) : ParsedUrl

    /** 无法识别或非 GitHub 的链接。 */
    data class External(
        val url: String,
    ) : ParsedUrl
}

/**
 * 将任意输入字符串解析为 [ParsedUrl]。
 *
 * 支持：绝对链接、相对链接、协议相对链接、@提及、裸 sha、issue 引用。
 * 无法识别或非 GitHub 域名一律返回 [ParsedUrl.External]。
 */
object GitHubLinkParser {
    private const val GITHUB_HOST = "github.com"
    private const val SHA_LENGTH = 40

    fun parseUrl(input: String): ParsedUrl {
        val raw = input.trim()
        if (raw.isEmpty()) return ParsedUrl.External(raw)

        // @提及
        if (raw.startsWith("@")) {
            val mention = raw.substring(1)
            // @org/team 无对应路由
            if (mention.contains('/')) return ParsedUrl.External(raw)
            return ParsedUrl.User(mention)
        }

        // 裸 sha（40 位 hex，独立 token）——无 owner/repo 语境，owner/repo 置空
        if (isBareSha(raw)) return ParsedUrl.Commit(null, null, raw)

        // 协议相对：//github.com/owner/repo
        var path = raw
        if (path.startsWith("//")) {
            path = path.substring(2)
            // 去掉 host 段，仅保留路径
            val hostSlash = path.indexOf('/')
            if (hostSlash >= 0) {
                val host = path.substring(0, hostSlash)
                if (host.equals(GITHUB_HOST, ignoreCase = true)) {
                    path = path.substring(hostSlash + 1)
                } else {
                    return ParsedUrl.External(raw)
                }
            } else {
                return ParsedUrl.External(raw)
            }
        }

        // 绝对链接：https://github.com/...
        if (path.startsWith("http://") || path.startsWith("https://")) {
            return parseAbsolute(path, raw)
        }

        // 相对链接
        return parseRelative(path, raw)
    }

    private fun parseAbsolute(
        url: String,
        original: String,
    ): ParsedUrl {
        val schemeEnd = url.indexOf("://")
        if (schemeEnd < 0) return ParsedUrl.External(original)
        val rest = url.substring(schemeEnd + 3)

        val slash = rest.indexOf('/')
        val host = if (slash < 0) rest else rest.substring(0, slash)
        if (!host.equals(GITHUB_HOST, ignoreCase = true)) return ParsedUrl.External(original)

        val path = if (slash < 0) "" else rest.substring(slash + 1)
        return parsePath(path, original)
    }

    private fun parseRelative(
        path: String,
        original: String,
    ): ParsedUrl {
        // #123 issue 引用
        if (path.startsWith("#")) {
            val number = path.substring(1).toIntOrNull()
            if (number != null) return ParsedUrl.IssueRef(null, null, number)
            return ParsedUrl.External(original)
        }

        // owner/repo#123
        val hashIndex = path.indexOf('#')
        if (hashIndex >= 0) {
            val base = path.substring(0, hashIndex)
            val number = path.substring(hashIndex + 1).toIntOrNull()
            if (number != null) {
                val (owner, repo) = splitOwnerRepo(base)
                if (owner != null && repo != null) return ParsedUrl.Issue(owner, repo, number)
            }
            return ParsedUrl.External(original)
        }

        // ../blob/main/file —— 去掉前导 ../ 后按路径解析
        var normalized = path
        while (normalized.startsWith("../")) {
            normalized = normalized.substring(3)
        }

        // 相对链接的单段（如 "justaword"）无 owner/repo 语境，无法归属
        if (!normalized.contains('/')) return ParsedUrl.External(original)

        return parsePath(normalized, original)
    }

    /**
     * 解析 github.com 之后的路径段。
     * 形态：owner/repo[/subpath]
     */
    private fun parsePath(
        path: String,
        original: String,
    ): ParsedUrl {
        // 去掉 query string（?tab=... 等）
        val pathOnly = path.substringBefore('?')
        val segments = pathOnly.split('/').filter { it.isNotEmpty() }
        if (segments.isEmpty()) return ParsedUrl.External(original)

        // 单段视为用户页
        if (segments.size == 1) {
            return ParsedUrl.User(segments[0])
        }

        val owner = segments[0]
        val repo = segments[1]

        // 仅 owner/repo
        if (segments.size == 2) return ParsedUrl.Repo(owner, repo)

        val sub = segments[2]
        val rest = segments.drop(3)

        return when (sub) {
            "issues" -> {
                val number = rest.firstOrNull()?.toIntOrNull()
                if (number != null) ParsedUrl.Issue(owner, repo, number) else ParsedUrl.External(original)
            }

            "pull" -> {
                val number = rest.firstOrNull()?.toIntOrNull()
                if (number != null) ParsedUrl.PullRequest(owner, repo, number) else ParsedUrl.External(original)
            }

            "discussions" -> {
                val number = rest.firstOrNull()?.toIntOrNull()
                if (number != null) ParsedUrl.Discussion(owner, repo, number) else ParsedUrl.External(original)
            }

            "commit" -> {
                val sha = rest.firstOrNull()
                if (sha != null && sha.length == SHA_LENGTH) {
                    ParsedUrl.Commit(owner, repo, sha)
                } else {
                    ParsedUrl.External(original)
                }
            }

            "blob" -> {
                if (rest.size >= 2) {
                    ParsedUrl.Blob(owner, repo, rest[0], rest.drop(1).joinToString("/"))
                } else {
                    ParsedUrl.External(original)
                }
            }

            "tree" -> {
                if (rest.isNotEmpty()) {
                    ParsedUrl.Tree(owner, repo, rest[0], rest.drop(1).joinToString("/"))
                } else {
                    ParsedUrl.External(original)
                }
            }

            "releases" -> {
                if (rest.isEmpty()) {
                    ParsedUrl.Release(owner, repo, null)
                } else if (rest.size == 2 && rest[0] == "tag") {
                    ParsedUrl.Release(owner, repo, rest[1])
                } else {
                    ParsedUrl.External(original)
                }
            }

            else -> {
                ParsedUrl.External(original)
            }
        }
    }

    private fun splitOwnerRepo(base: String): Pair<String?, String?> {
        val segments = base.split('/').filter { it.isNotEmpty() }
        if (segments.size != 2) return null to null
        return segments[0] to segments[1]
    }

    private fun isBareSha(value: String): Boolean {
        if (value.length != SHA_LENGTH) return false
        return value.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }
    }
}
