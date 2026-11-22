package com.sunuhwi.poe2dbkr

import org.json.JSONObject

internal object MobileCss {

    const val CSS = """
        html, body { background: #111216 !important; }
        body { padding-top: 0 !important; font-size: 15px !important; -webkit-text-size-adjust: 100%; }
        header, nav.navbar, .navbar-collapse, #consent-box, .background-image,
        #leftside_banner, #rightside_banner, #topbanner970, #middlebanner, #bottom_banner,
        .itemboximage { display: none !important; }
        .page-content, .page.poe2, .container {
            width: 100% !important; max-width: 100% !important;
            padding: 0 10px 24px !important; margin: 0 !important;
        }
        #head_toolbar { margin: 8px 0 !important; border-radius: 10px; background: #171a21 !important; }
        #head_toolbar .card-body { padding: 4px 6px !important; }
        #head_toolbar img { width: 26px !important; height: 26px !important; }
        .card { background: #171a21 !important; border: 1px solid #262b35 !important; border-radius: 12px; color: #e7e3db; }
        .card-header { background: transparent !important; border-bottom: 1px solid #262b35 !important; font-size: 15px !important; }
        .nav-tabs { flex-wrap: nowrap !important; overflow-x: auto; gap: 6px; }
        .nav-tabs .nav-link { white-space: nowrap; font-size: 13px; padding: 6px 10px; }
        .card-body, .tab-pane, .page-content { overflow-x: auto; -webkit-overflow-scrolling: touch; }
        table { font-size: 13px !important; max-width: 100% !important; }
        .table > :not(caption) > * > * { padding: 6px 8px; }
        img { max-width: 100%; height: auto; }
        .tooltip, .popover, .item-popup, .newItemPopup, .ui-tooltip,
        [class*="hover"] { max-width: 96vw !important; }
        .tooltip img, .popover img, .newItemPopup img, .item-popup img,
        .ui-tooltip img { max-width: 92vw !important; height: auto !important; }
        a, button, .nav-link, .dropdown-item { min-height: 34px; }
        .explicitMod, .implicitMod { font-size: 13px !important; line-height: 1.5; }
        .row-cols-lg-2 > * { width: 100% !important; }
        footer { margin-top: 16px !important; }
        footer .card { padding: 12px !important; }
        footer h5 { font-size: 13px !important; }
    """

    val SCRIPT: String
        get() = """
            (function () {
              if (!document.getElementById('poe2db-kr-style')) {
                var style = document.createElement('style');
                style.id = 'poe2db-kr-style';
                style.textContent = ${JSONObject.quote(CSS)};
                (document.head || document.documentElement).appendChild(style);
              }
              var width = document.documentElement.clientWidth || 360;
              var tables = document.querySelectorAll('table');
              for (var i = 0; i < tables.length; i++) {
                var table = tables[i];
                if (table.closest('.poe2db-kr-scroll') || table.classList.contains('filters')) continue;
                if (table.scrollWidth <= width + 8) continue;
                var wrap = document.createElement('div');
                wrap.className = 'poe2db-kr-scroll';
                wrap.style.cssText = 'overflow-x:auto;-webkit-overflow-scrolling:touch;width:100%';
                table.parentNode.insertBefore(wrap, table);
                wrap.appendChild(table);
              }
              if (!window.__poe2dbkrBlank) {
                window.__poe2dbkrBlank = true;
                document.addEventListener('click', function (event) {
                  var link = event.target && event.target.closest ? event.target.closest('a[target=_blank]') : null;
                  if (link) link.removeAttribute('target');
                }, true);
              }
            })();
        """

    fun searchScript(query: String): String = """
        (function () {
          var q = ${JSONObject.quote(query)};
          var form = document.querySelector('form[role=search]');
          var token = form && form.querySelector('input[name=token]');
          location.href = '/kr/search?q=' + encodeURIComponent(q) +
            (token ? '&token=' + encodeURIComponent(token.value) : '');
        })();
    """

    const val TREE_CSS = """
        html, body { background: #111216 !important; margin: 0 !important; padding: 0 !important;
            overflow: hidden !important; width: 100% !important; height: 100% !important; }
        header, nav, .navbar, .navbar-fixed-top, #topbanner970, #middlebanner, #bottom_banner,
        #leftside_banner, #rightside_banner, #consent-box, footer, .background-image,
        .sidebar, .advertisement, iframe[src*="banner"] { display: none !important; }
        /* 오른쪽 고정 패널이 화면을 거의 다 덮어 노드를 누를 수 없게 만든다. 트리만 남긴다. */
        #funcPanel, #statsPanel, #panelContent { display: none !important; }
        #passive_skill_tree { height: 100vh !important; width: 100vw !important;
            touch-action: none; display: block; }
        .custom-popover { --bs-popover-max-width: 88vw !important; }
        .popover { max-width: 90vw !important; }
    """

