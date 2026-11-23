package com.sunuhwi.poe2dbkr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 실제로 붙여넣은 빌드(PoB 2 포맷)가 끝까지 도는지 확인한다.
 *
 * 이 코드는 수도사(Martial Artist) 빌드로, PoB가 TotalDPS를 inf로 적어 보내는 경우다.
 * 불러오기 → 수치 계산 → 전투 시뮬레이션까지 어디서도 무한·NaN이 새어 나오면 안 된다.
 */
class RealBuildPobTest {

    private val code: String by lazy {
        File("src/test/resources/pob_real_build.txt").readText()
    }
    private val build: PobBuild by lazy { Pob.decode(code) }
    private val dict: KrDict by lazy {
        val file = File("src/main/assets/poe2_kr_i18n.json")
        if (file.isFile) KrDict.parse(file.readText()) else KrDict.EMPTY
    }

    @Test
    fun decodesAndParsesEverySection() {
        println("[TEST] class=${build.className} ascend=${build.ascendClassName} level=${build.level}")
        println("[TEST] skills=${build.skills.size} items=${build.items.size} slots=${build.slots.size} nodes=${build.treeNodes}")
        assertTrue("PoB XML을 풀지 못했다", build.rawXml.contains("<PathOfBuilding2"))
        assertEquals("Monk", build.className)
        assertEquals("Martial Artist", build.ascendClassName)
        assertEquals(90, build.level)
        assertTrue("스킬 그룹이 비었다", build.skills.size >= 10)
        assertTrue("장비가 비었다", build.items.isNotEmpty())
        assertTrue("장비 슬롯이 비었다", build.slots.isNotEmpty())
        assertTrue("패시브 노드가 비었다", build.treeNodeIds.isNotEmpty())
        val enabledGems = build.skills.flatMap { it.gems }.filter { it.enabled }
        assertTrue("장착한 젬이 비었다", enabledGems.size > 20)
        println("[TEST] enabledGems=${enabledGems.size} mainGroup=${build.mainSocketGroup}")
    }

    @Test
    fun mainSkillComesFromPobMainSocketGroup() {
        // PoB는 mainSocketGroup="6"으로 계산한다. 앱도 같은 그룹을 주력으로 봐야 한다.
        val group = Pob.mainGroup(build, 0)
        assertNotNull("주력 스킬 그룹을 찾지 못했다", group)
        println("[TEST] mainGroup=${group!!.title} gems=${group.gems.map { it.name }}")
        assertEquals("PoB 주력 그룹(6번)과 다른 그룹을 골랐다", 6, build.skills.indexOf(group) + 1)
    }

    @Test
    fun statsStayFinite() {
        val stats = BattleSetup.statsFor(build, dict = dict)
        assertNotNull("전투 수치를 만들지 못했다", stats)
        stats!!
        println("[TEST] skill=${stats.skillName} fx=${stats.fx} dps=${stats.dps} aps=${stats.aps} ehp=${stats.ehp}")
        println("[TEST] life=${stats.life} es=${stats.es} weapon=${stats.weaponLabel}")
        assertTrue("DPS가 무한·NaN이다: ${stats.dps}", stats.dps.isFinite())
        assertTrue("DPS가 0이다", stats.dps > 0)
        assertTrue("공속이 무한·NaN이다: ${stats.aps}", stats.aps.isFinite())
        assertTrue("공속이 0이다", stats.aps > 0)
        assertTrue("생명력이 무한·NaN이다", stats.life.isFinite() && stats.life > 0)
        assertTrue("보호막이 무한·NaN이다", stats.es.isFinite() && stats.es >= 0)
        assertTrue("EHP가 무한·NaN이다", stats.ehp.isFinite() && stats.ehp > 0)
        assertTrue("스킬 이름이 비었다", stats.skillName.isNotBlank())
    }

    @Test
    fun battleRunsWithRealBuild() {
        val stats = BattleSetup.statsFor(build, dict = dict)!!
        val world = BattleWorld(stats, BattleMode.MAP, 25, amp = false, boss = null)
        // 암울한 결과(두꺼비 폭발)가 주력인 빌드라 25마리를 잡는 데 시간이 걸린다.
        repeat(60 * 150) { world.update(1.0 / 60.0) }
        println("[TEST] state=${world.state} elapsed=${world.elapsed} kills=${world.kills}/${world.totalEnemies}")
        println("[TEST] damage=${world.damageDealt} taken=${world.damageTaken} life=${world.player.life}")
        assertTrue("피해량이 무한·NaN이다", world.damageDealt.isFinite())
        assertTrue("받은 피해가 무한·NaN이다", world.damageTaken.isFinite())
        assertTrue("플레이어 생명력이 무한·NaN이다", world.player.life.isFinite())
        assertTrue("전투가 끝나지 않았다", world.state != BattleWorld.State.RUNNING)
    }

