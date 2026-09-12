package com.yumiru11.githubapp.core.designsystem.token

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.material3.MotionScheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [AppMotionScheme] 契约测试（ui-design §4.5 / ADR-0008）。
 *
 * 锁死四件事：
 * 1. 委托的 spec 数值与官方 token **逐值一致**（不自造 spring 常量、不漂移）；
 * 2. effects 类 spec 的 `dampingRatio >= 1`（零回弹语义：位移/缩放可弹，颜色/透明度不可弹）；
 * 3. [AppMotionScheme.forMotionScale] 在缩放 ≤ 0 时退化为 standard；
 * 4. 工厂是**单例**——引用稳定才不会让 `LocalMotionScheme` 每帧变值、整棵子树失效。
 *
 * ## 为什么是「写死数值」而不是「引用官方 token 对象」
 * 官方 `androidx.compose.material3.tokens.ExpressiveMotionTokens` /
 * `StandardMotionTokens` 在 **1.5.0-alpha18 仍是 Kotlin-`internal`**：字节码层是
 * public（`javap` 看不到 `$material3` 名字修饰，容易误判），但 Kotlin 元数据层
 * internal，测试里符号引用会直接编译失败：
 * `Cannot access 'object ExpressiveMotionTokens : Any': it is internal in file`（实测）。
 *
 * 因此按预案改为断言具体数值，**来源如下**（可复现复核）：
 * ```
 * material3-android-1.5.0-alpha18.aar  →  classes.jar  →
 * javap -c -p androidx/compose/material3/tokens/ExpressiveMotionTokens.class   # <clinit>
 *   SpringDefaultSpatialDamping 0.8f / SpringDefaultSpatialStiffness 380.0f
 *   SpringFastSpatialDamping    0.6f / SpringFastSpatialStiffness    800.0f
 *   SpringSlowSpatialDamping    0.8f / SpringSlowSpatialStiffness    200.0f
 *   *EffectsDamping             1.0f（fconst_1，三个 effects 全部零回弹）
 *   SpringDefaultEffectsStiffness 1600.0f / Fast 3800.0f / Slow 800.0f
 * StandardMotionTokens: spatial damping 全 0.9f，stiffness 700/1400/300f，
 *   effects 同 expressive（1.0f / 1600·3800·800f）
 * ```
 * 数值一旦变化 = pin 的版本或官方 token 动了 → 本测试报警，需人工复核并同步。
 */
class AppMotionSchemeTest {
    // ── 1. 与官方 token 逐值一致 ──

    private fun assertSpec(
        expectedDamping: Float,
        expectedStiffness: Float,
        actual: FiniteAnimationSpec<Float>,
    ) {
        // 官方 impl 用 `spring(dampingRatio = token, stiffness = token)` 构造
        // （visibilityThreshold 取默认 null），SpringSpec.equals 比较
        // dampingRatio / stiffness / visibilityThreshold 三项 → 可直接值比较。
        assertEquals(spring<Float>(dampingRatio = expectedDamping, stiffness = expectedStiffness), actual)
        assertEquals("dampingRatio", expectedDamping, actual.dampingRatio(), 0f)
        assertEquals("stiffness", expectedStiffness, actual.stiffness(), 0f)
    }

    @Test
    fun expressive_defaultSpatialSpec_matchesOfficialExpressiveToken() {
        assertSpec(0.8f, 380f, AppMotionScheme.expressive().defaultSpatialSpec())
    }

    @Test
    fun expressive_fastSpatialSpec_matchesOfficialExpressiveToken() {
        assertSpec(0.6f, 800f, AppMotionScheme.expressive().fastSpatialSpec())
    }

    @Test
    fun expressive_slowSpatialSpec_matchesOfficialExpressiveToken() {
        assertSpec(0.8f, 200f, AppMotionScheme.expressive().slowSpatialSpec())
    }

    @Test
    fun expressive_defaultEffectsSpec_matchesOfficialExpressiveToken() {
        assertSpec(1f, 1600f, AppMotionScheme.expressive().defaultEffectsSpec())
    }

    @Test
    fun expressive_fastEffectsSpec_matchesOfficialExpressiveToken() {
        assertSpec(1f, 3800f, AppMotionScheme.expressive().fastEffectsSpec())
    }

    @Test
    fun expressive_slowEffectsSpec_matchesOfficialExpressiveToken() {
        assertSpec(1f, 800f, AppMotionScheme.expressive().slowEffectsSpec())
    }

    /** Standard 方案数值（跨版本更稳，但同样锁死以防 standard/expressive 被接反）。 */
    @Test
    fun standard_allSixSpecs_matchOfficialStandardTokens() {
        val scheme = AppMotionScheme.standard()
        assertSpec(0.9f, 700f, scheme.defaultSpatialSpec())
        assertSpec(0.9f, 1400f, scheme.fastSpatialSpec())
        assertSpec(0.9f, 300f, scheme.slowSpatialSpec())
        assertSpec(1f, 1600f, scheme.defaultEffectsSpec())
        assertSpec(1f, 3800f, scheme.fastEffectsSpec())
        assertSpec(1f, 800f, scheme.slowEffectsSpec())
    }

