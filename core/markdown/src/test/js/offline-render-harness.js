/*
 * offline-render-harness.js —— 离线 GFM 通道的 **Node 执行器**（测试用，不进 APK）。
 *
 * ## 为什么需要它
 *
 * `docs/agents/markdown-consistency-2026-09-11.md` §1 第 ③ 层此前只能做「产物文本断言」
 * （`WebViewOfflineGfmCapabilityTest`：JVM 无 JS 引擎 → 只能断言 assets 里有没有某段源码）。
 * 这证明不了「相对链接真的被改写了」这类行为结论——正是 D3 能长期潜伏的原因。
 *
 * 本脚本把真实的 `markdown-it.min.js` + `renderer.js` 装进 Node 执行，产出真实的渲染 HTML，
 * 由 `OfflineRendererExecutionTest` 断言。执行的是**生产代码本身**（renderer.js 的
 * `renderOfflineHtml`），不是复刻实现；浏览器专属的部分（DOM 插入、ResizeObserver、
 * DOMPurify 清洗）不在覆盖范围内——那需要真机 WebView。
 *
 * ## 用法
 *
 *     node offline-render-harness.js <markdownFile> [<owner/repo>|-]
 *
 * stdout = 渲染产物 HTML；退出码非 0 表示渲染失败（stderr 带原因）。
 * 相对路径依赖 Gradle 测试的工作目录（模块根），与既有 `File("src/main/assets/webview")` 一致。
 */
'use strict';

const fs = require('fs');
const path = require('path');

const ASSET_DIR = path.resolve(__dirname, '..', '..', 'main', 'assets', 'webview');

/** 需要浏览器 DOM / 与离线渲染无关的 bundle：不加载（加载会抛）。 */
const SKIP_BUNDLES = new Set(['purify.min.js', 'highlight.min.js', 'renderer.js', 'markdown-it.min.js']);

function bootstrapWindow() {
  // renderer.js 是 IIFE：它只依赖 window/document 的最小表面。
  global.window = global;
  global.document = {
    readyState: 'complete',
    addEventListener: function () {},
    querySelector: function () {
      return null;
    },
    getElementById: function () {
      return null;
    },
  };
}

/** 按文件名排序加载 assets/webview 下的可选插件资源（如 emoji 短码映射表）。 */
function loadOptionalAssets() {
  fs.readdirSync(ASSET_DIR)
    .filter(function (name) {
      return name.endsWith('.js') && !SKIP_BUNDLES.has(name);
    })
    .sort()
    .forEach(function (name) {
      require(path.join(ASSET_DIR, name));
    });
}

function main() {
  const markdownFile = process.argv[2];
  const repoContext = process.argv[3] && process.argv[3] !== '-' ? process.argv[3] : null;
  if (!markdownFile) {
    process.stderr.write('usage: node offline-render-harness.js <markdownFile> [<owner/repo>|-]\n');
    process.exit(2);
  }

  bootstrapWindow();
  // 与浏览器一致：markdown-it 先于 renderer.js 就位（renderer.js 读 window.markdownit）
  global.window.markdownit = require(path.join(ASSET_DIR, 'markdown-it.min.js'));
  loadOptionalAssets();
  require(path.join(ASSET_DIR, 'renderer.js'));

  const api = global.window.__appdevMarkdownPlugins;
  if (!api || typeof api.renderOfflineHtml !== 'function') {
    process.stderr.write('renderer.js 未暴露 renderOfflineHtml（离线渲染入口）\n');
    process.exit(3);
  }

  const html = api.renderOfflineHtml(fs.readFileSync(markdownFile, 'utf8'), { repoContext: repoContext });
  if (html === null) {
    process.stderr.write('renderOfflineHtml 返回 null：markdown-it 未就位\n');
    process.exit(4);
  }
  process.stdout.write(html);
}

main();
