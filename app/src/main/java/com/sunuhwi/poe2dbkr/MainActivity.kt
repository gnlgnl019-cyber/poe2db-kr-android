package com.sunuhwi.poe2dbkr

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.SystemBarStyle
import androidx.activity.addCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.sunuhwi.poe2dbkr.databinding.ActivityMainBinding
import java.text.NumberFormat
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: CatalogAdapter
    private lateinit var bookmarks: BookmarkStore

    private val handler = Handler(Looper.getMainLooper())
    private val filterTick = Runnable { applyFilter() }

    private var catalog: List<CatalogItem> = emptyList()
    private var loaded = false
    private var activeCategory: String? = null
    private var currentItem: CatalogItem? = null
    private var pendingSearch: String? = null
    private var exitArmed = false

    private val activeTab: Int
        get() = binding.bottomNav.selectedItemId

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyWindowInsets()
        bookmarks = BookmarkStore(this)

        setupList()
        setupHeader()
        setupWeb()
        setupDetail()
        setupTabs()
        setupBack()
        restoreState(savedInstanceState)

        Catalog.load(this) { loadedItems ->
            catalog = loadedItems
            loaded = true
            applyFilter()
        }
    }

    private fun applyWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            view.setPadding(bars.left, bars.top, bars.right, maxOf(bars.bottom, ime.bottom))
            insets
        }
    }

    private fun setupList() {
        adapter = CatalogAdapter(
            onOpen = { item -> openItem(item) },
            onStar = { item -> toggleBookmark(item) },
        )
        binding.rvList.layoutManager = LinearLayoutManager(this)
        binding.rvList.adapter = adapter
        binding.rvList.setHasFixedSize(false)
    }

    private fun setupHeader() {
        binding.btnSite.setOnClickListener { openSite(HOME_URL) }
        binding.btnInfo.setOnClickListener { showInfo() }

        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit

            override fun afterTextChanged(s: Editable?) {
                binding.btnClear.isVisible = !s.isNullOrEmpty()
                if (binding.detailPane.isVisible) closeDetail()
                handler.removeCallbacks(filterTick)
                handler.postDelayed(filterTick, 120)
            }
        })

        binding.etSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                hideKeyboard()
                handler.removeCallbacks(filterTick)
                applyFilter()
                true
            } else {
                false
            }
        }

        binding.btnClear.setOnClickListener {
            binding.etSearch.setText("")
            applyFilter()
        }

        binding.btnWebSearch.setOnClickListener {
            val query = currentQuery()
            if (query.isEmpty()) {
                openSite(HOME_URL)
            } else {
                pendingSearch = query
                openSite(HOME_URL)
            }
        }
    }

    private fun setupTabs() {
        buildChips()
        binding.bottomNav.setOnItemSelectedListener { item ->
            activeCategory = null
            binding.chipGroup.check(View.NO_ID)
            binding.chipGroup.check(binding.chipGroup.getChildAt(0).id)
            binding.chipScroll.isVisible = item.itemId == R.id.nav_home
            closeDetail()
            applyFilter()
            true
        }
        binding.bottomNav.setOnItemReselectedListener {
            closeDetail()
        }
        binding.bottomNav.selectedItemId = R.id.nav_home
    }

    private fun buildChips() {
        val group = binding.chipGroup
        group.removeAllViews()
        addChip(getString(R.string.chip_all), null, true)
        Catalog.categories.forEach { category ->
            addChip(getString(category.label), category.id, false)
        }
    }

    private fun addChip(label: String, categoryId: String?, checked: Boolean) {
        val chip = Chip(this)
        chip.id = View.generateViewId()
        chip.text = label
        chip.isCheckable = true
        chip.isChecked = checked
        chip.setOnClickListener { view ->
            val selected = (view as Chip).isChecked
            activeCategory = if (selected) categoryId else null
            if (selected) binding.chipGroup.check(view.id)
            applyFilter()
        }
        binding.chipGroup.addView(chip)
    }

    private fun applyFilter() {
        val query = currentQuery()
        val pool = when (activeTab) {
            R.id.nav_skills -> catalog.filter { it.cat == "skill_gem" || it.cat == "support_gem" }
            R.id.nav_uniques -> catalog.filter { it.cat == "unique" }
            R.id.nav_others -> catalog.filter {
                it.cat == "currency" || it.cat == "keyword" || it.cat == "ascendancy"
            }
            R.id.nav_favorites -> bookmarks.all()
            else -> catalog
        }
        val scoped = activeCategory?.let { category -> pool.filter { it.cat == category } } ?: pool
        val results = if (query.isEmpty()) {
            scoped
        } else {
            val needle = query.lowercase()
            scoped.filter { it.searchText.contains(needle) }
        }

        adapter.submit(results, bookmarks.urls())
        binding.tvStatus.text = if (loaded) statusText(query, results.size) else getString(R.string.status_loading)
        binding.emptyView.isVisible = loaded && results.isEmpty()
        binding.rvList.isVisible = results.isNotEmpty()
        updateEmptyState(query)
    }

    private fun updateEmptyState(query: String) {
        val favoritesEmpty = activeTab == R.id.nav_favorites && query.isEmpty()
        binding.tvEmptyTitle.setText(
            if (favoritesEmpty) R.string.favorites_empty_title else R.string.empty_title
        )
        binding.tvEmptyHint.setText(
            if (favoritesEmpty) R.string.favorites_empty_hint else R.string.empty_hint
        )
        binding.btnWebSearch.isVisible = !favoritesEmpty
    }

    private fun statusText(query: String, count: Int): String {
        val formatted = NumberFormat.getNumberInstance(Locale.KOREA).format(count)
        return when {
            query.isNotEmpty() -> getString(R.string.status_search, query, formatted)
            activeTab == R.id.nav_favorites -> getString(R.string.status_favorites, formatted)
            else -> getString(
                R.string.status_count,
                formatted,
                Catalog.fetchedAt.ifEmpty { getString(R.string.data_source) }
            )
        }
    }

    private fun currentQuery(): String = binding.etSearch.text?.toString()?.trim().orEmpty()

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWeb() {
        with(binding.webView.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            loadWithOverviewMode = false
            useWideViewPort = false
            builtInZoomControls = false
            displayZoomControls = false
            setSupportZoom(false)
            textZoom = 100
            cacheMode = WebSettings.LOAD_DEFAULT
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            allowFileAccess = false
            allowContentAccess = false
            userAgentString = "$userAgentString PoE2DBKR/1.0"
        }

        binding.webView.webViewClient = SiteClient()
        binding.webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                binding.detailProgress.progress = newProgress
                binding.detailProgress.isVisible =
                    binding.detailPane.isVisible && newProgress < 100
                if (newProgress >= 100) binding.detailSwipe.isRefreshing = false
            }

            override fun onCreateWindow(
                view: WebView,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: Message,
            ): Boolean {
                val transport = resultMsg.obj as? WebView.WebViewTransport ?: return false
                val opener = WebView(this@MainActivity)
                opener.webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        view: WebView,
                        request: WebResourceRequest,
                    ): Boolean {
                        openExternal(request.url)
                        view.destroy()
                        return true
                    }
                }
                transport.webView = opener
                resultMsg.sendToTarget()
                return true
            }
        }
        binding.webView.setDownloadListener { url, _, _, _, _ ->
            openExternal(Uri.parse(url))
        }
    }

    private fun setupDetail() {
        binding.detailSwipe.setColorSchemeColors(ContextCompat.getColor(this, R.color.gold))
        binding.detailSwipe.setProgressBackgroundColorSchemeColor(
            ContextCompat.getColor(this, R.color.surface_high)
        )
        binding.detailSwipe.setOnRefreshListener { binding.webView.reload() }
        binding.btnDetailBack.setOnClickListener { closeDetail() }
        binding.btnDetailRefresh.setOnClickListener { binding.webView.reload() }
        binding.btnDetailOpen.setOnClickListener {
            val url = binding.webView.url
            if (!url.isNullOrBlank()) openExternal(Uri.parse(url))
        }
        binding.btnDetailStar.setOnClickListener {
            currentItem?.let { item ->
                toggleBookmark(item)
                renderDetailStar()
            }
        }
    }

    private fun setupBack() {
        onBackPressedDispatcher.addCallback(this) {
            when {
                binding.detailPane.isVisible && binding.webView.canGoBack() -> binding.webView.goBack()
                binding.detailPane.isVisible -> closeDetail()
                currentQuery().isNotEmpty() -> binding.etSearch.setText("")
                activeTab != R.id.nav_home -> binding.bottomNav.selectedItemId = R.id.nav_home
                exitArmed -> finish()
                else -> {
                    exitArmed = true
                    Snackbar.make(binding.root, R.string.exit_hint, Snackbar.LENGTH_SHORT).show()
                    handler.postDelayed({ exitArmed = false }, 2000)
                }
            }
        }
    }

    private fun openItem(item: CatalogItem) {
        currentItem = item
        binding.tvDetailTitle.text = item.name
        binding.btnDetailStar.isVisible = true
        renderDetailStar()
        showDetailPane()
        binding.webView.loadUrl(item.url)
        hideKeyboard()
    }

    private fun openSite(url: String) {
        currentItem = null
        binding.tvDetailTitle.text = getString(R.string.site_title)
        binding.btnDetailStar.isVisible = false
        showDetailPane()
        binding.webView.loadUrl(url)
        hideKeyboard()
    }

    private fun showDetailPane() {
        binding.detailProgress.isVisible = true
        binding.listPane.isVisible = false
        binding.detailPane.isVisible = true
    }

    private fun closeDetail() {
        if (!binding.detailPane.isVisible) return
        binding.detailPane.isVisible = false
        binding.listPane.isVisible = true
        binding.detailProgress.isVisible = false
        binding.webView.stopLoading()
        binding.webView.loadUrl("about:blank")
        currentItem = null
        pendingSearch = null
    }

    private fun renderDetailStar() {
        val item = currentItem ?: return
        binding.btnDetailStar.setImageResource(
            if (bookmarks.contains(item.url)) R.drawable.ic_star else R.drawable.ic_star_border
        )
    }

    private fun toggleBookmark(item: CatalogItem) {
        val saved = bookmarks.toggle(item)
        adapter.updateStar(item.url, saved)
        if (activeTab == R.id.nav_favorites) applyFilter()
        Snackbar.make(
            binding.root,
            if (saved) R.string.bookmark_added else R.string.bookmark_removed,
            Snackbar.LENGTH_SHORT
        ).show()
    }

    private fun showInfo() {
        val version = runCatching {
            packageManager.getPackageInfo(packageName, 0).versionName
        }.getOrNull().orEmpty()
        val count = NumberFormat.getNumberInstance(Locale.KOREA).format(Catalog.total)
        val message = buildString {
            append(getString(R.string.info_version, version))
            append('\n')
            append(getString(R.string.info_data, count, Catalog.fetchedAt.ifEmpty { "-" }))
            append("\n\n")
            append(getString(R.string.info_body))
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.info_title)
            .setMessage(message)
            .setPositiveButton(R.string.info_close, null)
            .show()
    }

    private fun hideKeyboard() {
        val manager = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        manager.hideSoftInputFromWindow(binding.etSearch.windowToken, 0)
        binding.etSearch.clearFocus()
    }

    private fun openExternal(uri: Uri) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (e: ActivityNotFoundException) {
            Snackbar.make(binding.root, R.string.link_error, Snackbar.LENGTH_SHORT).show()
        }
    }

    private fun restoreState(state: Bundle?) {
        if (state == null) return
        val tab = state.getInt(KEY_TAB, R.id.nav_home)
        binding.bottomNav.selectedItemId = tab
        binding.etSearch.setText(state.getString(KEY_QUERY, ""))
        val url = state.getString(KEY_DETAIL_URL)
        if (!url.isNullOrBlank() && url.startsWith("http")) {
            currentItem = null
            binding.tvDetailTitle.text = state.getString(KEY_DETAIL_TITLE, getString(R.string.site_title))
            binding.btnDetailStar.isVisible = false
            showDetailPane()
            binding.webView.loadUrl(url)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_TAB, activeTab)
        outState.putString(KEY_QUERY, currentQuery())
        if (binding.detailPane.isVisible) {
            outState.putString(KEY_DETAIL_URL, binding.webView.url)
            outState.putString(KEY_DETAIL_TITLE, binding.tvDetailTitle.text?.toString())
        }
    }

    override fun onResume() {
        super.onResume()
        binding.webView.onResume()
    }

    override fun onPause() {
        binding.webView.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        handler.removeCallbacks(filterTick)
        binding.webView.destroy()
        super.onDestroy()
    }

    private inner class SiteClient : WebViewClient() {

        override fun shouldOverrideUrlLoading(
            view: WebView,
            request: WebResourceRequest,
        ): Boolean {
            val uri = request.url
            val host = uri.host.orEmpty()
            if (host == "poe2db.tw" || host.endsWith(".poe2db.tw")) return false
            openExternal(uri)
            return true
        }

        override fun onPageFinished(view: WebView, url: String) {
            binding.detailProgress.isVisible = false
            binding.detailSwipe.isRefreshing = false
            if (!url.startsWith("http")) return
            view.evaluateJavascript(MobileCss.SCRIPT, null)

            val query = pendingSearch
            if (query != null && url.endsWith("/kr/")) {
                pendingSearch = null
                view.evaluateJavascript(MobileCss.searchScript(query), null)
            }
        }

        override fun onReceivedError(
            view: WebView,
            request: WebResourceRequest,
            error: WebResourceError,
        ) {
            if (!request.isForMainFrame) return
            binding.detailProgress.isVisible = false
            binding.detailSwipe.isRefreshing = false
            Snackbar.make(binding.root, R.string.page_error, Snackbar.LENGTH_LONG)
                .setAction(R.string.retry) { view.reload() }
                .show()
        }
    }

    private companion object {
        const val HOME_URL = "https://poe2db.tw/kr/"
        const val KEY_TAB = "tab"
        const val KEY_QUERY = "query"
        const val KEY_DETAIL_URL = "detailUrl"
        const val KEY_DETAIL_TITLE = "detailTitle"
    }
}
