package com.sunuhwi.poe2dbkr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * PoE2 메타젬 전수 인식과 동시 구동 확인.
 * 빌드에 여러 메타젬이 있으면 첫 번째만 쓰지 않고, 각자의 조건·에너지·쿨다운으로 모두 발동해야 한다.
 */
class MetaGemTest {

    private val recognition = listOf(
        "Cast on Critical" to TriggerKind.CAST_ON_CRIT,
        "Cast on Critical Strike Support" to TriggerKind.CAST_ON_CRIT,
        "치명타 시 시전" to TriggerKind.CAST_ON_CRIT,
        "Cast on Shock" to TriggerKind.CAST_ON_SHOCK,
        "감전 시 시전" to TriggerKind.CAST_ON_SHOCK,
        "Cast on Freeze" to TriggerKind.CAST_ON_FREEZE,
        "빙결 시 시전" to TriggerKind.CAST_ON_FREEZE,
        "Cast on Ignite" to TriggerKind.CAST_ON_IGNITE,
        "점화 시 시전" to TriggerKind.CAST_ON_IGNITE,
        "Cast on Elemental Ailment" to TriggerKind.CAST_ON_ELEMENTAL_AILMENT,
        "원소 상태이상 시 시전" to TriggerKind.CAST_ON_ELEMENTAL_AILMENT,
        "Elemental Invocation" to TriggerKind.ELEMENTAL_INVOCATION,
        "원소 기원" to TriggerKind.ELEMENTAL_INVOCATION,
        "Cast on Minion Death" to TriggerKind.CAST_ON_MINION_DEATH,
        "소환수 사망 시 시전" to TriggerKind.CAST_ON_MINION_DEATH,
        "Cast when Damage Taken" to TriggerKind.CAST_WHEN_DAMAGE_TAKEN,
        "피격 시 시전" to TriggerKind.CAST_WHEN_DAMAGE_TAKEN,
        "Cast when Stunned" to TriggerKind.CAST_WHEN_STUNNED,
        "Cast on Dodge" to TriggerKind.CAST_ON_DODGE,
        "회피 시 시전" to TriggerKind.CAST_ON_DODGE,
        "Cast on Melee Kill" to TriggerKind.CAST_ON_MELEE_KILL,
        "Herald of Thunder" to TriggerKind.HERALD_OF_THUNDER,
        "천둥의 전령" to TriggerKind.HERALD_OF_THUNDER,
        "Herald of Ice" to TriggerKind.HERALD_OF_ICE,
        "얼음의 전령" to TriggerKind.HERALD_OF_ICE,
        "Herald of Ash" to TriggerKind.HERALD_OF_ASH,
        "재의 전령" to TriggerKind.HERALD_OF_ASH,
        "Herald of Plague" to TriggerKind.HERALD_OF_PLAGUE,
        "역병의 전령" to TriggerKind.HERALD_OF_PLAGUE,
        "Blasphemy" to TriggerKind.BLASPHEMY,
        "신성모독" to TriggerKind.BLASPHEMY,
    )

    @Test
    fun everyMetaGemNameIsRecognized() {
        recognition.forEach { (name, expected) ->
            val actual = TriggerKind.ofName(name)
            println("[TEST] $name -> ${actual?.name}")
            assertEquals("$name 인식 실패", expected, actual)
        }
    }

    @Test
    fun skillIdsAreRecognized() {
        val cases = listOf(
            "MetaCastOnCritPlayer" to TriggerKind.CAST_ON_CRIT,
            "MetaCastOnShockPlayer" to TriggerKind.CAST_ON_SHOCK,
            "CastOnFreezePlayer" to TriggerKind.CAST_ON_FREEZE,
            "CastOnIgnitePlayer" to TriggerKind.CAST_ON_IGNITE,
            "CastOnMinionDeathPlayer" to TriggerKind.CAST_ON_MINION_DEATH,
            "HeraldOfIcePlayer" to TriggerKind.HERALD_OF_ICE,
            "HeraldOfThunderPlayer" to TriggerKind.HERALD_OF_THUNDER,
            "SupportBlasphemyPlayer" to TriggerKind.BLASPHEMY,
            "CastWhenDamageTakenPlayer" to TriggerKind.CAST_WHEN_DAMAGE_TAKEN,
        )
        cases.forEach { (id, expected) ->
            assertEquals("$id 인식 실패", expected, TriggerKind.of(PobGem(name = "", skillId = id)))
        }
    }

