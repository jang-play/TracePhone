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
  앱 설정 화면이 HTTPS GET 한 번으로 읽고 "새 버전 …이 나왔습니다" 표시
```

역할이 이렇게 갈린다.

| | 맡는 일 | 맡지 않는 일 |
|---|---|---|
| **GitHub** | 소스, 빌드, 서명, **APK 파일 보관과 전달** | 버전 안내 |
| **Firebase** | **"지금 최신이 무엇인가"라는 한 줄의 기록** | 파일 보관, 빌드, 배포 |

---

## 매번 하는 일 — 한눈에 보기

코드를 고친 뒤 배포까지 여섯 걸음이다. 자세한 설명은 **1부**에 있다.

| | 하는 일 | 어디서 |
|---|---|---|
| 1 | `app/build.gradle.kts`의 `versionCode`·`versionName` **둘 다** 올린다 | 편집기 |
| 2 | 커밋하고 **커밋에 버전이 들어갔는지 확인한다** | 터미널 |
| 3 | 푸시한다 | 터미널 |
| 4 | `v<버전>` 태그를 만들어 민다 | 터미널 |
| 5 | Actions의 `배포`가 초록색이 되는지 본다 | 브라우저 |
| 6 | Firebase의 `latest` 세 값을 새 버전으로 바꾼다 | 브라우저 |

2번을 건너뛰면 4번에서 막힌다. 가장 자주 나는 실수다 — **태그는 작업 트리가 아니라
커밋을 가리킨다.**

6번을 건너뛰면 배포는 됐는데 앱이 계속 "최신입니다"라고 말한다.

### 복사해서 쓰는 명령 한 벌

`0.5.0`과 `5`를 올릴 버전으로 바꿔 쓴다.

```bash
cd /d/project/TracePhone

# --- 1. 버전을 올린다 (편집기에서 두 줄을 고친다) ---
#   versionCode = 5
#   versionName = "0.5.0"

# --- 2. 커밋하고 확인한다 ---
git add -A
git status --short | grep -E "backup/|keystore|\.base64|local\.properties"   # 아무것도 안 나와야 한다
git commit -m "0.5.0"

# 두 줄의 값이 같아야 한다. 다르면 커밋이 안 된 것이다.
grep -oP 'versionName = "\K[^"]+' app/build.gradle.kts
git show HEAD:app/build.gradle.kts | grep -oP 'versionName = "\K[^"]+'

# --- 3~4. 푸시하고 태그를 민다 ---
git push
git tag v0.5.0
git push origin v0.5.0
```

그리고 **5번(Actions 확인)**과 **6번(Firebase 갱신)**은 브라우저에서 한다.

---

## 반드시 알고 있어야 할 것

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
* `backup/`(실기기 DB 4.8 MB)은 **절대 올라가면 안 된다.** 1-2와 설정 C에서 직접 확인한다
* **키스토어 파일이 새면 누구나 이 앱의 '업데이트'를 만들 수 있다.** 시크릿에만 두고,
  저장소에는 어떤 형태로도 넣지 않는다

---

## 1부. 수정한 뒤 배포하기

### 1-1. 버전을 올린다

`app/build.gradle.kts`에서 **두 줄을 함께** 올린다.

```kotlin
versionCode = 5        // 기기가 '새 버전'으로 인식하는 숫자. 반드시 올린다
versionName = "0.5.0"  // 사람이 읽는 이름. 태그와 같아야 한다
```

워크플로가 둘을 모두 검사한다.

| 검사 | 어긋나면 |
|---|---|
| 태그 이름 == `versionName` | `태그 v… 이 가리키는 커밋의 versionName 은 …` 으로 멈춘다 |
| `versionCode` > 직전 태그의 `versionCode` | `versionCode가 … 에서 올라가지 않았습니다` 로 멈춘다 |

`versionCode`를 올리지 않으면 기기가 새 APK를 업데이트로 받아들이지 않는다.
설치는 되지만 아무 일도 일어나지 않아 원인을 찾기 어렵다. 그래서 CI가 막는다.

### 1-2. 커밋하고 — 커밋된 값을 확인한다

```bash
git add -A

