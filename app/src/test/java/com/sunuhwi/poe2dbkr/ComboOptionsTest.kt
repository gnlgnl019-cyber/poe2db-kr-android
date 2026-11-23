package com.sunuhwi.poe2dbkr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 콤보 설정 목록 — 빌드에 든 액티브·발동 스킬이 하나도 빠지지 않는지 확인한다.
 *
 * 예전에는 켜진 그룹만 보고 12개에서 잘라, 그룹이 18개인 빌드에서는 주력기(전기불꽃)가
 * 목록 밖으로 밀려 콤보 설정에 보이지 않았다. 지금은 보조 젬만 빼고 모두 보여 준다.
 */
class ComboOptionsTest {

    private fun gem(name: String, skillId: String = ""): PobGem =
        PobGem(name = name, level = 20, enabled = true, skillId = skillId)

    private fun group(
        vararg gems: String,
        enabled: Boolean = true,
        source: String = "",
        slot: String = "",
    ): PobSkillGroup = PobSkillGroup(
        label = gems.firstOrNull().orEmpty(),
        enabled = enabled,
        source = source,
        slot = slot,
        mainActiveSkill = 1,
    ).also { g ->
        gems.forEach { name ->
            val id = if (name.contains("Support") || name in SUPPORTS) {
                "Support${name.replace(" ", "").replace("'", "")}"
            } else {
                "${name.replace(" ", "")}Player"
            }
            g.gems.add(gem(name, id))
        }
    }

    private fun build(vararg groups: PobSkillGroup): PobBuild = PobBuild(
        className = "Sorceress",
        ascendClassName = "Stormweaver",
        level = 90,
        skills = groups.toMutableList(),
    ).also { it.stats.putAll(TestBuilds.stats("TotalDPS" to 900_000.0)) }

    @Test
    fun longBuildKeepsTheMainSkillInTheList() {
        // 주력기(전기불꽃)를 19번째 그룹에 두고, 앞에 액티브를 잔뜩 넣은 빌드
        val builders = (1..18).map { i ->
            group("Buff Skill $i", enabled = i != 18)
        }
        val build = build(*builders.toTypedArray(), group("Spark", "Living Lightning II"))
        val options = BattleCombos.options(build)
        val names = options.map { it.gem.lowercase() }
        println("[TEST] 옵션=${options.size}개: ${options.map { it.gem }}")
        assertTrue("12개에서 잘렸다: ${options.size}", options.size > 12)
        assertTrue("주력기 전기불꽃이 목록에 없다", names.any { it.contains("spark") })
        assertEquals("같은 스킬이 두 번 들어갔다", options.size, names.toSet().size)
    }

    @Test
    fun disabledGroupsAndGrantedSkillsAreStillListed() {
        val build = build(
            group("Spark"),
            // 다른 스킬 세트에서 꺼 둔 그룹
            group("Frost Bomb", enabled = false),
            // 패시브 트리·아이템이 부여한 스킬
            group("Hollow Form", "Whirling Assault", source = "Tree:1739"),
            group("Eternal Rage", source = "Item:15:Kraken Rosary", slot = "Amulet"),
        )
        val options = BattleCombos.options(build)
        val gems = options.map { it.gem.lowercase() }
        println("[TEST] 부여 스킬 목록: ${options.map { it.gem to it.note }}")
        assertTrue("꺼 둔 그룹의 스킬이 빠졌다", gems.any { it.contains("frost bomb") })
        assertTrue("패시브가 부여한 스킬이 빠졌다", gems.any { it.contains("whirling assault") })
        assertTrue("아이템이 부여한 스킬이 빠졌다", gems.any { it.contains("eternal rage") })
        assertEquals("패시브 부여 표시가 없다", "패시브 부여", options.first { it.gem.contains("Whirling") }.note)
        assertEquals("아이템 부여 표시가 없다", "아이템 부여", options.first { it.gem.contains("Eternal") }.note)
    }

    @Test
    fun skillGrantedOnlyByItemTextAppears() {
        val build = build(group("Spark"))
        val item = PobItem(id = 15, name = "Kraken Rosary", rarity = "Unique", baseType = "Absent Amulet")
        item.lines.add("Grants Skill: Level 18 Eternal Rage")
        build.items[15] = item
        val options = BattleCombos.options(build)
        println("[TEST] 아이템 설명 부여: ${options.map { it.gem }}")
        assertTrue("아이템 설명에만 있는 부여 스킬이 빠졌다", options.any { it.gem.contains("Eternal Rage") })
    }

    @Test
    fun supportGemsAndDuplicatesAreLeftOut() {
        val build = build(
            group("Spark", "Rapid Attacks II", "Execute III"),
            group("Spark", "Lightning Penetration"), // 같은 스킬이 다른 그룹에 또 있다
        )
        val options = BattleCombos.options(build)
        println("[TEST] 보조 젬 제외: ${options.map { it.gem }}")
        assertTrue("보조 젬이 목록에 들어갔다", options.none { it.gem.contains("Support") || it.gem == "Rapid Attacks II" })
        assertEquals("같은 스킬이 두 번 들어갔다", 1, options.count { it.gem.equals("Spark", true) })
        assertFalse("목록이 비었다", options.isEmpty())
    }

    @Test
    fun realMonkBuildListsEveryEquippedActiveSkill() {
        val file = File("src/test/resources/pob_real_build.txt")
        assertTrue("실제 빌드 파일이 없다", file.isFile)
        val build = Pob.decode(file.readText())
        val options = BattleCombos.options(build)
        val listed = options.map { it.gem.trim().lowercase() }.toSet()
        val expected = build.skills
            .filter { it.enabled }
            .flatMap { g -> g.gems.filter { it.enabled } }
            .filterNot { PobCalculator.isSupport(it) }
            .map { it.name.trim().lowercase() }
            .toSet()
        println("[TEST] 실제 빌드 액티브=${expected.size}개 · 콤보 목록=${options.size}개")
        val missing = expected - listed
        println("[TEST] 빠진 스킬=$missing")
        assertTrue("콤보 설정에 빠진 스킬이 있다: $missing", missing.isEmpty())
        assertTrue(
            "보조 젬이 콤보 목록에 들어갔다",
            options.none { it.gem.lowercase().contains("support") },
        )
    }

    private companion object {
        val SUPPORTS = setOf(
            "Prolonged Duration II", "Rapid Attacks II", "Spell Cascade",
            "Lightning Penetration", "Execute III", "Living Lightning II",
        )
    }
}
