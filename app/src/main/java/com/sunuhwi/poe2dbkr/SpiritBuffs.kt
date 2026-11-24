package com.sunuhwi.poe2dbkr

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.max
import kotlin.math.min

/**
 * 정신력을 점유하는 젬(정신력 젬) 하나.
 *
 * 점유량과 효과 문장은 poe2db의 Spirit Gems 페이지에서 그대로 받아 둔 자료
 * (assets/poe2_spirit_kr.json)를 읽는다. 효과 수치는 [SpiritBuffs.modsOf]가
 * 그 젬의 문장을 보고 정리한 값이다.
 */
data class SpiritGem(
    val en: String,
    val ko: String,
    /** 점유 정신력 (20레벨 기준) */
    val spirit: Double,
    val tags: List<String> = emptyList(),
    /** 젬 설명 (한 문단) */
    val text: String = "",
    /** 효과 문장 — 화면의 툴팁에 그대로 쓴다 */
    val lines: List<String> = emptyList(),
) {
    val displayName: String get() = ko.ifBlank { en }

    /** 젬이 다루는 원소·계열 (오라 색을 고를 때 쓴다) */
    val family: String
        get() = when {
            tags.any { it == "냉기" } || en.contains("Ice") || en.contains("Arctic") -> "냉기"
            tags.any { it == "번개" } || en.contains("Lightning") || en.contains("Thunder") -> "번개"
            tags.any { it == "화염" } || en.contains("Fire") || en.contains("Ash") || en.contains("Magma") -> "화염"
            tags.any { it == "카오스" } || en.contains("Plague") || en.contains("Blight") -> "카오스"
            tags.any { it == "물리" } || en.contains("Blood") || en.contains("Iron") -> "물리"
            else -> ""
        }
}

/** 정신력 젬이 전투에서 하는 일 — 시뮬레이터가 이 값으로 연출과 타격을 고른다 */
enum class SpiritBehavior(val label: String) {
    /** 수치만 올리는 지속 버프 */
    BUFF("지속 버프"),

    /** 피격당하면 단계를 터뜨려 주변을 밀어내는 폭발 */
    NOVA_ON_HIT("피격 시 폭발"),

    /** 피격당하면 방어도·보호막을 되찾는다 */
    WARD_ON_HIT("피격 시 방어"),

    /** 막아 낸 물리 피해를 모아 두었다가 터뜨린다 */
    STORED_PHYS("물리 피해 저장"),

    /** 중독 피해를 모아 두었다가 터뜨린다 */
    POISON_STORE("중독 피해 저장"),

    /** 가까이 오는 적을 계속 찌른다 */
    THORNS("가시 반격"),

    /** 회피 구르기 때 마름쇠를 뿌린다 */
    CALTROPS("회피 시 마름쇠"),

    /** 공격할수록 격노가 쌓여 강해진다 */
    RAGE("격노 중첩"),

    /** 적을 처치할 때마다 폭발한다 */
    ONKILL_BLAST("처치 시 폭발"),

    /** 원소 피해가 주기적으로 크게 오른다 */
    ELEMENTAL_RAMP("원소 증폭"),

    /** 오래 붙어 있는 희귀·고유 적에게 강해진다 */
    BOSS_RAMP("접근 시간 증폭"),

    /** 서 있으면 받는 피해가 줄어든다 */
    GUARD("정지 시 방어"),

    /** 소환수를 부린다 */
    MINION("소환수"),

    /** 발동 에너지를 모아 연결된 주문을 터뜨린다 */
    INVOCATION("발동 에너지"),

    /** 그 밖의 보조 효과 */
    UTILITY("보조"),
}

