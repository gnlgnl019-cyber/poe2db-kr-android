package com.sunuhwi.poe2dbkr

import kotlin.math.max

/**
 * 전령(Herald) 젬 해석.
 *
 * PoE2의 전령은 정신력을 점유하고, 적을 처치하는 순간에만 터지는 광역 폭발이다.
 * PoB XML에는 젬 이름과 보조 젬만 실리므로, 여기서 정신력 예산([PobBuild.stats]의
 * `Spirit`)과 소환수가 먼저 가져간 몫을 빼서 실제로 켜져 있는 전령만 골라 낸다.
 *
 * 피해는 주력 타격 한 방([HeraldMath])에 배율을 곱해 잡는다. 전령이 낸 피해는
 * 전투 쪽에서 전령 이름으로 적립되므로 스킬별 DPS 기록에도 그대로 남는다.
 */
enum class HeraldKind(
    val en: String,
    val ko: String,
    val element: DamageElement,
    /** 발동에 필요한 정신력 (PoE2 기본 30) */
    val spirit: Double = 30.0,
) {
    ICE("Herald of Ice", "얼음의 전령", DamageElement.COLD),
    THUNDER("Herald of Thunder", "천둥의 전령", DamageElement.LIGHTNING),
    ASH("Herald of Ash", "재의 전령", DamageElement.FIRE),
    ;

    /** 어떤 사건에 터지는지 — 화면·결과 글에 쓴다 */
    val trigger: String
        get() = when (this) {
            ICE -> "동결된 적 처치 시 빙결 폭발"
            THUNDER -> "감전된 적 처치 시 낙뢰"
            ASH -> "적 처치 시 초과 피해 화염 폭발"
        }
}

/** 전령 하나의 해석 결과 */
data class HeraldSpec(
    val kind: HeraldKind,
    /** PoB 젬 이름 (영문) */
    val gem: String,
    /** 화면에 보여줄 이름 */
    val name: String,
    val level: Int = 20,
    /** 같은 그룹에 물린 보조 젬 이름 */
    val supports: List<String> = emptyList(),
    /** 점유 정신력 */
    val spirit: Double = kind.spirit,
    /** 정신력이 남아 켜져 있는지 */
    val active: Boolean = true,
) {

    /** 보조 젬 하나마다 피해 8%, 젬 레벨 1당 1.5% (20레벨 기준) */
    val damageMul: Double
        get() {
            val levelBonus = (level.coerceIn(1, 40) - 20) * 0.015
            return (1.0 + supports.size * 0.08 + levelBonus).coerceAtLeast(0.5)
        }

    /** 광역 보조가 붙으면 폭발 반경이 넓어진다 */
    val areaMul: Double
        get() {
            val text = supports.joinToString(" ").lowercase()
            var mul = 1.0
            if (text.contains("magnified") || text.contains("증폭")) mul += 0.35
            if (text.contains("concentrated") || text.contains("집중")) mul -= 0.18
            if (text.contains("area") || text.contains("광역")) mul += 0.05
            return mul.coerceIn(0.6, 2.0)
        }

    /** 원소 집중을 물리면 상태이상을 걸지 못한다 */
    val appliesAilment: Boolean
        get() = supports.none {
            val lower = it.lowercase()
            lower.contains("elemental focus") || lower.contains("원소 집중")
        }

    /** HUD 한 줄에 붙일 이름 (꺼져 있으면 표시를 단다) */
    val hudName: String get() = if (active) name else "$name(꺼짐)"

    val supportText: String
        get() = if (supports.isEmpty()) "" else supports.joinToString(", ")
}

/** 빌드에서 뽑아낸 전령 구성 */
data class HeraldPlan(
    val specs: List<HeraldSpec> = emptyList(),
    /** 빌드 전체 정신력 */
    val spirit: Double = 0.0,
    /** 소환수가 먼저 가져간 정신력 */
    val spiritForMinions: Double = 0.0,
    /** 전령이 점유한 정신력 */
    val reserved: Double = 0.0,
) {
    val active: List<HeraldSpec> get() = specs.filter { it.active }
    val isEmpty: Boolean get() = specs.isEmpty()

    fun has(kind: HeraldKind): Boolean = specs.any { it.active && it.kind == kind }

    fun spec(kind: HeraldKind): HeraldSpec? = specs.firstOrNull { it.active && it.kind == kind }

    /** 전투에 실제로 켜져 있는 전령 이름들 */
    val names: String get() = active.joinToString(" · ") { it.name }

    /** HUD·결과 글 한 줄 (예: "얼음의 전령 · 정신력 30/130") */
    val summary: String
        get() = buildList {
            if (active.isNotEmpty()) add(names)
            if (specs.isNotEmpty() && spirit > 0.0) {
                add("정신력 ${(reserved + spiritForMinions).toInt()}/${spirit.toInt()}")
            }
        }.joinToString(" · ")

    companion object {
        val EMPTY = HeraldPlan()
    }
}

/** 전령이 터질 때의 피해·반경 — 화면과 시험 모두 이 값을 쓴다 */
object HeraldMath {

    /** 얼음의 전령: 주력 타격의 115%를 냉기 광역으로 */
    const val ICE_HIT = 1.15

    /** 천둥의 전령: 주력 타격의 95%를 번개로 */
    const val THUNDER_HIT = 0.95

