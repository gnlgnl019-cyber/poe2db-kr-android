package com.sunuhwi.poe2dbkr

import android.content.Context
import android.util.Base64
import android.util.Xml
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlSerializer
import java.io.ByteArrayOutputStream
import java.io.StringReader
import java.io.StringWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.Deflater
import java.util.zip.Inflater

data class PobGem(
    var name: String,
    var level: Int = 20,
    var enabled: Boolean = true,
    var skillId: String = "",
)

data class PobSkillGroup(
    var label: String,
    var enabled: Boolean = true,
    val gems: MutableList<PobGem> = mutableListOf(),
)

data class PobItem(
    var id: Int = 0,
    var name: String = "",
    var rarity: String = "Rare",
    val lines: MutableList<String> = mutableListOf(),
)

data class PobBuild(
    var className: String = "",
    var ascendClassName: String = "",
    var level: Int = 0,
    var mainSocketGroup: Int = 1,
    val stats: MutableMap<String, Double> = linkedMapOf(),
    val skills: MutableList<PobSkillGroup> = mutableListOf(),
    val items: MutableMap<Int, PobItem> = linkedMapOf(),
    val slots: MutableMap<String, Int> = linkedMapOf(),
    var treeTitle: String = "",
    var treeNodes: Int = 0,
    var rawXml: String = "",
) {
    val title: String
        get() = listOf(
            if (ascendClassName.isBlank()) className else "$className · $ascendClassName",
            if (level > 0) "레벨 $level" else "",
        ).filter { it.isNotBlank() }.joinToString(" / ")
}

object Pob {

    /** 장비 슬롯 (PoB 슬롯 이름 → 표시 이름) */
    val SLOTS = linkedMapOf(
        "Weapon 1" to "무기 1",
        "Weapon 2" to "무기 2",
        "Helmet" to "투구",
        "Body Armour" to "갑옷",
        "Gloves" to "장갑",
        "Boots" to "장화",
        "Amulet" to "목걸이",
        "Ring 1" to "반지 1",
        "Ring 2" to "반지 2",
        "Belt" to "허리띠",
    )

    val FLASK_PREFIX = "Flask"