/** 정신력 젬이 올려 주는 수치 (20레벨 기준, poe2db 문장의 가운데 값) */
data class SpiritMods(
    val evasionPct: Double = 0.0,
    val armourPct: Double = 0.0,
    val esPct: Double = 0.0,
    val esFlat: Double = 0.0,
    val lifePct: Double = 0.0,
    val lifeFlat: Double = 0.0,
    val damagePct: Double = 0.0,
    val attackSpeedPct: Double = 0.0,
    val castSpeedPct: Double = 0.0,
    val critChancePct: Double = 0.0,
    val accuracyPct: Double = 0.0,
    val resPct: Double = 0.0,
    val maxResPct: Double = 0.0,
    val physRedPct: Double = 0.0,
    /** 1초마다 회복하는 생명력 비율(%) */
    val regenPct: Double = 0.0,
    /** 1초마다 회복하는 에너지 보호막 비율(%) */
    val esRegenPct: Double = 0.0,
    val damageTakenLessPct: Double = 0.0,
    val moveSpeedPct: Double = 0.0,
    val blockPct: Double = 0.0,
    val ailmentMagnitudePct: Double = 0.0,
    val stunThresholdPct: Double = 0.0,
    val spellDamagePct: Double = 0.0,
    val elementalDamagePct: Double = 0.0,
) {
    val isEmpty: Boolean
        get() = this == EMPTY

    operator fun plus(other: SpiritMods): SpiritMods = SpiritMods(
        evasionPct = evasionPct + other.evasionPct,
        armourPct = armourPct + other.armourPct,
        esPct = esPct + other.esPct,
        esFlat = esFlat + other.esFlat,
        lifePct = lifePct + other.lifePct,
        lifeFlat = lifeFlat + other.lifeFlat,
        damagePct = damagePct + other.damagePct,
        attackSpeedPct = attackSpeedPct + other.attackSpeedPct,
        castSpeedPct = castSpeedPct + other.castSpeedPct,
        critChancePct = critChancePct + other.critChancePct,
        accuracyPct = accuracyPct + other.accuracyPct,
        resPct = resPct + other.resPct,
        maxResPct = maxResPct + other.maxResPct,
        physRedPct = physRedPct + other.physRedPct,
        regenPct = regenPct + other.regenPct,
        esRegenPct = esRegenPct + other.esRegenPct,
        damageTakenLessPct = damageTakenLessPct + other.damageTakenLessPct,
        moveSpeedPct = moveSpeedPct + other.moveSpeedPct,
        blockPct = blockPct + other.blockPct,
        ailmentMagnitudePct = ailmentMagnitudePct + other.ailmentMagnitudePct,
        stunThresholdPct = stunThresholdPct + other.stunThresholdPct,
        spellDamagePct = spellDamagePct + other.spellDamagePct,
        elementalDamagePct = elementalDamagePct + other.elementalDamagePct,
    )

    companion object {
        val EMPTY = SpiritMods()
    }
}

/** 빌드에서 찾은 정신력 젬 하나의 해석 결과 */
data class SpiritSpec(
    val gem: SpiritGem,
    /** PoB에 적힌 젬 이름 */
    val pobName: String = gem.en,
    val level: Int = 20,
    val supports: List<String> = emptyList(),
    /** 점유 정신력 */
    val spirit: Double = gem.spirit,
    /** 정신력이 남아 켜져 있는지 */
    val active: Boolean = true,
    /** 장비·패시브 트리가 준 스킬인지 (직접 소켓하지 않은 것) */
    val granted: Boolean = false,
    val mods: SpiritMods = SpiritMods.EMPTY,
    val behavior: SpiritBehavior = SpiritBehavior.BUFF,
    /** 화면에 함께 적을 한 줄 설명 */
    val note: String = "",
) {
    val name: String get() = gem.displayName

    /** HUD에 붙일 이름 (꺼져 있으면 표시를 단다) */
    val hudName: String get() = if (active) name else "$name(꺼짐)"

    val supportText: String get() = if (supports.isEmpty()) "" else supports.joinToString(", ")

    /** 툴팁에 올릴 효과 문장 — 점유량과 수치를 한글로 */
    fun tooltip(): List<String> {
        val out = ArrayList<String>()
        out.add("정신력 ${spirit.toInt()} 점유")
        if (note.isNotBlank()) out.add(note)
        gem.lines.take(6).forEach { out.add(it) }
        return out
    }
}

