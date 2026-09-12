package com.yumiru11.githubapp.feature.repo

import com.yumiru11.githubapp.core.data.model.Repository
import com.yumiru11.githubapp.core.editor.FileFindState
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import java.time.Instant

/**
 * 仓库域截图测试共用夹具（确定性、离线自足）。
 *
 * ## 为什么 mock ViewModel
 *
 * 与 [RepoDetailInitialViewTest] 同款（该测试已入库）：屏幕的 viewModel/filesViewModel
 * 形参已为测试开放，截图只关心「给定状态 → 像素」。mock 掉 VM 的加载副作用（协程时序、
 * 草稿读取）才能保证离屏帧确定性。
 *
 * ## 为什么用「文件」分区而不是 README
 *
 * README 主通道是 WebView（服务端 HTML / 离线 GFM）——Robolectric 下 WebView **无法
 * 栅格化像素**（见 MarkdownGfmFixtures 分层说明 / research/screenshot-automation-alt.md），
 * 帧会留一块空白。README 的像素回归由 CI 模拟器截图（screenshots.sh）兜底；
 * 本基线固定在文件分区，覆盖仓库头/统计/管理按钮/分区 Tab/文件树这些离屏可渲染内容。
 *
 * ## 时间戳一律相对
 *
 * 夹具不写绝对时间（跨日历静默过期，见 #246）：[repoScreenshotUpdatedAt] 运行时生成。
 */
internal fun repoScreenshotUpdatedAt(): Instant = Instant.now().minusSeconds(2 * SECONDS_PER_DAY)

private const val SECONDS_PER_DAY = 86_400L

/** 仓库详情成功态：登录 + 已 Star + 语言/Topics 齐全（管理按钮、语言栏、topic 行都可见）。 */
internal fun repoDetailSuccessState(): RepoDetailUiState.Success =
    RepoDetailUiState.Success(
        repo =
            Repository(
                ownerLogin = "octocat",
                name = "Hello-World",
                description = "My first repository on GitHub! Material You Android client.",
                isPrivate = false,
                stargazerCount = 1_234,
                forkCount = 56,
                language = "Kotlin",
                defaultBranch = "main",
                updatedAt = repoScreenshotUpdatedAt(),
            ),
        // 文件分区初始帧：README 状态不参与渲染（见文件头说明）
        readmeState = ReadmeState.Empty,
        isLoggedIn = true,
        isStarred = true,
        isWatching = false,
        languages = mapOf("Kotlin" to 128_000L, "Shell" to 4_200L),
        topics = listOf("android", "compose", "material-you"),
        canPushRepo = true,
    )

/** 文件树根节点（文件分区初始帧内容）。 */
internal fun repoRootTreeNodes(): List<GitTreeNode> =
    listOf(
        GitTreeNode(name = "app", path = "app", sha = "sha-app", isDirectory = true),
        GitTreeNode(name = "core", path = "core", sha = "sha-core", isDirectory = true),
        GitTreeNode(name = "docs", path = "docs", sha = "sha-docs", isDirectory = true),
        GitTreeNode(name = "README.md", path = "README.md", sha = "sha-readme", isDirectory = false, size = 2_048L),
        GitTreeNode(name = "settings.gradle.kts", path = "settings.gradle.kts", sha = "sha-settings", isDirectory = false, size = 512L),
    )

/** 文件浏览子状态：树已加载、未选中任何文件（文件分区列表形态）。 */
internal fun repoFilesScreenshotState(): RepoFilesUiState =
    RepoFilesUiState(
        treeState = TreeState.Loaded(repoRootTreeNodes()),
        selectedPath = null,
        fileState = FileViewState.Idle,
        editState = FileEditState.Idle,
        currentRef = "main",
        isFindOpen = false,
        findState = FileFindState(),
    )

/** 仓库详情 ViewModel 桩：Success 固定状态 + 空事件流。 */
internal fun repoDetailScreenshotViewModel(): RepoDetailViewModel =
    mockk(relaxed = true) {
        every { uiState } returns MutableStateFlow(repoDetailSuccessState())
        every { events } returns emptyFlow()
    }

/** 文件浏览 ViewModel 桩：树已加载 + 空编辑事件流。 */
internal fun repoFilesScreenshotViewModel(): RepoFilesViewModel =
    mockk(relaxed = true) {
        every { uiState } returns MutableStateFlow(repoFilesScreenshotState())
        every { editEvents } returns emptyFlow()
    }

/** 文件查看器内容：未知扩展名（grammar = null 纯文本），规避 TextMate 语法资产加载路径（FileEdit 先例）。 */
internal fun fileViewerContentData(): FileContentData =
    FileContentData(
        fileName = "notes.txt",
        path = "docs/notes.txt",
        size = 1_024L,
        kind = FileKind.CODE,
        text =
            "AppDev screenshot expansion\n" +
                "\n" +
                "fun main() {\n" +
                "    println(\"hello, baseline\")\n" +
                "}\n",
        sha = "blob-notes",
    )
