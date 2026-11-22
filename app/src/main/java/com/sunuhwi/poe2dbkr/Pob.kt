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
    /** PoB가 계산에 쓰는 활성 스킬 그룹인지 */
    var isMain: Boolean = false,
) {
    /** 그룹 이름이 비어 있으면 첫 스킬 이름으로 대신한다 */
    val title: String
        get() = label.ifBlank { gems.firstOrNull()?.name ?: "스킬" }
}

data class PobItem(
    var id: Int = 0,
    var name: String = "",
    var rarity: String = "Rare",
    val lines: MutableList<String> = mutableListOf(),
    /** 베이스 타입 (예: Rune Crossbow, Gelid Staff) */
    var baseType: String = "",
    /** 아이템 클래스 (예: Crossbow, Staff, Body Armour) */
    var itemClass: String = "",
    /** 스틱맨이 드는 무기 모양 */
    var weapon: WeaponVisualType = WeaponVisualType.NONE,
) {
    /** 화면에 보여줄 이름: 고유/희귀 이름 + 베이스 타입 */
    val displayName: String
        get() = when {
            name.isNotBlank() && baseType.isNotBlank() && !name.equals(baseType, true) -> "$name · $baseType"
            name.isNotBlank() -> name
            baseType.isNotBlank() -> baseType
            rarity.isNotBlank() -> rarity
            else -> ""
        }
}

