# AppDev 测试策略（整合外部方法论 + 落地路线）

> 2026-08-16 定稿。方法论来源：`docs/agents/unit-test-coverage.md`（外部 AI，25 节）；工具选型：主代理调研
> （Kover→JaCoCo 官方转向、AGP 原生 coverage、diff coverage 插件）。技术栈：Kotlin 2.3.21 / AGP 8.7.3 /
> JUnit4 + MockK + Turbine + MockWebServer3 + Robolectric 4.16 + Roborazzi。

## 1. 目标

核心业务改代码时，测试能快速告诉你哪里坏了。不追求 100% 覆盖，追求：
- **核心逻辑高覆盖**（UseCase/Mapper/Repository/ViewModel 状态流转）
- **新增代码必须带测试**（增量覆盖优先于总量）
- **UI 主流程自动化**（Compose UI Test / Roborazzi 截图）
- **门禁化**（CI 强制，不是建议）

## 2. 分层测试策略（方法论文档核心，落地到我们的架构）

| 层 | 覆盖目标 | 手段 | 我们的现状 |
|---|---|---|---|
| UseCase / Mapper / 解析器 | 90%+ | 纯 JVM 单测（MockK） | GitHubLinkParser 55 测试 ✅；其余随票补 |
| Repository | 80%+ | Fake DataSource + MockWebServer3 | T5 已建（66 测试）✅ |
| ViewModel / UiState | 75%+ | TestDispatcher + Turbine | 各 feature 随票建 ✅（T19/T20 等） |
| DataSource（网络） | 70%+ | MockWebServer3 / Apollo MockServer | T5 ✅ |
| Room / DataStore | 高 | Robolectric 内存库 / Fake | T5 14 测试 ✅ |
| Compose UI | 行为断言 | Robolectric + Compose UI Test | 部分 ✅ |
| 视觉 | 关键页面 | Roborazzi 截图 | 10+ 基线 ✅（WebView 除外） |

## 3. 工具选型（主代理 2026-08 调研结论）

### 覆盖率工具：JaCoCo 0.8.13+（不用 Kover）
- **Kover 独立插件停止新功能**（kotlinx-kover#746）：将并入 Kotlin Gradle Plugin，IntelliJ agent 弃用，全面转 JaCoCo agent
- **官方推荐直接用 JaCoCo**（#729）：`kover { useJacoco("0.8.13") }`；**JaCoCo 0.8.13+ 支持 Kotlin inline functions**（jacoco#1670），覆盖精度追平 IntelliJ agent
- AGP 8.7 原生支持：`enableUnitTestCoverage = true` + `createDebugUnitTestCoverageReport`
- 多模块聚合：AGP experimental `android.experimental.reportAggregationSupport=true` → `createAggregatedCoverageReport`

### 增量覆盖率（diff coverage）：PR 门禁用插件
- **`xyz.pavelkorolev.coverage.diff`**（新，2025+，支持 JaCoCo/Kover XML，CI-agnostic）：
  ```bash
  ./gradlew reportDiffCoverage --diffSha=${{ github.event.pull_request.base.sha }} --report=build/reports/jacoco/test/jacocoTestReport.xml
  ```
- 或 `com.form.diff-coverage`（老但经典，failIfCoverageLessThan 0.9）
- 原则：老项目不要求总量达标，**只要求新增/修改代码覆盖率 ≥ 80%**

## 4. 覆盖率门禁建议值（按模块设，逐步收紧）

| 模块类型 | 目标（Phase B 起步） |
|---|---|
| core:navigation（解析器） | 90% |
| core:github-rest / graphql（解析+错误处理） | 80% |
| core:github-data（Repository/编排） | 80% |
| core:markdown（解析/渲染逻辑，非 Compose 部分） | 70% |
| feature 模块 ViewModel | 70% |
| UI 层（Composable） | 不设单测门禁（Roborazzi 兜底） |

## 5. 测试命名与质量（方法论文档强调）

- 命名：`methodName_scenario_expectedBehavior`（已约定，T5 先例）
- **每个业务方法至少覆盖**：正常 / 边界（空、单元素、分页首尾、0 值）/ 异常（null、非法输入、4xx/5xx、超时、Token 过期）/ 状态变化（Loading/Success/Error/Empty/Refresh/LoadMore/Offline）
- **高断言强度**：断言业务结果（输出/状态/调用次数/副作用），不用 assertNotNull 凑数
- **分支覆盖 > 行覆盖**：条件组合都要测（age<18/18/>18 × agreed true/false）
- 时间/随机数/ID/线程必须可注入（Clock/IdGenerator/Dispatcher 构造注入）——审计现有代码，缺的补

