package com.yumiru11.githubapp.core.githubauth.auth

import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/**
 * 从 OAuth 回调 URI 提取 authorization code（纯 Kotlin，不依赖 android.net.Uri，可纯 JVM 测试）。
 *
 * GitHub OAuth 回调格式：`com.yumiru11.githubapp://oauth-callback?code=xxx&state=yyy`；
 * 错误回调：`?error=access_denied&error_description=...`（无 code → 返回 null）。
 *
 * 与 AppAuth 的 `AuthorizationResponse.fromIntent` 等价语义的手动实现：
 * 自定义 scheme 流程下 intent data 的 query 即携带 code，手动 parse 使提取逻辑可测。
 */
internal fun extractAuthorizationCode(callbackUri: String): String? {
    // 只取 query（? 之后、# 之前），忽略 fragment
    val query = callbackUri.substringAfter('?', "").substringBefore('#')
    if (query.isEmpty()) return null
    return query
        .split('&')
        .firstNotNullOfOrNull { pair ->
            val eq = pair.indexOf('=')
            if (eq <= 0) return@firstNotNullOfOrNull null
            val key = pair.substring(0, eq)
            val value = pair.substring(eq + 1)
            if (key == PARAM_CODE && value.isNotBlank()) {
                // 用 (String, String) 重载而非 (String, Charset)：后者是 API 33+ 才有的重载，
                // 在 minSdk 26 的设备上会抛 NoSuchMethodError（lint NewApi 在库模块 lint 恢复后
                // 首次报出，2026-09-12）。UTF-8 是 URLDecoder 强制支持的编码，不会抛异常。
                URLDecoder.decode(value, StandardCharsets.UTF_8.name())
            } else {
                null
            }
        }
}

private const val PARAM_CODE = "code"
