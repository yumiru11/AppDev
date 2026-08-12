package com.yumiru11.githubapp.core.common

/**
 * GitHub API 端点常量（REST + GraphQL 双通道共享）。
 */
object GitHubApiConfig {
    /** GitHub REST API v3 根地址 */
    const val REST_BASE_URL = "https://api.github.com/"

    /** GitHub GraphQL API 端点 */
    const val GRAPHQL_URL = "https://api.github.com/graphql"
}
