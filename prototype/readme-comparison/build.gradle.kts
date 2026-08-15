plugins {
    id("appdev.android.library")
    alias(libs.plugins.roborazzi)
}

android {
    namespace = "com.yumiru11.githubapp.prototype.readmecomparison"

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all {
                it.systemProperties["robolectric.pixelCopyRenderMode"] = "hardware"
            }
        }
    }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.material3)
    implementation(libs.compose.ui)

    // 现有两套渲染链路（prototype 只在一次性模块消费，不改 feature:repo）
    implementation(project(":core:markdown"))
    implementation(project(":core:navigation"))

    testImplementation(project(":core:testing"))
    testImplementation(libs.junit)
    testImplementation(kotlin("test"))
}
