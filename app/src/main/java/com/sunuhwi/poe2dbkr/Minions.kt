package com.sunuhwi.poe2dbkr

import kotlin.math.max
import kotlin.math.min

/**
 * 소환수·반려수 젬 해석.
 *
 * PoE2의 소환수 젬은 PoB XML에 젬 이름만 남고 소환수 수치(생명력·피해)는 거의 실리지 않는다.
 * 그래서 여기서는 poe2db 자료에 적힌 점유 정신력·소환 수·피해 속성을 읽어,
 * 전투 시뮬레이터가 쓸 소환수 목록([MinionPlan])으로 바꾼다.
 *
 * 피해는 플레이어 주력 DPS에 대한 비율로 잡는다. 소환수 젬이 주력이면 PoB가 계산한 DPS를
 * 소환수들이 나눠 갖고, 플레이어가 직접 때리는 빌드면 소환수가 덧붙이는 몫만 더한다.
 * (실제 게임의 소환수 피해 계산을 그대로 옮긴 것이 아니라 눈으로 확인할 수 있는 근사치다.)
 */
enum class MinionRole(val label: String) {
    MELEE("근접"),
    RANGED("원거리"),
    CASTER("주문"),
    SUPPORT("지원"),
    COMPANION("반려수"),
    TOAD("두꺼비"),
}

/** 소환수 한 종류 */
data class MinionType(
    val key: String,
    val nameKo: String,
    val nameEn: String,
    val role: MinionRole,
    val element: DamageElement,
    /** 한 번에 소환되는 마릿수 (늑대 무리처럼 여러 마리인 젬) */
    val count: Int,
    /** 늑대 무리처럼 마릿수가 레벨에 따라 늘어나는 경우의 최대치 */
    val maxCount: Int,
    /** 플레이어 주력 DPS 대비 이 소환수 한 마리의 몫 */
    val dpsShare: Double,
    /** 플레이어 최대 생명력 대비 이 소환수 한 마리의 생명력 */
    val lifeShare: Double,
    /** 소환에 드는 정신력 */
    val spirit: Double,
    /** 이동 속도(전투 캔버스 기준) */
    val speed: Float,
    /** 그리는 크기 */
    val scale: Float,
    /** 원거리에서 때리는 소환수인지 */
    val ranged: Boolean = false,
    /** 부활하는 언데드인지 (소환수 사망 시 시전의 에너지원) */
    val undead: Boolean = false,
    /** 적이 이 소환수를 직접 공격하지 않는지 (격노의 유령) */
    val untargetable: Boolean = false,
    /** 반려수인지 */
    val companion: Boolean = false,
    val note: String = "",
) {
    val label: String get() = if (nameKo.isBlank()) nameEn else nameKo
}

/** 전투에 세울 소환수 한 묶음 */
data class MinionSpawn(val type: MinionType, val count: Int) {
    val dps: Double get() = type.dpsShare * count
}

/**
 * 빌드에서 뽑아낸 소환수 구성.
 *
 * [toadCount]는 도이드리의 파멸이 만드는 마녀두꺼비 수, [darkConsequences]는 그 두꺼비가
 * 도약해 터질 때의 폭발 피해다. 소환수 젬이 하나도 없으면 [EMPTY]다.
 */
data class MinionPlan(
    val spawns: List<MinionSpawn> = emptyList(),
    val toadCount: Int = 0,
    val toadLevel: Int = 0,
    val darkConsequencesMin: Double = 0.0,
    val darkConsequencesMax: Double = 0.0,
    val darkConsequencesDps: Double = 0.0,
    val darkConsequencesCrit: Double = 7.0,
    val spiritReserved: Double = 0.0,
    val spiritBudget: Double = 0.0,
    val notes: List<String> = emptyList(),
) {
    val isEmpty: Boolean get() = spawns.isEmpty() && toadCount == 0
    val totalCount: Int get() = spawns.sumOf { it.count } + toadCount
    val minionDps: Double get() = spawns.sumOf { it.dps }
    val hasDarkConsequences: Boolean get() = toadCount > 0 && darkConsequencesMax > 0.0

    /** 화면에 한 줄로 적을 요약 (예: 해골 전사 2 · 해골 저격수 1 · 마녀두꺼비 8) */
    val summary: String
        get() = buildList {
            spawns.forEach { spawn ->
                val name = spawn.type.label
                add(if (spawn.count > 1) "$name ${spawn.count}" else name)
            }
            if (toadCount > 0) add("마녀두꺼비 $toadCount")
        }.joinToString(" · ")

    companion object {
        val EMPTY = MinionPlan()
    }
}

