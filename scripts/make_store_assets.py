#!/usr/bin/env python3
"""앱 아이콘과 스토어 그래픽을 만든다.

    python3 scripts/make_store_assets.py

결과:
    store-assets/icon-512.png
    store-assets/feature-graphic-1024x500.png
    app/src/main/res/mipmap-*/ic_launcher*.png
    app/src/main/res/mipmap-*/ic_launcher_foreground.png
"""

import math
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = Path(__file__).resolve().parent.parent
STORE = ROOT / "store-assets"
RES = ROOT / "app" / "src" / "main" / "res"

SANS_BOLD = "/usr/share/fonts/truetype/liberation/LiberationSans-Bold.ttf"
SERIF_BOLD = "/usr/share/fonts/truetype/liberation/LiberationSerif-Bold.ttf"
KR = "/workspace/.fonts/NotoSansKR.ttf"

BG_INNER = (32, 37, 47)
BG_OUTER = (9, 10, 13)
GOLD = (212, 175, 55)
GOLD_LIGHT = (240, 208, 118)
GOLD_DIM = (150, 122, 45)
TEXT_MUTED = (146, 153, 166)


def gradient(size):
    small = 256
    img = Image.new("RGB", (small, small))
    pixels = img.load()
    center = (small - 1) / 2
    max_dist = math.hypot(center, center)
    for y in range(small):
        for x in range(small):
            d = min(1.0, math.hypot(x - center, y - center) / max_dist)
            d = d ** 1.15
            pixels[x, y] = tuple(
                int(BG_INNER[i] * (1 - d) + BG_OUTER[i] * d) for i in range(3)
            )
    return img.resize((size, size), Image.LANCZOS)


def hexagon_points(cx, cy, radius, rotation=-90):
    points = []
    for step in range(6):
        angle = math.radians(rotation + step * 60)
        points.append((cx + radius * math.cos(angle), cy + radius * math.sin(angle)))
    return points


def glyph_layer(size, scale=1.0, glow=False):
    """투명 배경 위에 금색 육각 문양과 P2 레터마크를 그린다."""
    layer = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    draw = ImageDraw.Draw(layer)
    cx = cy = size / 2
    outer = size * 0.335 * scale
    inner = size * 0.235 * scale

    if glow:
        halo = Image.new("RGBA", (size, size), (0, 0, 0, 0))
        halo_draw = ImageDraw.Draw(halo)
        halo_draw.polygon(hexagon_points(cx, cy, outer), outline=(GOLD[0], GOLD[1], GOLD[2], 110))
        halo = halo.filter(ImageFilter.GaussianBlur(size * 0.02))
        layer = Image.alpha_composite(layer, halo)
        draw = ImageDraw.Draw(layer)

    draw.polygon(
        hexagon_points(cx, cy, outer),
        fill=(GOLD[0], GOLD[1], GOLD[2], 16),
    )
    draw.polygon(hexagon_points(cx, cy, outer), outline=(GOLD[0], GOLD[1], GOLD[2], 210))
    draw.polygon(hexagon_points(cx, cy, inner, rotation=-90), outline=(GOLD_DIM[0], GOLD_DIM[1], GOLD_DIM[2], 160))

    accent = size * 0.335 * scale
    for angle in (-90, -30, 30, 90, 150, 210):
        rad = math.radians(angle)
        x1 = cx + accent * math.cos(rad)
        y1 = cy + accent * math.sin(rad)
        x2 = cx + (accent + size * 0.03 * scale) * math.cos(rad)
        y2 = cy + (accent + size * 0.03 * scale) * math.sin(rad)
        draw.line((x1, y1, x2, y2), fill=(GOLD[0], GOLD[1], GOLD[2], 190), width=max(1, int(size * 0.008 * scale)))

    font = ImageFont.truetype(SERIF_BOLD, int(size * 0.29 * scale))
    draw.text((cx, cy - size * 0.02 * scale), "P2", font=font, fill=GOLD_LIGHT, anchor="mm")

    small = ImageFont.truetype(SANS_BOLD, int(size * 0.062 * scale))
    draw.text((cx, cy + size * 0.165 * scale), "DB", font=small, fill=(226, 198, 120, 235), anchor="mm")
    return layer


def icon_master(size=1024):
    base = gradient(size).convert("RGBA")
    base = Image.alpha_composite(base, glyph_layer(size, glow=True))
    return base


