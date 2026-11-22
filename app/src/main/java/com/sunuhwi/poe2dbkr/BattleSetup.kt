package com.sunuhwi.poe2dbkr

/**
 * 빌드 → 전투 수치 변환.
 *
 * 화면(MainActivity)은 이 결과만 받아 캔버스에 넘기므로, 불러오기·시뮬레이션 경로를
 * JVM 테스트에서 그대로 돌려 볼 수 있게 계산만 따로 떼어 둔다.
 */
enum class TriggerMode { AUTO, TEST, OFF }

object BattleSetup {

    fun statsFor(
        build: PobBuild?,
        overrides: Map<String, PobItem> = emptyMap(),
        flasks: Set<String> = emptySet(),
        config: PobCalculator.Config = PobCalculator.Config(),
        mainGroupPreference: Int = 0,
        fxOverride: SkillEffectType? = null,
        triggerMode: TriggerMode = TriggerMode.AUTO,
        dict: KrDict = KrDict.EMPTY,
    ): BattlePlayerStats? {
        if (build == null) return null
        val calc = PobCalculator.compute(build, overrides, flasks, config)
        val group = Pob.mainGroup(build, mainGroupPreference)
        val gems = group?.gems?.filter { it.enabled }.orEmpty()
        val mainGem = gems.firstOrNull { !PobCalculator.isSupport(it) && PobTriggers.kindOf(it) == null }
            ?: gems.firstOrNull { !PobCalculator.isSupport(it) }
            ?: gems.firstOrNull()
        val skillName = mainGem?.name ?: group?.label?.ifBlank { null } ?: build.className.ifBlank { "기본 공격" }
        val fx = fxOverride ?: SkillEffects.detect(skillName, group?.label.orEmpty())
        val weaponItem = Pob.weaponItem(build, overrides)
        val weapon = weaponItem?.weapon?.takeIf { it != WeaponVisualType.NONE }
            ?: WeaponTypes.detect(weaponItem?.itemClass, weaponItem?.baseType, weaponItem?.name)
                .takeIf { it != WeaponVisualType.NONE }
            ?: WeaponTypes.fallbackFor(fx)
        val form = WeaponTypes.attackForm(weapon, fx)
        val skillKo = dict.name(skillName).ifBlank { skillName }
        val weaponLabel = listOf(
            weapon.label,
            dict.name(weaponItem?.name.orEmpty()).ifBlank { weaponItem?.name.orEmpty() },
            dict.base(weaponItem?.baseType.orEmpty()).ifBlank { weaponItem?.baseType.orEmpty() },
        ).filter { it.isNotBlank() }.distinct().joinToString(" · ")
        val scale = if (calc.dpsBase > 0) calc.dpsNow / calc.dpsBase else 1.0
        val triggers = when (triggerMode) {
            TriggerMode.OFF -> emptyList()
            TriggerMode.TEST -> PobTriggers.synthetic(calc.dpsNow)
            TriggerMode.AUTO -> PobTriggers.extract(build, calc.dpsNow).map { spec ->
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
            dps = calc.dpsNow,
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
        )
    }
}
