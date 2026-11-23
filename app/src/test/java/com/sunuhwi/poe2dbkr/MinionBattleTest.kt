package com.sunuhwi.poe2dbkr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 소환수·반려수 젬이 전투에 실제로 세워져 싸우는지 확인한다.
 */
class MinionBattleTest {

    @Test
    fun planReadsSkeletonAndCompanionGems() {
        val build = TestBuilds.minionBuild()
        val plan = Minions.plan(build, build.stats, mainIsSummon = true, playerDps = 300_000.0)
        println("[TEST] minions=${plan.summary} reserved=${plan.spiritReserved}")
        assertTrue("소환수를 하나도 찾지 못했다", plan.spawns.isNotEmpty())
        val keys = plan.spawns.map { it.type.key }
        assertTrue("해골 전사를 찾지 못했다", "skeletal warrior" in keys)
        assertTrue("해골 저격수를 찾지 못했다", "skeletal sniper" in keys)
        assertTrue("반려수를 찾지 못했다", plan.spawns.any { it.type.companion })
        assertTrue("정신력 점유가 계산되지 않았다", plan.spiritReserved > 0.0)
        assertEquals(MinionPlan.EMPTY.isEmpty, MinionPlan.EMPTY.spawns.isEmpty())
    }

    @Test
    fun mainSummonSplitsPlayerDpsAcrossMinions() {
        val build = TestBuilds.minionBuild()
        val plan = Minions.plan(build, build.stats, mainIsSummon = true, playerDps = 300_000.0)
        val total = plan.minionDps
        println("[TEST] 소환수 DPS 합=$total")
        assertTrue("소환수 DPS가 0이다", total > 0.0)
        assertEquals("주력 DPS가 소환수 몫으로 나뉘지 않았다", 300_000.0, total, 1.0)
    }

    @Test
    fun battleSpawnsMinionsAndTheyDealDamage() {
        val build = TestBuilds.minionBuild()
        val stats = BattleSetup.statsFor(build)!!
        println("[TEST] stats minions=${stats.minions.summary}")
        assertTrue("전투 수치에 소환수가 없다", !stats.minions.isEmpty)
        val world = BattleWorld(stats, BattleMode.MAP, 25, amp = false, boss = null)
        println("[TEST] spawned minions=${world.minions.size}")
        assertTrue("전투에 소환수가 세워지지 않았다", world.minions.any { it.alive })
        assertTrue("반려수가 세워지지 않았다", world.companionCount > 0)
        repeat(60 * 30) { world.update(1.0 / 60.0) }
        println("[TEST] minionDamage=${world.minionDamage} minionKills=${world.minionKills} deaths=${world.minionDeaths}")
        assertTrue("소환수가 피해를 주지 않았다", world.minionDamage > 0.0)
        assertTrue("소환수 피해가 무한·NaN", world.minionDamage.isFinite())
    }

    @Test
    fun minionsPullAggroAndCanDie() {
        val build = TestBuilds.minionBuild()
        val stats = BattleSetup.statsFor(build)!!
        // 소환수를 일부러 약하게 만들어 적에게 죽는지 본다.
        val fragile = stats.copy(
            minions = stats.minions.copy(
                spawns = stats.minions.spawns.map { spawn ->
                    MinionSpawn(spawn.type.copy(lifeShare = 0.0004), spawn.count)
                },
            ),
        )
        val world = BattleWorld(fragile, BattleMode.MAP, 25, amp = false, boss = null)
        var aggro = 0
        repeat(60 * 20) {
            world.update(1.0 / 60.0)
            aggro += world.enemies.count { it.targetMinion != null }
        }
        println("[TEST] aggro samples=$aggro deaths=${world.minionDeaths}")
        assertTrue("적이 소환수를 노리지 않았다", aggro > 0)
        assertTrue("약한 소환수가 죽지 않았다", world.minionDeaths > 0)
    }

    @Test
    fun minionDeathFeedsCastOnMinionDeath() {
        val build = TestBuilds.minionBuild()
        val base = BattleSetup.statsFor(build, triggerMode = TriggerMode.OFF)!!
        val type = Minions.typeOf("Skeletal Warrior")!!
        val stats = base.copy(
            minions = MinionPlan(spawns = listOf(MinionSpawn(type.copy(lifeShare = 0.0002), 2))),
            triggers = listOf(
                TriggerSpec(
                    kind = TriggerKind.CAST_ON_MINION_DEATH,
                    triggerName = "Cast on Minion Death",
                    groupLabel = "테스트",
                    skills = listOf(TriggeredSkill("Comet", SkillEffectType.ICE, 40_000.0)),
                    dps = 40_000.0,
                ),
            ),
        )
        val world = BattleWorld(stats, BattleMode.MAP, 25, amp = false, boss = null)
        repeat(60 * 25) { world.update(1.0 / 60.0) }
        println("[TEST] deaths=${world.minionDeaths} triggerCasts=${world.triggerCasts}")
        assertTrue("소환수가 죽지 않았다", world.minionDeaths > 0)
        assertTrue("소환수 사망 시 시전이 발동하지 않았다", world.triggerCasts > 0)
    }

    @Test
    fun spiritBudgetLimitsSpawnCount() {
        val build = TestBuilds.build(
            groups = listOf(
                listOf("Skeletal Warrior"),
                listOf("Skeletal Brute"),
                listOf("Skeletal Frost Mage"),
            ),
            stats = TestBuilds.stats("Spirit" to 60.0),
        )
        val plan = Minions.plan(build, build.stats, playerDps = 100_000.0)
        println("[TEST] 정신력 60: ${plan.summary} reserved=${plan.spiritReserved} notes=${plan.notes}")
        assertTrue("정신력보다 많은 소환수를 세웠다", plan.spiritReserved <= 60.0 + 0.001)
        assertTrue("정신력 부족 안내가 없다", plan.notes.isNotEmpty())
    }
}
