package com.sunuhwi.poe2dbkr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 전직 패시브 노드 분석·적용 확인.
 *
 * poe2db 트리 자료의 전직 노드 669개를 모두 읽어 옵션을 분류하고,
 * 실제 빌드(마셜 아티스트)에 찍힌 전직 노드가 전투 수치·시뮬레이션에 반영되는지 본다.
 */
class AscendancyEffectsTest {

    private val tree: TreeDict by lazy {
        TreeDict.parse(File("src/main/assets/poe2_tree_kr.json").readText())
    }
    private val build: PobBuild by lazy {
        Pob.decode(File("src/test/resources/pob_real_build.txt").readText())
    }
    private val dict: KrDict by lazy {
        val file = File("src/main/assets/poe2_kr_i18n.json")
        if (file.isFile) KrDict.parse(file.readText()) else KrDict.EMPTY
    }

    @Test
    fun catalogHasEveryAscendancyNode() {
        val catalog = AscendancyEffects.catalog(tree)
        println("[ASC] 전직 ${catalog.size}종 · 노드 ${catalog.values.sumOf { it.size }}개")
        assertEquals("전직 수가 poe2db 자료와 다르다", 36, catalog.size)
        val total = catalog.values.sumOf { it.size }
        // 시작 노드(36개)를 뺀 소형·주요 전직 노드. 시작 노드까지 669개다.
        assertTrue("전직 노드가 너무 적다: $total", total >= 630)
        val starts = tree.nodes.values.count { it.isAscendancy && it.kind == 'a' }
        println("[ASC] 전직 시작 노드 ${starts}개 · 합계 ${total + starts}개")
        catalog.forEach { (code, nodes) ->
            assertTrue("$code 전직 노드가 비었다", nodes.isNotEmpty())
            nodes.forEach { node ->
                val parsed = AscendancyEffects.parseNode(node, AscendancyEffects.ascendancyName(code, tree))
                assertEquals("노드 번호가 바뀌었다", node.id, parsed.id)
                if (node.stats.isNotBlank()) {
                    assertTrue("옵션이 분류되지 않았다: ${node.id} ${node.stats}", parsed.lines.isNotEmpty())
                    parsed.lines.forEach { line ->
                        assertNotNull("분류 결과가 없다: ${line.text}", line.scope)
                    }
                }
            }
        }
    }

    @Test
    fun ascendancyNamesResolve() {
        val named = AscendancyEffects.catalog(tree).keys.map { it to AscendancyEffects.ascendancyName(it, tree) }
        val resolved = named.filter { it.second.isNotBlank() }
        println("[ASC] 한글 이름이 있는 전직 ${resolved.size}종: " + resolved.joinToString { "${it.first}=${it.second}" })
        assertTrue("전직 한글 이름을 거의 찾지 못했다", resolved.size >= 15)
        assertEquals("마셜 아티스트", AscendancyEffects.ascendancyName("Monk1", tree))
        assertEquals("리추얼리스트", AscendancyEffects.ascendancyName("Huntress3", tree))
        assertEquals("인퍼널리스트", AscendancyEffects.ascendancyName("Witch1", tree))
    }

