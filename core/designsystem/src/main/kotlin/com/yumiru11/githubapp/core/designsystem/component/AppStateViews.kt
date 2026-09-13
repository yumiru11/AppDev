package com.yumiru11.githubapp.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.yumiru11.githubapp.core.designsystem.icon.AppDevOcticons
import com.yumiru11.githubapp.core.designsystem.token.AppDimens

/**
 * 空态占位：矢量插图 + 标题 + 可选说明 + 可选行动。
 *
 * 替换各 feature 手搓的空态（#84 / 设计系统 Batch 2）。插图由调用方按语境选择：
 * GitHub 语义走 [com.yumiru11.githubapp.core.designsystem.icon.AppDevOcticons]
 * （如仓库列表 Repo、Issue 列表 IssueOpened），通用语义走
 * [com.yumiru11.githubapp.core.designsystem.icon.AppIcons]；文案由调用方传本地化
 * stringResource，本组件不内嵌字符串。图标纯装饰（无 contentDescription），
 * 信息由文本承载；全 app 禁 emoji 图标。
 *
 * **厚包装 + 逃生舱**：默认形态 = 48dp 插图 + `titleMedium` 标题 + `bodyMedium` 说明
 * + 内置 [Button]（`actionLabel`/`onAction` 成对时渲染）；逃生舱 = `modifier`（布局，
 * 含外距/对齐）与 [action]（自定义行动区，替代内置按钮，供「非单按钮」特例）。
 *
 * @param icon 矢量插图（纯装饰，contentDescription 恒为 null）
 * @param title 主文案（如「暂无通知」）
 * @param message 次级说明（可空）
 * @param actionLabel 内置行动按钮文案（与 [onAction] 成对出现才渲染按钮）
 * @param onAction 内置行动回调（可空则不渲染按钮）
 * @param action 逃生舱：自定义行动区（非空时**替代**内置按钮；`actionLabel` 被忽略）
 */
@Composable
fun AppEmptyState(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    message: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    action: (@Composable () -> Unit)? = null,
) {
    AppMessageState(
        icon = icon,
        title = title,
        modifier = modifier,
        message = message,
        actionLabel = actionLabel,
        onAction = onAction,
        action = action,
    )
}

/**
 * 错误态占位：矢量插图（Octicons Alert）+ 标题 + 可选说明 + 可选行动。
 *
 * **厚包装 + 逃生舱**：契约与 [AppEmptyState] 一致，仅插图语义固定为 Alert（错误）。
 * 典型用法 = `title` 传本地化错误文案、`actionLabel`/`onAction` 传重试。
 *
 * @param title 主文案
 * @param message 次级说明（可空）
 * @param actionLabel 内置行动按钮文案（与 [onAction] 成对出现才渲染按钮）
 * @param onAction 内置行动回调（可空则不渲染按钮）；典型用法 actionLabel=重试
 * @param action 逃生舱：自定义行动区（非空时**替代**内置按钮；`actionLabel` 被忽略）
 */
@Composable
fun AppErrorState(
    title: String,
    modifier: Modifier = Modifier,
    message: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    action: (@Composable () -> Unit)? = null,
) {
    AppMessageState(
        icon = AppDevOcticons.Alert,
        title = title,
        modifier = modifier,
        message = message,
        actionLabel = actionLabel,
        onAction = onAction,
        action = action,
    )
}

/**
 * 加载态占位：居中形变加载指示 + 可选标签。
 *
 * 指示器用 M3 Expressive 的 [LoadingIndicator]（形变 `RoundedPolygon` 序列）而非旧
 * [CircularProgressIndicator]：官方把前者定位为「should replace most uses of the
 * indeterminate circular progress indicator」，整页首载正是该语义。颜色走
 * `LoadingIndicatorDefaults.indicatorColor`（= `colorScheme.primary`，与旧指示器一致）
 * —— 零硬编码颜色。
 *
 * ⚠️ alpha 期门控不稳定：material3 **1.5.0-alpha18 的 `LoadingIndicator` 恰好无
 * `@ExperimentalMaterial3ExpressiveApi` 门控**（字节码实测该文件对该 marker 的引用数
 * = 0），故此处不需要 `@OptIn`；但官方 release notes 有 "Revert MaterialShapes and
 * LoadingIndicator promotions to stable"，**alpha19 起又变回门控**。升级 pin 时若编译
 * 报 opt-in 相关错误，在此处加**局部** `@OptIn(ExperimentalMaterial3ExpressiveApi::class)`
 * 并写理由，**不要**开全局 `-opt-in`（会让全仓失去门控保护）。详见
 * `docs/adr/0008-material3-alpha18-pin.md`。
 */
@Composable
fun AppLoadingState(
    modifier: Modifier = Modifier,
    label: String? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        LoadingIndicator()
        if (label != null) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** Empty/Error 共用的居中消息布局（图标语义不同，结构一致） */
@Composable
private fun AppMessageState(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    message: String?,
    actionLabel: String?,
    onAction: (() -> Unit)?,
    action: (@Composable () -> Unit)?,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(AppDimens.contentPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        if (message != null) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        when {
            action != null -> {
                action()
            }

            actionLabel != null && onAction != null -> {
                Button(onClick = onAction) {
                    Text(text = actionLabel)
                }
            }
        }
    }
}

/*
 * 整页/整分区加载态：把 AppLoadingState 垂直+水平居中后铺满给定区域。
 *
 * 为什么单独有这一个（2026-09-11）：feature:issue / feature:pullrequest 各自有一份
 * IssueLoadingContent / PullRequestLoadingContent，实现逐字相同（居中 Box + 裸 Material
 * 圆形指示器）—— 两处重复，且于是那 4 屏成了全应用仅存的「加载态与其他屏不一致」的地方。
 *
 * 收敛到本文件（core/designsystem/）的理由：这里是加载态的唯一事实来源；各 feature 只留调用点。
 * 用法：AppCenteredLoadingState(modifier = Modifier.fillMaxSize())
 */
@Composable
fun AppCenteredLoadingState(
    modifier: Modifier = Modifier,
    label: String? = null,
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        AppLoadingState(label = label)
    }
}
