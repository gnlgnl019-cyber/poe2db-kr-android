package com.sunuhwi.poe2dbkr

import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.math.max

/** 공식 거래소에서 찾은 매물 가격 한 건 */
data class TradeQuote(val amount: Double, val currency: String) {
    /** 디바인으로 환산한 값 — 환율을 모르는 화폐면 null */
    fun divine(rates: CurrencyRates): Double? = when (currency.lowercase()) {
        "divine" -> amount
        "exalted" -> if (rates.exaltedPerDivine > 0.0) amount / rates.exaltedPerDivine else null
        "chaos" -> if (rates.chaosPerDivine > 0.0) amount / rates.chaosPerDivine else null
        else -> null
    }
}

/** 공식 거래소에서 찾은 유사 매물 묶음 */
data class TradePrice(
    /** 디바인 중위값 */
    val divine: Double,
    /** 조회에 걸린 옵션 수 */
    val filters: Int,
    /** 가격을 읽은 매물 수 */
    val sample: Int,
) {
    val note: String get() = "매물 ${sample}건 · 중위값"
}

/** 거래소가 받는 옵션 필터 하나 */
data class TradeFilter(val id: String, val min: Double, val label: String)

/**
 * 공식 Path of Exile 2 거래소(trade2) 조회.
 *
 * 로그인 없이도 검색·조회가 되지만(사용자 에이전트만 붙이면 200), 매물 검색은
 * 호출 제한이 있어 한 번에 몇 건만 본다. 제한에 걸리면 조용히 포기하고 화면은
 * 옵션 추정가로 돌아간다 — 조회한 것처럼 적지 않는다.
 */
object Trade2 {

    private const val BASE = "https://www.pathofexile.com/api/trade2"
    private const val UA = "PoE2DB-KR-Android/1.0"

    /** 한 번 산출에 쓸 수 있는 검색 횟수 (호출 제한 보호) */
    const val MAX_SEARCHES = 3

    /** 검색 사이 간격 (밀리초) */
    const val SEARCH_GAP_MS = 1100L

    /** 가격을 읽을 매물 수 */
    const val SAMPLE = 5

    private var statCache: Pair<Long, Map<String, String>>? = null
    private const val CACHE_MS = 60 * 60 * 1000L

    private fun get(url: String, body: String? = null): Pair<Int, String> {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 8000
        conn.readTimeout = 12000
        conn.setRequestProperty("User-Agent", UA)
        if (body != null) {
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.outputStream.use { it.write(body.toByteArray()) }
        }
        return try {
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            code to text
        } finally {
            conn.disconnect()
        }
    }

    /** 옵션 글 → 거래소 스탯 id 사전 (explicit 그룹만) */
    fun statIndex(): Map<String, String> {
        statCache?.let { (at, value) ->
            if (System.currentTimeMillis() - at < CACHE_MS && value.isNotEmpty()) return value
        }
        val (code, text) = runCatching { get("$BASE/data/stats") }.getOrElse { return emptyMap() }
        if (code !in 200..299 || text.isBlank()) return statCache?.second ?: emptyMap()
        val index = runCatching { parseStatIndex(text) }.getOrDefault(emptyMap())
        if (index.isNotEmpty()) statCache = System.currentTimeMillis() to index
        return index
    }

    /**
     * data/stats 응답에서 explicit 옵션 사전을 만든다. 테스트에서도 그대로 돌린다.
     *
     * 응답이 8백 KB가 넘어 통째로 트리로 만들 이유가 없어, 항목 모양
     * `{"id":"explicit...","text":"..."}` 만 정규식으로 훑는다.
     */
    internal fun parseStatIndex(json: String): Map<String, String> {
        val out = HashMap<String, String>()
        val pattern = Regex("\\{\"id\":\"(explicit\\.[^\"]+)\",\"text\":\"((?:[^\"\\\\]|\\\\.)*)\"")
        for (match in pattern.findAll(json)) {
            val id = match.groupValues[1]
            val text = match.groupValues[2].replace("\\\"", "\"")
            if (id.isBlank() || text.isBlank()) continue
            out.putIfAbsent(normalize(text), id)
        }
        return out
    }

