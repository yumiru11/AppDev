# AppDev — Agent Guide

## 项目身份与现状

开发一个**功能全面的 Android GitHub 客户端**（轻量、流畅、全 Material You）。技术规格就是 `plan.md`（41KB 技术规划，必读），需求来源 `request.txt`。

**⚠️ 当前仓库状态：骨架已完成（2026-08-09）。** 25 模块 + buildSrc 约定插件已落地，`:app:compileDebugKotlin` 与 `:app:assembleDebug` 已验证全绿（Kotlin 2.3.21 / AGP 8.7.3 / Compose BOM 2026.06.01）。模块全为空壳，等待按 ticket 填充。**应用名/包名仍是占位符**：applicationId 与 namespace = `com.yumiru11.githubapp`（模块 namespace 用 `core.github_xxx` 下划线写法），产品定名后统一改。

**首个工程任务起点**：见「Gradle 脚手架」与「依赖选型」两节；下一步是拆 ticket（GitHub Issues 尚未创建仓库，需先 `gh repo create yumiru11/AppDev`）。

**关键参考实现：`~/dev/GitLight`（同一套规划的已落地版本，2 模块架构）。** 所有「照抄 GitLight 配置」均指该项目。

## 核心决策（来自 plan.md，勿偏离）

- **无 Kotlin Multiplatform**、**无 Waydroid/虚拟机**：测试与截图全跑 Linux 纯 JVM（Robolectric + Roborazzi）
- **GraphQL 读优先（Apollo Kotlin 5）、REST 写优先（Retrofit/OkHttp）**；认证用 OAuth PKCE（AppAuth），PAT 仅开发者模式（fine-grained PAT 不支持 GraphQL → 自动降级 REST-only）
- **Markdown 分层渲染**：主路径 mikepenz `multiplatform-markdown-renderer` 0.43.0（+m3、+code）；README/长文档/复杂 GFM 走 WebView 兜底（GitHub 服务端 `/markdown` HTML + 同一套 Material You CSS 令牌）；所有链接统一 `GitHubLinkParser` → 应用内导航
- **评论列表绝不用 WebView**；**token 绝不注入 WebView**；代码浏览/编辑用 Rosemoe Sora Editor
- i18n 从第一天落实：Compose 一律 `stringResource()`，禁止硬编码字符串（GitLight 的教训：全部 UI 文本硬编码被列为质量级 bug）
- 版本目录（`gradle/libs.versions.toml`）单一事实来源；设计令牌、Konsist 架构测试从第一行代码开始

## 构建环境（实测于 2026-08-09）

| 项 | 值 | 说明 |
|---|---|---|
| Gradle | **无全局 `gradle` CLI，一律 `./gradlew`** | 不装全局 gradle |
| Wrapper | `8.12-bin`，`distributionUrl=https://mirrors.cloud.tencent.com/gradle/gradle-8.12-bin.zip` | 走腾讯镜像；~/.gradle/wrapper/dists 已缓存 8.12-all/bin |

| Android SDK | `/home/zhiyi/Android/Sdk`（`local.properties` 里写 `sdk.dir=`） | platforms: android-35、android-37；build-tools: 35.0.0、36.0.0 |

| JVM target | 17（compileOptions + kotlinOptions 一致） | |


## Gradle 脚手架（模仿 GitLight 的配置）

新建项目时直接参照 `~/dev/GitLight` 以下文件，**以及已落地的 buildSrc 约定插件**：


- **根 `build.gradle.kts` 不要重复声明 AGP/Kotlin/Compose 插件**（它们在 buildSrc 类路径上，重复声明报 "already on the classpath"）；只留 serialization/ksp/hilt/apollo `apply false`

