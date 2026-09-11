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
     * @param topics 仓库 Topics（L06；空列表 → 简介区不渲染 chip 行）
     * @param canDeleteRepo 当前会话是否有 admin 权限（L04 删除仓库入口显隐；缺失 → 保守隐藏）
     * @param canPushRepo 当前会话是否有 push 权限（L05「新建 Release」/上传附件入口显隐）
     * @param pendingAssetUpload Release 附件上传进行中（L05 防重入）
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
        val topics: List<String> = emptyList(),
        val canDeleteRepo: Boolean = false,
        val canPushRepo: Boolean = false,
        val pendingAssetUpload: Boolean = false,
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
 * 仓库域加载错误类型（UI 层映射为本地化文案）。
 *
 * **错误域必须分开**（#201 P0）：仓库本体请求 404 与「仓库里某个路径 404」是两件事。
 * 实测缺陷：深链一个不存在的文件（`/contents/README.md → 404`）被复用 [NOT_FOUND]，
 * 界面把「文件不存在」说成「Repository not found / 仓库未找到」，而同一会话里
 * `GET /repos/{owner}/{repo}` 明明 200 —— 用户拿到的是**错误的原因**，且附带的
 * Retry 是死操作（确定性 404 重试必然复现）。见 [PATH_NOT_FOUND] / [isRetryable]。
 */
enum class RepoErrorType {
    /** 401/403：登录过期或 token 无该资源权限（GitHub 对无权限仓库谎报 404，勿混淆） */
    FORBIDDEN,

    /** 404：**仓库本体**不存在（`GET /repos/{owner}/{repo}` 404）。仅仓库级请求可映射到它 */
    NOT_FOUND,

    /** 404：**仓库存在，但请求的路径不存在**（contents/blob 404：文件已删除或改名）。 */
    PATH_NOT_FOUND,

    /** 网络/IO 错误 */
    NETWORK,

    /** 其他未知错误 */
    UNKNOWN,
    ;

    /**
     * 是否值得让用户重试（#201 要求 2：不给无效 Retry）。
     *
     * 404 类是**确定性失败**：仓库不存在（[NOT_FOUND]）重试还是不存在，路径已删除/改名
     * （[PATH_NOT_FOUND]）重试还是 404 —— 点了必然原样再失败（CI 帧 `editor.png` 的
     * 「Repository not found + Retry」正是这种死操作），故不给 Retry 按钮。
     * 其余保留重试入口：网络/超时（[NETWORK]）、未知（[UNKNOWN]，含 5xx）、
     * 以及 [FORBIDDEN]（403 也可能是 GitHub 限流，重试有意义，且文案已引导重新登录）。
     */
    val isRetryable: Boolean
        get() = this != NOT_FOUND && this != PATH_NOT_FOUND
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

    /** 删除仓库（L04） */
    DELETE,
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

    /** 详情加载成功（L05：含附件列表） */
    data class Loaded(
        val release: Release,
        val assets: List<ReleaseAsset> = emptyList(),
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

    /** 仓库删除成功（L04；UI 收到后返回上一页） */
    data object RepositoryDeleted : RepoEvent

    /** 仓库删除无权限（L04；403 非 admin） */
    data object RepositoryDeleteForbidden : RepoEvent

    /** 仓库删除失败（L04；网络/未知） */
    data object RepositoryDeleteFailed : RepoEvent

    /** Release 附件上传成功（L05） */
    data class AssetUploaded(
        val name: String,
    ) : RepoEvent

    /** Release 附件上传失败（L05；403/422/网络） */
    data object AssetUploadFailed : RepoEvent
}