    /** 숫자를 #으로 바꾼 옵션 문장 — 거래소 사전과 아이템 줄을 같은 모양으로 만든다 */
    fun normalize(text: String): String = text.lowercase()
        .replace(Regex("\\d+(?:\\.\\d+)?"), "#")
        .replace("+#", "#")
        .replace(Regex("\\s+"), " ")
        .trim()
        .trimEnd('.')

    private val SINGLE_NUMBER = Regex("[-+]?\\d+(?:\\.\\d+)?")

    /** 줄에 있는 수 하나 (없으면 null) */
    private fun oneNumber(line: String): Double? {
        val found = SINGLE_NUMBER.findAll(line.replace(",", "")).toList()
        if (found.size != 1) return null
        return found.first().value.toDoubleOrNull()
    }

    /**
     * 아이템 줄에서 거래소 필터를 고른다.
     *
     * 값어치가 큰 옵션부터 limit개를 고르고, 수치는 그대로 걸지 않고 조금 낮춰
     * "비슷하거나 조금 나은" 매물을 찾는다. 숫자가 둘 이상인 줄(추가 피해 범위 등)은
     * 거래소 필터로 옮기기 어려워 건너뛴다.
     */
    internal fun filters(item: PobItem, index: Map<String, String>, limit: Int = 3): List<TradeFilter> {
        if (index.isEmpty()) return emptyList()
        val picked = ArrayList<Pair<Double, TradeFilter>>()
        for (line in item.lines) {
            val value = oneNumber(line) ?: continue
            if (value <= 0.0) continue
            val id = index[normalize(line)] ?: continue
            val weight = GearPricing.lineWeight(line)
            if (weight <= 0.0) continue
            val min = if (value >= 20.0) Math.floor(value * 0.85) else Math.floor(value * 0.95)
            picked.add(weight to TradeFilter(id, max(0.0, min), line))
        }
        return picked.sortedByDescending { it.first }.take(limit).map { it.second }
    }

    /** 검색 요청 본문 — 테스트에서도 그대로 확인한다. */
    internal fun queryJson(
        item: PobItem,
        filters: List<TradeFilter>,
        includeType: Boolean = true,
    ): String {
        val body = StringBuilder()
        body.append("{\"query\":{\"status\":{\"option\":\"online\"},\"stats\":[{\"type\":\"and\",\"filters\":[")
        filters.forEachIndexed { i, filter ->
            if (i > 0) body.append(',')
            body.append("{\"id\":\"").append(filter.id).append("\",\"value\":{\"min\":")
            body.append(if (filter.min % 1.0 == 0.0) filter.min.toLong() else filter.min)
            body.append("},\"disabled\":false}")
        }
        body.append("]}]")
        if (includeType && item.baseType.isNotBlank()) {
            body.append(",\"type\":\"").append(escape(item.baseType)).append('"')
        }
        body.append("},\"sort\":{\"price\":\"asc\"}}")
        return body.toString()
    }

    private fun escape(value: String): String =
        value.replace("\\", "\\\\").replace("\"", "\\\"")

    /** fetch 응답에서 매물 가격만 뽑는다. 테스트에서도 그대로 돌린다. */
    internal fun parseQuotes(json: String): List<TradeQuote> {
        val out = ArrayList<TradeQuote>()
        val priceBlock = Regex("\"price\"\\s*:\\s*\\{([^{}]*)\\}")
        val amount = Regex("\"amount\"\\s*:\\s*([\\d.]+)")
        val currency = Regex("\"currency\"\\s*:\\s*\"([A-Za-z]+)\"")
        for (block in priceBlock.findAll(json)) {
            val body = block.groupValues[1]
            val amountValue = amount.find(body)?.groupValues?.get(1)?.toDoubleOrNull() ?: continue
            val currencyValue = currency.find(body)?.groupValues?.get(1).orEmpty()
            if (amountValue > 0.0 && currencyValue.isNotBlank()) {
                out.add(TradeQuote(amountValue, currencyValue))
            }
        }
        return out
    }