# 올라갈 목록에서 위험한 것을 걸러 본다. 아무것도 출력되지 않아야 정상이다.
git status --short | grep -E "backup/|keystore|\.base64|local\.properties"

git commit -m "0.5.0"
```

**여기서 멈추고 확인한다.** 가장 자주 나는 실수가 이 자리에서 생긴다.

```bash
# 두 값이 같아야 한다.
grep -oP 'versionName = "\K[^"]+' app/build.gradle.kts            # 작업 트리
git show HEAD:app/build.gradle.kts | grep -oP 'versionName = "\K[^"]+'   # 커밋된 것
```

> **태그는 작업 트리가 아니라 커밋을 가리킨다.** 파일을 고쳐 놓고 커밋하지 않은 채
> 태그를 만들면 태그에는 **옛 버전**이 담긴다. 실제로 `v0.2.1`과 `v0.3.0`이 그렇게
> 만들어져 배포가 두 번 막혔다. 고치려면 태그를 지우고 다시 만들어야 해서 번거롭다
> (3부의 '잘못 만든 태그 고치기').

### 1-3. 푸시한다

```bash
git push
```

푸시만 해도 **`빌드와 테스트`** 워크플로가 돈다. 단위 테스트와 lint만 보므로 비밀값이
필요 없다. 여기서 실패하면 배포는 시도하지 않는다.

### 1-4. 태그를 밀어 배포한다

배포는 **`v`로 시작하는 태그를 밀 때만** 돈다.

```bash
git tag v0.5.0
git push origin v0.5.0
```

워크플로가 하는 일은 순서대로 이렇다.

```
단위 테스트 → 버전·태그 검사 → 키스토어 복원 → release APK 빌드
          → 서명 지문 확인 → GitHub Release 생성(APK 첨부) → Firebase용 JSON 출력
```

### 1-5. Actions를 확인한다

저장소 → **Actions** 탭 → `배포` 실행.

통과하면 두 가지가 생긴다.

* **Releases**에 `0.5.0`과 `TracePhone-0.5.0.apk`
* 실행 요약(**Summary**)에 **Firebase에 붙여 넣을 JSON**

같은 JSON이 Release 설명에도, Release 파일 목록의 `latest.json`에도 들어 있다.
셋 중 어디서 복사해도 같다.

함께 볼 것이 하나 더 있다. **`서명 지문 확인`** 단계를 펼쳐 둘 중 어느 쪽인지 본다.

```
서명 지문: 0a49f14ece7d3dbe70e9ebb5e9797f753e81467e     ← 비교까지 된 경우
::warning::변수 EXPECTED_SIGNER_SHA1 이 없어 서명 검사를 건너뜁니다.   ← 건너뛴 경우
```

경고가 떴다면 **2부 설정 D**의 변수를 등록한다. 이 검사가 서명이 바뀐 APK가 기기로 흘러가
누적 기록을 지우는 사고를 막는 장치다.

### 1-6. Firebase의 `latest`를 갱신한다

**이 한 걸음은 자동화되어 있지 않다.** Firebase에 쓰려면 서비스 계정이 필요하고, 그것이
이 구성에서 피하려는 Google Cloud 절차다. 그래서 CI가 **붙여 넣을 내용을 대신 만들어 주고**,
옮기는 것만 사람이 한다.

Firebase 콘솔 → **Realtime Database** → **데이터** 탭 → `latest`의 세 값을 바꾼다.

```json
{
  "versionCode": 5,
  "versionName": "0.5.0",
  "downloadUrl": "https://github.com/jang-play/TracePhone/releases/download/v0.5.0/TracePhone-0.5.0.apk"
}
```

| 항목 | 규칙 |
|---|---|
| `versionCode` | 정수. 이 값이 **설치된 것보다 클 때만** 앱이 새 버전을 알린다 |
| `versionName` | 빈 문자열이면 앱이 형식 오류로 본다 |
| `downloadUrl` | **`https://github.com/` 으로 시작해야 한다.** 앱이 그 밖의 주소는 열지 않는다 |

