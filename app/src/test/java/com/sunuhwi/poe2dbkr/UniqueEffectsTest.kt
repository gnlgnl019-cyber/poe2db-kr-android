package com.sunuhwi.poe2dbkr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 고유 아이템의 특수 옵션이 전투 수치·시뮬레이션에 반영되는지 확인한다.
 */
class UniqueEffectsTest {

    private val realCode: String by lazy {
        File("src/test/resources/pob_real_build.txt").readText()
    }

    private fun build(vararg items: PobItem): PobBuild {
        val build = PobBuild(className = "Witch", ascendClassName = "Lich", level = 90)
        items.forEachIndexed { index, item ->
            item.id = index + 1
            build.items[item.id] = item
            build.slots[if (index == 0) "Amulet" else "Ring $index"] = item.id
        }
        return build
    }

    private fun item(name: String, vararg lines: String): PobItem = PobItem(
        name = name,
        rarity = "Unique",
        baseType = "Test Base",
        lines = lines.toMutableList(),
    )

    @Test
    fun readsRealBuildUniques() {
        val build = Pob.decode(realCode)
        val profile = UniqueEffects.profile(build)
        println("[TEST] 이름=${profile.names}")
        println("[TEST] 효과=${profile.effects}")
        println("[TEST] 요약=${profile.summary}")
        assertFalse("고유 아이템을 하나도 읽지 못했다", profile.isEmpty)
        assertTrue("헤드헌터를 찾지 못했다", profile.names.any { it.contains("Headhunter", true) })
        assertTrue("엘파의 울부짖음(정신력)을 찾지 못했다", profile.names.any { it.contains("Alpha", true) })
        assertTrue("추가 피해 효과를 찾지 못했다", profile.sim.gainAsExtraPct > 0.0)
        assertTrue("희귀 처치 옵션 획득을 찾지 못했다", profile.sim.rareKillBuff)
        assertTrue("정신력 효과를 찾지 못했다", profile.sim.spiritFlat > 0.0)
        assertTrue("피해 배수가 1보다 커야 한다", UniqueEffects.damageFactor(profile, true, true) > 1.0)
    }

    @Test
    fun parsesEnglishSpecialOptions() {
        val profile = UniqueEffects.profile(
            build(
                item(
                    "Test Bow",
                    "Gain 12% of Damage as Extra Fire Damage",
                    "Gain 15% of Damage as Extra Lightning Damage",
                    "Fires 2 additional Projectiles",
                    "Curse Enemies with Despair on Hit",
                    "Culling Strike",
                    "25% chance to Ignite",
                    "10% increased Minion Damage",
                    "+50 to Spirit",
                ),
            ),
        )
        val sim = profile.sim
        println("[TEST] sim=$sim")
        assertEquals(27.0, sim.gainAsExtraPct, 0.01)
        assertEquals(2, sim.extraProjectiles)
        assertTrue(sim.curseOnHit)
        assertTrue(sim.cullingStrike)
        assertEquals(25.0, sim.igniteChancePct, 0.01)
        assertEquals(10.0, sim.minionDamagePct, 0.01)
        assertEquals(50.0, sim.spiritFlat, 0.01)
        assertTrue("추가 투사체가 피해 배수에 안 들어갔다", UniqueEffects.damageFactor(profile, true, true) > 1.3)
    }

    @Test
    fun parsesKoreanSpecialOptions() {
        val profile = UniqueEffects.profile(
            build(
                item(
                    "시험용 목걸이",
                    "피해의 20%를 추가 냉기 피해로 획득",
                    "투사체 1개 추가",
                    "명중 시 적에게 저주 부여",
                    "마무리 타격",
                    "1초마다 생명력의 2% 재생",
                    "소환수 피해 30% 증가",
                ),
            ),
        )
        val sim = profile.sim
        println("[TEST] 한국어 sim=$sim")
        assertEquals(20.0, sim.gainAsExtraPct, 0.01)
        assertEquals(1, sim.extraProjectiles)
        assertTrue(sim.curseOnHit)
        assertTrue(sim.cullingStrike)
        assertTrue(sim.regenPct > 0.0)
        assertEquals(30.0, sim.minionDamagePct, 0.01)
    }

    @Test
    fun uniqueFactorFeedsCalculatorAndBattle() {
        val build = Pob.decode(realCode)
        val unique = UniqueEffects.profile(build)
        val calc = PobCalculator.compute(build, emptyMap(), emptySet(), PobCalculator.Config(), null, unique)
        println("[TEST] totalDps=${calc.totalDps} skills=${calc.skillDps.map { it.name to it.dps }}")
        assertTrue("계산된 DPS가 0", calc.totalDps > 0.0)
        assertTrue("DPS가 무한", calc.totalDps.isFinite())
        val stats = BattleSetup.statsFor(build)!!
        assertTrue("전투에 고유 효과가 없다", stats.uniqueApplied > 0)
        assertTrue("고유 효과 요약이 비었다", stats.uniqueSummary.isNotBlank())
        val world = BattleWorld(stats, BattleMode.MAP, 25, amp = false, boss = null)
        repeat(60 * 10) { world.update(1.0 / 60.0) }
        assertTrue("고유 효과가 들어간 전투 피해가 0", world.damageDealt > 0.0)
        assertTrue("전투 피해가 무한", world.damageDealt.isFinite())
    }
}
