package com.yumiru11.githubapp.feature.pullrequest

import androidx.lifecycle.SavedStateHandle
import androidx.paging.PagingData
import com.yumiru11.githubapp.feature.pullrequest.data.PullRequestRepository
import com.yumiru11.githubapp.feature.pullrequest.data.RepositoryControl
import com.yumiru11.githubapp.feature.pullrequest.model.CheckRun
import com.yumiru11.githubapp.feature.pullrequest.model.CheckRunConclusion
import com.yumiru11.githubapp.feature.pullrequest.model.CheckRunStatus
import com.yumiru11.githubapp.feature.pullrequest.model.CombinedStatus
import com.yumiru11.githubapp.feature.pullrequest.model.MergeableState
import com.yumiru11.githubapp.feature.pullrequest.model.PullRequest
import com.yumiru11.githubapp.feature.pullrequest.model.PullRequestBranch
import com.yumiru11.githubapp.feature.pullrequest.model.PullRequestCommit
import com.yumiru11.githubapp.feature.pullrequest.model.PullRequestCommitFile
import com.yumiru11.githubapp.feature.pullrequest.model.PullRequestFile
import com.yumiru11.githubapp.feature.pullrequest.model.PullRequestFileStatus
import com.yumiru11.githubapp.feature.pullrequest.model.PullRequestLabel
import com.yumiru11.githubapp.feature.pullrequest.model.PullRequestReviewState
import com.yumiru11.githubapp.feature.pullrequest.model.PullRequestState
import com.yumiru11.githubapp.feature.pullrequest.model.PullRequestTab
import com.yumiru11.githubapp.feature.pullrequest.model.PullRequestTimelineEventType
import com.yumiru11.githubapp.feature.pullrequest.model.PullRequestTimelineItem
import com.yumiru11.githubapp.feature.pullrequest.model.PullRequestUser
import com.yumiru11.githubapp.feature.pullrequest.model.ReviewThreadContext
import com.yumiru11.githubapp.feature.pullrequest.model.ViewerPermission
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import java.time.Instant

/**
 * PR 列表 / 详情截图测试共用夹具（确定性、离线自足）。
 *
 * ## 为什么时间戳要用相对值
 *
 * 详情页头部 / 时间线把 `createdAt` / `submittedAt` 交给 `relativeTimeText` 渲染成
 * "2 days ago" 这类相对文案。若夹具写死绝对时间戳（如 `2026-01-01T10:00:00Z`），
 * 渲染出的相对文案会随日历推进而漂移（今天是"8 months ago"、下月变"9 months ago"），
 * 基线会在无人改代码时静默过期。用 [isoDaysAgo] 在**测试运行时**按"当前时刻减 N 天"
 * 生成时间戳，渲染出的相对文案恒为"N days ago"，跨日历稳定。
 *
 * 抽出的原因同 IssueScreenshotFixtures：light/dark 与 RTL 全屏帧分属不同测试类，
 * 需要同一份确定性桩数据兜住所有帧。
 *
 * 本文件第一个声明 [isoDaysAgo] 即「相对时间夹具」：N 天前的 ISO-8601 时间戳，
 * 渲染为恒定"N days ago"，不随日历漂移。
 */
internal fun isoDaysAgo(days: Long): String = Instant.now().minusSeconds(days * SECONDS_PER_DAY).toString()

private const val SECONDS_PER_DAY = 86_400L

// ── 领域对象夹具 ────────────────────────────────────────────────────────

private fun pullRequestFixture(): PullRequest =
    PullRequest(
        id = 91L,
        number = 42,
        title = "test(pullrequest): 补 PR 列表/详情/文件变更截图基线",
        state = PullRequestState.OPEN,
        body = "## 摘要\n\n补上 PR 列表、详情与文件变更三个区块的截图基线，并纳入 CI verify。",
        author = PullRequestUser(login = "yumiru11"),
        labels = listOf(PullRequestLabel(name = "test", color = "0e8a16"), PullRequestLabel(name = "ui", color = "1d76db")),
        assignees = listOf(PullRequestUser(login = "octocat")),
        commentCount = 3,
        reviewCommentCount = 1,
        commitCount = 2,
        additions = 128,
        deletions = 12,
        changedFiles = 3,
        createdAt = isoDaysAgo(2),
        updatedAt = isoDaysAgo(1),
        nodeId = "PR_kwDOA",
        htmlUrl = "https://github.com/octocat/Hello-World/pull/42",
        mergeable = true,
        mergeableState = MergeableState.MERGEABLE,
        head = PullRequestBranch(label = "octocat:feature", ref = "feature", sha = HEAD_SHA, repoFullName = "octocat/Hello-World"),
        base = PullRequestBranch(label = "octocat:main", ref = "main", sha = "def456", repoFullName = "octocat/Hello-World"),
        requestedReviewers = listOf(PullRequestUser(login = "hubot")),
    )

