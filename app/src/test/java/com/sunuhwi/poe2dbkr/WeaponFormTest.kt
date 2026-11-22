package com.sunuhwi.poe2dbkr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 스틱맨이 든 무기와 공격 형태가 PoB 장비와 일치하는지 확인한다.
 *
 * 예전에는 "Weapon 1" 슬롯만 봐서, 무기를 쇠뇌/지팡이로 바꿔도 활 모션과 화살이 나갔다.
 */
class WeaponFormTest {

    private fun weaponLine(
        name: String,
        base: String,
        cls: String,
        slot: String = "Weapon 1",
        extra: String = "",
    ): String = """
        <?xml version="1.0" encoding="UTF-8"?>
        <PathOfBuilding>
          <Build level="80" className="Ranger" ascendClassName="Deadeye" mainSocketGroup="1"/>
          <Skills>
            <SkillGroup label="주력">
              <Gem name="Spark" skillId="Spark" level="20" enabled="true"/>
            </SkillGroup>
          </Skills>
          <Items activeItemSet="1">
            <Item id="1" rarity="RARE" name="$name">
              Rarity: RARE
              $name
              $base
              Item Class: $cls
              +80 to maximum Life
              $extra
            </Item>
            <ItemSet id="1">
              <Slot name="$slot" itemId="1"/>
            </ItemSet>
          </Items>
        </PathOfBuilding>
    """.trimIndent()

    @Test
    fun everyWeaponClassGetsItsOwnAttackForm() {
        val cases = mapOf(
            WeaponVisualType.SWORD to AttackForm.MELEE,
            WeaponVisualType.AXE to AttackForm.MELEE,
            WeaponVisualType.MACE to AttackForm.MELEE,
            WeaponVisualType.DAGGER to AttackForm.MELEE,
            WeaponVisualType.CLAW to AttackForm.MELEE,
            WeaponVisualType.FLAIL to AttackForm.MELEE,
            WeaponVisualType.QUARTERSTAFF to AttackForm.MELEE,
            WeaponVisualType.SPEAR to AttackForm.MELEE,
            WeaponVisualType.BOW to AttackForm.BOW,
            WeaponVisualType.CROSSBOW to AttackForm.CROSSBOW,
            WeaponVisualType.STAFF to AttackForm.CAST,
            WeaponVisualType.WAND to AttackForm.CAST,
            WeaponVisualType.SCEPTRE to AttackForm.CAST,
        )
        cases.forEach { (weapon, form) ->
            assertEquals("$weapon 공격 형태", form, WeaponTypes.attackForm(weapon, SkillEffectType.LIGHTNING))
        }
        // 무기가 없을 때만 스킬로 정한다.
        assertEquals(AttackForm.MELEE, WeaponTypes.attackForm(WeaponVisualType.NONE, SkillEffectType.MELEE))
        assertEquals(AttackForm.CAST, WeaponTypes.attackForm(WeaponVisualType.NONE, SkillEffectType.LIGHTNING))
    }

    @Test
    fun crossbowInWeaponOneDrivesCrossbowForm() {
        val build = Pob.parseXml(weaponLine("Stormshroud", "Rune Crossbow", "Crossbow"))
        val stats = BattleSetup.statsFor(build)
        assertNotNull(stats)
        assertEquals(WeaponVisualType.CROSSBOW, stats!!.weapon)
        assertEquals(AttackForm.CROSSBOW, stats.form)
        println("[TEST] 무기=${stats.weapon} 형태=${stats.form} 라벨=${stats.weaponLabel}")
    }

    @Test
    fun staffDrivesCastFormEvenWithLightningSkill() {
        val build = Pob.parseXml(weaponLine("Gelid Staff", "Gelid Staff", "Staff"))
        val stats = BattleSetup.statsFor(build)!!
        assertEquals(WeaponVisualType.STAFF, stats.weapon)
        assertEquals(AttackForm.CAST, stats.form)
    }

    @Test
    fun bowInSwapSlotIsStillRead() {
        val build = Pob.parseXml(weaponLine("Widowhail", "Recurve Bow", "Bow", slot = "Weapon 1 Swap"))
        val stats = BattleSetup.statsFor(build)!!
        assertEquals(WeaponVisualType.BOW, stats.weapon)
        assertEquals(AttackForm.BOW, stats.form)
    }

    @Test
    fun meleeWeaponWithSpellSkillStaysMelee() {
        val build = Pob.parseXml(weaponLine("Stormshroud", "Broadsword", "Sword"))
        val stats = BattleSetup.statsFor(build)!!
        assertEquals(WeaponVisualType.SWORD, stats.weapon)
        assertEquals(AttackForm.MELEE, stats.form)
    }

    @Test
    fun weaponFallsBackToItemListWhenSlotsAreMissing() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <PathOfBuilding>
              <Build level="80" className="Witch" ascendClassName="Infernalist"/>
              <Skills><SkillGroup label="주력"><Gem name="Fireball" level="20"/></SkillGroup></Skills>
              <Items activeItemSet="1">
                <Item id="3" rarity="RARE" name="Doom Song">
                  Rarity: RARE
                  Doom Song
                  Expert Dualstring Bow
                  Item Class: Bow
                  +80 to maximum Life
                </Item>
              </Items>
            </PathOfBuilding>
        """.trimIndent()
        val build = Pob.parseXml(xml)
        assertTrue("슬롯 정보가 없어야 한다", build.slots.isEmpty())
        val item = Pob.weaponItem(build)
        assertNotNull("아이템 목록에서 무기를 찾지 못했다", item)
        assertEquals(WeaponVisualType.BOW, item!!.weapon)
    }

    @Test
    fun poBItemTextIsShownInKorean() {
        val dict = KrDict.parse(java.io.File("src/main/assets/poe2_kr_i18n.json").readText())
        val build = Pob.parseXml(weaponLine("Stormshroud", "Rune Crossbow", "Crossbow"))
        val item = Pob.itemForSlot(build, "Weapon 1")!!
        val title = PobLocale.title(dict, item)
        val lines = PobLocale.lines(dict, item)
        println("[TEST] 제목=$title · 부제=${PobLocale.subtitle(dict, item)} · 옵션=$lines")
        assertTrue("제목이 영문 그대로다: $title", title.any { it.code > 0x7F })
        assertTrue("옵션이 한글로 안 바뀌었다: $lines", lines.any { line -> line.any { it.code > 0x7F } })
        assertTrue("아이콘이 비었다", PobLocale.icon(dict, item).isNotBlank())
    }
}
