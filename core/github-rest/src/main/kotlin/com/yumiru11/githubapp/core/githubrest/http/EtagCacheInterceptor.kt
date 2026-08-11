package com.yumiru11.githubapp.core.githubrest.http

import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody

/**
 * ETag 条件请求缓存拦截器（spec seam ②：304 Not Modified，plan.md §4.3/§4.6）。
 *
 * 行为：
 * 1. GET 请求若命中缓存，携带 `If-None-Match: {etag}` 发出
 * 2. 服务端返回 304 → 用缓存体回放为 200，调用方无感知
 * 3. 服务端返回 200 且带 ETag → peekBody 复制一份入缓存（原响应流不受影响）
 *
 * 仅缓存文本型 JSON 响应（≤ [MAX_CACHED_BODY_BYTES]），超限不入缓存但正常透传。
 */
class EtagCacheInterceptor(
    private val store: EtagStore,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.method != METHOD_GET) return chain.proceed(request)

        val cacheKey = request.url.toString()
        val cached = store.get(cacheKey)
        val conditional =
            if (cached != null) {
                request.newBuilder().header(HEADER_IF_NONE_MATCH, cached.etag).build()
            } else {
                request
            }

        val response = chain.proceed(conditional)
        return when {
            response.code == CODE_NOT_MODIFIED && cached != null -> response.replayFromCache(request, cached)
            response.code == CODE_OK -> response.cacheIfEtagged(cacheKey)
            else -> response
        }
    }

    /** 304 → 回放缓存体为 200 */
    private fun Response.replayFromCache(
        original: okhttp3.Request,
        entry: EtagEntry,
    ): Response {
        close()
        return Response
            .Builder()
            .request(original)
            .protocol(Protocol.HTTP_1_1)
            .code(CODE_OK)
            .message("OK")
            .header(HEADER_ETAG, entry.etag)
            .apply { entry.contentType?.let { header(HEADER_CONTENT_TYPE, it) } }
            .body(entry.body.toResponseBody(entry.contentType?.toMediaTypeOrNull()))
            .build()
    }

    /** 200 + ETag → 复制响应体入缓存（peekBody 不消费原流） */
    private fun Response.cacheIfEtagged(cacheKey: String): Response {
        val etag = header(HEADER_ETAG) ?: return this
        val body = peekBody(MAX_CACHED_BODY_BYTES).string()
        store.put(cacheKey, EtagEntry(etag = etag, contentType = header(HEADER_CONTENT_TYPE), body = body))
        return this
    }

    private companion object {
        const val METHOD_GET = "GET"
        const val CODE_OK = 200
        const val CODE_NOT_MODIFIED = 304
        const val HEADER_IF_NONE_MATCH = "If-None-Match"
        const val HEADER_ETAG = "ETag"
        const val HEADER_CONTENT_TYPE = "Content-Type"

        /** 单条缓存上限 2 MiB（仓库元数据 JSON 远小于此） */
        const val MAX_CACHED_BODY_BYTES = 2L * 1024 * 1024
    }
}