    @Test
    fun skillChipsUseOnlyBuildSkills() {
        val options = BattleSetup.skillOptions(build, dict)
        println("[TEST] chips=" + options.map { "${it.name}(${it.gem})" })
        assertTrue("스킬 칩이 비었다", options.isNotEmpty())
        assertTrue("스킬 칩이 너무 많다: ${options.size}", options.size <= BattleSetup.SKILL_CHIP_LIMIT)
        val allGems = build.skills.flatMap { it.gems }.map { it.name }.toSet()
        // 도이드리의 파멸이 발동시키는 암울한 결과는 젬 이름이 아니라 보조 젬이 만드는 스킬이다.
        val triggered = if (Minions.darkConsequencesGroup(build) != null) {
            setOf(Minions.DARK_CONSEQUENCES_EN)
        } else {
            emptySet()
        }
        options.forEach { option ->
            assertTrue("빌드에 없는 스킬이 칩에 올라왔다: ${option.gem}", option.gem in allGems || option.gem in triggered)
        }
    }

    @Test
    fun pickedSkillKeepsFiniteStats() {
        val options = BattleSetup.skillOptions(build, dict)
        assertTrue(options.isNotEmpty())
        options.forEach { option ->
            val stats = BattleSetup.statsFor(build, skillOverride = option.gem, dict = dict)
            assertNotNull("${option.gem} 수치를 만들지 못했다", stats)
            stats!!
            println("[TEST] ${option.gem} -> ${stats.skillName} fx=${stats.fx} dps=${stats.dps}")
            assertTrue("${option.gem} DPS가 무한·NaN", stats.dps.isFinite())
            assertTrue("${option.gem} 공속이 무한·NaN", stats.aps.isFinite())
            assertFalse("${option.gem} 스킬 이름이 비었다", stats.skillName.isBlank())
        }
    }

    @Test
    fun savedCodeReloadsSameBuild() {
        // '코드 복사'·'최근 빌드'는 Pob.encode로 다시 저장한다. 그 코드가 다시 열려야 한다.
        val again = Pob.decode(Pob.encode(build))
        assertEquals(build.className, again.className)
        assertEquals(build.ascendClassName, again.ascendClassName)
        assertEquals(build.level, again.level)
        assertEquals(build.mainSocketGroup, again.mainSocketGroup)
        assertEquals(build.skills.size, again.skills.size)
        assertEquals(
            build.skills.flatMap { it.gems }.map { it.name },
            again.skills.flatMap { it.gems }.map { it.name },
        )
        assertEquals(build.treeNodeIds, again.treeNodeIds)
        assertEquals("주력 스킬 그룹이 바뀌었다",
            Pob.mainGroup(build, 0)!!.mainActiveSkill, Pob.mainGroup(again, 0)!!.mainActiveSkill)
        val stats = BattleSetup.statsFor(again, dict = dict)
        assertNotNull(stats)
        assertTrue("다시 연 빌드의 DPS가 무한·NaN", stats!!.dps.isFinite() && stats.dps > 0)
        println("[TEST] 다시 열기: ${again.title} · ${stats.skillName} · DPS ${stats.dps}")
    }

    @Test
    fun treeTabSummarizesRealBuild() {
        val file = File("src/main/assets/poe2_tree_kr.json")
        if (!file.isFile) return
        val tree = TreeDict.parse(file.readText())
        val summary = Poe2Tree.summarize(build, tree)
        assertNotNull("패시브 요약을 만들지 못했다", summary)
        summary!!
        println("[TEST] class=${summary.classKo} ascend=${summary.ascendKo} nodes=${summary.allocated} notables=${summary.notables.size}")
        assertEquals(build.treeNodeIds.size, summary.allocated)
        assertTrue("직업 한글이 비었다", summary.classKo.isNotBlank())
        val url = Poe2Tree.url(build, tree)
        assertTrue("트리 주소를 만들지 못했다", url.startsWith("https://poe2db.tw/kr/passive-skill-tree/"))
        assertNotNull("트리 주소를 다시 풀지 못했다", tree.decode(url))
    }

    @Test
    fun pastedCodeWithLineBreaksStillDecodes() {
        // 사용자가 붙여넣는 코드는 줄바꿈·여백이 섮이기도 한다.
        val broken = code.chunked(64).joinToString("\n  ")
        val again = Pob.decode(broken)
        assertEquals("Monk", again.className)
        assertEquals(build.skills.size, again.skills.size)
    }
}
