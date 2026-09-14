package com.yumiru11.githubapp.feature.home.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.yumiru11.githubapp.core.designsystem.component.AppCard
import com.yumiru11.githubapp.core.designsystem.token.AppDimens
import com.yumiru11.githubapp.feature.home.R

/** 骨架屏容器测试标签（UI 测试定位；装饰条自身无文本可断言）。 */
internal const val FEED_SKELETON_TAG = "home-feed-skeleton"

/** 单张骨架卡的测试标签（断言行数与 [FEED_SKELETON_ROWS] 一致）。 */
internal const val FEED_SKELETON_ROW_TAG = "home-feed-skeleton-row"

/**
 * 首屏骨架卡行数：按 411×891dp 视口（既有多数模块的截图尺寸）取 6 行——
 * 快捷入口 + 玻璃头之下铺满首屏，末行自然溢出由 [Modifier.clipToBounds] 裁切，
 * 内容到达时不出现「骨架先撑满、内容缩一半」的跳变。
 */
internal const val FEED_SKELETON_ROWS = 6

/** 骨架条相对卡底色的对比度（`onSurfaceVariant` 叠在 `surfaceVariant` 卡上）。 */
private const val SKELETON_BAR_ALPHA = 0.3f

/** 首行动作文案条：对应 FeedRow 的 titleSmall（两行封顶）。 */
private const val TITLE_BAR_WIDTH_FRACTION = 0.6f

/** 次行正文条：对应 FeedRow 的 item.title（bodyMedium）。 */
private const val BODY_BAR_WIDTH_FRACTION = 0.9f

/** 末行元信息条：对应 FeedRow 的仓库名 + 时间（bodySmall/labelSmall）。 */
private const val META_BAR_WIDTH_FRACTION = 0.35f

/** 骨架条高：14/12/10dp 近似 titleSmall / bodyMedium / labelSmall 的行高。 */
private val titleBarHeight = 14.dp
private val bodyBarHeight = 12.dp
private val metaBarHeight = 10.dp

/** 头像占位直径，与 [FeedRow] 的真实头像同尺寸（32dp）——切换时不产生横向跳变。 */
private val avatarSize = 32.dp

/**
 * feed 首载骨架屏（UI-7；源 S3 UI-C10）。
 *
 * ## 语义与范围
 *
 * 静态镜像 [FeedRow] 的卡形：头像圆 + 动作文案条 + 正文条 + 元信息条，容器色与真实行
 * 同为 `surfaceVariant`，几何走 [AppDimens.spacing] / [AppDimens.cornerExtraSmall]
 * （实现计划 §2 决策 7 的间距 scale），零硬编码颜色。只在 feed **首载**（列表为空且
 * refresh 挂起）时替代共享 [com.yumiru11.githubapp.core.designsystem.component.AppLoadingState]；
 * 有内容的下拉刷新仍走 `PullToRefreshBox` 的既有指示器。
 *
 * ## 为什么是静态（无 shimmer）
 *
 * 无限动画会让 Roborazzi 的 compose 捕获路径在 `ShadowLooper.idle()` 上永不返回
 * （#C 根因，见 `captureScreenshotDeterministic` KDoc）。骨架是过渡态，静态即可读；
 * 若后续要加微光，必须改走 `core:testing` 的确定性捕获路径。
 *
 * ## 为什么留在 feature:home（而非 core:designsystem）
 *
 * 本组件绑死 [FeedRow] 的几何与配色（卡内 12dp、32dp 圆、同色角色），不是通用原语；
 * 按 ADR-0010 的批次纪律，等第二处真实消费出现再抽 `AppSkeleton`（YAGNI）。
 * 届时几何参数化、本文件降级为 Feed 专属调用点即可。
 */
@Composable
internal fun FeedSkeleton(modifier: Modifier = Modifier) {
    val loadingLabel = stringResource(R.string.feed_loading)
    Column(
        modifier =
            modifier
                .clipToBounds()
                .padding(horizontal = AppDimens.contentPadding)
                // 整块只播报一次「正在加载」；骨架条本身是纯装饰、无独立语义
                .semantics { contentDescription = loadingLabel }
                .testTag(FEED_SKELETON_TAG),
        verticalArrangement = Arrangement.spacedBy(AppDimens.spacing.s),
    ) {
        repeat(FEED_SKELETON_ROWS) {
            FeedSkeletonRow()
        }
    }
}

/** 单张骨架卡：几何与 [FeedRow] 的 `AppCard(surfaceVariant) + Row(12dp)` 逐项对齐。 */
@Composable
private fun FeedSkeletonRow() {
    AppCard(
        modifier = Modifier.fillMaxWidth().testTag(FEED_SKELETON_ROW_TAG),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(modifier = Modifier.padding(AppDimens.spacing.m)) {
            SkeletonBlock(modifier = Modifier.size(avatarSize), shape = CircleShape)
            Spacer(modifier = Modifier.width(AppDimens.spacing.m))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(AppDimens.spacing.xs),
            ) {
                SkeletonBlock(modifier = Modifier.height(titleBarHeight).fillMaxWidth(TITLE_BAR_WIDTH_FRACTION))
                SkeletonBlock(modifier = Modifier.height(bodyBarHeight).fillMaxWidth(BODY_BAR_WIDTH_FRACTION))
                SkeletonBlock(modifier = Modifier.height(metaBarHeight).fillMaxWidth(META_BAR_WIDTH_FRACTION))
            }
        }
    }
}

/** 单一骨架条：色值取 `onSurfaceVariant` × 固定 alpha（卡片同色角色系，零硬编码颜色值）。 */
@Composable
private fun SkeletonBlock(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(AppDimens.cornerExtraSmall),
) {
    Box(
        modifier =
            modifier
                .clip(shape)
                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = SKELETON_BAR_ALPHA)),
    )
}
