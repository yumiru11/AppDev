# AppDev — Agent Guide

> 本文件是给 AI 编码代理的**项目操作手册**（AGENTS.md 开放标准，30+ 工具读取）。读 `docs/agents/workflow.md` 了解执行流程，读 `docs/agents/project-status.md` 了解当前进度，读 `docs/agents/dsh-guide.md` 了解 DSH 专用执行方式。
> 若你从零开始：先读本文件 → `plan.md`（技术规划）→ `docs/ui-design.md`（UI 规范）→ `docs/agents/project-status.md`（当前状态）。

## 项目身份

开发一个**功能全面的 Android GitHub 客户端**（轻量、流畅、全 Material You）。技术规划 = `plan.md`（41KB，必读），需求来源 = `request.txt`。应用名/包名仍为占位符：applicationId 与 namespace = `com.yumiru11.githubapp`（模块 namespace 用 `core.github_xxx` 下划线写法），产品定名后统一改。

**当前状态（2026-09-13 修复波末）**：`main@db1b696`。**T1–T26 全部合入**；ui-audit 8 票（#83–#90）、Task B 渲染架构切换（PR #70/#73）、**四张新缺陷票 #200–#203** 全部关闭；#166 / #167 已关闭（条目逐条对账）。本轮修复波共合入 **42 张 PR（#229–#271，剔除非 PR 的 issue #250，全部 squash）**：前半 #229–#255（26 张）、中段 **#256–#266（11 张）**、收尾 **#267–#271（5 张）**。收尾波 = **#267** 状态对账（#266 后）、**#268** 设计系统落地计划 + ADR-0010、**#269** 离线 KaTeX 数学渲染、**#270** AGP 9.1.1 迁移（Gradle 9.3.1 / compileSdk 37 / 全模块 lint 恢复，#170 兑现）、**#271** nightly APK 体积回归门禁。**在途 PR：#275 离线 Mermaid（Phase 2，KaTeX/Mermaid 计划最后一块）**。
**当前活动票三张**：**#26（T25 真机项，需用户配 Secrets）**、#1（Spec，常开）、#71（截图测试面板，勿关）。（#250 已关闭 —— 探针断言 bug 由 #252 修复。）
**已知真实缺陷：无。** **RepoDetail 仓库头整块不渲染（UI-C01）已由 #258 修复**：根因是 `headerHeightPx` 自锁 —— 首帧外层高 0dp → 内层 `onSizeChanged` 在 `maxHeight=0` 约束下只能测到 0 → 自然高度永远回填不上，头部被裁成 0 高。改为 `Modifier.layout` 以 `Constraints.Infinity` 在 layout 阶段测自然高度，首帧即按自然高度渲染；回归测试 `repoDetailScreen_success_rendersRepositoryHeaderBlock` 锁定，CI `repo-star` / `repo-actions` 帧已转绿（坏帧 2 → 0）。

> 🔴 **四份审计报告已入库（2026-09-12，开工前必读其一）**
> `docs/agents/` 下：`spec-audit-2026-09-11.md`（需求符合性 106 条判定 / 16 条缺口 / 20 条文档漂移）· `commit-audit-2026-09-11.md`（296 提交逐票核对）· `ui-audit-2026-09-11.md`（**含系统栏的 CI 真机帧**逐张读图，Roborazzi 基线看不到系统栏）· `markdown-consistency-2026-09-11.md`（GFM §2.3 逐条 + 三层回归说明）· `agp9-feasibility-2026-09-11.md`（工具链迁移实测）
>
> 🔴 **本轮新增入库（2026-09-12 / 09-13）**：`docs/agents/remaining-backlog-2026-09-12.md`（残余审计缺口收敛：已闭环钉死 / 仍待实现 / 过时反证；**#267–#271 后已再次收敛**，只留真开口）· `docs/research/katex-mermaid-offline-feasibility.md`（离线 KaTeX/Mermaid 体积实测）· `docs/adr/0009-graphql-read-path-deviation.md`（读路径全 REST 的架构决定）· `docs/design-system/implementation-plan.md`（SPEC-1 组件 / SPEC-2 间距 / UI-2 选中态：批次表 + 门禁 + DoD）· `docs/adr/0010-design-system-rollout.md`（设计系统分批迁移决策）

