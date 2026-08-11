plugins {
    id("appdev.android.library")
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// Room schema 导出：MigrationTestHelper（Robolectric）校验 v1 建库与导出 schema 一致
ksp {
    arg("room.schemaLocation", "$projectDir/src/test/assets/schemas")
}

// 非 UI 模块：禁用 Compose 编译器插件（同 core:navigation 的处理方式），
// 避免无 Compose 运行时依赖时编译失败。
composeCompiler {
    targetKotlinPlatforms.set(emptySet())
}

android {
    namespace = "com.yumiru11.githubapp.core.database"

    // 显式关闭 compose（约定插件默认开启）
    buildFeatures {
        compose = false
    }

    // MigrationTestHelper 从 assets 读导出 schema（src/test/assets/schemas）
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

// AGP 在 buildFeatures.compose=true 时创建 kotlin-extension 配置并把 Compose 编译器
// 塞进编译器 classpath；清空该配置依赖以彻底禁用 Compose 编译器（afterEvaluate 见 core:navigation 注释）。
afterEvaluate {
    configurations.matching { it.name == "kotlin-extension" }.configureEach {
        dependencies.clear()
    }
}

dependencies {
    // Room（离线缓存：仓库元数据 + ETag）。room-runtime 必须 api：AppDatabase 继承
    // RoomDatabase（父类型在 room-runtime），app 测试编译/Hilt KSP 解析 DAO 与
    // @Inject AppDatabase 时需要该类型可见（implementation 不透传，2026-08-12 实测）。
    api(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // Hilt（DatabaseModule 装配 AppDatabase/DAO）
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    // 测试：Robolectric + room-testing（MigrationTestHelper）+ coroutines-test
    testImplementation(project(":core:testing"))
    testImplementation(libs.room.testing)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(kotlin("test"))
}
