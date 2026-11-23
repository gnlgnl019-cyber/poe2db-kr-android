package com.sunuhwi.poe2dbkr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot

/**
 * 사망 원인·스킬별 DPS 기록·사망 화면 페이드인과 충전 메커니즘을 확인한다.
 *
 * 화면(캔버스)은 이 값들을 읽어 그리기만 하므로, 여기서 값이 맞으면 연출도 같은 숫자를 쓴다.
 */
class DeathAndChargesTest {

    private fun stats(
        dps: Double = 480_000.0,
        aps: Double = 3.42,
        life: Double = 4_120.0,
        es: Double = 860.0,
        crit: Double = 31.6,
        critMulti: Double = 312.0,
        evade: Double = 20.0,
        element: SkillEffectType = SkillEffectType.LIGHTNING,
        charges: ChargeRules = ChargeRules(),
    ) = BattlePlayerStats(
        className = "Huntress",
        skillName = "Lightning Arrow",
        fx = element,
        weapon = WeaponVisualType.BOW,
        dps = dps,
        aps = aps,
        critChance = crit,
        critMulti = critMulti,
        life = life,
        es = es,
        physRed = 40.0,
        evade = evade,
        fireRes = 76.0,
        coldRes = 75.0,
        lightRes = 68.0,
        chaosRes = -12.0,
        ehp = 37_000.0,
        dpsBase = dps,
        charges = charges,
    )

    private fun tick(world: BattleWorld, seconds: Double) {
        val frames = (seconds * 60).toInt()
        repeat(frames) { world.update(1.0 / 60.0) }
    }

    // ------------------------------------------------------------- 사망 원인

    @Test
    fun deathCauseKeepsLastBlow() {
        val world = BattleWorld(stats(life = 900.0, es = 0.0, evade = 0.0), BattleMode.BOSS, 1, false, BossPresets.ALL[3])
        world.strikePlayer(5_000.0, DamageElement.LIGHTNING, "우버 자로크", "번개 낙뢰")
        tick(world, 0.1)

        assertEquals(BattleWorld.State.DEFEAT, world.state)
        val cause = world.deathCause
        assertNotNull("사망 원인이 기록되지 않았다", cause)
        cause!!
        println("[TEST] 사망 원인=${cause.headline} / ${cause.damageLine} / ${cause.recentLine}")
        assertEquals("우버 자로크", cause.killer)
        assertEquals("번개 낙뢰", cause.skill)
        assertEquals(DamageElement.LIGHTNING, cause.element)
        assertTrue("받은 피해가 기록되지 않았다", cause.damage > 1_000.0)
        assertTrue("한 방에 쓰러졌는데 일격으로 읽히지 않는다", cause.oneShot)
        assertTrue("사망 원인 문장에 공격자가 없다", cause.headline.contains("우버 자로크"))
        assertTrue("사망 원인 문장에 기술 이름이 없다", cause.headline.contains("번개 낙뢰"))
        assertTrue("한 방 characterization: ${cause.headline}", cause.headline.endsWith("일격"))
        assertTrue("직전 피해 요약이 비었다", cause.recent.isNotEmpty())
        assertTrue("피해 줄에 숫자가 없다", cause.damageLine.contains("피해"))
        assertTrue("최근 피해 줄이 비었다", cause.recentLine.contains("사망 직전"))
    }

    @Test
    fun deathCauseFallsBackWhenUnnamed() {
        val world = BattleWorld(stats(life = 600.0, es = 0.0, evade = 0.0), BattleMode.MAP, 5, false, null)
        // 이름 없는 공격(예: 지속 피해)도 문장이 깨지지 않아야 한다.
        world.strikePlayer(2_000.0, DamageElement.CHAOS, "", "")
        tick(world, 0.1)
        val cause = world.deathCause
        assertNotNull(cause)
        assertTrue("빈 이름이 문장을 깨뜨렸다: ${cause!!.headline}", cause.headline.contains("몬스터"))
        assertTrue("기술 이름이 비었다: ${cause.headline}", cause.headline.contains("공격"))
    }

    @Test
    fun bossFightDeathRecordsBossPattern() {
        // 보스만 있는 전투에서 실제로 맞고 쓰러질 때까지 돌린다.
        val world = BattleWorld(
            stats(life = 700.0, es = 0.0, evade = 0.0, dps = 1_000.0),
            BattleMode.BOSS, 1, false, BossPresets.ALL[0], seed = 424242L,
        )
        tick(world, 40.0)
        assertEquals(BattleWorld.State.DEFEAT, world.state)
        val cause = world.deathCause
        assertNotNull(cause)
        cause!!
        println("[TEST] 보스 사망=${cause.headline} (피해 ${cause.damage}, ${cause.at}초)")
        assertTrue("공격자가 보스로 기록되지 않았다: ${cause.killer}", cause.killer.contains("중재자"))
        assertTrue("기술 이름이 비었다", cause.skill.isNotBlank())
        assertTrue("피해량이 0이다", cause.damage > 0.0)
        assertEquals(DamageElement.FIRE, cause.element)
    }

