'use strict';

/*
 * server-html-harness.js —— 服务端 HTML 主通道（SERVER_HTML）的 **Node 执行器**（测试用，不进 APK）。
 *
 * 与 offline-render-harness.js 互补：那条跑的是 `renderOfflineHtml` 纯字符串渲染；
 * 这条驱动 `renderer.js` 的 `init()`（页面加载后的 DOM 阶段），用最小 fake DOM 复现
 * 「GitHub 服务端 HTML 主通道」：
 *   - 无 #markdown-raw（服务端 HTML，不是离线 markdown）
 *   - root 下若干假代码块（`code.language-*`）与两张假图片（一张预置 loading="eager"）
 *   - window.hljs 用记录型 stub（真实 highlight.min.js 需要真实 DOM，测的是**选择/预算逻辑**，
 *     不是 hljs 本身；真实高亮由 CI 模拟器截图兜底）
 *
 * 用法：
 *     node server-html-harness.js <blockCount> <blockChars>
 *
 * stdout 为逐行 `[key] value` 文本（JVM 侧不做 JSON 依赖）：
 *     [highlighted] c0,c1
 *     [totalBlocks] 2
 *     [maxBlocks] 30
 *     [maxTotalChars] 120000
 *     [image0] loading=lazy decoding=async
 *     [image1] loading=eager decoding=async
 */

const path = require('path');

const ASSET_DIR = path.resolve(__dirname, '..', '..', 'main', 'assets', 'webview');

function fakeCode(id, text) {
  return { id: id, textContent: text };
}

/** 记录型图片元素：支持 decorateImages/bindImages 需要的 DOM 子集。 */
function fakeImage(id, initial) {
  const attrs = {};
  if (initial) {
    Object.keys(initial).forEach(function (name) {
      attrs[name] = initial[name];
    });
  }
  return {
    id: id,
    style: {},
    addEventListener: function () {},
    hasAttribute: function (name) {
      return Object.prototype.hasOwnProperty.call(attrs, name);
    },
    getAttribute: function (name) {
      return Object.prototype.hasOwnProperty.call(attrs, name) ? attrs[name] : null;
    },
    setAttribute: function (name, value) {
      attrs[name] = value;
    },
    __attrs: attrs,
  };
}

function bootstrap(blockCount, blockChars, highlighted) {
  const codes = [];
  for (let i = 0; i < blockCount; i++) {
    codes.push(fakeCode('c' + i, 'x'.repeat(blockChars)));
  }
  const images = [fakeImage('i0', null), fakeImage('i1', { loading: 'eager' })];

  const root = {
    querySelectorAll: function (selector) {
      if (selector === 'code[class*="language-"]') return codes;
      if (selector === 'img') return images;
      return [];
    },
    // init() 里对 pre 的绑定在这里无 pre，直接空集
    querySelector: function () {
      return null;
    },
  };

  global.window = global;
  global.window.hljs = {
    highlightElement: function (element) {
      highlighted.push(element.id);
    },
  };
  global.document = {
    readyState: 'complete',
    addEventListener: function () {},
    querySelector: function (selector) {
      return selector === '.markdown-body' ? root : null;
    },
    getElementById: function () {
      return null; // 服务端 HTML 主通道：没有 #markdown-raw
    },
    createElement: function () {
      return { style: {}, addEventListener: function () {}, appendChild: function () {} };
    },
  };
  return { codes: codes, images: images };
}

function main() {
  const blockCount = parseInt(process.argv[2] || '0', 10);
  const blockChars = parseInt(process.argv[3] || '0', 10);
  if (isNaN(blockCount) || isNaN(blockChars)) {
    process.stderr.write('usage: node server-html-harness.js <blockCount> <blockChars>\n');
    process.exit(2);
  }

  const highlighted = [];
  const dom = bootstrap(blockCount, blockChars, highlighted);

  // renderer.js 是 IIFE：readyState !== 'loading' → require 时立即执行 init()
  require(path.join(ASSET_DIR, 'renderer.js'));

  const api = global.window.__appdevMarkdownPlugins;
  if (!api || !api.highlightLimits) {
    process.stderr.write('renderer.js 未暴露高亮预算（highlightLimits）\n');
    process.exit(3);
  }

  const lines = [
    '[highlighted] ' + highlighted.join(','),
    '[totalBlocks] ' + dom.codes.length,
    '[maxBlocks] ' + api.highlightLimits.maxBlocks,
    '[maxTotalChars] ' + api.highlightLimits.maxTotalChars,
  ];
  dom.images.forEach(function (img, index) {
    const loading = img.hasAttribute('loading') ? img.getAttribute('loading') : 'none';
    const decoding = img.hasAttribute('decoding') ? img.getAttribute('decoding') : 'none';
    lines.push('[image' + index + '] loading=' + loading + ' decoding=' + decoding);
  });
  process.stdout.write(lines.join('\n'));
}

main();
