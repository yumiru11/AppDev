---
name: appdev-ticket-flow
description: Use when executing a ticket (GitHub issue) in the AppDev repo — branch naming, TDD loop, PR creation with Fixes #N, merge strategy (merge commit vs squash), and multi-ticket worktree parallelism.
---

# AppDev Ticket 执行流程

完整版见 `docs/agents/workflow.md`。本 skill 是速查卡。

## 流程总览

```
读 ticket（gh issue view）→ 建分支 → TDD → 全门禁 → 提交 → push → PR(Fixes #N) → CI 绿 → 合并 → 关票
```

## 分支

- `feature/tX-<kebab>`（如 `feature/t12-repo-management`），基于 origin/main
- **铁律：不提交 main、不 push main**；worktree 并行时子代理 prompt 必须带 WORKDIR
- 新 worktree 首次构建前必须复制 local.properties：
  ```bash
  git worktree add /home/zhiyi/dev/appdev-tX feature/tX-xxx origin/main
  cp /home/zhiyi/dev/AppDev/local.properties /home/zhiyi/dev/appdev-tX/
  ```

## 提交信息（Conventional Commits，强制）

`type(scope): description` —— type: feat/fix/refactor/chore/docs/test/perf

```
feat(repo): 仓库管理 Star/Watch/Fork
fix(auth): 401 错误分类修复
docs(ui): 两轮 grill 决策落盘
```

## PR 与合并

```bash
git push -u origin feature/tX-xxx
gh pr create --title "<conventional title>" --body "Fixes #<N>" --base main
gh run list && gh run watch <run-id>   # 等 CI（不 sleep 循环）
```

- PR body 写 `Fixes #N` → 合并自动关票
- **合并策略**：复杂/多提交修复波用 **merge commit 保留历史**（不 squash）；单一小改动可 squash
- 合并后清理：远端分支（--delete-branch）、本地分支、worktree

## 多票并行（wave + worktree）

- 单票拆 5-7 小任务，波内并行、波间串行；每票独立分支/PR/CI
- **app 模块是共享冲突点**（MainActivity/manifest/导航装配）——app 收尾串行
- 同 worktree 并行任务按文件域拆分（prompt 写死边界）

## 验收标准

- [ ] issue body 验收逐条核对
- [ ] 全门禁绿（见 appdev-build-gates）
- [ ] CI 绿 + 合并 + 票关
- [ ] UI 相关票：合并前需真机/截图可验证环节
