package com.yumiru11.githubapp.core.ui

import androidx.compose.foundation.pager.PagerState
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * 首页可横滚 Tab 条：Trending / News / Issues / Pull Requests。
 *
 * Tab 指示器跟随 [pagerState] 联动。
 */
@Composable
fun HomeTabs(
    pagerState: PagerState,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val tabs =
        listOf(
            stringResource(R.string.tab_trending),
            stringResource(R.string.tab_news),
            stringResource(R.string.tab_issues),
            stringResource(R.string.tab_prs),
        )

    PrimaryScrollableTabRow(
        selectedTabIndex = pagerState.currentPage,
        modifier = modifier,
        edgePadding = 0.dp,
    ) {
        tabs.forEachIndexed { index, title ->
            Tab(
                selected = pagerState.currentPage == index,
                onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                text = { Text(title) },
            )
        }
    }
}
