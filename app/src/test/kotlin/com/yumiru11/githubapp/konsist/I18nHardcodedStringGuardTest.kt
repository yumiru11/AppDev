package com.yumiru11.githubapp.konsist

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * i18n 零硬编码文案守卫（项目红线「Compose 一律 stringResource()，禁止硬编码字符串」的自动门禁）。
 *
 * ## 为什么需要它（与同目录 [I18nParityTest] 的分工）
 *
 * - [I18nParityTest] 只校验**资源键的存在性**：en 有的 key，zh-rCN 必须有。它完全不看 Kotlin 源码。
 * - Android Lint 的 `HardcodedText` 只作用于 **XML 布局**，对 Compose 代码无效；而本仓 Compose
 *   相关 lint 检测器又因 Kotlin Analysis API 版本不匹配被整包跳过（见 `app/build.gradle.kts`
 *   的 lint 段注释与 issue #170）。
 * - 结果：「Kotlin 里直接写死用户可见文案」这条红线此前**没有任何自动检查**，只能靠人工审查。
 *   守卫的价值在于阻止**新增**泄漏，而不是一次清完历史欠账。
 *
 * 本测试对源码做**纯文本扫描**（不引入 Kotlin 编译器 / 解析器依赖，保持轻量、快、零依赖），
 * 覆盖 `core/<module>/src/main`、`feature/<module>/src/main`、`app/src/main` 下全部 `.kt`。
 *
 * ## 会抓什么
 *
 * 1. **硬编码中文**：main 源码里任何含 CJK 的字符串字面量（日志/异常消息、`@Preview` 夹具除外）。
 *    中文出现在 Android 生产源码里几乎必然是 UI 文案，这是精度最高的信号。
 * 2. `Text("…")` / `BasicText("…")` / `AnnotatedString("…")`：文本渲染位的字面量
 *    （含 `Text(text = "…")`、`Text(if (…) "A" else "B")`）。
 * 3. UI 文案形参：`contentDescription` / `supportingText` / `placeholder` / `headline` 等（即使只有一个单词也抓），
 *    以及 `label` / `title` / `text` / `message` 等**歧义形参**（要求值为多词自然语言，
 *    以排除 `Crossfade(label = "search-results")` 这类动画标签 / 测试 tag 误报）。
 * 4. `snackbarHostState.showSnackbar("…")` / `Toast.makeText(…, "…", …)`。
 * 5. **UI 源文件内的整句英文常量**：文件自身声明了 `@Composable`（或路径含 `/ui/`）时，
 *    多词且形如自然语言的孤立字面量（如 `private const val HINT = "Checking mergeability…"`）。
 *    这条专门覆盖「裸英文常量写进 UI 代码、既不在 `Text()` 里也不是形参」的泄漏形态。
 *
 * ## 不会抓什么（诚实说明局限）
 *
 * - **测试源码**（`src/test`、`src/androidTest`）、`build/` 产物、`prototype/` 不在扫描范围。
 *   `core:testing` 是夹具模块（只经 `testImplementation` 消费）但**仍在扫描范围内**——它符合
 *   `core/<module>/src/main` 的约定，且其中的字面量是夹具数据（`"The Octocat"`）而非 UI 文案，
 *   实测零命中；保留扫描范围以免留下「改个目录名就能躲过守卫」的口子。
 * - **日志与异常消息**：`Log.*` / `Timber.*` / `println` / `error` / `require` / `check` /
 *   `requireNotNull` / `checkNotNull` / `throw` / `*Exception(…)` 构造调用内的字面量一律豁免。
 * - **注解参数**：`@Suppress("…")`、`@Query("…")`、`@SerialName("…")` 等。
 * - **技术标识**：URL（含 `://`）、MediaType（`application/json`）、日期时间 pattern（`yyyy-MM-dd`）、
 *   正则（`Regex(…)` / `toRegex()`）、kebab/dot/colon 标识（`about:blank`、`theme-color`）、
 *   纯格式占位符（`%1$s`）、纯符号 / 数字、纯 ASCII 且长度 < 2。
 * - **模板里没有静态文字**的插值：`Text("$owner/$repo")`、`Text("${x}")` 不报——渲染的是数据而非文案。
 * - **`@Preview` 夹具**：`@Preview` 函数体内的字面量与文件名/路径含 `preview` 的文件整体豁免
 *   （dev-only 样例数据，不进用户可见路径）。**这是本守卫已知的最大盲区**。
 * - **纯文本扫描、无类型信息**，因此以下形态会漏：
 *   文案先经 `val s = "…"` 中转再进 `Text(s)`（跨语句/跨文件常量）；
 *   单字文案藏在 `Text(list.joinToString { "x" })` 这类「lambda 套在别的调用里」的形态；
 *   UI 文件里**全小写的多词技术短语**（如把 `"repo read:user"` 写进 UI 文件）会被误报，
 *   需用 `ALLOWED` 显式豁免。
 * - 只扫 `.kt`：`strings.xml` 的键存在性归 [I18nParityTest]，XML 硬编码归 Android Lint。
 *
 * ## 豁免与欠债（防误报失控 —— 误报会让守卫被 Suppress，从而彻底失效）
 *
 * - [ALLOWED]：确认合法的例外（文件 + 字面量 + 理由）。
 * - [KNOWN_DEBT]：**禁改文件**里扫出的既有命中（并行任务在改，本票不动它们），带 `TODO(#issue)` 标记。
 *   守卫允许它们存在以免阻塞上线，但新泄漏会被拦下。当前为空——干净 main 上经逐条核实无真泄漏。
 * - 两个清单都有**陈旧条目断言**：条目所指的命中一旦消失，测试会红并要求清理，防止豁免清单腐化成永久盲区。
 * - 另有**规模下限断言**：扫描文件数 / 字面量数低于下限即失败，防「门禁看起来在跑、其实什么都没查」
 *   （ArchitectureTest 的 `scopeResolution_…_coreScopeIsNotEmpty` 是同款防线）。
 *
 * 分工：`docs/agents/i18n-hardcoded-backlog.md` 记录现存命中分类明细与再收紧的代价。
 *
 * 测试命名规范：methodName_scenario_expectedBehavior。
 */
