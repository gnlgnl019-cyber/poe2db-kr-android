package com.sunuhwi.poe2dbkr

import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

/** 1 디바인에 몇 엑잘·카오스인지. 시세를 못 받으면 0으로 두고 디바인만 적는다. */
data class CurrencyRates(
    val exaltedPerDivine: Double = 0.0,
    val chaosPerDivine: Double = 0.0,
) {
    val usable: Boolean get() = chaosPerDivine > 0.0 || exaltedPerDivine > 0.0

    companion object {
        val NONE = CurrencyRates()
    }
}

/** poe.ninja에서 받아 둔 고유 아이템 시세 한 줄 */
data class UniqueQuote(
    val name: String,
    val baseType: String,
    val divine: Double,
    val listings: Int,
    val corrupted: Boolean = false,
)

/** 장비 한 부위의 값 — 어디서 나온 숫자인지 함께 들고 다닌다 */
data class GearSlotValue(
    /** PoB 슬롯 이름 (Weapon 1, Helmet …) */
    val slotKey: String,
    /** 화면에 쓸 이름 (무기 1, 투구 …) */
    val slotLabel: String,
    val itemName: String,
    val rarity: String,
    val divine: Double,
    /** 가격 근거: "poe.ninja 실거래가" / "옵션 추정가" */
    val basis: String,
    val note: String = "",
) {
    val line: String
        get() = buildString {
            append(slotLabel)
            if (itemName.isNotBlank()) append(" · ").append(itemName)
            append(" — ").append(GearPricing.divine(divine)).append(" 디바인")
            append(" (").append(basis).append(")")
            if (note.isNotBlank()) append(" · ").append(note)
        }
}

/** 빌드 장비 가치 합계 */
data class GearValue(
    val slots: List<GearSlotValue>,
    val rates: CurrencyRates,
    /** 환율을 못 받았거나 고유 시세가 비었을 때의 안내 */
    val notes: List<String> = emptyList(),
) {
    val divine: Double get() = slots.sumOf { it.divine }
    val chaos: Double get() = divine * rates.chaosPerDivine
    val exalted: Double get() = divine * rates.exaltedPerDivine
    val hasItems: Boolean get() = slots.isNotEmpty()

    /** "약 12.3 디바인" */
    val divineText: String get() = GearPricing.divine(divine)

    /** "3,690" — 환율을 못 받으면 빈 문자열 */
    val chaosText: String get() = if (rates.chaosPerDivine > 0.0) GearPricing.cardinal(chaos) else ""

    /** 펼쳤을 때 보여줄 부위별 내역 */
    val detail: String
        get() = buildString {
            slots.sortedWith(compareByDescending<GearSlotValue> { it.divine }.thenBy { it.slotLabel })
                .forEach { append(it.line).append('\n') }
            append("\n고유: poe.ninja 실거래가 · 희귀·마법: 옵션 티어 추정가")
            append(" (스킬 젬 레벨·생명력·저항·정신력·이동 속도·공격 속도·치명타·pDPS 가중)")
            if (rates.usable) {
                append("\n환율 1 디바인 = ")
                append(GearPricing.cardinal(rates.exaltedPerDivine)).append(" 엑잘 / ")
                append(GearPricing.cardinal(rates.chaosPerDivine)).append(" 카오스")
            } else {
                append("\n환율을 받지 못해 디바인 값만 적었습니다")
            }
            notes.forEach { append('\n').append(it) }
        }
}

/**
 * 빌드 장비 가치 산출.
 *
 * 주얼·젬·플라스크·호신부는 값에 넣지 않고, 실제로 몸에 걸친 장비만 센다.
 * 고유 아이템은 poe.ninja 거래가를 그대로 쓰고, 희귀·마법 장비는 옵션의 종류와
 * 수치에 가중치를 매겨 추정한다. 두 근거는 화면에서 항상 나눠 적는다.
 */
object GearPricing {

    /** 값을 매기는 부위 — PoB 슬롯 이름과 화면 이름 */
    val SLOTS: List<Pair<String, String>> = listOf(
        "Weapon 1" to "무기 1",
        "Weapon 2" to "무기 2",
        "Helmet" to "투구",
        "Body Armour" to "갑옷",
        "Gloves" to "장갑",
        "Boots" to "장화",
        "Amulet" to "목걸이",
        "Ring 1" to "반지 1",
        "Ring 2" to "반지 2",
        "Belt" to "허리띠",
    )

