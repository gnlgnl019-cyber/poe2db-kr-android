# PoE2DB KR

패스 오브 엑자일 2 한국어 자료를 폰에서 빠르게 찾아보는 앱입니다. poe2db.tw/kr의 스킬 젬, 보조 젬, 유니크 아이템, 화폐, 전직, 키워드를 미리 받아 앱에 넣어 두고, 목록은 네이티브로 그리며 상세 페이지는 웹뷰로 엽니다.

## 화면 구성

- **DB** — 전체 4,354건. 무기·방어구·장신구·룬·화폐·유니크·스킬 젬·보조 젬·전직·키워드 칩으로 좁혀 볼 수 있습니다.
- **시세** — poe.ninja 실시간 시세 (화폐·룬·에센스·영혼 핵 등 9종 교환 품목과 고유 장비)
- **패시브** — poe2db.tw 패시브·아틀라스 트리 뷰어 (핀치 줌). PoB 빌드를 불러온 뒤에는 그 캐릭터가 찍은 노드가 황금색으로 켜진 트리가 열립니다.
- **PoB** — 개요 / 스킬 / 장비 / 설정 / 패시브 / 전투 / 메타
- **즐겨찾기** — 별표로 저장한 항목

### 전투 시뮬레이터

PoB 탭에서 불러온 빌드의 DPS·공격 속도·치명타·생명력·보호막·방어도·회피·저항을 그대로 가져와 2D 스틱맨 전투로 돌려 봅니다.

- 16T 맵핑(10 / 25 / 50 / 100마리, 증폭 모드) 또는 우버 보스 5종(재의 중재자·크세쉬·안개 속의 왕·자로크·올로스)
- 메인 스킬 이름으로 이펙트를 자동 선택합니다: 얼음 화살(파편 부채꼴), 번개(연쇄 번개·낙뢰 기둥), 화염(2중 폭발 링), 근접(검기·충격파), 카오스(도트 오라), 소환수(미니언 타격). 칩으로 직접 바꿔 볼 수도 있습니다.
- 공격 속도에 맞춘 공격 모션, 치명타 화면 흔들림과 `CRIT!` 숫자, 처치 파편, 적 처치/사망 시 결과 요약(소요 시간·가한 피해·실전 DPS·치명타·피격/회피·생존율)
- 점멸 타격·질풍·충전 지팡이처럼 적에게 붙는 순간이동 근접기는 걸어가지 않고, 공격할 때마다 적 옆으로 텔레포트해 벱니다. 지나온 자리에 푸른·금빛 잔상 2~3개와 섬광 선이 남습니다.
- 초당 4회를 넘는 고속 공속은 한 프레임에 여러 타를 누적해 타격이 씹히지 않고, 베기 궤적도 2~3겹으로 겹쳐 그립니다. (초당 30회까지 반영)
- 전투 중 1배속/2배속, 일시정지, 초기화

### 우버 보스 패턴

보스마다 인게임에서 쓰는 대표 스킬을 자기 패턴으로 씁니다. 체력 50% 이하에서 2페이즈로 넘어가고, 패턴이 강화됩니다.

| 보스 | 1페이즈 | 2페이즈 |
|---|---|---|
| 재의 중재자 | 화염 검기(직선 예고), 불기둥 | 심판의 안전지대(전장 폭발·가운데만 안전), 유성 낙하 |
| 균열 군주 크세쉬 | 균열 구체 연사, 거대 손 내려찍기 | 균열 쫄 소환 |
| 안개 속의 왕 | 의식 투사체, 순간이동 | 저주 장판(가운데만 안전한 고리), 분신 |
| 올로스 | 룬 검기, 빙결 파편 | 룬 보호막(피해 흡수), 광역 냉기 폭발 |
| 자로크 | 시간 구체, 모래시계(십자 예고) | 시간 왜곡, 번개 구슬 회전 |

- 스킬이 떨어지기 전에 바닥에 경고 장판(원·직선·고리·십자)이 차오르고, 안에 있으면 방어력·저항·회피로 계산한 실제 피해를 받습니다
- 플레이어는 장판이 떨어지기 직전 구르기로 빠져나갑니다
- 전장 HUD에 `P1/P2`와 지금 쓰는 패턴 이름이 표시됩니다
- 보스 5종은 각자의 실루엣(화염 후광·균열 팔·사슴뿔·룬 방패·모래시계)으로 그려지고, 잔몹도 근접형(뿔·도끼)·술사형(후드·지팡이)·희귀 엘리트(왕관·오라)로 구분됩니다

