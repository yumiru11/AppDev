package com.yumiru11.githubapp.core.markdown

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import dev.textmate.compose.CodeBlock
import dev.textmate.grammar.Grammar
import dev.textmate.grammar.raw.GrammarReader
import dev.textmate.regex.JoniOnigLib
import dev.textmate.theme.Theme
import dev.textmate.theme.ThemeReader

/** fence 语言标记 → 语法文件映射（仅列 assets/grammars/ 实际存在的 7 种，其余走纯文本兜底） */
private val GRAMMAR_FILES = mapOf(
    "kotlin" to "grammars/kotlin.tmLanguage.json",
    "python" to "grammars/python.tmLanguage.json",
    "go" to "grammars/go.tmLanguage.json",
    "java" to "grammars/java.tmLanguage.json",
    "json" to "grammars/json.tmLanguage.json",
    "yaml" to "grammars/yaml.tmLanguage.json",
    "shell" to "grammars/shell.tmLanguage.json",
)

/** 加载 TextMate 语法（remember 缓存；不支持的语言返回 null） */
@Composable
fun rememberTextMateGrammar(language: String): Grammar? {
    val context = LocalContext.current
    return remember(context, language) {
        val file = GRAMMAR_FILES[language.lowercase()] ?: return@remember null
        val raw = context.assets.open(file).use { GrammarReader.readGrammar(it) }
        Grammar(raw.scopeName, raw, JoniOnigLib())
    }
}

/** 加载 VS Code 主题（Dark+/Light+：base + overlay 两层） */
@Composable
fun rememberTextMateTheme(darkTheme: Boolean): Theme {
    val context = LocalContext.current
    return remember(context, darkTheme) {
        val (base, plus) = if (darkTheme) {
            "themes/dark_vs.json" to "themes/dark_plus.json"
        } else {
            "themes/light_vs.json" to "themes/light_plus.json"
        }
        context.assets.open(base).use { b ->
            context.assets.open(plus).use { p ->
                ThemeReader.readTheme(b, p)
            }
        }
    }
}

/** markdown 代码块：KotlinTextMate 渲染（VS Code 同款），无语法时纯文本 */
@Composable
fun TextMateCodeBlock(code: String, language: String?, darkTheme: Boolean) {
    val theme = rememberTextMateTheme(darkTheme)
    val grammar = language?.let { rememberTextMateGrammar(it) }
    if (grammar != null) {
        CodeBlock(code = code, grammar = grammar, theme = theme)
    } else {
        Text(
            text = code,
            color = MaterialTheme.colorScheme.onSurface,
            fontFamily = FontFamily.Monospace,
        )
    }
}
