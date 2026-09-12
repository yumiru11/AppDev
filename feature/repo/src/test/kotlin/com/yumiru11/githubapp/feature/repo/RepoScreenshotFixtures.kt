package com.yumiru11.githubapp.feature.repo

import androidx.lifecycle.SavedStateHandle
import androidx.paging.PagingData
import com.yumiru11.githubapp.core.data.model.Repository
import com.yumiru11.githubapp.core.datastore.model.RepoLayoutMode
import com.yumiru11.githubapp.core.editor.FileFindState
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
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

// ── 「仓库」分区列表页（#166 / UI01+UI02）─────────────────────────────────

/**
 * 「仓库」分区 ViewModel 桩：固定 4 条分页数据 + 布局/登录态可切。
 *
 * 用 mockk 而非真实 [ReposViewModel]：真实 VM 的分页源 `ViewerRepositoriesPagingSource`
 * 由 Pager 冷启动（触网），截图只关心「给定状态 → 像素」（同 [repoDetailScreenshotViewModel] 先例）。
 *
 * @param layoutMode 通栏 / 网格（UI01 的两种布局各有一帧）
 * @param anonymous 游客态（true → 登录引导空态）
 */
internal fun reposScreenshotViewModel(
    layoutMode: RepoLayoutMode = RepoLayoutMode.LIST,
    anonymous: Boolean = false,
): ReposViewModel =
    mockk(relaxed = true) {
        every { layout } returns MutableStateFlow(layoutMode)
        every { isAnonymous } returns MutableStateFlow(anonymous)
        every { repositories } returns flowOf(PagingData.from(reposScreenshotRepositories()))
        every { events } returns MutableSharedFlow()
    }

/** 仓库卡片夹具：私有徽章 / 无语言 / 无描述等形态都出现（卡片各分支可见）。 */
private fun reposScreenshotRepositories(): List<Repository> =
    listOf(
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
        Repository(
            ownerLogin = "yumiru11",
            name = "AppDev",
            description = "轻量、流畅、全 Material You 的 Android GitHub 客户端。",
            isPrivate = false,
            stargazerCount = 18,
            forkCount = 2,
            language = "Kotlin",
            defaultBranch = "main",
            updatedAt = repoScreenshotUpdatedAt(),
        ),
        Repository(
            ownerLogin = "octocat",
            name = "screenshot-lab",
            description = "私有实验仓：截图基线录制与像素回归演练。",
            isPrivate = true,
            stargazerCount = 3,
            forkCount = 0,
            language = "Shell",
            defaultBranch = "main",
            updatedAt = repoScreenshotUpdatedAt(),
        ),
        Repository(
            ownerLogin = "hubot",
            name = "dotfiles",
            description = null,
            isPrivate = false,
            stargazerCount = 7,
            forkCount = 1,
            language = null,
            defaultBranch = "main",
            updatedAt = repoScreenshotUpdatedAt(),
        ),
    )

// ── 表单页（新建仓库 / 新建 Release）────────────────────────────────────

/**
 * 新建仓库页 VM：真实 VM + relaxed [RepoAdminRepository]，字段填齐
 * （名称合法 / 描述 / 私有开 / README 初始化关）→ 提交按钮可用态。
 *
 * 用真实 VM：截图里的「给定状态」就是用户填完表单的状态，由 VM 方法驱动（等价真实交互），
 * submit 未被调用故不发任何请求。
 */
internal fun createRepoScreenshotViewModel(): CreateRepoViewModel =
    CreateRepoViewModel(mockk<RepoAdminRepository>(relaxed = true)).apply {
        onNameChange("screenshot-lab")
        onDescriptionChange("Screenshot baseline playground for Material You components.")
        onPrivateChange(true)
        onAutoInitChange(false)
    }