/** 정신력 예산과 켜진 버프 목록 */
data class SpiritBuffPlan(
    val specs: List<SpiritSpec> = emptyList(),
    /** 빌드 전체 정신력 */
    val spirit: Double = 0.0,
    /** 소환수가 먼저 가져간 정신력 */
    val spiritForMinions: Double = 0.0,
    /** 이 버프들이 점유한 정신력 (켜진 것만) */
    val reserved: Double = 0.0,
    /** 전령이 가져간 정신력 */
    val heraldSpirit: Double = 0.0,
    /** 남은 정신력 */
    val free: Double = 0.0,
    /** 빌드에 있으나 정신력이 모자라 꺼진 것 */
    val inactive: List<SpiritSpec> = emptyList(),
) {
    val active: List<SpiritSpec> get() = specs.filter { it.active }
    val isEmpty: Boolean get() = specs.isEmpty()

    val mods: SpiritMods
        get() {
            var sum = SpiritMods.EMPTY
            active.forEach { sum += it.mods }
            return sum
        }

    fun has(en: String): Boolean = active.any { it.gem.en.equals(en, ignoreCase = true) }

    fun spec(en: String): SpiritSpec? = active.firstOrNull { it.gem.en.equals(en, ignoreCase = true) }

    /** 화면·결과 글 한 줄 (예: "바람의 무희 · 혼의 춤 · 정신력 90/130 · 혼의 춤(꺼짐)") */
    val summary: String
        get() = buildList {
            if (active.isNotEmpty()) add(active.joinToString(" · ") { it.name })
            if (spirit > 0.0) {
                add("정신력 ${(reserved + spiritForMinions).toInt()}/${spirit.toInt()}")
            }
            // 정신력이 모자라 꺼진 젬은 이유와 함께 남긴다.
            if (inactive.isNotEmpty()) add(inactive.joinToString(" · ") { "${it.name}(꺼짐)" })
        }.joinToString(" · ")

    companion object {
        val EMPTY = SpiritBuffPlan()
    }
}

/** assets의 정신력 젬 목록을 한 번만 읽어 둔다 */
class SpiritCatalog(val gems: List<SpiritGem> = emptyList()) {

    private val byKey: Map<String, SpiritGem> by lazy {
        val out = HashMap<String, SpiritGem>()
        gems.forEach { gem ->
            key(gem.en)?.let { out[it] = gem }
            key(gem.ko)?.let { out[it] = gem }
        }
        out
    }

    val isEmpty: Boolean get() = gems.isEmpty()

    /** PoB 젬 이름·한글 이름으로 찾는다 (보조 접미사·로마숫자는 무시) */
    fun find(name: String): SpiritGem? = key(name)?.let { byKey[it] }

    companion object {
        val EMPTY = SpiritCatalog()

        /** 이름 비교용 열쇠 — 소문자, 영문·숫자만 남긴다 */
        fun key(name: String): String? {
            val clean = name.trim().lowercase()
                .replace("’", "'")
                .replace("'", "")
                .filter { it.isLetterOrDigit() || it in "가-힣" }
            return clean.ifEmpty { null }
        }

        fun parse(raw: String): SpiritCatalog {
            val root = JSONArray(raw)
            val out = ArrayList<SpiritGem>(root.length())
            for (i in 0 until root.length()) {
                val row = root.optJSONObject(i) ?: continue
                val en = row.optString("en")
                val ko = row.optString("ko")
                if (en.isBlank() && ko.isBlank()) continue
                val tags = ArrayList<String>()
                row.optJSONArray("tags")?.let { arr ->
                    for (j in 0 until arr.length()) {
                        val tag = arr.optString(j)
                        if (tag.isNotBlank()) tags.add(tag)
                    }
                }
                val lines = ArrayList<String>()
                row.optJSONArray("lines")?.let { arr ->
                    for (j in 0 until arr.length()) {
                        val line = arr.optString(j)
                        if (visible(line)) lines.add(line)
                    }
                }
                out.add(
                    SpiritGem(
                        en = en,
                        ko = ko.ifBlank { en },
                        spirit = row.optDouble("spirit", 0.0),
                        tags = tags,
                        text = row.optString("text"),
                        lines = lines,
                    )
                )
            }
            return SpiritCatalog(out)
        }

        /**
         * 화면에 올릴 문장인지 — 젬 자료에는 엔진용 열쇠줄
         * (예: "base deal no damage [1]")이 섞여 있어 한글 문장만 남긴다.
         */
        internal fun visible(line: String): Boolean {
            val text = line.trim()
            if (text.isEmpty()) return false
            if (text.any { it in '가'..'힣' }) return true
            return false
        }
    }
}

