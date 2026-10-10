package com.yumiru11.githubapp.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.yumiru11.githubapp.core.designsystem.token.AppDimens
import com.yumiru11.githubapp.core.designsystem.token.AppIcon

/**
 * 预设 seed 色盘（T24；默认值 = [com.yumiru11.githubapp.core.datastore.preferences.UserPreferencesRepository.DEFAULT_SEED_COLOR]）
 * 与色名资源（#87 无障碍：TalkBack 读色名而不是十六进制）。
 */
internal val SEED_COLORS: List<Long> =
    listOf(
        0xFF0969DA, // GitHub brand blue
        0xFF7C3AED, // purple
        0xFF0EA5E9, // sky
        0xFF10B981, // emerald
        0xFFF59E0B, // amber
        0xFFEF4444, // red
        0xFFEC4899, // pink
        0xFF64748B, // slate
    )

/** 色值 → 色名资源（与 [SEED_COLORS] 一一对应）。 */
internal val SEED_COLOR_NAMES: Map<Long, Int> =
    mapOf(
        0xFF0969DA to R.string.settings_seed_blue,
        0xFF7C3AED to R.string.settings_seed_purple,
        0xFF0EA5E9 to R.string.settings_seed_sky,
        0xFF10B981 to R.string.settings_seed_emerald,
        0xFFF59E0B to R.string.settings_seed_amber,
        0xFFEF4444 to R.string.settings_seed_red,
        0xFFEC4899 to R.string.settings_seed_pink,
        0xFF64748B to R.string.settings_seed_slate,
    )

/** 单个 seed 色块：24dp 视觉圆 + 48dp 最小触区包裹（缺陷 #8 触点修复）。 */
@Composable
internal fun SeedSwatch(
    color: Long,
    name: String,
    isSelected: Boolean,
    enabled: Boolean,
    onSelect: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .size(AppDimens.minTouchTarget)
                .semantics { contentDescription = name }
                .selectable(
                    selected = isSelected,
                    enabled = enabled,
                    role = Role.RadioButton,
                    onClick = onSelect,
                ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
                Modifier
                    .size(AppIcon.iconMedium)
                    .clip(CircleShape)
                    .background(Color(color))
                    .then(
                        if (isSelected) {
                            Modifier.border(
                                width = 3.dp,
                                color = MaterialTheme.colorScheme.primary,
                                shape = CircleShape,
                            )
                        } else {
                            Modifier
                        },
                    ),
        )
    }
}
