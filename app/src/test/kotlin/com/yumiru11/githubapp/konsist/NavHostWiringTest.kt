package com.yumiru11.githubapp.konsist

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 导航接线守卫（P0 白屏缺陷回归门禁）。
 *
 * 为什么需要它：[AppNavHost] 把每个 destination 的屏幕做成「宿主注入的 screen lambda」，
 * 且每个 lambda 都带 `= {}` 形式的**默认空实现**。这条设计让 core:ui 不依赖各 feature
 * （分层正确），但也引入一个静默失效模式：宿主漏传某个 lambda **不会编译报错、不会崩溃**，
 * 只是该 destination 渲染 `Unit` —— 用户点进去就是一片白屏。
 *
 * 实证：T23 的 [branchesScreen] / [createPullRequestScreen]（分屏入口 MainActivity 的
 * 分支 Chip、创建 PR 入口 PR 列表顶栏「新建」）就这样漏传，两张共 650 行的已交付屏幕
 * 对用户完全不可达。Screen 组件自身的测试全绿（测的是组件），没有任何测试看「装配」。
 *
 * 判定口径（源码扫描，不需要 classpath 反射——Konsist/编译产物都看不出「漏传」）：
 * 1. 读 [AppNavHost.kt]，提取所有**带默认空实现**的 screen lambda 参数名
 *    （形态：`name: @Composable (...) -> Unit = {}` / `= { _, _, _ -> }`）
 * 2. 读 [MainActivity.kt] 的 `AppNavHost(...)` 调用点，检查每个名字是否被显式传入
 *    （形态：`name = ` 后紧跟 lambda `{`）
 * 3. 断言没有遗漏 —— 遗漏即「用户点进去白屏」
 *
 * 反向自检：解析结果为空（例如 AppNavHost 挪了位置、签名改形）时本测试**必须失败**，
 * 否则就会退化成「看起来在跑其实什么都没查」的门禁（本项目已有此教训）。
 *
 * 测试命名规范：methodName_scenario_expectedBehavior。
 */
class NavHostWiringTest {
    @Test
    fun appNavHostScreenLambdas_missingFromMainActivityCallSite_isEmpty() {
        val hostSource = sourceText(APP_NAV_HOST_PATH)
        val screenLambdas = screenLambdaParameters(hostSource)
        // 反向自检 1：解析器必须真的取到 lambda 参数（取 0 个 = 守卫空转，比漏接线更危险）
        assertTrue(
            "未能从 $APP_NAV_HOST_PATH 解析出任何 screen lambda 参数（预期 ≥ $MIN_EXPECTED_SCREEN_LAMBDAS 个）" +
                "——签名形态已变，请同步更新本守卫的解析逻辑",
            screenLambdas.size >= MIN_EXPECTED_SCREEN_LAMBDAS,
        )
        // 反向自检 2：T23 这两个 lambda 的签名形态必须仍被识别为「带默认空实现」。
        // 若解析器认不出它们，本守卫恰好会漏掉它们要防的那类缺陷（白屏）而恒绿。
        assertTrue(
            "解析器未把 $KNOWN_MISSING_CASE_LAMBDAS 识别为带默认空实现的 screen lambda——解析逻辑已失效",
            screenLambdas.containsAll(KNOWN_MISSING_CASE_LAMBDAS),
        )

        val callSite = callArgumentsSection(neutralize(sourceText(MAIN_ACTIVITY_PATH)), HOST_FUNCTION_NAME)
        assertTrue("在 $MAIN_ACTIVITY_PATH 找不到 $HOST_FUNCTION_NAME(...) 调用点", callSite != null)
        // 反向自检 3：取到的必须是**真调用点**的实参区（含已接线实参），而不是空的/别名区域
        assertTrue(
            "取到的 $HOST_FUNCTION_NAME 实参区不含 ${HOME_SCREEN_PARAMETER}，疑似定位失败——本守卫将失去意义",
            wiredArgumentPattern(HOME_SCREEN_PARAMETER).containsMatchIn(callSite.orEmpty()),
        )

        val missing = screenLambdas.filterNot { wiredArgumentPattern(it).containsMatchIn(callSite.orEmpty()) }
        assertTrue(
            "$HOST_FUNCTION_NAME 的以下 screen lambda 没有在 $MAIN_ACTIVITY_PATH 调用点接线，" +
                "它们会走默认空实现 → 用户进入对应页面看到白屏：\n" +
                missing.joinToString(separator = "") { "  - $it\n" },
            missing.isEmpty(),
        )
    }