class I18nHardcodedStringGuardTest {
    @Test
    fun productionSources_noHardcodedUserFacingCopy() {
        val scan = CopyGuard.scan(repoRoot())

        // 防「glob 写坏 → 扫到 0 个文件 → 空转恒绿」：规模下限即自检。
        assertTrue(
            "扫描范围异常：只扫到 ${scan.files.size} 个 main 源文件（下限 $MIN_SCANNED_FILES）。" +
                "检查 CopyGuard.productionKotlinFiles 的目录约定是否随模块布局漂移。",
            scan.files.size >= MIN_SCANNED_FILES,
        )
        assertTrue(
            "扫描范围异常：只扫到 ${scan.literalCount} 个字面量（下限 $MIN_SCANNED_LITERALS），词法器可能已失效。",
            scan.literalCount >= MIN_SCANNED_LITERALS,
        )

        val violations = scan.findings.filter { it.kind == FindingKind.LEAK }
        assertTrue(hardcodedCopyMessage(violations), violations.isEmpty())
    }

    /**
     * 反向测试：喂入**人造的**泄漏片段，断言扫描器抓得到、且不误伤合法写法。
     *
     * 为什么必须有：项目史上出现过「门禁看起来在跑其实什么都没查」（Compose lint registry 被整包跳过，
     * 见 `app/build.gradle.kts` lint 段）。正则写错的扫描器会静默恒绿，比没有守卫更危险——它给人虚假的安全感。
     * 这里同时断言**必须抓到**与**必须不抓**：后者锁定误报面（`Text("$owner/$repo")`、`Log.w(TAG, …)`
     * 这类写法在真实代码里大量存在，一旦误报，守卫立刻失去可用性）。
     */
    @Test
    fun scanner_syntheticSnippets_expectedFindingsOnly() {
        val findings = CopyGuard.scanSource(SYNTHETIC_PATH, SYNTHETIC_SOURCE)
        val actual = findings.map { "${it.line}:${it.value} [${it.rule}]" }.toSet()

        assertEquals(
            "扫描器对人造样例的判定与预期不符（漏检 = 守卫失效；多检 = 误报面失控）",
            SYNTHETIC_EXPECTED,
            actual,
        )
    }

    @Test
    fun exemptions_areNotStale_matchingExistingFindings() {
        val scan = CopyGuard.scan(repoRoot())
        val found =
            scan.findings
                .filter { it.kind != FindingKind.LEAK }
                .map { it.path to it.value }
                .toSet()
        val declared =
            (ALLOWED.map { it.path to it.value } + KNOWN_DEBT.map { it.path to it.value }).toSet()
        val stale = declared - found

        assertTrue(
            "豁免/欠债清单里有 ${stale.size} 条陈旧条目（对应的命中已不存在）。删掉它们，别让清单变成永久盲区：" +
                stale.joinToString(separator = "", prefix = "\n") { "  - ${it.first} → \"${it.second}\"\n" },
            stale.isEmpty(),
        )
    }

    private fun hardcodedCopyMessage(violations: List<Finding>): String =
        buildString {
            append("发现 ${violations.size} 处硬编码用户可见文案（红线：Compose 一律 stringResource()，en + zh-rCN 双份）。\n")
            append("修法：加 <string name=\"…\">（values/ 与 values-zh-rCN/ 成对）后改用 stringResource(R.string.…)。\n")
            append("确属合法的非 UI 文案（技术上无法走资源）：加进 CopyGuard.ALLOWED 并写明理由。\n")
            violations.sortedWith(compareBy({ it.path }, { it.line })).forEach { finding ->
                append("  - ${finding.path}:${finding.line}  [${finding.rule}]  \"${finding.value}\"\n")
            }
        }

    /** 仓库根：单测工作目录是模块目录（`app/`），上跳直到看见 settings.gradle.kts。 */
    private fun repoRoot(): File {
        var dir: File? = File(".").canonicalFile
        repeat(MAX_ROOT_UPWARD_HOPS) {
            val current = dir
            if (current != null && File(current, "settings.gradle.kts").isFile) return current
            dir = current?.parentFile
        }
        error("找不到仓库根（settings.gradle.kts）：从 ${File(".").canonicalPath} 上跳 $MAX_ROOT_UPWARD_HOPS 层均未命中")
    }

