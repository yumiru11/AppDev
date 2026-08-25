@file:Suppress("TooGenericExceptionCaught", "SwallowedException")
// - TooGenericExceptionCaught：网络/IO 错误统一兜底，T15 细化异常类型
// - SwallowedException：写操作失败统一回滚 + 事件通道（异常链无需透出）；refreshThreads 失败保持现状

package com.yumiru11.githubapp.feature.pullrequest

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yumiru11.githubapp.feature.pullrequest.data.PullRequestRepository
import com.yumiru11.githubapp.feature.pullrequest.data.RepositoryControl
import com.yumiru11.githubapp.feature.pullrequest.data.toTimelineItem
import com.yumiru11.githubapp.feature.pullrequest.model.DiffSide
import com.yumiru11.githubapp.feature.pullrequest.model.LineCommentAnchor
import com.yumiru11.githubapp.feature.pullrequest.model.LineCommentTarget
import com.yumiru11.githubapp.feature.pullrequest.model.PullRequestMergeMethod
import com.yumiru11.githubapp.feature.pullrequest.model.PullRequestReview
import com.yumiru11.githubapp.feature.pullrequest.model.PullRequestState
import com.yumiru11.githubapp.feature.pullrequest.model.PullRequestTab
import com.yumiru11.githubapp.feature.pullrequest.model.PullRequestTimelineItem
import com.yumiru11.githubapp.feature.pullrequest.model.PullRequestWriteAction
import com.yumiru11.githubapp.feature.pullrequest.model.ReviewComment
import com.yumiru11.githubapp.feature.pullrequest.model.ReviewConclusion
import com.yumiru11.githubapp.feature.pullrequest.model.ReviewThread
import com.yumiru11.githubapp.feature.pullrequest.model.ReviewThreadContext
import com.yumiru11.githubapp.feature.pullrequest.model.ViewerPermission
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
import java.time.Instant
import javax.inject.Inject

/**
 * PR 详情页 ViewModel（T15）。
 *
 * 从 SavedStateHandle 读取 [owner]/[repo]/[number] 导航参数，加载 PR 详情与四 Tab 数据。
 * 详情先行（Checks 需 head sha），随后时间线/提交/文件/Checks 并行加载（all-or-nothing）。
 *
 * 状态机（可单测）：
 * - [selectedTab]：四 Tab 切换（Conversation/Commits/Checks/Files）
 * - [expandedCheckIds]：Checks 失败详情展开集合
 * - [expandedCommitShas]：Commits 展开 diff 摘要集合
 * - [expandedFileNames]：Files changed 展开 patch 集合
 *
 * 错误一律映射为 [PullRequestErrorType]（UI 层 stringResource 本地化，ViewModel 不产英文文案）。
 */
