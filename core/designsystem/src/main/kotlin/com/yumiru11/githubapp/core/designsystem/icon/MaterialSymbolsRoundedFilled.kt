package com.yumiru11.githubapp.core.designsystem.icon

import androidx.compose.ui.graphics.vector.ImageVector
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.roundedfilled.Account_circle
import com.composables.icons.materialsymbols.roundedfilled.Blur_on
import com.composables.icons.materialsymbols.roundedfilled.Brightness_2
import com.composables.icons.materialsymbols.roundedfilled.Code
import com.composables.icons.materialsymbols.roundedfilled.Contrast
import com.composables.icons.materialsymbols.roundedfilled.Data_usage
import com.composables.icons.materialsymbols.roundedfilled.Expand_less
import com.composables.icons.materialsymbols.roundedfilled.Expand_more
import com.composables.icons.materialsymbols.roundedfilled.Format_color_fill
import com.composables.icons.materialsymbols.roundedfilled.Format_list_numbered
import com.composables.icons.materialsymbols.roundedfilled.Home
import com.composables.icons.materialsymbols.roundedfilled.Image
import com.composables.icons.materialsymbols.roundedfilled.Info
import com.composables.icons.materialsymbols.roundedfilled.Language
import com.composables.icons.materialsymbols.roundedfilled.Layers
import com.composables.icons.materialsymbols.roundedfilled.Motion_photos_on
import com.composables.icons.materialsymbols.roundedfilled.Notifications
import com.composables.icons.materialsymbols.roundedfilled.Palette
import com.composables.icons.materialsymbols.roundedfilled.Person
import com.composables.icons.materialsymbols.roundedfilled.Playlist_play
import com.composables.icons.materialsymbols.roundedfilled.Rounded_corner
import com.composables.icons.materialsymbols.roundedfilled.Search
import com.composables.icons.materialsymbols.roundedfilled.Settings
import com.composables.icons.materialsymbols.roundedfilled.Shapes
import com.composables.icons.materialsymbols.roundedfilled.Tune
import com.composables.icons.materialsymbols.roundedfilled.Vpn_key

/**
 * Material Symbols **RoundedFilled** 家族的常用图标（内部实现细节，对外只暴露 [AppIcons]）。
 *
 * com.composables 的图标是**扩展属性**（`val MaterialSymbols.RoundedFilled.Home`），四个家族
 * 同名 → 同一文件里逐名 import 会「同名冲突」，`as` 别名又会让未别名的那一族独占该名字
 * （实测编译不过）。故**一个家族一个文件**，各自显式 import；[AppIcons] 再把四族组合成
 * 单个语义图标的四变体规格。使用时必须写全接收者（`MaterialSymbols.RoundedFilled.Home`）。
 *
 * 多词图标在 com.composables 2.2.1 里的命名为**首字母大写的 snake_case**（`Arrow_back`，
 * 不是 `ArrowBack`）——名字取自库元数据实测，不是猜的。
 */
internal object MaterialSymbolsRoundedFilled {
    val Home: ImageVector = MaterialSymbols.RoundedFilled.Home

    val Info: ImageVector = MaterialSymbols.RoundedFilled.Info

    val Notifications: ImageVector = MaterialSymbols.RoundedFilled.Notifications

    val Person: ImageVector = MaterialSymbols.RoundedFilled.Person

    val Search: ImageVector = MaterialSymbols.RoundedFilled.Search

    val Settings: ImageVector = MaterialSymbols.RoundedFilled.Settings

    val Expand_less: ImageVector = MaterialSymbols.RoundedFilled.Expand_less

    val Expand_more: ImageVector = MaterialSymbols.RoundedFilled.Expand_more

    val Language: ImageVector = MaterialSymbols.RoundedFilled.Language

    val Tune: ImageVector = MaterialSymbols.RoundedFilled.Tune

    val Account_circle: ImageVector = MaterialSymbols.RoundedFilled.Account_circle

    val Palette: ImageVector = MaterialSymbols.RoundedFilled.Palette

    val Motion_photos_on: ImageVector = MaterialSymbols.RoundedFilled.Motion_photos_on

    val Code: ImageVector = MaterialSymbols.RoundedFilled.Code

    val Contrast: ImageVector = MaterialSymbols.RoundedFilled.Contrast

    val Brightness_2: ImageVector = MaterialSymbols.RoundedFilled.Brightness_2

    val Format_color_fill: ImageVector = MaterialSymbols.RoundedFilled.Format_color_fill

    val Shapes: ImageVector = MaterialSymbols.RoundedFilled.Shapes

    val Blur_on: ImageVector = MaterialSymbols.RoundedFilled.Blur_on

    val Image: ImageVector = MaterialSymbols.RoundedFilled.Image

    val Rounded_corner: ImageVector = MaterialSymbols.RoundedFilled.Rounded_corner

    val Format_list_numbered: ImageVector = MaterialSymbols.RoundedFilled.Format_list_numbered

    val Vpn_key: ImageVector = MaterialSymbols.RoundedFilled.Vpn_key

    val Data_usage: ImageVector = MaterialSymbols.RoundedFilled.Data_usage

    val Playlist_play: ImageVector = MaterialSymbols.RoundedFilled.Playlist_play

    val Layers: ImageVector = MaterialSymbols.RoundedFilled.Layers
}
