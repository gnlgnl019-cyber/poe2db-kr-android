#!/usr/bin/env python3
"""플레이스토어용 휴대전화 스크린샷(1080×1920)을 앱 화면 구성대로 그린다.

    python3 scripts/make_store_screenshots.py

- 레이아웃 값은 app/src/main/res/layout/*.xml 의 dp·sp 를 3배 밀도(360dp → 1080px)로 옮긴다.
- 글자·색은 values/strings.xml, values/colors.xml 을 그대로 쓴다.
- 아이콘과 이름은 앱에 들어 있는 assets/poe2db_kr.json, 코드의 메타 스냅샷에서 가져온다.
- 아이콘 파일은 .cache/store-icons/ 에 내려받아 두고 다음 실행 때 다시 쓴다.

결과: store-assets/screenshots/{1..5}-*.png
"""

import json
import math
import urllib.request
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

import fonts

ROOT = Path(__file__).resolve().parent.parent
STORE = ROOT / "store-assets"
OUT = STORE / "screenshots"
ICONS = ROOT / ".cache" / "store-icons"
FONT_FILE = fonts.need(fonts.KOREAN, "한글")

W, H = 1080, 1920
S = 3                      # 1dp = 3px

BG = (0x11, 0x12, 0x16)
SURFACE = (0x17, 0x1A, 0x21)
SURFACE_HIGH = (0x1E, 0x22, 0x2B)
DIVIDER = (0x26, 0x2B, 0x35)
GOLD = (0xD4, 0xAF, 0x37)
GOLD_SOFT = (0xE8, 0xC8, 0x6A)
TEXT = (0xEC, 0xE8, 0xE1)
TEXT2 = (0xA9, 0xAF, 0xBA)
MUTED = (0x79, 0x80, 0x8C)
GREEN = (0x98, 0xC3, 0x79)
RED = (0xE0, 0x6C, 0x75)
ON_GOLD = (0x14, 0x16, 0x1B)
ARENA = (0x0C, 0x0D, 0x11)
ARENA_FLOOR = (0x14, 0x16, 0x1C)


def blend(fg, alpha, bg):
    """반투명 색을 배경 위에 미리 섞는다(PIL 은 RGB 캔버스에서 알파를 무시한다)."""
    return tuple(round(f * alpha + b * (1 - alpha)) for f, b in zip(fg, bg))


GOLD_15 = blend(GOLD, 0.15, SURFACE)
GOLD_40 = blend(GOLD, 0.40, SURFACE)
GOLD_LINE = blend(GOLD, 0.55, BG)

_fonts = {}


def dp(value):
    return int(round(value * S))


def font(sp, weight="Regular"):
    key = (sp, weight)
    if key not in _fonts:
        f = ImageFont.truetype(FONT_FILE, max(8, int(round(sp * S))))
        try:
            f.set_variation_by_name(weight)
        except Exception:
            pass
        _fonts[key] = f
    return _fonts[key]


_canvas = Image.new("RGBA", (8, 8))
draw0 = ImageDraw.Draw(_canvas)


def text(draw, xy, value, f, fill, anchor="la"):
    draw.text(xy, value, font=f, fill=fill, anchor=anchor)


def fit(value, f, max_width):
    """max_width(px)에 맞게 줄임표를 붙여 자른다."""
    if draw0.textlength(value, font=f) <= max_width:
        return value
    cut = value
    while cut and draw0.textlength(cut + "…", font=f) > max_width:
        cut = cut[:-1]
    return cut.rstrip() + "…"


def rrect(draw, box, radius, fill=None, outline=None, width=1):
    draw.rounded_rectangle(box, radius=radius, fill=fill, outline=outline, width=width)


def format_value(value):
    """Market.formatValue 와 같은 표기."""
    if value >= 1000:
        s = f"{value:,.0f}"
    elif value >= 100:
        s = f"{value:.0f}"
    elif value >= 10:
        s = f"{value:.1f}"
    elif value >= 1:
        s = f"{value:.2f}"
    elif value >= 0.01:
        s = f"{value:.3f}"
    elif value > 0:
        s = f"{value:.4f}"
    else:
        return "0"
    return s.rstrip("0").rstrip(".") or "0"


# ---------------------------------------------------------------- 공통 조각
def new_screen():
    img = Image.new("RGB", (W, H), BG)
    return img, ImageDraw.Draw(img)


