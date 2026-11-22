package com.sunuhwi.poe2dbkr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 메타젬 에너지 초과 축적과 연발.
 *
 * 실제 PoE2에서는 한 번의 트리거에 혜성이 20발 넘게 쏟아지기도 한다.
 * 예전에는 에너지가 한 번 차면 1회만 발동하고 끝나서 그런 장면이 나오지 않았다.
 */
class MetaOverflowTest {

    private fun stats(
        dps: Double = 2_400_000.0,
        crit: Double = 100.0,
        weapon: WeaponVisualType = WeaponVisualType.BOW,
        fx: SkillEffectType = SkillEffectType.LIGHTNING,
        form: AttackForm = AttackForm.BOW,
    ) = BattlePlayerStats(
        className = "Ranger · Deadeye",
        skillName = "Spark",
        fx = fx,
        weapon = weapon,
        form = form,
        dps = dps,
        aps = 4.0,
        critChance = crit,
        critMulti = 300.0,
        life = 9_000.0,
        es = 2_000.0,
        physRed = 40.0,
        evade = 10.0,
        fireRes = 75.0,
        coldRes = 75.0,
        lightRes = 75.0,
        chaosRes = 0.0,
        ehp = 60_000.0,
        dpsBase = dps,
    )

    private val comet = TriggerSpec(
        kind = TriggerKind.CAST_ON_CRIT,
        triggerName = "Cast on Critical",
        groupLabel = "혜성",
        skills = listOf(TriggeredSkill("Comet", SkillEffectType.ICE, 900_000.0)),
        dps = 900_000.0,
    )

    private fun tick(world: BattleWorld, seconds: Double) {
        repeat((seconds * 60).toInt()) { world.update(1.0 / 60.0) }
    }

    @Test
    fun energyOverflowsPastOneHundredPercent() {
        val runtime = TriggerRuntime(comet, critRate = 4.0)
        runtime.addEnergy(TriggerRuntime.CAST_COST / 4.0)
        assertTrue("에너지가 안 찼다", runtime.energy > 0.0)
        runtime.addEnergy(5_000.0)
        assertEquals("에너지 상한", TriggerRuntime.MAX_ENERGY, runtime.energy, 0.001)
        assertTrue("100%를 훨씬 넘겨야 한다", runtime.energyPercent > 1_000.0)
        val burst = runtime.drainBurst()
        assertTrue("연발이 1발뿐이다: $burst", burst >= 20)
        assertTrue("남은 에너지가 음수", runtime.energy >= 0.0)
    }

    @Test
    fun singleTriggerReleasesManyComets() {
        val withTrigger = stats().copy(triggers = listOf(comet))
        val world = BattleWorld(withTrigger, BattleMode.MAP, 40, false, null)
        tick(world, 12.0)
        println(
            "[TEST] 연발 최대=${world.maxBurst} · 연발 총합=${world.burstCasts} " +
                "· 트리거 발동=${world.triggerCasts} · HUD=${world.metaHud()}",
        )
        assertEquals("트리거가 붙지 않았다", 1, world.triggers.size)
        assertTrue("한 번의 트리거에 연발이 나가지 않았다", world.maxBurst >= 10)
        assertTrue("연발 발 수가 부족하다: ${world.burstCasts}", world.burstCasts >= 20)
        assertTrue("메타 HUD가 비었다", world.metaHud().isNotBlank())
        val result = world.result()
        assertTrue("결과에 최대 연발이 없다", result.maxBurst >= 10)
        assertTrue("연발 피해가 없다", world.triggerDamage > 0.0)
        println("[TEST] 결과: 최대 연발 ${result.maxBurst} · 연발 ${result.burstCasts}발 · 피해 ${result.triggerDamage}")
    }

    @Test
    fun energyGrowsWithDamageNotJustEventCount() {
        // 보스전으로 돌려 잡몹이 금방 죽어 사건이 끊기는 일을 막는다.
        val boss = BossPresets.ALL.first { it.id == "arbiter" }
        val low = BattleWorld(stats(dps = 120_000.0).copy(triggers = listOf(comet)), BattleMode.BOSS, 25, false, boss)
        val high = BattleWorld(stats(dps = 6_000_000.0).copy(triggers = listOf(comet)), BattleMode.BOSS, 25, false, boss)
        tick(low, 5.0)
        tick(high, 5.0)
        println("[TEST] 낮은 DPS 연발=${low.burstCasts} · 높은 DPS 연발=${high.burstCasts}")
        assertTrue("피해가 클수록 메타 에너지가 더 차야 한다: ${low.burstCasts} vs ${high.burstCasts}",
            high.burstCasts > low.burstCasts)
        assertTrue("높은 DPS에서 연발이 없다", high.burstCasts > 0)
    }

    @Test
    fun burstQueueDrainsAsCastsFire() {
        val withTrigger = stats().copy(triggers = listOf(comet))
        val world = BattleWorld(withTrigger, BattleMode.MAP, 20, false, null)
        tick(world, 1.0)
        val early = world.triggers.first().queued
        tick(world, 6.0)
        val later = world.triggers.first().queued
        println("[TEST] 남은 연발 1초=$early · 7초=$later · 발동=${world.triggerCasts}")
        // 예약한 연발은 시간이 지나면 실제로 떨어져 큐가 줄어야 한다.
        assertTrue("연발이 하나도 예약되지 않았다", world.triggerCasts > 0)
        assertTrue("연발 큐가 줄지 않는다: $early → $later", later < early || early == 0)
    }
}
