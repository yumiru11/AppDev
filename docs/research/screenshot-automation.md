# 截图自动化回归 + CI 自动截图审查——调研决策报告

> 调研时间：2026-08-16。双线并行：2 个 librarian 子代理（JVM 工具线 + 设备/CI 线）+ 主代理补充验证。
> 需求来源：workflow-grill 3.1（用户拍板：找符合自动化需求的截图工具 → CICD 自动截图 + PR 评论 → 用户审查到 LGTM）。
> 技术栈基线：Kotlin 2.3.21 / Compose BOM 2026.06.01 (1.11) / AGP 8.7.3 / Robolectric 4.16.1 / Roborazzi 1.71 / 纯 JVM 测试 / GitHub Actions。

## 一、决策摘要

| 决策 | 结论 | 一句话理由 |
|---|---|---|
| JVM 截图工具 | **维持 Roborazzi** | 唯一走真实 Android 框架（RNG）的路线，Hilt/Espresso/动画全支持；1.72 升级纯装饰 |
| 补充层 | **可选加 Compose Preview Screenshot Testing** | 零成本补 @Preview 组件基准；与 Roborazzi 无冲突（独立 source set）；alpha 有 API 变动风险 |
| Paparazzi | **排除** | 开放 issue #1979：与 Robolectric 4.16（本项目的版本）同模块冲突「exacerbated」，且 Compose 1.11 只能走 alpha05 |
| Roborazzi Desktop | **排除** | Android-only 项目需先 KMP 迁移，官方明说不适用 |
| WebView 截图缺口 | **任何 JVM 工具都渲染不了 WebView** | Robolectric #9738「WebViews are not supported」；layoutlib 系（Paparazzi/CPST）也无 WebView |
| CI 截图方案 | **自建 KVM 模拟器 + adb/Maestro** | GitHub-hosted ubuntu-latest 暴露 /dev/kvm，模拟器能渲染 WebView；$0、约半天 |
| 真机兜底 | **Firebase Test Lab 备选** | 真机 WebView 验证，Spark 免费 5 台/天，Blaze ~$0.25-0.75/PR |
| BrowserStack/Sauce/Maestro Cloud | **全部排除** | $199-250/mo 起步，无免费层，私人项目不值 |
| PR 评论贴图 | **sticky comment + release asset 图片 URL** | GitHub artifact URL 不能内联渲染（平台限制），release asset 对私有仓库可用 |

## 二、JVM 侧结论（子代理 A + 主代理验证）

### Roborazzi（维持）
- **hardware 渲染已是默认**：Robolectric commit `17437ece`（2024-07-30）默认改 hardware，4.14 起生效；4.16.1 源码 `getProperty("robolectric.pixelCopyRenderMode", "hardware")` 确认默认值就是 hardware——项目里 core/markdown + prototype 显式声明 `pixelCopyRenderMode=hardware` 现在只是文档性（可统一/删除）
- 颜色保真天花板：HW 模式镜像设备 HardwareRenderer→Skia 路径，但跨 OS 一致性无保证（FAQ + nowinandroid#1242）；Google 官方：像素精度要求高时仍需设备测试
- **本机 record 1000s+ 是 Robolectric+hardware 路径固有**（Roborazzi 官方 benchmark：非 Robolectric 渲染器快 4-6 倍，PR #903）——提速靠**范围控制**：`-Pscreenshot` + JUnit @Category 过滤、`roborazzi.test.record=true` 折叠进常规测试、`generatedTestClassCount` = maxParallelForks
- WebView 内容必须排除在 Roborazzi 基线外

### Compose Preview Screenshot Testing（可选补充）
- 插件 `com.android.compose.screenshot` 0.0.1-alpha15；**tasks-only 路径要求 AGP 8.5.0+**（我们 8.7.3 ✓）；全 IDE 集成要 AGP 9+（我们不满足）
- layoutlib 渲染 @Preview，独立 screenshotTest source set，与 Roborazzi 共存无冲突
- 生产用户：home-assistant/android、d4rken-org/sdmaid-se、android/testing-samples
- 坑：alpha API 变动、无交互/动画/网络、KMP 不支持

## 三、设备/CI 侧结论（子代理 B）

### 推荐：自建 KVM 模拟器管线（$0，约半天）
1. `reactivecircus/android-emulator-runner@v2` + KVM udev 规则（ubuntu-latest x86 暴露 /dev/kvm）
2. 截图方式：`adb exec-out screencap` / Espresso Screenshot / **Maestro CLI（本地免费）**——Maestro YAML flow + takeScreenshot 最灵活
3. 对比：ImageMagick compare / pixelmatch
4. **PR 评论**：sticky action（mshick / marocchino / step-security）+ 图片内联

### PR 评论图片的硬约束
- **GitHub artifact URL 不能内联渲染**（mshick README + 开放 feature request #188521，2026-03 仍无解）
- 无公开 API 做拖拽式图片上传（gh CLI 关闭，cli/cli#1895）
- **可用方案**：`gh release create "pr-N-images" screens/*.png --prerelease` → browser_download_url 私有仓库可内联渲染（mareksuppa TIL）；或 `McCzarny/upload-image@v2` → imgbb/cloudinary

### 备选：Firebase Test Lab（真机）
- Spark 免费：10 虚拟 + 5 物理设备测试/天；Blaze：$1/虚拟设备时、$5/物理设备时（30/60 分钟免费/天）
- Robo 测试自动截图+视频（但抓不到精确页面）；instrumentation + testlab-instr-lib 精确捕获
- 集成：setup-gcloud + `gcloud firebase test android run` + Flank

### 排除
- BrowserStack App Automate ~$199-249/并行/mo；Sauce Real Device Cloud $199/mo（1 lane）；Maestro Cloud $250/设备/mo（2024-12-31 已 sunset 改 Robin）——全是付费墙

## 四、落地建议（分阶段）

1. **Phase 1（JVM，低成本）**：维持 Roborazzi；统一 pixelCopyRenderMode 声明；引入 `-Pscreenshot` 范围控制解决 record 慢
2. **Phase 2（WebView 缺口，$0）**：CI 加一个 emulator job（仅 WebView 兜底页面截图）→ sticky comment + release asset 图片 → 你 LGTM 闭环
3. **Phase 3（可选）**：Compose Preview 补充层；FTL 真机验证（真有需要时）

## 五、事实 vs 推断

**事实**（带来源）：hardware 默认（robolectric 源码 + commit 17437ece）；Paparazzi #1979 冲突（4.16 exacerbates，alpha06 未发）；CPST tasks-only 需 AGP 8.5+；FTL 免费额度/BrowserStack/Sauce/Maestro 定价；artifact URL 不内联；/dev/kvm 在 ubuntu-latest；Robolectric 不支持 WebView（#9738）

**推断**：KVM 模拟器 WebView 渲染足够人工审查环（≠ vivo 真机 WebView 的 color-mix 差异——真机仍是最终裁判）；record 慢的提速手段有效；Roborazzi/CPST 共存无冲突