def status_bar(draw):
    """안드로이드 상태 표시줄(24dp)."""
    draw.rectangle((0, 0, W, dp(24)), fill=BG)
    text(draw, (dp(16), dp(12)), "21:14", font(10.5, "Medium"), TEXT2, anchor="lm")
    x = W - dp(66)
    for i, h in enumerate((3, 5, 7, 9)):
        draw.rectangle((x + i * dp(3), dp(16) - dp(h), x + i * dp(3) + dp(2), dp(16)), fill=TEXT2)
    x = W - dp(44)
    for i in range(3):
        r = dp(3 + i * 2.2)
        draw.arc((x - r, dp(16) - r, x + r, dp(16) + r), 215, 325, fill=TEXT2, width=dp(1))
    draw.ellipse((x - dp(1), dp(15), x + dp(1), dp(17)), fill=TEXT2)
    draw.rounded_rectangle((W - dp(26), dp(8), W - dp(9), dp(17)), radius=dp(2), outline=TEXT2, width=dp(1))
    draw.rectangle((W - dp(8), dp(11), W - dp(7), dp(14)), fill=TEXT2)
    draw.rectangle((W - dp(24.5), dp(9.5), W - dp(18), dp(15.5)), fill=TEXT2)


def header(draw, subtitle):
    top = dp(24)
    text(draw, (dp(16), top + dp(12)), "PoE2DB KR", font(20, "Bold"), GOLD)
    text(draw, (dp(16), top + dp(38)), subtitle, font(11), MUTED)
    # 사이트 아이콘(지구본)
    cx, cy, r = W - dp(8 + 42 + 21), top + dp(18), dp(9)
    draw.ellipse((cx - r, cy - r, cx + r, cy + r), outline=GOLD, width=dp(1.4))
    draw.line((cx - r, cy, cx + r, cy), fill=GOLD, width=dp(1))
    draw.arc((cx - r * 0.55, cy - r, cx + r * 0.55, cy + r), 90, 270, fill=GOLD, width=dp(1))
    draw.arc((cx - r * 0.55, cy - r, cx + r * 0.55, cy + r), 270, 450, fill=GOLD, width=dp(1))
    # 정보 아이콘(i)
    cx = W - dp(8 + 21)
    draw.ellipse((cx - r, cy - r, cx + r, cy + r), outline=GOLD, width=dp(1.4))
    draw.ellipse((cx - dp(1.2), cy - dp(5), cx + dp(1.2), cy - dp(2.6)), fill=GOLD)
    draw.line((cx, cy - dp(1), cx, cy + dp(5)), fill=GOLD, width=dp(1.6))
    return top + dp(58)


def search_row(draw, y, hint):
    rrect(draw, (dp(16), y, W - dp(24), y + dp(42)), dp(21), fill=SURFACE, outline=DIVIDER, width=dp(1))
    cx, cy = dp(16 + 12 + 9), y + dp(21)
    draw.ellipse((cx - dp(6), cy - dp(6), cx + dp(6), cy + dp(6)), outline=MUTED, width=dp(1.4))
    draw.line((cx + dp(4), cy + dp(4), cx + dp(8), cy + dp(8)), fill=MUTED, width=dp(1.4))
    text(draw, (dp(16 + 12 + 26), cy), hint, font(14), MUTED, anchor="lm")
    return y + dp(42)


def chips(draw, x, y, labels, selected, height=32):
    x0 = x
    f = font(12, "Medium")
    for label in labels:
        w = draw0.textlength(label, font=f) + dp(26)
        on = label == selected
        if x0 + w > 0:
            rrect(draw, (x0, y, x0 + w, y + dp(height)), dp(height / 2),
                  fill=GOLD_15 if on else SURFACE,
                  outline=GOLD if on else DIVIDER, width=dp(1))
            text(draw, (x0 + w / 2, y + dp(height / 2)), label, f, GOLD if on else TEXT2, anchor="mm")
        x0 += w + dp(6)
    return y + dp(height)


def star(draw, cx, cy, r, color, width=2):
    pts = []
    for i in range(10):
        ang = -math.pi / 2 + i * math.pi / 5
        rad = r if i % 2 == 0 else r * 0.44
        pts.append((cx + rad * math.cos(ang), cy + rad * math.sin(ang)))
    draw.line(pts + [pts[0]], fill=color, width=width, joint="curve")


ICON_SOURCES = {
    "twister.webp": "Twister",
    "spark.webp": "Spark",
    "comet.webp": "Comet",
    "tempest_bell.webp": "Tempest Bell",
    "arc.webp": "Arc",
    "ice_shot.webp": "Ice Shot",
    "mageblood.webp": "Mageblood",
    "headhunter.webp": "Headhunter",
    "dream_fragments.webp": "Dream Fragments",
    "choir_of_the_storm.webp": "Choir of the Storm",
}


def ensure_icons():
    """앱 자료에 적힌 cdn 주소에서 아이콘을 받아 둔다(한 번 받으면 재사용)."""
    ICONS.mkdir(parents=True, exist_ok=True)
    catalog = json.loads((ROOT / "app/src/main/assets/poe2db_kr.json").read_text())
    url_by_name = {i.get("nameEn", "").lower(): i.get("icon", "") for i in catalog["items"]}
    for file_name, en in ICON_SOURCES.items():
        target = ICONS / file_name
        if target.exists():
            continue
        url = url_by_name.get(en.lower(), "")
        if not url:
            raise SystemExit(f"아이콘 주소를 찾지 못했습니다: {en}")
        req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0"})
        with urllib.request.urlopen(req, timeout=30) as resp:
            target.write_bytes(resp.read())


