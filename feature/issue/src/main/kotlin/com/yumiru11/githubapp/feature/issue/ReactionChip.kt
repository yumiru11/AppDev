package com.yumiru11.githubapp.feature.issue

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.yumiru11.githubapp.core.designsystem.token.AppDimens

/** 反应 chip 命中区测试标记（触区断言用，issue #168 / UI24）。 */
internal const val REACTION_CHIP_HIT_TAG = "reaction-chip-hit"

/** 反应 chip 视觉胶囊测试标记（「视觉不膨胀」断言用，issue #168 / UI24）。 */
internal const val REACTION_CHIP_PILL_TAG = "reaction-chip-pill"

/** 视觉胶囊高度上限：命中区扩到 48dp 后，胶囊本体仍须 ≤ 此值才算「没膨胀」。 */
internal val REACTION_CHIP_MAX_VISUAL_HEIGHT = 40.dp

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
            modifier = Modifier.testTag(REACTION_CHIP_PILL_TAG),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text(
                    text = content,
                    style = MaterialTheme.typography.labelMedium,
                    color = contentColor,
                )
                if (count > 0) {
                    Spacer(modifier = Modifier.width(4.dp))
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
