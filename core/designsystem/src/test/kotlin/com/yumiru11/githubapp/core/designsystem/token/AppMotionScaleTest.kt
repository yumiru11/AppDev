package com.yumiru11.githubapp.core.designsystem.token

import org.junit.Assert.assertEquals
import org.junit.Test

class AppMotionScaleTest {
    // ── resolveEffectiveMotionScale：DataStore 滑杆 × 系统缩放取最小 ──

    @Test
    fun resolveEffectiveMotionScale_userBelowSystem_takesUserScale() {
        val result = resolveEffectiveMotionScale(userScale = 0.8f, systemScale = 1f)
        assertEquals(0.8f, result)
    }

    @Test
    fun resolveEffectiveMotionScale_systemBelowUser_takesSystemScale() {
        val result = resolveEffectiveMotionScale(userScale = 1.5f, systemScale = 0.5f)
        assertEquals(0.5f, result)
    }

    @Test
    fun resolveEffectiveMotionScale_systemRemovedAnimations_returnsZero() {
        // 无障碍「移除动画」/开发者选项动画缩放关闭 → ANIMATOR_DURATION_SCALE = 0
        val result = resolveEffectiveMotionScale(userScale = 1.2f, systemScale = 0f)
        assertEquals(0f, result)
    }

    @Test
    fun resolveEffectiveMotionScale_negativeInputs_treatedAsZero() {
        assertEquals(0f, resolveEffectiveMotionScale(userScale = -1f, systemScale = 1f))
        assertEquals(0f, resolveEffectiveMotionScale(userScale = 1f, systemScale = -0.5f))
    }

    @Test
    fun resolveEffectiveMotionScale_bothAboveMax_clampedToMax() {
        val result = resolveEffectiveMotionScale(userScale = 10f, systemScale = 20f)
        assertEquals(MAX_MOTION_SCALE, result)
    }

    @Test
    fun resolveEffectiveMotionScale_defaultValues_returnsOne() {
        val result = resolveEffectiveMotionScale(userScale = 1f, systemScale = 1f)
        assertEquals(1f, result)
    }

    // ── AppMotion.scaledDuration：统一时长消费入口 ──

    @Test
    fun scaledDuration_defaultScale_returnsBaseMillis() {
        assertEquals(AppMotion.DURATION_PAGE_ENTER, AppMotion.scaledDuration(AppMotion.DURATION_PAGE_ENTER, 1f))
    }

    @Test
    fun scaledDuration_halfScale_scalesDown() {
        assertEquals(200, AppMotion.scaledDuration(400, 0.5f))
    }

    @Test
    fun scaledDuration_zeroScale_returnsZero() {
        assertEquals(0, AppMotion.scaledDuration(AppMotion.DURATION_TRANSIENT, 0f))
    }

    @Test
    fun scaledDuration_fractionalResult_roundsToNearestMillis() {
        assertEquals(50, AppMotion.scaledDuration(baseMillis = 90, motionScale = 0.55f))
    }

    @Test
    fun scaledDuration_scaleAboveMax_clampedToMax() {
        assertEquals(150, AppMotion.scaledDuration(baseMillis = 100, motionScale = 10f))
    }

    @Test
    fun scaledDuration_negativeBase_clampedToZero() {
        assertEquals(0, AppMotion.scaledDuration(baseMillis = -5, motionScale = 1f))
    }
}
