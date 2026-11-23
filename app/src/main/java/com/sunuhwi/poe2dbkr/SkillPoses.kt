package com.sunuhwi.poe2dbkr

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * 스틱맨의 관절 자세.
 *
 * 팔·다리를 막대기 하나로 긋지 않고 상완/전완, 허벅지/종아리로 나눠 꺾는다.
 * 각도는 모두 무릎·팔이 늘어뜨려진 아래 방향이 0이고, 바라보는 쪽이 양수다.
 * 무기 각도만 화면 좌표계(0 = 오른쪽, 양수 = 아래)를 쓴다.
 */
data class JointPose(
    /** 엉덩이를 앞뒤로 미는 거리 */
    val hipShift: Float = 0f,
    /** 엉덩이를 낮추는 거리 — 앉아서 힘을 모으거나 내려찍을 때 */
    val crouch: Float = 0f,
    /** 몸통을 앞으로 숙인 각도 */
    val lean: Float = 0.04f,
    /** 머리 젖힘 (몸통 각도에 더한다) */
    val headTilt: Float = 0f,
    val shoulderFront: Float = 0.35f,
    val elbowFront: Float = 0.25f,
    val shoulderBack: Float = -0.15f,
    val elbowBack: Float = 0.22f,
    val thighFront: Float = 0.10f,
    val kneeFront: Float = 0.06f,
    val thighBack: Float = -0.10f,
    val kneeBack: Float = 0.06f,
    /** 무기 각도 (바라보는 방향 기준 상대값) */
    val weapon: Float = 0.9f,
    /** 온몸 회전 — 소용돌이베기의 360도 회전 */
    val spin: Float = 0f,
    /** 몸이 떠오른 높이 */
    val rise: Float = 0f,
    /** 두 손으로 무기를 쥐는지 */
    val twoHanded: Boolean = false,
    /** 현재 자세를 화면에 얼마나 반영할지 (공격 중 1, 이동 중 0) */
    val weight: Float = 1f,
) {
    companion object {
        val IDLE = JointPose()
    }
}

/** 관절 좌표 — 그리기와 검증이 같은 계산을 쓰도록 한곳에서 낸다 */
data class SkeletonJoints(
    val hipX: Float, val hipY: Float,
    val shoulderX: Float, val shoulderY: Float,
    val neckX: Float, val neckY: Float,
    val headX: Float, val headY: Float, val headR: Float,
    val frontElbowX: Float, val frontElbowY: Float,
    val frontHandX: Float, val frontHandY: Float,
    val backElbowX: Float, val backElbowY: Float,
    val backHandX: Float, val backHandY: Float,
    val frontKneeX: Float, val frontKneeY: Float,
    val frontFootX: Float, val frontFootY: Float,
    val backKneeX: Float, val backKneeY: Float,
    val backFootX: Float, val backFootY: Float,
    /** 무기 끝 좌표 */
    val weaponTipX: Float, val weaponTipY: Float,
    val scale: Float,
) {
    val torsoLength: Float get() = abs(hipY - shoulderY)
}

/**
 * 스킬별 키프레임.
 *
 * 각 스킬은 인게임 시전 동작을 보고 잡았다 — 활은 시위를 당겼다 놓고,
 * 혜성은 두 손을 하늘로 들었다가 땅으로 내리꽂고, 전기불꽃은 마법봉을 채찍처럼 앞으로 튕긴다.
 * 어느 스킬도 같은 자세로 끝나지 않게 관절 각도를 따로 잡아 두었다.
 */
object SkillPoses {

    /** 키프레임 한 장 — t는 시전 진행도(0~1) */
    private data class Frame(
        val t: Float,
        val crouch: Float = 0f,
        val lean: Float = 0.04f,
        val head: Float = 0f,
        val shF: Float = 0.35f,
        val elF: Float = 0.25f,
        val shB: Float = -0.15f,
        val elB: Float = 0.22f,
        val thF: Float = 0.10f,
        val knF: Float = 0.06f,
        val thB: Float = -0.10f,
        val knB: Float = 0.06f,
        val wep: Float = 0.9f,
        val hip: Float = 0f,
        val rise: Float = 0f,
        val two: Boolean = false,
    )

    private fun frames(vararg frames: Frame) = frames.toList()

    // ---------------------------------------------------------------- 기본형

    private val BOW_HOLD = Frame(
        0f, lean = -0.06f, shF = 1.42f, elF = 0.06f, shB = 0.95f, elB = -1.55f,
        thF = 0.22f, thB = -0.22f, wep = 0f,
    )
    private val BOW_RELEASE = Frame(
        0.62f, lean = 0.12f, crouch = 1.5f, shF = 1.30f, elF = 0.18f, shB = -0.55f, elB = 0.35f,
        thF = 0.26f, thB = -0.26f, wep = 0f,
    )
    private val CAST_RAISE = Frame(
        0f, lean = -0.10f, head = -0.12f, shF = -0.55f, elF = 1.05f, shB = -0.30f, elB = 0.55f,
        wep = -1.15f,
    )
    private val CAST_THRUST = Frame(
        0.42f, lean = 0.24f, shF = 1.38f, elF = 0.10f, shB = -0.55f, elB = 0.70f,
        thF = 0.20f, thB = -0.26f, wep = 0f,
    )

