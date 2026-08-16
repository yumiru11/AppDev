# 2026 年 Android Compose + Material 3 + WebView 截图自动化回归与 CI 审查决策报告

> 适用范围：Kotlin 2.3、Jetpack Compose、Material 3、Android 客户端，`compileSdk 36`、`minSdk 26`、AGP 8.7，纯 JVM Robolectric/Roborazzi 测试，GitHub Actions CI，页面中存在 WebView 兜底渲染。  
> 交叉验证（2026-08-16 主代理核实）：CPST 任务名修正为 `updateDebugScreenshotTest`/`validateDebugScreenshotTest`；
> marocchino 版本修正为 v3；FTL 定价补充（Spark 免费 10 虚拟+5 物理/天，Blaze $1/$5 每小时）；
> Git LFS 调整为「设备矩阵扩大后」再引入（当前基线 PNG 直接入库工作正常）。其余结论与
> `docs/research/screenshot-automation.md`（主代理调研）一致。  
> - **[事实]**：来自官方文档、官方仓库或公开项目入口，可直接核验；但版本号、价格、兼容性会随时间变化，落地前请再次确认。  
> - **[推断]**：基于 Android/Compose/CI 工程实践的判断，需要通过小规模试点验证。  
> - 本报告无法实时抓取 2026 年最新价格和版本状态，因此所有第三方库版本、云真机计费、Robolectric 对最新 SDK 的支持都需要以官方链接为准。

---

## 1. 结论先行：推荐方案

### 最终推荐：分层截图回归体系

**主门禁：Roborazzi + Robolectric，跑在 GitHub Actions JVM 上。**  
用于覆盖 Jetpack Compose、Material 3、普通页面状态、主题、深色模式、字体缩放、语言、RTL、空态、错误态、加载态等大部分 UI 回归。

**WebView 真实渲染：用最小规模真机/模拟器截图管线兜底。**  
JVM 路线，包括 Roborazzi、Paparazzi、Compose Preview Screenshot Testing，都不应被期望能真实渲染 WebView 中的 HTML/CSS/JS、字体、滚动、WebView 版本差异。含 WebView 的关键页面必须通过 instrumentation 测试在 Android emulator 或真机上截图。

**PR 审查：GitHub Actions + sticky comment / Danger / reg-suit 风格的可视化评论。**  
CI 输出差异截图、diff 图、失败列表、artifact 链接，并在 PR 中自动更新一条评论。

**基线管理：优先 Git + Git LFS；如果设备矩阵扩大，再迁移到对象存储 + manifest。**  
截图基线必须和代码一起 review，基线变更必须显式提交，不允许 CI 隐式覆盖。

**云真机选择：有 Google Cloud/Firebase 生态优先 Firebase Test Lab；需要商业设备池和更强设备管理选 BrowserStack/Sauce；强合规或已有设备实验室才选自建 adb 管线。**

### 一句话结论

> **2026 年最稳的方案不是“一个工具打天下”，而是：Roborazzi 做高频 PR 门禁，真机/模拟器只覆盖 WebView 关键路径，PR 评论自动贴 diff，基线变更强制人工 review。**

---

## 2. 关键约束与事实基线

### 2.1 技术栈事实

| 项目 | 状态/影响 | 标注 |
|---|---|---|
| Kotlin 2.3 + Compose | Kotlin 2.x 后 Compose Compiler 以 Kotlin compiler plugin 形式集成，需要 Kotlin、Compose Compiler、AGP、AndroidX 版本严格匹配 | [事实] |
| AGP 8.7 | 需要确认其官方支持的 compileSdk、JDK、Kotlin 版本范围 | [事实] |
| `compileSdk 36` | 新 SDK 对 Robolectric、Paparazzi、LayoutLib、云真机镜像的支持通常滞后，需要实测 | [推断] |
| `minSdk 26` | JVM 截图工具可以按不同 SDK/qualifier 模拟配置；真机矩阵可从 Android 8.0/8.1 起覆盖 | [推断] |
| Jetpack Compose + Material 3 | 截图测试需要固定主题、颜色、字体、暗色模式、动态色、edge-to-edge、系统栏等变量 | [推断] |
| WebView 兜底渲染 | JVM 截图工具基本不能真实渲染 WebView 内容；真实 WebView 必须依赖 emulator/device | [推断，强] |

一手来源见文末。

---

## 3. 路线总览对比表

