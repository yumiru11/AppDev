package com.yumiru11.githubapp.core.designsystem.token

import android.os.Build

/**
 * 玻璃渲染模式判定（纯函数，JVM 可断言）。
 *
 * **为什么单独成函数**（issue #83 验收可验证性）：backdrop blur 依赖
 * RenderEffect/BlurEffect，而 Robolectric 不渲染 RenderEffect、CI 模拟器又停在
 * API 30（低于 [AppBlur.MIN_BLUR_API]）——于是「选中了哪条渲染路径」这件事在 JVM
 * 既断言不了像素、也断言不了分支，只能靠真机眼睛看（PR #92 的截图基线 diff 为空即
 * 是该缺口的体现）。把判定从 [com.yumiru11.githubapp.core.designsystem.component.GlassSurface]
 * 里收敛出来成本函数后，三条降级路径（关开关 / 无 HazeState / API<31）与一条生效
 * 路径全部可单测断言；真机验收卡从此只需回答「模糊看起来对不对」这一件 JVM 做不到的事。
 *
 * 判定顺序（docs/ui-design.md §6.1/§6.2 拍板）：模糊生效必须**同时**满足
 * ① 用户开启毛玻璃（设置页 T24 总开关）② 上游提供了共享 HazeState（内容侧已挂
 * `hazeSource`，模糊有真实像素可采样）③ 系统支持 RenderEffect（API 31+）。
 * 任一不满足 → 纯半透明 scrim，视觉与 PR #92 之前的旧 AppBlur 降级方案一致。
 */
object GlassRenderPolicy {
    /**
     * @param blurEnabled 毛玻璃总开关；false 强制降级
     * @param hasHazeState 上游是否提供了
     *   [com.yumiru11.githubapp.core.designsystem.component.LocalHazeState]；
     *   false 表示内容侧没挂 `hazeSource`，模糊会每帧采样到空背景（真机「无效果」根因）
     * @param sdkInt 判定用 API level；默认取设备值，测试传显值以覆盖 [AppBlur.MIN_BLUR_API] 边界
     */
    fun resolve(
        blurEnabled: Boolean,
        hasHazeState: Boolean,
        sdkInt: Int = Build.VERSION.SDK_INT,
    ): GlassRenderMode =
        if (blurEnabled && hasHazeState && sdkInt >= AppBlur.MIN_BLUR_API) {
            GlassRenderMode.BackdropBlur
        } else {
            GlassRenderMode.TranslucentScrim
        }

    /**
     * 内容侧（source）门禁：是否应当给滚动内容挂 `hazeSource`。
     *
     * 与 [resolve] 同源判定（[GlassSurface] 的 effect 侧走 resolve），避免「栏挂了
     * hazeEffect 而内容侧漏挂 / 反之」这种两侧漂移——那是 issue #83 真机「无效果」
     * 的成因之一。调用方是同时持有栏与内容的容器（MainTabPager / HomeScreen /
     * MainActivity），它们手里没有 HazeState 的「有没有」问题（自己就是提供者），
     * 故此处 hasHazeState 恒为 true。
     */
    fun shouldAttachHazeSource(
        blurEnabled: Boolean,
        sdkInt: Int = Build.VERSION.SDK_INT,
    ): Boolean = resolve(blurEnabled = blurEnabled, hasHazeState = true, sdkInt = sdkInt) == GlassRenderMode.BackdropBlur
}
