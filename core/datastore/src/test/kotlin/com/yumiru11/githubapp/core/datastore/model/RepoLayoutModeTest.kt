package com.yumiru11.githubapp.core.datastore.model

import org.junit.Assert.assertEquals
import org.junit.Test

/** 仓库列表布局模式（#166 / UI01）的纯函数契约。 */
class RepoLayoutModeTest {
    @Test
    fun toggled_list_returnsGrid() {
        assertEquals(RepoLayoutMode.GRID, RepoLayoutMode.LIST.toggled())
    }

    @Test
    fun toggled_grid_returnsList() {
        assertEquals(RepoLayoutMode.LIST, RepoLayoutMode.GRID.toggled())
    }

    @Test
    fun toggled_appliedTwice_returnsToOriginal() {
        RepoLayoutMode.entries.forEach { mode ->
            assertEquals(mode, mode.toggled().toggled())
        }
    }

    @Test
    fun entries_containsExactlyTwoLayouts() {
        // ui-design §3.2 用户拍板 B2-2：只有「网格 / 通栏」两态，新增布局需先改设计文档
        assertEquals(listOf(RepoLayoutMode.LIST, RepoLayoutMode.GRID), RepoLayoutMode.entries.toList())
    }
}