바꾼 뒤 브라우저로 주소를 직접 열어 확인한다.

```
https://tracephone-4ca52-default-rtdb.asia-southeast1.firebasedatabase.app/latest.json
```

> 이 단계를 빠뜨리면 **배포는 됐는데 앱은 계속 "최신입니다"라고 말한다.** 파일은
> 올라갔지만 '무엇이 최신인가'를 알려 주는 쪽이 옛 값을 쥐고 있기 때문이다.

### 1-7. 받아서 확인한다

#### 다른 사람 / 다른 기기

Release 페이지 주소를 알려 주면 된다. 로그인 없이 `.apk`를 내려받을 수 있다.

```
https://github.com/<내-사용자이름>/TracePhone/releases/latest
```

설치 뒤 **설정 → 애플리케이션 → 특별한 접근 권한 → 사용 정보 접근**을 켜야 동작한다.
이 안내는 Release 설명에도 자동으로 들어간다.

#### 앱 안에서 새 버전 확인

앱 → **설정** 화면을 아래로 내리면 **앱 버전** 카드가 있다.

* 화면에 들어오면 한 번 자동으로 확인한다
* **다시 확인** 버튼으로 다시 물을 수 있다
* 새 버전이 있으면 **내려받기** 버튼이 함께 나타난다
* `gradle.properties`의 주소가 비어 있으면 **카드가 아예 보이지 않는다**

통신은 이 카드가 보이는 동안에만 일어난다. 배경에서 주기적으로 확인하지 않는다.
보내는 것은 요청 주소뿐이고, 기기나 사용량 정보는 실리지 않는다.

#### 내 S23+ (누적 기록이 있는 기기)

'반드시 알고 있어야 할 것'의 (2)대로 **debug 유지를 권한다.** release를 꼭 넣겠다면 먼저 백업하고 아래만 쓴다.

```bash
ADB="/c/Users/DKSYSTEMS/AppData/Local/Android/Sdk/platform-tools/adb.exe"

# -r 은 기존 앱 위에 덮어쓴다는 뜻이다. 데이터가 보존된다.
"$ADB" install -r app/build/outputs/apk/release/app-release.apk
```

`uninstall`은 **절대 쓰지 않는다.**
설치가 `INSTALL_FAILED_UPDATE_INCOMPATIBLE`로 실패하면 **서명이 다른 것이다.**
그대로 멈추고 `keystore.properties`를 확인한다. 앱을 지워서 해결하려 하면 기록을 잃는다.

---

## 2부. 처음 한 번만 하는 설정

아래는 **이미 끝난 일**이다. 새 PC에서 다시 시작하거나 설정이 깨졌을 때만 본다.

### 설정 A — GitHub 계정과 저장소

#### A-1. 계정 만들기

1. <https://github.com/signup> 접속
2. 이메일 → 비밀번호 → 사용자 이름(영문, 주소에 들어간다)
3. 메일로 온 8자리 코드 입력
4. 요금제는 **Free**

#### A-2. 저장소 만들기

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

### 설정 B — Firebase 프로젝트와 버전 기록

Firebase는 **"지금 최신이 무엇인가"라는 한 줄만** 맡는다. 앱에 Firebase SDK를 넣지 않고,
`google-services.json`도 쓰지 않는다. Realtime Database는 경로 뒤에 `.json`을 붙이면
평범한 HTTPS GET으로 읽히기 때문이다.

#### B-1. 프로젝트 만들기

1. <https://console.firebase.google.com> 접속해 Google 계정으로 로그인
2. **프로젝트 만들기**
3. 이름에 `TracePhone` (뒤에 `-a1b2c` 같은 식별자가 붙는다. 정상이다)
4. **Google 애널리틱스는 끈다.** 이 앱은 사용 통계를 외부로 보내지 않는다
5. **프로젝트 만들기** → **계속**

