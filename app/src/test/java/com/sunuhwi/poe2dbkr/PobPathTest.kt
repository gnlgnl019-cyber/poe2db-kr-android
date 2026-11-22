package com.sunuhwi.poe2dbkr

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 샘플 빌드 경로(코드 해석 → 계산 → 전투)를 JVM에서 그대로 돌려 보는 테스트.
 * android.util.Base64 / Xml은 test 소스셋의 최소 구현이 대신한다.
 */
class PobPathTest {

    @Test
    fun sampleCodeDecodes() {
        val build = Pob.decode(Pob.sampleCode())
        println("[TEST] className=${build.className} ascend=${build.ascendClassName} level=${build.level}")
        println("[TEST] stats=${build.stats}")
        println("[TEST] skills=${build.skills.map { g -> g.label + ":" + g.gems.map { it.name } }}")
        println("[TEST] items=${build.items.size} slots=${build.slots}")
        println("[TEST] tree=${build.treeTitle} nodes=${build.treeNodes}")
        assertTrue(build.skills.isNotEmpty())
        assertTrue(build.items.isNotEmpty())
    }

    @Test
    fun computeWorks() {
        val build = Pob.decode(Pob.sampleCode())
        val calc = PobCalculator.compute(build, emptyMap(), emptySet(), PobCalculator.Config())
        println("[TEST] dps=${calc.dpsNow} life=${calc.lifeNow} ehp=${calc.ehpNow} resists=${calc.resists}")
        assertTrue(calc.dpsNow > 0)
    }

    @Test
    fun battleRuns() {
        val build = Pob.decode(Pob.sampleCode())
        val calc = PobCalculator.compute(build, emptyMap(), emptySet(), PobCalculator.Config())
        val group = build.skills.firstOrNull { it.label.toIntOrNull() == build.mainSocketGroup }
            ?: build.skills.firstOrNull { it.enabled && it.gems.isNotEmpty() }
        val gems = group?.gems?.filter { it.enabled }.orEmpty()
        val mainGem = gems.firstOrNull { !PobCalculator.SUPPORT_GEMS.containsKey(it.name.lowercase().trim()) }
            ?: gems.firstOrNull()
        val skillName = mainGem?.name ?: group?.label ?: build.className
        val fx = SkillEffects.detect(skillName, group?.label.orEmpty())
        val stats = BattlePlayerStats(
            className = build.className,
            skillName = skillName,
            fx = fx,
            weapon = SkillEffects.weaponFor(fx, emptyList()),
            dps = calc.dpsNow,
            aps = calc.apsNow,
            critChance = calc.critNow,
            critMulti = calc.critMultiNow,
            life = calc.lifeNow,
            es = calc.esNow,
            physRed = calc.physRedNow,
            evade = 20.0,
            fireRes = calc.resists[0].third,
            coldRes = calc.resists[1].third,
            lightRes = calc.resists[2].third,
            chaosRes = calc.resists[3].third,
            ehp = calc.ehpNow,
            dpsBase = calc.dpsBase,
        )
        val world = BattleWorld(stats, BattleMode.MAP, 25, false, null)
        assertNotNull(world)
        repeat(1200) { world.update(1.0 / 60.0) }
        println("[TEST] battle state=${world.state} kills=${world.kills} elapsed=${world.elapsed}")
        println("[TEST] result=${world.result()}")
    }

    @Test
    fun encodeRoundTrip() {
        val build = Pob.decode(Pob.sampleCode())
        val code = Pob.encode(build)
        println("[TEST] encoded len=${code.length}")
        val again = Pob.decode(code)
        println("[TEST] roundtrip skills=${again.skills.size} items=${again.items.size} level=${again.level}")
        assertTrue(again.skills.size == build.skills.size)
    }
}
