package com.sunuhwi.poe2dbkr

import kotlin.math.max
import kotlin.math.min

/**
 * 고유 아이템의 특수 옵션 해석.
 *
 * PoB는 고유 아이템의 수치(생명력·저항·공격 속도 등)를 이미 `PlayerStat`에 넣어 보내므로
 * 그런 값은 다시 더하지 않는다. 여기서는 **단일 타격 계산에 드러나지 않는** 효과만 골라
 * 전투 시뮬레이터가 직접 굴린다.
 *
 *  - 추가 투사체·연쇄: 한 번의 공격이 여러 적을 때린다
 *  - 피해 전환·추가 피해: `Gain 12% of Damage as Extra Fire Damage` 같은 구절
 *  - 처치 시 충전·희귀 몬스터 옵션 획득(헤드헌터) 같은 조건부 강화
 *  - 저주 부여·마무리 타격·상태이상 확률
 *  - 소환수 강화
 *
 * 옵션 문장은 영문 PoB 원문과 poe2db 한국어 표기를 모두 받는다.
 */
object UniqueEffects {

    /** 시뮬레이터가 직접 계산하는 효과 */
    data class Sim(
        var extraProjectiles: Int = 0,
        var extraChains: Int = 0,
        /** 피해의 몇 %를 추가 원소/카오스 피해로 얻는지 */
        var gainAsExtraPct: Double = 0.0,
        var gainElements: MutableSet<DamageElement> = linkedSetOf(),
        /** 명중 시 저주 (적 받는 피해 증가) */
        var curseOnHit: Boolean = false,
        /** 마무리 타격 */
        var cullingStrike: Boolean = false,
        /** 정지 상태 조건부 피해·공속 (조건은 전투에서 항상 켜진 것으로 본다) */
        var conditionalDamagePct: Double = 0.0,
        var conditionalAttackSpeedPct: Double = 0.0,
        var conditionalCritPct: Double = 0.0,
        /** 처치 시 충전 획득 확률(%) */
        var chargeOnKillPct: Double = 0.0,
        /** 희귀 몬스터 처치 시 그 옵션을 얻는 효과 (헤드헌터) */
        var rareKillBuff: Boolean = false,
        /** 주기적으로 무작위 성소 버프 (신념의 망치) */
        var shrineEverySeconds: Double = 0.0,
        /** 상태이상 부여 확률 증가(%) */
        var igniteChancePct: Double = 0.0,
        var poisonChancePct: Double = 0.0,
        var bleedChancePct: Double = 0.0,
        var shockChancePct: Double = 0.0,
        var freezeChancePct: Double = 0.0,
        /** 적이 걸린 원소 상태이상 종류당 피해 증가 (조련) */
        var damagePerAilmentPct: Double = 0.0,
        /** 받는 피해 감폭·치명타 피해 감소 */
        var damageTakenLessPct: Double = 0.0,
        var critDamageTakenLessPct: Double = 0.0,
        /** 1초마다 회복(최대 생명력·룬 수호 대비 %) */
        var regenPct: Double = 0.0,
        /** 생명력 대신 마나로 받는 피해 비율 */
        var damageTakenAsManaPct: Double = 0.0,
        /** 소환수 피해·생명력 증가(%) */
        var minionDamagePct: Double = 0.0,
        var minionLifePct: Double = 0.0,
        /** 정신력·접근 범위 */
        var spiritFlat: Double = 0.0,
        var presenceRadiusDoubled: Boolean = false,
        /** 부여 스킬 이름 (아이템이 주는 스킬) */
        var grantedSkills: MutableList<String> = ArrayList(),
        /** 장비에 붙은 저주/오라 이름 */
        var curses: MutableList<String> = ArrayList(),
    ) {
        val isEmpty: Boolean
            get() = this == Sim()
    }

    /** PoB가 이미 계산한 수치 (화면에 근거만 적는다) */
    data class Applied(
        val item: String,
        val itemKo: String,
        val text: String,
    )

    data class Profile(
        val sim: Sim,
        val applied: List<Applied>,
        /** 적용한 고유 아이템 이름 (한글 우선) */
        val names: List<String>,
        val effects: List<String>,
    ) {
        val isEmpty: Boolean get() = applied.isEmpty() && names.isEmpty()
        val summary: String
            get() = if (names.isEmpty()) "" else "고유 효과 ${applied.size}개 · " + names.joinToString(" · ")
    }