> ✅ **OAuth client id 注入已完成（PR #239），真机 PKCE 只剩你填一次值**
> `app/build.gradle.kts:11-48` 三级解析、**先命中先取**：Gradle 属性 `-PoauthClientId` → `local.properties:oauthClientId` → 环境变量 `OAUTH_CLIENT_ID`，写入 `BuildConfig.OAUTH_CLIENT_ID`；由 app 装配层 `OAuthConfigModule` 构造 `OAuthConfig`（core:github-auth 不感知 BuildConfig，Konsist 禁 core→app）。真实值只进构建产物、不落库（`local.properties` 已 gitignore）。
> **配置方法**：`./gradlew ... -PoauthClientId=Iv1.xxxx`，或在 `local.properties` 写一行 `oauthClientId=Iv1.xxxx`，或 `export OAUTH_CLIENT_ID=Iv1.xxxx`。CI 截图不消费此值（走 `SCREENSHOT_TOKEN` 注入只读 PAT）。
> **未配置时的行为**：回落到 `OAuthConfig.PLACEHOLDER_CLIENT_ID = "YOUR_OAUTH_APP_CLIENT_ID"`——构建/测试照常，仅**真机 PKCE 授权失败**；用 `OAuthConfig.isConfigured` 判定。⏳ 登录入口的「未配置」提示**未在 feature 层实现**（`isConfigured` 目前仅生产装配层注释与测试消费），若需要另开票。不阻塞模拟器截图与全部测试。

> 📌 **本轮已修复的三个 P0（勿再当成未修）**
> 1. **T23 白屏** ✅ PR #220 —— `MainActivity` 补 `branchesScreen` / `createPullRequestScreen` 接线；并新增 **`NavHostWiringTest`** 结构化守卫（断言 19 个 screen lambda 无默认空实现残留）
> 2. **Repos 分区 padding 恒 0** ✅ PR #215 —— 三键导航栏下末行被底栏压住；附 16 例**几何数值**回归测试（含反例灵敏度）
> 3. **通知面板同像素叠印** ✅ PR #219 —— 根因是 API 30 模拟器走**降级路径**（无模糊）+ 0.75 底色 → 下层透印；改为降级路径不透明

> 🧭 **此后必须遵守的方法学（本轮/修复波实测教训，代价很大）**
> 1. **判断 Kotlin 库成员可用性，`javap` 不够** —— JVM `public` 可能是 Kotlin `internal`（`javap` 看不到 `@Metadata` 那一层）。**唯一可靠做法：用真实 Kotlin 编译探针引用目标符号、跑 `compileDebugKotlin`、读错误原文。** 本轮在 material3 1.4.0 与 1.5.0-alpha18 上各撞一次。
> 2. **断言/门禁必须做「红→绿双向验证」** —— 任何守卫/断言先证明「缺陷形态下必红」。修复波两次抓到恒真守卫：**i18n lint canary 自引用断言**（期望值取自被检查的同一清单 → 恒真；已改为独立 `REQUIRED_I18N_LINT_RULES` + 运行期 canary，见 `buildSrc/.../AppDevI18nLint.kt:43,66,72`）、**`repo-actions` 探针空洞**（仓库头缺失仍假绿，补 `desc:"Avatar"` 闸门后才转红，#252）。RTL 旧写法 `@Config(qualifiers="...ldrtl")` 亦是「永远绿的假测试」，改组合内显式注入 + SHA 互斥断言（#245）。**不经红证明的守卫会以「在跑但什么都没查」的形态上线。**
> 3. **含无限动画的屏必须用 `captureScreenshotDeterministic`** —— Roborazzi `captureRoboImage(content)` 截图前会 `ShadowLooper.idle()` 排空主 looper，而无限动画（转圈 / 下拉刷新指示器）每帧经 Choreographer 续订 → `idle()` 永不返回 → verify/record 挂死（`:feature:issue` 7min+ 挂起根因，已修并纳入 CI verify）。`core:testing` 的 `captureScreenshotDeterministic`（冻结 `mainClock.autoAdvance=false` + 固定 `advanceTimeBy` + 手工 `decorView.draw(Canvas)` + `Bitmap.captureRoboImage` 不做 idle 等待）才稳。**已核实：普通 `testDebugUnitTest`（未开 record/verify）下 `captureRoboImage` 直接静默返回、不落盘** → 测试绿 **≠** 拍了帧，验收必须看产物（`.github/scripts/verify-screenshots.sh` 的帧闭包断言）。
> 4. **截图基线只能由 CI canonical 录制** —— `record-screenshots.yml`（`Record screenshots (CI canonical)`）是唯一权威录制环境；本机 `recordRoborazziDebug` **禁止**（渲染与 runner 非逐字节相同，本机录的基线 CI verify 全红）。**录制前必须先 rebase 到最新 main**（**已证实**：#256 即 rebase 至 #259 后录制，仅重录语义确实变化的 4 帧）。
> 5. **截图探针语义 + 时间炸弹** —— Compose `TabRow` 选中态是 `selected`，M3 `SegmentedButton` 是 `checkable/checked`（radio 语义，`selected` 恒 false，pr-diff 两帧假红根因）；`ExtendedFAB` 文案不进 uiautomator dump，须 `try_tap_fab` 结构性定位（**判据必须限制右下角 x ≥ 0.7w 且 y ≥ 0.85h**，放宽到「底部最大可点区域」会误点 diff 行，`adb-helpers.sh:840-842`）；M3 `OutlinedTextField` 的 placeholder 仅在聚焦且为空时渲染，**不得当就绪信号**（#250）。截图/测试夹具**禁止写死绝对时间戳**，一律相对时间（`isoDaysAgo`，#246）。探针不得静默降级为 `opt:`。
> 6. **截图捕获必须禁网（离线 ImageLoader）** —— `AsyncImage` 的真实网络往返会让录制/校验两次运行不可复现（`ProfileScreen_light` 曾因头像时有时无单独 verify 红）。`captureScreenshotDeterministic` 在捕获期 `installOfflineImageLoader()`（拦截器短路 http(s) 图片 → Coil `ErrorResult` → 渲染空白，与既有全部基线一致），`finally` reset（#256）。机制由临时 Robolectric 探针实证后删除。

