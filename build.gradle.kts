// 注意：AGP / kotlin-android / kotlin-compose 三个插件已由 buildSrc 类路径提供（见 buildSrc/build.gradle.kts），
// 根 build 里不可重复声明（否则报 "already on the classpath with an unknown version"）。
// 其余插件（serialization/ksp/hilt/apollo/spotless/detekt）不在 buildSrc，可以正常在这里 apply false。
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
