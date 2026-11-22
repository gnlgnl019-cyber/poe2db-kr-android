package com.sunuhwi.poe2dbkr

/**
 * 스틱맨이 손에 드는 무기 모양. PoB의 Weapon 1 아이템(베이스 타입·아이템 클래스)에서 먼저 판정하고,
 * 무기 정보가 없을 때만 스킬 이펙트로 대신 고른다.
 */
enum class WeaponVisualType(val label: String) {
    BOW("활"),
    CROSSBOW("쇠뇌"),
    STAFF("지팡이"),
    WAND("완드"),
    SCEPTRE("홀"),
    QUARTERSTAFF("육척봉"),
    SPEAR("창"),
    SWORD("검"),
    AXE("도끼"),
    MACE("철퇴"),
    DAGGER("단검"),
    CLAW("손톱"),
    FLAIL("도리깨"),
    SHIELD("방패"),
    NONE("맨손"),
    ;

    val isRanged: Boolean
        get() = this == BOW || this == CROSSBOW || this == WAND || this == SCEPTRE ||
            this == STAFF || this == QUARTERSTAFF
}

object WeaponTypes {

    /** 앞쪽 규칙이 먼저 이긴다. (예: Crossbow가 Bow보다 먼저) */
    private val RULES: List<Pair<WeaponVisualType, List<String>>> = listOf(
        WeaponVisualType.CROSSBOW to listOf("crossbow", "cross bow", "쇠뇌", "석궁"),
        WeaponVisualType.QUARTERSTAFF to listOf("quarterstaff", "quarter staff", "육척봉", "쿼터스태프"),
        WeaponVisualType.BOW to listOf("bow", "활"),
        WeaponVisualType.STAFF to listOf("staff", "stave", "지팡이", "스태프"),
        WeaponVisualType.WAND to listOf("wand", "완드"),
        WeaponVisualType.SCEPTRE to listOf("sceptre", "scepter", "홀"),
        WeaponVisualType.SPEAR to listOf("spear", "창"),
        WeaponVisualType.DAGGER to listOf("dagger", "단검"),
        WeaponVisualType.CLAW to listOf("claw", "손톱", "클로"),
        WeaponVisualType.FLAIL to listOf("flail", "도리깨", "플레일"),
        WeaponVisualType.AXE to listOf("axe", "도끼"),
        WeaponVisualType.MACE to listOf("mace", "hammer", "club", "철퇴", "망치", "해머", "둔기"),
        WeaponVisualType.SWORD to listOf("sword", "blade", "검"),
        WeaponVisualType.SHIELD to listOf("shield", "방패"),
    )

    /** 아이템 클래스 줄(예: "Crossbow", "Staff")을 우선 쓰고, 없으면 베이스 타입/이름에서 찾는다. */
    fun detect(vararg texts: String?): WeaponVisualType {
        val joined = texts.filterNotNull().filter { it.isNotBlank() }.joinToString(" ")
        if (joined.isBlank()) return WeaponVisualType.NONE
        val lower = joined.lowercase()
        for ((type, keys) in RULES) {
            if (keys.any { lower.contains(it) }) return type
        }
        return WeaponVisualType.NONE
    }

    fun isTwoHanded(type: WeaponVisualType): Boolean = when (type) {
        WeaponVisualType.BOW, WeaponVisualType.CROSSBOW, WeaponVisualType.STAFF,
        WeaponVisualType.QUARTERSTAFF, WeaponVisualType.SPEAR, WeaponVisualType.FLAIL,
        -> true
        else -> false
    }

    /** 무기 정보가 전혀 없을 때 스킬 이펙트로만 고르는 예전 방식의 대체값 */
    fun fallbackFor(fx: SkillEffectType): WeaponVisualType = when (fx) {
        SkillEffectType.ICE, SkillEffectType.LIGHTNING -> WeaponVisualType.BOW
        SkillEffectType.FIRE, SkillEffectType.CHAOS, SkillEffectType.MINION -> WeaponVisualType.STAFF
        SkillEffectType.MELEE -> WeaponVisualType.SWORD
    }

    private val CLASS_KEYS: List<Pair<String, List<String>>> = listOf(
        "Crossbow" to listOf("crossbow", "쇠뇌", "석궁"),
        "Quarterstaff" to listOf("quarterstaff", "육척봉"),
        "Bow" to listOf("bow", "활"),
        "Staff" to listOf("staff", "지팡이"),
        "Wand" to listOf("wand", "완드"),
        "Sceptre" to listOf("sceptre", "scepter", "홀"),
        "Spear" to listOf("spear", "창"),
        "Dagger" to listOf("dagger", "단검"),
        "Claw" to listOf("claw", "손톱"),
        "Flail" to listOf("flail", "도리깨"),
        "Axe" to listOf("axe", "도끼"),
        "Mace" to listOf("mace", "hammer", "club", "철퇴", "망치"),
        "Sword" to listOf("sword", "검"),
        "Shield" to listOf("shield", "방패"),
        "Quiver" to listOf("quiver", "화살통"),
        "Focus" to listOf("focus", "집중구"),
        "Helmet" to listOf("helmet", "circlet", "hood", "mask", "crown", "투구"),
        "Body Armour" to listOf("body armour", "robe", "vest", "mail", "coat", "garb", "갑옷"),
        "Gloves" to listOf("gloves", "bracers", "mitts", "장갑"),
        "Boots" to listOf("boots", "slippers", "shoes", "장화"),
        "Amulet" to listOf("amulet", "talisman", "목걸이"),
        "Ring" to listOf("ring", "반지"),
        "Belt" to listOf("belt", "sash", "허리띠"),
        "Charm" to listOf("charm", "부적"),
        "Flask" to listOf("flask", "플라스크"),
    )

    /** 아이템 클래스 이름을 추정한다. (PoB 원문에는 클래스 줄이 없는 경우가 많다) */
    fun itemClassOf(vararg texts: String?): String {
        val lower = texts.filterNotNull().filter { it.isNotBlank() }.joinToString(" ").lowercase()
        if (lower.isBlank()) return ""
        for ((label, keys) in CLASS_KEYS) {
            if (keys.any { lower.contains(it) }) return label
        }
        return ""
    }
}