    private fun defaultTrack(form: AttackForm): List<Frame> = when (form) {
        AttackForm.BOW -> frames(BOW_HOLD, BOW_RELEASE, BOW_HOLD.copy(t = 1f, shB = 0.6f, elB = -0.9f))
        AttackForm.CROSSBOW -> frames(
            Frame(0f, lean = 0.02f, shF = 1.36f, elF = 0.22f, shB = 1.10f, elB = 0.55f, wep = 0.02f),
            Frame(0.5f, lean = -0.14f, crouch = 1.2f, shF = 1.20f, elF = 0.30f, shB = 0.92f, elB = 0.62f, wep = 0f),
            Frame(1f, lean = 0.02f, shF = 1.34f, elF = 0.24f, shB = 1.05f, elB = 0.58f, wep = 0.02f),
        )
        AttackForm.CAST -> frames(CAST_RAISE, CAST_THRUST, CAST_RAISE.copy(t = 1f, shF = 0.2f))
        AttackForm.UNARMED -> frames(
            Frame(0f, lean = 0.10f, shF = 0.55f, elF = 0.55f, shB = -0.35f, elB = 0.85f),
            Frame(0.34f, lean = 0.26f, hip = 5f, shF = 1.50f, elF = 0.02f, shB = -0.70f, elB = 0.55f),
            Frame(1f, lean = 0.08f, shF = 0.6f, elF = 0.5f, shB = -0.3f, elB = 0.8f),
        )
        AttackForm.MELEE -> frames(
            Frame(0f, lean = -0.08f, shF = -1.05f, elF = 1.15f, shB = -0.55f, elB = 0.75f, wep = -1.35f),
            Frame(0.40f, lean = 0.30f, crouch = 2.5f, hip = 4f, shF = 1.45f, elF = 0.12f, shB = -0.30f, elB = 0.35f, wep = 0.35f),
            Frame(0.72f, lean = 0.16f, shF = 0.75f, elF = 0.35f, shB = -0.10f, elB = 0.30f, wep = 0.95f),
            Frame(1f, lean = 0.02f, shF = 0.30f, elF = 0.28f, shB = -0.15f, elB = 0.25f, wep = 0.85f),
        )
    }

    // ---------------------------------------------------------------- 스킬별 동작

    /** 소용돌이 베기: 몸을 낮춰 크게 감아 돌며 360도로 벤다 */
    private val WHIRLING_SLASH = frames(
        Frame(0f, crouch = 3.5f, lean = 0.34f, shF = -1.15f, elF = 0.35f, shB = -1.55f, elB = 0.45f,
            thF = 0.35f, knF = 0.45f, thB = -0.30f, knB = 0.40f, wep = -1.75f, hip = -3f),
        Frame(0.30f, crouch = 5f, lean = 0.45f, shF = 1.95f, elF = 0.06f, shB = 0.85f, elB = 0.20f,
            thF = 0.42f, knF = 0.55f, thB = -0.34f, knB = 0.45f, wep = 0.42f, hip = 4f),
        Frame(0.58f, crouch = 4f, lean = 0.30f, shF = 0.45f, elF = 0.10f, shB = 1.85f, elB = 0.10f,
            thF = 0.34f, knF = 0.48f, thB = -0.26f, knB = 0.38f, wep = 1.45f, hip = 2f),
        Frame(0.82f, crouch = 2f, lean = 0.16f, shF = 0.10f, elF = 0.30f, shB = 0.35f, elB = 0.35f,
            thF = 0.20f, knF = 0.22f, thB = -0.18f, knB = 0.20f, wep = 1.10f),
        Frame(1f, lean = 0.04f, shF = 0.32f, elF = 0.26f, shB = -0.15f, elB = 0.24f, wep = 0.9f),
    )

    /** 돌개바람: 두 팔을 머리 위로 모아 내려친 뒤 크게 휘돌려 회오리를 띄운다 */
    private val TWISTER = frames(
        Frame(0f, lean = -0.14f, head = -0.20f, shF = -2.55f, elF = -0.30f, shB = -2.35f, elB = -0.28f,
            thF = 0.24f, thB = -0.26f, wep = -2.35f, two = true),
        Frame(0.34f, crouch = 7f, lean = 0.55f, shF = 1.55f, elF = 0.55f, shB = 1.35f, elB = 0.60f,
            thF = 0.46f, knF = 0.62f, thB = -0.36f, knB = 0.50f, wep = 1.15f, two = true),
        Frame(0.58f, crouch = 4f, lean = 0.36f, shF = 0.10f, elF = 0.15f, shB = 1.90f, elB = 0.05f,
            thF = 0.36f, knF = 0.42f, thB = -0.30f, knB = 0.34f, wep = 0.05f, hip = 3f),
        Frame(0.80f, lean = 0.18f, shF = -0.30f, elF = 0.35f, shB = 0.45f, elB = 0.30f, wep = -0.55f),
        Frame(1f, lean = 0.04f, shF = 0.32f, elF = 0.26f, shB = -0.15f, elB = 0.24f, wep = 0.9f),
    )

