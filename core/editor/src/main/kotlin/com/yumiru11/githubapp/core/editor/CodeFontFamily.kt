package com.yumiru11.githubapp.core.editor

import com.yumiru11.githubapp.core.datastore.model.CodeFont

/**
 * 代码字体偏好的**纯映射层**（T24 死设置接线：`CodeFont` → 字形家族）。
 *
 * 为什么只到这里为止：[CodeFont] → 平台 `Typeface` 的落地与「写进 Sora 实例」两件事都需要
 * Android 运行时（`Typeface` 在 mockable android.jar 下恒为 null，`CodeEditor` 要真实 View），
 * 而本仓 JaCoCo **不记录 Robolectric 覆盖**（classloader 不带 location，agent 跳过）——
 * 那部分代码若留在本文件，会在 diff 覆盖率门禁里被判成「新增未覆盖行」。
 * 故按本仓既有排除口径（`*EditorView*` 类名：Sora 视图/胶水层「单测不可达」）把落地放
 * [applyCodeEditorPreferences]（见 `CodeEditorView.kt`），本文件只留可在纯 JVM 断言的映射。
 *
 * Sora 侧 API 依据（Rosemoe sora-editor `editor:0.23.6`，`javap -p -c` 反编译 AAR
 * `classes.jar` 核实，非猜测）见 [applyCodeEditorPreferences] 的 KDoc。
 */
enum class CodeFontFamily {
    /** 等宽字体（[CodeFont.MONO]；代码默认，缩进对齐靠它） */
    MONOSPACE,

    /** 系统默认字体（[CodeFont.SYSTEM]；阅读为主、不在意列对齐时更顺眼） */
    PLATFORM_DEFAULT,
}

/**
 * [CodeFont] 偏好 → 平台字形家族（纯函数，可在纯 JVM 单测里断言「选哪一档」）。
 *
 * `when` 穷举：将来给 [CodeFont] 加值而漏改此处会**编译失败**，不会静默落回旧字体。
 */
fun codeFontFamily(font: CodeFont): CodeFontFamily =
    when (font) {
        CodeFont.MONO -> CodeFontFamily.MONOSPACE
        CodeFont.SYSTEM -> CodeFontFamily.PLATFORM_DEFAULT
    }
