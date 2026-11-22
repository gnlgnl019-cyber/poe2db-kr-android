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

data class MarketRate(
    val id: String,
    val name: String,
    val koName: String,
    val iconUrl: String,
    val value: Double,
    val volume: Double,
    val volumeCurrency: String,
    val change: Double,
    val detailsId: String,
)

/** poe.ninja PoE2 공개 시세 API (5분 메모리 캐시) */
object Market {

    private const val BASE = "https://poe.ninja/poe2/api/economy"
    private const val UA = "PoE2DB-KR-Android/1.0"
    private const val CACHE_MS = 5 * 60 * 1000L

    val TYPES = linkedMapOf(
        "Currency" to "화폐",
        "Runes" to "룬",
        "Essences" to "에센스",
        "SoulCores" to "영혼 핵",
        "Fragments" to "조각",
        "UncutGems" to "미가공 젬",
        "Ritual" to "징조",
        "Delirium" to "감정",
        "Breach" to "기폭제",
    )

    private val cache = HashMap<String, Pair<Long, List<MarketRate>>>()
    @Volatile
    private var cachedLeagues: Pair<Long, List<MarketLeague>>? = null

    private fun get(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 10000
        conn.readTimeout = 15000
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

    fun overview(league: String, type: String, koMap: Map<String, String>): List<MarketRate> {
        val key = "$league/$type"
        val hit = cache[key]
        if (hit != null && System.currentTimeMillis() - hit.first < CACHE_MS) return hit.second

        val url = "$BASE/exchange/current/overview?league=${URLEncoder.encode(league, "UTF-8")}&type=$type"
        val root = JSONObject(get(url))
        val core = root.optJSONObject("core")
        val primary = core?.optString("primary", "divine").orEmpty().ifBlank { "divine" }
        val lines = root.optJSONArray("lines") ?: JSONArray()

        val rates = ArrayList<MarketRate>(lines.length())
        for (i in 0 until lines.length()) {
            val line = lines.optJSONObject(i) ?: continue
            val id = line.optString("id")
            val info = findItem(root, id)
            val name = info?.optString("name", id).orEmpty().ifBlank { id }
            val image = info?.optString("image", "").orEmpty()
            rates.add(
                MarketRate(
                    id = id,
                    name = name,
                    koName = koMap[name.lowercase()] ?: "",
                    iconUrl = if (image.startsWith("http")) image else "https://web.poecdn.com$image",
                    value = line.optDouble("primaryValue", 0.0),
                    volume = line.optDouble("volumePrimaryValue", 0.0),
                    volumeCurrency = line.optString("maxVolumeCurrency", primary),
                    change = line.optJSONObject("sparkline")?.optDouble("totalChange", 0.0) ?: 0.0,
                    detailsId = info?.optString("detailsId", id).orEmpty().ifBlank { id },
                )
            )
        }
        val sorted = rates.sortedWith(compareByDescending { it.value * it.volume })
        cache[key] = System.currentTimeMillis() to sorted
        return sorted
    }

    private fun findItem(root: JSONObject, id: String): JSONObject? {
        val items = root.optJSONArray("items") ?: return null
        for (i in 0 until items.length()) {
            val obj = items.optJSONObject(i) ?: continue
            if (obj.optString("id") == id) return obj
        }
        return null
    }

    fun clearCache() {
        cache.clear()
        cachedLeagues = null
    }

    fun currencyLabel(id: String): String = when (id) {
        "divine" -> "디바인"
        "exalted" -> "엑자일드"
        "chaos" -> "카오스"
        else -> id
    }

    fun formatValue(value: Double): String = when {
        value >= 100 -> "%.0f".format(value)
        value >= 10 -> "%.1f".format(value)
        value >= 1 -> "%.2f".format(value)
        value >= 0.01 -> "%.3f".format(value)
        else -> "%.4f".format(value)
    }.trimEnd('0').trimEnd('.').ifEmpty { "0" }
}

class MarketAdapter : ListAdapter<MarketRate, MarketAdapter.Holder>(Diff) {

    class Holder(val binding: ItemMarketBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val binding = ItemMarketBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return Holder(binding)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val rate = currentList[position]
        with(holder.binding) {
            icon.load(rate.iconUrl) {
                placeholder(R.drawable.bg_icon)
                error(R.drawable.bg_icon)
                crossfade(100)
            }
            tvName.text = if (rate.koName.isNotBlank()) rate.koName else rate.name
            tvNameEn.text = rate.name.takeIf { it != rate.koName } ?: ""
            tvNameEn.isVisible = tvNameEn.text.isNotBlank()
            tvPrice.text = "${Market.formatValue(rate.value)} 디바인"
            tvVolume.text = if (rate.volume > 0) {
                "거래량 ${Market.formatValue(rate.volume)} ${Market.currencyLabel(rate.volumeCurrency)}"
            } else {
                "거래량 집계 없음"
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
        }
    }

    private fun contextColor(view: android.view.View, res: Int): Int =
        androidx.core.content.ContextCompat.getColor(view.context, res)

    private object Diff : DiffUtil.ItemCallback<MarketRate>() {
        override fun areItemsTheSame(oldItem: MarketRate, newItem: MarketRate) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: MarketRate, newItem: MarketRate) = oldItem == newItem
    }
}
