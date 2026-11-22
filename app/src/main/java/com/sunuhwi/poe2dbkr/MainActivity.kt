package com.sunuhwi.poe2dbkr

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
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
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.LinearLayout
import android.widget.TextView
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
import com.google.android.material.tabs.TabLayout
import com.sunuhwi.poe2dbkr.databinding.ActivityMainBinding
import com.sunuhwi.poe2dbkr.databinding.DialogPobSlotBinding
import com.sunuhwi.poe2dbkr.databinding.ItemPobGemBinding
import com.sunuhwi.poe2dbkr.databinding.ItemPobSlotBinding
import com.sunuhwi.poe2dbkr.databinding.PaneDetailBinding
import com.sunuhwi.poe2dbkr.databinding.PaneListBinding
import com.sunuhwi.poe2dbkr.databinding.PaneMarketBinding
import com.sunuhwi.poe2dbkr.databinding.PanePobBinding
import com.sunuhwi.poe2dbkr.databinding.PaneTreeBinding
import java.text.NumberFormat
import java.util.Locale
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var listPane: PaneListBinding
    private lateinit var detailPane: PaneDetailBinding
    private lateinit var marketPane: PaneMarketBinding
    private lateinit var treePane: PaneTreeBinding
    private lateinit var pobPane: PanePobBinding

    private lateinit var adapter: CatalogAdapter
    private lateinit var marketAdapter: MarketAdapter
    private lateinit var slotAdapter: PobSlotAdapter
    private lateinit var bookmarks: BookmarkStore
    private lateinit var pobStore: PobStore

    private val network = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())
    private val filterTick = Runnable { applyFilter() }
    private val marketTick = Runnable { applyMarketFilter() }

    private var catalog: List<CatalogItem> = emptyList()
    private var uniques: List<CatalogItem> = emptyList()
    private var loaded = false
    private var activeCategory: String? = null
    private var currentItem: CatalogItem? = null
    private var pendingSearch: String? = null
    private var exitArmed = false

    private var leagues: List<MarketLeague> = emptyList()
    private var activeLeague: String? = null
    private var activeType = "Currency"
    private var marketRates: List<MarketRate> = emptyList()

    private var pobBuild: PobBuild? = null
    private val pobOverrides = HashMap<String, PobItem>()
    private val pobFlasks = mutableSetOf<String>()
    private val pobConfig = PobCalculator.Config()
    private var pobMainGroup = 1

    private lateinit var battleView: BattleCanvasView
    private var battleFxOverride: SkillEffectType? = null
    private var battleMode = BattleMode.MAP
    private var battleMobCount = 25
    private var battleAmp = false
    private var battleBoss: BossPreset = BossPresets.ALL[0]
    private var battleWorld: BattleWorld? = null
    private var battleStarted = false
    private var battleWantsPlay = false
    private var battleSpeed = 1f

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
        pobStore = PobStore(this)

        listPane = PaneListBinding.inflate(layoutInflater, binding.paneList, true)
        detailPane = PaneDetailBinding.inflate(layoutInflater, binding.paneDetail, true)
        marketPane = PaneMarketBinding.inflate(layoutInflater, binding.paneMarket, true)
        treePane = PaneTreeBinding.inflate(layoutInflater, binding.paneTree, true)
        pobPane = PanePobBinding.inflate(layoutInflater, binding.panePob, true)

        setupList()
        setupHeader()
        setupWeb()
        setupMarket()
        setupTree()
        setupPob()
        setupTabs()
        setupBack()
        restoreState(savedInstanceState)

        Catalog.load(this) { loadedItems ->
            catalog = loadedItems
            uniques = loadedItems.filter { it.cat == "unique" }
            loaded = true
            applyFilter()
            refreshPobCalc()
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

    // ------------------------------------------------------------- DB 목록
    private fun setupList() {
        adapter = CatalogAdapter(
            onOpen = { item -> openItem(item) },
            onStar = { item -> toggleBookmark(item) },
        )
        listPane.rvList.layoutManager = LinearLayoutManager(this)
        listPane.rvList.adapter = adapter
        listPane.rvList.setHasFixedSize(false)
    }

    private fun setupHeader() {
        binding.btnSite.setOnClickListener { openSite(HOME_URL) }
        binding.btnInfo.setOnClickListener { showInfo() }

        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                binding.btnClear.isVisible = !s.isNullOrEmpty()
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
    }

    private fun setupTabs() {
        buildChips()
        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    activeCategory = null
                    listPane.chipGroup.check(listPane.chipGroup.getChildAt(0).id)
                    listPane.chipScroll.isVisible = true
                    showPane(PANE_LIST)
                    applyFilter()
                }
                R.id.nav_market -> {
                    showPane(PANE_MARKET)
                    ensureMarket()
                }
                R.id.nav_tree -> {
                    showPane(PANE_TREE)
                    if (treePane.treeWebView.url.isNullOrBlank()) loadTree(PASSIVE_TREE_URL)
                }
                R.id.nav_pob -> {
                    showPane(PANE_POB)
                    refreshPobCalc()
                }
                R.id.nav_favorites -> {
                    listPane.chipScroll.isVisible = false
                    listPane.moreBar.isVisible = true
                    showPane(PANE_LIST)
                    applyFilter()
                }
            }
            true
        }
        binding.bottomNav.setOnItemReselectedListener {
            if (it.itemId == R.id.nav_home || it.itemId == R.id.nav_favorites) closeDetail()
        }
        binding.bottomNav.selectedItemId = R.id.nav_home
    }

    private fun showPane(pane: Int) {
        closeDetail()
        listPane.moreBar.isVisible = false
        binding.searchRow.isVisible = pane == PANE_LIST
        binding.tvStatus.isVisible = pane == PANE_LIST
        binding.tvHeaderSub.text = when (pane) {
            PANE_MARKET -> getString(R.string.tab_market) + " · poe.ninja"
            PANE_TREE -> getString(R.string.tab_tree)
            PANE_POB -> "PoB 실험실"
            else -> getString(R.string.app_tagline)
        }
        binding.paneList.isVisible = pane == PANE_LIST
        binding.paneMarket.isVisible = pane == PANE_MARKET
        binding.paneTree.isVisible = pane == PANE_TREE
        binding.panePob.isVisible = pane == PANE_POB
        binding.paneDetail.isVisible = false
        if (pane == PANE_POB) {
            if (battleWantsPlay) battleView.resume()
        } else {
            battleView.pause()
        }
        headerTitleClick(pane)
    }

    private fun headerTitleClick(pane: Int) {
        binding.headerTitle.setOnClickListener(null)
    }

    private fun buildChips() {
        val group = listPane.chipGroup
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
            if (selected) listPane.chipGroup.check(view.id)
            applyFilter()
        }
        listPane.chipGroup.addView(chip)
    }

    private fun applyFilter() {
        val query = currentQuery()
        val pool = if (activeTab == R.id.nav_favorites) bookmarks.all() else catalog
        val scoped = activeCategory?.let { category -> pool.filter { it.cat == category } } ?: pool
        val results = if (query.isEmpty()) {
            scoped
        } else {
            val needle = query.lowercase()
            scoped.filter { it.searchText.contains(needle) }
        }

        adapter.submit(results, bookmarks.urls())
        binding.tvStatus.text = if (loaded) statusText(query, results.size) else getString(R.string.status_loading)
        listPane.emptyView.isVisible = loaded && results.isEmpty()
        listPane.rvList.isVisible = results.isNotEmpty()
        if (activeTab == R.id.nav_favorites && query.isEmpty()) {
            listPane.tvEmptyTitle.setText(R.string.favorites_empty_title)
            listPane.tvEmptyHint.setText(R.string.favorites_empty_hint)
            listPane.btnWebSearch.isVisible = false
        } else {
            listPane.tvEmptyTitle.setText(R.string.empty_title)
            listPane.tvEmptyHint.setText(R.string.empty_hint)
            listPane.btnWebSearch.isVisible = true
        }
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

    // ------------------------------------------------------------- 상세 웹뷰
    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWeb() {
        with(detailPane.webView.settings) {
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

        detailPane.webView.webViewClient = SiteClient()
        detailPane.webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                detailPane.detailProgress.progress = newProgress
                detailPane.detailProgress.isVisible =
                    binding.paneDetail.isVisible && newProgress < 100
                if (newProgress >= 100) detailPane.detailSwipe.isRefreshing = false
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
        detailPane.webView.setDownloadListener { url, _, _, _, _ ->
            openExternal(Uri.parse(url))
        }

        detailPane.detailSwipe.setColorSchemeColors(ContextCompat.getColor(this, R.color.gold))
        detailPane.detailSwipe.setProgressBackgroundColorSchemeColor(
            ContextCompat.getColor(this, R.color.surface_high)
        )
        detailPane.detailSwipe.setOnRefreshListener { detailPane.webView.reload() }
        detailPane.btnDetailBack.setOnClickListener { closeDetail() }
        detailPane.btnDetailRefresh.setOnClickListener { detailPane.webView.reload() }
        detailPane.btnDetailOpen.setOnClickListener {
            val url = detailPane.webView.url
            if (!url.isNullOrBlank()) openExternal(Uri.parse(url))
        }
        detailPane.btnDetailStar.setOnClickListener {
            currentItem?.let { item ->
                toggleBookmark(item)
                renderDetailStar()
            }
        }
    }

    private fun setupBack() {
        onBackPressedDispatcher.addCallback(this) {
            when {
                binding.paneDetail.isVisible && detailPane.webView.canGoBack() -> detailPane.webView.goBack()
                binding.paneDetail.isVisible -> closeDetail()
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
        detailPane.tvDetailTitle.text = item.name
        detailPane.btnDetailStar.isVisible = true
        renderDetailStar()
        showDetailPane()
        detailPane.webView.loadUrl(item.url)
        hideKeyboard()
    }

    private fun openSite(url: String) {
        currentItem = null
        detailPane.tvDetailTitle.text = getString(R.string.site_title)
        detailPane.btnDetailStar.isVisible = false
        showDetailPane()
        detailPane.webView.loadUrl(url)
        hideKeyboard()
    }

    private fun showDetailPane() {
        binding.paneDetail.isVisible = true
        binding.paneList.isVisible = false
        binding.paneMarket.isVisible = false
        binding.paneTree.isVisible = false
        binding.panePob.isVisible = false
        detailPane.detailProgress.isVisible = true
    }

    private fun closeDetail() {
        if (!binding.paneDetail.isVisible) return
        binding.paneDetail.isVisible = false
        when (activeTab) {
            R.id.nav_market -> binding.paneMarket.isVisible = true
            R.id.nav_tree -> binding.paneTree.isVisible = true
            R.id.nav_pob -> binding.panePob.isVisible = true
            else -> binding.paneList.isVisible = true
        }
        detailPane.detailProgress.isVisible = false
        detailPane.webView.stopLoading()
        detailPane.webView.loadUrl("about:blank")
        currentItem = null
        pendingSearch = null
    }

    private fun renderDetailStar() {
        val item = currentItem ?: return
        detailPane.btnDetailStar.setImageResource(
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

    // ------------------------------------------------------------- 실시간 시세
    private fun setupMarket() {
        marketAdapter = MarketAdapter()
        marketPane.rvMarket.layoutManager = LinearLayoutManager(this)
        marketPane.rvMarket.adapter = marketAdapter

        val types = marketPane.typeChipGroup
        var firstId = View.NO_ID
        Market.TYPES.forEach { (type, label) ->
            val chip = Chip(this)
            chip.id = View.generateViewId()
            chip.text = label
            chip.isCheckable = true
            chip.tag = type
            chip.setOnClickListener { view ->
                if ((view as Chip).isChecked) {
                    activeType = type
                    types.check(view.id)
                    loadMarketRates()
                }
            }
            types.addView(chip)
            if (firstId == View.NO_ID) firstId = chip.id
        }
        types.check(firstId)

        marketPane.etMarketSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                handler.removeCallbacks(marketTick)
                handler.postDelayed(marketTick, 200)
            }
        })

        marketPane.btnMarketRefresh.setOnClickListener {
            Market.clearCache()
            loadMarketRates()
        }

        listPane.btnAtlasTree.setOnClickListener { openTree(ATLAS_TREE_URL) }
        listPane.btnWebSearchHome.setOnClickListener {
            val query = currentQuery()
            if (query.isEmpty()) openSite(HOME_URL) else {
                pendingSearch = query
                openSite(HOME_URL)
            }
        }
        listPane.btnHomeSite.setOnClickListener { openSite(HOME_URL) }
    }

    private fun ensureMarket() {
        if (leagues.isNotEmpty()) return
        marketPane.marketProgress.isVisible = true
        network.execute {
            val fetched = runCatching { Market.leagues() }.getOrDefault(emptyList())
            handler.post {
                marketPane.marketProgress.isVisible = false
                if (fetched.isEmpty()) {
                    Snackbar.make(binding.root, R.string.market_league_error, Snackbar.LENGTH_LONG)
                        .setAction(R.string.retry) { ensureMarket() }
                        .show()
                    return@post
                }
                leagues = fetched
                val labels = fetched.map { it.name }
                marketPane.spinnerLeague.adapter = android.widget.ArrayAdapter(
                    this,
                    android.R.layout.simple_spinner_dropdown_item,
                    labels
                )
                marketPane.spinnerLeague.setSelection(0, false)
                marketPane.spinnerLeague.onItemSelectedListener =
                    object : android.widget.AdapterView.OnItemSelectedListener {
                        override fun onItemSelected(
                            parent: android.widget.AdapterView<*>?,
                            view: View?,
                            position: Int,
                            id: Long,
                        ) {
                            val league = leagues.getOrNull(position)?.id
                            if (league != null && league != activeLeague) {
                                activeLeague = league
                                loadMarketRates()
                            }
                        }

                        override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
                    }
                activeLeague = fetched.first().id
                loadMarketRates()
            }
        }
    }

    private fun loadMarketRates() {
        val league = activeLeague ?: return
        val type = activeType
        marketPane.marketProgress.isVisible = marketRates.isEmpty()
        network.execute {
            val rates = runCatching {
                Market.overview(league, type, koMapping())
            }.getOrElse { emptyList() }
            handler.post {
                marketPane.marketProgress.isVisible = false
                if (rates.isEmpty()) {
                    marketPane.tvMarketError.isVisible = true
                    marketPane.rvMarket.isVisible = false
                } else {
                    marketPane.tvMarketError.isVisible = false
                    marketPane.rvMarket.isVisible = true
                    marketRates = rates
                    applyMarketFilter()
                    marketPane.tvMarketStatus.text = getString(
                        R.string.market_status,
                        league,
                        Market.TYPES[type] ?: type,
                        rates.size.toString(),
                    )
                }
            }
        }
    }

    private fun koMapping(): Map<String, String> {
        return catalog
            .filter { it.nameEn.isNotBlank() && it.name != it.nameEn }
            .associateBy({ it.nameEn.lowercase().trim() }, { it.name })
    }

    private fun applyMarketFilter() {
        if (marketRates.isEmpty()) return
        val query = marketPane.etMarketSearch.text?.toString()?.trim()?.lowercase().orEmpty()
        val results = if (query.isEmpty()) marketRates else marketRates.filter {
            it.name.lowercase().contains(query) || it.koName.contains(query) || it.id.contains(query)
        }
        marketAdapter.submitList(results)
        marketPane.tvMarketStatus.text = getString(
            R.string.market_status,
            activeLeague ?: "-",
            Market.TYPES[activeType] ?: activeType,
            results.size.toString(),
        )
    }

    // ------------------------------------------------------------- 패시브 트리
    @SuppressLint("SetJavaScriptEnabled")
    private fun setupTree() {
        with(treePane.treeWebView.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            useWideViewPort = true
            loadWithOverviewMode = true
            cacheMode = WebSettings.LOAD_DEFAULT
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            allowFileAccess = false
            allowContentAccess = false
            userAgentString = "$userAgentString PoE2DBKR/1.0"
        }
        treePane.treeWebView.webViewClient = TreeClient()
        treePane.treeWebView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                treePane.treeProgress.progress = newProgress
                treePane.treeProgress.isVisible = newProgress < 100
            }
        }
        treePane.chipPassiveTree.setOnClickListener { openTree(PASSIVE_TREE_URL) }
        treePane.chipAtlasTree.setOnClickListener { openTree(ATLAS_TREE_URL) }
        treePane.btnTreeRefresh.setOnClickListener { treePane.treeWebView.reload() }
        treePane.btnTreeOpen.setOnClickListener {
            val url = treePane.treeWebView.url
            if (!url.isNullOrBlank()) openExternal(Uri.parse(url))
        }
    }

    private fun openTree(url: String) {
        binding.bottomNav.selectedItemId = R.id.nav_tree
        loadTree(url)
    }

    private fun loadTree(url: String) {
        val current = treePane.treeWebView.url
        if (current != url) treePane.treeWebView.loadUrl(url)
    }

    // ------------------------------------------------------------- PoB 실험실
    @SuppressLint("SetJavaScriptEnabled")
    private fun setupPob() {
        slotAdapter = PobSlotAdapter(onClick = { slot -> showSlotDialog(slot) })
        pobPane.rvPobSlots.layoutManager = LinearLayoutManager(this)
        pobPane.rvPobSlots.adapter = slotAdapter

        pobPane.pobTabs.addTab(pobPane.pobTabs.newTab().setText(R.string.pob_tab_overview))
        pobPane.pobTabs.addTab(pobPane.pobTabs.newTab().setText(R.string.pob_tab_skills))
        pobPane.pobTabs.addTab(pobPane.pobTabs.newTab().setText(R.string.pob_tab_items))
        pobPane.pobTabs.addTab(pobPane.pobTabs.newTab().setText(R.string.pob_tab_config))
        pobPane.pobTabs.addTab(pobPane.pobTabs.newTab().setText(R.string.pob_tab_tree))
        pobPane.pobTabs.addTab(pobPane.pobTabs.newTab().setText(R.string.pob_tab_battle))
        pobPane.pobTabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                val page = when (tab.position) {
                    1 -> pobPane.rvPobSkills
                    2 -> pobPane.rvPobSlots
                    3 -> pobPane.pobPageConfig
                    4 -> pobPane.pobPageTree
                    5 -> pobPane.pobPageBattle
                    else -> pobPane.pobPageOverview
                }
                listOf(
                    pobPane.pobPageOverview, pobPane.rvPobSkills, pobPane.rvPobSlots,
                    pobPane.pobPageConfig, pobPane.pobPageTree, pobPane.pobPageBattle,
                ).forEach { it.isVisible = it === page }
                if (tab.position == 2) refreshSlots()
                if (tab.position == 1) renderSkills()
                if (tab.position == 5) {
                    refreshBattleHeader()
                    if (battleWantsPlay) battleView.resume()
                } else {
                    battleView.pause()
                }
            }

            override fun onTabUnselected(tab: TabLayout.Tab) = Unit
            override fun onTabReselected(tab: TabLayout.Tab) = Unit
        })

        pobPane.btnPobLoad.setOnClickListener { loadPob(pobPane.etPobCode.text?.toString().orEmpty()) }
        pobPane.btnPobSample.setOnClickListener {
            pobPane.etPobCode.setText(Pob.sampleCode())
            loadPob(Pob.sampleCode())
        }
        pobPane.btnPobExport.setOnClickListener { exportPob() }
        pobPane.btnPobReset.setOnClickListener { resetPob() }

        pobPane.seekEndurance.setOnSeekBarChangeListener(simpleSeek { pobConfig.enduranceCharges = it; refreshPobCalc() })
        pobPane.seekFrenzy.setOnSeekBarChangeListener(simpleSeek { pobConfig.frenzyCharges = it; refreshPobCalc() })
        pobPane.seekPower.setOnSeekBarChangeListener(simpleSeek { pobConfig.powerCharges = it; refreshPobCalc() })
        pobPane.switchOnslaught.setOnCheckedChangeListener { _, checked ->
            pobConfig.onslaught = checked; refreshPobCalc()
        }
        pobPane.switchArcaneSurge.setOnCheckedChangeListener { _, checked ->
            pobConfig.arcaneSurge = checked; refreshPobCalc()
        }
        pobPane.enemyGroup.setOnCheckedChangeListener { _, checkedId ->
            pobConfig.enemy = when (checkedId) {
                R.id.enemyRare -> PobCalculator.Config.Enemy.RARE
                R.id.enemyPinnacle -> PobCalculator.Config.Enemy.PINNACLE
                else -> PobCalculator.Config.Enemy.NORMAL
            }
            refreshPobCalc()
        }
        val ailments = listOf("감전", "점화", "동결", "출혈", "중독", "노출", "위축", "저주")
        ailments.forEach { name ->
            val chip = Chip(this)
            chip.text = name
            chip.isCheckable = true
            chip.setOnCheckedChangeListener { _, checked ->
                if (checked) pobConfig.ailments.add(name) else pobConfig.ailments.remove(name)
                refreshPobCalc()
            }
            pobPane.ailmentChips.addView(chip)
        }

        pobPane.btnOpenTree.setOnClickListener { openTree(PASSIVE_TREE_URL) }
        pobPane.btnOpenAtlas.setOnClickListener { openTree(ATLAS_TREE_URL) }

        setupBattle()
    }

    // ------------------------------------------------------------- 전투 시뮬레이터
    private fun setupBattle() {
        battleView = pobPane.battleCanvas
        battleView.onFinished = { result -> showBattleResult(result) }

        buildBattleModeChips()
        buildBattleOptChips()
        buildBattleFxChips()

        pobPane.btnBattleStart.setOnClickListener { startBattle() }
        pobPane.btnBattlePause.setOnClickListener { toggleBattlePause() }
        pobPane.btnBattleReset.setOnClickListener { resetBattle() }
        pobPane.btnBattleSpeed.setOnClickListener {
            battleSpeed = if (battleSpeed == 1f) 2f else 1f
            battleView.setSpeed(battleSpeed)
            pobPane.btnBattleSpeed.text = getString(
                if (battleSpeed == 1f) R.string.battle_speed_1x else R.string.battle_speed_2x
            )
        }
    }

    private fun battleChip(label: String, checked: Boolean, onPick: (Chip) -> Unit): Chip {
        val chip = Chip(this)
        chip.id = View.generateViewId()
        chip.text = label
        chip.isCheckable = true
        chip.isChecked = checked
        chip.setOnClickListener { view ->
            val c = view as Chip
            if (c.isChecked) onPick(c) else c.isChecked = true
        }
        return chip
    }

    private fun buildBattleModeChips() {
        val group = pobPane.battleModeChips
        group.removeAllViews()
        listOf(
            BattleMode.MAP to getString(R.string.battle_mode_map),
            BattleMode.BOSS to getString(R.string.battle_mode_boss),
        ).forEach { (mode, label) ->
            group.addView(
                battleChip(label, battleMode == mode) {
                    battleMode = mode
                    buildBattleOptChips()
                    restartBattleIfRunning()
                }
            )
        }
    }

    private fun buildBattleOptChips() {
        val group = pobPane.battleOptChips
        group.removeAllViews()
        if (battleMode == BattleMode.MAP) {
            listOf(10, 25, 50, 100).forEach { count ->
                group.addView(
                    battleChip(getString(R.string.battle_mob_count, count), battleMobCount == count) {
                        battleMobCount = count
                        for (i in 0 until group.childCount) {
                            val c = group.getChildAt(i) as Chip
                            if (c.text.toString().endsWith("마리")) c.isChecked = c.text.toString() == getString(R.string.battle_mob_count, count)
                        }
                        restartBattleIfRunning()
                    }
                )
            }
            group.addView(
                battleChip(getString(R.string.battle_map_amp), battleAmp) { chip ->
                    battleAmp = chip.isChecked
                    restartBattleIfRunning()
                }
            )
        } else {
            BossPresets.ALL.forEach { preset ->
                group.addView(
                    battleChip(preset.name, battleBoss.id == preset.id) {
                        battleBoss = preset
                        for (i in 0 until group.childCount) {
                            val c = group.getChildAt(i) as Chip
                            c.isChecked = c.text == preset.name
                        }
                        restartBattleIfRunning()
                    }
                )
            }
        }
    }

    private fun buildBattleFxChips() {
        val group = pobPane.battleFxChips
        group.removeAllViews()
        group.addView(
            battleChip(getString(R.string.battle_fx_auto), battleFxOverride == null) {
                battleFxOverride = null
                for (i in 0 until group.childCount) {
                    val c = group.getChildAt(i) as Chip
                    c.isChecked = c.text.toString() == getString(R.string.battle_fx_auto)
                }
                refreshBattleHeader()
                restartBattleIfRunning()
            }
        )
        SkillEffectType.values().forEach { type ->
            group.addView(
                battleChip(type.label, battleFxOverride == type) {
                    battleFxOverride = type
                    for (i in 0 until group.childCount) {
                        val c = group.getChildAt(i) as Chip
                        c.isChecked = c.text == type.label
                    }
                    refreshBattleHeader()
                    restartBattleIfRunning()
                }
            )
        }
    }

    private fun restartBattleIfRunning() {
        if (battleStarted) startBattle()
    }

    private fun battleStats(): BattlePlayerStats? {
        val build = pobBuild ?: return null
        val calc = PobCalculator.compute(build, pobOverrides, pobFlasks, pobConfig)
        val group = build.skills.firstOrNull { it.label.toIntOrNull() == pobMainGroup }
            ?: build.skills.firstOrNull { it.enabled && it.gems.isNotEmpty() }
        val gems = group?.gems?.filter { it.enabled }.orEmpty()
        val mainGem = gems.firstOrNull {
            !PobCalculator.SUPPORT_GEMS.containsKey(it.name.lowercase().trim())
        } ?: gems.firstOrNull()
        val skillName = mainGem?.name ?: group?.label ?: build.className.ifBlank { "기본 공격" }
        val fx = battleFxOverride ?: SkillEffects.detect(skillName, group?.label.orEmpty())
        val weaponItem = pobOverrides["Weapon 1"] ?: Pob.itemForSlot(build, "Weapon 1")
        val weapon = SkillEffects.weaponFor(fx, weaponItem?.lines.orEmpty())
        val evade = (calc.evasionNow / (calc.evasionNow + 7500.0) * 92.0).coerceAtMost(80.0)
        val res = calc.resists
        return BattlePlayerStats(
            className = listOf(build.className, build.ascendClassName)
                .filter { it.isNotBlank() }.joinToString(" · "),
            skillName = skillName,
            fx = fx,
            weapon = weapon,
            dps = calc.dpsNow,
            aps = calc.apsNow,
            critChance = calc.critNow,
            critMulti = calc.critMultiNow,
            life = calc.lifeNow,
            es = calc.esNow,
            physRed = calc.physRedNow,
            evade = evade,
            fireRes = res.getOrNull(0)?.third ?: 0.0,
            coldRes = res.getOrNull(1)?.third ?: 0.0,
            lightRes = res.getOrNull(2)?.third ?: 0.0,
            chaosRes = res.getOrNull(3)?.third ?: 0.0,
            ehp = calc.ehpNow,
            dpsBase = calc.dpsBase,
        )
    }

    private fun refreshBattleHeader() {
        val stats = battleStats()
        if (stats == null) {
            pobPane.tvBattleSkill.text = getString(R.string.battle_need_build)
            return
        }
        val fmt = NumberFormat.getNumberInstance(Locale.KOREA)
        val fxLabel = if (battleFxOverride == null) stats.fx.label else stats.fx.label + " · 직접 선택"
        pobPane.tvBattleSkill.text = getString(
            R.string.battle_skill_line,
            stats.skillName,
            fxLabel,
            stats.aps,
            fmt.format(stats.dps.toLong()),
        )
    }

    private fun startBattle() {
        val stats = battleStats()
        if (stats == null) {
            Snackbar.make(binding.root, R.string.battle_need_build, Snackbar.LENGTH_SHORT).show()
            return
        }
        val world = BattleWorld(
            stats = stats,
            mode = battleMode,
            mobCount = battleMobCount,
            amp = battleAmp,
            boss = if (battleMode == BattleMode.BOSS) battleBoss else null,
        )
        battleWorld = world
        battleStarted = true
        battleWantsPlay = true
        pobPane.tvBattleResult.isVisible = false
        pobPane.btnBattlePause.text = getString(R.string.battle_pause)
        battleView.setSpeed(battleSpeed)
        battleView.attach(world)
        battleView.play()
        refreshBattleHeader()
    }

    private fun toggleBattlePause() {
        if (battleWorld == null) {
            startBattle()
            return
        }
        if (battleView.isPaused) {
            battleView.resume()
            battleWantsPlay = true
            pobPane.btnBattlePause.text = getString(R.string.battle_pause)
        } else {
            battleView.pause()
            battleWantsPlay = false
            pobPane.btnBattlePause.text = getString(R.string.battle_resume)
        }
    }

    private fun resetBattle() {
        battleView.stop()
        battleView.clear()
        battleWorld = null
        battleStarted = false
        battleWantsPlay = false
        pobPane.tvBattleResult.isVisible = false
        pobPane.btnBattlePause.text = getString(R.string.battle_pause)
        refreshBattleHeader()
    }

    private fun showBattleResult(result: BattleResult) {
        val fmt = NumberFormat.getNumberInstance(Locale.KOREA)
        val head = getString(
            if (result.victory) R.string.battle_result_win else R.string.battle_result_lose,
            result.seconds,
        )
        val body = getString(
            R.string.battle_result_body,
            BattleWorld.formatShort(result.damageDealt),
            BattleWorld.formatShort(result.effectiveDps),
            result.crits,
            result.hitsTaken,
            result.evades,
            result.kills,
            result.totalEnemies,
            result.survival,
            fmt.format(result.lifeLeft.toLong()),
            fmt.format(result.esLeft.toLong()),
        )
        pobPane.tvBattleResult.text = head + "\n" + body
        pobPane.tvBattleResult.isVisible = true
        pobPane.btnBattlePause.text = getString(R.string.battle_pause)
        battleWantsPlay = false
    }

    private fun simpleSeek(onValue: (Int) -> Unit) = object : android.widget.SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(seekBar: android.widget.SeekBar?, value: Int, fromUser: Boolean) {
            if (fromUser) onValue(value)
        }

        override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) = Unit
        override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) = Unit
    }

    private fun loadPob(raw: String) {
        if (raw.isBlank()) return
        network.execute {
            val result = runCatching {
                if (raw.contains("pobb.in")) Pob.fetchPobbIn(raw) else Pob.decode(raw)
            }
            handler.post {
                val build = result.getOrNull()
                if (build == null) {
                    Snackbar.make(binding.root, R.string.pob_load_failed, Snackbar.LENGTH_LONG).show()
                    return@post
                }
                pobBuild = build
                pobOverrides.clear()
                pobFlasks.clear()
                pobMainGroup = build.mainSocketGroup
                pobPane.tvBuildTitle.text = build.title
                pobPane.tvBuildHint.text = getString(
                    R.string.pob_build_summary,
                    build.skills.size,
                    build.slots.size,
                    build.treeNodes,
                )
                runCatching { pobStore.add(Pob.encode(build), build.title) }
                renderRecent()
                refreshPobCalc()
                refreshSlots()
                renderSkills()
                Snackbar.make(binding.root, R.string.pob_loaded, Snackbar.LENGTH_SHORT).show()
            }
        }
    }

    private fun renderRecent() {
        val entries = pobStore.all()
        pobPane.recentScroll.isVisible = entries.isNotEmpty()
        if (entries.isEmpty()) return
        val group = pobPane.recentChips
        group.removeAllViews()
        entries.forEach { entry ->
            val chip = Chip(this)
            chip.text = entry.label
            chip.isCheckable = false
            chip.setOnClickListener { pobPane.etPobCode.setText(entry.code); loadPob(entry.code) }
            group.addView(chip)
        }
    }

    private fun exportPob() {
        val build = pobBuild ?: run {
            Snackbar.make(binding.root, R.string.pob_empty_title, Snackbar.LENGTH_SHORT).show()
            return
        }
        val code = Pob.encode(build)
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("PoB code", code))
        Snackbar.make(binding.root, R.string.pob_copied, Snackbar.LENGTH_SHORT).show()
    }

    private fun resetPob() {
        pobOverrides.clear()
        pobFlasks.clear()
        pobConfig.enduranceCharges = 0
        pobConfig.frenzyCharges = 0
        pobConfig.powerCharges = 0
        pobConfig.onslaught = false
        pobConfig.arcaneSurge = false
        pobConfig.ailments.clear()
        pobConfig.enemy = PobCalculator.Config.Enemy.NORMAL
        pobPane.seekEndurance.progress = 0
        pobPane.seekFrenzy.progress = 0
        pobPane.seekPower.progress = 0
        pobPane.switchOnslaught.isChecked = false
        pobPane.switchArcaneSurge.isChecked = false
        pobPane.enemyGroup.check(R.id.enemyNormal)
        refreshPobCalc()
        refreshSlots()
        Snackbar.make(binding.root, R.string.pob_reset_done, Snackbar.LENGTH_SHORT).show()
    }

    private fun refreshSlots() {
        val build = pobBuild ?: run {
            slotAdapter.submit(emptyList(), emptySet())
            return
        }
        val rows = ArrayList<Pair<String, PobItem?>>()
        Pob.SLOTS.forEach { (slot, _) ->
            rows.add(slot to (pobOverrides[slot] ?: Pob.itemForSlot(build, slot)))
        }
        build.slots.keys.filter { it.startsWith(Pob.FLASK_PREFIX) }.sorted().forEach { flask ->
            rows.add(flask to Pob.itemForSlot(build, flask))
        }
        slotAdapter.submit(rows, pobOverrides.keys)
    }

    private fun renderSkills() {
        val build = pobBuild ?: return
        val container = pobPane.rvPobSkills
        container.removeAllViews()
        val density = resources.displayMetrics.density
        build.skills.forEachIndexed { groupIndex, group ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(ContextCompat.getColor(context, R.color.surface))
                setPadding(
                    (12 * density).toInt(), (10 * density).toInt(),
                    (8 * density).toInt(), (10 * density).toInt()
                )
            }
            val cardParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = (8 * density).toInt() }
            card.layoutParams = cardParams

            val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            val title = TextView(this).apply {
                text = "${groupIndex + 1}. ${group.label}"
                setTextColor(ContextCompat.getColor(context, R.color.text_primary))
                textSize = 14f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            header.addView(title)
            if (groupIndex + 1 == build.mainSocketGroup) {
                val main = TextView(this).apply {
                    text = getString(R.string.pob_main_skill)
                    setTextColor(ContextCompat.getColor(context, R.color.gold))
                    textSize = 11f
                    setPadding((6 * density).toInt(), 0, (10 * density).toInt(), 0)
                }
                header.addView(main)
            } else {
                val setMain = com.google.android.material.button.MaterialButton(this).apply {
                    text = getString(R.string.pob_main_skill)
                    textSize = 10f
                    minHeight = (32 * density).toInt()
                    insetTop = 0; insetBottom = 0
                    setOnClickListener { pobMainGroup = groupIndex + 1; renderSkills(); refreshPobCalc() }
                }
                header.addView(setMain)
            }
            card.addView(header)

            group.gems.forEachIndexed { gemIndex, gem ->
                val row = ItemPobGemBinding.inflate(layoutInflater)
                row.gemSwitch.isChecked = gem.enabled
                row.gemSwitch.setOnCheckedChangeListener { _, checked ->
                    gem.enabled = checked
                    refreshPobCalc()
                }
                val isSupport = PobCalculator.SUPPORT_GEMS.containsKey(gem.name.lowercase().trim())
                row.tvGemName.text = gem.name
                row.tvGemKind.text = getString(if (isSupport) R.string.pob_support_kind else R.string.pob_skill_kind)
                row.tvGemLevel.text = "Lv ${gem.level}"
                row.btnLevelUp.setOnClickListener { gem.level = (gem.level + 1).coerceAtMost(40); renderSkills(); refreshPobCalc() }
                row.btnLevelDown.setOnClickListener { gem.level = (gem.level - 1).coerceAtLeast(1); renderSkills(); refreshPobCalc() }
                card.addView(row.root)
            }
            container.addView(card)
        }
    }

    private fun refreshPobCalc() {
        val build = pobBuild ?: return
        val result = PobCalculator.compute(build, pobOverrides, pobFlasks, pobConfig)
        val fmt = NumberFormat.getNumberInstance(Locale.KOREA)

        pobPane.tvDps.text = fmt.format(result.dpsNow.toLong())
        val dpsPct = if (result.dpsBase > 0) (result.dpsNow / result.dpsBase - 1) * 100 else 0.0
        pobPane.tvDpsDelta.text = "기준 %s DPS · %s%.1f%%".format(
            fmt.format(result.dpsBase.toLong()),
            if (dpsPct >= 0) "+" else "",
            dpsPct,
        )
        pobPane.tvDpsDelta.setTextColor(
            when {
                dpsPct > 0.05 -> ContextCompat.getColor(this, R.color.resist_high)
                dpsPct < -0.05 -> ContextCompat.getColor(this, R.color.resist_low)
                else -> ContextCompat.getColor(this, R.color.text_muted)
            }
        )

        pobPane.tvLife.text = fmt.format(result.lifeNow.toLong())
        pobPane.tvEs.text = fmt.format(result.esNow.toLong())
        pobPane.tvEhp.text = fmt.format(result.ehpNow.toLong())
        appendDelta(pobPane.tvLife, result.lifeBase, result.lifeNow)
        appendDelta(pobPane.tvEs, result.esBase, result.esNow)

        pobPane.tvDefence.text = buildString {
            append("방어도 %s (%.1f%% 물리 감소)".format(fmt.format(result.armourNow.toLong()), result.physRedNow))
            append(" · 회피 %s (%.1f%%)".format(fmt.format(result.evasionNow.toLong()), result.evasionPctNow))
            append("\n공속/시전 %.2f회/초".format(result.apsNow))
            append(" · 치명타 %.1f%% / 배율 %.0f%%".format(result.critNow, result.critMultiNow))
        }

        pobPane.tvResists.text = result.resists.joinToString("\n") { (label, base, now) ->
            val color = when {
                label == "카오스" -> R.color.text_secondary
                now >= 75 -> R.color.resist_high
                else -> R.color.resist_low
            }
            val spannable = android.text.SpannableString(
                "%s %s%% → %s%%%s".format(
                    label,
                    "%.0f".format(base),
                    "%.0f".format(now),
                    if (now >= 75 && now > 100) " (캡 초과)" else if (now < 75) " (75 미달)" else "",
                )
            )
            spannable.setSpan(
                android.text.style.ForegroundColorSpan(ContextCompat.getColor(this, color)),
                0, spannable.length, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            spannable
        }

        val skills = build.skills.filter { it.enabled }
        pobPane.tvSkillSummary.text = skills.joinToString("\n") { group ->
            "· ${group.label}: " + group.gems.filter { it.enabled }.joinToString(", ") { it.name }
        }.ifBlank { getString(R.string.pob_tree_empty) }

        refreshBattleHeader()

        pobPane.tvTreeInfo.text = buildString {
            append("클래스: ${build.title}\n")
            append("패시브 노드: ${build.treeNodes}개\n")
            if (build.treeTitle.isNotBlank()) append("트리: ${build.treeTitle}\n")
            append("\n아래 버튼으로 poe2db.tw 트리 뷰어에서 직접 찍어볼 수 있습니다.")
        }
    }

    private fun appendDelta(view: TextView, base: Double, now: Double) {
        if (base <= 0) return
        val diff = now - base
        if (diff == 0.0) return
        val sign = if (diff > 0) "+" else ""
        view.append("  (%s%s)".format(sign, "%.1f%%".format(diff / base * 100)))
    }

    private fun equipUnique(item: CatalogItem) {
        val dialog = currentSlotDialog ?: return
        val lines = mutableListOf<String>()
        if (item.summary.isNotBlank()) lines.add(item.summary)
        lines.addAll(item.stats)
        val unique = PobItem(name = item.name, rarity = "Unique", lines = lines)
        dialog.binding.etModLines.setText(lines.joinToString("\n"))
        dialog.pendingUnique = unique
        Snackbar.make(binding.root, "%s 선택됨 — 저장을 누르면 장착됩니다".format(item.name), Snackbar.LENGTH_SHORT).show()
    }

    private var currentSlotDialog: SlotDialog? = null

    private inner class SlotDialog(val slot: String, val binding: DialogPobSlotBinding) {
        var pendingUnique: PobItem? = null
    }

    private fun showSlotDialog(slot: String) {
        val build = pobBuild ?: return
        val binding = DialogPobSlotBinding.inflate(layoutInflater)
        val dialog = SlotDialog(slot, binding)
        currentSlotDialog = dialog

        binding.tvSlotTitle.text = Pob.SLOTS[slot] ?: slot
        val original = Pob.itemForSlot(build, slot)
        val current = pobOverrides[slot] ?: original
        binding.etModLines.setText(current?.lines?.joinToString("\n") ?: "")

        val uniqueAdapter = PobUniqueAdapter(onClick = { item -> equipUnique(item) })
        binding.rvUniqueList.layoutManager = LinearLayoutManager(this)
        binding.rvUniqueList.adapter = uniqueAdapter
        uniqueAdapter.submitList(uniques.take(60))
        binding.etUniqueSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                val query = s?.toString()?.trim()?.lowercase().orEmpty()
                uniqueAdapter.submitList(
                    if (query.isEmpty()) uniques.take(60)
                    else uniques.filter { it.searchText.contains(query) }.take(60)
                )
            }
        })

        val presets = listOf(
            "생명력" to "+80 최대 생명력",
            "화염 저항" to "+30% 화염 저항",
            "냉기 저항" to "+30% 냉기 저항",
            "번개 저항" to "+30% 번개 저항",
            "카오스 저항" to "+25% 카오스 저항",
            "방어도" to "+500 방어도",
            "회피" to "+500 회피",
            "보호막" to "+80 에너지 보호막 최대치",
            "피해 %" to "피해 25% 증가",
            "공속" to "공격 속도 15% 증가",
            "시전 속도" to "시전 속도 15% 증가",
            "치명타" to "치명타 확률 20% 증가",
        )
        presets.forEach { (label, line) ->
            val chip = Chip(this)
            chip.text = label
            chip.isCheckable = false
            chip.setOnClickListener {
                val currentText = binding.etModLines.text?.toString().orEmpty()
                binding.etModLines.setText(if (currentText.isBlank()) line else currentText + "\n" + line)
            }
            binding.presetChips.addView(chip)
        }

        binding.btnRestore.setOnClickListener {
            pobOverrides.remove(slot)
            refreshSlots()
            refreshPobCalc()
            Snackbar.make(binding.root, R.string.pob_slot_restored, Snackbar.LENGTH_SHORT).show()
            currentSlotDialog = null
            slotDialogReference?.dismiss()
        }

        slotDialogReference = MaterialAlertDialogBuilder(this)
            .setView(binding.root)
            .setPositiveButton(R.string.pob_equip_save) { _, _ ->
                val lines = binding.etModLines.text?.toString()
                    ?.split("\n")
                    ?.map { it.trim() }
                    ?.filter { it.isNotEmpty() }
                    .orEmpty()
                when {
                    lines.isEmpty() && pobOverrides.containsKey(slot) -> pobOverrides.remove(slot)
                    lines.isNotEmpty() -> {
                        val replaced = dialog.pendingUnique
                        pobOverrides[slot] = PobItem(
                            name = replaced?.name ?: current?.name ?: lines.firstOrNull() ?: "",
                            rarity = replaced?.rarity ?: current?.rarity ?: "Rare",
                            lines = lines.toMutableList(),
                        )
                    }
                }
                refreshSlots()
                refreshPobCalc()
                Snackbar.make(binding.root, R.string.pob_equip_saved, Snackbar.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.info_close, null)
            .show()
    }

    private var slotDialogReference: androidx.appcompat.app.AlertDialog? = null

    // ------------------------------------------------------------- 공용
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
            detailPane.detailProgress.isVisible = false
            detailPane.detailSwipe.isRefreshing = false
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
            detailPane.detailProgress.isVisible = false
            detailPane.detailSwipe.isRefreshing = false
            Snackbar.make(binding.root, R.string.page_error, Snackbar.LENGTH_LONG)
                .setAction(R.string.retry) { view.reload() }
                .show()
        }
    }

    private inner class TreeClient : WebViewClient() {

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
            treePane.treeProgress.isVisible = false
            if (!url.startsWith("http")) return
            view.evaluateJavascript(MobileCss.TREE_SCRIPT, null)
        }

        override fun onReceivedError(
            view: WebView,
            request: WebResourceRequest,
            error: WebResourceError,
        ) {
            if (!request.isForMainFrame) return
            Snackbar.make(binding.root, R.string.tree_error, Snackbar.LENGTH_LONG)
                .setAction(R.string.retry) { view.reload() }
                .show()
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
            detailPane.tvDetailTitle.text = state.getString(KEY_DETAIL_TITLE, getString(R.string.site_title))
            detailPane.btnDetailStar.isVisible = false
            showDetailPane()
            detailPane.webView.loadUrl(url)
        }
        if (activeTab == R.id.nav_market) ensureMarket()
        if (activeTab == R.id.nav_tree && treePane.treeWebView.url.isNullOrBlank()) loadTree(PASSIVE_TREE_URL)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_TAB, activeTab)
        outState.putString(KEY_QUERY, currentQuery())
        if (binding.paneDetail.isVisible) {
            outState.putString(KEY_DETAIL_URL, detailPane.webView.url)
            outState.putString(KEY_DETAIL_TITLE, detailPane.tvDetailTitle.text?.toString())
        }
    }

    override fun onResume() {
        super.onResume()
        detailPane.webView.onResume()
        treePane.treeWebView.onResume()
        if (battleWantsPlay) battleView.resume()
    }

    override fun onPause() {
        detailPane.webView.onPause()
        treePane.treeWebView.onPause()
        battleView.pause()
        super.onPause()
    }

    override fun onDestroy() {
        handler.removeCallbacks(filterTick)
        handler.removeCallbacks(marketTick)
        detailPane.webView.destroy()
        treePane.treeWebView.destroy()
        battleView.stop()
        super.onDestroy()
    }

    private companion object {
        const val HOME_URL = "https://poe2db.tw/kr/"
        const val PASSIVE_TREE_URL = "https://poe2db.tw/kr/passive-skill-tree/"
        const val ATLAS_TREE_URL = "https://poe2db.tw/kr/atlas-skill-tree/"
        const val PANE_LIST = 0
        const val PANE_MARKET = 1
        const val PANE_TREE = 2
        const val PANE_POB = 3
        const val KEY_TAB = "tab"
        const val KEY_QUERY = "query"
        const val KEY_DETAIL_URL = "detailUrl"
        const val KEY_DETAIL_TITLE = "detailTitle"
    }
}
