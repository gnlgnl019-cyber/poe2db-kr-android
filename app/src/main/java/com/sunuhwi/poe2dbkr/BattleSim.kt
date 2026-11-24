package com.sunuhwi.poe2dbkr

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/** 전투에서 쓰는 피해 속성 */
enum class DamageElement(val label: String, val color: Int) {
    PHYSICAL("물리", 0xFFE9E3D6.toInt()),
    FIRE("화염", 0xFFFF7B3C.toInt()),
    COLD("냉기", 0xFF74DBFF.toInt()),
    LIGHTNING("번개", 0xFFFFE272.toInt()),
    CHAOS("카오스", 0xFFC182FF.toInt()),
}

/** 메인 스킬에 맞춰 고르는 이펙트 계열 */
enum class SkillEffectType(val label: String, val element: DamageElement) {
    ICE("얼음 화살", DamageElement.COLD),
    LIGHTNING("연쇄 번개", DamageElement.LIGHTNING),
    FIRE("화염구", DamageElement.FIRE),
    MELEE("근접 강타", DamageElement.PHYSICAL),
    CHAOS("카오스 구체", DamageElement.CHAOS),
    MINION("소환수", DamageElement.PHYSICAL),
}

/** 스킬 이름과 보조 젬으로 이펙트 계열을 고른다 */
object SkillEffects {

    private val MINION = listOf(
        "summon", "minion", "skeleton", "skeletal", "zombie", "spectre", "spirit",
        "golem", "reaver", "archer", "sniper", "storm mage", "소환", "해골", "망령",
    )
    private val CHAOS = listOf(
        "chaos", "essence drain", "contagion", "wither", "despair", "corrupt", "void",
        "poison", "toxic", "plague", "dark", "카오스", "중독", "부패",
    )
    private val ICE = listOf(
        "ice", "frost", "cold", "freeze", "frozen", "glacial", "hail", "snow",
        "winter", "rime", "blizzard", "comet", "냉기", "얼음", "서리", "빙결", "한파",
    )
    private val LIGHTNING = listOf(
        "lightning", "spark", "arc", "thunder", "storm", "tempest", "shock", "volt",
        "ball lightning", "galvanic", "conduct", "번개", "전격", "뇌전", "폭풍",
    )
    private val FIRE = listOf(
        "fire", "flame", "ignite", "burn", "meteor", "infernal", "magma", "scorch",
        "ember", "blaze", "incinerate", "molten", "화염", "불꽃", "화상", "점화", "용암",
    )
    private val MELEE = listOf(
        "slam", "strike", "hammer", "mace", "axe", "cyclone", "earthquake", "boneshatter",
        "sunder", "cleave", "sweep", "smash", "lacerate", "shattering", "shield",
        "quarterstaff", "강타", "망치", "회오리", "참격", "베기",
    )

    fun detect(vararg names: String): SkillEffectType {
        val text = names.joinToString(" ").lowercase()
        if (text.isBlank()) return SkillEffectType.LIGHTNING
        fun has(list: List<String>) = list.any { text.contains(it) }
        return when {
            has(MINION) -> SkillEffectType.MINION
            has(CHAOS) -> SkillEffectType.CHAOS
            has(ICE) -> SkillEffectType.ICE
            has(LIGHTNING) -> SkillEffectType.LIGHTNING
            has(FIRE) -> SkillEffectType.FIRE
            has(MELEE) -> SkillEffectType.MELEE
            else -> SkillEffectType.LIGHTNING
        }
    }

    fun weaponFor(fx: SkillEffectType, weaponLines: List<String>): WeaponVisualType {
        val detected = WeaponTypes.detect(weaponLines.joinToString(" "))
        return if (detected != WeaponVisualType.NONE) detected else WeaponTypes.fallbackFor(fx)
    }
}

/**
 * 순간이동 근접기 판별.
 *
 * 점멸 타격처럼 적에게 걸어가지 않고 공격할 때마다 적 옆으로 순간이동해 베는 스킬은
 * 이동 연출 대신 텔레포트 잔상으로 그린다. 질풍·충전 지팡이처럼 빠른 연타 계열도 같은 결로 친다.
 */
object MeleeMotion {

    private val FLICKER_WORDS = listOf(
        "flicker", " flicker strike", "blink", "blink strike", "teleport strike",
        "charged staff", "질풍", "점멸", "섬광 타격", "순간 이동", "순간이동",
    )

    fun isFlicker(vararg names: String): Boolean {
        val text = names.joinToString(" ").lowercase()
        if (text.isBlank()) return false
        return FLICKER_WORDS.any { text.contains(it) }
    }
}

/** PoB에서 뽑아 전투에 넣는 플레이어 수치 */
data class BattlePlayerStats(
    val className: String,
    val skillName: String,
    val fx: SkillEffectType,
    val weapon: WeaponVisualType,
    /** 착용 무기로 정해진 공격 형태 (근접·활·쇠뇌·주문) */
    val form: AttackForm = WeaponTypes.attackForm(weapon, fx),
    val dps: Double,
    val aps: Double,
    val critChance: Double,
    val critMulti: Double,
    val life: Double,
    val es: Double,
    val physRed: Double,
    val evade: Double,
    val fireRes: Double,
    val coldRes: Double,
    val lightRes: Double,
    val chaosRes: Double,
    val ehp: Double,
    val dpsBase: Double = 0.0,
    val weaponLabel: String = "",
    val triggers: List<TriggerSpec> = emptyList(),
    /** 점멸 타격처럼 적 옆으로 순간이동해 베는 근접 스킬 */
    val flicker: Boolean = MeleeMotion.isFlicker(skillName),
    /** PoB에 DPS 값이 없어 평균 타격·공속으로 추정했는지 */
    val dpsEstimated: Boolean = false,
    /** PoB가 DPS를 무한(inf)으로 내보냈는지 */
    val dpsInfinite: Boolean = false,
    /** PoB 무한대 수치를 표시 상한(2.1B급)으로 잡았는지 */
    val dpsCapped: Boolean = false,
    /** 전직 이름 (예: 마셜 아티스트) */
    val ascendName: String = "",
    /** 화면에 보여 줄 전직 효과 요약 */
    val ascendSummary: String = "",
    /** 전직 주요 노드 이름 */
    val ascendNodes: List<String> = emptyList(),
    /** 전투 수치에 반영된 전직 노드 수 */
    val ascendApplied: Int = 0,
    /** 전직이 부여한, 전투 중 스스로 터지는 스킬 */
    val ascendSkills: List<AscendancyEffects.AscSkill> = emptyList(),
    /** 추가 투사체 수 */
    val extraProjectiles: Int = 0,
    /** 주력 스킬 젬 레벨 — 전기불꽃처럼 레벨마다 발사 수가 늘어나는 스킬에 쓴다 */
    val skillLevel: Int = 20,
    /** 효과 범위 증가(%) — 타격·폭발 반경에 적용 */
    val aoePct: Double = 0.0,
    /** 상태이상 확률 증가(%) — 기본 확률에 대한 상대값 */
    val shockChancePct: Double = 0.0,
    val freezeChancePct: Double = 0.0,
    val igniteChancePct: Double = 0.0,
    /** 감전·냉각·중독 강도 증가(%) */
    val ailmentMagnitudePct: Double = 0.0,
    /** 받는 피해 감폭(%) */
    val damageTakenLessPct: Double = 0.0,
    /** 물리 피해를 다른 속성으로 받는 비율(%) */
    val physTakenAsFirePct: Double = 0.0,
    val physTakenAsColdPct: Double = 0.0,
    val physTakenAsLightningPct: Double = 0.0,
    val physTakenAsChaosPct: Double = 0.0,
    /** 회피로 막는 원소 피해 감소(%) */
    val evasionAsElementalPct: Double = 0.0,
    /** 에너지 보호막 재충전 지연(초) */
    val esRechargeDelay: Double = PobCalculator.ES_RECHARGE_DELAY,
    /** 에너지 보호막 재충전 속도 증가(%) */
    val esRechargeRatePct: Double = 0.0,
    /** 1초마다 회복하는 생명력 비율(%) */
    val lifeRegenPct: Double = 0.0,
    /** 준 피해 중 흡수하는 비율(%) */
    val lifeLeechPct: Double = 0.0,
    /** 메타 스킬 에너지 획득 증가(%) */
    val metaEnergyPct: Double = 0.0,
    /** 재사용 대기시간 회복 속도 증가(%) — 메타젬·부여 스킬 간격을 줄인다 */
    val cooldownRatePct: Double = 0.0,
    /** 스킬 이름으로 고른 인게임 모션 (선회 공격·돌개바람 등) */
    val motion: SkillMotion = SkillMotions.detect(skillName),
    /** 같은 빌드에 들어 있어 봇이 콤보로 엮는 액티브 스킬들 */
    val kit: List<KitSkill> = emptyList(),

    /** 이 빌드가 부적으로 변신하는 야수 형상 (곰·늑대인간·와이번) */
    val beast: BeastForm = BeastForm.NONE,
    val beastProfile: BeastProfile = BeastProfile(),

    /** 전투 탭에서 짠 콤보 순서 — 있으면 빌드 자동 콤보 대신 이 순서를 그대로 돈다 */
    val combo: List<ComboStep> = emptyList(),

    /** 주력 스킬이 기름 지대를 남기는 스킬인지 */
    val oilSkill: Boolean = false,
    /** 공허의 형상 안에 넣은 근접 공격 — 상이 이 스킬을 한 번 휘두른다 */
    val hollowSkill: KitSkill? = null,

    /** 소환수·반려수 구성 (두꺼비·암울한 결과 포함) */
    val minions: MinionPlan = MinionPlan.EMPTY,

    /** PoB에서 읽은 전령 구성 (정신력 점유·보조 젬 포함) */
    val heralds: HeraldPlan = HeraldPlan.EMPTY,

    /** PoB에서 읽은 정신력 점유 버프 (오라·지속 버프·발동 젬 포함) */
    val spiritBuffs: SpiritBuffPlan = SpiritBuffPlan.EMPTY,

    /** 충전 운용 규칙 — 전투 격분·공명·충전 조절 등 (빌드 글에서 읽는다) */
    val charges: ChargeRules = ChargeRules(),

    /** 적용된 고유 아이템 효과 */
    val uniqueSummary: String = "",
    val uniqueEffects: List<String> = emptyList(),
    val uniqueApplied: Int = 0,

    /** 플레이어가 직접 때리는 빌드인지 (두꺼비처럼 소환수가 때리는 빌드는 false) */
    val playerCarriesDps: Boolean = true,

    /** 주력 스킬별 DPS와 그 합 */
    val skillDpsList: List<PobCalculator.SkillDps> = emptyList(),
    val totalDps: Double = 0.0,

    /** 장착한 고유 아이템의 특수 효과 (전투에서 직접 굴린다) */
    val unique: UniqueEffects.Sim = UniqueEffects.Sim(),

    /**
     * 플레이어가 직접 때릴 때 쓰는 DPS.
     * 두꺼비처럼 다른 개체가 피해를 내는 빌드에서는 `dps`(총 DPS)와 다르다.
     * 음수면 `dps`를 그대로 쓴다.
     */
    val playerDps: Double = -1.0,
) {
    /** 플레이어 평타 계산에 쓸 DPS */
    val hitDps: Double get() = if (playerDps >= 0.0) playerDps else dps

    val element: DamageElement get() = fx.element

    /** 공허의 형상 — 근접 공격을 할 때마다 상을 내보낸다 */
    val hollowForm: Boolean get() = ascendSkills.any { it.name.contains("형상") }

    /** 부적으로 변신한 상태인지 */
    val transformed: Boolean get() = beast.isBeast

    /** 공허의 집중 — 실체 없는 종을 주기적으로 띄운다 */
    val hollowFocus: Boolean get() = ascendSkills.any { it.name.contains("집중") }

    /** 전장 HUD에 같이 보여줄 트리거 설명 */
    val triggerText: String
        get() = triggers.joinToString("  ") { spec -> "${spec.kind.short} · ${spec.skillNames}" }

    /** 전장 HUD에 같이 보여줄 전직 효과 한 줄 */
    val ascendText: String
        get() = if (ascendSummary.isBlank()) "" else "✦ $ascendSummary"

    /** 전장 HUD에 같이 보여줄 소환수 한 줄 */
    val minionText: String
        get() = if (minions.isEmpty) "" else "◈ ${minions.summary}"

    /** 전장 HUD에 같이 보여줄 고유 효과 한 줄 */
    val uniqueText: String
        get() = if (uniqueSummary.isBlank()) "" else "⬩ $uniqueSummary"
}

enum class BattleMode(val label: String) { MAP("16T 맵핑"), BOSS("우버 보스") }

enum class EnemyKind(val label: String, val hp: Double, val hit: Double, val interval: Double, val speed: Float, val scale: Float) {
    NORMAL("일반", 120_000.0, 320.0, 1.55, 82f, 1.30f),
    MAGIC("마법", 330_000.0, 500.0, 1.60, 70f, 1.48f),
    RARE("희귀", 1_150_000.0, 1_050.0, 1.85, 57f, 1.86f),
    BOSS("보스", 0.0, 0.0, 1.70, 42f, 3.20f),
}

/** 엔드게임 보스 프리셋 (체력·피해는 커뮤니티 집계 기반 추정치) */
data class BossPreset(
    val id: String,
    val name: String,
    val enName: String,
    val element: DamageElement,
    val secondary: DamageElement,
    val physShare: Double,
    val hp: Double,
    val hit: Double,
    val interval: Double,
    val speed: Float,
    val pattern: String,
)

object BossPresets {
    val ALL: List<BossPreset> = listOf(
        BossPreset(
            id = "arbiter",
            name = "우버 재의 중재자",
            enName = "Arbiter of Ash",
            element = DamageElement.FIRE,
            secondary = DamageElement.PHYSICAL,
            physShare = 0.35,
            hp = 22_600_000.0,
            hit = 3_400.0,
            interval = 1.75,
            speed = 52f,
            pattern = "화염 참격 · 대지 강타",
        ),
        BossPreset(
            id = "xesht",
            name = "우버 크세쉬",
            enName = "Xesht, We That Are One",
            element = DamageElement.CHAOS,
            secondary = DamageElement.PHYSICAL,
            physShare = 0.55,
            hp = 32_000_000.0,
            hit = 3_400.0,
            interval = 1.55,
            speed = 76f,
            pattern = "연속 돌진 · 손아귀 강타",
        ),
        BossPreset(
            id = "king",
            name = "우버 안개 속의 왕",
            enName = "King in the Mists",
            element = DamageElement.COLD,
            secondary = DamageElement.CHAOS,
            physShare = 0.25,
            hp = 14_000_000.0,
            hit = 2_900.0,
            interval = 1.65,
            speed = 62f,
            pattern = "냉기 파동 · 소환 의식",
        ),
        BossPreset(
            id = "zarokh",
            name = "우버 자로크",
            enName = "Zarokh, the Temporal",
            element = DamageElement.LIGHTNING,
            secondary = DamageElement.FIRE,
            physShare = 0.20,
            hp = 18_000_000.0,
            hit = 3_100.0,
            interval = 1.45,
            speed = 70f,
            pattern = "시간 절단 · 번개 낙뢰",
        ),
        BossPreset(
            id = "olroth",
            name = "우버 올로스",
            enName = "Olroth, Origin of the Fall",
            element = DamageElement.COLD,
            secondary = DamageElement.PHYSICAL,
            physShare = 0.60,
            hp = 12_500_000.0,
            hit = 2_700.0,
            interval = 1.85,
            speed = 66f,
            pattern = "빙결 강타 · 균열 폭발",
        ),
    )
}

// ---------------------------------------------------------------- 시뮬레이션 상태

class PlayerState(stats: BattlePlayerStats) {
    var x = 185f
    var y = 400f
    // 생명력/보호막이 0으로 들어오면 첫 틱에 즉시 패배해 시뮬레이션이 멈춘 것처럼 보인다.
    // 재생 상한도 이 값으로 잡아야 0으로 되돌아가지 않는다.
    val baseLife: Double = stats.life.coerceAtLeast(1.0)
    val baseEs: Double = stats.es.coerceAtLeast(0.0)
    var life = baseLife
    var es = baseEs
    var facing = 0f
    var attackTimer = 0.3f
    var attackAnim = 0f
    var walkPhase = 0f
    var moving = false
    var esDelay = 0f
    var hurtFlash = 0f
    var dashTimer = 0f
    var dashVx = 0f
    var dashVy = 0f
    var attackCount = 0
    var dodgeCooldown = 0f
    /** 순간이동 직후의 잔상 시간 */
    var blinkGlow = 0f

    /** 기름 포화처럼 집중 유지로 계속 쏘는 스킬의 경과 시간 */
    var channel = 0f
    /** 변신 연출이 끝난 뒤의 야수 상태 (0→1) */
    var shiftAnim = 0f
    /** 방금 변신했다 — 화면에 오라와 충격파를 남긴다 */
    var shiftFlash = 0f
    /** 선회 공격의 남은 회전 시간과 회전 각도 */
    var whirlTimer = 0f
    var whirlAngle = 0f
    var whirlVx = 0f
    var whirlVy = 0f
}

class Enemy(
    val kind: EnemyKind,
    var x: Float,
    var y: Float,
    val maxHp: Double,
    val hit: Double,
    val interval: Double,
    val element: DamageElement,
    val ranged: Boolean,
    val speed: Float,
    val scale: Float,
    val slotAngle: Float,
    var boss: BossPreset? = null,
    /** 처음 공격까지의 시간 — 전투 난수로 정해 같은 씨앗이면 같은 흐름이 된다 */
    startDelay: Double = -1.0,
    walkStart: Float = -1f,
) {
    var hp = maxHp
    var attackTimer = if (startDelay >= 0.0) startDelay else interval * (0.35 + Random.nextDouble() * 0.5)
    var walkPhase = if (walkStart >= 0f) walkStart else Random.nextDouble().toFloat() * 6.28f
    var hitFlash = 0f
    var alive = true
    var dot = 0.0
    var dotTimer = 0.0
    var dotTick = 0.0
    var attackCount = 0
    var enraged = false
    var windup = 0f
    var vx = 0f
    var vy = 0f
    var deadFade = 0f
    var attackAnim = 0f
    // 상태이상 타이머(초). 0보다 크면 그 상태이상이 걸려 있다.
    var shockT = 0f
    var freezeT = 0f
    var igniteT = 0f
    var igniteDps = 0.0
    /** 올로스의 룬 보호막처럼 피해를 흡수하는 임시 보호막 */
    var shield = 0.0

    /** 마지막 한 방이 생명력을 넘긴 초과 피해 — 재의 전령이 그만큼 태운다 */
    var overkill = 0.0

    /** 동결 상태로 처치돼 산산조각 났는지 */
    var shattered = false
    /** 와류·돌개바람에 휘말린 시간 */
    var slowT = 0f
    /** 늦추는 정도 (0.7 = 이동 속도 30% 감소). 기름 유탄 40%, 기름 지대 20% */
    var slowMul = 0.7f
    val shocked: Boolean get() = shockT > 0f

    /** 지금 이 적이 노리는 대상 (소환수 어그로) */
    var targetMinion: Minion? = null
    var aggroTimer = 0f
    /** 명중 시 저주(고유 아이템)가 남은 시간 */
    var curseT = 0f

    /** 기름에 젖어 노출된 시간 — 총 원소 저항이 낮아진 것으로 본다 */
    var oilT = 0f
}

/**
 * 전장에 세워진 소환수·반려수 한 마리.
 *
 * 소환수는 적을 쫓아가 때리고, 적은 가까운 소환수를 먼저 물기도 한다. 죽으면
 * `소환수 사망 시 시전`의 에너지가 차고, 부활하는 해골은 잠시 뒤 다시 일어난다.
 */
class Minion(
    val type: MinionType,
    var x: Float,
    var y: Float,
    val maxHp: Double,
    /** 한 번 때릴 때의 피해 */
    val hitDamage: Double,
    val attackInterval: Double,
    /** 처음 때리기까지의 시간·걸음 위상 — 전투 난수로 정한다 */
    startDelay: Float = -1f,
    walkStart: Float = -1f,
) {
    var hp = maxHp
    var alive = true
    var attackTimer = if (startDelay >= 0f) startDelay else 0.3f + Random.nextDouble().toFloat() * 0.4f
    var attackAnim = 0f
    var walkPhase = if (walkStart >= 0f) walkStart else Random.nextDouble().toFloat() * 6.28f
    var facing = 0f
    var deadFade = 0f
    var hitFlash = 0f
    var respawn = 0f
    var leapT = 0f
    var leapFromX = x
    var leapFromY = y
    var leapX = x
    var leapY = y
    var target: Enemy? = null
    var aggroTimer = 0f
    var anim = 0f

    /** 두꺼비인지 (도약해 터진다) */
    val isToad: Boolean get() = type.role == MinionRole.TOAD

    val scale: Float get() = type.scale
    val label: String get() = type.label
}

/** 저주받은 지대 — 도이드리의 파멸이 마녀두꺼비를 만드는 자리 */
class CursedGround(var x: Float, var y: Float, val radius: Float, val element: DamageElement) {
    var spawnTimer = 0f
    var pulse = 0f
    var toads = 0
}

/**
 * 기름 지대.
 *
 * 기름 포화·기름 유탄이 떨어뜨린 자리에 생긴다. 밟은 적은 20% 느려지고 노출되어
 * 총 원소 저항이 20% 낮아진다. 점화 상태의 적·점화 지대·폭발이 닿으면 불이 붙어
 * (burning) 감속 대신 계속 타오르며, 노출은 그대로 남는다. (poe2db 기름 지대 키워드)
 */
class OilGround(var x: Float, var y: Float, var radius: Float, var life: Float) {
    var age = 0f
    var burning = false
    /** 기름 유탄이 만든 끈적한 기름 — 이동 속도를 더 세게 늦춘다 (poe2db 40%) */
    var sticky = false
    /** 불이 붙은 뒤 남은 연소 시간 */
    var burnLife = 0f
    var burnDps = 0.0
    var pulse = 0f
    /** 불붙은 기름이 피해를 나누어 주는 간격 */
    var tickTimer = 0f
    /** 한 번 불이 붙었는지 — 재점화로 피해가 무한히 늘지 않게 한다 */
    var ignited = false
    val fade: Float get() = (life / 1.2f).coerceIn(0f, 1f)
}

/** 땅에 박힌 주문 토템 — 제자리에서 주문을 쏜다 */
class Totem(var x: Float, var y: Float, var life: Float) {
    var age = 0f
    var cast = 0f
    var shots = 0
    var pulse = 0f
    val fade: Float get() = (life / 1.5f).coerceIn(0f, 1f)
}

enum class FxKind {
    SLASH, ICE_CONE, RING, BOLT, PILLAR, ORB, AURA, SPEAR, SHOCKWAVE, CYCLONE, MINION_HIT, CLOUD,
    GROUND_CRACK, METEOR, THRUST, IMPACT, AFTERIMAGE, STREAK,
    /** 기름이 튀는 자국 */
    OIL_SPLASH,
    /** 기름에 불이 붙은 화염 */
    OIL_FLAME,
    /** 냉기 폭발 — 얼음 결정이 부채꼴로 박힌다 */
    FROST_NOVA,
    /** 여러 갈래로 튀는 번개 그물 */
    ARC_WEB,
    /** 저주 인장 문양 */
    SIGIL,
    /** 땅에 박힌 주문 토템 */
    TOTEM,
    /** 변신 오라 */
    SHIFT,
    /** 얼음 결정 파편 */
    CRYSTAL,
    /** 바닥에 퍼지는 흙먼지 고리 */
    DUST,
    /** 돌개바람·날개가 일으키는 돌풍 */
    WING_GUST,
    /** 번개가 꽂히는 순간 터지는 흰-보라 스파크 섬광 */
    SPARK_FLASH,
    /** 여러 겹으로 타오르는 화염 폭발 */
    FLAME_BURST,
    /** 불길 위로 올라가는 검붉은 연기 */
    SMOKE,
    /** 동결된 적이 부서지는 산산조각 — 얼음 파편이 사방으로 튄다 */
    SHATTER,
    /** 전령이 터질 때 퍼지는 원소 물결 */
    HERALD_WAVE,
    /** 클리어 순간의 금빛 승리 물결 */
    VICTORY_WAVE,
}

class Fx(
    val kind: FxKind,
    var x: Float,
    var y: Float,
    var x2: Float = 0f,
    var y2: Float = 0f,
    var angle: Float = 0f,
    var radius: Float = 0f,
    var life: Float = 0.4f,
    var color: Int = 0xFFFFFFFF.toInt(),
    var seed: Int = 0,
    var width: Float = 1f,
) {
    var maxLife: Float = life
    var born: Double = 0.0
}

class Particle(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var life: Float,
    var maxLife: Float,
    var size: Float,
    var color: Int,
    var gravity: Float = 0f,
    var streak: Boolean = false,
)

class Popup(
    var x: Float,
    var y: Float,
    var text: String,
    var crit: Boolean,
    var life: Float,
    var color: Int,
    var size: Float = 1f,
)

enum class ProjKind {
    ICE_ARROW, LIGHT_ARROW, FIRE_ORB, FROST_ORB, CHAOS_ORB, MINION_ORB, BOLT, ARROW, SPARK, ENEMY_BOLT,
    /** 폭발 유탄 — 포물선으로 튕겼다가 퓨즈가 타면 터진다 */
    GRENADE,
    /** 얼음 화살 착탄 때 떨어지는 파편 */
    ICE_SHARD,
    /** 기름 포화가 토해 내는 기름 덩이 */
    OIL,
    /** 와이번이 뿜는 전기를 띤 기름 */
    CHARGED_OIL,
}

class Projectile(
    var x: Float,
    var y: Float,
    var dirX: Float,
    var dirY: Float,
    var speed: Float,
    val damage: Double,
    val crit: Boolean,
    val target: Enemy?,
    val hostile: Boolean,
    val element: DamageElement,
    val kind: ProjKind,
    var life: Float = 3f,
    var trail: MutableList<Float> = ArrayList(),
    /** 전기불꽃처럼 좌우로 흔들리며 가는 투사체의 흔들림 폭 */
    var wobble: Float = 0f,
    /** 유탄의 남은 퓨즈(초) */
    var fuse: Float = 0f,
    /** 유탄이 그리는 포물선 높이 */
    var lift: Float = 0f,
    /** 전기불꽃처럼 튕기는 투사체의 남은 튕김 횟수 */
    var bounces: Int = 0,
    /** 기름 투사체가 만드는 기름 지대 지름 */
    var oilRadius: Float = 0f,
    /** 지나온 적 — 전기불꽃 한 줄기는 같은 적을 두 번 꿰뚫지 않는다 */
    val pierced: MutableList<Enemy> = ArrayList(),
    /** 다음으로 방향을 꺾을 때까지 남은 시간 (전기불꽃) */
    var turnTimer: Float = 0f,
    /** 살아 있는 시간 (연출용, 전기불꽃) */
    var age: Float = 0f,
    /** 궤적을 그릴 때 쓰는 고정 난수 씨앗 */
    val seed: Int = 0,
    /** 이 투사체를 쏜 쪽 이름과 기술 (적 투사체의 사망 원인 기록용) */
    val source: String = "",
    val skill: String = "",
) {
    /** 남은 궤뚫기 횟수 — 0이면 다음 적을 궤뚫을 수 없다 (전기불꽃) */
    var pierce: Int = 0

    /** 이 투사체가 낸 피해를 어느 스킬에 적립할지 (플레이어가 쏜 것) */
    var creditSkill: String = ""

    /** 메타젬에 넘길 명중 무게 (전기불꽃 한 줄기는 발수만큼 되돌려 센다) */
    var energyWeight: Double = 1.0
}

/**
 * 공허의 형상이 깨우는 상.
 *
 * 플레이어가 근접 공격을 할 때마다 플레이어 근처(6미터)에 나타나,
 * 장착된 근접기를 부여받은 그대로 한 번 휘두르고 사라진다.
 * 권능 충전을 하나 쓰면 상이 두 개 더 생긴다.
 */
class Phantom(
    var x: Float,
    var y: Float,
    var facing: Float,
    val damage: Double,
    val life: Float,
    val motion: SkillMotion,
    val weapon: WeaponVisualType,
    val form: AttackForm,
) {
    var age = 0f
    val maxLife: Float = life
    /** 남은 타격 횟수 — 회전 모션은 지나가면서 여러 번 친다 */
    var ticks = SkillMotions.hitCount(motion).coerceAtLeast(1)
    var tickTimer = 0.11f
    /** 이번 프레임에 휘두른 각도 (그리기에 쓴다) */
    var spin = 0f
    /** 상이 앞으로 미끄러지는 속도 */
    val drift: Float = if (motion == SkillMotion.WHIRLING_ASSAULT) 132f else 18f
    val fade: Float get() = (1f - age / maxLife).coerceIn(0f, 1f)
}

/** 소용돌이 베기가 남기는 와류 — 겹치면 단계가 올라 커진다 */
class Whirlwind(
    var x: Float,
    var y: Float,
    var radius: Float,
    var stage: Int,
    var life: Float,
) {
    var age = 0f
    val maxLife: Float = life
    /** 플레이어가 한 번 빠져나갔는지 — 다시 가장자리를 지나면 붕괴한다 */
    var armed = false
    val fade: Float get() = (1f - age / maxLife).coerceIn(0f, 1f)
}

/** 돌개바람 — 천방지축으로 전진하며 반복해서 벤다 */
class Twister(
    var x: Float,
    var y: Float,
    var dirX: Float,
    var dirY: Float,
    val damage: Double,
    var life: Float,
    val amplified: Boolean,
    val element: DamageElement,
    /** 이 회오리만의 반경 (0이면 기본값) — 회오리 사격의 충격 반경 1미터처럼 스킬마다 다르다 */
    radiusOverride: Float = 0f,
) {
    var age = 0f
    var tick = 0.05f
    var angle = 0f
    val radius: Float = if (radiusOverride > 0f) radiusOverride else if (amplified) 62f else 52f
    val fade: Float get() = (1f - age / life).coerceIn(0f, 1f)
}

/** 서리 폭탄이 띄운 맥동하는 보주 — 맥동마다 노출을 걸고 4초 뒤 기폭한다 (poe2db) */
class FrostBomb(var x: Float, var y: Float, var fuse: Float) {
    var age = 0f
    var pulse = 0f
    var tickTimer = 0.9f
    val fade: Float get() = (1f - age / fuse.coerceAtLeast(0.01f)).coerceIn(0f, 1f)
}

/** 켈라리의 기만이 남긴 분신 — 다시 쓰면 터져 더 큰 피해를 준다 */
class SpiritDecoy(var x: Float, var y: Float, var life: Float) {
    var age = 0f
    val fade: Float get() = (1f - age / life.coerceAtLeast(0.01f)).coerceIn(0f, 1f)
}

/** 폭풍의 종·공허의 집중이 띄우는 종. 때리면 충격파가 터진다. */class Bell(
    var x: Float,
    var y: Float,
    var life: Float,
    /** 공허의 집중이 띄운 종은 언제나 치명타로 터진다 */
    val ghost: Boolean,
) {
    var age = 0f
    var hits = 0
    var swing = 0f
    val fade: Float get() = (1f - age / life).coerceIn(0f, 1f)
}

/** 결과 요약 */
data class BattleResult(
    val victory: Boolean,
    val seconds: Double,
    val damageDealt: Double,
    val effectiveDps: Double,
    val crits: Int,
    val hitsTaken: Int,
    val evades: Int,
    val kills: Int,
    val totalEnemies: Int,
    val survival: Double,
    val lifeLeft: Double,
    val esLeft: Double,
    val maxPool: Double,
    val triggerCasts: Int = 0,
    val triggerDamage: Double = 0.0,
    val triggerText: String = "",
    val triggers: List<TriggerReport> = emptyList(),
    /** 한 번 트리거에 터진 최대 연발 수 (예: 혜성 18발) */
    val maxBurst: Int = 0,
    /** 연발로 나간 총 발 수 */
    val burstCasts: Int = 0,
    /** 전직 부여 스킬 발동 횟수와 그 피해 */
    val ascendCasts: Int = 0,
    val ascendDamage: Double = 0.0,
    /** 봇이 돌린 콤보 이름과 부가 동작 횟수 */
    val comboName: String = "",
    val phantomCasts: Int = 0,
    val whirlwindCasts: Int = 0,
    val twisterCasts: Int = 0,
    val bellCasts: Int = 0,
    /** 소환수·반려수·두꺼비가 낸 피해와 처치 */
    val minionDamage: Double = 0.0,
    val minionKills: Int = 0,
    val minionDeaths: Int = 0,
    val toadExplosions: Int = 0,
    val darkConsequencesDamage: Double = 0.0,
    val minionText: String = "",
    /** 부적 변신 상태와 기름 지대 운용 결과 */
    val beast: BeastForm = BeastForm.NONE,
    val beastText: String = "",
    val oilSpills: Int = 0,
    val oilIgnitions: Int = 0,
    val oilDamage: Double = 0.0,
    val oilShots: Int = 0,
    /** 사망했다면 그 원인 (승리면 null) */
    val deathCause: DeathCause? = null,
    /** 스킬별 누적 피해·DPS·비율 (피해가 큰 순서) */
    val skillDamage: List<SkillDamageReport> = emptyList(),
    /** 충전 운용 빌드면 마지막 충전 상태 */
    val chargeText: String = "",
    val frenzyGained: Int = 0,
    val chargesSpent: Int = 0,
    /** 전령 발동 횟수·피해·전령별 기록 */
    val heraldProcs: Int = 0,
    val heraldDamage: Double = 0.0,
    val heraldReports: List<HeraldReport> = emptyList(),
    val heraldText: String = "",
    /** 정신력 점유 버프가 낸 피해·버프별 기록 */
    val spiritDamage: Double = 0.0,
    val spiritReports: List<SpiritReport> = emptyList(),
    /** 동결 상태로 부서진(산산조각) 적 수 */
    val shatters: Int = 0,
    /** 클리어 요약 (승리했을 때만) */
    val clear: ClearSummary? = null,
)

/** 메타젬 하나의 전투 결과 */
data class TriggerReport(
    val label: String,
    val short: String,
    val skillNames: String,
    val casts: Int,
    val damage: Double,
    val estimated: Boolean,
) {
    val line: String
        get() = "$label · $skillNames — ${casts}회" +
            if (damage > 0) " · ${BattleWorld.formatShort(damage)}" else ""
}

/** 트리거(메타) 스킬 하나의 전투 중 상태 */
/** 보스 스킬 예고 장판 모양 */
enum class TelegraphShape { CIRCLE, LINE, DONUT, SAFE_CIRCLE, CROSS }

/**
 * 보스 스킬의 예고 장판.
 * windup 동안 바닥에 경고 영역이 차오르고, 끝나는 순간 그 안에 있으면 피해를 받는다.
 */
class Telegraph(
    val shape: TelegraphShape,
    var x: Float,
    var y: Float,
    var angle: Float,
    var radius: Float,
    var length: Float,
    val windup: Float,
    val damage: Double,
    val element: DamageElement,
    val name: String,
    var delay: Float = 0f,
    /** 이 장판을 깐 공격자 이름 (비어 있으면 보스 이름으로 채운다) */
    val source: String = "",
) {
    var elapsed = 0f
        private set
    var resolved = false
        private set

    val progress: Float get() = if (windup <= 0f) 1f else ((elapsed - delay) / windup).coerceIn(0f, 1f)
    val active: Boolean get() = elapsed >= delay
    val aboutToLand: Boolean get() = active && progress >= 0.55f

    fun advance(dt: Float): Boolean {
        elapsed += dt
        if (!active) return false
        if (elapsed - delay >= windup) resolved = true
        return resolved
    }

    /** 주어진 좌표가 이 장판의 피해 범위 안인지 */
    fun contains(px: Float, py: Float): Boolean {
        val dx = px - x
        val dy = py - y
        val dist = hypot(dx, dy)
        return when (shape) {
            TelegraphShape.CIRCLE -> dist <= radius
            TelegraphShape.SAFE_CIRCLE -> dist > radius
            TelegraphShape.DONUT -> dist in radius..length
            TelegraphShape.CROSS -> {
                val along = dx * cos(angle) + dy * sin(angle)
                val across = -dx * sin(angle) + dy * cos(angle)
                (abs(across) <= radius && abs(along) <= length) ||
                    (abs(along) <= radius && abs(across) <= length)
            }
            TelegraphShape.LINE -> {
                val along = dx * cos(angle) + dy * sin(angle)
                val across = -dx * sin(angle) + dy * cos(angle)
                along >= -10f && along <= length && abs(across) <= radius
            }
        }
    }

    /** 플레이어가 벗어나야 할 방향 (장판 밖으로) */
    fun escapeAngle(px: Float, py: Float): Float = when (shape) {
        TelegraphShape.SAFE_CIRCLE -> atan2(y - py, x - px)
        else -> atan2(py - y, px - x)
    }
}

/** 보스 패턴 하나 (이름 + 다음 사용까지의 간격 + 동작) */
private class BossPattern(val name: String, val cooldown: Float, val action: () -> Unit)