    /** 전기불꽃: 마법봉을 뒤로 눕혔다가 채찍처럼 앞으로 튕긴다 (세 번 튕긴다) */
    private val SPARK = frames(
        Frame(0f, lean = -0.16f, head = -0.10f, shF = -0.70f, elF = 1.25f, shB = -0.45f, elB = 0.65f,
            thF = 0.22f, thB = -0.24f, wep = -1.55f),
        Frame(0.18f, lean = 0.22f, shF = 1.60f, elF = -0.10f, shB = -0.55f, elB = 0.85f,
            thF = 0.30f, thB = -0.28f, wep = -0.10f, hip = 3f),
        Frame(0.38f, lean = 0.06f, shF = 0.75f, elF = 0.55f, shB = -0.40f, elB = 0.70f, wep = -0.95f),
        Frame(0.56f, lean = 0.26f, shF = 1.55f, elF = -0.05f, shB = -0.55f, elB = 0.85f,
            thF = 0.32f, thB = -0.30f, wep = 0.05f, hip = 4f),
        Frame(0.76f, lean = 0.10f, shF = 0.85f, elF = 0.45f, shB = -0.40f, elB = 0.65f, wep = -0.75f),
        Frame(1f, lean = 0.04f, shF = 0.40f, elF = 0.30f, shB = -0.16f, elB = 0.24f, wep = 0.55f),
    )

    /** 연발 사격: 활을 앞으로 내밀고 시위를 당겼다 놓기를 빠르게 반복한다 */
    private val BARRAGE = frames(
        Frame(0f, lean = -0.05f, shF = 1.46f, elF = 0.04f, shB = 1.00f, elB = -1.60f,
            thF = 0.24f, thB = -0.24f, wep = 0f, two = true),
        Frame(0.22f, lean = 0.10f, shF = 1.34f, elF = 0.16f, shB = -0.50f, elB = 0.30f,
            thF = 0.28f, thB = -0.28f, wep = 0f),
        Frame(0.44f, lean = -0.02f, shF = 1.44f, elF = 0.08f, shB = 0.95f, elB = -1.35f, wep = 0f),
        Frame(0.66f, lean = 0.12f, shF = 1.32f, elF = 0.18f, shB = -0.45f, elB = 0.28f, wep = 0f),
        Frame(1f, lean = 0f, shF = 1.42f, elF = 0.10f, shB = 0.75f, elB = -1.05f, wep = 0f),
    )

    /** 번개 화살: 활시위를 끝까지 당겨 눈까지 끌어온 뒤 놓는다 */
    private val LIGHTNING_ARROW = frames(
        Frame(0f, lean = -0.10f, head = -0.06f, shF = 1.48f, elF = 0.02f, shB = 1.05f, elB = -1.75f,
            thF = 0.26f, thB = -0.26f, wep = 0f),
        Frame(0.34f, lean = -0.18f, head = -0.10f, shF = 1.50f, elF = 0.0f, shB = 1.05f, elB = -2.90f,
            thF = 0.30f, thB = -0.30f, wep = 0f),
        Frame(0.52f, lean = 0.18f, crouch = 1.4f, shF = 1.22f, elF = 0.24f, shB = -0.70f, elB = 0.45f,
            thF = 0.32f, thB = -0.30f, wep = 0f),
        Frame(0.78f, lean = 0.06f, shF = 1.36f, elF = 0.14f, shB = 0.30f, elB = -0.30f, wep = 0f),
        Frame(1f, lean = 0f, shF = 1.40f, elF = 0.12f, shB = 0.70f, elB = -1.00f, wep = 0f),
    )

    /** 얼음 화살: 같은 활이지만 몸을 낮게 깔고 짧게 쏜다 */
    private val ICE_SHOT = frames(
        Frame(0f, crouch = 2.5f, lean = 0.12f, shF = 1.32f, elF = 0.10f, shB = 0.90f, elB = -1.45f,
            thF = 0.34f, knF = 0.34f, thB = -0.30f, knB = 0.30f, wep = 0f),
        Frame(0.36f, crouch = 4f, lean = 0.02f, shF = 1.38f, elF = 0.06f, shB = 1.20f, elB = -1.85f,
            thF = 0.42f, knF = 0.46f, thB = -0.34f, knB = 0.38f, wep = 0f),
        Frame(0.58f, crouch = 2f, lean = -0.06f, shF = 1.28f, elF = 0.18f, shB = -0.55f, elB = 0.40f, wep = 0f),
        Frame(1f, crouch = 1f, lean = 0.08f, shF = 1.34f, elF = 0.12f, shB = 0.60f, elB = -0.80f, wep = 0f),
    )

