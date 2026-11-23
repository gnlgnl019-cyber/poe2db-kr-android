package com.sunuhwi.poe2dbkr

/**
 * 원소 상태이상 수치.
 *
 * 동결·감전·점화가 전투에서 실제로 무엇을 하는지 한곳에 모아 둔다.
 * 화면은 이 값을 그리기만 하고, 시험은 같은 값을 확인하므로
 * 상태이상 규칙을 바꾸면 여기만 고치면 된다.
 */
object Ailments {

    // ------------------------------------------------------------- 동결

    /** 빙결이 걸리면 이 시간 동안 얼어붙는다 (초) */
    const val FREEZE_TIME = 2.4f

    /** 동결된 적은 이동도 공격도 하지 못한다 — 남는 속도 비율 */
    const val FROZEN_SPEED = 0f

    /** 동결된 적의 몸을 감싸는 얼음 감옥의 기본 반경(캔버스 단위) */
    const val CAGE_RADIUS = 26f

    /** 동결 상태로 처치되면 산산조각 — 튀는 얼음 파편 수 */
    fun shatterShards(kind: EnemyKind): Int = when (kind) {
        EnemyKind.BOSS -> 34
        EnemyKind.RARE -> 24
        EnemyKind.MAGIC -> 18
        EnemyKind.NORMAL -> 13
    }

    // ------------------------------------------------------------- 감전

    /** 감전된 적이 받는 피해 증가의 기본값(%) — PoE2는 20%에서 시작한다 */
    const val SHOCK_TAKEN_PCT = 20.0

    /** 감전 수치(%)가 100% 오를 때마다 더해지는 피해 증가(%) */
    const val SHOCK_MAGNITUDE_PCT = 20.0

    /** 아무리 세게 감전돼도 이 배율을 넘지 않는다 */
    const val SHOCK_MAX_TAKEN_PCT = 60.0

    /** 감전 지속 시간(초) */
    const val SHOCK_TIME = 4.0f

    /** 감전 수치(%)를 받는 피해 증가(%)로 바꾼다 (20% ~ 60%) */
    fun shockTakenPct(magnitudePct: Double): Double {
        val extra = SHOCK_MAGNITUDE_PCT * (magnitudePct.coerceIn(0.0, 300.0) / 100.0)
        return (SHOCK_TAKEN_PCT + extra).coerceAtMost(SHOCK_MAX_TAKEN_PCT)
    }

    /** 감전된 적에게 들어가는 피해 배율 */
    fun shockMultiplier(magnitudePct: Double): Double = 1.0 + shockTakenPct(magnitudePct) / 100.0

    // ------------------------------------------------------------- 점화

    /** 점화 지속 시간(초) */
    const val IGNITE_TIME = 3.2f

    /** 점화 피해는 그 타격의 이 비율만큼 매초 들어간다 */
    const val IGNITE_SHARE = 0.22

    /** 점화의 초당 피해 (점화 수치가 오르면 같이 오른다) */
    fun igniteDps(hit: Double, magnitudePct: Double): Double {
        val mul = 1.0 + magnitudePct.coerceIn(0.0, 300.0) / 100.0
        val dps = hit.coerceAtLeast(0.0) * IGNITE_SHARE * mul
        return if (dps.isFinite() && dps > 0.0) dps else 0.0
    }

    /** 점화 이름 — 스킬별 DPS 기록에 이 이름으로 쌓인다 */
    const val IGNITE_LABEL = "점화"

    /** 동결 이름 — 기록·화면 표시 */
    const val FREEZE_LABEL = "빙결"
    const val SHOCK_LABEL = "감전"
}