/** 소환수 젬 목록과 인식 */
object Minions {

    /** 마녀두꺼비가 터지는 스킬의 한글 이름 */
    const val DARK_CONSEQUENCES_KO = "암울한 결과"
    const val DARK_CONSEQUENCES_EN = "Dark Consequences"

    /** 도이드리의 파멸 — 저주받은 지대에 마녀두꺼비를 만드는 혈통 보조 젬 */
    val DOEDRE_NAMES = listOf(
        "doedre's undoing", "doedres undoing", "doedre", "도이드리의 파멸", "도이드리의파멸",
    )

    /** 암울한 결과 레벨별 폭발 피해 (poe2db 레벨 효과표, 2026-11 기준) */
    private val DARK_LEVELS = listOf(
        5.0 to 10.0, 7.0 to 14.0, 10.0 to 19.0, 13.0 to 24.0, 17.0 to 31.0,
        21.0 to 38.0, 25.0 to 47.0, 30.0 to 56.0, 36.0 to 67.0, 43.0 to 79.0,
        50.0 to 93.0, 59.0 to 109.0, 68.0 to 127.0, 79.0 to 147.0, 92.0 to 170.0,
        106.0 to 197.0, 122.0 to 227.0, 141.0 to 261.0, 162.0 to 300.0, 186.0 to 345.0,
        214.0 to 397.0, 245.0 to 456.0, 282.0 to 523.0, 324.0 to 601.0, 372.0 to 691.0,
        427.0 to 793.0, 491.0 to 912.0, 565.0 to 1049.0, 650.0 to 1207.0, 749.0 to 1391.0,
        863.0 to 1603.0, 996.0 to 1849.0, 1150.0 to 2135.0, 1329.0 to 2468.0, 1538.0 to 2856.0,
        1781.0 to 3308.0, 2066.0 to 3836.0, 2399.0 to 4455.0, 2789.0 to 5179.0, 3246.0 to 6029.0,
    )

    /** 레벨에 따른 암울한 결과 피해 (최소, 최대) */
    fun darkConsequencesDamage(level: Int): Pair<Double, Double> {
        if (DARK_LEVELS.isEmpty()) return 0.0 to 0.0
        val index = (level - 1).coerceIn(0, DARK_LEVELS.size - 1)
        return DARK_LEVELS[index]
    }