> **안드로이드 앱을 등록하지 않아도 된다.** 앱 등록은 SDK를 쓸 때 필요한 절차다.
> 우리는 데이터베이스만 읽으므로 건너뛴다.

#### B-2. Realtime Database 만들기

1. 왼쪽 메뉴 **빌드** → **Realtime Database** → **데이터베이스 만들기**
2. 위치는 가까운 곳을 고른다 (예: `싱가포르 (asia-southeast1)`)
3. 보안 규칙은 **잠금 모드에서 시작**을 고른다
   (테스트 모드는 **누구나 쓰기 가능**이라 버전 기록을 바꿔치기할 수 있다)
4. **사용 설정**

#### B-3. 규칙 — `latest`만 읽기 허용

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

#### B-4. `latest` 값 넣기

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

#### B-5. 주소를 프로젝트에 적어 넣기

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

### 설정 C — 올릴 수 없는 것 확인과 첫 푸시

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

### 설정 D — 시크릿 4개와 변수 1개

GitHub 저장소 → **Settings** → 왼쪽 **Secrets and variables** → **Actions**.

#### D-1. Secrets 탭 — **New repository secret**으로 4개

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

#### D-2. Variables 탭 — **New repository variable**로 1개

| 이름 | 값 |
|---|---|
| `EXPECTED_SIGNER_SHA1` | `0a49f14ece7d3dbe70e9ebb5e9797f753e81467e` |

이것은 **안전장치**다. 빌드한 APK의 서명 지문을 이 값과 비교해, 다르면 배포를 멈춘다.
서명이 바뀐 APK가 기기로 흘러가 누적 기록을 지우는 사고를 막는다.

시크릿이 아니라 **변수**에 두는 이유: 지문은 비밀이 아니고, 시크릿에 넣으면 로그가
`***`로 가려져 어긋났을 때 무엇과 달랐는지 읽을 수 없다. 변수가 없으면 검사를 건너뛰며
경고만 남긴다.

---

## 3부. 문제가 생길 때

| 증상 | 원인과 처방 |
|---|---|
| `Permission denied` / `./gradlew: not found` | 워크플로의 `chmod +x ./gradlew` 단계가 빠졌다 |
| `Failed to install the following Android SDK packages` | `platforms;android-37` 이름이 바뀌었다. 로그의 이름으로 워크플로를 고친다 |
| `SigningConfig "release" is missing required property` | 시크릿 이름의 철자가 틀렸다. 대문자와 밑줄까지 똑같아야 한다 |
| `base64: invalid input` | base64를 만들 때 `-w0`을 빼서 줄바꿈이 섞였다 |
| `서명이 기대값과 다릅니다` | 의도한 안전장치가 작동했다. 키스토어 시크릿을 다시 만든다 |
| `태그 v… 이 가리키는 커밋의 versionName 은 …` | **버전 변경을 커밋하지 않고 태그를 만들었다.** 3부의 '잘못 만든 태그 고치기' 참고 |
| `versionCode가 … 에서 올라가지 않았습니다` | `versionCode`를 올리지 않았다. 올린 뒤 태그를 다시 만든다 |
| `gh: Resource not accessible by integration` | 워크플로의 `permissions: contents: write`가 빠졌다 |
| Release는 생겼는데 앱이 새 버전을 모른다 | 1-6(Firebase `latest` 갱신)을 빠뜨렸다 |
| 앱에 **앱 버전** 카드가 안 보인다 | `gradle.properties`의 주소가 비었거나 `https`가 아니다. 그 상태로 빌드된 APK다 |
| `버전 기록의 형식이 올바르지 않습니다` | Firebase의 `latest`에 세 항목이 다 있는지, `versionCode`가 숫자인지 본다 |
| `내려받기 주소를 신뢰할 수 없어 열지 않았습니다` | `downloadUrl`이 `https://github.com/` 으로 시작하지 않는다 |
| **`내려받기`를 눌렀더니 404** | **저장소가 비공개다.** GitHub은 비공개 저장소의 파일에 로그인 없는 요청에 로그인 화면이 아니라 404를 돌려준다. 폰 브라우저에는 GitHub 세션이 없다. 바로 아래 '내려받기가 404일 때' 참고 |
| `확인하지 못했습니다` | 규칙에서 `latest`의 `.read`가 `true`인지, 브라우저로 그 주소가 열리는지 본다 |

