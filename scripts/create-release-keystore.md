# 릴리스 키 만들기

플레이스토어에 올릴 AAB는 릴리스 키로 서명해야 한다. 한 번 만든 키는 잃어버리면 같은 앱을 다시 올릴 수 없으니 안전한 곳에 백업해 둔다.

## 1. 키스토어 생성

```bash
keytool -genkeypair -v \
  -keystore release.jks \
  -alias poe2dbkr \
  -keyalg RSA -keysize 4096 -validity 10000 \
  -storepass '비밀번호' -keypass '비밀번호' \
  -dname "CN=PoE2DB KR, OU=Mobile, O=Independent, L=Seoul, C=KR"
```

JDK 17 기준이며 `keytool`은 JDK의 `bin` 폴더에 있다.

## 2. 로컬 빌드에 연결

프로젝트 루트에 `keystore.properties`를 만들면 git에 올라가지 않고 자동으로 읽힌다.

```properties
storeFile=/절대경로/release.jks
storePassword=비밀번호
keyAlias=poe2dbkr
keyPassword=비밀번호
```

또는 환경변수로 넘겨도 된다.

```bash
KEYSTORE_PATH=/절대경로/release.jks \
KEYSTORE_PASSWORD=비밀번호 \
KEY_ALIAS=poe2dbkr \
KEY_PASSWORD=비밀번호 \
./gradlew bundleRelease
```

키가 없으면 릴리스 빌드는 디버그 키로 서명해 빌드만 통과시킨다. 이 경우 플레이스토어 업로드는 거절되므로 실제 배포 전에는 반드시 키를 연결한다.

## 3. GitHub Actions 연결

키스토어 파일은 바이너리라 시크릿에 그대로 넣을 수 없고, base64로 바꿔 넣는다.

```bash
base64 -w 0 release.jks > release.jks.b64
```

GitHub 저장소 → Settings → Secrets and variables → Actions 에서 다음 네 개를 등록한다.

| 이름 | 값 |
| --- | --- |
| `KEYSTORE_BASE64` | `release.jks.b64` 파일 내용 전체 |
| `KEYSTORE_PASSWORD` | 키스토어 비밀번호 |
| `KEY_ALIAS` | `poe2dbkr` |
| `KEY_PASSWORD` | 키 비밀번호 |

이후 `main`에 푸시하면 `app-debug.apk`와 `app-release.aab`가 아티팩트로 올라오고, `v1.0.0` 같은 태그를 푸시하면 GitHub Release에도 함께 첨부된다.

## 4. 플레이스토어 업로드

콘솔 → 앱 출시 → 프로덕션(또는 내부 테스트) → 새 버전 만들기에서 `app-release.aab`를 올린다. `versionCode`는 버전을 올릴 때마다 `app/build.gradle.kts`에서 1씩 증가시켜야 한다.
