package com.sunuhwi.poe2dbkr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 정신력 점유 버프 — poe2db에서 받아 둔 젬 목록을 읽고, PoB 빌드에서 찾아
 * 정신력 예산 안에서 켜며, 그 수치가 전투 수치와 전투 연출에 실제로 들어가는지 본다.
 */
class SpiritBuffTest {

    private val catalog: SpiritCatalog by lazy {
        val file = File("src/main/assets/poe2_spirit_kr.json")
        assertTrue("정신력 젬 자료가 없다: ${file.absolutePath}", file.isFile)
        SpiritCatalog.parse(file.readText())
    }

    private fun gem(name: String, level: Int = 20, support: Boolean = false) = PobGem(
        name = name,
        level = level,
        enabled = true,
        skillId = if (support) "Support${name.replace(" ", "")}Player" else name.replace(" ", "") + "Player",
    )

    private fun group(label: String, vararg gems: PobGem) = PobSkillGroup(label = label).apply {
        gems.forEach { this.gems.add(it) }
    }

    private fun build(spirit: Double, vararg groups: PobSkillGroup): PobBuild {
        val b = PobBuild(className = "Monk")
        groups.forEach { b.skills.add(it) }
        if (spirit > 0.0) b.stats["Spirit"] = spirit
        return b
    }

    // ------------------------------------------------------------- 자료

    @Test
    fun catalogReadsAllSpiritGems() {
        println("[TEST] 정신력 젬 ${catalog.gems.size}종")
        assertTrue("정신력 젬을 거의 읽지 못했다: ${catalog.gems.size}", catalog.gems.size >= 50)
        val wind = catalog.find("Wind Dancer")
        assertNotNull("바람의 무희를 찾지 못했다", wind)
        assertEquals("바람의 무희", wind!!.ko)
        assertEquals(30.0, wind.spirit, 0.001)
        assertTrue("효과 문장이 비었다", wind.lines.isNotEmpty())
        // 화면에 올리는 문장에는 엔진용 영문 열쇠줄이 남지 않아야 한다.
        catalog.gems.forEach { gem ->
            gem.lines.forEach { line ->
                assertTrue("영문 열쇠줄이 남았다: $line", SpiritCatalog.visible(line))
            }
        }
    }

    @Test
    fun koreanNameAlsoFindsGem() {
        assertEquals("Ghost Dance", catalog.find("혼의 춤")?.en)
        assertEquals("Elemental Conflux", catalog.find("원소 융합")?.en)
        assertEquals(null, catalog.find("Fireball"))
    }

    // ------------------------------------------------------------- 예산

    @Test
    fun spiritBudgetTurnsLaterBuffsOff() {
        val b = build(
            60.0,
            group("Wind Dancer", gem("Wind Dancer")),
            group("Ghost Dance", gem("Ghost Dance")),
            group("Elemental Conflux", gem("Elemental Conflux")),
        )
        val plan = SpiritBuffs.plan(b, catalog)
        println("[TEST] ${plan.summary} / 꺼짐=${plan.inactive.map { it.name }}")
        assertEquals(3, plan.specs.size)
        assertEquals(2, plan.active.size)
        assertEquals(1, plan.inactive.size)
        assertEquals("원소 융합", plan.inactive.first().name)
        assertEquals(60.0, plan.reserved, 0.001)
    }

    @Test
    fun heraldBudgetIsSharedWithSpiritBuffs() {
        val b = build(
            120.0,
            group("Wind Dancer", gem("Wind Dancer")),
            group("Elemental Conflux", gem("Elemental Conflux")),
            group("Herald of Ice", gem("Herald of Ice")),
        )
        val buffs = SpiritBuffs.plan(b, catalog)
        assertEquals(2, buffs.active.size)
        assertEquals(90.0, buffs.reserved, 0.001)
        // 정신력 120 중 버프가 90을 쓰고 남은 30으로 전령 하나가 켜진다.
        val heralds = Heralds.plan(b, KrDict.EMPTY, buffs.reserved)
        assertEquals(1, heralds.active.size)
        // 버프가 예산을 다 쓰면 전령은 꺼진다.
        val tight = build(
            90.0,
            group("Wind Dancer", gem("Wind Dancer")),
            group("Elemental Conflux", gem("Elemental Conflux")),
            group("Herald of Ice", gem("Herald of Ice")),
        )
        val tightBuffs = SpiritBuffs.plan(tight, catalog)
        assertEquals(0, Heralds.plan(tight, KrDict.EMPTY, tightBuffs.reserved).active.size)
    }

    // ------------------------------------------------------------- 수치 반영

    @Test
    fun modsAreFoldedIntoBattleStats() {
        val b = build(
            120.0,
            group("Wind Dancer", gem("Wind Dancer")),
            group("Defiance Banner", gem("Defiance Banner")),
            group("Sacrifice", gem("Sacrifice")),
        )
        val plain = build(0.0)
        val withBuffs = BattleSetup.statsFor(b, spiritCatalog = catalog)
        assertNotNull(withBuffs)
        val base = BattleSetup.statsFor(plain)
        assertNotNull(base)
        println("[TEST] 생명력 ${base!!.life} → ${withBuffs!!.life} · ES ${base.es} → ${withBuffs.es} · 회피 ${base.evade} → ${withBuffs.evade}")
        assertTrue("정신력 버프가 하나도 잡히지 않았다", withBuffs.spiritBuffs.specs.isNotEmpty())
        assertEquals(3, withBuffs.spiritBuffs.active.size)
        // 희생 — 생명력이 오르고 보호막 증가가 수치로 잡힌다.
        assertTrue("생명력이 오르지 않았다", withBuffs.life > base.life)
        assertTrue("보호막 증가가 잡히지 않았다", withBuffs.spiritBuffs.mods.esPct > 0.0)
        // 바람의 무희·저항의 깃발 — 회피 증가가 수치로 잡힌다.
        assertTrue("회피 증가가 잡히지 않았다", withBuffs.spiritBuffs.mods.evasionPct > 0.0)
    }

    @Test
    fun battleRunsSpiritEffects() {
        val b = build(
            120.0,
            group("Wind Dancer", gem("Wind Dancer")),
            group("Ghost Dance", gem("Ghost Dance")),
            group("Briarpatch", gem("Briarpatch")),
        )
        val stats = BattleSetup.statsFor(b, spiritCatalog = catalog)!!
        val world = BattleWorld(stats, BattleMode.MAP, 40, false, null)
        repeat(1500) { world.update(1.0 / 60.0) }
        println("[TEST] spiritHud=${world.spiritHud()} · 피해=${world.spiritDamage} · 처치=${world.kills}")
        assertTrue("정신력 버프가 HUD에 없다", world.spiritHud().isNotBlank())
        assertTrue("정신력 버프가 피해를 내지 않았다", world.spiritDamage > 0.0)
        assertTrue("버프별 기록이 비었다", world.spiritReports().isNotEmpty())
        assertFalse("꺼진 버프가 켜진 것으로 나온다", world.spiritOn("Elemental Conflux"))
    }

    @Test
    fun itemGrantedBuffIsListed() {
        val b = PobBuild(className = "Monk")
        b.stats["Spirit"] = 100.0
        val granted = PobSkillGroup(label = "Eternal Rage").apply {
            source = "Item:15:Kraken Rosary"
            slot = "Amulet"
            gems.add(gem("Eternal Rage"))
        }
        b.skills.add(granted)
        val plan = SpiritBuffs.plan(b, catalog)
        assertTrue("장비 부여 정신력 젬을 찾지 못했다", plan.specs.any { it.gem.en == "Eternal Rage" })
    }
}
