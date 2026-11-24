package com.sunuhwi.poe2dbkr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 정신력 젬·메타 젬 전수 자료와 전투 화면의 켜고 끄기가 실제 시뮬레이션에 반영되는지 본다.
 *
 * - poe2db에서 받아 둔 자료에 정신력을 점유하는 스킬이 빠짐없이 들어 있는지
 * - 정신력 젬(지속 버프)과 메타 젬(발동 장치)이 갈라져 있는지
 * - 젬을 끄면 버프 수치·발동·전령 피해가 실제로 빠지는지
 * - 전투에서 쓸 스킬을 직접 고르면 그 스킬로 싸우는지
 */
class SpiritMetaToggleTest {

    private val catalog: SpiritCatalog by lazy {
        val file = File("src/main/assets/poe2_spirit_kr.json")
        assertTrue("정신력 젬 자료가 없다: ${file.absolutePath}", file.isFile)
        SpiritCatalog.parse(file.readText())
    }

    private fun gem(name: String, level: Int = 20) = PobGem(
        name = name,
        level = level,
        enabled = true,
        skillId = name.replace(" ", "") + "Player",
    )

    private fun group(vararg gems: PobGem) =
        PobSkillGroup(label = gems.first().name, gems = gems.toMutableList())

    /** 정신력 210, 저항·방어 수치가 있는 근접 빌드 */
    private fun build(): PobBuild {
        val build = PobBuild(className = "Monk", ascendClassName = "Invoker", level = 90)
        build.stats["Life"] = 200_000.0
        build.stats["EnergyShield"] = 20_000.0
        build.stats["Armour"] = 12_000.0
        build.stats["Evasion"] = 18_000.0
        build.stats["FireResist"] = 60.0
        build.stats["ColdResist"] = 60.0
        build.stats["LightningResist"] = 60.0
        build.stats["ChaosResist"] = -10.0
        build.stats["TotalDPS"] = 40_000.0
        build.stats["FullDPS"] = 80_000.0
        build.stats["AttacksPerSecond"] = 3.2
        build.stats["CritChance"] = 38.0
        build.stats["CritMultiplier"] = 3.4
        build.stats["Spirit"] = 400.0
        build.fullDps = 80_000.0
        build.skills.add(group(gem("Tempest Flurry"), gem("Martial Tempo")))
        build.skills.add(group(gem("Arc"), gem("Lightning Arrow")))
        build.skills.add(group(gem("Cast on Critical"), gem("Comet")))
        build.skills.add(group(gem("Cast on Shock"), gem("Ball Lightning")))
        build.skills.add(group(gem("Discipline")))
        build.skills.add(group(gem("Ghost Dance")))
        build.skills.add(group(gem("Herald of Thunder")))
        build.fullDpsSkills.add(PobFullDpsSkill("Comet", 20_000.0))
        build.fullDpsSkills.add(PobFullDpsSkill("Arc", 14_000.0))
        return build
    }

    // ------------------------------------------------------------------ 자료

    @Test
    fun catalogCoversEveryReservingGem() {
        println("[TEST] 정신력 점유 스킬 ${catalog.gems.size}종 · 메타 ${catalog.gems.count { it.isMeta }}종")
        assertTrue("정신력 점유 스킬이 너무 적다: ${catalog.gems.size}", catalog.gems.size >= 140)
        assertTrue("메타 젬이 너무 적다", catalog.gems.count { it.isMeta } >= 20)
        val expected = mapOf(
            "Cast on Critical" to 100.0,
            "Cast on Shock" to 60.0,
            "Cast on Freeze" to 60.0,
            "Cast on Ignite" to 60.0,
            "Cast on Elemental Ailment" to 100.0,
            "Cast on Dodge" to 100.0,
            "Blasphemy" to 60.0,
            "Archmage" to 100.0,
            "Trinity" to 100.0,
            "Wind Dancer" to 30.0,
            "Ghost Dance" to 30.0,
            "Herald of Ice" to 30.0,
            "Herald of Thunder" to 30.0,
            "Herald of Ash" to 30.0,
        )
        expected.forEach { (name, spirit) ->
            val gem = catalog.find(name)
            assertNotNull("$name 을(를) 자료에서 찾지 못했다", gem)
            assertEquals("$name 점유량", spirit, gem!!.spirit, 0.01)
        }
        // 사용자가 짚은 정신력 젬 — 자료에 점유량이 없어도 버프로 잡혀야 한다.
        val discipline = catalog.find("Discipline")
        assertNotNull("단련이 없다", discipline)
        assertFalse("단련은 메타 젬이 아니다", discipline!!.isMeta)
        assertEquals("단련 보호막", 328.0, SpiritBuffs.modsOf(discipline).esFlat, 0.01)
        // 정신력 젬과 메타 젬이 갈라져 있어야 한다.
        assertTrue(catalog.find("Cast on Critical")!!.isMeta)
        assertFalse(catalog.find("Wind Dancer")!!.isMeta)
        assertFalse(catalog.find("Archmage")!!.isMeta)
    }

