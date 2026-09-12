package com.yumiru11.githubapp.core.markdown.webview

import com.yumiru11.githubapp.core.markdown.fixture.MarkdownGfmFixtures
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 离线渲染通道的**可执行回归**：用 node 跑真实的 `markdown-it.min.js` + `renderer.js`，
 * 断言真实渲染产物（见 [NodeRendererHarness] 说明「为什么需要它」）。
 *
 * 这是第 ③ 层（WebView 产物层）里唯一**真跑 JS** 的一环，覆盖：
 * 相对链接/图片改写（D3）、emoji 短码、脚注、锚点 id。
 * 不覆盖（真机 WebView 才有）：DOM 插入、DOMPurify 清洗、ResizeObserver、真实滚动。
 *
 * node 不可用时整个类由 `assume` 跳过（CI runner 自带 node），
 * 产物层断言仍在 [OfflineRelativeUrlRewriteTest] / [WebViewOfflineGfmCapabilityTest] 生效。
 *
 * 测试命名规范：methodName_scenario_expectedBehavior。
 */
class OfflineRendererExecutionTest {
    // ── D3：相对链接/图片改写 ────────────────────────────────────────────

    @Test
    fun offlineRender_fixture17_rewritesRelativeImagesWithAndWithoutReferenceStyle() {
        val html = renderFixture("17-image-relative")

        assertTrue(
            "直接相对路径图片必须指向 raw 域（此前 base=appassets → 404）",
            html.contains("src=\"https://raw.githubusercontent.com/octocat/Hello-World/HEAD/docs/screenshot.png\""),
        )
        assertTrue(
            "引用式相对图片（`[ref]: ../assets/diagram.png`）同样必须被改写（正则改写做不到，渲染产物层可以）",
            html.contains("src=\"https://appassets.androidplatform.net/assets/webview/diagram.png\""),
        )
    }

    @Test
    fun offlineRender_fixture21_rewritesRelativeLinksToAppRoutes() {
        val html = renderFixture("21-relative-link")

        assertTrue(
            "`./` 相对链接 → blob 域（点击后 GitHubLinkParser 走应用内文件浏览）",
            html.contains("href=\"https://github.com/octocat/Hello-World/blob/HEAD/docs/architecture.md\""),
        )
        assertTrue(
            "`../` 相对链接必须归一化（不得产出含 `..` 的 blob 死链）",
            html.contains("href=\"https://github.com/octocat/Hello-World/blob/HEAD/CONTRIBUTING.md\""),
        )
        assertTrue(
            "`/owner/repo/...` 站点路径形态 → github.com 站点路由（plan §2.11 的输入形态）",
            html.contains("href=\"https://github.com/owner/repo/blob/main/README.md\""),
        )
        assertTrue(
            "仓库内相对 issue（`issues/123`）→ issue 路由，而不是仓库里名为 issues/123 的文件",
            html.contains("href=\"https://github.com/octocat/Hello-World/issues/123\""),
        )
        assertTrue("`#section` 页内锚点必须保持原样（交给 scrollToAnchor）", html.contains("href=\"#section\""))
        assertFalse("产物里不得残留未改写的相对链接", html.contains("href=\"./docs/architecture.md\""))
    }

    @Test
    fun offlineRender_codeFenceAndInlineCode_keepOriginalText() {
        val markdown =
            """
            ```html
            <img src="docs/x.png">
            [a](./b.md)
            ```

            行内代码：`[c](./d.md)` 与 `<img src="e.png">`
            """.trimIndent()

        val html = render(markdown)

        assertTrue("围栏代码块里的示例必须原样保留（转义文本）", html.contains("&lt;img src=&quot;docs/x.png&quot;&gt;"))
        assertTrue("围栏代码块里的相对链接必须原样保留", html.contains("[a](./b.md)"))
        assertTrue("行内代码里的相对链接必须原样保留", html.contains("<code>[c](./d.md)</code>"))
        assertFalse("代码块内容不得被改写成绝对 URL", html.contains("HEAD/docs/x.png"))
    }

    @Test
    fun offlineRender_inlineHtmlImage_isRewrittenLikeMarkdownImage() {
        // markdown 内嵌 HTML（html: true）里的相对图片：纯文本正则在 raw markdown 上会漏（或误伤代码块），
        // 渲染产物层按真实属性改写
        val html = render("<img src=\"inline/img.png\" alt=\"i\">")

        assertTrue(
            "内联 HTML 的相对 img src 必须被改写",
            html.contains("src=\"https://raw.githubusercontent.com/octocat/Hello-World/HEAD/inline/img.png\""),
        )
    }

    @Test
    fun offlineRender_absoluteAndAnchorTargets_areLeftUntouched() {
        val html =
            render(
                "![a](https://cdn.example.com/x.png)\n\n[c](https://github.com/other/proj)\n\n[d](#section)\n",
            )

        assertTrue("绝对图片 URL 原样保留", html.contains("src=\"https://cdn.example.com/x.png\""))
        assertTrue("绝对链接原样保留", html.contains("href=\"https://github.com/other/proj\""))
        assertTrue("纯锚点原样保留", html.contains("href=\"#section\""))
    }

