#!/usr/bin/env python3
"""poe2db.tw 한국어 페이지를 긁어 앱에 넣을 카탈로그 JSON을 만든다.

    python3 scripts/fetch_poe2db.py

결과: app/src/main/assets/poe2db_kr.json
캐시: .cache/poe2db/ (재실행 시 재다운로드 생략)
"""

import hashlib
import json
import re
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from concurrent.futures import ThreadPoolExecutor
from datetime import date
from pathlib import Path

from lxml import html as lxml_html

ROOT = Path(__file__).resolve().parent.parent
CACHE = ROOT / ".cache" / "poe2db"
OUT = ROOT / "app" / "src" / "main" / "assets" / "poe2db_kr.json"
BASE = "https://poe2db.tw/kr/"
UA = (
    "Mozilla/5.0 (Linux; Android 14; SM-S918N) AppleWebKit/537.36 "
    "(KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
)

def fetch(url, tries=3):
    CACHE.mkdir(parents=True, exist_ok=True)
    path = CACHE / (hashlib.sha1(url.encode()).hexdigest() + ".html")
    if path.exists() and path.stat().st_size > 0:
        return path.read_text(encoding="utf-8", errors="replace")
    last = None
    for attempt in range(tries):
        try:
            req = urllib.request.Request(url, headers={"User-Agent": UA})
            with urllib.request.urlopen(req, timeout=25) as resp:
                body = resp.read().decode("utf-8", "replace")
            path.write_text(body, encoding="utf-8")
            return body
        except (urllib.error.URLError, TimeoutError, OSError) as exc:
            last = exc
            time.sleep(0.8 * (attempt + 1))
    raise RuntimeError(f"download failed: {url} ({last})")


def parse(body):
    parser = lxml_html.HTMLParser(encoding="utf-8")
    return lxml_html.document_fromstring(body.encode("utf-8"), parser=parser)


def clean(text):
    if not text:
        return ""
    text = text.replace("\xa0", " ")
    return re.sub(r"\s+", " ", text).strip()


def trim(text, limit):
    text = clean(text)
    return text if len(text) <= limit else text[: limit - 1].rstrip() + "…"


TAG_KO = {
    "Weapon 고유": "무기",
    "Armour 고유": "방어구",
    "Other 고유": "기타",
    "Cultivated Uniques": "특수",
    "Keywords": "키워드",
    "화폐 아이템": "화폐",
}

# 아이템 종류별 기본 아이템 페이지 (슬러그, 칩 태그)
ITEM_CLASS_PAGES = {
    "weapon": [
        ("Bows", "활"), ("Crossbows", "쇠뇌"), ("Spears", "창"),
        ("Quarterstaves", "육척봉"), ("Staves", "지팡이"), ("Wands", "마법봉"),
        ("Sceptres", "셉터"), ("Daggers", "단검"), ("Claws", "클로"),
        ("Flails", "도리깨"), ("One_Hand_Swords", "한손 검"), ("Two_Hand_Swords", "양손 검"),
        ("One_Hand_Axes", "한손 도끼"), ("Two_Hand_Axes", "양손 도끼"),
        ("One_Hand_Maces", "한손 철퇴"), ("Two_Hand_Maces", "양손 철퇴"),
        ("Shields", "방패"), ("Foci", "집중구"), ("Quivers", "화살통"),
        ("Talismans", "부적"), ("Traps", "덫"),
    ],
    "armour": [
        ("Body_Armours", "갑옷"), ("Helmets", "투구"),
        ("Gloves", "장갑"), ("Boots", "장화"),
    ],
    "jewellery": [
        ("Rings", "반지"), ("Amulets", "목걸이"), ("Belts", "허리띠"),
    ],
    "rune": [
        ("Rune", "룬"),
    ],
}


def slug_name(url):
    slug = url.rsplit("/", 1)[-1]
    slug = urllib.parse.unquote(slug).replace("_", " ").strip()
    return slug if re.fullmatch(r"[A-Za-z0-9'’,.\- ]+", slug) else ""