| 路线 | 代表工具 | WebView 内容覆盖 | CI 成本 | 基线管理 | 维护难度 | PR 评论集成 | 推荐角色 |
|---|---|---|---:|---|---|---|---|
| JVM Robolectric 截图 | Roborazzi | 不覆盖真实 WebView；只能测容器、占位、加载状态或 mock 结果 [推断] | 低 [推断] | 容易，PNG 可直接入库 [推断] | 低到中 [推断] | 好，可生成 diff/compare 结果并接入 GitHub comment [推断] | **主门禁，首选** |
| JVM LayoutLib 截图 | Paparazzi | 不适合真实 WebView；对 Compose/Material 3/新 SDK 兼容性需实测 [推断] | 低 [推断] | 容易，snapshot 入库 [事实] | 中到高，AGP/Kotlin/Compose 升级兼容性风险较高 [推断] | 一般，需要自定义 PR 评论 [推断] | 仅在已有 Paparazzi 资产时考虑 |
| 官方 Compose Preview 截图测试 | Compose Preview Screenshot Testing | 不覆盖真实 WebView；只覆盖 `@Preview` composable [事实/推断] | 低 [推断] | 由插件任务管理基线 [事实] | 低，但覆盖面有限 [推断] | 一般，需要自定义 CI 输出 [推断] | 适合设计系统、组件库、Preview 治理 |
| 云真机：Firebase Test Lab | gcloud + Espresso/Compose UI Test + 自定义截图 | 高，真实 Android System WebView/Chrome 内核 [事实] | 中到高，按设备时长计费 [事实] | 需自建：拉取截图、比较、更新基线 [推断] | 中到高：设备差异、WebView 版本、网络、flaky [推断] | 可集成，需要下载 artifact 后评论 [推断] | WebView 关键页兜底 |
| 云真机：BrowserStack/Sauce | App Automate/Espresso/Appium + 自定义截图 | 高，真实设备 WebView [事实] | 高，商业订阅 [事实] | 需自建或接第三方视觉平台 [推断] | 中到高 [推断] | 可集成，通常结合 Percy/自研评论 [推断] | 有预算、需要设备池/商业支持时选择 |
| 自建 adb 管线 | self-hosted runner + emulator/真机 + adb + instrumentation | 高，取决于设备/镜像 WebView [事实] | 表面低，隐性成本高：设备、稳定性、运维 [推断] | 完全自建 [推断] | 高到很高 [推断] | 可深度定制 [推断] | 强合规、已有设备实验室时选择 |

---

## 4. JVM 路线详细评估

### 4.1 Roborazzi：推荐作为主方案

Roborazzi 是基于 JVM/Robolectric 的 Android 截图测试工具，支持 record/verify/compare，适合 Compose UI 截图回归。[事实]

#### 优势

| 维度 | 评估 | 标注 |
|---|---|---|
| Compose 支持 | 适合 Compose composable、页面、组件级截图 | [推断] |
| 运行环境 | GitHub Actions Linux runner 即可，无需 emulator/device | [事实] |
| CI 速度 | 远快于 instrumentation，适合 PR 门禁 | [推断] |
| 配置矩阵 | 可通过 Robolectric qualifier 测深色、语言、字体、屏幕尺寸等 | [推断] |
| 基线管理 | PNG 可入库，diff 可输出 | [事实/推断] |
| PR 集成 | 可通过 compare/verify 输出差异，再由 GitHub Action 评论 | [推断] |

#### 局限

| 局限 | 说明 | 标注 |
|---|---|---|
| WebView | 不能依赖它渲染真实 WebView 内容。Robolectric 对 WebView 更多是 shadow/模拟，不是完整 Chromium/Android WebView 渲染 | [推断，强] |
| 像素一致性 | JVM 图形渲染、字体、JDK、runner 镜像可能引入差异，需要固定运行环境 | [推断] |
| 新 SDK 支持 | `compileSdk 36`、最新 Android API、Robolectric SDK 支持可能滞后，需测试 | [推断] |
| 系统 UI | 状态栏、导航栏、输入法、系统弹窗等不适合像素级回归，应裁剪或 mask | [推断] |

#### 推荐用法

- 每个关键页面建立状态驱动截图：loading、success、empty、error、offline、长文本、极端用户数据。
- Material 3 主题固定为测试主题，不依赖真实壁纸动态色。
- 使用固定 locale、fontScale、dark mode、density、layout direction。
- 对含 WebView 的页面，只截图 native 容器和占位状态，不要断言 WebView 像素。

#### Gradle/命令参考

常见任务形式如下，具体以 Roborazzi 当前文档为准：[事实入口见文末]

```bash
./gradlew recordRoborazziDebug
./gradlew verifyRoborazziDebug
./gradlew compareRoborazziDebug
```

推荐 PR 流程：

```bash
./gradlew verifyRoborazziDebug --continue
```

失败时上传：

- 期望图
- 实际图
- diff 图
- HTML/Markdown 报告

---

### 4.2 Paparazzi：可选，但不建议作为新项目首选

