package com.sunuhwi.poe2dbkr

import kotlin.math.max

/**
 * 전직(Ascendancy) 패시브 노드 효과.
 *
 * PoB 빌드에서 찍힌 전직 노드를 poe2db 트리 자료(assets/poe2_tree_kr.json)의 노드 번호로 찾아,
 * 노드에 적힌 옵션을 두 갈래로 나눈다.
 *
 *  - [AscScope.POB]  생명력·DPS·저항처럼 PoB가 이미 계산해 `PlayerStat`에 넣은 수치.
 *                    다시 더하면 두 번 세는 셈이므로 전투 수치에는 넣지 않고 목록에만 보여 준다.
 *  - [AscScope.SIM]  PoB의 단일 타격 계산으로는 드러나지 않는 메커니즘(부여 스킬, 추가 투사체,
 *                    상태이상 확률, 받는 피해 감폭, 보호막 재충전·흡수 등). 전투 시뮬레이터가
 *                    직접 계산해 반영한다.
 *  - [AscScope.NONE] 패시브 포인트·룬 홈처럼 전투와 무관한 옵션.
 *
 * 전직 노드는 669개(소형 425·주요 207·시작 36·특수 1)이고, 옵션 문장은 poe2db 한글 표기를 따른다.
 */
object AscendancyEffects {

    /** 옵션 한 줄을 어디에 반영하는지 */
    enum class AscScope { POB, SIM, NONE }

    /** 시뮬레이터가 직접 계산하는 효과 (전직 노드 합계) */
    data class Sim(
        var aoePct: Double = 0.0,
        var extraProjectiles: Int = 0,
        var extraChains: Int = 0,
        var metaEnergyPct: Double = 0.0,
        var cooldownPct: Double = 0.0,
        /** 상태이상 확률 증가(%) — 기본 확률에 대한 상대값 */
        var shockChancePct: Double = 0.0,
        var freezeChancePct: Double = 0.0,
        var igniteChancePct: Double = 0.0,
        var poisonChancePct: Double = 0.0,
        var bleedChancePct: Double = 0.0,
        /** 감전·냉각·중독 강도 증가(%) */
        var ailmentMagnitudePct: Double = 0.0,
        /** 받는 피해 감폭(%) */
        var damageTakenLessPct: Double = 0.0,
        /** 물리 피해를 다른 속성으로 받는 비율(%) */
        var physTakenAsFire: Double = 0.0,
        var physTakenAsCold: Double = 0.0,
        var physTakenAsLightning: Double = 0.0,
        var physTakenAsChaos: Double = 0.0,
        /** 회피로 막는 원소 피해 감소(%) */
        var evasionAsElementalPct: Double = 0.0,
        /** 에너지 보호막 재충전 지연 감소(초) */
        var esRechargeDelayCut: Double = 0.0,
        /** 에너지 보호막 재충전 속도 증가(%) */
        var esRechargeRatePct: Double = 0.0,
        /** 1초마다 회복하는 생명력 비율(%) */
        var lifeRegenPct: Double = 0.0,
        /** 준 피해 중 흡수하는 비율(%) */
        var lifeLeechPct: Double = 0.0,
    ) {
        val isEmpty: Boolean get() = this == Sim()
    }

    /** PoB가 이미 계산에 넣은 수치 (목록 표시용 합계) */
    data class Pob(
        var damagePct: Double = 0.0,
        var attackSpeedPct: Double = 0.0,
        var castSpeedPct: Double = 0.0,
        var skillSpeedPct: Double = 0.0,
        var critChancePct: Double = 0.0,
        var critMultiPct: Double = 0.0,
        var lifePct: Double = 0.0,
        var manaPct: Double = 0.0,
        var esPct: Double = 0.0,
        var armourPct: Double = 0.0,
        var evasionPct: Double = 0.0,
        var blockPct: Double = 0.0,
        var fireRes: Double = 0.0,
        var coldRes: Double = 0.0,
        var lightRes: Double = 0.0,
        var chaosRes: Double = 0.0,
        var maxResPct: Double = 0.0,
        var moveSpeedPct: Double = 0.0,
        var spirit: Double = 0.0,
        var attributeFlat: Double = 0.0,
        var attributePct: Double = 0.0,
        var minionDamagePct: Double = 0.0,
        var totemPct: Double = 0.0,
        var otherPct: Double = 0.0,
    ) {
        val isEmpty: Boolean get() = this == Pob()
    }

