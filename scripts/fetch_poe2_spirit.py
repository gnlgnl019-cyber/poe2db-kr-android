#!/usr/bin/env python3
"""정신력을 점유하는 PoE2 스킬을 poe2db.tw/kr에서 전수로 읽어 앱용 자료로 저장한다.

    python3 scripts/fetch_poe2_spirit.py

결과: app/src/main/assets/poe2_spirit_kr.json
      [{ "slug", "en", "ko", "kind", "cls", "spirit", "grade", "attribute",
         "tags":[…], "text", "lines":[…], "energy", "source" }]

`kind`는 두 가지다.
  - "spirit": 정신력을 점유해 지속 버프·효과를 주는 스킬 (바람의 무희, 단련, 대마법사 …)
  - "meta"  : 정신력을 점유하고, 조건에 맞춰 에너지를 모아 소켓된 다른 스킬을 발동하는 스킬
              (치명타 시 시전, 감전 시 시전, 방어 기원 …)

목록은 poe2db의 Skill Gems·Spirit Gems 페이지에서 모든 젬을 모은 뒤, 젬 페이지의
`Type` 표시에 HasReservation이 있는 것만 남겨 만든다. 점유량은 페이지의 `점유: N 정신력`,
발동 조건·에너지 획득은 효과 문장에서 그대로 옮긴다.
캐시: .cache/spirit2/
"""
import json
import re
import sys
import urllib.request
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
CACHE = ROOT / ".cache" / "spirit2"
OUT = ROOT / "app" / "src" / "main" / "assets" / "poe2_spirit_kr.json"
I18N = ROOT / "app" / "src" / "main" / "assets" / "poe2_kr_i18n.json"
UA = (
    "Mozilla/5.0 (Linux; Android 14; SM-S918N) AppleWebKit/537.36 "
    "(KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
)
BASE = "https://poe2db.tw/kr/"

NAV = {
    "Items", "Gem", "Skill_Gems", "Support_Gems", "Spirit_Gems", "Lineage_Supports",
    "Modifiers", "Keywords", "Desecrated_Modifiers", "Crafting", "Quest",
    "Ascendancy_class", "Act", "Waystones", "patreon",
}

TAG_STRIP = re.compile(r"<[^>]+>")
PICK = re.compile(r"\[([^\]|]*)\|([^\]]*)\]")
SPACES = re.compile(r"\s+")


def fetch(slug, name):
    CACHE.mkdir(parents=True, exist_ok=True)
    path = CACHE / f"{name}.html"
    if not path.exists() or path.stat().st_size < 3000:
        req = urllib.request.Request(BASE + slug, headers={"User-Agent": UA})
        with urllib.request.urlopen(req, timeout=60) as resp:
            path.write_bytes(resp.read())
    return path.read_text(encoding="utf-8", errors="replace")


def clean(text):
    t = TAG_STRIP.sub("", text or "")
    t = (
        t.replace("&nbsp;", " ").replace("&amp;", "&").replace("&quot;", '"')
        .replace("&#39;", "'").replace("&lt;", "<").replace("&gt;", ">")
    )
    t = PICK.sub(lambda m: (m.group(2) or m.group(1)).strip(), t)
    return SPACES.sub(" ", t).strip()


def list_slugs(html):
    """목록 페이지 → 젬 슬러그 집합"""
    found = set(re.findall(r'href="/kr/([A-Za-z0-9_%\-\.]+)"', html))
    return {s for s in found if s not in NAV}


def parse(html):
    """젬 페이지 → 이름·분류·점유량·태그·효과 문장"""
    flat = SPACES.sub(" ", TAG_STRIP.sub(" ", html))

    name = ""
    cls = ""
    found = re.search(r"BaseType (.+?) Class (.+?) TargetTypes", flat)
    if found:
        name = found.group(1).strip()
        cls = found.group(2).strip()

    flags = ""
    found = re.search(r"Type ([^<]{0,300}?)ItemType", flat)
    if found:
        flags = found.group(1)

    # 젬 머리(아이콘·태그·점유·요구 사항)만 잘라 쓴다. 페이지 아래쪽 다른 젬의 표시와 섞이지 않게.
    head = html
    cut = html.find('class="itemboximage"')
    if cut > 0:
        head = html[:cut]
    header = re.search(r'<div class="itemHeader.*?</div>\s*</div>', head, re.S)
    if header:
        head = header.group(0)

    tags = []
    for part in re.findall(r'class="GemTags[^"]*"[^>]*>([^<]+)</a>', head + html[html.find('ActiveSkillsCode'):][:0]):
        value = clean(part)
        if value and value not in tags:
            tags.append(value)
    if not tags:
        found = re.search(r'<div class="itemName typeLine">(.*?)</div>', html, re.S)
        if found:
            for part in re.split(r",", clean(found.group(1))):
                value = part.strip()
                if value and value not in tags:
                    tags.append(value)

    # 점유: 30 정신력 / 점유: (60 — 20) 정신력 → 20레벨 값(범위의 끝)을 쓴다
    spirit = 0
    for pattern in (
        r"점유:\s*(?:\((\d+)\s*[—–-]\s*(\d+)\)|(\d+))\s*정신력",
        r"Reservation:\s*(?:\((\d+)\s*[—–-]\s*(\d+)\)|(\d+))\s*Spirit",
        r"정신력\s*(?:\((\d+)\s*[—–-]\s*(\d+)\)|(\d+))\s*점유",
    ):
        found = re.search(pattern, flat)
        if found:
            spirit = int(found.group(2) or found.group(3) or 0)
            if spirit > 0:
                break
    grade = 0
    found = re.search(r"등급:\s*<span[^>]*>\s*([0-9]+)", html)
    if found:
        grade = int(found.group(1))

    attribute = ""
    found = re.search(r"Attribute /\d+\s*(힘|지능|민첩)", clean(html[: html.find('ActiveSkillsCode')] if 'ActiveSkillsCode' in html else html))
    if found:
        attribute = found.group(1)

    text = ""
    found = re.search(r'<div class="secDescrText">(.*?)</div>', html, re.S)
    if found:
        text = clean(found.group(1))

    lines = []
    for part in re.findall(r'<div class="explicitMod">(.*?)</div>', html, re.S):
        line = clean(part)
        if line and line not in lines:
            lines.append(line)

    return {
        "name": name or clean((re.search(r'<span class="lc">([^<]+)</span>', html) or [None, ""])[1]),
        "tags": tags,
        "spirit": spirit,
        "grade": grade,
        "cls": cls,
        "flags": flags,
        "attribute": attribute,
        "text": text,
        "lines": lines,
    }


