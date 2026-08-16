# GitHub Actions 上跑 Android 模拟器的可行性实证

> 2026-08-16 调研。背景：截图自动化 CI 第 1-6 轮全部失败（ubuntu API 34「无 KVM」→ macos-14 arm64 三次 boot 超时），用户坚持「Actions 一定能跑」。本报告基于一手来源（GitHub 官方文档/changelog、ReactiveCircus 仓库 issue、真实项目 workflow）给出结论与可行配置。

## 结论先行

**GitHub Actions 官方 runner 上跑 Android 模拟器完全可行——正确路径是：`ubuntu-latest（x86_64）+ Enable KVM 步骤 + x86_64 镜像 + API ≤ 30`。** 前 6 轮失败根因：
1. 第 1 轮（ubuntu API 34）：**缺 KVM 启用步骤**——ubuntu runner 有 `/dev/kvm`，但 runner 用户不在 kvm 组，必须 workflow 里先加 udev 规则（官方 README 原文 "Remember to enable KVM in your workflow"）
2. 第 3-6 轮（macos-14 arm64）：**平台硬限制**——GitHub arm64 macOS 托管 runner 不支持嵌套虚拟化（官方文档原话），arm64 模拟器无法硬件加速 → boot 永不完成

## Q1. ubuntu runner 的 KVM 现状（2026）

- **官方 changelog（2024-04-02）**："Hardware accelerated Android virtualization now available"——2-vCPU GitHub-hosted Linux runner 即可用硬件加速（此前仅 4+ vCPU larger runner）
- **GitHub 官方文档**（当前）："GitHub-hosted Linux runners support hardware acceleration for Android SDK tools"
- **2026-06 实证**（codersera 博客）："Standard ubuntu-latest/ubuntu-24.04 x86_64 runners ship with KVM available (you only need the udev group-permission fix)"
- **必须的启用步骤**（官方 README + changelog 同款）：
  ```yaml
  - name: Enable KVM group perms
    run: |
      echo 'KERNEL=="kvm", GROUP="kvm", MODE="0666", OPTIONS+="static_node=kvm"' | sudo tee /etc/udev/rules.d/99-kvm4all.rules
      sudo udevadm control --reload-rules
      sudo udevadm trigger --name-match=kvm
      ls -l /dev/kvm
  ```
- **⚠️ 私有仓库注意**：私有仓库标准 runner 是 **2 vCPU**（公开仓库 4 vCPU/16GB）——2024-01 的 issue 评论曾指出私有仓库无 KVM（早于 2024-04 公告）；公告后 2-vCPU runner 均可用硬件加速。若仍报 "hardware acceleration is not available"，先确认 `/dev/kvm` 权限步骤已加、且 arch 是 **x86_64**（KVM 仅 x86）
- **⚠️ 2026-06 回归风险**（ReactiveCircus#478，2026-06-17 开放）：~2026-06-11 起部分 runner 镜像出现**模拟器 job 挂起**（pachli 项目 + Blacksmith runner 同现）——若 boot 后脚本不退出/无日志推进，可能是此回归，考虑 pin 旧镜像或 emulator 版本
- runner-images#14062：`ubuntu-24.04-arm` 有 `/dev/kvm`；`ubuntu-26.04-arm` 没有（新镜像回归）——**用 x86_64 runner 最稳**

## Q2. macos-14 arm64：死路（平台硬限制）

- **GitHub 官方文档**（当前，arm64 macOS runner 限制）："**Nested-virtualization is not supported due to the limitation of Apple's Virtualization Framework.**"
- **ReactiveCircus#350**（2023-10 开放至今）：M1 macos runner "HVF error: HV_UNSUPPORTED"——2025-05 评论确认 macos-latest 仍失败；macos-26（M1 硬件）也不解决；**macos-13 已 2025-12-04 退役**
- **ReactiveCircus#380**：arm64-v8a 模拟器在 macos 上启动即崩溃（UniversalExceptionRaise）
- **ReactiveCircus#280**（2022）：arm64 镜像在 macOS 上 boot 超时是已知问题
- **反例澄清**：open-ani/animeko 的 arm64-v8a instrumented 测试跑在**自托管** macos-15（`runs-on: [self-hosted]`）——不是 GitHub 托管 runner
- **✅ 替代**：**`macos-15-intel`**（2025-09 新增的标准 runner，Intel 4 vCPU，ReactiveCircus#350 评论 marcprux 实证 "android-emulator-runner works on this runner"）+ **x86_64** 镜像——macos-13 的正统接班人

## Q3. 提高 boot 可靠性的配置（一手证据）

