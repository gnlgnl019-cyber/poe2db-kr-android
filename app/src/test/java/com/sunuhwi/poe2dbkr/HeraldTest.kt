package com.sunuhwi.poe2dbkr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * 전령(Herald) 3종 — PoB에서 읽어 오는지, 전투에서 조건대로 터지는지, 그 피해가
 * 스킬별 기록과 전체 피해에 정확히 남는지 확인한다.
 */
class HeraldTest {

    // ------------------------------------------------------------- 시험용 빌드

    private fun gem(name: String, level: Int = 20, support: Boolean = false) = PobGem(
        name = name,
        level = level,
        enabled = true,
        skillId = if (support) "Support" + name.replace(" ", "").replace("'", "") + "Player" else name.replace(" ", "") + "Player",
    )

    private fun group(label: String, vararg gems: PobGem) = PobSkillGroup(label = label).apply {
        gems.forEach { this.gems.add(it) }
    }

    private fun build(spirit: Double, vararg groups: PobSkillGroup): PobBuild {
        val b = PobBuild(className = "Sorceress")
        groups.forEach { b.skills.add(it) }
        if (spirit > 0.0) b.stats["Spirit"] = spirit
        return b
    }

    private fun threeHeraldBuild(spirit: Double = 120.0) = build(
        spirit,
        group("Herald of Ice", gem("Herald of Ice"), gem("Magnified Area II", 1, true)),
        group("Herald of Thunder", gem("Herald of Thunder"), gem("Lightning Penetration", 1, true)),
        group("Herald of Ash", gem("Herald of Ash")),
    )

    // ------------------------------------------------------------- 해석

    @Test
    fun readsThreeHeraldsAndTheirSupports() {
        val plan = Heralds.plan(threeHeraldBuild())
        assertEquals(3, plan.specs.size)
        assertEquals(3, plan.active.size)
        assertEquals(HeraldKind.ICE, plan.specs[0].kind)
        assertEquals("얼음의 전령", plan.specs[0].name)
        assertEquals(listOf("Magnified Area II"), plan.specs[0].supports)
        assertEquals(90.0, plan.reserved, 0.001)
        assertTrue("정신력 줄이 비었다", plan.summary.contains("정신력 90/120"))
    }

    @Test
    fun detectsHeraldNamesInEnglishAndKorean() {
        assertEquals(HeraldKind.ICE, Heralds.detect("Herald of Ice"))
        assertEquals(HeraldKind.THUNDER, Heralds.detect("천둥의 전령"))
        assertEquals(HeraldKind.ASH, Heralds.detect("재의전령"))
        assertEquals(null, Heralds.detect("Ice Nova"))
        assertEquals(null, Heralds.detect(""))
    }

    @Test
    fun spiritBudgetTurnsLaterHeraldsOff() {
        // 정신력 60이면 두 개(30+30)만 켜지고 세 번째는 꺼진다.
        val plan = Heralds.plan(threeHeraldBuild(spirit = 60.0))
        assertEquals(2, plan.active.size)
        assertTrue(plan.has(HeraldKind.ICE))
        assertTrue(plan.has(HeraldKind.THUNDER))
        assertFalse(plan.has(HeraldKind.ASH))
        assertEquals(60.0, plan.reserved, 0.001)
        assertTrue(plan.specs[2].hudName.contains("꺼짐"))
    }

    @Test
    fun minionSpiritIsTakenOutFirst() {
        // 정신력 90에서 소환수가 40을 쓰면 50이 남아 얼음(30)만 켜지고 천둥은 못 켠다.
        val plan = Heralds.plan(threeHeraldBuild(spirit = 90.0), spiritForMinions = 40.0)
        assertTrue(plan.has(HeraldKind.ICE))
        assertFalse(plan.has(HeraldKind.THUNDER))
        assertEquals(30.0, plan.reserved, 0.001)
    }

    @Test
    fun disabledGroupIsOff() {
        val ice = group("Herald of Ice", gem("Herald of Ice"))
        ice.enabled = false
        val plan = Heralds.plan(build(120.0, ice, group("Herald of Ash", gem("Herald of Ash"))))
        assertEquals(1, plan.specs.size)
        assertEquals(HeraldKind.ASH, plan.specs[0].kind)
    }