## 6. CI 分阶段（方法论 §22 落地）

| 阶段 | 内容 |
|---|---|
| **PR** | spotless + detekt + konsist + lint + 单测 + **JaCoCo 报告 + diff coverage 门禁** + verifyRoborazzi |
| **Merge 后** | 关键 Compose UI 测试（现有） |
| **Nightly**（后续） | 全量截图 / 多设备 / 性能基线（T25 阶段） |

## 7. 落地路线（4 阶段，对应方法论文档 §23）

- **Phase A（✅ 2026-08-16 完成）**：根 build 接 JaCoCo（AGP `enableUnitTestCoverage`，BuildType 级）→ `./gradlew :<模块>:createDebugUnitTestCoverageReport` 生成报告 → 基线数字（github-rest 23.5% / app 3.4%，注：AGP 报告只统计测试加载类，未加载类不计入——数字偏低，Phase B 定统计口径）→ **Phase B**：CI 门禁 + verification rules + diff coverage
- **Phase B（✅ 2026-08-16 完成，本票）**：版本锁定 + 分母口径 + 聚合报告/验证 + diff coverage 门禁，详见下方「Phase B 交付物」。
- **Phase C**：按 `docs/agents/testing-checklist.md` 分点清单补齐（A 纯逻辑 → B 数据层 → E ViewModel → G 可注入性，共 9 组 60+ 业务点）
- **Phase D**：UI 自动化主流程（Compose UI Test 关键路径）+ Nightly 全量

### Phase B 交付物（根 build.gradle.kts，契约任务名）

| 任务 | 契约 | 说明 |
|---|---|---|
| `coverageReport` | 根级 | 聚合全部模块 exec 的单一 JaCoCo 报告（XML+HTML，`build/reports/jacoco/coverageReport/`），依赖全部模块 `testDebugUnitTest` |
| `coverageVerify` | 根级 | 聚合各模块 `jacocoTestCoverageVerification` 门禁（LINE COVEREDRATIO），不达标即失败 |
| `:<mod>:jacocoTestReport` | 每模块 | 全量分母报告（T2 口径，AGP 自带 `createDebugUnitTestCoverageReport` 保留） |
| `diffCoverageCheck` | 根级 | PR 新增生产代码行覆盖率 ≥ 阈值（默认 0.80），用法 `./gradlew diffCoverageCheck -PdiffBaseSha=<base> [-PdiffCoverageThreshold=0.80]` |

**CI 用法**（GitHub Actions `pull_request` 事件）：
```bash
./gradlew coverageReport coverageVerify
./gradlew diffCoverageCheck -PdiffBaseSha=${{ github.event.pull_request.base.sha }}
```

**T1 版本锁定 0.8.13**：AGP 官方 DSL `testCoverage.jacocoVersion`（`TestCoverage` 接口，在 `CommonExtension` 新 DSL 上——legacy `AppExtension/LibraryExtension` 没有；根脚本须 `extensions.getByName("android") as CommonExtension<*,*,*,*,*,*>` 强转，`configure<CommonExtension>` 按注册类型精确匹配会失败）。此前尝试在 `plugins.withId("com.android.*")` 回调里改 jacoco 插件扩展不可行（插件尚未应用）。根聚合任务经 `apply(plugin = "org.gradle.jacoco")` 加载内置插件 + `the<JacocoPluginExtension>().toolVersion = "0.8.13"`——**注意 Gradle 内置 jacoco 插件 id 是 `org.gradle.jacoco`**（`plugins {}` 块无法无版本解析它，门户版 `org.jacoco` marker 在本机镜像下解析不到）。全链路统一 0.8.13（exec 合并跨版本会失败，jacoco#1471）。

