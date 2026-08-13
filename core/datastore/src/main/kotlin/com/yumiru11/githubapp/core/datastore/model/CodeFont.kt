package com.yumiru11.githubapp.core.datastore.model

/**
 * 代码字体偏好（T24 设置页「代码字体」，供代码浏览/编辑器消费）。
 *
 * 持久化为 name 字符串，未知值回退 [MONO]。
 * 本票仅持久化 + 设置页展示；Sora Editor 消费接线随编辑器功能票。
 */
enum class CodeFont {
    /** 等宽字体（代码默认） */
    MONO,

    /** 系统默认字体 */
    SYSTEM,
}
