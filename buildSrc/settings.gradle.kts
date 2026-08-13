pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        // 镜像不入库：本机由 ~/.gradle/init.d/mirror.gradle 注入，CI 直连官方源
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

rootProject.name = "buildSrc"
