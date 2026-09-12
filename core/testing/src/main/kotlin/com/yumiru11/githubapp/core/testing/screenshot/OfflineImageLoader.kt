package com.yumiru11.githubapp.core.testing.screenshot

import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.intercept.Interceptor
import coil3.request.ImageResult

/**
 * 截图捕获期间的「离线图片加载器」：把所有 http(s) 图片请求短路为失败。
 *
 * ## 为什么必须禁网（2026-09-12 实证）
 *
 * `AsyncImage` 是否完成加载取决于**真实网络往返**，而 [captureScreenshotDeterministic]
 * 冻结组合时钟后只推进虚拟时间、不做真实等待——同一份代码两次运行可能一次拿到图、
 * 一次拿不到。实测：CI 录制（record-screenshots.yml run 34709534968）四帧头像全空白
 * 入库，随后 Quality Gate 的 verify 把 `ProfileScreen_light` 的头像拉了下来 →
 * `b4443f17… != 1bd25108…` 基线校验失败。仓库既有基线（仓库头 / 个人页仓库行等
 * `https://github.com/{login}.png` 直连头像）全部是「无图」形态，基线只应包含与
 * 网络无关的确定性像素。
 *
 * ## 行为
 *
 * 安装一个带拦截器的 [ImageLoader]：http/https model 直接抛错（Coil 的 executeMain
 * 捕获后转成 `ErrorResult`，`AsyncImage` 渲染空白——与既有基线一致）；其余 model
 * （本地资源、null）行为不变。调用方用完必须 [SingletonImageLoader.reset]。
 */
internal fun installOfflineImageLoader() {
    SingletonImageLoader.setUnsafe { context ->
        ImageLoader
            .Builder(context)
            .components {
                add(
                    object : Interceptor {
                        override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
                            val model = chain.request.data.toString()
                            if (model.startsWith("http://") || model.startsWith("https://")) {
                                throw IllegalStateException(
                                    "截图捕获禁用网络图片（基线为离线确定性像素；" +
                                        "见 core:testing 的 installOfflineImageLoader）",
                                )
                            }
                            return chain.proceed()
                        }
                    },
                )
            }.build()
    }
}