/** 정신력 점유 버프가 한 전투에서 돈 횟수와 낸 피해 */
data class SpiritReport(
    val name: String,
    val procs: Int,
    val damage: Double,
) {
    val line: String
        get() = "$name — ${procs}회 · ${BattleWorld.formatShort(damage)}"
}

object SpiritBuffs {

    private const val ASSET = "poe2_spirit_kr.json"
    private val cached = AtomicReference<SpiritCatalog?>(null)

    fun catalog(context: Context): SpiritCatalog {
        cached.get()?.let { return it }
        val loaded = runCatching {
            val raw = context.assets.open(ASSET).bufferedReader().use { it.readText() }
            SpiritCatalog.parse(raw)
        }.getOrElse { SpiritCatalog.EMPTY }
        cached.compareAndSet(null, loaded)
        return cached.get() ?: loaded
    }

    /** 정신력 젬의 수치 — 문장에 적힌 20레벨 값(범위는 가운데)을 정리한 표 */
    fun modsOf(gem: SpiritGem): SpiritMods = when (gem.en) {
        // 회피 10% 증폭 ×3단계
        "Wind Dancer" -> SpiritMods(evasionPct = 30.0)
        // 방어도·회피 30% 증폭 오라
        "Defiance Banner" -> SpiritMods(armourPct = 30.0, evasionPct = 30.0)
        // 모든 원소 저항 최대치 +4%, 상태 이상 한계치 60% 증폭
        "Dread Banner" -> SpiritMods(maxResPct = 4.0, stunThresholdPct = 60.0)
        // 공격 피해 25% 증폭, 공격 속도 25% 증가, 정확도 50% 증가
        "War Banner" -> SpiritMods(damagePct = 25.0, attackSpeedPct = 25.0, accuracyPct = 50.0)
        // 판금 하나당 방어도 4% 증폭 ×10중첩 (+ 가시)
        "Scavenged Plating" -> SpiritMods(armourPct = 40.0)
        // 단계마다 공격 속도·이동 속도가 오르는 격노
        "Berserk" -> SpiritMods(damagePct = 20.0, attackSpeedPct = 8.0, moveSpeedPct = 8.0)
        // 버프 동안 공격 피해 24% 증폭
        "Savage Fury" -> SpiritMods(damagePct = 24.0, attackSpeedPct = 6.0)
        // 원소 피해 49% 증폭
        "Elemental Conflux" -> SpiritMods(elementalDamagePct = 49.0)
        // 친화력당 원소 피해 증폭 (최대 30 친화력)
        "Trinity" -> SpiritMods(elementalDamagePct = 12.0)
        // 희귀·고유 적에게 최대 49% 증폭, 마무리 타격
        "Attrition" -> SpiritMods(damagePct = 9.0)
        // 보호막 80% 증가, 생명력 100, 지능·정신력 40% 증가
        "Sacrifice" -> SpiritMods(esPct = 80.0, lifeFlat = 100.0, spellDamagePct = 12.0)
        // 최대 마나 100당 4% 추가 번개 피해
        "Archmage" -> SpiritMods(spellDamagePct = 18.0)
        // 최대 생명력 +50, 기절 한계치 +70, 이동 속도 15% 증가
        "Briarpatch" -> SpiritMods(lifeFlat = 50.0, stunThresholdPct = 70.0, moveSpeedPct = 15.0)
        // 1초마다 생명력을 회복시키는 축복
        "Time of Need" -> SpiritMods(regenPct = 3.0)
        // 짧게 버프 효과를 발휘하는 회복
        "Convalescence" -> SpiritMods(regenPct = 2.0)
        // 정지 중 물리·화염 피해를 덜 받고 동결 면역
        "Arctic Armour" -> SpiritMods(physRedPct = 6.0, damageTakenLessPct = 4.0)
        // 피격 시 상실한 보호막만큼 방어도
        "Barkskin" -> SpiritMods(armourPct = 10.0)
        // 명중 시 보호막을 되찾는다
        "Ghost Dance" -> SpiritMods(esRegenPct = 2.0)
        // 적의 상태 이상·기절 한계치를 깎는다
        "Overwhelming Presence" -> SpiritMods(ailmentMagnitudePct = 4.0)
        // 위축으로 카오스 피해를 더 받게 한다
        "Withering Presence" -> SpiritMods(damagePct = 6.0)
        // 사거리·정확도 보조 (전투 수치에는 영향 없음)
        "Mirage Archer" -> SpiritMods(damagePct = 4.0)
        else -> SpiritMods.EMPTY
    }

