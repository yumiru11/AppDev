@file:OptIn(ExperimentalFoundationApi::class)

package com.yumiru11.githubapp.core.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

/**
 * 首页 HorizontalPager，4 页内容：Trending / News / Issues / Pull Requests。
 *
 * 每页为可滚动占位列表。
 */
@Composable
fun HomePager(
    pagerState: PagerState,
    modifier: Modifier = Modifier,
) {
    val tabTitles =
        listOf(
            stringResource(R.string.tab_trending),
            stringResource(R.string.tab_news),
            stringResource(R.string.tab_issues),
            stringResource(R.string.tab_prs),
        )

    HorizontalPager(
        state = pagerState,
        modifier = modifier,
    ) { page ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Text(
                    text = tabTitles[page],
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            items(10) { index ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Box(
                        modifier = Modifier.padding(16.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Text(
                            text = stringResource(R.string.placeholder_item, index + 1),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }
        }
    }
}