    /** 옵션 한 줄 */
    data class AscLine(
        val text: String,
        val scope: AscScope,
        /** 화면에 같이 적을 짧은 설명 (없으면 빈 문자열) */
        val note: String = "",
    )

    /** 전직 노드 하나 */
    data class AscNode(
        val id: Int,
        val nameKo: String,
        val nameEn: String,
        val kind: Char,
        val ascendancy: String,
        val ascendancyKo: String,
        val lines: List<AscLine>,
        val grantedName: String = "",
        val grantedText: String = "",
        val grantedStats: String = "",
    ) {
        val title: String get() = nameKo.ifBlank { nameEn.ifBlank { "노드 $id" } }
        val isNotable: Boolean get() = kind == 'n'
        val hasSim: Boolean get() =
            lines.any { it.scope == AscScope.SIM } || AscendancyEffects.grantedSkill(grantedName) != null
        /** 이 노드가 전투에 실제로 하는 일을 한 줄로 */
        val effectText: String
            get() = lines.joinToString(" ") { it.text }.ifBlank { grantedText }
    }

    /** 전투 시뮬레이터가 돌리는 전직 부여 스킬 */
    data class AscSkill(
        val name: String,
        /** 발동 간격(초) */
        val interval: Double,
        /** 장착 스킬의 한 대 피해 대비 비율(%) */
        val damageSharePct: Double,
        val alwaysCrit: Boolean,
        val radius: Float,
        val element: DamageElement,
    )

    /** 전직 효과 묶음 */
    data class Profile(
        val classKo: String,
        val ascendKo: String,
        val ascendCode: String,
        val nodes: List<AscNode>,
        val sim: Sim,
        val pob: Pob,
        /** 주요(전직 고유) 노드 이름 — 화면 요약에 먼저 적는다 */
        val keyNames: List<String>,
        val skills: List<AscSkill>,
    ) {
        val count: Int get() = nodes.size
        val keyCount: Int get() = nodes.count { it.isNotable }
        val summary: String
            get() = buildString {
                append("전직 효과 ").append(count).append("개 적용")
                if (keyNames.isNotEmpty()) append(" · ").append(keyNames.joinToString(" · "))
            }
        val isBlank: Boolean get() = count == 0
    }

    /** 노드 옵션에 적힌 값 — 퍼센트·개수·초 단위를 먼저 본다. */
    private val NUMBER = Regex("(-?\\d+(?:\\.\\d+)?)")
    private val PERCENT = Regex("(-?\\d+(?:\\.\\d+)?)\\s*%")
    private val COUNTED = Regex("(-?\\d+(?:\\.\\d+)?)\\s*(개|초|회|미터|중첩)")
    /** "피해 20% 증가/증폭" 처럼 단순한 피해 수치만 세기 위한 틀 */
    private val DAMAGE_GAIN = Regex("피해\\s*\\d+(?:\\.\\d+)?%\\s*(증가|증폭)")
    private val DAMAGE_GAIN_EN = Regex("(increased|more)\\s+\\d+(?:\\.\\d+)?%\\s+.*damage")

    /** 부여 스킬 중 전투 피해를 내는 것으로 확인된 것 (poe2db 스킬 창 수치 기준) */
    private val GRANTED_SKILLS = mapOf(
        // 집중 유지 중 상을 만들어 장착한 근접 공격을 대신 수행한다 (상 1개, 권능 충전 시 +2).
        "공허의 형상" to AscSkill("공허의 형상", interval = 0.55, damageSharePct = 80.0, alwaysCrit = false, radius = 60f, element = DamageElement.PHYSICAL),
        "hollow form" to AscSkill("공허의 형상", interval = 0.55, damageSharePct = 80.0, alwaysCrit = false, radius = 60f, element = DamageElement.PHYSICAL),
        // 3초마다 종이 나타나고, 종을 때리면 충격파가 터진다. 종 명중은 항상 치명타.
        "공허의 집중" to AscSkill("공허의 집중", interval = 3.0, damageSharePct = 90.0, alwaysCrit = true, radius = 115f, element = DamageElement.PHYSICAL),
        "hollow focus" to AscSkill("공허의 집중", interval = 3.0, damageSharePct = 90.0, alwaysCrit = true, radius = 115f, element = DamageElement.PHYSICAL),
    )

