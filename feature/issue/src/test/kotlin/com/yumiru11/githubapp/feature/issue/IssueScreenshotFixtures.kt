package com.yumiru11.githubapp.feature.issue

import androidx.lifecycle.SavedStateHandle
import androidx.paging.PagingData
import com.yumiru11.githubapp.feature.issue.data.IssueRepository
import com.yumiru11.githubapp.feature.issue.model.Issue
import com.yumiru11.githubapp.feature.issue.model.IssueLabel
import com.yumiru11.githubapp.feature.issue.model.IssueState
import com.yumiru11.githubapp.feature.issue.model.IssueTimelineEventType
import com.yumiru11.githubapp.feature.issue.model.IssueTimelineItem
import com.yumiru11.githubapp.feature.issue.model.IssueUser
import com.yumiru11.githubapp.feature.issue.model.IssueViewerPermission
import com.yumiru11.githubapp.feature.issue.model.IssueWriteContext
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf

/**
 * 截图测试共用的 ViewModel 构造（列表 / 详情）。
 *
 * 抽出的原因：light/dark 与 RTL 全屏帧分属不同测试类（RTL 帧用
 * `captureScreenshotDeterministic`，与 Roborazzi compose 路径不能同类混用——
 * compose rule 的 Activity 会成为第二个 window root，把 Roborazzi 逼进
 * captureScreenRoboImage 分支），需要同一份确定性桩数据兜住两张帧。
 */
internal fun issueListViewModel(): IssueListViewModel {
    val repository =
        mockk<IssueRepository> {
            every { issues(any(), any(), any()) } returns
                flowOf(
                    PagingData.from(
                        listOf(
                            Issue(
                                id = 1L,
                                number = 42,
                                title = "Bug report: crash on startup",
                                state = IssueState.OPEN,
                                author = IssueUser(login = "octocat"),
                                commentCount = 3,
                            ),
                            Issue(
                                id = 2L,
                                number = 7,
                                title = "Add dark mode support",
                                state = IssueState.CLOSED,
                                author = IssueUser(login = "hubot"),
                                commentCount = 0,
                            ),
                        ),
                    ),
                )
        }
    return IssueListViewModel(
        SavedStateHandle(mapOf("owner" to "octocat", "repo" to "Hello-World")),
        repository,
    )
}

internal fun issueDetailViewModel(): IssueDetailViewModel {
    val repository =
        mockk<IssueRepository> {
            coEvery { getIssue("octocat", "Hello-World", 42) } returns
                Issue(
                    id = 1L,
                    number = 42,
                    title = "Bug report: crash on startup",
                    state = IssueState.OPEN,
                    body = "This is a **bug** description with a [link](https://github.com).",
                    author = IssueUser(login = "octocat"),
                    labels = listOf(IssueLabel(name = "bug", color = "d73a4a")),
                    assignees = listOf(IssueUser(login = "octocat"), IssueUser(login = "hubot")),
                    createdAt = isoDaysAgo(days = 240),
                    htmlUrl = "https://github.com/octocat/Hello-World/issues/42",
                )
            coEvery { timeline("octocat", "Hello-World", 42) } returns
                listOf(
                    IssueTimelineItem.Comment(
                        id = 10L,
                        author = IssueUser(login = "hubot"),
                        body = "Looks good to me, thanks!",
                        createdAt = isoDaysAgo(days = 240),
                    ),
                    IssueTimelineItem.Event(
                        id = 11L,
                        type = IssueTimelineEventType.LABELED,
                        actor = IssueUser(login = "octocat"),
                        label = IssueLabel(name = "bug", color = "d73a4a"),
                    ),
                )
            coEvery { getIssueWriteContext("octocat", "Hello-World", 42) } returns
                IssueWriteContext(
                    viewerLogin = "octocat",
                    viewerPermission = IssueViewerPermission.WRITE,
                    issueNodeId = "I_kwDOA",
                )
            coEvery { isSubscribed("octocat", "Hello-World", 42) } returns false
        }
    return IssueDetailViewModel(
        SavedStateHandle(mapOf("owner" to "octocat", "repo" to "Hello-World", "number" to 42)),
        repository,
        draftSaver(RecordingDraftRepository()),
    )
}

/**
 * 相对时间夹具（**截图用例必须相对，不能写死绝对时间**）。
 *
 * 详情页渲染的是相对时间（"8 months ago"），写死绝对时间戳就是**时间炸弹**：
 * 到某个不可预测的日期会突然翻桶变红，而且本机复现不了（本机与 CI 渲染基线本就不同）。
 * 姊妹用例 NotificationsPanelScreenshotTest 就是这么炸的（"2 weeks"→"3 weeks"）。
 *
 * 取 240 天：分桶为 minutes/(60*24*30) = days/30，240/30 = 8 → "8 months ago"，
 * 且渲染时刻必然晚于夹具构造 → 差值只会更大；稳定区间 240~269 天（整整一个月）。
 * 阈值口径核对自 core:ui 的 computeRelativeTime。
 */
internal fun isoDaysAgo(days: Long): String =
    java.time.Instant
        .now()
        .minus(days, java.time.temporal.ChronoUnit.DAYS)
        .toString()
