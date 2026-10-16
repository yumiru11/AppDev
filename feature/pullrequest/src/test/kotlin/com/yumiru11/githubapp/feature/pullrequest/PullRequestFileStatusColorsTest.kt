package com.yumiru11.githubapp.feature.pullrequest

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.yumiru11.githubapp.core.designsystem.theme.AppTheme
import com.yumiru11.githubapp.feature.pullrequest.model.PullRequestFileStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * diff 文件卡状态配色的契约测试（2026-10 UI 重构：状态色从"容器色叠 alpha"改为语义角色）。
 *
 * 断言每个变更类型都有**具体**容器色/前景色（不能是 Unspecified —— 那会渲染成全透明），
 * 且新增与删除必须区分开（同色等于看不出改了还是删了）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PullRequestFileStatusColorsTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun everyStatus_hasConcreteContainerAndContentColors() {
        val containers = mutableMapOf<PullRequestFileStatus, Color>()
        val contents = mutableMapOf<PullRequestFileStatus, Color>()
        composeRule.setContent {
            AppTheme {
                PullRequestFileStatus.entries.forEach { status ->
                    val container = fileStatusContainer(status)
                    val content = fileStatusContent(status)
                    @Suppress("UNUSED_EXPRESSION")
                    MaterialTheme.colorScheme.surface
                    SideEffect {
                        containers[status] = container
                        contents[status] = content
                    }
                }
            }
        }
        composeRule.waitForIdle()

        assertEquals(PullRequestFileStatus.entries.size, containers.size)
        containers.values.forEach { assertNotEquals(Color.Unspecified, it) }
        contents.values.forEach { assertNotEquals(Color.Unspecified, it) }
        assertNotEquals(containers[PullRequestFileStatus.ADDED], containers[PullRequestFileStatus.REMOVED])
        assertNotEquals(contents[PullRequestFileStatus.ADDED], contents[PullRequestFileStatus.REMOVED])
    }
}
