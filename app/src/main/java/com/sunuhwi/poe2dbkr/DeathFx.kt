package com.sunuhwi.poe2dbkr

import java.util.Locale
import kotlin.math.max
import kotlin.math.min

/**
 * 사망 화면 연출의 시간표.
 *
 * 전투가 끝난 뒤 캔버스는 이 시간표대로 붉은 사망 배너 → 사망 원인 → 스킬별 DPS 줄을
 * 차례로 흐리게 띄운다. 숫자만 여기 모아 두면 화면 없이도 알파 진행을 확인할 수 있다.
 */
object DeathFx {

    /** 쓰러지는 동안의 어두운 비네트가 차오르는 시간 */
    const val GLOOM_TIME = 0.9f
    /** 마지막에 어둡게 남는 정도 (0~1) */
    const val GLOOM_MAX = 0.82f
    /** 붉은 섬광이 걷히는 시간 */
    const val FLASH_TIME = 0.62f

    /** 붉은 배너가 떠오르기 시작하는 때와 걸리는 시간 */
    const val BANNER_DELAY = 0.32f
    const val BANNER_FADE = 0.62f
    /** 사망 원인 카드 */
    const val CAUSE_DELAY = 0.78f
    const val CAUSE_FADE = 0.62f
    /** 스킬별 DPS 줄 — 한 줄씩 시차를 두고 올라온다 */
    const val ROW_DELAY = 1.24f
    const val ROW_STAGGER = 0.11f
    const val ROW_FADE = 0.46f
    /** 다시 시작·닫기 단추 */
    const val BUTTON_DELAY = 1.9f
    const val BUTTON_FADE = 0.5f
    /** 연출이 모두 끝나는 때 — 캔버스가 이때까지는 프레임을 그린다 */
    val TOTAL_TIME: Float get() = BUTTON_DELAY + BUTTON_FADE + ROW_STAGGER * 6f

    /** 0에서 1로 곧게 차오르는 진행도 */
    fun progress(age: Float, delay: Float, duration: Float): Float {
        if (duration <= 0f) return if (age >= delay) 1f else 0f
        return ((age - delay) / duration).coerceIn(0f, 1f)
    }

    /** 시작과 끝이 부드러운 곡선 (smoothstep) */
    fun ease(t: Float): Float = t * t * (3f - 2f * t)

    /** 알파 0 → 1 페이드인 */
    fun alpha(age: Float, delay: Float, duration: Float): Float = ease(progress(age, delay, duration))

    fun bannerAlpha(age: Float): Float = alpha(age, BANNER_DELAY, BANNER_FADE)

    fun causeAlpha(age: Float): Float = alpha(age, CAUSE_DELAY, CAUSE_FADE)

    fun rowAlpha(age: Float, index: Int): Float =
        alpha(age, ROW_DELAY + max(0, index) * ROW_STAGGER, ROW_FADE)

    fun buttonAlpha(age: Float): Float = alpha(age, BUTTON_DELAY, BUTTON_FADE)

    /** 사망 충격의 붉은 섬광 — 1에서 0으로 걷힌다 */
    fun flash(age: Float): Float = 1f - progress(age, 0f, FLASH_TIME)

    /** 화면 가장자리가 어두워지는 정도 */
    fun gloom(age: Float): Float = ease(progress(age, 0f, GLOOM_TIME)) * GLOOM_MAX

    /** 쓰러지는 동작 — 0(서 있음)에서 1(누움) */
    fun fall(age: Float): Float = ease(progress(age, 0f, 0.55f))

    /** 몸에서 빠져나오는 영혼이 흩어지는 정도 */
    fun soul(age: Float): Float = progress(age, 0.16f, 1.35f)

    /** 화면에 남은 사망 화면을 다 그렸는지 */
    fun done(age: Float): Boolean = age >= TOTAL_TIME

    /** 사망 카드 안에서 쓸 수 있는 폭·높이 여유 (겹침 방지용) */
    const val LIST_GAP = 4f

    /**
     * 남은 자리에 들어가는 스킬 줄 수.
     *
     * 배너·사망 원인 카드·단추를 빼고 남은 높이를 줄 높이로 나눈다. 줄이 하나도
     * 들어가지 않으면 0을 돌려주고, 화면은 목록 카드를 아예 그리지 않는다.
     */
    fun rowCapacity(
        listTop: Float,
        listBottom: Float,
        headerH: Float,
        rowH: Float,
        /** 접힌 줄 안내("+ N개 스킬 더") 자리로 미리 빼 둘 높이 */
        reserve: Float = 0f,
        max: Int = 9,
    ): Int {
        if (rowH <= 0f || listBottom <= listTop) return 0
        val fit = ((listBottom - listTop - headerH - LIST_GAP - reserve) / rowH).toInt()
        return fit.coerceIn(0, max)
    }

    /** 목록 카드가 차지하는 높이 (줄 + 머리말 + 접힌 줄 안내) */
    fun listHeight(rows: Int, headerH: Float, rowH: Float, restLine: Float): Float =
        headerH + max(0, rows) * rowH + if (rows > 0) restLine else 0f
}

/** 적이 플레이어에게 남긴 피해 한 건 — 사망 원인을 되짚는 기록 */
data class DamageTrace(
    val source: String,
    val skill: String,
    val element: DamageElement,
    val damage: Double,
    /** 전투 경과 시간(초) */
    val at: Double,
    /** 이 한 대를 맞기 직전의 생명력 + 보호막 */
    val poolBefore: Double = 0.0,
)

