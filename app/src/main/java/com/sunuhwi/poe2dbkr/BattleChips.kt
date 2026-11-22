package com.sunuhwi.poe2dbkr

/** 전투 탭 칩의 두 가지 성격 */
enum class BattleChipKind {
    /** 여럿 중 하나만 켜지는 칩 (모드·마릿수·보스·이펙트·트리거). 켜진 칩을 다시 눌러도 켜진 채로 둔다. */
    PICK,

    /** 켜고 끄는 칩 (피통·피해 증폭). 누를 때마다 켜짐과 꺼짐이 뒤집힌다. */
    TOGGLE,
}

object BattleChipRule {

    /**
     * 누르기 전 상태가 [before]일 때 누른 뒤의 상태.
     *
     * 체크 가능한 칩은 누르는 순간 스스로 상태를 뒤집으므로, 화면 쪽에서는
     * 이 값을 그대로 칩에 되돌려 놓고 바뀐 경우에만 전투 설정을 다시 적용한다.
     */
    fun stateAfterClick(kind: BattleChipKind, before: Boolean): Boolean = when (kind) {
        BattleChipKind.PICK -> true
        BattleChipKind.TOGGLE -> !before
    }
}