    private companion object {
        const val MAX_ROOT_UPWARD_HOPS = 5

        /** 规模下限：当前实测 352 个文件 / 2020 个字面量，取下限以判定「明显失效」。 */
        const val MIN_SCANNED_FILES = 120
        const val MIN_SCANNED_LITERALS = 1000

        /**
         * 人造样例：一个「像真实 UI 文件」的片段，覆盖所有应抓形态 + 一组必须放过的合法写法。
         *
         * 路径刻意取成 composable + `/ui/` 文件，以同时验证「UI 文件内的整句英文常量」这条规则。
         * `${'$'}` 是 Kotlin 原始字符串里书写 `$` 的转义写法（模板语法本身）。
         */
        const val SYNTHETIC_PATH =
            "feature/demo/src/main/kotlin/com/yumiru11/githubapp/feature/demo/ui/DemoScreen.kt"

        val SYNTHETIC_SOURCE =
            """
            package com.yumiru11.githubapp.feature.demo.ui

            import android.util.Log

            private const val TAG = "DemoScreen"
            private const val MERGE_HINT = "Checking mergeability…"
            private const val MAX_LINES = 3

            @Composable
            fun DemoScreen(open: Boolean, count: Int, owner: String, repo: String, url: String) {
                Column {
                    Text("Checking mergeability…")
                    Text("合并状态检查中")
                    Icon(painter = painterResource(R.drawable.close), contentDescription = "Close dialog")
                    SearchField(placeholder = { Text("Search repositories") }, label = "Filter by name")
                    Text(if (open) "Open" else "Closed")
                    Text("${'$'}count results found")
                    scope.launch { snackbarHostState.showSnackbar("Changes saved") }
                    Text(MERGE_HINT)
                    Text(stringResource(R.string.demo_title))
                    Text("${'$'}owner/${'$'}repo")
                    Text("${'$'}{count}")
                    Text("${'$'}MAX_LINES")
                    Log.w(TAG, "failed to load demo payload: ${'$'}url")
                    checkNotNull(owner) { "owner must not be null" }
                    error("demo route requires no arguments")
                    throw IllegalStateException("demo invariant violated")
                    val accept = "application/vnd.github.v3+json"
                    val since = format("yyyy-MM-dd'T'HH:mm:ss", url)
                    val digits = Regex("^[a-z]+-[0-9]+${'$'}")
                    Modifier.testTag("demo-card-${'$'}owner").sharedTransitionElement(key = "repo-avatar-${'$'}owner")
                    Crossfade(targetState = open, label = "demo-crossfade") { }
                    @Suppress("MagicNumber")
                    val maxLines = MAX_LINES
                }
            }

            @Preview(name = "Light")
            @Composable
            private fun DemoScreenPreview() {
                DemoScreen(open = true, count = 3, owner = "octocat", repo = "hello")
                Text("预览样例：合并中")
            }

            /** 历史泄漏形态复刻：UI 状态映射函数里直接返回英文常量（既不在 Text() 里也不是形参）。 */
            private fun mergeStateText(state: String): String =
                when (state) {
                    "conflicting" -> "This branch has conflicts"
                    else -> "Checking mergeability…"
                }
            """

        /**
         * 期望命中（行号相对 [SYNTHETIC_SOURCE]），逐条锁定规则名 —— 改动样例时必须同步改这里，
         * 这也正是「扫描器改动会立刻暴露」的机制。
         *
         * 行号↔规则对应（供人工复核）：
         * - 7  裸英文常量（`const val`，不在 Text() 里、也不是 UI 形参）→ [RULE_UI_FILE_PROSE]
         * - 13 `Text("…")` → [RULE_TEXT]
         * - 14 中文 → [RULE_CJK]（中文优先于位置判定）
         * - 15 `contentDescription = "…"` → [RULE_UI_ARG]
         * - 16 `label = "…"` → [RULE_UI_ARG]（歧义形参 + 多词）
         * - 19 `showSnackbar("…")` → [RULE_SNACKBAR]
         * - 49 / 50 `when` 分支里的英文常量 → [RULE_UI_FILE_PROSE]（**历史泄漏形态复刻**：
         *   PR 详情页裸英文常量 `Checking mergeability...` 就是这么写进去的）
         *
         * 样例里其余写法（`Log.w` / `checkNotNull` 惰性消息 / `error` / `throw …Exception` /
         * MediaType / 日期 pattern / `Regex` / testTag / `sharedTransitionElement(key=…)` /
         * `Crossfade(label = "kebab")` / `@Suppress` / 纯插值模板 / `@Preview` 中文样例 /
         * `when` 分支里的单 token 键名 `"conflicting"`）
         * 都必须**不**出现在结果里 —— 它们由断言的全集比较隐式锁定。
         */
        val SYNTHETIC_EXPECTED =
            setOf(
                "7:Checking mergeability… [$RULE_UI_FILE_PROSE]",
                "13:Checking mergeability… [$RULE_TEXT]",
                "14:合并状态检查中 [$RULE_CJK]",
                "15:Close dialog [$RULE_UI_ARG contentDescription]",
                "16:Search repositories [$RULE_TEXT]",
                "16:Filter by name [$RULE_UI_ARG label]",
                "17:Open [$RULE_TEXT]",
                "17:Closed [$RULE_TEXT]",
                "18:${'$'}count results found [$RULE_TEXT]",
                "19:Changes saved [$RULE_SNACKBAR]",
                "49:This branch has conflicts [$RULE_UI_FILE_PROSE]",
                "50:Checking mergeability… [$RULE_UI_FILE_PROSE]",
            )
    }
}

