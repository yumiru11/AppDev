package com.yumiru11.githubapp.feature.repo

import androidx.lifecycle.SavedStateHandle
import com.yumiru11.githubapp.core.testing.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
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
}
