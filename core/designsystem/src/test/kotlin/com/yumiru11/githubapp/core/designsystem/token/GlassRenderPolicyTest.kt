package com.yumiru11.githubapp.core.designsystem.token

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [GlassRenderPolicy] 单测（issue #83 可验证性补齐 + issue #167 / UI22 弹层点位）。
 *
 * 为什么需要这组测试：backdrop blur 的真实像素在 JVM 侧不可断言——Robolectric 不渲染
 * RenderEffect，CI 模拟器又是 API 30（低于 [AppBlur.MIN_BLUR_API]），所以
 * GlassSurface 的 hazeEffect 分支过去只能靠真机眼睛确认。把渲染模式判定收敛成纯函数后，
 * 「生效一次 + 降级四条（含弹层窗口隔离）+ 31 边界」全部可在纯 JVM 断言，
 * 真机验收卡只保留观感项。
 *
 * [GlassRenderPolicy.resolveInDialogWindow] 那组（UI22）：弹层是独立 window，Haze 按
 * `LocalView.current.windowId` 过滤采样区域 → 弹层内不存在可糊的 backdrop，一律降级。
 * 窗口隔离本身由 `GlassSheetWindowIsolationTest` 用真实 `ModalBottomSheet` 实测断言。
 *
 * 命名规范：methodName_scenario_expectedBehavior。
 */
class GlassRenderPolicyTest {
    @Test
    fun resolve_api31WithHazeState_returnsBackdropBlur() {
        assertEquals(
            GlassRenderMode.BackdropBlur,
            GlassRenderPolicy.resolve(blurEnabled = true, backdropReachable = true, sdkInt = API_31),
        )
    }

    @Test
    fun resolve_blurDisabled_returnsTranslucentScrim() {
        assertEquals(
            GlassRenderMode.TranslucentScrim,
            GlassRenderPolicy.resolve(blurEnabled = false, backdropReachable = true, sdkInt = API_35),
        )
    }

    @Test
    fun resolve_missingHazeState_returnsTranslucentScrim() {
        // 内容侧没挂 hazeSource：模糊会每帧采样到空背景（真机「无效果」根因）→ 必须降级
        assertEquals(
            GlassRenderMode.TranslucentScrim,
            GlassRenderPolicy.resolve(blurEnabled = true, backdropReachable = false, sdkInt = API_35),
        )
    }

    @Test
    fun resolve_apiBelow31_returnsTranslucentScrim() {
        assertEquals(
            GlassRenderMode.TranslucentScrim,
            GlassRenderPolicy.resolve(blurEnabled = true, backdropReachable = true, sdkInt = API_30),
        )
    }

    @Test
    fun resolve_apiBoundaryAtMinBlurApi_returnsBackdropBlur() {
        // 边界：恰好等于 MIN_BLUR_API 即生效（>= 语义）
        assertEquals(
            GlassRenderMode.BackdropBlur,
            GlassRenderPolicy.resolve(blurEnabled = true, backdropReachable = true, sdkInt = AppBlur.MIN_BLUR_API),
        )
        assertEquals(
            GlassRenderMode.TranslucentScrim,
            GlassRenderPolicy.resolve(blurEnabled = true, backdropReachable = true, sdkInt = AppBlur.MIN_BLUR_API - 1),
        )
    }

    @Test
    fun shouldAttachHazeSource_api31Enabled_true() {
        assertTrue(GlassRenderPolicy.shouldAttachHazeSource(blurEnabled = true, sdkInt = API_35))
    }

    @Test
    fun shouldAttachHazeSource_blurDisabled_false() {
        // source 侧与 effect 侧同源判定：开关关掉时内容侧也不挂 hazeSource（省一份离屏 RenderNode）
        assertTrue(!GlassRenderPolicy.shouldAttachHazeSource(blurEnabled = false, sdkInt = API_35))
    }

    @Test
    fun shouldAttachHazeSource_apiBelowMin_false() {
        assertTrue(!GlassRenderPolicy.shouldAttachHazeSource(blurEnabled = true, sdkInt = API_30))
    }

    // ── 弹层点位（issue #167 / UI22，ui-design §6.1 #4）────────────────────────────

    @Test
    fun resolveInDialogWindow_api35WithBlurEnabled_returnsTranslucentScrim() {
        // 弹层是独立 window：Haze 采样不到主 window 的 hazeSource（windowId 不等），
        // 挂 hazeEffect 只会模糊同 window 内的纯色 scrim（无视觉差异）→ 固定走降级
        assertEquals(
            GlassRenderMode.TranslucentScrim,
            GlassRenderPolicy.resolveInDialogWindow(glassAllowed = true, sdkInt = API_35),
        )
    }

    @Test
    fun resolveInDialogWindow_blurDisabled_returnsTranslucentScrim() {
        // §6.3 逐项开关（BottomSheet）关闭：仍走降级路径，只是该点位连半透明层都不加
        // （不透明 surface 底，由 GlassSheetSurface 依 renderMode 决定 alpha）
        assertEquals(
            GlassRenderMode.TranslucentScrim,
            GlassRenderPolicy.resolveInDialogWindow(glassAllowed = false, sdkInt = API_35),
        )
    }

    @Test
    fun resolveInDialogWindow_apiBelow31_returnsTranslucentScrim() {
        assertEquals(
            GlassRenderMode.TranslucentScrim,
            GlassRenderPolicy.resolveInDialogWindow(glassAllowed = true, sdkInt = AppBlur.MIN_BLUR_API - 1),
        )
    }

    @Test
    fun resolveInDialogWindow_apiBeyond31_neverReturnsBackdropBlur() {
        // 饱和断言：弹层点位的返回值与 API 无关（窗口隔离是几何事实，不是版本能力）。
        // 若哪天本断言失败，说明 resolveInDialogWindow 放开了 BackdropBlur —— 此时必须
        // 同步给 GlassSheetSurface 补 hazeEffect 接线，否则会「声明了模糊其实没挂」。
        val sdkRange = listOf(API_26, API_30, AppBlur.MIN_BLUR_API, API_35, API_36)
        val modes = sdkRange.map { sdk -> GlassRenderPolicy.resolveInDialogWindow(glassAllowed = true, sdkInt = sdk) }
        assertTrue(
            "弹层点位必须恒为半透明降级，实际：${sdkRange.zip(modes)}",
            modes.all { it == GlassRenderMode.TranslucentScrim },
        )
    }

    @Test
    fun resolveInDialogWindow_defaultSdkArgument_returnsTranslucentScrim() {
        // 默认参数走设备值（Build.VERSION.SDK_INT），行为不随调用形式变化
        assertEquals(
            GlassRenderMode.TranslucentScrim,
            GlassRenderPolicy.resolveInDialogWindow(glassAllowed = true),
        )
    }

    private companion object {
        const val API_26 = 26
        const val API_30 = 30
        const val API_31 = 31
        const val API_35 = 35
        const val API_36 = 36
    }
}
