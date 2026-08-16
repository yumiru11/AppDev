# AppDev — Agent Guide

> 本文件是给 AI 编码代理的**项目操作手册**（AGENTS.md 开放标准，30+ 工具读取）。读 `docs/agents/workflow.md` 了解执行流程，读 `docs/agents/project-status.md` 了解当前进度，读 `docs/agents/dsh-guide.md` 了解 DSH 专用执行方式。
> 若你从零开始：先读本文件 → `plan.md`（技术规划）→ `docs/ui-design.md`（UI 规范）→ `docs/agents/project-status.md`（当前状态）。

## 项目身份

开发一个**功能全面的 Android GitHub 客户端**（轻量、流畅、全 Material You）。技术规划 = `plan.md`（41KB，必读），需求来源 = `request.txt`。应用名/包名仍为占位符：applicationId 与 namespace = `com.yumiru11.githubapp`（模块 namespace 用 `core.github_xxx` 下划线写法），产品定名后统一改。

**当前状态（2026-08-16）**：T1-T10 + T13 + T19/T20/T24 + T26 共 **15 票已完成并合入 main**。**README 渲染原型已完成并合入**（prototype/readme-comparison → main `97dfef8`）：原生主渲染 + WebView 兜底拍板（ADR-0007），FeatureDetector 已收紧（details/table 原生渲染）。剩余 11 票见 `docs/agents/project-status.md`。

## 核心决策（来自 plan.md，勿偏离）

- **无 Kotlin Multiplatform**、**无 Waydroid/虚拟机**：测试与截图全跑 Linux 纯 JVM（Robolectric + Roborazzi）
- **GraphQL 读优先（Apollo Kotlin 5）、REST 写优先（Retrofit 3/OkHttp 5）**；认证用 OAuth PKCE（AppAuth），PAT 仅开发者模式（fine-grained PAT 不支持 GraphQL → 自动降级 REST-only）
- **Markdown 分层渲染**：原生渲染器 mikepenz `multiplatform-markdown-renderer` **0.38.1** + KotlinTextMate 0.2.0 高亮（**主渲染，ADR-0007 拍板**；增强组件 EnhancedList/Paragraph/MarkdownImage/HtmlBlock/Table）；WebView 兜底（github-markdown-css + DOMPurify + markdown-it + highlight.js，Material You 变量注入——**真机 WebView 不支持 CSS color-mix，混色必须 Kotlin 预计算**）；shields 徽章需 **coil-svg + SvgDecoder**（Coil 默认无 SVG；SvgDecoder intrinsic 放大 ~10 倍，徽章固定高 20dp）；FeatureDetector 分流仅限 mermaid/数学/`<svg>/<canvas>/<iframe>/<math>`/超长
- **评论列表绝不用 WebView**；**token 绝不注入 WebView**；代码浏览/编辑用 Rosemoe Sora Editor
- i18n 从第一天落实：Compose 一律 `stringResource()`，禁止硬编码字符串（GitLight 教训）
- 版本目录（`gradle/libs.versions.toml`）单一事实来源；设计令牌、Konsist 架构测试从第一行代码开始

## 构建环境（本机事实）

| 项 | 值 |
|---|---|
| Gradle | **无全局 `gradle` CLI，一律 `./gradlew`** |
| Wrapper | `8.12-bin`，腾讯镜像（`~/.gradle/wrapper/dists` 已缓存） |
| JDK | 21（`gradle.properties` 已写 `org.gradle.java.home=/usr/lib/jvm/java-21-openjdk`，**勿改**） |
| Android SDK | `/home/zhiyi/Android/Sdk`（`local.properties`，**勿改**） |
| compileSdk | **36**（T5 合入时从 35 升；AGENTS 旧版写 35 是文档漂移，以代码为准） |
| 镜像 | 本机 `~/.gradle/init.d/mirror.gradle` 全局注入（不入库）；**仓库内 settings.gradle.kts 保持官方源，不要加回镜像** |
| 代理 | **需要代理时用户自己开**；禁止自行 `sudo mihomo`。push 被墙 → 停手告知用户 |

## 验证命令（质量门禁 = CI 同款，提交前必跑）

```bash
./gradlew spotlessCheck              # ktlint 格式（修正用 spotlessApply）
./gradlew detekt                     # 静态分析（config/detekt/detekt.yml 基线）
./gradlew konsistCheck               # 架构测试（Konsist 分层依赖方向）
./gradlew :app:lintDebug             # Android Lint（abortOnError）
./gradlew :app:testDebugUnitTest     # 单测
./gradlew :app:verifyRoborazziDebug  # 截图基准校验
./gradlew :app:assembleDebug         # 打 debug APK
```

快速验证（大量编辑后查 error，最快）：
```bash
./gradlew :app:compileDebugKotlin    # 全量编译入口（增量 ~13s）
./gradlew :core:markdown:compileDebugKotlin   # 单模块编译
./gradlew :core:test --tests "*XxxRepositoryTest*"   # 单模块单类
```

**⚠️ 铁律（血泪教训）**：
- 本地验证必须与 CI 门禁**命令级对齐**——只跑 compile/test 会漏 spotless/detekt，CI 必挂（T4/T6/T7 曾爆 9 个违规）。任何实现/修复任务验证命令**必须含 `spotlessCheck + detekt`**
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
- **PR 合并策略**：复杂/多提交修复波用 **merge commit 保留历史**（不 squash）；单一小改动可 squash。PR body 写 `Fixes #N` 自动关票
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
| 架构决策记录（ADR-0001~0006） | `docs/adr/` |
| 术语表 | `CONTEXT.md` |
| 真机走查反馈与状态 | `FEEDBACK.md` |
| 调研报告 | `docs/research/`（webview-material-you-fusion、highlight-engine-analysis） |
| Issue 管理 | `docs/agents/issue-tracker.md` |
| Triage 标签 | `docs/agents/triage-labels.md` |
| 领域文档布局 | `docs/agents/domain.md` |