    /** 혜성: 두 손을 하늘로 치켜들어 얼음을 부른 뒤 땅으로 내리꽂는다 */
    private val COMET = frames(
        Frame(0f, lean = -0.26f, head = -0.34f, shF = -2.70f, elF = -0.35f, shB = -2.60f, elB = -0.30f,
            thF = 0.20f, thB = -0.22f, wep = -2.55f, two = true),
        Frame(0.30f, rise = 3f, lean = -0.34f, head = -0.42f, shF = -2.90f, elF = -0.45f, shB = -2.80f, elB = -0.40f,
            thF = 0.16f, thB = -0.20f, wep = -2.75f, two = true),
        Frame(0.56f, crouch = 10f, lean = 0.78f, head = 0.10f, shF = 0.45f, elF = 0.20f, shB = 0.35f, elB = 0.25f,
            thF = 0.55f, knF = 0.80f, thB = -0.40f, knB = 0.70f, wep = 0.55f, two = true),
        Frame(0.74f, crouch = 8f, lean = 0.62f, shF = 0.60f, elF = 0.30f, shB = 0.45f, elB = 0.30f,
            thF = 0.48f, knF = 0.66f, thB = -0.36f, knB = 0.58f, wep = 0.80f, two = true),
        Frame(1f, crouch = 2f, lean = 0.16f, shF = 0.45f, elF = 0.35f, shB = -0.10f, elB = 0.30f, wep = 0.85f),
    )

    /** 폭풍의 종: 지팡이를 높이 들어 올렸다가 두 손으로 땅에 박는다 */
    private val TEMPEST_BELL = frames(
        Frame(0f, lean = -0.22f, head = -0.24f, shF = -2.30f, elF = 0.55f, shB = -2.05f, elB = 0.60f,
            wep = -2.20f, two = true),
        Frame(0.22f, rise = 2f, lean = -0.30f, shF = -2.60f, elF = 0.45f, shB = -2.40f, elB = 0.50f,
            wep = -2.45f, two = true),
        Frame(0.50f, crouch = 11f, lean = 0.70f, head = 0.16f, shF = 1.62f, elF = 0.35f, shB = 1.50f, elB = 0.40f,
            thF = 0.58f, knF = 0.86f, thB = -0.42f, knB = 0.72f, wep = 1.40f, two = true),
        Frame(0.70f, crouch = 7f, lean = 0.42f, shF = 1.05f, elF = 0.55f, shB = 0.95f, elB = 0.60f,
            thF = 0.44f, knF = 0.56f, thB = -0.32f, knB = 0.48f, wep = 1.10f, two = true),
        Frame(1f, crouch = 2f, lean = 0.12f, shF = 0.40f, elF = 0.30f, shB = -0.12f, elB = 0.26f, wep = 0.9f),
    )

    /** 살상 장법: 낮게 파고들어 손바닥을 꽂고, 반대팔로 균형을 잡는다 */
    private val KILLING_PALM = frames(
        Frame(0f, crouch = 3f, lean = 0.20f, shF = 0.70f, elF = 1.10f, shB = -0.55f, elB = 0.95f,
            thF = 0.40f, knF = 0.55f, thB = -0.34f, knB = 0.45f),
        Frame(0.28f, crouch = 6f, lean = 0.55f, hip = 7f, shF = 1.62f, elF = -0.05f, shB = -1.30f, elB = 0.55f,
            thF = 0.85f, knF = 0.20f, thB = -0.70f, knB = 0.55f),
        Frame(0.50f, crouch = 4f, lean = 0.40f, hip = 4f, shF = 1.50f, elF = 0.05f, shB = -1.05f, elB = 0.70f,
            thF = 0.70f, knF = 0.30f, thB = -0.55f, knB = 0.45f),
        Frame(1f, crouch = 1f, lean = 0.14f, shF = 0.55f, elF = 0.45f, shB = -0.25f, elB = 0.35f),
    )

    /** 점멸 타격: 낮게 숙여 칼을 옆구리에 감았다가 순식간에 뽑아 벤다 */
    private val FLICKER = frames(
        Frame(0f, crouch = 5f, lean = 0.42f, shF = -0.35f, elF = 1.35f, shB = -0.85f, elB = 0.90f,
            thF = 0.34f, knF = 0.55f, thB = -0.30f, knB = 0.48f, wep = -1.90f),
        Frame(0.24f, lean = 0.20f, shF = 1.72f, elF = 0.02f, shB = -0.60f, elB = 0.55f,
            thF = 0.55f, knF = 0.25f, thB = -0.40f, knB = 0.30f, wep = 0.15f),
        Frame(0.46f, lean = 0.10f, shF = 1.20f, elF = 0.20f, shB = -0.35f, elB = 0.40f, wep = 0.75f),
        Frame(1f, lean = 0.05f, shF = 0.40f, elF = 0.30f, shB = -0.18f, elB = 0.26f, wep = 0.9f),
    )

