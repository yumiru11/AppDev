package com.yumiru11.githubapp.core.githubdata.error

import com.apollographql.apollo.exception.ApolloHttpException
import com.apollographql.apollo.exception.ApolloNetworkException
import retrofit2.HttpException
import java.io.IOException

/**
 * GitHub 请求错误归一化模型（issue #6：401/403/404/409/422/429/5xx/网络）。
 *
 * REST（Retrofit 3 suspend 直抛 [HttpException]）与 GraphQL（Apollo 异常收敛到
 * response.exception）两个通道的错误统一映射为该 sealed 类型，供 UI 层分类展示。
 */
sealed interface GitHubError {
    /** 401：未认证/token 失效 */
    data object Unauthorized : GitHubError

    /** 403：无权限（含二级限流，按 Retry-After 归入 [RateLimited]） */
    data object Forbidden : GitHubError

    /** 404：资源不存在 */
    data object NotFound : GitHubError

    /** 409：冲突（空仓库创建 readme 等） */
    data object Conflict : GitHubError

    /** 422：参数校验失败 */
    data object Validation : GitHubError

    /** 429：限流，[retryAfterSeconds] 来自 Retry-After 头（可能缺失） */
    data class RateLimited(
        val retryAfterSeconds: Long?,
    ) : GitHubError

    /** 5xx：服务端错误，保留原始状态码 */
    data class Server(
        val code: Int,
    ) : GitHubError

    /** 网络层失败（连接超时/DNS/重置等） */
    data class Network(
        val cause: Throwable? = null,
    ) : GitHubError

    /** GraphQL 协议级错误（errors 数组非空） */
    data class GraphQl(
        val messages: List<String>,
    ) : GitHubError

    /** 其余未分类错误 */
    data class Unknown(
        val cause: Throwable? = null,
    ) : GitHubError

    /** 是否允许自动重试（[RetryPolicy] 语义：限流/服务端/网络瞬断） */
    val isRetryable: Boolean
        get() = this is RateLimited || this is Server || this is Network
}

/**
 * 归一化后的请求异常（携带 [GitHubError] 与原始异常链）。
 */
class GitHubRequestException(
    val error: GitHubError,
    cause: Throwable? = null,
) : Exception(cause)

private const val HTTP_SERVER_MIN = 500
private const val HTTP_SERVER_MAX = 599
private const val RETRY_AFTER_HEADER = "Retry-After"

/**
 * 将任意异常归一化为 [GitHubError]。
 *
 * 覆盖 Retrofit [HttpException]（按状态码 + Retry-After 头）、Apollo HTTP/网络异常、
 * [IOException]；其余归入 [GitHubError.Unknown]。
 */
fun Throwable.asGitHubError(): GitHubError =
    when (this) {
        is HttpException -> {
            httpStatusError(code(), response()?.headers()?.get(RETRY_AFTER_HEADER))
        }

        is ApolloHttpException -> {
            httpStatusError(statusCode, headers.firstOrNull { it.name.equals(RETRY_AFTER_HEADER, ignoreCase = true) }?.value)
        }

        is ApolloNetworkException -> {
            GitHubError.Network(this)
        }

        is IOException -> {
            GitHubError.Network(this)
        }

        else -> {
            GitHubError.Unknown(this)
        }
    }

private fun Throwable.httpStatusError(
    code: Int,
    retryAfter: String?,
): GitHubError =
    when (code) {
        401 -> GitHubError.Unauthorized
        403 -> GitHubError.Forbidden
        404 -> GitHubError.NotFound
        409 -> GitHubError.Conflict
        422 -> GitHubError.Validation
        429 -> GitHubError.RateLimited(retryAfterSeconds = retryAfter?.toLongOrNull())
        in HTTP_SERVER_MIN..HTTP_SERVER_MAX -> GitHubError.Server(code)
        else -> GitHubError.Unknown(this)
    }