### 내려받기가 404일 때

> **이미 해결된 문제다.** 저장소가 비공개였던 동안 폰 브라우저에 GitHub 세션이 없어 404가
> 났다. 2026-10-20에 저장소를 공개로 바꿔 해결됐다(이력 감사에서 키스토어·`backup/`·토큰이
> 한 번도 커밋되지 않은 것을 확인한 뒤 바꿨다). 아래는 다시 같은 증상이 날 때를 위한 기록이다.

먼저 **어느 쪽 문제인지** 가른다. PC 브라우저(GitHub에 로그인된 곳)에서 그 주소를 연다.

| PC에서 | 뜻 | 할 일 |
|---|---|---|
| 내려받아진다 | 파일은 있다. **비공개 저장소라서** 로그인 없는 폰이 못 받는 것이다 | 아래 셋 중 하나 |
| 거기서도 404 | Release나 파일 이름이 다르다 | Actions의 `배포` 실행이 성공했는지, Release에 `TracePhone-<버전>.apk`가 붙었는지 본다 |

비공개가 원인일 때의 선택지.

| 방법 | 결과 | 대가 |
|---|---|---|
| **저장소를 공개로 바꾼다** (권장) | 링크만 알면 누구나 받는다 | 소스와 커밋 이력이 공개된다. **되돌리기 어렵다** |
| 폰 브라우저에서 GitHub에 로그인한다 | 내 폰에서는 받아진다 | 받는 사람마다 계정과 저장소 접근 권한이 필요하다 |
| APK를 다른 곳에 둔다 | 비공개를 유지한다 | 앱이 `https://github.com/`만 신뢰하므로 `CheckForUpdateUseCase`의 `TRUSTED_DOWNLOAD_HOST`를 고쳐야 한다 |

공개로 바꾸기 전에 **이력 전체**를 감사한다. 한 번 공개되면 복제본이 남을 수 있다.

```bash
# 위험한 경로가 이력에 들어간 적이 있나. 아무것도 안 나와야 한다.
git log --all --pretty=format: --name-only --diff-filter=A | sort -u   | grep -Ei "backup/|\.keystore$|\.base64$|local\.properties|\.jks$|service-account"

# 커밋된 내용에 토큰·개인키가 있나
git grep -I -l -E "ghp_|github_pat_|AIza|BEGIN PRIVATE KEY" $(git rev-list --all)
```

바꾸는 곳: 저장소 → **Settings** → **General** → 맨 아래 **Danger Zone** →
`Change repository visibility` → `Change to public` → 저장소 이름을 입력해 확인.

> 공개로 바꾼 뒤에도 한 걸음 더 남는다. 브라우저로 내려받은 APK를 설치하려면
> 폰에서 **그 브라우저에 '출처를 알 수 없는 앱 설치' 권한**을 허용해야 한다.
> 설정 → 애플리케이션 → (브라우저) → 출처를 알 수 없는 앱 설치.

---

### 설치 화면이 뜨지 않을 때

0.4.1부터 앱이 직접 APK를 받아 설치 화면을 띄운다. 그래도 뜨지 않는 경우가 있다.

| 증상 | 처방 |
|---|---|
| 카드에 `설치하려면 이 앱에 출처를 알 수 없는 앱 설치를 허용해야 합니다` | `허용 화면 열기`를 누르고 TracePhone을 허용한 뒤 다시 누른다 |
| 다 받았다는데 설치 화면이 안 뜬다 | 알림이나 **내 파일** 앱에서 받은 파일을 다시 열어 설치한다. 기기에 따라 설치 인텐트가 무시된다 |
| `INSTALL_FAILED_UPDATE_INCOMPATIBLE` | **서명이 다른 APK다. 앱을 지우면 누적 기록을 잃는다.** 멈추고 CI의 `서명 지문 확인` 단계를 본다 |
| 앱 안에서 받는 길이 계속 막힌다 | 카드의 `브라우저로 열기`로 손수 받는다. 대체 경로로 남겨 둔 것이다 |

