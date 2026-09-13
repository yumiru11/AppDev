package com.yumiru11.githubapp.core.markdown.webview

/**
 * Mermaid 渲染结果的**可观测行**格式化（只拼字符串，不调用 `android.util.Log`）。
 *
 * 格式是**跨仓库契约**：`.github/scripts/mermaid-render-verify.sh` 与
 * `.github/workflows/mermaid-render-verify.yml` 用 grep 断言
 * `MermaidRender: engine=supported|blocked rendered=N failed=M` —— 改格式必须同步脚本与
 * [com.yumiru11.githubapp.core.markdown.webview.MermaidRenderLogTest]（后者把格式钉死）。
 *
 * 为什么在 core:markdown 而不在打印日志的 feature 层：格式契约属于 bridge 协议的一部分
 * （字段语义见 [MarkdownBridgeCallback.onMermaidResult]），宿主只管在消费回调时调用它
 * （feature:repo 的 README 路径是 CI 断言的那一处）。
 */
object MermaidRenderLog {
    /** 稳定前缀（CI logcat grep 锚点）。 */
    const val PREFIX: String = "MermaidRender"

    /** 组装一行：`MermaidRender: engine=supported|blocked rendered=N failed=M`。 */
    fun line(
        rendered: Int,
        failed: Int,
        engineSupported: Boolean,
    ): String {
        val engine = if (engineSupported) "supported" else "blocked"
        return "$PREFIX: engine=$engine rendered=$rendered failed=$failed"
    }
}
