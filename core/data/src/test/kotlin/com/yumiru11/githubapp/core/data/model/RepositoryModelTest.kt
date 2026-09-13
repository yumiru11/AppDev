package com.yumiru11.githubapp.core.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Repository 统一领域模型（DATA-2）：派生属性 [Repository.fullName] 的契约。
 *
 * fullName 是 GitHub REST `full_name` 的本地等价物，被多处消费：UI 展示与仓库选择器
 * 稳定 key（feature:home RepoPickerSheet）、搜索结果映射（core:github-data SearchMappers）。
 * 格式一旦漂移（分隔符、编码、大小写折叠）会同时破坏展示与 key 稳定性，故在此锁死。
 */
class RepositoryModelTest {
    @Test
    fun fullName_ownerLoginAndName_areJoinedWithExactlyOneSlash() {
        val repository = Repository(ownerLogin = "octocat", name = "Hello-World")

        assertEquals("octocat/Hello-World", repository.fullName)
    }

    @Test
    fun fullName_legalSpecialCharacters_arePreservedVerbatim() {
        // GitHub 的 owner/repo 合法字符含 '-'、'_'、'.'（如 my-org/my_repo.v2）；
        // 派生属性必须原样拼接，不做 URL 编码 / 大小写折叠 / 路径清洗。
        val repository = Repository(ownerLogin = "my-org", name = "my_repo.v2")

        assertEquals("my-org/my_repo.v2", repository.fullName)
    }

    @Test
    fun copy_renamedRepository_recomputesFullName() {
        // copy() 是 Star/Watch 乐观更新与失败回滚的既有路径；派生属性若被构造期
        // 快照（如 val fullName = "$ownerLogin/$name"），副本会带着旧 fullName ——
        // 本测试锁死「派生属性始终跟随当前 owner/name」。
        val original = Repository(ownerLogin = "octocat", name = "Hello-World", stargazerCount = 1)

        val renamed = original.copy(name = "Spoon-Knife")

        assertEquals("octocat/Spoon-Knife", renamed.fullName)
        assertEquals(1, renamed.stargazerCount)
        assertNotEquals(original.fullName, renamed.fullName)
    }
}
