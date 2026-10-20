# 배포 구성 — GitHub Release + Firebase 버전 기록

이 문서는 TracePhone을 **GitHub에 올리고, 태그를 밀면 APK가 GitHub Release에 붙고,
Firebase에 적어 둔 버전 기록을 앱이 읽어 새 버전을 알리게** 만드는 전 과정이다.

**Google Cloud는 쓰지 않는다.** 서비스 계정도, JSON 키도 필요 없다.

```
  내 PC에서 git push --tags
          ↓
  GitHub Actions
     단위 테스트 → release APK 빌드·서명 → 서명 지문 확인
          ↓
  GitHub Release 에 APK 첨부          ← 받는 사람은 여기서 내려받는다
          ↓
  Firebase Realtime Database 의 latest 경로에 버전 JSON 붙여넣기 (손으로 한 번)
          ↓
  앱 설정 화면이 HTTPS GET 한 번으로 읽고 "새 버전 0.3.0 이 나왔습니다" 표시
```

역할이 이렇게 갈린다.

| | 맡는 일 | 맡지 않는 일 |
|---|---|---|
| **GitHub** | 소스, 빌드, 서명, **APK 파일 보관과 전달** | 버전 안내 |
| **Firebase** | **"지금 최신이 무엇인가"라는 한 줄의 기록** | 파일 보관, 빌드, 배포 |

---

## 0. 시작 전에 반드시 읽을 것

### (1) 서명이 바뀌면 누적 기록이 사라진다

이 앱은 시스템이 90일만 보관하고 지우는 사용 이력을 영구 누적한다. 실기기에 119일,
15,751행이 있고 **복구할 방법이 없다.**

안드로이드는 **서명이 다른 APK로 덮어쓰기를 거부**한다. 설치하려면 앱을 지워야 하고,
지우면 DB도 사라진다. 그래서 새 키를 만들지 않고 **기기에 이미 깔린 debug 빌드와 같은 키**로
release를 서명한다.

| 항목 | 값 |
|---|---|
| 키스토어 | `~/.android/debug.keystore` |
| 별칭 | `androiddebugkey` |
| 비밀번호 | `android` (Android SDK가 정한 공개값이라 비밀이 아니다) |
| 지문 (SHA-1) | `0a49f14ece7d3dbe70e9ebb5e9797f753e81467e` |
| 유효기간 | 2056-08-26까지 |

대가는 하나다. **이 키로 서명한 앱은 Play Store에 올릴 수 없다.**

### (2) release 빌드로 옮기면 지금의 DB 백업 경로가 막힌다

지금까지 쓴 `adb exec-out run-as`는 앱이 디버그 가능할 때만 동작한다.
매니페스트를 비교하면 debug는 `android:debuggable="true"`인데 **release에는 그 속성이 없다.**

| 대상 | 빌드 | 이유 |
|---|---|---|
| 내 S23+ (누적 기록이 있는 기기) | **debug 유지를 권한다** | `run-as` 백업 경로를 살려둔다 |
| 다른 사람 / 다른 기기 | release | 축소·서명된 정식 빌드 |

기기도 release로 옮기겠다면 **옮기기 전에** 백업한다. 그 뒤로는 꺼낼 수 없다.

```bash
ADB="/c/Users/DKSYSTEMS/AppData/Local/Android/Sdk/platform-tools/adb.exe"
STAMP=$(date +%Y%m%d_%H%M%S)
mkdir -p "backup/$STAMP"
MSYS_NO_PATHCONV=1 "$ADB" exec-out run-as com.dksystems.tracephone \
  cat databases/tracephone.db > "backup/$STAMP/tracephone.db"
```

### (3) 저장소를 공개로 두면 따라오는 것

링크만 알면 누구나 Release 파일을 받을 수 있게 하려면 저장소가 공개여야 한다.
비공개 저장소의 Release 파일은 로그인 없이 받을 수 없다. 공개로 두면 이렇게 된다.

* **소스 전부와 커밋 이력이 공개된다.** 한 번 올린 것은 지워도 복제본이 남을 수 있다
* **Actions 로그가 공개된다.** 시크릿은 `***`로 가려지지만 그 밖의 출력은 그대로 보인다
* Actions 실행 시간은 공개 저장소에서 **무료**다
* `backup/`(실기기 DB 4.8 MB)은 **절대 올라가면 안 된다.** 2단계에서 직접 확인한다
* **키스토어 파일이 새면 누구나 이 앱의 '업데이트'를 만들 수 있다.** 시크릿에만 두고,
  저장소에는 어떤 형태로도 넣지 않는다

