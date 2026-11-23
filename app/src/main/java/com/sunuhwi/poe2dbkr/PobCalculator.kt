package com.sunuhwi.poe2dbkr

/**
 * PoB 기준 스탯에 장착 장비·보조 젬·전투 설정 변동을 더해 다시 계산하는 간이 시뮬레이터.
 * PoB 자체 계산을 대체하지 않고, 불러온 빌드 기준 값 위에서 바뀐 부분만 추정해 더한다.
 */
object PobCalculator {

    /** 전투에서 쓰는 기본 보호막 재충전 지연(초). PoE2 일반 값. */
    const val ES_RECHARGE_DELAY = 2.6

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

    /** 주력 스킬 하나의 DPS (PoB 값이 없으면 추정) */
    data class SkillDps(
        val name: String,
        val dps: Double,
        val estimated: Boolean = false,
        val note: String = "",
    )

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
        /** PoB에 DPS 값이 없어 평균 타입·공속으로 추정했는지 */
        val dpsEstimated: Boolean = false,
        /** PoB가 DPS를 무한(inf)로 내보냈는지 */
        val dpsInfinite: Boolean = false,
        val factor: Double = 1.0,
        /** 전직 노드가 주는 효과 범위 증가(%) — 전투 화면 타격 범위에 쓴다 */
        val ascendAoePct: Double = 0.0,
        /** 전직 효과를 반영한 뒤의 에너지 보호막 재충전 지연(초) */
        val esRechargeDelay: Double = ES_RECHARGE_DELAY,
        /** 전직 효과로 늘어난 흡수·재생 비율(초당 생명력 %) */
        val ascendLifeRegenPct: Double = 0.0,
        val ascendLifeLeechPct: Double = 0.0,
        /** 적용한 전직 노드 수 / 전체 전직 노드 수 */
        val ascendApplied: Int = 0,
        val ascendTotal: Int = 0,
        val ascendSummary: String = "",
        /** 주력 스킬별 DPS와 그 합 */
        val skillDps: List<SkillDps> = emptyList(),
        val totalDps: Double = 0.0,
        /** 주력 스킬 이름 */
        val mainSkill: String = "",
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

    /** 보조 젬인지 (PoB 2는 skillId 어딘가에 Support가 들어간다) */
    fun isSupport(gem: PobGem): Boolean {
        val id = gem.skillId.lowercase()
        if (id.startsWith("support") || id.contains("support")) return true
        val name = gem.name.lowercase().trim()
        if (name in SUPPORT_GEMS) return true
        return name.contains("support")
    }

    /** 무한·NaN은 없는 값으로 친다 (PoB가 inf로 내보낸 빌드 대응) */
    private fun finite(value: Double?): Double? = value?.takeIf { it.isFinite() }

    private fun stat(build: PobBuild, vararg keys: String): Double {
        for (key in keys) {
            finite(build.stats[key])?.let { return it }
        }
        return 0.0
    }

