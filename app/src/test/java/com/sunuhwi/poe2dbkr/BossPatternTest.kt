package com.sunuhwi.poe2dbkr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 우버 보스 5종의 고유 패턴·텔레그래프·2페이즈를 JVM에서 확인한다.
 * (화면 없이 시뮬레이션만 돌리므로 장판 판정과 페이즈 전환은 그대로 검증된다.)
 */
class BossPatternTest {

    private fun stats(dps: Double = 1_200_000.0, life: Double = 60_000.0) = BattlePlayerStats(
        className = "테스트",
        skillName = "Lightning Arrow",
        fx = SkillEffectType.LIGHTNING,
        weapon = WeaponVisualType.BOW,
        dps = dps,
        aps = 3.2,
        critChance = 40.0,
        critMulti = 320.0,
        life = life,
        es = 5_000.0,
        physRed = 55.0,
        evade = 25.0,
        fireRes = 70.0,
        coldRes = 70.0,
        lightRes = 70.0,
        chaosRes = 20.0,
        ehp = 90_000.0,
        dpsBase = dps,
    )

    private fun worldFor(boss: BossPreset, dps: Double = 1_200_000.0) =
        BattleWorld(stats(dps), BattleMode.BOSS, 0, false, boss)

    private fun tick(world: BattleWorld, seconds: Double) {
        repeat((seconds * 60).toInt()) { world.update(1.0 / 60.0) }
    }

    @Test
    fun everyBossUsesItsOwnPatterns() {
        val expected = mapOf(
            "arbiter" to listOf("화염 검기", "불기둥"),
            "xesht" to listOf("균열 구체 연사", "거대 손 내려찍기"),
            "king" to listOf("의식 투사체", "순간이동"),
            "zarokh" to listOf("시간 구체", "모래시계"),
            "olroth" to listOf("룬 검기", "빙결 파편"),
        )
        BossPresets.ALL.forEach { boss ->
            val world = worldFor(boss)
            tick(world, 25.0)
            println("[TEST] ${boss.id} patterns=${world.patternHistory.distinct()} phase=${world.bossPhase} telegraphs=${world.telegraphsSpawned}")
            assertTrue("${boss.name} 패턴이 없다", world.patternHistory.isNotEmpty())
            val names = expected.getValue(boss.id)
            names.forEach { name ->
                assertTrue("${boss.name} 에 '$name' 패턴이 없다: ${world.patternHistory}", world.patternHistory.contains(name))
            }
            assertTrue("${boss.name} 예고 장판이 없다", world.telegraphsSpawned > 0)
        }
    }

    private val phaseTwoPatterns = mapOf(
        "arbiter" to listOf("심판의 안전지대", "유성 낙하"),
        "xesht" to listOf("균열 쫄 소환"),
        "king" to listOf("저주 장판", "분신"),
        "zarokh" to listOf("시간 왜곡", "번개 구슬 회전"),
        "olroth" to listOf("룬 보호막", "광역 냉기 폭발"),
    )

    @Test
    fun secondPhaseUnlocksExtraPatterns() {
        BossPresets.ALL.forEach { boss ->
            // 보스가 일찍 죽지 않도록 낮은 DPS로 오래 때린다.
            val world = worldFor(boss, dps = 400_000.0)
            tick(world, 120.0)
            println("[TEST] ${boss.id} phase=${world.bossPhase} hp=${world.bossEnemy?.hp} state=${world.state} patterns=${world.patternHistory.distinct()}")
            assertEquals("${boss.name} 2페이즈로 넘어가지 않았다", 2, world.bossPhase)
            val names = phaseTwoPatterns.getValue(boss.id)
            assertTrue(
                "${boss.name} 2페이즈 전용 패턴이 나오지 않았다: ${world.patternHistory}",
                names.any { world.patternHistory.contains(it) },
            )
        }
    }

    @Test
    fun arbiterSecondPhaseHasSafeZoneAndMeteors() {
        val arbiter = BossPresets.ALL.first { it.id == "arbiter" }
        val world = worldFor(arbiter, dps = 400_000.0)
        tick(world, 120.0)
        val names = world.patternHistory.distinct()
        println("[TEST] arbiter=$names")
        assertTrue(names.contains("심판의 안전지대"))
        assertTrue(names.contains("유성 낙하"))
    }

    @Test
    fun olrothShieldPhaseReducesDamage() {
        val olroth = BossPresets.ALL.first { it.id == "olroth" }
        val world = worldFor(olroth, dps = 400_000.0)
        tick(world, 120.0)
        println("[TEST] olroth patterns=${world.patternHistory.distinct()} shield=${world.bossEnemy?.shield}")
        assertTrue(world.patternHistory.contains("룬 보호막"))
        assertTrue(world.patternHistory.contains("광역 냉기 폭발"))
    }

