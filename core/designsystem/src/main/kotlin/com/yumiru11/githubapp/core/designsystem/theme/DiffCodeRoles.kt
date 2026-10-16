package com.yumiru11.githubapp.core.designsystem.theme

import androidx.compose.ui.graphics.Color

/**
 * Diff / 代码查看器的语义色组（GitHubM3E `GhExtraColors` 的 diff 部分）。
 *
 * 单独抽成数据 + [withDiffRoles] 的原因有二：
 * 1. 六套色板（light/dark/OLED/高对比×2/默认）都要给这 10 个字段赋值，直接写在
 *    `ExtendedColors(...)` 里会把每个 palette 函数撑过 detekt 的 LongMethod 阈值；
 * 2. 取值口径集中在此处，改配色不用翻五个 palette 函数。
 */
internal data class DiffCodeRoles(
    val addBackground: Color,
    val addText: Color,
    val delBackground: Color,
    val delText: Color,
    val hunkBackground: Color,
    val hunkText: Color,
    val codeBackground: Color,
    val lineNumber: Color,
    val star: Color,
    val skeleton: Color,
)

/** 把 [roles] 写进 [ExtendedColors] 的 diff/code 字段（palette 函数末尾链式调用）。 */
internal fun ExtendedColors.withDiffRoles(roles: DiffCodeRoles): ExtendedColors =
    copy(
        diffAddBackground = roles.addBackground,
        diffAddText = roles.addText,
        diffDelBackground = roles.delBackground,
        diffDelText = roles.delText,
        diffHunkBackground = roles.hunkBackground,
        diffHunkText = roles.hunkText,
        codeBackground = roles.codeBackground,
        codeLineNumber = roles.lineNumber,
        star = roles.star,
        skeleton = roles.skeleton,
    )

/** Light（GitHub primer light）：add=success 族、del=danger 族、hunk=info 族。 */
internal val DiffCodeRolesLight =
    DiffCodeRoles(
        addBackground = Color(0xFFDAFBE1),
        addText = Color(0xFF1A7F37),
        delBackground = Color(0xFFFFEBE9),
        delText = Color(0xFFCF222E),
        hunkBackground = Color(0xFFDDF4FF),
        hunkText = Color(0xFF0969DA),
        codeBackground = Color(0xFFEEF1F5),
        lineNumber = Color(0xFF656D76),
        star = Color(0xFFE3A008),
        skeleton = Color(0xFFE6E9ED),
    )

/** Dark。 */
internal val DiffCodeRolesDark =
    DiffCodeRoles(
        addBackground = Color(0xFF0F2A1A),
        addText = Color(0xFF3FB950),
        delBackground = Color(0xFF2C1517),
        delText = Color(0xFFF85149),
        hunkBackground = Color(0xFF0D1F33),
        hunkText = Color(0xFF58A6FF),
        codeBackground = Color(0xFF161B22),
        lineNumber = Color(0xFF6E7681),
        star = Color(0xFFE3B341),
        skeleton = Color(0xFF21262D),
    )

/** OLED：代码底压到纯黑，diff 底色再压暗一档（省电且对比更强）。 */
internal val DiffCodeRolesOled =
    DiffCodeRoles(
        addBackground = Color(0xFF0B1A12),
        addText = Color(0xFF3FB950),
        delBackground = Color(0xFF1F0F10),
        delText = Color(0xFFF85149),
        hunkBackground = Color(0xFF0E1725),
        hunkText = Color(0xFF58A6FF),
        codeBackground = Color(0xFF000000),
        lineNumber = Color(0xFF6E7681),
        star = Color(0xFFE3B341),
        skeleton = Color(0xFF161B22),
    )

/** 高对比浅色：文字端更深（≥7:1 方向），底色保持浅。 */
internal val DiffCodeRolesHighContrastLight =
    DiffCodeRoles(
        addBackground = Color(0xFFDAFBE1),
        addText = Color(0xFF0B5A20),
        delBackground = Color(0xFFFFEBE9),
        delText = Color(0xFF8B1A12),
        hunkBackground = Color(0xFFDDF4FF),
        hunkText = Color(0xFF0550AE),
        codeBackground = Color(0xFFF0F3F6),
        lineNumber = Color(0xFF424A53),
        star = Color(0xFF9A6700),
        skeleton = Color(0xFFD0D7DE),
    )

/** 高对比深色：文字端更亮（GitHub HC 口径）。 */
internal val DiffCodeRolesHighContrastDark =
    DiffCodeRoles(
        addBackground = Color(0xFF0F2A1A),
        addText = Color(0xFF7EE787),
        delBackground = Color(0xFF2C1517),
        delText = Color(0xFFFF9492),
        hunkBackground = Color(0xFF0D1F33),
        hunkText = Color(0xFFA5D6FF),
        codeBackground = Color(0xFF0D1117),
        lineNumber = Color(0xFF8B949E),
        star = Color(0xFFD29922),
        skeleton = Color(0xFF30363D),
    )
