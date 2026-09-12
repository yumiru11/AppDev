package com.yumiru11.githubapp.core.markdown

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import coil3.EventListener
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import com.yumiru11.githubapp.core.markdown.fixture.MarkdownGfmFixtures
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * GIF 解码器注册回归（缺陷 #3：GIF 只出首帧）。
 *
 * Coil 3 的扩展解码器经 `ServiceLoader`（META-INF/services/coil3.util.DecoderServiceLoaderTarget）
 * 自动注册；本测试断言**默认 ImageLoader 构建路径**（与 :app 的 GitHubApp.newImageLoader 同型）
 * 的组件表里含 `coil3.gif.*` 解码器工厂，并用 §2.3 的 31-image-gif 夹具证明原生链
 * 会把 GIF URL 发进该 ImageLoader。API 28+ 注册 AnimatedImageDecoder.Factory，
 * API 26-27 注册 GifDecoder.Factory（Robolectric sdk=35 → AnimatedImageDecoder）。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class CoilGifDecoderTest {
    @get:Rule
    val composeRule = createComposeRule()

    @After
    fun tearDown() {
        SingletonImageLoader.reset()
    }

    @Test
    fun defaultImageLoader_registersGifDecoderFactory() {
        val loader = ImageLoader.Builder(RuntimeEnvironment.getApplication()).build()

        val factories = loader.components.decoderFactories.map { it.javaClass.name }

        assertTrue(
            "coil-gif 未随默认 ImageLoader 注册（仅剩首帧解码器）：$factories",
            factories.any { it.startsWith("coil3.gif.") },
        )
    }

    @Test
    fun gifFixture_nativeChain_requestsImageThroughCoil() {
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
                EnhancedMarkdownViewer(markdown = MarkdownGfmFixtures.byId("31-image-gif").markdown())
            }
        }

        composeRule.waitUntil(timeoutMillis = 10_000) {
            synchronized(requested) { requested.isNotEmpty() }
        }
        assertTrue(
            "31-image-gif 的动图 URL 未进入 Coil 请求：$requested",
            synchronized(requested) { requested.any { it == "https://example.com/demo.gif" } },
        )
    }
}
