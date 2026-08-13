pluginManagement {
    repositories {
        mavenLocal()
        // 阿里云镜像（Maven Central + Google 代理，覆盖全面，避免 Maven Central 429）
        maven("https://maven.aliyun.com/repository/public/")
        maven("https://maven.aliyun.com/repository/google/")
        // Gradle Plugin Portal（Gradle 特有插件，如 kotlin-android、spotless、detekt）
        gradlePluginPortal()
        // 腾讯云镜像（兜底）
        maven("https://mirrors.cloud.tencent.com/maven/maven2/")
        maven("https://mirrors.cloud.tencent.com/maven/google/")
        // 最终兜底：官方源
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        // 阿里云镜像（Maven Central + Google 代理，覆盖全面，避免 Maven Central 429）
        maven("https://maven.aliyun.com/repository/public/")
        maven("https://maven.aliyun.com/repository/google/")
        // 腾讯云镜像（兜底）
        maven("https://mirrors.cloud.tencent.com/maven/maven2/")
        maven("https://mirrors.cloud.tencent.com/maven/google/")
        // 最终兜底：官方源
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

rootProject.name = "AppDev"

include(":app")

include(":core:common")
include(":core:designsystem")
include(":core:data")
include(":core:ui")
include(":core:navigation")
include(":core:github-graphql")
include(":core:github-rest")
include(":core:github-auth")
include(":core:github-data")
include(":core:markdown")
include(":core:editor")
include(":core:database")
include(":core:datastore")
include(":core:testing")

include(":feature:auth")
include(":feature:home")
include(":feature:repo")
include(":feature:issue")
include(":feature:pullrequest")
include(":feature:search")
include(":feature:editor")
include(":feature:settings")
include(":feature:notifications")
include(":feature:profile")