Paparazzi 是 Cash App 的 JVM 截图工具，基于 LayoutLib，传统上适合 Android View 截图。[事实]

#### 优势

| 维度 | 评估 | 标注 |
|---|---|---|
| JVM 运行 | 不需要设备 | [事实] |
| View 截图 | 对传统 View 体系成熟 | [推断] |
| snapshot 机制 | `recordPaparazziDebug` / `verifyPaparazziDebug` 成熟 | [事实] |

#### 风险

| 风险 | 说明 | 标注 |
|---|---|---|
| Compose/Material 3 兼容性 | 对 Compose、Material 3、新 Kotlin/AGP 的适配稳定性需要实测 | [推断] |
| WebView | 不能依赖真实 WebView 渲染 | [推断，强] |
| 升级维护 | AGP/Kotlin/compileSdk 升级时，LayoutLib 类工具可能出现兼容性问题 | [推断] |
| 生态趋势 | 对 Compose 项目，Roborazzi/Robolectric 路线通常更贴近当前 Android 测试生态 | [推断] |

#### 结论

如果项目没有历史 Paparazzi 资产，不推荐作为 2026 年 Compose + Material 3 项目主方案。

---

### 4.3 Compose Preview Screenshot Testing：适合组件和 Preview 治理

Android 官方提供 Compose Preview Screenshot Testing 能力，通过 Gradle 插件对 `@Preview` composable 生成和校验截图。[事实]

常见任务包括：

```bash
./gradlew :app:updateDebugScreenshotTest
./gradlew :app:validateDebugScreenshotTest
```

#### 适合场景

- 设计系统组件库
- 通用 Button、Card、Dialog、TopBar、ListItem、Badge、Chip 等
- Preview 覆盖率治理
- 设计师/开发共同 review 组件视觉

#### 不适合场景

- 复杂业务页面状态
- ViewModel/Repository 驱动的真实页面
- Navigation 栈
- WebView 页面
- 动态列表滚动状态
- 真实主题、字体、系统配置组合

#### 结论

它是“组件视觉基线”的好补充，不适合作为应用级页面截图回归唯一方案。  
如果团队 Preview 覆盖率高，可以加入；如果 Preview 覆盖率低，优先做 Roborazzi。

---

## 5. 真机/云真机路线详细评估

### 5.1 为什么 WebView 必须走真机/模拟器

WebView 视觉结果受以下因素影响：

- Android System WebView 或 Chrome 版本
- 系统字体
- GPU/software rendering
- 设备 DPI
- 网页 JS 执行时序
- 网络资源加载
- viewport/meta 配置
- 滚动条、焦点、输入框光标
- 深色模式强制策略
- 无障碍字体缩放

JVM 工具无法完整模拟这些因素。  
因此，若页面中 WebView 是核心内容，必须使用 instrumentation 截图。[推断，强]

---

### 5.2 Firebase Test Lab

Firebase Test Lab 可以运行 instrumentation 测试，支持真机和设备矩阵。[事实]

#### 优势

| 维度 | 评估 | 标注 |
|---|---|---|
| WebView | 真实 Android WebView/Chrome 组件 | [事实] |
| 设备矩阵 | 可选不同型号、API、locale | [事实] |
| CI | 可通过 `gcloud firebase test android run` 集成到 GitHub Actions | [事实] |
| 成本 | Spark 免费 10 虚拟 + 5 物理设备测试/天；Blaze $1/虚拟设备时、$5/物理设备时（各 30/60 分钟免费/天） | [事实] |
| 结果 | 可下载 artifacts、日志、截图目录 | [事实] |

#### 风险

| 风险 | 说明 | 标注 |
|---|---|---|
| 基线不稳定 | 不同设备、WebView 版本、字体渲染可能导致 diff | [推断] |
| 成本扩张 | 若每个 PR 跑大量设备，费用快速上升 | [推断] |
| 网络依赖 | 远程网页内容不可作为像素级基线 | [推断] |
| 基线管理 | FTL 本身不是视觉回归平台，需要自己比较和评论 | [推断] |

#### 推荐策略

- PR 阶段只跑 1 个基准设备，例如 Pixel 系列 + 固定 API。
- Nightly/release 阶段跑 2～5 个设备矩阵。
- WebView 页面使用本地 HTML fixture，不加载远程不可控页面。
- 对状态栏、导航栏、WebView 滚动条、广告位、动态时间区域做 mask。
- 使用 `--directories-to-pull` 拉取截图目录，再做比较。

示例命令，仅示意：

```bash
gcloud firebase test android run \
  --type instrumentation \
  --app app/build/outputs/apk/debug/app-debug.apk \
  --test app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk \
  --device model=Pixel7,version=35,locale=zh_CN,orientation=portrait \
  --directories-to-pull /sdcard/screenshots \
  --results-bucket=gs://your-project-test-results
```

