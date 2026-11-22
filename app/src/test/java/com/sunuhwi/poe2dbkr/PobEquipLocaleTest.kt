package com.sunuhwi.poe2dbkr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 불러온 PoB 장비의 옵션 줄이 전부 한글로 바뀌는지 확인한다.
 * 실제 빌드에서 나온 희귀·마법 장비 줄과 PoB 표기({태그}, (augmented), 속성 머리말)를 그대로 쓴다.
 */
class PobEquipLocaleTest {

    private val dict: KrDict by lazy {
        val file = File("src/main/assets/poe2_kr_i18n.json")
        assertTrue("한글 사전 파일이 없다: ${file.absolutePath}", file.isFile)
        KrDict.parse(file.readText()).also { parsed ->
            parsed.passiveNames = mapOf("kite runner" to "카이트 러너", "gem enthusiast" to "보석 애호가")
        }
    }

    private fun assertKorean(label: String, line: String) {
        val ko = dict.modLine(line)
        println("[TEST] $label: $line → $ko")
        assertTrue("한글로 바뀌지 않았다: $line", ko.isNotBlank())
        assertTrue("한글이 없다: $line → $ko", ko.any { it.code > 0x7F })
    }

    @Test
    fun rareWeaponModsBecomeKorean() {
        listOf(
            "179% increased Physical Damage",
            "Adds 39 to 66 Physical Damage",
            "Adds 12 to 234 Lightning Damage",
            "+5% to Critical Hit Chance",
            "+5 to Level of all Projectile Skills",
            "+3.7% to Critical Hit Chance",
            "Bow Attacks fire an additional Arrow",
            "30% increased Critical Hit Chance for Attacks",
            "37% increased Critical Damage Bonus for Attack Damage",
            "Adds 3 to 61 Lightning damage to Attacks",
            "+175 to Accuracy Rating",
            "48% increased Damage with Bow Skills",
        ).forEach { assertKorean("무기", it) }
    }

    @Test
    fun armourJewelryAndFlaskModsBecomeKorean() {
        listOf(
            "+124 to maximum Life",
            "+22% to Cold Resistance",
            "8% increased Attack Speed",
            "30% increased Movement Speed",
            "14% increased Mana Regeneration Rate",
            "+60 to Stun Threshold",
            "+48 to maximum Energy Shield",
            "+36% increased Evasion and Energy Shield",
            "15% increased Charm Charges gained",
            "28% increased Global Defences",
            "100% increased effect of Socketed Items",
            "21% increased Effect of Notable Passive Skills in Radius",
            "Passives in Radius of Vaal Pact can be Allocated",
            "without being connected to your tree",
            "Notable Passive Skills in Radius also grant 2% increased Dexterity",
            "Small Passive Skills in Radius also grant 2% increased Lightning Damage",
        ).forEach { assertKorean("방어구·장신구", it) }
    }

    @Test
    fun flaskAndCharmLinesBecomeKorean() {
        listOf(
            "Grants Onslaught during effect",
            "Grants a Frenzy Charge on use",
            "Grants up to your maximum Rage on use",
            "Instant Recovery",
            "101% increased Charges per use",
            "25% reduced Charges per use",
            "Gains 0.25 Charges per Second",
            "Flasks gain 0.17 charges per Second",
            "80% reduced Amount Recovered",
            "Used when you are affected by a Slow",
            "Used when you become Frozen",
            "Used when you take Fire damage from a Hit",
            "Used when you Kill a Rare or Unique Enemy",
            "Energy Shield Recharge starts on use",
            "Excess Life Recovery added as Guard for 10 seconds",
            "This Flask cannot be Used but applies its Effect constantly",
            "3.7 Life Regeneration per second",
            "Recover Life equal to 17% of Mana Flask's Recovery Amount when used",
        ).forEach { assertKorean("플라스크·호신부", it) }
    }

    @Test
    fun pobTagsAndPropertiesAreHandled() {
        assertEquals("물리 피해: 214-390 (증가)", dict.modLine("Physical Damage: 214-390 (augmented)"))
        assertEquals("품질: +20", dict.modLine("Quality: +20"))
        assertEquals("방어도: 312 (증가)", dict.modLine("Armour: 312 (augmented)"))
        assertEquals("요구 사항: 레벨 64", dict.modLine("Requires: Level 64"))
        assertEquals("치명타 명중 확률: 9.5%", dict.modLine("Critical Strike Chance: 9.5%"))
        assertEquals("생명력 최대치 +80 (증가)", dict.modLine("+80 to maximum Life (augmented)"))
        assertEquals("모든 공격 스킬 레벨 +2 (제작)", dict.modLine("{crafted}+2 to Level of all Attack Skills"))
        assertEquals("물리 피해 38~54 추가 (분할)", dict.modLine("{fractured}Adds 38 to 54 Physical Damage"))
        assertEquals("물리 피해 106% 증가 (훼손)", dict.modLine("{desecrated}106% increased Physical Damage"))
        assertEquals("공격 속도 5% 증가 (룬)", dict.modLine("{enchant}{rune}5% increased Attack Speed"))
        assertEquals("결속: 생명력 최대치 +20 (룬)", dict.modLine("{enchant}{rune}Bonded: +20 to maximum Life"))
        assertEquals("카이트 러너 할당 (인챈트)", dict.modLine("{enchant}Allocates Kite Runner"))
        assertEquals("범위 표기 없이", "번개 저항 +30%", dict.modLine("+30% to Lightning Resistance"))
        assertEquals("번개 저항 -30%", dict.modLine("-30% to Lightning Resistance"))
    }

    @Test
    fun everyLineOfLoadedRareItemsIsKorean() {
        val build = Pob.decode(Pob.sampleCode())
        val rare = build.items.values.filter { it.rarity == "Rare" || it.rarity == "Magic" }
        println("[TEST] 희귀/마법 장비 ${rare.size}개")
        assertTrue(rare.isNotEmpty())
        rare.forEach { item ->
            val lines = PobLocale.lines(dict, item)
            println("[TEST] ${PobLocale.title(dict, item)} → $lines")
            assertTrue("옵션이 비었다: ${item.name}", lines.isNotEmpty())
            lines.forEach { line ->
                assertTrue("영문이 남았다: ${item.name} · $line", line.any { it.code > 0x7F })
            }
        }
    }
}
