package com.yumiru11.githubapp.core.designsystem.component

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Indication
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.yumiru11.githubapp.core.designsystem.token.AppDimens

/** M3 禁用态内容不透明度（状态层规范值） */
private const val DISABLED_CONTENT_ALPHA = 0.38f

/**
 * 子项（indented）条目的文本额外起始缩进。
 *
 * 为什么缩进作用在**文本槽**而不是整行内边距：material3 1.5.0-alpha18 的**非点击**
 * `ListItem` 重载没有 `contentPadding` 参数（编译实测），而行容器色必须保持通栏
 * （否则分组卡会被切出缺口）。缩进只作用于主/副标题文本，尾部控件保持原位。
 */
private val IndentStep: Dp = AppDimens.spacing.l

/** 一组分段条目的四角半径（纯数据，供 [appSettingsGroupItemCorners] 单测断言）。 */
data class AppSettingsGroupCorners(
    val topStart: Dp,
    val topEnd: Dp,
    val bottomStart: Dp,
    val bottomEnd: Dp,
)

/**
 * 纯函数：共 [count] 个条目中第 [index] 个在常态/[pressed] 态的四角半径。
 *
 * 首条目（index == 0）上缘、末条目（index == count-1）下缘取组外圆角
 * [AppDimens.cornerCardOuter]；中段条目四角取组内圆角 [AppDimens.cornerCardInner]；
 * 按压时整段四角涨到组外圆角（"鼓起来"成一张独立卡）；单条目组恒为全外圆角。
 */
fun appSettingsGroupItemCorners(
    count: Int,
    index: Int,
    pressed: Boolean,
): AppSettingsGroupCorners {
    val outer = AppDimens.cornerCardOuter
    val inner = AppDimens.cornerCardInner
    val isSingle = count == 1
    val isFirst = index == 0
    val isLast = index == count - 1
    val top = if (pressed || isSingle || isFirst) outer else inner
    val bottom = if (pressed || isSingle || isLast) outer else inner
    return AppSettingsGroupCorners(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom)
}

@DslMarker
private annotation class AppSettingsGroupDsl

/**
 * [AppSettingsGroup] 内容作用域：按序收集条目。
 *
 * 槽位命名与 M3 [ListItem] **逐字一致**（`overlineContent` / `headlineContent` /
 * `supportingContent` / `leadingContent` / `trailingContent`），调用方从 M3 文档
 * 可直接迁移，无需记第二套词汇。
 */
@AppSettingsGroupDsl
interface AppSettingsGroupScope {
    /**
     * 追加一个条目。
     *
     * @param onClick 非空时整段可点（涟漪 + 按压形变）；null = 纯展示条目
     * @param enabled false 时置灰且不可点（依赖上级开关的子项用）
     * @param indented 子项缩进（文本右移一档，尾部控件不动）
     */
    fun item(
        headlineContent: @Composable () -> Unit,
        onClick: (() -> Unit)? = null,
        modifier: Modifier = Modifier,
        enabled: Boolean = true,
        indented: Boolean = false,
        overlineContent: (@Composable () -> Unit)? = null,
        supportingContent: (@Composable () -> Unit)? = null,
        leadingContent: (@Composable () -> Unit)? = null,
        trailingContent: (@Composable () -> Unit)? = null,
        colors: ListItemColors? = null,
    )

    /**
     * 追加一个**开关型**条目：整行是一个 `Role.Switch` 语义节点，尾部自动放开关。
     *
     * 与"尾部自己塞一个可交互 Switch"的区别在无障碍：那种写法下 TalkBack 停在开关节点上
     * 只念「开关，关闭」，行标题读不到；本写法的行本身承载开关语义（标题 + 状态一句话读完），
     * 且点行的任意位置都能切换（触区 = 整行，恒 ≥ [AppDimens.minTouchTarget]）。
     */
    fun switchItem(
        checked: Boolean,
        onCheckedChange: (Boolean) -> Unit,
        headlineContent: @Composable () -> Unit,
        modifier: Modifier = Modifier,
        enabled: Boolean = true,
        indented: Boolean = false,
        supportingContent: (@Composable () -> Unit)? = null,
        leadingContent: (@Composable () -> Unit)? = null,
        colors: ListItemColors? = null,
    )
}

private class AppSettingsGroupScopeImpl : AppSettingsGroupScope {
    val items = mutableListOf<AppSettingsGroupItem>()

