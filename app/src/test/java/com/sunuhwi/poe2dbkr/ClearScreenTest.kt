package com.sunuhwi.poe2dbkr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 클리어(승리) 화면 — 적을 다 처치한 순간의 승리 연출과, 사망 화면과 대칭인
 * 스킬별 DPS 페이드인 카드가 실제 전투 기록과 같은 숫자를 쓰는지 확인한다.
 */
class ClearScreenTest {

    private fun stats(dps: Double = 4_000_000.0, life: Double = 5_400.0, es: Double = 700.0) = BattlePlayerStats(
        className = "Ranger · Deadeye",
        skillName = "Lightning Arrow",
        fx = SkillEffectType.LIGHTNING,
        weapon = WeaponVisualType.BOW,
        dps = dps,
        aps = 4.2,
        critChance = 30.0,
        critMulti = 260.0,
        life = life,
        es = es,
        physRed = 25.0,
        evade = 45.0,
        fireRes = 76.0,
        coldRes = 76.0,
        lightRes = 76.0,
        chaosRes = -20.0,
        ehp = 32_000.0,
        dpsBase = dps,
    )

    private fun tick(world: BattleWorld, seconds: Double) {
        repeat((seconds * 60).toInt()) { world.update(1.0 / 60.0) }
    }

    /** 적을 모두 처치할 때까지 돌린다 (최대 20초) */
    private fun runToClear(world: BattleWorld): Boolean {
        var guard = 0
        while (world.state == BattleWorld.State.RUNNING && guard < 60 * 20) {
            world.update(1.0 / 60.0)
            guard++
        }
        return world.state == BattleWorld.State.VICTORY
    }

    // ------------------------------------------------------------- 클리어 연출

    @Test
    fun clearingEveryEnemyTriggersVictoryEffect() {
        val world = BattleWorld(stats(), BattleMode.MAP, 3, false, null, seed = 101L)
        assertTrue("클리어하지 못했다", runToClear(world))
        assertEquals(BattleWorld.State.VICTORY, world.state)
        assertEquals(3, world.kills)
        assertNotNull(
            "금빛 승리 물결이 없다",
            world.effects.firstOrNull { it.kind == FxKind.VICTORY_WAVE },
        )
        assertTrue(
            "빛기둥 연출이 없다",
            world.effects.any { it.kind == FxKind.PILLAR },
        )
        assertTrue("불꽃 파편이 없다", world.particles.isNotEmpty())
    }

    @Test
    fun victoryAgeRunsAfterTheClear() {
        val world = BattleWorld(stats(), BattleMode.MAP, 3, false, null, seed = 103L)
        runToClear(world)
        assertTrue("클리어 직후부터 시간이 흘렀다", world.victoryAge < 0.05f)
        tick(world, 1.0)
        assertTrue("클리어 뒤 시간이 흐르지 않는다", world.victoryAge > 0.9f)
    }

    @Test
    fun deathDoesNotProduceVictoryScreen() {
        val world = BattleWorld(stats(dps = 10.0, life = 260.0, es = 0.0), BattleMode.BOSS, 1, false, BossPresets.ALL[0], seed = 107L)
        world.strikePlayer(900_000.0, DamageElement.PHYSICAL, "우버 보스", "내려찍기")
        tick(world, 0.1)
        assertEquals(BattleWorld.State.DEFEAT, world.state)
        assertNull("죽었는데 클리어 요약이 있다", world.result().clear)
        assertEquals(0f, world.victoryAge, 0.0001f)
    }

    // ------------------------------------------------------------- 클리어 요약

    @Test
    fun clearSummaryMatchesBattleRecord() {
        val world = BattleWorld(stats(), BattleMode.MAP, 6, false, null, seed = 109L)
        runToClear(world)
        val result = world.result()
        val clear = result.clear
        assertNotNull("클리어 요약이 없다", clear)
        assertEquals(result.damageDealt, clear!!.damage, 0.001)
        assertEquals(result.damageDealt / result.seconds, clear.dps, 0.001)
        assertEquals(result.kills, clear.kills)
        assertEquals(result.totalEnemies, clear.total)
        assertTrue("스킬별 기록이 비었다", clear.skills.isNotEmpty())
        assertEquals(result.damageDealt, clear.skills.sumOf { it.damage }, 1.0)
        assertTrue(
            "내림차순이 아니다",
            clear.skills.zipWithNext().all { (a, b) -> a.damage >= b.damage },
        )
        assertEquals(100.0, clear.skills.sumOf { it.share }, 0.5)
        assertEquals(clear.skills.first(), clear.topSkill)
    }

