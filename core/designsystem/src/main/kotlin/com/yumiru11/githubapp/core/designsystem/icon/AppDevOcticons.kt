/*
 * AppDev Octicons — extracted from the Gradle 8.12 user guide icon sprite.
 *
 * Source: gradle-8.12/docs/userguide/img/octicons-16.svg
 * Sprite metadata: "Octicons v11.2.0 by GitHub - https://primer.style/octicons/ - License: MIT"
 * These five paths are the only extracted assets. Keep this provenance comment if you regenerate.
 */
package com.yumiru11.githubapp.core.designsystem.icon

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/** Octicons v11.2.0 (MIT) needed by the GitHub Alert card. */
object AppDevOcticons {
    /** GitHub alert NOTE icon. */
    val Info: ImageVector by lazy {
        octicon(
            name = "Info",
            pathData =
                "M8 1.5a6.5 6.5 0 100 13 6.5 6.5 0 000-13zM0 8a8 8 0 1116 0A8 8 0 010 8zm6.5-.25A.75.75 0 0" +
                    "17.25 7h1a.75.75 0 01.75.75v2.75h.25a.75.75 0 010 1.5h-2a.75.75 0 010-1.5h.25v-2h-.25a.75." +
                    "75 0 01-.75-.75zM8 6a1 1 0 100-2 1 1 0 000 2z",
        )
    }

    /** GitHub alert TIP icon. */
    val LightBulb: ImageVector by lazy {
        octicon(
            name = "LightBulb",
            pathData =
                "M8 1.5c-2.363 0-4 1.69-4 3.75 0 .984.424 1.625.984 2.304l.214.253c.223.264.47.556.673.848." +
                    "284.411.537.896.621 1.49a.75.75 0 01-1.484.211c-.04-.282-.163-.547-.37-.847a8.695 8.695 0 " +
                    "00-.542-.68c-.084-.1-.173-.205-.268-.32C3.201 7.75 2.5 6.766 2.5 5.25 2.5 2.31 4.863 0 8 0" +
                    "s5.5 2.31 5.5 5.25c0 1.516-.701 2.5-1.328 3.259-.095.115-.184.22-.268.319-.207.245-.383.45" +
                    "3-.541.681-.208.3-.33.565-.37.847a.75.75 0 01-1.485-.212c.084-.593.337-1.078.621-1.489.203" +
                    "-.292.45-.584.673-.848.075-.088.147-.173.213-.253.561-.679.985-1.32.985-2.304 0-2.06-1.637" +
                    "-3.75-4-3.75zM6 15.25a.75.75 0 01.75-.75h2.5a.75.75 0 010 1.5h-2.5a.75.75 0 01-.75-.75zM5." +
                    "75 12a.75.75 0 000 1.5h4.5a.75.75 0 000-1.5h-4.5z",
        )
    }

    /** GitHub alert IMPORTANT icon. */
    val Alert: ImageVector by lazy {
        octicon(
            name = "Alert",
            pathData =
                "M8.22 1.754a.25.25 0 00-.44 0L1.698 13.132a.25.25 0 00.22.368h12.164a.25.25 0 00.22-.368L8" +
                    ".22 1.754zm-1.763-.707c.659-1.234 2.427-1.234 3.086 0l6.082 11.378A1.75 1.75 0 0114.082 15" +
                    "H1.918a1.75 1.75 0 01-1.543-2.575L6.457 1.047zM9 11a1 1 0 11-2 0 1 1 0 012 0zm-.25-5.25a.7" +
                    "5.75 0 00-1.5 0v2.5a.75.75 0 001.5 0v-2.5z",
        )
    }

    /** GitHub alert WARNING icon. */
    val Stop: ImageVector by lazy {
        octicon(
            name = "Stop",
            pathData =
                "M4.47.22A.75.75 0 015 0h6a.75.75 0 01.53.22l4.25 4.25c.141.14.22.331.22.53v6a.75.75 0 01-." +
                    "22.53l-4.25 4.25A.75.75 0 0111 16H5a.75.75 0 01-.53-.22L.22 11.53A.75.75 0 010 11V5a.75.75" +
                    " 0 01.22-.53L4.47.22zm.84 1.28L1.5 5.31v5.38l3.81 3.81h5.38l3.81-3.81V5.31L10.69 1.5H5.31z" +
                    "M8 4a.75.75 0 01.75.75v3.5a.75.75 0 01-1.5 0v-3.5A.75.75 0 018 4zm0 8a1 1 0 100-2 1 1 0 00" +
                    "0 2z",
        )
    }

    /** GitHub alert CAUTION icon. */
    val Flame: ImageVector by lazy {
        octicon(
            name = "Flame",
            pathData =
                "M7.998 14.5c2.832 0 5-1.98 5-4.5 0-1.463-.68-2.19-1.879-3.383l-.036-.037c-1.013-1.008-2.3-" +
                    "2.29-2.834-4.434-.322.256-.63.579-.864.953-.432.696-.621 1.58-.046 2.73.473.947.67 2.284-." +
                    "278 3.232-.61.61-1.545.84-2.403.633a2.788 2.788 0 01-1.436-.874A3.21 3.21 0 003 10c0 2.53 " +
                    "2.164 4.5 4.998 4.5zM9.533.753C9.496.34 9.16.009 8.77.146 7.035.75 4.34 3.187 5.997 6.5c.3" +
                    "44.689.285 1.218.003 1.5-.419.419-1.54.487-2.04-.832-.173-.454-.659-.762-1.035-.454C2.036 " +
                    "7.44 1.5 8.702 1.5 10c0 3.512 2.998 6 6.498 6s6.5-2.5 6.5-6c0-2.137-1.128-3.26-2.312-4.438" +
                    "-1.19-1.184-2.436-2.425-2.653-4.81z",
        )
    }
}

private fun octicon(
    name: String,
    pathData: String,
): ImageVector =
    ImageVector
        .Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 16f,
            viewportHeight = 16f,
        ).apply {
            addPath(
                pathData = PathParser().parsePathString(pathData).toNodes(),
                fill = SolidColor(Color.Black),
            )
        }.build()
