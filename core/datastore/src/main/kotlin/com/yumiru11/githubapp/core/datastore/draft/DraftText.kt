package com.yumiru11.githubapp.core.datastore.draft

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 「一段可编辑文本 + 它的草稿」状态对象（Compose 无关，可直接 JVM 单测）。
 *
 * 把 `RepoFilesViewModel` 里验证过的**三重恢复守卫**下沉为可复用状态，供评论 Sheet / 新建表单 /
 * 编辑对话框共用，避免每个编辑器各写一遍：
 * 1. **读不到草稿 / 读失败** → 保持基线（[DraftAutoSaver.load] 已把 IO 失败降级为 null）；
 * 2. **草稿 == 基线** → 不做"假恢复"（内容与既有正文一模一样，提示反而误导）；
 * 3. **用户已开始输入**（当前文本 != 基线）→ 绝不覆盖（读盘是异步的，回填可能晚于首次输入）。
 *
 * 写侧语义：[onChanged] 防抖落盘、回到基线即清草稿；[saveNow] 关闭时立即落盘；
 * [discard] 清草稿并把文本复位为基线（提交成功 / 用户主动丢弃）。
 *
 * @param key 草稿键（由 [DraftTargets] 工厂算出的稳定身份）
 * @param baseline 进入编辑时的基线文本（新评论/新表单 = ""；编辑既有内容 = 原正文）
 * @param scope 恢复读盘的作用域（生产传 `viewModelScope`）
 */
class DraftText(
    val key: DraftKey,
    val baseline: String,
    private val saver: DraftAutoSaver,
    scope: CoroutineScope,
) {
    private val _text = MutableStateFlow(baseline)

    /** 当前文本（UI 唯一事实源；恢复完成后即为草稿内容）。 */
    val text: StateFlow<String> = _text.asStateFlow()

    init {
        scope.launch {
            val draft = saver.load(key) ?: return@launch
            if (draft == baseline || _text.value != baseline) return@launch
            _text.value = draft
        }
    }

    /** 文本变更（编辑器每次输入）：防抖落盘；回到基线则清草稿（无未提交内容）。 */
    fun onChanged(value: String) {
        _text.value = value
        if (value == baseline) saver.discard(key) else saver.scheduleSave(key, value)
    }

    /** 立即落盘（关闭编辑器/对话框时调用；等于基线则什么都不写）。 */
    fun saveNow() {
        if (_text.value != baseline) saver.saveNow(key, _text.value)
    }

    /** 丢弃草稿并复位为基线（提交成功 / 用户主动丢弃）。 */
    fun discard() {
        saver.discard(key)
        _text.value = baseline
    }
}
