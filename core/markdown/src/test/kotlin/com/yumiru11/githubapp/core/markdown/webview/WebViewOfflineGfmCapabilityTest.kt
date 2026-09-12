package com.yumiru11.githubapp.core.markdown.webview

import com.yumiru11.githubapp.core.markdown.fixture.MarkdownGfmFixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 离线 GFM 通道（`OFFLINE_MARKDOWN_IT`）的**渲染器能力契约**回归。
 *
 * ## 为什么需要它
 *
 * WebView 路径的像素在 JVM 上测不了，但「离线通道到底支持哪些 §2.3 写法」是**产物可查**的：
 * `assets/webview/markdown-it.min.js`（版本 + 是否打包了 footnote/emoji/anchor 插件）、
 * `renderer.js`（自维护的 Alert / 任务列表插件、bridge 绑定）、以及是否打包了
 * KaTeX / Mermaid 运行时。
 *
 * 本类把 `docs/agents/markdown-consistency-2026-09-11.md` 里关于离线通道的每一条结论
 * **变成断言**：结论变了而断言没动 → 测试红，强制同步报告。
 *
 * 2026-09-12 起，脚注/emoji/锚点由 `renderer.js` 的自维护插件补齐（与 Alert/任务列表同一
 * 做法），bundle 依旧不打包对应插件——**真实渲染行为**由 `OfflineRendererExecutionTest`
 * （Node 执行真实 renderer.js）覆盖，本类只保「能力面 + 缺口集合」的产物级契约。
 *
 * ## 边界（诚实声明）
 *
 * - markdown-it bundle 未打包 footnote/emoji/anchor 插件；离线能力由 renderer.js 自维护，
 *   修改插件清单必须同步 `OFFLINE_MARKERS` 与一致性报告。
 * - 核心语法（标题/列表/表格/删除线/代码/链接/自动链接/内联 HTML）由 markdown-it 14.1.0
 *   核心特性提供，本类用**版本横幅 + 选项字面量**锁定，不做语义级渲染断言。
 *
 * 测试命名规范：methodName_scenario_expectedBehavior。
 */
class WebViewOfflineGfmCapabilityTest {
    private val webviewAssetsDir = File("src/main/assets/webview")

    @Test
    fun webviewAssets_existOnDisk_guardAgainstSilentNoop() {
        assertTrue("WebView 资源目录必须存在（路径漂移会让本类全部空转）: ${webviewAssetsDir.path}", webviewAssetsDir.isDirectory)
        assertTrue("WebView 资源目录不得为空", (webviewAssetsDir.listFiles()?.size ?: 0) > 0)
    }

    @Test
    fun markdownItBundle_isPinnedAt14_1_0() {
        val bundle = markdownItBundle()

        assertTrue(
            "离线渲染的 GFM 能力以 markdown-it 版本为基线；升级版本必须同步一致性报告的离线通道结论",
            bundle.contains("markdown-it 14.1.0"),
        )
    }

    @Test
    fun markdownItBundle_doesNotBundlePlugins_rendererJsHandRollsThem() {
        val bundle = markdownItBundle()

        // bundle 保持原样（无 footnote/emoji/anchor 插件）；升级 bundle 会改变离线能力面
        assertFalse("markdown-it 14.1.0 未打包 markdown-it-footnote", bundle.contains("footnote"))
        assertFalse("未打包 markdown-it-emoji", bundle.contains("emoji"))
        assertFalse("未打包 markdown-it-anchor", bundle.contains("anchor"))

        // 三个能力由 renderer.js 的自维护插件提供（2026-09-12 补齐，同 Alert/任务列表做法）
        val renderer = rendererJs()
        assertTrue("脚注插件必须存在（footnotePlugin）", renderer.contains("footnotePlugin"))
        assertTrue("emoji 短码插件必须存在（emojiPlugin）", renderer.contains("emojiPlugin"))
        assertTrue("标题锚点插件必须存在（anchorPlugin）", renderer.contains("anchorPlugin"))
    }

    @Test
    fun webviewAssets_containNoKatexOrMermaidRuntime() {
        val files = webviewAssetsDir.listFiles()?.map { it.name }.orEmpty()

        val offenders = files.filter { name -> name.contains("katex", ignoreCase = true) || name.contains("mermaid", ignoreCase = true) }
        assertTrue("assets/webview 不得出现 KaTeX / Mermaid 运行时（§2.3 两条「兜底通道，可选」未实现）: $offenders", offenders.isEmpty())

        assertFalse("任何打包脚本都不得含 katex", markdownItBundle().contains("katex"))
        assertFalse("任何打包脚本都不得含 mermaid", markdownItBundle().contains("mermaid"))
        assertFalse("renderer.js 不得含 katex", rendererJs().contains("katex"))
        assertFalse("renderer.js 不得含 mermaid", rendererJs().contains("mermaid"))
    }

