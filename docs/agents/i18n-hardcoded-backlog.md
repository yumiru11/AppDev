# i18n 硬编码文案守卫 —— 上线基线明细（backlog）

配套代码：`app/src/test/kotlin/com/yumiru11/githubapp/konsist/I18nHardcodedStringGuardTest.kt`
（同目录 `I18nParityTest` 只管「en 的 key 在 zh-rCN 是否存在」，不看 Kotlin 源码。）

## 结论（干净 main 实测）

| 项 | 数量 | 处置 |
|---|---|---|
| 扫描范围 | **352 个 main 源文件 / 2020 个字符串字面量** | `core/<module>/src/main`、`feature/<module>/src/main`、`app/src/main` 全部 `.kt` |
| **真泄漏（用户可见硬编码文案）** | **0** | 无需新增 `stringResource` 条目 |
| 合法豁免（`ALLOWED`，逐条给理由） | 2 | 见 §A |
| dev-only 预览夹具（结构性豁免） | 12 | 见 §B |
| 日志 / 异常消息（结构性豁免） | 4 | 见 §C |
| 禁改文件里扫出的既有命中（`KNOWN_DEBT`） | **0** | 清单为空，见 §D |

守卫上线时 **绿**，不阻塞任何并行任务；它的作用是拦住**新增**泄漏，不是清历史欠账。

## §A 合法豁免（`CopyGuard.ALLOWED`）

| 文件:行 | 字面量 | 理由 |
|---|---|---|
| `core/markdown/.../GitHubTextMateTheme.kt:50` | `M3 Dark fallback` | TextMate 语法高亮主题的 `name` 字段（shiki/TextMate 配置标识，不渲染给用户） |
| `core/markdown/.../GitHubTextMateTheme.kt:50` | `M3 Light fallback` | 同上（亮色分支） |

同族的 `"M3 Dark"` / `"M3 Light"`（`M3TextMateTheme.kt:147`）与 `"M3 Editor"`
（`core/editor` 的两个 View）**不触发**守卫——「UI 文件内整句常量」要求字面量含空格且 ≥2 个 ≥2 字母的词。
这是启发式的边界，不是精确语义，故一并在此登记，避免后人误以为漏检。

## §B dev-only 预览夹具（`@Preview` 函数体 / 路径或文件名含 `preview`）

**这些不是用户可见文案**：`@Preview` 夹具只服务 IDE 预览，不进用户使用路径。
守卫对它们做**结构性豁免**（见测试类 KDoc「不会抓什么」），代价是：有人把真实文案写进预览夹具不会被拦。

| 文件:行 | 字面量 | 位置 |
|---|---|---|
| `feature/home/.../ui/FeedRow.kt:182` | `perf(list): Paging itemKey 迁移与模型稳定性标注` | `@Preview FeedRowPreviewLight/Dark` |
| `feature/issue/.../IssueListScreen.kt:353` | `列表滚动时已有卡片闪烁重排` | `@Preview IssueRowPreviewLight` |
| `feature/issue/.../IssueListScreen.kt:372` | `WebView 正文渲染与原生评论混排的基线对齐` | `@Preview IssueRowPreviewDark` |
| `feature/pullrequest/.../PullRequestListScreen.kt:341` | `docs(ui): 归档 UI 审查报告并立项 ui-audit 修复批` | `@Preview PullRequestRowPreviewLight` |
| `feature/pullrequest/.../PullRequestListScreen.kt:360` | `feat(markdown): WebView 主渲染切换收尾` | `@Preview PullRequestRowPreviewDark` |
| `feature/notifications/.../ui/NotificationsPanelPreviews.kt:46` | `feat(notifications): 通知面板完整形态` | `previewGroups()` 夹具数据（`*Previews.kt`） |
| `feature/notifications/.../ui/NotificationsPanelPreviews.kt:55` | `fix(designsystem): backdrop blur 修复` | 同上 |
| `feature/search/.../preview/SearchRowPreviews.kt:30` | `功能全面的 Android GitHub 客户端` | `@Preview RepositoryRowPreviewLight` |
| `feature/search/.../preview/SearchRowPreviews.kt:49` | `功能全面的 Android GitHub 客户端` | `@Preview RepositoryRowPreviewDark` |
| `feature/search/.../preview/SearchRowPreviews.kt:84` | `perf(list): Paging itemKey 迁移与模型稳定性标注` | `@Preview SearchIssueRowPreviewLight` |
| `feature/search/.../preview/SearchRowPreviews.kt:103` | `feat(markdown): WebView 主渲染切换收尾` | `@Preview SearchIssueRowPreviewDark` |

