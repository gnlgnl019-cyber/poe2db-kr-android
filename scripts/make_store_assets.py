#!/usr/bin/env python3
"""앱 아이콘과 스토어 그래픽을 PoE2 카오스 오브 기반으로 만든다.

    python3 scripts/make_store_assets.py

원본: cdn.poe2db.tw 의 카오스 오브 아이콘 (108×108 WEBP)
      https://cdn.poe2db.tw/image/Art/2DItems/Currency/CurrencyRerollRare.webp
      없으면 자동으로 내려받아 store-assets/source/ 에 둔다.

결과:
    store-assets/icon-512.png
    store-assets/feature-graphic-1024x500.png
    app/src/main/res/mipmap-*/ic_launcher.png
    app/src/main/res/mipmap-*/ic_launcher_round.png
    app/src/main/res/mipmap-*/ic_launcher_foreground.png
"""

import math
import urllib.request
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = Path(__file__).resolve().parent.parent
STORE = ROOT / "store-assets"
RES = ROOT / "app" / "src" / "main" / "res"
SOURCE_DIR = STORE / "source"
SOURCE_FILE = SOURCE_DIR / "chaos-orb-108.webp"
SOURCE_URL = "https://cdn.poe2db.tw/image/Art/2DItems/Currency/CurrencyRerollRare.webp"

SANS_BOLD = "/usr/share/fonts/truetype/liberation/LiberationSans-Bold.ttf"
SERIF_BOLD = "/usr/share/fonts/truetype/liberation/LiberationSerif-Bold.ttf"
KR = "/workspace/.fonts/NotoSansKR.ttf"

BG_IN = (28, 29, 36)       # #1C1D24
BG_OUT = (17, 18, 22)      # #111216
DISC_IN = (28, 29, 36)
DISC_OUT = (20, 22, 27)
GOLD = (212, 175, 55)      # #D4AF37
GOLD_LIGHT = (240, 208, 118)
TEXT_MUTED = (146, 153, 166)

# 아이콘 비율: 원형 메달 안의 오브가 아이콘을 꽉 채우되 테두리에 닿지 않게
ICON_RADIUS = 0.400        # 아이콘 한 변 대비 메달 반지름
ICON_ART = 1.04            # 메달 지름 대비 오브 이미지 크기(원형으로 잘라 넣는다)
FG_RADIUS = 0.360          # 어댑티브 아이콘 108dp 캔버스 기준 (지름 77.8dp, 안전원 안)


# ---------------------------------------------------------------- 원본 이미지
def ensure_source():
    SOURCE_DIR.mkdir(parents=True, exist_ok=True)
    if not SOURCE_FILE.exists():
        req = urllib.request.Request(SOURCE_URL, headers={"User-Agent": "Mozilla/5.0"})
        with urllib.request.urlopen(req, timeout=30) as resp:
            SOURCE_FILE.write_bytes(resp.read())
    return Image.open(SOURCE_FILE).convert("RGBA")


def _premultiplied_resize(img, size):
    """가장자리 번짐 없이 RGBA 를 확대한다(알파 프리멀티플라이)."""
    arr = np.asarray(img).astype(np.float32)
    alpha = arr[:, :, 3:4] / 255.0
    pm = np.dstack([arr[:, :, :3] * alpha, arr[:, :, 3:4]])
    resized = Image.fromarray(np.clip(pm, 0, 255).astype(np.uint8), "RGBA").resize(
        (size, size), Image.LANCZOS
    )
    out = np.asarray(resized).astype(np.float32)
    a = np.clip(out[:, :, 3:4], 1, 255)
    rgb = np.clip(out[:, :, :3] * 255.0 / a, 0, 255)
    return Image.fromarray(np.dstack([rgb, out[:, :, 3:4]]).astype(np.uint8), "RGBA")


def art_master():
    """108px 원본을 단계적으로 8배 확대하며 살짝 샤프닝한 마스터(864px)."""
    img = ensure_source()
    side = img.size[0]
    while side * 2 <= 864:
        side *= 2
        img = _premultiplied_resize(img, side)
        img = img.filter(ImageFilter.UnsharpMask(radius=1.3, percent=50, threshold=0))
    if img.size[0] != 864:
        img = _premultiplied_resize(img, 864)
    cache = ROOT / ".cache" / "chaos-art-master.png"
    cache.parent.mkdir(parents=True, exist_ok=True)
    img.save(cache)
    return img