---

## 1단계 — GitHub 계정과 저장소

### 1-1. 계정 만들기

1. <https://github.com/signup> 접속
2. 이메일 → 비밀번호 → 사용자 이름(영문, 주소에 들어간다)
3. 메일로 온 8자리 코드 입력
4. 요금제는 **Free**

### 1-2. 저장소 만들기

오른쪽 위 `+` → **New repository**

| 칸 | 값 |
|---|---|
| Repository name | `TracePhone` |
| 공개 범위 | **Public** |
| Add a README file | **체크하지 않는다** (이미 파일이 있다) |
| Add .gitignore | **None** (이미 있다) |
| Choose a license | None |

**Create repository**를 누르고, 다음 화면의 주소를 복사한다.
`https://github.com/<내-사용자이름>/TracePhone.git`

---

## 2단계 — Firebase 프로젝트와 버전 기록

Firebase는 **"지금 최신이 무엇인가"라는 한 줄만** 맡는다. 앱에 Firebase SDK를 넣지 않고,
`google-services.json`도 쓰지 않는다. Realtime Database는 경로 뒤에 `.json`을 붙이면
평범한 HTTPS GET으로 읽히기 때문이다.

### 2-1. 프로젝트 만들기

1. <https://console.firebase.google.com> 접속해 Google 계정으로 로그인
2. **프로젝트 만들기**
3. 이름에 `TracePhone` (뒤에 `-a1b2c` 같은 식별자가 붙는다. 정상이다)
4. **Google 애널리틱스는 끈다.** 이 앱은 사용 통계를 외부로 보내지 않는다
5. **프로젝트 만들기** → **계속**

> **안드로이드 앱을 등록하지 않아도 된다.** 앱 등록은 SDK를 쓸 때 필요한 절차다.
> 우리는 데이터베이스만 읽으므로 건너뛴다.

### 2-2. Realtime Database 만들기

1. 왼쪽 메뉴 **빌드** → **Realtime Database** → **데이터베이스 만들기**
2. 위치는 가까운 곳을 고른다 (예: `싱가포르 (asia-southeast1)`)
3. 보안 규칙은 **잠금 모드에서 시작**을 고른다
   (테스트 모드는 **누구나 쓰기 가능**이라 버전 기록을 바꿔치기할 수 있다)
4. **사용 설정**

### 2-3. 규칙 — `latest`만 읽기 허용

**규칙** 탭을 눌러 내용을 전부 지우고 아래로 바꾼 뒤 **게시**한다.

```json
{
  "rules": {
    ".read": false,
    ".write": false,
    "latest": {
      ".read": true,
      ".write": false
    }
  }
}
```

이 규칙의 뜻은 이렇다.

* `latest`는 **누구나 읽을 수 있다** — 앱이 로그인 없이 읽어야 하므로 필요하다
* **어디에도 쓸 수 없다** — 값은 콘솔에서만 바꾼다. 앱도, 남도 쓰지 못한다
* 그 밖의 경로는 읽지도 못한다 — 나중에 다른 데이터를 두더라도 실수로 공개되지 않는다

### 2-4. `latest` 값 넣기

**데이터** 탭에서 루트의 `+`를 눌러 아래 구조를 만든다.

```
latest
 ├─ versionCode   2
 ├─ versionName   0.2.0
 └─ downloadUrl   https://github.com/<내-사용자이름>/TracePhone/releases/download/v0.2.0/TracePhone-0.2.0.apk
```

처음에는 지금 버전(`0.2.0`)을 그대로 적어 둔다. 첫 배포가 끝나면 그 주소가 실제로 생긴다.

> `downloadUrl`은 **반드시 `https://github.com/` 으로 시작해야 한다.**
> 앱이 그 밖의 주소는 열지 않는다. 버전 기록은 공개 읽기이므로, 쓰기 권한이 새는 사고가
> 나더라도 악성 주소로 유도되지 않게 막아 둔 것이다.

### 2-5. 주소를 프로젝트에 적어 넣기

**데이터** 탭 위쪽에 데이터베이스 주소가 보인다. 이런 모양이다.

```
https://tracephone-a1b2c-default-rtdb.asia-southeast1.firebasedatabase.app
```

