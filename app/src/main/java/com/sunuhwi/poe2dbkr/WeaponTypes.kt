package com.sunuhwi.poe2dbkr

/**
 * 스틱맨이 손에 드는 무기 모양. PoB의 무기 슬롯 아이템(베이스 타입·아이템 클래스)에서 먼저 판정하고,
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

    /** 이 무기를 들었을 때의 공격 형태 */
    val form: AttackForm
        get() = when (this) {
            BOW -> AttackForm.BOW
            CROSSBOW -> AttackForm.CROSSBOW
            STAFF, WAND, SCEPTRE -> AttackForm.CAST
            QUARTERSTAFF, SPEAR, SWORD, AXE, MACE, DAGGER, CLAW, FLAIL, SHIELD -> AttackForm.MELEE
            NONE -> AttackForm.UNARMED
        }

    val isRanged: Boolean get() = form == AttackForm.BOW || form == AttackForm.CROSSBOW || form == AttackForm.CAST
}

/**
 * 공격 형태. 무기가 정하고, 무기 정보가 없을 때만 스킬로 정한다.
 * 근접은 적에게 붙어 휘두르고, 활은 시위를 당겨 화살을, 쇠뇌는 볼트를,
 * 지팡이류는 무기를 치켜들고 주문을 쏜다.
 */
enum class AttackForm(val label: String) {
    MELEE("근접"),
    BOW("활"),
    CROSSBOW("쇠뇌"),
    CAST("주문"),
    UNARMED("맨손"),
}

object WeaponTypes {

    /** 앞쪽 규칙이 먼저 이긴다. (예: Crossbow가 Bow보다 먼저, Quarterstaff가 Staff보다 먼저) */
    private val RULES: List<Pair<WeaponVisualType, List<String>>> = listOf(
        WeaponVisualType.CROSSBOW to listOf("crossbow", "cross bow", "쇠뇌", "석궁"),
        WeaponVisualType.QUARTERSTAFF to listOf("quarterstaff", "quarter staff", "warstaff", "육척봉", "쿼터스태프"),
        WeaponVisualType.BOW to listOf("bow", "활"),
        WeaponVisualType.STAFF to listOf("staff", "stave", "지팡이", "스태프"),
        WeaponVisualType.WAND to listOf("wand", "마법봉", "완드"),
        WeaponVisualType.SCEPTRE to listOf("sceptre", "scepter", "셉터", "홀"),
        WeaponVisualType.SPEAR to listOf("spear", "창"),
        WeaponVisualType.DAGGER to listOf("dagger", "단검", "단도"),
        WeaponVisualType.CLAW to listOf("claw", "손톱", "클로"),
        WeaponVisualType.FLAIL to listOf("flail", "도리깨", "플레일"),
        WeaponVisualType.AXE to listOf("axe", "도끼"),
        WeaponVisualType.MACE to listOf("mace", "hammer", "club", "maul", "sledge", "철퇴", "망치", "해머", "둔기"),
        WeaponVisualType.SWORD to listOf("sword", "blade", "sabre", "saber", "검"),
        WeaponVisualType.SHIELD to listOf("shield", "buckler", "targe", "방패"),
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

    /** 무기 정보가 전혀 없을 때 스킬 이펙트로만 고르는 대체값 */
    fun fallbackFor(fx: SkillEffectType): WeaponVisualType = when (fx) {
        SkillEffectType.ICE, SkillEffectType.LIGHTNING -> WeaponVisualType.BOW
        SkillEffectType.FIRE, SkillEffectType.CHAOS, SkillEffectType.MINION -> WeaponVisualType.STAFF
        SkillEffectType.MELEE -> WeaponVisualType.SWORD
    }

    /**
     * 착용 무기 → 공격 형태.
     * 무기를 알면 무기가 이기고(활은 활, 쇠뇌는 쇠뇌, 지팡이는 주문), 무기가 없을 때만 스킬로 정한다.
     */
    fun attackForm(weapon: WeaponVisualType, fx: SkillEffectType): AttackForm = when (weapon) {
        WeaponVisualType.NONE -> if (fx == SkillEffectType.MELEE) AttackForm.MELEE else AttackForm.CAST
        else -> weapon.form
    }

    private val CLASS_KEYS: List<Pair<String, List<String>>> = listOf(
        "Crossbow" to listOf("crossbow", "쇠뇌", "석궁"),
        "Quarterstaff" to listOf("quarterstaff", "육척봉"),
        "Bow" to listOf("bow", "활"),
        "Staff" to listOf("staff", "지팡이"),
        "Wand" to listOf("wand", "마법봉"),
        "Sceptre" to listOf("sceptre", "scepter", "셉터", "홀"),
        "Spear" to listOf("spear", "창"),
        "Dagger" to listOf("dagger", "단검"),
        "Claw" to listOf("claw", "손톱"),
        "Flail" to listOf("flail", "도리깨"),
        "Axe" to listOf("axe", "도끼"),
        "Mace" to listOf("mace", "hammer", "club", "maul", "철퇴", "망치"),
        "Sword" to listOf("sword", "blade", "검"),
        "Shield" to listOf("shield", "buckler", "targe", "방패"),
        "Quiver" to listOf("quiver", "화살통"),
        "Focus" to listOf("focus", "집중구"),
        "Helmet" to listOf("helmet", "circlet", "hood", "mask", "crown", "helm", "투구"),
        "Body Armour" to listOf("body armour", "body armor", "robe", "vest", "mail", "coat", "garb", "cuirass", "갑옷"),
        "Gloves" to listOf("gloves", "bracers", "mitts", "gauntlets", "장갑"),
        "Boots" to listOf("boots", "slippers", "shoes", "greaves", "sabatons", "장화"),
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
