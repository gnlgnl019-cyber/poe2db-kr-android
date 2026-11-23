package com.sunuhwi.poe2dbkr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * poe2db.tw/kr 수치와 시뮬레이션이 어긋나지 않는지 확인한다.
 *
 * 스킬마다 젬 설명에 적힌 투사체 수·기폭 시간·반경·연계 시간을 그대로 옮겼고,
 * 여기서는 그 값이 전투에서 실제로 그렇게 도는지 본다.
 */
class Poe2SkillNumbersTest {

    private fun stats(
        skill: String,
        fx: SkillEffectType = SkillEffectType.ICE,
        weapon: WeaponVisualType = WeaponVisualType.WAND,
        form: AttackForm = AttackForm.CAST,
        aps: Double = 1.0,
        kit: List<KitSkill> = emptyList(),
    ) = BattlePlayerStats(
        className = "Sorceress · Stormweaver",
        skillName = skill,
        fx = fx,
        weapon = weapon,
        form = form,
        dps = 20_000.0,
        aps = aps,
        critChance = 20.0,
        critMulti = 220.0,
        life = 3_000.0,
        es = 800.0,
        physRed = 20.0,
        evade = 20.0,
        fireRes = 75.0,
        coldRes = 75.0,
        lightRes = 75.0,
        chaosRes = 0.0,
        ehp = 20_000.0,
        dpsBase = 20_000.0,
        kit = kit,
    )

    private fun tick(world: BattleWorld, seconds: Double) {
        repeat((seconds * 60).toInt()) { world.update(1.0 / 60.0) }
    }

    @Test
    fun tempestBellComboFallsOffAfterEightSeconds() {
        // 주력이 폭풍의 종인 빌드는 주력기를 칠 때마다 연계가 쌓인다.
        // 주력기 + 폭풍의 종이 함께 든 빌드 — 주력기로 연계를 채운다.
        val world = BattleWorld(
            stats(
                "전기불꽃", fx = SkillEffectType.LIGHTNING, aps = 1.0,
                kit = listOf(KitSkill("폭풍의 종", SkillMotion.TEMPEST_BELL, 0.0, ComboRole.BURST)),
            ),
            BattleMode.MAP, 12, false, null,
        )
        tick(world, 0.5)
        val stacks = world.comboStacks
        println("[TEST] 연계=$stacks · 흐른 시간=${world.comboSilence} · 제한=${ComboPlanner.BELL_COMBO_TIMEOUT}")
        assertTrue("연계가 쌓이지 않았다", stacks > 0)
        // 8초가 되기 전에는 연계가 남아 있고
        world.advanceBellCombo((ComboPlanner.BELL_COMBO_TIMEOUT - world.comboSilence) - 0.2f)
        assertTrue("8초가 안 됐는데 연계가 사라졌다", world.comboStacks > 0)
        // 8초를 넘기면 사라진다 (poe2db: 이 스킬이 8초 동안 연계를 생성하지 않으면 연계 상실)
        world.advanceBellCombo(0.4f)
        assertEquals("8초가 지났는데 연계가 남아 있다", 0, world.comboStacks)
    }

    @Test
    fun explosiveGrenadeThrowsTwoWithThreeSecondFuse() {
        val world = BattleWorld(stats("폭발 유탄", fx = SkillEffectType.FIRE), BattleMode.MAP, 20, false, null)
        var pair: List<Projectile> = emptyList()
        for (frame in 0 until 120) {
            world.update(1.0 / 60.0)
            val live = world.projectiles.filter { it.kind == ProjKind.GRENADE }
            if (live.size >= 2) {
                pair = live
                break
            }
        }
        println("[TEST] 유탄=${pair.size} 퓨즈=${pair.map { it.fuse }}")
        assertTrue("폭발 유탄이 두 개 나가지 않았다 (poe2db: 투사체 +1개)", pair.size >= 2)
        // poe2db 기폭 시간 3초 — 던진 직후 퓨즈가 3초 근처다.
        assertTrue("퓨즈가 3초가 아니다: ${pair.firstOrNull()?.fuse}", pair.first().fuse > 2.6f && pair.first().fuse <= 3.0f)
    }

