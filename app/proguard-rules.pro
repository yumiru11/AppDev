# AppDev R8 keep rules（#169 / L15）
#
# 目标：release 变体开启 isMinifyEnabled + isShrinkResources 后功能不回退。
# 每段都注明「为什么需要」——多数依赖已自带 consumer rules，此处只补
# 反射/序列化/资源查找这类 R8 静态分析看不穿的入口。
#
# 验证方式：`./gradlew :app:assembleRelease`（本地带 KEYSTORE_FILE 属性时同时验签名），
# 再对产物跑 `aapt2 dump badging` / `unzip -l` 确认图标、组件、资源齐全。

# ── 基础元数据 ─────────────────────────────────────────────────────
# kotlinx.serialization / Room / Retrofit 都靠注解与泛型签名做反射查找
-keepattributes *Annotation*, InnerClasses, Signature, EnclosingMethod
# 保留行号，让 release 崩溃栈能与 mapping.txt 对上
-keepattributes SourceFile, LineNumberTable
-renamesourcefileattribute SourceFile

# ── kotlinx.serialization ──────────────────────────────────────────
# @Serializable 编译期生成 `Foo$$serializer`，运行期通过 serializer<T>() 反射取得。
# 导航路由（core:navigation 的 @Serializable AppRoute）与全部 DTO 走这条路径，
# 被裁掉即 "Serializer for class 'X' is not found" 崩溃。
-keepattributes RuntimeVisibleAnnotations, AnnotationDefault
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}
-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}
-if @kotlinx.serialization.Serializable class ** {
    public static ** INSTANCE;
}
-keepclassmembers class <1> {
    public static <1> INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclassmembers class **$serializer {
    *** INSTANCE;
    *** serializer(...);
}
-keep,allowobfuscation,allowshrinking class kotlinx.serialization.KSerializer
-keep,allowobfuscation,allowshrinking class kotlinx.serialization.descriptors.SerialDescriptor
-keep,allowobfuscation,allowshrinking class kotlinx.serialization.encoding.Decoder
-keep,allowobfuscation,allowshrinking class kotlinx.serialization.encoding.Encoder
# sealed 多态（AppRoute 的路由参数类）靠 @SerialName 字符串查找子类
-keepclassmembers class ** {
    @kotlinx.serialization.SerialName <fields>;
}

# ── Navigation Compose 类型安全路由 ────────────────────────────────
# AppRoute 的 KClass 由 NavType 在运行期反射实例化（serializer() 泛型查找）
-keep class com.yumiru11.githubapp.core.navigation.** { *; }

# ── Apollo Kotlin 5（GraphQL 读通道）──────────────────────────────
# 生成的 Operation/Adapter 通过反射注册；apollo 自带 consumer rules 只覆盖部分入口
-keep class com.apollographql.apollo.** { *; }
-keep class com.apollographql.cache.** { *; }
-dontwarn com.apollographql.apollo.**
-keepclassmembers class * extends com.apollographql.apollo.api.Operation {
    <init>(...);
}
-keep class * implements com.apollographql.apollo.api.Adapter { *; }

# ── Retrofit 3 + OkHttp 5（REST 写优先通道）───────────────────────
# Retrofit 自带 consumer rules（保留接口方法上的注解与泛型返回）；此处补 3.x 的
# suspend 适配与 kotlinx-serialization converter 的工厂查找。
-keepattributes RuntimeVisibleParameterAnnotations
-keep,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn retrofit2.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
# OkHttp 5 在 JVM 平台可选依赖（Android 上不加载），R8 会报 missing class
-dontwarn javax.annotation.**
-dontwarn kotlinx.serialization.internal.**

# ── Room（离线缓存：README/仓库元数据/搜索历史）───────────────────
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-keep @androidx.room.Entity class * { *; }
-dontwarn androidx.room.paging.**

# ── Hilt / Dagger ──────────────────────────────────────────────────
-dontwarn dagger.hilt.**
-keep class dagger.hilt.** { *; }

# ── AppAuth（PKCE 登录，ADR-0001）─────────────────────────────────
# RedirectUriReceiverActivity 由 manifest 声明、由框架按类名实例化；
# Configuration/CustomTabsIntent 里还有 Parcelable 反射读回。
-keep class net.openid.appauth.** { *; }
-dontwarn net.openid.appauth.**

# ── Sora Editor（代码浏览/编辑内核）──────────────────────────────
# 语言/主题由 TextMate 语法注册表按类名与服务加载，R8 无法静态追踪。
-keep class io.github.rosemoe.sora.** { *; }
-keep class org.eclipse.tm4e.** { *; }
-dontwarn io.github.rosemoe.sora.**
-dontwarn org.eclipse.tm4e.**

# ── KotlinTextMate（原生 Markdown 短文本高亮）─────────────────────
-keep class io.github.ivanmagda.** { *; }
-dontwarn io.github.ivanmagda.**

# ── mikepenz markdown renderer（评论/通知短文本）──────────────────
-keep class com.mikepenz.markdown.** { *; }
-dontwarn com.mikepenz.markdown.**

# ── Coil 3（图片/SVG 徽章）────────────────────────────────────────
-dontwarn coil3.**
-dontwarn okhttp3.internal.platform.**
# SvgDecoder 由 ImageLoader 组件注册表按类名创建，保留其构造入口
-keep class coil3.svg.** { *; }

# ── Timber（debug 日志树；release 由 R8 裁掉调用点）──────────────
-dontwarn org.jetbrains.annotations.**

# ── 数据模型（被 kotlinx.serialization 与 Retrofit 反射引用）───────
-keep class com.yumiru11.githubapp.core.githubrest.model.** { *; }
-keep class com.yumiru11.githubapp.core.data.model.** { *; }

# ── 可选依赖的 missing-class 兜底 ─────────────────────────────────
-dontwarn java.lang.invoke.**
-dontwarn org.slf4j.**
-dontwarn org.codehaus.mojo.**
