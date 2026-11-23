package com.sunuhwi.poe2dbkr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.hypot

/**
 * 관절 모션·콤보 설정·기름 포화·부적 변신 확인.
 *
 * 그리기와 검증이 같은 [SkillPoses.skeleton] 좌표를 보므로, 여기서 손·무기·무릎 위치를
 * 확인하면 화면에 그려지는 자세도 같은 값이다.
 */
class BattleMotionComboTest {

    private fun stats(
        skill: String = "Whirling Slash",
        fx: SkillEffectType = SkillEffectType.MELEE,
        weapon: WeaponVisualType = WeaponVisualType.SWORD,
        form: AttackForm = AttackForm.MELEE,
        kit: List<KitSkill> = emptyList(),
        combo: List<ComboStep> = emptyList(),
        beast: BeastForm = BeastForm.NONE,
        beastProfile: BeastProfile = BeastProfile(),
        ascend: List<AscendancyEffects.AscSkill> = emptyList(),
        dps: Double = 500_000.0,
        aps: Double = 2.4,
    ) = BattlePlayerStats(
        className = "Monk",
        skillName = skill,
        fx = fx,
        weapon = weapon,
        form = form,
        dps = dps,
        aps = aps,
        critChance = 20.0,
        critMulti = 240.0,
        life = 3_000.0,
        es = 400.0,
        physRed = 25.0,
        evade = 15.0,
        fireRes = 70.0,
        coldRes = 70.0,
        lightRes = 70.0,
        chaosRes = 0.0,
        ehp = 20_000.0,
        dpsBase = dps,
        kit = kit,
        combo = combo,
        beast = beast,
        beastProfile = beastProfile,
        ascendSkills = ascend,
    )

    private fun poseAt(motion: SkillMotion, phase: Float, form: AttackForm = AttackForm.MELEE): SkeletonJoints {
        val attack = (1f - phase).coerceIn(0f, 1f)
        val pose = SkillPoses.pose(motion, form, attack, 0f, false, 0f)
        return SkillPoses.skeleton(200f, 500f, 1.62f, pose, 0f)
    }

    @Test
    fun everySkillHasItsOwnArticulatedMotion() {
        val signatures = LinkedHashMap<SkillMotion, Float>()
        val motions = listOf(
            SkillMotion.WHIRLING_SLASH, SkillMotion.TWISTER, SkillMotion.SPARK,
            SkillMotion.LIGHTNING_ARROW, SkillMotion.ICE_SHOT, SkillMotion.COMET,
            SkillMotion.TEMPEST_BELL, SkillMotion.KILLING_PALM, SkillMotion.FLICKER,
            SkillMotion.CURSE, SkillMotion.GRENADE, SkillMotion.OIL_BARRAGE,
            SkillMotion.OIL_GRENADE, SkillMotion.BARRAGE, SkillMotion.TOTEM,
            SkillMotion.BEAST_MAUL, SkillMotion.BEAST_HOWL, SkillMotion.WYVERN_BREATH,
            SkillMotion.WHIRLING_ASSAULT, SkillMotion.TORNADO_SHOT, SkillMotion.FIREBALL,
        )
        motions.forEach { motion ->
            // 한 순간만 보면 비슷한 자세가 겹치므로 시전 초·중·후반을 모두 더해 서명을 만든다.
            var sig = 0f
            listOf(0.16f, 0.40f, 0.66f).forEach { phase ->
                val j = poseAt(motion, phase)
                sig += j.frontHandX + j.frontHandY * 1.7f + j.backHandX * 0.6f +
                    j.weaponTipY * 0.3f + j.frontKneeX * 0.4f
            }
            signatures[motion] = sig
        }
        // 스킬마다 자세가 달라야 한다 — 같은 숫자가 나오면 키프레임이 복사된 것이다.
        val rounded = signatures.values.map { it.toInt() }
        val unique = rounded.toSet().size
        println("[TEST] 스킬 자세 서명=${signatures.size}개 중 서로 다른 값=$unique")
        if (unique < signatures.size - 2) {
            val byValue = signatures.entries.groupBy { it.value.toInt() }.filter { it.value.size > 1 }
            println("[TEST] 같은 자세: " + byValue.values.joinToString { list -> list.joinToString(",") { it.key.label } })
        }
        assertTrue("스킬 자세가 겹친다: $unique/${signatures.size}", unique >= signatures.size - 2)
    }

