package com.yumiru11.githubapp.core.githubrest.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * GitHub README 元数据 DTO（GET /repos/{owner}/{repo}/readme，Accept: application/json）。
 *
 * 默认 Accept 为 JSON 时返回元数据 + base64 编码内容；切换 Accept 为
 * `application/vnd.github.html+json` 时由 [com.yumiru11.githubapp.core.githubrest.api.ReadmeApi.getReadmeHtml]
 * 直接拿到服务端已渲染 HTML 字符串。
 *
 * `_links` 字段含多种 URL 形态，仅取 [htmlUrl]（github.com 页面 URL）与 [downloadUrl]
 * （raw 用户下载链接，可用作图床相对路径基准）。
 *
 * `private` 为 Kotlin 保留词，用 @SerialName 映射 isPrivate 风格由命名策略处理；
 * 此处无字段冲突，保持 snake_case 自动映射。
 */
@Serializable
data class ReadmeDto(
    val name: String,
    val path: String,
    val sha: String,
    val size: Long = 0L,
    val url: String? = null,
    val htmlUrl: String? = null,
    @SerialName("download_url") val downloadUrl: String? = null,
    val type: String? = null,
    val content: String? = null,
    val encoding: String? = null,
) {
    /** 解码 README 内容（GitHub 默认 base64 + 行尾 \n 分段，需先去 \n 再 base64 解码） */
    fun decodeContent(): String? {
        if (content == null || encoding == null) return null
        if (encoding != "base64") return content
        val cleaned = content.replace("\n", "")
        return runCatching {
            java.util.Base64
                .getDecoder()
                .decode(cleaned)
                .decodeToString()
        }.getOrNull()
    }
}
