package com.yumiru11.githubapp

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.svg.SvgDecoder
import com.yumiru11.githubapp.core.githubrest.di.GitHubHttpClient
import com.yumiru11.githubapp.logging.RedactingDebugTree
import dagger.hilt.android.HiltAndroidApp
import okhttp3.OkHttpClient
import timber.log.Timber
import javax.inject.Inject

/**
 * 应用入口：Hilt 图根节点 + Coil ImageLoader 装配。
 *
 * Coil 3 装配（2026-08-14 真机走查修复：图片不加载）：
 * 默认 ImageLoader 不带网络组件，所有 AsyncImage/Coil3ImageTransformerImpl
 * 的网络图静默失败——必须显式装配 OkHttpNetworkFetcherFactory。
 * 复用 [GitHubHttpClient]（含认证头拦截器），私有图也能鉴权加载。
 */
@HiltAndroidApp
class GitHubApp :
    Application(),
    SingletonImageLoader.Factory {
    @Inject
    @GitHubHttpClient
    lateinit var okHttpClient: OkHttpClient

    override fun onCreate() {
        super.onCreate()
        // 调试工具链（#169 / L14）：仅 debug 变体种日志树，且消息体经 LogRedaction 脱敏。
        // release 不种树 → Timber 调用点被 R8 裁掉，线上零日志开销。
        if (BuildConfig.DEBUG) {
            Timber.plant(RedactingDebugTree())
        }
    }

    override fun newImageLoader(platformContext: PlatformContext): ImageLoader =
        ImageLoader
            .Builder(platformContext)
            .components {
                add(OkHttpNetworkFetcherFactory(callFactory = okHttpClient))
                // shields 徽章是 SVG（GitHub README 高频元素）；Coil 3 默认不含 SVG 解码器
                add(SvgDecoder.Factory())
            }.build()
}
