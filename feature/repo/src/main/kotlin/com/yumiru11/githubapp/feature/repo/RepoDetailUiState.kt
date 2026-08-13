package com.yumiru11.githubapp.feature.repo

import com.yumiru11.githubapp.core.data.model.Repository

/**
 * 仓库详情页 UI 状态。
 */
sealed interface RepoDetailUiState {
    /** 加载中 */
    data object Loading : RepoDetailUiState

    /** 加载成功 */
    data class Success(
        val repo: Repository,
        val readmeState: ReadmeState,
    ) : RepoDetailUiState

    /** 加载失败 */
    data class Error(
        val message: String,
    ) : RepoDetailUiState
}

/**
 * README 子状态。
 */
sealed interface ReadmeState {
    data object Loading : ReadmeState

    data object Empty : ReadmeState

    /**
     * @param html 服务端渲染 HTML（WEBVIEW 模式）或原始 Markdown 文本（NATIVE 模式）
     * @param renderMode 渲染通道：WEBVIEW 走 T8 WebViewMarkdownRenderer，NATIVE 走 MarkdownViewer
     */
    data class Loaded(
        val html: String,
        val renderMode: ReadmeRenderMode = ReadmeRenderMode.WEBVIEW,
    ) : ReadmeState

    data class Error(
        val message: String,
    ) : ReadmeState
}

/**
 * README 渲染通道选择。
 */
enum class ReadmeRenderMode {
    /** 服务端 HTML → WebView 兜底通道（FeatureDetector 判定复杂或内容为 HTML） */
    WEBVIEW,

    /** 原生 Markdown 文本 → MarkdownViewer（简单 Markdown） */
    NATIVE,
}
