---
name: appdev-build-gates
description: Use before any implementation, fix, commit, or push in the AppDev Android repo — select the exact Gradle verification commands, fix common spotless/detekt violations, and avoid the known slow/blocked tasks (Roborazzi record, network-restricted builds).
---

# AppDev 构建与质量门禁

本仓库（AppDev，Android GitHub 客户端）的质量门禁 = CI 同款命令。任何实现/修复任务验证**必须命令级对齐 CI**——只跑 compile/test 会漏 spotless/detekt，CI 必挂（T4/T6/T7 曾爆 9 个违规）。

## 环境事实（先读 AGENTS.md 再干活）

- 无全局 gradle：一律 `./gradlew`；JDK 21（gradle.properties 已锁）；compileSdk **36**（不是 35！）
- 镜像由本机 `~/.gradle/init.d/mirror.gradle` 注入，不入库；**不要改 settings.gradle.kts 加镜像**
- 被墙（HTTP 000/超时）→ 停手报告，禁止自行 sudo mihomo（用户自己开代理）
- 不要用 LSP（本机 kotlin-ls 冷启动失败）；验证一律 Gradle 输出为准
- 构建输出禁止 grep/tail/head 过滤后反复重跑——一次跑完看完整输出

## 全门禁命令（提交前必跑）

```bash
./gradlew spotlessCheck              # ktlint 格式（修正：./gradlew spotlessApply）
./gradlew detekt                     # 静态分析
./gradlew konsistCheck               # 架构测试
./gradlew :app:lintDebug             # Android Lint
./gradlew :app:testDebugUnitTest     # 单测
./gradlew :app:verifyRoborazziDebug  # 截图校验
./gradlew :app:assembleDebug         # APK
```

快速验证（编辑后查 error，最快）：
```bash
./gradlew :app:compileDebugKotlin    # 全量编译入口（增量 ~13s）
./gradlew :core:markdown:compileDebugKotlin   # 单模块
./gradlew :core:test --tests "*XxxRepositoryTest*"   # 单模块单类
```

## 常见违规修复

| 违规 | 修法 |
|---|---|
| ktlint 格式 | `./gradlew spotlessApply` |
| Kotlin 包名含下划线 | 改包名（`core.githubauth` 而非 `core.github_auth`）；git mv 目录 |
| `const val` 非 SCREAMING_SNAKE | 改名 |
| MatchingDeclarationName | 文件名匹配唯一顶层声明 |
| detekt NestedBlockDepth/ReturnCount 等业务合理违规 | `@file:Suppress("RuleName")` + 理由注释（T3 先例） |
| SwallowedException（真缺陷） | 补 cause 保留异常链（不能 Suppress） |

## 禁止 / 慎用

- `recordRoborazziDebug`：本机极慢（1000s+ 曾卡死）——**默认禁止跑**；截图任务先问用户
- `:app:testDebugUnitTest` 全量：较慢，优先模块级测试；但提交前门禁必须含它
- edit 工具超时消息不可信——超时后 grep/read 验证是否落地再重试，防重复写入
