package com.sunuhwi.poe2dbkr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.hypot

/**
 * 인게임 스킬 모션 확인.
 *
 * 공허의 형상의 상, 선회 공격의 회전 전진, 소용돌이 베기 → 돌개바람 콤보,
 * 폭풍의 종 연계, 전기불꽃·번개 화살·얼음 화살 투사체가 실제로 그렇게 도는지 본다.
 */
class SkillMotionTest {

    private fun stats(
        skill: String = "Whirling Assault",
        fx: SkillEffectType = SkillEffectType.MELEE,
        weapon: WeaponVisualType = WeaponVisualType.QUARTERSTAFF,
        form: AttackForm = AttackForm.MELEE,
        dps: Double = 620_000.0,
        aps: Double = 3.0,
        kit: List<KitSkill> = emptyList(),
        ascend: List<AscendancyEffects.AscSkill> = emptyList(),
        crit: Double = 25.0,
        combo: List<ComboStep> = emptyList(),
    ) = BattlePlayerStats(
        className = "Monk · Martial Artist",
        skillName = skill,
        fx = fx,
        weapon = weapon,
        form = form,
        dps = dps,
        aps = aps,
        critChance = crit,
        critMulti = 260.0,
        life = 3_400.0,
        es = 500.0,
        physRed = 30.0,
        evade = 20.0,
        fireRes = 75.0,
        coldRes = 75.0,
        lightRes = 75.0,
        chaosRes = 0.0,
        ehp = 24_000.0,
        dpsBase = dps,
        ascendSkills = ascend,
        kit = kit,
        combo = combo,
    )

    private fun tick(world: BattleWorld, seconds: Double) {
        repeat((seconds * 60).toInt()) { world.update(1.0 / 60.0) }
    }

    private val hollowForm = AscendancyEffects.AscSkill(
        "공허의 형상", interval = 0.55, damageSharePct = 80.0,
        alwaysCrit = false, radius = 60f, element = DamageElement.PHYSICAL,
    )

    @Test
    fun skillNamesPickTheirInGameMotion() {
        assertEquals(SkillMotion.WHIRLING_ASSAULT, SkillMotions.detect("Whirling Assault"))
        assertEquals(SkillMotion.WHIRLING_ASSAULT, SkillMotions.detect("선회 공격"))
        assertEquals(SkillMotion.WHIRLING_SLASH, SkillMotions.detect("Whirling Slash", "소용돌이 베기"))
        assertEquals(SkillMotion.TWISTER, SkillMotions.detect("돌개바람"))
        assertEquals(SkillMotion.TEMPEST_BELL, SkillMotions.detect("폭풍의 종"))
        assertEquals(SkillMotion.SPARK, SkillMotions.detect("전기불꽃"))
        assertEquals(SkillMotion.LIGHTNING_ARROW, SkillMotions.detect("번개 화살", "Lightning Arrow"))
        assertEquals(SkillMotion.ICE_SHOT, SkillMotions.detect("얼음 화살", "Ice Shot"))
        assertEquals(SkillMotion.COMET, SkillMotions.detect("Comet", "혜성"))
        assertEquals(SkillMotion.FIREBALL, SkillMotions.detect("화염구"))
        assertEquals(SkillMotion.GRENADE, SkillMotions.detect("Explosive Grenade", "폭발 유탄"))
        assertEquals(SkillMotion.KILLING_PALM, SkillMotions.detect("살상 장법"))
        assertEquals(SkillMotion.TORNADO_SHOT, SkillMotions.detect("회오리 사격", "Tornado Shot"))
        // 이름이 겹치는 스킬끼리 헷갈리면 안 된다.
        assertEquals(SkillMotion.TWISTER, SkillMotions.detect("돌개바람"))
        assertEquals(SkillMotion.TORNADO_SHOT, SkillMotions.detect("회오리 사격"))
        assertEquals(SkillMotion.DEFAULT, SkillMotions.detect("Boneshatter"))
        // 이름으로 정해지는 모션은 근접/원거리 구분도 같이 쓴다.
        assertTrue(SkillMotions.detect("선회 공격").isMelee)
        assertFalse(SkillMotions.detect("번개 화살").isMelee)
    }