    /** 저주: 두 팔을 옆으로 벌리고 손바닥을 펴 저주 문양을 띄운다 */
    private val CURSE = frames(
        Frame(0f, lean = -0.12f, head = -0.18f, shF = 0.85f, elF = 0.20f, shB = -0.95f, elB = 0.25f,
            thF = 0.18f, thB = -0.18f, wep = -0.55f),
        Frame(0.42f, lean = -0.20f, head = -0.26f, shF = 1.35f, elF = 0.02f, shB = -1.35f, elB = 0.05f,
            thF = 0.22f, thB = -0.22f, wep = -0.20f),
        Frame(0.72f, lean = 0.10f, shF = 0.95f, elF = -0.25f, shB = -0.95f, elB = -0.25f, wep = -0.80f),
        Frame(1f, lean = 0.04f, shF = 0.4f, elF = 0.28f, shB = -0.2f, elB = 0.25f, wep = 0.8f),
    )

    /** 폭발 유탄: 뒤로 크게 젖혀 던지고, 던진 뒤 몸이 앞으로 쏠린다 */
    private val GRENADE = frames(
        Frame(0f, lean = -0.28f, head = -0.10f, shF = 0.55f, elF = 0.45f, shB = -2.45f, elB = 1.25f,
            thF = 0.20f, thB = -0.24f, wep = -2.30f),
        Frame(0.26f, lean = 0.36f, hip = 5f, shF = 1.05f, elF = 0.30f, shB = 1.60f, elB = 0.05f,
            thF = 0.40f, knF = 0.30f, thB = -0.34f, knB = 0.28f, wep = -0.20f),
        Frame(0.52f, lean = 0.24f, shF = 0.75f, elF = 0.40f, shB = 1.10f, elB = 0.20f, wep = -0.40f),
        Frame(1f, lean = 0.04f, shF = 0.35f, elF = 0.26f, shB = -0.15f, elB = 0.24f, wep = 0.85f),
    )

    /** 기름 포화: 와이번이 기름을 토해 내듯 상체를 숙이고 두 손을 앞으로 뻗어 계속 뿜는다 */
    private val OIL_BARRAGE = frames(
        Frame(0f, crouch = 3f, lean = 0.36f, head = 0.10f, shF = 1.35f, elF = 0.30f, shB = 1.15f, elB = 0.35f,
            thF = 0.38f, knF = 0.38f, thB = -0.30f, knB = 0.34f, wep = 0.10f, two = true),
        Frame(0.22f, crouch = 4.5f, lean = 0.44f, shF = 1.52f, elF = 0.22f, shB = 1.32f, elB = 0.26f,
            thF = 0.44f, knF = 0.44f, thB = -0.34f, knB = 0.38f, wep = 0.02f, two = true),
        Frame(0.50f, crouch = 5f, lean = 0.50f, shF = 1.62f, elF = 0.18f, shB = 1.42f, elB = 0.22f,
            thF = 0.48f, knF = 0.48f, thB = -0.36f, knB = 0.40f, wep = -0.04f, two = true),
        Frame(0.76f, crouch = 3.5f, lean = 0.40f, shF = 1.44f, elF = 0.28f, shB = 1.24f, elB = 0.32f, wep = 0.06f, two = true),
        Frame(1f, crouch = 3f, lean = 0.36f, shF = 1.35f, elF = 0.30f, shB = 1.15f, elB = 0.35f, wep = 0.10f, two = true),
    )

    /** 기름 유탄: 손을 낮게 눕혀 아래로 굴려 던진다 */
    private val OIL_GRENADE = frames(
        Frame(0f, crouch = 4f, lean = 0.05f, shF = 0.40f, elF = 0.60f, shB = -1.65f, elB = 0.45f,
            thF = 0.28f, knF = 0.30f, thB = -0.26f, knB = 0.28f, wep = -1.45f),
        Frame(0.30f, crouch = 2f, lean = 0.40f, hip = 4f, shF = 1.20f, elF = 0.10f, shB = 1.20f, elB = 0.15f,
            thF = 0.38f, knF = 0.26f, thB = -0.32f, knB = 0.24f, wep = 0.30f),
        Frame(1f, crouch = 2f, lean = 0.12f, shF = 0.60f, elF = 0.35f, shB = -0.20f, elB = 0.30f, wep = 0.70f),
    )

    /** 주문 토템: 한 손을 땅에 짚어 토템을 세운다 */
    private val TOTEM = frames(
        Frame(0f, crouch = 2f, lean = 0.20f, shF = 0.60f, elF = 0.80f, shB = -0.30f, elB = 0.60f),
        Frame(0.35f, crouch = 12f, lean = 0.80f, hip = 2f, shF = 1.15f, elF = 0.95f, shB = -0.20f, elB = 1.10f,
            thF = 0.62f, knF = 0.95f, thB = -0.46f, knB = 0.80f),
        Frame(0.62f, crouch = 6f, lean = 0.42f, shF = 0.85f, elF = 0.55f, shB = -0.25f, elB = 0.55f,
            thF = 0.42f, knF = 0.52f, thB = -0.34f, knB = 0.44f),
        Frame(1f, crouch = 1f, lean = 0.10f, shF = 0.4f, elF = 0.3f, shB = -0.15f, elB = 0.24f),
    )