    fun grantedSkill(name: String): AscSkill? = GRANTED_SKILLS[name.trim().lowercase()]

    /** 빌드에 찍힌 전직 노드만 모은다. 트리 자료가 없으면 null. */
    fun profile(build: PobBuild, tree: TreeDict): Profile? {
        if (tree.isEmpty() || build.treeNodeIds.isEmpty()) return null
        val classIndex = tree.classIndex(build.className, build.treeClassId)
        val ascIndex = if (classIndex >= 0) {
            tree.ascendancyIndex(classIndex, build.ascendClassName, build.treeAscendClassId)
        } else {
            0
        }
        val cls = tree.classes.getOrNull(classIndex)
        val classKo = cls?.nameKo.orEmpty().ifBlank { build.className }
        val ascendKo = cls?.ascendancies?.getOrNull(ascIndex - 1)?.second.orEmpty()
            .ifBlank { build.ascendClassName }
        val ascendCode = if (ascIndex > 0 && classIndex >= 0) "${cls?.nameEn.orEmpty().replace(" ", "")}$ascIndex" else ""

        val sim = Sim()
        val pob = Pob()
        val nodes = ArrayList<AscNode>()
        val skills = ArrayList<AscSkill>()
        build.treeNodeIds.distinct().forEach { id ->
            val node = tree.node(id) ?: return@forEach
            if (!node.isAscendancy) return@forEach
            if (node.kind == 'a' || node.kind == 'c') return@forEach
            // 빌드의 전직과 다른 전직 노드가 섞여 있으면 건너뛴다.
            if (ascendCode.isNotBlank() && node.ascendancy.isNotBlank() && node.ascendancy != ascendCode) {
                return@forEach
            }
            val parsed = parseNode(node, ascendKo.ifBlank { build.ascendClassName })
            nodes.add(parsed)
            parsed.lines.forEach { line -> accumulate(line, sim, pob) }
            val granted = grantedSkill(parsed.grantedName)
            if (granted != null && skills.none { it.name == granted.name }) skills.add(granted)
        }
        if (nodes.isEmpty()) return null
        // 주요 노드를 먼저, 그다음 노드 번호 순으로 보여 준다.
        nodes.sortWith(compareByDescending<AscNode> { it.isNotable }.thenBy { it.id })
        return Profile(
            classKo = classKo,
            ascendKo = ascendKo,
            ascendCode = if (ascendCode.isNotBlank()) ascendCode else nodes.first().ascendancy,
            nodes = nodes,
            sim = sim,
            pob = pob,
            keyNames = nodes.filter { it.isNotable }.map { it.title },
            skills = skills,
        )
    }

    /** 트리 자료 전체에서 전직 노드를 전직별로 모은다 (분석·커버리지 확인용). */
    fun catalog(tree: TreeDict): Map<String, List<TreeNode>> {
        val out = LinkedHashMap<String, MutableList<TreeNode>>()
        tree.nodes.values
            .filter { it.isAscendancy && it.kind != 'a' }
            .sortedWith(compareBy({ it.ascendancy }, { it.id }))
            .forEach { node -> out.getOrPut(node.ascendancy) { ArrayList() }.add(node) }
        return out
    }

    /** 전직 코드(Monk1)의 한글 전직 이름 */
    fun ascendancyName(code: String, tree: TreeDict): String {
        val pair = classAndAscOf(code, tree) ?: return ""
        return tree.classes.getOrNull(pair.first)?.ascendancies?.getOrNull(pair.second - 1)?.second.orEmpty()
    }

