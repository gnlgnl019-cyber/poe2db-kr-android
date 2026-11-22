package com.sunuhwi.poe2dbkr

/**
 * PoB의 트리거(메타) 젬 — 치명타 시 시전처럼 조건을 채우면 연결된 스킬이 대신 나가는 그룹.
 * PoB 2는 <Skill> 그룹 안에 메타 젬 + 발동 스킬을 함께 저장한다.
 */
enum class TriggerCondition { CRIT, SHOCK, FREEZE, IGNITE, MINION_DEATH }

enum class TriggerKind(
    val label: String,
    val short: String,
    val condition: TriggerCondition,
    private val names: List<String>,
    private val ids: List<String>,
) {
    CAST_ON_CRIT(
        "치명타 시 시전", "CoC", TriggerCondition.CRIT,
        listOf("cast on critical", "cast on crit", "치명타 시 시전"),
        listOf("metacastoncrit", "castoncrit", "castoncritical"),
    ),
    CAST_ON_SHOCK(
        "감전 시 시전", "감전", TriggerCondition.SHOCK,
        listOf("cast on shock", "감전 시 시전"),
        listOf("metacastonshock", "castonshock"),
    ),
    CAST_ON_FREEZE(
        "빙결 시 시전", "빙결", TriggerCondition.FREEZE,
        listOf("cast on freeze", "빙결 시 시전"),
        listOf("metacastonfreeze", "castonfreeze"),
    ),
    CAST_ON_IGNITE(
        "점화 시 시전", "점화", TriggerCondition.IGNITE,
        listOf("cast on ignite", "점화 시 시전"),
        listOf("metacastsonignite", "metacastonignite", "castsonignite", "castonignite"),
    ),
    CAST_ON_MINION_DEATH(
        "소환수 사망 시 시전", "소환", TriggerCondition.MINION_DEATH,
        listOf("cast on minion death", "소환수 사망 시 시전"),
        listOf("metacastonminiondeath", "castonminiondeath"),
    ),
    ;

    fun matches(gem: PobGem): Boolean {
        val name = gem.name.lowercase().replace("_", " ").replace("-", " ").trim()
        val id = gem.skillId.lowercase().replace("_", "")
        return names.any { name == it || name.startsWith("$it ") } ||
            ids.any { id.contains(it) }
    }

    companion object {
        fun of(gem: PobGem): TriggerKind? = values().firstOrNull { it.matches(gem) }
    }
}

/** 트리거로 발동되는 스킬 하나 */
data class TriggeredSkill(
    val name: String,
    val fx: SkillEffectType,
    val dps: Double,
) {
    val dpsPerCastFallback: Double get() = dps
}

/** 트리거 그룹 하나 (종류 + 연결된 발동 스킬들 + DPS) */
data class TriggerSpec(
    val kind: TriggerKind,
    val triggerName: String,
    val groupLabel: String,
    val skills: List<TriggeredSkill>,
    val dps: Double,
    /** PoB 값이 없어 추정한 DPS인지 */
    val estimated: Boolean = false,
) {
    val skillNames: String get() = skills.joinToString(" · ") { it.name }
    val summary: String get() = "${kind.label}: $skillNames"
}

object PobTriggers {

    fun kindOf(gem: PobGem): TriggerKind? = TriggerKind.of(gem)

    /** PoB 빌드에서 트리거 그룹을 모두 뽑아낸다. mainDps는 추정치를 만들 때만 쓴다. */
    fun extract(build: PobBuild, mainDps: Double): List<TriggerSpec> {        val specs = ArrayList<TriggerSpec>()
        val fullByName = build.fullDpsSkills
            .groupBy { normalize(it.name) }
            .mapValues { entry -> entry.value.sumOf { it.dps } }
        val totalDps = build.stats["TotalDPS"] ?: build.stats["CombinedDPS"] ?: 0.0
        var extra = build.fullDps - totalDps
        if (extra < 0.0) extra = 0.0

        for (group in build.skills) {
            if (!group.enabled) continue
            val triggerGem: PobGem? = group.gems.firstOrNull { it.enabled && TriggerKind.of(it) != null }
            if (triggerGem == null) continue
            val kind = TriggerKind.of(triggerGem) ?: continue
            val triggered = group.gems.filter { gem ->
                gem.enabled && gem !== triggerGem &&
                    !PobCalculator.isSupport(gem) && TriggerKind.of(gem) == null
            }
            if (triggered.isEmpty()) continue

            var estimated = false
            val matched = triggered.map { gem ->
                gem.name to (fullByName[normalize(gem.name)] ?: 0.0)
            }
            val matchedTotal = matched.sumOf { it.second }
            val perSkill: List<TriggeredSkill> = when {
                matchedTotal > 0.0 -> triggered.map { gem ->
                    TriggeredSkill(gem.name, SkillEffects.detect(gem.name), fullByName[normalize(gem.name)] ?: 0.0)
                }
                extra > 0.0 -> {
                    val each = extra / triggered.size
                    triggered.map { gem -> TriggeredSkill(gem.name, SkillEffects.detect(gem.name), each) }
                }
                mainDps > 0.0 -> {
                    estimated = true
                    val each = mainDps * ESTIMATED_SHARE / triggered.size
                    triggered.map { gem -> TriggeredSkill(gem.name, SkillEffects.detect(gem.name), each) }
                }
                else -> emptyList()
            }
            if (perSkill.isEmpty()) continue
            specs.add(
                TriggerSpec(
                    kind = kind,
                    triggerName = triggerGem.name,
                    groupLabel = group.title,
                    skills = perSkill,
                    dps = perSkill.sumOf { it.dps },
                    estimated = estimated,
                )
            )
        }
        return specs
    }

    /** 빌드에 트리거가 없을 때 칩으로 확인해 보는 용도 */
    fun synthetic(mainDps: Double): List<TriggerSpec> {
        val dps = if (mainDps > 0.0) mainDps * ESTIMATED_SHARE else 1000.0
        return listOf(
            TriggerSpec(
                kind = TriggerKind.CAST_ON_CRIT,
                triggerName = "Cast on Critical",
                groupLabel = "테스트",
                skills = listOf(TriggeredSkill("Comet", SkillEffectType.ICE, dps)),
                dps = dps,
                estimated = true,
            )
        )
    }

    /** PoB 값이 없을 때 트리거가 차지한다고 보는 대략적인 몫 */
    private const val ESTIMATED_SHARE = 0.20

    private fun normalize(name: String): String =
        name.lowercase()
            .replace(Regex("^\\d+x\\s+"), "")
            .replace(Regex("[^a-z0-9가-힣]"), "")
}