    @Test
    fun everyGemCarriesItsOwnEffectLines() {
        val empty = catalog.gems.filter { it.lines.isEmpty() && it.text.isBlank() }
        println("[TEST] 효과 문장이 빈 젬 ${empty.size}종 ${empty.map { it.en }}")
        assertTrue("효과 문장이 너무 많이 비었다", empty.size <= 6)
        catalog.gems.filter { it.isMeta }.forEach { meta ->
            println("[TEST] 메타 ${meta.displayName} ${meta.spiritLabel} — ${meta.energy.take(50)}")
        }
    }

    @Test
    fun skillListDropsReservingGems() {
        // 스킬 선택 칸에는 액티브 스킬만 올라야 한다 (정신력·메타·전령 젬 제외).
        catalog.parseRegistered()
        val options = BattleSetup.skillOptions(build(), dict = KrDict.EMPTY)
        val names = options.map { it.gem }
        println("[TEST] 스킬 칸: $names")
        assertTrue("주력기가 없다", names.any { it == "Comet" || it == "Arc" || it == "Tempest Flurry" || it == "Lightning Arrow" })
        assertTrue("정신력 젬이 스킬 칸에 올라왔다", names.none { it == "Discipline" || it == "Ghost Dance" })
        assertTrue("메타 젬이 스킬 칸에 올라왔다", names.none { it == "Cast on Critical" || it == "Cast on Shock" })
        assertTrue("전령이 스킬 칸에 올라왔다", names.none { it.startsWith("Herald of") })
    }

    // ------------------------------------------------------------- 켜고 끄기

    @Test
    fun turningSpiritGemOffRemovesItsNumbers() {
        catalog.parseRegistered()
        val on = BattleSetup.statsFor(build(), triggerMode = TriggerMode.OFF, spiritCatalog = catalog)!!
        val off = BattleSetup.statsFor(
            build(),
            triggerMode = TriggerMode.OFF,
            spiritCatalog = catalog,
            disabledSpirit = setOf("Discipline", "Ghost Dance"),
        )!!
        println("[TEST] 정신력 젬 켬 → 보호막 ${on.es} · 회피 ${on.evade} / 끔 → 보호막 ${off.es} · 회피 ${off.evade}")
        assertTrue("단련을 켰는데 보호막이 늘지 않았다", on.es > 20_000.0)
        assertTrue("단련을 껐는데 보호막이 그대로다", off.es < on.es)
        assertTrue("끈 젬이 목록에 남아 있다", off.spiritBuffs.active.isEmpty())
        assertEquals("정신력 점유가 남아 있다", 0.0, off.spiritBuffs.reserved, 0.01)
    }

    @Test
    fun spiritAndMetaGemsShareTheSpiritBudget() {
        catalog.parseRegistered()
        val plan = SpiritBuffs.plan(build(), catalog)
        println("[TEST] 정신력 ${plan.reserved} / ${plan.spirit} · 버프 ${plan.specs.map { it.name }} · 메타 ${plan.meta.map { it.name }}")
        assertTrue("메타 젬이 따로 잡히지 않았다", plan.meta.isNotEmpty())
        assertTrue("정신력 젬이 잡히지 않았다", plan.specs.isNotEmpty())
        assertTrue("메타 젬이 정신력을 점유하지 않았다", plan.reserved > plan.specs.filter { it.active }.sumOf { it.spirit })
        assertTrue("정신력 예산을 넘겼다", plan.reserved <= plan.spirit + 0.01)
    }

