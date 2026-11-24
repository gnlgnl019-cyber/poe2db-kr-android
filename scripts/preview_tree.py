#!/usr/bin/env python3
"""앱의 트리 화면을 오프스크린으로 그려 확인용 미리보기를 만든다.

    python3 scripts/preview_tree.py

결과: docs/preview-passive-tree.png (불러온 빌드의 패시브 트리)
      docs/preview-atlas-tree.png   (아틀라스 트리)

앱의 TreeCanvasView와 같은 규칙으로 PIL에 옮긴 그림이다.
- poe2db 화면을 띄우지 않고 노드 좌표·연결선·아이콘으로 앱이 직접 그린다
- 가운데 직업 일러스트·금색 고리·전직 노드 링 같은 장식은 없다
- 노드는 실제 자리에 종류별 크기로, 찍은 노드는 금빛으로 빛난다
"""

import base64
import json
import math
import re
import zlib
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

import fonts

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "app" / "src" / "main" / "assets"
DOCS = ROOT / "docs"
FONT = fonts.find(fonts.KOREAN)

BG_TOP = (19, 26, 38)
BG_MID = (11, 14, 20)
BG_EDGE = (5, 7, 10)
EDGE = (106, 85, 51)
EDGE_ON = (232, 192, 96)
GOLD = (240, 220, 168)
GOLD_DIM = (201, 180, 122)
TEXT = (236, 232, 225)
MUTED = (121, 128, 140)
BRONZE = (69, 62, 48)
BRONZE_SOFT = (138, 117, 73)

# 앱의 TreeMetrics와 같은 값 (트리 좌표 단위) — poe2db 화면이 쓰는 반지름 그대로
RADIUS = {"n": 76, "k": 109, "m": 76, "j": 76, "a": 76, "c": 84, "e": 51, "s": 51}
ICON = {"n": 49, "k": 69, "m": 49, "j": 49, "a": 49, "c": 56, "e": 34, "s": 34}
DOT_SCALE = 0.045


def font(size):
    if FONT is None:
        return ImageFont.load_default()
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


def paste_circle(image, art, cx, cy, radius, alpha=255):
    """아이콘을 원 안에 가둬 붙인다 (앱의 clipPath와 같은 효과)."""
    size = max(2, int(radius * 2))
    art = art.resize((size, size), Image.LANCZOS).convert("RGBA")
    if alpha < 255:
        art.putalpha(art.split()[3].point(lambda v: int(v * alpha / 255)))
    mask = Image.new("L", (size, size), 0)
    ImageDraw.Draw(mask).ellipse((0, 0, size - 1, size - 1), fill=255)
    if art.split()[3].getextrema()[0] < 255:
        mask = Image.composite(mask, Image.new("L", (size, size), 0), art.split()[3])
    image.paste(art, (int(cx - radius), int(cy - radius)), mask)


