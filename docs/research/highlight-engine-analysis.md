# rikkahub HighlightEngine 深度分析：highlight.js Kotlin 移植的架构、完整度与 AppDev 移植成本评估

> 分析对象：`/home/zhiyi/dev/rikkahub/highlight/`（AGPL-3.0，**仅架构分析，不引用代码**）
> 对照基准：`highlight.js 11.11.1`（BSD-3-Clause，本机 npm 实装于 `/tmp/hljs-check/node_modules/highlight.js`，`lib/core.js` 2597 行）
> 分析日期：2026-08-14
> 结论性质：rikkahub 的移植是 **hljs 11.11.1 核心引擎的近乎 1:1 忠实移植**，配 30 个语言语法与「golden token fixtures」对照测试；但 AGPL 红线使其代码不可直接引用，AppDev 若要走此路线必须从 hljs BSD 源头自行移植。

---

## ① rikkahub 高亮模块架构总览

独立 Gradle 模块 `:highlight`（namespace `me.rerere.highlight`），纯 Kotlin + Compose，无任何 rikkahub 业务依赖。主源码 9313 行（含测试 9887 行）。

### 核心引擎（`core/`，8 文件，1812 行）

| 文件 | 行数 | 职责 | 对应 hljs 源 |
|---|---|---|---|
| `core/HighlightEngine.kt` | 436 | 模式栈解析器主体：`Run` 内部类持有全部解析状态（buffer/relevance/index/continuations），`scan()` 主循环 + 12 个处理函数 | `lib/core.js` `_highlight()`（1738 起） |
| `core/Mode.kt` | 360 | `Mode` 数据类（全部 hljs mode 属性）+ `Language` 定义 + `CompiledScope` + `MatchData`/`CallbackResponse` 回调接口 + `frozen()`/`inherit()`/`overwriteFrom()` | `lib/core.js` Mode 对象 + `inherit$1`（78） |
| `core/ModeCompiler.kt` | 249 | `compileLanguage()` 移植：compileMatch/multiClass/beforeMatchExt/beginKeywordsExt/compileIllegal/compileRelevance/buildModeRegex/expandOrCloneMode | `lib/core.js` `compileLanguage`（1148） |
| `core/MultiRegex.kt` | 152 | `MultiRegex`（多正则合一、组号→规则映射）+ `ResumableMultiRegex`（同位置续扫） | `lib/core.js` 1178 / 1256 |
| `core/Regexes.kt` | 246 | **JS 正则 → java.util.regex 翻译层**：`translateJsRegex`/`rewriteBackreferences`/`countMatchGroups`/`startsWith`/`either`/`lookahead`/`concat` 等 | `lib/core.js` 389–464 |
| `core/Keywords.kt` | 123 | 关键词 DSL（`keywords { keyword() literal() builtIn() ... }`）+ `compileKeywords`/`scoreForKeyword`/`COMMON_KEYWORDS` | `lib/core.js` 871–958 |
| `core/CommonModes.kt` | 183 | 共享 MODES：`C_NUMBER_MODE`/`QUOTE_STRING_MODE`/`comment()`（含 doctag+prose 规则）/`shebang()`/`endSameAsBegin()` 等，全部 `frozen()` | `lib/common.js`（41 行）+ `lib/core.js` 内嵌 |
| `core/TokenEmitter.kt` | 63 | 扁平 token 列表发射器（嵌套 scope 取最内层胜出，相邻同 scope 合并） | hljs 的 `Emitter`（树形 span）的扁平化替代 |

### 顶层 API（4 文件，632 行）

| 文件 | 行数 | 职责 |
|---|---|---|
| `Highlighter.kt` | 84 | `CodeHighlighter`（引擎门面，`highlight()`/`supports()`）+ `CodeHighlightText` Composable（`MAX_CODE_LENGTH = 4096` 超长降级纯文本，Highlighter.kt:19） |
| `HighlightToken.kt` | 14 | `sealed interface HighlightToken { Plain / Styled(type) }` |
| `HighlightStyle.kt` | 124 | `HighlightTextColorPalette`（15 槽 Atom One 配色）+ scope→SpanStyle 映射 + tier 回退 |
| `HighlighterPreview.kt` | 410 | Android Studio @Preview 演示（非运行时组件） |