    /** 정신력 젬이 전투에서 하는 일 */
    fun behaviorOf(gem: SpiritGem): SpiritBehavior = when (gem.en) {
        "Wind Dancer" -> SpiritBehavior.NOVA_ON_HIT
        "Ghost Dance", "Barkskin" -> SpiritBehavior.WARD_ON_HIT
        "Iron Ward" -> SpiritBehavior.STORED_PHYS
        "Plague Bearer" -> SpiritBehavior.POISON_STORE
        "Briarpatch", "Magma Barrier", "Scavenged Plating" -> SpiritBehavior.THORNS
        "Trail of Caltrops" -> SpiritBehavior.CALTROPS
        "Savage Fury", "Berserk", "Eternal Rage" -> SpiritBehavior.RAGE
        "Herald of Plague", "Herald of Blood" -> SpiritBehavior.ONKILL_BLAST
        "Elemental Conflux", "Trinity" -> SpiritBehavior.ELEMENTAL_RAMP
        "Attrition" -> SpiritBehavior.BOSS_RAMP
        "Arctic Armour", "Convalescence", "Time of Need" -> SpiritBehavior.GUARD
        "Grim Feast", "Ravenous Swarm", "Wolf Pack", "Raging Spirits", "Rhoa Mount" -> SpiritBehavior.MINION
        "Barrier Invocation", "Elemental Invocation", "Reaper's Invocation", "Feral Invocation",
        "Cast on Critical", "Cast on Dodge", "Cast on Elemental Ailment", "Cast on Minion Death",
        -> SpiritBehavior.INVOCATION
        "Blasphemy", "Blink", "Mana Remnants", "Siphon Elements", "Alchemist's Boon",
        "Shard Scavenger", "Lingering Illusion", "Combat Frenzy", "Charge Regulation",
        "Herald of Ice", "Herald of Thunder", "Herald of Ash",
        -> SpiritBehavior.UTILITY
        else -> SpiritBehavior.BUFF
    }