    @Test
    fun offlineRender_withoutRepoContext_keepsRelativePaths() {
        val html = render("![a](./docs/x.png)\n\n[b](./docs/y.md)\n", repoContext = null)

        assertTrue("无仓库上下文时相对图片原样保留", html.contains("src=\"./docs/x.png\""))
        assertTrue("无仓库上下文时相对链接原样保留", html.contains("href=\"./docs/y.md\""))
        assertFalse("无仓库上下文时不得凭空造出 raw 域", html.contains("raw.githubusercontent.com"))
    }

    // ── emoji 短码 / 脚注 / 锚点（审计 §9 第 4 条的三项补齐，2026-09-12） ────

    @Test
    fun offlineRender_emojiShortcodes_areConvertedOutsideCode() {
        val html = renderFixture("26-emoji-shortcode")

        assertTrue("`:rocket:` → 🚀、`:tada:` → 🎉", html.contains("🚀") && html.contains("🎉"))
        assertTrue("`:+1:` / `:bug:` / `:sparkles:` 也在映射表内", html.contains("👍") && html.contains("🐛") && html.contains("✨"))
        assertFalse("已识别的短码不得残留原文", html.contains(":rocket:") || html.contains(":tada:"))
    }

    @Test
    fun offlineRender_emojiShortcodes_doNotTouchFencedOrInlineCode() {
        val markdown =
            """
            ```text
            :rocket: **:tada:**
            ```

            行内：`:rocket:` 与 `:+1:`
            """.trimIndent()

        val html = render(markdown)

        assertTrue("围栏代码块里的短码必须原样保留", html.contains(":rocket: **:tada:**"))
        assertTrue("行内代码里的短码必须原样保留", html.contains("<code>:rocket:</code>"))
        assertTrue("行内代码里的 +1 短码必须原样保留", html.contains("<code>:+1:</code>"))
        assertFalse("代码里的短码不得被替换成 emoji", html.contains("🚀") || html.contains("👍"))
    }

    @Test
    fun offlineRender_footnoteRefsAndDefinitions_renderGitHubLikeStructure() {
        val html = renderFixture("35-footnote")

        assertTrue("引用 → [data-footnote-ref] 上标链接", html.contains("data-footnote-ref"))
        assertTrue("定义区 → section.footnotes", html.contains("<section class=\"footnotes\" data-footnotes>"))
        assertTrue("编号按引用出现顺序", html.contains("id=\"fn-1\"") && html.contains("id=\"fn-2\""))
        assertTrue("回跳锚点齐备", html.contains("data-footnote-backref") && html.contains("href=\"#fnref-1\""))
        assertTrue("定义正文按 markdown 渲染（含行内代码）", html.contains("<code>代码</code>"))
        assertFalse("定义行不得不残留在正文", html.contains("[^1]:") || html.contains("[^note]:"))
    }

    @Test
    fun offlineRender_footnoteWithoutDefinition_staysLiteralText() {
        // 无定义时不能把正文吃掉（GitHub 同样按普通文本渲染）
        val html = render("正文[^ghost] 继续。")

        assertTrue("未定义的脚注引用保留原文", html.contains("[^ghost]"))
        assertFalse("不得凭空生成脚注区", html.contains("class=\"footnotes\""))
    }

    @Test
    fun offlineRender_headingAnchors_generateSlugIdsAndKeepAnchorLinks() {
        val html = renderFixture("28-anchor-jump")

        assertTrue("中文标题 → 同名 id", html.contains("id=\"目标章节\""))
        assertTrue("英文标题 → GitHub slug id", html.contains("id=\"section-with-hyphen\""))
        assertTrue("页内锚点保持 # 形态（点击交给 scrollToAnchor）", html.contains("href=\"#section-with-hyphen\""))
        assertTrue(
            "中文锚点的百分号编码形态也保持 # 开头（scrollToAnchor 会 decode）",
            html.contains("href=\"#%E7%9B%AE%E6%A0%87%E7%AB%A0%E8%8A%82\""),
        )
        assertFalse("不得把 # 锚点改写成绝对链接", html.contains("github.com/octocat/Hello-World/blob/HEAD/#"))
    }

    @Test
    fun offlineRender_duplicateHeadings_getSuffixedSlugIds() {
        val html = render("## Same\n\n## Same\n")

        assertTrue("首个标题用基础 slug", html.contains("id=\"same\""))
        assertTrue("重复标题必须加 -1 后缀（否则锚点只能跳到第一个）", html.contains("id=\"same-1\""))
    }

    // ── 共用 ────────────────────────────────────────────────────────────

    private fun renderFixture(id: String): String = render(MarkdownGfmFixtures.byId(id).markdown())

    private fun render(
        markdown: String,
        repoContext: String? = REPO_CONTEXT,
    ): String = NodeRendererHarness.renderOffline(markdown, repoContext)

    private companion object {
        const val REPO_CONTEXT = "octocat/Hello-World"
    }
}
