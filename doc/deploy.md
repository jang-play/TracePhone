# 배포 구성 — GitHub + Firebase App Distribution

이 문서는 TracePhone을 **GitHub에 올리고, 태그를 밀면 Firebase App Distribution으로
APK가 배포되게** 만드는 전 과정을 처음부터 따라갈 수 있게 적은 것이다.

전체 그림은 이렇다.

```
내 PC에서 git push --tags
        ↓
GitHub Actions가 받는다
        ↓
단위 테스트 → release APK 빌드 → 서명 → 서명 지문 확인
        ↓
Firebase App Distribution에 업로드
        ↓
테스터에게 메일/링크로 설치 안내
```

---

## 0. 시작 전에 반드시 읽을 것 — 누적 DB와 서명

이 앱은 **시스템이 지운 뒤에도 남는 사용 기록**을 쌓는 것이 존재 이유다.
2026-10-20 기준 실기기에 119일 연속, 15,751행이 들어 있고 **복구할 방법이 없다.**
배포 구성에서 이 기록을 위협하는 것이 두 가지 있다.

### (1) 서명이 바뀌면 기록이 사라진다

안드로이드는 **서명이 다른 APK로 덮어쓰는 것을 거부한다.** 설치하려면 앱을 지워야 하고,
지우면 DB도 함께 사라진다.

그래서 이 구성은 **새 release 키를 만들지 않고, 기기에 이미 깔린 debug 빌드와 같은 키**
(`~/.android/debug.keystore`)로 release를 서명한다. 서명이 그대로이므로 `adb install -r`로
계속 덮어쓸 수 있다.

| 항목 | 값 |
|---|---|
| 별칭 | `androiddebugkey` |
| 비밀번호 | `android` (Android SDK가 정한 공개값이라 비밀이 아니다) |
| 지문 (SHA-1) | `0a49f14ece7d3dbe70e9ebb5e9797f753e81467e` |
| 유효기간 | 2056-08-26까지 (30년) |

치르는 대가는 하나다. **이 키로 서명한 앱은 Play Store에 올릴 수 없다.**
Play Store가 필요해지면 그때 키를 바꿔야 하고, 키를 바꾸는 순간 위의 문제가 돌아온다.
그 전에 앱에 DB 내보내기/가져오기 기능을 먼저 만들어야 한다.

### (2) release 빌드로 옮기면 지금의 백업 경로가 막힌다

지금까지 DB를 뽑을 때 쓴 `adb exec-out run-as com.dksystems.tracephone ...`은
**앱이 디버그 가능할 때만** 동작한다. 확인해 보면

* debug 매니페스트: `android:debuggable="true"`
* release 매니페스트: 그 속성이 없다

즉 **기기를 release 빌드로 바꾸면 `run-as` 백업이 거부된다.** 앱 안에 내보내기 기능이
없는 현재 상태에서는 그 뒤로 DB를 꺼낼 방법이 사라진다.

**권하는 운용 방식**

| 대상 | 빌드 | 이유 |
|---|---|---|
| 내 S23+ (누적 기록이 있는 기기) | **debug 유지** | `run-as` 백업 경로를 살려둔다 |
| 다른 사람 / 다른 기기 | release (App Distribution) | 최소화·축소된 정식 빌드 |

기기도 release로 옮기고 싶다면 **옮기기 전에 DB를 백업하고**, 그 뒤로는 adb로 꺼낼 수
없다는 것을 알고 옮긴다.

```bash
# release로 옮기기 전, debug 빌드가 깔려 있는 동안에만 가능하다
ADB="/c/Users/DKSYSTEMS/AppData/Local/Android/Sdk/platform-tools/adb.exe"
STAMP=$(date +%Y%m%d_%H%M%S)
mkdir -p "backup/$STAMP"
MSYS_NO_PATHCONV=1 "$ADB" exec-out run-as com.dksystems.tracephone \
  cat databases/tracephone.db > "backup/$STAMP/tracephone.db"
```

---

## 1단계 — GitHub 계정과 저장소

### 1-1. 계정 만들기

