package com.sunuhwi.poe2dbkr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 도이드리의 파멸 → 마녀두꺼비 → 암울한 결과(Dark Consequences)가
 * 인식되고 전투에서 실제로 터지는지 확인한다.
 *
 * 이 스킬은 저주받은 지대에 마녀두꺼비를 만들고, 두꺼비가 적에게 도약하면
 * 1.6미터 폭발(카오스 피해)이 발동한다. 젬 id는 DoedresUndoingSupport이고
 * 한글 이름은 도이드리의 파멸, 발동 스킬은 암울한 결과다.
 */
class DarkConsequencesTest {

    private val realCode: String by lazy {
        File("src/test/resources/pob_real_build.txt").readText()
    }

    @Test
    fun recognizesDoedreSupportByNameAndId() {
        assertTrue(Minions.isDoedre("Doedre's Undoing"))
        assertTrue(Minions.isDoedre("도이드리의 파멸"))
        assertTrue(Minions.isDoedre("Doedres Undoing"))
        assertFalse(Minions.isDoedre("Despair"))
        assertTrue("저주 젬을 찾지 못했다", Minions.isCurseGem("Despair"))
        assertTrue(Minions.isCurseGem("절망"))
    }

    @Test
    fun darkConsequencesDamageFollowsGemLevel() {
        val level1 = Minions.darkConsequencesDamage(1)
        val level20 = Minions.darkConsequencesDamage(20)
        val level40 = Minions.darkConsequencesDamage(40)
        println("[TEST] 1레벨=${level1} 20레벨=${level20} 40레벨=${level40}")
        assertEquals(5.0, level1.first, 0.01)
        assertEquals(10.0, level1.second, 0.01)
        assertEquals(186.0, level20.first, 0.01)
        assertEquals(345.0, level20.second, 0.01)
        assertEquals(3246.0, level40.first, 0.01)
        assertEquals("레벨이 범위를 벗어나도 값이 있어야 한다", 3246.0, Minions.darkConsequencesDamage(99).first, 0.01)
    }

    @Test
    fun planCreatesWitchtoads() {
        val build = TestBuilds.doedreBuild()
        val plan = Minions.plan(build, build.stats, playerDps = 45_000.0)
        println("[TEST] doedre plan: toads=${plan.toadCount} dps=${plan.darkConsequencesDps} notes=${plan.notes}")
        assertEquals("저주받은 지대 2개 × 4마리", 8, plan.toadCount)
        assertTrue("암울한 결과 피해가 없다", plan.hasDarkConsequences)
        assertTrue("암울한 결과 DPS가 0", plan.darkConsequencesDps > 0.0)
    }

    @Test
    fun mainSkillBecomesDarkConsequences() {
        val build = TestBuilds.doedreBuild()
        assertEquals("Dark Consequences", PobCalculator.mainSkillName(build))
        assertTrue("암울한 결과로 인식되지 않았다", PobCalculator.isDarkConsequences(build))
        val dps = PobCalculator.darkConsequencesDps(build, critMultiBase = 387.0)
        println("[TEST] 암울한 결과 DPS=$dps")
        assertTrue("암울한 결과 DPS가 0", dps > 1_000.0)
        assertTrue("DPS가 무한", dps.isFinite())
    }

    @Test
    fun battleExplodesToads() {
        val build = TestBuilds.doedreBuild()
        val stats = BattleSetup.statsFor(build)!!
        println("[TEST] skill=${stats.skillName} fx=${stats.fx} dps=${stats.dps} toads=${stats.minions.toadCount}")
        assertEquals("암울한 결과", stats.skillName)
        assertEquals(8, stats.minions.toadCount)
        assertTrue("플레이어 평타 DPS가 암울한 결과 DPS와 같다", stats.playerDps < stats.dps)
        val world = BattleWorld(stats, BattleMode.MAP, 25, amp = false, boss = null)
        assertEquals("저주받은 지대가 깔리지 않았다", 2, world.cursedGrounds.size)
        repeat(60 * 30) { world.update(1.0 / 60.0) }
        println("[TEST] explosions=${world.toadExplosions} darkDamage=${world.darkConsequencesDamage} kills=${world.kills}")
        assertTrue("두꺼비가 터지지 않았다", world.toadExplosions > 0)
        assertTrue("암울한 결과 피해가 없다", world.darkConsequencesDamage > 0.0)
        assertTrue("피해가 무한·NaN", world.darkConsequencesDamage.isFinite())
    }

    @Test
    fun curseWithoutDoedreDoesNotTrigger() {
        val build = TestBuilds.build(
            groups = listOf(listOf("Despair", "Spell Cascade")),
            stats = TestBuilds.stats("AverageHit" to 500.0),
        )
        assertFalse(PobCalculator.isDarkConsequences(build))
        val plan = Minions.plan(build, build.stats)
        assertEquals(0, plan.toadCount)
    }

    @Test
    fun realBuildRunsDarkConsequences() {
        val build = Pob.decode(realCode)
        assertTrue("실제 빌드에서 도이드리의 파멸을 찾지 못했다", Minions.darkConsequencesGroup(build) != null)
        assertEquals("Dark Consequences", PobCalculator.mainSkillName(build))
        val stats = BattleSetup.statsFor(build, dict = KrDict.EMPTY, tree = TreeDict.EMPTY)!!
        println("[TEST] 실빌드 skill=${stats.skillName} dps=${stats.dps} toads=${stats.minions.toadCount}")
        assertEquals("암울한 결과", stats.skillName)
        assertTrue("실빌드 암울한 결과 DPS가 0", stats.dps > 1_000.0)
        assertTrue("실빌드 DPS가 무한", stats.dps.isFinite())
        assertTrue("실빌드에서 두꺼비가 없다", stats.minions.toadCount > 0)
    }
}
