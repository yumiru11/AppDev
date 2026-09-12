package com.yumiru11.githubapp.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/**
 * 排版令牌（plan.md §5.4「字体：`AppTypography`（字阶、代码等宽追加）」）——全应用字号的
 * **单一事实来源**，由 [AppTheme] 注入 `MaterialTheme(typography = ...)`。
 *
 * ## 取值来源（可追溯，非凭记忆）
 * - **[displayLarge]..[labelSmall]**（M3 15 档）= `androidx.compose.material3` 的
 *   `Typography()` **官方默认值**（material3 1.4.0 / compose-bom 2026.06.01）。
 *   这些值是**在该 artifact 上跑 JVM 探针导出**的，不是照抄记忆里的规范表。
 *   `docs/ui-design.md` 没有为 UI 字阶逐档指定字号（§1.1 只管阴影/圆角/图标，§12 只把
 *   「flexible typography」列为 M3 Expressive 方向），所以按官方 M3 baseline 落地：
 *   与「不传 `typography`」时**逐字段**相等 → 零视觉回归由
 *   `AppTypographyTest.from_material3Defaults_equalsEveryTier` 逐档锁死。
 * - **[markdownHeading1]..[markdownCode]** = `docs/ui-design.md` §3.11「排版基准：
 *   **GitHub 网页基准**（正文 16sp / 1.6 行距，标题比例网页版）」，值与
 *   `core/markdown`（`MarkdownViewer` / `EnhancedMarkdownViewer` 的 `markdownTypography`）
 *   现有硬编码值逐一对应——本票只立令牌、不改字号、不接线消费者。
 * - 代码等宽 = 同条 §5.4 的「代码等宽追加」：见 [markdownCode]。
 *
 * ## 为什么 M3 档要显式写 `platformStyle` / `lineHeightStyle`
 * M3 baseline 的每个 `TextStyle` 除字号/行高/字距/字重外，还带两个**影响排版度量**的字段：
 * `PlatformTextStyle(includeFontPadding = false)`（关掉字体内边距）与
 * `LineHeightStyle(Center, Trim.None)`（行高校正居中分配）。它们同样由探针实测得出；
 * 漏掉不会改字号，却会悄悄改行盒与首行内边距——「看着一样、实际不一样」的视觉回归。
 * Markdown 档**不设**这两个字段，因为 `core/markdown` 现状同样没设（接线时保持现状）。
 *
 * ## 待用户校准（挂账）
 * - **「md 标题字号偏大」= FEEDBACK #11 / #23**（`docs/ui-audit-2026-08-21.md`
 *   「md 标题字号校准：等实机微调」）。校准口径 = **只改本文件的
 *   [markdownHeading1]..[markdownHeading6]**（单点），再由 `core/markdown` 消费；
 *   现状值是 GitHub 网页比例（h1 32sp … h6 14sp），是否整体收一档等实机走查拍板。
 * - M3 15 档**不是**待校准项：它们是官方 baseline，偏离 baseline 必须是有意为之——
 *   改了 `AppTypographyTest` 会立刻红，正是为了逼出这个「有意」。
 *
 * ## 为什么没有 `scale` 参数
 * 与 [AppShapes.from] 的 `cornerScale` 不同：设置页只有「圆角强度」「动画强度」两个
 * 滑杆（`AppearanceSettingsSection`），**没有字号滑杆**，故不发明参数。保留工厂形态
 * [from]，将来真加字号滑杆时签名为 `from(fontScale: Float)` 即可，调用点不必改。
 * 无障碍「大字号」由系统 fontScale 在 sp 渲染层生效，无需应用内自造刻度。
 */
object AppTypography {
    // ── M3 baseline 15 档（来源：androidx.compose.material3 Typography() 官方默认值）──

    /** 特大展示（营销/空态大标题） */
    val displayLarge: TextStyle = baselineStyle(fontSize = 57.sp, lineHeight = 64.sp, letterSpacing = (-0.2).sp)

    /** 展示（大空态插图标题） */
    val displayMedium: TextStyle = baselineStyle(fontSize = 45.sp, lineHeight = 52.sp, letterSpacing = 0.sp)

    /** 小展示 */
    val displaySmall: TextStyle = baselineStyle(fontSize = 36.sp, lineHeight = 44.sp, letterSpacing = 0.sp)

    /** 大标题（页面主标题） */
    val headlineLarge: TextStyle = baselineStyle(fontSize = 32.sp, lineHeight = 40.sp, letterSpacing = 0.sp)

    /** 中标题（引导页/登录页标题） */
    val headlineMedium: TextStyle = baselineStyle(fontSize = 28.sp, lineHeight = 36.sp, letterSpacing = 0.sp)

    /** 小标题（首页分区大标题） */
    val headlineSmall: TextStyle = baselineStyle(fontSize = 24.sp, lineHeight = 32.sp, letterSpacing = 0.sp)

    /** 特大桥接标题（TopAppBar、BottomSheet 标题） */
    val titleLarge: TextStyle = baselineStyle(fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = 0.sp)

    /** 桥接标题（卡片/对话框标题、空态标题）；M3 起用 Medium 字重 */
    val titleMedium: TextStyle =
        baselineStyle(fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.2.sp, fontWeight = FontWeight.Medium)

    /** 小桥接标题（列表主标题、时间线条目） */
    val titleSmall: TextStyle =
        baselineStyle(fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp, fontWeight = FontWeight.Medium)

