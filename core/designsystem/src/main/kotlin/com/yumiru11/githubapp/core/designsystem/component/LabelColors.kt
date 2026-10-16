package com.yumiru11.githubapp.core.designsystem.component

import androidx.compose.ui.graphics.Color

/**
 * GitHub label 胶囊配色（基准 = GitHubM3E 的 \`labelColors\`）。
 *
 * GitHub 的 label 颜色是 label 自带的 6 位 hex（无 #），需要就地算出**可读的内容色**：
 * 按 ITU-R BT.601 亮度加权，亮度 > 0.6 用深色字、否则白字——这样浅黄/浅绿底不会配白字
 * 变成"看不见的标签"（GitHub 网页端同款判定）。
 *
 * @param hex label 颜色（可带 # 前缀，可为 null/非法 → 回落到中性灰）
 * @return Pair(背景色, 内容色)
 */
fun labelColors(hex: String?): Pair<Color, Color> {
    val parsed = hex?.removePrefix("#")?.toLongOrNull(16) ?: LABEL_FALLBACK_HEX
    val background = Color(0xFF000000 or parsed)
    val luminance =
        (
            0.299 * ((parsed shr 16) and 0xFF) +
                0.587 * ((parsed shr 8) and 0xFF) +
                0.114 * (parsed and 0xFF)
        ) / 255.0
    val content = if (luminance > LABEL_LIGHT_LUMINANCE_THRESHOLD) Color(0xFF10131A) else Color(0xFFFFFFFF)
    return background to content
}

/** 无法解析颜色时的中性灰（GitHub 默认 label 灰）。 */
private const val LABEL_FALLBACK_HEX = 0x8B949EL

/** 亮度阈值：> 0.6 视为浅底（配深色字）。 */
private const val LABEL_LIGHT_LUMINANCE_THRESHOLD = 0.6