### 메타젬

PoB 2 빌드에 들어 있는 메타젬을 **전부** 인식해 전투에서 동시에 굴립니다. 첫 그룹만 쓰지 않습니다.

- 치명타 시 시전 · 감전 시 시전 · 빙결 시 시전 · 점화 시 시전 · 원소 상태이상 시 시전 · 원소 기원 · 소환수 사망 시 시전 · 피격 시 시전 · 기절 시 시전 · 회피 시 시전 · 근접 처치 시 시전
- 전령 4종(천둥·얼음·재·역병)과 신성모독
- 영문 이름·한글 이름·PoB `skillId` 모두로 판별합니다
- 적중할 때마다 치명타·감전·빙결·점화·처치·피격·회피 사건을 실제로 판정해 각 메타젬의 에너지를 채우고, 가득 차면 연결된 스킬(Comet·Arc·Fireball·Frost Bomb 등)이 이펙트와 피해로 발동합니다
- 전장 HUD와 결과 요약에 메타젬별 발동 횟수·피해량을 표시합니다

수치는 PoB 기준값에 재생·보호막 충전·회피를 일반적인 값으로 가정한 간이 시각 시뮬레이션이며 실제 게임 판정과 다릅니다.

검색창은 이름, 영문 이름, 태그, 설명을 한 번에 훑습니다. 카드를 누르면 상세 웹뷰가 열리고, 웹뷰에는 모바일용 CSS를 넣어 상단 메뉴와 배너를 걷어 내고 넓은 표를 가로 스크롤로 감쌉니다.

### 메타 랭킹

PoB 탭의 **메타** 페이지는 리그 전직 점유율과 인기 빌드 DPS를 스냅샷으로 보여 줍니다.
poe.ninja 빌드 통계는 공개 API가 아니라 앱이 직접 받아오지 않고, U4N 티어리스트·PoB Archives에
공개된 값을 `Meta.kt`에 옮겨 두고 화면에 출처와 기준일을 함께 적습니다.
전직·스킬 이름은 게임 한국어 표기(poe2db.tw/kr)로 적고, DPS는 만 단위로 읽습니다.
poe.ninja 빌드 랭킹은 같은 페이지의 버튼으로 앱 안에서 실시간으로 열 수 있습니다.

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

업로드 키는 `keystore/poe2db-upload.jks`에 있고, 루트의 `keystore.properties`를 읽어 서명합니다(둘 다 git 제외).
키가 없으면 릴리스 빌드는 디버그 키로 서명해 빌드만 통과시키므로, 배포 전에는 `scripts/create-release-keystore.md`를 확인하세요.

```bash
./gradlew bundleRelease
jarsigner -verify app/build/outputs/bundle/release/app-release.aab
```

스토어에 올릴 산출물은 `dist/`에 모아 둡니다.

```bash
dist/poe2db-kr-1.0.0-release-signed.aab   # 업로드 키로 서명한 AAB
dist/poe2db-kr-1.0.0-debug.apk            # 기기에 설치해 볼 APK
```

## 데이터 갱신

수록 자료는 `app/src/main/assets/poe2db_kr.json`에 들어 있습니다. 게임 패치로 수치가 바뀌면 다시 긁어서 앱을 새로 올립니다.

```bash
python3 scripts/fetch_poe2db.py        # poe2db.tw/kr 파싱 → assets JSON
python3 scripts/fetch_poe2_i18n.py     # 시세·장비 한글 사전 → assets/poe2_kr_i18n.json
python3 scripts/fetch_poe2_tree.py     # 패시브 트리 노드·직업 이름 → assets/poe2_tree_kr.json
python3 scripts/make_store_assets.py   # 아이콘, 스토어 그래픽
python3 scripts/make_store_screenshots.py  # 스토어 스크린샷 5장
```

`fetch_poe2_i18n.py`는 poe.ninja 시세 응답의 영문 이름·옵션과 poe2db 한국어 페이지를 대조해
한글 이름 4,700여 개, 베이스 1,790여 개, 아이콘 3,880여 개, 옵션 문장 틀 1,880여 개를 만듭니다.
장비 옵션은 poe2db 분류 페이지(Modifiers Calc)의 영문판·한글판을 줄마다 짝지어 접사·에센스·
훼손·타락·룬·결속 문장을 모두 담고, 그 표에 없는 고유·플라스크·호신부 옵션은 고유 아이템
페이지의 두 언어 옵션을 대조해 채웁니다. `props`/`tags` 항목은 품질·방어도·요구 사항 같은
아이템 머리말과 `(implicit)`·`(crafted)` 꼬리표의 한글 표기입니다.
패치로 신규 아이템이 늘면 다시 돌려 사전을 갱신하세요 (`.cache/i18n/` 캐시).

