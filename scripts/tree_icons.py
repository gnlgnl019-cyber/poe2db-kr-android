#!/usr/bin/env python3
"""패시브 트리 노드 아이콘을 한 장의 스프라이트로 묶는다.

poe2db가 노드마다 알려 주는 아이콘 경로(예: Art/2DArt/SkillIcons/passives/2handeddamage.png)를
cdn.poe2db.tw에서 받아 정사각 타일로 줄 세우고, 노드별 위치를 돌려 준다.

앱은 이 한 장만 메모리에 올려 두고 노드를 그릴 때 해당 타일만 잘라 쓴다.
(아이콘 576장을 낱개로 열지 않으므로 트리를 밀고 당길 때 끊김이 없다.)
"""
import urllib.request
from pathlib import Path

from PIL import Image

CDN = "https://cdn.poe2db.tw/image/"
UA = (
    "Mozilla/5.0 (Linux; Android 14; SM-S918N) AppleWebKit/537.36 "
    "(KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
)
TILE = 64
PAD = 0
COLUMNS = 24


def download(icon, cache):
    """아이콘 한 장을 캐시에서 꺼내거나 받는다. 실패하면 None."""
    if icon.startswith("http"):
        base = icon.rsplit(".", 1)[0]
        name = base.rsplit("/", 1)[-1] + "_" + str(abs(hash(base)) % 100000) + ".webp"
        url = base + ".webp"
    else:
        name = icon.replace("/", "_").rsplit(".", 1)[0] + ".webp"
        url = CDN + icon.rsplit(".", 1)[0] + ".webp"
    path = cache / name
    if not path.exists() or path.stat().st_size < 200:
        try:
            req = urllib.request.Request(url, headers={"User-Agent": UA})
            with urllib.request.urlopen(req, timeout=30) as resp:
                path.write_bytes(resp.read())
        except Exception:
            return None
    try:
        image = Image.open(path)
        image.load()
        return image
    except Exception:
        return None


def build(icons, cache, out_path):
    """아이콘 경로 목록 → (스프라이트 그림, {경로: [x, y, 크기]})"""
    cache.mkdir(parents=True, exist_ok=True)
    uniq = []
    for icon in icons:
        if icon and icon not in uniq:
            uniq.append(icon)
    step = TILE + PAD
    rows = (len(uniq) + COLUMNS - 1) // COLUMNS
    sheet = Image.new("RGBA", (COLUMNS * step, max(rows, 1) * step), (0, 0, 0, 0))
    rects = {}
    missing = 0
    for index, icon in enumerate(uniq):
        image = download(icon, cache)
        col = index % COLUMNS
        row = index // COLUMNS
        x = col * step
        y = row * step
        if image is None:
            missing += 1
            continue
        if image.mode != "RGBA":
            image = image.convert("RGBA")
        if image.size != (TILE, TILE):
            image = image.resize((TILE, TILE), Image.LANCZOS)
        sheet.paste(image, (x, y), image)
        rects[icon] = [x, y, TILE, TILE]
    out_path.parent.mkdir(parents=True, exist_ok=True)
    sheet.save(out_path, "WEBP", quality=92, method=6)
    print(f"  아이콘 {len(rects)}/{len(uniq)}장 · 빠짐 {missing} · {out_path.name} {out_path.stat().st_size // 1024}KB {sheet.size}")
    return rects
