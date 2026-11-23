package com.sunuhwi.poe2dbkr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * PoE2 장비창 배치와 탭 토글.
 *
 * 아이콘을 한 번 탭하면 옵션 창이 열리고, 같은 아이콘을 다시 탭하면 닫힌다.
 * 다른 칸을 탭하면 그 칸으로 바뀐다.
 */
class PobInventoryTest {

    @Test
    fun gridPlacesEveryEquipmentSlotOnce() {
        val wanted = listOf(
            "Helmet", "Body Armour", "Gloves", "Boots", "Amulet",
            "Ring 1", "Ring 2", "Belt", "Weapon 1", "Weapon 2",
        )
        val placed = PobInventory.GRID.map { it.slot }
        assertEquals("장비 슬롯이 빠졌거나 겹쳤다: $placed", wanted.sorted(), placed.sorted())
        placed.forEach { slot ->
            assertTrue("PoB에 없는 슬롯: $slot", Pob.SLOTS.containsKey(slot))
        }
    }

    @Test
    fun gridCellsDoNotOverlapAndFitTheGrid() {
        val seen = HashSet<Pair<Int, Int>>()
        PobInventory.GRID.forEach { cell ->
            assertTrue("${cell.slot} 열 범위를 벗어남", cell.col >= 0 && cell.lastCol < PobInventory.COLUMNS)
            assertTrue("${cell.slot} 행 범위를 벗어남", cell.row >= 0 && cell.row < PobInventory.ROWS)
            for (col in cell.col..cell.lastCol) {
                assertTrue("${cell.slot}가 다른 칸과 겹침", seen.add(col to cell.row))
            }
        }
    }

    @Test
    fun helmetSitsOnTopAndBodyInTheMiddle() {
        val helmet = PobInventory.GRID.first { it.slot == "Helmet" }
        val body = PobInventory.GRID.first { it.slot == "Body Armour" }
        val gloves = PobInventory.GRID.first { it.slot == "Gloves" }
        val boots = PobInventory.GRID.first { it.slot == "Boots" }
        assertEquals("투구는 맨 윗줄", 0, helmet.row)
        assertTrue("투구는 가운데 칸", helmet.col > 0 && helmet.lastCol < PobInventory.COLUMNS - 1)
        assertEquals("갑옷은 투구 바로 아래", helmet.row + 1, body.row)
        val ring1 = PobInventory.GRID.first { it.slot == "Ring 1" }
        val ring2 = PobInventory.GRID.first { it.slot == "Ring 2" }
        assertEquals("반지 1은 왼쪽", 0, ring1.col)
        assertEquals("반지 2는 오른쪽", PobInventory.COLUMNS - 1, ring2.col)
        assertEquals("두 반지는 같은 줄", ring1.row, ring2.row)
        assertEquals("허리띠는 가운데", body.col, PobInventory.GRID.first { it.slot == "Belt" }.col) 
        assertEquals("장갑은 왼쪽 아래", 0, gloves.col)
        assertEquals("장화는 오른쪽 아래", PobInventory.COLUMNS - 1, boots.col)
        assertEquals("장갑과 장화는 같은 줄", gloves.row, boots.row)
    }

    @Test
    fun firstTapOpensAndSecondTapOnSameIconCloses() {
        var selected: String? = null
        selected = PobInventory.toggle(selected, "Helmet")
        assertEquals("첫 탭에 열려야 한다", "Helmet", selected)
        assertTrue(PobInventory.isSelected(selected, "Helmet"))
        selected = PobInventory.toggle(selected, "Helmet")
        assertNull("같은 아이콘을 다시 탭하면 닫혀야 한다", selected)
        assertFalse(PobInventory.isSelected(selected, "Helmet"))
    }

    @Test
    fun tappingAnotherSlotSwitchesPanel() {
        var selected = PobInventory.toggle(null, "Helmet")
        selected = PobInventory.toggle(selected, "Body Armour")
        assertEquals("다른 칸을 탭하면 그 칸으로 바뀐다", "Body Armour", selected)
        assertFalse(PobInventory.isSelected(selected, "Helmet"))
        // 바뀐 칸을 다시 탭하면 닫힌다.
        assertNull(PobInventory.toggle(selected, "Body Armour"))
    }

    @Test
    fun extrasAreKnownSlots() {
        PobInventory.EXTRAS.forEach { slot ->
            assertTrue("보조 칸에 PoB에 없는 슬롯: $slot", Pob.SLOTS.containsKey(slot))
        }
        assertTrue("무기 교체 칸이 없다", "Weapon 1 Swap" in PobInventory.EXTRAS)
    }

    @Test
    fun flasksComeFromTheLoadedBuild() {
        val code = java.io.File("src/test/resources/pob_real_build.txt").readText()
        val build = Pob.decode(code)
        val flasks = PobInventory.flaskSlots(build)
        println("[TEST] flasks=$flasks")
        assertTrue("플라스크 슬롯을 찾지 못했다", flasks.isNotEmpty())
        assertTrue(flasks.all { it.startsWith(Pob.FLASK_PREFIX) })
        assertTrue("플라스크 아이템이 없다", flasks.all { Pob.itemForSlot(build, it) != null })
    }
}
