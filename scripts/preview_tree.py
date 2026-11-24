#!/usr/bin/env python3
"""앱의 트리 화면을 오프스크린으로 그려 확인용 미리보기를 만든다.

    python3 scripts/preview_tree.py

결과: docs/preview-passive-tree.png (불러온 빌드의 패시브 트리)
      docs/preview-atlas-tree.png   (아틀라스 트리)

앱의 TreeCanvasView와 같은 규칙(배경 그라데이션, 금동 연결선, 찍은 노드 금빛,
직업 메달, 전직 링)을 PIL로 옮긴 그림이다. 실제 기기 렌더링과 픽셀 단위로 같지는
않지만, 자료·아이콘·배치가 화면에서 어떻게 보이는지 눈으로 확인하는 데 쓴다.
"""
import base64
import json
import math
import re
import zlib
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "app" / "src" / "main" / "assets"
DOCS = ROOT / "docs"
FONT = "/usr/share/fonts/pickle-hwp/NotoSansCJKkr-Regular.otf"

BG_TOP = (19, 26, 38)
BG_MID = (11, 14, 20)
BG_EDGE = (5, 7, 10)
EDGE = (90, 74, 46)
EDGE_ON = (232, 192, 96)
GOLD = (240, 220, 168)
GOLD_DIM = (201, 180, 122)
TEXT = (236, 232, 225)
MUTED = (121, 128, 140)
BRONZE = (107, 90, 51)
BRONZE_SOFT = (154, 127, 70)


def font(size):
    try:
        return ImageFont.truetype(FONT, size)
    except Exception:
        return ImageFont.load_default()


def load_pob(path):
    raw = Path(path).read_text().strip()
    xml = zlib.decompress(base64.b64decode(raw.replace("-", "+").replace("_", "/") + "==")).decode(
        "utf-8", "replace"
    )
    spec = re.search(r'<Spec [^>]*>', xml).group(0)
    build = re.search(r'<Build [^>]*>', xml).group(0)
    nodes = [int(v) for v in re.search(r'nodes="([^"]*)"', spec).group(1).split(",") if v]
    class_id = int(re.search(r'classId="(\d+)"', spec).group(1))
    ascend_id = int(re.search(r'ascendClassId="(\d+)"', spec).group(1))
    level = int(re.search(r'level="(\d+)"', build).group(1))
    return nodes, class_id, ascend_id, level


def radial_background(size):
    w, h = size
    image = Image.new("RGB", size, BG_EDGE)
    px = image.load()
    cx, cy, r = w * 0.5, h * 0.45, max(w, h) * 1.2
    for y in range(0, h, 2):
        for x in range(0, w, 2):
            d = math.hypot(x - cx, y - cy) / r
            if d > 1:
                continue
            if d < 0.55:
                t = d / 0.55
                color = tuple(int(BG_TOP[i] + (BG_MID[i] - BG_TOP[i]) * t) for i in range(3))
            else:
                t = (d - 0.55) / 0.45
                color = tuple(int(BG_MID[i] + (BG_EDGE[i] - BG_MID[i]) * t) for i in range(3))
            for dy in range(2):
                for dx in range(2):
                    if x + dx < w and y + dy < h:
                        px[x + dx, y + dy] = color
    return image


def crop_icon(sheet, rect):
    x, y, w, h = rect
    return sheet.crop((x, y, x + w, y + h))


def paste_circle(image, art, cx, cy, radius):
    """아이콘을 원 안에 가둬 붙인다 (앱의 clipPath와 같은 효과)."""
    size = max(2, int(radius * 2))
    art = art.resize((size, size), Image.LANCZOS).convert("RGBA")
    mask = Image.new("L", (size, size), 0)
    ImageDraw.Draw(mask).ellipse((0, 0, size - 1, size - 1), fill=255)
    if art.split()[3].getextrema()[0] < 255:
        mask = Image.composite(mask, Image.new("L", (size, size), 0), art.split()[3])
    image.paste(art, (int(cx - radius), int(cy - radius)), mask)


def normalize(tree, atlas):
    """두 자료를 같은 모양(id, 이름, 설명, 종류, x, y, 아이콘)으로 맞춘다."""
    passive_nodes = [
        {"id": row[0], "name": row[2], "stats": row[3], "kind": row[4],
         "x": row[6], "y": row[7], "icon": row[11]}
        for row in tree["nodes"]
    ]
    atlas_nodes = [
        {"id": row[0], "name": row[1], "stats": row[2], "kind": row[3],
         "x": row[4], "y": row[5], "icon": row[6]}
        for row in atlas["nodes"]
    ]
    return (
        {"nodes": passive_nodes, "edges": [(e[0], e[1]) for e in tree["edges"]]},
        {"nodes": atlas_nodes, "edges": [(e[0], e[1]) for e in atlas["edges"]]},
    )


