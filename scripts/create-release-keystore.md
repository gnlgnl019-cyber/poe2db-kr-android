# 릴리스(업로드) 키 만들기

플레이스토어에 올릴 AAB는 업로드 키로 서명해야 한다. 한 번 만든 키는 잃어버리면 같은 패키지로 다시 올릴 수 없으니 `keystore/` 폴더 밖 개인 보관소에도 백업해 둔다.

이 저장소에는 이미 `keystore/poe2db-upload.jks`(별칭 `poe2db-upload`, RSA 2048, 10000일)가 만들어져 있고,
비밀번호와 인증서 지문은 `keystore/README_PRIVATE.txt`에 적혀 있다. 두 파일 모두 `.gitignore`에 걸려 있다.
키를 새로 만들어야 하는 상황이 아니면 2번으로 넘어간다.

## 1. 키스토어 생성

```bash
mkdir -p keystore
keytool -genkeypair -v \
  -keystore keystore/poe2db-upload.jks -storetype JKS \
  -alias poe2db-upload \
  -keyalg RSA -keysize 2048 -sigalg SHA256withRSA -validity 10000 \
  -storepass '비밀번호' -keypass '비밀번호' \
  -dname "CN=PoE2DB KR, OU=Mobile, O=PoE2DB KR, L=Seoul, ST=Seoul, C=KR"
```

JDK 17 기준이며 `keytool`은 JDK의 `bin` 폴더에 있다.

## 2. 로컬 빌드에 연결

프로젝트 루트의 `keystore.properties`를 자동으로 읽는다. git에는 올라가지 않는다.
`storeFile`은 프로젝트 루트 기준 경로이고, 절대 경로를 적어도 된다.

```properties
storeFile=keystore/poe2db-upload.jks
storePassword=비밀번호
keyAlias=poe2db-upload
keyPassword=비밀번호
```

환경변수로 넘겨도 된다.

```bash
KEYSTORE_PATH=$PWD/keystore/poe2db-upload.jks \
KEYSTORE_PASSWORD=비밀번호 \
KEY_ALIAS=poe2db-upload \
KEY_PASSWORD=비밀번호 \
./gradlew bundleRelease
```

키가 없으면 릴리스 빌드는 디버그 키로 서명해 빌드만 통과시킨다. 이 경우 플레이스토어 업로드는 거절된다.

## 3. 서명 확인

```bash
jarsigner -verify dist/poe2db-kr-1.0.0-release-signed.aab     # jar verified.
keytool -printcert -jarfile dist/poe2db-kr-1.0.0-release-signed.aab | grep -E "Owner|SHA256"
```

`Owner`가 `CN=PoE2DB KR`이고 SHA-256 지문이 `keystore/README_PRIVATE.txt`의 값과 같아야 한다.
디버그 키로 서명되면 `CN=Android Debug`로 나온다.

## 4. GitHub Actions 연결

키스토어 파일은 바이너리라 시크릿에 그대로 넣을 수 없고, base64로 바꿔 넣는다.

```bash
base64 -w 0 keystore/poe2db-upload.jks > poe2db-upload.jks.b64
```

GitHub 저장소 → Settings → Secrets and variables → Actions 에서 다음 네 개를 등록한다.

| 이름 | 값 |
| --- | --- |
| `KEYSTORE_BASE64` | `poe2db-upload.jks.b64` 파일 내용 전체 |
| `KEYSTORE_PASSWORD` | 키스토어 비밀번호 |
| `KEY_ALIAS` | `poe2db-upload` |
| `KEY_PASSWORD` | 키 비밀번호 |

이후 `main`에 푸시하면 `app-debug.apk`와 `app-release.aab`가 아티팩트로 올라오고, `v1.0.0` 같은 태그를 푸시하면 GitHub Release에도 함께 첨부된다.

## 5. 플레이스토어 업로드

콘솔 → 앱 출시 → 프로덕션(또는 내부 테스트) → 새 버전 만들기에서 AAB를 올린다.
`versionCode`는 버전을 올릴 때마다 `app/build.gradle.kts`에서 1씩 증가시켜야 한다.
첫 업로드 뒤 콘솔에서 Play 앱 서명을 켜면 구글 쪽 서명 키가 따로 생기고, 이 업로드 키는 번들 서명에만 쓰인다.