    @Test
    fun realBuildAscendancyNodesAreFound() {
        val profile = AscendancyEffects.profile(build, tree)
        assertNotNull("전직 효과를 찾지 못했다", profile)
        profile!!
        println("[ASC] ${profile.classKo} · ${profile.ascendKo} → ${profile.summary}")
        profile.nodes.forEach { node ->
            println("[ASC] ${node.id} ${node.title} (${node.kind}) · " + node.lines.joinToString { "${it.text}[${it.scope}]" })
        }
        // PoB 빌드의 전직 노드 8개(시작 노드 제외)
        assertEquals("전직 노드 수가 다르다", 8, profile.count)
        val names = profile.nodes.map { it.title }
        assertTrue("공허의 형상 기술이 빠졌다: $names", names.contains("공허의 형상 기술"))
        assertTrue("공허의 집중 기술이 빠졌다: $names", names.contains("공허의 집중 기술"))
        assertTrue("룬 기혈이 빠졌다: $names", names.contains("룬 기혈"))
        assertTrue("돌주먹의 도가 빠졌다: $names", names.contains("돌주먹의 도"))
        assertEquals("마셜 아티스트", profile.ascendKo)
        // 주요 노드 4개 + 소형 4개
        assertEquals(4, profile.keyCount)
        assertTrue("부여 스킬을 찾지 못했다: ${profile.skills}", profile.skills.map { it.name }.containsAll(listOf("공허의 형상", "공허의 집중")))
        // 효과 범위 8% · 모든 능력치 +5 같은 소형 노드도 잡혀야 한다.
        assertEquals(8.0, profile.sim.aoePct, 0.01)
        assertEquals(5.0, profile.pob.attributeFlat, 0.01)
        // 이 빌드에는 무예 숙련자(연계 소모 → 재충전 지연 -0.2초)가 없으므로 재충전 감소는 0이다.
        assertEquals(0.0, profile.sim.esRechargeDelayCut, 0.01)
        assertTrue(profile.summary.contains("전직 효과 8개"))
    }

    @Test
    fun parseCommonOptionTexts() {
        val node = TreeNode(
            id = 1, nameEn = "Test", nameKo = "시험", kind = 'n', ascendancy = "Monk1",
            stats = listOf(
                "스킬이 투사체 1개 추가 발사",
                "받는 원소 피해 10% 감폭",
                "물리 피해의 20%를 카오스 피해로 받음",
                "1초마다 생명력의 3% 재생",
                "에너지 보호막 재충전 지연 -1초",
                "감전 확률 20% 증가",
                "효과 범위 8% 증가",
                "최대 생명력 3% 증가",
                "생명력 흡수량 12% 증가",
                "재사용 대기시간 회복 속도 6% 증가",
                "스킬 부여: {공허의 형상}",
                "패시브 스킬 포인트 1포인트 제공",
            ).joinToString("\n"),
        )
        val parsed = AscendancyEffects.parseNode(node, "마셜 아티스트")
        // 노드 전체를 합산한 프로필로 값을 확인한다.
        val profile = AscendancyEffects.profile(
            PobBuild(
                className = "Monk",
                ascendClassName = "Martial Artist",
                treeClassId = 10,
                treeAscendClassId = 1,
                treeNodeIds = mutableListOf(1),
            ),
            TreeDict(
                classes = listOf(TreeClass("Monk", "몽크", listOf("Martial Artist" to "마셜 아티스트"))),
                nodes = mapOf(1 to node),
                classStarts = mapOf(0 to 1),
            ),
        )
        assertNotNull("전직 프로필을 만들지 못했다", profile)
        profile!!
        println("[ASC] 합산 sim=" + profile.sim + " pob=" + profile.pob)
        assertEquals(1, profile.sim.extraProjectiles)
        assertEquals(10.0, profile.sim.damageTakenLessPct, 0.01)
        assertEquals(20.0, profile.sim.physTakenAsChaos, 0.01)
        assertEquals(3.0, profile.sim.lifeRegenPct, 0.01)
        assertEquals(1.0, profile.sim.esRechargeDelayCut, 0.01)
        assertEquals(20.0, profile.sim.shockChancePct, 0.01)
        assertEquals(8.0, profile.sim.aoePct, 0.01)
        assertEquals(3.0, profile.pob.lifePct, 0.01)
        assertEquals(12.0, profile.sim.lifeLeechPct, 0.01)
        assertEquals(6.0, profile.sim.cooldownPct, 0.01)
        val scopes = parsed.lines.map { it.scope }
        assertTrue("스킬 부여는 표시용이어야 한다", parsed.lines.first { it.text.contains("스킬 부여") }.scope == AscendancyEffects.AscScope.NONE)
        assertTrue("패시브 포인트는 전투와 무관하다", parsed.lines.first { it.text.contains("패시브 스킬 포인트") }.scope == AscendancyEffects.AscScope.NONE)
        assertTrue(scopes.contains(AscendancyEffects.AscScope.POB))
    }

