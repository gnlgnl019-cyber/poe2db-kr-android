"""그래픽 스크립트가 쓰는 글꼴 파일을 찾는다.

리눅스 배포판·맥·윈도마다 글꼴이 놓이는 자리가 달라서 흔한 폴더를 차례로 뒤진다.
찾지 못하면 어떤 글꼴이 필요한지 알려 주고 멈춘다.
"""

from functools import lru_cache
from pathlib import Path

FONT_DIRS = (
    Path("/usr/share/fonts"),
    Path("/usr/local/share/fonts"),
    Path.home() / ".fonts",
    Path.home() / ".local/share/fonts",
    Path("/Library/Fonts"),
    Path("/System/Library/Fonts"),
    Path("C:/Windows/Fonts"),
)

KOREAN = (
    "NotoSansKR-Regular.ttf",
    "NotoSansKR-Regular.otf",
    "NotoSansCJKkr-Regular.otf",
    "NotoSansCJK-Regular.ttc",
    "NanumGothic.ttf",
    "NanumBarunGothic.ttf",
    "malgun.ttf",
    "AppleSDGothicNeo.ttc",
)

BOLD_SANS = (
    "NotoSansKR-Bold.ttf",
    "LiberationSans-Bold.ttf",
    "DejaVuSans-Bold.ttf",
    "arialbd.ttf",
)

BOLD_SERIF = (
    "LiberationSerif-Bold.ttf",
    "DejaVuSerif-Bold.ttf",
    "NotoSerifKR-Bold.otf",
    "timesbd.ttf",
)


@lru_cache(maxsize=None)
def find(names):
    """이름 목록에서 처음 찾은 글꼴 파일. 없으면 None."""
    for name in names:
        for root in FONT_DIRS:
            if not root.is_dir():
                continue
            for path in root.rglob(name):
                if path.is_file():
                    return path
    return None


def need(names, what):
    """찾지 못하면 멈춘다. 그래픽을 반쯤 그려 놓고 넘어가는 것보다 낫다."""
    path = find(names)
    if path is None:
        raise SystemExit(
            f"{what} 글꼴을 찾지 못했습니다. {' 또는 '.join(names[:3])}을 설치하거나 "
            "fonts.py의 FONT_DIRS에 글꼴 폴더를 더하세요."
        )
    return path
