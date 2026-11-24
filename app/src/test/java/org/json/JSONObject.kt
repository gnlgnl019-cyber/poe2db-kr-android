package org.json

/**
 * JVM 단위 테스트용 최소 JSON 구현.
 *
 * 앱은 android.jar의 org.json을 쓰지만, 그 스텁은 테스트 JVM에서 예외만 던진다.
 * 시세 응답 해석(아이콘·이름 매핑)을 실제 페이로드로 검증하려고 필요한 만큼만 구현한다.
 */
class JSONException(message: String) : RuntimeException(message)

private class JsonParser(private val text: String) {
    private var pos = 0

    fun parse(): Any? {
        val value = readValue()
        skipWhitespace()
        return value
    }

    private fun skipWhitespace() {
        while (pos < text.length && text[pos].isWhitespace()) pos++
    }

    private fun readValue(): Any? {
        skipWhitespace()
        if (pos >= text.length) throw JSONException("unexpected end")
        return when (val c = text[pos]) {
            '{' -> readObject()
            '[' -> readArray()
            '"' -> readString()
            't' -> { expect("true"); true }
            'f' -> { expect("false"); false }
            'n' -> { expect("null"); null }
            else -> if (c == '-' || c.isDigit()) readNumber() else throw JSONException("unexpected $c")
        }
    }

    private fun expect(word: String) {
        if (!text.startsWith(word, pos)) throw JSONException("expected $word")
        pos += word.length
    }

    private fun readObject(): MutableMap<String, Any?> {
        val out = LinkedHashMap<String, Any?>()
        pos++ // {
        skipWhitespace()
        if (pos < text.length && text[pos] == '}') { pos++; return out }
        while (true) {
            skipWhitespace()
            val key = readString()
            skipWhitespace()
            if (pos >= text.length || text[pos] != ':') throw JSONException("expected :")
            pos++
            out[key] = readValue()
            skipWhitespace()
            if (pos >= text.length) throw JSONException("unterminated object")
            when (text[pos]) {
                ',' -> pos++
                '}' -> { pos++; return out }
                else -> throw JSONException("expected , or }")
            }
        }
    }

    private fun readArray(): MutableList<Any?> {
        val out = ArrayList<Any?>()
        pos++ // [
        skipWhitespace()
        if (pos < text.length && text[pos] == ']') { pos++; return out }
        while (true) {
            out.add(readValue())
            skipWhitespace()
            if (pos >= text.length) throw JSONException("unterminated array")
            when (text[pos]) {
                ',' -> pos++
                ']' -> { pos++; return out }
                else -> throw JSONException("expected , or ]")
            }
        }
    }

    private fun readString(): String {
        if (pos >= text.length || text[pos] != '"') throw JSONException("expected string")
        pos++
        val sb = StringBuilder()
        while (pos < text.length) {
            val c = text[pos++]
            when (c) {
                '"' -> return sb.toString()
                '\\' -> {
                    if (pos >= text.length) throw JSONException("bad escape")
                    when (val e = text[pos++]) {
                        '"' -> sb.append('"')
                        '\\' -> sb.append('\\')
                        '/' -> sb.append('/')
                        'b' -> sb.append('\b')
                        'f' -> sb.append('\u000C')
                        'n' -> sb.append('\n')
                        'r' -> sb.append('\r')
                        't' -> sb.append('\t')
                        'u' -> {
                            val hex = text.substring(pos, minOf(text.length, pos + 4))
                            sb.append(hex.toInt(16).toChar())
                            pos += 4
                        }
                        else -> throw JSONException("bad escape $e")
                    }
                }
                else -> sb.append(c)
            }
        }
        throw JSONException("unterminated string")
    }

    private fun readNumber(): Double {
        val start = pos
        if (pos < text.length && text[pos] == '-') pos++
        while (pos < text.length && (text[pos].isDigit() || text[pos] in ".eE+-")) pos++
        return text.substring(start, pos).toDouble()
    }
}

open class JSONObject {
    internal val map: MutableMap<String, Any?> = LinkedHashMap()

    constructor()
    constructor(text: String) {
        val parsed = JsonParser(text).parse()
        if (parsed !is Map<*, *>) throw JSONException("not an object")
        parsed.forEach { (k, v) -> map[k.toString()] = v }
    }

