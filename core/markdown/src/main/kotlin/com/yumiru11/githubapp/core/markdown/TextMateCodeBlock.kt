@file:Suppress("TooGenericExceptionCaught", "SwallowedException") // 语法资产加载多异常来源必须全兜底（IO/解析/正则初始化）；降级为样式代码块是设计决策而非吞错（2026-08-14 真机走查修复）

package com.yumiru11.githubapp.core.markdown

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import dev.textmate.compose.CodeBlock
import dev.textmate.grammar.Grammar
import dev.textmate.grammar.raw.GrammarReader
import dev.textmate.regex.JoniOnigLib
import dev.textmate.theme.Theme

/** fence 语言标记 → 语法文件映射（仅列 assets/grammars/ 实际存在的 7 种，其余走纯文本兜底） */
private val GRAMMAR_FILES =
    mapOf(
        "kotlin" to "grammars/kotlin.tmLanguage.json",
        "python" to "grammars/python.tmLanguage.json",
        "go" to "grammars/go.tmLanguage.json",
        "java" to "grammars/java.tmLanguage.json",
        "json" to "grammars/json.tmLanguage.json",
        "yaml" to "grammars/yaml.tmLanguage.json",
        "shell" to "grammars/shell.tmLanguage.json",
    )

/** 加载 TextMate 语法（remember 缓存；不支持的语言返回 null；资产损坏不崩溃） */
@Composable
fun rememberTextMateGrammar(language: String): Grammar? {
    val context = LocalContext.current
    return remember(context, language) {
        val file = GRAMMAR_FILES[language.lowercase()] ?: return@remember null
        try {
            context.assets
                .open(file)
                .use { GrammarReader.readGrammar(it) }
                .let { Grammar(it.scopeName, it, JoniOnigLib()) }
        } catch (e: Exception) {
            // 语法资产缺失/损坏：落 null 走样式兜底，不让代码块渲染崩溃（2026-08-14 真机走查修复）
            null
        }
    }
}

/** 加载 M3 派生 TextMate 主题（从 colorScheme 动态生成，代替固定 Dark+/Light+ 资产） */
@Composable
fun rememberTextMateTheme(darkTheme: Boolean): Theme = rememberM3TextMateTheme(darkTheme = darkTheme)

/** markdown 代码块（M3 全融合主题便捷入口）。 */
@Composable
fun TextMateCodeBlock(
    code: String,
    language: String?,
    darkTheme: Boolean,
) {
    TextMateCodeBlock(code = code, language = language, theme = rememberTextMateTheme(darkTheme))
}

/** markdown 代码块：KotlinTextMate 渲染（VS Code 同款），无语法/失败时带样式代码块兜底 */
@Composable
fun TextMateCodeBlock(
    code: String,
    language: String?,
    theme: Theme,
) {
    val grammar = language?.let { rememberTextMateGrammar(it) }
    if (grammar != null) {
        CodeBlock(code = code, grammar = grammar, theme = theme)
    } else {
        FallbackCodeBlock(code)
    }
}

/** 语法不可用时的兜底代码块：深色容器 + 圆角 + 等宽 + 横向滚动（不是裸文本，2026-08-14 修复） */
@Composable
private fun FallbackCodeBlock(code: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = code,
            color = MaterialTheme.colorScheme.onSurface,
            fontFamily = FontFamily.Monospace,
            fontSize = MaterialTheme.typography.bodySmall.fontSize,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(12.dp),
        )
    }
}
