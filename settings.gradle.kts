pluginManagement {
    repositories {
        // ⚠️ 阿里云镜像不再写在这里：本机由 ~/.gradle/init.d/mirror.gradle 注入（不入库），
        // CI/GitHub Actions 直连官方源（阿里云在 CI 上 502，2026-08-12 实测）。
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
        // ⚠️ 阿里云镜像不再写在这里：本机由 ~/.gradle/init.d/mirror.gradle 注入（不入库），
        // CI/GitHub Actions 直连官方源（阿里云在 CI 上 502，2026-08-12 实测）。
        google()
        mavenCentral()
    }
}

rootProject.name = "AppDev"

include(":app")

include(":prototype:readme-comparison")

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
