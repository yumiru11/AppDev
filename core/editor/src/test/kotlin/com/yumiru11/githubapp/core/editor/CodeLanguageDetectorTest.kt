package com.yumiru11.githubapp.core.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 语言检测测试（文件名 → TextMate 语法资产）。
 */
class CodeLanguageDetectorTest {
    @Test
    fun grammarForFile_commonExtensions_mapsToGrammar() {
        assertEquals("kotlin.tmLanguage.json", CodeLanguageDetector.grammarForFile("Main.kt"))
        assertEquals("kotlin.tmLanguage.json", CodeLanguageDetector.grammarForFile("build.gradle.kts"))
        assertEquals("java.tmLanguage.json", CodeLanguageDetector.grammarForFile("Foo.java"))
        assertEquals("python.tmLanguage.json", CodeLanguageDetector.grammarForFile("app.py"))
        assertEquals("go.tmLanguage.json", CodeLanguageDetector.grammarForFile("main.go"))
        assertEquals("json.tmLanguage.json", CodeLanguageDetector.grammarForFile("data.json"))
        assertEquals("yaml.tmLanguage.json", CodeLanguageDetector.grammarForFile("config.yaml"))
        assertEquals("yaml.tmLanguage.json", CodeLanguageDetector.grammarForFile("config.yml"))
        assertEquals("shell.tmLanguage.json", CodeLanguageDetector.grammarForFile("deploy.sh"))
        assertEquals("shell.tmLanguage.json", CodeLanguageDetector.grammarForFile("run.bash"))
        assertEquals("JavaScript.tmLanguage.json", CodeLanguageDetector.grammarForFile("index.js"))
        assertEquals("html.tmLanguage.json", CodeLanguageDetector.grammarForFile("index.html"))
        assertEquals("xml.tmLanguage.json", CodeLanguageDetector.grammarForFile("layout.xml"))
        assertEquals("markdown.tmLanguage.json", CodeLanguageDetector.grammarForFile("README.md"))
        assertEquals("markdown.tmLanguage.json", CodeLanguageDetector.grammarForFile("CHANGELOG.markdown"))
    }

    @Test
    fun grammarForFile_pathWithDirectories_extractsFileName() {
        assertEquals("kotlin.tmLanguage.json", CodeLanguageDetector.grammarForFile("src/main/kotlin/Main.kt"))
    }

    @Test
    fun grammarForFile_uppercaseExtension_isCaseInsensitive() {
        assertEquals("java.tmLanguage.json", CodeLanguageDetector.grammarForFile("Main.JAVA"))
        assertEquals("json.tmLanguage.json", CodeLanguageDetector.grammarForFile("data.JSON"))
    }

    @Test
    fun grammarForFile_unknownExtension_returnsNull() {
        assertNull(CodeLanguageDetector.grammarForFile("notes.txt"))
        assertNull(CodeLanguageDetector.grammarForFile("archive.zip"))
        assertNull(CodeLanguageDetector.grammarForFile("image.png"))
    }

    @Test
    fun grammarForFile_noExtension_returnsNull() {
        assertNull(CodeLanguageDetector.grammarForFile("Makefile"))
        assertNull(CodeLanguageDetector.grammarForFile("README"))
        assertNull(CodeLanguageDetector.grammarForFile(".gitignore"))
    }
}
