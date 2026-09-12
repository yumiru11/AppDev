package com.yumiru11.githubapp.core.markdown.fixture

/**
 * `plan.md` §2.3「渲染目标清单（GFM 全覆盖）」的夹具目录（fixture catalog）。
 *
 * ## 为什么需要它
 *
 * `request.txt` 的第一优先级验收标准是「readme 必须正确渲染，能做到与网页端一致的正常渲染
 * 部分写法」。在 2026-09-11 之前，全仓没有任何 Markdown golden/对比回归——§2.3 的 35 条写法
 * 与实现之间的对应关系只存在于散文里，无法核查。本目录把该清单变成**机器可校验**的夹具集：
 *
 * - 每条 §2.3 原子条目恰好对应一个 `.md` 夹具（[CLAUSES] 与 [ALL] 双向对照，见
 *   `MarkdownGfmFixtureCatalogTest`）；
 * - 每个夹具声明它在三条渲染路径上的支持情况（[RenderPath]），这张表就是
 *   `docs/agents/markdown-consistency-2026-09-11.md` 的数据源；
 * - 夹具内容取自 `core/markdown/src/test/resources/markdown-fixtures/`，同时被原生像素回归
 *   （`MarkdownFixtureScreenshotTest`）与 WebView 产物回归（`WebViewFixtureRenderModeTest`）消费。
 *
 * ## 三条渲染路径（ADR-0007）
 *
 * - [RenderPath.NATIVE]：Compose 原生链（`EnhancedMarkdownViewer` / `MarkdownViewer`，
 *   mikepenz 0.38.1 + GFMFlavourDescriptor）。服务评论列表与通知等短文本。
 * - [RenderPath.SERVER_HTML]：GitHub 服务端 HTML（`GET /repos/{o}/{r}/readme` html Accept
 *   或 `POST /markdown` gfm+context）经 DOMPurify 清洗后由 WebView 渲染。README 一级通道。
 * - [RenderPath.OFFLINE_GFM]：离线 markdown-it 14.1.0 + highlight.js + `renderer.js`
 *   自维护插件（Alert / 任务列表）。Issue 正文与 README 服务端异常时的降级通道。
 *
 * **诚实声明**：路径集合 ⊆ 表示「有渲染能力」，不等于「与网页端像素一致」。
 * 原生路径的像素等价性由 `MarkdownFixtureScreenshotTest` 的基线证明（基线待云端录制）；
 * WebView 路径在 Robolectric 下**无法栅格化像素**（见 `docs/research/screenshot-automation-alt.md`），
 * 只能证明 HTML/CSS 产物契约，真实像素靠 CI 模拟器截图（`.github/scripts/screenshots.sh`）。
 */
object MarkdownGfmFixtures {
    /** 夹具资源目录（`core/markdown/src/test/resources/`）。 */
    const val RESOURCE_DIR: String = "markdown-fixtures"

    /**
     * 渲染路径。三条路径互相独立。
     *
     * **语义**：`path ∈ paths` 表示该路径能把这条 §2.3 写法渲染成**与网页端等价**的结果；
     * `path ∉ paths` 表示该路径要么完全不渲染它，要么只降级成别的东西（例：Mermaid 围栏
     * 在 WebView 里会退化成普通代码块，但因为没有图表运行时，三条路径都不算「渲染了 Mermaid」）。
     */
    enum class RenderPath {
        NATIVE,
        SERVER_HTML,
        OFFLINE_GFM,
    }

