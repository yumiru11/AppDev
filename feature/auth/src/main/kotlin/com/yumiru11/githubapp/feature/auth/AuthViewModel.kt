package com.yumiru11.githubapp.feature.auth

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yumiru11.githubapp.core.githubauth.auth.AuthState
import com.yumiru11.githubapp.core.githubauth.auth.OAuthConfig
import com.yumiru11.githubapp.core.githubauth.auth.OAuthSessionManager
import com.yumiru11.githubapp.core.githubauth.token.SessionData
import com.yumiru11.githubapp.core.githubauth.token.TokenStorage
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 登录页 ViewModel（T4 Wave2 接线：把静态 LoginScreen 接到认证核心）。
 *
 * - [authState]：透传 [OAuthSessionManager.authState]（登录态单一事实来源，UI 据此编排）
 * - [onSignIn]：AppAuth 标准流程拉起（内部 buildAuthorizationRequest → 浏览器授权）；
 *   回调 PendingIntent 指向 MainActivity（manifest filter，ADR-0001）——**浏览器拉起与回调需真机验证**
 * - [onBrowseAsGuest]：游客浏览，不改登录态，发导航事件由宿主跳主页
 * - [onSavePat]：开发者 PAT 模式，落盘 TokenStorage（isRestOnly=true，ADR-0003）后刷新状态为 PAT
 */
@HiltViewModel
class AuthViewModel
    @Inject
    constructor(
        private val sessionManager: OAuthSessionManager,
        private val tokenStorage: TokenStorage,
        private val config: OAuthConfig,
        @ApplicationContext private val context: Context,
    ) : ViewModel() {
        /** 登录态流：直接透传 [OAuthSessionManager.authState]（单一事实来源）。 */
        val authState: StateFlow<AuthState> = sessionManager.authState

        /** 导航事件通道（游客浏览等不改变 authState 的导航）。 */
        private val _navigationEvents = Channel<AuthNavigation>(Channel.BUFFERED)

        /** 宿主收集导航事件并执行跳转。 */
        val navigationEvents: Flow<AuthNavigation> = _navigationEvents.receiveAsFlow()

        /**
         * GitHub 登录：拉起 AppAuth 授权流程。
         *
         * [OAuthSessionManager.performAuthorization] 内部先 [OAuthSessionManager.buildAuthorizationRequest]
         * 再以 AuthorizationService 拉起浏览器；回调经自定义 scheme 回 MainActivity
         * （onNewIntent/intent → handleCallback，见 MainActivity）。
         */
        fun onSignIn() {
            sessionManager.performAuthorization(context, buildCallbackPendingIntent())
        }

        /** 游客浏览：不改变登录态，仅发导航事件（由宿主导航到主页）。 */
        fun onBrowseAsGuest() {
            _navigationEvents.trySend(AuthNavigation.Home)
        }

        /** 开发者模式：保存 PAT（REST-only，ADR-0003）并刷新登录态为 PAT；空白输入忽略。 */
        fun onSavePat(pat: String) {
            if (pat.isBlank()) return
            tokenStorage.saveSession(SessionData(pat = pat, isRestOnly = true))
            viewModelScope.launch { sessionManager.refreshState() }
        }

        /**
         * 回调 PendingIntent：隐式 VIEW intent（redirect URI + 本应用包名）。
         *
         * manifest 中 MainActivity 的 oauth-callback filter（scheme + host 约束）比 AppAuth
         * 库 RedirectUriReceiverActivity（仅 scheme）更具体，回调直达 MainActivity。
         */
        private fun buildCallbackPendingIntent(): PendingIntent {
            val callbackIntent =
                Intent(Intent.ACTION_VIEW, Uri.parse(config.redirectUri))
                    .setPackage(context.packageName)
            return PendingIntent.getActivity(
                context,
                OAUTH_CALLBACK_REQUEST_CODE,
                callbackIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        private companion object {
            /** 回调 PendingIntent 请求码（同码复用同一实例，FLAG_UPDATE_CURRENT 更新内容）。 */
            const val OAUTH_CALLBACK_REQUEST_CODE = 1001
        }
    }
