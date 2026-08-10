package com.yumiru11.githubapp.prototype.md

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.textmate.grammar.Grammar
import dev.textmate.grammar.raw.GrammarReader
import dev.textmate.regex.JoniOnigLib
import dev.textmate.theme.Theme
import dev.textmate.theme.ThemeReader
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * 实证 dump：语法 token → scope 栈 → 主题最终颜色。
 * 用于研究"不同关键词的具体颜色来源"（scope 命名 → Dark+/Light+ 规则 → 色值）。
 */
@RunWith(RobolectricTestRunner::class)
class TokenDumpTest {

    private fun loadGrammar(name: String): Grammar {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val raw = context.assets.open("grammars/$name.tmLanguage.json").use { GrammarReader.readGrammar(it) }
        return Grammar(raw.scopeName, raw, JoniOnigLib())
    }

    private fun loadTheme(): Theme {
        val context = ApplicationProvider.getApplicationContext<Context>()
        return context.assets.open("themes/dark_vs.json").use { b ->
            context.assets.open("themes/dark_plus.json").use { p ->
                ThemeReader.readTheme(b, p)
            }
        }
    }

    private fun dump(language: String, code: String) {
        val grammar = loadGrammar(language)
        val theme = loadTheme()
        println("========== $language ==========")
        var state: dev.textmate.grammar.tokenize.StateStack? = null
        for (line in code.lines()) {
            val result = grammar.tokenizeLine(line, state)
            state = result.ruleStack
            for (token in result.tokens) {
                val style = theme.match(token.scopes)
                val hex = "#%06X".format(style.foreground and 0xFFFFFF)
                val font = style.fontStyle.joinToString(",") { it.name } ?: ""
                val slice = line.substring(
                    token.startIndex.coerceIn(0, line.length),
                    token.endIndex.coerceIn(token.startIndex, line.length),
                ).trim()
                println("  $hex ${token.scopes.joinToString(" ")}  [${token.startIndex}..${token.endIndex}] '$slice'")
            }
        }
    }

    @Test
    fun dumpKotlin() = dump(
        "kotlin",
        """
        inline fun <T : Any> Result<T>.foldOrNull(
            onSuccess: (T) -> Unit,
            onError: (Throwable) -> Unit,
        ): T? = fold(
            onSuccess = { onSuccess(it); it },
            onFailure = { onError(it); null },
        )
        """.trimIndent(),
    )

    @Test
    fun dumpPython() = dump(
        "python",
        """
        # 装饰器
        def cached(fn: Callable) -> Callable:
            store = {}
            def wrap(*args):
                if args not in store:
                    store[args] = fn(*args)
                return store[args]
            return wrap
        """.trimIndent(),
    )

    @Test
    fun dumpGo() = dump(
        "go",
        """
        func fetchAll(urls []string) []string {
            ch := make(chan string)
            for _, u := range urls {
                go func(u string) { ch <- fetch(u) }(u)
            }
            return results
        }
        """.trimIndent(),
    )

    @Test
    fun dumpJava() = dump(
        "java",
        """
        public class RepoService {
            private final HttpClient client;
            public List<String> filterStarts(List<String> items, String prefix) {
                return items.stream()
                    .filter(s -> s.startsWith(prefix))
                    .map(String::toUpperCase)
                    .collect(Collectors.toList());
            }
        }
        """.trimIndent(),
    )
}