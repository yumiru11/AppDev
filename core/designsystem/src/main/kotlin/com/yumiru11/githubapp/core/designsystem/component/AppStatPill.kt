package com.yumiru11.githubapp.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.yumiru11.githubapp.core.designsystem.token.AppDimens

/**
 * 元信息小胶囊（星标数 / 语言 / 分支 / 状态）：图标 + 数字或短词。
 *
 * 用于列表行的第二行元信息，避免一堆裸 Text 排成一行难以扫读。
 */
@Composable
fun AppStatPill(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    containerColor: Color = MaterialTheme.colorScheme.secondaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onSecondaryContainer,
    iconSize: Dp = 14.dp,
) {
    Row(
        modifier =
            modifier
                .clip(RoundedCornerShape(percent = 50))
                .background(containerColor)
                .padding(horizontal = AppDimens.spacing.m, vertical = AppDimens.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(iconSize), tint = contentColor)
            Spacer(Modifier.width(AppDimens.spacing.xs))
        }
        Text(text = text, style = MaterialTheme.typography.labelMedium, color = contentColor, maxLines = 1)
    }
}