def absolute(href):
    if not href:
        return ""
    if href.startswith("http"):
        return href
    if href.startswith("/"):
        return "https://poe2db.tw" + href
    return BASE + href.lstrip("./")


def norm_icon(src):
    if not src:
        return ""
    src = src.strip()
    if src.startswith("//"):
        return "https:" + src
    if src.startswith("http") or src.startswith("/"):
        return src
    return "https://poe2db.tw/" + src.lstrip("./")


def section_title(node):
    heads = node.xpath('.//h5[contains(@class,"card-header")]')
    if not heads:
        return ""
    title = clean(heads[0].text_content())
    return re.sub(r"\s*/\s*\d+\s*$", "", title)


def card_summary(card):
    parts = []
    for div in card.xpath(
        './/div[contains(@class,"secDescrText") or contains(@class,"explicitMod") '
        'or contains(@class,"implicitMod") or contains(@class,"fontinRegular") '
        'or contains(@class,"property")]'
    ):
        part = clean(div.text_content())
        if part and part not in parts:
            parts.append(part)
        if len(parts) >= 4:
            break
    return parts


def extract_card(card):
    icons = card.xpath('.//div[contains(@class,"flex-shrink-0")]//img/@src')
    icon = norm_icon(icons[0]) if icons else ""

    unique_name = card.xpath('.//span[@class="uniqueName"]')
    type_line = card.xpath('.//span[@class="uniqueTypeLine"]')

    name, url = "", ""
    anchors = card.xpath('.//div[contains(@class,"flex-grow-1")]//a[@href]')
    for anchor in anchors:
        label = clean(anchor.text_content())
        if label:
            name, url = label, absolute(anchor.get("href"))
            break
    if unique_name:
        name = clean(unique_name[0].text_content())

    parts = card_summary(card)
    if type_line:
        meta = clean(type_line[0].text_content())
        body = [p for p in parts if p != meta]
    else:
        meta = ""
        body = parts
    if body and meta and body[0].startswith(meta):
        body[0] = body[0][len(meta):].strip(" ,·")
        body = [b for b in body if b]
    summary = trim(" · ".join(body[:2]), 240)

    return {
        "name": name,
        "nameEn": "",
        "cat": "",
        "catLabel": "",
        "tags": [],
        "summary": summary,
        "meta": meta,
        "stats": [clean(p) for p in parts[1:4]] if len(parts) > 1 else [],
        "icon": icon,
        "url": url,
    }


def parse_gem_page(cat, label, slug):
    doc = parse(fetch(BASE + slug))
    items = []
    for row in doc.xpath("//tr[@data-filters]"):
        anchors = row.xpath(".//td[2]//a[@href]")
        if not anchors:
            continue
        anchor = anchors[0]
        name = re.sub(r"\s*\(\d+\)\s*$", "", clean(anchor.text_content()))
        if not name:
            continue
        imgs = row.xpath(".//img/@src")
        tags = [clean(t) for t in row.xpath('.//div[contains(@class,"gem_tags")]//a/text()')]
        item = {
            "name": name,
            "nameEn": "",
            "cat": cat,
            "catLabel": label,
            "tags": [t for t in tags if t][:5],
            "summary": "",
            "meta": "",
            "stats": [],
            "icon": norm_icon(imgs[0]) if imgs else "",
            "url": absolute(anchor.get("href")),
            "hover": anchor.get("data-hover") or "",
        }
        items.append(item)
    return items


