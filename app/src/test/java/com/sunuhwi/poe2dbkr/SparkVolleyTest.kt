package com.sunuhwi.poe2dbkr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * 전기불꽃(Spark) — poe2db.tw/kr/Spark 기준 확인.
 *
 * 투사체 (4—9)개 발사, 지속시간 2초, 냉기 주입을 소모한 나선(원형) 발사,
 * 명중하거나 만료될 때까지 천방지축으로 움직이는 궤적을 시뮬레이션과 렌더링이
 * 그대로 따르는지 본다. 인게임 스샷처럼 화면 사방이 전기 줄기로 채워져야 한다.
 */
class SparkVolleyTest {

    private fun stats(
        skill: String = "전기불꽃",
        dps: Double = 900_000.0,
        aps: Double = 2.4,
        extra: Int = 0,
        level: Int = 20,
    ) = BattlePlayerStats(
        className = "Sorceress · Stormweaver",
        skillName = skill,
        fx = SkillEffectType.LIGHTNING,
        weapon = WeaponVisualType.WAND,
        form = AttackForm.CAST,
        dps = dps,
        aps = aps,
        critChance = 30.0,
        critMulti = 260.0,
        life = 3_200.0,
        es = 900.0,
        physRed = 20.0,
        evade = 25.0,
        fireRes = 75.0,
        coldRes = 75.0,
        lightRes = 75.0,
        chaosRes = 10.0,
        ehp = 22_000.0,
        dpsBase = dps,
        extraProjectiles = extra,
        skillLevel = level,
    )

    private fun tick(world: BattleWorld, seconds: Double) {
        repeat((seconds * 60).toInt()) { world.update(1.0 / 60.0) }
    }

    /** 첫 시전이 깔린 프레임의 전기불꽃을 그대로 잡는다 */
    private fun firstVolley(world: BattleWorld): List<Projectile> {
        repeat(120) {
            world.update(1.0 / 60.0)
            val live = world.projectiles.filter { it.kind == ProjKind.SPARK }
            if (live.size >= SparkMath.SALVO_AT_MAX_LEVEL) return live
        }
        return emptyList()
    }

    @Test
    fun poe2dbProjectileCountScalesWithGemLevel() {
        assertEquals(4, SparkMath.baseProjectiles(1))
        assertEquals(9, SparkMath.baseProjectiles(20))
        assertEquals(SparkMath.SALVO_AT_MAX_LEVEL, SparkMath.salvo(20, 0))
        assertTrue("20레벨 나선 시전이 16발보다 적다", SparkMath.salvo(20, 0) >= 16)
        assertEquals(20, SparkMath.salvo(20, 1))
        assertTrue("추가 투사체가 겹마다 붙지 않는다", SparkMath.salvo(20, 3) > SparkMath.salvo(20, 1))
        // 시전 1회 몫을 발수로 나누므로 줄기 하나의 피해는 작나, 합치면 원래 몫을 넘지 않는다.
        val count = SparkMath.salvo(20, 0)
        val per = SparkMath.perHitDamage(1_000_000.0, count)
        assertTrue(per > 0.0)
        assertTrue("피해가 시전 1회 몫을 넘는다", per * count <= 1_000_000.0)
    }

    @Test
    fun oneCastFillsTheScreenInEveryDirection() {
        val world = BattleWorld(stats(), BattleMode.MAP, 25, false, null, seed = 4242L)
        val volley = firstVolley(world)
        assertNotNull(volley)
        println("[TEST] 전기불꽃 첫 시전=${volley.size}발 최대발수=${world.sparkSalvo}")
        assertTrue("한 번에 16발 이상 나가지 않았다: ${volley.size}", volley.size >= 16)

        // 사방으로 퍼졌는지 — 네 사분면을 모두 덮고, 이웃한 줄기 사이 각도가 크게 벌어지지 않아야 한다.
        val angles = volley.map { atan2(it.dirY, it.dirX) }
            .map { if (it < 0) it + (2 * PI).toFloat() else it }
            .sorted()
        val quadrants = volley.map {
            (if (it.dirX >= 0) 1 else 0) + (if (it.dirY >= 0) 2 else 0)
        }.toSet()
        assertEquals("전기불꽃이 사방으로 퍼지지 않았다: $quadrants", 4, quadrants.size)
        var maxGap = 0f
        for (i in angles.indices) {
            val next = if (i + 1 < angles.size) angles[i + 1] else angles[0] + (2 * PI).toFloat()
            maxGap = maxOf(maxGap, next - angles[i])
        }
        println("[TEST] 사분면=$quadrants 최대 각도 간격=${Math.toDegrees(maxGap.toDouble()).toInt()}도")
        assertTrue("줄기가 한 곳에 뭉쳤다 (최대 간격 ${Math.toDegrees(maxGap.toDouble())}도)", maxGap < 1.4f)
    }

