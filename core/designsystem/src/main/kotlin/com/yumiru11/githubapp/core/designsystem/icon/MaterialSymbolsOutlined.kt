package com.yumiru11.githubapp.core.designsystem.icon

import androidx.compose.ui.graphics.vector.ImageVector
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.outlined.Account_circle
import com.composables.icons.materialsymbols.outlined.Blur_on
import com.composables.icons.materialsymbols.outlined.Brightness_2
import com.composables.icons.materialsymbols.outlined.Code
import com.composables.icons.materialsymbols.outlined.Contrast
import com.composables.icons.materialsymbols.outlined.Data_usage
import com.composables.icons.materialsymbols.outlined.Expand_less
import com.composables.icons.materialsymbols.outlined.Expand_more
import com.composables.icons.materialsymbols.outlined.Format_color_fill
import com.composables.icons.materialsymbols.outlined.Format_list_numbered
import com.composables.icons.materialsymbols.outlined.Home
import com.composables.icons.materialsymbols.outlined.Image
import com.composables.icons.materialsymbols.outlined.Info
import com.composables.icons.materialsymbols.outlined.Language
import com.composables.icons.materialsymbols.outlined.Layers
import com.composables.icons.materialsymbols.outlined.Motion_photos_on
import com.composables.icons.materialsymbols.outlined.Notifications
import com.composables.icons.materialsymbols.outlined.Palette
import com.composables.icons.materialsymbols.outlined.Person
import com.composables.icons.materialsymbols.outlined.Playlist_play
import com.composables.icons.materialsymbols.outlined.Rounded_corner
import com.composables.icons.materialsymbols.outlined.Search
import com.composables.icons.materialsymbols.outlined.Settings
import com.composables.icons.materialsymbols.outlined.Shapes
import com.composables.icons.materialsymbols.outlined.Tune
import com.composables.icons.materialsymbols.outlined.Vpn_key

/**
 * Material Symbols **Outlined** 家族的常用图标（内部实现细节，对外只暴露 [AppIcons]）。
 *
 * com.composables 的图标是**扩展属性**（`val MaterialSymbols.Outlined.Home`），四个家族
 * 同名 → 同一文件里逐名 import 会「同名冲突」，`as` 别名又会让未别名的那一族独占该名字
 * （实测编译不过）。故**一个家族一个文件**，各自显式 import；[AppIcons] 再把四族组合成
 * 单个语义图标的四变体规格。使用时必须写全接收者（`MaterialSymbols.Outlined.Home`）。
 *
 * 多词图标在 com.composables 2.2.1 里的命名为**首字母大写的 snake_case**（`Arrow_back`，
 * 不是 `ArrowBack`）——名字取自库元数据实测，不是猜的。
 */
internal object MaterialSymbolsOutlined {
    val Home: ImageVector = MaterialSymbols.Outlined.Home

    val Info: ImageVector = MaterialSymbols.Outlined.Info

    val Notifications: ImageVector = MaterialSymbols.Outlined.Notifications

    val Person: ImageVector = MaterialSymbols.Outlined.Person

    val Search: ImageVector = MaterialSymbols.Outlined.Search

    val Settings: ImageVector = MaterialSymbols.Outlined.Settings

    val Expand_less: ImageVector = MaterialSymbols.Outlined.Expand_less

    val Expand_more: ImageVector = MaterialSymbols.Outlined.Expand_more

    val Language: ImageVector = MaterialSymbols.Outlined.Language

    val Tune: ImageVector = MaterialSymbols.Outlined.Tune

    val Account_circle: ImageVector = MaterialSymbols.Outlined.Account_circle

    val Palette: ImageVector = MaterialSymbols.Outlined.Palette

    val Motion_photos_on: ImageVector = MaterialSymbols.Outlined.Motion_photos_on

    val Code: ImageVector = MaterialSymbols.Outlined.Code

    val Contrast: ImageVector = MaterialSymbols.Outlined.Contrast

    val Brightness_2: ImageVector = MaterialSymbols.Outlined.Brightness_2

    val Format_color_fill: ImageVector = MaterialSymbols.Outlined.Format_color_fill

    val Shapes: ImageVector = MaterialSymbols.Outlined.Shapes

    val Blur_on: ImageVector = MaterialSymbols.Outlined.Blur_on

    val Image: ImageVector = MaterialSymbols.Outlined.Image

    val Rounded_corner: ImageVector = MaterialSymbols.Outlined.Rounded_corner

    val Format_list_numbered: ImageVector = MaterialSymbols.Outlined.Format_list_numbered

    val Vpn_key: ImageVector = MaterialSymbols.Outlined.Vpn_key

    val Data_usage: ImageVector = MaterialSymbols.Outlined.Data_usage

    val Playlist_play: ImageVector = MaterialSymbols.Outlined.Playlist_play

    val Layers: ImageVector = MaterialSymbols.Outlined.Layers
}
