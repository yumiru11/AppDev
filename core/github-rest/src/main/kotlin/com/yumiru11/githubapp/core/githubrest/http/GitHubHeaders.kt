package com.yumiru11.githubapp.core.githubrest.http

/**
 * GitHub REST 统一请求头常量（plan.md §4.3 请求规范）。
 */
object GitHubHeaders {
    /** GitHub 官方要求的 Accept 媒体类型 */
    const val ACCEPT_VALUE = "application/vnd.github+json"

    /** API 版本固定头 */
    const val API_VERSION_HEADER = "X-GitHub-Api-Version"

    /** 当前锁定的 GitHub API 版本 */
    const val API_VERSION_VALUE = "2022-11-28"

    /** User-Agent（GitHub 强制要求，缺失即 403） */
    const val USER_AGENT_VALUE = "AppDev-GitHub-Client/0.1.0"
}
