#!/usr/bin/env python3
"""poe.ninja 시세 응답과 poe2db.tw 한국어 페이지를 대조해 앱용 한글 사전을 만든다.

    python3 scripts/fetch_poe2_i18n.py

결과: app/src/main/assets/poe2_kr_i18n.json
      (아이템명·베이스 타입·아이템 클래스·카테고리·아이콘·속성 부여 문장)

동작:
  1. 내장 카탈로그(poe2db_kr.json)에서 이미 확보한 영문→한글 이름·아이콘을 먼저 채운다.
  2. poe.ninja 시세 API가 실제로 돌려주는 품목 이름/베이스 타입 중 사전에 없는 것만
     poe2db.tw/kr/<영문 슬러그> 페이지의 아이템 팝업에서 한글 이름·베이스·아이콘·옵션을 뽑는다.
  3. 고유 아이템은 poe.ninja 영문 옵션과 poe2db 한글 옵션을 순서대로 짝지어
     숫자를 # 으로 지운 문장 틀(template) 사전을 만든다. 희귀/마법 아이템 옵션도 같은 틀로 번역된다.

캐시: .cache/i18n/ (재실행 시 재다운로드 생략)
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

ROOT = Path(__file__).resolve().parent.parent
CACHE = ROOT / ".cache" / "i18n"
ASSETS = ROOT / "app" / "src" / "main" / "assets"
CATALOG = ASSETS / "poe2db_kr.json"
OUT = ASSETS / "poe2_kr_i18n.json"
BASE = "https://poe2db.tw/kr/"
API = "https://poe.ninja/poe2/api/economy"
LEAGUE = "Runes of Aldur"
UA = (
    "Mozilla/5.0 (Linux; Android 14; SM-S918N) AppleWebKit/537.36 "
    "(KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
)

EXCHANGE_TYPES = [
    "Currency", "Runes", "Essences", "SoulCores", "Fragments",
    "UncutGems", "Ritual", "Delirium", "Breach",
]
ITEM_TYPES = ["UniqueWeapons", "UniqueArmours", "UniqueAccessories", "UniqueJewels"]

# 아이템 클래스(영문) → 한글. PoB "Item Class:" 줄과 시세 카테고리에 함께 쓴다.
CLASS_KO = {
    "Bow": "활", "Crossbow": "쇠뇌", "Staff": "지팡이", "Wand": "마법봉",
    "Sceptre": "셉터", "Quarterstaff": "육척봉", "Spear": "창", "Sword": "검",
    "Axe": "도끼", "Mace": "철퇴", "Dagger": "단검", "Claw": "클로", "Flail": "도리깨",
    "Shield": "방패", "Buckler": "버클러", "Quiver": "화살통", "Focus": "집중구",
    "Talisman": "부적", "Helmet": "투구", "Body Armour": "갑옷", "Gloves": "장갑",
    "Boots": "장화", "Amulet": "목걸이", "Ring": "반지", "Belt": "허리띠",
    "Jewel": "주얼", "Flask": "플라스크", "Charm": "부적", "Trap": "덫",
    "One Hand Sword": "한손 검", "Two Hand Sword": "양손 검",
    "One Hand Axe": "한손 도끼", "Two Hand Axe": "양손 도끼",
    "One Hand Mace": "한손 철퇴", "Two Hand Mace": "양손 철퇴",
}

# poe.ninja 카테고리 문자열("[Sword|One Hand Sword]") 안쪽 이름 → 한글
CATEGORY_KO = {
    "Sword": "검", "Bow": "활", "Crossbow": "쇠뇌", "Staff": "지팡이", "Wand": "마법봉",
    "Sceptre": "셉터", "Quarterstaff": "육척봉", "Spear": "창", "Dagger": "단검",
    "Claw": "클로", "Flail": "도리깨", "Shield": "방패", "Buckler": "버클러",
    "Quiver": "화살통", "Focus": "집중구", "Talisman": "부적", "Jewel": "주얼",
    "Helmet": "투구", "Body Armour": "갑옷", "Gloves": "장갑", "Boots": "장화",
    "Amulet": "목걸이", "Ring": "반지", "Belt": "허리띠", "Flask": "플라스크",
    "Currency": "화폐", "Runes": "룬", "Essences": "에센스", "Fragments": "조각",
    "Ritual": "징조", "Delirium": "감정", "Breach": "균열", "UncutGems": "미가공 젬",
    "Expedition": "탐험", "Vaal": "바알",
}

# 희귀/마법 장비에 자주 붙는 옵션 문장 틀. poe2db 고유 아이템에서 배우지 못하는
# 기본 접사까지 덮기 위해 손으로 채운다. 숫자는 # 으로 표기한다.
MANUAL_MODS = {
    "+# to Strength": "힘 +#",
    "+# to Dexterity": "민첩 +#",
    "+# to Intelligence": "지능 +#",
    "+# to all Attributes": "모든 능력치 +#",
    "+# to maximum Life": "생명력 최대치 +#",
    "+# to maximum Mana": "마나 최대치 +#",
    "+# to maximum Energy Shield": "에너지 보호막 최대치 +#",
    "+# to Armour": "방어도 +#",
    "+# to Evasion Rating": "회피도 +#",
    "+#% to Fire Resistance": "화염 저항 +#%",
    "+#% to Cold Resistance": "냉기 저항 +#%",
    "+#% to Lightning Resistance": "번개 저항 +#%",
    "+#% to Chaos Resistance": "카오스 저항 +#%",
    "+#% to all Elemental Resistances": "모든 원소 저항 +#%",
    "#% increased Physical Damage": "물리 피해 #% 증가",
    "#% increased Attack Speed": "공격 속도 #% 증가",
    "#% increased Cast Speed": "시전 속도 #% 증가",
    "#% increased Movement Speed": "이동 속도 #% 증가",
    "#% increased Critical Hit Chance": "치명타 확률 #% 증가",
    "#% increased Critical Damage Bonus": "치명타 피해 보너스 #% 증가",
    "#% increased Spell Damage": "주문 피해 #% 증가",
    "#% increased Projectile Damage": "투사체 피해 #% 증가",
    "#% increased Fire Damage": "화염 피해 #% 증가",
    "#% increased Cold Damage": "냉기 피해 #% 증가",
    "#% increased Lightning Damage": "번개 피해 #% 증가",
    "#% increased Chaos Damage": "카오스 피해 #% 증가",
    "#% increased Elemental Damage": "원소 피해 #% 증가",
    "#% increased Armour": "방어도 #% 증가",
    "#% increased Evasion Rating": "회피도 #% 증가",
    "#% increased Energy Shield": "에너지 보호막 #% 증가",
    "#% increased Stun Threshold": "기절 한계치 #% 증가",
    "#% increased Mana Regeneration Rate": "마나 재생 속도 #% 증가",
    "#% increased Life Regeneration Rate": "생명력 재생 속도 #% 증가",
    "Adds # to # Physical Damage": "물리 피해 #~# 추가",
    "Adds # to # Fire Damage": "화염 피해 #~# 추가",
    "Adds # to # Cold Damage": "냉기 피해 #~# 추가",
    "Adds # to # Lightning Damage": "번개 피해 #~# 추가",
    "Adds # to # Chaos Damage": "카오스 피해 #~# 추가",
    "#% of Physical Damage Converted to Fire Damage": "물리 피해의 #%를 화염 피해로 전환",
    "#% of Physical Damage Converted to Cold Damage": "물리 피해의 #%를 냉기 피해로 전환",
    "#% of Physical Damage Converted to Lightning Damage": "물리 피해의 #%를 번개 피해로 전환",
    "Gain #% of Physical Damage as Extra Fire Damage": "물리 피해의 #%를 추가 화염 피해로 획득",
    "Gain #% of Physical Damage as Extra Cold Damage": "물리 피해의 #%를 추가 냉기 피해로 획득",
    "Gain #% of Physical Damage as Extra Lightning Damage": "물리 피해의 #%를 추가 번개 피해로 획득",
    "+#% Chance to Block": "막기 확률 +#%",
    "#% chance to Avoid being Stunned": "기절 회피 확률 #%",
    "#% increased Quantity of Items found": "발견하는 아이템 수량 #% 증가",
    "#% increased Rarity of Items found": "발견하는 아이템 희귀도 #% 증가",
    "Cannot be Frozen": "빙결될 수 없음",
    "Cannot be Shocked": "감전될 수 없음",
    "Cannot be Ignited": "점화될 수 없음",
    "Cannot be Poisoned": "중독될 수 없음",
    "Cannot be Stunned": "기절할 수 없음",
    "Cannot be Knocked Back": "밀려날 수 없음",
    "Corrupted": "타락",
    "Quality: +#%": "품질: +#%",
    "Has # Augment Sockets (Hidden)": "증강 홈 #개 (숨김)",
    "Has # Rune Sockets": "룬 홈 #개",
    "Has # Jewel Socket (Hidden)": "주얼 홈 #개 (숨김)",
    "[# Random Socket Modifiers]": "[무작위 홈 속성 #개]",
    "Has # Charm Slots": "호신부 슬롯 #개",
    "Level: #": "레벨 #",
    "Requires Level #": "레벨 # 필요",
    "#% increased Stun Buildup": "기절 축적 #% 증가",
    "#% increased Ailment Magnitude": "상태 이상 강도 #% 증가",
    "#% increased Skill Effect Duration": "스킬 효과 지속시간 #% 증가",
    "+#% to Critical Damage Bonus": "치명타 피해 보너스 +#%",
    "#% increased Damage": "피해 #% 증가",
    "#% increased Area of Effect": "효과 범위 #% 증가",
    "Gain #% of Damage as Extra Fire Damage": "피해의 #%를 추가 화염 피해로 획득",
    "Gain #% of Damage as Extra Cold Damage": "피해의 #%를 추가 냉기 피해로 획득",
    "Gain #% of Damage as Extra Lightning Damage": "피해의 #%를 추가 번개 피해로 획득",
}

TAG = re.compile(r"<[^>]+>")
BR = re.compile(r"<br\s*/?>", re.I)
NUM = re.compile(r"\d+(?:\.\d+)?")
PICK = re.compile(r"\[([^\]|]*)\|([^\]]*)\]")
PLAIN = re.compile(r"\[([^\]]*)\]")


def clean_tags(text):
    """poe.ninja의 [표시|실제] 태그를 화면에 보이는 쪽으로 바꾼다."""
    if not text:
        return ""
    out = PICK.sub(lambda m: (m.group(2) or m.group(1)).strip(), text)
    out = PLAIN.sub(lambda m: m.group(1).strip(), out)
    return out


def tidy_ko(text):
    """게임 표기(10%, (4—6))에 맞춰 한글 옵션 문장의 띄어쓰기를 다듬는다."""
    t = text.replace("\u2014", "—").replace("\u2013", "—")
    t = re.sub(r"\(\s*(\d+(?:\.\d+)?)\s*[—~-]\s*(\d+(?:\.\d+)?)\s*\)", r"(\1—\2)", t)
    t = re.sub(r"(\d)\s+—\s+(\d)", r"\1—\2", t)
    t = re.sub(r"\s*~\s*", "~", t)
    t = re.sub(r"(\d)\s+%", r"\1%", t)
    t = re.sub(r"\s+([,.])", r"\1", t)
    return re.sub(r"\s+", " ", t).strip()


def cache_path(name):
    CACHE.mkdir(parents=True, exist_ok=True)
    key = hashlib.sha1(name.encode()).hexdigest()[:16]
    return CACHE / f"{key}.html"


def fetch(url, tries=3, force=False):
    path = cache_path(url)
    if path.exists() and path.stat().st_size > 0 and not force:
        return path.read_text(encoding="utf-8", errors="replace")
    last = None
    for attempt in range(tries):
        try:
            req = urllib.request.Request(url, headers={"User-Agent": UA})
            with urllib.request.urlopen(req, timeout=25) as resp:
                body = resp.read().decode("utf-8", "replace")
            if resp.status == 200 and len(body) > 2000:
                path.write_text(body, encoding="utf-8")
                return body
            last = f"HTTP {resp.status}"
        except (urllib.error.URLError, TimeoutError, OSError) as exc:
            last = exc
        time.sleep(0.6 * (attempt + 1))
    return last if isinstance(last, str) and last.startswith("HTTP") else None


def fetch_json(url):
    CACHE.mkdir(parents=True, exist_ok=True)
    path = CACHE / (hashlib.sha1(url.encode()).hexdigest()[:16] + ".json")
    if path.exists() and path.stat().st_size > 100:
        return json.loads(path.read_text(encoding="utf-8"))
    req = urllib.request.Request(url, headers={"User-Agent": "PoE2DB-KR-Android/1.0"})
    with urllib.request.urlopen(req, timeout=30) as resp:
        body = resp.read().decode("utf-8", "replace")
    path.write_text(body, encoding="utf-8")
    return json.loads(body)


def slug_of(name):
    """poe2db 슬러그: 공백→_, 아포스트로피는 제거."""
    s = name.replace("’", "").replace("'", "").replace("`", "")
    s = re.sub(r"\s+", "_", s.strip())
    return urllib.parse.quote(s, safe="_()+-.,")


def clean(text):
    if not text:
        return ""
    text = TAG.sub(" ", text)
    text = text.replace("&nbsp;", " ").replace("&amp;", "&")
    text = text.replace("\xa0", " ")
    return re.sub(r"\s+", " ", text).strip()


def mod_text(inner):
    """옵션 div 안쪽 HTML. <br>만 줄로 바꾸고 나머지 태그는 붙여 쓴다."""
    text = BR.sub("\n", inner)
    text = TAG.sub("", text)
    text = text.replace("&nbsp;", " ").replace("&amp;", "&")
    text = text.replace("\xa0", " ")
    text = re.sub(r"[ \t]+", " ", text)
    return "\n".join(line.strip() for line in text.split("\n") if line.strip())


def popup(html):
    """페이지 첫 아이템 팝업에서 이름·베이스·옵션·아이콘을 뽑는다."""
    out = {"name": "", "base": "", "implicit": [], "explicit": [], "icon": ""}
    m = re.search(r'<meta property="og:image" content="([^"]+)"', html)
    if m:
        out["icon"] = m.group(1).strip()
    # 아이템 팝업 블록만 잘라 낸다 (페이지에 여러 팝업이 있을 수 있음)
    start = html.find('class="newItemPopup')
    if start < 0:
        return out
    block = html[start:start + 30000]
    name = re.search(
        r'<div class="itemName">\s*<span class="[^"]*">(.*?)</span>', block, re.S)
    if name:
        out["name"] = clean(name.group(1))
    base = re.search(
        r'<div class="itemName typeLine">\s*<span class="[^"]*">(.*?)</span>', block, re.S)
    if base:
        out["base"] = clean(base.group(1))
    content = block[: block.find('class="itemName typeLine"') + 1] if 'typeLine' in block else block
    for cls, key in (("implicitMod", "implicit"), ("explicitMod", "explicit")):
        for div in re.finditer(
            r'<div class="' + cls + r'[^"]*">(.*?)</div>', block, re.S):
            inner = div.group(1)
            inner = re.sub(r'<span class="secondary">.*?</span>', "", inner, flags=re.S)
            text = mod_text(inner)
            for part in text.split("\n"):
                part = part.strip()
                if part and part not in out[key]:
                    out[key].append(part)
    del content
    return out


def title_name(html):
    m = re.search(r"<title>(.*?)</title>", html, re.S)
    if not m:
        return ""
    title = clean(m.group(1))
    return re.sub(r"\s*-\s*PoE2DB.*$", "", title).strip()


def template(text):
    """숫자를 # 으로 지운 문장 틀 (poe.ninja 태그도 함께 제거)."""
    t = clean_tags(text)
    t = NUM.sub("#", t)
    t = re.sub(r"\(\s*#\s*[-~—]\s*#\s*\)", "(#-#)", t)
    t = re.sub(r"\s+", " ", t).strip().lower()
    return t


