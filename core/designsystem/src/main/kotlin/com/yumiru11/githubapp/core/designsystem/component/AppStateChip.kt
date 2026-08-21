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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.yumiru11.githubapp.core.designsystem.theme.extendedColors

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

/** 状态 → 语义色角色（纯映射，[gitHubStatusColorRole] 可单测） */
enum class AppStateColorRole {
    SUCCESS,
    DANGER,
    TERTIARY,
    SURFACE_VARIANT,
    ERROR,
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
 */
@Composable
fun AppStateChip(
    status: GitHubStatus,
    label: String,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    val extended = MaterialTheme.extendedColors
    val (dotColor, containerColor, contentColor) =
        when (gitHubStatusColorRole(status)) {
            AppStateColorRole.SUCCESS ->
                Triple(extended.success, extended.successContainer, extended.onSuccessContainer)

            AppStateColorRole.DANGER ->
                Triple(extended.danger, extended.dangerContainer, extended.onDangerContainer)

            AppStateColorRole.TERTIARY ->
                Triple(colorScheme.tertiary, colorScheme.tertiaryContainer, colorScheme.onTertiaryContainer)

            AppStateColorRole.SURFACE_VARIANT ->
                Triple(colorScheme.outline, colorScheme.surfaceVariant, colorScheme.onSurfaceVariant)

            AppStateColorRole.ERROR ->
                Triple(colorScheme.error, colorScheme.errorContainer, colorScheme.onErrorContainer)
        }
    Surface(
        modifier = modifier.semantics(mergeDescendants = true) {},
        shape = RoundedCornerShape(percent = 50),
        color = containerColor,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(dotColor),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = contentColor,
            )
        }
    }
}
