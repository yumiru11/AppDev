package com.yumiru11.githubapp.konsist

import com.lemonappdev.konsist.api.Konsist
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Konsist 架构测试占位类（T1）。
 *
 * - CI 的 `./gradlew konsistCheck` 通过 :app 的 `testDebugUnitTest` 过滤
 *   `com.yumiru11.githubapp.konsist.*` 包执行，本类保证该包始终非空、任务可跑。
 * - T2 将在此包补充正式架构规则（分层依赖方向、core:model 禁止 import android 包等）。
 */
class ArchitectureTest {
    @Test
    fun projectHasKotlinSources() {
        val files = Konsist.scopeFromProject().files
        assertTrue("Konsist should be able to scan the project sources", files.isNotEmpty())
    }
}
