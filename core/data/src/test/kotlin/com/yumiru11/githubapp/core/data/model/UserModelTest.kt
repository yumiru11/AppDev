package com.yumiru11.githubapp.core.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * User 统一领域模型（DATA-2）：Star 总数的「未取到 ≠ 0」契约。
 *
 * GitHub REST `/user` 不返回 star 总数（#166 / UI21），模型用可空表示「未取到」；
 * UI（feature:profile 统计行）据此决定是否渲染这一项，而不是拿 0 冒充真实值。
 */
class UserModelTest {
    @Test
    fun starredCount_notFetched_isNullAndDistinctFromRealZero() {
        val notFetched = User(login = "octocat")
        val realZero = User(login = "octocat", starredCount = 0)

        // 默认值必须是 null（未取到）；若被改成 0，Profile 统计行会把「未取到」画成假计数。
        assertNull(notFetched.starredCount)
        assertEquals(0, realZero.starredCount)
    }
}