    @Test
    fun cometRaisesBothHandsThenSlamsDown() {
        val up = poseAt(SkillMotion.COMET, 0.12f)
        val down = poseAt(SkillMotion.COMET, 0.56f)
        println("[TEST] 혜성 들기 손=(${up.frontHandX},${up.frontHandY}) 머리=${up.headY} 내려찍기 손=${down.frontHandY} 어깨=${down.shoulderY}")
        assertTrue("혜성이 두 손을 머리 위로 들지 않는다", up.frontHandY < up.headY)
        assertTrue("반대손도 들지 않는다", up.backHandY < up.headY)
        assertTrue("혜성이 땅으로 내려찍지 않는다", down.frontHandY > down.shoulderY + 4f)
        assertTrue("내려찍을 때 몸이 낮아지지 않는다", down.hipY > up.hipY)
    }

    @Test
    fun sparkFlicksWandForwardAndBack() {
        val back = poseAt(SkillMotion.SPARK, 0.02f)
        val thrust = poseAt(SkillMotion.SPARK, 0.18f)
        println("[TEST] 전기불꽃 뒤로=${back.frontHandX} 앞으로=${thrust.frontHandX} 어깨=${thrust.shoulderX}")
        assertTrue("마법봉을 뒤로 눕히지 않는다", back.frontHandX < back.shoulderX + 4f)
        assertTrue("마법봉을 앞으로 튕기지 않는다", thrust.frontHandX > thrust.shoulderX + 10f)
        assertTrue("손이 크게 움직이지 않는다", abs(thrust.frontHandX - back.frontHandX) > 12f)
    }

    @Test
    fun bowDrawsStringToTheCheekAndReleases() {
        val drawn = poseAt(SkillMotion.LIGHTNING_ARROW, 0.30f, AttackForm.BOW)
        val released = poseAt(SkillMotion.LIGHTNING_ARROW, 0.62f, AttackForm.BOW)
        val drawnNear = hypot(drawn.backHandX - drawn.headX, drawn.backHandY - drawn.headY)
        val releasedNear = hypot(released.backHandX - released.headX, released.backHandY - released.headY)
        println("[TEST] 활 당김 손거리=${drawnNear} 놓은 뒤=${releasedNear}")
        assertTrue("시위를 얼굴 쪽으로 당기지 않는다", drawnNear < 22f)
        assertTrue("시위를 놓은 손이 뒤로 빠지지 않는다", releasedNear > drawnNear + 4f)
        assertTrue("앞손이 활을 앞으로 내밀지 않는다", drawn.frontHandX > drawn.shoulderX + 8f)
    }

    @Test
    fun twisterBringsBothArmsOverheadThenSweeps() {
        val overhead = poseAt(SkillMotion.TWISTER, 0.02f)
        val sweep = poseAt(SkillMotion.TWISTER, 0.58f)
        println("[TEST] 돌개바람 만세 손=${overhead.frontHandY} 휘두름=${sweep.frontHandY}")
        assertTrue("두 팔을 머리 위로 모으지 않는다", overhead.frontHandY < overhead.headY && overhead.backHandY < overhead.headY)
        assertTrue("크게 휘돌리지 않는다", sweep.frontHandY > overhead.frontHandY + 20f)
    }

    @Test
    fun whirlingSlashTurnsTheWholeBodyAndSweepsTheBlade() {
        val early = poseAt(SkillMotion.WHIRLING_SLASH, 0.05f)
        val mid = poseAt(SkillMotion.WHIRLING_SLASH, 0.32f)
        val spin = SkillPoses.bodySpin(SkillMotion.WHIRLING_SLASH, attack = 0.35f)
        println("[TEST] 소용돌이베기 검끝=${early.weaponTipX},${early.weaponTipY} → ${mid.weaponTipX},${mid.weaponTipY} 몸회전=$spin")
        assertTrue("360도 회전이 아니다: $spin", spin > 3.0f)
        assertTrue("검끝이 원호를 그리지 않는다", abs(mid.weaponTipY - early.weaponTipY) > 10f)
        assertNotEquals(early.weaponTipX, mid.weaponTipX)
    }

    @Test
    fun kneesBendInsteadOfStayingStraight() {
        val j = poseAt(SkillMotion.COMET, 0.56f)
        val cross = (j.frontKneeX - j.hipX) * (j.frontFootY - j.hipY) -
            (j.frontKneeY - j.hipY) * (j.frontFootX - j.hipX)
        println("[TEST] 무릎 꺾임 넓이=$cross")
        assertTrue("무릎이 일직선이다 — 관절이 꺾이지 않았다", abs(cross) > 20f)
    }