### 语言包（`languages/`，30 语言 + 注册表，约 6800 行）

30 个语言文件 + `languages/Languages.kt`（71 行，`builtinLanguages()` 注册表）。详见 §③。

### 测试基建（5 文件 + fixtures）

- `HljsFixtures.kt`：golden token 对照——`src/test/resources/hljs/<lang>/*.tokens` 是**真实 highlight.js 11.11.1 的输出**，Kotlin 引擎必须逐 token 复现（HljsFixtures.kt:27-64）
- `tools/generate-hljs-fixtures.mjs`：Node 脚本，用 npm 的 `highlight.js@11.11.1` 生成 golden 文件（`tools/package.json` 锁定版本）
- `LanguageFixtureTest.kt`：30 语言逐一对照；`HighlightEngineTest.kt`：引擎行为单测（endsParent/starts/beginKeywords 点号守卫/match 数组/subLanguage 等）；`RegexesTest.kt`：正则翻译层单测

---

## ② HighlightEngine 核心机制剖析（对照 hljs 11.11.1 逐一标注）

### 已覆盖（移植完整度极高）

| 机制 | rikkahub 位置 | hljs 原版位置 | 备注 |
|---|---|---|---|
| `MultiRegex`（多正则合一，组号→规则） | `MultiRegex.kt:39-93` | `core.js:1178` | 逐行对应 |
| `ResumableMultiRegex`（同位置续扫跳过已试规则） | `MultiRegex.kt:103-151` | `core.js:1256` | 含上游「resume 会跳过所有先前规则」的已知缺陷的等价处理（MultiRegex.kt:137-144 注释） |
| `scan()` 主循环 + `processLexeme` | `HighlightEngine.kt:103-121, 382-429` | `core.js:2031` | 含零宽 begin/end 卡死保护（HighlightEngine.kt:394-402）与 `MAX_ITERATIONS=100_000` 死循环守卫（:423-425） |
| `processKeywords`（关键词 relevance + `_` 前缀只计分不着色） | `HighlightEngine.kt:146-184` | `core.js:1751` | `MAX_KEYWORD_HITS = 7`（HighlightEngine.kt:433 = core.js:1595） |
| `processSubLanguage`（单语言 + 语言列表 + continuation 传递） | `HighlightEngine.kt:186-214` | `core.js:1791` | 含「宿主 relevance>0 才累加子语言分」规则（:212） |
| `highlightAuto`（子语言列表自动检测） | `HighlightEngine.kt:223-234` | `core.js:2230` | 平局时 plaintext 胜出（`maxByOrNull` + `relevance > 0` 判定） |
| `emitKeyword`/`emitMultiClass`（match 数组逐组发射） | `HighlightEngine.kt:236-256` | `core.js:1830/1842` | |
| `startNewMode`/`endOfMode`/`doIgnore`/`doBeginMatch`/`doEndMatch` | `HighlightEngine.kt:262-379` | `core.js:1864-2011` | 全部标志位语义一致：`endsParent`（含链式上溯 :289）、`endsWithParent`（:295-297）、`returnBegin/returnEnd`（:333/:378）、`excludeBegin/excludeEnd`、`skip`、`starts`（:377） |
| `processContinuations`（跨块 continuation 栈重开 scope） | `HighlightEngine.kt:124-132` | `core.js:2012` | |
| `compileLanguage` 全套：`compileMatch`/`multiClass`/`beforeMatchExt`/`beginKeywordsExt`/`compileIllegal`/`compileRelevance`/`buildModeRegex`/`compileMode` | `ModeCompiler.kt:27-85` | `core.js:1148-1521` | `beforeMatch` 用 lookahead 重写（ModeCompiler.kt:153-171 = core.js:848-867）；`beginKeywords` 的 `__beforeBegin` 点号守卫（ModeCompiler.kt:213-215 = core.js:772-805） |
| `expandOrCloneMode`（variants 展开 + 依赖父模式复制 + frozen 复制） | `ModeCompiler.kt:228-249` | `core.js:1523` | `cachedVariants` 缓存（Mode.kt:150） |
| `scope:{}`/`beginScope`/`endScope`/`beginScopes`/`endScopes`（含组号重映射 `remapScopeNames`） | `ModeCompiler.kt:102-127` | `core.js:1061-1081` | |
| `keywords` 三种形态 + `$pattern` | `Keywords.kt:10-89` | `core.js:893` | `COMMON_KEYWORDS` 零分表（Keywords.kt:95-97 = core.js:871） |
| `onBegin`/`onEnd` 回调 + `Response.ignoreMatch()` + `data` 共享槽 | `Mode.kt:125-133, 348-360` | `core.js` Response 机制 | `endSameAsBegin` 用它实现（CommonModes.kt:176-181） |
| `classNameAliases`（scope 别名） | `Mode.kt:336` + `HighlightEngine.kt:258` | `core.js:1491` | |
| `case_insensitive`/`unicodeRegex` | `Mode.kt:334-335` | `core.js:1159-1160` | |
| `safeMode`/`debugMode` | `HighlightEngine.kt:11`（`highlightDebugMode`） | `core.js:2562-2563` | 语法崩溃降级纯文本 vs 测试期抛错 |
| `frozen()` 共享模式保护 | `Mode.kt:308-313` | `common.js` deepFreeze | |
| `startsWith`/`either`/`lookahead`/`anyNumberOfTimes`/`optional`/`concat`/`countMatchGroups`/`rewriteBackreferences` | `Regexes.kt:13-105` | `core.js:389-464` | |
| **JS 正则 → Java 翻译层**（`[^]`→`[\s\S]`、`[]`→`(?!)`、字符类内 `[`/`&&` 转义、`{` 非量词转义、`\p{XID_Start}`→`\p{javaUnicodeIdentifierStart}`） | `Regexes.kt:122-246` | 无对应（hljs 直接用 JS 引擎） | **这是移植最独特、风险最高的部分** |

