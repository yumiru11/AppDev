package com.yumiru11.githubapp.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.yumiru11.githubapp.core.designsystem.token.AppBlur
import com.yumiru11.githubapp.core.designsystem.token.GlassRenderMode
import com.yumiru11.githubapp.core.designsystem.token.GlassRenderPolicy
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.hazeEffect

/**
 * 玻璃拟真容器（docs/ui-design.md §6；issue #83 改为 backdrop blur）。
 *
 * 顶栏 / 底栏等 §6.1 允许清单场景的毛玻璃背景容器：
 * - **API 31+** 且上游提供了 [LocalHazeState]（模式由 [GlassRenderPolicy] 判定）：
 *   挂 Haze `hazeEffect`（RenderEffect backdrop blur），模糊 **背后滚动内容**
 *   （内容侧须挂 `hazeSource`，由同时持有栏与内容的容器组件接线——MainTabPager /
 *   HomeScreen）。玻璃层自身与栏内文字/图标保持锐利（修复 ui-audit 缺陷 #1：
 *   旧实现 `Modifier.blur` 误模糊自身绘制内容，即 FEEDBACK #17 根因）。
 *   半透明 scrim 叠在模糊层之上保证可读性（§6.1 静止态）。
 * - **API 26–30 / 无 HazeState / [blurEnabled]=false**：纯半透明 surface 层降级
 *   （[AppBlur.SCRIM_ALPHA] alpha），不做 bitmap 模糊，性能优先（§6.2）；
 *   API<31 半透明降级策略与旧 AppBlur 方案一致。
 *
 * 玻璃层颜色一律取自 `MaterialTheme.colorScheme.surface`，禁止硬编码颜色。
 *
 * 性能约束（§6.2，调用方责任）：
 * - 只用于 §6.1 允许清单（顶栏 / 底栏 / BottomSheet / 通知面板 / 全屏查看器 /
 *   Banner 悬浮层）
 * - **禁止**列表 item 内使用；**禁止**叠加超过 2 层毛玻璃；**禁止**动态模糊
 *
 * 接线方式：
 * - 玻璃栏：本组件包住 `TopAppBar` / `NavigationBar`，后者 `containerColor` 设为
 *   `Color.Transparent`、自身 insets 归零；系统栏 insets 由 [windowInsets] 统一处理
 *   （玻璃延伸进状态栏/导航栏区域，内容按 insets 内缩）
 * - 内容侧：容器组件对「栏背后的滚动内容」挂 `Modifier.hazeSource(hazeState)`，
 *   并用 `CompositionLocalProvider(LocalHazeState provides rememberHazeState())`
 *   覆盖栏与内容的公共父级（见 MainTabPager / HomeScreen 示例）
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
    val hazeState = LocalHazeState.current
    // 渲染模式判定收敛到 GlassRenderPolicy（issue #83）：三条降级路径（关开关 /
    // 无 HazeState / API<31）改由纯函数判定并已由单测断言——本组件的分支在
    // Robolectric 下不可像素断言（不渲染 RenderEffect），此前只能靠真机看。
    val renderMode = GlassRenderPolicy.resolve(blurEnabled = blurEnabled, hasHazeState = hazeState != null)
    val useHaze = renderMode == GlassRenderMode.BackdropBlur

    Box(
        modifier =
            modifier
                .then(
                    if (useHaze) {
                        Modifier.hazeEffect(state = hazeState) {
                            style =
                                HazeStyle(
                                    backgroundColor = surfaceColor,
                                    // 无 tint：scrim 由下方 background(glassColor) 统一负责，
                                    // 保证模糊/降级两条路径视觉一致
                                    tints = emptyList(),
                                    blurRadius = AppBlur.blurRadius,
                                )
                        }
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