    @Test
    fun beastFormsHaveTheirOwnJoints() {
        val bear = BeastMotions.pose(BeastForm.BEAR, attack = 0.55f, walk = 1f, moving = true, time = 0.4f)
        val wolf = BeastMotions.pose(BeastForm.WEREWOLF, attack = 0.5f, walk = 1f, moving = true, time = 0.4f)
        val wyvern = BeastMotions.pose(BeastForm.WYVERN, attack = 0f, walk = 0f, moving = false, time = 0.4f, channel = 0.7f)
        println("[TEST] 곰 턱=${bear.jaw} 앞발=${bear.frontReach} 늑대 꼬리=${wolf.tail} 와이번 날개=${wyvern.wing}")
        assertTrue("곰이 내려찍으며 턱을 벌리지 않는다", bear.jaw > 0.3f)
        assertTrue("늑대인간이 꼬리를 쓰지 않는다", abs(wolf.tail) > 0.1f || wolf.mane > 0.3f)
        assertTrue("와이번이 날개를 펴지 않는다", wyvern.wing > 0.7f)
        assertTrue("와이번이 숨을 토하지 않는다", wyvern.jaw > 0.4f)
        assertNotEquals(bear.jaw, wolf.jaw)
    }

    @Test
    fun talismanBuildTransformsAndFiresOil() {
        val build = TestBuilds.build(
            className = "Druid",
            ascend = "Oracle",
            groups = listOf(listOf("Oil Barrage"), listOf("Fireball")),
        )
        val talisman = PobItem(
            id = 99,
            name = "광분 부적",
            rarity = "Unique",
            baseType = "Fury Talisman",
            itemClass = "부적",
        )
        build.items[99] = talisman
        build.slots["Weapon 1"] = 99
        val stats = BattleSetup.statsFor(build)!!
        println("[TEST] 부적 판정=${stats.beast} 프로필=${stats.beastProfile} 무기=${stats.weapon} 형태=${stats.form}")
        assertEquals(BeastForm.WYVERN, stats.beast)
        assertEquals(AttackForm.CAST, stats.form)
        assertEquals("야수는 무기를 들지 않는다", WeaponVisualType.CLAW, stats.weapon)
        assertTrue("부적 이름을 읽지 못했다", stats.beastProfile.talisman.contains("부적"))

        val world = BattleWorld(stats, BattleMode.MAP, 20, false, null)
        repeat(60 * 2) { world.update(1.0 / 60.0) }
        println("[TEST] 변신=${world.player.shiftAnim} HUD=${world.beastHud()} 기름=${world.oilSpills} 포화=${world.barrageShots}")
        assertEquals("변신이 끝나지 않았다", 1f, world.player.shiftAnim, 0.001f)
        assertTrue("변신 HUD가 없다", world.beastHud().contains("와이번"))
        assertTrue("기름 포화가 나가지 않았다", world.barrageShots > 0)
        assertTrue("기름 지대가 생기지 않았다", world.oilSpills > 0)
        assertTrue("피해가 없다", world.damageDealt > 0)
    }

    @Test
    fun talismanIsNotAShieldOrAnAmulet() {
        assertTrue(Beasts.isTalisman(PobItem(baseType = "Fury Talisman", itemClass = "부적")))
        assertTrue(Beasts.isTalisman(PobItem(baseType = "Lumbering Talisman", itemClass = "Talismans")))
        assertFalse("부적 문양 방패는 부적이 아니다", Beasts.isTalisman(PobItem(baseType = "Sigil Crest Shield", itemClass = "Shield")))
        assertFalse("리구리움 부적은 목걸이다", Beasts.isTalisman(PobItem(name = "Ligurium Talisman", baseType = "Lapis Amulet", itemClass = "Amulet")))
        assertEquals(BeastForm.BEAR, Beasts.formOf("사나운 발톱"))
        assertEquals(BeastForm.WYVERN, Beasts.formOf("Oil Barrage"))
        assertEquals(BeastForm.WEREWOLF, Beasts.formOf("십자 베기"))
        assertEquals(null, Beasts.formOf("Lightning Arrow"))
    }