    @Test
    fun rendererJs_providesHandRolledAlertAndTaskListPlugins() {
        val renderer = rendererJs()

        assertTrue("GitHub Alerts 由自维护插件补齐（markdown-it 核心不含）", renderer.contains("githubAlertPlugin"))
        assertTrue("Alert 容器类名必须与 github-markdown-css 对齐", renderer.contains("markdown-alert-"))
        assertTrue("任务列表由自维护插件补齐", renderer.contains("taskListPlugin"))
        assertTrue("任务列表 checkbox 类名必须与 css 对齐", renderer.contains("task-list-item-checkbox"))
        assertTrue("五类 Alert 必须全部被识别", ALERT_TYPES.all { renderer.contains(it) })
    }

    @Test
    fun rendererJs_exposesWhitelistedBridgeCallbacksOnly() {
        val renderer = rendererJs()

        // plan §2.9 的 JS → Kotlin bridge 契约
        listOf("onLinkClick", "onCodeCopy", "onImageClick", "onCheckboxClick", "onHeightChanged").forEach { callback ->
            assertTrue("bridge 回调 $callback 必须被绑定", renderer.contains(callback))
        }
    }

    @Test
    fun rendererJs_exposesAnchorScrollApi_headingIdsAndClickInterception() {
        // plan §2.9 约定 Kotlin→JS 的 scrollToAnchor(id)：2026-09-12 由 renderer.js 落地。
        // heading id 生成（anchorPlugin）→ # 链接点击拦截 → WebView 内滚动，三步缺一不可。
        val renderer = rendererJs()

        assertTrue("必须暴露 Kotlin 可 evaluateJavascript 的入口", renderer.contains("window.scrollToAnchor = scrollToAnchor"))
        assertTrue("标题必须生成 slug id（anchorPlugin）", renderer.contains("anchorPlugin"))
        assertTrue("页内 # 锚点必须滚动而不是走 onLinkClick", renderer.contains("href.charAt(0) === '#'"))
        assertTrue(
            "28-anchor-jump 必须被登记为离线通道已支持",
            MarkdownGfmFixtures.RenderPath.OFFLINE_GFM in MarkdownGfmFixtures.byId("28-anchor-jump").paths,
        )
    }

    @Test
    fun rendererJs_injectsLazyLoadingAndAsyncDecoding() {
        val renderer = rendererJs()

        assertTrue("离线产物必须注入 loading=\"lazy\"", renderer.contains("loading=\"lazy\""))
        assertTrue("离线产物必须注入 decoding=\"async\"", renderer.contains("decoding=\"async\""))
        assertTrue(
            "29-image-lazy 必须被登记为离线通道已支持",
            MarkdownGfmFixtures.RenderPath.OFFLINE_GFM in MarkdownGfmFixtures.byId("29-image-lazy").paths,
        )
    }

    @Test
    fun rendererJs_sanitizesContentRootOnly_leavingInjectedCssOutside() {
        // 细节正确性：renderer.js 的 DOMPurify 配置 FORBID_TAGS 含 'style'，
        // 而注入的 <style id="theme-vars"> / <style id="app-css"> 位于 <head>。
        // 因此清洗必须作用在 .markdown-body 根节点上（sanitizeNode(root)），
        // 若改成清洗整篇 document，注入的主题 CSS 会被 DOMPurify 删掉 → 主题全丢。
        val renderer = rendererJs()

        assertTrue("DOMPurify 必须禁用 style 标签", renderer.contains("FORBID_TAGS") && renderer.contains("'style'"))
        assertTrue("清洗必须作用于内容根节点", renderer.contains("sanitizeNode(root)"))
        assertFalse("不得清洗整篇 document（会删掉注入的主题 CSS）", renderer.contains("sanitizeNode(document"))
    }

    @Test
    fun offlineGfmFixtures_requiredRendererCapability_isPresentInBundle() {
        val failures =
            MarkdownGfmFixtures.ALL
                .filter { MarkdownGfmFixtures.RenderPath.OFFLINE_GFM in it.paths }
                .mapNotNull { fixture ->
                    val marker = OFFLINE_MARKERS[fixture.id]
                    when {
                        marker == null -> "${fixture.id}: 声明了 OFFLINE_GFM 却没有能力标记，回归集会漏检"
                        !offlineSources().contains(marker) -> "${fixture.id}: 离线产物缺少能力标记「$marker」"
                        else -> null
                    }
                }

        assertTrue("离线通道声明的能力必须能在打包产物里找到依据:\n${failures.joinToString("\n")}", failures.isEmpty())
    }