    /**
     * 소환수 젬 목록.
     *
     * 수치는 poe2db의 젬 설명(점유 정신력·소환 수·피해 속성)과 전투에서 눈으로 구분되는
     * 움직임을 기준으로 잡았다. `dpsShare`는 플레이어 주력 DPS 한 줄에 대한 상대값이다.
     */
    val TYPES: List<MinionType> = listOf(
        MinionType(
            key = "skeletal warrior", nameKo = "해골 전사", nameEn = "Skeletal Warrior",
            role = MinionRole.MELEE, element = DamageElement.PHYSICAL,
            count = 2, maxCount = 6, dpsShare = 0.11, lifeShare = 0.38, spirit = 20.0,
            speed = 32f, scale = 0.62f, undead = true,
            note = "처음 두 마리는 정신력 점유 없음",
        ),
        MinionType(
            key = "skeletal sniper", nameKo = "해골 저격수", nameEn = "Skeletal Sniper",
            role = MinionRole.RANGED, element = DamageElement.CHAOS,
            count = 1, maxCount = 4, dpsShare = 0.20, lifeShare = 0.30, spirit = 30.0,
            speed = 26f, scale = 0.60f, ranged = true, undead = true,
            note = "가스 화살·중독",
        ),
        MinionType(
            key = "skeletal arsonist", nameKo = "해골 방화범", nameEn = "Skeletal Arsonist",
            role = MinionRole.CASTER, element = DamageElement.FIRE,
            count = 1, maxCount = 4, dpsShare = 0.22, lifeShare = 0.30, spirit = 39.0,
            speed = 26f, scale = 0.62f, ranged = true, undead = true,
            note = "폭탄 투척·소환수 폭발",
        ),
        MinionType(
            key = "skeletal frost mage", nameKo = "해골 서리 마법사", nameEn = "Skeletal Frost Mage",
            role = MinionRole.CASTER, element = DamageElement.COLD,
            count = 1, maxCount = 4, dpsShare = 0.18, lifeShare = 0.28, spirit = 39.0,
            speed = 24f, scale = 0.60f, ranged = true, undead = true,
            note = "얼음 보호막",
        ),
        MinionType(
            key = "skeletal storm mage", nameKo = "해골 폭풍 마법사", nameEn = "Skeletal Storm Mage",
            role = MinionRole.CASTER, element = DamageElement.LIGHTNING,
            count = 1, maxCount = 4, dpsShare = 0.19, lifeShare = 0.28, spirit = 39.0,
            speed = 24f, scale = 0.60f, ranged = true, undead = true,
            note = "감전 지대",
        ),
        MinionType(
            key = "skeletal brute", nameKo = "해골 돌격병", nameEn = "Skeletal Brute",
            role = MinionRole.MELEE, element = DamageElement.PHYSICAL,
            count = 1, maxCount = 3, dpsShare = 0.27, lifeShare = 0.55, spirit = 48.0,
            speed = 28f, scale = 0.82f, undead = true,
            note = "함성·기절",
        ),
        MinionType(
            key = "skeletal reaver", nameKo = "해골 강탈자", nameEn = "Skeletal Reaver",
            role = MinionRole.MELEE, element = DamageElement.PHYSICAL,
            count = 1, maxCount = 4, dpsShare = 0.23, lifeShare = 0.36, spirit = 30.0,
            speed = 40f, scale = 0.66f, undead = true,
            note = "격노할수록 빨라짐",
        ),
        MinionType(
            key = "skeletal cleric", nameKo = "해골 성직자", nameEn = "Skeletal Cleric",
            role = MinionRole.SUPPORT, element = DamageElement.PHYSICAL,
            count = 1, maxCount = 2, dpsShare = 0.0, lifeShare = 0.26, spirit = 20.0,
            speed = 24f, scale = 0.58f, undead = true,
            note = "다른 소환수 치유·부활",
        ),
        MinionType(
            key = "raise zombie", nameKo = "좀비", nameEn = "Raise Zombie",
            role = MinionRole.MELEE, element = DamageElement.PHYSICAL,
            count = 3, maxCount = 10, dpsShare = 0.08, lifeShare = 0.50, spirit = 0.0,
            speed = 24f, scale = 0.70f, undead = true,
            note = "20초 지속",
        ),
        MinionType(
            key = "raging spirits", nameKo = "격노의 유령", nameEn = "Raging Spirits",
            role = MinionRole.MELEE, element = DamageElement.FIRE,
            count = 4, maxCount = 10, dpsShare = 0.06, lifeShare = 0.12, spirit = 30.0,
            speed = 54f, scale = 0.44f, undead = true, untargetable = true,
            note = "적이 지나쳐 감",
        ),
        MinionType(
            key = "bind spectre", nameKo = "망령", nameEn = "Bind Spectre",
            role = MinionRole.CASTER, element = DamageElement.CHAOS,
            count = 1, maxCount = 4, dpsShare = 0.22, lifeShare = 0.40, spirit = 30.0,
            speed = 24f, scale = 0.66f, ranged = true, undead = true,
        ),
        MinionType(
            key = "tame beast", nameKo = "길들인 야수", nameEn = "Tame Beast",
            role = MinionRole.COMPANION, element = DamageElement.PHYSICAL,
            count = 1, maxCount = 1, dpsShare = 0.38, lifeShare = 0.75, spirit = 0.0,
            speed = 46f, scale = 0.86f, companion = true,
            note = "고유 야수 1마리",
        ),
        MinionType(
            key = "wolf pack", nameKo = "늑대 무리", nameEn = "Wolf Pack",
            role = MinionRole.COMPANION, element = DamageElement.PHYSICAL,
            count = 3, maxCount = 6, dpsShare = 0.05, lifeShare = 0.30, spirit = 60.0,
            speed = 48f, scale = 0.60f, companion = true,
        ),
        MinionType(
            key = "cackling companions", nameKo = "하이에나 반려수", nameEn = "Cackling Companions",
            role = MinionRole.COMPANION, element = DamageElement.PHYSICAL,
            count = 6, maxCount = 9, dpsShare = 0.035, lifeShare = 0.24, spirit = 60.0,
            speed = 50f, scale = 0.54f, companion = true,
        ),
        MinionType(
            key = "mist raven", nameKo = "연무의 큰까마귀", nameEn = "Mist Raven",
            role = MinionRole.COMPANION, element = DamageElement.CHAOS,
            count = 1, maxCount = 2, dpsShare = 0.16, lifeShare = 0.30, spirit = 30.0,
            speed = 58f, scale = 0.62f, companion = true, untargetable = true,
            note = "마무리 타격",
        ),
        MinionType(
            key = "summon infernal hound", nameKo = "지옥불 사냥개", nameEn = "Summon Infernal Hound",
            role = MinionRole.COMPANION, element = DamageElement.FIRE,
            count = 1, maxCount = 2, dpsShare = 0.24, lifeShare = 0.60, spirit = 40.0,
            speed = 46f, scale = 0.74f, companion = true,
            note = "주변 점화",
        ),
        MinionType(
            key = "wild protector", nameKo = "야생 보호자", nameEn = "Wild Protector",
            role = MinionRole.COMPANION, element = DamageElement.PHYSICAL,
            count = 1, maxCount = 1, dpsShare = 0.28, lifeShare = 0.80, spirit = 40.0,
            speed = 40f, scale = 0.94f, companion = true,
            note = "곰 혼백",
        ),
        MinionType(
            key = "azmerian wolf", nameKo = "아즈메리 늑대", nameEn = "Azmerian Wolf",
            role = MinionRole.COMPANION, element = DamageElement.PHYSICAL,
            count = 1, maxCount = 1, dpsShare = 0.26, lifeShare = 0.65, spirit = 40.0,
            speed = 50f, scale = 0.78f, companion = true,
            note = "출혈·울부짖음",
        ),
        MinionType(
            key = "rhoa mount", nameKo = "로아", nameEn = "Rhoa Mount",
            role = MinionRole.COMPANION, element = DamageElement.PHYSICAL,
            count = 1, maxCount = 1, dpsShare = 0.14, lifeShare = 0.55, spirit = 100.0,
            speed = 52f, scale = 0.88f, companion = true,
            note = "부리 공격",
        ),
        MinionType(
            key = "manifest weapon", nameKo = "무기 현현", nameEn = "Manifest Weapon",
            role = MinionRole.MELEE, element = DamageElement.PHYSICAL,
            count = 1, maxCount = 1, dpsShare = 0.34, lifeShare = 0.45, spirit = 40.0,
            speed = 42f, scale = 0.72f,
            note = "무기 복제품",
        ),
        MinionType(
            key = "spirit vessel", nameKo = "혼백 그릇", nameEn = "Spirit Vessel",
            role = MinionRole.COMPANION, element = DamageElement.PHYSICAL,
            count = 1, maxCount = 1, dpsShare = 0.26, lifeShare = 0.70, spirit = 40.0,
            speed = 44f, scale = 0.80f, companion = true,
            note = "곰·늑대인간 스킬 사용",
        ),
        MinionType(
            key = "azmerian swarms", nameKo = "아즈메리 무리", nameEn = "Azmerian Swarms",
            role = MinionRole.MELEE, element = DamageElement.PHYSICAL,
            count = 3, maxCount = 6, dpsShare = 0.04, lifeShare = 0.18, spirit = 30.0,
            speed = 50f, scale = 0.44f,
        ),
        MinionType(
            key = "eternal march", nameKo = "영혼 행군", nameEn = "Eternal March",
            role = MinionRole.MELEE, element = DamageElement.CHAOS,
            count = 2, maxCount = 6, dpsShare = 0.09, lifeShare = 0.30, spirit = 30.0,
            speed = 30f, scale = 0.62f, undead = true,
        ),
        MinionType(
            key = "golem", nameKo = "골렘", nameEn = "Summon Golem",
            role = MinionRole.MELEE, element = DamageElement.PHYSICAL,
            count = 1, maxCount = 3, dpsShare = 0.22, lifeShare = 0.85, spirit = 50.0,
            speed = 22f, scale = 0.95f,
            note = "돌 골렘",
        ),
    )

