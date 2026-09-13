package com.yumiru11.githubapp.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.yumiru11.githubapp.core.designsystem.theme.extendedColors
import com.yumiru11.githubapp.core.designsystem.token.AppDimens

/**
 * GitHub 领域状态（#84 状态色语义表）。
 *
 * 覆盖 Issue/PR 详情（OPEN/CLOSED/MERGED/DRAFT）与 PR 合并前检查
 * （MERGEABLE/CONFLICTING）——后者由 #17 MergeBox 等后续票消费。
 */
enum class GitHubStatus {
    OPEN,
    CLOSED,
    MERGED,
    DRAFT,
    MERGEABLE,
    CONFLICTING,
}

/**
 * 状态 → 语义色角色（纯映射，[gitHubStatusColorRole] 可单测）。
 *
 * 角色是**色语义的唯一词汇表**：Issue/PR 状态与 Checks 状态（pending →
 * WARNING）都归一到这 6 个角色，具体色值只由 [appStateColors] 决定
 * （plan.md §5.3「成功/进行中/失败/跳过/中性应各不相同」）。
 */
enum class AppStateColorRole {
    SUCCESS,
    DANGER,
    WARNING,
    TERTIARY,
    SURFACE_VARIANT,
    ERROR,
}

/**
 * 语义色角色的三件套：强调色（圆点/图标）、容器底色、容器内容色。
 *
 * 与 `LabelChipColors` 同款「把色值决策收敛到一处」的做法：调用方只认角色，
 * 不认色槽，深浅主题自动适配。
 */
@Immutable
data class AppStateColors(
    val accent: Color,
    val container: Color,
    val onContainer: Color,
)

/**
 * 语义色角色 → 实际色值（零硬编码色：全部取自 [ExtendedColors] / [MaterialTheme]）。
 *
 * `WARNING`（Checks pending）取自 plan.md §5.3 的扩展色 warning 家族。
 */
@Composable
fun appStateColors(role: AppStateColorRole): AppStateColors {
    val colorScheme = MaterialTheme.colorScheme
    val extended = MaterialTheme.extendedColors
    return when (role) {
        AppStateColorRole.SUCCESS -> {
            AppStateColors(extended.success, extended.successContainer, extended.onSuccessContainer)
        }

        AppStateColorRole.DANGER -> {
            AppStateColors(extended.danger, extended.dangerContainer, extended.onDangerContainer)
        }

        AppStateColorRole.WARNING -> {
            AppStateColors(extended.warning, extended.warningContainer, extended.onWarningContainer)
        }

        AppStateColorRole.TERTIARY -> {
            AppStateColors(colorScheme.tertiary, colorScheme.tertiaryContainer, colorScheme.onTertiaryContainer)
        }

        AppStateColorRole.SURFACE_VARIANT -> {
            AppStateColors(colorScheme.outline, colorScheme.surfaceVariant, colorScheme.onSurfaceVariant)
        }

        AppStateColorRole.ERROR -> {
            AppStateColors(colorScheme.error, colorScheme.errorContainer, colorScheme.onErrorContainer)
        }
    }
}

/**
 * GitHub 状态 → 语义色角色的纯映射（audit 缺陷 #4：四态不再同用 secondaryContainer）。
 *
 * 语义表：open→success、closed→danger、merged→tertiary、draft→surfaceVariant、
 * mergeable→success、conflicting→error。颜色在 [AppStateChip] 经
 * ExtendedColors / MaterialTheme 消费规范化。
 */
fun gitHubStatusColorRole(status: GitHubStatus): AppStateColorRole =
    when (status) {
        GitHubStatus.OPEN, GitHubStatus.MERGEABLE -> AppStateColorRole.SUCCESS
        GitHubStatus.CLOSED -> AppStateColorRole.DANGER
        GitHubStatus.MERGED -> AppStateColorRole.TERTIARY
        GitHubStatus.DRAFT -> AppStateColorRole.SURFACE_VARIANT
        GitHubStatus.CONFLICTING -> AppStateColorRole.ERROR
    }

/**
 * GitHub 状态徽标：语义色圆点 + 标签的胶囊 chip。
 *
 * - 颜色按 [gitHubStatusColorRole] 取 ExtendedColors/MaterialTheme 语义 token，
 *   深浅主题自动适配；零硬编码色
 * - label 由调用方传入本地化文案（stringResource），本组件不内嵌字符串
 * - `mergeDescendants` 合并语义节点，TalkBack 单焦点朗读完整标签（audit 缺陷 #18）
 * - 圆点为纯装饰（无 contentDescription），状态信息由文本承载
 * - [stateDescription] 补状态播报（issue #168 / UI26）：调用方传本地化**短语**
 *   （如 en "This item is open" / zh "该项已开启"），与可见标签互补而非逐字重复，
 *   避免 TalkBack 把同一句话播报两遍
 */
@Composable
fun AppStateChip(
    status: GitHubStatus,
    label: String,
    modifier: Modifier = Modifier,
    stateDescription: String? = null,
) {
    AppStateChip(
        role = gitHubStatusColorRole(status),
        label = label,
        modifier = modifier,
        stateDescription = stateDescription,
    )
}

/**
 * 按语义色角色渲染同一款徽标。
 *
 * 供 [GitHubStatus] 覆盖不到的领域状态复用（如 Checks 的 pending →
 * [AppStateColorRole.WARNING]，plan.md §5.3），避免各 feature 自己造
 * 「状态 → 颜色」的第二套映射。
 */
@Composable
fun AppStateChip(
    role: AppStateColorRole,
    label: String,
    modifier: Modifier = Modifier,
    stateDescription: String? = null,
) {
    val colors = appStateColors(role)
    Surface(
        modifier =
            modifier.semantics(mergeDescendants = true) {
                if (stateDescription != null) this.stateDescription = stateDescription
            },
        shape = RoundedCornerShape(percent = 50),
        color = colors.container,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = AppDimens.spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(colors.accent),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = colors.onContainer,
            )
        }
    }
}
