package com.sunuhwi.poe2dbkr

/**
 * 정신력을 점유하는 젬(정신력 젬) 이름 목록 — PoE2 0.5.5 기준 130종.
 *
 * poe2db의 Spirit Gems 목록에는 아직 옛 판의 젬만 실려 있어(앱이 받아 둔 자료 53종),
 * 단련(Discipline)·원소 착취처럼 목록에 없는 정신력 젬이 스킬 칸에 섞이는 일이 있었다.
 * 그래서 점유 젬 전체를 이름으로 알아볼 수 있게 여기에 모아 둔다.
 * (자료: exiledb.com/gems/spirit — 게임 파일에서 읽은 0.5.5 목록)
 *
 * 쓰는 곳:
 * - 전투 탭 '스킬 선택' 칸에서 정신력 젬을 뺀다 (직접 쓰는 스킬이 아니므로)
 * - 콤보 후보, 스킬별 DPS 추정에서도 뺀다
 * - 정신력 젬으로 잡히면 점유량·효과가 없더라도 '정신력 버프'로 남긴다
 */
object SpiritGems {

    /** 이름 비교용 열쇠 — 소문자, 영문·숫자만 남긴다 */
    fun key(name: String): String =
        name.trim().lowercase().filter { it.isLetterOrDigit() }

    /** 지속형(정신력 점유) 스킬 젬 이름 */
    private val SKILLS = listOf(
        "Alchemist's Boon", "Align Fate", "Archmage", "Arctic Armour", "Attrition",
        "Azmerian Swarms", "Azmerian Wolf", "Barkskin", "Berserk", "Bind Spectre",
        "Black Powder Blitz", "Blink", "Blood Boil", "Briarpatch", "Cackling Companions",
        "Charge Regulation", "Combat Frenzy", "Convalescence", "Crackling Palm",
        "Defiance Banner", "Discipline", "Dread Banner", "Elemental Conflux", "Eternal Rage",
        "Explosive Transmutation", "Fulmination", "Future-Past", "Gelid Palm", "Ghost Dance",
        "Grim Feast", "Harbinger of Madness", "Heart of Ice", "Herald of Ash", "Herald of Blood",
        "Herald of Ice", "Herald of Plague", "Herald of the Royal Queen", "Herald of Thunder",
        "Hollow Focus", "Impurity", "Into the Breach", "Iron Ward", "Kelari, the Tainted Sands",
        "Kelari's Malediction", "Leylines", "Life Remnants", "Lingering Illusion", "Magma Barrier",
        "Malice", "Mana Remnants", "Manifest Weapon", "Midnight Zenith", "Mirror of Refraction",
        "Mist Raven", "Navira, the Last Mirage", "Overwhelming Presence", "Plague Bearer",
        "Purity of Fire", "Purity of Ice", "Purity of Lightning", "Queen's Procession",
        "Raging Spirits", "Ravenous Swarm", "Remnants of Kalguur", "Rhoa Mount",
        "Ruzhan, the Blazing Sword", "Sacrifice", "Sanguine Revelry", "Savage Fury",
        "Scavenged Plating", "Shard Scavenger", "Shroud", "Siphon Elements", "Skeletal Arsonist",
        "Skeletal Brute", "Skeletal Cleric", "Skeletal Frost Mage", "Skeletal Reaver",
        "Skeletal Sniper", "Skeletal Storm Mage", "Skeletal Warrior", "Sorcery Ward",
        "Spiraling Conspiracy", "Summon Infernal Hound", "Supporting Fire", "Tame Beast",
        "Temporal Rift", "Time of Need", "Trail of Caltrops", "Trinity",
        "Verisium Manifestations", "Virtuous Barrier", "Void Illusion", "War Banner",
        "Wild Protector", "Wildwood's Gifts", "Wind Dancer", "Withering Presence", "Wolf Pack",
    )

    /** 메타 젬(발동 장치) — 정신력을 점유하지만 '스킬을 쓴느' 젬이 아니다 */
    private val META = listOf(
        "Barrier Invocation", "Blasphemy", "Called Shots", "Cast on Block", "Cast on Charm Use",
        "Cast on Critical", "Cast on Death", "Cast on Dodge", "Cast on Elemental Ailment",
        "Cast on Freeze", "Cast on Ignite", "Cast on Melee Kill", "Cast on Melee Stun",
        "Cast on Minion Death", "Cast on Shock", "Cast when Damage Taken", "Cast when Stunned",
        "Cast while Channelling", "Curse on Block", "Demon Magus", "Elemental Invocation",
        "Feral Invocation", "Fire Spell on Hit", "Hydra Familiar", "Mirage Archer",
        "Mirage Deadeye", "Reaper's Invocation", "Spellslinger", "Spirit Vessel",
        "Summon Companion", "Thundergod's Wrath",
    )

    private val SKILL_KEYS: Set<String> = SKILLS.mapTo(HashSet()) { key(it) }
    private val META_KEYS: Set<String> = META.mapTo(HashSet()) { key(it) }

    /** 정신력을 점유하는 지속형 스킬인지 */
    fun isSpirit(name: String): Boolean {
        val k = key(name)
        return k.isNotEmpty() && (k in SKILL_KEYS || k in META_KEYS)
    }

    /** 그중 발동 장치(메타)인지 */
    fun isMeta(name: String): Boolean {
        val k = key(name)
        return k.isNotEmpty() && k in META_KEYS
    }

    /** 정신력 젬이지만 poe2db 목록에 없는 이름일 때 화면에 붙일 설명 */
    const val NO_DATA_NOTE = "자료에 점유량이 없어 효과만 반영"

    val count: Int get() = SKILL_KEYS.size + META_KEYS.size
}
