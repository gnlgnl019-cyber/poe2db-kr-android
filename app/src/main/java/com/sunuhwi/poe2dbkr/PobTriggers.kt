package com.sunuhwi.poe2dbkr

/**
 * PoB의 메타젬 — 조건을 채우면 연결된 스킬이 대신 나가는 젬.
 * PoB 2는 <Skill> 그룹 안에 메타젬 + 발동 스킬을 함께 저장한다.
 */
enum class TriggerCondition {
    /** 치명타 적중 */
    CRIT,
    /** 감전 */
    SHOCK,
    /** 빙결 */
    FREEZE,
    /** 점화 */
    IGNITE,
    /** 처치 (전령류) */
    KILL,
    /** 소환수 사망 */
    MINION_DEATH,
    /** 플레이어 피격 */
    HIT_TAKEN,
    /** 플레이어 회피 */
    DODGE,
    /** 지속 오라 (신성모독 등) */
    AURA,
}

enum class TriggerKind(
    val label: String,
    val short: String,
    val condition: TriggerCondition,
    /** 발동 후 재사용 대기(초) */
    val cooldown: Float,
    /** 조건을 몇 번 채워야 한 번 발동하는지 */
    val energyNeeded: Double,
    private val names: List<String>,
    private val ids: List<String>,
    /** 전령·저주처럼 링크된 액티브 없이 젬 자체가 피해를 내는 경우 */
    val selfCasting: Boolean = false,
) {
    CAST_ON_CRIT(
        "치명타 시 시전", "CoC", TriggerCondition.CRIT, 0.35f, 4.0,
        listOf(
            "cast on critical strike", "cast on crit", "cast on critical", "치명타 시 시전",
        ),
        listOf("metacastoncrit", "castoncrit", "castoncritical", "castoncriticalstrike", "supportcastoncrit"),
    ),
    CAST_ON_SHOCK(
        "감전 시 시전", "감전", TriggerCondition.SHOCK, 0.5f, 3.0,
        listOf("cast on shock", "감전 시 시전"),
        listOf("metacastonshock", "castonshock"),
    ),
    CAST_ON_FREEZE(
        "빙결 시 시전", "빙결", TriggerCondition.FREEZE, 0.5f, 3.0,
        listOf("cast on freeze", "빙결 시 시전"),
        listOf("metacastonfreeze", "castonfreeze"),
    ),
    CAST_ON_IGNITE(
        "점화 시 시전", "점화", TriggerCondition.IGNITE, 0.5f, 3.0,
        listOf("cast on ignite", "점화 시 시전"),
        listOf("metacastsonignite", "metacastonignite", "castsonignite", "castonignite"),
    ),
    CAST_ON_ELEMENTAL_AILMENT(
        "원소 상태이상 시 시전", "원소", TriggerCondition.SHOCK, 0.45f, 3.0,
        listOf(
            "cast on elemental ailment", "elemental ailment", "원소 상태이상 시 시전",
            "원소 상태이상", "원소 시 시전",
        ),
        listOf("metacastelementalailment", "castonelementalailment", "castonelementalailments"),
    ),
    ELEMENTAL_INVOCATION(
        "원소 기원", "기원", TriggerCondition.SHOCK, 0.6f, 4.0,
        listOf("elemental invocation", "원소 기원", "원소 소환"),
        listOf("elementalinvocation", "metaelementalinvocation"),
    ),
    CAST_ON_MINION_DEATH(
        "소환수 사망 시 시전", "소환", TriggerCondition.MINION_DEATH, 0.5f, 1.0,
        listOf("cast on minion death", "소환수 사망 시 시전", "소환수 사망"),
        listOf("metacastonminiondeath", "castonminiondeath"),
    ),
    CAST_WHEN_DAMAGE_TAKEN(
        "피격 시 시전", "피격", TriggerCondition.HIT_TAKEN, 0.5f, 1.0,
        listOf("cast when damage taken", "cast on damage taken", "피격 시 시전", "피해 받을 시 시전"),
        listOf("metacastwhendamagetaken", "castwhendamagetaken", "castondamagetaken", "cwdt"),
    ),
    CAST_WHEN_STUNNED(
        "기절 시 시전", "기절", TriggerCondition.HIT_TAKEN, 0.8f, 2.0,
        listOf("cast when stunned", "cast on stun", "기절 시 시전"),
        listOf("metacastwhenstunned", "castwhenstunned", "castonstun"),
    ),
    CAST_ON_DODGE(
        "회피 시 시전", "회피", TriggerCondition.DODGE, 0.6f, 2.0,
        listOf("cast on dodge", "회피 시 시전", "회피 시 발동"),
        listOf("metacastondodge", "castondodge"),
    ),
    CAST_ON_MELEE_KILL(
        "근접 처치 시 시전", "근접처치", TriggerCondition.KILL, 0.5f, 2.0,
        listOf("cast on melee kill", "근접 처치 시 시전"),
        listOf("metacastonmeleekill", "castonmeleekill"),
    ),
    HERALD_OF_THUNDER(
        "천둥의 전령", "천둥", TriggerCondition.KILL, 0.45f, 1.0,
        listOf("herald of thunder", "천둥의 전령", "전령: 천둥", "천둥 전령"),
        listOf("heraldoflthunder", "heraldoflthunder2", "heraldofthunder", "metaheraldofthunder"),
        selfCasting = true,
    ),
    HERALD_OF_ICE(
        "얼음의 전령", "얼음", TriggerCondition.KILL, 0.45f, 1.0,
        listOf("herald of ice", "얼음의 전령", "전령: 얼음", "얼음 전령"),
        listOf("heraldofice", "metaheraldofice"),
        selfCasting = true,
    ),
    HERALD_OF_ASH(
        "재의 전령", "재", TriggerCondition.KILL, 0.45f, 1.0,
        listOf("herald of ash", "재의 전령", "전령: 재", "재 전령"),
        listOf("heraldofash", "metaheraldofash"),
        selfCasting = true,
    ),
    HERALD_OF_PLAGUE(
        "역병의 전령", "역병", TriggerCondition.KILL, 0.45f, 1.0,
        listOf("herald of plague", "역병의 전령", "전령: 역병", "역병 전령"),
        listOf("heraldofplague", "metaheraldofplague"),
        selfCasting = true,
    ),
    BLASPHEMY(
        "신성모독", "저주", TriggerCondition.AURA, 1.2f, 1.0,
        listOf("blasphemy", "신성모독", "모독"),
        listOf("blasphemy", "supportblasphemy", "metablasphemy"),
        selfCasting = false,
    ),
    ;

    /**
     * 전령 계열인지 — 전령은 [Heralds]가 따로 계산하므로 발동 장치 목록에서는 뺀다.
     * (둘 다 돌리면 같은 전령이 두 번 집계된다.)
     */
    val isHerald: Boolean get() = name.startsWith("HERALD_")

    /** 이 메타젬의 에너지를 채우는 사건들 (원소 상태이상 계열은 감전·빙결·점화를 모두 받는다) */
    val accepts: Set<TriggerCondition>
        get() = when (this) {
            CAST_ON_ELEMENTAL_AILMENT, ELEMENTAL_INVOCATION -> setOf(
                TriggerCondition.SHOCK, TriggerCondition.FREEZE, TriggerCondition.IGNITE,
            )
            else -> setOf(condition)
        }

    /** 젬 이름/스킬 id 로 이 메타젬인지 판별한다. 이름이 한글이어도 잡힌다. */
    fun matches(gem: PobGem): Boolean {        val name = normalizeName(gem.name)
        val id = normalizeId(gem.skillId)
        if (name.isEmpty() && id.isEmpty()) return false
        if (ids.any { id.isNotEmpty() && (id == it || id.contains(it)) }) return true
        return names.any { alias ->
            val key = normalizeName(alias)
            key.isNotEmpty() && (name == key || name.startsWith("$key ") || name.contains(key))
        }
    }

    companion object {
        /** 조건으로 다루는 상태이상 묶음 (원소 상태이상 시 시전이 감전·빙결·점화를 모두 받는다) */
        val AILMENTS = listOf(TriggerCondition.SHOCK, TriggerCondition.FREEZE, TriggerCondition.IGNITE)

        fun of(gem: PobGem): TriggerKind? = values().firstOrNull { it.matches(gem) }

        fun ofName(name: String): TriggerKind? = of(PobGem(name = name))

        fun normalizeName(value: String): String =
            value.lowercase()
                .replace(Regex("^\\d+x\\s+"), "")
                .replace(Regex("[^a-z0-9가-힣 ]"), " ")
                .replace(Regex("\\s+"), " ")
                .trim()

        fun normalizeId(value: String): String =
            value.lowercase().replace(Regex("[^a-z0-9]"), "")
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

    /** 추정 DPS를 만들 때 메타젬이 가져간다고 보는 대략적인 몫 */
    private const val ESTIMATED_SHARE = 0.20

    fun kindOf(gem: PobGem): TriggerKind? = TriggerKind.of(gem)

    /**
     * PoB 빌드에서 메타젬 그룹을 **모두** 뽑아낸다.
     * 첫 그룹만 쓰지 않고, 꺼지지 않은 모든 메타젬이 각자의 조건·에너지·쿨다운으로 전투에서 돈다.
     * mainDps는 PoB에 DPS 값이 없을 때 추정치를 만들 때만 쓴다.
     */
    fun extract(build: PobBuild, mainDps: Double): List<TriggerSpec> {
        val specs = ArrayList<TriggerSpec>()
        val fullByName = build.fullDpsSkills
            .groupBy { TriggerKind.normalizeName(it.name) }
            .mapValues { entry -> entry.value.sumOf { it.dps } }
        val totalDps = build.stats["TotalDPS"] ?: build.stats["CombinedDPS"] ?: 0.0
        var extra = build.fullDps - totalDps
        if (extra < 0.0) extra = 0.0
        val usedGroups = HashSet<PobSkillGroup>()

        for (group in build.skills) {
            if (!group.enabled) continue
            // 한 그룹에 메타젬이 여럿 들어 있을 수도 있으므로 전부 모은다.
            val triggerGems = group.gems.filter { it.enabled && TriggerKind.of(it) != null }
            if (triggerGems.isEmpty()) continue
            for (triggerGem in triggerGems) {
                val kind = TriggerKind.of(triggerGem) ?: continue
                // 전령은 [Heralds]가 따로 계산한다 — 여기서 또 잡으면 같은 전령이 두 번 집계되고,
                // 전투 화면에서 전령을 꺼도 이 목록 때문에 계속 터진다.
                if (kind.isHerald) continue
                val triggered = group.gems.filter { gem ->
                    gem.enabled && gem !== triggerGem &&
                        !PobCalculator.isSupport(gem) && TriggerKind.of(gem) == null
                }
                val sourceGems = if (triggered.isEmpty() && kind.selfCasting) listOf(triggerGem) else triggered
                if (sourceGems.isEmpty()) continue

                val matched = sourceGems.map { gem ->
                    gem.name to (fullByName[TriggerKind.normalizeName(gem.name)] ?: 0.0)
                }
                val matchedTotal = matched.sumOf { it.second }
                var estimated = false
                val perSkill: List<TriggeredSkill> = when {
                    matchedTotal > 0.0 -> sourceGems.map { gem ->
                        TriggeredSkill(gem.name, SkillEffects.detect(gem.name), fullByName[TriggerKind.normalizeName(gem.name)] ?: 0.0)
                    }
                    extra > 0.0 -> {
                        val each = extra / sourceGems.size
                        sourceGems.map { gem -> TriggeredSkill(gem.name, SkillEffects.detect(gem.name), each) }
                    }
                    mainDps > 0.0 -> {
                        estimated = true
                        val each = mainDps * ESTIMATED_SHARE / sourceGems.size
                        sourceGems.map { gem -> TriggeredSkill(gem.name, SkillEffects.detect(gem.name), each) }
                    }
                    else -> emptyList()
                }
                if (perSkill.isEmpty()) continue
                usedGroups.add(group)
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
        }
        return specs
    }

    /** 빌드에 메타젬이 없을 때 칩으로 확인해 보는 용도 — 여러 종류를 한꺼번에 돌려 본다. */
    fun synthetic(mainDps: Double): List<TriggerSpec> {
        val dps = if (mainDps > 0.0) mainDps * ESTIMATED_SHARE else 10_000.0
        val skill = { name: String, fx: SkillEffectType -> TriggeredSkill(name, fx, dps) }
        return listOf(
            TriggerSpec(
                kind = TriggerKind.CAST_ON_CRIT,
                triggerName = "Cast on Critical",
                groupLabel = "테스트",
                skills = listOf(skill("Comet", SkillEffectType.ICE)),
                dps = dps,
                estimated = true,
            ),
            TriggerSpec(
                kind = TriggerKind.CAST_ON_SHOCK,
                triggerName = "Cast on Shock",
                groupLabel = "테스트",
                skills = listOf(skill("Arc", SkillEffectType.LIGHTNING)),
                dps = dps * 0.8,
                estimated = true,
            ),
            TriggerSpec(
                kind = TriggerKind.HERALD_OF_ICE,
                triggerName = "Herald of Ice",
                groupLabel = "테스트",
                skills = listOf(skill("Herald of Ice", SkillEffectType.ICE)),
                dps = dps * 0.6,
                estimated = true,
            ),
            TriggerSpec(
                kind = TriggerKind.CAST_ON_IGNITE,
                triggerName = "Cast on Ignite",
                groupLabel = "테스트",
                skills = listOf(skill("Fireball", SkillEffectType.FIRE)),
                dps = dps * 0.7,
                estimated = true,
            ),
        )
    }
}