    /** 야수의 기본 공격: 앞발을 크게 들어 내리친다 */
    /** 회오리 사격: 활을 높이 쳐들어 하늘로 쏘아 올린다 */
    private val TORNADO_SHOT = frames(
        Frame(0f, lean = -0.24f, head = -0.30f, shF = 1.10f, elF = 0.06f, shB = 0.90f, elB = -1.60f,
            thF = 0.22f, thB = -0.24f, wep = -0.30f),
        Frame(0.34f, lean = -0.34f, head = -0.38f, shF = 1.05f, elF = 0.02f, shB = 1.15f, elB = -1.95f,
            thF = 0.26f, thB = -0.28f, wep = -0.45f),
        Frame(0.58f, lean = -0.10f, shF = 0.80f, elF = 0.16f, shB = -0.45f, elB = 0.35f, wep = -0.20f),
        Frame(1f, lean = -0.04f, shF = 1.00f, elF = 0.10f, shB = 0.60f, elB = -0.90f, wep = -0.10f),
    )

    /** 와이번 숨결 — 목을 길게 내밀고 두 손으로 뿌린다 */
    private val WYVERN_BREATH = frames(
        Frame(0f, crouch = 2f, lean = 0.10f, head = -0.18f, shF = 0.85f, elF = 0.55f, shB = 0.70f, elB = 0.60f,
            thF = 0.26f, knF = 0.28f, thB = -0.24f, knB = 0.26f),
        Frame(0.30f, crouch = 4f, lean = 0.46f, head = 0.16f, shF = 1.55f, elF = 0.14f, shB = 1.40f, elB = 0.20f,
            thF = 0.40f, knF = 0.40f, thB = -0.32f, knB = 0.34f),
        Frame(0.62f, crouch = 3f, lean = 0.38f, head = 0.06f, shF = 1.30f, elF = 0.30f, shB = 1.15f, elB = 0.34f),
        Frame(1f, crouch = 1f, lean = 0.12f, shF = 0.55f, elF = 0.35f, shB = -0.10f, elB = 0.28f),
    )

    /** 화염구: 손바닥 앞에 불덩이를 모았다가 밀어낸다 */
    private val FIREBALL = frames(
        Frame(0f, lean = -0.06f, shF = 0.25f, elF = 0.95f, shB = -0.25f, elB = 0.55f, wep = -0.35f),
        Frame(0.30f, lean = 0.12f, shF = 1.05f, elF = 0.60f, shB = -0.35f, elB = 0.65f, wep = -0.10f),
        Frame(0.52f, lean = 0.30f, shF = 1.50f, elF = 0.05f, shB = -0.55f, elB = 0.75f,
            thF = 0.28f, thB = -0.30f, wep = 0.05f),
        Frame(0.74f, lean = 0.14f, shF = 0.95f, elF = 0.30f, shB = -0.30f, elB = 0.50f, wep = 0.35f),
        Frame(1f, lean = 0.04f, shF = 0.35f, elF = 0.30f, shB = -0.15f, elB = 0.25f, wep = 0.8f),
    )

    /** 선회 공격: 두 팔을 수평으로 뻗은 채 몸을 돌려 전진한다 */
    private val WHIRLING_ASSAULT = frames(
        Frame(0f, crouch = 2.5f, lean = 0.24f, shF = 1.55f, elF = 0.20f, shB = -1.45f, elB = 0.25f,
            thF = 0.40f, knF = 0.42f, thB = -0.34f, knB = 0.36f, wep = 1.45f),
        Frame(0.34f, crouch = 4f, lean = 0.34f, shF = 1.70f, elF = 0.10f, shB = 1.55f, elB = 0.15f,
            thF = 0.55f, knF = 0.50f, thB = -0.42f, knB = 0.40f, wep = 1.60f),
        Frame(0.66f, crouch = 3f, lean = 0.28f, shF = 1.62f, elF = 0.16f, shB = -1.35f, elB = 0.20f,
            thF = 0.46f, knF = 0.44f, thB = -0.36f, knB = 0.38f, wep = 1.50f),
        Frame(1f, crouch = 1.5f, lean = 0.16f, shF = 0.70f, elF = 0.35f, shB = -0.40f, elB = 0.30f, wep = 1.10f),
    )

    private val BEAST_MAUL = frames(
        Frame(0f, lean = -0.10f, shF = -0.95f, elF = 0.75f, shB = -0.70f, elB = 0.80f, wep = -0.6f),
        Frame(0.36f, lean = 0.42f, crouch = 4f, shF = 1.30f, elF = 0.30f, shB = 1.05f, elB = 0.35f, wep = 0.5f),
        Frame(0.68f, lean = 0.22f, shF = 0.70f, elF = 0.55f, shB = 0.45f, elB = 0.60f, wep = 0.9f),
        Frame(1f, lean = 0.04f, shF = 0.3f, elF = 0.3f, shB = -0.1f, elB = 0.25f, wep = 0.8f),
    )