def draw_tree(meta, icons, class_art, allocated_ids, focus, class_start, ascend_ring, counters,
              out_path, medallion_art=None, focus_span=2600.0):
    nodes = meta["nodes"]
    edges = meta["edges"]
    width, height = 1080, 1760
    image = radial_background((width, height))
    draw = ImageDraw.Draw(image, "RGBA")

    by_id = {n["id"]: n for n in nodes}

    fx, fy = focus
    span = focus_span
    scale = width / (span * 2)
    ox = width / 2 - fx * scale
    oy = height / 2 - fy * scale

    def sx(x):
        return ox + x * scale

    def sy(y):
        return oy + y * scale

    allocated = set(allocated_ids)

    # 연결선
    for a, b in edges:
        if a not in by_id or b not in by_id:
            continue
        x1, y1 = sx(by_id[a]["x"]), sy(by_id[a]["y"])
        x2, y2 = sx(by_id[b]["x"]), sy(by_id[b]["y"])
        if max(x1, x2) < -50 or min(x1, x2) > width + 50 or max(y1, y2) < -50 or min(y1, y2) > height + 50:
            continue
        if a in allocated and b in allocated:
            draw.line((x1, y1, x2, y2), fill=EDGE_ON + (70,), width=7)
            draw.line((x1, y1, x2, y2), fill=EDGE_ON + (235,), width=3)
        else:
            draw.line((x1, y1, x2, y2), fill=EDGE + (150,), width=2)

    # 노드
    kind_radius = {"n": 44, "k": 60, "m": 40, "j": 34, "a": 32, "c": 0, "s": 26}
    for row in nodes:
        nid, kind = row["id"], row["kind"]
        px, py = sx(row["x"]), sy(row["y"])
        if px < -60 or px > width + 60 or py < -60 or py > height + 60:
            continue
        if kind == "c":
            continue
        radius = kind_radius.get(kind, 26) * scale
        on = nid in allocated
        icon_rect = row["icon"]
        if radius < 3 and not on:
            continue
        if on:
            draw.ellipse((px - radius * 1.8, py - radius * 1.8, px + radius * 1.8, py + radius * 1.8),
                         fill=(232, 192, 96, 40))
            draw.ellipse((px - radius * 1.3, py - radius * 1.3, px + radius * 1.3, py + radius * 1.3),
                         fill=(232, 192, 96, 70))
        if icon_rect:
            art = crop_icon(icons, icon_rect)
            if not on:
                art = art.convert("RGBA")
                art.putalpha(art.split()[3].point(lambda v: int(v * 0.58)))
            paste_circle(image, art, px, py, radius * 1.02)
        ring = GOLD_DIM if on else BRONZE_SOFT
        draw.ellipse((px - radius, py - radius, px + radius, py + radius),
                     outline=ring if on else BRONZE, width=max(1, int(radius * 0.22)))

    # 직업 메달 + 전직 링
    if class_start in by_id:
        mx, my = sx(by_id[class_start]["x"]), sy(by_id[class_start]["y"])
        radius = 132.0
        draw.ellipse((mx - radius * 2.1, my - radius * 2.1, mx + radius * 2.1, my + radius * 2.1),
                     fill=(232, 192, 96, 22))
        disc = Image.new("RGBA", (int(radius * 2), int(radius * 2)), (0, 0, 0, 0))
        mask = Image.new("L", disc.size, 0)
        ImageDraw.Draw(mask).ellipse((2, 2, disc.size[0] - 2, disc.size[1] - 2), fill=255)
        if medallion_art is not None:
            art = medallion_art.resize(disc.size, Image.LANCZOS).convert("RGBA")
        else:
            art = Image.new("RGBA", disc.size, (13, 18, 24, 255))
        disc.paste(art, (0, 0), mask)
        image.paste(disc, (int(mx - radius), int(my - radius)), disc)
        draw.ellipse((mx - radius, my - radius, mx + radius, my + radius), outline=(154, 127, 70), width=int(radius * 0.075))
        draw.ellipse((mx - radius * 0.93, my - radius * 0.93, mx + radius * 0.93, my + radius * 0.93),
                     outline=(216, 183, 106), width=int(radius * 0.03))
        ring_r = radius * 1.32
        for order, nid in enumerate(ascend_ring):
            if nid not in by_id:
                continue
            angle = 2 * math.pi * order / len(ascend_ring) - math.pi / 2
            px = mx + math.cos(angle) * ring_r
            py = my + math.sin(angle) * ring_r
            size = radius * 0.2
            row = by_id[nid]
            if row["icon"]:
                paste_circle(image, crop_icon(icons, row["icon"]), px, py, size)
            draw.ellipse((px - size, py - size, px + size, py + size), outline=BRONZE_SOFT, width=2)

    # 상단 띠 + 카운터
    draw.rectangle((0, 0, width, 92), fill=(10, 13, 18, 240))
    draw.rectangle((0, 91, width, 92), fill=BRONZE)
    chip_font = font(30)
    draw.rounded_rectangle((16, 22, 210, 70), radius=8, fill=(212, 175, 55, 40), outline=(212, 175, 55, 255), width=2)
    draw.text((36, 30), "패시브 트리", font=chip_font, fill=(240, 220, 168))
    draw.rounded_rectangle((222, 22, 430, 70), radius=8, fill=(23, 26, 33, 255), outline=BRONZE, width=2)
    draw.text((242, 30), "아틀라스 트리", font=chip_font, fill=MUTED)
    for i, glyph in enumerate(["⌕", "☺", "ⓘ", "↗", "⟳"]):
        gx = width - 70 * (5 - i) + 8
        draw.text((gx, 26), glyph, font=font(30), fill=MUTED)
    draw.rectangle((0, 92, width, 140), fill=(10, 13, 18, 235))
    x = 20
    for text, color in counters:
        draw.text((x, 100), text, font=font(27), fill=color)
        x += int(draw.textlength(text, font=font(27))) + 26

    # 우하단 버튼
    for i, glyph in enumerate(["+", "−", "⟲", "⤢"]):
        bx = width - 108
        by = height - 100 - (3 - i) * 108
        draw.rounded_rectangle((bx, by, bx + 96, by + 96), radius=8, fill=(18, 21, 28, 220), outline=BRONZE_SOFT, width=2)
        draw.text((bx + 38, by + 26), glyph, font=font(38), fill=(240, 220, 168))

    DOCS.mkdir(parents=True, exist_ok=True)
    image.save(out_path)
    print(f"저장: {out_path}")


