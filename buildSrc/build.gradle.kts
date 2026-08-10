plugins {
    `kotlin-dsl`
}

// 仓库统一由 buildSrc/settings.gradle.kts 管理（FAIL_ON_PROJECT_REPOS，
// dl.google.com 不可达，镜像优先）

dependencies {
    implementation("com.android.tools.build:gradle:8.7.3")
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.10")
    implementation("org.jetbrains.kotlin.plugin.compose:org.jetbrains.kotlin.plugin.compose.gradle.plugin:2.4.10")
}
