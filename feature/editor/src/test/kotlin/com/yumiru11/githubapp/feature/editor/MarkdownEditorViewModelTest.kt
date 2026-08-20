package com.yumiru11.githubapp.feature.editor

import com.yumiru11.githubapp.core.editor.MarkdownToolbarAction
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Markdown 编辑器 ViewModel 测试（T21）。
 *
 * 覆盖状态管理：初始内容、文本同步、预览切换、工具栏委托（控制器未就绪时安全 no-op）。
 */
class MarkdownEditorViewModelTest {
    @Test
    fun initialState_initialContentProvided_textAndEditTab() {
        val viewModel = MarkdownEditorViewModel(initialContent = "# Hello")

        assertEquals("# Hello", viewModel.uiState.value.text)
        assertFalse(viewModel.uiState.value.isPreview)
    }

    @Test
    fun initialState_noInitialContent_textEmpty() {
        val viewModel = MarkdownEditorViewModel()

        assertEquals("", viewModel.uiState.value.text)
    }

    @Test
    fun onTextChanged_updatesTextState() {
        val viewModel = MarkdownEditorViewModel(initialContent = "a")

        viewModel.onTextChanged("ab")

        assertEquals("ab", viewModel.uiState.value.text)
    }

    @Test
    fun togglePreview_fromEditToPreview_flipsFlag() {
        val viewModel = MarkdownEditorViewModel()

        viewModel.togglePreview()

        assertTrue(viewModel.uiState.value.isPreview)
    }

    @Test
    fun togglePreview_twice_returnsToEdit() {
        val viewModel = MarkdownEditorViewModel()

        viewModel.togglePreview()
        viewModel.togglePreview()

        assertFalse(viewModel.uiState.value.isPreview)
    }

    @Test
    fun togglePreview_preservesText() {
        val viewModel = MarkdownEditorViewModel(initialContent = "keep me")
        viewModel.onTextChanged("keep me edited")

        viewModel.togglePreview()

        assertEquals("keep me edited", viewModel.uiState.value.text)
        assertTrue(viewModel.uiState.value.isPreview)
    }

    @Test
    fun applySyntax_controllerNotReady_isSafeNoOp() =
        runTest {
            val viewModel = MarkdownEditorViewModel(initialContent = "text")

            // 控制器未注入（编辑器未创建）时调用不崩溃、不改状态
            viewModel.applySyntax(MarkdownToolbarAction.BOLD)
            viewModel.undo()
            viewModel.redo()

            assertEquals("text", viewModel.uiState.value.text)
        }
}