def icon(name, size):
    img = Image.open(ICONS / name).convert("RGBA")
    return img.resize((size, size), Image.LANCZOS)


def icon_tile(canvas, name, x, y, box=54, inner=46):
    d = ImageDraw.Draw(canvas)
    rrect(d, (x, y, x + dp(box), y + dp(box)), dp(12), fill=SURFACE_HIGH, outline=DIVIDER, width=dp(1))
    art = icon(name, dp(inner))
    canvas.paste(art, (int(x + dp(box - inner) / 2), int(y + dp(box - inner) / 2)), art)


def bottom_nav(draw, active):
    top = H - dp(74)
    draw.rectangle((0, top, W, H), fill=SURFACE)
    draw.line((0, top, W, top), fill=DIVIDER, width=dp(1))
    items = ["DB", "시세", "패시브", "PoB", "즐겨찾기"]
    for i, label in enumerate(items):
        cx = W / 10 + i * (W / 5)
        on = i == active
        color = GOLD if on else MUTED
        icy = top + dp(24)
        if on:
            draw.rounded_rectangle((cx - dp(26), icy - dp(14), cx + dp(26), icy + dp(14)),
                                   radius=dp(14), fill=GOLD_15)
        draw_nav_icon(draw, i, cx, icy, color)
        text(draw, (cx, top + dp(58)), label, font(10.5, "Medium" if on else "Regular"), color, anchor="mm")


def draw_nav_icon(draw, kind, cx, cy, color):
    w = dp(1.8)
    if kind == 0:      # 집
        draw.line([(cx - dp(9), cy - dp(1)), (cx, cy - dp(8)), (cx + dp(9), cy - dp(1))],
                  fill=color, width=w, joint="curve")
        draw.line([(cx - dp(6), cy - dp(3)), (cx - dp(6), cy + dp(7)), (cx + dp(6), cy + dp(7)),
                   (cx + dp(6), cy - dp(3))], fill=color, width=w, joint="curve")
    elif kind == 1:    # 상승 그래프
        draw.line([(cx - dp(9), cy + dp(3)), (cx - dp(4), cy - dp(1)), (cx, cy + dp(2)), (cx + dp(5), cy - dp(5)),
                   (cx + dp(9), cy - dp(8))], fill=color, width=w, joint="curve")
    elif kind == 2:    # 패시브 트리
        for dx, dy in ((-6, -5), (3, -7), (-6, 3), (4, 4)):
            draw.ellipse((cx + dp(dx) - dp(2.6), cy + dp(dy) - dp(2.6),
                          cx + dp(dx) + dp(2.6), cy + dp(dy) + dp(2.6)), outline=color, width=w)
        draw.line((cx - dp(4), cy - dp(3), cx + dp(4), cy - dp(5)), fill=color, width=1)
        draw.line((cx - dp(4), cy - dp(3), cx - dp(4), cy + dp(1)), fill=color, width=1)
    elif kind == 3:    # PoB(번개)
        draw.polygon([(cx + dp(2), cy - dp(9)), (cx - dp(6), cy + dp(1)), (cx - dp(1), cy + dp(1)),
                      (cx - dp(2), cy + dp(9)), (cx + dp(6), cy - dp(2)), (cx + dp(1), cy - dp(2))], fill=color)
    else:              # 즐겨찾기(별)
        star(draw, cx, cy, dp(8), color, width=w)


# ---------------------------------------------------------------- 1. DB 목록
DB_CARDS = [
    ("twister.webp", "돌개바람", "Twister",
     "공격 · 효과 범위 · 투사체 · 지속시간",
     "창을 휘둘러 돌개바람을 일으킵니다. 적을 실명시키고 반복해서 명중합니다."),
    ("spark.webp", "전기불꽃", "Spark",
     "주문 · 투사체 · 번개 · 지속시간",
     "천방지축으로 움직이는 전기불꽃 투사체를 날립니다."),
    ("comet.webp", "혜성", "Comet",
     "주문 · 효과 범위 · 냉기",
     "하늘에서 얼음 덩어리를 불러내려 대상 지점에 큰 피해를 줍니다."),
    ("tempest_bell.webp", "폭풍의 종", "Tempest Bell",
     "공격 · 효과 범위 · 근접 · 지속형",
     "연계를 쌓은 뒤 지팡이의 종을 거대하게 키워 지면에 떨어뜨립니다."),
    ("arc.webp", "연쇄 번개", "Arc",
     "주문 · 투사체 · 번개 · 연쇄",
     "번개가 주변의 적에게 옮겨가며 연쇄될 때마다 피해가 조금씩 줄어듭니다."),
]