    /** 값에 넣지 않는 슬롯 접두사 (주얼·플라스크·호신부) */
    val EXCLUDED_PREFIXES = listOf("Jewel", "Flask", "Charm")

    /** 이 슬롯을 값을 매기는지 */
    fun isPricedSlot(slot: String): Boolean {
        if (SLOTS.none { it.first == slot }) return false
        return EXCLUDED_PREFIXES.none { slot.startsWith(it, ignoreCase = true) }
    }

    fun slotLabel(slot: String): String = SLOTS.firstOrNull { it.first == slot }?.second ?: slot

    /**
     * 옵션 한 줄의 값 (디바인 기준).
     * 같은 옵션이 여러 줄에 나뉘어 있어도 각각 더하되, 옵션별 상한을 넘지 않게 한다.
     */
    private class Weight(val label: String, val regex: Regex, val per: Double, val cap: Double)

    private val WEIGHTS = listOf(
        // 모든 스킬 젬 레벨은 장비 하나의 값를 통째로 바꾼다.
        Weight("모든 스킬 젬 레벨", Regex("level of all .*skill gems|모든 .*스킬 젬 레벨", RegexOption.IGNORE_CASE), 5.0, 25.0),
        Weight("최대 생명력", Regex("to maximum life|최대 생명력", RegexOption.IGNORE_CASE), 0.012, 4.0),
        Weight("최대 에너지 보호막", Regex("to maximum energy shield|최대 에너지 보호막", RegexOption.IGNORE_CASE), 0.02, 3.5),
        Weight("정신력", Regex("to spirit|정신력", RegexOption.IGNORE_CASE), 0.06, 4.0),
        Weight("카오스 저항", Regex("chaos resistance|카오스 저항", RegexOption.IGNORE_CASE), 0.05, 2.0),
        Weight("원소 저항", Regex("(fire|cold|lightning) resistance|(화염|냉기|번개) 저항", RegexOption.IGNORE_CASE), 0.018, 3.0),
        Weight("이동 속도", Regex("movement speed|이동 속도", RegexOption.IGNORE_CASE), 0.05, 3.0),
        Weight("공격 속도", Regex("attack speed|공격 속도", RegexOption.IGNORE_CASE), 0.12, 2.0),
        Weight("시전 속도", Regex("cast speed|시전 속도", RegexOption.IGNORE_CASE), 0.08, 1.5),
        Weight("치명타 확률", Regex("critical hit chance|치명타 확률", RegexOption.IGNORE_CASE), 0.06, 2.0),
        Weight("치명타 피해", Regex("critical damage bonus|치명타 피해", RegexOption.IGNORE_CASE), 0.03, 2.0),
        Weight("물리 피해", Regex("physical damage|물리 피해", RegexOption.IGNORE_CASE), 0.008, 2.5),
        Weight("원소 피해", Regex("(fire|cold|lightning) damage|(화염|냉기|번개) 피해", RegexOption.IGNORE_CASE), 0.006, 2.0),
        Weight("방어도", Regex("increased armour|방어도", RegexOption.IGNORE_CASE), 0.006, 1.2),
        Weight("회피", Regex("increased evasion|회피", RegexOption.IGNORE_CASE), 0.006, 1.2),
        Weight("생명력 재생", Regex("life regeneration|생명력 재생", RegexOption.IGNORE_CASE), 0.02, 1.0),
        Weight("능력치", Regex("to (strength|dexterity|intelligence)|(힘|민첩|지능)", RegexOption.IGNORE_CASE), 0.006, 1.5),
        Weight("마나", Regex("to maximum mana|최대 마나", RegexOption.IGNORE_CASE), 0.004, 1.0),
        Weight("아이템 희귀도", Regex("rarity of items|아이템 희귀도", RegexOption.IGNORE_CASE), 0.01, 1.0),
    )

    private val NUMBER = Regex("[-+]?\\d+(?:\\.\\d+)?")

    /** 줄에서 가장 큰 수치 (범위 표기 45-90은 큰 쪽) */
    private fun value(line: String): Double {
        // "1,200"처럼 천 단위 쉼표가 섞인 속성 줄도 숫자로 읽는다.
        val numbers = NUMBER.findAll(line.replace(",", ""))
            .mapNotNull { it.value.toDoubleOrNull() }
            .map { abs(it) }
            .toList()
        return numbers.maxOrNull() ?: 0.0
    }