---

### 5.3 BrowserStack / Sauce Labs / AWS Device Farm

BrowserStack 等商业设备云支持 Android 自动化测试，包括 Espresso/Appium 等。[事实]

#### 适合场景

- 公司已有 BrowserStack/Sauce 合同
- 需要真实设备池但不想自建设备机房
- 需要多国家网络环境、设备型号、OS 版本
- 需要商业支持、审计、SSO、权限管理

#### 风险

| 风险 | 说明 | 标注 |
|---|---|---|
| 成本 | 通常高于自建 emulator，尤其多设备并发 | [事实] |
| 视觉回归 | 不是天然 Android 截图基线平台，需要自研或接 Percy/Argos 等 | [推断] |
| 基线更新 | 仍需自己管理 golden image | [推断] |
| 隐私 | 需要评估应用数据、测试账号、内部 API 安全 | [推断] |

#### 结论

如果已有 BrowserStack，可以直接复用；如果没有，且主要目标是 Android Compose + WebView 截图回归，Firebase Test Lab 或自建 emulator 通常更直接。

---

### 5.4 自建 adb 管线

自建管线可以是：

- GitHub self-hosted runner + Android emulator
- USB 真机 + adb
- 小型设备实验室
- Docker Android/emulator 方案
- 使用 `adb shell screencap` 或 instrumentation `UiAutomation.takeScreenshot` / Compose `captureToImage`

#### 优势

| 维度 | 评估 | 标注 |
|---|---|---|
| 控制力 | 可固定镜像、locale、字体、动画、WebView fixture | [推断] |
| 成本 | 无云真机按次计费 | [事实] |
| 安全 | 数据不出内网 | [推断] |
| 定制 | 可深度集成 PR comment、baseline update、设备健康检查 | [推断] |

#### 劣势

| 维度 | 评估 | 标注 |
|---|---|---|
| 维护成本 | 很高：设备掉线、ADB、电量、锁屏、系统更新、存储、网络 | [推断] |
| CI 稳定性 | 需要设备健康检查、自动重启、watchdog | [推断] |
| GitHub Actions | Linux KVM/emulator 加速、self-hosted runner 安全隔离需要仔细设计 | [推断] |
| 扩展性 | 并发截图需要更多设备/emulator | [推断] |

#### 适合场景

- 合规要求禁止云真机
- 已有设备实验室
- 有专人维护 CI infra
- 需要长期控制成本且设备规模可控

#### 不建议场景

- 小团队
- 没有 infra 维护人力
- 只为了少量 WebView 页面搭建真机室

---

## 6. PR 评论与自动截图审查集成

### 6.1 推荐 PR 审查流程

推荐流程：

```text
PR 提交
  |
  v
paths-filter 判断是否涉及 UI/截图
  |
  v
Roborazzi verify/compare
  |
  v
失败或存在 diff
  |
  v
上传 artifact：expected / actual / diff / report
  |
  v
更新 PR sticky comment
  |
  v
开发者本地或 workflow_dispatch 执行 record
  |
  v
提交新的 baseline PNG
  |
  v
CI 重新 verify
```

### 6.2 GitHub Actions 关键组件

| 用途 | 推荐工具 | 标注 |
|---|---|---|
| 路径过滤 | `dorny/paths-filter` | [事实] |
| Gradle 构建缓存 | `gradle/actions/setup-gradle` | [事实] |
| 上传截图 artifact | `actions/upload-artifact` | [事实] |
| PR 评论 | `actions/github-script` 或 `marocchino/sticky-pull-request-comment` | [事实] |
| 更复杂审查 | Danger JS/Kotlin、reg-suit、自研报告服务 | [事实] |
| Fork PR 评论安全 | 使用 `workflow_run` 而不是高风险 `pull_request_target` | [推断] |

### 6.3 PR 评论内容建议

评论中至少包含：

```markdown
## Screenshot Regression Report

- Total: 132
- Passed: 128
- Changed: 3
- New: 1
- Failed threshold: 0

### Changed
| Screen | Expected | Actual | Diff |
|---|---|---|---|
| HomeLight | expected_home_light.png | actual_home_light.png | diff_home_light.png |

### Actions
- Download artifact: screenshot-report
- Update baseline locally: ./gradlew recordRoborazziDebug
```

如果图片无法直接内联，至少提供 artifact 链接。私有仓库中 GitHub artifact 图片直链受限，若要内联缩略图，可考虑上传到内部对象存储、GitHub Pages、GCS signed URL 或视觉回归 SaaS。[推断]

---

## 7. 基线管理方案

### 7.1 基线存储选择