    /**
     * 提取 [AppNavHost] 函数签名中**带默认空实现**的 screen lambda 参数名。
     *
     * 做法：按「顶层逗号」切出每个形参的完整声明文本，再判它的默认值是不是一个空 lambda。
     * 顶层 = **圆括号与花括号深度都为 0**：圆括号深度挡住 lambda 类型 `(a, b) -> Unit` 里的
     * 逗号，花括号深度挡住默认值 `{ _, _, _ -> }` 形参表里的逗号。
     *
     * ⚠️ 已踩过的坑（每一个都让守卫静默变绿），改这里前务必先跑红/绿双向验证：
     * 1. 用 `substringAfter('=')` 判默认值 —— 形如 `onCreated: (owner, repo, n) -> Unit` 的
     *    **参数类型本身含 `->`**，切出来的根本不是默认值；
     * 2. 自分隔符扫描时把 `(`/`{` 计入深度却忘了回填字符 —— 形参退化成
     *    `homeScreen: @Composable -> Unit =`，空实现判定必然失配；
     * 3. 消费完一个形参后不前进游标 —— 形参**类型里**的嵌套名字（`(initialQuery: String)`
     *    里的 initialQuery）会被当成顶层形参重复匹配，结果被污染。
     */
    private fun screenLambdaParameters(hostSource: String): List<String> {
        val signature = declarationSection(neutralize(hostSource), HOST_FUNCTION_NAME) ?: return emptyList()
        return parameterDeclarations(signature)
            .mapNotNull { declaration ->
                val name = NAME_PREFIX.find(declaration)?.groupValues?.get(1) ?: return@mapNotNull null
                if (hasEmptyLambdaDefault(declaration)) name else null
            }
    }

    /** 顶层逗号切分出的形参声明文本（保留原样，不做字符丢弃）。 */
    private fun parameterDeclarations(signature: String): List<String> {
        val declarations = mutableListOf<String>()
        val current = StringBuilder()
        var roundDepth = 0
        var braceDepth = 0
        signature.forEach { character ->
            when (character) {
                '(' -> {
                    roundDepth++
                    current.append(character)
                }

                ')' -> {
                    roundDepth--
                    current.append(character)
                }

                '{' -> {
                    braceDepth++
                    current.append(character)
                }

                '}' -> {
                    braceDepth--
                    current.append(character)
                }

                ',' -> {
                    if (roundDepth == 0 && braceDepth == 0) {
                        declarations += current.toString()
                        current.clear()
                    } else {
                        current.append(character)
                    }
                }

                else -> {
                    current.append(character)
                }
            }
        }
        declarations += current.toString()
        return declarations.map { it.trim() }.filter { it.isNotEmpty() }
    }

    /** 函数声明的参数列表文本：从 `fun AppNavHost(` 起至配对的 `)`。 */
    private fun declarationSection(
        text: String,
        functionName: String,
    ): String? {
        val declaration = text.indexOf("fun $functionName(")
        if (declaration < 0) return null
        return balancedRegion(text, declaration + "fun $functionName".length)
    }

    /** 函数调用实参区文本：首个 `AppNavHost(` 起至配对的 `)`（`fun AppNavHost(` 声明处不含 `.precededBy`，同样配平）。 */
    private fun callArgumentsSection(
        text: String,
        functionName: String,
    ): String? {
        val call = text.indexOf("$functionName(")
        if (call < 0) return null
        return balancedRegion(text, call + functionName.length)
    }

