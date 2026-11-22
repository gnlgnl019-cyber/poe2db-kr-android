# PoE2DB KR

패스 오브 엑자일 2 한국어 자료를 폰에서 빠르게 찾아보는 앱입니다. poe2db.tw/kr의 스킬 젬, 보조 젬, 유니크 아이템, 화폐, 전직, 키워드를 미리 받아 앱에 넣어 두고, 목록은 네이티브로 그리며 상세 페이지는 웹뷰로 엽니다.

## 화면 구성

- **홈** — 전체 2,468건. 카테고리 칩으로 좁혀 볼 수 있습니다.
- **스킬** — 스킬 젬 399개, 보조 젬 558개
- **유니크** — 463개
- **화폐·기타** — 화폐 38종, 키워드 766개, 전직·직업 244개
- **즐겨찾기** — 별표로 저장한 항목

검색창은 이름, 영문 이름, 태그, 설명을 한 번에 훑습니다. 카드를 누르면 상세 웹뷰가 열리고, 웹뷰에는 모바일용 CSS를 넣어 상단 메뉴와 배너를 걷어 내고 넓은 표를 가로 스크롤로 감쌉니다.

## 빌드

JDK 17과 Android SDK 35가 필요합니다.

```bash
./gradlew assembleDebug      # app/build/outputs/apk/debug/app-debug.apk
./gradlew bundleRelease      # app/build/outputs/bundle/release/app-release.aab
```

`local.properties`에 SDK 경로를 적어 둡니다.

```properties
sdk.dir=/path/to/android-sdk
```

릴리스 키가 없으면 릴리스 빌드는 디버그 키로 서명합니다. 빌드는 통과하지만 플레이스토어 업로드는 되지 않으니, 배포 전에는 `scripts/create-release-keystore.md`를 따라 키를 연결하세요.

## 데이터 갱신

수록 자료는 `app/src/main/assets/poe2db_kr.json`에 들어 있습니다. 게임 패치로 수치가 바뀌면 다시 긁어서 앱을 새로 올립니다.

```bash
python3 scripts/fetch_poe2db.py        # poe2db.tw/kr 파싱 → assets JSON
python3 scripts/make_store_assets.py   # 아이콘, 스토어 그래픽
```

`fetch_poe2db.py`는 lxml만 있으면 됩니다. 내려받은 페이지는 `.cache/poe2db/`에 남아 다음 실행 때는 목록 페이지만 다시 받습니다. 젬 설명은 젬별 툴팁 조각을 한 번씩 더 받아 채웁니다.

## 프로젝트 구조

```
app/src/main/java/com/sunuhwi/poe2dbkr/
  MainActivity.kt     화면 구성, 검색, 웹뷰, 뒤로가기 처리
  Catalog.kt          내장 JSON 로딩과 카테고리 정의
  CatalogAdapter.kt   카드 목록 어댑터 (Coil 아이콘 로딩)
  BookmarkStore.kt    즐겨찾기 SharedPreferences 저장
  MobileCss.kt        웹뷰에 넣는 모바일 CSS와 검색 스크립트
scripts/
  fetch_poe2db.py     poe2db.tw 수집 스크립트
  make_store_assets.py 아이콘·스토어 그래픽 생성
store-assets/         스토어 등록용 아이콘, 그래픽, 문구, 개인정보 처리방침
```

## 배포

`.github/workflows/build-apk.yml`이 `main` 푸시와 태그 푸시에서 APK와 AAB를 만들고 아티팩트로 올립니다. `v1.0.0` 태그를 밀면 GitHub Release에도 첨부됩니다. 서명 키는 저장소 시크릿 `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`로 넣습니다.

## 고지

poe2db.tw와 Grinding Gear Games가 만든 공식 앱이 아닙니다. 게임 자료와 이미지의 권리는 원저작자에게 있고, 이 앱은 열람을 돕는 목적으로만 배포합니다. 삭제 요청이 오면 해당 자료를 내립니다.
