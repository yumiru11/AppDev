package com.yumiru11.githubapp.core.editor

import io.github.rosemoe.sora.widget.CodeEditor

/**
 * 代码编辑器控制句柄（core:editor 对外暴露的窄接口，隔离 Sora 类型）。
 *
 * feature 层只经此句柄控制编辑器（搜索/跳转行），不直接接触 Sora API——
 * 保证 core:editor 是唯一持有 Sora 依赖的模块（plan.md §10.1）。
 */
class CodeEditorController
    internal constructor(
        private val editor: CodeEditor,
    ) {
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
    }
