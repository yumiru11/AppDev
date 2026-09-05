package com.yumiru11.githubapp.core.navigation

import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * AppRoute 类型安全路由的序列化 round-trip 测试（#90）。
 *
 * Navigation 2.8 以 @Serializable 的 route 类生成 pattern 与参数编码；本测试驱动
 * 每个 route 类型的生成序列化器（$serializer：descriptor/serialize/deserialize），
 * 既验证模型可序列化（JaCoCo 分母含生成代码，与 github-rest DTO 同口径），
 * 也守护 Navigation 路由表的参数结构稳定性。
 */
class AppRouteSerializationTest {
    private val json = Json { encodeDefaults = true }

    /** 泛型 round-trip：root 类型必须是具体的 @Serializable 路由（非多态接口）。 */
    private inline fun <reified T : AppRoute> roundTrip(value: T) {
        val encoded = json.encodeToString(serializer<T>(), value)
        val decoded = json.decodeFromString(serializer<T>(), encoded)
        assertEquals("round-trip 应还原相等对象：$value", value, decoded)
    }

    @Test
    fun allRoutes_jsonRoundTrip_preservesEquality() {
        roundTrip(AppRoute.Home)
        roundTrip(AppRoute.Login)
        roundTrip(AppRoute.Search)
        roundTrip(AppRoute.Settings)
        roundTrip(AppRoute.Editor)
        roundTrip(AppRoute.Repo("owner", "repo"))
        roundTrip(AppRoute.Repo("owner", "repo", "feature/x"))
        roundTrip(AppRoute.Issues("owner", "repo"))
        roundTrip(AppRoute.Issue("owner", "repo", 123))
        roundTrip(AppRoute.Pulls("owner", "repo"))
        roundTrip(AppRoute.PrCreate("owner", "repo"))
        roundTrip(AppRoute.Branches("owner", "repo"))
        roundTrip(AppRoute.Branches("owner", "repo", "feature/x"))
        roundTrip(AppRoute.IssueCreate("owner", "repo"))
        roundTrip(AppRoute.Pr("owner", "repo", 456))
        roundTrip(AppRoute.Commit("owner", "repo", "0123456789abcdef"))
        roundTrip(AppRoute.Discussion("owner", "repo", 7))
        roundTrip(AppRoute.User("login"))
        // 多段文件路径（历史深链崩溃点）：path 经参数序列化器编码，round-trip 保持原样
        roundTrip(AppRoute.Blob("owner", "repo", "main", "app/src/main/kt/Main.kt"))
        roundTrip(AppRoute.Blob("owner", "repo", "main", "My File.kt"))
    }
}