def rounded(img, radius_ratio=0.21):
    mask = Image.new("L", img.size, 0)
    ImageDraw.Draw(mask).rounded_rectangle(
        (0, 0, img.size[0] - 1, img.size[1] - 1), radius=int(img.size[0] * radius_ratio), fill=255
    )
    out = Image.new("RGBA", img.size, (0, 0, 0, 0))
    out.paste(img, (0, 0), mask)
    return out


def circle(img):
    mask = Image.new("L", img.size, 0)
    ImageDraw.Draw(mask).ellipse((0, 0, img.size[0] - 1, img.size[1] - 1), fill=255)
    out = Image.new("RGBA", img.size, (0, 0, 0, 0))
    out.paste(img, (0, 0), mask)
    return out


def write_launcher(master):
    densities = {
        "mdpi": 48,
        "hdpi": 72,
        "xhdpi": 96,
        "xxhdpi": 144,
        "xxxhdpi": 192,
    }
    for density, px in densities.items():
        folder = RES / f"mipmap-{density}"
        folder.mkdir(parents=True, exist_ok=True)
        square = master.resize((px, px), Image.LANCZOS)
        rounded(square).save(folder / "ic_launcher.png")
        circle(square).save(folder / "ic_launcher_round.png")

        canvas_px = int(px * 108 / 48)
        art_px = int(canvas_px * 72 / 108)
        art = glyph_layer(art_px, scale=1.0)
        foreground = Image.new("RGBA", (canvas_px, canvas_px), (0, 0, 0, 0))
        foreground.paste(art, ((canvas_px - art_px) // 2, (canvas_px - art_px) // 2), art)
        foreground.save(folder / "ic_launcher_foreground.png")


def write_adaptive_xml():
    folder = RES / "mipmap-anydpi-v26"
    folder.mkdir(parents=True, exist_ok=True)
    for name in ("ic_launcher.xml", "ic_launcher_round.xml"):
        (folder / name).write_text(
            '<?xml version="1.0" encoding="utf-8"?>\n'
            '<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n'
            '    <background android:drawable="@color/ic_launcher_background" />\n'
            '    <foreground android:drawable="@mipmap/ic_launcher_foreground" />\n'
            '</adaptive-icon>\n',
            encoding="utf-8",
        )


def feature_graphic():
    width, height = 1024, 500
    img = Image.new("RGB", (width, height))
    draw = ImageDraw.Draw(img)

    for y in range(height):
        ratio = y / height
        color = tuple(int(BG_INNER[i] * (1 - ratio * 0.75) + BG_OUTER[i] * ratio * 0.75) for i in range(3))
        draw.line((0, y, width, y), fill=color)

    pattern = Image.new("RGBA", (width, height), (0, 0, 0, 0))
    pattern_draw = ImageDraw.Draw(pattern)
    for row in range(-2, 8):
        for col in range(-1, 8):
            cx = 700 + col * 120 + (60 if row % 2 else 0)
            cy = row * 104
            pattern_draw.polygon(
                hexagon_points(cx, cy, 52), outline=(212, 175, 55, 18)
            )
    img = Image.alpha_composite(img.convert("RGBA"), pattern)

    art = glyph_layer(400, scale=0.78, glow=True)
    img.paste(art, (660, 48), art)

    draw = ImageDraw.Draw(img)
    title_font = ImageFont.truetype(SERIF_BOLD, 96)
    tag_font = ImageFont.truetype(KR, 30)
    sub_font = ImageFont.truetype(KR, 22)
    tag_sans = ImageFont.truetype(KR, 22)

    draw.text((78, 132), "PoE2DB KR", font=title_font, fill=GOLD_LIGHT)
    draw.line((80, 252, 520, 252), fill=(212, 175, 55, 120), width=2)
    draw.text((80, 276), "Path of Exile 2 한국어 데이터베이스", font=tag_font, fill=(226, 226, 230))
    draw.text((80, 336), "스킬 젬 · 유니크 · 화폐 · 전직 · 키워드", font=sub_font, fill=TEXT_MUTED)
    draw.text((80, 396), "2,400+ 항목 내장 · 오프라인 목록 · 즐겨찾기", font=tag_sans, fill=(160, 165, 178))

    out = img.convert("RGB")
    out.save(STORE / "feature-graphic-1024x500.png")


def main():
    STORE.mkdir(parents=True, exist_ok=True)
    master = icon_master(1024)
    master.resize((512, 512), Image.LANCZOS).convert("RGB").save(STORE / "icon-512.png")
    write_launcher(master)
    write_adaptive_xml()
    feature_graphic()
    print("done:", STORE, RES)


if __name__ == "__main__":
    main()
