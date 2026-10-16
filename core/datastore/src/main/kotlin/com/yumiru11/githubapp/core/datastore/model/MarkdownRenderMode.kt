package com.yumiru11.githubapp.core.datastore.model

/**
 * Markdown 渲染通道策略（设置页「Markdown 渲染」叶子页）。
 *
 * 语义 = 「遇到原生处理不了的内容时，允不允许回退」：
 * - [NATIVE_PREFERRED]：默认。逐块原生渲染；数学公式等特例按引擎选择回退到 WebView，
 *   单个块回退而非整篇回退，长文里只有特例区域付出 WebView 代价。
 * - [NATIVE_ONLY]：绝不使用 WebView。原生处理不了的块渲染为占位卡片（保留源码可复制），
 *   适合「不想让任何网页内容进进程」的极端偏好。
 * - [WEBVIEW_PREFERRED]：整篇交给 WebView（传统通道），短文本仍可原生。
 * - [WEBVIEW_ONLY]：完全使用网页渲染通道（排障/对照用）。
 *
 * 持久化为 name 字符串，未知值回退 [NATIVE_PREFERRED]（升级不崩溃）。
 */
enum class MarkdownRenderMode {
    NATIVE_PREFERRED,
    NATIVE_ONLY,
    WEBVIEW_PREFERRED,
    WEBVIEW_ONLY,
    ;

    /** 是否允许在特殊内容上回退到 WebView（[NATIVE_ONLY] 之外的三种都允许）。 */
    val allowsWebViewFallback: Boolean get() = this != NATIVE_ONLY

    companion object {
        fun fromName(name: String?): MarkdownRenderMode = entries.firstOrNull { it.name == name } ?: NATIVE_PREFERRED
    }
}
