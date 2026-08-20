# GitHub Actions 模拟器长截图（full-page scroll screenshot）——调研决策报告

> 调研时间：2026-08-20。主代理直接调研（GitHub 代码搜索 + 官方文档 + 社区实证），无子代理。
> 需求来源：CI 截图管线（`.github/scripts/screenshots.sh`）目前 `adb exec-out screencap -p` 只能截当前视口——长 README / Issue 正文无法整页捕获，需要 full-page 截图。
> 技术栈基线：GitHub Actions + `reactivecircus/android-emulator-runner`（Android 34 / pixel_6 / 1080x2400@420dpi）+ bash 脚本 + `adb`。**本报告只做调研与决策，不改任何代码。**

## 一、决策摘要

| 决策 | 结论 | 一句话理由 |
|---|---|---|
| 内置长截图能力 | **不存在** | `screencap` 只截视口；Android 14+ ScrollCapture API 是**应用内 API**，由系统截图 UI 触发，adb 无法调用 |
| 推荐方案 | **A. 滚动 + 拼接（scroll + stitch）** | 唯一被多个 OSS 项目实证、零应用代码、覆盖全部内容类型的路线 |
| 拼接实现 | **Python（PIL/numpy）重叠检测 + 拼接** | 纯 adb 驱动，无 Appium 依赖；wear-explorer / seeway-jietu 两个实证实现同款算法 |
| B. wm size 拉高逻辑屏 | **排除（可作实验性 fallback）** | SurfaceFlinger 合成上限 + 真机实证截图损坏（XDA）；LazyColumn 虚拟化不随视口展开 |
| C. 应用侧 debug hook | **暂缓** | 最可靠但需应用代码 + debug 布局 ≠ 生产布局，截图失真 |
| D. scrcpy / uiautomator / 快照 | **排除** | 均无整页捕获能力，只是换一种"截视口"的方式 |

## 二、问题 1：内置能力盘点（结论：没有）

| 候选 | 结论 | 依据 |
|---|---|---|
| `adb exec-out screencap -p` | 只截当前视口 | 官方行为；StackOverflow 72804636 共识：长截图 = 多次截图 + 滚动 + 拼接 |
| `adb emu screenrecord screenshot` | 模拟器控制台截图，仍是视口 | Android Studio 文档（emulator-take-screenshots） |
| **Android 14+ ScrollCapture API**（`ScrollCaptureCallback`，API 31+） | **应用内 API，adb 不可用** | 官方文档：由系统截图 UI（电源+音量下）触发，应用需注册 `View.setScrollCaptureCallback` / `Window.registerScrollCaptureCallback`；CI 无头模拟器无法走系统截图 UI 流程 |
| `uiautomator` / `UiScrollable` | 能滚动，但截图仍是视口 | UiAutomator2 的 screenshot 走 screencap 同路径 |
| 模拟器 console / 快照 | 无整页能力 | — |

**结论**：API 34 模拟器上，无 root、无应用改动的前提下，**不存在任何内置整页截图能力**。唯一可行路线是"滚动 N 屏 + 重叠检测 + 拼接"。

## 三、问题 2：OSS 滚动拼接工具实证（4 个）