    @Test
    fun victoryHasNoDeathCause() {
        val world = BattleWorld(stats(dps = 9_000_000.0), BattleMode.MAP, 3, false, null, seed = 9L)
        tick(world, 30.0)
        assertEquals(BattleWorld.State.VICTORY, world.state)
        assertNull(world.deathCause)
        assertNull(world.result().deathCause)
    }

    // --------------------------------------------------- 스킬별 DPS 기록

    @Test
    fun skillDamageAddsUpToTotalDamage() {
        val world = BattleWorld(stats(), BattleMode.MAP, 25, false, null, seed = 77L)
        tick(world, 6.0)
        val rows = world.skillTally()
        println("[TEST] 스킬별=${rows.joinToString { it.line }}")
        assertTrue("스킬별 기록이 비었다", rows.isNotEmpty())
        assertEquals(
            "스킬별 합이 전체 피해와 다르다",
            world.damageDealt,
            rows.sumOf { it.damage },
            0.01,
        )
        assertTrue("피해량 내림차순이 아니다", rows.zipWithNext().all { (a, b) -> a.damage >= b.damage })
        assertEquals("비율 합이 100%가 아니다", 100.0, rows.sumOf { it.share }, 0.01)
        rows.forEach { row ->
            assertEquals("DPS가 피해/시간과 다르다", row.damage / world.elapsed, row.dps, 0.01)
            assertTrue("명중 수가 없다", row.hits > 0)
            assertTrue("비율이 범위를 벗어났다", row.share in 0.0..100.0)
        }
        // 결과에도 같은 목록이 실린다.
        val result = world.result()
        assertEquals(rows.map { it.name }, result.skillDamage.map { it.name })
    }

    @Test
    fun skillRowsAreNamedAndSplit() {
        // 주력기와 콤보 스킬이 섞인 빌드 — 이름이 서로 다르게 남는다.
        val world = BattleWorld(
            stats().copy(
                motion = SkillMotion.SPARK,
                kit = listOf(KitSkill("선회 공격", SkillMotion.WHIRLING_ASSAULT, 120_000.0, ComboRole.BUILD)),
            ),
            BattleMode.MAP, 30, false, null, seed = 31L,
        )
        tick(world, 8.0)
        val names = world.skillTally().map { it.name }
        println("[TEST] 스킬 이름=$names")
        assertTrue("주력기 기록이 없다", names.any { it.contains("Lightning Arrow") })
        assertTrue("콤보 스킬 기록이 없다", names.size >= 2)
    }

    // ------------------------------------------------------ 사망 화면 페이드인

    @Test
    fun deathOverlayFadesInStepByStep() {
        // 처음에는 아무것도 없다
        assertEquals(0f, DeathFx.bannerAlpha(0f), 0.001f)
        assertEquals(0f, DeathFx.causeAlpha(0f), 0.001f)
        assertEquals(0f, DeathFx.rowAlpha(0f, 0), 0.001f)
        assertEquals(0f, DeathFx.buttonAlpha(0f), 0.001f)

        // 시간이 흐르면 순서대로 차오른다
        val mid = DeathFx.CAUSE_DELAY + DeathFx.CAUSE_FADE * 0.5f
        assertTrue("배너가 먼저 뜨지 않는다", DeathFx.bannerAlpha(mid) > 0.99f)
        assertTrue("사망 원인이 뒤따르지 않는다", DeathFx.causeAlpha(mid) in 0.2f..0.9f)

        // 줄은 한 줄씩 시차를 두고 올라온다
        val rowAge = DeathFx.ROW_DELAY + DeathFx.ROW_FADE + 0.05f
        assertTrue("첫 줄이 올라오지 않는다", DeathFx.rowAlpha(rowAge, 0) > 0.99f)
        assertTrue("둘째 줄이 첫 줄보다 늦다", DeathFx.rowAlpha(rowAge, 1) < DeathFx.rowAlpha(rowAge, 0))
        assertTrue("몇 줄 뒤에는 다시 올라온다", DeathFx.rowAlpha(rowAge + 1f, 4) > 0.5f)

        // 끝까지 가면 모두 1이 된다
        val end = DeathFx.TOTAL_TIME + 0.1f
        assertEquals(1f, DeathFx.bannerAlpha(end), 0.001f)
        assertEquals(1f, DeathFx.causeAlpha(end), 0.001f)
        assertEquals(1f, DeathFx.rowAlpha(end, 6), 0.001f)
        assertEquals(1f, DeathFx.buttonAlpha(end), 0.001f)
        assertTrue("단추가 줄보다 먼저 나온다", DeathFx.BUTTON_DELAY > DeathFx.ROW_DELAY)
        assertTrue(DeathFx.done(end))
    }