    /**
     * 이 줄이 옵션 값에서 차지하는 몫.
     * 유사 매물을 찾을 때 어떤 옵션을 거래소 필터로 걸지 고르는 데도 쓴다.
     */
    fun lineWeight(line: String): Double {
        var best = 0.0
        for (weight in WEIGHTS) {
            if (!weight.regex.containsMatchIn(line)) continue
            best = max(best, value(line) * weight.per)
        }
        return best
    }

    /** 무기 속성 줄에서 초당 피해 (물리 + 원소) */
    internal fun weaponDps(lines: List<String>): Double {
        var phys = 0.0
        var elemental = 0.0
        var aps = 0.0
        for (line in lines) {
            val lower = line.lowercase()
            val range = Regex("(\\d+(?:\\.\\d+)?)\\s*-\\s*(\\d+(?:\\.\\d+)?)")
            if (lower.contains("physical damage") || lower.contains("물리 피해")) {
                range.find(line)?.let { m ->
                    phys = max(phys, (m.groupValues[1].toDouble() + m.groupValues[2].toDouble()) / 2.0)
                }
            } else if (lower.contains("elemental damage") || lower.contains("원소 피해")) {
                range.find(line)?.let { m ->
                    elemental = max(elemental, (m.groupValues[1].toDouble() + m.groupValues[2].toDouble()) / 2.0)
                }
            } else if (lower.contains("attacks per second") || lower.contains("초당 공격 횟수")) {
                NUMBER.find(line)?.value?.toDoubleOrNull()?.let { aps = max(aps, it) }
            }
        }
        val speed = if (aps > 0.0) aps else 1.0
        return (phys + elemental * 0.9) * speed
    }

    /** 장비 속성 줄에서 방어구 수치 */
    private fun defenceValue(lines: List<String>): Double {
        var out = 0.0
        for (line in lines) {
            val lower = line.lowercase()
            if (!lower.contains("armour") && !lower.contains("evasion") &&
                !lower.contains("energy shield") && !lower.contains("방어도") &&
                !lower.contains("회피") && !lower.contains("에너지 보호막")
            ) {
                continue
            }
            if (lower.contains("increased") || lower.contains("증가")) continue
            out = max(out, value(line))
        }
        return out
    }

    /** 품질(%) */
    private fun quality(lines: List<String>): Double =
        lines.firstOrNull { it.startsWith("Quality", true) || it.startsWith("품질") }
            ?.let { value(it) } ?: 0.0

    /** 희귀·마법 장비의 옵션 추정가 (디바인) */
    fun rarePrice(item: PobItem, slotLabel: String): Double {
        val lines = item.lines
        if (lines.isEmpty()) return 0.02
        var score = 0.0
        val caps = HashMap<String, Double>()
        for (line in lines) {
            for (weight in WEIGHTS) {
                if (!weight.regex.containsMatchIn(line)) continue
                // "물리 피해" 같은 넓은 낱말은 무기 속성 줄(Physical Damage: 45-90)을 피한다.
                if (line.contains(":") && !line.contains("+") && weight.label != "모든 스킬 젬 레벨") {
                    val isRange = Regex("\\d+\\s*-\\s*\\d+").containsMatchIn(line)
                    if (isRange) continue
                }
                val add = value(line) * weight.per
                if (add <= 0.0) continue
                val used = caps[weight.label] ?: 0.0
                val room = (weight.cap - used).coerceAtLeast(0.0)
                val take = minOf(add, room)
                caps[weight.label] = used + take
                score += take
            }
        }
        val q = quality(lines)
        score += q * 0.012
        if (slotLabel.startsWith("무기")) {
            val dps = weaponDps(lines)
            score += (dps - 60.0).coerceAtLeast(0.0) * 0.004
            score *= 1.15
        } else {
            score += defenceValue(lines) * 0.0005
        }
        // 옵션이 거의 없으면 창고 쓰레기 값으로 둔다.
        return round2((0.05 + score).coerceIn(0.02, 400.0))
    }