    /** 大正文（正文首选档） */
    val bodyLarge: TextStyle = baselineStyle(fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.5.sp)

    /** 中正文（次要说明文字） */
    val bodyMedium: TextStyle = baselineStyle(fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.2.sp)

    /** 小正文（辅助信息、注释） */
    val bodySmall: TextStyle = baselineStyle(fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.4.sp)

    /** 大标签（按钮文字、长条操作） */
    val labelLarge: TextStyle =
        baselineStyle(fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp, fontWeight = FontWeight.Medium)

    /** 中标签（Chip、状态徽标） */
    val labelMedium: TextStyle =
        baselineStyle(fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp, fontWeight = FontWeight.Medium)

    /** 小标签（角标、极简辅助标注） */
    val labelSmall: TextStyle =
        baselineStyle(fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp, fontWeight = FontWeight.Medium)

    // ── Markdown 内容区字阶（ui-design §3.11 GitHub 网页基准；标题档 = 校准点）──
    // 颜色不属令牌（同一档在 light/dark/6 套主题下取不同 color role）：消费方以
    // `AppTypography.markdownX.copy(color = MaterialTheme.colorScheme.onSurface)` 组合。
    // letterSpacing / platformStyle / lineHeightStyle 均与 core/markdown 现状一致
    // （不设）—— 接线时零视觉变化。

    /** md `#`（校准点：FEEDBACK #11/#23「标题偏大」的调整入口） */
    val markdownHeading1: TextStyle = markdownStyle(fontSize = 32.sp, lineHeight = 40.sp, fontWeight = FontWeight.Medium)

    /** md `##`（校准点） */
    val markdownHeading2: TextStyle = markdownStyle(fontSize = 24.sp, lineHeight = 32.sp, fontWeight = FontWeight.Medium)

    /** md `###`（校准点） */
    val markdownHeading3: TextStyle = markdownStyle(fontSize = 20.sp, lineHeight = 28.sp, fontWeight = FontWeight.Medium)

    /** md `####`（校准点） */
    val markdownHeading4: TextStyle = markdownStyle(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium)

    /** md `#####`（校准点） */
    val markdownHeading5: TextStyle = markdownStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium)

    /** md `######`（校准点） */
    val markdownHeading6: TextStyle = markdownStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium)

    /** md 正文 / 引用 / 列表（§3.11：正文 16sp、行距 = 1.6 × 字号） */
    val markdownBody: TextStyle = markdownStyle(fontSize = 16.sp, lineHeight = 25.6.sp)

    /**
     * md 代码块 / 行内代码（§5.4「代码等宽追加」）。
     *
     * `fontFamily` = 等宽（= 偏好 `CodeFont.MONO` 的默认档）；用户切到 `CodeFont.SYSTEM`
     * 时由消费方覆盖 `fontFamily`——令牌给默认值，偏好给覆盖值，两者不互为主。
     */
    val markdownCode: TextStyle =
        markdownStyle(fontSize = 14.sp, lineHeight = 20.sp, fontFamily = FontFamily.Monospace)

    /**
     * 注入 `MaterialTheme(typography = ...)` 的 M3 [Typography]（[AppTheme] 调用）。
     *
     * 返回 [baseline] 单例而非每次新建：[Typography] 未重写 `equals`（身份比较），
     * 每次重组新建实例会让 `LocalTypography` 变化、令全树文本样式失效；单例引用稳定，
     * 避免主题颜色转场（[rememberAnimatedColorScheme] 逐帧重组）时的无用失效。
     */
    fun from(): Typography = baseline

    /** 15 档顺序与 M3 [Typography] 构造参数一致；声明在所有令牌之后以保证初始化顺序。 */
    private val baseline: Typography =
        Typography(
            displayLarge = displayLarge,
            displayMedium = displayMedium,
            displaySmall = displaySmall,
            headlineLarge = headlineLarge,
            headlineMedium = headlineMedium,
            headlineSmall = headlineSmall,
            titleLarge = titleLarge,
            titleMedium = titleMedium,
            titleSmall = titleSmall,
            bodyLarge = bodyLarge,
            bodyMedium = bodyMedium,
            bodySmall = bodySmall,
            labelLarge = labelLarge,
            labelMedium = labelMedium,
            labelSmall = labelSmall,
        )

    /** M3 baseline 档：SansSerif 字族 + 补齐 baseline 的段落/行高样式（见类 KDoc）。 */
    private fun baselineStyle(
        fontSize: TextUnit,
        lineHeight: TextUnit,
        letterSpacing: TextUnit,
        fontWeight: FontWeight = FontWeight.Normal,
    ): TextStyle =
        TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = fontWeight,
            fontSize = fontSize,
            lineHeight = lineHeight,
            letterSpacing = letterSpacing,
            // 这两行不是可有可无：M3 baseline 实测带这两个字段，漏掉不改字号但改排版度量
            platformStyle = PlatformTextStyle(includeFontPadding = false),
            lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
        )

    /** Markdown 档：只写字族/字重/字号/行高，其余留默认（= `core/markdown` 现状）。 */
    private fun markdownStyle(
        fontSize: TextUnit,
        lineHeight: TextUnit,
        fontWeight: FontWeight = FontWeight.Normal,
        fontFamily: FontFamily = FontFamily.SansSerif,
    ): TextStyle =
        TextStyle(
            fontFamily = fontFamily,
            fontWeight = fontWeight,
            fontSize = fontSize,
            lineHeight = lineHeight,
        )
}
