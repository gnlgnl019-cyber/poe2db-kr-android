package com.sunuhwi.poe2dbkr

/**
 * 세 가지 충전.
 *
 * PoE2의 권능·인내·격분 충전은 서로 다른 곳에서 생기고 쓰임도 다르다.
 * 전장 HUD와 결과 화면에서 색으로 구분한다.
 */
enum class ChargeKind(val label: String, val color: Int) {
    /** 권능 — 치명타·처치에서 생겨 기름 포화·공허의 상이 태운다 */
    POWER("권능", 0xFF5AA9FF.toInt()),

    /** 인내 — 맞을 때 쌓여 방어를 올린다 */
    ENDURANCE("인내", 0xFFFF6A4A.toInt()),

    /** 격분 — 빙결·전기 충격·속박에서 생겨 공격 속도를 올린다 */
    FRENZY("격분", 0xFF5BE08C.toInt()),
}

/**
 * 충전 운용 규칙.
 *
 * 빌드에 들어온 젬·패시브·전직·고유 아이템 글에서 읽어 낸다. 전투에서는 이 규칙대로
 * 충전을 얻고 태우며, 공명처럼 얻는 충전의 종류를 바꾸는 노드도 그대로 반영한다.
 */
data class ChargeRules(
    val maxPower: Int = BASE_MAX,
    val maxEndurance: Int = BASE_MAX,
    val maxFrenzy: Int = BASE_MAX,
    /**
     * 전투 격분 — 빙결·전기 충격·속박을 걸면 격분 충전을 얻는다.
     * poe2db: 20레벨 기준 6.1초에 한 번 (8초 → 6.1초).
     */
    val combatFrenzy: Boolean = false,
    val frenzyCooldown: Float = 6.1f,
    /**
     * 공명 — 얻는 충전이 권능→인내→격분→권능으로 한 칸씩 바뀐다.
     * (PoE2 키스톤: 권능 대신 인내, 인내 대신 격분, 격분 대신 권능)
     */
    val resonance: Boolean = false,
    /**
     * 충전 조절 — 유지 중이면 10초마다 세 충전을 하나씩 태우고, 그동안
     * 충전마다 치명타·방어·공격 속도가 오른다. (poe2db: Charge Regulation)
     */
    val regulation: Boolean = false,
    val regulationInterval: Float = 10f,
    val regulationCritPct: Double = 26.0,
    val regulationDefencePct: Double = 20.0,
    val regulationSpeedPct: Double = 25.0,
    /** 적 처치 시 격분 충전 (고유 아이템·패시브) */
    val frenzyOnKill: Boolean = false,
    /** 피격 시 인내 충전 */
    val enduranceOnHit: Boolean = false,
) {
    companion object {
        /** 기본 최대 충전 수 */
        const val BASE_MAX = 3
    }

    fun cap(kind: ChargeKind): Int = when (kind) {
        ChargeKind.POWER -> maxPower.coerceIn(1, 12)
        ChargeKind.ENDURANCE -> maxEndurance.coerceIn(1, 12)
        ChargeKind.FRENZY -> maxFrenzy.coerceIn(1, 12)
    }

    /** 얻으려던 충전이 실제로 어느 충전이 되는지 (공명이면 한 칸씩 돈다) */
    fun resolve(gained: ChargeKind): ChargeKind {
        if (!resonance) return gained
        return when (gained) {
            ChargeKind.POWER -> ChargeKind.ENDURANCE
            ChargeKind.ENDURANCE -> ChargeKind.FRENZY
            ChargeKind.FRENZY -> ChargeKind.POWER
        }
    }

    /** 충전을 다루는 빌드인지 (HUD에 충전 줄을 띄울지 정한다) */
    val usesCharges: Boolean
        get() = combatFrenzy || frenzyOnKill || enduranceOnHit || regulation || resonance ||
            maxPower > BASE_MAX || maxEndurance > BASE_MAX || maxFrenzy > BASE_MAX
}

object Charges {

    private val FRENZY_WORDS = listOf("combat frenzy", "전투 격분")
    private val RESONANCE_WORDS = listOf("resonance", "공명")
    private val REGULATION_WORDS = listOf("charge regulation", "충전 조절")
    private val FRENZY_KILL_WORDS = listOf(
        "frenzy charge on kill", "gain a frenzy charge when you kill", "처치 시 격분",
        "적을 처치하면 격분",
    )
    private val ENDURANCE_HIT_WORDS = listOf(
        "endurance charge when you take", "endurance charge on hit", "피격 시 인내",
        "맞을 때 인내",
    )

