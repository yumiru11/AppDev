plugins {
    id("appdev.android.library")
}

android {
    namespace = "com.yumiru11.githubapp.core.designsystem"
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.material3)
    implementation(libs.compose.ui)
    // ThemeMode 枚举统一在 core:datastore（数据层），UI 依赖数据层方向
    implementation(project(":core:datastore"))
}