/**
 * 사망 원인.
 *
 * 마지막으로 생명력을 깎은 한 방([killer] + [skill])과, 그 직전에 몰린 피해([recent])를
 * 함께 들고 있어 "무엇에 맞고 죽었는지"를 한 줄로 말할 수 있다.
 */
data class DeathCause(
    val killer: String,
    val skill: String,
    val element: DamageElement,
    val damage: Double,
    val at: Double,
    /** 그 한 방을 맞기 직전의 생명력 + 보호막 */
    val poolBefore: Double,
    val maxPool: Double,
    val recent: List<DamageTrace> = emptyList(),
) {
    /** 피해가 최대 생명력의 80%를 넘는 한 방인지 */
    val oneShot: Boolean get() = maxPool > 0.0 && damage >= maxPool * 0.8

    /** "우버 자로크 · 번개 낙뢰" */
    val headline: String get() = DeathText.headline(this)

    /** "피해 6,240 · 번개 · 12.4초" */
    val damageLine: String get() = DeathText.damageLine(this)

    /** "사망 직전 2.8초 동안 4회 맞아 9,120 피해" */
    val recentLine: String get() = DeathText.recentLine(this)
}

/** 스킬 하나가 전투 중 쌓은 피해 */
class SkillDamageRow(val name: String) {
    var damage = 0.0
    var hits = 0
}

/** 화면에 내림차순으로 보여 줄 스킬별 DPS 기록 한 줄 */
data class SkillDamageReport(
    val name: String,
    val damage: Double,
    val hits: Int,
    val dps: Double,
    /** 전체 피해 대비 비율(%) */
    val share: Double,
) {
    val line: String
        get() = "$name — ${DeathText.number(damage)} · ${hits}회 · ${BattleWorld.formatShort(dps)}/s · ${share.toInt()}%"
}

object SkillTally {

    /**
     * 모아 둔 스킬별 피해를 정렬해 내보낸다.
     *
     * 전투 시간이 0.1초도 안 됐으면 DPS가 터무니없이 커지므로 그 값을 뺀다.
     */
    fun rank(rows: Collection<SkillDamageRow>, seconds: Double, total: Double): List<SkillDamageReport> {
        if (rows.isEmpty()) return emptyList()
        val span = max(0.1, seconds)
        val sum = if (total > 0.0) total else rows.sumOf { it.damage }
        return rows
            .filter { it.damage > 0.0 && it.damage.isFinite() }
            .map { row ->
                SkillDamageReport(
                    name = row.name,
                    damage = row.damage,
                    hits = row.hits,
                    dps = row.damage / span,
                    share = if (sum > 0.0) (row.damage / sum * 100.0).coerceIn(0.0, 100.0) else 0.0,
                )
            }
            .sortedWith(compareByDescending<SkillDamageReport> { it.damage }.thenBy { it.name })
    }

    /** 화면에 들어가는 줄 수만큼 자르고 나머지는 한 줄로 접는다 */
    fun pages(reports: List<SkillDamageReport>, limit: Int): List<SkillDamageReport> =
        if (limit <= 0) emptyList() else reports.take(limit)

    /** 접힌 나머지 줄 수 */
    fun rest(reports: List<SkillDamageReport>, limit: Int): Int =
        max(0, reports.size - max(0, limit))
}

/** 사망 화면 글자 — 화면과 테스트가 같은 문장을 쓴다 */
object DeathText {

    /** 사망 화면에 크게 쓰는 글 */
    fun title(): String = "사망"

    /** 사망 화면 아래 한 줄 — 수치는 적지 않는다 */
    fun subtitle(): String = "캐릭터가 쓰러졌습니다"

    /** 4,820 처럼 천 단위로 끊어 쓴다 */
    fun number(value: Double): String {
        if (!value.isFinite()) return "—"
        return String.format(Locale.KOREA, "%,.0f", max(0.0, value))
    }

    /** 공격자와 기술 이름. 이름이 비어 있어도 문장이 깨지지 않게 채운다 */
    fun headline(cause: DeathCause): String {
        val killer = cause.killer.ifBlank { "몬스터" }
        val skill = cause.skill.ifBlank { "공격" }
        return if (cause.oneShot) "$killer · $skill 일격" else "$killer · $skill"
    }

    fun damageLine(cause: DeathCause): String {
        val parts = ArrayList<String>()
        parts.add("피해 " + number(cause.damage))
        parts.add(cause.element.label)
        parts.add(String.format(Locale.KOREA, "%.1f초", max(0.0, cause.at)))
        return parts.joinToString(" · ")
    }

    /** 사망 직전 몇 초 동안 몇 대 맞았는지 */
    fun recentLine(cause: DeathCause): String {
        val hits = cause.recent.filter { it.damage > 0.0 && it.damage.isFinite() }
        if (hits.isEmpty()) return ""
        val total = hits.sumOf { it.damage }
        val span = min(9.9, max(0.1, cause.at - hits.first().at))
        val count = if (hits.size == 1) "한 대" else "${hits.size}대"
        return String.format(Locale.KOREA, "사망 직전 %.1f초 동안 %s 맞아 %s 피해", span, count, number(total))
    }

    /** 이 한 방이 생명력의 몇 %를 가져갔는지 */
    fun shareLine(cause: DeathCause): String {
        if (cause.maxPool <= 0.0) return ""
        val pct = (cause.poolBefore / cause.maxPool * 100.0).coerceIn(0.0, 100.0)
        return String.format(Locale.KOREA, "맞기 직전 생명력 %s (%.0f%%)", number(cause.poolBefore), pct)
    }
}