    @Test
    fun turningMetaGemOffStopsItFiring() {
        catalog.parseRegistered()
        val seed = 20261124L
        fun run(disabled: Set<TriggerKind>): BattleResult {
            val stats = BattleSetup.statsFor(
                build(),
                triggerMode = TriggerMode.AUTO,
                spiritCatalog = catalog,
                disabledTriggers = disabled,
            )!!
            val world = BattleWorld(stats, BattleMode.MAP, 40, true, null, seed)
            repeat(60 * 60) { world.update(1.0 / 60.0) }
            return world.result()
        }
        val on = run(emptySet())
        val off = run(setOf(TriggerKind.CAST_ON_CRIT, TriggerKind.CAST_ON_SHOCK))
        val onCrit = on.triggers.firstOrNull { it.label.contains("치명타") }
        val offCrit = off.triggers.firstOrNull { it.label.contains("치명타") }
        println("[TEST] 메타 켬 ${on.triggers.map { it.line }}")
        println("[TEST] 메타 끔 ${off.triggers.map { it.line }}")
        assertNotNull(onCrit)
        assertTrue("켰는데 치명타 시 시전이 한 번도 안 터졌다", onCrit!!.casts > 0)
        assertTrue("껐는데 치명타 시 시전이 남아 있다", offCrit == null || offCrit.casts == 0)
        assertTrue("메타 피해가 줄지 않았다", off.triggerDamage < on.triggerDamage)
    }

    @Test
    fun turningHeraldOffRemovesItsDamage() {
        catalog.parseRegistered()
        fun run(off: Set<String>): BattleWorld {
            val stats = BattleSetup.statsFor(
                build(),
                triggerMode = TriggerMode.AUTO,
                spiritCatalog = catalog,
                disabledSpirit = off,
            )!!
            val world = BattleWorld(stats, BattleMode.MAP, 20, false, null, 21L)
            repeat(40) { world.update(1.0 / 60.0) }
            // 감전된 적이 쓰러지면 천둥의 전령이 터진다 (같은 조건으로 두 번 재 본다).
            world.testEnemy(0)?.let { world.testKill(it, shocked = true) }
            repeat(30) { world.update(1.0 / 60.0) }
            return world
        }
        val probe = BattleSetup.statsFor(build(), triggerMode = TriggerMode.AUTO, spiritCatalog = catalog)!!
        println("[TEST] 전령 계획 ${probe.heralds.specs.map { "${it.name}:${it.active}" }} · 정신력 ${probe.heralds.reserved}/${probe.heralds.spirit}")
        assertTrue("정신력이 남는데 전령이 꺼졌다", probe.heralds.specs.any { it.active })
        val on = run(emptySet())
        val off = run(setOf("Herald of Thunder"))
        val onReport = on.heraldReports().firstOrNull { it.kind == HeraldKind.THUNDER }
        println("[TEST] 전령 켬 ${onReport?.line} · 전체 전령 피해 ${on.heraldDamage}")
        println("[TEST] 전령 끔 ${off.heraldReports().firstOrNull { it.kind == HeraldKind.THUNDER }?.line} · 전체 전령 피해 ${off.heraldDamage}")
        assertNotNull("켰는데 전령이 터지지 않았다", onReport)
        assertTrue("켰는데 전령 피해가 없다", onReport!!.damage > 0.0)
        assertTrue("껐는데 전령 피해가 남았다", off.heraldDamage < on.heraldDamage)
        assertEquals("껐는데도 전령이 돌았다", 0, off.heraldProcsTotal)
    }

    @Test
    fun pickingSkillChangesWhatTheBattleUses() {
        catalog.parseRegistered()
        val auto = BattleSetup.statsFor(build(), triggerMode = TriggerMode.OFF, spiritCatalog = catalog)!!
        val picked = BattleSetup.statsFor(
            build(),
            triggerMode = TriggerMode.OFF,
            spiritCatalog = catalog,
            skillOverride = "Lightning Arrow",
        )!!
        println("[TEST] 자동 → ${auto.skillName} · 직접 선택 → ${picked.skillName}")
        assertEquals("고른 스킬로 싸우지 않는다", "Lightning Arrow", picked.skillName)
        assertTrue("자동 선택이 고른 스킬과 같다", auto.skillName != picked.skillName)
    }
}

/** 이름으로 등록된 정신력·메타 젬 목록을 다시 한 번 채운다 (테스트마다 실행 순서가 달라도 되게) */
private fun SpiritCatalog.parseRegistered() {
    SpiritGems.register(gems.flatMap { listOf(it.en, it.ko) })
    SpiritGems.registerMeta(gems.filter { it.isMeta }.flatMap { listOf(it.en, it.ko) })
}
