@file:Suppress("ReturnCount")
// 拦截器 guard-clause 风格（null/host 不在白名单/游客无 token 早返回），拆散反损可读性。

package com.yumiru11.githubapp.core.markdown.webview

import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayInputStream

/**
 * 私有仓库图片代理（plan.md §2.14 / §2.9）。
 *
 * WebView shouldInterceptRequest 白名单拦截器：仅对 GitHub 私有图床域名注入
 * Authorization header（token 不进入 HTML/JS，仅由原生拦截器加到网络请求）。
 *
 * 注意 ADR-0005 推迟图片认证头，但 ticket T8 验收含「私有仓库图片经代理加载成功」——
 * 以 ticket 验收为准实现，标注「需真机验证」。
 *
 * @param tokenProvider 返回当前 OAuth token（游客返回 null，公开图直通）
 * @param httpClient 复用 OkHttp（T5 网络层已建）
 */
class PrivateImageInterceptor(
    private val tokenProvider: () -> String?,
    private val httpClient: OkHttpClient,
) {
    /** 触发 Authorization 注入的 GitHub 图床白名单 host */
    private val privateImageHosts =
        setOf(
            "raw.githubusercontent.com",
            "avatars.githubusercontent.com",
            "private-user-images.githubusercontent.com",
            "camo.githubusercontent.com",
        )

    /**
     * 拦截请求：若 host 在白名单且有 token，加 Authorization 转发；否则返回 null（系统处理）。
     *
     * @return 拦截后的响应；null 表示不拦截（系统默认处理）
     */
    fun intercept(request: WebResourceRequest): WebResourceResponse? {
        val url = request.url ?: return null
        val host = url.host?.lowercase() ?: return null
        if (host !in privateImageHosts) return null

        val token = tokenProvider() ?: return null // 游客：公开图直通

        val requestBuilder =
            Request
                .Builder()
                .url(url.toString())
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Accept", "image/*")

        request.requestHeaders?.forEach { (key, value) ->
            if (!key.equals("Authorization", ignoreCase = true)) {
                requestBuilder.addHeader(key, value)
            }
        }

        val response =
            httpClient
                .newCall(requestBuilder.build())
                .execute()

        return response.use {
            val body = it.body.bytes()
            val mimeType = it.header("Content-Type") ?: "image/*"
            WebResourceResponse(
                mimeType,
                "utf-8",
                ByteArrayInputStream(body),
            )
        }
    }
}
