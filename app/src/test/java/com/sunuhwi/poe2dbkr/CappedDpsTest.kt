package com.sunuhwi.poe2dbkr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * PoB가 DPS를 무한(inf)으로 내보낸 빌드의 표시.
 *
 * 두꺼비 폭발(암울한 결과)처럼 피해가 끝없이 되풀이되는 조합은 PoB 계산이 발산해
 * `TotalDPS=inf`가 된다. poe.ninja는 그 값을 32비트 상한(2,147,483,647)으로 적어 `2.1B`로 보여 주고,
 * 앱도 같은 값을 써야 스샷과 숫자가 맞는다.
 */
class CappedDpsTest {

    private val code: String by lazy {
        File("src/test/resources/pob_real_build.txt").readText()
    }

    @Test
    fun infiniteBuildShowsNineteenBillionCap() {
        val build = Pob.decode(code)
        val calc = PobCalculator.compute(build, emptyMap())
        println("[TEST] capped=${calc.dpsCapped} main=${calc.mainSkill} total=${calc.totalDps}")
        println("[TEST] cap=${BattleWorld.formatShort(calc.dpsNow)}")
        assertTrue("무한대 빌드로 인식하지 못했다", calc.dpsCapped)
        assertTrue("DPS가 무한·NaN", calc.dpsNow.isFinite())
        assertEquals(PobCalculator.DPS_CAP, calc.dpsNow, 1.0)
        assertEquals("주력 스킬 이름", "Dark Consequences", calc.mainSkill)
        assertEquals("2.1B", BattleWorld.formatShort(calc.dpsNow))
    }

    @Test
    fun mainSkillKeepsCapAndOthersStaySmall() {
        val build = Pob.decode(code)
        val calc = PobCalculator.compute(build, emptyMap())
        val main = calc.skillDps.first()
        assertEquals("Dark Consequences", main.name)
        assertEquals(PobCalculator.DPS_CAP, main.dps, 1.0)
        val others = calc.skillDps.drop(1)
        println("[TEST] skills=${calc.skillDps.map { "${it.name}=${it.dps.toLong()}" }}")
        assertTrue("주력 말고 다른 스킬이 없다", others.isNotEmpty())
        others.forEach { skill ->
            assertTrue("${skill.name} DPS가 무한·NaN", skill.dps.isFinite())
            assertTrue(
                "${skill.name} DPS가 상한에 눌러앉았다: ${skill.dps}",
                skill.dps < PobCalculator.DPS_CAP * 0.001,
            )
        }
        assertTrue("스킬별 DPS가 모두 같다", others.map { it.dps.toLong() }.distinct().size > 1)
    }

    @Test
    fun battleThrowsCapScaleDamage() {
        val build = Pob.decode(code)
        val stats = BattleSetup.statsFor(build)!!
        println("[TEST] stats dps=${stats.dps} capped=${stats.dpsCapped} toad=${stats.minions.darkConsequencesDps}")
        assertTrue("전투 DPS가 상한까지 오르지 않았다", stats.dps >= PobCalculator.DPS_CAP * 0.999)
        assertTrue("전투 DPS가 무한·NaN", stats.dps.isFinite())
        assertTrue("두꺼비 폭발이 상한 규모가 아니다", stats.minions.darkConsequencesDps >= PobCalculator.DPS_CAP * 0.999)
    }

    @Test
    fun sixteenTMapIsClearedInSeconds() {
        val build = Pob.decode(code)
        val stats = BattleSetup.statsFor(build)!!
        // 화면 기본값과 같은 25마리 16T 맵. 두꺼비 폭발 한 번에 무리가 정리된다.
        val world = BattleWorld(stats, BattleMode.MAP, 25, amp = false, boss = null)
        repeat(60 * 60) { world.update(1.0 / 60.0) }
        println(
            "[TEST] 16T 25마리: state=${world.state} elapsed=${world.elapsed} kills=${world.kills} " +
                "폭발=${world.toadExplosions} 피해=${BattleWorld.formatShort(world.damageDealt)}",
        )
        assertEquals("맵을 끝내지 못했다", BattleWorld.State.VICTORY, world.state)
        assertEquals("25마리를 다 잡지 못했다", 25, world.kills)
        assertTrue("2.1B 빌드치고 너무 느리다: ${world.elapsed}초", world.elapsed < 60.0)
        assertTrue("가한 피해가 무한·NaN", world.damageDealt.isFinite())
        assertTrue("두꺼비가 터지지 않았다", world.toadExplosions > 0)
    }

    @Test
    fun hundredMobBrawlBurnsBillionsInSeconds() {
        // 100마리 무리에서는 1,471 생명력짜리 이 빌드가 버티지 못할 수 있다(생존은 DPS가 아니라 방어 문제).
        // 여기서는 화력만 본다 — 10초 안에 수십 마리를 지우고 피해가 10억을 넘어야 한다.
        val build = Pob.decode(code)
        val world = BattleWorld(BattleSetup.statsFor(build)!!, BattleMode.MAP, 100, amp = false, boss = null)
        repeat(60 * 10) { world.update(1.0 / 60.0) }
        println(
            "[TEST] 16T 100마리 10초: state=${world.state} kills=${world.kills} " +
                "폭발=${world.toadExplosions} 피해=${BattleWorld.formatShort(world.damageDealt)}",
        )
        assertTrue("10초에 25마리도 못 잡았다: ${world.kills}", world.kills >= 25)
        assertTrue("피해가 10억에 못 미친다: ${world.damageDealt}", world.damageDealt > 1_000_000_000.0)
        assertTrue("가한 피해가 무한·NaN", world.damageDealt.isFinite())
    }

    @Test
    fun uberBossesMeltUnderTheCap() {
        val build = Pob.decode(code)
        val stats = BattleSetup.statsFor(build)!!
        BossPresets.ALL.forEach { boss ->
            val world = BattleWorld(stats, BattleMode.BOSS, 1, amp = false, boss = boss)
            repeat(60 * 60) { world.update(1.0 / 60.0) }
            println(
                "[TEST] ${boss.name}: state=${world.state} elapsed=${world.elapsed} " +
                    "남은 보스 체력=${BattleWorld.formatShort(world.enemies.firstOrNull()?.hp ?: 0.0)} 피해=${BattleWorld.formatShort(world.damageDealt)}",
            )
            assertEquals("${boss.name}를 잡지 못했다", BattleWorld.State.VICTORY, world.state)
            assertTrue("${boss.name}전이 너무 길다: ${world.elapsed}초", world.elapsed < 30.0)
            assertTrue("가한 피해가 무한·NaN", world.damageDealt.isFinite())
        }
    }
}