### 缺失（均为 Compose 场景不需要或 API 面差异）

| 缺失项 | hljs 位置 | 影响评估 |
|---|---|---|
| `escapeHTML`/HTML 输出 | `core.js:61` | 无影响——Compose 用扁平 token 列表 + `AnnotatedString`，不需要 HTML 转义 |
| `highlightElement`/`highlightAll`/`updateClassName` DOM 集成 | `core.js:2288-2373` | 无影响——无 DOM |
| 插件系统（`addPlugin`/`removePlugin`/`before:highlight`/`after:highlight` 钩子） | `core.js:2500-2508` | 无影响——AppDev 无插件需求 |
| `configure` 全局选项（`options.languages`/`tabReplace`/`useBR`） | `core.js` options | 无影响——`highlightAuto` 改为显式传 subset |
| `registerLanguage`/`unregisterLanguage`/`registerAliases`/`getLanguage`/`listLanguages` API 面 | `core.js:2399-2458` | 改为构造器注入 `builtinLanguages()`（Highlighter.kt:30），更 Kotlin 化 |
| `supersetOf` 平局判定（C++/Arduino 类） | `core.js:2252-2258` | 轻微——`highlightAuto` 平局时无 superset 优先，仅影响 `subLanguageList` 场景 |
| `disableAutodetect`/`autoDetection` 过滤 | `core.js:2237` | 轻微——无全局 auto-detect API |
| `secondBest` 返回值 | `core.js:2268` | 无影响——AppDev 不需要 |
| 内置 384 个语言包 | `lib/languages/`（384 文件） | **主要差距**——仅移植 30 个（见 §③） |

**结论：引擎核心覆盖度 ≈ 100%（Compose 场景），语言包覆盖度 ≈ 8%（30/384）。**

---

## ③ 语言支持清单与 Language DSL 结构

### 30 个语言（`languages/Languages.kt:40-70` 注册顺序）