| 方案 | 优点 | 缺点 | 推荐阶段 |
|---|---|---|---|
| Git 普通文件 | 简单，PR diff 可见 | 仓库膨胀，二进制 diff 不友好 | 小项目/试点 |
| Git LFS | 适合 PNG，仓库压力较小 | 需要 LFS 配额，CI checkout 配置 | 设备矩阵扩大后 |
| 对象存储 + manifest | 适合多设备矩阵、大量图片 | 工程复杂 | 设备矩阵扩大后 |
| 视觉回归 SaaS | 报告、审批、存储一体化 | 成本、数据安全 | 有预算时 |

### 7.2 基线命名建议

```text
screenshots/
  compose/
    home_light_zh_CN_font100_api35.png
    home_dark_zh_CN_font100_api35.png
    home_light_en_US_font130_api35.png
  webview/
    webview_fallback_light_pixel7_api35_webview131.png
```

建议包含：

- 页面名
- 主题
- locale
- fontScale
- API/设备
- WebView 版本，尤其是真机截图
- 可选：fixture hash

### 7.3 基线更新策略

推荐策略：

1. CI 只 verify，不自动 record。
2. 开发者确认 UI 变化合理后，本地执行 record。
3. 新 baseline 作为普通代码变更提交。
4. CODEOWNERS 强制设计/前端/客户端负责人 review baseline。
5. 禁止 CI 在普通 PR 中自动 push baseline。
6. 对 intentional UI change，可以提供一个受控的 `workflow_dispatch` record 工作流。

`.gitattributes` 示例：

```gitattributes
*.png filter=lfs diff=lfs merge=lfs -text
```

CODEOWNERS 示例：

```text
**/screenshots/** @android-ui-owner @design-system-owner
```

---

## 8. WebView 精准化测试策略

这是整个方案中最容易失准的部分。

### 8.1 不要截图远程网页

如果 WebView 加载远程 URL，以下因素都会导致 flaky：

- CDN 资源变化
- A/B test
- 广告/推荐位
- 时间戳
- Cookie/登录态
- 网络延迟
- 字体加载
- JS 动画

因此，像素级回归必须使用本地 fixture。[推断]

### 8.2 使用本地 HTML fixture

推荐：

- 将关键 WebView 页面制作成静态 HTML/CSS/JS fixture
- 放入 `assets/` 或测试资源目录
- 使用 `WebViewAssetLoader` 加载本地资源
- 固定 viewport、字体、颜色、图片
- 禁用不可控动画
- 使用固定文案和时间戳

一手来源：AndroidX WebKit `WebViewAssetLoader`。[事实]

### 8.3 WebView 截图前置条件

测试中应等待：

- `onPageFinished`
- 自定义 JS ready signal
- 图片加载完成
- 字体加载完成
- WebView 内容高度稳定
- 滚动位置固定

可以通过：

```kotlin
webView.evaluateJavascript("window.__SCREENSHOT_READY__") { ... }
```

或注入固定 ready 标记。

### 8.4 WebView 区域 mask

即使本地 fixture，也可能存在：

- 光标闪烁
- 滚动条
- 长按选择手柄
- 系统 WebView 版本差异
- 字体 anti-aliasing 差异

推荐：

- 对光标区域 mask
- 对滚动条 mask
- 对动态时间 mask
- 对状态栏/导航栏 mask
- 对广告/推荐区域 mask

### 8.5 WebView 基线设备策略

推荐：

- PR 阶段只用一个基准设备/WebView 组合。
- Nightly 跑多设备，但不一定作为 PR 门禁。
- 记录 WebView 版本号。
- 当 WebView 大版本升级导致合理视觉变化时，统一更新 baseline。

---

## 9. Compose/Material 3 精准化清单

### 9.1 固定主题

不要让截图依赖：

- 真实壁纸
- 动态色
- 系统暗色自动切换
- 时间自动切换

测试中注入固定：

- `lightColorScheme()` / `darkColorScheme()`
- 固定 seed 或固定 Material 3 color scheme
- 固定 typography
- 固定 shapes

### 9.2 固定系统配置

| 配置 | 推荐值 |
|---|---|
| locale | `zh-CN`、`en-US`、`ar` 等固定集合 |
| fontScale | `1.0`，可选 `1.3` |
| darkMode | light/dark 分开 |
| density | 固定 |
| layoutDirection | LTR/RTL 分开 |
| 动画 | 关闭 |
| 时间 | 固定 timestamp |
| 用户名/头像/ID | 使用固定 fake 数据 |
| 网络 | 使用 fake repository |

### 9.3 固定 Compose 测试状态

不要让页面依赖真实时间、随机 ID、远程配置。

推荐：

```kotlin
val state = HomeUiState(
  userName = "测试用户",
  items = fakeItems,
  now = fixedInstant,
  isLoading = false,
  error = null
)
```

