@file:Suppress("TooGenericExceptionCaught", "SwallowedException")
// - TooGenericExceptionCaught：#mention 候选是纯增强读路径，未认证 / 无权限（私有仓库协作者）
//   / 网络失败在语义上没有区别——都只需「没候选」，不分类。
// - SwallowedException：候选读失败按**有意降级**吞掉——编辑正文本身不依赖候选，
//   为它弹错误提示反而打扰用户；编辑器的实际失败面由草稿保存与工具栏路径承担。

package com.yumiru11.githubapp.feature.editor

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yumiru11.githubapp.core.editor.assembleMentionCandidates
import com.yumiru11.githubapp.core.githubdata.repository.RepositoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Markdown 编辑器的 `@mention` 候选（SPEC-3）。
 *
 * 候选源 = 当前仓库协作者（REST `GET /repos/{owner}/{repo}/collaborators`，见
 * [RepositoryRepository.listCollaborators]）。owner/repo 来自导航 route 参数
 * （`AppRoute.Editor`，经 [SavedStateHandle] 注入）——无仓库语境（空串）时直接无候选。
 *
 * 与 [MarkdownEditorViewModel]（正文/预览/工具栏状态）分开：本 VM 只承担一个
 * 异步读 + 一个列表，且需要 Hilt 注入仓库；正文 VM 保持无依赖构造（既有测试不变）。
 *
 * 归一化（去重/排序/截断）委托 [assembleMentionCandidates]——与 Issue 评论输入
 * 共用同一份规则。
 */
@HiltViewModel
class EditorMentionsViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val repository: RepositoryRepository,
    ) : ViewModel() {
        private val owner: String = savedStateHandle.get<String>("owner").orEmpty()
        private val repo: String = savedStateHandle.get<String>("repo").orEmpty()

        private val _candidates = MutableStateFlow<List<String>>(emptyList())

        /** 补全候选（稳定有序：字典序、已去重、≤ 上限）。加载失败 = 空列表。 */
        val candidates: StateFlow<List<String>> = _candidates.asStateFlow()

        init {
            loadCandidates()
        }

        private fun loadCandidates() {
            if (owner.isBlank() || repo.isBlank()) return
            viewModelScope.launch {
                val logins =
                    try {
                        repository.listCollaborators(owner, repo).map { it.login }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (t: Throwable) {
                        emptyList()
                    }
                _candidates.value = assembleMentionCandidates(logins)
            }
        }
    }
