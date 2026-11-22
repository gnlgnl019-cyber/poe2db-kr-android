package com.sunuhwi.poe2dbkr

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.sunuhwi.poe2dbkr.databinding.ItemMarketBinding
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class MarketLeague(val id: String, val name: String)

/** 시세 목록 한 줄 (화폐/룬 같은 교환 품목과 고유 아이템을 함께 담는다) */
data class MarketRate(
    val id: String,
    val name: String,
    val koName: String,
    val baseType: String,
    val koBase: String,
    val iconUrl: String,
    val category: String,
    val divine: Double,
    val exalted: Double,
    val chaos: Double,
    val volume: Double,
    val volumeCurrency: String,
    val change: Double,
    val detailsId: String,
    val listingCount: Int = 0,
    val levelRequired: Int = 0,
    val corrupted: Boolean = false,
    val flavour: String = "",
    val properties: List<String> = emptyList(),
    val implicits: List<String> = emptyList(),
    val explicits: List<String> = emptyList(),
    val granted: List<String> = emptyList(),
    val requirements: List<String> = emptyList(),
    val spark: List<Double?> = emptyList(),
    val pageUrl: String = "",
    val isUnique: Boolean = false,
) {
    val value: Double get() = divine
}

enum class MarketKind { EXCHANGE, ITEM }

data class MarketType(
    val id: String,
    val label: String,
    val apiType: String,
    val kind: MarketKind,
    val filter: (MarketRate) -> Boolean = { true },
)

enum class MarketSort(val label: String) {
    VALUE_DESC("시세 높은순"),
    VALUE_ASC("시세 낮은순"),
    CHANGE_DESC("변동 큰순"),
    LISTINGS_DESC("매물 많은순"),
    NAME("이름순"),
}

/** poe.ninja PoE2 공개 시세 API (5분 메모리 캐시) */
object Market {

    private const val BASE = "https://poe.ninja/poe2/api/economy"
    private const val UA = "PoE2DB-KR-Android/1.0"
    private const val CACHE_MS = 5 * 60 * 1000L

    private val BELT_KEYS = listOf("belt", "sash")
    private val AMULET_KEYS = listOf("amulet", "talisman")
    private val RING_KEYS = listOf("ring")

    val TYPES: List<MarketType> = listOf(
        MarketType("Currency", "화폐", "Currency", MarketKind.EXCHANGE),
        MarketType("Runes", "룬", "Runes", MarketKind.EXCHANGE),
        MarketType("Essences", "에센스", "Essences", MarketKind.EXCHANGE),
        MarketType("SoulCores", "영혼 핵", "SoulCores", MarketKind.EXCHANGE),
        MarketType("Fragments", "조각", "Fragments", MarketKind.EXCHANGE),
        MarketType("UncutGems", "미가공 젬", "UncutGems", MarketKind.EXCHANGE),
        MarketType("Ritual", "징조", "Ritual", MarketKind.EXCHANGE),
        MarketType("Delirium", "감정", "Delirium", MarketKind.EXCHANGE),
        MarketType("Breach", "기폭제", "Breach", MarketKind.EXCHANGE),
        MarketType("UniqueWeapons", "고유 무기", "UniqueWeapons", MarketKind.ITEM),
        MarketType("UniqueArmours", "고유 방어구", "UniqueArmours", MarketKind.ITEM),
        MarketType("UniqueAccessories", "고유 장신구", "UniqueAccessories", MarketKind.ITEM),
        MarketType(
            "UniqueBelts", "허리띠", "UniqueAccessories", MarketKind.ITEM,
        ) { matches(it, BELT_KEYS) },
        MarketType(
            "UniqueAmulets", "목걸이", "UniqueAccessories", MarketKind.ITEM,
        ) { matches(it, AMULET_KEYS) },
        MarketType(
            "UniqueRings", "반지", "UniqueAccessories", MarketKind.ITEM,
        ) { matches(it, RING_KEYS) },
        MarketType("UniqueJewels", "고유 주얼", "UniqueJewels", MarketKind.ITEM),
    )

    private fun matches(rate: MarketRate, keys: List<String>): Boolean {
        val text = "${rate.baseType} ${rate.category} ${rate.name}".lowercase()
        return keys.any { text.contains(it) }
    }

