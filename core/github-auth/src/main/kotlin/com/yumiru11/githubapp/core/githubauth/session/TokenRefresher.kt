// 网络+解析+Result 包装的标准协程结构：NestedBlockDepth 是 token 端点调用链固有深度；
// catch (e: Exception) 前已有 CancellationException 重抛（协程取消语义正确），泛化兜底是 Result 模式的收口。
@file:Suppress("NestedBlockDepth", "TooGenericExceptionCaught")

package com.yumiru11.githubapp.core.githubauth.session

import com.yumiru11.githubapp.core.githubauth.token.TokenStorage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Token 刷新器（认证保持层核心，plan.md §4.1）。
 *
 * 用 refresh token 调 GitHub token 端点换新 access/refresh token（GitHub 每次刷新轮换
 * refresh token，必须持久化新值），成功后更新 [TokenStorage]。
 *
 * ## 并发防护（关键设计）
 *
 * 多个 401 并发触发 [refreshIfNeeded] 时只发起一次网络刷新，其余协程共享同一结果：
 * - [inflight] 为「单飞行」哨兵（@Volatile，免锁读）；刷新进行中时新调用直接等待同一
 *   [CompletableDeferred]，不触碰网络
 * - [refreshLock] 只保护「创建 deferred」临界区；锁内复查避免两个协程同时成为刷新发起者
 *
 * 若 401 在上一轮刷新完成后才到达（新事件），自然触发新一轮刷新——语义正确。
 */
class TokenRefresher(
    private val tokenStorage: TokenStorage,
    private val config: TokenRefreshConfig,
    private val client: OkHttpClient,
) {
    /** Token 端点 URL（AuthSessionInterceptor 据此跳过自身请求，防御性） */
    val tokenEndpoint: HttpUrl get() = config.tokenEndpoint

    private val refreshLock = Mutex()

    @Volatile
    private var inflight: CompletableDeferred<Result<Boolean>>? = null

    /**
     * 需要刷新则刷新并返回结果：
     * - `Success(true)`：刷新成功，[TokenStorage] 已写入新 token
     * - `Success(false)`：无 refresh token（游客/PAT 模式），无需刷新
     * - `Failure`：刷新失败（refresh token 过期/网络错误），上层据此发登出信号
     */
    suspend fun refreshIfNeeded(): Result<Boolean> {
        // 快速路径：已有刷新在途 → 共享同一结果（不重复触发网络请求）
        inflight?.let { return it.await() }

        return refreshLock.withLock {
            // 锁内复查：可能恰在快速路径检查后由其他协程启动
            val active = inflight
            if (active != null) {
                active.await()
            } else {
                val deferred = CompletableDeferred<Result<Boolean>>()
                inflight = deferred
                try {
                    val result = performRefresh()
                    deferred.complete(result)
                    result
                } catch (e: CancellationException) {
                    // 发起者被取消：仍要让等待共享结果的协程拿到失败信号，再向上传播取消
                    deferred.complete(Result.failure(TokenRefreshException("refresh cancelled")))
                    throw e
                } catch (e: Exception) {
                    val failure = Result.failure<Boolean>(e)
                    deferred.complete(failure)
                    failure
                } finally {
                    inflight = null
                }
            }
        }
    }

    private suspend fun performRefresh(): Result<Boolean> {
        val session = tokenStorage.loadSession()
        val refreshToken = session.refreshToken ?: return Result.success(false)

        return try {
            val response =
                withContext(Dispatchers.IO) {
                    client.newCall(buildRefreshRequest(refreshToken)).execute()
                }
            response.use { resp ->
                if (!resp.isSuccessful) {
                    Result.failure(TokenRefreshException("token endpoint returned HTTP ${resp.code}"))
                } else {
                    val parsed = TOKEN_JSON.decodeFromString<TokenEndpointResponse>(resp.body.string())
                    val newAccess = parsed.accessToken
                    if (newAccess.isNullOrBlank()) {
                        Result.failure(TokenRefreshException("token endpoint returned no access_token"))
                    } else {
                        // GitHub 轮换 refresh token：新值缺失时保留旧值
                        val newRefresh = parsed.refreshToken ?: refreshToken
                        tokenStorage.saveSession(session.copy(accessToken = newAccess, refreshToken = newRefresh))
                        Result.success(true)
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun buildRefreshRequest(refreshToken: String): Request =
        Request
            .Builder()
            .url(config.tokenEndpoint)
            // GitHub 默认返回表单编码；要求 JSON 便于解析
            .header(HEADER_ACCEPT, MEDIA_TYPE_JSON)
            .post(
                FormBody
                    .Builder()
                    .add(PARAM_GRANT_TYPE, GRANT_TYPE_REFRESH_TOKEN)
                    .add(PARAM_REFRESH_TOKEN, refreshToken)
                    .add(PARAM_CLIENT_ID, config.clientId)
                    .build(),
            ).build()

    private companion object {
        const val HEADER_ACCEPT = "Accept"
        const val MEDIA_TYPE_JSON = "application/json"
        const val PARAM_GRANT_TYPE = "grant_type"
        const val GRANT_TYPE_REFRESH_TOKEN = "refresh_token"
        const val PARAM_REFRESH_TOKEN = "refresh_token"
        const val PARAM_CLIENT_ID = "client_id"

        @OptIn(ExperimentalSerializationApi::class)
        val TOKEN_JSON: Json =
            Json {
                ignoreUnknownKeys = true
                isLenient = true
                namingStrategy = JsonNamingStrategy.SnakeCase
            }
    }
}
