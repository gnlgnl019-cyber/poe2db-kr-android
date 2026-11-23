package com.sunuhwi.poe2dbkr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * PoB가 TotalDPS/HitSpeed를 inf로 내보내는 빌드에서도
 * 주력 스킬별 DPS와 총합 DPS를 현실적인 값으로 산출하는지 확인한다.
 */
class PobDpsTest {

    private val realCode: String by lazy {
        File("src/test/resources/pob_real_build.txt").readText()
    }

    @Test
    fun realBuildInfStatsBecomeFinitePerSkillDps() {
        val build = Pob.decode(realCode)
        println("[TEST] nonFinite=${build.nonFiniteStats}")
        assertTrue("inf 스탯을 읽지 못했다", build.nonFiniteStats.contains("TotalDPS"))
        val calc = PobCalculator.compute(build, emptyMap())
        println("[TEST] main=${calc.mainSkill} total=${calc.totalDps}")
        println("[TEST] skills=${calc.skillDps.take(6).map { "${it.name}=${it.dps.toLong()}" }}")
        assertEquals("Dark Consequences", calc.mainSkill)
        assertTrue("총 DPS가 0", calc.totalDps > 1_000.0)
        assertTrue("총 DPS가 무한", calc.totalDps.isFinite())
        assertTrue("스킬별 DPS가 비었다", calc.skillDps.isNotEmpty())
        assertTrue("주력 스킬이 목록에 없다", calc.skillDps.any { it.name == "Dark Consequences" })
        assertTrue(
            "주력 스킬 DPS가 전체의 55%보다 작다",
            calc.skillDps.first { it.name == "Dark Consequences" }.dps >= calc.totalDps * 0.5,
        )
        calc.skillDps.forEach { skill ->
            assertTrue("${skill.name} DPS가 무한·NaN: ${skill.dps}", skill.dps.isFinite())
            assertTrue("${skill.name} DPS가 음수", skill.dps >= 0.0)
        }
    }

    @Test
    fun infHitSpeedUsesSkillHitCount() {
        val build = TestBuilds.build(
            groups = listOf(listOf("Whirling Assault", "Rapid Attacks III")),
            stats = TestBuilds.stats(
                "AverageHit" to 2_000.0,
                "Speed" to 2.0,
                "HitSpeed" to Double.POSITIVE_INFINITY,
                "CritChance" to 0.0,
                "CritMultiplier" to 1.5,
            ),
        )
        build.nonFiniteStats.add("HitSpeed")
        val calc = PobCalculator.compute(build, emptyMap())
        println("[TEST] 선회 공격 dps=${calc.dpsNow}")
        // 2,000 × 2.0 × 3타 = 12,000
        assertTrue("다단 히트가 반영되지 않았다: ${calc.dpsNow}", calc.dpsNow >= 11_000.0)
        assertTrue("DPS가 무한", calc.dpsNow.isFinite())
        assertTrue(calc.dpsEstimated)
    }

    @Test
    fun infTotalDpsBecomesDisplayCap() {
        // PoB가 TotalDPS를 무한대로 내보냈으면 계산이 발산한 빌드다.
        // 평균 타격으로 4만대까지 깎지 않고 poe.ninja처럼 32비트 상한(2.1B)으로 보여 준다.
        val build = TestBuilds.build(
            groups = listOf(listOf("Lightning Arrow", "Primal Armament")),
            stats = TestBuilds.stats(
                "AverageHit" to 5_000.0,
                "Speed" to 1.5,
                "CritChance" to 20.0,
                "CritMultiplier" to 2.0,
                "TotalDPS" to Double.POSITIVE_INFINITY,
            ),
        )
        build.nonFiniteStats.add("TotalDPS")
        val calc = PobCalculator.compute(build, emptyMap())
        println("[TEST] 상한 dps=${calc.dpsNow} 기준=${calc.dpsReference}")
        assertEquals(PobCalculator.DPS_CAP, calc.dpsNow, 1.0)
        assertTrue(calc.dpsInfinite)
        assertTrue(calc.dpsCapped)
        assertFalse("표시 상한은 추정치가 아니다", calc.dpsEstimated)
        // 나머지 스킬 몫을 잡는 기준값은 평균 타격 × 공속 × 치명타로 남아 있어야 한다.
        assertEquals(5_000.0 * 1.5 * 1.2, calc.dpsReference, 1.0)
    }

    @Test
    fun hitBasedFallbackWithoutPobDpsStaysFinite() {
        val build = TestBuilds.build(
            groups = listOf(listOf("Lightning Arrow", "Primal Armament")),
            stats = TestBuilds.stats(
                "AverageHit" to 5_000.0,
                "Speed" to 1.5,
                "CritChance" to 20.0,
                "CritMultiplier" to 2.0,
            ),
        )
        val calc = PobCalculator.compute(build, emptyMap())
        // 5,000 × 1.5 × (1 + 0.2 × 1.0) = 9,000
        println("[TEST] fallback dps=${calc.dpsNow}")
        assertEquals(9_000.0, calc.dpsNow, 1.0)
        assertTrue(calc.dpsEstimated)
        assertFalse(calc.dpsCapped)
    }

    @Test
    fun chipsGetDistinctPerSkillDps() {
        val build = Pob.decode(realCode)
        val calc = PobCalculator.compute(build, emptyMap())
        val byName = calc.skillDps.filter { it.dps > 0.0 }.associate { TriggerKind.normalizeName(it.name) to it.dps }
        val options = BattleSetup.skillOptions(build, KrDict.EMPTY, dpsByName = byName)
        println("[TEST] chips=${options.map { "${it.name}=${it.dps.toLong()}" }}")
        assertTrue("스킬 칩이 비었다", options.isNotEmpty())
        assertTrue("칩 DPS가 전부 같다", options.map { it.dps.toLong() }.distinct().size > 1)
        options.forEach { assertTrue("${it.name} 칩 DPS가 0", it.dps > 0.0) }
    }

    @Test
    fun totalDpsEqualsCalculatorResult() {
        val build = TestBuilds.build(
            groups = listOf(
                listOf("Fireball", "Elemental Focus"),
                listOf("Frost Bomb"),
            ),
            stats = TestBuilds.stats("AverageHit" to 3_000.0, "Speed" to 1.2, "TotalDPS" to 120_000.0),
        )
        val calc = PobCalculator.compute(build, emptyMap())
        println("[TEST] total=${calc.totalDps} dps=${calc.dpsNow} skills=${calc.skillDps.map { it.name to it.dps }}")
        assertEquals("총합이 계산 결과와 다르다", calc.dpsNow, calc.totalDps, 0.01)
        assertTrue("보조 젬 효과가 반영되지 않았다", calc.totalDps > 120_000.0)
        val sum = calc.skillDps.sumOf { it.dps }
        assertEquals("스킬별 DPS 합이 총합과 다르다", calc.totalDps, sum, calc.totalDps * 0.01)
    }
}