    /** 재의 전령: 초과 피해가 없을 때도 타격의 30%는 태운다 */
    const val ASH_HIT = 0.30

    /** 기본 폭발 반경 */
    const val ICE_RADIUS = 104f
    const val THUNDER_RADIUS = 118f
    const val ASH_RADIUS = 96f

    /** 범위 안 다른 적이 받는 비율 */
    const val SPLASH_RATIO = 0.75

    fun iceDamage(hit: Double, spec: HeraldSpec): Double =
        scale(hit * ICE_HIT, spec)

    fun thunderDamage(hit: Double, spec: HeraldSpec): Double =
        scale(hit * THUNDER_HIT, spec)

    /**
     * 초과 피해(Overkill) — 적의 남은 생명력을 넘긴 만큼.
     * 재의 전령은 그 값을 그대로 화염 피해로 바꾸고, 초과가 없으면 타격의 30%를 쓴다.
     */
    fun ashDamage(hit: Double, overkill: Double, spec: HeraldSpec): Double =
        scale(max(overkill.coerceAtLeast(0.0), hit * ASH_HIT), spec)

    fun radius(kind: HeraldKind, spec: HeraldSpec): Float {
        val base = when (kind) {
            HeraldKind.ICE -> ICE_RADIUS
            HeraldKind.THUNDER -> THUNDER_RADIUS
            HeraldKind.ASH -> ASH_RADIUS
        }
        return (base * spec.areaMul).toFloat()
    }

    private fun scale(value: Double, spec: HeraldSpec): Double {
        val out = value * spec.damageMul
        return if (out.isFinite() && out > 0.0) out else 0.0
    }
}

/** 전령이 한 전투에서 돈 횟수와 낸 피해 */
data class HeraldReport(
    val kind: HeraldKind,
    val name: String,
    val procs: Int,
    val damage: Double,
) {
    val line: String
        get() = "$name — ${procs}회 · ${BattleWorld.formatShort(damage)}"
}

/** 전령 젬 목록과 인식 */
object Heralds {

    /** 영문 이름 조각 — PoB nameSpec·gemId 어느 쪽으로 와도 잡는다 */
    private val PATTERNS = linkedMapOf(
        HeraldKind.ICE to listOf("herald of ice", "heraldofice", "얼음의 전령", "얼음의전령"),
        HeraldKind.THUNDER to listOf("herald of thunder", "heraldofthunder", "천둥의 전령", "천둥의전령"),
        HeraldKind.ASH to listOf("herald of ash", "heraldofash", "재의 전령", "재의전령"),
    )

    fun detect(name: String): HeraldKind? {
        val text = name.trim().lowercase().replace(" ", "")
        if (text.isEmpty()) return null
        for ((kind, words) in PATTERNS) {
            if (words.any { text.contains(it.replace(" ", "")) }) return kind
        }
        return null
    }

    /**
     * 빌드에서 켜져 있는 전령 목록.
     *
     * 그룹이 꺼져 있으면 전령도 꺼진 것으로 본다. 정신력 예산이 있는데
     * 소환수 몫을 빼고 남은 정신력이 모자라면 그 전령은 점유하지 못한 것으로 표시한다.
     */
    fun plan(
        build: PobBuild?,
        dict: KrDict = KrDict.EMPTY,
        /** 소환수가 먼저 가져간 정신력 */
        spiritForMinions: Double = 0.0,
        /** 전투 화면에서 직접 끈 전령 이름 */
        disabled: Set<String> = emptySet(),
    ): HeraldPlan {
        if (build == null) return HeraldPlan.EMPTY
        val found = LinkedHashMap<HeraldKind, HeraldSpec>()
        for (group in build.skills) {
            if (!group.enabled) continue
            val gems = group.gems.filter { it.enabled }
            val herald = gems.firstNotNullOfOrNull { gem ->
                if (PobCalculator.isSupport(gem)) null else detect(gem.name)?.let { it to gem }
            } ?: continue
            val (kind, gem) = herald
            if (found.containsKey(kind)) continue
            val supports = gems.filter { it !== gem && PobCalculator.isSupport(it) }.map { it.name }
            found[kind] = HeraldSpec(
                kind = kind,
                gem = gem.name,
                name = dict.gemName(gem.name).ifBlank { kind.ko },
                level = gem.level,
                supports = supports,
                spirit = kind.spirit,
            )
        }
        if (found.isEmpty()) return HeraldPlan.EMPTY

        val budget = build.stats["Spirit"]?.takeIf { it.isFinite() && it > 0.0 } ?: 0.0
        val off = disabled.map { it.trim().lowercase() }.toSet()
        var reserved = 0.0
        val specs = found.values.map { spec ->
            if (off.any { it == spec.name.lowercase() || it == spec.gem.trim().lowercase() }) {
                return@map spec.copy(active = false)
            }
            if (budget <= 0.0) return@map spec
            val small = budget - spiritForMinions.coerceAtLeast(0.0) - reserved < spec.spirit
            if (!small) reserved += spec.spirit
            spec.copy(active = !small)
        }
        return HeraldPlan(
            specs = specs,
            spirit = budget,
            spiritForMinions = spiritForMinions.coerceAtLeast(0.0),
            reserved = reserved,
        )
    }
}
