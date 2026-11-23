package com.sunuhwi.poe2dbkr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 16T 맵핑 마릿수를 500까지 늘려도 화면·연산이 버티는지 확인한다.
 * 500마리를 한꺼번에 그리지 않고, 활성 상한(120)만 세운 뒤 처치되면 채우는 방식이다.
 */
class MobScalingTest {

    private fun stats(dps: Double = 2_000_000.0) = BattlePlayerStats(
        className = "Ranger · Deadeye",
        skillName = "Lightning Arrow",
        fx = SkillEffectType.LIGHTNING,
        weapon = WeaponVisualType.BOW,
        dps = dps,
        aps = 4.2,
        critChance = 45.0,
        critMulti = 320.0,
        life = 14_000.0,
        es = 4_000.0,
        physRed = 88.0,
        evade = 72.0,
        fireRes = 90.0,
        coldRes = 90.0,
        lightRes = 90.0,
        chaosRes = 60.0,
        ehp = 400_000.0,
        dpsBase = dps,
    )

    @Test
    fun countsBeyondHundredAreSupported() {
        listOf(10, 25, 50, 100, 200, 300, 500).forEach { count ->
            val world = BattleWorld(stats(), BattleMode.MAP, count, amp = false, boss = null)
            assertEquals("전체 마릿수가 다르다", count, world.totalEnemies)
            assertTrue("활성 상한을 넘겼다: ${world.aliveCount}", world.aliveCount <= BattleWorld.ACTIVE_CAP)
            println("[TEST] ${count}마리 → 활성 ${world.aliveCount}, 대기 ${world.pendingCount}")
        }
    }

    @Test
    fun fiveHundredMobsRunToCompletionWithinBudget() {
        val world = BattleWorld(stats(8_000_000.0), BattleMode.MAP, 500, amp = false, boss = null)
        val startedAt = System.nanoTime()
        var steps = 0
        val maxSteps = 60 * 240
        while (world.state == BattleWorld.State.RUNNING && steps < maxSteps) {
            world.update(1.0 / 60.0)
            steps++
            assertTrue("동시 활성 적이 상한을 넘었다", world.aliveCount <= BattleWorld.ACTIVE_CAP + 4)
        }
        val wall = (System.nanoTime() - startedAt) / 1_000_000.0
        println("[TEST] 500마리: ${steps}스텝, ${"%.0f".format(wall)}ms, 처치 ${world.kills}/${world.totalEnemies}, 상태 ${world.state}")
        assertEquals("모든 적을 처치하지 못했다", 500, world.kills)
        assertEquals("승리로 끝나지 않았다", BattleWorld.State.VICTORY, world.state)
        assertTrue("처치 수가 무한·NaN", world.damageDealt.isFinite())
        // JVM 테스트 머신에서 240초 분량을 30초 안에 못 돌면 최적화가 빠진 것이다.
        assertTrue("500마리 시뮬레이션이 너무 느리다: ${wall}ms", wall < 30_000.0)
    }

    @Test
    fun renderCostStaysBoundedByActiveCap() {
        // 캔버스는 world.enemies만 그린다. 목록이 500까지 커지지 않는지 확인한다.
        val world = BattleWorld(stats(200_000.0), BattleMode.MAP, 500, amp = false, boss = null)
        var maxList = 0
        repeat(60 * 60) {
            world.update(1.0 / 60.0)
            maxList = maxOf(maxList, world.enemies.size)
        }
        println("[TEST] enemies 목록 최대 ${maxList}마리")
        assertTrue("그릴 목록이 상한을 크게 넘었다: $maxList", maxList <= BattleWorld.ACTIVE_CAP + 60)
    }

    @Test
    fun performancePerFrameFitsSixtyFpsBudget() {
        val world = BattleWorld(stats(3_000_000.0), BattleMode.MAP, 500, amp = true, boss = null)
        repeat(30) { world.update(1.0 / 60.0) }
        val start = System.nanoTime()
        repeat(300) { world.update(1.0 / 60.0) }
        val perFrameMs = (System.nanoTime() - start) / 1_000_000.0 / 300.0
        println("[TEST] 500마리 프레임당 ${"%.2f".format(perFrameMs)}ms")
        // 기기에서는 이보다 느리지만, JVM에서 프레임당 16ms를 넘으면 폰에서는 확실히 밀린다.
        assertTrue("프레임당 계산이 너무 무겁다: ${perFrameMs}ms", perFrameMs < 16.0)
    }
}
