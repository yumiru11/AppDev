@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.yumiru11.githubapp.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import com.yumiru11.githubapp.core.designsystem.component.AppScaffold
import com.yumiru11.githubapp.core.designsystem.token.AppDimens
import com.yumiru11.githubapp.core.ui.AppBackButton

/**
 * 设置层级页公共骨架（L1/L2/L3 通用）。
 *
 * ## 形态（对齐参考实现的结构，非代码）
 *
 * - 顶栏 = M3 Expressive [LargeFlexibleTopAppBar]：展开态是「圆形返回按钮 + 大标题」上下排布，
 *   滚动收起为小标题栏；[TopAppBarDefaults.exitUntilCollapsedScrollBehavior] 负责折叠
 * - 顶栏与页面**同色**（`containerColor = background`），标题像浮在页面上而不是被色带框住；
 *   滚动后顶栏转为 `surfaceContainer` 一档色调差——分层用色调差表达，不加阴影
 * - 内容：左右 16dp（[AppDimens.spacing.l]）、组间 16dp、底部预留 32dp 呼吸位
 *
 * @param content LazyColumn 内容（各页按「一组一张卡」组织）
 */
@Composable
internal fun SettingsPageScaffold(
    title: String,
    onBackClick: () -> Unit,
    content: LazyListScope.() -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    AppScaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(text = title) },
                navigationIcon = { AppBackButton(onClick = onBackClick) },
                scrollBehavior = scrollBehavior,
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ),
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding =
                PaddingValues(
                    start = AppDimens.spacing.l,
                    end = AppDimens.spacing.l,
                    top = innerPadding.calculateTopPadding(),
                    bottom = innerPadding.calculateBottomPadding() + AppDimens.spacing.xxl,
                ),
            verticalArrangement = Arrangement.spacedBy(AppDimens.spacing.l),
        ) {
            content()
        }
    }
}