## 核心决策（来自 plan.md，勿偏离）

- **无 Kotlin Multiplatform**、**无 Waydroid/虚拟机**：测试与截图全跑 Linux 纯 JVM（Robolectric + Roborazzi）
- **GraphQL 读优先（Apollo Kotlin 5）、REST 写优先（Retrofit 3/OkHttp 5）**；认证用 OAuth PKCE（AppAuth），PAT 仅开发者模式（fine-grained PAT 不支持 GraphQL → 自动降级 REST-only）
- **Markdown 分层渲染**：**WebView 主渲染**（README/Issue 正文——服务端 HTML 优先 + 离线 GFM markdown-it 降级两级，ADR-0007 拍板；github-markdown-css + DOMPurify + markdown-it + highlight.js，Material You 变量注入——**真机 WebView 不支持 CSS color-mix，混色必须 Kotlin 预计算**；**数学公式走离线 KaTeX 0.18.7**，#269：DOMPurify 清洗**之后**渲染，仅 woff2 字体，`assets/webview/katex/` ≈ +0.32 MiB，配置未放宽；**Mermaid 图走离线 @mermaid-js/tiny 11.17.2**（IIFE 单文件，同样清洗后渲染，`securityLevel:'strict'` + 图数上限 10；**Chromium ≥94 门禁**——class static block 是解析期语法级失败，Kotlin 按 UA 决定注入 + JS 语法探针双层兜底，不满足回退普通代码块，`assets/webview/mermaid/` ≈ +0.64 MiB））；评论列表/通知短文本保持原生（**铁律「评论列表绝不用 WebView」不变**）；FeatureDetector 保留但 README 分流判定不再使用；增强组件链（EnhancedMarkdownViewer 等）继续服务短文本；shields 徽章需 **coil-svg + SvgDecoder**（Coil 默认无 SVG；SvgDecoder intrinsic 放大 ~10 倍，徽章固定高 20dp）
- **评论列表绝不用 WebView**；**token 绝不注入 WebView**；代码浏览/编辑用 Rosemoe Sora Editor
- i18n 从第一天落实：Compose 一律 `stringResource()`，禁止硬编码字符串（GitLight 教训）
- 版本目录（`gradle/libs.versions.toml`）单一事实来源；设计令牌、Konsist 架构测试从第一行代码开始
- **`material3` 显式 pin 在 `1.5.0-alpha18`**（不走 BOM）—— **M3 Expressive** 的唯一可用窗口；决策与撤除条件见 `docs/adr/0008-material3-alpha18-pin.md`：
  - **为什么 pin**：`MotionScheme`（Expressive 弹簧物理）· `LoadingIndicator`（形变加载）· `WavyProgressIndicator` · `SplitButtonLayout` · `ButtonGroup` · `Medium/LargeFlexibleTopAppBar` 在 **1.4.0 上是 Kotlin `internal`**（拿不到），从 1.5.0-alpha 起才 public
  - **为什么曾停在 alpha18**：**1.5.0-alpha19+ 要求 `minCompileSdk=37` + `minAGP=9.1.0`**（AAR 元数据实测）。**该门禁已随 #270 的 AGP 9.1.1 / compileSdk 37 迁移清除**（alpha19+ 的技术前提已满足），但 pin 仍**有意保留 alpha18**，等 1.5.0 stable 或明确要跟 alpha 线再动（撤除条件见 ADR-0008）
  - **何时撤掉**：1.5.0 stable 回归某个 compose-bom 后删除本 pin（回到纯 BOM 托管）。AGP 9 迁移**已完成（#270）**；若确需 alpha 线新特性，可在此基础上直接跟 alpha19+/stable，不撤则维持现状
  - **代价**：alpha18 无 `FloatingToolbar` 家族；alpha 期改名已实测 2 例（`FlexibleTopAppBar` 这个名字在 1.5 线**根本不存在**；`SplitButtonDefaults.leadingButtonShape` 该版没有）
  - **只 pin 单个 artifact、不动 BOM**（BOM 覆盖 Compose 全家，改它是把整条线拖进 alpha）