    private const val SAMPLE_CODE =
        "eNqtWNty2zYQfU6-AsOZTNvx6EKKkiiNnIwtN7anUaJaTvIMk5CEGgQUErSj_lG_oz_WBUALFC-WnDQPGQh7AOyevdKTd99jhh5I" +
        "klLBTx233XUQ4aGIKF-dOplctgLn3dvXkzmW60_L84wyJbEHPLftdtvdnoNiEWWMfKkI3r5-NdHnECMPhJ06I89BIcNp-hHH5NS5" +
        "wXxFEgfhNCQ8mlrBBcER2RK4GVO-EOE9kZeJyDagpYMeKHmciQhg07MP04WD7jCPqDx1PgoORzaYyzURfIb_EsmliCr7lBf3QcdX" +
        "kznDW5IsJJYohf9OnVshMbuYw-UPmGXwlB94vX63PXQ69QemIr6jnETFM33X9bp-22s6cyYlDu_TOWySUPBod7DX9hsPTRMqp2vM" +
        "Q2LhbnvwHHyWMUk3jCqqd0eatQIH4xW5wDH8vzvg-m7X7zad-UCXFuqD1U3AGeZ4BxwFjbjfOUlW28WaEmZpCQaN-LMkFpk1z1XO" +
        "arz7Aas4teBg1G0Ev6cJuSEpTeUOP2wmW7CoDO43c7ZaSw4ZVToxCBqvX2ORltCt3JGTjk40tbpNCEE4lPSBLDYkVDmj71M_kKSS" +
        "2QRD7EkJhJNEPDqIQ2KlU5FxeM_tDhyUJZC3ayk36bjT2QjiRXdt-di5TzobSFd4o5XeU8ZaEl7t6IdeTT4nzCw6-WrSUY9rNZV2" +
        "arFQp1KUikRekjg930LmnDoyyfKU1HKd9FCU8B0jUS5FDN-pWuKiHYPoTCtv3oTbENdVpCxHWtPrqCDJBXl5grhF3zLMqNyaH6WX" +
        "9fk5ThQ3xknF567IUhbeWGSbDRhnduseqNxfuXEGT1HM0C2JN6J6dS7OpT_2xCLEUkLhXosa3feEP3a_9cE1X2Ym78rP7DAWcuix" +
        "JWZp_hrE1i5UjoscD11BiYOuJJbodp3xCApj1ZllhFXbyD4td5Ifo0ZVi2dYUeLjCbFPvJyPHvpKeYQuVFOpMlGUWSXV7tNmrps7" +
        "eqlu-c9ULa8lVAGNUStEI93r86sD30EJTvTNNzgB1Y1uRlm1M0YLKZI4XcPVkdrMnYO-CrZEi3__Ubqic_GoZK38n1rnW38axcfo" +
        "xOuiX3G2igmXJPpNyebrbUpDyEPTE8fIc_1Wb1TB2Vh_AgZBC4aAMk71ZH3fQib0niDTzsdo1O6_UfJ8LkAbUN9MBmPkwkhQvqdo" +
        "xg35lkGrUtJ0rJVRzIEGfhmp6W2UnnjBGyRFbgESMAygWxoTZIeIshonw5E6EePvNM7gapgFKpCer2-1DJk2pp1SwioFKA8TglMS" +
        "mRPoBkc0S0vISUeZsh8yng0Z7-UhQ6AbJOgPKgkyo0eZHbtbjBftU-1DZeM5g3kVYsQzvtSDyRjBwFU2NJ9DwLfB6DnX7jnMqzhs" +
        "4B5k3_U0-2qWeYb4E7evYbouPQfrHevLOg_1rIfcwx6CMgMN6A48ovbeEyY4mgkYYbboSoiKf64Ii4ncI9evIV4Pl7mLVZIezb5b" +
        "YX80OMz-0NCqprdneHX9YtyrQRnAKwK6YgmWQA5oEuo49S2nvcOc3lL46PolRZcJDjWtsEg3ypEzTFm1PkZbZKJ4j9eRFxzg1R0O" +
        "jia2VyHW9fyDzHreMQG7V09MbUUwjsKPw9Hat8x2DzM7xRJ4ncNIqWmED7slBfYuGZTRtGyh3d1x6g38oxnrVhgbeodD0T2qEIyO" +
        "oLWOrcGL2FIjRCrJRhdWRsg9tGch02oA5ps7mmpq6fE09bp7aQY-UNeYgNDGQzk4SOPgCBrrCBruCBqOLEEzvKLhPkN6a4z_zhKd" +
        "oVMBEyc6izNmqlvRoPrdIgnDUSVW_IqRDRoHVuPgiIZKVxwU_YJhWNAd9VG0oMlyoAnqS2Vmqdnb0zuoVoVhHfNGFNRFrRb1B0da" +
        "O_opa-c4w_-XoYO9ZFbdwFQ8t7H3Npjkdq1Nw8M2XcGXPzqHzMyHGsrROWGV8Krb27NoWO2UR8wp_s9MFm5hnvjM6bdsZ2DhE0a1" +
        "4SQ2FpdnuKK6SpVYQJTlg_ASBkOCwzWCJhfD8EFhKCUh6MO2VXUWRJqvqAUT8kkHgjfQxUFHGGbia_N5Y_5Ks_-BWD3j2TNeDcoM" +
        "PRbTq8EUurgF-jVA05Uspl97GVRkCxnUQExRsphhDUZlRZGPoAlTsH9Upw7EYoHVbg3kPcPp_R737tMHqfWXWeuP0angS7rSe0_L" +
        "SWf_7-BvX_8Hq-S9-w=="
    fun decode(input: String): PobBuild {
        val text = input.trim().trim('\n', '\r', ' ', '"')
        val xml = if (text.startsWith("<")) {
            text
        } else {
            inflate(text.replace("-", "+").replace("_", "/").let { s ->
                val pad = s.length % 4
                if (pad == 0) s else s + "=".repeat(4 - pad)
            })
        }
        return parseXml(xml)
    }

    fun encode(build: PobBuild): String {
        val xml = toXml(build)
        return deflate(xml).replace("+", "-").replace("/", "_").replace("=", "")
    }

