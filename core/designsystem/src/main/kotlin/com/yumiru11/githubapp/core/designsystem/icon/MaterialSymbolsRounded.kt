package com.yumiru11.githubapp.core.designsystem.icon

import androidx.compose.ui.graphics.vector.ImageVector
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Account_circle
import com.composables.icons.materialsymbols.rounded.Blur_on
import com.composables.icons.materialsymbols.rounded.Brightness_2
import com.composables.icons.materialsymbols.rounded.Code
import com.composables.icons.materialsymbols.rounded.Contrast
import com.composables.icons.materialsymbols.rounded.Data_usage
import com.composables.icons.materialsymbols.rounded.Expand_less
import com.composables.icons.materialsymbols.rounded.Expand_more
import com.composables.icons.materialsymbols.rounded.Format_color_fill
import com.composables.icons.materialsymbols.rounded.Format_list_numbered
import com.composables.icons.materialsymbols.rounded.Home
import com.composables.icons.materialsymbols.rounded.Image
import com.composables.icons.materialsymbols.rounded.Info
import com.composables.icons.materialsymbols.rounded.Language
import com.composables.icons.materialsymbols.rounded.Layers
import com.composables.icons.materialsymbols.rounded.Motion_photos_on
import com.composables.icons.materialsymbols.rounded.Notifications
import com.composables.icons.materialsymbols.rounded.Palette
import com.composables.icons.materialsymbols.rounded.Person
import com.composables.icons.materialsymbols.rounded.Playlist_play
import com.composables.icons.materialsymbols.rounded.Rounded_corner
import com.composables.icons.materialsymbols.rounded.Search
import com.composables.icons.materialsymbols.rounded.Settings
import com.composables.icons.materialsymbols.rounded.Shapes
import com.composables.icons.materialsymbols.rounded.Tune
import com.composables.icons.materialsymbols.rounded.Vpn_key

/**
 * Material Symbols **Rounded** 家族的常用图标（内部实现细节，对外只暴露 [AppIcons]）。
 *
 * com.composables 的图标是**扩展属性**（`val MaterialSymbols.Rounded.Home`），四个家族
 * 同名 → 同一文件里逐名 import 会「同名冲突」，`as` 别名又会让未别名的那一族独占该名字
 * （实测编译不过）。故**一个家族一个文件**，各自显式 import；[AppIcons] 再把四族组合成
 * 单个语义图标的四变体规格。使用时必须写全接收者（`MaterialSymbols.Rounded.Home`）。
 *
 * 多词图标在 com.composables 2.2.1 里的命名为**首字母大写的 snake_case**（`Arrow_back`，
 * 不是 `ArrowBack`）——名字取自库元数据实测，不是猜的。
 */
internal object MaterialSymbolsRounded {
    val Home: ImageVector = MaterialSymbols.Rounded.Home

    val Info: ImageVector = MaterialSymbols.Rounded.Info

    val Notifications: ImageVector = MaterialSymbols.Rounded.Notifications

    val Person: ImageVector = MaterialSymbols.Rounded.Person

    val Search: ImageVector = MaterialSymbols.Rounded.Search

    val Settings: ImageVector = MaterialSymbols.Rounded.Settings

    val Expand_less: ImageVector = MaterialSymbols.Rounded.Expand_less

    val Expand_more: ImageVector = MaterialSymbols.Rounded.Expand_more

    val Language: ImageVector = MaterialSymbols.Rounded.Language

    val Tune: ImageVector = MaterialSymbols.Rounded.Tune

    val Account_circle: ImageVector = MaterialSymbols.Rounded.Account_circle

    val Palette: ImageVector = MaterialSymbols.Rounded.Palette

    val Motion_photos_on: ImageVector = MaterialSymbols.Rounded.Motion_photos_on

    val Code: ImageVector = MaterialSymbols.Rounded.Code

    val Contrast: ImageVector = MaterialSymbols.Rounded.Contrast

    val Brightness_2: ImageVector = MaterialSymbols.Rounded.Brightness_2

    val Format_color_fill: ImageVector = MaterialSymbols.Rounded.Format_color_fill

    val Shapes: ImageVector = MaterialSymbols.Rounded.Shapes

    val Blur_on: ImageVector = MaterialSymbols.Rounded.Blur_on

    val Image: ImageVector = MaterialSymbols.Rounded.Image

    val Rounded_corner: ImageVector = MaterialSymbols.Rounded.Rounded_corner

    val Format_list_numbered: ImageVector = MaterialSymbols.Rounded.Format_list_numbered

    val Vpn_key: ImageVector = MaterialSymbols.Rounded.Vpn_key

    val Data_usage: ImageVector = MaterialSymbols.Rounded.Data_usage

    val Playlist_play: ImageVector = MaterialSymbols.Rounded.Playlist_play

    val Layers: ImageVector = MaterialSymbols.Rounded.Layers
}
