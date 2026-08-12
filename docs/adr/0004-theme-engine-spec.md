# ADR-0004: 主题引擎规格（6 套主题 + 玻璃拟真收敛 + 图标策略）

- **状态**: Accepted (2026-08-12)
- **相关**: T6（#7 主题引擎）、docs/ui-design.md
- **决策者**: 用户 + 实施者（grill-with-docs 会话拍板）

## 决策

### 1. 六套预设主题

| 套 | 名称 | 说明 |
|----|------|------|
| 1 | Light | 浅色 |
| 2 | Dark | 深色 |
| 3 | OLED | 纯黑背景（AMOLED 省电） |
| 4 | Dynamic Light | Android 12+ 壁纸取色（API 31+，低版本回退 Light） |
| 5 | Dynamic Dark | Android 12+ 壁纸取色（API 31+，低版本回退 Dark） |
| 6 | High Contrast | 高对比（无障碍） |

### 2. 玻璃拟真收敛

- 首版**只做 2 处**：顶栏 + 底栏
- **设置里提供关闭项**（玻璃效果开关，DataStore 持久化）
- BottomSheet / 看图背景 / 横幅 3 处后置（性能验证后再推广）
- 实现：API 31+ RenderEffect 真模糊；26-30 半透明降级

### 3. 图标策略

- 当前：**静态库** `icons-material-symbols`（Rounded + 选中 Filled），不动摇已合入导航骨架
- 细体诉求（wght=300 + ROND=100）：**单独开评估票**调研 dev.vicart 变量字体
  - 评估通过 → T6 或后续引入；未通过 → 维持静态库
  - 评估维度：Kotlin 2.3.21 兼容性、字体资产体积、与 ImageVector 双体系混用、动态 FILL 性能

## 理由

- OLED 是 GitHub 客户端常见诉求；高对比覆盖无障碍
- 玻璃拟真先 2 处验证视觉与性能，避免 RenderEffect 在复杂页面的性能风险
- 图标双体系混用是风险点，静态库保底、变量字体须先评估
