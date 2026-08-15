# AppDev 执行流程手册（AI Agent 工作流）

> 本文件定义 AppDev 从「接到 ticket」到「合并关票」的完整执行流程。所有 AI 代理（OpenCode / DSH / 云端）在 AppDev 干活前必读。
> 配合阅读：`AGENTS.md`（环境/门禁/铁律）、`docs/agents/project-status.md`（当前进度）。

## 1. Ticket 工作流总览

```
读 ticket 规格 → 建分支 → TDD 实现 → 本地全门禁 → 提交 → push → PR（Fixes #N）→ CI 绿 → 合并 → 关票
```

### 1.1 读 ticket

- Ticket = GitHub Issue，用 `gh` 读：
  ```bash
  gh issue view <number> --repo yumiru11/AppDev   # 完整规格
  gh issue view <number> --comments --repo yumiru11/AppDev  # 含讨论
  ```
- 规格在 issue body 里（验收标准/范围/依赖）。**Blocked by 关系的票必须等前置票合入 main 后再开分支**
- 规格引用 `plan.md` § 号 / `docs/ui-design.md` § 号——实现前读对应章节

### 1.2 分支

- 分支命名：`feature/tX-<kebab>`（如 `feature/t12-repo-management`），基于 `origin/main`
- **铁律：不提交 main、不 push main**（main 只接受 PR 合并）
- worktree 并行（多票同时跑）：
  ```bash
  git worktree add /home/zhiyi/dev/appdev-tX feature/tX-xxx origin/main
  cp /home/zhiyi/dev/AppDev/local.properties /home/zhiyi/dev/appdev-tX/   # 新 worktree 必做！
  ```
- **子代理派发时 prompt 必须带 WORKDIR**（默认会在会话目录改错地方——血泪教训）

### 1.3 实现（TDD）

1. 先写失败测试（RED）→ 实现（GREEN）→ 重构
2. 测试命名 `methodName_scenario_expectedBehavior`
3. 每步可编译：`./gradlew :<模块>:compileDebugKotlin`
4. 大任务拆小步提交，每提交 = 一个可编译状态

### 1.4 本地全门禁（提交前必跑，与 CI 命令级对齐）

```bash
./gradlew spotlessCheck detekt konsistCheck :app:lintDebug :app:testDebugUnitTest :app:verifyRoborazziDebug :app:assembleDebug
```

- 违规修复：`./gradlew spotlessApply`；detekt 业务合理违规用 `@file:Suppress("RuleName")` + 理由注释
- **只跑 compile/test 会漏 spotless/detekt → CI 必挂**（T4/T6/T7 三票 9 个违规的教训）
- 截图任务：`recordRoborazziDebug` 本机极慢——**默认禁止**，截图相关先问用户；verify 必须跑

### 1.5 提交与 PR

- 提交信息 = **Conventional Commits**：`type(scope): description`（feat/fix/refactor/chore/docs/test/perf）
- PR：
  ```bash
  git push -u origin feature/tX-xxx
  gh pr create --title "<conventional title>" --body "Fixes #<N>" --base main
  ```
- PR body 写 `Fixes #N` → 合并时自动关票
- **合并策略**：复杂/多提交修复波用 **merge commit 保留历史**（不 squash，便于 git bisect/blame）；单一小改动可 squash。合并消息仍 Conventional Commits
- PR 标题遵循 Conventional Commits（squash 时作为 commit message）

### 1.6 CI 与合并

- CI = GitHub Actions Quality Gate（spotless→detekt→lint→konsist→test→roborazzi→assemble）
- 等 CI：`gh run list` + `gh run watch <id>`（不用 sleep 循环）
- CI 绿 + mergeable → 合并 → 确认票自动关闭（Fixes）
- 合并后：删远端分支（merge 时 --delete-branch）、删本地分支、worktree 清理

## 2. 多票并行（worktree + wave 模式）

- **wave 模式**（用户拍板）：单票拆 5-7 个小任务，波内并行派子代理、波间串行；每票结束跑全门禁 + 提交 + 关票
- worktree 物理隔离文件冲突；共享构建缓存（秒级增量）
- **app 模块是共享冲突点**——多个票同时改 app/（MainActivity/manifest/导航装配）必打架，app 收尾串行
- 同 worktree 内并行任务：按文件域拆分（prompt 里写死边界），预测冲突点
- 每票独立分支独立 PR 独立 CI

## 3. 子代理派发规范（如果任务由子代理执行）

Prompt 必须包含：
1. **WORKDIR**（绝对路径，worktree 或主工作区）
2. 验证命令**全门禁**（spotlessCheck + detekt + 相关模块 compile/test）——禁止只给 compile/test
3. 文件域硬边界（哪些文件可以动，哪些禁碰）
4. **不提交**（由主代理统一提交）或明确提交授权
5. 构建输出不过滤（一次跑完看完整输出）
6. 不用 LSP（Gradle 输出为准）
7. edit 超时验证（grep 确认落地）
8. **防卡条款**：Gradle 命令强制 `timeout 420` 超时即停报告；禁无限等待/重试死磕；截图/record 任务禁跑
9. 停手条件（被墙/依赖冲突/库不支持 → 停手报告，不硬闯）

## 4. 验收标准

- [ ] 规格验收标准逐条核对（issue body 里）
- [ ] 全门禁绿（1.4 命令级）
- [ ] CI 绿 + PR 合并 + 票关闭
- [ ] 截图基线改动有说明（verifyRoborazziDebug 是硬门禁，基线改动需明确）
- **UI 相关票**：合并前必须有真机/截图可验证的验收环节（行为层——主题/语言/导航——单测覆盖不了，真机走查机制待建，见 docs/agents/project-status.md 遗留项）

## 5. 停手条件（遇到即停，报告，不硬闯）

- push/下载被墙（HTTP 000/超时）→ 报告，等用户开代理（**禁止自行 sudo mihomo**）
- 截图 record 卡死 >10 分钟 → 降级方案或报告
- 依赖版本冲突（Kotlin 2.3.21 / compileSdk 36 兼容）→ 报告冲突详情
- 库不支持某功能（如 mikepenz 0.38.1 无 details 槽）→ 记录为缺口，不硬造

## 6. 项目级 Skills（渐进披露，省 token）

项目特定知识放在 `.agents/skills/<name>/SKILL.md`（30+ 代理自动发现的标准位置），**模型只在任务匹配时按需加载**——不占常驻上下文，不污染首轮请求（对 DSH × V4 Pro 尤其重要：大注入会拉歪锚定轨迹）。

现有项目 skills：

| Skill | 用途 |
|---|---|
| `appdev-build-gates` | 全门禁验证命令、常见违规修复、截图限制——任何实现/修复任务必载 |
| `appdev-ticket-flow` | ticket → 分支 → TDD → 门禁 → PR → 合并全流程速查 |
| `appdev-dsh-run` | DSH × V4 Pro 执行方式（热身轮 + 指针式投递 + 验收线） |

新增项目 skill 的规则：
- SKILL.md 结构：YAML frontmatter（`name` + `description` 触发条件）+ 正文
- **只放项目特有知识**（命令/约定/坑），不放通用编程知识
- 描述写清触发场景（如「任何 Android 构建/测试/提交前」），模型靠 description 决定是否加载

