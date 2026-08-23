package com.yumiru11.githubapp.core.githubrest.model

import com.yumiru11.githubapp.core.githubrest.api.GitHubRestClient
import kotlinx.serialization.decodeFromString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * RepositoryDto permissions 解析测试（T17 权限显隐数据源）。
 *
 * 覆盖：write 用户（push=true）权限位解析、默认分支解析、missing permissions 缺省 null。
 */
class RepositoryDtoTest {
    private val json = GitHubRestClient.createJson()

    @Test
    fun repositoryDto_fullPermissions_parsesBits() {
        val dto: RepositoryDto =
            json.decodeFromString(
                """
                {
                  "id": 1,
                  "name": "Hello-World",
                  "full_name": "octocat/Hello-World",
                  "private": false,
                  "owner": { "login": "octocat", "id": 1 },
                  "default_branch": "main",
                  "permissions": { "admin": false, "maintain": false, "push": true, "triage": true, "pull": true }
                }
                """.trimIndent(),
            )

        val permissions = dto.permissions
        assertNotNull(permissions)
        assertTrue(permissions?.push == true)
        assertTrue(permissions?.triage == true)
        assertEquals(false, permissions?.admin)
        assertEquals("main", dto.defaultBranch)
    }

    @Test
    fun repositoryDto_permissionsMissing_defaultsToNull() {
        val dto: RepositoryDto =
            json.decodeFromString(
                """
                {
                  "id": 1,
                  "name": "Hello-World",
                  "full_name": "octocat/Hello-World",
                  "private": false,
                  "owner": { "login": "octocat", "id": 1 }
                }
                """.trimIndent(),
            )

        assertEquals(null, dto.permissions)
    }
}
