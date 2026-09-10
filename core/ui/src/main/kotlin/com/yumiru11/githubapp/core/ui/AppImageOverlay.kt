package com.yumiru11.githubapp.core.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.yumiru11.githubapp.core.designsystem.token.AppDimens
import com.yumiru11.githubapp.core.designsystem.token.AppMotion
import com.yumiru11.githubapp.core.designsystem.token.AppOverlay

/**
 * 图片全屏查看器（#166 / UI11，ui-design §3.11 9g 用户拍板）。
 *
 * 契约：
 * - **纯黑背景**（沉浸优先，§6.1 第 5 项把查看器从毛玻璃允许清单里排除）
 * - **向上 fade in**（缩放从 0.94 起 + 透明度 0→1，Emphasized 曲线，时长走 [AppMotion] 缩放）
 * - 图片**圆角 + 阴影**（与全局形状/tonal elevation 契约一致）
 * - 点击任意处或右上角关闭；返回键由 [Dialog] 自带处理
 *
 * 消费方（RepoDetail / IssueDetail / PR Tab）此前 onImageClick 都是空实现桩 ——
 * README 里的图片点了没反应。现在统一走本组件。
 *
 * @param imageUrl null = 不展示（消费方直接用状态变量传入，无需额外可见性布尔）
 */
@Composable
fun AppImageOverlay(
    imageUrl: String?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (imageUrl == null) return

    val progress = remember { Animatable(0f) }
    val duration = AppMotion.scaledDuration(AppMotion.DURATION_TRANSIENT)
    LaunchedEffect(imageUrl, duration) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(durationMillis = duration, easing = AppMotion.EmphasizedDecelerate))
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties =
            DialogProperties(
                usePlatformDefaultWidth = false,
                // 看图是沉浸场景：系统栏内容也压暗，不与应用内容混色
                decorFitsSystemWindows = false,
            ),
    ) {
        Box(
            modifier =
                modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = progress.value }
                    .background(AppOverlay.imageViewerScrim)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss,
                    ),
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model = imageUrl,
                contentDescription = stringResource(R.string.image_viewer_cd),
                contentScale = ContentScale.Fit,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(AppDimens.contentPadding)
                        .graphicsLayer {
                            val scale = ENTER_SCALE_START + (1f - ENTER_SCALE_START) * progress.value
                            scaleX = scale
                            scaleY = scale
                        }.shadow(AppDimens.cornerSmall)
                        .clip(RoundedCornerShape(AppDimens.cornerLarge)),
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.TopEnd).padding(AppDimens.cornerSmall),
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = stringResource(R.string.image_viewer_close_cd),
                )
            }
        }
    }
}

/** 进入动效的起始缩放（§3.11「向上 fade in」的缩放分量） */
private const val ENTER_SCALE_START = 0.94f
