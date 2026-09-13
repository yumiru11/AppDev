plugins {
    id("com.android.application")
    // AGP 9 built-in Kotlin：不再应用 org.jetbrains.kotlin.android（AGP 9 会硬拒绝）。
    // Compose 编译器插件仍需显式应用——built-in Kotlin 只替代 kotlin-android。
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    // compileSdk 37：AGP 9.1.x 支持的最高 API 即 37.0（android-37.0 平台已安装）；
    // material3 1.5.0-alpha19+ 的 AAR 元数据要求 minCompileSdk=37，升到这里后解除升级门控。
    compileSdk = 37
    // AGP 9.x 的最低 SDK Build Tools 就是 36.0.0
    buildToolsVersion = "36.0.0"

    defaultConfig {
        minSdk = 26
        // targetSdk 保持 35（不跟随 compileSdk）：AGP 9 的
        // android.sdk.defaultTargetSdkToCompileSdkIfUnset=true 只影响未显式设置的模块，
        // 本项目显式写 35 不受新默认值影响；升 targetSdk 属运行期行为变更，独立走查另票。
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
        // #170 已随 AGP 9.1.1 关闭：lint 32.1.1 带上了 Kotlin 2.3 时代的 Analysis API，
        // Compose/Lifecycle 库内检测器全部恢复加载（CI 守卫断言跳过 registry 数 = 0）。
        // 本文件不再允许出现 disable +=（CI 守卫断言 disable 数为 0）。
        abortOnError = true

        // ── i18n 规则（plan.md §11.4 / 需求审计 §9.4）─────────────────────────────
        // 契约、与另外两道 i18n 守卫的分工、防退化机制见
        // buildSrc/src/main/kotlin/AppDevI18nLint.kt。
        enableI18nRules()
        verifyI18nRulesEnabled(project.path)
    }
}

// built-in Kotlin 的 jvmTarget 默认取 android.compileOptions.targetCompatibility（已设 VERSION_17），
// 故原先按 KotlinAndroidProjectExtension 类型 configure 的 jvmTarget 块已删除——既冗余，
// 该类型也不再是 built-in Kotlin 下 "kotlin" 扩展的注册类型。