뒤에 `/latest.json`을 붙여 `gradle.properties`의 마지막 줄에 넣는다.

```properties
tracephone.updateManifestUrl=https://tracephone-a1b2c-default-rtdb.asia-southeast1.firebasedatabase.app/latest.json
```

**이 주소는 공개 저장소에 올라간다.** 공개 읽기로 둔 값이라 문제가 되지 않는다.
비워 두면 앱이 업데이트 확인 카드를 **아예 내보이지 않는다.**

브라우저에서 그 주소를 직접 열어 값이 보이는지 확인한다. 이렇게 나와야 한다.

```json
{"downloadUrl":"https://github.com/...","versionCode":2,"versionName":"0.2.0"}
```

---

## 3단계 — 올릴 수 없는 것부터 확인하고 첫 푸시

저장소가 **공개**이므로 이 확인을 건너뛰면 안 된다.

* `backup/` — 실기기에서 뽑은 **실제 사용 기록 DB 4.8 MB**. 개인 데이터다
* `keystore.properties` — 서명 키의 위치와 비밀번호

```bash
cd /d/project/TracePhone
git init -b main
git check-ignore -v backup/ keystore.properties local.properties
```

세 줄 모두 `.gitignore:...`로 시작하는 결과가 나와야 한다. 하나라도 빠지면 멈춘다.

```bash
git add .

# 올라갈 목록에서 위험한 것을 한 번 더 걸러 본다. 아무것도 출력되지 않아야 정상이다.
git status --short | grep -E "backup/|keystore\.properties|local\.properties|\.keystore"

git commit -m "TracePhone 초기 커밋"

git remote add origin https://github.com/<내-사용자이름>/TracePhone.git
git push -u origin main
```

`git push`에서 로그인 창이 뜬다. Git Credential Manager가 브라우저를 열어 주므로
GitHub에 로그인하고 **Authorize**를 누른다. 한 번만 하면 된다.

> 브라우저가 열리지 않으면: GitHub → 프로필 → Settings → Developer settings →
> Personal access tokens → Tokens (classic) → Generate new token →
> `repo`만 체크 → 만들어진 문자열을 비밀번호 칸에 붙여 넣는다.

푸시하면 **Actions** 탭에서 `빌드와 테스트`가 자동으로 돈다. 초록색이 되는지 먼저 본다.
여기서 실패하면 배포는 시도하지 않는다.

---

## 4단계 — 시크릿 4개와 변수 1개

GitHub 저장소 → **Settings** → 왼쪽 **Secrets and variables** → **Actions**.

### 4-1. Secrets 탭 — **New repository secret**으로 4개

| 이름 | 값 |
|---|---|
| `RELEASE_KEYSTORE_BASE64` | 키스토어를 base64로 바꾼 문자열 (아래) |
| `RELEASE_STORE_PASSWORD` | `android` |
| `RELEASE_KEY_ALIAS` | `androiddebugkey` |
| `RELEASE_KEY_PASSWORD` | `android` |

base64는 이렇게 만든다. 줄바꿈이 섞이면 복원이 깨지므로 `-w0`이 반드시 필요하다.

```bash
base64 -w0 "C:/Users/DKSYSTEMS/.android/debug.keystore" > keystore.base64.txt

# 되돌려 원본과 같은지 확인한다. "같음"이 나와야 한다.
base64 -d keystore.base64.txt | cmp - "C:/Users/DKSYSTEMS/.android/debug.keystore" \
  && echo 같음
```

내용을 전부 복사해 시크릿에 붙여 넣은 뒤 **파일을 지운다.**

```bash
rm keystore.base64.txt
```

> 이 파일은 **서명 키 그 자체**다. `.gitignore`에 `keystore.base64.txt`와 `*.base64`를
> 넣어 두었으므로 잊고 남겨 두어도 저장소로 새지 않지만, 지우는 것이 원칙이다.
> 공개 저장소에 이 내용이 올라가면 **누구나 이 앱의 '업데이트'를 만들 수 있다.**

### 4-2. Variables 탭 — **New repository variable**로 1개

| 이름 | 값 |
|---|---|
| `EXPECTED_SIGNER_SHA1` | `0a49f14ece7d3dbe70e9ebb5e9797f753e81467e` |

이것은 **안전장치**다. 빌드한 APK의 서명 지문을 이 값과 비교해, 다르면 배포를 멈춘다.
서명이 바뀐 APK가 기기로 흘러가 누적 기록을 지우는 사고를 막는다.

