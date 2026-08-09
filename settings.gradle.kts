pluginManagement {
    repositories {
        // dl.google.com / maven.google.com 在本机不可达（HTTP 000，2026-08-09 实测），
        // androidx/google 工件必须走阿里云镜像；google() 保留作兜底
        maven("https://maven.aliyun.com/repository/google")
        maven("https://maven.aliyun.com/repository/central")
        maven("https://maven.aliyun.com/repository/gradle-plugin")
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven("https://maven.aliyun.com/repository/google")
        maven("https://maven.aliyun.com/repository/central")
        google()
        mavenCentral()
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