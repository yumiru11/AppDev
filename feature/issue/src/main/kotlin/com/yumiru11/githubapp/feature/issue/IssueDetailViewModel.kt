@file:Suppress("TooGenericExceptionCaught", "SwallowedException")
// - TooGenericExceptionCaught：网络/IO 错误统一兜底，T13 细化异常类型
// - SwallowedException：订阅态探测 / Assignee-Milestone 候选项加载失败按保守值降级（false/空列表），
//   异常链在写操作失败路径经 Snackbar 事件通道透出，读路径降级属有意设计（#163 L01/L02）

package com.yumiru11.githubapp.feature.issue

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yumiru11.githubapp.core.datastore.draft.DraftAutoSaver
import com.yumiru11.githubapp.core.datastore.draft.DraftTargets
import com.yumiru11.githubapp.core.datastore.draft.DraftText
import com.yumiru11.githubapp.feature.issue.data.IssueRepository
import com.yumiru11.githubapp.feature.issue.data.flipTaskListItem
import com.yumiru11.githubapp.feature.issue.data.toTimelineItem
import com.yumiru11.githubapp.feature.issue.model.Issue
import com.yumiru11.githubapp.feature.issue.model.IssueLabel
import com.yumiru11.githubapp.feature.issue.model.IssueReactions
import com.yumiru11.githubapp.feature.issue.model.IssueState
import com.yumiru11.githubapp.feature.issue.model.IssueTimelineItem
import com.yumiru11.githubapp.feature.issue.model.IssueUser
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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
@Suppress("TooManyFunctions") // 详情页聚合读 + T14 写操作 + #163 订阅/元数据编辑（26 个方法），拆类会牺牲单页状态机内聚
class IssueDetailViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val repository: IssueRepository,
        private val drafts: DraftAutoSaver,
    ) : ViewModel() {
        private val owner: String = checkNotNull(savedStateHandle["owner"])
        private val repo: String = checkNotNull(savedStateHandle["repo"])
        private val number: Int = checkNotNull(savedStateHandle["number"])

        private val _uiState = MutableStateFlow<IssueDetailUiState>(IssueDetailUiState.Loading)
        val uiState: StateFlow<IssueDetailUiState> = _uiState.asStateFlow()

        private val _events = MutableSharedFlow<IssueDetailEvent>(extraBufferCapacity = EVENT_BUFFER)
        val events: SharedFlow<IssueDetailEvent> = _events.asSharedFlow()

        /** #163 L02：元数据编辑 Sheet 状态（null = Sheet 关闭） */
        private val _editState = MutableStateFlow<IssueEditUiState?>(null)
        val editState: StateFlow<IssueEditUiState?> = _editState.asStateFlow()

        /** 新评论正文草稿（进程被杀后重新打开评论 Sheet 自动恢复）。 */
        val commentDraft: DraftText =
            DraftText(DraftTargets.issueComment(owner, repo, number), baseline = "", saver = drafts, scope = viewModelScope)

        /** 编辑 Issue 正文草稿（打开对话框时按当前正文建基线；null = 未打开）。 */
        private val _editIssueDraft = MutableStateFlow<DraftText?>(null)
        val editIssueDraft: StateFlow<DraftText?> = _editIssueDraft.asStateFlow()

        /** 编辑既有评论正文草稿（null = 未打开）。 */
        private val _editCommentDraft = MutableStateFlow<DraftText?>(null)
        val editCommentDraft: StateFlow<DraftText?> = _editCommentDraft.asStateFlow()

        /** 打开「编辑 Issue」对话框：以当前正文为基线恢复草稿。 */
        fun openEditIssue(baseBody: String) {
            _editIssueDraft.value = DraftText(DraftTargets.issueEdit(owner, repo, number), baseBody, drafts, viewModelScope)
        }

        /** 关闭「编辑 Issue」对话框（未提交）：当前文本立即落盘，不丢编辑。 */
        fun closeEditIssue() {
            _editIssueDraft.value?.saveNow()
            _editIssueDraft.value = null
        }

        /** 打开「编辑评论」对话框：以当前评论正文为基线恢复草稿。 */
        fun openEditComment(
            commentId: Long,
            baseBody: String,
        ) {
            _editCommentDraft.value = DraftText(DraftTargets.commentEdit(owner, repo, commentId), baseBody, drafts, viewModelScope)
        }

        /** 关闭「编辑评论」对话框（未提交）：当前文本立即落盘。 */
        fun closeEditComment() {
            _editCommentDraft.value?.saveNow()
            _editCommentDraft.value = null
        }

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
                            // #163 L01：订阅态探测（失败按未订阅降级，不阻塞详情页）
                            isSubscribed = isSubscribedSafely(),
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
                    _editIssueDraft.value?.discard()
                    _editIssueDraft.value = null
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
                    commentDraft.discard()
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
                    _editCommentDraft.value?.discard()
                    _editCommentDraft.value = null
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

        // ---- #163 L01：Issue 级订阅 ----

        /**
         * 切换订阅态：乐观翻转 → 成功保持 / 失败回滚 + Snackbar。
         * 未登录（[IssueDetailUiState.Success.canSubscribe] = false）或写操作进行中时忽略。
         */
        fun toggleSubscription() {
            val current = currentSuccess() ?: return
            if (!current.canSubscribe || current.subscriptionPending) return
            val target = !current.isSubscribed
            viewModelScope.launch {
                _uiState.value = current.copy(isSubscribed = target, subscriptionPending = true)
                try {
                    if (target) {
                        repository.subscribe(owner, repo, number)
                    } else {
                        repository.unsubscribe(owner, repo, number)
                    }
                    val state = currentSuccess() ?: return@launch
                    _uiState.value = state.copy(isSubscribed = target, subscriptionPending = false)
                    emitSnackbar(
                        if (target) IssueSnackbarMessage.SUBSCRIBED else IssueSnackbarMessage.UNSUBSCRIBED,
                    )
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _uiState.value = current.copy(isSubscribed = current.isSubscribed, subscriptionPending = false)
                    emitSnackbar(e.toIssueSnackbarMessage())
                }
            }
        }

        // ---- #163 L02：Labels / Assignees / Milestone 编辑 ----

        /**
         * 打开元数据编辑 Sheet：以当前 Issue 的三字段为初始选择，并行加载候选项。
         *
         * 权限闸门 = [IssueDetailUiState.Success.canManageMeta]（TRIAGE+，含 WRITE/MAINTAIN/ADMIN）：
         * 无权限时**不发起任何候选项请求**（不加载编辑入口）。
         * 加载失败策略：标签（编辑主体）失败 → Sheet 错误态可重试；
         * Assignees 404（无权限）/ Milestones 失败 → 降级为空候选，不阻塞标签编辑。
         */
        fun openMetaEditor() {
            val current = currentSuccess() ?: return
            if (!current.canManageMeta) return
            val issue = current.issue
            _editState.value =
                IssueEditUiState(
                    selectedLabels = issue.labels.map { it.name }.toSet(),
                    selectedAssignees = issue.assignees.map { it.login }.toSet(),
                    selectedMilestone = issue.milestone?.number,
                    loading = true,
                )
            viewModelScope.launch {
                try {
                    val (labels, assignees, milestones) =
                        coroutineScope {
                            val labelsDeferred = async { repository.getLabels(owner, repo) }
                            val assigneesDeferred = async { loadCandidatesOrEmpty { repository.getAssignees(owner, repo) } }
                            val milestonesDeferred = async { loadCandidatesOrEmpty { repository.getMilestones(owner, repo) } }
                            Triple(labelsDeferred.await(), assigneesDeferred.await(), milestonesDeferred.await())
                        }
                    _editState.value =
                        _editState.value?.copy(
                            labels = labels,
                            assignees = assignees,
                            milestones = milestones,
                            loading = false,
                            errorType = null,
                        )
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _editState.value = _editState.value?.copy(loading = false, errorType = e.toIssueErrorType())
                }
            }
        }

        /** 关闭元数据编辑 Sheet（丢弃未保存选择） */
        fun dismissMetaEditor() {
            _editState.value = null
        }

        /** 标签多选切换 */
        fun toggleLabelSelection(name: String) {
            _editState.value = _editState.value?.toggleLabel(name)
        }

        /** Assignee 多选切换 */
        fun toggleAssigneeSelection(login: String) {
            _editState.value = _editState.value?.toggleAssignee(login)
        }

        /** Milestone 单选（再次点击已选项 = 清除里程碑） */
        fun selectMilestone(number: Long?) {
            _editState.value = _editState.value?.selectMilestone(number)
        }

        /**
         * 保存元数据编辑：**只把变更字段进 PATCH** → 乐观更新 HeaderCard → 失败回滚 + Snackbar。
         * 无字段变更时直接关闭 Sheet（不发请求）。保存中 [IssueEditUiState.saving] 防连点。
         */
        fun saveIssueMeta() {
            val current = currentSuccess() ?: return
            val edit = _editState.value ?: return
            if (edit.saving) return
            val issue = current.issue
            val patch = buildMetaPatch(issue, edit)
            if (patch.isEmpty) {
                // 无字段变更 → 不发请求，直接关闭 Sheet
                _editState.value = null
                return
            }
            _uiState.value = current.copy(issue = optimisticMetaIssue(issue, edit))
            _editState.value = edit.copy(saving = true)
            viewModelScope.launch {
                try {
                    val updated =
                        repository.updateIssueMeta(
                            owner = owner,
                            repo = repo,
                            number = number,
                            labels = patch.labels,
                            assignees = patch.assignees,
                            milestone = patch.milestone,
                            clearMilestone = patch.clearMilestone,
                        )
                    val state = currentSuccess() ?: return@launch
                    _uiState.value = state.copy(issue = updated.withWriteContext(issue))
                    _editState.value = null
                    emitSnackbar(IssueSnackbarMessage.ISSUE_META_UPDATED)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    val state = currentSuccess() ?: return@launch
                    _uiState.value = state.copy(issue = issue)
                    _editState.value = edit.copy(saving = false)
                    emitSnackbar(e.toIssueSnackbarMessage())
                }
            }
        }

        // ---- 私有实现 ----

        /** 订阅态探测：失败按未订阅降级（读路径不阻塞详情页；写操作失败另有 Snackbar 反馈） */
        private suspend fun isSubscribedSafely(): Boolean =
            try {
                repository.isSubscribed(owner, repo, number)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                false
            }

        /** 候选项加载：失败降级为空列表（Assignees 404 = 无权限 / Milestones 缺失） */
        private suspend fun <T> loadCandidatesOrEmpty(block: suspend () -> List<T>): List<T> =
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                emptyList()
            }

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