1. <https://github.com/signup> 에 접속한다
2. 이메일 → 비밀번호 → 사용자 이름(영문, 이게 주소에 들어간다)을 넣는다
3. 메일로 온 8자리 코드를 입력한다
4. 요금제는 **Free**를 고른다. 비공개 저장소도 무료다

### 1-2. 저장소 만들기

1. 오른쪽 위 `+` → **New repository**
2. 이렇게 채운다

   | 칸 | 값 |
   |---|---|
   | Repository name | `TracePhone` |
   | Description | 비워도 된다 |
   | 공개 범위 | **Private** — 개인 앱이고, 비공개가 안전하다 |
   | Add a README file | **체크하지 않는다** (이미 파일이 있다) |
   | Add .gitignore | **None** (이미 있다) |
   | Choose a license | None |

3. **Create repository**를 누른다
4. 다음 화면에 나오는 주소를 복사한다. 이런 모양이다
   `https://github.com/<내-사용자이름>/TracePhone.git`

### 1-3. 올라가면 안 되는 것부터 확인한다

이 프로젝트에는 **올리면 안 되는 파일이 두 종류** 있다.

* `backup/` — 실기기에서 뽑은 **실제 사용 기록 DB 4.8 MB**. 개인 데이터다
* `keystore.properties` — 서명 키의 위치와 비밀번호

둘 다 `.gitignore`에 들어 있지만, 올리기 **전에** 직접 확인한다.

```bash
cd /d/project/TracePhone
git init -b main
git check-ignore -v backup/ keystore.properties local.properties
```

세 줄 모두 `.gitignore:...` 로 시작하는 결과가 나와야 한다.
하나라도 안 나오면 멈추고 `.gitignore`를 고친다.

### 1-4. 첫 커밋과 푸시

```bash
cd /d/project/TracePhone

git add .

# 올라갈 목록을 눈으로 확인한다. backup/ 이나 keystore.properties 가 보이면 멈춘다.
git status --short | grep -E "backup/|keystore\.properties|local\.properties"
# 아무것도 출력되지 않아야 정상이다

git commit -m "TracePhone 초기 커밋"

git remote add origin https://github.com/<내-사용자이름>/TracePhone.git
git push -u origin main
```

`git push`에서 로그인 창이 뜬다. 윈도의 Git Credential Manager가 브라우저를 열어 주므로
GitHub에 로그인하고 **Authorize**를 누르면 된다. 한 번만 하면 이후에는 묻지 않는다.

> 브라우저가 열리지 않으면: GitHub → 오른쪽 위 프로필 → Settings →
> Developer settings → Personal access tokens → Tokens (classic) →
> Generate new token → `repo` 권한만 체크 → 만들어진 문자열을
> 비밀번호 칸에 붙여 넣는다.

---

## 2단계 — Firebase 프로젝트와 앱 등록

**중요**: 이 앱에는 Firebase SDK를 넣지 않는다. App Distribution은 APK를 올려두고
링크로 나눠주는 기능일 뿐이라 앱 코드가 전혀 바뀌지 않는다.
그래서 콘솔이 안내하는 `google-services.json` 추가와 플러그인 적용은 **건너뛴다.**
사용 기록을 다루는 앱에 네트워크 전송을 만들지 않는 것이 이 선택의 이유다.

### 2-1. 프로젝트 만들기

1. <https://console.firebase.google.com> 에 접속해 Google 계정으로 로그인한다
2. **프로젝트 만들기**를 누른다
3. 프로젝트 이름에 `TracePhone`을 넣는다
   (뒤에 `-a1b2c` 같은 식별자가 자동으로 붙는다. 정상이다)
4. **Google 애널리틱스**는 **끈다**. 이 앱은 사용 통계를 외부로 보내지 않는다
5. **프로젝트 만들기** → 완료되면 **계속**

### 2-2. 안드로이드 앱 등록