class TriggerRuntime(
    val spec: TriggerSpec,
    private val critRate: Double,
    /** 전직의 재사용 대기시간 회복 속도 증가(%). 메타젬 재사용 대기시간을 줄인다. */
    cooldownRatePct: Double = 0.0,
) {

    companion object {
        /** 발동 1회에 드는 에너지. 화면 표시는 이 값을 100%로 본다. */
        const val CAST_COST = 100.0
        /** 실제 PoE2처럼 100%를 훨씬 넘게 쌓일 수 있다 (4,000% = 40회분) */
        const val MAX_ENERGY = 4_000.0
        /** 한 번에 터지는 최대 연발 수 (화면·성능 보호) */
        const val MAX_BURST = 25
    }

    var energy = 0.0
        private set
    var cooldown = 0f
        private set
    var casts = 0
        private set
    var damage = 0.0
        private set
    private var nextSkill = 0
    private var auraClock = 0f

    /** 방금 터진 연발 수, 이번 전투 최대 연발 */
    var lastBurst = 0
        private set
    var bestBurst = 0
        private set
    /** 아직 화면에 내리지 못한 발 수 */
    var queued = 0
        private set

    /** 조건을 몇 번 채워야 한 번 터지는지 (한 번의 발동 비용은 항상 100) */
    val threshold: Double = CAST_COST

    val cooldownMax: Float =
        (spec.kind.cooldown / (1.0 + cooldownRatePct.coerceIn(0.0, 300.0) / 100.0)).toFloat()

    /** 트리거가 자연히 터지는 간격(초) — 한 번 발동의 피해를 정할 때 쓴다. */
    val interval: Double = maxOf(cooldownMax.toDouble(), 0.45).coerceAtMost(6.0)

    /** 현재 메타 에너지 % (100% = 1회분) */
    val energyPercent: Double get() = energy / CAST_COST * 100.0

    fun tick(dt: Float) {
        if (cooldown > 0f) cooldown = maxOf(0f, cooldown - dt)
    }

    /** 지속 오라(신성모독)는 시간이 흐르며 스스로 에너지를 채운다. */
    fun tickAura(dt: Float): Boolean {
        if (spec.kind.condition != TriggerCondition.AURA) return false
        auraClock += dt
        val pulse = 0.9f
        var gained = false
        while (auraClock >= pulse) {
            auraClock -= pulse
            addEnergy(CAST_COST / spec.kind.energyNeeded.coerceAtLeast(1.0))
            gained = true
        }
        return gained
    }

    /** 에너지는 100%를 넘어 최대 4,000%까지 쌓인다. */
    fun addEnergy(amount: Double) {
        if (amount <= 0.0 || !amount.isFinite()) return
        energy = min(MAX_ENERGY, energy + amount)
    }

    fun ready(): Boolean = energy >= CAST_COST && cooldown <= 0f

    /**
     * 쌓인 에너지를 100씩 깎아 연발 횟수를 만든다.
     * 1,200%가 쌓여 있으면 12발이 한꺼번에 예약된다.
     */
    fun drainBurst(): Int {
        val count = (energy / CAST_COST).toInt().coerceIn(0, MAX_BURST)
        if (count <= 0) return 0
        energy = max(0.0, energy - count * CAST_COST)
        cooldown = cooldownMax
        queued += count
        lastBurst = count
        bestBurst = max(bestBurst, count)
        return count
    }

    /** 다음 발동 스킬 (여러 개면 번갈아) */
    fun nextTriggered(): TriggeredSkill? {
        if (spec.skills.isEmpty()) return null
        val skill = spec.skills[nextSkill % spec.skills.size]
        nextSkill++
        casts++
        queued = maxOf(0, queued - 1)
        return skill
    }

    /** 연발 i번째에 나갈 스킬 (스킬이 여럿이면 번갈아) */
    fun skillFor(index: Int): TriggeredSkill? {
        if (spec.skills.isEmpty()) return null
        return spec.skills[index % spec.skills.size]
    }

    /** 실제로 한 발 나갔을 때 호출 */
    fun noteCast() {
        casts++
        queued = maxOf(0, queued - 1)
    }

    fun addDamage(value: Double) {
        if (value.isFinite() && value > 0) damage += value
    }

    /** 한 번 발동할 때의 피해: 그 스킬의 지속 DPS × 발동 간격 */
    fun castDamage(skill: TriggeredSkill): Double = (skill.dps * interval).coerceAtLeast(1.0)

    /** 이 트리거 한 발이 기준으로 삼는 피해량 (에너지 환산용) */
    fun referenceDamage(): Double = spec.skills.firstOrNull()?.let { castDamage(it) } ?: 1.0

    fun report(): TriggerReport = TriggerReport(
        label = spec.kind.label,
        short = spec.kind.short,
        skillNames = spec.skillNames,
        casts = casts,
        damage = damage,
        estimated = spec.estimated,
    )
}