`fetch_poe2_tree.py`는 poe2db 패시브 트리 자료(`data_kr.json`/`data_us.json`)에서 직업·전직 이름과
주요 패시브(키스톤·주목할 패시브·마스터리·전직 노드)의 한글 이름·설명만 추려 190KB 남짓으로 만듭니다.
앱은 이 파일로 PoB의 `Spec nodes` 목록을 poe2db 트리 주소로 바꾸고, 패시브 탭에 한글 노드 목록을 띄웁니다.

`fetch_poe2db.py`는 lxml만 있으면 됩니다. 내려받은 페이지는 `.cache/poe2db/`에 남아 다음 실행 때는 목록 페이지만 다시 받습니다. 젬 설명은 젬별 툴팁 조각을 한 번씩 더 받아 채웁니다.

## 프로젝트 구조

```
app/src/main/java/com/sunuhwi/poe2dbkr/
  MainActivity.kt     화면 구성, 검색, 웹뷰, 뒤로가기 처리, PoB·전투 화면 연결
  Catalog.kt          내장 JSON 로딩과 카테고리 정의
  CatalogAdapter.kt   카드 목록 어댑터 (Coil 아이콘 로딩)
  BookmarkStore.kt    즐겨찾기 SharedPreferences 저장
  MobileCss.kt        웹뷰에 넣는 모바일 CSS와 검색 스크립트
  Pob.kt              PoB 공유 코드(zlib+base64)와 XML 파싱·인코딩
  PobCalculator.kt    장비·보조 젬·전투 설정 변동을 반영한 스탯 재계산
  PobAdapters.kt      PoB 장비·유니크 목록 어댑터
  BattleSim.kt        전투 시뮬레이션 (적·보스, 피해 판정, 상태이상, 메타젬 발동)
  PobTriggers.kt      메타젬 인식·추출 (이름·skillId, 조건별 에너지)
  BattleSetup.kt      빌드 → 전투 수치 변환 (화면과 테스트가 함께 씀)
  BattleCanvasView.kt 전장·스틱맨·이펙트 캔버스 (Choreographer 프레임 루프)
  WeaponTypes.kt      무기 판정과 공격 형태(근접·활·쇠뇌·주문)
  I18n.kt             한글 사전 로딩·이름/베이스/아이콘/옵션 변환
  PobLocale.kt        PoB 장비를 한글 이름·옵션으로 표시
  Poe2Tree.kt         패시브 트리 자료 로딩, PoB 노드 → poe2db 트리 주소, 한글 요약
  Meta.kt             전직 점유율·인기 빌드 스냅샷
scripts/
  fetch_poe2db.py     poe2db.tw 수집 스크립트
  fetch_poe2_i18n.py  poe.ninja·poe2db 대조 한글 사전 생성
  fetch_poe2_tree.py  패시브 트리 한글 자료 생성
  make_store_assets.py 아이콘·스토어 그래픽 생성
  make_store_screenshots.py 스토어 스크린샷(1080×1920) 생성
store-assets/         스토어 등록용 아이콘, 그래픽, 문구, 스크린샷, 개인정보 처리방침
```

## 배포

릴리스 AAB는 로컬에서 서명해 플레이스토어 콘솔에 직접 올립니다.

```bash
./gradlew bundleRelease
cp app/build/outputs/bundle/release/app-release.aab dist/poe2db-kr-1.0.0-release-signed.aab
jarsigner -verify dist/poe2db-kr-1.0.0-release-signed.aab
```

`versionCode`는 업로드할 때마다 `app/build.gradle.kts`에서 1씩 올립니다.
업로드 키와 비밀번호는 `keystore/`(git 제외)에만 두고, 저장소나 이슈에 적지 않습니다.
GitHub Actions로 빌드하려면 같은 값을 저장소 시크릿 `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`,
`KEY_ALIAS`, `KEY_PASSWORD`로 넣으면 됩니다.

## 고지

poe2db.tw와 Grinding Gear Games가 만든 공식 앱이 아닙니다. 게임 자료와 이미지의 권리는 원저작자에게 있고, 이 앱은 열람을 돕는 목적으로만 배포합니다. 삭제 요청이 오면 해당 자료를 내립니다.