    @Test
    fun noHeraldGemGivesEmptyPlan() {
        val plain = build(120.0, group("Fireball", gem("Fireball")))
        assertTrue(Heralds.plan(plain).isEmpty)
        assertTrue(Heralds.plan(null).isEmpty)
    }

    @Test
    fun supportsRaiseDamageAndArea() {
        val plain = HeraldSpec(HeraldKind.ICE, "Herald of Ice", "얼음의 전령", level = 20)
        val supported = plain.copy(supports = listOf("Magnified Area II", "Elemental Focus"))
        assertEquals(1.0, plain.damageMul, 0.001)
        assertTrue(supported.damageMul > plain.damageMul)
        assertTrue(supported.areaMul > plain.areaMul)
        assertFalse("원소 집중을 물렸는데 상태이상이 남았다", supported.appliesAilment)
        assertTrue(HeraldMath.iceDamage(1_000.0, supported) > HeraldMath.iceDamage(1_000.0, plain))
        assertTrue(HeraldMath.radius(HeraldKind.ICE, supported) > HeraldMath.radius(HeraldKind.ICE, plain))
    }

    @Test
    fun ashDamageUsesOverkill() {
        val spec = HeraldSpec(HeraldKind.ASH, "Herald of Ash", "재의 전령")
        val hit = 1_000.0
        assertEquals(300.0, HeraldMath.ashDamage(hit, 0.0, spec), 0.001)
        assertEquals(5_000.0, HeraldMath.ashDamage(hit, 5_000.0, spec), 0.001)
    }

    // ------------------------------------------------------------- 전투

    private fun stats(
        element: SkillEffectType = SkillEffectType.ICE,
        dps: Double = 300_000.0,
        heralds: HeraldPlan = HeraldPlan.EMPTY,
    ) = BattlePlayerStats(
        className = "Sorceress · Stormweaver",
        skillName = "Ice Strike",
        fx = element,
        weapon = WeaponVisualType.WAND,
        dps = dps,
        aps = 3.0,
        critChance = 15.0,
        critMulti = 200.0,
        life = 8_000.0,
        es = 1_000.0,
        physRed = 30.0,
        evade = 10.0,
        fireRes = 75.0,
        coldRes = 75.0,
        lightRes = 75.0,
        chaosRes = 0.0,
        ehp = 50_000.0,
        dpsBase = dps,
        heralds = heralds,
    )

    private fun planOf(vararg kinds: HeraldKind): HeraldPlan {
        val specs = kinds.map { HeraldSpec(it, it.en, it.ko, active = true) }
        return HeraldPlan(specs = specs, spirit = 120.0, reserved = 30.0 * kinds.size)
    }

    private fun tick(world: BattleWorld, seconds: Double) {
        repeat((seconds * 60).toInt()) { world.update(1.0 / 60.0) }
    }

    private fun row(world: BattleWorld, name: String): SkillDamageReport? =
        world.skillTally().firstOrNull { it.name == name }

    @Test
    fun iceHeraldExplodesWhenFrozenEnemyDies() {
        val world = BattleWorld(stats(heralds = planOf(HeraldKind.ICE)), BattleMode.MAP, 12, false, null, seed = 7L)
        tick(world, 0.4)
        val victim = world.testEnemy(0)
        assertNotNull(victim)
        val before = world.damageDealt
        world.testKill(victim!!, frozen = true)
        assertTrue("산산조각이 안 났다", victim.shattered)
        assertEquals(1, world.shatters)
        assertTrue("얼음의 전령이 안 터졌다", world.heraldProcsTotal >= 1)
        val report = world.heraldReports().firstOrNull { it.kind == HeraldKind.ICE }
        assertNotNull(report)
        assertTrue("전령 피해가 0", report!!.damage > 0.0)
        assertTrue("전체 피해가 안 늘었다", world.damageDealt > before)
        val recorded = row(world, "얼음의 전령")
        assertNotNull("스킬별 기록에 얼음의 전령이 없다", recorded)
        assertEquals(report.damage, recorded!!.damage, 0.001)
        assertTrue("원소 피해 합계와 전체 피해가 어긋난다", world.damageLedgerBalanced())
    }