### 若要收紧（**不建议现在做**）
删掉 `CopyGuard.isExempt` 里的两条预览豁免（`literal.line in previewLines` 与
`PREVIEW_PATH.containsMatchIn(path)`），上面 11 条会立刻变红。届时的最小代价是：
把夹具数据收敛到 `core/testing` 风格的共享 fixture 或在 `ALLOWED` 里逐条登记——
但两种做法都只是把豁免搬家，**不会提升对真实泄漏的拦截能力**，却会让新增预览持续摩擦。
因此本票选择保留结构性豁免，并在此显式登记盲区。

## §C 日志 / 异常消息（结构性豁免）

`Log.*` / `error(...)` 的消息不该进 `strings.xml`（它们是开发者向的消息，不是 UI 文案）。

| 文件:行 | 字面量 | 位置 |
|---|---|---|
| `app/src/main/java/com/yumiru11/githubapp/MainActivity.kt:477` | `OAuth 回调失败（用户取消或错误回调）: ${e.message}` | `Log.w(TAG, …)` |
| `app/src/main/java/com/yumiru11/githubapp/MainActivity.kt:480` | `OAuth token 交换失败: ${e.message}` | `Log.w(TAG, …)` |
| `feature/repo/.../RepoDetailScreen.kt:1617` | `附件读取失败: ${e.message}` | `Log.w(TAG, …)` |
| `core/ui/.../AppNavHost.kt:126` | `起始 destination 仅支持无参路由：$startDestination` | `error(…)`（编程错误，非用户路径） |
| `feature/repo/.../RepoRepository.kt:236` | `README HTML 响应为 JSON 且内容为空` | `error(…)` |

## §D 【待修清单】`KNOWN_DEBT`（禁改文件里的既有命中）

**当前为空。** 逐条核实干净 main 上的全部 16 个含 CJK 字面量后：
12 个是 §B 的预览夹具、4 个是 §C 的日志/异常消息，**没有一条真泄漏**，
因此本票**没有需要改动的 production 代码**，也就没有需要主代理分派到
`feature/pullrequest` / `feature/search` / `feature/repo` / `feature/issue` /
`feature/home` / `feature/settings` / `core/ui` / `core/designsystem` / `core/editor` /
`MainActivity.kt` 的修复项。

`KNOWN_DEBT` 机制本身已落地并带**陈旧条目断言**（条目所指命中消失即测试变红），
后续若在禁改文件中发现真泄漏，按此格式登记即可让守卫先放行、再排期修：

```kotlin
private val KNOWN_DEBT = listOf(
    Debt(
        path = "feature/xxx/src/main/kotlin/…/XxxScreen.kt",
        value = "Hardcoded English copy",
        issue = "TODO(#NNN) 等 xxx 任务落地后改 stringResource",
    ),
)
```

## §E 历史泄漏与守卫覆盖

实例：PR 详情页出现裸英文常量 `Checking mergeability...`（CI 真机截图 `pr-conversation.png` 发现），
当时**没有任何自动检查**能拦住它——`I18nParityTest` 只看资源键，Android Lint 的 `HardcodedText`
只作用于 XML 布局，Compose lint 检测器又被整包跳过（`app/build.gradle.kts` lint 段 / issue #170）。

守卫已针对该形态做了**端到端验证**：临时在 `feature/profile/.../ui` 下写入
`Text("Checking mergeability...")`，`./gradlew :app:testDebugUnitTest --tests "*I18nHardcodedStringGuardTest*"`
立即变红并给出 `文件:行 [Text() 字面量]`（验证后文件已删除）。同样的形态也被
`scanner_syntheticSnippets_expectedFindingsOnly` 反向测试永久锁定。
