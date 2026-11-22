package com.sunuhwi.poe2dbkr

/**
 * PoB가 저장하는 아이템 원문을 이름·베이스 타입·아이템 클래스·옵션 줄로 나눈다.
 *
 * PoB 1 형식(앱 내보내기 샘플)과 PoB 2 형식이 서로 다르다.
 *
 *   Rare: Stormshroud                |  Rarity: RARE
 *   Thunder Wolf Séance Bow          |  Corruption Goad
 *   --------                         |  Gelid Staff
 *   Bow                              |  Unique ID: 650f...
 *   Quality: +20 (augmented)         |  Item Level: 80
 *   ...                              |  Implicits: 0
 *                                    |  145% increased Spell Damage
 *
 * 두 형식 모두에서 "Rarity:"/구분선/PoB 메타 줄을 걷어내고 실제 옵션만 남긴다.
 */
object PobItemParser {

    private val RARITY_LINE = Regex("(?i)^rarity\\s*:\\s*(\\w*)")
    private val LEGACY_RARITY = Regex("^(Rare|Magic|Normal|Unique|Unidentified)\\s*:\\s*(.*)$")
    private val TAG_PREFIX = Regex("^(\\{[^}]*\\})+")
    private val MOD_RANGE = Regex("^<.*>$")

    private val META_PREFIXES = listOf(
        "Unique ID:", "Item Level:", "LevelReq:", "Implicits:", "Crafted:",
        "Prefix:", "Suffix:", "Sockets:", "Rune:", "Charm Slots:", "Note:",
        "Item Class:", "Requirements:", "Radius:", "Limited to:",
    )
    private val META_LINES = setOf(
        "Corrupted", "Mirrored", "Split", "Unidentified", "Synthesised", "Fractured",
    )

    fun apply(item: PobItem, raw: String) {
        val lines = raw.replace("\r", "")
            .split("\n")
            .map { it.trim() }
            .filter { it.isNotEmpty() && !MOD_RANGE.matches(it) }

        var rarity = item.rarity
        var name = ""
        var baseType = ""
        var itemClass = ""
        val body = ArrayList<String>()
        var start = 0

        val first = lines.firstOrNull()
        val legacy = first?.let { LEGACY_RARITY.find(it) }
        val modern = first?.let { RARITY_LINE.find(it) }
        when {
            legacy != null -> {
                rarity = normalizeRarity(legacy.groupValues[1])
                name = legacy.groupValues[2].trim()
                start = 1
            }
            modern != null -> {
                rarity = normalizeRarity(modern.groupValues[1])
                start = 1
            }
        }

        // "Item Class: Crossbow" 처럼 클래스가 명시된 경우
        lines.firstOrNull { it.startsWith("Item Class:", true) }?.let {
            itemClass = it.substringAfter(':').trim()
        }

        val content = lines.drop(start)
        var cursor = 0
        fun nextContent(): String? {
            while (cursor < content.size) {
                val line = content[cursor++]
                if (isMeta(line)) continue
                return line
            }
            return null
        }
        when (rarity) {
            "Normal" -> {
                if (name.isBlank()) name = nextContent().orEmpty()
                baseType = name
            }
            "Magic" -> {
                if (name.isBlank()) name = nextContent().orEmpty()
                baseType = guessBaseType(name)
            }
            else -> {
                if (name.isBlank()) name = nextContent().orEmpty()
                baseType = nextContent().orEmpty()
                if (baseType.isBlank()) baseType = guessBaseType(name)
            }
        }

        // 옵션/속성 줄: 이름·베이스·메타 줄을 뺀 나머지
        for (line in content) {
            if (isMeta(line)) continue
            if (line == name || line == baseType) continue
            body.add(cleanModLine(line))
        }

        // "Bow", "Staff" 같은 클래스 단독 줄은 클래스 정보로만 쓰고 옵션에서는 뺀다
        val classOnly = body.firstOrNull { single ->
            WeaponTypes.itemClassOf(single).isNotBlank() && single.length <= 18 &&
                !single.any { it.isDigit() } && !single.contains("+")
        }
        if (itemClass.isBlank() && classOnly != null) {
            itemClass = WeaponTypes.itemClassOf(classOnly)
            body.remove(classOnly)
        }
        if (itemClass.isBlank()) itemClass = WeaponTypes.itemClassOf(baseType, name)

        item.rarity = rarity
        item.name = name.ifBlank { baseType.ifBlank { rarity } }
        item.baseType = baseType
        item.itemClass = itemClass
        item.weapon = WeaponTypes.detect(itemClass, baseType, name)
        item.lines.clear()
        item.lines.addAll(body)
    }

    private fun isMeta(line: String): Boolean {
        if (line.startsWith("---")) return true
        if (META_LINES.any { line.equals(it, true) }) return true
        if (META_PREFIXES.any { line.startsWith(it, true) }) return true
        return false
    }

    private fun cleanModLine(line: String): String {
        var out = TAG_PREFIX.replace(line, "").trim()
        if (out.startsWith("*")) out = out.removePrefix("*").trim()
        return out
    }

    private fun normalizeRarity(raw: String): String = when (raw.trim().lowercase()) {
        "unique" -> "Unique"
        "magic" -> "Magic"
        "normal", "superior" -> "Normal"
        "rare" -> "Rare"
        "unidentified" -> "Unidentified"
        "" -> "Rare"
        else -> raw.trim().replaceFirstChar { it.uppercase() }
    }

    /** 마법 아이템은 베이스 타입 줄이 없어서 이름에서 흔한 베이스만 건져낸다 */
    private fun guessBaseType(name: String): String {
        val lower = name.lowercase()
        val known = listOf(
            "crossbow", "bow", "staff", "wand", "sceptre", "scepter", "quarterstaff",
            "spear", "sword", "axe", "mace", "hammer", "club", "dagger", "claw", "flail",
            "shield", "quiver", "focus",
        )
        val hit = known.firstOrNull { lower.contains(it) } ?: return ""
        return name.split(" ")
            .filter { it.lowercase().contains(hit) }
            .joinToString(" ")
            .ifBlank { name }
    }
}
