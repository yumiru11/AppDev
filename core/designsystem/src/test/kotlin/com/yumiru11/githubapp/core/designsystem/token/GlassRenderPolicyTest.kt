package com.yumiru11.githubapp.core.designsystem.token

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [GlassRenderPolicy] 单测（issue #83 可验证性补齐）。
 *
 * 为什么需要这组测试：backdrop blur 的真实像素在 JVM 侧不可断言——Robolectric 不渲染
 * RenderEffect，CI 模拟器又是 API 30（低于 [AppBlur.MIN_BLUR_API]），所以
 * GlassSurface 的 hazeEffect 分支过去只能靠真机眼睛确认。把渲染模式判定收敛成纯函数后，
 * 「生效一次 + 降级三条 + 31 边界」全部可在纯 JVM 断言，真机验收卡只保留观感项。
 *
 * 命名规范：methodName_scenario_expectedBehavior。
 */
class GlassRenderPolicyTest {
    @Test
    fun resolve_api31WithHazeState_returnsBackdropBlur() {
        assertEquals(
            GlassRenderMode.BackdropBlur,
            GlassRenderPolicy.resolve(blurEnabled = true, hasHazeState = true, sdkInt = API_31),
        )
    }

    @Test
    fun resolve_blurDisabled_returnsTranslucentScrim() {
        assertEquals(
            GlassRenderMode.TranslucentScrim,
            GlassRenderPolicy.resolve(blurEnabled = false, hasHazeState = true, sdkInt = API_35),
        )
    }

    @Test
    fun resolve_missingHazeState_returnsTranslucentScrim() {
        // 内容侧没挂 hazeSource：模糊会每帧采样到空背景（真机「无效果」根因）→ 必须降级
        assertEquals(
            GlassRenderMode.TranslucentScrim,
            GlassRenderPolicy.resolve(blurEnabled = true, hasHazeState = false, sdkInt = API_35),
        )
    }

    @Test
    fun resolve_apiBelow31_returnsTranslucentScrim() {
        assertEquals(
            GlassRenderMode.TranslucentScrim,
            GlassRenderPolicy.resolve(blurEnabled = true, hasHazeState = true, sdkInt = API_30),
        )
    }

    @Test
    fun resolve_apiBoundaryAtMinBlurApi_returnsBackdropBlur() {
        // 边界：恰好等于 MIN_BLUR_API 即生效（>= 语义）
        assertEquals(
            GlassRenderMode.BackdropBlur,
            GlassRenderPolicy.resolve(blurEnabled = true, hasHazeState = true, sdkInt = AppBlur.MIN_BLUR_API),
        )
        assertEquals(
            GlassRenderMode.TranslucentScrim,
            GlassRenderPolicy.resolve(blurEnabled = true, hasHazeState = true, sdkInt = AppBlur.MIN_BLUR_API - 1),
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

    private companion object {
        const val API_30 = 30
        const val API_31 = 31
        const val API_35 = 35
    }
}