/** 扫描结论。 */
private data class ScanResult(
    val files: List<String>,
    val literalCount: Int,
    val findings: List<Finding>,
)

private enum class FindingKind { LEAK, ALLOWED, KNOWN_DEBT }

private data class Finding(
    val path: String,
    val line: Int,
    val value: String,
    val rule: String,
    val kind: FindingKind,
)

/** 确认合法的例外：必须写明「为什么它不是用户可见文案」。 */
private data class Allowance(
    val path: String,
    val value: String,
    val reason: String,
)

/** 禁改文件里的既有命中：等并行任务落地后再修（`TODO(#issue)`）。 */
private data class Debt(
    val path: String,
    val value: String,
    val issue: String,
)

private const val RULE_CJK = "硬编码中文"
private const val RULE_TEXT = "Text() 字面量"
private const val RULE_UI_FILE_PROSE = "UI 文件内整句常量"
private const val RULE_SNACKBAR = "Snackbar/Toast 字面量"

/** UI 形参规则名前缀：实际拼成 `UI 形参 contentDescription`。 */
private const val RULE_UI_ARG = "UI 形参"

/** [I18nHardcodedStringGuardTest] 的豁免清单；放在顶层以保持测试类只读。 */
private val ALLOWED: List<Allowance> =
    listOf(
        Allowance(
            path = "core/markdown/src/main/kotlin/com/yumiru11/githubapp/core/markdown/GitHubTextMateTheme.kt",
            value = "M3 Dark fallback",
            reason = "TextMate 语法高亮主题的 name 字段（shiki/TextMate 配置标识，不渲染给用户）",
        ),
        Allowance(
            path = "core/markdown/src/main/kotlin/com/yumiru11/githubapp/core/markdown/GitHubTextMateTheme.kt",
            value = "M3 Light fallback",
            reason = "同上（亮色分支）",
        ),
    )

/**
 * 禁改文件（并行任务在建：feature/pullrequest、feature/search、feature/repo、feature/issue、
 * feature/home、feature/settings、core/ui、core/designsystem、core/editor、MainActivity）里
 * 扫出的既有命中。**当前为空**：干净 main 上经逐条核实无真泄漏；分类明细见
 * `docs/agents/i18n-hardcoded-backlog.md`。
 */
private val KNOWN_DEBT: List<Debt> = emptyList()

/**
 * 源码扫描器。刻意做成纯文本 + 逐字符状态机：不依赖 Kotlin 编译器，快（全仓亚秒级）、无新依赖、
 * 不随 Kotlin 版本漂移——这正是 Android Lint 检测器与本机 LSP 方案不可用的原因（见类 KDoc）。
 */
private object CopyGuard {
    /** 扫描范围：`core/<module>/src/main`、`feature/<module>/src/main`、`app/src/main` 下全部 `.kt`。 */
    private val SCAN_ROOTS = listOf("core", "feature", "app")

    /** 回溯窗口：足够覆盖跨行实参列表，又不至于把上一条语句的调用误认成外层调用。 */
    private const val MAX_LOOKBEHIND = 900

    fun scan(repoRoot: File): ScanResult {
        val files = productionKotlinFiles(repoRoot)
        var literalCount = 0
        val findings = mutableListOf<Finding>()
        files.forEach { file ->
            val relPath = relativePath(repoRoot, file)
            val source = file.readText()
            literalCount += MaskedSource.of(source).literals.size
            findings += resolveKind(scanSource(relPath, source))
        }
        return ScanResult(files.map { relativePath(repoRoot, it) }, literalCount, findings)
    }

    fun scanSource(
        relPath: String,
        text: String,
    ): List<Finding> = findingsFor(relPath, MaskedSource.of(text))

    /** `core/<module>/src/main`、`feature/<module>/src/main`、`app/src/main` 下的全部 `.kt`。 */
    private fun productionKotlinFiles(repoRoot: File): List<File> {
        val moduleDirs =
            SCAN_ROOTS.flatMap { top ->
                val dir = File(repoRoot, top)
                if (top == "app" || !dir.isDirectory) listOf(dir) else dir.listFiles().orEmpty().toList()
            }
        return moduleDirs
            .map { File(it, "src/main") }
            .filter { it.isDirectory }
            .flatMap { root ->
                root
                    .walkTopDown()
                    .onEnter { it.name != "build" }
                    .filter { it.isFile && it.extension == "kt" }
                    .toList()
            }.sortedBy { it.path }
    }

    private fun relativePath(
        repoRoot: File,
        file: File,
    ): String = file.relativeTo(repoRoot).path.replace(File.separatorChar, '/')

    private fun resolveKind(findings: List<Finding>): List<Finding> =
        findings.map { finding ->
            val allowed = ALLOWED.any { it.path == finding.path && it.value == finding.value }
            val debt = KNOWN_DEBT.any { it.path == finding.path && it.value == finding.value }
            when {
                allowed -> finding.copy(kind = FindingKind.ALLOWED)
                debt -> finding.copy(kind = FindingKind.KNOWN_DEBT)
                else -> finding
            }
        }

    // ── 逐字面量判定 ──────────────────────────────────────────────────────────────────

