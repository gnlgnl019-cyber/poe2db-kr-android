package com.sunuhwi.poe2dbkr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 빌드 장비 가치 산출을 확인한다.
 *
 * 주얼·젬·플라스크·호신부는 값에서 빠지고, 고유는 poe.ninja 실거래가, 희귀는
 * 공식 거래소 유사 매물(없으면 옵션 추정가)을 쓰는지 본다.
 */
class GearValueTest {

    private fun item(
        id: Int,
        name: String,
        rarity: String,
        base: String,
        vararg lines: String,
    ): PobItem = PobItem(id = id, name = name, rarity = rarity, baseType = base).apply {
        this.lines.addAll(lines)
    }

    private fun build(vararg worn: Pair<String, PobItem>): PobBuild {
        val build = PobBuild(className = "Monk", ascendClassName = "Invoker", level = 90)
        worn.forEachIndexed { index, (slot, item) ->
            build.items[index + 1] = item.copy(id = index + 1)
            build.slots[slot] = index + 1
        }
        return build
    }

    @Test
    fun onlyWornGearIsPriced() {
        val build = build(
            "Helmet" to item(1, "투구", "Rare", "Iron Helm", "+80 to maximum Life", "+40% to Fire Resistance"),
            "Body Armour" to item(2, "갑옷", "Rare", "Expert Plate", "+120 to maximum Life"),
            "Jewel 1" to item(3, "주얼", "Rare", "Sapphire", "+15% to Cold Resistance"),
            "Flask 1" to item(4, "플라스크", "Magic", "Life Flask", "Recover 300 Life"),
            "Charm 1" to item(5, "호신부", "Magic", "Charm", "+20 to Spirit"),
        )
        val value = GearPricing.value(build)
        println("[TEST] 부위=${value.slots.map { it.slotKey to it.divine }}")
        assertEquals(2, value.slots.size)
        assertEquals(listOf("Helmet", "Body Armour"), value.slots.map { it.slotKey })
        assertTrue("주얼이 값에 들어갔다", value.slots.none { it.itemName.contains("주얼") })
        // 슬롯 판정 자체도 확인
        assertTrue(GearPricing.isPricedSlot("Ring 2"))
        assertFalse(GearPricing.isPricedSlot("Jewel 1"))
        assertFalse(GearPricing.isPricedSlot("Flask 2"))
        assertFalse(GearPricing.isPricedSlot("Charm 3"))
        assertFalse(GearPricing.isPricedSlot("Weapon 1 Swap"))
    }

    @Test
    fun uniqueUsesMarketPrice() {
        val ring = item(1, "Kalandra's Touch", "Unique", "Ring", "+10 to all Attributes")
        val build = build("Ring 1" to ring)
        val quotes = mapOf(
            "kalandra's touch" to UniqueQuote("Kalandra's Touch", "Ring", 12.5, 240),
        )
        val value = GearPricing.value(build, CurrencyRates(320.0, 96.0), quotes)
        val slot = value.slots.single()
        println("[TEST] 고유=${slot.line}")
        assertEquals(12.5, slot.divine, 0.001)
        assertEquals("poe.ninja 실거래가", slot.basis)
        assertTrue("매물 수가 안 적혔다", slot.note.contains("240"))
        assertEquals("고유", slot.rarity)
    }

    @Test
    fun uniqueWithoutQuoteFallsBackToEstimate() {
        val ring = item(1, "Unknown Ring", "Unique", "Ring", "+60 to maximum Life", "+40% to Lightning Resistance")
        val build = build("Ring 1" to ring)
        val value = GearPricing.value(build, CurrencyRates(320.0, 96.0), emptyMap())
        val slot = value.slots.single()
        assertTrue("근거가 실거래가로 적혔다: ${slot.basis}", slot.basis == "옵션 추정가")
        assertTrue("추정가가 0이다", slot.divine > 0.0)
        assertTrue(value.notes.any { it.contains("고유") })
    }

    @Test
    fun rarePriceGrowsWithOptions() {
        val junk = PobItem(id = 1, name = "밀짚모자", rarity = "Rare", baseType = "Rusted Helm").apply {
            lines.add("+6 to maximum Life")
        }
        val good = PobItem(id = 2, name = "왕관", rarity = "Rare", baseType = "Iron Helm").apply {
            lines.addAll(
                listOf(
                    "+92 to maximum Life",
                    "+45% to Fire Resistance",
                    "+43% to Cold Resistance",
                    "+38 to Spirit",
                    "+18% increased Armour",
                )
            )
        }
        val junkPrice = GearPricing.rarePrice(junk, "투구")
        val goodPrice = GearPricing.rarePrice(good, "투구")
        println("[TEST] 잡옵=$junkPrice 좋은옵=$goodPrice")
        assertTrue("옵션이 좋은 쪽이 더 싸다", goodPrice > junkPrice * 4)
        assertTrue(goodPrice > 1.0)
    }

