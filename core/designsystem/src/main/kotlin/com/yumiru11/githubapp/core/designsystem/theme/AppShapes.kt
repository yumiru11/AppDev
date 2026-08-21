package com.yumiru11.githubapp.core.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.yumiru11.githubapp.core.designsystem.token.AppDimens

/** Resolved corner radii (single source of truth = [AppDimens] × cornerScale). */
data class CornerTokens(
    val extraSmall: Dp,
    val small: Dp,
    val medium: Dp,
    val large: Dp,
    val extraLarge: Dp,
)

/**
 * Material 3 [Shapes] factory bound to the [AppDimens] corner tokens.
 *
 * ui-design.md §1.1-5 mandates "圆角全覆盖": every container/button/input/card/sheet
 * resolves its radius from the shape system. Injecting these shapes into
 * [androidx.compose.material3.MaterialTheme] removes the dual source of truth between
 * the M3 defaults and [AppDimens] — components consume `MaterialTheme.shapes.*`.
 *
 * At [DEFAULT_CORNER_SCALE] (= 1f) the radii equal the M3 defaults
 * (4/8/12/16/28 dp), so visuals are unchanged until the user moves the
 * settings "圆角强度" slider (DataStore cornerScale, wired via AppTheme).
 */
object AppShapes {
    /** Mirror of the DataStore cornerScale slider bounds (UserPreferencesRepository contract). */
    const val MIN_CORNER_SCALE: Float = 0.5f

    /** Mirror of the DataStore cornerScale slider bounds (UserPreferencesRepository contract). */
    const val MAX_CORNER_SCALE: Float = 1.5f

    /**
     * Resolve the effective corner radii for a user scale factor.
     *
     * Pure function (unit-tested): out-of-range input is coerced into
     * [MIN_CORNER_SCALE]..[MAX_CORNER_SCALE], matching the settings slider range.
     */
    fun resolveCornerTokens(cornerScale: Float): CornerTokens {
        val scale = cornerScale.coerceIn(MIN_CORNER_SCALE, MAX_CORNER_SCALE)
        return CornerTokens(
            extraSmall = AppDimens.cornerExtraSmall * scale,
            small = AppDimens.cornerSmall * scale,
            medium = AppDimens.cornerMedium * scale,
            large = AppDimens.cornerLarge * scale,
            extraLarge = AppDimens.cornerExtraLarge * scale,
        )
    }

    /** Build the M3 [Shapes] for a user corner scale factor. */
    fun from(cornerScale: Float): Shapes {
        val tokens = resolveCornerTokens(cornerScale)
        return Shapes(
            extraSmall = RoundedCornerShape(tokens.extraSmall),
            small = RoundedCornerShape(tokens.small),
            medium = RoundedCornerShape(tokens.medium),
            large = RoundedCornerShape(tokens.large),
            extraLarge = RoundedCornerShape(tokens.extraLarge),
        )
    }
}