@HiltViewModel
class PullRequestDetailViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val repository: PullRequestRepository,
    ) : ViewModel() {
        private val owner: String = checkNotNull(savedStateHandle["owner"])
        private val repo: String = checkNotNull(savedStateHandle["repo"])
        private val number: Int = checkNotNull(savedStateHandle["number"])

        private val _uiState = MutableStateFlow<PullRequestDetailUiState>(PullRequestDetailUiState.Loading)
        val uiState: StateFlow<PullRequestDetailUiState> = _uiState.asStateFlow()

        private val _selectedTab = MutableStateFlow(PullRequestTab.CONVERSATION)
        val selectedTab: StateFlow<PullRequestTab> = _selectedTab.asStateFlow()

        private val _expandedCheckIds = MutableStateFlow<Set<Long>>(emptySet())
        val expandedCheckIds: StateFlow<Set<Long>> = _expandedCheckIds.asStateFlow()

        private val _expandedCommitShas = MutableStateFlow<Set<String>>(emptySet())
        val expandedCommitShas: StateFlow<Set<String>> = _expandedCommitShas.asStateFlow()

        private val _expandedFileNames = MutableStateFlow<Set<String>>(emptySet())
        val expandedFileNames: StateFlow<Set<String>> = _expandedFileNames.asStateFlow()

        /** T16：已打开的行评论目标（null = 未打开） */
        private val _lineCommentTarget = MutableStateFlow<LineCommentTarget?>(null)
        val lineCommentTarget: StateFlow<LineCommentTarget?> = _lineCommentTarget.asStateFlow()

        /** T16 写操作失败事件通道（UI 层 stringResource 本地化，ViewModel 不产文案） */
        private val _events = MutableSharedFlow<PullRequestDetailEvent>(extraBufferCapacity = EVENT_BUFFER)
        val events: SharedFlow<PullRequestDetailEvent> = _events.asSharedFlow()

        init {
            loadPullRequestDetail()
        }

        fun retry() {
            _uiState.value = PullRequestDetailUiState.Loading
            loadPullRequestDetail()
        }

        /** 切换四 Tab（幂等：同 Tab 不重复发射） */
        fun selectTab(tab: PullRequestTab) {
            if (_selectedTab.value == tab) return
            _selectedTab.value = tab
        }

        /** 展开/收起 Check Run 失败详情 */
        fun toggleCheckExpanded(checkId: Long) {
            val current = _expandedCheckIds.value
            _expandedCheckIds.value = if (checkId in current) current - checkId else current + checkId
        }

        /** 展开/收起提交 diff 摘要 */
        fun toggleCommitExpanded(sha: String) {
            val current = _expandedCommitShas.value
            _expandedCommitShas.value = if (sha in current) current - sha else current + sha
        }

        /** 展开/收起文件 patch */
        fun toggleFileExpanded(filename: String) {
            val current = _expandedFileNames.value
            _expandedFileNames.value = if (filename in current) current - filename else current + filename
        }

        /** 打开行评论输入（聚合锚点的会话与评论；T16） */
        fun openLineComment(
            path: String,
            side: DiffSide,
            line: Int,
        ) {
            val state = _uiState.value as? PullRequestDetailUiState.Success ?: return
            val anchor = LineCommentAnchor(path = path, side = side, line = line)
            val thread = state.reviewThreads.firstOrNull { it.path == path && it.side == side && it.anchorLine == line }
            val comments = state.reviewComments.filter { it.path == path && it.side == side && it.anchorLine == line }
            _lineCommentTarget.value = LineCommentTarget(anchor = anchor, thread = thread, comments = comments)
        }

        /** 关闭行评论输入 */
        fun dismissLineComment() {
            _lineCommentTarget.value = null
        }

        /** 新增/回复行内评论：乐观插入 → 失败回滚 + Snackbar（T16，T14 Issue 同款模式） */
        fun submitLineComment(
            anchor: LineCommentAnchor,
            body: String,
            inReplyToId: Long? = null,
        ) {
            if (body.isBlank()) return
            val state = _uiState.value as? PullRequestDetailUiState.Success ?: return
            viewModelScope.launch {
                val optimistic =
                    ReviewComment(
                        id = -System.nanoTime(),
                        body = body,
                        path = anchor.path,
                        line = if (anchor.side == DiffSide.RIGHT) anchor.line else null,
                        originalLine = if (anchor.side == DiffSide.LEFT) anchor.line else null,
                        side = anchor.side,
                        inReplyToId = inReplyToId,
                    )
                _uiState.value = state.copy(reviewComments = state.reviewComments + optimistic)
                try {
                    val created =
                        if (inReplyToId != null) {
                            repository.replyReviewComment(owner, repo, number, anchor.path, inReplyToId, body)
                        } else {
                            repository.createReviewComment(
                                owner,
                                repo,
                                number,
                                anchor,
                                body,
                                state.pullRequest.head
                                    ?.sha
                                    .orEmpty(),
                            )
                        }
                    val current = _uiState.value as? PullRequestDetailUiState.Success ?: return@launch
                    _uiState.value =
                        current.copy(
                            reviewComments = current.reviewComments.map { if (it.id == optimistic.id) created else it },
                        )
                    refreshThreads(current.pullRequest.nodeId)
                    _lineCommentTarget.value = null
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    rollbackReviewComment(optimistic.id)
                    _events.tryEmit(PullRequestDetailEvent.CommentFailed)
                }
            }
        }

        /** 解析/解除会话（GraphQL；REST-only 会话不显示入口，故不可达） */
        fun toggleThreadResolved(thread: ReviewThread) {
            val state = _uiState.value as? PullRequestDetailUiState.Success ?: return
            if (!state.canResolveThreads) return
            val targetResolved = !thread.isResolved
            _uiState.value =
                state.copy(
                    reviewThreads =
                        state.reviewThreads.map {
                            if (it.id == thread.id) it.copy(isResolved = targetResolved) else it
                        },
                )
            viewModelScope.launch {
                try {
                    repository.setThreadResolved(thread.id, targetResolved)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    val current = _uiState.value as? PullRequestDetailUiState.Success ?: return@launch
                    _uiState.value =
                        current.copy(
                            reviewThreads =
                                current.reviewThreads.map {
                                    if (it.id == thread.id) it.copy(isResolved = thread.isResolved) else it
                                },
                        )
                    _events.tryEmit(PullRequestDetailEvent.CommentFailed)
                }
            }
        }

        /**
         * 提交 Review（T17）：乐观插入时间线头 → 成功替换为服务端条目 / 失败回滚 + Snackbar。
         *
         * 结论权限收敛：APPROVE/REQUEST_CHANGES 需 WRITE（[PullRequestDetailUiState.Success.canApprove]）；
         * COMMENT 需 READ 且正文非空。
         */
        fun submitReview(
            conclusion: ReviewConclusion,
            body: String,
        ) {
            if (body.isBlank() && conclusion == ReviewConclusion.COMMENT) return
            val state = _uiState.value as? PullRequestDetailUiState.Success ?: return
            if (!state.canReview || state.pendingAction != null) return
            if ((conclusion == ReviewConclusion.APPROVE || conclusion == ReviewConclusion.REQUEST_CHANGES) && !state.canApprove) {
                return
            }
            val optimistic =
                PullRequestTimelineItem.Review(
                    id = -System.nanoTime(),
                    author = null,
                    body = body.takeIf { it.isNotBlank() },
                    state = conclusion.toReviewState(),
                )
            _uiState.value = state.copy(timeline = listOf(optimistic) + state.timeline, pendingAction = PullRequestWriteAction.REVIEW)
            viewModelScope.launch {
                try {
                    val review = repository.submitReview(owner, repo, number, conclusion, body)
                    val current = _uiState.value as? PullRequestDetailUiState.Success ?: return@launch
                    _uiState.value =
                        current.copy(
                            timeline = current.timeline.map { if (it.id == optimistic.id) review.toTimelineItem() else it },
                            pendingAction = null,
                        )
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    val current = _uiState.value as? PullRequestDetailUiState.Success ?: return@launch
                    _uiState.value =
                        current.copy(
                            timeline = current.timeline.filterNot { it.id == optimistic.id },
                            pendingAction = null,
                        )
                    _events.tryEmit(PullRequestDetailEvent.ReviewFailed)
                }
            }
        }

        /**
         * 合并 PR（T17）：乐观置 MERGED → 成功保持并静默刷新 / 失败回滚 + Snackbar。
         * 勾选删除分支：合并成功后先执行删除（失败仅提示，不回滚合并）。
         */
        fun mergePullRequest(
            method: PullRequestMergeMethod,
            commitTitle: String,
            commitMessage: String,
            deleteBranch: Boolean,
        ) {
            val original = _uiState.value as? PullRequestDetailUiState.Success ?: return
            if (!original.canMerge || original.pendingAction != null) return
            if (original.pullRequest.state != PullRequestState.OPEN) return
            val headRef = original.pullRequest.head?.ref
            _uiState.value =
                original.copy(
                    pullRequest = original.pullRequest.copy(state = PullRequestState.MERGED, mergedAt = Instant.now().toString()),
                    canMerge = false,
                    pendingAction = PullRequestWriteAction.MERGE,
                )
            viewModelScope.launch {
                try {
                    repository.mergePullRequest(
                        owner,
                        repo,
                        number,
                        method,
                        commitTitle,
                        commitMessage,
                        original.pullRequest.head?.sha,
                    )
                    val current = _uiState.value as? PullRequestDetailUiState.Success ?: return@launch
                    _uiState.value = current.copy(pendingAction = null)
                    _events.tryEmit(PullRequestDetailEvent.MergeSucceeded)
                    // merged=false：已被合并/无需新提交 → 按成功处理（状态已 MERGED）
                    if (deleteBranch && original.canDeleteHeadBranch) {
                        deleteHeadBranchAfterMerge(headRef)
                    }
                    refreshPullRequestAndTimeline()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    val current = _uiState.value as? PullRequestDetailUiState.Success
                    _uiState.value =
                        current?.copy(pullRequest = original.pullRequest, canMerge = original.canMerge, pendingAction = null)
                            ?: return@launch
                    _events.tryEmit(PullRequestDetailEvent.MergeFailed)
                }
            }
        }

        /** 合并成功后按勾选删除 head 分支（失败仅提示，不回滚合并） */
        private suspend fun deleteHeadBranchAfterMerge(headRef: String?) {
            if (headRef == null) return
            try {
                repository.deleteBranch(owner, repo, headRef)
                successAfterMergeDeleteBranch()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _events.tryEmit(PullRequestDetailEvent.DeleteBranchFailed)
            }
        }

        /** 合并成功后删除分支完成：隐藏删除入口（防止重复删除） */
        private fun successAfterMergeDeleteBranch() {
            val current = _uiState.value as? PullRequestDetailUiState.Success ?: return
            _uiState.value = current.copy(canDeleteHeadBranch = false)
            _events.tryEmit(PullRequestDetailEvent.DeleteBranchSucceeded)
        }

        /**
         * Update branch（T17）：无本地状态可乐观，pending 防重入 + 成功/失败 Snackbar。
         * 仅同仓库 + 打开 + WRITE（canMerge 已含）可触发。
         */
        fun updateBranch() {
            val state = _uiState.value as? PullRequestDetailUiState.Success ?: return
            if (!state.canMerge || !state.headSameRepo || state.pendingAction != null) return
            _uiState.value = state.copy(pendingAction = PullRequestWriteAction.UPDATE_BRANCH)
            viewModelScope.launch {
                try {
                    repository.updateBranch(owner, repo, number, state.pullRequest.head?.sha)
                    val current = _uiState.value as? PullRequestDetailUiState.Success
                    _uiState.value = current?.copy(pendingAction = null) ?: return@launch
                    _events.tryEmit(PullRequestDetailEvent.UpdateBranchSucceeded)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    val current = _uiState.value as? PullRequestDetailUiState.Success
                    _uiState.value = current?.copy(pendingAction = null) ?: return@launch
                    _events.tryEmit(PullRequestDetailEvent.UpdateBranchFailed)
                }
            }
        }

        /** 删除 head 分支（T17：已合并态清理；失败仅提示，不影响合并结果） */
        fun deleteBranch() {
            val state = _uiState.value as? PullRequestDetailUiState.Success ?: return
            if (!state.canDeleteHeadBranch || state.pendingAction != null) return
            val branch = state.pullRequest.head?.ref ?: return
            _uiState.value = state.copy(pendingAction = PullRequestWriteAction.DELETE_BRANCH)
            viewModelScope.launch {
                try {
                    repository.deleteBranch(owner, repo, branch)
                    val current = _uiState.value as? PullRequestDetailUiState.Success
                    _uiState.value = current?.copy(canDeleteHeadBranch = false, pendingAction = null) ?: return@launch
                    _events.tryEmit(PullRequestDetailEvent.DeleteBranchSucceeded)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    val current = _uiState.value as? PullRequestDetailUiState.Success
                    _uiState.value = current?.copy(pendingAction = null) ?: return@launch
                    _events.tryEmit(PullRequestDetailEvent.DeleteBranchFailed)
                }
            }
        }

        /** 合并/相关写操作后的静默刷新（保留 Tab 与展开状态，仅替换详情与时间线数据） */
        private suspend fun refreshPullRequestAndTimeline() {
            try {
                val pullRequest = repository.getPullRequest(owner, repo, number)
                val timeline = repository.timeline(owner, repo, number)
                val current = _uiState.value as? PullRequestDetailUiState.Success ?: return
                _uiState.value = current.copy(pullRequest = pullRequest, timeline = timeline)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // 刷新失败保持乐观态（写操作已成功）
            }
        }

        private suspend fun refreshThreads(pullRequestNodeId: String?) {
            if (pullRequestNodeId == null) return
            try {
                val context = repository.reviewThreadContext(pullRequestNodeId)
                val current = _uiState.value as? PullRequestDetailUiState.Success ?: return
                _uiState.value =
                    current.copy(
                        reviewThreads = context.threads,
                        canResolveThreads = context.pullRequestNodeId != null,
                    )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // 刷新失败保持现状（乐观写入已成功）
            }
        }

        private fun rollbackReviewComment(id: Long) {
            val current = _uiState.value as? PullRequestDetailUiState.Success ?: return
            _uiState.value = current.copy(reviewComments = current.reviewComments.filterNot { it.id == id })
        }

        private fun loadPullRequestDetail() {
            viewModelScope.launch {
                _uiState.value = PullRequestDetailUiState.Loading
                try {
                    val pullRequest = repository.getPullRequest(owner, repo, number)
                    val headSha = pullRequest.head?.sha
                    val bundle =
                        coroutineScope {
                            val timelineDeferred = async { repository.timeline(owner, repo, number) }
                            val commitsDeferred = async { repository.commits(owner, repo, number) }
                            val filesDeferred = async { repository.files(owner, repo, number) }
                            val checksDeferred =
                                async {
                                    headSha?.let { repository.checkRuns(owner, repo, it) } ?: emptyList()
                                }
                            val statusDeferred =
                                async {
                                    headSha?.let { repository.combinedStatus(owner, repo, it) }
                                }
                            val reviewCommentsDeferred = async { repository.reviewComments(owner, repo, number) }
                            val threadContextDeferred = async { repository.reviewThreadContext(pullRequest.nodeId) }
                            val controlDeferred = async { repository.repositoryControl(owner, repo) }
                            TimelineBundle(
                                timeline = timelineDeferred.await(),
                                commits = commitsDeferred.await(),
                                files = filesDeferred.await(),
                                checkRuns = checksDeferred.await(),
                                combinedStatus = statusDeferred.await(),
                                reviewComments = reviewCommentsDeferred.await(),
                                threadContext = threadContextDeferred.await(),
                                control = controlDeferred.await(),
                            )
                        }
                    val control = bundle.control
                    val prHead = pullRequest.head
                    val headSameRepo = prHead?.repoFullName == "$owner/$repo"
                    val headIsDefaultBranch = control.defaultBranch != null && prHead?.ref == control.defaultBranch
                    _uiState.value =
                        PullRequestDetailUiState.Success(
                            pullRequest = pullRequest,
                            timeline = bundle.timeline,
                            commits = bundle.commits,
                            files = bundle.files,
                            checkRuns = bundle.checkRuns,
                            combinedStatus = bundle.combinedStatus,
                            reviewComments = bundle.reviewComments,
                            reviewThreads = bundle.threadContext.threads,
                            canResolveThreads = bundle.threadContext.pullRequestNodeId != null,
                            viewerPermission = control.viewerPermission,
                            defaultBranch = control.defaultBranch,
                            canReview = control.viewerPermission != ViewerPermission.UNKNOWN,
                            canApprove = control.viewerPermission == ViewerPermission.WRITE,
                            canMerge = pullRequest.state == PullRequestState.OPEN && control.viewerPermission == ViewerPermission.WRITE,
                            headSameRepo = headSameRepo,
                            headIsDefaultBranch = headIsDefaultBranch,
                            canDeleteHeadBranch =
                                control.viewerPermission == ViewerPermission.WRITE &&
                                    headSameRepo &&
                                    !headIsDefaultBranch &&
                                    !prHead?.ref.isNullOrBlank(),
                        )
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _uiState.value = PullRequestDetailUiState.Error(errorType = e.toPullRequestErrorType())
                }
            }
        }

        private companion object {
            const val EVENT_BUFFER = 8
        }
    }

/** 并行加载结果聚合（避免 data class 五元组样板） */
private data class TimelineBundle(
    val timeline: List<com.yumiru11.githubapp.feature.pullrequest.model.PullRequestTimelineItem>,
    val commits: List<com.yumiru11.githubapp.feature.pullrequest.model.PullRequestCommit>,
    val files: List<com.yumiru11.githubapp.feature.pullrequest.model.PullRequestFile>,
    val checkRuns: List<com.yumiru11.githubapp.feature.pullrequest.model.CheckRun>,
    val combinedStatus: com.yumiru11.githubapp.feature.pullrequest.model.CombinedStatus?,
    val reviewComments: List<com.yumiru11.githubapp.feature.pullrequest.model.ReviewComment>,
    val threadContext: com.yumiru11.githubapp.feature.pullrequest.model.ReviewThreadContext,
    val control: RepositoryControl,
)