브라우저로 받은 경우에도 그 브라우저에 **출처를 알 수 없는 앱 설치** 권한이 필요하다.
설정 → 애플리케이션 → (브라우저) → 출처를 알 수 없는 앱 설치.

### 잘못 만든 태그 고치기

어느 태그가 어느 버전을 담고 있는지 먼저 본다.

```bash
for t in $(git tag --list 'v*' --sort=version:refname); do
  echo "$t -> $(git show "$t:app/build.gradle.kts" | grep -oP 'versionName = "\K[^"]+')"
done
```

`v0.3.0 -> 0.2.2` 처럼 태그 이름과 값이 **다르게 나오는 태그**는 버전 변경이 커밋되지 않은
상태로 만들어진 것이다. 이렇게 고친다.

```bash
# 1) 버전 변경을 커밋한다
git add app/build.gradle.kts
git commit -m "0.3.0"
git push

# 2) 잘못된 태그를 로컬과 원격에서 지운다
git tag -d v0.3.0
git push origin :refs/tags/v0.3.0

# 3) 지금 커밋에 태그를 다시 만들어 민다
git tag v0.3.0
git push origin v0.3.0
```

태그에 이미 Release가 만들어져 있었다면, 태그를 지우면 그 Release는 가리킬 대상이 없어진다.
GitHub의 **Releases** 화면에서 그 Release도 함께 지운 뒤 다시 만드는 것이 깔끔하다.
워크플로가 실패해 멈춘 태그에는 Release가 생기지 않았으므로 지울 것이 없다.

---

### Releases는 폴더가 아니다

저장소의 **파일 목록에는 나타나지 않는다.** GitHub의 별도 기능이고, 자리는 세 곳이다.

| 어디 | 어떻게 |
|---|---|
| 주소로 직접 | `https://github.com/<내-사용자이름>/TracePhone/releases` |
| 저장소 메인 화면 | **Code** 탭 오른쪽 사이드바의 **Releases** 칸 (창이 좁으면 파일 목록 **아래**로 내려간다) |
| 태그 목록 | `.../tags` — 태그와 Release는 다른 것이다 |

아직 아무것도 만들지 않았으면 `No releases published`만 보인다. **정상이다.**
Release는 **`v` 태그를 밀고 워크플로가 성공한 뒤에** 생긴다. 손으로 만들 필요가 없다.

순서를 기억하면 헷갈리지 않는다.

```
git push origin v0.2.0
   → .../tags 에 태그가 곧바로 보인다
   → Actions 에서 '배포'가 돈다 (몇 분)
   → 성공하면 .../releases 에 0.2.0 이 생기고 APK가 붙는다
```

태그는 보이는데 Release가 없으면 **Actions 탭을 본다.** 워크플로가 아직 돌고 있거나
실패한 것이다. 3부 맨 앞의 표에서 오류 메시지를 찾는다.

---

## 부록

### 바꾼 파일

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
| `work/UsageRefresher.kt` | **새 파일(0.4.0).** 당김 새로고침 — 동기화 + 위젯 갱신을 `@ApplicationScope`에서 |
| `ui/summary/UsageSummaryViewModel.kt` | (0.4.0) `isRefreshing`·`refresh()` |
| `ui/summary/UsageSummaryScreen.kt` | (0.4.0) `pullToRefresh(enabled = selection.isCurrent)` |

새 런타임 의존성 0. Firebase SDK 0. 마이그레이션 0. DataStore 추가 0.

### 확인한 것 / 확인하지 못한 것

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

확인한 것 (2026-10-20, GitHub)