- `gradle/wrapper/gradle-wrapper.properties` → 上面表格的腾讯镜像 8.12-bin
- `gradle.properties` → 必含：`org.gradle.java.home=/usr/lib/jvm/java-21-openjdk`、`org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8`、`android.useAndroidX=true`、`android.nonTransitiveRClass=true`、`org.gradle.parallel=true`、`org.gradle.caching=true`
- `local.properties` → `sdk.dir=/home/zhiyi/Android/Sdk`（不入库）
- `settings.gradle.kts` → pluginManagement + dependencyResolutionManagement（**保持官方源**：google()/mavenCentral()/gradlePluginPortal()；镜像由本机 `~/.gradle/init.d/mirror.gradle` 注入不入库）+ `repositoriesMode.FAIL_ON_PROJECT_REPOS`
- `gradle/libs.versions.toml` → 版本目录单一事实来源。具体版本见下方「依赖选型」表（先进基线：AGP 8.7.3 / Kotlin 2.3.21 / Compose BOM 2026.06.01 / Hilt 2.57.2 / Apollo 5.0.1 / Retrofit 2.11.0 / OkHttp 4.12.0 / Coil 3.4.0 / Navigation 2.8.4；不要使用 GitLight 老基线）
- 模块 `build.gradle.kts` 模式（大多数已被约定插件覆盖，仅以下需要手写）：
  - **okhttp 版本强制**：已统一在根 `build.gradle.kts` 的 `subprojects { configurations.all { resolutionStrategy { force(...) } } }`——防 Apollo KMP 传递依赖拉高版本（勿删）
  - Apollo schema（仅 `core:github-graphql`）：`apollo { service("github") { ... introspection { headers.put("Authorization", "Bearer ${System.getenv("GITHUB_TOKEN") ?: ""}") } } }`
  - JUnit 4（非 GitLight 的 JUnit 5）；packaging 豁免已在 appdev.android.application 约定插件内置

## 依赖选型（2026-08-09 已调研并落地到 libs.versions.toml）

**工具链对齐原则**：库以各自最新稳定版本为准，但 Kotlin 编译器版本必须 ≥ 所有依赖的编译版本（KMP metadata 向后兼容限制）。GitLight 的 Kotlin 2.1.20 / Compose BOM 2025.01.01 对 mikepenz 0.43.0（Kotlin 2.3.20 构建）太老，**新项目升级 Kotlin**。

| 分类 | 选型（artifact:version） | 依据/备注 |
|---|---|---|
| Kotlin | **2.3.21** | 已在 Central 确认；mikepenz 0.43.0 以 2.3.20 构建；AGP 兼容 8.2.2–9.0 |
| Gradle wrapper | **8.12-bin（腾讯镜像）** | AGP 8.7.3 最低要求 8.9，8.12 已缓存，无需下载 |
| AGP | **8.7.3** | GitLight 实测稳定 + 本机 JDK 21 固定配套；AGP 9.x 内置 Kotlin 大改（`kotlin-android` 插件废弃），新项目不值得首批踩 |
| KSP | **2.3.11** | KSP2 已改纯版本号（不再 `kotlin-版本-ksp-版本` 双段式） |
| Compose BOM | **2026.06.01** | mikepenz 0.43.0 以 Compose 1.10.3 构建；2026.06.01 已在骨架首建验证 |
| Hilt | **2.57.2** | 2.59+ 只支持 AGP 9；2.58.x 未上 Central；AGP8 可用最高 2.57.2（GitLight 同款）。若 Kotlin 2.3 metadata 报错，补 `ksp("org.jetbrains.kotlin:kotlin-metadata-jvm:2.3.0")` |
| Markdown | `com.mikepenz:multiplatform-markdown-renderer` + `-m3` + `-code` + `-coil3`，**0.43.0** | 用法：`rememberMarkdownState` + `Markdown(state)`、`markdownComponents{...}` 自定义段落/alert、`Coil3ImageTransformerImpl` 喂图片 |
| 图片 | Coil **3.4.0**（不要盲目 3.5.0：它用 Kotlin 2.4 构建） | `io.coil-kt.coil3:coil-compose` + `coil-network-okhttp`（Coil 3 默认不带网络） |
| **图标（核心）** | `com.composables:icons-material-symbols-{outlined,rounded,sharp,*-filled}-{android,cmp}:2.2.1` | = marella.github.io / Google Material Symbols 同一套图标（marella 只是 SVG/font 分发站）。纯 Android 用 `-android`（vector drawable，IDE 预览）或 `-cmp`（ImageVector 对象）。**不用 deprecated 的 material-icons-extended** |
| GitHub 专属图标 | **Octicons 手挑 SVG 入库**（primer/octicons，MIT）→ core:designsystem 的 vector drawable | merge/draft PR/branch/fork/issue/discussion/workflow 等 ~15 个；无现成 Compose 库 |
| AppAuth | `net.openid:appauth:0.11.1` | OAuth PKCE（2021 后未更，标准实现，稳定） |
| Apollo | 5.0.1 | 5.0.0 的 patch（GitLight 验证同线）；若 Kotlin 2.3 冲突再降 5.0.0 |
| Retrofit/OkHttp | **3.0.0 / 5.4.0**（2026-08-11 由 dependabot 升大版本，CI 已验证；Retrofit 3 原生 suspend + 自动 HttpException；OkHttp 5 拆 JVM/Android artifact，旧 API 二进制兼容） | MockWebServer 用新 artifact `mockwebserver3`（不再旧 `mockwebserver`）；`resolutionStrategy force` 统一在根 build.gradle.kts 强制 okhttp |
| Room / Paging | **2.8.4** / **3.5.0** | plan 新增（GitLight 无 Room） |
| 截图/UI 测试 | **Roborazzi 1.71.0** + **Robolectric 4.15.1**（Native Graphics）+ `roborazzi-compose` + `roborazzi-junit-rule` | `@GraphicsMode(GraphicsMode.Mode.NATIVE)`；任务 `recordRoborazziDebug`/`verifyRoborazziDebug` |
| 单测 | JUnit 4 + MockK 1.14.11 + Turbine + MockWebServer + Apollo MockServer | plan §12.1；注意与 GitLight 的 JUnit 5 不同（Robolectric 生态用 JUnit4 顺）； |
| 代码编辑 | Sora Editor：`io.github.Rosemoe.sora-editor:bom:0.23.6`（artifact 名是 `bom` 不是 `editor-bom`；含 `editor` + `language-textmate`） | 0.23.6 稳定版；2026 迁 Central Portal 后新版本可能换 namespace |
| WebView 兜底 | WebViewAssetLoader + DOMPurify + Shiki + markdown-it（assets 打包） | plan §2.9-2.10；npm 产物进 app/assets |