    private val cache = HashMap<String, Pair<Long, List<MarketRate>>>()
    @Volatile
    private var cachedLeagues: Pair<Long, List<MarketLeague>>? = null

    private fun get(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 10000
        conn.readTimeout = 20000
        conn.setRequestProperty("User-Agent", UA)
        try {
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    fun leagues(): List<MarketLeague> {
        cachedLeagues?.let { (at, value) ->
            if (System.currentTimeMillis() - at < CACHE_MS) return value
        }
        val arr = JSONArray(get("$BASE/leagues"))
        val list = (0 until arr.length()).mapNotNull { i ->
            val obj = arr.optJSONObject(i) ?: return@mapNotNull null
            MarketLeague(obj.optString("id"), obj.optString("name", obj.optString("id")))
        }
        cachedLeagues = System.currentTimeMillis() to list
        return list
    }

    fun overview(league: String, typeId: String, koMap: Map<String, String>): List<MarketRate> {
        val type = TYPES.firstOrNull { it.id == typeId } ?: TYPES.first()
        val key = "$league/${type.apiType}"
        val hit = cache[key]
        val base = if (hit != null && System.currentTimeMillis() - hit.first < CACHE_MS) {
            hit.second
        } else {
            val fetched = when (type.kind) {
                MarketKind.EXCHANGE -> fetchExchange(league, type.apiType, koMap)
                MarketKind.ITEM -> fetchItems(league, type.apiType, koMap)
            }
            cache[key] = System.currentTimeMillis() to fetched
            fetched
        }
        return base.filter(type.filter)
    }

    private fun rates(root: JSONObject): Triple<Double, Double, Double> {
        val core = root.optJSONObject("core") ?: return Triple(1.0, 0.0, 0.0)
        val r = core.optJSONObject("rates") ?: JSONObject()
        return Triple(1.0, r.optDouble("exalted", 0.0), r.optDouble("chaos", 0.0))
    }

    private fun fetchExchange(
        league: String,
        type: String,
        koMap: Map<String, String>,
    ): List<MarketRate> {
        val url = "$BASE/exchange/current/overview?league=${URLEncoder.encode(league, "UTF-8")}&type=$type"
        val root = JSONObject(get(url))
        val (_, exRate, chaosRate) = rates(root)
        return parseExchange(root, koMap, exRate, chaosRate)
    }

    /** 교환(화폐·룬·에센스) 응답 해석. 테스트에서도 그대로 돌린다. */
    internal fun parseExchange(
        root: JSONObject,
        koMap: Map<String, String>,
        exRate: Double,
        chaosRate: Double,
    ): List<MarketRate> {
        val core = root.optJSONObject("core")
        val primary = core?.optString("primary", "divine").orEmpty().ifBlank { "divine" }
        val lines = root.optJSONArray("lines") ?: JSONArray()
        // poe.ninja는 품목 메타데이터를 응답 최상단 items 배열에 담아 준다.
        // (core.items에는 기준 화폐 3개만 들어 있어서 여기만 보면 이름·아이콘이 전부 비어 버린다.)
        val index = itemIndex(root)

        val result = ArrayList<MarketRate>(lines.length())
        for (i in 0 until lines.length()) {
            val line = lines.optJSONObject(i) ?: continue
            val id = line.optString("id")
            val info = index[id] ?: findItem(core, id)
            val name = info?.optString("name", id).orEmpty().ifBlank { id }
            val image = info?.optString("image", "").orEmpty()
            val divine = if (primary == "divine") line.optDouble("primaryValue", 0.0) else line.optDouble("primaryValue", 0.0)
            result.add(
                MarketRate(
                    id = id,
                    name = name,
                    koName = koMap[name.lowercase().trim()] ?: "",
                    baseType = "",
                    koBase = "",
                    iconUrl = resolveIcon(image),
                    category = info?.optString("category", "").orEmpty(),
                    divine = divine,
                    exalted = divine * exRate,
                    chaos = divine * chaosRate,
                    volume = line.optDouble("volumePrimaryValue", 0.0),
                    volumeCurrency = line.optString("maxVolumeCurrency", primary),
                    change = sparkChange(line),
                    detailsId = info?.optString("detailsId", id).orEmpty().ifBlank { id },
                    spark = sparkline(line),
                    pageUrl = "https://poe2db.tw/kr/" + name.replace(" ", "_"),
                )
            )
        }
        return result.sortedByDescending { it.divine }
    }

    private fun fetchItems(
        league: String,
        type: String,
        koMap: Map<String, String>,
    ): List<MarketRate> {
        val url = "$BASE/stash/current/item/overview?league=${URLEncoder.encode(league, "UTF-8")}&type=$type"
        val root = JSONObject(get(url))
        val (_, exRate, chaosRate) = rates(root)
        return parseItems(root, koMap, exRate, chaosRate)
    }

    /** 고유 아이템 시세 응답 해석. 테스트에서도 그대로 돌린다. */
    internal fun parseItems(
        root: JSONObject,
        koMap: Map<String, String>,
        exRate: Double,
        chaosRate: Double,
    ): List<MarketRate> {
        val lines = root.optJSONArray("lines") ?: JSONArray()

        val result = ArrayList<MarketRate>(lines.length())
        for (i in 0 until lines.length()) {
            val line = lines.optJSONObject(i) ?: continue
            val name = line.optString("name").ifBlank { line.optString("itemId") }
            if (name.isBlank()) continue
            val baseType = line.optString("baseType")
            val category = line.optString("category")
            val pageUrl = poe2dbUrl(name)
            val divine = line.optDouble("primaryValue", 0.0)
            result.add(
                MarketRate(
                    id = line.optString("detailsId", name).ifBlank { name },
                    name = name,
                    koName = koMap[name.lowercase().trim()] ?: "",
                    baseType = baseType,
                    koBase = "",
                    iconUrl = resolveIcon(line.optString("icon")),
                    category = category,
                    divine = divine,
                    exalted = divine * exRate,
                    chaos = divine * chaosRate,
                    volume = line.optDouble("listingCount", 0.0),
                    volumeCurrency = "",
                    change = sparkChange(line),
                    detailsId = line.optString("detailsId", name),
                    listingCount = line.optInt("listingCount", 0),
                    levelRequired = line.optInt("levelRequired", 0),
                    corrupted = line.optBoolean("corrupted", false),
                    flavour = cleanTags(line.optString("flavourText")),
                    properties = modifiers(line, "propertyModifiers"),
                    implicits = modifiers(line, "implicitModifiers"),
                    explicits = modifiers(line, "explicitModifiers"),
                    granted = modifiers(line, "grantedSkillModifiers"),
                    requirements = modifiers(line, "requirementModifiers"),
                    spark = sparkline(line),
                    pageUrl = pageUrl,
                    isUnique = true,
                )
            )
        }
        return result.sortedByDescending { it.divine }
    }

    fun sort(rates: List<MarketRate>, sort: MarketSort): List<MarketRate> = when (sort) {
        MarketSort.VALUE_DESC -> rates.sortedByDescending { it.divine }
        MarketSort.VALUE_ASC -> rates.sortedBy { it.divine }
        MarketSort.CHANGE_DESC -> rates.sortedByDescending { it.change }
        MarketSort.LISTINGS_DESC -> rates.sortedByDescending { it.listingCount.coerceAtLeast(0) * 1.0 + it.volume }
        MarketSort.NAME -> rates.sortedBy { if (it.koName.isNotBlank()) it.koName else it.name }
    }

    private fun findItem(core: JSONObject?, id: String): JSONObject? {
        val items = core?.optJSONArray("items") ?: return null
        for (i in 0 until items.length()) {
            val obj = items.optJSONObject(i) ?: continue
            if (obj.optString("id") == id) return obj
        }
        return null
    }

    /** 응답 안의 품목 메타데이터(최상단 items + core.items)를 id/상세 id로 찾을 수 있게 모은다. */
    private fun itemIndex(root: JSONObject): Map<String, JSONObject> {
        val out = HashMap<String, JSONObject>()
        fun collect(array: JSONArray?) {
            if (array == null) return
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val id = obj.optString("id")
                if (id.isNotBlank() && !out.containsKey(id)) out[id] = obj
                val details = obj.optString("detailsId")
                if (details.isNotBlank() && !out.containsKey(details)) out[details] = obj
            }
        }
        collect(root.optJSONArray("items"))
        collect(root.optJSONObject("core")?.optJSONArray("items"))
        return out
    }

    /**
     * poe.ninja 아이콘 경로를 실제로 열리는 주소로 만든다.
     * 빈 값이거나 `/gen/image/...` 같은 상대 경로가 그대로 들어오면 이미지가 통째로 비어 버린다.
     */
    fun resolveIcon(raw: String?): String {
        val value = raw?.trim().orEmpty()
        if (value.isEmpty()) return ""
        if (value.startsWith("data:")) return value
        if (value.startsWith("//")) return "https:$value"
        if (value.startsWith("http://")) return "https://" + value.removePrefix("http://")
        if (value.startsWith("https://")) return value
        val host = "https://web.poecdn.com"
        return host + if (value.startsWith("/")) value else "/$value"
    }

    private fun sparkChange(line: JSONObject): Double {
        val spark = line.optJSONObject("sparkLine") ?: line.optJSONObject("sparkline")
        return spark?.optDouble("totalChange", 0.0) ?: 0.0
    }

    private fun sparkline(line: JSONObject): List<Double?> {
        val spark = line.optJSONObject("sparkLine") ?: line.optJSONObject("sparkline") ?: return emptyList()
        val data = spark.optJSONArray("data") ?: return emptyList()
        val out = ArrayList<Double?>(data.length())
        for (i in 0 until data.length()) {
            if (data.isNull(i)) out.add(null) else out.add(data.optDouble(i))
        }
        return out
    }

    private fun modifiers(line: JSONObject, key: String): List<String> {
        val arr = line.optJSONArray(key) ?: return emptyList()
        val out = ArrayList<String>(arr.length())
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val text = cleanTags(obj.optString("text"))
            if (text.isNotBlank()) out.add(text)
        }
        return out
    }