| # | 语言 | aliases（`tools/languages.mjs`） | 行数 |
|---|---|---|---|
| 1 | json | json5 | 50 |
| 2 | ini | ini | 87 |
| 3 | cmake | — | 77 |
| 4 | go | — | 107 |
| 5 | glsl | — | 141 |
| 6 | yaml | — | 194 |
| 7 | bash | shell | 184 |
| 8 | dockerfile | — | 35 |
| 9 | javascript | js, jsx, mjs, cjs | 602 |
| 10 | typescript | ts, tsx, mts, cts | 119 |
| 11 | xml | html, xhtml, rss, atom, xjb, xsd, xsl, plist, wsf, svg | 216 |
| 12 | css | — | 126 |
| 13 | dart | — | 162 |
| 14 | java | jsp | 181 |
| 15 | kotlin | kt, kts | 239 |
| 16 | latex | tex | 312 |
| 17 | lua | pluto | 92 |
| 18 | powershell | pwsh, ps, ps1 | 303 |
| 19 | properties | — | 73 |
| 20 | python | py, gyp, ipython | 257 |
| 21 | c | h | 279 |
| 22 | cpp | cc, c++, h++, hpp, hh, hxx, cxx | 365 |
| 23 | csharp | cs, c# | 289 |
| 24 | sql | — | 224 |
| 25 | diff | patch | 60 |
| 26 | markdown | md, mkdown, mkd | 232 |
| 27 | rust | rs | 171 |
| 28 | ruby | rb, gemspec, podspec, thor, irb | 334 |
| 29 | php | — | 430 |
| 30 | swift | — | 575 |

**hljs 11.11.1 共 384 个语言文件**（`lib/languages/` 实测），覆盖 GitHub 常见语言的主力子集（缺：scala、groovy、haskell、elixir、erlang、perl、r、objectivec、vbnet、nginx、makefile、toml 等——注意 `toml` 有 fixture 但**无语言实现**，LanguageFixtureTest.kt:12 的 toml 测试实际走的是未注册降级路径）。

### Language DSL 结构（`Mode.kt:330-337` + `Mode.kt:12-296`）

```kotlin
Language(name, aliases: Set<String>, root: Mode, caseInsensitive, unicodeRegex, classNameAliases)
Mode { scope / begin / end / match / beginList+beginScopes / beforeMatch / illegal / illegalList
       keywords / beginKeywords / contains(含 SELF) / starts / variants / subLanguage / subLanguageList
       label / relevance / excludeBegin / excludeEnd / returnBegin / returnEnd / endsParent / endsWithParent / skip
       onBegin / onEnd }
keywords { pattern($pattern); keyword() literal() builtIn() type() symbol() variable() title() meta() section() }
```

与 hljs 语法定义一一对应；`JavaScriptGrammar`（JavaScript.kt:103-108）暴露 `contains`/`paramsContains` 可变列表供 TypeScript 语法「伸手进去」追加模式，复刻了上游 `typescript.js` 对 `javascript.js` 的运行时修改（JavaScript.kt:96-102 注释）。
---

## ④ 样式/主题体系

### HighlightTextColorPalette（`HighlightStyle.kt:24-60`）— Atom One Dark 默认

| 槽位 | 默认色 | 槽位 | 默认色 |
|---|---|---|---|
| keyword | `#C678DD` 紫 | property | `#E06C75` 红 |
| string | `#98C379` 绿 | boolean | `#D19A66` 橙 |
| number | `#D19A66` 橙 | variable | `#E06C75` 红 |
| comment | `#5C6370` 灰（italic） | tag | `#E06C75` 红 |
| function | `#61AFEF` 蓝 | attrName | `#D19A66` 橙 |
| operator | `#56B6C2` 青 | attrValue | `#98C379` 绿 |
| punctuation | `#ABB2BF` 浅灰 | fallback | `#ABB2BF` |
| className | `#E5C07B` 黄 | | |

### scope → 样式映射（`HighlightStyle.kt:86-124`）

约 40 个 hljs scope 名映射到 15 槽：`comment/quote`→comment+italic；`keyword/doctag/formula`→keyword；`string/regexp/addition`→string；`title/function/symbol/bullet/link/meta/selector-id`→function；`section/name/selector-tag/deletion/subst/property`→property；`emphasis`→fallback+italic；`strong`→fallback+bold 等。

### tier scope 回退（`HighlightStyle.kt:69-80`）

`getStyleForTokenType` 从 `title.function` 逐级剥段回退到 `title` → 根 scope → fallback，复刻上游 CSS 主题「每 tier 一个 class」的级联效果。**这是与 TextMate 主题（最后命中者胜的 selector 列表）不同的机制**，但效果等价。