    fun compute(
        build: PobBuild,
        overrides: Map<String, PobItem>,
        flasksEnabled: Set<String> = emptySet(),
        config: Config = Config(),
        /** 불러운 빌드에 찍힌 전직 노드 효과 */
        ascend: AscendancyEffects.Profile? = null,
        /** 장착한 고유 아이템의 특수 효과 */
        unique: UniqueEffects.Profile = UniqueEffects.EMPTY,
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
        val mainName = mainSkillName(build)
        val speedStat = stat(build, "AttacksPerSecond", "CastsPerSecond", "Speed", "HitSpeed")
        val averageHit = stat(build, "AverageHit", "AverageDamage")
        val critBase = stat(build, "CritChance").takeIf { it > 0 } ?: 5.0
        // PoB 2는 배율을 3.5처럼 배수로 저장한다. 350처럼 퍼센트로 저장한 빌드도 받아 준다.
        val rawMulti = stat(build, "CritMultiplier")
        val critMultiBase = if (rawMulti > 0) {
            if (rawMulti <= 10.0) rawMulti * 100.0 else rawMulti
        } else {
            150.0
        }
        val dpsPoB = stat(build, "TotalDPS", "CombinedDPS", "WithIgniteDPS").takeIf { it > 0 }
        // PoB가 DPS를 비우거나 무한(inf)으로 내보낸 빌드가 있다. 그럴 때만 직접 어림한다.
        val dpsFromHit = if (averageHit > 0 && speedStat > 0) {
            val critFactor = 1.0 + (critBase / 100.0) * (critMultiBase / 100.0 - 1.0).coerceAtLeast(0.0)
            averageHit * speedStat * hitsPerUse(mainName) * critFactor
        } else {
            0.0
        }
        val dpsBase = dpsPoB ?: dpsFromHit
        val dpsEstimated = dpsPoB == null
        val dpsInfinite = dpsPoB == null && build.nonFiniteStats.any { it in DPS_STATS }
        val apsBase = speedStat.takeIf { it > 0 } ?: 1.0
        val speedPct = total.atkSpdPct + total.castSpdPct
        val dmgFactor = (1 + total.dmgPct / 100) * (1 + speedPct / 100) *
            (1 + total.critChancePct / 100) * (1 + total.critMultiFlat / 100)
        // 전직 효과 중 PoB 단일 타격 계산에 없는 것만 여기서 보정한다.
        //  - 추가 투사체: 평균 타격으로 DPS를 어림할 때는 투사체 수가 빠져 있다.
        //  - 그 밖의 수치는 PoB가 이미 PlayerStat에 합산했으므로 다시 곱하지 않는다.
        val ascExtraProj = ascend?.sim?.extraProjectiles ?: 0
        val ascProjectileFactor = if (dpsEstimated && ascExtraProj > 0) 1.0 + ascExtraProj else 1.0
        val skillName = mainName
        val projectile = SkillEffects.detect(skillName) != SkillEffectType.MELEE
        val uniqueFactor = UniqueEffects.damageFactor(unique, projectile, dpsEstimated)
        val darkDps = if (isDarkConsequences(build)) darkConsequencesDps(build, critMultiBase) else 0.0
        val dpsNow = if (darkDps > 0.0) darkDps else dpsBase * dmgFactor * ascProjectileFactor * uniqueFactor
        val apsNow = apsBase * (1 + speedPct / 100)

        val critNow = (critBase * (1 + total.critChancePct / 100)).coerceAtMost(100.0)
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
        // PoB의 TotalEHP에는 "받는 피해 감폭"이 이미 들어 있으므로 여기서 또 늘리지 않는다.
        // 그 효과는 전투에서 실제로 받는 피해를 깎는 쪽(BattleWorld)에서만 쓴다.
        val ehpNow = if (ehpPoB > 0) ehpPoB * (poolNow / poolBase) else ehp(poolNow, armourNow, fireN, coldN, lightN)
        val rechargeDelay = (ES_RECHARGE_DELAY - (ascend?.sim?.esRechargeDelayCut ?: 0.0)).coerceIn(0.4, ES_RECHARGE_DELAY)

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
            dpsEstimated = dpsEstimated,
            dpsInfinite = dpsInfinite,
            factor = dmgFactor * ascProjectileFactor,
            ascendAoePct = ascend?.sim?.aoePct ?: 0.0,
            esRechargeDelay = rechargeDelay,
            ascendLifeRegenPct = (ascend?.sim?.lifeRegenPct ?: 0.0),
            ascendLifeLeechPct = (ascend?.sim?.lifeLeechPct ?: 0.0),
            ascendApplied = ascend?.nodes?.count { it.hasSim } ?: 0,
            ascendTotal = ascend?.count ?: 0,
            ascendSummary = ascend?.summary.orEmpty(),
            skillDps = distribute(build, dpsNow, dpsEstimated || darkDps > 0.0, skillName, scale = dpsNow / dpsBase.coerceAtLeast(0.0001)),
            totalDps = dpsNow,
            mainSkill = skillName,
        )
    }

    /** 공격 한 번에 여러 번 명중하는 스킬의 명중 횟수 (PoB가 HitSpeed를 비울 때의 보정) */
    fun hitsPerUse(skillName: String): Double {
        val key = TriggerKind.normalizeName(skillName)
        if (key.isEmpty()) return 1.0
        return when {
            key.contains("whirling assault") || key.contains("선회 공격") -> 3.0
            key.contains("flicker strike") || key.contains("질풍") || key.contains("점멸") -> 2.0
            key.contains("tempest") || key.contains("폭풍의 종") -> 1.6
            key.contains("barrage") || key.contains("연발") || key.contains("rapid") -> 2.4
            key.contains("spark") || key.contains("전기불꽃") -> 1.4
            key.contains("sunder") || key.contains("분쇄") -> 1.3
            else -> 1.0
        }
    }

    /**
     * PoB의 주력 스킬 이름.
     * `mainActiveSkill`이 보조 젬(도이드리의 파멸)을 가리키면 그 보조 젬이 발동하는
     * 암울한 결과가 실제 주력 스킬이다.
     */
    fun mainSkillName(build: PobBuild): String {
        val group = Pob.mainGroup(build, 0) ?: return build.className.ifBlank { "기본 공격" }
        val gems = group.gems.filter { it.enabled }
        // 도이드리의 파멸이 붙은 저주는 저주가 아니라 두꺼비 폭발(암울한 결과)로 피해를 낸다.
        // 주문 토템처럼 저주를 옭아주는 젬이 주력으로 잡혀도 실제 피해 스킬은 암울한 결과다.
        if (gems.any { Minions.isDoedre(it.name) } && gems.any { Minions.isCurseGem(it.name) }) {
            return Minions.DARK_CONSEQUENCES_EN
        }
        val wanted = group.mainActiveSkill
        if (wanted > 0) {
            gems.getOrNull(wanted - 1)?.let { gem ->
                if (!isSupport(gem) && PobTriggers.kindOf(gem) == null) return gem.name
            }
        }
        val actives = gems.filter { !isSupport(it) && PobTriggers.kindOf(it) == null }
        return actives.firstOrNull()?.name ?: group.title
    }

    /** 빌드 주력이 도이드리의 파멸로 발동하는 암울한 결과인지 */
    fun isDarkConsequences(build: PobBuild): Boolean {
        val group = Minions.darkConsequencesGroup(build) ?: return false
        // 저주 젬이 같이 있어야 두꺼비가 나온다.
        return group.gems.any { it.enabled && Minions.isCurseGem(it.name) }
    }

    /**
     * 암울한 결과의 초당 피해.
     * PoB는 이 스킬을 inf로 내보내므로(두꺼비가 계속 터진다) `AverageHit`과 두꺼비 수·생성 주기로 어림한다.
     */
    fun darkConsequencesDps(build: PobBuild, critMultiBase: Double = 150.0): Double {
        val group = Minions.darkConsequencesGroup(build) ?: return 0.0
        val level = group.gems.filter { it.enabled }
            .maxOfOrNull { gem -> if (Minions.isCurseGem(gem.name)) gem.level else 0 }
            ?.takeIf { it > 0 } ?: 20
        val (low, high) = Minions.darkConsequencesDamage(level)
        val avg = (low + high) / 2.0
        val hit = stat(build, "AverageHit").takeIf { it > 0 } ?: avg
        val crit = stat(build, "CritChance").takeIf { it > 0 } ?: 7.0
        val critFactor = 1.0 + (crit / 100.0) * (critMultiBase / 100.0 - 1.0).coerceAtLeast(0.0)
        val rate = Minions.TOAD_PER_AREA * Minions.TOAD_AREAS / Minions.TOAD_CYCLE
        return hit * rate * critFactor
    }

    /**
     * 주력 스킬별 DPS 목록.
     * PoB Full DPS 목록이 있으면 그 값을, 없으면 전체 DPS를 보조 젬·젬 레벨 비중으로 나눈다.
     * 주력 스킬이 암울한 결과면 두꺼비 폭발이 전체 DPS가 된다.
     */
    fun distribute(
        build: PobBuild,
        total: Double,
        estimated: Boolean,
        mainName: String,
        scale: Double = 1.0,
    ): List<SkillDps> {
        val out = LinkedHashMap<String, SkillDps>()
        val full = build.fullDpsSkills.filter { it.dps.isFinite() && it.dps > 0.0 }
        if (full.isNotEmpty()) {
            val factor = scale.takeIf { it.isFinite() && it > 0.0 } ?: 1.0
            full.forEach { skill ->
                val key = TriggerKind.normalizeName(skill.name)
                if (key.isEmpty()) return@forEach
                val value = skill.dps * factor
                val old = out[key]
                out[key] = SkillDps(skill.name, (old?.dps ?: 0.0) + value, false)
            }
        }
        // 주력 스킬이 목록에 없으면 넣는다 (PoB가 주력만 TotalDPS로 내보내는 경우).
        val mainKey = TriggerKind.normalizeName(mainName)
        if (mainKey.isNotEmpty() && out.keys.none { it == mainKey }) {
            val rest = out.values.sumOf { it.dps }
            val share = if (out.isEmpty()) total else (total - rest).coerceAtLeast(0.0)
            out[mainKey] = SkillDps(mainName, share, estimated)
        }
        if (out.isEmpty()) {
            if (total > 0.0) out[mainKey.ifEmpty { "주력" }] = SkillDps(mainName.ifBlank { "주력" }, total, estimated)
            return out.values.toList()
        }
        // PoB가 스킬별 값을 주지 않았으면 보조 젬·레벨 비중으로 전체를 나눈다.
        if (full.isEmpty()) {
            val skills = build.skills.filter { it.enabled }
                .flatMap { group ->
                    group.gems.filter { it.enabled && !isSupport(it) && PobTriggers.kindOf(it) == null }
                        .map { gem -> gem to group }
                }
                .distinctBy { it.first.name.lowercase() }
            if (skills.isNotEmpty()) {
                val weights = LinkedHashMap<String, Double>()
                skills.forEach { (gem, group) ->
                    val supportPct = group.gems.filter { it.enabled && isSupport(it) }
                        .sumOf { g -> SUPPORT_GEMS[g.name.lowercase()]?.dmgPct ?: 0.0 }
                    weights[gem.name] = ((1.0 + supportPct / 100.0) * (1.0 + (gem.level - 20) * 0.02))
                        .coerceAtLeast(0.05)
                }
                // 주력 스킬은 같은 무게라도 눈에 띄게 큰 몫을 갖도록 가중치를 준다.
                val mainGemName = weights.keys.firstOrNull { TriggerKind.normalizeName(it) == mainKey }
                val raw = LinkedHashMap<String, Double>()
                weights.forEach { (name, weight) ->
                    val bonus = if (name == mainGemName) 3.0 else 1.0
                    raw[name] = weight * bonus
                }
                val rawSum = raw.values.sum()
                if (rawSum > 0.0) {
                    out.clear()
                    // 주력이 젬 목록에 없는 발동 스킬(암울한 결과 등)이면 55%를 먼저 떼어 준다.
                    val mainRaw = if (mainGemName != null) raw.getValue(mainGemName) else 0.0
                    val otherSum = (rawSum - mainRaw).coerceAtLeast(0.0001)
                    val mainShare = if (mainGemName != null) {
                        (mainRaw / rawSum).coerceAtLeast(0.55)
                    } else {
                        0.55
                    }
                    raw.forEach { (name, weight) ->
                        if (name == mainGemName) return@forEach
                        val share = (weight / otherSum) * (1.0 - mainShare)
                        out[name] = SkillDps(name, total * share, estimated)
                    }
                    val mainLabel = mainGemName ?: mainKey.ifEmpty { "주력" }
                    out[mainLabel] = SkillDps(
                        mainGemName ?: mainName,
                        total * mainShare,
                        estimated,
                        note = if (mainGemName == null) "발동 스킬" else "",
                    )
                }
            }
        }
        return out.values.sortedByDescending { it.dps }
    }

    /** PoB가 DPS로 내보내는 스탯 이름들 */
    private val DPS_STATS = setOf(
        "TotalDPS", "CombinedDPS", "WithIgniteDPS", "WithBleedDPS", "WithPoisonDPS",
    )
}
