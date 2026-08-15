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
 *    补 GitHub Alert / 任务列表两个最小 GFM 插件，并用 highlight.js 高亮代码块
 *
 * 安全：本脚本不接收任何 token；token 仅由 PrivateImageInterceptor 加到网络请求。
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
        inline.children.splice(0, 1, checkbox);
      }
    });
  }

  function renderOfflineMarkdown() {
    var rawEl = document.getElementById('markdown-raw');
    if (!rawEl) return;
    var raw = rawEl.getAttribute('data-markdown-raw') || '';
    if (typeof window.markdownit === 'undefined') {
      rawEl.textContent = raw;
      return;
    }
    var md = window.markdownit({ html: true, linkify: true, breaks: false });
    md.use(githubAlertPlugin);
    md.use(taskListPlugin);
    var html = md.render(raw);
    var container = document.createElement('div');
    container.innerHTML = html;
    rawEl.parentNode.replaceChild(container, rawEl);
    container.className = 'markdown-body';
    highlightCodeBlocks(container);
    return container;
  }

  // Exposed for offline JVM/Node tests of the GFM plugins (not used by the bridge).
  window.__appdevMarkdownPlugins = {
    githubAlertPlugin: githubAlertPlugin,
    taskListPlugin: taskListPlugin,
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
