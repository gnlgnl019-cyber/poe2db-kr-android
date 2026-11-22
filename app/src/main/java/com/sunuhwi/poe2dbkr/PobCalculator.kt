package com.sunuhwi.poe2dbkr

/**
 * PoB 기준 스탯에 장착 장비·보조 젬·전투 설정 변동을 더해 다시 계산하는 간이 시뮬레이터.
 * PoB 자체 계산을 대체하지 않고, 불러온 빌드 기준 값 위에서 바뀐 부분만 추정해 더한다.
 */
object PobCalculator {

    data class Mods(
        var life: Double = 0.0,
        var mana: Double = 0.0,
        var es: Double = 0.0,
        var armourFlat: Double = 0.0,
        var armourPct: Double = 0.0,
        var evasionFlat: Double = 0.0,
        var evasionPct: Double = 0.0,
        var fireRes: Double = 0.0,
        var coldRes: Double = 0.0,
        var lightRes: Double = 0.0,
        var chaosRes: Double = 0.0,
        var dmgPct: Double = 0.0,
        var atkSpdPct: Double = 0.0,
        var castSpdPct: Double = 0.0,
        var critChancePct: Double = 0.0,
        var critMultiFlat: Double = 0.0,
        var moveSpdPct: Double = 0.0,
    ) {
        operator fun plus(o: Mods) = Mods(
            life + o.life, mana + o.mana, es + o.es,
            armourFlat + o.armourFlat, armourPct + o.armourPct,
            evasionFlat + o.evasionFlat, evasionPct + o.evasionPct,
            fireRes + o.fireRes, coldRes + o.coldRes, lightRes + o.lightRes, chaosRes + o.chaosRes,
            dmgPct + o.dmgPct, atkSpdPct + o.atkSpdPct, castSpdPct + o.castSpdPct,
            critChancePct + o.critChancePct, critMultiFlat + o.critMultiFlat, moveSpdPct + o.moveSpdPct,
        )

        operator fun minus(o: Mods) = plus(Mods(
            -o.life, -o.mana, -o.es, -o.armourFlat, -o.armourPct, -o.evasionFlat, -o.evasionPct,
            -o.fireRes, -o.coldRes, -o.lightRes, -o.chaosRes, -o.dmgPct, -o.atkSpdPct,
            -o.castSpdPct, -o.critChancePct, -o.critMultiFlat, -o.moveSpdPct,
        ))
    }

    data class Config(
        var enduranceCharges: Int = 0,
        var frenzyCharges: Int = 0,
        var powerCharges: Int = 0,
        var onslaught: Boolean = false,
        var arcaneSurge: Boolean = false,
        var enemy: Enemy = Enemy.NORMAL,
        val ailments: MutableSet<String> = mutableSetOf(),
    ) {
        enum class Enemy(val label: String, val ehpDiv: Double) {
            NORMAL("일반", 1.0), RARE("희귀", 1.3), PINNACLE("피나클 보스", 1.8)
        }
    }

    data class StatLine(val label: String, val base: Double, val now: Double, val unit: String = "", val better: Int = 1)

    data class Result(
        val mods: Mods,
        val dpsBase: Double,
        val dpsNow: Double,
        val apsBase: Double,
        val apsNow: Double,
        val critBase: Double,
        val critNow: Double,
        val critMultiBase: Double,
        val critMultiNow: Double,
        val lifeBase: Double,
        val lifeNow: Double,
        val esBase: Double,
        val esNow: Double,
        val armourBase: Double,
        val armourNow: Double,
        val physRedBase: Double,
        val physRedNow: Double,
        val evasionBase: Double,
        val evasionNow: Double,
        val evasionPctBase: Double,
        val evasionPctNow: Double,
        val resists: List<Triple<String, Double, Double>>, // 이름, 기준, 현재
        val ehpBase: Double,
        val ehpNow: Double,
        val factor: Double = 1.0,
    )