    @Test
    fun weaponUsesPdps() {
        val lines = arrayOf(
            "Physical Damage: 120-240",
            "Attacks per Second: 1.60",
            "+35% increased Physical Damage",
            "+90 to maximum Life",
        )
        val strong = PobItem(id = 1, name = "강타자", rarity = "Rare", baseType = "Expert Quarterstaff").apply {
            this.lines.addAll(lines)
        }
        val plain = PobItem(id = 2, name = "막대기", rarity = "Rare", baseType = "Expert Quarterstaff").apply {
            this.lines.addAll(listOf("+35% increased Physical Damage", "+90 to maximum Life"))
        }
        val dps = GearPricing.weaponDps(strong.lines)
        println("[TEST] 무기 dps=$dps 추정=${GearPricing.rarePrice(strong, "무기 1")} / ${GearPricing.rarePrice(plain, "무기 1")}")
        assertEquals(288.0, dps, 0.001)
        assertTrue(GearPricing.rarePrice(strong, "무기 1") > GearPricing.rarePrice(plain, "무기 1") + 0.5)
    }

    @Test
    fun totalConvertsToDivineAndChaos() {
        val build = build(
            "Ring 1" to item(1, "A", "Unique", "Ring", "+10 to all Attributes"),
            "Ring 2" to item(2, "B", "Unique", "Ring", "+10 to all Attributes"),
        )
        val quotes = mapOf(
            "a" to UniqueQuote("A", "Ring", 3.5, 10),
            "b" to UniqueQuote("B", "Ring", 1.5, 5),
        )
        val value = GearPricing.value(build, CurrencyRates(320.0, 96.0), quotes)
        println("[TEST] 합계=${value.divineText} 디바인 / ${value.chaosText} 카오스")
        assertEquals(5.0, value.divine, 0.001)
        assertEquals(480.0, value.chaos, 0.5)
        assertEquals("5.00", value.divineText)
        assertEquals("480", value.chaosText)
        assertTrue(value.detail.contains("환율 1 디바인 = 320 엑잘 / 96 카오스"))
        assertTrue(value.detail.contains("poe.ninja 실거래가"))
        assertTrue(value.detail.contains("옵션"))
    }

    @Test
    fun withoutRatesOnlyDivineIsShown() {
        val build = build("Belt" to item(1, "허리띠", "Rare", "Plate Belt", "+90 to maximum Life"))
        val value = GearPricing.value(build, CurrencyRates.NONE)
        assertTrue(value.hasItems)
        assertEquals("", value.chaosText)
        assertTrue(value.notes.any { it.contains("환율") })
    }

    @Test
    fun tradePriceBeatsEstimate() {
        val boots = item(
            1, "질풍 장화", "Rare", "Expert Boots",
            "+90 to maximum Life", "+45% to Fire Resistance", "30% increased Movement Speed",
        )
        val build = build("Boots" to boots)
        val estimate = GearPricing.value(build, CurrencyRates(320.0, 96.0))
        val traded = GearPricing.value(
            build, CurrencyRates(320.0, 96.0),
            trades = mapOf("Boots" to TradePrice(7.5, 3, 5)),
        )
        val slot = traded.slots.single()
        println("[TEST] 추정=${estimate.slots.single().divine} 거래소=${slot.line}")
        assertEquals(7.5, slot.divine, 0.001)
        assertEquals("공식 거래소 유사 매물", slot.basis)
        assertTrue(slot.note.contains("5"))
        assertTrue(traded.divine > estimate.divine || estimate.divine != 7.5)
    }

    @Test
    fun tradeCandidatesSkipUniquesAndCheapItems() {
        val build = build(
            "Ring 1" to item(1, "Unique Ring", "Unique", "Ring", "+10 to all Attributes"),
            "Helmet" to item(2, "좋은 투구", "Rare", "Iron Helm", "+95 to maximum Life", "+45% to Fire Resistance"),
            "Gloves" to item(3, "나쁜 장갑", "Rare", "Rusted Mitts", "+5 to maximum Life"),
        )
        val candidates = GearPricing.tradeCandidates(build)
        println("[TEST] 조회 후보=${candidates.map { it.first }}")
        assertEquals(2, candidates.size)
        assertEquals("Helmet", candidates.first().first)
        assertTrue(candidates.none { it.first == "Ring 1" })
    }