시크릿이 아니라 **변수**에 두는 이유: 지문은 비밀이 아니고, 시크릿에 넣으면 로그가
`***`로 가려져 어긋났을 때 무엇과 달랐는지 읽을 수 없다. 변수가 없으면 검사를 건너뛰며
경고만 남긴다.

---

## 5단계 — 첫 배포

배포는 **`v`로 시작하는 태그**를 밀 때만 돈다. 현재 버전은 `0.2.0`이다.

```bash
git add gradle.properties
git commit -m "업데이트 확인 주소 설정"
git push

git tag v0.2.0
git push origin v0.2.0
```

Actions 탭에 `배포`가 나타난다. 통과하면

* **Releases**에 `0.2.0`이 생기고 `TracePhone-0.2.0.apk`가 붙는다
* 실행 요약(Summary)에 **Firebase에 붙여 넣을 JSON**이 출력된다

### 5-1. Firebase의 `latest` 갱신

워크플로가 만들어 준 JSON을 Firebase 콘솔 **데이터** 탭의 `latest` 아래 세 값에 옮긴다.

```json
{
  "versionCode": 2,
  "versionName": "0.2.0",
  "downloadUrl": "https://github.com/.../releases/download/v0.2.0/TracePhone-0.2.0.apk"
}
```

**이 한 걸음은 자동화되어 있지 않다.** Firebase에 쓰려면 서비스 계정이 필요하고,
그것이 지금 피하려는 Google Cloud 절차다. 그래서 CI가 **붙여 넣을 내용을 대신 만들어 주고**,
옮기는 것만 사람이 한다. 같은 JSON은 Release 설명에도 들어 있다.

> 이 단계를 빠뜨리면 앱은 계속 "최신입니다"라고 말한다. 파일은 올라갔지만
> 최신이 무엇인지 알려 주는 쪽이 옛 값을 쥐고 있기 때문이다.

---

## 6단계 — 받고 확인하기

### 다른 사람 / 다른 기기

Release 페이지 주소를 알려 주면 된다. 로그인 없이 `.apk`를 내려받을 수 있다.

```
https://github.com/<내-사용자이름>/TracePhone/releases/latest
```

설치 뒤 **설정 → 애플리케이션 → 특별한 접근 권한 → 사용 정보 접근**을 켜야 동작한다.
이 안내는 Release 설명에도 자동으로 들어간다.

### 앱 안에서 새 버전 확인

앱 → **설정** 화면을 아래로 내리면 **앱 버전** 카드가 있다.

* 화면에 들어오면 한 번 자동으로 확인한다
* **다시 확인** 버튼으로 다시 물을 수 있다
* 새 버전이 있으면 **내려받기** 버튼이 함께 나타난다
* `gradle.properties`의 주소가 비어 있으면 **카드가 아예 보이지 않는다**

통신은 이 카드가 보이는 동안에만 일어난다. 배경에서 주기적으로 확인하지 않는다.
보내는 것은 요청 주소뿐이고, 기기나 사용량 정보는 실리지 않는다.

### 내 S23+ (누적 기록이 있는 기기)

0단계대로 **debug 유지를 권한다.** release를 꼭 넣겠다면 먼저 백업하고 아래만 쓴다.

```bash
ADB="/c/Users/DKSYSTEMS/AppData/Local/Android/Sdk/platform-tools/adb.exe"

# -r 은 기존 앱 위에 덮어쓴다는 뜻이다. 데이터가 보존된다.
"$ADB" install -r app/build/outputs/apk/release/app-release.apk
```

`uninstall`은 **절대 쓰지 않는다.**
설치가 `INSTALL_FAILED_UPDATE_INCOMPATIBLE`로 실패하면 **서명이 다른 것이다.**
그대로 멈추고 `keystore.properties`를 확인한다. 앱을 지워서 해결하려 하면 기록을 잃는다.

---

## 7단계 — 다음 배포부터

워크플로가 두 가지를 검사해 어긋나면 멈춘다.

* **태그와 `versionName`이 같은지** — 같은 버전이 여러 번 올라가 어느 것이 최신인지
  알 수 없게 되는 것을 막는다
* **`versionCode`가 직전 태그보다 커졌는지** — 이름만 올리고 `versionCode`를 그대로 두면
  기기가 새 APK를 업데이트로 받아들이지 않는다. 설치는 되지만 아무 일도 일어나지 않아
  원인을 찾기 어렵다

