# AppDev 执行流程手册（AI Agent 工作流）

> 本文件定义 AppDev 从「接到 ticket」到「合并关票」的完整执行流程。所有 AI 代理（OpenCode / DSH / 云端）在 AppDev 干活前必读。
> 配合阅读：`AGENTS.md`（环境/门禁/铁律）、`docs/agents/project-status.md`（当前进度）、`docs/agents/testing-strategy.md`（测试/覆盖率策略）。

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

- **覆盖率门禁**（Phase A 起）：`./gradlew jacocoTestReport`（或模块级）——覆盖率策略见 `docs/agents/testing-strategy.md`；CI PR 阶段加 diff coverage（新增代码 ≥80%）

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

## 4. 验收标准（2026-08-16 workflow grill 拍板版）

> grill 源：`docs/workflow-grill.md`（用户回答）；以下为执行规则。

- [ ] 规格验收标准逐条核对（issue body 里）
- [ ] 全门禁绿（1.4 命令级）
- [ ] CI 绿 + PR 合并 + 票关闭

### 4.1 验收卡 + 分级验收（grill 1.1）

- **每票交付物必须包含「验收卡」**：3-5 条可执行的真机操作 + 预期结果（用户照卡测）
- **分级**：★ 票代理自验 + 截图证据；★★★ 票用户真机验收；全局感受（导航/主题/图标）里程碑走查
- **验收卡由代理根据提交 diff 专门制定**——每任务必须针对任务内容详细定制测试，禁止套模板（grill 1.2/4.2）

### 4.2 PR 测试清单（grill 4.2）

- 每个 PR 附「测试清单」：根据 diff 给出详细测试步骤（用户审核用，先按卡做再自由玩 5 分钟）

### 4.3 验收标准编写（grill 10.2）

- **代理自写验收标准 + 用户审批**（票开工前过目）
- 验收标准分**硬/软**：硬 = 可断言（数字/行为/状态）；软 = 观感（写清参考物，走查兜底）
- 验收标准写**用户视角**（「我能看到徽章」「点链接进 app」）而非技术断言（grill 5.2）
- 规格写「意图 + 验收」而非「做法」（grill 5.2）

### 4.4 意图传递（grill 5.1/5.2/9.2）

- **重要结论必须直接读文档**，不允许代理用提示词概括后执行（避免偏差）
- UI 设计意图给出时**用 ASCII 图描述**（比文字精确）
- 代理遇到「规格与常识冲突」必须**停手问**，不硬闯
- grill 结论**写进文档**（ui-design.md/ADR），让代理自己读——不写进对话/提示词（避免偏差）

### 4.5 反馈前置（grill 6.1/6.2）

- **里程碑固定走查**：每 3-4 票合并后用户走查一次
- **关键页面首版即走查**：新页面第一版能跑就展示，不等全部做完
- **并行票共享反馈闸门**：多票并行做，合 main 前统一过用户

### 4.6 真机验收兜底（grill 1.2/4.1）

- 截图基准回归（工具调研落地后）：UI 票自动截图对比基准，偏差即挂起
- 用户不在场时：代理自验 + 证据链，拿不准标「需真人确认」挂起，不阻塞其他票
- 里程碑/阶段完成时用户亲自完整测试

### 4.7 截图基线规则（grill 3.2）

- **基线改动必须用户批准**（PR 里显式说明，verifyRoborazziDebug 是硬门禁）
- 截图自动化方向（grill 3.1，待调研）：找符合自动化需求的截图工具 → CICD 自动截图 + PR 评论 → 用户审查提修改意见直到 LGTM

### 4.8 设计闸门（grill 7.2/2.1）

- **票开工前过「设计是否已定」闸门**：设计已定（有 ADR/grill 结论）才派；开放性问题先调研 + 决策，允许自主设计但**用户审查后才算过**
- 原型先行仅限「复杂且能为后面铺路」的场景；常规 UI 用文字规格 + 截图锚点为主

### 4.9 爆炸半径控制（grill 8.1/8.2）

- Konsist 架构约束（已做）+ **共享文件清单**（AppNavHost/MainActivity/designsystem 等高冲突文件，改动前先查谁在改）+ **票规格标注波及模块**
- 全局性问题（背景/间距/图标体系）**开专项票**，不混入功能票

### 4.10 未定项（grill 7.1/10.1 用户待定）

- 云端代理验收标准升级、浪费接受度——待截图工具调研结论 + 后续实践后补

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