def enrich_gem(item):
    hover = item.pop("hover", "")
    if not hover:
        return item
    try:
        doc = parse(fetch(hover))
    except RuntimeError:
        return item

    descr = doc.xpath('.//div[contains(@class,"secDescrText")]')
    mods = [clean(m.text_content()) for m in doc.xpath('.//div[contains(@class,"explicitMod")]')]
    props = [clean(p.text_content()) for p in doc.xpath('.//div[@class="property"]')]
    en = doc.xpath('.//div[@class="content"]/div[not(@*)]')
    en_name = clean(en[-1].text_content()) if en else ""

    item["nameEn"] = en_name if re.fullmatch(r"[A-Za-z0-9'’,\- ]+", en_name or "") else ""
    item["summary"] = trim(descr[0].text_content() if descr else " · ".join(mods[:2]), 240)
    item["stats"] = [m for m in mods[:3]]
    detail = [p for p in props if p and not p.startswith("등급")]
    if detail and "," in detail[0]:
        detail = detail[1:]
    item["meta"] = " · ".join(detail[:2])
    if not item["tags"]:
        item["tags"] = [clean(t) for t in doc.xpath('.//a[contains(@class,"GemTags")]/text()')][:5]
    return item


def parse_card_page(cat, label, slug):
    doc = parse(fetch(BASE + slug))
    items = []
    panes = doc.xpath('//div[contains(@class,"tab-pane")]')
    sections = panes if panes else [doc]
    seen = set()
    for pane in sections:
        pane_title = section_title(pane)
        for card in pane.xpath('.//div[@class="d-flex border-top rounded"]'):
            item = extract_card(card)
            if not item["name"] or not item["url"]:
                continue
            if item["url"] in seen:
                continue
            seen.add(item["url"])
            item["cat"] = cat
            item["catLabel"] = label
            if pane_title:
                item["tags"] = [pane_title]
            items.append(item)
    if not panes:
        for card in doc.xpath('//div[@class="d-flex border-top rounded"]'):
            item = extract_card(card)
            if not item["name"] or not item["url"] or item["url"] in seen:
                continue
            seen.add(item["url"])
            item["cat"] = cat
            item["catLabel"] = label
            items.append(item)
    return items


def parse_item_class_pages(cat, label, pages):
    """무기·방어구·장신구·룬 등 기본 아이템 페이지를 읽는다.

    각 페이지의 탭 중 이름이 '…아이템' 또는 '룬'인 것만 가져오고,
    '…고유'(유니크)·'…Ref'(영문 참조) 탭은 건너뛴다.
    """
    items = []
    seen = set()
    for slug, tag in pages:
        doc = parse(fetch(BASE + slug))
        count = 0
        for pane in doc.xpath('//div[contains(@class,"tab-pane")]'):
            title = section_title(pane)
            if not title or title.endswith("고유") or "Ref" in title:
                continue
            if title.endswith("아이템"):
                tag_name = title[: -len("아이템")].strip()
            elif title == "룬":
                tag_name = "룬"
            else:
                continue
            for card in pane.xpath('.//div[@class="d-flex border-top rounded"]'):
                item = extract_card(card)
                if not item["name"] or not item["url"] or item["url"] in seen:
                    continue
                seen.add(item["url"])
                item["cat"] = cat
                item["catLabel"] = label
                item["tags"] = [tag_name or tag]
                items.append(item)
                count += 1
        print(f"  {label}/{slug}: {count}")
    return items


