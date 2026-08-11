package com.yumiru11.githubapp.core.githubrest.http

import okhttp3.Interceptor
import okhttp3.Response

/**
 * 统一请求头拦截器：Accept / X-GitHub-Api-Version / User-Agent（plan.md §4.3）。
 *
 * 用 header()（覆盖语义）保证同一请求只出现一份规范头。
 */
class GitHubHeaderInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request =
            chain
                .request()
                .newBuilder()
                .header(HEADER_ACCEPT, GitHubHeaders.ACCEPT_VALUE)
                .header(GitHubHeaders.API_VERSION_HEADER, GitHubHeaders.API_VERSION_VALUE)
                .header(HEADER_USER_AGENT, GitHubHeaders.USER_AGENT_VALUE)
                .build()
        return chain.proceed(request)
    }

    private companion object {
        const val HEADER_ACCEPT = "Accept"
        const val HEADER_USER_AGENT = "User-Agent"
    }
}
