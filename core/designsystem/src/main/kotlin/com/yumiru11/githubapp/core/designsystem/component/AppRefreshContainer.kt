package com.yumiru11.githubapp.core.designsystem.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Arrow_downward
import com.yumiru11.githubapp.core.designsystem.token.AppDimens

/**
 * 下拉刷新容器（基准 = M3E \`GhRefreshContainer\`）：圆面指示器 + 波形进度。
 *
 * 指示器外观随下拉距离缩放（[PullToRefreshState.distanceFraction]），刷新中显示
 * [AppWavyProgress]（动效 0 时静态，见其 KDoc）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRefreshContainer(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    indicatorTopPadding: androidx.compose.ui.unit.Dp = AppDimens.spacing.m,
    content: @Composable BoxScope.() -> Unit,
) {
    val state = rememberPullToRefreshState()
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier,
        state = state,
        indicator = {
            val fraction = if (isRefreshing) 1f else state.distanceFraction.coerceIn(0f, 1f)
            if (fraction > 0.01f) {
                Box(
                    modifier =
                        Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = indicatorTopPadding)
                            .graphicsLayer {
                                scaleX = fraction
                                scaleY = fraction
                                alpha = fraction
                            },
                ) {
                    Surface(
                        shape = RoundedCornerShape(percent = 50),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shadowElevation = 3.dp,
                        modifier = Modifier.size(40.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            if (isRefreshing) {
                                AppWavyProgress(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .height(18.dp)
                                            .padding(horizontal = AppDimens.spacing.s),
                                    strokeWidth = 2.dp,
                                )
                            } else {
                                Icon(
                                    imageVector = MaterialSymbols.Rounded.Arrow_downward,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    }
                }
            }
        },
        content = content,
    )
}
