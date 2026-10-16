package com.yumiru11.githubapp.core.markdown.diagram

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.swithun.cmpmermaid.compose.MermaidDiagram
import com.swithun.cmpmermaid.core.GMResult
import com.swithun.cmpmermaid.core.MermaidTheme

/**
 * 原生 Mermaid 图表渲染（默认通道，不依赖 WebView）。
 *
 * 引擎 = cmp-mermaid（MIT，上游 swithun-liu/cmp-mermaid）：把 Mermaid 12 语义翻译成 Kotlin，
 * 用 Compose Canvas 绘制 — 解析、布局、场景图、绘制全部在本进程内完成，无 JS、无网络。
 *
 * 与 WebView 通道的关系：本组件是「原生优先」路径；引擎对**合法但不支持**的输入
 * （如 Architecture randomize、handDrawn 外观）会返回错误而不是画错图，此时回退到调用方
 * 传入的 [fallback]（通常 = 代码块 + WebView 岛由上层决定）。绝不静默降级成错误的图。
 *
 * 主题：跟随系统深浅色（[MermaidTheme.Dark] / [MermaidTheme.FlowchartDefault]）；
 * 图表语义描述由调用方经 stringResource 传入（i18n 铁律）。
 */
@Composable
fun AppMermaidDiagram(
    source: String,
    contentDescription: String,
    modifier: Modifier = Modifier,
    fallback: @Composable () -> Unit,
) {
    val darkTheme = isSystemInDarkTheme()
    var renderFailed by remember(source) { mutableStateOf(false) }
    if (renderFailed) {
        fallback()
        return
    }
    MermaidDiagram(
        source = source,
        modifier = modifier.fillMaxWidth(),
        theme = if (darkTheme) MermaidTheme.Dark else MermaidTheme.FlowchartDefault,
        contentDescription = contentDescription,
        onRenderResult = { result -> if (result is GMResult.Err) renderFailed = true },
    )
}