    private fun findingsFor(
        path: String,
        source: MaskedSource,
    ): List<Finding> {
        val previewLines = previewFunctionLines(source.masked)
        val isUiFile = source.masked.contains(COMPOSABLE_ANNOTATION) || path.contains("/ui/")
        return source.literals.mapNotNull { literal ->
            ruleFor(path, source.masked, literal, previewLines, isUiFile)?.let { rule ->
                Finding(path, literal.line, literal.value, rule, FindingKind.LEAK)
            }
        }
    }

    private fun ruleFor(
        path: String,
        masked: String,
        literal: MaskedLiteral,
        previewLines: Set<Int>,
        isUiFile: Boolean,
    ): String? {
        val plain = staticText(literal.value)
        val call = enclosingCall(masked, literal.start)?.substringAfterLast('.')
        return when {
            isExempt(literal, plain, path, masked, previewLines) -> {
                null
            }

            HAS_CJK.containsMatchIn(plain) -> {
                RULE_CJK
            }

            call == "Text" || call == "BasicText" || call == "AnnotatedString" -> {
                RULE_TEXT
            }

            call == "showSnackbar" || call == "makeText" -> {
                RULE_SNACKBAR
            }

            else -> {
                namedArgumentRule(masked, literal.start, plain)
                    ?: RULE_UI_FILE_PROSE.takeIf { isUiFile && looksLikeNaturalLanguage(plain) }
            }
        }
    }

    /**
     * 歧义形参（`label`/`title`/`text`/`message`…）：只有值**是多词自然语言**才算文案。
     * 这一条挡掉大量误报：`Crossfade(label = "search-results")`、
     * `animateDpAsState(label = "cardGroupTopCorner")` 都是动画调试标签，不是文案。
     */
    private fun namedArgumentRule(
        masked: String,
        start: Int,
        plain: String,
    ): String? {
        val name =
            NAMED_ARG_BEFORE
                .find(masked.substring(maxOf(0, start - MAX_ARG_LOOKBEHIND), start))
                ?.groupValues
                ?.get(1)
        val call = enclosingCall(masked, start)
        return when {
            name == null -> {
                null
            }

            name in UNAMBIGUOUS_UI_ARGS -> {
                "$RULE_UI_ARG $name"
            }

            name in AMBIGUOUS_UI_ARGS && plain.contains(' ') && call?.firstOrNull()?.isUpperCase() == true -> {
                "$RULE_UI_ARG $name"
            }

            else -> {
                null
            }
        }
    }

    /** 「不是用户可见文案」的全部豁免口径，集中一处便于审计。 */
    private fun isExempt(
        literal: MaskedLiteral,
        plain: String,
        path: String,
        masked: String,
        previewLines: Set<Int>,
    ): Boolean {
        val trimmed = plain.trim()
        val hasCjk = HAS_CJK.containsMatchIn(plain)
        val before = masked.substring(maxOf(0, literal.start - MAX_ARG_LOOKBEHIND), literal.start)
        return when {
            // 没有任何字母/汉字：纯数字、纯符号，以及「模板里没有静态文字」的插值（"$owner/$repo"）
            !plain.any { it.isLetter() || isCjk(it) } -> true

            trimmed.isEmpty() -> true

            !hasCjk && trimmed.length < 2 -> true

            FORMAT_ONLY.matches(trimmed) -> true

            // dev-only 夹具：@Preview 函数体 / 文件名含 preview（已知盲区，见类 KDoc 与 backlog 文档）
            literal.line in previewLines -> true

            PREVIEW_PATH.containsMatchIn(path) -> true

            isNonUiSinkCall(enclosingCall(masked, literal.start)) -> true

            isAnnotationArgument(masked, literal.start) -> true

            THROW_BEFORE.containsMatchIn(before) -> true

            isTechnicalIdentifier(trimmed) -> true

            else -> false
        }
    }

    /** 日志 / 异常 / 断言消息：合法非 UI 字符串，不参与翻译。 */
    private fun isNonUiSinkCall(call: String?): Boolean {
        val tail = call?.substringAfterLast('.') ?: return false
        return call.matches(LOG_CALL) ||
            tail in NON_UI_SINK_TAILS ||
            EXCEPTION_CTOR.matches(tail) ||
            call.matches(EXCEPTION_CTOR)
    }

    /** `@Suppress("…")` / `@Query("…")` / `@SerialName("…")`：注解实参，不可能是 UI 文案。 */
    private fun isAnnotationArgument(
        masked: String,
        start: Int,
    ): Boolean {
        val parenIndex = masked.lastIndexOf('(', start)
        if (parenIndex < 0) return false
        var cursor = parenIndex - 1
        while (cursor >= 0 && isIdentifierChar(masked[cursor])) cursor--
        while (cursor >= 0 && masked[cursor].isWhitespace()) cursor--
        return cursor >= 0 && masked[cursor] == '@'
    }

    /** 技术标识（URL / MediaType / 日期 pattern / kebab 键名…）：任务口径明确豁免。 */
    private fun isTechnicalIdentifier(value: String): Boolean =
        value.contains("://") ||
            MIME_TYPE.matches(value) ||
            HEX_COLOR.matches(value) ||
            IDENTIFIER_LIKE.matches(value) ||
            (DATE_TIME_PATTERN.matches(value) && DATE_TIME_FIELD.containsMatchIn(value))

