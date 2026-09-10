plugins {
    id("appdev.android.library")
    alias(libs.plugins.roborazzi)
}

android {
    namespace = "com.yumiru11.githubapp.core.designsystem"

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.material3)
    implementation(libs.compose.ui)
    // Haze：backdrop blur（issue #83）——GlassSurface 的 hazeEffect 消费方
    implementation(libs.haze)
    // ThemeMode 枚举统一在 core:datastore（数据层），UI 依赖数据层方向
    implementation(project(":core:datastore"))

    // Material Symbols ImageVector（AppIcon 按 iconStyle 在 outlined/rounded/filled
    // 三档间切换，issue #168 / UI12）。base = MaterialSymbols 命名空间对象；
    // 各家族 = 扩展属性（MaterialSymbols.Rounded.Home 等）。
    // outlined / *-filled 家族版本目录无别名 → 坐标直写，但版本仍取 catalog
    // 单一事实来源（core/database 的 room-paging 同款先例）。
    implementation(libs.icons.material.symbols.base.cmp)
    implementation(libs.icons.material.symbols.rounded.cmp)
    implementation("com.composables:icons-material-symbols-outlined-cmp:${libs.versions.icons.get()}")
    implementation("com.composables:icons-material-symbols-outlined-filled-cmp:${libs.versions.icons.get()}")
    implementation("com.composables:icons-material-symbols-rounded-filled-cmp:${libs.versions.icons.get()}")

    // 测试：core:testing 已 api 导出 JUnit4/Robolectric/Roborazzi/compose-test
    testImplementation(project(":core:testing"))
}