    private val byKey: Map<String, MinionType> = TYPES.associateBy { it.key }

    /** 젬 이름으로 소환수 종류를 찾는다 (영문·한글) */
    fun typeOf(gemName: String): MinionType? {
        val key = TriggerKind.normalizeName(gemName)
        if (key.isEmpty()) return null
        byKey[key]?.let { return it }
        // 이름이 조금 달라도(레벨 접두사·수식어) 포함 관계로 찾는다.
        return TYPES.firstOrNull { type ->
            val en = TriggerKind.normalizeName(type.nameEn)
            val ko = TriggerKind.normalizeName(type.nameKo)
            (en.isNotEmpty() && (key == en || key.startsWith("$en "))) ||
                (ko.isNotEmpty() && key.contains(ko))
        }
    }

    /** 빌드의 소환수 젬을 모두 모아 전투 구성으로 만든다. */
    fun plan(
        build: PobBuild,
        stats: Map<String, Double> = build.stats,
        mainIsSummon: Boolean = false,
        playerDps: Double = 0.0,
        darkHitOverride: Double = 0.0,
        cooldownRatePct: Double = 0.0,
        critChance: Double = 7.0,
        critMulti: Double = 150.0,
    ): MinionPlan {
        val found = LinkedHashMap<String, MutableList<Int>>() // key -> 그룹 안에서 센 마릿수
        var doedreGroup: PobSkillGroup? = null
        var doedreLevel = 1
        for (group in build.skills) {
            if (!group.enabled) continue
            var groupHasDoedre = false
            var curseLevel = 0
            for (gem in group.gems) {
                if (!gem.enabled) continue
                if (isDoedre(gem.name)) {
                    groupHasDoedre = true
                    doedreLevel = max(doedreLevel, gem.level)
                    continue
                }
                val type = typeOf(gem.name) ?: continue
                if (type.role == MinionRole.SUPPORT && !group.gems.any { it.enabled && it !== gem && typeOf(it.name)?.undead == true }) {
                    // 성직자는 치료할 해골이 있을 때만 의미가 있다.
                }
                val slot = found.getOrPut(type.key) { ArrayList() }
                slot.add(gem.level)
                if (curseLevel == 0 && isCurseGem(gem.name)) curseLevel = gem.level
            }
            if (groupHasDoedre && doedreGroup == null) {
                // 암울한 결과는 보조 대상 저주 젬의 레벨을 따른다.
                val level = if (curseLevel > 0) curseLevel else doedreLevel
                doedreLevel = max(doedreLevel, level)
                doedreGroup = group
            }
        }

        val budget = stats["Spirit"]?.takeIf { it.isFinite() && it > 0.0 } ?: 0.0
        var reserved = 0.0
        val notes = ArrayList<String>()
        val spawns = ArrayList<MinionSpawn>()
        var countLeft = MAX_MINION_INSTANCES
        for ((key, levels) in found) {
            val type = byKey[key] ?: continue
            var n = type.count
            // 젬 레벨이 높으면 마릿수가 늘어나는 소환수(늑대 무리 등)
            val level = levels.maxOrNull() ?: 20
            if (type.maxCount > type.count) {
                val extra = ((level - 20).coerceAtLeast(0) / 10)
                n = (n + extra).coerceAtMost(type.maxCount)
            }
            if (type.key == "skeletal warrior") n = 2
            if (type.key == "raise zombie") n = min(type.maxCount, max(3, level / 2))
            if (type.key == "raging spirits") n = min(type.maxCount, max(4, level / 3))
            if (budget > 0.0 && type.spirit > 0.0) {
                val affordable = ((budget - reserved) / type.spirit).toInt().coerceAtLeast(0)
                if (affordable < n) {
                    if (affordable <= 0) {
                        notes.add("${type.label}: 정신력이 모자라 소환하지 못함")
                        continue
                    }
                    notes.add("${type.label}: 정신력에 맞춰 $affordable 마리로 줄임")
                    n = affordable
                }
                reserved += type.spirit * n
            }
            n = min(n, countLeft)
            if (n <= 0) continue
            countLeft -= n
            spawns.add(MinionSpawn(type, n))
        }

        // 도이드리의 파멸: 저주받은 지대 2개 × 지대마다 4마리
        var toads = 0
        var darkMin = 0.0
        var darkMax = 0.0
        var darkDps = 0.0
        if (doedreGroup != null) {
            toads = TOAD_PER_AREA * TOAD_AREAS
            val damage = darkConsequencesDamage(doedreLevel)
            darkMin = damage.first
            darkMax = damage.second
            val avg = (darkMin + darkMax) / 2.0
            // 두꺼비는 0.25초마다 생성되지만 도약·폭발까지 시간이 걸린다.
            val explodeRate = toads / TOAD_CYCLE * (1.0 + cooldownRatePct.coerceIn(0.0, 200.0) / 200.0)
            val critFactor = 1.0 + (critChance / 100.0) * (critMulti / 100.0 - 1.0).coerceAtLeast(0.0)
            val hit = if (darkHitOverride > 0.0) darkHitOverride else avg
            darkDps = hit * explodeRate * critFactor
            notes.add("도이드리의 파멸: 두꺼비 ${toads}마리 · 암울한 결과 ${doedreLevel}레벨")
        }

        val skillDps = spawns.sumOf { it.type.dpsShare * it.count }
        if (mainIsSummon && playerDps > 0.0 && skillDps > 0.0) {
            // 주력 스킬이 소환수라면 PoB가 계산한 DPS가 곧 소환수들의 몫이다.
            // 비율을 그대로 곱해 두 번 세지 않도록, 소환수 몫을 playerDps로 맞춘다.
            val scale = playerDps / skillDps
            val scaled = spawns.map { spawn ->
                MinionSpawn(spawn.type.copy(dpsShare = spawn.type.dpsShare * scale), spawn.count)
            }
            spawns.clear()
            spawns.addAll(scaled)
        }

        return MinionPlan(
            spawns = spawns,
            toadCount = toads,
            toadLevel = doedreLevel,
            darkConsequencesMin = darkMin,
            darkConsequencesMax = darkMax,
            darkConsequencesDps = darkDps,
            darkConsequencesCrit = critChance,
            spiritReserved = reserved,
            spiritBudget = budget,
            notes = notes,
        )
    }

