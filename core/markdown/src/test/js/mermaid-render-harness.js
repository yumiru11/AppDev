'use strict';

/*
 * mermaid-render-harness.js —— 离线 Mermaid 渲染通道的 **Node 执行器**（测试用，不进 APK）。
 *
 * 与 math-render-harness.js 同构，覆盖三层（证明的不是「源码里有某个字符串」）：
 * 1. **真实 bundle 加载 + 真实 parse**：生产 assets 的 `mermaid/mermaid.tiny.js` 用
 *    `vm.runInNewContext` 按浏览器脚本语义执行（IIFE 顶层 `var` 在 Node 模块作用域里不会
 *    变成 global，`require()` 直接加载会失败），再用**真实的** `mermaid.parse` 校验
 *    §2.3 夹具 `34-mermaid.md` 的图语法、拒绝非图文本。
 * 2. **真实 renderer.js + 最小 fake DOM**：驱动 `renderMermaid(root)` 的选择/接管/回退语义
 *    （离线 `pre > code.language-mermaid`、GitHub README 的 `pre[lang="mermaid"]`、
 *    POST /markdown 的 `div.highlight-source-mermaid > pre`）。
 *    mermaid 引擎本身用**记录型 stub**（真实渲染需要浏览器布局引擎；这里测的是选择/配置/
 *    回退逻辑，真实栅格化由 mermaid-render-verify CI job（API 33 render / API 30 blocked）兜底）。
 * 3. **门禁矩阵 + bridge 上报**：`window.mermaid` 缺失、语法探针失败（模拟 Chromium < 94 的
 *    class static block 不支持）、`initialize` 抛异常、`run` 失败（suppressErrors 下 resolve
 *    但不产 SVG）—— 每条路径都必须回退为普通代码块且 DOM 无损，并经
 *    `AndroidBridge.onMermaidResult` 上报 `rendered,failed,engineSupported`（引擎被拦下时
 *    也必须上报 blocked 事实）。
 *
 * 用法：node mermaid-render-harness.js
 * stdout = 逐行 `[key] value`（JVM 侧按行解析，不做 JSON 依赖）。
 */

const path = require('path');
const fs = require('fs');
const vm = require('vm');

const ASSET_DIR = path.resolve(__dirname, '..', '..', 'main', 'assets', 'webview');
const MERMAID_DIR = path.join(ASSET_DIR, 'mermaid');
const FIXTURE_FILE = path.resolve(__dirname, '..', 'resources', 'markdown-fixtures', '34-mermaid.md');

let mathmlNodeCount = 0; // 未使用，保持 FakeNode 与 math harness 同构（便于后续合并维护）

function FakeNode(type, tag, namespace) {
  this.nodeType = type;
  this.tagName = tag ? tag.toUpperCase() : undefined;
  this.namespaceURI = namespace;
  this.childNodes = [];
  this.parentNode = null;
  this.style = {};
  this.className = '';
  this.attributes = {};
  this.nodeValue = type === 3 ? '' : null;
}

Object.defineProperty(FakeNode.prototype, 'textContent', {
  get: function () {
    if (this.nodeType === 3) return this.nodeValue;
    return this.childNodes
      .map(function (child) {
        return child.textContent;
      })
      .join('');
  },
  set: function (value) {
    if (this.nodeType === 3) {
      this.nodeValue = String(value);
    } else {
      this.childNodes = [];
    }
  },
});

FakeNode.prototype.appendChild = function (child) {
  child.parentNode = this;
  this.childNodes.push(child);
  return child;
};

FakeNode.prototype.setAttribute = function (name, value) {
  this.attributes[name] = String(value);
};

FakeNode.prototype.getAttribute = function (name) {
  return Object.prototype.hasOwnProperty.call(this.attributes, name) ? this.attributes[name] : null;
};

FakeNode.prototype.hasAttribute = function (name) {
  return Object.prototype.hasOwnProperty.call(this.attributes, name);
};

FakeNode.prototype.replaceChild = function (newChild, oldChild) {
  const index = this.childNodes.indexOf(oldChild);
  if (index < 0) throw new Error('replaceChild: not a child of this node');
  const inserted = newChild.nodeType === 11 ? newChild.childNodes.slice() : [newChild];
  inserted.forEach(function (node) {
    node.parentNode = this;
  }, this);
  this.childNodes.splice.apply(this.childNodes, [index, 1].concat(inserted));
  return oldChild;
};