def parse_ascendancy():
    body = fetch(BASE + "Ascendancy_class")
    doc = parse(body)
    items = []
    seen = set()

    class_pane = doc.xpath('//div[@id="전직Classes"]')
    if class_pane:
        for card in class_pane[0].xpath('.//div[@class="d-flex border-top rounded"]'):
            base = card.xpath('.//div[contains(@class,"flex-shrink-0")]//figure')
            if base:
                fig = base[0]
                name = clean("".join(fig.xpath(".//figcaption//text()")))
                link = fig.xpath(".//a[@href]")
                icon = fig.xpath(".//img/@src")
                if name and link:
                    url = absolute(link[0].get("href"))
                    if url not in seen:
                        seen.add(url)
                        items.append({
                            "name": name, "nameEn": "", "cat": "ascendancy", "catLabel": "전직·직업",
                            "tags": ["직업"], "summary": "", "meta": "",
                            "stats": [], "icon": norm_icon(icon[0]) if icon else "", "url": url,
                        })
            for fig in card.xpath('.//div[contains(@class,"flex-grow-1")]//figure'):
                name = clean("".join(fig.xpath(".//figcaption//text()")))
                link = fig.xpath(".//a[@href]")
                icon = fig.xpath(".//img/@src")
                if not (name and link):
                    continue
                url = absolute(link[0].get("href"))
                if url in seen:
                    continue
                seen.add(url)
                items.append({
                    "name": name, "nameEn": "", "cat": "ascendancy", "catLabel": "전직·직업",
                    "tags": ["전직"], "summary": "", "meta": "",
                    "stats": [], "icon": norm_icon(icon[0]) if icon else "", "url": url,
                })

    passive_pane = doc.xpath('//div[@id="전직패시브"]')
    if passive_pane:
        for card in passive_pane[0].xpath('.//div[@class="d-flex border-top rounded"]'):
            item = extract_card(card)
            if not item["name"] or not item["url"] or item["url"] in seen:
                continue
            seen.add(item["url"])
            item["cat"] = "ascendancy"
            item["catLabel"] = "전직·직업"
            item["tags"] = ["전직 패시브"] + item["tags"]
            items.append(item)
    return items


def main():
    started = time.time()
    items = []

    for cat, label, slug in [("skill_gem", "스킬 젬", "Skill_Gems"),
                             ("support_gem", "보조 젬", "Support_Gems")]:
        page_items = parse_gem_page(cat, label, slug)
        items.extend(page_items)
        print(f"{label}: {len(page_items)}")

    for cat, label, slug in [("unique", "유니크 아이템", "Unique_item"),
                             ("currency", "화폐", "Currency"),
                             ("keyword", "키워드", "Keywords")]:
        page_items = parse_card_page(cat, label, slug)
        items.extend(page_items)
        print(f"{label}: {len(page_items)}")

    for cat, label in [("weapon", "무기"), ("armour", "방어구"),
                       ("jewellery", "장신구"), ("rune", "룬")]:
        page_items = parse_item_class_pages(cat, label, ITEM_CLASS_PAGES[cat])
        items.extend(page_items)
        print(f"{label}: {len(page_items)}")

    gems = [i for i in items if i["cat"] in ("skill_gem", "support_gem") and i.get("hover")]
    print(f"보강할 젬: {len(gems)}")
    with ThreadPoolExecutor(max_workers=12) as pool:
        list(pool.map(enrich_gem, gems))

    asc = parse_ascendancy()
    items = [i for i in items if i["cat"] != "ascendancy"] + asc
    print(f"전직·직업: {len(asc)}")

    legacy = [i for i in items if i["cat"] == "unique"]
    for item in legacy:
        if not item["tags"]:
            item["tags"] = ["유니크"]

    seen = set()
    unique_items = []
    for item in items:
        if "DNT" in item["name"] or "[UNUSED]" in item["name"]:
            continue
        item["tags"] = [TAG_KO.get(t, t) for t in item["tags"]]
        if not item["nameEn"]:
            item["nameEn"] = slug_name(item["url"])
        key = (item["cat"], item["url"])
        if key in seen:
            continue
        seen.add(key)
        item.pop("hover", None)
        unique_items.append(item)

    counts = {}
    for item in unique_items:
        counts[item["catLabel"]] = counts.get(item["catLabel"], 0) + 1

    payload = {
        "source": "https://poe2db.tw/kr/",
        "fetchedAt": date.today().isoformat(),
        "total": len(unique_items),
        "counts": counts,
        "items": unique_items,
    }
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(json.dumps(payload, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")
    size = OUT.stat().st_size
    print(f"{OUT} · {len(unique_items)}건 · {size / 1024 / 1024:.2f}MB · {time.time() - started:.0f}s")
    print(counts)


if __name__ == "__main__":
    sys.exit(main())