def screen_db():
    img, draw = new_screen()
    status_bar(draw)
    y = header(draw, "Path of Exile 2 · 한국어 데이터베이스")
    y = search_row(draw, y + dp(10), "스킬, 유니크, 화폐, 무기, 방어구 검색")
    text(draw, (dp(16), y + dp(8)), "4,354개 항목 · 2026-11-22 기준", font(11), MUTED)
    y = chips(draw, dp(14), y + dp(22),
              ["전체", "무기", "방어구", "장신구", "룬", "화폐", "유니크", "스킬 젬", "보조 젬"],
              "전체")
    y += dp(10)
    card_h = dp(84)
    for i, (icon_name, name, name_en, tags, summary) in enumerate(DB_CARDS):
        top = y + i * card_h
        if top > H - dp(74):
            break
        rrect(draw, (dp(12), top, W - dp(12), top + card_h - dp(8)), dp(14), fill=SURFACE,
              outline=DIVIDER, width=dp(1))
        icon_tile(img, icon_name, dp(22), top + dp(10))
        tx = dp(86)
        right = W - dp(52)
        text(draw, (tx, top + dp(11)), name, font(15, "Bold"), TEXT)
        text(draw, (tx, top + dp(31)), name_en, font(11), MUTED)
        text(draw, (tx, top + dp(46)), fit(tags, font(11), right - tx), font(11), GOLD_SOFT)
        text(draw, (tx, top + dp(59)), fit(summary, font(11), right - tx), font(11), TEXT2)
        star(draw, W - dp(34), top + dp(26), dp(8), MUTED, width=dp(1.4))
    bottom_nav(draw, 0)
    return img


# ---------------------------------------------------------------- 2. 시세
MARKET_ROWS = [
    ("mageblood.webp", "마법사의 피", "Mageblood · Utility Belt", 229.5, 3896, 12.4),
    ("headhunter.webp", "헤드헌터", "Headhunter · Heavy Belt", 67.7, 943, -3.1),
    ("dream_fragments.webp", "꿈의 조각", "Dream Fragments · Sapphire Ring", 1.0, 1835, 0.0),
    ("choir_of_the_storm.webp", "태풍의 합창", "Choir of the Storm · Jade Amulet", 1.0, 1289, -1.2),
]
EXALTED_PER_DIVINE = 549.4
CHAOS_PER_DIVINE = 7.61


def screen_market():
    img, draw = new_screen()
    status_bar(draw)
    y = header(draw, "시세 · poe.ninja")
    rrect(draw, (dp(14), y + dp(6), W - dp(56), y + dp(50)), dp(22), fill=SURFACE, outline=DIVIDER, width=dp(1))
    text(draw, (dp(24), y + dp(28)), "알두르의 룬", font(13), TEXT, anchor="lm")
    draw.polygon([(W - dp(76), y + dp(24)), (W - dp(64), y + dp(24)), (W - dp(70), y + dp(32))], fill=MUTED)
    cx, cy = W - dp(34), y + dp(28)
    draw.arc((cx - dp(8), cy - dp(8), cx + dp(8), cy + dp(8)), 40, 320, fill=MUTED, width=dp(1.6))
    draw.polygon([(cx + dp(7), cy - dp(9)), (cx + dp(10), cy - dp(3)), (cx + dp(3), cy - dp(3))], fill=MUTED)
    y += dp(56)
    y = chips(draw, -dp(46), y, ["기폭제", "고유 무기", "고유 방어구", "고유 장신구", "허리띠", "목걸이", "반지"],
              "고유 장신구")
    y = chips(draw, dp(14), y + dp(4), ["시세 높은순", "시세 낮은순", "변동 큰순", "매물 많은순", "이름순"],
              "시세 높은순")
    y = search_row(draw, y + dp(6), "아이템 이름 검색")
    text(draw, (dp(16), y + dp(8)), "알두르의 룬 · 고유 장신구 87개 · 5분 캐시", font(11), MUTED)
    y += dp(26)
    row_h = dp(106)
    for i, (icon_name, name, sub, divine, listings, change) in enumerate(MARKET_ROWS):
        top = y + i * row_h
        if top > H - dp(74) + dp(20):
            break
        rrect(draw, (dp(12), top, W - dp(12), top + row_h - dp(8)), dp(14), fill=SURFACE,
              outline=DIVIDER, width=dp(1))
        icon_tile(img, icon_name, dp(22), top + dp(10))
        tx = dp(86)
        text(draw, (tx, top + dp(12)), name, font(15, "Bold"), TEXT)
        text(draw, (tx, top + dp(33)), fit(sub, font(11), W - dp(130) - tx), font(11), MUTED)
        text(draw, (tx, top + dp(49)), f"{format_value(divine)} 디바인", font(13, "Bold"), GOLD_SOFT)
        text(draw, (tx, top + dp(67)),
             f"≈ {format_value(divine * EXALTED_PER_DIVINE)} 엑잘 · "
             f"{format_value(divine * CHAOS_PER_DIVINE)} 카오스", font(10.5), MUTED)
        text(draw, (tx, top + dp(83)), f"매물 {listings:,}건", font(11), MUTED)
        if change > 0.05:
            text(draw, (W - dp(22), top + dp(16)), f"+{change:.1f}%", font(13, "Bold"), GREEN, anchor="ra")
        elif change < -0.05:
            text(draw, (W - dp(22), top + dp(16)), f"{change:.1f}%", font(13, "Bold"), RED, anchor="ra")
        else:
            text(draw, (W - dp(22), top + dp(16)), "0%", font(13, "Bold"), MUTED, anchor="ra")
    bottom_nav(draw, 1)
    return img


