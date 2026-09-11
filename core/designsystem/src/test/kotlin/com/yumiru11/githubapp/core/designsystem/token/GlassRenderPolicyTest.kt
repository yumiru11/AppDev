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
 * 窗口隔离本身由 `GlassSheetSurfaceTest.modalBottomSheet_contentWindowDiffersFromMainWindow`
 * 用真实 `ModalBottomSheet` 实测断言。
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
    // 收敛后（merge）：生产入口是 GlassSurface(backdropReachable = false)，它内部调 resolve()，
    // 与本组 resolveInDialogWindow 的返回值恒等 —— 本组是「独立 window」这条几何事实的
    // 规范锚点（布尔量本身说不出为什么，理由只在这里成文）。

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
        // §6.3 逐项开关（BottomSheet）关闭：仍走降级路径（几何决定的），开关只影响
        // §6.3 的「该点位是否走玻璃效果」裁决本身，不改变渲染模式
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
        // 同步改 GlassSheetSurface 的 backdropReachable（放开 hazeEffect 接线），
        // 否则会「声明了模糊其实没挂」。
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

    // ── layerAlpha（#201 P0 全屏面板叠印）────────────────────────────────────
    // 四条组合把「哪种情况必须不透明」钉死：只有真的在模糊时才可以半透明。
    // 全屏面板的降级路径若回到 SCRIM_ALPHA，下层文字会以 12.5% 浓度不模糊地透上来叠印。
    // 收敛后本函数是**全仓唯一**的降级叠色出口（弹层薄封装也走这里）。

    @Test
    fun layerAlpha_backdropBlurOpaqueRequested_keepsTranslucentGlass() {
        // 模糊生效：透上来的是糊掉的色块（无字形），玻璃观感必须保留 → 半透明
        assertEquals(
            AppBlur.SCRIM_ALPHA,
            GlassRenderPolicy.layerAlpha(
                renderMode = GlassRenderMode.BackdropBlur,
                opaqueWhenBlurUnavailable = true,
            ),
        )
    }

    @Test
    fun layerAlpha_translucentScrimOpaqueRequested_returnsFullyOpaque() {
        // 降级 + 调用方要求不透明（全屏面板）→ 1.0，这是 P0 的修复点
        assertEquals(
            1f,
            GlassRenderPolicy.layerAlpha(
                renderMode = GlassRenderMode.TranslucentScrim,
                opaqueWhenBlurUnavailable = true,
            ),
        )
    }

    @Test
    fun layerAlpha_translucentScrimDefault_keepsTranslucentScrim() {
        // 顶栏/底栏/BottomSheet 不传该开关：行为与 #83 完全一致（内容从栏后穿过）
        assertEquals(
            AppBlur.SCRIM_ALPHA,
            GlassRenderPolicy.layerAlpha(
                renderMode = GlassRenderMode.TranslucentScrim,
                opaqueWhenBlurUnavailable = false,
            ),
        )
    }

    @Test
    fun layerAlpha_backdropBlurDefault_keepsTranslucentScrim() {
        assertEquals(
            AppBlur.SCRIM_ALPHA,
            GlassRenderPolicy.layerAlpha(
                renderMode = GlassRenderMode.BackdropBlur,
                opaqueWhenBlurUnavailable = false,
            ),
        )
    }

    @Test
    fun layerAlpha_sheetDialogWindowMode_keepsTranslucentScrim() {
        // 收敛交叉断言：弹层的渲染模式（恒 TranslucentScrim）流进唯一的 alpha 出口后，
        // 得到的就是栏侧同款半透明 scrim ——「弹层关掉开关也不换色」由这一条钉死，
        // 无需再有一条弹层专用的 alpha 函数。
        assertEquals(
            AppBlur.SCRIM_ALPHA,
            GlassRenderPolicy.layerAlpha(
                renderMode = GlassRenderPolicy.resolveInDialogWindow(glassAllowed = true),
                opaqueWhenBlurUnavailable = false,
            ),
        )
        assertEquals(
            AppBlur.SCRIM_ALPHA,
            GlassRenderPolicy.layerAlpha(
                renderMode = GlassRenderPolicy.resolveInDialogWindow(glassAllowed = false),
                opaqueWhenBlurUnavailable = false,
            ),
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