    val EMPTY = Profile(Sim(), emptyList(), emptyList(), emptyList())

    private val PERCENT = Regex("(-?\\d+(?:\\.\\d+)?)\\s*%")
    private val FLAT = Regex("([+-]\\d+(?:\\.\\d+)?)")

    /** 원소 이름 */
    private fun elementOf(text: String): DamageElement? {
        val lower = text.lowercase()
        return when {
            lower.contains("fire") || text.contains("화염") -> DamageElement.FIRE
            lower.contains("cold") || text.contains("냉기") -> DamageElement.COLD
            lower.contains("lightning") || text.contains("번개") -> DamageElement.LIGHTNING
            lower.contains("chaos") || text.contains("카오스") -> DamageElement.CHAOS
            lower.contains("physical") || text.contains("물리") -> DamageElement.PHYSICAL
            else -> null
        }
    }

    private fun pct(line: String): Double =
        PERCENT.find(line)?.groupValues?.get(1)?.toDoubleOrNull()
            ?: FLAT.find(line)?.groupValues?.get(1)?.toDoubleOrNull()
            ?: Regex("(\\d+(?:\\.\\d+)?)").find(line)?.groupValues?.get(1)?.toDoubleOrNull()
            ?: 0.0

    /**
     * 빌드에 장착된 고유 아이템을 읽는다.
     * [overrides]에 들어 있는 슬롯은 원래 아이템 대신 그 아이템을 본다.
     */
    fun profile(
        build: PobBuild,
        overrides: Map<String, PobItem> = emptyMap(),
        dict: KrDict = KrDict.EMPTY,
    ): Profile {
        val sim = Sim()
        val applied = ArrayList<Applied>()
        val names = ArrayList<String>()
        val effects = ArrayList<String>()
        val seen = HashSet<String>()

        for (slot in Pob.SLOTS.keys + listOf("Flask 1", "Flask 2")) {
            val item = overrides[slot] ?: Pob.itemForSlot(build, slot) ?: continue
            if (!item.rarity.equals("Unique", true)) continue
            val key = item.name.ifBlank { item.baseType }.lowercase()
            if (key.isEmpty() || !seen.add(key)) continue
            val nameKo = dict.name(item.name).ifBlank { item.name }
            names.add(if (nameKo.isNotBlank() && nameKo != item.name) nameKo else item.displayName)
            if (item.lines.isNotEmpty()) {
                parseItem(item, sim, applied, effects, dict)
            }
            applyKnownUnique(item, sim, applied, effects, dict)
        }
        // 보석(주얼)은 슬롯 이름 대신 아이템으로만 들어온다. PoB는 장착한 것만 저장하므로 같이 읽는다.
        for (item in build.items.values) {
            if (!item.rarity.equals("Unique", true)) continue
            if (!isJewel(item)) continue
            val key = item.name.ifBlank { item.baseType }.lowercase()
            if (key.isEmpty() || !seen.add(key)) continue
            val nameKo = dict.name(item.name).ifBlank { item.name }
            names.add(if (nameKo.isNotBlank() && nameKo != item.name) nameKo else item.displayName)
            if (item.lines.isNotEmpty()) {
                parseItem(item, sim, applied, effects, dict)
            }
            applyKnownUnique(item, sim, applied, effects, dict)
        }
        return Profile(sim, applied, names, effects.distinct())
    }

    private val JEWEL_BASES = setOf(
        "diamond", "ruby", "emerald", "sapphire", "prismatic jewel", "timeless jewel",
        "time-lost diamond", "time-lost ruby", "time-lost emerald", "time-lost sapphire",
        "grand spectrum", "sublime vision",
    )

    private fun isJewel(item: PobItem): Boolean {
        val base = item.baseType.lowercase().trim()
        if (base in JEWEL_BASES) return true
        if (item.itemClass.contains("jewel", true)) return true
        return item.lines.any { it.contains("Radius:", true) } && base.isNotBlank()
    }

