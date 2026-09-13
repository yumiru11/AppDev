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
 *    补 GitHub Alert / 任务列表 / emoji 短码 / 脚注 / 标题锚点 / @user 提及 /
 *    #123 引用 / 完整 sha 引用八个最小 GFM 插件，
 *    并用 highlight.js 高亮代码块；渲染产物再按仓库上下文改写相对链接/图片
 *    （见 renderOfflineHtml 的说明）
 * 4. 服务端 HTML 主通道（SERVER_HTML）同样用 highlight.js 高亮代码块（双预算护栏，
 *    见 highlightCodeBlocks）；图片统一补 loading="lazy" / decoding="async"
 *    （离线产物在 renderOfflineHtml 内联注入，服务端 HTML 由 decorateImages 在清洗后补）
 * 5. 数学公式（$…$ / $$…$$）由 renderMath 在 DOMPurify 清洗**之后**用离线 KaTeX 渲染
 *    （不得放宽清洗配置，理由见该函数上方的顺序决策说明）
 * 6. Mermaid 图（```mermaid 围栏 / GitHub README 的 pre[lang="mermaid"] / POST /markdown 的
 *    highlight-source-mermaid 代码块）由 renderMermaid 在清洗**之后**用离线 Mermaid Tiny 渲染；
 *    引擎门禁（Chromium ≥ 94，class static block）不满足时回退为普通代码块，并把
 *    「拦下 / 渲染了几张 / 失败几张」经 AndroidBridge.onMermaidResult 上报（见该函数上方的门禁说明）
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
    // <kbd>/<sub>/<sup> 是 DOMPurify 默认白名单内的惰性排版标签（离线通道的内嵌 HTML
    // 语义依赖它们；2026-09-12 恢复语义）。显式 ADD_TAGS 是**防漂移钉住**：后续若有人
    // 换成 ALLOWED_TAGS 或扩充 FORBID_TAGS，不会静默丢掉这三个标签的语义。
    // 不新增任何属性/协议面——不是安全放宽。
    ADD_TAGS: ['kbd', 'sub', 'sup'],
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
          // 页内锚点（#section、脚注 #fn-1 / #fnref-1）：WebView 内滚动，不交给 Kotlin
          // （过去 # 链接会走 onLinkClick → 外部浏览器/应用内路由，锚点永远跳不动）
          if (href.charAt(0) === '#') {
            scrollToAnchor(href.slice(1));
            return;
          }
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

  /**
   * 代码块复制按钮。
   *
   * 视觉全部交给 markdown-you.css 的 .md-copy-btn 规则：hover 设备悬停/聚焦才显示，
   * 触屏设备（@media (hover: none)）低强调常显 + :active 按压反馈。此前按钮靠内联
   * `opacity: 0` + mouseenter/mouseleave 显隐——手机上既没有 hover、按钮又不可见，
   * 等于不存在（2026-09-12 审计缺口）。内联样式会压过媒体查询，因此这里不再写视觉样式。
   */
  function bindCodeCopy(root) {
    var pres = root.querySelectorAll('pre');
    for (var i = 0; i < pres.length; i++) {
      (function (pre) {
        if (pre.querySelector('.md-copy-btn')) return;
        var btn = document.createElement('button');
        btn.className = 'md-copy-btn';
        btn.type = 'button';
        btn.textContent = 'Copy';
        pre.style.position = 'relative';
        pre.appendChild(btn);
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

  // 高亮双预算护栏（2026-09-12 审计缺口 1）：服务端 HTML 主通道此前完全不高亮；
  // 但 README/Issue 的产品化 HTML 可能含大量/超长代码块，全量 hljs 高亮会阻塞首屏
  // 与滚动手感。策略：按「块数 + 总字符数」设预算，预算内逐块高亮；单块超预算则跳过
  // 该块（继续尝试后续更小的块），预算耗尽后其余保持原文——可读性无损，只是不着色。
  // 阈值有回归断言（core/markdown 的 ServerHtmlChannelExecutionTest，Node 真实执行）。
  var HIGHLIGHT_MAX_BLOCKS = 30;
  var HIGHLIGHT_MAX_TOTAL_CHARS = 120000;

  /**
   * 高亮 root 下的语言代码块（两条通道共用）。
   *
   * @param {Element} root 内容根节点
   * @param {{maxBlocks: (number|undefined), maxTotalChars: (number|undefined)}} [options] 预算覆盖（测试用）
   * @returns {number} 实际高亮的块数
   */
  function highlightCodeBlocks(root, options) {
    if (typeof window.hljs === 'undefined') return 0;
    var opts = options || {};
    var maxBlocks = opts.maxBlocks || HIGHLIGHT_MAX_BLOCKS;
    var maxTotalChars = opts.maxTotalChars || HIGHLIGHT_MAX_TOTAL_CHARS;
    var codes = root.querySelectorAll('code[class*="language-"]');
    var budget = maxTotalChars;
    var highlighted = 0;
    for (var i = 0; i < codes.length && highlighted < maxBlocks; i++) {
      var size = (codes[i].textContent || '').length;
      if (size > budget) continue; // 单块超预算：跳过，留给后续更小的块
      budget -= size;
      try {
        window.hljs.highlightElement(codes[i]);
        highlighted++;
      } catch (e) {
        // 未知语言/解析失败时保留原文，不阻断渲染
      }
    }
    return highlighted;
  }

  // ── 数学公式（KaTeX 0.18.7，清洗后渲染）──────────────────────────────────
  //
  // 顺序决策（方案 B，见 docs/research/katex-mermaid-offline-feasibility.md §3.4/§4）：
  // 必须在 sanitizeNode() **之后**运行。PURIFY_CONFIG 的 FORBID_TAGS/FORBID_ATTR 含
  // 'style'，KaTeX 的排版产物（每式约 25 个 style 属性 + MathML）过一遍清洗就被剥掉样式；
  // 且 DOMPurify 的 FORBID_* 优先于 ADD_*，唯一「放宽」途径是把 style 移出 FORBID_*
  // ——那是全局削弱 sanitizer，已否决。因此 KaTeX 的产出作为**库生成的可信 DOM** 直接挂到
  // 已清洗的树上，LaTeX 源码侧由 trust:false（禁 \href/\html*）、throwOnError:false
  // （错误渲染为 .katex-error 而非抛）与 maxSize 上限兜住，绝不写 innerHTML。
  //
  // 误判防线（Kotlin 的 FeatureDetector.MATH_REGEX 只做「是否注入脚本」的开关；此处独立更严）：
  // 1. 只扫文本节点，天然跳过 pre/code/script/style/textarea —— 代码里的 $var 不中招；
  // 2. 块级 $$…$$ 可跨行且优先于行内；行内不跨行；
  // 3. 行内 $ 前不接字母/数字、$ 后非数字/空白、收尾 $ 前非空白、后不接字母/数字。
  // 风格与文件其余部分一致：ES5（var/function），新旧 WebView 同构。
  var MATH_MAX_FORMULAS = 120;
  var MATH_MAX_FORMULA_CHARS = 2000;
  var MATH_MAX_TOTAL_CHARS = 120000;
  var MATH_SKIP_TAGS = { PRE: 1, CODE: 1, SCRIPT: 1, STYLE: 1, TEXTAREA: 1 };
  var MATH_TOKEN_REGEX = /\$\$([^$]+?)\$\$|\$(?![\d\s])([^$\n]*[^\s$])\$/g;

  /** 该元素子树是否可参与数学扫描（代码/预格式/已渲染公式都不再扫）。 */
  function isMathScannableElement(el) {
    var tag = el.tagName ? String(el.tagName).toUpperCase() : '';
    if (MATH_SKIP_TAGS[tag]) return false;
    // KaTeX 产物（.katex / .katex-display / .katex-error）里含公式源码文本，重扫会自我递归
    var cls = el.className ? String(el.className) : '';
    return cls.indexOf('katex') < 0;
  }

  /** 收集可扫描的文本节点（含 `$` 的才收，避免无谓遍历）。 */
  function collectMathTextNodes(node, out) {
    var children = node.childNodes || [];
    for (var i = 0; i < children.length; i++) {
      var child = children[i];
      if (child.nodeType === 3) {
        if (child.nodeValue && child.nodeValue.indexOf('$') >= 0) out.push(child);
        continue;
      }
      if (child.nodeType !== 1) continue;
      if (isMathScannableElement(child)) collectMathTextNodes(child, out);
    }
    return out;
  }

  /**
   * 从一个文本节点的内容里提取数学段（纯函数，Node 单测直接覆盖）。
   *
   * @returns {Array<{start:number,end:number,tex:string,displayMode:boolean}>}
   */
  function scanMathText(text) {
    var out = [];
    if (!text || text.indexOf('$') < 0) return out;
    MATH_TOKEN_REGEX.lastIndex = 0;
    var match;
    while ((match = MATH_TOKEN_REGEX.exec(text)) !== null) {
      var block = match[1] !== undefined;
      var tex = block ? match[1] : match[2];
      var start = match.index;
      var end = MATH_TOKEN_REGEX.lastIndex;
      if (block) {
        // 块级：不与相邻 $ 粘连（$$$…$$$ 这种形态不视为公式）
        if (text.charAt(start - 1) === '$' || text.charAt(end) === '$') continue;
      } else {
        // 行内：两侧不接字母/数字（US$5、$x$5 不误判）
        var prev = start > 0 ? text.charAt(start - 1) : '';
        var next = end < text.length ? text.charAt(end) : '';
        if (/[A-Za-z0-9]/.test(prev) || /[A-Za-z0-9]/.test(next)) continue;
      }
      out.push({ start: start, end: end, tex: tex, displayMode: block });
    }
    return out;
  }

  /**
   * 读取 CSS 变量（KaTeX 的 errorColor 必须是具体色值——真机 WebView 不支持 color-mix，
   * 混色只能由 Kotlin 预计算后以变量注入）。取不到时返回备用色，保证深色下仍可读。
   */
  function readThemeColor(name, fallback) {
    try {
      if (typeof window.getComputedStyle === 'function' && document.documentElement) {
        var value = window.getComputedStyle(document.documentElement).getPropertyValue(name);
        if (value && String(value).trim()) return String(value).trim();
      }
    } catch (e) {
      // 主题变量读取失败不影响渲染，走备用色
    }
    return fallback;
  }

  /**
   * 渲染 root 下的数学公式（**清洗后** pass；服务端 HTML 与离线通道共用）。
   *
   * 预算护栏（与 highlightCodeBlocks 同构，测试可覆盖 options）：公式数、单式字符数、
   * 总字符数三个上限——超限的公式保留原文而不是静默丢内容；单式渲染失败同样保留原文。
   *
   * @param {Element} root 已清洗的内容根节点
   * @param {{maxFormulas:(number|undefined),maxFormulaChars:(number|undefined),maxTotalChars:(number|undefined),errorColor:(string|undefined)}} [options] 预算覆盖（测试用）
   * @returns {number} 实际渲染的公式数
   */
  function renderMath(root, options) {
    if (!root || typeof window.katex === 'undefined' || typeof window.katex.render !== 'function') return 0;
    var opts = options || {};
    var maxFormulas = opts.maxFormulas || MATH_MAX_FORMULAS;
    var maxFormulaChars = opts.maxFormulaChars || MATH_MAX_FORMULA_CHARS;
    var budget = opts.maxTotalChars || MATH_MAX_TOTAL_CHARS;
    var errorColor = opts.errorColor || readThemeColor('--md-sys-color-error', '#b3261e');
    var baseOptions = {
      throwOnError: false, // 语法错误 → 渲染 .katex-error，不抛、不丢正文
      strict: 'ignore', // 非致命 KaTeX 警示不打断
      trust: false, // 禁 \href / \html* / \includegraphics（未信源内容）
      maxSize: 25, // \rule/\kern 等尺寸上限（em）
      errorColor: errorColor,
    };

    var nodes = collectMathTextNodes(root, []);
    var rendered = 0;
    for (var i = 0; i < nodes.length && rendered < maxFormulas; i++) {
      var textNode = nodes[i];
      var segments = scanMathText(textNode.nodeValue);
      if (!segments.length) continue;
      var parent = textNode.parentNode;
      if (!parent) continue;

      var frag = document.createDocumentFragment();
      var cursor = 0;
      var replaced = false;
      for (var s = 0; s < segments.length; s++) {
        var segment = segments[s];
        if (rendered >= maxFormulas) continue;
        if (segment.tex.length > maxFormulaChars || segment.tex.length > budget) continue;
        if (segment.start > cursor) {
          frag.appendChild(document.createTextNode(textNode.nodeValue.slice(cursor, segment.start)));
        }
        budget -= segment.tex.length;
        var holder = document.createElement('span');
        holder.setAttribute('data-math', segment.displayMode ? 'block' : 'inline');
        var renderOptions = {
          throwOnError: baseOptions.throwOnError,
          strict: baseOptions.strict,
          trust: baseOptions.trust,
          maxSize: baseOptions.maxSize,
          errorColor: baseOptions.errorColor,
          displayMode: segment.displayMode,
        };
        try {
          window.katex.render(segment.tex, holder, renderOptions);
          rendered++;
        } catch (e) {
          // 渲染异常（非 ParseError，如环境问题）：保留原文，绝不吞内容
          holder.textContent = segment.tex;
        }
        frag.appendChild(holder);
        cursor = segment.end;
        replaced = true;
      }
      if (!replaced) continue;
      if (cursor < textNode.nodeValue.length) {
        frag.appendChild(document.createTextNode(textNode.nodeValue.slice(cursor)));
      }
      parent.replaceChild(frag, textNode);
    }
    return rendered;
  }

  // ── Mermaid 图表（@mermaid-js/tiny 11.17.2，清洗后渲染）─────────────────────
  //
  // 顺序决策：与 renderMath 相同——必须在 sanitizeNode() **之后**运行。Mermaid 把配色烘进
  // SVG 的内联 <style> 与 style 属性，而 PURIFY_CONFIG 的 FORBID_* 含 'style'，过一遍清洗
  // 全丢（可行性报告 §3.4 实测）；且 Mermaid 需要真实 DOM 度量 + 异步渲染，字符串阶段做不了。
  // 未信源图定义由 securityLevel:'strict'（编码 HTML、禁点击）+ htmlLabels:false 兜住，
  // 绝不改 loose/antiscript、绝不放宽 PURIFY_CONFIG。
  //
  // Chromium 门禁（可行性报告 §2.5/§6.4）：mermaid 11 的构建含 ES2024 class static block
  // （实测 mermaid.tiny.js 11.17.2 有 513 处 `static{`），Chromium < 94 在**解析期**就拒绝
  // 整个脚本——那是不可被脚本内 try/catch 捕获的语法级失败。因此三层门禁：
  //   1. Kotlin 侧按 WebView UA 的 Chrome/<major> 决定是否注入脚本（WebViewMermaidSupport）；
  //   2. 此处用 `new Function('class X { static { } }')` 做真语法探针：构造 Function 时会
  //      解析函数体，不支持的引擎抛**可捕获的** SyntaxError（probe 必须放在字符串里，
  //      直接写在本文件会让老引擎连 renderer.js 都解析不了）；
  //   3. `window.mermaid` 缺失（门禁拦下/脚本解析失败）→ 直接 no-op，代码块原样保留。
  // 任一不满足都回退为普通代码块。注：若未来加 CSP 且不含 unsafe-eval，探针会失败并
  // 同样回退（优雅降级，不崩页面；见可行性报告 R1）。
  //
  // 检测面（三类形态，与 Kotlin 侧 MERMAID_HTML_REGEX 对应）：
  // - 离线通道：`pre > code.language-mermaid`（markdown-it 对 ```mermaid 围栏的产物）；
  // - GitHub README 服务端 HTML（GET /repos/{o}/{r}/readme Accept html+json，2026-09-13 实测
  //   深链 CI 目标 mermaid-js/mermaid）：`<pre lang="mermaid" aria-label="Raw mermaid code">`
  //   （外层是 js-render-enrichment-target + render-plaintext-hidden，图由 github.com 前端
  //   脚本水合；旧报告里写的 highlight-source-mermaid **不是**这条路径的形态）；
  // - POST /markdown GFM 渲染（备用通道，可行性报告 §3.5 实测）：`div.highlight-source-mermaid > pre`。
  // 只在检测到图定义时才 parse/execute（性能护栏），单文档最多接管 MERMAID_MAX_DIAGRAMS 张。
  var MERMAID_MAX_DIAGRAMS = 10;

  /** class 属性（字符串形态）是否含指定类名。 */
  function hasClassName(el, name) {
    if (!el || !el.className) return false;
    return (' ' + String(el.className) + ' ').indexOf(' ' + name + ' ') >= 0;
  }

  /**
   * `<pre>` 是否承载 Mermaid 图定义；是则返回源文本，否则 null（纯函数，Node 单测直接覆盖）。
   *
   * 三类形态（互斥）：
   * - 离线通道：`pre > code.language-mermaid`（markdown-it 对 ```mermaid 围栏的产物）；
   * - GitHub README 服务端 HTML（GET /repos/{o}/{r}/readme html+json，2026-09-13 对 CI 目标
   *   mermaid-js/mermaid 实测）：`<pre lang="mermaid">`（位于 div.render-plaintext-hidden 内）；
   * - POST /markdown 备用通道：`div.highlight-source-mermaid > pre`（可行性报告 §3.5 实测）。
   */
  function mermaidSourceOf(pre) {
    if (!pre || pre.nodeType !== 1) return null;
    var tag = pre.tagName ? String(pre.tagName).toUpperCase() : '';
    if (tag !== 'PRE') return null;
    var children = pre.childNodes || [];
    for (var i = 0; i < children.length; i++) {
      var child = children[i];
      if (child.nodeType !== 1) continue;
      if (String(child.tagName).toUpperCase() === 'CODE' && hasClassName(child, 'language-mermaid')) {
        return child.textContent || '';
      }
    }
    // GitHub README 实测形态：属性在 <pre> 本身上（lang="mermaid"），无 language-* 子节点。
    if (typeof pre.getAttribute === 'function') {
      var lang = pre.getAttribute('lang');
      if (lang && String(lang).toLowerCase() === 'mermaid') return pre.textContent || '';
    }
    if (pre.parentNode && hasClassName(pre.parentNode, 'highlight-source-mermaid')) {
      return pre.textContent || '';
    }
    return null;
  }

  /** 深度优先收集承载 Mermaid 定义的 <pre>（命中即不下钻，避免重复计图）。 */
  function collectMermaidPreElements(node, out) {
    var children = node.childNodes || [];
    for (var i = 0; i < children.length; i++) {
      var child = children[i];
      if (child.nodeType !== 1) continue;
      if (mermaidSourceOf(child) !== null) {
        out.push(child);
        continue;
      }
      collectMermaidPreElements(child, out);
    }
    return out;
  }

  /** 沿 parentNode 向上找最近含指定类名的祖先（不含自身；fake DOM 无 closest）。 */
  function closestByClass(node, className) {
    var current = node ? node.parentNode : null;
    while (current && current.nodeType === 1) {
      if (hasClassName(current, className)) return current;
      current = current.parentNode;
    }
    return null;
  }

  /** 前一个元素兄弟（fake DOM 无 previousElementSibling）。 */
  function previousElementSibling(node) {
    if (!node || !node.parentNode) return null;
    var siblings = node.parentNode.childNodes || [];
    var previous = null;
    for (var i = 0; i < siblings.length; i++) {
      if (siblings[i] === node) return previous;
      if (siblings[i].nodeType === 1) previous = siblings[i];
    }
    return null;
  }

  /** 深度优先找第一个含指定类名的后代元素（fake DOM 无 querySelectorAll）。 */
  function findDescendantByClass(node, className) {
    var children = node.childNodes || [];
    for (var i = 0; i < children.length; i++) {
      var child = children[i];
      if (child.nodeType !== 1) continue;
      if (hasClassName(child, className)) return child;
      var found = findDescendantByClass(child, className);
      if (found) return found;
    }
    return null;
  }

  /** 逐个隐藏（display:none；style 不可用时退化为 hidden 属性）。 */
  function hideElements(elements) {
    for (var i = 0; i < (elements || []).length; i++) {
      var element = elements[i];
      if (!element) continue;
      if (element.style) element.style.display = 'none';
      else if (typeof element.setAttribute === 'function') element.setAttribute('hidden', '');
    }
  }

  /**
   * GitHub README 的 Mermaid 占位结构解析（**必须在替换 pre 之前调用**：替换后 pre 已脱离文档树）。
   *
   * GitHub 对 ```mermaid 围栏同时下发三份内容：可见的源码块
   * （`div.snippet-clipboard-content`）、待水合的 section（`section.js-render-needs-enrichment`，
   * 内含隐藏的 `div.render-plaintext-hidden > pre[lang="mermaid"]`）与加载指示器
   * （`span.js-render-enrichment-loader`）。github.com 的前端脚本把 section 换成真图并隐藏
   * 源码块；App 不跑 GitHub 脚本，故由 renderer.js 在**成功渲染后**补这一步。
   *
   * 只在成功路径隐藏：失败回退为普通代码块时源码块必须可见（绝不丢内容）。
   *
   * @returns {Array<Element>} 成功渲染后需要隐藏的元素（非 GitHub 结构/离线通道为空数组）
   */
  function githubEnrichmentNodesToHide(pre) {
    var section = closestByClass(pre, 'js-render-needs-enrichment');
    if (!section) return [];
    var nodes = [];
    var source = previousElementSibling(section);
    if (source && hasClassName(source, 'snippet-clipboard-content')) nodes.push(source);
    var loader = findDescendantByClass(section, 'js-render-enrichment-loader');
    if (loader) nodes.push(loader);
    return nodes;
  }

  /**
   * 引擎可用性（三层门禁的第 2/3 层）。
   *
   * @returns {{supported: boolean, reason: string}} reason ∈ '' | 'runtime'（脚本未加载）
   *   | 'syntax'（class static block 不被支持）
   */
  function detectMermaidEngine() {
    if (
      typeof window.mermaid === 'undefined' ||
      typeof window.mermaid.run !== 'function' ||
      typeof window.mermaid.initialize !== 'function'
    ) {
      return { supported: false, reason: 'runtime' };
    }
    try {
      // 语法探针：老引擎（Chromium < 94）在构造 Function 时解析函数体并抛 SyntaxError。
      // 探针字符串本身是 ES5 级语法，老引擎能解析；被解析的是字符串内容。
      new Function('class __appdev_mermaid_probe { static { var probe = 1; } }');
    } catch (e) {
      return { supported: false, reason: 'syntax' };
    }
    return { supported: true, reason: '' };
  }

  /**
   * Mermaid base 主题变量：从注入的 CSS 变量解析具体色值（真机 WebView 不支持
   * color-mix，混色已由 Kotlin 预计算；取不到时用备用色，保证深浅色都可读）。
   */
  function mermaidThemeVariables() {
    return {
      background: readThemeColor('--md-sys-color-surface', '#ffffff'),
      mainBkg: readThemeColor('--md-sys-color-surface-container-high', '#f6f8fa'),
      nodeBorder: readThemeColor('--md-sys-color-outline-variant', '#d0d7de'),
      primaryColor: readThemeColor('--md-sys-color-primary-container', '#e8def8'),
      primaryTextColor: readThemeColor('--md-sys-color-on-surface', '#1f2328'),
      primaryBorderColor: readThemeColor('--md-sys-color-primary', '#6750a4'),
      lineColor: readThemeColor('--md-sys-color-outline', '#8b949e'),
      textColor: readThemeColor('--md-sys-color-on-surface', '#1f2328'),
      secondaryColor: readThemeColor('--md-sys-color-secondary-container', '#e8def8'),
      tertiaryColor: readThemeColor('--md-sys-color-tertiary-container', '#ffd8e4'),
      clusterBkg: readThemeColor('--md-sys-color-surface-container', '#f6f8fa'),
      clusterBorder: readThemeColor('--md-sys-color-outline-variant', '#d0d7de'),
      edgeLabelBackground: readThemeColor('--md-sys-color-surface', '#ffffff'),
      fontFamily: readThemeColor('--fontStack-sansSerif', 'sans-serif'),
    };
  }

  /** 把接管失败的容器换回原始 <pre> 代码块（回退形态 = 普通代码块，绝不丢内容）。 */
  function restoreMermaidPre(item) {
    var holder = item.holder;
    var parent = holder.parentNode;
    if (!parent) return;
    try {
      parent.replaceChild(item.pre, holder);
    } catch (e) {
      // 已被别处移除（页面切换等）：忽略，不阻塞其它图
    }
  }

  /**
   * SVG 是否是 mermaid 的**错误卡片**（而非真图）。
   *
   * mermaid 11 在 `suppressErrors:true` 下对解析失败/不支持的类型也会注入 <svg>：
   * `aria-roledescription="error"` + `.error-icon`/`.error-text`（真实 Chromium 实测）。
   * 只查「是否有 SVG」会把错误卡片误判为渲染成功。
   */
  function isMermaidErrorSvg(svg) {
    if (!svg || typeof svg.getAttribute !== 'function') return false;
    if (String(svg.getAttribute('aria-roledescription') || '').toLowerCase() === 'error') return true;
    return typeof svg.querySelector === 'function' && !!svg.querySelector('.error-icon, .error-text');
  }

  /**
   * 容器是否已含**有效**渲染产物（真图 SVG，不是错误卡片）。
   *
   * 判定失败 = 回退为普通代码块 + 计入 failed。
   */
  function containsRenderedSvg(node) {
    var children = node.childNodes || [];
    for (var i = 0; i < children.length; i++) {
      var child = children[i];
      if (child.nodeType !== 1) continue;
      if (String(child.tagName).toUpperCase() === 'SVG') {
        if (!isMermaidErrorSvg(child)) return true;
        continue; // 错误卡片：继续找是否还有其它有效 SVG
      }
      if (containsRenderedSvg(child)) return true;
    }
    return false;
  }

  /**
   * Mermaid 渲染结果上报（JS → Kotlin bridge）。
   *
   * 语义与 Kotlin 侧 `MarkdownBridgeCallback.onMermaidResult` 一一对应：
   * rendered = 产出**有效** SVG（真图，非错误卡片）的图数；failed = 接管后未产出有效 SVG
   * （已恢复代码块）的图数；engineSupported = false 表示引擎不可用（Kotlin 门禁拦下 /
   * 语法探针失败 / window.mermaid 缺失）。
   *
   * 为什么引擎被拦下也要上报（rendered=0）：这是**机器可读的「为什么没渲染」**——
   * CI 的 mermaid-render-verify job 用 API 30（Chromium 83）断言 blocked 回退、
   * API 33（Chromium 101）断言 rendered>=1，没这条上报就只能靠人眼看截图。
   */
  function reportMermaidResult(rendered, failed, engineSupported) {
    if (!ANDROID_BRIDGE || typeof ANDROID_BRIDGE.onMermaidResult !== 'function') return;
    try {
      ANDROID_BRIDGE.onMermaidResult(rendered, failed, engineSupported);
    } catch (e) {
      // bridge 不可用/宿主未接线：静默（上报失败不得影响渲染本身）
    }
  }

  /**
   * run() 结束后收尾：未产出**有效** SVG 的容器（语法错误/不支持的类型，含 mermaid 错误卡片）
   * 恢复为代码块，并上报结果。
   *
   * @returns {number} 失败（已恢复代码块）的图数
   */
  function settleMermaidRun(items) {
    var failed = 0;
    for (var i = 0; i < items.length; i++) {
      if (!containsRenderedSvg(items[i].holder)) {
        restoreMermaidPre(items[i]);
        failed++;
      } else {
        // 成功：隐藏 GitHub 下发的源码块与加载指示器（真图替代源码展示；失败路径不隐藏）
        hideElements(items[i].hideOnSuccess);
      }
    }
    reportMermaidResult(items.length - failed, failed, true);
    return failed;
  }

  /**
   * 渲染 root 下的 Mermaid 图（**清洗后** pass；服务端 HTML 与离线通道共用）。
   *
   * 异步：`mermaid.run()` 的 Promise 完成后，对「未产出 SVG」的容器做回退（恢复原代码块）。
   * 预算护栏：maxDiagrams（默认 10，可行性报告 §6.3 的低端机建议）——超出的图保持代码块。
   *
   * @param {Element} root 已清洗的内容根节点
   * @param {{maxDiagrams: (number|undefined)}} [options] 预算覆盖（测试用）
   * @returns {number} 实际接管的图表数（0 = 引擎不可用 / 未检测到图 / 替换失败）
   */
  function renderMermaid(root, options) {
    if (!root) return 0;

    // 先收集候选（检测优先于执行）：无图页面不触碰引擎，也不上报——没有可观测事实。
    var candidates = collectMermaidPreElements(root, []);
    if (!candidates.length) return 0;

    // 引擎门禁（Chromium < 94 / 脚本未注入）：有图但引擎不可用 → 回退代码块 + 上报 blocked。
    var engine = detectMermaidEngine();
    if (!engine.supported) {
      reportMermaidResult(0, 0, false);
      return 0;
    }

    var opts = options || {};
    var maxDiagrams = opts.maxDiagrams || MERMAID_MAX_DIAGRAMS;
    var items = [];
    for (var i = 0; i < candidates.length && items.length < maxDiagrams; i++) {
      var pre = candidates[i];
      var parent = pre.parentNode;
      if (!parent) continue;
      var holder = document.createElement('div');
      holder.className = 'mermaid';
      holder.setAttribute('data-appdev-mermaid', 'pending');
      holder.textContent = mermaidSourceOf(pre) || '';
      // GitHub 占位结构必须在替换前解析（替换后 pre 脱离文档树，找不到 section）
      var hideOnSuccess = githubEnrichmentNodesToHide(pre);
      try {
        parent.replaceChild(holder, pre);
      } catch (e) {
        continue; // 替换失败（非真实 DOM 的边界形态）：保留代码块
      }
      items.push({ holder: holder, pre: pre, hideOnSuccess: hideOnSuccess });
    }
    if (!items.length) return 0;

    var nodes = items.map(function (item) {
      return item.holder;
    });
    var started;
    try {
      window.mermaid.initialize({
        startOnLoad: false,
        securityLevel: 'strict', // 未信源图定义：编码 HTML、禁点击（不得改 loose/antiscript）
        htmlLabels: false,
        theme: 'base',
        themeVariables: mermaidThemeVariables(),
      });
      started = window.mermaid.run({ nodes: nodes, suppressErrors: true });
    } catch (e) {
      // initialize/run 同步阶段异常：整体回退为代码块（图确实尝试过但没渲染出来 → failed 计数）
      for (var r = 0; r < items.length; r++) restoreMermaidPre(items[r]);
      reportMermaidResult(0, items.length, true);
      return 0;
    }
    if (started && typeof started.then === 'function') {
      started.then(
        function () {
          settleMermaidRun(items);
        },
        function () {
          settleMermaidRun(items);
        },
      );
    } else {
      // run() 未返回 thenable（异常实现/stub）：只能按当前 DOM 状态同步结算
      settleMermaidRun(items);
    }
    return items.length;
  }

  var IMG_TAG_REGEX = /<img\b[^>]*>/gi;

  /**
   * 给 HTML 字符串里的 `<img>` 补 loading="lazy" / decoding="async"（不覆盖已有值）。
   *
   * 作用于离线渲染产物（markdown-it 输出）——此时图片已是真实属性，代码块里的
   * 示例文本已被转义，正则不会误伤。服务端 HTML 通道不走这里（见 decorateImages）。
   */
  function addImageLoadingAttributes(html) {
    return html.replace(IMG_TAG_REGEX, function (tag) {
      var out = tag;
      if (!/\sloading\s*=/i.test(out)) out = out.replace(/<img\b/i, '<img loading="lazy"');
      if (!/\sdecoding\s*=/i.test(out)) out = out.replace(/<img\b/i, '<img decoding="async"');
      return out;
    });
  }

  /**
   * DOM 后处理：给 root 下所有图片补懒加载/异步解码（服务端 HTML 主通道）。
   *
   * 必须在 DOMPurify 清洗**之后**执行：清洗可能剥掉未知属性，且这里用 hasAttribute
   * 保证不覆盖服务端已经给出的 loading="eager" 等显式取值。
   */
  function decorateImages(root) {
    var imgs = root.querySelectorAll('img');
    for (var i = 0; i < imgs.length; i++) {
      if (!imgs[i].hasAttribute('loading')) imgs[i].setAttribute('loading', 'lazy');
      if (!imgs[i].hasAttribute('decoding')) imgs[i].setAttribute('decoding', 'async');
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

  // ── 离线 GFM 补齐：emoji 短码 / 标题锚点 / 脚注（审计 §9 第 4 条） ──────
  //
  // 三个插件都只作用于 markdown-it 解析后的 token 流：代码围栏是块级 code/fence token、
  // 行内代码是 code_inline token——都不经过 text token（emoji）或 \s 规则（脚注/锚点），
  // 「不误伤代码」是结构保证而不是正则巧合。

  /**
   * 页内锚点滚动（plan.md §2.9 的 Kotlin→JS 契约 `scrollToAnchor(id)`）。
   *
   * 由两处调用：`bindLinks` 拦截 `a[href^="#"]` 点击、以及 Kotlin 侧
   * `WebView.evaluateJavascript("scrollToAnchor('…')")`。
   * 找不到目标时返回 false（调用方无需处理，页面保持原位）。
   */
  function scrollToAnchor(id) {
    var target = String(id == null ? '' : id).replace(/^#/, '');
    if (!target) return false;
    try {
      target = decodeURIComponent(target);
    } catch (e) {
      // 非法百分号编码：按原样查找
    }
    var element = document.getElementById(target);
    if (!element) return false;
    if (element.scrollIntoView) {
      element.scrollIntoView({ behavior: 'smooth', block: 'start' });
    }
    return true;
  }

  // Kotlin 侧可 evaluateJavascript 的入口（发现页/目录跳转预留的公共 API）
  window.scrollToAnchor = scrollToAnchor;

  /** GitHub 常用 emoji 短码 → Unicode（gemoji 精选子集；未收录的短码原样保留）。 */
  var EMOJI_DATA =
    'smile:😄,smiley:😃,grin:😁,laughing:😆,satisfied:😆,sweat_smile:😅,rofl:🤣,joy:😂,' +
    'relaxed:☺️,blush:😊,innocent:😇,slightly_smiling_face:🙂,upside_down_face:🙃,wink:😉,' +
    'heart_eyes:😍,smiling_face_with_three_hearts:🥰,kissing_heart:😘,kissing:😗,' +
    'kissing_smiling_eyes:😙,kissing_closed_eyes:😚,yum:😋,stuck_out_tongue:😛,' +
    'stuck_out_tongue_winking_eye:😜,stuck_out_tongue_closed_eyes:😝,zany_face:🤪,' +
    'face_with_raised_eyebrow:🤨,nerd_face:🤓,sunglasses:😎,star_struck:🤩,partying_face:🥳,' +
    'smirk:😏,unamused:😒,disappointed:😞,pensive:😔,worried:😟,confused:😕,' +
    'slightly_frowning_face:🙁,frowning_face:☹️,persevere:😣,confounded:😖,tired_face:😫,' +
    'weary:😩,triumph:😤,angry:😠,rage:😡,no_mouth:😶,neutral_face:😐,expressionless:😑,' +
    'hushed:😯,frowning:😦,anguished:😧,open_mouth:😮,astonished:😲,flushed:😳,pleading_face:🥺,' +
    'fearful:😨,cold_sweat:😰,disappointed_relieved:😥,cry:😢,sob:😭,scream:😱,sweat:😓,' +
    'sleepy:😪,sleeping:😴,dizzy_face:😵,exploding_head:🤯,face_with_thermometer:🤒,' +
    'face_with_head_bandage:🤕,nauseated_face:🤢,sneezing_face:🤧,mask:😷,cowboy_hat_face:🤠,' +
    'smiling_imp:😈,imp:👿,japanese_ogre:👹,japanese_goblin:👺,clown_face:🤡,ghost:👻,skull:💀,' +
    'alien:👽,robot:🤖,poop:💩,hankey:💩,angel:😇,santa:🎅,baby:👶,' +
    'heart:❤️,broken_heart:💔,two_hearts:💕,sparkling_heart:💖,heartpulse:💗,heartbeat:💓,' +
    'blue_heart:💙,green_heart:💚,yellow_heart:💛,purple_heart:💜,orange_heart:🧡,black_heart:🖤,' +
    'white_heart:🤍,gift_heart:💝,cupid:💘,kiss:💋,love_letter:💌,100:💯,anger:💢,boom:💥,' +
    'collision:💥,dizzy:💫,sparkles:✨,star:⭐,star2:🌟,comet:☄️,zap:⚡,fire:🔥,sunny:☀️,' +
    'rainbow:🌈,cloud:☁️,snowflake:❄️,snowman:⛄,droplet:💧,ocean:🌊,earth_africa:🌍,' +
    'earth_americas:🌎,earth_asia:🌏,full_moon:🌕,new_moon:🌑,crescent_moon:🌙,sun_with_face:🌞,' +
    'thumbsup:👍,+1:👍,thumbsdown:👎,-1:👎,ok_hand:👌,punch:👊,fist:✊,v:✌️,wave:👋,raised_hand:✋,' +
    'hand:✋,raised_hands:🙌,open_hands:👐,pray:🙏,clap:👏,muscle:💪,metal:🤘,point_left:👈,' +
    'point_right:👉,point_up:☝️,point_down:👇,point_up_2:👆,writing_hand:✍️,nail_care:💅,selfie:🤳,' +
    'eyes:👀,eye:👁️,ear:👂,nose:👃,tongue:👅,lips:👄,brain:🧠,' +
    'dog:🐶,cat:🐱,mouse:🐭,hamster:🐹,rabbit:🐰,fox_face:🦊,bear:🐻,panda_face:🐼,koala:🐨,' +
    'tiger:🐯,lion:🦁,cow:🐮,pig:🐷,pig_nose:🐽,frog:🐸,monkey:🐵,see_no_evil:🙈,hear_no_evil:🙉,' +
    'speak_no_evil:🙊,chicken:🐔,penguin:🐧,bird:🐦,baby_chick:🐤,hatching_chick:🐣,hatched_chick:🐥,' +
    'duck:🦆,eagle:🦅,owl:🦉,bat:🦇,wolf:🐺,boar:🐗,horse:🐴,unicorn:🦄,bee:🐝,honeybee:🐝,' +
    'bug:🐛,butterfly:🦋,snail:🐌,beetle:🐞,ant:🐜,spider:🕷️,spider_web:🕸️,scorpion:🦂,crab:🦀,' +
    'snake:🐍,lizard:🦎,turtle:🐢,tropical_fish:🐠,fish:🐟,blowfish:🐡,dolphin:🐬,whale:🐳,' +
    'whale2:🐋,crocodile:🐊,leopard:🐆,zebra:🦓,gorilla:🦍,elephant:🐘,rhino:🦏,sheep:🐑,goat:🐐,' +
    'rooster:🐓,turkey:🦃,peacock:🦚,parrot:🦜,swan:🦢,flamingo:🦩,dove:🕊️,dragon:🐉,' +
    'dragon_face:🐲,t_rex:🦖,sauropod:🦕,octopus:🐙,shell:🐚,squid:🦑,shrimp:🦐,cherry_blossom:🌸,' +
    'rose:🌹,wilted_flower:🥀,tulip:🌷,hibiscus:🌺,bouquet:💐,sunflower:🌻,four_leaf_clover:🍀,' +
    'maple_leaf:🍁,fallen_leaf:🍂,leaves:🍃,mushroom:🍄,cactus:🌵,palm_tree:🌴,evergreen_tree:🌲,' +
    'deciduous_tree:🌳,seedling:🌱,herb:🌿,ear_of_rice:🌾,chestnut:🌰,apple:🍎,green_apple:🍏,' +
    'pear:🍐,tangerine:🍊,lemon:🍋,banana:🍌,watermelon:🍉,grapes:🍇,strawberry:🍓,melon:🍈,' +
    'cherries:🍒,peach:🍑,pineapple:🍍,kiwi_fruit:🥝,tomato:🍅,eggplant:🍆,avocado:🥑,' +
    'broccoli:🥦,cucumber:🥒,carrot:🥕,corn:🌽,hot_pepper:🌶️,potato:🥔,sweet_potato:🍠,' +
    'bread:🍞,croissant:🥐,baguette_bread:🥖,pretzel:🥨,pancakes:🥞,cheese:🧀,meat_on_bone:🍖,' +
    'poultry_leg:🍗,hamburger:🍔,fries:🍟,pizza:🍕,hotdog:🌭,sandwich:🥪,taco:🌮,burrito:🌯,' +
    'ramen:🍜,spaghetti:🍝,curry:🍛,sushi:🍣,bento:🍱,rice:🍚,rice_ball:🍙,rice_cracker:🍘,' +
    'egg:🥚,fried_egg:🍳,honey_pot:🍯,cake:🍰,birthday:🎂,custard:🍮,lollipop:🍭,candy:🍬,' +
    'chocolate_bar:🍫,icecream:🍦,ice_cream:🍨,doughnut:🍩,cookie:🍪,milk_glass:🥛,coffee:☕,' +
    'tea:🍵,sake:🍶,beer:🍺,beers:🍻,clinking_glasses:🥂,wine_glass:🍷,cocktail:🍸,' +
    'tropical_drink:🍹,champagne:🍾,' +
    'rocket:🚀,tada:🎉,confetti_ball:🎊,balloon:🎈,gift:🎁,sparkler:🎇,fireworks:🎆,' +
    'checkered_flag:🏁,trophy:🏆,medal_sports:🏅,first_place_medal:🥇,second_place_medal:🥈,' +
    'third_place_medal:🥉,soccer:⚽,basketball:🏀,football:🏈,baseball:⚾,tennis:🎾,volleyball:🏐,' +
    'rugby_football:🏉,golf:⛳,fishing_pole_and_fish:🎣,dart:🎯,8ball:🎱,game_die:🎲,video_game:🎮,' +
    'musical_note:🎵,notes:🎶,headphones:🎧,microphone:🎤,guitar:🎸,violin:🎻,trumpet:🎺,drum:🥁,' +
    'movie_camera:🎥,clapper:🎬,art:🎨,camera:📷,iphone:📱,computer:💻,desktop_computer:🖥️,' +
    'keyboard:⌨️,printer:🖨️,floppy_disk:💾,cd:💿,dvd:📀,tv:📺,radio:📻,battery:🔋,electric_plug:🔌,' +
    'bulb:💡,flashlight:🔦,candle:🕯️,wastebasket:🗑️,moneybag:💰,money_with_wings:💸,dollar:💵,' +
    'credit_card:💳,gem:💎,scales:⚖️,wrench:🔧,hammer:🔨,hammer_and_wrench:🛠️,nut_and_bolt:🔩,' +
    'gear:⚙️,chains:⛓️,bomb:💣,hocho:🔪,dagger:🗡️,crossed_swords:⚔️,shield:🛡️,smoking:🚬,' +
    'coffin:⚰️,crystal_ball:🔮,telescope:🔭,microscope:🔬,satellite:📡,syringe:💉,pill:💊,ring:💍,' +
    'key:🔑,lock:🔒,unlock:🔓,closed_lock_with_key:🔐,bell:🔔,no_bell:🔕,bookmark:🔖,link:🔗,' +
    'paperclip:📎,pushpin:📌,round_pushpin:📍,scissors:✂️,pen:🖊️,fountain_pen:🖋️,pencil2:✏️,' +
    'crayon:🖍️,paintbrush:🖌️,mag:🔍,mag_right:🔎,mailbox:📫,package:📦,page_facing_up:📄,' +
    'page_with_curl:📃,clipboard:📋,memo:📝,book:📖,open_book:📖,books:📚,newspaper:📰,notebook:📓,' +
    'ledger:📒,closed_book:📕,green_book:📗,blue_book:📘,orange_book:📙,scroll:📜,calendar:📅,' +
    'date:📆,chart_with_upwards_trend:📈,chart_with_downwards_trend:📉,bar_chart:📊,file_folder:📁,' +
    'open_file_folder:📂,spiral_notepad:🗒️,file_cabinet:🗄️,framed_picture:🖼️,warning:⚠️,' +
    'no_entry:⛔,no_entry_sign:🚫,stop_sign:🛑,construction:🚧,rotating_light:🚨,traffic_light:🚦,' +
    'vertical_traffic_light:🚥,recycle:♻️,beginner:🔰,sos:🆘,speaker:🔈,sound:🔉,loud_sound:🔊,' +
    'mute:🔇,mega:📣,loudspeaker:📢,question:❓,grey_question:❔,exclamation:❗,grey_exclamation:❕,' +
    'white_check_mark:✅,heavy_check_mark:✔️,x:❌,o:⭕,o2:🅾️,negative_squared_cross_mark:❎,' +
    'curly_loop:➰,loop:➿,arrow_up:⬆️,arrow_down:⬇️,arrow_left:⬅️,arrow_right:➡️,' +
    'arrows_clockwise:🔃,arrows_counterclockwise:🔄,anchor:⚓,boat:⛵,ship:🚢,airplane:✈️,' +
    'helicopter:🚁,steam_locomotive:🚂,train:🚆,metro:🚇,tram:🚊,bus:🚌,ambulance:🚑,' +
    'fire_engine:🚒,police_car:🚓,taxi:🚕,car:🚗,bike:🚲,motorcycle:🏍️,walking:🚶,runner:🏃,' +
    'dancer:💃,man_dancing:🕺,couple:👫,family:👨‍👩‍👧';

  var EMOJI_SHORTCODES = (function () {
    var map = {};
    EMOJI_DATA.split(',').forEach(function (pair) {
      var separator = pair.indexOf(':');
      if (separator > 0) map[pair.slice(0, separator)] = pair.slice(separator + 1);
    });
    return map;
  })();

  /**
   * emoji 短码：`:rocket:` → 🚀（GitHub 惯用写法，§2.3 第 26 条）。
   *
   * 只重写 inline token 的 text 子节点：`code_inline`（行内代码）是独立 token 类型，
   * 围栏/缩进代码块是块级 token（不进入 inline children）——代码里的 `:rocket:` 结构上不可能被替换。
   */
  function emojiPlugin(md) {
    md.core.ruler.after('inline', 'emoji_shortcodes', function (state) {
      var tokens = state.tokens;
      for (var i = 0; i < tokens.length; i++) {
        if (tokens[i].type !== 'inline' || !tokens[i].children) continue;
        var children = tokens[i].children;
        for (var j = 0; j < children.length; j++) {
          if (children[j].type !== 'text') continue;
          children[j].content = children[j].content.replace(/:([a-z0-9_+-]+):/g, function (match, name) {
            return Object.prototype.hasOwnProperty.call(EMOJI_SHORTCODES, name) ? EMOJI_SHORTCODES[name] : match;
          });
        }
      }
    });
  }

  // ── 离线 GFM 补齐：@user / #123 / 裸 sha（2026-09-12） ──────────────────
  //
  // 只做**用户**提及：`@org/team` 没有应用内路由（GitHubLinkParser 明确把 `@org/team`
  // 归为 External），因此整段保持纯文本——绝不允许把 `@org` 从 `@org/team` 里切出来
  // 半截链接（负向前瞻 `(?![A-Za-z0-9\-/])` 保证）。
  //
  // #123 / 裸 sha 需要仓库上下文（`owner/repo`）才能拼出绝对 URL：无上下文时整段保持纯
  // 文本（`href="#123"` 会被 WebView 的 bindLinks 当成页内锚点吞掉，绝不能产出）。
  // `#123` → `{repo}/issues/123`（GitHub 对 PR 会 302 到 /pull/N）；**完整 40 位** hex
  // sha → `{repo}/commit/<sha>`（短 sha 不链接，避免误伤正文里的短 hex 词）。
  // 点击后由 Kotlin 侧 GitHubLinkParser 分流到应用内 Issue/Commit 路由。
  //
  // 作用面与 emojiPlugin 同构：只改写 inline token 的 text 子节点。行内代码是 code_inline
  // token、围栏是块级 token，结构上不进 text 子节点；已有链接（link_open…link_close）与
  // 内联 HTML 的 <a> 之间整段跳过，避免 <a> 嵌套。邮箱（`octocat@github.com`）因 @ 前是
  // 词字符而不匹配；`#123` 前是词字符/`#`/`/` 时不匹配；sha 前后必须是词边界。

  /** 提及词法：GitHub 用户名为 1–39 位字母/数字/连字符，首尾不得是连字符。 */
  var MENTION_SOURCE = '(^|[^\\w./+@-])@([A-Za-z0-9](?:[A-Za-z0-9-]{0,37}[A-Za-z0-9])?)(?![A-Za-z0-9\\-/])';
  /** issue/PR 引用词法：仓库内裸 `#123`（`owner/repo#123` 的 `#` 前是词字符，不命中）。 */
  var ISSUE_REF_SOURCE = '(^|[^\\w#/])#(\\d+)(?![0-9])';
  /** 裸提交词法：**完整 40 位** hex。 */
  var COMMIT_SHA_SOURCE = '(^|[^\\w@#])([0-9a-fA-F]{40})(?![0-9a-fA-F])';
  /** 内联 HTML 锚：html:true 下原样透传，必须计入 link 深度护栏（否则会嵌第二层 <a>）。 */
  var HTML_ANCHOR_OPEN = /^<a(?:\s[^>]*)?>/i;
  var HTML_ANCHOR_CLOSE = /^<\/a\s*>/i;
  var GITHUB_PROFILE_BASE = 'https://github.com/';

  /**
   * 把一段 text 按 `source` 切成「纯文本 / 链接」片段；没有命中返回 null（调用方保持原 token）。
   *
   * 每次调用新建 RegExp：避免共享 `g` 正则的 lastIndex 状态串味。
   *
   * @returns {Array<{text: string}|{href: string, label: string}>|null}
   */
  function referenceSegments(content, source, hrefOf, labelOf) {
    var pattern = new RegExp(source, 'g');
    var segments = [];
    var cursor = 0;
    var found = false;
    var match;
    while ((match = pattern.exec(content)) !== null) {
      found = true;
      // 前缀（空白/标点）并入前一段纯文本，不能丢
      var before = content.slice(cursor, match.index) + match[1];
      if (before) segments.push({ text: before });
      segments.push({ href: hrefOf(match[2]), label: labelOf(match[2]) });
      cursor = match.index + match[0].length;
    }
    if (!found) return null;
    if (cursor < content.length) segments.push({ text: content.slice(cursor) });
    return segments;
  }

  function mentionSegments(content) {
    return referenceSegments(
      content,
      MENTION_SOURCE,
      function (user) {
        return GITHUB_PROFILE_BASE + user;
      },
      function (user) {
        return '@' + user;
      },
    );
  }

  /** `{owner}/{repo}` → `https://github.com/{owner}/{repo}`；无上下文返回 null。 */
  function repoBase(ctx) {
    return ctx ? GITHUB_BASE + ctx.owner + '/' + ctx.repo : null;
  }

  function issueRefSegments(content, base) {
    return referenceSegments(
      content,
      ISSUE_REF_SOURCE,
      function (number) {
        return base + '/issues/' + number;
      },
      function (number) {
        return '#' + number;
      },
    );
  }

  function commitShaSegments(content, base) {
    return referenceSegments(
      content,
      COMMIT_SHA_SOURCE,
      function (sha) {
        return base + '/commit/' + sha;
      },
      function (sha) {
        return sha;
      },
    );
  }

  /**
   * inline children 就地改写：片段 → link_open/text/link_close 三连 token。
   *
   * link 深度护栏覆盖两类锚：markdown 链接 token 与内联 HTML 的 `<a>`。
   */
  function linkifyTextTokens(state, children, segmentsOf, context) {
    var result = [];
    var linkDepth = 0;
    for (var i = 0; i < children.length; i++) {
      var token = children[i];
      if (token.type === 'link_open') linkDepth++;
      if (token.type === 'link_close') linkDepth--;
      if (token.type === 'html_inline') {
        if (HTML_ANCHOR_OPEN.test(token.content)) linkDepth++;
        if (HTML_ANCHOR_CLOSE.test(token.content)) linkDepth--;
      }
      if (token.type !== 'text' || linkDepth > 0) {
        result.push(token);
        continue;
      }
      var segments = segmentsOf(token.content, context);
      if (!segments) {
        result.push(token);
        continue;
      }
      for (var s = 0; s < segments.length; s++) {
        if (segments[s].href) {
          var open = newToken(state, 'link_open', 'a', 1);
          open.attrSet('href', segments[s].href);
          var label = newToken(state, 'text', '', 0);
          label.content = segments[s].label;
          result.push(open, label, newToken(state, 'link_close', 'a', -1));
        } else {
          var text = newToken(state, 'text', '', 0);
          text.content = segments[s].text;
          result.push(text);
        }
      }
    }
    return result;
  }

  /**
   * `@user` → `<a href="https://github.com/user">@user</a>`（GitHub 网页端同样把它渲染成链接）。
   *
   * 点击后由 Kotlin 侧 `GitHubLinkParser.parseUrl → ParsedUrl.User` 分流到应用内用户页。
   */
  function mentionPlugin(md) {
    md.core.ruler.after('inline', 'mention_links', function (state) {
      var tokens = state.tokens;
      for (var i = 0; i < tokens.length; i++) {
        var inline = tokens[i];
        if (inline.type !== 'inline' || !inline.children) continue;
        inline.children = linkifyTextTokens(state, inline.children, mentionSegments, null);
      }
    });
  }

  /**
   * `#123` → `{repo}/issues/123`（GitHub 对 PR 会 302 到 /pull/N）。
   *
   * 无仓库上下文（`data-base-repo` 缺失/非法）→ 整段纯文本，绝不产 `href="#123"`。
   */
  function issueRefPlugin(md) {
    md.core.ruler.after('inline', 'github_issue_refs', function (state) {
      var base = repoBase(parseRepoContext(state.env && state.env.repoContext));
      if (!base) return;
      var tokens = state.tokens;
      for (var i = 0; i < tokens.length; i++) {
        var inline = tokens[i];
        if (inline.type !== 'inline' || !inline.children) continue;
        inline.children = linkifyTextTokens(state, inline.children, issueRefSegments, base);
      }
    });
  }

  /** **完整 40 位** hex sha → `{repo}/commit/<sha>`（短 sha 不链接）。 */
  function commitShaPlugin(md) {
    md.core.ruler.after('inline', 'github_commit_shas', function (state) {
      var base = repoBase(parseRepoContext(state.env && state.env.repoContext));
      if (!base) return;
      var tokens = state.tokens;
      for (var i = 0; i < tokens.length; i++) {
        var inline = tokens[i];
        if (inline.type !== 'inline' || !inline.children) continue;
        inline.children = linkifyTextTokens(state, inline.children, commitShaSegments, base);
      }
    });
  }

  /** GitHub slug 的字符剔除：保留字母/数字/组合符号/连字符（CJK 字母不会被剔除）。 */
  var SLUG_STRIP = (function () {
    try {
      return new RegExp('[^\\p{L}\\p{N}\\p{M}\\-]', 'gu');
    } catch (e) {
      // 老 WebView 不支持 Unicode 属性转义：退化为 ASCII 规则（CJK 标题退化为 section-N）
      return /[^a-z0-9\-]/g;
    }
  })();

  /**
   * 标题锚点 id：`## Section With Hyphen` → `<h2 id="section-with-hyphen">`（GitHub slug 规则，
   * 重复标题追加 `-1`、`-2`）。配合 `scrollToAnchor` 实现 `#section` 跳转（§2.3 第 28 条）。
   */
  function anchorPlugin(md) {
    function slugify(text) {
      return String(text).trim().toLowerCase().replace(/\s+/g, '-').replace(SLUG_STRIP, '');
    }

    function textOf(inline) {
      var out = '';
      var children = inline.children || [];
      for (var i = 0; i < children.length; i++) {
        var child = children[i];
        if (child.type === 'text' || child.type === 'code_inline') out += child.content;
        if (child.type === 'softbreak' || child.type === 'hardbreak') out += ' ';
      }
      return out;
    }

    md.core.ruler.after('emoji_shortcodes', 'anchor_headings', function (state) {
      var tokens = state.tokens;
      if (!state.env.__anchorCounts) state.env.__anchorCounts = {};
      var counts = state.env.__anchorCounts;
      for (var i = 0; i < tokens.length; i++) {
        if (tokens[i].type !== 'heading_open') continue;
        var inline = tokens[i + 1];
        if (!inline || inline.type !== 'inline') continue;
        var base = slugify(textOf(inline)) || 'section';
        var seen = counts[base] || 0;
        counts[base] = seen + 1;
        tokens[i].attrSet('id', seen === 0 ? base : base + '-' + seen);
      }
    });
  }

  /**
   * 脚注：`[^1]` 引用 + `[^1]: 定义`（§2.3 第 35 条，尽力而为）。
   *
   * 简化点（相对 markdown-it-footnote）：定义只支持行首 + 四空格缩进续行，不支持多段落
   * 定义的嵌套块解析；编号按引用出现顺序，重复引用复用同一编号。产物形态对齐
   * github-markdown-css：`[data-footnote-ref]`、`.footnotes`、`data-footnote-backref`。
   */
  function footnotePlugin(md) {
    function ensureEnv(env) {
      if (!env.footnotes) {
        env.footnotes = { defs: {}, order: [], indexMap: {}, refCounts: {} };
      }
      return env.footnotes;
    }

    function lineText(state, line) {
      return state.src.slice(state.bMarks[line] + state.tShift[line], state.eMarks[line]);
    }

    function footnoteDefRule(state, startLine, endLine, silent) {
      var match = /^\[\^([^\]\s]+)\]:[ \t]?(.*)$/.exec(lineText(state, startLine));
      if (!match) return false;
      if (silent) return true;

      var footnotes = ensureEnv(state.env);
      var label = match[1];
      var content = match[2];
      var next = startLine + 1;
      // 四空格缩进的续行并入定义；空行/非缩进行结束定义
      while (next < endLine) {
        var text = lineText(state, next);
        if (text.trim() === '' || state.sCount[next] < 4) break;
        content += '\n' + text.replace(/^ {0,4}/, '');
        next++;
      }
      footnotes.defs[label] = content;
      state.line = next;
      return true;
    }

    function footnoteRefRule(state, silent) {
      var start = state.pos;
      if (state.src.charCodeAt(start) !== 0x5B || state.src.charCodeAt(start + 1) !== 0x5E) return false;
      var end = state.src.indexOf(']', start + 2);
      if (end < 0) return false;
      var label = state.src.slice(start + 2, end);
      if (!label || /[\s\[\]]/.test(label)) return false;
      var defs = state.env.footnotes && state.env.footnotes.defs;
      // 无定义 = 普通文本（GitHub 同样如此），不能因为缺定义把正文吃掉
      if (!defs || !Object.prototype.hasOwnProperty.call(defs, label)) return false;
      if (!silent) {
        var token = state.push('footnote_ref', '', 0);
        token.meta = { label: label };
      }
      state.pos = end + 1;
      return true;
    }

    md.block.ruler.before('reference', 'footnote_def', footnoteDefRule, { alt: ['paragraph', 'reference'] });
    md.inline.ruler.before('link', 'footnote_ref', footnoteRefRule);

    md.renderer.rules.footnote_ref = function (tokens, idx, options, env) {
      var footnotes = env.footnotes;
      if (!footnotes) return '';
      var label = tokens[idx].meta.label;
      if (!footnotes.indexMap[label]) {
        footnotes.order.push(label);
        footnotes.indexMap[label] = footnotes.order.length;
      }
      var number = footnotes.indexMap[label];
      footnotes.refCounts[label] = (footnotes.refCounts[label] || 0) + 1;
      var occurrence = footnotes.refCounts[label];
      var refId = occurrence === 1 ? 'fnref-' + number : 'fnref-' + number + '-' + occurrence;
      return '<sup class="footnote-ref"><a href="#fn-' + number + '" id="' + refId +
        '" data-footnote-ref aria-describedby="footnote-label">' + number + '</a></sup>';
    };
  }

  /**
   * 追加脚注定义区（在 markdown-it 渲染完成后）。
   *
   * `env.footnotes.order` 由 `footnote_ref` 渲染规则按输出顺序填充——定义正文此时才做
   * 第二次 `md.render`（定义里可以有行内 markdown，但不能有新的脚注定义）。
   */
  function appendFootnotes(md, html, env) {
    var footnotes = env && env.footnotes;
    if (!footnotes || !footnotes.order.length) return html;

    var items = footnotes.order.map(function (label) {
      var number = footnotes.indexMap[label];
      var body = md.render(footnotes.defs[label]).trim();
      var backref =
        '<a href="#fnref-' + number + '" class="data-footnote-backref" data-footnote-backref' +
        ' aria-label="Back to reference ' + number + '">↩</a>';
      if (/<\/p>$/.test(body)) {
        body = body.replace(/<\/p>$/, ' ' + backref + '</p>');
      } else {
        body += '<p>' + backref + '</p>';
      }
      return '<li id="fn-' + number + '">' + body + '</li>';
    });

    return html +
      '\n<section class="footnotes" data-footnotes>\n' +
      '<h2 class="sr-only" id="footnote-label">Footnotes</h2>\n<ol>\n' +
      items.join('\n') +
      '\n</ol>\n</section>\n';
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
    md.use(emojiPlugin);
    md.use(anchorPlugin);
    md.use(footnotePlugin);
    md.use(mentionPlugin);
    md.use(issueRefPlugin);
    md.use(commitShaPlugin);
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
    var md = createMarkdownIt();
    // 仓库上下文随 env 下发（issue 引用/sha 插件在 inline token 层就必须知道 owner/repo）
    var env = { repoContext: opts.repoContext };
    var html = appendFootnotes(md, md.render(raw, env), env);
    // 懒加载属性在 URL 改写前注入：两者都只动属性，互不依赖；产物由 Node 回归断言
    return rewriteRelativeUrls(addImageLoadingAttributes(html), opts.repoContext);
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
    emojiPlugin: emojiPlugin,
    anchorPlugin: anchorPlugin,
    footnotePlugin: footnotePlugin,
    mentionPlugin: mentionPlugin,
    mentionSegments: mentionSegments,
    issueRefPlugin: issueRefPlugin,
    commitShaPlugin: commitShaPlugin,
    issueRefSegments: issueRefSegments,
    commitShaSegments: commitShaSegments,
    renderOfflineHtml: renderOfflineHtml,
    rewriteRelativeUrls: rewriteRelativeUrls,
    parseRepoContext: parseRepoContext,
    scrollToAnchor: scrollToAnchor,
    highlightCodeBlocks: highlightCodeBlocks,
    renderMath: renderMath,
    renderMermaid: renderMermaid,
    detectMermaidEngine: detectMermaidEngine,
    mermaidSourceOf: mermaidSourceOf,
    collectMermaidPreElements: collectMermaidPreElements,
    mermaidThemeVariables: mermaidThemeVariables,
    mermaidLimits: { maxDiagrams: MERMAID_MAX_DIAGRAMS },
    scanMathText: scanMathText,
    mathLimits: {
      maxFormulas: MATH_MAX_FORMULAS,
      maxFormulaChars: MATH_MAX_FORMULA_CHARS,
      maxTotalChars: MATH_MAX_TOTAL_CHARS,
    },
    addImageLoadingAttributes: addImageLoadingAttributes,
    decorateImages: decorateImages,
    highlightLimits: { maxBlocks: HIGHLIGHT_MAX_BLOCKS, maxTotalChars: HIGHLIGHT_MAX_TOTAL_CHARS },
  };

  function init() {
    var root = document.querySelector('.markdown-body');
    if (!root) return;

    // 离线模式优先渲染 markdown
    var offline = !!document.getElementById('markdown-raw');
    if (offline) {
      root = renderOfflineMarkdown() || root;
    }

    // 权威清洗
    sanitizeNode(root);

    // 数学公式（KaTeX）：必须在 DOMPurify 清洗之后（方案 B）——清洗会剥掉 KaTeX 排版
    // 所需的全部 style 属性；katex.min.js 未注入时（Kotlin 侧未检测到数学）本调用 no-op。
    renderMath(root);

    // Mermaid 图：同样在清洗之后（方案 B）；引擎不可用（Chromium < 94 / 脚本未注入）或
    // 未检测到图定义时 no-op，代码块原样保留。异步渲染，完成后自动回退失败项。
    renderMermaid(root);

    // 图片懒加载/异步解码：服务端 HTML 主通道在清洗后补属性；离线产物已在
    // renderOfflineHtml 里写好，这里兜底一次清洗可能剥掉属性的情况（hasAttribute 不覆盖已有值）
    decorateImages(root);

    // 绑定白名单事件
    bindLinks(root);
    bindImages(root);
    bindCheckboxes(root);
    bindCodeCopy(root);
    observeHeight(root);

    // 服务端 HTML 主通道的代码块高亮（离线通道已在 renderOfflineMarkdown 内完成）。
    // 双预算护栏见 highlightCodeBlocks：GitHub 产物可能含数百个代码块。
    if (!offline) {
      highlightCodeBlocks(root);
    }
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }
})();
