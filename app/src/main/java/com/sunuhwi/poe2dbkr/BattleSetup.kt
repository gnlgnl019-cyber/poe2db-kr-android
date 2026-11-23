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
        /** 계산된 스킬별 DPS (있으면 PoB 값 대신 이 값을 쓴다) */
        dpsByName: Map<String, Double> = emptyMap(),
    ): List<BattleSkillOption> {
        if (build == null || limit <= 0) return emptyList()
        val main = Pob.mainGroup(build, 0)
        // 주력 그룹을 먼저 본다. 나머지는 PoB에 적힌 순서 그대로.
        val groups = build.skills.filter { it.enabled }.sortedBy { if (it === main) 0 else 1 }
        val out = LinkedHashMap<String, BattleSkillOption>()
        // 도이드리의 파멸처럼 보조 젬이 발동시키는 스킬은 젬 목록에 없으므로 맨 앞에 따로 올린다.
        if (PobCalculator.isDarkConsequences(build)) {
            val direct = dpsByName[TriggerKind.normalizeName(Minions.DARK_CONSEQUENCES_EN)]
            out[Minions.DARK_CONSEQUENCES_EN.lowercase()] = BattleSkillOption(
                gem = Minions.DARK_CONSEQUENCES_EN,
                name = dict.gemName(Minions.DARK_CONSEQUENCES_EN).ifBlank { Minions.DARK_CONSEQUENCES_KO },
                fx = SkillEffectType.CHAOS,
                dps = direct?.takeIf { it.isFinite() && it > 0.0 } ?: 0.0,
            )
        }
        for (group in groups) {
            for (gem in group.gems) {
                if (!gem.enabled) continue
                if (PobCalculator.isSupport(gem)) continue
                if (PobTriggers.kindOf(gem) != null) continue
                val key = gem.name.trim().lowercase()
                if (key.isEmpty() || out.containsKey(key)) continue
                val direct = dpsByName[TriggerKind.normalizeName(gem.name)]
                out[key] = BattleSkillOption(
                    gem = gem.name,
                    name = dict.gemName(gem.name).ifBlank { gem.name },
                    fx = SkillEffects.detect(gem.name, group.label),
                    dps = direct?.takeIf { it.isFinite() && it > 0.0 } ?: skillDps(build, gem.name),
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

    /**
     * 빌드에 같이 들어 있는 액티브 스킬 중 모션을 아는 것만 골라 콤보 재료로 넘긴다.
     * 소용돌이 베기·돌개바람·폭풍의 종·살상 장법처럼 준비 단계가 있는 스킬이 여기로 들어온다.
     */
    fun kitSkills(
        build: PobBuild?,
        dict: KrDict = KrDict.EMPTY,
        main: String = "",
        scale: Double = 1.0,
        limit: Int = 12,
        /** 계산기가 잡은 스킬별 DPS (있으면 PoB Full DPS 대신 쓴다) */
        dpsByName: Map<String, Double> = emptyMap(),
    ): List<KitSkill> {
        if (build == null || limit <= 0) return emptyList()
        val mainKey = main.trim().lowercase()
        val seen = HashSet<String>()
        val out = ArrayList<KitSkill>()
        for (group in build.skills) {
            if (!group.enabled) continue
            for (gem in group.gems) {
                if (!gem.enabled) continue
                if (PobCalculator.isSupport(gem)) continue
                if (PobTriggers.kindOf(gem) != null) continue
                val key = gem.name.trim().lowercase()
                if (key.isEmpty() || key == mainKey) continue
                if (!seen.add(key)) continue
                val motion = SkillMotions.detect(gem.name, group.label)
                if (motion == SkillMotion.DEFAULT) continue
                val own = dpsByName[TriggerKind.normalizeName(gem.name)] ?: skillDps(build, gem.name)
                val dps = (own * scale).takeIf { it.isFinite() && it >= 0.0 } ?: 0.0
                out.add(
                    KitSkill(
                        name = dict.gemName(gem.name).ifBlank { gem.name },
                        motion = motion,
                        dps = dps,
                        role = SkillMotions.roleOf(motion),
                    )
                )
                if (out.size >= limit) return out
            }
        }
        return out
    }

    /**
     * 공허의 형상 안에 넣은 근접 공격 스킬.
     * 상이 플레이어 대신 휘두르는 스킬이라, 상의 모션과 피해를 이 스킬로 잡는다.
     */
    fun hollowSocketSkill(
        build: PobBuild?,
        dict: KrDict = KrDict.EMPTY,
        scale: Double = 1.0,
        dpsByName: Map<String, Double> = emptyMap(),
    ): KitSkill? {
        if (build == null) return null
        for (group in build.skills) {
            if (!group.enabled) continue
            val label = group.label.lowercase()
            val isForm = group.gems.any { gem ->
                gem.enabled && (
                    gem.name.lowercase().contains("hollow form") ||
                        gem.name.contains("공허의 형상")
                    )
            } || label.contains("hollow form") || label.contains("공허의 형상")
            if (!isForm) continue
            for (gem in group.gems) {
                if (!gem.enabled) continue
                if (PobCalculator.isSupport(gem)) continue
                val key = gem.name.lowercase()
                if (key.contains("hollow") || gem.name.contains("공허")) continue
                val motion = SkillMotions.detect(gem.name, group.label)
                if (motion == SkillMotion.DEFAULT) continue
                val own = dpsByName[TriggerKind.normalizeName(gem.name)] ?: skillDps(build, gem.name)
                val dps = (own * scale).takeIf { it.isFinite() && it > 0.0 } ?: 0.0
                return KitSkill(
                    name = dict.gemName(gem.name).ifBlank { gem.name },
                    motion = motion,
                    dps = dps,
                    role = SkillMotions.roleOf(motion),
                )
            }
        }
        return null
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
        /** 전투 탭에서 짜 둔 콤보 순서 (있으면 이 순서대로 돈다) */
        combo: List<ComboStep> = emptyList(),
    ): BattlePlayerStats? {
        if (build == null) return null
        // 빌드에 찍힌 전직 노드를 먼저 모아 두고, 그 효과를 수치 계산과 전투 양쪽에 넘긴다.
        val ascend = AscendancyEffects.profile(build, tree)
        val unique = UniqueEffects.profile(build, overrides, dict)
        val calc = PobCalculator.compute(build, overrides, flasks, config, ascend, unique)
        val picked = activeSkill(build, skillOverride)
        val group = picked?.second ?: Pob.mainGroup(build, mainGroupPreference)
        val mainGem = picked?.first ?: mainGemOf(group)
        val dark = picked == null && PobCalculator.isDarkConsequences(build)
        val skillName = when {
            dark -> Minions.DARK_CONSEQUENCES_EN
            else -> mainGem?.name ?: group?.label?.ifBlank { null } ?: build.className.ifBlank { "기본 공격" }
        }
        val fx = if (dark) SkillEffectType.CHAOS else SkillEffects.detect(skillName, group?.label.orEmpty())
        val weaponItem = Pob.weaponItem(build, overrides)
        // 부적(Talisman) 장착·형태 변환 스킬로 야수 변신을 먼저 판정한다 (무기 형태가 여기에 달려 있다).
        val shapeNames = build.skills.filter { it.enabled }.flatMap { g ->
            g.gems.filter { it.enabled }.map { it.name }.toList() + g.label
        }
        val beastProfile = Beasts.profile(build, overrides, skillName, shapeNames, dict)
        val beast = beastProfile.form
        val weapon = if (beast.isBeast) {
            // 야수는 무기를 들지 않는다 — 발톱과 이빨로 싸운다.
            WeaponVisualType.CLAW
        } else {
            weaponItem?.weapon?.takeIf { it != WeaponVisualType.NONE }
                ?: WeaponTypes.detect(weaponItem?.itemClass, weaponItem?.baseType, weaponItem?.name)
                    .takeIf { it != WeaponVisualType.NONE }
                ?: WeaponTypes.fallbackFor(fx)
        }
        val form = if (beast.isBeast) {
            // 야수는 사람 무기를 휘두르지 않는다 — 와이번은 숨결을 뿜고 나머지는 달려들어 친다.
            if (beast == BeastForm.WYVERN) AttackForm.CAST else AttackForm.MELEE
        } else {
            WeaponTypes.attackForm(weapon, fx)
        }
        val skillKo = if (dark) {
            dict.gemName(skillName).ifBlank { Minions.DARK_CONSEQUENCES_KO }
        } else {
            dict.gemName(skillName).ifBlank { skillName }
        }
        val weaponLabel = listOf(
            weapon.label,
            dict.name(weaponItem?.name.orEmpty()).ifBlank { weaponItem?.name.orEmpty() },
            dict.base(weaponItem?.baseType.orEmpty()).ifBlank { weaponItem?.baseType.orEmpty() },
        ).filter { it.isNotBlank() }.distinct().joinToString(" · ")
        val scale = if (calc.dpsBase > 0) calc.dpsNow / calc.dpsBase else 1.0
        // 고른 스킬의 DPS가 PoB에 있으면 그 값을 쓴다. 없으면 빌드 주력 DPS로 둔다.
        // 고른 스킬의 DPS는 계산기가 낸 스킬별 값을 먼저 쓰고, 없으면 PoB Full DPS 목록을 본다.
        val pickedDps = picked?.first?.let { gem ->
            val key = TriggerKind.normalizeName(gem.name)
            calc.skillDps.firstOrNull { TriggerKind.normalizeName(it.name) == key }
                ?.dps?.takeIf { it.isFinite() && it > 0.0 }
                ?: skillDps(build, gem.name).takeIf { it > 0.0 }
        }
        val dpsNow = if (dark) calc.dpsNow else pickedDps?.times(scale) ?: calc.dpsNow
        // 스킬별로 계산기가 잡아 둔 DPS (발동·콤보 젬도 주력 상한에 휩쓸리지 않게 한다)
        val skillDpsByName = calc.skillDps
            .filter { it.dps.isFinite() && it.dps > 0.0 }
            .associate { TriggerKind.normalizeName(it.name) to it.dps }
        val triggers = when (triggerMode) {
            TriggerMode.OFF -> emptyList()
            TriggerMode.TEST -> PobTriggers.synthetic(dpsNow)
            TriggerMode.AUTO -> {
                // 무한대 빌드는 주력이 상한(2.1B)이라, 그 값을 메타젬 몫으로 나누면
                // 발동 스킬까지 수억 대가 된다. 스킬별로 잡아 둔 제 몫이 있으면 그것을 쓴다.
                PobTriggers.extract(build, dpsNow).map { spec ->
                    val skills = spec.skills.map { skill ->
                        val own = if (calc.dpsCapped) {
                            skillDpsByName[TriggerKind.normalizeName(skill.name)]
                        } else null
                        skill.copy(dps = (own ?: skill.dps) * scale)
                    }
                    spec.copy(dps = skills.sumOf { it.dps }, skills = skills)
                }
            }
        }
        val evade = (calc.evasionNow / (calc.evasionNow + 7500.0) * 92.0).coerceAtMost(80.0)
        val res = calc.resists
        // 500마리처럼 큰 무리를 세워도 정신력·자리 문제로 모두 나오지는 않는다.
        val minions = Minions.plan(
            build = build,
            stats = build.stats,
            mainIsSummon = Minions.typeOf(skillName) != null && !dark,
            playerDps = dpsNow,
            darkHitOverride = build.stats["AverageHit"] ?: 0.0,
            darkDpsOverride = if (calc.dpsCapped) calc.dpsNow else 0.0,
            critChance = calc.critNow.takeIf { it > 0 } ?: 7.0,
            critMulti = calc.critMultiNow.takeIf { it > 100 } ?: 150.0,
            minionDamagePct = unique.sim.minionDamagePct,
        )
        // 두꺼비 빌드에서 플레이어가 직접 때리는 스킬(철퇴·선회 등)의 DPS.
        // 암울한 결과 DPS를 플레이어 평타에 또 쓰면 두 번 세게 되므로 따로 잡는다.
        val ownDps = calc.skillDps
            .filter { TriggerKind.normalizeName(it.name) != TriggerKind.normalizeName(Minions.DARK_CONSEQUENCES_EN) }
            .maxOfOrNull { it.dps } ?: 0.0
        val kit = kitSkills(build, dict, skillName, scale, dpsByName = skillDpsByName)
        // 충전 운용(전투 격분·공명·충전 조절 등)은 젬·패시브·전직·고유 아이템 글에서 읽는다.
        val chargeLines = ArrayList<String>()
        for (group in build.skills) {
            if (!group.enabled) continue
            if (group.label.isNotBlank()) chargeLines.add(group.label)
            for (gem in group.gems) if (gem.enabled) chargeLines.add(gem.name)
        }
        ascend?.nodes?.forEach { node ->
            chargeLines.add(node.nameKo)
            chargeLines.add(node.nameEn)
            node.lines.forEach { chargeLines.add(it.text) }
        }
        unique.effects.forEach { chargeLines.add(it) }
        for (id in build.treeNodeIds) {
            val node = tree.nodes[id] ?: continue
            chargeLines.add(node.nameKo)
            chargeLines.add(node.nameEn)
            chargeLines.add(node.stats)
        }
        val charges = Charges.read(chargeLines)
        // 전령은 소환수가 먼저 가져간 정신력을 빼고 남은 예산으로 켜진다.
        val heralds = Heralds.plan(build, dict, minions.spiritReserved)
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
            dpsEstimated = pickedDps == null && calc.dpsEstimated && !dark,
            dpsInfinite = pickedDps == null && calc.dpsInfinite && !dark,
            dpsCapped = pickedDps == null && calc.dpsCapped,
            ascendName = ascend?.ascendKo.orEmpty(),
            ascendSummary = ascend?.summary.orEmpty(),
            ascendNodes = ascend?.keyNames.orEmpty(),
            ascendApplied = calc.ascendApplied,
            ascendSkills = ascend?.skills.orEmpty(),
            extraProjectiles = ascend?.sim?.extraProjectiles ?: 0,
            // 전기불꽃처럼 젬 레벨마다 발사 수가 늘어나는 스킬에 쓴다.
            skillLevel = mainGem?.level?.takeIf { it in 1..40 } ?: 20,
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
            motion = SkillMotions.detect(skillName, group?.label.orEmpty(), skillKo),
            kit = kit,
            charges = charges,
            beast = beast,
            beastProfile = beastProfile,
            combo = combo,
            oilSkill = SkillMotions.detect(skillName, group?.label.orEmpty(), skillKo) == SkillMotion.OIL_BARRAGE ||
                kit.any { it.motion == SkillMotion.OIL_BARRAGE || it.motion == SkillMotion.OIL_GRENADE },
            hollowSkill = hollowSocketSkill(build, dict, scale, dpsByName = skillDpsByName),
            minions = minions,
            heralds = heralds,
            unique = unique.sim,
            uniqueSummary = unique.summary,
            uniqueEffects = unique.effects,
            uniqueApplied = unique.applied.size,
            playerDps = if (dark) ownDps else -1.0,
            playerCarriesDps = if (dark) ownDps > 0.0 else Minions.typeOf(skillName) == null,
            skillDpsList = calc.skillDps,
            totalDps = calc.totalDps,
        )
    }
}
