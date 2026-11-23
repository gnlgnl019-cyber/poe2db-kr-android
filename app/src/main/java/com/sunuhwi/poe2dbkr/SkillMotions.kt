package com.sunuhwi.poe2dbkr

/**
 * 스킬 젬 이름으로 인게임 모션을 고른다.
 *
 * poe2db 한글 표기와 영문 이름을 모두 받는다. 전투 시뮬레이터는 여기서 고른 모션대로
 * 투사체·궤적·회전·소환물을 그리므로, 같은 근접기라도 선회 공격(회전 전진)과
 * 소용돌이 베기(와류 생성)가 다르게 보인다.
 */
enum class SkillMotion(val label: String) {
    /** 따로 잡아 둔 모션이 없는 스킬 — 무기 모양대로 친다 */
    DEFAULT("기본 공격"),

    /** 선회 공격: 빙글빙글 돌며 앞으로 전진하는 다단 회전 베기 */
    WHIRLING_ASSAULT("선회 공격"),

    /** 돌개바람: 창을 휘둘러 전진하는 회오리를 여러 개 띄운다 */
    TWISTER("돌개바람"),

    /** 소용돌이 베기: 제자리에서 크게 휘둘러 와류를 남긴다 */
    WHIRLING_SLASH("소용돌이 베기"),

    /** 폭풍의 종: 연계를 채운 뒤 지팡이 종을 떨어뜨린다 */
    TEMPEST_BELL("폭풍의 종"),

    /** 전기불꽃: 지면을 지그재그로 기어가는 번개 덩어리 */
    SPARK("전기불꽃"),

    /** 번개 화살: 착탄 지점에서 주변으로 연쇄 번개가 뻗는다 */
    LIGHTNING_ARROW("번개 화살"),

    /** 얼음 화살: 착탄 지점에서 부채꼴로 얼음 파편이 흩어진다 */
    ICE_SHOT("얼음 화살"),

    /** 혜성: 하늘에서 얼음 덩어리가 떨어진다 */
    COMET("혜성"),

    /** 화염구: 충돌하면 폭발하며 불꽃 고리를 뿌린다 */
    FIREBALL("화염구"),

    /** 폭발 유탄: 튕겨 날아간 뒤 퓨즈가 타면 터진다 */
    GRENADE("폭발 유탄"),

    /** 살상 장법: 적에게 질주해 마무리 타격으로 권능 충전을 챙긴다 */
    KILLING_PALM("살상 장법"),

    /** 회오리 사격: 하늘로 쏜 화살이 떨어진 자리에 회오리를 만든다 */
    TORNADO_SHOT("회오리 사격"),

    /** 점멸 타격: 적 옆으로 순간이동해 벤다 */
    FLICKER("점멸 타격"),

    /** 저주·징표: 몸에서 저주가 퍼져 나간다 */
    CURSE("저주"),
    ;

    val isMelee: Boolean
        get() = this == WHIRLING_ASSAULT || this == WHIRLING_SLASH || this == TEMPEST_BELL ||
            this == KILLING_PALM || this == FLICKER || this == DEFAULT

    /** 준비 단계가 있는 콤보 스킬인지 (봇이 순서를 짜 준다) */
    val isComboPiece: Boolean
        get() = this == WHIRLING_SLASH || this == TWISTER || this == TEMPEST_BELL ||
            this == KILLING_PALM || this == CURSE
}

/** 콤보에서 이 스킬이 맡는 자리 */
enum class ComboRole {
    /** 주력 딜링 */
    MAIN,

    /** 다음 스킬을 위한 준비 (와류·연계·권능 충전) */
    BUILD,

    /** 준비를 소모하는 마무리 */
    BURST,

    /** 저주·징표처럼 먼저 걸어 두는 스킬 */
    CURSE,
}

/** 빌드에 같이 들어 있는 액티브 스킬 하나 */
data class KitSkill(
    val name: String,
    val motion: SkillMotion,
    /** PoB가 계산한 이 스킬의 DPS (없으면 0) */
    val dps: Double,
    val role: ComboRole,
)