**验证顺序（scaffold ticket 第一步）**：`compileDebugKotlin` 全绿 → 拿 renderer 0.43.0 + coil3 跑一个真 App 验证 metadata 兼容。若 renderer 或 coil 与 Kotlin 2.3.20 不兼容，先升至 Kotlin 2.3.21/2.4.x 而非降依赖。

## 验证命令（实测耗时，依快慢排序）

```bash
./gradlew :app:compileDebugKotlin   # 大量编辑后查 error——最快（增量 ~13s，全量 ~40s）
./gradlew test                      # 全部单测
./gradlew :core:test --tests "*XxxRepositoryTest*"   # 单模块单类
./gradlew :app:assembleDebug        # 打 debug APK（不跑 Lint）
./gradlew :app:build                 # 完整验证：编译+Lint+单测（分钟级）
./gradlew :core:downloadApolloSchema # 拉取 GitHub GraphQL schema（需 GITHUB_TOKEN 环境变量）
```

关键经验（GitLight 实测）：**编译 error 用 Gradle 判**（快且全模块）；**源码警告只有 LSP 通道能看**（Gradle 编译输出 0 条警告）；LSP 冷启动 3-5 分钟，daemon 保持 warm；`assembleDebug` 不跑 Lint。CLI 用 `./gradlew` 而非直接 `lint`。

## 质量门禁（T1：CI 同款命令，提交前本机跑）

```bash
./gradlew spotlessCheck              # ktlint 格式检查（全模块；修正用 spotlessApply）
./gradlew detekt                     # 静态分析（config/detekt/detekt.yml 基线）
./gradlew konsistCheck               # 架构测试（Konsist，过滤 :app 单测 konsist 包；T2 起含分层依赖方向护栏，违规即失败）
./gradlew :app:lintDebug             # Android Lint（abortOnError）
./gradlew :app:testDebugUnitTest     # 单测
./gradlew :app:verifyRoborazziDebug  # 截图基准校验（基准图入库，recordRoborazziDebug 更新）
```

- Spotless 未挂进 `check/assemble`（`isEnforceCheck = false`），不拖慢日常构建；CI 显式调用
- 截图基准路径：`app/src/test/screenshots/*.png`（入库；不用 build/outputs，因 build/ 不进版本库），失败时 CI 上传 diff artifact
- **⚠️ 铁律（2026-08-12 血泪教训）：本地验证必须与 CI Quality Gate 命令级对齐——子代理/自主开发只跑 `compileDebugKotlin + testDebugUnitTest` 会漏掉 spotless/detekt，CI 必挂**。T4/T6/T7 三票 Wave 曾因派发时只给 compile/test 导致 9 个违规（包名下划线、常量命名、MatchingDeclarationName、NestedBlockDepth/ReturnCount/TooGenericExceptionCaught）全在 CI 才爆。任何实现/修复任务的验证命令必须含 `spotlessCheck + detekt`（派子代理时写进 prompt）。ktlint 常见坑：Kotlin 包名禁下划线（用 githubauth 不用 github_auth）、const val 必须 SCREAMING_SNAKE、文件名须匹配唯一顶层声明；detekt 业务合理违规用 `@file:Suppress` + 理由注释（T3 先例）。区分两类 CI 红：环境差异（镜像/网络，本地绿 CI 红，如 aliyun 502）vs 验证覆盖不足（命令没跑，本次 spotless/detekt 属此类）

