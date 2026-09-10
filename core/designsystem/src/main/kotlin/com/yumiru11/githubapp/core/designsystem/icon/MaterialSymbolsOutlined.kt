package com.yumiru11.githubapp.core.designsystem.icon

import androidx.compose.ui.graphics.vector.ImageVector
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.outlined.Home
import com.composables.icons.materialsymbols.outlined.Info
import com.composables.icons.materialsymbols.outlined.Notifications
import com.composables.icons.materialsymbols.outlined.Person
import com.composables.icons.materialsymbols.outlined.Search
import com.composables.icons.materialsymbols.outlined.Settings

/**
 * Material Symbols **Outlined** 家族的常用图标（内部实现细节，对外只暴露 [AppIcons]）。
 *
 * com.composables 的图标是**扩展属性**（`val MaterialSymbols.Outlined.Home`），四个家族
 * 同名 → 同一文件里逐名 import 会「同名冲突」，`as` 别名又会让未别名的那一族独占该名字
 * （实测编译不过）。故**一个家族一个文件**，各自显式 import；[AppIcons] 再把四族组合成
 * 单个语义图标的四变体规格。使用时必须写全接收者（`MaterialSymbols.Outlined.Home`）。
 */
internal object MaterialSymbolsOutlined {
    val Home: ImageVector = MaterialSymbols.Outlined.Home

    val Info: ImageVector = MaterialSymbols.Outlined.Info

    val Notifications: ImageVector = MaterialSymbols.Outlined.Notifications

    val Person: ImageVector = MaterialSymbols.Outlined.Person

    val Search: ImageVector = MaterialSymbols.Outlined.Search

    val Settings: ImageVector = MaterialSymbols.Outlined.Settings
}
