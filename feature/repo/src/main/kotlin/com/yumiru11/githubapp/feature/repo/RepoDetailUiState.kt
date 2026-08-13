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

    /** 加载失败（错误类型驱动文案，UI 层 stringResource 映射，ViewModel 不产英文） */
    data class Error(
        val errorType: RepoErrorType,
    ) : RepoDetailUiState
}

/**
 * README 子状态。
 */
sealed interface ReadmeState {
    data object Loading : ReadmeState

    data object Empty : ReadmeState

    /**
     * @param content 渲染内容：WEBVIEW 模式为服务端渲染 HTML，NATIVE 模式为原始 Markdown 文本
     * @param renderMode 渲染通道：WEBVIEW 走 T8 WebViewMarkdownRenderer，NATIVE 走 MarkdownViewer
     */
    data class Loaded(
        val content: String,
        val renderMode: ReadmeRenderMode = ReadmeRenderMode.WEBVIEW,
    ) : ReadmeState

    /** 加载失败（错误类型驱动文案，UI 层 stringResource 映射） */
    data class Error(
        val errorType: RepoErrorType,
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

/**
 * 仓库/README 加载错误类型（UI 层映射为本地化文案）。
 */
enum class RepoErrorType {
    /** 404：仓库或 README 不存在 */
    NOT_FOUND,

    /** 网络/IO 错误 */
    NETWORK,

    /** 其他未知错误 */
    UNKNOWN,
}
