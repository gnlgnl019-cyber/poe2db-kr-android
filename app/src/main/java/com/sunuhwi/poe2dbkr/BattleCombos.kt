package com.sunuhwi.poe2dbkr

import android.content.Context

/**
 * 전투 탭 콤보 설정.
 *
 * 빌드에 장착된 스킬을 원하는 순서로 늘어놓고, 같은 스킬을 여러 번 넣을 수도 있다.
 * 설정한 순서는 [BattleWorld]가 그대로 돌린다 (쿨·연계가 모자라면 그 칸에서 기다린다).
 */
data class ComboPick(
    /** 스킬 젬 이름 (같은 이름을 여러 번 넣으므로 항목 구분은 순서로 한다) */
    val gem: String,
    /** 화면에 보여줄 이름 */
    val label: String,
    val motion: SkillMotion,
    val role: ComboRole,
    /** 이 칸을 몇 번 반복할지 */
    val repeats: Int = 1,
    val note: String = "",
    /** PoB·계산기가 잡은 이 스킬의 DPS (없으면 0) */
    val dps: Double = 0.0,
)

object BattleCombos {

    /** 콤보에 넣을 수 있는 스킬 후보 — 빌드의 액티브 스킬 전부 */
    fun options(
        build: PobBuild?,
        dict: KrDict = KrDict.EMPTY,
        dpsByName: Map<String, Double> = emptyMap(),
        limit: Int = 12,
    ): List<ComboPick> {
        val list = ArrayList<ComboPick>()
        // 도이드리의 파멸(암울한 결과)은 젬 목록에 없으니 따로 넣는다.
        if (build != null && PobCalculator.isDarkConsequences(build)) {
            list.add(
                ComboPick(
                    gem = Minions.DARK_CONSEQUENCES_EN,
                    label = dict.gemName(Minions.DARK_CONSEQUENCES_EN).ifBlank { Minions.DARK_CONSEQUENCES_KO },
                    motion = SkillMotion.CURSE,
                    role = ComboRole.CURSE,
                    note = "저주 · 두꺼비",
                    dps = dpsByName[TriggerKind.normalizeName(Minions.DARK_CONSEQUENCES_EN)] ?: 0.0,
                )
            )
        }
        for (group in build?.skills.orEmpty()) {
            if (!group.enabled) continue
            for (gem in group.gems) {
                if (!gem.enabled) continue
                if (PobCalculator.isSupport(gem)) continue
                if (PobTriggers.kindOf(gem) != null) continue
                val motion = SkillMotions.detect(gem.name, group.label)
                val label = dict.gemName(gem.name).ifBlank { gem.name }
                if (list.any { it.label == label && it.motion == motion }) continue
                list.add(
                    ComboPick(
                        gem = gem.name,
                        label = label,
                        motion = motion,
                        role = SkillMotions.roleOf(motion),
                        dps = dpsByName[TriggerKind.normalizeName(gem.name)]
                            ?: build?.let { BattleSetup.skillDps(it, gem.name) } ?: 0.0,
                    )
                )
                if (list.size >= limit) return list
            }
        }
        return list
    }

    /** 설정한 콤보를 봇이 쓰는 순서로 바꾼다 */
    fun toSteps(picks: List<ComboPick>): List<ComboStep> = picks.map { pick ->
        ComboStep(
            label = pick.label,
            motion = pick.motion,
            role = pick.role,
            repeats = pick.repeats.coerceIn(1, 9),
            note = pick.note,
            dps = pick.dps,
        )
    }

    /** 빌드에서 뽑은 자동 추천 콤보 (준비 → 마무리 순서) */
    fun autoPicks(stats: BattlePlayerStats, options: List<ComboPick>): List<ComboPick> {
        val plan = ComboPlanner.plan(stats.motion, stats.kit)
        val out = ArrayList<ComboPick>()
        plan.forEach { step ->
            val opt = options.firstOrNull { it.motion == step.motion && it.label == step.label }
                ?: options.firstOrNull { it.motion == step.motion }
            out.add(
                ComboPick(
                    gem = opt?.gem ?: step.label,
                    label = step.label,
                    motion = step.motion,
                    role = step.role,
                    repeats = step.repeats.coerceIn(1, 9),
                    note = step.note,
                    dps = opt?.dps ?: step.dps,
                )
            )
        }
        return out
    }

    /** 화면에 한 줄로 보여줄 콤보 */
    fun summary(picks: List<ComboPick>): String =
        picks.joinToString(" → ") { pick ->
            pick.label + if (pick.repeats > 1) " x${pick.repeats}" else ""
        }

    /**
     * 저장 형식: 한 줄에 한 칸.
     * 제목에 구분자로 쓰는 문자(탭·줄바꿈)는 스킬 이름에 나오지 않는다.
     */
    fun encode(picks: List<ComboPick>): String = picks.joinToString("\n") { pick ->
        listOf(
            pick.gem.replace("\u001f", " ").replace("\n", " "),
            pick.label.replace("\u001f", " ").replace("\n", " "),
            pick.motion.name,
            pick.role.name,
            pick.repeats.toString(),
            pick.note.replace("\u001f", " ").replace("\n", " "),
            if (pick.dps.isFinite()) pick.dps.toString() else "0",
        ).joinToString("\u001f")
    }

    fun decode(text: String, options: List<ComboPick>): List<ComboPick> {
        if (text.isBlank()) return emptyList()
        val out = ArrayList<ComboPick>()
        text.split("\n").forEach { line ->
            val parts = line.split("\u001f")
            if (parts.size < 5) return@forEach
            val motion = runCatching { SkillMotion.valueOf(parts[2]) }.getOrNull() ?: return@forEach
            val role = runCatching { ComboRole.valueOf(parts[3]) }.getOrNull() ?: SkillMotions.roleOf(motion)
            val repeats = parts[4].toIntOrNull()?.coerceIn(1, 9) ?: 1
            val gem = parts[0]
            val label = parts[1].ifBlank { parts[0] }
            // 지금 빌드에 없는 스킬은 콤보에서 뺀다 (다른 빌드를 불러왔을 때).
            val opt = options.firstOrNull { it.gem == gem && it.motion == motion }
                ?: options.firstOrNull { it.label == label && it.motion == motion }
                ?: options.firstOrNull { it.motion == motion }
            if (options.isNotEmpty() && opt == null) return@forEach
            out.add(
                ComboPick(
                    gem = opt?.gem ?: gem,
                    label = opt?.label ?: label,
                    motion = motion,
                    role = role,
                    repeats = repeats,
                    note = parts.getOrNull(5).orEmpty(),
                    dps = opt?.dps ?: parts.getOrNull(6)?.toDoubleOrNull() ?: 0.0,
                )
            )
        }
        return out
    }
}

/** 콤보 설정을 기기에 저장한다 (전투 탭을 다시 열어도 남는다) */
class ComboStore(context: Context) {

    private val prefs = context.getSharedPreferences("battle_combo", Context.MODE_PRIVATE)

    fun load(options: List<ComboPick>): List<ComboPick> =
        BattleCombos.decode(prefs.getString(KEY, "").orEmpty(), options)

    fun save(picks: List<ComboPick>) {
        prefs.edit().putString(KEY, BattleCombos.encode(picks)).apply()
    }

    fun clear() {
        prefs.edit().remove(KEY).apply()
    }

    private companion object {
        const val KEY = "steps"
    }
}