    fun fetchPobbIn(id: String): PobBuild {
        val clean = id.trim().trim('/')
            .replace(Regex("^https?://(www\\.)?pobb\\.in/"), "")
            .takeWhile { it != '/' && it != '?' }
        if (clean.isEmpty()) throw IllegalArgumentException("pobb.in 링크 형식이 아닙니다")
        val url = URL("https://pobb.in/$clean/raw")
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = 10000
        conn.readTimeout = 15000
        conn.setRequestProperty("User-Agent", "PoE2DB-KR-Android/1.0")
        val body = try {
            conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
        return decode(body)
    }

    private fun inflate(code: String): String {
        val data = Base64.decode(code, Base64.DEFAULT)
        val inflater = Inflater()
        inflater.setInput(data)
        val out = ByteArrayOutputStream()
        val buf = ByteArray(8192)
        try {
            while (!inflater.finished()) {
                val n = inflater.inflate(buf)
                if (n == 0 && inflater.needsInput()) break
                out.write(buf, 0, n)
            }
        } finally {
            inflater.end()
        }
        return out.toString("UTF-8")
    }

    private fun deflate(xml: String): String {
        val deflater = Deflater(Deflater.BEST_COMPRESSION, false)
        deflater.setInput(xml.toByteArray(Charsets.UTF_8))
        deflater.finish()
        val out = ByteArrayOutputStream()
        val buf = ByteArray(8192)
        while (!deflater.finished()) {
            val n = deflater.deflate(buf)
            out.write(buf, 0, n)
        }
        deflater.end()
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }

    fun parseXml(xml: String): PobBuild {
        val build = PobBuild(rawXml = xml)
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(StringReader(xml))

        var inItem = false
        var currentItem: PobItem? = null
        var rawName: String = ""

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                when (parser.name) {
                    "Build" -> {
                        build.className = parser.getAttributeValue(null, "className").orEmpty()
                        build.ascendClassName =
                            parser.getAttributeValue(null, "ascendClassName").orEmpty()
                        build.level = parser.getAttributeValue(null, "level")?.toIntOrNull() ?: 0
                        build.mainSocketGroup =
                            parser.getAttributeValue(null, "mainSocketGroup")?.toIntOrNull() ?: 1
                    }
                    "PlayerStat" -> {
                        val stat = parser.getAttributeValue(null, "stat").orEmpty()
                        val value = parser.getAttributeValue(null, "value")?.toDoubleOrNull()
                        if (stat.isNotEmpty() && value != null) build.stats[stat] = value
                    }
                    "SkillGroup" -> {
                        val label = parser.getAttributeValue(null, "label").orEmpty()
                        val enabled = parser.getAttributeValue(null, "enabled") != "false"
                        build.skills.add(PobSkillGroup(label.ifBlank { "스킬 ${build.skills.size + 1}" }, enabled))
                    }
                    "Gem" -> {
                        val name = parser.getAttributeValue(null, "name").orEmpty()
                        val level = parser.getAttributeValue(null, "level")?.toIntOrNull() ?: 20
                        val enabled = parser.getAttributeValue(null, "enabled") != "false"
                        val skillId = parser.getAttributeValue(null, "skillId").orEmpty()
                        if (name.isNotEmpty()) {
                            build.skills.lastOrNull()?.gems?.add(PobGem(name, level, enabled, skillId))
                        }
                    }
                    "Item" -> {
                        inItem = true
                        rawName = ""
                        val id = parser.getAttributeValue(null, "id")?.toIntOrNull() ?: 0
                        val rarity = parser.getAttributeValue(null, "rarity").orEmpty().ifBlank { "Rare" }
                        val name = parser.getAttributeValue(null, "name").orEmpty()
                        currentItem = PobItem(id = id, name = name, rarity = rarity)
                    }
                    "Slot" -> {
                        val slotName = parser.getAttributeValue(null, "name").orEmpty()
                        val itemId = parser.getAttributeValue(null, "itemId")?.toIntOrNull() ?: 0
                        if (slotName.isNotEmpty() && itemId > 0) build.slots[slotName] = itemId
                    }
                    "Spec" -> {
                        build.treeTitle = parser.getAttributeValue(null, "title").orEmpty()
                        build.treeNodes = parser.getAttributeValue(null, "nodesCount")?.toIntOrNull() ?: 0
                    }
                }
            } else if (event == XmlPullParser.TEXT) {
                if (inItem) {
                    val text = parser.text ?: ""
                    if (text.isNotBlank()) {
                        text.split("\n").forEach { rawName += it + "\n" }
                    }
                }
            } else if (event == XmlPullParser.END_TAG) {
                when (parser.name) {
                    "Item" -> {
                        val item = currentItem
                        if (item != null) {
                            finalizeItem(item, rawName)
                            build.items[item.id] = item
                        }
                        inItem = false
                        currentItem = null
                        rawName = ""
                    }
                }
            }
            event = parser.next()
        }
        return build
    }

    private fun finalizeItem(item: PobItem, rawText: String) {
        val lines = rawText.split("\n")
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("---") && !it.startsWith("- - -") }
        val fixed = lines.filterNot {
            it.startsWith("Rare:") || it.startsWith("Magic:") || it.startsWith("Normal:") ||
                it.startsWith("Unidentified:")
        }
        if (item.name.isBlank()) {
            item.name = fixed.firstOrNull { l ->
                !l.endsWith(":") && !l.contains(Regex("^Quality:|^Level:|^Item Level:|^Requirements:"))
            } ?: item.rarity
        }
        item.lines.addAll(fixed)
    }

    fun toXml(build: PobBuild): String {
        val serializer = Xml.newSerializer()
        val writer = StringWriter()
        serializer.setOutput(writer)
        serializer.startDocument("UTF-8", null)
        serializer.startTag(null, "PathOfBuilding")
        serializer.attribute(null, "moduleVersion", "21.10.03")

        serializer.startTag(null, "Build")
        serializer.attribute(null, "level", build.level.coerceAtLeast(1).toString())
        serializer.attribute(null, "className", build.className)
        serializer.attribute(null, "ascendClassName", build.ascendClassName)
        serializer.attribute(null, "mainSocketGroup", build.mainSocketGroup.toString())
        serializer.attribute(null, "viewMode", "CALCS")
        for ((stat, value) in build.stats) {
            serializer.startTag(null, "PlayerStat")
            serializer.attribute(null, "stat", stat)
            serializer.attribute(null, "value", trimNum(value))
            serializer.endTag(null, "PlayerStat")
        }
        serializer.endTag(null, "Build")

        serializer.startTag(null, "Tree")
        serializer.attribute(null, "activeSpec", "1")
        serializer.startTag(null, "Spec")
        serializer.attribute(null, "title", build.treeTitle.ifBlank { "빌드" })
        serializer.attribute(null, "nodesCount", build.treeNodes.toString())
        serializer.endTag(null, "Spec")
        serializer.endTag(null, "Tree")

        serializer.startTag(null, "Skills")
        build.skills.forEachIndexed { index, group ->
            serializer.startTag(null, "SkillGroup")
            serializer.attribute(null, "label", group.label)
            serializer.attribute(null, "enabled", group.enabled.toString())
            group.gems.forEach { gem ->
                serializer.startTag(null, "Gem")
                serializer.attribute(null, "name", gem.name)
                serializer.attribute(null, "skillId", gem.skillId)
                serializer.attribute(null, "level", gem.level.toString())
                serializer.attribute(null, "enabled", gem.enabled.toString())
                serializer.endTag(null, "Gem")
            }
            serializer.endTag(null, "SkillGroup")
        }
        serializer.endTag(null, "Skills")

        serializer.startTag(null, "Items")
        build.items.values.forEach { item ->
            serializer.startTag(null, "Item")
            serializer.attribute(null, "id", item.id.toString())
            serializer.attribute(null, "rarity", item.rarity)
            if (item.name.isNotBlank() && item.rarity == "Unique") {
                serializer.attribute(null, "name", item.name)
            }
            serializer.text("\n" + item.lines.joinToString("\n") + "\n")
            serializer.endTag(null, "Item")
        }
        serializer.startTag(null, "ItemSet")
        build.slots.forEach { (slot, itemId) ->
            serializer.startTag(null, "Slot")
            serializer.attribute(null, "name", slot)
            serializer.attribute(null, "itemId", itemId.toString())
            serializer.endTag(null, "Slot")
        }
        serializer.endTag(null, "ItemSet")
        serializer.endTag(null, "Items")

        serializer.endTag(null, "PathOfBuilding")
        serializer.endDocument()
        return writer.toString()
    }

    private fun trimNum(value: Double): String =
        if (value == value.toLong().toDouble()) value.toLong().toString() else "%.2f".format(value)

    fun itemForSlot(build: PobBuild, slot: String): PobItem? =
        build.slots[slot]?.let { build.items[it] }

    /** 유니크 아이템을 슬롯에 새로 장착한다 */
    fun equipItem(build: PobBuild, slot: String, item: PobItem) {
        val newId = (build.items.keys.maxOrNull() ?: 0) + 1
        val copy = PobItem(id = newId, name = item.name, rarity = item.rarity, lines = item.lines)
        build.items[newId] = copy
        build.slots[slot] = newId
    }

    fun sampleCode(): String = SAMPLE_CODE
}