1. 프로젝트 개요 화면에서 **Android 아이콘**을 누른다
2. 이렇게 채운다

   | 칸 | 값 |
   |---|---|
   | Android 패키지 이름 | `com.dksystems.tracephone` |
   | 앱 닉네임 | `TracePhone` (아무거나 괜찮다) |
   | 디버그 서명 인증서 SHA-1 | **비워 둔다** (Google 로그인을 쓸 때만 필요하다) |

   패키지 이름은 **반드시 정확히** 위와 같아야 한다.
   `app/build.gradle.kts`의 `applicationId`와 한 글자라도 다르면 업로드가 실패한다.

3. **앱 등록**을 누른다
4. 다음 화면에서 `google-services.json`을 내려받으라고 한다 → **내려받지 않고 다음**
5. SDK를 추가하라고 한다 → **다음**
6. 앱을 실행해 확인하라고 한다 → **이 단계 건너뛰기**

### 2-3. 앱 ID 복사

1. 왼쪽 위 톱니바퀴 → **프로젝트 설정**
2. 아래로 내려 **내 앱** 칸에서 **앱 ID**를 찾아 복사한다
3. 이런 모양이다

   ```
   1:123456789012:android:0a1b2c3d4e5f6a7b
   ```

   나중에 GitHub 시크릿 `FIREBASE_APP_ID`에 넣는다.

### 2-4. App Distribution 켜기

1. 왼쪽 메뉴에서 **출시 및 모니터링** → **App Distribution**
2. **시작하기**를 누른다

### 2-5. 테스터 그룹 만들기 — 이름이 중요하다

워크플로가 `--groups testers`로 그룹을 지정하므로 **그룹 별칭이 정확히 `testers`**여야 한다.

1. App Distribution 화면에서 **테스터 및 그룹** 탭
2. **그룹 추가**
3. 그룹 이름에 `testers`를 넣는다
4. 만들어진 뒤 그룹을 눌러 **별칭(alias)이 `testers`인지 확인**한다.
   다르면 수정하거나, 워크플로의 `--groups` 값을 그 별칭으로 바꾼다
5. 그룹 안에서 **테스터 추가** → 설치받을 사람의 메일 주소를 넣는다 (자기 주소를 포함한다)

---

## 3단계 — 서비스 계정 키 (CI가 업로드할 권한)

GitHub Actions는 사람이 로그인할 수 없으므로, **기계용 계정**의 열쇠를 준다.

1. Firebase 콘솔 → 톱니바퀴 → **프로젝트 설정** → **서비스 계정** 탭
2. 아래쪽 **모든 서비스 계정 관리**를 누른다 (Google Cloud 콘솔로 넘어간다)
3. 위쪽 **+ 서비스 계정 만들기**
4. 이름에 `github-actions-distribute`를 넣고 **만들고 계속하기**
5. **역할 선택**에서 `Firebase App Distribution 관리자`를 고른다
   (검색창에 `App Distribution`을 치면 나온다) → **계속** → **완료**
6. 목록에서 방금 만든 계정을 누른다 → **키** 탭 → **키 추가** → **새 키 만들기**
7. **JSON**을 고르고 **만들기** → 파일이 자동으로 내려받아진다

> 이 JSON 파일은 **비밀번호와 같다.** 이 파일만 있으면 누구나 이 프로젝트에 앱을
> 올릴 수 있다. 저장소에 넣지 말고, 시크릿에 붙여 넣은 뒤 PC에서는 지운다.

업로드가 `API has not been used` 오류로 실패하면 Google Cloud 콘솔에서
`Firebase App Distribution API`를 검색해 **사용**을 누른다.

---

## 4단계 — GitHub 시크릿 7개 등록

GitHub 저장소 → **Settings** → 왼쪽 **Secrets and variables** → **Actions** →
**New repository secret**를 눌러 아래 7개를 하나씩 넣는다.

| 이름 | 넣을 값 |
|---|---|
| `RELEASE_KEYSTORE_BASE64` | 키스토어를 base64로 바꾼 문자열 (아래에서 만든다) |
| `RELEASE_STORE_PASSWORD` | `android` |
| `RELEASE_KEY_ALIAS` | `androiddebugkey` |
| `RELEASE_KEY_PASSWORD` | `android` |
| `EXPECTED_SIGNER_SHA1` | `0a49f14ece7d3dbe70e9ebb5e9797f753e81467e` |
| `FIREBASE_APP_ID` | 2-3에서 복사한 `1:...:android:...` |
| `FIREBASE_SERVICE_ACCOUNT` | 3단계에서 내려받은 JSON 파일의 **전체 내용** |