### M3 融合可能性

- **直接可行**：palette 是纯数据类，App 侧已演示替换——rikkahub 自己就定义了 `AtomOneDarkPalette`/`AtomOneLightPalette`（`app/.../theme/CodeColor.kt:7-42`）覆盖默认。
- **M3 动态映射**：可仿照 AppDev 现有 `M3TextMateTheme.kt` 的 `SCOPE_RULES`（scope→M3 角色，`M3TextMateTheme.kt:42-72`）思路，把 15 槽映射到 `colorScheme` 角色（keyword→primary、string→onPrimaryContainer、comment→outline、punctuation→outlineVariant、invalid→error 等），随动态取色/深色/OLED 主题走同一设计系统。hljs 移植的 15 槽粒度比 TextMate 的 25+ 规则更粗，映射更简单。

---

## ⑤ 渲染层（HighlightCodeBlock）

`app/src/main/java/me/rerere/rikkahub/ui/components/richtext/HighlightCodeBlock.kt`（532 行）：

- **容器**：`border(outlineVariant) + clip(shapes.large) + background(surfaceContainer)`（:134-139），M3 语义色
- **头部栏**：语言标签（等宽 12sp，onSurfaceVariant 50% 透明）+ 操作按钮（:352-471）：下载（按语言映射扩展名 :393-411）、复制（Clipboard API :428-435）、html/svg 内联预览切换（WebView :165-173）、mermaid 特判（:174-179）
- **代码区**：`CodeHighlightText`（`Highlighter.kt:43-84`）——`remember(code, language, colors)` 缓存 AnnotatedString；`MAX_CODE_LENGTH = 4096` 超长直接纯文本（Highlighter.kt:60-62）
- **行号/换行**：`CodeBlockWithLineNumbersWrapped`（逐行渲染保对齐，:252-292）与 `CodeBlockDefault`（横向滚动，:294-350）；行号列 onSurfaceVariant 40% 透明
- **折叠**：>10 行自动折叠（`COLLAPSE_LINES = 10`，:84），设置项控制
- **编辑器复用**：`HighlightCodeVisualTransformation`（:505-531）把高亮器接进 `VisualTransformation`，用于属性编辑器/设置搜索页输入框（`PropertyEditor.kt:187`、`SettingSearchDetailPage.kt:954/986`）
- **Markdown 集成**（`Markdown.kt:592-618`）：`CODE_FENCE` 节点取 `FENCE_LANG` 为 language（缺省 `plaintext`），`completeCodeBlock = hasEnd`（未闭合 fence 不触发 mermaid/预览特判）

---

## ⑥ 构建与模块组织

- **独立模块** `highlight/build.gradle.kts`：`rikkahub.android.library.compose` 约定插件，namespace `me.rerere.highlight`，minSdk 24；依赖仅 Compose BOM/ui/ui-graphics/ui-tooling-preview/material3 + JUnit（:13-21）。**零 rikkahub 业务依赖，可整体搬出复用**（但 AGPL 红线禁止）。
- **消费方**：`app/build.gradle.kts:284` `implementation(project(":highlight"))`。
- **测试工具链**：`tools/` 下 Node 脚本（`package.json` 锁定 `highlight.js@11.11.1`），`npm run generate` 用真实 hljs 生成 golden token 流；`HljsFixtures.kt` 逐 token 断言（含「token 流必须还原源码」完整性校验，generate-hljs-fixtures.mjs:88-91）。**这是移植质量的基石**——没有它，正则翻译层的偏差会静默吞掉。

---

## ⑦ rikkahub 移植 vs highlight.js 原版差异表