    /** 야수의 함성: 고개를 젖히고 가슴을 편다 */
    private val BEAST_HOWL = frames(
        Frame(0f, crouch = 3f, lean = 0.30f, head = 0.20f, shF = 0.50f, elF = 0.55f, shB = 0.45f, elB = 0.60f),
        Frame(0.30f, rise = 2f, lean = -0.30f, head = -0.40f, shF = -1.10f, elF = 0.35f, shB = -1.00f, elB = 0.40f),
        Frame(0.64f, lean = -0.22f, head = -0.30f, shF = -0.85f, elF = 0.45f, shB = -0.75f, elB = 0.5f),
        Frame(1f, lean = 0.04f, shF = 0.3f, elF = 0.3f, shB = 0.25f, elB = 0.3f),
    )

    private fun trackFor(motion: SkillMotion, form: AttackForm): List<Frame> {
        val named = when (motion) {
            SkillMotion.WHIRLING_SLASH -> WHIRLING_SLASH
            SkillMotion.TWISTER -> TWISTER
            SkillMotion.SPARK -> SPARK
            SkillMotion.BARRAGE -> BARRAGE
            SkillMotion.LIGHTNING_ARROW -> LIGHTNING_ARROW
            SkillMotion.ICE_SHOT -> ICE_SHOT
            SkillMotion.TORNADO_SHOT -> TORNADO_SHOT
            SkillMotion.COMET -> COMET
            SkillMotion.TEMPEST_BELL -> TEMPEST_BELL
            SkillMotion.KILLING_PALM -> KILLING_PALM
            SkillMotion.FLICKER -> FLICKER
            SkillMotion.CURSE -> CURSE
            SkillMotion.GRENADE -> GRENADE
            SkillMotion.OIL_BARRAGE -> OIL_BARRAGE
            SkillMotion.FIREBALL -> FIREBALL
            SkillMotion.WHIRLING_ASSAULT -> WHIRLING_ASSAULT
            SkillMotion.OIL_GRENADE -> OIL_GRENADE
            SkillMotion.TOTEM -> TOTEM
            SkillMotion.BEAST_MAUL -> BEAST_MAUL
            SkillMotion.BEAST_HOWL -> BEAST_HOWL
            SkillMotion.WYVERN_BREATH -> WYVERN_BREATH
            else -> emptyList()
        }
        return if (named.isNotEmpty()) named else defaultTrack(form)
    }

    private fun ease(t: Float): Float = t * t * (3f - 2f * t)

    private fun sample(track: List<Frame>, phase: Float): Frame {
        if (track.isEmpty()) return Frame(0f)
        val p = phase.coerceIn(0f, 1f)
        var i = 0
        while (i < track.size - 1 && p > track[i + 1].t) i++
        val a = track[i]
        val b = track[min(track.size - 1, i + 1)]
        val span = (b.t - a.t).takeIf { it > 0.0001f } ?: 1f
        val k = ease(((p - a.t) / span).coerceIn(0f, 1f))
        fun mix(x: Float, y: Float) = x + (y - x) * k
        return Frame(
            t = p,
            crouch = mix(a.crouch, b.crouch),
            lean = mix(a.lean, b.lean),
            head = mix(a.head, b.head),
            shF = mix(a.shF, b.shF),
            elF = mix(a.elF, b.elF),
            shB = mix(a.shB, b.shB),
            elB = mix(a.elB, b.elB),
            thF = mix(a.thF, b.thF),
            knF = mix(a.knF, b.knF),
            thB = mix(a.thB, b.thB),
            knB = mix(a.knB, b.knB),
            wep = mix(a.wep, b.wep),
            hip = mix(a.hip, b.hip),
            rise = mix(a.rise, b.rise),
            two = if (k < 0.5f) a.two else b.two,
        )
    }