    @Test
    fun iceHeraldStaysQuietWithoutFrozenDeath() {
        val world = BattleWorld(stats(heralds = planOf(HeraldKind.ICE)), BattleMode.MAP, 12, false, null, seed = 11L)
        tick(world, 0.4)
        val victim = world.testEnemy(0)!!
        world.testKill(victim, frozen = false)
        assertEquals(0, world.heraldProcsTotal)
        assertEquals(null, row(world, "얼음의 전령"))
    }

    @Test
    fun thunderHeraldStrikesAroundShockedKill() {
        val world = BattleWorld(
            stats(element = SkillEffectType.LIGHTNING, heralds = planOf(HeraldKind.THUNDER)),
            BattleMode.MAP, 12, false, null, seed = 21L,
        )
        tick(world, 0.6)
        val victim = world.testEnemy(0)!!
        world.testKill(victim, shocked = true)
        val report = world.heraldReports().firstOrNull { it.kind == HeraldKind.THUNDER }
        assertNotNull("천둥의 전령이 안 터졌다", report)
        assertTrue(report!!.procs >= 1)
        assertNotNull("스킬별 기록에 천둥의 전령이 없다", row(world, "천둥의 전령"))
        assertTrue(world.damageLedgerBalanced())
    }

    @Test
    fun ashHeraldBurnsNearbyEnemies() {
        val world = BattleWorld(stats(element = SkillEffectType.FIRE, heralds = planOf(HeraldKind.ASH)), BattleMode.MAP, 14, false, null, seed = 33L)
        tick(world, 0.8)
        val victim = world.testEnemy(0)!!
        val others = world.enemies.filter { it !== victim && it.alive }
        assertTrue("주변에 적이 없다", others.isNotEmpty())
        world.testKill(victim, overkill = 900_000.0)
        val report = world.heraldReports().firstOrNull { it.kind == HeraldKind.ASH }
        assertNotNull("재의 전령이 안 터졌다", report)
        assertTrue("재의 전령 피해가 없다", report!!.damage > 0.0)
        assertTrue("주변 적이 점화되지 않았다", others.any { it.igniteT > 0f })
        assertTrue(world.damageLedgerBalanced())
    }

    @Test
    fun everyHeraldPaysIntoTheSameLedger() {
        val world = BattleWorld(
            stats(heralds = planOf(HeraldKind.ICE, HeraldKind.THUNDER, HeraldKind.ASH)),
            BattleMode.MAP, 20, false, null, seed = 5L,
        )
        var guard = 0
        while (world.state == BattleWorld.State.RUNNING && guard < 60 * 30) {
            world.update(1.0 / 60.0)
            guard++
            // 몇 마리씩 동결·감전·초과 피해 상태로 처치해 전령을 모두 돌린다.
            if (guard % 40 == 0) {
                val target = world.enemies.firstOrNull { it.alive } ?: break
                world.testKill(target, frozen = guard % 80 == 0, shocked = guard % 120 == 0, overkill = 250_000.0)
            }
        }
        assertTrue("전령이 하나도 안 돌았다", world.heraldProcsTotal > 0)
        assertTrue("피해 기록과 전체 피해가 어긋난다", world.damageLedgerBalanced())
        val sum = world.skillTally().sumOf { it.damage }
        assertTrue(abs(sum - world.damageDealt) <= 1.0)
    }

    @Test
    fun withoutHeraldsNothingProcs() {
        val world = BattleWorld(stats(), BattleMode.MAP, 8, false, null, seed = 3L)
        tick(world, 0.5)
        world.testKill(world.testEnemy(0)!!, frozen = true, shocked = true, overkill = 500_000.0)
        assertEquals(0, world.heraldProcsTotal)
        assertTrue(world.heraldReports().isEmpty())
    }
}
