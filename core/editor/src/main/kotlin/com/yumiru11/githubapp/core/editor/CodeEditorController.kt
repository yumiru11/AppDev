package com.yumiru11.githubapp.core.editor

import io.github.rosemoe.sora.event.PublishSearchResultEvent
import io.github.rosemoe.sora.event.Unsubscribe
import io.github.rosemoe.sora.text.Content
import io.github.rosemoe.sora.text.ContentListener
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.EditorSearcher

/**
 * 代码编辑器控制句柄（core:editor 对外暴露的窄接口，隔离 Sora 类型）。
 *
 * feature 层只经此句柄控制编辑器（查找/跳转行/撤销重做/文本读写），不直接接触 Sora API——
 * 保证 core:editor 是唯一持有 Sora 依赖的模块（plan.md §10.1）。
 *
 * T22 扩展编辑能力（只读浏览场景下写方法无副作用）：文本监听 / 读写 / 撤销重做。
 * #166（UI14）扩展文件内查找：查询高亮 / 上下一处 / 清除（状态机见 [FileFindState]）。
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

        /**
         * 文件内查找结果回调（Sora 后台扫描完成 / 清除后触发；回灌权威 [FileFindState]）。
         *
         * 查询词**异步**生效：调用 [findText] 当帧拿不到匹配数，宿主须据此回调刷新计数
         * （不是同步返回值，避免读到上一查询词的陈旧结果）。
         */
        var onFindResult: (FileFindState) -> Unit = {}

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

        /** Sora 侧当前查询词镜像（`EditorSearcher` 没有 pattern getter，只能自行记录）。 */
        private var findQuery: String = ""

        // Sora 搜索线程完成 / 清除时派发结果事件 → 回灌宿主（销毁时退订，见 destroy）
        private val findReceipt =
            editor.subscribeEvent(PublishSearchResultEvent::class.java) { _: PublishSearchResultEvent, _: Unsubscribe ->
                onFindResult(currentFindState())
            }

        /** 销毁控制器，移除监听器防止内存泄漏。必须在 Compose onReset/onRelease 中调用。 */
        fun destroy() {
            editor.text.removeContentListener(contentListener)
            findReceipt.unsubscribe()
            findQuery = ""
        }

        /**
         * 文件内查找（#166 / UI14）：设置查询词并高亮**全部**匹配项。
         *
         * Sora 0.23.6 行为（已核对 AAR 字节码，见 PR 说明）：
         * - [EditorSearcher.search] 空串抛 `IllegalArgumentException` → 空查询词走 [clearFindText]
         * - 扫描在后台线程执行，完成后主线程派发 [PublishSearchResultEvent]（本控制器订阅 →
         *   [onFindResult] 回灌权威计数）；**不自动选中**首处匹配，由宿主在结果到位后补一跳
         * - 匹配高亮由 Searcher 绘制，清除依据是 pattern/结果表（[clearFindText]）
         *
         * @param query 查询词（原样匹配，忽略大小写；空串等价于清除）
         */
        fun findText(query: String) {
            if (query.isEmpty()) {
                clearFindText()
                return
            }
            findQuery = query
            // 环形跳转（末项 → 首项）：Sora 构造期默认即 true，显式声明意图防上游改默认值
            editor.searcher.setCyclicJumping(true)
            editor.searcher.search(query, FIND_SEARCH_OPTIONS)
        }

        /**
         * 跳到下一处匹配（循环）并返回回灌后的权威状态。
         * 无查询词时返回空状态（Sora 的 `gotoNext` 在无查询词时抛 `IllegalStateException`）。
         */
        fun findNext(): FileFindState {
            if (editor.searcher.hasQuery()) editor.searcher.gotoNext()
            return currentFindState()
        }

        /** 跳到上一处匹配（循环）并返回回灌后的权威状态。 */
        fun findPrevious(): FileFindState {
            if (editor.searcher.hasQuery()) editor.searcher.gotoPrevious()
            return currentFindState()
        }

        /**
         * 清除查询词与**全部匹配高亮**（关闭查找面板 / 更换文件内容时调用；UI14 验收项）。
         * 无查询词时为空操作（Sora 的 `stopSearch` 会派发结果事件，宿主收到空状态）。
         */
        fun clearFindText() {
            findQuery = ""
            if (editor.searcher.hasQuery()) editor.searcher.stopSearch()
        }

        /**
         * 当前查找快照（查询词镜像 + Sora 匹配表的权威计数与序号）。
         *
         * 读取前必须判 `hasQuery()`：Sora 的 `getMatchedPositionCount` /
         * `getCurrentMatchedPositionIndex` 内部 `checkState()` 在无查询词时抛
         * `IllegalStateException("pattern not set")`。
         */
        fun currentFindState(): FileFindState {
            val searcher = editor.searcher
            if (findQuery.isEmpty() || !searcher.hasQuery()) return FileFindState()
            return FileFindState(
                query = findQuery,
                matchCount = searcher.matchedPositionCount,
                currentMatchIndex = searcher.currentMatchedPositionIndex,
            )
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

/**
 * 文件内查找选项：字面量匹配（非正则 / 非全词）+ 忽略大小写
 * （GitHub 网页代码视图与主流编辑器默认一致；大小写开关不在本票范围）。
 */
private val FIND_SEARCH_OPTIONS =
    EditorSearcher.SearchOptions(EditorSearcher.SearchOptions.TYPE_NORMAL, true)
