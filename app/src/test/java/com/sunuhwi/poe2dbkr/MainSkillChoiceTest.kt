package com.sunuhwi.poe2dbkr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 전투 탭의 「메인 스킬 선택」이 실제 전투에 그대로 반영되는지 확인한다.
 *
 * - 빌드의 액티브 스킬이 선택 후보로 올라오는지
 * - A를 고르면 A만, B를 고르면 B만 나가는지 (봇이 다른 스킬을 섞어 쓰지 않는지)
 * - 콤보를 직접 짜지 않았으면 콤보 순서가 비어 있는지
 */
class MainSkillChoiceTest {

    private fun build(): PobBuild = TestBuilds.build(
        className = "Ranger",
        ascend = "Deadeye",
        groups = listOf(
            listOf("Lightning Arrow", "Rapid Attacks II"),
            listOf("Ice Shot"),
            listOf("Fireball"),
        ),
    )

    @Test
    fun activeSkillsOfTheBuildBecomeTheChoiceList() {
        val options = BattleSetup.skillOptions(build(), KrDict.EMPTY)
        println("[TEST] 메인 스킬 후보=" + options.map { it.gem })
        assertEquals("빌드의 액티브 스킬 3개가 후보로 올라와야 한다", 3, options.size)
        assertEquals("PoB 주력 그룹의 스킬이 맨 앞", "Lightning Arrow", options.first().gem)
        // 보조 젬은 후보가 아니다.
        assertTrue("보조 젬이 후보에 올라왔다", options.none { PobCalculator.isSupport(PobGem(name = it.gem)) })
    }

    private fun run(
        gem: String,
        seconds: Double = 4.0,
    ): BattleWorld {
        val stats = BattleSetup.statsFor(build(), skillOverride = gem)
        assertNotNull("전투 수치를 만들지 못했다: $gem", stats)
        val world = BattleWorld(stats!!, BattleMode.MAP, 12, false, null)
        repeat((seconds * 60).toInt()) { world.update(1.0 / 60.0) }
        return world
    }

    /** 스킬 피해 기록에서 상태이상(점화 등) 줄을 뺀 실제 스킬 이름 */
    private fun skillNames(world: BattleWorld): List<String> =
        world.skillTally().filter { it.damage > 0.0 }.map { it.name }
            .filter { it !in setOf("점화", "냉기", "감전", "중독", "출혈") }

    @Test
    fun everyChoiceOnTheChipRowIsHonoured() {
        // 칩에 올라온 스킬을 하나씩 골라, 그 스킬이 그대로 전투 수치가 되는지 본다.
        val options = BattleSetup.skillOptions(build(), KrDict.EMPTY)
        options.forEach { option ->
            val stats = BattleSetup.statsFor(build(), skillOverride = option.gem)
            assertNotNull("전투 수치를 만들지 못했다: ${option.gem}", stats)
            assertEquals("고른 스킬이 다른 스킬로 바뀌었다", option.gem, stats!!.skillName)
        }
    }

    @Test
    fun theBattleTabShowsNoNumberSummaryAboveTheChips() {
        // 전투 탭 위쪽의 수치 요약 글은 걷어 냈다 (스킬 선택과 얽혀 오해를 샀다).
        val layout = java.io.File("src/main/res/layout/pane_pob.xml").readText()
        assertFalse("전투 탭에 스킬 요약 글이 남아 있다", layout.contains("@+id/tvBattleSkill\""))
        assertFalse("전투 탭에 전직 효과 글이 남아 있다", layout.contains("@+id/tvAscendancy\""))
        assertFalse("전투 탭에 고유 효과 글이 남아 있다", layout.contains("@+id/tvBattleUnique\""))
        val activity = java.io.File("src/main/java/com/sunuhwi/poe2dbkr/MainActivity.kt").readText()
        assertFalse("전투 탭 요약을 아직 채운다", activity.contains("tvBattleSkill.text"))
        assertFalse("전투 탭 전직 글을 아직 채운다", activity.contains("tvAscendancy.text"))
    }

    @Test
    fun chosenSkillIsTheOnlySkillUsed() {
        val world = run("Ice Shot")
        val fired = skillNames(world)
        println("[TEST] 고른 스킬=얼음 화살 · 실제로 쓴 스킬=$fired")
        assertTrue("고른 스킬이 나가지 않았다", world.damageDealt > 0.0)
        assertEquals("고른 스킬 외의 스킬을 섞어 썼다: $fired", listOf("Ice Shot"), fired)
        assertTrue("콤보를 짜지 않았는데 콤보 순서가 생겼다", world.comboPlan.isEmpty())
        assertTrue("콤보를 짜지 않았는데 콤보 기록이 있다: ${world.comboHistory}", world.comboHistory.isEmpty())
    }

    @Test
    fun anotherChoiceUsesOnlyThatSkill() {
        val world = run("Fireball")
        val fired = skillNames(world)
        println("[TEST] 고른 스킬=화염구 · 실제로 쓴 스킬=$fired")
        assertTrue("고른 스킬이 나가지 않았다", world.damageDealt > 0.0)
        assertEquals("다른 스킬을 섞어 썼다: $fired", listOf("Fireball"), fired)
        assertFalse("얼음 화살이 남아 있다", fired.contains("Ice Shot"))
        assertFalse("번개 화살이 남아 있다", fired.contains("Lightning Arrow"))
    }

    @Test
    fun switchingTheChoiceSwitchesTheSkillAndDamage() {
        val ice = run("Ice Shot")
        val fire = run("Fireball")
        println("[TEST] 얼음 화살 피해=${ice.damageDealt.toLong()} · 화염구 피해=${fire.damageDealt.toLong()}")
        assertTrue(ice.damageDealt > 0.0)
        assertTrue(fire.damageDealt > 0.0)
        assertTrue(
            "고른 스킬이 바뀌었는데 같은 스킬로 싸운다",
            skillNames(ice) != skillNames(fire),
        )
    }

    @Test
    fun userComboIsTheOnlyThingThatMixesSkills() {
        val build = build()
        val combo = listOf(
            ComboStep("번개 화살", SkillMotion.DEFAULT, ComboRole.MAIN, repeats = 2),
            ComboStep("얼음 화살", SkillMotion.ICE_SHOT, ComboRole.BURST),
        )
        val stats = BattleSetup.statsFor(build, skillOverride = "Lightning Arrow", combo = combo)!!
        val world = BattleWorld(stats, BattleMode.MAP, 12, false, null)
        repeat(60 * 4) { world.update(1.0 / 60.0) }
        println("[TEST] 직접 짠 콤보=${world.comboHistory.take(8)} 직접=${world.comboCustom}")
        assertTrue("직접 짠 콤보로 인식하지 않았다", world.comboCustom)
        assertEquals(2, world.comboPlan.size)
        assertTrue("직접 짠 콤보가 돌지 않았다", world.comboHistory.isNotEmpty())
    }
}