# ---------------------------------------------------------------- 배경/장식
def radial_bg(size):
    small = max(64, size // 4)
    img = Image.new("RGB", (small, small))
    pixel = img.load()
    center = (small - 1) / 2
    max_dist = math.hypot(center, center)
    for y in range(small):
        for x in range(small):
            d = min(1.0, math.hypot(x - center, y - center) / max_dist) ** 1.3
            pixel[x, y] = tuple(
                int(BG_IN[i] * (1 - d) + BG_OUT[i] * d) for i in range(3)
            )
    return img.resize((size, size), Image.LANCZOS)


def hex_pattern(size, alpha=8, radius_frac=0.13):
    layer = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    draw = ImageDraw.Draw(layer)
    radius = size * radius_frac
    step_x = radius * math.sqrt(3)
    step_y = radius * 1.5
    for row in range(-2, int(size / step_y) + 3):
        for col in range(-2, int(size / step_x) + 3):
            cx = col * step_x + (step_x / 2 if row % 2 else 0)
            cy = row * step_y
            points = [
                (
                    cx + radius * math.cos(math.radians(-90 + 60 * k)),
                    cy + radius * math.sin(math.radians(-90 + 60 * k)),
                )
                for k in range(6)
            ]
            draw.polygon(points, outline=(*GOLD, alpha), width=1)
    return layer


def disc_gradient(diameter):
    img = Image.new("RGB", (diameter, diameter))
    pixel = img.load()
    center = (diameter - 1) / 2
    for y in range(diameter):
        for x in range(diameter):
            r = min(1.0, math.hypot(x - center, y - center) / center)
            pixel[x, y] = tuple(
                int(DISC_IN[i] * (1 - r) + DISC_OUT[i] * r) for i in range(3)
            )
    return img


def medallion(size, radius_frac, art, art_ratio, rim_alpha=185, inner_shadow=100, band=0.05):
    """어두운 원판 위에 카오스 오브를 원형으로 잘라 넣고 금색 테두리를 두른다."""
    center = size / 2
    radius = size * radius_frac
    box = (center - radius, center - radius, center + radius, center + radius)
    diameter = int(round(2 * radius))
    out = Image.new("RGBA", (size, size), (0, 0, 0, 0))

    disc = disc_gradient(diameter).convert("RGBA")
    disc_mask = Image.new("L", (diameter, diameter), 0)
    ImageDraw.Draw(disc_mask).ellipse((0, 0, diameter - 1, diameter - 1), fill=255)
    out.paste(disc, (int(round(box[0])), int(round(box[1]))), disc_mask)

    art_px = int(round(diameter * art_ratio))
    orb = art.resize((art_px, art_px), Image.LANCZOS)
    arr = np.asarray(orb).astype(np.float32)
    arr[:, :, :3] = np.clip(arr[:, :, :3] * 1.03 + 3, 0, 255)   # 살짝 밝게
    orb = Image.fromarray(arr.astype(np.uint8), "RGBA")
    sharp_radius = max(0.8, art_px * 0.0045)
    rgb = orb.convert("RGB").filter(
        ImageFilter.UnsharpMask(radius=sharp_radius, percent=72, threshold=2)
    )
    orb = Image.merge("RGBA", (*rgb.split(), orb.split()[3]))

    orb_mask = Image.new("L", (art_px, art_px), 0)
    ImageDraw.Draw(orb_mask).ellipse((0, 0, art_px - 1, art_px - 1), fill=255)
    orb.putalpha(Image.composite(orb.split()[3], Image.new("L", (art_px, art_px), 0), orb_mask))

    layer = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    layer.paste(orb, (int(round(center - art_px / 2)), int(round(center - art_px / 2))), orb)
    clip = Image.new("L", (size, size), 0)
    ImageDraw.Draw(clip).ellipse(box, fill=255)
    layer.putalpha(Image.composite(layer.split()[3], Image.new("L", (size, size), 0), clip))
    out = Image.alpha_composite(out, layer)

    if inner_shadow:
        shadow = Image.new("RGBA", (size, size), (0, 0, 0, 0))
        draw = ImageDraw.Draw(shadow)
        width = max(2, int(radius * band * 2))
        draw.ellipse(
            (
                center - (radius - width / 2),
                center - (radius - width / 2),
                center + (radius - width / 2),
                center + (radius - width / 2),
            ),
            outline=(0, 0, 0, inner_shadow),
            width=width,
        )
        shadow = shadow.filter(ImageFilter.GaussianBlur(radius * band * 0.9))
        shadow.putalpha(Image.composite(shadow.split()[3], Image.new("L", (size, size), 0), clip))
        out = Image.alpha_composite(out, shadow)

    rim = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    ImageDraw.Draw(rim).ellipse(box, outline=(*GOLD, rim_alpha), width=max(1, int(size * 0.006)))
    out = Image.alpha_composite(out, rim.filter(ImageFilter.GaussianBlur(size * 0.0012)))
    return out


def icon_master(size, art, hex_alpha=8, radius_frac=ICON_RADIUS, art_ratio=ICON_ART):
    base = radial_bg(size).convert("RGBA")
    if hex_alpha:
        base = Image.alpha_composite(base, hex_pattern(size, hex_alpha))
    center = size / 2
    radius = size * radius_frac
    halo = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    ImageDraw.Draw(halo).ellipse(
        (center - radius, center - radius, center + radius, center + radius),
        fill=(*GOLD, 24),
    )
    base = Image.alpha_composite(base, halo.filter(ImageFilter.GaussianBlur(size * 0.045)))
    return Image.alpha_composite(base, medallion(size, radius_frac, art, art_ratio))


def foreground_master(size, art):
    """어댑티브 아이콘 전경: 투명 배경 위 메달(안전 영역 안에 들어간다)."""
    return medallion(size, FG_RADIUS, art, ICON_ART)


# ---------------------------------------------------------------- 출력
def rounded(img, radius_ratio=0.21):
    mask = Image.new("L", img.size, 0)
    ImageDraw.Draw(mask).rounded_rectangle(
        (0, 0, img.size[0] - 1, img.size[1] - 1),
        radius=int(img.size[0] * radius_ratio),
        fill=255,
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


def write_launcher(art):
    densities = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
    master = icon_master(1024, art)
    fg_master = foreground_master(432, art)
    for density, px in densities.items():
        folder = RES / f"mipmap-{density}"
        folder.mkdir(parents=True, exist_ok=True)
        square = master.resize((px, px), Image.LANCZOS)
        rounded(square).save(folder / "ic_launcher.png")
        circle(square).save(folder / "ic_launcher_round.png")

        canvas_px = int(px * 108 / 48)
        fg_master.resize((canvas_px, canvas_px), Image.LANCZOS).save(
            folder / "ic_launcher_foreground.png"
        )


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


def feature_graphic(art):
    width, height = 1024, 500
    img = Image.new("RGB", (width, height))
    draw = ImageDraw.Draw(img)

    for y in range(height):
        ratio = y / height
        color = tuple(
            int(BG_IN[i] * (1 - ratio * 0.75) + BG_OUT[i] * ratio * 0.75) for i in range(3)
        )
        draw.line((0, y, width, y), fill=color)

    pattern = Image.new("RGBA", (width, height), (0, 0, 0, 0))
    pattern_draw = ImageDraw.Draw(pattern)
    for row in range(-2, 8):
        for col in range(-1, 8):
            cx = 700 + col * 120 + (60 if row % 2 else 0)
            cy = row * 104
            points = [
                (cx + 52 * math.cos(math.radians(-90 + 60 * k)), cy + 52 * math.sin(math.radians(-90 + 60 * k)))
                for k in range(6)
            ]
            pattern_draw.polygon(points, outline=(*GOLD, 18))
    img = Image.alpha_composite(img.convert("RGBA"), pattern)

    # 오른쪽: 카오스 오브 메달
    med_size = 392
    med = medallion(med_size, 0.455, art, ICON_ART, rim_alpha=195, inner_shadow=110)
    halo = Image.new("RGBA", (med_size, med_size), (0, 0, 0, 0))
    m_center = med_size / 2
    m_radius = med_size * 0.455
    ImageDraw.Draw(halo).ellipse(
        (m_center - m_radius, m_center - m_radius, m_center + m_radius, m_center + m_radius),
        fill=(*GOLD, 30),
    )
    halo = halo.filter(ImageFilter.GaussianBlur(med_size * 0.06))
    med_layer = Image.alpha_composite(halo, med)
    img.paste(med_layer, (612, 54), med_layer)

    draw = ImageDraw.Draw(img)
    title_font = ImageFont.truetype(SERIF_BOLD, 96)
    tag_font = ImageFont.truetype(KR, 30)
    sub_font = ImageFont.truetype(KR, 22)
    tag_sans = ImageFont.truetype(KR, 22)

    draw.text((78, 132), "PoE2DB KR", font=title_font, fill=GOLD_LIGHT)
    draw.line((80, 252, 520, 252), fill=(*GOLD, 120), width=2)
    draw.text((80, 276), "Path of Exile 2 한국어 데이터베이스", font=tag_font, fill=(226, 226, 230))
    draw.text((80, 336), "스킬 젬 · 유니크 · 화폐 · 전직 · 키워드", font=sub_font, fill=TEXT_MUTED)
    draw.text((80, 396), "2,400+ 항목 내장 · 오프라인 목록 · 즐겨찾기", font=tag_sans, fill=(160, 165, 178))

    img.convert("RGB").save(STORE / "feature-graphic-1024x500.png")


def main():
    STORE.mkdir(parents=True, exist_ok=True)
    art = art_master()
    icon_master(512, art).convert("RGB").save(STORE / "icon-512.png")
    write_launcher(art)
    write_adaptive_xml()
    feature_graphic(art)
    print("done:", STORE, RES)


if __name__ == "__main__":
    main()
