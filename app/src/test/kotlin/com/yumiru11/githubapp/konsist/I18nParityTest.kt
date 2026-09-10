package com.yumiru11.githubapp.konsist

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * i18n 覆盖守卫（T25 验收「zh 文案全覆盖」）。
 *
 * 为什么需要它：lint 的 MissingTranslation 默认只报 warning，而本项目 :app:lintDebug
 * 是 abortOnError（warning 不拦）—— 漏翻译会静默通过。而「漏翻一条」在真机上的表现是
 * 中英混排，用户一眼就能看到，属于交付事故。这里把「en 的每个 key 在 zh-rCN 都有对应」
 * 变成硬断言。
 *
 * 口径：
 * - 三类资源都查：string / plurals / string-array
 * - translatable=false 的条目（品牌名、技术标识等）豁免
 * - 只要求 zh 是 en 的超集（zh 多出来的 key 允许）
 *
 * 测试命名规范：methodName_scenario_expectedBehavior。
 */
class I18nParityTest {
    @Test
    fun zhRcnResources_coverEveryTranslatableEnglishKey() {
        val missing = mutableListOf<String>()

        englishResourceFiles().forEach { englishFile ->
            val moduleRes = englishFile.parentFile.parentFile
            val zhFile = File(File(moduleRes, "values-zh-rCN"), "strings.xml")
            val moduleName = moduleRes.parentFile.parentFile.parentFile.name
            if (!zhFile.exists()) {
                missing += "$moduleName: 缺整个 values-zh-rCN/strings.xml"
                return@forEach
            }
            val englishKeys = resourceKeys(englishFile.readText())
            val zhKeys = resourceKeys(zhFile.readText())
            (englishKeys - zhKeys).forEach { key -> missing += "$moduleName: $key" }
        }

        assertTrue(
            "以下 en 文案在 values-zh-rCN 中缺失（请成对补齐，或给品牌名加 translatable=false）：" +
                missing.joinToString(separator = "", prefix = "\n") { "  - $it\n" },
            missing.isEmpty(),
        )
    }

    /** 全仓 en 资源文件（app 与各 core、feature 模块）。测试工作目录 = 模块目录，故先上跳一级。 */
    private fun englishResourceFiles(): List<File> {
        val repoRoot = File(".").canonicalFile.parentFile
        return repoRoot
            .walkTopDown()
            .onEnter { it.name != "build" && it.name != ".git" && it.name != "node_modules" }
            .filter { file ->
                file.isFile &&
                    file.name == "strings.xml" &&
                    file.parentFile.name == "values" &&
                    file.path.replace(File.separatorChar, SLASH).contains("/src/main/res/")
            }.toList()
    }

    /** 提取可翻译资源 key（string / plurals / string-array），跳过 translatable=false。 */
    private fun resourceKeys(xml: String): Set<String> {
        val keys = mutableSetOf<String>()
        RESOURCE_PATTERN.findAll(xml).forEach { match ->
            if (!match.groupValues[3].contains(NOT_TRANSLATABLE)) {
                keys += match.groupValues[2]
            }
        }
        return keys
    }

    private companion object {
        const val SLASH = '/'
        const val NOT_TRANSLATABLE = "translatable=\"false\""

        /** 同时匹配 string / plurals / string-array 的开标签，第 3 组是其余属性。 */
        val RESOURCE_PATTERN = Regex("<(string|plurals|string-array)\\s+name=\"([^\"]+)\"([^>]*)>")
    }
}
