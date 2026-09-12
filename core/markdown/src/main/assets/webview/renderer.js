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
 *    补 GitHub Alert / 任务列表 / emoji 短码 / 脚注 / 标题锚点 / @user 提及六个最小 GFM 插件，
 *    并用 highlight.js 高亮代码块；渲染产物再按仓库上下文改写相对链接/图片
 *    （见 renderOfflineHtml 的说明）
 * 4. 服务端 HTML 主通道（SERVER_HTML）同样用 highlight.js 高亮代码块（双预算护栏，
 *    见 highlightCodeBlocks）；图片统一补 loading="lazy" / decoding="async"
 *    （离线产物在 renderOfflineHtml 内联注入，服务端 HTML 由 decorateImages 在清洗后补）
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

  // ── 离线 GFM 补齐：@user 提及（2026-09-12） ─────────────────────────────
  //
  // 只做**用户**提及：`@org/team` 没有应用内路由（GitHubLinkParser 明确把 `@org/team`
  // 归为 External），因此整段保持纯文本——绝不允许把 `@org` 从 `@org/team` 里切出来
  // 半截链接（负向前瞻 `(?![A-Za-z0-9\-/])` 保证）。
  //
  // 作用面与 emojiPlugin 同构：只改写 inline token 的 text 子节点。行内代码是 code_inline
  // token、围栏是块级 token，结构上不进 text 子节点；已有链接（link_open…link_close）
  // 之间整段跳过，避免 <a> 嵌套。邮箱（`octocat@github.com`）因 @ 前是词字符而不匹配。

  /** 提及词法：GitHub 用户名为 1–39 位字母/数字/连字符，首尾不得是连字符。 */
  var MENTION_SOURCE = '(^|[^\\w./+@-])@([A-Za-z0-9](?:[A-Za-z0-9-]{0,37}[A-Za-z0-9])?)(?![A-Za-z0-9\\-/])';
  var GITHUB_PROFILE_BASE = 'https://github.com/';

  /**
   * 把一段 text 切成「纯文本 / 提及」片段；没有提及返回 null（调用方保持原 token）。
   *
   * 每次调用新建 RegExp：避免共享 `g` 正则的 lastIndex 状态串味。
   *
   * @returns {Array<{text: string}|{mention: string, user: string}>|null}
   */
  function mentionSegments(content) {
    var pattern = new RegExp(MENTION_SOURCE, 'g');
    var segments = [];
    var cursor = 0;
    var found = false;
    var match;
    while ((match = pattern.exec(content)) !== null) {
      found = true;
      // 前缀（空白/标点）并入前一段纯文本，不能丢
      var before = content.slice(cursor, match.index) + match[1];
      if (before) segments.push({ text: before });
      segments.push({ mention: '@' + match[2], user: match[2] });
      cursor = match.index + match[0].length;
    }
    if (!found) return null;
    if (cursor < content.length) segments.push({ text: content.slice(cursor) });
    return segments;
  }

  /** inline children 就地改写：提及 → link_open/text/link_close 三连 token。 */
  function linkifyMentionTokens(state, children) {
    var result = [];
    var linkDepth = 0;
    for (var i = 0; i < children.length; i++) {
      var token = children[i];
      if (token.type === 'link_open') linkDepth++;
      if (token.type === 'link_close') linkDepth--;
      if (token.type !== 'text' || linkDepth > 0) {
        result.push(token);
        continue;
      }
      var segments = mentionSegments(token.content);
      if (!segments) {
        result.push(token);
        continue;
      }
      for (var s = 0; s < segments.length; s++) {
        if (segments[s].user) {
          var open = newToken(state, 'link_open', 'a', 1);
          open.attrSet('href', GITHUB_PROFILE_BASE + segments[s].user);
          var label = newToken(state, 'text', '', 0);
          label.content = segments[s].mention;
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
   * 运行时机与 emojiPlugin 一致（inline 之后的 core 规则），且只动 text token。
   */
  function mentionPlugin(md) {
    md.core.ruler.after('inline', 'mention_links', function (state) {
      var tokens = state.tokens;
      for (var i = 0; i < tokens.length; i++) {
        var inline = tokens[i];
        if (inline.type !== 'inline' || !inline.children) continue;
        inline.children = linkifyMentionTokens(state, inline.children);
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
    var env = {};
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
    renderOfflineHtml: renderOfflineHtml,
    rewriteRelativeUrls: rewriteRelativeUrls,
    parseRepoContext: parseRepoContext,
    scrollToAnchor: scrollToAnchor,
    highlightCodeBlocks: highlightCodeBlocks,
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
