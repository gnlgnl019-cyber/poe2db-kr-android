package com.sunuhwi.poe2dbkr

/** 테스트용 최소 빌드 만들기 — 젬 그룹과 PlayerStat만 채운다. */
object TestBuilds {

    fun stats(vararg pairs: Pair<String, Double>): MutableMap<String, Double> {
        val map = linkedMapOf(
            "Life" to 2_400.0,
            "EnergyShield" to 520.0,
            "Mana" to 600.0,
            "Armour" to 3_200.0,
            "Evasion" to 2_400.0,
            "FireResist" to 70.0,
            "ColdResist" to 70.0,
            "LightningResist" to 70.0,
            "ChaosResist" to 0.0,
            "AverageHit" to 1_200.0,
            "Speed" to 1.6,
            "CritChance" to 20.0,
            "CritMultiplier" to 250.0,
            "TotalEHP" to 18_000.0,
            "Spirit" to 300.0,
            "SpiritUnreserved" to 300.0,
        )
        pairs.forEach { (key, value) -> map[key] = value }
        return map
    }

    fun build(
        className: String = "Witch",
        ascend: String = "Lich",
        groups: List<List<String>>,
        stats: MutableMap<String, Double> = stats(),
        mainGroup: Int = 1,
    ): PobBuild {
        val build = PobBuild(className = className, ascendClassName = ascend, level = 90)
        build.stats.putAll(stats)
        build.mainSocketGroup = mainGroup
        groups.forEachIndexed { index, gems ->
            val group = PobSkillGroup(
                label = gems.firstOrNull().orEmpty(),
                enabled = true,
                isMain = index + 1 == mainGroup,
                mainActiveSkill = 1,
            )
            gems.forEach { name ->
                val support = name.contains("Support") || name in setOf(
                    "Prolonged Duration II", "Rapid Attacks II", "Spell Cascade", "Splinter Totem II",
                )
                group.gems.add(
                    PobGem(
                        name = name,
                        level = 20,
                        enabled = true,
                        skillId = if (support) "Support${name.replace(" ", "")}" else "${name.replace(" ", "")}Player",
                    )
                )
            }
            build.skills.add(group)
        }
        return build
    }

    /** 해골 전사·저격수·성직자 + 길들인 야수 */
    fun minionBuild(): PobBuild = build(
        groups = listOf(
            listOf("Skeletal Warrior", "Minion Damage", "Rapid Attacks II"),
            listOf("Skeletal Sniper", "Minion Damage"),
            listOf("Skeletal Cleric"),
            listOf("Tame Beast", "Minion Damage"),
        ),
        stats = stats("AverageHit" to 900.0, "Speed" to 1.4, "TotalDPS" to 1_800.0),
    )

    /** 도이드리의 파멸 — 저주 + 마녀두꺼비 + 암울한 결과 */
    fun doedreBuild(): PobBuild {
        val b = build(
            className = "Witch",
            ascend = "Infernalist",
            groups = listOf(
                listOf("Spell Totem", "Despair", "Doedre's Undoing", "Spell Cascade"),
            ),
            stats = stats(
                "AverageHit" to 2_802.0,
                "Speed" to 1.75,
                "CritChance" to 29.106,
                "CritMultiplier" to 3.87,
                "TotalDPS" to Double.POSITIVE_INFINITY,
                "HitSpeed" to Double.POSITIVE_INFINITY,
            ),
        )
        b.nonFiniteStats.add("TotalDPS")
        b.nonFiniteStats.add("HitSpeed")
        return b
    }
}
