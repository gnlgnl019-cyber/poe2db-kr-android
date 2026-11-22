package com.sunuhwi.poe2dbkr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 실제 PoB 2(XML) 빌드 형식 — 쇠뇌(Crossbow) 장착 + "치명타 시 시전(Cast on Critical)" 연결.
 * 아이템/스킬/트리거 파싱과 전투 중 자동 발동을 JVM에서 그대로 확인한다.
 */
class PobTriggerTest {

    private val crossbowBuild = """
        <?xml version="1.0" encoding="UTF-8"?>
        <PathOfBuilding2>
        <Build ascendClassName="Deadeye" viewMode="CALCS" level="88" mainSocketGroup="1" className="Ranger" characterLevelAutoMode="false">
        <PlayerStat value="4380" stat="Life"/>
        <PlayerStat value="640" stat="EnergyShield"/>
        <PlayerStat value="9200" stat="Armour"/>
        <PlayerStat value="15400" stat="Evasion"/>
        <PlayerStat value="76" stat="FireResist"/>
        <PlayerStat value="75" stat="ColdResist"/>
        <PlayerStat value="75" stat="LightningResist"/>
        <PlayerStat value="-18" stat="ChaosResist"/>
        <PlayerStat value="2800000" stat="TotalDPS"/>
        <PlayerStat value="3600000" stat="FullDPS"/>
        <PlayerStat value="40" stat="CritChance"/>
        <PlayerStat value="3.5" stat="CritMultiplier"/>
        <PlayerStat value="2.1" stat="Speed"/>
        <PlayerStat value="61000" stat="TotalEHP"/>
        </Build>
        <Skills activeSkillSet="1">
        <SkillSet id="1" title="Default">
        <Skill enabled="true" includeInFullDPS="true" mainActiveSkill="1" label="">
        <Gem nameSpec="Lightning Arrow" skillId="LightningArrowPlayer" level="20" quality="20" enabled="true"/>
        <Gem nameSpec="Martial Tempo" skillId="SupportMartialTempoPlayer" level="20" quality="0" enabled="true"/>
        <Gem nameSpec="Primal Armament" skillId="SupportPrimalArmamentPlayer" level="20" quality="0" enabled="true"/>
        </Skill>
        <Skill enabled="true" includeInFullDPS="true" mainActiveSkill="nil" label="">
        <Gem nameSpec="Cast on Critical" skillId="MetaCastOnCritPlayer" level="20" quality="20" enabled="true"/>
        <Gem nameSpec="Comet" skillId="CometPlayer" level="20" quality="0" enabled="true"/>
        <Gem nameSpec="Pinpoint Critical" skillId="SupportPinpointCriticalPlayer" level="1" quality="0" enabled="true"/>
        <Gem nameSpec="Magnified Effect" skillId="SupportMagnifiedAreaPlayer" level="1" quality="0" enabled="true"/>
        </Skill>
        <Skill enabled="true" includeInFullDPS="true" mainActiveSkill="nil" label="">
        <Gem nameSpec="Herald of Thunder" skillId="HeraldOfThunderPlayer" level="20" quality="0" enabled="true"/>
        </Skill>
        </SkillSet>
        </Skills>
        <FullDPSSkill stat="Comet" value="800000" source="Cast on Critical"/>
        <Items activeItemSet="1">
        <Item id="1">
        Rarity: RARE
        Doom String
        Rune Crossbow
        Unique ID: 1a2b3c
        Item Level: 80
        Quality: 20
        Physical Damage: 120-220
        Critical Hit Chance: 8.5%
        Attacks per Second: 1.45
        LevelReq: 60
        Implicits: 0
        +42 to maximum Life
        38% increased Projectile Damage
        24% increased Critical Hit Chance
        </Item>
        <Item id="2">
        Rarity: RARE
        Storm Ward
        Two-Stone Ring
        Item Level: 78
        Implicits: 0
        +31% to Fire Resistance
        +28% to Cold Resistance
        +48 to maximum Life
        </Item>
        <Item id="3">
        Rarity: UNIQUE
        Maligaro's Virtuosity
        Fine Bracers
        Evasion: 104
        Item Level: 81
        Quality: 20
        Implicits: 0
        67% increased Evasion Rating
        26% increased Critical Hit Chance
        </Item>
        <ItemSet id="1">
        <Slot name="Weapon 1" itemId="1" itemPbURL=""/>
        <Slot name="Weapon 2" itemId="0" itemPbURL=""/>
        <Slot name="Ring 1" itemId="2" itemPbURL=""/>
        <Slot name="Gloves" itemId="3" itemPbURL=""/>
        </ItemSet>
        </Items>
        </PathOfBuilding2>
    """.trimIndent()

    private fun parse(): PobBuild = Pob.parseXml(crossbowBuild)

