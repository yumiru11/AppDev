package com.yumiru11.githubapp.feature.issue.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.yumiru11.githubapp.core.designsystem.token.AppDimens
import com.yumiru11.githubapp.core.designsystem.token.AppMotion

/** 反应 chip 命中区测试标记（触区断言用，issue #168 / UI24）。 */
internal const val REACTION_CHIP_HIT_TAG = "reaction-chip-hit"

/** 反应 chip 视觉胶囊测试标记（「视觉不膨胀」断言用，issue #168 / UI24）。 */
internal const val REACTION_CHIP_PILL_TAG = "reaction-chip-pill"

/** 视觉胶囊高度上限：命中区扩到 48dp 后，胶囊本体仍须 ≤ 此值才算「没膨胀」。 */
internal val REACTION_CHIP_MAX_VISUAL_HEIGHT = 40.dp

/** 反应命中瞬间的放大峰值（§4.3 回弹：点赞类图标轻弹一下）。 */
private const val REACTION_POP_SCALE = 1.25f

/**
 * 单个反应 chip（issue #168 / UI24，ui-audit #15）。
 *
 * 几何：**命中区 48dp × 48dp（[AppDimens.minTouchTarget]），视觉胶囊保持 ~32dp**。
 * 旧实现把 48dp 直接加在胶囊本体上（heightIn）→ 触点达标但视觉膨胀、整行变高；
 * 现改为外层 Box 承担 [sizeIn]（Box 不向下传播最小约束，胶囊保持自身尺寸居中），
 * clickable 落在 48dp 的 Box 上 → 视觉外的命中区也真实可点（ReactionChipTouchTargetTest
 * 用「点击胶囊外沿」而非只看布局尺寸来锁定这一点）。
 *
 * 无障碍：已反应态播报状态描述（audit 缺陷 #18；文案由调用方 i18n 传入）。
 *
 * 动效（#167 / UI17，ui-design §4.1「小型状态变化 200ms」+ §4.3「回弹」）：
 * 命中反应时胶囊做一次 1.0 → 1.25 → 1.0 的微弹跳——冲击段走
 * [AppMotion.DURATION_SMALL_STATE_CHANGE] 折算时长（设置滑杆 × 系统缩放取 min），
 * 回落段走 HighBouncy 弹簧令牌。**首次组合不播**（进页面时整排 chip 一起弹很吵，
 * 也避免滚动中触发进入动画打断），缩放只作用于视觉胶囊（`graphicsLayer`），
 * 命中区与布局尺寸不变（UI24 的触点/不膨胀断言不受影响）。
 */
@Composable
internal fun ReactionChip(
    content: String,
    count: Int,
    reacted: Boolean,
    reactedStateText: String,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val containerColor =
        if (reacted) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        }
    val contentColor =
        if (reacted) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        }
    val bounceScale = rememberReactionBounceScale(reacted)
    Box(
        modifier =
            modifier
                .sizeIn(
                    minWidth = AppDimens.minTouchTarget,
                    minHeight = AppDimens.minTouchTarget,
                ).clip(MaterialTheme.shapes.small)
                .clickable(onClick = onToggle)
                .then(
                    if (reacted) {
                        Modifier.semantics { stateDescription = reactedStateText }
                    } else {
                        Modifier
                    },
                ).testTag(REACTION_CHIP_HIT_TAG),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = MaterialTheme.shapes.small,
            color = containerColor,
            modifier =
                Modifier
                    .testTag(REACTION_CHIP_PILL_TAG)
                    .graphicsLayer {
                        scaleX = bounceScale()
                        scaleY = bounceScale()
                    },
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = AppDimens.spacing.m, vertical = 6.dp),
            ) {
                Text(
                    text = content,
                    style = MaterialTheme.typography.labelMedium,
                    color = contentColor,
                )
                if (count > 0) {
                    Spacer(modifier = Modifier.width(AppDimens.spacing.xs))
                    Text(
                        text = count.toString(),
                        style = MaterialTheme.typography.labelMedium,
                        color = contentColor,
                    )
                }
            }
        }
    }
}

/**
 * 反应命中瞬间的缩放值读取器（1.0 → 1.25 → 1.0 的微弹跳，#167 / UI17）。
 *
 * 返回 lambda 而非 `State<Float>`：调用点只在 `graphicsLayer {}` 里读它，
 * 读取延迟到绘制阶段 → 每帧不触发重组（与 AppMotion「只重绘不重组」惯例一致）。
 *
 * - 冲击段：`AppMotion.DURATION_SMALL_STATE_CHANGE`（§4.1 小型状态变化 200ms）经
 *   [AppMotion.scaledDuration] 折算；回落段：HighBouncy 弹簧令牌（§4.3 回弹）
 * - **只在 reacted 真正翻转时播**：首次组合（进页面/滚动回收后重组合）不播，
 *   避免整排 chip 一起弹、也不在滚动中触发动画打断
 * - 折算为 0（系统「移除动画」或滑杆到底）→ 直接跳过，状态色照常切换
 */
@Composable
private fun rememberReactionBounceScale(reacted: Boolean): () -> Float {
    val bounce = remember { Animatable(1f) }
    var firstComposition by rememberSaveable { mutableStateOf(true) }
    val impactMillis = AppMotion.scaledDuration(AppMotion.DURATION_SMALL_STATE_CHANGE)
    LaunchedEffect(reacted) {
        if (firstComposition) {
            firstComposition = false
            return@LaunchedEffect
        }
        if (impactMillis <= 0) return@LaunchedEffect
        bounce.animateTo(
            targetValue = REACTION_POP_SCALE,
            animationSpec = tween(impactMillis, easing = AppMotion.EmphasizedDecelerate),
        )
        bounce.animateTo(
            targetValue = 1f,
            animationSpec =
                spring(
                    dampingRatio = AppMotion.DampingRatioHighBouncy,
                    stiffness = AppMotion.StiffnessMedium,
                ),
        )
    }
    return { bounce.value }
}
