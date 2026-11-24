package com.sunuhwi.poe2dbkr

import android.annotation.SuppressLint
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
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ImageView
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
import coil.load
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayout
import com.sunuhwi.poe2dbkr.databinding.ActivityMainBinding
import com.sunuhwi.poe2dbkr.databinding.DialogMarketItemBinding
import com.sunuhwi.poe2dbkr.databinding.DialogPobLoadBinding
import com.sunuhwi.poe2dbkr.databinding.DialogPobSlotBinding
import com.sunuhwi.poe2dbkr.databinding.ItemPobGemBinding
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
    private var marketSort = MarketSort.VALUE_DESC
    private var catalogByEn: Map<String, CatalogItem> = emptyMap()
    /** 시세·장비 화면의 한글 사전 (assets/poe2_kr_i18n.json) */
    private var krDict: KrDict = KrDict.EMPTY

    private var pobBuild: PobBuild? = null
    private val pobOverrides = HashMap<String, PobItem>()
    /** 전투 탭 콤보 설정 (빌드 스킬 순서) */
    private lateinit var comboStore: ComboStore
    private var comboPicks: List<ComboPick> = emptyList()
    private val pobFlasks = mutableSetOf<String>()
    private val pobConfig = PobCalculator.Config()
    private var pobMainGroup = 0

    private lateinit var battleView: BattleCanvasView
    private val battleReady: Boolean get() = this::battleView.isInitialized
    /** 전투 탭에서 직접 고른 스킬 젬 이름. null이면 빌드 주력 스킬(자동) */
    private var battleSkillPick: String? = null
    private var battleTriggerMode = TriggerMode.AUTO
    private var battleMode = BattleMode.MAP
    private var battleMobCount = 25
    private var battleAmp = false
    private var battleBoss: BossPreset = BossPresets.ALL[0]
    private var battleWorld: BattleWorld? = null
    private var battleStarted = false
    private var battleWantsPlay = false
    private var battleSpeed = 1f
    /** 전투 화면에서 직접 끈 정신력 젬 이름 (정신력 점유에서 뺀다) */
    private val battleSpiritOff = LinkedHashSet<String>()
    /** 전투 화면에서 직접 끈 메타(발동) 젬 */
    private val battleTriggerOff = LinkedHashSet<TriggerKind>()
    /** 전면 광고 (PoB 빌드 '불러오기'를 누를 때 한 번) */
    private var ads: Ads? = null

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
        // 광고는 미리 받아 두고, 없으면 빌드 불러오기를 그대로 진행한다.
        ads = Ads(this).also { it.preload() }
        // 정신력 점유 스킬 이름을 미리 읽어 둔다 (스킬 칸·콤보에서 정신력·메타 젬을 걸러 내는 데 쓴다).
        runCatching { SpiritBuffs.catalog(this) }
        bookmarks = BookmarkStore(this)
        pobStore = PobStore(this)
        comboStore = ComboStore(this)
        // 지난번에 짜 둔 콤보를 그대로 불러온다 (빌드에 없는 스킬은 갱신하면서 걸러진다).
        comboPicks = comboStore.load(emptyList())

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

        I18n.preload(this) { dict ->
            krDict = dict
            if (marketRates.isNotEmpty()) {
                marketRates = marketRates.map { localize(it, dict) }
                applyMarketFilter()
            }
        }

        Catalog.load(this) { loadedItems ->
            catalog = loadedItems
            uniques = loadedItems.filter { it.cat == "unique" }
            catalogByEn = loadedItems
                .filter { it.nameEn.isNotBlank() }
                .associateBy { it.nameEn.lowercase().trim() }
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
                    // 탭을 다시 눌러 돌아왔을 때는 보던 자리(확대·이동)를 그대로 둔다.
                    if (treePane.treeCanvas.isEmpty()) showPassiveTree()
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
            if (battleWantsPlay && battleReady) battleView.resume()
        } else if (battleReady) {
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
        } catch (e: Exception) {
            Log.w(LOG_TAG, "링크를 열지 못했습니다: $uri", e)
            Snackbar.make(binding.root, R.string.link_error, Snackbar.LENGTH_SHORT).show()
        }
    }

    // ------------------------------------------------------------- 실시간 시세
    private fun setupMarket() {
        marketAdapter = MarketAdapter(onClick = { rate -> showMarketDetail(rate) })
        marketPane.rvMarket.layoutManager = LinearLayoutManager(this)
        marketPane.rvMarket.adapter = marketAdapter

        val types = marketPane.typeChipGroup
        var firstId = View.NO_ID
        Market.TYPES.forEach { type ->
            val chip = Chip(this)
            chip.id = View.generateViewId()
            chip.text = type.label
            chip.isCheckable = true
            chip.tag = type.id
            chip.setOnClickListener { view ->
                if ((view as Chip).isChecked) {
                    activeType = type.id
                    types.check(view.id)
                    loadMarketRates()
                }
            }
            types.addView(chip)
            if (firstId == View.NO_ID) firstId = chip.id
        }
        types.check(firstId)

        val sortGroup = marketPane.sortChipGroup
        MarketSort.values().forEach { sort ->
            val chip = Chip(this)
            chip.id = View.generateViewId()
            chip.text = sort.label
            chip.isCheckable = true
            chip.tag = sort
            chip.isChecked = sort == marketSort
            chip.setOnClickListener { view ->
                if ((view as Chip).isChecked) {
                    marketSort = sort
                    sortGroup.check(view.id)
                    applyMarketFilter()
                }
            }
            sortGroup.addView(chip)
        }

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

        listPane.btnAtlasTree.setOnClickListener { openNativeAtlas() }
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
                Market.overview(league, type, krDict).map { rate -> enrich(rate) }
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
                        Market.TYPES.firstOrNull { it.id == type }?.label ?: type,
                        rates.size.toString(),
                    )
                }
            }
        }
    }

    /** 내장 DB에서 한글 이름·베이스 타입·옵션·페이지 주소를 채운다. */
    private fun enrich(rate: MarketRate): MarketRate {
        val db = catalogByEn[rate.name.lowercase().trim()] ?: return rate
        return rate.copy(
            koName = rate.koName.ifBlank { db.name },
            koBase = rate.koBase.ifBlank { db.meta },
            pageUrl = rate.pageUrl.ifBlank { db.url },
        )
    }

    /** 사전이 늦게 도착했을 때 이미 받아 둔 시세 줄을 다시 한글로 맞춘다. */
    private fun localize(rate: MarketRate, dict: KrDict): MarketRate = rate.copy(
        koName = rate.koName.ifBlank { dict.name(rate.name) },
        koBase = rate.koBase.ifBlank { dict.base(rate.baseType) },
        koCategory = rate.koCategory.ifBlank { dict.category(rate.category) },
    )

    private fun applyMarketFilter() {
        if (marketRates.isEmpty()) return
        val query = marketPane.etMarketSearch.text?.toString()?.trim()?.lowercase().orEmpty()
        val filtered = if (query.isEmpty()) marketRates else marketRates.filter {
            it.name.lowercase().contains(query) || it.koName.contains(query) ||
                it.baseType.lowercase().contains(query) || it.koBase.contains(query) ||
                it.id.contains(query)
        }
        val results = Market.sort(filtered, marketSort)
        marketAdapter.submitList(results)
        marketPane.tvMarketStatus.text = getString(
            R.string.market_status,
            activeLeague ?: "-",
            Market.TYPES.firstOrNull { it.id == activeType }?.label ?: activeType,
            results.size.toString(),
        )
    }

    private fun mutedColor(): Int = ContextCompat.getColor(this, R.color.text_muted)

    private fun showMarketDetail(rate: MarketRate) {
        val binding = DialogMarketItemBinding.inflate(layoutInflater)
        val dialog = com.google.android.material.bottomsheet.BottomSheetDialog(this)
        dialog.setContentView(binding.root)

        val db = catalogByEn[rate.name.lowercase().trim()]
        binding.ivIcon.load(rate.iconUrl.ifBlank { db?.icon.orEmpty() }.ifBlank { krDict.icon(rate.name) }) {
            placeholder(R.drawable.bg_icon)
            error(R.drawable.bg_icon)
        }
        binding.tvDetailName.text = rate.koName.ifBlank { rate.name }
        binding.tvDetailNameEn.text = listOf(rate.name, rate.baseType)
            .filter { it.isNotBlank() && it != rate.koName }
            .joinToString(" · ")
        binding.tvDetailNameEn.isVisible = binding.tvDetailNameEn.text.isNotBlank()
        binding.tvDetailBase.text = listOfNotNull(
            rate.koBase.takeIf { it.isNotBlank() },
            rate.koCategory.ifBlank { rate.category }.takeIf { it.isNotBlank() },
            rate.levelRequired.takeIf { it > 0 }?.let { "레벨 $it" },
            if (rate.corrupted) "타락" else null,
        ).joinToString(" · ")
        binding.tvDetailBase.isVisible = binding.tvDetailBase.text.isNotBlank()

        when {
            rate.change > 0.05 -> {
                binding.tvDetailChange.text = "7일 +%.1f%%".format(rate.change)
                binding.tvDetailChange.setTextColor(ContextCompat.getColor(this, R.color.resist_high))
            }
            rate.change < -0.05 -> {
                binding.tvDetailChange.text = "7일 %.1f%%".format(rate.change)
                binding.tvDetailChange.setTextColor(ContextCompat.getColor(this, R.color.resist_low))
            }
            else -> {
                binding.tvDetailChange.text = "7일 0%"
                binding.tvDetailChange.setTextColor(mutedColor())
            }
        }

        binding.tvDetailPrice.text = "${Market.formatValue(rate.divine)} 디바인"
        binding.tvDetailPriceSub.text =
            "≈ ${Market.formatValue(rate.exalted)} 엑잘 · ${Market.formatValue(rate.chaos)} 카오스"
        binding.tvDetailListings.text = when {
            rate.listingCount > 0 -> "매물 ${rate.listingCount}건"
            rate.volume > 0 -> "거래량 ${Market.formatValue(rate.volume)} ${Market.currencyLabel(rate.volumeCurrency)}"
            else -> "매물 집계 없음"
        }

        val spark = rate.spark
        if (spark.size >= 2) {
            binding.sparkView.isVisible = true
            binding.tvSparkTitle.isVisible = true
            binding.sparkView.setData(spark, rate.change >= 0)
        } else {
            binding.sparkView.isVisible = false
            binding.tvSparkTitle.text = getString(R.string.market_no_spark)
            binding.tvSparkTitle.isVisible = true
        }

        fun section(view: android.widget.TextView, title: String, lines: List<String>) {
            if (lines.isEmpty()) {
                view.isVisible = false
                return
            }
            view.isVisible = true
            view.text = title + "\n" + lines.joinToString("\n") { "· $it" }
        }
        val dbStats = db?.stats.orEmpty()
        section(
            binding.tvSecProperties,
            getString(R.string.market_detail_properties),
            rate.properties + listOfNotNull(db?.summary?.takeIf { it.isNotBlank() }),
        )
        section(binding.tvSecImplicit, getString(R.string.market_detail_implicit), rate.implicits)
        section(
            binding.tvSecExplicit,
            getString(R.string.market_detail_explicit),
            rate.explicits + dbStats,
        )
        section(binding.tvSecGranted, getString(R.string.market_detail_granted), rate.granted)
        section(binding.tvSecRequirements, getString(R.string.market_detail_requirements), rate.requirements)

        binding.tvSecFlavour.isVisible = rate.flavour.isNotBlank()
        binding.tvSecFlavour.text = "\u201C" + rate.flavour + "\u201D"

        val sourceUrl = rate.pageUrl.ifBlank {
            db?.url ?: Market.poe2dbUrl(rate.name)
        }
        val hasSource = rate.isUnique && sourceUrl.isNotBlank()
        binding.tvSecSource.isVisible = hasSource
        binding.tvSourceValue.isVisible = hasSource
        if (hasSource) {
            binding.tvSourceValue.text = getString(R.string.market_source_loading)
            network.execute {
                val source = Poe2dbSource.lookup(sourceUrl)
                handler.post {
                    if (!dialog.isShowing) return@post
                    val parts = ArrayList<String>()
                    if (source.droppedBy.isNotEmpty()) {
                        parts.add(getString(R.string.market_source_drop) + ": " + source.droppedBy.joinToString(", "))
                    }
                    if (source.forged.isNotBlank()) {
                        parts.add(getString(R.string.market_source_forge) + ": " + source.forged.take(220))
                    }
                    binding.tvSourceValue.text = if (parts.isEmpty()) {
                        getString(R.string.market_source_none)
                    } else {
                        parts.joinToString("\n")
                    }
                }
            }
        }

        binding.btnOpenPage.setOnClickListener {
            dialog.dismiss()
            if (sourceUrl.isNotBlank()) openExternal(Uri.parse(sourceUrl))
        }
        binding.btnCloseDetail.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    // ------------------------------------------------------------- 패시브 트리
    private fun setupTree() {
        treePane.treeCanvas.onNodeTap = TreeCanvasView.OnNodeTap { id, allocated ->
            showTreeNode(id, allocated)
            refreshTreePoints()
        }
        treePane.treeCanvas.onPointsChanged = TreeCanvasView.OnPointsChanged {
            renderTreePoints(it)
        }
        // 이어지지 않은 노드를 누르면 왜 안 찍히는지 알려 준다.
        treePane.treeCanvas.onRefused = TreeCanvasView.OnRefused { reason ->
            val message = when (reason) {
                TreeAllocation.Result.NOT_CONNECTED -> R.string.tree_refused_not_connected
                TreeAllocation.Result.NO_POINTS -> R.string.tree_refused_no_points
                TreeAllocation.Result.START_NODE -> R.string.tree_refused_start
                TreeAllocation.Result.BREAKS_PATH -> R.string.tree_refused_breaks
                else -> 0
            }
            if (message != 0) Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT).show()
        }
        // 그리는 도중 한 조각이 실패해도 트리는 화면에 그대로 둔다.
        treePane.treeCanvas.onDrawProblem = { t ->
            Log.e(LOG_TAG, "패시브 트리를 그리는 중 일부를 건너뛰었습니다", t)
        }
        treePane.btnTreeNodeClose.setOnClickListener { treePane.treeNodeCard.isVisible = false }
        treePane.chipPassiveTree.setOnClickListener { showPassiveTree() }
        treePane.chipAtlasTree.setOnClickListener { showAtlasTree() }
        treePane.btnTreeSearch.setOnClickListener { showTreeSearch() }
        treePane.btnTreeStats.setOnClickListener { showTreeStats() }
        treePane.btnTreeFit.setOnClickListener {
            if (treePane.treeCanvas.isEmpty()) showPassiveTree() else treePane.treeCanvas.showWholeTree()
        }
        treePane.btnTreeRefresh.setOnClickListener {
            if (treePane.treeCanvas.hasAllocationToReset()) {
                treePane.treeCanvas.resetAllocation()
                refreshTreePoints()
                Snackbar.make(binding.root, R.string.tree_reset_done, Snackbar.LENGTH_SHORT).show()
            } else {
                treePane.treeCanvas.reset()
            }
        }
    }

    private fun canvasVisible(): Boolean = treePane.treeCanvas.isVisible

    /** PoB 탭 패시브 화면의 트리 — 그 자리에서도 확대·축소하고 노드를 눌러 설명을 본다. */
    private fun setupPobTree() {
        // 이 화면은 빌드를 보여 주기만 한다. 찍고 빼는 것은 트리 탭에서 한 곳에서만 한다.
        pobPane.pobTreeCanvas.editable = false
        pobPane.pobTreeCanvas.onNodeTap = TreeCanvasView.OnNodeTap { id, allocated ->
            showPobTreePick(id, allocated)
        }
        pobPane.pobTreeCanvas.onDrawProblem = { t ->
            Log.e(LOG_TAG, "패시브 탭 트리를 그리는 중 일부를 건너뛰었습니다", t)
        }
    }

    /**
     * PoB 탭 패시브 화면에 트리를 그린다.
     *
     * 불러온 빌드가 있으면 그 캐릭터의 직업·전직·찍은 노드를 그대로 그리고,
     * 없으면 고른 직업의 시작 노드에서 펼친다.
     */
    private fun bindPobTree(build: PobBuild?) {
        val tree = Poe2Tree.of(this)
        if (tree.isEmpty()) return
        val canvas = pobPane.pobTreeCanvas
        pobPane.tvPobTreePick.isVisible = false
        if (build != null && build.treeNodeIds.isNotEmpty()) {
            val classIndex = tree.classIndex(build.className, build.treeClassId)
            val ascendIndex = if (classIndex >= 0) {
                tree.ascendancyIndex(classIndex, build.ascendClassName, build.treeAscendClassId)
            } else 0
            canvas.bindLevel = build.level
            canvas.bind(tree, build.treeNodeIds, build.treeMastery.keys.toList(), classIndex, ascendIndex)
        } else {
            canvas.bindLevel = 0
            canvas.bindDefault(tree, treeClassPick, treeAscendPick)
        }
    }

    /** PoB 탭 트리에서 노드를 눌렀을 때 — 이름·상태·설명을 트리 아래에 짧게 적는다. */
    private fun showPobTreePick(id: Int, allocated: Boolean) {
        val canvas = pobPane.pobTreeCanvas
        val name = canvas.nodeName(id).ifBlank { getString(R.string.tree_node_generic) }
        val stats = canvas.nodeStats(id).trim()
        val state = getString(if (allocated) R.string.tree_node_allocated else R.string.tree_node_free)
        val text = buildString {
            append(name).append(" · ").append(state)
            if (stats.isNotBlank()) append("\n").append(stats)
        }
        pobPane.tvPobTreePick.text = text
        pobPane.tvPobTreePick.isVisible = true
    }

    /**
     * 패시브 칩: 불러온 빌드가 있으면 그 캐릭터의 트리, 없으면 직업 시작점에서 직접 찍어 보는 트리.
     * 어느 쪽이든 앱이 노드와 연결선을 직접 그린다 (poe2db 화면을 띄우지 않는다).
     */
    private fun showPassiveTree() {
        val tree = Poe2Tree.of(this)
        if (tree.isEmpty()) {
            Snackbar.make(binding.root, R.string.tree_no_data, Snackbar.LENGTH_LONG).show()
            return
        }
        val build = pobBuild
        if (build != null && build.treeNodeIds.isNotEmpty()) {
            showBuildTree()
        } else {
            bindDefaultTree(tree)
        }
    }

    /**
     * 불러온 캐릭터의 패시브 트리를 앱 안에서 그린다 (직업·전직·찍은 노드 그대로).
     */
    private fun showBuildTree() {
        val build = pobBuild
        if (build == null || build.treeNodeIds.isEmpty()) {
            showPassiveTree()
            return
        }
        try {
            bindBuildTree(build)
        } catch (t: Throwable) {
            Log.e(LOG_TAG, "불러온 빌드의 패시브 트리를 그리지 못했습니다", t)
            // 실패해도 화면을 poe2db로 바꾸지 않는다. 다시 시도할 수 있게만 알린다.
            runCatching {
                Snackbar.make(
                    binding.root,
                    getString(R.string.tree_render_failed, " (${t.javaClass.simpleName})"),
                    Snackbar.LENGTH_LONG,
                ).show()
            }
        }
    }

    /** 트리 화면을 실제로 채운다 (showBuildTree의 본체) */
    private fun bindBuildTree(build: PobBuild) {
        val tree = Poe2Tree.of(this)
        if (tree.isEmpty()) {
            Snackbar.make(binding.root, R.string.tree_no_data, Snackbar.LENGTH_LONG).show()
            return
        }
        treePane.treeCanvas.isVisible = true
        treePane.treeCanvasBar.isVisible = true
        treePane.treeNodeCard.isVisible = false
        treePane.chipPassiveTree.isChecked = true
        val classIndex = tree.classIndex(build.className, build.treeClassId)
        val ascendIndex = if (classIndex >= 0) {
            tree.ascendancyIndex(classIndex, build.ascendClassName, build.treeAscendClassId)
        } else 0
        treeClassPick = classIndex
        treeAscendPick = ascendIndex
        treePane.treeCanvas.bindLevel = build.level
        treePane.treeCanvas.bind(tree, build.treeNodeIds, build.treeMastery.keys.toList(), classIndex, ascendIndex)
        // 직업 선택 줄은 트리 위에 늘 보이게 먼저 채운다.
        // (포인트·정신력 계산이 실패해도 직업 줄과 트리는 그대로 보여야 한다.)
        runCatching { buildClassRow() }
            .onFailure { Log.e(LOG_TAG, "직업 선택 줄을 채우지 못했습니다", it) }
        treePane.treeClassScroll.isVisible = treePane.treeClassRow.childCount > 0
        treePane.tvTreeClassTitle.isVisible = treePane.treeClassScroll.isVisible
        if (treePane.treeClassScroll.isVisible) {
            scrollClassRowToPick(treePane.treeClassRow, treePane.treeClassScroll)
        }
        runCatching { updateTreeInfo() }
            .onFailure { Log.e(LOG_TAG, "트리 정보 줄을 갱신하지 못했습니다", it) }
        runCatching { refreshTreePoints() }
            .onFailure { Log.e(LOG_TAG, "트리 포인트를 갱신하지 못했습니다", it) }
        runCatching { bindPobTree(build) }
            .onFailure { Log.e(LOG_TAG, "패시브 탭 트리를 그리지 못했습니다", it) }
        binding.bottomNav.selectedItemId = R.id.nav_tree
    }

    /** 빌드 없이 트리를 펼친다 — 직업 시작점에서 둘러보고 직접 찍어 볼 수 있다. */
    private fun bindDefaultTree(tree: TreeDict) {
        if (tree.classes.isEmpty()) {
            Snackbar.make(binding.root, R.string.tree_no_data, Snackbar.LENGTH_LONG).show()
            return
        }
        if (treeClassPick !in tree.classes.indices) treeClassPick = 0
        treePane.treeCanvas.isVisible = true
        treePane.treeCanvasBar.isVisible = true
        treePane.treeNodeCard.isVisible = false
        treePane.chipPassiveTree.isChecked = true
        treePane.treeCanvas.bindLevel = 0
        treePane.treeCanvas.bindDefault(tree, treeClassPick, treeAscendPick)
        runCatching { buildClassRow() }
            .onFailure { Log.e(LOG_TAG, "직업 선택 줄을 채우지 못했습니다", it) }
        treePane.treeClassScroll.isVisible = treePane.treeClassRow.childCount > 0
        treePane.tvTreeClassTitle.isVisible = treePane.treeClassScroll.isVisible
        if (treePane.treeClassScroll.isVisible) {
            scrollClassRowToPick(treePane.treeClassRow, treePane.treeClassScroll)
        }
        runCatching { updateTreeInfo() }
            .onFailure { Log.e(LOG_TAG, "트리 정보 줄을 갱신하지 못했습니다", it) }
        runCatching { refreshTreePoints() }
            .onFailure { Log.e(LOG_TAG, "트리 포인트를 갱신하지 못했습니다", it) }
        runCatching { bindPobTree(null) }
            .onFailure { Log.e(LOG_TAG, "패시브 탭 트리를 그리지 못했습니다", it) }
        binding.bottomNav.selectedItemId = R.id.nav_tree
    }

    /** 아틀라스 트리도 앱 안에서 그린다. */
    private fun showAtlasTree() {
        val atlas = TreeAtlas.of(this)
        if (atlas.isEmpty()) {
            Snackbar.make(binding.root, R.string.tree_no_data, Snackbar.LENGTH_LONG).show()
            return
        }
        treePane.treeCanvas.isVisible = true
        treePane.treeCanvasBar.isVisible = true
        treePane.treeClassScroll.isVisible = false
        treePane.tvTreeClassTitle.isVisible = false
        treePane.treeNodeCard.isVisible = false
        // 카운터를 그리기 전에 아틀라스 모드로 바꿔 둔다 (패시브 카운터가 잠깐 보이지 않게).
        treePane.chipAtlasTree.isChecked = true
        treePane.treeCanvas.bindAtlas(atlas)
        treePane.tvTreeCanvasInfo.text = getString(R.string.tree_atlas_points, 0, atlas.totalPoints)
        refreshTreePoints()
        binding.bottomNav.selectedItemId = R.id.nav_tree
    }

    /** 트리 위쪽의 직업·전직 안내 줄 */
    private fun updateTreeInfo() {
        if (!canvasVisible()) return
        // 아틀라스 트리에는 직업·전직이 없다.
        if (treePane.chipAtlasTree.isChecked) return
        val tree = Poe2Tree.of(this)
        val cls = tree.classes.getOrNull(treeClassPick) ?: return
        val classKo = cls.nameKo.ifBlank { cls.nameEn }
        val ascendKo = cls.ascendancies.getOrNull(treeAscendPick - 1)?.second.orEmpty()
        val count = treePane.treeCanvas.allocatedIds().size
        treePane.tvTreeCanvasInfo.text = if (ascendKo.isNotBlank() && ascendKo != "WIP") {
            getString(R.string.tree_canvas_info_ascend, classKo, ascendKo, count)
        } else {
            getString(R.string.tree_canvas_info, classKo, count)
        }
    }

    /** 찍은 포인트를 위쪽 줄에 다시 그린다. */
    private fun refreshTreePoints() {
        renderTreePoints(treePane.treeCanvas.points())
        updateTreeInfo()
    }

    /** 패시브 20 / 123 · 전직 2 / 8 ( · 정신력 90 / 130) */
    private fun renderTreePoints(points: TreePoints) {
        // 아틀라스는 패시브·전직이 없으므로 총점만 적는다.
        if (treePane.chipAtlasTree.isChecked) {
            treePane.tvTreePoints.text = getString(R.string.tree_atlas_points, points.allocated, points.total)
            treePane.tvTreePoints.isVisible = true
            return
        }
        val builder = android.text.SpannableStringBuilder()
        fun add(label: String, value: String, color: Int) {
            if (builder.isNotEmpty()) builder.append("   ")
            val start = builder.length
            builder.append("$label $value")
            builder.setSpan(
                android.text.style.ForegroundColorSpan(color), start, builder.length,
                android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
        }
        add(
            getString(R.string.tree_points_passive),
            if (points.total > 0) "${points.allocated} / ${points.total}" else "${points.allocated}",
            if (points.total > 0 && points.allocated >= points.total) Color.parseColor("#E8C060") else Color.parseColor("#ECE8E1"),
        )
        if (points.ascendancyTotal > 0) {
            add(
                getString(R.string.tree_points_ascend),
                "${points.ascendancy} / ${points.ascendancyTotal}",
                Color.parseColor("#C9B47A"),
            )
        }
        // 정신력 점유 (정신력 젬 + 전령)
        val build = pobBuild
        val spirit = build?.stats?.get("Spirit")?.takeIf { it.isFinite() && it > 0.0 }
        if (build != null && spirit != null) {
            val plan = SpiritBuffs.plan(build, SpiritBuffs.catalog(this), krDict)
            val heralds = Heralds.plan(build, krDict, plan.reserved)
            val used = plan.reserved + heralds.reserved
            add(
                getString(R.string.tree_points_spirit),
                "${used.toInt()} / ${spirit.toInt()}",
                Color.parseColor("#8CE8C0"),
            )
        }
        treePane.tvTreePoints.text = builder
        treePane.tvTreePoints.isVisible = builder.isNotEmpty()
    }

    /** 노드 이름으로 트리에서 찾는다. */
    private fun showTreeSearch() {
        if (treePane.treeCanvas.isEmpty()) {
            showPassiveTree()
            if (treePane.treeCanvas.isEmpty()) return
        }
        val input = android.widget.EditText(this).apply {
            hint = getString(R.string.tree_search_hint)
            setTextColor(Color.parseColor("#ECE8E1"))
            setHintTextColor(Color.parseColor("#79808C"))
            setSingleLine(true)
            imeOptions = EditorInfo.IME_ACTION_SEARCH
        }
        val results = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, (8 * resources.displayMetrics.density).toInt(), 0, 0)
        }
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.tree_search)
            .setView(LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding((18 * resources.displayMetrics.density).toInt(), 0, (18 * resources.displayMetrics.density).toInt(), 0)
                addView(input)
                addView(results)
            })
            .setNegativeButton(R.string.pob_close, null)
            .create()
        fun fill(query: String) {
            results.removeAllViews()
            val hits = treePane.treeCanvas.find(query, 8)
            if (query.isNotBlank() && hits.isEmpty()) {
                results.addView(TextView(this).apply {
                    text = getString(R.string.tree_search_empty)
                    setTextColor(Color.parseColor("#79808C"))
                    textSize = 12f
                })
            }
            hits.forEach { hit ->
                results.addView(TextView(this).apply {
                    text = hit.name
                    setTextColor(Color.parseColor("#F0DCA8"))
                    textSize = 13f
                    setPadding(0, (10 * resources.displayMetrics.density).toInt(), 0, (4 * resources.displayMetrics.density).toInt())
                    setOnClickListener {
                        treePane.treeCanvas.focusNode(hit.id)
                        showTreeNode(hit.id, treePane.treeCanvas.isAllocated(hit.id))
                        dialog.dismiss()
                    }
                })
            }
        }
        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = fill(s?.toString().orEmpty())
            override fun afterTextChanged(s: Editable?) = Unit
        })
        input.setOnEditorActionListener { _, action, _ ->
            if (action == EditorInfo.IME_ACTION_SEARCH) {
                val hit = treePane.treeCanvas.find(input.text.toString(), 1).firstOrNull()
                if (hit != null) {
                    treePane.treeCanvas.focusNode(hit.id)
                    showTreeNode(hit.id, treePane.treeCanvas.isAllocated(hit.id))
                    dialog.dismiss()
                }
                true
            } else false
        }
        dialog.show()
        input.requestFocus()
    }

    private var treeClassPick = 0
    private var treeAscendPick = 1

    /** 직업 12줄 + 그 직업의 전직을 버튼으로 채운다. (트리 화면 위쪽 · 패시브 탭 위쪽 두 곳) */
    private fun buildClassRow() {
        fillClassButtons(treePane.treeClassRow, withAscendancy = true)
        fillClassButtons(pobPane.pobTreeClassRow, withAscendancy = false)
    }

    /** 직업·전직 버튼을 한 줄에 채운다. */
    private fun fillClassButtons(row: LinearLayout, withAscendancy: Boolean) {
        val tree = Poe2Tree.of(this)
        row.removeAllViews()
        if (tree.isEmpty()) return
        val pad = (5 * resources.displayMetrics.density).toInt()
        // 한 직업 버튼이 실패해도 나머지 줄은 그린다.
        tree.classes.forEachIndexed { index, cls ->
            // 시작 노드가 없는 직업은 트리에서 찍을 수 없으므로 내보내지 않는다.
            if (!tree.hasStartNode(index)) return@forEachIndexed
            runCatching {
                val name = cls.nameKo.ifBlank { cls.nameEn }.ifBlank { getString(R.string.tree_class_unknown) }
                val button = classPickButton(name, index == treeClassPick, { pickTreeClass(index) }, pad)
                button.tag = index
                row.addView(button)
            }.onFailure { Log.e(LOG_TAG, "직업 버튼을 만들지 못했습니다 (${cls.nameEn})", it) }
        }
        if (!withAscendancy) {
            if (row.childCount == 0) {
                row.addView(android.widget.TextView(this).apply {
                    text = getString(R.string.tree_class_unknown)
                    setTextColor(mutedColor())
                    textSize = 12f
                    setPadding(pad, pad, pad, pad)
                })
            }
            return
        }
        // 고른 직업의 전직 세 줄
        val cls = tree.classes.getOrNull(treeClassPick)
        cls?.ascendancies?.forEachIndexed { order, ascend ->
            val (_, ko) = ascend
            // 아직 공개되지 않은 전직(WIP)은 한국어 이름이 없으므로 목록에서 뺀다.
            if (ko.isBlank() || ko == "WIP") return@forEachIndexed
            runCatching {
                row.addView(classPickButton("↳ $ko", treeAscendPick == order + 1) {
                    treeAscendPick = order + 1
                    treePane.treeCanvas.setClass(tree, treeClassPick, treeAscendPick)
                    buildClassRow()
                    updateTreeInfo()
                    refreshTreePoints()
                }, pad)
            }.onFailure { Log.e(LOG_TAG, "전직 버튼을 만들지 못했습니다 ($ko)", it) }
        }
        if (row.childCount == 0) {
            row.addView(android.widget.TextView(this).apply {
                text = getString(R.string.tree_class_unknown)
                setTextColor(mutedColor())
                textSize = 12f
                setPadding(pad, pad, pad, pad)
            })
        }
    }

    /** 고른 직업 버튼이 줄 밖에 있으면 그 자리로 민다. */
    private fun scrollClassRowToPick(row: LinearLayout, scroll: android.widget.HorizontalScrollView) {
        val child = (0 until row.childCount)
            .map { row.getChildAt(it) }
            .firstOrNull { it.tag == treeClassPick } ?: return
        scroll.post { scroll.smoothScrollTo(maxOf(0, child.left - 12), 0) }
    }

    /**
     * 직업을 골랐을 때.
     *
     * 불러온 빌드와 같은 직업이면 그 빌드의 트리를 그대로 보여 주고, 다른 직업이면
     * 그 직업의 시작 노드에서 새로 찍을 수 있게 트리 화면을 연다.
     */
    private fun pickTreeClass(index: Int) {
        val tree = Poe2Tree.of(this)
        if (tree.isEmpty()) return
        val sameClass = index == treeClassPick && !treePane.treeCanvas.isEmpty()
        val build = pobBuild
        val buildClass = if (build != null) tree.classIndex(build.className, build.treeClassId) else -1
        treeClassPick = index
        if (!sameClass) treeAscendPick = 1
        when {
            // 이미 그 직업으로 보고 있으면 시작 노드로만 화면을 옮긴다.
            sameClass -> {
                binding.bottomNav.selectedItemId = R.id.nav_tree
                treePane.treeCanvas.focusStart()
                updateTreeInfo()
            }
            build != null && buildClass == index && build.treeNodeIds.isNotEmpty() -> showBuildTree()
            else -> {
                binding.bottomNav.selectedItemId = R.id.nav_tree
                bindDefaultTree(tree)
            }
        }
        buildClassRow()
        if (treePane.treeClassScroll.isVisible) {
            scrollClassRowToPick(treePane.treeClassRow, treePane.treeClassScroll)
        }
    }

    private fun classPickButton(label: String, checked: Boolean, onClick: () -> Unit): android.widget.CheckedTextView =
        android.widget.CheckedTextView(this).apply {
            text = label
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor(if (checked) "#F0DCA8" else "#A9AFBA"))
            background = ContextCompat.getDrawable(this@MainActivity, R.drawable.bg_tree_chip)
            isChecked = checked
            setPadding((10 * resources.displayMetrics.density).toInt(), (5 * resources.displayMetrics.density).toInt(),
                (10 * resources.displayMetrics.density).toInt(), (5 * resources.displayMetrics.density).toInt())
            val params = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            params.marginEnd = (5 * resources.displayMetrics.density).toInt()
            layoutParams = params
            setOnClickListener { onClick() }
        }

    private fun classPickButton(
        label: String,
        checked: Boolean,
        onClick: () -> Unit,
        pad: Int,
    ): android.widget.CheckedTextView =
        classPickButton(label, checked, onClick).apply {
            val params = layoutParams as LinearLayout.LayoutParams
            params.marginEnd = pad
            layoutParams = params
        }

    /** 스탯 요약 — 전투 탭과 같은 수치를 짧게 보여 준다. */
    private fun showTreeStats() {
        val stats = battleStats()
        if (stats == null) {
            Snackbar.make(binding.root, R.string.battle_need_build, Snackbar.LENGTH_SHORT).show()
            return
        }
        val text = buildString {
            append(getString(R.string.tree_stat_dps)).append(' ')
            append(BattleWorld.formatShort(stats.dps))
            append("\n").append(getString(R.string.tree_stat_life)).append(' ')
            append(BattleWorld.formatShort(stats.life))
            append(" · ").append(getString(R.string.tree_stat_es)).append(' ')
            append(BattleWorld.formatShort(stats.es))
            append("\n").append(getString(R.string.tree_stat_evade)).append(' ')
            append(String.format(Locale.KOREA, "%.1f", stats.evade)).append("%")
            append(" · ").append(getString(R.string.tree_stat_phys)).append(' ')
            append(BattleWorld.formatShort(stats.physRed))
            append("\n").append(getString(R.string.tree_stat_res))
            append(" 화 ").append(stats.fireRes.toInt()).append(" / 빙 ")
            append(stats.coldRes.toInt()).append(" / 번 ").append(stats.lightRes.toInt())
            append(" / 혼 ").append(stats.chaosRes.toInt())
            append("\n").append(getString(R.string.tree_stat_crit)).append(' ')
            append(String.format(Locale.KOREA, "%.1f", stats.critChance)).append("%")
            append(" · ").append(String.format(Locale.KOREA, "%.2f", stats.aps)).append("/s")
            if (stats.spiritBuffs.active.isNotEmpty()) {
                append("\n\n").append(stats.spiritBuffs.summary)
            }
            if (stats.ascendSummary.isNotBlank()) {
                append("\n").append(stats.ascendSummary)
            }
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.tree_stats)
            .setMessage(text)
            .setPositiveButton(R.string.pob_close, null)
            .show()
    }

    /** 트리 탭으로 옮겨 앱 안의 아틀라스 트리를 그린다. */
    private fun openNativeAtlas() {
        binding.bottomNav.selectedItemId = R.id.nav_tree
        showAtlasTree()
    }

    /** 트리에서 노드를 누르면 한글 설명을 띄운다. */
    private fun showTreeNode(id: Int, allocated: Boolean) {
        val tree = Poe2Tree.of(this)
        val node = tree.node(id)
        val kind = when {
            node == null -> getString(R.string.tree_node_generic)
            node.isKeystone -> getString(R.string.pob_tree_group_keystone)
            node.isAscendancy -> getString(R.string.pob_tree_group_ascendancy)
            node.isMastery -> getString(R.string.pob_tree_group_mastery)
            node.isNotable -> getString(R.string.pob_tree_group_notable)
            else -> getString(R.string.tree_node_generic)
        }
        val state = getString(if (allocated) R.string.tree_node_allocated else R.string.tree_node_free)
        treePane.tvTreeNodeName.text = node?.displayName.orEmpty().ifBlank { getString(R.string.tree_node_generic) }
        treePane.tvTreeNodeKind.text = "$kind · $state"
        val stats = node?.stats.orEmpty().trim()
        treePane.tvTreeNodeStats.text = stats
        treePane.tvTreeNodeStats.isVisible = stats.isNotBlank()
        treePane.treeNodeCard.isVisible = true
    }

    // ------------------------------------------------------------- PoB 실험실
    @SuppressLint("SetJavaScriptEnabled")
    private fun setupPob() {
        setupPobTree()
        pobPane.pobTabs.tabMode = TabLayout.MODE_FIXED
        pobPane.pobTabs.addTab(pobPane.pobTabs.newTab().setText(R.string.pob_tab_overview))
        pobPane.pobTabs.addTab(pobPane.pobTabs.newTab().setText(R.string.pob_tab_skills))
        pobPane.pobTabs.addTab(pobPane.pobTabs.newTab().setText(R.string.pob_tab_items))
        pobPane.pobTabs.addTab(pobPane.pobTabs.newTab().setText(R.string.pob_tab_config))
        pobPane.pobTabs.addTab(pobPane.pobTabs.newTab().setText(R.string.pob_tab_tree))
        pobPane.pobTabs.addTab(pobPane.pobTabs.newTab().setText(R.string.pob_tab_battle))
        pobPane.pobTabs.addTab(pobPane.pobTabs.newTab().setText(R.string.pob_tab_meta))
        pobPane.pobTabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                val page = when (tab.position) {
                    1 -> pobPane.pobPageSkills
                    2 -> pobPane.pobPageItems
                    3 -> pobPane.pobPageConfig
                    4 -> pobPane.pobPageTree
                    5 -> pobPane.pobPageBattle
                    6 -> pobPane.pobPageMeta
                    else -> pobPane.pobPageOverview
                }
                listOf(
                    pobPane.pobPageOverview, pobPane.pobPageSkills, pobPane.pobPageItems,
                    pobPane.pobPageConfig, pobPane.pobPageTree, pobPane.pobPageBattle,
                    pobPane.pobPageMeta,
                ).forEach { it.isVisible = it === page }
                if (tab.position == 2) refreshSlots()
                if (tab.position == 1) renderSkills()
                if (tab.position == 6) renderMeta()
                if (tab.position == 4) {
                    runCatching { buildClassRow() }
                        .onFailure { Log.e(LOG_TAG, "패시브 탭 직업 줄을 채우지 못했습니다", it) }
                    runCatching { bindPobTree(pobBuild) }
                        .onFailure { Log.e(LOG_TAG, "패시브 탭 트리를 그리지 못했습니다", it) }
                }
                if (tab.position == 5) {
                    if (battleReady) {
                        if (battleWantsPlay) battleView.resume() else battleView.refresh()
                    }
                } else if (battleReady) {
                    battleView.pause()
                }
            }

            override fun onTabUnselected(tab: TabLayout.Tab) = Unit
            override fun onTabReselected(tab: TabLayout.Tab) = Unit
        })

        pobPane.tvGearValue.setOnClickListener {
            gearExpanded = !gearExpanded
            renderGearValue()
        }
        pobPane.btnPobLoad.setOnClickListener { showPobLoadDialog() }
        pobPane.btnPobSample.setOnClickListener {
            loadPob(Pob.sampleCode(), null)
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

        pobPane.btnOpenNinjaBuilds.setOnClickListener { showNinjaBuilds() }
        pobPane.btnOpenTree.setOnClickListener { showBuildTree() }
        pobPane.btnOpenAtlas.setOnClickListener { openNativeAtlas() }

        setupBattle()
    }

    // ------------------------------------------------------------- 메타 랭킹
    private fun renderMeta() {
        val density = resources.displayMetrics.density
        val rows = pobPane.metaAscRows
        rows.removeAllViews()
        val maxShare = MetaSnapshot.ASCENDANCIES.maxOfOrNull { it.second } ?: 1.0
        MetaSnapshot.ASCENDANCIES.forEachIndexed { index, (name, share) ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, (3 * density).toInt(), 0, (7 * density).toInt())
            }
            val head = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            val label = TextView(this).apply {
                text = "%d. %s".format(index + 1, name)
                setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
                textSize = 12f
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            val pct = TextView(this).apply {
                text = "%.0f%%".format(share)
                setTextColor(
                    if (index == 0) ContextCompat.getColor(context, R.color.gold)
                    else ContextCompat.getColor(context, R.color.text_primary)
                )
                textSize = 12f
                gravity = Gravity.END
                setPadding((10 * density).toInt(), 0, 0, 0)
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )
            }
            head.addView(label)
            head.addView(pct)

            val track = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, (9 * density).toInt()
                ).apply { topMargin = (4 * density).toInt() }
                setBackgroundColor(ContextCompat.getColor(context, R.color.divider))
            }
            val filled = android.view.View(this).apply {
                setBackgroundColor(
                    if (index == 0) ContextCompat.getColor(context, R.color.gold)
                    else ContextCompat.getColor(context, R.color.gold_40)
                )
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, share.toFloat())
            }
            val empty = android.view.View(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.MATCH_PARENT,
                    (maxShare - share).toFloat().coerceAtLeast(0.001f),
                )
            }
            track.addView(filled)
            track.addView(empty)

            row.addView(head)
            row.addView(track)
            rows.addView(row)
        }

        val cards = pobPane.metaBuildCards
        cards.removeAllViews()
        MetaSnapshot.BUILDS.forEach { build ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(ContextCompat.getColor(context, R.color.surface))
                setPadding((12 * density).toInt(), (10 * density).toInt(), (12 * density).toInt(), (10 * density).toInt())
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = (6 * density).toInt() }
            }
            card.addView(TextView(this).apply {
                text = "${build.skill}  ·  ${build.dps} DPS"
                setTextColor(ContextCompat.getColor(context, R.color.text_primary))
                textSize = 13f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            })
            card.addView(TextView(this).apply {
                text = build.ascendancy
                setTextColor(ContextCompat.getColor(context, R.color.gold_soft))
                textSize = 11f
            })
            card.addView(TextView(this).apply {
                text = build.note
                setTextColor(ContextCompat.getColor(context, R.color.text_muted))
                textSize = 11f
                setPadding(0, (3 * density).toInt(), 0, 0)
            })
            cards.addView(card)
        }

        pobPane.tvMetaSource.text = getString(
            R.string.meta_source_label,
            MetaSnapshot.SOURCE,
            MetaSnapshot.UPDATED,
        ) + " · " + MetaSnapshot.LEAGUE
    }

    private fun showNinjaBuilds() {
        val density = resources.displayMetrics.density
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(ContextCompat.getColor(context, R.color.bg))
        }
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((14 * density).toInt(), (10 * density).toInt(), (6 * density).toInt(), (10 * density).toInt())
            setBackgroundColor(ContextCompat.getColor(context, R.color.surface))
        }
        bar.addView(TextView(this).apply {
            text = getString(R.string.meta_web_title)
            setTextColor(ContextCompat.getColor(context, R.color.text_primary))
            textSize = 14f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        val open = com.google.android.material.button.MaterialButton(this).apply {
            text = getString(R.string.detail_open_browser)
            textSize = 11f
            minHeight = (34 * density).toInt()
            insetTop = 0
            insetBottom = 0
            setOnClickListener { openExternal(Uri.parse(NINJA_BUILDS_URL)) }
        }
        bar.addView(open)
        val close = com.google.android.material.button.MaterialButton(this).apply {
            text = getString(R.string.info_close)
            textSize = 11f
            minHeight = (34 * density).toInt()
            insetTop = 0
            insetBottom = 0
        }
        bar.addView(close)

        val web = WebView(this)
        with(web.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            useWideViewPort = true
            loadWithOverviewMode = true
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            textZoom = 100
            userAgentString = "$userAgentString PoE2DBKR/1.0"
        }
        web.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) = Unit
        }
        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val host = request.url.host.orEmpty()
                if (host.endsWith("poe.ninja") || host.endsWith("pathofexile.com")) return false
                openExternal(request.url)
                return true
            }

            override fun onPageFinished(view: WebView, url: String) {
                if (!url.startsWith("http")) return
                view.evaluateJavascript(MobileCss.NINJA_SCRIPT, null)
            }
        }
        web.loadUrl(NINJA_BUILDS_URL)
        root.addView(bar)
        root.addView(
            web,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        )

        val dialog = android.app.Dialog(this)
        dialog.setContentView(root)
        dialog.setCanceledOnTouchOutside(false)
        close.setOnClickListener {
            web.stopLoading()
            web.destroy()
            dialog.dismiss()
        }
        dialog.show()
        dialog.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        )
    }

    // ------------------------------------------------------------- 전투 시뮬레이터
    private fun setupBattle() {
        battleView = pobPane.battleCanvas
        battleView.onFinished = { result -> showBattleResult(result) }

        buildBattleModeChips()
        buildBattleOptChips()
        buildBattleSkillChips()
        buildBattleTriggerChips()
        buildBattleSpiritChips()
        buildBattleMetaChips()

        pobPane.btnBattleCombo.setOnClickListener { showComboDialog() }

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

    private fun battleChip(
        label: String,
        checked: Boolean,
        kind: BattleChipKind = BattleChipKind.PICK,
        onPick: (Chip) -> Unit,
    ): Chip {
        val chip = Chip(this)
        chip.id = View.generateViewId()
        chip.text = label
        chip.isCheckable = true
        chip.isChecked = checked
        chip.setOnClickListener { view ->
            val c = view as Chip
            // 누르는 순간 칩이 상태를 먼저 뒤집었으므로, 누르기 전 상태는 지금의 반대다.
            val before = !c.isChecked
            val after = BattleChipRule.stateAfterClick(kind, before)
            // 배경·테두리·글자색은 체크 상태를 따라가므로 여기서 바로 켜짐/꺼짐이 보인다.
            if (c.isChecked != after) c.isChecked = after
            if (after != before) onPick(c)
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
            listOf(10, 25, 50, 100, 200, 300, 500).forEach { count ->
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
                battleChip(getString(R.string.battle_map_amp), battleAmp, BattleChipKind.TOGGLE) { chip ->
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

    /**
     * 메인 스킬 선택 줄 — 불러온 빌드에 실제로 켜져 있는 액티브 스킬 중 하나를 고른다.
     * 고른 스킬 하나로 전투하고, 여러 스킬을 섞어 쓰지 않는다.
     */
    private fun buildBattleSkillChips() {
        val group = pobPane.battleSkillChips
        group.removeAllViews()
        val options = BattleSetup.skillOptions(pobBuild, krDict, dpsByName = battleSkillDps())
        // 고른 스킬이 이 빌드에 없으면(다른 빌드를 불러왔거나 스킬을 껐으면) 첫 스킬을 메인으로 잡는다.
        if (battleSkillPick == null || options.none { it.gem == battleSkillPick }) {
            battleSkillPick = options.firstOrNull()?.gem
        }
        pobPane.tvBattleSkillPickTitle.isVisible = options.isNotEmpty()
        options.forEach { option ->
            val chip = battleChip(option.name, battleSkillPick == option.gem) {
                battleSkillPick = option.gem
                markSkillChip(group, option.gem)
                restartBattleIfRunning()
            }
            // 같은 한국어 이름이 겹쳐도 젬 이름으로 구분한다.
            chip.tag = option.gem
            group.addView(chip)
        }
    }

    /** 스킬 칩 중 고른 젬만 체크하고 나머지는 푼다 (tag에 젬 이름) */
    private fun markSkillChip(group: ViewGroup, gem: String?) {
        for (i in 0 until group.childCount) {
            val c = group.getChildAt(i) as Chip
            c.isChecked = c.tag == gem
        }
    }

    private fun restartBattleIfRunning() {
        if (battleStarted) startBattle()
    }

    /** 전투 화면의 콤보 요약 — 사용자가 직접 짠 콤보가 있을 때만 적는다 */
    private fun refreshComboLabel(current: BattlePlayerStats? = null) {
        val stats = current ?: runCatching { battleStats() }.getOrNull()
        if (stats != null) {
            // 빌드를 바꿔 지금 빌드에 없는 스킬이 남아 있으면 콤보에서 조용히 뺀다.
            val options = BattleCombos.options(pobBuild, krDict)
            if (options.isNotEmpty()) {
                val gems = options.map { it.gem.trim().lowercase() }.toSet()
                val labels = options.map { it.label }.toSet()
                val filtered = comboPicks.filter { pick ->
                    pick.gem.trim().lowercase() in gems ||
                        pick.label in labels ||
                        pick.label == stats.skillName
                }
                if (filtered.size != comboPicks.size) {
                    comboPicks = filtered
                    comboStore.save(filtered)
                }
            }
        }
        if (comboPicks.isEmpty()) {
            pobPane.tvBattleCombo.isVisible = false
            pobPane.tvBattleCombo.text = ""
        } else {
            pobPane.tvBattleCombo.isVisible = true
            pobPane.tvBattleCombo.text = getString(R.string.battle_combo_line, BattleCombos.summary(comboPicks))
        }
    }

    /**
     * 콤보 설정 시트.
     *
     * 빌드에 장착된 스킬을 눌러 순서를 쌓고, 같은 스킬을 여러 번 넣을 수 있다.
     * 바뀔 때마다 저장하고 전투 중이면 그 자리에서 다시 시작해 바로 확인할 수 있게 한다.
     */
    private fun showComboDialog() {
        val stats = battleStats()
        if (stats == null) {
            Snackbar.make(binding.root, R.string.battle_need_build, Snackbar.LENGTH_SHORT).show()
            return
        }
        val density = resources.displayMetrics.density
        val options = BattleCombos.options(pobBuild, krDict, battleSkillDps())
        if (options.isEmpty()) {
            Snackbar.make(binding.root, R.string.battle_need_build, Snackbar.LENGTH_SHORT).show()
            return
        }
        val work = ArrayList(comboPicks)

        // 손가락으로 누르기 편하게: 누를 수 있는 것은 모두 44dp 이상으로 잡고,
        // 누르는 동안 살짝 줄어들어 눌린 것이 눈에 보이게 한다.
        fun rippleBackground(view: android.view.View) {
            val value = android.util.TypedValue()
            theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, value, true)
            if (value.resourceId != 0) view.setBackgroundResource(value.resourceId)
        }
        fun pressable(view: android.view.View) {
            view.isClickable = true
            view.isFocusable = true
            view.setOnTouchListener { v, event ->
                when (event.actionMasked) {
                    android.view.MotionEvent.ACTION_DOWN ->
                        v.animate().scaleX(0.94f).scaleY(0.94f).setDuration(80).start()
                    android.view.MotionEvent.ACTION_UP,
                    android.view.MotionEvent.ACTION_CANCEL ->
                        v.animate().scaleX(1f).scaleY(1f).setDuration(110).start()
                }
                false
            }
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((18 * density).toInt(), (4 * density).toInt(), (18 * density).toInt(), (6 * density).toInt())
        }
        root.addView(TextView(this).apply {
            text = getString(R.string.battle_combo_hint)
            setTextColor(ContextCompat.getColor(context, R.color.text_muted))
            textSize = 11f
            setLineSpacing(0f, 1.15f)
        })
        root.addView(TextView(this).apply {
            text = getString(R.string.battle_combo_sequence)
            setTextColor(ContextCompat.getColor(context, R.color.gold))
            textSize = 12f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(0, (10 * density).toInt(), 0, (4 * density).toInt())
        })
        val seqColumn = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(seqColumn)

        root.addView(TextView(this).apply {
            text = getString(R.string.battle_combo_add)
            setTextColor(ContextCompat.getColor(context, R.color.gold))
            textSize = 12f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(0, (12 * density).toInt(), 0, (2 * density).toInt())
        })
        val chipRow = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        root.addView(chipRow)

        val scroll = android.widget.ScrollView(this).apply {
            addView(root)
        }

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.battle_combo_title)
            .setView(scroll)
            .setPositiveButton(R.string.battle_combo_close, null)
            .create()

        fun apply() {
            comboPicks = work.toList()
            if (comboPicks.isEmpty()) comboStore.clear() else comboStore.save(comboPicks)
            refreshComboLabel(stats)
            restartBattleIfRunning()
        }

        lateinit var render: () -> Unit
        render = {
            seqColumn.removeAllViews()
            if (work.isEmpty()) {
                seqColumn.addView(TextView(this).apply {
                    text = getString(R.string.battle_combo_empty)
                    setTextColor(ContextCompat.getColor(context, R.color.text_muted))
                    textSize = 11f
                    setPadding(0, (4 * density).toInt(), 0, (4 * density).toInt())
                })
            }
            work.forEachIndexed { index, pick ->
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(0, (3 * density).toInt(), 0, (3 * density).toInt())
                }
                row.addView(TextView(this).apply {
                    text = "${index + 1}"
                    setTextColor(ContextCompat.getColor(context, R.color.gold_soft))
                    textSize = 11f
                    gravity = Gravity.CENTER
                    width = (22 * density).toInt()
                })
                row.addView(TextView(this).apply {
                    text = pick.label + if (pick.repeats > 1) "  x${pick.repeats}" else ""
                    setTextColor(ContextCompat.getColor(context, R.color.text_primary))
                    textSize = 13.5f
                    maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.END
                    setPadding((4 * density).toInt(), (10 * density).toInt(), (4 * density).toInt(), (10 * density).toInt())
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                })
                fun smallButton(label: String, description: String, action: () -> Unit) {
                    row.addView(TextView(this).apply {
                        text = label
                        contentDescription = description
                        setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
                        textSize = 16f
                        gravity = Gravity.CENTER
                        minWidth = (46 * density).toInt()
                        minHeight = (46 * density).toInt()
                        setPadding((12 * density).toInt(), (10 * density).toInt(), (12 * density).toInt(), (10 * density).toInt())
                        rippleBackground(this)
                        pressable(this)
                        setOnClickListener {
                            action()
                            render()
                            apply()
                        }
                    })
                }
                smallButton("▲", getString(R.string.battle_combo_up)) {
                    if (index > 0) {
                        val item = work.removeAt(index)
                        work.add(index - 1, item)
                    }
                }
                smallButton("▼", getString(R.string.battle_combo_down)) {
                    if (index < work.size - 1) {
                        val item = work.removeAt(index)
                        work.add(index + 1, item)
                    }
                }
                smallButton("✕", getString(R.string.battle_combo_remove)) {
                    if (index in work.indices) work.removeAt(index)
                }
                seqColumn.addView(row)
            }

            chipRow.removeAllViews()
            options.forEach { option ->
                val chip = Chip(this)
                chip.id = View.generateViewId()
                chip.text = option.label + (if (option.dps > 0) "  ${dpsText(option.dps)}" else "")
                chip.isCheckable = false
                chip.minHeight = (48 * density).toInt()
                chip.minWidth = (48 * density).toInt()
                chip.textSize = 13.5f
                chip.setPadding((6 * density).toInt(), (8 * density).toInt(), (6 * density).toInt(), (8 * density).toInt())
                pressable(chip)
                chip.setOnClickListener {
                    work.add(option)
                    render()
                    apply()
                }
                chipRow.addView(chip)
            }

        }

        // 처음 열면 빈 순서로 시작한다 — 직접 추가한 순서만 전투에 쓴다.
        render()

        val tools = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, (10 * density).toInt(), 0, 0)
        }
        fun toolButton(label: String, action: () -> Unit) {
            tools.addView(TextView(this).apply {
                text = label
                setTextColor(ContextCompat.getColor(context, R.color.gold))
                textSize = 13f
                gravity = Gravity.CENTER
                minHeight = (48 * density).toInt()
                setPadding((16 * density).toInt(), (12 * density).toInt(), (16 * density).toInt(), (12 * density).toInt())
                setBackgroundResource(R.drawable.bg_search)
                pressable(this)
                setOnClickListener {
                    action()
                    render()
                    apply()
                }
            })
        }
        toolButton(getString(R.string.battle_combo_clear)) {
            work.clear()
        }
        root.addView(tools)
        dialog.show()
    }


    private fun buildBattleTriggerChips() {
        val group = pobPane.battleTriggerChips
        group.removeAllViews()
        // 빌드의 메타젬을 그대로 쓰거나(자동) 무시한다(끔). 가짜 스킬을 끼워 넣는 시험 모드는 두지 않는다.
        listOf(
            TriggerMode.AUTO to getString(R.string.battle_trigger_auto),
            TriggerMode.OFF to getString(R.string.battle_trigger_off),
        ).forEach { (mode, label) ->
            group.addView(
                battleChip(label, battleTriggerMode == mode) {
                    battleTriggerMode = mode
                    for (i in 0 until group.childCount) {
                        val c = group.getChildAt(i) as Chip
                        c.isChecked = c.text.toString() == label
                    }
                    restartBattleIfRunning()
                }
            )
        }
    }

    /**
     * 정신력 점유 칩 — 정신력 젬과 전령을 하나씩 켜고 끈다.
     * 누른 젬은 정신력 점유에서 빠지고, 남은 예산으로 뒤 젬이 켜진다 (수치는 헤더에 곧바로 반영).
     */
    private fun buildBattleSpiritChips() {
        val group = pobPane.battleSpiritChips
        group.removeAllViews()
        val build = pobBuild
        val plan = if (build == null) {
            SpiritBuffPlan.EMPTY
        } else {
            SpiritBuffs.plan(build, SpiritBuffs.catalog(this), krDict, disabled = battleSpiritOff)
        }
        val heralds = if (build == null) {
            HeraldPlan.EMPTY
        } else {
            Heralds.plan(build, krDict, plan.reserved + plan.spiritForMinions, disabled = battleSpiritOff)
        }
        val specs = plan.specs
        val heraldSpecs = heralds.specs
        val any = specs.isNotEmpty() || heraldSpecs.isNotEmpty()
        pobPane.tvBattleSpirit.isVisible = any
        pobPane.battleSpiritScroll.isVisible = any
        if (!any) return
        val budget = if (plan.spirit > 0.0) {
            "정신력 ${(plan.reserved + plan.spiritForMinions).toInt()} / ${plan.spirit.toInt()}"
        } else {
            "정신력 점유"
        }
        pobPane.tvBattleSpirit.text = getString(R.string.battle_spirit_title, budget)
        specs.forEach { spec ->
            val off = isSpiritOff(spec)
            val label = buildString {
                append(spec.name)
                append(' ')
                append(if (spec.spirit > 0.0) spec.spirit.toInt().toString() else "?")
                if (!spec.active) append(" · 꺼짐")
            }
            group.addView(
                battleChip(label, spec.active) {
                    toggleSpiritOff(spec, off)
                    buildBattleSpiritChips()
                    buildBattleMetaChips()
                    restartBattleIfRunning()
                }
            )
        }
        heraldSpecs.forEach { spec ->
            val off = battleSpiritOff.any { it.equals(spec.gem, true) || it.equals(spec.name, true) }
            val label = "${spec.name} ${spec.spirit.toInt()}" + if (!spec.active) " · 꺼짐" else ""
            group.addView(
                battleChip(label, spec.active) {
                    if (off) battleSpiritOff.removeAll { it.equals(spec.gem, true) || it.equals(spec.name, true) }
                    else battleSpiritOff.add(spec.gem.ifBlank { spec.name })
                    buildBattleSpiritChips()
                    buildBattleMetaChips()
                    restartBattleIfRunning()
                }
            )
        }
    }

    private fun isSpiritOff(spec: SpiritSpec): Boolean =
        battleSpiritOff.any { it.equals(spec.gem.en, true) || it.equals(spec.name, true) }

    private fun toggleSpiritOff(spec: SpiritSpec, off: Boolean) {
        if (off) {
            battleSpiritOff.removeAll { it.equals(spec.gem.en, true) || it.equals(spec.name, true) }
        } else {
            battleSpiritOff.add(spec.gem.en.ifBlank { spec.name })
        }
        // 메타 젬은 발동 장치 목록에서도 같이 빼야 실제로 멈춘다.
        val kind = runCatching { TriggerKind.ofName(spec.gem.en) ?: TriggerKind.ofName(spec.name) }.getOrNull()
        if (kind != null) {
            if (off) battleTriggerOff.remove(kind) else battleTriggerOff.add(kind)
        }
    }

    /**
     * 메타(발동) 젬 칩 — 어떤 발동 장치를 전투에 넣을지 젬별로 고른다.
     * 정신력을 점유하는 메타 젬은 여기서 끄면 점유도 함께 빠진다.
     */
    private fun buildBattleMetaChips() {
        val group = pobPane.battleMetaChips
        group.removeAllViews()
        val build = pobBuild
        if (build == null) {
            pobPane.tvBattleMeta.isVisible = false
            pobPane.battleMetaScroll.isVisible = false
            return
        }
        val plan = runCatching {
            SpiritBuffs.plan(build, SpiritBuffs.catalog(this), krDict, disabled = battleSpiritOff)
        }.getOrDefault(SpiritBuffPlan.EMPTY)
        val kinds = runCatching { PobTriggers.extract(build, 0.0).map { it.kind }.distinct() }
            .getOrDefault(emptyList())
        val metaSpecs = plan.meta
        val any = kinds.isNotEmpty() || metaSpecs.isNotEmpty()
        pobPane.tvBattleMeta.isVisible = any
        pobPane.battleMetaScroll.isVisible = any
        if (!any) return
        metaSpecs.forEach { spec ->
            val kind = runCatching { TriggerKind.ofName(spec.gem.en) ?: TriggerKind.ofName(spec.name) }.getOrNull()
            val on = spec.active && (kind == null || kind !in battleTriggerOff)
            val label = buildString {
                append(spec.name)
                if (spec.spirit > 0.0) append(" ").append(spec.spirit.toInt())
                append(" (메타)")
                if (!on) append(" · 꺼짐")
            }
            group.addView(
                battleChip(label, on) {
                    toggleSpiritOff(spec, !on)
                    buildBattleMetaChips()
                    buildBattleSpiritChips()
                    restartBattleIfRunning()
                }
            )
        }
        // 자료에 없는 메타 젬도 발동 장치 이름으로 남는다.
        kinds.filter { kind -> metaSpecs.none { TriggerKind.ofName(it.gem.en) == kind } }.forEach { kind ->
            val on = kind !in battleTriggerOff
            group.addView(
                battleChip(kind.label, on) {
                    if (on) battleTriggerOff.add(kind) else battleTriggerOff.remove(kind)
                    buildBattleMetaChips()
                    restartBattleIfRunning()
                }
            )
        }
    }

    private fun battleStats(): BattlePlayerStats? = BattleSetup.statsFor(
        build = pobBuild,
        overrides = pobOverrides,
        flasks = pobFlasks,
        config = pobConfig,
        mainGroupPreference = pobMainGroup,
        skillOverride = battleSkillPick,
        triggerMode = battleTriggerMode,
        dict = krDict,
        tree = Poe2Tree.of(this),
        combo = BattleCombos.toSteps(comboPicks),
        spiritCatalog = SpiritBuffs.catalog(this),
        disabledSpirit = battleSpiritOff,
        disabledTriggers = battleTriggerOff,
    )

    /** PoB가 비운 스킬별 DPS를 보충하기 위해 계산기 값만 따로 받아 둔다 (스킬 칩에 쓴다) */
    private fun battleSkillDps(): Map<String, Double> {
        val build = pobBuild ?: return emptyMap()
        return runCatching {
            val ascend = AscendancyEffects.profile(build, Poe2Tree.of(this))
            val unique = UniqueEffects.profile(build, pobOverrides, krDict)
            val calc = PobCalculator.compute(build, pobOverrides, pobFlasks, pobConfig, ascend, unique)
            calc.skillDps.filter { it.dps.isFinite() && it.dps > 0.0 }
                .associate { TriggerKind.normalizeName(it.name) to it.dps }
        }.getOrDefault(emptyMap())
    }

    private fun startBattle() {
        try {
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
        } catch (t: Throwable) {
            Log.e(LOG_TAG, "전투를 시작하지 못했습니다", t)
            battleStarted = false
            battleWantsPlay = false
            Snackbar.make(
                binding.root,
                getString(R.string.battle_error, t.javaClass.simpleName),
                Snackbar.LENGTH_LONG,
            ).show()
        }
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
        val fired = result.triggers.filter { it.casts > 0 }
        val triggerLine = if (result.triggerCasts > 0) {
            "\n" + getString(
                R.string.battle_result_trigger,
                result.triggerCasts,
                BattleWorld.formatShort(result.triggerDamage),
            ) + if (fired.isNotEmpty()) {
                "\n" + fired.joinToString("\n") { "· " + it.line }
            } else ""
        } else ""
        // 전직이 부여한 스킬이 전투 중 돈 횟수도 같이 적는다.
        val ascendLine = if (result.ascendCasts > 0) {
            "\n" + getString(
                R.string.battle_result_ascend,
                result.ascendCasts,
                BattleWorld.formatShort(result.ascendDamage),
            )
        } else ""
        // 봇이 어떤 콤보를 돌렸고 준비 스킬이 몇 번 나갔는지
        val comboCount = result.phantomCasts + result.whirlwindCasts + result.twisterCasts + result.bellCasts
        val comboLine = if (result.comboName.isNotBlank() && comboCount > 0) {
            "\n" + getString(
                R.string.battle_result_combo,
                result.comboName,
                result.phantomCasts,
                result.whirlwindCasts,
                result.twisterCasts,
                result.bellCasts,
            )
        } else ""
        // 부적으로 변신해 싸웠거나 기름을 뿌렸으면 그 결과도 적는다.
        val beastLine = if (result.beast.isBeast || result.oilSpills > 0) {
            "\n" + getString(
                R.string.battle_result_beast,
                result.beastText.ifBlank { result.beast.label },
                result.oilSpills,
                result.oilIgnitions,
                BattleWorld.formatShort(result.oilDamage),
            )
        } else ""
        // 소환수·두꺼비가 낸 피해도 따로 적는다.
        val minionLine = if (result.minionText.isNotBlank() || result.toadExplosions > 0) {
            "\n" + getString(
                R.string.battle_result_minion,
                if (result.minionText.isBlank()) "-" else result.minionText,
                BattleWorld.formatShort(result.minionDamage),
                result.minionKills,
                result.minionDeaths,
                result.toadExplosions,
                BattleWorld.formatShort(result.darkConsequencesDamage),
            )
        } else ""
        // 사망했다면 무엇에 맞고 쓰러졌는지, 어느 스킬이 피해를 냈는지도 함께 적는다.
        val deathLine = result.deathCause?.takeIf { !result.victory }?.let { cause ->
            "\n" + getString(R.string.battle_result_death, cause.headline, cause.damageLine)
        } ?: ""
        val chargeLine = if (result.chargeText.isNotBlank()) {
            "\n" + getString(R.string.battle_result_charges, result.chargeText, result.frenzyGained, result.chargesSpent)
        } else ""
        val skillLine = if (result.skillDamage.isNotEmpty()) {
            "\n" + getString(R.string.battle_result_skills) + "\n" +
                result.skillDamage.take(6).joinToString("\n") { "· " + it.line }
        } else ""
        // 전령이 돌았으면 전령별 발동 횟수와 피해도 적는다.
        val heraldLine = if (result.heraldProcs > 0) {
            "\n" + getString(R.string.battle_result_herald, result.heraldProcs, BattleWorld.formatShort(result.heraldDamage)) +
                if (result.heraldReports.isNotEmpty()) {
                    "\n" + result.heraldReports.joinToString("\n") { "· " + it.line }
                } else ""
        } else ""
        val shatterLine = if (result.shatters > 0) {
            "\n" + getString(R.string.battle_result_shatter, result.shatters)
        } else ""
        // 정신력 점유 버프가 돌았으면 버프별 발동 횟수와 피해도 적는다.
        val spiritLine = if (result.spiritReports.isNotEmpty()) {
            "\n" + getString(
                R.string.battle_result_spirit,
                result.spiritReports.joinToString(" · ") { it.name },
                BattleWorld.formatShort(result.spiritDamage),
            ) + "\n" + result.spiritReports.take(4).joinToString("\n") { "· " + it.line }
        } else ""
        val clearLine = result.clear?.topSkill?.let { top ->
            "\n" + getString(R.string.battle_result_clear, top.name, BattleWorld.formatShort(top.damage))
        } ?: ""
        pobPane.tvBattleResult.text = head + deathLine + "\n" + body + chargeLine + clearLine + skillLine +
            heraldLine + spiritLine + shatterLine + triggerLine + ascendLine + comboLine + minionLine + beastLine
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

    /** 빌드 하나 때문에 앱이 멈추지 않게, 화면 그리기 실패는 여기서 붙잡고 안내만 남긴다. */
    private inline fun guardPob(block: () -> Unit) {
        try {
            block()
        } catch (t: Throwable) {
            reportPobError(t)
        }
    }

    private fun reportPobError(t: Throwable) {
        Log.e(LOG_TAG, "빌드 화면을 갱신하지 못했습니다", t)
        runCatching {
            pobPane.tvBuildHint.text =
                getString(R.string.pob_render_failed, t.javaClass.simpleName)
        }
    }

    // ------------------------------------------------------------- 빌드 불러오기 (코드 입력 시트)
    private var loadDialog: BottomSheetDialog? = null
    private var loadBinding: DialogPobLoadBinding? = null

    private fun showPobLoadDialog() {
        val sheet = DialogPobLoadBinding.inflate(layoutInflater)
        val dialog = BottomSheetDialog(this)
        dialog.setContentView(sheet.root)
        loadDialog = dialog
        loadBinding = sheet
        sheet.tvLoadError.isVisible = false

        sheet.btnClose.setOnClickListener { dialog.dismiss() }
        sheet.btnPaste.setOnClickListener {
            val pasted = clipboardText()
            if (pasted.isBlank()) {
                showLoadError(getString(R.string.pob_paste_empty))
            } else {
                sheet.etPobCode.setText(pasted)
                sheet.etPobCode.setSelection(sheet.etPobCode.text?.length ?: 0)
                sheet.tvLoadError.isVisible = false
            }
        }
        sheet.btnPobLoadNow.setOnClickListener {
            val raw = sheet.etPobCode.text?.toString().orEmpty().trim()
            if (raw.isEmpty()) {
                showLoadError(getString(R.string.pob_load_hint_need_code))
                return@setOnClickListener
            }
            // 광고가 준비돼 있으면 먼저 보여 주고, 닫힌 뒤에 빌드를 연다.
            // 준비되지 않았으면 기다리지 않고 바로 연다 (광고 때문에 막히지 않는다).
            showLoadError(getString(R.string.pob_loading), highlight = false)
            val ad = ads
            if (ad == null) loadPob(raw, dialog) else ad.showThenLoad { loadPob(raw, dialog) }
        }
        renderRecent(sheet)
        dialog.setOnDismissListener {
            if (loadDialog === dialog) {
                loadDialog = null
                loadBinding = null
            }
        }
        dialog.show()
        dialog.window?.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE or
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        )
        sheet.etPobCode.requestFocus()
    }

    private fun showLoadError(message: String, highlight: Boolean = true) {
        val sheet = loadBinding
        if (sheet == null) {
            Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
            return
        }
        sheet.tvLoadError.text = message
        sheet.tvLoadError.setTextColor(
            ContextCompat.getColor(this, if (highlight) R.color.resist_low else R.color.text_muted)
        )
        sheet.tvLoadError.isVisible = true
    }

    private fun clipboardText(): String {
        val manager = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return ""
        val clip = manager.primaryClip ?: return ""
        if (clip.itemCount == 0) return ""
        return clip.getItemAt(0).coerceToText(this)?.toString().orEmpty().trim()
    }

    private fun loadPob(raw: String, dialog: BottomSheetDialog? = null) {
        val code = raw.trim()
        if (code.isEmpty()) return
        if (loadBinding != null) showLoadError(getString(R.string.pob_loading), highlight = false)
        network.execute {
            val result = runCatching {
                if (code.contains("pobb.in")) Pob.fetchPobbIn(code) else Pob.decode(code)
            }
            handler.post {
                val build = result.getOrNull()
                if (build == null) {
                    showLoadError(getString(R.string.pob_load_failed))
                    return@post
                }
                val hadBattle = battleStarted
                pobBuild = build
                treePane.treeCanvas.clear()
                pobOverrides.clear()
                pobFlasks.clear()
                inventorySelection = null
                pobMainGroup = 0
                battleSkillPick = null
                try {
                    pobPane.tvBuildTitle.text = build.title
                    pobPane.tvBuildHint.text = getString(
                        R.string.pob_build_summary,
                        build.skills.size,
                        build.slots.size,
                        build.treeNodes,
                    )
                    runCatching { pobStore.add(Pob.encode(build), build.title) }
                    val sheet = loadBinding
                    if (sheet != null) renderRecent(sheet)
                    resetBattleForNewBuild()
                    refreshPobCalc()
                    refreshSlots()
                    refreshGearValue(build)
                    renderSkills()
                    pobPane.pobTabs.getTabAt(0)?.select()
                    dialog?.dismiss()
                    Snackbar.make(
                        binding.root,
                        if (hadBattle) R.string.battle_reset_build else R.string.pob_loaded,
                        Snackbar.LENGTH_SHORT,
                    ).show()
                } catch (t: Throwable) {
                    reportPobError(t)
                }
            }
        }
    }

    /** 빌드를 새로 불러오면 이미 돌던 전투는 그 빌드 수치가 아니므로 비운다. */
    private fun resetBattleForNewBuild() {
        if (!battleReady) return
        battleView.clear()
        battleWorld = null
        battleStarted = false
        battleWantsPlay = false
        pobPane.tvBattleResult.isVisible = false
        pobPane.btnBattlePause.text = getString(R.string.battle_pause)
    }

    private fun renderRecent(sheet: DialogPobLoadBinding) {
        guardPob {
            val entries = pobStore.all()
            sheet.recentScroll.isVisible = entries.isNotEmpty()
            sheet.tvRecentTitle.isVisible = entries.isNotEmpty()
            sheet.recentChips.removeAllViews()
            if (entries.isEmpty()) return
            entries.forEach { entry ->
                val chip = Chip(this)
                chip.text = entry.label
                chip.isCheckable = false
                chip.setOnClickListener { loadPob(entry.code, loadDialog) }
                sheet.recentChips.addView(chip)
            }
        }
    }

    private fun exportPob() {
        val build = pobBuild ?: run {
            Snackbar.make(binding.root, R.string.pob_empty_title, Snackbar.LENGTH_SHORT).show()
            return
        }
        val code = Pob.encode(build)
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val copied = runCatching {
            clipboard.setPrimaryClip(ClipData.newPlainText("PoB code", code))
        }.isSuccess
        Snackbar.make(
            binding.root,
            if (copied) R.string.pob_copied else R.string.pob_copy_failed,
            Snackbar.LENGTH_SHORT,
        ).show()
    }

    private fun resetPob() {
        gearValue = null
        gearValueSignature = ""
        pobPane.tvGearValue.isVisible = false
        pobPane.tvGearDetail.isVisible = false
        pobOverrides.clear()
        pobFlasks.clear()
        inventorySelection = null
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

    /** 지금 옵션이 열린 슬롯. 아이콘을 다시 탭하면 null로 돌아간다. */
    private var inventorySelection: String? = null

    // ------------------------------------------------------- 장비 가치
    /** 마지막으로 산출한 장비 가치 (불러온 빌드 기준) */
    private var gearValue: GearValue? = null
    /** 어떤 장비 구성으로 계산했는지 — 같은 구성이면 다시 부르지 않는다 */
    private var gearValueSignature = ""
    /** 부위별 내역을 펼쳤는지 */
    private var gearExpanded = false

    /**
     * 불러온 빌드의 장비 가치를 계산해 개요 카드 위쪽에 적는다.
     *
     * 주얼·젬·플라스크·호신부는 빼고 몸에 걸친 장비만 센다. 환율과 고유 시세는
     * poe.ninja에서 한 번 받아 5분 동안 재사용하므로, 탭을 오가도 다시 부르지 않는다.
     */
    private fun refreshGearValue(build: PobBuild?) {
        if (build == null) {
            gearValue = null
            gearValueSignature = ""
            pobPane.tvGearValue.isVisible = false
            pobPane.tvGearDetail.isVisible = false
            return
        }
        val signature = gearSignature(build)
        if (signature == gearValueSignature && gearValue != null) {
            renderGearValue()
            return
        }
        gearValueSignature = signature
        gearValue = null
        gearExpanded = false
        pobPane.tvGearValue.isVisible = true
        pobPane.tvGearValue.text = getString(R.string.gear_value_loading)
        pobPane.tvGearDetail.isVisible = false
        val overrides = HashMap(pobOverrides)
        network.execute {
            val league = activeLeague ?: runCatching { Market.leagues().firstOrNull()?.id }.getOrNull()
            val value = runCatching { GearMarket.value(build, league, krDict, overrides) }.getOrNull()
            handler.post {
                if (pobBuild !== build) return@post
                gearValue = value
                renderGearValue()
            }
        }
    }

    /** 현재 장비 구성 지문 — 아이템이 바뀌면 값도 다시 계산한다 */
    private fun gearSignature(build: PobBuild): String = buildString {
        append(build.title).append('|')
        for ((key, _) in GearPricing.SLOTS) {
            val item = pobOverrides[key] ?: Pob.itemForSlot(build, key) ?: continue
            append(key).append(':').append(item.name).append('/').append(item.baseType).append('/')
            append(item.rarity).append('/').append(item.lines.joinToString(";")).append('|')
        }
    }

    private fun renderGearValue() {
        val value = gearValue
        val headline = pobPane.tvGearValue
        if (value == null || !value.hasItems) {
            headline.isVisible = true
            headline.text = getString(R.string.gear_value_empty)
            pobPane.tvGearDetail.isVisible = false
            return
        }
        headline.isVisible = true
        headline.text = if (value.rates.chaosPerDivine > 0.0) {
            getString(R.string.gear_value_headline, value.divineText, value.chaosText)
        } else {
            getString(R.string.gear_value_headline_no_rate, value.divineText)
        }
        pobPane.tvGearDetail.text = value.detail
        pobPane.tvGearDetail.isVisible = gearExpanded
    }

    private fun refreshSlots() {
        guardPob {
            val build = pobBuild
            pobPane.inventoryGrid.removeAllViews()
            pobPane.inventoryExtras.removeAllViews()
            if (build == null) {
                inventorySelection = null
                pobPane.inventoryDetail.isVisible = false
                pobPane.inventoryGrid.addView(TextView(this).apply {
                    text = getString(R.string.pob_inventory_no_build)
                    setTextColor(ContextCompat.getColor(context, R.color.text_muted))
                    textSize = 12f
                    setPadding(0, (12 * resources.displayMetrics.density).toInt(), 0, 0)
                })
                return
            }
            renderInventoryGrid(build)
            renderInventoryExtras(build)
            renderInventoryDetail(build)
        }
    }

    /** 아이콘 한 칸을 탭했을 때: 열기 → 같은 칸이면 닫기, 다른 칸이면 그 칸으로 바꾸기 */
    private fun tapInventorySlot(slot: String) {
        inventorySelection = PobInventory.toggle(inventorySelection, slot)
        val build = pobBuild
        if (build != null) {
            renderInventoryGrid(build)
            renderInventoryExtras(build)
            renderInventoryDetail(build)
        }
    }

    private fun inventoryItem(build: PobBuild, slot: String): PobItem? =
        pobOverrides[slot] ?: Pob.itemForSlot(build, slot)

    private fun renderInventoryGrid(build: PobBuild) {
        val grid = pobPane.inventoryGrid
        grid.removeAllViews()
        val density = resources.displayMetrics.density
        val gap = (6 * density).toInt()
        val height = (88 * density).toInt()
        for (row in 0 until PobInventory.ROWS) {
            val rowView = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            rowView.layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { bottomMargin = gap }
            var col = 0
            while (col < PobInventory.COLUMNS) {
                val cell = PobInventory.GRID.firstOrNull { it.row == row && it.col == col }
                if (cell == null) {
                    rowView.addView(View(this).apply {
                        layoutParams = LinearLayout.LayoutParams(0, height, 1f).apply {
                            marginEnd = gap
                        }
                    })
                    col++
                    continue
                }
                rowView.addView(
                    inventorySlotCell(build, cell.slot, compact = false).apply {
                        layoutParams = LinearLayout.LayoutParams(0, height, cell.colSpan.toFloat()).apply {
                            marginEnd = gap
                        }
                    },
                )
                col += cell.colSpan
            }
            grid.addView(rowView)
        }
    }

    /** 격자 아래 작은 칸 — 교체 무기·반지 3·부적·플라스크 */
    private fun renderInventoryExtras(build: PobBuild) {
        val box = pobPane.inventoryExtras
        box.removeAllViews()
        val density = resources.displayMetrics.density
        val gap = (6 * density).toInt()
        val height = (62 * density).toInt()
        val slots = PobInventory.EXTRAS + PobInventory.flaskSlots(build)
        slots.chunked(3).forEach { line ->
            val rowView = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            rowView.layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { bottomMargin = gap }
            line.forEach { slot ->
                rowView.addView(
                    inventorySlotCell(build, slot, compact = true).apply {
                        layoutParams = LinearLayout.LayoutParams(0, height, 1f).apply { marginEnd = gap }
                    },
                )
            }
            // 줄이 덜 찼으면 빈 자리를 같은 비율로 매운다.
            repeat(3 - line.size) {
                rowView.addView(View(this).apply {
                    layoutParams = LinearLayout.LayoutParams(0, height, 1f).apply { marginEnd = gap }
                })
            }
            box.addView(rowView)
        }
    }

    /** 슬롯 한 칸: 아이콘·슬롯 이름·아이템 이름. 칸을 탭하면 옵션 창이 열리거나 닫힌다. */
    private fun inventorySlotCell(
        build: PobBuild,
        slot: String,
        compact: Boolean,
    ): LinearLayout {
        val item = inventoryItem(build, slot)
        val selected = PobInventory.isSelected(inventorySelection, slot)
        val density = resources.displayMetrics.density
        val cell = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding((4 * density).toInt(), (4 * density).toInt(), (4 * density).toInt(), (4 * density).toInt())
            isClickable = true
            isFocusable = true
            setBackgroundResource(
                if (selected) R.drawable.bg_slot_selected else PobInventory.rarityFrame(item?.rarity.orEmpty()),
            )
            setOnClickListener { tapInventorySlot(slot) }
        }
        val iconSize = if (compact) 30 else 40
        cell.addView(ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                (iconSize * density).toInt(),
                if (compact) (iconSize * density).toInt() else (iconSize * density * 0.8).toInt(),
            )
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = getString(R.string.pob_item_icon)
            val icon = if (item != null) PobLocale.icon(krDict, item) else ""
            if (icon.isNotBlank()) {
                load(icon) { placeholder(R.drawable.bg_icon); error(R.drawable.bg_icon); crossfade(80) }
            } else {
                setImageResource(R.drawable.bg_icon)
            }
        })
        val slotName = Pob.SLOTS[slot] ?: slot
        val showSlotName = !compact || item == null
        if (showSlotName) {
            cell.addView(TextView(this).apply {
                text = slotName
                setTextColor(ContextCompat.getColor(context, R.color.text_muted))
                textSize = if (compact) 8f else 9f
                maxLines = 1
            })
        }
        cell.addView(TextView(this).apply {
            text = item?.let { PobLocale.title(krDict, it) } ?: slotName.takeIf { compact } ?: getString(R.string.pob_inventory_empty)
            setTextColor(
                if (item != null) PobInventory.rarityColor(this, item.rarity)
                else ContextCompat.getColor(context, R.color.text_muted)
            )
            textSize = if (compact) 8.5f else 9.5f
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            gravity = Gravity.CENTER
        })
        if (slot in pobOverrides) {
            cell.addView(TextView(this).apply {
                text = getString(R.string.pob_inventory_changed)
                setTextColor(ContextCompat.getColor(context, R.color.gold))
                textSize = 8f
                maxLines = 1
            })
        }
        return cell
    }

    /** 아이콘을 탭했을 때 열리는 아이템 옵션 창 */
    private fun renderInventoryDetail(build: PobBuild) {
        val box = pobPane.inventoryDetail
        box.removeAllViews()
        val slot = inventorySelection
        val item = slot?.let { inventoryItem(build, it) }
        if (slot == null) {
            box.isVisible = false
            return
        }
        box.isVisible = true
        val density = resources.displayMetrics.density
        val slotName = Pob.SLOTS[slot] ?: slot
        val rarity = item?.rarity.orEmpty()
        val rarityKo = PobInventory.rarityLabel(this, rarity)
        box.addView(TextView(this).apply {
            text = item?.let { PobLocale.title(krDict, it) } ?: getString(R.string.pob_inventory_empty)
            setTextColor(
                if (item != null) PobInventory.rarityColor(this, item.rarity)
                else ContextCompat.getColor(context, R.color.text_muted)
            )
            textSize = 15f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })
        box.addView(TextView(this).apply {
            text = listOf(slotName, rarityKo, item?.let { PobLocale.subtitle(krDict, it) }.orEmpty())
                .filter { it.isNotBlank() }
                .joinToString(" · ")
            setTextColor(ContextCompat.getColor(context, R.color.text_muted))
            textSize = 11f
            setPadding(0, (2 * density).toInt(), 0, 0)
        })
        if (item != null) {
            val options = PobLocale.lines(krDict, item)
            if (options.isNotEmpty()) {
                box.addView(TextView(this).apply {
                    text = getString(R.string.pob_inventory_options)
                    setTextColor(ContextCompat.getColor(context, R.color.gold))
                    textSize = 11f
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                    setPadding(0, (10 * density).toInt(), 0, (2 * density).toInt())
                })
                box.addView(TextView(this).apply {
                    text = options.joinToString("\n") { "· $it" }
                    setTextColor(ContextCompat.getColor(context, R.color.text_primary))
                    textSize = 12f
                    setLineSpacing((2 * density), 1f)
                })
            }
        }
        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, (12 * density).toInt(), 0, 0)
        }
        val change = com.google.android.material.button.MaterialButton(
            this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle,
        ).apply {
            text = getString(R.string.pob_inventory_change)
            textSize = 12f
            minHeight = (40 * density).toInt()
            insetTop = 0; insetBottom = 0
            setTextColor(ContextCompat.getColor(context, R.color.gold))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                .apply { marginEnd = (6 * density).toInt() }
            setOnClickListener { showSlotDialog(slot) }
        }
        val close = com.google.android.material.button.MaterialButton(this).apply {
            text = getString(R.string.pob_inventory_close)
            textSize = 12f
            minHeight = (40 * density).toInt()
            insetTop = 0; insetBottom = 0
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener { tapInventorySlot(slot) }
        }
        buttons.addView(change)
        buttons.addView(close)
        box.addView(buttons)
    }

    private fun renderSkills() {
        guardPob { renderSkillsBody() }
    }

    private fun renderSkillsBody() {
        val build = pobBuild ?: return
        val dict = krDict
        val container = pobPane.skillsContainer
        container.removeAllViews()
        val density = resources.displayMetrics.density
        // 그룹마다 그 스킬의 DPS를 같이 적어 어느 스킬이 화력을 끌고 가는지 보이게 한다.
        val calc = runCatching {
            PobCalculator.compute(build, pobOverrides, pobFlasks, pobConfig)
        }.getOrNull()
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
                text = "${groupIndex + 1}. ${skillGroupTitle(group, dict)}"
                setTextColor(ContextCompat.getColor(context, R.color.text_primary))
                textSize = 14f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            header.addView(title)
            if (calc != null) {
                val groupDps = groupDps(build, group, calc)
                if (groupDps > 0.0) {
                    header.addView(TextView(this).apply {
                        text = BattleWorld.formatShort(groupDps)
                        setTextColor(ContextCompat.getColor(context, R.color.unique_name))
                        textSize = 12f
                        setTypeface(typeface, android.graphics.Typeface.BOLD)
                        setPadding(0, 0, (10 * density).toInt(), 0)
                    })
                }
            }
            if (group === Pob.mainGroup(build, pobMainGroup)) {
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
                val nameKo = dict.gemName(gem.name).ifBlank { gem.name }
                row.tvGemName.text = nameKo
                row.tvGemKind.text = getString(
                    when {
                        PobTriggers.kindOf(gem) != null -> R.string.pob_meta_kind
                        isSupport || PobCalculator.isSupport(gem) -> R.string.pob_support_kind
                        else -> R.string.pob_skill_kind
                    }
                )
                row.tvGemLevel.text = "Lv ${gem.level}"
                row.btnLevelUp.setOnClickListener { gem.level = (gem.level + 1).coerceAtMost(40); renderSkills(); refreshPobCalc() }
                row.btnLevelDown.setOnClickListener { gem.level = (gem.level - 1).coerceAtLeast(1); renderSkills(); refreshPobCalc() }
                row.tvGemName.setOnClickListener { showGemDetail(group, gem, dict) }
                row.tvGemKind.setOnClickListener { showGemDetail(group, gem, dict) }
                card.addView(row.root)
            }
            container.addView(card)
        }
    }

    /** 스킬 그룹 하나의 DPS — 그 그룹 액티브 스킬 중 가장 센 값, 주력 그룹은 총 DPS */
    private fun groupDps(build: PobBuild, group: PobSkillGroup, calc: PobCalculator.Result): Double {
        val keys = group.gems
            .filter { it.enabled && SkillRoles.isActiveDamageGem(it) }
            .map { TriggerKind.normalizeName(it.name) }
            .toSet()
        val fromList = calc.skillDps
            .filter { TriggerKind.normalizeName(it.name) in keys }
            .maxOfOrNull { it.dps } ?: 0.0
        return if (group === Pob.mainGroup(build, pobMainGroup)) maxOf(fromList, calc.dpsNow) else fromList
    }

    /** 스킬 그룹 제목. PoB는 label에 첫 젬 이름을 그대로 넣어 두고, 샘플 빌드는 "1 Lightning Arrow"처럼 번호를 붙인다. */
    private fun skillGroupTitle(group: PobSkillGroup, dict: KrDict): String {
        val raw = group.label.trim().ifBlank { group.gems.firstOrNull()?.name.orEmpty() }
        if (raw.isBlank()) return getString(R.string.pob_skill_kind)
        dict.gemName(raw).takeIf { it.isNotBlank() }?.let { return it }
        val match = Regex("^(\\d+)\\s+(.*)$").find(raw)
        if (match != null) {
            val rest = match.groupValues[2].trim()
            val ko = dict.gemName(rest)
            if (ko.isNotBlank()) return "${match.groupValues[1]} $ko"
        }
        return raw
    }

    /** 젬 하나의 설명 창. 이름·종류·레벨은 한글로, 수치는 PoB가 계산한 값을 쓴다. */
    private fun showGemDetail(group: PobSkillGroup, gem: PobGem, dict: KrDict) {
        val meta = PobTriggers.kindOf(gem)
        val kind = when {
            meta != null -> getString(R.string.pob_meta_kind)
            PobCalculator.isSupport(gem) -> getString(R.string.pob_support_kind)
            else -> getString(R.string.pob_skill_kind)
        }
        val lines = ArrayList<String>(4)
        lines.add("$kind · 레벨 ${gem.level}")
        val dps = BattleSetup.skillDps(pobBuild ?: return, gem.name)
        if (dps > 0) lines.add("PoB DPS ${NumberFormat.getNumberInstance(Locale.KOREA).format(dps.toLong())}")
        if (group === Pob.mainGroup(pobBuild ?: return, pobMainGroup)) {
            lines.add(getString(R.string.pob_main_skill))
        }
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(dict.gemName(gem.name).ifBlank { gem.name })
            .setMessage(lines.joinToString("\n"))
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun refreshPobCalc() {
        guardPob { refreshPobCalcBody() }
    }

    private fun refreshPobCalcBody() {
        val build = pobBuild ?: return
        val result = PobCalculator.compute(build, pobOverrides, pobFlasks, pobConfig)
        val fmt = NumberFormat.getNumberInstance(Locale.KOREA)

        pobPane.tvDps.text = dpsText(result.dpsNow)
        val dpsPct = if (result.dpsBase > 0) (result.dpsNow / result.dpsBase - 1) * 100 else 0.0
        pobPane.tvDpsDelta.text = when {
            result.dpsInfinite -> getString(R.string.pob_dps_infinite)
            result.dpsEstimated -> getString(R.string.pob_dps_missing)
            else -> "기준 %s DPS · %s%.1f%%".format(
                fmt.format(result.dpsBase.toLong()),
                if (dpsPct >= 0) "+" else "",
                dpsPct,
            )
        }
        pobPane.tvDpsDelta.setTextColor(
            when {
                result.dpsEstimated -> ContextCompat.getColor(this, R.color.text_muted)
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
        val dict = krDict
        pobPane.tvSkillSummary.text = skills.joinToString("\n") { group ->
            "· ${skillGroupTitle(group, dict)}: " +
                group.gems.filter { it.enabled }.joinToString(", ") { dict.gemName(it.name).ifBlank { it.name } }
        }.ifBlank { getString(R.string.pob_tree_empty) }

        // 이 빌드에 맞는 스킬·정신력·메타 칩을 다시 만들고 헤더도 새 수치로 갱신한다.
        buildBattleSkillChips()
        buildBattleSpiritChips()
        buildBattleMetaChips()

        renderTreeTab(build)
    }

    /** 패시브 탭: 찍은 노드 수·전직·키스톤·주요 패시브를 한글로 보여 준다. */
    private fun renderTreeTab(build: PobBuild) {
        val tree = Poe2Tree.of(this)
        val box = pobPane.llTreeNodes
        box.removeAllViews()
        pobPane.btnOpenTree.setText(R.string.pob_tree_open_character)
        // 직업 줄은 빌드가 없어도 늘 채운다 — 거기서 직업을 골라 시작 노드부터 찍는다.
        runCatching { buildClassRow() }
            .onFailure { Log.e(LOG_TAG, "패시브 탭 직업 줄을 채우지 못했습니다", it) }
        // 트리 탭을 보고 있을 때만 그 자리에서 다시 그린다 (다른 탭에서 빌드를 불러올 때 탭이 튀지 않게).
        if (binding.paneTree.isVisible) showBuildTree()
        val summary = Poe2Tree.summarize(build, tree)
        if (summary == null) {
            pobPane.tvTreeInfo.text = getString(R.string.pob_tree_no_nodes)
            return
        }
        pobPane.tvTreeInfo.text = if (summary.ascendKo.isBlank()) {
            getString(R.string.pob_tree_summary_plain, summary.classKo, summary.allocated)
        } else {
            getString(
                R.string.pob_tree_summary,
                summary.classKo,
                summary.ascendKo,
                summary.allocated,
                summary.ascendancyNodes.size,
                summary.keystones.size,
                summary.notableCount,
            )
        }
        addTreeGroup(box, R.string.pob_tree_group_ascendancy, summary.ascendancyNodes)
        addTreeGroup(box, R.string.pob_tree_group_keystone, summary.keystones)
        addTreeGroup(box, R.string.pob_tree_group_notable, summary.notables)
        addTreeGroup(box, R.string.pob_tree_group_mastery, summary.masteries)
    }

    /** 노드 목록 한 묶음. 줄을 누르면 한글 설명이 펼쳐진다. */
    private fun addTreeGroup(box: LinearLayout, titleRes: Int, nodes: List<TreeNode>) {
        if (nodes.isEmpty()) return
        val density = resources.displayMetrics.density
        val header = TextView(this).apply {
            text = getString(titleRes) + " " + nodes.size
            setTextColor(ContextCompat.getColor(context, R.color.gold))
            textSize = 12f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(0, (10 * density).toInt(), 0, (4 * density).toInt())
        }
        box.addView(header)
        nodes.forEach { node ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundResource(R.drawable.bg_search)
                setPadding((12 * density).toInt(), (8 * density).toInt(), (12 * density).toInt(), (8 * density).toInt())
            }
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = (4 * density).toInt() }
            row.layoutParams = params
            val name = TextView(this).apply {
                text = node.displayName
                setTextColor(ContextCompat.getColor(context, R.color.text_primary))
                textSize = 13f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            }
            row.addView(name)
            val stats = node.stats.trim()
            if (stats.isNotBlank()) {
                val first = stats.lineSequence().firstOrNull().orEmpty()
                row.addView(TextView(this).apply {
                    text = first
                    setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
                    textSize = 11f
                })
            }
            row.setOnClickListener {
                val kind = getString(treeKindRes(node))
                val detail = buildString {
                    if (stats.isNotBlank()) append(stats).append("\n\n")
                    append(kind)
                }
                androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle(node.displayName)
                    .setMessage(detail)
                    .setPositiveButton(android.R.string.ok, null)
                    .show()
            }
            box.addView(row)
        }
    }

    private fun treeKindRes(node: TreeNode): Int = when {
        node.isKeystone -> R.string.pob_tree_group_keystone
        node.isAscendancy -> R.string.pob_tree_group_ascendancy
        node.isMastery -> R.string.pob_tree_group_mastery
        else -> R.string.pob_tree_group_notable
    }

    /** DPS 한 줄 표기 — 10억 이상은 poe.ninja처럼 2.1B로 줄여 쓴다. */
    private fun dpsText(value: Double): String = when {
        !value.isFinite() -> "—"
        value >= 1_000_000_000 -> BattleWorld.formatShort(value)
        else -> NumberFormat.getNumberInstance(Locale.KOREA).format(value.toLong())
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
        val baseType = item.summary.ifBlank { item.nameEn }
        val itemClass = WeaponTypes.itemClassOf(baseType, item.nameEn, item.name)
        val unique = PobItem(
            name = item.name,
            rarity = "Unique",
            lines = lines,
            baseType = baseType,
            itemClass = itemClass,
            weapon = WeaponTypes.detect(itemClass, baseType, item.nameEn, item.name),
        )
        dialog.binding.etModLines.setText(lines.joinToString("\n"))
        dialog.pendingUnique = unique
        Snackbar.make(binding.root, "%s 선택됨 — 저장을 누르면 장착됩니다".format(item.name), Snackbar.LENGTH_SHORT).show()
    }

    private var currentSlotDialog: SlotDialog? = null

    private inner class SlotDialog(val slot: String, val binding: DialogPobSlotBinding) {
        var pendingUnique: PobItem? = null
    }

    private fun showSlotDialog(slot: String) {
        val build = pobBuild ?: run {
            Snackbar.make(binding.root, R.string.pob_empty_title, Snackbar.LENGTH_SHORT).show()
            return
        }
        slotDialogReference?.takeIf { it.isShowing }?.dismiss()
        currentSlotDialog = null
        val binding = DialogPobSlotBinding.inflate(layoutInflater)
        val dialog = SlotDialog(slot, binding)
        currentSlotDialog = dialog

        binding.tvSlotTitle.text = Pob.SLOTS[slot] ?: slot
        val original = Pob.itemForSlot(build, slot)
        val current = pobOverrides[slot] ?: original
        binding.etModLines.setText(current?.lines?.joinToString("\n") ?: "")
        if (current == null) {
            binding.slotHeader.isVisible = false
            binding.tvKoLines.isVisible = false
        } else {
            binding.slotHeader.isVisible = true
            binding.tvKoLines.isVisible = true
            binding.tvSlotItemTitle.text = PobLocale.title(krDict, current)
            binding.tvSlotItemTitle.setTextColor(PobInventory.rarityColor(binding.root, current.rarity))
            binding.tvSlotItemSub.text = PobLocale.subtitle(krDict, current)
            binding.ivSlotIcon.load(PobLocale.icon(krDict, current).ifBlank { null }) {
                placeholder(R.drawable.bg_icon)
                error(R.drawable.bg_icon)
            }
            val koLines = PobLocale.lines(krDict, current)
            binding.tvKoLines.text = if (koLines.isEmpty()) "" else koLines.joinToString("\n") { "· $it" }
            binding.tvKoLines.isVisible = koLines.isNotEmpty()
        }

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
            refreshGearValue(pobBuild)
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
                        val base = replaced?.baseType?.takeIf { it.isNotBlank() }
                            ?: current?.baseType?.takeIf { it.isNotBlank() }
                            ?: lines.firstOrNull().orEmpty()
                        val cls = WeaponTypes.itemClassOf(base, lines.joinToString(" "))
                            .ifBlank { replaced?.itemClass ?: current?.itemClass.orEmpty() }
                        val weapon = WeaponTypes.detect(cls, base, lines.joinToString(" "))
                            .takeIf { it != WeaponVisualType.NONE }
                            ?: replaced?.weapon ?: current?.weapon ?: WeaponVisualType.NONE
                        pobOverrides[slot] = PobItem(
                            name = replaced?.name ?: current?.name ?: lines.firstOrNull() ?: "",
                            rarity = replaced?.rarity ?: current?.rarity ?: "Rare",
                            lines = lines.toMutableList(),
                            baseType = base,
                            itemClass = cls,
                            weapon = weapon,
                        )
                    }
                }
                refreshSlots()
                refreshPobCalc()
                refreshGearValue(pobBuild)
                Snackbar.make(binding.root, R.string.pob_equip_saved, Snackbar.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.info_close, null)
            .show()
        slotDialogReference?.setOnDismissListener {
            currentSlotDialog = null
            slotDialogReference = null
        }
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
        if (activeTab == R.id.nav_tree && treePane.treeCanvas.isEmpty()) showPassiveTree()
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
        if (battleWantsPlay && battleReady) battleView.resume()
    }

    override fun onPause() {
        detailPane.webView.onPause()
        if (battleReady) battleView.pause()
        super.onPause()
    }

    override fun onDestroy() {
        handler.removeCallbacks(filterTick)
        handler.removeCallbacks(marketTick)
        detailPane.webView.destroy()
        if (battleReady) battleView.stop()
        super.onDestroy()
    }

    private companion object {
        const val LOG_TAG = "PoE2DBKR"
        const val HOME_URL = "https://poe2db.tw/kr/"
        const val NINJA_BUILDS_URL = "https://poe.ninja/poe2/builds"
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
