package com.sunuhwi.poe2dbkr

/**
 * 커뮤니티 공개 집계를 옮겨 둔 메타 스냅샷.
 * poe.ninja 빌드 통계는 외부 접근이 막혀 있어(403) 앱이 실시간 값을 흉내 내지 않는다.
 * 아래 수치는 공개된 티어리스트/빌드 목록을 그대로 옮긴 것이며, 화면에 출처와 기준일을 함께 적는다.
 */
data class MetaBuild(
    val skill: String,
    val ascendancy: String,
    val dps: String,
    val note: String,
)

object MetaSnapshot {

    /** 스냅샷 기준일 */
    const val UPDATED = "2026-11-22"

    /** 리그/패치 */
    const val LEAGUE = "Runes of Aldur · 0.5.5h"

    const val SOURCE =
        "출처: U4N PoE2 0.5 티어리스트(2026-11-22 갱신, poe.ninja 집계 인용) · PoB Archives 공개 빌드 목록 · 실시간 수치가 아닙니다"

    /** 전직 점유율(%) — U4N 0.5 리그 표 */
    val ASCENDANCIES: List<Pair<String, Double>> = listOf(
        "Martial Artist" to 25.0,
        "Spirit Walker" to 17.0,
        "Deadeye" to 15.0,
        "Tactician" to 5.0,
        "Oracle" to 5.0,
        "Disciple of Ketzuli" to 4.0,
        "Witchhunter" to 4.0,
        "Lich" to 4.0,
        "Stormweaver" to 3.0,
        "Titan" to 3.0,
        "Blood Mage" to 2.0,
        "Gemling Legionnaire" to 2.0,
        "Infernalist" to 2.0,
        "Abyssal Lich" to 2.0,
        "Shaman" to 1.0,
    )

    /** 커뮤니티에 공개된 빌드 예시 (가이드 작성자가 신고한 DPS) */
    val BUILDS: List<MetaBuild> = listOf(
        MetaBuild(
            skill = "Twister",
            ascendancy = "Martial Artist",
            dps = "18.4M",
            note = "시뮬라크럼·매핑 — 공개 빌드 중 최고 DPS 예시",
        ),
        MetaBuild(
            skill = "Spark Totems",
            ascendancy = "—",
            dps = "2.87M",
            note = "레벨링부터 엔드게임까지 겸용",
        ),
        MetaBuild(
            skill = "Arc (Stormshield)",
            ascendancy = "Gemling Legionnaire",
            dps = "2.69M",
            note = "매핑 중심 · 방패 스택",
        ),
        MetaBuild(
            skill = "Whirling Assault",
            ascendancy = "Martial Artist",
            dps = "843K",
            note = "원소 근접 · 가성비 세팅",
        ),
        MetaBuild(
            skill = "Comet (Cast on Critical)",
            ascendancy = "Blood Mage",
            dps = "706K",
            note = "치명타 시 시전 트리거 빌드",
        ),
    )

    /** 인기 스킬 태그 — PoB Archives 빌드 목록에서 자주 보이는 축 */
    val POPULAR_SKILLS: List<String> = listOf(
        "Twister", "Whirling Assault", "Spark", "Storm Wave", "Lightning Arrow",
        "Explosive Grenade", "Comet", "Arc", "Tempest Bell",
    )
}