    /**
     * 한 부위의 값.
     *
     * 고유는 poe.ninja 실거래가를, 희귀·마법은 공식 거래소 유사 매물 중위값을
     * 먼저 쓰고, 거래소를 못 보면 옵션 추정가로 내려간다.
     */
    fun price(item: PobItem, slot: String, quote: UniqueQuote?, trade: TradePrice? = null): GearSlotValue {
        val label = slotLabel(slot)
        val unique = item.rarity.equals("Unique", true)
        if (unique) {
            if (quote != null && quote.divine > 0.0) {
                val note = if (quote.corrupted && !item.lines.any { it.equals("Corrupted", true) }) {
                    "타락 매물 기준"
                } else if (quote.listings > 0) {
                    "매물 ${quote.listings}건"
                } else {
                    ""
                }
                return GearSlotValue(
                    slotKey = slot,
                    slotLabel = label,
                    itemName = item.displayName,
                    rarity = "고유",
                    divine = round2(quote.divine),
                    basis = "poe.ninja 실거래가",
                    note = note,
                )
            }
            return GearSlotValue(
                slotKey = slot,
                slotLabel = label,
                itemName = item.displayName,
                rarity = "고유",
                divine = rarePrice(item, label),
                basis = "옵션 추정가",
                note = "poe.ninja에 이 고유 아이템 시세가 없음",
            )
        }
        val rarity = when (item.rarity.lowercase()) {
            "magic" -> "마법"
            "normal" -> "일반"
            else -> "희귀"
        }
        if (trade != null && trade.divine > 0.0) {
            return GearSlotValue(
                slotKey = slot,
                slotLabel = label,
                itemName = item.displayName,
                rarity = rarity,
                divine = trade.divine,
                basis = "공식 거래소 유사 매물",
                note = trade.note,
            )
        }
        return GearSlotValue(
            slotKey = slot,
            slotLabel = label,
            itemName = item.displayName,
            rarity = rarity,
            divine = rarePrice(item, label),
            basis = "옵션 추정가",
        )
    }

    /**
     * 빌드 하나의 장비 가치.
     *
     * overrides는 화면에서 바꿔 낀 아이템(슬롯 이름 → 아이템)이다.
     * quotes는 poe.ninja 고유 시세(소문자 이름 → 시세).
     */
    fun value(
        build: PobBuild,
        rates: CurrencyRates = CurrencyRates.NONE,
        quotes: Map<String, UniqueQuote> = emptyMap(),
        overrides: Map<String, PobItem> = emptyMap(),
        /** 슬롯 이름 → 공식 거래소 유사 매물 중위값 */
        trades: Map<String, TradePrice> = emptyMap(),
        notes: List<String> = emptyList(),
    ): GearValue {
        val slots = ArrayList<GearSlotValue>()
        for ((key, _) in SLOTS) {
            if (!isPricedSlot(key)) continue
            val item = overrides[key] ?: Pob.itemForSlot(build, key) ?: continue
            if (item.displayName.isBlank()) continue
            val lowered = item.name.lowercase()
            val quote = quotes[lowered] ?: quotes[lowered + "|" + item.baseType.lowercase()]
            slots.add(price(item, key, quote, trades[key]))
        }
        val all = ArrayList<String>(notes)
        if (!rates.usable) all.add("1 디바인 환율을 받지 못했습니다 (poe.ninja 화폐 시세)")
        if (quotes.isEmpty() && slots.any { it.rarity == "고유" }) {
            all.add("고유 아이템 시세를 받지 못해 옵션으로 추정했습니다")
        }
        return GearValue(slots, rates, all)
    }

    /** 거래소 조회 대상이 될 만한 희귀 장비 (값어치가 큰 순서) */
    fun tradeCandidates(
        build: PobBuild,
        overrides: Map<String, PobItem> = emptyMap(),
    ): List<Pair<String, PobItem>> =
        SLOTS.mapNotNull { (key, _) ->
            if (!isPricedSlot(key)) return@mapNotNull null
            val item = overrides[key] ?: Pob.itemForSlot(build, key) ?: return@mapNotNull null
            if (item.displayName.isBlank()) return@mapNotNull null
            if (item.rarity.equals("Unique", true)) return@mapNotNull null
            key to item
        }.sortedByDescending { (key, item) -> rarePrice(item, slotLabel(key)) }

    /** 소수점 둘째 자리까지 */
    fun round2(value: Double): Double = if (!value.isFinite()) 0.0 else Math.round(value * 100.0) / 100.0

    /** 디바인 값 표기 — 1 미만은 소수 셋째 자리까지 */
    fun divine(value: Double): String {
        if (!value.isFinite()) return "—"
        return when {
            value >= 100 -> String.format(Locale.KOREA, "%,.0f", value)
            value >= 10 -> String.format(Locale.KOREA, "%.1f", value)
            value >= 1 -> String.format(Locale.KOREA, "%.2f", value)
            else -> String.format(Locale.KOREA, "%.3f", value)
        }
    }

    /** 3,690 처럼 천 단위로 끊어 쓴다 */
    fun cardinal(value: Double): String {
        if (!value.isFinite()) return "—"
        return String.format(Locale.KOREA, "%,.0f", max(0.0, value))
    }
}