    @Test
    fun battleGetsStrongerWithAscendancySkills() {
        val withAsc = BattleSetup.statsFor(build, dict = dict, tree = tree)
        val withoutAsc = BattleSetup.statsFor(build, dict = dict)
        assertNotNull(withAsc)
        assertNotNull(withoutAsc)
        withAsc!!
        withoutAsc!!
        println("[ASC] 전직 반영=${withAsc.ascendSummary} · 효과범위 ${withAsc.aoePct}% · 재충전 ${withAsc.esRechargeDelay}s")
        assertTrue("전직 요약이 비었다", withAsc.ascendSummary.isNotBlank())
        // 화면에는 8개가 모두 뜨고, 그중 전투 계산에 직접 들어가는 노드는 3개(부여 스킬 2 + 효과 범위 1)다.
        assertEquals(3, withAsc.ascendApplied)
        assertEquals(8.0, withAsc.aoePct, 0.01)
        assertEquals(PobCalculator.ES_RECHARGE_DELAY, withAsc.esRechargeDelay, 0.01)
        assertTrue("부여 스킬이 전투에 넘어오지 않았다", withAsc.ascendSkills.isNotEmpty())
        assertEquals(withAsc.dps, withoutAsc.dps, 0.01)

        // 같은 난수 시드로 돌려야 전직 효과만큼의 차이를 볼 수 있다.
        val plain = BattleWorld(withoutAsc, BattleMode.MAP, 20, amp = false, boss = null, seed = 7L)
        val boosted = BattleWorld(withAsc, BattleMode.MAP, 20, amp = false, boss = null, seed = 7L)
        repeat(60 * 20) {
            plain.update(1.0 / 60.0)
            boosted.update(1.0 / 60.0)
        }
        val plainResult = plain.result()
        val boostedResult = boosted.result()
        println("[ASC] 전직 없음 → 피해 ${plainResult.damageDealt} · 처치 ${plainResult.kills}")
        println("[ASC] 전직 적용 → 피해 ${boostedResult.damageDealt} · 처치 ${boostedResult.kills} · 부여 스킬 ${boostedResult.ascendCasts}회 ${boostedResult.ascendDamage}")
        assertTrue("전직 부여 스킬이 한 번도 안 돌았다", boostedResult.ascendCasts > 0)
        assertTrue("전직 부여 스킬 피해가 0이다", boostedResult.ascendDamage > 0)
        // 이 빌드는 두꺼비 폭발이 피해의 대부분이라 총 피해 비교로는 전직 효과를 가려내기 어렵다.
        // 같은 조건의 단일 보스전을 만들어 부여 스킬이 더해지는 피해만 따로 본다.
        val solo = BattlePlayerStats(
            className = "테스트", skillName = "빠른 참격", fx = SkillEffectType.MELEE,
            weapon = WeaponVisualType.SWORD, dps = 120_000.0, aps = 2.0, critChance = 0.0,
            critMulti = 150.0, life = 9_000.0, es = 0.0, physRed = 60.0, evade = 0.0,
            fireRes = 75.0, coldRes = 75.0, lightRes = 75.0, chaosRes = 0.0, ehp = 40_000.0,
        )
        val hollow = solo.copy(
            ascendSkills = withAsc.ascendSkills,
            hollowSkill = KitSkill("선회 공격", SkillMotion.WHIRLING_ASSAULT, 40_000.0, ComboRole.MAIN),
        )
        val plainSolo = BattleWorld(solo, BattleMode.BOSS, 1, amp = false, boss = BossPresets.ALL[0], seed = 11L)
        val hollowSolo = BattleWorld(hollow, BattleMode.BOSS, 1, amp = false, boss = BossPresets.ALL[0], seed = 11L)
        repeat(60 * 12) {
            plainSolo.update(1.0 / 60.0)
            hollowSolo.update(1.0 / 60.0)
        }
        println("[ASC] 단일 보스 → 전직 없음 ${plainSolo.damageDealt} · 전직 적용 ${hollowSolo.damageDealt} · 부여 ${hollowSolo.ascendDamage}")
        assertTrue("단일 보스전에서 전직 부여 스킬이 안 돌았다", hollowSolo.ascendCasts > 0)
        assertTrue(
            "전직 부여 스킬이 피해를 더하지 않았다: ${plainSolo.damageDealt} → ${hollowSolo.damageDealt}",
            hollowSolo.damageDealt > plainSolo.damageDealt,
        )
    }

