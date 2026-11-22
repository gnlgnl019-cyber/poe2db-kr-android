package com.sunuhwi.poe2dbkr

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class BookmarkStore(context: Context) {

    private val prefs =
        context.applicationContext.getSharedPreferences("bookmarks", Context.MODE_PRIVATE)

    fun all(): List<CatalogItem> {
        val raw = prefs.getString(KEY_ITEMS, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val obj = array.optJSONObject(index) ?: continue
                    val url = obj.optString("url")
                    val name = obj.optString("name")
                    if (url.isEmpty() || name.isEmpty()) continue
                    add(
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
            }
        }.getOrDefault(emptyList())
    }

    fun urls(): Set<String> = all().mapTo(HashSet()) { it.url }

    fun contains(url: String): Boolean = all().any { it.url == url }

    fun toggle(item: CatalogItem): Boolean {
        val current = all().toMutableList()
        val index = current.indexOfFirst { it.url == item.url }
        if (index >= 0) {
            current.removeAt(index)
            save(current)
            return false
        }
        current.add(0, item)
        save(current)
        return true
    }

    fun remove(url: String) {
        save(all().filterNot { it.url == url })
    }

    private fun save(items: List<CatalogItem>) {
        val array = JSONArray()
        items.forEach { item ->
            array.put(
                JSONObject().apply {
                    put("name", item.name)
                    put("nameEn", item.nameEn)
                    put("cat", item.cat)
                    put("catLabel", item.catLabel)
                    put("tags", JSONArray(item.tags))
                    put("summary", item.summary)
                    put("meta", item.meta)
                    put("stats", JSONArray(item.stats))
                    put("icon", item.icon)
                    put("url", item.url)
                }
            )
        }
        prefs.edit().putString(KEY_ITEMS, array.toString()).apply()
    }

    private companion object {
        const val KEY_ITEMS = "items"
    }
}