    /** 화면에 함께 적을 설명 — 수치만으로는 보이지 않는 부분을 짚어 준다 */
    fun noteOf(gem: SpiritGem): String = when (gem.en) {
        "Combat Frenzy", "Charge Regulation" ->
            "충전 운용으로 반영 (빌드의 충전 규칙에서 계산)"
        "Blasphemy" -> "장착한 저주를 오라로 바꾼다 — 저주 하나당 정신력 60"
        "Grim Feast" -> "소환수가 쓰러질 때 남기는 잔류물로 생명력을 회복한다"
        "Ravenous Swarm" -> "플레이어 주위를 도는 무리가 6초마다 새로 생긴다"
        "Wolf Pack", "Rhoa Mount", "Raging Spirits", "Mirage Archer" ->
            "소환수를 부리는 젬 — 소환수 구성에 함께 적는다"
        "Barrier Invocation", "Elemental Invocation", "Reaper's Invocation", "Feral Invocation",
        "Cast on Critical", "Cast on Dodge", "Cast on Elemental Ailment", "Cast on Minion Death",
        -> "발동 에너지를 모아 연결된 주문을 대신 시전한다"
        "Herald of Plague" -> "처치한 적의 독을 주변으로 퍼뜨린다"
        "Herald of Blood" -> "혈액상실을 겪은 적이 쓰러지면 폭발한다"
        else -> ""
    }

    /**
     * 빌드에서 정신력 젬을 모아 예산 안에서 켠다.
     *
     * 소환수가 먼저 정신력을 가져가고, 남은 예산을 PoB에 적힌 순서대로 채운다.
     * 예산을 넘긴 젬은 '꺼짐'으로 남겨 화면에서 이유를 볼 수 있게 한다.
     */
    fun plan(
        build: PobBuild?,
        catalog: SpiritCatalog = SpiritCatalog.EMPTY,
        dict: KrDict = KrDict.EMPTY,
        /** 소환수가 먼저 가져간 정신력 */
        spiritForMinions: Double = 0.0,
        /** 전령이 가져간 정신력 (전령은 따로 계산한다) */
        heraldSpirit: Double = 0.0,
    ): SpiritBuffPlan {
        if (build == null || catalog.isEmpty) return SpiritBuffPlan.EMPTY
        val budget = build.stats["Spirit"]?.takeIf { it.isFinite() && it > 0.0 } ?: 0.0
        val found = ArrayList<SpiritSpec>()
        val seen = HashSet<String>()
        for (group in build.skills) {
            if (!group.enabled) continue
            val gems = group.gems.filter { it.enabled }
            for (gem in gems) {
                if (PobCalculator.isSupport(gem)) continue
                val entry = catalog.find(gem.name) ?: continue
                val key = SpiritCatalog.key(entry.en) ?: continue
                if (!seen.add(key)) continue
                if (entry.en.startsWith("Herald of")) continue
                val supports = gems.filter { it !== gem && PobCalculator.isSupport(it) }.map { it.name }
                found.add(
                    SpiritSpec(
                        gem = entry,
                        pobName = gem.name,
                        level = gem.level.coerceIn(1, 40),
                        supports = supports,
                        spirit = entry.spirit,
                        granted = group.grantedByItem || group.grantedByTree,
                        mods = modsOf(entry),
                        behavior = behaviorOf(entry),
                        note = noteOf(entry),
                    )
                )
            }
        }
        if (found.isEmpty()) {
            return SpiritBuffPlan(
                spirit = budget,
                spiritForMinions = spiritForMinions.coerceAtLeast(0.0),
                heraldSpirit = heraldSpirit,
            )
        }
        var used = spiritForMinions.coerceAtLeast(0.0) + heraldSpirit.coerceAtLeast(0.0)
        val specs = found.map { spec ->
            if (budget <= 0.0) return@map spec
            if (spec.spirit <= 0.0) return@map spec
            if (budget - used < spec.spirit) return@map spec.copy(active = false)
            used += spec.spirit
            spec
        }
        val reserved = specs.filter { it.active }.sumOf { it.spirit }
        return SpiritBuffPlan(
            specs = specs,
            spirit = budget,
            spiritForMinions = spiritForMinions.coerceAtLeast(0.0),
            reserved = reserved,
            free = max(0.0, budget - used),
            inactive = specs.filter { !it.active },
        )
    }

