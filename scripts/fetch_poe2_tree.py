#!/usr/bin/env python3
"""poe2db 패시브 트리 데이터를 앱용으로 줄여 저장한다.

    python3 scripts/fetch_poe2_tree.py

결과: app/src/main/assets/poe2_tree_kr.json
      (직업·전직 이름 영문/한글, 모든 패시브 노드의 한글 이름·설명·좌표, 연결선,
       마스터리 효과)

앱은 이 파일로
  - PoB 빌드의 직업·전직을 poe2db 트리의 직업 번호로 옮기고,
  - 찍은 노드 목록을 poe2db 트리 주소(노드 배열)로 만들어 열며,
  - 앱 안의 트리 화면에 노드·연결선을 직접 그리고(찍은 노드는 황금색),
  - 패시브 탭에 한글 노드 목록을 보여 준다.

원본: https://poe2db.tw/data/passive-skill-tree/0.5/data_kr.json (영문판은 data_us.json)
캐시: .cache/tree/
"""
import json
import re
import sys
import urllib.request
from datetime import date
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
CACHE = ROOT / ".cache" / "tree"
OUT = ROOT / "app" / "src" / "main" / "assets" / "poe2_tree_kr.json"
URL = "https://poe2db.tw/data/passive-skill-tree/0.5/data_{lang}.json?17"
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
    if node.get("isJewelSocket"):
        return "j"
    if node.get("isKeystone"):
        return "k"
    if node.get("isMastery"):
        return "m"
    if node.get("isNotable"):
        return "n"
    if node.get("isAscendancyStart"):
        return "a"
    if "classStartIndex" in node:
        return "c"
    return "s"


def main():
    ko = load("kr")
    en = load("us")
    ko_nodes = ko["nodes"]
    en_nodes = en["nodes"]

    classes = []
    for i, ko_class in enumerate(ko["classes"]):
        en_class = en["classes"][i] if i < len(en["classes"]) else {}
        ko_asc = ko_class.get("ascendancies") or []
        en_asc = en_class.get("ascendancies") or []
        rows = []
        for j, asc in enumerate(ko_asc):
            en_name = en_asc[j].get("name") if j < len(en_asc) else ""
            rows.append([en_name or "", asc.get("name") or ""])
        classes.append([en_class.get("name") or ko_class.get("name") or "", ko_class.get("name") or "", rows])

    # 전직 이름(Druid1) → (직업 번호, 전직 번호). 전직 시작 노드를 찾을 때 쓴다.
    asc_index = {}
    for i, ko_class in enumerate(ko["classes"]):
        for j, asc in enumerate(ko_class.get("ascendancies") or []):
            if asc.get("id"):
                asc_index[asc["id"]] = (i, j + 1)

    nodes = []
    masteries = {}
    counts = {}
    class_starts = []
    asc_starts = []
    for key, node in ko_nodes.items():
        if key == "root":
            continue
        kind = kind_of(node)
        counts[kind] = counts.get(kind, 0) + 1
        en_node = en_nodes.get(key) or {}
        stats = "\n".join(strip_markup(s) for s in (node.get("stats") or []))
        nodes.append([
            int(key),
            en_node.get("name") or node.get("name") or "",
            node.get("name") or en_node.get("name") or "",
            stats,
            kind,
            node.get("ascendancyId") or "",
            round(node.get("x") or 0, 1),
            round(node.get("y") or 0, 1),
        ])
        if kind == "m" and node.get("masteryEffects"):
            rows = []
            for effect in node["masteryEffects"]:
                rows.append([
                    effect.get("effect"),
                    "\n".join(strip_markup(s) for s in (effect.get("stats") or [])),
                ])
            if rows:
                masteries[str(key)] = rows
        # 직업 시작 노드와 전직 시작 노드는 화면에서 트리 중심을 잡는 데 쓴다.
        for index in node.get("classStartIndex") or []:
            class_starts.append([index, int(key)])
        if kind == "a":
            pair = asc_index.get(node.get("ascendancyId") or "")
            if pair:
                asc_starts.append([pair[0], pair[1], int(key)])

    # 연결선: 양 끝이 모두 트리에 있는 노드일 때만 (root는 중심점이라 뺀다).
    edges = []
    for edge in ko.get("edges") or []:
        a, b = str(edge.get("from")), str(edge.get("to"))
        if a == b or a == "root" or b == "root":
            continue
        if a in ko_nodes and b in ko_nodes:
            edges.append([int(a), int(b)])

    # 범위는 실제 노드 좌표로 다시 잡는다. 원본 min/max에는 최근 추가된 전직 노드가 빠져 있다.
    xs = [n[6] for n in nodes]
    ys = [n[7] for n in nodes]
    bounds = [int(min(xs)) - 1, int(min(ys)) - 1, int(max(xs)) + 1, int(max(ys)) + 1]

    payload = {
        "source": "https://poe2db.tw/kr/passive-skill-tree/",
        "fetchedAt": date.today().isoformat(),
        "bounds": bounds,
        "classes": classes,
        "nodes": nodes,
        "edges": edges,
        "classStarts": class_starts,
        "ascStarts": asc_starts,
        "masteries": masteries,
    }
    OUT.write_text(json.dumps(payload, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")
    print(f"노드 종류 {counts}")
    print(f"연결선 {len(edges)} · 직업 시작 {len(class_starts)} · 전직 시작 {len(asc_starts)}")
    print(f"저장: {OUT} ({OUT.stat().st_size // 1024}KB) · 노드 {len(nodes)} · 마스터리 {len(masteries)}")


if __name__ == "__main__":
    sys.exit(main())
