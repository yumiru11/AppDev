package com.yumiru11.githubapp.feature.editor

import androidx.lifecycle.ViewModel
import com.yumiru11.githubapp.core.editor.MarkdownEditorController
import com.yumiru11.githubapp.core.editor.MarkdownToolbarAction
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Markdown 编辑器 UI 状态（T21）。 */
data class MarkdownEditorUiState(
    /** 当前文档全文（编辑器文本变更时同步）。 */
    val text: String = "",
    /** 是否处于预览 Tab（false = 编辑 Tab）。 */
    val isPreview: Boolean = false,
)

/**
 * Markdown 编辑器 ViewModel（T21，plan.md §7.1）。
 *
 * - 文本状态：编辑器是文本唯一事实源，[onTextChanged] 同步到 [uiState]（预览/状态用）
 * - 预览切换：[togglePreview] 翻转编辑/预览 Tab
 * - 工具栏/撤销重做：委托给 [MarkdownEditorController]（编辑器就绪后注入）
 */
class MarkdownEditorViewModel(
    initialContent: String = "",
) : ViewModel() {
    private val _uiState = MutableStateFlow(MarkdownEditorUiState(text = initialContent))
    val uiState: StateFlow<MarkdownEditorUiState> = _uiState.asStateFlow()

    private var controller: MarkdownEditorController? = null

    /** 编辑器控制句柄就绪回调（视图在编辑器创建后注入）。 */
    fun onEditorReady(controller: MarkdownEditorController) {
        this.controller = controller
    }

    /** 编辑器文本变更同步（预览/状态）。 */
    fun onTextChanged(text: String) {
        _uiState.update { it.copy(text = text) }
    }

    /** 切换编辑/预览 Tab。 */
    fun togglePreview() {
        _uiState.update { it.copy(isPreview = !it.isPreview) }
    }

    /** 应用工具栏语法动作（委托编辑器控制句柄）。 */
    fun applySyntax(action: MarkdownToolbarAction) {
        controller?.applySyntax(action)
    }

    /** 撤销。 */
    fun undo() {
        controller?.undo()
    }

    /** 重做。 */
    fun redo() {
        controller?.redo()
    }
}
