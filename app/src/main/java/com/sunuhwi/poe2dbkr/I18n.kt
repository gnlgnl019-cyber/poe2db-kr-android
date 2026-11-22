package com.sunuhwi.poe2dbkr

import android.content.Context
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicReference

/** 한글 사전에서 아이템 하나에 해당하는 정확한 옵션 묶음 */
data class KrItemMods(val implicits: List<String>, val explicits: List<String>)

/**
 * poe.ninja 영문 응답과 PoB 영문 아이템을 화면에 한글로 보여 주기 위한 사전.
 *
 * assets/poe2_kr_i18n.json (scripts/fetch_poe2_i18n.py가 생성)을 그대로 읽는다.
 * 이름은 poe2db.tw/kr 페이지, 옵션 문장은 고유 아이템의 영문/한글 옵션을 짝지어 만든 틀이다.
 */
class KrDict(
    val names: Map<String, String> = emptyMap(),
    val bases: Map<String, String> = emptyMap(),
    val icons: Map<String, String> = emptyMap(),
    val mods: Map<String, String> = emptyMap(),
    val itemMods: Map<String, KrItemMods> = emptyMap(),
    val classes: Map<String, String> = emptyMap(),
    val categories: Map<String, String> = emptyMap(),
    val classIcons: Map<String, String> = emptyMap(),
    val props: Map<String, String> = emptyMap(),
    val tags: Map<String, String> = emptyMap(),
    val fetchedAt: String = "",
) {

    companion object {
        val EMPTY = KrDict()

        /** "Uncut Skill Gem (Level 17)" 처럼 등급이 붙은 이름을 떼어 내는 규칙 */
        private val LEVEL = Regex("^(.*?)\\s*\\(Level\\s*(\\d+)\\)$", RegexOption.IGNORE_CASE)
        private const val PLACEHOLDER = "#"
        /** (10—20) 처럼 범위로 적힌 값 */
        private val RANGE = Regex("\\(\\s*([-+]?\\d+(?:\\.\\d+)?)\\s*[-~—–]\\s*([-+]?\\d+(?:\\.\\d+)?)\\s*\\)")
        /** 부호가 붙을 수 있는 숫자 */
        private val SIGNED = Regex("([-+])?(\\d+(?:\\.\\d+)?)")
        /** PoB가 줄 앞뒤에 붙이는 {tags:...} {range:0.5} 같은 표시 */
        private val BRACES = Regex("\\{[^}]*\\}")
        /** PoB가 줄 끝에 다는 (implicit) (crafted) 같은 꼬리표 */
        private val TAIL_TAG = Regex(
            "\\s*\\((implicit|crafted|enchant|fractured|desecrated|rune|augmented|scourged|veiled|custom)\\)\\s*$",
            RegexOption.IGNORE_CASE,
        )
        private val SPACES = Regex("\\s+")
        /** 반경 안 패시브에 덧붙는 옵션의 앞뒤 문장 */
        private val RADIUS = listOf(
            Triple("notable passive skills in radius also grant ", "반경 내 주요 패시브 스킬이 ", " 도 부여"),
            Triple("small passive skills in radius also grant ", "반경 내 소형 패시브 스킬이 ", " 도 부여"),
        )
        private val PREFIXES = listOf(
            "Runemastered " to "룬 장인의 ",
            "Superior " to "상급 ",
            "Corrupted " to "타락한 ",
        )

        fun parse(raw: String): KrDict {
            val root = JSONObject(raw)
            return KrDict(
                names = pairs(root.optJSONArray("names")),
                bases = pairs(root.optJSONArray("bases")),
                icons = pairs(root.optJSONArray("icons")),
                mods = pairs(root.optJSONArray("mods")),
                itemMods = root.optJSONArray("itemMods")?.let { arr ->
                    val out = HashMap<String, KrItemMods>(arr.length())
                    for (i in 0 until arr.length()) {
                        val row = arr.optJSONArray(i) ?: continue
                        val key = row.optString(0).lowercase()
                        if (key.isEmpty()) continue
                        out[key] = KrItemMods(
                            implicits = row.optJSONArray(1).asStrings(),
                            explicits = row.optJSONArray(2).asStrings(),
                        )
                    }
                    out
                }.orEmpty(),
                classes = pairs(root.optJSONArray("classes")),
                categories = pairs(root.optJSONArray("categories")),
                classIcons = pairs(root.optJSONArray("classIcons")),
                props = pairs(root.optJSONArray("props")),
                tags = pairs(root.optJSONArray("tags")),
                fetchedAt = root.optString("fetchedAt"),
            )
        }

        /** [키, 값] 배열을 소문자 키 맵으로 */
        private fun pairs(arr: org.json.JSONArray?): Map<String, String> {
            if (arr == null) return emptyMap()
            val out = HashMap<String, String>(arr.length())
            for (i in 0 until arr.length()) {
                val row = arr.optJSONArray(i) ?: continue
                val key = row.optString(0).lowercase()
                val value = row.optString(1)
                if (key.isNotEmpty() && value.isNotBlank()) out[key] = value
            }
            return out
        }
    }

    fun isEmpty(): Boolean = names.isEmpty() && bases.isEmpty()

    /**
     * 패시브 이름 (영문 → 한글). 트리 자료를 읽은 뒤 한 번 채워 두면
     * "Allocates Kite Runner" 같은 인챈트 문장도 한글로 바뀐다.
     */
    var passiveNames: Map<String, String> = emptyMap()

    /** 영문 아이템 이름 → 한글 이름. 사전에 없으면 빈 문자열. */
    fun name(en: String): String {
        val key = key(en)
        if (key.isEmpty()) return ""
        names[key]?.let { return it }
        // "Uncut Skill Gem (Level 17)" → "미가공 스킬 젬 (17레벨)"
        LEVEL.matchEntire(en.trim())?.let { match ->
            val base = names[key(match.groupValues[1])] ?: return ""
            return "$base (${match.groupValues[2]}레벨)"
        }
        // "Runemastered Spiked Buckler" 같은 접두 표기
        for ((prefix, koPrefix) in PREFIXES) {
            if (en.startsWith(prefix, true)) {
                val rest = en.substring(prefix.length)
                val base = bases[key(rest)] ?: names[key(rest)] ?: continue
                return koPrefix + base
            }
        }
        return ""
    }

    /** 영문 베이스 타입 → 한글. 무기·방어구 목록이 곧 베이스 사전이다. */
    fun base(en: String): String {
        val key = key(en)
        if (key.isEmpty()) return ""
        bases[key]?.let { return it }
        names[key]?.let { if (it != en) return it }
        for ((prefix, koPrefix) in PREFIXES) {
            if (en.startsWith(prefix, true)) {
                val rest = en.substring(prefix.length)
                val base = bases[key(rest)] ?: names[key(rest)] ?: continue
                return koPrefix + base
            }
        }
        return ""
    }

    fun icon(en: String): String {
        val key = key(en)
        if (key.isEmpty()) return ""
        icons[key]?.let { return it }
        for ((prefix, _) in PREFIXES) {
            if (en.startsWith(prefix, true)) {
                val rest = en.substring(prefix.length)
                icons[key(rest)]?.let { return it }
            }
        }
        return ""
    }

    /** 아이템 클래스 한글명. 사전에 없으면 원문을 그대로 돌려준다. */
    fun classKo(en: String): String {
        if (en.isBlank()) return ""
        classes[key(en)]?.let { return it }
        // "One Hand Sword" 처럼 두 단어인 경우 마지막 단어로 다시 시도
        val last = en.trim().substringAfterLast(' ')
        return classes[key(last)] ?: en
    }

    /** poe.ninja 카테고리("[Sword|One Hand Sword]")를 한글로. */
    fun category(raw: String): String {
        if (raw.isBlank()) return ""
        val inner = raw.trim().removePrefix("[").removeSuffix("]")
        val visible = inner.substringAfter('|', inner).trim()
        categories[key(visible)]?.let { return it }
        categories[key(inner.substringBefore('|').trim())]?.let { return it }
        categories[key(raw.trim())]?.let { return it }
        return reverseLookup(categories, visible) ?: reverseLookup(categories, inner) ?: visible
    }

    /** 클래스별 대표 아이콘 (베이스 타입을 못 찾았을 때 쓰는 마지막 대체값) */
    fun classIcon(className: String): String = classIcons[key(className)].orEmpty()

    /**
     * 아이템 단위 옵션. 게임 한국어 데이터에 영문으로 남아 있는 줄(예: "Has 5 Augment Sockets (Hidden)")은
     * 문장 틀로 다시 한 번 옮긴다.
     */
    fun itemModsFor(name: String): KrItemMods? {
        val raw = itemMods[key(name)] ?: return null
        return KrItemMods(raw.implicits.map { localizeIfEnglish(it) }, raw.explicits.map { localizeIfEnglish(it) })
    }

    private fun localizeIfEnglish(line: String): String {
        if (line.isBlank() || line.any { it.code > 0x7F }) return line
        return modLine(line).ifBlank { line }
    }

    /**
     * 영문 옵션 한 줄을 한글로. 사전 문장 틀에 숫자를 원문 순서대로 채운다.
     * 틀이 없으면 빈 문자열(화면에서는 영문을 그대로 보여 준다).
     */
    fun modLine(en: String): String {
        val text = en.trim()
        if (text.isEmpty()) return ""
        // PoB 2는 줄 앞에 {crafted}{fractured} 같은 꼬리표를 붙인다. 한글 꼬리표로 옮겨 둔다.
        var tag = ""
        var head = text
        while (head.startsWith("{")) {
            val close = head.indexOf('}')
            if (close < 0) break
            val word = head.substring(1, close).substringBefore(':').lowercase().trim()
            if (tags.containsKey(word)) tag = word
            head = head.substring(close + 1).trim()
        }
        var line = Market.cleanTags(BRACES.replace(head, "")).trim()
        if (line.isEmpty()) return ""
        TAIL_TAG.find(line)?.let { match ->
            tag = match.groupValues[1].lowercase()
            line = line.removeRange(match.range).trim()
        }
        val body = translateLine(line) ?: return ""
        val suffix = tags[tag]?.let { " ($it)" }.orEmpty()
        return body + suffix
    }

    /** "속성 이름: 값" 형태의 아이템 머리말 (품질·방어도·요구 사항 등) */
    private fun translateProperty(line: String): String? {
        val split = line.indexOf(": ")
        if (split <= 0) return null
        val label = props[line.substring(0, split).lowercase().trim()] ?: return null
        var value = line.substring(split + 2).trim()
        // "Requires: Level 64" → "요구 사항: 레벨 64"
        if (value.startsWith("Level ", true)) value = "레벨 " + value.substring(6)
        var tag = ""
        TAIL_TAG.find(value)?.let { match ->
            tag = match.groupValues[1].lowercase()
            value = value.removeRange(match.range).trim()
        }
        val suffix = tags[tag]?.let { " ($it)" }.orEmpty()
        return "$label: $value$suffix"
    }

    /** 옵션 한 줄을 사전으로 옮긴다. 접두 규칙(Bonded 등)을 먼저 보고 문장 틀을 쓴다. */
    private fun translateLine(line: String, depth: Int = 0): String? {
        if (line.isEmpty()) return null
        val lower = line.lowercase()
        if (depth < 3) {
            // 룬 결속 옵션: "Bonded: +20 to maximum Life" → "결속: 생명력 최대치 +20"
            if (lower.startsWith("bonded: ")) {
                translateLine(line.substring(8).trim(), depth + 1)?.let { return "결속: $it" }
            }
            // 반경 내 패시브에 덧붙는 옵션 (오래된 주얼)
            for ((prefix, before, after) in RADIUS) {
                if (lower.startsWith(prefix)) {
                    translateLine(line.substring(prefix.length).trim(), depth + 1)
                        ?.let { return "$before$it$after" }
                }
            }
            // 인첸트가 지정하는 패시브: "Allocates Kite Runner" → "카이트 러너 할당"
            if (lower.startsWith("allocates ")) {
                val name = line.substring(10).trim()
                passiveNames[name.lowercase()]?.let { return "$it 할당" }
            }
        }
        translateProperty(line)?.let { return it }
        mods[templateOf(line)]?.let { template -> return fillNumbers(template, line) }
        // 줄 자체는 사전에 없지만 부호만 반대인 짝이 있으면 부호를 뒤집어 쓴다.
        // ("-30% to Lightning Resistance" → "번개 저항 -30%")
        if (line.startsWith("-")) {
            val flipped = translateLine(line.substring(1).trim(), depth)
            if (flipped != null) return negateNumbers(flipped)
        }
        // 마법/희귀 아이템 앞에 붙는 접사 등급 표기 제거 후 재시도
        val stripped = line.replace(Regex("^(?:\\([^)]*\\)\\s*)+"), "")
        if (stripped != line) mods[templateOf(stripped)]?.let { return fillNumbers(it, stripped) }
        return null
    }

    private fun negateNumbers(text: String): String {
        val withMinus = Regex("\\+(\\d)").replace(text) { "-${it.groupValues[1]}" }
        if (withMinus != text) return withMinus
        return Regex("\\d").replace(text) { "-${it.value}" }
    }

    /** 영문 옵션 문장을 사전 키 형태(숫자 → #, 범위 → #, 소문자)로 바꾼다. */
    internal fun templateOf(text: String): String = canonical(text, keepSign = false)

    /**
     * 사전 만들기 스크립트와 같은 규칙으로 숫자를 지운다.
     * 범위 (10—20)은 값 하나로 보고, 열쇠에서는 + 부호를 지운다.
     */
    private fun canonical(text: String, keepSign: Boolean): String {
        var t = Market.cleanTags(BRACES.replace(text, ""))
        t = t.replace('—', '-').replace('–', '-')
        t = RANGE.replace(t, PLACEHOLDER)
        t = SIGNED.replace(t) { match ->
            when (match.groupValues[1]) {
                "-" -> "-#"
                "+" -> if (keepSign) "+#" else "#"
                else -> "#"
            }
        }
        if (!keepSign) t = t.replace("+#", "#")
        return SPACES.replace(t.trim().lowercase(), " ").trimEnd('.', ' ')
    }

    /** 영문 옵션 목록 전체를 한글로. 번역이 없는 줄은 영문 그대로 남긴다. */
    fun modLines(lines: List<String>): List<String> = lines.map { line ->
        val ko = modLine(line)
        if (ko.isNotBlank()) ko else Market.cleanTags(line)
    }

    /** 사전 커버리지 (보고·테스트용) */
    fun stats(): String =
        "이름 ${names.size} · 베이스 ${bases.size} · 아이콘 ${icons.size} · 옵션 틀 ${mods.size}"

    private fun fillNumbers(template: String, english: String): String {
        val numbers = SIGNED.findAll(english).map {
            (if (it.groupValues[1] == "-") "-" else "") to it.groupValues[2]
        }.toList()
        if (numbers.isEmpty()) return template
        var index = 0
        val out = StringBuilder(template.length + 8)
        for (ch in template) {
            if (ch == PLACEHOLDER.first()) {
                val (sign, digits) = numbers.getOrNull(index) ?: ("" to PLACEHOLDER)
                index++
                val prev = out.lastOrNull()
                if (prev == '+' || prev == '-') {
                    out.append(digits)
                } else {
                    if (sign == "-") out.append('-')
                    out.append(digits)
                }
            } else {
                out.append(ch)
            }
        }
        return out.toString()
    }

    private fun reverseLookup(map: Map<String, String>, value: String): String? {
        if (value.isBlank()) return null
        return map.entries.firstOrNull { it.value == value }?.key?.let { map[it] }
    }

    private fun key(en: String): String = en.lowercase().trim()
}

