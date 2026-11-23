package com.sunuhwi.poe2dbkr

/**
 * 빌드 → 전투 수치 변환.
 *
 * 화면(MainActivity)은 이 결과만 받아 캔버스에 넘기므로, 불러오기·시뮬레이션 경로를
 * JVM 테스트에서 그대로 돌려 볼 수 있게 계산만 따로 떼어 둔다.
 */
enum class TriggerMode { AUTO, TEST, OFF }

/** 전투 탭 스킬 칩 하나 — 빌드에 실제로 들어 있는 액티브 스킬 */
data class BattleSkillOption(
    /** PoB 젬 이름. 칩을 고르면 이 값을 그대로 넘긴다. */
    val gem: String,
    /** 화면에 보여줄 한국어 이름 */
    val name: String,
    val fx: SkillEffectType,
    /** PoB가 계산해 둔 이 스킬의 DPS (PoB에 값이 없으면 0) */
    val dps: Double,
)

object BattleSetup {

    /** 전투 탭 스킬 칩은 주력 스킬까지 5개면 충분하다 */
    const val SKILL_CHIP_LIMIT = 5

    /**
     * 전투 탭 스킬 칩에 올릴 스킬 목록.
     *
     * 불러온 빌드에서 켜져 있는 스킬 그룹의 젬 중 보조 젬과 메타젬을 뺀 액티브 스킬만 고르고,
     * 주력 스킬 그룹을 앞에 둔다. 빌드가 없으면 빈 목록이라 '자동' 칩만 남는다.
     */
    fun skillOptions(
        build: PobBuild?,
        dict: KrDict = KrDict.EMPTY,
        limit: Int = SKILL_CHIP_LIMIT,
    ): List<BattleSkillOption> {
        if (build == null || limit <= 0) return emptyList()
        val main = Pob.mainGroup(build, 0)
        // 주력 그룹을 먼저 본다. 나머지는 PoB에 적힌 순서 그대로.
        val groups = build.skills.filter { it.enabled }.sortedBy { if (it === main) 0 else 1 }
        val out = LinkedHashMap<String, BattleSkillOption>()
        for (group in groups) {
            for (gem in group.gems) {
                if (!gem.enabled) continue
                if (PobCalculator.isSupport(gem)) continue
                if (PobTriggers.kindOf(gem) != null) continue
                val key = gem.name.trim().lowercase()
                if (key.isEmpty() || out.containsKey(key)) continue
                out[key] = BattleSkillOption(
                    gem = gem.name,
                    name = dict.gemName(gem.name).ifBlank { gem.name },
                    fx = SkillEffects.detect(gem.name, group.label),
                    dps = skillDps(build, gem.name),
                )
                if (out.size >= limit) return out.values.toList()
            }
        }
        return out.values.toList()
    }

    /** PoB Full DPS 목록에서 그 젬의 DPS를 찾는다 (없으면 0) */
    fun skillDps(build: PobBuild, gemName: String): Double {
        val key = TriggerKind.normalizeName(gemName)
        if (key.isEmpty()) return 0.0
        return build.fullDpsSkills
            .filter { TriggerKind.normalizeName(it.name) == key }
            .sumOf { it.dps }
    }

    /** 빌드에서 켜져 있는 그 액티브 스킬(과 그 그룹). 보조 젬·메타젬은 고르지 않는다. */
    private fun activeSkill(build: PobBuild, name: String?): Pair<PobGem, PobSkillGroup>? {
        val key = name?.trim()?.lowercase().orEmpty()
        if (key.isEmpty()) return null
        for (group in build.skills) {
            if (!group.enabled) continue
            for (gem in group.gems) {
                if (!gem.enabled) continue
                if (gem.name.trim().lowercase() != key) continue
                if (PobCalculator.isSupport(gem)) continue
                if (PobTriggers.kindOf(gem) != null) continue
                return gem to group
            }
        }
        return null
    }

    /**
     * 그룹에서 PoB가 주력으로 삼은 스킬.
     * mainActiveSkill은 그룹 안 젬 순서(1부터)를 가리키므로, 보조 젬이 걸리면 첫 액티브 스킬로 물러난다.
     */
    private fun mainGemOf(group: PobSkillGroup?): PobGem? {
        val gems = group?.gems?.filter { it.enabled }.orEmpty()
        val actives = gems.filter { !PobCalculator.isSupport(it) }
        val wanted = group?.mainActiveSkill ?: 0
        if (wanted > 0) {
            gems.getOrNull(wanted - 1)?.takeIf { !PobCalculator.isSupport(it) }?.let { return it }
            actives.getOrNull(wanted - 1)?.let { return it }
        }
        return actives.firstOrNull { PobTriggers.kindOf(it) == null }
            ?: actives.firstOrNull()
            ?: gems.firstOrNull()
    }