    /**
     * 一个 §2.3 夹具。
     *
     * @param id 夹具 id（= 文件名去 `.md`），形如 `01-headings`
     * @param clause §2.3 清单里的原子条目原文（逐字对齐 plan.md，便于人工核对）
     * @param paths 支持该写法的渲染路径集合；空集 = 三条路径都不渲染（未实现）
     * @param darkBaseline 是否也纳入深色主题像素基线（见 `MarkdownFixtureScreenshotTest`）
     * @param note 实现细节/偏差说明，供报告引用
     */
    data class Fixture(
        val id: String,
        val clause: String,
        val paths: Set<RenderPath>,
        val darkBaseline: Boolean = false,
        val note: String = "",
    ) {
        /** 资源文件名。 */
        val fileName: String get() = "$id.md"

        /** 该夹具是否纳入原生像素基线。 */
        val isNative: Boolean get() = RenderPath.NATIVE in paths

        /** 读取夹具正文；资源缺失时抛异常（禁止「找不到就静默跳过」的假绿）。 */
        fun markdown(): String {
            val stream =
                MarkdownGfmFixtures::class.java.getResourceAsStream("/$RESOURCE_DIR/$fileName")
                    ?: error("GFM fixture not found on test classpath: $RESOURCE_DIR/$fileName")
            return stream.use { it.readBytes().decodeToString() }
        }
    }

    /** 三条路径全支持（§2.3 里大多数写法）。 */
    private val ALL_PATHS = RenderPath.entries.toSet()

    /**
     * §2.3 清单的 35 条原子条目（原文来自 `plan.md` §2.3，逐条拆分到最小正交单元）。
     *
     * 完整性由 `MarkdownGfmFixtureCatalogTest.allClauses_haveExactlyOneFixture_areCovered` 强制：
     * 少了夹具 → 测试红；多了夹具 → 测试红。这样「§2.3 全覆盖」从口号变成断言。
     */
    val CLAUSES: List<String> =
        listOf(
            "标题",
            "段落",
            "引用块（blockquote）",
            "加粗",
            "斜体",
            "删除线",
            "表格（含单元格内联内容、对齐）",
            "有序列表",
            "无序列表",
            "嵌套列表",
            "任务列表 `- [ ]` / `- [x]`",
            "行内代码",
            "围栏代码块",
            "代码语言标注",
            "语法高亮",
            "代码块复制按钮",
            "图片（相对路径引用）",
            "图片（GitHub 缓存域）",
            "外部链接",
            "自动链接（裸 URL）",
            "相对链接（`./`、`../`、`/owner/repo`）",
            "提及：`@user`",
            "提及：`@org/team`",
            "引用：`#123`、`owner/repo#123`、`gh-123`",
            "提交引用（裸 sha）",
            "Emoji 短句：`:rocket:`",
            "GitHub Alerts：`[!NOTE]`/`[!TIP]`/`[!IMPORTANT]`/`[!WARNING]`/`[!CAUTION]`",
            "锚点跳转（`#section`）",
            "图片：懒加载",
            "图片：点击放大",
            "图片：GIF",
            "内嵌 HTML（安全子集）",
            "Math/KaTeX（兜底通道，可选）",
            "Mermaid（兜底通道，可选）",
            "脚注（尽力而为，不保证与网页完全一致）",
        )

