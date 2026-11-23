package com.sunuhwi.poe2dbkr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * 원소 상태이상 — 동결(이동·공격 정지, 얼음 감옥, 산산조각), 감전(받는 피해 증가),
 * 점화(화염 도트가 `점화` DPS로 적립)가 실제 전투 수치에 반영되는지 확인한다.
 */
class AilmentsTest {

    private fun stats(
        element: SkillEffectType = SkillEffectType.ICE,
        dps: Double = 60_000.0,
        magnitude: Double = 0.0,
    ) = BattlePlayerStats(
        className = "Monk · Invoker",
        skillName = "Ice Strike",
        fx = element,
        weapon = WeaponVisualType.QUARTERSTAFF,
        dps = dps,
        aps = 2.0,
        critChance = 10.0,
        critMulti = 180.0,
        life = 9_000.0,
        es = 1_200.0,
        physRed = 35.0,
        evade = 20.0,
        fireRes = 75.0,
        coldRes = 75.0,
        lightRes = 75.0,
        chaosRes = 0.0,
        ehp = 55_000.0,
        dpsBase = dps,
        ailmentMagnitudePct = magnitude,
    )

    private fun tick(world: BattleWorld, seconds: Double) {
        repeat((seconds * 60).toInt()) { world.update(1.0 / 60.0) }
    }

    // ------------------------------------------------------------- 동결

    @Test
    fun frozenEnemyDoesNotMoveOrAttack() {
        val world = BattleWorld(stats(), BattleMode.BOSS, 1, false, BossPresets.ALL[3], seed = 17L)
        val boss = world.testEnemy(0)!!
        tick(world, 0.5)
        boss.freezeT = 4f
        val x = boss.x
        val y = boss.y
        val attacks = boss.attackCount
        tick(world, 0.9)
        assertEquals("얼어붙은 적이 움직였다", x, boss.x, 0.001f)
        assertEquals("얼어붙은 적이 움직였다", y, boss.y, 0.001f)
        assertEquals("얼어붙은 적이 공격했다", attacks, boss.attackCount)
    }

    @Test
    fun frozenEnemyMovesAgainOnceItThaws() {
        val world = BattleWorld(stats(dps = 1_000.0), BattleMode.MAP, 30, false, null, seed = 19L)
        tick(world, 0.4)
        val enemy = world.testEnemy(0)!!
        enemy.freezeT = 0.5f
        val x = enemy.x
        tick(world, 0.3)
        assertEquals("얼어 있는 동안 움직였다", x, enemy.x, 0.001f)
        tick(world, 1.2)
        assertTrue("녹은 뒤에도 제자리다", abs(enemy.x - x) > 0.5f || enemy.hp <= 0.0)
    }

    @Test
    fun frozenDeathShatters() {
        val world = BattleWorld(stats(), BattleMode.MAP, 10, false, null, seed = 23L)
        tick(world, 0.4)
        val enemy = world.testEnemy(0)!!
        world.testKill(enemy, frozen = true)
        assertTrue(enemy.shattered)
        assertEquals(1, world.shatters)
        assertNotNull(
            "산산조각 파편 연출이 없다",
            world.effects.firstOrNull { it.kind == FxKind.SHATTER },
        )
        assertTrue("산산조각에 얼음 파편이 없다", world.particles.isNotEmpty())
    }

    @Test
    fun unfrozenDeathLeavesNoShatter() {
        val world = BattleWorld(stats(), BattleMode.MAP, 10, false, null, seed = 29L)
        tick(world, 0.4)
        val enemy = world.testEnemy(0)!!
        world.testKill(enemy)
        assertFalse(enemy.shattered)
        assertEquals(0, world.shatters)
    }

    // ------------------------------------------------------------- 감전

    @Test
    fun shockStartsAtTwentyPercentMoreDamage() {
        assertEquals(1.20, Ailments.shockMultiplier(0.0), 0.0001)
        assertEquals(1.40, Ailments.shockMultiplier(100.0), 0.0001)
        assertEquals(1.60, Ailments.shockMultiplier(500.0), 0.0001)
        assertEquals(20.0, Ailments.shockTakenPct(0.0), 0.0001)
    }