# ---------------------------------------------------------------- 3. PoB 개요
POB_TABS = ["개요", "스킬", "장비", "설정", "패시브", "전투", "메타"]


def pob_tabs(draw, y, active):
    draw.rectangle((0, y, W, y + dp(42)), fill=SURFACE)
    w = W / len(POB_TABS)
    idx = POB_TABS.index(active)
    for i, label in enumerate(POB_TABS):
        on = label == active
        text(draw, (w * i + w / 2, y + dp(18)), label, font(11.5, "Medium" if on else "Regular"),
             GOLD if on else TEXT2, anchor="mm")
    draw.rectangle((w * idx + dp(6), y + dp(39), w * (idx + 1) - dp(6), y + dp(42)), fill=GOLD)
    return y + dp(42)


def stat_cell(draw, x, y, w, label, value):
    text(draw, (x + w / 2, y), label, font(11), MUTED, anchor="ma")
    text(draw, (x + w / 2, y + dp(17)), value, font(15, "Bold"), TEXT, anchor="ma")


def screen_pob():
    img, draw = new_screen()
    status_bar(draw)
    y = header(draw, "PoB 실험실")
    y = pob_tabs(draw, y, "개요")
    x0, x1 = dp(12), W - dp(12)
    y += dp(10)
    # 내장 빌드 요약
    rrect(draw, (x0, y, x1, y + dp(52)), dp(20), fill=SURFACE, outline=DIVIDER, width=dp(1))
    text(draw, (x0 + dp(14), y + dp(10)), "얼음 화살 데드아이 · 92레벨", font(15, "Bold"), TEXT)
    text(draw, (x0 + dp(14), y + dp(31)), "스킬 그룹 3개 · 장착 장비 10개 · 패시브 118포인트", font(11), MUTED)
    y += dp(60)
    # 코드 입출력
    rrect(draw, (x0, y, x1, y + dp(92)), dp(20), fill=SURFACE, outline=DIVIDER, width=dp(1))
    draw.rounded_rectangle((x0 + dp(12), y + dp(10), x1 - dp(12), y + dp(50)), radius=dp(20), fill=GOLD)
    text(draw, ((x0 + x1) / 2, y + dp(30)), "빌드 불러오기", font(14, "Bold"), ON_GOLD, anchor="mm")
    bw = (x1 - x0 - dp(24) - dp(12)) / 3
    for i, label in enumerate(["샘플 빌드", "코드 복사", "초기화"]):
        bx = x0 + dp(12) + i * (bw + dp(6))
        if i < 2:
            draw.rounded_rectangle((bx, y + dp(56), bx + bw, y + dp(86)), radius=dp(15), fill=SURFACE_HIGH)
        else:
            draw.rounded_rectangle((bx, y + dp(56), bx + bw, y + dp(86)), radius=dp(15), outline=GOLD_40, width=dp(1))
        text(draw, (bx + bw / 2, y + dp(71)), label, font(11.5), TEXT if i < 2 else GOLD, anchor="mm")
    y += dp(102)
    # 계산 대시보드
    rrect(draw, (x0, y, x1, y + dp(118)), dp(20), fill=SURFACE, outline=DIVIDER, width=dp(1))
    text(draw, (x0 + dp(14), y + dp(12)), "예상 DPS (PoB 기준 + 장비·설정 변동)", font(11.5, "Bold"), TEXT2)
    text(draw, (x0 + dp(14), y + dp(28)), "42,180", font(21, "Bold"), TEXT)
    text(draw, (x0 + dp(14), y + dp(58)), "+1,240 · 장비 2개 변경", font(11.5), GREEN)
    draw.line((x0 + dp(14), y + dp(74), x1 - dp(14), y + dp(74)), fill=DIVIDER, width=dp(1))
    cw = (x1 - x0 - dp(28)) / 3
    stat_cell(draw, x0 + dp(14), y + dp(82), cw, "생명력", "4,120")
    stat_cell(draw, x0 + dp(14) + cw, y + dp(82), cw, "보호막", "1,860")
    stat_cell(draw, x0 + dp(14) + cw * 2, y + dp(82), cw, "유효 체력", "21,430")
    y += dp(128)
    # 스킬 구성
    text(draw, (dp(14), y), "스킬 구성", font(13, "Bold"), GOLD)
    y += dp(19)
    rrect(draw, (x0, y, x1, y + dp(88)), dp(20), fill=SURFACE, outline=DIVIDER, width=dp(1))
    icon_tile(img, "ice_shot.webp", x0 + dp(10), y + dp(9), box=44, inner=36)
    text(draw, (x0 + dp(62), y + dp(9)), "얼음 화살", font(14, "Bold"), TEXT)
    text(draw, (x0 + dp(62), y + dp(28)), "메인 스킬 · 레벨 20 · DPS 30,940", font(11), MUTED)
    sx = x0 + dp(62)
    for label in ["관통 II", "냉기 관통", "원소 집중"]:
        w = draw0.textlength(label, font(10.5)) + dp(16)
        rrect(draw, (sx, y + dp(44), sx + w, y + dp(63)), dp(9), fill=blend(GOLD, 0.15, SURFACE),
              outline=GOLD_40, width=dp(1))
        text(draw, (sx + w / 2, y + dp(53.5)), label, font(10.5), GOLD_SOFT, anchor="mm")
        sx += w + dp(5)
    sx = x0 + dp(62)
    for label in ["냉기 숙련", "연쇄"]:
        w = draw0.textlength(label, font(10.5)) + dp(16)
        rrect(draw, (sx, y + dp(67), sx + w, y + dp(86)), dp(9), fill=SURFACE_HIGH, outline=DIVIDER, width=dp(1))
        text(draw, (sx + w / 2, y + dp(76.5)), label, font(10.5), TEXT2, anchor="mm")
        sx += w + dp(5)
    bottom_nav(draw, 3)
    return img


