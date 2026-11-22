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

/** PoB에서 뽑아 전투에 넣는 플레이어 수치 */
data class BattlePlayerStats(
    val className: String,
    val skillName: String,
    val fx: SkillEffectType,
    val weapon: WeaponVisualType,
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
    var life = stats.life
    var es = stats.es
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
}

enum class FxKind {
    SLASH, ICE_CONE, RING, BOLT, PILLAR, ORB, AURA, SPEAR, SHOCKWAVE, CYCLONE, MINION_HIT, CLOUD, GROUND_CRACK, METEOR,
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

enum class ProjKind { ICE_ARROW, LIGHT_ARROW, FIRE_ORB, CHAOS_ORB, MINION_ORB, BOLT, ENEMY_BOLT }

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
)

/** 트리거(메타) 스킬 하나의 전투 중 상태 */
class TriggerRuntime(val spec: TriggerSpec, private val critRate: Double) {
    var energy = 0.0
        private set
    var cooldown = 0f
        private set
    var casts = 0
        private set
    private var nextSkill = 0

    /** 조건을 몇 번 채워야 한 번 터지는지 */
    val threshold: Double = when (spec.kind.condition) {
        TriggerCondition.CRIT -> 4.0
        TriggerCondition.MINION_DEATH -> 1.0
        else -> 3.0
    }
    val cooldownMax: Float = when (spec.kind) {
        TriggerKind.CAST_ON_CRIT -> 0.35f
        else -> 0.5f
    }

    /** 트리거가 자연히 터지는 간격(초) */
    val interval: Double = maxOf(cooldownMax.toDouble(), threshold / critRate.coerceAtLeast(0.08))
        .coerceAtMost(4.0)

    fun tick(dt: Float) {
        if (cooldown > 0f) cooldown = maxOf(0f, cooldown - dt)
    }

    fun addEnergy(amount: Double = 1.0) {
        energy += amount
    }

    fun ready(): Boolean = energy >= threshold && cooldown <= 0f

    fun consume(): TriggeredSkill? {
        if (spec.skills.isEmpty()) return null
        val skill = spec.skills[nextSkill % spec.skills.size]
        nextSkill++
        energy = 0.0
        cooldown = cooldownMax
        casts++
        return skill
    }

    /** 한 번 발동할 때의 피해: 그 스킬의 지속 DPS × 발동 간격 */
    fun castDamage(skill: TriggeredSkill): Double = (skill.dps * interval).coerceAtLeast(1.0)
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

        fun formatShort(value: Double): String = when {
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

    private class PendingStrike(
        val x: Float,
        val y: Float,
        val radius: Float,
        val damage: Double,
        val element: DamageElement,
        val name: String,
        val kind: FxKind,
        var delay: Float,
    )

    private val pendingStrikes = ArrayList<PendingStrike>()

    val totalEnemies: Int get() = enemies.size
    val aliveCount: Int get() = enemies.count { it.alive }
    val bossEnemy: Enemy? get() = enemies.firstOrNull { it.boss != null }
    val maxPool: Double get() = stats.life + stats.es

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
        updateEnemies(dt)
        updateProjectiles(dt)
        updateDots(dt)
        updateTriggers(dt)
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
            player.life = min(stats.life, player.life + stats.life * 0.009 * dt)
            if (leechPool > 0.0) {
                val heal = min(leechPool, stats.life * 0.03 * dt)
                leechPool -= heal
                player.life = min(stats.life, player.life + heal)
            }
        }
        player.esDelay = max(0f, player.esDelay - dt)
        if (player.esDelay <= 0f && player.es < stats.es) {
            player.es = min(stats.es, player.es + stats.es * 0.22 * dt)
        }
        player.attackAnim = max(0f, player.attackAnim - dt / 0.26f)
        player.dodgeCooldown = max(0f, player.dodgeCooldown - dt)

        val nearest = nearestEnemy()
        var moving = false

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
            val desired = when {
                stats.fx == SkillEffectType.MELEE -> 58f
                stats.fx == SkillEffectType.MINION -> 175f
                else -> 163f
            }
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

        if (moving) player.walkPhase += dt * 11f
        player.moving = moving
        player.x = player.x.coerceIn(44f, W - 44f)
        player.y = player.y.coerceIn(84f, H - 34f)