### 9.4 系统栏处理

推荐：

- 截图只保留内容区
- 或裁剪状态栏/导航栏
- 或使用 Android System UI demo mode 固定状态栏 [事实]

---

## 10. GitHub Actions CI 设计建议

### 10.1 PR JVM 截图工作流

建议流程：

```yaml
name: pr-compose-screenshots

on:
  pull_request:

jobs:
  roborazzi:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
        with:
          lfs: true

      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: 21

      - uses: gradle/actions/setup-gradle@v4

      - name: Verify Roborazzi screenshots
        run: ./gradlew verifyRoborazziDebug --continue

      - name: Upload screenshot report
        if: failure()
        uses: actions/upload-artifact@v4
        with:
          name: roborazzi-report
          path: |
            **/build/outputs/roborazzi
            **/build/reports/roborazzi
          retention-days: 7
```

> 具体路径以 Roborazzi 当前版本输出为准。[推断]

### 10.2 PR 评论工作流

简化示例：

```yaml
      - name: Comment PR
        if: failure()
        uses: marocchino/sticky-pull-request-comment@v3
        with:
          message: |
            ## Screenshot regression failed
            Please download the artifact and compare diffs.
            If the change is intentional, run:
            `./gradlew recordRoborazziDebug`
```

更复杂的报告可以由脚本生成 Markdown 后贴入。

### 10.3 WebView/FTL 工作流

建议：

```yaml
name: webview-screenshots

on:
  pull_request:
    paths:
      - "app/**"
      - "feature/web/**"
      - "webview/**"

jobs:
  webview:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: Build APKs
        run: ./gradlew assembleDebug assembleDebugAndroidTest

      - name: Authenticate gcloud
        uses: google-github-actions/auth@v2

      - name: Run Firebase Test Lab
        run: |
          gcloud firebase test android run \
            --type instrumentation \
            --app app/build/outputs/apk/debug/app-debug.apk \
            --test app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk \
            --device model=Pixel7,version=35,locale=zh_CN,orientation=portrait \
            --directories-to-pull /sdcard/screenshots
```

之后：

1. 下载 FTL 结果。
2. 提取截图。
3. 与 golden 比较。
4. 生成 diff。
5. 上传 artifact。
6. 评论 PR。

---

## 11. 成本与稳定性评估

### 11.1 CI 成本

| 方案 | 成本特征 | 标注 |
|---|---|---|
| Roborazzi | GitHub Actions 分钟数 + artifact 存储 + LFS 存储 | [推断] |
| Paparazzi | 类似 Roborazzi | [推断] |
| Compose Preview | 类似 JVM 构建成本 | [推断] |
| Firebase Test Lab | 按设备时长计费，可能随设备矩阵扩大显著增加 | [事实] |
| BrowserStack | 商业订阅，通常较高 | [事实] |
| 自建 adb | 无云计费，但设备、运维、人力成本高 | [推断] |

### 11.2 稳定性

| 方案 | 稳定性 | 关键风险 |
|---|---|---|
| Roborazzi | 高 | JDK/runner/字体/SDK 支持 |
| Paparazzi | 中 | AGP/Kotlin/Compose/LayoutLib 兼容 |
| Compose Preview | 高 | 覆盖面小 |
| FTL | 中 | WebView 版本、设备差异、网络 |
| BrowserStack | 中 | 商业设备池状态、网络、测试脚本 |
| 自建 adb | 低到中 | 设备运维、ADB、稳定性 |

---

## 12. 推荐落地路径

### Phase 0：兼容性与基线治理准备

目标：确认 Kotlin 2.3、AGP 8.7、compileSdk 36、Robolectric/Roborazzi 组合可用。

任务：

1. 建立截图测试模块或 source set。
2. 固定 JDK、Gradle、AGP、Kotlin、Compose Compiler 版本。
3. 验证 Robolectric 是否支持目标 SDK。
4. 如果 `sdk 36` 支持不稳，JVM 截图先跑 `sdk 35` 或 `sdk 34`。
5. 真机/云真机覆盖 `sdk 36` 或当前主流版本。
6. 制定 baseline 目录、命名、LFS、CODEOWNERS。

退出标准：

- 一个页面能稳定 record/verify。
- 同一 commit 连续跑 10 次无明显 flaky。

---

### Phase 1：Roborazzi 覆盖核心 Compose 页面

目标：让 PR 有第一道视觉门禁。

覆盖范围：

- 启动页/首页
- 列表页
- 详情页
- 表单页
- 设置页
- 错误页
- 空态页
- Material 3 组件关键状态
- 深色模式
- 中文/英文
- 默认字体/大字体
- RTL 若有需求

建议先覆盖 20～50 个高价值截图，而不是全量 500 张。