private fun timelineFixture(): List<PullRequestTimelineItem> =
    listOf(
        PullRequestTimelineItem.Comment(
            id = 10L,
            author = PullRequestUser(login = "hubot"),
            body = "列表页看起来没问题，文件变更那张再确认下缓存命中后的行数。",
            createdAt = isoDaysAgo(1),
        ),
        PullRequestTimelineItem.Review(
            id = 11L,
            author = PullRequestUser(login = "octocat"),
            body = "已补截图，缓存键按 head sha。",
            state = PullRequestReviewState.COMMENTED,
            submittedAt = isoDaysAgo(1),
        ),
        PullRequestTimelineItem.Event(
            id = 12L,
            type = PullRequestTimelineEventType.LABELED,
            actor = PullRequestUser(login = "yumiru11"),
            createdAt = isoDaysAgo(1),
            label = PullRequestLabel(name = "test", color = "0e8a16"),
        ),
    )

private fun commitsFixture(): List<PullRequestCommit> =
    listOf(
        PullRequestCommit(
            sha = HEAD_SHA,
            message = "test(pullrequest): 补截图基线并纳入 CI verify",
            author = PullRequestUser(login = "yumiru11"),
            createdAt = isoDaysAgo(1),
            files =
                listOf(
                    PullRequestCommitFile(
                        filename = "PullRequestListScreenScreenshotTest.kt",
                        status = PullRequestFileStatus.ADDED,
                        additions = 116,
                        deletions = 0,
                    ),
                ),
        ),
        PullRequestCommit(
            sha = "def456",
            message = "feat(pullrequest): 文件列表缓存按 head sha",
            author = PullRequestUser(login = "octocat"),
            createdAt = isoDaysAgo(2),
        ),
    )

private fun filesFixture(): List<PullRequestFile> =
    listOf(
        PullRequestFile(
            filename = "PullRequestScreenshotFixtures.kt",
            status = PullRequestFileStatus.ADDED,
            additions = 140,
            deletions = 0,
            changes = 140,
        ),
        PullRequestFile(
            filename = "PullRequestListScreenScreenshotTest.kt",
            status = PullRequestFileStatus.MODIFIED,
            additions = 116,
            deletions = 4,
            changes = 120,
            // 非空 patch → 行尾展示展开箭头（本帧不展开，patch 不参与渲染）
            patch = "@@ -1,3 +1,4 @@\n-old\n+new\n context\n",
        ),
        PullRequestFile(
            filename = "docs/agents/project-status.md",
            status = PullRequestFileStatus.MODIFIED,
            additions = 8,
            deletions = 4,
            changes = 12,
        ),
    )

private fun checkRunsFixture(): List<CheckRun> =
    listOf(
        CheckRun(
            id = 100L,
            name = "build",
            status = CheckRunStatus.COMPLETED,
            conclusion = CheckRunConclusion.SUCCESS,
            appName = "GitHub Actions",
        ),
        CheckRun(
            id = 101L,
            name = "detekt",
            status = CheckRunStatus.COMPLETED,
            conclusion = CheckRunConclusion.FAILURE,
            appName = "GitHub Actions",
            outputTitle = "Static analysis found 1 issue",
            outputSummary = "1 smell in PullRequestListScreen.kt",
            outputText = "PullRequestListScreen.kt:120:15 TooManyFunctions",
        ),
    )

// ── ViewModel 夹具 ──────────────────────────────────────────────────────

