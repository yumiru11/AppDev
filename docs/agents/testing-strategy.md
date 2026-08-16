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

- **Phase A（下一步）**：根 build 接 JaCoCo 0.8.13+ → `enableUnitTestCoverage` → 每模块 verification rules（宽松起步：核心模块 70-80%）→ 本地 `./gradlew jacocoTestReport` 可看
- **Phase B**：CI 加覆盖率任务 + PR 评论/报告上传 + diff coverage 门禁（新增代码 ≥80%）
- **Phase C**：按 §5 质量清单补齐核心模块测试（对照方法论文档的覆盖清单逐层补）
- **Phase D**：UI 自动化主流程（Compose UI Test 关键路径）+ Nightly 全量

## 8. 参考

- 方法论全文：`docs/agents/unit-test-coverage.md`
- JaCoCo 0.8.13 inline functions：https://github.com/jacoco/jacoco/pull/1670
- Kover 转向：https://github.com/Kotlin/kotlinx-kover/issues/746、#720
- diff coverage 插件：https://plugins.gradle.org/plugin/xyz.pavelkorolev.coverage.diff
- AGP 覆盖率官方文档：https://developer.android.com/studio/test/coverage-report