---

### Phase 2：接入 GitHub Actions PR 评论

目标：让截图失败可 review。

任务：

1. PR 触发 `verifyRoborazziDebug`。
2. 失败上传 artifact。
3. sticky comment 输出 diff 摘要。
4. 提供本地 record 命令。
5. baseline 变更必须提交 PNG。
6. CODEOWNERS review。

退出标准：

- 开发者能在 PR 中清楚看到哪个页面变化。
- baseline 更新流程不超过 10 分钟。

---

### Phase 3：WebView 关键页真机/模拟器兜底

目标：覆盖 JVM 无法验证的 WebView 渲染。

任务：

1. 选择 1～5 个关键 WebView 页面。
2. 制作本地 HTML fixture。
3. 编写 instrumentation screenshot test。
4. 使用 Compose `captureToImage`、`UiAutomation.takeScreenshot` 或 `adb exec-out screencap -p`。
5. 固定 WebView settings：
   - JavaScript enabled/disabled 按场景固定
   - viewport 固定
   - dark mode 固定
   - font scale 固定
   - 禁止不可控动画
6. 在 FTL 或 self-hosted emulator 上运行。
7. 输出截图到 `/sdcard/screenshots` 并拉取。

退出标准：

- WebView fixture 页面连续 10 次运行 diff 稳定。
- 能识别出 HTML/CSS/JS 渲染破坏。

---

### Phase 4：设备矩阵与夜间任务

目标：避免 PR 成本过高。

策略：

- PR：Roborazzi 全量 + WebView 基准设备 1 台。
- Nightly：WebView 多设备、多 API、多 locale。
- Release：关键页面全矩阵。
- 使用 Flank 或自研 sharding 控制 FTL 成本。

---

### Phase 5：可选官方 Compose Preview Screenshot Testing

目标：治理设计系统和 Preview。

适用条件：

- 团队已有大量 `@Preview`
- 有设计系统组件库
- 需要组件级视觉基线
- 不需要覆盖业务状态

不建议与 Roborazzi 同时管理同一批组件基线，避免双基线系统。

---

## 13. 精准化与防 flaky 清单

### 13.1 必须固定

- [ ] JDK 版本
- [ ] Gradle 版本
- [ ] AGP 版本
- [ ] Kotlin 版本
- [ ] Compose Compiler 版本
- [ ] AndroidX Compose BOM/版本
- [ ] runner OS 镜像
- [ ] locale
- [ ] dark/light theme
- [ ] fontScale
- [ ] density
- [ ] layout direction
- [ ] fake time
- [ ] fake user
- [ ] fake repository
- [ ] 动画关闭
- [ ] 状态栏/导航栏策略
- [ ] WebView fixture 内容 hash

### 13.2 必须 mask/crop

- [ ] 状态栏时间
- [ ] 电池图标
- [ ] 导航栏手势条
- [ ] WebView 光标
- [ ] WebView 滚动条
- [ ] 广告位
- [ ] 推荐位
- [ ] 用户头像，若来自远程
- [ ] 动态日期
- [ ] 随机 ID

### 13.3 阈值策略

建议从保守阈值开始：

| 场景 | 建议初始阈值 | 标注 |
|---|---:|---|
| JVM Compose 组件 | 0 pixel 或极低阈值，配合 mask | [推断] |
| JVM 页面 | 0.05%～0.2% diff pixels | [推断] |
| 真机 WebView | 0.3%～1.0% diff pixels，强 mask | [推断] |
| 多设备矩阵 | 不作为 PR 强门禁，只做报告 | [推断] |

阈值不是越大越好。阈值过大失去回归意义，阈值过小导致 flaky。必须配合 mask 和固定 fixture。

---

## 14. 方案选择决策树

```text
是否主要是 Compose/Material 3 UI 回归？
├─ 是 → Roborazzi
│
是否包含 WebView 真实内容？
├─ 否 → Roborazzi 足够
└─ 是 → 需要 emulator/device
        │
        是否有 Firebase/GCP 生态？
        ├─ 是 → Firebase Test Lab
        │
        是否已有 BrowserStack/Sauce？
        ├─ 是 → 使用现有商业设备云
        │
        是否强合规/已有设备实验室？
        ├─ 是 → 自建 adb 管线
        └─ 否 → FTL 或 GitHub self-hosted emulator
```

---

## 15. 最终推荐架构