    private fun parseItem(
        item: PobItem,
        sim: Sim,
        applied: MutableList<Applied>,
        effects: MutableList<String>,
        dict: KrDict,
    ) {
        val label = item.name.ifBlank { item.baseType }
        for (raw in item.lines) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            val lower = line.lowercase()
            fun has(vararg keys: String) = keys.any { lower.contains(it) }
            var matched = true
            when {
                // 피해의 일부를 다른 속성으로 얻는다
                has("gain", "of damage as extra") && has("damage") -> {
                    val element = elementOf(line)
                    val value = pct(line)
                    if (element != null && value > 0) {
                        sim.gainAsExtraPct += value
                        sim.gainElements.add(element)
                        note(applied, effects, item, line, "${element.label} 추가 피해 ${value.toInt()}%", dict)
                    }
                }
                line.contains("피해의") && line.contains("추가") && line.contains("획득") -> {
                    val element = elementOf(line)
                    val value = pct(line)
                    if (element != null && value > 0) {
                        sim.gainAsExtraPct += value
                        sim.gainElements.add(element)
                        note(applied, effects, item, line, "${element.label} 추가 피해 ${value.toInt()}%", dict)
                    }
                }
                // 추가 투사체·연쇄
                has("additional projectile", "extra projectile", "fires an additional") ||
                    line.contains("투사체") && line.contains("추가") -> {
                    sim.extraProjectiles += max(1, pct(line).toInt())
                    note(applied, effects, item, line, "추가 투사체 ${max(1, pct(line).toInt())}", dict)
                }
                has("chain") && (has("additional", "extra") || line.contains("추가")) -> {
                    sim.extraChains += max(1, pct(line).toInt())
                    note(applied, effects, item, line, "추가 연쇄 ${max(1, pct(line).toInt())}", dict)
                }
                // 저주 부여
                has("curse") && has("on hit", "on hitting", "hits") -> {
                    sim.curseOnHit = true
                    val name = curseNameOf(line)
                    if (name.isNotBlank()) sim.curses.add(name)
                    note(applied, effects, item, line, "명중 시 저주" + if (name.isNotBlank()) "($name)" else "", dict)
                }
                line.contains("저주") && (line.contains("명중") || line.contains("적중")) -> {
                    sim.curseOnHit = true
                    note(applied, effects, item, line, "명중 시 저주", dict)
                }
                // 마무리 타격
                has("culling strike") || line.contains("마무리 타격") -> {
                    sim.cullingStrike = true
                    note(applied, effects, item, line, "마무리 타격", dict)
                }
                // 희귀 몬스터 옵션 획득 (헤드헌터)
                has("rare monster", "rare enemy") && has("modifiers", "mods") -> {
                    sim.rareKillBuff = true
                    note(applied, effects, item, line, "희귀 처치 시 옵션 획득", dict)
                }
                // 주기적 성소 버프
                has("shrine") -> {
                    val seconds = Regex("every (\\d+) seconds").find(lower)?.groupValues?.get(1)?.toDoubleOrNull() ?: 10.0
                    sim.shrineEverySeconds = if (sim.shrineEverySeconds > 0) min(sim.shrineEverySeconds, seconds) else seconds
                    note(applied, effects, item, line, "${seconds.toInt()}초마다 성소 버프", dict)
                }
                // 처치 시 충전
                has("chance to gain a charge") || (line.contains("충전") && line.contains("처치")) -> {
                    val value = pct(line)
                    sim.chargeOnKillPct = max(sim.chargeOnKillPct, value)
                    note(applied, effects, item, line, "처치 시 충전 ${value.toInt()}%", dict)
                }
                // 상태이상 확률
                has("chance to ignite") || line.contains("점화를 유발") -> {
                    sim.igniteChancePct += pct(line)
                    note(applied, effects, item, line, "점화 확률 ${pct(line).toInt()}%", dict)
                }
                has("chance to poison") || line.contains("중독을 유발") -> {
                    sim.poisonChancePct += pct(line)
                    note(applied, effects, item, line, "중독 확률 ${pct(line).toInt()}%", dict)
                }
                has("chance to bleed") || line.contains("출혈을 유발") -> {
                    sim.bleedChancePct += pct(line)
                    note(applied, effects, item, line, "출혈 확률 ${pct(line).toInt()}%", dict)
                }
                has("chance to shock") || line.contains("감전을 유발") -> {
                    sim.shockChancePct += pct(line)
                    note(applied, effects, item, line, "감전 확률 ${pct(line).toInt()}%", dict)
                }
                has("chance to freeze") || line.contains("동결") -> {
                    sim.freezeChancePct += pct(line)
                    note(applied, effects, item, line, "동결 확률 ${pct(line).toInt()}%", dict)
                }
                // 상태이상 종류당 피해
                has("damage for each type of elemental ailment") -> {
                    sim.damagePerAilmentPct = max(sim.damagePerAilmentPct, pct(line))
                    note(applied, effects, item, line, "원소 상태이상 하나당 피해 ${pct(line).toInt()}%", dict)
                }
                // 받는 피해 감폭
                (has("less damage taken") || line.contains("받는 피해") && line.contains("감폭")) -> {
                    sim.damageTakenLessPct += pct(line)
                    note(applied, effects, item, line, "받는 피해 감폭 ${pct(line).toInt()}%", dict)
                }
                has("reduced critical damage bonus") || line.contains("치명타 피해 보너스") && line.contains("감소") -> {
                    sim.critDamageTakenLessPct += pct(line)
                    note(applied, effects, item, line, "받는 치명타 피해 감소 ${pct(line).toInt()}%", dict)
                }
                // 생명력 대신 마나로 받음
                has("damage is taken from mana", "taken from mana before life") ->
                    sim.damageTakenAsManaPct = max(sim.damageTakenAsManaPct, pct(line))
                // 재생
                has("regenerate") && has("% of", "% per second") -> {
                    sim.regenPct += pct(line)
                    note(applied, effects, item, line, "초당 재생 ${pct(line)}%", dict)
                }
                line.contains("재생") && (line.contains("1초마다") || line.contains("매초")) -> {
                    sim.regenPct += pct(line)
                    note(applied, effects, item, line, "초당 재생 ${pct(line)}%", dict)
                }
                // 소환수 강화
                (has("minion", "소환수") || has("minions")) && has("life", "생명력") -> {
                    sim.minionLifePct += pct(line)
                    note(applied, effects, item, line, "소환수 생명력 ${pct(line).toInt()}%", dict)
                }
                (has("minion", "소환수") || has("minions")) && has("damage", "피해") -> {
                    sim.minionDamagePct += pct(line)
                    note(applied, effects, item, line, "소환수 피해 ${pct(line).toInt()}%", dict)
                }
                // 부여 스킬
                has("grants skill", "grants level") || line.contains("스킬 부여") -> {
                    val skill = grantedNameOf(line)
                    if (skill.isNotBlank()) {
                        sim.grantedSkills.add(skill)
                        note(applied, effects, item, line, "스킬 부여: $skill", dict)
                    }
                }
                // 정신력
                has("to spirit") || line.contains("정신력 +") -> {
                    sim.spiritFlat += pct(line)
                    note(applied, effects, item, line, "정신력 +${pct(line).toInt()}", dict)
                }
                // 접근 범위
                has("presence radius is doubled") || line.contains("접근 범위 반경 2배") -> {
                    sim.presenceRadiusDoubled = true
                    note(applied, effects, item, line, "접근 범위 2배", dict)
                }
                else -> matched = false
            }
            if (!matched) continue
        }
        // 인식하지 못한 나머지 옵션은 PoB가 계산한 것으로 보고 목록에만 남긴다.
    }

    private fun note(
        applied: MutableList<Applied>,
        effects: MutableList<String>,
        item: PobItem,
        line: String,
        short: String,
        dict: KrDict,
    ) {
        val name = item.name.ifBlank { item.baseType }
        val ko = dict.name(name).ifBlank { name }
        if (applied.none { it.item == name && it.text == line }) {
            applied.add(Applied(name, ko, line))
            effects.add(short)
        }
    }

    /** 아이템 이름만으로 알 수 있는 대표 효과 (옵션 줄에 드러나지 않는 것) */
    private fun applyKnownUnique(
        item: PobItem,
        sim: Sim,
        applied: MutableList<Applied>,
        effects: MutableList<String>,
        dict: KrDict,
    ) {
        val key = TriggerKind.normalizeName(item.name)
        if (key.isEmpty()) return
        fun add(short: String) {
            if (!effects.contains(short)) effects.add(short)
            if (applied.none { it.item == item.name && it.text == short }) {
                applied.add(Applied(item.name, dict.name(item.name).ifBlank { item.name }, short))
            }
        }
        when {
            key.contains("headhunter") -> {
                sim.rareKillBuff = true
                add("희귀 몬스터 처치 시 그 옵션을 60초 동안 획득")
            }
            key.contains("taming") -> {
                sim.damagePerAilmentPct = max(sim.damagePerAilmentPct, 20.0)
                add("적에게 걸린 원소 상태이상 하나당 피해 20%")
            }
            key.contains("murkshaft") || key.contains("진창 수갱") -> {
                sim.grantedSkills.add("Bursting Fen Toad")
                add("터지는 늪 두꺼비 10레벨 부여")
            }
            key.contains("doedres ten") || key.contains("도이드리의 임기") -> {
                add("주문 피해 100% 증가 · 시전 속도 15~25% 감소")
            }
            key.contains("lochtonial") || key.contains("록토니얼") -> {
                sim.chargeOnKillPct = max(sim.chargeOnKillPct, 8.0)
                add("명중 시 동료에게 충전 부여")
            }
            key.contains("morior") || key.contains("모리오르") -> {
                sim.damageTakenLessPct += 6.0
                add("홈 하나당 받는 치명타 피해 15% 감소")
            }
            key.contains("alphas howl") || key.contains("우두머리의 울부짖음") -> {
                sim.presenceRadiusDoubled = true
                add("접근 범위 2배")
            }
            key.contains("olroths resolve") || key.contains("올로스의 결의") -> {
                sim.regenPct += 3.0
                add("플라스크 효과 중 룬 수호 재생")
            }
            key.contains("hammer of faith") || key.contains("신념의 망치") -> {
                sim.shrineEverySeconds = if (sim.shrineEverySeconds > 0) sim.shrineEverySeconds else 10.0
                add("10초마다 무작위 성소 버프")
            }
            key.contains("rite of passage") || key.contains("통과의례") -> {
                add("희귀·고유 처치 시 아즈메리 혼백")
            }
            key.contains("decree of flight") || key.contains("비행의 법령") -> {
                add("구르기로 가호 획득")
            }
            key.contains("nascent hope") || key.contains("태동하는 희망") -> {
                add("동결 시 사용되어 보호막 재충전")
            }
            key.contains("beiras anguish") || key.contains("베이라의 비통") -> {
                sim.igniteChancePct += 25.0
                add("사용 시 점화 지대")
            }
            key.contains("lavianga") || key.contains("라비앙가") -> {
                add("마나 플라스크 효과 상시 적용")
            }
        }
    }

    /**
     * 고유 효과를 반영한 피해 배수.
     * 추가 피해는 그대로 곱하고, 추가 투사체·연쇄는 명중 횟수를 늘린 것으로 본다.
     */
    fun damageFactor(profile: Profile, projectile: Boolean, estimated: Boolean): Double {
        val sim = profile.sim
        if (sim.isEmpty) return 1.0
        val extra = 1.0 + sim.gainAsExtraPct.coerceIn(0.0, 300.0) / 100.0
        val projectiles = if (projectile && estimated) 1.0 + sim.extraProjectiles.coerceAtMost(4) else 1.0
        val chains = 1.0 + sim.extraChains.coerceAtMost(3) * 0.15
        val conditional = 1.0 + (sim.conditionalDamagePct + sim.conditionalAttackSpeedPct) / 100.0
        return extra * projectiles * chains * conditional
    }

    private fun curseNameOf(line: String): String {
        val known = listOf(
            "Despair", "Enfeeble", "Vulnerability", "Elemental Weakness", "Flammability",
            "Conductivity", "Frostbite", "Temporal Chains", "Hypothermia",
            "절망", "쇠약", "취약성", "원소 약화", "인화성", "전도성", "동상", "시간의 사슬",
        )
        return known.firstOrNull { line.contains(it, ignoreCase = true) }.orEmpty()
    }

    private fun grantedNameOf(line: String): String {
        Regex("(?i)grants? (?:skill:? )?(?:level \\d+ )?(.+)$").find(line)?.let {
            return it.groupValues[1].trim().trimEnd('.')
        }
        val korean = line.substringAfter("스킬 부여:", "").ifBlank { line.substringAfter("스킬 부여", "") }
        return korean.trim().trim('{', '}', ' ')
    }
}
