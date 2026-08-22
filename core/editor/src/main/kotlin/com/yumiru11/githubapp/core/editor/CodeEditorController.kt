package com.yumiru11.githubapp.core.editor

import io.github.rosemoe.sora.text.Content
import io.github.rosemoe.sora.text.ContentListener
import io.github.rosemoe.sora.widget.CodeEditor

/**
 * 代码编辑器控制句柄（core:editor 对外暴露的窄接口，隔离 Sora 类型）。
 *
 * feature 层只经此句柄控制编辑器（搜索/跳转行/撤销重做/文本读写），不直接接触 Sora API——
 * 保证 core:editor 是唯一持有 Sora 依赖的模块（plan.md §10.1）。
 *
 * T22 扩展编辑能力（只读浏览场景下写方法无副作用）：文本监听 / 读写 / 撤销重做。
 */
class CodeEditorController
    internal constructor(
        private val editor: CodeEditor,
    ) {
        /**
         * 文本变更回调（编辑器内容每次变化后触发，含程序化 setText）。
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

        /** 销毁控制器，移除监听器防止内存泄漏。必须在 Compose onReset/onRelease 中调用。 */
        fun destroy() {
            editor.text.removeContentListener(contentListener)
        }

        /** 打开搜索模式（Sora 内置搜索界面：输入/高亮/上下条）。 */
        fun startSearch() {
            editor.beginSearchMode()
        }

        /**
         * 跳转到指定行（1 起；编辑器内部按 0 起行号处理）。
         * 越界行号由 Sora 内部收敛到有效范围。
         */
        fun jumpToLine(line: Int) {
            if (line <= 0) return
            editor.setSelection(line - 1, 0)
        }

        /** 当前全文。 */
        fun getText(): String = editor.text.toString()

        /** 替换全文（外部初始化/冲突重载用）。 */
        fun setText(text: String) {
            editor.setText(text)
        }

        /** 撤销（编辑模式 Sora 内置 undo 栈；只读模式无副作用）。 */
        fun undo() {
            editor.undo()
        }

        /** 重做（编辑模式 Sora 内置 undo 栈；只读模式无副作用）。 */
        fun redo() {
            editor.redo()
        }

        /** 当前是否可撤销。 */
        fun canUndo(): Boolean = editor.canUndo()

        /** 当前是否可重做。 */
        fun canRedo(): Boolean = editor.canRedo()
    }