## 测试体系（plan.md §12，全部 Linux JVM 免模拟器）

```bash
./gradlew :app:testDebugUnitTest            # 单测 + Robolectric + Compose 行为测试
./gradlew :app:recordRoborazziDebug         # 生成/更新截图基准（app/src/test/screenshots/*.png，基准入库）
./gradlew :app:verifyRoborazziDebug         # 校验截图
./gradlew :app:verifyRoborazziDebug --tests "*Markdown*"   # 只跑 MD 快照
./gradlew :app:konsistCheck :app:detekt :app:lintDebug
```

- 金字塔：单测（JUnit + MockK + Turbine）→ 集成（MockWebServer / Apollo MockServer，MockWebServer 模拟 GitHub API，测试不碰真实网络）→ Compose UI（Robolectric 4.10+ Native Graphics + `testOptions.unitTests { isIncludeAndroidResources = true }`）→ 截图（Roborazzi）
- 测试命名：**所有新增测试一律遵守 `methodName_scenario_expectedBehavior`**（如 `guestWelcomeScreen_lightTheme_matchesBaseline`）；detekt 已豁免测试源码的 FunctionNaming 检查以容纳下划线
- 测试基建在 `core:testing`（MainDispatcherRule / ScreenshotTest 截图基类 / GitHubFakes 工厂骨架），新增测试依赖一律 `testImplementation(project(":core:testing"))`，不重复声明 Robolectric/Roborazzi

## CI/CD（plan.md §13）

PR + main push 检查：`setup-java@v4`（temurin 21）→ `gradle/actions/setup-gradle@v4` → `spotlessCheck detekt lintDebug konsistCheck` → `testDebugUnitTest` → `verifyRoborazziDebug` → `assembleDebug` → 上传 APK artifact（concurrency cancel-in-progress）。tag v* → 签名（keystore 在 GitHub Secrets）→ Release draft。

## 模块架构（plan.md §10.1）

```text
app/                       UI + 导航装配
core/                      common, designsystem, data, ui, navigation, github-graphql,
                           github-rest, github-auth, github-data, markdown, editor,
                           database, datastore, testing
feature/                   auth, home, repo, issue, pullrequest, search, editor,
                           settings, notifications, profile
```

- `core:github-*` 只依赖网络与模型，不依赖 UI；`core:markdown` 内部隔离 WebView；`core:editor` 隔离 Sora 依赖
- feature 之间只通过 navigation 深链交互，互不引用
- Konsist 校验分层依赖方向（T2 落地于 `app/src/test/kotlin/.../konsist/ArchitectureTest.kt`）；工单措辞「`core:model` 禁止 import android 包」的实际落地：骨架无独立 core:model 模块，规则施加于 core:data 与 core:github-* 的 model 包（package 含 `model` 段），未来建立 core:model 后平移
- 状态管理：单 Activity + Navigation Compose、ViewModel 暴露 `StateFlow<UiState>`、写操作走事件通道（乐观更新/失败回滚/Snackbar）

## 相关文档

- `plan.md` — 完整技术规划（Markdown 分层方案 §2、技术栈 §3、认证 §4、Material You §5、Issue/PR 页结构 §6、写功能 §7、编辑器 §8、模块 §10、i18n §11、测试 §12、CI §13、性能 §14、安全 §15）
- `~/dev/GitLight/` — 参考实现（gradle 配置模板、AGENTS.md 的验证经验、issues.md 踩坑记录）
- `CONTEXT.md` — 项目术语表（多模块共用术语在此登记；grill-with-docs 会话维护）
- `docs/adr/` — 架构决策记录（编号 `NNNN-标题.md`；当前 0001-0005 覆盖认证/存储/主题/渲染分层；新决策经 grill-with-docs 会话产生）
- `docs/ui-design.md` — UI 设计规范（导航/动效/图标/玻璃拟真；T6 消费）

## Agent skills

### Issue tracker

Issues and specs live as GitHub Issues（gh CLI）。See `docs/agents/issue-tracker.md`.

### Triage labels

Five canonical roles, default vocabulary: `needs-triage` · `needs-info` · `ready-for-agent` · `ready-for-human` · `wontfix`. See `docs/agents/triage-labels.md`.

### Domain docs

Single-context layout — root `CONTEXT.md` + `docs/adr/`. See `docs/agents/domain.md`.