package com.yumiru11.githubapp.core.datastore.model

/**
 * 应用图标风格（T24 设置页「图标风格」）。
 *
 * 持久化为 name 字符串，未知值回退 [ROUNDED]。
 * 对应 Material Symbols 三档风格；docs/ui-design.md §5 默认 rounded。
 */
enum class IconStyle {
    /** 线性描边（Material Symbols Outlined） */
    OUTLINED,

    /** 圆润（Material Symbols Rounded，默认，ui-design §5） */
    ROUNDED,

    /** 填充（Material Symbols Filled） */
    FILLED,
}
