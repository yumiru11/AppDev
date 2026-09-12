package com.yumiru11.githubapp.core.designsystem.component

import android.view.WindowId
import androidx.activity.ComponentActivity
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.yumiru11.githubapp.core.designsystem.token.AppBlur
import com.yumiru11.githubapp.core.designsystem.token.GlassRenderPolicy
import com.yumiru11.githubapp.core.designsystem.token.GlassScope
import com.yumiru11.githubapp.core.designsystem.token.GlassSettings
import com.yumiru11.githubapp.core.designsystem.token.LocalGlassSettings
import dev.chrisbanes.haze.rememberHazeState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 弹层玻璃的**窗口隔离契约**（issue #167 / UI22，ui-design §6.1 #4）。
 *
 * 这是本票「backdrop 能否拿到源内容」结论的机械核实：M3 [ModalBottomSheet] 在
 * **独立 window**（Dialog → 自己的 ViewRootImpl）里绘制，而 Haze 的 `hazeEffect`
 * 只能采样**同 window** 的 `hazeSource`——`HazeEffectNode` 用
 * `LocalView.current.windowId` 过滤可采样区域（Haze 1.6.10 `Utils.android.kt#getWindowId`），
 * 跨 window 也没有可共享的 `GraphicsLayer`/`RenderNode`（两个 window 是 SurfaceFlinger
 * 的两层）。于是弹层里挂 `hazeEffect` 只会采样到同 window 内的纯色 dialog scrim，
 * **模糊纯色 = 零视觉差异**，白付一份离屏 RenderNode 的代价。
 *
 * 实测断言（读源码之外再做一次机械核实）：
 * 1. `ModalBottomSheet` 内容所在层的 `LocalView.windowId` 与主 window **不同**
 *    → Haze 必然剔除主 window 的采样区域，模糊不可能生效；
 * 2. 但 `LocalHazeState` 在弹层内**并非为 null**（CompositionLocal 会跨 window 下传），
 *    所以「弹层拿不到 HazeState」不足以解释降级——**窗口隔离才是判据**。这正是
 *    [GlassRenderPolicy.resolveInDialogWindow] 把结论收敛成独立纯函数（而不是复用
 *    `hazeState == null` 那条降级路径）的原因。
 *
 * 若哪天赋层改成「主 window 内的覆盖层」导致本测试 fail（windowId 相同），
 * [GlassRenderPolicy.resolveInDialogWindow] 应同步放开 BackdropBlur，并同步改
 * [GlassSheetSurface] 传给 [GlassSurface] 的 `backdropReachable = false` 以补 `hazeEffect` 接线。
 * 收敛后测试类名与「独立组件」的说法已对齐：[GlassSheetSurface] 现在是 [GlassSurface]
 * 的薄封装，本类同时覆盖「窗口隔离」与「薄封装的开关落点」两件事。
 *
 * 后半组（`sheetGlassAlpha_*` / `glassSheetSurface_*`）锁住 §6.3 开关语义在弹层层面的落点：
 * 半透明层取值与栏侧一致（[AppBlur.SCRIM_ALPHA]），且**点位隔离**——关掉顶栏/底栏/面板
 * 开关不影响弹层，关掉弹层开关也只影响弹层（开关的「禁用」裁决由 `GlassSettingsTest` 断言，
 * 组件消费由本组断言）。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class GlassSheetSurfaceTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun modalBottomSheet_contentWindowDiffersFromMainWindow() {
        // 弹层内 [GlassSheetSurface] 上下文的诊断快照（跨 window 下传的 CompositionLocal）
        var sheetWindowId: WindowId? = null
        var sheetSawHazeState: Boolean? = null
        var mainCompositionWindowId: WindowId? = null

        composeRule.setContent {
            val hazeState = rememberHazeState()
            mainCompositionWindowId = LocalView.current.windowId
            CompositionLocalProvider(LocalHazeState provides hazeState) {
                GlassSheetProbe(
                    onSheetWindowId = { sheetWindowId = it },
                    onSheetHazeState = { sheetSawHazeState = it },
                )
            }
        }
        composeRule.waitForIdle()

        assertNotNull("主 composition 必须能读到 windowId", mainCompositionWindowId)
        assertNotNull("弹层内容必须已组合（否则探针没跑到 GlassSheetSurface 层）", sheetWindowId)
        assertTrue(
            "ModalBottomSheet 必须落在独立 window：main=$mainCompositionWindowId sheet=$sheetWindowId",
            mainCompositionWindowId != sheetWindowId,
        )
        assertTrue(
            "LocalHazeState 应跨 window 下传进弹层（故降级判据是窗口隔离，而非 HazeState 缺失）",
            sheetSawHazeState == true,
        )
    }

    /**
     * 探针：打开真实 [ModalBottomSheet]，在其内容层读取 [LocalView] 的 `windowId` 与
     * [LocalHazeState]，模拟 [GlassSheetSurface] 在弹层内实际看到的上下文。
     */
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun GlassSheetProbe(
        onSheetWindowId: (WindowId?) -> Unit,
        onSheetHazeState: (Boolean) -> Unit,
    ) {
        ModalBottomSheet(onDismissRequest = {}) {
            onSheetWindowId(LocalView.current.windowId)
            onSheetHazeState(LocalHazeState.current != null)
            GlassSheetSurface {
                Text(text = "probe")
            }
        }
    }

    // ── §6.3 开关 / 主题在该点位的落点 ──────────────────────────────────────────
    // 断言「薄封装实际走的生产判定入口」而非像素：
    // 弹层这条路径的可见结果就是那一层半透明 scrim，断言决策函数既精确又不依赖
    // Robolectric 的渲染语义。「该点位是否被裁决为禁用」由 GlassSettingsTest 断言，
    // 这里断言**组件实际拿到的值**（含点位隔离：关别的点位不影响弹层）。

    @Test
    fun sheetGlassAlpha_defaultSettings_returnsScrimAlpha() {
        assertEquals(
            "全开 → 玻璃层 = surfaceContainerLow @ AppBlur.SCRIM_ALPHA（§6.1 静止态 / §6.2 降级层）",
            AppBlur.SCRIM_ALPHA,
            sheetGlassAlphaFor(GlassSettings()),
            0.0001f,
        )
    }

    @Test
    fun sheetGlassAlpha_masterSwitchOff_keepsScrimAlpha() {
        // 设置页总开关关掉 → 与栏侧同款：照旧叠同一层半透明 scrim（关闭的是玻璃的**效果**
        // 即模糊/背景图，不是把容器换色）。开关确实被消费：它决定 §6.3 该点位的裁决
        // 结果并流进唯一的 alpha 出口（layerAlpha）。
        assertEquals(AppBlur.SCRIM_ALPHA, sheetGlassAlphaFor(GlassSettings(masterEnabled = false)), 0.0001f)
    }

    @Test
    fun sheetGlassAlpha_bottomSheetSwitchOff_keepsScrimAlpha() {
        // §6.3 逐项开关（BottomSheet）单独关闭 → 该点位不再走玻璃效果（模糊/背景图），
        // 半透明层与栏侧保持一致（不换色）
        assertEquals(AppBlur.SCRIM_ALPHA, sheetGlassAlphaFor(GlassSettings(bottomSheetEnabled = false)), 0.0001f)
    }

    @Test
    fun sheetGlassAlpha_otherPointSwitchOff_keepsSheetGlass() {
        // 反向断言：顶栏/底栏/面板开关与弹层点位互不干扰（GlassScope 隔离）
        val otherOff =
            GlassSettings(
                topBarEnabled = false,
                bottomBarEnabled = false,
                panelEnabled = false,
            )
        assertEquals(AppBlur.SCRIM_ALPHA, sheetGlassAlphaFor(otherOff), 0.0001f)
    }

    @Test
    fun sheetGlassAlpha_oledTheme_keepsScrimAlpha() {
        // §6.3 F2-1 用户拍板：OLED 纯黑下禁用玻璃（withAccessibilityOverrides 归入总开关）。
        // 弹层点位的玻璃效果本来就只剩半透明层（几何上无法模糊），故与栏侧同款取值——
        // 关键是「该点位被裁决为禁用」，由 GlassSettingsTest 单独断言
        val oled = GlassSettings().withAccessibilityOverrides(oledEnabled = true, highContrastEnabled = false)
        assertEquals(AppBlur.SCRIM_ALPHA, sheetGlassAlphaFor(oled), 0.0001f)
    }

    @Test
    fun sheetGlassAlpha_highContrastTheme_keepsScrimAlpha() {
        // §6.3 F1-3 用户拍板：高对比主题下禁用玻璃（同 OLED：弹层无模糊可禁，取值与开启一致）
        val highContrast = GlassSettings().withAccessibilityOverrides(oledEnabled = false, highContrastEnabled = true)
        assertEquals(AppBlur.SCRIM_ALPHA, sheetGlassAlphaFor(highContrast), 0.0001f)
    }

    @Test
    fun sheetGlassAlpha_oledWithMasterOff_keepsScrimAlpha() {
        // 两个降级来源同时命中：结果与单命中一致，无叠加副作用
        val both =
            GlassSettings(masterEnabled = false)
                .withAccessibilityOverrides(oledEnabled = true, highContrastEnabled = false)
        assertEquals(AppBlur.SCRIM_ALPHA, sheetGlassAlphaFor(both), 0.0001f)
    }

    @Test
    fun glassSheetSurface_withGlassSettings_composesWithoutCrash() {
        // 组件级冒烟：弹层玻璃容器在「该点位关闭」的上下文中也能组合（降级路径不崩）
        composeRule.setContent {
            CompositionLocalProvider(LocalGlassSettings provides GlassSettings(bottomSheetEnabled = false)) {
                GlassSheetSurface {
                    Text(text = "sheet")
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("sheet").assertExists()
    }

    /**
     * 走生产同一条 seam：先按 §6.3 裁决点位开关（[GlassSettings.enabledFor]），
     * 再交给**全仓唯一的降级叠色出口** [GlassRenderPolicy.layerAlpha]。
     *
     * 收敛前这里调的是弹层专用的 `GlassRenderPolicy.sheetGlassAlpha`，与 #219 合入的
     * `layerAlpha` 构成两条并行降级实现；该函数已随收敛删除（「降级该叠多厚」不再有两个
     * 事实来源）。断言口径不变，被测路径改走与栏侧/面板共用的那一条。
     */
    private fun sheetGlassAlphaFor(settings: GlassSettings): Float =
        GlassRenderPolicy.layerAlpha(
            renderMode = GlassRenderPolicy.resolveInDialogWindow(glassAllowed = settings.enabledFor(GlassScope.BOTTOM_SHEET)),
            opaqueWhenBlurUnavailable = false,
        )
}
