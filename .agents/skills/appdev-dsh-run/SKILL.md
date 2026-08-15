---
name: appdev-dsh-run
description: Use when preparing to run an AppDev task on DeepSeek Harness (dsh) with DeepSeek V4 Pro — build the warm-up message, pointer message, and task file following the anchored-standard two-round delivery, and check the let-me acceptance line.
---

# AppDev × DSH × V4 Pro 执行

完整研究见 `docs/agents/dsh-guide.md` + `~/Documents/dsv4p-dsh.md`。本 skill 是执行速查。

## 核心原理（30 秒版）

V4 Pro 是「接口敏感」模型：**首轮请求的工具 schema + persona + 注入状态决定整条会话的策略区域**。minimal 两工具锚定 → 96-99 分；standard 大 schema → 91-92 + 失控探索（400k 上下文）。anchored-standard preset = 首轮 minimal 锚定 → 晋升完整工具。

## 执行步骤

### 1. 环境
- preset 已装：`~/.dsh/.agent-presets/anchored-standard`（选择 Anchored Standard）
- **DSH 完整重启**；新建空 session；工作区干净

### 2. 消息 1（热身轮 · 英文 · ≤60 词）
```
We need to <任务一句话> for the AppDev Android project.

First, please read the project documentation and existing implementation (<关键文档/模块>) and reply with a brief summary of your understanding.

Do not start any code changes yet — the full task specification arrives in my next message.
```
**检查回复：无 `let me` 首行即合格；不合格 → 重开会话（绝不补救）**

### 3. 消息 2（执行轮 · 英文指针）
```
Please read and strictly follow <任务文件路径>:
- The task specification section is authoritative (do not add or remove requirements);
- The appendix is the verified output of the preparation session: facts are already verified — adopt them directly, do not re-verify;
- Degradation plans are pre-approved — apply them directly when needed.
- Report per the final report format at the end of the file. Start.
```

### 4. 任务文件（执行提示包）必须包含
```
0. 任务定位
0.1 执行节奏（硬约束：≤10 行计划 → 立即开工 → 按需读文件 → 降级即走）
1. 前置状态（已核实表——模型直接采用，不重验）
2. 目标产物（清单）
3. 用户已拍板的设计要求（权威，逐条）
4. 验证与降级（全门禁 + 授权降级）
5. 开发规则（分支/提交/门禁/TDD/i18n）
6. 已知风险与授权
7. 停手条件
8. 交付物清单
9. 最终报告格式
```

## 验收线

- `let me` 计数趋近 0（中文思考无英文指纹属正常，**不要找 "We need" 字样**）
- 阶段回复少；提交节奏符合计划；降级场景直接执行授权降级并在报告说明

## 红线（对 AppDev 任务）

- 任务书**不要**用中文大任务书 + 过程叙述（「现在开始。第一步：」）——直接触发 let me
- 项目文档由模型用 read 读（anchored 已抑制首轮注入），不要塞进消息
- 被墙/依赖冲突 → 模型停手报告，不硬闯
- 参考仓库 rikkahub 是 AGPL——只参考思路零复制
