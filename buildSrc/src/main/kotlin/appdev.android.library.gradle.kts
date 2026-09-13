plugins {
    id("com.android.library")
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
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    lint {
        abortOnError = true
        // #170 已随 AGP 9.1.1 关闭：lint 32.1.1 带上了 Kotlin 2.3 时代的 Analysis API，
        // Compose/Lifecycle 库内检测器全部恢复加载并正常运行（library 模块不再有
        // IncompatibleClassChangeError 崩溃）。
        // 本文件不再允许出现 disable +=（CI 守卫断言 disable 数为 0）。

        // ── i18n 规则（plan.md §11.4 / 需求审计 §9.4）─────────────────────────────
        // 用户可见文案 99% 在 feature/core，所以这四条**必须配在约定插件**才对全模块生效。
        // 契约与「与另外两道 i18n 守卫的分工」见 buildSrc/src/main/kotlin/AppDevI18nLint.kt。
        // 下一行同时是配置期自检：被删掉 → 任何 Gradle 调用都直接失败（不留静默降级的口子）。
        enableI18nRules()
        verifyI18nRulesEnabled(project.path)
    }
}

// built-in Kotlin 的 jvmTarget 默认取 android.compileOptions.targetCompatibility（已设 VERSION_17），
// 故原先按 KotlinAndroidProjectExtension 类型 configure 的 jvmTarget 块已删除——既冗余，
// 该类型也不再是 built-in Kotlin 下 "kotlin" 扩展的注册类型。

// ── library 模块的 lint 任务已恢复启用（2026-09-12，本票实测）────────────────────────────
//
// 历史：这里曾有一段 `tasks.configureEach { if (name.startsWith("lint")) enabled = false }`，
// 理由是「AGP 8.7.3 的 LintJarApiMigration 与新版 Compose AAR 内置 lint 检查器二进制不兼容
// （IncompatibleClassChangeError），崩溃发生在检测器注册/迁移期，disable 规则无法绕过」，
// 于是库模块的 lint 任务被整体关掉、质量门禁只跑 `:app:lintDebug`。
//
// 实测结论（本票，AGP 8.7.3 + Gradle 8.12）：
// 1. 该崩溃在 **:app 上同样发生、且同样是非致命**的 —— `:app:lintAnalyzeDebug` 会打印
//    `java.lang.NegativeArraySizeException` 于 `LintJarApiMigration.migrateClassNames`，
//    但 lint 继续跑完并写出报告（BUILD SUCCESSFUL）。library 模块同理，不再需要禁用任务。
// 2. 真缺口被这次实测抓到：`:app:lintDebug` **不分析依赖模块**（`checkDependencies` 默认 false，
//    其 lint model 的 resDirectories/javaDirectories 只含 app 自己的 `src/{main,debug}/…`）。
//    在 `feature:home` 放一条「en 有 zh 无」的字符串 + 一个硬编码 XML 布局，`:app:lintDebug`
//    报告里 0 命中；`:feature:home:lintDebug` 才报出来。→ 「只跑 app lint」= feature/core 的
//    资源与源码完全不进任何门禁（需求审计 §10 P1）。
//
// 因此恢复库模块 lint 任务；`.github/workflows/ci.yml` 的 lint 步骤也相应扩到全模块。