# ---------------------------------------------------------------- 4. 전투
def stickman(draw, cx, cy, scale=1.0, color=(0xC9, 0xD2, 0xDE), width=2, pose="idle"):
    w = max(2, int(width * scale))
    hr = dp(3.3) * scale
    draw.ellipse((cx - hr, cy - hr, cx + hr, cy + hr), outline=color, width=w)
    neck = cy + hr
    hip = neck + dp(8) * scale
    draw.line((cx, neck, cx, hip), fill=color, width=w)
    if pose == "swing":
        draw.line((cx, neck + dp(1) * scale, cx + dp(7) * scale, neck + dp(3) * scale), fill=color, width=w)
        draw.line((cx, neck + dp(1) * scale, cx - dp(4) * scale, neck + dp(5) * scale), fill=color, width=w)
        draw.line((cx, hip, cx + dp(5) * scale, hip + dp(8) * scale), fill=color, width=w)
        draw.line((cx, hip, cx - dp(5) * scale, hip + dp(7) * scale), fill=color, width=w)
    elif pose == "run":
        draw.line((cx, neck + dp(1) * scale, cx - dp(6) * scale, neck + dp(4) * scale), fill=color, width=w)
        draw.line((cx, neck + dp(1) * scale, cx + dp(5) * scale, neck + dp(3) * scale), fill=color, width=w)
        draw.line((cx, hip, cx + dp(6) * scale, hip + dp(8) * scale), fill=color, width=w)
        draw.line((cx, hip, cx - dp(3) * scale, hip + dp(8) * scale), fill=color, width=w)
    else:
        draw.line((cx, neck + dp(1) * scale, cx - dp(5) * scale, neck + dp(5) * scale), fill=color, width=w)
        draw.line((cx, neck + dp(1) * scale, cx + dp(5) * scale, neck + dp(5) * scale), fill=color, width=w)
        draw.line((cx, hip, cx - dp(4) * scale, hip + dp(8) * scale), fill=color, width=w)
        draw.line((cx, hip, cx + dp(4) * scale, hip + dp(8) * scale), fill=color, width=w)