    /** 장비가 준, 정신력 젬 목록에는 없는 버프 스킬 (예: 목걸이의 부여 스킬) */
    fun grantedBuffs(
        build: PobBuild?,
        catalog: SpiritCatalog = SpiritCatalog.EMPTY,
        dict: KrDict = KrDict.EMPTY,
    ): List<SpiritSpec> {
        if (build == null) return emptyList()
        val out = ArrayList<SpiritSpec>()
        val seen = HashSet<String>()
        for (group in build.skills) {
            if (!group.enabled || !group.grantedByItem) continue
            for (gem in group.gems) {
                if (!gem.enabled || PobCalculator.isSupport(gem)) continue
                val key = SpiritCatalog.key(gem.name) ?: continue
                if (!seen.add(key)) continue
                // 정신력 젬으로 이미 잡힌 것은 건너뛴다.
                if (catalog.find(gem.name) != null) continue
                if (PobTriggers.kindOf(gem) != null) continue
                if (SkillMotions.detect(gem.name, group.label) != SkillMotion.DEFAULT) continue
                val ko = dict.gemName(gem.name).ifBlank { gem.name }
                out.add(
                    SpiritSpec(
                        gem = SpiritGem(en = gem.name, ko = ko, spirit = 0.0, text = group.slot),
                        pobName = gem.name,
                        level = gem.level,
                        spirit = 0.0,
                        granted = true,
                        behavior = SpiritBehavior.BUFF,
                        note = "장비(=${group.slot.ifBlank { "장비" }})가 부여한 지속 버프",
                    )
                )
            }
        }
        return out
    }

    /**
     * 켜진 정신력 젬의 수치를 전투 수치에 접어 넣는다.
     * 회피는 확률(%)로 들어 있으므로, 같은 공식으로 평가액 비율을 되돌려 곱한 뒤 다시 확률로 바꾼다.
     */
    fun apply(stats: BattlePlayerStats, plan: SpiritBuffPlan): BattlePlayerStats {
        val mods = plan.mods
        if (mods.isEmpty) return stats
        val dodgeBefore = stats.evade.coerceIn(0.0, 92.0)
        val rating = dodgeBefore / max(0.0001, (92.0 - dodgeBefore)) * 7500.0
        val dodgeAfter = (rating * (1.0 + mods.evasionPct / 100.0) / (rating * (1.0 + mods.evasionPct / 100.0) + 7500.0) * 92.0)
            .coerceIn(0.0, 80.0)
        val life = max(1.0, stats.life * (1.0 + mods.lifePct / 100.0) + mods.lifeFlat)
        val es = max(0.0, stats.es * (1.0 + mods.esPct / 100.0) + mods.esFlat)
        val damageFactor = 1.0 + (mods.damagePct + mods.elementalDamagePct + mods.spellDamagePct) / 100.0
        val poolBefore = stats.life + stats.es
        val poolAfter = life + es
        val poolScale = if (poolBefore > 0.0) poolAfter / poolBefore else 1.0
        return stats.copy(
            life = life,
            es = es,
            evade = if (mods.evasionPct != 0.0) dodgeAfter else stats.evade,
            physRed = min(90.0, stats.physRed + mods.physRedPct),
            fireRes = stats.fireRes + mods.resPct,
            coldRes = stats.coldRes + mods.resPct,
            lightRes = stats.lightRes + mods.resPct,
            chaosRes = stats.chaosRes + mods.resPct,
            dps = stats.dps * damageFactor,
            dpsBase = stats.dpsBase * damageFactor,
            aps = stats.aps * (1.0 + mods.attackSpeedPct / 100.0),
            critChance = min(100.0, stats.critChance * (1.0 + mods.critChancePct / 100.0)),
            lifeRegenPct = stats.lifeRegenPct + mods.regenPct,
            esRechargeRatePct = stats.esRechargeRatePct + mods.esRegenPct * 10.0,
            damageTakenLessPct = stats.damageTakenLessPct + mods.damageTakenLessPct,
            ehp = stats.ehp * poolScale,
            totalDps = stats.totalDps * damageFactor,
            skillDpsList = stats.skillDpsList.map { it.copy(dps = it.dps * damageFactor) },
            spiritBuffs = plan,
        )
    }
}