## 构建环境（本机事实）

| 项 | 值 |
|---|---|
| Gradle | **无全局 `gradle` CLI，一律 `./gradlew`** |
| Wrapper | `9.3.1-bin`，腾讯镜像（`~/.gradle/wrapper/dists` 已缓存） |
| JDK | 21（`gradle.properties` 已写 `org.gradle.java.home=/usr/lib/jvm/java-21-openjdk`，**勿改**） |
| Android SDK | `/home/zhiyi/Android/Sdk`（`local.properties`，**勿改**） |
| 工具链 | **AGP 9.1.1 / Gradle 9.3.1 / KSP 2.3.12 / Hilt 2.59.2 / built-in Kotlin**（#270 从 AGP 8.7.3 升；不再应用 `org.jetbrains.kotlin.android`，AGP 9 会硬拒绝） |
| compileSdk | **37** / buildTools **36.0.0**（#270 从 36 升；android-37.0 平台已安装） |
| targetSdk | **35**（`:app` 显式写死，不跟随 compileSdk；并设 `android.sdk.defaultTargetSdkToCompileSdkIfUnset=false`——AGP 9 新默认会让**库模块**取 37，Robolectric 4.16.1 报 `targetSdkVersion=37 > maxSdkVersion=36`） |
| 镜像 | 本机 `~/.gradle/init.d/mirror.gradle` 全局注入（不入库）；**仓库内 settings.gradle.kts 保持官方源，不要加回镜像** |
| 代理 | **需要代理时用户自己开**；禁止自行 `sudo mihomo`。push 被墙 → 停手告知用户 |

## 验证命令（质量门禁 = CI 同款，提交前必跑）

> ⚠️ **本机一律 `--no-daemon`**（2026-09-12 用户明确要求：daemon 会占内存、有 OOM 风险）。
> 本机内存 15GB、多 worktree 并行时曾出现 load 30+ / 可用内存 1GB；daemon 各自 `-Xmx2048m` 且**不会随构建结束释放**。
> `--no-daemon` 单次构建略慢（无热 JVM），但内存可控 —— **这是本机默认，不要省掉**。
> **CI 不受影响**：workflow 里已有自己的 `--no-daemon`/runner 配置，不要改动 CI 侧。
> 若见到残留 daemon：`./gradlew --stop`。

```bash
./gradlew --no-daemon spotlessCheck      # ktlint 格式（修正用 spotlessApply）
./gradlew --no-daemon detekt             # 静态分析（config/detekt/detekt.yml 基线）
./gradlew --no-daemon konsistCheck       # 架构测试（Konsist 分层依赖方向；无匹配测试已改为硬失败，#260）
./gradlew --no-daemon lintDebug         # Android Lint 全模块（abortOnError；#270 后检测器全恢复，0 disable）
./gradlew --no-daemon :app:testDebugUnitTest    # 单测
./gradlew --no-daemon coverageVerify     # JaCoCo 覆盖率硬门禁（22 模块阈值见 build.gradle.kts coverageThresholds；无 exec 数据 = 硬失败，#260）
./gradlew --no-daemon :app:verifyRoborazziDebug # 截图基准校验（app；模块需逐个列）
./gradlew --no-daemon :app:assembleDebug # 打 debug APK
```