1. `app/build.gradle.kts`에서 두 값을 올린다

   ```kotlin
   versionCode = 3        // 기기가 '새 버전'으로 인식하는 숫자. 반드시 올린다
   versionName = "0.3.0"  // 사람이 읽는 이름. 태그와 같아야 한다
   ```

2. 커밋하고 태그를 민다

   ```bash
   git add app/build.gradle.kts
   git commit -m "0.3.0"
   git push
   git tag v0.3.0
   git push origin v0.3.0
   ```

3. Actions 요약에 나온 JSON을 Firebase `latest`에 옮긴다

태그를 잘못 밀었으면 지운다.

```bash
git tag -d v0.3.0
git push origin :refs/tags/v0.3.0
```

---

## 8단계 — 문제가 생길 때

| 증상 | 원인과 처방 |
|---|---|
| `Permission denied` / `./gradlew: not found` | 워크플로의 `chmod +x ./gradlew` 단계가 빠졌다 |
| `Failed to install the following Android SDK packages` | `platforms;android-37` 이름이 바뀌었다. 로그의 이름으로 워크플로를 고친다 |
| `SigningConfig "release" is missing required property` | 시크릿 이름의 철자가 틀렸다. 대문자와 밑줄까지 똑같아야 한다 |
| `base64: invalid input` | base64를 만들 때 `-w0`을 빼서 줄바꿈이 섞였다 |
| `서명이 기대값과 다릅니다` | 의도한 안전장치가 작동했다. 키스토어 시크릿을 다시 만든다 |
| `태그 v… 과 versionName … 이 다릅니다` | `versionName`을 올리지 않고 태그를 만들었다 |
| `versionCode가 … 에서 올라가지 않았습니다` | `versionCode`를 올리지 않았다. 올린 뒤 태그를 다시 만든다 |
| `gh: Resource not accessible by integration` | 워크플로의 `permissions: contents: write`가 빠졌다 |
| Release는 생겼는데 앱이 새 버전을 모른다 | 5-1단계(Firebase `latest` 갱신)를 빠뜨렸다 |
| 앱에 **앱 버전** 카드가 안 보인다 | `gradle.properties`의 주소가 비었거나 `https`가 아니다. 그 상태로 빌드된 APK다 |
| `버전 기록의 형식이 올바르지 않습니다` | Firebase의 `latest`에 세 항목이 다 있는지, `versionCode`가 숫자인지 본다 |
| `내려받기 주소를 신뢰할 수 없어 열지 않았습니다` | `downloadUrl`이 `https://github.com/` 으로 시작하지 않는다 |
| `확인하지 못했습니다` | 규칙에서 `latest`의 `.read`가 `true`인지, 브라우저로 그 주소가 열리는지 본다 |

---

## 바꾼 파일

| 파일 | 변경 |
|---|---|
| `app/build.gradle.kts` | release 서명 설정, `buildConfig = true`, `UPDATE_MANIFEST_URL`, 테스트용 `org.json` |
| `app/proguard-rules.pro` | 열거형 상수 이름 고정 (DB 열과 DataStore에 이름이 그대로 저장된다) |
| `app/src/main/AndroidManifest.xml` | `INTERNET` 권한 — 받는 쪽으로만 쓴다 |
| `gradle.properties` | `tracephone.updateManifestUrl` (비어 있으면 기능이 꺼진다) |
| `gradle/libs.versions.toml` | `org.json` 테스트 의존성 |
| `.gitignore` | `backup/`, `keystore.properties`, `.kotlin/`, 다이어그램 캡처 제외 |
| `keystore.properties` | **새 파일, git 추적 제외.** 로컬 release 서명 값 |
| `domain/model/AppRelease.kt` | **새 파일.** 버전 기록과 확인 결과 다섯 갈래 |
| `domain/update/LatestReleaseSource.kt` | **새 파일.** 기록을 가져오는 인터페이스 |
| `domain/update/InstalledAppVersion.kt` | **새 파일.** 설치된 버전(테스트에서 바꿔 끼우기 위한 인터페이스) |
| `domain/usecase/CheckForUpdateUseCase.kt` | **새 파일.** 버전 비교와 주소 신뢰 검사 |
| `data/update/UpdateManifest.kt` | **새 파일.** JSON 파싱(틀린 입력을 조용히 메우지 않는다) |
| `data/update/HttpLatestReleaseSource.kt` | **새 파일.** HTTPS GET 한 번. 이 앱의 유일한 네트워크 코드 |
| `data/update/BuildConfigAppVersion.kt` | **새 파일.** |
| `di/RepositoryModule.kt` | 위 둘 바인딩 |
| `ui/settings/AppUpdateViewModel.kt` | **새 파일.** `SettingsViewModel`에 얹지 않았다(이미 주입 10개) |
| `ui/settings/AppUpdateCard.kt` | **새 파일.** 주소가 없으면 카드를 내보이지 않는다 |
| `ui/settings/SettingsScreen.kt` | 카드 연결 |
| `res/values/strings.xml` | 문구 12개 |
| `.github/workflows/build.yml` | **새 파일.** 푸시마다 단위 테스트 + lint |
| `.github/workflows/distribute.yml` | **새 파일.** 태그 → 빌드·서명확인 → GitHub Release + 붙여 넣을 JSON 생성 |

