package com.yumiru11.githubapp.core.designsystem.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch

/**
 * 分区条 + 分页内容（需求：分区支持左右滑动切换，顶部条跟随滑动）。
 *
 * 双向联动：
 * - 滑动 → [AppScrollableTabs] 的选中项即时跟随目标页（[PagerState.targetPage]）；
 * - 点条 → 动画滚页（弹簧，由调用方通过 [animateToPage] 注入，默认 [PagerState.animateScrollToPage]）。
 *
 * 用法（首页 / 仓库页 / PR 详情共用）：
 * \`\`\`
 * val pagerState = rememberPagerState { tabs.size }
 * AppSectionPager(tabs, pagerState, onTabClick = { onTabSwitched() }) { page -> PageContent(page) }
 * \`\`\`
 *
 * @param tabs 分区项（标签 + 可选图标/角标）
 * @param pagerState 分页状态（调用方 remember，便于与其它状态联动）
 * @param onTabClick 点条时的额外回调（例如触发 fade-through 淡入淡出）
 * @param pageContent 每页内容；调用方负责各页自己的滚动容器与底部避让
 */
@Composable
fun AppSectionPager(
    tabs: List<AppTab>,
    pagerState: PagerState,
    modifier: Modifier = Modifier,
    showDivider: Boolean = true,
    onTabClick: (Int) -> Unit = {},
    pageContent: @Composable (page: Int) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val selectedIndex = pagerState.targetPage.coerceIn(0, (tabs.size - 1).coerceAtLeast(0))
    Column(modifier = modifier.fillMaxSize()) {
        AppScrollableTabs(
            tabs = tabs,
            selectedIndex = selectedIndex,
            onSelect = { index ->
                if (index != pagerState.targetPage) {
                    onTabClick(index)
                    scope.launch { pagerState.animateScrollToPage(index) }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            showDivider = showDivider,
        )
        androidx.compose.foundation.pager.HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            pageContent(page)
        }
    }
}

/**
 * 折叠头部的进度纯函数：滚动偏移 → 0..1 的收起比例（供仓库页与单测共用）。
 *
 * @param scrollPx 当前滚动偏移（WebView 回传的 px 或 nestedScroll 累计 px）
 * @param collapseDistancePx 完全收起所需距离（越大越"迟钝"）
 */
fun headerCollapseFraction(
    scrollPx: Int,
    collapseDistancePx: Int,
): Float {
    if (collapseDistancePx <= 0) return 0f
    return (scrollPx.toFloat() / collapseDistancePx.toFloat()).coerceIn(0f, 1f)
}

/** 折叠头可见高度：自然高度按收起比例收缩（0 = 完全收起，只留分区条）。 */
fun collapsedHeaderHeight(
    naturalHeightPx: Int,
    collapseFraction: Float,
): Int = (naturalHeightPx * (1f - collapseFraction.coerceIn(0f, 1f))).toInt().coerceAtLeast(0)

/** 折叠头向上位移：让内容"收进裁剪区"而不是原地压缩（UI10 观感）。 */
fun collapsedHeaderDrift(
    naturalHeightPx: Int,
    collapseFraction: Float,
    drift: Float = 0.25f,
): Int = (-collapseFraction.coerceIn(0f, 1f) * naturalHeightPx * drift).toInt()

/** 分区切换时的复位：把折叠头恢复为展开（新分区从顶部开始）。 */
@Composable
fun ResetCollapseOnPageChange(
    page: Int,
    onReset: () -> Unit,
) {
    LaunchedEffect(page) { onReset() }
}