/** 新建 Release 页 VM：Tag/目标/标题/说明填齐、预发布开（草稿关）→ 提交按钮可用态。 */
internal fun releaseCreateScreenshotViewModel(): ReleaseCreateViewModel =
    ReleaseCreateViewModel(
        SavedStateHandle(mapOf("owner" to "octocat", "repo" to "Hello-World", "ref" to "main")),
        mockk<RepoManagementRepository>(relaxed = true),
    ).apply {
        onTagChange("v1.2.0")
        onTargetChange("main")
        onNameChange("v1.2.0 — screenshot baseline expansion")
        onBodyChange("- 六屏十五帧基线补齐\n- 基线由 CI canonical workflow 录制")
        onPrereleaseChange(true)
    }

// ── Commit 详情（文件列表 / 单文件 diff 两态）───────────────────────────

/** 提交完整 SHA（[CommitDetail.shortSha] 渲染前 7 位）。 */
internal const val COMMIT_SCREENSHOT_SHA = "a1b2c3d4e5f60718293a4b5c6d7e8f9012345678"

/**
 * Commit 详情页 VM：真实 VM + [CommitRepository] 桩，init 在 Unconfined main 上同步落 Success。
 *
 * @param withDiff true = 预选带 patch 的文件（页内 diff 态）；false = 文件列表态
 */
internal fun commitDetailScreenshotViewModel(withDiff: Boolean = false): CommitDetailViewModel {
    val files = commitFilesFixture()
    val repository =
        mockk<CommitRepository> {
            coEvery { getCommit("octocat", "Hello-World", any()) } returns
                Result.success(commitDetailFixture(files))
        }
    return CommitDetailViewModel(
        SavedStateHandle(mapOf("owner" to "octocat", "repo" to "Hello-World", "sha" to COMMIT_SCREENSHOT_SHA)),
        repository,
    ).apply {
        if (withDiff) selectFile(files.first { it.patch != null })
    }
}

/** 提交详情夹具（作者时间走相对值：渲染「N days ago」，跨日历稳定，#246）。 */
private fun commitDetailFixture(files: List<CommitFile>): CommitDetail =
    CommitDetail(
        sha = COMMIT_SCREENSHOT_SHA,
        message =
            "test(repo): 补齐 CommitDetail / Repos / 表单页截图基线\n\n" +
                "六屏十五帧，基线由 CI canonical workflow 录制。",
        authorName = "yumiru11",
        authorDate = repoScreenshotIsoDaysAgo(2),
        authorLogin = "yumiru11",
        authorAvatarUrl = "https://github.com/yumiru11.png",
        additions = 268,
        deletions = 24,
        files = files,
    )

/** 文件变更夹具：ADDED/MODIFIED 状态徽章 + 一条带 patch（供 diff 帧解析）。 */
private fun commitFilesFixture(): List<CommitFile> =
    listOf(
        CommitFile(
            filename = "feature/repo/src/test/kotlin/com/yumiru11/githubapp/feature/repo/ReposScreenScreenshotTest.kt",
            status = CommitFileStatus.ADDED,
            additions = 96,
            deletions = 0,
        ),
        CommitFile(
            filename = "feature/repo/src/test/kotlin/com/yumiru11/githubapp/feature/repo/CommitDetailScreenScreenshotTest.kt",
            status = CommitFileStatus.MODIFIED,
            additions = 88,
            deletions = 4,
            patch =
                "@@ -1,4 +1,6 @@\n" +
                    " package com.yumiru11.githubapp.feature.repo\n" +
                    " \n" +
                    "-// 旧：三个模块各自维护截图基线\n" +
                    "+// 新：六屏十五帧统一说明\n" +
                    "+// 基线由 CI canonical workflow 录制\n" +
                    " object ScreenshotIndex\n",
        ),
        CommitFile(
            filename = "docs/agents/project-status.md",
            status = CommitFileStatus.MODIFIED,
            additions = 6,
            deletions = 2,
        ),
    )

/** 相对时间（ISO-8601，N 天前）：截图夹具禁写绝对时间戳（#246 时间炸弹）。 */
internal fun repoScreenshotIsoDaysAgo(days: Long): String = Instant.now().minusSeconds(days * SECONDS_PER_DAY).toString()
