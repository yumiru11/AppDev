package com.yumiru11.githubapp.core.testing.screenshot

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import com.github.takahirom.roborazzi.captureRoboImage

/**
 * 含**无限动画**屏幕的确定性截图捕获（Roborazzi compose 捕获路径的替代品）。
 *
 * ## 为什么存在（#C 根因，2026-09-12 实证）
 *
 * Roborazzi 的 compose 捕获入口 `captureRoboImage(content)` 在截图前会执行
 * `ShadowLooper.shadowMainLooper().idle()`（见 Roborazzi.kt `captureScreenIfMultipleWindows`）。
 * `idle()` 会一直排空主 looper 的任务队列——而无限动画（`CircularProgressIndicator`、
 * `PullToRefreshBox` 的刷新指示器）每帧都通过 Choreographer 续订下一帧，
 * 队列**永不为空** → `idle()` 永不返回 → verify/record 挂死。
 *
 * 本机 jstack 实证（`:feature:issue:verifyRoborazziDebug` 挂起 7min+ 时抓取）：
 * ```
 * "SDK 35 Main Thread" RUNNABLE cpu=47.9s（全文全是在烧 CPU，不是阻塞）
 *   ShadowPausedLooper.idle ← RoborazziKt.captureScreenIfMultipleWindows(Roborazzi.kt:216)
 *     ← Choreographer.doFrame ← AndroidUiDispatcher.performFrameDispatch
 *       ← Recomposer.runRecomposeAndApplyChanges ← Snapshot.sendApplyNotifications
 * ```
 * 最小复现：仅 `AppCenteredLoadingState()`（一个转圈）在 verify/record 下即挂死
 * （marker 停在 `loading-compose#1`，150s timeout 杀进程）。
 *
 * ## 做法
 *
 * 1. `mainClock.autoAdvance = false`：冻结组合测试时钟，动画不再自驱（ComposeTestRule
 *    的 `InfiniteAnimationPolicy` 同时兜底，无限动画不会阻塞 idle）；
 * 2. `advanceTimeBy(advanceMillis)`：从 setContent 起推进**固定**时长——同一份代码
 *    两次独立运行产出的 PNG md5 相同（本机双跑实测，`b506b0b4…`）；
 * 3. `activity.window.decorView.draw(Canvas(bitmap))`：手工绘制到 Bitmap
 *    （Robolectric Native Graphics 下 `captureToImage()` 的 forceRedraw 会 2s 超时，不可用）；
 * 4. `Bitmap.captureRoboImage(file)`：Roborazzi 的 Bitmap 重载**不做 idle 等待**，
 *    直接做 baseline 写入（record）或比对（verify）。
 *
 * 调用方必须持有 `createAndroidComposeRule<ComponentActivity>()`（或同族 rule），
 * 并且**不要再调用** `captureRoboImage(content)` / `ScreenshotTest.captureScreenshot`
 * ——那条路径依然会挂。
 *
 * @param name 基准文件名（不含扩展名），与 [ScreenshotTest.captureScreenshot] 同约定
 * @param darkTheme 是否用 M3 暗色 colorScheme 包一层 MaterialTheme
 * @param screenshotDir 基准目录（相对被测模块根），默认 `src/test/screenshots`
 * @param advanceMillis 冻结时钟后推进的固定毫秒数（默认 2000ms，足够 Paging 数据落位与
 *  入场动画走完；不稳定时调大只会平移动画相位，确定性不变）
 */
fun AndroidComposeTestRule<*, ComponentActivity>.captureScreenshotDeterministic(
    name: String,
    darkTheme: Boolean,
    screenshotDir: String = "src/test/screenshots",
    advanceMillis: Long = 2_000L,
    content: @Composable () -> Unit,
) {
    mainClock.autoAdvance = false
    setContent {
        MaterialTheme(colorScheme = if (darkTheme) darkColorScheme() else lightColorScheme()) {
            content()
        }
    }
    mainClock.advanceTimeBy(advanceMillis)

    val view = activity.window.decorView
    val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
    view.draw(Canvas(bitmap))
    bitmap.captureRoboImage("$screenshotDir/$name.png")
}
