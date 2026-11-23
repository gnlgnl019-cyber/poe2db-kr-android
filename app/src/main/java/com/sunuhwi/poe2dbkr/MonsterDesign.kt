package com.sunuhwi.poe2dbkr

/**
 * 몬스터 실루엣 설계.
 *
 * 플레이어·환영·소환수는 밝은 뼈대/유령 계열로 그리므로, 몬스터는 **어두운 덩어리**로 잡는다.
 * 모든 몬스터는 검은 외곽선과 붉거나 보랏빛으로 빛나는 눈을 갖고, 종류마다 실루엣이 다르다.
 *
 *  - 일반: 앞으로 굽은 뿔 달린 짐승 — 두꺼운 팔·발톱
 *  - 마법: 떠 있는 후드 로브 — 지팡이·보랏빛 눈
 *  - 희귀: 갑주를 두른 거구 — 어깨 가시·황금 왕관·오라
 *
 * 그리기 코드가 길어지지 않도록 수치만 여기서 정하고, 캔버스는 이 값을 그대로 쓴다.
 */
enum class MonsterBuild(val label: String) {
    HORNED_BRUTE("뿔 달린 짐승"),
    HOODED_CASTER("후드 술사"),
    ARMORED_ELITE("갑주 엘리트"),
    BOSS("보스"),
}

data class MonsterLook(
    val build: MonsterBuild,
    /** 몸통 색 */
    val body: Int,
    /** 테두리 하이라이트 */
    val rim: Int,
    /** 검은 외곽선 */
    val outline: Int,
    /** 눈빛 */
    val eye: Int,
    /** 키(px, scale 1 기준) */
    val height: Float,
    /** 어깨 너비(px) */
    val width: Float,
    /** 뿔 개수 */
    val horns: Int,
    /** 등 가시 개수 */
    val spikes: Int,
    /** 박쥐 날개 */
    val wings: Boolean,
    /** 갑주 판 */
    val armored: Boolean,
    /** 발광 오라 색 (0이면 없음) */
    val aura: Int,
    val auraRadius: Float,
) {
    /** 눈이 붉은 악마 계열인지 (보랏빛 눈과 구분) */
    val demonEyes: Boolean get() = eye == EYE_RED || eye == EYE_ORANGE

    companion object {
        const val EYE_RED = 0xFFFF3B30.toInt()
        const val EYE_VIOLET = 0xFFB07AFF.toInt()
        const val EYE_ORANGE = 0xFFFF7A2E.toInt()
        const val OUTLINE = 0xFF0A0A0C.toInt()
    }
}

object MonsterDesign {

    fun look(kind: EnemyKind, element: DamageElement, enraged: Boolean): MonsterLook = when (kind) {
        EnemyKind.NORMAL -> MonsterLook(
            build = MonsterBuild.HORNED_BRUTE,
            body = 0xFF5A4740.toInt(),
            rim = 0xFF8C6A55.toInt(),
            outline = MonsterLook.OUTLINE,
            eye = if (enraged) MonsterLook.EYE_ORANGE else MonsterLook.EYE_RED,
            height = 46f,
            width = 26f,
            horns = 2,
            spikes = 3,
            wings = false,
            armored = false,
            aura = 0,
            auraRadius = 0f,
        )
        EnemyKind.MAGIC -> MonsterLook(
            build = MonsterBuild.HOODED_CASTER,
            body = 0xFF3A3358.toInt(),
            rim = 0xFF6B5F9E.toInt(),
            outline = MonsterLook.OUTLINE,
            eye = MonsterLook.EYE_VIOLET,
            height = 50f,
            width = 24f,
            horns = 0,
            spikes = 2,
            wings = false,
            armored = false,
            aura = element.color,
            auraRadius = 40f,
        )
        EnemyKind.RARE -> MonsterLook(
            build = MonsterBuild.ARMORED_ELITE,
            body = 0xFF6A5535.toInt(),
            rim = 0xFFC9A24E.toInt(),
            outline = MonsterLook.OUTLINE,
            eye = if (enraged) MonsterLook.EYE_RED else MonsterLook.EYE_ORANGE,
            height = 58f,
            width = 34f,
            horns = 2,
            spikes = 4,
            wings = true,
            armored = true,
            aura = 0xFFFFC24D.toInt(),
            auraRadius = 56f,
        )
        EnemyKind.BOSS -> MonsterLook(
            build = MonsterBuild.BOSS,
            body = 0xFF7A3226.toInt(),
            rim = 0xFFFF8A66.toInt(),
            outline = MonsterLook.OUTLINE,
            eye = MonsterLook.EYE_RED,
            height = 96f,
            width = 58f,
            horns = 4,
            spikes = 6,
            wings = true,
            armored = true,
            aura = element.color,
            auraRadius = 110f,
        )
    }
}