def normalize(tree, atlas):
    """두 자료를 같은 모양(id, 이름, 설명, 종류, x, y, 아이콘)으로 맞춘다."""
    passive_nodes = [
        {"id": row[0], "name": row[2] or row[1], "stats": row[3], "kind": row[4], "ascendancy": row[5],
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


def fit(box, width, height):
    """찍은 노드가 화면을 채우도록 배율·이동값을 잡는다 (앱의 TreeViewport.fit)."""
    min_x, min_y, max_x, max_y = box
    span_x = max(max_x - min_x, 6000.0)
    span_y = max(max_y - min_y, 6000.0)
    cx = (min_x + max_x) / 2
    cy = (min_y + max_y) / 2
    pad_x = span_x * 0.10 + 180
    pad_y = span_y * 0.10 + 180
    total_x = span_x + pad_x * 2
    total_y = span_y + pad_y * 2
    scale = min(width / total_x, height / total_y)
    return scale, width / 2 - cx * scale, height / 2 - cy * scale


def draw_tree(meta, icons, allocated_ids, box, out_path, counters, class_row=None,
              class_starts=(), class_name=""):
    nodes = meta["nodes"]
    edges = meta["edges"]
    width, height = 1080, 1760
    image = radial_background((width, height))
    draw = ImageDraw.Draw(image, "RGBA")

    by_id = {n["id"]: n for n in nodes}
    allocated = set(allocated_ids)
    scale, ox, oy = fit(box, width, height)

    def sx(x):
        return ox + x * scale

    def sy(y):
        return oy + y * scale

    dot_scale = scale < DOT_SCALE

    # 연결선 — 화면을 가로지르는 긴 통로(전직 시작점으로 뻗는 선)는 긋지 않는다.
    def long_edge(a, b):
        if a not in by_id or b not in by_id:
            return True
        dx = by_id[a]["x"] - by_id[b]["x"]
        dy = by_id[a]["y"] - by_id[b]["y"]
        return dx * dx + dy * dy > 2500 * 2500

    for a, b in edges:
        if long_edge(a, b):
            continue
        x1, y1 = sx(by_id[a]["x"]), sy(by_id[a]["y"])
        x2, y2 = sx(by_id[b]["x"]), sy(by_id[b]["y"])
        if max(x1, x2) < -50 or min(x1, x2) > width + 50 or max(y1, y2) < -50 or min(y1, y2) > height + 50:
            continue
        stroke = max(2, int(4.2 * scale * 3))
        if a in allocated and b in allocated:
            draw.line((x1, y1, x2, y2), fill=EDGE_ON + (80,), width=int(stroke * 4))
            draw.line((x1, y1, x2, y2), fill=EDGE_ON + (235,), width=stroke)
        else:
            draw.line((x1, y1, x2, y2), fill=EDGE + (175,), width=max(1, stroke - 1))

    # 노드 — 작은 노드를 먼저, 큰 노드를 나중에 그린다
    def onclick_order(row):
        return 0 if row["kind"] in ("s", "e") else 1

    for row in sorted(nodes, key=onclick_order):
        # 숙련 노드는 주요 노드 위에 겹쳐 있어 그리지 않는다 (앱과 같다).
        if row["kind"] == "m":
            continue
        px, py = sx(row["x"]), sy(row["y"])
        if px < -60 or px > width + 60 or py < -60 or py > height + 60:
            continue
        kind = row["kind"]
        on = row["id"] in allocated
        radius = RADIUS.get(kind, 51) * scale
        icon_r = ICON.get(kind, 34) * scale
        if dot_scale:
            dot = {"k": 5.7, "n": 4.5, "a": 4.5, "m": 3.6, "j": 3.6, "e": 3.3, "c": 6.6}.get(kind, 2.6)
            if on:
                draw.ellipse((px - dot * 2, py - dot * 2, px + dot * 2, py + dot * 2), fill=EDGE_ON + (60,))
                color = GOLD
            else:
                color = {"k": (180, 118, 47), "n": (154, 132, 85), "c": (255, 231, 176),
                         "m": (91, 123, 160)}.get(kind, (74, 67, 54))
            draw.ellipse((px - dot, py - dot, px + dot, py + dot), fill=color)
            continue
        if on:
            draw.ellipse((px - radius * 1.3, py - radius * 1.3, px + radius * 1.3, py + radius * 1.3),
                         fill=(232, 192, 96, 45))
        # 아이콘 뒤의 어두운 바탕은 아이콘 크기에만 깐다 (이웃 노드를 가리지 않게).
        fill = (58, 46, 24, 255) if on else {
            "k": (36, 28, 18), "n": (26, 29, 38), "c": (34, 26, 16),
        }.get(kind, (14, 17, 24))
        draw.ellipse((px - icon_r, py - icon_r, px + icon_r, py + icon_r), fill=fill)
        if row["icon"] and icon_r >= 1.2 * 3:
            paste_circle(image, crop_icon(icons, row["icon"]), px, py, icon_r,
                         alpha=255 if on else 210)
        else:
            dot = max(radius * 0.16, 3)
            draw.ellipse((px - dot, py - dot, px + dot, py + dot),
                         fill=GOLD if on else (60, 58, 52))
        # 테두리 고리 — 가는 선이라 이웃 위에 얹혀도 가리지 않는다.
        ring = GOLD_DIM if on else {
            "c": (224, 190, 120), "k": (180, 118, 47), "n": (138, 117, 73),
            "m": (78, 106, 140), "j": (140, 122, 70), "a": (126, 151, 184),
            "e": (126, 151, 184),
        }.get(kind, BRONZE)
        stroke = max(1, int(radius * 0.10))
        draw.ellipse((px - radius, py - radius, px + radius, py + radius), outline=ring, width=stroke)
        if kind in ("k", "n"):
            # 주요 노드는 아이콘과 고리 사이에 얇은 고리를 하나 더 둔다.
            inner = radius * 0.80
            draw.ellipse((px - inner, py - inner, px + inner, py + inner),
                         outline=(255, 231, 176, 120) if on else (62, 55, 40, 120), width=1)

    # 직업 시작점 이름
    if scale >= 0.04:
        label_font = font(int(11 * 3))
        for nid in class_starts:
            row = by_id.get(nid)
            if not row:
                continue
            px, py = sx(row["x"]), sy(row["y"])
            if px < -100 or px > width + 100 or py < -50 or py > height + 50:
                continue
            label = class_name if nid == class_starts[0] and class_name else row["name"]
            if not label:
                continue
            text_w = draw.textlength(label, font=label_font)
            left = px - text_w / 2
            if left < 4 or left + text_w > width - 4:
                continue
            draw.text((left, py - RADIUS["c"] * scale - 18), label, font=label_font, fill=(255, 231, 176))

    # 상단 띠 + 카운터
    draw.rectangle((0, 0, width, 92), fill=(10, 13, 18, 240))
    draw.rectangle((0, 91, width, 92), fill=BRONZE_SOFT)
    chip_font = font(30)
    draw.rounded_rectangle((16, 22, 210, 70), radius=8, fill=(212, 175, 55, 40), outline=(212, 175, 55, 255), width=2)
    draw.text((36, 30), "패시브 트리", font=chip_font, fill=GOLD)
    draw.rounded_rectangle((222, 22, 430, 70), radius=8, fill=(23, 26, 33, 255), outline=BRONZE_SOFT, width=2)
    draw.text((242, 30), "아틀라스 트리", font=chip_font, fill=MUTED)
    draw.rectangle((0, 92, width, 140), fill=(10, 13, 18, 235))
    x = 20
    for text, color in counters:
        draw.text((x, 100), text, font=font(27), fill=color)
        x += int(draw.textlength(text, font=font(27))) + 26

    # 상단에 고정된 직업 고르기 줄 (앱에서는 늘 보인다) — 열두 직업을 두 줄로 접는다
    if class_row:
        rows = [class_row[:6], class_row[6:]]
        top = 141
        draw.rectangle((0, top, width, top + 30 + 62 * len(rows)), fill=(10, 13, 18, 225))
        draw.text((16, top + 6), "직업 선택 — 시작 노드에서부터 찍습니다", font=font(24), fill=GOLD)
        for r, group in enumerate(rows):
            cx = 16
            y = top + 34 + r * 62
            for label, on in group:
                w = int(draw.textlength(label, font=font(26))) + 34
                draw.rounded_rectangle((cx, y, cx + w, y + 54), radius=8,
                                       fill=(212, 175, 55, 45) if on else (23, 26, 33, 255),
                                       outline=(212, 175, 55) if on else BRONZE_SOFT, width=2)
                draw.text((cx + 17, y + 12), label, font=font(26), fill=GOLD if on else MUTED)
                cx += w + 10

    DOCS.mkdir(parents=True, exist_ok=True)
    image.save(out_path)
    print(f"저장: {out_path} (배율 {scale:.4f})")


def main():
    tree = json.load(open(ASSETS / "poe2_tree_kr.json", encoding="utf-8"))
    atlas = json.load(open(ASSETS / "poe2_atlas_kr.json", encoding="utf-8"))
    tree_icons = Image.open(ASSETS / "tree_icons.webp").convert("RGBA")
    atlas_icons = Image.open(ASSETS / "atlas_icons.webp").convert("RGBA")

    nodes, class_id, ascend_id, level = load_pob(ROOT / "app/src/test/resources/pob_real_build.txt")
    start = None
    for ci, nid in tree["classStarts"]:
        if ci == class_id:
            start = nid
    passive_meta, atlas_meta = normalize(tree, atlas)
    node_map = {n[0]: n for n in tree["nodes"]}
    class_index = tree["classes"][class_id]
    class_en = class_index[0]
    class_ko = class_index[1] or class_en
    ascend_ko = class_index[2][ascend_id - 1][1] if len(class_index[2]) >= ascend_id else ""

    passive = sum(1 for nid in nodes if node_map.get(nid, [0, "", "", "", "s"])[4] not in ("a", "c")
                  and node_map.get(nid, ["", "", "", "", "", ""])[5] == "")
    ascend = sum(1 for nid in nodes if node_map.get(nid, ["", "", "", "", "", ""])[5]
                 and node_map.get(nid, [0, "", "", "", "s"])[4] not in ("a", "c"))
    total = level - 1 + 25

    # 화면 맞춤: 찍은 노드 중 전직(바깥 동그라미)을 뺀 자리 + 직업 시작점
    main_nodes = [n for n in nodes if n in node_map and not node_map[n][5]]
    xs = [node_map[n][6] for n in main_nodes]
    ys = [node_map[n][7] for n in main_nodes]
    if start in node_map:
        xs.append(node_map[start][6])
        ys.append(node_map[start][7])
    box = (min(xs), min(ys), max(xs), max(ys))
    class_starts = sorted({nid for _, nid in tree["classStarts"] if nid in node_map},
                          key=lambda nid: math.atan2(node_map[nid][7], node_map[nid][6]))

    draw_tree(
        passive_meta, tree_icons, nodes, box,
        DOCS / "preview-passive-tree.png",
        [("패시브 %d / %d" % (passive, total), TEXT),
         ("전직 %d / 8" % ascend, GOLD_DIM),
         ("정신력 90 / 130", (140, 232, 192))],
        class_row=[(c[1] or c[0], i == class_id) for i, c in enumerate(tree["classes"])],
        class_starts=class_starts,
        class_name=class_ko,
    )
    print(f"직업 {class_en} {ascend_ko} · 찍은 노드 {len(nodes)}")

    # 노드 하나하나가 이웃을 가리지 않는지 눈으로 보는 확대 그림 (직업 시작 노드 둘레)
    if start in node_map:
        sx0, sy0 = node_map[start][6], node_map[start][7]
        half = 1500
        draw_tree(
            passive_meta, tree_icons, nodes,
            (sx0 - half, sy0 - half, sx0 + half, sy0 + half),
            DOCS / "preview-passive-tree-nodes.png",
            [("패시브 %d / %d" % (passive, total), TEXT),
             ("전직 %d / 8" % ascend, GOLD_DIM)],
            class_row=[(c[1] or c[0], i == class_id) for i, c in enumerate(tree["classes"])],
            class_starts=[start],
            class_name=class_ko,
        )

    atlas_nodes = [n for n in atlas["nodes"]]
    axs = [n[4] for n in atlas_nodes]
    ays = [n[5] for n in atlas_nodes]
    draw_tree(
        atlas_meta, atlas_icons, [],
        (min(axs), min(ays), max(axs), max(ays)),
        DOCS / "preview-atlas-tree.png",
        [("아틀라스 0 / 123", TEXT)],
    )


if __name__ == "__main__":
    main()