* **`배포` 워크플로가 `v0.3.0` 태그로 실제로 성공했다.** 러너에 `android-37` 플랫폼이 있고
  JDK 21로 AGP 9.4가 돈다는 것이 이 실행으로 확인됐다
* 그 전에 태그가 두 번 막혔다 — `v0.2.1`과 `v0.3.0`이 **버전 변경을 커밋하지 않은 채**
  만들어져 있었다. 검사가 의도대로 작동해 잘못된 APK가 배포되는 것을 막았다

확인한 것 (2026-10-20, 실기기 SM-S916N)

* **Firebase Realtime Database에서 실제로 읽힌다.** 설정 화면의 '앱 버전' 카드가
  `설치된 0.3.0 이 최신입니다`를 표시했다 — HTTPS 요청·파싱·버전 비교가 끝까지 이어졌다.
  `다시 확인`도 동작한다
* **`install -r`로 덮어써도 누적 기록이 보존된다.** 0.1.0 → 0.3.0 설치 전후로
  `daily_app_usage` **18,020행 / 2026-06-06~10-20 / 137일 / 앱 245개**가 한 행도 다르지 않고,
  `firstInstallTime`이 2026-09-03으로 유지됐다(새 설치가 아니라 업데이트로 처리됨)
* 요약 화면 `3 GB / 6 GB · 49% 여유`, 위젯 `3 GB / 6 GB 49%` 일치. 치명적 예외 없음
* **`networkKind` 열에 `'WIFI'` 12,127행 / `'MOBILE'` 5,893행이 문자열로 들어 있다.**
  열거형 이름을 고정한 proguard 규칙이 막는 위험이 가정이 아니라 실물이다

확인하지 **못한** 것

* **release 빌드가 기기에서 동작하는지.** 이 기기에는 백업 경로를 지키려고 `debug`를 넣었다.
  R8은 빌드가 아니라 런타임에 깨지므로, Hilt 주입·Room 조회·주기 동기화·Glance 위젯은
  **다른 기기에서** 확인하는 것이 이 기기의 안전망을 지키는 방법이다
* **CI가 만든 release APK가 기기에서 도는지.** `v0.3.0`으로 내려받은
  `TracePhone-0.3.0.apk`는 서명(`0a49f1…467e`, 폰의 키와 동일)·버전·권한·R8 적용·열거형
  이름 보존까지 전부 확인했지만 **한 번도 실행되지 않았다**
* **워크플로의 `서명 지문 확인` 단계가 실제로 비교했는지.** `EXPECTED_SIGNER_SHA1` 변수를
  등록했는지에 달려 있다. 변수가 없으면 경고만 남기고 지나간다 — 1-5에서 확인한다
* **당김 새로고침(0.4.0)의 실기기 동작.** 설치 직전 기기 연결이 끊겨 보류했다. 특히
  가로 `periodSwipe`와 세로 당김이 **대각선 제스처**에서 서로를 가로채는지 확인이 남았다
* Firebase 콘솔 화면의 메뉴 이름. Google이 수시로 바꾼다

### 하지 않은 것

| 후보 | 이유 |
|---|---|
| 배경에서 주기적으로 버전 확인 + 알림 | 통신이 사용자가 보지 않는 때에 일어난다. 사용 기록 앱에서 그 성질을 바꾸는 선택이라 지금은 설정 화면 안으로만 제한했다 |
| Firebase `latest` 자동 갱신 | 쓰기에는 서비스 계정이 필요하고, 그것이 피하려는 Google Cloud 절차다. 대신 CI가 붙여 넣을 JSON을 만들어 준다 |
| Firebase App Distribution | 업로드에 서비스 계정 JSON이 필요하다 |
| Play Store | 지금 서명 키로는 올릴 수 없다. 키를 바꾸려면 DB 내보내기 기능이 먼저 필요하다 |
| 앱 안에서 APK 자동 설치 | `REQUEST_INSTALL_PACKAGES` 권한이 필요하고 심사·보안 부담이 크다. 브라우저로 넘긴다 |
