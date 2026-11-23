package com.sunuhwi.poe2dbkr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 몬스터 실루엣이 종류마다 다르고, 플레이어·소환수와 구분되는 어두운 색·붉은 눈을 쓰는지 확인한다.
 * (캔버스 픽셀 대신 그림에 쓰는 값 자체를 검증한다.)
 */
class MonsterDesignTest {

    @Test
    fun eachKindHasItsOwnSilhouette() {
        val normal = MonsterDesign.look(EnemyKind.NORMAL, DamageElement.PHYSICAL, false)
        val magic = MonsterDesign.look(EnemyKind.MAGIC, DamageElement.FIRE, false)
        val rare = MonsterDesign.look(EnemyKind.RARE, DamageElement.COLD, false)
        println("[TEST] ${normal.build} / ${magic.build} / ${rare.build}")
        assertEquals(MonsterBuild.HORNED_BRUTE, normal.build)
        assertEquals(MonsterBuild.HOODED_CASTER, magic.build)
        assertEquals(MonsterBuild.ARMORED_ELITE, rare.build)
        assertNotEquals("일반과 마법 몬스터 실루엣이 같다", normal.build, magic.build)
        assertTrue("희귀 몬스터에 갑주가 없다", rare.armored)
        assertTrue("희귀 몬스터에 날개가 없다", rare.wings)
        assertTrue("일반 몬스터에 뿔이 없다", normal.horns >= 2)
        assertEquals("후드 술사에 뿔이 붙었다", 0, magic.horns)
    }

    @Test
    fun monstersUseDarkOutlineAndGlowingEyes() {
        EnemyKind.entries.forEach { kind ->
            val look = MonsterDesign.look(kind, DamageElement.LIGHTNING, false)
            assertEquals("검은 외곽선이 아니다", MonsterLook.OUTLINE, look.outline)
            assertTrue("눈이 붉지도 보랏빛도 아니다", look.demonEyes || look.eye == MonsterLook.EYE_VIOLET)
            // 플레이어 몸통(밝은 크림)과 확실히 다른 어두운 색
            val brightness = ((look.body shr 16 and 0xFF) + (look.body shr 8 and 0xFF) + (look.body and 0xFF)) / 3
            println("[TEST] $kind body=${Integer.toHexString(look.body)} 밝기=$brightness")
            assertTrue("몬스터 몸통이 너무 밝다: $kind", brightness < 120)
        }
    }

    @Test
    fun enragedAndElementChangeLook() {
        val calm = MonsterDesign.look(EnemyKind.NORMAL, DamageElement.PHYSICAL, false)
        val angry = MonsterDesign.look(EnemyKind.NORMAL, DamageElement.PHYSICAL, true)
        assertNotEquals("격노해도 눈빛이 그대로다", calm.eye, angry.eye)
        assertTrue("격노 몬스터가 악마 눈이 아니다", angry.demonEyes)
        val caster = MonsterDesign.look(EnemyKind.MAGIC, DamageElement.COLD, false)
        assertEquals("술사 오라가 속성을 따르지 않는다", DamageElement.COLD.color, caster.aura)
        assertFalse("일반 몬스터에 오라가 붙었다", calm.aura != 0)
    }

    @Test
    fun rareIsBiggerThanNormal() {
        val normal = MonsterDesign.look(EnemyKind.NORMAL, DamageElement.PHYSICAL, false)
        val rare = MonsterDesign.look(EnemyKind.RARE, DamageElement.PHYSICAL, false)
        assertTrue("희귀 몬스터가 일반보다 크지 않다", rare.height > normal.height && rare.width > normal.width)
        assertTrue("희귀 몬스터 오라가 없다", rare.auraRadius > 0f)
    }
}