    private val KO_MAX = Regex("최대\\s*(권능|인내|격분)\\s*충전\\s*\\+?\\s*(\\d+)")
    private val EN_MAX = Regex("\\+?(\\d+)\\s*to maximum (power|endurance|frenzy) charges", RegexOption.IGNORE_CASE)
    private val EVERY = Regex("(\\d+(?:\\.\\d+)?)\\s*(?:seconds|초)")

    /**
     * 글 뭉치에서 충전 규칙을 읽는다.
     *
     * 젬 이름, 패시브 노드 이름·설명, 전직 노드, 고유 아이템 효과가 모두 여기로 들어온다.
     * 한글·영문 표기를 함께 본다.
     */
    fun read(lines: List<String>): ChargeRules {
        var maxPower = ChargeRules.BASE_MAX
        var maxEndurance = ChargeRules.BASE_MAX
        var maxFrenzy = ChargeRules.BASE_MAX
        var combatFrenzy = false
        var resonance = false
        var regulation = false
        var frenzyOnKill = false
        var enduranceOnHit = false
        var frenzyCooldown = 6.1f
        var regulationInterval = 10f
        var crit = 26.0
        var defence = 20.0
        var speed = 25.0

        for (raw in lines) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            val lower = line.lowercase()

            // 젬 이름 줄과 그 효과 줄이 따로 오므로, 효과 줄("빙결... every 6.3 seconds")도 같이 본다.
            val frenzyGem = FRENZY_WORDS.any { lower.contains(it) } ||
                (lower.contains("frenzy charge") && lower.contains("freeze") && lower.contains("every"))
            if (frenzyGem) {
                combatFrenzy = true
                // "no more than once every 6.3 seconds" 처럼 젬 레벨별 쿨타임이 적혀 있으면 그 값을 쓴다.
                EVERY.findAll(line).firstOrNull()?.groupValues?.get(1)?.toFloatOrNull()?.let {
                    if (it in 0.5f..30f) frenzyCooldown = it
                }
            }
            if (RESONANCE_WORDS.any { lower.contains(it) }) resonance = true
            if (REGULATION_WORDS.any { lower.contains(it) }) {
                regulation = true
                EVERY.findAll(line).lastOrNull()?.groupValues?.get(1)?.toFloatOrNull()?.let {
                    if (it in 1f..60f) regulationInterval = it
                }
            }
            if (FRENZY_KILL_WORDS.any { lower.contains(it) }) frenzyOnKill = true
            if (ENDURANCE_HIT_WORDS.any { lower.contains(it) }) enduranceOnHit = true

            KO_MAX.findAll(line).forEach { match ->
                val n = match.groupValues[2].toIntOrNull() ?: 0
                when (match.groupValues[1]) {
                    "권능" -> maxPower += n
                    "인내" -> maxEndurance += n
                    "격분" -> maxFrenzy += n
                }
            }
            EN_MAX.findAll(line).forEach { match ->
                val n = match.groupValues[1].toIntOrNull() ?: 0
                when (match.groupValues[2].lowercase()) {
                    "power" -> maxPower += n
                    "endurance" -> maxEndurance += n
                    "frenzy" -> maxFrenzy += n
                }
            }
            // 충전 조절의 수치는 같은 줄에 백분율로 적혀 있다.
            if (lower.contains("critical hit chance") && lower.contains("power charge")) {
                value(lower)?.let { crit = it }
            }
            if (lower.contains("armour") && lower.contains("endurance charge")) {
                value(lower)?.let { defence = it }
            }
            if (lower.contains("skill speed") && lower.contains("frenzy charge")) {
                value(lower)?.let { speed = it }
            }
        }

        return ChargeRules(
            maxPower = maxPower.coerceAtMost(12),
            maxEndurance = maxEndurance.coerceAtMost(12),
            maxFrenzy = maxFrenzy.coerceAtMost(12),
            combatFrenzy = combatFrenzy,
            frenzyCooldown = frenzyCooldown.coerceIn(0.5f, 30f),
            resonance = resonance,
            regulation = regulation,
            regulationInterval = regulationInterval.coerceIn(1f, 60f),
            regulationCritPct = crit.coerceIn(0.0, 100.0),
            regulationDefencePct = defence.coerceIn(0.0, 60.0),
            regulationSpeedPct = speed.coerceIn(0.0, 60.0),
            frenzyOnKill = frenzyOnKill,
            enduranceOnHit = enduranceOnHit,
        )
    }

    private val PERCENT = Regex("(\\d+(?:\\.\\d+)?)\\s*%")

    private fun value(line: String): Double? = PERCENT.find(line)?.groupValues?.get(1)?.toDoubleOrNull()
}
