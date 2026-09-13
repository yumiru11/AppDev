'use strict';

/*
 * math-render-harness.js —— 离线 KaTeX 数学渲染（post-sanitize pass）的 **Node 执行器**
 * （测试用，不进 APK）。
 *
 * ## 为什么需要它
 *
 * `renderer.js` 的 `renderMath(root)` 需要真实 DOM 才能在浏览器里跑；JVM 侧没有 JS 引擎，
 * 只靠源码文本断言证明不了「公式真的按预期被渲染、代码块真的不被误伤」。本脚本用最小
 * fake DOM（KaTeX 的 `toNode()` 只用到 createElement/createElementNS/createTextNode/
 * createDocumentFragment/appendChild/className/style/setAttribute）装上**真实的**
 * `katex.min.js`（生产 assets）与**真实的** `renderer.js`，端到端执行渲染并打印结果。
 *
 * 未覆盖：浏览器真实排版/字体栅格化（Robolectric 下 WebView 无法出帧）——那由 CI 真机
 * 截图通道兜底；本脚本覆盖的是「选择哪段文本、用什么选项、产出什么 DOM 结构」。
 *
 * 用法：node math-render-harness.js
 * stdout = 逐行 `[key] value`（JVM 侧不做 JSON 解析，与既有 harness 同构）。
 */

const path = require('path');

const ASSET_DIR = path.resolve(__dirname, '..', '..', 'main', 'assets', 'webview');
const KATEX_DIR = path.join(ASSET_DIR, 'katex');