    /** 全部夹具，顺序 = §2.3 清单顺序。 */
    val ALL: List<Fixture> =
        listOf(
            Fixture("01-headings", "标题", ALL_PATHS, darkBaseline = false),
            Fixture("02-paragraphs", "段落", ALL_PATHS),
            Fixture("03-blockquote", "引用块（blockquote）", ALL_PATHS, darkBaseline = true),
            Fixture("04-bold", "加粗", ALL_PATHS),
            Fixture("05-italic", "斜体", ALL_PATHS),
            Fixture("06-strikethrough", "删除线", ALL_PATHS),
            Fixture(
                "07-table",
                "表格（含单元格内联内容、对齐）",
                ALL_PATHS,
                darkBaseline = true,
                note = "原生链按 ADR-0005 接受超出容器即裁剪，不做横滚组件",
            ),
            Fixture("08-ordered-list", "有序列表", ALL_PATHS),
            Fixture("09-unordered-list", "无序列表", ALL_PATHS),
            Fixture("10-nested-list", "嵌套列表", ALL_PATHS),
            Fixture("11-task-list", "任务列表 `- [ ]` / `- [x]`", ALL_PATHS, darkBaseline = true),
            Fixture("12-inline-code", "行内代码", ALL_PATHS),
            Fixture("13-fenced-code-block", "围栏代码块", ALL_PATHS, darkBaseline = true),
            Fixture("14-code-language-tag", "代码语言标注", ALL_PATHS),
            Fixture("15-syntax-highlight", "语法高亮", ALL_PATHS, darkBaseline = true),
            Fixture("16-code-copy", "代码块复制按钮", ALL_PATHS),
            Fixture(
                "17-image-relative",
                "图片（相对路径引用）",
                ALL_PATHS,
                note =
                    "离线通道由 renderer.js 在 markdown-it 渲染产物层改写相对 img src → raw 域" +
                        "（2026-09-12 修复；Node 真实执行回归见 OfflineRendererExecutionTest）。" +
                        "原生侧另有缺陷：EnhancedMarkdownViewer 未把 baseRepoUrl 透传给 " +
                        "EnhancedMarkdownImage（见报告「发现但未修」第 2 条）",
            ),
            Fixture("18-image-github-cache-domain", "图片（GitHub 缓存域）", ALL_PATHS),
            Fixture("19-external-link", "外部链接", ALL_PATHS),
            Fixture("20-autolink", "自动链接（裸 URL）", ALL_PATHS),
            Fixture(
                "21-relative-link",
                "相对链接（`./`、`../`、`/owner/repo`）",
                ALL_PATHS,
                note =
                    "原生链在点击时分流解析（resolveMarkdownUrl）；离线通道由 renderer.js 在渲染产物层" +
                        "改写（blob/HEAD 与站点路由形态，2026-09-12 修复）",
            ),
            Fixture(
                "22-mention-user",
                "提及：`@user`",
                setOf(RenderPath.SERVER_HTML),
                note = "原生 + 离线 markdown-it 均无 mention 插件，按纯文本渲染",
            ),
            Fixture(
                "23-mention-org-team",
                "提及：`@org/team`",
                setOf(RenderPath.SERVER_HTML),
                note = "同 @user，无 org/team mention 支持",
            ),
            Fixture(
                "24-issue-ref",
                "引用：`#123`、`owner/repo#123`、`gh-123`",
                setOf(RenderPath.SERVER_HTML),
                note = "GitHubLinkParser 只在链接点击时解析绝对 URL，正文里的裸 #123 不被 linkify",
            ),
            Fixture(
                "25-commit-sha-ref",
                "提交引用（裸 sha）",
                setOf(RenderPath.SERVER_HTML),
                note = "同上：正文裸 sha 无自动链接",
            ),
            Fixture(
                "26-emoji-shortcode",
                "Emoji 短句：`:rocket:`",
                setOf(RenderPath.SERVER_HTML, RenderPath.OFFLINE_GFM),
                note =
                    "原生链无 emoji 插件（mikepenz 按纯文本渲染）；离线通道由 renderer.js 的 " +
                        "emojiPlugin 补齐（gemoji 常用子集 → Unicode，2026-09-12）；未收录的短码原样保留。" +
                        "代码块/行内代码结构上不受影响",
            ),
            Fixture(
                "27-github-alerts",
                "GitHub Alerts：`[!NOTE]`/`[!TIP]`/`[!IMPORTANT]`/`[!WARNING]`/`[!CAUTION]`",
                ALL_PATHS,
                darkBaseline = true,
            ),
            Fixture(
                "28-anchor-jump",
                "锚点跳转（`#section`）",
                setOf(RenderPath.OFFLINE_GFM),
                note =
                    "离线通道：renderer.js 的 anchorPlugin 生成 GitHub slug 的 heading id，并暴露 " +
                        "window.scrollToAnchor(id)；页内 # 链接点击走 WebView 内滚动（2026-09-12）。" +
                        "服务端 HTML / 原生通道无 id 生成与滚动绑定",
            ),
            Fixture(
                "29-image-lazy",
                "图片：懒加载",
                setOf(RenderPath.SERVER_HTML, RenderPath.OFFLINE_GFM),
                note =
                    "离线产物由 renderOfflineHtml 字符串层注入 loading=\"lazy\"/decoding=\"async\"；" +
                        "服务端 HTML 主通道由 renderer.js 的 decorateImages 在 DOMPurify 清洗后补，" +
                        "不覆盖显式值（2026-09-12）。原生链（Coil AsyncImage）未纳入本项判定",
            ),
            Fixture(
                "30-image-zoom",
                "图片：点击放大",
                ALL_PATHS,
                note = "原生 EnhancedMarkdownImage 全屏 Dialog；WebView 侧 renderer.js bindImages → onImageClick",
            ),
            Fixture(
                "31-image-gif",
                "图片：GIF",
                setOf(RenderPath.SERVER_HTML, RenderPath.OFFLINE_GFM),
                note = "WebView 原生支持 GIF 动画；原生链无 coil-gif 依赖 → 动图只出首帧",
            ),
            Fixture(
                "32-inline-html",
                "内嵌 HTML（安全子集）",
                ALL_PATHS,
                darkBaseline = true,
                note =
                    "夹具只含 <details> / <kbd> / <sub> / <sup> / <script>，**刻意不含 shields 徽章**：" +
                        "徽章渲染走 coil3 AsyncImage 且无注入口，需要真实网络 → 像素不确定，" +
                        "不能进 golden 基线（徽章解析本身由 EnhancedHtmlBlockTest 覆盖）。" +
                        "已知偏差：<details> 采用 GitHub 惯用的「空行分隔」写法时，块正文会被同时" +
                        "折叠进卡片**并**以普通段落常驻可见（重复渲染，见报告「发现但未修」第 1 条）",
            ),
            Fixture(
                "33-math-katex",
                "Math/KaTeX（兜底通道，可选）",
                emptySet(),
                note = "未实现：assets/webview 无 KaTeX，markdown-it 未打包 math 插件",
            ),
            Fixture(
                "34-mermaid",
                "Mermaid（兜底通道，可选）",
                emptySet(),
                note = "未实现：无 Mermaid 运行时；围栏降级为普通代码块（语言标签 mermaid）",
            ),
            Fixture(
                "35-footnote",
                "脚注（尽力而为，不保证与网页完全一致）",
                setOf(RenderPath.SERVER_HTML, RenderPath.OFFLINE_GFM),
                note =
                    "离线通道由 renderer.js 的 footnotePlugin 补齐（单行/四空格续行定义，" +
                        "2026-09-12）；服务端 HTML 通道由 GitHub 渲染脚注。简化点：多段落定义不嵌套解析",
            ),
        )