/** assets의 사전을 한 번만 읽어 두고 앱 전체가 같이 쓴다. */
object I18n {

    private const val ASSET = "poe2_kr_i18n.json"
    private val cached = AtomicReference<KrDict?>(null)

    fun of(context: Context): KrDict {
        cached.get()?.let { return it }
        val loaded = runCatching {
            val raw = context.assets.open(ASSET).bufferedReader().use { it.readText() }
            KrDict.parse(raw)
        }.getOrElse { KrDict.EMPTY }
        loaded.passiveNames = runCatching { passiveNamesOf(context) }.getOrDefault(emptyMap())
        cached.compareAndSet(null, loaded)
        return cached.get() ?: loaded
    }

    /** 트리 자료의 패시브 이름을 "영문 소문자 → 한글"로 편다. */
    private fun passiveNamesOf(context: Context): Map<String, String> =
        Poe2Tree.of(context).nodes.values
            .filter { it.nameEn.isNotBlank() && it.nameKo.isNotBlank() }
            .associate { it.nameEn.lowercase() to it.nameKo }

    fun ofRaw(raw: String): KrDict = KrDict.parse(raw)

    /** 화면이 처음 열릴 때 미리 읽어 둔다 (메인 스레드 파싱 방지) */
    fun preload(context: Context, onReady: (KrDict) -> Unit) {
        cached.get()?.let { onReady(it); return }
        val app = context.applicationContext
        Thread {
            val dict = of(app)
            android.os.Handler(android.os.Looper.getMainLooper()).post { onReady(dict) }
        }.start()
    }
}
