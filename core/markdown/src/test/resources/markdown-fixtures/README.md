# GFM 夹具集（`plan.md` §2.3 渲染目标清单）

每个 `.md` 文件**恰好对应 `plan.md` §2.3 的一条原子条目**，文件名的数字前缀即清单顺序。
夹具正文是**纯 Markdown**：元数据（对应哪一条、支持哪些渲染路径、备注）一律不写进文件，
以免污染像素基线。

## 唯一的元数据来源

| 想知道 | 看哪里 |
|---|---|
| 这个夹具对应 §2.3 的哪一条、支持哪些渲染路径、为什么 | `core/markdown/src/test/kotlin/.../fixture/MarkdownGfmFixtures.kt` 的 `ALL` |
| 逐条现状表（✅/🟡/❌）+ 缺口 + 缺陷清单 | `docs/agents/markdown-consistency-2026-09-11.md` |
| 谁在消费这些夹具 | `MarkdownGfmFixtureCatalogTest`（自校验）· `GfmNativeParserCapabilityTest`（解析层）· `MarkdownFixtureScreenshotTest`（原生像素）· `WebViewFixtureRenderModeTest` / `WebViewOfflineGfmCapabilityTest`（WebView 产物层） |

## 三条渲染路径（ADR-0007）

| 路径 | 载体 | 说明 |
|---|---|---|
| `NATIVE` | `EnhancedMarkdownViewer` / `MarkdownViewer` | 评论列表、通知等短文本 |
| `SERVER_HTML` | GitHub 服务端 HTML + WebView | README 一级通道（GitHub 自己渲染） |
| `OFFLINE_GFM` | markdown-it + renderer.js + WebView | Issue 正文、README 降级通道 |

`paths` 里没有某条路径 = 该路径**渲染不出与网页端等价的结果**（不是「没测」）。

## 新增 / 修改夹具的规矩

1. 一个 `.md` 只放一类写法，保持短小（像素基线在 `w480dp-h1600dp` 视口内不裁剪）。
2. 同步在 `MarkdownGfmFixtures.kt` 的 `ALL`（及 `CLAUSES`，若是新的 §2.3 条目）登记；
   **漏登记 = 夹具不生效**，`MarkdownGfmFixtureCatalogTest` 会红。
3. 同步更新 `docs/agents/markdown-consistency-2026-09-11.md` 的夹具表；
   `MarkdownConsistencyReportTest` 会逐行比对，**不改报告会红**。
4. 新增 `NATIVE` 夹具后需要录制像素基线 —— 只能走 CI 的
   `Record screenshots (CI canonical)` workflow，**本机禁止 `recordRoborazziDebug`**。
5. 不要往夹具里放需要真实网络的资源（例：shields.io 徽章）：像素会不确定，
   破坏基线可复现性。网络类覆盖请写单元测试或走真机走查。
