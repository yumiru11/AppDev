// 注意：AGP / kotlin-android / kotlin-compose 三个插件已由 buildSrc 类路径提供（见 buildSrc/build.gradle.kts），
// 根 build 里不可重复声明（否则报 "already on the classpath with an unknown version"）。
// 其余插件（serialization/ksp/hilt/apollo）不在 buildSrc，可以正常在这里 apply false；
// spotless/detekt 需要作用到根项目本身（全局格式化/静态分析），因此直接 apply。
// javapoet 冲突处理见 buildSrc/build.gradle.kts（buildSrc 是根构建 classloader 的 parent）。
plugins {
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.apollo) apply false
    alias(libs.plugins.spotless)
    alias(libs.plugins.detekt)
}

// ── Spotless（ktlint 格式化，全模块生效）───────────────────────────────
// checkOnTask = false：不挂进 check/assemble，避免拖慢日常增量构建；CI 显式调 spotlessCheck
spotless {
    isEnforceCheck = false
    kotlin {
        target("**/*.kt", "**/*.kts")
        targetExclude("**/build/**")
        ktlint(libs.versions.ktlint.get())
        trimTrailingWhitespace()
        endWithNewline()
    }
}

// ── Detekt（静态分析，全项目 Kotlin 源码）─────────────────────────────────
detekt {
    buildUponDefaultConfig = true
    allRules = false
    config.setFrom(files("$rootDir/config/detekt/detekt.yml"))
}

tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
    source = files("app/src", "core", "feature", "buildSrc/src").asFileTree
    exclude("**/build/**")
    reports {
        html.required.set(true)
        sarif.required.set(false)
        md.required.set(false)
        xml.required.set(false)
    }
}

// ── 版本统一强制（防止传递依赖拉高/拉低关键版本）──────────────────────
// okhttp：Apollo KMP 传递依赖会拉高版本，必须强制单一版本（见 AGENTS.md「依赖选型」）
// kotlin-metadata-jvm：Hilt 2.57.2 编译器自带 metadata 2.2，Kotlin 2.3 的 @Metadata
// 版本 2.3.x 会报 “maximum supported version is 2.2.0” → force 到 Kotlin 同版本（AGENTS.md 指引）
// ── JaCoCo 覆盖率（Phase A，testing-strategy.md）：Kover 官方已转向 JaCoCo（kotlinx-kover#746）
// 0.8.13+ 支持 Kotlin inline functions（jacoco#1670）。AGP 8 的 enableUnitTestCoverage 是
// BuildType 级属性，启用时 AGP 自动应用 JacocoPlugin；版本经 testCoverage.jacocoVersion 指定。
subprojects {
    plugins.withId("com.android.application") {
        extensions.configure<com.android.build.gradle.AppExtension> {
            buildTypes.getByName("debug") { enableUnitTestCoverage = true }
        }
    }
    plugins.withId("com.android.library") {
        extensions.configure<com.android.build.gradle.LibraryExtension> {
            buildTypes.getByName("debug") { enableUnitTestCoverage = true }
        }
    }
}

subprojects {
    configurations.all {
        resolutionStrategy {
            force("com.squareup.okhttp3:okhttp:${libs.versions.okhttp.get()}")
            force("org.jetbrains.kotlin:kotlin-metadata-jvm:${libs.versions.kotlin.get()}")
        }
    }
}