| 项目 | 技术栈 | 重叠检测算法 | 实证证据 |
|---|---|---|---|
| **[virtual-device-scrollshot](https://github.com/ashfaqahmed39/virtual-device-scrollshot)**（ashfaqahmed39） | Node/Appium + UiAutomator2 + Sharp，npm 包，MIT | 灰度像素重叠拼接；**拼接不可靠时显式失败** | CI badge ✓；README 有 Android/iOS 成品图；`--max-frames 20` / `--max-height 30000` 上限 |
| **[wear-explorer `scroll-capture.py`](https://github.com/middleseatman/wear-explorer)** | Python + numpy + PIL，纯 adb | **中央竖条像素比较**（避开圆形表盘边角）；`reduce_motion` 关动画 | 真实 Wear OS 模拟器截图脚本，成品图入库 |
| **[seeway-jietu `capture_long_results_real_device.py`](https://github.com/seeway1983-design/seeway-jietu)** | Python + numpy + PIL + uiautomator | 水平条带（x 20%-82%）像素比较；**连续 2 帧相同判底**；OCR 兜底判停 | 真机长截图脚本，输出 metadata JSON |
| **[chat-extract-skill `adb_long_screenshot.py`](https://github.com/784228565/chat-extract-skill)** | Python + cv2 + PIL | **相位相关 `cv2.phaseCorrelate`** + 模板匹配兜底 + alpha 混合拼接 | 通用 adb 长截图脚本 |

**共性模式**（4 个实现全部一致）：
1. 关动画（`settings put global window_animation_scale 0` 等 / `reduce_motion`）
2. `screencap` → `input swipe` → 等待稳定 → 再 `screencap` 循环
3. 相邻帧做**条带像素匹配**找重叠偏移（不用全帧，避开状态栏/导航栏/滚动条）
4. 拼接用 PIL/numpy（或 Sharp），**找不到可靠重叠时显式失败**而非硬拼

## 四、问题 3：本项目内容类型的可靠性分析

### a. Compose LazyColumn（Issue 详情页——评论列表，虚拟化列表）
- **虚拟化**：`LazyColumn` 只组合可见 item，滚动时 item 回收重建。**拼接不受影响**——每帧截图是滚动稳定后的完整视口，内容确定。
- **滚动位置持久化**：`rememberLazyListState` 保证滚动稳定，无回弹。
- **风险点**：Compose 滚动条（scrollbar）在滚动期间显示、约 1s 后淡出——**必须在 swipe 后等待滚动条淡出再截图**（`sleep 1.5s`）。
- **结论**：可靠，需等待稳定。

### b. WebView 自适应高度（README / Issue 正文——`onHeightChanged` 撑高，外层 `Column` + `verticalScroll`）
- WebView 是**独立硬件层（surface）**：外层容器滚动时 WebView 层整体平移，**不触发 WebView 内部重绘**——不存在"滚动后栅格化延迟"问题。
- **风险点**：图片懒加载——必须在导航后等待 WebView 完全加载（现有脚本已 `sleep 6-8s`，保持）。
- **结论**：可靠，拼接时 WebView 内容在相邻帧间是连续平移的。

### c. 静态页（Home——固定内容）
- 无滚动容器或内容极短，**单帧即可**，拼接退化为"检测到无重叠即完成"。

### 什么会破坏拼接（按本项目逐项排查）

| 破坏因素 | 本项目是否命中 | 缓解 |
|---|---|---|
| 状态栏/导航栏每帧固定 | **命中**（系统栏不随内容滚动） | 重叠检测**裁剪系统栏区域**（顶部 ~150px / 底部 ~150px），只用内容区条带 |
| 滚动条 overlay | **命中**（Compose 滚动条） | swipe 后 `sleep 1.5s` 等淡出；条带避开右缘 |
| 粘性头部（sticky header） | **不命中**（Issue 头部是 LazyColumn item，随滚动移出；README 页无 sticky） | 若未来加 sticky header 需裁剪 |
| 帧间动画（fling/overscroll） | **命中**（`input swipe` 默认有惯性） | 关动画 + 慢速 swipe（长 duration）+ 等待稳定 |
| WebView 栅格化延迟 | **不命中**（硬件层平移，见 b） | — |
| 内容加载中（图片/网络） | **命中**（WebView 懒加载） | 导航后固定等待 + 拼接前校验条带非空白 |

## 五、问题 4：方案对比

### A. 滚动 + 拼接（推荐）
- **优点**：零应用代码；覆盖全部内容类型；4 个 OSS 实证；纯 adb + Python 标准生态（PIL/numpy），CI 可直接 `pip install pillow numpy`。
- **缺点**：长 README 需 10-20 帧；拼接质量依赖重叠检测阈值；慢（每帧 ~2-3s）。
- **对本项目**：README/Issue 正文是 WebView 硬件层平移（最易拼），评论是 LazyColumn 稳定内容（可靠），Home 单帧。**全部内容类型都适合**。

### B. wm size 拉高逻辑屏（1080x24000）——排除
- **SurfaceFlinger 合成上限**：`frameworks/native` 源码有 `maxGraphicsWidth/maxGraphicsHeight`（GPU fallback 合成上限）与 `maxVirtualDisplaySize`；1080×24000 远超常规上限，swiftshader 模拟器大概率合成失败或极慢。
- **真机实证损坏**：XDA 帖子（changing-resolution-blocks-screenshots）——`wm size 2160x4320` 后系统截图直接报错；Pie 上自定义 wm size 导致截图只截左上角/带黑边。
- **布局行为**：LazyColumn 是虚拟化容器，**不会因视口变高而展开全部 item**（仍只组合可见项）；WebView 撑高理论上可行，但被合成上限一票否决。
- **结论**：不可靠，排除。

### C. 应用侧 debug hook——暂缓
- debug source set 深链渲染"非滚动全高布局"再单帧截图：**最可靠**，但需应用代码，且 debug 布局 ≠ 生产布局（截图失真），成本最高。
- **结论**：作为未来选项记录，本轮不做。

### D. 其他
- **scrcpy**：镜像 + 截屏，仍是视口，无整页能力。
- **uiautomator UiScrollable**：能滚动，截图仍视口。
- **模拟器快照**：无整页能力。
- 全部排除。

## 六、推荐实现大纲（方案 A）

### 伪代码（Python，纯 adb + PIL/numpy）

```python
# 1. 关动画（一次性）
adb shell settings put global window_animation_scale 0
adb shell settings put global transition_animation_scale 0
adb shell settings put global animator_duration_scale 0
adb shell settings put global reduce_motion 1

# 2. 导航到目标页（沿用现有 screenshots.sh 的 am start + wait_for_activity）
#    等待 WebView 加载完成（sleep 6-8s）

# 3. 滚动捕获循环
frames = []
while len(frames) < MAX_FRAMES:            # 上限 20
    adb exec-out screencap -p > f_{n}.png  # 截当前视口
    frames.append(f_{n}.png)
    if len(frames) >= 2 and identical(frames[-1], frames[-2]):  # 连续 2 帧相同 = 到底
        break
    adb shell input swipe 540 1800 540 600 900   # 慢速上滑（900ms，避免 fling）
    sleep 1.5                                    # 等滚动条淡出 + 稳定

# 4. 重叠检测（相邻帧对）
def find_overlap(top_img, bottom_img):
    # 裁剪系统栏：top[150:-150, 20:-20]（避开状态栏/导航栏/滚动条右缘）
    # 对候选偏移 d in [200..H-200]：
    #   strip_top    = top_img[-d:, :]      # 上一帧底部 d 行
    #   strip_bottom = bottom_img[:d, :]    # 下一帧顶部 d 行
    #   score = mean_abs_diff(strip_top, strip_bottom)
    # 取 score 最小的 d；要求 score < 阈值 且 d >= 最小重叠(200px)
    # 找不到可靠重叠 → 显式失败（不硬拼）

# 5. 拼接
canvas = Image.new('RGB', (W, total_H))
for i, (img, d) in enumerate(zip(frames, overlaps)):
    canvas.paste(img, (0, y_offset)); y_offset += img.H - d

# 6. 产物
#   full-page.png（整页）+ frames/（原始帧）+ metadata.json（每帧偏移/重叠/帧数）
```

### 关键命令
```bash
adb exec-out screencap -p > frame.png          # 截图（现有脚本已用）
adb shell input swipe 540 1800 540 600 900     # 慢速滚动（坐标按 1080x2400 视口）
adb shell settings put global window_animation_scale 0   # 关动画
pip install pillow numpy                        # CI 依赖（仅这两个）
```

### 预期产物
- `full-page.png`：整页拼接图（README/Issue 正文/评论列表各一张）
- `frames/`：原始帧（调试用）
- `metadata.json`：每帧重叠偏移、帧数、是否到底

## 七、失败模式 + 缓解

| 失败模式 | 缓解 |
|---|---|
| 重叠检测失败（空白/低对比区域） | 条带要求最小内容像素；扩大条带宽度；**显式失败**（virtual-device-scrollshot 同策略） |
| 滚动条残影入帧 | swipe 后 `sleep 1.5s`；条带避开右缘 20px |
| 状态栏/导航栏干扰匹配 | 裁剪系统栏区域后再匹配 |
| WebView 图片未加载 | 导航后固定等待（沿用现有 6-8s）；拼接前校验条带非空白 |
| LazyColumn 滚动中 item 重建 | 滚动稳定后再截图（sleep 已覆盖） |
| overscroll 回弹导致帧间位移异常 | 慢速 swipe（900ms）+ 关动画 |
| 到底误判（内容中途静止） | **连续 2 帧相同**才判底（seeway-jietu 实证） |
| 长页面内存 | PIL 增量拼接，canvas 按需扩展；`--max-height 30000` 上限（virtual-device-scrollshot 同款） |

## 八、事实 vs 推断

**事实**（带来源）：
- `screencap` 只截视口，长截图 = 滚动 + 拼接（[StackOverflow 72804636](https://stackoverflow.com/questions/72804636)）
- Android 14+ ScrollCapture API 是应用内 API，系统截图 UI 触发，adb 不可用（[developer.android.com ScrollCaptureCallback](https://developer.android.com/reference/android/view/ScrollCaptureCallback)）
- SurfaceFlinger 有 `maxGraphicsWidth/maxGraphicsHeight` 合成上限（[frameworks/native SurfaceFlinger.h](https://android.googlesource.com/platform/frameworks/native/+/refs/heads/main/services/surfaceflinger/SurfaceFlinger.h)）
- `wm size` 拉高分辨率导致截图损坏/黑边（[XDA changing-resolution-blocks-screenshots](https://xdaforums.com/t/changing-resolution-blocks-screenshots.3717441/)）
- 4 个 OSS 滚动拼接实现全部采用"条带像素匹配 + 显式失败"模式（[virtual-device-scrollshot](https://github.com/ashfaqahmed39/virtual-device-scrollshot)、[wear-explorer](https://github.com/middleseatman/wear-explorer)、[seeway-jietu](https://github.com/seeway1983-design/seeway-jietu)、[chat-extract-skill](https://github.com/784228565/chat-extract-skill)）

**推断**：
- WebView 硬件层平移不触发重绘（基于 Android surface 架构，未在本项目实测）
- Compose 滚动条 ~1s 淡出（基于 Compose 默认行为，未实测）
- PIL/numpy 条带匹配对本项目内容（文本为主、对比度高）足够可靠（基于 wear-explorer/seeway-jietu 实证，未在本项目跑通）

## 九、落地建议

1. **Phase 1（本轮）**：在 `.github/scripts/screenshots.sh` 中为 README/Issue 正文/评论列表增加"滚动拼接"分支——Python 脚本（PIL/numpy）做重叠检测 + 拼接，bash 侧沿用现有导航/等待逻辑。
2. **Phase 2（可选）**：若拼接质量不达标（低对比内容），升级为 `cv2.phaseCorrelate`（chat-extract-skill 方案）。
3. **Phase 3（可选）**：应用侧 debug hook 作为终极方案，仅在需要像素级精确时考虑。