base64 문자열은 이렇게 만든다. 줄바꿈이 섞이면 복원이 깨지므로 `-w0`이 필요하다.

```bash
base64 -w0 "C:/Users/DKSYSTEMS/.android/debug.keystore" > keystore.base64.txt

# 제대로 됐는지 되돌려 비교한다. "같음"이 나와야 한다.
base64 -d keystore.base64.txt | cmp - "C:/Users/DKSYSTEMS/.android/debug.keystore" \
  && echo 같음
```

`keystore.base64.txt`의 내용을 전부 복사해 시크릿에 붙여 넣은 뒤 **파일을 지운다.**

```bash
rm keystore.base64.txt
```

> `EXPECTED_SIGNER_SHA1`은 안전장치다. 워크플로가 빌드한 APK의 서명 지문을 이 값과
> 비교해서, 다르면 배포를 멈춘다. 서명이 바뀐 APK가 기기로 흘러가 기록을 지우는
> 사고를 막는 장치다. 비워 두면 검사를 건너뛴다.

---

## 5단계 — 첫 배포

### 5-1. 푸시만 해서 검증 워크플로를 먼저 돌려본다

시크릿이 필요 없는 `빌드와 테스트` 워크플로가 먼저 통과하는지 본다.

```bash
git push
```

GitHub 저장소 → **Actions** 탭 → `빌드와 테스트` 실행을 눌러 초록색이 되는지 본다.
여기서 실패하면 배포는 시도하지 않는다. 실패한 단계를 눌러 로그를 읽는다.

### 5-2. 태그를 밀어 배포한다

배포는 **`v`로 시작하는 태그**를 밀 때만 돈다. 지금 버전은 `0.1.0`이다.

```bash
git tag v0.1.0
git push origin v0.1.0
```

Actions 탭에 `Firebase 배포`가 나타난다. 통과하면 Firebase 콘솔의
App Distribution → **출시** 목록에 APK가 올라오고, 테스터에게 메일이 간다.

### 5-3. 두 번째 배포부터 — 버전을 먼저 올린다

워크플로가 **태그와 `versionName`이 같은지 검사**해서 다르면 멈춘다.
같은 버전이 여러 번 올라가 어느 것이 최신인지 알 수 없게 되는 것을 막기 위한 것이다.

`app/build.gradle.kts`에서 두 값을 올린다.

```kotlin
versionCode = 2        // 설치할 때 신버전인지 판단하는 숫자. 반드시 올린다
versionName = "0.2.0"  // 사람이 읽는 이름. 태그와 같아야 한다
```

그리고

```bash
git add app/build.gradle.kts
git commit -m "0.2.0"
git push
git tag v0.2.0
git push origin v0.2.0
```

---

## 6단계 — 기기에 설치

### 다른 사람 기기 (App Distribution)

테스터는 메일의 링크를 열고, **Firebase App Distribution** 앱을 설치한 뒤
목록에서 TracePhone을 받는다.

설치 뒤 앱이 동작하려면 사용자가 **사용 정보 접근 권한**을 직접 켜야 한다.
설정 → 애플리케이션 → 특별한 접근 권한 → 사용 정보 접근. 앱 안에서도 안내한다.

### 내 S23+ (누적 기록이 있는 기기)

0단계에서 적은 대로 **debug 빌드를 유지하는 것을 권한다.**
release를 꼭 넣겠다면 **먼저 DB를 백업하고**, 아래 명령만 쓴다.

```bash
ADB="/c/Users/DKSYSTEMS/AppData/Local/Android/Sdk/platform-tools/adb.exe"

# -r 은 기존 앱 위에 덮어쓴다는 뜻이다. 데이터가 보존된다.
"$ADB" install -r app/build/outputs/apk/release/app-release.apk
```

