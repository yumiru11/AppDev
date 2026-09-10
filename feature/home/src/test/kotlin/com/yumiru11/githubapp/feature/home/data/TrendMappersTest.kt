package com.yumiru11.githubapp.feature.home.data

import com.yumiru11.githubapp.core.githubrest.model.RepositoryDto
import com.yumiru11.githubapp.core.githubrest.model.TrendDto
import com.yumiru11.githubapp.core.githubrest.model.UserDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Trending DTO → 领域模型映射测试（L08）：两路数据源归一 + 脏数据/空字段收敛。
 */
class TrendMappersTest {
    @Test
    fun trendDtoToTrendItem_completeRow_mapsAllFields() {
        val item =
            TrendDto(
                owner = "JetBrains",
                repo = "kotlin",
                description = "The Kotlin Programming Language.",
                language = "Kotlin",
                stars = 51_234,
                forks = 6_789,
            ).toTrendItem()

        assertEquals("JetBrains/kotlin", item?.fullName)
        assertEquals("The Kotlin Programming Language.", item?.description)
        assertEquals("Kotlin", item?.language)
        assertEquals(51_234, item?.stars)
        assertEquals(6_789, item?.forks)
        assertEquals("https://github.com/JetBrains/kotlin", item?.url)
    }

    @Test
    fun trendDtoToTrendItem_missingOwner_returnsNull() {
        assertNull(TrendDto(owner = "", repo = "kotlin").toTrendItem())
    }

    @Test
    fun trendDtoToTrendItem_blankOptionalFields_collapseToNull() {
        val item =
            TrendDto(
                owner = "JetBrains",
                repo = "kotlin",
                description = "   ",
                language = "",
            ).toTrendItem()

        assertNull(item?.description)
        assertNull(item?.language)
    }

    @Test
    fun repositoryDtoToTrendItem_searchFallbackRow_mapsFullNameAndUrl() {
        val item =
            RepositoryDto(
                id = 1,
                name = "kotlin",
                fullName = "JetBrains/kotlin",
                isPrivate = false,
                owner = UserDto(login = "JetBrains", id = 1),
                description = "Search fallback result",
                htmlUrl = "https://github.com/JetBrains/kotlin",
                stargazersCount = 51_234,
                forksCount = 6_789,
                language = "Kotlin",
            ).toTrendItem()

        assertEquals("JetBrains/kotlin", item.fullName)
        assertEquals(51_234, item.stars)
        assertEquals("https://github.com/JetBrains/kotlin", item.url)
    }

    @Test
    fun repositoryDtoToTrendItem_missingHtmlUrl_derivesGithubUrl() {
        val item =
            RepositoryDto(
                id = 1,
                name = "kotlin",
                fullName = "JetBrains/kotlin",
                isPrivate = false,
                owner = UserDto(login = "JetBrains", id = 1),
                htmlUrl = null,
            ).toTrendItem()

        assertEquals("https://github.com/JetBrains/kotlin", item.url)
    }
}