```text
┌──────────────────────────────────────────────────────┐
│ PR Gate                                              │
│                                                      │
│ 1. paths-filter                                      │
│ 2. Roborazzi verify/compare                          │
│ 3. upload artifact                                   │
│ 4. sticky PR comment                                 │
│                                                      │
│ 覆盖：Compose/M3 页面、组件、状态、主题、字体、locale    │
└──────────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────────┐
│ WebView Gate                                         │
│                                                      │
│ 1. instrumentation screenshot test                   │
│ 2. local HTML fixture                                │
│ 3. FTL / self-hosted emulator / device               │
│ 4. pull screenshots                                  │
│ 5. compare with golden                               │
│ 6. PR comment                                        │
│                                                      │
│ 覆盖：WebView 容器、HTML/CSS/JS 渲染、字体、滚动等      │
└──────────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────────┐
│ Baseline Governance                                  │
│                                                      │
│ Git LFS + CODEOWNERS + explicit record workflow      │
└──────────────────────────────────────────────────────┘
```

---

## 16. 一手来源链接

以下链接为官方文档或项目入口。由于工具版本和云价格会变化，请在落地前核对。

### Roborazzi / Robolectric

- Roborazzi GitHub  
  https://github.com/takahirom/roborazzi  
- Robolectric 官网  
  https://robolectric.org/  
- Robolectric GitHub  
  https://github.com/robolectric/robolectric  

### Paparazzi

- Paparazzi GitHub  
  https://github.com/cashapp/paparazzi  

### Compose Preview Screenshot Testing

- Compose Preview Screenshot Testing，Android 官方文档入口  
  https://developer.android.com/studio/preview/compose-screenshot-testing  

### Compose / Material 3 / AndroidX Testing

- Jetpack Compose Testing  
  https://developer.android.com/jetpack/compose/testing  
- Compose Compiler / Kotlin plugin 相关  
  https://developer.android.com/develop/ui/compose/compiler  
- Kotlin releases  
  https://kotlinlang.org/docs/releases.html  

### AGP

- Android Gradle Plugin release notes  
  https://developer.android.com/build/releases/gradle-plugin  

### WebView

- Android WebView reference  
  https://developer.android.com/reference/android/webkit/WebView  
- AndroidX WebKit `WebViewAssetLoader`  
  https://developer.android.com/reference/androidx/webkit/WebViewAssetLoader  

### Firebase Test Lab

- Firebase Test Lab overview  
  https://firebase.google.com/docs/test-lab  
- Firebase Test Lab pricing  
  https://firebase.google.com/docs/test-lab/pricing  
- Firebase Test Lab Android command-line  
  https://firebase.google.com/docs/test-lab/android/command-line  

### BrowserStack

- BrowserStack App Automate docs  
  https://www.browserstack.com/docs/app-automate  
- BrowserStack Espresso docs  
  https://www.browserstack.com/docs/app-automate/espresso  
- BrowserStack pricing  
  https://www.browserstack.com/pricing  

### 自建 adb / emulator

- ADB documentation  
  https://developer.android.com/tools/adb  
- Android emulator GitHub Action  
  https://github.com/ReactiveCircus/android-emulator-runner  
- Android System UI demo mode  
  https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/packages/SystemUI/docs/demo_mode.md  

### GitHub Actions / PR comment / visual review

- GitHub Actions artifacts  
  https://docs.github.com/en/actions/using-workflows/storing-workflow-data-as-artifacts  
- upload-artifact  
  https://github.com/actions/upload-artifact  
- github-script  
  https://github.com/actions/github-script  
- sticky pull request comment  
  https://github.com/marocchino/sticky-pull-request-comment  
- paths-filter  
  https://github.com/dorny/paths-filter  
- Danger  
  https://danger.systems/js/  
- reg-suit  
  https://github.com/reg-viz/reg-suit  

### 图像比较工具

- Pixelmatch  
  https://github.com/mapbox/pixelmatch  
- odiff  
  https://github.com/dmtrKovalenko/odiff  
- ImageMagick compare  
  https://imagemagick.org/script/compare.php  

### FTL sharding / cost control

- Flank  
  https://github.com/Flank/flank  

---

## 17. 最终建议

如果只能选一个落地方案，选这个：

> **Roborazzi 作为 PR 主门禁；WebView 关键页用 Firebase Test Lab 或自建 emulator 做 instrumentation 截图；基线存 Git LFS；GitHub Action 自动评论 diff；显式 record 更新 baseline。**

这个组合在以下维度最平衡：

| 维度 | 评价 |
|---|---|
| Compose/Material 3 覆盖 | 强 |
| WebView 覆盖 | 通过真机/模拟器兜底 |
| CI 成本 | 可控 |
| 维护难度 | 可接受 |
| PR 审查体验 | 好 |
| 精准化潜力 | 高 |
| 2026 年可持续性 | 相对最好 |

最重要的一条原则：

> **不要用 JVM 截图工具验证 WebView 真实渲染，也不要用全量真机截图覆盖所有 Compose 页面。前者不准，后者太贵且太 flaky。**