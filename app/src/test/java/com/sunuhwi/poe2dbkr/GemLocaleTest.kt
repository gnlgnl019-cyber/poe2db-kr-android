package com.sunuhwi.poe2dbkr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 불러온 빌드의 스킬 이름이 모두 한글로 나오는지 확인한다.
 *
 * PoB는 젬 이름을 영문으로 적어 보내므로, 화면에 그대로 쓰면 영어가 섞인다.
 * 실제 빌드(pob_real_build.txt)와 내장 샘플 빌드의 젬을 하나도 빠짐없이 사전에 대 본다.
 */
class GemLocaleTest {

    private val realCode: String by lazy { File("src/test/resources/pob_real_build.txt").readText() }
    private val realBuild: PobBuild by lazy { Pob.decode(realCode) }
    private val sampleBuild: PobBuild by lazy { Pob.decode(Pob.sampleCode()) }
    private val dict: KrDict by lazy {
        KrDict.parse(File("src/main/assets/poe2_kr_i18n.json").readText())
    }

    private fun hangul(text: String) = text.any { it in '\uAC00'..'\uD7A3' }

    private fun gemsOf(build: PobBuild) = build.skills.flatMap { it.gems }.map { it.name }.distinct()

    @Test
    fun everyGemOfRealBuildIsKorean() {
        val gems = gemsOf(realBuild)
        println("[TEST] 실 빌드 젬 ${realBuild.skills.flatMap { it.gems }.size}개 · 이름 ${gems.size}종")
        val missing = gems.filter { !hangul(dict.gemName(it)) }
        println("[TEST] 한글 없음: $missing")
        assertTrue("젬 사전이 비었다", dict.gems.size > 900)
        assertTrue("한글로 바뀌지 않은 젬: $missing", missing.isEmpty())
        gems.forEach { gem ->
            assertEquals(
                "$gem 의 한글 이름이 영문과 같다: ${dict.gemName(gem)}",
                false,
                dict.gemName(gem).equals(gem, ignoreCase = true),
            )
        }
    }

    @Test
    fun everyGemOfSampleBuildIsKorean() {
        val gems = gemsOf(sampleBuild)
        println("[TEST] 샘플 빌드 젬: " + gems.joinToString { "$it→${dict.gemName(it)}" })
        val missing = gems.filter { !hangul(dict.gemName(it)) }
        assertTrue("샘플 빌드에서 한글로 바뀌지 않은 젬: $missing", missing.isEmpty())
    }

    @Test
    fun skillTabAndBattleHeaderUseKoreanNames() {
        // 스킬 탭 카드·전투 헤더·스킬 칩이 모두 같은 사전을 본다.
        val options = BattleSetup.skillOptions(realBuild, dict)
        assertTrue("스킬 칩이 비었다", options.isNotEmpty())
        options.forEach { option ->
            assertTrue("칩 이름이 영문이다: ${option.name}", hangul(option.name))
        }
        val stats = BattleSetup.statsFor(realBuild, dict = dict)
        assertNotNull(stats)
        println("[TEST] 전투 헤더 스킬: ${stats!!.skillName}")
        assertTrue("전투 헤더 스킬이 영문이다: ${stats.skillName}", hangul(stats.skillName))

        // 보조 젬·메타 젬이 이름만 영문으로 남는 곳이 없는지 그룹 전체를 본다.
        realBuild.skills.forEach { group ->
            group.gems.forEach { gem ->
                val ko = dict.gemName(gem.name)
                assertTrue("${gem.name} 한글 없음", hangul(ko))
                assertFalse("${gem.name} 이 영문 그대로다", ko.all { it.code < 0x80 })
            }
        }
    }

    @Test
    fun tieredAndLegacyNamesAreCovered() {
        val pairs = listOf(
            "Rapid Attacks II" to "빠른 공격 II",
            "Prolonged Duration II" to "지속시간 연장 II",
            "Execute III" to "처형 III",
            "Martial Tempo" to "무도의 속도",
            "Scattershot" to "난사",
            "Lightning Arrow" to "번개 화살",
            "Mace Strike" to "철퇴 타격",
        )
        pairs.forEach { (en, ko) ->
            assertEquals("$en", ko, dict.gemName(en))
        }
    }

    @Test
    fun unknownGemFallsBackWithoutBreaking() {
        assertEquals("", dict.gemName("Definitely Not A Gem"))
        // 사전에 없는 이름은 화면에서 영문을 그대로 보여 준다 (빈 줄이 되지 않는다).
        assertEquals("Some New Gem", dict.gemName("Some New Gem").ifBlank { "Some New Gem" })
    }

    @Test
    fun dictionaryLoadsEverySection() {
        println("[TEST] ${dict.stats()}")
        assertTrue(dict.names.size > 4000)
        assertTrue(dict.gems.size > 900)
        assertTrue(dict.mods.size > 1000)
        // 젬 사전 키는 모두 영문 소문자다 (화면에서 소문자로 찾는다).
        assertTrue(dict.gems.keys.all { key -> key == key.lowercase() })
        assertTrue("젬 사전 값에 한글이 없는 항목이 있다", dict.gems.values.all { hangul(it) })
    }
}
