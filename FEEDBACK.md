# AppDev 真机走查反馈汇总（2026-08-14）

> 本文档汇总用户真机走查（APK 亲测）的全部反馈，供 UI 设计收紧、bug 修复、需求确认使用。
> 状态图例：✅ 已修复 / 🔵 设计待定（需 grill）/ 📌 功能未做（依赖后续 ticket）/ ⚠️ 待验证

---

## 一、第一轮真机走查（2026-08-14 早）

| # | 反馈 | 根因 | 状态 |
|---|------|------|------|
| 1 | 底部「仓库」按钮点进搜索页 | T3 时代占位符（AppNavHost repos 路由指向 SearchScreen） | ✅ 已修复（T11 前占位页）|
| 2 | 游客模式上来全屏模糊（连导航栏都模糊） | HomeScreen 游客态无登录引导卡填充，GlassSurface 透出 | ✅ 已修复（游客直进首页 + 引导卡）|
| 3 | 主题跟随系统+动态取色时不随系统深色（保持浅色） | resolveEffectiveThemeMode 写死 DYNAMIC_LIGHT，未映射系统深色 | ✅ 已修复（systemDark 参数）|
| 4 | 语言切换时跳回登录页 + 中英超级快抽搐 | authState 无 initialValue 闪登录页 + LaunchedEffect 竞态多次 recreate | ✅ 已修复（configChanges + 初始态）|
| 5 | 底部图标无「按下实心/未按空心」区分 | 全用 material-icons-core Filled 集合 | 🔵 设计待定（Material Symbols 大票未落地，ADR-0004）|
| 6 | 设置图标风格切换无效 | AppIcon 令牌定义了风格但 UI 无消费点 | ✅ 已修复（消费点未落地前隐藏入口）|
| 7 | 令牌登录报「网络错误」 | GuestTokenProvider 恒返回 null → 一律 401 → 错误分类误导 | ✅ 已修复（SessionTokenProvider 真注入 + UNAUTHORIZED 分类）|

---

## 二、第二轮真机走查（2026-08-14 中，PR #59 首版后）

| # | 反馈 | 根因 | 状态 |
|---|------|------|------|
| 8 | 底部导航图标没有变换效果 | 胶囊增强被 revert 删除，未重应用 | ✅ 已修复（T19/T20/T24 合并后重应用胶囊+实心）|
| 9 | 各种卡片灰灰的不好看（含首页） | 卡片用 surfaceVariant 平灰，无主题色处理 | 🔵 设计待定（用户明示：应主题色轻微处理，避免对比度过大）|
| 10 | 行内代码框无圆角 | renderer 0.38.1 无 shape 槽（SpanStyle 无圆角） | 🔵 设计待定（0.43/WebView 解决）|
| 11 | md 标题文本过大 | Markdown 标题字号偏大 | 🔵 设计待定（需调）|
| 12 | 代码框直接显示原文本（不含 ``` 符号） | 语法加载失败/无语言 → 裸文本 | ✅ 已修复（语法 try/catch + 样式兜底+`</param><param name="code">块）|
| 13 | 图片不加载（README 等） | Coil 3 无 ImageLoader 装配（app 空壳）| ✅ 已修复（GitHubApp 装配 OkHttpNetworkFetcherFactory）|
| 14 | 顶部栏到系统通知栏留出距离 | 分区重构 Scaffold 双重 insets | ✅ 已修复（contentWindowInsets 归零）|
| 15 | 通知全部显示出错（登录正常） | 401/403 无分类 → UNKNOWN 误导文案 | ✅ 已修复（UNAUTHORIZED 分类）|
| 16 | 设置页进入后变浅色（跟随系统时） | SettingsScreen 调 resolveEffectiveThemeMode 未传 systemDark | ✅ 已修复 |
| 17 | 毛玻璃效果仅模糊顶/底栏，非预期 | 旧实现 `Modifier.blur` 糊的是栏自身内容；且常驻头部把列表视口推出玻璃矩形（#83 查到的几何根因）| 🟡 机制已修（Haze backdrop + 分区条入玻璃头 + 内容 full-bleed + 长条按钮随 feed 滚动），**待真机复测闭环**（见 PR 验收卡）|

---