    @Test
    fun mitigationAndLeechFollowAscendancy() {
        val base = BattleSetup.statsFor(build, dict = dict)!!
        val hardened = base.copy(
            damageTakenLessPct = 30.0,
            physTakenAsChaosPct = 50.0,
            lifeRegenPct = 3.0,
            lifeLeechPct = 50.0,
            esRechargeDelay = 1.2,
        )
        val plainWorld = BattleWorld(base, BattleMode.MAP, 1, amp = false, boss = null)
        val hardWorld = BattleWorld(hardened, BattleMode.MAP, 1, amp = false, boss = null)
        val phys = 1_000.0
        val plain = plainWorld.incomingDamage(phys, DamageElement.PHYSICAL)
        val hard = hardWorld.incomingDamage(phys, DamageElement.PHYSICAL)
        println("[ASC] 물리 1000 피해 → 기본 $plain · 전직 감폭/전환 $hard")
        assertTrue("받는 피해 감폭이 반영되지 않았다: $plain → $hard", hard < plain)
        val fire = plainWorld.incomingDamage(phys, DamageElement.FIRE)
        val fireHard = hardWorld.incomingDamage(phys, DamageElement.FIRE)
        assertTrue("받는 피해 감폭이 원소에도 반영되지 않았다", fireHard < fire)
        // 흡수·재생은 전투를 돌려 확인한다.
        repeat(60 * 3) { hardWorld.update(1.0 / 60.0) }
        assertTrue("재생으로 생명력이 회복되지 않았다", hardWorld.player.life > 0)
    }

    /** 전직별 분석표를 출력한다 (docs/ascendancy-effects.md 생성용). */
    @Test
    fun analysisReport() {
        val catalog = AscendancyEffects.catalog(tree)
        catalog.entries.sortedBy { it.key }.forEach { (code, nodes) ->
            val pair = code.dropLast(1)
            val classKo = tree.classes.firstOrNull { it.nameEn.replace(" ", "") == pair }?.nameKo.orEmpty()
            val ascKo = AscendancyEffects.ascendancyName(code, tree).ifBlank { "미공개" }
            val parsed = nodes.map { AscendancyEffects.parseNode(it, ascKo) }
            val sim = parsed.count { node -> node.lines.any { it.scope == AscendancyEffects.AscScope.SIM } || node.grantedName.isNotBlank() }
            println("[ASC-ASC] $code|$classKo|$ascKo|${nodes.size}|${parsed.count { it.isNotable }}|$sim")
            parsed.filter { it.isNotable }.forEach { node ->
                val scope = scopeOf(node)
                println("[ASC-NODE] $code|${node.id}|$scope|${node.title}|${node.lines.joinToString(" / ") { it.text }}")
            }
        }
        val smalls = LinkedHashMap<String, AscendancyEffects.AscScope>()
        catalog.values.flatten()
            .filter { it.kind == 's' }
            .forEach { node ->
                AscendancyEffects.parseNode(node, "").lines.forEach { line ->
                    smalls.putIfAbsent(line.text, line.scope)
                }
            }
        smalls.forEach { (text, scope) -> println("[ASC-SMALL] $scope|$text") }
        assertFalse(smalls.isEmpty())
    }

    private fun scopeOf(node: AscendancyEffects.AscNode): String {
        val scopes = node.lines.map { it.scope }.toSet()
        return when {
            node.hasSim -> "SIM"
            scopes.contains(AscendancyEffects.AscScope.POB) -> "POB"
            else -> "NONE"
        }
    }
}