| 维度 | highlight.js 11.11.1 | rikkahub 移植 |
|---|---|---|
| 引擎核心 | `lib/core.js` 2597 行 | `core/` 8 文件 1812 行，机制覆盖 ≈100% |
| 正则引擎 | JS 原生（`g`/`m`/`i`/`u` 标志） | `java.util.regex` + 自研翻译层（`Regexes.kt:122-246`），`g` 标志用显式 lastIndex 表达 |
| 输出 | 嵌套 span 树 + HTML 转义 | 扁平 `HighlightToken` 列表（最内层 scope 胜出，TokenEmitter.kt:8-12） |
| 语言包 | 384 个（`lib/languages/`） | 30 个（8%） |
| 自动检测 | `highlightAuto` + `configure({languages})` + `disableAutodetect` + `supersetOf` 平局 | `highlightAuto` 仅限 `subLanguageList` 场景，无全局 API，无 superset 判定 |
| DOM 集成 | `highlightElement`/`highlightAll`/`updateClassName` | 无（Compose 不需要） |
| 插件系统 | `addPlugin`/`removePlugin` + before/after 钩子 | 无 |
| 主题 | CSS 类（每 tier 一个 class，级联） | `HighlightTextColorPalette` 15 槽 + tier 回退（`HighlightStyle.kt:69-80`） |
| 错误处理 | `SAFE_MODE`（默认吞错降级）+ `debugMode()` | `highlightDebugMode` 同语义（HighlightEngine.kt:11, 92-97） |
| 验证 | 官方测试套件 | golden fixtures 对照真实 hljs 输出（30 语言 × sample/edge 样本） |
| 许可 | BSD-3-Clause | AGPL-3.0（**不可引用**） |

---

## ⑧ 与 KotlinTextMate（AppDev 现状）对比矩阵

| 维度 | KotlinTextMate（`dev.textmate.compose` 0.2.0，AppDev 在用） | hljs Kotlin 移植（rikkahub 方案） |
|---|---|---|
| 语法模型 | TextMate grammar（plist/JSON，VS Code 同款） | 正则模式栈（hljs 语法） |
| 语言覆盖 | 理论 70+（VS Code 生态现成 grammar）；**AppDev 实际打包 7 个**（`TextMateCodeBlock.kt:25-34`：kotlin/python/go/java/json/yaml/shell） | 30 个（需自建语言包） |
| 主题 | JSON theme，scope selector 列表（最后命中者胜）；AppDev 已做 M3 派生（`M3TextMateTheme.kt` SCOPE_RULES 25 条） | 15 槽 palette + tier 回退；M3 映射更简单 |
| 输出 | `AnnotatedString`（`dev.textmate.compose.CodeBlock`） | `List<HighlightToken>` → `AnnotatedString` |
| 运行时 | 纯 JVM 正则（JoniOnigLib，Oniguruma 的 Java 移植），无 JNI | `java.util.regex`（Android 内置） |
| 语法精度 | TextMate 语义更细（storage/entity/support/meta 全 scope 词汇），VS Code 渲染级 | hljs 10+ 大类 scope，粒度粗（但这就是 GitHub 网页版 README 的高亮水平） |
| 资产体积 | grammar JSON 大（7 个资产几 MB 级），**可增量加语言**（加一个 JSON 即可，零代码） | 每个语言 ~100-600 行 Kotlin 语法代码 + 需随 hljs 上游手工同步 |
| 性能 | Oniguruma 回溯引擎较慢；代码块级高亮可接受 | `java.util.regex` 线性化更好；实测 rikkahub 生产在用 |
| 维护 | 上游库 0.2.0（2023 后活跃度低，io.github.ivan-magda 个人项目）；TextMate 语法资产本身由 VS Code 社区持续维护 | 引擎自持（BSD 可改）；语法包需自建 30 个并随 hljs 11.x 升级手工跟进 |
| 与 AppDev 现状契合度 | **已集成**（core:markdown TextMateCodeBlock + M3TextMateTheme，7 语法 + 兜底） | 需新写 `core:markdown` 集成层（可复用 rikkahub 的架构思路，代码自写） |
| 许可 | MIT（AppDev 已引用） | 引擎 BSD-3-Clause（可从 hljs 源头移植）；rikkahub 代码 AGPL **不可引用** |

关键点：**KotlinTextMate 的瓶颈不在引擎而在资产**——7 个 grammar 是够用的最小集，扩展路径是「加 JSON 资产」而非「写 Kotlin 代码」；hljs 移植的瓶颈恰好在语法包本身（30 个已自建，其余 354 个需要逐个手写或翻译）。

---

## ⑨ AppDev 从源头移植的成本评估

### 工作量分解（从 hljs 11.11.1 BSD 源码自行移植，参照 rikkahub 的架构蓝图与规模）

