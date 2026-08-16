package com.yumiru11.githubapp.feature.repo

import com.yumiru11.githubapp.core.githubrest.model.TreeItemDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 文件树构建/展开逻辑测试（T11 验收第 1 条：递归、按需加载）。
 */
class FileTreeBuilderTest {
    private fun blob(
        path: String,
        size: Long = 10L,
    ) = TreeItemDto(path = path, type = "blob", sha = "sha-$path", size = size)

    private fun dir(path: String) = TreeItemDto(path = path, type = "tree", sha = "sha-$path")

    @Test
    fun buildRootNodes_entries_keepRootPaths() {
        val nodes = FileTreeBuilder.buildRootNodes(listOf(blob("README.md"), dir("src"), dir("docs")))

        assertEquals(3, nodes.size)
        // 排序：目录在前（docs < src），文件在后（README.md）
        assertEquals("docs", nodes[0].path)
        assertTrue(nodes[0].isDirectory)
        assertEquals("sha-docs", nodes[0].sha)
        assertEquals("src", nodes[1].path)
        assertTrue(nodes[1].isDirectory)
        assertEquals("README.md", nodes[2].path)
        assertFalse(nodes[2].isDirectory)
        assertEquals(10L, nodes[2].size)
    }

    @Test
    fun buildChildNodes_entries_prependParentPath() {
        val nodes = FileTreeBuilder.buildChildNodes(listOf(blob("Main.kt"), dir("util")), parentPath = "src/main")

        assertEquals("src/main/util", nodes[0].path)
        assertEquals("util", nodes[0].name)
        assertTrue(nodes[0].isDirectory)
        assertEquals("src/main/Main.kt", nodes[1].path)
        assertEquals("Main.kt", nodes[1].name)
    }

    @Test
    fun buildRootNodes_directoriesSortedBeforeFiles_caseInsensitive() {
        val nodes = FileTreeBuilder.buildRootNodes(listOf(blob("Zebra.txt"), dir("alpha"), blob("beta.kt")))

        assertEquals(listOf("alpha", "beta.kt", "Zebra.txt"), nodes.map { it.path })
    }

    @Test
    fun buildRootNodes_blankPathEntries_areSkipped() {
        val nodes = FileTreeBuilder.buildRootNodes(listOf(TreeItemDto(path = "", type = "blob"), blob("a.txt")))

        assertEquals(1, nodes.size)
        assertEquals("a.txt", nodes[0].path)
    }

    @Test
    fun buildChildNodes_commitEntry_typeCommit_isFile() {
        // submodule（type=commit）按文件处理（无内容，点开由 Contents API 404 兜底）
        val nodes = FileTreeBuilder.buildChildNodes(listOf(TreeItemDto(path = "lib", type = "commit", sha = "s")), "sub")

        assertFalse(nodes[0].isDirectory)
        assertEquals("sub/lib", nodes[0].path)
    }

    @Test
    fun updateNode_matchingPath_transformsNode() {
        val root =
            FileTreeBuilder
                .buildRootNodes(listOf(dir("src")))
                .let { FileTreeBuilder.updateNode(it, "src") { n -> n.copy(isExpanded = true) } }

        assertTrue(root[0].isExpanded)
    }

    @Test
    fun updateNode_nestedPath_transformsDescendant() {
        val src = FileTreeBuilder.buildChildNodes(listOf(dir("main")), "src")
        val root = FileTreeBuilder.buildRootNodes(listOf(dir("src"))).map { it.copy(children = src) }

        val updated = FileTreeBuilder.updateNode(root, "src/main") { n -> n.copy(isExpanded = true) }

        assertTrue(updated[0].children!![0].isExpanded)
    }

    @Test
    fun updateNode_unknownPath_returnsSameList() {
        val root = FileTreeBuilder.buildRootNodes(listOf(blob("a.txt")))

        val updated = FileTreeBuilder.updateNode(root, "missing") { n -> n.copy(isExpanded = true) }

        assertEquals(root, updated)
    }

    @Test
    fun visibleRows_expandedDirectories_flattenInOrder() {
        val srcChildren = FileTreeBuilder.buildChildNodes(listOf(blob("Main.kt"), dir("util")), "src")
        val root =
            FileTreeBuilder
                .buildRootNodes(listOf(blob("README.md"), dir("src")))
                .map { if (it.path == "src") it.copy(children = srcChildren, isExpanded = true) else it }

        val rows = FileTreeBuilder.visibleRows(root)

        assertEquals(4, rows.size)
        // 排序后：src（展开）→ src/util（目录在前）→ src/Main.kt → README.md
        assertEquals(listOf("src", "src/util", "src/Main.kt", "README.md"), rows.map { it.node.path })
        assertEquals(listOf(0, 1, 1, 0), rows.map { it.depth })
    }

    @Test
    fun visibleRows_collapsedDirectory_hidesChildren() {
        val srcChildren = FileTreeBuilder.buildChildNodes(listOf(blob("Main.kt")), "src")
        val root =
            FileTreeBuilder
                .buildRootNodes(listOf(dir("src")))
                .map { it.copy(children = srcChildren, isExpanded = false) }

        val rows = FileTreeBuilder.visibleRows(root)

        assertEquals(1, rows.size)
        assertEquals("src", rows[0].node.path)
    }

    @Test
    fun visibleRows_unloadedDirectory_noChildrenWalked() {
        val root = FileTreeBuilder.buildRootNodes(listOf(dir("src")))

        val rows = FileTreeBuilder.visibleRows(root)

        assertEquals(1, rows.size)
        assertNull(root[0].children)
    }

    @Test
    fun visibleRows_deepNesting_depthsAreCorrect() {
        val util = FileTreeBuilder.buildChildNodes(listOf(blob("x.kt")), "src/main/util")
        val main = FileTreeBuilder.buildChildNodes(listOf(dir("util")), "src/main").map { it.copy(children = util, isExpanded = true) }
        val src = FileTreeBuilder.buildChildNodes(listOf(dir("main")), "src").map { it.copy(children = main, isExpanded = true) }
        val root = FileTreeBuilder.buildRootNodes(listOf(dir("src"))).map { it.copy(children = src, isExpanded = true) }

        val rows = FileTreeBuilder.visibleRows(root)

        assertEquals(listOf(0, 1, 2, 3), rows.map { it.depth })
        assertEquals("src/main/util/x.kt", rows.last().node.path)
    }
}