    @Test
    fun nonMetaGemsAreNotTriggers() {
        listOf(
            "Lightning Arrow", "Martial Tempo", "Comet", "Flammability", "Molten Shell", "Arc",
            "번개 화살", "관통 보조",
        ).forEach { name ->
            assertEquals("$name 은 메타젬이 아니다", null, TriggerKind.ofName(name))
        }
    }

    private fun gem(name: String, id: String = "") = PobGem(name = name, skillId = id, enabled = true)

    private fun group(vararg gems: PobGem) =
        PobSkillGroup(label = gems.first().name, gems = gems.toMutableList())

    /** 조건마다 메타젬이 하나씩 들어간 빌드 */
    private fun multiGemBuild(): PobBuild {
        val build = PobBuild(className = "Ranger", ascendClassName = "Deadeye", level = 90)
        build.stats["Life"] = 200_000.0
        build.stats["EnergyShield"] = 20_000.0
        build.stats["Armour"] = 12000.0
        build.stats["Evasion"] = 18000.0
        build.stats["FireResist"] = 76.0
        build.stats["ColdResist"] = 75.0
        build.stats["LightningResist"] = 75.0
        build.stats["ChaosResist"] = -10.0
        build.stats["TotalDPS"] = 40_000.0
        build.stats["FullDPS"] = 80_000.0
        build.stats["AttacksPerSecond"] = 3.2
        build.stats["CritChance"] = 38.0
        build.stats["CritMultiplier"] = 3.4
        build.fullDps = 80_000.0
        build.skills.add(group(gem("Lightning Arrow", "LightningArrowPlayer"), gem("Martial Tempo", "SupportMartialTempo")))
        build.skills.add(group(gem("Cast on Critical", "MetaCastOnCritPlayer"), gem("Comet", "CometPlayer")))
        build.skills.add(group(gem("Cast on Shock", "MetaCastOnShockPlayer"), gem("Arc", "ArcPlayer")))
        build.skills.add(group(gem("Cast on Freeze", "MetaCastOnFreezePlayer"), gem("Frost Bomb", "FrostBombPlayer")))
        build.skills.add(group(gem("Cast on Ignite", "MetaCastOnIgnitePlayer"), gem("Fireball", "FireballPlayer")))
        build.skills.add(group(gem("Herald of Thunder", "HeraldOfThunderPlayer")))
        build.skills.add(group(gem("Herald of Ice", "HeraldOfIcePlayer")))
        build.skills.add(group(gem("Cast when Damage Taken", "CastWhenDamageTakenPlayer"), gem("Molten Shell", "MoltenShellPlayer")))
        build.skills.add(group(gem("Cast on Dodge", "CastOnDodgePlayer"), gem("Blink", "BlinkPlayer")))
        build.skills.add(group(gem("Blasphemy", "BlasphemyPlayer"), gem("Flammability", "FlammabilityPlayer")))
        build.skills.add(group(gem("Cast on Minion Death", "CastOnMinionDeathPlayer"), gem("Summon Skeleton", "SummonSkeletonsPlayer")))
        build.fullDpsSkills.add(PobFullDpsSkill("Comet", 20_000.0))
        build.fullDpsSkills.add(PobFullDpsSkill("Arc", 14_000.0))
        build.fullDpsSkills.add(PobFullDpsSkill("Fireball", 10_000.0))
        return build
    }

