# UI-4 / UI-6 证据图（不是基线）

本目录是 PR 的**对比材料**，不参与任何门禁（基线仍在 `feature/repo/src/test/screenshots/`，只能由
`record-screenshots.yml`（CI canonical）录制）。

| 文件 | 内容 |
|---|---|
| `ui-6-file-tree-time_light_compare.png` | UI-6 文件树修改时间列：Roborazzi 三栏对比（左 = 旧基线 / 中 = 像素 diff / 右 = 本 PR 渲染） |
| `ui-6-file-tree-time_dark_compare.png` | 同上，深色主题 |
| `ui-4-readme-empty_light.png` | UI-4 空 README 态收编 `AppEmptyState` 后的浅色渲染（本 PR 新增基线帧的本地实际产物） |
| `ui-4-readme-empty_dark.png` | 同上，深色主题 |

来源：本机 `:feature:repo:verifyRoborazziDebug` 失败产物（`build/outputs/roborazzi/`）。本机渲染与
CI runner **不是逐字节相同**，故这些图只作语义对比、不能当基线提交（#181 教训）。

UI-6 的 diff 栏只高亮新增的五条相对时间（`3 days ago` … `3 weeks ago`），其余像素（仓库头/分区 Tab/
分支 Chip/文件名/图标）逐字节不变。UI-4 的两个新帧覆盖此前的零基线状态（该空态此前只有非门禁的
模拟器 `repo-actions` 帧）。
