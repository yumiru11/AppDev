package com.yumiru11.githubapp.core.designsystem.icon

import androidx.compose.ui.graphics.vector.ImageVector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * AppDevOcticons 全量物化测试（#84 入库 15 枚）。
 *
 * ImageVector 为 lazy 构建，访问即触发 PathParser 解析——断言全部图标可解析、
 * 视口为 Octicons 标准 16×16（同时为 JaCoCo 覆盖 lazy 委托行）。
 */
@RunWith(RobolectricTestRunner::class)
class AppDevOcticonsTest {
    @Test
    fun appDevOcticons_allIcons_materializeWithOcticonsViewport() {
        val all: List<ImageVector> =
            listOf(
                AppDevOcticons.Info,
                AppDevOcticons.LightBulb,
                AppDevOcticons.Alert,
                AppDevOcticons.Stop,
                AppDevOcticons.Flame,
                // #84 批次二
                AppDevOcticons.Repo,
                AppDevOcticons.IssueOpened,
                AppDevOcticons.PullRequest,
                AppDevOcticons.Merge,
                AppDevOcticons.Branch,
                AppDevOcticons.Forked,
                AppDevOcticons.Star,
                AppDevOcticons.Eye,
                AppDevOcticons.Comment,
                AppDevOcticons.Check,
                AppDevOcticons.Diff,
                AppDevOcticons.File,
                AppDevOcticons.Tag,
                AppDevOcticons.History,
                AppDevOcticons.Bookmark,
            )
        assertEquals(20, all.size)
        all.forEach { icon ->
            assertTrue(icon.name.isNotBlank())
            assertEquals(16f, icon.viewportWidth)
            assertEquals(16f, icon.viewportHeight)
        }
    }
}