def screen_battle():
    img, draw = new_screen()
    status_bar(draw)
    y = header(draw, "PoB 실험실")
    y = pob_tabs(draw, y, "전투")
    text(draw, (dp(12), y + dp(6)), "얼음 화살 · 레인저 · 초당 2.87회 · DPS 42,180 · EHP 21,430",
         font(11.5, "Bold"), GOLD)
    text(draw, (dp(12), y + dp(24)), "트리거: 치명타 시 시전 (혜성)", font(10.5), TEXT2)
    cy = y + dp(42)
    cy = chips(draw, dp(10), cy, ["16T 맵핑", "우버 보스"], "16T 맵핑", height=28)
    cy = chips(draw, dp(10), cy + dp(2), ["25마리", "50마리", "100마리"], "50마리", height=28)
    cy = chips(draw, dp(10), cy + dp(2), ["자동", "얼음", "번개", "화염"], "자동", height=28)
    cy = chips(draw, dp(10), cy + dp(2), ["트리거 자동", "CoC 테스트", "트리거 끔"], "트리거 자동", height=28)
    by = cy + dp(6)
    bh = dp(42)
    widths = [1.2, 1.0, 0.7, 1.0]
    labels = ["전투 시작", "일시정지", "1x", "초기화"]
    bw = (W - dp(20) - dp(9)) / sum(widths)
    bx = dp(10)
    for i, label in enumerate(labels):
        w = bw * widths[i]
        if i == 0:
            draw.rounded_rectangle((bx, by, bx + w, by + bh), radius=dp(21), fill=GOLD)
            text(draw, (bx + w / 2, by + bh / 2), label, font(13, "Bold"), ON_GOLD, anchor="mm")
        elif i == 1:
            draw.rounded_rectangle((bx, by, bx + w, by + bh), radius=dp(21), fill=SURFACE_HIGH)
            text(draw, (bx + w / 2, by + bh / 2), label, font(12), TEXT, anchor="mm")
        else:
            draw.rounded_rectangle((bx, by, bx + w, by + bh), radius=dp(21), outline=GOLD_40, width=dp(1))
            text(draw, (bx + w / 2, by + bh / 2), label, font(12), GOLD, anchor="mm")
        bx += w + dp(3)
    top = by + bh + dp(8)
    bottom = top + dp(132)
    rrect(draw, (dp(10), top, W - dp(10), bottom), dp(12), fill=ARENA, outline=DIVIDER, width=dp(1))
    ax0, ax1 = dp(10), W - dp(10)
    fx, fy = (ax0 + ax1) / 2 + dp(10), bottom - dp(22)
    draw.ellipse((ax0 + dp(14), fy - dp(46), ax1 - dp(14), fy + dp(22)), fill=ARENA_FLOOR)
    draw.arc((ax0 + dp(14), fy - dp(46), ax1 - dp(14), fy + dp(22)), 0, 360, fill=blend(DIVIDER, 0.9, ARENA), width=dp(1))
    tx, ty = ax1 - dp(92), fy - dp(24)
    draw.ellipse((tx - dp(48), ty - dp(18), tx + dp(48), ty + dp(18)), outline=(0xD8, 0x3B, 0x3B), width=dp(2))
    draw.pieslice((tx - dp(41), ty - dp(15), tx + dp(41), ty + dp(15)), 180, 320,
                  fill=blend((0xE0, 0x3B, 0x3B), 0.30, ARENA_FLOOR))
    bcy = ty - dp(4)
    draw.line((tx - dp(7), bcy - dp(7), tx - dp(11), bcy - dp(13)), fill=GOLD_SOFT, width=dp(2))
    draw.line((tx + dp(7), bcy - dp(7), tx + dp(11), bcy - dp(13)), fill=GOLD_SOFT, width=dp(2))
    stickman(draw, tx, bcy, 1.55, GOLD_SOFT, 2.6, "idle")
    mobs = [(ax0 + dp(62), fy - dp(8)), (ax0 + dp(104), fy - dp(40)), (ax0 + dp(146), fy - dp(10)),
            (ax0 + dp(188), fy - dp(44)), (ax0 + dp(222), fy - dp(6)), (ax0 + dp(168), fy - dp(26)),
            (ax0 + dp(118), fy - dp(20)), (ax0 + dp(78), fy - dp(48))]
    for mx, my in mobs:
        stickman(draw, mx, my, 0.9, (0xB8, 0xC0, 0xCC), 1.4, "run")
    px, py = ax0 + dp(52), fy - dp(34)
    draw.ellipse((px - dp(19), py - dp(16), px + dp(19), py + dp(38)),
                 outline=blend(GOLD, 0.55, ARENA_FLOOR), width=dp(2))
    stickman(draw, px, py, 1.3, (0xF2, 0xDC, 0x9A), 2.6, "swing")
    for qx, qy in [(px + dp(24), py - dp(10)), (px + dp(42), py - dp(16)), (px + dp(60), py - dp(22)),
                   (px + dp(78), py - dp(28))]:
        draw.line((qx - dp(5), qy + dp(2), qx + dp(5), qy - dp(2)), fill=(0x8F, 0xE6, 0xFF), width=dp(2))
    text(draw, (tx - dp(58), ty - dp(40)), "18,420", font(12, "Bold"), (0xE8, 0xFC, 0xFF), anchor="mm")
    text(draw, (tx + dp(8), ty - dp(48)), "CRIT!", font(10, "Bold"), GOLD, anchor="mm")
    text(draw, (tx + dp(6), ty - dp(34)), "41,260", font(14, "Bold"), GOLD_SOFT, anchor="mm")
    text(draw, (ax0 + dp(12), top + dp(12)), "P1 · 얼음 화살", font(10.5, "Bold"), GOLD, anchor="lm")
    text(draw, (ax1 - dp(12), top + dp(12)), "보스 · 재의 중재자", font(10.5), TEXT2, anchor="rm")
    draw.rounded_rectangle((ax0 + dp(12), top + dp(21), ax0 + dp(148), top + dp(28)), radius=dp(3), fill=(0x3A, 0x1A, 0x1A))
    draw.rounded_rectangle((ax0 + dp(12), top + dp(21), ax0 + dp(94), top + dp(28)), radius=dp(3), fill=(0xD8, 0x3B, 0x3B))
    ry = bottom + dp(8)
    rrect(draw, (dp(12), ry, W - dp(12), ry + dp(56)), dp(18), fill=SURFACE, outline=DIVIDER, width=dp(1))
    text(draw, (dp(24), ry + dp(10)), "클리어 34.6s", font(13, "Bold"), GOLD)
    text(draw, (dp(24), ry + dp(28)), "가한 피해 2,418,900 · 실전 DPS 40,120", font(10.5), TEXT2)
    text(draw, (dp(24), ry + dp(41)), "치명타 96회 · 피격 7회 / 회피 21회 · 처치 50/50", font(10.5), TEXT2)
    text(draw, (dp(12), min(ry + dp(64), H - dp(72))),
         "PoB 빌드 수치 기반 간이 시각 시뮬레이션 · 실제 게임 판정과 다름", font(10), MUTED)
    bottom_nav(draw, 3)
    return img