    @Test
    fun telegraphShapesContainCorrectPoints() {
        val circle = Telegraph(TelegraphShape.CIRCLE, 100f, 100f, 0f, 50f, 0f, 1f, 1.0, DamageElement.FIRE, "원")
        assertTrue(circle.contains(120f, 120f))
        assertFalse(circle.contains(200f, 100f))

        val line = Telegraph(TelegraphShape.LINE, 100f, 100f, 0f, 20f, 200f, 1f, 1.0, DamageElement.FIRE, "직선")
        assertTrue(line.contains(250f, 110f))
        assertFalse(line.contains(250f, 160f))
        assertFalse(line.contains(50f, 100f))

        val donut = Telegraph(TelegraphShape.DONUT, 100f, 100f, 0f, 40f, 120f, 1f, 1.0, DamageElement.CHAOS, "고리")
        assertTrue(donut.contains(180f, 100f))
        assertFalse(donut.contains(110f, 100f))
        assertFalse(donut.contains(300f, 100f))

        val safe = Telegraph(TelegraphShape.SAFE_CIRCLE, 320f, 390f, 0f, 90f, 0f, 1f, 1.0, DamageElement.FIRE, "안전")
        assertFalse(safe.contains(330f, 400f))
        assertTrue(safe.contains(600f, 700f))

        val cross = Telegraph(TelegraphShape.CROSS, 100f, 100f, 0f, 10f, 150f, 1f, 1.0, DamageElement.LIGHTNING, "십자")
        assertTrue(cross.contains(200f, 100f))
        assertTrue(cross.contains(100f, 220f))
        assertFalse(cross.contains(180f, 180f))
    }

    @Test
    fun telegraphHitsPlayerInsideAndSparesPlayerOutside() {
        val boss = BossPresets.ALL.first { it.id == "arbiter" }
        val inside = worldFor(boss)
        inside.player.dodgeCooldown = 999f
        inside.telegraphs.add(
            Telegraph(TelegraphShape.CIRCLE, inside.player.x, inside.player.y, 0f, 120f, 0f, 0.6f, 4000.0, DamageElement.FIRE, "테스트 장판")
        )
        val before = inside.player.life + inside.player.es
        tick(inside, 0.9)
        val after = inside.player.life + inside.player.es
        println("[TEST] inside before=$before after=$after hitsTaken=${inside.hitsTaken}")
        assertTrue("안에 서 있는데 피해가 없다", after < before)

        val outside = worldFor(boss)
        outside.player.dodgeCooldown = 999f
        outside.telegraphs.add(
            Telegraph(TelegraphShape.CIRCLE, outside.player.x + 260f, outside.player.y + 260f, 0f, 60f, 0f, 0.6f, 4000.0, DamageElement.FIRE, "먼 장판")
        )
        val beforeOut = outside.player.life + outside.player.es
        tick(outside, 0.9)
        val afterOut = outside.player.life + outside.player.es
        println("[TEST] outside before=$beforeOut after=$afterOut hitsTaken=${outside.hitsTaken}")
        assertEquals("밖에 있는데 맞았다", beforeOut, afterOut, 1.0)
    }

    @Test
    fun playerRollsAwayFromTelegraph() {
        val boss = BossPresets.ALL.first { it.id == "zarokh" }
        val world = worldFor(boss)
        world.telegraphs.add(
            Telegraph(TelegraphShape.CIRCLE, world.player.x, world.player.y, 0f, 140f, 0f, 1.2f, 3000.0, DamageElement.LIGHTNING, "구르기 확인")
        )
        tick(world, 0.9)
        println("[TEST] dashTimer=${world.player.dashTimer} cooldown=${world.player.dodgeCooldown} pos=${world.player.x},${world.player.y}")
        assertTrue("장판을 보고 구르지 않았다", world.player.dodgeCooldown > 0f || world.player.dashTimer > 0f)
    }

    @Test
    fun phasesAndPatternsAreExposedForHud() {
        val king = BossPresets.ALL.first { it.id == "king" }
        val world = worldFor(king, dps = 400_000.0)
        tick(world, 60.0)
        println("[TEST] hud pattern=${world.bossPatternName} phase=${world.bossPhase}")
        assertTrue(world.bossPatternName.isNotBlank())
        assertTrue(world.bossPhase >= 1)
    }
}
