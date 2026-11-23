package com.sunuhwi.poe2dbkr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.text.NumberFormat
import java.util.Locale

/**
 * 화면에 그릴 때 쓰는 문자열 리소스와 인자 타입이 서로 맞는지 확인한다.
 * 인자 개수나 타입이 어긋나면 기기에서 getString()이 예외를 던지며 앱이 종료된다.
 */
class PobStringsTest {

    private val strings: Map<String, String> by lazy {
        val file = File("src/main/res/values/strings.xml")
        val xml = file.readText()
        Regex("<string name=\"([^\"]+)\">(.*?)</string>", RegexOption.DOT_MATCHES_ALL)
            .findAll(xml)
            .associate { it.groupValues[1] to it.groupValues[2] }
    }

    private fun format(name: String, vararg args: Any): String =
        String.format(Locale.KOREA, strings.getValue(name), *args)

    @Test
    fun buildSummaryMatchesArgs() {
        val out = format("pob_build_summary", 3, 12, 106)
        println("[STR] $out")
        assertTrue(out.contains("3"))
        assertTrue(out.contains("106"))
    }

    @Test
    fun battleHeaderMatchesArgs() {
        val fmt = NumberFormat.getNumberInstance(Locale.KOREA)
        val out = format(
            "battle_skill_line",
            "Lightning Arrow",
            "연쇄 번개",
            3.42,
            fmt.format(752_467L),
            fmt.format(37_426L),
        )
        println("[STR] $out")
        assertTrue(out.contains("Lightning Arrow"))
        assertTrue(out.contains("3.42"))
    }

    @Test
    fun battleResultMatchesArgs() {
        val fmt = NumberFormat.getNumberInstance(Locale.KOREA)
        val out = format(
            "battle_result_body",
            "7.16M", "1.21M", 10, 0, 0, 25, 25, 100.0, fmt.format(4_120L), fmt.format(860L),
        )
        println("[STR] $out")
        assertTrue(out.contains("25/25"))
    }

    @Test
    fun heraldResultMatchesArgs() {
        val out = format("battle_result_herald", 12, "1.24M")
        println("[STR] $out")
        assertTrue(out.contains("12"))
        assertTrue(out.contains("1.24M"))
        assertTrue(format("battle_result_shatter", 7).contains("7"))
        assertTrue(format("battle_result_clear", "얼음의 전령", "880K").contains("얼음의 전령"))
    }

    @Test
    fun renderFailedMatchesArgs() {
        assertEquals("빌드를 화면에 그리지 못했습니다 (NullPointerException). 다른 빌드 코드로 다시 시도해 주세요.",
            format("pob_render_failed", "NullPointerException"))
    }
}