> **截图 verify 覆盖面（CI 同款，`ci.yml:192-205`）**：`app` + **13 个模块** —— `:core:ui` / `:core:designsystem` / `:core:markdown` / `:feature:auth` / `:feature:issue` / `:feature:repo` / `:feature:notifications` / `:feature:pullrequest` / `:feature:home` / `:feature:search` / `:feature:editor` / `:feature:profile` / `:feature:settings`；录制名单（`record-screenshots.yml`）与 verify 名单镜像。**帧闭包断言**：`.github/scripts/verify-screenshots.sh`（#260）要求 ≥1 张 PNG、无 0 字节 PNG、`screenshots.sh` 声明的每帧必有产出或显式处置标记（`.skipped.txt`），帧数下限 28。
>
> **两条新增硬门禁（2026-09-13）**：① **lint 覆盖面守卫**（`ci.yml`，AGP 9 收口）断言 0 跳过 registry + 0 `UnknownIssueId` + 0 `disable +=`（31 条旧 disable 已删、48 个 lint error 已在源码修复）；② **APK 体积回归**（`#271`）：`.github/scripts/check-apk-size.sh` 消费 `.github/apk-size-budget.properties`（baseline `7585167` B，budget = baseline + 256 KiB = `7847311` B），release APK 超预算即 nightly 判红。

快速验证（大量编辑后查 error，最快）：
```bash
./gradlew --no-daemon :app:compileDebugKotlin    # 全量编译入口（增量 ~13s）
./gradlew --no-daemon :core:markdown:compileDebugKotlin   # 单模块编译
./gradlew --no-daemon :core:markdown:testDebugUnitTest --tests "*XxxRepositoryTest*"   # 单模块单类（注意：没有 :core 聚合项目）
```

**⚠️ 铁律（血泪教训）**：
- 本地验证必须与 CI 门禁**命令级对齐**——只跑 compile/test 会漏 spotless/detekt，CI 必挂（T4/T6/T7 曾爆 9 个违规）。任何实现/修复任务验证命令**必须含 `spotlessCheck + detekt`**
- **覆盖率门禁同理必跑**：CI 有 `coverageVerify` 硬门禁（22 个有阈值模块的 LINE ≥ `coverageThresholds` 阈值；阈值已按 #261 后的**真实**覆盖率棘轮到实测值，#263）。新增/删除生产代码后必须跑 `coverageVerify`。**无 exec 数据不再静默 SKIP**——声明了阈值的模块若没有单测执行数据，任务**硬失败**（#260）。JaCoCo 排除按【编译类名】匹配（`*EditorViewKt*` 精确到 Composable 编译产物，逻辑 ViewModel 不再被误伤，#247）；`MarkdownEditorViewModel` 等有单测的逻辑类必须留在分母内
- **Robolectric 的覆盖率是真数据（#261）**：JaCoCo agent 默认 `inclnolocationclasses=false` 会跳过 Robolectric `SandboxClassLoader` 定义的无 CodeSource 应用类 → 覆盖率假 0。修复 = `includeNoLocationClasses=true` + `includes=com/yumiru11/*`（`build.gradle.kts` 的 `configureRobolectricCoverage`）。**「必须纯 JVM 可测才进覆盖率」的旧约束已不成立**
- 构建输出**禁止用 grep/tail/head 过滤后反复重跑**——一次跑完看完整输出
- **不要用 LSP**（本机 kotlin-ls 冷启动失败/超时）——验证一律以 Gradle 输出为准
- `recordRoborazziDebug` 本机极慢（1000s+ 曾卡死）——**默认禁止跑**；截图相关任务需先问用户
- edit 工具超时消息**不可信**——超时后必须 grep/read 验证是否落地再重试，防重复写入

## 测试体系（全部 Linux JVM 免模拟器）

- 金字塔：单测（JUnit 4 + MockK + Turbine）→ 集成（MockWebServer / Apollo MockServer，不碰真实网络）→ Compose UI（Robolectric Native Graphics）→ 截图（Roborazzi）
- 测试命名：**`methodName_scenario_expectedBehavior`**（如 `guestWelcomeScreen_lightTheme_matchesBaseline`）
- 测试基建在 `core:testing`（MainDispatcherRule / ScreenshotTest / GitHubFakes）；新增测试依赖一律 `testImplementation(project(":core:testing"))`
- 截图基准路径：`app/src/test/screenshots/*.png`（入库）；CI `verifyRoborazziDebug` 校验
- **覆盖率（Phase A 进行中）**：JaCoCo 0.8.13+（不用 Kover——官方已转向，kotlinx-kover#746）；AGP `enableUnitTestCoverage`；diff coverage 门禁（新增代码 ≥80%）——完整策略见 `docs/agents/testing-strategy.md`

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
- Konsist 校验分层依赖方向（`app/src/test/kotlin/.../konsist/ArchitectureTest.kt`）；「`core:model` 禁止 import android」实际落地于 core:data 与 core:github-* 的 model 包（package 含 `model` 段）
- 状态管理：单 Activity + Navigation Compose、ViewModel 暴露 `StateFlow<UiState>`、写操作走事件通道（乐观更新/失败回滚/Snackbar）