    /** `(` 起、符号平衡的括号区内容（不含两端括号）；未找到返回 null。 */
    private fun balancedRegion(
        text: String,
        openIndex: Int,
    ): String? {
        if (openIndex < 0 || openIndex >= text.length || text[openIndex] != '(') return null
        var depth = 0
        for (index in openIndex until text.length) {
            when (text[index]) {
                '(' -> {
                    depth++
                }

                ')' -> {
                    depth--
                    if (depth == 0) return text.substring(openIndex + 1, index)
                }

                else -> {
                    Unit
                }
            }
        }
        return null
    }

    /**
     * 参数默认值是否为**平衡的空 lambda**（`{}` 或 `{ _, _ -> }` 这类渲染 `Unit` 的实现）。
     *
     * 定位方式：从参数的第一个 `= {` 起取配平区域（不是 `substringAfter('=')` —— 那样会被
     * 形如 `onCreated: (a, b, c) -> Unit` 的**参数类型里的等号语义**干扰），再看区域内容是否
     * 除空白/下划线/箭头外为空。
     */
    private fun hasEmptyLambdaDefault(parameter: String): Boolean {
        val defaultStart = parameter.indexOf("= {")
        if (defaultStart < 0) return false
        val body = balancedBraces(parameter.substring(defaultStart + "= ".length)) ?: return false
        return EMPTY_LAMBDA_BODY.matches(body)
    }

    /** `{` 起、花括号平衡的区域（含两端花括号）。 */
    private fun balancedBraces(text: String): String? {
        val open = text.indexOf('{')
        if (open < 0) return null
        var depth = 0
        for (index in open until text.length) {
            when (text[index]) {
                '{' -> {
                    depth++
                }

                '}' -> {
                    depth--
                    if (depth == 0) return text.substring(open, index + 1)
                }

                else -> {
                    Unit
                }
            }
        }
        return null
    }

    /**
     * 中性化源码：把注释与字符串字面量内容替换为等长空白（保留换行），其余字符 **原位保留**。
     *
     * 为什么必须先做：所有后续判定都是「文本里有没有 `name = {`」这类形态匹配，注释里的
     * 代码片段会被误判成「已接线」。实证——把 branchesScreen 整段注释掉后，旧实现仍报
     * `wired(branchesScreen) = true`（命中的正是注释里的那行），守卫静默变绿。等长替换保证
     * 下标语义与原文一致。
     */
    private fun neutralize(source: String): String {
        val result = StringBuilder(source)
        var index = 0
        while (index < source.length) {
            val character = source[index]
            when {
                source.startsWith(LINE_COMMENT, index) -> {
                    val end = lineEnd(source, index)
                    blank(result, index, end)
                    index = end
                }

                source.startsWith(BLOCK_COMMENT, index) -> {
                    val end = source.indexOf(BLOCK_COMMENT_END, index + BLOCK_COMMENT.length)
                    val stop = if (end < 0) source.length else end + BLOCK_COMMENT_END.length
                    blank(result, index, stop)
                    index = stop
                }

                character == '"' && source.startsWith(TRIPLE_QUOTE, index) -> {
                    val end = source.indexOf(TRIPLE_QUOTE, index + TRIPLE_QUOTE.length)
                    val stop = if (end < 0) source.length else end + TRIPLE_QUOTE.length
                    blank(result, index, stop)
                    index = stop
                }

                character == '"' -> {
                    index = blankLiteral(source, result, index, '"')
                }

                character == '\'' -> {
                    index = blankLiteral(source, result, index, '\'')
                }

                else -> {
                    index++
                }
            }
        }
        return result.toString()
    }

    /** 注释结束下标（不含换行本身，便于保留行结构）。 */
    private fun lineEnd(
        source: String,
        index: Int,
    ): Int = source.indexOf('\n', index).let { if (it < 0) source.length else it }