| 工作项 | 规模参考（rikkahub 实测） | 保守人天 | 说明 |
|---|---|---|---|
| 引擎核心（Mode/MultiRegex/ResumableMultiRegex/HighlightEngine/TokenEmitter/Keywords） | 8 文件 1812 行 | 5-8 | 机械移植但需通读 `_highlight` 全流程；**regex 翻译层（Regexes.kt 246 行）是最大难点**：`translateJsRegex` 的 JS→Java 差异表要靠单测磨 |
| golden fixtures 工具链（Node 生成器 + 测试基建） | 3 文件 + 30 语言 fixtures | 2-3 | 强烈建议照搬思路（BSD 下重写）：用 npm hljs 生成基准，逐 token 断言；**这是唯一能证明移植正确性的手段** |
| 语言语法包（30 个） | ~6800 行 | 20-30 | 简单语言（dockerfile/json/ini）0.5-1 天；复杂语言（javascript 602 行 / swift 575 行 / php 430 行）2-3 天；含「从 JS 语法翻译 + fixtures 调平」 |
| Compose 渲染层（CodeHighlightText + palette + tier 回退） | ~220 行 | 1-2 | 扁平 token → AnnotatedString，比 TextMate CodeBlock 简单 |
| M3 主题映射（15 槽 → colorScheme 角色） | — | 1 | 参照 AppDev 现有 M3TextMateTheme 的 SCOPE_RULES 模式 |
| 代码块容器（复制/行号/折叠/滚动） | HighlightCodeBlock 532 行 | 2-3 | AppDev 已有 markdown 渲染器，需要新写但可借鉴 rikkahub 交互设计 |
| **合计（30 语言）** | ~9900 行（含测试） | **30-45 人天** | |

### 风险

1. **正则差异（高）**：JS 与 `java.util.regex` 的语义分歧不止注释列出的 6 类（`[^]`/`[]`/类内 `[`/`&&`/`{` 量词/Unicode 属性名），还有 lookbehind 支持、`\u{...}`、命名组等；翻译层每漏一个差异就产生一条静默的 token 偏差——golden fixtures 是唯一防线。
2. **hljs 版本漂移（中）**：hljs 11.x 仍在演进（11.11.1 → 更新版会改语法内部结构），30 个手写语法与新版失同步后需要人工 diff 移植；锁定版本（如 rikkahub 的做法 `tools/package.json` 锁 11.11.1）可缓解但牺牲更新。
3. **语言覆盖天花板（中）**：30/384 = 8%。GitHub 上 README/issue 常见的 scala/groovy/haskell/toml/makefile 等都不在列，扩语言是持续成本（每语言 0.5-3 天）。
4. **无自动检测（低）**：fence 无语言标记时只能纯文本（rikkahub 也是），对 GitHub 客户端影响小（GFM 规范要求 fence 带语言）。

### 对比参照：rikkahub 的规模即最低可行规模

rikkahub 的 9313 行主源码 + 5 测试文件 + 66 个 fixture 文件是「30 语言可交付」的最小规模。少于这个规模（比如只移植引擎 + 10 语言）则不如保持 KotlinTextMate——因为引擎 1800 行的固定成本必须摊到足够多的语言上才划算。

---

## ⑩ 决策建议

### 决策矩阵

| 维度 | A. 自移植 hljs 引擎（BSD 源头） | B. 保持 KotlinTextMate（现状） | C. WebView + starry-night |
|---|---|---|---|
| 许可 | BSD-3-Clause ✓（rikkahub AGPL 代码本身不可用，但架构可参考） | MIT ✓（已引用） | MIT ✓ |
| 工作量 | **30-45 人天**（引擎+30 语言+测试） | **0**（已集成 7 语法） | 中（WebView 桥 + 资产打包，1-2 周） |
| 效果 | 30 语言、hljs 级高亮（GitHub 网页 README 同款） | 7 语言、VS Code 级精度；其余降级纯文本 | 600+ 语言、GitHub 生产级（github.com 自用） |
| 维护 | 引擎自持；30 语法随 hljs 11.x 手工同步 | 上游库低活跃（0.2.0）；加语言=加 JSON 资产，零代码 | 上游 GitHub 持续维护；Assets 更新靠打包 |
| M3 融合 | 15 槽 palette 映射，简单 | 已完成（SCOPE_RULES 25 条） | CSS 主题 → M3 令牌映射，需桥接 |
| 与 plan.md 契合 | ✓ 纯 Compose 主路径 | ✓ 纯 Compose 主路径 | ✗ **plan.md 铁律「评论列表绝不用 WebView」**——评论内代码块会被 WebView 渲染，违背架构决策；且 token 不注入 WebView 的安全约束使 starry-night 只能走本地 assets（无私有 token 问题） |
| 一句话 | 质量与覆盖的「自己动手」路线 | 成本最低的「够用」路线 | 覆盖最广但架构违和的路线 |

