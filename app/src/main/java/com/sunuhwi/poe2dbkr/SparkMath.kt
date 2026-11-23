package com.sunuhwi.poe2dbkr

import kotlin.math.roundToInt

/**
 * 전기불꽃(Spark) 한 번 시전의 발수와 궤적 수치.
 *
 * poe2db.tw/kr/Spark 의 젬 설명·수치를 그대로 옮겼다.
 *  - 투사체 (4—9)개 발사 — 1레벨 4개에서 20레벨 9개까지 늘어난다
 *  - 투사체 지속시간 2초
 *  - 냉기 주입을 소모하여 전기불꽃 여러 개를 나선형으로, 가능하면 원형으로 발사
 *  - 적에게 명중하거나 만료될 때까지 천방지축으로 움직인다
 *
 * 그래서 한 번 시전은 "기본 발사 수 × 두 겹(나선)"으로 깔리고, 여기에 빌드의
 * 추가 투사체가 겹마다 더해진다. 20레벨 빌드는 9 × 2 = 18발, 추가 투사체 1개면
 * 20발이 화면을 채운다 — 인게임 전기불꽃 스샷과 같은 밀도다.
 */
object SparkMath {

    /** 투사체 지속시간 (poe2db: 2초) */
    const val DURATION = 2.0f

    /** 1레벨 발사 수 */
    const val MIN_BASE = 4

    /** 20레벨 발사 수 */
    const val MAX_BASE = 9

    /** 냉기 주입을 소모한 나선 발사 겹 수 (원형으로 두 겹) */
    const val RINGS = 2

    /** 20레벨 나선 시전의 기본 발수 — 화면에 깔리는 최소치 (9 × 2) */
    const val SALVO_AT_MAX_LEVEL = MAX_BASE * RINGS

    /** 한 줄기가 꿰뚫는 추가 적 수 */
    const val PIERCE = 2

    /** 벽에 부딪혀 튕기는 횟수 */
    const val BOUNCES = 3

    /** 줄기 하나가 평균 몇 적을 때리는지 — 시전 1회 총 피해를 나눌 때 쓴다 */
    const val EXPECTED_HITS = 1.2

    /** 젬 레벨에 따른 기본 발사 수 (4 → 9) */
    fun baseProjectiles(gemLevel: Int): Int {
        val lv = gemLevel.coerceIn(1, 20)
        val grown = MIN_BASE + (MAX_BASE - MIN_BASE) * (lv - 1) / 19.0
        return grown.roundToInt().coerceIn(MIN_BASE, MAX_BASE)
    }

    /**
     * 한 번 시전에 깔리는 전기불꽃 수.
     * 나선 발사를 쓰면 겹마다 기본 발사 수가 나가고, 추가 투사체도 겹마다 붙는다.
     */
    fun salvo(gemLevel: Int, extraProjectiles: Int = 0, spiral: Boolean = true): Int {
        val perRing = baseProjectiles(gemLevel) + extraProjectiles.coerceIn(0, 8)
        val count = perRing * (if (spiral) RINGS else 1)
        return count.coerceIn(1, 40)
    }

    /** 줄기 하나가 적을 한 번 꿰뚫을 때의 피해 — 시전 1회 총 피해를 지킨다 */
    fun perHitDamage(total: Double, salvo: Int): Double {
        if (!total.isFinite() || total <= 0.0) return 0.5
        return (total / (salvo.coerceAtLeast(1) * EXPECTED_HITS)).coerceAtLeast(0.5)
    }

    /**
     * 메타젬(치명타 시 시전 등)에 넘길 명중 한 번의 무게.
     *
     * poe2db 젬 피해는 투사체 하나가 닿았을 때의 피해이므로, 발수로 나누어 담은
     * 시뮬레이션 피해를 그대로 에너지로 쓰면 한 발이 너무 작게 세어진다.
     * 발수만큼 되돌려 "전기불꽃 한 줄기가 닿은 피해"를 기준으로 센다.
     */
    fun energyWeight(salvo: Int): Double = salvo.coerceAtLeast(1) * EXPECTED_HITS
}
