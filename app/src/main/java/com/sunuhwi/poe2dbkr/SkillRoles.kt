package com.sunuhwi.poe2dbkr

/**
 * 스킬이 전투에서 맡는 자리 — 주력 피해를 내는지, 이동·보조용인지.
 *
 * 자동으로 주력 스킬을 고를 때 이동기(점멸 타격 등)가 늘 주력 자리를 차지하면
 * 정작 빌드의 주력기(폭풍의 질풍 등)가 전투에서 한 번도 안 나가고 DPS 기록에도 안 남는다.
 */
object SkillRoles {

    /** 이동기 — 제자리에서 피해를 내기보다 몸을 옮기는 스킬 */
    private val MOVEMENT = listOf(
        "flicker strike", "blink", "leap slam", "dash", "shield charge", "flame dash",
        "frostblink", "lightning warp", "bodyswap", "escape shot", "disengage",
        "shattering step", "phase run", "smoke mine", "temporal rift", "tempest bell",
    )

    /** 이동기·보조용 스킬인지 (이름으로 판단) */
    fun isMovement(name: String): Boolean {
        val key = name.trim().lowercase()
        if (key.isEmpty()) return false
        return MOVEMENT.any { key == it || key.contains(it) }
    }

    /** 스킬 칸·콤보·DPS 기록에 올릴 수 있는 '직접 쓰는 피해 스킬'인지 */
    fun isActiveDamageGem(gem: PobGem): Boolean {
        if (PobCalculator.isSupport(gem)) return false
        if (PobTriggers.kindOf(gem) != null) return false
        if (SpiritGems.isSpirit(gem.name)) return false
        if (Heralds.detect(gem.name) != null) return false
        return true
    }
}