# ---------------------------------------------------------------- 5. 메타
META_ASC = [
    ("마셜 아티스트", 25.0), ("스피릿 워커", 17.0), ("데드아이", 15.0), ("택티션", 5.0),
    ("오라클", 5.0), ("디사이플 오브 케출리", 4.0), ("위치헌터", 4.0), ("리치", 4.0),
    ("스톰위버", 3.0), ("타이탄", 3.0), ("블러드 메이지", 2.0), ("젬링 리저네어", 2.0),
    ("인퍼널리스트", 2.0), ("심연의 리치", 2.0), ("샤먼", 1.0),
]


def screen_meta():
    img, draw = new_screen()
    status_bar(draw)
    y = header(draw, "PoB 실험실")
    y = pob_tabs(draw, y, "메타")
    y += dp(10)
    draw.rounded_rectangle((dp(12), y, W - dp(12), y + dp(42)), radius=dp(21), outline=GOLD_40, width=dp(1))
    text(draw, (W / 2, y + dp(21)), "poe.ninja 빌드 랭킹 실시간으로 열기", font(13), GOLD, anchor="mm")
    y += dp(56)
    text(draw, (dp(14), y), "전직 점유율 (리그 기준, %)", font(13, "Bold"), GOLD)
    y += dp(22)
    x0, x1 = dp(14), W - dp(14)
    row = dp(31)
    for i, (name, share) in enumerate(META_ASC):
        if y + row > H - dp(74) - dp(160):
            break
        text(draw, (x0, y), f"{i + 1}. {name}", font(12), GOLD if i == 0 else TEXT2)
        text(draw, (x1, y), f"{share:.0f}%", font(12, "Bold"), GOLD if i == 0 else TEXT, anchor="ra")
        track = y + dp(19)
        draw.rectangle((x0, track, x1, track + dp(8)), fill=DIVIDER)
        draw.rectangle((x0, track, x0 + (x1 - x0) * share / 25.0, track + dp(8)),
                       fill=GOLD if i == 0 else GOLD_40)
        y += row
    y += dp(6)
    text(draw, (dp(14), y), "인기 빌드 · DPS 스냅샷", font(13, "Bold"), GOLD)
    y += dp(20)
    build_h = dp(60)
    if y + build_h + dp(34) < H - dp(74):
        rrect(draw, (dp(12), y, W - dp(12), y + build_h), dp(18), fill=SURFACE, outline=DIVIDER, width=dp(1))
        text(draw, (dp(24), y + dp(8)), "돌개바람  ·  1,840만 DPS", font(13, "Bold"), TEXT)
        text(draw, (dp(24), y + dp(26)), "마셜 아티스트", font(11), GOLD_SOFT)
        text(draw, (dp(24), y + dp(42)), "시뮬라크럼·매핑 — 공개 빌드 중 최고 DPS 예시", font(10.5), MUTED)
        y += build_h + dp(10)
    text(draw, (dp(14), min(y, H - dp(96))),
         "출처: U4N 0.5 티어리스트 · PoB Archives 공개 목록 · 실시간 수치 아님", font(10), MUTED)
    text(draw, (dp(14), min(y + dp(15), H - dp(82))), "기준일 2026-11-22 · 알두르의 룬 · 0.5.5h",
         font(10), MUTED)
    bottom_nav(draw, 3)
    return img


def main():
    ensure_icons()
    OUT.mkdir(parents=True, exist_ok=True)
    screens = [
        ("1-db", screen_db),
        ("2-market", screen_market),
        ("3-pob", screen_pob),
        ("4-battle", screen_battle),
        ("5-meta", screen_meta),
    ]
    for name, make in screens:
        path = OUT / f"{name}.png"
        make().save(path, optimize=True)
        print("saved", path)


if __name__ == "__main__":
    main()
