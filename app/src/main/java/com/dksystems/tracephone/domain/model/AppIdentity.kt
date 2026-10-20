package com.dksystems.tracephone.domain.model

/**
 * 사용량 한 줄이 어느 앱의 것인지 나타낸다.
 *
 * UID는 앱과 1:1이 아니다. `sharedUserId`로 여러 패키지가 한 UID를 공유할 수 있고,
 * 이미 제거된 앱이나 테더링 같은 특수 UID도 통계에 섞여 들어온다.
 * 따라서 [packageName]은 없을 수 있지만 [label]은 **항상** 화면에 표시 가능한 값이다.
 *
 * @property uid 시스템이 부여한 커널 UID. 저장소의 식별 키다.
 * @property packageName 대표 패키지명. 매핑에 실패하면 null.
 * @property label 화면에 표시할 이름. 매핑 실패 시 대체 라벨이 들어간다.
 * @property isInstalled 지금 이 기기의 설치 목록에 있는가.
 *
 *   거짓이면 **앱이 지워졌거나, 조회 권한이 그 UID에 미치지 않는 것**이다.
 *   둘을 구별할 방법이 없으므로 화면 문구도 '지워짐'이 아니라 '설치되지 않음'으로 적는다.
 *
 *   기본값을 `true`로 두어, 설치 여부를 따질 일이 없는 호출부가 그대로 쓰게 한다.
 */
data class AppIdentity(
    val uid: Int,
    val packageName: String?,
    val label: String,
    val isInstalled: Boolean = true
)
