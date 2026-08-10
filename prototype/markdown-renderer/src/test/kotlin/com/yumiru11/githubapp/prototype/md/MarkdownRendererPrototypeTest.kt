// ===== PROTOTYPE 截图测试（Robolectric + Roborazzi，Linux 纯 JVM 免模拟器）=====
// 矩阵：3 变体 × 2 主题 = 6 张截图。产物：prototype/markdown-renderer/build/outputs/roborazzi/*.png
package com.yumiru11.githubapp.prototype.md

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-640dpi")
class MarkdownRendererPrototypeTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun snapshot(name: String, variant: MdVariant, dark: Boolean) {
        composeRule.setContent { PrototypeMarkdownScreen(variant, dark) }
        composeRule.onRoot().captureRoboImage("$name.png")
    }

    @Test fun md_a_issueBody_light_renders() = snapshot("md_a_issue_light", MdVariant.A, dark = false)

    @Test fun md_a_issueBody_dark_renders() = snapshot("md_a_issue_dark", MdVariant.A, dark = true)

    @Test fun md_b_heavyGfm_light_renders() = snapshot("md_b_gfm_light", MdVariant.B, dark = false)

    @Test fun md_b_heavyGfm_dark_renders() = snapshot("md_b_gfm_dark", MdVariant.B, dark = true)

    @Test fun md_c_readme_light_renders() = snapshot("md_c_readme_light", MdVariant.C, dark = false)

    @Test fun md_c_readme_dark_renders() = snapshot("md_c_readme_dark", MdVariant.C, dark = true)

    @Test fun md_d_codeMatrix_light_renders() = snapshot("md_d_code_light", MdVariant.D, dark = false)

    @Test fun md_d_codeMatrix_dark_renders() = snapshot("md_d_code_dark", MdVariant.D, dark = true)

    @Test fun md_e_codeMatrix2_light_renders() = snapshot("md_e_code2_light", MdVariant.E, dark = false)

    @Test fun md_f_tailElements_light_renders() = snapshot("md_f_tail_light", MdVariant.F, dark = false)

    @Test fun md_f_tailElements_dark_renders() = snapshot("md_f_tail_dark", MdVariant.F, dark = true)
}