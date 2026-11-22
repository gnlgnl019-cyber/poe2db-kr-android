package com.sunuhwi.poe2dbkr

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.net.HttpURLConnection
import java.net.URL

/**
 * 시세 화면 아이콘 주소가 실제로 이미지에 닿는지 확인한다.
 *
 * poe.ninja 교환 API는 품목 이름·아이콘을 응답 최상단 `items` 배열에 담아 주는데,
 * 예전 코드는 `core.items`(기준 화폐 3개)만 뒤져서 나머지 줄의 아이콘이 빈 주소가 되고
 * 화폐·룬·에센스 탭이 통째로 빈 네모로 보였다.
 */
class MarketIconTest {

    private val fixture = """
        {
          "core": {
            "items": [
              {"id": "divine", "name": "Divine Orb", "image": "/gen/image/DIVINE/2986e220b3/CurrencyModValues.png", "category": "Currency", "detailsId": "divine-orb"},
              {"id": "exalted", "name": "Exalted Orb", "image": "https://web.poecdn.com/gen/image/EXALTED/ad7c366789/CurrencyAddModToRare.png", "category": "Currency", "detailsId": "exalted-orb"},
              {"id": "chaos", "name": "Chaos Orb", "image": "/gen/image/CHAOS/aaa/CurrencyModValues.png", "category": "Currency", "detailsId": "chaos-orb"}
            ],
            "rates": {"exalted": 566.2, "chaos": 7.59},
            "primary": "divine",
            "secondary": "exalted"
          },
          "items": [
            {"id": "alch", "name": "Orb of Alchemy", "image": "/gen/image/ALCH/9b80b44821/CurrencyUpgradeToRare.png", "category": "Currency", "detailsId": "orb-of-alchemy"},
            {"id": "annul", "name": "Orb of Annulment", "image": "/gen/image/ANNUL/cc/CurrencyRemoveMod.png", "category": "Currency", "detailsId": "orb-of-annulment"}
          ],
          "lines": [
            {"id": "alch", "primaryValue": 0.004321, "volumePrimaryValue": 2.33, "maxVolumeCurrency": "divine", "sparkline": {"totalChange": -26.44, "data": [-5.82, null, -9.92]}},
            {"id": "annul", "primaryValue": 0.31, "volumePrimaryValue": 1.1, "maxVolumeCurrency": "divine", "sparkline": {"totalChange": 4.2}},
            {"id": "divine", "primaryValue": 1.0, "volumePrimaryValue": 120.5, "maxVolumeCurrency": "divine"},
            {"id": "unknown-thing", "primaryValue": 0.02, "volumePrimaryValue": 0.4}
          ]
        }
    """.trimIndent()

    @Test
    fun resolveIconMakesUsableUrls() {
        assertEquals("", Market.resolveIcon(""))
        assertEquals("", Market.resolveIcon(null))
        assertEquals("", Market.resolveIcon("   "))
        assertEquals(
            "https://web.poecdn.com/gen/image/WzI1LDE0XS/33c430b8b4/Redbeak.png",
            Market.resolveIcon("/gen/image/WzI1LDE0XS/33c430b8b4/Redbeak.png"),
        )
        assertEquals(
            "https://web.poecdn.com/gen/image/x.png",
            Market.resolveIcon("gen/image/x.png"),
        )
        assertEquals("https://web.poecdn.com/gen/image/x.png", Market.resolveIcon("http://web.poecdn.com/gen/image/x.png"))
        assertEquals("https://web.poecdn.com/gen/image/x.png", Market.resolveIcon("//web.poecdn.com/gen/image/x.png"))
        assertEquals(
            "https://cdn.poe2db.tw/image/Art/2DArt/SkillIcons/WitchContagion.webp",
            Market.resolveIcon("https://cdn.poe2db.tw/image/Art/2DArt/SkillIcons/WitchContagion.webp"),
        )
    }