    /** poe.ninja 빌드 페이지를 모바일 폭에 맞추는 보정 스크립트 */
    val NINJA_SCRIPT: String
        get() = """
            (function () {
              var meta = document.querySelector('meta[name=viewport]');
              if (!meta) {
                meta = document.createElement('meta');
                meta.name = 'viewport';
                (document.head || document.documentElement).appendChild(meta);
              }
              meta.setAttribute('content', 'width=device-width, initial-scale=1, maximum-scale=5, user-scalable=yes');
              var style = document.getElementById('poe2dbkr-ninja');
              if (!style) {
                style = document.createElement('style');
                style.id = 'poe2dbkr-ninja';
                (document.head || document.documentElement).appendChild(style);
              }
              style.textContent = 'html,body{-webkit-text-size-adjust:100%!important;}' +
                '.container,.container-fluid,.mx-auto{max-width:100%!important;padding-left:8px!important;padding-right:8px!important;}' +
                'table{max-width:100%!important;}' +
                'img{max-width:100%!important;height:auto!important;}'
            })();
        """

    val TREE_SCRIPT: String
        get() = """
            (function () {
              if (!document.getElementById('poe2db-kr-tree-style')) {
                var style = document.createElement('style');
                style.id = 'poe2db-kr-tree-style';
                style.textContent = ${JSONObject.quote(TREE_CSS)};
                (document.head || document.documentElement).appendChild(style);
              }
              var meta = document.querySelector('meta[name=viewport]');
              if (!meta) {
                meta = document.createElement('meta');
                meta.name = 'viewport';
                (document.head || document.documentElement).appendChild(meta);
              }
              meta.setAttribute('content', 'width=device-width, initial-scale=1, maximum-scale=12, user-scalable=yes');

              function treeSvg() { return document.getElementById('passive_skill_tree'); }
              function zoomRoot() { return treeSvg() || document.getElementsByTagName('svg')[0]; }

              window.__poe2dbkrTree = {
                zoom: function (dir) {
                  var root = zoomRoot();
                  if (!root) return false;
                  var deltaY = dir > 0 ? -100 : 100;
                  for (var i = 0; i < 6; i++) {
                    root.dispatchEvent(new WheelEvent('wheel', {
                      deltaY: deltaY, deltaMode: 0, bubbles: true, cancelable: true, view: window
                    }));
                  }
                  return true;
                },
                reset: function () {
                  var root = zoomRoot();
                  var svg = treeSvg();
                  if (!root || !svg) return false;
                  var base = window.__poe2dbkrTree.baseWidth;
                  if (!base) return false;
                  for (var i = 0; i < 30; i++) {
                    var w = svg.viewBox.baseVal.width;
                    if (w >= base * 0.98) break;
                    root.dispatchEvent(new WheelEvent('wheel', {
                      deltaY: 100, deltaMode: 0, bubbles: true, cancelable: true, view: window
                    }));
                  }
                  return true;
                },
                viewBox: function () {
                  var svg = treeSvg();
                  return svg ? svg.getAttribute('viewBox') : '';
                }
              };

              var firstSvg = treeSvg();
              if (firstSvg && firstSvg.viewBox && firstSvg.viewBox.baseVal &&
                  !window.__poe2dbkrTree.baseWidth) {
                window.__poe2dbkrTree.baseWidth = firstSvg.viewBox.baseVal.width;
              }

              var svg = treeSvg();
              if (svg && svg.parentNode && svg.parentNode.tagName === 'BODY') {
                svg.style.height = (window.innerHeight || 600) + 'px';
              }

              /* 모바일에서는 터치가 click으로 이어지지 않는 경우가 있어, 짧은 탭을 노드 클릭으로 직접 전달한다. */
              if (!window.__poe2dbkrTap) {
                var root = zoomRoot();
                if (root) {
                  window.__poe2dbkrTap = { down: null, swallow: 0 };
                  root.addEventListener('pointerdown', function (e) {
                    if (e.isPrimary === false) return;
                    window.__poe2dbkrTap.down = { x: e.clientX, y: e.clientY, t: Date.now() };
                  }, true);
                  root.addEventListener('pointerup', function (e) {
                    var d = window.__poe2dbkrTap.down;
                    window.__poe2dbkrTap.down = null;
                    if (!d) return;
                    var moved = Math.sqrt(Math.pow(e.clientX - d.x, 2) + Math.pow(e.clientY - d.y, 2));
                    if (moved > 14 || Date.now() - d.t > 700) return;
                    var target = document.elementFromPoint(e.clientX, e.clientY);
                    var node = target && target.closest ? target.closest('circle') : null;
                    if (!node) return;
                    var tap = new MouseEvent('click', {
                      bubbles: true, cancelable: true, view: window, detail: 1
                    });
                    tap.__poe2dbkrTap = true;
                    window.__poe2dbkrTap.swallow = Date.now() + 600;
                    node.dispatchEvent(tap);
                  }, true);
                  document.addEventListener('click', function (e) {
                    if (e.__poe2dbkrTap) return;
                    var until = window.__poe2dbkrTap.swallow;
                    if (until && Date.now() < until) {
                      window.__poe2dbkrTap.swallow = 0;
                      var target = e.target;
                      if (target && target.closest && target.closest('circle')) {
                        e.stopPropagation();
                        e.preventDefault();
                      }
                    }
                  }, true);
                }
              }
            })();
        """
}