/** 최근 불러온 PoB 빌드 로컬 저장 (기기 안에만 보관) */
class PobStore(context: Context) {

    private val prefs = context.getSharedPreferences("pob_recent", Context.MODE_PRIVATE)
    private val KEY = "recent"

    data class Entry(val code: String, val label: String, val time: Long)

    fun all(): List<Entry> {
        val raw = prefs.getString(KEY, "[]") ?: "[]"
        val arr = runCatching { JSONArray(raw) }.getOrElse { return emptyList() }
        return (0 until arr.length()).mapNotNull { i ->
            val obj = arr.optJSONObject(i) ?: return@mapNotNull null
            Entry(obj.optString("code"), obj.optString("label"), obj.optLong("time"))
        }
    }

    fun add(code: String, label: String) {
        val raw = prefs.getString(KEY, "[]") ?: "[]"
        val arr = runCatching { JSONArray(raw) }.getOrDefault(JSONArray())
        val filtered = (0 until arr.length()).mapNotNull { i ->
            arr.optJSONObject(i)?.takeIf { it.optString("code") != code }
        }
        val entry = JSONObject()
            .put("code", code)
            .put("label", label)
            .put("time", System.currentTimeMillis())
        val next = JSONArray(listOf(entry) + filtered.take(4))
        prefs.edit().putString(KEY, next.toString()).apply()
    }

    fun clear() {
        prefs.edit().remove(KEY).apply()
    }
}