    @Test
    fun clearRowsAreCappedAndTheRestCounted() {
        val skills = (1..12).map {
            SkillDamageReport("스킬$it", 1_000.0 * it, it, 100.0 * it, it.toDouble())
        }
        val summary = ClearSummary(12.5, 78_000.0, 6_240.0, 60, 60, skills)
        assertEquals(5, summary.rows(5).size)
        assertEquals(7, summary.rest(5))
        assertEquals(0, summary.rest(12))
        assertEquals(0, summary.rows(0).size)
    }

    @Test
    fun clearTextReadsLikeTheScreen() {
        val row = SkillDamageReport("연쇄 번개", 420_000.0, 42, 12_300.0, 84.0)
        val summary = ClearSummary(12.4, 500_000.0, 40_322.0, 60, 60, listOf(row))
        assertTrue(summary.headline.contains("12.4"))
        assertTrue(summary.bodyLine.contains("처치 60/60"))
        assertTrue(summary.bodyLine.contains("총 피해"))
        assertTrue(summary.bodyLine.contains("평균 DPS"))
        assertTrue(ClearText.rowDetail(row).contains("명중 42회"))
        assertTrue(ClearText.rowDetail(row).contains("84%"))
        assertTrue(ClearText.columnHeader().contains("비율"))
    }

    // ------------------------------------------------------------- 간단한 결과 화면

    @Test
    fun resultScreenSaysOnlyWhatHappened() {
        assertEquals("클리어!", ClearText.title())
        assertTrue("클리어 화면에 수치가 남아 있다", ClearText.subtitle().none { it.isDigit() })
        assertEquals("사망", DeathText.title())
        assertTrue("사망 화면에 수치가 남아 있다", DeathText.subtitle().none { it.isDigit() })
        assertTrue("클리어 화면 보조 글이 없다", ClearText.subtitle().isNotBlank())
        assertTrue("사망 화면 보조 글이 없다", DeathText.subtitle().isNotBlank())
    }

    // ------------------------------------------------------------- 페이드인 시간표

    @Test
    fun victoryCardFadesInStepByStep() {
        assertEquals(0f, VictoryFx.bannerAlpha(0f), 0.0001f)
        assertEquals(0f, VictoryFx.summaryAlpha(0f), 0.0001f)
        assertEquals(0f, VictoryFx.rowAlpha(0f, 0), 0.0001f)
        assertEquals(0f, VictoryFx.buttonAlpha(0f), 0.0001f)
        assertTrue(VictoryFx.done(VictoryFx.TOTAL_TIME + 0.1f))
        // 배너 → 요약 → 첫 줄 → 단추 순서로 올라온다
        val age = 1.2f
        assertTrue(VictoryFx.bannerAlpha(age) >= VictoryFx.summaryAlpha(age))
        assertTrue(VictoryFx.summaryAlpha(age) > 0f)
        assertTrue(VictoryFx.rowAlpha(age, 0) > 0f)
        assertEquals(0f, VictoryFx.buttonAlpha(age), 0.0001f)
        // 줄은 한 줄씩 늦게 올라온다
        assertTrue(VictoryFx.rowAlpha(1.6f, 0) > VictoryFx.rowAlpha(1.6f, 3))
        assertEquals(1f, VictoryFx.rowAlpha(VictoryFx.TOTAL_TIME, 0), 0.0001f)
    }

    @Test
    fun listCapacityShrinksOnShortScreens() {
        val tall = VictoryFx.rowCapacity(0f, 900f, 22f, 30f, reserve = 14f)
        val short = VictoryFx.rowCapacity(0f, 120f, 22f, 30f, reserve = 14f)
        assertEquals(9, tall)
        assertTrue("좁은 화면에서 줄이 너무 많다 ($short)", short in 1..2)
        assertEquals(0, VictoryFx.rowCapacity(200f, 150f, 22f, 30f))
        assertEquals(0, VictoryFx.rowCapacity(0f, 900f, 22f, 0f))
    }

    @Test
    fun flashAndGlowFadeOverTime() {
        assertEquals(1f, VictoryFx.flash(0f), 0.0001f)
        assertTrue(VictoryFx.flash(0.5f) < 1f)
        assertEquals(0f, VictoryFx.flash(1f), 0.0001f)
        assertTrue(VictoryFx.glow(0.6f) > VictoryFx.glow(0.1f))
    }
}
