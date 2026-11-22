package com.sunuhwi.poe2dbkr

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.annotation.StringRes
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.Executors

data class CatalogItem(
    val name: String,
    val nameEn: String,
    val cat: String,
    val catLabel: String,
    val tags: List<String>,
    val summary: String,
    val meta: String,
    val stats: List<String>,
    val icon: String,
    val url: String,
) {
    val searchText: String =
        "$name $nameEn ${tags.joinToString(" ")} $summary $meta $catLabel".lowercase()
}

data class Category(val id: String, @StringRes val label: Int)

object Catalog {

    private const val ASSET = "poe2db_kr.json"
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    @Volatile
    private var cached: List<CatalogItem>? = null

    @Volatile
    var fetchedAt: String = ""
        private set

    @Volatile
    var total: Int = 0
        private set

    val categories = listOf(
        Category("weapon", R.string.chip_weapon),
        Category("armour", R.string.chip_armour),
        Category("jewellery", R.string.chip_jewellery),
        Category("rune", R.string.chip_rune),
        Category("currency", R.string.chip_currency),
        Category("unique", R.string.chip_unique),
        Category("skill_gem", R.string.chip_skill),
        Category("support_gem", R.string.chip_support),
        Category("ascendancy", R.string.chip_ascendancy),
        Category("keyword", R.string.chip_keyword),
    )

    /** 영문명 → 한글명 매핑 (시세 화면 병기용) */
    @Volatile
    private var koMap: Map<String, String>? = null

    fun nameKo(context: Context, nameEn: String): String {
        val map = koMap ?: buildKoMap(context).also { koMap = it }
        return map[nameEn.lowercase().trim()] ?: ""
    }

    private fun buildKoMap(context: Context): Map<String, String> {
        return read(context)
            .filter { it.nameEn.isNotBlank() && it.name.isNotBlank() && it.name != it.nameEn }
            .associateBy({ it.nameEn.lowercase().trim() }, { it.name })
    }

    fun load(context: Context, onReady: (List<CatalogItem>) -> Unit) {
        cached?.let {
            onReady(it)
            return
        }
        val appContext = context.applicationContext
        worker.execute {
            val loaded = runCatching { read(appContext) }.getOrDefault(emptyList())
            cached = loaded
            main.post { onReady(loaded) }
        }
    }

    private fun read(context: Context): List<CatalogItem> {
        val raw = context.assets.open(ASSET).bufferedReader().use { it.readText() }
        val root = JSONObject(raw)
        fetchedAt = root.optString("fetchedAt")
        val items = root.optJSONArray("items") ?: JSONArray()
        val result = ArrayList<CatalogItem>(items.length())
        for (index in 0 until items.length()) {
            val obj = items.optJSONObject(index) ?: continue
            val name = obj.optString("name")
            val url = obj.optString("url")
            if (name.isEmpty() || url.isEmpty()) continue
            result.add(
                CatalogItem(
                    name = name,
                    nameEn = obj.optString("nameEn"),
                    cat = obj.optString("cat"),
                    catLabel = obj.optString("catLabel"),
                    tags = obj.optJSONArray("tags").asStrings(),
                    summary = obj.optString("summary"),
                    meta = obj.optString("meta"),
                    stats = obj.optJSONArray("stats").asStrings(),
                    icon = obj.optString("icon"),
                    url = url,
                )
            )
        }
        total = result.size
        return result
    }
}

internal fun JSONArray?.asStrings(): List<String> {
    if (this == null) return emptyList()
    val result = ArrayList<String>(length())
    for (index in 0 until length()) {
        val value = optString(index)
        if (value.isNotEmpty()) result.add(value)
    }
    return result
}