    /** 옵션 한 줄을 스탯 증감으로 환산한다. 영문/한글 주요 패턴만 인식한다. */
    fun parseLine(rawLine: String): Mods {
        val line = rawLine.trim().replace("\u00A0", " ")
        if (line.isEmpty()) return Mods()
        val lower = line.lowercase()
        val m = Mods()
        val num = Regex("(\\d+(?:\\.\\d+)?)").find(line)?.groupValues?.get(1)?.toDoubleOrNull() ?: return m

        fun has(vararg keys: String) = keys.any { lower.contains(it) }

        val plusPercent = line.contains("% 증가") || lower.contains("% increased") ||
            line.contains("% 추가") || lower.contains("% more")
        val plusFlat = line.startsWith("+") || line.contains(" +") || line.contains("+${num.toInt()}")

        when {
            // 생명/마나/보호막
            has("최대 생명력", "생명력 최대치", "to maximum life") -> if (plusFlat) m.life += num
            has("마나 최대치", "최대 마나", "to maximum mana") -> if (plusFlat) m.mana += num
            has("에너지 보호막 최대치", "최대 에너지 보호막", "maximum energy shield") -> if (plusFlat) m.es += num

            // 방어도/회피
            has("방어도 +", "+to armour", "+ to armour") || (has("방어도") && plusFlat && !line.contains("%")) -> m.armourFlat += num
            has("방어도") && line.contains("%") -> m.armourPct += num
            has("회피 +", "to evasion") && !line.contains("%") -> m.evasionFlat += num
            has("회피") && line.contains("%") -> m.evasionPct += num

            // 저항
            has("화염 저항", "fire resistance") -> if (line.contains("+") || plusFlat) m.fireRes += num
            has("냉기 저항", "cold resistance") -> if (line.contains("+") || plusFlat) m.coldRes += num
            has("번개 저항", "lightning resistance") -> if (line.contains("+") || plusFlat) m.lightRes += num
            has("카오스 저항", "chaos resistance") -> if (line.contains("+") || plusFlat) m.chaosRes += num

            // 피해/공속/치명타
            has("공격 속도", "attack speed") -> if (line.contains("%")) m.atkSpdPct += num
            has("시전 속도", "cast speed") -> if (line.contains("%")) m.castSpdPct += num
            has("이동 속도", "movement speed") -> if (line.contains("%")) m.moveSpdPct += num
            has("치명타 피해", "critical multiplier", "crit multiplier") -> if (plusFlat) m.critMultiFlat += num
            has("치명타", "critical") -> if (line.contains("%")) m.critChancePct += num
            has("피해") && line.contains("%") -> m.dmgPct += num
            has("damage") && line.contains("%") -> m.dmgPct += num
        }
        return m
    }

    fun sumLines(lines: List<String>): Mods = lines.fold(Mods()) { acc, line -> acc + parseLine(line) }

    /** 보조 젬 효과표 (주요 젬만, 간이 수치) */
    val SUPPORT_GEMS = mapOf(
        "heft" to (Mods(dmgPct = 30.0)),
        "momentum" to (Mods(dmgPct = 15.0)),
        "martial tempo" to (Mods(atkSpdPct = 20.0)),
        "arcane tempo" to (Mods(castSpdPct = 20.0)),
        "conciseness" to (Mods(castSpdPct = 25.0)),
        "elemental focus" to (Mods(dmgPct = 40.0)),
        "ruthless" to (Mods(dmgPct = 25.0)),
        "heavy impact" to (Mods(dmgPct = 25.0)),
        "prismatic armament" to (Mods(dmgPct = 10.0)),
        "corruption" to (Mods(dmgPct = 10.0)),
        "incision" to (Mods(dmgPct = 15.0)),
        "corrosion" to (Mods(dmgPct = 12.0)),
        "lifespring" to Mods(),
        "drain ailments" to Mods(),
    )

