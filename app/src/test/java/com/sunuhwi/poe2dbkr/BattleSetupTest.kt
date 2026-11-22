package com.sunuhwi.poe2dbkr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 앱에서 "빌드 불러오기 → 전투 시작"을 눌렀을 때와 같은 경로를 JVM에서 그대로 돌린다.
 * 이 경로가 수치를 못 만들면 전투 탭은 시작 자체가 되지 않는다.
 */
class BattleSetupTest {

    private val build by lazy { Pob.decode(Pob.sampleCode()) }

    @Test
    fun sampleBuildProducesBattleStats() {
        val stats = BattleSetup.statsFor(build)
        assertNotNull("샘플 빌드로 전투 수치를 만들지 못했다", stats)
        stats!!
        println("[TEST] stats=$stats")
        assertTrue("DPS가 0", stats.dps > 0)
        assertTrue("공격 속도가 0", stats.aps > 0)
        assertTrue("생명력이 0", stats.life > 0)
        assertTrue("스킬 이름이 비었다", stats.skillName.isNotBlank())
        assertTrue("무기 판정 실패", stats.weapon != WeaponVisualType.NONE)
        println("[TEST] skill=${stats.skillName} fx=${stats.fx} weapon=${stats.weapon} label=${stats.weaponLabel}")
        println("[TEST] trigger=${stats.triggerText.ifBlank { "없음" }}")
    }

    @Test
    fun sampleBattleRunsFromStats() {
        val stats = BattleSetup.statsFor(build)!!
        val world = BattleWorld(stats, BattleMode.MAP, 25, false, null)
        repeat(600) { world.update(1.0 / 60.0) }
        println("[TEST] 10s elapsed=${world.elapsed} kills=${world.kills}/${world.totalEnemies} state=${world.state}")
        assertTrue("틱이 진행되지 않았다", world.elapsed > 1.0)
        assertTrue("피해가 들어가지 않았다", world.damageDealt > 0)
    }

    @Test
    fun triggerTestModeAddsCastOnCrit() {
        val stats = BattleSetup.statsFor(build, triggerMode = TriggerMode.TEST)!!
        assertTrue("테스트 트리거가 붙지 않았다", stats.triggers.isNotEmpty())
        println("[TEST] triggerText=${stats.triggerText}")
        val world = BattleWorld(stats, BattleMode.MAP, 25, false, null)
        repeat(900) { world.update(1.0 / 60.0) }
        println("[TEST] triggerCasts=${world.triggerCasts} damage=${world.triggerDamage}")
        assertTrue("트리거가 한 번도 발동하지 않았다", world.triggerCasts > 0)
    }

    @Test
    fun nullBuildHasNoStats() {
        assertEquals(null, BattleSetup.statsFor(null))
    }

    @Test
    fun weaponOverrideChangesVisual() {
        val bow = BattleSetup.statsFor(build)!!
        val weapon = Pob.itemForSlot(build, "Weapon 1")
        assertNotNull("샘플 빌드에 무기가 없다", weapon)
        val override = weapon!!.copy(weapon = WeaponVisualType.CROSSBOW)
        val changed = BattleSetup.statsFor(build, overrides = mapOf("Weapon 1" to override))!!
        assertEquals(WeaponVisualType.CROSSBOW, changed.weapon)
        println("[TEST] bow=${bow.weapon} override=${changed.weapon}")
    }
}
