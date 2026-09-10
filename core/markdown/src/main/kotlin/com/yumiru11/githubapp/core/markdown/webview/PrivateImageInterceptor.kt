@file:Suppress("ReturnCount")
// 拦截器 guard-clause 风格（null / 非 GET / host 不在白名单 / 游客无 token 早返回），
// 拆散反损可读性。

package com.yumiru11.githubapp.core.markdown.webview

import android.util.Log
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayInputStream
import java.io.IOException

/**
 * 私有仓库图片代理（plan.md §2.14 / §2.9）。
 *
 * WebView shouldInterceptRequest 白名单拦截器：仅对 GitHub 图床域名注入
 * Authorization header（token 不进入 HTML/JS，仅由原生拦截器加到网络请求）。
 *
 * 注意 ADR-0005 推迟图片认证头，但 ticket T8 验收含「私有仓库图片经代理加载成功」——
 * 以 ticket 验收为准实现。
 *
 * ## 白名单复核（#169 / L16，2026-09-10）
 *
 * 复核结论：原 4 条 host 遗漏了 GitHub 现役图片宿主——
 * - `user-images.githubusercontent.com`：2023 年前的 issue/README 附件域名，老仓库仍在用
 * - `objects.githubusercontent.com`：release asset / 部分附件 302 后的最终宿主
 * - `media.githubusercontent.com`：Git LFS 媒体（大图/视频）宿主
 * - `github.com/user-attachments/`：**2024 起 README 图片的新默认宿主**，私有仓库必须带 header
 *
 * 取舍：`github.com` **不整站放行**——整站在白名单会让 token 跟随任意 github.com 资源请求
 * （含 HTML 导航类请求）外发；只放行 [ATTACHMENTS_PATH_PREFIX] 这一条静态附件路径。
 * 同时收紧为非 GET 一律不代理（WebView 的图片加载恒为 GET，代理写请求没有意义）。
 *
 * @param tokenProvider 返回当前 OAuth token（游客返回 null，公开图直通）
 * @param httpClient 复用 OkHttp（T5 网络层已建）
 */
class PrivateImageInterceptor(
    private val tokenProvider: () -> String?,
    private val httpClient: OkHttpClient,
) {
    /**
     * 拦截请求：若 host 在白名单且有 token，加 Authorization 转发；否则返回 null（系统处理）。
     *
     * @return 拦截后的响应；null 表示不拦截（系统默认处理）
     */
    fun intercept(request: WebResourceRequest): WebResourceResponse? {
        if (!request.method.equals(GET_METHOD, ignoreCase = true)) return null

        val url = request.url ?: return null
        val host = url.host?.lowercase() ?: return null
        if (!isAllowlisted(host, url.path.orEmpty())) return null

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

        return try {
            val response = httpClient.newCall(requestBuilder.build()).execute()
            response.use {
                WebResourceResponse(
                    it.header("Content-Type") ?: "image/*",
                    "utf-8",
                    ByteArrayInputStream(it.body.bytes()),
                )
            }
        } catch (e: IOException) {
            // 图片代理失败不能拖垮整页渲染：返回 null 交回 WebView 自行处理（会显裂图占位）
            Log.w(LOG_TAG, "private image proxy failed: ${e.javaClass.simpleName}")
            null
        }
    }

    companion object {
        private const val LOG_TAG = "PrivateImage"

        /** WebView 图片加载恒为 GET；非 GET 不代理 */
        const val GET_METHOD = "GET"

        /** GitHub 用户附件的路径前缀（2024 起 README 图片默认宿主） */
        const val ATTACHMENTS_PATH_PREFIX = "/user-attachments/"

        /** 用户附件宿主：只放行 [ATTACHMENTS_PATH_PREFIX] 子路径，不整站放行 */
        const val ATTACHMENTS_HOST = "github.com"

        /**
         * 整站放行的 GitHub 图床 host（这些域名只服务静态图片/媒体资源）。
         * 全部小写，比较前对 host 做 lowercase 归一。
         */
        val IMAGE_HOSTS: Set<String> =
            setOf(
                "raw.githubusercontent.com",
                "avatars.githubusercontent.com",
                "user-images.githubusercontent.com",
                "private-user-images.githubusercontent.com",
                "camo.githubusercontent.com",
                "media.githubusercontent.com",
                "objects.githubusercontent.com",
            )

        /** 纯函数白名单判定（可单测）：host 归一化后按整站集合或附件路径前缀匹配。 */
        fun isAllowlisted(
            host: String,
            path: String,
        ): Boolean {
            val normalized = host.lowercase()
            if (normalized in IMAGE_HOSTS) return true
            return normalized == ATTACHMENTS_HOST && path.startsWith(ATTACHMENTS_PATH_PREFIX)
        }
    }
}