    /** poe.ninja는 [Physical|물리] 형태의 태그를 쓴다. 표시용으로 안쪽 이름만 남긴다. */
    fun cleanTags(text: String): String {
        if (text.isBlank()) return ""
        return text.replace(Regex("\\[([^\\]|]*)\\|([^\\]]*)\\]")) { m ->
            val visible = m.groupValues[2].ifBlank { m.groupValues[1] }
            visible
        }.replace(Regex("\\[([^\\]]*)\\]")) { m -> m.groupValues[1] }
    }

    fun poe2dbUrl(name: String): String =
        "https://poe2db.tw/kr/" + URLEncoder.encode(name.replace(" ", "_"), "UTF-8")

    fun clearCache() {
        cache.clear()
        cachedLeagues = null
    }

    fun currencyLabel(id: String): String = when (id) {
        "divine" -> "디바인"
        "exalted" -> "엑잘"
        "chaos" -> "카오스"
        else -> id
    }

    fun formatValue(value: Double): String = when {
        value >= 1000 -> "%,.0f".format(value)
        value >= 100 -> "%.0f".format(value)
        value >= 10 -> "%.1f".format(value)
        value >= 1 -> "%.2f".format(value)
        value >= 0.01 -> "%.3f".format(value)
        value > 0 -> "%.4f".format(value)
        else -> "0"
    }.trimEnd('0').trimEnd('.').ifEmpty { "0" }
}