    @Test
    fun deathOverlayAlphaOnlyGrows() {
        var previous = -1f
        var age = 0f
        while (age <= DeathFx.TOTAL_TIME + 0.5f) {
            val a = DeathFx.causeAlpha(age)
            assertTrue("사망 원인 알파가 줄었다 ($age)", a >= previous - 0.0001f)
            previous = a
            age += 0.05f
        }
        assertEquals(DeathFx.GLOOM_MAX, DeathFx.gloom(2f), 0.001f)
        assertTrue("어두워지는 정도가 범위를 벗어났다", DeathFx.gloom(0.5f) in 0f..DeathFx.GLOOM_MAX)
        assertEquals(1f, DeathFx.flash(0f), 0.001f)
        assertEquals(0f, DeathFx.flash(1f), 0.001f)
        assertEquals(0f, DeathFx.fall(0f), 0.001f)
        assertEquals(1f, DeathFx.fall(1f), 0.001f)
    }

    @Test
    fun deathCardFitsInsideSmallScreens() {
        // 캔버스는 420dp 높이다. 배너·사망 원인 카드·단추를 뺀 자리에 줄이 겹치지 않게 들어가야 한다.
        val headerH = 22f
        val rowH = 23f
        val restLine = 14f
        for (vh in listOf(420f, 380f, 340f)) {
            for (causeLines in 1..2) {
                val banner = 74f
                val cause = 44f + causeLines * 18f + 8f
                val listTop = 12f + banner + cause
                val listBottom = vh - 12f - 46f - 10f
                val capacity = DeathFx.rowCapacity(listTop, listBottom, headerH, rowH, reserve = restLine)
                val used = DeathFx.listHeight(capacity, headerH, rowH, restLine)
                println("[TEST] vh=$vh 원인줄=$causeLines 자리=$capacity 높이=$used / 남은=${listBottom - listTop}")
                assertTrue("좁은 화면에서 목록이 넘친다", used + 4f <= listBottom - listTop + 0.01f)
                assertTrue("420dp 화면에 줄이 너무 적게 들어간다: $capacity", vh < 400f || capacity >= 5)
            }
        }
        // 자리가 아예 없으면 카드를 그리지 않고 0을 돌려준다
        assertEquals(0, DeathFx.rowCapacity(300f, 320f, headerH, rowH, reserve = restLine))
        // 줄이 없으면 머리말 높이만 남는다 (카드 자체를 그리지 않는 판단은 화면이 한다)
        assertEquals(headerH, DeathFx.listHeight(0, headerH, rowH, restLine), 0.01f)
    }

    @Test
    fun deathAgeAdvancesAfterDefeat() {
        val world = BattleWorld(stats(life = 500.0, es = 0.0, evade = 0.0), BattleMode.MAP, 4, false, null)
        world.strikePlayer(3_000.0, DamageElement.PHYSICAL, "희귀 몬스터", "근접 강타")
        tick(world, 0.1)
        assertEquals(BattleWorld.State.DEFEAT, world.state)
        assertEquals(0f, world.deathAge, 0.2f)
        tick(world, 1.0)
        assertTrue("사망 연출 시간이 흐르지 않았다", world.deathAge > 0.8f)
    }

    // ------------------------------------------------------------ 충전 메커니즘

    @Test
    fun chargeTextIsReadFromBuildLines() {
        val rules = Charges.read(
            listOf(
                "Combat Frenzy",
                "Gain a Frenzy Charge when you Freeze, Electrocute or Pin an enemy, no more than once every 6.3 seconds",
                "Charge Regulation",
                "Consumes one of each Charge every 10 seconds",
                "Resonance",
                "+1 to maximum Power Charges",
            )
        )
        println("[TEST] 충전 규칙=$rules")
        assertTrue("전투 격분을 읽지 못했다", rules.combatFrenzy)
        assertEquals(6.3f, rules.frenzyCooldown, 0.01f)
        assertTrue("충전 조절을 읽지 못했다", rules.regulation)
        assertTrue("공명을 읽지 못했다", rules.resonance)
        assertEquals(4, rules.maxPower)

        val ko = Charges.read(listOf("전투 격분", "최대 격분 충전 +1", "피격 시 인내 충전"))
        assertTrue("한글 전투 격분을 읽지 못했다", ko.combatFrenzy)
        assertEquals(4, ko.maxFrenzy)
        assertTrue("한글 인내 충전을 읽지 못했다", ko.enduranceOnHit)
    }