    /** 原生像素基线的文件名前缀（`core/markdown/src/test/screenshots/`）。 */
    const val BASELINE_PREFIX: String = "MarkdownFixture"

    /**
     * 基线文件名（不含扩展名）。
     *
     * @param dark 是否深色主题
     */
    fun baselineName(
        fixture: Fixture,
        dark: Boolean,
    ): String = "${BASELINE_PREFIX}_${fixture.id}_${if (dark) "dark" else "light"}"

    /**
     * 本回归集需要的全部基线文件名。
     *
     * 这是「需录制基线清单」的唯一事实来源——交付说明与报告都引用它，避免手写清单漂移。
     */
    fun baselineNames(): List<String> =
        nativeFixtures().map { baselineName(it, dark = false) } +
            darkFixtures().map { baselineName(it, dark = true) }

    /** 支持原生渲染的夹具（`MarkdownFixtureScreenshotTest` 逐个建基线）。 */
    fun nativeFixtures(): List<Fixture> = ALL.filter { it.isNative }

    /** 纳入深色主题基线的夹具。 */
    fun darkFixtures(): List<Fixture> = nativeFixtures().filter { it.darkBaseline }

    /** 三条路径都不渲染的夹具（报告里的 ❌ 行）。 */
    fun unimplementedFixtures(): List<Fixture> = ALL.filter { it.paths.isEmpty() }

    /** 按 id 查夹具；未知 id 抛异常（禁止静默跳过）。 */
    fun byId(id: String): Fixture = ALL.firstOrNull { it.id == id } ?: error("Unknown GFM fixture id: $id")
}