        // 공격
        player.attackTimer -= dt
        if (player.attackTimer <= 0f) {
            val target = nearest
            if (target != null) {
                player.attackTimer = (1.0 / stats.aps.coerceIn(0.2, 12.0)).toFloat()
                player.attackAnim = 1f
                player.attackCount++
                fireAttack(target)
            } else {
                player.attackTimer = 0.1f
            }
        }
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
        val critFactor = 1.0 + (stats.critChance / 100.0) * (stats.critMulti / 100.0 - 1.0)
        val perHit = stats.dps / stats.aps
        return (perHit / critFactor).coerceAtLeast(1.0)
    }

    private fun rollDamage(): Pair<Double, Boolean> {
        val crit = rand.nextDouble() * 100.0 < stats.critChance
        val base = baseHitDamage() * (0.92 + rand.nextDouble() * 0.16)
        return (if (crit) base * stats.critMulti / 100.0 else base) to crit
    }

    private fun fireAttack(target: Enemy) {
        val (damage, crit) = rollDamage()
        val angle = atan2(target.y - player.y, target.x - player.x)

        when (stats.fx) {
            SkillEffectType.MELEE -> {
                addFx(FxKind.SLASH, player.x, player.y, angle = angle, radius = 74f, life = 0.26f, color = DamageElement.PHYSICAL.color, width = 1.4f)
                addFx(FxKind.SHOCKWAVE, target.x, target.y, radius = 46f, life = 0.42f, color = 0xFFBFC7D5.toInt())
                val inArc = enemies.filter { e ->
                    e.alive && hypot(e.x - player.x, e.y - player.y) < 96f &&
                        abs(normalize(atan2(e.y - player.y, e.x - player.x) - angle)) < 1.15f
                }.sortedBy { hypot(it.x - player.x, it.y - player.y) }.take(5)
                if (inArc.isEmpty()) {
                    hitEnemy(target, damage, crit, angle, 1f)
                } else {
                    inArc.forEachIndexed { i, e ->
                        hitEnemy(e, if (i == 0) damage else damage * 0.55, crit && i == 0, angle, if (i == 0) 1f else 0.7f)
                    }
                }
                shake = max(shake, 2.4f)
                if (crit) shake = max(shake, 5.2f)
            }
            SkillEffectType.ICE -> {
                spawnProjectile(
                    projectileKind(DamageElement.COLD), target, damage, crit, DamageElement.COLD,
                    speed = if (stats.weapon == WeaponVisualType.CROSSBOW) 1240f else 880f, angle = angle,
                )
            }
            SkillEffectType.LIGHTNING -> {
                spawnProjectile(
                    projectileKind(DamageElement.LIGHTNING), target, damage, crit, DamageElement.LIGHTNING,
                    speed = if (stats.weapon == WeaponVisualType.CROSSBOW) 1380f else 1080f, angle = angle,
                )
                addFx(FxKind.BOLT, player.x + cos(angle) * 26f, player.y + sin(angle) * 26f,
                    x2 = target.x, y2 = target.y, life = 0.14f, color = DamageElement.LIGHTNING.color, seed = rand.nextInt())
            }
            SkillEffectType.FIRE -> {
                spawnProjectile(
                    projectileKind(DamageElement.FIRE), target, damage, crit, DamageElement.FIRE,
                    speed = if (stats.weapon == WeaponVisualType.CROSSBOW) 980f else 560f, angle = angle,
                )
            }
            SkillEffectType.CHAOS -> {
                spawnProjectile(
                    projectileKind(DamageElement.CHAOS), target, damage, crit, DamageElement.CHAOS,
                    speed = if (stats.weapon == WeaponVisualType.CROSSBOW) 1040f else 470f, angle = angle,
                )
            }
            SkillEffectType.MINION -> {
                val fan = listOf(target) + nearbyEnemies(target, 240f, 2)
                fan.forEachIndexed { i, e ->
                    val spread = (i - (fan.size - 1) / 2f) * 0.20f
                    spawnProjectile(
                        ProjKind.MINION_ORB, e, if (i == 0) damage else damage * 0.42,
                        crit && i == 0, DamageElement.PHYSICAL,
                        speed = 600f, angle = angle + spread, from = player,
                    )
                }
            }
        }
    }

    private fun normalize(a: Float): Float {
        var v = a
        while (v > PI_F) v -= 2f * PI_F
        while (v < -PI_F) v += 2f * PI_F
        return v
    }

    private fun projectileKind(element: DamageElement): ProjKind = when (stats.weapon) {
        WeaponVisualType.CROSSBOW -> ProjKind.BOLT
        WeaponVisualType.BOW -> when (element) {
            DamageElement.COLD -> ProjKind.ICE_ARROW
            DamageElement.FIRE -> ProjKind.FIRE_ORB
            else -> ProjKind.LIGHT_ARROW
        }
        else -> when (element) {
            DamageElement.COLD -> ProjKind.ICE_ARROW
            DamageElement.FIRE -> ProjKind.FIRE_ORB
            DamageElement.CHAOS -> ProjKind.CHAOS_ORB
            else -> ProjKind.LIGHT_ARROW
        }
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

    private fun updateEnemies(dt: Float) {
        for (e in enemies) {
            if (!e.alive) continue
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
                val sp = e.speed * pull * (if (e.windup > 0f) 0.25f else 1f)
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

    private fun hitEnemy(e: Enemy, damage: Double, crit: Boolean, angle: Float, scale: Float = 1f) {
        if (!e.alive) return
        e.hp -= damage
        damageDealt += damage
        e.hitFlash = 1f
        if (crit) {
            critCount++
            shake = max(shake, if (e.kind == EnemyKind.BOSS) 3.4f else 4.6f)
        }
        addPopup(
            e.x + (rand.nextFloat() - 0.5f) * 20f,
            e.y - 52f * e.scale - rand.nextFloat() * 10f,
            if (crit) "CRIT! ${formatShort(damage)}" else formatShort(damage),
            crit,
            if (crit) 1.0f else 0.62f,
            if (crit) 0xFFFFC24D.toInt() else 0xFFF5F1E8.toInt(),
            if (crit) 1.32f else 0.92f * scale,
        )
        leechPool += damage * 0.006
        onSkillHit(e, crit, angle)
        feedTriggers(e, crit)
        if (e.hp <= 0) killEnemy(e)
    }

    // ------------------------------------------------------------ 트리거(치명타·감전·빙결·점화 시 시전)

    private fun feedTriggers(e: Enemy, crit: Boolean) {
        if (triggers.isEmpty()) return
        val element = stats.fx.element
        for (rt in triggers) {
            if (rt.cooldown > 0f || rt.energy >= rt.threshold) continue
            val hit = when (rt.spec.kind.condition) {
                TriggerCondition.CRIT -> crit
                TriggerCondition.SHOCK -> element == DamageElement.LIGHTNING &&
                    (crit || rand.nextDouble() < 0.25)
                TriggerCondition.FREEZE -> element == DamageElement.COLD &&
                    (crit || rand.nextDouble() < 0.20)
                TriggerCondition.IGNITE -> element == DamageElement.FIRE &&
                    (crit || rand.nextDouble() < 0.30)
                TriggerCondition.MINION_DEATH -> false
            }
            if (!hit) continue
            rt.addEnergy(1.0)
            if (rt.ready()) castTrigger(rt, e)
        }
    }

    private fun updateTriggers(dt: Float) {
        for (rt in triggers) {
            rt.tick(dt)
            if (rt.ready()) {
                val target = nearestEnemy()
                if (target != null) castTrigger(rt, target)
            }
        }
    }

    private fun castTrigger(rt: TriggerRuntime, target: Enemy) {
        val skill = rt.consume() ?: return
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
            SkillEffectType.ICE -> spawnMeteor(target, damage, skill.name)
            SkillEffectType.LIGHTNING -> triggerLightning(target, damage, skill.name)
            SkillEffectType.FIRE -> triggerFire(target, damage, skill.name)
            SkillEffectType.CHAOS -> triggerChaos(target, damage, skill.name)
            SkillEffectType.MELEE -> triggerSlam(target, damage, skill.name)
            SkillEffectType.MINION -> triggerMinions(target, damage, skill.name)
        }
        addFx(FxKind.AURA, player.x, player.y, radius = 54f, life = 0.3f, color = color)
    }

    /** 혜성: 하늘에서 얼음 덩어리가 떨어져 착탄 지점에 광역 피해 */
    private fun spawnMeteor(target: Enemy, damage: Double, label: String) {
        val fall = 0.55f
        val radius = 96f
        val x = target.x + (rand.nextFloat() - 0.5f) * 18f
        val y = target.y + (rand.nextFloat() - 0.5f) * 10f
        addFx(
            FxKind.METEOR, x, y,
            radius = radius, life = fall, color = DamageElement.COLD.color,
            seed = rand.nextInt(), width = 1.2f,
        )
        addFx(FxKind.GROUND_CRACK, x, y, radius = radius, life = fall, color = 0xFF7FD8FF.toInt())
        pendingStrikes.add(
            PendingStrike(x, y, radius, damage, DamageElement.COLD, label, FxKind.METEOR, fall)
        )
    }

    private fun triggerLightning(target: Enemy, damage: Double, label: String) {
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
        dealAreaDamage(target, damage, 88f, DamageElement.LIGHTNING, label)
        shake = max(shake, 4.2f)
    }

    private fun triggerFire(target: Enemy, damage: Double, label: String) {
        addFx(FxKind.RING, target.x, target.y, radius = 92f, life = 0.5f, color = 0xFFFF8A3D.toInt(), width = 1.2f)
        addFx(FxKind.RING, target.x, target.y, radius = 52f, life = 0.36f, color = 0xFFFFD27A.toInt(), width = 0.8f)
        addBurst(target.x, target.y, 16, 0xFFFFA64D.toInt(), 280f, 0.8f, gravity = -80f)
        dealAreaDamage(target, damage, 92f, DamageElement.FIRE, label)
        shake = max(shake, 4.6f)
    }

    private fun triggerChaos(target: Enemy, damage: Double, label: String) {
        addFx(FxKind.AURA, target.x, target.y, radius = 82f, life = 0.7f, color = DamageElement.CHAOS.color)
        addBurst(target.x, target.y, 14, 0xFFC182FF.toInt(), 200f, 0.9f, gravity = -50f)
        dealAreaDamage(target, damage, 84f, DamageElement.CHAOS, label)
    }

    private fun triggerSlam(target: Enemy, damage: Double, label: String) {
        addFx(FxKind.SHOCKWAVE, target.x, target.y, radius = 78f, life = 0.45f, color = 0xFFFFD89A.toInt(), width = 1.4f)
        addBurst(target.x, target.y, 10, 0xFFFFC24D.toInt(), 230f, 0.55f)
        dealAreaDamage(target, damage, 76f, DamageElement.PHYSICAL, label)
        shake = max(shake, 3.6f)
    }

    private fun triggerMinions(target: Enemy, damage: Double, label: String) {
        val fan = listOf(target) + nearbyEnemies(target, 200f, 2)
        fan.forEachIndexed { index, enemy ->
            val angle = atan2(enemy.y - player.y, enemy.x - player.x)
            spawnProjectile(
                ProjKind.MINION_ORB, enemy, if (index == 0) damage else damage * 0.45,
                false, DamageElement.PHYSICAL, speed = 660f, angle = angle,
            )
        }
    }

    private fun dealAreaDamage(center: Enemy, damage: Double, radius: Float, element: DamageElement, label: String) {
        var hits = 0
        for (other in enemies) {
            if (!other.alive) continue
            if (hypot(other.x - center.x, other.y - center.y) > radius) continue
            val taken = damage * if (other === center) 1.0 else 0.62
            other.hp -= taken
            other.hitFlash = 1f
            damageDealt += taken
            triggerDamage += taken
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
                dealAreaDamage(target, strike.damage, strike.radius, strike.element, strike.name)
            } else {
                for (other in enemies) {
                    if (!other.alive) continue
                    if (hypot(other.x - strike.x, other.y - strike.y) > strike.radius) continue
                    val taken = strike.damage * 0.62
                    other.hp -= taken
                    other.hitFlash = 1f
                    damageDealt += taken
                    triggerDamage += taken
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
        // 소환수가 적을 잡으면 "소환수 사망 시 시전" 대신 처치 에너지로 쌓아 준다
        if (stats.fx == SkillEffectType.MINION && triggers.isNotEmpty()) {
            for (rt in triggers) {
                if (rt.spec.kind.condition != TriggerCondition.MINION_DEATH) continue
                if (rt.cooldown > 0f || rt.energy >= rt.threshold) continue
                rt.addEnergy(1.0)
                if (rt.ready()) castTrigger(rt, e)
            }
        }
    }

    private fun hitPlayer(raw: Double, element: DamageElement, canEvade: Boolean, fromX: Float, fromY: Float, heavy: Boolean = false) {
        if (state != State.RUNNING) return
        if (canEvade && rand.nextDouble() * 100.0 < stats.evade) {
            evades++
            addPopup(player.x + (rand.nextFloat() - 0.5f) * 16f, player.y - 62f, "회피", false, 0.7f, 0xFF8CE8C0.toInt(), 0.86f)
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
        )
    }

}
