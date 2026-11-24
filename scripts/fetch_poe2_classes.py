#!/usr/bin/env python3
"""직업 일러스트를 한 장으로 묶는다 (트리 한가운데의 직업 메달에 쓴다).

    python3 scripts/fetch_poe2_classes.py

결과: app/src/main/assets/class_art.webp
      app/src/main/assets/class_art.json  {"file":…, "tile":…, "classes": {"Monk": [x,y,w,h], …}}

원본: https://cdn.poe2db.tw/image/art/2dart/baseclassillustrations/<class>baseillustration.webp
캐시: .cache/classes/
"""
import json
import sys
import urllib.request
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
CACHE = ROOT / ".cache" / "classes"
OUT_IMG = ROOT / "app" / "src" / "main" / "assets" / "class_art.webp"
OUT_JSON = ROOT / "app" / "src" / "main" / "assets" / "class_art.json"
UA = (
    "Mozilla/5.0 (Linux; Android 14; SM-S918N) AppleWebKit/537.36 "
    "(KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
)
TILE = 256
COLUMNS = 3
CLASSES = [
    "marauder", "witch", "ranger", "duelist", "shadow", "templar",
    "warrior", "sorceress", "huntress", "mercenary", "monk", "druid",
]


def fetch(name):
    CACHE.mkdir(parents=True, exist_ok=True)
    path = CACHE / f"{name}.webp"
    if not path.exists() or path.stat().st_size < 2000:
        url = f"https://cdn.poe2db.tw/image/art/2dart/baseclassillustrations/{name}baseillustration.webp"
        req = urllib.request.Request(url, headers={"User-Agent": UA})
        with urllib.request.urlopen(req, timeout=60) as resp:
            path.write_bytes(resp.read())
    image = Image.open(path)
    image.load()
    return image


def main():
    rows = (len(CLASSES) + COLUMNS - 1) // COLUMNS
    sheet = Image.new("RGBA", (COLUMNS * TILE, rows * TILE), (0, 0, 0, 255))
    rects = {}
    for index, name in enumerate(CLASSES):
        image = fetch(name)
        if image.mode != "RGBA":
            image = image.convert("RGBA")
        image = image.resize((TILE, TILE), Image.LANCZOS)
        col = index % COLUMNS
        row = index // COLUMNS
        sheet.paste(image, (col * TILE, row * TILE))
        rects[name.capitalize()] = [col * TILE, row * TILE, TILE, TILE]
    sheet.save(OUT_IMG, "WEBP", quality=88, method=6)
    OUT_JSON.write_text(
        json.dumps({"file": "class_art.webp", "tile": TILE, "classes": rects}, ensure_ascii=False),
        encoding="utf-8",
    )
    print(f"직업 {len(rects)}장 · {OUT_IMG.name} {OUT_IMG.stat().st_size // 1024}KB {sheet.size}")


if __name__ == "__main__":
    sys.exit(main())
