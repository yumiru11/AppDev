/*
 * renderer.js — WebView 兜底渲染桥接脚本（自维护）。
 *
 * 职责（plan.md §2.9 / §2.14）：
 * 1. 调用 DOMPurify.sanitize 清洗 markdown-body 内容（权威清洗）
 * 2. 绑定白名单事件 → AndroidBridge（@JavascriptInterface 白名单）：
 *    - a 链接 click → onLinkClick(href)
 *    - 代码块复制按钮 click → onCodeCopy(code)
 *    - img click → onImageClick(src)
 *    - 任务列表 checkbox change → onCheckboxClick(index, checked)
 *    - ResizeObserver → onHeightChanged(height)
 * 3. 离线模式（OFFLINE_MARKDOWN_IT）：调用 markdown-it 渲染原始 markdown，
 *    补 GitHub Alert / 任务列表两个最小 GFM 插件，并用 highlight.js 高亮代码块；
 *    渲染产物再按仓库上下文改写相对链接/图片（见 renderOfflineHtml 的说明）
 *
 * 安全：本脚本不接收任何 token；token 仅由 PrivateImageInterceptor 加到网络请求。
 * 仓库上下文（`owner/repo`）不是凭据，由 `data-base-repo` 属性传入（公开信息）。
 */
(function () {
  'use strict';

  var ANDROID_BRIDGE = (typeof AndroidBridge !== 'undefined') ? AndroidBridge : null;
  var PURIFY = (typeof DOMPurify !== 'undefined') ? DOMPurify : null;

  var PURIFY_CONFIG = {
    FORBID_TAGS: ['script', 'iframe', 'object', 'embed', 'form', 'style'],
    FORBID_ATTR: ['onerror', 'onload', 'onclick', 'onmouseover', 'style'],
    ALLOWED_URI_REGEXP: /^(?:(?:https?|mailto):|[^a-z]|[a-z+.-]+(?:[^a-z+.-:]|$))/i
  };

  function sanitizeNode(node) {
    if (!PURIFY) return;
    var html = node.innerHTML;
    var cleaned = PURIFY.sanitize(html, PURIFY_CONFIG);
    node.innerHTML = cleaned;
  }

  function bindLinks(root) {
    var anchors = root.querySelectorAll('a[href]');
    for (var i = 0; i < anchors.length; i++) {
      (function (anchor) {
        anchor.addEventListener('click', function (event) {
          event.preventDefault();
          var href = anchor.getAttribute('href') || '';
          if (ANDROID_BRIDGE && href) {
            ANDROID_BRIDGE.onLinkClick(href);
          }
        });
      })(anchors[i]);
    }
  }

  function bindImages(root) {
    var imgs = root.querySelectorAll('img');
    for (var i = 0; i < imgs.length; i++) {
      (function (img) {
        img.addEventListener('click', function () {
          var src = img.getAttribute('src') || '';
          if (ANDROID_BRIDGE && src) {
            ANDROID_BRIDGE.onImageClick(src);
          }
        });
        img.style.cursor = 'pointer';
      })(imgs[i]);
    }
  }

  function bindCheckboxes(root) {
    var checkboxes = root.querySelectorAll('input[type="checkbox"]');
    for (var i = 0; i < checkboxes.length; i++) {
      (function (checkbox, index) {
        checkbox.addEventListener('change', function () {
          if (ANDROID_BRIDGE) {
            ANDROID_BRIDGE.onCheckboxClick(index, checkbox.checked);
          }
        });
      })(checkboxes[i], i);
    }
  }

  function bindCodeCopy(root) {
    var pres = root.querySelectorAll('pre');
    for (var i = 0; i < pres.length; i++) {
      (function (pre) {
        if (pre.querySelector('.md-copy-btn')) return;
        var btn = document.createElement('button');
        btn.className = 'md-copy-btn';
        btn.textContent = 'Copy';
        btn.style.position = 'absolute';
        btn.style.top = '4px';
        btn.style.right = '4px';
        btn.style.fontSize = '12px';
        btn.style.padding = '2px 8px';
        btn.style.borderRadius = '4px';
        btn.style.background = 'var(--md-sys-color-primary)';
        btn.style.color = '#fff';
        btn.style.border = 'none';
        btn.style.cursor = 'pointer';
        btn.style.opacity = '0';
        pre.style.position = 'relative';
        pre.appendChild(btn);
        pre.addEventListener('mouseenter', function () { btn.style.opacity = '1'; });
        pre.addEventListener('mouseleave', function () { btn.style.opacity = '0'; });
        btn.addEventListener('click', function (event) {
          event.preventDefault();
          var code = pre.querySelector('code');
          var text = code ? code.textContent : pre.textContent;
          if (ANDROID_BRIDGE) {
            ANDROID_BRIDGE.onCodeCopy(text);
          }
        });
      })(pres[i]);
    }
  }

  function observeHeight(root) {
    if (typeof ResizeObserver === 'undefined') return;
    var observer = new ResizeObserver(function (entries) {
      for (var i = 0; i < entries.length; i++) {
        var height = Math.ceil(entries[i].contentRect.height);
        if (ANDROID_BRIDGE) {
          ANDROID_BRIDGE.onHeightChanged(height);
        }
      }
    });
    observer.observe(root);
  }

  function highlightCodeBlocks(root) {
    if (typeof window.hljs === 'undefined') return;
    var codes = root.querySelectorAll('code[class*="language-"]');
    for (var i = 0; i < codes.length; i++) {
      try {
        window.hljs.highlightElement(codes[i]);
      } catch (e) {
        // 未知语言/解析失败时保留原文，不阻断渲染
      }
    }
  }

  var ALERT_TYPES = ['NOTE', 'TIP', 'IMPORTANT', 'WARNING', 'CAUTION'];

  function alertIconClass(type) {
    return 'octicon octicon-' + type.toLowerCase();
  }

  function newToken(state, type, tag, nesting) {
    return new state.Token(type, tag, nesting || 0);
  }

  function githubAlertPlugin(md) {
    var defaultBlockquoteOpen = md.renderer.rules.blockquote_open ||
      function (tokens, idx) {
        return '<blockquote>\n';
      };
    var defaultBlockquoteClose = md.renderer.rules.blockquote_close ||
      function (tokens, idx) {
        return '</blockquote>\n';
      };
    var defaultParagraphOpen = md.renderer.rules.paragraph_open ||
      function (tokens, idx) {
        return '<p>';
      };

    md.renderer.rules.blockquote_open = function (tokens, idx) {
      var token = tokens[idx];
      if (token.alertType) {
        return '<div class="markdown-alert markdown-alert-' + token.alertType + '">\n';
      }
      return defaultBlockquoteOpen(tokens, idx);
    };

    md.renderer.rules.blockquote_close = function (tokens, idx) {
      if (tokens[idx].alertType) {
        return '</div>\n';
      }
      return defaultBlockquoteClose(tokens, idx);
    };

    md.renderer.rules.paragraph_open = function (tokens, idx) {
      if (tokens[idx].alertTitle) {
        return '<p class="markdown-alert-title">';
      }
      return defaultParagraphOpen(tokens, idx);
    };

    md.core.ruler.after('inline', 'github_alerts', function (state) {
      var tokens = state.tokens;
      for (var i = 0; i < tokens.length; i++) {
        var open = tokens[i];
        if (open.type !== 'blockquote_open') continue;

        var closeIndex = -1;
        var depth = 1;
        for (var j = i + 1; j < tokens.length; j++) {
          if (tokens[j].type === 'blockquote_open') depth++;
          if (tokens[j].type === 'blockquote_close') {
            depth--;
            if (depth === 0) {
              closeIndex = j;
              break;
            }
          }
        }
        if (closeIndex < 0) continue;

        var paragraphOpen = -1;
        var paragraphClose = -1;
        var inlineIndex = -1;
        for (var k = i + 1; k < closeIndex; k++) {
          if (tokens[k].type === 'paragraph_open' && paragraphOpen < 0) paragraphOpen = k;
          if (paragraphOpen >= 0 && tokens[k].type === 'inline') inlineIndex = k;
          if (paragraphOpen >= 0 && tokens[k].type === 'paragraph_close') {
            paragraphClose = k;
            break;
          }
        }
        if (paragraphOpen < 0 || paragraphClose < 0 || inlineIndex < 0) continue;

        var inline = tokens[inlineIndex];
        var first = inline.children && inline.children[0];
        if (!first || first.type !== 'text') continue;
        var match = /^\s*\[!(NOTE|TIP|IMPORTANT|WARNING|CAUTION)\]\s*/i.exec(first.content);
        if (!match) continue;

        var type = match[1].toUpperCase();
        var alertType = type.toLowerCase();
        open.alertType = alertType;
        tokens[closeIndex].alertType = alertType;
        tokens[paragraphOpen].alertTitle = true;

        // Title paragraph: vector octicon (CSS mask) + strong label.
        var icon = newToken(state, 'html_inline', '', 0);
        icon.content = '<span class="' + alertIconClass(type) + '" aria-hidden="true"></span><strong>' + type + '</strong>';
        inline.children.splice(0, 1, icon);

        // Move everything after the first softbreak into a new body paragraph.
        var split = -1;
        for (var c = 1; c < inline.children.length; c++) {
          if (inline.children[c].type === 'softbreak') {
            split = c;
            break;
          }
        }
        if (split > 0) {
          var bodyChildren = inline.children.slice(split + 1);
          inline.children = inline.children.slice(0, split);
          var bodyOpen = newToken(state, 'paragraph_open', 'p', 1);
          var bodyInline = newToken(state, 'inline', '', 0);
          bodyInline.children = bodyChildren;
          var bodyClose = newToken(state, 'paragraph_close', 'p', -1);
          var insertAt = paragraphClose + 1;
          tokens.splice(insertAt, 0, bodyOpen, bodyInline, bodyClose);
        }
      }
    });
  }

  function taskListPlugin(md) {
    var defaultListItemOpen = md.renderer.rules.list_item_open ||
      function (tokens, idx) {
        return '<li>';
      };

    md.renderer.rules.list_item_open = function (tokens, idx) {
      if (tokens[idx].taskItem) {
        return '<li class="task-list-item">';
      }
      return defaultListItemOpen(tokens, idx);
    };

    md.core.ruler.after('inline', 'task_lists', function (state) {
      var tokens = state.tokens;
      for (var i = 0; i < tokens.length; i++) {
        if (tokens[i].type !== 'list_item_open') continue;
        var inline = null;
        for (var j = i + 1; j < tokens.length && tokens[j].type !== 'list_item_close'; j++) {
          if (tokens[j].type === 'inline') {
            inline = tokens[j];
            break;
          }
        }
        if (!inline || !inline.children || !inline.children.length) continue;
        var first = inline.children[0];
        if (first.type !== 'text') continue;
        var match = /^\[([ xX])\]\s+/.exec(first.content);
        if (!match) continue;

        tokens[i].taskItem = true;
        var checked = match[1].toLowerCase() === 'x';
        var checkbox = newToken(state, 'html_inline', '', 0);
        checkbox.content = '<input type="checkbox" class="task-list-item-checkbox" ' +
          (checked ? 'checked ' : '') + 'aria-label="Task item">';
        first.content = first.content.slice(match[0].length);
        // 前插 checkbox，保留被切过首部的文本 token（splice(0,0,...) 而非 (0,1,...) 否则文字被吞）
        inline.children.splice(0, 0, checkbox);
      }
    });
  }

  // ── 离线通道：仓库上下文与相对 URL 改写 ───────────────────────────────
  //
  // 为什么改写发生在这一层（而不是 Kotlin 的 WebViewHtmlBuilder）：
  // 离线模式的输入是**未解析的 markdown 文本**。在那一层，`./docs/x.png` 与代码围栏里的
  // 示例文本、行内代码里的 `](../x)` 无法区分——任何正则都会误伤（既有 assets/ 改写即如此）。
  // markdown-it 渲染成 HTML 之后，代码围栏与行内代码里的尖括号/引号已经是转义文本
  // （`&lt;img src=&quot;…&quot;&gt;`），`<img … src="…"` 形式的属性正则不可能命中；而所有
  // 真实的链接目标（引用式 `[ref]: path`、相对路径、内联 HTML `<img src>`）都已是属性值。
  // 因此改写作用在**渲染产物**上：与 Kotlin 侧 SERVER_HTML 通道同一阶段、同一规则，
  // 且必须发生在 DOMPurify 清洗**之前**（URI 白名单会剔除未解析的相对 src）。
  //
  // 目标域（与 Kotlin rewriteRelativeUrls / 原生 resolveMarkdownUrl 保持同一约定）：
  // - img src → https://raw.githubusercontent.com/{owner}/{repo}/HEAD/{path}
  // - a href  → https://github.com/{owner}/{repo}/blob/HEAD/{path}（点击后由 GitHubLinkParser 分流到应用内路由）
  // - a href 的**路由形态**（`issues/123`、`/owner/repo/issues/123`）→ 对应的 github.com 路由
  //   （plan.md §2.11 的输入形态；否则会把 issue 链接渲染成仓库里名为 `issues/123` 的文件）
  // - assets/ → appassets 域（app 内置资产，不是仓库文件）

  var ASSET_BASE = 'https://appassets.androidplatform.net/assets/webview/';
  var RAW_BASE = 'https://raw.githubusercontent.com/';
  var GITHUB_BASE = 'https://github.com/';
  var IMG_SRC_REGEX = /(<img[^>]*?\ssrc=")([^"]*)(")/g;
  var ANCHOR_HREF_REGEX = /(<a[^>]*?\shref=")([^"]*)(")/g;

  /** GitHub 站点路由关键字（与 `GitHubLinkParser.parsePath` 的 vocabulary 对齐）。 */
  var SITE_ROUTE_SEGMENTS = ['issues', 'pull', 'blob', 'tree', 'commit', 'releases', 'discussions', 'raw'];

  /** `owner/repo`（或完整 repo URL）→ {owner, repo}；非法/缺省返回 null。 */
  function parseRepoContext(repoContext) {
    if (!repoContext) return null;
    var parts = String(repoContext).replace(/^https?:\/\/github\.com\//i, '').split('/');
    if (parts.length < 2 || !parts[0] || !parts[1]) return null;
    return { owner: parts[0], repo: parts[1] };
  }

  /**
   * 相对路径归一化：绝对 URL（http/https/data）与纯锚点原样返回；其余去掉 `./` 与前导 `/`，
   * 折叠 `.` / `..`（`..` 越出仓库根时钳制到根，而不是产出含 `..` 的死链）。
   *
   * 与 Kotlin `WebViewHtmlBuilder.normalizeRepoRelativePath` 是同一约定（两条通道同一套规则）。
   */
  function normalizeRelative(path) {
    if (/^https?:/i.test(path) || /^data:/i.test(path) || path.charAt(0) === '#') return path;
    var segments = path.replace(/^\//, '').split('/');
    var normalized = [];
    for (var i = 0; i < segments.length; i++) {
      if (segments[i] === '' || segments[i] === '.') continue;
      if (segments[i] === '..') {
        normalized.pop();
        continue;
      }
      normalized.push(segments[i]);
    }
    return normalized.length ? normalized.join('/') : path;
  }

  /**
   * 站点路径形态：前导 `/`，且第 3 段是 GitHub 路由关键字（`/owner/repo/issues/123`）。
   * 这种写法在网页端指 github.com 的站点路径，不是仓库内文件（plan.md §2.11）。
   */
  function isSiteRoutePath(path) {
    if (path.charAt(0) !== '/') return false;
    var parts = path.replace(/^\/+/, '').split('/');
    return parts.length >= 4 && SITE_ROUTE_SEGMENTS.indexOf(parts[2].toLowerCase()) >= 0;
  }

  /** 仓库内路由形态：`issues/123`、`pull/7`、`commit/<sha>`、`blob/<ref>/<path>`（形状明确，避免误伤同名目录）。 */
  function isRepoRoutePath(path) {
    var parts = path.split('/');
    if (parts.length < 2) return false;
    var head = parts[0].toLowerCase();
    if (head === 'issues' || head === 'pull' || head === 'discussions') return /^\d+$/.test(parts[1]);
    if (head === 'commit') return /^[0-9a-f]{7,40}$/i.test(parts[1]);
    if (head === 'blob' || head === 'tree' || head === 'raw') return parts.length >= 3 && parts[1] !== '';
    if (head === 'releases') return parts.length >= 3 && parts[1] === 'tag' && parts[2] !== '';
    return false;
  }

  /** 链接目标绝对化；null = 原样保留（绝对 URL / 锚点 / mailto）。 */
  function resolveLinkTarget(href, owner, repo) {
    if (/^https?:/i.test(href) || /^mailto:/i.test(href) || href.charAt(0) === '#') return null;
    if (isSiteRoutePath(href)) return GITHUB_BASE + href.replace(/^\/+/, '');
    var normalized = normalizeRelative(href);
    if (isRepoRoutePath(normalized)) return GITHUB_BASE + owner + '/' + repo + '/' + normalized;
    return GITHUB_BASE + owner + '/' + repo + '/blob/HEAD/' + normalized;
  }

  /** 图片目标绝对化；null = 原样保留（绝对 URL / data: / 锚点）。 */
  function resolveImageTarget(src, owner, repo) {
    if (/^https?:/i.test(src) || /^data:/i.test(src) || src.charAt(0) === '#') return null;
    var normalized = normalizeRelative(src);
    if (normalized.indexOf('assets/') === 0) {
      return ASSET_BASE + normalized.slice('assets/'.length);
    }
    return RAW_BASE + owner + '/' + repo + '/HEAD/' + normalized;
  }

  /** 把渲染产物里的相对 img src / a href 改写成绝对 URL（无仓库上下文时原样返回）。 */
  function rewriteRelativeUrls(html, repoContext) {
    var ctx = parseRepoContext(repoContext);
    if (!ctx) return html;

    var out = html.replace(IMG_SRC_REGEX, function (match, prefix, src, suffix) {
      var target = resolveImageTarget(src, ctx.owner, ctx.repo);
      if (target === null) return match;
      return prefix + target + suffix;
    });

    return out.replace(ANCHOR_HREF_REGEX, function (match, prefix, href, suffix) {
      var target = resolveLinkTarget(href, ctx.owner, ctx.repo);
      if (target === null) return match;
      return prefix + target + suffix;
    });
  }

  /** 离线 markdown-it 实例（配置与插件清单即离线通道的能力面）。 */
  function createMarkdownIt() {
    var md = window.markdownit({ html: true, linkify: true, breaks: false });
    md.use(githubAlertPlugin);
    md.use(taskListPlugin);
    return md;
  }

  /**
   * 原始 markdown → 注入 HTML（**纯字符串路径**，无 DOM 依赖）。
   *
   * 拆出这个函数是为了让离线通道的渲染产物可以被 Node 单测**真实执行**并逐串断言
   * （见 `core/markdown/src/test/js/offline-render-harness.js`）——JVM 无 JS 引擎，
   * 只靠源码文本断言无法证明「相对链接真的被改写了」。
   *
   * @param {string} raw 原始 markdown
   * @param {{repoContext: (string|null)}} options 仓库上下文（`owner/repo`）
   * @returns {string|null} HTML；markdown-it 未加载时返回 null（调用方降级为纯文本）
   */
  function renderOfflineHtml(raw, options) {
    var opts = options || {};
    if (typeof window.markdownit === 'undefined') return null;
    return rewriteRelativeUrls(createMarkdownIt().render(raw), opts.repoContext);
  }

  function renderOfflineMarkdown() {
    var rawEl = document.getElementById('markdown-raw');
    if (!rawEl) return null;
    var raw = rawEl.getAttribute('data-markdown-raw') || '';
    var html = renderOfflineHtml(raw, { repoContext: rawEl.getAttribute('data-base-repo') });
    if (html === null) {
      // markdown-it 未加载（assets 缺失）：原样显示原始 markdown，不阻断页面
      rawEl.textContent = raw;
      return null;
    }
    var container = document.createElement('div');
    container.innerHTML = html;
    rawEl.parentNode.replaceChild(container, rawEl);
    container.className = 'markdown-body';
    highlightCodeBlocks(container);
    return container;
  }

  // Exposed for offline JVM/Node tests of the GFM plugins + offline render pipeline
  // (not used by the bridge).
  window.__appdevMarkdownPlugins = {
    githubAlertPlugin: githubAlertPlugin,
    taskListPlugin: taskListPlugin,
    renderOfflineHtml: renderOfflineHtml,
    rewriteRelativeUrls: rewriteRelativeUrls,
    parseRepoContext: parseRepoContext,
  };

  function init() {
    var root = document.querySelector('.markdown-body');
    if (!root) return;

    // 离线模式优先渲染 markdown
    if (document.getElementById('markdown-raw')) {
      root = renderOfflineMarkdown() || root;
    }

    // 权威清洗
    sanitizeNode(root);

    // 绑定白名单事件
    bindLinks(root);
    bindImages(root);
    bindCheckboxes(root);
    bindCodeCopy(root);
    observeHeight(root);
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }
})();