/** poe2db 아이템 페이지에서 드랍/제작 정보만 뽑아 온다. */
data class MarketSource(
    val droppedBy: List<String> = emptyList(),
    val forged: String = "",
    val found: Boolean = false,
)

object Poe2dbSource {

    private const val UA = "PoE2DB-KR-Android/1.0"
    private val cache = HashMap<String, MarketSource>()

    private val TAG = Regex("<[^>]+>")

    fun lookup(url: String): MarketSource {
        if (url.isBlank()) return MarketSource()
        cache[url]?.let { return it }
        val html = runCatching {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 8000
            conn.readTimeout = 12000
            conn.setRequestProperty("User-Agent", UA)
            try {
                conn.inputStream.bufferedReader().use { it.readText() }
            } finally {
                conn.disconnect()
            }
        }.getOrElse { return MarketSource() }
        val source = parse(html)
        cache[url] = source
        return source
    }

    /** poe2db 아이템 페이지 HTML에서 드랍/제작 정보를 뽑는다. */
    fun parse(html: String): MarketSource {
        val dropped = ArrayList<String>()
        Regex(">([^<>]{1,40})</a>」Dropped by").findAll(html).forEach { match ->
            val name = match.groupValues[1].trim()
            if (name.isNotEmpty() && dropped.none { it == name }) dropped.add(name)
        }
        if (html.contains("Dropped by") && dropped.isEmpty()) {
            // "월드 드랍"처럼 링크 없는 표기
            Regex(">([^<>]{2,30})」Dropped by").find(html)?.let {
                val name = it.groupValues[1].trim()
                if (name.isNotEmpty()) dropped.add(name)
            }
        }

        var forged = ""
        val forgeIndex = html.indexOf("id=\"Forgerecipe\"")
        if (forgeIndex >= 0) {
            val bodyStart = html.indexOf('>', forgeIndex).let { if (it >= 0) it + 1 else forgeIndex }
            val block = html.substring(bodyStart, minOf(html.length, bodyStart + 4000))
            val end = block.indexOf("class=\"card mb-1\"")
            var slice = if (end > 0) block.substring(0, end) else block
            val tableEnd = slice.lastIndexOf("</table>")
            if (tableEnd > 0) slice = slice.substring(0, tableEnd + 8)
            forged = slice.replace(TAG, " ")
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace(Regex("\\s+"), " ")
                .trim()
                .removePrefix("Forge recipe")
                .replace("장착 Recipe /1", "·")
                .replace("제안 당신의 제안 Note", "")
                .trim()
        }

        return MarketSource(dropped, forged, true)
    }
}