    @Test
    fun allMetaGemsAreExtractedNotJustTheFirst() {
        val specs = PobTriggers.extract(multiGemBuild(), 40_000.0)
        println("[TEST] specs=" + specs.map { it.kind.name })
        val kinds = specs.map { it.kind }
        assertTrue(kinds.contains(TriggerKind.CAST_ON_CRIT))
        assertTrue(kinds.contains(TriggerKind.CAST_ON_SHOCK))
        assertTrue(kinds.contains(TriggerKind.CAST_ON_FREEZE))
        assertTrue(kinds.contains(TriggerKind.CAST_ON_IGNITE))
        assertTrue(kinds.contains(TriggerKind.HERALD_OF_THUNDER))
        assertTrue(kinds.contains(TriggerKind.HERALD_OF_ICE))
        assertTrue(kinds.contains(TriggerKind.CAST_WHEN_DAMAGE_TAKEN))
        assertTrue(kinds.contains(TriggerKind.CAST_ON_DODGE))
        assertTrue(kinds.contains(TriggerKind.BLASPHEMY))
        assertEquals("메타젬 10개가 모두 잡혀야 한다", 10, specs.size)
    }

    @Test
    fun everyMetaGemFiresDuringBattle() {
        val build = multiGemBuild()
        val stats = BattleSetup.statsFor(build, triggerMode = TriggerMode.AUTO)
        assertNotNull(stats)
        stats!!
        // 처치·피격·회피가 계속 생기도록 증폭 100마리 맵에서 오래 싸운다.
        val world = BattleWorld(stats, BattleMode.MAP, 100, true, null)
        repeat(60 * 120) { world.update(1.0 / 60.0) }
        val result = world.result()
        println("[TEST] state=${world.state} elapsed=${world.elapsed}")
        result.triggers.forEach { println("[TEST] ${it.line} (추정=${it.estimated})") }
        assertTrue("결과에 메타젬 목록이 없다", result.triggers.size >= 10)
        // 소환수 사망은 소환수 빌드에서만 생기므로 별도 테스트에서 확인한다.
        val silent = result.triggers.filter { it.casts == 0 && !it.label.contains("소환수") }
        println("[TEST] 한 번도 안 터진 메타젬=${silent.map { it.label }}")
        assertTrue("발동하지 않은 메타젬: ${silent.map { it.label }}", silent.isEmpty())
        assertTrue("메타젬 피해 합계가 0", result.triggerDamage > 0)
        assertTrue("메타젬별 피해가 하나도 기록되지 않았다", result.triggers.any { it.damage > 0 })
    }

    @Test
    fun minionDeathMetaGemRunsInMinionBuild() {
        val build = multiGemBuild()
        val stats = BattleSetup.statsFor(build, triggerMode = TriggerMode.AUTO)!!
        val minionStats = stats.copy(fx = SkillEffectType.MINION)
        val world = BattleWorld(minionStats, BattleMode.MAP, 25, false, null)
        repeat(60 * 45) { world.update(1.0 / 60.0) }
        val report = world.result().triggers.firstOrNull { it.label.contains("소환수") }
        println("[TEST] 소환수 사망 시 시전 -> ${report?.line}")
        assertNotNull(report)
        assertTrue("소환수 사망 트리거가 발동하지 않았다", report!!.casts > 0)
    }

    @Test
    fun elementalAilmentGemAcceptsAnyAilment() {
        assertEquals(
            setOf(TriggerCondition.SHOCK, TriggerCondition.FREEZE, TriggerCondition.IGNITE),
            TriggerKind.CAST_ON_ELEMENTAL_AILMENT.accepts,
        )
        assertEquals(setOf(TriggerCondition.SHOCK), TriggerKind.CAST_ON_SHOCK.accepts)
    }

    @Test
    fun triggerHudListsEveryGem() {
        val stats = BattleSetup.statsFor(multiGemBuild(), triggerMode = TriggerMode.AUTO)!!
        val world = BattleWorld(stats, BattleMode.MAP, 25, false, null)
        repeat(60 * 12) { world.update(1.0 / 60.0) }
        val hud = world.triggerHud()
        println("[TEST] hud=$hud")
        assertTrue(hud.contains("CoC"))
        assertTrue(hud.contains("천둥"))
        assertTrue(hud.contains("저주"))
    }
}