    /** 官方 impl 的 12 个 spec 全部是 [SpringSpec]（非 tween），取值型断言依赖这一点。 */
    private fun FiniteAnimationSpec<Float>.dampingRatio(): Float = (this as SpringSpec<Float>).dampingRatio

    private fun FiniteAnimationSpec<Float>.stiffness(): Float = (this as SpringSpec<Float>).stiffness

    // ── 2. effects 类零回弹（dampingRatio >= 1） ──

    @Test
    fun expressive_effectsSpecs_zeroBounce() {
        // M3 语义：effects（颜色/透明度）不得过冲，dampingRatio 必须 >= 1（临界阻尼）。
        // spatial（位移/尺寸）才允许 < 1 的回弹 —— Expressive 的「弹性」只在空间维度。
        val scheme = AppMotionScheme.expressive()
        assertTrue("defaultEffects", scheme.defaultEffectsSpec<Float>().dampingRatio() >= NO_BOUNCE_DAMPING)
        assertTrue("fastEffects", scheme.fastEffectsSpec<Float>().dampingRatio() >= NO_BOUNCE_DAMPING)
        assertTrue("slowEffects", scheme.slowEffectsSpec<Float>().dampingRatio() >= NO_BOUNCE_DAMPING)
    }

    @Test
    fun standard_effectsSpecs_zeroBounce() {
        val scheme = AppMotionScheme.standard()
        assertTrue("defaultEffects", scheme.defaultEffectsSpec<Float>().dampingRatio() >= NO_BOUNCE_DAMPING)
        assertTrue("fastEffects", scheme.fastEffectsSpec<Float>().dampingRatio() >= NO_BOUNCE_DAMPING)
        assertTrue("slowEffects", scheme.slowEffectsSpec<Float>().dampingRatio() >= NO_BOUNCE_DAMPING)
    }

    @Test
    fun expressive_spatialSpecs_areBouncy() {
        // 反向断言：Expressive 的价值就在 spatial 的低阻尼回弹；若哪天 spatial 也 >= 1，
        // 说明 expressive 相对 standard 已退化（二者只差 stiffness），必须人工复核。
        val scheme = AppMotionScheme.expressive()
        assertTrue("defaultSpatial", scheme.defaultSpatialSpec<Float>().dampingRatio() < NO_BOUNCE_DAMPING)
        assertTrue("fastSpatial", scheme.fastSpatialSpec<Float>().dampingRatio() < NO_BOUNCE_DAMPING)
        assertTrue("slowSpatial", scheme.slowSpatialSpec<Float>().dampingRatio() < NO_BOUNCE_DAMPING)
    }

    // ── 3. forMotionScale 退化 ──

    @Test
    fun forMotionScale_zeroScale_degradesToStandard() {
        // 无障碍「移除动画」/ 动画缩放关闭：去回弹，不是去动画（见 AppMotionScheme KDoc）
        assertSame(AppMotionScheme.standard(), AppMotionScheme.forMotionScale(0f))
    }

    @Test
    fun forMotionScale_negativeScale_degradesToStandard() {
        assertSame(AppMotionScheme.standard(), AppMotionScheme.forMotionScale(-1f))
    }

    @Test
    fun forMotionScale_positiveScales_returnsExpressive() {
        assertSame(AppMotionScheme.expressive(), AppMotionScheme.forMotionScale(0.5f))
        assertSame(AppMotionScheme.expressive(), AppMotionScheme.forMotionScale(1f))
        assertSame(AppMotionScheme.expressive(), AppMotionScheme.forMotionScale(MAX_MOTION_SCALE))
    }

    // ── 4. 单例（CompositionLocal 引用稳定） ──

    @Test
    fun standard_repeatedCalls_returnsSameInstance() {
        assertSame(AppMotionScheme.standard(), AppMotionScheme.standard())
    }

    @Test
    fun expressive_repeatedCalls_returnsSameInstance() {
        // 逐帧新建会让 MaterialTheme(motionScheme = …) 每帧写新值 → 整棵子树失效
        assertSame(AppMotionScheme.expressive(), AppMotionScheme.expressive())
    }

    @Test
    fun standard_delegatesToOfficialSingleton() {
        assertSame(MotionScheme.standard(), AppMotionScheme.standard())
    }

    @Test
    fun expressive_delegatesToOfficialSingleton() {
        assertSame(MotionScheme.expressive(), AppMotionScheme.expressive())
    }

    @Test
    fun forMotionScale_isPure_sameInputSameInstance() {
        assertSame(AppMotionScheme.forMotionScale(1f), AppMotionScheme.forMotionScale(1f))
    }

    /** 官方 impl 的 spec 也应是**复用**的单例字段，而非逐次新建。 */
    @Test
    fun expressive_specs_areReusedAcrossCalls() {
        val scheme = AppMotionScheme.expressive()
        assertSame(scheme.defaultSpatialSpec<Float>(), scheme.defaultSpatialSpec<Float>())
        assertSame(scheme.slowEffectsSpec<Float>(), scheme.slowEffectsSpec<Float>())
    }

    private companion object {
        /** 临界阻尼 = 零回弹（`Spring.DampingRatioNoBouncy` 的语义值）。 */
        const val NO_BOUNCE_DAMPING: Float = 1f
    }
}
