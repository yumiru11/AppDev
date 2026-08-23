package com.yumiru11.githubapp.core.githubgraphql

import com.apollographql.cache.normalized.memory.MemoryCacheFactory
import okhttp3.OkHttpClient
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * [GitHubApolloClientFactory] 单测：构造路径（无持久缓存 / 有持久缓存链 memory cache）。
 * 行为仅验证成功构建（Apollo 运行时保障），不发起真实网络。
 */
class GitHubApolloClientFactoryTest {
    private val serverUrl = "https://api.github.com/graphql"

    @Test
    fun create_withoutPersistentCache_buildsClient() {
        val client = GitHubApolloClientFactory.create(serverUrl, OkHttpClient())

        assertNotNull(client)
    }

    @Test
    fun create_withPersistentCache_chainsMemoryCache() {
        val persistent = MemoryCacheFactory(maxSizeBytes = 1024)

        val client = GitHubApolloClientFactory.create(serverUrl, OkHttpClient(), persistentCacheFactory = persistent)

        assertNotNull(client)
    }
}
