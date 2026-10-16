package com.yumiru11.githubapp.core.designsystem.component

import androidx.compose.ui.graphics.vector.ImageVector

/**
 * FAB 展开菜单的一项（见 [AppFabMenu]）。
 *
 * 单独成文件：ktlint/detekt 的 MatchingDeclarationName 要求「文件里唯一的顶层类型声明」
 * 与文件名一致 —— 组件文件名为 AppFabMenu.kt，因此数据类独立出来。
 */
data class AppFabMenuItem(
    val label: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
    val enabled: Boolean = true,
)
