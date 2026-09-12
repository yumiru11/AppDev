package com.yumiru11.githubapp.feature.pullrequest

import com.yumiru11.githubapp.core.designsystem.component.AppStateColorRole
import com.yumiru11.githubapp.feature.pullrequest.model.CheckRun
import com.yumiru11.githubapp.feature.pullrequest.model.CheckRunConclusion
import com.yumiru11.githubapp.feature.pullrequest.model.CheckRunStatus
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Check Run → 语义色角色映射（plan.md §5.3 Checks 状态色；审计 P1）。
 *
 * 审计缺陷：旧 [PullRequestTabContent] 的 `checkRunTint` 把 success 与
 * queued/in_progress 同用 `primary`，四个状态家族无法区分。本测试锁定
 * success→SUCCESS、failure/timed_out→DANGER、pending/action_required→WARNING、
 * 无结论（skipped/neutral/cancelled/unknown）→SURFACE_VARIANT。
 *
 * 纯 JVM 测试：映射函数无 Compose/Android 依赖，色值解析在
 * `core:designsystem` 的 `appStateColors`（由 AppStateChipTest 覆盖）。
 */
class CheckRunColorRoleTest {
    private fun checkRun(
        status: CheckRunStatus,
        conclusion: CheckRunConclusion = CheckRunConclusion.UNKNOWN,
    ): CheckRun =
        CheckRun(
            id = 1L,
            name = "ci",
            status = status,
            conclusion = conclusion,
        )

    @Test
    fun checkRunColorRole_completedSuccess_mapsToSuccess() {
        assertEquals(
            AppStateColorRole.SUCCESS,
            checkRunColorRole(checkRun(CheckRunStatus.COMPLETED, CheckRunConclusion.SUCCESS)),
        )
    }

    @Test
    fun checkRunColorRole_completedFailure_mapsToDanger() {
        assertEquals(
            AppStateColorRole.DANGER,
            checkRunColorRole(checkRun(CheckRunStatus.COMPLETED, CheckRunConclusion.FAILURE)),
        )
    }

    @Test
    fun checkRunColorRole_completedTimedOut_mapsToDanger() {
        // 超时 = 红灯结论（GitHub 与 failure 同族）
        assertEquals(
            AppStateColorRole.DANGER,
            checkRunColorRole(checkRun(CheckRunStatus.COMPLETED, CheckRunConclusion.TIMED_OUT)),
        )
    }

    @Test
    fun checkRunColorRole_queued_mapsToWarning() {
        // plan.md §5.3：Checks pending → warning（扩展色）
        assertEquals(
            AppStateColorRole.WARNING,
            checkRunColorRole(checkRun(CheckRunStatus.QUEUED)),
        )
    }

    @Test
    fun checkRunColorRole_inProgress_mapsToWarning() {
        assertEquals(
            AppStateColorRole.WARNING,
            checkRunColorRole(checkRun(CheckRunStatus.IN_PROGRESS)),
        )
    }

    @Test
    fun checkRunColorRole_completedActionRequired_mapsToWarning() {
        assertEquals(
            AppStateColorRole.WARNING,
            checkRunColorRole(checkRun(CheckRunStatus.COMPLETED, CheckRunConclusion.ACTION_REQUIRED)),
        )
    }

    @Test
    fun checkRunColorRole_completedSkipped_mapsToSurfaceVariant() {
        assertEquals(
            AppStateColorRole.SURFACE_VARIANT,
            checkRunColorRole(checkRun(CheckRunStatus.COMPLETED, CheckRunConclusion.SKIPPED)),
        )
    }

    @Test
    fun checkRunColorRole_completedNeutral_mapsToSurfaceVariant() {
        assertEquals(
            AppStateColorRole.SURFACE_VARIANT,
            checkRunColorRole(checkRun(CheckRunStatus.COMPLETED, CheckRunConclusion.NEUTRAL)),
        )
    }

    @Test
    fun checkRunColorRole_completedCancelled_mapsToSurfaceVariant() {
        // 取消 ≠ 失败：GitHub 渲染为灰色斜杠圆圈，与 skipped 同族
        assertEquals(
            AppStateColorRole.SURFACE_VARIANT,
            checkRunColorRole(checkRun(CheckRunStatus.COMPLETED, CheckRunConclusion.CANCELLED)),
        )
    }

    @Test
    fun checkRunColorRole_completedUnknownConclusion_mapsToSurfaceVariant() {
        assertEquals(
            AppStateColorRole.SURFACE_VARIANT,
            checkRunColorRole(checkRun(CheckRunStatus.COMPLETED, CheckRunConclusion.UNKNOWN)),
        )
    }

    @Test
    fun checkRunColorRole_unknownStatus_mapsToSurfaceVariant() {
        assertEquals(
            AppStateColorRole.SURFACE_VARIANT,
            checkRunColorRole(checkRun(CheckRunStatus.UNKNOWN, CheckRunConclusion.SUCCESS)),
        )
    }

    @Test
    fun checkRunColorRole_pendingWithStaleConclusion_ignoresConclusion() {
        // 未完成时结论不参与：in_progress + success → 仍为 pending 的 WARNING
        assertEquals(
            AppStateColorRole.WARNING,
            checkRunColorRole(checkRun(CheckRunStatus.IN_PROGRESS, CheckRunConclusion.SUCCESS)),
        )
    }
}
