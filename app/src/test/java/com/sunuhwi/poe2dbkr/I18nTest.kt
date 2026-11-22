package com.sunuhwi.poe2dbkr

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * 시세·장비 한글화 검증.
 *
 * 앱이 쓰는 사전(assets/poe2_kr_i18n.json)을 그대로 읽어, poe.ninja 실제 응답의
 * 아이템 이름·베이스·카테고리·옵션이 한글로 바뀌는지 확인한다.
 */
class I18nTest {

    private val dict: KrDict by lazy {
        val file = File("src/main/assets/poe2_kr_i18n.json")
        assertTrue("한글 사전 파일이 없다: ${file.absolutePath}", file.isFile)
        KrDict.parse(file.readText())
    }

    private val fixture: JSONObject by lazy {
        JSONObject(File("src/test/resources/ninja_fixture.json").readText())
    }

    @Test
    fun dictionaryHasNamesBasesIconsAndMods() {
        println("[TEST] ${dict.stats()} · itemMods ${dict.itemMods.size}")
        println("[TEST] classes=${dict.classes.size} categories=${dict.categories.size} sample=" +
            dict.classes.entries.take(3) + " / " + dict.categories.entries.take(3))
        assertTrue("이름 사전이 비었다", dict.names.size > 3_000)
        assertTrue("베이스 사전이 비었다", dict.bases.size > 1_200)
        assertTrue("아이콘 사전이 비었다", dict.icons.size > 2_500)
        assertTrue("옵션 문장 틀이 부족하다", dict.mods.size > 150)
    }

    @Test
    fun knownNamesAreKorean() {
        assertEquals("신성한 오브", dict.name("Divine Orb"))
        assertEquals("연금술의 오브", dict.name("Orb of Alchemy"))
        assertEquals("붉은 부리", dict.name("Redbeak"))
        assertEquals("마술의 에센스", dict.name("Essence of Sorcery"))
        assertEquals("타카티의 영혼 핵", dict.name("Soul Core of Tacati"))
        assertEquals("빛의 징조", dict.name("Omen of Light"))
        assertEquals("미가공 스킬 젬 (17레벨)", dict.name("Uncut Skill Gem (Level 17)"))
        assertEquals("절단도", dict.base("Shortsword"))
        assertEquals("한손 검", dict.classKo("One Hand Sword"))
        assertEquals("한손 검", dict.category("[Sword|One Hand Sword]"))
    }

    @Test
    fun everyFixtureNameIsKorean() {
        val missing = ArrayList<String>()
        var total = 0
        val exchange = fixture.optJSONObject("exchange")!!
        val exchangeKeys = listOf(
            "Currency", "Runes", "Essences", "SoulCores", "Fragments",
            "UncutGems", "Ritual", "Delirium", "Breach",
        )
        for (type in exchangeKeys) {
            val data = exchange.optJSONObject(type) ?: continue
            val index = HashMap<String, JSONObject>()
            val items = data.optJSONArray("items")
            for (i in 0 until (items?.length() ?: 0)) {
                val obj = items!!.optJSONObject(i) ?: continue
                index[obj.optString("id")] = obj
            }
            val lines = data.optJSONArray("lines") ?: continue
            for (i in 0 until lines.length()) {
                val line = lines.optJSONObject(i) ?: continue
                val name = index[line.optString("id")]?.optString("name").orEmpty()
                if (name.isBlank()) continue
                total++
                if (dict.name(name).isBlank()) missing.add("$type:$name")
            }
        }

        var uniqueTotal = 0
        var baseTotal = 0
        var baseKo = 0
        var optionTotal = 0
        var optionKo = 0
        val uniques = fixture.optJSONObject("unique")!!
        for (type in listOf("UniqueWeapons", "UniqueArmours", "UniqueAccessories", "UniqueJewels")) {
            val lines = uniques.optJSONObject(type)?.optJSONArray("lines") ?: continue
            for (i in 0 until lines.length()) {
                val line = lines.optJSONObject(i) ?: continue
                val name = line.optString("name")
                if (name.isBlank()) continue
                uniqueTotal++
                if (dict.name(name).isBlank()) missing.add("$type:$name")
                val base = line.optString("baseType")
                if (base.isNotBlank()) {
                    baseTotal++
                    if (dict.base(base).isNotBlank()) baseKo++ else missing.add("base:$base")
                }
                val exact = dict.itemModsFor(name)
                val raws = line.optJSONArray("explicitModifiers")
                val english = (0 until (raws?.length() ?: 0)).mapNotNull { k ->
                    Market.cleanTags(raws!!.optJSONObject(k)?.optString("text").orEmpty()).takeIf { it.isNotBlank() }
                }
                if (english.isEmpty()) continue
                // 화면과 같은 규칙: 아이템 단위 한글 옵션이 있으면 그것을, 없으면 문장 틀을 쓴다.
                val shown = exact?.explicits?.takeIf { it.isNotEmpty() } ?: dict.modLines(english)
                optionTotal++
                if (shown.all { line -> line.any { it.code > 0x7F } }) optionKo++
            }
        }
        println("[TEST] 이름 ${total + uniqueTotal} · 베이스 $baseKo/$baseTotal · 옵션 $optionKo/$optionTotal")
        println("[TEST] 사전에 없는 항목: ${missing.take(8)} (${missing.size})")
        assertTrue("품목이 비었다", total + uniqueTotal > 50)
        assertTrue("한글로 안 바뀐 이름/베이스가 있다: ${missing.take(8)}", missing.isEmpty())
        assertEquals("베이스 타입이 한글로 안 바뀐 항목이 있다", baseTotal, baseKo)
        println("[TEST] 옵션이 모두 한글인 고유 아이템 $optionKo/$optionTotal")
        assertTrue(
            "옵션 한글화율이 낮다: $optionKo/$optionTotal",
            optionKo.toDouble() / optionTotal >= 0.9,
        )
    }

