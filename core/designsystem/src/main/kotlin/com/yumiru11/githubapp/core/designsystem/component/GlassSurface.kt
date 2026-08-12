package com.yumiru11.githubapp.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.yumiru11.githubapp.core.designsystem.token.AppBlur

/**
 * 玻璃拟真容器（docs/ui-design.md §6）。
 *
 * 顶栏 / 底栏等 §6.1 允许清单场景的毛玻璃背景容器：
 * - **API 31+**（[AppBlur.isBlurSupported]）：[Modifier.blur]（RenderEffect 真模糊）
 *   模糊背后滚动内容；[BlurredEdgeTreatment.Unbounded] 让模糊延伸到元素边界之外，
 *   保证顶栏玻璃覆盖状态栏区域时该区域同样被模糊。
 * - **API 26–30**：纯半透明 surface 层降级（[AppBlur.SCRIM_ALPHA] alpha），
 *   不做 bitmap 模糊，性能优先（§6.2）。
 * - **[blurEnabled]=false**：完全不模糊，仅半透明 surface（设置页「毛玻璃」开关关闭时）。
 *
 * 玻璃层颜色一律取自 `MaterialTheme.colorScheme.surface`，禁止硬编码颜色。
 *
 * 性能约束（§6.2，调用方责任）：
 * - 只用于 §6.1 允许清单（顶栏 / 底栏 / BottomSheet / 全屏查看器 / Banner 悬浮层）
 * - **禁止**列表 item 内使用；**禁止**叠加超过 2 层毛玻璃；**禁止**动态模糊
 *
 * 接线方式（T6 完整装配 / T4/T10 集成时）：
 * - 顶栏：`GlassSurface(windowInsets = WindowInsets.statusBars, blurEnabled = blurEnabled)`
 *   包住 `TopAppBar`，并将 TopAppBar 的 `containerColor` 设为 `Color.Transparent`
 * - 底栏：同上包住 `NavigationBar`，`windowInsets = WindowInsets.navigationBars`
 * - [blurEnabled] 由 feature 层收集 `UserPreferencesRepository.blurEnabled` 后传入
 *
 * @param modifier 应用在玻璃容器上的修饰符（尺寸 / 对齐等）
 * @param shape 玻璃容器形状；圆角时内容同样被裁剪到该形状
 * @param windowInsets 内容需要避让的系统 insets（如状态栏 / 导航栏）。
 *   玻璃背景延伸进 insets 区域（玻璃盖住状态栏），内容按 insets 内缩。
 *   默认无 insets。
 * @param blurEnabled 毛玻璃开关；false 时退化为纯半透明 surface（不模糊）
 * @param content 玻璃层之上的内容
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RectangleShape,
    windowInsets: WindowInsets = WindowInsets(0.dp),
    blurEnabled: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    val surfaceColor = MaterialTheme.colorScheme.surface
    // 半透明纯色层：模糊层之上叠加，保证内容可读性（§6.1 静止态 / §6.2 降级层）
    val glassColor = surfaceColor.copy(alpha = AppBlur.SCRIM_ALPHA)
    val useBlur = blurEnabled && AppBlur.isBlurSupported()

    Box(
        modifier =
            modifier
                .then(
                    if (useBlur) {
                        Modifier.blur(
                            radius = AppBlur.blurRadius,
                            edgeTreatment = BlurredEdgeTreatment.Unbounded,
                        )
                    } else {
                        Modifier
                    },
                ).background(color = glassColor, shape = shape)
                .clip(shape),
    ) {
        Box(modifier = Modifier.windowInsetsPadding(windowInsets)) {
            content()
        }
    }
}
