#!/usr/bin/env python3
"""poe2db 아틀라스 패시브 트리를 앱용으로 줄여 저장한다.

    python3 scripts/fetch_poe2_atlas.py

결과: app/src/main/assets/poe2_atlas_kr.json
      app/src/main/assets/atlas_icons.webp

원본: https://poe2db.tw/data/atlas-skill-tree/0.5/data_kr.json
캐시: .cache/atlas/
"""
import json
import math
import re
import sys
import urllib.request
from datetime import date
from pathlib import Path

from tree_icons import build as build_icons

ROOT = Path(__file__).resolve().parent.parent
CACHE = ROOT / ".cache" / "atlas"
OUT = ROOT / "app" / "src" / "main" / "assets" / "poe2_atlas_kr.json"
ICON_OUT = ROOT / "app" / "src" / "main" / "assets" / "atlas_icons.webp"
ICON_CACHE = ROOT / ".cache" / "icons"
URL = "https://poe2db.tw/data/atlas-skill-tree/0.5/data_{lang}.json"
UA = (
    "Mozilla/5.0 (Linux; Android 14; SM-S918N) AppleWebKit/537.36 "
    "(KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
)

PICK = re.compile(r"\[([^\]|]*)\|([^\]]*)\]")
PLAIN = re.compile(r"\[([^\]]*)\]")
TAG = re.compile(r"<[^>]+>")


def strip_markup(text):
    t = (text or "").replace("<br/>", "\n").replace("<br>", "\n")
    t = TAG.sub("", t)
    t = t.replace("&nbsp;", " ").replace("&amp;", "&").replace("&quot;", '"')
    t = PICK.sub(lambda m: (m.group(2) or m.group(1)).strip(), t)
    t = PLAIN.sub(lambda m: m.group(1).strip(), t)
    return t.strip()


def load(lang):
    CACHE.mkdir(parents=True, exist_ok=True)
    path = CACHE / f"data_{lang}.json"
    if not path.exists() or path.stat().st_size < 100000:
        req = urllib.request.Request(URL.format(lang=lang), headers={"User-Agent": UA})
        with urllib.request.urlopen(req, timeout=60) as resp:
            path.write_bytes(resp.read())
    return json.loads(path.read_text(encoding="utf-8"))


def kind_of(node):
    if node.get("isKeystone"):
        return "k"
    if node.get("isMastery"):
        return "m"
    if node.get("isNotable"):
        return "n"
    return "s"


def place(node, groups, radii, per_orbit):
    """묶음 중심과 궤도·순번으로 노드 자리를 계산한다 (poe2db 트리와 같은 규칙)."""
    group = groups.get(str(node.get("group"))) or {}
    cx = float(group.get("x") or 0.0)
    cy = float(group.get("y") or 0.0)
    orbit = int(node.get("orbit") or 0)
    index = int(node.get("orbitIndex") or 0)
    radius = radii[orbit] if 0 <= orbit < len(radii) else 0
    count = per_orbit[orbit] if 0 <= orbit < len(per_orbit) else 0
    if radius == 0 or count == 0:
        return round(cx, 1), round(cy, 1)
    angle = math.radians(index * 360.0 / count)
    return round(cx + radius * math.sin(angle), 1), round(cy - radius * math.cos(angle), 1)


def main():
    ko = load("kr")
    nodes_raw = ko["nodes"]
    groups = ko.get("groups") or {}
    constants = ko.get("constants") or {}
    radii = constants.get("orbitRadii") or [0]
    per_orbit = constants.get("skillsPerOrbit") or [1]
    icon_rects = build_icons(
        [node.get("icon") for node in nodes_raw.values() if node.get("icon")],
        ICON_CACHE,
        ICON_OUT,
    )

    nodes = []
    for key, node in nodes_raw.items():
        if key == "root":
            continue
        stats = "\n".join(strip_markup(s) for s in (node.get("stats") or []))
        x, y = place(node, groups, radii, per_orbit)
        nodes.append([
            int(key),
            strip_markup(node.get("name") or ""),
            stats,
            kind_of(node),
            x,
            y,
            icon_rects.get(node.get("icon") or ""),
        ])

    present = {row[0] for row in nodes}
    seen = set()
    edges = []

    def link(a, b):
        if a == b or a not in present or b not in present:
            return
        pair = (min(a, b), max(a, b))
        if pair in seen:
            return
        seen.add(pair)
        edges.append([pair[0], pair[1]])

    for key, node in nodes_raw.items():
        if key == "root":
            continue
        here = int(key)
        for link_id in node.get("out") or []:
            link(here, int(link_id))
        for link_id in node.get("in") or []:
            link(here, int(link_id))
        # 트리 끝을 잇는 선은 노드마다 따로 적혀 있다.
        for item in node.get("connections") or []:
            link_id = item.get("id") if isinstance(item, dict) else item
            if link_id is not None:
                link(here, int(link_id))

    xs = [row[4] for row in nodes]
    ys = [row[5] for row in nodes]
    bounds = [int(min(xs)) - 1, int(min(ys)) - 1, int(max(xs)) + 1, int(max(ys)) + 1]
    points = ko.get("points") or {}

    payload = {
        "source": "https://poe2db.tw/kr/atlas-skill-tree/",
        "fetchedAt": date.today().isoformat(),
        "bounds": bounds,
        "points": {"total": points.get("totalPoints") or 0},
        "nodes": nodes,
        "edges": edges,
        "icons": "atlas_icons.webp",
    }
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(json.dumps(payload, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")
    print(f"아틀라스 노드 {len(nodes)} · 연결선 {len(edges)} · 포인트 {payload['points']['total']}")
    print(f"저장: {OUT} ({OUT.stat().st_size // 1024}KB)")


if __name__ == "__main__":
    sys.exit(main())
