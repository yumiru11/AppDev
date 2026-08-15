package com.yumiru11.githubapp.core.markdown

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import com.mikepenz.markdown.compose.LocalImageTransformer
import com.mikepenz.markdown.compose.components.MarkdownComponentModel
import com.mikepenz.markdown.compose.elements.MarkdownImage

/**
 * B 增强版图片：圆角 + tonal 阴影 + 点击后纯黑全屏 fade-in 预览。
 *
 * 实际解码仍走 renderer 的 [com.mikepenz.markdown.model.ImageTransformer]
 * （默认可换为 Coil3 / 原型本地 asset transformer）。
 */
private val IMAGE_SRC_REGEX = Regex("""!\[[^\]]*]\(([^)\s]+)""")

@Composable
fun EnhancedMarkdownImage(model: MarkdownComponentModel) {
    val transformer = LocalImageTransformer.current
    val src =
        remember(model.content, model.node) {
            val nodeText = model.content.substring(model.node.startOffset, model.node.endOffset)
            IMAGE_SRC_REGEX.find(nodeText)?.groupValues?.get(1)
        } ?: return
    var preview by remember { mutableStateOf(false) }
    val shape = MaterialTheme.shapes.medium

    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .shadow(4.dp, shape)
                .clip(shape)
                .clickable { preview = true },
    ) {
        MarkdownImage(content = model.content, node = model.node)
    }

    if (preview) {
        Dialog(onDismissRequest = { preview = false }) {
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
