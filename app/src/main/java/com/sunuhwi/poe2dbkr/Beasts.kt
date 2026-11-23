package com.sunuhwi.poe2dbkr

import kotlin.math.sin

/**
 * 부적(Talisman)으로 변신하는 야수 형상.
 *
 * PoE2에서 부적은 근접 양손 무기이고, 부적을 들고 있는 동안에는 그 부적의 형상으로
 * 형태 변환한 상태가 된다. 형상은 곰·늑대인간·와이번 셋이고, 형상마다 쓸 수 있는
 * 형태 변환 스킬이 따로 있다. (poe2db.tw/kr/Talismans, Bear_Form, Werewolf_Form, Wyvern_Form)
 */
enum class BeastForm(val label: String, val enName: String) {
    NONE("인간", "Human"),
    BEAR("곰", "Bear"),
    WEREWOLF("늑대인간", "Werewolf"),
    WYVERN("와이번", "Wyvern"),
    ;

    val isBeast: Boolean get() = this != NONE

    /** 형상이 주는 방어·이동 보너스 (PoE2DB 형상 문서 기준) */
    val bonus: String
        get() = when (this) {
            NONE -> ""
            BEAR -> "레벨당 방어도 +10 · 방어도의 30%가 원소 피해에도 적용 · 격노 15마다 인내 충전"
            WEREWOLF -> "네 발로 달리는 동안 이동 속도 30% 증가 · 스킬 속도 10% 증가"
            WYVERN -> "기절 한계 50% 증가 · 원소 상태 이상 한계 50% 증가 · 에너지 보호막 재충전 50% 가속"
        }

    val element: DamageElement
        get() = when (this) {
            NONE -> DamageElement.PHYSICAL
            BEAR -> DamageElement.FIRE
            WEREWOLF -> DamageElement.COLD
            WYVERN -> DamageElement.LIGHTNING
        }
}

/** 부적과 형상 판정 결과 — 전투 화면·HUD·테스트가 같이 본다 */
data class BeastProfile(
    val form: BeastForm = BeastForm.NONE,
    /** 착용한 부적 이름 (없으면 빈 값) */
    val talisman: String = "",
    /** 이 형상으로 쓸 수 있는 빌드 내 형태 변환 스킬 */
    val skills: List<String> = emptyList(),
    /** 왜 이 형상으로 판정했는지 (화면에 한 줄로 적는다) */
    val reason: String = "",
) {
    val active: Boolean get() = form.isBeast
    val summary: String
        get() = if (!active) "" else "${form.label} 형상" +
            (if (talisman.isNotBlank()) " · $talisman" else "") +
            (if (skills.isNotEmpty()) " · ${skills.take(2).joinToString(", ")}" else "")
}

object Beasts {

    /** 형태 변환 스킬 → 형상. poe2db 스킬 젬의 태그(곰/늑대인간/와이번)에서 뽑았다. */
    private val SKILL_FORMS: List<Pair<BeastForm, List<String>>> = listOf(
        BeastForm.BEAR to listOf(
            "maul", "사나운 발톱", "furious slam", "분개한 강타", "molten crash", "용암 충격",
            "fury of the mountain", "산의 광분", "ferocious roar", "사나운 포효",
            "rampage", "광란", "walking calamity", "걸어다니는 대재난",
        ),
        BeastForm.WEREWOLF to listOf(
            "shred", "발기기", "lunar assault", "달의 공격", "pounce", "급습",
            "cross slash", "십자 베기", "arctic howl", "한기의 울부짖음",
            "lunar blessing", "달의 축복",
        ),
        BeastForm.WYVERN to listOf(
            "oil barrage", "기름 포화", "rolling magma", "몰려오는 마그마",
            "wing blast", "날개 돌풍", "devour", "포식", "flame breath", "화염 숨결",
            "rend", "분리",
        ),
    )

    /** 형상별 기본 공격 이름 */
    fun basicAttack(form: BeastForm): String = when (form) {
        BeastForm.BEAR -> "곰 발톱"
        BeastForm.WEREWOLF -> "늑대인간 할퀴기"
        BeastForm.WYVERN -> "와이번 물기"
        BeastForm.NONE -> "기본 공격"
    }

    /** 젬 이름·그룹 이름에서 형태 변환 스킬의 형상을 찾는다 (없으면 null) */
    fun formOf(vararg names: String): BeastForm? {
        val text = names.filter { it.isNotBlank() }.joinToString(" ").lowercase()
        if (text.isBlank()) return null
        for ((form, keys) in SKILL_FORMS) {
            if (keys.any { text.contains(it) }) return form
        }
        return null
    }

    /** 이 아이템이 부적(양손 근접 무기)인지 */
    fun isTalisman(item: PobItem?): Boolean {
        if (item == null) return false
        val text = listOf(item.itemClass, item.baseType, item.name)
            .filter { it.isNotBlank() }.joinToString(" ").lowercase()
        if (text.isBlank()) return false
        // 방패(부적 문양 방패)·목걸이(리구리움 부적)는 형태 변환 부적이 아니다.
        if (text.contains("방패") || text.contains("shield") || text.contains("amulet") ||
            text.contains("목걸이") || text.contains("sigil crest")
        ) {
            return false
        }
        return text.contains("talisman") || item.itemClass.trim() == "부적"
    }