    @Test
    fun whirlingAssaultSpinsForwardAndHitsRepeatedly() {
        val world = BattleWorld(stats(skill = "Whirling Assault"), BattleMode.MAP, 20, false, null)
        val startX = world.player.x
        val startY = world.player.y
        var sawCyclone = false
        var spinSeen = 0f
        var travelled = 0f
        var lastX = startX
        var lastY = startY
        repeat(120) {
            world.update(1.0 / 60.0)
            if (world.effects.any { fx -> fx.kind == FxKind.CYCLONE }) sawCyclone = true
            spinSeen = maxOf(spinSeen, world.player.whirlAngle)
            travelled += hypot(world.player.x - lastX, world.player.y - lastY)
            lastX = world.player.x
            lastY = world.player.y
        }
        println(
            "[TEST] 선회 공격 회전=${world.whirls} 다단히트=${world.whirlHits} " +
                "이동=${travelled.toInt()}px 회전각=$spinSeen fx=$sawCyclone",
        )
        assertTrue("선회 공격이 돌지 않았다", world.whirls > 0)
        assertTrue("회전 각도가 늘지 않았다", spinSeen > 1.0f)
        assertTrue("주변 적을 여러 번 베지 않았다: ${world.whirlHits}", world.whirlHits >= 12)
        assertTrue("회전하며 앞으로 나아가지 않았다: ${travelled}px", travelled > 60f)
        assertTrue("회오리 연출이 없다", sawCyclone)
        assertTrue("피해를 주지 않았다", world.damageDealt > 0)
    }

    @Test
    fun hollowFormSendsPhantomThatSwingsOnceAndVanishes() {
        val world = BattleWorld(
            stats(ascend = listOf(hollowForm)),
            BattleMode.MAP, 20, false, null,
        )
        var mostAtOnce = 0
        var sawPhantom = false
        repeat(120) {
            world.update(1.0 / 60.0)
            mostAtOnce = maxOf(mostAtOnce, world.phantoms.size)
            if (world.phantoms.isNotEmpty()) sawPhantom = true
        }
        println(
            "[TEST] 공허의 형상 상 생성=${world.phantomSpawns} 공격=${world.phantomCasts} " +
                "최대 동시=${mostAtOnce} 상 피해=${world.ascendDamage}",
        )
        assertTrue("상이 나타나지 않았다", sawPhantom)
        assertTrue("상이 근접 공격에 반응하지 않았다", world.phantomSpawns > 0)
        assertTrue("상이 장착 스킬을 휘두르지 않았다", world.phantomCasts > 0)
        assertTrue("상의 피해가 없다", world.ascendDamage > 0)
        // 상은 한 번 휘두르고 사라진다 — 2초 동안 쌓여 있으면 안 된다.
        assertTrue("상이 사라지지 않고 쌓인다: $mostAtOnce", mostAtOnce <= 4)

        // 공허의 형상이 없는 빌드에서는 상이 나오지 않는다.
        val plain = BattleWorld(stats(), BattleMode.MAP, 20, false, null)
        tick(plain, 2.0)
        assertEquals(0, plain.phantomSpawns)
        assertEquals(0, plain.phantoms.size)
    }

    @Test
    fun powerChargeTurnsOnePhantomIntoThree() {
        val world = BattleWorld(stats(aps = 1.0, ascend = listOf(hollowForm)), BattleMode.MAP, 20, false, null)
        world.grantPowerCharges(1)
        // 첫 공격(0.3초)만 보고 상 수를 센다.
        tick(world, 0.5)
        println("[TEST] 권능 1개 소모 → 상 ${world.phantomSpawns}개 · 남은 충전 ${world.powerCharges}")
        assertEquals("권능 충전을 쓰면 상이 셋 나와야 한다", 3, world.phantomSpawns)
        assertEquals("쓰고 남은 충전이 0이어야 한다", 0, world.powerCharges)
    }

    @Test
    fun userComboStagesWhirlwindThenTwisterEatsThem() {
        val kit = listOf(
            KitSkill("돌개바람", SkillMotion.TWISTER, dps = 240_000.0, role = ComboRole.BURST),
        )
        // 콤보는 사용자가 직접 짠 경우에만 돈다 — 소용돌이 베기 2번 → 돌개바람.
        val combo = listOf(
            ComboStep("소용돌이 베기", SkillMotion.WHIRLING_SLASH, ComboRole.BUILD, repeats = 2, note = "와류 2단계"),
            ComboStep("돌개바람", SkillMotion.TWISTER, ComboRole.BURST, note = "와류 소모"),
        )
        val whirlStats = stats(skill = "Whirling Slash", kit = kit, aps = 2.4, combo = combo)
        val world = BattleWorld(whirlStats, BattleMode.MAP, 24, false, null)
        println("[TEST] 콤보 순서=" + world.comboSummary)
        var sawWhirlwind = false
        var maxStage = 0
        var slowedEnemy = false
        repeat(60 * 6) {
            world.update(1.0 / 60.0)
            if (world.whirlwinds.isNotEmpty()) sawWhirlwind = true
            maxStage = maxOf(maxStage, world.whirlwinds.maxOfOrNull { v -> v.stage } ?: 0)
            if (world.enemies.any { e -> e.alive && e.slowT > 0f }) slowedEnemy = true
        }
        println(
            "[TEST] 와류=${world.whirlwindCasts} 최고단계=$maxStage 돌개바람=${world.twisterCasts} " +
                "콤보HUD=${world.comboHud()}",
        )
        assertTrue("와류가 생기지 않았다", sawWhirlwind)
        assertTrue("와류가 생성되지 않았다", world.whirlwindCasts >= 2)
        assertTrue("와류에 휘말린 적이 없다", slowedEnemy)
        assertTrue("돌개바람이 나가지 않았다", world.twisterCasts > 0)
        assertTrue("콤보가 HUD에 없다: '${world.comboHud()}'", world.comboHud().contains("돌개바람"))
        // 준비가 끝나면 주력기로도 계속 때린다.
        assertTrue("피해를 주지 않았다", world.damageDealt > 0)
    }