    @Test
    fun everySparkCarriesDamageAndPierces() {
        val world = BattleWorld(stats(), BattleMode.MAP, 25, false, null, seed = 4242L)
        val volley = firstVolley(world)
        assertTrue("첫 시전이 없다", volley.isNotEmpty())
        // 예전에는 첫 덩어리만 피해를 들고 나머지는 연출뿐이었다 — 지금은 전부 피해를 준다.
        assertTrue("피해가 0인 전기불꽃이 있다", volley.all { it.damage > 0.0 })
        assertEquals("꿰뚫기 횟수가 poe2db 기준과 다르다", SparkMath.PIERCE, volley.first().pierce)
        assertEquals("벽 튕김 횟수가 다르다", SparkMath.BOUNCES, volley.first().bounces)

        tick(world, 3.5)
        println("[TEST] 시전=${world.sparkCasts} 발수=${world.sparkSalvo} 명중=${world.sparkHits} 피해=${world.damageDealt.toLong()}")
        assertTrue("시전이 한 번도 없다", world.sparkCasts > 0)
        assertTrue(
            "여러 줄기가 함께 맞지 않았다 (시전 ${world.sparkCasts} · 명중 ${world.sparkHits})",
            world.sparkHits > world.sparkCasts * 5,
        )
        assertTrue("피해가 없다", world.damageDealt > 0.0)
    }

    @Test
    fun sparkCrawlsInIrregularZigzags() {
        // 적을 한 마리만 세워 줄기가 적에게 닿아 일찍 사라지는 일을 줄인다.
        val world = BattleWorld(stats(), BattleMode.MAP, 1, false, null, seed = 4242L)
        val volley = firstVolley(world)
        val spark = volley.firstOrNull() ?: error("전기불꽃이 나가지 않았다")
        val startX = spark.x
        val startY = spark.y
        var last = atan2(spark.dirY, spark.dirX)
        var turns = 0
        var maxTurn = 0f
        var travelled = 0f
        var px = spark.x
        var py = spark.y
        val path = ArrayList<Float>()
        var frames = 0
        while (world.projectiles.any { it === spark } && frames < 90) {
            world.update(1.0 / 60.0)
            frames++
            if (world.projectiles.none { it === spark }) break
            path.add(spark.x)
            path.add(spark.y)
            travelled += hypot(spark.x - px, spark.y - py)
            px = spark.x
            py = spark.y
            val heading = atan2(spark.dirY, spark.dirX)
            var delta = abs(heading - last)
            if (delta > PI) delta = (2 * PI - delta).toFloat()
            if (delta > 0.25f) turns++
            maxTurn = maxOf(maxTurn, delta)
            last = heading
        }
        // 곧게 간 줄기에서 얼마나 벗어났는지 (시작→끝 직선과의 최대 거리)
        val endX = px
        val endY = py
        val lineLen = hypot(endX - startX, endY - startY).coerceAtLeast(1f)
        var lateral = 0f
        var i = 0
        while (i < path.size) {
            val x = path[i]
            val y = path[i + 1]
            val dist = abs((endY - startY) * (x - startX) - (endX - startX) * (y - startY)) / lineLen
            lateral = maxOf(lateral, dist)
            i += 2
        }
        println(
            "[TEST] 줄기 이동=${travelled.toInt()}px 꺾임=$turns 최대 ${Math.toDegrees(maxTurn.toDouble()).toInt()}도 " +
                "직선 이탈=${lateral.toInt()}px",
        )
        assertTrue("줄기가 꺾이지 않는다 (꺾임 $turns)", turns >= 4)
        assertTrue("꺾임이 불규칙할 만큼 크지 않다", maxTurn > 0.5f)
        assertTrue("줄기가 직선으로만 간다 (이탈 ${lateral}px)", lateral > 20f)
    }

    @Test
    fun sparksLastTwoSecondsThenVanish() {
        val world = BattleWorld(stats(aps = 1.0), BattleMode.MAP, 12, false, null, seed = 4242L)
        val volley = firstVolley(world)
        assertTrue(volley.isNotEmpty())
        val first = volley.first()
        assertEquals(SparkMath.DURATION.toDouble(), first.life.toDouble(), 0.3)
        // 1.6초에는 아직 살아 있고, 2.2초가 지나면 모두 사라진다.
        tick(world, 1.4)
        val aliveAt14 = world.projectiles.any { it === first }
        tick(world, 1.2)
        val aliveAt26 = world.projectiles.any { it === first }
        println("[TEST] 1.4초 생존=$aliveAt14 · 2.6초 생존=$aliveAt26")
        assertTrue("전기불꽃이 1.4초에 벌써 사라졌다", aliveAt14)
        assertTrue("전기불꽃이 2.6초까지 남아 있다", !aliveAt26)
    }

    @Test
    fun castLeavesBranchingBoltsAroundThePlayer() {
        val world = BattleWorld(stats(), BattleMode.MAP, 25, false, null, seed = 4242L)
        var bolts = 0
        var webs = 0
        repeat(30) {
            world.update(1.0 / 60.0)
            bolts = maxOf(bolts, world.effects.count { fx -> fx.kind == FxKind.BOLT })
            webs = maxOf(webs, world.effects.count { fx -> fx.kind == FxKind.ARC_WEB })
        }
        println("[TEST] 시전 연출 BOLT=$bolts ARC_WEB=$webs")
        assertTrue("사방으로 갈라지는 번개 연출이 없다", bolts >= 5)
        assertTrue("번개 그물 연출이 없다", webs >= 1)
    }
}
