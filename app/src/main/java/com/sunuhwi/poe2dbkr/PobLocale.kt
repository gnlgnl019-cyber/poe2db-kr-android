package com.sunuhwi.poe2dbkr

/**
 * PoB 아이템을 화면에 보여 줄 때 쓰는 한글 표기.
 *
 * PoB는 아이템을 영문으로 저장하므로, 장비 탭·슬롯 시트에서 이름·베이스 타입·아이템 클래스·옵션 줄을
 * 사전(poe2_kr_i18n.json)으로 옮긴다. 희귀 아이템의 무작위 이름은 게임에 고정 번역이 없어
 * 베이스 타입을 대표 이름으로 쓰고 원문 이름은 보조 줄에 남긴다.
 */
object PobLocale {

    /** 고유 아이템 이름 (한글). 없으면 빈 문자열. */
    fun uniqueName(dict: KrDict, item: PobItem): String =
        if (item.rarity.equals("Unique", true)) dict.name(item.name) else ""

    fun base(dict: KrDict, item: PobItem): String {
        val ko = dict.base(item.baseType)
        if (ko.isNotBlank()) return ko
        return dict.name(item.baseType)
    }

    fun classKo(dict: KrDict, item: PobItem): String {
        val fromClass = dict.classKo(item.itemClass)
        if (fromClass.isNotBlank() && fromClass != item.itemClass) return fromClass
        if (item.weapon != WeaponVisualType.NONE) return item.weapon.label
        return item.itemClass
    }

    /** 카드·시트 제목: "붉은 부리 · 절단도" 처럼 고유명과 베이스를 함께. */
    fun title(dict: KrDict, item: PobItem): String {
        val unique = uniqueName(dict, item)
        val baseKo = base(dict, item)
        val nameKo = dict.name(item.name)
        val classKo = classKo(dict, item).takeIf { it != item.itemClass }
        return when {
            unique.isNotBlank() && baseKo.isNotBlank() -> "$unique · $baseKo"
            unique.isNotBlank() -> unique
            baseKo.isNotBlank() && !nameKo.isBlank() && item.name != item.baseType ->
                "$nameKo · $baseKo"
            baseKo.isNotBlank() -> baseKo
            nameKo.isNotBlank() -> nameKo
            // 사전에 없는 베이스(구버전·커스텀 이름)는 클래스라도 한글로 앞에 붙인다.
            item.baseType.isNotBlank() && !classKo.isNullOrBlank() -> "$classKo · ${item.baseType}"
            else -> item.displayName
        }
    }

    /** 제목 아래 작은 줄: 영문 이름·클래스·베이스. */
    fun subtitle(dict: KrDict, item: PobItem): String {
        val parts = ArrayList<String>(3)
        val classKo = classKo(dict, item)
        if (classKo.isNotBlank()) parts.add(classKo)
        if (item.baseType.isNotBlank() && item.baseType != item.name) parts.add(item.baseType)
        if (item.name.isNotBlank() && item.rarity != "Normal" && dict.name(item.name).isBlank()) {
            parts.add(item.name)
        }
        return parts.distinct().joinToString(" · ")
    }

    /** 옵션 줄 한글화. 번역이 없는 줄은 영문 그대로 둔다. */
    fun lines(dict: KrDict, item: PobItem): List<String> {
        val exact = dict.itemModsFor(item.name)
        if (exact != null && (exact.implicits.isNotEmpty() || exact.explicits.isNotEmpty())) {
            return exact.implicits + exact.explicits
        }
        return item.lines.map { line ->
            val ko = dict.modLine(line)
            if (ko.isNotBlank()) ko else line
        }
    }

    /** 슬롯 카드에 띄울 아이콘 주소. 고유명 → 베이스 타입 → 클래스 대표 아이콘 순. */
    fun icon(dict: KrDict, item: PobItem): String {
        val byName = dict.icon(item.name)
        if (byName.isNotBlank() && item.rarity.equals("Unique", true)) return byName
        val byBase = dict.icon(item.baseType)
        if (byBase.isNotBlank()) return byBase
        if (byName.isNotBlank()) return byName
        val byClass = dict.classIcon(item.itemClass)
        if (byClass.isNotBlank()) return byClass
        return dict.classIcon(item.weapon.name.lowercase().replaceFirstChar { it.uppercase() })
    }
}