let mathmlNodeCount = 0;

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
    // KaTeX 在加载期检查 quirks mode；非 CSS1Compat 会禁用渲染
    compatMode: 'CSS1Compat',
    readyState: 'complete',
    documentElement: new FakeNode(1, 'html'),
    addEventListener: function () {},
    querySelector: function () {
      return null; // 不走 init()，只测 renderMath 本身
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

function text(value) {
  return document.createTextNode(value);
}

function element(tag, children) {
  const node = document.createElement(tag);
  (children || []).forEach(function (child) {
    node.appendChild(child);
  });
  return node;
}

function walk(node, visit) {
  visit(node);
  (node.childNodes || []).forEach(function (child) {
    walk(child, visit);
  });
}

function collectByPredicate(root, predicate) {
  const found = [];
  walk(root, function (node) {
    if (predicate(node)) found.push(node);
  });
  return found;
}

function textNodesWith(root, needle) {
  return collectByPredicate(root, function (node) {
    return node.nodeType === 3 && node.nodeValue.indexOf(needle) >= 0;
  });
}

/** 主场景：真实 markdown 形态的文本树（含行内/块级/单字符公式与三类「不得误伤」样本）。 */
function buildMainRoot() {
  const root = element('div', []);
  root.appendChild(element('p', [text('行内公式：$E = mc^2$ 尾随文本')]));
  root.appendChild(element('p', [text('价格 $5 and $10 与 $HOME 不是公式')]));
  root.appendChild(element('p', [text('块级：$$\n\\int_0^\\infty e^{-x^2} dx = \\frac{\\sqrt{\\pi}}{2}\n$$')]));
  root.appendChild(element('p', [text('单字符 $x$ 与跨行 $a\nb$ 只渲染前者')]));
  root.appendChild(element('pre', [element('code', [text('echo $HOME')])]));
  root.appendChild(element('code', [text('$not math$')]));
  root.appendChild(element('p', [text('解析错误 $\\frac{1}{$ 保留原文')]));
  root.appendChild(element('p', [text('信任面 $\\href{javascript:alert(1)}{x}$ 不产链接')]));
  return root;
}

function main() {
  bootstrapDom();
  // KaTeX UMD 在 require 时读取 document（quirks-mode 判定），必须先建 fake DOM
  global.window.katex = require(path.join(KATEX_DIR, 'katex.min.js'));
  require(path.join(ASSET_DIR, 'renderer.js'));

  const api = global.window.__appdevMarkdownPlugins;
  if (!api || typeof api.renderMath !== 'function' || typeof api.scanMathText !== 'function') {
    process.stderr.write('renderer.js 未暴露 renderMath / scanMathText\n');
    process.exit(3);
  }

  const lines = [];
  const scan = api.scanMathText;

  lines.push('[katexVersion] ' + global.window.katex.version);
  lines.push('[limits] ' + JSON.stringify(api.mathLimits));
  lines.push('[scanInline] ' + JSON.stringify(scan('文本 $x$ 文本').map(function (s) { return s.tex + ':' + s.displayMode; })));
  lines.push('[scanBlock] ' + JSON.stringify(scan('$$\na+b\n$$').map(function (s) { return s.tex + ':' + s.displayMode; })));
  lines.push('[scanPrice] ' + scan('价格 $5 and $10').length);
  lines.push('[scanMultilineInline] ' + scan('跨行 $a\nb$').length);
  lines.push('[scanWordBoundary] ' + scan('US$5').length + ',' + scan('$x$5').length);
  lines.push('[scanTripleDollar] ' + scan('$$$x$$$').length);

  const root = buildMainRoot();
  const rendered = api.renderMath(root);
  lines.push('[rendered] ' + rendered);
  lines.push(
    '[dataMath] ' +
      collectByPredicate(root, function (n) {
        return n.attributes && n.attributes['data-math'] !== undefined;
      })
        .map(function (n) {
          return n.attributes['data-math'];
        })
        .sort()
        .join(','),
  );
  lines.push(
    '[texValues] ' +
      collectByPredicate(root, function (n) {
        return n.attributes && n.attributes.encoding === 'application/x-tex';
      })
        .map(function (n) {
          // 单行协议：块级公式的 annotation 含换行，转义成字面 \n 才能被 JVM 侧逐行解析
          return n.textContent.replace(/\n/g, '\\n');
        })
        .join('|'),
  );
  lines.push(
    '[katexHtmlSlots] ' +
      collectByPredicate(root, function (n) {
        return n.className && String(n.className).indexOf('katex-html') >= 0;
      }).length,
  );
  lines.push('[mathmlNodes] ' + mathmlNodeCount);
  lines.push(
    '[errorSpans] ' +
      collectByPredicate(root, function (n) {
        return n.className && String(n.className).indexOf('katex-error') >= 0;
      }).length,
  );
  lines.push(
    '[errorStyle] ' +
      collectByPredicate(root, function (n) {
        return n.className && String(n.className).indexOf('katex-error') >= 0;
      })
        .map(function (n) {
          return n.attributes.style || '';
        })
        .join('|'),
  );
  lines.push(
    '[trustAnchors] ' +
      collectByPredicate(root, function (n) {
        return n.tagName === 'A';
      }).length,
  );
  lines.push('[codeUntouched] ' + (textNodesWith(root, 'echo $HOME').length === 1));
  lines.push('[inlineCodeUntouched] ' + (textNodesWith(root, '$not math$').length === 1));
  lines.push('[priceUntouched] ' + (textNodesWith(root, '$5 and $10').length === 1));
  lines.push('[crossLineUntouched] ' + (textNodesWith(root, '$a\nb$').length === 1));

  // 预算护栏：5 个公式、maxFormulas=2 → 只渲染 2 个，其余原文保留
  const capped = element('div', [element('p', [text('$a1$ $a2$ $a3$ $a4$ $a5$')])]);
  const cappedRendered = api.renderMath(capped, { maxFormulas: 2 });
  lines.push('[cappedRendered] ' + cappedRendered);
  lines.push('[cappedRemaining] ' + capped.textContent.replace(/\n/g, '\\n'));

  // 主题色：getComputedStyle 可用时 errorColor 必须取自 --md-sys-color-error
  global.window.getComputedStyle = function () {
    return {
      getPropertyValue: function (name) {
        return name === '--md-sys-color-error' ? '#ff0000' : '';
      },
    };
  };
  const themed = element('div', [element('p', [text('$\\frac{1}{$')])]);
  api.renderMath(themed);
  lines.push(
    '[themedErrorStyle] ' +
      collectByPredicate(themed, function (n) {
        return n.className && String(n.className).indexOf('katex-error') >= 0;
      })
        .map(function (n) {
          return n.attributes.style || '';
        })
        .join('|'),
  );

  // 未注入 katex.min.js（Kotlin 侧未检测到数学的常规形态）时必须整体 no-op、原文无损
  const savedKatex = global.window.katex;
  delete global.window.katex;
  const gated = element('div', [element('p', [text('$x$')])]);
  lines.push('[withoutKatex] ' + api.renderMath(gated) + ',' + (textNodesWith(gated, '$x$').length === 1));
  global.window.katex = savedKatex;

  process.stdout.write(lines.join('\n'));
}

main();
