package com.yumiru11.githubapp.core.githubauth.token

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 生产 [TokenStorage]：EncryptedSharedPreferences 加密落盘（security-crypto 1.1.0-alpha06）。
 *
 * - 主密钥 [MasterKey]（AES256_GCM，AndroidKeyStore 硬件背书）
 * - PrefKey AES256_SIV / PrefValue AES256_GCM
 * - 明文 token 永不落 SharedPreferences 明文文件，仅经 [TokenStorage] 进出（plan.md §4 token 安全红线）
 *
 * 与 [InMemoryTokenStorage] 语义一致：未写入字段返回空会话；saveSession 的 null 字段清除对应 key。
 */
@Singleton
class EncryptedTokenStorage
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) : TokenStorage {
        private val prefs: SharedPreferences by lazy {
            val masterKey =
                MasterKey
                    .Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()
            EncryptedSharedPreferences.create(
                context,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        }

        override fun loadSession(): SessionData =
            SessionData(
                accessToken = prefs.getString(KEY_ACCESS_TOKEN, null),
                refreshToken = prefs.getString(KEY_REFRESH_TOKEN, null),
                pat = prefs.getString(KEY_PAT, null),
                isRestOnly = prefs.getBoolean(KEY_IS_REST_ONLY, false),
            )

        override fun saveSession(session: SessionData) {
            prefs
                .edit()
                .apply {
                    if (session.accessToken != null) {
                        putString(KEY_ACCESS_TOKEN, session.accessToken)
                    } else {
                        remove(KEY_ACCESS_TOKEN)
                    }
                    if (session.refreshToken != null) {
                        putString(KEY_REFRESH_TOKEN, session.refreshToken)
                    } else {
                        remove(KEY_REFRESH_TOKEN)
                    }
                    if (session.pat != null) {
                        putString(KEY_PAT, session.pat)
                    } else {
                        remove(KEY_PAT)
                    }
                    putBoolean(KEY_IS_REST_ONLY, session.isRestOnly)
                }.apply()
        }

        override fun clear() {
            prefs.edit().clear().apply()
        }

        private companion object {
            const val PREFS_NAME = "auth_session"
            const val KEY_ACCESS_TOKEN = "access_token"
            const val KEY_REFRESH_TOKEN = "refresh_token"
            const val KEY_PAT = "pat"
            const val KEY_IS_REST_ONLY = "is_rest_only"
        }
    }