## 三、第三轮真机走查（2026-08-14 晚，PR #59 二版后）

| # | 反馈 | 根因 | 状态 |
|---|------|------|------|
| 18 | 点击通知加载过程中立即崩溃 | Paging key lambda 里 lazyItems[index]（反模式）| ✅ 已修复（key = index）⚠️ 需复测确认 |
| 19 | README 图片完全无法加载 | Coil 装配缺失（同 #13）| ✅ 已修复 |
| 20 | md 的 [x] 框无法渲染 | checkbox 组件注入问题（T7 曾修，可能回归）| 待确认（本轮 PR 已含 checkbox 接线，需复测）|
| 21 | star history 无法加载 | 与 README 图同源（相对路径图）| ✅ 已修复（相对路径改写）|
| 22 | 代码框无高亮、无复制按钮 | 高亮依赖语言标注；复制按钮是未实现功能 | 高亮✅/复制按钮 🔵 新功能未做 |
| 23 | md 标题字体过大 | 同 #11 | 🔵 设计待定 |
| 24 | 底部图标仍无填充/空心变换 | 同 #5（material-icons-core 局限）| 🔵 设计待定 |
| 25 | 点击文档内同仓库文档用浏览器打开 | README 相对链接（./docs/x.md）解析失败 → External | ✅ 已修复（resolveMarkdownUrl + baseRepoUrl 接线）|
| 26 | README 有跳转链接但图片空白 | 相对路径图解析到假域 | ✅ 已修复（WebView relative URL 改写）|

---

## 四、第四轮反馈（2026-08-14 深夜）

| # | 反馈 | 根因 | 状态 |
|---|------|------|------|
| 27 | 某些 README 渲染出一大串 JSON 文本 | GitHub 对部分 README（openchamber 等）无视 html Accept 返回 API JSON | ✅ 已修复（JSON 响应识别 + 回退 markdown 渲染）|

---

## 五、设计方向性反馈（2026-08-14，需深度 grill）

| # | 反馈 | 含义 | 状态 |
|---|------|------|------|
| 28 | **WebView 渲染完胜原生**：链接按钮、引用块、折叠块、表格「比现在的渲染好太多成熟太多稳定太多」 | WebView（GitHub 服务端 HTML + github-markdown-css）效果碾压 mikepenz 0.38.1 原生渲染 | 🔵 架构转向调研完成（docs/research/webview-material-you-fusion.md），待用户决策落地 |
| 29 | WebView 核心痛点 = 不兼容 Material You 主题 / UI 风格（深浅色适配、圆角设计） | 融合是唯一障碍 | 🔵 调研已给方案（CSS 变量全覆盖 + data-theme），待立项 ADR-0006 |
| 30 | 用户提到**全局背景图**美化诉求（此前未提）| 背景美化是新增需求方向 | 🔵 调研已确认可行（背景图放 Compose 层 + 透明 WebView），待 grill 细节 |
| 31 | 用户对「完成度」焦虑：GitLight 同类项目因「做完了但不符合预期」被搁置 | 工作流缺陷：无真机验收，行为层测试覆盖弱 | 📌 教训已固化（记忆 #126/#127），UI 票开工前强制 grill |

---

## 六、已沉淀的 UI 需求方向（docs/ui-design.md，待 grill 细化）

- B 站式导航：底部 3 Tab（首页/仓库/我的）+ 顶栏胶囊搜索框 + 通知铃铛（通知 = 全屏滑入面板，非底栏）
- Material You 动态主题（6 套色板）+ ExtendedColors + 玻璃拟真（顶/底栏已落地）
- 图标 = Material Symbols wght=300 细体 + FILL 实心切换（未落地，ADR-0004 挂账）
- M3 Emphasized 动效（400/200/500ms）+ spring 弹性
- 首页 = 横向滚动 Tab + HorizontalPager（Trending/News/Issues/PRs）
- Stars 并入「我的」二级页面
- i18n 双语（en/zh-rCN）零硬编码

---

## 附：grill 与调研文档索引

- UI 设计全面 grill 问题 → `docs/ui-grill.md`
- 需外部调研的问题 → `docs/research-questions.md`
- WebView 融合深度调研 → `docs/research/webview-material-you-fusion.md`