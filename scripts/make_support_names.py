#!/usr/bin/env python3
"""poe2db 보조 젬 페이지에서 영문 이름을 읽어 SupportGems.kt의 목록을 다시 만든다.

    python3 scripts/make_support_names.py

결과: app/src/main/java/com/sunuhwi/poe2dbkr/SupportGems.kt 의 NAMES 목록을 덮어쓴다.
캐시: .cache/gems/kr_support.html, .cache/gems/us_support.html (없으면 내려받는다)

이름 목록이 필요한 이유: PoB 공유 코드·poe.ninja에서 가져온 빌드는 보조 젬의 skillId가 비어 있다.
그때 이름으로도 보조 젬을 가려내지 못하면 '범위 집중' 같은 보조 젬이 액티브 스킬로 잡혀
스킬 칸·콤보·스킬별 DPS에 섞인다.
"""
import re
import sys
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
CACHE = ROOT / ".cache" / "gems"
OUT = ROOT / "app" / "src" / "main" / "java" / "com" / "sunuhwi" / "poe2dbkr" / "SupportGems.kt"
UA = (
    "Mozilla/5.0 (Linux; Android 14; SM-S918N) AppleWebKit/537.36 "
    "(KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
)
FILTER = re.compile(r'data-filters="([^"]*)"')
ENGLISH = re.compile(r"[A-Za-z][A-Za-z'\- ]*")


def page(url: str, name: str) -> str:
    CACHE.mkdir(parents=True, exist_ok=True)
    path = CACHE / name
    if not path.exists() or path.stat().st_size < 2000:
        req = urllib.request.Request(url, headers={"User-Agent": UA})
        with urllib.request.urlopen(req, timeout=90) as resp:
            path.write_bytes(resp.read())
    return path.read_text(encoding="utf-8", errors="replace")


def names(html: str) -> set:
    """data-filters 의 마지막 영문 이름 (한글 페이지는 '보조 효과 범위 Concentrated Area')"""
    out = set()
    for text in FILTER.findall(html):
        tokens = ENGLISH.findall(text)
        if not tokens:
            continue
        candidate = tokens[-1].strip()
        if len(candidate) >= 3:
            out.add(candidate)
    return out


def main() -> int:
    ko = page("https://poe2db.tw/kr/Support_Gems", "kr_support.html")
    us = page("https://poe2db.tw/us/Support_Gems", "us_support.html")
    ko_names = names(ko)
    us_filters = [t.strip().lower() for t in FILTER.findall(us)]
    final = sorted(
        n for n in ko_names
        if any(u.endswith(" " + n.lower()) or u == "support " + n.lower() for u in us_filters)
    )
    print(f"보조 젬 이름 {len(final)}종")
    body = "\n".join('        "%s",' % n.replace('"', '\\"') for n in final)
    src = OUT.read_text(encoding="utf-8")
    start = src.index("    private val NAMES: Set<String> = hashSetOf(")
    head_end = src.index("\n", start) + 1
    tail_start = src.index("    ).mapTo(HashSet())", head_end)
    OUT.write_text(src[:head_end] + body + "\n" + src[tail_start:], encoding="utf-8")
    print(f"저장: {OUT}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