function bootstrapDom() {
  global.window = global;
  global.document = {
    // mermaid/katex 在加载期检查 quirks mode；非 CSS1Compat 会禁用渲染
    compatMode: 'CSS1Compat',
    readyState: 'complete',
    documentElement: new FakeNode(1, 'html'),
    addEventListener: function () {},
    querySelector: function () {
      return null; // 不走 init()，只驱动 renderMermaid 本身
    },
    getElementById: function () {
      return null;
    },
    createElement: function (tag) {
      return new FakeNode(1, tag);
    },
    createElementNS: function (namespace, tag) {
      if (namespace === 'http://www.w3.org/1998/Math/MathML') mathmlNodeCount++;
      return new FakeNode(1, tag, namespace);
    },
    createTextNode: function (text) {
      const node = new FakeNode(3, null, null);
      node.nodeValue = text;
      return node;
    },
    createDocumentFragment: function () {
      return new FakeNode(11, null, null);
    },
  };
}

function element(tag, className, children) {
  const node = document.createElement(tag);
  if (className) node.className = className;
  (children || []).forEach(function (child) {
    node.appendChild(child);
  });
  return node;
}

function text(value) {
  return document.createTextNode(value);
}

function walk(node, visit) {
  visit(node);
  (node.childNodes || []).forEach(function (child) {
    walk(child, visit);
  });
}

function countByTag(root, tag) {
  let count = 0;
  walk(root, function (node) {
    if (node.tagName === tag) count++;
  });
  return count;
}

function textNodesWith(root, needle) {
  const found = [];
  walk(root, function (node) {
    if (node.nodeType === 3 && node.nodeValue.indexOf(needle) >= 0) found.push(node);
  });
  return found;
}

/** 离线通道形态：`<pre><code class="language-mermaid">…` */
function offlineMermaidBlock(source) {
  return element('pre', null, [element('code', 'language-mermaid', [text(source)])]);
}

/** 服务端 HTML 通道形态（POST /markdown 实测）：`<div class="highlight highlight-source-mermaid"><pre>…` */
function serverMermaidBlock(source) {
  return element('div', 'highlight highlight-source-mermaid', [element('pre', null, [text(source)])]);
}

/** GitHub README 的 `pre[lang="mermaid"]` 本体（无 wrapper）。 */
function githubReadmeMermaidPre(source) {
  const pre = element('pre', null, [text(source)]);
  pre.setAttribute('lang', 'mermaid');
  pre.setAttribute('aria-label', 'Raw mermaid code');
  return pre;
}

/**
 * GitHub README 的完整占位结构（GET /repos/{o}/{r}/readme Accept: html+json，2026-09-13 实测）：
 * 可见源码块（snippet-clipboard-content）+ 待水合 section（隐藏 render-plaintext-hidden >
 * pre[lang=mermaid] + js-render-enrichment-loader）。
 *
 * 返回 wrapper：childNodes[0] = 源码块，childNodes[1] = section。
 */
function githubReadmeMermaidBlock(source) {
  const target = element('div', 'js-render-enrichment-target', [
    element('div', 'render-plaintext-hidden', [githubReadmeMermaidPre(source)]),
  ]);
  const loader = element('span', 'js-render-enrichment-loader', []);
  const section = element('section', 'js-render-needs-enrichment render-needs-enrichment', [target, loader]);
  const sourceBlock = element('div', 'snippet-clipboard-content notranslate', [
    element('pre', 'notranslate', [element('code', null, [text(source)])]),
  ]);
  return element('div', null, [sourceBlock, section]);
}

/** wrapper 里的可见源码块 / 加载指示器（清理行为断言用）。 */
function githubReadmeSourceBlockOf(wrapper) {
  return wrapper.childNodes[0];
}

function githubReadmeLoaderOf(wrapper) {
  return wrapper.childNodes[1].childNodes[1];
}

/** 普通代码块（不得被识别为图）。 */
function plainCodeBlock(language, source) {
  return element('pre', null, [element('code', 'language-' + language, [text(source)])]);
}

const SAMPLE = 'graph TD\n    A[Start] --> B{Decision}';

