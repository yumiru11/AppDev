package com.yumiru11.githubapp.core.markdown.webview

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 离线 GFM 通道的**相对链接/图片改写**产物契约（D3 修复的守门测试，纯 JVM 无 JS 引擎）。
 *
 * ## 缺陷（`docs/agents/markdown-consistency-2026-09-11.md` §4 D3）
 *
 * 离线通道（Issue/PR 正文的**唯一**通道）此前**完全不改写**相对链接/图片：
 * `WebViewHtmlBuilder.kt` 的 `rewriteRelativeUrls(buildContentBlock(...))` 作用在
 * **已转义的属性值**上——`data-markdown-raw="…"` 里没有 `<img>` 标签，属性正则无从匹配。
 * 而 WebView base 是 `https://appassets.androidplatform.net/`，相对路径解析到 appassets 域 → 404。
 *
 * ## 修法与分工（本类锁定的契约）
 *
 * - Kotlin：只把仓库上下文（`owner/repo`，公开信息）以 `data-base-repo` 交给脚本，
 *   **不在 markdown 文本上做正则改写**；
 * - `renderer.js`：在 markdown-it 的**渲染产物**上改写（代码块已是转义文本 → 误伤不可能；
 *   引用式链接、内联 HTML `<img src>` 一并覆盖），且发生在 DOMPurify 清洗之前。
 *
 * 「真的改写了」由 [OfflineRendererExecutionTest]（Node 执行真实 renderer.js）证明；
 * 本类证明**输入侧契约**（上下文正确传递/正确缺省/正确转义）与两条通道的规则一致性。
 *
 * 测试命名规范：methodName_scenario_expectedBehavior。
 */
class OfflineRelativeUrlRewriteTest {
    @Test
    fun build_offlineMode_withBaseRepoUrl_handsRepoContextToRenderer() {
        val html =
            WebViewHtmlBuilder.build(
                sanitizedHtml = "![img](./docs/x.png)",
                tokens = MarkdownThemeTokens.fromLightScheme(),
                renderMode = RenderMode.OFFLINE_MARKDOWN_IT,
                baseRepoUrl = "https://github.com/octo/hello",
            )

        assertTrue(
            "离线产物必须把仓库上下文交给 renderer.js（它才可能改写相对路径）",
            html.contains("data-base-repo=\"octo/hello\""),
        )
        assertTrue(
            "raw markdown 必须原样保留（改写发生在渲染产物层，不在文本上做正则）",
            html.contains("![img](./docs/x.png)"),
        )
        assertFalse("不得在 markdown 文本层改写出 raw 域 URL", html.contains("raw.githubusercontent.com"))
    }

    @Test
    fun build_offlineMode_withoutBaseRepoUrl_omitsRepoContext() {
        val html =
            WebViewHtmlBuilder.build(
                sanitizedHtml = "![img](./docs/x.png)",
                tokens = MarkdownThemeTokens.fromLightScheme(),
                renderMode = RenderMode.OFFLINE_MARKDOWN_IT,
            )

        assertFalse("无 baseRepoUrl 时不得注入仓库上下文（无上下文可解析）", html.contains("data-base-repo"))
    }

    @Test
    fun build_offlineMode_blankOrMalformedBaseRepoUrl_omitsRepoContext() {
        listOf("", "https://github.com/onlyowner").forEach { base ->
            val html =
                WebViewHtmlBuilder.build(
                    sanitizedHtml = "![img](./docs/x.png)",
                    tokens = MarkdownThemeTokens.fromLightScheme(),
                    renderMode = RenderMode.OFFLINE_MARKDOWN_IT,
                    baseRepoUrl = base,
                )

            assertFalse("非法 baseRepoUrl「$base」不得注入仓库上下文", html.contains("data-base-repo"))
        }
    }

    @Test
    fun build_offlineMode_repoNameWithQuote_escapesAttributeValue() {
        // 属性注入防御：owner/repo 来自 API，进入 HTML 属性前必须转义（不能被构造成新属性）
        val html =
            WebViewHtmlBuilder.build(
                sanitizedHtml = "# T",
                tokens = MarkdownThemeTokens.fromLightScheme(),
                renderMode = RenderMode.OFFLINE_MARKDOWN_IT,
                baseRepoUrl = "https://github.com/oc\"to/hello",
            )

        assertTrue("引号必须被转义", html.contains("data-base-repo=\"oc&quot;to/hello\""))
        assertFalse("不得出现未转义的属性注入", html.contains("data-base-repo=\"oc\"to/hello\""))
    }

    @Test
    fun build_serverHtmlMode_doesNotEmitRepoContext() {
        // 服务端 HTML 通道的改写由 Kotlin 自己完成（输入已是 HTML），不经过 renderer.js
        val html =
            WebViewHtmlBuilder.build(
                sanitizedHtml = "<img src=\"docs/x.png\">",
                tokens = MarkdownThemeTokens.fromLightScheme(),
                renderMode = RenderMode.SERVER_HTML,
                baseRepoUrl = "https://github.com/octo/hello",
            )

        assertFalse("服务端 HTML 通道不得注入离线容器上下文", html.contains("data-base-repo"))
        assertTrue(
            "服务端 HTML 通道仍由 Kotlin 改写到 raw 域",
            html.contains("src=\"https://raw.githubusercontent.com/octo/hello/HEAD/docs/x.png\""),
        )
    }

    @Test
    fun build_serverHtmlMode_normalizesDotDotSegments_clampsToRepoRoot() {
        // 两条通道同一约定：`..` 折叠；越出仓库根时钳制（否则产出 blob/HEAD/../x 这类死链，
        // GitHubLinkParser 会把 `..` 当字面文件名）
        val html =
            WebViewHtmlBuilder.build(
                sanitizedHtml = "<img src=\"docs/../img/x.png\"><a href=\"../CONTRIBUTING.md\">c</a>",
                tokens = MarkdownThemeTokens.fromLightScheme(),
                renderMode = RenderMode.SERVER_HTML,
                baseRepoUrl = "https://github.com/octo/hello",
            )

        assertTrue(
            "中间段 .. 必须折叠",
            html.contains("src=\"https://raw.githubusercontent.com/octo/hello/HEAD/img/x.png\""),
        )
        assertTrue(
            "越出仓库根的 .. 必须钳制到仓库根",
            html.contains("href=\"https://github.com/octo/hello/blob/HEAD/CONTRIBUTING.md\""),
        )
        assertFalse("产物里不得残留 .. 路径段", html.contains("/HEAD/../"))
    }

    @Test
    fun rendererJs_rewritesRelativeUrlsOnRenderedProduct_beforeSanitize() {
        // 产物级证据：离线渲染入口存在、读取 Kotlin 注入的仓库上下文、两类目标域齐备
        val renderer = File("src/main/assets/webview/renderer.js").readText()

        assertTrue("离线渲染入口必须可被 Node 单测执行", renderer.contains("renderOfflineHtml"))
        assertTrue("必须读取 Kotlin 注入的仓库上下文", renderer.contains("'data-base-repo'"))
        assertTrue("相对图片 → raw 域", renderer.contains("raw.githubusercontent.com"))
        assertTrue("相对链接 → blob 域（点击后由 GitHubLinkParser 走应用内路由）", renderer.contains("/blob/HEAD/"))
        assertTrue(
            "改写必须在 DOMPurify 清洗之前（相对 src 会被 URI 白名单剔除）",
            renderer.indexOf("rewriteRelativeUrls(createMarkdownIt().render(raw)") in
                0..<renderer.indexOf("sanitizeNode(root)"),
        )
    }
}
