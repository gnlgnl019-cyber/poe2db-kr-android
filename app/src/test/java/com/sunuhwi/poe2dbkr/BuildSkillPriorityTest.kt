package com.sunuhwi.poe2dbkr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 주력 스킬 고르기와 스킬별 DPS 기록.
 *
 * 사용자가 제보한 증상: 폭풍의 질풍 빌드인데 전투는 이동기(점멸 타격)만 쓰고,
 * 클리어 후 기록에는 주력기 대신 '천둥의 전령'과 보조 젬('범위 집중')이 잡혔다.
 * 원인은 ① 보조 젬을 skillId 없이 들여온 빌드에서 보조 젬을 액티브로 착각한 것,
 * ② PoB가 지정한 주력이 이동기였을 때 그것만 쓰던 것, ③ 전령 피해를 상한 DPS로 다시 계산한 것이다.
 */
class BuildSkillPriorityTest {

    private val catalog: SpiritCatalog by lazy {
        SpiritCatalog.parse(File("src/main/assets/poe2_spirit_kr.json").readText())
    }

    private fun gem(
        name: String,
        level: Int = 20,
        skillId: String = name.replace(" ", "") + "Player",
        gemId: String = "",
        variantId: String = "",
    ) = PobGem(name = name, level = level, enabled = true, skillId = skillId, gemId = gemId, variantId = variantId)

    /** skillId·gemId가 비어 있는 채로 들어온 보조 젬 (PoB 공유 코드·poe.ninja 가져오기에서 흔하다) */
    private fun bareSupport(name: String) = PobGem(name = name, level = 1, enabled = true, skillId = "")

    private fun group(label: String, vararg gems: PobGem) = PobSkillGroup(label = label).apply {
        gems.forEach { this.gems.add(it) }
    }

    /**
     * 폭풍의 질풍(주력) + 점멸 타격(이동기) + 범위 집중(보조) + 천둥의 전령 + 정신력 젬 조합.
     * mainSocketGroup은 점멸 타격 그룹을 가리킨다 (PoB가 그렇게 저장한 빌드).
     */
    private fun monkBuild(): PobBuild {
        val b = PobBuild(className = "Monk", ascendClassName = "Invoker", level = 90)
        b.stats["Spirit"] = 240.0
        b.stats["AverageHit"] = 1_000_000.0
        b.stats["HitSpeed"] = 5.0
        b.stats["CritChance"] = 12.0
        b.stats["CritMultiplier"] = 2.2
        b.stats["Life"] = 3_200.0
        b.stats["EnergyShield"] = 600.0
        b.stats["Armour"] = 5_000.0
        b.stats["Evasion"] = 6_000.0
        b.stats["FireResist"] = 75.0
        b.stats["ColdResist"] = 75.0
        b.stats["LightningResist"] = 75.0
        b.stats["ChaosResist"] = 0.0

        b.skills.add(
            group(
                "Flicker Strike",
                gem("Flicker Strike"),
                bareSupport("Rakiata's Flow"),
                bareSupport("Concentrated Area"),
            )
        )
        b.skills.add(
            group(
                "Tempest Flurry",
                gem("Tempest Flurry"),
                bareSupport("Martial Tempo"),
                bareSupport("Heft"),
            )
        )
        b.skills.add(group("Herald of Thunder", gem("Herald of Thunder")))
        b.skills.add(group("Wind Dancer", gem("Wind Dancer")))
        // 단련 — poe2db 정신력 젬 목록에 없던 젬 (이름으로 알아본다)
        b.skills.add(group("Discipline", bareSupport("Discipline").copy(skillId = "DisciplinePlayer")))
        b.mainSocketGroup = 1
        b.skills[0].mainActiveSkill = 1
        b.skills[1].mainActiveSkill = 1

        b.fullDpsSkills.add(PobFullDpsSkill("Flicker Strike", 3_000_000.0))
        b.fullDpsSkills.add(PobFullDpsSkill("Tempest Flurry", 4_500_000.0))
        b.fullDps = 7_500_000.0
        b.stats["TotalDPS"] = 7_500_000.0
        return b
    }

    // ------------------------------------------------------------- 보조 젬 가리기