/** 记录型 mermaid 引擎 stub。 */
function installMermaidStub(behavior) {
  const record = { initializeCalls: 0, config: null, runCalls: 0, nodes: [] };
  global.window.mermaid = {
    initialize: function (config) {
      record.initializeCalls++;
      record.config = config;
      if (behavior === 'init-throws') throw new Error('initialize failed');
    },
    run: function (options) {
      record.runCalls++;
      record.nodes = options && options.nodes ? options.nodes.slice() : [];
      if (behavior === 'success') {
        record.nodes.forEach(function (node) {
          node.appendChild(document.createElementNS('http://www.w3.org/2000/svg', 'svg'));
        });
        return Promise.resolve();
      }
      if (behavior === 'reject') return Promise.reject(new Error('render rejected'));
      if (behavior === 'error-svg') {
        // mermaid 真实失败形态（真实 Chromium 实测）：注入 aria-roledescription="error"
        // 的错误卡片 SVG（带 .error-icon/.error-text），必须按失败处理。
        record.nodes.forEach(function (node) {
          const svg = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
          svg.setAttribute('aria-roledescription', 'error');
          node.appendChild(svg);
        });
        return Promise.resolve();
      }
      return Promise.resolve(); // 'noop'：真实 suppressErrors 行为——resolve 但不产 SVG
    },
  };
  return record;
}

function removeMermaidStub() {
  delete global.window.mermaid;
}

/**
 * 记录型 AndroidBridge（renderer.js 在 require 时抓取全局引用，必须在 require 之前安装）。
 *
 * 只实现 onMermaidResult（本 harness 要断言的上报路径）；其余回调是 no-op。返回调用列表，
 * 元素形如 `{ rendered, failed, engineSupported }`。
 */
function installBridgeRecorder() {
  const calls = [];
  global.AndroidBridge = {
    onLinkClick: function () {},
    onCodeCopy: function () {},
    onImageClick: function () {},
    onCheckboxClick: function () {},
    onHeightChanged: function () {},
    onMermaidResult: function (rendered, failed, engineSupported) {
      calls.push({ rendered: rendered, failed: failed, engineSupported: engineSupported });
    },
  };
  return calls;
}

/** 最后一次上报的 `rendered,failed,engineSupported`（无上报返回 'none'）。 */
function lastReport(calls) {
  if (!calls.length) return 'none';
  const call = calls[calls.length - 1];
  return call.rendered + ',' + call.failed + ',' + call.engineSupported;
}

/** 让下一次 engine 探测的语法探针失败（模拟 Chromium < 94）。 */
function withBrokenClassStaticBlockProbe(run) {
  const realFunction = global.Function;
  global.Function = function () {
    throw new SyntaxError('probe: class static blocks are not supported');
  };
  try {
    return run();
  } finally {
    global.Function = realFunction;
  }
}

function tick() {
  return new Promise(function (resolve) {
    setTimeout(resolve, 0);
  });
}

/**
 * 用真实 bundle 的 parse 校验语法（独立 realm，浏览器脚本语义）。
 *
 * 限制（诚实声明）：带标签的图（`A[Start]`）在无 DOM 的 Node 里会走 DOMPurify 清洗
 * 路径（mermaid 把 dompurify 打进 bundle，无 `document` 时实例不可用），所以这里用
 * **夹具去掉标签后的骨架**（`graph TD` + 拓扑）过真实 parser——结构合法性由真引擎证明，
 * 标签文本的清洗行为由浏览器/真机截图覆盖。
 */
async function realBundleParse() {
  const code = fs.readFileSync(path.join(MERMAID_DIR, 'mermaid.tiny.js'), 'utf8');
  const sandbox = {};
  sandbox.globalThis = sandbox;
  sandbox.window = sandbox;
  sandbox.self = sandbox;
  sandbox.console = { log: function () {}, warn: function () {}, error: function () {}, info: function () {}, debug: function () {} };
  vm.runInNewContext(code, sandbox, { filename: 'mermaid.tiny.js' });

  const fixture = fs.readFileSync(FIXTURE_FILE, 'utf8');
  const fenced = /```mermaid\n([\s\S]*?)```/.exec(fixture);
  if (!fenced) throw new Error('34-mermaid.md 夹具里找不到 ```mermaid 围栏');
  // 去标签保拓扑：`A[Start]`→`A`、`B{Decision}`→`B`、`|Yes|`→边标签
  const skeleton = fenced[1]
    .replace(/\|[^|]*\|/g, '')
    .replace(/\[[^\]]*\]/g, '')
    .replace(/\{[^}]*\}/g, '')
    .replace(/\([^)]*\)/g, '');

  const simple = await sandbox.mermaid.parse('graph TD; A-->B;');
  const fromFixture = await sandbox.mermaid.parse(skeleton);
  let invalid = 'RESOLVED-UNEXPECTEDLY';
  try {
    await sandbox.mermaid.parse('this is definitely not {{ a diagram');
  } catch (error) {
    invalid = 'rejected';
  }
  return {
    code: code,
    simpleType: simple && simple.diagramType,
    fixtureSkeletonType: fromFixture && fromFixture.diagramType,
    invalid: invalid,
  };
}