    @Test
    fun modTemplatesFillNumbersInOrder() {
        assertEquals("공격 속도 15% 증가", dict.modLine("15% increased Attack Speed"))
        assertEquals("생명력 최대치 +80", dict.modLine("+80 to maximum Life"))
        assertEquals("번개 저항 +30%", dict.modLine("+30% to Lightning Resistance"))
        assertEquals("물리 피해 12~24 추가", dict.modLine("Adds 12 to 24 Physical Damage"))
        // 사전에 없는 문장은 영문을 그대로 둔다.
        assertEquals("Weird Unknown Mod", dict.modLines(listOf("Weird Unknown Mod")).first())
    }

    @Test
    fun baseTypeIconMatchesAnActualImage() {
        val base = "Shortsword"
        val icon = dict.icon(base)
        assertTrue("베이스 아이콘이 없다: $base", icon.startsWith("https://cdn.poe2db.tw/"))
        val item = PobItem(name = "Stormshroud", rarity = "Rare", baseType = "Shortsword", itemClass = "Sword")
        assertTrue("희귀 장비 아이콘이 비었다", PobLocale.icon(dict, item).isNotBlank())
        val unique = PobItem(name = "Redbeak", rarity = "Unique", baseType = "Shortsword", itemClass = "Sword")
        assertTrue("고유 장비 아이콘이 비었다", PobLocale.icon(dict, unique).isNotBlank())
        // 클래스 대표 아이콘도 전부 채워져 있어야 한다.
        listOf("Bow", "Crossbow", "Staff", "Wand", "Sceptre", "Helmet", "Body Armour", "Gloves", "Boots", "Ring", "Amulet", "Belt")
            .forEach { cls -> assertTrue("클래스 아이콘 없음: $cls", dict.classIcon(cls).isNotBlank()) }
    }

    /** 사전 커버리지가 실제 라이브 응답에서도 유지되는지 (네트워크 없으면 건너뜀) */
    @Test
    fun liveMarketNamesStayKorean() {
        val body = runCatching {
            val url = "https://poe.ninja/poe2/api/economy/exchange/current/overview" +
                "?league=${URLEncoder.encode("Runes of Aldur", "UTF-8")}&type=Currency"
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 8000
            conn.readTimeout = 15000
            conn.setRequestProperty("User-Agent", "PoE2DB-KR-Android/1.0")
            try {
                conn.inputStream.bufferedReader().use { it.readText() }
            } finally {
                conn.disconnect()
            }
        }.getOrElse { error ->
            println("[TEST] 네트워크 없음: ${error.message}")
            ""
        }
        assumeTrue("네트워크 없음", body.isNotBlank())
        val rates = Market.parseExchange(JSONObject(body), dict, 1.0, 1.0)
        val named = rates.count { it.koName.isNotBlank() }
        println("[TEST] live currency ${rates.size} · 한글 $named")
        assertTrue("라이브 응답 품목이 비었다", rates.size > 5)
        assertTrue("라이브 이름 한글화율 ${named}/${rates.size}", named.toDouble() / rates.size >= 0.95)
        assertNotNull(rates.firstOrNull())
    }

    @Test
    fun marketDetailShowsKoreanOptions() {
        val root = fixture.optJSONObject("unique")!!.optJSONObject("UniqueWeapons")!!
        val rates = Market.parseItems(root, dict, 400.0, 8.0)
        println("[TEST] unique lines ${rates.size}")
        assertTrue(rates.isNotEmpty())
        rates.forEach { rate ->
            assertTrue("이름이 영문으로 남았다: ${rate.name}", rate.koName.isNotBlank())
            if (rate.baseType.isNotBlank()) {
                assertTrue("베이스가 영문으로 남았다: ${rate.baseType}", rate.koBase.isNotBlank())
            }
            val lines = rate.explicits + rate.properties + rate.implicits
            val korean = lines.count { line -> line.any { it.code > 0x7F } }
            assertTrue("옵션이 거의 영문이다: ${rate.name} $lines", lines.isEmpty() || korean >= lines.size / 2)
        }
    }
}
