package com.yumiru11.githubapp.core.markdown.webview

import android.webkit.JavascriptInterface
import com.yumiru11.githubapp.core.navigation.link.GitHubLinkParser
import com.yumiru11.githubapp.core.navigation.link.ParsedUrl

/**
 * WebView JS bridge 白名单回调接口（plan.md §2.9）。
 *
 * 由上层（feature:repo / 其他宿主）实现，接收来自 WebView 内 JS 的 6 个白名单事件。
 * 严禁在此接口暴露 token 或任意对象——仅基本类型 + URL 字符串。
 */
interface MarkdownBridgeCallback {
    /** 外部链接（非 GitHub 内部路由）→ 上层走 Custom Tabs 打开 */
    fun onExternalLink(url: String)

    /** GitHub 内部链接 → 上层走应用内导航（GitHubLinkParser 解析结果） */
    fun onInternalLink(parsed: ParsedUrl)

    /** 代码块复制按钮触发 */
    fun onCodeCopy(code: String)

    /** 图片点击（src 为图片 URL，私有仓库图片经拦截器已加 Authorization） */
    fun onImageClick(src: String)

    /** 任务列表勾选框状态变更 */
    fun onCheckboxClick(
        index: Int,
        checked: Boolean,
    )

    /** 内容高度变化（用于宿主动态调整 WebView 高度，避免内部滚动） */
    fun onHeightChanged(heightPx: Int)

    /**
     * Mermaid 渲染结果（renderer.js 在 `renderMermaid` 完成后上报；引擎被门禁拦下时同样上报）。
     *
     * 三个参数都是 JS 侧的**原始计数/判定**，不做范围校验（负数是 JS 缺陷，原样透传更好诊断）：
     * - [rendered]：产出**有效** SVG 的图数（成功水合；mermaid 的错误卡片 SVG 不算）
     * - [failed]：被接管但未产出有效 SVG、已恢复为代码块的图数（语法错误 / 不支持的类型）
     * - [engineSupported]：引擎门禁是否放行——false 表示 Kotlin 未注入脚本（Chromium < 94）、
     *   语法探针失败或 `window.mermaid` 缺失；此时 rendered/failed 恒为 0
     *
     * 为什么需要它：JVM/Node 只能证明选择/配置/回退语义，真机 WebView 是否**真的产出了 SVG**
     * 只有这个回调可观测（CI 的 mermaid-render-verify job 从 logcat 断言该行）。
     */
    fun onMermaidResult(
        rendered: Int,
        failed: Int,
        engineSupported: Boolean,
    )
}

/**
 * WebView JS bridge（注入为 `AndroidBridge` 全局对象，JS 调用 `AndroidBridge.onLinkClick(url)`）。
 *
 * 白名单方法（[JavascriptInterface] 注解的 public 方法）：仅以下 6 个回调可被 JS 调用，
 * 其余 Kotlin 成员不暴露给 JS（[JavascriptInterface] 是显式白名单）。
 *
 * @param callback 宿主回调（UI 线程分派由宿主自行处理）
 * @param linkParser 链接解析器（默认 [GitHubLinkParser]；可注入便于单测）
 */
class MarkdownBridge(
    private val callback: MarkdownBridgeCallback,
    private val linkParser: GitHubLinkParser = GitHubLinkParser,
) {
    /**
     * 链接点击回调（JS 端 a 标签 click 拦截后调用）。
     *
     * 解析后分派：GitHub 内部链接 → [MarkdownBridgeCallback.onInternalLink]；
     * 外部链接 → [MarkdownBridgeCallback.onExternalLink]。
     * 空 URL 静默跳过（防御）。
     */
    @JavascriptInterface
    fun onLinkClick(url: String?) {
        if (url.isNullOrBlank()) return
        when (val parsed = linkParser.parseUrl(url)) {
            is ParsedUrl.External -> callback.onExternalLink(parsed.url)
            else -> callback.onInternalLink(parsed)
        }
    }

    /** 代码块复制按钮回调 */
    @JavascriptInterface
    fun onCodeCopy(code: String?) {
        if (code == null) return
        callback.onCodeCopy(code)
    }

    /** 图片点击回调 */
    @JavascriptInterface
    fun onImageClick(src: String?) {
        if (src.isNullOrBlank()) return
        callback.onImageClick(src)
    }

    /** 任务列表勾选框回调 */
    @JavascriptInterface
    fun onCheckboxClick(
        index: Int,
        checked: Boolean,
    ) {
        callback.onCheckboxClick(index, checked)
    }

    /** 内容高度变化回调（JS 端 ResizeObserver 上报） */
    @JavascriptInterface
    fun onHeightChanged(heightPx: Int) {
        callback.onHeightChanged(heightPx)
    }

    /**
     * Mermaid 渲染结果回调（JS 端 `renderMermaid` 完成后调用一次；引擎不可用时也会上报）。
     *
     * 参数语义与「为什么不用 String 传计数」的取舍见 [MarkdownBridgeCallback.onMermaidResult]。
     */
    @JavascriptInterface
    fun onMermaidResult(
        rendered: Int,
        failed: Int,
        engineSupported: Boolean,
    ) {
        callback.onMermaidResult(rendered, failed, engineSupported)
    }
}
