package com.sunuhwi.poe2dbkr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 전투 시뮬레이션 틱이 어떤 빌드 수치에서도 계속 돌아가는지 확인한다.
 * 화면(캔버스)은 이 월드의 상태를 읽어 그리기만 하므로, 여기서 틱이 전진하면 전투가 멈추지 않는다.
 */
class BattleSimTest {

    private fun stats(
        dps: Double = 480_000.0,
        aps: Double = 3.42,
        life: Double = 4_120.0,
        es: Double = 860.0,
        crit: Double = 31.6,
        critMulti: Double = 312.0,
    ) = BattlePlayerStats(
        className = "Ranger · Deadeye",
        skillName = "Lightning Arrow",
        fx = SkillEffectType.LIGHTNING,
        weapon = WeaponVisualType.BOW,
        dps = dps,
        aps = aps,
        critChance = crit,
        critMulti = critMulti,
        life = life,
        es = es,
        physRed = 40.0,
        evade = 20.0,
        fireRes = 76.0,
        coldRes = 75.0,
        lightRes = 68.0,
        chaosRes = -12.0,
        ehp = 37_000.0,
        dpsBase = dps,
    )

    private fun tick(world: BattleWorld, seconds: Double) {
        val frames = (seconds * 60).toInt()
        repeat(frames) { world.update(1.0 / 60.0) }
    }

    @Test
    fun ticksAdvanceAndDealDamage() {
        val world = BattleWorld(stats(), BattleMode.MAP, 25, false, null)
        tick(world, 1.0)
        println("[TEST] 1s elapsed=${world.elapsed} damage=${world.damageDealt} playerLife=${world.player.life}")
        assertTrue("시뮬레이션 시간이 흐르지 않았다", world.elapsed > 0.9)
        assertTrue("피해가 들어가지 않았다", world.damageDealt > 0.0)
        assertTrue("적이 움직이지 않았다", world.enemies.any { it.x != 0f })
        assertEquals(BattleWorld.State.RUNNING, world.state)
    }

    @Test
    fun battleFinishesInReasonableTime() {
        val world = BattleWorld(stats(), BattleMode.MAP, 25, false, null)
        tick(world, 60.0)
        println("[TEST] 60s state=${world.state} kills=${world.kills}/${world.totalEnemies}")
        assertTrue("전투가 끝나지 않았다", world.state != BattleWorld.State.RUNNING)
    }

    @Test
    fun zeroDpsStillRuns() {
        // PoB에 TotalDPS가 없어 DPS가 0으로 들어와도 화면과 틱은 계속 돌아야 한다.
        val world = BattleWorld(stats(dps = 0.0), BattleMode.MAP, 10, false, null)
        tick(world, 3.0)
        println("[TEST] dps0 elapsed=${world.elapsed} state=${world.state} damage=${world.damageDealt}")
        assertTrue("DPS 0에서 시간이 멈췄다", world.elapsed > 2.9)
        assertEquals(BattleWorld.State.RUNNING, world.state)
    }

    @Test
    fun zeroApsKeepsAttacking() {
        val world = BattleWorld(stats(aps = 0.0), BattleMode.MAP, 10, false, null)
        tick(world, 3.0)
        println("[TEST] aps0 attacks=${world.player.attackCount} damage=${world.damageDealt}")
        assertTrue("공격이 시작되지 않았다", world.player.attackCount > 0)
        assertTrue("공격이 피해를 주지 않았다", world.damageDealt > 0.0)
    }

    @Test
    fun zeroLifeDoesNotEndImmediately() {
        val world = BattleWorld(stats(life = 0.0, es = 0.0), BattleMode.MAP, 10, false, null)
        tick(world, 1.0)
        println("[TEST] life0 state=${world.state} life=${world.player.life} elapsed=${world.elapsed}")
        assertTrue("생명력 0에서 첫 틱에 죽었다", world.elapsed > 0.9)
    }

    @Test
    fun bossEncounterSpawnsOneBoss() {
        val world = BattleWorld(stats(), BattleMode.BOSS, 25, false, BossPresets.ALL.first())
        assertEquals(1, world.totalEnemies)
        assertTrue(world.bossEnemy != null)
        tick(world, 2.0)
        assertTrue(world.elapsed > 1.9)
        println("[TEST] boss hp=${world.bossEnemy?.hp} damages=${world.damageDealt}")
    }
}
