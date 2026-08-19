package com.yumiru11.githubapp.feature.repo

import com.yumiru11.githubapp.core.data.model.Repository
import com.yumiru11.githubapp.core.markdown.webview.RenderMode

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
     * @param content 渲染内容：服务端 HTML 或离线 GFM 降级时的原始 Markdown
     * @param renderMode 渲染通道（Task B 后恒为 WebView）
     * @param webViewRenderMode WebView 子模式：服务端 HTML 或离线 markdown-it（降级时）
     */
    data class Loaded(
        val content: String,
        val renderMode: ReadmeRenderMode = ReadmeRenderMode.WEBVIEW,
        val webViewRenderMode: RenderMode = RenderMode.SERVER_HTML,
    ) : ReadmeState

    /** 加载失败（错误类型驱动文案，UI 层 stringResource 映射） */
    data class Error(
        val errorType: RepoErrorType,
    ) : ReadmeState
}

/**
 * README 渲染通道选择（Task B 后仅剩 WebView）。
 */
enum class ReadmeRenderMode {
    /** WebView 渲染通道（服务端 HTML 或离线 GFM 降级） */
    WEBVIEW,
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
