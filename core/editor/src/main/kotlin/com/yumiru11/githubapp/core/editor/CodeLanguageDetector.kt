package com.yumiru11.githubapp.core.editor

/**
 * 代码语言检测（文件名 → TextMate 语法资产）。
 *
 * 语法资产位于 core/editor assets/grammars/（TextMate tmLanguage JSON，VS Code 同款）。
 * 未知扩展名返回 null → 编辑器以纯文本展示（无高亮），不崩溃。
 *
 * 纯函数，供单元测试直接验证映射表。
 */
object CodeLanguageDetector {
    /** 扩展名（小写，无点）→ 语法资产文件名 */
    private val EXTENSION_GRAMMARS: Map<String, String> =
        mapOf(
            "kt" to "kotlin.tmLanguage.json",
            "kts" to "kotlin.tmLanguage.json",
            "java" to "java.tmLanguage.json",
            "py" to "python.tmLanguage.json",
            "go" to "go.tmLanguage.json",
            "json" to "json.tmLanguage.json",
            "yml" to "yaml.tmLanguage.json",
            "yaml" to "yaml.tmLanguage.json",
            "sh" to "shell.tmLanguage.json",
            "bash" to "shell.tmLanguage.json",
            "zsh" to "shell.tmLanguage.json",
            "js" to "JavaScript.tmLanguage.json",
            "mjs" to "JavaScript.tmLanguage.json",
            "cjs" to "JavaScript.tmLanguage.json",
            "html" to "html.tmLanguage.json",
            "htm" to "html.tmLanguage.json",
            "xml" to "xml.tmLanguage.json",
            "md" to "markdown.tmLanguage.json",
            "markdown" to "markdown.tmLanguage.json",
        )

    /**
     * 返回文件名对应的语法资产名（assets/grammars/ 下），未知语言返回 null。
     *
     * @param fileName 文件名（可含路径，取最后一个点后的扩展名；无扩展名 → null）
     */
    fun grammarForFile(fileName: String): String? {
        val base = fileName.substringAfterLast('/')
        val ext = base.substringAfterLast('.', "")
        if (ext.isEmpty() || ext == base) return null
        return EXTENSION_GRAMMARS[ext.lowercase()]
    }
}
