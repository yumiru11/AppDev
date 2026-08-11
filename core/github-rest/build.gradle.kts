plugins {
    id("appdev.android.library")
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.yumiru11.githubapp.core.github_rest"
}

dependencies {
    // REST 写优先通道：Retrofit 3（原生 suspend）+ kotlinx-serialization converter
    api(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.serialization.json)

    // Hilt（RestNetworkModule 装配 OkHttp/Retrofit）
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    // 测试：MockWebServer3 模拟 GitHub API（新 API 命名空间 okhttp3.mockwebserver3）
    testImplementation(project(":core:testing"))
    testImplementation(libs.mockwebserver3)
}