`uninstall`은 **절대 쓰지 않는다.** 누적 기록이 사라지고 복구할 수 없다.

설치가 `INSTALL_FAILED_UPDATE_INCOMPATIBLE`로 실패하면 **서명이 다른 것이다.**
그대로 멈추고 `keystore.properties`가 `~/.android/debug.keystore`를 가리키는지 확인한다.
앱을 지워서 해결하려 하면 기록을 잃는다.

---

## 7단계 — 문제가 생길 때

| 증상 | 원인과 처방 |
|---|---|
| `Permission denied` / `./gradlew: not found` | 워크플로의 `chmod +x ./gradlew` 단계가 빠졌다 |
| `Failed to install the following Android SDK packages` | `platforms;android-37` 이름이 바뀌었다. 로그에 나오는 이름으로 워크플로를 고친다 |
| `SigningConfig "release" is missing required property` | 시크릿 이름의 철자가 틀렸다. 7개 모두 대문자와 밑줄까지 똑같아야 한다 |
| `base64: invalid input` | base64를 만들 때 `-w0`을 빼서 줄바꿈이 섞였다 |
| `서명이 기기의 것과 다릅니다` | 의도한 안전장치가 작동한 것이다. 키스토어 시크릿을 다시 만든다 |
| `Request to ... had HTTP Error: 403` | 서비스 계정에 `Firebase App Distribution 관리자` 역할이 없다 |
| `API has not been used in project` | Google Cloud 콘솔에서 `Firebase App Distribution API`를 켠다 |
| `UNAUTHENTICATED` | `FIREBASE_SERVICE_ACCOUNT`에 JSON 전체가 아니라 일부만 들어갔다 |
| 태그 검사에서 멈춘다 | `versionName`을 올리지 않고 태그를 만들었다 |

이미 밀어버린 태그를 고치려면

```bash
git tag -d v0.1.0
git push origin :refs/tags/v0.1.0
```

---

## 바꾼 파일

| 파일 | 변경 |
|---|---|
| `app/build.gradle.kts` | release 서명 설정. 값은 `keystore.properties` 또는 환경변수에서 읽는다 |
| `app/proguard-rules.pro` | 열거형 상수 이름 고정 (DB 열과 DataStore에 이름이 그대로 저장된다) |
| `.gitignore` | `backup/`, `keystore.properties`, `.kotlin/`, 다이어그램 캡처 제외 |
| `keystore.properties` | **새 파일, git 추적 제외.** 로컬 release 서명 값 |
| `.github/workflows/build.yml` | **새 파일.** 푸시마다 단위 테스트 + lint |
| `.github/workflows/distribute.yml` | **새 파일.** 태그를 밀면 release 빌드 → 서명 확인 → Firebase 업로드 |

## 확인한 것 / 확인하지 못한 것

확인한 것 (2026-10-20, 로컬)

* `:app:assembleRelease` 첫 성공. R8 축소가 빌드를 깨지 않았다 (3분 3초, APK 2.5 MB)
* release APK의 서명 지문이 기기의 debug 키와 **같다** (`0a49f1…467e`)
* `MOBILE_BACKGROUND`, `ALMOST`, `DETAILED` 등 저장되는 열거형 이름이 dex에 **남아 있다**
* release 매니페스트에 `debuggable`이 **없다** → `run-as` 백업 불가
* 두 워크플로 YAML 문법

확인하지 **못한** 것

* **GitHub Actions에서 실제로 도는지.** 저장소가 아직 없어 한 번도 실행되지 않았다.
  특히 러너에 `android-37` 플랫폼이 있는지, JDK 21로 AGP 9.4가 도는지는 첫 실행이 답한다
* **release 빌드가 기기에서 제대로 동작하는지.** R8은 빌드가 아니라 런타임에 깨진다.
  Hilt 주입, Room 조회, WorkManager 주기 동기화, Glance 위젯을 눈으로 확인해야 한다.
  빌드 성공은 동작을 보장하지 않는다
* Firebase 콘솔 화면의 메뉴 이름. Google이 수시로 바꾼다
