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
    /** PoB가 이 그룹에서 고른 스킬 번호 (mainActiveSkill, 1부터. 없으면 0) */
    var mainActiveSkill: Int = 0,
    /** 이 그룹을 준 곳 — "Item:15:Kraken Rosary" / "Tree:1739" (직접 소켓했으면 빈 값) */
    var source: String = "",
    /** 부여한 장비 슬롯 (예: Amulet) */
    var slot: String = "",
) {
    /** 장비가 부여한 스킬인지 (부적·목걸이의 Grants Skill 등) */
    val grantedByItem: Boolean get() = source.startsWith("Item:")

    /** 패시브 트리 노드가 부여한 스킬인지 */
    val grantedByTree: Boolean get() = source.startsWith("Tree:")

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
    /** Spec nodes="1,2,3" 의 노드 번호 (찍은 패시브) */
    val treeNodeIds: MutableList<Int> = mutableListOf(),
    /** masteryEffects="{노드,효과}" — 노드 번호 → 고른 마스터리 효과 번호 */
    val treeMastery: MutableMap<Int, Int> = linkedMapOf(),
    var treeClassId: Int = -1,
    var treeAscendClassId: Int = 0,
    var treeUrl: String = "",
    var rawXml: String = "",
    var fullDps: Double = 0.0,
    /** PoB가 inf·nan으로 내보낸 스탯 이름 (값이 없어 계산에서 뺀다) */
    val nonFiniteStats: MutableSet<String> = linkedSetOf(),
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
        "eNqtWdty2zgSfU6-AqWqqd2t0DZxI0CXnanY2VxqxzPeyDPzjEiQxDVFKrw44_2j_Y79sT0AaVESKUvJ7Dy4oO6DRvfpbqCZufjx" +
        "j2VKHmxRJnl2OaKn4YjYbJJPk2x-Oaqr2Yke_fj65cWtqRa_zK7qJHWabgOjpzQ8DfmILPNpndrfeorXL19c-H0ktQ82vRzFbEQm" +
        "qSnLn83SXo4-mWxuixEx5cRm0-tO8daaqX20sGySbJxP7m31vsjrFbwckYfEfr3Jp4Bdv_npejwin002TarL0c95hi0rk1ULm2c3" +
        "5l958T6f9uRJtimHjy8ublPzaItxZSpS4s_l6C6vTPr2FsYfTFrjKKEZl-GpGp0Nb7jOl5-TzE4390hKWShO2b49b6rKTO7LWwjt" +
        "JM-m6438VOzddF0k1fXCZBPbwelp9Bz8pk6rZJUmjur1lv1eIcFmbt-aJf6uN1BBQxHu2_NTMuugAlHvA96YzKyBsd6L-3tmi_nj" +
        "eJHYtKNFR3vxb4plXnfhUZesvbYfjKvTDqzjcC_4XVLYT7ZMymqNV_vJztPpLlju52y-qDJ01M6OSO81vzB5uYM-aRN5ceYbza3u" +
        "CmuJmVTJgx2v7MT1jLfnfpAqqdKuwUj65AQxRZF_HZEMjVVe53WG8yinbbt-RLewrT51EtrCUek65DqQWlMRMMp5FMRKyIDLOKQB" +
        "k4rSgPNYRAEXoRJBREVEA8o101ijFgPBoZRR6DaJiGETZToMIiYiWBRKQ8slwBEMSk1VQJUDszBSKhCRlsKdphW2hBIQEWseUKqZ" +
        "hDaOw0DKUOtAUKkCriSNA6ZUKGEGLgU85lEcSKqE2xrGWMMBaLkAMuIaW6WEp1gDrUXsjMNdZxB-wgHNAipjBf-Zw-oQvxm88K4I" +
        "_GVACK0UByFYMi1cEFIAoTQilpEGh4zi0gAHnCEgF49EO6mARSGHEfQg4DELdcDDCKFIsEphVoAWzlTEIYfPAQ2Zos5RSHB1UTAF" +
        "RRTACwStGM7nTMbgPYQvXEuu3KHwAqwiYqpxHMTeOHVRxIggiELl8xGDCErDCGy74CBGcpzpEEoGr0EzqIASXjCQi5hDifwhajgn" +
        "WORid0lD-mUELpnLC6JwGXFJayhWLmcM6QatOnYSrkCM8DHKOJKIFzTAAAs5R3KkisGABj-CCQp55JKMCkBoImYshE2F1EgOMVKJ" +
        "4ByKSuZqk0bY52sjZtpThtoQceTCFcKFwrSLH_64WqQxNlEV-ToJle-yFxe_FmmzOGtXF2eu83yPutZ0i_F9kqYlKfOiem-X5dUj" +
        "no3LUVXU7Xvk9f7Fw4tsPqd22mpJaj67h5SS9fVB3vjObc6ENZL5J3RXT0pn07XtWtMq2rcZlzb5Ups0qR6bHzsn-_23pnAXQ3ND" +
        "bR73wc6qjTPG9WqF4Brp0AE9-z2LNzgqMSm5s8tV3jfdqlvt9x0xnpiqwtSyyAd831J-n_0uBx-zWd08OrvHrDEd5NBhM5OW7Wmo" +
        "rXWpHFc5jHzA-46RLJ-Ru0WdTTEV9JO5i-jcbnS_zNaa76PGPZXPsOLUxxPSHfHtfHDye5JNyVs3UfWZ2NR1Tjrpk7D1jcbf6lv7" +
        "s3TLjxVuAY9xK5I0j2trWosRKUzhLX8yBVxvfGucdZJzMq7yYlkuYHrqhG1yyO95OiPj__7H-Uqu8q9Od9L-59at6J-N4-fkFQvJ" +
        "X009X9qsstO_Od3t4rFMJujDZiA8J4yKEx73cF2tPwG1PsEEvItzA6m3N66K5N6SZpY9J_Gp_MHp26GYrOB-MxafE4p5eNfOZhif" +
        "7Jcac5rTlufeGcccPBC7SE_vXu0rpn8gVd5GQHJMwuQuWVrSTdC7brxSsduxNH8kyxqmMQj3IFx4qx1DzQznk7KDdQ4k2aSwprTT" +
        "Zgf5ZKZJXe4gL85cKNslw7qSYd9eMhavQUH-kVSWNHP3LjuddLNefE59Dl2MVyk-1lAjrMmln8rPCSa83UDbIRy51fFzqd1KGOsl" +
        "LKIH2afMs-8G-WeIf0Wlh_l76TkYPzaXQxniXYbo4QzhmsED9BkZcbJ3Ns0zcpNjfn8kH_K8l58PNl3aaotcMUC8_7JqU-ya9Gj2" +
        "aY_9ODrMvmpodZ8uz_BKxWbdu69EgOcWvpoKkaAHPAlDnIqOU36Y07tkbou_lOR9YSaeVizKlUvkjUnS_v04fSRNFW_xivHwAK8Y" +
        "C48mlveIxZfDQWYZO6Zgt-6T5m4lGEfx43C1yo7Z8DCz16YCr7cYKT2Nt7aYJWDvfYprtNyNsJOuOWWROJqxsMeYYodLkR51EcRH" +
        "0DrEVvRNbLkRoqzsyl-sqbX3eJ7zquwXYCtc0zRwlx5PEw-32gw5cGaagvDB4zo4SGN0BI1DBKk1QSruCLox82SyzZAXnZt_14Xv" +
        "0OscEyd5s6zT5nbbDGhYukmCinu1InpB7vFYdx7rIx7UZJ7B0d8MhgX_on7NT_DIZqAJ90tvZhmQbfmt-7eCGmK-UemhqvUqGR0Z" +
        "bfynor01tfl_BRptNbN7DZobj-59e_eERMMuJnU4pg81muEKndkONUlGrmzaK68h2VZEqv9SHjGniD8zWdCNeeLXLPlSrwPc-IRx" +
        "z3CxbCLeneE23XWuLHNUWTsIzzAYWjNZEDxySwwfCYZSO4E_6WPfnbGtmq-ocZpXTz5Ys8IrDh8xzCzbfzts_oly-wOxv4d1e9gA" +
        "qhl6OgwfwGy84h1QDACbV6nDyEFjuJE7SDQAaS6lDqMGMK4rNvnQ-zAb8cdD7qAWN1gNByDvUlPeb3FPnz5Iu3w1a_8xep1ns2Tu" +
        "ZU_Li7Pt_wn0-uX_AO4WUwg"

    fun decode(input: String): PobBuild {
        val text = input.trim().trim('\n', '\r', ' ', '"', '\uFEFF')
        val xml = if (text.startsWith("<")) {
            text
        } else {
            // 붙여넣은 코드는 줄바꿈·공백이 섮여 있을 수 있으므로 먼저 걷어 낸다.
            val code = text.filterNot { it.isWhitespace() }
                .replace("-", "+").replace("_", "/")
                .let { s ->
                    val pad = s.length % 4
                    if (pad == 0) s else s + "=".repeat(4 - pad)
                }
            inflate(code)
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
                        val value = parseStatNumber(parser.getAttributeValue(null, "value"))
                        if (stat.isNotEmpty() && value != null) {
                            // 무한·NaN은 계산에 쓸 수 없으므로 이름만 기억해 두고 뺀다.
                            if (value.isFinite()) build.stats[stat] = value
                            else build.nonFiniteStats.add(stat)
                        }
                        if (stat == "FullDPS" && value != null && value.isFinite()) build.fullDps = value
                    }
                    "FullDPSSkill" -> {
                        val name = parser.getAttributeValue(null, "stat").orEmpty()
                        val dps = parseStatNumber(parser.getAttributeValue(null, "value"))
                        val source = parser.getAttributeValue(null, "source").orEmpty()
                        if (name.isNotBlank() && dps != null && dps.isFinite()) {
                            build.fullDpsSkills.add(PobFullDpsSkill(name, dps, source))
                        }
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
                                mainActiveSkill = main?.toIntOrNull() ?: 0,
                                source = parser.getAttributeValue(null, "source").orEmpty(),
                                slot = parser.getAttributeValue(null, "slot").orEmpty(),
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
                        build.treeClassId = parser.getAttributeValue(null, "classId")?.toIntOrNull() ?: -1
                        build.treeAscendClassId =
                            parser.getAttributeValue(null, "ascendClassId")?.toIntOrNull() ?: 0
                        build.treeUrl = parser.getAttributeValue(null, "url").orEmpty()
                        parser.getAttributeValue(null, "nodes")?.let { raw ->
                            raw.split(',').forEach { token ->
                                token.trim().toIntOrNull()?.let { build.treeNodeIds.add(it) }
                            }
                        }
                        parser.getAttributeValue(null, "masteryEffects")?.let { raw ->
                            Regex("\\{(\\d+),(\\d+)\\}").findAll(raw).forEach { match ->
                                val node = match.groupValues[1].toIntOrNull() ?: return@forEach
                                val effect = match.groupValues[2].toIntOrNull() ?: return@forEach
                                build.treeMastery[node] = effect
                            }
                        }
                        if (build.treeNodes == 0 && build.treeNodeIds.isNotEmpty()) {
                            build.treeNodes = build.treeNodeIds.size
                        }
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

    /**
     * PoB가 적어 보낸 스탯 값. 최신 PoB는 계산이 풀리지 않은 스탯을 inf·nan으로 내보내므로
     * 이런 값도 숫자로 읽어 낸다(계산에서는 따로 걸러 낸다).
     */
    internal fun parseStatNumber(raw: String?): Double? {
        val text = raw?.trim()?.lowercase() ?: return null
        return when (text) {
            "inf", "+inf", "infinity", "+infinity" -> Double.POSITIVE_INFINITY
            "-inf", "-infinity" -> Double.NEGATIVE_INFINITY
            "nan", "-nan", "+nan" -> Double.NaN
            else -> text.toDoubleOrNull()
        }
    }

    /** 계산·전투에 쓰는 메인 스킬 그룹을 고른다. preferred는 1부터 시작하는 그룹 번호(0이면 PoB 설정을 따른다). */
    fun mainGroup(build: PobBuild, preferred: Int = 0): PobSkillGroup? {
        val usable = build.skills.filter { it.gems.any { gem -> gem.enabled } }
        if (usable.isEmpty()) return null
        // 화면에서 고른 그룹이 있으면 그 그룹, 없으면 PoB가 계산한 그룹(mainSocketGroup)을 쓴다.
        val wanted = if (preferred in 1..build.skills.size) preferred else build.mainSocketGroup
        if (wanted in 1..build.skills.size) {
            val picked = build.skills[wanted - 1]
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
        if (build.treeNodeIds.isNotEmpty()) {
            serializer.attribute(null, "nodes", build.treeNodeIds.joinToString(","))
        }
        if (build.treeMastery.isNotEmpty()) {
            serializer.attribute(
                null,
                "masteryEffects",
                build.treeMastery.entries.joinToString(",") { "{${it.key},${it.value}}" },
            )
        }
        if (build.treeClassId >= 0) serializer.attribute(null, "classId", build.treeClassId.toString())
        if (build.treeAscendClassId > 0) {
            serializer.attribute(null, "ascendClassId", build.treeAscendClassId.toString())
        }
        serializer.endTag(null, "Spec")
        serializer.endTag(null, "Tree")

        serializer.startTag(null, "Skills")
        build.skills.forEachIndexed { index, group ->
            serializer.startTag(null, "SkillGroup")
            serializer.attribute(null, "label", group.label)
            serializer.attribute(null, "enabled", group.enabled.toString())
            if (group.mainActiveSkill > 0) {
                serializer.attribute(null, "mainActiveSkill", group.mainActiveSkill.toString())
            }
            if (group.source.isNotBlank()) serializer.attribute(null, "source", group.source)
            if (group.slot.isNotBlank()) serializer.attribute(null, "slot", group.slot)
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