    /** 单行字面量：返回结束后的下标，内容抹空。 */
    private fun blankLiteral(
        source: String,
        result: StringBuilder,
        start: Int,
        quote: Char,
    ): Int {
        var cursor = start + 1
        while (cursor < source.length) {
            when (source[cursor]) {
                '\\' -> {
                    cursor += 2
                }

                quote -> {
                    blank(result, start, cursor + 1)
                    return cursor + 1
                }

                '\n' -> {
                    blank(result, start, cursor)
                    return cursor
                }

                else -> {
                    cursor++
                }
            }
        }
        blank(result, start, source.length)
        return source.length
    }

    private fun blank(
        result: StringBuilder,
        from: Int,
        until: Int,
    ) {
        for (index in from until minOf(until, result.length)) {
            if (result[index] != '\n') result[index] = ' '
        }
    }

    private fun sourceText(relativePath: String): String {
        val file = File(repoRoot(), relativePath)
        assertTrue("找不到源码文件：${file.path}", file.isFile)
        return file.readText()
    }

    /**
     * 仓库根目录。优先沿用同目录 I18nParityTest 的口径（测试工作目录 = 模块目录，上跳一级）；
     * 但显式校验根目录标记 —— 解析失败必须让本测试失败，而不是静默扫空目录后恒绿。
     */
    private fun repoRoot(): File {
        val candidates =
            listOfNotNull(
                runCatching { File(".").canonicalFile.parentFile }.getOrNull(),
                runCatching { File(".").canonicalFile }.getOrNull(),
            )
        return candidates.firstOrNull { File(it, MARKER_FILE).isFile }
            ?: error("无法定位仓库根目录（候选：${candidates.joinToString { it.path }}），缺少标记文件 $MARKER_FILE")
    }

    private companion object {
        const val HOST_FUNCTION_NAME = "AppNavHost"
        const val APP_NAV_HOST_PATH = "core/ui/src/main/kotlin/com/yumiru11/githubapp/core/ui/AppNavHost.kt"
        const val MAIN_ACTIVITY_PATH = "app/src/main/java/com/yumiru11/githubapp/MainActivity.kt"
        const val MARKER_FILE = "settings.gradle.kts"

        /**
         * 当前 AppNavHost 恰好有 19 个带默认空实现的 screen lambda（21 个形参减去
         * navController / modifier / startDestination 三个装配参数）。低于此数即解析器失效。
         */
        const val MIN_EXPECTED_SCREEN_LAMBDAS = 19

        /** T23 白屏实证案例：解析器必须能把它们的签名形态判为「带默认空实现」。 */
        val KNOWN_MISSING_CASE_LAMBDAS = listOf("branchesScreen", "createPullRequestScreen")

        /** 用于反查「取到的是真调用点实参区」的必然已接线参数。 */
        const val HOME_SCREEN_PARAMETER = "homeScreen"

        const val TRIPLE_QUOTE = "\"\"\""
        const val LINE_COMMENT = "//"
        const val BLOCK_COMMENT = "/*"
        const val BLOCK_COMMENT_END = "*/"

        /** 形参名：声明文本开头处的标识符。 */
        val NAME_PREFIX = Regex("""^([A-Za-z_][A-Za-z0-9_]*)\s*:""")

        /** 空实现 lambda：`{}` 或 `{ _, _ -> }`（形参全是下划线/占位、无任何语句体）。 */
        val EMPTY_LAMBDA_BODY = Regex("""^\{[\s_>,\-]*(?:->)?[\s_>,\-]*}$""")

        /** 调用点里的显式接线：`name = {` 或 `name={`。 */
        const val WIRED_ARGUMENT_TEMPLATE = """(^|[\s,(])%s\s*=\s*\{"""

        fun wiredArgumentPattern(parameter: String): Regex = Regex(WIRED_ARGUMENT_TEMPLATE.format(Regex.escape(parameter)))
    }
}
