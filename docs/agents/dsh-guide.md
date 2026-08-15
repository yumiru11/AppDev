# DSH × DeepSeek V4 Pro 使用指南（AppDev 专用）

> 本指南沉淀「如何用 DeepSeek Harness（dsh）+ DeepSeek V4 Pro 在 AppDev 高效执行任务」。基于 2026-08-15 的实证研究（xiaobright/dsh-anchored-standard + modeltest 45 会话控制实验，详见 `~/Documents/dsv4p-dsh.md` 与 `~/dev/AppDev/tmp-prompt.md`）。
> **目标**：让 V4 Pro 发挥全部能力（minimal 系 96-99 vs standard 系 91-92 的差距）+ 省 token + 减少反复探索。

## 1. V4 Pro 的模型特点（决定一切的前提）

**一句话：V4 Pro 是「接口敏感」模型——首次请求的 API 可见内容（工具 schema + persona + 注入状态）决定整条会话的策略区域。**

| 特点 | 实证（xiaobright 45 会话实验） |
|---|---|
| 首轮工具 schema 决定性 | minimal 两工具（bash + str_replace_editor）5/5 锚定（`we` 风格、let me=0）；standard 系 schema 11/11 掉入 standard-like（let me=208） |
| 首轮决定后续 | 锚定后恢复 25 工具，轨迹保持（355 块仅 1 次 let me）；首轮拉歪后**同一会话无法补救，必须重开** |
| 任务措辞主导指纹 | 中文大任务书 + 过程叙述（「现在开始。第一步：」）→ `Let me start by`；英文祈使短句 → `We need` |
| 中文题面无英文指纹 | 中文思考时 we=0/letMe=0（分类器记 ambiguous）——**验收不能用 "We need" 字样，用 let me 计数** |
| 注入抑制锚定 | 9KB 技能目录注入 0/9 锚定 vs 无注入 ~81%；AGENTS.md 摘要/技能目录提示会拉歪首轮 |
| 大任务失控风险 | standard 路径下出现过 400k 上下文、45 分钟、$0.46 的失控探索（搜索范围/停止条件/工具预算全漂移） |

**对 AppDev 的含义**：
- 项目文档（AGENTS.md/workflow.md）是**模型自举的载体**，但**不要让 dsh 在首轮注入它们**（suppressedContextSources 已处理）——模型用 read 工具自己读
- 任务书给「指针」（读哪个文件），不给「全文+流程叙述」
- 验收线 = `let me` 计数趋近 0

## 2. 推荐执行方式：anchored-standard + 两轮投递

### 2.1 环境准备（阶段 0）

1. 安装 preset：`~/.dsh/.agent-presets/anchored-standard`（克隆 xiaobright/dsh-anchored-standard，复制 preset 目录；已装好）
2. **DSH 完整重启**（preset 变更后必须重启）；新建空 session，选择 **Anchored Standard (experimental)**
3. 工作区干净（无本地技能目录注入更稳）
4. 不要在已产生内容的会话中途切换 preset

### 2.2 两轮投递（阶段 2 核心）

**消息 1（热身轮 · 英文 · 60 词内）**：
```
We need to <任务一句话> for the AppDev Android project.

First, please read the project documentation and existing implementation (<关键文档/模块路径>) and reply with a brief summary of your understanding.

Do not start any code changes yet — the full task specification arrives in my next message.
```
- 检查回复思维链：**无 `let me` 首行即合格**；不合格 → **重开会话**（不在同一会话补救）
- 热身轮做真实文档阅读（两工具够用，回复即晋升信号）

**消息 2（执行轮 · 英文指针）**：
```
Please read and strictly follow <任务文件路径>:
- The task specification section is authoritative (do not add or remove requirements);
- The appendix is the verified output of the preparation session: facts are already verified — adopt them directly, do not re-verify;
- Degradation plans are pre-approved — apply them directly when needed.
- Report per the final report format at the end of the file. Start.
```
- 完整规格通过文件读取进入上下文（大内容不碰 bootstrap 请求）
- 任务文件 = 准备会话产出的执行提示包（含前置状态表/计划/风险/降级授权）

### 2.3 执行节奏约束（省 token 硬约束）

任务文件里必须写：
1. 回复后第一件事：输出 ≤10 行执行计划确认 → **立即开始**
2. **禁止重复探索**：已核实事实直接采用，不重读文档/不重验/不跑基线
3. 按需读文件（只在需要精确 API 时）；无目的浏览禁止
4. 每个动作都有产出；降级即走（授权过的降级直接执行）

## 3. 任务文件（执行提示包）模板

```
# <任务名> — 执行提示包

## 消息 1（热身轮）  /  消息 2（执行轮）     ← 上文格式
## 0. 任务定位
## 0.1 执行节奏（硬约束）
## 1. 前置状态（已核实表：仓库/分支/基线/依赖/能力边界/参考实现）
## 2. 目标产物（清单式）
## 3. 用户已拍板的设计要求（权威，逐条列出）
## 4. 验证与降级（全门禁命令 + 授权降级方案）
## 5. 开发规则（分支/提交/门禁/TDD/i18n）
## 6. 已知风险与授权（截图超时/依赖冲突/库不支持 → 直接降级）
## 7. 停手条件（被墙/版本冲突 → 停手报告）
## 8. 交付物清单
## 9. 最终报告格式
```

## 4. 验收与监控

| 阶段 | 检查项 |
|---|---|
| 热身回复 | `let me` 趋近 0（容忍晋升瞬间 1 次扰动）；阶段回复少 |
| 执行期 | 提交节奏符合计划、每步可编译；重复验证/无目的浏览 → 一条短指令纠正 |
| 降级场景 | 应直接执行授权降级并在报告说明（不等待） |
| 交付 | 按最终报告格式核对；质量门禁命令级对齐 CI；报告标注降级项 |

## 5. 备选方案与边界

- **不想拆消息** → whoami-standard preset：热身轮自动推迟真实消息（代价：多一次调用，工具轮次风格不承诺 we）
- **纯知识问答类** → anchored 依赖首轮调工具晋升，知识题可能永不晋升 → 用 zero/whoami 变体
- **promoteOn**：默认 `either`（首答纯文字也会晋升）；改 `tool-call` 会困死纯文字首答
- **边界（诚实声明）**：98/99 是同一题 n=2 复现，不构成跨任务通用认证；无第三方复现；「we 风格=高分」已被 Flash 反例证伪。本指南是**实践优先**的配置，不是科学结论
- rc.6 预构建包 `bootstrapMaxTokens` 被覆盖不生效——只依赖 schema 锚定 + 预热/拆分

## 6. AppDev 实战记录

| 任务 | 结果 |
|---|---|
| README 原型（2026-08-15） | 8KB 中文任务书直发 anchored → 首块 `let me start with`（失败案例）；改用两轮投递 + tmp-prompt.md → 热身回复无 let me（合格） |

## 7. 相关资源

- `~/Documents/dsv4p-dsh.md` — 完整研究记录（时间线/实验矩阵/结论）
- `~/dev/AppDev/tmp-prompt.md` — 实战执行提示包样例
- `~/.dsh-anchored-src/` — anchored-standard 源码（README.zh-CN.md 中文说明）
- `~/.modeltest-src/` — xiaobright 评测源码（docs/v4.1/ 三份核心文档）
- xiaobright/dsh-anchored-standard（GitHub，issue #6/#11/#12/#17 + PR #19）
- deepseek-ai/deepseek-harness（`~/dev/deepseek-harness`，官方，minimal preset = 基准方法论）
