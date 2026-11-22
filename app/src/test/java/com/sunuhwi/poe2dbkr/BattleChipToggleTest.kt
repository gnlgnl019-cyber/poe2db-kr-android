package com.sunuhwi.poe2dbkr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 전투 탭 '피통·피해 증폭' 칩이 누를 때마다 켜짐/꺼짐을 오가는지 확인한다.
 * 화면 코드는 이 규칙대로 칩의 체크 상태를 바꾸고, 그 값을 그대로 BattleWorld에 넘긴다.
 */
class BattleChipToggleTest {

    private fun stats() = BattlePlayerStats(
        className = "Ranger · Deadeye",
        skillName = "Lightning Arrow",
        fx = SkillEffectType.LIGHTNING,
        weapon = WeaponVisualType.BOW,
        dps = 480_000.0,
        aps = 3.42,
        critChance = 31.6,
        critMulti = 312.0,
        life = 4_120.0,
        es = 860.0,
        physRed = 40.0,
        evade = 20.0,
        fireRes = 76.0,
        coldRes = 75.0,
        lightRes = 68.0,
        chaosRes = -12.0,
        ehp = 37_000.0,
        dpsBase = 480_000.0,
    )

    @Test
    fun toggleFlipsEveryTap() {
        var amp = false
        repeat(5) { tap ->
            amp = BattleChipRule.stateAfterClick(BattleChipKind.TOGGLE, amp)
            println("[TEST] 증폭 칩 ${tap + 1}번째 클릭 -> amp=$amp")
            assertEquals("증폭 칩을 누를 때마다 상태가 뒤집혀야 한다", (tap + 1) % 2 == 1, amp)
        }
    }

    @Test
    fun toggleStartsFromEitherSide() {
        assertTrue(BattleChipRule.stateAfterClick(BattleChipKind.TOGGLE, false))
        assertFalse(BattleChipRule.stateAfterClick(BattleChipKind.TOGGLE, true))
    }

    @Test
    fun pickChipKeepsItselfOn() {
        // 모드·마릿수·보스처럼 하나만 켜지는 칩은 다시 눌러도 꺼지지 않는다.
        assertTrue(BattleChipRule.stateAfterClick(BattleChipKind.PICK, true))
        assertTrue(BattleChipRule.stateAfterClick(BattleChipKind.PICK, false))
    }

    @Test
    fun ampOnRaisesMonsterHealthAndDamage() {
        val normal = BattleWorld(stats(), BattleMode.MAP, 25, amp = false, boss = null)
        val boosted = BattleWorld(stats(), BattleMode.MAP, 25, amp = true, boss = null)

        assertEquals("25마리 · 16T", normal.bannerText)
        assertEquals("25마리 · 증폭 16T", boosted.bannerText)
        assertEquals(normal.totalEnemies, boosted.totalEnemies)

        val hpRatio = boosted.enemies.sumOf { it.maxHp } / normal.enemies.sumOf { it.maxHp }
        val hitRatio = boosted.enemies.sumOf { it.hit } / normal.enemies.sumOf { it.hit }
        println("[TEST] 증폭 체력 배율=$hpRatio 피해 배율=$hitRatio")
        assertEquals(1.8, hpRatio, 1e-6)
        assertEquals(1.4, hitRatio, 1e-6)
    }

    @Test
    fun ampOffGoesBackToNormalWorld() {
        // 켰다가 다시 끄면 다음 전투는 일반 16T 수치로 돌아간다.
        val kicked = BattleChipRule.stateAfterClick(BattleChipKind.TOGGLE, false)
        val restored = BattleChipRule.stateAfterClick(BattleChipKind.TOGGLE, kicked)
        val world = BattleWorld(stats(), BattleMode.MAP, 10, amp = restored, boss = null)
        val plain = BattleWorld(stats(), BattleMode.MAP, 10, amp = false, boss = null)

        assertFalse(restored)
        assertEquals("10마리 · 16T", world.bannerText)
        assertEquals(plain.enemies.sumOf { it.maxHp }, world.enemies.sumOf { it.maxHp }, 1e-6)
        assertEquals(plain.enemies.sumOf { it.hit }, world.enemies.sumOf { it.hit }, 1e-6)
    }
}