    private fun classAndAscOf(code: String, tree: TreeDict): Pair<Int, Int>? {
        if (code.length < 2) return null
        val index = code.last().digitToIntOrNull() ?: return null
        val prefix = code.dropLast(1)
        tree.classes.forEachIndexed { classIndex, cls ->
            if (cls.nameEn.replace(" ", "").equals(prefix, ignoreCase = true)) {
                return classIndex to index
            }
        }
        return null
    }

    fun parseNode(node: TreeNode, ascendKo: String): AscNode {
        val lines = node.stats.split('\n')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { text ->
                val probe = Sim()
                val probePob = Pob()
                val scope = classify(text, probe, probePob)
                AscLine(text, scope, note = noteFor(text, scope))
            }
        return AscNode(
            id = node.id,
            nameKo = node.nameKo,
            nameEn = node.nameEn,
            kind = node.kind,
            ascendancy = node.ascendancy,
            ascendancyKo = ascendKo,
            lines = lines,
            grantedName = node.grantedName,
            grantedText = node.grantedText,
            grantedStats = node.grantedStats,
        )
    }

    /** 노드 하나의 효과를 합계에 더한다. */
    private fun accumulate(line: AscLine, sim: Sim, pob: Pob) {
        if (line.scope == AscScope.NONE) return
        if (line.scope == AscScope.SIM) classify(line.text, sim, Pob())
        else classify(line.text, Sim(), pob)
    }

    private fun numOf(line: String): Double {
        val match = PERCENT.find(line) ?: COUNTED.find(line) ?: NUMBER.find(line)
        return match?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
    }

    private fun noteFor(text: String, scope: AscScope): String = when {
        scope == AscScope.POB -> "PoB 합산"
        text.contains("스킬 부여") -> "부여 스킬"
        else -> ""
    }

