plugins {
    `kotlin-dsl`
}

// 仓库统一由 buildSrc/settings.gradle.kts 管理（FAIL_ON_PROJECT_REPOS，
// dl.google.com 不可达，镜像优先）

dependencies {
    implementation("com.android.tools.build:gradle:8.7.3")
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:2.3.21")
    implementation("org.jetbrains.kotlin.plugin.compose:org.jetbrains.kotlin.plugin.compose.gradle.plugin:2.3.21")
}

// javapoet 版本强制：AGP 8.7.3 传递依赖 javapoet 1.10.0（ClassName.canonicalName 为字段），
// 而 Hilt 2.57 插件需要 1.13.0（canonicalName() 方法）。buildSrc 是根构建 classloader 的
// parent（parent-first 加载），不在这里 force 会 NoSuchMethodError（2026-08-12 实测）。
configurations.all {
    resolutionStrategy {
        force("com.squareup:javapoet:1.13.0")
    }
}