새 런타임 의존성 0. Firebase SDK 0. 마이그레이션 0. DataStore 추가 0.

## 확인한 것 / 확인하지 못한 것

확인한 것 (2026-10-20, 로컬)

* `:app:assembleRelease` 성공. R8 축소가 새 코드를 포함해 빌드를 깨지 않았다 (APK **2.5 MB** 그대로 — `org.json`은 테스트 클래스패스에만 들어간다)
* release APK의 서명 지문이 기기의 debug 키와 **같다** (`0a49f1…467e`)
* `UPDATE_MANIFEST_URL`이 주소를 줄 때와 비울 때 각각 올바르게 박힌다
* 단위 테스트 **291건 전부 통과** (기존 267 + 신규 24)
* **뮤테이션 확인**: 주소 신뢰 검사의 뒷조건을 `true`로 되돌리면 테스트가 실패한다
  (`github.com.evil.example`이 통과하는 구멍) → 그 검사가 실제로 고정되어 있다
* release 매니페스트에 `debuggable`이 **없다** → `run-as` 백업 불가
* `git check-ignore`로 `backup/` 등 제외 확인 (임시 저장소를 써서 프로젝트를 건드리지 않았다)
* 두 워크플로의 YAML 문법, 그리고 **`run` 스크립트 15개 전부를 `bash -n`으로 문법 검사**
  (`${{ }}`는 자리표시자로 바꿔 넣었다). 줄 연결 백슬래시가 깨진 두 줄을 이 검사로 찾아 고쳤다

확인하지 **못한** 것

* **GitHub Actions에서 실제로 도는지.** 저장소가 아직 없어 한 번도 실행되지 않았다.
  러너에 `android-37` 플랫폼이 있는지, JDK 21로 AGP 9.4가 도는지는 첫 실행이 답한다
* **Firebase Realtime Database에서 실제로 읽히는지.** 데이터베이스가 아직 없다.
  파서와 비교 로직은 단위 테스트로 고정했지만, 실제 응답 형태는 2-5단계에서
  브라우저로 직접 확인해야 한다
* **release 빌드가 기기에서 동작하는지.** R8은 빌드가 아니라 런타임에 깨진다.
  Hilt 주입·Room 조회·주기 동기화·Glance 위젯을 눈으로 봐야 한다
* Firebase 콘솔 화면의 메뉴 이름. Google이 수시로 바꾼다

## 하지 않은 것

| 후보 | 이유 |
|---|---|
| 배경에서 주기적으로 버전 확인 + 알림 | 통신이 사용자가 보지 않는 때에 일어난다. 사용 기록 앱에서 그 성질을 바꾸는 선택이라 지금은 설정 화면 안으로만 제한했다 |
| Firebase `latest` 자동 갱신 | 쓰기에는 서비스 계정이 필요하고, 그것이 피하려는 Google Cloud 절차다. 대신 CI가 붙여 넣을 JSON을 만들어 준다 |
| Firebase App Distribution | 업로드에 서비스 계정 JSON이 필요하다 |
| Play Store | 지금 서명 키로는 올릴 수 없다. 키를 바꾸려면 DB 내보내기 기능이 먼저 필요하다 |
| 앱 안에서 APK 자동 설치 | `REQUEST_INSTALL_PACKAGES` 권한이 필요하고 심사·보안 부담이 크다. 브라우저로 넘긴다 |
