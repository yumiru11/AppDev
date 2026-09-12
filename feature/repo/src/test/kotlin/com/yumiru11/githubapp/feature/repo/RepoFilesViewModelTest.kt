@file:Suppress("LargeClass")
// 1037 行：T11 树/目录/文件 + T22 编辑提交（提交/冲突三选项/删除/失败路径）+ #166 UI14
// 文件内查找状态层全流程单测聚一文件（单一被测类，拆分收益低于同文件聚合；
// 后续测试膨胀再拆 EditTest / FindTest 子类）

package com.yumiru11.githubapp.feature.repo

import androidx.lifecycle.SavedStateHandle
import com.yumiru11.githubapp.core.datastore.draft.DraftAutoSaver
import com.yumiru11.githubapp.core.datastore.draft.DraftKey
import com.yumiru11.githubapp.core.datastore.draft.DraftRepository
import com.yumiru11.githubapp.core.datastore.draft.DraftTargets
import com.yumiru11.githubapp.core.editor.FileFindState
import com.yumiru11.githubapp.core.testing.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

/**
 * RepoFilesViewModel 单测（纯 JVM，MockK 桩 RepoRepository）。
 *
 * 覆盖 4 态：根树 加载/成功/错误；目录 展开成功/展开失败/收起；
 * 文件 加载/成功/错误/重试/关闭；同 ref 免重复加载。
 */
class RepoFilesViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val savedStateHandle =
        SavedStateHandle(mapOf("owner" to "octocat", "repo" to "Hello-World"))

    private fun viewModel(
        repoRepository: RepoRepository,
        drafts: DraftAutoSaver = draftSaver(RecordingDraftRepository()),
    ): RepoFilesViewModel =
        RepoFilesViewModel(
            savedStateHandle = savedStateHandle,
            repoRepository = repoRepository,
            drafts = drafts,
        )

    private fun treeNode(
        name: String,
        path: String,
        isDirectory: Boolean = false,
        sha: String = "sha-$name",
        children: List<GitTreeNode>? = null,
        isExpanded: Boolean = false,
    ) = GitTreeNode(name = name, path = path, sha = sha, isDirectory = isDirectory, children = children, isExpanded = isExpanded)

    private fun httpException(code: Int): HttpException =
        HttpException(Response.error<Any>(code, "error".toResponseBody("text/plain".toMediaType())))

    @Test
    fun loadRootTree_success_emitsLoadedTree() =
        runTest {
            val repoRepository =
                mockk<RepoRepository> {
                    coEvery { getTree("octocat", "Hello-World", "main") } returns
                        Result.success(listOf(treeNode("README.md", "README.md"), treeNode("src", "src", isDirectory = true)))
                }
            val viewModel = viewModel(repoRepository)

            viewModel.loadRootTree("main")

            val treeState = viewModel.uiState.value.treeState
            assertTrue(treeState is TreeState.Loaded)
            assertEquals(2, (treeState as TreeState.Loaded).rootNodes.size)
        }

    @Test
    fun loadRootTree_networkError_emitsErrorNetwork() =
        runTest {
            val repoRepository =
                mockk<RepoRepository> {
                    coEvery { getTree(any(), any(), any()) } returns Result.failure(IOException("down"))
                }
            val viewModel = viewModel(repoRepository)

            viewModel.loadRootTree("main")

            assertEquals(TreeState.Error(RepoErrorType.NETWORK), viewModel.uiState.value.treeState)
        }

    @Test
    fun loadRootTree_notFound_emitsErrorPathNotFound() =
        runTest {
            val repoRepository =
                mockk<RepoRepository> {
                    coEvery { getTree(any(), any(), any()) } returns Result.failure(httpException(404))
                }
            val viewModel = viewModel(repoRepository)

            viewModel.loadRootTree("main")

            // #201：404 是「该 ref/路径不存在」，不是「仓库不存在」（仓库级 404 由 RepoDetailViewModel 负责）
            assertEquals(TreeState.Error(RepoErrorType.PATH_NOT_FOUND), viewModel.uiState.value.treeState)
        }

    @Test
    fun loadRootTree_forbidden_emitsErrorForbidden() =
        runTest {
            val repoRepository =
                mockk<RepoRepository> {
                    coEvery { getTree(any(), any(), any()) } returns Result.failure(httpException(403))
                }
            val viewModel = viewModel(repoRepository)

            viewModel.loadRootTree("main")

            assertEquals(TreeState.Error(RepoErrorType.FORBIDDEN), viewModel.uiState.value.treeState)
        }

    @Test
    fun loadRootTree_sameRefAlreadyLoaded_skipsReload() =
        runTest {
            val repoRepository =
                mockk<RepoRepository> {
                    coEvery { getTree(any(), any(), any()) } returns
                        Result.success(listOf(treeNode("a.txt", "a.txt")))
                }
            val viewModel = viewModel(repoRepository)

            viewModel.loadRootTree("main")
            viewModel.loadRootTree("main")

            coVerify(exactly = 1) { repoRepository.getTree(any(), any(), "main") }
        }

    @Test
    fun loadRootTree_afterError_sameRefReloads() =
        runTest {
            val repoRepository =
                mockk<RepoRepository> {
                    coEvery { getTree(any(), any(), any()) } returns Result.failure(IOException("down"))
                }
            val viewModel = viewModel(repoRepository)
            viewModel.loadRootTree("main")
            assertEquals(TreeState.Error(RepoErrorType.NETWORK), viewModel.uiState.value.treeState)

            coEvery { repoRepository.getTree(any(), any(), any()) } returns
                Result.success(listOf(treeNode("a.txt", "a.txt")))
            viewModel.loadRootTree("main")

            assertTrue(viewModel.uiState.value.treeState is TreeState.Loaded)
            coVerify(exactly = 2) { repoRepository.getTree(any(), any(), any()) }
        }

    @Test
    fun toggleDirectory_unloadedDirectory_fetchesAndExpands() =
        runTest {
            val repoRepository =
                mockk<RepoRepository> {
                    coEvery { getTree(any(), any(), any()) } returns
                        Result.success(listOf(treeNode("src", "src", isDirectory = true, sha = "dirsha")))
                    coEvery { getChildTree("octocat", "Hello-World", "dirsha", "src") } returns
                        Result.success(listOf(treeNode("Main.kt", "src/Main.kt")))
                }
            val viewModel = viewModel(repoRepository)
            viewModel.loadRootTree("main")

            val srcNode = (viewModel.uiState.value.treeState as TreeState.Loaded).rootNodes[0]
            viewModel.toggleDirectory(srcNode)

            val root = (viewModel.uiState.value.treeState as TreeState.Loaded).rootNodes[0]
            assertTrue(root.isExpanded)
            assertEquals(1, root.children!!.size)
            assertEquals("src/Main.kt", root.children!![0].path)
        }

    @Test
    fun toggleDirectory_fetchFailure_keepsCollapsed() =
        runTest {
            val repoRepository =
                mockk<RepoRepository> {
                    coEvery { getTree(any(), any(), any()) } returns
                        Result.success(listOf(treeNode("src", "src", isDirectory = true, sha = "dirsha")))
                    coEvery { getChildTree(any(), any(), any(), any()) } returns Result.failure(IOException("down"))
                }
            val viewModel = viewModel(repoRepository)
            viewModel.loadRootTree("main")

            val srcNode = (viewModel.uiState.value.treeState as TreeState.Loaded).rootNodes[0]
            viewModel.toggleDirectory(srcNode)

            val root = (viewModel.uiState.value.treeState as TreeState.Loaded).rootNodes[0]
            assertTrue(!root.isExpanded)
            assertNull(root.children)
        }

    @Test
    fun toggleDirectory_expandedDirectory_collapses() =
        runTest {
            val repoRepository =
                mockk<RepoRepository> {
                    coEvery { getTree(any(), any(), any()) } returns
                        Result.success(listOf(treeNode("src", "src", isDirectory = true, sha = "dirsha")))
                    coEvery { getChildTree(any(), any(), any(), any()) } returns Result.success(listOf(treeNode("Main.kt", "src/Main.kt")))
                }
            val viewModel = viewModel(repoRepository)
            viewModel.loadRootTree("main")
            val srcNode = (viewModel.uiState.value.treeState as TreeState.Loaded).rootNodes[0]
            viewModel.toggleDirectory(srcNode)

            val expanded = (viewModel.uiState.value.treeState as TreeState.Loaded).rootNodes[0]
            viewModel.toggleDirectory(expanded)

            val root = (viewModel.uiState.value.treeState as TreeState.Loaded).rootNodes[0]
            assertTrue(!root.isExpanded)
            // 收起保留子节点缓存（再次展开免网络）
            assertEquals(1, root.children!!.size)
            coVerify(exactly = 1) { repoRepository.getChildTree(any(), any(), any(), any()) }
        }

    @Test
    fun toggleDirectory_loadedChildren_expandsWithoutNetwork() =
        runTest {
            val repoRepository =
                mockk<RepoRepository> {
                    coEvery { getTree(any(), any(), any()) } returns
                        Result.success(
                            listOf(
                                treeNode(
                                    "src",
                                    "src",
                                    isDirectory = true,
                                    sha = "dirsha",
                                    children = listOf(treeNode("a.kt", "src/a.kt")),
                                ),
                            ),
                        )
                }
            val viewModel = viewModel(repoRepository)
            viewModel.loadRootTree("main")

            val srcNode = (viewModel.uiState.value.treeState as TreeState.Loaded).rootNodes[0]
            viewModel.toggleDirectory(srcNode)

            assertTrue((viewModel.uiState.value.treeState as TreeState.Loaded).rootNodes[0].isExpanded)
            coVerify(exactly = 0) { repoRepository.getChildTree(any(), any(), any(), any()) }
        }

    // ---- TREE 深链：expandTreePath（初始视图自动展开到目标目录） ----

    @Test
    fun expandTreePath_nestedPath_expandsEachLevelSequentially() =
        runTest {
            val repoRepository =
                mockk<RepoRepository> {
                    coEvery { getTree(any(), any(), any()) } returns
                        Result.success(listOf(treeNode("src", "src", isDirectory = true, sha = "srcsha")))
                    coEvery { getChildTree("octocat", "Hello-World", "srcsha", "src") } returns
                        Result.success(listOf(treeNode("main", "src/main", isDirectory = true, sha = "mainsha")))
                    coEvery { getChildTree("octocat", "Hello-World", "mainsha", "src/main") } returns
                        Result.success(listOf(treeNode("Main.kt", "src/main/Main.kt")))
                }
            val viewModel = viewModel(repoRepository)
            viewModel.loadRootTree("main")

            viewModel.expandTreePath("src/main")

            val src = (viewModel.uiState.value.treeState as TreeState.Loaded).rootNodes[0]
            assertTrue("第一级目录应展开", src.isExpanded)
            val mainDir = src.children!![0]
            assertTrue("第二级目录应展开", mainDir.isExpanded)
            assertEquals("src/main/Main.kt", mainDir.children!![0].path)
            // 逐级按需拉取：两级各一次，不多不少
            coVerify(exactly = 1) { repoRepository.getChildTree("octocat", "Hello-World", "srcsha", "src") }
            coVerify(exactly = 1) { repoRepository.getChildTree("octocat", "Hello-World", "mainsha", "src/main") }
        }

    @Test
    fun expandTreePath_calledTwice_doesNotReloadExpandedLevels() =
        runTest {
            val repoRepository =
                mockk<RepoRepository> {
                    coEvery { getTree(any(), any(), any()) } returns
                        Result.success(listOf(treeNode("src", "src", isDirectory = true, sha = "srcsha")))
                    coEvery { getChildTree(any(), any(), any(), any()) } returns
                        Result.success(listOf(treeNode("Main.kt", "src/Main.kt")))
                }
            val viewModel = viewModel(repoRepository)
            viewModel.loadRootTree("main")

            viewModel.expandTreePath("src")
            viewModel.expandTreePath("src")

            // 幂等：第二次调用命中 isExpanded 分支，不再请求子树
            coVerify(exactly = 1) { repoRepository.getChildTree(any(), any(), any(), any()) }
        }

    @Test
    fun expandTreePath_unknownPath_stopsWithoutAnyFetch() =
        runTest {
            val repoRepository =
                mockk<RepoRepository> {
                    coEvery { getTree(any(), any(), any()) } returns
                        Result.success(listOf(treeNode("src", "src", isDirectory = true, sha = "srcsha")))
                }
            val viewModel = viewModel(repoRepository)
            viewModel.loadRootTree("main")

            viewModel.expandTreePath("docs")

            // 路径不存在（已删除/改名）：静默停在根树，不弹错
            val src = (viewModel.uiState.value.treeState as TreeState.Loaded).rootNodes[0]
            assertTrue(!src.isExpanded)
            coVerify(exactly = 0) { repoRepository.getChildTree(any(), any(), any(), any()) }
        }

    @Test
    fun expandTreePath_terminalIsFile_expandsParentOnly() =
        runTest {
            val repoRepository =
                mockk<RepoRepository> {
                    coEvery { getTree(any(), any(), any()) } returns
                        Result.success(listOf(treeNode("src", "src", isDirectory = true, sha = "srcsha")))
                    coEvery { getChildTree(any(), any(), any(), any()) } returns
                        Result.success(listOf(treeNode("Main.kt", "src/Main.kt")))
                }
            val viewModel = viewModel(repoRepository)
            viewModel.loadRootTree("main")

            // /tree/ 语义是目录；万一路径末段是文件，展开到父目录就停（不把文件当树拉）
            viewModel.expandTreePath("src/Main.kt")

            coVerify(exactly = 1) { repoRepository.getChildTree(any(), any(), any(), any()) }
            val src = (viewModel.uiState.value.treeState as TreeState.Loaded).rootNodes[0]
            assertTrue(src.isExpanded)
        }

    @Test
    fun expandTreePath_childFetchFailure_stopsWithTreeIntact() =
        runTest {
            val repoRepository =
                mockk<RepoRepository> {
                    coEvery { getTree(any(), any(), any()) } returns
                        Result.success(listOf(treeNode("src", "src", isDirectory = true, sha = "srcsha")))
                    coEvery { getChildTree(any(), any(), any(), any()) } returns Result.failure(IOException("down"))
                }
            val viewModel = viewModel(repoRepository)
            viewModel.loadRootTree("main")

            viewModel.expandTreePath("src/main")

            // 子树加载失败：保持收起（不抛异常、不破坏已加载的根树）
            val src = (viewModel.uiState.value.treeState as TreeState.Loaded).rootNodes[0]
            assertTrue(!src.isExpanded)
            assertNull(src.children)
        }

    @Test
    fun expandTreePath_blankPath_isNoOp() =
        runTest {
            val repoRepository =
                mockk<RepoRepository> {
                    coEvery { getTree(any(), any(), any()) } returns
                        Result.success(listOf(treeNode("src", "src", isDirectory = true, sha = "srcsha")))
                }
            val viewModel = viewModel(repoRepository)
            viewModel.loadRootTree("main")

            viewModel.expandTreePath("  ".trim())
            viewModel.expandTreePath("/")

            coVerify(exactly = 0) { repoRepository.getChildTree(any(), any(), any(), any()) }
        }

    @Test
    fun expandTreePath_beforeRootLoaded_isNoOp() =
        runTest {
            val repoRepository = mockk<RepoRepository>()
            val viewModel = viewModel(repoRepository)

            viewModel.expandTreePath("src")

            // 根树未加载：静默等待（UI 侧以 tree Loaded 为触发键，此处保证不崩）
            assertTrue(viewModel.uiState.value.treeState is TreeState.Loading)
            coVerify(exactly = 0) { repoRepository.getChildTree(any(), any(), any(), any()) }
        }

    @Test
    fun openFile_success_setsSelectedAndLoaded() =
        runTest {
            val repoRepository =
                mockk<RepoRepository> {
                    coEvery { getTree(any(), any(), any()) } returns Result.success(listOf(treeNode("Main.kt", "Main.kt")))
                    coEvery { getFileContent("octocat", "Hello-World", "Main.kt", "main") } returns
                        Result.success(FileContentData("Main.kt", "Main.kt", 4L, FileKind.CODE, "code"))
                }
            val viewModel = viewModel(repoRepository)
            viewModel.loadRootTree("main")

            val fileNode = (viewModel.uiState.value.treeState as TreeState.Loaded).rootNodes[0]
            viewModel.openFile(fileNode, "main")

            val state = viewModel.uiState.value
            assertEquals("Main.kt", state.selectedPath)
            assertEquals(FileViewState.Loaded(FileContentData("Main.kt", "Main.kt", 4L, FileKind.CODE, "code")), state.fileState)
        }

    @Test
    fun openFile_error_emitsFileError() =
        runTest {
            val repoRepository =
                mockk<RepoRepository> {
                    coEvery { getTree(any(), any(), any()) } returns Result.success(listOf(treeNode("Main.kt", "Main.kt")))
                    coEvery { getFileContent(any(), any(), any(), any()) } returns Result.failure(httpException(404))
                }
            val viewModel = viewModel(repoRepository)
            viewModel.loadRootTree("main")

            val fileNode = (viewModel.uiState.value.treeState as TreeState.Loaded).rootNodes[0]
            viewModel.openFile(fileNode, "main")

            val state = viewModel.uiState.value
            assertEquals("Main.kt", state.selectedPath)
            // #201：contents 404 = 文件已删除/改名，文案与「仓库未找到」不是一回事
            assertEquals(FileViewState.Error(RepoErrorType.PATH_NOT_FOUND), state.fileState)
        }

    @Test
    fun openDeepLinkFile_missingPath_emitsErrorPathNotFound() =
        runTest {
            // CI 实证场景：深链 blob/main/README.md（该文件后来不存在）→ contents 404
            val repoRepository =
                mockk<RepoRepository> {
                    coEvery { getFileContent(any(), any(), any(), any()) } returns Result.failure(httpException(404))
                }
            val viewModel = viewModel(repoRepository)

            viewModel.openDeepLinkFile("README.md")

            assertEquals(FileViewState.Error(RepoErrorType.PATH_NOT_FOUND), viewModel.uiState.value.fileState)
        }

    @Test
    fun retryLoadFile_afterError_reloadsSamePath() =
        runTest {
            val repoRepository =
                mockk<RepoRepository> {
                    coEvery { getTree(any(), any(), any()) } returns Result.success(listOf(treeNode("Main.kt", "Main.kt")))
                    coEvery { getFileContent(any(), any(), any(), any()) } returns Result.failure(IOException("down"))
                }
            val viewModel = viewModel(repoRepository)
            viewModel.loadRootTree("main")
            val fileNode = (viewModel.uiState.value.treeState as TreeState.Loaded).rootNodes[0]
            viewModel.openFile(fileNode, "main")
            assertEquals(FileViewState.Error(RepoErrorType.NETWORK), viewModel.uiState.value.fileState)

            coEvery { repoRepository.getFileContent(any(), any(), any(), any()) } returns
                Result.success(FileContentData("Main.kt", "Main.kt", 4L, FileKind.CODE, "code"))
            viewModel.retryLoadFile("main")

            assertEquals(
                FileViewState.Loaded(FileContentData("Main.kt", "Main.kt", 4L, FileKind.CODE, "code")),
                viewModel.uiState.value.fileState,
            )
            coVerify(exactly = 2) { repoRepository.getFileContent(any(), any(), "Main.kt", any()) }
        }

    @Test
    fun closeFile_clearsSelectionAndFileState() =
        runTest {
            val repoRepository =
                mockk<RepoRepository> {
                    coEvery { getTree(any(), any(), any()) } returns Result.success(listOf(treeNode("Main.kt", "Main.kt")))
                    coEvery { getFileContent(any(), any(), any(), any()) } returns
                        Result.success(FileContentData("Main.kt", "Main.kt", 4L, FileKind.CODE, "code"))
                }
            val viewModel = viewModel(repoRepository)
            viewModel.loadRootTree("main")
            val fileNode = (viewModel.uiState.value.treeState as TreeState.Loaded).rootNodes[0]
            viewModel.openFile(fileNode, "main")

            viewModel.closeFile()

            val state = viewModel.uiState.value
            assertNull(state.selectedPath)
            assertEquals(FileViewState.Idle, state.fileState)
        }

    @Test
    fun toggleDirectory_fileNode_isIgnored() =
        runTest {
            val repoRepository =
                mockk<RepoRepository> {
                    coEvery { getTree(any(), any(), any()) } returns Result.success(listOf(treeNode("a.txt", "a.txt")))
                }
            val viewModel = viewModel(repoRepository)
            viewModel.loadRootTree("main")

            val fileNode = (viewModel.uiState.value.treeState as TreeState.Loaded).rootNodes[0]
            viewModel.toggleDirectory(fileNode)

            assertTrue(!(viewModel.uiState.value.treeState as TreeState.Loaded).rootNodes[0].isExpanded)
            coVerify(exactly = 0) { repoRepository.getChildTree(any(), any(), any(), any()) }
        }

    // ── T22 文件编辑提交（Contents API + 409 冲突） ─────────────────────────────

    /** 打开文件并进入编辑态（默认 CODE 文本，含 sha；返回就绪的 VM）。 */
    private fun editingSetup(
        repoRepository: RepoRepository,
        text: String = "code",
        sha: String? = "blob-old",
        kind: FileKind = FileKind.CODE,
        drafts: DraftAutoSaver = draftSaver(RecordingDraftRepository()),
    ): RepoFilesViewModel {
        coEvery { repoRepository.getTree(any(), any(), any()) } returns
            Result.success(listOf(treeNode("Main.kt", "Main.kt")))
        coEvery { repoRepository.getFileContent(any(), any(), any(), any()) } returns
            Result.success(FileContentData("Main.kt", "Main.kt", 4L, kind, text, sha))
        val vm = viewModel(repoRepository, drafts)
        vm.loadRootTree("main")
        vm.openFile(treeNode("Main.kt", "Main.kt"), "main")
        vm.startEdit()
        return vm
    }

    @Test
    fun startEdit_fromLoadedCodeFile_entersEditingWithTextAndSha() =
        runTest {
            val vm = editingSetup(mockk<RepoRepository>(relaxed = true), text = "code", sha = "blob-old")

            val editState = vm.uiState.value.editState
            assertEquals(
                FileEditState.Editing(isNew = false, text = "code", sha = "blob-old", isMarkdown = false),
                editState,
            )
        }

    @Test
    fun startEdit_binaryFile_keepsIdle() =
        runTest {
            val vm = editingSetup(mockk<RepoRepository>(relaxed = true), kind = FileKind.BINARY)

            assertEquals(FileEditState.Idle, vm.uiState.value.editState)
        }

    @Test
    fun startNewFile_entersEditingIsNew() =
        runTest {
            val vm = viewModel(mockk<RepoRepository>(relaxed = true))

            vm.startNewFile()

            assertEquals(FileEditState.Editing(isNew = true, text = "", sha = null, isMarkdown = false), vm.uiState.value.editState)
        }

    @Test
    fun onEditorTextChanged_updatesEditingText() =
        runTest {
            val vm = editingSetup(mockk<RepoRepository>(relaxed = true))

            vm.onEditorTextChanged("new text")

            assertEquals("new text", (vm.uiState.value.editState as FileEditState.Editing).text)
        }

    @Test
    fun commitEdit_success_emitsCommittedAndRefreshesTree() =
        runTest {
            val repoRepository =
                mockk<RepoRepository> {
                    coEvery { getTree(any(), any(), any()) } returns Result.success(listOf(treeNode("Main.kt", "Main.kt")))
                    coEvery { getFileContent(any(), any(), any(), any()) } returns
                        Result.success(FileContentData("Main.kt", "Main.kt", 4L, FileKind.CODE, "code", "blob-old"))
                    coEvery { updateFileContent("octocat", "Hello-World", "Main.kt", "code", "blob-old", "fix", "main") } returns
                        Result.success(FileCommitResult.Success("commit-1", "blob-new"))
                }
            val vm = editingSetup(repoRepository)
            val events = mutableListOf<FileEditEvent>()
            val job = launch(UnconfinedTestDispatcher()) { vm.editEvents.collect { events.add(it) } }

            vm.commitEdit(message = "fix", newBranchName = null, newFilePath = null)

            assertEquals(FileEditEvent.Committed("Main.kt", "main", false), events.single())
            val state = vm.uiState.value
            assertTrue(state.editState is FileEditState.Idle)
            assertNull(state.selectedPath)
            // AC4 缓存失效：提交成功后重载目标分支树（初次 + 刷新 = 2 次）
            coVerify(exactly = 2) { repoRepository.getTree(any(), any(), "main") }
            job.cancel()
        }

    @Test
    fun commitEdit_conflict_entersConflictState() =
        runTest {
            val repoRepository =
                mockk<RepoRepository>(relaxed = true) {
                    coEvery { getTree(any(), any(), any()) } returns Result.success(listOf(treeNode("Main.kt", "Main.kt")))
                    coEvery { getFileContent(any(), any(), any(), any()) } returns
                        Result.success(FileContentData("Main.kt", "Main.kt", 4L, FileKind.CODE, "code", "blob-old"))
                    coEvery { updateFileContent(any(), any(), any(), any(), any(), any(), any()) } returns
                        Result.success(FileCommitResult.Conflict("latest99"))
                }
            val vm = editingSetup(repoRepository)

            vm.commitEdit(message = "fix", newBranchName = null, newFilePath = null)

            assertEquals(
                FileEditState.Conflict(
                    operation = ConflictOperation.UPDATE,
                    latestSha = "latest99",
                    localText = "code",
                    message = "fix",
                    branch = "main",
                    isMarkdown = false,
                ),
                vm.uiState.value.editState,
            )
        }

    @Test
    fun commitEdit_newFile_sendsPutWithoutShaOnEnteredPath() =
        runTest {
            val repoRepository =
                mockk<RepoRepository> {
                    coEvery { getTree(any(), any(), any()) } returns Result.success(listOf(treeNode("Main.kt", "Main.kt")))
                    coEvery { updateFileContent(any(), any(), any(), any(), any(), any(), any()) } returns
                        Result.success(FileCommitResult.Success("c1", "b1"))
                }
            val vm = viewModel(repoRepository)
            vm.loadRootTree("main")
            vm.startNewFile()
            vm.onEditorTextChanged("hello")

            vm.commitEdit(message = "add new", newBranchName = null, newFilePath = "docs/new.md")

            coVerify {
                repoRepository.updateFileContent("octocat", "Hello-World", "docs/new.md", "hello", null, "add new", "main")
            }
        }

    @Test
    fun commitEdit_newBranch_sendsBranchNameAndRefreshesNewBranchTree() =
        runTest {
            val repoRepository =
                mockk<RepoRepository> {
                    coEvery { getTree(any(), any(), any()) } returns Result.success(listOf(treeNode("Main.kt", "Main.kt")))
                    coEvery { getFileContent(any(), any(), any(), any()) } returns
                        Result.success(FileContentData("Main.kt", "Main.kt", 4L, FileKind.CODE, "code", "blob-old"))
                    // 新建分支：先经 Git Refs API 建引用（Contents API 对不存在的 ref 返回 404）
                    coEvery { createBranch(any(), any(), any(), any()) } returns Result.success(Unit)
                    coEvery { updateFileContent(any(), any(), any(), any(), any(), any(), any()) } returns
                        Result.success(FileCommitResult.Success("c1", "b1"))
                }
            val vm = editingSetup(repoRepository)
            val events = mutableListOf<FileEditEvent>()
            val job = launch(UnconfinedTestDispatcher()) { vm.editEvents.collect { events.add(it) } }

            vm.commitEdit(message = "fix", newBranchName = "feat-x", newFilePath = null)

            // 先建分支引用（基分支=当前查看分支），再提交文件
            coVerify(exactly = 1) { repoRepository.createBranch("octocat", "Hello-World", "feat-x", any()) }
            coVerify {
                repoRepository.updateFileContent("octocat", "Hello-World", "Main.kt", "code", "blob-old", "fix", "feat-x")
            }
            assertEquals(FileEditEvent.Committed("Main.kt", "feat-x", true), events.single())
            // 新分支提交后：树切到新分支重新加载
            coVerify(exactly = 1) { repoRepository.getTree(any(), any(), "feat-x") }
            job.cancel()
        }

    @Test
    fun commitEdit_failure_returnsToEditingAndEmitsFailed() =
        runTest {
            val repoRepository =
                mockk<RepoRepository>(relaxed = true) {
                    coEvery { getTree(any(), any(), any()) } returns Result.success(listOf(treeNode("Main.kt", "Main.kt")))
                    coEvery { getFileContent(any(), any(), any(), any()) } returns
                        Result.success(FileContentData("Main.kt", "Main.kt", 4L, FileKind.CODE, "code", "blob-old"))
                    coEvery { updateFileContent(any(), any(), any(), any(), any(), any(), any()) } returns
                        Result.failure(IOException("down"))
                }
            val vm = editingSetup(repoRepository)
            val events = mutableListOf<FileEditEvent>()
            val job = launch(UnconfinedTestDispatcher()) { vm.editEvents.collect { events.add(it) } }

            vm.commitEdit(message = "fix", newBranchName = null, newFilePath = null)

            // 失败回编辑态（文本保留），错误事件上抛（UI Snackbar）
            assertEquals(
                FileEditState.Editing(isNew = false, text = "code", sha = "blob-old", isMarkdown = false),
                vm.uiState.value.editState,
            )
            assertEquals(FileEditEvent.Failed(RepoErrorType.NETWORK), events.single())
            job.cancel()
        }

    @Test
    fun reloadAfterConflict_refetchesLatestIntoEditor() =
        runTest {
            val repoRepository =
                mockk<RepoRepository> {
                    coEvery { getTree(any(), any(), any()) } returns Result.success(listOf(treeNode("Main.kt", "Main.kt")))
                    // 单一 getFileContent 桩（openFile 与重载共用）：远端最新内容。
                    // 不用双桩/answers 计数——MockK 多桩匹配顺序易混淆，单桩天然确定。
                    coEvery { getFileContent(any(), any(), any(), any()) } returns
                        Result.success(FileContentData("Main.kt", "Main.kt", 9L, FileKind.CODE, "remote-new", "blob-latest"))
                    coEvery { updateFileContent(any(), any(), any(), any(), any(), any(), any()) } returns
                        Result.success(FileCommitResult.Conflict("latest99"))
                }
            val vm = viewModel(repoRepository)
            vm.loadRootTree("main")
            vm.openFile(treeNode("Main.kt", "Main.kt"), "main")
            vm.startEdit()
            // 本地编辑 → 提交 → 409 冲突（重载会丢弃本地文本换成远端最新）
            vm.onEditorTextChanged("local-edit")
            vm.commitEdit(message = "fix", newBranchName = null, newFilePath = null)
            val conflictState = vm.uiState.value.editState
            assertEquals(ConflictOperation.UPDATE, (conflictState as FileEditState.Conflict).operation)
            assertEquals("latest99", conflictState.latestSha)
            coVerify(exactly = 1) { repoRepository.getFileContent(any(), any(), any(), any()) }

            vm.reloadAfterConflict()

            // 重载成功：编辑器文本与 sha 换成远端最新（本地文本被丢弃是预期行为）
            assertEquals(
                FileEditState.Editing(isNew = false, text = "remote-new", sha = "blob-latest", isMarkdown = false),
                vm.uiState.value.editState,
            )
            coVerify(exactly = 2) { repoRepository.getFileContent(any(), any(), any(), any()) }
        }

    @Test
    fun overwriteAfterConflict_retriesPutWithLatestSha() =
        runTest {
            val repoRepository =
                mockk<RepoRepository> {
                    coEvery { getTree(any(), any(), any()) } returns Result.success(listOf(treeNode("Main.kt", "Main.kt")))
                    coEvery { getFileContent(any(), any(), any(), any()) } returns
                        Result.success(FileContentData("Main.kt", "Main.kt", 4L, FileKind.CODE, "code", "blob-old"))
                    coEvery { updateFileContent(any(), any(), any(), any(), any(), any(), any()) } returns
                        Result.success(FileCommitResult.Conflict("latest99"))
                    // 覆盖：用冲突携带的最新 sha 重交本地文本
                    coEvery { updateFileContent("octocat", "Hello-World", "Main.kt", "code", "latest99", "fix", "main") } returns
                        Result.success(FileCommitResult.Success("c2", "b2"))
                }
            val vm = editingSetup(repoRepository)
            val events = mutableListOf<FileEditEvent>()
            val job = launch(UnconfinedTestDispatcher()) { vm.editEvents.collect { events.add(it) } }
            vm.commitEdit(message = "fix", newBranchName = null, newFilePath = null)

            vm.overwriteAfterConflict()

            assertEquals(FileEditEvent.Committed("Main.kt", "main", false), events.last())
            assertTrue(vm.uiState.value.editState is FileEditState.Idle)
            coVerify {
                repoRepository.updateFileContent("octocat", "Hello-World", "Main.kt", "code", "latest99", "fix", "main")
            }
            job.cancel()
        }

    @Test
    fun keepLocalAfterConflict_emitsKeepLocalAndClosesEditor() =
        runTest {
            val repoRepository =
                mockk<RepoRepository>(relaxed = true) {
                    coEvery { getTree(any(), any(), any()) } returns Result.success(listOf(treeNode("Main.kt", "Main.kt")))
                    coEvery { getFileContent(any(), any(), any(), any()) } returns
                        Result.success(FileContentData("Main.kt", "Main.kt", 4L, FileKind.CODE, "code", "blob-old"))
                    coEvery { updateFileContent(any(), any(), any(), any(), any(), any(), any()) } returns
                        Result.success(FileCommitResult.Conflict("latest99"))
                }
            val vm = editingSetup(repoRepository)
            val events = mutableListOf<FileEditEvent>()
            val job = launch(UnconfinedTestDispatcher()) { vm.editEvents.collect { events.add(it) } }
            vm.commitEdit(message = "fix", newBranchName = null, newFilePath = null)

            vm.keepLocalAfterConflict()

            // 本地文本上抛（UI 复制剪贴板），远端未被覆盖，编辑器关闭
            assertEquals(FileEditEvent.KeepLocal("code"), events.single())
            assertTrue(vm.uiState.value.editState is FileEditState.Idle)
            job.cancel()
        }

    @Test
    fun deleteFile_success_emitsDeletedAndRefreshesTree() =
        runTest {
            val repoRepository =
                mockk<RepoRepository> {
                    coEvery { getTree(any(), any(), any()) } returns Result.success(listOf(treeNode("Main.kt", "Main.kt")))
                    coEvery { getFileContent(any(), any(), any(), any()) } returns
                        Result.success(FileContentData("Main.kt", "Main.kt", 4L, FileKind.CODE, "code", "blob-old"))
                    coEvery { deleteFile("octocat", "Hello-World", "Main.kt", "blob-old", "remove", "main") } returns
                        Result.success(FileCommitResult.Success("del-c", null))
                }
            val vm = editingSetup(repoRepository)
            val events = mutableListOf<FileEditEvent>()
            val job = launch(UnconfinedTestDispatcher()) { vm.editEvents.collect { events.add(it) } }

            vm.deleteFile(message = "remove")

            assertEquals(FileEditEvent.Deleted("Main.kt"), events.single())
            assertTrue(vm.uiState.value.editState is FileEditState.Idle)
            assertNull(vm.uiState.value.selectedPath)
            coVerify(exactly = 2) { repoRepository.getTree(any(), any(), "main") }
            job.cancel()
        }

    @Test
    fun deleteFile_conflict_entersDeleteConflict() =
        runTest {
            val repoRepository =
                mockk<RepoRepository>(relaxed = true) {
                    coEvery { getTree(any(), any(), any()) } returns Result.success(listOf(treeNode("Main.kt", "Main.kt")))
                    coEvery { getFileContent(any(), any(), any(), any()) } returns
                        Result.success(FileContentData("Main.kt", "Main.kt", 4L, FileKind.CODE, "code", "blob-old"))
                    coEvery { deleteFile(any(), any(), any(), any(), any(), any()) } returns
                        Result.success(FileCommitResult.Conflict("latest99"))
                }
            val vm = editingSetup(repoRepository)

            vm.deleteFile(message = "remove")

            assertEquals(
                FileEditState.Conflict(
                    operation = ConflictOperation.DELETE,
                    latestSha = "latest99",
                    localText = "code",
                    message = "remove",
                    branch = "main",
                    isMarkdown = false,
                ),
                vm.uiState.value.editState,
            )
        }

    @Test
    fun dismissEdit_clearsEditState() =
        runTest {
            val vm = editingSetup(mockk<RepoRepository>(relaxed = true))

            vm.dismissEdit()

            assertTrue(vm.uiState.value.editState is FileEditState.Idle)
            // 查看器保持打开（selectedPath 不被清）
            assertEquals("Main.kt", vm.uiState.value.selectedPath)
        }

    @Test
    fun commitEdit_blankMessageOrPath_isIgnored() =
        runTest {
            val repoRepository =
                mockk<RepoRepository> {
                    coEvery { getTree(any(), any(), any()) } returns Result.success(listOf(treeNode("Main.kt", "Main.kt")))
                    coEvery { getFileContent(any(), any(), any(), any()) } returns
                        Result.success(FileContentData("Main.kt", "Main.kt", 4L, FileKind.CODE, "code", "blob-old"))
                }
            val vm = editingSetup(repoRepository)

            vm.commitEdit(message = "  ", newBranchName = null, newFilePath = null)
            // 校验不过：不进入提交，不调写接口
            assertTrue(vm.uiState.value.editState is FileEditState.Editing)
            coVerify(exactly = 0) { repoRepository.updateFileContent(any(), any(), any(), any(), any(), any(), any()) }
        }

    @Test
    fun commitEdit_newFileBlankPath_isIgnored() =
        runTest {
            val repoRepository =
                mockk<RepoRepository> {
                    coEvery { getTree(any(), any(), any()) } returns Result.success(listOf(treeNode("Main.kt", "Main.kt")))
                }
            val vm = viewModel(repoRepository)
            vm.loadRootTree("main")
            vm.startNewFile()

            vm.commitEdit(message = "msg", newBranchName = null, newFilePath = "   ")

            assertTrue(vm.uiState.value.editState is FileEditState.Editing)
            coVerify(exactly = 0) { repoRepository.updateFileContent(any(), any(), any(), any(), any(), any(), any()) }
        }

    @Test
    fun reloadAfterConflict_deleteConflict_refreshesViewerAndClosesEditor() =
        runTest {
            val repoRepository =
                mockk<RepoRepository> {
                    coEvery { getTree(any(), any(), any()) } returns Result.success(listOf(treeNode("Main.kt", "Main.kt")))
                    coEvery { getFileContent(any(), any(), any(), any()) } returns
                        Result.success(FileContentData("Main.kt", "Main.kt", 4L, FileKind.CODE, "code", "blob-old"))
                    coEvery { deleteFile(any(), any(), any(), any(), any(), any()) } returns
                        Result.success(FileCommitResult.Conflict("latest99"))
                }
            val vm = editingSetup(repoRepository)
            vm.deleteFile(message = "remove")
            assertTrue(vm.uiState.value.editState is FileEditState.Conflict)

            vm.reloadAfterConflict()

            // 删除冲突「重载」：关闭编辑器回查看器并刷新最新内容（refreshViewerContent）
            assertTrue(vm.uiState.value.editState is FileEditState.Idle)
            assertEquals(
                FileViewState.Loaded(FileContentData("Main.kt", "Main.kt", 4L, FileKind.CODE, "code", "blob-old")),
                vm.uiState.value.fileState,
            )
            coVerify(exactly = 2) { repoRepository.getFileContent(any(), any(), any(), any()) }
        }

    @Test
    fun reloadAfterConflict_fetchFailure_emitsFailedAndClosesEditor() =
        runTest {
            val repoRepository =
                mockk<RepoRepository> {
                    coEvery { getTree(any(), any(), any()) } returns Result.success(listOf(treeNode("Main.kt", "Main.kt")))
                    // 单桩 + 计数：第 1 次（openFile）成功返回旧内容；第 2 次（冲突重载）网络失败。
                    // 不用双桩——MockK 多桩匹配顺序易混淆（本项目血泪）。
                    var fetchCount = 0
                    coEvery { getFileContent(any(), any(), any(), any()) } answers {
                        fetchCount++
                        if (fetchCount == 1) {
                            Result.success(FileContentData("Main.kt", "Main.kt", 4L, FileKind.CODE, "code", "blob-old"))
                        } else {
                            Result.failure(IOException("down"))
                        }
                    }
                    coEvery { updateFileContent(any(), any(), any(), any(), any(), any(), any()) } returns
                        Result.success(FileCommitResult.Conflict("latest99"))
                }
            val vm = viewModel(repoRepository)
            vm.loadRootTree("main")
            vm.openFile(treeNode("Main.kt", "Main.kt"), "main")
            vm.startEdit()
            val events = mutableListOf<FileEditEvent>()
            val job = launch(UnconfinedTestDispatcher()) { vm.editEvents.collect { events.add(it) } }
            vm.commitEdit(message = "fix", newBranchName = null, newFilePath = null)

            vm.reloadAfterConflict()

            // 重载失败：关编辑器（不卡冲突态）+ 错误事件
            assertTrue(vm.uiState.value.editState is FileEditState.Idle)
            assertEquals(FileEditEvent.Failed(RepoErrorType.NETWORK), events.single())
            job.cancel()
        }

    @Test
    fun overwriteAfterConflict_deleteRetry_succeedsAndRefreshes() =
        runTest {
            val repoRepository =
                mockk<RepoRepository> {
                    coEvery { getTree(any(), any(), any()) } returns Result.success(listOf(treeNode("Main.kt", "Main.kt")))
                    coEvery { getFileContent(any(), any(), any(), any()) } returns
                        Result.success(FileContentData("Main.kt", "Main.kt", 4L, FileKind.CODE, "code", "blob-old"))
                    coEvery { deleteFile(any(), any(), any(), any(), any(), any()) } returns
                        Result.success(FileCommitResult.Conflict("latest99"))
                    // 重试删除：用最新 sha
                    coEvery { deleteFile("octocat", "Hello-World", "Main.kt", "latest99", "remove", "main") } returns
                        Result.success(FileCommitResult.Success("del-c", null))
                }
            val vm = editingSetup(repoRepository)
            val events = mutableListOf<FileEditEvent>()
            val job = launch(UnconfinedTestDispatcher()) { vm.editEvents.collect { events.add(it) } }
            vm.deleteFile(message = "remove")

            vm.overwriteAfterConflict()

            assertEquals(FileEditEvent.Deleted("Main.kt"), events.single())
            assertTrue(vm.uiState.value.editState is FileEditState.Idle)
            coVerify {
                repoRepository.deleteFile("octocat", "Hello-World", "Main.kt", "latest99", "remove", "main")
            }
            job.cancel()
        }

    @Test
    fun overwriteAfterConflict_secondConflict_keepsConflictWithNewSha() =
        runTest {
            val repoRepository =
                mockk<RepoRepository> {
                    coEvery { getTree(any(), any(), any()) } returns Result.success(listOf(treeNode("Main.kt", "Main.kt")))
                    coEvery { getFileContent(any(), any(), any(), any()) } returns
                        Result.success(FileContentData("Main.kt", "Main.kt", 4L, FileKind.CODE, "code", "blob-old"))
                    coEvery { updateFileContent(any(), any(), any(), any(), any(), any(), any()) } returns
                        Result.success(FileCommitResult.Conflict("latest99"))
                    // 覆盖重试再次 409（竞态窗口）
                    coEvery { updateFileContent("octocat", "Hello-World", "Main.kt", "code", "latest99", "fix", "main") } returns
                        Result.success(FileCommitResult.Conflict("latest100"))
                }
            val vm = editingSetup(repoRepository)
            vm.commitEdit(message = "fix", newBranchName = null, newFilePath = null)

            vm.overwriteAfterConflict()

            val conflict = vm.uiState.value.editState as FileEditState.Conflict
            assertEquals("latest100", conflict.latestSha)
        }

    @Test
    fun overwriteAfterConflict_failure_emitsFailedAndRestoresConflict() =
        runTest {
            val repoRepository =
                mockk<RepoRepository> {
                    coEvery { getTree(any(), any(), any()) } returns Result.success(listOf(treeNode("Main.kt", "Main.kt")))
                    coEvery { getFileContent(any(), any(), any(), any()) } returns
                        Result.success(FileContentData("Main.kt", "Main.kt", 4L, FileKind.CODE, "code", "blob-old"))
                    coEvery { updateFileContent(any(), any(), any(), any(), any(), any(), any()) } returns
                        Result.success(FileCommitResult.Conflict("latest99"))
                    // 覆盖重试网络失败
                    coEvery { updateFileContent("octocat", "Hello-World", "Main.kt", "code", "latest99", "fix", "main") } returns
                        Result.failure(IOException("down"))
                }
            val vm = editingSetup(repoRepository)
            val events = mutableListOf<FileEditEvent>()
            val job = launch(UnconfinedTestDispatcher()) { vm.editEvents.collect { events.add(it) } }
            vm.commitEdit(message = "fix", newBranchName = null, newFilePath = null)

            vm.overwriteAfterConflict()

            // 失败回冲突态（用户仍可三选一）+ 错误事件
            assertTrue(vm.uiState.value.editState is FileEditState.Conflict)
            assertEquals(FileEditEvent.Failed(RepoErrorType.NETWORK), events.single())
            job.cancel()
        }

    @Test
    fun deleteFile_failure_emitsFailedAndRestoresEditing() =
        runTest {
            val repoRepository =
                mockk<RepoRepository> {
                    coEvery { getTree(any(), any(), any()) } returns Result.success(listOf(treeNode("Main.kt", "Main.kt")))
                    coEvery { getFileContent(any(), any(), any(), any()) } returns
                        Result.success(FileContentData("Main.kt", "Main.kt", 4L, FileKind.CODE, "code", "blob-old"))
                    coEvery { deleteFile(any(), any(), any(), any(), any(), any()) } returns
                        Result.failure(IOException("down"))
                }
            val vm = editingSetup(repoRepository)
            val events = mutableListOf<FileEditEvent>()
            val job = launch(UnconfinedTestDispatcher()) { vm.editEvents.collect { events.add(it) } }

            vm.deleteFile(message = "remove")

            // 失败回编辑态（文本保留）+ 错误事件
            assertEquals(
                FileEditState.Editing(isNew = false, text = "code", sha = "blob-old", isMarkdown = false),
                vm.uiState.value.editState,
            )
            assertEquals(FileEditEvent.Failed(RepoErrorType.NETWORK), events.single())
            job.cancel()
        }

    // ─── #166 / UI14 文件内查找（状态层；纯逻辑状态机单测见 core:editor FileFindStateTest）───

    /** 打开代码文件查看器（查找入口只在 CODE 分支出现）。 */
    private fun viewerSetup(repoRepository: RepoRepository): RepoFilesViewModel {
        coEvery { repoRepository.getTree(any(), any(), any()) } returns
            Result.success(listOf(treeNode("Main.kt", "Main.kt")))
        coEvery { repoRepository.getFileContent(any(), any(), any(), any()) } returns
            Result.success(FileContentData("Main.kt", "Main.kt", 4L, FileKind.CODE, "code"))
        val vm = viewModel(repoRepository)
        vm.loadRootTree("main")
        vm.openFile(treeNode("Main.kt", "Main.kt"), "main")
        return vm
    }

    @Test
    fun openFind_setsPanelOpenKeepingEmptyQuery() =
        runTest {
            val vm = viewerSetup(mockk())

            vm.openFind()

            val state = vm.uiState.value
            assertTrue(state.isFindOpen)
            assertEquals(FileFindState(), state.findState)
        }

    @Test
    fun onFindQueryChanged_afterMatches_resetsCounterAndEntersSearching() =
        runTest {
            val vm = viewerSetup(mockk())
            vm.openFind()
            vm.onFindResults(FileFindState(query = "fun", matchCount = 5, currentMatchIndex = 2))

            vm.onFindQueryChanged("func")

            val state = vm.uiState.value.findState
            assertEquals("func", state.query)
            assertEquals("新查询结果未到，不得沿用上一查询词计数", 0, state.matchCount)
            assertEquals(0, state.matchOrdinal)
            assertTrue(state.isSearching)
        }

    @Test
    fun onFindNext_withMatches_advancesOrdinalAndWrapsToFirst() =
        runTest {
            val vm = viewerSetup(mockk())
            vm.openFind()
            vm.onFindResults(FileFindState(query = "fun", matchCount = 3, currentMatchIndex = 0))

            vm.onFindNext()
            assertEquals(2, vm.uiState.value.findState.matchOrdinal)

            vm.onFindNext()
            assertEquals(3, vm.uiState.value.findState.matchOrdinal)

            vm.onFindNext()
            assertEquals("末项之后回到第一项", 1, vm.uiState.value.findState.matchOrdinal)
        }

    @Test
    fun onFindPrevious_withMatches_wrapsBackToLastMatch() =
        runTest {
            val vm = viewerSetup(mockk())
            vm.openFind()
            vm.onFindResults(FileFindState(query = "fun", matchCount = 4, currentMatchIndex = 0))

            vm.onFindPrevious()

            assertEquals("首项之前回到末项", 4, vm.uiState.value.findState.matchOrdinal)
        }

    @Test
    fun onFindResults_noMatches_clearsOrdinalAndCounter() =
        runTest {
            val vm = viewerSetup(mockk())
            vm.openFind()
            vm.onFindQueryChanged("zzz")

            vm.onFindResults(FileFindState(query = "zzz", matchCount = 0, currentMatchIndex = FileFindState.NO_MATCH))

            val state = vm.uiState.value.findState
            assertEquals(0, state.matchCount)
            assertEquals(0, state.matchOrdinal)
            assertFalse(state.isSearching)
            assertFalse(state.hasMatches)
        }

    @Test
    fun onFindResults_authoritativeIndex_overridesOptimisticOrdinal() =
        runTest {
            val vm = viewerSetup(mockk())
            vm.openFind()
            vm.onFindResults(FileFindState(query = "fun", matchCount = 3, currentMatchIndex = 0))
            vm.onFindNext()
            assertEquals("乐观推进后为第 2 项", 2, vm.uiState.value.findState.matchOrdinal)

            // 编辑器权威结果（Sora 匹配表）为准：光标在首处匹配上 → 收敛回第 1 项
            vm.onFindResults(FileFindState(query = "fun", matchCount = 3, currentMatchIndex = 0))

            assertEquals(1, vm.uiState.value.findState.matchOrdinal)
            assertEquals(3, vm.uiState.value.findState.matchCount)
        }

    @Test
    fun onFindResults_outOfRangeIndex_dropsOrdinalNotCounter() =
        runTest {
            val vm = viewerSetup(mockk())
            vm.openFind()
            vm.onFindQueryChanged("fun")

            vm.onFindResults(FileFindState(query = "fun", matchCount = 2, currentMatchIndex = 7))

            val state = vm.uiState.value.findState
            assertEquals(2, state.matchCount)
            assertEquals("越界序号记为未选中，不臆造第 n 项", 0, state.matchOrdinal)
        }

    @Test
    fun closeFind_resetsFindStateAndClosesPanel() =
        runTest {
            val vm = viewerSetup(mockk())
            vm.openFind()
            vm.onFindResults(FileFindState(query = "fun", matchCount = 5, currentMatchIndex = 1))

            vm.closeFind()

            val state = vm.uiState.value
            assertFalse(state.isFindOpen)
            assertEquals(FileFindState(), state.findState)
        }

    @Test
    fun closeFile_endsFindSession() =
        runTest {
            val vm = viewerSetup(mockk())
            vm.openFind()
            vm.onFindResults(FileFindState(query = "fun", matchCount = 5, currentMatchIndex = 1))

            vm.closeFile()

            val state = vm.uiState.value
            assertFalse("关闭查看器不得残留查找会话", state.isFindOpen)
            assertEquals(FileFindState(), state.findState)
        }

    @Test
    fun openFile_anotherFile_endsFindSession() =
        runTest {
            val vm = viewerSetup(mockk())
            vm.openFind()
            vm.onFindResults(FileFindState(query = "fun", matchCount = 5, currentMatchIndex = 1))

            vm.openFile(treeNode("Other.kt", "Other.kt"), "main")

            val state = vm.uiState.value
            assertFalse("换文件不得残留上一文件的查找会话（高亮由 View 侧 clearFindText 清除）", state.isFindOpen)
            assertEquals(FileFindState(), state.findState)
        }

    // ── 草稿持久化（需求审计 §10 P2：进程被杀不丢工作）────────────────────────

    private fun draftKey(): DraftKey = DraftTargets.fileEdit("octocat", "Hello-World", "main", "Main.kt")

    private fun draftSaver(
        repository: DraftRepository,
        debounceMillis: Long = 0,
    ) = DraftAutoSaver(repository, CoroutineScope(UnconfinedTestDispatcher()), debounceMillis)

    @Test
    fun startEdit_storedDraftDiffersFromRemote_restoresTextAndEmitsDraftRestored() =
        runTest {
            val drafts = RecordingDraftRepository()
            drafts.store(draftKey(), "draft text")

            val vm = editingSetup(mockk<RepoRepository>(relaxed = true), text = "remote", drafts = draftSaver(drafts))
            val events = mutableListOf<FileEditEvent>()
            val job = launch(UnconfinedTestDispatcher()) { vm.editEvents.collect { events.add(it) } }

            assertEquals("draft text", (vm.uiState.value.editState as FileEditState.Editing).text)
            assertEquals(listOf<FileEditEvent>(FileEditEvent.DraftRestored), events)
            job.cancel()
        }

    @Test
    fun startEdit_storedDraftEqualsRemote_keepsRemoteAndEmitsNothing() =
        runTest {
            val drafts = RecordingDraftRepository()
            drafts.store(draftKey(), "remote")

            val vm = editingSetup(mockk<RepoRepository>(relaxed = true), text = "remote", drafts = draftSaver(drafts))
            val events = mutableListOf<FileEditEvent>()
            val job = launch(UnconfinedTestDispatcher()) { vm.editEvents.collect { events.add(it) } }

            assertEquals("remote", (vm.uiState.value.editState as FileEditState.Editing).text)
            assertTrue("草稿与远端一致不得假恢复", events.isEmpty())
            job.cancel()
        }

    @Test
    fun startEdit_draftLoadThrows_keepsRemoteSilently() =
        runTest {
            val drafts = RecordingDraftRepository(loadFailure = IOException("corrupt"))

            val vm = editingSetup(mockk<RepoRepository>(relaxed = true), text = "remote", drafts = draftSaver(drafts))
            val events = mutableListOf<FileEditEvent>()
            val job = launch(UnconfinedTestDispatcher()) { vm.editEvents.collect { events.add(it) } }

            assertEquals("remote", (vm.uiState.value.editState as FileEditState.Editing).text)
            assertTrue("读失败按无草稿静默处理", events.isEmpty())
            job.cancel()
        }

    @Test
    fun startEdit_userTypedBeforeDraftLoad_doesNotClobberInput() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            val drafts = RecordingDraftRepository(loadGate = gate)
            drafts.store(draftKey(), "from-disk")
            val repoRepository = mockk<RepoRepository>(relaxed = true)
            coEvery { repoRepository.getTree(any(), any(), any()) } returns Result.success(listOf(treeNode("Main.kt", "Main.kt")))
            coEvery { repoRepository.getFileContent(any(), any(), any(), any()) } returns
                Result.success(FileContentData("Main.kt", "Main.kt", 4L, FileKind.CODE, "remote", "blob-old"))
            val vm = viewModel(repoRepository, draftSaver(drafts))
            vm.loadRootTree("main")
            vm.openFile(treeNode("Main.kt", "Main.kt"), "main")
            vm.startEdit()

            vm.onEditorTextChanged("typed by user")
            gate.complete(Unit)
            runCurrent()

            assertEquals("typed by user", (vm.uiState.value.editState as FileEditState.Editing).text)
        }

    @Test
    fun onEditorTextChanged_backToRemoteBaseline_discardsDraft() =
        runTest {
            val drafts = RecordingDraftRepository()
            val vm = editingSetup(mockk<RepoRepository>(relaxed = true), text = "remote", drafts = draftSaver(drafts))

            vm.onEditorTextChanged("changed")
            assertEquals("changed", drafts.content(draftKey()))

            vm.onEditorTextChanged("remote")

            assertNull("回到基线 = 无未提交内容", drafts.content(draftKey()))
        }

    @Test
    fun dismissEdit_pendingDebounce_savesCurrentTextImmediately() =
        runTest {
            val drafts = RecordingDraftRepository()
            val vm = editingSetup(mockk<RepoRepository>(relaxed = true), drafts = draftSaver(drafts, debounceMillis = 60_000))

            vm.onEditorTextChanged("work in progress")
            assertNull("防抖窗口未到不落盘", drafts.content(draftKey()))

            vm.dismissEdit()

            assertEquals("work in progress", drafts.content(draftKey()))
        }

    @Test
    fun commitEdit_success_discardsStoredDraft() =
        runTest {
            val drafts = RecordingDraftRepository()
            val repoRepository =
                mockk<RepoRepository> {
                    coEvery { getTree(any(), any(), any()) } returns Result.success(listOf(treeNode("Main.kt", "Main.kt")))
                    coEvery { getFileContent(any(), any(), any(), any()) } returns
                        Result.success(FileContentData("Main.kt", "Main.kt", 4L, FileKind.CODE, "code", "blob-old"))
                    coEvery { updateFileContent(any(), any(), any(), any(), any(), any(), any()) } returns
                        Result.success(FileCommitResult.Success("commit-1", "blob-new"))
                }
            val vm = editingSetup(repoRepository, drafts = draftSaver(drafts))
            vm.onEditorTextChanged("committing")
            assertEquals("committing", drafts.content(draftKey()))

            vm.commitEdit(message = "fix", newBranchName = null, newFilePath = null)

            assertNull("提交成功后草稿使命结束", drafts.content(draftKey()))
        }

    @Test
    fun discardRestoredDraft_resetsTextToBaselineAndClearsStoredDraft() =
        runTest {
            val drafts = RecordingDraftRepository()
            drafts.store(draftKey(), "draft")
            val vm = editingSetup(mockk<RepoRepository>(relaxed = true), text = "remote", drafts = draftSaver(drafts))
            assertEquals("draft", (vm.uiState.value.editState as FileEditState.Editing).text)

            vm.discardRestoredDraft()

            assertEquals("remote", (vm.uiState.value.editState as FileEditState.Editing).text)
            assertNull(drafts.content(draftKey()))
        }
}

/** 内存 [DraftRepository]（可控读门闩 / IO 失败注入，供草稿恢复时序测试）。 */
private class RecordingDraftRepository(
    private val loadFailure: IOException? = null,
    private val loadGate: CompletableDeferred<Unit>? = null,
) : DraftRepository {
    private val drafts = mutableMapOf<DraftKey, String>()

    fun store(
        key: DraftKey,
        content: String,
    ) {
        drafts[key] = content
    }

    fun content(key: DraftKey): String? = drafts[key]

    override suspend fun load(key: DraftKey): String? {
        loadGate?.await()
        loadFailure?.let { throw it }
        return drafts[key]
    }

    override suspend fun save(
        key: DraftKey,
        content: String,
    ) {
        if (content.isBlank()) drafts.remove(key) else drafts[key] = content
    }

    override suspend fun clear(key: DraftKey) {
        drafts.remove(key)
    }
}
