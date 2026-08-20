@file:Suppress("TooGenericExceptionCaught") // 网络/IO 错误统一兜底，T13 细化异常类型

package com.yumiru11.githubapp.feature.issue

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yumiru11.githubapp.feature.issue.data.IssueRepository
import com.yumiru11.githubapp.feature.issue.data.flipTaskListItem
import com.yumiru11.githubapp.feature.issue.data.toTimelineItem
import com.yumiru11.githubapp.feature.issue.model.Issue
import com.yumiru11.githubapp.feature.issue.model.IssueReactions
import com.yumiru11.githubapp.feature.issue.model.IssueState
import com.yumiru11.githubapp.feature.issue.model.IssueTimelineItem
import com.yumiru11.githubapp.feature.issue.model.IssueUser
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Issue 详情页 ViewModel（T13 读 + T14 写）。
 *
 * 从 SavedStateHandle 读取 [owner]/[repo]/[number] 导航参数，加载 Issue 详情与时间线。
 * 写操作（T14）统一模式：**乐观更新 → 失败回滚 + Snackbar 事件通道**——
 * 先本地更新 [IssueDetailUiState.Success]，再调仓库；失败恢复原状态并 emit
 * [IssueDetailEvent.ShowSnackbar]（UI 层 stringResource 本地化，ViewModel 不产英文文案）。
 */
