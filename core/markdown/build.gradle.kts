plugins {
    id("appdev.android.library")
}

android {
    namespace = "com.yumiru11.githubapp.core.markdown"

    defaultConfig {
        // coil-gif 由 Coil 3 的 ServiceLoader 机制按类名实例化（META-INF/services/
        // coil3.util.DecoderServiceLoaderTarget），R8 会改名/裁剪 → 随模块分发 keep 规则
        consumerProguardFiles("consumer-rules.pro")
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            // Android 桩方法返回默认值（而不是抛 "not mocked"）：WebViewDarkModePolicyTest
            // 是纯 JVM 测试（原因见该文件 KDoc：Robolectric 沙箱类不产 JaCoCo 覆盖数据）
            isReturnDefaultValues = true
            all {
                it.systemProperties["robolectric.pixelCopyRenderMode"] = "hardware"
            }
        }
    }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.material3)
    implementation(libs.compose.ui)

    // Markdown 渲染链路（toml 全局 markdown=0.38.1：0.43.0 需 compileSdk 37 / AGP 9，见 AGENTS.md）
    implementation(libs.markdown.renderer)
    implementation(libs.markdown.renderer.m3)
    implementation(libs.markdown.renderer.code)
    implementation(libs.markdown.renderer.coil3)

    // 图片（Coil 3 + okhttp 网络）
    implementation(libs.coil.compose)
    // GIF 动图（缺陷 #3）：Coil 3 核心不含 GIF 解码器；coil-gif 经 ServiceLoader 自动注册
    // （API 28+ AnimatedImageDecoder / API 26-27 GifDecoder，见 coil3.gif.internal.GifDecoderServiceLoaderTarget）
    implementation(libs.coil.gif)
    implementation(libs.coil.network.okhttp)

    // HTML 子集原生渲染：标签/属性三级白名单解析（MIT）
    implementation(libs.jsoup)
    // 原生 Mermaid 图表渲染（Compose Canvas，MIT；上游 swithun-liu/cmp-mermaid）
    // 取 Android 专用坐标：KMP 根坐标的变体带 Kotlin platform 属性，AGP 9 内置 Kotlin
    // 的消费端匹配不到 androidJvm 变体（实测 "Could not find mermaid-compose-0.1.10.jar"）。
    // JetBrains Compose 传递依赖整体排除：Android 目标下它们与 androidx.compose.* 同源，
    // 本项目已有 BOM 托管，重复引入会撞类。
    // mermaid-compose 的 POM 把 mermaid-core 标成 runtime scope → 编译期拿不到其类型
    // （MermaidTheme / GMResult / MermaidError）。显式补齐编译期依赖，版本与 compose 侧一致。
    implementation(
        "io.github.swithun-liu:mermaid-core-android:" +
            libs.versions.cmp.mermaid
                .get(),
    )
    implementation(libs.cmp.mermaid) {
        exclude(group = "org.jetbrains.compose.ui")
        exclude(group = "org.jetbrains.compose.foundation")
        exclude(group = "org.jetbrains.compose.runtime")
        exclude(group = "org.jetbrains.compose.annotation-internal")
        exclude(group = "org.jetbrains.compose.collection-internal")
        exclude(group = "org.jetbrains.compose.ui.util")
    }

    // WebViewAssetLoader（assets 安全加载，禁 file://；T8 WebView 兜底通道用）
    implementation(libs.androidx.webkit)

    // 图标（Material Symbols cmp 变体：GitHub Alert 卡片用 MaterialSymbols.Rounded.*）
    implementation(libs.icons.material.symbols.base.cmp)
    implementation(libs.icons.material.symbols.rounded.cmp)

    // KotlinTextMate：VS Code 同款语法高亮（替换 renderer-code 内置 Highlights）
    implementation(libs.textmate.compose)

    // GitHubLinkParser（core:navigation 提供，Markdown 内链接解析后交上层导航）
    implementation(project(":core:navigation"))

    // 设计系统（Octicons ImageVector + ExtendedColors 语义色，GitHub Alert 增强用）
    implementation(project(":core:designsystem"))

    testImplementation(project(":core:testing"))
    // PrivateImageInterceptor 代理链测试：MockWebServer 验证 Authorization 注入与回包
    testImplementation(libs.mockwebserver3)
    // WebViewDarkModePolicyTest：用 relaxed mock 的 WebSettings 复现"检查通过但调用不支持"的组合
    testImplementation(libs.mockk)
}

configurations.all {
    resolutionStrategy {
        // renderer-coil3 桥传递依赖 coil 3.5.0（需要更高 compileSdk）→ 强制回 toml 的 3.4.0（原型实测）
        force("io.coil-kt.coil3:coil:${libs.versions.coil.get()}")
        force("io.coil-kt.coil3:coil-android:${libs.versions.coil.get()}")
        force("io.coil-kt.coil3:coil-compose:${libs.versions.coil.get()}")
        force("io.coil-kt.coil3:coil-compose-core:${libs.versions.coil.get()}")
        force("io.coil-kt.coil3:coil-core:${libs.versions.coil.get()}")
    }
}