    /**
     * 지금 프레임의 관절 자세를 만든다.
     *
     * @param attack 1에서 0으로 줄어드는 공격 애니메이션 값
     * @param channel 기름 포화처럼 집중 유지 중일 때의 경과 시간(초)
     */
    fun pose(
        motion: SkillMotion,
        form: AttackForm,
        attack: Float,
        walk: Float,
        moving: Boolean,
        time: Float,
        channel: Float = 0f,
    ): JointPose {
        val a = attack.coerceIn(0f, 1f)
        // 시전 중에는 0→1로 진행하고, 끝나면 서 있는 자세로 돌아간다.
        val phase = when {
            channel > 0f -> ((channel * 1.55f) % 1f)
            a > 0.01f -> (1f - a)
            else -> 0f
        }
        val track = trackFor(motion, form)
        val f = sample(track, phase)
        val swing = if (moving) sin(walk) else sin(walk * 0.62f) * 0.28f
        val swing2 = if (moving) sin(walk + 3.1416f) else -sin(walk * 0.62f) * 0.28f

        // 서 있는 자세: 걸음에 맞춰 다리가 번갈아 들리고 팔이 살짝 반대로 흔들린다.
        val idleThF = 0.10f + swing * 0.46f
        val idleKnF = 0.06f + max(0f, -swing) * 0.55f
        val idleThB = -0.10f + swing2 * 0.46f
        val idleKnB = 0.06f + max(0f, -swing2) * 0.55f
        // 시전 앞부분은 키프레임을 그대로 쓰고, 끝날 때만 서 있는 자세로 돌아간다.
        val w = if (a > 0.01f || channel > 0f) (a * 2.2f).coerceIn(0f, 1f) else 0f
        val idleArm = swing * 0.25f

        fun mix(x: Float, y: Float) = x + (y - x) * w
        val breath = sin(time * 1.7f) * 0.012f
        return JointPose(
            hipShift = f.hip * w,
            crouch = f.crouch * w,
            lean = mix(0.04f + breath, f.lean),
            headTilt = mix(0f, f.head),
            shoulderFront = mix(0.35f - idleArm, f.shF),
            elbowFront = mix(0.25f, f.elF),
            shoulderBack = mix(-0.15f + idleArm, f.shB),
            elbowBack = mix(0.22f, f.elB),
            thighFront = mix(idleThF, f.thF),
            kneeFront = mix(idleKnF, f.knF),
            thighBack = mix(idleThB, f.thB),
            kneeBack = mix(idleKnB, f.knB),
            weapon = mix(0.9f + sin(time * 1.3f) * 0.03f, f.wep),
            spin = 0f,
            rise = f.rise * w,
            twoHanded = f.two && w > 0.35f,
            weight = w,
        )
    }

    /** 소용돌이 계열은 몸통 자체가 돈다 — 그리기·검증이 같이 쓴다 */
    fun bodySpin(motion: SkillMotion, attack: Float, spin: Float = 0f): Float {
        if (motion != SkillMotion.WHIRLING_SLASH && motion != SkillMotion.WHIRLING_ASSAULT) return 0f
        val a = attack.coerceIn(0f, 1f)
        val turn = (1f - a) * 6.2832f
        return spin + turn
    }

    /** 관절 좌표 계산 — 그리기와 테스트가 같은 값을 본다. */
    fun skeleton(x: Float, y: Float, s: Float, pose: JointPose, facing: Float): SkeletonJoints {
        val dir = if (cos(facing) >= 0f) 1f else -1f
        val legLen = 17f * s
        val torsoLen = 19f * s
        val headR = 6.4f * s
        val rise = pose.rise * s
        val hipX = x + dir * pose.hipShift * s
        val hipY = y - legLen + pose.crouch * s - rise
        val lean = pose.lean
        val shX = hipX + dir * sin(lean) * torsoLen
        val shY = hipY - cos(lean) * torsoLen
        val headA = lean + pose.headTilt
        val headX = shX + dir * sin(headA) * headR * 1.25f
        val headY = shY - cos(headA) * headR * 1.25f

        val upper = 7.6f * s
        val fore = 7.4f * s
        fun limb(sx: Float, sy: Float, a1: Float, a2: Float): FloatArray {
            val ex = sx + dir * sin(a1) * upper
            val ey = sy + cos(a1) * upper
            val hx = ex + dir * sin(a1 + a2) * fore
            val hy = ey + cos(a1 + a2) * fore
            return floatArrayOf(ex, ey, hx, hy)
        }
        val front = limb(shX, shY, pose.shoulderFront, pose.elbowFront)
        val back = limb(shX, shY, pose.shoulderBack, pose.elbowBack)

        val thigh = legLen * 0.5f
        val shin = legLen * 0.5f
        fun leg(a1: Float, bend: Float): FloatArray {
            val kx = hipX + dir * sin(a1) * thigh
            val ky = hipY + cos(a1) * thigh
            val fa = a1 - bend
            val fx = kx + dir * sin(fa) * shin
            val fy = ky + cos(fa) * shin
            return floatArrayOf(kx, ky, fx, fy)
        }
        val lgF = leg(pose.thighFront, pose.kneeFront)
        val lgB = leg(pose.thighBack, pose.kneeBack)

        val wAng = facing + pose.weapon
        val reach = weaponReachLength(s) * 0.92f
        return SkeletonJoints(
            hipX = hipX, hipY = hipY,
            shoulderX = shX, shoulderY = shY,
            neckX = shX, neckY = shY,
            headX = headX, headY = headY, headR = headR,
            frontElbowX = front[0], frontElbowY = front[1],
            frontHandX = front[2], frontHandY = front[3],
            backElbowX = back[0], backElbowY = back[1],
            backHandX = back[2], backHandY = back[3],
            frontKneeX = lgF[0], frontKneeY = lgF[1],
            frontFootX = lgF[2], frontFootY = lgF[3],
            backKneeX = lgB[0], backKneeY = lgB[1],
            backFootX = lgB[2], backFootY = lgB[3],
            weaponTipX = front[2] + cos(wAng) * reach,
            weaponTipY = front[3] + sin(wAng) * reach,
            scale = s,
        )
    }

    /** 무기 그리기 함수들이 쓰는 길이(px) — 무기 끝 좌표 검증에도 쓴다 */
    fun weaponReachLength(s: Float): Float = 30f * s
}
