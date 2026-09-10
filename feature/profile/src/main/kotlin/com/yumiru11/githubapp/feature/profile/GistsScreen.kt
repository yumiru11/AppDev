package com.yumiru11.githubapp.feature.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.yumiru11.githubapp.core.designsystem.component.AppEmptyState
import com.yumiru11.githubapp.core.designsystem.component.AppErrorState
import com.yumiru11.githubapp.core.designsystem.component.AppLoadingState
import com.yumiru11.githubapp.core.designsystem.icon.AppDevOcticons
import com.yumiru11.githubapp.core.ui.time.relativeTimeText
import com.yumiru11.githubapp.feature.profile.model.GistItem

/** 语言色点直径（与首页 Trending 小节同视觉规格） */
private val LANGUAGE_DOT_SIZE = 10.dp

/**
 * Gists 列表页（L11 / ui-design.md §3.7）。
 *
 * 统一「列表页」模板：标题 + LazyColumn；条目 = 文件名 + 语言色点 + 描述 + 相对时间。
 * v1 不做 gist 详情渲染，点击经 [onOpenExternal] 交给宿主用 Chrome Custom Tabs 打开 html_url。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GistsScreen(
    onBackClick: () -> Unit,
    onOpenExternal: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: GistsViewModel = hiltViewModel(),
) {
    val gists = viewModel.gists.collectAsLazyPagingItems()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.profile_gists_title)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.profile_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
            GistListContent(
                gists = gists,
                onOpenExternal = onOpenExternal,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** 三态 + 分页列表（抽出具名函数：状态分支与列表装配保持单层缩进） */
@Composable
private fun GistListContent(
    gists: LazyPagingItems<GistItem>,
    onOpenExternal: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val refresh = gists.loadState.refresh
    when {
        refresh is LoadState.Loading && gists.itemCount == 0 -> {
            AppLoadingState(modifier = modifier)
        }

        refresh is LoadState.Error && gists.itemCount == 0 -> {
            AppErrorState(
                title = stringResource(R.string.profile_list_error),
                actionLabel = stringResource(R.string.profile_list_retry),
                onAction = { gists.retry() },
                modifier = modifier,
            )
        }

        gists.itemCount == 0 -> {
            AppEmptyState(
                icon = AppDevOcticons.File,
                title = stringResource(R.string.profile_gists_empty),
                modifier = modifier,
            )
        }

        else -> {
            LazyColumn(
                modifier = modifier,
                contentPadding = PaddingValues(bottom = 16.dp),
            ) {
                items(
                    count = gists.itemCount,
                    key = gists.itemKey { it.id },
                ) { index ->
                    gists[index]?.let { gist ->
                        GistRow(
                            gist = gist,
                            onClick = { gist.htmlUrl?.let(onOpenExternal) },
                        )
                    }
                }
                if (gists.loadState.append is LoadState.Loading) {
                    item(key = "append-loading") {
                        AppLoadingState(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 24.dp),
                        )
                    }
                }
                if (gists.loadState.append is LoadState.Error) {
                    item(key = "append-error") {
                        AppErrorState(
                            title = stringResource(R.string.profile_list_error),
                            actionLabel = stringResource(R.string.profile_list_retry),
                            onAction = { gists.retry() },
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 24.dp),
                        )
                    }
                }
            }
        }
    }
}

/** Gist 条目：文件名 + 语言色点/语言 + 描述（2 行）+ 相对时间 */
@Composable
private fun GistRow(
    gist: GistItem,
    onClick: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            text = gist.fileName,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        val description = gist.description
        if (description != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            val language = gist.language
            if (language != null) {
                LanguageDot(language = language)
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = language,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.width(12.dp))
            }
            gist.createdAt?.let { timestamp ->
                relativeTimeText(timestamp)?.let { text ->
                    Text(
                        text = text,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
    HorizontalDivider()
}

/**
 * 语言色点（ui-design §3.7「语言色点」）。
 *
 * GitHub 原色是品牌色板（硬编码十六进制），与「零硬编码颜色」红线冲突，
 * 故按语言名稳定散列到 M3 主题色角色：同一语言恒定同色，且随主题/动态取色联动。
 */
@Composable
private fun LanguageDot(language: String) {
    val colors = languageDotColors()
    Box(
        modifier =
            Modifier
                .size(LANGUAGE_DOT_SIZE)
                .clip(CircleShape)
                .background(colors[Math.floorMod(language.hashCode(), colors.size)]),
    )
}

/** 色点色板：主题色角色循环（零硬编码颜色；与首页 Trending 同口径） */
@Composable
private fun languageDotColors(): List<Color> =
    listOf(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.secondary,
        MaterialTheme.colorScheme.tertiary,
        MaterialTheme.colorScheme.error,
        MaterialTheme.colorScheme.onSurfaceVariant,
    )