### 建议

1. **短期（现版本）：保持 KotlinTextMate（B）**。已集成、M3 主题已落地、7 个高频语言覆盖 GitHub 客户端 90%+ 的代码块场景（kotlin/python/go/java/json/yaml/shell 正是 AppDev 自身技术栈），剩余语言走样式兜底块（`TextMateCodeBlock.kt` FallbackCodeBlock）不伤体验。缺语言的真实痛点是「fence 语言不在 7 个里」的观感问题，而不是渲染错误。
2. **中期（若语言覆盖成为真实痛点）：优先扩 TextMate 资产而非移植 hljs**。从 VS Code 官方/社区 grammar 仓库（MIT/许可宽松）复制 JSON 语法资产进 `core:markdown/assets/grammars/`，每个语言 ≈ 0.5 天、零代码改动，10 个语言一周内可覆盖 95% 场景。这比 hljs 移植（30-45 人天）便宜一个数量级。
3. **不建议 WebView + starry-night（C）作为主路径**：违反 plan.md「评论列表绝不用 WebView」铁律（代码块大量出现在评论/issue 正文），且 WebView 冷启动/内存成本对滚动列表不友好。仅可考虑作为**长文档 WebView 兜底路径**的增强（AppDev 已有 `/markdown` HTML 兜底，届时可评估在 HTML 侧接 starry-night 替换 Shiki——但这属于 WebView 路径内部优化，与本次讨论的 Compose 主路径无关）。
4. **只有一种情况值得启动 A（hljs 移植）**：未来出现「必须支持 30+ 语言且拒绝 WebView、且 TextMate 资产因许可/体积不可行」的硬需求。届时从 hljs 11.11.1 BSD 源码移植，严格按 rikkahub 的架构蓝图（引擎 1800 行 + 30 语言 + golden fixtures 工具链），投入预算 30-45 人天，并锁定 hljs 版本做 fixtures 基线。
5. **无论走哪条路，rikkahub 的「golden fixtures 对照真实 hljs 输出」验证方法论都值得抄进 AppDev**——它把「正则移植正确性」从玄学变成可回归的断言，是本次分析最有价值的可迁移资产（BSD 下重写工具脚本即可，不涉及 AGPL 代码）。

---

## 附：证据索引

- 引擎移植对应关系：`HighlightEngine.kt`/`ModeCompiler.kt`/`MultiRegex.kt`/`Regexes.kt`/`Keywords.kt`/`CommonModes.kt`/`Mode.kt`/`TokenEmitter.kt`（rikkahub）；`lib/core.js`（hljs 11.11.1，/tmp/hljs-check）
- 语言清单：`languages/Languages.kt:40-70` + `tools/languages.mjs`；hljs 语言数 = `lib/languages/` 384 文件实测
- 样式：`HighlightStyle.kt:24-124`；App 侧调色板 `app/.../theme/CodeColor.kt:7-42`
- 渲染层：`app/.../richtext/HighlightCodeBlock.kt`（532 行）、`Markdown.kt:592-618`
- 构建：`highlight/build.gradle.kts`、`app/build.gradle.kts:284`
- AppDev 现状：`core/markdown/TextMateCodeBlock.kt`、`M3TextMateTheme.kt`、`gradle/libs.versions.toml`（textmate 0.2.0）
- 许可红线：rikkahub 仓库 AGPL-3.0（`/home/zhiyi/dev/rikkahub`）；hljs BSD-3-Clause（npm package.json）