/** 봇이 돌리는 콤보 한 칸 */
data class ComboStep(
    val label: String,
    val motion: SkillMotion,
    val role: ComboRole,
    /** 이 칸을 몇 번 반복하는지 */
    val repeats: Int = 1,
    /** 화면에 같이 적을 짧은 설명 */
    val note: String = "",
)

object SkillMotions {

    private fun has(text: String, vararg keys: String) = keys.any { text.contains(it) }

    /**
     * 스킬 이름(보조 젬·그룹 이름 포함)으로 모션을 고른다.
     * 구체적인 이름을 먼저 보고, 애매한 것(회오리·종)은 뒤에서 잡는다.
     */
    fun detect(vararg names: String): SkillMotion {
        val text = names.joinToString(" ").lowercase()
        if (text.isBlank()) return SkillMotion.DEFAULT
        return when {
            has(text, "whirling assault", "선회 공격") -> SkillMotion.WHIRLING_ASSAULT
            has(text, "whirling slash", "소용돌이 베기", "회오리 베기") -> SkillMotion.WHIRLING_SLASH
            has(text, "twister", "돌개바람") -> SkillMotion.TWISTER
            has(text, "tempest bell", "폭풍의 종") -> SkillMotion.TEMPEST_BELL
            has(text, "tornado shot", "회오리 사격") -> SkillMotion.TORNADO_SHOT
            has(text, "lightning arrow", "번개 화살") -> SkillMotion.LIGHTNING_ARROW
            has(text, "ice shot", "얼음 화살") -> SkillMotion.ICE_SHOT
            has(text, "spark", "전기불꽃", "전기 불꽃") -> SkillMotion.SPARK
            has(text, "comet", "혜성") -> SkillMotion.COMET
            has(text, "fireball", "화염구") -> SkillMotion.FIREBALL
            has(text, "explosive grenade", "폭발 유탄", "폭발 수류탄") -> SkillMotion.GRENADE
            has(text, "killing palm", "살상 장법") -> SkillMotion.KILLING_PALM
            has(text, "flicker strike", "점멸 타격") -> SkillMotion.FLICKER
            has(
                text, "curse", "저주", "징표", "mark", "despair", "flammability", "conductivity",
                "절망", "화염 취약", "전도",
            ) -> SkillMotion.CURSE
            else -> SkillMotion.DEFAULT
        }
    }

    /** 이 모션을 빌드 콤보에서 어떤 자리로 쓰는지 */
    fun roleOf(motion: SkillMotion): ComboRole = when (motion) {
        SkillMotion.CURSE -> ComboRole.CURSE
        SkillMotion.WHIRLING_SLASH, SkillMotion.KILLING_PALM -> ComboRole.BUILD
        SkillMotion.TWISTER, SkillMotion.TEMPEST_BELL -> ComboRole.BURST
        else -> ComboRole.MAIN
    }

    /** 와류를 남기는 스킬 (돌개바람이 먹는다) */
    fun makesWhirlwind(motion: SkillMotion) = motion == SkillMotion.WHIRLING_SLASH

    /** 이 모션의 스킬은 한 번에 몇 대를 때리는지 (모션 길이에 맞춘 다단 히트) */
    fun hitCount(motion: SkillMotion): Int = when (motion) {
        SkillMotion.WHIRLING_ASSAULT -> 4
        SkillMotion.WHIRLING_SLASH -> 2
        SkillMotion.TWISTER -> 3
        else -> 1
    }
}

/**
 * 빌드에 같이 들어 있는 스킬을 읽어 콤보 순서를 짠다.
 *
 * 인게임에서 준비 단계가 필요한 조합만 묶는다.
 *  - 소용돌이 베기 → 돌개바람 : 와류를 2번 쌓고 돌개바람으로 터뜨린다
 *  - 폭풍의 종 : 주력기로 연계 4를 채운 뒤 종을 떨어뜨리고 다시 때린다
 *  - 저주 → 주력기
 *  - 살상 장법 → 권능 충전 소모기(점멸 타격 등)
 */
