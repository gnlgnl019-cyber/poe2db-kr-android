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

    /** 기본값은 제한 없음 — 빌드의 액티브 스킬을 하나도 빠뜨리지 않는다 */
    const val NO_LIMIT = Int.MAX_VALUE

    /**
     * 콤보에 넣을 수 있는 스킬 후보 — 빌드의 모든 액티브·발동 스킬.
     *
     * PoB는 빌드에 든 스킬 그룹을 전부 내보낸다. 그 안에는 다른 스킬 세트에서 꺼 둔
     * 그룹(enabled="false")과 아이템·패시브 트리가 부여한 스킬(source="Item:…",
     * "Tree:…")도 섞여 있고, 아이템 설명에만 적힌 부여 스킬은 목록에 아예 없다.
     * 예전에는 켜진 그룹만 보고 12개에서 잘라, 그룹이 많은 빌드에서는 주력기(전기불꽃 등)가
     * 목록 밖으로 밀려 콤보 설정에 안 보였다. 지금은 보조 젬만 빼고 모두 보여 준다.
     *
     * 순서: 주력 그룹 → 켜진 그룹 → 꺼 둔 그룹 → 아이템 부여 스킬
     */
    fun options(
        build: PobBuild?,
        dict: KrDict = KrDict.EMPTY,
        dpsByName: Map<String, Double> = emptyMap(),
        limit: Int = NO_LIMIT,
    ): List<ComboPick> {
        val list = ArrayList<ComboPick>()
        val seen = HashSet<String>()
        if (build == null) return list

        fun label(name: String): String = dict.gemName(name).ifBlank { name }

        fun dpsOf(name: String): Double =
            dpsByName[TriggerKind.normalizeName(name)]
                ?: BattleSetup.skillDps(build, name)

        fun add(gem: String, motion: SkillMotion, note: String = ""): Boolean {
            val key = gem.trim().lowercase()
            if (key.isEmpty() || !seen.add(key)) return false
            list.add(
                ComboPick(
                    gem = gem,
                    label = label(gem),
                    motion = motion,
                    role = SkillMotions.roleOf(motion),
                    note = note,
                    dps = dpsOf(gem),
                )
            )
            return list.size < limit
        }

        // 도이드리의 파멸(암울한 결과)은 젬 목록에 없으니 따로 넣는다.
        if (PobCalculator.isDarkConsequences(build)) {
            seen.add(Minions.DARK_CONSEQUENCES_EN.lowercase())
            list.add(
                ComboPick(
                    gem = Minions.DARK_CONSEQUENCES_EN,
                    label = label(Minions.DARK_CONSEQUENCES_EN).ifBlank { Minions.DARK_CONSEQUENCES_KO },
                    motion = SkillMotion.CURSE,
                    role = ComboRole.CURSE,
                    note = "저주 · 두꺼비",
                    dps = dpsByName[TriggerKind.normalizeName(Minions.DARK_CONSEQUENCES_EN)] ?: 0.0,
                )
            )
        }

        val main = Pob.mainGroup(build, 0)
        val groups = build.skills
            .filter { it.enabled }
            .sortedBy { if (it === main) 0 else 1 } + build.skills.filter { !it.enabled }
        for (group in groups) {
            // 보조 젬은 '직접 쓰는 스킬'이 아니므로 뺀다. 정신력 젬은 콤보에 넣어 볼 수 있게 남긴다.
            for (gem in group.gems) {
                if (PobCalculator.isSupport(gem)) continue
                val name = gem.name.trim()
                if (name.isEmpty()) continue
                val motion = SkillMotions.detect(name, group.label)
                val note = when {
                    group.grantedByItem -> "아이템 부여"
                    group.grantedByTree -> "패시브 부여"
                    !group.enabled -> "꺼 둔 스킬 세트"
                    PobTriggers.kindOf(gem) != null -> "발동 장치"
                    else -> ""
                }
                if (!add(name, motion, note)) return list
            }
        }

        // 아이템 설명에만 적힌 부여 스킬 (예: "Grants Skill: Level 18 Eternal Rage")
        for (item in build.items.values) {
            for (line in item.lines) {
                val match = GRANTED_SKILL.find(line) ?: continue
                val name = match.groupValues[1].trim()
                if (name.isEmpty()) continue
                val motion = SkillMotions.detect(name, item.displayName)
                if (!add(name, motion, "아이템 부여")) return list
            }
        }
        return list
    }

    /** 아이템 설명에 적힌 "Grants Skill: Level 18 Eternal Rage" */
    private val GRANTED_SKILL = Regex("grants skill:\\s*(?:level\\s*\\d+\\s*)?(.+)", RegexOption.IGNORE_CASE)

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
            // 같은 동작이라는 이유로 다른 스킬을 끌어오면, 보조 젬 이름이 주력 몫으로
            // 기록되는 식의 엉뚱한 결과가 나므로 젬 이름이나 화면 이름이 맞을 때만 받는다.
            val opt = options.firstOrNull { it.gem == gem }
                ?: options.firstOrNull { it.label == label }
            if (opt == null) {
                // 빌드를 아직 모르는 때(앱을 열 때)는 적힌 그대로 들고 있는다.
                if (options.isNotEmpty()) return@forEach
                out.add(
                    ComboPick(
                        gem = gem,
                        label = label,
                        motion = motion,
                        role = role,
                        repeats = repeats,
                        note = parts.getOrNull(5).orEmpty(),
                        dps = parts.getOrNull(6)?.toDoubleOrNull() ?: 0.0,
                    )
                )
                return@forEach
            }
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