    /** 무기 슬롯에 낀 부적 (교체 슬롯 포함) */
    fun talismanItem(build: PobBuild?, overrides: Map<String, PobItem> = emptyMap()): PobItem? {
        if (build == null && overrides.isEmpty()) return null
        val slots = listOf(
            "Weapon 1", "Weapon 2", "Weapon 1 Swap", "Weapon 2 Swap",
        )
        for (slot in slots) {
            val item = overrides[slot] ?: build?.slots?.get(slot)?.let { build.items[it] }
            if (isTalisman(item)) return item
        }
        return (build?.items?.values.orEmpty().asSequence() + overrides.values.asSequence())
            .firstOrNull { isTalisman(it) }
    }

    /**
     * 빌드의 부적과 스킬로 변신 형상을 정한다.
     *
     * 1) 주력 스킬이 형태 변환 스킬이면 그 스킬의 형상 (기름 포화 → 와이번)
     * 2) 아니면 빌드에 든 형태 변환 스킬의 형상
     * 3) 부적만 있고 형태 변환 스킬이 없으면 곰 (형상은 언제든 바꿀 수 있으므로 기본값)
     */
    fun profile(
        build: PobBuild?,
        overrides: Map<String, PobItem> = emptyMap(),
        mainSkill: String = "",
        kitNames: List<String> = emptyList(),
        dict: KrDict = KrDict.EMPTY,
    ): BeastProfile {
        val talisman = talismanItem(build, overrides)
        val mainForm = formOf(mainSkill)
        val kitForms = kitNames.mapNotNull { name -> formOf(name)?.let { it to name } }
        val form = when {
            mainForm != null -> mainForm
            kitForms.isNotEmpty() -> kitForms.first().first
            talisman != null -> BeastForm.BEAR
            else -> BeastForm.NONE
        }
        if (!form.isBeast) return BeastProfile()
        val skills = LinkedHashMap<String, String>()
        if (mainForm != null && mainSkill.isNotBlank()) {
            skills[mainSkill.lowercase()] = dict.gemName(mainSkill).ifBlank { mainSkill }
        }
        kitForms.forEach { (_, name) ->
            if (name.isNotBlank()) skills[name.lowercase()] = dict.gemName(name).ifBlank { name }
        }
        val reason = when {
            mainForm != null -> "주력 스킬 ${skills[mainSkill.lowercase()] ?: mainSkill}이 ${form.label} 형태 변환"
            kitForms.isNotEmpty() -> "빌드의 ${form.label} 형태 변환 스킬"
            else -> "부적 장착 → ${form.label} 형상"
        }
        return BeastProfile(
            form = form,
            talisman = talisman?.displayName.orEmpty(),
            skills = skills.values.toList(),
            reason = reason,
        )
    }
}

/**
 * 야수 자세 — 머리·턱·갈기·날개·꼬리·앞뒷다리 관절을 따로 움직인다.
 * 값은 모두 0~1 또는 라디안이고, 그리기와 테스트가 함께 쓴다.
 */
data class BeastPose(
    /** 몸통이 뜬 높이 (점프·도약) */
    val lift: Float = 0f,
    /** 몸통이 앞으로 기운 각도 */
    val pitch: Float = 0f,
    /** 고개 각도 (음수 = 위를 봄) */
    val head: Float = 0f,
    /** 턱 벌어짐 (0 닫힘 ~ 1 최대) */
    val jaw: Float = 0f,
    /** 갈기·목덜미가 곤두선 정도 */
    val mane: Float = 0f,
    /** 꼬리 흔들림 (-1 ~ 1) */
    val tail: Float = 0f,
    /** 날개 펼침 (와이번) */
    val wing: Float = 0f,
    /** 날개 한 번 치기 (0~1) */
    val flap: Float = 0f,
    /** 앞발·앞다리 뻗음 */
    val frontReach: Float = 0f,
    /** 뒷다리 굽힘 */
    val crouch: Float = 0f,
    /** 걸음 위상 */
    val stride: Float = 0f,
    /** 공격 애니메이션 반영도 */
    val attack: Float = 0f,
)

object BeastMotions {