async function main() {
  const lines = [];

  // ── 1. 真实 bundle：IIFE 形态 + 版本标记 + 真实 parse ──────────────────
  const real = await realBundleParse();
  lines.push('[mermaidBundleBytes] ' + Buffer.byteLength(real.code, 'utf8'));
  lines.push('[mermaidDynamicImports] ' + (real.code.match(/import\(/g) || []).length);
  lines.push('[mermaidStaticBlocks] ' + (real.code.match(/static\{/g) || []).length);
  lines.push('[mermaidVersionMarker] ' + (real.code.indexOf('11.17.2-tiny') >= 0));
  lines.push('[realParseSimpleType] ' + real.simpleType);
  lines.push('[realParseFixtureSkeletonType] ' + real.fixtureSkeletonType);
  lines.push('[realParseInvalid] ' + real.invalid);

  // ── 2. 真实 renderer.js（fake DOM）驱动的选择/接管/回退矩阵 ──────────────
  bootstrapDom();
  // bridge 必须在 require(renderer.js) 之前安装：renderer.js 加载时就抓取全局 AndroidBridge 引用
  const bridgeCalls = installBridgeRecorder();
  require(path.join(ASSET_DIR, 'renderer.js'));
  const api = global.window.__appdevMarkdownPlugins;
  if (!api || typeof api.renderMermaid !== 'function' || typeof api.mermaidSourceOf !== 'function') {
    process.stderr.write('renderer.js 未暴露 renderMermaid / mermaidSourceOf\n');
    process.exit(3);
  }
  lines.push('[mermaidLimits] ' + JSON.stringify(api.mermaidLimits));

  // 2a. 离线形态 + 成功路径 + 主题变量（getComputedStyle 可用）
  let record = installMermaidStub('success');
  const themeVars = {
    '--md-sys-color-primary': '#112233',
    '--md-sys-color-on-surface': '#445566',
    '--fontStack-sansSerif': 'Custom Sans, sans-serif',
  };
  global.window.getComputedStyle = function () {
    return {
      getPropertyValue: function (name) {
        return Object.prototype.hasOwnProperty.call(themeVars, name) ? themeVars[name] : '';
      },
    };
  };
  const offlineRoot = element('div', 'markdown-body', [offlineMermaidBlock(SAMPLE), plainCodeBlock('kotlin', 'val x = 1')]);
  const offlineTook = api.renderMermaid(offlineRoot);
  await tick();
  lines.push('[offlineTookOver] ' + offlineTook);
  lines.push('[offlineInitializeCalls] ' + record.initializeCalls);
  lines.push('[offlineRunNodes] ' + (record.nodes ? record.nodes.length : 0));
  lines.push('[offlineSvgCount] ' + countByTag(offlineRoot, 'SVG'));
  lines.push('[offlinePlainCodeIntact] ' + (countByTag(offlineRoot, 'PRE') === 1));
  lines.push(
    '[initConfig] ' +
      [
        'startOnLoad=' + record.config.startOnLoad,
        'securityLevel=' + record.config.securityLevel,
        'htmlLabels=' + record.config.htmlLabels,
        'theme=' + record.config.theme,
      ].join(';'),
  );
  lines.push('[themePrimaryBorder] ' + record.config.themeVariables.primaryBorderColor);
  lines.push('[themeFontFamily] ' + record.config.themeVariables.fontFamily);
  lines.push('[offlineReport] ' + lastReport(bridgeCalls));

  // 2b. 服务端 HTML 形态
  record = installMermaidStub('success');
  const serverRoot = element('div', 'markdown-body', [serverMermaidBlock(SAMPLE)]);
  const serverTook = api.renderMermaid(serverRoot);
  await tick();
  lines.push('[serverTookOver] ' + serverTook);
  lines.push('[serverSvgCount] ' + countByTag(serverRoot, 'SVG'));
  lines.push('[serverReport] ' + lastReport(bridgeCalls));

  // 2b2. GitHub README 实测形态（pre[lang="mermaid"] + 源码块占位结构，CI 深链目标 mermaid-js/mermaid）
  record = installMermaidStub('success');
  const readmeRoot = element('div', 'markdown-body', [githubReadmeMermaidBlock(SAMPLE)]);
  const readmeTook = api.renderMermaid(readmeRoot);
  await tick();
  const readmeWrapper = readmeRoot.childNodes[0];
  lines.push('[readmeTookOver] ' + readmeTook);
  lines.push('[readmeSvgCount] ' + countByTag(readmeRoot, 'SVG'));
  lines.push('[readmeReport] ' + lastReport(bridgeCalls));
  lines.push('[readmeSourceHidden] ' + (githubReadmeSourceBlockOf(readmeWrapper).style.display === 'none'));
  lines.push('[readmeLoaderHidden] ' + (githubReadmeLoaderOf(readmeWrapper).style.display === 'none'));

  // 2b3. 同一 GitHub 结构 + 渲染失败 → 源码块/指示器必须保持可见（回退不丢内容）
  record = installMermaidStub('noop');
  const readmeFailRoot = element('div', 'markdown-body', [githubReadmeMermaidBlock(SAMPLE)]);
  api.renderMermaid(readmeFailRoot);
  await tick();
  const readmeFailWrapper = readmeFailRoot.childNodes[0];
  lines.push('[readmeFailSourceVisible] ' + (githubReadmeSourceBlockOf(readmeFailWrapper).style.display !== 'none'));
  lines.push('[readmeFailLoaderVisible] ' + (githubReadmeLoaderOf(readmeFailWrapper).style.display !== 'none'));
  lines.push('[readmeFailCodeRestored] ' + (countByTag(readmeFailRoot, 'PRE') === 2));

  // 2c. 无图页面：不得 initialize/run（性能护栏），也不得上报（无事实可报）
  const reportsBeforePlain = bridgeCalls.length;
  record = installMermaidStub('success');
  const noDiagramRoot = element('div', 'markdown-body', [plainCodeBlock('mermaidish', 'not a diagram'), element('p', null, [text('plain')])]);
  const noDiagramTook = api.renderMermaid(noDiagramRoot);
  await tick();
  lines.push('[noDiagramTookOver] ' + noDiagramTook);
  lines.push('[noDiagramInitCalls] ' + record.initializeCalls);
  lines.push('[noDiagramRunCalls] ' + record.runCalls);
  lines.push('[noDiagramReports] ' + (bridgeCalls.length - reportsBeforePlain));

  // 2d. 引擎缺失（脚本未注入 / 老引擎解析失败）：整体 no-op、DOM 无损
  removeMermaidStub();
  const absentRoot = element('div', 'markdown-body', [offlineMermaidBlock(SAMPLE)]);
  const absentTook = api.renderMermaid(absentRoot);
  lines.push('[engineAbsentTookOver] ' + absentTook);
  lines.push('[engineAbsentPreIntact] ' + (countByTag(absentRoot, 'PRE') === 1 && textNodesWith(absentRoot, SAMPLE).length === 1));
  lines.push('[engineAbsentReason] ' + api.detectMermaidEngine().reason);
  lines.push('[engineAbsentReport] ' + lastReport(bridgeCalls));

  // 2e. 语法探针失败（Chromium < 94 的 class static block）：整体 no-op、DOM 无损
  record = installMermaidStub('success');
  const syntaxRoot = element('div', 'markdown-body', [offlineMermaidBlock(SAMPLE)]);
  const syntaxTook = withBrokenClassStaticBlockProbe(function () {
    return api.renderMermaid(syntaxRoot);
  });
  lines.push('[syntaxTookOver] ' + syntaxTook);
  lines.push('[syntaxPreIntact] ' + (countByTag(syntaxRoot, 'PRE') === 1 && record.runCalls === 0));
  lines.push('[syntaxReport] ' + lastReport(bridgeCalls));

  // 2f. run 失败（suppressErrors 下 resolve 但不产 SVG）→ 恢复原代码块
  record = installMermaidStub('noop');
  const failRoot = element('div', 'markdown-body', [offlineMermaidBlock(SAMPLE)]);
  const failTook = api.renderMermaid(failRoot);
  await tick();
  lines.push('[runFailureTookOver] ' + failTook);
  lines.push('[runFailureCodeRestored] ' + (countByTag(failRoot, 'PRE') === 1 && textNodesWith(failRoot, SAMPLE).length === 1));
  lines.push('[runFailureHolderGone] ' + (countByTag(failRoot, 'DIV') === 1));
  lines.push('[runFailureReport] ' + lastReport(bridgeCalls));

  // 2f2. run 成功但注入的是 mermaid **错误卡片**（真实失败形态）→ 必须计入 failed 并恢复代码块
  record = installMermaidStub('error-svg');
  const errorCardRoot = element('div', 'markdown-body', [offlineMermaidBlock(SAMPLE)]);
  const errorCardTook = api.renderMermaid(errorCardRoot);
  await tick();
  lines.push('[errorCardTookOver] ' + errorCardTook);
  lines.push('[errorCardCodeRestored] ' + (countByTag(errorCardRoot, 'PRE') === 1 && textNodesWith(errorCardRoot, SAMPLE).length === 1));
  lines.push('[errorCardReport] ' + lastReport(bridgeCalls));

  // 2g. run reject → 同样恢复
  record = installMermaidStub('reject');
  const rejectRoot = element('div', 'markdown-body', [offlineMermaidBlock(SAMPLE)]);
  api.renderMermaid(rejectRoot);
  await tick();
  lines.push('[runRejectCodeRestored] ' + (countByTag(rejectRoot, 'PRE') === 1 && textNodesWith(rejectRoot, SAMPLE).length === 1));
  lines.push('[runRejectReport] ' + lastReport(bridgeCalls));

  // 2h. initialize 同步抛异常 → 整体回退、返回 0
  record = installMermaidStub('init-throws');
  const initFailRoot = element('div', 'markdown-body', [offlineMermaidBlock(SAMPLE)]);
  const initFailTook = api.renderMermaid(initFailRoot);
  await tick();
  lines.push('[initThrowsTookOver] ' + initFailTook);
  lines.push('[initThrowsCodeRestored] ' + (countByTag(initFailRoot, 'PRE') === 1 && textNodesWith(initFailRoot, SAMPLE).length === 1));
  lines.push('[initThrowsReport] ' + lastReport(bridgeCalls));

  // 2i. 预算上限：3 张图、maxDiagrams=2 → 只接管 2 张，其余保持代码块
  record = installMermaidStub('success');
  const budgetRoot = element('div', 'markdown-body', [
    offlineMermaidBlock(SAMPLE + ' A'),
    offlineMermaidBlock(SAMPLE + ' B'),
    offlineMermaidBlock(SAMPLE + ' C'),
  ]);
  const budgetTook = api.renderMermaid(budgetRoot, { maxDiagrams: 2 });
  await tick();
  lines.push('[budgetTookOver] ' + budgetTook);
  lines.push('[budgetRemainingPre] ' + countByTag(budgetRoot, 'PRE'));
  lines.push('[budgetRunNodes] ' + record.nodes.length);
  lines.push('[budgetReport] ' + lastReport(bridgeCalls));

  // 2j. mermaidSourceOf 形态矩阵（纯函数）
  lines.push('[sourceOffline] ' + (api.mermaidSourceOf(offlineMermaidBlock(SAMPLE)) === SAMPLE));
  lines.push('[sourceServer] ' + (api.mermaidSourceOf(serverMermaidBlock(SAMPLE).childNodes[0]) === SAMPLE));
  lines.push('[sourceReadme] ' + (api.mermaidSourceOf(githubReadmeMermaidPre(SAMPLE)) === SAMPLE));
  lines.push('[sourcePlain] ' + (api.mermaidSourceOf(plainCodeBlock('kotlin', SAMPLE)) === null));
  const langOtherPre = element('pre', null, [text(SAMPLE)]);
  langOtherPre.setAttribute('lang', 'kotlin');
  lines.push('[sourceLangOther] ' + (api.mermaidSourceOf(langOtherPre) === null));

  // 主题变量取不到（无 getComputedStyle）→ 备用色，保证深色下仍可读
  delete global.window.getComputedStyle;
  lines.push('[themeFallbackPrimary] ' + api.mermaidThemeVariables().primaryBorderColor);

  process.stdout.write(lines.join('\n'));
}

main().catch(function (error) {
  process.stderr.write(String((error && error.stack) || error) + '\n');
  process.exit(1);
});
