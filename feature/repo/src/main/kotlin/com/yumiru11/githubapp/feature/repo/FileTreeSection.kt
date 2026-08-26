package com.yumiru11.githubapp.feature.repo

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Folder
import com.composables.icons.materialsymbols.rounded.Folder_open
import com.composables.icons.materialsymbols.rounded.Text_snippet

/**
 * 文件树 Tab 内容（T11 验收第 1 条：浏览任意公共仓库目录结构，递归、按需加载子目录）。
 *
 * 首次组合时按默认分支加载根树；目录点击展开（子树按需拉取）/收起。
 * 文件点击 → 打开查看器（[RepoFilesViewModel.openFile]）。
 */
@Composable
fun FileTreeSection(
    treeState: TreeState,
    defaultBranch: String?,
    initialRef: String? = null,
    viewModel: RepoFilesViewModel,
    modifier: Modifier = Modifier,
) {
    // T23：分支切换深链进入时以 initialRef 加载；否则回退默认分支
    val ref = initialRef?.takeIf { it.isNotBlank() } ?: defaultBranch ?: DEFAULT_REF
    LaunchedEffect(ref) {
        viewModel.loadRootTree(ref)
    }

    when (treeState) {
        is TreeState.Loading -> {
            Box(modifier = modifier.fillMaxWidth().padding(vertical = 64.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }

        is TreeState.Error -> {
            ErrorContent(
                errorType = treeState.errorType,
                onRetry = { viewModel.loadRootTree(ref) },
                modifier = modifier.fillMaxWidth(),
            )
        }

        is TreeState.Loaded -> {
            val rows = FileTreeBuilder.visibleRows(treeState.rootNodes)
            LazyColumn(modifier = modifier.fillMaxSize()) {
                items(items = rows, key = { it.node.path }) { row ->
                    TreeRowItem(
                        row = row,
                        onClick = {
                            if (row.node.isDirectory) {
                                viewModel.toggleDirectory(row.node)
                            } else {
                                viewModel.openFile(row.node, ref)
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun TreeRowItem(
    row: TreeRow,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(modifier = Modifier.width((row.depth * INDENT_PER_DEPTH_DP).dp))
        Icon(
            imageVector =
                when {
                    row.node.isDirectory -> {
                        if (row.node.isExpanded) MaterialSymbols.Rounded.Folder_open else MaterialSymbols.Rounded.Folder
                    }

                    else -> {
                        MaterialSymbols.Rounded.Text_snippet
                    }
                },
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = row.node.name,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 分支缺失时的兜底 ref（Git Data API 接受 HEAD 引用） */
internal const val DEFAULT_REF = "HEAD"

private const val INDENT_PER_DEPTH_DP = 16
