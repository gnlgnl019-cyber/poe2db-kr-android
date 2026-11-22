package com.sunuhwi.poe2dbkr

/**
 * 커뮤니티 공개 집계를 옮겨 둔 메타 스냅샷.
 * poe.ninja 빌드 통계는 외부 접근이 막혀 있어(403) 앱이 실시간 값을 흉내 내지 않는다.
 * 아래 수치는 공개된 티어리스트/빌드 목록을 그대로 옮긴 것이며, 화면에 출처와 기준일을 함께 적는다.
 *
 * 전직·스킬 이름은 게임 한국어 표기를 쓴다(poe2db.tw/kr 전직·스킬 젬 페이지와 대조).
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
    const val LEAGUE = "알두르의 룬 · 0.5.5h"

    const val SOURCE =
        "출처: U4N PoE2 0.5 티어리스트(2026-11-22 갱신, poe.ninja 집계 인용) · PoB Archives 공개 빌드 목록 · 실시간 수치가 아닙니다"

    /** 전직 점유율(%) — U4N 0.5 리그 표. 괄호 안은 원문 표기. */
    val ASCENDANCIES: List<Pair<String, Double>> = listOf(
        "마셜 아티스트" to 25.0,        // Martial Artist
        "스피릿 워커" to 17.0,          // Spirit Walker
        "데드아이" to 15.0,             // Deadeye
        "택티션" to 5.0,                // Tactician
        "오라클" to 5.0,                // Oracle
        "디사이플 오브 케출리" to 4.0,   // Disciple of Ketzuli
        "위치헌터" to 4.0,              // Witchhunter
        "리치" to 4.0,                  // Lich
        "스톰위버" to 3.0,              // Stormweaver
        "타이탄" to 3.0,                // Titan
        "블러드 메이지" to 2.0,         // Blood Mage
        "젬링 리저네어" to 2.0,         // Gemling Legionnaire
        "인퍼널리스트" to 2.0,          // Infernalist
        "심연의 리치" to 2.0,           // Abyssal Lich
        "샤먼" to 1.0,                  // Shaman
    )

    /** 커뮤니티에 공개된 빌드 예시 (가이드 작성자가 신고한 DPS) */
    val BUILDS: List<MetaBuild> = listOf(
        MetaBuild(
            skill = "돌개바람",
            ascendancy = "마셜 아티스트",
            dps = "1,840만",
            note = "시뮬라크럼·매핑 — 공개 빌드 중 최고 DPS 예시",
        ),
        MetaBuild(
            skill = "전기불꽃 토템",
            ascendancy = "—",
            dps = "287만",
            note = "레벨링부터 엔드게임까지 겸용",
        ),
        MetaBuild(
            skill = "연쇄 번개 (폭풍 방패)",
            ascendancy = "젬링 리저네어",
            dps = "269만",
            note = "매핑 중심 · 방패 스택",
        ),
        MetaBuild(
            skill = "선회 공격",
            ascendancy = "마셜 아티스트",
            dps = "84.3만",
            note = "원소 근접 · 가성비 세팅",
        ),
        MetaBuild(
            skill = "혜성 (치명타 시 시전)",
            ascendancy = "블러드 메이지",
            dps = "70.6만",
            note = "치명타 시 시전 트리거 빌드",
        ),
    )

    /** 인기 스킬 태그 — PoB Archives 빌드 목록에서 자주 보이는 축 */
    val POPULAR_SKILLS: List<String> = listOf(
        "돌개바람", "선회 공격", "전기불꽃", "폭풍 파도", "번개 화살",
        "폭발 유탄", "혜성", "연쇄 번개", "폭풍의 종",
    )
}