    @Test
    fun oilGroundSlowsThenBurnsAndDamages() {
        val world = BattleWorld(stats(skill = "기름 포화", fx = SkillEffectType.FIRE), BattleMode.MAP, 12, false, null)
        val enemy = world.enemies.first { it.alive }
        world.spillOilAt(enemy.x, enemy.y, 70f)
        repeat(30) { world.update(1.0 / 60.0) }
        println("[TEST] 기름=${world.oils.size} 노출=${enemy.oilT} 느려짐=${enemy.slowT}")
        assertTrue("기름 지대가 없다", world.oils.isNotEmpty())
        assertTrue("기름에 젖은 적이 노출되지 않는다", enemy.oilT > 0f)
        assertTrue("기름에 젖은 적이 느려지지 않는다", enemy.slowT > 0f)
        val before = world.oils.first().burning
        world.igniteOilAt(enemy.x, enemy.y, 30f)
        repeat(30) { world.update(1.0 / 60.0) }
        println("[TEST] 불붙음=$before→${world.oils.firstOrNull()?.burning} 점화=${world.oilIgnitions} 기름피해=${world.oilDamage.toLong()}")
        assertTrue("기름에 불이 붙지 않았다", world.oilIgnitions > 0)
        assertTrue("불붙은 기름이 피해를 주지 않는다", world.oilDamage > 0)
        assertTrue("불붙은 적이 점화되지 않는다", enemy.igniteT > 0f)
    }

    @Test
    fun comboSettingCastsTheExactSequence() {
        val kit = listOf(
            KitSkill("연발 사격", SkillMotion.BARRAGE, dps = 300_000.0, role = ComboRole.BURST),
            KitSkill("돌개바람", SkillMotion.TWISTER, dps = 340_000.0, role = ComboRole.BURST),
        )
        val combo = listOf(
            ComboStep("소용돌이 베기", SkillMotion.WHIRLING_SLASH, ComboRole.BUILD, repeats = 3, note = "와류 2단계"),
            ComboStep("연발 사격", SkillMotion.BARRAGE, ComboRole.BURST, dps = 300_000.0),
            ComboStep("돌개바람", SkillMotion.TWISTER, ComboRole.BURST, dps = 340_000.0, note = "와류 소모"),
        )
        val world = BattleWorld(
            stats(skill = "Whirling Slash", kit = kit, combo = combo, aps = 3.0),
            BattleMode.MAP, 20, false, null,
        )
        println("[TEST] 설정 콤보=${world.comboSummary} 직접=${world.comboCustom}")
        assertTrue("직접 설정 콤보로 인식하지 않았다", world.comboCustom)
        assertEquals(3, world.comboPlan.size)
        repeat(60 * 5) { world.update(1.0 / 60.0) }
        println("[TEST] 콤보 기록=${world.comboHistory.take(9)} 횟수=${world.comboCasts}")
        val history = world.comboHistory
        assertTrue("콤보가 3번 돌지 않았다: $history", history.size >= 3)
        assertEquals("첫 칸이 소용돌이 베기가 아니다", "소용돌이 베기", history[0])
        // 세 번 반복한 뒤에야 다음 스킬로 넘어간다.
        assertEquals(3, history.take(3).count { it == "소용돌이 베기" })
        assertTrue("연발 사격이 나가지 않았다: $history", history.contains("연발 사격"))
        assertTrue("돌개바람이 나가지 않았다: $history", world.twisterCasts > 0)
        assertTrue("와류가 쌓이지 않았다", world.whirlwindCasts >= 2)
        // 순서가 섞이지 않는다 — 연발 사격 뒤에 곧바로 돌개바람이 온다.
        val barrageAt = history.indexOf("연발 사격")
        assertTrue("연발 사격 뒤에 돌개바람이 오지 않는다: $history", history.drop(barrageAt).contains("돌개바람"))
    }

    @Test
    fun comboWaitsForBellChainsInsteadOfBreaking() {
        val kit = listOf(KitSkill("폭풍의 종", SkillMotion.TEMPEST_BELL, dps = 400_000.0, role = ComboRole.BURST))
        val combo = listOf(
            ComboStep("폭풍의 종", SkillMotion.TEMPEST_BELL, ComboRole.BURST, note = "종"),
            ComboStep("주력 타격", SkillMotion.DEFAULT, ComboRole.MAIN, repeats = 2),
        )
        val world = BattleWorld(stats(skill = "Mace Strike", kit = kit, combo = combo), BattleMode.MAP, 18, false, null)
        repeat(60 * 5) { world.update(1.0 / 60.0) }
        println("[TEST] 종=${world.bellCasts} 대기=${world.comboWaits} 연계=${world.comboStacks} 기록=${world.comboHistory.take(8)}")
        assertTrue("연계를 채우지 않고 종을 쳤다", world.comboWaits > 0)
        assertTrue("종이 끝내 떨어지지 않았다", world.bellCasts > 0)
        assertTrue("피해가 없다", world.damageDealt > 0)
    }