class MarketAdapter(
    private val onClick: (MarketRate) -> Unit,
) : ListAdapter<MarketRate, MarketAdapter.Holder>(Diff) {

    class Holder(val binding: ItemMarketBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val binding = ItemMarketBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return Holder(binding)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val rate = currentList[position]
        with(holder.binding) {
            icon.load(rate.iconUrl.ifBlank { null }) {
                placeholder(R.drawable.bg_icon)
                error(R.drawable.bg_icon)
                crossfade(100)
            }
            tvName.text = if (rate.koName.isNotBlank()) rate.koName else rate.name
            tvNameEn.text = listOf(rate.name, rate.baseType)
                .filter { it.isNotBlank() && it != rate.koName }
                .joinToString(" · ")
                .ifBlank { rate.koName.takeIf { rate.name != it } ?: "" }
            tvNameEn.isVisible = tvNameEn.text.isNotBlank()
            tvPrice.text = "${Market.formatValue(rate.divine)} 디바인"
            val ex = "≈ ${Market.formatValue(rate.exalted)} 엑잘 · ${Market.formatValue(rate.chaos)} 카오스"
            tvPriceSub.text = ex
            tvPriceSub.isVisible = rate.exalted > 0
            tvListings.text = when {
                rate.listingCount > 0 -> "매물 ${rate.listingCount}건"
                rate.volume > 0 -> "거래량 ${Market.formatValue(rate.volume)} ${Market.currencyLabel(rate.volumeCurrency)}"
                else -> "매물 집계 없음"
            }
            when {
                rate.change > 0.05 -> {
                    tvChange.text = "+%.1f%%".format(rate.change)
                    tvChange.setTextColor(contextColor(root, R.color.resist_high))
                }
                rate.change < -0.05 -> {
                    tvChange.text = "%.1f%%".format(rate.change)
                    tvChange.setTextColor(contextColor(root, R.color.resist_low))
                }
                else -> {
                    tvChange.text = "0%"
                    tvChange.setTextColor(contextColor(root, R.color.text_muted))
                }
            }
            root.setOnClickListener { onClick(rate) }
        }
    }

    private fun contextColor(view: android.view.View, res: Int): Int =
        androidx.core.content.ContextCompat.getColor(view.context, res)

    private object Diff : DiffUtil.ItemCallback<MarketRate>() {
        override fun areItemsTheSame(oldItem: MarketRate, newItem: MarketRate) =
            oldItem.id == newItem.id && oldItem.corrupted == newItem.corrupted

        override fun areContentsTheSame(oldItem: MarketRate, newItem: MarketRate) = oldItem == newItem
    }
}
