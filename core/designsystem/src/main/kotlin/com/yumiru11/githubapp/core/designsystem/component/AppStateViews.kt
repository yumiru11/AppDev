package com.yumiru11.githubapp.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
 * 空态占位：矢量插图 + 标题 + 可选说明 + 可选行动按钮。
 *
 * 替换 Home/Notifications/Profile 各自手搓的空态（#84）。插图由调用方从
 * 已批准的 [com.yumiru11.githubapp.core.designsystem.icon.AppDevOcticons]
 * 中按语境选择（如通知空态用 Check「全部已读」、仓库列表空态用 Repo）；
 * 文案由调用方传本地化 stringResource，本组件不内嵌字符串。图标纯装饰
 * （无 contentDescription），信息由文本承载；全 app 禁 emoji 图标。
 *
 * @param icon 矢量插图（Octicons，纯装饰）
 * @param title 主文案（如「暂无通知」）
 * @param message 次级说明（可空）
 * @param actionLabel 行动按钮文案（与 [onAction] 成对出现才渲染按钮）
 * @param onAction 行动回调（可空则不渲染按钮）
 */
@Composable
fun AppEmptyState(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    message: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    AppMessageState(
        icon = icon,
        title = title,
        modifier = modifier,
        message = message,
        actionLabel = actionLabel,
        onAction = onAction,
    )
}

/**
 * 错误态占位：矢量插图（Octicons Alert）+ 标题 + 可选说明 + 可选行动按钮。
 *
 * @param title 主文案
 * @param message 次级说明（可空）
 * @param actionLabel 行动按钮文案（与 [onAction] 成对出现才渲染按钮）
 * @param onAction 行动回调（可空则不渲染按钮）；典型用法 actionLabel=重试
 */
@Composable
fun AppErrorState(
    title: String,
    modifier: Modifier = Modifier,
    message: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    AppMessageState(
        icon = AppDevOcticons.Alert,
        title = title,
        modifier = modifier,
        message = message,
        actionLabel = actionLabel,
        onAction = onAction,
    )
}

/**
 * 加载态占位：居中圆形进度 + 可选标签。
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
        CircularProgressIndicator()
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
        if (actionLabel != null && onAction != null) {
            Button(onClick = onAction) {
                Text(text = actionLabel)
            }
        }
    }
}
