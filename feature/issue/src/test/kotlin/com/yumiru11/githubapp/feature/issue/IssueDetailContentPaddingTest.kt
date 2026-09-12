package com.yumiru11.githubapp.feature.issue

import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.yumiru11.githubapp.core.designsystem.token.AppDimens
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * [issueDetailContentPadding] 数值断言（UI-5：评论 FAB 压住时间线末尾）。
 *
 * 详情页列表底部预留全部收敛在这个纯函数里（不拉起 Hilt + WebView 整屏组合），
 * 口径与 `ReposContentPaddingTest` 同源：正向数值 + 复刻修复前口径的反向护栏。
 * 整屏首帧由既有详情截图帧覆盖，本测试锁住数值契约。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class IssueDetailContentPaddingTest {
    @Test
    fun issueDetailContentPadding_bottom_reservesAtLeast96dp() {
        val padding = issueDetailContentPadding()

        assertEquals(AppDimens.fabContentClearance, padding.calculateBottomPadding())
        assertTrue(
            "底部预留必须 ≥ 96dp 验收线（remaining-backlog-2026-09-12 §3.3 UI-5）",
            padding.calculateBottomPadding() >= FAB_CLEARANCE_ACCEPTANCE,
        )
    }

    @Test
    fun issueDetailContentPadding_horizontalAndTop_unchangedFromPreFix() {
        val padding = issueDetailContentPadding()

        assertEquals(AppDimens.contentPadding, padding.calculateLeftPadding(LayoutDirection.Ltr))
        assertEquals(AppDimens.contentPadding, padding.calculateRightPadding(LayoutDirection.Ltr))
        assertEquals(
            "顶部保持修复前 8dp 口径，不得顺带改动正文与顶栏间距",
            8.dp,
            padding.calculateTopPadding(),
        )
    }

    @Test
    fun issueDetailContentPadding_preFixBottomOnly8dp_wouldFallInsideFabRect() {
        // 反向护栏：复刻修复前 `vertical = 8.dp`（bottom 仅 8dp）的等效行为。
        // FAB 上沿距内容底 = 16dp 边距 + 56dp 高度 = 72dp；8dp < 72dp → 末条必然被压住，
        // 证明「≥ 72dp」这一断言对修复前后的差异真实敏感。
        val preFixBottom = 8.dp

        assertTrue(
            "修复前口径应落进 FAB 矩形（保证反向用例灵敏度）",
            preFixBottom < FAB_TOP_FROM_CONTENT_BOTTOM,
        )
        assertTrue(
            "修复后底部预留必须越过 FAB 上沿",
            issueDetailContentPadding().calculateBottomPadding() >= FAB_TOP_FROM_CONTENT_BOTTOM,
        )
    }

    private companion object {
        /** 验收线（remaining-backlog-2026-09-12 §3.3 UI-5）：底部 contentPadding ≥ 96dp。 */
        val FAB_CLEARANCE_ACCEPTANCE = 96.dp

        /** ExtendedFAB：距内容底 16dp + 高 56dp = 72dp。 */
        val FAB_TOP_FROM_CONTENT_BOTTOM = 16.dp + 56.dp
    }
}
