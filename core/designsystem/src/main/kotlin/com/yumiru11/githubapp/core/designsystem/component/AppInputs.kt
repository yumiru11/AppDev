package com.yumiru11.githubapp.core.designsystem.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.yumiru11.githubapp.core.designsystem.icon.AppIcon
import com.yumiru11.githubapp.core.designsystem.icon.AppIconSpec
import com.yumiru11.githubapp.core.designsystem.icon.AppIcons
import com.yumiru11.githubapp.core.designsystem.token.AppDimens

/**
 * 顶栏胶囊搜索框（**按钮**语义：点击进入搜索页），基准 = M3E \`GhSearchBarButton\`。
 *
 * 56dp 高、全胶囊、surfaceContainerHigh 底；文案由调用方传本地化 stringResource。
 */
@Composable
fun AppSearchBarButton(
    placeholder: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = AppDimens.buttonHeight,
    trailing: (@Composable () -> Unit)? = null,
) {
    Surface(
        modifier = modifier.height(height).clip(RoundedCornerShape(percent = 50)).clickable(onClick = onClick),
        shape = RoundedCornerShape(percent = 50),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = AppDimens.spacing.l, end = AppDimens.spacing.s),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppIcon(
                spec = AppIcons.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                size = 22.dp,
            )
            Spacer(Modifier.width(AppDimens.spacing.m))
            Text(
                text = placeholder,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            trailing?.invoke()
        }
    }
}

/** 单行/多行输入框（16dp 圆角，M3E \`GhTextField\` 口径）。 */
@Composable
fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    supporting: String? = null,
    singleLine: Boolean = false,
    minLines: Int = 1,
    maxLines: Int = 8,
    leadingIcon: AppIconSpec? = null,
    trailingIcon: AppIconSpec? = null,
    enabled: Boolean = true,
    isError: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        enabled = enabled,
        isError = isError,
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it, style = MaterialTheme.typography.bodyMedium) } },
        supportingText = supporting?.let { { Text(it, style = MaterialTheme.typography.bodySmall) } },
        singleLine = singleLine,
        minLines = minLines,
        maxLines = maxLines,
        shape = RoundedCornerShape(AppDimens.cornerMedium),
        leadingIcon = leadingIcon?.let { spec -> { AppIcon(spec = spec, contentDescription = null, size = 20.dp) } },
        trailingIcon = trailingIcon?.let { spec -> { AppIcon(spec = spec, contentDescription = null, size = 20.dp) } },
        textStyle = MaterialTheme.typography.bodyLarge,
    )
}

/** 多行 Markdown 输入框（新建正文 / 评论草稿；真正的编辑器见 core:editor 的 MarkdownComposer）。 */
@Composable
fun AppMarkdownField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    supporting: String? = null,
    minHeight: Int = 160,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth().heightIn(min = minHeight.dp),
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it, style = MaterialTheme.typography.bodyMedium) } },
        supportingText = supporting?.let { { Text(it, style = MaterialTheme.typography.bodySmall) } },
        shape = RoundedCornerShape(AppDimens.cornerMedium),
        textStyle = MaterialTheme.typography.bodyMedium,
    )
}

/** 带开关的设置行（标题 + 可选说明 + 右侧 Switch），基准 = M3E \`GhSwitchRow\`。 */
@Composable
fun AppSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    enabled: Boolean = true,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clickable(enabled = enabled) { onCheckedChange(!checked) }
                .padding(horizontal = AppDimens.spacing.l, vertical = AppDimens.spacing.m + 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (description != null) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.width(AppDimens.spacing.l))
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}
