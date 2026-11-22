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
) {
    val element: DamageElement get() = fx.element

    /** 전장 HUD에 같이 보여줄 트리거 설명 */
    val triggerText: String
        get() = triggers.joinToString("  ") { spec -> "${spec.kind.short} · ${spec.skillNames}" }
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
) {
    var hp = maxHp
    var attackTimer = interval * (0.35 + Random.nextDouble() * 0.5)
    var walkPhase = Random.nextDouble().toFloat() * 6.28f
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
    val shocked: Boolean get() = shockT > 0f
}

enum class FxKind {
    SLASH, ICE_CONE, RING, BOLT, PILLAR, ORB, AURA, SPEAR, SHOCKWAVE, CYCLONE, MINION_HIT, CLOUD,
    GROUND_CRACK, METEOR, THRUST, IMPACT, AFTERIMAGE, STREAK,
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

enum class ProjKind { ICE_ARROW, LIGHT_ARROW, FIRE_ORB, FROST_ORB, CHAOS_ORB, MINION_ORB, BOLT, ARROW, SPARK, ENEMY_BOLT }

class Projectile(
    var x: Float,
    var y: Float,
    var dirX: Float,
    var dirY: Float,
    val speed: Float,
    val damage: Double,
    val crit: Boolean,
    val target: Enemy?,
    val hostile: Boolean,
    val element: DamageElement,
    val kind: ProjKind,
    var life: Float = 3f,
    var trail: MutableList<Float> = ArrayList(),
)

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

class TriggerRuntime(val spec: TriggerSpec, private val critRate: Double) {

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