    @Test
    fun exchangeLinesTakeNameAndIconFromTopLevelItems() {
        val rates = Market.parseExchange(JSONObject(fixture), emptyMap(), 566.2, 7.59)
        val byId = rates.associateBy { it.id }

        val alch = byId.getValue("alch")
        assertEquals("Orb of Alchemy", alch.name)
        assertEquals("https://web.poecdn.com/gen/image/ALCH/9b80b44821/CurrencyUpgradeToRare.png", alch.iconUrl)
        assertEquals(0.004321 * 566.2, alch.exalted, 0.0001)

        val annul = byId.getValue("annul")
        assertEquals("Orb of Annulment", annul.name)
        assertEquals("https://web.poecdn.com/gen/image/ANNUL/cc/CurrencyRemoveMod.png", annul.iconUrl)

        // core.items에 남아 있는 기준 화폐는 그대로 찾아야 한다.
        val divine = byId.getValue("divine")
        assertEquals("Divine Orb", divine.name)
        assertEquals("https://web.poecdn.com/gen/image/DIVINE/2986e220b3/CurrencyModValues.png", divine.iconUrl)

        // 메타데이터가 아예 없는 줄은 이름 없이 id로 두되, 아이콘 주소를 엉뚱하게 만들지 않는다.
        val unknown = byId.getValue("unknown-thing")
        assertEquals("unknown-thing", unknown.name)
        assertEquals("", unknown.iconUrl)

        // 어떤 줄도 "https://web.poecdn.com" 같은 빈 주소를 갖지 않아야 한다.
        rates.forEach { rate ->
            assertNotEquals("빈 아이콘 주소", "https://web.poecdn.com", rate.iconUrl)
            assertNotEquals("빈 아이콘 주소", "https://web.poecdn.com/", rate.iconUrl)
        }
        assertTrue(rates.first().divine >= rates.last().divine)
    }

    @Test
    fun uniqueItemIconsAreAbsoluteUrls() {
        val json = """
            {
              "core": {"rates": {"exalted": 400.0, "chaos": 8.0}},
              "lines": [
                {"id": 727, "name": "Redbeak", "baseType": "Shortsword",
                 "icon": "https://web.poecdn.com/gen/image/WzI1LDE0XS/33c430b8b4/Redbeak.png",
                 "category": "[Sword|One Hand Sword]", "primaryValue": 5477, "listingCount": 12},
                {"id": 728, "name": "Widowhail", "baseType": "Recurve Bow",
                 "icon": "/gen/image/WzI1XS/aa/Widowhail.png",
                 "category": "[Bow]", "primaryValue": 120, "listingCount": 3}
              ]
            }
        """.trimIndent()
        val rates = Market.parseItems(JSONObject(json), emptyMap(), 400.0, 8.0)
        assertEquals("https://web.poecdn.com/gen/image/WzI1LDE0XS/33c430b8b4/Redbeak.png", rates.first { it.name == "Redbeak" }.iconUrl)
        assertEquals("https://web.poecdn.com/gen/image/WzI1XS/aa/Widowhail.png", rates.first { it.name == "Widowhail" }.iconUrl)
    }

    /** 실제 poe.ninja 응답으로 뽑은 아이콘 주소가 HTTP 200 이미지로 열리는지 (네트워크 없으면 건너뜀) */
    @Test
    fun liveIconUrlsRespond() {
        val body = runCatching { httpGet("https://poe.ninja/poe2/api/economy/exchange/current/overview?league=Runes%20of%20Aldur&type=Currency") }
            .getOrElse { error ->
                println("[TEST] 네트워크 없음, 라이브 확인 생략: ${error.message}")
                ""
            }
        assumeTrue("네트워크 없음", body.isNotBlank())
        val rates = Market.parseExchange(JSONObject(body), emptyMap(), 1.0, 1.0)
        println("[TEST] live currency lines=${rates.size}")
        val withIcon = rates.filter { it.iconUrl.isNotBlank() }
        println("[TEST] live icons=${withIcon.size}/${rates.size}")
        assertTrue("아이콘 주소가 하나도 없다", withIcon.size > 3)
        withIcon.take(4).forEach { rate ->
            val (code, type) = httpHead(rate.iconUrl)
            println("[TEST] ${rate.name} -> ${rate.iconUrl} [$code $type]")
            assertEquals("${rate.name} 아이콘 응답", 200, code)
            assertTrue("${rate.name} 이미지 타입", type.startsWith("image/"))
        }
    }

    private fun httpGet(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 10000
        conn.readTimeout = 20000
        conn.setRequestProperty("User-Agent", "PoE2DB-KR-Android/1.0")
        try {
            if (conn.responseCode != 200) error("HTTP ${conn.responseCode}")
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    private fun httpHead(url: String): Pair<Int, String> {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 10000
        conn.readTimeout = 20000
        conn.setRequestProperty("User-Agent", "okhttp/4.12.0")
        try {
            val code = conn.responseCode
            val type = conn.contentType.orEmpty()
            if (conn.inputStream != null) conn.inputStream.close()
            return code to type
        } finally {
            conn.disconnect()
        }
    }
}
