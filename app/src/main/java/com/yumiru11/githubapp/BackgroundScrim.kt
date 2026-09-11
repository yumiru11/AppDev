package com.yumiru11.githubapp

/**
 * 全局背景图的压暗/压白换算（#167 / UI04，ui-design §7.4）。
 *
 * **为什么单独抽成纯函数**：这是本特性里唯一有"规则"的部分 —— §7.4 拍板了
 * 「浅色下背景变白、深色下背景变黑、深色自动降低亮度」，而实际观感完全由这几个
 * 系数决定。抽出来就能在 JVM 里断言（例如"不透明度越高蒙版越薄""深色永远比浅色更暗"），
 * 不必靠截图上肉眼看。
 *
 * 换算口径：
 * - 蒙版 alpha = 1 - 有效不透明度。alpha 越大 → 底色越"实"（图越淡）
 * - 浅色：底色白，有效不透明度 = opacity
 * - 深色：底色黑，且**有效不透明度再乘 0.6**（§7.4「深色自动降低亮度」）——
 *   深色下同样的不透明度会显得更亮更抢眼，压一档才与浅色观感一致
 */
object BackgroundScrim {
    /** 深色下的额外压暗系数（推荐值起步，之后看模拟器截图再调） */
    const val DARK_DIM_FACTOR: Float = 0.6f

    /** 蒙版 alpha（0 = 完全露出背景图，1 = 完全盖住） */
    fun scrimAlpha(
        opacity: Float,
        darkTheme: Boolean,
    ): Float {
        val effective = if (darkTheme) opacity * DARK_DIM_FACTOR else opacity
        return (1f - effective).coerceIn(0f, 1f)
    }
}