**T2 分母口径（重要修正）**：实测证实 **AGP 8.7 的 `createDebugUnitTestCoverageReport` 并不排除未执行类**（feature/settings 报告中 40 类里有 19 类从未被测试加载，按 0% 计入）——Phase A 记录的"只统计测试加载类"是早期状态（github-rest 当时 23.5%/243 行，现为 78.2%/数百行）。真正的分母控制点：
- **synthetic 类**（`$$serializer`、`$WhenMappings`、Compose lambda 类）：JaCoCo 0.8.13 Analyzer 对 `ACC_SYNTHETIC` 类直接跳过（`Analyzer.analyzeClass` 显式 return），两侧报告均不含——设计如此（生成代码），无需处理；
- **Hilt 生成类**：本配置在 classDirs 统一排除（`**/*_Factory.class`、`**/Hilt_*.class`、`**/*_HiltComponents*.class`、`**/Dagger*Component*.class`、`**/hilt_aggregated_deps/**`、R/BuildConfig/Manifest）。
- **跨模块执行**：app/feature 测试会执行下层模块的类 → 单模块 exec 与聚合 exec 数字不一致。`coverageVerify` 契约要求"同一份聚合数据"→ 每模块报告/验证的 executionData 取全部模块 exec 的并集（惰性 provider），并显式 dependsOn 全部 `testDebugUnitTest`（否则 Gradle implicit-dependency 校验失败）。
- 统计口径：JaCoCo LINE 计数器——部分覆盖行同时计入 covered 和 missed（COVEREDRATIO = covered/(covered+missed)），不要用逐行 `ci>0` 简单相除，会虚高。

**T3 阈值表（2026-08-16 聚合口径实测，LINE COVEREDRATIO）**：

| 模块 | 阈值 | 实测 | 模块类型 |
|---|---|---|---|
| core:navigation | 0.94 | 96.1% | 逻辑 |
| core:github-data | 0.94 | 96.1% | 逻辑 |
| core:datastore | 0.84 | 86.7% | 逻辑 |
| core:github-graphql | 0.90 | 92.5% | 逻辑 |
| core:github-auth | 0.70 | 72.5% | 逻辑 |
| core:markdown | 0.42 | 44.2% | UI/渲染 |
| core:designsystem | 0.64 | 66.7% | UI/渲染 |
| core:github-rest | 0.76 | 78.2% | 网络 DTO |
| feature:repo | 0.37 | 39.0% | 逻辑（地板 70，Phase C 目标） |
| feature:profile | 0.29 | 30.9% | 逻辑（地板 60，Phase C 目标） |
| feature:notifications | 0.28 | 30.2% | 逻辑（地板 60，Phase C 目标） |
| feature:home | 0.28 | 30.7% | 逻辑（地板 60，Phase C 目标） |
| feature:issue | 0.21 | 23.7% | 逻辑（地板 50，Phase C 目标） |
| feature:settings | 0.17 | 19.6% | UI/渲染（地板 25，Phase C 目标） |
| app、feature:auth | — | — | 豁免（纯 UI 装配） |

阈值 = max(地板, 实测 − 2pt)，保证 CI 今天能过；实测低于地板的 6 个模块按实测 − 2pt 设阈值，地板作为 Phase C 收紧目标。无单元测试的模块（无 exec）跳过验证。

**T4 diff coverage**：自研任务（方案 c）而非 diff-coverage 插件——插件任务名不满足契约且 Gradle 8.12/Kotlin 2.3 兼容性需额外验证；自研零新依赖（JDK XML + git）。算法：`git diff --unified=0 base...HEAD` 解析新增行（仅 `src/main/` 下 .kt/.java）→ 与聚合 XML 的 `<sourcefile>/<line>` 逐行比对（`ci>0` 计覆盖）→ 未达阈值列出未覆盖文件与行号并失败。base 来源：`-PdiffBaseSha` > `DIFF_BASE_SHA` 环境变量 > `HEAD~1`。实测：HEAD~3 基线 diff（49 行新增，49.0% 覆盖）在默认 0.80 下正确失败，`-PdiffCoverageThreshold=0.40` 下正确通过。

## 8. 参考

- 方法论全文：`docs/agents/unit-test-coverage.md`
- JaCoCo 0.8.13 inline functions：https://github.com/jacoco/jacoco/pull/1670
- Kover 转向：https://github.com/Kotlin/kotlinx-kover/issues/746、#720
- diff coverage 插件：https://plugins.gradle.org/plugin/xyz.pavelkorolev.coverage.diff
- AGP 覆盖率官方文档：https://developer.android.com/studio/test/coverage-report
