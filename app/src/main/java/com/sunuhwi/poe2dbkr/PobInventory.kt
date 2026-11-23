package com.sunuhwi.poe2dbkr

import android.content.Context
import android.view.View
import androidx.core.content.ContextCompat

/**
 * PoE2 캐릭터 장비창(인벤토리) 배치.
 *
 * 리스트 대신 게임과 같은 자리에 슬롯을 놓는다.
 *
 * ```
 *        [   투구   ] [목걸이]
 * [무기 1] [  갑옷  ] [무기 2]
 * [반지 1] [ 허리띠 ] [반지 2]
 * [ 장갑 ]           [ 장화 ]
 * ```
 *
 * 배치·선택 상태 계산은 안드로이드에 기대지 않으므로 JVM 테스트에서 그대로 확인한다.
 */
object PobInventory {

    /** 격자 열 수 */
    const val COLUMNS = 4

    /** 격자 행 수 */
    const val ROWS = 4

    /** 슬롯 한 칸. [col]에서 [colSpan]칸을 차지한다. */
    data class Cell(val slot: String, val col: Int, val row: Int, val colSpan: Int = 1) {
        val lastCol: Int get() = col + colSpan - 1
    }

    /** 가운데 두 칸을 크게 쓰는 게임 장비창 배치 */
    val GRID: List<Cell> = listOf(
        Cell("Helmet", col = 1, row = 0, colSpan = 2),
        Cell("Amulet", col = 3, row = 0),
        Cell("Weapon 1", col = 0, row = 1),
        Cell("Body Armour", col = 1, row = 1, colSpan = 2),
        Cell("Weapon 2", col = 3, row = 1),
        Cell("Ring 1", col = 0, row = 2),
        Cell("Belt", col = 1, row = 2, colSpan = 2),
        Cell("Ring 2", col = 3, row = 2),
        Cell("Gloves", col = 0, row = 3),
        Cell("Boots", col = 3, row = 3),
    )

    /** 격자 아래 작은 칸으로 붙는 슬롯 (무기 교체·반지 3·부적) */
    val EXTRAS: List<String> = listOf(
        "Weapon 1 Swap", "Weapon 2 Swap", "Ring 3", "Charm 1", "Charm 2", "Charm 3",
    )

    /** 빌드에 들어 있는 플라스크 슬롯 이름 */
    fun flaskSlots(build: PobBuild?): List<String> =
        build?.slots?.keys?.filter { it.startsWith(Pob.FLASK_PREFIX) }?.sorted().orEmpty()

    /**
     * 아이콘을 한 번 탭하면 그 슬롯의 옵션 창이 열리고,
     * 같은 아이콘을 다시 탭하면 닫힌다. 다른 슬롯을 탭하면 그 슬롯으로 바뀐다.
     */
    fun toggle(selected: String?, tapped: String): String? =
        if (selected == tapped) null else tapped

    fun isSelected(selected: String?, slot: String): Boolean = selected == slot

    /** 등급 이름(한글) */
    fun rarityLabel(context: Context, rarity: String): String = context.getString(
        when (rarity.lowercase()) {
            "unique" -> R.string.pob_rarity_unique
            "rare" -> R.string.pob_rarity_rare
            "magic" -> R.string.pob_rarity_magic
            else -> R.string.pob_rarity_normal
        }
    )

    /** 등급별 이름 색 */
    fun rarityColor(view: View, rarity: String): Int = when (rarity.lowercase()) {
        "unique" -> ContextCompat.getColor(view.context, R.color.unique_name)
        "magic" -> ContextCompat.getColor(view.context, R.color.magic_name)
        "rare" -> ContextCompat.getColor(view.context, R.color.rare_name)
        else -> ContextCompat.getColor(view.context, R.color.text_primary)
    }

    /** 등급별 테두리 drawable */
    fun rarityFrame(rarity: String): Int = when (rarity.lowercase()) {
        "unique" -> R.drawable.bg_slot_unique
        "rare" -> R.drawable.bg_slot_rare
        "magic" -> R.drawable.bg_slot_magic
        else -> R.drawable.bg_slot_normal
    }
}