    /** 보조 젬인지 (PoB 2는 skillId가 Support로 시작한다) */
    fun isSupport(gem: PobGem): Boolean {
        val id = gem.skillId.lowercase()
        if (id.startsWith("support")) return true
        if (gem.name.lowercase().trim() in SUPPORT_GEMS) return true
        return false
    }

    private fun stat(build: PobBuild, vararg keys: String): Double {
        for (key in keys) {
            build.stats[key]?.let { return it }
        }
        return 0.0
    }

    fun compute(
        build: PobBuild,
        overrides: Map<String, PobItem>,
        flasksEnabled: Set<String> = emptySet(),
        config: Config = Config(),
    ): Result {
        // 1. 장비 변동: 슬롯별 (새 아이템 - 원본 아이템) 옵션 차이
        var gear = Mods()
        for (slot in Pob.SLOTS.keys) {
            val original = Pob.itemForSlot(build, slot)
            val replaced = overrides[slot]
            if (replaced != null) {
                gear += sumLines(replaced.lines)
                if (original != null) gear -= sumLines(original.lines)
            }
        }

        // 2. 메인 스킬 그룹 보조 젬 + 젬 레벨 변동
        val mainGroup = Pob.mainGroup(build, 0)
        var support = Mods()
        if (mainGroup != null) {
            for (gem in mainGroup.gems) {
                if (!gem.enabled) continue
                val key = gem.name.lowercase().trim()
                if (SUPPORT_GEMS.containsKey(key)) {
                    val effect = SUPPORT_GEMS[key]!!
                    support += effect
                }
            }
        }

        // 3. 전투 설정
        var cfg = Mods()
        var ehpMult = config.enemy.ehpDiv
        if (config.enduranceCharges > 0) {
            cfg.fireRes += 3.0 * config.enduranceCharges
            cfg.coldRes += 3.0 * config.enduranceCharges
            cfg.lightRes += 3.0 * config.enduranceCharges
        }
        if (config.frenzyCharges > 0) cfg.atkSpdPct += 4.0 * config.frenzyCharges
        if (config.powerCharges > 0) cfg.critChancePct += 15.0 * config.powerCharges
        if (config.onslaught) {
            cfg.atkSpdPct += 20.0
            cfg.moveSpdPct += 20.0
        }
        if (config.arcaneSurge) cfg.castSpdPct += 20.0
        if (config.ailments.contains("감전")) ehpMult *= 1.2
        if (config.ailments.contains("동결")) ehpMult *= 1.15
        if (config.ailments.contains("출혈")) ehpMult *= 1.10
        if (config.ailments.contains("중독")) ehpMult *= 1.08
        if (config.ailments.contains("위축")) ehpMult *= 1.05
        if (config.ailments.contains("노출")) {
            cfg.fireRes -= 15; cfg.coldRes -= 15; cfg.lightRes -= 15
        }
        if (config.ailments.contains("저주")) {
            cfg.fireRes -= 10; cfg.coldRes -= 10; cfg.lightRes -= 10; cfg.chaosRes -= 10
        }

        val total = gear + support + cfg

        // 4. 스탯 재계산
        val speedStat = stat(build, "AttacksPerSecond", "CastsPerSecond", "Speed", "HitSpeed")
        val averageHit = stat(build, "AverageHit", "AverageDamage")
        val dpsFromHit = if (averageHit > 0 && speedStat > 0) averageHit * speedStat else 0.0
        val dpsBase = stat(build, "TotalDPS", "CombinedDPS", "WithIgniteDPS").takeIf { it > 0 }
            ?: dpsFromHit
        val apsBase = speedStat.takeIf { it > 0 } ?: 1.0
        val speedPct = total.atkSpdPct + total.castSpdPct
        val dmgFactor = (1 + total.dmgPct / 100) * (1 + speedPct / 100) *
            (1 + total.critChancePct / 100) * (1 + total.critMultiFlat / 100)
        val dpsNow = dpsBase * dmgFactor
        val apsNow = apsBase * (1 + speedPct / 100)

        val critBase = stat(build, "CritChance").takeIf { it > 0 } ?: 5.0
        val critNow = (critBase * (1 + total.critChancePct / 100)).coerceAtMost(100.0)
        // PoB 2는 배율을 3.5처럼 배수로 저장한다. 350처럼 퍼센트로 저장한 빌드도 받아 준다.
        val rawMulti = stat(build, "CritMultiplier")
        val critMultiBase = if (rawMulti > 0) {
            if (rawMulti <= 10.0) rawMulti * 100.0 else rawMulti
        } else {
            150.0
        }
        val critMultiNow = critMultiBase + total.critMultiFlat

        val lifeBase = stat(build, "Life")
        val lifeNow = (lifeBase + total.life).coerceAtLeast(1.0)
        val esBase = stat(build, "EnergyShield")
        val esNow = (esBase + total.es).coerceAtLeast(0.0)

        val armourBase = stat(build, "Armour")
        val armourNow = ((armourBase + total.armourFlat) * (1 + total.armourPct / 100)).coerceAtLeast(0.0)
        val evasionBase = stat(build, "Evasion")
        val evasionNow = ((evasionBase + total.evasionFlat) * (1 + total.evasionPct / 100)).coerceAtLeast(0.0)

        fun physRed(armour: Double) = (armour / (armour + 12_000) * 100).coerceIn(0.0, 90.0)

        fun resBase(name: String) = stat(build, name)

        val fireB = resBase("FireResist"); val coldB = resBase("ColdResist")
        val lightB = resBase("LightningResist"); val chaosB = resBase("ChaosResist")
        val fireN = fireB + total.fireRes; val coldN = coldB + total.coldRes
        val lightN = lightB + total.lightRes; val chaosN = chaosB + total.chaosRes

        fun avgRes(f: Double, c: Double, l: Double) = (f + c + l) / 3
        fun ehp(pool: Double, armour: Double, f: Double, c: Double, l: Double): Double {
            val red = 1 - physRed(armour) / 100
            val el = 1 - avgRes(f, c, l).coerceIn(-100.0, 100.0) / 100
            val elClamped = el.coerceAtLeast(0.05)
            return pool / (red * elClamped) / ehpMult
        }

        val ehpPoB = stat(build, "TotalEHP")
        val poolBase = (lifeBase + esBase).coerceAtLeast(1.0)
        val poolNow = (lifeNow + esNow).coerceAtLeast(1.0)
        val ehpBase = if (ehpPoB > 0) ehpPoB else ehp(poolBase, armourBase, fireB, coldB, lightB)
        val ehpNow = if (ehpPoB > 0) ehpPoB * (poolNow / poolBase) else ehp(poolNow, armourNow, fireN, coldN, lightN)

        return Result(
            mods = total,
            dpsBase = dpsBase, dpsNow = dpsNow,
            apsBase = apsBase, apsNow = apsNow,
            critBase = critBase, critNow = critNow,
            critMultiBase = critMultiBase, critMultiNow = critMultiNow,
            lifeBase = lifeBase, lifeNow = lifeNow,
            esBase = esBase, esNow = esNow,
            armourBase = armourBase, armourNow = armourNow,
            physRedBase = physRed(armourBase), physRedNow = physRed(armourNow),
            evasionBase = evasionBase, evasionNow = evasionNow,
            evasionPctBase = (evasionBase / (evasionBase + 1640) * 100).coerceIn(0.0, 95.0),
            evasionPctNow = (evasionNow / (evasionNow + 1640) * 100).coerceIn(0.0, 95.0),
            resists = listOf(
                Triple("화염", fireB, fireN),
                Triple("냉기", coldB, coldN),
                Triple("번개", lightB, lightN),
                Triple("카오스", chaosB, chaosN),
            ),
            ehpBase = ehpBase,
            ehpNow = ehpNow,
            factor = dmgFactor,
        )
    }
}