/** PoB가 계산해 둔 스킬별 DPS (Full DPS 목록) */
data class PobFullDpsSkill(
    val name: String,
    val dps: Double,
    val source: String = "",
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
    var fullDps: Double = 0.0,
    var activeItemSet: Int = 0,
    val fullDpsSkills: MutableList<PobFullDpsSkill> = mutableListOf(),
    val itemSets: MutableMap<Int, MutableMap<String, Int>> = linkedMapOf(),
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
        "Weapon 1 Swap" to "무기 1 교체",
        "Weapon 2 Swap" to "무기 2 교체",
        "Helmet" to "투구",
        "Body Armour" to "갑옷",
        "Gloves" to "장갑",
        "Boots" to "장화",
        "Amulet" to "목걸이",
        "Ring 1" to "반지 1",
        "Ring 2" to "반지 2",
        "Ring 3" to "반지 3",
        "Belt" to "허리띠",
        "Charm 1" to "부적 1",
        "Charm 2" to "부적 2",
        "Charm 3" to "부적 3",
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
        val itemText = StringBuilder()
        var currentSetId = 0
        var currentSet: MutableMap<String, Int>? = null

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
                        if (stat == "FullDPS" && value != null) build.fullDps = value
                    }
                    "FullDPSSkill" -> {
                        val name = parser.getAttributeValue(null, "stat").orEmpty()
                        val dps = parser.getAttributeValue(null, "value")?.toDoubleOrNull() ?: 0.0
                        val source = parser.getAttributeValue(null, "source").orEmpty()
                        if (name.isNotBlank()) build.fullDpsSkills.add(PobFullDpsSkill(name, dps, source))
                    }
                    "SkillGroup", "Skill" -> {
                        val label = parser.getAttributeValue(null, "label").orEmpty()
                        val enabled = parser.getAttributeValue(null, "enabled") != "false"
                        val main = parser.getAttributeValue(null, "mainActiveSkill")
                        val isMain = main != null && main != "nil" && main != "0" && main.isNotBlank()
                        build.skills.add(
                            PobSkillGroup(
                                label = label,
                                enabled = enabled,
                                isMain = isMain,
                            )
                        )
                    }
                    "Gem" -> {
                        val name = parser.getAttributeValue(null, "name")
                            ?: parser.getAttributeValue(null, "nameSpec").orEmpty()
                        val level = parser.getAttributeValue(null, "level")?.toIntOrNull() ?: 20
                        val enabled = parser.getAttributeValue(null, "enabled") != "false"
                        val skillId = parser.getAttributeValue(null, "skillId").orEmpty()
                        if (name.isNotBlank()) {
                            build.skills.lastOrNull()?.gems?.add(PobGem(name, level, enabled, skillId))
                        }
                    }
                    "Items" -> {
                        build.activeItemSet =
                            parser.getAttributeValue(null, "activeItemSet")?.toIntOrNull() ?: 0
                    }
                    "ItemSet" -> {
                        val setId = parser.getAttributeValue(null, "id")?.toIntOrNull()
                            ?: (build.itemSets.keys.maxOrNull() ?: 0) + 1
                        currentSetId = setId
                        currentSet = build.itemSets.getOrPut(setId) { linkedMapOf() }
                    }
                    "Item" -> {
                        inItem = true
                        itemText.setLength(0)
                        val id = parser.getAttributeValue(null, "id")?.toIntOrNull() ?: 0
                        val rarity = parser.getAttributeValue(null, "rarity").orEmpty().ifBlank { "Rare" }
                        val name = parser.getAttributeValue(null, "name").orEmpty()
                        currentItem = PobItem(id = id, name = name, rarity = rarity)
                    }
                    "Slot" -> {
                        val slotName = parser.getAttributeValue(null, "name").orEmpty()
                        val itemId = parser.getAttributeValue(null, "itemId")?.toIntOrNull() ?: 0
                        if (slotName.isNotEmpty() && itemId > 0) {
                            val set = currentSet
                            if (set != null) set[slotName] = itemId else build.slots[slotName] = itemId
                        }
                    }
                    "Spec" -> {
                        build.treeTitle = parser.getAttributeValue(null, "title").orEmpty()
                        build.treeNodes = parser.getAttributeValue(null, "nodesCount")?.toIntOrNull() ?: 0
                    }
                }
            } else if (event == XmlPullParser.TEXT || event == XmlPullParser.CDSECT) {
                if (inItem) itemText.append(parser.text ?: "")
            } else if (event == XmlPullParser.END_TAG) {
                when (parser.name) {
                    "Item" -> {
                        val item = currentItem
                        if (item != null) {
                            PobItemParser.apply(item, itemText.toString())
                            build.items[item.id] = item
                        }
                        inItem = false
                        currentItem = null
                        itemText.setLength(0)
                    }
                    "ItemSet" -> currentSet = null
                }
            }
            event = parser.next()
        }

        // 활성 아이템 세트의 슬롯을 쓴다. 세트 정보가 없으면 그동안 모은 슬롯을 그대로 쓴다.
        val active = build.itemSets[build.activeItemSet]
            ?: build.itemSets.values.firstOrNull { it.isNotEmpty() }
        if (active != null) {
            build.slots.clear()
            build.slots.putAll(active)
        }
        // 이름이 비어 있던 스킬 그룹은 첫 젬 이름으로 채운다 (PoB 2 형식은 label이 비어 있다)
        build.skills.forEach { group ->
            if (group.label.isBlank()) {
                group.label = group.gems.firstOrNull()?.name ?: "스킬"
            }
        }
        return build
    }

    /** 계산·전투에 쓰는 메인 스킬 그룹을 고른다. preferred는 1부터 시작하는 그룹 번호(0이면 자동). */
    fun mainGroup(build: PobBuild, preferred: Int = 0): PobSkillGroup? {
        val usable = build.skills.filter { it.gems.any { gem -> gem.enabled } }
        if (usable.isEmpty()) return null
        if (preferred in 1..build.skills.size) {
            val picked = build.skills[preferred - 1]
            if (picked.enabled && picked.gems.any { it.enabled }) return picked
        }
        usable.firstOrNull { it.isMain }?.let { return it }
        build.skills.firstOrNull { it.isMain && it.enabled }?.let { return it }
        usable.firstOrNull { group ->
            group.gems.any { gem ->
                gem.enabled && !PobCalculator.isSupport(gem) && PobTriggers.kindOf(gem) == null
            }
        }?.let { return it }
        return usable.firstOrNull { it.enabled } ?: usable.first()
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
        serializer.attribute(null, "activeItemSet", "1")
        build.items.values.forEach { item ->
            serializer.startTag(null, "Item")
            serializer.attribute(null, "id", item.id.toString())
            serializer.attribute(null, "rarity", item.rarity)
            if (item.name.isNotBlank() && item.rarity == "Unique") {
                serializer.attribute(null, "name", item.name)
            }
            val text = buildString {
                append("\nRarity: ").append(item.rarity.uppercase()).append('\n')
                if (item.name.isNotBlank() && item.rarity != "Normal") append(item.name).append('\n')
                if (item.baseType.isNotBlank() && !item.baseType.equals(item.name, true)) {
                    append(item.baseType).append('\n')
                }
                item.lines.forEach { append(it).append('\n') }
            }
            serializer.text(text)
            serializer.endTag(null, "Item")
        }
        serializer.startTag(null, "ItemSet")
        serializer.attribute(null, "id", "1")
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

    /**
     * 빌드가 실제로 들고 있는 무기. 무기 슬롯(1·2·교체)을 먼저 보고,
     * 슬롯 정보가 빠진 빌드면 아이템 목록에서 무기 클래스 아이템을 직접 찾는다.
     * (예전에는 "Weapon 1" 하나만 봐서, 무기를 쇠뇌로 바꿔도 활 모션으로 나오는 일이 있었다.)
     */
    fun weaponItem(build: PobBuild, overrides: Map<String, PobItem> = emptyMap()): PobItem? {
        val order = listOf("Weapon 1", "Weapon 2", "Weapon 1 Swap", "Weapon 2 Swap")
        for (slot in order) {
            val item = overrides[slot] ?: itemForSlot(build, slot)
            if (item != null && item.weapon != WeaponVisualType.NONE) return item
        }
        val candidates = (build.items.values.asSequence() + overrides.values.asSequence())
            .filter { it.weapon != WeaponVisualType.NONE }
            .distinctBy { it.id }
            .toList()
        return candidates.firstOrNull { it.rarity.equals("Unique", true) } ?: candidates.firstOrNull()
    }

    /** 유니크 아이템을 슬롯에 새로 장착한다 */
    fun equipItem(build: PobBuild, slot: String, item: PobItem) {
        val newId = (build.items.keys.maxOrNull() ?: 0) + 1
        val copy = PobItem(
            id = newId,
            name = item.name,
            rarity = item.rarity,
            lines = item.lines,
            baseType = item.baseType,
            itemClass = item.itemClass,
            weapon = item.weapon,
        )
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