    internal constructor(source: Map<String, Any?>) {
        map.putAll(source)
    }

    fun has(key: String): Boolean = map.containsKey(key)

    fun isNull(key: String): Boolean = map[key] == null

    fun optJSONObject(key: String): JSONObject? = when (val value = map[key]) {
        null -> null
        is JSONObject -> value
        is Map<*, *> -> JSONObject(value.entries.associate { it.key.toString() to it.value })
        else -> null
    }

    fun optJSONArray(key: String): JSONArray? = when (val value = map[key]) {
        null -> null
        is JSONArray -> value
        is List<*> -> JSONArray(value)
        else -> null
    }

    fun optString(key: String): String = optString(key, "")

    fun optString(key: String, fallback: String): String {
        val value = map[key] ?: return fallback
        return when (value) {
            is String -> value
            is Number -> if (value.toDouble() == value.toLong().toDouble()) value.toLong().toString() else value.toString()
            is Boolean -> value.toString()
            else -> fallback
        }
    }

    fun getString(key: String): String = optString(key)

    fun optDouble(key: String, fallback: Double): Double {
        val value = map[key]
        return when (value) {
            is Number -> value.toDouble()
            is String -> value.toDoubleOrNull() ?: fallback
            else -> fallback
        }
    }

    fun optDouble(key: String): Double = optDouble(key, Double.NaN)

    fun optInt(key: String, fallback: Int): Int {
        val value = map[key]
        return when (value) {
            is Number -> value.toInt()
            is String -> value.toIntOrNull() ?: fallback
            else -> fallback
        }
    }

    fun optBoolean(key: String, fallback: Boolean = false): Boolean {
        val value = map[key]
        return when (value) {
            is Boolean -> value
            is String -> value == "true"
            else -> fallback
        }
    }

    fun length(): Int = map.size

    /** 실제 org.json과 같은 이름 순회 (테스트에서도 이름 목록을 쓸 수 있게) */
    fun keys(): MutableIterator<String> = map.keys.iterator()

    override fun toString(): String = map.toString()
}

class JSONArray {
    internal val list: MutableList<Any?> = ArrayList()

    constructor()
    constructor(text: String) {
        val parsed = JsonParser(text).parse()
        if (parsed !is List<*>) throw JSONException("not an array")
        list.addAll(parsed)
    }

    internal constructor(source: List<*>) {
        list.addAll(source)
    }

    fun length(): Int = list.size

    fun isNull(index: Int): Boolean = index < 0 || index >= list.size || list[index] == null

    fun optJSONObject(index: Int): JSONObject? = when (val value = list.getOrNull(index)) {
        null -> null
        is JSONObject -> value
        is Map<*, *> -> JSONObject(value.entries.associate { it.key.toString() to it.value })
        else -> null
    }

    fun optJSONArray(index: Int): JSONArray? = when (val value = list.getOrNull(index)) {
        null -> null
        is JSONArray -> value
        is List<*> -> JSONArray(value)
        else -> null
    }

    fun optString(index: Int): String = optString(index, "")

    fun optString(index: Int, fallback: String): String = when (val value = list.getOrNull(index)) {
        null -> fallback
        is String -> value
        is Number -> value.toString()
        is Boolean -> value.toString()
        else -> fallback
    }

    fun getString(index: Int): String = optString(index)

    fun optDouble(index: Int): Double = when (val value = list.getOrNull(index)) {
        is Number -> value.toDouble()
        is String -> value.toDoubleOrNull() ?: Double.NaN
        else -> Double.NaN
    }

    fun optDouble(index: Int, fallback: Double): Double = when (val value = list.getOrNull(index)) {
        is Number -> value.toDouble()
        is String -> value.toDoubleOrNull() ?: fallback
        else -> fallback
    }

    fun optInt(index: Int, fallback: Int = 0): Int = when (val value = list.getOrNull(index)) {
        is Number -> value.toInt()
        is String -> value.toIntOrNull() ?: fallback
        else -> fallback
    }

    fun optBoolean(index: Int, fallback: Boolean = false): Boolean = when (val value = list.getOrNull(index)) {
        is Boolean -> value
        is String -> value == "true"
        else -> fallback
    }

    fun put(value: Any?): JSONArray {
        list.add(value)
        return this
    }

    override fun toString(): String = list.toString()
}
