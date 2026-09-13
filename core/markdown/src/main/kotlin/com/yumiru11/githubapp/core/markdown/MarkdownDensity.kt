package com.yumiru11.githubapp.core.markdown

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

/**
 * Markdown 阅读密度令牌 —— 原生渲染与 WebView 渲染的**单一事实来源**。
 *
 * ## 来源与 cm → dp 换算（2026-09-13 产品负责人真机实测目标）
 *
 * 实测屏幕 6.7 cm × 14.9 cm；本项目基准设备 411dp 宽 → **≈61.3 dp/cm**（411 / 6.7）。
 * 另列 360dp 宽设备的备选假设 **≈53.7 dp/cm**（360 / 6.7），供产品负责人对照：
 *
 * | 目标（cm） | 语义 | 61.3 dp/cm（411dp，主推 = [Literal]） | 53.7 dp/cm（360dp，备选） |
 * |---|---|---|---|
 * | 0.7 cm / 侧 | 左右间距 [sides] | 43 dp | ≈38 dp |
 * | 0.6 cm | 段间距 [paragraphGap] | 37 dp | ≈32 dp |
 * | 0.3 cm | 视觉行距 [lineLeading] | 18 dp（行盒 = 16 + 18 = 34 dp ≈ 2.125×） | ≈16 dp（行盒 32 dp = 2.0×） |
 * | 0.4 cm / 层 | 每层缩进 [indentPerLevel] | 24 dp | ≈21 dp |
 *
 * 未获产品负责人拍板的 53.7 dp/cm 备选**不落地**——只出现在上表与本 KDoc 中。
 *
 * ## 两个通道如何消费同一份令牌
 *
 * - **WebView**：[toCssVariables] 生成 `--md-reading-*` CSS 自定义属性（由
 *   [com.yumiru11.githubapp.core.markdown.webview.WebViewHtmlBuilder] 注入
 *   `<style id="reading-density">`），`markdown-you.css` 只消费 `var(--md-reading-*, 回退值)`。
 * - **原生**：`EnhancedMarkdownViewer` / `MarkdownViewer` 直接读取
 *   [MarkdownDensity.current] 的 [sides] / [paragraphGap] / [lineHeight] / [indentPerLevel]。
 *
 * 切换预设 = 改 [MarkdownDensity.current] 一行；两个通道同时生效。
 */
@Immutable
data class MarkdownDensityTokens(
    /** 正文左右外边距（每侧），WebView 的 `padding` / 原生的水平 padding。 */
    val sides: Dp,
    /** 段间距（段落块之间的垂直间距）。 */
    val paragraphGap: Dp,
    /** 行视觉行距：叠加在 16sp 正文字号之上的额外行距（行盒 = 16 + lineLeading）。 */
    val lineLeading: Dp,
    /** Markdown 每层缩进（列表 / 嵌套列表 / 引用块）。 */
    val indentPerLevel: Dp,
) {
    /** 原生文本行高（sp）：16sp 正文 + [lineLeading]。 */
    val lineHeight: TextUnit
        get() = (MarkdownDensity.BODY_FONT_SIZE.value + lineLeading.value).sp

    /** 行高倍数（WebView `line-height` 用）：lineHeight / 16。 */
    val lineHeightMultiplier: Float
        get() = (MarkdownDensity.BODY_FONT_SIZE.value + lineLeading.value) / MarkdownDensity.BODY_FONT_SIZE.value

    /**
     * 原生正文文本样式（正文 / 段落 / 引用 / 列表共用）：16sp + [lineHeight]，颜色由调用方给定。
     *
     * 两个原生 Viewer（`EnhancedMarkdownViewer` / `MarkdownViewer`）共用本方法，
     * 保证「原生行高」与「WebView line-height」同源（[lineHeight] / [lineHeightMultiplier]）。
     */
    fun bodyTextStyle(color: Color): TextStyle =
        TextStyle(fontSize = MarkdownDensity.BODY_FONT_SIZE, lineHeight = lineHeight, color = color)

    /**
     * 生成阅读密度 CSS 自定义属性声明块（注入 `<style id="reading-density">`）。
     *
     * 值一律为确定性的 px / 无单位倍数，不含任何用户输入；`markdown-you.css` 的
     * `var(--md-reading-*, 回退值)` 回退值与 [MarkdownDensity.Literal] 保持一致。
     */
    fun toCssVariables(): String =
        buildString {
            append(":root {\n")
            append("  ${MarkdownDensity.CSS_VAR_SIDES}: ${sides.toCssPx()};\n")
            append("  ${MarkdownDensity.CSS_VAR_PARAGRAPH_GAP}: ${paragraphGap.toCssPx()};\n")
            append("  ${MarkdownDensity.CSS_VAR_LINE_HEIGHT}: ${lineHeightMultiplier.toCssNumber()};\n")
            append("  ${MarkdownDensity.CSS_VAR_INDENT}: ${indentPerLevel.toCssPx()};\n")
            append("}\n")
        }
}

/**
 * 阅读密度预设与当前选择（唯一开关）。
 *
 * 默认 [Literal]（主推：严格按产品负责人实测 cm 值换算）。
 * 切换保守观感 = 把 [current] 改为 [Conservative]（一行）。
 */
object MarkdownDensity {
    /** 正文基准字号（与 markdown-you.css 的 16px / 原生 16.sp 对齐）。 */
    val BODY_FONT_SIZE: TextUnit = 16.sp

    /** WebView 左右间距变量。 */
    const val CSS_VAR_SIDES: String = "--md-reading-sides"

    /** WebView 段间距变量。 */
    const val CSS_VAR_PARAGRAPH_GAP: String = "--md-reading-paragraph-gap"

    /** WebView 行高倍数变量。 */
    const val CSS_VAR_LINE_HEIGHT: String = "--md-reading-line-height"

    /** WebView 每层缩进变量。 */
    const val CSS_VAR_INDENT: String = "--md-reading-indent"

    /**
     * 字面预设（主推）：0.7cm 侧距 / 0.6cm 段距 / 0.3cm 行距 / 0.4cm 每层缩进
     * 按 61.3 dp/cm 直译。
     */
    val Literal: MarkdownDensityTokens =
        MarkdownDensityTokens(
            sides = 43.dp,
            paragraphGap = 37.dp,
            lineLeading = 18.dp,
            indentPerLevel = 24.dp,
        )

    /**
     * 保守预设：侧距/段距对齐现有 24dp 栅格，行高 1.8（视觉行距 12.8dp），每层缩进 24dp。
     */
    val Conservative: MarkdownDensityTokens =
        MarkdownDensityTokens(
            sides = 24.dp,
            paragraphGap = 24.dp,
            lineLeading = 12.8.dp,
            indentPerLevel = 24.dp,
        )

    /**
     * 当前生效预设 —— **唯一开关**：改这一行为 [Literal] / [Conservative] 即同时切换
     * WebView 与原生两个渲染通道。
     */
    val current: MarkdownDensityTokens = Literal
}

/** Dp → CSS px 字符串（整数值省去 `.0`，与既有静态 px 常量同构）。 */
private fun Dp.toCssPx(): String {
    val v = value
    return if (v % 1f == 0f) "${v.toInt()}px" else "${v}px"
}

/** Float → CSS 数值（最多 4 位小数，去尾零；Locale.US 保证小数点与语言环境无关）。 */
private fun Float.toCssNumber(): String =
    String
        .format(Locale.US, "%.4f", this)
        .trimEnd('0')
        .trimEnd('.')