/**
 * 元数据编辑的 PATCH 参数（#163 L02）：**只含变更字段**——未变更字段保持 null，
 * [com.yumiru11.githubapp.core.githubrest.model.UpdateIssueRequest] 序列化器会跳过它们。
 */
private data class IssueMetaPatch(
    val labels: List<String>? = null,
    val assignees: List<String>? = null,
    val milestone: Long? = null,
    val clearMilestone: Boolean = false,
) {
    /** 三个字段都未变更（无需发请求） */
    val isEmpty: Boolean
        get() = labels == null && assignees == null && milestone == null && !clearMilestone
}

/** 对比当前 Issue 与编辑选择，产出仅含变更字段的 PATCH 参数（标签按候选项顺序稳定排序） */
private fun buildMetaPatch(
    issue: Issue,
    edit: IssueEditUiState,
): IssueMetaPatch {
    val labelsChanged = edit.selectedLabels != issue.labels.map { it.name }.toSet()
    val assigneesChanged = edit.selectedAssignees != issue.assignees.map { it.login }.toSet()
    val milestoneChanged = edit.selectedMilestone != issue.milestone?.number
    val orderedLabels = edit.labels.map { it.name }.filter { it in edit.selectedLabels }
    return IssueMetaPatch(
        labels = (orderedLabels + edit.selectedLabels.filterNot { it in orderedLabels }).takeIf { labelsChanged },
        assignees = edit.selectedAssignees.toList().takeIf { assigneesChanged },
        milestone = edit.selectedMilestone.takeIf { milestoneChanged },
        clearMilestone = milestoneChanged && edit.selectedMilestone == null,
    )
}

/** 编辑选择的乐观映射（用已有标签/用户对象补齐展示信息，避免 HeaderCard 闪空） */
private fun optimisticMetaIssue(
    issue: Issue,
    edit: IssueEditUiState,
): Issue =
    issue.copy(
        labels =
            edit.selectedLabels.map { name ->
                issue.labels.firstOrNull { it.name == name }
                    ?: edit.labels.firstOrNull { it.name == name }
                    ?: IssueLabel(name = name)
            },
        assignees =
            edit.selectedAssignees.map { login ->
                issue.assignees.firstOrNull { it.login == login }
                    ?: edit.assignees.firstOrNull { it.login == login }
                    ?: IssueUser(login = login)
            },
        milestone =
            edit.selectedMilestone?.let { number ->
                issue.milestone?.takeIf { it.number == number }
                    ?: edit.milestones.firstOrNull { it.number == number }
            },
    )

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