    /** UI 文件里的整句英文常量：≥2 个词、含空格、且不含代码结构字符。 */
    private fun looksLikeNaturalLanguage(plain: String): Boolean {
        val value = plain.trim()
        return when {
            !value.contains(' ') -> false
            value.any { it in CODE_CHARS } -> false
            value.contains('_') -> false
            DOTTED_IDENTIFIER.containsMatchIn(value) -> false
            WORD.findAll(value).count() < 2 -> false
            else -> LOWER_WORD.containsMatchIn(value)
        }
    }

    // ── 词法层：注释与字面量抹成空格（长度不变 → 下标即原文下标）────────────────────

    private class MaskedSource private constructor(
        val masked: String,
        val literals: List<MaskedLiteral>,
    ) {
        companion object {
            fun of(text: String): MaskedSource {
                val buffer = text.toCharArray()
                val literals = mutableListOf<MaskedLiteral>()
                var index = 0
                var line = 1
                while (index < text.length) {
                    val token = scanOne(text, buffer, index, line)
                    when {
                        token != null -> {
                            line = token.nextLine
                            token.literal?.let { literals += it }
                            index = token.next
                        }

                        text[index] == '\n' -> {
                            line++
                            index++
                        }

                        else -> {
                            index++
                        }
                    }
                }
                return MaskedSource(String(buffer), literals)
            }

            /** 抹掉从 [index] 开始的一个注释 / 字符串 / 字符字面量；返回下一位置与新行号。 */
            private fun scanOne(
                text: String,
                buffer: CharArray,
                index: Int,
                line: Int,
            ): ScannedToken? =
                when {
                    text.startsWith("//", index) -> lineComment(text, buffer, index, line)
                    text.startsWith("/*", index) -> blockComment(text, buffer, index, line)
                    text[index] == '"' -> stringLiteral(text, buffer, index, line)
                    text[index] == '\'' -> charLiteral(text, buffer, index, line)
                    else -> null
                }

            private fun lineComment(
                text: String,
                buffer: CharArray,
                index: Int,
                line: Int,
            ): ScannedToken {
                val found = text.indexOf('\n', index)
                val end = if (found < 0) text.length else found
                blank(buffer, text, index, end)
                return ScannedToken(end, line)
            }

            private fun blockComment(
                text: String,
                buffer: CharArray,
                index: Int,
                line: Int,
            ): ScannedToken {
                var cursor = index + 2
                while (cursor + 1 < text.length && !text.startsWith("*/", cursor)) cursor++
                val end = minOf(cursor + 2, text.length)
                blank(buffer, text, index, end)
                return ScannedToken(end, line + newlines(text, index, end))
            }

            private fun stringLiteral(
                text: String,
                buffer: CharArray,
                index: Int,
                line: Int,
            ): ScannedToken {
                val raw = text.startsWith(TRIPLE_QUOTE, index)
                val quote = if (raw) TRIPLE_QUOTE else "\""
                var cursor = index + quote.length
                val content = StringBuilder()
                while (cursor < text.length && !text.startsWith(quote, cursor)) {
                    val char = text[cursor]
                    if (!raw && char == '\\' && cursor + 1 < text.length) {
                        content.append(char).append(text[cursor + 1])
                        cursor += 2
                    } else {
                        if (char == '\n' && !raw) break
                        content.append(char)
                        cursor++
                    }
                }
                val end = if (text.startsWith(quote, cursor)) cursor + quote.length else cursor
                blank(buffer, text, index, end)
                return ScannedToken(end, line + newlines(text, index, end), MaskedLiteral(content.toString(), index, line))
            }

            private fun charLiteral(
                text: String,
                buffer: CharArray,
                index: Int,
                line: Int,
            ): ScannedToken {
                var cursor = index + 1
                while (cursor < text.length && text[cursor] != '\'') {
                    if (text[cursor] == '\\') cursor++
                    cursor++
                }
                val end = minOf(cursor + 1, text.length)
                blank(buffer, text, index, end)
                return ScannedToken(end, line + newlines(text, index, end))
            }

            private fun newlines(
                text: String,
                from: Int,
                to: Int,
            ): Int {
                var count = 0
                for (i in from until minOf(to, text.length)) if (text[i] == '\n') count++
                return count
            }

            /** 抹成空格但保留换行符（行号仍可按 `\n` 计数）。 */
            private fun blank(
                buffer: CharArray,
                text: String,
                from: Int,
                to: Int,
            ) {
                for (i in from until minOf(to, text.length)) if (text[i] != '\n') buffer[i] = ' '
            }

            private const val TRIPLE_QUOTE = "\"\"\""
        }
    }

    private class ScannedToken(
        val next: Int,
        val nextLine: Int,
        val literal: MaskedLiteral? = null,
    )

    private data class MaskedLiteral(
        val value: String,
        val start: Int,
        val line: Int,
    )

    // ── 位置判定：从字面量向左找「最内层包住它的调用」────────────────────────────────

