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
 * 3. 离线模式（OFFLINE_MARKDOWN_IT）：调用 markdown-it 渲染原始 markdown
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

  function renderOfflineMarkdown() {
    var rawEl = document.getElementById('markdown-raw');
    if (!rawEl) return;
    var raw = rawEl.getAttribute('data-markdown-raw') || '';
    if (typeof window.markdownit === 'undefined') {
      rawEl.textContent = raw;
      return;
    }
    var md = window.markdownit({ html: true, linkify: true, breaks: false });
    var html = md.render(raw);
    var container = document.createElement('div');
    container.innerHTML = html;
    rawEl.parentNode.replaceChild(container, rawEl);
    container.className = 'markdown-body';
    return container;
  }

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
