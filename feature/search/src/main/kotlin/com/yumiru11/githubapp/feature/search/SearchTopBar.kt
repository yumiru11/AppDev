@file:OptIn(ExperimentalMaterial3Api::class)

package com.yumiru11.githubapp.feature.search

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import com.yumiru11.githubapp.core.designsystem.component.GlassSurface
import com.yumiru11.githubapp.core.designsystem.icon.AppIcon
import com.yumiru11.githubapp.core.designsystem.icon.AppIcons
import com.yumiru11.githubapp.core.designsystem.token.AppDimens
import com.yumiru11.githubapp.core.designsystem.token.GlassScope

/**
 * 搜索页顶栏：返回键 + 大号搜索框（自动聚焦，IME 搜索键提交）。
 */
@Composable
internal fun SearchTopBar(
    input: String,
    onInputChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 与首页顶栏同构：玻璃底 + 状态栏内边距 + 胶囊输入（搜索页此前是不透明 TopAppBar，
    // 背景图开启时会出现"一条实色条压住壁纸"的割裂感）
    GlassSurface(
        modifier = modifier,
        windowInsets = WindowInsets.statusBars,
        scope = GlassScope.TOP_BAR,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AppDimens.spacing.s, vertical = AppDimens.spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBackClick) {
                AppIcon(
                    spec = AppIcons.ArrowBack,
                    contentDescription = stringResource(R.string.search_back),
                )
            }
            SearchInput(
                value = input,
                onValueChange = onInputChange,
                onSubmit = onSubmit,
                onClear = { onInputChange("") },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun SearchInput(
    value: String,
    onValueChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier =
            modifier
                .focusRequester(focusRequester)
                .padding(vertical = AppDimens.spacing.xs),
        placeholder = {
            Text(
                text = stringResource(R.string.search_hint),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        leadingIcon = {
            AppIcon(
                spec = AppIcons.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        trailingIcon = {
            if (value.isNotEmpty()) {
                IconButton(onClick = onClear) {
                    AppIcon(
                        spec = AppIcons.Close,
                        contentDescription = stringResource(R.string.search_clear_input),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        singleLine = true,
        // 形状令牌（ui-design §1.1-5）：28dp == AppDimens.cornerExtraLarge（scale=1 时视觉不变）
        shape = MaterialTheme.shapes.extraLarge,
        colors =
            TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
            ),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
    )
}
