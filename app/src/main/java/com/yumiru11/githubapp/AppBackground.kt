package com.yumiru11.githubapp

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage

/**
 * 全局背景图层（#167 / UI04，ui-design §7.4 用户拍板）。
 *
 * 四条拍板项的落地方式：
 * 1. **默认无图，用户自选** → [imageUri] 为 null 时整层不存在（零开销，也不改变任何现有观感）
 * 2. **统一不透明度设置** → [opacity]，观感换算见 [BackgroundScrim]
 * 3. **浅色变白 / 深色变黑并自动压暗** → [BackgroundScrim.scrimAlpha] + 对应底色
 * 4. **背景固定不动** → 图铺在内容**之下**的独立 Box 上，不参与任何滚动容器，
 *    因此天然不随内容滚动（不需要 nestedScroll 之类的额外处理）
 *
 * 另外两处要点：
 * - **WebView 会透出背景图**：core:markdown 的 WebView 早已 setBackgroundColor(TRANSPARENT)，
 *   且 CSS 里 html/body 背景为 transparent，所以 README/Issue 正文天然透出这一层，
 *   无需改动渲染管线（§7.4 要求的「WebView 透明透出」其实已经具备）。
 * - **OLED / 高对比下整体禁用**（[enabled]）：纯黑面板与高对比文字是无障碍诉求，
 *   叠背景图会直接破坏它们 —— 与毛玻璃的 withAccessibilityOverrides 同一条原则。
 *
 * @param imageUri 背景图 URI（null = 不画背景）
 * @param opacity 用户设定的统一不透明度（0..1）
 * @param darkTheme 当前是否深色（决定底色与压暗系数）
 * @param enabled OLED / 高对比等场景下传 false 直接禁用
 */
@Composable
fun AppBackground(
    imageUri: String?,
    opacity: Float,
    darkTheme: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    if (!enabled || imageUri.isNullOrBlank()) {
        content()
        return
    }
    Box(modifier = modifier.fillMaxSize()) {
        AsyncImage(
            model = imageUri,
            contentDescription = null, // 纯装饰：不进无障碍树（TalkBack 念一张背景图毫无意义）
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        // 压白/压暗层。底色用纯白/纯黑而不是 surface：§7.4 的「变白/变黑」是字面意思，
        // 用 surface 会把「变白」变成「变灰」，深色下也压不到真正的黑。
        // 露出多少完全交给 alpha（BackgroundScrim 已按深浅色分别换算）。
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(
                        (if (darkTheme) Color.Black else Color.White)
                            .copy(alpha = BackgroundScrim.scrimAlpha(opacity, darkTheme)),
                    ),
        )
        content()
    }
}