def number_count(text):
    return len(NUM.findall(clean_tags(text)))


def numbers_of(text):
    """문장에 나오는 숫자를 순서대로. 숫자 순서가 다르면 다른 문장을 짝지은 것이다."""
    return [float(x) for x in NUM.findall(clean_tags(text))]


def templatize_ko(text):
    t = re.sub(r"\(\s*(\d+(?:\.\d+)?)\s*—\s*(\d+(?:\.\d+)?)\s*\)", r"(#—#)", text)
    t = NUM.sub("#", t)
    return re.sub(r"\s+", " ", t).strip()


def normalize_key(name):
    return name.lower().strip()


def main():
    catalog = json.loads(CATALOG.read_text(encoding="utf-8"))["items"]
    names = {}
    bases = {}
    icons = {}

    for item in catalog:
        en = (item.get("nameEn") or "").strip()
        ko = (item.get("name") or "").strip()
        if not en or not ko or en == ko:
            continue
        names.setdefault(normalize_key(en), ko)
        if item.get("icon"):
            icons.setdefault(normalize_key(en), item["icon"])
        if item.get("cat") in ("weapon", "armour", "jewellery"):
            # 방어구/무기/장신구 목록은 그 자체가 베이스 타입 사전이다
            bases.setdefault(normalize_key(en), ko)

    # ---------------------------------------------------------------- 시세 품목 수집
    lines = []          # (english name, baseType, implicit[], explicit[], category)
    missing = {}        # english name -> kind

    level_suffix = re.compile(r"^(.*?)\s*\(Level\s*(\d+)\)$", re.I)

    def add(name, base="", implicit=None, explicit=None, category=""):
        if not name:
            return
        lines.append((name, base, implicit or [], explicit or [], category))
        # "Uncut Skill Gem (Level 17)" 은 기본 이름 페이지에서 한글명을 얻는다.
        m = level_suffix.match(name)
        base_name = m.group(1) if m else name
        if normalize_key(base_name) not in names:
            missing.setdefault(base_name, "item")
        if normalize_key(name) not in names and not m:
            missing.setdefault(name, "item")
        if base and normalize_key(base) not in bases and normalize_key(base) not in names:
            missing.setdefault(base, "base")

    for t in EXCHANGE_TYPES:
        url = f"{API}/exchange/current/overview?league={urllib.parse.quote(LEAGUE)}&type={t}"
        try:
            data = fetch_json(url)
        except Exception as exc:  # 네트워크 실패 시 캐시만으로 진행
            print(f"[warn] {t}: {exc}", file=sys.stderr)
            continue
        index = {}
        for it in data.get("items", []):
            index[it.get("id")] = it
            index[it.get("detailsId")] = it
        for line in data.get("lines", []):
            info = index.get(line.get("id")) or {}
            name = info.get("name") or line.get("name") or ""
            add(name, "", [], [], info.get("category") or t)

    for t in ITEM_TYPES:
        url = f"{API}/stash/current/item/overview?league={urllib.parse.quote(LEAGUE)}&type={t}"
        try:
            data = fetch_json(url)
        except Exception as exc:
            print(f"[warn] {t}: {exc}", file=sys.stderr)
            continue
        for line in data.get("lines", []):
            name = line.get("name") or ""
            base = line.get("baseType") or ""
            implicit = [clean(x.get("text", "")) for x in line.get("implicitModifiers") or []]
            explicit = [clean(x.get("text", "")) for x in line.get("explicitModifiers") or []]
            add(name, base, implicit, explicit, line.get("category") or t)

    print(f"품목 {len(lines)}줄 · 사전에 없는 이름 {len([k for k, v in missing.items() if v == 'item'])}개 "
          f"· 베이스 {len([k for k, v in missing.items() if v == 'base'])}개")

    # ---------------------------------------------------------------- poe2db 보강
    # 고유 아이템은 이름이 이미 사전에 있어도 옵션 문장을 얻으려고 페이지를 한 번 더 읽는다.
    unique_names = {name for name, base, imp, exp, _ in lines if imp or exp}
    targets = [(k, v) for k, v in missing.items()]
    for name in sorted(unique_names):
        if name not in missing:
            targets.append((name, "unique"))
    fetched = {}

    def work(pair):
        name, kind = pair
        url = BASE + slug_of(name)
        html = fetch(url)
        if not html:
            return name, kind, None
        info = popup(html)
        if not info["name"]:
            info["name"] = title_name(html)
        return name, kind, info

    with ThreadPoolExecutor(max_workers=8) as pool:
        for i, (name, kind, info) in enumerate(pool.map(work, targets), 1):
            if info and info["name"]:
                fetched[name] = info
                if kind == "item":
                    names[normalize_key(name)] = info["name"]
                if kind == "base" or (info.get("base") and normalize_key(name) == normalize_key(info.get("base", ""))):
                    bases[normalize_key(name)] = info["name"]
                if info.get("icon"):
                    icons.setdefault(normalize_key(name), info["icon"])
            if i % 50 == 0:
                print(f"  {i}/{len(targets)} …")

    # 시세 응답의 베이스 타입은 별도 페이지에서 한글명을 얻는다.
    base_missing = [b for b in {b for _, b, _, _, _ in lines if b}
                    if normalize_key(b) not in bases and normalize_key(b) not in names]
    staged = []
    prefixes = ("Runemastered ", "Superior ", "Corrupted ")
    for b in base_missing:
        trimmed = b
        for pre in prefixes:
            if trimmed.startswith(pre):
                trimmed = trimmed[len(pre):]
        url = BASE + slug_of(trimmed)
        html = fetch(url)
        if not html:
            continue
        info = popup(html)
        ko = info["name"] or title_name(html)
        if not ko:
            continue
        suffix = b[len(trimmed):] if trimmed != b else ""
        bases[normalize_key(b)] = (ko + " " + suffix).strip() if suffix else ko
        if info.get("icon"):
            icons.setdefault(normalize_key(b), info["icon"])
        staged.append((b, ko))
        if len(staged) % 40 == 0:
            print(f"  베이스 {len(staged)} …")

    # ---------------------------------------------------------------- 옵션 틀
    mods = {template(k): v for k, v in MANUAL_MODS.items()}
    item_mods = {}
    learned = 0
    for name, base, implicit, explicit, _ in lines:
        info = fetched.get(name)
        if not info:
            continue
        ko_imp = [tidy_ko(x) for x in info.get("implicit") or []]
        ko_exp = [tidy_ko(x) for x in info.get("explicit") or []]
        # 고유 아이템 페이지의 베이스 타입(한글)을 영문 베이스 타입에 연결한다.
        if base and info.get("base"):
            bases.setdefault(normalize_key(base), info["base"])
        if implicit and ko_imp and len(implicit) == len(ko_imp):
            for en, ko in zip(implicit, ko_imp):
                key = template(en)
                # 숫자 개수가 다르면 다른 줄을 짝지은 것이므로 버린다.
                if not key or not ko or re.fullmatch(r"[\x00-\x7f]+", ko):
                    continue
                if numbers_of(en) != numbers_of(ko):
                    continue
                mods.setdefault(key, templatize_ko(ko))
        if explicit and ko_exp and len(explicit) == len(ko_exp):
            for en, ko in zip(explicit, ko_exp):
                key = template(en)
                if not key or not ko or re.fullmatch(r"[\x00-\x7f]+", ko):
                    continue
                if numbers_of(en) != numbers_of(ko):
                    continue
                if key not in mods:
                    mods[key] = templatize_ko(ko)
                    learned += 1
        if (implicit or explicit) and (ko_imp or ko_exp):
            item_mods[normalize_key(name)] = {"implicit": ko_imp, "explicit": ko_exp}

    # 옵션 틀에서 숫자를 되돌려 쓰기 쉽도록 "#-#" 범위 표기를 "(#-#)" 로 통일
    normalized_mods = {}
    for k, v in mods.items():
        k2 = re.sub(r"\(\s*#\s*[-~—]\s*#\s*\)", "(#-#)", k)
        normalized_mods.setdefault(k2, v)

    # "Uncut Skill Gem (Level 17)" 처럼 등급이 붙은 이름은 기본 이름 번역 + 등급 표기로 채운다.
    level_re = re.compile(r"^(.*?)\s*\(Level\s*(\d+)\)$", re.I)
    for name, _, _, _, _ in lines:
        m = level_re.match(name)
        if not m:
            continue
        base_ko = names.get(normalize_key(m.group(1)))
        if base_ko:
            names[normalize_key(name)] = f"{base_ko} ({m.group(2)}레벨)"

    # ---------------------------------------------------------------- 클래스 대표 아이콘
    class_keywords = {
        "Bow": [" bow"], "Crossbow": ["crossbow"], "Staff": ["staff"],
        "Quarterstaff": ["quarterstaff"], "Wand": ["wand"], "Sceptre": ["sceptre"],
        "Spear": ["spear"], "Sword": ["sword"], "Axe": ["axe"], "Mace": ["mace"],
        "Dagger": ["dagger"], "Claw": ["claw"], "Flail": ["flail"],
        "Shield": ["shield"], "Buckler": ["buckler"], "Quiver": ["quiver"],
        "Focus": ["focus"], "Talisman": ["talisman"], "Helmet": ["helmet", "hood", "cap"],
        "Body Armour": ["vest", "robe", "cuirass", "coat"], "Gloves": ["gloves", "mitts"],
        "Boots": ["boots", "shoes"], "Amulet": ["amulet"], "Ring": ["ring"],
        "Belt": ["belt", "sash"], "Jewel": ["jewel"], "Flask": ["flask"],
    }
    pool = [i for i in catalog
            if i.get("cat") in ("weapon", "armour", "jewellery") and i.get("icon") and i.get("nameEn")]
    class_icons = {}
    for label, keys in class_keywords.items():
        best = None
        for item in pool:
            en = (item.get("nameEn") or "").lower()
            if any(k.strip() and (k in (" " + en + " ")) for k in keys):
                if best is None or len(item["nameEn"]) < len(best["nameEn"]):
                    best = item
        if best:
            class_icons[label] = best["icon"]

    payload = {
        "source": "https://poe2db.tw/kr/ · poe.ninja PoE2 economy API",
        "fetchedAt": date.today().isoformat(),
        # 사전은 [키, 값] 배열로 쓴다. 앱의 JSON 구현(안드로이드/테스트) 모두에서
        # 객체 키 목록을 따로 읽지 않고 순서대로 파싱할 수 있게 하기 위함이다.
        "names": sorted(names.items()),
        "bases": sorted(bases.items()),
        "icons": sorted(icons.items()),
        "mods": sorted(normalized_mods.items()),
        "itemMods": sorted((k, v.get("implicit", []), v.get("explicit", [])) for k, v in item_mods.items()),
        "classes": sorted(CLASS_KO.items()),
        "categories": sorted({**CLASS_KO, **CATEGORY_KO}.items()),
        "classIcons": sorted(class_icons.items()),
    }
    OUT.write_text(json.dumps(payload, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")

    total = len(lines)
    named = sum(1 for n, _, _, _, _ in lines if normalize_key(n) in names)
    based = sum(1 for _, b, _, _, _ in lines if b and normalize_key(b) in bases)
    with_base = sum(1 for _, b, _, _, _ in lines if b)
    print(f"사전: 이름 {len(names)} · 베이스 {len(bases)} · 아이콘 {len(icons)} · 옵션 틀 {len(normalized_mods)} (학습 {learned})")
    print(f"커버리지: 이름 {named}/{total} ({named * 100.0 / max(1, total):.1f}%) · "
          f"베이스 {based}/{with_base} ({based * 100.0 / max(1, with_base):.1f}%)")
    print(f"저장: {OUT} ({OUT.stat().st_size // 1024}KB)")


if __name__ == "__main__":
    main()
