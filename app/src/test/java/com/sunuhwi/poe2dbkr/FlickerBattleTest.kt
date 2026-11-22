package com.sunuhwi.poe2dbkr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

/**
 * 점멸 타격(순간이동 근접)과 초고속 공속 연출·판정을 확인한다.
 * 캔버스 없이 월드만 돌려도 잔상 이펙트와 타격 수가 상태로 남으므로 여기서 검증한다.
 */
class FlickerBattleTest {

    private fun meleeStats(
        skill: String = "Flicker Strike",
        aps: Double = 8.5,
        dps: Double = 620_000.0,
        weapon: WeaponVisualType = WeaponVisualType.SWORD,
    ) = BattlePlayerStats(
        className = "Monk · Invoker",
        skillName = skill,
        fx = SkillEffectType.MELEE,
        weapon = weapon,
        form = AttackForm.MELEE,
        dps = dps,
        aps = aps,
        critChance = 28.0,
        critMulti = 260.0,
        life = 3_200.0,
        es = 400.0,
        physRed = 35.0,
        evade = 25.0,
        fireRes = 75.0,
        coldRes = 75.0,
        lightRes = 75.0,
        chaosRes = 0.0,
        ehp = 21_000.0,
        dpsBase = dps,
    )

    private fun tick(world: BattleWorld, seconds: Double) {
        repeat((seconds * 60).toInt()) { world.update(1.0 / 60.0) }
    }

    @Test
    fun flickerSkillsAreRecognised() {
        assertTrue(MeleeMotion.isFlicker("Flicker Strike"))
        assertTrue(MeleeMotion.isFlicker("점멸 타격"))
        assertTrue(MeleeMotion.isFlicker("Charged Staff"))
        assertTrue(MeleeMotion.isFlicker("질풍"))
        assertFalse(MeleeMotion.isFlicker("Lightning Arrow"))
        assertFalse(MeleeMotion.isFlicker("Boneshatter"))
        assertTrue(meleeStats().flicker)
        assertFalse(meleeStats(skill = "Boneshatter").flicker)
    }

    @Test
    fun flickerTeleportsNextToEnemiesAndLeavesTrail() {
        val world = BattleWorld(meleeStats(), BattleMode.MAP, 12, false, null)
        val startX = world.player.x
        val startY = world.player.y
        var sawAfterImage = false
        var sawStreak = false
        var closest = Float.MAX_VALUE
        repeat(120) {
            world.update(1.0 / 60.0)
            if (world.effects.any { it.kind == FxKind.AFTERIMAGE }) sawAfterImage = true
            if (world.effects.any { it.kind == FxKind.STREAK }) sawStreak = true
            val alive = world.enemies.filter { e -> e.alive }
            if (alive.isNotEmpty()) {
                val d = alive.minOf { e -> hypot(e.x - world.player.x, e.y - world.player.y) }
                closest = minOf(closest, d)
            }
        }
        println("[TEST] flicker pos=${world.player.x},${world.player.y} start=$startX,$startY closest=$closest attacks=${world.player.attackCount}")
        assertTrue("점멸 타격이 공격하지 않았다", world.player.attackCount > 0)
        assertTrue("적 옆으로 순간이동하지 않았다 (최단 거리 $closest)", closest < 70f)
        assertTrue("이동 거리가 없어 걸어간 것으로 보인다", hypot(world.player.x - startX, world.player.y - startY) > 0f)
        assertTrue("잔상 이펙트가 없다", sawAfterImage)
        assertTrue("섬광 선 이펙트가 없다", sawStreak)
        assertTrue("피해를 주지 않았다", world.damageDealt > 0.0)
    }

    @Test
    fun flickerKeepsKillingWhileHuntingEachEnemy() {
        val world = BattleWorld(meleeStats(aps = 6.0), BattleMode.MAP, 10, false, null)
        tick(world, 8.0)
        println("[TEST] flicker kills=${world.kills}/${world.totalEnemies} attacks=${world.player.attackCount}")
        assertTrue("처치가 이어지지 않았다", world.kills >= 2)
    }

    @Test
    fun normalMeleeDoesNotTeleport() {
        val world = BattleWorld(meleeStats(skill = "Boneshatter", aps = 3.0), BattleMode.MAP, 12, false, null)
        var sawAfterImage = false
        repeat(120) {
            world.update(1.0 / 60.0)
            if (world.effects.any { it.kind == FxKind.AFTERIMAGE }) sawAfterImage = true
        }
        println("[TEST] normal melee pos=${world.player.x},${world.player.y}")
        assertFalse("일반 근접기가 순간이동 잔상을 남겼다", sawAfterImage)
    }

    @Test
    fun veryHighAttackSpeedAccumulatesHits() {
        // 12회/초 → 2초 동안 24타 근처. 프레임(1/60초)에 묻혀 타격이 사라지면 안 된다.
        val world = BattleWorld(meleeStats(aps = 12.0, dps = 1_200_000.0), BattleMode.MAP, 30, false, null)
        tick(world, 2.0)
        val attacks = world.player.attackCount
        println("[TEST] aps12 attacks/2s=$attacks dps=${world.damageDealt}")
        assertTrue("고공속에서 공격이 씹혔다: $attacks", attacks >= 20)
        assertTrue("고공속에서 타격이 24회를 크게 넘었다: $attacks", attacks <= 30)
    }

    @Test
    fun superFastAttacksStayWithinFrameBudget() {
        // 프레임 제한 상한(30회/초)을 넘겨 넣어도 한 프레임에 무한 연타로 폭주하지 않는다.
        val world = BattleWorld(meleeStats(aps = 40.0, dps = 2_000_000.0), BattleMode.MAP, 20, false, null)
        repeat(60) { world.update(1.0 / 30.0) } // 30프레임/초로 2초
        val attacks = world.player.attackCount
        println("[TEST] aps40 attacks/2s=$attacks")
        assertTrue("공격이 전혀 없었다", attacks > 0)
        assertTrue("한 프레임에 너무 많이 때렸다: $attacks", attacks <= 70)
    }
}
