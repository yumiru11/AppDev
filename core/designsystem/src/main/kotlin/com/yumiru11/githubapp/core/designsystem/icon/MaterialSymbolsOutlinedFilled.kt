package com.yumiru11.githubapp.core.designsystem.icon

import androidx.compose.ui.graphics.vector.ImageVector
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.outlinedfilled.Account_circle
import com.composables.icons.materialsymbols.outlinedfilled.Blur_on
import com.composables.icons.materialsymbols.outlinedfilled.Brightness_2
import com.composables.icons.materialsymbols.outlinedfilled.Code
import com.composables.icons.materialsymbols.outlinedfilled.Contrast
import com.composables.icons.materialsymbols.outlinedfilled.Data_usage
import com.composables.icons.materialsymbols.outlinedfilled.Expand_less
import com.composables.icons.materialsymbols.outlinedfilled.Expand_more
import com.composables.icons.materialsymbols.outlinedfilled.Format_color_fill
import com.composables.icons.materialsymbols.outlinedfilled.Format_list_numbered
import com.composables.icons.materialsymbols.outlinedfilled.Home
import com.composables.icons.materialsymbols.outlinedfilled.Image
import com.composables.icons.materialsymbols.outlinedfilled.Info
import com.composables.icons.materialsymbols.outlinedfilled.Language
import com.composables.icons.materialsymbols.outlinedfilled.Layers
import com.composables.icons.materialsymbols.outlinedfilled.Motion_photos_on
import com.composables.icons.materialsymbols.outlinedfilled.Notifications
import com.composables.icons.materialsymbols.outlinedfilled.Palette
import com.composables.icons.materialsymbols.outlinedfilled.Person
import com.composables.icons.materialsymbols.outlinedfilled.Playlist_play
import com.composables.icons.materialsymbols.outlinedfilled.Rounded_corner
import com.composables.icons.materialsymbols.outlinedfilled.Search
import com.composables.icons.materialsymbols.outlinedfilled.Settings
import com.composables.icons.materialsymbols.outlinedfilled.Shapes
import com.composables.icons.materialsymbols.outlinedfilled.Tune
import com.composables.icons.materialsymbols.outlinedfilled.Vpn_key

/**
 * Material Symbols **OutlinedFilled** 家族的常用图标（内部实现细节，对外只暴露 [AppIcons]）。
 *
 * com.composables 的图标是**扩展属性**（`val MaterialSymbols.OutlinedFilled.Home`），四个家族
 * 同名 → 同一文件里逐名 import 会「同名冲突」，`as` 别名又会让未别名的那一族独占该名字
 * （实测编译不过）。故**一个家族一个文件**，各自显式 import；[AppIcons] 再把四族组合成
 * 单个语义图标的四变体规格。使用时必须写全接收者（`MaterialSymbols.OutlinedFilled.Home`）。
 *
 * 多词图标在 com.composables 2.2.1 里的命名为**首字母大写的 snake_case**（`Arrow_back`，
 * 不是 `ArrowBack`）——名字取自库元数据实测，不是猜的。
 */
internal object MaterialSymbolsOutlinedFilled {
    val Home: ImageVector = MaterialSymbols.OutlinedFilled.Home

    val Info: ImageVector = MaterialSymbols.OutlinedFilled.Info

    val Notifications: ImageVector = MaterialSymbols.OutlinedFilled.Notifications

    val Person: ImageVector = MaterialSymbols.OutlinedFilled.Person

    val Search: ImageVector = MaterialSymbols.OutlinedFilled.Search

    val Settings: ImageVector = MaterialSymbols.OutlinedFilled.Settings

    val Expand_less: ImageVector = MaterialSymbols.OutlinedFilled.Expand_less

    val Expand_more: ImageVector = MaterialSymbols.OutlinedFilled.Expand_more

    val Language: ImageVector = MaterialSymbols.OutlinedFilled.Language

    val Tune: ImageVector = MaterialSymbols.OutlinedFilled.Tune

    val Account_circle: ImageVector = MaterialSymbols.OutlinedFilled.Account_circle

    val Palette: ImageVector = MaterialSymbols.OutlinedFilled.Palette

    val Motion_photos_on: ImageVector = MaterialSymbols.OutlinedFilled.Motion_photos_on

    val Code: ImageVector = MaterialSymbols.OutlinedFilled.Code

    val Contrast: ImageVector = MaterialSymbols.OutlinedFilled.Contrast

    val Brightness_2: ImageVector = MaterialSymbols.OutlinedFilled.Brightness_2

    val Format_color_fill: ImageVector = MaterialSymbols.OutlinedFilled.Format_color_fill

    val Shapes: ImageVector = MaterialSymbols.OutlinedFilled.Shapes

    val Blur_on: ImageVector = MaterialSymbols.OutlinedFilled.Blur_on

    val Image: ImageVector = MaterialSymbols.OutlinedFilled.Image

    val Rounded_corner: ImageVector = MaterialSymbols.OutlinedFilled.Rounded_corner

    val Format_list_numbered: ImageVector = MaterialSymbols.OutlinedFilled.Format_list_numbered

    val Vpn_key: ImageVector = MaterialSymbols.OutlinedFilled.Vpn_key

    val Data_usage: ImageVector = MaterialSymbols.OutlinedFilled.Data_usage

    val Playlist_play: ImageVector = MaterialSymbols.OutlinedFilled.Playlist_play

    val Layers: ImageVector = MaterialSymbols.OutlinedFilled.Layers
}