    @Test
    fun crossbowItemParsesWithClassAndName() {
        val build = parse()
        val weapon = Pob.itemForSlot(build, "Weapon 1")
        println("[TEST] weapon=${weapon?.name} base=${weapon?.baseType} class=${weapon?.itemClass} visual=${weapon?.weapon}")
        assertEquals("Doom String", weapon?.name)
        assertEquals("Rune Crossbow", weapon?.baseType)
        assertEquals("Crossbow", weapon?.itemClass)
        assertEquals(WeaponVisualType.CROSSBOW, weapon?.weapon)
        assertTrue(weapon!!.lines.any { it.contains("maximum Life") })
        // PoB 메타 줄은 옵션에 섞이지 않는다
        assertTrue(weapon.lines.none { it.startsWith("Rarity:") || it.startsWith("Unique ID:") })
    }

    @Test
    fun skillGemsComeFromNameSpec() {
        val build = parse()
        val names = build.skills.map { group -> group.gems.map { it.name } }
        println("[TEST] skills=$names")
        assertEquals(3, build.skills.size)
        assertTrue(names[0].contains("Lightning Arrow"))
        assertTrue(names[1].contains("Cast on Critical"))
        assertTrue(names[1].contains("Comet"))
        assertEquals("Lightning Arrow", Pob.mainGroup(build, 0)?.gems?.first()?.name)
    }

    @Test
    fun castOnCriticalGroupIsDetected() {
        val build = parse()
        val specs = PobTriggers.extract(build, 2_800_000.0)
        println("[TEST] triggers=" + specs.map { it.kind.name + ":" + it.skillNames + "=" + it.dps })
        assertEquals(1, specs.size)
        val spec = specs.first()
        assertEquals(TriggerKind.CAST_ON_CRIT, spec.kind)
        assertEquals(listOf("Comet"), spec.skills.map { it.name })
        assertEquals(800_000.0, spec.dps, 1.0)
        assertEquals(SkillEffectType.ICE, spec.skills.first().fx)
    }

    @Test
    fun poB2StatsAndCritMultiplierAreNormalized() {
        val build = parse()
        val calc = PobCalculator.compute(build, emptyMap(), emptySet(), PobCalculator.Config())
        println("[TEST] dps=${calc.dpsNow} aps=${calc.apsNow} crit=${calc.critNow} multi=${calc.critMultiNow} life=${calc.lifeNow} ehp=${calc.ehpNow}")
        assertEquals(2_800_000.0, calc.dpsBase, 1.0)
        assertEquals(2.1, calc.apsBase, 0.01)
        assertEquals(350.0, calc.critMultiBase, 0.01)
        assertEquals(4380.0, calc.lifeNow, 0.5)
        assertEquals(61_000.0, calc.ehpBase, 1.0)
    }

    @Test
    fun triggerFiresDuringBattle() {
        val build = parse()
        val calc = PobCalculator.compute(build, emptyMap(), emptySet(), PobCalculator.Config())
        val specs = PobTriggers.extract(build, calc.dpsNow)
        val stats = BattlePlayerStats(
            className = "Ranger",
            skillName = "Lightning Arrow",
            fx = SkillEffectType.LIGHTNING,
            weapon = Pob.itemForSlot(build, "Weapon 1")!!.weapon,
            dps = calc.dpsNow,
            aps = calc.apsNow,
            critChance = calc.critNow,
            critMulti = calc.critMultiNow,
            life = calc.lifeNow,
            es = calc.esNow,
            physRed = calc.physRedNow,
            evade = 30.0,
            fireRes = 76.0,
            coldRes = 75.0,
            lightRes = 75.0,
            chaosRes = -18.0,
            ehp = calc.ehpNow,
            triggers = specs,
        )
        val world = BattleWorld(stats, BattleMode.BOSS, 0, false, BossPresets.ALL[0])
        repeat(60 * 12) { world.update(1.0 / 60.0) }
        val result = world.result()
        println("[TEST] battle casts=${result.triggerCasts} triggerDamage=${result.triggerDamage} dealt=${result.damageDealt} state=${world.state}")
        assertTrue("트리거가 한 번도 발동하지 않았습니다", result.triggerCasts > 0)
        assertTrue("트리거 피해가 0입니다", result.triggerDamage > 0.0)
        assertTrue(result.triggerText.contains("CoC"))
    }

    @Test
    fun syntheticTriggerWorksWithoutPobMetaGem() {
        val specs = PobTriggers.synthetic(500_000.0)
        assertEquals(1, specs.size)
        assertEquals(TriggerKind.CAST_ON_CRIT, specs.first().kind)
        assertEquals("Comet", specs.first().skills.first().name)
    }

    @Test
    fun legacySampleStillParsesBow() {
        val build = Pob.decode(Pob.sampleCode())
        val weapon = Pob.itemForSlot(build, "Weapon 1")
        println("[TEST] sample weapon=${weapon?.displayName} class=${weapon?.itemClass} visual=${weapon?.weapon} triggers=${PobTriggers.extract(build, 1.0).size}")
        assertEquals("Stormshroud", weapon?.name)
        assertEquals("Thunder Wolf Séance Bow", weapon?.baseType)
        assertEquals(WeaponVisualType.BOW, weapon?.weapon)
        assertTrue(PobTriggers.extract(build, 1.0).isEmpty())
        assertTrue(weapon!!.lines.any { it.contains("maximum Life") })
    }
}