    /** 검색 응답에서 쿼리 id와 매물 해시 목록 */
    internal fun parseSearch(json: String): Pair<String, List<String>>? {
        val queryId = Regex("^\\s*\\{\\s*\"id\"\\s*:\\s*\"([^\"]+)\"")
            .find(json)?.groupValues?.get(1) ?: return null
        val body = Regex("\"result\"\\s*:\\s*\\[([^\\]]*)\\]").find(json)?.groupValues?.get(1).orEmpty()
        val ids = Regex("\"([0-9a-f]{16,})\"").findAll(body).map { it.groupValues[1] }.toList()
        return queryId to ids
    }

    /** 가격 목록의 중위값 (디바인) — 환율을 모르는 화폐는 뺀다 */
    fun medianDivine(quotes: List<TradeQuote>, rates: CurrencyRates): Double {
        val values = quotes.mapNotNull { it.divine(rates) }.filter { it.isFinite() && it > 0.0 }.sorted()
        if (values.isEmpty()) return 0.0
        val mid = values.size / 2
        return if (values.size % 2 == 1) values[mid] else (values[mid - 1] + values[mid]) / 2.0
    }

    /** 검색 한 번 — 실패(제한·차단·응답 없음)면 null */
    fun search(league: String, item: PobItem, index: Map<String, String>): List<TradeQuote>? {
        val filters = filters(item, index)
        if (filters.isEmpty() || item.baseType.isBlank()) return null
        val url = "$BASE/search/poe2/" + URLEncoder.encode(league, "UTF-8")
        var (code, text) = runCatching { get(url, queryJson(item, filters)) }.getOrElse { return null }
        // 기본형 이름을 거래소가 모르면(400) 옵션만으로 한 번 더 찾는다.
        if (code == 400 && item.baseType.isNotBlank()) {
            val retry = runCatching { get(url, queryJson(item, filters, includeType = false)) }
                .getOrElse { return null }
            code = retry.first
            text = retry.second
        }
        if (code !in 200..299 || text.isBlank()) return null
        val (queryId, ids) = runCatching { parseSearch(text) }.getOrNull() ?: return null
        if (queryId.isBlank()) return null
        if (ids.isEmpty()) return emptyList()
        val joined = ids.take(SAMPLE).joinToString(",")
        val fetchUrl = "$BASE/fetch/" + URLEncoder.encode(joined, "UTF-8") + "?query=" +
            URLEncoder.encode(queryId, "UTF-8")
        val (fcode, ftext) = runCatching { get(fetchUrl) }.getOrElse { return null }
        if (fcode !in 200..299 || ftext.isBlank()) return null
        return runCatching { parseQuotes(ftext) }.getOrNull()
    }

    fun clearCache() {
        statCache = null
    }
}

/** 장비 가치 산출 중 희귀 장비 유사 매물을 찾는 세션 (호출 한도 관리) */
class RareTrader(
    private val league: String,
    private val rates: CurrencyRates,
) {
    private val index: Map<String, String> by lazy { Trade2.statIndex() }
    private var budget = Trade2.MAX_SEARCHES
    private var lastAt = 0L

    /** 호출 제한에 걸려 조회를 그만뒀는지 */
    var skipped = false
        private set
    var searched = 0
        private set

    /** 이 아이템의 유사 매물 중위값 (조회할 수 없으면 null) */
    fun price(item: PobItem): TradePrice? {
        if (budget <= 0) {
            skipped = true
            return null
        }
        val wait = Trade2.SEARCH_GAP_MS - (System.currentTimeMillis() - lastAt)
        if (wait > 0) runCatching { Thread.sleep(wait) }
        lastAt = System.currentTimeMillis()
        budget--
        val quotes = Trade2.search(league, item, index)
        if (quotes == null) {
            skipped = true
            return null
        }
        if (quotes.isEmpty()) return null
        searched++
        val median = Trade2.medianDivine(quotes, rates)
        if (median <= 0.0) return null
        return TradePrice(GearPricing.round2(median), Trade2.filters(item, index).size, quotes.size)
    }
}
