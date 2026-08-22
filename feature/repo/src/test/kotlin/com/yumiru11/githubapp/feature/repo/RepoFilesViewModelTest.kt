@file:Suppress("LargeClass")
// 704 行：T11 树/目录/文件 + T22 编辑提交（提交/冲突三选项/删除/失败路径）全流程单测聚一文件
// （单一被测类，拆分收益低于同文件聚合；后续测试膨胀再拆 EditTest 子类）

package com.yumiru11.githubapp.feature.repo

import androidx.lifecycle.SavedStateHandle
import com.yumiru11.githubapp.core.testing.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
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

    private fun viewModel(repoRepository: RepoRepository): RepoFilesViewModel =
        RepoFilesViewModel(
            savedStateHandle = savedStateHandle,
            repoRepository = repoRepository,
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
    fun loadRootTree_notFound_emitsErrorNotFound() =
        runTest {
            val repoRepository =
                mockk<RepoRepository> {
                    coEvery { getTree(any(), any(), any()) } returns Result.failure(httpException(404))
                }
            val viewModel = viewModel(repoRepository)

            viewModel.loadRootTree("main")

            assertEquals(TreeState.Error(RepoErrorType.NOT_FOUND), viewModel.uiState.value.treeState)
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
            assertEquals(FileViewState.Error(RepoErrorType.NOT_FOUND), state.fileState)
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
    ): RepoFilesViewModel {
        coEvery { repoRepository.getTree(any(), any(), any()) } returns
            Result.success(listOf(treeNode("Main.kt", "Main.kt")))
        coEvery { repoRepository.getFileContent(any(), any(), any(), any()) } returns
            Result.success(FileContentData("Main.kt", "Main.kt", 4L, kind, text, sha))
        val vm = viewModel(repoRepository)
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
                    coEvery { updateFileContent(any(), any(), any(), any(), any(), any(), any()) } returns
                        Result.success(FileCommitResult.Success("c1", "b1"))
                }
            val vm = editingSetup(repoRepository)
            val events = mutableListOf<FileEditEvent>()
            val job = launch(UnconfinedTestDispatcher()) { vm.editEvents.collect { events.add(it) } }

            vm.commitEdit(message = "fix", newBranchName = "feat-x", newFilePath = null)

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
}