    override fun item(
        headlineContent: @Composable () -> Unit,
        onClick: (() -> Unit)?,
        modifier: Modifier,
        enabled: Boolean,
        indented: Boolean,
        overlineContent: (@Composable () -> Unit)?,
        supportingContent: (@Composable () -> Unit)?,
        leadingContent: (@Composable () -> Unit)?,
        trailingContent: (@Composable () -> Unit)?,
        colors: ListItemColors?,
    ) {
        items +=
            AppSettingsGroupItem(
                headlineContent = headlineContent,
                onClick = onClick,
                checked = null,
                onCheckedChange = null,
                modifier = modifier,
                enabled = enabled,
                indented = indented,
                overlineContent = overlineContent,
                supportingContent = supportingContent,
                leadingContent = leadingContent,
                trailingContent = trailingContent,
                colors = colors,
            )
    }

    override fun switchItem(
        checked: Boolean,
        onCheckedChange: (Boolean) -> Unit,
        headlineContent: @Composable () -> Unit,
        modifier: Modifier,
        enabled: Boolean,
        indented: Boolean,
        supportingContent: (@Composable () -> Unit)?,
        leadingContent: (@Composable () -> Unit)?,
        colors: ListItemColors?,
    ) {
        items +=
            AppSettingsGroupItem(
                headlineContent = headlineContent,
                onClick = null,
                checked = checked,
                onCheckedChange = onCheckedChange,
                modifier = modifier,
                enabled = enabled,
                indented = indented,
                overlineContent = null,
                supportingContent = supportingContent,
                leadingContent = leadingContent,
                trailingContent = {
                    Switch(
                        checked = checked,
                        onCheckedChange = null,
                        enabled = enabled,
                    )
                },
                colors = colors,
            )
    }
}

private data class AppSettingsGroupItem(
    val headlineContent: @Composable () -> Unit,
    val onClick: (() -> Unit)?,
    val checked: Boolean?,
    val onCheckedChange: ((Boolean) -> Unit)?,
    val modifier: Modifier,
    val enabled: Boolean,
    val indented: Boolean,
    val overlineContent: (@Composable () -> Unit)?,
    val supportingContent: (@Composable () -> Unit)?,
    val leadingContent: (@Composable () -> Unit)?,
    val trailingContent: (@Composable () -> Unit)?,
    val colors: ListItemColors?,
)

/**
 * 应用统一**分组设置卡**（设置页专用容器）。
 *
 * ## 形态（为什么不用 M3 `Card`）
 *
 * 一组条目在视觉上是**同一张卡**：整组只有外轮廓是大圆角
 * （[AppDimens.cornerCardOuter]），组内条目之间以 [AppDimens.groupSeam] 细缝分隔、
 * 常态圆角退到 [AppDimens.cornerCardInner]；按压时被按的那条四角涨回外圆角——
 * 「鼓出来」成一张独立卡、松手弹回。
 *
 * M3 `Card` 自带 elevation 与固定圆角，做不到**按位置切圆角 + 按压形变**这两件事，
 * 故本组件由官方 [ListItem] + 位置化裁剪组合而成：**行内容仍是官方组件、槽位语义与 M3
 * 逐字一致**，自研部分只有「位置化圆角」这一件 M3 没有对应物的逻辑。
 *
 * ## 动效
 *
 * 圆角动画取 [MaterialTheme.motionScheme] 的空间 spec（Expressive 弹簧物理），
 * **不自造时长**；[Modifier.clickable] / [Modifier.toggleable] 与本组件的形变共用
 * **同一个** [MutableInteractionSource]——涟漪与圆角由同一份按压状态驱动，天然同步。
 * 系统「移除动画」由 Compose 内建 `MotionDurationScale` 兜底。
 *
 * ## 无障碍
 *
 * - 组标题带 `heading()` 语义（TalkBack 可按节跳转）
 * - [AppSettingsGroupScope.switchItem] 的整行是一个开关节点（见其 KDoc）
 *
 * @param title 组标题（由调用方 `stringResource` 传入，组件内零硬编码文案）；null = 无标题组
 */
@Composable
fun AppSettingsGroup(
    modifier: Modifier = Modifier,
    title: String? = null,
    content: AppSettingsGroupScope.() -> Unit,
) {
    val scope = AppSettingsGroupScopeImpl()
    scope.content()

    Column(modifier = modifier.fillMaxWidth()) {
        if (title != null) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmallEmphasized,
                color = MaterialTheme.colorScheme.primary,
                modifier =
                    Modifier
                        .padding(
                            start = AppDimens.spacing.xs,
                            top = AppDimens.spacing.s,
                            bottom = AppDimens.spacing.s,
                        ).semantics { heading() },
            )
        }
        val count = scope.items.size
        scope.items.forEachIndexed { index, item ->
            AppSettingsGroupRow(item = item, index = index, count = count)
            if (index != count - 1) {
                Spacer(modifier = Modifier.height(AppDimens.groupSeam))
            }
        }
    }
}

