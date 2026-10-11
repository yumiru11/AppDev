package com.yumiru11.githubapp.core.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource

/**
 * 应用统一**返回按钮**（二级页顶栏用）。
 *
 * ## 形态
 *
 * 圆形 [FilledTonalIconButton]：`shapes = IconButtonDefaults.shapes()` 取 M3 Expressive 的
 * **形状形变**（按下时圆角随交互状态变化），容器色取 `surfaceContainerHigh`——与设置页
 * 分组卡同一档，视觉上"这颗按钮和卡片是同一种材料"（层级仍靠色调差，不加阴影）。
 *
 * 图标用 `Icons.AutoMirrored.Filled.ArrowBack`：RTL 下自动镜像，与全仓其余 18 个二级页
 * 的返回箭头同源，不引入第二套图标语义。
 *
 * 无障碍：`contentDescription` 走 [R.string.nav_back]（en + zh-rCN 成对），
 * 不自造第二份文案。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AppBackButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FilledTonalIconButton(
        onClick = onClick,
        modifier = modifier,
        shapes = IconButtonDefaults.shapes(),
        colors =
            IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ),
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = stringResource(R.string.nav_back),
        )
    }
}