def main():
    tree = json.load(open(ASSETS / "poe2_tree_kr.json", encoding="utf-8"))
    atlas = json.load(open(ASSETS / "poe2_atlas_kr.json", encoding="utf-8"))
    arts = json.load(open(ASSETS / "class_art.json", encoding="utf-8"))
    tree_icons = Image.open(ASSETS / "tree_icons.webp").convert("RGBA")
    atlas_icons = Image.open(ASSETS / "atlas_icons.webp").convert("RGBA")
    class_sheet = Image.open(ASSETS / arts["file"]).convert("RGBA")

    nodes, class_id, ascend_id, level = load_pob(ROOT / "app/src/test/resources/pob_real_build.txt")
    start = None
    for ci, nid in tree["classStarts"]:
        if ci == class_id:
            start = nid
    asc_start = None
    for ci, ai, nid in tree["ascStarts"]:
        if ci == class_id and ai == ascend_id:
            asc_start = nid
    passive_meta, atlas_meta = normalize(tree, atlas)
    node_map = {n[0]: n for n in tree["nodes"]}
    asc_internal = node_map[asc_start][5] if asc_start else ""
    ring = [n[0] for n in tree["nodes"] if n[5] == asc_internal and n[4] == "n"]
    class_index = tree["classes"][class_id]
    class_en = class_index[0]
    ascend_ko = class_index[2][ascend_id - 1][1] if len(class_index[2]) >= ascend_id else ""

    passive = sum(1 for nid in nodes if node_map.get(nid, [0, "", "", "", "s"])[4] not in ("a", "c")
                  and node_map.get(nid, ["", "", "", "", "", ""])[5] == "")
    ascend = sum(1 for nid in nodes if node_map.get(nid, ["", "", "", "", "", ""])[5]
                 and node_map.get(nid, [0, "", "", "", "s"])[4] not in ("a", "c"))
    total = level - 1 + 25

    # 전직 노드는 트리 반대편에 모여 있어 화면 맞춤에서 뺀다.
    main_nodes = [n for n in nodes if not node_map[n][5]]
    xs = [node_map[n][6] for n in main_nodes] + [node_map[start][6]]
    ys = [node_map[n][7] for n in main_nodes] + [node_map[start][7]]
    focus_x = (min(xs) + max(xs)) / 2
    focus_y = (min(ys) + max(ys)) / 2
    focus_span = max(max(xs) - min(xs), max(ys) - min(ys)) * 1.35
    # 전직 링은 시작점 둘레에 고르게 두른다 (각도 순).
    ring.sort(key=lambda nid: math.atan2(node_map[nid][7] - node_map[start][7],
                                         node_map[nid][6] - node_map[start][6]))
    draw_tree(
        passive_meta, tree_icons, class_sheet, nodes, (focus_x, focus_y),
        start, ring,
        [("패시브 %d / %d" % (passive, total), TEXT),
         ("전직 %d / 8" % ascend, GOLD_DIM),
         ("정신력 90 / 130", (140, 232, 192))],
        DOCS / "preview-passive-tree.png",
        medallion_art=crop_icon(class_sheet, arts["classes"].get(class_en)),
        focus_span=focus_span,
    )
    print(f"직업 {class_en} {ascend_ko} · 찍은 노드 {len(nodes)} · 전직 링 {len(ring)}")

    draw_tree(
        atlas_meta, atlas_icons, class_sheet, [], (0, -3000), -1, [],
        [("아틀라스 0 / 123", TEXT)],
        DOCS / "preview-atlas-tree.png",
    )


if __name__ == "__main__":
    main()