    @Test
    fun resonanceSwapsChargeKinds() {
        val world = BattleWorld(stats(charges = ChargeRules(resonance = true)), BattleMode.MAP, 5, false, null)
        assertEquals(ChargeKind.POWER, world.gainCharge(ChargeKind.FRENZY))
        assertEquals(1, world.chargeCount(ChargeKind.POWER))
        assertEquals(0, world.chargeCount(ChargeKind.FRENZY))

        assertEquals(ChargeKind.ENDURANCE, world.gainCharge(ChargeKind.POWER))
        assertEquals(ChargeKind.FRENZY, world.gainCharge(ChargeKind.ENDURANCE))
        assertEquals(1, world.chargeCount(ChargeKind.ENDURANCE))
        assertEquals(1, world.chargeCount(ChargeKind.FRENZY))

        // 가득 차면 더 얻지 않는다
        repeat(6) { world.gainCharge(ChargeKind.FRENZY) }
        assertEquals(ChargeRules.BASE_MAX, world.chargeCount(ChargeKind.POWER))
    }

    @Test
    fun combatFrenzyGainsChargesOnShockOrFreeze() {
        val rules = ChargeRules(combatFrenzy = true, frenzyCooldown = 0.3f)
        val world = BattleWorld(stats(charges = rules), BattleMode.MAP, 30, false, null, seed = 2024L)
        tick(world, 8.0)
        println("[TEST] 격분 획득=${world.frenzyGained} 보유=${world.chargeCount(ChargeKind.FRENZY)}")
        assertTrue("전투 격분으로 격분 충전을 얻지 못했다", world.frenzyGained > 0)
        assertTrue("격분 충전이 남아 있지 않다", world.chargeCount(ChargeKind.FRENZY) > 0)
        // 쿨타임이 있어 한 번에 몰아서 쌓이지는 않는다 (8초에 0.3초 쿨이면 최대 27회)
        assertTrue("쿨타임이 무시됐다", world.frenzyGained < 40)
        assertTrue("HUD 문장에 격분이 없다: ${world.chargesHud()}", world.chargesHud().contains("격분"))
        assertTrue(world.chargeRules.usesCharges)
    }

    @Test
    fun regulationBurnsChargesOnInterval() {
        val rules = ChargeRules(regulation = true, regulationInterval = 1f)
        val world = BattleWorld(stats(charges = rules), BattleMode.MAP, 30, false, null, seed = 5L)
        listOf(ChargeKind.POWER, ChargeKind.ENDURANCE, ChargeKind.FRENZY).forEach { world.gainCharge(it) }
        tick(world, 1.5)
        println("[TEST] 충전 조절 후=${world.chargesHud()} 소모=${world.chargesSpent}")
        val burned = world.chargesSpent
        assertTrue("충전 조절이 충전을 태우지 않았다", burned >= 3)
        tick(world, 3.0)
        // 치명타로 권능이 다시 붙는 판도 있으므로 "주기가 지나면 또 태운다"로 확인한다.
        val allEmpty = ChargeKind.values().all { world.chargeCount(it) == 0 }
        assertTrue(
            "주기가 지나도 또 태우지 않았다 (소모 $burned → ${world.chargesSpent})",
            world.chargesSpent > burned || allEmpty,
        )
    }

    // ------------------------------------------------- 기름 포화 360도 광역

