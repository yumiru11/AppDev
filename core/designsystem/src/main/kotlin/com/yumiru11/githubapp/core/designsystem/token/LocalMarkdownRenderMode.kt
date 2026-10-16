package com.yumiru11.githubapp.core.designsystem.token

import androidx.compose.runtime.staticCompositionLocalOf
import com.yumiru11.githubapp.core.datastore.model.MarkdownRenderMode

/**
 * Markdown 渲染通道策略（设置页「Markdown 渲染」下发，AppThemeHost 装配）。
 *
 * static：偏好快照，改变时整体重组；消费侧（README/正文宿主）据此在
 * 「原生渲染」与「WebView 回退」之间裁决。默认原生优先。
 */
val LocalMarkdownRenderMode = staticCompositionLocalOf { MarkdownRenderMode.NATIVE_PREFERRED }
