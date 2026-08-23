package com.yumiru11.githubapp.core.designsystem.component

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.yumiru11.githubapp.core.designsystem.token.AppDimens
import com.yumiru11.githubapp.core.designsystem.token.LocalMotionScale

/** 一组分段条目的四角半径（纯数据，供 [cardGroupSegmentCorners] 单测断言）。 */
data class GroupSegmentCorners(
    val topStart: Dp,
    val topEnd: Dp,
    val bottomStart: Dp,
    val bottomEnd: Dp,
)

/**
 * 纯函数：共 [count] 个条目中第 [index] 个在常态/[pressed] 态的四角半径。
 *
 * 首条目（index == 0）上缘与末条目（index == count-1）下缘取外圆角；
 * 按压时整段四角放大到外圆角（弹性「鼓起」）；单条目组恒为全外圆角。
 */
fun cardGroupSegmentCorners(
    count: Int,
    index: Int,
    pressed: Boolean,
): GroupSegmentCorners {
    val outer = AppDimens.cornerCardOuter
    val inner = AppDimens.cornerCardInner
    val isSingle = count == 1
    val isFirst = index == 0
    val isLast = index == count - 1
    val topCorner = if (pressed || isSingle || isFirst) outer else inner
    val bottomCorner = if (pressed || isSingle || isLast) outer else inner
    return GroupSegmentCorners(
        topStart = topCorner,
        topEnd = topCorner,
        bottomStart = bottomCorner,
        bottomEnd = bottomCorner,
    )
}

@DslMarker
private annotation class CardGroupDsl

/** [CardGroup] 内容作用域：按序收集分段条目。 */
@CardGroupDsl
interface CardGroupScope {
    /** 追加一个条目；[onClick] 非空时整段可点击并参与按压形变。 */
    fun item(
        onClick: (() -> Unit)? = null,
        content: @Composable () -> Unit,
    )
}

private class CardGroupScopeImpl : CardGroupScope {
    val entries = mutableListOf<CardGroupEntry>()

    override fun item(
        onClick: (() -> Unit)?,
        content: @Composable () -> Unit,
    ) {
        entries += CardGroupEntry(onClick = onClick, content = content)
    }
}

private data class CardGroupEntry(
    val onClick: (() -> Unit)?,
    val content: @Composable () -> Unit,
)

/** 分段间距（条目间留缝，让按压「鼓起」的整段圆角可见）。 */
private val SegmentSpacing: Dp = 2.dp

/** 无动效阈值：生效动效缩放 ≤ 0（系统「移除动画」）时形变即时完成。 */
private const val NO_MOTION_SCALE = 0f

/**
 * 分组卡（ui-audit 提案 #2 / #87）：设置页「原生系统设置式」分段卡容器。
 *
 * - 形态：外圆角 [AppDimens.cornerCardOuter]（20dp）/ 内条目 [AppDimens.cornerCardInner]
 *   （4dp）——首条目上缘、末条目下缘取外圆角，中段条目四角取内圆角，视觉成一组分段卡
 * - 弹性形变：可点击条目按压时四角弹性放大到外圆角（整段「鼓起」为独立卡），松手弹回；
 *   参考 rikkahub CardGroup 思路（AGPL，仅参考交互思路、零代码复制）：spring 低回弹 +
 *   圆角下限钳制；动效缩放为 0（系统「移除动画」）时即时完成（ui-design §4.4）
 * - 条目经 [CardGroupScope.item] DSL 收集后统一渲染（首/末位自动计算圆角，调用方零簿记）
 *
 * 用法：CardGroup { item { Row1() }; item(onClick = ::open) { Row2() } }
 */
@Composable
fun CardGroup(
    modifier: Modifier = Modifier,
    content: CardGroupScope.() -> Unit,
) {
    val scope = CardGroupScopeImpl()
    scope.content()

    Column(modifier = modifier.fillMaxWidth()) {
        val count = scope.entries.size
        scope.entries.forEachIndexed { index, entry ->
            CardGroupSegment(entry = entry, index = index, count = count)
            if (index != count - 1) {
                Spacer(modifier = Modifier.height(SegmentSpacing))
            }
        }
    }
}

/** 单个分段条目：面底色 + 动画圆角裁剪 + 可选整段点击。 */
@Composable
private fun CardGroupSegment(
    entry: CardGroupEntry,
    index: Int,
    count: Int,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val corners = cardGroupSegmentCorners(count = count, index = index, pressed = pressed)

    // 弹性形变：spring 低回弹（§4.2）；动效缩放为 0 时 snap 即时完成（§4.4）。
    // 圆角钳制 ≥ 0：spring 回弹过冲不得产生负圆角（RoundedCornerShape 会抛异常）。
    val motionScale = LocalMotionScale.current
    val cornerSpec =
        if (motionScale <= NO_MOTION_SCALE) {
            snap<Dp>()
        } else {
            spring<Dp>(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow)
        }
    val topCorner by animateDpAsState(targetValue = corners.topStart, animationSpec = cornerSpec, label = "cardGroupTopCorner")
    val bottomCorner by animateDpAsState(targetValue = corners.bottomStart, animationSpec = cornerSpec, label = "cardGroupBottomCorner")

    Surface(
        shape =
            RoundedCornerShape(
                topStart = topCorner.coerceAtLeast(0.dp),
                topEnd = topCorner.coerceAtLeast(0.dp),
                bottomStart = bottomCorner.coerceAtLeast(0.dp),
                bottomEnd = bottomCorner.coerceAtLeast(0.dp),
            ),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(
            modifier =
                Modifier.clickable(
                    interactionSource = interactionSource,
                    indication = LocalIndication.current,
                    enabled = entry.onClick != null,
                    onClick = entry.onClick ?: {},
                ),
        ) {
            entry.content()
        }
    }
}