    /**
     * 옵션 한 줄을 분류하고, 시뮬레이션 효과면 [sim], PoB 합산 수치면 [pob]에 더한다.
     * 앞쪽에 있는 규칙이 더 구체적이므로 순서를 바꾸면 안 된다.
     */
    private fun classify(raw: String, sim: Sim, pob: Pob): AscScope {
        val line = raw.trim()
        if (line.isEmpty()) return AscScope.NONE
        val lower = line.lowercase()
        fun has(vararg keys: String) = keys.any { lower.contains(it.lowercase()) }
        val n = numOf(line)

        // 1. 전투와 무관한 문장
        if (has("패시브 스킬 포인트", "passive skill point")) return AscScope.NONE
        if (has("스킬 부여", "grants skill")) return AscScope.NONE
        if (has("룬 전용 홈", "rune socket")) return AscScope.NONE
        if (has("불운", "unlucky")) return AscScope.NONE
        if (has("무시", "ignore")) return AscScope.NONE
        if (has("남색", "보라색 화염", "파란 화염", "붉은 화염")) return AscScope.NONE
        if (has("시작 지점에서 패시브 스킬 할당")) return AscScope.NONE
        if (has("상자 장착", "소지품 슬롯")) return AscScope.NONE

        // 2. 받는 피해·피해 전환 (시뮬)
        if (has("물리 피해의") && has("받음")) {
            when {
                has("화염 피해로 받음") -> sim.physTakenAsFire += n
                has("냉기 피해로 받음") -> sim.physTakenAsCold += n
                has("번개 피해로 받음") -> sim.physTakenAsLightning += n
                has("카오스 피해로 받음") -> sim.physTakenAsChaos += n
            }
            return AscScope.SIM
        }
        if (has("회피의") && has("원소 피해 감소")) {
            sim.evasionAsElementalPct += max(0.0, n)
            return AscScope.SIM
        }
        if (has("받는 원소 피해") && has("감폭")) {
            sim.damageTakenLessPct += n
            return AscScope.SIM
        }
        if (has("받는 피해") && has("감폭")) {
            sim.damageTakenLessPct += n
            return AscScope.SIM
        }

        // 3. 보호막 재충전·재생·흡수 (시뮬)
        if (has("재충전 지연")) {
            // "-0.2초"처럼 줄어드는 값이므로 부호를 뒤집어 감소량으로 쌓는다.
            sim.esRechargeDelayCut += -n
            return AscScope.SIM
        }
        if (has("재충전 속도")) {
            sim.esRechargeRatePct += n
            return AscScope.SIM
        }
        if (has("생명력 흡수") || (has("주문 피해의") && has("생명력으로 흡수")) || has("흡수 회복")) {
            sim.lifeLeechPct += n
            return AscScope.SIM
        }
        if (has("1초마다 생명력의", "1초마다 최대 생명력의")) {
            sim.lifeRegenPct += n
            return AscScope.SIM
        }

        // 4. 상태이상 확률·강도 (시뮬)
        if (has("감전 확률")) {
            sim.shockChancePct += n
            return AscScope.SIM
        }
        if (has("출혈 유발") || has("출혈 확률")) {
            sim.bleedChancePct += n
            return AscScope.SIM
        }
        if (has("동결 확률", "빙결 확률")) {
            sim.freezeChancePct += n
            return AscScope.SIM
        }
        if (has("점화 확률")) {
            sim.igniteChancePct += n
            return AscScope.SIM
        }
        if (has("중독 확률")) {
            sim.poisonChancePct += n
            return AscScope.SIM
        }
        if (has("감전의 강도", "냉각의 강도", "중독의 강도", "점화 강도", "출혈 강도")) {
            sim.ailmentMagnitudePct += n
            return AscScope.SIM
        }

        // 5. 사격·범위·메타 (시뮬)
        if (has("투사체 1개 추가", "additional projectile")) {
            sim.extraProjectiles += n.toInt().coerceAtLeast(1)
            return AscScope.SIM
        }
        if (has("추가 연쇄")) {
            sim.extraChains += n.toInt().coerceAtLeast(1)
            return AscScope.SIM
        }
        if (has("메타 스킬의 에너지 획득")) {
            sim.metaEnergyPct += n
            return AscScope.SIM
        }
        if (has("재사용 대기시간 회복")) {
            sim.cooldownPct += n
            return AscScope.SIM
        }
        if (has("효과 범위")) {
            sim.aoePct += n
            return AscScope.SIM
        }

        // 6. PoB가 이미 계산하는 수치 (감소·감폭은 부호를 뒤집는다)
        val v = if ((lower.contains("감소") || lower.contains("감폭")) && n > 0) -n else n
        when {
            has("최대 생명력", "생명력 최대치") -> pob.lifePct += v
            has("최대 마나", "마나 최대치") -> pob.manaPct += v
            has("에너지 보호막 최대치") -> pob.esPct += v
            has("방어도 및 회피") -> {
                pob.armourPct += v
                pob.evasionPct += v
            }
            has("방어도, 회피") -> {
                pob.armourPct += v
                pob.evasionPct += v
                pob.esPct += v
            }
            has("방어도") -> pob.armourPct += v
            has("회피") -> pob.evasionPct += v
            has("저항 최대치") -> pob.maxResPct += v
            has("모든 원소 저항") -> {
                pob.fireRes += v
                pob.coldRes += v
                pob.lightRes += v
            }
            has("화염 저항") -> pob.fireRes += v
            has("냉기 저항") -> pob.coldRes += v
            has("번개 저항") -> pob.lightRes += v
            has("카오스 저항") -> pob.chaosRes += v
            has("공격 속도") -> pob.attackSpeedPct += v
            has("시전 속도") -> pob.castSpeedPct += v
            has("스킬 속도") -> pob.skillSpeedPct += v
            has("치명타 피해") -> pob.critMultiPct += v
            has("치명타") -> pob.critChancePct += v
            has("막기 확률") -> pob.blockPct += v
            has("이동 속도") -> pob.moveSpeedPct += v
            has("정신력") -> pob.spirit += v
            has("모든 능력치") -> pob.attributeFlat += v
            has("능력치") -> pob.attributePct += v
            has("토템") -> pob.totemPct += v
            has("소환수", "반려수") -> pob.minionDamagePct += v
            has("피해") && DAMAGE_GAIN.containsMatchIn(lower) -> pob.damagePct += v
            has("damage") && DAMAGE_GAIN_EN.containsMatchIn(lower) -> pob.damagePct += v
            else -> return AscScope.NONE
        }
        return AscScope.POB
    }
}