    @Test
    fun supportGemIsRecognizedWithoutSkillId() {
        assertTrue("이름만으로 보조 젬을 알아보지 못했다", PobCalculator.isSupport(bareSupport("Concentrated Area")))
        assertTrue(
            "gemId로 보조 젬을 알아보지 못했다",
            PobCalculator.isSupport(PobGem(name = "무언가", skillId = "", gemId = "Metadata/Items/Gem/SupportGemConcentratedEffect")),
        )
        assertFalse("주력기를 보조 젬으로 봤다", PobCalculator.isSupport(gem("Tempest Flurry")))
        assertFalse("정신력 젬을 보조 젬으로 봤다", PobCalculator.isSupport(gem("Wind Dancer")))
        println("[TEST] 보조 젬 이름 목록 ${SupportGems.size}종")
        assertTrue("보조 젬 이름 목록이 비었다", SupportGems.size > 300)
    }

    @Test
    fun supportAndSpiritGemsNeverFillTheSkillChips() {
        val options = BattleSetup.skillOptions(monkBuild())
        val names = options.map { it.gem }
        println("[TEST] 스킬 칸: $names")
        assertTrue("주력기가 스킬 칸에 없다: $names", names.contains("Tempest Flurry"))
        assertFalse("보조 젬이 스킬 칸에 올라왔다: $names", names.contains("Concentrated Area"))
        assertFalse("정신력 젬이 스킬 칸에 올라왔다: $names", names.contains("Wind Dancer"))
        assertFalse("정신력 젬이 스킬 칸에 올라왔다: $names", names.contains("Discipline"))
        assertFalse("전령이 스킬 칸에 올라왔다: $names", names.contains("Herald of Thunder"))
        assertEquals("주력기가 맨 앞이 아니다: $names", "Tempest Flurry", names.first())
    }

    // ------------------------------------------------------------- 자동 주력 스킬

    @Test
    fun autoMainDoesNotLetTheMovementSkillMonopolize() {
        val build = monkBuild()
        val main = BattleSetup.autoMain(build)
        assertNotNull("주력 스킬을 고르지 못했다", main)
        println("[TEST] 자동 주력 = ${main!!.first.name}")
        assertEquals("이동기(점멸 타격)가 주력으로 잡혔다", "Tempest Flurry", main.first.name)

        val stats = BattleSetup.statsFor(build, spiritCatalog = catalog)
        assertNotNull(stats)
        println("[TEST] 전투 주력 = ${stats!!.skillName} · DPS ${stats.dps}")
        assertEquals("전투가 폭풍의 질풍으로 시작하지 않는다", "Tempest Flurry", stats.skillName)
        assertTrue("주력 DPS가 비었다", stats.dps > 0.0)
    }

    // ------------------------------------------------------------- 스킬별 DPS 기록

    @Test
    fun clearTallyKeepsTheMainSkillAndKeepsHeraldsInCheck() {
        val stats = BattleSetup.statsFor(monkBuild(), spiritCatalog = catalog)
        assertNotNull(stats)
        val world = BattleWorld(stats!!, BattleMode.MAP, 25, false, null)
        repeat((12.0 * 60).toInt()) { world.update(1.0 / 60.0) }
        val rows = world.skillTally()
        val names = rows.map { it.name }
        println("[TEST] 기록: ${rows.joinToString { "${it.name} ${"%.0f".format(it.damage)} (${"%.0f".format(it.share)}%)" }}")
        assertTrue("주력기가 기록에 없다: $names", names.any { it.contains("Tempest") })
        assertFalse("보조 젬이 공격 스킬처럼 기록됐다: $names", names.any { it.contains("집중") || it.contains("Concentrated") })
        assertFalse("정신력 젬이 피해 기록에 올라왔다: $names", names.any { it.contains("Wind Dancer") })
        val main = rows.firstOrNull { it.name.contains("Tempest") }!!
        val herald = rows.firstOrNull { it.name.contains("전령") }
        println("[TEST] 주력 ${"%.0f".format(main.damage)} · 전령 ${herald?.damage ?: 0.0}")
        assertTrue("주력기 피해가 0이다", main.damage > 0.0)
        if (herald != null) {
            assertTrue(
                "전령이 주력기보다 큰 비중을 차지했다 (전령 ${herald.damage}, 주력 ${main.damage})",
                herald.damage <= main.damage * 1.5,
            )
        }
        assertEquals("가장 큰 피해를 낸 스킬이 주력기가 아니다", main.name, rows.first().name)
    }

