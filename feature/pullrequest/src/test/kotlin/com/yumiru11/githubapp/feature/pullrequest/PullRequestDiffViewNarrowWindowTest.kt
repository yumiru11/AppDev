package com.yumiru11.githubapp.feature.pullrequest

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.ScrollAxisRange
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.dp
import com.yumiru11.githubapp.core.designsystem.theme.AppTheme
import com.yumiru11.githubapp.feature.pullrequest.model.DiffLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * UI-1：窄窗口（< 600dp）下 unified-only + 超长行横向可达的回归测试。
 *
 * 红→绿双向验证（AGENTS.md 方法学，逐条实证形态）：
 * - [diffView_narrowWindow_doesNotOfferSideBySide]：改动前切换按钮恒在 → 必红；
 * - [diffView_narrowWindow_longLine_laidOutAtFullWidth]：改动前行宽 = 视口−行号区（411−104≈307dp），
 *   小于 600dp 阈值 → 必红；现在按自然宽（~1250dp）排版；
 * - [diffView_narrowWindow_longLine_horizontalScrollRevealsLineEnd]：改动前文件内无任何
 *   `horizontalScroll` → 找不到滚动节点 → 必红；
 * - [diffView_narrowWindow_shortLines_columnFillsViewport]：去掉列宽下限（widthIn(min)）后
 *   短行行宽塌缩为内容宽 → 必红（本测试锁定列宽 = max(视口, 最长行)）。
 *
 * Robolectric 的 `qualifiers` 同时决定 `LocalConfiguration.screenWidthDp` 与窗口尺寸，
 * 与被测代码的宽度判定同一来源（w411dp = 手机；Panel 测试见同目录 Wide 测试）。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class PullRequestDiffViewNarrowWindowTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun setDiffContent(diff: List<DiffLine>) {
        composeRule.setContent {
            AppTheme(darkTheme = false) {
                Box(modifier = Modifier.size(width = 411.dp, height = 891.dp)) {
                    PullRequestDiffView(
                        path = DIFF_FIXTURE_PATH,
                        diff = diff,
                        comments = emptyList(),
                        threads = emptyList(),
                        onLineComment = { _, _, _ -> },
                    )
                }
            }
        }
    }

    @Test
    fun diffView_narrowWindow_doesNotOfferSideBySide() {
        setDiffContent(diffViewFixtureLines())

        composeRule.onNodeWithText("Unified").assertDoesNotExist()
        composeRule.onNodeWithText("Side-by-side").assertDoesNotExist()
    }

    @Test
    fun diffView_narrowWindow_longLine_laidOutAtFullWidth() {
        setDiffContent(diffViewFixtureLines())

        val longLine =
            composeRule
                .onNodeWithText(LONG_DIFF_LINE, useUnmergedTree = true)
                .assertExists()

        val bounds = longLine.getUnclippedBoundsInRoot()
        val width = bounds.right - bounds.left
        assertTrue(
            "超长行未按自然宽度排版（实测 $width，视口 411dp）—— 行仍被截断到视口宽度",
            width > 600.dp,
        )
    }

    @Test
    fun diffView_narrowWindow_longLine_horizontalScrollRevealsLineEnd() {
        setDiffContent(diffViewFixtureLines())

        val scrollable = composeRule.onNode(hasHorizontalScrollRange()).assertExists()
        val before = scrollable.fetchSemanticsNode().scrollRange
        assertTrue(
            "内容宽未超过视口（maxValue=${before.maxValue()}）—— 超长行不可横向滚动",
            before.maxValue() > 0f,
        )
        assertEquals("初始滚动位置应为行首", 0f, before.value())

        scrollable.performSemanticsAction(SemanticsActions.ScrollBy) { it(500f, 0f) }

        val after = composeRule.onNode(hasHorizontalScrollRange()).fetchSemanticsNode().scrollRange
        assertTrue("横向滚动后 offset 仍为 0 —— 行尾不可达", after.value() > 0f)
    }

    @Test
    fun diffView_narrowWindow_shortLines_columnFillsViewport() {
        setDiffContent(shortDiffViewFixtureLines())

        val shortRow =
            composeRule
                .onNode(hasClickAction() and hasText(SHORT_DIFF_LINE, substring = true))
                .assertExists()
        val rowBounds = shortRow.getUnclippedBoundsInRoot()
        val rowWidth = rowBounds.right - rowBounds.left
        assertTrue(
            "短行行宽 $rowWidth 未铺满视口 411dp —— 列宽应 = max(视口, 最长行)，否则底色条纹参差",
            rowWidth > 380.dp,
        )

        val range = composeRule.onNode(hasHorizontalScrollRange()).fetchSemanticsNode().scrollRange
        assertEquals("纯短行 diff 不应产生横向滚动距离", 0f, range.maxValue())
    }
}

/** 横向滚动容器的语义判据（`Modifier.horizontalScroll` 暴露的滚动轴范围）。 */
internal fun hasHorizontalScrollRange(): SemanticsMatcher =
    SemanticsMatcher("has horizontal scroll range") { node ->
        node.config.contains(SemanticsProperties.HorizontalScrollAxisRange)
    }

/** 节点上的横向滚动轴范围（不存在时抛断言错误，便于测试给出清晰失败）。 */
internal val SemanticsNode.scrollRange: ScrollAxisRange
    get() {
        check(config.contains(SemanticsProperties.HorizontalScrollAxisRange)) {
            "节点没有横向滚动语义（HorizontalScrollAxisRange）"
        }
        return config[SemanticsProperties.HorizontalScrollAxisRange]
    }
