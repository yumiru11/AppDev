package com.yumiru11.githubapp.core.markdown.webview

import org.junit.Assume
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * 离线渲染通道的 **Node 执行器**（JVM 单测用，不进 APK）。
 *
 * ## 为什么需要它
 *
 * WebView 路径的像素在 JVM 上测不到（`docs/research/screenshot-automation-alt.md`），
 * 但这不等于「什么都不能真跑」：`renderer.js` 的离线渲染入口
 * `renderOfflineHtml(raw, {repoContext})` 是**纯字符串 → 字符串**的纯函数，可以在 Node 里
 * 用真实的 `markdown-it.min.js` 真实执行。这补上了既有第 ③ 层（`WebViewOfflineGfmCapabilityTest`
 * 只能做源码文本断言）缺的那一环——正是它让 D3（离线不重写相对链接）长期潜伏：
 * 源码里有正则、产物里没有改写，文本断言看不出来。
 *
 * 执行的是**生产代码本身**（`src/main/assets/webview/renderer.js`），不是复刻实现。
 * 未覆盖：DOM 插入、ResizeObserver、DOMPurify 清洗、真实滚动 —— 那些需要真机 WebView。
 *
 * ## 与既有测试的路径约定一致
 *
 * Gradle 单测的工作目录是模块根（`core/markdown/`），与
 * `WebViewOfflineGfmCapabilityTest` 的 `File("src/main/assets/webview")` 同源。
 *
 * 本类不是测试类；断言与命名规范由调用方遵守（`methodName_scenario_expectedBehavior`）。
 */
internal object NodeRendererHarness {
    private const val TIMEOUT_SECONDS = 60L
    private val HARNESS_FILE = File("src/test/js/offline-render-harness.js")
    private val ASSETS_DIR = File("src/main/assets/webview")

    /** 可用的 node 可执行文件；未安装/不在 PATH 时为 null。 */
    val nodeBinary: String? by lazy {
        listOf("node", "nodejs", "/usr/bin/node").firstOrNull { candidate -> canRun(candidate) }
    }

    /**
     * 要求 node 可用：**不可用时用 JUnit `assume` 跳过**（CI runner 自带 node；
     * 本地缺 node 只跳过「执行层」，产物层断言仍会跑，不留假绿——跳过会在测试报告里显式列出）。
     */
    fun requireAvailable(): String {
        val node = nodeBinary
        Assume.assumeTrue(
            "node 不可用（未安装/不在 PATH）：跳过「离线渲染器真实执行」回归；" +
                "产物层断言（OfflineRelativeUrlRewriteTest / WebViewOfflineGfmCapabilityTest）仍然生效",
            node != null,
        )
        return node ?: error("node 不可用")
    }

    /** 守住测试基建本身不空转（harness/资源缺失必须红，而不是静默通过）。 */
    fun assertHarnessPresent() {
        check(HARNESS_FILE.isFile) { "Node harness 缺失（执行层会空转）: ${HARNESS_FILE.absolutePath}" }
        check(ASSETS_DIR.isDirectory) { "WebView 资源目录缺失: ${ASSETS_DIR.absolutePath}" }
    }

    /**
     * 用 node 执行真实 `renderer.js` 的离线渲染，返回注入 WebView 的 HTML（DOMPurify 清洗前）。
     *
     * @param markdown 原始 markdown（离线通道的输入）
     * @param repoContext `owner/repo`；null 表示无仓库上下文（相对路径应原样保留）
     */
    fun renderOffline(
        markdown: String,
        repoContext: String?,
    ): String {
        val node = requireAvailable()
        assertHarnessPresent()

        val markdownFile = File.createTempFile("offline-gfm-", ".md")
        val stdoutFile = File.createTempFile("offline-gfm-out-", ".html")
        val stderrFile = File.createTempFile("offline-gfm-err-", ".log")
        return try {
            markdownFile.writeText(markdown)
            val process =
                ProcessBuilder(
                    node,
                    HARNESS_FILE.absolutePath,
                    markdownFile.absolutePath,
                    repoContext ?: "-",
                ).redirectOutput(stdoutFile)
                    .redirectError(stderrFile)
                    .start()
            val finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            if (!finished) {
                process.destroyForcibly()
                error("node harness 超时（${TIMEOUT_SECONDS}s）：${HARNESS_FILE.path}")
            }
            val exitCode = process.exitValue()
            if (exitCode != 0) {
                error("node harness 退出码 $exitCode：${stderrFile.readText().take(2_000)}")
            }
            stdoutFile.readText()
        } finally {
            markdownFile.delete()
            stdoutFile.delete()
            stderrFile.delete()
        }
    }

    private fun canRun(candidate: String): Boolean =
        try {
            val process =
                ProcessBuilder(candidate, "--version")
                    .redirectErrorStream(true)
                    .redirectOutput(File("/dev/null"))
                    .start()
            val finished = process.waitFor(20, TimeUnit.SECONDS)
            if (!finished) process.destroyForcibly()
            finished && process.exitValue() == 0
        } catch (_: IOException) {
            false
        }
}