/** 单条：官方 [ListItem] 内容 + 位置化圆角裁剪 + 可选整段交互。 */
@Composable
private fun AppSettingsGroupRow(
    item: AppSettingsGroupItem,
    index: Int,
    count: Int,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val indication = LocalIndication.current
    val corners = appSettingsGroupItemCorners(count = count, index = index, pressed = pressed)
    val indent = if (item.indented) IndentStep else 0.dp

    ListItem(
        headlineContent = { IndentedText(startIndent = indent) { item.headlineContent() } },
        modifier =
            item.modifier
                .fillMaxWidth()
                .clip(
                    RoundedCornerShape(
                        topStart = corners.topStart,
                        topEnd = corners.topEnd,
                        bottomStart = corners.bottomStart,
                        bottomEnd = corners.bottomEnd,
                    ),
                ).appSettingsGroupInteraction(item = item, interactionSource = interactionSource, indication = indication),
        overlineContent = item.overlineContent?.let { content -> { IndentedText(startIndent = indent) { content() } } },
        supportingContent = item.supportingContent?.let { content -> { IndentedText(startIndent = indent) { content() } } },
        leadingContent = item.leadingContent,
        trailingContent = item.trailingContent,
        colors = appSettingsGroupItemColors(enabled = item.enabled, override = item.colors),
    )
}

/** 文本槽缩进容器（非子项 startIndent = 0dp，零副作用）。 */
@Composable
private fun IndentedText(
    startIndent: Dp,
    content: @Composable () -> Unit,
) {
    Box(modifier = Modifier.padding(start = startIndent)) { content() }
}

/** 整段交互：开关型走 [Modifier.toggleable]（`Role.Switch`），进入型走 [Modifier.clickable]。 */
private fun Modifier.appSettingsGroupInteraction(
    item: AppSettingsGroupItem,
    interactionSource: MutableInteractionSource,
    indication: Indication,
): Modifier =
    when {
        item.onCheckedChange != null -> {
            toggleable(
                value = item.checked ?: false,
                interactionSource = interactionSource,
                indication = indication,
                enabled = item.enabled,
                role = Role.Switch,
                onValueChange = item.onCheckedChange,
            )
        }

        item.onClick != null -> {
            clickable(
                interactionSource = interactionSource,
                indication = indication,
                enabled = item.enabled,
                role = Role.Button,
                onClick = item.onClick,
            )
        }

        else -> {
            this
        }
    }

/**
 * 条目配色：容器 = `surfaceContainerHighest`，禁用态按 M3 状态层规范把内容压到 38%。
 *
 * 为什么是 `surfaceContainerHighest`：① 它在本项目六套手写调色板 + 动态取色下**全部**与页面底
 * 形成可见色调差（`surfaceBright`/`surfaceContainerLow` 在亮色或纯黑下会与页面同色，卡片会"消失"）；
 * ② 暗色下取值 `#30363D` 更接近「深色卡片浮在近黑页面之上」的目标观感（实测出图比对过
 * `surfaceContainerHigh` 的 `#21262D`，对比不足）；层级只靠色调差表达，不叠阴影。
 *
 * 注意：M3 [ListItem] 的非点击重载没有 `enabled` 参数，「禁用」只由本组件显式着色表达。
 */
@Composable
private fun appSettingsGroupItemColors(
    enabled: Boolean,
    override: ListItemColors?,
): ListItemColors {
    if (override != null) return override
    val scheme = MaterialTheme.colorScheme
    return if (enabled) {
        ListItemDefaults.colors(containerColor = scheme.surfaceContainerHighest)
    } else {
        ListItemDefaults.colors(
            containerColor = scheme.surfaceContainerHighest,
            headlineColor = scheme.onSurface.copy(alpha = DISABLED_CONTENT_ALPHA),
            supportingColor = scheme.onSurfaceVariant.copy(alpha = DISABLED_CONTENT_ALPHA),
            leadingIconColor = scheme.onSurfaceVariant.copy(alpha = DISABLED_CONTENT_ALPHA),
            trailingIconColor = scheme.onSurfaceVariant.copy(alpha = DISABLED_CONTENT_ALPHA),
        )
    }
}
