@file:Suppress("LongParameterList")
// LongParameterList：面板是「状态 + 回调透传」的受控组件（查看器/编辑页两处宿主接线），
// 12 个参数是查找/替换两行控件的 UI 面固有（同 Screen 层 LongParameterList 先例）。

package com.yumiru11.githubapp.feature.repo

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Close
import com.composables.icons.materialsymbols.rounded.Keyboard_arrow_down
import com.composables.icons.materialsymbols.rounded.Keyboard_arrow_up
import com.composables.icons.materialsymbols.rounded.Search
import com.yumiru11.githubapp.core.designsystem.token.AppDimens
import com.yumiru11.githubapp.core.editor.CodeEditorController
import com.yumiru11.githubapp.core.editor.FileFindState

/**
 * 文件内查找 / 替换面板（EDITOR-1 从 `FileViewerScreen` 抽出，查看器与编辑页共用）。
 *
 * - [showReplace] = false（文件查看器，只读）：仅查找行 + 导航行，行为与抽取前逐像素等价
 * - [showReplace] = true（文件编辑页，可编辑）：多一行替换输入 + 「替换 / 全部替换」动作；
 *   无匹配时动作禁用（[FileFindState.hasMatches]），不给「点了没反应」的死按钮
 *
 * 颜色全取 M3 令牌（surfaceContainerHigh / onSurfaceVariant / 透明容器）；文案全走
 * stringResource（en + zh-rCN）。
 */
@Composable
internal fun FileFindReplaceBar(
    state: FileFindState,
    replaceQuery: String,
    showReplace: Boolean,
    focusRequester: FocusRequester,
    onQueryChange: (String) -> Unit,
    onReplaceQueryChange: (String) -> Unit,
    onReplace: () -> Unit,
    onReplaceAll: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.widthIn(min = 280.dp, max = 340.dp),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 3.dp,
        shadowElevation = 6.dp,
    ) {
        Column(
            modifier = Modifier.padding(start = AppDimens.spacing.m, end = AppDimens.spacing.xs, top = 2.dp, bottom = AppDimens.spacing.xs),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = MaterialSymbols.Rounded.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
                TextField(
                    value = state.query,
                    onValueChange = onQueryChange,
                    modifier =
                        Modifier
                            .weight(1f)
                            .padding(start = AppDimens.spacing.s)
                            .focusRequester(focusRequester)
                            .testTag(FileFindReplaceBarTags.QUERY),
                    placeholder = { Text(text = stringResource(R.string.repo_file_find_hint)) },
                    singleLine = true,
                    shape = MaterialTheme.shapes.large,
                    colors = transparentFieldColors(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    // 键盘「搜索」键 = 下一处（无匹配时为空操作，状态机原样返回）
                    keyboardActions = KeyboardActions(onSearch = { onNext() }),
                )
                IconButton(onClick = onClose) {
                    Icon(
                        imageVector = MaterialSymbols.Rounded.Close,
                        contentDescription = stringResource(R.string.repo_file_find_close),
                    )
                }
            }
            if (showReplace) {
                ReplaceSection(
                    hasQuery = state.hasQuery,
                    hasMatches = state.hasMatches,
                    replaceQuery = replaceQuery,
                    onReplaceQueryChange = onReplaceQueryChange,
                    onReplace = onReplace,
                    onReplaceAll = onReplaceAll,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onPrevious, enabled = state.hasMatches) {
                    Icon(
                        imageVector = MaterialSymbols.Rounded.Keyboard_arrow_up,
                        contentDescription = stringResource(R.string.repo_file_find_previous),
                    )
                }
                IconButton(onClick = onNext, enabled = state.hasMatches) {
                    Icon(
                        imageVector = MaterialSymbols.Rounded.Keyboard_arrow_down,
                        contentDescription = stringResource(R.string.repo_file_find_next),
                    )
                }
                Text(
                    text = findCounterText(state),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.padding(start = AppDimens.spacing.xs, end = AppDimens.spacing.m),
                )
            }
        }
    }
}

/**
 * 替换行（输入 + 动作）：仅编辑面渲染（[FileFindReplaceBar] 的 `showReplace` 分支）。
 *
 * 动作可用判据是 [hasMatches]（无匹配时禁用，不给「点了没反应」的死按钮）；
 * 输入框在无查询词时禁用（先有查找目标才谈替换）。
 */
@Composable
private fun ReplaceSection(
    hasQuery: Boolean,
    hasMatches: Boolean,
    replaceQuery: String,
    onReplaceQueryChange: (String) -> Unit,
    onReplace: () -> Unit,
    onReplaceAll: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Spacer(modifier = Modifier.size(28.dp))
        TextField(
            value = replaceQuery,
            onValueChange = onReplaceQueryChange,
            enabled = hasQuery,
            modifier = Modifier.weight(1f).padding(start = AppDimens.spacing.s).testTag(FileFindReplaceBarTags.REPLACE),
            placeholder = { Text(text = stringResource(R.string.repo_file_replace_hint)) },
            singleLine = true,
            shape = MaterialTheme.shapes.large,
            colors = transparentFieldColors(),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onReplace() }),
        )
        Spacer(modifier = Modifier.size(48.dp))
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Spacer(modifier = Modifier.weight(1f))
        TextButton(onClick = onReplace, enabled = hasMatches) {
            Text(text = stringResource(R.string.repo_file_replace))
        }
        TextButton(onClick = onReplaceAll, enabled = hasMatches) {
            Text(text = stringResource(R.string.repo_file_replace_all))
        }
    }
}

/** 查找/替换输入框无边框透明底（面板已是高对比容器，不再叠 TextField 容器）。 */
@Composable
private fun transparentFieldColors() =
    TextFieldDefaults.colors(
        focusedContainerColor = Color.Transparent,
        unfocusedContainerColor = Color.Transparent,
        disabledContainerColor = Color.Transparent,
        focusedIndicatorColor = Color.Transparent,
        unfocusedIndicatorColor = Color.Transparent,
        disabledIndicatorColor = Color.Transparent,
    )

/**
 * 计数文案：「第 n / 共 m 项」/「无匹配结果」。
 *
 * 查询词为空或结果未回灌（[FileFindState.isSearching]）时留空——新查询词生效是异步的，
 * 立刻显示「无匹配」会闪一帧假阴性。
 */
@Composable
internal fun findCounterText(state: FileFindState): String =
    when {
        !state.hasQuery || state.isSearching -> ""
        state.hasMatches -> stringResource(R.string.repo_file_find_counter, state.matchOrdinal, state.matchCount)
        else -> stringResource(R.string.repo_file_find_no_results)
    }

/**
 * 编辑器查找结果 → ViewModel 回灌（查看器与编辑页共用接线）。
 *
 * 首次结果无选中项时补跳到首处匹配（Sora 只回灌匹配表，不自动选中）——
 * 缺这一步，用户输入查询词后计数正确但编辑器不高亮「当前处」。
 */
internal fun bindFindResults(
    viewModel: RepoFilesViewModel,
    controller: CodeEditorController,
) {
    controller.onFindResult = { result ->
        viewModel.onFindResults(result)
        if (result.hasMatches && result.currentMatchIndex == FileFindState.NO_MATCH) {
            viewModel.onFindResults(controller.findNext())
        }
    }
}

/** 测试定位 tag：面板文案随语言变化，交互测试以 tag 定位两个输入框（文案只用于人读）。 */
internal object FileFindReplaceBarTags {
    const val QUERY = "file_find_query"

    const val REPLACE = "file_find_replace"
}