    @Test
    fun statIndexAndFiltersFollowPoEText() {
        val json = """
            {"result":[
              {"id":"explicit","label":"Explicit","entries":[
                {"id":"explicit.stat_life","text":"# to maximum Life","type":"explicit"},
                {"id":"explicit.stat_fire","text":"#% to Fire Resistance","type":"explicit"},
                {"id":"explicit.stat_move","text":"#% increased Movement Speed","type":"explicit"}
              ]},
              {"id":"implicit","entries":[{"id":"implicit.x","text":"# to maximum Life"}]}
            ]}
        """.trimIndent()
        val index = Trade2.parseStatIndex(json)
        println("[TEST] 스탯 사전=$index")
        assertEquals("explicit.stat_life", index["# to maximum life"])
        assertEquals("explicit.stat_fire", index["#% to fire resistance"])

        val boots = item(
            1, "질풍 장화", "Rare", "Expert Boots",
            "+90 to maximum Life", "+45% to Fire Resistance", "30% increased Movement Speed",
        )
        val filters = Trade2.filters(boots, index)
        println("[TEST] 필터=${filters.map { it.id to it.min }}")
        assertEquals(3, filters.size)
        val life = filters.first { it.id == "explicit.stat_life" }
        assertTrue("수치를 조금 낮춰 잡지 않았다: ${life.min}", life.min in 70.0..90.0)
    }

    @Test
    fun queryJsonCarriesTypeAndSort() {
        val item = item(1, "질풍 장화", "Rare", "Expert Boots", "+90 to maximum Life")
        val query = Trade2.queryJson(
            item,
            listOf(TradeFilter("explicit.stat_life", 76.0, "+90 to maximum Life")),
        )
        println("[TEST] 질의=$query")
        assertTrue(query.contains("\"id\":\"explicit.stat_life\""))
        assertTrue(query.contains("\"min\":76"))
        assertTrue(query.contains("\"type\":\"Expert Boots\""))
        assertTrue(query.contains("\"sort\":{\"price\":\"asc\"}"))
        assertTrue(query.contains("\"option\":\"online\""))
    }

    @Test
    fun quotesConvertToDivineAndMedian() {
        val rates = CurrencyRates(exaltedPerDivine = 320.0, chaosPerDivine = 96.0)
        val quotes = listOf(
            TradeQuote(2.0, "divine"),
            TradeQuote(320.0, "exalted"),
            TradeQuote(96.0, "chaos"),
            TradeQuote(5.0, "mirror"),
        )
        val median = Trade2.medianDivine(quotes, rates)
        println("[TEST] 중위값=$median")
        assertEquals(1.0, median, 0.01)
        assertEquals(2.0, quotes.first().divine(rates)!!, 0.001)
        assertEquals(null, TradeQuote(5.0, "mirror").divine(rates))
        assertEquals(0.0, Trade2.medianDivine(emptyList(), rates), 0.001)
    }

    @Test
    fun fetchQuotesAreParsed() {
        val json = """
            {"result":[
              {"id":"a","listing":{"price":{"type":"~b/o","amount":3,"currency":"divine"}}},
              {"id":"b","listing":{"price":{"type":"~b/o","amount":300,"currency":"exalted"}}},
              {"id":"c","listing":{}}
            ]}
        """.trimIndent()
        val quotes = Trade2.parseQuotes(json)
        assertEquals(2, quotes.size)
        assertEquals("divine", quotes[0].currency)
        assertEquals(300.0, quotes[1].amount, 0.001)
    }

    @Test
    fun sampleBuildValuationRuns() {
        // 앱에 들어 있는 예시 빌드로 실제 PoB 장비 줄을 그대로 통과시켜 본다.
        val build = Pob.decode(Pob.sampleCode())
        val value = GearPricing.value(build, CurrencyRates(300.0, 90.0))
        println("[TEST] 예시 빌드=${build.title} 부위=${value.slots.size} 합계=${value.divineText} 디바인")
        value.slots.forEach { println("  - ${it.rarity} ${it.line}") }
        assertTrue("예시 빌드에서 장비를 하나도 못 찾았다", value.slots.size >= 3)
        assertTrue("합계가 0이다", value.divine > 0.0)
        assertTrue("값에 주얼·플라스크가 섞였다", value.slots.none {
            it.slotKey.startsWith("Jewel") || it.slotKey.startsWith("Flask") || it.slotKey.startsWith("Charm")
        })
    }

    @Test
    fun emptyBuildHasNoValue() {
        val value = GearPricing.value(PobBuild())
        assertFalse(value.hasItems)
        assertEquals(0.0, value.divine, 0.001)
        assertTrue(value.divineText == "0.000")
    }
}
