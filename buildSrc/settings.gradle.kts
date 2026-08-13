pluginManagement {
    repositories {
        mavenLocal()
        // 腾讯云镜像（阿里云 2026-08 全 404，实测腾讯云可用）
        maven("https://mirrors.tencent.com/nexus/repository/maven-public/")
        maven("https://mirrors.tencent.com/nexus/repository/google-maven/")
        // 兜底：Gradle Plugin Portal 官方源
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenLocal()
        // 腾讯云镜像（阿里云 2026-08 全 404，实测腾讯云可用）
        maven("https://mirrors.tencent.com/nexus/repository/maven-public/")
        maven("https://mirrors.tencent.com/nexus/repository/google-maven/")
        // 兜底：官方源
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

rootProject.name = "buildSrc"
