package com.sunuhwi.poe2dbkr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarketTest {

    private fun rate(name: String, base: String, divine: Double, change: Double = 0.0, listings: Int = 0) =
        MarketRate(
            id = name.lowercase(),
            name = name,
            koName = "",
            baseType = base,
            koBase = "",
            iconUrl = "",
            category = "",
            divine = divine,
            exalted = divine * 400,
            chaos = divine * 10,
            volume = listings.toDouble(),
            volumeCurrency = "",
            change = change,
            detailsId = name.lowercase(),
            listingCount = listings,
        )

    @Test
    fun tagsAreCleanedForDisplay() {
        assertEquals(
            "Adds (4-6) to (7-10) Physical Damage",
            Market.cleanTags("Adds (4-6) to (7-10) [Physical|Physical] Damage"),
        )
        assertEquals("Freeze Freezes Enemies", Market.cleanTags("[Freeze] [Freeze|Freezes] Enemies"))
        assertEquals("", Market.cleanTags(""))
    }

    @Test
    fun beltAmuletRingChipsFilterAccessories() {
        val all = listOf(
            rate("Ingenuity", "Utility Belt", 12.0),
            rate("The Everlasting Gaze", "Lapis Amulet", 3.0),
            rate("Polcirkeln", "Sapphire Ring", 1.5),
            rate("Snakepit", "Topaz Ring", 2.0),
        )
        val belts = Market.TYPES.first { it.id == "UniqueBelts" }.filter
        val amulets = Market.TYPES.first { it.id == "UniqueAmulets" }.filter
        val rings = Market.TYPES.first { it.id == "UniqueRings" }.filter
        assertEquals(listOf("Ingenuity"), all.filter(belts).map { it.name })
        assertEquals(listOf("The Everlasting Gaze"), all.filter(amulets).map { it.name })
        assertEquals(listOf("Polcirkeln", "Snakepit"), all.filter(rings).map { it.name })
    }

    @Test
    fun sortingWorks() {
        val list = listOf(
            rate("A", "Ring", 1.0, 5.0, 100),
            rate("B", "Ring", 9.0, -12.0, 3),
            rate("C", "Ring", 4.0, 40.0, 30),
        )
        assertEquals(listOf("B", "C", "A"), Market.sort(list, MarketSort.VALUE_DESC).map { it.name })
        assertEquals(listOf("A", "C", "B"), Market.sort(list, MarketSort.VALUE_ASC).map { it.name })
        assertEquals(listOf("C", "A", "B"), Market.sort(list, MarketSort.CHANGE_DESC).map { it.name })
        assertEquals(listOf("A", "C", "B"), Market.sort(list, MarketSort.LISTINGS_DESC).map { it.name })
        assertEquals(listOf("A", "B", "C"), Market.sort(list, MarketSort.NAME).map { it.name })
    }

    @Test
    fun uniqueCategoriesAreExposed() {
        val ids = Market.TYPES.map { it.id }
        listOf(
            "UniqueWeapons", "UniqueArmours", "UniqueAccessories",
            "UniqueBelts", "UniqueAmulets", "UniqueRings", "UniqueJewels",
        ).forEach { assertTrue("$it 없음", it in ids) }
        assertEquals("UniqueAccessories", Market.TYPES.first { it.id == "UniqueRings" }.apiType)
        assertEquals(MarketKind.ITEM, Market.TYPES.first { it.id == "UniqueWeapons" }.kind)
    }

    @Test
    fun dropSourceIsParsedFromPoe2dbHtml() {
        val html = """
            <table><tbody>
            <tr><td>Limit</td><td>「<a href="/kr/The_King_in_the_Mists">연무 속의 왕</a>」Dropped by</td></tr>
            </tbody></table>
            <div id="Forgerecipe" class="tab-pane fade"><div class="card mb-2">
            <h5 class="card-header">Forge recipe</h5>
            <div class="card-body"><h5 class="card-header">장착 Recipe /1</h5>
            <table><tbody><tr><td><a href="x">묠니르의 유산</a></td><td>1x <a href="y">알두르의 유산</a><br>1x <a href="z">묠니르</a><br></td><td></td></tr></tbody></table>
            </div></div></div>
            <div class="card mb-1"><div class="card-body">footer</div></div>
        """.trimIndent()
        val source = Poe2dbSource.parse(html)
        println("[TEST] source=$source")
        assertEquals(listOf("연무 속의 왕"), source.droppedBy)
        assertTrue(source.forged.contains("알두르의 유산"))
        assertTrue(source.found)
    }

    @Test
    fun emptyHtmlHasNoSource() {
        val source = Poe2dbSource.parse("<html><body>nothing</body></html>")
        assertTrue(source.droppedBy.isEmpty())
        assertEquals("", source.forged)
    }
}