    @Test
    fun offlineGfmUnsupportedFixtures_areExactlyTheDocumentedGapSet() {
        // 不含 OFFLINE_GFM 路径的夹具 = 离线通道的已知缺口，必须与报告一致
        val expected =
            setOf(
                "23-mention-org-team",
                "24-issue-ref",
                "25-commit-sha-ref",
                "33-math-katex",
                "34-mermaid",
            )
        val actual =
            MarkdownGfmFixtures.ALL
                .filterNot { MarkdownGfmFixtures.RenderPath.OFFLINE_GFM in it.paths }
                .map { it.id }
                .toSet()

        assertEquals("离线通道缺口集合变化时必须同步一致性报告", expected, actual)
    }

    @Test
    fun offlineGfmFixtures_count_matchesDocumentedCoverage() {
        val offlineIds =
            MarkdownGfmFixtures.ALL
                .filter { MarkdownGfmFixtures.RenderPath.OFFLINE_GFM in it.paths }
                .map { it.id }

        assertEquals(
            "离线通道覆盖的 §2.3 条目数（2026-09-12 @user 提及补齐后：23 + 相对图/相对链/emoji/锚点/脚注/图片懒加载 + 提及）",
            30,
            offlineIds.size,
        )
    }

    @Test
    fun rendererJs_purifyPinsInlineHtmlSemanticsAndMentionPlugin() {
        val renderer = rendererJs()

        assertTrue(
            "@user 提及插件必须存在（离线通道 → github.com/<user>）",
            renderer.contains("mentionPlugin"),
        )
        assertTrue(
            "<kbd>/<sub>/<sup> 必须显式钉在 DOMPurify 白名单（ADD_TAGS），防后续改配置时静默丢语义",
            renderer.contains("ADD_TAGS") && renderer.contains("'kbd', 'sub', 'sup'"),
        )
        assertFalse(
            "不得把 style/script 放进 ADD_TAGS（那不是语义钉住，是安全放宽）",
            renderer.contains("ADD_TAGS: ['script'") || renderer.contains("ADD_TAGS: ['style'"),
        )
    }

    private fun markdownItBundle(): String = readAsset("markdown-it.min.js")

    private fun rendererJs(): String = readAsset("renderer.js")

    private fun offlineSources(): String = markdownItBundle() + rendererJs()

    private fun readAsset(name: String): String {
        val file = File(webviewAssetsDir, name)
        assertTrue("资源必须存在（否则本测试静默空转）: ${file.path}", file.isFile)
        return file.readText()
    }

    private companion object {
        val ALERT_TYPES = listOf("NOTE", "TIP", "IMPORTANT", "WARNING", "CAUTION")

        /**
         * 夹具 → 离线产物里证明其可渲染的标记。
         *
         * markdown-it 核心语法统一用版本横幅（`markdown-it 14.1.0`）作为基线标记，
         * 自维护插件与 bridge 用具体符号名。
         */
        val OFFLINE_MARKERS: Map<String, String> =
            mapOf(
                "01-headings" to MARKDOWN_IT_BANNER,
                "02-paragraphs" to MARKDOWN_IT_BANNER,
                "03-blockquote" to MARKDOWN_IT_BANNER,
                "04-bold" to MARKDOWN_IT_BANNER,
                "05-italic" to MARKDOWN_IT_BANNER,
                "06-strikethrough" to MARKDOWN_IT_BANNER,
                "07-table" to MARKDOWN_IT_BANNER,
                "08-ordered-list" to MARKDOWN_IT_BANNER,
                "09-unordered-list" to MARKDOWN_IT_BANNER,
                "10-nested-list" to MARKDOWN_IT_BANNER,
                "11-task-list" to "taskListPlugin",
                "12-inline-code" to MARKDOWN_IT_BANNER,
                "13-fenced-code-block" to MARKDOWN_IT_BANNER,
                "14-code-language-tag" to MARKDOWN_IT_BANNER,
                "15-syntax-highlight" to "highlightElement",
                "16-code-copy" to "md-copy-btn",
                "17-image-relative" to "raw.githubusercontent.com",
                "18-image-github-cache-domain" to MARKDOWN_IT_BANNER,
                "19-external-link" to MARKDOWN_IT_BANNER,
                "20-autolink" to "linkify: true",
                "21-relative-link" to "/blob/HEAD/",
                "22-mention-user" to "mentionPlugin",
                "26-emoji-shortcode" to "emojiPlugin",
                "27-github-alerts" to "githubAlertPlugin",
                "28-anchor-jump" to "scrollToAnchor",
                "29-image-lazy" to "loading=\"lazy\"",
                "30-image-zoom" to "onImageClick",
                "31-image-gif" to MARKDOWN_IT_BANNER,
                "32-inline-html" to "html: true",
                "35-footnote" to "footnotePlugin",
            )

        const val MARKDOWN_IT_BANNER = "markdown-it 14.1.0"
    }
}
