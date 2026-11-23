package com.sunuhwi.poe2dbkr

import java.util.Locale
import kotlin.math.max

/**
 * 클리어(승리) 화면 연출의 시간표.
 *
 * 사망 화면([DeathFx])과 대칭이다. 전투가 끝난 뒤 캔버스는 금빛 클리어 배너 →
 * 클리어 요약 → 스킬별 DPS 줄 → 다시 시작·닫기 단추를 차례로 흐리게 띄운다.
 * 숫자만 여기 모아 두면 화면 없이도 알파 진행을 확인할 수 있다.
 */
object VictoryFx {

    /** 금빛 섬광이 걷히는 시간 */
    const val FLASH_TIME = 0.7f

    /** 클리어 배너 */
    const val BANNER_DELAY = 0.2f
    const val BANNER_FADE = 0.58f

    /** 요약 카드 (총 시간·총 피해·평균 DPS·처치) */
    const val SUMMARY_DELAY = 0.62f
    const val SUMMARY_FADE = 0.56f

    /** 스킬별 DPS 줄 — 한 줄씩 시차를 두고 올라온다 */
    const val ROW_DELAY = 1.04f
    const val ROW_STAGGER = 0.1f
    const val ROW_FADE = 0.44f

    /** 다시 시작·닫기 단추 */
    const val BUTTON_DELAY = 1.66f
    const val BUTTON_FADE = 0.5f

    /** 목록 카드 안에서 쓸 수 있는 폭·높이 여유 */
    const val LIST_GAP = 4f

    /** 줄 하나에 담는 값의 열 수 (누적 피해 · 명중 · 실전 DPS · 비율) */
    const val COLUMNS = 4

    /** 연출이 모두 끝나는 때 — 캔버스가 이때까지는 프레임을 그린다 */
    val TOTAL_TIME: Float get() = BUTTON_DELAY + BUTTON_FADE + ROW_STAGGER * 6f

    fun progress(age: Float, delay: Float, duration: Float): Float {
        if (duration <= 0f) return if (age >= delay) 1f else 0f
        return ((age - delay) / duration).coerceIn(0f, 1f)
    }

    fun ease(t: Float): Float = t * t * (3f - 2f * t)

    fun alpha(age: Float, delay: Float, duration: Float): Float = ease(progress(age, delay, duration))

    fun bannerAlpha(age: Float): Float = alpha(age, BANNER_DELAY, BANNER_FADE)

    fun summaryAlpha(age: Float): Float = alpha(age, SUMMARY_DELAY, SUMMARY_FADE)

    fun rowAlpha(age: Float, index: Int): Float =
        alpha(age, ROW_DELAY + max(0, index) * ROW_STAGGER, ROW_FADE)

    fun buttonAlpha(age: Float): Float = alpha(age, BUTTON_DELAY, BUTTON_FADE)

    /** 클리어 순간의 금빛 섬광 — 1에서 0으로 걷힌다 */
    fun flash(age: Float): Float = 1f - progress(age, 0f, FLASH_TIME)

    /** 승리 빛기둥이 차오르는 정도 */
    fun glow(age: Float): Float = ease(progress(age, 0f, 1.1f))

    fun done(age: Float): Boolean = age >= TOTAL_TIME

    /**
     * 남은 자리에 들어가는 스킬 줄 수.
     * 배너·요약 카드·단추를 빼고 남은 높이를 줄 높이로 나눈다.
     */
    fun rowCapacity(
        listTop: Float,
        listBottom: Float,
        headerH: Float,
        rowH: Float,
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

/**
 * 클리어 요약.
 *
 * 사망 화면이 "무엇에 맞고 죽었나"를 말한다면, 여기서는 "얼마나 빨리, 얼마나 세게
 * 쓸어버렸나"를 말한다. 값은 [BattleWorld.clearSummary]가 전투 기록에서 뽑아 준다.
 */
data class ClearSummary(
    val seconds: Double,
    val damage: Double,
    val dps: Double,
    val kills: Int,
    val total: Int,
    /** 피해가 큰 순서로 정렬된 스킬별 기록 */
    val skills: List<SkillDamageReport> = emptyList(),
) {
    /** 가장 많이 때린 스킬 */
    val topSkill: SkillDamageReport? get() = skills.firstOrNull()

    val headline: String get() = ClearText.headline(seconds)

    val bodyLine: String get() = ClearText.bodyLine(this)

    /** 목록에 올릴 줄 — 피해가 큰 순서 그대로 */
    fun rows(limit: Int = skills.size): List<SkillDamageReport> =
        if (limit <= 0) emptyList() else skills.take(limit)

    /** 목록에 다 못 올린 줄 수 */
    fun rest(limit: Int): Int = max(0, skills.size - max(0, limit))
}

/** 클리어 화면 글자 — 화면과 시험이 같은 문장을 쓴다 */
object ClearText {

    fun number(value: Double): String = DeathText.number(value)

    fun headline(seconds: Double): String =
        String.format(Locale.KOREA, "전투 %.1f초", max(0.0, seconds))

    /** "처치 60/60 · 총 피해 4.82M · 평균 DPS 389K" */
    fun bodyLine(summary: ClearSummary): String {
        val kills = if (summary.total > 0) "${summary.kills}/${summary.total}" else "${summary.kills}"
        return "처치 $kills · 총 피해 ${BattleWorld.formatShort(summary.damage)} · " +
            "평균 DPS ${BattleWorld.formatShort(summary.dps)}"
    }

    /** 줄 아래 작은 글씨 — "명중 42회 · 12.3K/s · 8%" */
    fun rowDetail(report: SkillDamageReport): String =
        "명중 ${report.hits}회 · ${BattleWorld.formatShort(report.dps)}/s · ${report.share.toInt()}%"

    /** 줄 오른쪽 큰 글씨 — 누적 피해 */
    fun rowValue(report: SkillDamageReport): String = BattleWorld.formatShort(report.damage)

    /** 목록 머리말 오른쪽 — 열 이름 */
    fun columnHeader(): String = "누적 피해 · 명중 · DPS/초 · 비율"
}
