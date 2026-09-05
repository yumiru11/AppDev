package com.yumiru11.githubapp.core.designsystem.token

/**
 * 玻璃层的渲染模式（issue #83）。由 [GlassRenderPolicy.resolve] 判定，二选一。
 *
 * 单独成类型（而非返回 Boolean）的用意：降级原因有三种（用户关开关 / 无 HazeState /
 * API<31），但**视觉结果只有一种**（纯半透明 surface 层）。调用方只关心「挂不挂 hazeEffect」，
 * 故把「为什么降级」留在策略函数内部，接口只暴露「降级成什么」。
 */
enum class GlassRenderMode {
    /** Haze `hazeEffect`（RenderEffect backdrop blur）：模糊**栏背后**的滚动内容，栏内文字/图标保持锐利 */
    BackdropBlur,

    /** 纯半透明 surface 层（[AppBlur.SCRIM_ALPHA] alpha）：不做 bitmap 模糊，性能优先（§6.2） */
    TranslucentScrim,
}
