package com.yumiru11.githubapp.core.githubrest.model

import kotlinx.serialization.Serializable

/**
 * POST /markdown 请求体（GitHub 服务端 GFM 渲染）。
 *
 * @param text 待渲染 Markdown 文本
 * @param mode 渲染模式："markdown"（标准）或 "gfm"（GitHub Flavored Markdown，启用表格/任务列表等）
 * @param context GFM 模式下的仓库全名（owner/repo），用于解析相对链接与 @mention
 */
@Serializable
data class MarkdownRenderRequest(
    val text: String,
    val mode: String = MODE_GFM,
    val context: String? = null,
) {
    companion object {
        const val MODE_GFM = "gfm"
    }
}
