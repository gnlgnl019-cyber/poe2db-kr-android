#!/usr/bin/env python3
"""스킬 젬·보조 젬의 영문 이름을 한글 이름으로 옮기는 사전을 만든다.

    python3 scripts/fetch_poe2_gems.py

결과: app/src/main/assets/poe2_kr_i18n.json 의 "gems" 항목
      ([영문 이름, 한글 이름] 배열. 나머지 항목은 건드리지 않는다.)

동작:
  1. poe2db.tw의 젬 목록 페이지(스킬 젬·보조 젬·Spirit Gems)를 영문판과 한국어판 두 벌로 읽어
     같은 슬러그의 이름을 짝짓는다. 슬러그가 곧 두 판을 잇는 열쇠다.
  2. 지금 목록에 없는 예전 이름(PoB 0.1 시절 보조 젬 등)은 웨이백 머신에 남은 0.1 목록에서 보탠다.
     오래된 샘플 빌드의 "Martial Tempo" 같은 이름도 한글로 나오게 하기 위해서다.

캐시: .cache/gems/ (재실행 시 재다운로드 생략)
"""

import gzip
import json
import re
import sys
import urllib.request
from datetime import date
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
CACHE = ROOT / ".cache" / "gems"
OUT = ROOT / "app" / "src" / "main" / "assets" / "poe2_kr_i18n.json"
UA = (
    "Mozilla/5.0 (Linux; Android 14; SM-S918N) AppleWebKit/537.36 "
    "(KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
)

# 현재 0.5 목록. (파일 이름, 주소)
CURRENT = [
    ("kr_skill", "https://poe2db.tw/kr/Skill_Gems"),
    ("us_skill", "https://poe2db.tw/us/Skill_Gems"),
    ("kr_support", "https://poe2db.tw/kr/Support_Gems"),
    ("us_support", "https://poe2db.tw/us/Support_Gems"),
    ("kr_spirit", "https://poe2db.tw/kr/Spirit_Gems"),
    ("us_spirit", "https://poe2db.tw/us/Spirit_Gems"),
]

# 0.1 목록(PoB 샘플 빌드에 남아 있는 예전 젬 이름). 웨이백 머신 사본.
# 예전 페이지는 행마다 data-tags 끝에 영문 이름을 함께 적어 두어 한 장으로 짝이 맞는다.
ARCHIVE = [
    ("old_kr_support", "https://web.archive.org/web/20250113163617id_/https://poe2db.tw/kr/Support_Gems"),
    ("old_kr_skill", "https://web.archive.org/web/20250113174700id_/https://poe2db.tw/kr/Skill_Gems"),
]

LINK = re.compile(r'href="/[a-z]{2}/([^"]+)"[^>]*>([^<]*)</a>')
HANGUL = re.compile(r"[가-힣]")


def fetch(name, url):
    CACHE.mkdir(parents=True, exist_ok=True)
    path = CACHE / f"{name}.html"
    if not path.exists() or path.stat().st_size < 20000:
        req = urllib.request.Request(url, headers={"User-Agent": UA})
        with urllib.request.urlopen(req, timeout=120) as resp:
            path.write_bytes(resp.read())
    raw = path.read_bytes()
    # 웨이백 머신 사본은 압축된 채로 오기도 한다.
    if raw[:2] == b"\x1f\x8b":
        raw = gzip.decompress(raw)
        path.write_bytes(raw)
    return raw.decode("utf-8", errors="replace")


def rows_current(html):
    """현재 페이지: <tr data-filters="태그… 영문이름"> 안의 두 번째 링크가 이름."""
    out = {}
    for part in html.split('<tr data-filters="')[1:]:
        _, _, body = part.partition('">')
        body = body.split("<tr")[0]
        named = [(h, re.sub("<[^>]+>", "", t).strip()) for h, t in LINK.findall(body)]
        named = [(h, t) for h, t in named if t]
        if named:
            out.setdefault(named[0][0], named[0][1])
    return out


def rows_archive(html):
    """0.1 페이지: <tr data-tags='태그 영문이름'> 안의 두 번째 링크가 한글 이름.

    돌려주는 값은 슬러그 → (영문 이름, 한글 이름).
    """
    out = {}
    for part in re.split(r"<tr data-tags='", html)[1:]:
        tags, _, body = part.partition("'>")
        body = body.split("<tr")[0]
        named = [(h, re.sub("<[^>]+>", "", t).strip()) for h, t in LINK.findall(body)]
        named = [(h, t) for h, t in named if t]
        if not named:
            continue
        tail = re.search(r"([A-Za-z][A-Za-z0-9'\-\. ]*)$", tags)
        en = tail.group(1).strip() if tail else ""
        out.setdefault(named[0][0], (en, named[0][1]))
    return out


def pair(kr, us):
    """같은 슬러그의 영문 이름과 한글 이름을 짝짓는다."""
    out = {}
    for slug, en in us.items():
        ko = kr.get(slug)
        if not en or not ko:
            continue
        if not HANGUL.search(ko) or ko.strip() == en.strip():
            continue
        out[en.strip()] = ko.strip()
    return out


def main():
    pages = {}
    for name, url in CURRENT:
        pages[name] = rows_current(fetch(name, url))
    for name, url in ARCHIVE:
        try:
            pages[name] = rows_archive(fetch(name, url))
        except Exception as exc:  # 사본을 못 받아도 현재 목록만으로 진행한다.
            print(f"예전 목록 {name} 건너뜀: {exc}")
            pages[name] = {}

    gems = {}
    for prefix in ("skill", "support", "spirit"):
        gems.update(pair(pages.get(f"kr_{prefix}", {}), pages.get(f"us_{prefix}", {})))
    # 지금 이름이 먼저다. 예전 이름은 비어 있는 것만 채운다.
    for page in (pages.get("old_kr_skill", {}), pages.get("old_kr_support", {})):
        for en, ko in page.values():
            if en and ko and HANGUL.search(ko) and ko.strip() != en.strip():
                gems.setdefault(en.strip(), ko.strip())

    if len(gems) < 500:
        print(f"젬 이름이 너무 적다({len(gems)}). 페이지 구조가 바뀌었는지 확인한다.")
        return 1

    raw = json.loads(OUT.read_text(encoding="utf-8"))
    raw["gems"] = sorted(gems.items())
    OUT.write_text(json.dumps(raw, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")
    print(f"현재 목록: 스킬 {len(pages.get('kr_skill', {}))} · 보조 {len(pages.get('kr_support', {}))} · "
          f"Spirit {len(pages.get('kr_spirit', {}))}")
    print(f"예전 목록: 스킬 {len(pages.get('old_kr_skill', {}))} · 보조 {len(pages.get('old_kr_support', {}))}")
    print(f"젬 사전 {len(gems)}종 · 저장 {OUT} ({OUT.stat().st_size // 1024}KB, {date.today().isoformat()})")
    return 0


if __name__ == "__main__":
    sys.exit(main())
