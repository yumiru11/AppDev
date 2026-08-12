package com.yumiru11.githubapp.core.githubauth.auth

import android.app.PendingIntent
import android.content.Context
import android.net.Uri
import com.yumiru11.githubapp.core.githubauth.token.SessionData
import com.yumiru11.githubapp.core.githubauth.token.TokenStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationService
import net.openid.appauth.AuthorizationServiceConfiguration
import net.openid.appauth.ResponseTypeValues
import javax.inject.Inject
import javax.inject.Singleton

/**
 * OAuth PKCE 授权流程管理器（T4 认证核心，plan.md §4.1，ADR-0001）。
 *
 * 职责：
 * 1. [buildAuthorizationRequest]：AppAuth [AuthorizationRequest] 构造（PKCE 由 Builder 自动开启）
 * 2. [performAuthorization]：真机浏览器拉起（AuthorizationService 生命周期封装）——**需真机验证**
 * 3. [handleCallback]：回调 URI → 提取 code → [TokenEndpointClient] 换 token → 持久化 → 更新状态
 * 4. [authState]：登录状态流（SignedIn / PAT / Anonymous），UI 据此编排
 * 5. [signOut]：清空凭据 + 置 Anonymous
 *
 * ## 真机验证边界
 * - AppAuth 的 [AuthorizationService] 依赖 Android 上下文，浏览器拉起与
 *   `performTokenRequest` 路径无法在纯 JVM 单测覆盖——本类的 token 交换走可注入的
 *   [TokenEndpointClient] seam（纯 JVM + MockWebServer 可测），真机流程标注「需真机验证」。
 * - app 侧 Manifest 深链 intent-filter 接线为 Wave2 工作（本模块仅提供回调 URI 常量）。
 */
@Singleton
class OAuthSessionManager
    @Inject
    constructor(
        private val tokenStorage: TokenStorage,
        private val tokenEndpointClient: TokenEndpointClient,
        private val config: OAuthConfig,
    ) {
        private val _authState: MutableStateFlow<AuthState> =
            MutableStateFlow(deriveAuthState(tokenStorage.loadSession()))

        /** 认证状态流：初始化即从 [TokenStorage] 推导，登录/登出/降级后经 [refreshState] 更新。 */
        val authState: StateFlow<AuthState> = _authState.asStateFlow()

        /**
         * 构造 GitHub OAuth 授权请求（PKCE 由 AppAuth Builder 自动开启 code_verifier/challenge）。
         *
         * 授权端点与 scope 取自 [OAuthConfig]；`response_type=code`（授权码流程）。
         */
        fun buildAuthorizationRequest(): AuthorizationRequest {
            val serviceConfiguration =
                AuthorizationServiceConfiguration(
                    Uri.parse(config.authorizeEndpoint),
                    Uri.parse(config.tokenEndpoint),
                )
            return AuthorizationRequest
                .Builder(
                    serviceConfiguration,
                    config.clientId,
                    ResponseTypeValues.CODE,
                    Uri.parse(config.redirectUri),
                ).setScope(config.scopes)
                .build()
        }

        /**
         * 真机流程：拉起浏览器授权（**需真机验证**）。
         *
         * [AuthorizationService] 生命周期由本方法封装：构造后使用，`finally` 中
         * [AuthorizationService.dispose] 释放（AppAuth 无独立 start/end，构造即 start）。
         * 回调经自定义 scheme `com.yumiru11.githubapp://oauth-callback` 回应用
         * （ADR-0001），由宿主 Activity 转发给 [handleCallback]。
         */
        fun performAuthorization(
            context: Context,
            pendingIntent: PendingIntent,
        ) {
            val service = AuthorizationService(context)
            try {
                service.performAuthorizationRequest(buildAuthorizationRequest(), pendingIntent)
            } finally {
                service.dispose()
            }
        }

        /**
         * OAuth 回调入口（App 深链/RedirectUriReceiverActivity 转发到这里）。
         *
         * 提取 code → [TokenEndpointClient] 换 token → 组 [SessionData] 持久化 → 状态置
         * [AuthState.SignedIn]。成功返回新会话快照；回调缺 code 抛 [OAuthCallbackException]。
         */
        suspend fun handleCallback(uri: Uri): SessionData = handleCallbackUri(uri.toString())

        /** 回调编排的 String 入口（public [handleCallback] 的纯 JVM 可测核心）。 */
        internal suspend fun handleCallbackUri(callbackUri: String): SessionData {
            val code =
                extractAuthorizationCode(callbackUri)
                    ?: throw OAuthCallbackException("oauth callback URI missing authorization code")
            val result = tokenEndpointClient.exchangeCode(code)
            val session =
                SessionData(
                    accessToken = result.accessToken,
                    refreshToken = result.refreshToken,
                )
            tokenStorage.saveSession(session)
            _authState.value = AuthState.SignedIn(session)
            return session
        }

        /** 重读 [TokenStorage]，同步 [authState]（外部存储变化后调用，如 PAT 登录/降级）。 */
        suspend fun refreshState() {
            _authState.value = deriveAuthState(tokenStorage.loadSession())
        }

        /** 登出：清空全部凭据并置 [AuthState.Anonymous]。对匿名态调用是幂等 no-op。 */
        suspend fun signOut() {
            tokenStorage.clear()
            _authState.value = AuthState.Anonymous
        }
    }

/**
 * 从会话快照推导 [AuthState]（ADR-0003 优先级：isRestOnly → PAT；有 access token → SignedIn；否则 Anonymous）。
 *
 * `pat` 字段本身不参与推导：PAT 登录保证 isRestOnly=true（见 session/loginWithPat），
 * 其余字段（refreshToken 等）为 SignedIn 快照的补充信息。
 */
internal fun deriveAuthState(session: SessionData): AuthState =
    when {
        session.isRestOnly -> AuthState.PAT
        session.accessToken != null -> AuthState.SignedIn(session)
        else -> AuthState.Anonymous
    }

/** OAuth 回调解析失败（URI 无 authorization code / 错误回调）。 */
class OAuthCallbackException(
    message: String,
) : Exception(message)
