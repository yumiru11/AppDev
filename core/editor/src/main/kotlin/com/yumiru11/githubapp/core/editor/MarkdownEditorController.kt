package com.yumiru11.githubapp.core.editor

import io.github.rosemoe.sora.text.Content
import io.github.rosemoe.sora.text.ContentListener
import io.github.rosemoe.sora.widget.CodeEditor

/**
 * Markdown 编辑器控制句柄（core:editor 对外暴露的窄接口，隔离 Sora 类型）。
 *
 * 编辑模式（T21）扩展自 [CodeEditorController] 的只读控制：
 * - 工具栏语法应用：[applySyntax] 读取当前选区 → [MarkdownSyntaxFormatter] 计算
 *   新文本/新选区 → 写回编辑器（setText + setSelection）
 * - 撤销/重做：Sora 内置 undo 栈（编辑模式开启）
 * - 文本变更监听：[onTextChanged] 回调（编辑器内容变化 → 上层同步预览/状态）
 *
 * feature 层只经此句柄控制编辑器，不直接接触 Sora API——
 * 保证 core:editor 是唯一持有 Sora 依赖的模块（plan.md §10.1）。
 */
class MarkdownEditorController
    internal constructor(
        private val editor: CodeEditor,
    ) {
        /**
         * 文本变更回调（编辑器内容每次变化后触发，含工具栏语法应用）。
         * 由宿主在重组时更新（rememberUpdatedState 语义），避免闭包捕获过期状态。
         */
        var onTextChanged: (String) -> Unit = {}
            internal set

        private val contentListener =
            object : ContentListener {
                override fun beforeReplace(content: Content) = Unit

                override fun afterInsert(
                    content: Content,
                    startLine: Int,
                    startColumn: Int,
                    endLine: Int,
                    endColumn: Int,
                    insertedContent: CharSequence,
                ) {
                    onTextChanged(editor.text.toString())
                }

                override fun afterDelete(
                    content: Content,
                    startLine: Int,
                    startColumn: Int,
                    endLine: Int,
                    endColumn: Int,
                    deletedContent: CharSequence,
                ) {
                    onTextChanged(editor.text.toString())
                }
            }

        init {
            editor.text.addContentListener(contentListener)
        }

        /** 销毁控制器，移除监听器防止内存泄漏。必须在 Compose onDispose 中调用。 */
        fun destroy() {
            editor.text.removeContentListener(contentListener)
        }

        /** 应用工具栏动作（读取当前选区 → 语法格式化 → 写回 + 定位新选区）。 */
        fun applySyntax(action: MarkdownToolbarAction) {
            val text = editor.text.toString()
            val start = editor.cursor.getLeft()
            val end = editor.cursor.getRight()
            val result = MarkdownSyntaxFormatter.apply(action, text, start, end)
            editor.setText(result.text)
            editor.setSelection(result.selectionStart, result.selectionEnd)
        }

        /** 撤销（Sora 内置 undo 栈）。 */
        fun undo() {
            editor.undo()
        }

        /** 重做。 */
        fun redo() {
            editor.redo()
        }

        /** 当前是否可撤销。 */
        fun canUndo(): Boolean = editor.canUndo()

        /** 当前是否可重做。 */
        fun canRedo(): Boolean = editor.canRedo()

        /** 当前全文。 */
        fun getText(): String = editor.text.toString()

        /** 替换全文（外部初始化/重置用）。 */
        fun setText(text: String) {
            editor.setText(text)
        }
    }
