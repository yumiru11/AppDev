# ADR-0006：UI 导航架构与视觉规范（两层导航 + Material You 融合）

- 状态：**已接受**（2026-08-15，两轮 grill-with-docs 拍板）
- 日期：2026-08-15
- 关联：ADR-0004（主题引擎）、ADR-0005（渲染策略）；spec #1；`docs/ui-design.md`（权威 UI 规范，本 ADR 的详细落点）

## 背景

AppDev 的 UI 设计在两轮 grill-with-docs（`docs/ui-grill.md`、`docs/ui-grill-round2.md`）后全部决策点清零。原有 UI 规划（docs/ui-design.md 旧版）中的导航结构（首页 = 分区 Pager，底部 3 Tab 平等并列）与用户实际预期**不符**——用户在真机走查后明确：他要的是「首页是大的，套着小的」两层导航。此外玻璃/卡片/动效/图标等视觉决策在 grill 中逐项重拍，需要正式固化。

## 决策

### 1. 两层导航结构

- **底部 NavigationBar = 大分区**：首页 / 仓库 / 我的（3 个固定；「做哪些功能就加哪些」——后续可扩展）
- **首页大分区内部 = 顶部小分区条**：动态 / Issue / PR（首批；随功能扩展），样式 = 横向条 + 底部主题色指示条（GitHub 网页 Gists/Home/Issues 风格）
- **顶栏固定不动**，只有内容区横移 + 分区栏跟随内容（Android 风格「外壳固定」）
- 「仓库」不再是底部 Tab 的独立内容——它是底部 Tab（我的仓库列表）+ 首页内小分区（仓库动态）双位置？——**否**：用户拍板「仓库、issue、pr 等功能分区」是首页内部的小分区，底部 Tab 仓库 = 我的仓库列表（B 方案：首页 / 仓库 / 我的 3 Tab，首页内分区条 = 动态 / Issue / PR）
- 首页内容区顶部 = Home 字样 + 长条按钮（新建 Issue / 查看 PR / 新建仓库）+ feed 动态流 + trending repo 穿插（模仿 GitHub 网页登录后首页；用户确认示意图形态）

### 2. 玻璃拟真（替代 ADR-0004 的 5 处清单）

| 位置 | 决策 |
|---|---|
| 顶栏 / 底栏 / 通知面板 / BottomSheet | **默认开**（毛玻璃） |
| 图片全屏查看器 | **改纯黑**（沉浸优先） |
| FAB / 设置分组头 / 卡片 | **不用玻璃**（FAB 用自带阴影；分组用卡片区分；卡片不透明保可读性） |

- 毛玻璃的「本质」= macOS 访达侧边栏滚动穿越感（内容滚过栏下被糊掉，能看到有东西在动）
- 强度：中（8dp）；**全局总开关 + 设置页逐项开关**；OLED / 高对比主题下禁用背景图与玻璃
- API 31+ RenderEffect 真模糊，26-30 半透明纯色降级（沿用 ADR-0004 技术方案）

### 3. 卡片视觉（PiliPlus 风格）

- 卡片底 = 中性 surfaceContainer（浅色偏白、深色偏深灰）+ **带一点点主题色点缀**（参考 PiliPlus 首页卡片：恰到好处不突兀——不是整卡染色，是星标/语言点/链接/时间等局部点缀）
- 语言点用 GitHub 原色；星标/链接/时间用主题色
- 网格 / 通栏两种布局**用户可切换**（视图切换按钮）
- 长按弹小窗选功能（非左滑）

### 4. 动效

- 分区切换：非线性 + 微微回弹（加速→匀减速→过冲一点回弹）
- 页面转场（分区切换外）：fade / fade slide
- 通知面板滑入：与全局动效一致（非线性 + 微回弹，300ms 左右）
- 列表 stagger：可选开关；Star 星形弹跳；下拉刷新 M3 加载圈只刷当前分区
- **不用触觉反馈**；主题切换 Crossfade；WebView 深浅无闪烁同步

### 5. 图标

- 底部导航**选中实心 / 未选空心**必须做——已验证 `icons-material-symbols-{outlined,rounded,sharp}` + `-filled` 变体全在依赖中，**现在就能实现**（不等可变字体）
- 粗细 400 起、提供 300 选项；圆角 ROND 30-50（全局微圆润）
- **图标候选必须先给用户验证批准**（用户拍板）
- GitHub 独有功能一律 Octicons；**全应用禁 emoji 图标**（含 Alert 卡片，用户硬性要求）

### 6. 渲染细节（两条路线通用）

- 代码高亮：C 半融合（容器随主题 + 语法色 GitHub 原色）
- Alert 卡片：GitHub 网页样式（**带左侧色条**）+ Octicons 图标
- 引用块：竖条主题色 3dp + 淡底色；行内代码：rikkahub 效果（主题色底 + 主题色字）
- 排版基准：GitHub 网页基准（16sp/1.6），标题比例收敛；内容左右留白与主页一致
- 图片：点击向上 fade in + 圆角 + 阴影；表格 GitHub 样式；checkbox M3 风格

### 7. Trending 数据源

- 官方无 Trending API；采用 gh4a 同款：第三方 JSON 镜像（`Unpublished/GithubTrending`，raw.githubusercontent），fallback 到 Search API 变通（`created:>7d sort:stars`）

## 后果

### 正面

- 导航结构与用户预期一致（「大分区 × 小分区」）；仓库/Issue/PR 入口清晰
- 玻璃只出现在有存在感的位置；卡片可读性有保障
- 图标「空心/实心」无需等可变字体，可立即实现
- 渲染细节（Alert 色条/Octicons/半融合高亮）与 GitHub 网页对齐，减少返工

### 负面 / 待办

- **README 渲染方向（A/B/C）未定**——用户钦定先做 prototype（单场景复刻完整 README 双版本对照）再拍板（见 `docs/ui-design.md` §11）
- 图标候选清单待选给用户验证；卡片 PiliPlus 风格待实现时提取参照
- 已合入的 T3/T19/T24 需按新导航/通知分组/玻璃开关做一轮 UI 修复波
- 底部 Tab「仓库」与首页内小分区的关系需在实现时明确（我的仓库列表 vs 仓库动态）

## 参考

- `docs/ui-design.md`（2026-08-15 新版，权威规范）
- `docs/research/webview-material-you-fusion.md`（WebView 融合调研）
- `docs/research/highlight-engine-analysis.md`（高亮引擎对比）
- ~/dev/PiliPlus、~/dev/rikkahub、~/dev/XMSLEEP、~/dev/gh4a（参考仓库）
