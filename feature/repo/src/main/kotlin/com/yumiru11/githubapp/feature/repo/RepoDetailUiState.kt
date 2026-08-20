package com.yumiru11.githubapp.feature.repo

import com.yumiru11.githubapp.core.data.model.Release
import com.yumiru11.githubapp.core.data.model.Repository
import com.yumiru11.githubapp.core.data.model.Tag
import com.yumiru11.githubapp.core.markdown.webview.RenderMode

/**
 * 仓库详情页 UI 状态。
 */
sealed interface RepoDetailUiState {
    /** 加载中 */
    data object Loading : RepoDetailUiState

    /**
     * 加载成功。
     *
     * @param isLoggedIn 登录态（游客只读：false 时隐藏 Star/Watch/Fork 按钮）
     * @param isStarred 是否已星标（登录态加载；乐观更新）
     * @param isWatching 是否 Watch 中（登录态加载；乐观更新）
     * @param pendingAction 进行中的写操作（防重入，按钮禁用）
     * @param releasesState Releases 列表状态（第三个 Tab 懒加载）
     * @param tagsState Tags 列表状态（第三个 Tab 懒加载）
     * @param expandedReleaseId 展开中的 Release 详情（null = 列表态）
     * @param releaseDetailState Release 详情状态
     * @param languages 语言 → 字节数（Linguist 数据，语言栏渲染源）
     */
    data class Success(
        val repo: Repository,
        val readmeState: ReadmeState,
        val isLoggedIn: Boolean = false,
        val isStarred: Boolean = false,
        val isWatching: Boolean = false,
        val pendingAction: RepoAction? = null,
        val releasesState: ReleasesState = ReleasesState.Idle,
        val tagsState: TagsState = TagsState.Idle,
        val expandedReleaseId: Long? = null,
        val releaseDetailState: ReleaseDetailState = ReleaseDetailState.Idle,
        val languages: Map<String, Long> = emptyMap(),
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

/**
 * 仓库写操作类型（pendingAction 防重入 + 按钮禁用）。
 */
enum class RepoAction {
    /** Star/Unstar */
    STAR,

    /** Watch/Unwatch */
    WATCH,

    /** Fork */
    FORK,
}

/**
 * Releases 列表状态（第三个 Tab 懒加载）。
 */
sealed interface ReleasesState {
    /** 未加载（Tab 未打开） */
    data object Idle : ReleasesState

    /** 加载中 */
    data object Loading : ReleasesState

    /** 加载成功 */
    data class Loaded(
        val releases: List<Release>,
    ) : ReleasesState

    /** 加载失败（错误类型驱动文案） */
    data class Error(
        val errorType: RepoErrorType,
    ) : ReleasesState
}

/**
 * Tags 列表状态（第三个 Tab 懒加载）。
 */
sealed interface TagsState {
    /** 未加载（Tab 未打开） */
    data object Idle : TagsState

    /** 加载中 */
    data object Loading : TagsState

    /** 加载成功 */
    data class Loaded(
        val tags: List<Tag>,
    ) : TagsState

    /** 加载失败（错误类型驱动文案） */
    data class Error(
        val errorType: RepoErrorType,
    ) : TagsState
}

/**
 * Release 详情状态（列表项点击展开，页内展开不进导航）。
 */
sealed interface ReleaseDetailState {
    /** 未展开 */
    data object Idle : ReleaseDetailState

    /** 详情加载中 */
    data object Loading : ReleaseDetailState

    /** 详情加载成功 */
    data class Loaded(
        val release: Release,
    ) : ReleaseDetailState

    /** 详情加载失败（错误类型驱动文案） */
    data class Error(
        val errorType: RepoErrorType,
    ) : ReleaseDetailState
}

/**
 * 仓库管理操作事件（UI 层 stringResource 映射文案，ViewModel 不产文案）。
 */
sealed interface RepoEvent {
    /** Fork 成功 */
    data object Forked : RepoEvent

    /** Fork 无权限（403） */
    data object ForkPermissionDenied : RepoEvent

    /** 已 Fork 过该仓库（422） */
    data object ForkAlreadyExists : RepoEvent

    /** Fork 其他失败 */
    data object ForkFailed : RepoEvent

    /** Star/Watch 切换失败（已回滚） */
    data object ToggleFailed : RepoEvent
}