    val cooldownMax: Float = spec.kind.cooldown

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
) {
    companion object {
        const val W = 640f
        const val H = 780f
        private const val PI_F = 3.1415927f
        private const val MAX_FX = 220
        private const val MAX_PARTICLES = 420
        private const val MAX_POPUPS = 46
        /** 한 프레임에 처리할 수 있는 최대 공격 횟수 (초고속 공속 대응) */
        private const val MAX_STRIKES_PER_STEP = 6
        /** 메타 에너지 100%(발동 1회)에 해당하는 피해량 */
        const val REFERENCE_HIT = 60_000.0

        fun formatShort(value: Double): String = when {
            // 무한·NaN은 화면에 숫자 대신 대시로 내보낸다.
            !value.isFinite() -> "—"
            value >= 1_000_000_000 -> String.format(java.util.Locale.KOREA, "%.2fB", value / 1_000_000_000)
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

    private var leechPool = 0.0
    private var meleeBudget = 2
    private var meleeBudgetTimer = 0.5f
    private var bossPulseTimer = 2.4f
    private var slamX = 0f
    private var slamY = 0f
    private var slamRadius = 150f
    private val rand = Random(System.nanoTime())

    val triggers: List<TriggerRuntime> =
        stats.triggers.map { spec ->
            val critRate = stats.aps * (stats.critChance / 100.0)
            TriggerRuntime(spec, critRate)
        }
    var triggerCasts = 0
        private set
    var triggerDamage = 0.0
        private set

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

    val totalEnemies: Int get() = enemies.size
    val aliveCount: Int get() = enemies.count { it.alive }
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
            )
            enemies.add(e)
            bannerText = b.name
            banner = 2.2f
            return
        }
        val rares = max(1, mobCount / 20)
        val magics = max(1, mobCount / 6)
        val normals = max(1, mobCount - rares - magics)
        var index = 0
        repeat(normals) { spawn(EnemyKind.NORMAL, index++) }
        repeat(magics) { spawn(EnemyKind.MAGIC, index++) }
        repeat(rares) { spawn(EnemyKind.RARE, index++) }
        bannerText = if (amp) "${mobCount}마리 · 증폭 16T" else "${mobCount}마리 · 16T"
        banner = 1.6f
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
            )
        )
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
        meleeBudgetTimer -= dt
        if (meleeBudgetTimer <= 0f) {
            meleeBudgetTimer = 0.5f
            meleeBudget = 2
        }
        updatePlayer(dt)
        updateBossPatterns(dt)
        updateTelegraphs(dt)
        updateAilments(dt)
        updateEnemies(dt)
        updateProjectiles(dt)
        updateDots(dt)
        updateTriggers(dt)
        updateCastQueue(dt)
        updateStrikes(dt)
        if (player.life <= 0.0) {
            player.life = 0.0
            state = State.DEFEAT
            addBurst(player.x, player.y, 26, 0xFFFF5A5A.toInt(), 260f, 1.1f)
            shake = 12f
        } else if (enemies.none { it.alive }) {
            state = State.VICTORY
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
        }
        player.hurtFlash = max(0f, player.hurtFlash - dt * 4f)
    }

    // ------------------------------------------------------------ 플레이어

    private fun updatePlayer(dt: Float) {
        // 재생 · 보호막 충전 · 흡수
        if (player.life > 0) {
            player.life = min(player.baseLife, player.life + player.baseLife * 0.009 * dt)
            if (leechPool > 0.0) {
                val heal = min(leechPool, player.baseLife * 0.03 * dt)
                leechPool -= heal
                player.life = min(player.baseLife, player.life + heal)
            }
        }
        player.esDelay = max(0f, player.esDelay - dt)
        if (player.esDelay <= 0f && player.es < player.baseEs) {
            player.es = min(player.baseEs, player.es + player.baseEs * 0.22 * dt)
        }
        player.attackAnim = max(0f, player.attackAnim - dt / 0.26f)
        player.dodgeCooldown = max(0f, player.dodgeCooldown - dt)

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
            if (target == null) {
                player.attackTimer = 0.1f
                break
            }
            player.attackTimer += interval
            player.attackAnim = 1f
            player.attackCount++
            strikes++
            fireAttack(target)
        }
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

    /** PoB DPS를 평균 피해로 환산 (치명타 기대값 포함) */
    private fun baseHitDamage(): Double {
        val aps = effectiveAps()
        val dps = stats.dps.takeIf { it.isFinite() && it > 0.0 } ?: 1.0
        val critFactor = 1.0 + (stats.critChance / 100.0) * (stats.critMulti / 100.0 - 1.0)
        val perHit = dps / aps
        val factor = critFactor.takeIf { it.isFinite() && it > 0.0 } ?: 1.0
        return (perHit / factor).coerceAtLeast(1.0)
    }

    /** 초당 공격 횟수가 0/NaN으로 들어와도 루프가 멈추지 않게 한다. */
    private fun effectiveAps(): Double =
        stats.aps.takeIf { it.isFinite() && it > 0.0 }?.coerceIn(0.2, 30.0) ?: 1.0

    private fun rollDamage(): Pair<Double, Boolean> {
        val crit = rand.nextDouble() * 100.0 < stats.critChance
        val base = baseHitDamage() * (0.92 + rand.nextDouble() * 0.16)
        return (if (crit) base * stats.critMulti / 100.0 else base) to crit
    }

    private fun fireAttack(target: Enemy) {
        val (damage, crit) = rollDamage()
        val angle = atan2(target.y - player.y, target.x - player.x)
        val blink = stats.flicker &&
            (stats.form == AttackForm.MELEE || stats.form == AttackForm.UNARMED)
        if (blink) {
            flickerStrike(target, damage, crit)
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
        val reach: Float = when (style) {
            "thrust" -> 118f
            "sweep" -> 108f
            "slam" -> 92f
            else -> 96f
        }
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
        if (crit) shake = max(shake, 5.2f)
    }

    /** 활: 시위를 당겼다 놓으며 깃털 달린 화살을 날린다. */
    private fun bowShot(target: Enemy, damage: Double, crit: Boolean, angle: Float) {
        spawnProjectile(
            ProjKind.ARROW, target, damage, crit, stats.fx.element,
            speed = 980f, angle = angle,
        )
        addFx(
            FxKind.SLASH, player.x + cos(angle) * 16f, player.y + sin(angle) * 16f - 22f,
            angle = angle, radius = 26f, life = 0.14f, color = 0xFFF2E6C8.toInt(), width = 0.6f,
        )
    }

    /** 쇠뇌: 앞세워 조준하고 반동과 함께 볼트를 연사한다. */
    private fun crossbowShot(target: Enemy, damage: Double, crit: Boolean, angle: Float) {
        spawnProjectile(
            ProjKind.BOLT, target, damage, crit, stats.fx.element,
            speed = 1_520f, angle = angle,
        )
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
        projectiles.add(
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
            if (e.igniteT <= 0f) continue
            e.igniteT = max(0f, e.igniteT - dt)
            val tick = e.igniteDps * dt
            if (tick <= 0) continue
            e.hp -= tick
            damageDealt += tick
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
            val walkRate = if (e.kind == EnemyKind.BOSS && e.boss != null) 5f else 8f
            e.walkPhase += dt * walkRate
            val dx = player.x - e.x
            val dy = player.y - e.y
            val dist = hypot(dx, dy).coerceAtLeast(0.001f)
            val stop = if (e.ranged) 168f else 26f * e.scale
            val bossNow = e.boss
            if (bossNow != null) {
                bossPulseTimer -= dt
                if (bossPulseTimer <= 0f) {
                    bossPulseTimer = 2.2f
                    if (dist < 380f) {
                        hitPlayer(maxPool * 0.09, bossNow.element, false, e.x, e.y)
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
                val aimX = player.x + cos(ang) * stop * 0.9f
                val aimY = player.y + sin(ang) * stop * 0.9f
                val ax = aimX - e.x
                val ay = aimY - e.y
                val ad = hypot(ax, ay).coerceAtLeast(0.001f)
                val sp = e.speed * pull * (if (e.windup > 0f) 0.25f else 1f) *
                    (if (frozen) 0.35f else 1f)
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
            if (frozen) {
                e.attackTimer = max(e.attackTimer, 0.35)
                continue
            }
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
                    enemyShoot(e, dx / dist, dy / dist)
                } else {
                    hitPlayer(e.hit, e.element, true, e.x, e.y)
                    addFx(FxKind.SLASH, fastX(e.x, dx, dist), fastY(e.y, dy, dist), angle = atan2(dy, dx), radius = 32f, life = 0.2f, color = 0xFFB06060.toInt())
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
                projectiles.add(
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
                    )
                )
            }
        } else {
            projectiles.add(
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
                hitPlayer(e.hit * 1.35, e.element, false, e.x, e.y, heavy = true)
            }
        }
    }

    private fun updateDots(dt: Float) {
        for (e in enemies) {
            if (!e.alive || e.dot <= 0.0) continue
            e.dotTimer -= dt
            val tick = e.dot * dt
            e.hp -= tick
            e.dot -= tick
            damageDealt += tick
            if (e.dotTimer <= 0f) {
                e.dotTimer = 0.35
                addPopup(e.x + (rand.nextFloat() - 0.5f) * 22f, e.y - 52f * e.scale, formatShort(tick), false, 0.55f, DamageElement.CHAOS.color, 0.78f)
            }
            if (e.hp <= 0) killEnemy(e)
        }
    }

    // ------------------------------------------------------------ 피해 처리

    /** 룬 보호막이 있으면 피해를 먼저 흡수한다. */
    private fun applyShield(e: Enemy, damage: Double): Double {
        if (e.shield <= 0.0) return damage
        val absorbed = min(e.shield, damage * 0.6)
        e.shield -= absorbed
        return damage - absorbed
    }

    private fun hitEnemy(e: Enemy, damage: Double, crit: Boolean, angle: Float, scale: Float = 1f) {
        if (!e.alive) return
        // 감전된 적은 피해를 더 받는다
        val dealt = applyShield(e, if (e.shocked) damage * 1.15 else damage)
        e.hp -= dealt
        damageDealt += dealt
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
        leechPool += dealt * 0.006
        onSkillHit(e, crit, angle)
        applyAilments(e, crit)
        if (crit) {
            // 한 방의 피해가 클수록 메타 에너지가 많이 찬다 (절대 피해 기준).
            fireEvent(TriggerCondition.CRIT, e, dealt / REFERENCE_HIT)
        }
        if (e.hp <= 0) killEnemy(e)
    }

    /** 명중한 피해 속성으로 상태이상을 굴린다. 주 속성은 확률이 높고 다른 속성도 낮은 확률로 걸린다. */
    private fun applyAilments(e: Enemy, crit: Boolean) {
        val element = stats.fx.element
        fun roll(ailment: DamageElement, match: Double, other: Double): Boolean {
            val base = if (element == ailment) match else other
            val chance = base + if (crit) 0.35 else 0.0
            return rand.nextDouble() < chance
        }
        if (roll(DamageElement.LIGHTNING, 0.30, 0.10)) {
            e.shockT = max(e.shockT, 4.0f)
            fireEvent(TriggerCondition.SHOCK, e, 0.9)
            addPopup(e.x, e.y - 74f * e.scale, "감전", false, 0.5f, 0xFFFFE272.toInt(), 0.72f)
        }
        if (roll(DamageElement.COLD, 0.22, 0.07)) {
            e.freezeT = max(e.freezeT, 2.4f)
            fireEvent(TriggerCondition.FREEZE, e, 0.8)
            addPopup(e.x + 16f, e.y - 74f * e.scale, "빙결", false, 0.5f, 0xFF9BE8FF.toInt(), 0.72f)
            addFx(FxKind.ICE_CONE, e.x, e.y, radius = 26f, life = 0.3f, color = 0xFFBFF4FF.toInt())
        }
        if (roll(DamageElement.FIRE, 0.30, 0.09)) {
            val ignite = baseHitDamage() * 0.22
            e.igniteT = max(e.igniteT, 3.2f)
            e.igniteDps = max(e.igniteDps, ignite)
            fireEvent(TriggerCondition.IGNITE, e, 0.9)
            addPopup(e.x - 14f, e.y - 74f * e.scale, "점화", false, 0.5f, 0xFFFF8A3D.toInt(), 0.72f)
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
        val scaled = weight * TriggerRuntime.CAST_COST
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

    private fun dealAreaDamage(center: Enemy, damage: Double, radius: Float, element: DamageElement, label: String, rt: TriggerRuntime? = null) {
        var hits = 0
        for (other in enemies) {
            if (!other.alive) continue
            if (hypot(other.x - center.x, other.y - center.y) > radius) continue
            val taken = applyShield(other, damage * if (other === center) 1.0 else 0.62)
            other.hp -= taken
            other.hitFlash = 1f
            damageDealt += taken
            triggerDamage += taken
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
        leechPool += damage * 0.006
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
            addBurst(strike.x, strike.y, 18, 0xFFBFF4FF.toInt(), 300f, 0.75f, gravity = -60f)
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
                // 얼음 파편이 주변 적에게도 날아간다
                nearbyEnemies(e, 96f, 2).forEach { other ->
                    val tickDamage = baseHitDamage() * 0.30
                    other.hp -= tickDamage
                    damageDealt += tickDamage
                    other.hitFlash = 0.8f
                    addFx(FxKind.BOLT, e.x, e.y, x2 = other.x, y2 = other.y, life = 0.12f, color = 0xFF9BE8FF.toInt(), seed = rand.nextInt(), width = 0.5f)
                    if (other.hp <= 0) killEnemy(other)
                }
            }
            SkillEffectType.LIGHTNING -> {
                addFx(FxKind.BOLT, e.x, e.y - 40f, x2 = e.x + (rand.nextFloat() - 0.5f) * 40f, y2 = e.y + 20f, life = 0.18f, color = 0xFFFFF3A8.toInt(), seed = rand.nextInt(), width = 1.2f)
                addBurst(e.x, e.y, 6, 0xFFFFE272.toInt(), 190f, 0.4f)
                // 연쇄 번개
                val chain = nearbyEnemies(e, 155f, 2)
                var prev = e
                chain.forEachIndexed { index, other ->
                    addFx(FxKind.BOLT, prev.x, prev.y - 24f, x2 = other.x, y2 = other.y - 24f, life = 0.2f, color = 0xFFFFE272.toInt(), seed = rand.nextInt(), width = 1.0f)
                    val chainDamage = baseHitDamage() * (0.55 - index * 0.15)
                    other.hp -= chainDamage
                    damageDealt += chainDamage
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
                addBurst(e.x, e.y, 12, 0xFFFFA64D.toInt(), 250f, 0.75f, gravity = -90f)
                nearbyEnemies(e, 86f, 3).forEach { other ->
                    val splash = baseHitDamage() * 0.35
                    other.hp -= splash
                    damageDealt += splash
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
        e.alive = false
        e.hp = 0.0
        kills++
        addBurst(e.x, e.y, if (e.kind == EnemyKind.BOSS) 40 else 12, 0xFFE8D9B8.toInt(), if (e.kind == EnemyKind.BOSS) 420f else 250f, 0.9f)
        addFx(FxKind.RING, e.x, e.y, radius = if (e.kind == EnemyKind.BOSS) 130f else 40f, life = 0.5f, color = 0xFFFFC24D.toInt())
        if (e.kind == EnemyKind.BOSS) shake = 10f
        // 전령류·근접 처치 시 시전은 처치 사건으로 돈다
        fireEvent(TriggerCondition.KILL, null, 0.6)
        // 소환수 빌드에서 적을 잡으면 소환수가 쓰러진 것으로 쳐 준다
        if (stats.fx == SkillEffectType.MINION) fireEvent(TriggerCondition.MINION_DEATH, null, 0.6)
    }

    private fun hitPlayer(raw: Double, element: DamageElement, canEvade: Boolean, fromX: Float, fromY: Float, heavy: Boolean = false) {
        if (state != State.RUNNING) return
        if (canEvade && rand.nextDouble() * 100.0 < stats.evade) {
            evades++
            addPopup(player.x + (rand.nextFloat() - 0.5f) * 16f, player.y - 62f, "회피", false, 0.7f, 0xFF8CE8C0.toInt(), 0.86f)
            fireEvent(TriggerCondition.DODGE, null, 1.0)
            return
        }
        val mit = when (element) {
            DamageElement.PHYSICAL -> stats.physRed
            DamageElement.FIRE -> stats.fireRes
            DamageElement.COLD -> stats.coldRes
            DamageElement.LIGHTNING -> stats.lightRes
            DamageElement.CHAOS -> stats.chaosRes
        }
        val reduction = (1.0 - mit.coerceIn(-60.0, 90.0) / 100.0)
        val dmg = raw * reduction
        var rest = dmg
        if (player.es > 0) {
            val absorbed = min(player.es, rest)
            player.es -= absorbed
            rest -= absorbed
        }
        player.life -= rest
        player.esDelay = 2.6f
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
        shake = max(shake, if (heavy) 8f else 2.2f)
        addFx(FxKind.SLASH, fromX, fromY, angle = atan2(player.y - fromY, player.x - fromX), radius = 34f, life = 0.18f, color = 0xFFFF6A6A.toInt())
    }

    // ------------------------------------------------------------ 투사체

    private fun updateProjectiles(dt: Float) {
        var i = 0
        while (i < projectiles.size) {
            val p = projectiles[i]
            p.life -= dt
            if (p.life <= 0f) {
                projectiles.removeAt(i)
                continue
            }
            if (p.hostile) {
                p.x += p.dirX * p.speed * dt
                p.y += p.dirY * p.speed * dt
                val d = hypot(player.x - p.x, player.y - p.y)
                if (d < 24f) {
                    hitPlayer(p.damage, p.element, true, p.x, p.y)
                    addBurst(p.x, p.y, 5, p.element.color, 150f, 0.35f)
                    projectiles.removeAt(i)
                    continue
                }
                if (p.x < -40 || p.x > W + 40 || p.y < -40 || p.y > H + 40) {
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
                val ttlEnd = p.target == null || !p.target.alive
                if (near || ttlEnd) {
                    if (t != null && t.alive) {
                        hitEnemy(t, p.damage, p.crit, atan2(p.dirY, p.dirX))
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
            projectiles.add(
                Projectile(
                    x = b.x + cos(a) * 60f, y = b.y + sin(a) * 60f,
                    dirX = cos(a), dirY = sin(a), speed = 300f,
                    damage = b.hit * 0.95, crit = false, target = null, hostile = true,
                    element = DamageElement.LIGHTNING, kind = ProjKind.ENEMY_BOLT,
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
            projectiles.add(
                Projectile(
                    x = b.x, y = b.y - 46f * b.scale,
                    dirX = cos(a), dirY = sin(a), speed = speed,
                    damage = b.hit * 0.8, crit = false, target = null, hostile = true,
                    element = element, kind = ProjKind.ENEMY_BOLT,
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
            hitPlayer(t.damage, t.element, true, t.x, t.y, heavy = true)
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
        )
    }

}