    @Test
    fun oilBarrageCoversFullCircleAndSpendsPower() {
        val world = BattleWorld(
            stats().copy(
                skillName = "Oil Barrage",
                fx = SkillEffectType.FIRE,
                motion = SkillMotion.OIL_BARRAGE,
                charges = ChargeRules(combatFrenzy = true),
            ),
            BattleMode.BOSS, 1, false, BossPresets.ALL[0], seed = 601L,
        )
        world.grantPowerCharges(3)
        tick(world, 0.6)

        val oilShots = world.projectiles.filter { it.kind == ProjKind.OIL || it.kind == ProjKind.CHARGED_OIL }
        println("[TEST] 기름 탄=${oilShots.size} 발사=${world.barrageShots} 전기기름=${world.chargedOilShots} 태움=${world.chargesSpent}")
        assertTrue("기름 탄이 너무 적다 (${oilShots.size})", oilShots.size >= 16)
        assertTrue("권능 충전을 태우지 않았다", world.chargesSpent >= 3)
        assertTrue("전기를 띤 기름이 나가지 않았다", world.chargedOilShots > 0)

        // 방향이 사방으로 퍼졌는지 — 가장 큰 빈 각도가 작아야 한다.
        val angles = oilShots.map { atan2(it.dirY, it.dirX) }.sorted()
        var maxGap = 0f
        for (i in angles.indices) {
            val next = if (i == angles.lastIndex) angles[0] + (Math.PI * 2).toFloat() else angles[i + 1]
            maxGap = maxOf(maxGap, next - angles[i])
        }
        println("[TEST] 최대 빈 각도=${Math.toDegrees(maxGap.toDouble())}도")
        assertTrue("탄이 한 방향에만 몰렸다 (빈 각도 ${Math.toDegrees(maxGap.toDouble())}도)", maxGap < 1.2f)

        // 떨어진 자리에 웅덩이가 생기고, 전격 폭발이 적을 때린다.
        tick(world, 1.6)
        println("[TEST] 기름 웅덩이=${world.oils.size} 피해=${world.damageDealt} 연쇄=${world.oilChainIgnitions}")
        assertTrue("기름 웅덩이가 생기지 않았다", world.oils.isNotEmpty())
        assertTrue("기름 포화 피해가 없다", world.damageDealt > 0.0)
    }

    @Test
    fun oilPoolsSpreadAroundPlayer() {
        val world = BattleWorld(
            stats().copy(skillName = "Oil Barrage", fx = SkillEffectType.FIRE, motion = SkillMotion.OIL_BARRAGE),
            BattleMode.MAP, 20, false, null, seed = 733L,
        )
        world.grantPowerCharges(2)
        tick(world, 2.4)
        val px = world.player.x
        val py = world.player.y
        val quads = world.oils.map { o ->
            val dx = o.x - px
            val dy = o.y - py
            when {
                dx >= 0f && dy < 0f -> 0
                dx >= 0f -> 1
                dy >= 0f -> 2
                else -> 3
            }
        }.toSet()
        println("[TEST] 웅덩이=${world.oils.size} 사분면=$quads")
        assertTrue("기름 웅덩이가 너무 적다", world.oils.size >= 4)
        assertTrue("웅덩이가 한쪽에만 생겼다: $quads", quads.size >= 3)
    }

    @Test
    fun oilChainIgnitesNeighbourPools() {
        val world = BattleWorld(
            stats().copy(skillName = "Oil Barrage", fx = SkillEffectType.FIRE, motion = SkillMotion.OIL_BARRAGE),
            BattleMode.MAP, 2, false, null, seed = 88L,
        )
        world.spillOilAt(240f, 300f, 44f)
        world.spillOilAt(320f, 310f, 44f)
        world.testOilImpact(240f, 300f, 44f, 20_000.0)
        println("[TEST] 연쇄=${world.oilChainIgnitions} 불붙은 웅덩이=${world.oils.count { it.ignited }}")
        assertTrue("옆 기름으로 불이 옮겨 붙지 않았다", world.oilChainIgnitions >= 1)
        assertTrue("기름이 불붙지 않았다", world.oils.any { it.ignited })
    }

    @Test
    fun oilBarrageDamageStaysWithinBudget() {
        // 발수만 늘고 전체 피해가 부풀면 DPS가 망가진다 — 저항을 없앤 대상으로 확인한다.
        val base = stats(dps = 600_000.0, aps = 3.0, crit = 0.0, life = 900_000.0, es = 0.0)
            .copy(skillName = "Oil Barrage", fx = SkillEffectType.FIRE, motion = SkillMotion.OIL_BARRAGE, playerDps = 600_000.0)
        val world = BattleWorld(base, BattleMode.BOSS, 1, false, BossPresets.ALL[0], seed = 12L)
        world.grantPowerCharges(3)
        tick(world, 1.2)
        val hits = world.player.attackCount
        println("[TEST] 공격 $hits 회 · 피해 ${world.damageDealt} · 연타 ${world.barrageShots}")
        assertTrue("공격이 나가지 않았다", hits >= 1)
        // 한 번 시전의 피해 상한: 그 한 방 피해의 2배(권능 3개 소모 시 1.35배 + 폭발 몫)보다 크지 않다.
        val perTurn = 600_000.0 / 3.0
        assertTrue(
            "기름 포화 피해가 과하게 부풀었다: ${world.damageDealt} (한 방 ${perTurn})",
            world.damageDealt < perTurn * 2.6 * hits,
        )
    }
}