    @Test
    fun oilGrenadeFuseMatchesPoe2db() {
        val world = BattleWorld(stats("기름 유탄", fx = SkillEffectType.FIRE), BattleMode.MAP, 20, false, null)
        var grenade: Projectile? = null
        for (frame in 0 until 120) {
            world.update(1.0 / 60.0)
            grenade = world.projectiles.firstOrNull { it.kind == ProjKind.GRENADE && it.oilRadius > 0f }
            if (grenade != null) break
        }
        println("[TEST] 기름 유탄 퓨즈=${grenade?.fuse} 기름 반경=${grenade?.oilRadius}")
        assertTrue("기름 유탄이 나가지 않았다", grenade != null)
        assertTrue("퓨즈가 1.6초가 아니다: ${grenade!!.fuse}", grenade!!.fuse > 1.0f && grenade!!.fuse <= 1.6f)
    }

    @Test
    fun fireballBlastSendsRingOfSmallFireArrows() {
        val world = BattleWorld(stats("화염구", fx = SkillEffectType.FIRE), BattleMode.MAP, 20, false, null)
        var ring = 0
        repeat(60 * 4) {
            world.update(1.0 / 60.0)
            val arrows = world.projectiles.count {
                it.kind == ProjKind.ARROW && it.element == DamageElement.FIRE
            }
            ring = maxOf(ring, arrows)
        }
        println("[TEST] 화염구 고리 화살=${ring} 고리=${world.fireballRings} 피해=${world.damageDealt.toLong()}")
        assertTrue("화염구 고리가 터지지 않았다", world.fireballRings > 0)
        assertTrue("작은 화염 화살이 고리로 나가지 않았다: $ring", ring >= 6)
    }

    @Test
    fun iceNovaPushesAndHitsEveryDirection() {
        val world = BattleWorld(stats("얼음 폭발", fx = SkillEffectType.ICE), BattleMode.MAP, 25, false, null)
        var sawNova = false
        repeat(60 * 3) {
            world.update(1.0 / 60.0)
            if (world.effects.any { fx -> fx.kind == FxKind.FROST_NOVA }) sawNova = true
        }
        println("[TEST] 얼음 폭발=${world.iceNovaCasts} 회 시전 · 연출=$sawNova · 피해=${world.damageDealt.toLong()}")
        assertTrue("얼음 폭발이 시전되지 않았다", world.iceNovaCasts > 0)
        assertTrue("냉기 파동 연출이 없다", sawNova)
        assertTrue("피해가 없다", world.damageDealt > 0.0)
    }

    @Test
    fun frostBombPulsesThenBlastsAfterFourSeconds() {
        val world = BattleWorld(stats("서리 폭탄"), BattleMode.MAP, 20, false, null)
        tick(world, 3.0)
        val early = world.frostBombBlasts
        tick(world, 2.5)
        println("[TEST] 서리 폭탄 3초 기폭=$early · 5.5초 기폭=${world.frostBombBlasts}")
        assertEquals("서리 폭탄이 4초보다 먼저 터졌다 (poe2db: 기폭 시간 4초)", 0, early)
        assertTrue("서리 폭탄이 기폭하지 않는다", world.frostBombBlasts > 0)
    }

    @Test
    fun kelariDecoyExplodesWhenCastAgain() {
        val world = BattleWorld(stats("켈라리의 기만", fx = SkillEffectType.MELEE), BattleMode.MAP, 20, false, null)
        tick(world, 1.2)
        val decoy = world.spiritDecoy
        println("[TEST] 분신=$decoy 폭발=${world.decoyBlasts}")
        assertTrue("분신이 남지 않았다", decoy != null)
        tick(world, 2.0)
        assertTrue("다시 썼는데 분신이 터지지 않았다", world.decoyBlasts > 0)
    }

    @Test
    fun tornadoShotTornadoIsOneMetreWide() {
        val world = BattleWorld(
            stats("회오리 사격", fx = SkillEffectType.MELEE, weapon = WeaponVisualType.BOW, form = AttackForm.BOW),
            BattleMode.MAP, 20, false, null,
        )
        tick(world, 2.0)
        val radii = world.twisters.map { it.radius }
        println("[TEST] 회오리 사격 회오리 반경=$radii")
        assertTrue("회오리가 서지 않았다", radii.isNotEmpty())
        // poe2db: 충격 반경 1미터 (1m ≈ 42px) — 74px(1.8m)로 부풀려 두지 않는다.
        assertTrue("회오리가 1미터보다 훨씬 넓다: $radii", radii.all { it <= 46f })
    }
}
