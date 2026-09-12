import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    // compileSdk 36：OkHttp 5.4（okhttp-android）AAR 要求 minCompileSdk 36（2026-08-12 实测）；
    // android-36 平台已从腾讯镜像安装（本机无 dl.google.com 访问）；AGP 8.7.3 对 36 仅警告不阻塞
    compileSdk = 36
    buildToolsVersion = "35.0.0"

    defaultConfig {
        minSdk = 26
        targetSdk = 35
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // Apollo Kotlin 5.0.0 KMP JAR（normalized-cache 等）携带重复的 commonMain/ 元数据，
    // 运行时无用，整体排除避免 mergeDebugJavaResource 重复路径冲突（GitLight 实测）
    packaging {
        resources {
            excludes += "commonMain/**"
            excludes += "META-INF/kotlin-project-structure-metadata.json"
        }
    }

    lint {
        // Compose/Lifecycle 库内 lint 检测器 disable 列表在 app/build.gradle.kts（模块级，
        // 与 #170 的根因注释放在一起）；这里只放**全应用模块共有**的 i18n 契约。
        abortOnError = true

        // ── i18n 规则（plan.md §11.4 / 需求审计 §9.4）─────────────────────────────
        // 契约、与另外两道 i18n 守卫的分工、防退化机制见
        // buildSrc/src/main/kotlin/AppDevI18nLint.kt。
        enableI18nRules()
        verifyI18nRulesEnabled(project.path)
    }
}

extensions.configure<KotlinAndroidProjectExtension>("kotlin") {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}