- `cores`：默认 2；模拟器在 host < 6 逻辑核时警告 "Setting number of virtual cores to 1"（讨论 #286）——**不要 cores: 4 配 3-vCPU runner**（macos-14 是 3 vCPU，超配可能卡）
- `emulator-boot-timeout`：默认 600s（action.yml）——硬件加速下 1-3 分钟足够；软件渲染才需要 900+
- `emulator-options`：`-no-window -no-snapshot -noaudio -no-boot-anim`（官方默认同款）——**不要 `-gpu swiftshader_indirect`**（硬件加速时拖慢）
- `disable-animations: true`
- **API level 可靠性**：API 29/30 最稳（README 示例 21/23/29）；API 33 有已知失败（#340）；API 34 arm64-v8a 失败（#392）；**API 30 是社区甜点**（#469：30 好 31 出问题）
- **ATD 镜像**（`google_atd`/`aosp_atd`，API 30，channel: canary）：为 CI 优化的轻量镜像（CHANGELOG #198）
- **AVD 快照缓存**：README 推荐两段式（生成快照 + 用快照，`no-snapshot-save`）大幅减少启动时间——可选优化

## Q4. 替代 action / 方案

- **Malinskiy/action-android**：带 `emulator-run-cmd` 的替代 action
- **手动原生方案**（natsuk4ze/gal PR）：android-actions/setup-android + 手动 avdmanager/emulator + KVM udev + 缓存——不依赖 action 的可行路线
- **Redroid（Docker，无 KVM）**：`docker run --privileged redroid/redroid`——ARM runner 无 KVM 时的方案（2026-06 文章）——不适用于我们的 x86_64 场景（KVM 方案更优）
- F-Droid GitLab CI 无 KVM 软件渲染（#46 eighthave）：default 镜像 API 22-27 可行但慢——最后手段

## Q5. 截图：Maestro 不是必需的（大量真实 workflow 用 adb screencap）

真实项目 CI（GitHub-hosted）实证：
- **kzahel/web-server-chrome** `android-ci.yml`：`reactivecircus@v2` + api 36 + x86_64 → script 里 `adb exec-out screencap -p > release-screen.png` + `uiautomator dump` + logcat + upload artifact
- **kiwix/kiwix-android** ci.yml：reactivecircus api 30 + 上传 `screencap.png`
- **inaturalist/iNaturalistAndroid** CI.yml：`adb exec-out screencap -p > screenshots/0-early-boot.png`（sleep 120s 后）
- **naufalzaidanell/SAKU-apps**：uiautomator dump grep + `adb exec-out screencap -p` + logcat + artifacts
- **traccar/rootless-logcat、tananaev/passport-reader** release.yml：`adb shell screencap -p /sdcard/launch.png` + `adb pull`

**驱动导航**（无 Maestro）：`adb shell input tap/swipe/text` + `uiautomator dump`（拿 UI 层级找坐标/文本）+ `cmd uimode night yes`（深色）+ `adb exec-out screencap -p`（截图）——**完全够截图自动化，且比 Maestro 少一个失败面**

## 最终推荐配置（本仓库适用）

```yaml
runs-on: ubuntu-latest            # x86_64，公开仓库 4 vCPU 16GB
steps:
  - uses: actions/checkout@v7
  - name: Enable KVM group perms   # ← 前 6 轮失败的关键缺口
    run: |
      echo 'KERNEL=="kvm", GROUP="kvm", MODE="0666", OPTIONS+="static_node=kvm"' | sudo tee /etc/udev/rules.d/99-kvm4all.rules
      sudo udevadm control --reload-rules
      sudo udevadm trigger --name-match=kvm
  - uses: reactivecircus/android-emulator-runner@v2
    with:
      api-level: 30               # 甜点版本（29/30 最稳）
      target: default
      arch: x86_64                # KVM 仅 x86——不要 arm64-v8a
      profile: pixel_6
      cores: 2                    # 公开 runner 4 vCPU，2 稳妥
      emulator-options: -no-window -no-snapshot -noaudio -no-boot-anim
      disable-animations: true
      script: |
        # adb screencap 轻量截图（Maestro 可选）
        adb exec-out screencap -p > /tmp/home.png
        adb shell cmd uimode night yes   # 深色
        adb exec-out screencap -p > /tmp/home-dark.png
```

## 一手来源索引

| 来源 | 链接 |
|---|---|
| GitHub changelog 2024-04-02（2-vCPU KVM） | github.blog/changelog/2024-04-02-hardware-accelerated-android-virtualization-now-available |
| GitHub 文档（runner 硬件加速 + ARM 嵌套虚拟化限制） | docs.github.com/en/actions/reference/runners/github-hosted-runners |
| ReactiveCircus README（KVM 启用步骤 + ubuntu 推荐） | github.com/ReactiveCircus/android-emulator-runner |
| #350（M1 HVF UNSUPPORTED + macos-15-intel 替代） | github.com/ReactiveCircus/android-emulator-runner/issues/350 |
| #478（2026-06 模拟器挂起回归） | .../issues/478 |
| #392（arm64-v8a + API 34 失败） | .../issues/392 |
| #280 / #380 / #469 / #340 / #286 / #400 | ReactiveCircus 仓库 issues |
| runner-images#14062（ubuntu-26.04-arm 无 KVM） | github.com/actions/runner-images/issues/14062 |
| kzahel/web-server-chrome android-ci.yml | github.com/kzahel/web-server-chrome/.github/workflows/android-ci.yml |
| kiwix-android ci.yml / inaturalist / SAKU-apps / traccar | 各仓库 .github/workflows |
| codersera 2026-06-08（KVM + Redroid） | codersera.com/blog/android-emulator-docker-without-kvm |