## 编码规范（ktlint + detekt 强制）

- Kotlin 包名**禁下划线**（`core.githubauth` 而非 `core.github_auth`）；`const val` 必须 SCREAMING_SNAKE；文件名须匹配唯一顶层声明（MatchingDeclarationName）
- detekt 业务合理违规用 `@file:Suppress("RuleName")` + 理由注释（T3 先例）；测试源码豁免 FunctionNaming 以容纳下划线
- **零硬编码颜色**：一律 `MaterialTheme.colorScheme.*` + `ExtendedColors`；**零硬编码文案**：一律 `stringResource`（en + zh-rCN，含 contentDescription）
- **全应用禁 emoji 图标**（用户硬性要求）——Alert 卡片图标/空态插图/按钮一律矢量图标（Octicons SVG 或 Material Symbols）
- 布局一律 `start/end`（RTL 兼容），代码块保持 LTR

## Git 工作流（docs/agents/workflow.md 有完整版）

- 分支命名：`feature/tX-<kebab>`（如 `feature/t12-repo-management`）
- **提交信息 = Conventional Commits**：`type(scope): description`（type: feat/fix/refactor/chore/docs/test/perf）
- **PR 合并策略**：默认 **squash**（用户偏好线性历史；2026-09-12/13 修复波 42 张全 squash）；确需保留多提交历史的复杂修复波可用 merge commit。PR body 写 `Fixes #N` 自动关票
- **分支保护**：必需检查**只有 `Quality Gate`**（`required_status_checks.contexts = [\"Quality Gate\"]`，`strict=true`）；截图 job 名为 **`Screenshots (emulator + adb)`** 且**非必需**（红榜要读但不挡合并）。`strict=true` → PR 落后 main（BEHIND）时 `gh pr merge` 会拒，改用 REST：`gh api -X PUT repos/yumiru11/AppDev/pulls/<n>/merge -f merge_method=squash`（**docs-only PR 无 CI 检查，均走 REST 合并**）
- **铁律：不提交 main、不 push main 之外的分支**；worktree 并行时子代理 prompt 必须带 WORKDIR
- 参考仓库（~/dev/）：`rikkahub`（原生 Markdown 参考，**AGPL-3.0 只参考思路零复制**）、`PiliPlus`（卡片风格）、`XMSLEEP`（MD3）、`gh4a`（WebView markdown + Trending 数据源）

## 相关文档（指针式索引）

| 主题 | 文档 |
|---|---|
| 技术规划（41KB） | `plan.md` |
| **当前进度 / 剩余票** | `docs/agents/project-status.md` |
| **执行流程（ticket/分支/PR/验证）** | `docs/agents/workflow.md` |
| **DSH × V4 Pro 使用指南** | `docs/agents/dsh-guide.md` |
| UI 设计规范（权威，2026-08-15 拍板版） | `docs/ui-design.md` |
| **UI 审查问题清单（2026-08-21 快照，缺陷/挂账/提案）** | `docs/ui-audit-2026-08-21.md` |
| **全量审计报告（2026-09-06，9 张分类票来源）** | `docs/agents/task-audit-2026-09-06.md` |
| **残余缺口清单（2026-09-12，修复波后收敛）** | `docs/agents/remaining-backlog-2026-09-12.md` |
| **设计系统落地计划（SPEC-1/2 + UI-2）** | `docs/design-system/implementation-plan.md` |
| 架构决策记录（ADR-0001~0010） | `docs/adr/`（0009 GraphQL 读路径 / 0010 设计系统分批迁移） |
| 术语表 | `CONTEXT.md` |
| 真机走查反馈与状态 | `FEEDBACK.md` |
| 调研报告 | `docs/research/`（webview-material-you-fusion、highlight-engine-analysis、katex-mermaid-offline-feasibility） |
| Issue 管理 | `docs/agents/issue-tracker.md` |
| Triage 标签 | `docs/agents/triage-labels.md` |
| 领域文档布局 | `docs/agents/domain.md` |
