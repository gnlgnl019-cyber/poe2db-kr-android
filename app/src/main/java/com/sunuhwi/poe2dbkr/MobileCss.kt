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
            overflow: hidden !important; }
        header, nav, .navbar, .navbar-fixed-top, #topbanner970, #middlebanner, #bottom_banner,
        #leftside_banner, #rightside_banner, #consent-box, footer, .background-image,
        .sidebar, .advertisement, iframe[src*="banner"] { display: none !important; }
        #passive_skill_tree { height: 100vh !important; width: 100vw !important; }
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
              var tree = document.getElementById('passive_skill_tree');
              if (tree && tree.parentNode && tree.parentNode.tagName === 'BODY') {
                tree.style.height = (window.innerHeight || 600) + 'px';
              }
            })();
        """
}