def is_reserving(info):
    """정신력을 점유하는 스킬인지 (poe2db의 Type 표시로 판단)"""
    if "HasReservation" in info["flags"]:
        return True
    if info["spirit"] > 0:
        return True
    # 점유 문장이 효과에만 적힌 경우 (예: 신성 모독 — '저주 하나당 정신력 60 점유')
    return any("정신력" in line and "점유" in line for line in info["lines"])


def is_meta(info):
    if "메타" in info["tags"]:
        return True
    if info["cls"] == "Meta Skill Gem":
        return True
    if "Meta" in info["flags"]:
        return True
    return any("발동" in tag for tag in info["tags"])


def energy_hint(info):
    """메타 젬의 발동 조건·에너지 획득 문장을 한 줄로"""
    if not is_meta(info):
        return ""
    picked = [line for line in info["lines"] if "에너지" in line]
    if not picked:
        picked = [line for line in info["lines"] if "발동" in line]
    return picked[0] if picked else ""


def korean_name_map():
    """영문 이름 → 한글 이름 (assets/poe2_kr_i18n.json)"""
    if not I18N.is_file():
        return {}
    data = json.loads(I18N.read_text(encoding="utf-8"))
    rows = data.get("gems") or []
    out = {}
    for row in rows:
        if isinstance(row, list) and len(row) >= 2:
            out[row[1].strip()] = row[0].strip()
    return out


def main():
    # 1) 모든 스킬 젬 슬러그 (Skill Gems + Spirit Gems 목록)
    slugs = set()
    for page, cache_name in (
        ("Skill_Gems", "Skill_Gems"),
        ("Support_Gems", "Support_Gems"),
        ("Spirit_Gems", "Spirit_Gems"),
    ):
        try:
            html = fetch(page, cache_name)
            slugs |= list_slugs(html)
        except Exception as exc:  # noqa: BLE001
            print(f"[WARN] {page} 목록을 읽지 못했습니다: {exc}", file=sys.stderr)
    print(f"[INFO] 젬 페이지 {len(slugs)}개를 확인합니다")

    # 2) 젬 페이지를 병렬로 받아 점유 스킬만 남긴다
    def job(slug):
        try:
            html = fetch(slug, f"g_{slug}")
        except Exception:  # noqa: BLE001
            return None
        info = parse(html)
        if not info["name"] or not is_reserving(info):
            return None
        info["slug"] = slug
        return info

    with ThreadPoolExecutor(max_workers=8) as pool:
        results = [r for r in pool.map(job, sorted(slugs)) if r]

    ko_to_en = korean_name_map()

    rows = []
    for info in results:
        ko = info["name"]
        en = ko_to_en.get(ko, "")
        if not en:
            # i18n에 없는 이름은 내부 Id(영문)를 쓴다.
            en = info["slug"].replace("_", " ")
        rows.append(
            {
                "slug": info["slug"],
                "en": en,
                "ko": ko,
                "kind": "meta" if is_meta(info) else "spirit",
                "cls": info["cls"],
                "spirit": info["spirit"],
                "grade": info["grade"],
                "attribute": info["attribute"],
                "tags": info["tags"],
                "text": info["text"],
                "lines": info["lines"],
                "energy": energy_hint(info),
                "source": BASE + info["slug"],
            }
        )

    rows.sort(key=lambda r: (0 if r["kind"] == "meta" else 1, r["ko"]))
    OUT.write_text(json.dumps(rows, ensure_ascii=False, indent=1) + "\n", encoding="utf-8")

    meta = [r for r in rows if r["kind"] == "meta"]
    spirit = [r for r in rows if r["kind"] == "spirit"]
    priced = [r for r in rows if r["spirit"] > 0]
    print(f"[INFO] 점유 스킬 {len(rows)}종 — 정신력 젬 {len(spirit)} · 메타 젬 {len(meta)}")
    print(f"[INFO] 점유량이 적힌 젬 {len(priced)}종 · 비어 있는 젬 {len(rows) - len(priced)}종")
    for row in rows:
        print(f"  {row['kind']:6s} {row['spirit']:>3} {row['ko']} ({row['en']})")


if __name__ == "__main__":
    main()
