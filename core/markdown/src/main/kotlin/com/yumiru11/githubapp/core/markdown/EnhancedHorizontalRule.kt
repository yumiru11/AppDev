package com.yumiru11.githubapp.core.markdown

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** B 增强版分隔线：outlineVariant 1dp + 上下 16dp 留白。 */
@Composable
fun EnhancedHorizontalRule() {
    HorizontalDivider(
        thickness = 1.dp,
        color = MaterialTheme.colorScheme.outlineVariant,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
    )
}