@HiltViewModel
class IssueDetailViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val repository: IssueRepository,
    ) : ViewModel() {
        private val owner: String = checkNotNull(savedStateHandle["owner"])
        private val repo: String = checkNotNull(savedStateHandle["repo"])
        private val number: Int = checkNotNull(savedStateHandle["number"])

        private val _uiState = MutableStateFlow<IssueDetailUiState>(IssueDetailUiState.Loading)
        val uiState: StateFlow<IssueDetailUiState> = _uiState.asStateFlow()

        private val _events = MutableSharedFlow<IssueDetailEvent>(extraBufferCapacity = EVENT_BUFFER)
        val events: SharedFlow<IssueDetailEvent> = _events.asSharedFlow()

        init {
            loadIssueDetail()
        }

        fun retry() {
            _uiState.value = IssueDetailUiState.Loading
            loadIssueDetail()
        }

        private fun loadIssueDetail() {
            viewModelScope.launch {
                _uiState.value = IssueDetailUiState.Loading
                try {
                    val issue = repository.getIssue(owner, repo, number)
                    val timeline: List<IssueTimelineItem> = repository.timeline(owner, repo, number)
                    val writeContext = repository.getIssueWriteContext(owner, repo, number)
                    _uiState.value =
                        IssueDetailUiState.Success(
                            issue =
                                issue.copy(
                                    viewerPermission = writeContext.viewerPermission,
                                    graphqlId = writeContext.issueNodeId,
                                ),
                            timeline = timeline,
                            writeContext = writeContext,
                        )
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _uiState.value = IssueDetailUiState.Error(errorType = e.toIssueErrorType())
                }
            }
        }

        // ---- T14 写操作：乐观更新 → 失败回滚 + Snackbar ----

        /** 关闭 Issue（StateChip 乐观同步） */
        fun closeIssue() = setIssueState(IssueState.CLOSED)

        /** 重开 Issue（StateChip 乐观同步） */
        fun reopenIssue() = setIssueState(IssueState.OPEN)

        /** 编辑 Issue 标题/正文 */
        fun updateIssue(
            title: String,
            body: String,
        ) {
            val current = currentSuccess() ?: return
            val original = current.issue
            viewModelScope.launch {
                _uiState.value = current.copy(issue = original.copy(title = title, body = body))
                try {
                    val updated = repository.updateIssue(owner, repo, number, title = title, body = body)
                    _uiState.value = current.copy(issue = updated.withWriteContext(original))
                    emitSnackbar(IssueSnackbarMessage.ISSUE_UPDATED)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _uiState.value = current.copy(issue = original)
                    emitSnackbar(e.toIssueSnackbarMessage())
                }
            }
        }

        /** 新增评论（乐观插入临时项，成功后替换为服务端返回） */
        fun addComment(body: String) {
            val current = currentSuccess() ?: return
            val tempId = nextTempCommentId()
            val tempComment =
                IssueTimelineItem.Comment(
                    id = tempId,
                    author = current.writeContext?.viewerLogin?.let { IssueUser(login = it) },
                    body = body,
                )
            viewModelScope.launch {
                _uiState.value = current.copy(timeline = current.timeline + tempComment)
                try {
                    val created = repository.createComment(owner, repo, number, body)
                    val real = created.toTimelineItem()
                    val state = currentSuccess() ?: return@launch
                    _uiState.value = state.copy(timeline = state.timeline.map { if (it.id == tempId) real else it })
                    emitSnackbar(IssueSnackbarMessage.COMMENT_ADDED)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _uiState.value = current.copy(timeline = current.timeline)
                    emitSnackbar(e.toIssueSnackbarMessage())
                }
            }
        }

        /** 编辑评论 */
        fun updateComment(
            commentId: Long,
            body: String,
        ) {
            val current = currentSuccess() ?: return
            val originalTimeline = current.timeline
            viewModelScope.launch {
                _uiState.value =
                    current.copy(
                        timeline =
                            originalTimeline.map {
                                if (it.id == commentId && it is IssueTimelineItem.Comment) it.copy(body = body) else it
                            },
                    )
                try {
                    val updated = repository.updateComment(owner, repo, commentId, body)
                    val real = updated.toTimelineItem()
                    val state = currentSuccess() ?: return@launch
                    _uiState.value = state.copy(timeline = state.timeline.map { if (it.id == commentId) real else it })
                    emitSnackbar(IssueSnackbarMessage.COMMENT_UPDATED)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _uiState.value = current.copy(timeline = originalTimeline)
                    emitSnackbar(e.toIssueSnackbarMessage())
                }
            }
        }

        /** 删除评论 */
        fun deleteComment(commentId: Long) {
            val current = currentSuccess() ?: return
            val originalTimeline = current.timeline
            viewModelScope.launch {
                _uiState.value = current.copy(timeline = originalTimeline.filterNot { it.id == commentId })
                try {
                    repository.deleteComment(owner, repo, commentId)
                    emitSnackbar(IssueSnackbarMessage.COMMENT_DELETED)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _uiState.value = current.copy(timeline = originalTimeline)
                    emitSnackbar(e.toIssueSnackbarMessage())
                }
            }
        }

        /** 切换 Issue 反应（已反应 → 删除；未反应 → 新增） */
        fun toggleIssueReaction(content: String) {
            val current = currentSuccess() ?: return
            val itemId = current.issue.id
            val existing = current.myReactions[itemId]?.get(content)
            if (existing != null) {
                removeReaction(itemId, content, existing, isIssue = true)
            } else {
                addReaction(itemId, content, isIssue = true)
            }
        }

        /** 切换评论反应 */
        fun toggleCommentReaction(
            commentId: Long,
            content: String,
        ) {
            val current = currentSuccess() ?: return
            val existing = current.myReactions[commentId]?.get(content)
            if (existing != null) {
                removeReaction(commentId, content, existing, isIssue = false)
            } else {
                addReaction(commentId, content, isIssue = false)
            }
        }

        /** 任务列表 checkbox 反向同步（WebView bridge → GraphQL mutation） */
        fun toggleTaskListItem(
            index: Int,
            checked: Boolean,
        ) {
            val current = currentSuccess() ?: return
            val originalBody = current.issue.body ?: return
            val newBody = flipTaskListItem(originalBody, index, checked)
            if (newBody == originalBody) return
            val original = current
            viewModelScope.launch {
                _uiState.value = current.copy(issue = current.issue.copy(body = newBody))
                try {
                    val updated =
                        repository.toggleTaskListItem(
                            owner = owner,
                            repo = repo,
                            number = number,
                            nodeId = current.writeContext?.issueNodeId,
                            body = originalBody,
                            index = index,
                            checked = checked,
                        )
                    val state = currentSuccess() ?: return@launch
                    _uiState.value = state.copy(issue = updated.withWriteContext(original.issue))
                    emitSnackbar(IssueSnackbarMessage.TASK_LIST_UPDATED)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _uiState.value = original
                    emitSnackbar(e.toIssueSnackbarMessage())
                }
            }
        }

        // ---- 私有实现 ----

        private fun setIssueState(newState: IssueState) {
            val current = currentSuccess() ?: return
            val original = current.issue
            viewModelScope.launch {
                _uiState.value = current.copy(issue = original.copy(state = newState))
                try {
                    val updated = repository.updateIssue(owner, repo, number, state = newState.toRaw())
                    _uiState.value = current.copy(issue = updated.withWriteContext(original))
                    emitSnackbar(
                        if (newState == IssueState.CLOSED) {
                            IssueSnackbarMessage.ISSUE_CLOSED
                        } else {
                            IssueSnackbarMessage.ISSUE_REOPENED
                        },
                    )
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _uiState.value = current.copy(issue = original)
                    emitSnackbar(e.toIssueSnackbarMessage())
                }
            }
        }

        private fun addReaction(
            itemId: Long,
            content: String,
            isIssue: Boolean,
        ) {
            val current = currentSuccess() ?: return
            val original = current
            viewModelScope.launch {
                _uiState.value = current.withReactionDelta(itemId, content, delta = 1)
                try {
                    val reaction =
                        if (isIssue) {
                            repository.addIssueReaction(owner, repo, number, content)
                        } else {
                            repository.addCommentReaction(owner, repo, itemId, content)
                        }
                    val state = currentSuccess() ?: return@launch
                    val mine = state.myReactions[itemId].orEmpty() + (content to reaction.id)
                    _uiState.value = state.copy(myReactions = state.myReactions + (itemId to mine))
                    emitSnackbar(IssueSnackbarMessage.REACTION_ADDED)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _uiState.value = original
                    emitSnackbar(e.toIssueSnackbarMessage())
                }
            }
        }

        private fun removeReaction(
            itemId: Long,
            content: String,
            reactionId: Long,
            isIssue: Boolean,
        ) {
            val current = currentSuccess() ?: return
            val original = current
            viewModelScope.launch {
                _uiState.value = current.withReactionDelta(itemId, content, delta = -1)
                try {
                    if (isIssue) {
                        repository.removeIssueReaction(owner, repo, number, reactionId)
                    } else {
                        repository.removeCommentReaction(owner, repo, itemId, reactionId)
                    }
                    val state = currentSuccess() ?: return@launch
                    val mine = state.myReactions[itemId].orEmpty() - content
                    _uiState.value = state.copy(myReactions = state.myReactions + (itemId to mine))
                    emitSnackbar(IssueSnackbarMessage.REACTION_REMOVED)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _uiState.value = original
                    emitSnackbar(e.toIssueSnackbarMessage())
                }
            }
        }

        private fun currentSuccess(): IssueDetailUiState.Success? = _uiState.value as? IssueDetailUiState.Success

        private suspend fun emitSnackbar(message: IssueSnackbarMessage) {
            _events.emit(IssueDetailEvent.ShowSnackbar(message))
        }

        private var tempIdCounter = 0L

        /** 乐观临时评论 id：Long.MIN_VALUE 起递增，避免与真实 id/时间线合成负 id 冲突 */
        private fun nextTempCommentId(): Long = Long.MIN_VALUE + tempIdCounter++

        private companion object {
            const val EVENT_BUFFER = 8
        }
    }

/** 仓库返回的 [Issue] 合并写上下文字段（REST 响应不含 viewerPermission/graphqlId） */
private fun Issue.withWriteContext(original: Issue): Issue =
    copy(
        viewerPermission = original.viewerPermission,
        graphqlId = original.graphqlId,
    )

/** 乐观更新反应计数（issue 或 comment 的 reactions） */
private fun IssueDetailUiState.Success.withReactionDelta(
    itemId: Long,
    content: String,
    delta: Int,
): IssueDetailUiState.Success {
    val update: (IssueReactions) -> IssueReactions = { reactions ->
        reactions.copy(
            totalCount = (reactions.totalCount + delta).coerceAtLeast(0),
            counts = reactions.counts + (content to ((reactions.counts[content] ?: 0) + delta).coerceAtLeast(0)),
        )
    }
    return if (itemId == issue.id) {
        copy(issue = issue.copy(reactions = update(issue.reactions)))
    } else {
        copy(
            timeline =
                timeline.map {
                    if (it.id == itemId && it is IssueTimelineItem.Comment) {
                        it.copy(reactions = update(it.reactions))
                    } else {
                        it
                    }
                },
        )
    }
}