    /** 형상별 기본 자세 — 공격·이동에 따라 관절을 움직인다 */
    fun pose(
        form: BeastForm,
        attack: Float,
        walk: Float,
        moving: Boolean,
        time: Float,
        channel: Float = 0f,
    ): BeastPose {
        val a = attack.coerceIn(0f, 1f)
        val phase = if (channel > 0f) (channel * 1.4f) % 1f else 1f - a
        val stride = if (moving) sin(walk) else sin(walk * 0.6f) * 0.25f
        val breath = sin(time * 2.1f)
        val base = when (form) {
            BeastForm.BEAR -> BeastPose(
                pitch = 0.06f + breath * 0.01f,
                head = -0.06f,
                jaw = 0.06f,
                mane = 0.25f,
                tail = stride * 0.35f,
                stride = stride,
            )
            BeastForm.WEREWOLF -> BeastPose(
                lift = 1.5f,
                pitch = -0.16f + breath * 0.02f,
                head = 0.04f,
                jaw = 0.10f,
                mane = 0.45f,
                tail = stride * 0.7f,
                stride = stride,
            )
            BeastForm.WYVERN -> BeastPose(
                lift = 4f + breath * 1.2f,
                pitch = -0.08f,
                head = -0.02f,
                jaw = 0.05f,
                mane = 0.15f,
                tail = stride * 0.55f,
                wing = 0.55f + breath * 0.08f,
                flap = (sin(time * 2.6f) + 1f) * 0.5f,
                stride = stride,
            )
            BeastForm.NONE -> BeastPose()
        }
        if (a <= 0.01f && channel <= 0f) return base
        val w = if (channel > 0f) 1f else a
        val mix: (Float, Float) -> Float = { x, y -> x + (y - x) * w }
        return when (form) {
            BeastForm.BEAR -> beastAttack(base, phase, w, mix)
            BeastForm.WEREWOLF -> wolfAttack(base, phase, w, mix)
            BeastForm.WYVERN -> wyvernAttack(base, phase, w, mix, channel)
            BeastForm.NONE -> base
        }
    }

    /** 곰: 앞발을 크게 들어 내리치고, 내려찍을 때 턱을 벌려 포효한다 */
    private fun beastAttack(
        base: BeastPose,
        phase: Float,
        w: Float,
        mix: (Float, Float) -> Float,
    ): BeastPose {
        val rear = when {
            phase < 0.28f -> 0.55f
            phase < 0.5f -> 0.9f
            phase < 0.62f -> 0.15f
            else -> 0.35f
        }
        val reach = when {
            phase < 0.28f -> -0.25f
            phase < 0.5f -> 0.55f
            phase < 0.62f -> 1f
            else -> 0.45f
        }
        return base.copy(
            lift = mix(base.lift, 14f * rear),
            pitch = mix(base.pitch, -0.45f * rear + 0.25f * reach),
            head = mix(base.head, -0.55f * rear + 0.18f * reach),
            jaw = mix(base.jaw, 0.25f + 0.75f * reach),
            mane = mix(base.mane, 0.9f * rear),
            frontReach = mix(base.frontReach, reach),
            crouch = mix(base.crouch, if (phase in 0.5f..0.7f) 0.9f else 0.2f),
            attack = w,
        )
    }

    /** 늑대인간: 낮게 웅크렸다가 앞으로 파고들며 두 팔을 교차로 할퀸다 */
    private fun wolfAttack(
        base: BeastPose,
        phase: Float,
        w: Float,
        mix: (Float, Float) -> Float,
    ): BeastPose {
        val lunge = when {
            phase < 0.22f -> -0.35f
            phase < 0.48f -> 0.75f
            phase < 0.7f -> 0.55f
            else -> 0.2f
        }
        val slash = when {
            phase < 0.25f -> 0.15f
            phase < 0.5f -> 1f
            else -> 0.35f
        }
        return base.copy(
            lift = mix(base.lift, 3f + 10f * lunge.coerceAtLeast(0f)),
            pitch = mix(base.pitch, 0.35f * lunge),
            head = mix(base.head, -0.18f + 0.3f * slash),
            jaw = mix(base.jaw, 0.35f + 0.6f * slash),
            mane = mix(base.mane, 0.85f * slash),
            frontReach = mix(base.frontReach, slash),
            crouch = mix(base.crouch, if (phase < 0.25f) 0.85f else 0.25f),
            tail = mix(base.tail, -0.8f + 1.6f * slash),
            attack = w,
        )
    }

    /** 와이번: 날개를 크게 펼쳐 뜬 채 목을 앞으로 내밀어 기름·화염을 토한다 */
    private fun wyvernAttack(
        base: BeastPose,
        phase: Float,
        w: Float,
        mix: (Float, Float) -> Float,
        channel: Float,
    ): BeastPose {
        val spread = if (channel > 0f) 0.95f else when {
            phase < 0.3f -> 0.9f
            phase < 0.6f -> 0.7f
            else -> 0.55f
        }
        val neck = if (channel > 0f) 0.85f else when {
            phase < 0.3f -> 0.35f
            phase < 0.65f -> 1f
            else -> 0.4f
        }
        val flap = if (channel > 0f) (sin(channel * 9f) + 1f) * 0.5f else {
            (sin(phase * 12f) * 0.5f + 0.5f)
        }
        return base.copy(
            lift = mix(base.lift, 8f + flap * 6f),
            pitch = mix(base.pitch, 0.12f * neck),
            head = mix(base.head, -0.25f - 0.25f * neck),
            jaw = mix(base.jaw, 0.2f + 0.8f * neck),
            wing = mix(base.wing, spread),
            flap = mix(base.flap, flap),
            frontReach = mix(base.frontReach, neck),
            crouch = mix(base.crouch, 0.15f),
            tail = mix(base.tail, sin(phase * 6.28f) * 0.5f),
            attack = w,
        )
    }
}