    fun statsFor(
        build: PobBuild?,
        overrides: Map<String, PobItem> = emptyMap(),
        flasks: Set<String> = emptySet(),
        config: PobCalculator.Config = PobCalculator.Config(),
        mainGroupPreference: Int = 0,
        /** 전투 탭에서 직접 고른 스킬 젬 이름. null이면 빌드 주력 스킬을 쓴다. */
        skillOverride: String? = null,
        triggerMode: TriggerMode = TriggerMode.AUTO,
        dict: KrDict = KrDict.EMPTY,
        /** 전직 노드 효과를 찾을 패시브 트리 자료 */
        tree: TreeDict = TreeDict.EMPTY,
    ): BattlePlayerStats? {
        if (build == null) return null
        // 빌드에 찍힌 전직 노드를 먼저 모아 두고, 그 효과를 수치 계산과 전투 양쪽에 넘긴다.
        val ascend = AscendancyEffects.profile(build, tree)
        val calc = PobCalculator.compute(build, overrides, flasks, config, ascend)
        val picked = activeSkill(build, skillOverride)
        val group = picked?.second ?: Pob.mainGroup(build, mainGroupPreference)
        val mainGem = picked?.first ?: mainGemOf(group)
        val skillName = mainGem?.name ?: group?.label?.ifBlank { null } ?: build.className.ifBlank { "기본 공격" }
        val fx = SkillEffects.detect(skillName, group?.label.orEmpty())
        val weaponItem = Pob.weaponItem(build, overrides)
        val weapon = weaponItem?.weapon?.takeIf { it != WeaponVisualType.NONE }
            ?: WeaponTypes.detect(weaponItem?.itemClass, weaponItem?.baseType, weaponItem?.name)
                .takeIf { it != WeaponVisualType.NONE }
            ?: WeaponTypes.fallbackFor(fx)
        val form = WeaponTypes.attackForm(weapon, fx)
        val skillKo = dict.gemName(skillName).ifBlank { skillName }
        val weaponLabel = listOf(
            weapon.label,
            dict.name(weaponItem?.name.orEmpty()).ifBlank { weaponItem?.name.orEmpty() },
            dict.base(weaponItem?.baseType.orEmpty()).ifBlank { weaponItem?.baseType.orEmpty() },
        ).filter { it.isNotBlank() }.distinct().joinToString(" · ")
        val scale = if (calc.dpsBase > 0) calc.dpsNow / calc.dpsBase else 1.0
        // 고른 스킬의 DPS가 PoB에 있으면 그 값을 쓴다. 없으면 빌드 주력 DPS로 둔다.
        val pickedDps = picked?.first?.let { skillDps(build, it.name) }?.takeIf { it > 0 }
        val dpsNow = pickedDps?.times(scale) ?: calc.dpsNow
        val triggers = when (triggerMode) {
            TriggerMode.OFF -> emptyList()
            TriggerMode.TEST -> PobTriggers.synthetic(dpsNow)
            TriggerMode.AUTO -> PobTriggers.extract(build, dpsNow).map { spec ->
                spec.copy(
                    dps = spec.dps * scale,
                    skills = spec.skills.map { skill -> skill.copy(dps = skill.dps * scale) },
                )
            }
        }
        val evade = (calc.evasionNow / (calc.evasionNow + 7500.0) * 92.0).coerceAtMost(80.0)
        val res = calc.resists
        return BattlePlayerStats(
            className = listOf(build.className, build.ascendClassName)
                .filter { it.isNotBlank() }.joinToString(" · "),
            skillName = skillKo,
            fx = fx,
            weapon = weapon,
            form = form,
            dps = dpsNow,
            aps = calc.apsNow,
            critChance = calc.critNow,
            critMulti = calc.critMultiNow,
            life = calc.lifeNow,
            es = calc.esNow,
            physRed = calc.physRedNow,
            evade = evade,
            fireRes = res.getOrNull(0)?.third ?: 0.0,
            coldRes = res.getOrNull(1)?.third ?: 0.0,
            lightRes = res.getOrNull(2)?.third ?: 0.0,
            chaosRes = res.getOrNull(3)?.third ?: 0.0,
            ehp = calc.ehpNow,
            dpsBase = calc.dpsBase,
            weaponLabel = weaponLabel,
            triggers = triggers,
            dpsEstimated = pickedDps == null && calc.dpsEstimated,
            dpsInfinite = pickedDps == null && calc.dpsInfinite,
            ascendName = ascend?.ascendKo.orEmpty(),
            ascendSummary = ascend?.summary.orEmpty(),
            ascendNodes = ascend?.keyNames.orEmpty(),
            ascendApplied = calc.ascendApplied,
            ascendSkills = ascend?.skills.orEmpty(),
            extraProjectiles = ascend?.sim?.extraProjectiles ?: 0,
            aoePct = calc.ascendAoePct,
            shockChancePct = ascend?.sim?.shockChancePct ?: 0.0,
            freezeChancePct = ascend?.sim?.freezeChancePct ?: 0.0,
            igniteChancePct = ascend?.sim?.igniteChancePct ?: 0.0,
            ailmentMagnitudePct = ascend?.sim?.ailmentMagnitudePct ?: 0.0,
            damageTakenLessPct = ascend?.sim?.damageTakenLessPct ?: 0.0,
            physTakenAsFirePct = ascend?.sim?.physTakenAsFire ?: 0.0,
            physTakenAsColdPct = ascend?.sim?.physTakenAsCold ?: 0.0,
            physTakenAsLightningPct = ascend?.sim?.physTakenAsLightning ?: 0.0,
            physTakenAsChaosPct = ascend?.sim?.physTakenAsChaos ?: 0.0,
            evasionAsElementalPct = ascend?.sim?.evasionAsElementalPct ?: 0.0,
            esRechargeDelay = calc.esRechargeDelay,
            esRechargeRatePct = ascend?.sim?.esRechargeRatePct ?: 0.0,
            lifeRegenPct = calc.ascendLifeRegenPct,
            lifeLeechPct = calc.ascendLifeLeechPct,
            metaEnergyPct = ascend?.sim?.metaEnergyPct ?: 0.0,
            cooldownRatePct = ascend?.sim?.cooldownPct ?: 0.0,
        )
    }
}