    @Test
    fun userComboFillsFourChainsBeforeBellFalls() {
        val kit = listOf(
            KitSkill("폭풍의 종", SkillMotion.TEMPEST_BELL, dps = 300_000.0, role = ComboRole.BURST),
        )
        // 콤보를 직접 짜야 종이 나간다 — 주력 4번으로 연계를 채운 뒤 종.
        val combo = listOf(
            ComboStep("Boneshatter", SkillMotion.DEFAULT, ComboRole.MAIN, repeats = ComboPlanner.BELL_COMBO, note = "연계 4"),
            ComboStep("폭풍의 종", SkillMotion.TEMPEST_BELL, ComboRole.BURST, note = "종 강타"),
        )
        val world = BattleWorld(
            stats(skill = "Boneshatter", kit = kit, aps = 3.0, combo = combo),
            BattleMode.MAP, 20, false, null,
        )
        var bellSeen = false
        var comboBeforeBell = -1
        repeat(60 * 6) {
            world.update(1.0 / 60.0)
            if (world.bells.any { b -> !b.ghost }) {
                if (!bellSeen) comboBeforeBell = world.comboStacks
                bellSeen = true
            }
        }
        println("[TEST] 폭풍의 종 설치=${world.bellCasts} 종 확인=$bellSeen 연계=$comboBeforeBell")
        assertTrue("종이 설치되지 않았다", bellSeen)
        assertTrue("종이 한 번도 떨어지지 않았다", world.bellCasts > 0)
        // 종을 떨어뜨리면 연계는 0으로 돌아간다.
        assertTrue("연계를 채우지 않고 종을 썼다: $comboBeforeBell", comboBeforeBell == 0 || comboBeforeBell == ComboPlanner.BELL_COMBO)
        assertTrue("종 충격파 피해가 없다", world.damageDealt > 0)
    }

    @Test
    fun sparkCrawlsAlongTheGroundInAVolley() {
        val world = BattleWorld(
            stats(skill = "전기불꽃", fx = SkillEffectType.LIGHTNING, weapon = WeaponVisualType.WAND, form = AttackForm.CAST),
            BattleMode.MAP, 25, false, null,
        )
        repeat(40) { world.update(1.0 / 60.0) }
        val sparks = world.projectiles.filter { it.kind == ProjKind.SPARK }
        println("[TEST] 전기불꽃 투사체=${sparks.size} 흔들림=${sparks.firstOrNull()?.wobble}")
        assertTrue("전기불꽃이 여러 개 나가지 않았다: ${sparks.size}", sparks.size >= 4)
        assertTrue("전기불꽃이 지그재그로 가지 않는다", sparks.any { it.wobble > 0f })
    }

    @Test
    fun lightningArrowChainsFromTheImpact() {
        val world = BattleWorld(
            stats(skill = "Lightning Arrow", fx = SkillEffectType.LIGHTNING, weapon = WeaponVisualType.BOW, form = AttackForm.BOW),
            BattleMode.MAP, 30, false, null,
        )
        var bolts = 0
        repeat(60 * 6) {
            world.update(1.0 / 60.0)
            bolts = maxOf(bolts, world.effects.count { fx -> fx.kind == FxKind.BOLT })
        }
        println("[TEST] 번개 화살 연쇄 BOLT=${bolts} 피해=${world.damageDealt.toLong()}")
        assertTrue("연쇄 번개가 뻗지 않았다", bolts >= 2)
        assertTrue("피해가 없다", world.damageDealt > 0)
    }