    // ------------------------------------------------------------- 정신력 젬

    @Test
    fun disciplineIsCountedAsASpiritBuff() {
        val plan = SpiritBuffs.plan(monkBuild(), catalog)
        val discipline = plan.specs.firstOrNull { it.gem.en == "Discipline" }
        println("[TEST] 정신력 버프: ${plan.specs.joinToString { "${it.name}(${it.spirit.toInt()})${if (it.active) "" else " 꺼짐"}" }}")
        assertNotNull("단련이 정신력 버프로 잡히지 않았다", discipline)
        assertTrue("단련이 켜지지 않았다", discipline!!.active)
        // 단련은 총 최대 에너지 보호막을 올린다.
        val base = BattleSetup.statsFor(monkBuild().also { it.skills.removeAll { g -> g.label == "Discipline" } })
        val withBuff = BattleSetup.statsFor(monkBuild(), spiritCatalog = catalog)
        assertNotNull(base)
        assertNotNull(withBuff)
        println("[TEST] ES ${base!!.es} → ${withBuff!!.es}")
        assertTrue("단련의 보호막 증가가 수치에 안 들어갔다", withBuff.es > base.es + 100.0)
    }

    @Test
    fun spiritGemsCanBeTurnedOffDuringBattle() {
        val build = monkBuild()
        val on = SpiritBuffs.plan(build, catalog)
        val off = SpiritBuffs.plan(build, catalog, disabled = setOf("Wind Dancer"))
        println("[TEST] 켜짐 ${on.reserved} · 바람의 무희 끔 ${off.reserved}")
        assertTrue("바람의 무희가 꺼지지 않았다", off.specs.first { it.gem.en == "Wind Dancer" }.active.not())
        assertTrue("끈 젬의 정신력이 예산에서 빠지지 않았다", off.reserved < on.reserved)
        val statsOn = BattleSetup.statsFor(build, spiritCatalog = catalog)
        val statsOff = BattleSetup.statsFor(build, spiritCatalog = catalog, disabledSpirit = setOf("Wind Dancer"))
        assertNotNull(statsOn)
        assertNotNull(statsOff)
        assertTrue(
            "끈 뒤에도 회피 증가가 그대로다",
            statsOff!!.spiritBuffs.mods.evasionPct < statsOn!!.spiritBuffs.mods.evasionPct,
        )
    }

    // ------------------------------------------------------------- 트리 화면 자료

    @Test
    fun treeSceneSurvivesUnknownNodesAndMissingClass() {
        val tree = TreeDict.parse(File("src/main/assets/poe2_tree_kr.json").readText())
        val scene = TreeScene.of(tree, -1, 0)
        println("[TEST] 직업 없음: 노드 ${scene.size} · 시작 ${scene.classStart}")
        assertEquals(tree.nodes.size, scene.size)
        assertEquals(-1, scene.classStart)
        val alloc = TreeAllocation(scene.geometry, scene.ascendancy, scene.classStart, scene.ascendStart, 120)
        // 빌드에 적힌 노드 번호가 자료에 없어도(다른 판의 빌드) 예외 없이 넘어가야 한다.
        alloc.bind(listOf(999_999, 0, -1, 42) + tree.nodes.keys.take(5))
        val points = alloc.points()
        println("[TEST] 찍힌 노드 ${alloc.allocatedIds().size} · $points")
        assertTrue(points.allocated >= 0)
        // 12직업 × 전직 0~3을 모두 훑어 화면 자료가 깨지지 않는지 본다.
        var named = 0
        for (classIndex in tree.classes.indices) {
            for (ascend in 0..3) {
                val s = TreeScene.of(tree, classIndex, ascend)
                if (s.className.isNotBlank()) named++
                assertTrue("직업 $classIndex/$ascend 전직이 이어지지 않았다", s.ascendStart >= -1)
            }
        }
        println("[TEST] 직업 이름이 붙은 조합 $named")
        assertTrue("직업 이름이 하나도 안 붙는다", named > 0)
    }
}
