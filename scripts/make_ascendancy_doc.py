#!/usr/bin/env python3
"""전직 패시브 노드 분석표를 docs/ascendancy-effects.md로 만든다.

    ./gradlew :app:testDebugUnitTest --tests "*AscendancyEffectsTest*"
    python3 scripts/make_ascendancy_doc.py

분석 결과(전직별 노드 수, 주요 노드 옵션, 소형 노드 옵션, 어디에 반영되는지)는
Kotlin 쪽 분류기(AscendancyEffects.kt)가 테스트에서 찍은 줄을 그대로 받아 쓴다.
같은 규칙을 두 번 적지 않으려고 이렇게 한다.
"""
import re
import sys
from collections import Counter, OrderedDict
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
ASSET = ROOT / "app" / "src" / "main" / "assets" / "poe2_tree_kr.json"
RESULT = ROOT / "app" / "build" / "test-results" / "testDebugUnitTest" / \
    "TEST-com.sunuhwi.poe2dbkr.AscendancyEffectsTest.xml"
OUT = ROOT / "docs" / "ascendancy-effects.md"

LABEL = {"SIM": "시뮬", "POB": "PoB 합산", "NONE": "표시만"}


def read_report():
    if not RESULT.is_file():
        sys.exit(f"테스트 결과가 없습니다: {RESULT}\n먼저 AscendancyEffectsTest를 실행하세요.")
    text = RESULT.read_text(encoding="utf-8")
    body = re.search(r"<system-out><!\[CDATA\[(.*?)\]\]></system-out>", text, re.S)
    if not body:
        sys.exit("테스트 출력을 찾지 못했습니다.")
    asc, nodes, smalls = [], [], []
    for raw in body.group(1).split("\n"):
        line = raw.strip()
        if line.startswith("[ASC-ASC]"):
            code, class_ko, asc_ko, count, notables, sim = line[len("[ASC-ASC]"):].strip().split("|")
            asc.append((code, class_ko, asc_ko, int(count), int(notables), int(sim)))
        elif line.startswith("[ASC-NODE]"):
            code, node_id, scope, name, text = line[len("[ASC-NODE]"):].strip().split("|", 4)
            nodes.append((code, node_id, scope, name, text))
        elif line.startswith("[ASC-SMALL]"):
            scope, text = line[len("[ASC-SMALL]"):].strip().split("|", 1)
            smalls.append((scope, text))
    if not asc:
        sys.exit("전직 분석 줄이 비었습니다. 테스트가 통과했는지 확인하세요.")
    return asc, nodes, smalls


def asset_counts():
    """자료에서 전직별 공개 노드 수·빈 자리 수와 소형 노드 옵션 횟수를 센다."""
    import json
    data = json.loads(ASSET.read_text(encoding="utf-8"))
    real, empty, counter = Counter(), Counter(), Counter()
    for row in data["nodes"]:
        if len(row) < 6 or not row[5]:
            continue
        name, stats = (row[1] or "").strip(), (row[3] or "").strip()
        if not name and not stats:
            empty[row[5]] += 1
            continue
        real[row[5]] += 1
        if row[4] == "s":
            for line in stats.split("\n"):
                if line.strip():
                    counter[line.strip()] += 1
    return real, empty, counter


def main():
    asc, nodes, smalls = read_report()
    real, empty, counts = asset_counts()
    by_code = OrderedDict()
    for code, node_id, scope, name, text in nodes:
        by_code.setdefault(code, []).append((node_id, scope, name, text))

    total_nodes = sum(real.values())
    total_notables = sum(a[4] for a in asc)
    total_sim = sum(a[5] for a in asc)
    total_empty = sum(empty.values())
    lines = []
    lines.append("---")
    lines.append("title: \"PoE2 전직 패시브 노드 분석과 전투 시뮬레이터 적용\"")
    lines.append("kind: note")
    lines.append("---")
    lines.append("")
    lines.append("# PoE2 전직 패시브 노드 분석과 시뮬레이터 적용")
    lines.append("")
    lines.append("poe2db 한글 트리 자료(0.5, 2026-11-23)의 전직 노드를 전부 읽어 옵션을 분류한 표다. "
                 "앱의 `AscendancyEffects.kt`가 이 분류대로 전투 수치를 계산한다.")
    lines.append("")
    lines.append(f"- 직업 12종 · 전직 36종 · 전직 노드 {total_nodes + total_empty:,}개(시작 노드 36개 포함)")
    lines.append(f"- 이 중 이름과 옵션이 공개된 노드 {total_nodes:,}개 · 미공개 빈 자리 {total_empty}개")
    lines.append(f"- 주요(전직 고유) 노드 {total_notables}개 · 소형 노드 {total_nodes - total_notables - 36}개")
    lines.append(f"- 전투 시뮬레이터가 직접 계산하는 노드 {total_sim}개, 나머지는 PoB 수치에 이미 포함된 옵션")
    lines.append("")
    lines.append("## 반영 방식")
    lines.append("")
    lines.append("| 구분 | 뜻 | 처리 |")
    lines.append("| --- | --- | --- |")
    lines.append("| 시뮬 | PoB의 단일 타격 계산으로 드러나지 않는 효과 | 부여 스킬 발동, 추가 투사체, 상태이상 확률, 받는 피해 감폭·전환, "
                 "보호막 재충전·재생·흡수, 효과 범위, 메타 에너지 |")
    lines.append("| PoB 합산 | 생명력·DPS·저항처럼 PoB가 이미 계산해 넘긴 수치 | 다시 더하지 않고 목록에만 표시 |")
    lines.append("| 표시만 | 패시브 포인트, 룬 홈, 문구뿐인 효과 | 화면에만 표시 |")
    lines.append("")
    lines.append("## 전직별 요약")
    lines.append("")
    lines.append("| 전직 | 직업 | 공개 노드 | 주요 | 시뮬 반영 | 미공개 |")
    lines.append("| --- | --- | ---: | ---: | ---: | ---: |")
    for code, class_ko, asc_ko, count, notables, sim in asc:
        blank = empty.get(code, 0)
        lines.append(f"| {asc_ko} ({code}) | {class_ko} | {real.get(code, count)} | {notables} | {sim} | {blank} |")
    lines.append("")
    lines.append("## 전직별 주요 노드")
    for code, class_ko, asc_ko, *_ in asc:
        rows = by_code.get(code, [])
        lines.append("")
        lines.append(f"### {asc_ko} · {class_ko} ({code})")
        if not rows:
            lines.append("")
            lines.append("주요 노드가 아직 공개되지 않았다(빈 자리만 있음).")
            continue
        lines.append("")
        lines.append("| ID | 노드 | 옵션 | 반영 |")
        lines.append("| ---: | --- | --- | --- |")
        for node_id, scope, name, text in rows:
            text = text.replace("|", "／")
            lines.append(f"| {node_id} | {name} | {text} | {LABEL.get(scope, scope)} |")
    lines.append("")
    lines.append("## 소형 전직 노드 옵션")
    lines.append("")
    lines.append("같은 옵션이 여러 전직에 나오므로 한 번만 적고 횟수를 붙였다.")
    lines.append("")
    lines.append("| 옵션 | 전직 노드 수 | 반영 |")
    lines.append("| --- | ---: | --- |")
    for scope, text in sorted(smalls, key=lambda x: (-counts[x[1]], x[1])):
        lines.append(f"| {text.replace('|', '／')} | {counts[text]} | {LABEL.get(scope, scope)} |")
    lines.append("")
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text("\n".join(lines), encoding="utf-8")
    print(f"저장: {OUT} ({len(lines)}줄)")


if __name__ == "__main__":
    main()
