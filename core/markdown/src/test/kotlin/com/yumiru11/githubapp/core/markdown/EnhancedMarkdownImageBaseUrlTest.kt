package com.yumiru11.githubapp.core.markdown

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import coil3.EventListener
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 原生链相对图片的 baseRepoUrl 透传回归（缺陷 #2）。
 *
 * 用记录式 EventListener 的 SingletonImageLoader 截获 Coil 实际请求的 model：
 * 修复前 EnhancedMarkdownViewer 不把 baseRepoUrl 传进 EnhancedMarkdownImage，
 * 相对路径段落根本不会发起图片请求（回退为纯文本）；修复后必须请求 raw 域完整 URL。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class EnhancedMarkdownImageBaseUrlTest {
    @get:Rule
    val composeRule = createComposeRule()

    @After
    fun tearDown() {
        SingletonImageLoader.reset()
    }

    @Test
    fun relativeImage_withBaseRepoUrl_requestsResolvedRawUrl() {
        val requested = mutableListOf<String>()
        SingletonImageLoader.setUnsafe { context ->
            ImageLoader
                .Builder(context)
                .eventListener(
                    object : EventListener() {
                        override fun onStart(request: ImageRequest) {
                            synchronized(requested) { requested += request.data.toString() }
                        }
                    },
                ).build()
        }

        composeRule.setContent {
            MaterialTheme {
                EnhancedMarkdownViewer(
                    markdown = "![shot](./docs/screenshot.png)",
                    baseRepoUrl = "https://github.com/owner/repo",
                )
            }
        }

        composeRule.waitUntil(timeoutMillis = 10_000) {
            synchronized(requested) { requested.isNotEmpty() }
        }
        assertEquals(
            listOf("https://raw.githubusercontent.com/owner/repo/HEAD/docs/screenshot.png"),
            synchronized(requested) { requested.toList() },
        )
    }
}