class BattleWorld(
    val stats: BattlePlayerStats,
    val mode: BattleMode,
    val mobCount: Int,
    val amp: Boolean,
    val boss: BossPreset?,
    /** 난수 시드 — 같은 조건의 두 전투를 비교할 때 쓴다 (기본은 실행마다 다름) */
    seed: Long = System.nanoTime(),
) {
    companion object {
        const val W = 640f
        const val H = 780f
        private const val PI_F = 3.1415927f
        private const val MAX_FX = 220
        private const val MAX_PARTICLES = 420
        private const val MAX_POPUPS = 46
        /** 대규모 무리에서 화면에 동시에 세우는 적 수 상한 */
        const val ACTIVE_CAP = 120
        /** 한 프레임에 처리할 수 있는 최대 공격 횟수 (초고속 공속 대응) */
        private const val MAX_STRIKES_PER_STEP = 6
        /** 메타 에너지 100%(발동 1회)에 해당하는 피해량 */
        const val REFERENCE_HIT = 60_000.0

        /** 서리 폭탄 기폭 시간 — poe2db: 4초 */
        const val FROST_BOMB_FUSE = 4.0f

        fun formatShort(value: Double): String = when {
            // 무한·NaN은 화면에 숫자 대신 대시로 내보낸다.
            !value.isFinite() -> "—"
            value >= 1_000_000_000 -> String.format(java.util.Locale.KOREA, "%.1fB", value / 1_000_000_000)
            value >= 1_000_000 -> String.format(java.util.Locale.KOREA, "%.2fM", value / 1_000_000)
            value >= 10_000 -> String.format(java.util.Locale.KOREA, "%.0fK", value / 1_000)
            value >= 1_000 -> String.format(java.util.Locale.KOREA, "%.1fK", value / 1_000)
            else -> String.format(java.util.Locale.KOREA, "%.0f", value)
        }
    }

    enum class State { RUNNING, VICTORY, DEFEAT }

    var state = State.RUNNING
        private set

    val enemies = ArrayList<Enemy>()
    val projectiles = ArrayList<Projectile>()
    val effects = ArrayList<Fx>()
    val particles = ArrayList<Particle>()
    val popups = ArrayList<Popup>()
    val player = PlayerState(stats)

    // ---------------------------------------------------------- 소환수·반려수
    /** 지금 전장에 있는 소환수 (죽은 것 포함, 흐려지면 지운다) */
    val minions = ArrayList<Minion>()
    /** 저주받은 지대 — 도이드리의 파멸 */
    val cursedGrounds = ArrayList<CursedGround>()
    /** 두꺼비가 터질 때의 폭발 한 번 피해 */
    private val darkHitPerExplosion: Double = run {
        val plan = stats.minions
        if (!plan.hasDarkConsequences) 0.0
        else plan.darkConsequencesDps / (Minions.TOAD_PER_AREA * Minions.TOAD_AREAS / Minions.TOAD_CYCLE)
    }
    var minionDamage = 0.0
        private set
    var minionKills = 0
        private set
    var minionDeaths = 0
        private set
    var toadExplosions = 0
        private set
    var darkConsequencesDamage = 0.0
        private set
    /** 희귀 몬스터를 잡아 얻은 헤드헌터식 강화 중첩 (최대 5) */
    var huntStacks = 0
        private set

    /** 반려수가 한 번이라도 전장에 나왔는지 (화면·테스트 확인용) */
    val companionCount: Int get() = minions.count { it.alive && it.type.companion }

    // ---------------------------------------------------------- 대규모 무리 스폰 풀
    /** 500마리처럼 큰 무리는 화면에 한꺼번에 세우지 않고 남은 무리를 순서대로 채운다 */
    private val spawnQueue = ArrayDeque<EnemyKind>()
    private var plannedEnemies = 0
    /** 화면에 동시에 세우는 적 수 상한 (기기 성능 보호) */
    private val activeCap: Int = if (mode == BattleMode.BOSS) 1 else ACTIVE_CAP
    /** 지금까지 전장에 세운 적 수 */
    var spawnedEnemies = 0
        private set

    var elapsed = 0.0
        private set
    var damageDealt = 0.0
        private set
    var damageTaken = 0.0
        private set
    var critCount = 0
        private set
    var hitsTaken = 0
        private set
    var evades = 0
        private set
    var kills = 0
        private set
    var shake = 0f
        private set
    var hurtFlash = 0f
        private set
    var banner = 0f
        private set
    var bannerText = ""

    /** 쓰러진 뒤 흐른 시간 — 화면이 이 값으로 사망 연출과 페이드인을 맞춘다 */
    var deathAge = 0f
        private set
    /** 사망 원인 (쓰러지기 전에는 null) */
    var deathCause: DeathCause? = null
        private set

    /** 클리어한 뒤 흐른 시간 — 승리 연출과 페이드인 카드가 이 값을 따라간다 */
    var victoryAge = 0f
        private set

    // ---------------------------------------------------------- 전령
    /** 이 전투에 켜진 전령 (정신력이 남은 것만) */
    val heralds: List<HeraldSpec> = stats.heralds.active
    private val heraldProcs = LinkedHashMap<HeraldKind, Int>()
    private val heraldDamageByName = LinkedHashMap<HeraldKind, Double>()
    /** 전령 발동 총 횟수와 전령이 낸 피해 */
    var heraldProcsTotal = 0
        private set
    var heraldDamage = 0.0
        private set
    /** 동결 상태로 부서진 적 수 (산산조각) */
    var shatters = 0
        private set
    /** 천둥의 전령이 감전에서 낙뢰를 다시 내리는 간격 제한 */
    private var thunderOnShockCooldown = 0f

    // ---------------------------------------------------------- 정신력 점유 버프
    /** 이 전투에 켜진 정신력 점유 버프 (오라·지속 버프·발동 젬) */
    val spiritBuffs: List<SpiritSpec> = stats.spiritBuffs.active
    private var spiritAuraTimer = 0f
    /** 바람의 무희 단계 (최대 3) */
    private var windStages = 0f
    private var windStageTimer = 0f
    /** 혼의 춤 연막 (최대 3) */
    private var shrouds = 0
    private var shroudTimer = 0f
    /** 철의 수호가 모아 둔 물리 피해 */
    private var ironWardStored = 0.0
    private var ironWardTimer = 0f
    /** 독성 운반자가 모아 둔 중독 피해 */
    private var plagueStored = 0.0
    private var plagueTimer = 0f
    /** 격노 중첩 (야만적인 광분·광폭·영원한 격노) */
    private var rageStacks = 0f
    private var caltropTimer = 0f
    private var briarTimer = 0f
    /** 원소 융합의 원소가 바뀌는 시계 (8초 주기) */
    private var confluxPhase = 0f
    /** 희귀·고유 적과 붙어 있던 시간 (소모) */
    private var attritionTime = 0f
    /** 껍질피부가 준 방어가 남은 시간 */
    private var barkGuard = 0f
    /** 정신력 버프가 낸 피해와 발동 횟수 */
    var spiritDamage = 0.0
        private set
    private val spiritProcs = LinkedHashMap<String, Int>()
    private val spiritDamageByName = LinkedHashMap<String, Double>()

    /** 그 정신력 젬이 켜져 있는지 */
    fun spiritOn(en: String): Boolean = spiritBuffs.any { it.gem.en == en }

    // ---------------------------------------------------------- 피해·사망 기록
    /** 이번 전투에서 스킬 하나가 쌓은 피해 (주력·콤보·메타젬·소환수·전직 전부) */
    private val skillRows = LinkedHashMap<String, SkillDamageRow>()
    /** 플레이어가 맞은 피해의 최근 기록 — 사망 원인을 되짚는다 */
    private val damageTraces = ArrayList<DamageTrace>()
    /** 지금 피해를 넣고 있는 스킬 이름 — 어느 스킬 몫인지 적립할 때 쓴다 */
    private var skillContext = ""

    /** 플레이어를 마지막으로 때린 공격자·기술·피해량 */
    var lastHitSource = ""
        private set
    var lastHitSkill = ""
        private set
    var lastHitDamage = 0.0
        private set

    private var leechPool = 0.0
    private var meleeBudget = 2
    private var meleeBudgetTimer = 0.5f
    private var bossPulseTimer = 2.4f
    private var slamX = 0f
    private var slamY = 0f
    private var slamRadius = 150f
    private val rand = Random(seed)

    val triggers: List<TriggerRuntime> =
        stats.triggers.map { spec ->
            val critRate = stats.aps * (stats.critChance / 100.0)
            TriggerRuntime(spec, critRate, stats.cooldownRatePct)
        }
    var triggerCasts = 0
        private set
    var triggerDamage = 0.0
        private set

    /** 콤보 순서 — 전투 탭에서 짜 둔 콤보가 있으면 그것을 그대로 돌린다 */
    val comboPlan: List<ComboStep> =
        if (stats.combo.isNotEmpty()) stats.combo else ComboPlanner.plan(stats.motion, stats.kit)
    /** 사용자가 직접 짠 콤보인지 (자동 추천과 구분해 HUD에 적는다) */
    val comboCustom: Boolean = stats.combo.isNotEmpty()
    private var comboIndex = 0
    private var comboServed = 0
    /** 콤보 칸을 마지막으로 시전한 스킬과, 그 스킬이 실제로 나간 횟수 */
    val comboCasts = LinkedHashMap<String, Int>()
    /** 실제로 나간 콤보 스킬 이름 순서 (최근 40개) — 화면 검증·테스트에 쓴다 */
    val comboHistory = ArrayList<String>()
    /** 연계가 모자라 이번 차례를 주력기로 때우고 같은 칸에 머무른 횟수 */
    var comboWaits = 0
        private set
    /** 폭풍의 종을 쓰는 빌드인지 — 연계를 쌓아야 하는지 판단 */
    private val usesBell: Boolean =
        stats.motion == SkillMotion.TEMPEST_BELL || stats.kit.any { it.motion == SkillMotion.TEMPEST_BELL }

    /** 봇이 자동으로 돌리는 콤보 설명 (전투 전 화면에 보여 준다) */
    val comboSummary: String get() = ComboPlanner.summary(comboPlan)

    /** 지금 콤보에서 서 있는 칸 (화면 칩 강조에 쓴다) */
    val comboCurrent: Int get() = if (comboPlan.isEmpty()) 0 else comboIndex % comboPlan.size

    // ---------------------------------------------------------- 전직 부여 스킬
    /** 전직이 부여한 스킬(공허의 형상 등)의 남은 대기 시간 */
    private val ascendTimers = FloatArray(stats.ascendSkills.size) { i ->
        (ascendInterval(stats.ascendSkills[i]) * 0.6).toFloat()
    }

    /** 전직 부여 스킬의 발동 간격 (재사용 대기시간 회복 반영) */
    private fun ascendInterval(skill: AscendancyEffects.AscSkill): Double =
        skill.interval / (1.0 + stats.cooldownRatePct.coerceIn(0.0, 300.0) / 100.0)
    var ascendCasts = 0
        private set
    var ascendDamage = 0.0
        private set

    // ---------------------------------------------------------- 공허의 상·와류·돌개바람·종
    /** 공허의 형상이 남긴 반투명 상 */
    val phantoms = ArrayList<Phantom>()
    /** 와류 */
    val whirlwinds = ArrayList<Whirlwind>()
    /** 기름 지대 */
    val oils = ArrayList<OilGround>()
    /** 땅에 박힌 주문 토템 */
    val totems = ArrayList<Totem>()
    /** 기름 지대 위에 붙은 불꽃 수·터진 횟수 */
    var oilSpills = 0
        private set
    var oilIgnitions = 0
        private set
    var oilDamage = 0.0
        private set
    /** 전기를 띤 기름을 쏜 횟수 (권능 충전 소모) */
    var chargedOilShots = 0
        private set
    /** 기름 포화 연발 수 */
    var barrageShots = 0
        private set
    /** 기름 웅덩이에서 옆 웅덩이로 옮겨 붙은 불꽃 수 (연쇄 폭발) */
    var oilChainIgnitions = 0
        private set
    /** 토템이 쏜 주문 횟수 */
    var totemCasts = 0
        private set
    /** 전기불꽃 시전 횟수·한 번에 깔린 최대 발수·꿰뚫린 횟수 (HUD·테스트 확인용) */
    var sparkCasts = 0
        private set
    var sparkSalvo = 0
        private set
    var sparkHits = 0
        private set
    /** 돌개바람 */
    val twisters = ArrayList<Twister>()
    /** 서리 폭탄이 띄운 보주 */
    val frostBombs = ArrayList<FrostBomb>()
    /** 켈라리의 기만이 남긴 분신 (다시 쓰면 터진다) */
    var spiritDecoy: SpiritDecoy? = null
        private set
    /** 서리 폭탄 기폭 횟수 · 분신 폭발 횟수 (HUD·테스트 확인용) */
    var frostBombBlasts = 0
        private set
    var decoyBlasts = 0
        private set
    /** 얼음 폭발 시전 횟수 · 화염구가 띄운 화염 화살 고리 수 (테스트 확인용) */
    var iceNovaCasts = 0
        private set
    var fireballRings = 0
        private set
    /** 폭풍의 종·실촐 없는 종 */
    val bells = ArrayList<Bell>()
    /** 쌓인 권능 충전 — 공허의 상을 더 만들고 기름 포화가 태운다 */
    var powerCharges = 0
        private set
    /** 맞으며 쌓이는 인내 충전 */
    var enduranceCharges = 0
        private set
    /** 빙결·감전에서 쌓이는 격분 충전 */
    var frenzyCharges = 0
        private set
    /** 전투 격분이 다음 격분 충전을 줄 때까지 남은 시간 */
    private var frenzyTimer = 0f
    /** 충전 조절이 세 충전을 하나씩 태우는 주기 */
    private var regulationTimer = 0f
    /** 이번 전투에서 얻은 격분 충전 횟수 (결과·시험 확인용) */
    var frenzyGained = 0
        private set
    /** 이번 전투에서 태운 충전 수 */
    var chargesSpent = 0
        private set

    /** 충전 운용 규칙 */
    val chargeRules: ChargeRules get() = stats.charges
    /** 폭풍의 종에 쓰는 연계 */
    var comboStacks = 0
        private set
    /** 마지막으로 연계를 쌓은 뒤 흐른 시간 — 8초가 지나면 연계를 잃는다 */
    var comboSilence = 0f
        private set
    var phantomCasts = 0
        private set
    /** 지금까지 불러낸 상의 수 (한 번에 1~3개) */
    var phantomSpawns = 0
        private set
    var whirlwindCasts = 0
        private set
    var twisterCasts = 0
        private set
    var bellCasts = 0
        private set
    /** 선회 공격이 돌아간 횟수와 그 안에서 들어간 다단 히트 수 */
    var whirls = 0
        private set
    var whirlHits = 0
        private set
    /** 로그·HUD에 보여줄 지금 돌리는 콤보 */
    var comboText = ""
        private set
    var lastComboEvent = ""
        private set

    private var whirlPool = 0.0
    private var whirlTicks = 0
    private var whirlTickTimer = 0f
    private var hollowFocusTimer = 0f
    private var phantomCooldown = 0f

    // ---------------------------------------------------------- 보스 패턴 상태
    val telegraphs = ArrayList<Telegraph>()
    var bossPhase = 1
        private set
    var bossPatternName = ""
        private set
    private var bossPatternTimer = 2.2f
    private var bossPatternIndex = 0
    private var phaseTwoAnnounced = false
    /** 지금까지 사용한 보스 패턴 이름 (HUD·테스트 확인용) */
    val patternHistory = ArrayList<String>()
    /** 화면에 그려진 예고 장판 수 (테스트 확인용) */
    var telegraphsSpawned = 0
        private set

    private class PendingStrike(
        val x: Float,
        val y: Float,
        val radius: Float,
        val damage: Double,
        val element: DamageElement,
        val name: String,
        val kind: FxKind,
        var delay: Float,
        val trigger: TriggerRuntime? = null,
    )

    private val pendingStrikes = ArrayList<PendingStrike>()

    /** 연발로 예약된 발동 (0.04~0.07초 간격으로 하나씩 떨어진다) */
    private class PendingCast(
        var delay: Float,
        val rt: TriggerRuntime,
        val skill: TriggeredSkill,
        val target: Enemy,
        val index: Int,
        val burst: Int,
    )

    private val castQueue = ArrayList<PendingCast>()

    /** 이번 전투에서 가장 많이 터진 연발 수와 연발로 나간 총 발 수 */
    var maxBurst = 0
        private set
    var burstCasts = 0
        private set
    var lastBurstLabel = ""
        private set

    /** 이번 전투에 나오는 전체 적 수 (스폰 대기 포함) */
    val totalEnemies: Int get() = if (mode == BattleMode.BOSS) 1 else plannedEnemies
    val aliveCount: Int get() = enemies.count { it.alive }
    /** 아직 전장에 세우지 않은 적 수 */
    val pendingCount: Int get() = spawnQueue.size
    val bossEnemy: Enemy? get() = enemies.firstOrNull { it.boss != null }
    val maxPool: Double get() = player.baseLife + player.baseEs

    init {
        buildEncounter()
    }

    private fun buildEncounter() {
        if (mode == BattleMode.BOSS && boss != null) {
            val b = boss
            val e = Enemy(
                kind = EnemyKind.BOSS,
                x = 470f,
                y = 390f,
                maxHp = b.hp,
                hit = b.hit,
                interval = b.interval,
                element = b.element,
                ranged = true,
                speed = b.speed,
                scale = EnemyKind.BOSS.scale,
                slotAngle = 0f,
                boss = b,
                startDelay = b.interval * (0.35 + rand.nextDouble() * 0.5),
                walkStart = rand.nextFloat() * 6.28f,
            )
            enemies.add(e)
            plannedEnemies = 1
            bannerText = b.name
            banner = 2.2f
            spawnMinions()
            return
        }
        plannedEnemies = mobCount.coerceAtLeast(1)
        val rares = max(1, plannedEnemies / 20)
        val magics = max(1, plannedEnemies / 6)
        val normals = max(1, plannedEnemies - rares - magics)
        // 화면에 바로 세울 수 있는 만큼만 채우고, 나머지는 처치되면 순서대로 리필한다.
        repeat(normals) { spawnQueue.addLast(EnemyKind.NORMAL) }
        repeat(magics) { spawnQueue.addLast(EnemyKind.MAGIC) }
        repeat(rares) { spawnQueue.addLast(EnemyKind.RARE) }
        refillSpawns()
        bannerText = if (amp) "${mobCount}마리 · 증폭 16T" else "${mobCount}마리 · 16T"
        banner = 1.6f
        spawnMinions()
    }

    /** 소환수·반려수를 플레이어 주위에 세운다. 두꺼비는 저주받은 지대에서 나온다. */
    private fun spawnMinions() {
        val plan = stats.minions
        if (plan.isEmpty) return
        var index = 0
        for (spawn in plan.spawns) {
            val type = spawn.type
            repeat(spawn.count) {
                val angle = index * 2.399f + 0.6f
                val ring = 52f + (index % 3) * 16f
                val x = (player.x + cos(angle) * ring).coerceIn(40f, W - 30f)
                val y = (player.y + sin(angle) * ring * 0.6f + 18f).coerceIn(80f, H - 40f)
                val life = (stats.life + stats.es) * type.lifeShare * (1.0 + plan.spiritBudget * 0.0004)
                val dps = plan.minionDps / max(1, plan.spawns.sumOf { it.count })
                val interval = if (type.role == MinionRole.CASTER || type.ranged) 0.9 else 0.65
                minions.add(
                    Minion(
                        type = type,
                        x = x,
                        y = y,
                        maxHp = life.coerceAtLeast(30.0),
                        hitDamage = (dps * interval).coerceAtLeast(1.0),
                        attackInterval = interval,
                        startDelay = (0.3 + rand.nextFloat() * 0.4).toFloat(),
                        walkStart = rand.nextFloat() * 6.28f,
                    )
                )
                index++
            }
        }
        if (plan.toadCount > 0) {
            // 저주받은 지대 두 곳을 적 무리 쪽에 깐다.
            repeat(Minions.TOAD_AREAS) { area ->
                val ax = (W * 0.5f + (area - 0.5f) * 180f).coerceIn(120f, W - 80f)
                val ay = (H * 0.42f + area * 90f).coerceIn(120f, H - 80f)
                cursedGrounds.add(CursedGround(ax, ay, 96f, DamageElement.CHAOS))
            }
            // 각 지대는 0.25초마다 한 마리씩, 최대 4마리까지 만든다.
            repeat(plan.toadCount) { i -> spawnToad(i) }
        }
    }

    private fun toadType(level: Int): MinionType = MinionType(
        key = "witchtoad",
        nameKo = "마녀두꺼비",
        nameEn = "Witchtoad",
        role = MinionRole.TOAD,
        element = DamageElement.CHAOS,
        count = 1,
        maxCount = 8,
        dpsShare = 0.0,
        lifeShare = 0.02,
        spirit = 0.0,
        speed = 66f,
        scale = 0.42f,
    )

    private fun spawnToad(index: Int) {
        val plan = stats.minions
        if (plan.toadCount <= 0) return
        val ground = cursedGrounds.getOrNull(index % cursedGrounds.size.coerceAtLeast(1)) ?: return
        val type = toadType(plan.toadLevel)
        val x = ground.x + (rand.nextFloat() - 0.5f) * ground.radius
        val y = ground.y + (rand.nextFloat() - 0.5f) * ground.radius * 0.6f
        val life = (stats.life + stats.es) * type.lifeShare
        minions.add(
            Minion(
                type = type,
                x = x,
                y = y,
                maxHp = life.coerceAtLeast(10.0),
                hitDamage = darkHitPerExplosion.coerceAtLeast(1.0),
                attackInterval = 1.0,
                startDelay = (0.3 + rand.nextFloat() * 0.4).toFloat(),
                walkStart = rand.nextFloat() * 6.28f,
            )
        )
        ground.toads = minions.count { it.alive && it.isToad }
    }

    /** 죽어서 흐려진 적을 지우고, 비어 있는 자리를 대기 중인 무리로 채운다. */
    private fun refillSpawns() {
        if (spawnQueue.isEmpty()) return
        // 죽어서 흐려지는 중인 적이 쌓이면 먼저 치운다 (그릴 목록을 상한 안으로 유지).
        if (enemies.size >= activeCap + 30) enemies.removeAll { !it.alive }
        var guard = 0
        while (aliveCount < activeCap && spawnQueue.isNotEmpty() && guard++ < activeCap) {
            spawnNext(spawnQueue.removeFirst())
        }
    }

    private fun spawn(kind: EnemyKind, index: Int) {
        val columns = if (mobCount >= 50) 5 else 4
        val column = index % columns
        val row = index / columns
        val x = 300f + column * 64f + rand.nextFloat() * 26f
        val spread = 656f
        val rows = max(1, (mobCount + columns - 1) / columns)
        val y = 62f + spread * (row + 0.5f) / rows + (rand.nextFloat() - 0.5f) * 26f
        val hpMul = if (amp) 1.8 else 1.0
        val hitMul = if (amp) 1.4 else 1.0
        val crowd = when {
            mobCount >= 100 -> 0.82f
            mobCount >= 50 -> 0.92f
            else -> 1.0f
        }
        val element = when (kind) {
            EnemyKind.NORMAL -> DamageElement.PHYSICAL
            EnemyKind.MAGIC -> listOf(
                DamageElement.FIRE, DamageElement.COLD, DamageElement.LIGHTNING,
            )[rand.nextInt(3)]
            else -> listOf(
                DamageElement.FIRE, DamageElement.COLD, DamageElement.LIGHTNING, DamageElement.PHYSICAL,
            )[rand.nextInt(4)]
        }
        enemies.add(
            Enemy(
                kind = kind,
                x = x.coerceIn(90f, W - 40f),
                y = y.coerceIn(54f, H - 34f),
                maxHp = kind.hp * hpMul,
                hit = kind.hit * hitMul,
                interval = kind.interval,
                element = element,
                ranged = kind == EnemyKind.MAGIC || kind == EnemyKind.RARE,
                speed = kind.speed * (0.9f + rand.nextFloat() * 0.2f),
                scale = kind.scale * crowd,
                slotAngle = index * 2.399f,
                startDelay = kind.interval * (0.35 + rand.nextDouble() * 0.5),
                walkStart = rand.nextFloat() * 6.28f,
            )
        )
        spawnedEnemies++
    }

    /**
     * 대기 중인 무리를 하나 꺼내 세운다.
     * 첫 화면을 채운 뒤에는 무리가 사방에서 들어오는 것처럼 가장자리에서 세운다.
     */
    private fun spawnNext(kind: EnemyKind) {
        if (spawnedEnemies < activeCap) {
            spawn(kind, spawnedEnemies)
            return
        }
        val side = rand.nextInt(3)
        val x: Float
        val y: Float
        when (side) {
            0 -> {
                x = W - 18f
                y = 90f + rand.nextFloat() * (H - 160f)
            }
            1 -> {
                x = 90f + rand.nextFloat() * (W - 150f)
                y = H - 20f
            }
            else -> {
                x = 90f + rand.nextFloat() * (W - 150f)
                y = 60f
            }
        }
        val hpMul = if (amp) 1.8 else 1.0
        val hitMul = if (amp) 1.4 else 1.0
        val element = when (kind) {
            EnemyKind.NORMAL -> DamageElement.PHYSICAL
            EnemyKind.MAGIC -> listOf(
                DamageElement.FIRE, DamageElement.COLD, DamageElement.LIGHTNING,
            )[rand.nextInt(3)]
            else -> listOf(
                DamageElement.FIRE, DamageElement.COLD, DamageElement.LIGHTNING, DamageElement.PHYSICAL,
            )[rand.nextInt(4)]
        }
        enemies.add(
            Enemy(
                kind = kind,
                x = x,
                y = y,
                maxHp = kind.hp * hpMul,
                hit = kind.hit * hitMul,
                interval = kind.interval,
                element = element,
                ranged = kind == EnemyKind.MAGIC || kind == EnemyKind.RARE,
                speed = kind.speed * (0.9f + rand.nextFloat() * 0.2f),
                scale = kind.scale * 0.82f,
                slotAngle = rand.nextFloat() * 6.28f,
                startDelay = kind.interval * (0.35 + rand.nextDouble() * 0.5),
                walkStart = rand.nextFloat() * 6.28f,
            )
        )
        spawnedEnemies++
    }

    // ------------------------------------------------------------ 진행

    fun update(dtRaw: Double) {
        if (state != State.RUNNING) {
            decayVisuals(dtRaw.toFloat())
            return
        }
        var remaining = dtRaw
        while (remaining > 0.0001) {
            val dt = min(remaining, 1.0 / 60.0)
            step(dt.toFloat())
            remaining -= dt
            if (state != State.RUNNING) break
        }
        decayVisuals(dtRaw.toFloat())
    }

    private fun step(dt: Float) {
        elapsed += dt
        if (thunderOnShockCooldown > 0f) thunderOnShockCooldown = max(0f, thunderOnShockCooldown - dt)
        meleeBudgetTimer -= dt
        if (meleeBudgetTimer <= 0f) {
            meleeBudgetTimer = 0.5f
            meleeBudget = 2
        }
        updatePlayer(dt)
        updateBellCombo(dt)
        updateMinions(dt)
        updateBossPatterns(dt)
        updateTelegraphs(dt)
        updateAilments(dt)
        updateEnemies(dt)
        updateProjectiles(dt)
        updateDots(dt)
        updateTriggers(dt)
        updateCastQueue(dt)
        updateAscendancySkills(dt)
        updatePhantoms(dt)
        updateWhirlwinds(dt)
        updateTwisters(dt)
        updateFrostBombs(dt)
        updateBells(dt)
        updateOils(dt)
        updateTotems(dt)
        updateCharges(dt)
        checkOilIgnition()
        updateStrikes(dt)
        updateSpirit(dt)
        refillSpawns()
        if (player.life <= 0.0) {
            player.life = 0.0
            state = State.DEFEAT
            deathCause = buildDeathCause()
            playDeathEffect()
        } else if (spawnQueue.isEmpty() && enemies.none { it.alive }) {
            state = State.VICTORY
            victoryAge = 0f
            playVictoryEffect()
        }
    }

    private fun decayVisuals(dt: Float) {
        shake = max(0f, shake - dt * 34f)
        hurtFlash = max(0f, hurtFlash - dt * 2.6f)
        banner = max(0f, banner - dt)
        var i = 0
        while (i < effects.size) {
            effects[i].life -= dt
            if (effects[i].life <= 0f) effects.removeAt(i) else i++
        }
        i = 0
        while (i < particles.size) {
            val p = particles[i]
            p.life -= dt
            p.x += p.vx * dt
            p.y += p.vy * dt
            p.vy += p.gravity * dt
            p.vx *= (1f - 2.2f * dt)
            p.vy *= (1f - 1.4f * dt)
            if (p.life <= 0f) particles.removeAt(i) else i++
        }
        i = 0
        while (i < popups.size) {
            val p = popups[i]
            p.life -= dt
            p.y -= dt * 46f * p.size
            if (p.life <= 0f) popups.removeAt(i) else i++
        }
        for (e in enemies) {
            e.hitFlash = max(0f, e.hitFlash - dt * 5f)
            e.attackAnim = max(0f, e.attackAnim - dt / 0.3f)
            if (!e.alive) e.deadFade = min(1f, e.deadFade + dt * 2.2f)
            if (e.curseT > 0f) e.curseT = max(0f, e.curseT - dt)
        }
        // 쓰러진 뒤에는 사망 연출 시간이 흐른다 (화면 페이드인이 이 값을 따라간다).
        if (state != State.RUNNING) deathAge += dt
        // 클리어 연출도 같은 식으로 시간이 흐른다.
        if (state == State.VICTORY) victoryAge += dt
        // 흐려진 적은 지워 목록이 500마리까지 무한히 길어지지 않게 한다.
        if (enemies.size > activeCap) enemies.removeAll { !it.alive }
        player.hurtFlash = max(0f, player.hurtFlash - dt * 4f)
    }

    // ------------------------------------------------------------ 플레이어

    private fun updatePlayer(dt: Float) {
        // 재생 · 보호막 충전 · 흡수
        if (player.life > 0) {
            // 기본 재생 0.9%/초에 전직 효과(갑옷 재생 등)를 더한다.
            val regen = 0.009 + stats.lifeRegenPct.coerceIn(0.0, 20.0) / 100.0
            player.life = min(player.baseLife, player.life + player.baseLife * regen * dt)
            if (leechPool > 0.0) {
                val heal = min(leechPool, player.baseLife * 0.03 * dt)
                leechPool -= heal
                player.life = min(player.baseLife, player.life + heal)
            }
        }
        player.esDelay = max(0f, player.esDelay - dt)
        if (player.esDelay <= 0f && player.es < player.baseEs) {
            val rate = 0.22 * (1.0 + stats.esRechargeRatePct.coerceIn(-50.0, 200.0) / 100.0)
            player.es = min(player.baseEs, player.es + player.baseEs * rate * dt)
        }
        player.attackAnim = max(0f, player.attackAnim - dt / 0.26f)
        player.channel = max(0f, player.channel - dt)
        player.shiftFlash = max(0f, player.shiftFlash - dt)
        if (stats.beast.isBeast && player.shiftAnim < 1f) {
            player.shiftAnim = min(1f, player.shiftAnim + dt * 1.8f)
            if (player.shiftAnim >= 1f) {
                addFx(FxKind.SHIFT, player.x, player.y - 24f, radius = 96f, life = 0.7f, color = stats.beast.element.color)
                addFx(FxKind.RING, player.x, player.y, radius = 78f, life = 0.6f, color = stats.beast.element.color, width = 1.8f)
                addBurst(player.x, player.y - 24f, 18, stats.beast.element.color, 240f, 0.8f, gravity = -60f)
                bannerText = "${stats.beast.label} 형상"
                banner = 1.8f
                shake = max(shake, 4f)
            }
        }
        player.dodgeCooldown = max(0f, player.dodgeCooldown - dt)
        phantomCooldown = max(0f, phantomCooldown - dt)

        val nearest = nearestEnemy()
        var moving = false

        // 보스 장판이 곧 떨어지면 옆으로 구른다
        if (player.dodgeCooldown <= 0f) {
            val danger = dangerTelegraph()
            if (danger != null) {
                val a = danger.escapeAngle(player.x, player.y) + (rand.nextFloat() - 0.5f) * 0.35f
                player.dashTimer = 0.34f
                player.dashVx = cos(a) * 380f
                player.dashVy = sin(a) * 380f
                player.dodgeCooldown = 1.0f
                addPopup(player.x, player.y - 70f, "구르기", false, 0.55f, 0xFF8CE8C0.toInt(), 0.8f)
            }
        }

        if (player.dashTimer > 0f) {
            player.dashTimer -= dt
            player.x += player.dashVx * dt
            player.y += player.dashVy * dt
            player.walkPhase += dt * 26f
            moving = true
        } else if (player.whirlTimer > 0f) {
            // 선회 공격: 제자리에서 도는 게 아니라 적을 향해 빙글빙글 돌며 밀고 들어간다.
            player.whirlTimer -= dt
            player.whirlAngle += dt * 16f
            player.x = (player.x + player.whirlVx * dt).coerceIn(44f, W - 44f)
            player.y = (player.y + player.whirlVy * dt).coerceIn(84f, H - 34f)
            player.walkPhase += dt * 22f
            moving = true
        } else if (nearest != null) {
            val dx = nearest.x - player.x
            val dy = nearest.y - player.y
            val dist = hypot(dx, dy).coerceAtLeast(0.001f)
            player.facing = atan2(dy, dx)
            val desired = when (stats.form) {
                AttackForm.MELEE -> 56f
                AttackForm.BOW -> 152f
                AttackForm.CROSSBOW -> 168f
                AttackForm.CAST -> 148f
                AttackForm.UNARMED -> 52f
            }
            // 점멸 타격은 걸어가지 않는다. 다음 공격 때 적 옆으로 순간이동해 벤다.
            if (stats.flicker && (stats.form == AttackForm.MELEE || stats.form == AttackForm.UNARMED)) {
                moving = false
            } else {
                var vx = 0f
                var vy = 0f
                val speed = 148f
                if (dist > desired + 26f) {
                    vx = dx / dist
                    vy = dy / dist
                    moving = true
                } else if (dist < desired - 34f) {
                    vx = -dx / dist
                    vy = -dy / dist
                    moving = true
                } else {
                    val side = if (sin(elapsed * 0.8) >= 0) 1f else -1f
                    vx = -dy / dist * side * 0.55f
                    vy = dx / dist * side * 0.55f
                    moving = true
                }
                if (boss != null && nearest.kind == EnemyKind.BOSS && nearest.windup > 0.3f &&
                    player.dodgeCooldown <= 0f
                ) {
                    // 텔레그래프를 보고 옆으로 구르기
                    val side = if (rand.nextBoolean()) 1f else -1f
                    player.dashTimer = 0.40f
                    player.dashVx = -dy / dist * side * 320f
                    player.dashVy = dx / dist * side * 320f
                    player.dodgeCooldown = 1.4f
                }
                player.x += vx * speed * dt
                player.y += vy * speed * dt
            }
        }

        if (moving) player.walkPhase += dt * 11f
        player.moving = moving
        player.x = player.x.coerceIn(44f, W - 44f)
        player.y = player.y.coerceIn(84f, H - 34f)

        // 공격 — 공속이 아주 빠르면 한 프레임에 여러 번 친다. 남은 시간을 이어서 누적한다.
        player.blinkGlow = max(0f, player.blinkGlow - dt)
        player.attackTimer -= dt
        val interval = (1.0 / effectiveAps()).toFloat()
        var strikes = 0
        while (player.attackTimer <= 0f && strikes < MAX_STRIKES_PER_STEP) {
            val target = nearest
            player.attackTimer += interval
            player.attackAnim = 1f
            strikes++
            if (!stats.playerCarriesDps) {
                // 두꺼비·소환수가 때리는 빌드 — 플레이어는 저주/토템을 놓는 동작만 한다.
                player.attackCount++
                continue
            }
            if (target == null) {
                player.attackTimer = 0.1f
                break
            }
            player.attackCount++
            underSkill(mainSkillName) { takeTurn(target) }
        }
        updateWhirl(dt)
        // 프레임이 크게 밀려 예약이 쌓였을 때는 한 번 비워 무한 연타를 막는다.
        if (player.attackTimer < -interval * MAX_STRIKES_PER_STEP) player.attackTimer = 0f
        else if (player.attackTimer <= 0f) player.attackTimer = interval
    }

    private fun nearestEnemy(): Enemy? {
        var best: Enemy? = null
        var bestD = Float.MAX_VALUE
        for (e in enemies) {
            if (!e.alive) continue
            val d = (e.x - player.x) * (e.x - player.x) + (e.y - player.y) * (e.y - player.y)
            if (d < bestD) {
                bestD = d
                best = e
            }
        }
        return best
    }

    private fun nearestEnemyTo(x: Float, y: Float, range: Float = Float.MAX_VALUE): Enemy? {
        var best: Enemy? = null
        var bestD = range * range
        for (e in enemies) {
            if (!e.alive) continue
            val d = (e.x - x) * (e.x - x) + (e.y - y) * (e.y - y)
            if (d < bestD) {
                bestD = d
                best = e
            }
        }
        return best
    }

    // ------------------------------------------------------------ 소환수·반려수

    /**
     * 소환수·반려수가 적을 쫓아가 때리고, 두꺼비가 도약해 터진다.
     * 소환수의 피해는 플레이어 피해와 따로 세어 결과에 적는다.
     */
    private fun updateMinions(dt: Float) {
        if (minions.isEmpty() && cursedGrounds.isEmpty()) {
            if (stats.minions.toadCount > 0) spawnMinions()
            return
        }
        for (m in minions) {
            m.hitFlash = max(0f, m.hitFlash - dt * 5f)
            m.attackAnim = max(0f, m.attackAnim - dt * 3.2f)
            if (!m.alive) {
                m.deadFade = min(1f, m.deadFade + dt * 2f)
                if (m.respawn > 0f) {
                    m.respawn -= dt
                    if (m.respawn <= 0f) {
                        m.alive = true
                        m.hp = m.maxHp
                        m.deadFade = 0f
                        m.x = player.x + (rand.nextFloat() - 0.5f) * 70f
                        m.y = player.y + 26f + (rand.nextFloat() - 0.5f) * 30f
                        addFx(FxKind.RING, m.x, m.y, radius = 30f, life = 0.4f, color = 0xFFE8E0C8.toInt())
                    }
                }
                continue
            }
            m.anim += dt
            m.walkPhase += dt * if (m.type.role == MinionRole.COMPANION) 12f else 8f
            if (m.isToad) {
                updateToad(m, dt)
                continue
            }
            val target = nearestEnemyTo(m.x, m.y)
            m.target = target
            if (target == null) {
                // 적이 없으면 플레이어 옆으로 모인다.
                val dx = player.x - m.x
                val dy = player.y + 20f - m.y
                val dist = hypot(dx, dy)
                if (dist > 60f) {
                    m.x += dx / dist * m.type.speed * 0.6f * dt
                    m.y += dy / dist * m.type.speed * 0.6f * dt
                    m.facing = atan2(dy, dx)
                }
                continue
            }
            val dx = target.x - m.x
            val dy = target.y - m.y
            val dist = hypot(dx, dy).coerceAtLeast(0.001f)
            m.facing = atan2(dy, dx)
            val stop = if (m.type.ranged) 132f else 22f * m.type.scale
            if (dist > stop) {
                val speed = m.type.speed * (if (m.attackAnim > 0.4f) 0.4f else 1f)
                m.x += dx / dist * speed * dt
                m.y += dy / dist * speed * dt
            }
            m.x = m.x.coerceIn(26f, W - 26f)
            m.y = m.y.coerceIn(64f, H - 26f)
            m.attackTimer -= dt
            if (m.attackTimer <= 0f && dist < stop + 36f) {
                m.attackTimer = (m.attackInterval * (0.9 + rand.nextDouble() * 0.2)).toFloat()
                m.attackAnim = 1f
                val crit = rand.nextDouble() * 100.0 < stats.critChance * 0.6
                val hit = m.hitDamage * (if (crit) stats.critMulti / 100.0 else 1.0) * (0.9 + rand.nextDouble() * 0.2)
                minionStrike(m, target, hit, crit)
                if (m.type.role == MinionRole.SUPPORT) {
                    // 해골 성직자는 때리기보다 주변 소환수를 고친다.
                    healMinions(m)
                }
            }
        }
        minions.removeAll { !it.alive && it.deadFade >= 1f && it.respawn <= 0f }
        updateCursedGrounds(dt)
    }

    /** 소환수의 타격 — 적에게 피해를 넣고, 원거리면 투사체 대신 즉시 명중 연출 */
    private fun minionStrike(m: Minion, target: Enemy, damage: Double, crit: Boolean) {
        if (!target.alive) return
        val element = m.type.element
        val dealt = applyShield(target, damage)
        target.hp -= dealt
        damageDealt += dealt
        minionDamage += dealt
        creditDamage(m.type.label, dealt)
        target.hitFlash = 1f
        addPopup(
            target.x + (rand.nextFloat() - 0.5f) * 16f,
            target.y - 46f * target.scale,
            if (crit) "CRIT! ${formatShort(dealt)}" else formatShort(dealt),
            crit,
            0.5f,
            if (crit) 0xFFFFC24D.toInt() else element.color,
            0.72f,
        )
        if (m.type.ranged) {
            addFx(FxKind.BOLT, m.x, m.y - 20f, x2 = target.x, y2 = target.y - 20f, life = 0.16f, color = element.color, width = 0.9f)
        } else {
            addFx(FxKind.SLASH, target.x, target.y - 20f, angle = m.facing, radius = 22f, life = 0.16f, color = element.color)
        }
        // 소환수의 속성으로도 상태이상이 걸린다.
        when (element) {
            DamageElement.FIRE -> if (rand.nextDouble() < 0.18) {
                target.igniteT = max(target.igniteT, 2.6f)
                target.igniteDps = max(target.igniteDps, m.hitDamage * 0.15)
            }
            DamageElement.COLD -> if (rand.nextDouble() < 0.12) target.freezeT = max(target.freezeT, 1.4f)
            DamageElement.LIGHTNING -> if (rand.nextDouble() < 0.15) target.shockT = max(target.shockT, 2.4f)
            else -> Unit
        }
        if (target.hp <= 0.0) {
            minionKills++
            killEnemy(target)
        }
    }

    private fun healMinions(source: Minion) {
        var healed = false
        for (other in minions) {
            if (other === source || !other.alive) continue
            if (other.hp < other.maxHp * 0.7 && hypot(other.x - source.x, other.y - source.y) < 140f) {
                other.hp = min(other.maxHp, other.hp + other.maxHp * 0.06)
                healed = true
            }
        }
        if (healed) addFx(FxKind.RING, source.x, source.y, radius = 44f, life = 0.35f, color = 0xFF9CE8B0.toInt())
    }

    /** 두꺼비 — 가까운 적에게 도약해 터진다 */
    private fun updateToad(m: Minion, dt: Float) {
        if (m.leapT > 0f) {
            m.leapT -= dt / 0.42f
            val t = (1f - m.leapT).coerceIn(0f, 1f)
            m.x = m.leapFromX + (m.leapX - m.leapFromX) * t
            m.y = m.leapFromY + (m.leapY - m.leapFromY) * t - sin(t * 3.14159f) * 38f
            m.facing = atan2(m.leapY - m.leapFromY, m.leapX - m.leapFromX)
            if (m.leapT <= 0f) explodeToad(m)
            return
        }
        m.attackTimer -= dt
        val target = nearestEnemyTo(m.x, m.y, 260f)
        if (target != null && m.attackTimer <= 0f) {
            m.attackTimer = 0.45f + rand.nextFloat() * 0.35f
            m.leapFromX = m.x
            m.leapFromY = m.y
            m.leapX = target.x
            m.leapY = target.y
            m.leapT = 1f
        } else if (target == null) {
            // 적이 없으면 저주받은 지대 안에서 팔짝인다.
            val ground = cursedGrounds.minByOrNull { hypot(it.x - m.x, it.y - m.y) }
            if (ground != null && m.attackTimer <= 0f) {
                m.attackTimer = 1.1f
                m.leapFromX = m.x
                m.leapFromY = m.y
                m.leapX = (ground.x + (rand.nextFloat() - 0.5f) * ground.radius).coerceIn(30f, W - 30f)
                m.leapY = (ground.y + (rand.nextFloat() - 0.5f) * ground.radius * 0.6f).coerceIn(70f, H - 30f)
                m.leapT = 1f
            }
        }
    }

    /** 마녀두꺼비 폭발 — 암울한 결과 */
    private fun explodeToad(m: Minion) {
        if (!m.alive) return
        m.alive = false
        m.deadFade = 0f
        val radius = aoe(74f)
        val element = DamageElement.CHAOS
        addFx(FxKind.CLOUD, m.x, m.y, radius = radius, life = 0.55f, color = element.color)
        addFx(FxKind.IMPACT, m.x, m.y, radius = radius, life = 0.35f, color = 0xFFD9C4FF.toInt())
        addFx(FxKind.RING, m.x, m.y, radius = radius, life = 0.4f, color = 0xFFB07AFF.toInt())
        addBurst(m.x, m.y - 10f, 14, element.color, 260f, 0.6f)
        var hits = 0
        for (e in enemies) {
            if (!e.alive) continue
            if (hypot(e.x - m.x, e.y - m.y) > radius + 16f * e.scale) continue
            val dealt = applyShield(e, darkHitPerExplosion.coerceAtLeast(1.0) * (0.9 + rand.nextDouble() * 0.2))
            e.hp -= dealt
            damageDealt += dealt
            minionDamage += dealt
            darkConsequencesDamage += dealt
            creditDamage(Minions.DARK_CONSEQUENCES_KO, dealt)
            e.hitFlash = 1f
            hits++
            if (e.hp <= 0.0) {
                minionKills++
                killEnemy(e)
            }
        }
        toadExplosions++
        if (hits > 0) addPopup(m.x, m.y - 30f, "암울한 결과", false, 0.7f, 0xFFC9A6FF.toInt(), 0.9f)
    }

    /** 저주받은 지대 — 0.25초마다 두꺼비를 하나씩 만든다 */
    private fun updateCursedGrounds(dt: Float) {
        if (cursedGrounds.isEmpty()) return
        cursedGrounds.forEachIndexed { index, ground ->
            ground.pulse += dt
            val aliveToads = minions.count { it.alive && it.isToad }
            ground.toads = aliveToads
            if (stats.minions.toadCount <= 0) return@forEachIndexed
            if (aliveToads >= stats.minions.toadCount) return@forEachIndexed
            ground.spawnTimer -= dt
            if (ground.spawnTimer <= 0f) {
                ground.spawnTimer = 0.25f
                val toadsHere = minions.count { it.alive && it.isToad && hypot(it.x - ground.x, it.y - ground.y) < ground.radius * 1.4f }
                if (toadsHere < Minions.TOAD_PER_AREA) spawnToad(index)
            }
        }
    }

    /** 소환수가 맞는다 */
    private fun hitMinion(m: Minion, raw: Double, fromX: Float, fromY: Float) {
        if (!m.alive) return
        // 소환수는 저항이 따로 없어 원소 피해를 그대로 받는다.
        m.hp -= raw
        m.hitFlash = 1f
        addPopup(m.x, m.y - 30f, formatShort(raw), false, 0.4f, 0xFFFF9A9A.toInt(), 0.62f)
        addFx(FxKind.SLASH, fromX, fromY, angle = atan2(m.y - fromY, m.x - fromX), radius = 20f, life = 0.16f, color = 0xFFFF6A6A.toInt())
        if (m.hp <= 0.0) killMinion(m)
    }

    private fun killMinion(m: Minion) {
        if (!m.alive) return
        m.alive = false
        m.hp = 0.0
        m.deadFade = 0f
        minionDeaths++
        addBurst(m.x, m.y - 8f, 10, 0xFFE8E0C8.toInt(), 200f, 0.6f)
        // 소환수 사망 시 시전 — 유지형 소환수가 쓰러지면 메타 에너지가 찬다.
        if (!m.isToad) fireEvent(TriggerCondition.MINION_DEATH, null, 1.0)
        var total = 0
        for (other in minions) if (other.alive && other.type.key == m.type.key) total++
        if (total == 0) {
            // 수가 줄면 일정 시간 뒤 다시 세운다.
            m.respawn = 5.0f
        }
    }

    /** 소환수 전체에게 들어오는 광역 피해 (보스 장판·함성) */
    private fun damageMinionsInRadius(x: Float, y: Float, radius: Float, damage: Double) {
        for (m in minions) {
            if (!m.alive || m.isToad) continue
            if (hypot(m.x - x, m.y - y) <= radius) {
                m.hp -= damage * 0.7
                m.hitFlash = 1f
                if (m.hp <= 0.0) killMinion(m)
            }
        }
    }

    /** 고유 아이템이 주는 추가 투사체까지 더한 값 */
    private fun totalExtraProjectiles(): Int =
        stats.extraProjectiles + stats.unique.extraProjectiles

    /** PoB DPS를 평균 피해로 환산 (치명타 기대값·희귀 처치 강화 포함) */
    private fun baseHitDamage(): Double {
        val aps = effectiveAps()
        val dps = stats.hitDps.takeIf { it.isFinite() && it > 0.0 } ?: 1.0
        val critFactor = 1.0 + (stats.critChance / 100.0) * (stats.critMulti / 100.0 - 1.0)
        val perHit = dps / aps
        val factor = critFactor.takeIf { it.isFinite() && it > 0.0 } ?: 1.0
        val hunt = 1.0 + huntStacks * 0.08
        // 정신력 버프의 격노·소모·원소 융합은 때리는 순간마다 배율로 들어간다.
        return (perHit / factor * hunt * spiritHitMul()).coerceAtLeast(1.0)
    }

    /** 초당 공격 횟수가 0/NaN으로 들어와도 루프가 멈추지 않게 한다. */
    private fun effectiveAps(): Double {
        val base = stats.aps.takeIf { it.isFinite() && it > 0.0 }?.coerceIn(0.2, 30.0) ?: 1.0
        // 충전 조절이 유지 중이면 격분 충전이 있는 동안 공격 속도가 오른다.
        val speed = if (chargeRules.regulation && frenzyCharges > 0) chargeRules.regulationSpeedPct else 0.0
        return base * (1.0 + speed / 100.0)
    }

    private fun rollDamage(): Pair<Double, Boolean> {
        // 충전 조절이 유지 중이면 권능 충전이 있는 동안 치명타 확률이 오른다.
        val extraCrit = if (chargeRules.regulation && powerCharges > 0) chargeRules.regulationCritPct else 0.0
        val critChance = (stats.critChance * (1.0 + extraCrit / 100.0)).coerceAtMost(100.0)
        val crit = rand.nextDouble() * 100.0 < critChance
        val base = baseHitDamage() * (0.92 + rand.nextDouble() * 0.16)
        return (if (crit) base * stats.critMulti / 100.0 else base) to crit
    }

    private fun fireAttack(target: Enemy) {
        // 폭풍의 종을 쓰는 빌드는 주력기로 연계를 쌓는다 (종 자체는 연계를 만들지 않는다).
        if (usesBell && stats.motion != SkillMotion.TEMPEST_BELL) gainBellCombo()
        val (damage, crit) = rollDamage()
        spiritOnStrike(damage)
        val angle = atan2(target.y - player.y, target.x - player.x)
        val blink = stats.flicker &&
            (stats.form == AttackForm.MELEE || stats.form == AttackForm.UNARMED)
        if (blink) {
            flickerStrike(target, damage, crit)
            return
        }
        // 이름으로 알아본 모션(선회 공격·기름 포화·전기불꽃 등)이 있으면 그 모션으로 낸다.
        if (castMotion(stats.motion, target, damage, crit, angle)) return
        // 야수로 변신한 빌드는 사람 무기 대신 발톱·숨결로 친다.
        if (stats.beast.isBeast) {
            val beastMotion = if (stats.beast == BeastForm.WYVERN &&
                (stats.fx == SkillEffectType.FIRE || stats.fx == SkillEffectType.LIGHTNING)
            ) {
                SkillMotion.WYVERN_BREATH
            } else {
                SkillMotion.BEAST_MAUL
            }
            castMotion(beastMotion, target, damage, crit, angle)
            return
        }
        when (stats.form) {
            AttackForm.MELEE, AttackForm.UNARMED -> meleeStrike(target, damage, crit, angle)
            AttackForm.BOW -> bowShot(target, damage, crit, angle)
            AttackForm.CROSSBOW -> crossbowShot(target, damage, crit, angle)
            AttackForm.CAST -> castSpell(target, damage, crit, angle)
        }
    }

    /**
     * 봇의 한 차례.
     *
     * 빌드에 준비 단계가 필요한 조합(와류→돌개바람, 연계→폭풍의 종 등)이 있으면
     * 그 순서대로 스킬을 바꿔 쓰고, 아니면 주력 스킬로 그냥 친다.
     */
    private fun takeTurn(target: Enemy) {
        val step = nextComboStep()
        if (step == null) {
            fireAttack(target)
            return
        }
        // 폭풍의 종은 연계 4가 필요하다 — 모자라면 이번 차례는 주력기로 연계를 채운다.
        if (step.motion == SkillMotion.TEMPEST_BELL &&
            step.role == ComboRole.BURST && comboStacks < ComboPlanner.BELL_COMBO
        ) {
            comboWaits++
            comboIndex = (comboIndex - 1 + comboPlan.size) % comboPlan.size
            comboServed = 0
            comboText = "${step.label} · 연계 ${min(ComboPlanner.BELL_COMBO, comboStacks + 1)}/${ComboPlanner.BELL_COMBO}"
            noteComboCast(stats.skillName.ifBlank { "주력" })
            fireAttack(target)
            return
        }
        if (step.motion == stats.motion) {
            noteComboCast(step.label)
            fireAttack(target)
            return
        }
        noteComboCast(step.label)
        performKitStep(step, target)
    }

    /** 콤보 순서를 기록한다 */
    private fun noteComboCast(label: String) {
        if (label.isBlank()) return
        comboCasts[label] = (comboCasts[label] ?: 0) + 1
        comboHistory.add(label)
        if (comboHistory.size > 40) comboHistory.removeAt(0)
    }

    /** 콤보 한 칸을 진행하고 화면에 지금 순서를 적는다 */
    private fun nextComboStep(): ComboStep? {
        if (comboPlan.size <= 1) return null
        val step = comboPlan[comboIndex % comboPlan.size]
        comboServed++
        if (comboServed >= step.repeats) {
            comboServed = 0
            comboIndex = (comboIndex + 1) % comboPlan.size
        }
        val next = comboPlan[comboIndex % comboPlan.size]
        comboText = when (step.role) {
            ComboRole.BUILD -> "${step.label} · ${step.note.ifBlank { next.label }}"
            ComboRole.BURST -> if (step.note.isBlank()) step.label else "${step.label} · ${step.note}"
            ComboRole.CURSE -> "${step.label} → ${next.label}"
            ComboRole.MAIN -> {
                val stack = if (usesBell && step.repeats > 1 && step.note.isNotBlank()) {
                    " ${min(comboStacks, step.repeats)}/${step.repeats}"
                } else ""
                "${step.label}$stack → ${next.label}"
            }
        }
        return step
    }

    /** 빌드의 다른 스킬을 한 번 쓴다 (봇이 콤보 순서에 따라 부른다) */
    private fun performKitStep(step: ComboStep, target: Enemy) {
        val kit = stats.kit.firstOrNull { it.motion == step.motion }
        val dps = kit?.dps?.takeIf { it.isFinite() && it > 0.0 }
            ?: step.dps.takeIf { it.isFinite() && it > 0.0 }
            ?: stats.dps * 0.55
        val damage = (dps * 0.42).coerceAtLeast(1.0)
        val crit = rand.nextDouble() * 100.0 < stats.critChance
        val angle = atan2(target.y - player.y, target.x - player.x)
        underSkill(step.label) {
            if (!castMotion(step.motion, target, damage, crit, angle)) fireAttack(target)
        }
    }

    /**
     * 스킬 모션별 동작. 인게임 모양을 보고 만든 회전·투사체·소용환 동작이다.
     * 처리한 모션이면 true를 돌린다.
     */
    private fun castMotion(
        motion: SkillMotion,
        target: Enemy,
        damage: Double,
        crit: Boolean,
        angle: Float,
    ): Boolean {
        when (motion) {
            SkillMotion.WHIRLING_ASSAULT -> whirlAssault(target, damage, crit)
            SkillMotion.WHIRLING_SLASH -> whirlSlash(target, damage, crit, angle)
            SkillMotion.TWISTER -> spinTwister(target, damage, angle)
            SkillMotion.TEMPEST_BELL -> ringBell(target, damage, crit)
            SkillMotion.SPARK -> sparkVolley(target, damage, crit, angle)
            SkillMotion.ICE_NOVA -> iceNova(target, damage, crit, angle)
            SkillMotion.FROST_BOMB -> frostBomb(target, damage)
            SkillMotion.SPIRIT_GUST -> spiritGust(target, damage, crit, angle)
            SkillMotion.LIGHTNING_ARROW -> lightningArrow(target, damage, crit, angle)
            SkillMotion.ICE_SHOT -> iceShot(target, damage, crit, angle)
            SkillMotion.COMET -> cometStrike(target, damage)
            SkillMotion.FIREBALL -> fireball(target, damage, crit, angle)
            SkillMotion.GRENADE -> grenade(target, damage, angle)
            SkillMotion.KILLING_PALM -> killingPalm(target, damage, crit)
            SkillMotion.TORNADO_SHOT -> tornadoShot(target, damage, crit, angle)
            SkillMotion.CURSE -> curseCast(target, damage, angle)
            SkillMotion.OIL_BARRAGE -> oilBarrage(target, damage)
            SkillMotion.OIL_GRENADE -> oilGrenade(target, damage, angle)
            SkillMotion.BARRAGE -> barrageVolley(target, damage, crit, angle)
            SkillMotion.TOTEM -> placeTotem(target, damage, angle)
            SkillMotion.BEAST_MAUL -> beastMaul(target, damage, crit, angle)
            SkillMotion.BEAST_HOWL -> beastHowl(target, damage)
            SkillMotion.WYVERN_BREATH -> wyvernBreath(target, damage, crit, angle)
            else -> return false
        }
        return true
    }

    /** 선회 공격 — 빙글빙글 돌며 적 쪽으로 전진하고, 그 사이 주변을 계속 벤다. */
    private fun whirlAssault(target: Enemy, damage: Double, crit: Boolean) {
        whirlPool += damage
        // 아주 빠른 공속에서도 회전 히트가 무한정 쌓이지 않게 묶어 둔다.
        if (whirlTicks < 8) whirlTicks += SkillMotions.hitCount(SkillMotion.WHIRLING_ASSAULT)
        whirlTickTimer = min(whirlTickTimer, 0.03f)
        whirls++
        player.whirlTimer = 0.32f
        val dx = target.x - player.x
        val dy = target.y - player.y
        val dist = hypot(dx, dy).coerceAtLeast(0.001f)
        player.whirlVx = dx / dist * 195f
        player.whirlVy = dy / dist * 195f
        player.facing = atan2(dy, dx)
        player.attackAnim = 1f
        addFx(FxKind.CYCLONE, player.x, player.y - 8f, radius = aoe(98f), life = 0.34f, color = 0xFFEAF4FF.toInt(), width = 1.3f)
        addFx(FxKind.RING, player.x, player.y, radius = aoe(66f), life = 0.26f, color = 0xFFBFD8FF.toInt())
        if (crit) shake = max(shake, 3.2f)
        spawnPhantoms(target)
    }

    /** 선회 공격의 다단 히트를 한 번씩 내리찍는다 */
    private fun updateWhirl(dt: Float) {
        if (whirlTicks <= 0) return
        whirlTickTimer -= dt
        if (whirlTickTimer > 0f) return
        whirlTickTimer = 0.085f
        whirlTicks--
        val per = if (whirlTicks <= 0) whirlPool else whirlPool / (whirlTicks + 1)
        whirlPool = max(0.0, whirlPool - per)
        strikeWhirl(per)
    }

    /** 회전 한 바퀴 — 사방의 적을 베고 종도 친다 */
    private fun strikeWhirl(damage: Double) {
        val reach = aoe(114f)
        val arc = rand.nextFloat() * 6.28f
        addFx(FxKind.SLASH, player.x, player.y - 18f, angle = arc, radius = reach, life = 0.16f, color = 0xFFDCE8FF.toInt(), width = 1.5f)
        addFx(FxKind.SLASH, player.x, player.y - 18f, angle = arc + 3.14f, radius = reach * 0.88f, life = 0.18f, color = 0xFFBFD4FF.toInt(), width = 1.1f)
        addBurst(player.x, player.y - 10f, 2, 0xFFBFD8FF.toInt(), 140f, 0.3f)
        val victims = enemies.filter { it.alive && hypot(it.x - player.x, it.y - player.y) < reach }
            .sortedBy { hypot(it.x - player.x, it.y - player.y) }
        if (victims.isEmpty()) {
            underSkill(SkillMotion.WHIRLING_ASSAULT.label) {
                nearestEnemy()?.let { hitEnemy(it, damage, false, arc, 0.8f) }
            }
        } else {
            victims.take(6).forEachIndexed { index, e ->
                underSkill(SkillMotion.WHIRLING_ASSAULT.label) {
                    hitEnemy(e, if (index == 0) damage else damage * 0.6, false, arc, 0.9f)
                }
                whirlHits++
            }
            // 다음 회전은 가장 가까운 적을 향해 전진한다
            val t = victims.first()
            if (player.whirlTimer > 0f) {
                val a = atan2(t.y - player.y, t.x - player.x)
                player.whirlVx = cos(a) * 195f
                player.whirlVy = sin(a) * 195f
            }
        }
        strikeBells(player.x, player.y, reach, damage)
    }

    /** 소용돌이 베기 — 크게 휘돌러 와류를 남긴다. 겹치면 단계가 올라 커진다. */
    private fun whirlSlash(target: Enemy, damage: Double, crit: Boolean, angle: Float) {
        val reach = aoe(120f)
        // 360도로 한 바퀴 도는 검기 원호 — 세 장을 시차를 두고 남긴다.
        addFx(FxKind.CYCLONE, player.x, player.y - 6f, radius = reach, life = 0.44f, color = 0xFFD6EEFF.toInt(), width = 1.4f)
        for (i in 0 until 3) {
            addFx(
                FxKind.SLASH, player.x, player.y - 18f, angle = angle + i * 2.094f,
                radius = reach * (1f - i * 0.06f), life = 0.30f + i * 0.03f,
                color = if (i == 0) 0xFFE6F0FF.toInt() else 0xFFCFE0FF.toInt(),
                width = 1.9f - i * 0.2f,
            )
        }
        addFx(FxKind.DUST, player.x, player.y, radius = reach * 0.9f, life = 0.4f, color = 0xFFB9AFA6.toInt())
        addBurst(player.x, player.y - 10f, 8, 0xFFD6EEFF.toInt(), 190f, 0.35f)
        val victims = enemies.filter { it.alive && hypot(it.x - player.x, it.y - player.y) < reach }
        if (victims.isEmpty()) hitEnemy(target, damage, crit, angle, 1f)
        victims.forEach { e ->
            hitEnemy(e, if (e === target) damage else damage * 0.7, crit && e === target, angle, 0.9f)
            e.slowT = max(e.slowT, 3.0f)
        }
        // 와류: 같은 자리에 있으면 단계를 올리고 적 쪽으로 밀려 간다
        val existing = whirlwinds.minByOrNull { hypot(it.x - player.x, it.y - player.y) }
        if (existing != null && hypot(existing.x - player.x, existing.y - player.y) < existing.radius * 0.85f) {
            existing.stage = min(3, existing.stage + 1)
            existing.radius = 68f + existing.stage * 18f
            existing.life = max(existing.life, 3.4f)
            existing.x += (target.x - existing.x) * 0.35f
            existing.y += (target.y - existing.y) * 0.35f
            lastComboEvent = "와류 ${existing.stage}단계"
        } else {
            if (whirlwinds.size >= 4) whirlwinds.removeAt(0)
            whirlwinds.add(Whirlwind(player.x, player.y, 86f, 1, 3.8f))
            whirlwindCasts++
            lastComboEvent = "와류 1단계"
        }
        val stage = whirlwinds.maxOfOrNull { it.stage } ?: 1
        // 단계가 오를수록 바깥으로 퍼지는 단계 링
        for (s in 1..stage) {
            addFx(
                FxKind.RING, player.x, player.y, radius = 52f + s * 22f,
                life = 0.34f + s * 0.05f, color = 0xFFBFE4FF.toInt(), width = 1f + s * 0.25f,
            )
        }
        addPopup(player.x, player.y - 96f, "와류 ${stage}단계", false, 0.7f, 0xFFBFE4FF.toInt(), 0.82f)
        spawnPhantoms(target)
    }

    /** 돌개바람 — 창을 휘돌러 전진하는 회오리를 띄운다. 와류를 먹으면 증폭된다. */
    private fun spinTwister(target: Enemy, damage: Double, angle: Float) {
        val eaten = whirlwinds.toList()
        whirlwinds.clear()
        val count = 1 + min(3, eaten.size)
        val total = damage * (1.0 + eaten.size * 0.55)
        repeat(count) { i ->
            val spread = (i - (count - 1) / 2f) * 0.30f
            val twister = Twister(
                x = (target.x + (rand.nextFloat() - 0.5f) * 44f).coerceIn(40f, W - 40f),
                y = (target.y + (rand.nextFloat() - 0.5f) * 34f).coerceIn(70f, H - 30f),
                dirX = cos(angle + spread),
                dirY = sin(angle + spread),
                damage = total / count,
                life = 1.8f,
                amplified = eaten.isNotEmpty(),
                element = stats.fx.element,
            )
            twister.angle = angle + spread
            twisters.add(twister)
        }
        twisterCasts += count
        lastComboEvent = if (eaten.isNotEmpty()) "와류 소모 돌개바람 x$count" else "돌개바람 x$count"
        // 두 팔을 내려친 자리에서 다층 회오리가 솟구친다.
        for (i in 0 until 3) {
            addFx(
                FxKind.CYCLONE, player.x + cos(angle) * (26f + i * 10f), player.y - 16f - i * 10f,
                radius = 92f - i * 18f, life = 0.30f + i * 0.05f,
                color = if (i == 0) 0xFFD6F0FF.toInt() else 0xFFBBD8EE.toInt(),
                width = 1.3f - i * 0.2f,
            )
        }
        addFx(FxKind.WING_GUST, player.x, player.y - 12f, angle = angle, radius = 110f, life = 0.36f, color = 0xFFE8F6FF.toInt())
        addBurst(target.x, target.y - 20f, 10, 0xFFD6F0FF.toInt(), 220f, 0.5f)
        addBurst(player.x, player.y - 20f, 8, 0xFFE8F6FF.toInt(), 190f, 0.4f)
    }

    /** 폭풍의 종 연계를 하나 쌓는다 — 남은 연계 시간도 함께 되돌린다 */
    private fun gainBellCombo() {
        comboStacks = min(ComboPlanner.BELL_COMBO, comboStacks + 1)
        comboSilence = 0f
    }

    /** poe2db: 폭풍의 종이 8초 동안 연계를 생성하지 않으면 연계를 잃는다 */
    private fun updateBellCombo(dt: Float) {
        if (comboStacks <= 0) return
        comboSilence += dt
        if (comboSilence < ComboPlanner.BELL_COMBO_TIMEOUT) return
        comboStacks = 0
        comboSilence = 0f
        addPopup(player.x, player.y - 92f, "연계 상실", false, 0.6f, 0xFF9FD8FF.toInt(), 0.78f)
    }

    /** 테스트용 — 전투를 돌리지 않고 연계 시계만 돌린다 */
    internal fun advanceBellCombo(seconds: Float) = updateBellCombo(seconds)

    /** 폭풍의 종 — 연계 4를 채우면 지팡이 종을 키워 지면에 떨어뜨린다. */
    private fun ringBell(target: Enemy, damage: Double, crit: Boolean) {        if (comboStacks < ComboPlanner.BELL_COMBO && bells.none { it.ghost }) {
            addPopup(player.x, player.y - 92f, "연계 ${comboStacks}/${ComboPlanner.BELL_COMBO}", false, 0.6f, 0xFF9FD8FF.toInt(), 0.8f)
            meleeStrike(target, damage, crit, atan2(target.y - player.y, target.x - player.x))
            return
        }
        comboStacks = 0
        comboSilence = 0f
        val angle = atan2(target.y - player.y, target.x - player.x)
        val bx = (player.x + cos(angle) * 44f).coerceIn(50f, W - 50f)
        val by = (player.y + sin(angle) * 34f).coerceIn(80f, H - 30f)
        if (bells.count { !it.ghost } >= 3) bells.removeAll { !it.ghost }
        bells.add(Bell(bx, by, 12f, ghost = false))
        bellCasts++
        val radius = aoe(124f)
        addFx(FxKind.IMPACT, bx, by, radius = radius, life = 0.5f, color = 0xFFFFD98A.toInt(), width = 1.7f)
        addFx(FxKind.SHOCKWAVE, bx, by, radius = radius, life = 0.6f, color = 0xFFFFE9A8.toInt(), width = 1.8f)
        addBurst(bx, by, 16, 0xFFFFE9B0.toInt(), 300f, 0.7f)
        shake = max(shake, 6.2f)
        areaHit(bx, by, radius, damage * 1.2, DamageElement.PHYSICAL, "폭풍의 종", true)
    }

    /** 종을 때리면 충격파가 터지고, 공허의 종은 권능 충전을 돌려준다. */
    private fun strikeBells(x: Float, y: Float, radius: Float, damage: Double) {
        var i = 0
        while (i < bells.size) {
            val b = bells[i]
            if (hypot(b.x - x, b.y - y) > radius + 20f) {
                i++
                continue
            }
            b.hits++
            b.swing = 1f
            val shock = aoe(if (b.ghost) 120f else 134f)
            val hit = if (b.ghost) damage * 1.4 else damage * 0.8
            addFx(FxKind.SHOCKWAVE, b.x, b.y, radius = shock, life = 0.52f, color = 0xFFFFE9A8.toInt(), width = 1.7f)
            addFx(FxKind.RING, b.x, b.y, radius = shock * 0.62f, life = 0.4f, color = 0xFFFFD98A.toInt())
            addBurst(b.x, b.y - 18f, 12, 0xFFFFE9B0.toInt(), 260f, 0.6f)
            areaHit(b.x, b.y, shock, hit, DamageElement.PHYSICAL, if (b.ghost) "공허의 종" else "종 충격파", true)
            if (b.ghost) {
                ascendCasts++
                ascendDamage += hit
                gainPowerCharge()
            }
            shake = max(shake, 4.4f)
            if (b.hits >= if (b.ghost) 1 else 3) bells.removeAt(i) else i++
        }
    }

    /**
     * 전기불꽃 — poe2db.tw/kr/Spark.
     *
     * 20레벨 젬은 투사체를 9개 쏘고, 냉기 주입을 소모하면 같은 수를 나선 한 겹 더
     * 깔아 원형으로 퍼뜨린다. 그래서 한 번 시전이 18발, 추가 투사체가 있으면 그만큼
     * 더 나가 화면 전체를 채운다. 줄기는 2초 동안 바닥을 지그재고 기어가며(pierce)
     * 지나가는 적을 꿰뚫고, 벽에 부딪히면 튕긴다. 피해는 시전 1회 몫을 발수로 나눠
     * 담으므로, 여러 줄기가 함께 맞아도 DPS가 부풀지 않는다.
     */
    private fun sparkVolley(target: Enemy, damage: Double, crit: Boolean, angle: Float) {
        val extra = totalExtraProjectiles().coerceIn(0, 8)
        val count = SparkMath.salvo(stats.skillLevel, extra)
        val per = SparkMath.perHitDamage(damage, count)
        sparkCasts++
        sparkSalvo = max(sparkSalvo, count)
        val perRing = (count + SparkMath.RINGS - 1) / SparkMath.RINGS
        repeat(count) { i ->
            // 두 겹을 반 칸씩 엇갈리게 돌려 나선(원형)으로 퍼뜨린다.
            val ring = i % SparkMath.RINGS
            val slot = i / SparkMath.RINGS
            val a = angle + (slot + ring * 0.5f) * (PI_F * 2f / perRing) +
                (rand.nextFloat() - 0.5f) * 0.20f
            val p = Projectile(
                x = player.x + cos(a) * (14f + ring * 8f),
                y = player.y + sin(a) * (14f + ring * 8f) - 20f,
                dirX = cos(a), dirY = sin(a),
                speed = 210f + rand.nextFloat() * 100f,
                damage = per,
                crit = crit,
                target = target,
                hostile = false,
                element = DamageElement.LIGHTNING,
                kind = ProjKind.SPARK,
                life = SparkMath.DURATION * (0.88f + rand.nextFloat() * 0.12f),
                wobble = 0.55f + rand.nextFloat() * 0.35f,
                bounces = SparkMath.BOUNCES,
                seed = rand.nextInt(),
            )
            p.pierce = SparkMath.PIERCE
            p.energyWeight = SparkMath.energyWeight(count)
            p.turnTimer = rand.nextFloat() * 0.12f
            addProjectile(p)
        }
        // 시전 순간 손끝에서 사방으로 번개가 터진다.
        addFx(FxKind.AURA, player.x, player.y - 26f, radius = 44f, life = 0.3f, color = 0xFFFFF3A8.toInt())
        addFx(FxKind.ARC_WEB, player.x, player.y - 24f, angle = angle, radius = 62f, life = 0.24f, color = 0xFFFFE272.toInt())
        repeat(5) { i ->
            val a = angle + i * 1.2566f + (rand.nextFloat() - 0.5f) * 0.4f
            addFx(
                FxKind.BOLT, player.x, player.y - 22f,
                x2 = player.x + cos(a) * (54f + rand.nextFloat() * 40f),
                y2 = player.y + sin(a) * (54f + rand.nextFloat() * 30f) - 18f,
                life = 0.15f, color = 0xFFBFE6FF.toInt(), seed = rand.nextInt(), width = 0.85f,
            )
        }
        addBurst(player.x, player.y - 22f, 10, 0xFFFFE272.toInt(), 210f, 0.32f)
    }

    /**
     * 얼음 폭발 — poe2db.tw/kr/Ice_Nova.
     *
     * 얼음 파동이 사방으로 퍼지고, 가까이 붙은 적일수록 세게 밀려나며 더 많이 맞는다.
     * 나비라의 파열(얼음 지대를 파열시켜 높은 냉기 피해)도 같은 모션으로 낸다.
     */
    private fun iceNova(target: Enemy, damage: Double, crit: Boolean, angle: Float) {
        val radius = aoe(92f)
        addFx(FxKind.FROST_NOVA, player.x, player.y - 8f, radius = radius, life = 0.55f, color = 0xFFBFF4FF.toInt())
        addFx(FxKind.RING, player.x, player.y, radius = radius, life = 0.5f, color = 0xFF9BE8FF.toInt(), width = 1.6f)
        addFx(FxKind.ICE_CONE, player.x, player.y, angle = angle, radius = radius * 0.8f, life = 0.4f, color = 0xFFDFF9FF.toInt())
        addBurst(player.x, player.y - 14f, 14, 0xFFBFF4FF.toInt(), 260f, 0.5f)
        var hitAny = false
        var first = true
        iceNovaCasts++
        for (e in enemies) {
            if (!e.alive) continue
            val dx = e.x - player.x
            val dy = e.y - player.y
            val dist = hypot(dx, dy)
            if (dist > radius) continue
            hitAny = true
            val near = (1f - dist / radius).coerceIn(0f, 1f)
            // 가까울수록 강하게 밀려나고 피해도 크다.
            val push = 40f + near * 86f
            val a = if (dist < 1f) angle else atan2(dy, dx)
            e.x = (e.x + cos(a) * push).coerceIn(30f, W - 30f)
            e.y = (e.y + sin(a) * push * 0.7f).coerceIn(84f, H - 26f)
            hitEnemy(e, damage * (0.62 + near * 0.48), crit && first, a, 0.95f)
            first = false
        }
        if (!hitAny) hitEnemy(target, damage, crit, angle, 1f)
        shake = max(shake, 3.2f)
    }

    /**
     * 서리 폭탄 — poe2db.tw/kr/Frost_Bomb.
     *
     * 맥동하는 보주가 0.9초마다 주변 적에게 원소 노출(총 원소 저항 -20%)을 걸고,
     * 4초가 지나면 기폭하며 주변에 냉기 피해를 준다.
     */
    private fun frostBomb(target: Enemy, damage: Double) {
        if (frostBombs.size >= 8) frostBombs.removeAt(0)
        val x = (target.x + (rand.nextFloat() - 0.5f) * 18f).coerceIn(60f, W - 60f)
        val y = (target.y + (rand.nextFloat() - 0.5f) * 14f).coerceIn(100f, H - 40f)
        frostBombs.add(FrostBomb(x, y, FROST_BOMB_FUSE))
        addFx(FxKind.AURA, x, y - 18f, radius = 34f, life = 0.4f, color = 0xFFBFF4FF.toInt())
        addFx(FxKind.CRYSTAL, x, y, radius = 22f, life = 0.4f, color = 0xFFDFF9FF.toInt(), seed = rand.nextInt())
        addBurst(x, y - 16f, 8, 0xFFBFF4FF.toInt(), 150f, 0.4f)
        lastComboEvent = "서리 폭탄 · 4초 뒤 기폭"
    }

    /** 서리 보주가 살아 있는 동안 맥동하며 노출을 걸고, 수명이 다하면 기폭한다 */
    private fun updateFrostBombs(dt: Float) {
        var i = 0
        while (i < frostBombs.size) {
            val b = frostBombs[i]
            b.age += dt
            b.fuse -= dt
            b.pulse += dt
            b.tickTimer -= dt
            if (b.tickTimer <= 0f) {
                b.tickTimer = 0.9f
                // 맥동할 때마다 주변 적을 노출시킨다 (기름 젖음과 같은 총 원소 저항 -20%).
                for (e in enemies) {
                    if (!e.alive) continue
                    if (hypot(e.x - b.x, e.y - b.y) > 96f) continue
                    e.oilT = max(e.oilT, 2.2f)
                }
                addFx(FxKind.RING, b.x, b.y, radius = 96f, life = 0.4f, color = 0xFF9BE8FF.toInt(), width = 1.1f)
                addBurst(b.x, b.y - 14f, 5, 0xFFDFF9FF.toInt(), 120f, 0.3f)
            }
            if (b.fuse <= 0f) {
                val radius = aoe(82f)
                addFx(FxKind.FROST_NOVA, b.x, b.y, radius = radius, life = 0.55f, color = 0xFFBFF4FF.toInt())
                addFx(FxKind.ICE_CONE, b.x, b.y, angle = rand.nextFloat() * 6.28f, radius = radius, life = 0.5f, color = 0xFFDFF9FF.toInt())
                addFx(FxKind.CRYSTAL, b.x, b.y, radius = radius * 0.9f, life = 0.6f, color = 0xFFDFF9FF.toInt(), seed = rand.nextInt())
                addBurst(b.x, b.y, 18, 0xFFBFF4FF.toInt(), 280f, 0.6f)
                areaHit(b.x, b.y, radius, baseHitDamage() * 2.4, DamageElement.COLD, "서리 폭탄", true)
                frostBombBlasts++
                shake = max(shake, 4.0f)
                frostBombs.removeAt(i)
                continue
            }
            i++
        }
        val decoy = spiritDecoy
        if (decoy != null) {
            decoy.age += dt
            if (decoy.age >= decoy.life) spiritDecoy = null
        }
    }

    /**
     * 켈라리의 기만 — poe2db.tw/kr/Kelaris_Deception.
     *
     * 목표 지점에 주문 돌풍을 일으키고 3초짜리 분신을 남긴다. 분신이 살아 있는 동안
     * 다시 쓰면 분신이 폭발하며 더 큰 피해(폭발 반경 2.5미터)를 준다.
     */
    private fun spiritGust(target: Enemy, damage: Double, crit: Boolean, angle: Float) {
        val decoy = spiritDecoy
        if (decoy != null) {
            spiritDecoy = null
            val radius = aoe(105f)
            addFx(FxKind.IMPACT, decoy.x, decoy.y, radius = radius, life = 0.5f, color = 0xFFD8C3A0.toInt(), width = 1.6f)
            addFx(FxKind.SHOCKWAVE, decoy.x, decoy.y, radius = radius, life = 0.55f, color = 0xFFE8D9B8.toInt(), width = 1.6f)
            addFx(FxKind.CYCLONE, decoy.x, decoy.y - 16f, radius = radius, life = 0.45f, color = 0xFFF2E6C8.toInt(), width = 1.3f)
            addBurst(decoy.x, decoy.y - 16f, 20, 0xFFE8D9B8.toInt(), 300f, 0.7f)
            areaHit(decoy.x, decoy.y, radius, damage * 2.2, DamageElement.PHYSICAL, "분신 폭발", true)
            decoyBlasts++
            lastComboEvent = "분신 폭발"
            shake = max(shake, 4.6f)
            return
        }
        val x = target.x
        val y = target.y
        addFx(FxKind.CYCLONE, x, y - 18f, radius = aoe(64f), life = 0.35f, color = 0xFFF2E6C8.toInt(), width = 1.1f)
        addBurst(x, y - 18f, 12, 0xFFE8D9B8.toInt(), 240f, 0.5f)
        areaHit(x, y, aoe(64f), damage * 0.7, DamageElement.PHYSICAL, "켈라리의 돌풍")
        addFx(FxKind.AURA, x, y - 20f, radius = 26f, life = 0.5f, color = 0xFFD8C3A0.toInt())
        spiritDecoy = SpiritDecoy(x, y, 3f)
        lastComboEvent = "분신 소환 · 다시 쓰면 폭발"
    }

    /** 번개 화살 — 화살이 꿉으면 주변으로 연쇄 번개가 뻗는다 */
    private fun lightningArrow(target: Enemy, damage: Double, crit: Boolean, angle: Float) {
        val extra = totalExtraProjectiles().coerceIn(0, 4)
        for (i in 0..extra) {
            spawnProjectile(
                ProjKind.LIGHT_ARROW, target, damage / (extra + 1), crit, DamageElement.LIGHTNING,
                speed = 940f, angle = angle + (i - extra / 2f) * 0.09f,
            )
        }
        addFx(FxKind.SLASH, player.x + cos(angle) * 16f, player.y + sin(angle) * 16f - 22f, angle = angle, radius = 26f, life = 0.13f, color = 0xFFFFF3A8.toInt(), width = 0.6f)
        addBurst(player.x + cos(angle) * 20f, player.y + sin(angle) * 20f - 22f, 5, 0xFFFFE272.toInt(), 190f, 0.26f)
    }

    /** 얼음 화살 — 명중하면 부채골로 얼음 파편이 흩어진다 */
    private fun iceShot(target: Enemy, damage: Double, crit: Boolean, angle: Float) {
        spawnProjectile(ProjKind.ICE_ARROW, target, damage, crit, DamageElement.COLD, speed = 990f, angle = angle)
        addFx(FxKind.SLASH, player.x + cos(angle) * 16f, player.y + sin(angle) * 16f - 22f, angle = angle, radius = 24f, life = 0.13f, color = 0xFFBFF4FF.toInt(), width = 0.6f)
    }

    /** 혜성 — 하늘에서 얼음 덩어리가 떨어지고, 시전자는 뒤로 도약한다 */
    private fun cometStrike(target: Enemy, damage: Double) {
        val radius = aoe(118f)
        val fall = 0.55f
        addFx(FxKind.METEOR, target.x, target.y, radius = radius, life = fall, color = DamageElement.COLD.color, width = 1.5f)
        addFx(FxKind.GROUND_CRACK, target.x, target.y, radius = radius, life = fall, color = 0xFF7FD8FF.toInt())
        pendingStrikes.add(PendingStrike(target.x, target.y, radius, damage, DamageElement.COLD, "혜성", FxKind.METEOR, fall))
        val a = atan2(player.y - target.y, player.x - target.x)
        player.dashTimer = 0.22f
        player.dashVx = cos(a) * 230f
        player.dashVy = sin(a) * 230f
    }

    /** 화염구 — 충돌하면서 폭발해 불티 고리를 뿌린다 */
    private fun fireball(target: Enemy, damage: Double, crit: Boolean, angle: Float) {
        addProjectile(
            Projectile(
                x = player.x + cos(angle) * 18f, y = player.y + sin(angle) * 18f - 22f,
                dirX = cos(angle), dirY = sin(angle), speed = 620f,
                damage = damage, crit = crit, target = target, hostile = false,
                element = DamageElement.FIRE, kind = ProjKind.FIRE_ORB, life = 2.2f,
            )
        )
        addFx(FxKind.AURA, player.x + cos(angle) * 20f, player.y + sin(angle) * 20f - 22f, radius = 24f, life = 0.3f, color = 0xFFFF8A3D.toInt())
    }

    /** 폭발 유탄 — 통통 튀어 날아간 뒤 퓨즈(3초)가 타면 터진다. poe2db: 투사체 +1개 발사 */
    private fun grenade(target: Enemy, damage: Double, angle: Float) {
        // 대상 지점까지 굴러가도록 거리에 맞춰 던진다
        val dist = hypot(target.x - player.x, target.y - player.y)
        val shots = 2
        repeat(shots) { i ->
            val a = angle + (i - (shots - 1) / 2f) * 0.12f
            val speed = (dist * 2.3f).coerceIn(180f, 760f)
            addProjectile(
                Projectile(
                    x = player.x + cos(a) * 16f, y = player.y + sin(a) * 16f - 24f,
                    dirX = cos(a), dirY = sin(a), speed = speed,
                    damage = damage / shots, crit = false, target = target, hostile = false,
                    element = DamageElement.FIRE, kind = ProjKind.GRENADE,
                    life = 4.2f, fuse = 3.0f, seed = rand.nextInt(),
                )
            )
        }
        addBurst(player.x + cos(angle) * 18f, player.y + sin(angle) * 18f - 24f, 4, 0xFFFFC24D.toInt(), 140f, 0.24f)
    }

    /** 살상 장법 — 적에게 질주해 마무리 타격하고 권능 충전을 챙긴다 */
    private fun killingPalm(target: Enemy, damage: Double, crit: Boolean) {
        val fromX = player.x
        val fromY = player.y
        val a = atan2(target.y - fromY, target.x - fromX)
        player.x = (target.x - cos(a) * 34f).coerceIn(44f, W - 44f)
        player.y = (target.y - sin(a) * 34f).coerceIn(84f, H - 34f)
        player.facing = a
        addFx(FxKind.STREAK, fromX, fromY - 24f, x2 = player.x, y2 = player.y - 24f, life = 0.14f, color = 0xFFCFE8FF.toInt(), width = 1.3f)
        addFx(FxKind.AFTERIMAGE, fromX, fromY, angle = a, life = 0.2f, color = 0xFF9AD8FF.toInt(), width = 0.8f)
        addFx(FxKind.IMPACT, target.x, target.y, radius = 44f, life = 0.32f, color = 0xFFDCE8FF.toInt(), width = 1.3f)
        if (target.hp <= target.maxHp * 0.18) {
            hitEnemy(target, target.hp.coerceAtLeast(1.0), crit, a, 1.3f)
            gainPowerCharge()
            addPopup(target.x, target.y - 82f * target.scale, "마무리 타격 · 권능", true, 0.9f, 0xFFFFE272.toInt(), 0.9f)
        } else {
            hitEnemy(target, damage, crit, a, 1f)
        }
        spawnPhantoms(target)
    }

    /** 회오리 사격 — 화살이 떨어진 자리에 회오리가 선다 */
    private fun tornadoShot(target: Enemy, damage: Double, crit: Boolean, angle: Float) {
        spawnProjectile(ProjKind.ARROW, target, damage * 0.4, crit, DamageElement.PHYSICAL, speed = 760f, angle = angle)
        twisters.add(
            Twister(
                x = target.x, y = target.y,
                dirX = cos(angle), dirY = sin(angle),
                damage = damage * 0.6, life = 2.0f, amplified = false,
                element = DamageElement.PHYSICAL,
                // poe2db: 회오리 사격의 충격 반경 1미터 (1m ≈ 42px)
                radiusOverride = 42f,
            )
        )
        twisters.last().angle = angle
        twisterCasts++
        addFx(FxKind.CYCLONE, target.x, target.y - 14f, radius = 74f, life = 0.36f, color = 0xFFE8E0CC.toInt(), width = 1.2f)
    }

    /** 저주·징표 — 몸에서 저주가 퍼져 나간다 */
    private fun curseCast(target: Enemy, damage: Double, angle: Float) {
        val radius = aoe(118f)
        addFx(FxKind.SIGIL, target.x, target.y, angle = angle, radius = radius, life = 0.8f, color = 0xFFC182FF.toInt(), seed = rand.nextInt())
        addFx(FxKind.AURA, target.x, target.y, radius = radius, life = 0.85f, color = 0xFFC182FF.toInt())
        addFx(FxKind.RING, target.x, target.y, radius = radius * 0.8f, life = 0.6f, color = 0xFF9B5DE5.toInt(), width = 1.2f)
        addFx(FxKind.RING, target.x, target.y, radius = radius * 0.45f, life = 0.5f, color = 0xFFD9B8FF.toInt(), width = 0.9f)
        addBurst(target.x, target.y - 16f, 12, 0xFFC182FF.toInt(), 160f, 0.8f, gravity = -60f)
        areaHit(target.x, target.y, radius, damage, DamageElement.CHAOS, "저주", ratio = 0.8)
        if (stats.motion == SkillMotion.CURSE) {
            addFx(FxKind.SIGIL, player.x, player.y - 20f, radius = 46f, life = 0.45f, color = 0xFFC182FF.toInt(), seed = rand.nextInt())
            addFx(FxKind.AURA, player.x, player.y - 20f, radius = 40f, life = 0.4f, color = 0xFFC182FF.toInt())
        }
    }

    // ------------------------------------------------------------ 기름 포화·기름 지대·변신

    /**
     * 기름 포화.
     *
     * 와이번으로 변신해 기름을 사방으로 퍼붓는다. 화면을 반 원이 아니라 360도로 덮도록
     * 기름 덩이가 고리 모양으로 날아가고, 떨어진 자리마다 기름 웅덩이가 생겨 적을 20%
     * 늦추고 노출(총 원소 저항 -20%)시킨다. 웅덩이는 옆 웅덩이로 불이 옮겨 붙으며
     * 연쇄 폭발한다.
     *
     * 권능 충전이 있으면 하나도 남기지 않고 태워, 전기를 띤 기름 고리를 더 쏘고 태운
     * 자리마다 전격이 사방으로 터진다. 그래서 권능 수급이 이어지면 기름 포화가
     * 끊기지 않고 화면 전체를 계속 덮는다. (poe2db.tw/kr/Oil_Barrage)
     */
    private fun oilBarrage(target: Enemy, damage: Double) {
        val aim = atan2(target.y - player.y, target.x - player.x)
        // 권능 충전을 모두 태운다 — 집중 유지와 전격 폭발의 연료.
        val spent = spendCharges(ChargeKind.POWER)
        val outer = (10 + totalExtraProjectiles() + spent * 2).coerceIn(8, 20)
        val charged = if (spent > 0) (6 + spent * 3).coerceIn(6, 16) else 0
        val shots = outer + charged
        // 발수만 늘고 총 피해는 부풀지 않게 이번 시전 피해를 발수로 나눈다.
        val budget = damage * (1.0 + 0.35 * spent)
        val perHit = (budget / (shots + spent * 2).coerceAtLeast(1)).coerceAtLeast(1.0)

        // 바깥 고리 — 360도로 고르게 퍼진다.
        repeat(outer) { i ->
            val a = aim + i * (PI_F * 2f / outer) + (rand.nextFloat() - 0.5f) * 0.07f
            addProjectile(
                Projectile(
                    x = player.x + cos(a) * 24f,
                    y = player.y + sin(a) * 24f - 30f,
                    dirX = cos(a), dirY = sin(a),
                    speed = 470f + rand.nextFloat() * 150f,
                    damage = perHit, crit = false,
                    target = enemyInCone(a, 0.7f, 470f),
                    hostile = false,
                    element = if (spent > 0 && i % 3 == 0) DamageElement.LIGHTNING else DamageElement.FIRE,
                    kind = ProjKind.OIL,
                    life = 1.4f + rand.nextFloat() * 0.6f,
                    oilRadius = aoe(54f),
                )
            )
        }
        // 전기를 띤 기름 — 보랏빛·연둣빛 화염이 겹으로 퍼진다.
        repeat(charged) { i ->
            val a = aim + 0.35f + i * (PI_F * 2f / charged) + (rand.nextFloat() - 0.5f) * 0.1f
            addProjectile(
                Projectile(
                    x = player.x + cos(a) * 20f,
                    y = player.y + sin(a) * 20f - 26f,
                    dirX = cos(a), dirY = sin(a),
                    speed = 560f + rand.nextFloat() * 170f,
                    damage = perHit * 0.8, crit = false,
                    target = enemyInCone(a, 0.8f, 520f),
                    hostile = false,
                    element = DamageElement.LIGHTNING,
                    kind = ProjKind.CHARGED_OIL,
                    life = 1.3f + rand.nextFloat() * 0.6f,
                    wobble = 0.42f,
                    oilRadius = aoe(46f),
                )
            )
        }
        barrageShots += shots
        chargedOilShots += charged
        player.channel = 1.4f

        // 숨을 내뿜는 자리 — 기름 안개가 크게 퍼진다.
        addFx(
            FxKind.OIL_SPLASH, player.x + cos(aim) * 26f, player.y - 34f,
            angle = aim, radius = 46f, life = 0.4f, color = 0xFF8C7433.toInt(),
        )
        addFx(FxKind.RING, player.x, player.y - 6f, radius = 90f, life = 0.4f, color = 0xFFC7A64B.toInt(), width = 1.4f)
        addBurst(player.x + cos(aim) * 28f, player.y - 32f, 14, 0xFFC7A64B.toInt(), 220f, 0.5f)

        // 태운 권능 충전마다 사방으로 전격이 터진다.
        repeat(spent) { k ->
            val radius = aoe((168f - k * 26f).coerceAtLeast(80f))
            addFx(FxKind.SHOCKWAVE, player.x, player.y, radius = radius, life = 0.5f, color = 0xFFB07AFF.toInt(), width = 1.9f)
            addFx(FxKind.RING, player.x, player.y, radius = radius * 0.7f, life = 0.45f, color = 0xFF6FE08C.toInt(), width = 1.4f)
            addFx(FxKind.ARC_WEB, player.x, player.y - 20f, radius = radius, life = 0.3f, color = 0xFFFFE272.toInt())
            addFx(FxKind.OIL_FLAME, player.x, player.y - 8f, radius = radius * 0.8f, life = 0.5f, color = 0xFFB07AFF.toInt())
            addBurst(player.x, player.y - 24f, 14, 0xFFFFF3A8.toInt(), 280f, 0.6f, gravity = -90f)
            areaHit(player.x, player.y, radius, budget * 0.16, DamageElement.LIGHTNING, "전격 기름 폭발", ratio = 0.55)
            // 폭발에서 튄 전기가 가까운 적으로 이어진다.
            nearbyEnemiesByPoint(player.x, player.y, radius * 1.2f, 4).forEach { other ->
                addFx(
                    FxKind.BOLT, player.x, player.y - 22f, x2 = other.x, y2 = other.y - 20f,
                    life = 0.2f, color = 0xFFFFF3A8.toInt(), seed = rand.nextInt(), width = 1.1f,
                )
            }
            shake = max(shake, 6f)
        }
        if (spent > 0) {
            addPopup(player.x, player.y - 106f, "권능 ${spent}개 태움 · 기름 폭격", true, 0.85f, 0xFFFFE272.toInt(), 0.95f)
        } else {
            addPopup(player.x, player.y - 98f, "기름 포화", false, 0.6f, 0xFFCFEFFF.toInt(), 0.8f)
        }
    }

    /** 한 점에서 가장 가까운 적 몇 (전격이 이어지는 대상) */
    private fun nearbyEnemiesByPoint(x: Float, y: Float, range: Float, limit: Int): List<Enemy> =
        enemies.filter { it.alive && hypot(it.x - x, it.y - y) <= range }
            .sortedBy { hypot(it.x - x, it.y - y) }
            .take(limit)

    /** 그 방향 부채꼴 안에서 가장 가까운 적 — 360도 탄막이 허공으로만 날아가지 않게 한다 */
    private fun enemyInCone(angle: Float, halfWidth: Float, range: Float): Enemy? {
        var best: Enemy? = null
        var bestD = range * range
        for (e in enemies) {
            if (!e.alive) continue
            val dx = e.x - player.x
            val dy = e.y - player.y
            val d2 = dx * dx + dy * dy
            if (d2 > bestD) continue
            if (abs(normalize(atan2(dy, dx) - angle)) > halfWidth) continue
            best = e
            bestD = d2
        }
        return best
    }

    /** 기름 덩이가 떨어진 자리 — 웅덩이를 남기고 옆 기름으로 불이 옮겨 붙는다 */
    private fun oilImpact(x: Float, y: Float, radius: Float, damage: Double, charged: Boolean = false) {
        spillOil(x, y, radius)
        val pool = oils.lastOrNull()
        val flame = if (charged) 0xFFB07AFF.toInt() else 0xFFFF8A3D.toInt()
        addFx(FxKind.OIL_FLAME, x, y - 4f, radius = radius * 1.15f, life = 0.42f, color = flame)
        addFx(FxKind.RING, x, y, radius = radius, life = 0.34f, color = 0xFFFF8A3D.toInt(), width = 1.2f)
        addBurst(x, y - 6f, 7, 0xFFC7A64B.toInt(), 190f, 0.45f)
        // 연쇄 폭발 — 떨어진 웅덩이 말고 옆 웅덩이로 불과 전기가 옮겨 붙는다.
        var chained = 0
        for (other in oils) {
            if (other === pool || other.ignited) continue
            if (hypot(other.x - x, other.y - y) > 170f) continue
            other.ignited = true
            other.burning = true
            other.burnLife = 2.8f
            other.burnDps = max(other.burnDps, damage * 0.22)
            chained++
            addFx(
                FxKind.BOLT, x, y - 6f, x2 = other.x, y2 = other.y - 6f,
                life = 0.2f, color = 0xFFFFE272.toInt(), seed = rand.nextInt(), width = 1.0f,
            )
            addFx(FxKind.OIL_FLAME, other.x, other.y, radius = other.radius, life = 0.5f, color = flame)
        }
        if (charged) {
            // 전기를 띤 기름은 연둣빛 불티와 검은 연기를 남긴다.
            addFx(FxKind.CLOUD, x, y - 6f, radius = radius * 1.2f, life = 0.6f, color = 0xFF2B2620.toInt())
            addBurst(x, y - 10f, 6, 0xFF6FE08C.toInt(), 160f, 0.5f, gravity = -90f)
        }
        // 떨어진 웅덩이 자체에도 불이 붙는다.
        igniteOil(x, y, radius * 0.5f, damage)
        if (chained > 0) {
            oilChainIgnitions += chained
            addBurst(x, y - 8f, 6 + chained * 2, 0xFF6FE08C.toInt(), 210f, 0.5f, gravity = -70f)
        }
    }

    /** 기름 유탄 — 아래로 굴려 터뜨려 기름 지대를 만든다 */
    private fun oilGrenade(target: Enemy, damage: Double, angle: Float) {
        val dist = hypot(target.x - player.x, target.y - player.y)
        val speed = (dist * 2.1f).coerceIn(190f, 720f)
        addProjectile(
            Projectile(
                x = player.x + cos(angle) * 18f, y = player.y + sin(angle) * 18f - 26f,
                dirX = cos(angle), dirY = sin(angle), speed = speed,
                damage = damage * 0.6, crit = false, target = target, hostile = false,
                element = DamageElement.FIRE, kind = ProjKind.GRENADE, life = 3.4f,
                fuse = 1.6f, oilRadius = aoe(76f), seed = rand.nextInt(),
            )
        )
        addFx(
            FxKind.OIL_SPLASH, player.x + cos(angle) * 20f, player.y + sin(angle) * 16f - 24f,
            radius = 16f, life = 0.26f, color = 0xFFB99A3D.toInt(),
        )
        addBurst(player.x + cos(angle) * 22f, player.y - 26f, 5, 0xFFC7A64B.toInt(), 150f, 0.3f)
    }

    /** 연발 사격 — 시위를 빠르게 당겼다 놓으며 화살을 몰아 쏜다 */
    private fun barrageVolley(target: Enemy, damage: Double, crit: Boolean, angle: Float) {
        val extra = totalExtraProjectiles().coerceIn(0, 4)
        val shots = 3
        val per = damage / (shots + extra)
        repeat(shots) { i ->
            val a = angle + (i - 0.5f) * 0.10f
            spawnProjectile(
                ProjKind.ARROW, target, per, crit && i == 0,
                stats.fx.element, speed = 900f + i * 180f, angle = a,
            )
        }
        repeat(extra) { i ->
            spawnProjectile(
                ProjKind.ARROW, target, per, false, stats.fx.element,
                speed = 1_050f, angle = angle + (i - (extra - 1) / 2f) * 0.13f,
            )
        }
        barrageShots += shots + extra
        // 당겼다 놓은 자리마다 남는 시위 자국
        addFx(
            FxKind.SLASH, player.x + cos(angle) * 18f, player.y + sin(angle) * 18f - 24f,
            angle = angle, radius = 30f, life = 0.12f, color = 0xFFF2E6C8.toInt(), width = 0.7f,
        )
        addBurst(player.x + cos(angle) * 22f, player.y - 26f, 4, 0xFFE8D9B0.toInt(), 150f, 0.24f)
    }

    /** 주문 토템 — 땅에 세워 두고 토템이 주문을 대신 쏜다 */
    private fun placeTotem(target: Enemy, damage: Double, angle: Float) {
        if (totems.size >= 2) totems.removeAt(0)
        val tx = (player.x - cos(angle) * 24f).coerceIn(40f, W - 40f)
        val ty = (player.y + 4f).coerceIn(84f, H - 26f)
        totems.add(Totem(tx, ty, 8f))
        addFx(FxKind.TOTEM, tx, ty, radius = 26f, life = 0.55f, color = stats.fx.element.color)
        addFx(FxKind.PILLAR, tx, ty, radius = 16f, life = 0.35f, color = stats.fx.element.color, width = 0.7f)
        addBurst(tx, ty - 10f, 8, stats.fx.element.color, 150f, 0.5f, gravity = -60f)
        addPopup(tx, ty - 64f, "주문 토템", false, 0.7f, 0xFFBFE4FF.toInt(), 0.78f)
    }

    /** 토템이 살아 있는 동안 주변 적에게 주문을 쏜다 */
    private fun updateTotems(dt: Float) {
        var i = 0
        while (i < totems.size) {
            val t = totems[i]
            t.age += dt
            t.life -= dt
            t.pulse += dt
            t.cast -= dt
            if (t.life <= 0f) {
                addBurst(t.x, t.y - 12f, 6, 0xFFBFD8FF.toInt(), 110f, 0.4f)
                totems.removeAt(i)
                continue
            }
            if (t.cast <= 0f) {
                t.cast = 0.55f
                val victim = enemies.filter { it.alive && hypot(it.x - t.x, it.y - t.y) < 320f }
                    .minByOrNull { hypot(it.x - t.x, it.y - t.y) }
                if (victim != null) {
                    t.shots++
                    totemCasts++
                    addFx(
                        FxKind.BOLT, t.x, t.y - 30f, x2 = victim.x, y2 = victim.y - 20f,
                        life = 0.18f, color = stats.fx.element.color, seed = rand.nextInt(), width = 1.1f,
                    )
                    underSkill(SkillMotion.TOTEM.label) {
                        hitEnemy(victim, baseHitDamage() * 0.34, false, atan2(victim.y - t.y, victim.x - t.x))
                    }
                    addBurst(victim.x, victim.y - 18f, 4, stats.fx.element.color, 150f, 0.3f)
                }
            }
            i++
        }
    }

    /** 야수 내리치기 — 앞발을 크게 들어 내려찍고 바닥을 찢는다 */
    private fun beastMaul(target: Enemy, damage: Double, crit: Boolean, angle: Float) {
        val reach = aoe(96f)
        addFx(FxKind.SLASH, player.x + cos(angle) * 22f, player.y - 18f, angle = angle, radius = reach, life = 0.22f, color = stats.fx.element.color, width = 1.8f)
        addFx(FxKind.IMPACT, target.x, target.y, radius = aoe(62f), life = 0.4f, color = 0xFFD8C3A0.toInt(), width = 1.4f)
        addFx(FxKind.DUST, target.x, target.y, radius = aoe(70f), life = 0.5f, color = 0xFFB9AFA6.toInt())
        addBurst(target.x, target.y - 8f, 10, 0xFFC9B38A.toInt(), 230f, 0.55f, gravity = 120f)
        val victims = enemies.filter { e ->
            e.alive && hypot(e.x - player.x, e.y - player.y) < reach + 10f &&
                abs(normalize(atan2(e.y - player.y, e.x - player.x) - angle)) < 1.4f
        }.take(4)
        if (victims.isEmpty()) hitEnemy(target, damage, crit, angle) else victims.forEachIndexed { i, e ->
            hitEnemy(e, if (i == 0) damage else damage * 0.6, crit && i == 0, angle)
        }
        shake = max(shake, 4.2f)
        strikeBells(player.x, player.y, reach, damage)
    }

    /** 야수 함성 — 고개를 젖혀 포효해 주변을 밀어내고 겁을 준다 */
    private fun beastHowl(target: Enemy, damage: Double) {
        val radius = aoe(150f)
        addFx(FxKind.SHOCKWAVE, player.x, player.y, radius = radius, life = 0.55f, color = stats.fx.element.color, width = 1.8f)
        addFx(FxKind.RING, player.x, player.y - 16f, radius = radius * 0.7f, life = 0.4f, color = 0xFFFFF3D6.toInt())
        addBurst(player.x, player.y - 34f, 16, stats.fx.element.color, 280f, 0.7f)
        enemies.filter { it.alive && hypot(it.x - player.x, it.y - player.y) < radius }.forEach { e ->
            e.slowT = max(e.slowT, 1.4f)
            hitEnemy(e, damage * 0.7, false, atan2(e.y - player.y, e.x - player.x), 0.9f)
        }
        shake = max(shake, 5f)
    }

    /** 와이번 숨결 — 목을 내밀어 화염·번개가 섞인 숨결을 부채꼴로 토한다 */
    private fun wyvernBreath(target: Enemy, damage: Double, crit: Boolean, angle: Float) {
        val shots = 4
        repeat(shots) { i ->
            val a = angle + (i - (shots - 1) / 2f) * 0.14f
            addProjectile(
                Projectile(
                    x = player.x + cos(a) * 26f,
                    y = player.y + sin(a) * 26f - 34f,
                    dirX = cos(a), dirY = sin(a), speed = 620f + i * 30f,
                    damage = damage / shots, crit = crit && i == 0, target = target,
                    hostile = false, element = stats.fx.element, kind = ProjKind.FIRE_ORB,
                    life = 1.8f,
                )
            )
        }
        val mouthX = player.x + cos(angle) * 30f
        val mouthY = player.y - 34f
        addFx(FxKind.OIL_FLAME, mouthX, mouthY, angle = angle, radius = 46f, life = 0.34f, color = stats.fx.element.color)
        addBurst(mouthX, mouthY, 10, stats.fx.element.color, 200f, 0.4f)
        igniteOil(target.x, target.y, 90f)
    }

    /** 기름 지대를 만든다 (sticky = 기름 유탄의 끈적한 기름) */
    private fun spillOil(x: Float, y: Float, radius: Float, sticky: Boolean = false) {
        if (oils.size >= 8) oils.removeAt(0)
        oils.add(OilGround(x, y, radius, 6f).also { it.sticky = sticky })
        oilSpills++
        addFx(FxKind.OIL_SPLASH, x, y, radius = radius, life = 0.55f, color = 0xFF8C7433.toInt())
        addBurst(x, y, 7, 0xFFB99A3D.toInt(), 130f, 0.45f)
    }

    /** 기름에 불을 붙인다 — 한 번 붙으면 지속 시간 동안 타오른다 */
    private fun igniteOil(x: Float, y: Float, radius: Float, damage: Double = 0.0) {
        for (o in oils) {
            if (hypot(o.x - x, o.y - y) > radius + o.radius) continue
            if (o.ignited) continue
            o.ignited = true
            o.burning = true
            o.burnLife = 3.2f
            o.burnDps = max(o.burnDps, damage * 0.25)
            oilIgnitions++
            addFx(FxKind.OIL_FLAME, o.x, o.y, radius = o.radius, life = 0.6f, color = 0xFFFF7B3C.toInt())
            addFx(FxKind.RING, o.x, o.y, radius = o.radius * 1.1f, life = 0.5f, color = 0xFFFFB05A.toInt(), width = 1.4f)
            addBurst(o.x, o.y, 14, 0xFFFF8A3D.toInt(), 220f, 0.6f, gravity = -90f)
            shake = max(shake, 3.6f)
        }
    }

    /** 기름 지대 유지 — 밟은 적을 늦추고 노출시키며, 불붙은 기름은 계속 태운다 */
    private fun updateOils(dt: Float) {
        var i = 0
        while (i < oils.size) {
            val o = oils[i]
            o.age += dt
            o.life -= dt
            o.pulse += dt
            if (o.burning) {
                o.burnLife -= dt
                if (o.burnLife <= 0f) o.burning = false
            }
            for (e in enemies) {
                if (!e.alive) continue
                if (hypot(e.x - o.x, e.y - o.y) > o.radius) continue
                e.oilT = max(e.oilT, 1.4f)
                if (o.burning) {
                    e.igniteT = max(e.igniteT, 0.8f)
                    e.igniteDps = max(e.igniteDps, baseHitDamage() * 0.08)
                } else {
                    // 기름 지대는 이동 속도 20% 감속 (poe2db). 기름 유탄이 만든 지대는 40%.
                    slowEnemy(e, 0.4f, if (o.sticky) 0.6f else 0.8f)
                }
            }
            if (o.burning) {
                // 매 프레임 피해를 주면 피해 숫자가 화면을 덮으므로 0.35초마다 나누어 준다.
                o.tickTimer -= dt
                if (o.tickTimer <= 0f) {
                    o.tickTimer = 0.35f
                    val tick = baseHitDamage() * 0.05 * 0.35
                    areaHit(o.x, o.y, o.radius, tick.coerceAtLeast(0.0), DamageElement.FIRE, "기름 화염", ratio = 1.0)
                    oilDamage += tick
                }
            }
            if (o.life <= 0f) {
                oils.removeAt(i)
                continue
            }
            i++
        }
    }

    /** 기름 위에 점화된 적이 있으면 기름이 옮겨 붙는다 */
    private fun checkOilIgnition() {
        if (oils.isEmpty()) return
        for (e in enemies) {
            if (!e.alive || e.igniteT <= 0f) continue
            if (oils.any { !it.ignited && hypot(e.x - it.x, e.y - it.y) < it.radius }) {
                igniteOil(e.x, e.y, 10f)
            }
        }
    }

    /** 시험용 — 이름이 붙은 공격으로 플레이어를 때린다 (사망 원인 기록 확인) */
    internal fun strikePlayer(raw: Double, element: DamageElement, source: String, skill: String) =
        hitPlayer(raw, element, false, player.x, player.y, heavy = true, source = source, skill = skill)

    /** 시험용 — 기름 지대를 직접 만들고 불을 붙여 본다 */
    internal fun spillOilAt(x: Float, y: Float, radius: Float) = spillOil(x, y, radius)

    /** 시험용 — 기름이 떨어진 자리의 연쇄 폭발을 직접 일으켜 본다 */
    internal fun testOilImpact(x: Float, y: Float, radius: Float, damage: Double) =
        oilImpact(x, y, radius, damage)

    internal fun igniteOilAt(x: Float, y: Float, radius: Float) = igniteOil(x, y, radius, 1.0)

    /** 시험용 — 전장의 적 하나 (첫 적부터) */
    internal fun testEnemy(index: Int = 0): Enemy? = enemies.getOrNull(index)

    /** 시험용 — 적을 원하는 상태로 처치해 전령·산산조각 조건을 확인한다 */
    internal fun testKill(
        e: Enemy,
        frozen: Boolean = false,
        shocked: Boolean = false,
        overkill: Double = 0.0,
    ) {
        if (frozen) e.freezeT = max(e.freezeT, 1.0f)
        if (shocked) e.shockT = max(e.shockT, 1.0f)
        e.overkill = overkill
        killEnemy(e)
    }

    /** 시험용 — 한 방의 실제 피해 (감전 배율·초과 피해 확인) */
    internal fun testHit(e: Enemy, damage: Double): Double {
        val before = damageDealt
        hitEnemy(e, damage, false, 0f)
        return damageDealt - before
    }

    /** 스킬별 기록과 전체 피해가 같은지 (원소 상태이상·전령 피해가 빠짐없이 적립됐는지) */
    internal fun damageLedgerBalanced(tolerance: Double = 1e-6): Boolean {
        val sum = skillRows.values.sumOf { it.damage }
        return abs(sum - damageDealt) <= tolerance * max(1.0, damageDealt)
    }

    /** 지금 들고 있는 충전 수 */
    fun chargeCount(kind: ChargeKind): Int = when (kind) {
        ChargeKind.POWER -> powerCharges
        ChargeKind.ENDURANCE -> enduranceCharges
        ChargeKind.FRENZY -> frenzyCharges
    }

    private fun setChargeCount(kind: ChargeKind, value: Int) {
        when (kind) {
            ChargeKind.POWER -> powerCharges = value
            ChargeKind.ENDURANCE -> enduranceCharges = value
            ChargeKind.FRENZY -> frenzyCharges = value
        }
    }

    /**
     * 충전 하나를 얻는다.
     *
     * 공명이 있으면 얻는 종류가 한 칸씩 바뀌고(권능→인내→격분), 이미 가득이면 아무 일도
     * 일어나지 않는다. 실제로 얻은 충전 종류를 돌려준다.
     */
    fun gainCharge(kind: ChargeKind): ChargeKind? {
        val actual = chargeRules.resolve(kind)
        val now = chargeCount(actual)
        if (now >= chargeRules.cap(actual)) return null
        setChargeCount(actual, now + 1)
        if (actual == ChargeKind.FRENZY) frenzyGained++
        addPopup(
            player.x + (rand.nextFloat() - 0.5f) * 16f, player.y - 108f,
            "${actual.label} 충전 ${now + 1}", false, 0.6f, actual.color, 0.82f,
        )
        addFx(FxKind.RING, player.x, player.y - 22f, radius = 30f, life = 0.35f, color = actual.color, width = 1.1f)
        return actual
    }

    /** 충전 하나를 태운다 */
    fun spendCharge(kind: ChargeKind): Boolean {
        val now = chargeCount(kind)
        if (now <= 0) return false
        setChargeCount(kind, now - 1)
        chargesSpent++
        return true
    }

    /** 들고 있는 충전을 모두 태우고 그 수를 돌려준다 */
    fun spendCharges(kind: ChargeKind): Int {
        val now = chargeCount(kind)
        if (now <= 0) return 0
        setChargeCount(kind, 0)
        chargesSpent += now
        return now
    }

    /** 전장 HUD에 적는 충전 줄 — 권능(파랑)·인내(빨강)·격분(초록) */
    fun chargesHud(): String = "권능 $powerCharges · 인내 $enduranceCharges · 격분 $frenzyCharges"

    /** 권능 충전 — 상을 더 만들거나 기름 포화에 쓴다 */
    private fun gainPowerCharge() {
        gainCharge(ChargeKind.POWER)
    }

    /**
     * 빙결·감전을 걸었을 때 — 전투 격분이 있으면 쿨타임마다 격분 충전을 얻는다.
     *
     * poe2db의 발동 조건은 빙결·전기 충격·속박이다. 전투 모형에는 전기 충격·속박이
     * 없어서 감전을 그 자리에 세운다.
     */
    private fun onControlApplied(target: Enemy) {
        val rules = chargeRules
        if (!rules.combatFrenzy || frenzyTimer > 0f) return
        val gained = gainCharge(ChargeKind.FRENZY) ?: return
        frenzyTimer = rules.frenzyCooldown
        addPopup(target.x, target.y - 88f * target.scale, "전투 격분 · ${gained.label}", false, 0.7f, gained.color, 0.8f)
        addFx(FxKind.AURA, player.x, player.y - 24f, radius = 38f, life = 0.35f, color = gained.color)
    }

    /** 충전 시계 — 전투 격분 쿨타임과 충전 조절의 소모 주기를 돌린다 */
    private fun updateCharges(dt: Float) {
        if (frenzyTimer > 0f) frenzyTimer = max(0f, frenzyTimer - dt)
        if (!chargeRules.regulation) return
        regulationTimer -= dt
        if (regulationTimer > 0f) return
        regulationTimer = chargeRules.regulationInterval
        var burned = 0
        for (kind in ChargeKind.values()) if (spendCharge(kind)) burned++
        if (burned > 0) {
            addPopup(
                player.x, player.y - 92f, "충전 조절 · 충전 ${burned}개 소모",
                false, 0.72f, 0xFFCFE8FF.toInt(), 0.8f,
            )
            addFx(FxKind.AURA, player.x, player.y - 20f, radius = 44f, life = 0.4f, color = 0xFFCFE8FF.toInt())
        }
    }

    /** 권능 충전을 직접 채운다 (충전이 모자란 상황을 시험할 때) */
    internal fun grantPowerCharges(count: Int) {
        repeat(count) { gainPowerCharge() }
    }

    /** 한 지점을 중심으로 반경 안의 적을 벤다 */
    private fun areaHit(
        x: Float,
        y: Float,
        radius: Float,
        damage: Double,
        element: DamageElement,
        label: String,
        crit: Boolean = false,
        ratio: Double = 0.65,
    ): Int {
        var hits = 0
        for (e in enemies) {
            if (!e.alive) continue
            if (hypot(e.x - x, e.y - y) > radius) continue
            val taken = applyShield(e, if (hits == 0) damage else damage * ratio)
            e.hp -= taken
            e.hitFlash = 1f
            damageDealt += taken
            creditDamage(skillContext.ifBlank { label }, taken)
            hits++
            addPopup(
                e.x + (rand.nextFloat() - 0.5f) * 18f,
                e.y - 52f * e.scale,
                formatShort(taken), crit, 0.6f, element.color, 0.9f,
            )
            if (e.hp <= 0) killEnemy(e)
        }
        if (hits > 0) {
            leechPool += damage * leechRate()
            addPopup(x, y - 66f, label, false, 0.7f, 0xFFCFEFFF.toInt(), 0.75f)
        }
        return hits
    }

    /** 공허의 형상 — 근접기를 쓸 때마다 반투명 상을 내보낸다 */
    private fun spawnPhantoms(target: Enemy) {
        if (!stats.hollowForm || phantomCooldown > 0f) return
        phantomCooldown = 0.34f
        val count = if (powerCharges > 0) {
            powerCharges--
            3
        } else 1
        // 상은 공허의 형상에 넣은 근접기를 그대로 따라 쓴다.
        val motion = stats.hollowSkill?.motion
            ?: if (stats.motion == SkillMotion.DEFAULT) SkillMotion.WHIRLING_ASSAULT else stats.motion
        val perHit = stats.hollowSkill?.dps?.takeIf { it > 0.0 }?.div(effectiveAps()) ?: baseHitDamage()
        val damage = perHit * 0.8
        repeat(count) {
            val a = rand.nextFloat() * 6.283f
            var x = target.x + cos(a) * (26f + rand.nextFloat() * 34f)
            var y = target.y + sin(a) * (20f + rand.nextFloat() * 24f)
            // 상은 플레이어 주위 6미터 안에만 생긴다
            val dx = x - player.x
            val dy = y - player.y
            val d = hypot(dx, dy).coerceAtLeast(0.001f)
            if (d > 84f) {
                x = player.x + dx / d * 84f
                y = player.y + dy / d * 84f
            }
            x = x.coerceIn(40f, W - 40f)
            y = y.coerceIn(80f, H - 30f)
            phantoms.add(
                Phantom(                    x = x, y = y,
                    facing = atan2(target.y - y, target.x - x),
                    damage = damage,
                    life = 0.66f,
                    motion = motion,
                    weapon = stats.weapon,
                    form = stats.form,
                )
            )
            addFx(FxKind.AURA, x, y - 12f, radius = 34f, life = 0.34f, color = 0xFFB07AFF.toInt())
            addBurst(x, y - 20f, 6, 0xFFB07AFF.toInt(), 120f, 0.4f, gravity = -90f)
            phantomSpawns++
        }
        if (count > 1) addPopup(player.x, player.y - 96f, "공허의 상 x$count", false, 0.8f, 0xFFC9A6FF.toInt(), 0.9f)
    }

    /** 상은 잠깐 떠서 장착 스킬을 한 번 휘두르고 사라진다 */
    private fun updatePhantoms(dt: Float) {
        var i = 0
        while (i < phantoms.size) {
            val p = phantoms[i]
            p.age += dt
            p.spin += dt * 15f
            p.x += cos(p.facing) * p.drift * dt
            p.y += sin(p.facing) * p.drift * 0.7f * dt
            p.tickTimer -= dt
            if (p.ticks > 0 && p.tickTimer <= 0f) {
                p.ticks--
                p.tickTimer = if (p.ticks > 0) 0.15f else 0.2f
                phantomStrike(p)
            }
            if (p.age >= p.life) {
                addBurst(p.x, p.y - 22f, 5, 0xFFB07AFF.toInt(), 90f, 0.4f, gravity = -70f)
                phantoms.removeAt(i)
                continue
            }
            i++
        }
    }

    /** 상의 한 번의 공격 — 전직 부여 스킬 피해로 집계된다 */
    private fun phantomStrike(p: Phantom) {
        val reach = aoe(104f)
        addFx(FxKind.SLASH, p.x, p.y - 18f, angle = p.spin, radius = reach, life = 0.2f, color = 0xFFC9A6FF.toInt(), width = 1.2f)
        if (p.motion == SkillMotion.WHIRLING_ASSAULT) {
            addFx(FxKind.CYCLONE, p.x, p.y - 6f, radius = reach * 0.8f, life = 0.24f, color = 0xFFB07AFF.toInt(), width = 1.1f)
        }
        val victims = enemies.filter { it.alive && hypot(it.x - p.x, it.y - p.y) < reach }
            .sortedBy { hypot(it.x - p.x, it.y - p.y) }
            .take(3)
        if (victims.isEmpty()) return
        val crit = rand.nextDouble() * 100.0 < stats.critChance
        victims.forEachIndexed { index, e ->
            val dealt = applyShield(e, if (index == 0) p.damage else p.damage * 0.55)
            e.hp -= dealt
            e.hitFlash = 0.9f
            damageDealt += dealt
            ascendDamage += dealt
            creditDamage(stats.hollowSkill?.name ?: "공허의 형상", dealt)
            addPopup(
                e.x + (rand.nextFloat() - 0.5f) * 16f,
                e.y - 50f * e.scale,
                formatShort(dealt), false, 0.5f, 0xFFC9A6FF.toInt(), 0.86f,
            )
            if (e.hp <= 0) killEnemy(e)
        }
        ascendCasts++
        phantomCasts++
        strikeBells(p.x, p.y, reach, p.damage)
        leechPool += p.damage * leechRate()
        if (crit) shake = max(shake, 2.2f)
    }

    /** 와류: 적을 늦추고, 플레이어가 빠졌다가 가장자리를 지나면 붕괴하며 벤다 */
    private fun updateWhirlwinds(dt: Float) {
        var i = 0
        while (i < whirlwinds.size) {
            val w = whirlwinds[i]
            w.age += dt
            for (e in enemies) {
                if (!e.alive) continue
                if (hypot(e.x - w.x, e.y - w.y) < w.radius) e.slowT = max(e.slowT, 0.5f)
            }
            val d = hypot(player.x - w.x, player.y - w.y)
            if (!w.armed && d > w.radius + 8f) w.armed = true
            if (w.armed && d < w.radius + 26f) {
                addFx(FxKind.SHOCKWAVE, w.x, w.y, radius = w.radius, life = 0.46f, color = 0xFFD6EEFF.toInt(), width = 1.6f)
                addBurst(w.x, w.y, 14, 0xFFD6F0FF.toInt(), 230f, 0.6f)
                enemies.forEach { e -> if (hypot(e.x - w.x, e.y - w.y) < w.radius) e.slowT = max(e.slowT, 1.2f) }
                areaHit(w.x, w.y, w.radius, baseHitDamage() * (0.55 + w.stage * 0.25), DamageElement.PHYSICAL, "와류 붕괴")
                whirlwinds.removeAt(i)
                continue
            }
            if (w.age >= w.maxLife) {
                whirlwinds.removeAt(i)
                continue
            }
            i++
        }
    }

    /** 돌개바람: 흔들리며 전진하고, 휘말린 적을 반복해서 벤다 */
    private fun updateTwisters(dt: Float) {
        var i = 0
        while (i < twisters.size) {
            val t = twisters[i]
            t.age += dt
            if (t.age >= t.life) {
                addBurst(t.x, t.y - 20f, 8, 0xFFD6EEFF.toInt(), 190f, 0.5f)
                twisters.removeAt(i)
                continue
            }
            t.angle += (rand.nextFloat() - 0.5f) * 3.4f * dt
            t.dirX = cos(t.angle)
            t.dirY = sin(t.angle)
            t.x = (t.x + t.dirX * 118f * dt).coerceIn(30f, W - 30f)
            t.y = (t.y + t.dirY * 118f * dt).coerceIn(60f, H - 30f)
            t.tick -= dt
            if (t.tick <= 0f) {
                t.tick = 0.24f
                val hit = t.damage / 7.0
                val inside = enemies.filter { it.alive && hypot(it.x - t.x, it.y - t.y) < t.radius }
                inside.forEachIndexed { index, e ->
                    e.slowT = max(e.slowT, 0.8f)
                    underSkill(SkillMotion.TWISTER.label) {
                        hitEnemy(e, if (index == 0) hit else hit * 0.7, false, t.angle, 0.8f)
                    }
                }
                if (inside.isNotEmpty()) strikeBells(t.x, t.y, t.radius, hit)
                addFx(FxKind.SHOCKWAVE, t.x, t.y, radius = t.radius * 0.9f, life = 0.3f, color = 0xFFD6F0FF.toInt(), width = 1.1f)
            }
            i++
        }
    }

    /** 종·상 수명과 흔들림 */
    private fun updateBells(dt: Float) {
        var i = 0
        while (i < bells.size) {
            val b = bells[i]
            b.age += dt
            b.swing = max(0f, b.swing - dt * 3.4f)
            if (b.age >= b.life) {
                bells.removeAt(i)
                continue
            }
            i++
        }
    }

    /**
     * 점멸 타격: 걸어가지 않고 적 옆으로 순간이동해 벤다.
     * 지나온 길에 푸른·금빛 잔상을 남기고, 도착 지점에 섬광을 터뜨린다.
     */
    private fun flickerStrike(target: Enemy, damage: Double, crit: Boolean) {
        val fromX = player.x
        val fromY = player.y
        val toTarget = atan2(target.y - fromY, target.x - fromX)
        val side = if (rand.nextBoolean()) 1f else -1f
        val ring = 34f + rand.nextFloat() * 8f
        val landing = toTarget + side * (0.55f + rand.nextFloat() * 0.5f)
        player.x = (target.x - cos(landing) * ring).coerceIn(44f, W - 44f)
        player.y = (target.y - sin(landing) * ring).coerceIn(84f, H - 34f)
        player.facing = atan2(target.y - player.y, target.x - player.x)
        player.blinkGlow = 0.26f

        // 지나온 자리에 남는 잔상 2~3개 (푸른색·금빛 교대)
        val ghostCount = if (effectiveAps() >= 6.0) 3 else 2
        for (i in 1..ghostCount) {
            val f = i.toFloat() / (ghostCount + 1).toFloat()
            val ghostColor = if (i % 2 == 0) 0xFFFFD98A.toInt() else 0xFF6FD3FF.toInt()
            addFx(
                FxKind.AFTERIMAGE,
                fromX + (player.x - fromX) * f,
                fromY + (player.y - fromY) * f,
                angle = player.facing, life = 0.20f + f * 0.10f, color = ghostColor, width = f,
            )
        }
        // 이동 궤적을 잇는 섬광 선
        addFx(
            FxKind.STREAK, fromX, fromY - 24f,
            x2 = player.x, y2 = player.y - 24f,
            life = 0.13f, color = 0xFF9BE4FF.toInt(), width = 1.5f,
        )
        addFx(FxKind.RING, player.x, player.y - 18f, radius = 26f, life = 0.2f, color = 0xFFFFE9A8.toInt())
        addFx(FxKind.SLASH, player.x, player.y - 20f, angle = player.facing, radius = 104f, life = 0.18f, color = 0xFFEAF4FF.toInt(), width = 1.3f)
        meleeStrike(target, damage, crit, player.facing)
        if (crit) shake = max(shake, 5.6f)
    }

    /**
     * 근접: 적에게 붙어 크게 휘두른다.
     * 무기마다 궤적이 다르다 — 검·도끼는 베기, 철퇴·도리깨는 내려찍기,
     * 창은 찌르기, 육척봉은 넓게 휩쓸기, 단검·손톱은 빠른 연격.
     */
    private fun meleeStrike(target: Enemy, damage: Double, crit: Boolean, angle: Float) {
        val style = when (stats.weapon) {
            WeaponVisualType.MACE, WeaponVisualType.FLAIL -> "slam"
            WeaponVisualType.SPEAR -> "thrust"
            WeaponVisualType.QUARTERSTAFF -> "sweep"
            WeaponVisualType.DAGGER, WeaponVisualType.CLAW -> "stab"
            WeaponVisualType.AXE -> "cleave"
            else -> "slash"
        }
        val reach: Float = aoe(
            when (style) {
                "thrust" -> 118f
                "sweep" -> 108f
                "slam" -> 92f
                else -> 96f
            }
        )
        when (style) {
            "slam" -> {
                addFx(FxKind.IMPACT, target.x, target.y, radius = 74f, life = 0.42f, color = DamageElement.PHYSICAL.color, width = 1.5f)
                addFx(FxKind.SHOCKWAVE, target.x, target.y, radius = 60f, life = 0.5f, color = 0xFFD8C3A0.toInt(), width = 1.6f)
                addBurst(target.x, target.y, 12, 0xFFC9B38A.toInt(), 240f, 0.6f, gravity = 120f)
                shake = max(shake, 4.6f)
            }
            "thrust" -> {
                addFx(
                    FxKind.THRUST, player.x, player.y - 22f,
                    x2 = player.x + cos(angle) * reach, y2 = player.y + sin(angle) * reach - 22f,
                    angle = angle, radius = reach, life = 0.22f, color = 0xFFE6F0FF.toInt(), width = 1.3f,
                )
            }
            "sweep" -> {
                addFx(FxKind.SLASH, player.x, player.y - 18f, angle = angle, radius = reach, life = 0.32f, color = DamageElement.PHYSICAL.color, width = 1.7f)
                addFx(FxKind.SLASH, player.x, player.y - 18f, angle = angle + 3.14f * 0.35f, radius = reach * 0.8f, life = 0.3f, color = 0xFFCFD8E6.toInt(), width = 1.2f)
            }
            "stab" -> {
                addFx(FxKind.THRUST, player.x, player.y - 24f, x2 = target.x, y2 = target.y - 20f, angle = angle, radius = 60f, life = 0.16f, color = 0xFFEAF2FF.toInt(), width = 0.9f)
                addFx(FxKind.THRUST, player.x, player.y - 20f, x2 = target.x, y2 = target.y - 16f, angle = angle + 0.18f, radius = 56f, life = 0.2f, color = 0xFFCFE2FF.toInt(), width = 0.7f)
            }
            "cleave" -> {
                addFx(FxKind.SLASH, player.x, player.y - 20f, angle = angle, radius = reach, life = 0.3f, color = DamageElement.PHYSICAL.color, width = 1.8f)
                addFx(FxKind.SHOCKWAVE, target.x, target.y, radius = 52f, life = 0.38f, color = 0xFFBFC7D5.toInt())
            }
            else -> {
                addFx(FxKind.SLASH, player.x, player.y - 20f, angle = angle, radius = reach, life = 0.26f, color = DamageElement.PHYSICAL.color, width = 1.5f)
                addFx(FxKind.SHOCKWAVE, target.x, target.y, radius = 46f, life = 0.4f, color = 0xFFBFC7D5.toInt())
            }
        }
        // 공속이 아주 빠르면 궤적이 한 줄로 보이지 않도록 베기 호를 여러 겹으로 남긴다.
        val swingAps = effectiveAps()
        if (swingAps >= 4.0) {
            val extra = if (swingAps >= 8.0) 2 else 1
            for (i in 1..extra) {
                val off = (if (i % 2 == 0) -1f else 1f) * 0.30f * i
                addFx(
                    FxKind.SLASH, player.x, player.y - 18f,
                    angle = angle + off, radius = reach * (0.98f - i * 0.10f),
                    life = 0.15f + i * 0.03f, color = 0xFFEAF4FF.toInt(), width = 1.0f - i * 0.15f,
                )
            }
        }
        val arcHalf = if (style == "sweep") 1.5f else 1.15f
        val inArc = enemies.filter { e ->
            e.alive && hypot(e.x - player.x, e.y - player.y) < reach + 6f &&
                abs(normalize(atan2(e.y - player.y, e.x - player.x) - angle)) < arcHalf
        }.sortedBy { hypot(it.x - player.x, it.y - player.y) }.take(5)
        if (inArc.isEmpty()) {
            hitEnemy(target, damage, crit, angle, 1f)
        } else {
            inArc.forEachIndexed { i, e ->
                hitEnemy(e, if (i == 0) damage else damage * 0.55, crit && i == 0, angle, if (i == 0) 1f else 0.7f)
            }
        }
        // 휘두른 자리에 종이 있으면 울린다
        strikeBells(player.x, player.y, reach, damage)
        // 공허의 형상은 근접 공격에 반응해 상을 깨운다
        spawnPhantoms(target)
        if (crit) shake = max(shake, 5.2f)
    }

    /** 활: 시위를 당겼다 놓으며 깃털 달린 화살을 날린다. */
    private fun bowShot(target: Enemy, damage: Double, crit: Boolean, angle: Float) {
        // 전직이 투사체를 더해 주면(예: 끝없는 탄약) 부채꼴로 같이 날린다.
        val extra = totalExtraProjectiles().coerceIn(0, 6)
        for (i in 0..extra) {
            val spread = (i - extra / 2f) * 0.075f
            spawnProjectile(
                ProjKind.ARROW, target, damage, crit, stats.fx.element,
                speed = 980f, angle = angle + spread,
            )
        }
        addFx(
            FxKind.SLASH, player.x + cos(angle) * 16f, player.y + sin(angle) * 16f - 22f,
            angle = angle, radius = 26f, life = 0.14f, color = 0xFFF2E6C8.toInt(), width = 0.6f,
        )
    }

    /** 쇠뇌: 앞세워 조준하고 반동과 함께 볼트를 연사한다. */
    private fun crossbowShot(target: Enemy, damage: Double, crit: Boolean, angle: Float) {
        val extra = totalExtraProjectiles().coerceIn(0, 6)
        for (i in 0..extra) {
            val spread = (i - extra / 2f) * 0.06f
            spawnProjectile(
                ProjKind.BOLT, target, damage, crit, stats.fx.element,
                speed = 1_520f, angle = angle + spread,
            )
        }
        addBurst(player.x + cos(angle) * 18f, player.y + sin(angle) * 18f - 22f, 4, 0xFFFFE8A8.toInt(), 160f, 0.22f)
        addFx(FxKind.RING, player.x + cos(angle) * 20f, player.y + sin(angle) * 20f - 22f, radius = 16f, life = 0.16f, color = 0xFFFFD98A.toInt())
    }

    /** 지팡이·완드·홀: 무기를 치켜들고 주문을 쏜다. 번개는 전기불꽃이 갈라진다. */
    private fun castSpell(target: Enemy, damage: Double, crit: Boolean, angle: Float) {
        val tipX = player.x + cos(angle) * 12f
        val tipY = player.y - 40f + sin(angle) * 8f
        addFx(FxKind.PILLAR, tipX, tipY + 34f, radius = 14f, life = 0.18f, color = alphaColor(stats.fx.element.color, 170))
        when (stats.fx) {
            SkillEffectType.LIGHTNING -> {
                // 전기불꽃: 갈라지는 주문 줄기가 여러 갈래로 뻗는다
                val victims = (listOf(target) + nearbyEnemies(target, 150f, 2)).distinct().take(3)
                var prevX = tipX
                var prevY = tipY
                victims.forEach { victim ->
                    addFx(
                        FxKind.BOLT, prevX, prevY, x2 = victim.x, y2 = victim.y - 18f,
                        life = 0.2f, color = 0xFFFFF3A8.toInt(), seed = rand.nextInt(), width = 1.1f,
                    )
                    prevX = victim.x
                    prevY = victim.y - 18f
                }
                victims.forEachIndexed { i, victim ->
                    val spread = (i - (victims.size - 1) / 2f) * 0.16f
                    spawnProjectile(
                        ProjKind.SPARK, victim, if (i == 0) damage else damage * 0.55, crit && i == 0,
                        DamageElement.LIGHTNING, speed = 1_180f, angle = angle + spread,
                    )
                }
            }
            SkillEffectType.MINION -> {
                val fan = listOf(target) + nearbyEnemies(target, 240f, 2)
                fan.forEachIndexed { i, e ->
                    val spread = (i - (fan.size - 1) / 2f) * 0.20f
                    spawnProjectile(
                        ProjKind.MINION_ORB, e, if (i == 0) damage else damage * 0.42,
                        crit && i == 0, DamageElement.PHYSICAL, speed = 620f, angle = angle + spread,
                    )
                }
            }
            SkillEffectType.FIRE -> {
                spawnProjectile(ProjKind.FIRE_ORB, target, damage, crit, DamageElement.FIRE, speed = 620f, angle = angle)
                addFx(FxKind.AURA, tipX, tipY, radius = 26f, life = 0.3f, color = 0xFFFF8A3D.toInt())
            }
            SkillEffectType.ICE -> {
                spawnProjectile(ProjKind.FROST_ORB, target, damage, crit, DamageElement.COLD, speed = 700f, angle = angle)
                addFx(FxKind.AURA, tipX, tipY, radius = 24f, life = 0.3f, color = 0xFF8FE6FF.toInt())
            }
            SkillEffectType.CHAOS -> {
                spawnProjectile(ProjKind.CHAOS_ORB, target, damage, crit, DamageElement.CHAOS, speed = 560f, angle = angle)
                addFx(FxKind.AURA, tipX, tipY, radius = 26f, life = 0.34f, color = DamageElement.CHAOS.color)
            }
            SkillEffectType.MELEE -> meleeStrike(target, damage, crit, angle)
        }
    }

    private fun alphaColor(color: Int, a: Int): Int =
        (color and 0x00FFFFFF) or ((a.coerceIn(0, 255)) shl 24)

    private fun normalize(a: Float): Float {
        var v = a
        while (v > PI_F) v -= 2f * PI_F
        while (v < -PI_F) v += 2f * PI_F
        return v
    }

    private fun nearbyEnemies(from: Enemy, range: Float, limit: Int): List<Enemy> =
        enemies.filter { it.alive && it !== from && hypot(it.x - from.x, it.y - from.y) < range }
            .sortedBy { hypot(it.x - from.x, it.y - from.y) }
            .take(limit)

    private fun spawnProjectile(
        kind: ProjKind,
        target: Enemy,
        damage: Double,
        crit: Boolean,
        element: DamageElement,
        speed: Float,
        angle: Float,
        from: PlayerState = player,
    ) {
        addProjectile(
            Projectile(
                x = from.x + cos(angle) * 20f,
                y = from.y + sin(angle) * 20f - 22f,
                dirX = cos(angle),
                dirY = sin(angle),
                speed = speed,
                damage = damage,
                crit = crit,
                target = target,
                hostile = false,
                element = element,
                kind = kind,
            )
        )
    }

    // ------------------------------------------------------------ 적

    /** 감전·빙결·점화 타이머를 줄이고 점화 피해를 준다. */
    private fun updateAilments(dt: Float) {
        for (e in enemies) {
            if (!e.alive) continue
            if (e.shockT > 0f) e.shockT = max(0f, e.shockT - dt)
            if (e.freezeT > 0f) e.freezeT = max(0f, e.freezeT - dt)
            if (e.slowT > 0f) e.slowT = max(0f, e.slowT - dt)
            if (e.oilT > 0f) e.oilT = max(0f, e.oilT - dt)
            if (e.igniteT <= 0f) continue
            e.igniteT = max(0f, e.igniteT - dt)
            val tick = e.igniteDps * dt
            if (tick <= 0) continue
            e.hp -= tick
            damageDealt += tick
            creditDamage(Ailments.IGNITE_LABEL, tick)
            if (rand.nextDouble() < 0.12) {
                addPopup(e.x, e.y - 46f * e.scale, formatShort(tick), false, 0.5f, 0xFFFF8A3D.toInt(), 0.7f)
            }
            if (e.hp <= 0) killEnemy(e)
        }
    }

    private fun updateEnemies(dt: Float) {
        for (e in enemies) {
            if (!e.alive) continue
            val frozen = e.freezeT > 0f
            // 동결된 적은 얼음 감옥 안에서 굳는다 — 걷지도, 차지도, 예고를 펼치지도 못한다.
            if (frozen) {
                e.vx = 0f
                e.vy = 0f
                e.windup = 0f
                e.attackAnim = 0f
                e.attackTimer = max(e.attackTimer, 0.4)
                continue
            }
            val walkRate = if (e.kind == EnemyKind.BOSS && e.boss != null) 5f else 8f
            e.walkPhase += dt * walkRate
            // 소환수 어그로 — 가까운 소환수가 있으면 그쪽으로 돌아선다.
            e.aggroTimer -= dt
            if (e.aggroTimer <= 0f || e.targetMinion?.alive != true) {
                e.aggroTimer = 0.7f + rand.nextFloat() * 0.6f
                var pick: Minion? = null
                var pickD = 190f * 190f
                for (m in minions) {
                    if (!m.alive || m.type.untargetable) continue
                    val d2 = (m.x - e.x) * (m.x - e.x) + (m.y - e.y) * (m.y - e.y)
                    if (d2 < pickD) {
                        pickD = d2
                        pick = m
                    }
                }
                e.targetMinion = if (pick != null && rand.nextDouble() < 0.72) pick else null
            }
            val targetM = e.targetMinion?.takeIf { it.alive && !it.type.untargetable }
            val tx = targetM?.x ?: player.x
            val ty = targetM?.y ?: player.y
            val dx = tx - e.x
            val dy = ty - e.y
            val dist = hypot(dx, dy).coerceAtLeast(0.001f)
            val stop = if (e.ranged) 168f else 26f * e.scale
            val bossNow = e.boss
            if (bossNow != null) {
                bossPulseTimer -= dt
                if (bossPulseTimer <= 0f) {
                    bossPulseTimer = 2.2f
                    if (dist < 380f) {
                        hitPlayer(
                            maxPool * 0.09, bossNow.element, false, e.x, e.y,
                            source = bossNow.name, skill = "기세 폭발",
                        )
                        damageMinionsInRadius(e.x, e.y, 190f, maxPool * 0.05)
                        addFx(FxKind.AURA, e.x, e.y, radius = 150f, life = 0.55f, color = bossNow.element.color)
                    }
                }
                if (e.hp < e.maxHp * 0.5 && !e.enraged) {
                    e.enraged = true
                    bannerText = "${bossNow.name} 격노"
                    banner = 1.8f
                    addFx(FxKind.AURA, e.x, e.y, radius = 96f, life = 0.9f, color = 0xFFFF4B4B.toInt())
                }
                if (e.windup > 0f) {
                    e.windup -= dt
                    if (e.windup <= 0f) resolveSlam(e)
                }
            }

            if (dist > stop) {
                val pull = if (dist > 300f) 1f else 0.82f
                val ang = e.slotAngle + (elapsed * 0.10).toFloat()
                val aimX = tx + cos(ang) * stop * 0.9f
                val aimY = ty + sin(ang) * stop * 0.9f
                val ax = aimX - e.x
                val ay = aimY - e.y
                val ad = hypot(ax, ay).coerceAtLeast(0.001f)
                val sp = e.speed * pull * (if (e.windup > 0f) 0.25f else 1f) *
                    (if (e.slowT > 0f) e.slowMul else 1f)
                e.vx = ax / ad * sp
                e.vy = ay / ad * sp
                e.x += e.vx * dt
                e.y += e.vy * dt
            } else if (e.ranged && dist < 118f) {
                e.vx = -dx / dist * e.speed * 0.6f
                e.vy = -dy / dist * e.speed * 0.6f
                e.x += e.vx * dt
                e.y += e.vy * dt
            }

            e.x = e.x.coerceIn(24f, W - 24f)
            e.y = e.y.coerceIn(56f, H - 30f)

            if (e.windup > 0f) continue
            e.attackTimer -= dt
            val inRange = if (e.ranged) dist < 300f else dist < stop + 24f
            if (e.attackTimer <= 0f && inRange) {
                if (!e.ranged && meleeBudget <= 0) {
                    // 몰려든 적이 한꺼번에 때리지 않도록 순서를 기다린다
                    e.attackTimer = 0.18
                    continue
                }
                e.attackCount++
                e.attackAnim = 1f
                val interval = e.interval * (if (e.enraged) 0.82 else 1.0) * (0.9 + rand.nextDouble() * 0.2)
                e.attackTimer = interval
                if (!e.ranged) meleeBudget--
                if (bossNow != null && e.attackCount % 4 == 3) {
                    beginSlam(e)
                } else if (e.ranged) {
                    if (targetM != null) {
                        // 원거리 몬스터가 소환수를 노린다
                        addFx(FxKind.BOLT, e.x, e.y - 30f, x2 = targetM.x, y2 = targetM.y - 16f, life = 0.16f, color = e.element.color, width = 1f)
                        hitMinion(targetM, e.hit, e.x, e.y)
                    } else {
                        enemyShoot(e, dx / dist, dy / dist)
                    }
                } else {
                    if (targetM != null) {
                        hitMinion(targetM, e.hit, e.x, e.y)
                        addFx(FxKind.SLASH, fastX(e.x, dx, dist), fastY(e.y, dy, dist), angle = atan2(dy, dx), radius = 30f, life = 0.2f, color = 0xFFB06060.toInt())
                    } else {
                        hitPlayer(e.hit, e.element, true, e.x, e.y, source = enemyTitle(e), skill = "근접 강타")
                        addFx(FxKind.SLASH, fastX(e.x, dx, dist), fastY(e.y, dy, dist), angle = atan2(dy, dx), radius = 32f, life = 0.2f, color = 0xFFB06060.toInt())
                    }
                }
            }
        }
    }

    private fun fastX(x: Float, dx: Float, dist: Float) = x + dx / dist * 24f
    private fun fastY(y: Float, dy: Float, dist: Float) = y + dy / dist * 24f

    private fun enemyShoot(e: Enemy, dirX: Float, dirY: Float) {
        val bossNow = e.boss
        if (bossNow != null) {
            repeat(3) { i ->
                val spread = (i - 1) * 0.16f
                addProjectile(
                    Projectile(
                        x = e.x, y = e.y - 46f * e.scale,
                        dirX = cos(atan2(dirY, dirX) + spread),
                        dirY = sin(atan2(dirY, dirX) + spread),
                        speed = 330f,
                        damage = e.hit * 0.62,
                        crit = false,
                        target = null,
                        hostile = true,
                        element = bossNow.element,
                        kind = ProjKind.ENEMY_BOLT,
                        source = bossNow.name,
                        skill = bossPatternName.ifBlank { "보스 사격" },
                    )
                )
            }
        } else {
            addProjectile(
                Projectile(
                    x = e.x, y = e.y - 28f,
                    dirX = dirX, dirY = dirY,
                    speed = 260f,
                    damage = e.hit,
                    crit = false,
                    target = null,
                    hostile = true,
                    element = e.element,
                    kind = ProjKind.ENEMY_BOLT,
                    source = enemyTitle(e),
                    skill = "원거리 사격",
                )
            )
        }
    }

    private fun beginSlam(e: Enemy) {
        e.windup = 0.95f
        slamX = player.x
        slamY = player.y
        slamRadius = if (e.scale > 2f) 118f else 86f
        addFx(FxKind.GROUND_CRACK, slamX, slamY, radius = slamRadius, life = 0.95f, color = 0xFFFF6A3D.toInt())
    }

    private fun resolveSlam(e: Enemy) {
        addFx(FxKind.SHOCKWAVE, slamX, slamY, radius = slamRadius, life = 0.55f, color = 0xFFFF7A3D.toInt(), width = 1.6f)
        addBurst(slamX, slamY, 16, 0xFFFF9A4D.toInt(), 300f, 0.7f)
        shake = max(shake, 9f)
        val d = hypot(player.x - slamX, player.y - slamY)
        if (d < slamRadius) {
            val dodge = (0.16 + stats.evade * 0.004).coerceAtMost(0.55)
            if (rand.nextDouble() < dodge) {
                evades++
                addPopup(player.x, player.y - 60f, "회피", false, 0.8f, 0xFF8CE8C0.toInt(), 0.9f)
            } else {
                hitPlayer(
                    e.hit * 1.35, e.element, false, e.x, e.y, heavy = true,
                    source = enemyTitle(e), skill = "내려찍기",
                )
            }
        }
        damageMinionsInRadius(slamX, slamY, slamRadius, e.hit * 1.2)
    }

    private fun updateDots(dt: Float) {
        for (e in enemies) {
            if (!e.alive || e.dot <= 0.0) continue
            e.dotTimer -= dt
            val tick = e.dot * dt
            e.hp -= tick
            e.dot -= tick
            damageDealt += tick
            creditDamage("카오스 지속 피해", tick)
            if (e.dotTimer <= 0f) {
                e.dotTimer = 0.35
                addPopup(e.x + (rand.nextFloat() - 0.5f) * 22f, e.y - 52f * e.scale, formatShort(tick), false, 0.55f, DamageElement.CHAOS.color, 0.78f)
            }
            if (e.hp <= 0) killEnemy(e)
        }
    }

    // ------------------------------------------------------------ 피해 처리

    /** 준 피해 가운데 생명력으로 흡수하는 비율 (기본 0.6%에 전직 흡수 효과를 더한다) */
    private fun leechRate(): Double = 0.006 * (1.0 + stats.lifeLeechPct.coerceIn(0.0, 500.0) / 100.0)

    /** 룬 보호막이 있으면 피해를 먼저 흡수한다. */
    private fun applyShield(e: Enemy, damage: Double): Double {
        if (e.shield <= 0.0) return damage
        val absorbed = min(e.shield, damage * 0.6)
        e.shield -= absorbed
        return damage - absorbed
    }

    private fun hitEnemy(
        e: Enemy,
        damage: Double,
        crit: Boolean,
        angle: Float,
        scale: Float = 1f,
        /** 메타젬에 넘길 명중 사건의 무게 (음수면 피해량으로 센다) */
        energyWeight: Double = -1.0,
    ) {
        if (!e.alive) return
        // 감전된 적은 피해를 더 받고(20%~감전 수치), 저주가 걸린 적은 저항이 낮아진 것으로 본다.
        val shockedMul = if (e.shocked) Ailments.shockMultiplier(stats.ailmentMagnitudePct) else 1.0
        val curseMul = if (e.curseT > 0f) 1.08 else 1.0
        // 기름에 젖은 적은 노출되어 총 원소 저항이 20% 낮아진 것으로 본다.
        val oilMul = if (e.oilT > 0f && stats.fx.element != DamageElement.PHYSICAL) 1.12 else 1.0
        if (stats.unique.curseOnHit) e.curseT = max(e.curseT, 6f)
        val hpBefore = e.hp
        val dealt = applyShield(e, damage * shockedMul * curseMul * oilMul)
        e.hp -= dealt
        // 생명력을 넘긴 몫은 초과 피해로 남긴다 — 재의 전령이 이 값을 태운다.
        e.overkill = (dealt - hpBefore).coerceAtLeast(0.0)
        damageDealt += dealt
        creditDamage(skillContext, dealt)
        e.hitFlash = 1f
        if (crit) {
            critCount++
            shake = max(shake, if (e.kind == EnemyKind.BOSS) 3.4f else 4.6f)
        }
        addPopup(
            e.x + (rand.nextFloat() - 0.5f) * 20f,
            e.y - 52f * e.scale - rand.nextFloat() * 10f,
            if (crit) "CRIT! ${formatShort(dealt)}" else formatShort(dealt),
            crit,
            if (crit) 1.0f else 0.62f,
            if (crit) 0xFFFFC24D.toInt() else 0xFFF5F1E8.toInt(),
            if (crit) 1.32f else 0.92f * scale,
        )
        leechPool += dealt * leechRate()
        onSkillHit(e, crit, angle)
        applyAilments(e, crit, dealt)        // 마무리 타격(고유 아이템) — 생명력이 얼마 남지 않은 적을 즉시 처치한다.
        if (stats.unique.cullingStrike && e.alive && e.hp > 0.0 && e.hp <= e.maxHp * 0.10) {
            addPopup(e.x, e.y - 60f * e.scale, "마무리", false, 0.55f, 0xFFFFD36A.toInt(), 0.8f)
            killEnemy(e)
            return
        }
        if (crit) {
            // 한 방의 피해가 클수록 메타 에너지가 많이 찬다 (절대 피해 기준).
            fireEvent(TriggerCondition.CRIT, e, if (energyWeight > 0.0) energyWeight else dealt / REFERENCE_HIT)
            // 권능 충전은 치명타에서 가끔 생긴다 — 공허의 상을 더 만들 수 있다.
            if (rand.nextDouble() < 0.12) gainPowerCharge()
        }
        if (e.hp <= 0) killEnemy(e)
    }

    /** 명중한 피해 속성으로 상태이상을 굴린다. 주 속성은 확률이 높고 다른 속성도 낮은 확률로 걸린다. */
    private fun applyAilments(e: Enemy, crit: Boolean, hitDamage: Double = 0.0) {
        val element = stats.fx.element
        fun roll(ailment: DamageElement, match: Double, other: Double, bonusPct: Double): Boolean {
            val base = if (element == ailment) match else other
            val chance = base * (1.0 + bonusPct.coerceIn(0.0, 300.0) / 100.0) + if (crit) 0.35 else 0.0
            return rand.nextDouble() < chance
        }
        if (roll(DamageElement.LIGHTNING, 0.30, 0.10, stats.shockChancePct + stats.unique.shockChancePct)) {
            e.shockT = max(e.shockT, Ailments.SHOCK_TIME)
            fireEvent(TriggerCondition.SHOCK, e, 0.9)
            onControlApplied(e)
            addPopup(e.x, e.y - 74f * e.scale, Ailments.SHOCK_LABEL, false, 0.5f, 0xFFFFE272.toInt(), 0.72f)
            addFx(FxKind.SPARK_FLASH, e.x, e.y - 34f * e.scale, radius = 16f, life = 0.16f, color = 0xFFBDE6FF.toInt(), width = 0.9f)
            // 천둥의 전령 — 감전이 걸리면 그 자리에 낙뢰가 떨어진다 (0.4초에 한 번).
            heraldThunderOnShock(e, hitDamage)
        }
        if (roll(DamageElement.COLD, 0.22, 0.07, stats.freezeChancePct + stats.unique.freezeChancePct)) {
            e.freezeT = max(e.freezeT, Ailments.FREEZE_TIME)
            fireEvent(TriggerCondition.FREEZE, e, 0.8)
            onControlApplied(e)
            addPopup(e.x + 16f, e.y - 74f * e.scale, Ailments.FREEZE_LABEL, false, 0.5f, 0xFF9BE8FF.toInt(), 0.72f)
            addFx(FxKind.ICE_CONE, e.x, e.y, radius = 26f, life = 0.3f, color = 0xFFBFF4FF.toInt())
            addFx(FxKind.FROST_NOVA, e.x, e.y, radius = 30f * e.scale, life = 0.32f, color = 0xFF9BE8FF.toInt(), width = 0.8f)
        }
        if (roll(DamageElement.FIRE, 0.30, 0.09, stats.igniteChancePct + stats.unique.igniteChancePct)) {
            val ignite = Ailments.igniteDps(baseHitDamage(), stats.ailmentMagnitudePct)
            e.igniteT = max(e.igniteT, Ailments.IGNITE_TIME)
            e.igniteDps = max(e.igniteDps, ignite)
            fireEvent(TriggerCondition.IGNITE, e, 0.9)
            addPopup(e.x - 14f, e.y - 74f * e.scale, Ailments.IGNITE_LABEL, false, 0.5f, 0xFFFF8A3D.toInt(), 0.72f)
        }
    }

    // ------------------------------------------------------------ 트리거(치명타·감전·빙결·점화 시 시전)

    /**
     * 조건 사건 하나를 모든 메타젬에 전달한다.
     * 각 메타젬은 자기 조건(accepts)에 맞는 사건만 받아 독립적으로 에너지를 채운다.
     * energy는 사건의 피해 규모(weight)에 따라 100을 훨씬 넘겨 쌓이고,
     * 100을 넘길 때마다 연발 1발씩 예약된다.
     */
    private fun fireEvent(condition: TriggerCondition, target: Enemy? = null, weight: Double = 1.0) {
        if (triggers.isEmpty()) return
        for (rt in triggers) {
            if (!rt.spec.kind.accepts.contains(condition)) continue
            rt.addEnergy(energyGain(rt, weight))
            if (rt.ready()) releaseBurst(rt, target)
        }
    }

    /**
     * 한 사건이 주는 메타 에너지.
     * 피해량을 기준값(REFERENCE_HIT)으로 나눈 만큼 그대로 %가 된다 —
     * 60,000 피해면 100%(1발), 600,000 피해면 1,000%(10발)이 한 번에 쌓인다.
     */
    private fun energyGain(rt: TriggerRuntime, weight: Double): Double {
        val scaled = weight * TriggerRuntime.CAST_COST * (1.0 + stats.metaEnergyPct.coerceIn(0.0, 300.0) / 100.0)
        return scaled.coerceIn(TriggerRuntime.CAST_COST * 0.1, TriggerRuntime.CAST_COST * 12.0)
    }

    /**
     * 쌓인 에너지만큼 연발을 예약한다. 발동 간격은 0.04~0.07초.
     * 예: 1,800%가 쌓여 있으면 혜성 18발이 우수수 떨어진다.
     */
    private fun releaseBurst(rt: TriggerRuntime, preferred: Enemy?) {
        val count = rt.drainBurst()
        if (count <= 0) return
        val anchor = preferred?.takeIf { it.alive } ?: nearestEnemy() ?: return
        val label = rt.spec.skills.firstOrNull()?.name ?: rt.spec.kind.short
        lastBurstLabel = "$label x$count"
        maxBurst = max(maxBurst, count)
        burstCasts += count
        if (count > 1) {
            addPopup(player.x, player.y - 98f, "$label x$count!", true, 1.0f, 0xFFFFE6A8.toInt(), 1.0f)
            shake = max(shake, min(6.5f, 1.4f + count * 0.22f))
        }
        var delay = 0f
        for (i in 0 until count) {
            val skill = rt.skillFor(i) ?: break
            val victim = if (i == 0) anchor else pickBurstTarget(anchor)
            castQueue.add(PendingCast(delay, rt, skill, victim, i, count))
            delay += 0.04f + rand.nextFloat() * 0.03f
        }
    }

    /** 연발 사이에 떨어질 자리를 고른다 (같은 무리 안에서 조금씩 흩어진다) */
    private fun pickBurstTarget(anchor: Enemy): Enemy {
        val alive = enemies.filter { it.alive }
        if (alive.isEmpty()) return anchor
        val near = alive.filter { hypot(it.x - anchor.x, it.y - anchor.y) < 280f }
        val pool = if (near.isNotEmpty()) near else alive
        return pool[rand.nextInt(pool.size)]
    }

    private fun updateTriggers(dt: Float) {
        for (rt in triggers) {
            rt.tick(dt)
            // 신성모독 같은 지속 오라는 스스로 에너지를 채운다.
            if (rt.tickAura(dt) && rt.ready()) releaseBurst(rt, nearestEnemy())
            // 소환수 빌드는 소환수가 쓰러지는 사건이 계속 생긴다.
            if (rt.spec.kind.accepts.contains(TriggerCondition.MINION_DEATH) &&
                stats.fx == SkillEffectType.MINION && rt.ready()
            ) {
                releaseBurst(rt, nearestEnemy())
            }
        }
    }

    /** 예약된 연발을 하나씩 떨어뜨린다. */
    private fun updateCastQueue(dt: Float) {
        var i = 0
        while (i < castQueue.size) {
            val cast = castQueue[i]
            cast.delay -= dt
            if (cast.delay > 0f) {
                i++
                continue
            }
            val victim = cast.target.takeIf { it.alive } ?: nearestEnemy()
            if (victim != null) {
                cast.rt.noteCast()
                castNow(cast.rt, cast.skill, victim, cast.index, cast.burst)
            }
            castQueue.removeAt(i)
        }
    }

    private fun castNow(
        rt: TriggerRuntime,
        skill: TriggeredSkill,
        target: Enemy,
        index: Int = 0,
        burst: Int = 1,
    ) {
        val damage = rt.castDamage(skill)
        triggerCasts++
        val color = skill.fx.element.color
        addPopup(
            player.x + (rand.nextFloat() - 0.5f) * 14f,
            player.y - 86f,
            "${rt.spec.kind.short}!",
            false, 0.7f, 0xFFCFF3FF.toInt(), 0.86f,
        )
        when (skill.fx) {
            SkillEffectType.ICE -> spawnMeteor(target, damage, skill.name, rt, index, Math.max(1, burst))
            SkillEffectType.LIGHTNING -> triggerLightning(target, damage, skill.name, rt)
            SkillEffectType.FIRE -> triggerFire(target, damage, skill.name, rt)
            SkillEffectType.CHAOS -> triggerChaos(target, damage, skill.name, rt)
            SkillEffectType.MELEE -> triggerSlam(target, damage, skill.name, rt)
            SkillEffectType.MINION -> triggerMinions(target, damage, skill.name, rt)
        }
        addFx(FxKind.AURA, player.x, player.y, radius = 54f, life = 0.3f, color = color)
    }

    /** 전직 효과 범위 증가를 반영한 반경 */
    private fun aoe(radius: Float): Float =
        (radius * (1f + (stats.aoePct / 100.0).toFloat())).coerceAtLeast(radius * 0.5f)

    /** 적을 늦춘다 — 더 센 감속이 걸려 있으면 그 값을 유지한다 */
    private fun slowEnemy(e: Enemy, seconds: Float, mul: Float = 0.7f) {
        if (e.slowT <= 0f || mul < e.slowMul) e.slowMul = mul
        e.slowT = max(e.slowT, seconds)
    }

    /**
     * 전직이 부여한 스킬을 굴린다.
     *
     * 공허의 형상은 상을 따로 쌓지 않는다 — 플레이어가 근접기를 쓸 때마다
     * [spawnPhantoms]가 반투명 상을 내보내 장착 스킬을 한 번 휘두르게 한다.
     * 공허의 집중은 몇 초마다 실체 없는 종을 플레이어 주위에 띄워 두고,
     * 그 종을 때리면 충격파와 권능 충전이 돌아온다.
     */
    private fun updateAscendancySkills(dt: Float) {
        if (stats.ascendSkills.isEmpty()) return
        stats.ascendSkills.forEachIndexed { index, skill ->
            ascendTimers[index] -= dt
            if (ascendTimers[index] > 0f) return@forEachIndexed
            ascendTimers[index] = ascendInterval(skill).toFloat()
            if (!skill.name.contains("집중")) return@forEachIndexed
            if (bells.count { it.ghost } >= 3) return@forEachIndexed
            // 종은 플레이어 주위 3미터 안에 나타난다
            val a = rand.nextFloat() * 6.283f
            val x = (player.x + cos(a) * (22f + rand.nextFloat() * 20f)).coerceIn(40f, W - 40f)
            val y = (player.y + sin(a) * (16f + rand.nextFloat() * 16f)).coerceIn(84f, H - 30f)
            bells.add(Bell(x, y, 21f, ghost = true))
            addFx(FxKind.AURA, x, y - 14f, radius = 40f, life = 0.6f, color = 0xFFD8C3A0.toInt())
            addBurst(x, y - 18f, 8, 0xFFE8D9B8.toInt(), 130f, 0.5f, gravity = -70f)
            addPopup(x, y - 60f, "공허의 종", false, 0.8f, 0xFFE8D9B8.toInt(), 0.78f)
        }
    }

    /** 혜성: 하늘에서 얼음 덩어리가 떨어져 착탄 지점에 광역 피해 */
    private fun spawnMeteor(
        target: Enemy,
        damage: Double,
        label: String,
        rt: TriggerRuntime? = null,
        index: Int = 0,
        burst: Int = 1,
    ) {
        // 연발이면 한 자리에 몰리지 않도록 좌표를 넓게 흩고, 떨어지는 시차도 둔다.
        val spread = if (burst > 1) 150f + burst * 6f else 18f
        val x = (target.x + (rand.nextFloat() - 0.5f) * spread).coerceIn(30f, BattleWorld.W - 30f)
        val y = (target.y + (rand.nextFloat() - 0.5f) * spread * 0.9f).coerceIn(60f, BattleWorld.H - 30f)
        val fall = if (burst > 1) 0.32f + rand.nextFloat() * 0.2f else 0.55f
        val radius = if (burst > 1) 78f else 96f
        addFx(
            FxKind.METEOR, x, y,
            radius = radius, life = fall, color = DamageElement.COLD.color,
            seed = rand.nextInt() + index, width = 1.1f + (index % 3) * 0.15f,
        )
        addFx(FxKind.GROUND_CRACK, x, y, radius = radius, life = fall, color = 0xFF7FD8FF.toInt())
        pendingStrikes.add(
            PendingStrike(x, y, radius, damage, DamageElement.COLD, label, FxKind.METEOR, fall, rt)
        )
    }

    private fun triggerLightning(target: Enemy, damage: Double, label: String, rt: TriggerRuntime? = null) {
        val victims = (listOf(target) + nearbyEnemies(target, 140f, 2)).distinct().take(3)
        var prev = target
        victims.forEachIndexed { index, victim ->
            addFx(
                FxKind.BOLT, prev.x, prev.y - 30f, x2 = victim.x, y2 = victim.y - 20f,
                life = 0.22f, color = 0xFFFFF3A8.toInt(), seed = rand.nextInt(), width = 1.3f,
            )
            prev = victim
        }
        addFx(FxKind.PILLAR, target.x, target.y, radius = 30f, life = 0.4f, color = 0xFFFFF6C8.toInt())
        addFx(FxKind.RING, target.x, target.y, radius = 76f, life = 0.4f, color = 0xFFFFE272.toInt())
        addBurst(target.x, target.y, 12, 0xFFFFF3A8.toInt(), 250f, 0.5f)
        dealAreaDamage(target, damage, 88f, DamageElement.LIGHTNING, label, rt)
        shake = max(shake, 4.2f)
    }

    private fun triggerFire(target: Enemy, damage: Double, label: String, rt: TriggerRuntime? = null) {
        addFx(FxKind.RING, target.x, target.y, radius = 92f, life = 0.5f, color = 0xFFFF8A3D.toInt(), width = 1.2f)
        addFx(FxKind.RING, target.x, target.y, radius = 52f, life = 0.36f, color = 0xFFFFD27A.toInt(), width = 0.8f)
        addBurst(target.x, target.y, 16, 0xFFFFA64D.toInt(), 280f, 0.8f, gravity = -80f)
        dealAreaDamage(target, damage, 92f, DamageElement.FIRE, label, rt)
        shake = max(shake, 4.6f)
    }

    private fun triggerChaos(target: Enemy, damage: Double, label: String, rt: TriggerRuntime? = null) {
        addFx(FxKind.AURA, target.x, target.y, radius = 82f, life = 0.7f, color = DamageElement.CHAOS.color)
        addBurst(target.x, target.y, 14, 0xFFC182FF.toInt(), 200f, 0.9f, gravity = -50f)
        dealAreaDamage(target, damage, 84f, DamageElement.CHAOS, label, rt)
    }

    private fun triggerSlam(target: Enemy, damage: Double, label: String, rt: TriggerRuntime? = null) {
        addFx(FxKind.SHOCKWAVE, target.x, target.y, radius = 78f, life = 0.45f, color = 0xFFFFD89A.toInt(), width = 1.4f)
        addBurst(target.x, target.y, 10, 0xFFFFC24D.toInt(), 230f, 0.55f)
        dealAreaDamage(target, damage, 76f, DamageElement.PHYSICAL, label, rt)
        shake = max(shake, 3.6f)
    }

    private fun triggerMinions(target: Enemy, damage: Double, label: String, rt: TriggerRuntime? = null) {
        val fan = listOf(target) + nearbyEnemies(target, 200f, 2)
        fan.forEachIndexed { index, enemy ->
            val angle = atan2(enemy.y - player.y, enemy.x - player.x)
            spawnProjectile(
                ProjKind.MINION_ORB, enemy, if (index == 0) damage else damage * 0.45,
                false, DamageElement.PHYSICAL, speed = 660f, angle = angle,
            )
        }
    }

    private fun dealAreaDamage(center: Enemy, damage: Double, radiusRaw: Float, element: DamageElement, label: String, rt: TriggerRuntime? = null) {
        val radius = aoe(radiusRaw)
        var hits = 0
        for (other in enemies) {
            if (!other.alive) continue
            if (hypot(other.x - center.x, other.y - center.y) > radius) continue
            val taken = applyShield(other, damage * if (other === center) 1.0 else 0.62)
            other.hp -= taken
            other.hitFlash = 1f
            damageDealt += taken
            triggerDamage += taken
            creditDamage(label, taken)
            rt?.addDamage(taken)
            hits++
            addPopup(
                other.x + (rand.nextFloat() - 0.5f) * 18f,
                other.y - 52f * other.scale,
                formatShort(taken), false, 0.6f, element.color, 0.92f,
            )
            if (other.hp <= 0) killEnemy(other)
        }
        if (hits > 0) {
            addPopup(center.x, center.y - 72f * center.scale, label, false, 0.8f, 0xFFCFEFFF.toInt(), 0.78f)
        }
        leechPool += damage * leechRate()
    }

    private fun updateStrikes(dt: Float) {
        var i = 0
        while (i < pendingStrikes.size) {
            val strike = pendingStrikes[i]
            strike.delay -= dt
            if (strike.delay > 0f) {
                i++
                continue
            }
            addFx(FxKind.SHOCKWAVE, strike.x, strike.y, radius = strike.radius, life = 0.5f, color = strike.element.color, width = 1.5f)
            addFx(FxKind.RING, strike.x, strike.y, radius = strike.radius * 0.7f, life = 0.4f, color = 0xFFE8FCFF.toInt())
            addFx(FxKind.FROST_NOVA, strike.x, strike.y - 4f, radius = strike.radius * 0.95f, life = 0.55f, color = 0xFFBFF4FF.toInt())
            addFx(FxKind.CRYSTAL, strike.x, strike.y, radius = strike.radius * 0.8f, life = 0.6f, color = 0xFFDFF9FF.toInt(), seed = rand.nextInt())
            addBurst(strike.x, strike.y, 18, 0xFFBFF4FF.toInt(), 300f, 0.75f, gravity = -60f)
            addBurst(strike.x, strike.y, 10, 0xFFEAF9FF.toInt(), 200f, 0.55f, gravity = 90f)
            val target = enemies.firstOrNull { it.alive && hypot(it.x - strike.x, it.y - strike.y) < 1f }
            if (target != null) {
                dealAreaDamage(target, strike.damage, strike.radius, strike.element, strike.name, strike.trigger)
            } else {
                for (other in enemies) {
                    if (!other.alive) continue
                    if (hypot(other.x - strike.x, other.y - strike.y) > strike.radius) continue
                    val taken = strike.damage * 0.62
                    other.hp -= taken
                    other.hitFlash = 1f
                    damageDealt += taken
                    triggerDamage += taken
                    creditDamage(strike.name, taken)
                    strike.trigger?.addDamage(taken)
                    addPopup(other.x, other.y - 50f * other.scale, formatShort(taken), false, 0.6f, strike.element.color, 0.9f)
                    if (other.hp <= 0) killEnemy(other)
                }
                addPopup(strike.x, strike.y - 60f, strike.name, false, 0.8f, 0xFFCFEFFF.toInt(), 0.78f)
            }
            shake = max(shake, 6.4f)
            pendingStrikes.removeAt(i)
        }
    }

    private fun onSkillHit(e: Enemy, crit: Boolean, angle: Float) {
        when (stats.fx) {
            SkillEffectType.ICE -> {
                addFx(FxKind.ICE_CONE, e.x, e.y, angle = angle, radius = 58f, life = 0.46f, color = DamageElement.COLD.color)
                addBurst(e.x, e.y, 8, 0xFFBFF4FF.toInt(), 210f, 0.55f)
                addFx(FxKind.RING, e.x, e.y, radius = 34f, life = 0.34f, color = 0xFF9BE8FF.toInt())
                // 착탄점에 육각 결정이 퍼진다
                addFx(FxKind.CRYSTAL, e.x, e.y, radius = 44f, life = 0.44f, color = 0xFFDFF9FF.toInt(), seed = (angle * 1000f).toInt() + 7)
                // 얼음 파편이 주변 적에게도 날아간다
                nearbyEnemies(e, 96f, 2).forEach { other ->
                    val tickDamage = baseHitDamage() * 0.30
                    other.hp -= tickDamage
                    damageDealt += tickDamage
                    creditDamage(skillContext, tickDamage)
                    other.hitFlash = 0.8f
                    addFx(FxKind.BOLT, e.x, e.y, x2 = other.x, y2 = other.y, life = 0.12f, color = 0xFF9BE8FF.toInt(), seed = rand.nextInt(), width = 0.5f)
                    if (other.hp <= 0) killEnemy(other)
                }
            }
            SkillEffectType.LIGHTNING -> {
                // 푸른-보라 갈래 번개 두 줄기가 꽂히고 착탄점에 스파크가 터진다
                val spread = (rand.nextFloat() - 0.5f) * 26f
                val boltSeed = rand.nextInt()
                addFx(
                    FxKind.BOLT, e.x, e.y - 150f,
                    x2 = e.x + spread, y2 = e.y + 20f,
                    life = 0.2f, color = 0xFFDCEBFF.toInt(), seed = boltSeed, width = 1.5f,
                )
                addFx(
                    FxKind.BOLT, e.x - spread * 0.7f, e.y - 120f,
                    x2 = e.x + spread * 0.5f, y2 = e.y + 16f,
                    life = 0.16f, color = 0xFFBFE0FF.toInt(), seed = boltSeed + 977, width = 1.1f,
                )
                addFx(
                    FxKind.SPARK_FLASH, e.x, e.y - 18f, radius = 26f, life = 0.2f,
                    color = 0xFFFFFFFF.toInt(), seed = boltSeed + 31, width = 1.2f,
                )
                addBurst(e.x, e.y, 6, 0xFFFFE272.toInt(), 190f, 0.4f)
                // 연쇄 번개
                val chain = nearbyEnemies(e, 155f, 2)
                var prev = e
                chain.forEachIndexed { index, other ->
                    addFx(FxKind.BOLT, prev.x, prev.y - 24f, x2 = other.x, y2 = other.y - 24f, life = 0.2f, color = 0xFFFFE272.toInt(), seed = rand.nextInt(), width = 1.0f)
                    val chainDamage = baseHitDamage() * (0.55 - index * 0.15)
                    other.hp -= chainDamage
                    damageDealt += chainDamage
                    creditDamage(skillContext, chainDamage)
                    other.hitFlash = 1f
                    addPopup(other.x, other.y - 48f * other.scale, formatShort(chainDamage), false, 0.5f, 0xFFFFE272.toInt(), 0.8f)
                    if (other.hp <= 0) killEnemy(other)
                    prev = other
                }
                if (player.attackCount % 5 == 0) {
                    addFx(FxKind.PILLAR, e.x, e.y, radius = 27f, life = 0.34f, color = 0xFFFFF6C8.toInt())
                    addBurst(e.x, e.y, 10, 0xFFFFFFFF.toInt(), 240f, 0.5f)
                    shake = max(shake, 5.5f)
                }
            }
            SkillEffectType.FIRE -> {
                addFx(FxKind.RING, e.x, e.y, radius = 64f, life = 0.5f, color = 0xFFFF8A3D.toInt())
                addFx(FxKind.RING, e.x, e.y, radius = 36f, life = 0.34f, color = 0xFFFFD27A.toInt(), width = 0.7f)
                addFx(FxKind.FLAME_BURST, e.x, e.y - 14f, radius = 46f, life = 0.46f, color = 0xFFFF7B3C.toInt(), seed = (angle * 791f).toInt() + 11, width = 1f)
                addFx(FxKind.SMOKE, e.x, e.y - 26f, radius = 26f, life = 0.85f, color = 0xFF2A1A14.toInt(), seed = (angle * 517f).toInt() + 23)
                addBurst(e.x, e.y, 12, 0xFFFFA64D.toInt(), 250f, 0.75f, gravity = -90f)
                nearbyEnemies(e, 86f, 3).forEach { other ->
                    val splash = baseHitDamage() * 0.35
                    other.hp -= splash
                    damageDealt += splash
                    creditDamage(skillContext, splash)
                    other.hitFlash = 0.9f
                    if (other.hp <= 0) killEnemy(other)
                }
                shake = max(shake, if (crit) 5.4f else 2.6f)
            }
            SkillEffectType.CHAOS -> {
                e.dot += baseHitDamage() * 0.55
                e.dotTimer = 0.0
                addFx(FxKind.AURA, e.x, e.y, radius = 46f, life = 0.62f, color = DamageElement.CHAOS.color)
                addBurst(e.x, e.y, 9, 0xFFC182FF.toInt(), 150f, 0.8f, gravity = -40f)
                nearbyEnemies(e, 118f, 3).forEach { other ->
                    other.dot += baseHitDamage() * 0.16
                    other.dotTimer = 0.0
                    addFx(FxKind.AURA, other.x, other.y, radius = 32f, life = 0.45f, color = 0x88C182FF.toInt())
                }
            }
            SkillEffectType.MINION -> {
                addBurst(e.x, e.y, 7, 0xFFFFE9B0.toInt(), 170f, 0.45f)
                addFx(FxKind.MINION_HIT, e.x, e.y, radius = 30f, life = 0.3f, color = 0xFFFFE9B0.toInt())
            }
            SkillEffectType.MELEE -> {
                if (crit) {
                    addFx(FxKind.SHOCKWAVE, e.x, e.y, radius = 52f, life = 0.4f, color = 0xFFFFD89A.toInt())
                    addBurst(e.x, e.y, 10, 0xFFFFC24D.toInt(), 240f, 0.55f)
                }
            }
        }
    }

    private fun killEnemy(e: Enemy) {
        if (!e.alive) return
        val frozenDeath = e.freezeT > 0f
        e.alive = false
        e.hp = 0.0
        // 무리 리필은 step 끝에서 한다 (적 목록을 도는 중에 바꾸면 안 된다).
        kills++
        if (frozenDeath) {
            // 동결된 적은 쓰러지는 대신 얼음으로 부서진다.
            e.shattered = true
            playShatter(e)
        } else {
            addBurst(e.x, e.y, if (e.kind == EnemyKind.BOSS) 40 else 12, 0xFFE8D9B8.toInt(), if (e.kind == EnemyKind.BOSS) 420f else 250f, 0.9f)
            addFx(FxKind.RING, e.x, e.y, radius = if (e.kind == EnemyKind.BOSS) 130f else 40f, life = 0.5f, color = 0xFFFFC24D.toInt())
        }
        if (e.kind == EnemyKind.BOSS) shake = 10f
        // 희귀 몬스터를 잡으면 그 힘을 잠시 얻는다 (헤드헌터).
        if (stats.unique.rareKillBuff && (e.kind == EnemyKind.RARE || e.kind == EnemyKind.BOSS)) {
            huntStacks = min(5, huntStacks + 1)
            addPopup(e.x, e.y - 70f * e.scale, "강화 ${huntStacks}중첩", false, 0.9f, 0xFFFFC24D.toInt(), 0.9f)
        }
        if (stats.unique.chargeOnKillPct > 0.0 && rand.nextDouble() * 100.0 < stats.unique.chargeOnKillPct) {
            gainCharge(ChargeKind.POWER)
        }
        if (chargeRules.frenzyOnKill) gainCharge(ChargeKind.FRENZY)
        // 전령류·근접 처치 시 시전은 처치 사건으로 돈다
        fireEvent(TriggerCondition.KILL, null, 0.6)
        // 소환수 빌드에서 적을 잡으면 소환수가 쓰러진 것으로 쳐 준다
        if (stats.fx == SkillEffectType.MINION) fireEvent(TriggerCondition.MINION_DEATH, null, 0.6)
        // 전령 — 처치 직전의 상태(동결·산산조각·감전)로 조건부 발동한다.
        triggerHeralds(e, frozenDeath)
        // 정신력 버프 중 처치 조건이 붙은 것(역병·피의 전령)도 함께 돈다.
        spiritOnKill(e)
    }

    // ------------------------------------------------------------ 정신력 점유 버프

    /**
     * 정신력 점유 버프의 시간 흐름.
     * 단계·연막·저장 피해처럼 시간에 따라 쌓이는 것들을 여기서 돌린다.
     */
    private fun updateSpirit(dt: Float) {
        if (spiritBuffs.isEmpty()) return
        // 켜진 버프가 있으면 발밑에 옅은 오라 고리를 띄운다 (어느 버프인지 색으로 구분).
        spiritAuraTimer -= dt
        if (spiritAuraTimer <= 0f) {
            spiritAuraTimer = 0.9f
            val color = spiritColor(spiritBuffs.first())
            addFx(FxKind.AURA, player.x, player.y, radius = 44f, life = 0.72f, color = color)
        }
        if (barkGuard > 0f) barkGuard = max(0f, barkGuard - dt)

        // 바람의 무희 — 1.3초마다 단계 하나 (최대 3)
        if (spiritOn("Wind Dancer")) {
            windStageTimer += dt
            if (windStageTimer >= 1.3f && windStages < 3f) {
                windStageTimer = 0f
                windStages += 1f
            }
        }
        // 혼의 춤 — 11초마다 연막 하나 (최대 3)
        if (spiritOn("Ghost Dance")) {
            shroudTimer += dt
            if (shroudTimer >= 11f) {
                shroudTimer = 0f
                shrouds = min(3, shrouds + 1)
            }
        }
        // 격노 — 공격을 이어 가면 쌓이고, 적이 없으면 흘러내린다
        if (spiritOn("Savage Fury") || spiritOn("Berserk") || spiritOn("Eternal Rage")) {
            val fighting = enemies.any { it.alive }
            if (fighting) {
                rageStacks = min(20f, rageStacks + dt * effectiveAps().toFloat() * 0.5f)
            } else {
                rageStacks = max(0f, rageStacks - dt * 2f)
            }
            // 야만적인 광분은 유지하는 동안 생명력을 태운다.
            if (spiritOn("Savage Fury") && rageStacks > 0f && player.life > 1.0) {
                player.life -= player.baseLife * 0.03 * dt * (rageStacks / 20f)
            }
        }
        // 소모 — 희귀·고유 적과 붙어 있는 시간만큼 강해진다
        if (spiritOn("Attrition")) {
            val close = enemies.any { it.alive && it.kind != EnemyKind.NORMAL }
            attritionTime = if (close) min(40f, attritionTime + dt) else max(0f, attritionTime - dt * 2f)
        }
        // 원소 융합 — 8초마다 원소가 바뀌고, 그 원소의 피해가 4초 동안 커진다
        if (spiritOn("Elemental Conflux") || spiritOn("Trinity")) {
            confluxPhase = (confluxPhase + dt) % 8f
        }
        // 철의 수호 — 모아 둔 물리 피해가 한계를 넘으면 터진다
        if (ironWardStored > 0.0) {
            ironWardTimer = max(0f, ironWardTimer - dt)
            val limit = max(400.0, (player.baseLife + player.baseEs) * 0.15)
            if (ironWardTimer <= 0f && ironWardStored >= limit) {
                ironWardTimer = 1.2f
                val stored = ironWardStored
                ironWardStored = 0.0
                val radius = 150f
                addFx(FxKind.SHOCKWAVE, player.x, player.y, radius = radius, life = 0.5f, color = 0xFFBFD6E8.toInt(), width = 1.6f)
                val dealt = heraldBlast(player.x, player.y, radius, stored * 2.0, DamageElement.PHYSICAL, "철의 수호", ignite = false)
                noteSpirit("Iron Ward", "철의 수호", dealt)
            }
        }
        // 독성 운반자 — 모아 둔 중독을 3초마다 물리 피해로 터뜨린다
        if (spiritOn("Plague Bearer") && plagueStored > 0.0) {
            plagueTimer -= dt
            if (plagueTimer <= 0f) {
                plagueTimer = 3f
                val burst = plagueStored
                plagueStored = 0.0
                addFx(FxKind.CLOUD, player.x, player.y, radius = 96f, life = 0.8f, color = 0xFF6FBF6A.toInt())
                val dealt = heraldBlast(player.x, player.y, 96f, burst, DamageElement.PHYSICAL, "독성 운반자", ignite = false)
                noteSpirit("Plague Bearer", "독성 운반자", dealt)
            }
        }
        // 가시밭 — 가까이 온 적을 계속 찌른다
        if (spiritOn("Briarpatch")) {
            briarTimer -= dt
            if (briarTimer <= 0f) {
                briarTimer = 0.7f
                val hit = baseHitDamage() * 0.12
                val dealt = heraldBlast(player.x, player.y, 120f, hit, DamageElement.PHYSICAL, "가시밭", ignite = false)
                if (dealt > 0.0) noteSpirit("Briarpatch", "가시밭", dealt)
            }
        }
        // 마름쇠 — 회피로 흘린 뒤에도 자리가 남아 있으면 마저 밟힌다
        if (caltropTimer > 0f) caltropTimer = max(0f, caltropTimer - dt)
    }

    /** 정신력 버프 하나가 낸 피해를 적립한다 */
    private fun noteSpirit(en: String, label: String, damage: Double) {
        if (damage <= 0.0 || !damage.isFinite()) return
        spiritDamage += damage
        spiritDamageByName[label] = (spiritDamageByName[label] ?: 0.0) + damage
        spiritProcs[en] = (spiritProcs[en] ?: 0) + 1
    }

    /** 정신력 젬의 오라 색 */
    private fun spiritColor(spec: SpiritSpec): Int = when (spec.gem.family) {
        "냉기" -> 0xFF9BE8FF.toInt()
        "번개" -> 0xFFFFE272.toInt()
        "화염" -> 0xFFFF8A3D.toInt()
        "카오스" -> 0xFF9C7BFF.toInt()
        "물리" -> 0xFFD9C08A.toInt()
        else -> 0xFFB8A16A.toInt()
    }

    /** 플레이어가 피해를 받으면 도는 정신력 버프 반응 (껍질피부·혼의 춤·바람의 무희·마그마 장벽) */
    private fun spiritOnHitTaken(dmg: Double, element: DamageElement) {
        if (spiritBuffs.isEmpty()) return
        if (spiritOn("Barkskin")) {
            barkGuard = 6f
            addFx(FxKind.RING, player.x, player.y, radius = 40f, life = 0.4f, color = 0xFF9FB48A.toInt())
        }
        if (spiritOn("Ghost Dance") && shrouds > 0) {
            shrouds--
            val gain = (player.baseEs - player.es).coerceAtLeast(0.0) * 0.35 + 6.0
            if (gain > 0.0) {
                player.es += gain
                player.esDelay = max(player.esDelay, stats.esRechargeDelay.toFloat() * 0.5f)
                addPopup(player.x, player.y - 74f, "유령 연막", false, 0.7f, 0xFFB8D8FF.toInt(), 0.8f)
                addFx(FxKind.AURA, player.x, player.y, radius = 38f, life = 0.5f, color = 0xFFB8D8FF.toInt())
            }
        }
        if (spiritOn("Wind Dancer") && windStages > 0f) {
            val stages = windStages
            windStages = 0f
            windStageTimer = 0f
            val dealt = heraldBlast(
                player.x, player.y, 92f, baseHitDamage() * 0.5 * stages,
                DamageElement.PHYSICAL, "강풍의 힘", ignite = false,
            )
            addFx(FxKind.WING_GUST, player.x, player.y, radius = 92f, life = 0.5f, color = 0xFFCFE8E0.toInt(), width = 1.5f)
            noteSpirit("Wind Dancer", "바람의 무희", dealt)
        }
        if (spiritOn("Magma Barrier")) {
            val dealt = heraldBlast(
                player.x, player.y, 112f, baseHitDamage() * 0.25,
                DamageElement.FIRE, "마그마 장벽", ignite = true,
            )
            addFx(FxKind.FLAME_BURST, player.x, player.y, radius = 112f, life = 0.4f, color = 0xFFFF8A3D.toInt(), width = 0.8f)
            noteSpirit("Magma Barrier", "마그마 장벽", dealt)
        }
        if (element == DamageElement.PHYSICAL && spiritOn("Iron Ward")) {
            ironWardStored += dmg * 0.6
        }
    }

    /** 회피 — 마름쇠의 발자취가 자리를 남긴다 */
    private fun spiritOnDodge() {
        if (!spiritOn("Trail of Caltrops")) return
        if (caltropTimer > 0f) return
        caltropTimer = 0.4f
        addFx(FxKind.DUST, player.x, player.y, radius = 34f, life = 0.35f, color = 0xFFB9A982.toInt())
        val dealt = heraldBlast(
            player.x, player.y, 62f, baseHitDamage() * 0.15,
            DamageElement.PHYSICAL, "마름쇠의 발자취", ignite = false,
        )
        noteSpirit("Trail of Caltrops", "마름쇠의 발자취", dealt)
    }

    /** 플레이어 공격이 들어갈 때 정신력 버프가 함께 쌓는다 (독성 운반자) */
    private fun spiritOnStrike(damage: Double) {
        if (spiritBuffs.isEmpty()) return
        if (spiritOn("Plague Bearer")) {
            val cap = baseHitDamage() * 30.0
            plagueStored = min(cap, plagueStored + damage * 0.04)
        }
    }

    /** 적이 쓰러질 때 — 역병·피의 전령이 남은 자리에 폭발을 남긴다 */
    private fun spiritOnKill(e: Enemy) {
        if (spiritBuffs.isEmpty()) return
        if (spiritOn("Herald of Plague")) {
            addFx(FxKind.CLOUD, e.x, e.y, radius = 96f, life = 0.7f, color = 0xFF6FBF6A.toInt())
            val dealt = heraldBlast(e.x, e.y, 96f, baseHitDamage() * 0.4, DamageElement.CHAOS, "역병의 전령", ignite = false)
            noteSpirit("Herald of Plague", "역병의 전령", dealt)
        }
        if (spiritOn("Herald of Blood")) {
            addFx(FxKind.IMPACT, e.x, e.y, radius = 84f, life = 0.45f, color = 0xFFC23030.toInt(), width = 1.4f)
            val dealt = heraldBlast(e.x, e.y, 84f, baseHitDamage() * 0.6, DamageElement.PHYSICAL, "피의 전령", ignite = false)
            noteSpirit("Herald of Blood", "피의 전령", dealt)
        }
    }

    /** 격노·소모·원소 융합이 지금 주는 피해 배율 */
    private fun spiritHitMul(): Double {
        if (spiritBuffs.isEmpty()) return 1.0
        var mul = 1.0
        if (rageStacks > 0f) mul *= 1.0 + rageStacks * 0.012
        if (spiritOn("Attrition")) mul *= 1.0 + (attritionTime / 40.0).coerceIn(0.0, 1.0) * 0.49
        if (confluxPhase < 4f && (spiritOn("Elemental Conflux") || spiritOn("Trinity"))) mul *= 1.49
        return mul
    }

    /** HUD에 올릴 정신력 버프 한 줄 */
    fun spiritHud(): String {
        if (spiritBuffs.isEmpty()) return ""
        if (spiritDamage <= 0.0) return stats.spiritBuffs.summary
        val parts = spiritBuffs.take(3).map { spec ->
            val procs = spiritProcs[spec.gem.en] ?: 0
            if (procs > 0) "${spec.name} ${procs}회" else spec.name
        }
        return parts.joinToString(" · ")
    }

    /** 정신력 버프별 결과 기록 (발동한 것만) */
    fun spiritReports(): List<SpiritReport> = spiritBuffs.mapNotNull { spec ->
        val label = spiritBuffs.firstOrNull { it === spec }?.name ?: spec.name
        val damage = spiritDamageByName[label] ?: 0.0
        val procs = spiritProcs[spec.gem.en] ?: 0
        if (procs <= 0 && damage <= 0.0) null else SpiritReport(spec.name, procs, damage)
    }

    /**
     * 처치 사건 하나로 전령 3종의 조건을 살핀다.
     *
     * 얼음의 전령은 동결·산산조각 난 적에게서, 천둥의 전령은 감전된 적에게서,
     * 재의 전령은 초과 피해만큼 터진다. 각 전령은 자기 이름으로 피해를 적립하므로
     * 스킬별 DPS 기록과 전체 피해 합계가 그대로 맞는다.
     */
    private fun triggerHeralds(e: Enemy, frozenDeath: Boolean) {
        if (heralds.isEmpty()) return
        val hit = baseHitDamage()
        val ice = stats.heralds.spec(HeraldKind.ICE)
        if (ice != null && (frozenDeath || e.shattered)) heraldIce(ice, e, hit)
        val thunder = stats.heralds.spec(HeraldKind.THUNDER)
        if (thunder != null && e.shockT > 0f) heraldThunder(thunder, e, hit)
        val ash = stats.heralds.spec(HeraldKind.ASH)
        if (ash != null) heraldAsh(ash, e, hit, e.overkill)
    }

    /** 얼음의 전령 — 처치된 자리에 육각 서리 파동과 범위 냉기 피해 */
    private fun heraldIce(spec: HeraldSpec, e: Enemy, hit: Double) {
        val radius = HeraldMath.radius(HeraldKind.ICE, spec)
        addFx(FxKind.HERALD_WAVE, e.x, e.y, radius = radius, life = 0.62f, color = 0xFF8FE6FF.toInt(), width = 1.8f)
        addFx(FxKind.FROST_NOVA, e.x, e.y, radius = radius * 0.92f, life = 0.58f, color = 0xFFBFF4FF.toInt())
        addFx(FxKind.CRYSTAL, e.x, e.y, radius = radius * 0.6f, life = 0.6f, color = 0xFFDFF9FF.toInt())
        addBurst(e.x, e.y - 12f, 18, 0xFFCFEFFF.toInt(), 260f, 0.8f, gravity = 70f)
        addPopup(e.x, e.y - 78f * e.scale, spec.name, false, 0.9f, 0xFFBFF4FF.toInt(), 0.84f)
        val dealt = heraldBlast(
            x = e.x,
            y = e.y,
            radius = radius,
            damage = HeraldMath.iceDamage(hit, spec),
            element = DamageElement.COLD,
            label = spec.name,
            ignite = false,
        )
        noteHerald(spec, dealt)
    }

    /** 천둥의 전령 — 감전된 적이 터지면 주변으로 연쇄 낙뢰 */
    private fun heraldThunder(spec: HeraldSpec, e: Enemy, hit: Double) {
        val radius = HeraldMath.radius(HeraldKind.THUNDER, spec)
        val base = HeraldMath.thunderDamage(hit, spec)
        addFx(FxKind.HERALD_WAVE, e.x, e.y, radius = radius, life = 0.55f, color = 0xFFFFE272.toInt(), width = 1.6f)
        addFx(FxKind.PILLAR, e.x, e.y, radius = 26f, life = 0.36f, color = 0xFFFFF3A8.toInt())
        addFx(FxKind.ARC_WEB, e.x, e.y - 26f, radius = radius * 0.8f, life = 0.4f, color = 0xFFFFE272.toInt())
        addBurst(e.x, e.y - 30f, 16, 0xFFFFF3A8.toInt(), 280f, 0.55f)
        addPopup(e.x, e.y - 78f * e.scale, spec.name, false, 0.9f, 0xFFFFF3A8.toInt(), 0.84f)
        shake = max(shake, 5.5f)
        var total = 0.0
        var struck = 0
        for (other in enemies) {
            if (!other.alive) continue
            if (hypot(other.x - e.x, other.y - e.y) > radius) continue
            val arcAngle = atan2(other.y - e.y, other.x - e.x)
            addFx(
                FxKind.BOLT, e.x, e.y - 30f,
                x2 = other.x, y2 = other.y - 24f,
                life = 0.2f, color = 0xFFFFF3A8.toInt(), width = 1.3f,
            )
            addFx(FxKind.SPARK_FLASH, other.x, other.y - 26f * other.scale, radius = 18f, life = 0.16f, color = 0xFFFFFFFF.toInt(), width = 1.1f)
            val share = if (struck == 0) 1.0 else HeraldMath.SPLASH_RATIO
            total += heraldHit(other, base * share, DamageElement.LIGHTNING, spec.name, arcAngle)
            struck++
            if (struck >= 4) break
        }
        noteHerald(spec, total)
    }

    /** 재의 전령 — 처치의 초과 피해만큼 화염 폭발, 주변 적 점화 */
    private fun heraldAsh(spec: HeraldSpec, e: Enemy, hit: Double, overkill: Double) {
        val radius = HeraldMath.radius(HeraldKind.ASH, spec)
        addFx(FxKind.HERALD_WAVE, e.x, e.y, radius = radius, life = 0.6f, color = 0xFFFF7B3C.toInt(), width = 1.6f)
        addFx(FxKind.FLAME_BURST, e.x, e.y - 14f, radius = radius * 0.8f, life = 0.62f, color = 0xFFFF7B3C.toInt(), width = 1.4f)
        addFx(FxKind.SMOKE, e.x, e.y - 30f, radius = radius * 0.55f, life = 0.9f, color = 0xFF2A1A14.toInt())
        addBurst(e.x, e.y - 18f, 16, 0xFFFFA64D.toInt(), 250f, 0.8f, gravity = -80f)
        addPopup(e.x, e.y - 78f * e.scale, spec.name, false, 0.9f, 0xFFFFB066.toInt(), 0.84f)
        val dealt = heraldBlast(
            x = e.x,
            y = e.y,
            radius = radius,
            damage = HeraldMath.ashDamage(hit, overkill, spec),
            element = DamageElement.FIRE,
            label = spec.name,
            ignite = spec.appliesAilment,
        )
        noteHerald(spec, dealt)
    }

    /** 전령 폭발 한 번 — 범위 안 적에게 피해를 넣고, 필요하면 불을 붙인다 */
    private fun heraldBlast(
        x: Float,
        y: Float,
        radius: Float,
        damage: Double,
        element: DamageElement,
        label: String,
        ignite: Boolean,
    ): Double {
        var total = 0.0
        var hits = 0
        for (other in enemies) {
            if (!other.alive) continue
            if (hypot(other.x - x, other.y - y) > radius) continue
            val share = if (hits == 0) 1.0 else HeraldMath.SPLASH_RATIO
            total += heraldHit(other, damage * share, element, label, atan2(other.y - y, other.x - x))
            if (ignite && other.alive) {
                other.igniteT = max(other.igniteT, Ailments.IGNITE_TIME)
                other.igniteDps = max(other.igniteDps, damage * 0.18)
            }
            hits++
        }
        if (hits == 0) {
            // 맞은 적이 없어도 발동은 했으므로 이름만 띄운다.
            addPopup(x, y - 60f, label, false, 0.7f, element.color, 0.7f)
        }
        return total
    }

    /** 전령 피해 한 건 — 적립과 함께 상태이상·연출을 붙인다 */
    private fun heraldHit(e: Enemy, damage: Double, element: DamageElement, label: String, angle: Float): Double {
        val taken = applyShield(e, damage)
        e.hp -= taken
        damageDealt += taken
        creditDamage(label, taken)
        e.hitFlash = 1f
        addPopup(
            e.x + (rand.nextFloat() - 0.5f) * 16f,
            e.y - 48f * e.scale,
            formatShort(taken), false, 0.55f, element.color, 0.88f,
        )
        when (element) {
            DamageElement.COLD -> addFx(FxKind.ICE_CONE, e.x, e.y, angle = angle, radius = 34f, life = 0.3f, color = 0xFFBFF4FF.toInt())
            DamageElement.LIGHTNING -> addFx(FxKind.SPARK_FLASH, e.x, e.y - 26f * e.scale, radius = 16f, life = 0.15f, color = 0xFFFFFFFF.toInt(), width = 1.2f)
            DamageElement.FIRE -> addFx(FxKind.FLAME_BURST, e.x, e.y - 18f, radius = 30f, life = 0.35f, color = 0xFFFF8A3D.toInt(), width = 0.6f)
            else -> Unit
        }
        if (e.hp <= 0) killEnemy(e)
        return taken
    }

    /**
     * 천둥의 전령 — 감전이 걸릴 때마다 그 적에게 낙뢰 한 줄기.
     *
     * 피해는 '감전을 건 그 타격'을 기준으로 잡는다. 예전에는 주력 타격을 매번 다시 계산했는데,
     * PoB가 DPS를 상한(2.1B)으로 내보내는 빌드에서는 그 한 방이 수억이 되어
     * 전령만 전체 피해의 90% 넘게 차지하는 결과가 나왔다.
     */
    private fun heraldThunderOnShock(e: Enemy, hitDamage: Double = 0.0) {
        val spec = stats.heralds.spec(HeraldKind.THUNDER) ?: return
        if (thunderOnShockCooldown > 0f) return
        thunderOnShockCooldown = 0.4f
        val single = (if (hitDamage.isFinite() && hitDamage > 0.0) hitDamage else baseHitDamage())
            .coerceAtMost(baseHitDamage() * 1.5)
        val base = HeraldMath.thunderDamage(single, spec) * 0.6
        addFx(FxKind.BOLT, e.x + 26f, e.y - 210f, x2 = e.x, y2 = e.y - 22f, life = 0.18f, color = 0xFFFFF3A8.toInt(), width = 1.5f)
        addFx(FxKind.PILLAR, e.x, e.y, radius = 18f, life = 0.3f, color = 0xFFFFF3A8.toInt())
        addBurst(e.x, e.y - 24f, 8, 0xFFFFF3A8.toInt(), 220f, 0.42f)
        val dealt = heraldHit(e, base, DamageElement.LIGHTNING, spec.name, 0f)
        noteHerald(spec, dealt)
    }

    /** 전령 발동 한 번을 센다 */
    private fun noteHerald(spec: HeraldSpec, damage: Double) {
        heraldProcs[spec.kind] = (heraldProcs[spec.kind] ?: 0) + 1
        heraldDamageByName[spec.kind] = (heraldDamageByName[spec.kind] ?: 0.0) + damage
        heraldProcsTotal++
        heraldDamage += damage
    }

    /** 동결된 적의 산산조각 — 얼음 파편이 사방으로 튀고 시체가 얼음 가루로 흩어진다 */
    private fun playShatter(e: Enemy) {
        shatters++
        val shards = Ailments.shatterShards(e.kind)
        val seed = ((e.slotAngle * 1000f).toInt() and 0x7FFFFFF) + shatters * 31
        addFx(FxKind.SHATTER, e.x, e.y - 24f * e.scale, radius = 46f * e.scale, life = 0.7f, color = 0xFFDFF9FF.toInt(), seed = seed)
        addFx(FxKind.FROST_NOVA, e.x, e.y, radius = 44f * e.scale, life = 0.5f, color = 0xFF9BE8FF.toInt(), seed = seed + 7)
        addFx(FxKind.RING, e.x, e.y, radius = 52f * e.scale, life = 0.42f, color = 0xFFFFFFFF.toInt(), seed = seed + 11, width = 0.8f)
        addBurstPattern(e.x, e.y - 30f * e.scale, shards, 0xFFE8FCFF.toInt(), 330f, 0.95f, gravity = 420f, seed = seed)
        addBurstPattern(e.x, e.y - 20f * e.scale, shards / 2, 0xFF9BE8FF.toInt(), 210f, 0.8f, gravity = -60f, seed = seed + 5)
        addPopup(e.x, e.y - 70f * e.scale, "산산조각", false, 0.8f, 0xFFCFEFFF.toInt(), 0.86f)
        shake = max(shake, if (e.kind == EnemyKind.BOSS) 9f else 3.6f)
    }

    /** 받는 피해 계산 결과 (전직 감폭·전환 확인용) */
    internal fun incomingDamage(raw: Double, element: DamageElement): Double =
        damageAfterAscendancy(raw, element)

    /** 전직 효과를 반영해 받는 피해를 줄인다 (감폭·물리→원소 전환·회피의 원소 감소) */
    private fun damageAfterAscendancy(raw: Double, element: DamageElement): Double {
        // 충전 조절이 유지 중이면 인내 충전이 있는 동안 받는 피해가 줄어든다.
        val guard = if (chargeRules.regulation && enduranceCharges > 0) chargeRules.regulationDefencePct else 0.0
        // 껍질피부 — 피격 직후 6초 동안 방어도가 올라 받는 피해가 줄어든다.
        val bark = if (barkGuard > 0f) 12.0 else 0.0
        val less = (stats.damageTakenLessPct + stats.unique.damageTakenLessPct + guard + bark).coerceIn(0.0, 80.0) / 100.0
        var total = raw * (1.0 - less)
        val elementalReduction = if (element == DamageElement.PHYSICAL) {
            0.0
        } else {
            (stats.evade * stats.evasionAsElementalPct / 200.0).coerceIn(0.0, 30.0) / 100.0
        }
        fun res(part: Double, type: DamageElement): Double {
            val mit = when (type) {
                DamageElement.PHYSICAL -> stats.physRed
                DamageElement.FIRE -> stats.fireRes
                DamageElement.COLD -> stats.coldRes
                DamageElement.LIGHTNING -> stats.lightRes
                DamageElement.CHAOS -> stats.chaosRes
            }
            val cut = (mit.coerceIn(-60.0, 90.0) / 100.0 + elementalReduction).coerceAtMost(0.9)
            return part * (1.0 - cut)
        }
        if (element == DamageElement.PHYSICAL) {
            val parts = listOf(
                DamageElement.FIRE to stats.physTakenAsFirePct,
                DamageElement.COLD to stats.physTakenAsColdPct,
                DamageElement.LIGHTNING to stats.physTakenAsLightningPct,
                DamageElement.CHAOS to stats.physTakenAsChaosPct,
            ).filter { it.second > 0.0 }
            if (parts.isEmpty()) return res(total, DamageElement.PHYSICAL)
            val convertedPct = parts.sumOf { it.second }.coerceIn(0.0, 100.0)
            var sum = res(total * (1.0 - convertedPct / 100.0), DamageElement.PHYSICAL)
            parts.forEach { (type, pct) ->
                sum += res(total * pct / 100.0, type)
            }
            return sum
        }
        return res(total, element)
    }

    /** 빌드 주력 스킬 이름 — 이름이 비어 있으면 이펙트 계열로 대신한다 */
    val mainSkillName: String get() = stats.skillName.ifBlank { stats.fx.label }

    /** 피해를 넣는 동안 어느 스킬 몫인지 바꿔 둔다 */
    private inline fun <T> underSkill(name: String, block: () -> T): T {
        val prev = skillContext
        skillContext = name
        try {
            return block()
        } finally {
            skillContext = prev
        }
    }

    /** 스킬 하나에 피해를 적립한다 */
    private fun creditDamage(name: String, amount: Double) {
        if (amount <= 0.0 || !amount.isFinite()) return
        val key = name.ifBlank { mainSkillName }
        val row = skillRows[key] ?: SkillDamageRow(key).also { skillRows[key] = it }
        row.damage += amount
        row.hits++
    }

    /** 스킬별 누적 피해·실전 DPS·비율 — 피해가 큰 순서 */
    fun skillTally(): List<SkillDamageReport> =
        SkillTally.rank(skillRows.values, elapsed, damageDealt)

    /** 적 이름 — 보스는 보스 이름, 잡몹은 종류로 적는다 */
    private fun enemyTitle(e: Enemy): String = e.boss?.name ?: when (e.kind) {
        EnemyKind.BOSS -> "보스"
        EnemyKind.RARE -> "희귀 몬스터"
        EnemyKind.MAGIC -> "마법 몬스터"
        EnemyKind.NORMAL -> "일반 몬스터"
    }

    /** 플레이어가 맞은 피해를 남긴다 — 사망 원인과 직전 피해 요약에 쓴다 */
    private fun recordHit(source: String, skill: String, element: DamageElement, damage: Double, poolBefore: Double) {
        if (damage <= 0.0 || !damage.isFinite()) return
        val attacker = source.ifBlank { "몬스터" }
        val attack = skill.ifBlank { "공격" }
        lastHitSource = attacker
        lastHitSkill = attack
        lastHitDamage = damage
        damageTraces.add(DamageTrace(attacker, attack, element, damage, elapsed, poolBefore))
        while (damageTraces.size > 12) damageTraces.removeAt(0)
    }

    /** 마지막 한 방을 사망 원인으로 정리한다 */
    private fun buildDeathCause(): DeathCause? {
        val fatal = damageTraces.lastOrNull() ?: return null
        return DeathCause(
            killer = fatal.source,
            skill = fatal.skill,
            element = fatal.element,
            damage = fatal.damage,
            at = fatal.at,
            poolBefore = fatal.poolBefore,
            maxPool = maxPool,
            recent = damageTraces.filter { it.at >= fatal.at - 6.0 }.takeLast(6),
        )
    }

    /**
     * 사망 연출 — 붉은 충격파와 파편이 터지고 몸에서 빠져나온 영혼이 위로 흩어진다.
     * 쓰러지는 몸과 어두워지는 화면은 캔버스가 [deathAge]에 맞춰 그린다.
     */
    private fun playDeathEffect() {
        deathAge = 0f
        addFx(FxKind.SHOCKWAVE, player.x, player.y, radius = 126f, life = 1.0f, color = 0xFFFF3B3B.toInt(), width = 2.2f)
        addFx(FxKind.RING, player.x, player.y - 12f, radius = 62f, life = 0.85f, color = 0xFFFF7A6A.toInt(), width = 1.6f)
        addFx(FxKind.IMPACT, player.x, player.y, radius = 76f, life = 0.6f, color = 0xFFFF6A5A.toInt(), width = 1.4f)
        addFx(FxKind.GROUND_CRACK, player.x, player.y, radius = 96f, life = 1.2f, color = 0xFFB03030.toInt())
        addFx(FxKind.AURA, player.x, player.y - 26f, radius = 98f, life = 1.6f, color = 0xFF7A2A2A.toInt())
        // 튀는 파편 — 무거운 조각과 가벼운 불티를 섞는다
        addBurst(player.x, player.y - 8f, 30, 0xFFFF5A4A.toInt(), 320f, 1.3f, gravity = 420f)
        addBurst(player.x, player.y - 20f, 16, 0xFFFFC0A8.toInt(), 210f, 1.0f, gravity = 250f)
        // 위로 떠오르는 영혼
        addBurst(player.x, player.y - 42f, 18, 0xFFD8E8FF.toInt(), 120f, 1.9f, gravity = -150f)
        addBurst(player.x, player.y - 48f, 10, 0xFF9CD0FF.toInt(), 80f, 2.3f, gravity = -110f)
        addPopup(player.x, player.y - 98f, "사망", true, 1.6f, 0xFFFF5A5A.toInt(), 1.2f)
        shake = max(shake, 15f)
        hurtFlash = 1f
    }

    /**
     * 클리어 연출 — 플레이어 발밑에서 금빛 충격파가 퍼지고, 청백빛 빛기둥이 솟으며
     * 불꽃 파편이 사방으로 흩어진다. 사망 연출([playDeathEffect])과 정확히 반대 결이다.
     */
    private fun playVictoryEffect() {
        victoryAge = 0f
        val gold = 0xFFFFD36A.toInt()
        val pale = 0xFFDCF4FF.toInt()
        // 금빛 이중 충격파
        addFx(FxKind.VICTORY_WAVE, player.x, player.y, radius = 190f, life = 1.0f, color = gold, width = 2.4f)
        addFx(FxKind.SHOCKWAVE, player.x, player.y, radius = 132f, life = 0.85f, color = gold, width = 1.8f)
        addFx(FxKind.RING, player.x, player.y - 10f, radius = 88f, life = 0.7f, color = pale, width = 1.4f)
        addFx(FxKind.RING, player.x, player.y - 10f, radius = 58f, life = 0.55f, color = 0xFFFFFFFF.toInt(), width = 1f)
        // 솟는 빛기둥과 후광
        addFx(FxKind.PILLAR, player.x, player.y, radius = 54f, life = 0.95f, color = gold)
        addFx(FxKind.PILLAR, player.x - 30f, player.y - 6f, radius = 26f, life = 0.7f, color = pale)
        addFx(FxKind.PILLAR, player.x + 30f, player.y - 6f, radius = 26f, life = 0.7f, color = pale)
        addFx(FxKind.AURA, player.x, player.y - 24f, radius = 120f, life = 1.2f, color = 0xFF8FD4FF.toInt())
        // 흩날리는 불꽃 파편 — 무거운 금빛 조각과 가벼운 청백 불티
        addBurst(player.x, player.y - 12f, 34, gold, 330f, 1.35f, gravity = -120f)
        addBurst(player.x, player.y - 30f, 22, pale, 230f, 1.5f, gravity = -180f)
        addBurst(player.x, player.y - 40f, 14, 0xFFFFFFFF.toInt(), 150f, 1.7f, gravity = -240f)
        addPopup(player.x, player.y - 104f, "클리어", true, 1.8f, gold, 1.25f)
        shake = max(shake, 9f)
    }

    /** 클리어 요약 — 총 시간·총 피해·평균 DPS·처치와 스킬별 기록 */
    fun clearSummary(): ClearSummary {
        val seconds = max(0.1, elapsed)
        return ClearSummary(
            seconds = seconds,
            damage = damageDealt,
            dps = damageDealt / seconds,
            kills = kills,
            total = totalEnemies,
            skills = skillTally(),
        )
    }

    /** 전령 발동 현황 (HUD 한 줄, 예: "얼음의 전령 12회 · 천둥의 전령 3회") */
    fun heraldHud(): String {
        if (stats.heralds.isEmpty) return ""
        if (heraldProcsTotal == 0) return stats.heralds.summary
        val parts = stats.heralds.active.map { spec ->
            val procs = heraldProcs[spec.kind] ?: 0
            "${spec.name} ${procs}회"
        }
        return parts.joinToString(" · ")
    }

    /** 전령별 결과 기록 (발동한 것만) */
    fun heraldReports(): List<HeraldReport> = stats.heralds.specs.mapNotNull { spec ->
        val procs = heraldProcs[spec.kind] ?: 0
        if (procs <= 0) null
        else HeraldReport(spec.kind, spec.name, procs, heraldDamageByName[spec.kind] ?: 0.0)
    }

    private fun hitPlayer(
        raw: Double,
        element: DamageElement,
        canEvade: Boolean,
        fromX: Float,
        fromY: Float,
        heavy: Boolean = false,
        /** 때린 쪽 이름과 기술 — 비어 있으면 이름 없는 공격으로 남긴다 */
        source: String = "",
        skill: String = "",
    ) {
        if (state != State.RUNNING) return
        if (canEvade && rand.nextDouble() * 100.0 < stats.evade) {
            evades++
            addPopup(player.x + (rand.nextFloat() - 0.5f) * 16f, player.y - 62f, "회피", false, 0.7f, 0xFF8CE8C0.toInt(), 0.86f)
            fireEvent(TriggerCondition.DODGE, null, 1.0)
            spiritOnDodge()
            return
        }
        val dmg = damageAfterAscendancy(raw, element)
        val poolBefore = player.life + player.es
        var rest = dmg
        if (player.es > 0) {
            val absorbed = min(player.es, rest)
            player.es -= absorbed
            rest -= absorbed
        }
        player.life -= rest
        player.esDelay = stats.esRechargeDelay.toFloat()
        player.hurtFlash = 1f
        hitsTaken++
        damageTaken += dmg
        fireEvent(TriggerCondition.HIT_TAKEN, null, (dmg / maxPool * 8.0).coerceIn(0.3, 3.0))
        addPopup(
            player.x + (rand.nextFloat() - 0.5f) * 18f,
            player.y - 64f,
            formatShort(dmg),
            false,
            0.62f,
            0xFFFF7A7A.toInt(),
            if (heavy) 1.1f else 0.86f,
        )
        recordHit(source, skill, element, dmg, poolBefore)
        spiritOnHitTaken(dmg, element)
        if (chargeRules.enduranceOnHit) gainCharge(ChargeKind.ENDURANCE)
        shake = max(shake, if (heavy) 8f else 2.2f)
        addFx(FxKind.SLASH, fromX, fromY, angle = atan2(player.y - fromY, player.x - fromX), radius = 34f, life = 0.18f, color = 0xFFFF6A6A.toInt())
    }

    // ------------------------------------------------------------ 투사체

    /** 투사체가 적에게 꿉었을 때 — 화살·구딴 종류별로 터지는 모양이 다르다 */
    private fun onProjectileHit(p: Projectile, t: Enemy) {
        val angle = atan2(p.dirY, p.dirX)
        if (p.damage > 0.0) hitEnemy(t, p.damage, p.crit, angle)
        when (p.kind) {
            ProjKind.LIGHT_ARROW -> chainBurst(t, p.damage)
            ProjKind.ICE_ARROW -> iceShards(t)
            ProjKind.FIRE_ORB -> fireballBurst(t, p.damage)
            ProjKind.OIL -> {
                oilImpact(p.x, p.y, if (p.oilRadius > 0f) p.oilRadius else aoe(62f), p.damage)
                addFx(FxKind.OIL_SPLASH, p.x, p.y, radius = 30f, life = 0.4f, color = 0xFF8C7433.toInt())
            }
            ProjKind.CHARGED_OIL -> {
                // 전기를 띤 기름은 기름 지대를 만들지 않고 주변에 번개가 튄다.
                val arc = atan2(t.y - p.y, t.x - p.x)
                addFx(FxKind.BOLT, p.x, p.y, x2 = t.x, y2 = t.y - 18f, life = 0.18f, color = 0xFFFFF3A8.toInt(), seed = rand.nextInt(), width = 1f)
                addFx(FxKind.ARC_WEB, t.x, t.y - 14f, angle = arc, radius = 54f, life = 0.26f, color = 0xFFFFE272.toInt())
                nearbyEnemies(t, 120f, 2).forEach { other ->
                    addFx(FxKind.BOLT, t.x, t.y - 20f, x2 = other.x, y2 = other.y - 16f, life = 0.16f, color = 0xFFFFF3A8.toInt(), seed = rand.nextInt(), width = 0.8f)
                    hitEnemy(other, p.damage * 0.4, false, angle, 0.8f)
                }
                addBurst(t.x, t.y - 18f, 6, 0xFFFFE272.toInt(), 190f, 0.34f)
                if (p.oilRadius > 0f) oilImpact(p.x, p.y, p.oilRadius, p.damage, charged = true)
            }
            else -> Unit
        }
    }

    /** 번개 화살 착탄 — 주변 적에게 연쇄 번개가 뻗는다 */
    private fun chainBurst(from: Enemy, damage: Double) {
        var prev = from
        val victims = nearbyEnemies(from, 175f, 4)
        victims.forEachIndexed { index, other ->
            // 갈래가 갈라지는 번개 그물 + 굵은 줄기
            addFx(
                FxKind.BOLT, prev.x, prev.y - 28f, x2 = other.x, y2 = other.y - 22f,
                life = 0.22f, color = 0xFFFFF3A8.toInt(), seed = rand.nextInt(), width = 1.15f,
            )
            addFx(
                FxKind.ARC_WEB, other.x, other.y - 16f, angle = atan2(other.y - prev.y, other.x - prev.x),
                radius = 44f, life = 0.24f, color = 0xFFFFE272.toInt(),
            )
            val arc = damage * (0.6 - index * 0.11)
            if (arc > 0.0) hitEnemy(other, arc, false, 0f, 0.8f)
            addFx(FxKind.PILLAR, other.x, other.y, radius = 18f, life = 0.26f, color = 0xFFFFF6C8.toInt(), width = 0.7f)
            addBurst(other.x, other.y - 18f, 6, 0xFFFFE272.toInt(), 180f, 0.35f)
            prev = other
        }
        addFx(FxKind.PILLAR, from.x, from.y, radius = 28f, life = 0.32f, color = 0xFFFFF6C8.toInt())
        addFx(FxKind.RING, from.x, from.y, radius = 74f, life = 0.34f, color = 0xFFFFE272.toInt(), width = 1.2f)
        addBurst(from.x, from.y - 16f, 10, 0xFFFFF3A8.toInt(), 220f, 0.42f)
    }

    /** 얼음 화살 착탄 — 부채골로 얼음 파편이 날아간다 */
    private fun iceShards(from: Enemy) {
        val base = atan2(player.y - from.y, player.x - from.x) + PI_F
        addFx(FxKind.ICE_CONE, from.x, from.y, angle = base, radius = 88f, life = 0.55f, color = DamageElement.COLD.color)
        addFx(FxKind.FROST_NOVA, from.x, from.y - 6f, radius = 82f, life = 0.5f, color = 0xFFBFF4FF.toInt())
        addFx(FxKind.CRYSTAL, from.x, from.y, angle = base, radius = 72f, life = 0.6f, color = 0xFFDFF9FF.toInt(), seed = rand.nextInt())
        val fan = nearbyEnemies(from, 150f, 4)
        repeat(6) { i ->
            val a = base + (i - 2.5f) * 0.24f
            val victim = fan.getOrNull(i % (fan.size.coerceAtLeast(1)))
            addProjectile(
                Projectile(
                    x = from.x, y = from.y - 16f,
                    dirX = cos(a), dirY = sin(a), speed = 520f + i * 20f,
                    damage = 0.0, crit = false, target = victim, hostile = false,
                    element = DamageElement.COLD, kind = ProjKind.ICE_SHARD, life = 0.55f,
                )
            )
        }
        addBurst(from.x, from.y - 16f, 12, 0xFFBFF4FF.toInt(), 220f, 0.55f)
    }

    /** 화염구 폭발 — 불티 고리가 사방으로 퍼지고, 주입을 소모해 작은 화염 화살이 원형으로 나간다 */
    private fun fireballBurst(at: Enemy, damage: Double) {
        addFx(FxKind.RING, at.x, at.y, radius = 82f, life = 0.5f, color = 0xFFFF8A3D.toInt(), width = 1.3f)
        addFx(FxKind.RING, at.x, at.y, radius = 46f, life = 0.34f, color = 0xFFFFD27A.toInt(), width = 0.8f)
        addFx(FxKind.OIL_FLAME, at.x, at.y - 8f, radius = 40f, life = 0.3f, color = 0xFFFF8A3D.toInt())
        repeat(12) {
            val a = it * 0.524f
            addBurst(at.x + cos(a) * 12f, at.y + sin(a) * 12f, 1, 0xFFFFA64D.toInt(), 220f, 0.5f, gravity = -70f)
        }
        areaHit(at.x, at.y, aoe(78f), damage * 0.45, DamageElement.FIRE, "화염구 폭발")
        igniteOil(at.x, at.y, 60f, damage)
        // poe2db: 화염 주입을 소모하면 이 폭발이 작은 화염 화살을 고리 모양으로 발사한다.
        val around = nearbyEnemies(at, 240f, 8)
        fireballRings++
        repeat(8) { i ->
            val a = i * (PI_F * 2f / 8f)
            val victim = around.getOrNull(i % around.size.coerceAtLeast(1)) ?: at
            addProjectile(
                Projectile(
                    x = at.x, y = at.y - 14f,
                    dirX = cos(a), dirY = sin(a), speed = 430f + i * 14f,
                    damage = damage * 0.08, crit = false, target = victim, hostile = false,
                    element = DamageElement.FIRE, kind = ProjKind.ARROW, life = 0.9f,
                    seed = rand.nextInt(),
                )
            )
        }
        addFx(FxKind.RING, at.x, at.y, radius = 96f, life = 0.38f, color = 0xFFFFB05A.toInt(), width = 1.1f)
        shake = max(shake, 3.4f)
    }

    /** 유탄 폭발 */
    private fun grenadeBurst(p: Projectile) {
        val radius = aoe(92f)
        addFx(FxKind.IMPACT, p.x, p.y, radius = radius, life = 0.45f, color = 0xFFFF8A3D.toInt(), width = 1.5f)
        addFx(FxKind.RING, p.x, p.y, radius = radius, life = 0.5f, color = 0xFFFFC24D.toInt(), width = 1.3f)
        addFx(FxKind.OIL_FLAME, p.x, p.y - 6f, radius = radius * 0.7f, life = 0.4f, color = 0xFFFF8A3D.toInt())
        addBurst(p.x, p.y - 10f, 20, 0xFFFFA64D.toInt(), 310f, 0.75f, gravity = -60f)
        areaHit(p.x, p.y, radius, p.damage, DamageElement.FIRE, "폭발 유탄")
        if (p.oilRadius > 0f) {
            spillOil(p.x, p.y, p.oilRadius, sticky = true)
            addFx(FxKind.OIL_SPLASH, p.x, p.y, radius = p.oilRadius * 1.2f, life = 0.7f, color = 0xFF8C7433.toInt())
            addPopup(p.x, p.y - 70f, "기름 지대", false, 0.7f, 0xFFC7A64B.toInt(), 0.8f)
        } else {
            igniteOil(p.x, p.y, radius * 0.8f, p.damage)
        }
        shake = max(shake, 4.4f)
    }

    private fun updateProjectiles(dt: Float) {
        var i = 0
        while (i < projectiles.size) {
            val p = projectiles[i]
            p.life -= dt
            // 화염구·서리 보주처럼 흔들리며 날아가는 연출에 쓰는 나이 (전기불꽃은 따로 센다)
            if (p.kind != ProjKind.SPARK) p.age += dt
            if (p.life <= 0f) {
                // 기름 덩이는 날아간 자리에 그대로 떨어져 기름 지대가 된다.
                if (p.oilRadius > 0f && (p.kind == ProjKind.OIL || p.kind == ProjKind.CHARGED_OIL)) {
                    oilImpact(p.x, p.y, p.oilRadius, p.damage, charged = p.kind == ProjKind.CHARGED_OIL)
                }
                // 수명이 다한 전기불꽃은 마지막 불꽃만 남기고 흐트러진다.
                if (p.kind == ProjKind.SPARK) addBurst(p.x, p.y, 3, 0xFFCFE8FF.toInt(), 120f, 0.2f)
                projectiles.removeAt(i)
                continue
            }
            if (p.hostile) {
                p.x += p.dirX * p.speed * dt
                p.y += p.dirY * p.speed * dt
                val d = hypot(player.x - p.x, player.y - p.y)
                if (d < 24f) {
                    hitPlayer(p.damage, p.element, true, p.x, p.y, source = p.source, skill = p.skill)
                    addBurst(p.x, p.y, 5, p.element.color, 150f, 0.35f)
                    projectiles.removeAt(i)
                    continue
                }
                if (p.x < -40 || p.x > W + 40 || p.y < -40 || p.y > H + 40) {
                    projectiles.removeAt(i)
                    continue
                }
            } else if (p.kind == ProjKind.GRENADE) {
                // 유탄은 적을 쫓지 않고 굴러가다 멈춘다 — 퓨즈가 다 타면 그 자리에서 터진다
                p.x += p.dirX * p.speed * dt
                p.y += p.dirY * p.speed * dt
                p.speed *= (1f - 2.1f * dt)
                p.lift += dt
                if (p.x < 30f || p.x > W - 30f) {
                    p.dirX = -p.dirX
                    p.x = p.x.coerceIn(30f, W - 30f)
                }
                if (p.y < 70f || p.y > H - 26f) {
                    p.dirY = -p.dirY
                    p.y = p.y.coerceIn(70f, H - 26f)
                }
                p.fuse -= dt
                if (p.fuse <= 0f) {
                    grenadeBurst(p)
                    projectiles.removeAt(i)
                    continue
                }
            } else if (p.kind == ProjKind.SPARK) {
                // 전기불꽃은 대상에게 곧게 날아가지 않고 제멋대로 꺾이며 바닥을 기어간다.
                if (!updateSpark(p, dt)) {
                    projectiles.removeAt(i)
                    continue
                }
            } else {
                val target = p.target
                if (target != null && target.alive) {
                    val dx = target.x - p.x
                    val dy = (target.y - 22f * target.scale) - p.y
                    val d = hypot(dx, dy).coerceAtLeast(0.001f)
                    p.dirX = dx / d
                    p.dirY = dy / d
                }
                // 전기를 띤 기름은 좌우로 흔들리며 날아간다
                if (p.wobble > 0f) {
                    val t = (elapsed * 11.0).toFloat() + p.trail.size * 0.35f
                    val a = atan2(p.dirY, p.dirX) + sin(t) * p.wobble * dt * 14f
                    p.dirX = cos(a)
                    p.dirY = sin(a)
                }
                // 미니언은 살짝 휘어 날아간다
                if (p.kind == ProjKind.MINION_ORB) {
                    val t = (elapsed * 6.0).toFloat()
                    val px = -p.dirY * cos(t) * 0.9f
                    val py = p.dirX * cos(t) * 0.9f
                    p.x += (p.dirX + px) * p.speed * dt
                    p.y += (p.dirY + py) * p.speed * dt
                } else {
                    p.x += p.dirX * p.speed * dt
                    p.y += p.dirY * p.speed * dt
                }
                p.trail.add(p.x)
                p.trail.add(p.y)
                if (p.trail.size > 14) p.trail.subList(0, p.trail.size - 14).clear()

                val t = p.target
                val near = if (t != null && t.alive) {
                    hypot(t.x - p.x, (t.y - 26f * t.scale) - p.y) < 17f + 8f * t.scale
                } else false
                // 기름 덩이는 목표가 없어도 그 자리에 떨어질 때까지 날아간다.
                val ttlEnd = (p.target == null || !p.target.alive) && p.oilRadius <= 0f
                if (near || ttlEnd) {
                    if (t != null && t.alive) {
                        underSkill(p.creditSkill) { onProjectileHit(p, t) }
                    } else {
                        addBurst(p.x, p.y, 4, p.element.color, 120f, 0.3f)
                    }
                    projectiles.removeAt(i)
                    continue
                }
            }
            i++
        }
    }

    /**
     * 전기불꽃 한 줄기를 굴린다.
     *
     * 0.06~0.3초마다 방향을 불규칙하게 꺾어 지그재그로 기어가고, 스치는 적을
     * 꿰뚫어 피해를 준다. 한 줄기가 같은 적을 두 번 때리지 않고, 남은 꿰뚫기나
     * 튕김이 바닥나거나 수명이 다하면 사라진다. (살아 있으면 true)
     */
    private fun updateSpark(p: Projectile, dt: Float): Boolean {
        p.age += dt
        val target = p.target
        // 방향 전환 — 목표 쪽으로는 아주 약하게만 기울고, 거의 제멋대로 꺾인다.
        p.turnTimer -= dt
        if (p.turnTimer <= 0f) {
            p.turnTimer = 0.06f + rand.nextFloat() * 0.24f
            val current = atan2(p.dirY, p.dirX)
            val jitter = (rand.nextFloat() - 0.5f) * 2.6f * p.wobble
            var aim = 0f
            if (target != null && target.alive) {
                val want = atan2(target.y - p.y, target.x - p.x) - current
                aim = Math.IEEEremainder(want.toDouble(), PI_F * 2.0).toFloat() * 0.30f
            }
            val a = current + jitter + aim
            p.dirX = cos(a)
            p.dirY = sin(a)
        }
        p.x += p.dirX * p.speed * dt
        p.y += p.dirY * p.speed * dt
        p.trail.add(p.x)
        p.trail.add(p.y)
        // 궤적을 길게 남겨야 화면에 지그재그 줄기가 보인다.
        if (p.trail.size > 72) p.trail.subList(0, p.trail.size - 72).clear()

        // 벽에 부밪히면 튕기며 꺾이다. 남은 튕김이 없으면 거기서 흐트러진다.
        var wall = false
        if (p.x < 24f || p.x > W - 24f) {
            p.dirX = -p.dirX
            p.x = p.x.coerceIn(24f, W - 24f)
            wall = true
        }
        if (p.y < 70f || p.y > H - 24f) {
            p.dirY = -p.dirY
            p.y = p.y.coerceIn(70f, H - 24f)
            wall = true
        }
        if (wall) {
            if (p.bounces <= 0) return false
            p.bounces--
            p.speed = (p.speed * 0.94f).coerceAtLeast(190f)
            p.turnTimer = 0f
            addFx(
                FxKind.BOLT, p.x, p.y,
                x2 = p.x + p.dirX * 46f + (rand.nextFloat() - 0.5f) * 36f,
                y2 = p.y + p.dirY * 46f + (rand.nextFloat() - 0.5f) * 28f,
                life = 0.14f, color = 0xFFBFE6FF.toInt(), seed = rand.nextInt(), width = 0.85f,
            )
            addBurst(p.x, p.y, 3, 0xFFE8F4FF.toInt(), 150f, 0.22f)
        }

        // 지나가는 적을 꿰뚫는다 — 한 줄기는 같은 적을 한 번만 때린다.
        for (e in enemies) {
            if (!e.alive) continue
            if (p.pierced.any { it === e }) continue
            if (hypot(e.x - p.x, (e.y - 26f * e.scale) - p.y) > 18f + 10f * e.scale) continue
            p.pierced.add(e)
            sparkHits++
            val angle = atan2(p.dirY, p.dirX)
            // 전기불꽃은 한 줄기가 한 번 스치는 것이 하나의 명중 사건이다 — 피해는 발수로
            // 나뉘어 작지만, 메타젬 에너지는 명중 한 번의 몫으로 센다 (poe2db 메타젬 설명).
            hitEnemy(e, p.damage, p.crit && p.pierced.size == 1, angle, 0.92f, energyWeight = p.energyWeight)
            addFx(
                FxKind.BOLT, p.x, p.y, x2 = e.x, y2 = e.y - 18f,
                life = 0.14f, color = 0xFFFFF6C8.toInt(), seed = rand.nextInt(), width = 0.7f,
            )
            addFx(FxKind.ARC_WEB, e.x, e.y - 14f, angle = angle, radius = 36f, life = 0.16f, color = 0xFFBFE6FF.toInt())
            addBurst(e.x, e.y - 16f, 3, 0xFFE8F4FF.toInt(), 160f, 0.22f)
            if (p.pierce <= 0) return false
            p.pierce--
        }
        return true
    }

    // ------------------------------------------------------------ 연출 헬퍼

    fun addFx(
        kind: FxKind,
        x: Float,
        y: Float,
        x2: Float = 0f,
        y2: Float = 0f,
        angle: Float = 0f,
        radius: Float = 0f,
        life: Float = 0.4f,
        color: Int = 0xFFFFFFFF.toInt(),
        seed: Int = 0,
        width: Float = 1f,
    ) {
        if (effects.size >= MAX_FX) effects.removeAt(0)
        effects.add(Fx(kind, x, y, x2, y2, angle, radius, life, color, if (seed == 0) rand.nextInt() else seed, width))
    }

    /**
     * 같은 씨앗이면 매번 같은 모양으로 퍼지는 파편.
     * 처치 연출처럼 자주 터지는 이펙트에 써서 전투 난수 흐름을 건드리지 않는다.
     */
    private fun addBurstPattern(
        x: Float,
        y: Float,
        count: Int,
        color: Int,
        speed: Float,
        life: Float,
        gravity: Float = 0f,
        seed: Int = 0,
    ) {
        for (i in 0 until count) {
            if (particles.size >= MAX_PARTICLES) particles.removeAt(0)
            val a = i * 2.399f + seed * 0.137f
            val s = speed * (0.45f + ((i * 37 + seed) % 60) / 100f)
            particles.add(
                Particle(
                    x, y,
                    cos(a) * s, sin(a) * s - speed * 0.18f,
                    life * (0.7f + ((i * 53 + seed) % 50) / 100f), life,
                    2.4f + ((i * 11 + seed) % 30) / 10f,
                    color, gravity, i % 3 == 0,
                )
            )
        }
    }

    fun addBurst(x: Float, y: Float, count: Int, color: Int, speed: Float, life: Float, gravity: Float = 0f) {
        repeat(count) {
            if (particles.size >= MAX_PARTICLES) particles.removeAt(0)
            val a = rand.nextFloat() * 6.283f
            val s = speed * (0.35f + rand.nextFloat() * 0.75f)
            particles.add(
                Particle(
                    x, y,
                    cos(a) * s, sin(a) * s - speed * 0.18f,
                    life * (0.6f + rand.nextFloat() * 0.7f), life,
                    2.4f + rand.nextFloat() * 3.4f,
                    color, gravity, rand.nextFloat() < 0.35f,
                )
            )
        }
    }

    /** 투사체를 전장에 올린다 — 지금 피해를 넣는 스킬 이름을 함께 달아 둔다 */
    private fun addProjectile(p: Projectile) {
        if (p.creditSkill.isBlank()) p.creditSkill = skillContext
        projectiles.add(p)
    }

    private fun addPopup(x: Float, y: Float, text: String, crit: Boolean, life: Float, color: Int, size: Float) {
        if (popups.size >= MAX_POPUPS) popups.removeAt(0)
        popups.add(Popup(x, y, text, crit, life, color, size))
    }

    /** HUD에 보여줄 메타젬별 발동 현황 — 활성화된 메타젬을 전부 나열한다. */
    fun triggerHud(): String =
        triggers.joinToString("  ") { rt ->
            val queued = if (rt.queued > 0) " x${rt.queued}" else ""
            "${rt.spec.kind.short} ${rt.casts}$queued"
        }

    /** HUD에 보여줄 전직 효과 — 부여 스킬이 돌면 발동 횟수를 붙인다. */
    fun ascendHud(): String {
        val base = stats.ascendText
        if (base.isBlank()) return ""
        if (stats.ascendSkills.isEmpty()) return base
        val cast = if (ascendCasts > 0) " · 부여 스킬 ${ascendCasts}회" else ""
        return base + cast
    }

    /** 봇이 지금 돌리는 콤보 — 전장 HUD에 한 줄로 적는다. */
    fun comboHud(): String {
        if (comboPlan.size <= 1) return ""
        val body = comboText.ifBlank { ComboPlanner.summary(comboPlan) }
        val extra = buildString {
            val stage = whirlwinds.maxOfOrNull { it.stage } ?: 0
            if (stage > 0) append(" · 와류 ").append(stage).append("단계")
            if (usesBell && comboStacks > 0) append(" · 연계 ").append(comboStacks)
            if (twisters.isNotEmpty()) append(" · 돌개바람 ").append(twisters.size)
            if (powerCharges > 0) append(" · 권능 ").append(powerCharges)
        }
        return "봇 $body$extra"
    }

    /** 현재 메타 에너지 %와 남은 연발 — 전투 화면 위쪽에 크게 띄운다. */
    fun metaHud(): String {
        if (triggers.isEmpty()) return ""
        val main = triggers.maxByOrNull { it.energyPercent } ?: return ""
        val label = main.spec.skills.firstOrNull()?.name ?: main.spec.kind.short
        val pct = main.energyPercent.toInt()
        val queued = if (main.queued > 0) " · 연발 x${main.queued}" else ""
        return "$label ${pct}%$queued"
    }

    // ------------------------------------------------------------ 보스 패턴·페이즈

    private fun updateBossPatterns(dt: Float) {
        val b = bossEnemy ?: return
        if (!b.alive) return
        if (bossPhase == 1 && b.hp <= b.maxHp * 0.5) enterPhaseTwo(b)
        bossPatternTimer -= dt
        if (bossPatternTimer > 0f) return
        val patterns = bossPatterns(b)
        if (patterns.isEmpty()) return
        val pattern = patterns[bossPatternIndex % patterns.size]
        bossPatternIndex++
        bossPatternName = pattern.name
        patternHistory.add(pattern.name)
        pattern.action()
        bossPatternTimer = pattern.cooldown * (if (bossPhase >= 2) 0.78f else 1f)
    }

    private fun enterPhaseTwo(b: Enemy) {
        bossPhase = 2
        phaseTwoAnnounced = true
        b.enraged = true
        bossPatternTimer = 1.0f
        bossPatternIndex = 0
        bannerText = "${b.boss?.name.orEmpty()} · 2페이즈"
        banner = 2.4f
        shake = max(shake, 9f)
        addFx(FxKind.AURA, b.x, b.y, radius = 200f, life = 1.2f, color = b.boss?.element?.color ?: 0xFFFF6A3D.toInt())
        addFx(FxKind.RING, b.x, b.y, radius = 150f, life = 0.8f, color = 0xFFFFFFFF.toInt(), width = 2f)
        addBurst(b.x, b.y - 40f * b.scale, 30, b.boss?.element?.color ?: 0xFFFF6A3D.toInt(), 360f, 1.1f)
    }

    private fun bossPatterns(b: Enemy): List<BossPattern> {
        val id = b.boss?.id ?: return emptyList()
        val second = bossPhase >= 2
        val list = ArrayList<BossPattern>()
        when (id) {
            "arbiter" -> {
                list.add(BossPattern("화염 검기", 2.7f) { arbiterSlash(b) })
                list.add(BossPattern("불기둥", 3.0f) { arbiterPillar(b) })
                if (second) {
                    list.add(BossPattern("심판의 안전지대", 4.6f) { arbiterJudgment(b) })
                    list.add(BossPattern("유성 낙하", 4.0f) { arbiterMeteors(b) })
                }
            }
            "xesht" -> {
                list.add(BossPattern("균열 구체 연사", 2.6f) { xeshtVolley(b) })
                list.add(BossPattern("거대 손 내려찍기", 3.4f) { xeshtHand(b) })
                if (second) list.add(BossPattern("균열 쫄 소환", 5.0f) { xeshtAdds(b) })
            }
            "king" -> {
                list.add(BossPattern("의식 투사체", 2.8f) { kingVolley(b) })
                list.add(BossPattern("순간이동", 3.2f) { kingBlink(b) })
                if (second) {
                    list.add(BossPattern("저주 장판", 4.2f) { kingCurse(b) })
                    list.add(BossPattern("분신", 5.2f) { kingClones(b) })
                }
            }
            "zarokh" -> {
                list.add(BossPattern("시간 구체", 2.7f) { zarokhVolley(b) })
                list.add(BossPattern("모래시계", 3.6f) { zarokhHourglass(b) })
                if (second) {
                    list.add(BossPattern("시간 왜곡", 3.8f) { zarokhWarp(b) })
                    list.add(BossPattern("번개 구슬 회전", 4.4f) { zarokhOrbit(b) })
                }
            }
            "olroth" -> {
                list.add(BossPattern("룬 검기", 2.7f) { olrothSlash(b) })
                list.add(BossPattern("빙결 파편", 3.0f) { olrothShards(b) })
                if (second) {
                    list.add(BossPattern("룬 보호막", 6.0f) { olrothShield(b) })
                    list.add(BossPattern("광역 냉기 폭발", 4.2f) { olrothNova(b) })
                }
            }
            else -> list.add(BossPattern("강타", 3.0f) { beginSlam(b) })
        }
        return list
    }

    // ---- 장판 추가 함수

    private fun tele(x: Float, y: Float, shape: TelegraphShape, radius: Float, length: Float,
                     angle: Float, windup: Float, damage: Double, element: DamageElement,
                     name: String, delay: Float = 0f) {
        telegraphs.add(Telegraph(shape, x, y, angle, radius, length, windup, damage, element, name, delay))
        telegraphsSpawned++
        addFx(
            if (shape == TelegraphShape.LINE) FxKind.SLASH else FxKind.GROUND_CRACK,
            x, y, angle = angle, radius = if (shape == TelegraphShape.LINE) length else radius,
            life = windup + delay, color = alphaOf(element.color, 150),
        )
    }

    private fun alphaOf(color: Int, a: Int): Int =
        (color and 0x00FFFFFF) or ((a.coerceIn(0, 255) and 0xFF) shl 24)

    private fun circleAtPlayer(radius: Float, windup: Float, damage: Double, element: DamageElement, name: String) {
        tele(player.x, player.y, TelegraphShape.CIRCLE, radius, 0f, 0f, windup, damage, element, name)
    }

    private fun lineAtPlayer(radius: Float, length: Float, windup: Float, damage: Double, element: DamageElement, name: String, spread: Float = 0f) {
        val b = bossEnemy ?: return
        val angle = atan2(player.y - b.y, player.x - b.x) + spread
        tele(b.x, b.y, TelegraphShape.LINE, radius, length, angle, windup, damage, element, name)
    }

    // ---- 재의 중재자

    private fun arbiterSlash(b: Enemy) {
        val dmg = b.hit * 2.1
        lineAtPlayer(24f, 460f, 0.95f, dmg, DamageElement.FIRE, "화염 검기")
        lineAtPlayer(24f, 460f, 1.15f, dmg * 0.85, DamageElement.FIRE, "화염 검기", spread = 0.34f)
        lineAtPlayer(24f, 460f, 1.15f, dmg * 0.85, DamageElement.FIRE, "화염 검기", spread = -0.34f)
    }

    private fun arbiterPillar(b: Enemy) {
        circleAtPlayer(88f, 1.05f, b.hit * 1.9, DamageElement.FIRE, "불기둥")
        repeat(2) {
            val x = player.x + (rand.nextFloat() - 0.5f) * 220f
            val y = player.y + (rand.nextFloat() - 0.5f) * 220f
            tele(x, y, TelegraphShape.CIRCLE, 70f, 0f, 0f, 1.3f, b.hit * 1.5, DamageElement.FIRE, "불기둥")
        }
    }

    private fun arbiterJudgment(b: Enemy) {
        // 전장 전체가 불타고 가운데 안전 원만 남는다
        telegraphs.add(
            Telegraph(
                TelegraphShape.SAFE_CIRCLE,
                BattleWorld.W / 2f, BattleWorld.H / 2f, 0f,
                92f, 0f, 2.3f, b.hit * 2.6, DamageElement.FIRE, "심판의 안전지대",
            )
        )
        addFx(FxKind.AURA, BattleWorld.W / 2f, BattleWorld.H / 2f, radius = 92f, life = 2.3f, color = 0xFF7DFFB0.toInt())
    }

    private fun arbiterMeteors(b: Enemy) {
        repeat(6) { i ->
            val x = player.x + if (i == 0) 0f else (rand.nextFloat() - 0.5f) * 360f
            val y = player.y + if (i == 0) 0f else (rand.nextFloat() - 0.5f) * 420f
            tele(
                x.coerceIn(40f, BattleWorld.W - 40f), y.coerceIn(60f, BattleWorld.H - 30f),
                TelegraphShape.CIRCLE, 74f, 0f, 0f, 1.5f + i * 0.12f, b.hit * 1.7,
                DamageElement.FIRE, "유성 낙하",
            )
        }
    }

    // ---- 균열 군주 크세쉬

    private fun xeshtVolley(b: Enemy) {
        bossVolley(b, 5, DamageElement.CHAOS, 0.16f, 380f)
        addFx(FxKind.AURA, b.x, b.y - 50f * b.scale, radius = 60f, life = 0.4f, color = DamageElement.CHAOS.color)
    }

    private fun xeshtHand(b: Enemy) {
        val dmg = b.hit * 2.4
        circleAtPlayer(120f, 1.15f, dmg, DamageElement.PHYSICAL, "거대 손 내려찍기")
        lineAtPlayer(40f, 300f, 1.15f, dmg * 0.8, DamageElement.CHAOS, "균열 손아귀")
    }

    private fun xeshtAdds(b: Enemy) {
        bossPatternName = "균열 쫄 소환"
        repeat(3) {
            val angle = rand.nextFloat() * 6.283f
            val x = (b.x + cos(angle) * 120f).coerceIn(60f, BattleWorld.W - 40f)
            val y = (b.y + sin(angle) * 120f).coerceIn(60f, BattleWorld.H - 30f)
            val e = Enemy(
                kind = EnemyKind.MAGIC, x = x, y = y,
                maxHp = 210_000.0, hit = 420.0, interval = 1.5,
                element = DamageElement.CHAOS, ranged = true, speed = 78f, scale = 1.2f,
                slotAngle = rand.nextFloat() * 6.283f,
            )
            enemies.add(e)
            addFx(FxKind.AURA, x, y, radius = 46f, life = 0.6f, color = DamageElement.CHAOS.color)
            addBurst(x, y, 10, 0xFFC182FF.toInt(), 190f, 0.6f)
        }
    }

    // ---- 안개 속의 왕

    private fun kingVolley(b: Enemy) {
        bossVolley(b, 4, DamageElement.COLD, 0.2f, 320f)
    }

    private fun kingBlink(b: Enemy) {
        val angle = rand.nextFloat() * 6.283f
        val x = (player.x + cos(angle) * 150f).coerceIn(60f, BattleWorld.W - 40f)
        val y = (player.y + sin(angle) * 150f).coerceIn(70f, BattleWorld.H - 30f)
        addBurst(b.x, b.y - 40f * b.scale, 18, 0xFF9A7DFF.toInt(), 260f, 0.7f)
        addFx(FxKind.AURA, b.x, b.y, radius = 70f, life = 0.5f, color = 0xFF7A5ACC.toInt())
        b.x = x
        b.y = y
        addFx(FxKind.AURA, x, y, radius = 90f, life = 0.6f, color = 0xFFB79CFF.toInt())
        circleAtPlayer(96f, 0.9f, b.hit * 1.8, DamageElement.COLD, "안개의 손아귀")
    }

    private fun kingCurse(b: Enemy) {
        // 가운데만 안전한 저주 고리
        telegraphs.add(
            Telegraph(
                TelegraphShape.DONUT, player.x, player.y, 0f,
                74f, 260f, 2.1f, b.hit * 2.0, DamageElement.CHAOS, "저주 장판",
            )
        )
        addFx(FxKind.RING, player.x, player.y, radius = 260f, life = 2.1f, color = 0xFF8E6BE0.toInt(), width = 1.6f)
    }

    private fun kingClones(b: Enemy) {
        bossPatternName = "분신"
        repeat(2) { i ->
            val angle = if (i == 0) 0.6f else 3.6f
            val x = (b.x + cos(angle) * 130f).coerceIn(60f, BattleWorld.W - 40f)
            val y = (b.y + sin(angle) * 130f).coerceIn(60f, BattleWorld.H - 30f)
            enemies.add(
                Enemy(
                    kind = EnemyKind.MAGIC, x = x, y = y,
                    maxHp = 260_000.0, hit = 380.0, interval = 1.7,
                    element = DamageElement.COLD, ranged = true, speed = 70f, scale = 1.15f,
                    slotAngle = angle,
                )
            )
            addFx(FxKind.AURA, x, y, radius = 52f, life = 0.7f, color = 0xFFB79CFF.toInt())
        }
    }

    // ---- 자로크

    private fun zarokhVolley(b: Enemy) {
        bossVolley(b, 4, DamageElement.LIGHTNING, 0.14f, 400f)
    }

    private fun zarokhHourglass(b: Enemy) {
        val angle = rand.nextFloat() * 3.14f
        tele(player.x, player.y, TelegraphShape.CROSS, 26f, 220f, angle, 1.2f, b.hit * 2.0, DamageElement.LIGHTNING, "모래시계")
        addFx(FxKind.RING, player.x, player.y, radius = 90f, life = 1.2f, color = 0xFFFFE9A8.toInt())
    }

    private fun zarokhWarp(b: Enemy) {
        // 시간이 느려진 틈에 사방에서 구체가 날아온다
        bossVolley(b, 8, DamageElement.LIGHTNING, 0.38f, 300f)
        addFx(FxKind.AURA, player.x, player.y, radius = 120f, life = 0.7f, color = 0xFFB79CFF.toInt())
    }

    private fun zarokhOrbit(b: Enemy) {
        repeat(10) { i ->
            val a = i * 0.628f + elapsed.toFloat()
            addProjectile(
                Projectile(
                    x = b.x + cos(a) * 60f, y = b.y + sin(a) * 60f,
                    dirX = cos(a), dirY = sin(a), speed = 300f,
                    damage = b.hit * 0.95, crit = false, target = null, hostile = true,
                    element = DamageElement.LIGHTNING, kind = ProjKind.ENEMY_BOLT,
                    source = b.boss?.name.orEmpty(),
                    skill = bossPatternName.ifBlank { "번개 구슬 회전" },
                )
            )
        }
        addFx(FxKind.RING, b.x, b.y, radius = 90f, life = 0.5f, color = 0xFFFFF3A8.toInt(), width = 1.4f)
    }

    // ---- 올로스

    private fun olrothSlash(b: Enemy) {
        lineAtPlayer(26f, 420f, 0.95f, b.hit * 2.0, DamageElement.COLD, "룬 검기")
    }

    private fun olrothShards(b: Enemy) {
        val dmg = b.hit * 1.4
        lineAtPlayer(22f, 340f, 1.0f, dmg, DamageElement.COLD, "빙결 파편", spread = -0.28f)
        lineAtPlayer(22f, 340f, 1.0f, dmg, DamageElement.COLD, "빙결 파편")
        lineAtPlayer(22f, 340f, 1.0f, dmg, DamageElement.COLD, "빙결 파편", spread = 0.28f)
    }

    private fun olrothShield(b: Enemy) {
        b.shield = b.maxHp * 0.10
        b.freezeT = 0f
        addFx(FxKind.AURA, b.x, b.y - 40f * b.scale, radius = 110f, life = 1.0f, color = 0xFF7FE0FF.toInt())
        addFx(FxKind.RING, b.x, b.y, radius = 100f, life = 0.8f, color = 0xFFBFF4FF.toInt(), width = 1.6f)
        addBurst(b.x, b.y - 40f * b.scale, 16, 0xFF9BE8FF.toInt(), 220f, 0.7f)
        bannerText = "룬 보호막"
        banner = 1.4f
    }

    private fun olrothNova(b: Enemy) {
        tele(b.x, b.y, TelegraphShape.CIRCLE, 230f, 0f, 0f, 1.6f, b.hit * 2.2, DamageElement.COLD, "광역 냉기 폭발")
        repeat(4) { i ->
            val a = i * 1.57f
            tele(b.x + cos(a) * 150f, b.y + sin(a) * 150f, TelegraphShape.CIRCLE, 70f, 0f, 0f, 1.9f, b.hit * 1.3, DamageElement.COLD, "서리 파편")
        }
    }

    // ---- 공용

    private fun bossVolley(b: Enemy, count: Int, element: DamageElement, spread: Float, speed: Float) {
        val base = atan2(player.y - b.y, player.x - b.x)
        repeat(count) { i ->
            val a = base + (i - (count - 1) / 2f) * spread
            addProjectile(
                Projectile(
                    x = b.x, y = b.y - 46f * b.scale,
                    dirX = cos(a), dirY = sin(a), speed = speed,
                    damage = b.hit * 0.8, crit = false, target = null, hostile = true,
                    element = element, kind = ProjKind.ENEMY_BOLT,
                    source = b.boss?.name.orEmpty(),
                    skill = bossPatternName.ifBlank { "보스 투사체" },
                )
            )
        }
        addFx(FxKind.AURA, b.x, b.y - 46f * b.scale, radius = 54f, life = 0.35f, color = element.color)
    }

    private fun updateTelegraphs(dt: Float) {
        var i = 0
        while (i < telegraphs.size) {
            val t = telegraphs[i]
            val done = t.advance(dt)
            if (!done) {
                i++
                continue
            }
            resolveTelegraph(t)
            telegraphs.removeAt(i)
            if (state != State.RUNNING) return
        }
    }

    private fun resolveTelegraph(t: Telegraph) {
        val color = t.element.color
        when (t.shape) {
            TelegraphShape.LINE -> {
                addFx(FxKind.SLASH, t.x, t.y, angle = t.angle, radius = t.length, life = 0.3f, color = color, width = 2.2f)
                addFx(FxKind.SHOCKWAVE, t.x + cos(t.angle) * t.length * 0.5f, t.y + sin(t.angle) * t.length * 0.5f, radius = t.radius * 2.2f, life = 0.35f, color = color)
            }
            TelegraphShape.CIRCLE, TelegraphShape.DONUT, TelegraphShape.SAFE_CIRCLE -> {
                addFx(FxKind.SHOCKWAVE, t.x, t.y, radius = if (t.shape == TelegraphShape.DONUT) t.length else t.radius, life = 0.5f, color = color, width = 1.8f)
                addFx(FxKind.RING, t.x, t.y, radius = t.radius, life = 0.4f, color = color)
                addBurst(t.x, t.y, 20, color, 320f, 0.8f)
            }
            TelegraphShape.CROSS -> {
                repeat(4) { i ->
                    val a = t.angle + i * 1.5708f
                    addFx(FxKind.BOLT, t.x, t.y, x2 = t.x + cos(a) * t.length, y2 = t.y + sin(a) * t.length, life = 0.25f, color = color, width = 1.6f)
                }
                addFx(FxKind.RING, t.x, t.y, radius = 70f, life = 0.45f, color = color)
            }
        }
        shake = max(shake, 6.5f)
        if (t.contains(player.x, player.y)) {
            hitPlayer(
                t.damage, t.element, true, t.x, t.y, heavy = true,
                source = t.source.ifBlank { bossEnemy?.boss?.name.orEmpty() },
                skill = t.name,
            )
        } else {
            addPopup(player.x, player.y - 76f, "회피 성공", false, 0.7f, 0xFF8CE8C0.toInt(), 0.82f)
        }
    }

    /** 지금 피해야 할 장판 (곧 떨어지고, 안에 서 있으면 맞는다) */
    fun dangerTelegraph(): Telegraph? =
        telegraphs.firstOrNull { it.aboutToLand && it.contains(player.x, player.y) }

    fun result(): BattleResult {
        val seconds = max(0.1, elapsed)
        return BattleResult(
            victory = state == State.VICTORY,
            seconds = seconds,
            damageDealt = damageDealt,
            effectiveDps = damageDealt / seconds,
            crits = critCount,
            hitsTaken = hitsTaken,
            evades = evades,
            kills = kills,
            totalEnemies = totalEnemies,
            survival = ((player.life + player.es) / maxPool * 100.0).coerceIn(0.0, 100.0),
            lifeLeft = player.life,
            esLeft = player.es,
            maxPool = maxPool,
            triggerCasts = triggerCasts,
            triggerDamage = triggerDamage,
            triggerText = stats.triggerText,
            triggers = triggers.map { it.report() },
            maxBurst = maxBurst,
            burstCasts = burstCasts,
            ascendCasts = ascendCasts,
            ascendDamage = ascendDamage,
            comboName = if (comboPlan.size > 1) ComboPlanner.summary(comboPlan) else "",
            phantomCasts = phantomCasts,
            whirlwindCasts = whirlwindCasts,
            twisterCasts = twisterCasts,
            bellCasts = bellCasts,
            minionDamage = minionDamage,
            minionKills = minionKills,
            minionDeaths = minionDeaths,
            toadExplosions = toadExplosions,
            darkConsequencesDamage = darkConsequencesDamage,
            minionText = stats.minions.summary,
            beast = stats.beast,
            beastText = stats.beastProfile.summary,
            oilSpills = oilSpills,
            oilIgnitions = oilIgnitions,
            oilDamage = oilDamage,
            oilShots = barrageShots,
            deathCause = deathCause,
            skillDamage = skillTally(),
            chargeText = if (chargeRules.usesCharges) chargesHud() else "",
            frenzyGained = frenzyGained,
            chargesSpent = chargesSpent,
            heraldProcs = heraldProcsTotal,
            heraldDamage = heraldDamage,
            heraldReports = heraldReports(),
            spiritDamage = spiritDamage,
            spiritReports = spiritReports(),
            heraldText = stats.heralds.summary,
            shatters = shatters,
            clear = if (state == State.VICTORY) clearSummary() else null,
        )
    }

    /** 부적 변신 상태를 HUD에 한 줄로 */
    fun beastHud(): String {
        if (!stats.beast.isBeast) return ""
        val parts = ArrayList<String>()
        parts.add(stats.beast.label)
        if (stats.beastProfile.talisman.isNotBlank()) parts.add(stats.beastProfile.talisman)
        if (player.shiftAnim < 1f) parts.add("변신 중") else parts.add("변신")
        if (powerCharges > 0) parts.add("권능 $powerCharges")
        return parts.joinToString(" · ")
    }

    /** 기름 지대·불붙은 기름 상태 */
    fun oilHud(): String {
        if (oils.isEmpty() && oilSpills == 0) return ""
        val burning = oils.count { it.burning }
        val parts = ArrayList<String>()
        parts.add("기름 ${oils.size}")
        if (burning > 0) parts.add("불붙음 $burning")
        if (barrageShots > 0) parts.add("포화 ${barrageShots}발")
        if (chargedOilShots > 0) parts.add("전기 기름 $chargedOilShots")
        if (totems.isNotEmpty()) parts.add("토템 ${totems.size}")
        return parts.joinToString(" · ")
    }

    /** 전장 HUD에 보여줄 소환수 줄 — 살아 있는 소환수 수와 두꺼비 폭발 횟수 */
    fun minionHud(): String {
        if (stats.minions.isEmpty) return ""
        val alive = minions.count { it.alive && !it.isToad }
        val toads = minions.count { it.alive && it.isToad }
        val parts = ArrayList<String>()
        if (alive > 0) parts.add("소환수 $alive")
        if (stats.minions.toadCount > 0) {
            parts.add("두꺼비 $toads/${stats.minions.toadCount}")
            if (toadExplosions > 0) parts.add("암울한 결과 $toadExplosions")
        }
        return if (parts.isEmpty()) "" else parts.joinToString(" · ")
    }

}