    /**
     * 向左回溯找最内层调用名（含 `.`，如 `Log.w`、`snackbarHostState.showSnackbar`）；找不到返回 null。
     *
     * 括号与花括号同时计深度，四条边界规则都是为了既不漏检、也不乱认「外层调用」
     * （前者会漏掉真泄漏，后者会把 `const val DEFAULT_REF = "HEAD"` 误报成 Text 字面量）：
     *
     * 1. **块边界即停**：向左遇到「深度 0 上未配对的 `{`」说明已经走出字面量所在的最内层块
     *    （`when (s) { … }`、`fun f() { … }`），外面的调用与它无关 → 返回 null。
     * 2. **块内的候选一律作废**：跨进上一个块（`}` 使 braceDepth > 0）后遇到的 `Text(...)`
     *    属于别的语句，不能认领。
     * 3. **尾随 lambda 例外**：紧跟 `)` 之后的 `{`（`require(x) { "msg" }` 形态）继续向左认领
     *    `x)` 对应的调用名；若该名字是控制流关键字（`when (s) { … }` / `if (x) { … }`）则认领失败。
     * 4. **控制流关键字透明**：`Text(if (open) "Open" else "Closed")` 里最内层括号是 `if (`，
     *    跳过它继续向外找才是真正的渲染调用 `Text`。
     *
     * detekt 豁免（T3 先例）：本函数是手写的「双深度 + 尾随 lambda 标记」向左回溯状态机，
     * 把状态拆进子函数只会让 4 个状态变量在参数表里来回传递，可读性与可审计性反而更差；
     * 其行为已由 `scanner_syntheticSnippets_expectedFindingsOnly` 的正/反向用例逐条锁定。
     */
    @Suppress("CyclomaticComplexMethod", "NestedBlockDepth", "ReturnCount")
    private fun enclosingCall(
        masked: String,
        start: Int,
    ): String? {
        var parenDepth = 0
        var braceDepth = 0
        var lambdaPending = false
        var index = start - 1
        val limit = maxOf(0, start - MAX_LOOKBEHIND)
        while (index >= limit) {
            when (masked[index]) {
                ')' -> {
                    parenDepth++
                }

                '(' -> {
                    if (parenDepth > 0) {
                        parenDepth--
                        if (parenDepth == 0 && braceDepth == 0 && lambdaPending) {
                            return nonControlFlowCall(masked, index)
                        }
                    } else if (braceDepth == 0) {
                        val name = identifierEndingAt(masked, index - 1) ?: return null
                        if (name.substringAfterLast('.') !in CONTROL_FLOW_KEYWORDS) return name
                    }
                }

                '}' -> {
                    braceDepth++
                }

                '{' -> {
                    when {
                        braceDepth > 0 -> braceDepth--
                        isTrailingLambdaBrace(masked, index) -> lambdaPending = true
                        else -> return null
                    }
                }

                ';' -> {
                    if (parenDepth == 0 && braceDepth == 0) return null
                }
            }
            index--
        }
        return null
    }

    /** 尾随 lambda 的调用名；控制流关键字（`if (x) { … }` / `when (s) { … }`）视为认领失败。 */
    private fun nonControlFlowCall(
        masked: String,
        parenIndex: Int,
    ): String? {
        val name = identifierEndingAt(masked, parenIndex - 1) ?: return null
        return name.takeIf { it.substringAfterLast('.') !in CONTROL_FLOW_KEYWORDS }
    }

    /** `{` 是否紧跟在 `)` 之后（尾随 lambda 形态，如 `require(x) { "msg" }`）。 */
    private fun isTrailingLambdaBrace(
        masked: String,
        braceIndex: Int,
    ): Boolean {
        var cursor = braceIndex - 1
        while (cursor >= 0 && masked[cursor].isWhitespace()) cursor--
        return cursor >= 0 && masked[cursor] == ')'
    }

    /** 取 [end] 位置结尾的（可含 `.` 的）调用名，如 `Log.w`、`snackbarHostState.showSnackbar`。 */
    private fun identifierEndingAt(
        masked: String,
        end: Int,
    ): String? {
        var cursor = end
        while (cursor >= 0 && masked[cursor].isWhitespace()) cursor--
        val last = cursor
        while (cursor >= 0 && isIdentifierChar(masked[cursor])) cursor--
        if (last < 0 || cursor == last) return null
        return masked.substring(cursor + 1, last + 1)
    }

    /** Kotlin 标识符字符（调用名允许带 `.`，如 `Log.w`）。 */
    private fun isIdentifierChar(char: Char): Boolean = char.isLetterOrDigit() || char in IDENTIFIER_EXTRA_CHARS

    // ── @Preview 夹具定位 ────────────────────────────────────────────────────────────

    /** `@Preview` 标注函数体的行号集合（dev-only 夹具豁免）。 */
    private fun previewFunctionLines(masked: String): Set<Int> {
        val covered = mutableSetOf<Int>()
        var at = masked.indexOf(PREVIEW_ANNOTATION)
        while (at >= 0) {
            previewBodyRange(masked, at)?.let { body -> covered += lineOf(masked, at)..lineOf(masked, body) }
            at = masked.indexOf(PREVIEW_ANNOTATION, at + 1)
        }
        return covered
    }

    /** `@Preview` → 其后第一个 `fun` 的函数体字符区间（配对花括号）；结构异常返回 null。 */
    private fun previewBodyRange(
        masked: String,
        at: Int,
    ): Int? {
        val funAt = masked.indexOf("fun ", at)
        if (funAt < 0 || funAt - at > MAX_ANNOTATION_GAP) return null
        val braceAt = masked.indexOf('{', funAt)
        if (braceAt < 0) return null
        var depth = 0
        var cursor = braceAt
        while (cursor < masked.length) {
            when (masked[cursor]) {
                '{' -> {
                    depth++
                }

                '}' -> {
                    depth--
                    if (depth == 0) return cursor
                }
            }
            cursor++
        }
        return null
    }