object ComboPlanner {

    /** 연계 4 — 폭풍의 종을 쓸 수 있는 최소 연계 */
    const val BELL_COMBO = 4

    /** 돌개바람이 먹을 와류 단계 목표 */
    const val WHIRLWIND_STAGES = 2

    fun plan(mainMotion: SkillMotion, kit: List<KitSkill>): List<ComboStep> {
        val steps = ArrayList<ComboStep>()
        val mainLabel = if (mainMotion == SkillMotion.DEFAULT) "주력 타격" else mainMotion.label

        val slash = kit.firstOrNull { it.motion == SkillMotion.WHIRLING_SLASH }
        val twister = kit.firstOrNull { it.motion == SkillMotion.TWISTER }
        // 주력기가 소용돌이 베기면 그 스킬로 와류를 쌓고, 아니면 빌드에 든 소용돌이 베기로 쌓는다.
        val builderMotion = when {
            slash != null -> SkillMotion.WHIRLING_SLASH
            mainMotion == SkillMotion.WHIRLING_SLASH -> SkillMotion.WHIRLING_SLASH
            else -> null
        }
        if (builderMotion != null && twister != null) {
            steps.add(
                ComboStep(
                    label = slash?.name ?: mainLabel,
                    motion = SkillMotion.WHIRLING_SLASH,
                    role = if (slash != null) ComboRole.BUILD else ComboRole.MAIN,
                    repeats = WHIRLWIND_STAGES,
                    note = "와류 ${WHIRLWIND_STAGES}단계",
                )
            )
            steps.add(ComboStep(twister.name, SkillMotion.TWISTER, ComboRole.BURST, note = "와류 소모"))
        } else if (twister != null) {
            steps.add(ComboStep(twister.name, SkillMotion.TWISTER, ComboRole.BURST))
        } else if (slash != null) {
            steps.add(ComboStep(slash.name, SkillMotion.WHIRLING_SLASH, ComboRole.BUILD, note = "와류 준비"))
        }

        val bell = kit.firstOrNull { it.motion == SkillMotion.TEMPEST_BELL }
        if (bell != null && bell.motion != mainMotion) {
            steps.add(ComboStep(mainLabel, mainMotion, ComboRole.MAIN, repeats = BELL_COMBO, note = "연계 $BELL_COMBO"))
            steps.add(ComboStep(bell.name, SkillMotion.TEMPEST_BELL, ComboRole.BURST, note = "종 강타"))
            steps.add(ComboStep(mainLabel, mainMotion, ComboRole.MAIN, repeats = 2, note = "종 울리기"))
        }

        kit.firstOrNull { it.motion == SkillMotion.CURSE }?.let { curse ->
            steps.add(0, ComboStep(curse.name, SkillMotion.CURSE, ComboRole.CURSE, note = "저주 먼저"))
        }

        val palm = kit.firstOrNull { it.motion == SkillMotion.KILLING_PALM }
        val chargeSpender = mainMotion == SkillMotion.FLICKER ||
            kit.any { it.motion == SkillMotion.FLICKER }
        if (palm != null && chargeSpender) {
            steps.add(ComboStep(palm.name, SkillMotion.KILLING_PALM, ComboRole.BUILD, note = "권능 충전"))
        }

        if (steps.none { it.role == ComboRole.MAIN }) {
            steps.add(ComboStep(mainLabel, mainMotion, ComboRole.MAIN, repeats = 2))
        }
        // 준비 단계만 있고 주력기가 없으면 콤보가 끝나고 딜이 멈춘다. 주력기를 뒤에 붙인다.
        if (steps.none { it.motion == mainMotion }) {
            steps.add(ComboStep(mainLabel, mainMotion, ComboRole.MAIN, repeats = 2))
        }
        return steps
    }
    /** 화면에 한 줄로 보여 줄 콤보 설명 */
    fun summary(plan: List<ComboStep>): String =
        plan.joinToString(" → ") { step -> step.label }
}
