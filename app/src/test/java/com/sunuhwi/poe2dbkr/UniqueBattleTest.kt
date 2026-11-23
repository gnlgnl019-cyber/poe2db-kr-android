package com.sunuhwi.poe2dbkr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 고유 아이템 효과가 전투 안에서 실제로 굴러가는지 확인한다.
 */
class UniqueBattleTest {

    private fun stats(unique: UniqueEffects.Sim, dps: Double = 600_000.0) = BattlePlayerStats(
        className = "Monk",
        skillName = "빠른 참격",
        fx = SkillEffectType.MELEE,
        weapon = WeaponVisualType.SWORD,
        dps = dps,
        aps = 3.0,
        critChance = 0.0,
        critMulti = 150.0,
        life = 6_000.0,
        es = 1_000.0,
        physRed = 70.0,
        evade = 40.0,
        fireRes = 80.0,
        coldRes = 80.0,
        lightRes = 80.0,
        chaosRes = 30.0,
        ehp = 90_000.0,
        dpsBase = dps,
        unique = unique,
    )

    @Test
    fun curseOnHitLowersResistanceAndShowsOnEnemy() {
        val sim = UniqueEffects.Sim(curseOnHit = true)
        val world = BattleWorld(stats(sim, dps = 50_000.0), BattleMode.MAP, 25, amp = false, boss = null)
        repeat(60 * 4) { world.update(1.0 / 60.0) }
        val cursed = world.enemies.count { it.curseT > 0f }
        println("[TEST] 저주 걸린 적=$cursed / 피해=${world.damageDealt.toLong()}")
        assertTrue("명중 시 저주가 적에게 남지 않았다", cursed > 0)
        assertTrue("피해가 0", world.damageDealt > 0.0)
    }

    @Test
    fun cullingStrikeFinishesWeakEnemies() {
        val sim = UniqueEffects.Sim(cullingStrike = true)
        val world = BattleWorld(stats(sim, dps = 200.0), BattleMode.MAP, 10, amp = false, boss = null)
        // 적을 모두 일격사 직전까지 깎아 둔다.
        world.enemies.forEach { it.hp = it.maxHp * 0.05 }
        repeat(60 * 3) { world.update(1.0 / 60.0) }
        println("[TEST] 마무리 타격 처치=${world.kills}")
        assertTrue("마무리 타격으로 처치되지 않았다", world.kills > 0)
    }

    @Test
    fun rareKillGrantsHeadhunterStacks() {
        val sim = UniqueEffects.Sim(rareKillBuff = true)
        val world = BattleWorld(stats(sim, dps = 2_000_000.0), BattleMode.MAP, 25, amp = false, boss = null)
        repeat(60 * 30) { world.update(1.0 / 60.0) }
        println("[TEST] 처치=${world.kills} 강화 중첩=${world.huntStacks}")
        assertTrue("희귀 몬스터를 잡지 못했다", world.kills > 0)
        assertTrue("희귀 처치 강화가 쌓이지 않았다", world.huntStacks > 0)
        assertEquals("강화 중첩이 5를 넘었다", true, world.huntStacks <= 5)
    }

    @Test
    fun extraProjectilesComeFromUniquesToo() {
        val base = stats(UniqueEffects.Sim(), dps = 100_000.0)
        val withBow = base.copy(
            weapon = WeaponVisualType.BOW,
            form = AttackForm.BOW,
            fx = SkillEffectType.LIGHTNING,
            skillName = "번개 화살",
            unique = UniqueEffects.Sim(extraProjectiles = 2),
        )
        val world = BattleWorld(withBow, BattleMode.MAP, 40, amp = false, boss = null)
        var maxProjectiles = 0
        repeat(60 * 5) {
            world.update(1.0 / 60.0)
            maxProjectiles = maxOf(maxProjectiles, world.projectiles.count { !it.hostile })
        }
        println("[TEST] 투사체 최대=$maxProjectiles 피해=${world.damageDealt.toLong()}")
        assertTrue("고유 아이템 추가 투사체가 발사되지 않았다", maxProjectiles >= 2)
    }

    @Test
    fun minionDamageBoostChangesMinionPlan() {
        val build = TestBuilds.minionBuild()
        val plain = Minions.plan(build, build.stats, mainIsSummon = true, playerDps = 100_000.0)
        val boosted = Minions.plan(
            build, build.stats, mainIsSummon = true, playerDps = 100_000.0, minionDamagePct = 50.0,
        )
        println("[TEST] 소환수 피해 ${plain.minionDps} → ${boosted.minionDps}")
        assertTrue("소환수 피해 증가가 반영되지 않았다", boosted.minionDps > plain.minionDps * 1.4)
    }
}