    private fun lineOf(
        masked: String,
        index: Int,
    ): Int {
        var line = 1
        for (i in 0 until minOf(index, masked.length)) if (masked[i] == '\n') line++
        return line
    }

    // ── 字面量内容归一化 ─────────────────────────────────────────────────────────────

    /**
     * 取字面量的**静态文字**：`${expr}` / `$ident` 换成非空格占位符 `\u0001`，
     * 于是 `"$owner/$repo"` 归一化后不含词，而 `"$count results"` 仍保留 "results"。
     */
    private fun staticText(raw: String): String =
        raw
            .replace(BRACED_TEMPLATE, INTERPOLATION)
            .replace(SIMPLE_TEMPLATE, INTERPOLATION)
            .replace("\\n", " ")
            .replace("\\t", " ")
            .replace(ESCAPE_SEQUENCE, "")

    private fun isCjk(char: Char): Boolean =
        char.code in 0x3000..0x303F ||
            char.code in 0x3040..0x30FF ||
            char.code in 0x3400..0x4DBF ||
            char.code in 0x4E00..0x9FFF ||
            char.code in 0xF900..0xFAFF ||
            char.code in 0xFF00..0xFFEF

    private const val INTERPOLATION = "\u0001"
    private const val COMPOSABLE_ANNOTATION = "@Composable"
    private const val PREVIEW_ANNOTATION = "@Preview"
    private const val IDENTIFIER_EXTRA_CHARS = "_."
    private const val MAX_ARG_LOOKBEHIND = 120
    private const val MAX_ANNOTATION_GAP = 400

    private val HAS_CJK =
        Regex("[\\u3000-\\u303f\\u3040-\\u30ff\\u3400-\\u4dbf\\u4e00-\\u9fff\\uf900-\\ufaff\\uff00-\\uffef]")
    private val BRACED_TEMPLATE = Regex("\\$\\{[^}]*}")
    private val SIMPLE_TEMPLATE = Regex("\\$[A-Za-z_][A-Za-z0-9_]*")
    private val ESCAPE_SEQUENCE = Regex("\\\\[u\"'\\\\$]")
    private val WORD = Regex("[A-Za-z]{2,}")
    private val LOWER_WORD = Regex("[a-z]{3,}")
    private val DOTTED_IDENTIFIER = Regex("[A-Za-z]\\.[A-Za-z]")
    private val NAMED_ARG_BEFORE = Regex("([A-Za-z_][A-Za-z0-9_]*)\\s*=\\s*$")
    private val PREVIEW_PATH = Regex("preview", RegexOption.IGNORE_CASE)
    private val LOG_CALL = Regex("^(android\\.util\\.)?(Log|Timber|Logger)\\.\\w+$")
    private val EXCEPTION_CTOR = Regex("^[A-Z]\\w*(Exception|Error)$")
    private val THROW_BEFORE = Regex("\\bthrow\\s+$")
    private val MIME_TYPE = Regex("^[\\w.+-]+/[\\w.+-]+$")
    private val HEX_COLOR = Regex("^#[0-9A-Fa-f]{3,8}$")
    private val IDENTIFIER_LIKE = Regex("^[a-z0-9]+(?:[-.:][a-z0-9]+)+$")
    private val FORMAT_ONLY = Regex("^[%\\d$.,;:!?\\-_/\\\\|+*=<>()\\[\\]{} \\t\\n#@&~^\\u0001]*$")
    private val DATE_TIME_PATTERN =
        Regex(
            "^[^A-Za-z]*(?:(?:y+|M+|d+|H+|h+|m+|s+|S+|a+|E+|z+|Z+|k+|K+|G+|w+|W+|D+|F+|u+|X+|x+|Q+|V+|O+|n+|N+|P+|p+|L+|C+|e+|c+|t+)[^A-Za-z]*)+$",
        )
    private val DATE_TIME_FIELD = Regex("(y{2,}|M{2,}|d{2,}|H{2,}|m{2,}|s{2,})")

    /** 代码结构字符：出现即认为不是自然语言（`"\"editor.background\": \""` 这类拼接产物）。 */
    private val CODE_CHARS =
        setOf('"', '{', '}', '[', ']', '<', '>', '=', '_', '\\', '|', ';', '#', '@', '$', '~', '\u0001')

    private val CONTROL_FLOW_KEYWORDS =
        setOf("if", "else", "for", "while", "when", "catch", "return", "do", "is", "in")

    private val NON_UI_SINK_TAILS =
        setOf(
            "println",
            "print",
            "error",
            "require",
            "check",
            "requireNotNull",
            "checkNotNull",
            "Regex",
            "toRegex",
            "Query",
            "Insert",
            "Update",
            "Delete",
            "SerialName",
            "Suppress",
        )

    private val UNAMBIGUOUS_UI_ARGS =
        setOf(
            "contentDescription",
            "stateDescription",
            "paneTitle",
            "supportingText",
            "placeholder",
            "headline",
            "emptyText",
            "emptyMessage",
            "errorText",
            "errorMessage",
            "actionLabel",
            "confirmText",
            "dismissText",
            "tooltipText",
            "badgeText",
            "titleText",
            "subtitleText",
        )

    private val AMBIGUOUS_UI_ARGS =
        setOf(
            "label",
            "title",
            "subtitle",
            "text",
            "message",
            "description",
            "hint",
            "summary",
            "caption",
            "header",
            "footer",
            "snippet",
        )
}