    @Test
    fun comboSettingRoundTripsThroughStorage() {
        val options = listOf(
            ComboPick("Whirling Slash", "소용돌이 베기", SkillMotion.WHIRLING_SLASH, ComboRole.BUILD, dps = 100.0),
            ComboPick("Twister", "돌개바람", SkillMotion.TWISTER, ComboRole.BURST, note = "와류 소모", dps = 200.0),
        )
        val picks = listOf(options[0], options[0], options[1])
        val encoded = BattleCombos.encode(picks)
        val decoded = BattleCombos.decode(encoded, options)
        println("[TEST] 저장='$encoded' → ${decoded.map { it.label }}")
        assertEquals(3, decoded.size)
        assertEquals("소용돌이 베기", decoded[0].label)
        assertEquals("소용돌이 베기", decoded[1].label)
        assertEquals(SkillMotion.TWISTER, decoded[2].motion)
        assertEquals("와류 소모", decoded[2].note)
        assertEquals(BattleCombos.summary(picks), BattleCombos.summary(decoded))
        // 빌드에 없는 스킬은 읽을 때 빠진다.
        val onlyTwister = BattleCombos.decode(encoded, listOf(options[1]))
        assertEquals(1, onlyTwister.size)
        assertEquals(SkillMotion.TWISTER, onlyTwister[0].motion)
    }

    @Test
    fun sparkVolleyBouncesAndWebs() {
        val world = BattleWorld(
            stats(skill = "전기불꽃", fx = SkillEffectType.LIGHTNING, weapon = WeaponVisualType.WAND, form = AttackForm.CAST),
            BattleMode.MAP, 25, false, null,
        )
        var sparks = 0
        var bouncers = 0
        var webs = 0
        repeat(60 * 3) {
            world.update(1.0 / 60.0)
            val live = world.projectiles.filter { p -> p.kind == ProjKind.SPARK }
            sparks = maxOf(sparks, live.size)
            bouncers = maxOf(bouncers, live.count { p -> p.bounces > 0 })
            webs = maxOf(webs, world.effects.count { fx -> fx.kind == FxKind.ARC_WEB })
        }
        println("[TEST] 전기불꽃=$sparks 튕김=$bouncers 그물=$webs")
        assertTrue("전기불꽃이 여러 갈래로 나가지 않는다", sparks >= 5)
        assertTrue("전기불꽃이 벽에서 튕기지 않는다", bouncers > 0)
        assertTrue("번개가 사방으로 갈라지지 않는다", webs > 0)
    }

    @Test
    fun allKnownSkillsCastWithoutErrors() {
        val cases = listOf(
            Triple("Whirling Slash", SkillMotion.WHIRLING_SLASH, SkillEffectType.MELEE),
            Triple("Twister", SkillMotion.TWISTER, SkillEffectType.MELEE),
            Triple("Spark", SkillMotion.SPARK, SkillEffectType.LIGHTNING),
            Triple("Lightning Arrow", SkillMotion.LIGHTNING_ARROW, SkillEffectType.LIGHTNING),
            Triple("Ice Shot", SkillMotion.ICE_SHOT, SkillEffectType.ICE),
            Triple("Comet", SkillMotion.COMET, SkillEffectType.ICE),
            Triple("Oil Barrage", SkillMotion.OIL_BARRAGE, SkillEffectType.FIRE),
            Triple("Oil Grenade", SkillMotion.OIL_GRENADE, SkillEffectType.FIRE),
            Triple("Barrage", SkillMotion.BARRAGE, SkillEffectType.LIGHTNING),
            Triple("Spell Totem", SkillMotion.TOTEM, SkillEffectType.LIGHTNING),
            Triple("Maul", SkillMotion.BEAST_MAUL, SkillEffectType.MELEE),
            Triple("Ferocious Roar", SkillMotion.BEAST_HOWL, SkillEffectType.MELEE),
            Triple("Flicker Strike", SkillMotion.FLICKER, SkillEffectType.MELEE),
        )
        cases.forEach { (name, motion, fx) ->
            assertEquals(name, motion, SkillMotions.detect(name))
            val world = BattleWorld(
                stats(skill = name, fx = fx, weapon = WeaponVisualType.SWORD, form = AttackForm.MELEE),
                BattleMode.MAP, 14, false, null,
            )
            repeat(60 * 2) { world.update(1.0 / 60.0) }
            println("[TEST] $name → 모션=${motion.label} 피해=${world.damageDealt.toLong()} f만=${world.effects.size} 기름=${world.oilSpills}")
            assertTrue("$name 스킬이 아무 동작도 하지 않았다", world.player.attackCount > 0)
        }
    }
}