/** 列表页 ViewModel：三条不同状态的 PR（Open / Merged / Draft）+ WRITE 权限（创建入口可见）。 */
internal fun pullRequestListViewModel(): PullRequestListViewModel {
    val repository =
        mockk<PullRequestRepository> {
            every { pulls("octocat", "Hello-World", any()) } returns
                flowOf(
                    PagingData.from(
                        listOf(
                            PullRequest(
                                id = 91L,
                                number = 91,
                                title = "test(pullrequest): 补 PR 截图基线",
                                state = PullRequestState.OPEN,
                                author = PullRequestUser(login = "yumiru11"),
                                commentCount = 2,
                            ),
                            PullRequest(
                                id = 73L,
                                number = 73,
                                title = "feat(markdown): WebView 主渲染切换收尾",
                                state = PullRequestState.MERGED,
                                author = PullRequestUser(login = "octocat"),
                                commentCount = 5,
                            ),
                            PullRequest(
                                id = 42L,
                                number = 42,
                                title = "docs(ui): 归档 UI 审查报告",
                                state = PullRequestState.DRAFT,
                                author = PullRequestUser(login = "hubot"),
                                commentCount = 0,
                            ),
                        ),
                    ),
                )
            coEvery { repositoryControl("octocat", "Hello-World") } returns
                RepositoryControl(viewerPermission = ViewerPermission.WRITE, defaultBranch = "main")
        }
    return PullRequestListViewModel(
        SavedStateHandle(mapOf("owner" to "octocat", "repo" to "Hello-World")),
        repository,
    )
}

/**
 * 详情页 ViewModel：四 Tab 数据齐全、WRITE 权限、同仓库 head 分支。
 *
 * @param tab 初始选中的 Tab（详情页默认 CONVERSATION；Checks/Files 帧显式切过去）
 */
internal fun pullRequestDetailViewModel(tab: PullRequestTab = PullRequestTab.CONVERSATION): PullRequestDetailViewModel {
    val repository =
        mockk<PullRequestRepository> {
            coEvery { getPullRequest("octocat", "Hello-World", 42) } returns pullRequestFixture()
            coEvery { timeline("octocat", "Hello-World", 42) } returns timelineFixture()
            coEvery { commits("octocat", "Hello-World", 42) } returns commitsFixture()
            coEvery { files("octocat", "Hello-World", 42, HEAD_SHA) } returns filesFixture()
            coEvery { checkRuns("octocat", "Hello-World", HEAD_SHA) } returns checkRunsFixture()
            coEvery { combinedStatus("octocat", "Hello-World", HEAD_SHA) } returns CombinedStatus(state = "success", totalCount = 2)
            coEvery { reviewComments("octocat", "Hello-World", 42) } returns emptyList()
            coEvery { reviewThreadContext(any()) } returns ReviewThreadContext(pullRequestNodeId = "PR_kwDOA")
            coEvery { repositoryControl("octocat", "Hello-World") } returns
                RepositoryControl(viewerPermission = ViewerPermission.WRITE, defaultBranch = "main")
            coEvery { viewerLoginOrNull() } returns "yumiru11"
        }
    val viewModel =
        PullRequestDetailViewModel(
            SavedStateHandle(mapOf("owner" to "octocat", "repo" to "Hello-World", "number" to 42)),
            repository,
            draftSaver(RecordingDraftRepository()),
        )
    viewModel.selectTab(tab)
    return viewModel
}

/** head 分支 sha（详情页文件列表缓存键的一部分，夹具内多处引用）。 */
internal const val HEAD_SHA = "abc123"

// ── 创建 PR 页（T23）────────────────────────────────────────────────────

/**
 * 创建 PR 页 ViewModel：真实 VM + [PullRequestRepository] 桩。
 *
 * - 分支候选 = main + feature 分支（base 默认 main、head 默认 feature）
 * - WRITE 权限（`canCreate = true`，顶栏 Create 可用）
 * - 标题 / 正文已填（正文经 DraftText，与屏上输入等价；不发任何写请求）
 */
internal fun pullRequestCreateScreenshotViewModel(): PullRequestCreateViewModel {
    val repository =
        mockk<PullRequestRepository>(relaxed = true) {
            coEvery { repositoryControl("octocat", "Hello-World") } returns
                RepositoryControl(viewerPermission = ViewerPermission.WRITE, defaultBranch = "main")
            coEvery { branches("octocat", "Hello-World") } returns
                Result.success(listOf("main", "feature/screenshot-baseline-remainder"))
        }
    return PullRequestCreateViewModel(
        SavedStateHandle(mapOf("owner" to "octocat", "repo" to "Hello-World")),
        repository,
        draftSaver(RecordingDraftRepository()),
    ).apply {
        updateTitle("test(ui): 补齐创建 PR 页截图基线")
        bodyDraft.onChanged("- 六屏十五帧\n- 基线由 CI canonical workflow 录制")
    }
}