    /** 도이드리의 파멸 보조 젬인지 */
    fun isDoedre(name: String): Boolean {
        val key = TriggerKind.normalizeName(name)
        if (key.isEmpty()) return false
        return DOEDRE_NAMES.any { alias ->
            val a = TriggerKind.normalizeName(alias)
            a.isNotEmpty() && (key == a || key.contains(a))
        }
    }

    /** 저주 젬인지 (도이드리의 파멸이 붙는 대상) */
    fun isCurseGem(name: String): Boolean {
        val key = TriggerKind.normalizeName(name)
        return listOf(
            "despair", "enfeeble", "temporal chains", "flammability", "hypothermia",
            "elemental weakness", "vulnerability", "conductivity", "frostbite",
            "절망", "쇠약", "시간의 사슬", "인화성", "저체온", "원소 약화", "취약성",
        ).any { key.contains(TriggerKind.normalizeName(it)) }
    }

    /** 암울한 결과가 붙은 그룹인지 (화면·테스트에서 확인용) */
    fun darkConsequencesGroup(build: PobBuild): PobSkillGroup? {
        for (group in build.skills) {
            if (!group.enabled) continue
            if (group.gems.any { it.enabled && isDoedre(it.name) }) return group
        }
        return null
    }

    /** 두꺼비는 저주받은 지대 하나에 4마리, 지대는 2개까지 */
    const val TOAD_PER_AREA = 4
    const val TOAD_AREAS = 2

    /** 두꺼비 한 마리가 도약해 터지기까지 걸리는 대략적인 시간(초) */
    const val TOAD_CYCLE = 0.9

    /** 화면·연산 부담을 위해 동시에 세우는 소환수 상한 */
    const val MAX_MINION_INSTANCES = 24
}
