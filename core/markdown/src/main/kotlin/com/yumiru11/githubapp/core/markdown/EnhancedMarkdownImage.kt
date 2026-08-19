@file:Suppress("LongMethod") // 增强图片组件含尺寸分支（徽章 20dp/普通图全宽/预览 Dialog）——天然多分支，拆散反损可读性（T3 先例）

package com.yumiru11.githubapp.core.markdown

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.mikepenz.markdown.compose.LocalImageTransformer
import com.mikepenz.markdown.compose.components.MarkdownComponentModel
import com.mikepenz.markdown.compose.elements.MarkdownImage
import com.yumiru11.githubapp.core.markdown.native.resolveRawImageUrl

/**
 * B 增强版图片：圆角 + tonal 阴影 + 点击后纯黑全屏 fade-in 预览。
 *
 * 实际解码仍走 renderer 的 [com.mikepenz.markdown.model.ImageTransformer]
 * （默认可换为 Coil3 / 原型本地 asset transformer）。
 */
private val IMAGE_SRC_REGEX = Regex("""!\[[^\]]*]\(([^)\s]+)""")

/** 徽章类图片判定：shields.io/badge 服务或 .svg 后缀（SVG intrinsic 10x 必须固定 20dp）。 */
private fun isBadgeLike(src: String): Boolean =
    src.contains("shields.io") || src.contains("badge") || src.endsWith(".svg", ignoreCase = true)

@Composable
fun EnhancedMarkdownImage(
    model: MarkdownComponentModel,
    stretch: Boolean = true,
    baseRepoUrl: String? = null,
) {
    val transformer = LocalImageTransformer.current
    val src =
        remember(model.content, model.node) {
            val nodeText = model.content.substring(model.node.startOffset, model.node.endOffset)
            IMAGE_SRC_REGEX.find(nodeText)?.groupValues?.get(1)
        } ?: return
    // 相对路径图（docs/x.png）解析为 raw 域完整 URL（2026-08-17 真机：openchamber
    // chat_example.png 相对路径被当行内图渲染 → 特别小）
    val resolvedSrc =
        remember(src, baseRepoUrl) {
            if (src.startsWith("http")) src else baseRepoUrl?.let { resolveRawImageUrl(it, src) } ?: src
        }
    var preview by remember { mutableStateOf(false) }
    val shape = MaterialTheme.shapes.medium

    Box(
        modifier =
            (if (stretch) Modifier.fillMaxWidth() else Modifier)
                .padding(vertical = 4.dp)
                .shadow(8.dp, shape)
                .clip(shape)
                .clickable { preview = true },
    ) {
        if (resolvedSrc.startsWith("http")) {
            // 网络图直接走 Coil AsyncImage：mikepenz transformer 链（Coil3ImageTransformerImpl）
            // 在原型真机加载失败（2026-08-16 徽章验证），直接加载可绕开并暴露真实错误。
            // 复用外层容器（圆角 + tonal 阴影 + 点击预览），对齐 WebView markdown-you.css 的 img 观感。
            // stretch=false（徽章段落）：保持原始尺寸——用户反馈 fillMaxWidth 是「导弹」（2026-08-16）。
            if (stretch) {
                coil3.compose.AsyncImage(
                    model = src,
                    contentDescription = null,
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                // 徽章（SVG/徽章服务）：固定标准高度 20dp + Fit 等比
                // （Coil SvgDecoder intrinsic 是 SVG 声明尺寸 ~10 倍，撑破一切宽约束——2026-08-16）。
                if (isBadgeLike(resolvedSrc)) {
                    coil3.compose.AsyncImage(
                        model = resolvedSrc,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.height(20.dp),
                    )
                } else {
                    // 普通大图（截图/封面/架构图）：全宽等比显示——不能按徽章 20dp 压缩
                    // （2026-08-17 真机：openchamber 部分截图被徽章化「特别小」）
                    coil3.compose.AsyncImage(
                        model = resolvedSrc,
                        contentDescription = null,
                        contentScale = ContentScale.FillWidth,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        } else {
            MarkdownImage(content = model.content, node = model.node)
        }
    }

    if (preview) {
        // usePlatformDefaultWidth=false：默认 Dialog 窗口带左右系统内边距，全屏黑背景盖不满
        // （2026-08-17 真机：放大预览左右边缘露出下层内容）。
        Dialog(
            onDismissRequest = { preview = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            AnimatedVisibility(
                visible = true,
                enter = fadeIn(),
            ) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .background(Color.Black)
                            .clickable { preview = false },
                ) {
                    val imageData = transformer.transform(src)
                    if (imageData != null) {
                        Image(
                            painter = imageData.painter,
                            contentDescription = imageData.contentDescription,
                            modifier = Modifier.fillMaxSize().padding(16.dp),
                            contentScale = ContentScale.Fit,
                        )
                    }
                }
            }
        }
    }
}