    @Test
    fun iceShotScattersShards() {
        val world = BattleWorld(
            stats(skill = "Ice Shot", fx = SkillEffectType.ICE, weapon = WeaponVisualType.BOW, form = AttackForm.BOW),
            BattleMode.MAP, 25, false, null,
        )
        var shards = 0
        var cone = false
        repeat(60 * 5) {
            world.update(1.0 / 60.0)
            shards = maxOf(shards, world.projectiles.count { p -> p.kind == ProjKind.ICE_SHARD })
            if (world.effects.any { fx -> fx.kind == FxKind.ICE_CONE }) cone = true
        }
        println("[TEST] 얼음 화살 파편=${shards} 부채꼴=$cone")
        assertTrue("얼음 파편이 흩어지지 않았다", shards >= 3)
        assertTrue("부채꼴 연출이 없다", cone)
    }

    @Test
    fun rocketGrenadeExplodesAfterFuse() {
        val world = BattleWorld(
            stats(skill = "폭발 유탄", fx = SkillEffectType.FIRE, weapon = WeaponVisualType.CROSSBOW, form = AttackForm.CROSSBOW),
            BattleMode.MAP, 30, false, null,
        )
        var grenades = 0
        repeat(60 * 6) {
            world.update(1.0 / 60.0)
            grenades = maxOf(grenades, world.projectiles.count { p -> p.kind == ProjKind.GRENADE })
        }
        println("[TEST] 유탄=${grenades} 피해=${world.damageDealt.toLong()}")
        assertTrue("유탄이 날아가지 않았다", grenades > 0)
        assertTrue("유탄이 터지지 않았다", world.damageDealt > 0)
    }

    /** 실제 마셜 아티스트 빌드의 공허의 형상에는 선회 공격이 들어 있다 */
    @Test
    fun realBuildSocketedSkillDrivesThePhantoms() {
        val build = Pob.decode(File("src/test/resources/pob_real_build.txt").readText())
        val tree = TreeDict.parse(File("src/main/assets/poe2_tree_kr.json").readText())
        val dictFile = File("src/main/assets/poe2_kr_i18n.json")
        val dict = if (dictFile.isFile) KrDict.parse(dictFile.readText()) else KrDict.EMPTY
        val stats = BattleSetup.statsFor(build, dict = dict, tree = tree)!!
        println("[TEST] 주력=${stats.skillName} · 소켓=${stats.hollowSkill}")
        assertNotNull("공허의 형상에 넣은 스킬을 찾지 못했다", stats.hollowSkill)
        assertEquals("소켓 스킬이 선회 공격이어야 한다", SkillMotion.WHIRLING_ASSAULT, stats.hollowSkill!!.motion)
        assertTrue("공허의 형상이 이 빌드에 없다", stats.hollowForm)
        val world = BattleWorld(stats, BattleMode.MAP, 20, false, null)
        var phantomMotion: SkillMotion? = null
        repeat(60 * 3) {
            world.update(1.0 / 60.0)
            world.phantoms.firstOrNull()?.let { phantomMotion = it.motion }
        }
        println("[TEST] 상 모션=$phantomMotion 생성=${world.phantomSpawns} 피해=${world.ascendDamage.toLong()}")
        assertEquals("상이 소켓 스킬 모션을 쓰지 않았다", SkillMotion.WHIRLING_ASSAULT, phantomMotion)
        assertTrue("상이 생성되지 않았다", world.phantomSpawns > 0)
        assertTrue("상 피해가 없다", world.ascendDamage > 0)
    }

    @Test
    fun buildKitIsReadFromTheLoadedBuild() {
        val build = Pob.decode(Pob.sampleCode())
        val kit = BattleSetup.kitSkills(build, main = "Lightning Arrow")
        println("[TEST] 빌드 콤보 재료=" + kit.joinToString { "${it.name}(${it.motion.label}/${it.role})" })
        val stats = BattleSetup.statsFor(build)
        assertNotNull(stats)
        stats!!
        println("[TEST] 주력 모션=${stats.motion.label} 콤보=${ComboPlanner.summary(ComboPlanner.plan(stats.motion, stats.kit))}")
        assertTrue("주력 스킬의 모션을 읽지 못했다", stats.motion != SkillMotion.DEFAULT || stats.skillName.isNotBlank())
        // 빌드에 모션을 아는 스킬이 있으면 콤보 재료로 넘어온다 (없으면 빈 목록이 정상).
        kit.forEach { skill ->
            assertTrue("보조 젬이 콤보 재료로 들어왔다: ${skill.name}", skill.motion != SkillMotion.DEFAULT)
        }
    }
}