    @Test
    fun shockedEnemyTakesMoreDamage() {
        // 근접 타격은 연쇄·분열 피해를 더하지 않아 한 방의 배율만 따로 잴 수 있다.
        val world = BattleWorld(stats(element = SkillEffectType.MELEE, dps = 10_000.0), BattleMode.MAP, 10, false, null, seed = 31L)
        tick(world, 0.3)
        val plain = world.enemies.first { it.alive }
        val shocked = world.enemies.first { it.alive && it !== plain }
        shocked.shockT = 4f
        plain.hp = 5_000_000.0
        shocked.hp = 5_000_000.0
        val plainTaken = world.testHit(plain, 1_000.0)
        val shockedTaken = world.testHit(shocked, 1_000.0)
        assertEquals(1_000.0, plainTaken, 0.5)
        assertTrue("감전이 피해를 늘리지 않았다 ($shockedTaken)", shockedTaken >= plainTaken * 1.2)
    }

    @Test
    fun shockMultiplierGrowsWithAilmentMagnitude() {
        val base = BattleWorld(stats(element = SkillEffectType.LIGHTNING), BattleMode.MAP, 6, false, null, seed = 37L)
        val magnified = BattleWorld(stats(element = SkillEffectType.LIGHTNING, magnitude = 100.0), BattleMode.MAP, 6, false, null, seed = 37L)
        val a = base.testEnemy(0)!!
        val b = magnified.testEnemy(0)!!
        a.shockT = 4f
        b.shockT = 4f
        a.hp = 9_000_000.0
        b.hp = 9_000_000.0
        val plainTaken = base.testHit(a, 2_000.0)
        val boostedTaken = magnified.testHit(b, 2_000.0)
        assertTrue("감전 수치가 피해에 반영되지 않았다", boostedTaken > plainTaken)
    }

    // ------------------------------------------------------------- 점화

    @Test
    fun igniteDpsComesFromTheHit() {
        assertEquals(2_200.0, Ailments.igniteDps(10_000.0, 0.0), 0.001)
        assertEquals(4_400.0, Ailments.igniteDps(10_000.0, 100.0), 0.001)
        assertEquals(0.0, Ailments.igniteDps(-5.0, 0.0), 0.001)
    }

    @Test
    fun igniteTicksAreCreditedToIgniteDps() {
        val world = BattleWorld(stats(element = SkillEffectType.FIRE), BattleMode.MAP, 10, false, null, seed = 41L)
        tick(world, 0.3)
        val enemy = world.testEnemy(0)!!
        enemy.hp = 900_000.0
        enemy.igniteDps = 5_000.0
        enemy.igniteT = 2.0f
        val before = world.damageDealt
        tick(world, 1.0)
        val row = world.skillTally().firstOrNull { it.name == Ailments.IGNITE_LABEL }
        assertNotNull("점화 피해가 기록되지 않았다", row)
        assertTrue("점화 피해가 너무 적다", row!!.damage > 3_000.0)
        assertTrue("전체 피해가 안 늘었다", world.damageDealt > before)
        assertTrue(world.damageLedgerBalanced())
    }

    @Test
    fun ailmentTicksStopWhenTheTimerRunsOut() {
        val world = BattleWorld(stats(element = SkillEffectType.MELEE, dps = 1.0), BattleMode.MAP, 6, false, null, seed = 43L)
        tick(world, 0.2)
        val enemy = world.testEnemy(0)!!
        enemy.hp = 9_000_000.0
        enemy.igniteT = 0.4f
        enemy.igniteDps = 1_000.0
        tick(world, 1.2)
        assertEquals(0f, enemy.igniteT, 0.0001f)
        val after = world.skillTally().firstOrNull { it.name == Ailments.IGNITE_LABEL }?.damage ?: 0.0
        tick(world, 0.5)
        val later = world.skillTally().firstOrNull { it.name == Ailments.IGNITE_LABEL }?.damage ?: 0.0
        assertTrue("불이 꺼졌는데 점화 피해가 계속 쌓인다 ($after → $later)", later - after < 2.0)
    }
}
