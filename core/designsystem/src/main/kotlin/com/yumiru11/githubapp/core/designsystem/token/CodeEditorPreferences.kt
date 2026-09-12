package com.yumiru11.githubapp.core.designsystem.token

import androidx.compose.runtime.staticCompositionLocalOf
import com.yumiru11.githubapp.core.datastore.model.CodeFont

/**
 * 代码编辑器外观偏好快照（T24 设置页「代码字体」/「行号」，供 Sora 代码视图与编辑器的消费点读取）。
 *
 * 为什么是一处 CompositionLocal 而不是逐屏传参：消费点分散在 `core:editor` 的两个视图
 * （`CodeEditorView` / `MarkdownEditorView`）与**四个**宿主（仓库文件查看器 CODE 态与
 * Markdown Source 态、文件编辑页、Markdown 编辑页、issue 评论输入 Sheet 的
 * MarkdownComposer），而 issue/editor 侧连 ViewModel 都不持有偏好
 * （MarkdownEditorViewModel 由 `initializer` 手工构造，无 Hilt 注入点）。逐层透传要在
 * 4 个 feature 里各加一遍管线，与本仓既有的 [LocalIconStyle] / [LocalGlassSettings] /
 * [LocalStaggerEnabled] 同款「偏好 → CompositionLocal → 消费点」约定相悖。
 *
 * 默认值与 `UserPreferencesRepository` 的默认值一致（[CodeFont.MONO] + 行号开），
 * 故截图测试 / `@Preview` / 未注入偏好的宿主（如组件级测试）行为与接线前一致。
 */
data class CodeEditorPreferences(
    /** 代码字体（设置页「代码字体」） */
    val codeFont: CodeFont = CodeFont.MONO,
    /** 是否显示行号（设置页「行号」） */
    val lineNumbers: Boolean = true,
)

/**
 * 全局代码编辑器偏好（由 app 层 `AppThemeHost` 从 `UserPreferencesRepository.codeFont` /
 * `codeLineNumbers` 注入）。
 *
 * static：值是偏好快照，切换时消费子树整体重组（不需要逐帧读取；同 [LocalIconStyle] 先例）。
 */
val LocalCodeEditorPreferences = staticCompositionLocalOf { CodeEditorPreferences() }
