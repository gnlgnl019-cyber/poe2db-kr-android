#!/usr/bin/env python3
"""정신력을 점유하는 젬(Spirit Gems)을 전부 읽어 앱용 자료로 저장한다.

    python3 scripts/fetch_poe2_spirit.py

결과: app/src/main/assets/poe2_spirit_kr.json
      [{ "slug", "en", "ko", "type", "grade", "spirit", "tags":[…], "text", "lines":[…] }]

목록은 poe2db의 Spirit Gems 페이지에서, 점유량·효과 문장은 젬별 페이지에서 읽는다.
(전투 시뮬레이터는 이 파일의 점유량으로 정신력 예산을 맞추고, 효과 문장에서
수치를 뽑아 방어·공격 수치에 반영한다.)
캐시: .cache/gems/, .cache/spirit/
"""
import json
import re
import sys
import urllib.request
from datetime import date
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
CACHE = ROOT / ".cache" / "spirit"
GEMS = ROOT / ".cache" / "gems"
OUT = ROOT / "app" / "src" / "main" / "assets" / "poe2_spirit_kr.json"
UA = (
    "Mozilla/5.0 (Linux; Android 14; SM-S918N) AppleWebKit/537.36 "
    "(KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
)

LINK_ROW = re.compile(r"<td[^>]*>(.*?)</td>", re.S)
LINK = re.compile(r'href="/[a-z]{2}/([^"]+)"[^>]*>([^<]*)</a>')
TAG_STRIP = re.compile(r"<[^>]+>")
PICK = re.compile(r"\[([^\]|]*)\|([^\]]*)\]")


def fetch(url, name):
    CACHE.mkdir(parents=True, exist_ok=True)
    path = CACHE / f"{name}.html"
    if not path.exists() or path.stat().st_size < 2000:
        req = urllib.request.Request(url, headers={"User-Agent": UA})
        with urllib.request.urlopen(req, timeout=90) as resp:
            path.write_bytes(resp.read())
    return path.read_text(encoding="utf-8", errors="replace")


def clean(text):
    t = TAG_STRIP.sub("", text or "")
    t = t.replace("&nbsp;", " ").replace("&amp;", "&").replace("&quot;", '"')
    t = PICK.sub(lambda m: (m.group(2) or m.group(1)).strip(), t)
    return re.sub(r"\s+", " ", t).strip()


def slugs(page):
    """Spirit Gems 목록 → {슬러그: 이름}"""
    out = {}
    for row in re.findall(r"<tr[^>]*>(.*?)</tr>", page, re.S):
        cells = LINK_ROW.findall(row)
        if len(cells) < 3:
            continue
        for slug, name in LINK.findall(cells[2]):
            name = clean(name)
            if name:
                out.setdefault(slug, name)
    return out


def parse(html):
    """젬 페이지 → 점유량·유형·태그·효과 문장"""
    spirit = 0
    found = re.search(r"점유:\s*<span[^>]*>([^<]*)</span>", html)
    if found:
        number = re.search(r"(\d+)", clean(found.group(1)))
        if number:
            spirit = int(number.group(1))
    grade = 0
    found = re.search(r"등급:\s*<span[^>]*>([^<]*)</span>", html)
    if found:
        number = re.search(r"(\d+)", clean(found.group(1)))
        if number:
            grade = int(number.group(1))
    tags = []
    head = html.find('class="Stats"')
    if head > 0:
        block = html[head:head + 4000]
        for _, name in LINK.findall(block):
            name = clean(name)
            if name and name not in tags:
                tags.append(name)
    gem_type = ""
    found = re.search(r'<span class="ItemType align-middle">([^<]*)</span>', html)
    if found:
        gem_type = clean(found.group(1))
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
        "spirit": spirit,
        "grade": grade,
        "type": gem_type,
        "tags": tags,
        "text": text,
        "lines": lines,
    }


def main():
    ko_page = (GEMS / "kr_spirit.html").read_text(encoding="utf-8", errors="replace")
    us_page = (GEMS / "us_spirit.html").read_text(encoding="utf-8", errors="replace")
    ko_names = slugs(ko_page)
    us_names = slugs(us_page)

    rows = []
    for slug, ko_name in ko_names.items():
        html = fetch(f"https://poe2db.tw/kr/{slug}", f"kr_{slug}")
        info = parse(html)
        info["slug"] = slug
        info["ko"] = ko_name
        info["en"] = us_names.get(slug, "")
        rows.append(info)
        print(f"  {ko_name:<14} {info['en']:<26} 정신력 {info['spirit']:>3} · 문장 {len(info['lines'])}")

    rows.sort(key=lambda row: (row["spirit"], row["ko"]))
    OUT.write_text(json.dumps(rows, ensure_ascii=False, indent=1), encoding="utf-8")
    print(f"저장: {OUT} ({OUT.stat().st_size // 1024}KB) · 젬 {len(rows)}")


if __name__ == "__main__":
    sys.exit(main())
